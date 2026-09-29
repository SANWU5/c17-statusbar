# Better C17 Status Bar 1.12.2

[简体中文](README.md) · [Download APK (1.12.2)](https://github.com/SANWU5/c17-statusbar/releases/latest)

Author: aiingjie · Package: `dev.puitheme.iosstatusbar` · libxposed API 101

An LSPosed module for customizing the status bar in OnePlus/Oplus SystemUI. It requires a rooted Android device with LSPosed support.

## Features

- Adjust the position, size, and color of the clock, network speed, Wi-Fi, cellular signal, labels, and battery.
- Pick colors with the HSB palette and configure transparency.
- Show the actual cellular signal and use a compact single-row layout.
- Set custom clock formats and replace the carrier text shown in the notification shade.
- Use the system font, the included variable PingFang font, or an imported font.
- Customize the battery while keeping its original shape, with separate colors for the charge level, text, and charging bolt.

The module targets OnePlus/Oplus SystemUI. Other Android systems may use different internal status bar components.

## Build

Open the repository in Android Studio with JDK 17, Android SDK Platform 35, and Gradle 8.9. Run `:app:assembleDebug`. Gradle downloads Android Gradle Plugin 8.7.3 and libxposed API 101 from Google Maven, Maven Central, and the Gradle Plugin Portal.

## Privacy

Settings are stored locally and read by the module and SystemUI. This repository does not include device identifiers, exported personal settings, screenshots, debug logs, signing keys, or passwords.

## License

The module source code is released under the MIT License in [LICENSE](LICENSE). The included PingFang font has separate attribution and licensing details in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [FONT.md](FONT.md).

