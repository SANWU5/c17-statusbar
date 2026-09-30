# Better C17 Status Bar 1.14.0

Author: aiingjie · Package: dev.puitheme.iosstatusbar · versionCode: 28 · LibXposed API 101

[简体中文](README.md) · [Repository](https://github.com/SANWU5/c17-statusbar) · [Download v1.14.0](https://github.com/SANWU5/c17-statusbar/releases/tag/v1.14.0)

## What's new

- Native tile paging with left and right edge fade by default. The output stays within the tile viewport, without an extra transparency veil over the top or center. Portrait and landscape switches and existing range/position settings are retained. Advanced blur remains available and is off by default.
- Global active fill colors with separate light and dark palettes, opacity and gradients. Supported Wi-Fi inner circles, volume/brightness fills and other active tiles share the palette while native glass layers, borders, highlights, icons, labels and animations remain. This feature is off by default.
- Configuration import/export through Android's document picker. JSON is validated before confirmation and application. Font files, logs, diagnostics switches and local file metadata are excluded; a missing custom font falls back to the system font.
- Stable Wi-Fi glyph placement when hidden badges and activity arrows change. Native connection visibility and airplane-mode spacing are preserved; turning hiding off restores the system's latest requested visibility.

## Controls

Independent switches cover network speed, cellular signal, Wi-Fi, network type text, clock, carrier labels, fonts, battery and tile paging. Real signal levels and a single-row layout are supported. Network type labels normalize to 5G, 4G or 3G; real-time speed such as `0.00 KB/s` remains available. System badges and upload/download arrows can be hidden separately.

Notification shade, control center and lock-screen carrier labels have independent original/time/custom-text modes, position, size, weight, spacing, theme colors and opacity. Lock-screen customization is off by default. Clock formats support dates, weekdays and AM/PM, with position and size controls.

Values retain two decimal places and support direct input. Sliders show recommended ranges; manually entered finite values outside those ranges require confirmation within ten seconds. Cancel, back or timeout discards the pending change. Positions and letter spacing allow signed values; sizes and durations are nonnegative, with font weights from 100 to 900. Rendering safeguards do not rewrite saved values.

Tile effects have independent left/right ranges and horizontal offsets, vertical offset, coverage height and vertical feather. A height of zero uses the page height; a side range of zero disables that side. Advanced blur adds radius and strength controls and requires Android 12 or later with hardware rendering; unsupported paths fall back to fade or native drawing.

## Battery, colors and fonts

The default horizontal battery uses only the required vectors from the user-provided PUI Theme For OPlus 17, version 17.0.0.119, `PuiThemeBatteryHorizontal.apk`. The complete PUI module is not bundled; see [resource provenance](docs/PUI-battery-evidence.md). Native battery style is also available, and other battery shapes retain system drawing.

While charging, the in-battery percentage and a cutout lightning symbol alternate smoothly. Battery body, text, lightning, charging and low-battery/power-saving fill colors are independent. Position, scale, dimensions and transition timing are adjustable.

The color picker supports HSB, alpha and RGB/ARGB input. Alpha can follow the system or use a custom value. Fonts can follow the system, use the bundled variable PingFang subset or use an imported font. Missing characters fall back to system fonts; provenance is in [FONT.md](FONT.md).

## Installation and updates

Install over the existing APK with the same signing key, enable the module in LSPosed and select SystemUI as its scope. Restart SystemUI after a module code update; ordinary setting changes use the live update path.

The settings app can check [official GitHub releases](https://github.com/SANWU5/c17-statusbar/releases/latest) manually or periodically at launch. It opens release/APK links for the user and does not automatically download or install. Drafts, prereleases and older versions are not offered as updates. Update checks run in the settings app, not SystemUI.

Large sizes or offsets can overlap other elements; relaxing internal clipping cannot enlarge system windows or the physical screen. Compatibility is based on OnePlus/Oplus SystemUI. Other ROMs may use different resources and view structures.

## Optional diagnostics

Diagnostics are off by default. Enable them to reproduce a problem, then view, share, save or clear the records. Fixed stages and exception types are recorded without raw exception messages, custom text or setting values. The current log and one rotated log are each limited to 512KiB, with a 64KiB tail preview. Sharing uses temporary read-only snapshots and the system chooser; saving uses Android's document picker.

## Build and validation

Android Gradle Plugin 8.7.3, compileSdk 35, minSdk 26, targetSdk 35 and Java 8. `io.github.libxposed:api:101.0.0` is a compile-only dependency. Gradle/aapt generates R for source builds, and the font remains uncompressed.

The final local run records 27 suites and 48,636 assertions. Build and device observations are documented in [VALIDATION.md](VALIDATION.md). Local checks do not establish hardware-rendering, real-network or other-ROM behavior.

The source includes production code/resources, public documents and all release checks. `validation/test-libs/json-20240303.jar` is only a desktop test runtime and is excluded from the APK. Delivery files exclude phone screenshots, private settings, logs, device identifiers, complete SystemUI/PUI packages and signing keys. APKs signed with a different key cannot overwrite the current installation.

Module source is available under the [MIT license](LICENSE). Fonts and other third-party resources retain their separate provenance and terms; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
