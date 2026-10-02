# C17 Status Bar · 1.6.0

Author: aiingjie · Package: `dev.puitheme.iosstatusbar` · versionCode: 51

The current version is the **1.6.0 stable release** (versionCode 51), published as [v1.6.0](https://github.com/SANWU5/c17-statusbar/releases/tag/v1.6.0). The primary device reference is OnePlus 13 running ColorOS 17. Desktop regression checks passed 51 groups and 76,430 assertions; no additional device-operation tests were performed for this build. See [Validation](VALIDATION.md) for the exact scope. Export any configuration you wish to retain before updating.

- Known bugs: **abnormal notification-shade time animation (【通知栏时间动画异常】)** and **notification-shade time misalignment (【通知栏时间异常错位】)** remain in this release.
- Planned: **swap Wi-Fi and cellular-data positions (【Wi-Fi与蜂窝数据的位置互换】)**.
- Cooperation/donations: **QQ 2726344450**.

[中文](README.md) · [Changelog](CHANGELOG.md) · [Validation](VALIDATION.md)

The app uses Miuix, the component system used by the LSPosed manager. Home, Configuration and About use a regular navigation bar adjoining the system navigation area, without floating gaps or shadows. The liquid-glass renderer and live configuration previews are removed. Configuration is organized into status bar, notification shade, control center and lock screen; dialogs appear above navigation.

Manually entered numeric values receive a 20-second trial before confirmation. Timeout, leaving the app or cold-start recovery restores the previous value. Slider bounds are suggestions; finite manual values may exceed them, subject to runtime drawing guards. Unified corners are a hard exception: 0–30dp across tiles, media, brightness, volume and device cards. The same value uses consistent physical pixel radii and native continuous-corner coefficients, clamped to each surface’s actual bounds while retaining slider press deformation, materials and touch geometry. The 1×1 icon-size option also covers recognized square device-card glyphs and the device-space entry, retaining battery rings; horizontal multi-device cards remain native.

The top-icon page switch remains disabled by default. Phone and control-center LEFT now use the same OEM handoff as RIGHT. Only the verified old notification-page copies are hidden while expanded or switching horizontally; vertical edge phases and full collapse remain native. The large clock replaces qs_footer_clock only in portrait; landscape restores the native time and ordinary notification-clock configuration. Safe mode, keyguard, disabling and detachment restore native state. RIGHT and other accepted features remain unchanged; notification stacking remains unavailable.

Battery text gains independent X/Y offsets, size, weight and spacing. Network text hides only when both Wi-Fi and mobile-data switches are confirmed off; unknown state does not trigger this rule. Notification icons support native icons, one heart, text or an imported image. An independent maximum-count option applies only to native icon mode, accepts nonnegative integers and treats 0 as no icons without deleting notifications.

Cellular settings add an optional gap adjustment when no network indicators are shown, disabled by default with a 0dp delta. It changes the native cellular-to-battery boundary only when the Wi-Fi icon and cellular network-type text are confirmed absent and the final visible native icon is cellular, directly before the battery. The adjustment follows RTL layout; disabled or unknown conditions retain native spacing. A disconnected network or an off switch alone does not establish eligibility.

The font catalog offers 10 genuine variable designs: two include Simplified Chinese, while eight Latin fonts use system Chinese fallback. This is a selection, not a popularity ranking. Downloads are on demand, with pinned sources, size, SHA256 and actual weight-axis validation. Cancellation, failure or lifecycle changes preserve the previous font. Full sources and licenses are listed in the [font catalog documentation](docs/FONT-CATALOG.zh-CN.md).

System font weight applies to the actual variation axis, preserving other native axes and italic style and restoring them when disabled. Media backgrounds wait 1.5 seconds after the last content or style change, retaining the previous result during update bursts. Once the new background is prepared, a lightweight alpha crossfade mixes the prepared old and new results without additional blur passes. Unchanged content continues to reuse its blur and material results.

New installations leave custom features disabled, except the mandatory hiding of cellular data arrows. Explicit saved preferences are retained and loaded at boot. Root access permits configuration without LSPosed activation; safe mode restores native rendering. Application data is private and excluded from backup/device transfer; uninstall also releases the loaded SystemUI module's in-memory ownership.

About restores the manual “Check for updates” action. It checks this project's stable GitHub Release and accepts only HTTPS release and APK links belonging to this repository, opening them in the system browser for viewing or download.

If an update reports an incompatible signature, users of the old signing certificate on Android 28 or later can first install the existing official [1.15.0 release](https://github.com/SANWU5/c17-statusbar/releases/tag/v1.15.0) over their app, then install 1.6.0 without uninstalling. Signature verification supports this route; it is not an old-certificate device migration test. Updating old-certificate installations on Android 26/27 is not guaranteed. See the [upgrade notes](docs/PARAMETER-UPGRADE-1.6.0.zh-CN.md).

Build with JDK17, Gradle, SDK37, AGP9.4.1, Kotlin/Compose2.4.20 and Miuix0.9.4. Java hooks retain Java8 bytecode; Kotlin targets JVM11. See [Windows build instructions](docs/COMPOSE-BUILD.zh-CN.md).

Private keys, phone captures, raw SystemUI, personal preferences and logs are excluded from the repository. Imported font/image files remain local and are not embedded in configuration JSON. See [Validation](VALIDATION.md) for build checks and device observations, and [third-party notices](THIRD_PARTY_NOTICES.md) for licensing. Desktop checks are not treated as device tests; no measured power savings are claimed.

The display version is now 1.6.0 while Android versionCode increases to 51. Older clients compare display versions and may not offer this update; download the APK from the [1.6.0 release page](https://github.com/SANWU5/c17-statusbar/releases/tag/v1.6.0) and install over the existing app.
