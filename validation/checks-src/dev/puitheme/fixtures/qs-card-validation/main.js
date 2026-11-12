// SPDX-License-Identifier: MIT
var plugin = {
  "id": "dev.c17.validation.qs-card",
  "name": "C17 临时卡片验证",
  "version": "1.0.0",
  "versionCode": 1,
  "apiVersion": 1,
  "description": "仅供 code195 本地验证：临时切换五项卡片开关，停用后恢复原设置。",
  "author": "C17 contributors",
  "capabilities": ["settings"]
};

var mode = typeof C17.state.mode === "string" ? C17.state.mode : "native";
plugin.ui = [
  {
    type: "text",
    title: "本地临时验证 · 需要 code195",
    description: "只临时覆盖本次五个开关，不保存系统卡片尺寸、不改其他设置或图片。壁纸使用本机已有图片，缺图保留原生。导入或设置检查通过不代表当前 ROM 的布局已经验证。"
  },
  {
    type: "select",
    key: "mode",
    title: "验证模式",
    default: "native",
    options: [
      {label: "恢复本次实验原生", value: "native"},
      {label: "大音乐", value: "large"},
      {label: "六行", value: "rows"},
      {label: "原生卡片拖拽", value: "resize"},
      {label: "无媒体隐藏", value: "idle"},
      {label: "卡片壁纸", value: "wallpaper"},
      {label: "六行＋大音乐", value: "rows_large"},
      {label: "综合", value: "all"}
    ]
  }
];
plugin.settings = {
  qs_card_large_media_enabled: mode === "large" || mode === "rows_large" || mode === "all",
  qs_card_six_rows_enabled: mode === "rows" || mode === "rows_large" || mode === "all",
  qs_card_native_resize_enabled: mode === "resize" || mode === "all",
  qs_card_hide_idle_controls_enabled: mode === "idle" || mode === "all",
  qs_card_wallpaper_enabled: mode === "wallpaper" || mode === "all"
};
plugin.hooks = [];
C17.define(plugin);
