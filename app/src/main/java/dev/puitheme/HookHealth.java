// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which SystemUI hook groups really installed, as SystemUI itself reported with a verified runtime probe.
 * The report carries only fixed identifiers and counts; the labels and the effect on settings live here, in the app.
 */
final class HookHealth {
    static final String KEY = "hook_health";
    /** "id=count" for every hook group, where count is the number of hooks that were created. */
    static final String GROUPS = "groups";
    /** Ids of the groups whose installer finished and was recorded as complete. */
    static final String COMPLETE = "complete";
    /** "name=on|off|failed" for each hook that is installed only while its feature is on. */
    static final String LAZY = "lazy";
    private static final int MAX_ITEMS = 64;

    enum Severity { OK, INFO, WARN, ERROR }

    static final class Finding {
        final String id, title, detail; final Severity severity;
        Finding(String id, Severity severity, String title, String detail) {
            this.id = id; this.severity = severity; this.title = title; this.detail = detail;
        }
    }

    private static final class Group {
        final String label, impact; final boolean onDemand;
        Group(String label, String impact, boolean onDemand) { this.label = label; this.impact = impact; this.onDemand = onDemand; }
    }

    private static final Map<String, Group> GROUP_INFO = new LinkedHashMap<>();
    private static final Map<String, Group> LAZY_INFO = new LinkedHashMap<>();
    private static void group(String id, String label, String impact) { GROUP_INFO.put(id, new Group(label, impact, false)); }
    private static void lazy(String id, String label, String impact) { LAZY_INFO.put(id, new Group(label, impact, true)); }
    static {
        group("context", "基础环境", "所有功能都依赖它；缺失时模块基本不工作");
        group("panel_mode", "面板模式识别", "下拉面板的单双面板判断，影响通知中心与控制中心相关设置");
        group("shade_wallpaper", "下拉面板壁纸", "下拉面板壁纸");
        group("model", "网络与电池状态", "状态栏网络、电池相关功能取不到状态");
        group("drawable", "状态栏图标绘制", "状态栏图案、颜色与透明度");
        group("badge", "角标隐藏", "网络角标隐藏");
        group("wifi", "Wi-Fi 图标", "Wi-Fi 图标大小、位置与样式");
        group("speed", "网速", "网速显示与位置");
        group("compose", "状态栏 Compose 适配", "新版 Compose 状态栏上的文字、图标与排序");
        group("network_order", "网络图标排序", "Wi-Fi、蜂窝、数据图标的顺序与位置");
        group("status_flow", "状态栏排序与边距", "状态栏排序（实验性）、左右间距");
        group("status_hints", "系统提示图标", "蓝牙、定位等提示图标的数量与间距");
        group("text", "时钟与文字", "状态栏时钟、运营商文字、锁屏时钟与文字样式");
        group("battery", "电池", "电池图标、百分比与充电样式");
        group("data_spacing", "数据与电池间距", "网络与电池之间的间距");
        group("tiles", "控制中心磁贴翻页", "磁贴页切换效果（可按需安装）");
        group("qs_appearance", "磁贴颜色", "控制中心磁贴底色、渐变与透明度");
        group("qs_full_fill", "2×1 磁贴铺满", "2×1 磁贴铺满外观");
        group("qs_corners", "磁贴圆角", "磁贴与按钮圆角");
        group("qs_icon_size", "磁贴图标大小", "控制中心磁贴图标大小");
        group("icon_management", "图标管理", "控制中心图案、颜色、透明度与已接入的动态图标");
        group("qs_panel_corners", "面板圆角", "控制中心与通知面板圆角");
        group("qs_media", "音乐卡片", "控制中心音乐卡片封面背景");
        group("big_clock", "大时钟", "通知中心大时钟、日期、左右文字组件与背景");
        group("notification_clear", "清除按钮", "通知清除按钮外观");
        group("notification_icons", "通知图标", "状态栏通知图标显示");
        group("notification_row_icons", "通知行图标", "通知卡片内的应用图标");
        group("c17_highlights", "高光去除", "通知与卡片高光去除");
        group("lockscreen_blur", "锁屏模糊与背景", "锁屏、AOD 的模糊与流体云背景");
        group("fluid_accent", "流体云强调色", "流体云跟随媒体色、锁屏与 AOD 外观");
        group("fluid_media", "流体云音乐卡", "音乐流体云卡片绘制（可按需安装）");
        group("fluid_notification", "流体云样式", "流体云样式、通知跟随");
        group("mini_stack", "同应用小堆叠", "同应用通知小堆叠");

        lazy("statusbar_flow_layout", "状态栏排序布局", "状态栏排序（实验性）与左右间距");
        lazy("privacy_indicator", "隐私小点", "去除隐私小点与摄像头处的使用中胶囊");
        lazy("status_hint_child_index", "提示图标布局", "系统提示图标的数量、间距与排序");
        lazy("network_order_child_index", "网络图标顺序", "Wi-Fi、蜂窝、数据图标的顺序");
        lazy("network_shared_native_placement", "网络共用位置", "网络图标的共用位置");
        lazy("shade_wallpaper", "下拉面板壁纸绘制", "下拉面板壁纸");
        lazy("qs_media_child", "音乐卡片子视图", "控制中心音乐卡片封面背景");
        lazy("notification_mini_stack", "同应用小堆叠", "同应用通知小堆叠");
        lazy("lock_hide", "锁屏元素隐藏", "锁屏时间、日期、提示等元素的隐藏");
        lazy("device_gradient", "卡片渐变", "流体云原生渐变");
        lazy("capsule_bounce", "胶囊出现回弹", "流体云胶囊的出现回弹");
    }

    private HookHealth() { }

    static String label(String id) {
        Group group = GROUP_INFO.get(id);
        if (group == null) group = LAZY_INFO.get(id);
        return group == null ? id : group.label;
    }

    static boolean knownGroup(String id) { return GROUP_INFO.containsKey(id); }
    static List<String> groupIds() { return Collections.unmodifiableList(new ArrayList<>(GROUP_INFO.keySet())); }

    /** Typed, bounded copy of a report; anything unexpected is dropped. */
    static Bundle copy(Bundle source) {
        if (source == null) return null;
        Bundle copy = new Bundle();
        copy.putStringArrayList(GROUPS, entries(source.getStringArrayList(GROUPS), "[a-z][a-z0-9_]{0,31}=[0-9]{1,5}"));
        copy.putStringArrayList(COMPLETE, entries(source.getStringArrayList(COMPLETE), "[a-z][a-z0-9_]{0,31}"));
        copy.putStringArrayList(LAZY, entries(source.getStringArrayList(LAZY), "[a-z][a-z0-9_]{0,47}=(on|off|failed)"));
        for (String key : new String[]{"custom_plugin_requested","custom_plugin_resolved","custom_plugin_installed","custom_plugin_unavailable"})
            if (source.containsKey(key)) copy.putInt(key,Math.max(0,Math.min(64,source.getInt(key,0))));
        for (String key : new String[]{"custom_plugin_revision","custom_plugin_hits"})
            if (source.containsKey(key)) copy.putLong(key,Math.max(0L,source.getLong(key,0L)));
        if (source.containsKey("custom_plugin_active")) copy.putBoolean("custom_plugin_active",source.getBoolean("custom_plugin_active",false));
        for (String key : new String[]{"custom_plugin_runtime_requested","custom_plugin_runtime_loaded",
                "custom_plugin_runtime_hooks","custom_plugin_runtime_pending","custom_plugin_runtime_retained_guards"})
            if (source.containsKey(key)) copy.putInt(key,Math.max(0,source.getInt(key,0)));
        for (String key : new String[]{"custom_plugin_runtime_revision","custom_plugin_runtime_errors","custom_plugin_runtime_hits"})
            if (source.containsKey(key)) copy.putLong(key,Math.max(0L,source.getLong(key,0L)));
        if (source.containsKey("custom_plugin_runtime_active"))
            copy.putBoolean("custom_plugin_runtime_active",source.getBoolean("custom_plugin_runtime_active",false));
        String runtimeError = source.getString("custom_plugin_runtime_last_error");
        if (runtimeError != null) copy.putString("custom_plugin_runtime_last_error",runtimeError.substring(0,Math.min(1024,runtimeError.length())));
        return copy;
    }

    private static ArrayList<String> entries(List<String> raw, String pattern) {
        ArrayList<String> result = new ArrayList<>();
        if (raw != null) for (String value : raw) {
            if (result.size() >= MAX_ITEMS) break;
            if (value != null && value.matches(pattern)) result.add(value);
        }
        return result;
    }

    /** Each group's hook count, 0 when it never installed anything. */
    static Map<String, Integer> counts(Bundle report) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (report == null) return result;
        List<String> raw = report.getStringArrayList(GROUPS);
        if (raw != null) for (String entry : raw) {
            int split = entry.indexOf('=');
            if (split <= 0) continue;
            try { result.put(entry.substring(0, split), Integer.parseInt(entry.substring(split + 1))); }
            catch (NumberFormatException ignored) { }
        }
        return result;
    }

    static Map<String, String> lazyStates(Bundle report) {
        Map<String, String> result = new LinkedHashMap<>();
        if (report == null) return result;
        List<String> raw = report.getStringArrayList(LAZY);
        if (raw != null) for (String entry : raw) {
            int split = entry.indexOf('=');
            if (split > 0) result.put(entry.substring(0, split), entry.substring(split + 1));
        }
        return result;
    }

    /** Plain-language findings. {@code report} null: SystemUI did not answer, so nothing can be said about hooks. */
    static List<Finding> findings(Bundle report) {
        List<Finding> result = new ArrayList<>();
        if (report == null) {
            result.add(new Finding("hooks_unknown", Severity.INFO, "还没有拿到系统界面的挂钩状态",
                    "模块未在系统界面里生效，或系统界面还没有回应。先在主页确认“当前版本”已生效，再回来刷新。"));
            return result;
        }
        Map<String, Integer> counts = counts(report);
        List<String> complete = report.getStringArrayList(COMPLETE);
        List<String> broken = new ArrayList<>(), ready = new ArrayList<>();
        int hooks = 0;
        for (String id : GROUP_INFO.keySet()) {
            Group info = GROUP_INFO.get(id);
            int count = counts.containsKey(id) ? counts.get(id) : 0;
            hooks += count;
            boolean done = count > 0 || (complete != null && complete.contains(id));
            if (done) ready.add(id);
            else if (!info.onDemand && !"tiles".equals(id) && !"fluid_media".equals(id)) broken.add(id);
        }
        if (broken.isEmpty()) {
            result.add(new Finding("hooks_ok", Severity.OK, "挂钩全部就绪",
                    ready.size() + " 组功能已安装，共 " + hooks + " 个挂钩。"));
        } else {
            boolean base = broken.contains("context");
            StringBuilder detail = new StringBuilder("以下功能的挂钩没有装上，对应设置会无效：");
            for (String id : broken) detail.append("\n· ").append(label(id)).append("：").append(GROUP_INFO.get(id).impact);
            detail.append("\n常见原因是系统界面更新后内部类名变化，或这台设备的系统界面没有对应结构。开启诊断日志后重启系统界面，把导出的报告发给作者。");
            result.add(new Finding("hooks_missing", base ? Severity.ERROR : Severity.WARN,
                    broken.size() + " 组功能的挂钩未就绪", detail.toString()));
        }
        Map<String, String> lazy = lazyStates(report);
        List<String> failed = new ArrayList<>();
        for (Map.Entry<String, String> entry : lazy.entrySet()) if ("failed".equals(entry.getValue())) failed.add(entry.getKey());
        if (!failed.isEmpty()) {
            StringBuilder detail = new StringBuilder("这些按需挂钩在需要时安装失败：");
            for (String name : failed) detail.append("\n· ").append(label(name)).append("：").append(effect(name));
            result.add(new Finding("lazy_failed", Severity.WARN, failed.size() + " 个按需挂钩安装失败", detail.toString()));
        }
        int pluginRequested = report.getInt("custom_plugin_requested",0);
        if (pluginRequested > 0) {
            int missing = report.getInt("custom_plugin_unavailable",pluginRequested);
            if (!report.getBoolean("custom_plugin_active",false))
                result.add(new Finding("custom_plugins_paused",Severity.INFO,"扩展插件系统规则已暂停","安全模式或模块停用时已恢复原生外观。"));
            else if (missing > 0)
                result.add(new Finding("custom_plugins_missing",Severity.WARN,"扩展插件有 " + missing + " 条规则未就绪",
                        "共 " + pluginRequested + " 条规则；请核对插件要求的系统版本与类名、方法。未匹配的规则保留原生行为。"));
            else result.add(new Finding("custom_plugins_ready",Severity.INFO,"扩展插件系统规则已加载",
                    pluginRequested + " 条规则，" + report.getInt("custom_plugin_installed",0) + " 个挂钩；回调累计命中 "
                            + report.getLong("custom_plugin_hits",0L) + " 次。实际外观仍需在对应界面确认。"));
        }
        int runtimeRequested = report.getInt("custom_plugin_runtime_requested",0);
        if (runtimeRequested > 0) {
            int loaded = report.getInt("custom_plugin_runtime_loaded",0);
            if (!report.getBoolean("custom_plugin_runtime_active",false))
                result.add(new Finding("custom_plugin_runtime_paused",Severity.INFO,"扩展插件运行已暂停","安全模式或模块停用时，宿主停止插件并调用其清理逻辑。"));
            else if (loaded < runtimeRequested) {
                String error = report.getString("custom_plugin_runtime_last_error","");
                result.add(new Finding("custom_plugin_runtime_pending",error.isEmpty() ? Severity.INFO : Severity.WARN,
                        "扩展插件已运行 " + loaded + "/" + runtimeRequested,
                        error.isEmpty() ? "资源可能仍在后台加载；稍后刷新。如持续未运行，请检查插件与系统版本。"
                                : "插件运行未完成：" + error));
            } else result.add(new Finding("custom_plugin_runtime_ready",Severity.INFO,"扩展插件运行已加载",
                    loaded + " 个插件，" + report.getInt("custom_plugin_runtime_hooks",0) + " 个受管理挂钩。实际交互仍需在对应界面确认。"));
        }
        return result;
    }

    private static String effect(String lazyName) {
        Group group = LAZY_INFO.get(lazyName);
        return group == null ? "对应功能可能无效" : group.impact;
    }

    /** Every group in a stable order with its state, for the detail list. */
    static List<String[]> table(Bundle report) {
        List<String[]> rows = new ArrayList<>();
        if (report == null) return rows;
        Map<String, Integer> counts = counts(report);
        List<String> complete = report.getStringArrayList(COMPLETE);
        for (String id : GROUP_INFO.keySet()) {
            int count = counts.containsKey(id) ? counts.get(id) : 0;
            boolean done = count > 0 || (complete != null && complete.contains(id));
            rows.add(new String[]{id, GROUP_INFO.get(id).label, done ? "就绪" : GROUP_INFO.get(id).onDemand || "tiles".equals(id) || "fluid_media".equals(id) ? "按需" : "未就绪",
                    count > 0 ? count + " 个挂钩" : ""});
        }
        for (Map.Entry<String, String> entry : lazyStates(report).entrySet())
            rows.add(new String[]{entry.getKey(), label(entry.getKey()), "on".equals(entry.getValue()) ? "已装" : "failed".equals(entry.getValue()) ? "失败" : "未启用", "按需"});
        return rows;
    }

    static String toId(Enum<?> constant) { return constant.name().toLowerCase(Locale.ROOT); }
}
