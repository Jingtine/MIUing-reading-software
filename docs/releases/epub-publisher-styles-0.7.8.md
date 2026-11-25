# 0.7.8 EPUB 原书样式与语言

两本固定版本的 W3C／IDPF 公开样书复现了 EPUB 正文在 WebView 中按 HTML 解析时丢失的语义。

| 样书 | 0.7.7 中的表现 | 0.7.8 的处理 |
| --- | --- | --- |
| `childrens-media-query`（《Abroad》） | 出版者的 `epub:type` CSS 选择器没有匹配章节。两层背景插图和宽屏分栏规则未应用。 | 渲染时补回 EPUB 属性的命名空间，让原书样式匹配；也保留原来的属性写法供脚注识别等路径使用。 |
| `internallinks` | 从目录可进入日文正文，`xml:lang="ja"` 在 HTML 布局中没有被识别为正文语言。 | 对仅声明 `xml:lang` 的 HTML 元素提供对应的 `lang` 属性，让浏览器识别正文语言。出版者显式提供的 `lang` 保持原值。 |

修复只在渲染初始化时补充属性，原书和正文内容保持原样。样书回归通过实际目录打开日文正文，并检查 HTML 语言匹配、两层背景图样式及横屏分栏。背景图样式和日文语言断言在 0.7.7 分别复现失败。

[插图样书与样式](https://github.com/IDPF/epub3-samples/tree/7651e2002b631e6577fadf7e9e0692fa6efb8746/30/childrens-media-query) · [日文样书正文](https://github.com/IDPF/epub3-samples/blob/7651e2002b631e6577fadf7e9e0692fa6efb8746/30/internallinks/OEBPS/0001.xhtml) · [HTML 语言规范](https://html.spec.whatwg.org/multipage/dom.html#the-lang-and-xml:lang-attributes)

## 语料与范围

新增 `childrens-media-query` 后，本地独立语料包含 9 本 EPUB、6 份独立字体对照。源版本、打包方式和许可说明沿用现有流程，语料留在本地测试目录。 [语料构建方法](../../tools/epub-compatibility/README.md)

## 发布验证

- Android 16 和 Android 8 手机模拟器各通过 20 项回归，包括样书导入、字体、脚注、中文排版及搜索定位。Android 8 另以加强后的断言确认两张背景资源均已读取。
- Android 16 平板窗口个人包通过 5 项。插图样书确认运行在横屏宽窗口，出版者的两栏规则和两张背景资源均生效。
- 核心 25 项、JVM 10 项通过，Lint 0 错误、24 项警告。常规和个人 APK 的开发签名、实际版本、字体与脚本均完成检查。

| 本地安装包 | SHA-256 |
| --- | --- |
| 常规版 | `c66ce7423ce48a547ae8ca1384939caefcd2771f98e3ca448f33c5913893d000` |
| 个人版 | `56374837a5e825f845e200b0c3c52a96eb1fcba87c276ee910d72d1ba1a6325d` |
