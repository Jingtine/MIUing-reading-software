# 0.7.7 EPUB 章内链接兼容

《荒原》公开样书的注释中有返回诗行的链接。在 0.7.6 中点击该链接，WebView 将 `#ln20` 当作章节资源目录的片段，阅读器没有执行跳转，也没有生成返回位置。

0.7.7 对正文中的纯片段链接使用当前章节查找目标，再交给既有阅读导航。有效目标跳到对应段落，点返回可回到原文。保留已有的脚注优先处理和跨文件链接路径。链接目标由 DOM 中实际存在的元素确认，正文和原始 EPUB 保持原样。

实现前核对了 Foliate-js 在当前章节解析链接并交给阅读器定位的做法。在 MIUing 中复用现有阅读导航与锚点，仅补足纯片段链接的入口。 [Foliate-js 源码](https://github.com/johnfactotum/foliate-js/blob/78914aef4466eb960965702401634c2cb348e9b1/view.js)

## 真实样书回归

测试使用固定版本 W3C／IDPF EPUB 样书《荒原》的 `wasteland-woff.epub`，从注释里的实际回链跳到 `ln20`，核对诗行出现在视口、章节未切换，随后核对返回的原文锚点。旧版在「没有产生阅读跳转」的断言处失败。样书从上游原始 Git blob 构建并做摘要校验，仅保留于本地测试语料。 [上游样书目录](https://github.com/IDPF/epub3-samples/tree/7651e2002b631e6577fadf7e9e0692fa6efb8746/30/wasteland-woff) · [语料说明](../../tools/epub-compatibility/README.md)

设备回归复用八本 EPUB 和五本 libmobi 样书，同时覆盖脚注、中文横排、搜索返回及转换后的阅读。这里的样书结果用于发现实际缺陷，兼容性阶段继续逐步扩充语料。 [MOBI 样本清单](https://github.com/bfabiszewski/libmobi/blob/906274205c11944b628da1c553b255acb1af7c55/tests/Makefile.am)

Android 16 和 Android 8 手机模拟器各通过 17 项，Android 16 平板窗口（1600 × 1200）个人包通过 4 项。核心 25 项和 JVM 10 项检查通过，Lint 为 0 错误、24 项警告。两版 APK 的包名、版本、原开发签名、正文字体和运行时脚本均已核对，安装包和摘要存于本地 `dist/0.7.7/`。

| 安装包 | SHA-256 |
| --- | --- |
| 常规版 | `c7507648bcdf8091958db42a76433bd2c6203b372777ef5720712423f458f0db` |
| 个人版 | `d60fb2021d4e8d7fd5ef2d8454f863af9cbd4e90744509f9530fe55a3d0b6c7f` |
