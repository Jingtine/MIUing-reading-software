# PDF 阅读推进记录 · 0.3.0

本轮继续补齐 Apple Books 的本地 PDF 阅读能力；界面仍待逐页对照原版校准。

## 已实现

- 正文搜索（最多 100 条结果）、结果定位与页面范围提示；长按没有文字的页面时给出不可选择提示。
- 内嵌层级目录、独立页码跳转入口。
- 长按选词、拖动扩大范围、复制、高亮与笔记；标记按页码和文本范围保存。
- 自由手写、撤销、重开后恢复；显示原 PDF 中已有的高亮和笔迹。
- 系统文件选择器导出带标准 Highlight/Ink 批注的 PDF 副本，包含笔记内容。

页面图像继续使用系统 PdfRenderer。PdfBox-Android 提取文字和坐标、读取目录、生成批注。以裁剪区域与页面旋转为基准统一选择、显示和导出坐标；缓存最近六页文字，页面图像按可见范围加载，搜索可取消。

导出使用点击时的书籍/标记快照，先在缓存生成完整 PDF，再打开所选输出位置。任务由应用进程持有，阅读页或 Activity 重建不会取消任务；不保证进程被系统终止后继续。导入原件不被修改，测试会比较导出前后的 SHA-256。导出的既有批注再次导入后作为只读批注显示。

## 验证

2026-11-18，JDK 17、Gradle 8.13、compile/target API35。

| 检查 | 结果 |
| --- | --- |
| `:core:check` | 12 passed, 0 failed |
| `:app:testDebugUnitTest` | 1 个几何映射测试通过，包含四种旋转、裁剪原点与往返映射 |
| API35 手机完整设备测试 | 10/10，59.120 秒 |
| API35 平板窗口 1600×1200、160 dpi 完整设备测试 | 10/10，61.200 秒 |
| API26 Google APIs 模拟器完整设备测试 | 10/10，63.645 秒 |
| `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` | 构建成功；lint 0 errors、13 warnings |

10 个设备测试由书库 3 项、PDF 服务 3 项、PDF 界面操作 1 项、阅读冒烟 2 项、EPUB 阅读定位 1 项组成。PDF 服务覆盖文字/目录/搜索、四种旋转及裁剪、连字、批注文件重读与原文件摘要不变；界面操作覆盖搜索/目录跳页、隐藏工具栏后的选择、高亮保存、手写撤销、重建恢复及导出途中重建。API26 WebView 启动较慢，测试现等待阅读引擎实际初始化，不再以固定延迟判定。

代码审查发现的导出随页面重建取消、搜索旧结果残留、单页提取反复遍历整个页树、连字偏移和旧系统批注显示问题已修正。模拟器截图已检查，高亮与笔迹位于预期区域：

- [手机 PDF 标记](previews/phone-pdf-markup.png)
- [平板 PDF 标记](previews/tablet-pdf-markup.png)

调试 APK：26,950,064 字节（约 25.7 MiB），SHA-256：`7636aada619892c990b620ee6710347818b946b371895ecebc2c441bacf40a73`。未增加网络或公共存储权限。

## 仍待完成

扫描件 OCR、复杂竖排/双向文字的精细选择、既有批注编辑、形状/签名/橡皮擦，以及真实设备验证。当前可选择文字的质量受 PDF 本身字体编码与文本层影响。导出保留原有批注；原件中的其他批注类型在各系统渲染器上的显示尚未完整验证。

完整复刻还包括原版界面的逐页校准、仿真翻页、精细主题排版与自定义字体，见 [总对照清单](reference/parity.md)。

## 依赖与来源

- [PdfBox-Android 维护者仓库](https://github.com/TomRoush/PdfBox-Android)，依赖版本 2.0.27.0；对应 LICENSE/NOTICE 已放入应用 assets。
- [Android 旧版 PdfRenderer 原生实现](https://android.googlesource.com/platform/frameworks/base/+/260a36d/core/jni/android/graphics/pdf/PdfRenderer.cpp)：旧版页面渲染不启用 PDF 批注，本应用显式叠加高亮和笔迹；API35 关闭系统高亮叠加以避免重复显示。
