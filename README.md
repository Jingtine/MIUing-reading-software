# MIUing

Android 手机与平板离线阅读器，按 Apple Books 的书库组织与阅读体验开发。使用用户提供的猫咪 M 图标。

当前为 **0.6.4 开发版**，主适配 Android 16（编译/目标 API 36），兼容下限 Android 8（API 26）。以 EPUB/TXT 文字阅读为开发重点，以流畅、美观、轻量和接近 Apple Books 的阅读体验为目标。功能与视觉对齐进度见 [对照清单](docs/reference/parity.md)。

## 当前可用

- 系统文件选择器导入 EPUB、PDF、TXT；应用内保留副本、摘要去重、逐项导入结果。
- 主页继续阅读、书库网格/列表、标题/作者搜索与排序。
- 欲读清单、已读完、自定义藏书及书籍菜单。
- EPUB/TXT 翻页与滚动、EPUB3/NCX 层级目录和段落跳转、正文搜索、书签、正文锚点定位。
- 文字搜索结果定位并临时标出该章首次匹配，输入关键词不移动正文；支持跨行内元素、字面标点、空白与 Unicode 大小写处理，搜索后可返回来源。
- 文字阅读页使用右上关闭、章节状态与右下阅读菜单；目录、主题、搜索、书签、笔记和章节进度通过菜单进入。工具显隐保持相同正文尺寸和位置，页码按实际本章排版更新。
- 目录、书签、笔记、搜索、书内链接和菜单跳转后，可通过左下返回按钮逐次回到原文；最多保留当前会话的 20 条来源，旋转或 Activity 重建后继续有效。
- EPUB/TXT 卷页与快速淡入淡出效果，在“主题与设置”中选择；遵循系统关闭动画设置。向前跨章落在上一章末尾，超长章节按实际距离判断翻页边界。
- 快速连续翻页可中断旧动画，及时响应新操作；翻页路径、渐变及矩形复用，减少每帧创建绘制对象。
- 横向慢滑也能翻页；原地长按和文字选择继续避免意外翻页。
- 按原版参考调整的紧凑主题面板：字号、翻页、外观、亮度、3×2 六主题与独立“自定义”页面。
- 每个主题独立保存书籍/衬线/无衬线/等宽字体、粗体、行距、页边距、字符/词间距与两端对齐；支持完成保存、放弃及还原。字体实际覆盖 EPUB 段落和行内样式，原始模式可恢复书籍字体。
- 连续调整主题和排版保持原文位置，首次打开时的进度或目录片段也能跨初始布局更新恢复。
- EPUB/TXT 精确文字高亮（重复文字、跨段落）与笔记编辑。
- 当前章定位复用文字索引，内容变动时更新；批量处理高亮，字号和主题变化保留未变的高亮及选区，减少重复扫描和节点重建。
- PDF 逐页阅读、缩放、页码与内嵌目录跳转、正文搜索、长按拖选、文字高亮与笔记。
- PDF 手写标记、撤销及带批注副本导出；显示文件中已有的高亮和笔迹。
- 随窗口宽度切换导航布局，宽窗口侧栏可收起。

应用不需要网络权限，不接入书源、商店或账户。

## 构建

需要 JDK 17、Android SDK 36（platforms;android-36；现有 AGP 默认使用 build-tools;35.0.0）。

```powershell
$env:JAVA_HOME = '你的 JDK 17 安装路径'
# local.properties 中设置 sdk.dir=你的 SDK 路径（使用 / 分隔）
.\gradlew.bat :core:check :app:assembleDebug :app:lintDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`，当前约 25.9 MiB。该 APK 使用开发签名，供本地安装测试。

PDF 搜索入口在阅读页底部；长按文字后可高亮或添加笔记，顶部画笔可进入手写模式，导出按钮通过系统文件选择器保存带批注的副本。扫描 PDF 暂无 OCR。实现与验证记录见 [PDF 0.3 开发记录](docs/pdf-parity-0.3.md)。

卷页效果及手势修复记录见 [翻页 0.4 开发记录](docs/page-turns-0.4.md)。
连续操作与绘制优化见 [0.4.1 优化记录](docs/turn-optimization-0.4.1.md)。

文字排版与主题面板见 [0.5.0 开发记录](docs/theme-panel-0.5.md)，原版参考和仍需校准的部分见 [参考依据](docs/reference/theme-panel-reference.md)。

文字阅读控件、菜单、组件拆分与回归记录见 [0.6.0 开发记录](docs/reader-chrome-0.6.md)。

Android16 主适配与分页边界书签修复见 [0.6.1 开发记录](docs/android16-0.6.1.md)；原包为何不能整体转成 APK、哪些资源可参考见 [移植核对](docs/reference/bundle-portability.md)。

文字索引、高亮计算与受控测量见 [0.6.2 优化记录](docs/text-engine-0.6.2.md)。

跳转返回、滑块预览与错误恢复见 [0.6.3 开发记录](docs/reading-history-0.6.3.md)。

文字搜索结果定位与临时标记见 [0.6.4 开发记录](docs/search-location-0.6.4.md)。

本工作区额外在 `.tools` 中下载了 Gradle 与 Android SDK，均不纳入版本控制。若官方 Gradle 下载受网络限制，可使用本机已校验的 `.tools/gradle-8.13/bin/gradle.bat` 运行同样的任务。

## 测试

核心测试无需 Android SDK 或第三方测试依赖：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-core.ps1 -Jdk '你的 JDK 17 路径'
```

已连接模拟器/设备时运行 Android 测试：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

包含真实 TXT 导入、去重、原文件移除、进度持久化、原子备份恢复、大封面采样、EPUB 正文与图片渲染、禁用书内脚本、跨行内元素高亮、章节/阅读模式切换与 Activity 重建测试；PDF 覆盖文字、目录、搜索、旋转裁剪坐标、批注导出、原文件摘要不变，以及选择/标记/重建/导出的真实界面操作。

## 目录

- `app/`：Compose 界面、Android 文件导入、本地书库、WebView/PdfRenderer 阅读。
- `core/`：可独立测试的 EPUB/TXT 解析。
- `图标设计/`：用户原始图标。
- `docs/`：需求、原版资源摘录、对照进度与模拟器截图。
- `Books.app/`：用户原始参考包，保留在本地，不加入 Git 或 APK。

PDF 文本与批注处理使用 [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) 2.0.27.0，Apache-2.0 许可证与 NOTICE 随应用包含在 `app/src/main/assets/licenses/`。

目标远端：`git@github.com:Jingtine/MIUing-reading-software.git`。
