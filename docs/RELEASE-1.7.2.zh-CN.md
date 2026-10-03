# 更好的 C17 状态栏 1.7.2

版本号 **1.7.2**，versionCode **64**。本次为修复更新，变化对比 [1.7.1](https://github.com/SANWU5/c17-statusbar/releases/tag/v1.7.1)。

## 修复

- **蜂窝信号位置**：补齐 ColorOS 17 原生信号图标的绘制路径，修复部分单卡布局中水平、垂直位置调整不生效的问题。
- **网络制式文字**：补齐单卡原生组件的首次绑定和后续刷新，按主流量卡当前状态更新文字，修复已开启功能但文字不显示或不更新的问题。
- **下拉时钟错位**：关闭“通知栏与控制中心时钟”的独立设置时，仍可跟随状态栏的时间格式和文字样式，但不再继承状态栏专用的位置偏移。下拉时钟保留原生页面的位置与动画；需要调整位置时，在下拉时钟页面单独开启位置调整。
- **系统界面适配**：修正部分系统版本缺少时钟宽度字段、磁贴回调没有实际方法体时的处理，避免可选接口异常中断其他功能加载。

## 更新说明

已有配置保留，无需删除数据。覆盖安装后重启 SystemUI 或手机，让新代码生效。

本次依据用户提供的诊断日志、截图及原生代码路径定位修复。构建与检查结果见 [验证记录](https://github.com/SANWU5/c17-statusbar/blob/v1.7.2/VALIDATION.md)；本轮未进行手机实机复测。

## 作者与支持

作者 **aiingjie** · GitHub [sanwu5](https://github.com/sanwu5) · 酷安 **konwo**。反馈、合作或捐赠请联系 **QQ 2726344450**。

本模块及全部功能永久免费，仅接受自愿捐赠。不捐赠也可完整使用，请勿购买付费转售版本。

[微信收款码](https://github.com/SANWU5/c17-statusbar/blob/v1.7.2/docs/donation/wechat.png) · [支付宝收款码](https://github.com/SANWU5/c17-statusbar/blob/v1.7.2/docs/donation/alipay.jpg)

项目采用 [GPL-3.0](https://github.com/SANWU5/c17-statusbar/blob/v1.7.2/LICENSE)，组件与字体版权见 [第三方声明](https://github.com/SANWU5/c17-statusbar/blob/v1.7.2/THIRD_PARTY_NOTICES.md)。
