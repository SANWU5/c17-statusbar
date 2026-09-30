# 本地回归检查

先构建应用，向 run-checks.ps1 传入编译后的生产类目录或类 JAR（CompiledClasses）、SDK android.jar（AndroidJar）及 libxposed API 101 JAR（XposedApiJar）。JdkBin 可选，填写完整 JDK 的 bin 目录；默认使用 PATH 中的 Java。

脚本的检查入口对应本轮最终日志。validation/test-libs/json-20240303.jar 为桌面 JSON 解析运行库，执行时排在 android.jar 前；该文件不进入 APK。HSB 对照使用完整 JDK 的 java.awt.Color，Android 桩只记录参数和状态；这些检查不能替代屏幕绘制、真实网络切换或不同 ROM 的验证。

本轮已完成和未完成的项目见根目录 VALIDATION.md。verify_font_reference.py 与 native-canvas 为字体或原生 Canvas 的独立参考入口；它们不因存在于源码包就自动计入本轮通过记录。
