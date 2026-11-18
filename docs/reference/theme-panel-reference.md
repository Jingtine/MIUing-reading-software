# 主题面板参考依据

本轮以 EPUB/TXT 文字阅读为重点，参考固定版本的 [Apple iPhone iOS 18 阅读指南](https://support.apple.com/en-gb/guide/iphone/iphc1af7c57/18.0/ios/18.0)，避免将不同系统版本的界面混作一个基准。

指南内图片为 `Art/S1002_bksThemesSettings_iPhone.png`，公开[图片地址](https://help.apple.com/assets/683A54F5227CBBDB98051B96/683A54FC545676DF600F97D4/en_US/e34cdd0ec9e55209214b999a8bc7cce8.png)。本地检查原图 886×1016：顶部标题与关闭、字号 A/A、翻页样式、外观、亮度、分隔线、3×2 主题卡片、底部 Customize。六张卡片顺序为 Original / Quiet / Paper；Bold / Calm / Focus。详细排版进入单独的 Customize 页面。

卡片空白区域与面板背景取样后采用以下近似色。它们是截图取样值，并非 Apple 官方设计参数；压缩、显示和系统版本均可能影响颜色。

| 区域 | RGB |
| --- | --- |
| 原始、粗体 | 255, 255, 255 |
| 安静 | 74, 74, 77 |
| 纸张 | 238, 237, 237 |
| 平静 | 241, 226, 201 |
| 专注 | 255, 252, 243 |
| 面板背景 | 243, 243, 243 |

中文文案摘录自用户提供的 Mac Books 7.6（6387）`Localizable.loctable` 的 `zh_CN` 字段，见 [labels](apple-books-labels.json)。Mac 包的系统目标为 macOS 15.8；该资源只能帮助核对文字，不能证明 Android 手机或平板布局与原版一致。

已核对主面板的层级、排列、卡片顺序和关闭入口。Android 图标、系统字体、阴影及具体尺寸仍为近似实现。字体选择提供书籍原字体和 Android 衬线、无衬线、等宽字体；不声称复现 Apple 专有字体或六主题全部排版参数。

未取得同版本 iPad 原版设置面板的可用截图。宽屏采用右下方有界面板，实际验证了可用性和边界，但其位置、宽度仍待原版 iPad 截图校准。原版截图只保留在忽略的 `.tools` 供本地对照；仓库截图为 MIUing 自身运行画面。
