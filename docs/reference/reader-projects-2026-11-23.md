# 截图中三个开源参考的核验

核验日期：2026-11-23。阅读公开源码，不把第三方 README 的“Apple 风格”描述当成与原版一致的实测结论。

## Peyilo/libreadview

核验提交：[ceacbf3](https://github.com/Peyilo/libreadview/tree/ceacbf3e172de6dad81c54a4ec336a2bd5891873)。[README](https://github.com/Peyilo/libreadview/blob/ceacbf3e172de6dad81c54a4ec336a2bd5891873/README.md) 确实提供 `io.github.peyilo:libreadview:0.0.5` 坐标；[模块配置](https://github.com/Peyilo/libreadview/blob/ceacbf3e172de6dad81c54a4ec336a2bd5891873/app/libreadview/build.gradle.kts) 为 minSdk21、compileSdk36，和本项目的 SDK 范围不冲突。本次未下载或构建其发布制品，不能据此保证二进制接入无问题。

[EffectFactory](https://github.com/Peyilo/libreadview/blob/ceacbf3e172de6dad81c54a4ec336a2bd5891873/app/libreadview/src/main/java/org/peyilo/libreadview/turning/EffectFactory.kt) 包含 IBOOK_SLIDE、CURL、COVER；CURL 当前选择 IBookCurlEffect。[IBookSlideEffect](https://github.com/Peyilo/libreadview/blob/ceacbf3e172de6dad81c54a4ec336a2bd5891873/app/libreadview/src/main/java/org/peyilo/libreadview/turning/IBookSlideEffect.kt) 使用 View 页面容器：主页面平移，底页四分之一幅度跟随，底页最高 80/255 的黑色遮光。它依赖自己的页面容器，直接接入需要适配容器及生命周期。

本轮在 MIUing 现有两页临时位图层适配这一几何关系，保留 EPUB/TXT 排版、锚点、批注、脚注及全书分页；新增可选择的“平移”，不切换默认卷页。没有增加整库 Gradle 依赖，没有移植该库的文字布局或完整卷页引擎。[MIT 许可证](https://github.com/Peyilo/libreadview/blob/ceacbf3e172de6dad81c54a4ec336a2bd5891873/LICENSE) 原文随 APK 放入 `assets/licenses/libreadview-LICENSE.txt`。

## bekhruzbekswe/reada

截图仓库名是 **bekhruzbekswe/reada**。最初转录错字得到的404不代表项目不存在。已核验提交：[1b976cc](https://github.com/bekhruzbekswe/reada/tree/1b976cc6c0bf40f35a2257b35af86dbdef4b5b68)。[package.json](https://github.com/bekhruzbekswe/reada/blob/1b976cc6c0bf40f35a2257b35af86dbdef4b5b68/package.json) 声明 Expo54.0.35、React Native0.81.5、@epubjs-react-native/core 和 react-native-webview；[阅读页](https://github.com/bekhruzbekswe/reada/blob/1b976cc6c0bf40f35a2257b35af86dbdef4b5b68/src/app/reader/%5Bid%5D.tsx) 使用该 Reader 与 EPUB CFI/位置表恢复。

[design目录](https://github.com/bekhruzbekswe/reada/tree/1b976cc6c0bf40f35a2257b35af86dbdef4b5b68/design) 存在 HTML 设计稿及设计对话。根 LICENSE 是带 Expo 版权标记的 MIT 文本。README 宣称可在 Expo Go 的 Android/iOS 上运行；本次只核验源码，未运行其 Android 构建，未验证现时 Expo Go 支持范围。可作 UI 与恢复逻辑参考，但 Expo/React 组件不能整体移入当前 Kotlin/Compose 工程。未复制其代码或资源，也不将其第三方设计稿作为 Apple 官方基准。

## 04ai/ehviewer_scaffold

核验提交：[36d5d6e](https://github.com/04ai/ehviewer_scaffold/tree/36d5d6e2a028aa55a7d5c0559553832ddb9879fd)。[AppleModifiers.kt](https://github.com/04ai/ehviewer_scaffold/blob/36d5d6e2a028aa55a7d5c0559553832ddb9879fd/android/app/src/main/kotlin/com/example/ehviewer_scaffold/utils/AppleModifiers.kt) 与 [Physics.kt](https://github.com/04ai/ehviewer_scaffold/blob/36d5d6e2a028aa55a7d5c0559553832ddb9879fd/android/app/src/main/kotlin/com/example/ehviewer_scaffold/ui/theme/Physics.kt) 确实有 Compose 弹簧按压配置和位移阻尼函数；属于图片画廊应用，不能提供 EPUB 正文引擎。

README 声明 GPL-3.0，但本次完整源码树未找到 LICENSE，读取根 LICENSE 返回404，GitHub 仓库许可元数据也为空。因此没有把 README 标签当成已完成的许可核验，没有复制代码。卡片按压、圆角或转场的一般交互思路，可根据 Apple 原版证据和本项目需求独立实现；本轮没有引入其 Rust/JNI 栈。
