package com.miuing.core;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.w3c.dom.*;

/**
 * Font algorithms adapted from Foliate-js epub.js, MIT, John Factotum.
 * Source: 78914aef4466eb960965702401634c2cb348e9b1, lines 554–597.
 * Standard font obfuscation only. Validation and bounded extracted-file IO are MIUing's.
 * License: app/src/main/assets/licenses/foliate-js-LICENSE.txt.
 */
final class EpubFontObfuscation {
    private static final String IDPF="http://www.idpf.org/2008/embedding";
    private static final String ADOBE="http://ns.adobe.com/pdf/enc#RC";
    private static final String DC="http://purl.org/dc/elements/1.1/";
    private static final Set<String> FONT_TYPES=Set.of("application/vnd.ms-opentype","application/font-sfnt",
        "application/font-woff","application/x-font-ttf","application/x-font-truetype",
        "application/x-font-opentype","application/x-font-otf","application/x-font-woff");
    private record Transform(Path path,String algorithm,byte[] key,int prefix) {}

    static void decode(Path root,Document opf,Map<Path,String> manifestTypes) throws IOException {
        Path encryption=root.resolve("META-INF/encryption.xml");
        if(!Files.exists(encryption)) return;
        Document document=PublicationParser.xml(encryption);
        NodeList entries=document.getElementsByTagNameNS("*","EncryptedData");
        if(entries.getLength()>10000) throw new IOException("EPUB 字体混淆条目过多");
        Map<Path,Transform> transforms=new LinkedHashMap<>();
        Map<String,byte[]> keys=new HashMap<>();
        for(int i=0;i<entries.getLength();i++) {
            Element entry=(Element)entries.item(i);
            NodeList methods=entry.getElementsByTagNameNS("*","EncryptionMethod");
            NodeList references=entry.getElementsByTagNameNS("*","CipherReference");
            if(methods.getLength()!=1 || references.getLength()!=1) throw new IOException("EPUB 字体混淆声明不完整");
            String algorithm=((Element)methods.item(0)).getAttribute("Algorithm");
            if(!algorithm.equals(IDPF) && !algorithm.equals(ADOBE))
                throw new IOException("此 EPUB 使用暂不支持的加密方式；目前仅支持标准字体混淆");
            Path path=resource(root,((Element)references.item(0)).getAttribute("URI"));
            String type=manifestTypes.get(path);
            String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean fontType=type!=null && (type.startsWith("font/") || FONT_TYPES.contains(type) ||
                type.equals("application/octet-stream") && name.matches(".*\\.(otf|ttf|woff|woff2)"));
            if(!fontType || !Files.isRegularFile(path) ||
                !path.toRealPath().startsWith(root.toRealPath()))
                throw new IOException("EPUB 混淆目标必须是书内已声明的字体文件");
            Transform previous=transforms.get(path);
            if(previous!=null) {
                if(!previous.algorithm.equals(algorithm)) throw new IOException("EPUB 字体混淆声明冲突");
                continue;
            }
            byte[] key=keys.get(algorithm);
            if(key==null) {key=algorithm.equals(IDPF)?idpfKey(opf):adobeKey(opf);keys.put(algorithm,key);}
            transforms.put(path,new Transform(path,algorithm,key,algorithm.equals(IDPF)?1040:1024));
        }
        // Validate every declaration first. Duplicate entries cannot transform a font twice.
        byte[] prefix=new byte[1040];
        for(Transform transform:transforms.values()) {
            try(RandomAccessFile font=new RandomAccessFile(transform.path.toFile(),"rw")) {
                int length=(int)Math.min(transform.prefix,font.length());
                font.readFully(prefix,0,length);
                for(int i=0;i<length;i++) prefix[i]^=transform.key[i%transform.key.length];
                font.seek(0);font.write(prefix,0,length);
            }
        }
    }
    private static Path resource(Path root,String href) throws IOException {
        try {
            URI uri=new URI(href);
            if(href.isEmpty() || uri.getRawQuery()!=null || uri.getRawFragment()!=null || uri.getPath()==null || uri.getPath().isEmpty())
                throw new IOException("EPUB 字体路径无效");
            return PublicationParser.relativeResource(root,root,href);
        } catch(URISyntaxException e) {throw new IOException("EPUB 字体路径无效",e);}
    }
    private static byte[] idpfKey(Document opf) throws IOException {
        NodeList identifiers=opf.getElementsByTagNameNS(DC,"identifier");
        String designated=opf.getDocumentElement().getAttribute("unique-identifier");
        Element selected=null;
        for(int i=0;i<identifiers.getLength();i++) {
            Element identifier=(Element)identifiers.item(i);
            if(designated.isEmpty() || designated.equals(identifier.getAttribute("id"))) {selected=identifier;break;}
        }
        String value=selected==null?"":selected.getTextContent().replaceAll("[ \\t\\r\\n]","");
        if(value.isEmpty()) throw new IOException("EPUB 缺少字体混淆所需的主标识符");
        try {return MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));}
        catch(NoSuchAlgorithmException e) {throw new IOException("系统不支持 EPUB 字体校验",e);}
    }
    private static byte[] adobeKey(Document opf) throws IOException {
        NodeList identifiers=opf.getElementsByTagNameNS(DC,"identifier");
        for(int i=0;i<identifiers.getLength();i++) {
            String value=identifiers.item(i).getTextContent().trim();
            value=value.substring(value.lastIndexOf(':')+1);
            if(!value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) continue;
            String hex=value.replace("-","");byte[] key=new byte[16];
            for(int n=0;n<key.length;n++) key[n]=(byte)((Character.digit(hex.charAt(n*2),16)<<4)|Character.digit(hex.charAt(n*2+1),16));
            return key;
        }
        throw new IOException("EPUB 缺少字体混淆所需的有效 UUID");
    }
}
