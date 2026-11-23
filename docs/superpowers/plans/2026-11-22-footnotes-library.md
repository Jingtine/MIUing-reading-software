# 脚注与书库 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 就地查看脚注，并改善完整封面和返回书库体验。
**Architecture:** core提取安全脚注片段，ReadingFootnotes协调异步结果，reader-footnotes.js在当前renderer外层展示；BookCards复用封面/元信息组件，MainActivity保存书库界面状态。
**Tech Stack:** 现有Java/Kotlin/Compose/WebView，无新依赖。
**Spec:** docs/superpowers/specs/2026-11-22-footnotes-library-design.md

## Global Constraints

- compile/target36、min26；正文/界面字体策略及私人资产忽略保持原样。
- 最多正文和一个辅助WebView；脚注不得参与正文索引、页数和进度写入。
- 不改既有全书计数、PDF和书库持久化数据格式。

## Review Focus

- 带片段相对URL、百分号编码、非spine尾注和目录穿越。
- 脚注内容包含脚本、回链、外部资源、过大或损坏XML。
- 异步结果在关闭/切章/旋转后不能重新打开旧脚注。
- 弹层触摸不能翻动正文，选择弹层文字不能污染正文高亮。
- 封面宽高比、长元信息、返回显示方式/列表位置及侧栏宽度。

### Task 1: 脚注

Files: core/EpubFootnotes.java、ReadingFootnotes.kt、reader-footnotes.js、ReadingWeb.kt、ReadingLayout.kt、TextReader.kt、ReaderScreen.kt。
- [x] 新增core安全提取和真实阅读弹层回归，观察RED。
- [x] 实现语义识别、受限提取、原文URL、隔离弹层、异步取消和系统返回。
- [x] 验证弹层同/跨章、关闭原位、非spine、误判、字体及正文/页数不变GREEN。

### Task 2: 书库

Files: BookCards.kt、LibraryScreen.kt、MainActivity.kt、LibraryPresentationTest.kt。
- [x] 加真实完整封面、长书名、整卡点击及返回状态用例，观察RED。
- [x] 提取复用书卡组件，保留封面、统一元信息、校准继续阅读；SaveableStateHolder保留书库状态。
- [x] 目视检查手机/平板/窄窗口和实际回归GREEN。

### Task 3: 验证与交付

- [x] 独立整体代码审查、修复实质发现及回归。
- [x] Core/JVM/JS/lint，手机全量、平板/Android8相关测试。
- [x] 版本0.6.12/code19；冻结常规/个人包，核对六份JS、字体及原签名。
- [x] 文档和截图、提交推送、核实远端与干净状态，清理专用模拟器与本轮临时目录。
