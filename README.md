# MIUing

Android 手机与平板离线阅读器，按 Apple Books 的书库组织与阅读体验开发。使用用户提供的猫咪 M 图标。

当前为 **0.7.5 开发版**，主适配 Android 16（编译/目标 API 36），兼容下限 Android 8（API 26）。以可重排文字阅读为开发重点，以流畅、美观、轻量和接近 Apple Books 的阅读体验为目标。功能与视觉对齐进度见 [对照清单](docs/reference/parity.md)。

## 当前可用

- 文字全书搜索按命中逐条显示上下文，支持跨章节前后导航、旋转后重建结果、关闭返回搜索前原文。复用现有DOM匹配器与分页预览；详见[搜索记录](docs/book-search-0.7.4.md)。

- 系统打开/分享接入：单本导入后阅读、多本汇总；支持冷启动和已运行时接收，旋转不重复导入。详见 [0.7.3 接入记录](docs/external-import-0.7.3.md)。

- 系统文件选择器导入 EPUB、PDF、TXT、MOBI、AZW、AZW3、FB2；应用内保留原始副本、摘要去重、逐项导入结果。
- MOBI/AZW/AZW3/FB2 使用固定版本 Foliate 开源解析模块，离线生成私有 EPUB 阅读副本，共用目录、搜索、批注、脚注、主题和阅读进度。支持未加密 MOBI/KF8；KFX、DRM 和 Print Replica 暂不支持。新增转换格式原文件上限64MiB，细节见 [0.7.0 格式记录](docs/multiformat-0.7.0.md)。
- 书库备份：导出书籍、阅读副本、封面、进度、书签、批注及藏书；恢复时仅补入缺失图书，保留已有阅读记录。[备份说明](docs/library-backup-0.7.1.md)。
- EPUB 内嵌字体支持 IDPF/Adobe 标准字体混淆，复用 Foliate 算法，保留原书文件；公开 OTF/WOFF 样书验证与范围见 [0.7.2 兼容性记录](docs/epub-compatibility-0.7.2.md)。
- 主页继续阅读、书库网格/列表、标题/作者搜索与排序。封面保留完整比例并底边对齐，书卡显示作者和真实阅读状态；退出阅读保留书库标签、筛选、排序、显示方式与滚动状态。
- 欲读清单、已读完、自定义藏书及书籍菜单。
- EPUB/TXT 翻页与滚动、EPUB3/NCX 层级目录和段落跳转、正文搜索、书签、正文锚点定位。
- EPUB 结构化脚注就地弹层，支持同章及跨文件（含非 spine 尾注），继承当前正文字体和主题；关闭/系统返回不跳章节，不改变正文页数。
- 文字搜索按章节显示结果，选中后可通过“上一处／下一处”查看该章每处匹配及总数；输入关键词不移动正文，支持跨行内元素、字面标点、空白与 Unicode 大小写处理，连续查看后可一次返回搜索来源。
- EPUB/TXT 阅读菜单的“阅读设置”可控制阅读时延长亮屏：操作后保持 10 分钟，离开阅读或切后台时恢复系统策略，开关全局保存。
- 文字阅读页使用右上关闭、章节状态与右下阅读菜单；目录、主题、搜索、书签和笔记通过菜单进入。工具显隐保持相同正文尺寸和位置；分页模式支持整本页码及跨章节进度跳转，后台计数完成前明确显示本章范围。平板双栏逐页计数，末尾空白不计入总页数。
- 主题自定义布局增加可选中文排版：首行缩进、段间距及浏览器严格中文断行；关闭时恢复书籍段落样式，保留原文和阅读锚点。
- 目录、书签、笔记、搜索、书内链接和菜单跳转后，可通过左上返回入口逐次回到原文；最多保留当前会话的 20 条来源，旋转或 Activity 重建后继续有效。
- 阅读页按固定原版截图校准返回位置、菜单图形和状态层级；无书籍字体时回退到系统衬线，保留发布者字体。状态文字满足各主题最低对比度，系统栏随阅读背景明暗并在退出后还原。
- EPUB/TXT 卷页、iBook 风格平移与快速淡入淡出效果，在“主题与设置”中选择；遵循系统关闭动画设置。向后跨章落在上一章末尾，超长章节按实际距离判断翻页边界。
- 快速连续翻页可中断旧动画，及时响应新操作；翻页路径、渐变及矩形复用，减少每帧创建绘制对象。
- 卷页与平移跟随水平拖动，松手完成或回弹；支持相邻章节预览。拖动预览不改变阅读进度，取消、重排和页面重建不会误翻页。
- 横向慢滑也能翻页；原地长按和文字选择继续避免意外翻页。
- 按原版参考调整的紧凑主题面板：字号、翻页、外观、亮度、3×2 六主题与独立“自定义”页面。
- 每个主题独立保存书籍/衬线/无衬线/等宽字体、粗体、行距、页边距、字符/词间距与两端对齐；支持完成保存、放弃及还原。字体实际覆盖 EPUB 段落和行内样式，原始模式可恢复书籍字体。
- 连续调整主题和排版保持原文位置，首次打开时的进度或目录片段也能跨初始布局更新恢复。
- EPUB/TXT 精确文字高亮（重复文字、跨段落）与笔记编辑；高亮和笔记支持按章节整理、类型/关键词筛选，并将筛选结果导出为 TXT 或 Markdown，见 [0.7.5 记录](docs/notes-export-0.7.5.md)。
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

默认常规构建约 99.3 MiB，含六款用户指定的正文字库。工作区个人构建约 115.1 MiB，另含本地华文楷体、楷体；使用 `-PpersonalFonts=true` 显式开启，字库目录不上传仓库。设置及构建说明见 [0.6.8 新增字体](docs/reading-fonts-0.6.8.md)。APK 使用开发签名，供本地安装测试。

PDF 搜索入口在阅读页底部；长按文字后可高亮或添加笔记，顶部画笔可进入手写模式，导出按钮通过系统文件选择器保存带批注的副本。扫描 PDF 暂无 OCR。实现与验证记录见 [PDF 0.3 开发记录](docs/pdf-parity-0.3.md)。

卷页效果及手势修复记录见 [翻页 0.4 开发记录](docs/page-turns-0.4.md)。
连续操作与绘制优化见 [0.4.1 优化记录](docs/turn-optimization-0.4.1.md)。

文字排版与主题面板见 [0.5.0 开发记录](docs/theme-panel-0.5.md)，原版参考和仍需校准的部分见 [参考依据](docs/reference/theme-panel-reference.md)。

文字阅读控件、菜单、组件拆分与回归记录见 [0.6.0 开发记录](docs/reader-chrome-0.6.md)。

Android16 主适配与分页边界书签修复见 [0.6.1 开发记录](docs/android16-0.6.1.md)；原包为何不能整体转成 APK、哪些资源可参考见 [移植核对](docs/reference/bundle-portability.md)。

文字索引、高亮计算与受控测量见 [0.6.2 优化记录](docs/text-engine-0.6.2.md)。

跳转返回、滑块预览与错误恢复见 [0.6.3 开发记录](docs/reading-history-0.6.3.md)。

文字搜索结果定位与临时标记见 [0.6.4 开发记录](docs/search-location-0.6.4.md)。

阅读亮屏设置、生命周期与超时验证见 [0.6.5 开发记录](docs/reading-sleep-0.6.5.md)。

阅读页视觉、字体回退和系统栏校准见 [0.6.6 对照记录](docs/reader-visual-0.6.6.md)。

界面跟随 Android 系统字体，正文新增不明体、思源宋体、方正晶宋、京華老宋体四款离线字库，并保留书籍字体选择；范围和验证见 [0.6.7 正文字体](docs/reading-fonts-0.6.7.md)。

追加思源黑体 CN、霞鹜文楷，当前常规构建共六款内置正文字体；本地个人构建加入华文楷体、楷体，共八款，见 [0.6.8 新增字体](docs/reading-fonts-0.6.8.md)。

跟手卷页、跨章预览和输入中断处理见 [0.6.9 翻页记录](docs/interactive-turn-0.6.9.md)。

整本分页、缓存、全书进度和可选中文排版见 [0.6.10 开发记录](docs/pagination-typography-0.6.10.md)。

本章逐项搜索导航、关闭时实时位置和回归见 [0.6.11 开发记录](docs/search-navigation-0.6.11.md)。

脚注弹层、完整封面与返回书库状态见 [0.6.12 开发记录](docs/footnotes-library-0.6.12.md)。

iBook 风格平移、绘制确认与兼容性验证见 [0.6.13 开发记录](docs/ibook-slide-0.6.13.md)。

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

手势状态机的输入顺序回归可独立运行：`node --test tools/test-reader-gestures.cjs`。

整本分页和中文排版回归覆盖实际单/双栏页数、延迟图片、缓存与布局失效、跨章进度、正文锚点和出版样式恢复；最终设备矩阵见 [0.6.10 验证记录](docs/pagination-typography-0.6.10.md)。

包含真实 TXT 导入、去重、原文件移除、进度持久化、原子备份恢复、大封面采样、EPUB 正文与图片渲染、禁用书内脚本、跨行内元素高亮、章节/阅读模式切换与 Activity 重建测试；PDF 覆盖文字、目录、搜索、旋转裁剪坐标、批注导出、原文件摘要不变，以及选择/标记/重建/导出的真实界面操作。

## 目录

- `app/`：Compose 界面、Android 文件导入、本地书库、WebView/PdfRenderer 阅读。
- `core/`：可独立测试的 EPUB/TXT 解析。
- `图标设计/`：用户原始图标。
- `docs/`：需求、原版资源摘录、对照进度与模拟器截图。
- `Books.app/`：用户原始参考包，保留在本地，不加入 Git 或 APK。

PDF 文本与批注处理使用 [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) 2.0.27.0，Apache-2.0 许可证与 NOTICE 随应用包含在 `app/src/main/assets/licenses/`。

iBook 风格平移动效参考 [Peyilo/libreadview](https://github.com/Peyilo/libreadview) 的 IBookSlideEffect，在现有临时位图层适配；MIT 许可随应用包含。三个参考项目的适用性见[源码核验记录](docs/reference/reader-projects-2026-11-23.md)。

新增格式解析复用 [Foliate-js](https://github.com/johnfactotum/foliate-js) 的 MOBI/KF8、FB2 模块及 fflate；固定源代码和 MIT 许可随项目保留，许可随 APK 分发。仅打包解析与导入适配，不包含其整套界面；无需网络或在线转换服务。转换脚本[重建说明](tools/import-converter/README.md)。

目标远端：`git@github.com:Jingtine/MIUing-reading-software.git`。

书库备份使用 [Zip4j](https://github.com/srikanth-lingala/zip4j/releases/tag/v2.11.6) 2.11.6（Apache-2.0），通过系统文件选择器读写 ZIP/Zip64。许可证随 APK 分发于 assets/licenses/zip4j-LICENSE.txt。
