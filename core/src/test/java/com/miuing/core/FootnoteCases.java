package com.miuing.core;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static com.miuing.core.PublicationParserTest.*;

final class FootnoteCases {
    static void run() {
        test("footnote extracts containing note and removes active content",()->{
            Path file=Files.createTempFile("miuing-note",".xhtml");
            try {
                Files.writeString(file,"<html xmlns:epub='http://www.idpf.org/2007/ops'><body><p>outside</p><aside epub:type='footnote'><p id='n'>中文 <em>emphasis</em><script>bad()</script><img src='https://example.org/a'/><a epub:type='backlink' href='c.xhtml#r'>back</a></p></aside></body></html>");
                String html=EpubFootnotes.read(file,"n");
                check(html.contains("中文 <em>emphasis</em>"),"lost note content");
                check(!html.contains("outside") && !html.contains("bad") && !html.contains("href") && !html.contains("src=") && !html.contains("back"),"unsafe/extraneous content");
            } finally {Files.delete(file);}
        });
        test("footnote supports UTF16 and escaped plain content",()->{
            Path file=Files.createTempFile("miuing-note",".xhtml");
            try {
                Files.write(file,"<html><body><p id='注'>甲 &lt;b&gt; &amp; 乙<br/>下一行</p></body></html>".getBytes(StandardCharsets.UTF_16));
                String html=EpubFootnotes.read(file,"注");
                check(html.contains("&lt;b&gt;") && html.contains("&amp;") && html.contains("<br"),"unsafe escaping/encoding");
            } finally {Files.delete(file);}
        });
        test("footnote rejects missing and excessive content",()->{
            Path file=Files.createTempFile("miuing-note",".xhtml");
            try {
                Files.writeString(file,"<html><body><p id='n'>"+"文".repeat(33000)+"</p></body></html>");
                try {EpubFootnotes.read(file,"missing");throw new AssertionError("accepted missing target");} catch(java.io.IOException expected) {}
                try {EpubFootnotes.read(file,"n");throw new AssertionError("accepted large target");} catch(java.io.IOException expected) {}
            } finally {Files.delete(file);}
        });
        test("footnote rejects entity declarations",()->{
            Path file=Files.createTempFile("miuing-note",".xhtml");
            try {
                Files.writeString(file,"<!DOCTYPE html [<!ENTITY x SYSTEM 'file:///secret'>]><html><p id='n'>&x;</p></html>");
                try {EpubFootnotes.read(file,"n");throw new AssertionError("accepted entity");} catch(java.io.IOException expected) {}
            } finally {Files.delete(file);}
        });
    }
}
