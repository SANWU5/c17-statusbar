# 本地回归检查

先构建应用，然后向 run-checks.ps1 传入该构建的生产类目录或类 jar（CompiledClasses）、SDK 的 android.jar（AndroidJar）及 libxposed API 101 jar（XposedApiJar）。JdkBin 可选，填写 JDK 的 bin 目录；默认使用 PATH 中的 Java。HSB 对照使用完整 JDK 的 java.awt.Color，只属于桌面检查代码，不进入 Android 安装包。

检查顺序和入口都在脚本中。Android 桩仅记录参数与状态；它们不能模拟硬件绘制层、屏幕窗口及不同 ROM。复现的 stacked_mobile 槽位识别已覆盖真实调用条件。手机与 Canvas 的实际观察记录见根目录 VALIDATION.md；字体检查入口为 verify_font_reference.py，独立原生 Canvas 入口见 native-canvas/README.md。
