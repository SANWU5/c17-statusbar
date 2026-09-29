# Android Canvas 诊断入口

这是专用的绘制检查入口，不属于应用界面，也不会修改系统电池服务。用 Android SDK 编译本目录的 ChargeBoltRenderCheck.java 和 app/src/main/java/dev/puitheme/ChargeBolt.java，将 D8 输出的 classes.dex 打包成 jar。通过已授权 ADB 连接把 jar 与输出目录放入 /data/local/tmp 后，用 CLASSPATH 和 app_process 运行 dev.puitheme.ChargeBoltRenderCheck，参数为输出目录。

输出包含真实透明 PNG、各场景逐像素检查及总计数。检查覆盖软件 Canvas；实际 SystemUI 的硬件层、系统窗口边界和其他 ROM 仍需实机观察。
