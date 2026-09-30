# Better C17 Status Bar 1.14.0

Author: aiingjie · Stable release: v1.14.0 · versionCode: 28

## Changes

- **Tile edge fade:** keeps native horizontal paging and fades only the left and right edges by default. Removes the native split crop during isolated composition, contains the result within the tile viewport, and avoids an extra transparency veil over the top or center. Portrait and landscape switches and existing range/position settings are retained. Advanced blur remains available and is off by default.
- **Global active fill colors:** separate light and dark theme palettes control color, opacity and gradients for supported Wi-Fi inner circles, volume/brightness fills and other active tiles. Native glass layers, borders, highlights, icons, labels and animations are preserved. This feature is off by default.
- **Configuration import/export:** save or select JSON through Android's document picker. Imports are validated before confirmation and application. Font files, logs and local file information are excluded; a missing custom font falls back to the system font.
- **Stable Wi-Fi placement:** hidden badges and activity arrows no longer change the glyph's measured position as activity changes. Turning hiding off restores the latest visibility requested by the system.

## Installation

Install the APK over the existing installation with the same signing key, enable the module in LSPosed and select the SystemUI scope. Restart SystemUI after a code update. Ordinary setting changes apply through the existing live update path. Existing status bar controls, three independent carrier panels, in-battery charging transitions, precise numeric input, HSB colors, fonts and optional diagnostics remain available.

## Files and validation

- APK: `更好的C17状态栏-1.14.0.apk` (349,281 bytes).
- Source: `更好的C17状态栏-源码-1.14.0.zip`.
- APK SHA-256: `219ca55278345bc7e62bdcc7339b025f2ee9bd0866b7959603f6adc611766589`.
- Local checks: 27 suites and 48,636 assertions, including the new tile appearance and configuration transfer suites.

The source includes every release check. Delivery files exclude phone screenshots, private settings, logs, complete SystemUI packages and signing keys. Local assertions do not establish device or other-ROM compatibility; detailed build records are in `VALIDATION.md`.

### Device observations

- APK installation: performed.
- SystemUI restart: performed.
- Full phone reboot: not performed.

### Verified observations

- 覆盖安装本版并重启 SystemUI，确认模块页面和状态栏正常显示。
- 全局活动填充在 Wi-Fi、移动网络内圈、普通活动磁贴及亮度和音量已填充部分生效，保留外层玻璃、高光和原有图标颜色。
- 分别核对浅色与深色配置、双色渐变、55% 透明度，以及关闭调色后的原生恢复。
- 横竖屏分别截取左滑、右滑和停稳画面；纯渐隐作用于磁贴分页边缘，顶部状态栏和旁边控件未受处理。
- 核对左侧水平偏移移出边界时的窄渐隐，再临时归零核对两侧效果，随后恢复用户数值。
- 通过系统文件界面成功导出 220 项配置，核对取消导入及确认导入，恢复测试前的颜色和透明度设置。
- 诊断日志测试结束后关闭，系统主题模式和屏幕方向恢复测试前设置。

### Pending observations

- 尚未完成真实离开无线覆盖环境的连接切换与 Wi-Fi 位置连续采样。
- 尚未在其他系统版本或机型上完成实机视觉核对。

### Notes and limits

- 高级模糊默认关闭；本次采用用户选择的原生滑动与边缘渐隐。
- 系统内部玻璃绘制结构不匹配时保留原生绘制，避免使用整块纯色覆盖。
- 本地检查覆盖测量、状态切换及配置边界，不等同于所有机型的实机验证。

[Repository](https://github.com/SANWU5/c17-statusbar) · [Release page](https://github.com/SANWU5/c17-statusbar/releases/tag/v1.14.0)
