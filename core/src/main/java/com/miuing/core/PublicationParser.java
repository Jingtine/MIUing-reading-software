package com.miuing.core;

import java.nio.file.*;
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

public final class PublicationParser {
    public static final int TXT_LAYOUT_LEGACY = 1;
    public static final int TXT_LAYOUT_CURRENT = 2;
    public static String readMarkup(Path path) throws IOException {
        byte[] bytes=Files.readAllBytes(path);
        Charset charset=StandardCharsets.UTF_8;
        if(bytes.length>=2 && (bytes[0]==(byte)0xff && bytes[1]==(byte)0xfe || bytes[0]==(byte)0xfe && bytes[1]==(byte)0xff)) charset=StandardCharsets.UTF_16;
        else if(bytes.length>=4 && bytes[0]==0 && bytes[1]=='<') charset=StandardCharsets.UTF_16BE;
        else if(bytes.length>=4 && bytes[0]=='<' && bytes[1]==0) charset=StandardCharsets.UTF_16LE;
        else {
            String head=new String(bytes,0,Math.min(bytes.length,512),StandardCharsets.ISO_8859_1);
            var match=java.util.regex.Pattern.compile("(?i)<\\?xml[^>]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(head);
            if(match.find()) try { charset=Charset.forName(match.group(1)); } catch(Exception e) { throw new IOException("不支持的章节编码",e); }
        }
        return new String(bytes,charset).replace("\ufeff", "");
    }
    private static final long ENTRY_LIMIT=16L*1024*1024, TOTAL_LIMIT=256L*1024*1024;
    public static Publication open(Path source, Path output) throws IOException {
        return open(source, output, TXT_LAYOUT_CURRENT);
    }
    /** A saved TXT locator must be reopened with the layout that created it. */
    public static Publication open(Path source, Path output, int txtLayoutVersion) throws IOException {
        Files.createDirectories(output);
        String name=source.getFileName().toString();
        String title=name.replaceFirst("(?i)\\.[^.]+$", "");
        if(name.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            if(txtLayoutVersion != TXT_LAYOUT_LEGACY && txtLayoutVersion != TXT_LAYOUT_CURRENT)
                throw new IOException("不支持此 TXT 分章版本");
            return text(source,output,title,txtLayoutVersion);
        }
        if(!name.toLowerCase(Locale.ROOT).endsWith(".epub")) throw new IOException("不支持的文件格式");
        Path root=output.toAbsolutePath().normalize();
        long total=0; int count=0;
        try(ZipFile zip=new ZipFile(source.toFile())) {
            var entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();
                if(++count>10000) throw new IOException("EPUB 文件条目过多");
                Path target=contained(root,entry.getName());
                if(entry.isDirectory()) { Files.createDirectories(target); continue; }
                Files.createDirectories(target.getParent());
                long size=0;
                try(InputStream in=zip.getInputStream(entry); OutputStream out=Files.newOutputStream(target)) {
                    byte[] buffer=new byte[8192]; int n;
                    while((n=in.read(buffer))!=-1) {
                        size+=n; total+=n;
                        if(size>ENTRY_LIMIT || total>TOTAL_LIMIT) throw new IOException("EPUB 解压大小超出限制");
                        out.write(buffer,0,n);
                    }
                }
            }
        } catch(IllegalArgumentException e) { throw new IOException("无效的 EPUB",e); }
        Document container=xml(root.resolve("META-INF/container.xml"));
        NodeList roots=container.getElementsByTagNameNS("*","rootfile");
        if(roots.getLength()==0) throw new IOException("EPUB 缺少内容清单");
        Path opf=contained(root,((Element)roots.item(0)).getAttribute("full-path"));
        Document doc=xml(opf);
        title=value(doc,"title",title); String author=value(doc,"creator","未知作者");
        Map<String,Path> items=new HashMap<>(); Map<Path,String> resourceTypes=new HashMap<>(); Path cover=null,nav=null,ncx=null;
        String coverId="";
        NodeList metas=doc.getElementsByTagNameNS("*","meta");
        for(int i=0;i<metas.getLength();i++) {
            Element m=(Element)metas.item(i);
            if(m.getAttribute("name").equals("cover")) coverId=m.getAttribute("content");
        }
        NodeList manifest=doc.getElementsByTagNameNS("*","item");
        for(int i=0;i<manifest.getLength();i++) {
            Element item=(Element)manifest.item(i);
            String href=item.getAttribute("href");
            Path path=relativeResource(root,opf.getParent(),href);
            items.put(item.getAttribute("id"),path);
            resourceTypes.put(path,item.getAttribute("media-type").toLowerCase(Locale.ROOT));
            if(Arrays.asList(item.getAttribute("properties").split("\\s+")).contains("nav")) nav=path;
            if(item.getAttribute("media-type").equals("application/x-dtbncx+xml")) ncx=path;
            if(item.getAttribute("properties").contains("cover-image") || item.getAttribute("id").equals(coverId)) cover=path;
        }
        EpubFontObfuscation.decode(root,doc,resourceTypes);
        List<Publication.Chapter> chapters=new ArrayList<>();
        NodeList spine=doc.getElementsByTagNameNS("*","itemref");
        for(int i=0;i<spine.getLength();i++) {
            Element ref=(Element)spine.item(i);
            if(ref.getAttribute("linear").equals("no")) continue;
            Path path=items.get(ref.getAttribute("idref"));
            if(path==null || !Files.isRegularFile(path)) throw new IOException("EPUB 章节缺失");
            String chapter="第 "+(chapters.size()+1)+" 节";
            // Chapter XHTML is treated as data; malformed HTML still renders in the reader.
            try { chapter=value(xml(path),"title",chapter); } catch(IOException ignored) { }
            chapters.add(new Publication.Chapter(chapter,path));
        }
        if(chapters.isEmpty()) throw new IOException("EPUB 没有可阅读章节");
        NodeList spineElements=doc.getElementsByTagNameNS("*","spine");
        if(spineElements.getLength()>0) {
            Path specified=items.get(((Element)spineElements.item(0)).getAttribute("toc"));
            if(specified!=null) ncx=specified;
        }
        List<Publication.Navigation> navigation=EpubNavigation.read(root,nav,ncx,chapters);
        return navigation.isEmpty()?new Publication(title,author,"EPUB",chapters,cover):new Publication(title,author,"EPUB",chapters,cover,navigation);
    }
    private static Path contained(Path root,String name) throws IOException {
        if(name.contains("\\") || name.contains(":")) throw new IOException("无效的 EPUB 路径");
        Path p=root.resolve(name).normalize();
        if(!p.startsWith(root) || p.equals(root)) throw new IOException("EPUB 路径越界");
        return p;
    }
    static Path relativeResource(Path root,Path parent,String href) throws IOException {
        try {
            java.net.URI uri=new java.net.URI(href);
            if(uri.isAbsolute() || uri.getRawAuthority()!=null) throw new IOException("EPUB 资源必须在书内");
            Path path=parent.resolve(uri.getPath()).normalize();
            return contained(root,root.relativize(path).toString().replace('\\','/'));
        } catch(java.net.URISyntaxException | IllegalArgumentException e) { throw new IOException("无效的 EPUB 资源路径",e); }
    }
    static Document xml(Path path) throws IOException {
        try {
            if(Files.size(path)>ENTRY_LIMIT) throw new IOException("XML 文件过大");
            String markup=readMarkup(path);
            // Android's XML factory does not support Xerces' disallow-doctype feature.
            // Reject DTD declarations before parsing, including UTF-16/32 ASCII encodings.
            if(markup.contains("<!ENTITY") || java.util.regex.Pattern.compile("(?s)<!DOCTYPE[^>]*\\[").matcher(markup).find()) throw new IOException("不支持 XML 外部实体与内部 DTD");
            // NCX commonly declares an external DTD. Strip the declaration without fetching it.
            markup=markup.replaceAll("(?s)<!DOCTYPE[^>]*>","");
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            DocumentBuilder builder=f.newDocumentBuilder();
            builder.setEntityResolver((publicId,systemId) -> { throw new org.xml.sax.SAXException("External entities are disabled"); });
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            return builder.parse(new org.xml.sax.InputSource(new StringReader(markup)));
        } catch(Exception e) { throw new IOException("无法解析 EPUB 内容清单",e); }
    }
    private static String value(Document doc,String tag,String fallback) {
        NodeList nodes=doc.getElementsByTagNameNS("*",tag);
        if(nodes.getLength()==0) return fallback;
        String s=nodes.item(0).getTextContent().trim(); return s.isEmpty()?fallback:s;
    }
    private static Publication text(Path source,Path output,String title,int layoutVersion) throws IOException {
        if(Files.size(source)>32L*1024*1024) throw new IOException("TXT 暂支持 32 MB 以内的文件");
        byte[] bytes=Files.readAllBytes(source); String text;
        if(bytes.length>=2 && (bytes[0]==(byte)0xff && bytes[1]==(byte)0xfe || bytes[0]==(byte)0xfe && bytes[1]==(byte)0xff)) text=new String(bytes,StandardCharsets.UTF_16);
        else try { text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch(CharacterCodingException e) { text=new String(bytes,Charset.forName("GB18030")); }
        text=text.replace("\ufeff","").replace("\r\n","\n");
        if(layoutVersion == TXT_LAYOUT_CURRENT) text=text.replace('\r','\n');
        List<Publication.Chapter> chapters=new ArrayList<>();
        StringBuilder chunk=new StringBuilder(); String heading=title;
        for(String line:text.split("\n",-1)) {
            boolean isHeading=line.trim().matches("(?i)(第.{1,20}[章节卷回部].*|chapter\\s+\\w+.*)");
            if((isHeading || chunk.length()>24000) && chunk.length()>0) {
                writeTextChapter(output,chapters,heading,chunk.toString()); chunk.setLength(0);
                heading=isHeading?line.trim():title+" · "+(chapters.size()+1);
            } else if(isHeading) heading=line.trim();
            chunk.append(line).append('\n');
        }
        if(chunk.length()>0 || chapters.isEmpty()) writeTextChapter(output,chapters,heading,chunk.toString());
        return new Publication(title,"未知作者","TXT",chapters,null);
    }
    private static void writeTextChapter(Path dir,List<Publication.Chapter> chapters,String title,String text) throws IOException {
        Path path=dir.resolve("chapter-"+chapters.size()+".html");
        String escaped=text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
        Files.write(path,("<!doctype html><html><head><meta charset='UTF-8'></head><body><div style='white-space:pre-wrap'>"+escaped+"</div></body></html>").getBytes(StandardCharsets.UTF_8));
        chapters.add(new Publication.Chapter(title,path));
    }
}
