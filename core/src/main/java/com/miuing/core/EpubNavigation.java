package com.miuing.core;

import org.w3c.dom.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

final class EpubNavigation {
    static List<Publication.Navigation> read(Path root,Path nav,Path ncx,List<Publication.Chapter> chapters) {
        List<Publication.Navigation> entries=new ArrayList<>();
        if(nav!=null) try {
            Document doc=PublicationParser.xml(nav);
            NodeList nodes=doc.getElementsByTagNameNS("*","nav");
            for(int i=0;i<nodes.getLength();i++) {
                Element element=(Element)nodes.item(i);
                String type=element.getAttributeNS("http://www.idpf.org/2007/ops","type");
                if(Arrays.asList(type.split("\\s+")).contains("toc") || element.getAttribute("role").equals("doc-toc")) {
                    Element ol=child(element,"ol");if(ol!=null) html(root,nav.getParent(),ol,0,entries,chapters);
                    break;
                }
            }
        } catch(IOException ignored) { entries.clear(); }
        if(entries.stream().noneMatch(e->e.chapter>=0)) entries.clear();
        if(entries.isEmpty() && ncx!=null) try {
            Document doc=PublicationParser.xml(ncx);
            NodeList maps=doc.getElementsByTagNameNS("*","navMap");
            if(maps.getLength()>0) ncx(root,ncx.getParent(),(Element)maps.item(0),0,entries,chapters);
        } catch(IOException ignored) { entries.clear(); }
        if(entries.stream().noneMatch(e->e.chapter>=0)) entries.clear();
        return entries;
    }
    private static void html(Path root,Path parent,Element ol,int depth,List<Publication.Navigation> out,List<Publication.Chapter> chapters) {
        if(depth>32 || out.size()>=2000) return;
        for(Element li:children(ol,"li")) {
            Element label=child(li,"a");if(label==null) label=child(li,"span");
            if(label!=null) add(root,parent,label.getTextContent(),label.getAttribute("href"),depth,out,chapters);
            Element nested=child(li,"ol");if(nested!=null) html(root,parent,nested,depth+1,out,chapters);
        }
    }
    private static void ncx(Path root,Path parent,Element element,int depth,List<Publication.Navigation> out,List<Publication.Chapter> chapters) {
        if(depth>32 || out.size()>=2000) return;
        for(Element point:children(element,"navPoint")) {
            Element label=child(point,"navLabel"),content=child(point,"content");
            if(label!=null) add(root,parent,label.getTextContent(),content==null?"":content.getAttribute("src"),depth,out,chapters);
            ncx(root,parent,point,depth+1,out,chapters);
        }
    }
    private static void add(Path root,Path parent,String title,String href,int depth,List<Publication.Navigation> out,List<Publication.Chapter> chapters) {
        if(out.size()>=2000 || title.trim().isEmpty()) return;
        if(href.isEmpty()) { out.add(new Publication.Navigation(title.trim(),-1,"",depth));return; }
        try {
            URI uri=new URI(href);
            Path target=PublicationParser.relativeResource(root,parent,href);
            for(int i=0;i<chapters.size();i++) if(chapters.get(i).path.equals(target)) {
                out.add(new Publication.Navigation(title.trim(),i,uri.getFragment()==null?"":uri.getFragment(),depth));return;
            }
        } catch(Exception ignored) { /* Unknown/external destinations cannot navigate out of the book. */ }
    }
    private static Element child(Element parent,String name) {
        for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element && name.equals(n.getLocalName())) return (Element)n;
        return null;
    }
    private static List<Element> children(Element parent,String name) {
        List<Element> result=new ArrayList<>();
        for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element && name.equals(n.getLocalName())) result.add((Element)n);
        return result;
    }
}
