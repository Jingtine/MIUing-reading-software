package com.miuing.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

import static com.miuing.core.PublicationParserTest.*;

/** Known-byte vectors are complemented by independent upstream OTF/WOFF corpus checks. */
final class EpubFontObfuscationTest {
    static final String IDPF="http://www.idpf.org/2008/embedding", ADOBE="http://ns.adobe.com/pdf/enc#RC";
    static byte[] plain(int length) {
        byte[] bytes=new byte[length];for(int i=0;i<length;i++) bytes[i]=(byte)(i*31+7);
        return bytes;
    }
    static byte[] encoded(byte[] original,byte[] key,int prefix) {
        byte[] bytes=original.clone();for(int i=0;i<Math.min(prefix,bytes.length);i++) bytes[i]^=key[i%key.length];return bytes;
    }
    static byte[] idpf(String id) throws Exception {return MessageDigest.getInstance("SHA-1").digest(id.replaceAll("[ \\t\\r\\n]","").getBytes(StandardCharsets.UTF_8));}
    static byte[] adobe() {return HexFormat.of().parseHex("12345678123456789abcdef012345678");}
    static String declaration(String algorithm,String uri) {
        return "<EncryptedData xmlns='http://www.w3.org/2001/04/xmlenc#'><EncryptionMethod Algorithm='"+algorithm+"'/><CipherData><CipherReference URI='"+uri+"'/></CipherData></EncryptedData>";
    }
    static Path book(Path dir,String identifiers,String declarations,byte[] font,String fontName,String mediaType) throws Exception {
        Map<String,byte[]> entries=new LinkedHashMap<>();
        for(var entry:epub().entrySet()) entries.put(entry.getKey(),entry.getValue().getBytes(StandardCharsets.UTF_8));
        String opf=new String(entries.get("OPS/package.opf"),StandardCharsets.UTF_8)
            .replace("<package ","<package unique-identifier='primary' ")
            .replace("</metadata>",identifiers+"</metadata>")
            .replace("</manifest>","<item id='font' href='"+fontName.replace(" ","%20")+"' media-type='"+mediaType+"'/></manifest>");
        entries.put("OPS/package.opf",opf.getBytes(StandardCharsets.UTF_8));entries.put("OPS/"+fontName,font);
        entries.put("META-INF/encryption.xml",("<encryption>"+declarations+"</encryption>").getBytes(StandardCharsets.UTF_8));
        Path file=dir.resolve("book.epub");
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(file))) {
            for(var entry:entries.entrySet()) {out.putNextEntry(new ZipEntry(entry.getKey()));out.write(entry.getValue());out.closeEntry();}
        }
        return file;
    }
    static String ids(String primary) {return "<dc:identifier id='other'>wrong-first-key</dc:identifier><dc:identifier id='primary'>"+primary+"</dc:identifier>";}
    static void run(Path root) throws Exception {
        Path cases=Files.createDirectory(root.resolve("font-cases"));
        test("IDPF uses designated identifier, XML whitespace and UTF-8",()->{
            Path d=Files.createDirectory(cases.resolve("idpf"));String id=" urn:miu:字\n\t 符\r\u00a0 ";
            byte[] original=plain(2200);Path source=book(d,ids(id),declaration(IDPF,"OPS/font.otf"),encoded(original,idpf(id),1040),"font.otf","application/vnd.ms-opentype");
            byte[] archive=Files.readAllBytes(source);PublicationParser.open(source,d.resolve("out"));
            check(Arrays.equals(original,Files.readAllBytes(d.resolve("out/OPS/font.otf"))),"IDPF font not decoded");
            check(Arrays.equals(archive,Files.readAllBytes(source)),"Original EPUB changed");
        });
        test("Adobe uses valid case-insensitive UUID identifier",()->{
            Path d=Files.createDirectory(cases.resolve("adobe"));byte[] original=plain(2200);
            Path source=book(d,ids("URN:UUID:12345678-1234-5678-9ABC-DEF012345678"),declaration(ADOBE,"OPS/font.woff"),encoded(original,adobe(),1024),"font.woff","font/woff");
            PublicationParser.open(source,d.resolve("out"));check(Arrays.equals(original,Files.readAllBytes(d.resolve("out/OPS/font.woff"))),"Adobe font not decoded");
        });
        test("Both prefix boundaries and short resources preserve all suffix bytes",()->{
            for(String algorithm:List.of(IDPF,ADOBE)) for(int length:new int[]{0,1,1023,1024,1039,1040,1041,4097}) {
                Path d=Files.createDirectory(cases.resolve("prefix-"+(algorithm.equals(IDPF)?"i":"a")+length));
                String id="urn:uuid:12345678-1234-5678-9abc-def012345678";byte[] original=plain(length);
                Path source=book(d,ids(id),declaration(algorithm,"OPS/font.otf"),encoded(original,algorithm.equals(IDPF)?idpf(id):adobe(),algorithm.equals(IDPF)?1040:1024),"font.otf","font/otf");
                PublicationParser.open(source,d.resolve("out"));check(Arrays.equals(original,Files.readAllBytes(d.resolve("out/OPS/font.otf"))),"Wrong prefix length: "+length);
            }
        });
        test("Reopen and identical duplicate declarations never double XOR",()->{
            Path d=Files.createDirectory(cases.resolve("repeat"));String id="test-id";byte[] original=plain(2200);
            String dec=declaration(IDPF,"OPS/font.otf");Path source=book(d,ids(id),dec+dec,encoded(original,idpf(id),1040),"font.otf","font/otf");
            for(int i=0;i<3;i++) {PublicationParser.open(source,d.resolve("out"));check(Arrays.equals(original,Files.readAllBytes(d.resolve("out/OPS/font.otf"))),"Double transform");}
        });
        test("Percent-encoded font path resolves inside publication root",()->{
            Path d=Files.createDirectory(cases.resolve("encoded"));String id="test-id";byte[] original=plain(2200);
            Path source=book(d,ids(id),declaration(IDPF,"OPS/Book%20Font.otf"),encoded(original,idpf(id),1040),"Book Font.otf","font/otf");
            PublicationParser.open(source,d.resolve("out"));check(Arrays.equals(original,Files.readAllBytes(d.resolve("out/OPS/Book Font.otf"))),"Escaped font path lost");
        });
        test("Declared font MIME supports extensionless resources, generic binary requires a font suffix",()->{
            Path d=Files.createDirectory(cases.resolve("font-mime"));String id="mime-id";byte[] original=plain(2200);
            Path source=book(d,ids(id),declaration(IDPF,"OPS/FontData"),encoded(original,idpf(id),1040),"FontData","font/otf");
            PublicationParser.open(source,d.resolve("out"));
            check(Arrays.equals(original,Files.readAllBytes(d.resolve("out/OPS/FontData"))),"Manifest font MIME was ignored");
            Path bad=Files.createDirectory(cases.resolve("generic-binary"));
            Path generic=book(bad,ids(id),declaration(IDPF,"OPS/data.bin"),plain(2200),"data.bin","application/octet-stream");
            rejects(()->PublicationParser.open(generic,bad.resolve("out")));
        });
        test("Unknown encryption and non-font targets are rejected explicitly",()->{
            for(String algorithm:List.of("urn:unsupported:drm",IDPF)) {
                Path d=Files.createDirectory(cases.resolve(algorithm.equals(IDPF)?"nonfont":"unknown"));
                Path source=book(d,ids("test-id"),declaration(algorithm,"OPS/one.xhtml"),plain(2200),"font.otf","font/otf");
                rejects(()->PublicationParser.open(source,d.resolve("out")));
            }
        });
        test("Unsafe missing and undeclared font targets are rejected",()->{
            int i=0;for(String target:List.of("../outside.otf","OPS/%2e%2e/%2e%2e/outside.otf","file:///tmp/font.otf","//example.com/font.otf","OPS/font.otf#part","OPS/font.otf?x=1","OPS/missing.otf","")) {
                Path d=Files.createDirectory(cases.resolve("unsafe-"+i++));
                Path source=book(d,ids("test-id"),declaration(IDPF,target),plain(2200),"font.otf","font/otf");
                rejects(()->PublicationParser.open(source,d.resolve("out")));
            }
        });
        test("Missing primary key invalid Adobe UUID and conflicting declarations fail",()->{
            int i=0;for(String[] input:List.of(
                new String[]{"<dc:identifier id='other'>wrong</dc:identifier>",declaration(IDPF,"OPS/font.otf")},
                new String[]{ids("   "),declaration(IDPF,"OPS/font.otf")},
                new String[]{ids("not-a-uuid"),declaration(ADOBE,"OPS/font.otf")},
                new String[]{ids("urn:uuid:12345678-1234-5678-9abc-def012345678"),declaration(IDPF,"OPS/font.otf")+declaration(ADOBE,"OPS/font.otf")})) {
                Path d=Files.createDirectory(cases.resolve("invalid-"+i++));Path source=book(d,input[0],input[1],plain(2200),"font.otf","font/otf");
                rejects(()->PublicationParser.open(source,d.resolve("out")));
                check(Arrays.equals(plain(2200),Files.readAllBytes(d.resolve("out/OPS/font.otf"))),"Validation partially transformed font");
            }
        });
    }
}
