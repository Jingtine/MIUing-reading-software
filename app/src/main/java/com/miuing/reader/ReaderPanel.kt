package com.miuing.reader

enum class ReaderPanel(val title: String) {
    Contents("目录"),
    Bookmarks("书签"),
    Notes("高亮标记和笔记"),
    Search("搜索"),
    ReadingOptions("阅读设置"),
    GoToPage("前往页码"),
    AddNote("添加笔记"),
    EditNote("编辑笔记");

    val editsNote: Boolean get() = this == AddNote || this == EditNote
}

data class ReaderPageInfo(val current: Int, val total: Int)
