# iBook 平移实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在现有文字阅读器中提供可取消、可跨章的 iBook 风格平移。
**Architecture:** 复用 ReadingTurnHost 生命周期、预览与导航；PageTurnOverlay 只增加位图合成分支。页面模式采用已有字符串配置。
**Tech Stack:** Kotlin、Canvas、WebView、Compose、Node、Android instrumentation。
**Spec:** ../specs/2026-11-23-ibook-slide-design.md

## Global Constraints

API26–36；不新增运行时依赖；默认卷页；两 WebView 与每位图 2,000,000 像素上限；私人字体与 APK 不入 Git。

## Review Focus

反向翻页正文不可镜像；捕获失败不残留遮罩；跨章交接只提交一次；配置变化回收旧快照；系统禁用动画直接导航。

### Task 1: 样式与合成

**Files:** PageTurnOverlay.kt、ReadingTurnHost.kt、TextReader.kt、ReaderScreen.kt、ThemeSettingsPanel.kt、reader-gestures.js；新 SlideTurnTest.kt，扩展 tools/test-reader-gestures.cjs。
**Interfaces:** beginDrag(source:Bitmap, forward:Int, paperColor:Int, mode:String="curl")；ReadingTurnHost.begin 在末尾接收 mode:String；其他导航接口不变。

- [x] 添加 Node 平移拖动和 instrumentation 合成、UI 持久化/拖动/跨章测试，先观察缺失模式 RED。
- [x] 增加平移合成和样式传递，点击捕获目标快照；加入完整 MIT 许可。
- [x] 运行 Node、JVM、构建、lint；手机/平板/API26 新模式及既有相关用例通过，目视实际中间帧。
- [x] 独立整体审阅；修复重要问题并验证，记录版本、测试与参考核验结论。
- [x] 构建常规/个人 APK，核对签名/字体/脚本；提交并推送，清理自有测试数据及模拟器。
