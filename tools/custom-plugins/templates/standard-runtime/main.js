// SPDX-License-Identifier: MIT
var plugin = {
  "id": "dev.example.configuration-journal",
  "name": "配置记录开发示例",
  "version": "1.0.0",
  "versionCode": 1,
  "apiVersion": 2,
  "description": "开发模板：按开关记录配置摘要，展示设置分类、管理状态与一次性操作。不是 ROM 适配插件。",
  "author": "C17 contributors",
  "capabilities": [
    "runtime"
  ],
  "minimumHost": 261,
  "features": [
    "runtime.actions",
    "runtime.status",
    "ui.group-paths",
    "ui.management-groups",
    "ui.action-confirmation",
    "scope.systemui"
  ],
  "scopes": [
    "com.android.systemui"
  ],
  "runtime": {
    "entry": "runtime.js"
  },
  "usage": "说明页申请所需作用域并按需重启。设置页启用插件后，可开启自动记录并修改备注；管理页查看或手动保存最近记录。关闭插件停止记录，已有记录在再次启用时保留。"
};

plugin.ui = [
  {
    "type": "switch",
    "key": "record_changes",
    "title": "自动记录配置变化",
    "default": false,
    "section": "features",
    "groupPath": [
      "记录设置"
    ]
  },
  {
    "type": "input",
    "key": "note",
    "title": "记录备注",
    "default": "我的配置",
    "section": "features",
    "groupPath": [
      "记录设置",
      "备注"
    ]
  },
  {
    "type": "status",
    "key": "journal_status",
    "title": "最近记录",
    "section": "management",
    "groupPath": [
      "运行状态"
    ]
  },
  {
    "type": "action",
    "key": "save_record",
    "title": "保存当前记录",
    "section": "management",
    "groupPath": [
      "运行状态"
    ]
  },
  {
    "type": "action",
    "key": "clear_record",
    "title": "清空记录",
    "confirmation": "清空本示例保存的最近记录？配置备注和自动记录开关不变。",
    "section": "management",
    "groupPath": [
      "记录管理"
    ]
  }
];
plugin.settings = {};
plugin.hooks = [];
C17.define(plugin);
