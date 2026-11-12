# 时钟文字修正开发模板

仅供开发者阅读、复制和改写；不列入公开插件目录，不代表目标 ROM 已完成适配。使用本模板源码遵循仓库 MIT 许可；正式插件应单独声明作者、来源、许可、适配和恢复方法。

在 `com.android.systemui.statusbar.policy.Clock` 自己声明的零参 `updateClock()` 完成后，将当前 TextView 文字里的字面量 `AM` 替换为「上午」。不使用正则，不修改系统语言或用户配置。

这是 API 教学样例，**不是已完成的 ColorOS/Oplus ROM 缺陷修复**。必须先检查目标 ROM 的真实类/方法；不存在、方法继承而非该类声明、重载有歧义等情况均不能套用。24 小时制、已本地化或不含 AM 的文字不会变化。首次安装默认停用，未进行手机验收。

参考：[AOSP Clock.java 的 updateClock](https://android.googlesource.com/platform/frameworks/base/+/ac387ca9f3c2a304ff0b19c143c60517c916b1b8/packages/SystemUI/src/com/android/systemui/statusbar/policy/Clock.java)。

本模板保留旧 API 1 字段和原最低宿主。采用 code210 新详情契约的版本可参考 [SDK](../../docs/SDK.zh-CN.md#详情与宿主能力声明)，按使用说明与作用域、功能与设置、管理组织；保留文字修正开关，不以分组删除参数。SystemUI 声明与真实框架勾选须分开，无法核实时显示未知；其他应用勾选不会自动提供执行支持。
