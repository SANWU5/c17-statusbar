# C17 Status Bar · 1.15.1-beta.1

Author: aiingjie · Package: `dev.puitheme.iosstatusbar` · versionCode: 40

**This is a test build. Do not update casually.** The primary device reference is OnePlus 13 running ColorOS 17. Stable users can remain on [1.15.0](https://github.com/SANWU5/c17-statusbar/releases/tag/v1.15.0).

[中文](README.md) · [Changelog](CHANGELOG.md) · [Validation](VALIDATION.md)

The app now uses Miuix, the component system used by the LSPosed manager. Home, Configuration and About use a regular navigation bar; the liquid-glass renderer is removed. Configuration is organized into status bar, notification shade, control center and lock screen, with complete category labels.

Unified corners cover tiles, media, brightness, volume and device cards with a 0–80dp range. The slider rewrite retains native geometry while changing final curve parameters. The clear button reuses geometry caches, retaining independent motion and safe spacing. Notification icon customization supports native icons, one heart, text or an imported image, along with position, size and colors.

New installations leave custom features disabled, except the mandatory hiding of cellular data arrows. Explicit saved preferences are retained and loaded at boot. Root access permits configuration without LSPosed activation; safe mode restores native rendering. Application data is private and excluded from backup/device transfer; uninstall also releases the loaded SystemUI module's in-memory ownership.

Build with JDK17, Gradle, SDK37, AGP9.4.1, Kotlin/Compose2.4.20 and Miuix0.9.4. Java hooks retain Java8 bytecode; Kotlin targets JVM11. See [Windows build instructions](docs/COMPOSE-BUILD.zh-CN.md).

Private keys, phone captures, raw SystemUI, personal preferences and logs are excluded from the repository. Imported font/image files remain local and are not embedded in configuration JSON. Validation records distinguish build checks from actual device observations; no measured power savings are claimed.
