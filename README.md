# MIUing

Android 手机与平板离线阅读器，按 Apple Books 的书库组织与阅读体验开发。使用用户提供的猫咪 M 图标。

当前为 **0.1.0 开发版**，不是已完成的 Apple Books 完整复刻。功能与视觉对齐进度见 [对照清单](docs/reference/parity.md)。

## 当前可用

- 系统文件选择器导入 EPUB、PDF、TXT；应用内保留副本、摘要去重、逐项导入结果。
- 主页继续阅读、书库网格/列表、标题/作者搜索与排序。
- 欲读清单、已读完、自定义藏书及书籍菜单。
- EPUB/TXT 翻页与滚动、章节目录、正文搜索、书签、阅读位置保存。
- 六种阅读主题、字号和衬线/无衬线字体切换。
- EPUB/TXT 文字高亮与笔记；PDF 逐页阅读、缩放和页码跳转。
- 随窗口宽度切换导航布局，宽窗口侧栏可收起。

应用不需要网络权限，不接入书源、商店或账户。

## 构建

需要 JDK 17、Android SDK 35（platforms;android-35、build-tools;35.0.0）。

```powershell
$env:JAVA_HOME = '你的 JDK 17 安装路径'
# local.properties 中设置 sdk.dir=你的 SDK 路径（使用 / 分隔）
.\gradlew.bat :core:check :app:assembleDebug :app:lintDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。该 APK 使用开发签名，供本地安装测试。

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

包含真实 TXT 导入、去重、原文件移除、进度持久化、原子备份恢复、大封面采样、EPUB 正文与图片渲染、禁用书内脚本、跨行内元素高亮、章节/阅读模式切换与 Activity 重建测试，以及 PDF 渲染测试。

## 目录

- `app/`：Compose 界面、Android 文件导入、本地书库、WebView/PdfRenderer 阅读。
- `core/`：可独立测试的 EPUB/TXT 解析。
- `图标设计/`：用户原始图标。
- `docs/`：需求、原版资源摘录、对照进度与模拟器截图。
- `Books.app/`：用户原始参考包，保留在本地，不加入 Git 或 APK。

目标远端：`git@github.com:Jingtine/MIUing-reading-software.git`。
