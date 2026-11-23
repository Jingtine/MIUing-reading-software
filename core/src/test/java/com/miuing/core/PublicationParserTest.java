package com.miuing.core;

import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;

/** Dependency-free executable tests, also runnable through Gradle core:verifyCore. */
public final class PublicationParserTest {
    static int passed, failed;
    interface Case { void run() throws Exception; }
    static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    static void test(String name, Case body) {
        try { body.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable e) { failed++; System.out.println("FAIL " + name + ": " + e); }
    }
    static Path fixture(Path dir, Map<String,String> entries) throws Exception {
        Path file = dir.resolve("book.epub");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue().getBytes(StandardCharsets.UTF_8)); out.closeEntry();
            }
        }
        return file;
    }
    static Map<String,String> epub() {
        Map<String,String> e = new LinkedHashMap<>();
        e.put("mimetype", "application/epub+zip");
        e.put("META-INF/container.xml", "<container><rootfiles><rootfile full-path='OPS/package.opf'/></rootfiles></container>");
        e.put("OPS/package.opf", "<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title>测试书</dc:title><dc:creator>作者甲</dc:creator></metadata><manifest><item id='one' href='one.xhtml' media-type='application/xhtml+xml'/><item id='two' href='two.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='two'/><itemref idref='one'/></spine></package>");
        e.put("OPS/one.xhtml", "<html><head><title>第一章</title></head><body>一</body></html>");
        e.put("OPS/two.xhtml", "<html><head><title>第二章</title></head><body>二</body></html>");
        return e;
    }
    static void rejects(Case c) throws Exception {
        try { c.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected rejected input");
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("miuing-core-test");
        try {
            test("EPUB metadata and spine order", () -> {
                Path d = Files.createDirectory(root.resolve("epub"));
                Publication p = PublicationParser.open(fixture(d,epub()),d.resolve("out"));
                check(p.title.equals("测试书") && p.author.equals("作者甲"), "Metadata lost");
                check(p.chapters.size()==2 && p.chapters.get(0).title.equals("第二章"), "Spine order wrong");
                check(Files.readString(p.chapters.get(1).path).contains("一"), "Chapter content lost");
            });
            test("ZIP path traversal is rejected", () -> {
                Path d=Files.createDirectory(root.resolve("traversal")); var e=epub(); e.put("../escaped.txt","escape");
                rejects(() -> PublicationParser.open(fixture(d,e),d.resolve("out")));
                check(!Files.exists(d.resolve("escaped.txt")),"Entry escaped extraction directory");
            });
            test("EPUB3 nested navigation keeps labels, fragments and hierarchy", () -> {
                Path d=Files.createDirectory(root.resolve("nav3"));var e=epub();
                e.put("OPS/package.opf",e.get("OPS/package.opf").replace("</manifest>","<item id='nav' href='nav.xhtml' media-type='application/xhtml+xml' properties='nav'/></manifest>"));
                e.put("OPS/nav.xhtml","<html xmlns:epub='http://www.idpf.org/2007/ops'><body><nav epub:type='toc'><ol><li><a href='two.xhtml#start'>上篇</a><ol><li><a href='one.xhtml#%E8%8A%82'>细节</a></li></ol></li><li><a href='https://example.com/'>External</a></li></ol></nav></body></html>");
                Publication p=PublicationParser.open(fixture(d,e),d.resolve("out"));
                check(p.navigation.size()==2,"Unsafe or missing nav entries");
                check(p.navigation.get(0).title.equals("上篇") && p.navigation.get(0).chapter==0 && p.navigation.get(0).fragment.equals("start"),"Parent destination incorrect");
                check(p.navigation.get(1).depth==1 && p.navigation.get(1).chapter==1 && p.navigation.get(1).fragment.equals("节"),"Nested/escaped fragment lost");
            });
            test("EPUB2 NCX supports external DOCTYPE without fetching it", () -> {
                Path d=Files.createDirectory(root.resolve("ncx"));var e=epub();
                e.put("OPS/package.opf",e.get("OPS/package.opf").replace("</manifest>","<item id='ncx' href='toc.ncx' media-type='application/x-dtbncx+xml'/></manifest>").replace("<spine>","<spine toc='ncx'>"));
                e.put("OPS/toc.ncx","<!DOCTYPE ncx SYSTEM 'https://invalid.example/never-fetch.dtd'><ncx><navMap><navPoint><navLabel><text>第一部分</text></navLabel><content src='two.xhtml#part'/><navPoint><navLabel><text>子节</text></navLabel><content src='one.xhtml#child'/></navPoint></navPoint></navMap></ncx>");
                Publication p=PublicationParser.open(fixture(d,e),d.resolve("out"));
                check(p.navigation.size()==2 && p.navigation.get(0).title.equals("第一部分"),"NCX labels missing");
                check(p.navigation.get(1).depth==1 && p.navigation.get(1).fragment.equals("child"),"NCX nesting lost");
            });
            test("Broken navigation falls back to readable spine", () -> {
                Path d=Files.createDirectory(root.resolve("bad-nav"));var e=epub();
                e.put("OPS/package.opf",e.get("OPS/package.opf").replace("</manifest>","<item id='nav' href='nav.xhtml' media-type='application/xhtml+xml' properties='nav'/></manifest>"));
                e.put("OPS/nav.xhtml","<broken");
                Publication p=PublicationParser.open(fixture(d,e),d.resolve("out"));
                check(p.navigation.size()==2 && p.navigation.get(0).title.equals("第二章"),"Invalid nav blocked fallback");
            });
            test("Disabled navigation headings do not hide valid NCX", () -> {
                Path d=Files.createDirectory(root.resolve("disabled-nav"));var e=epub();
                e.put("OPS/package.opf",e.get("OPS/package.opf").replace("</manifest>","<item id='nav' href='nav.xhtml' media-type='application/xhtml+xml' properties='nav'/><item id='ncx' href='toc.ncx' media-type='application/x-dtbncx+xml'/></manifest>"));
                e.put("OPS/nav.xhtml","<html xmlns:epub='http://www.idpf.org/2007/ops'><nav epub:type='toc'><ol><li><span>分组</span><ol><li><a href='missing.xhtml'>坏链接</a></li></ol></li></ol></nav></html>");
                e.put("OPS/toc.ncx","<ncx><navMap><navPoint><navLabel><text>可读目录</text></navLabel><content src='two.xhtml'/></navPoint></navMap></ncx>");
                Publication p=PublicationParser.open(fixture(d,e),d.resolve("out"));
                check(p.navigation.get(0).title.equals("可读目录") && p.navigation.get(0).chapter==0,"Disabled heading suppressed navigation fallback");
            });
            test("UTF-8 BOM TXT is escaped into safe readable HTML", () -> {
                Path p=root.resolve("中文.txt"); Files.writeString(p,"\ufeff第一章 开始\n这是正文 <script>alert(1)</script>");
                Publication b=PublicationParser.open(p,root.resolve("txt"));
                String html=Files.readString(b.chapters.get(0).path);
                check(html.contains("这是正文") && !html.contains("\ufeff") && html.contains("&lt;script&gt;"),"Unsafe or garbled text");
            });
            test("GB18030 Chinese TXT is decoded", () -> {
                Path p=root.resolve("legacy.txt"); Files.write(p,"第一章\n中文阅读内容".getBytes(Charset.forName("GB18030")));
                Publication b=PublicationParser.open(p,root.resolve("legacy"));
                check(Files.readString(b.chapters.get(0).path).contains("中文阅读内容"),"Legacy text garbled");
            });
            test("UTF-16 EPUB markup is decoded according to its BOM", () -> {
                Path p=root.resolve("utf16.xhtml");
                Files.write(p,"<?xml version='1.0' encoding='UTF-16'?><html><body>正文中文</body></html>".getBytes(StandardCharsets.UTF_16));
                check(PublicationParser.readMarkup(p).contains("正文中文"),"UTF-16 markup garbled");
            });
            test("Malformed EPUB is rejected", () -> {
                Path p=root.resolve("bad.epub"); Files.writeString(p,"not a zip");
                rejects(() -> PublicationParser.open(p,root.resolve("bad")));
            });
            test("External XML entities are rejected without reading host files", () -> {
                Path d=Files.createDirectory(root.resolve("xxe")); var e=epub();
                e.put("META-INF/container.xml","<!DOCTYPE container [<!ENTITY xxe SYSTEM 'file:///secret'>]><container>&xxe;</container>");
                rejects(() -> PublicationParser.open(fixture(d,e),d.resolve("out")));
            });
            test("Oversized inflated ZIP entry is rejected", () -> {
                Path d=Files.createDirectory(root.resolve("oversized")); var e=epub(); e.put("huge.txt","x".repeat(17*1024*1024));
                rejects(() -> PublicationParser.open(fixture(d,e),d.resolve("out")));
            });
            EpubFontObfuscationTest.run(root);
        } finally {
            try (var files=Files.walk(root)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
        FootnoteCases.run();
        System.out.println(passed+" passed, "+failed+" failed");
        if(failed>0) System.exit(1);
    }
}
