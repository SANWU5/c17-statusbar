// SPDX-License-Identifier: MIT
var plugin = {
  "id": "dev.c17.clock-seconds",
  "name": "时钟秒数与字号",
  "version": "1.0.0",
  "versionCode": 1,
  "apiVersion": 1,
  "description": "独立控制秒数和字号比例，不改写原设置。",
  "author": "C17 examples",
  "capabilities": ["settings"]
};

var scale = typeof C17.state.scale === "number" ? C17.state.scale : 100;
plugin.ui = [
  {"type": "text", "title": "默认状态栏时钟", "description": "启用期间临时覆盖默认时钟的格式与字号；关闭插件恢复原设置。双排时钟另有自己的规则。"},
  {"type": "switch", "key": "seconds", "title": "显示秒数", "default": true},
  {"type": "slider", "key": "scale", "title": "字号比例（%）", "min": 25, "max": 250, "step": 1, "default": 100}
];
plugin.settings = {
  clock_controls_enabled: true,
  clock_enabled: true,
  clock_pattern: C17.state.seconds === false ? "HH:mm" : "HH:mm:ss",
  clock_size_enabled: true,
  clock_scale: scale
};
plugin.hooks = [];
C17.define(plugin);
