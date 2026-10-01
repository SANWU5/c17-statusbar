# Windows 手动构建 C17

当前 Miuix / Compose 界面使用正式 Gradle Wrapper 和 `tools/Build-Compose.ps1` 构建，工具链及本机签名接入见 [Compose 构建指南](COMPOSE-BUILD.zh-CN.md)。以下保留历史 Java 版本的独立构建流程。

`tools/Build-Standalone.ps1` 直接编译 Java 源码、Android 资源、字体与 Xposed 元数据，生成并验证签名 APK。它不编译 Kotlin / Compose，不适用于当前新版界面；工具本身不会安装到手机、提交 Git 或上传修改。

## 新电脑需要准备

- Windows PowerShell 5.1 或 PowerShell 7。
- 完整 JDK 17，包含 `java`、`javac`、`jar`、`keytool`。设置 `JAVA_HOME`，或在命令中指定 `-JdkHome`。
- Android SDK：Android SDK Platform 35（API 35）和 Android SDK Build-Tools 35.0.0。可在 Android Studio 的 SDK Manager 安装；已有命令行 SDK 时，也可运行 `sdkmanager "platforms;android-35" "build-tools;35.0.0"`。
- LibXposed API 101：离线构建时准备 `tools/lib/api-101.0.0.jar`（迁移包可附带），或用 `-ApiJar` 指定。没有本地依赖且未传 `-Offline` 时，脚本会从 Maven Central 下载官方 `api-101.0.0.aar`，提取其 `classes.jar` 用于编译；该 API 不打包进 APK。

脚本依次检测 `ANDROID_SDK_ROOT`、`ANDROID_HOME`、项目 `local.properties` 中的 `sdk.dir`、当前用户的 `AppData/Local/Android/Sdk` 以及 `tools/android-sdk`。也可以直接指定 `-AndroidSdk`。

联网构建只自动下载缺失的 LibXposed API；JDK 和 Android SDK 需要事先安装。完全离线时，以上工具与依赖必须已经存在。

## 构建

在完整项目根目录打开 PowerShell。示例路径需要换成新电脑实际安装位置。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\Build-Standalone.ps1 `
  -JdkHome 'C:\Program Files\Eclipse Adoptium\jdk-17' `
  -AndroidSdk 'C:\Android\Sdk' `
  -Offline
```

已经设置好 JDK/SDK 环境变量，并允许下载缺失依赖时：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\Build-Standalone.ps1
```

默认输出 `app/build/standalone/c17-statusbar-<版本>-standalone.apk` 和对应 `.sha256`。版本号读取 `AndroidManifest.xml`；其他输出位置可以通过 `-OutputDirectory` 指定。

中文项目目录受支持。为规避部分 Windows `aapt2` 的中文路径问题，脚本会把源码与依赖复制到独立 ASCII 临时目录，完成后清理。若用户临时目录不可用，指定一个可写的纯英文目录：

```powershell
.\tools\Build-Standalone.ps1 -AndroidSdk 'C:\Android\Sdk' -StagingRoot 'C:\C17Temp' -Offline
```

## 使用迁移包的原签名继续更新

1.15.0 发布包增加公开版→当前签名的 Android 升级链，公开证明文件为 `tools/signing/c17-public-to-current.lineage`，不含私钥。先完成独立构建，再用 `tools/Sign-WithLineage.ps1` 传入维护者自己的旧/当前 Keystore 进行发布签名。需要在本地设置 `C17_PREVIOUS_STORE_PASS` 与 `C17_STORE_PASS`；如密钥密码独立，另设 `C17_PREVIOUS_KEY_PASS` / `C17_KEY_PASS`。任何私钥或密码均不能上传。该工具使用 `--rotation-min-sdk-version 28`，随后核对签名、对齐并生成哈希。

签名链与 SDK 版本选择的依据见 [Android 官方 apksigner 文档](https://developer.android.com/tools/apksigner)。后续正式发布应继续保留相同升级链和签名，不改回无链的旧证书；普通第三方重编译仍使用自己的私钥。

默认签名是新电脑自己的 `%USERPROFILE%/.android/debug.keystore`；不存在时生成标准 Android 调试密钥，密码为公开约定的 `android`。它与手机当前安装包的签名可能不同。

覆盖安装手机当前版本时，需要使用迁移包提供的现用 Keystore，以及迁移说明中的密码和 alias。下面的 `migration-private/test.keystore` 是示例位置，请以迁移包里的实际文件为准：

```powershell
$env:C17_STORE_PASS = Read-Host '输入迁移包中现用 Keystore 的密码'
.\tools\Build-Standalone.ps1 `
  -AndroidSdk 'C:\Android\Sdk' `
  -Keystore '.\migration-private\test.keystore' `
  -StorePass $env:C17_STORE_PASS `
  -KeyAlias 'androiddebugkey' `
  -Offline
Remove-Item Env:C17_STORE_PASS
```

如果私钥密码与 Keystore 密码不同，另外传 `-KeyPass`。也可以只设置 `C17_STORE_PASS` 环境变量，脚本会在未传 `-StorePass` 时读取它。构建工具不会把密码写进源码或构建日志。现用私有 Keystore 和密码应保留在个人迁移备份中，不要上传到 GitHub。

签名文件不匹配时，Android 会拒绝覆盖安装。要延续手机已有模块和设置，应保留并使用原签名。

## 参数与排查

| 参数 | 用途 |
| --- | --- |
| `-JdkHome` | JDK 17 安装目录，包含 `bin` |
| `-AndroidSdk` | Android SDK 根目录 |
| `-BuildToolsVersion` | 默认 `35.0.0`，需存在于 SDK 的 `build-tools` 中 |
| `-ApiJar` | 已提取的 LibXposed API 101 JAR 路径 |
| `-Offline` | 禁止下载缺失的构建依赖 |
| `-Keystore` / `-StorePass` | 指定已有签名文件及密码 |
| `-KeyAlias` / `-KeyPass` | alias 默认 `androiddebugkey`，私钥密码默认沿用 StorePass |
| `-OutputDirectory` | APK 输出目录；相对路径按项目根目录解析 |
| `-StagingRoot` | 可写的 ASCII 临时目录根路径 |
| `-KeepStaging` | 保留临时源码、class、DEX 和签名副本，便于诊断；用完请删除 |

脚本在每一步检查工具退出状态，依次完成资源链接、Java 编译、D8、模块元数据打包、`zipalign`、`apksigner` 签名及验证。先对齐再签名；最终 APK 生成后不要手动修改其压缩包内容，否则签名会失效。[Android 官方 apksigner 说明](https://developer.android.com/tools/apksigner)。

需要运行项目已有检查时，可使用 `validation/run-checks.ps1`，详见 `validation/README.md`；这与上述 APK 构建工具分开运行。
