package com.miuing.core;

import java.nio.file.Path;
import java.util.List;

public final class Publication {
    public final String title, author, format;
    public final List<Chapter> chapters;
    public final Path cover;
    public final List<Navigation> navigation;
    public Publication(String title, String author, String format, List<Chapter> chapters, Path cover) {
        this(title,author,format,chapters,cover,chapterNavigation(chapters));
    }
    public Publication(String title,String author,String format,List<Chapter> chapters,Path cover,List<Navigation> navigation) {
        this.title = title; this.author = author; this.format = format;
        this.chapters = List.copyOf(chapters); this.cover = cover;
        this.navigation=List.copyOf(navigation);
    }
    private static List<Navigation> chapterNavigation(List<Chapter> chapters) {
        java.util.ArrayList<Navigation> entries=new java.util.ArrayList<>();
        for(int i=0;i<chapters.size();i++) entries.add(new Navigation(chapters.get(i).title,i,"",0));
        return entries;
    }
    public static final class Navigation {
        public final String title,fragment;
        public final int chapter,depth;
        public Navigation(String title,int chapter,String fragment,int depth) {
            this.title=title;this.chapter=chapter;this.fragment=fragment;this.depth=depth;
        }
    }
    public static final class Chapter {
        public final String title;
        public final Path path;
        public Chapter(String title, Path path) { this.title = title; this.path = path; }
    }
}
