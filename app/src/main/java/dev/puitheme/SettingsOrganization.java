// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Presentation only: destinations and folding never project, reset or save runtime settings. */
public final class SettingsOrganization {
    private static final Map<String, String> GUIDES;
    static {
        Map<String, String> guides = new LinkedHashMap<>();
        guides.put(QsWifiAlias.GROUP,"只修改控制中心 WiFi 名称。列表来自系统允许读取的已保存网络或模块实际识别过的连接；不会修改 SSID、密码、接入点或网络配置。");
        guides.put("statusbar_layout", "只管理元素区域与顺序；左右间距在各元素页独立设置，无需开启自定义排序。");
        guides.put("speed", "显示样式、大小、位置与刷新速度分别管理；左右间距独立于网速外观开关。");
        guides.put("data", "信号外形与布局分别设置；制式显示方式、角标与数据小箭头在网络组合中管理。");
        guides.put("network", "Wi-Fi 与独立制式文字共用位置和左右间距，大小各自设置；原生角标保持信号角位置并独立微调。Wi-Fi 显示与自定义分别开关；制式文字关闭后转为原生角标，隐藏需单独选择。");
        guides.put("clock", "默认时钟和双排时钟互斥；通用设置中的等宽、冒号和左右间距同时供两种模式使用。");
        guides.put("carrier", "通知、控制中心、锁屏各自开关；共用左右间距单独保存。");
        guides.put("font", "在“其他 → 字体配置”先选字体，再选作用范围并统一应用；未选范围保留原字体，字号、粗细与颜色仍在各功能页设置。");
        guides.put("battery", "样式、内文字、布局与颜色分标签；每种独立颜色有自己的开关。系统百分比关闭时保留所选样式。");
        guides.put("notification_icons", "显示方式决定图片、文字和原生图标参数；图标数量与指定应用替换分别管理。");
        guides.put("status_hint_icons", "显示数量与保留优先级在本页；左右位置与逐个排序在排列页面，不会强制显示系统隐藏的图标。");
        guides.put("privacy_dot", "只影响屏幕角落的隐私小点和摄像头处的胶囊；下拉面板里的隐私图标、系统的使用记录和权限管理不受影响。");
        guides.put("notification_icon_overrides", "逐应用规则独立保存；单个文字或图片显示方式会暂停这些规则。");
        guides.put("status_icon_library", "先选图标库，再选应用范围；单项选择优先，颜色和透明度独立设置。关闭总开关暂停图案、颜色和动画，保留配置与图标库；重开后恢复。未适配状态保留原生。");
        guides.put("qs_appearance", "浅色和深色分别设置底色与渐变；圆角、铺满和图标大小是独立功能。");
        guides.put("tile_2x1_fill", "只管理 2×1 磁贴背景铺满；颜色、圆角与点击行为继续由各自功能管理。");
        guides.put("tile_corners", "磁贴、滑块、音乐和设备卡片共用圆角，范围为 0–30 dp。");
        guides.put("tile_icon_size", "只调整 1×1 磁贴和设备图标，保留原生点击区域与电量环。");
        guides.put("qs_entrances", "编辑和设置按钮分别隐藏；允许隐藏后点击是独立的附加选择。");
        guides.put("shade_clock", "通知中心与控制中心共用这组小时间；通知大时钟显示时会暂停通知页的小时间，控制中心不受影响。");
        String big = "按时间、外观、日期、左右组件、动效和底部分组。背景透出优先于其他材质；各类文字的遮罩强度独立保存，只有主时间使用立体边缘。";
        guides.put("notification_big_clock", big);
        guides.put("notification_big_clock_landscape", "横屏有独立开关和参数；" + big.replace("左右组件、", ""));
        guides.put("notification_stack", "整列堆叠需要对应方向的大时钟；横屏另需许可。自动隐藏小堆叠只在外层实际覆盖通知时发生。");
        guides.put("notification_mini_stack", "同应用小堆叠独立于大时钟和整列堆叠，两种堆叠各自开关；自动隐藏只在实际被整列覆盖时发生。");
        guides.put("notification_clear", "竖屏颜色与入场效果服从本页开关，保留原生清除动作；横屏位置与颜色在横屏页独立设置。");
        guides.put("notification_clear_landscape", "横屏位置与颜色分别开关；关闭位置不会关闭已保存的颜色选择。");
        guides.put("qs_media", "卡片背景、小封面发光和换封面过渡分别收纳；自定义颜色优先于主色，主色优先于封面图片。");
        guides.put("c17_highlight_removal", "去除高光和统一通知颜色是两个独立功能；关闭去高光不会代替关闭统一颜色。");
        guides.put("classic_text", "经典面板的运营商与时间分别开关；在分离面板暂停，已保存的参数保留。");
        guides.put("shade_wallpaper_portrait", "功能未完成。竖屏方向、各面板和大时钟区域分别设置；不要用重置代替关闭。");
        guides.put("shade_wallpaper_landscape", "功能未完成。横屏配置独立于竖屏；当前面板类型之外的配置继续保存。");
        guides.put("lockscreen_date", "只替换锁屏日期内容，不改变通知页或控制中心的日期。");
        guides.put("lockscreen_lock_icon", "只隐藏小锁图案，保留指纹、解锁触控与安全行为。");
        guides.put("lockscreen_status_blur", "只模糊状态栏后方壁纸；模糊范围与颜色遮罩分别设置，文字和图标保持清晰。");
        guides.put("fluid_cloud_accent", "媒体强调色、遮罩与跟随规则分别管理；仅播放时跟随也供锁屏和 AOD 使用。");
        guides.put("fluid_cloud_style", "通知类材质、流体云外观和适配范围分标签；按可识别的业务分类选择本页样式的作用范围，未识别卡片归入“其他与未识别”。作用于悬浮展开、锁屏和 AOD，通知栏场景已停用。");
        guides.put("fluid_cloud_capsule", "底色、边框、形状与内容分标签；常驻显示独立于外观开关，关闭外观不关闭常驻。");
        guides.put("lock_awake", "亮屏渐变、自定义和普通通知跟随分别开关；统一样式使用各场景自己的卡片效果，不复制 AOD 参数。");
        guides.put("lock_aod", "AOD 自定义与通知白色玻璃分别设置；亮屏和 AOD 各自保存，桌面悬浮卡片、日期及壁纸模糊独立管理。");
        GUIDES = Collections.unmodifiableMap(guides);
    }
    private SettingsOrganization() {}
    public static Map<String, String> guides() { return GUIDES; }
    public static String guide(String id) { String guide = GUIDES.get(id); return guide == null ? "" : guide; }
    /** A full block folds, never a lone slider. Ordinary controls remain visible by default. */
    public static boolean advanced(String group, String section) {
        String title = section.contains(" · ") ? section.substring(section.lastIndexOf(" · ") + 3) : section;
        if (("label".equals(group)||"network".equals(group)) && section.contains("自定义显示内容")) return true;
        if ("fluid_cloud_capsule".equals(group)) return Arrays.asList("跑马灯", "向内渐隐", "出现回弹", "实时频谱").contains(section);
        if (group.startsWith("notification_big_clock")) return Arrays.asList("锁屏质感", "玻璃边框", "主时间立体边缘", "收起样式", "收起位置", "回弹效果", "通知顶部边缘").contains(section);
        if (group.startsWith("shade_wallpaper")) return "大时钟区域".equals(section);
        if ("qs_media".equals(group)) return "过渡".equals(section) || title.equals("外发光");
        return Arrays.asList("颜色与透明度", "渐变效果", "电池边界距离", "文字样式", "文字与间距", "数字对齐", "刷新速度", "充电切换").contains(title);
    }
}
