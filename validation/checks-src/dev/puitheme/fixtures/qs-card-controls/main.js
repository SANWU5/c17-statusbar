// SPDX-License-Identifier: MIT
var plugin = {
  "id": "dev.c17.validation.qs-card",
  "name": "控制中心自由编辑",
  "version": "1.2.1",
  "versionCode": 5,
  "apiVersion": 1,
  "description": "自由编辑顶部4至7行卡片与磁贴，通过更多磁贴入口打开专页，并逐项配置音乐手动调整、空媒体隐藏与壁纸。",
  "author": "C17 contributors",
  "capabilities": [
    "settings"
  ]
};

function flag(key) {
  return typeof C17.state[key] === "boolean" ? C17.state[key] : false;
}
var rows = typeof C17.state.qs_card_grid_rows === "string" ? C17.state.qs_card_grid_rows : "4";
var mode = typeof C17.state.qs_card_small_tiles_mode === "string" ? C17.state.qs_card_small_tiles_mode : "more";
if (mode === "swipe") mode = "more";
var opacity = typeof C17.state.qs_card_wallpaper_opacity === "number" ? C17.state.qs_card_wallpaper_opacity : 100;

plugin.ui = [
  {
    "type": "text",
    "title": "控制中心自由编辑 · 需要 C17 2.0.6",
    "description": "仅适用于已适配的竖屏控制中心。顶部自由编辑；更多磁贴入口可排序，点击打开专页，返回或收起重开回到顶部。音乐仅开放手动拖为4×2，不自动放大。旧验证模式更新需逐项重设；壁纸请在本详情关联入口选择。"
  },
  {
    "type": "select",
    "key": "qs_card_grid_rows",
    "title": "顶部自由编辑空间（4列）",
    "description": "保留单元高度，在4至7行中选择；实际空间不足时保留原生。",
    "default": "4",
    "options": [
      {
        "value": "4",
        "label": "4列×4行"
      },
      {
        "value": "5",
        "label": "4列×5行"
      },
      {
        "value": "6",
        "label": "4列×6行"
      },
      {
        "value": "7",
        "label": "4列×7行"
      }
    ]
  },
  {
    "type": "switch",
    "key": "qs_card_large_media_enabled",
    "title": "音乐卡片拖拽调整",
    "description": "开启后仅允许在系统编辑中将音乐卡片拖为4×2，不自动改变当前尺寸；系统保存原生尺寸。",
    "default": false
  },
  {
    "type": "switch",
    "key": "qs_card_native_resize_enabled",
    "title": "原生卡片拖拽调整",
    "description": "允许已适配的原生音乐卡片与大卡在系统编辑中拖拽调整至4×2，未适配卡片保持原生。",
    "default": false
  },
  {
    "type": "switch",
    "key": "qs_card_small_tiles_2x1_enabled",
    "title": "顶部2×1小磁贴",
    "description": "在已适配竖屏布局中显示2列×1行的小磁贴。",
    "default": false
  },
  {
    "type": "switch",
    "key": "qs_card_hide_idle_controls_enabled",
    "title": "无媒体时隐藏控件",
    "description": "没有媒体数据时隐藏内容控件，已暂停的媒体继续显示。",
    "default": false
  },
  {
    "type": "switch",
    "key": "qs_card_wallpaper_enabled",
    "title": "音乐卡片壁纸",
    "description": "使用宿主中选择的音乐卡片图片，不修改其他面板背景。",
    "default": false
  },
  {
    "type": "select",
    "key": "qs_card_small_tiles_mode",
    "title": "更多磁贴",
    "description": "在顶部放置可排序的更多磁贴入口，点击打开专页；返回回到顶部，上划收起后再次下拉也回到顶部。此项可独立开启，无需同时开启顶部小磁贴。",
    "default": "more",
    "options": [
      {
        "value": "always",
        "label": "始终显示"
      },
      {
        "value": "hidden",
        "label": "隐藏"
      },
      {
        "value": "more",
        "label": "更多磁贴入口"
      }
    ]
  },
  {
    "type": "slider",
    "key": "qs_card_wallpaper_opacity",
    "title": "音乐卡片壁纸不透明度",
    "description": "仅调整宿主保存的音乐图片，包内不含图片。",
    "min": 0,
    "max": 100,
    "step": 1,
    "unit": "%",
    "default": 100
  }
];
plugin.settings = {
  qs_card_grid_rows: Number(rows),
  qs_card_large_media_enabled: flag("qs_card_large_media_enabled"),
  qs_card_native_resize_enabled: flag("qs_card_native_resize_enabled"),
  qs_card_small_tiles_2x1_enabled: flag("qs_card_small_tiles_2x1_enabled"),
  qs_card_hide_idle_controls_enabled: flag("qs_card_hide_idle_controls_enabled"),
  qs_card_wallpaper_enabled: flag("qs_card_wallpaper_enabled"),
  qs_card_small_tiles_mode: mode,
  qs_card_wallpaper_opacity: opacity
};
plugin.hooks = [];
C17.define(plugin);
