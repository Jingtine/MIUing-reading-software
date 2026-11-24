# 笔记整理与导出 0.7.5

本轮在现有高亮、笔记编辑和原文定位基础上增加整理与可读文件导出。继续使用 Kotlin、Compose 和系统文件选择器，未增加依赖或权限。

## 使用

阅读菜单 → 高亮标记和笔记：

- 按章节分组并按正文位置排列；旧笔记没有位置时保留原有相对顺序。
- “全部／仅高亮／有笔记”筛选，搜索摘录、批注或章节名称。
- “导出筛选结果”选择 TXT 或 Markdown，保存当前筛选的全部条目。
- 文件包含书名、作者（有记录时）、章节、摘录及批注。TXT 使用 UTF-8；Markdown 转义用户文字中的标记和 HTML 符号，逐行引用摘录。
- 原有前往原文、编辑和移除入口继续使用原来的记录与定位。

导出范围为当前书籍的文字高亮和笔记。PDF 记录以页码分组；手写笔迹仍使用原有 PDF 副本导出。书签保留原有独立列表。记录中没有笔记创建时间，不在文件中生成虚构日期。

## 复用与结构

先核对 GitHub，再按项目现状采用其做法：

- [KOReader Markdown 模板](https://github.com/koreader/koreader/blob/master/plugins/exporter.koplugin/template/md.lua)：书名、作者、章节组织，摘录与批注分开。
- [Foliate annotations](https://github.com/johnfactotum/foliate/blob/gtk4/src/annotations.js)：过滤与多格式文本导出的交互参考。
- [Readium 高亮列表](https://github.com/readium/kotlin-toolkit/blob/develop/test-app/src/main/java/org/readium/r2/testapp/outline/HighlightsFragment.kt)：章节、摘录、批注分开呈现和回到原文。

Readium 为 BSD-3-Clause；Foliate 主程序为 GPLv3，KOReader 为 AGPLv3。本轮采用交互与文件组织方式，没有复制这些项目的程序源码，也没有为简单文件输出引入完整阅读工具包。

`NotesDocument.kt` 负责纯数据筛选和文件格式，`ReaderNotes.kt` 负责呈现，`NoteExportSession.kt` 保存点击导出时的不可变快照。`MainActivity` 持有系统选择器，旋转或切换书籍不改变待保存的内容。进程重启后丢失的请求会提示重新导出，不以别的书籍填补内容。

备份原有的暂存、复制、失败清理实现提取为 `DocumentExport`，笔记和书库备份共用。先完成本地文件，再写入目标；失败或取消时清理暂存和未完成目标（文件提供方支持删除时）。导出不修改书籍文件、阅读进度或笔记记录。

## 验证

- 核心检查 25 项、JVM 测试 10 项通过；Lint 0 错误，24 项既有警告。
- Android 8 手机最终常规包：9/9，50.849 秒。覆盖筛选/搜索/编辑/移除、实际 TXT/Markdown 保存、旋转后的不可变快照、取消、进程中断提示和写入/关闭失败清理。
- Android 16 平板窗口（1600×1200，160 dpi）最终个人包：9/9，51.692 秒。
- 精确文件名输入定位更新后，Android 16 平板四项实际导出测试再次通过（42.069 秒）。
- 前期 Android 16/8 各 15 项功能与搜索/备份回归通过；最终小屏问题单独复现失败后修复，并补充实际摘录可见断言。笔记面板直接展开，防止筛选控件占满半展开高度。
- Android 8 横屏系统选择器可能同时出现输入法/搜索编辑框，测试按实际 `android:id/title` 文件名资源定位，避免改错输入框。实际文件内容也通过系统 Downloads 读取验证。
- 独立代码审查通过；两包的原有签名、脚本、许可与字体内容逐项核对，未混入样书或测试资源。尚未在实体设备执行本轮验证。

[手机截图](previews/phone-notes-0.7.5.png) · [平板截图](previews/tablet-notes-0.7.5.png)

| 安装包 | 字节数 | SHA-256 |
| --- | ---: | --- |
| 常规版（六款正文字库） | 104075120 | `fe08ba17f5e5d036f6a3074f71559b8242e29f6017144bd5daab35130c53583b` |
| 个人版（八款正文字库） | 120728982 | `3f0f88d203b12a8e870c20284756f0a3eafaba783b8c9ce784d6e8a92d375f7d` |
