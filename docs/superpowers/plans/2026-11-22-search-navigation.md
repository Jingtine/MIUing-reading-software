# 本章逐项搜索导航 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 选中章节搜索结果后，准确切换该章各处匹配并返回原文。

**Architecture:** DOM 索引流式计数并返回选定范围；桥只发布当前文档的匹配状态。独立 SearchNavigation 组件呈现控件；ReaderScreen 保存序号，复用现有跳转和原文历史。

**Tech Stack:** Kotlin/Compose、现有 WebView JavaScript；无新增依赖。

**Spec:** docs/superpowers/specs/2026-11-22-search-navigation-design.md

## Global Constraints

- compile/target36、min26；UI 系统字体、正文常规六款/个人八款保持不变。
- 最多正文与一个辅助 WebView；正文锚点持久化不变。
- 仅当前章匹配导航；不改变全书章节发现及 PDF 搜索。

## Review Focus

- 第二处重复文字必须有不同真实锚点，跨行内元素和大小写扩展保持正确。
- 空查询/无匹配不创建范围；序号越界安全夹紧，边界无额外跳转。
- 逐项切换与旋转不污染历史；一次返回到搜索前来源。
- 高亮与笔记重叠不改原文；关闭搜索不移动当前原文。
- 已销毁文档的回调不能更新新文档控件。

### Task 1: 真实 DOM 逐项匹配

Files: reader-text.js、reader.js、SearchNavigationTest.kt。
- [x] 添加真实渲染重复文字、跨元素、大小写扩展及标记重叠用例，观察失败。
- [x] find(query,index=0) 流式计数，highlights 返回当前匹配；配置 searchIndex 指定命中，发布 searchMatch(index,total)。
- [x] 验证新 renderer 用例及既有文字引擎/搜索回归。

### Task 2: 控件、状态与交付

Files: SearchNavigation.kt、ReadingWeb.kt、TextReader.kt、ReaderScreen.kt、ReaderChromeTest.kt。
- [x] 添加实际下一处/上一处、旋转、边界、关闭及单次返回来源用例，观察失败。
- [x] 桥校验并隔离旧回调；序号保存且逐项跳转不追加历史；独立控件只在选中搜索时显示。
- [x] 独立审查；Core/JVM/JS/lint；Android16 手机全量、平板及 Android8 相关回归。
- [x] 版本0.6.11/code18，两种构建字体/签名/脚本核对、文档、提交推送与专用测试设备清理。
