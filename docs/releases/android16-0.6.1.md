# Android 16 主适配 · 0.6.1

日期：2026-11-19。根据用户要求，将主适配基准升级至 Android16/API36。compileSdk、targetSdk均为36，minSdk26只表示最低兼容下限，不代表按Android8设计界面。现有AGP8.9.2满足[官方SDK设置要求](https://developer.android.com/about/versions/16/setup-sdk)，无需更换框架或增加依赖。

重点验证[Android16目标行为](https://developer.android.com/about/versions/16/behavior-changes-16)：边到边布局、系统返回与大屏可调整窗口。保留现有Insets和AndroidX BackHandler实现，不增加退回旧系统行为的开关。新增系统返回真实流程：阅读菜单→关闭菜单，主题→关闭主题，正文→返回书库。

| 环境 | 回归 |
| --- | --- |
| Android16/API36 手机，1080×2400 / 420dpi | 32/32，263.558秒 |
| Android16/API36 平板窗口，1600×1200 / 160dpi | 32/32，245.021秒 |
| Android8/API26兼容下限，1080×1920 / 420dpi | 完整32/32，219.823秒；此前4项冒烟也通过 |

aapt已确认包版本0.6.1/code8、compileSdk36、targetSdk36、minSdk26。核心12项、JVM几何1项、构建通过，lint为0 errors、20 warnings。没有新增权限或依赖。

首轮Android16完整回归31/32通过，书签边界用例失败，未放宽原有精度断言。实测锚点4602恢复到了4159；同一个文字位置的折叠光标矩形为x380.55/y698.29，实际字符为x435.43/y28.19，视口宽411.43：浏览器把光标放在上一列末尾，字符却在下一列首行。恢复锚点改为测量一个实际字符的矩形，保留文本末尾回退和原有选择范围，不改变书签格式。专项由失败转为通过，随后重新完整回归。

后续回归发现主题测试未等待自定义页面出现便寻找滚动区域，补上明确的页面就绪等待，保留所有实际操作及断言。32项为0.6的31项加系统返回1项；同一最终APK在新旧系统上验证，不用降低断言精度来适配新系统。

最终开发包26,770,766字节，SHA256：`27007618331408bb5c07c1f48f183a5aad3836a42b47ce0c0f513f37931c3f3c`。核心12/12、JVM1/1、构建及JS语法检查通过，lint为0 errors、20 warnings。

关于整体移植：已重新检查原包，见[移植可行性记录](../reference/bundle-portability.md)。Mac编译程序不能直接转换成Android安装包；已有CSS、文案和资源可以核对复用，三个小脚本并非完整阅读引擎。Android继续复用系统WebView、文件选择器等组件，避免无谓地重写系统能力。

独立只读审查未发现Critical/Important问题；补上返回测试等待菜单附着的时机，并记录实际书签修复。当前验证覆盖完成后的系统Back路由，未覆盖预测返回手势的进度/中途取消、厂商刘海屏及实体设备。

本轮升级平台目标、修复实测分页边界问题并验证已有行为，不宣称因此完成Apple Books全部复刻。

已检查并保存[Android16手机正文](../previews/android16-phone-reader.png)、[手机菜单](../previews/android16-phone-menu.png)、[平板正文](../previews/android16-tablet-reader.png)、[平板菜单](../previews/android16-tablet-menu.png)，核对系统栏边界、右下菜单与双栏布局。测试后关闭模拟器并恢复测试设置。
