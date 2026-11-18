# 开发记录：2026-11-18

## 本次落地

已建立 Kotlin/Compose Android 工程与独立 JVM 解析模块。当前交付为可安装开发版，不将其称作 Apple Books 完整复刻。

本地 Git 分支：feat/miuing-android。origin 已关联用户指定的 SSH 仓库。Apple 参考应用包与构建工具通过 gitignore 保留在本地。

## 实际验证

- 核心 8 项 JVM 测试通过：EPUB 元数据/章节顺序、越界 ZIP 路径、UTF-8 BOM TXT、GB18030 TXT、UTF-16 XHTML、损坏 EPUB、外部实体、压缩条目大小限制。
- Android 15/API35 模拟器：手机窗口和 1600×1200 / 160dpi 平板窗口运行同一组 4 项仪器测试。
- 仪器测试覆盖 TXT 私有副本和去重、原始文件移除、进度与原子备份恢复、封面内存采样、EPUB 正文与嵌套目录图片、禁止书内脚本、高亮、阅读模式/章节切换、Activity 重建，以及 PDF 页面渲染/重建。
- 专门复现并修复分页正文出视口的问题，增加可见区域断言；截图保存在 docs/previews。
- assembleDebug 和 Android lint 已执行；lint 的非阻断警告保留在生成报告中，没有将“无 lint 错误”写成“无警告”。
- app:testDebugUnitTest 没有独立 JVM 用例（NO-SOURCE）；核心由 core:check 验证，Android 集成行为由仪器测试验证。

## 审阅与修复

独立审阅发现并修复 WebView 替换未附着、封面无界像素解码、AtomicFile 备份恢复、同名 content 子目录定位、UTF-16 XHTML 解码和跨行内元素高亮问题。后续复核指出的 PDF 关闭竞态与章节读取异常路径也已处理。

Android 实际运行额外暴露的 Files.writeString 缺失和 XML factory 特性不兼容均已修复，不能仅依赖桌面 JDK 测试推断 Android 兼容。

## 尚未完成

完整的原版视觉与交互对齐、嵌套 EPUB 目录、PDF 文本/批注能力、精确内容位置锚点、复杂跨段落高亮、API26与实体设备验证。具体边界见 docs/reference/parity.md。

原版在 macOS/iPad 上的实际运行截图仍未获得，当前无法验证逐像素一致性。这是原版保真验收的缺失证据，不能用现有模拟器截图替代。
