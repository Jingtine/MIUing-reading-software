package com.miuing.core;

import java.nio.file.Path;
import java.util.List;

public final class Publication {
    public final String title, author, format;
    public final List<Chapter> chapters;
    public final Path cover;
    public Publication(String title, String author, String format, List<Chapter> chapters, Path cover) {
        this.title = title; this.author = author; this.format = format;
        this.chapters = List.copyOf(chapters); this.cover = cover;
    }
    public static final class Chapter {
        public final String title;
        public final Path path;
        public Chapter(String title, Path path) { this.title = title; this.path = path; }
    }
}
