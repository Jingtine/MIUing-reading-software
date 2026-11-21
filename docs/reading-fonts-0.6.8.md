# 新增思源黑体与霞鹜文楷 · 0.6.8

按用户追加的两个文件扩充现有正文内置字体。复用现有目录、字体选择、每主题存储和本地资产加载，不修改渲染引擎或界面字体。原四款及书籍字体选择继续保留。

| 显示名称 | 文件 | 版本（字体元数据） | 字节 | 稳定 ID |
| --- | --- | --- | ---: | --- |
| 思源黑体 | SourceHanSansCN-Regular.otf | 2.005 | 8,429,224 | source-han-sans |
| 霞鹜文楷 | LXGWWenKai-Regular.ttf | 1.530 | 25,928,940 | lxgw-wenkai |

两份原始文件直接内置，不转换或删除字符。均附原始版权元数据和从作者仓库取得的 SIL OFL1.1 文本。字库及摘要记录在 `app/src/main/assets/fonts/PROVENANCE.txt`。单文件均低于旧 WebView 的30MiB门槛，仍以实际 Android8加载验证兼容性。

接入步骤：先确认新增 ID 在旧版被归一化回 book；再向 ReadingFonts 目录添加两个条目和原始资产；扩展现有实际字体测试，覆盖六款加载、来源8000仍可见、保存及重建后恢复霞鹜文楷、切回书籍字体。

新增两款的原始文件、资产和常规 APK 中的字库逐字节相同。旧版基线明确将 source-han-sans 归一化回 book；接入后实际字体选择用例同时覆盖新旧六款。渲染引擎和默认界面字体未修改。

## 后续提供的系统字体

用户同时追加了 `C:/Windows/Fonts/STKAITI.TTF`（华文楷体，1.02，12,736,196字节）和 `SIMKAI.TTF`（楷体，5.03，11,779,732字节），并明确软件用于个人自用、不商用。SIMKAI 的内嵌许可限制了用途；[微软字体分发 FAQ](https://learn.microsoft.com/en-us/typography/fonts/font-faq) 明确区分文档嵌入与应用内置/再分发权限。个人自用不等于获得额外分发权，因此两个系统字库只保留在本地忽略目录，不上传 GitHub，也不进入默认常规构建。

个人构建通过显式 `-PpersonalFonts=true` 开启。原始文件不修改，复制至 `.local-fonts/fonts/st-kaiti.ttf`、`kai-ti.ttf`；缺任一文件则构建失败。Gradle 同时生成 `BuildConfig.PERSONAL_FONTS` 并仅在该开关开启时加入这个资产目录，字体目录相应显示八款正文选项。默认开关关闭，即使本地文件存在也不打包这两份文件、也不显示不可用选项；曾保存的个人字体在常规版中回退为所在主题默认字体。

```powershell
# 默认：六款常规正文内置字体
.\gradlew.bat :app:assembleDebug
# 本工作区个人自用：八款，含本地系统字库
.\gradlew.bat :app:assembleDebug -PpersonalFonts=true
```

个人字库 SHA256：华文楷体 `A29C99C161FC43CE6ABA2D7C152065359C2CB3019BE4AE6248171178CB7D04D5`；楷体 `95B3E69321F4E55F6ECF36B29D1E31A0C1205ACFDE0117DF6472A17494C3CBD2`。已核对个人 APK 中的文件与用户提供的原文件完全相同。`.local-fonts/` 已验证受 Git 忽略；常规 APK 已逐项检查，不含这两个字库和个人来源文件。应用内界面字体仍跟随 Android 系统。

## 验证记录

一次 Android8 自动旋转用例在父布局尚未放置时请求滚动，触发 Compose BringIntoView 异常；崩溃还遗留了横屏方向，使随后预期竖屏的用例找不到首屏自定义入口。用例初始化屏幕自然方向，并等实际视口宽度变化和界面空闲后再操作；未修改生产界面或放宽字体/位置断言。更长的字体列表同样等字体页出现后再滚动。

常规构建 Core12/12、JVM1/1、构建和 Lint0错误20条已有警告通过；Android8相关13/13（131.643s），含六款实际加载及个人字体 ID 回退。常规包104,178,219字节（约99.4MiB），SHA256：`EE60CA2B96FFBB1686139F5F6E9BD72F86B57EC1A2D87D191086E198E9C8969F`。

个人构建 Core12/12、JVM1/1、构建与 Lint 同样通过。冻结个人包120,831,636字节（约115.2MiB），SHA256：`3B6414B489C346DB7CCD7BD06FA0F46AE2965B09141FCC1B75B44B2FA28361F8`。

同一冻结个人包最终矩阵（2026-11-21）：

| 环境 | 范围 | 结果 | 耗时 |
| --- | --- | --- | --- |
| Android16 手机，1080×2400 / 420dpi | 完整仪器测试 | 57/57 | 437.648s |
| Android16 平板窗口，1600×1200 / 160dpi | 字体/主题/存储/锚点/导入相关回归 | 13/13 | 146.817s |
| Android8，1080×1920 / 420dpi | 同上，八款字体实际加载 | 13/13 | 151.266s |

相关13项为存储4、主题3、字体加载3、锚点/笔记1、EPUB/PDF冒烟2。个人构建的实际字体用例遍历全部八款，确认目标8000仍在视口、选择保存、重建后恢复最后所选楷体，并切回书籍原字体。八款均属于正文选项；菜单、按钮、字体名称使用系统界面字体。

一次只读代码审查未发现技术问题，仅指出个人构建说明仍是旧叙述，现已补齐开关、目录、缺文件失败及默认不包含行为。最终两包的资产和哈希均复核，系统字库及个人 APK 未进入 Git；测试模拟器尺寸/密度已还原并关闭。

新增 OFL 字库的实际截图：手机 [思源黑体](previews/phone-body-source-han-sans-0.6.8.png)、[霞鹜文楷](previews/phone-body-lxgw-wenkai-0.6.8.png)；平板 [思源黑体](previews/tablet-body-source-han-sans-0.6.8.png)、[霞鹜文楷](previews/tablet-body-lxgw-wenkai-0.6.8.png)。系统字库截图只保留在本地 `.tools/personal-*-kai-ti.png` 等文件中，不提交。

本工作区交付文件位于 `app/build/outputs/apk/debug/MIUing-0.6.8-personal.apk` 和 `MIUing-0.6.8-standard.apk`；两者均为开发签名。个人包供本地使用，仓库默认构建仍为六款字库，不据此宣称额外字体分发权或 Apple 原版字体等价。
