package com.miuing.core;

import org.w3c.dom.*;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;

/** Extract a small, inert fragment; book CSS, URLs and executable content never cross this boundary. */
public final class EpubFootnotes {
    private static final Set<String> SAFE=Set.of("p","div","span","em","strong","b","i","u","s","small","sup","sub","br","ol","ul","li","blockquote","code","pre","h1","h2","h3","h4","h5","h6");
    private static final Set<String> SKIP=Set.of("script","style","noscript","iframe","object","embed","svg","img","link","meta","form","input","button");
    public static String read(Path path,String fragment) throws IOException {
        if(fragment==null || fragment.isEmpty()) throw new IOException("脚注缺少目标位置");
        Document doc=PublicationParser.xml(path);
        Element target=null;
        Deque<Node> pending=new ArrayDeque<>();pending.add(doc);
        while(!pending.isEmpty()) {
            Node node=pending.removeLast();
            if(node instanceof Element && (fragment.equals(((Element)node).getAttribute("id")) || fragment.equals(((Element)node).getAttribute("xml:id")))) {target=(Element)node;break;}
            NodeList children=node.getChildNodes();for(int i=children.getLength()-1;i>=0;i--) pending.add(children.item(i));
        }
        if(target==null) throw new IOException("没有找到脚注");
        for(Node node=target;node instanceof Element;node=node.getParentNode()) {
            Element element=(Element)node;
            if(type(element,"footnote","endnote") || role(element,"doc-footnote","doc-endnote")) {target=element;break;}
        }
        StringBuilder out=new StringBuilder();write(target,out,0);
        if(out.toString().trim().isEmpty()) throw new IOException("脚注没有可显示的文字");
        return out.toString();
    }
    private static boolean tokens(String value,String... names) {
        return Arrays.stream(value.split("\\s+")).anyMatch(token->Arrays.asList(names).contains(token));
    }
    private static boolean type(Element element,String... names) {return tokens(element.getAttributeNS("http://www.idpf.org/2007/ops","type"),names);}
    private static boolean role(Element element,String... names) {return tokens(element.getAttribute("role"),names);}
    private static void write(Node node,StringBuilder out,int depth) throws IOException {
        if(depth>128) throw new IOException("脚注结构过于复杂");
        if(node.getNodeType()==Node.TEXT_NODE || node.getNodeType()==Node.CDATA_SECTION_NODE) {
            String text=node.getNodeValue();
            for(int i=0;i<text.length();i++) {
                char c=text.charAt(i);out.append(c=='&'?"&amp;":c=='<'?"&lt;":c=='>'?"&gt;":String.valueOf(c));
                if(out.length()>32768) throw new IOException("脚注过长，请查看原文");
            }
            return;
        }
        if(!(node instanceof Element)) return;
        Element element=(Element)node;
        String tag=element.getLocalName();if(tag==null) tag=element.getTagName();tag=tag.toLowerCase(Locale.ROOT);
        if(SKIP.contains(tag) || type(element,"backlink") || role(element,"doc-backlink")) return;
        boolean safe=SAFE.contains(tag);
        if(safe) out.append('<').append(tag).append('>');
        NodeList children=node.getChildNodes();for(int i=0;i<children.getLength();i++) write(children.item(i),out,depth+1);
        if(safe && !tag.equals("br")) out.append("</").append(tag).append('>');
        if(out.length()>32768) throw new IOException("脚注过长，请查看原文");
    }
}
