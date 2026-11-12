// SPDX-License-Identifier: MIT
var plugin = {
  "id": "dev.c17.text-fix",
  "name": "时钟文字修正示例",
  "version": "1.0.0",
  "versionCode": 1,
  "apiVersion": 1,
  "description": "按 ROM 核验后，将 Clock 文字中的字面量 AM 替换为上午。",
  "author": "C17 examples",
  "capabilities": ["systemui"]
};

plugin.ui = [
  {"type": "text", "title": "先核验 ROM 目标", "description": "教学示例：仅适用于声明 Clock.updateClock() 的 ROM，且原文含 AM。导入成功不代表目标手机已适配。"},
  {"type": "switch", "key": "enabled", "title": "将 AM 修正为上午", "default": true}
];
plugin.settings = {};
plugin.hooks = C17.state.enabled === false ? [] : [{
  id: "am-literal",
  className: "com.android.systemui.statusbar.policy.Clock",
  method: "updateClock",
  argCount: 0,
  kind: "text",
  find: "AM",
  replace: "上午"
}];
C17.define(plugin);
