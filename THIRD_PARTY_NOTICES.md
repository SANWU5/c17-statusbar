# 第三方声明

## 软件源代码

仓库中的模块源码采用 GNU GPLv3（`GPL-3.0-only`），完整许可证见根目录 `LICENSE`，版权声明见 `COPYRIGHT.md`。

## 内置苹方字体

`app/src/main/assets/fonts/PingFangSC-VF.ttf` 是 Apple 苹方 UI 的精简可变字体重建版本，由 alphaArgon / ACT-02 处理。本仓库仅为状态栏保留所需字符。

字体资源保留其原有条款。其转载与修改依据上游项目 README 中的许可说明，并保留来源和作者标记：

- 上游项目：https://github.com/ACT-02/PingFangUI-VF
- 本地来源说明：`FONT.md` 与 `app/src/main/assets/fonts/SOURCE.txt`

上游允许转载和修改，并要求注明来源。字体的原始设计归 Apple；此字体重建及其分发条款与本仓库的软件许可证分别处理。仓库不声称与 Apple 有官方关联。

## PUI 电池外观

所需电池矢量资源取自用户提供的 PUI Theme For OPlus 17 模块。资源作者的权利保留，不随本项目软件许可证变更而重新授权；来源与校验见 `docs/PUI-battery-evidence.md`。交付包不包含完整的 PUI 模块。

## 构建与检查依赖

LibXposed API 101 仅作编译依赖，未将其 JAR 打包进 APK。Android 平台类由系统提供。

`validation/test-libs/json-20240303.jar` 仅用于桌面检查，未打包进 APK，其声明保留在 JAR 内。第三方依赖保留各自的许可证与声明。
