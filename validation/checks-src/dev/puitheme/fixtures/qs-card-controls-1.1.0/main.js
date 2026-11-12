// SPDX-License-Identifier: MIT
var plugin = {
  "id": "dev.c17.validation.qs-card",
  "name": "控制中心扩展",
  "version": "1.1.0",
  "versionCode": 2,
  "apiVersion": 1,
  "description": "逐项配置4至7行空间、大音乐、原生卡片调整、2×1小磁贴、底部显示、空媒体隐藏与音乐壁纸。",
  "author": "C17 contributors",
  "capabilities": [
    "settings"
  ]
};

function flag(key) {
  return typeof C17.state[key] === "boolean" ? C17.state[key] : false;
}
var rows = typeof C17.state.qs_card_grid_rows === "string" ? C17.state.qs_card_grid_rows : "4";
var mode = typeof C17.state.qs_card_small_tiles_mode === "string" ? C17.state.qs_card_small_tiles_mode : "always";
var opacity = typeof C17.state.qs_card_wallpaper_opacity === "number" ? C17.state.qs_card_wallpaper_opacity : 100;

plugin.ui = [
  {
    "type": "text",
    "title": "控制中心扩展 · 需要 C17 2.0.4",
    "description": "各项独立设置；布局和下滑显示适用于已适配的竖屏控制中心。旧版验证模式更新后需逐项重设。图片请在本详情的音乐卡片壁纸入口选择；无图片保留原生。停用恢复原设置，安全模式暂停覆盖。"
  },
  {
    "type": "select",
    "key": "qs_card_grid_rows",
    "title": "顶部空间（4列）",
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
    "title": "4×2 大音乐卡片",
    "description": "仅对完成适配的音乐卡片扩展显示，系统保存原尺寸。",
    "default": false
  },
  {
    "type": "switch",
    "key": "qs_card_native_resize_enabled",
    "title": "原生卡片拖拽调整",
    "description": "允许已适配卡片调整至4×2，未知外部插件保持原生。",
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
    "title": "底部普通磁贴区域",
    "description": "下滑模式进入时默认隐藏；在竖屏控制中心空白处再次下滑展开、上滑收起，编辑布局时显示可添加磁贴。",
    "default": "always",
    "options": [
      {
        "value": "always",
        "label": "始终显示"
      },
      {
        "value": "hidden",
        "label": "始终隐藏"
      },
      {
        "value": "swipe",
        "label": "下滑展开、上滑收起"
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
