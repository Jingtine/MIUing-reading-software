# 常见电子书格式整合实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 导入MOBI/AZW/AZW3和FB2并共用现有完整阅读功能。
**Architecture:** 固定Foliate解析模块，在隔离离线WebView转换，写入私有EPUB；原文件及去重身份保持不变。
**Tech Stack:** Kotlin、WebView、JavaScript、esbuild、ZIP、Android instrumentation。
**Spec:** ../specs/2026-11-23-multiformat-design.md

## Global Constraints

API26–36；无网络权限；新增格式原文件64MiB、文本32MiB、单资源16MiB、总输出256MiB、2000章节/2000目录项、60秒限时。未加密MOBI/KF8与FB2；不支持KFX/DRM/Print Replica。私人字库/APK不入Git。

## Review Focus

旧WebView语法与内置API；跨文件内链/图片/字体落盘；取消清理与迟到桥接；错误封装/压缩/DRM不会发布假记录；旧数据及已有批注定位不变。

### Task 1: 转换与导入

**Files:** 新增third_party/foliate-js解析源、tools/import-converter构建入口/兼容层/适配代码、assets/book-import.html和生成脚本、assets/licenses许可；新增BookFormatImporter.kt、ConvertedBookWriter.kt；修改LibraryStore.kt与导入提示。
**Interfaces:** `suspend BookFormatImporter.convert(context:Context,source:File,destination:File,format:String)`，生成标准EPUB或抛出明确异常；`ConvertedBookWriter.write(name:String,data:String,last:Boolean):Boolean` 分块，`complete(metadata:JSONObject,destination:File)` 提交ZIP。

- [x] 添加MultiformatImportTest，先观察当前LibraryStore拒绝MOBI/FB2的真实RED；为资源/目录/内链/重开/DRM/失败清理建立夹具。
- [x] 固定上游代码与MIT许可，构建Chrome69兼容产物，记录源SHA；实现有界导出与本地化。
- [x] 实现临时转换WebView与有界EPUB writer；接入格式路由、原文件保留、去重与统一阅读入口。
- [x] Android16设备新格式GREEN；新增格式使用统一主题/搜索/高亮/分页/动效，旧EPUB/TXT测试回归。

### Task 2: 兼容与交付

**Files:** 测试、README、格式能力记录、parity、版本配置、实际截图。
**Interfaces:** 沿用Task1；Gradle直接使用生成asset，重建命令记录在tools/import-converter。

- [x] 在API26旧WebView、Android16手机/平板窗口验证新增格式，逐项记录真实样本范围。
- [x] 核心/JVM/Node/脚本/构建/lint检查；独立整体审阅，重要问题以RED→GREEN修复。
- [x] 构建紧凑常规/个人APK，核对脚本/许可/字体/签名；更新说明，提交推送并核对远端，清理自有测试环境。
