// SPDX-License-Identifier: MIT
var plugin = {
  "id": "dev.c17.ui-spacing",
  "name": "时钟垂直间距示例",
  "version": "1.0.0",
  "versionCode": 1,
  "apiVersion": 1,
  "description": "按 ROM 核验后，微调原生 Clock 的垂直平移。",
  "author": "C17 examples",
  "capabilities": ["systemui"]
};

var offset = typeof C17.state.offset === "number" ? C17.state.offset : 0;
plugin.ui = [
  {"type": "text", "title": "时钟视图垂直平移", "description": "先核验 ROM 的 Clock.updateClock()。默认 0dp；正数向下，负数向上。这是位置微调，不改变系统布局高度。"},
  {"type": "slider", "key": "offset", "title": "垂直位移（dp）", "min": -12, "max": 12, "step": 0.5, "default": 0}
];
plugin.settings = {};
plugin.hooks = [{
  id: "clock-vertical-offset",
  className: "com.android.systemui.statusbar.policy.Clock",
  method: "updateClock",
  argCount: 0,
  kind: "property",
  property: "translationY",
  value: offset
}];
C17.define(plugin);
