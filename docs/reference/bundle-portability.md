# 原包可以移植哪些部分

核对对象是用户提供的 `Books.app`。Info.plist 的平台为 macosx，主程序 Books 的文件头为 cafebabe（通用 Mach-O 容器），不是 Android APK、DEX 或 Android 工程。

找到的 JavaScript 为：

| 文件 | 大小 | 内容 |
| --- | ---: | --- |
| ProductFeed.js | 747 字节 | 小型测试 feed 工厂，非文字阅读引擎 |
| AppleWidgetController.js | 10490 字节 | Widget 控制辅助代码 |
| epub-message-placeholders.js | 192 字节 | 向 iframe 发送主题消息 |

BookEPUB 资源另有 base、flowable、fonts 和明暗配色 CSS。这些可以核对排版规则；部分规则依赖原生代码注入的 __ibooks_* 属性、CSS变量或 Apple 字体，并不能单独提供翻页、目录、持久化、菜单与 Android 系统交互。

因此可复用的是素材、本地化、排版规则与行为依据。没有发现完整 Swift/Objective-C 源码工程或可独立运行的跨平台阅读器。将 Mac 编译成品整体转成 Android 安装包不可行，原生系统接口仍需实现或替换；不会为了复刻而重写系统 WebView、文件选择器等已有组件，也不会将不受支持的规则盲目复制进正文样式。
