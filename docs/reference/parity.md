# Apple Books 对照进度

基准：用户提供的 macOS Apple Books 7.6（6387）资源，以及 Apple 官方对应平台界面说明。

本地是 Windows；尚未在 macOS 或 iPad 上运行原版逐页截图比较。下述“已实现”指 MIUing 可用功能，不代表已完成像素级或全部行为等价。

| 范围 | 当前状态 |
| --- | --- |
| MIUing 名称、用户猫咪图标、自适应 Android 启动图标 | 已接入 |
| 主页、继续阅读、书库网格/列表、搜索及排序 | 已实现，布局仍需对原版逐页校准 |
| 欲读清单、已读完、自定义藏书、书籍菜单 | 已实现 |
| EPUB/PDF/TXT 本地导入、私有副本、去重 | 已实现 |
| EPUB/TXT 翻页与滚动、书签、进度、字号/字体/主题 | 已实现基础行为 |
| 六种主题 | 原版名称已核对；可调字体、行距、页边距、字间距与应用内亮度，预设细节尚未完全对齐 |
| 高亮与笔记 | 新高亮保存精确文本范围，支持重复文字及跨段落，笔记可编辑并前往原文；旧高亮按文字匹配兼容 |
| 正文搜索 | EPUB/TXT 按章节检索；PDF 按页检索与结果跳转（最多显示 100 条） |
| EPUB 目录 | 支持 EPUB3 nav、EPUB2 NCX 层级和片段跳转；缺失/损坏/不可导航时回退到章节 |
| PDF | 页面渲染、跳页、缩放与平移、内嵌目录、文本选择、高亮/笔记、手写标记与撤销、带批注副本导出；显示已有高亮与笔迹 |
| 手机/平板布局 | 使用实际窗口宽度适配；平板宽窗口支持双栏分页；模拟器测试与截图见 previews |
| 仿真翻页 | 已有基于页面快照的卷页与快速淡入淡出；系统关闭动画时直接翻页。卷曲形变、手指跟随与跨章转场仍待对照完善 |
| 精细主题排版、自定义字体 | 待对照实现；不将其作为永久删减项 |
| MOBI、AZW3 | 按约定放到后续格式兼容阶段 |
| Apple 账户、书城、iCloud | 不在已确认的本地离线范围内 |

## 当前边界

- 可重排版、无 DRM 的常规 EPUB 为主要兼容目标；不执行书内脚本，不加载外部网络资源。
- EPUB 解压限制：单文件 16 MB、总计 256 MB、最多 10000 个条目；TXT 最大 32 MB，按段分章。
- EPUB/TXT 阅读位置与书签按章节和正文文本锚点保存，调整排版时定位原文；旧记录和无可定位正文的内容保留进度回退。
- PDF 搜索/选择需要可提取文字，暂不提供扫描件 OCR；既有高亮/笔迹只读显示，形状、签名、橡皮擦和复杂文字排布仍待完善。
- 当前卷页为触发后播放的近似效果；未实现拖拽跟随或中途回弹。极快连点响应仍需微调。
- 测试使用 Android 15/API35 手机与平板窗口、Android 8/API26 Google APIs 模拟器；尚未在实体设备验证。
- 开发包无发布签名，尚未发布到应用商店。

## 来源

- 本地 `Books.app/Contents/Info.plist` 与 `Resources/Localizable.loctable`，提取的小范围页面名称见 apple-books-labels.json。
- [Apple Books Mac 阅读说明](https://support.apple.com/en-sa/guide/books/ibks5f526382/mac)。
- [iPad 阅读说明](https://support.apple.com/zh-cn/guide/ipad/ipadc8494b6b/ipados)。
- [iPhone 阅读与翻页样式说明](https://support.apple.com/zh-cn/guide/iphone/-iphc1af7c57/ios)。
- [iPadOS 18 标签栏和边栏设计](https://www.apple.com.cn/newsroom/2024/09/ipados-18-is-now-available-taking-ipad-to-the-next-level/)。
