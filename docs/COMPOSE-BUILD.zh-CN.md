# 正式 Miuix / Compose 构建

设置界面使用 Maven Central 正式发布的 Miuix `0.9.4`，与手机 LSPosed 管理器采用的新版组件体系一致。SystemUI hooks、配置提供器与 Root 检测继续采用现有 Java 实现。

## 固定工具链

| 项目 | 版本 |
| --- | --- |
| JDK | 17 |
| Gradle Wrapper | 9.6.0，校验官方发行包 SHA256 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin / Compose Compiler | 2.4.20 |
| Compose Multiplatform | 1.12.0 |
| Miuix UI / Preference / Icons | 0.9.4 |
| Material 扩展功能图标 | 1.7.8 |
| Activity Compose | 1.13.0 |
| LibXposed manager service | 102.0.0 |
| Android compile SDK | 37.0 |
| Android Build Tools | 36.0.0 |
| minSdk / targetSdk | 26 / 35 |
| Java hooks / Kotlin UI JVM target | 1.8 / 11 |

AGP 9 已内置 Kotlin，项目不再同时应用 `org.jetbrains.kotlin.android`。根构建脚本固定 Kotlin Gradle Plugin 到 `2.4.20`，与 Compose Compiler 对齐。未引入要求 minSdk 33 的 `miuix-blur`。

Compose 1.12 的内联 API 已使用 JVM 11，Kotlin 界面因此采用 JVM 11 输出；Java hooks 继续采用 1.8。Android 模块的默认编译目标为 11，以满足 AGP 内置 Kotlin 的模块检查，实际 `JavaCompile` 任务单独固定 `sourceCompatibility` 和 `targetCompatibility` 为 1.8。构建明确允许这两种 class 输出，最终都由 D8 转换为支持 minSdk 26 的 Android DEX。

依赖来自 [Miuix 官方指南](https://compose-miuix-ui.github.io/miuix/guide/getting-started) 和 [0.9.4 官方版本表](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/gradle/libs.versions.toml)。工具链参考 [AGP 9.4 兼容表](https://developer.android.com/build/releases/agp-9-4-0-release-notes) 与 [内置 Kotlin 迁移指南](https://developer.android.com/build/migrate-to-built-in-kotlin)。

## Windows 构建

SDK 应安装 `platforms;android-37.0` 与 `build-tools;36.0.0`。设置 `JAVA_HOME`，并在不提交的 `local.properties` 中设置 `sdk.dir`，或运行：

```powershell
.\tools\Build-Compose.ps1 -Variant Both -JdkHome 'C:\path\to\jdk-17' -AndroidSdk 'C:\path\to\android-sdk'
```

脚本优先使用 `-GradleHome` 或 `C17_GRADLE_HOME` 指定的 Gradle，其次检测本机 `CodexAndroidTools\gradle-9.6.0`，否则使用正式 wrapper。已完成依赖下载后可加 `-Offline`。

本机有 `HTTP_PROXY` / `HTTPS_PROXY` 时，脚本会给 Java 构建进程传递代理 host / port；不会把代理地址写进共享项目配置。

也可以直接运行：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease
```

APK 输出至 `app/build/outputs/apk/debug/` 和 `app/build/outputs/apk/release/`。`Build-Standalone.ps1` 仅用于历史 Java 界面，不能用于包含 Kotlin / Compose 的当前界面。

## 本机覆盖安装签名

构建脚本只从环境变量或不提交的 `.local-private/signing.properties` 读取以下字段：

```properties
C17_KEYSTORE=C:/path/to/existing-device-signing.keystore
C17_STORE_PASSWORD=<本机密码>
C17_KEY_PASSWORD=<密钥密码>
C17_KEY_ALIAS=<已有别名>
```

环境变量优先。四个字段完整时，Debug 和 Release 都使用同一已有本机签名，保持包名 `dev.puitheme.iosstatusbar`。没有本机签名时，Debug 使用 Android 默认调试签名，Release 保持未签名；不要把这种包当成已有设备包的覆盖安装包。keystore、签名参数、设备参考与录屏均保留在私有目录，不进入提交或发布。

Release 暂不启用 R8 名称压缩或资源裁剪，以保留 Xposed 入口及对 OEM 类、字段、方法的反射绑定。LibXposed API 101 为 `compileOnly`，优先使用已有 `tools/lib/api-101.0.0.jar`，不会打进 APK。界面通过 [Maven Central 正式发布的 `io.github.libxposed:service:102.0.0`](https://central.sonatype.com/artifact/io.github.libxposed/service/102.0.0) 接收管理器服务；其 AAR 自动合并官方 provider，无需在应用清单中重复注册。
