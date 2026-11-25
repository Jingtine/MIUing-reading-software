# MIUing 1.0 各阶段的 GitHub 参考仓库

2026-11-25 核对。阶段编号对应 [1.0 路线图](../ROADMAP.md)。链接指向核对过的源码、样例或构建配置。这里记录参考结论，各阶段的接入和实测仍按路线图执行。

## 1. 大书搜索与性能

- **首选 `johnfactotum/foliate-js`。** `view.js` 按章节产出命中项和进度，`search.js` 生成命中上下文。MIUing 已参考其上下文算法，这次重点参考分批返回，沿用现有 DOM 匹配和原文定位。MIT。[分批返回](https://github.com/johnfactotum/foliate-js/blob/78914aef4466eb960965702401634c2cb348e9b1/view.js) · [搜索匹配](https://github.com/johnfactotum/foliate-js/blob/78914aef4466eb960965702401634c2cb348e9b1/search.js) · [许可](https://github.com/johnfactotum/foliate-js/blob/78914aef4466eb960965702401634c2cb348e9b1/LICENSE)
- **对照 `readium/kotlin-toolkit`。** `SearchIterator` 定义了分批获取和关闭的接口，`StringSearchService` 按阅读顺序提取资源正文。可参考搜索任务的生命周期设计。它的提取方式与 MIUing 的实际 DOM 匹配不同，命中位置须用本项目回归验证。BSD-3-Clause。[接口](https://github.com/readium/kotlin-toolkit/blob/89b96c20c447d0aec17db57a06456b7decffde4c/readium/shared/src/main/java/org/readium/r2/shared/publication/services/search/SearchService.kt) · [实现](https://github.com/readium/kotlin-toolkit/blob/89b96c20c447d0aec17db57a06456b7decffde4c/readium/shared/src/main/java/org/readium/r2/shared/publication/services/search/StringSearchService.kt) · [许可](https://github.com/readium/kotlin-toolkit/blob/89b96c20c447d0aec17db57a06456b7decffde4c/LICENSE)
- **测量参考 `android/performance-samples`。** 用启动和帧耗时样例设计可重复的测量。首批结果、完整搜索和重复搜索需加 MIUing 自己的计时点。Apache-2.0。[启动测量](https://github.com/android/performance-samples/blob/05c8130adfd2d7e35d4cd25033cb4f89b1008c1f/MacrobenchmarkSample/macrobenchmark/src/main/kotlin/com/example/macrobenchmark/benchmark/startup/StartupBenchmark.kt) · [帧耗时测量](https://github.com/android/performance-samples/blob/05c8130adfd2d7e35d4cd25033cb4f89b1008c1f/MacrobenchmarkSample/macrobenchmark/src/main/kotlin/com/example/macrobenchmark/benchmark/frames/FrameTimingBenchmark.kt) · [许可](https://github.com/android/performance-samples/blob/05c8130adfd2d7e35d4cd25033cb4f89b1008c1f/LICENSE)

先实现已找到的结果可打开，再用同一批长篇样本测量重复搜索，决定缓存是否值得加。上述源码核对无法证明任何缓存方案在 MIUing 上的收益。

## 2. 文字书兼容与排版

- **`IDPF/epub3-samples`。** 在现有八本语料基础上补测目录、图片、字体、脚注及复杂 EPUB 样式。样书默认 CC BY-SA 3.0，例外逐本核对，沿用本地语料流程。[样书仓库和许可](https://github.com/IDPF/epub3-samples/blob/7651e2002b631e6577fadf7e9e0692fa6efb8746/README.md) · [MIUing 语料流程](../../tools/epub-compatibility/README.md)
- **`bfabiszewski/libmobi`。** 已核对样本清单，优先选无 DRM 的 NCX 目录、CP1252、Unicode/HUFF 和混合媒体样书，核对现有转换器的输出。参考范围是测试语料，样书许可单独核对。解析库源码采用 LGPL-3.0-or-later。[样本清单](https://github.com/bfabiszewski/libmobi/blob/906274205c11944b628da1c553b255acb1af7c55/tests/Makefile.am) · [源码许可说明](https://github.com/bfabiszewski/libmobi/blob/906274205c11944b628da1c553b255acb1af7c55/README.md#license)
- **`w3c/clreq`。** 参考行首行尾禁则、标点宽度调整和混排规则，挑选适用于简体中文横排的案例，结合旧版 Android WebView 实测。[中文排版需求源码](https://github.com/w3c/clreq/blob/c22e7d02087374625d7737639be4a88e1abc9302/index.html)

## 3. Apple Books 视觉与交互

- **`Peyilo/libreadview`。** 阅读手感优先看已有的平移动画、底页视差及遮光。MIUing 已按 MIT 许可适配相关几何关系，后续用已有翻页实现继续校准。[平移动效源码](https://github.com/Peyilo/libreadview/blob/ceacbf3e172de6dad81c54a4ec336a2bd5891873/app/libreadview/src/main/java/org/peyilo/libreadview/turning/IBookSlideEffect.kt) · [许可](https://github.com/Peyilo/libreadview/blob/ceacbf3e172de6dad81c54a4ec336a2bd5891873/LICENSE)
- **`bekhruzbekswe/reada`。** 阅读菜单、主题入口和再入阅读的操作可作对照。它使用 Expo/React Native 与 EPUB CFI，适合对照流程。MIUing 使用自己的 Compose 界面、图标与阅读定位。MIT。[阅读页](https://github.com/bekhruzbekswe/reada/blob/1b976cc6c0bf40f35a2257b35af86dbdef4b5b68/src/app/reader/%5Bid%5D.tsx) · [许可](https://github.com/bekhruzbekswe/reada/blob/1b976cc6c0bf40f35a2257b35af86dbdef4b5b68/LICENSE)

原版参考用于核对阅读时的视觉层级、留白和控件操作。最终设计保留 MIUing 自己的视觉识别。[现有对照清单](parity.md)

## 4. 异常恢复与数据可靠性

- **`androidx/androidx`。** `AtomicFile` 展示临时写入、同步、提交及失败回退，并把并发访问保护留给调用方。MIUing 已用框架版 `AtomicFile`，重点检查写入失败及导入、备份恢复、阅读位置更新的协调。Apache-2.0。[源码和类注释](https://github.com/androidx/androidx/blob/e64ad8b6ace2f2208f6ea4f4d07882e4517a3bfd/core/core/src/main/java/androidx/core/util/AtomicFile.java)
- **`srikanth-lingala/zip4j`。** 项目现用 2.11.6，继续参考流式读写和 ZIP64。预检、完整性校验、暂存和书库合并回滚由 MIUing 自己验证。Apache-2.0。[版本与用法](https://github.com/srikanth-lingala/zip4j/blob/v2.11.6/README.md) · [许可](https://github.com/srikanth-lingala/zip4j/blob/v2.11.6/LICENSE) · [现有备份记录](../releases/library-backup-0.7.1.md)

## 5. 真机、平板与无障碍

- **`android/compose-samples` 的 Jetcaster。** `Home.kt` 根据实际窗口宽高调整单栏和多栏，适合参考分屏与矮横屏判断。Apache-2.0。[自适应布局源码](https://github.com/android/compose-samples/blob/09aae2013b3e5897f9679e2b7539f505f4f6f7f6/Jetcaster/mobile/src/main/java/com/example/jetcaster/ui/home/Home.kt) · [许可](https://github.com/android/compose-samples/blob/09aae2013b3e5897f9679e2b7539f505f4f6f7f6/LICENSE)
- **`android/performance-samples`。** 使用真实设备测阅读启动和页面操作的帧耗时。Android 8 继续用现有仪器测试验证兼容性。无障碍另外以真机 TalkBack 检查 Compose 控件及 WebView 正文的焦点顺序、选字和返回。[测量样例](https://github.com/android/performance-samples/tree/05c8130adfd2d7e35d4cd25033cb4f89b1008c1f/MacrobenchmarkSample) · [Android 无障碍测试指南](https://developer.android.com/develop/ui/compose/accessibility/testing)

## 6. 工程与 1.0 发布收尾

- **`android/nowinandroid`。** 参考构建、JVM 测试、Lint 和上传测试报告的任务组织，按 MIUing 实际 Gradle 任务配置。Apache-2.0。[构建工作流](https://github.com/android/nowinandroid/blob/2982e63cb691ad49cd009aab43dc206fee6699c2/.github/workflows/Build.yaml) · [许可](https://github.com/android/nowinandroid/blob/2982e63cb691ad49cd009aab43dc206fee6699c2/LICENSE)
- **`gradle/actions`。** `setup-gradle` 提供依赖缓存与 Wrapper 校验，可作为持续集成入口。仓库主要采用 MIT，另含分别授权的内置外部组件，详情以许可页为准。[配置文档](https://github.com/gradle/actions/blob/c0152e9a6b68fc2b5c1f256331f2e8f82b68ad62/docs/setup-gradle.md) · [许可](https://github.com/gradle/actions/blob/c0152e9a6b68fc2b5c1f256331f2e8f82b68ad62/LICENSE)

现有 0.7.5 APK 使用开发签名。发布前先核对已安装包与候选正式包的签名证书。采用不同签名时，安排并实测备份、迁移和恢复，单独验证替换后的阅读进度及批注。[Android 应用签名指南](https://developer.android.com/studio/publish/app-signing) · [`apksigner` 官方说明](https://developer.android.com/tools/apksigner)
