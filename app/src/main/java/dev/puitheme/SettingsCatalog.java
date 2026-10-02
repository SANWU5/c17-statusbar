// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;

/** User-facing descriptors shared by the Compose configuration pages. No View or Activity state. */
public final class SettingsCatalog {
    public static final String STATUSBAR = "statusbar", NOTIFICATION = "notification", CONTROL_CENTER = "control_center", LOCK_SCREEN = "lockscreen";
    public static final String BOOLEAN = "boolean", NUMERIC = "numeric", STRING = "string";
    public static final String COLOR = "color", FONT = "font", IMAGE = "image", OPTIONS = "options";
    private static final String[] CLOCK_VALUES = {"HH:mm", "HH:mm:ss", "{上午下午} hh:mm", "{时段} HH:mm", "M月d日 {周} HH:mm", "yyyy年MM月dd日 {星期} HH:mm", "MM/dd HH:mm"};
    private static final String[] CLOCK_LABELS = {"时分 · 24 小时", "时分秒 · 24 小时", "上午／下午 · 12 小时", "时段与时间", "月日、星期与时间", "完整日期、星期与时间", "短日期与时间"};
    private static final String[] DATE_VALUES = {NotificationBigClockSettings.DATE_DEFAULT, "M月d日 EEEE", "yyyy年M月d日 {星期}", "M月d日 {周} {农历}", "{干支} {农历日期}"};
    private static final String[] DATE_LABELS = {"月日、星期、干支与农历", "月日与星期", "完整日期与星期", "月日、星期与农历", "干支年与农历日期"};
    private static final String[] FOOTER_VALUES = {"{text}", "HH:mm {text}", "yyyy年M月d日 {周} {text}", "{农历} {text}", "'C17' HH:mm {text}", "✨ {text}"};
    private static final String[] FOOTER_LABELS = {"纯文本", "时间与文字", "日期、星期与文字", "农历与文字", "C17、时间与文字", "星光与文字"};
    private static final List<Group> GROUPS;
    private static final Map<String, Group> GROUP_BY_ID;
    private static final Map<String, Item> ITEM_BY_KEY;
    private static final Set<String> HIDDEN;

    public static final class Group {
        public final String id, key, title, description, category, masterKey;
        public final List<Item> items;
        private Group(Builder builder) {
            id = key = builder.id;
            title = builder.title; description = builder.description;
            category = builder.category; masterKey = builder.master;
            items = Collections.unmodifiableList(new ArrayList<>(builder.items));
        }
    }

    /** A short, flat list of destinations belonging to the same screen or task. */
    public static final class NavigationSection {
        public final String title;
        public final List<Group> groups;
        private NavigationSection(String title, String... ids) {
            this.title = title;
            List<Group> result = new ArrayList<>();
            for (String id : ids) result.add(group(id));
            groups = Collections.unmodifiableList(result);
        }
    }

    /** Scene tabs only partition long forms; they never create or change setting keys. */
    public static final class DetailPage {
        public final String id, title;
        public final List<Item> items;
        private DetailPage(String id, String title, List<Item> items) {
            this.id = id; this.title = title;
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
        }
    }

    public static final class Item {
        public final String key, title, description, type, section, unit, groupId;
        /** Suggested slider interval; persisted values are not clipped to these suggestions. */
        public final float min, max, step;
        /** Optional presets for string fields, or the complete values for an options field. */
        public final String[] values, labels;
        /** For colors, the matching other theme, theme name, and separately persisted alpha mode. */
        public final String pairedKey, scene, alphaKey;
        public final boolean master, allowsCustomValue;
        public final Object defaultValue;
        private Item(Builder group, String key, String title, String description, String type,
                String section, String unit, float min, float max, float step,
                String[] values, String[] labels, String pairedKey, String scene) {
            this.key = key; this.title = title; this.description = description; this.type = type;
            this.section = organizedSection(group.id, key, section); this.unit = unit; this.min = min; this.max = max; this.step = step;
            this.values = values == null ? new String[0] : values.clone();
            this.labels = labels == null ? new String[0] : labels.clone();
            if (this.values.length != this.labels.length) throw new IllegalArgumentException("Preset labels mismatch: " + key);
            this.pairedKey = pairedKey == null ? "" : pairedKey;
            this.scene = scene == null ? "" : scene;
            alphaKey = COLOR.equals(type) ? StatusBarSettings.alphaKey(key) : "";
            groupId = group.id; master = key.equals(group.master);
            allowsCustomValue = STRING.equals(type) || NUMERIC.equals(type);
            defaultValue = SettingsCatalog.defaultValue(key);
        }
    }

    private static final class Builder {
        final String id, title, description, category, master;
        final List<Item> items = new ArrayList<>();
        Builder(String id, String title, String description, String category, String master) {
            this.id = id; this.title = title; this.description = description;
            this.category = category; this.master = master;
            toggle(master, "启用" + title, description, "功能开关");
        }
        void toggle(String key, String title, String description, String section) {
            add(key, title, description, BOOLEAN, section, "", 0, 1, 1, null, null, null, null);
        }
        void number(String key, String title, String section, String unit, float min, float max, float step) {
            String description = key.contains("offset_") ? "相对于系统默认位置" : key.endsWith("_weight") || key.equals(StatusBarSettings.FONT_WEIGHT) ? "100 最细，900 最粗" : "";
            add(key, title, description, NUMERIC, section, unit, min, max, step, null, null, null, null);
        }
        void choice(String key, String title, String description, String section, String[] values, String[] labels) {
            add(key, title, description, OPTIONS, section, "", 0, 0, 0, values, labels, null, null);
        }
        void text(String key, String title, String description, String section, String[] values, String[] labels) {
            add(key, title, description, STRING, section, "", 0, 0, 0, values, labels, null, null);
        }
        void colorPair(String light, String dark, String title, String section) {
            add(light, title + " · 浅色背景", "用于浅色背景，可独立设置颜色与透明度", COLOR, section, "", 0, 0, 0, null, null, dark, "light");
            add(dark, title + " · 深色背景", "用于深色背景，可独立设置颜色与透明度", COLOR, section, "", 0, 0, 0, null, null, light, "dark");
        }
        void add(String key, String title, String description, String type, String section, String unit,
                float min, float max, float step, String[] values, String[] labels, String pairedKey, String scene) {
            items.add(new Item(this, key, title, description, type, section, unit, min, max, step, values, labels, pairedKey, scene));
        }
    }

    static {
        Set<String> hidden = new LinkedHashSet<>(Arrays.asList(
                StatusBarSettings.DATA_ACTIVITY_HIDDEN, StatusBarSettings.SAFE_MODE,
                StatusBarSettings.DIAGNOSTICS_ENABLED, StatusBarSettings.FONT_REVISION, NotificationIconArea.IMAGE_REVISION,
                NotificationBigClockSettings.STATUS_ICONS_FIXED, NotificationBigClockSettings.COMPACT_WEIGHT,
                NotificationBigClockSettings.STACK_GAP, NotificationBigClockSettings.STACK_INSET,
                StatusBarSettings.TILES_FADE_RANGE,
                "carrier_position_enabled", "carrier_size_enabled", "carrier_color_enabled",
                "carrier_text_style_enabled", "carrier_replace_enabled",
                StatusBarSettings.CARRIER_MODE, StatusBarSettings.CARRIER_PATTERN, StatusBarSettings.CARRIER_TEXT,
                StatusBarSettings.CARRIER_OFFSET_X, StatusBarSettings.CARRIER_OFFSET_Y,
                StatusBarSettings.CARRIER_SCALE, StatusBarSettings.CARRIER_WEIGHT, StatusBarSettings.CARRIER_SPACING,
                "carrier_color_light", "carrier_color_dark"));
        HIDDEN = Collections.unmodifiableSet(hidden);
        List<Group> groups = new ArrayList<>();
        groups.add(speed()); groups.add(data()); groups.add(wifi()); groups.add(label());
        groups.add(clock("clock", STATUSBAR)); groups.add(carrier()); groups.add(font()); groups.add(battery()); groups.add(notificationIcons());
        groups.add(tiles()); groups.add(qs()); groups.add(tileCorners()); groups.add(tileIconSize()); groups.add(shadeIcons()); groups.add(clock("shade_clock", NOTIFICATION));
        groups.add(bigClock()); groups.add(clear()); groups.add(media());
        Map<String, Group> byId = new LinkedHashMap<>();
        Map<String, Item> byKey = new LinkedHashMap<>();
        for (Group group : groups) {
            if (byId.put(group.id, group) != null) throw new IllegalStateException("Duplicate settings group: " + group.id);
            for (Item item : group.items) {
                if (hidden.contains(item.key)) throw new IllegalStateException("Hidden setting exposed: " + item.key);
                if (byKey.put(item.key, item) != null) throw new IllegalStateException("Duplicate settings item: " + item.key);
            }
        }
        GROUPS = Collections.unmodifiableList(groups);
        GROUP_BY_ID = Collections.unmodifiableMap(byId);
        ITEM_BY_KEY = Collections.unmodifiableMap(byKey);
    }

    private static Builder builder(String id, String title, String description, String category, String master) {
        return new Builder(id, title, description, category, master);
    }
    private static String organizedSection(String groupId, String key, String section) {
        if ("battery".equals(groupId) && (BatteryTextStyle.MASTER.equals(key) || BatteryTextStyle.NUMBERS.containsKey(key))) return "电池内文字";
        String theme = key.contains("_light_") ? "浅色界面 · " : key.contains("_dark_") ? "深色界面 · " : "";
        if (!theme.isEmpty() && "qs_appearance".equals(groupId)) return theme + (key.contains("gradient") ? "渐变" : "底色");
        if (!theme.isEmpty() && "qs_media".equals(groupId)) return theme + (key.contains("background") ? "背景" : "外发光");
        if ("notification_big_clock".equals(groupId)) {
            if (NotificationBigClockSettings.OFFSET_X.equals(key) || NotificationBigClockSettings.OFFSET_Y.equals(key)) return "初始位置";
            if (NotificationBigClockSettings.COMPACT_OFFSET_X.equals(key) || NotificationBigClockSettings.COMPACT_OFFSET_Y.equals(key)) return "收起位置";
            if (NotificationBigClockSettings.COMPACT_MAX_SIZE.equals(key) || NotificationBigClockSettings.COMPACT_SCALE.equals(key)) return "收起样式";
            if (NotificationBigClockSettings.MAX_SIZE.equals(key) || NotificationBigClockSettings.SCALE.equals(key)) return "展开样式";
            if (NotificationBigClockSettings.PATTERN.equals(key) || NotificationBigClockSettings.FONT.equals(key)) return "时间格式";
            if (NotificationBigClockSettings.WEIGHT.equals(key) || NotificationBigClockSettings.LETTER_SPACING.equals(key)) return "文字样式";
        }
        String scene = section.contains(" · ") ? section.substring(0, section.indexOf(" · ")) + " · " : "";
        if (key.endsWith("_position_enabled")) return scene + "位置";
        if (key.endsWith("_size_enabled")) return scene + ("carrier".equals(groupId) || groupId.endsWith("clock") ? "文字样式" : "大小");
        if (key.endsWith("_color_enabled") && (section.endsWith("独立开关") || "独立开关".equals(section))) return scene + "颜色与透明度";
        if (key.endsWith("_text_style_enabled")) return scene + ("speed".equals(groupId) || "label".equals(groupId) ? "文字与间距" : "文字样式");
        if ("speed_lines_enabled".equals(key)) return "文字与间距";
        if ("独立开关".equals(section)) return "显示样式";
        return section;
    }
    private static void controls(Builder b, String prefix, String section, boolean text) {
        b.toggle(prefix + "_position_enabled", "位置调整", "应用所设置的水平与垂直位置", section);
        b.toggle(prefix + "_size_enabled", "大小调整", "应用所设置的大小比例", section);
        b.toggle(prefix + "_color_enabled", "颜色调整", "浅色与深色背景分别设置", section);
        if (text) b.toggle(prefix + "_text_style_enabled", "文字粗细与字距", "应用所设置的文字样式", section);
    }
    private static void position(Builder b, String prefix, String section) {
        b.number(prefix + "_offset_x", "水平位置", section, "dp", -80, 80, .01f);
        b.number(prefix + "_offset_y", "垂直位置", section, "dp", -24, 24, .01f);
    }
    private static void scale(Builder b, String key, String title, String section) {
        b.number(key, title, section, "%", 25, 250, .01f);
    }
    private static void textSize(Builder b, String prefix, String section) {
        scale(b, prefix + "_scale", "文字大小", section);
        b.number(prefix + "_weight", "文字粗细", section, "", 100, 900, 1);
        b.number(prefix + "_spacing", "字距微调", section, "dp", -2, 8, .01f);
    }
    private static void normalColors(Builder b, String prefix, String title) {
        b.colorPair(prefix + "_color_light", prefix + "_color_dark", title, "颜色与透明度");
    }
    private static void alignment(Builder b, String key, String title, String section) {
        b.choice(key, title, "", section, new String[]{"left", "center", "right"}, new String[]{"靠左", "居中", "靠右"});
    }
    private static Group speed() {
        Builder b = builder("speed", "实时网速", "保留系统实时数值与 KB/s 单位。", STATUSBAR, "speed_enabled");
        controls(b, "speed", "独立开关", true);
        b.toggle("speed_lines_enabled", "数字与单位间距", "分别安排数字与单位的间距", "独立开关");
        position(b, "speed", "位置");
        scale(b, StatusBarSettings.SPEED_SCALE, "整体大小", "大小");
        scale(b, StatusBarSettings.SPEED_NUMBER_SCALE, "数字大小", "大小");
        scale(b, StatusBarSettings.SPEED_UNIT_SCALE, "单位大小", "大小");
        b.number(StatusBarSettings.SPEED_LINE_GAP, "数字与单位间距", "文字与间距", "dp", -8, 8, .01f);
        b.number(StatusBarSettings.SPEED_WEIGHT, "文字粗细", "文字与间距", "", 100, 900, 1);
        normalColors(b, "speed", "网速文字");
        return new Group(b);
    }
    private static Group data() {
        Builder b = builder("data", "蜂窝信号", "信号格数跟随真实系统信号，蜂窝数据箭头自动隐藏。", STATUSBAR, "data_enabled");
        controls(b, "data", "独立开关", false);
        b.toggle("data_icon_enabled", "仿 iOS 信号样式", "保留真实信号强度", "独立开关");
        b.toggle("data_badge_hidden", "隐藏系统网络角标", "减少重复网络标识", "独立开关");
        b.toggle("data_single_enabled", "应用单排信号选项", "按照下面的信号布局显示", "独立开关");
        b.toggle(DataBatterySpacing.ENABLED, "无网络标识间距", "系统确认 Wi-Fi 图标和网络制式文字均不显示、蜂窝直接邻接电池时生效；状态未知时保留原生间距。", "电池边界间距");
        b.add(DataBatterySpacing.SPACING, "蜂窝与电池间距微调", "相对原生边界增加或减少，0 保留原生；仅改变蜂窝与电池边界，不改变其他图标间距与偏移。", NUMERIC,
                "电池边界间距", "dp", -12, 24, .01f, null, null, null, null);
        b.choice(StatusBarSettings.SIGNAL_LAYOUT, "信号布局", "双卡次信号仍由系统维护", "信号布局", new String[]{"system", "single"}, new String[]{"跟随系统单双排", "只显示主信号单排"});
        position(b, "data", "位置"); scale(b, StatusBarSettings.DATA_ICON_SCALE, "图标大小", "大小");
        normalColors(b, "data", "蜂窝信号");
        return new Group(b);
    }
    private static Group wifi() {
        Builder b = builder("wifi", "Wi-Fi 图标", "位置、大小与颜色分别设置，透明度跟随原生状态。", STATUSBAR, "wifi_enabled");
        controls(b, "wifi", "独立开关", false);
        b.toggle("wifi_icon_enabled", "仿 iOS Wi-Fi 样式", "保留真实 Wi-Fi 信号强度", "独立开关");
        b.toggle("wifi_activity_hidden", "隐藏 Wi-Fi 上传下载箭头", "保留 Wi-Fi 图标与连接状态", "独立开关");
        b.toggle("wifi_badge_hidden", "隐藏 Wi-Fi 角标", "减少重复网络标识", "独立开关");
        position(b, "wifi", "位置"); scale(b, StatusBarSettings.WIFI_ICON_SCALE, "图标大小", "大小");
        normalColors(b, "wifi", "Wi-Fi 图标");
        return new Group(b);
    }
    private static Group label() {
        Builder b = builder("label", "网络制式文字", "统一显示 5G、4G、3G；Wi-Fi 接通后隐藏，Wi-Fi 与移动数据都关闭时也自动隐藏。", STATUSBAR, "label_enabled");
        controls(b, "label", "独立开关", true);
        b.toggle(StatusBarSettings.LABEL_HIDDEN, "隐藏网络制式文字", "隐藏 5G、4G 等文字标识", "独立开关");
        b.toggle("label_normalize_enabled", "统一网络制式名称", "使用统一的 5G、4G、3G 名称", "独立开关");
        position(b, "label", "位置"); scale(b, StatusBarSettings.LABEL_SCALE, "文字大小", "大小");
        b.number(StatusBarSettings.SLOT_WIDTH, "预留宽度", "文字与间距", "dp", 0, 80, .01f);
        b.number(StatusBarSettings.FONT_WEIGHT, "文字粗细", "文字与间距", "", 100, 900, 1);
        normalColors(b, "label", "网络制式文字");
        return new Group(b);
    }
    private static Group clock(String id, String category) {
        boolean shade = "shade_clock".equals(id);
        Builder b = builder(id, shade ? "通知栏与控制中心时钟" : "状态栏时间", shade ? "独立设置下拉时钟，关闭后跟随状态栏时间。竖屏通知页的大时钟优先显示。" : "设置时间格式、位置、大小与字距。下拉时钟可跟随此处设置。", category, id + "_controls_enabled");
        controls(b, id, "独立开关", true);
        b.toggle(id + "_enabled", "自定义时间格式", "支持时分秒、日期、星期与中文时段", "时间格式");
        b.text(id + "_pattern", "时间格式", "可选择常用格式或输入自定义格式", "时间格式", CLOCK_VALUES, CLOCK_LABELS);
        position(b, id, "位置"); textSize(b, id, "文字样式"); normalColors(b, id, "时间文字");
        return new Group(b);
    }
    private static Group notificationIcons() {
        Builder b = builder("notification_icons", "通知图标区域", "调整状态栏左侧通知图标，可选择原生图标、单个爱心、文字或图片。", STATUSBAR, NotificationIconArea.MASTER);
        b.choice(NotificationIconArea.MODE, "显示方式", "只在存在可显示的通知时出现", "显示样式",
                new String[]{"native", "heart", "text", "image"},
                new String[]{"原生通知图标", "单个爱心", "自定义符号或文字", "自定义图片"});
        b.text(NotificationIconArea.TEXT, "符号或文字", "最多12个字符，可输入符号或短文字", "显示样式", null, null);
        b.add(NotificationIconArea.IMAGE_NAME, "自定义图片", "选择 PNG、JPEG 或 WebP，不超过2MB", IMAGE,
                "显示样式", "", 0, 0, 0, null, null, null, null);
        b.toggle(NotificationIconArea.POSITION, "位置调整", "应用水平与垂直偏移", "位置");
        b.number(NotificationIconArea.X, "水平位置", "位置", "dp", -160, 160, .01f);
        b.number(NotificationIconArea.Y, "垂直位置", "位置", "dp", -160, 160, .01f);
        b.toggle(NotificationIconArea.SIZE_ENABLED, "大小调整", "应用图标或内容的大小", "大小");
        b.number(NotificationIconArea.SIZE, "显示大小", "大小", "dp", 8, 40, .01f);
        b.toggle("notification_icons_count_enabled", "限制原生图标数量", "只作用于原生显示方式，保留所有实际通知", "图标数量");
        b.number("notification_icons_max_count", "最多显示图标数量", "图标数量", "个", 0, 10, 1);
        b.toggle(NotificationIconArea.COLOR_ENABLED, "颜色调整", "关闭后跟随系统图标颜色", "颜色与透明度");
        b.colorPair("notification_icons_color_light", "notification_icons_color_dark", "图标颜色", "颜色与透明度");
        return new Group(b);
    }
    private static Group carrier() {
        Builder b = builder("carrier", "运营商文字自定义", "通知栏、控制中心与锁屏分别设置运营商、时间或纯文本。", NOTIFICATION, "carrier_enabled");
        String[] titles = {"通知页", "控制中心", "锁屏"};
        for (int i = 0; i < CarrierPanels.GROUPS.length; i++) {
            String p = CarrierPanels.GROUPS[i], s = titles[i];
            b.toggle(p + "_enabled", "启用" + s + "独立设置", i == 2 ? "关闭时恢复系统运营商内容" : "只作用于此界面", s);
            controls(b, p, s + " · 独立开关", true);
            b.toggle(p + "_replace_enabled", "运营商替换", "应用下面所选的显示内容", s + " · 显示内容");
            b.choice(p + "_mode", "显示内容", "", s + " · 显示内容", new String[]{"original", "time", "text"}, new String[]{"运营商", "自定义时间", "纯文本"});
            b.text(p + "_pattern", "时间格式", "选择自定义时间后使用，支持中文日期与时段", s + " · 显示内容", CLOCK_VALUES, CLOCK_LABELS);
            b.text(p + "_text", "自定义文本", "支持中文、英文与 emoji，最多 120 个字符", s + " · 显示内容", null, null);
            position(b, p, s + " · 位置"); textSize(b, p, s + " · 文字样式");
            b.colorPair(p + "_color_light", p + "_color_dark", "运营商文字", s + " · 颜色与透明度");
        }
        return new Group(b);
    }
    private static Group font() {
        Builder b = builder("font", "文字字体", "统一用于时间、下拉文字、网络制式与网速。", STATUSBAR, "font_enabled");
        b.choice(StatusBarSettings.FONT_MODE, "字体来源", "自选字体需要先导入字体文件", "字体来源", new String[]{"system", "pingfang", "custom"}, new String[]{"跟随系统", "苹方", "自选字体"});
        b.add(StatusBarSettings.FONT_NAME, "自选字体", "导入 TTF / OTF / TTC 字体文件", FONT, "字体文件", "", 0, 0, 0, null, null, null, null);
        return new Group(b);
    }
    private static Group battery() {
        Builder b = builder("battery", "电池", "跟随真实电量，电池外形、数字与闪电分别设置。", STATUSBAR, "battery_enabled");
        controls(b, "battery", "独立开关", false);
        b.toggle("battery_style_enabled", "应用所选电池样式", "关闭时恢复系统原生样式", "电池样式");
        b.choice(StatusBarSettings.BATTERY_STYLE, "电池样式", "", "电池样式", new String[]{"pui", "native"}, new String[]{"PUI 默认样式", "系统原生样式"});
        b.toggle(StatusBarSettings.BATTERY_CHARGE_INSIDE, "电池内数字与闪电切换", "充电时平滑切换，停止充电后显示电量", "充电切换");
        b.number(StatusBarSettings.BATTERY_HOLD, "每项停留时间", "充电切换", "秒", 1, 10, .01f);
        b.number(StatusBarSettings.BATTERY_FADE, "淡入淡出时长", "充电切换", "秒", .15f, 3, .01f);
        position(b, "battery", "位置");
        scale(b, StatusBarSettings.BATTERY_SCALE, "整体大小", "大小");
        scale(b, StatusBarSettings.BATTERY_WIDTH_SCALE, "宽度微调", "大小");
        scale(b, StatusBarSettings.BATTERY_HEIGHT_SCALE, "高度微调", "大小");
        b.toggle(BatteryTextStyle.MASTER, "电池内文字调整", "独立调整电量数字，关闭后恢复原生文字", "电池内文字");
        b.number(BatteryTextStyle.X, "数字水平位置", "电池内文字", "dp", -20, 20, .01f);
        b.number(BatteryTextStyle.Y, "数字垂直位置", "电池内文字", "dp", -20, 20, .01f);
        b.number(BatteryTextStyle.SIZE, "数字字号", "电池内文字", "sp", 4, 24, .01f);
        b.number(BatteryTextStyle.WEIGHT, "数字字体粗细", "电池内文字", "", 100, 900, 1);
        b.number(BatteryTextStyle.SPACING, "数字字距", "电池内文字", "dp", -2, 4, .01f);
        normalColors(b, "battery", "电池外形与普通填充");
        String[] parts = {"text", "bolt", "charge", "alert"};
        String[] titles = {"电量数字", "充电闪电", "充电填充", "低电量／省电填充"};
        for (int i = 0; i < parts.length; i++) {
            String p = "battery_" + parts[i];
            b.toggle(p + "_color_enabled", "独立" + titles[i] + "颜色", "关闭时跟随系统颜色", titles[i]);
            b.colorPair(p + "_color_light", p + "_color_dark", titles[i], titles[i]);
        }
        return new Group(b);
    }
    private static Group tiles() {
        Builder b = builder("tiles", "翻页动效", "磁贴左右翻页时的边缘渐隐与模糊。参数难调，开销大；建议先使用渐隐，谨慎开启模糊。", CONTROL_CENTER, "tiles_enabled");
        b.toggle("tiles_portrait_enabled", "竖屏启用", "", "独立开关");
        b.toggle("tiles_landscape_enabled", "横屏启用", "", "独立开关");
        b.toggle("tiles_fade_enabled", "滑动边缘渐隐", "", "独立开关");
        b.toggle("tiles_blur_enabled", "高级模糊", "默认关闭，随滑动逐渐虚化边缘", "独立开关");
        b.number(StatusBarSettings.TILES_LEFT_RANGE, "左侧渐隐范围", "边缘范围与位置", "dp", 0, 192, .01f);
        b.number(StatusBarSettings.TILES_RIGHT_RANGE, "右侧渐隐范围", "边缘范围与位置", "dp", 0, 192, .01f);
        b.number(StatusBarSettings.TILES_LEFT_OFFSET_X, "左侧水平位置", "边缘范围与位置", "dp", -160, 160, .01f);
        b.number(StatusBarSettings.TILES_RIGHT_OFFSET_X, "右侧水平位置", "边缘范围与位置", "dp", -160, 160, .01f);
        b.number(StatusBarSettings.TILES_OFFSET_Y, "渐隐区域垂直位置", "边缘范围与位置", "dp", -240, 240, .01f);
        b.number(StatusBarSettings.TILES_REGION_HEIGHT, "渐隐区域高度", "边缘范围与位置", "dp", 0, 1000, .01f);
        b.number(StatusBarSettings.TILES_VERTICAL_FEATHER, "上下柔和过渡范围", "边缘范围与位置", "dp", 0, 192, .01f);
        b.number(StatusBarSettings.TILES_BLUR_RADIUS, "模糊半径", "模糊与渐隐", "dp", 0, 48, .01f);
        b.number(StatusBarSettings.TILES_STRENGTH, "渐隐强度", "模糊与渐隐", "%", 0, 100, .01f);
        return new Group(b);
    }
    private static Group tileCorners() {
        Builder b = builder("tile_corners", "统一圆角", "统一调整磁贴、亮度与音量滑块、音乐和设备卡片。关闭后恢复系统圆角。为避免原生绘制异常，半径仅支持 0–30 dp。", CONTROL_CENTER, "qs_tile_corners_enabled");
        b.number("qs_tile_corner_radius", "圆角半径", "圆角", "dp", 0, 30, .5f);
        return new Group(b);
    }
    private static Group tileIconSize() {
        Builder b = builder("tile_icon_size", "1×1 磁贴与设备图标", "调整 1×1 磁贴及耳机、设备空间卡片内的图标大小，保留原生居中、卡片外形、设备电量环和点击区域。", CONTROL_CENTER, QsTileIconSize.MASTER);
        b.number(QsTileIconSize.SIZE, "图标大小", "图标大小", "dp", 0, 64, .01f);
        return new Group(b);
    }
    private static Group shadeIcons() {
        Builder b = builder("shade_status_icons", "顶部图标切页", "此开关或大时钟任一开启，下拉期间隐藏顶部状态栏的小时间、通知图标与右侧图标，并隐藏通知页自己的小时间和右侧图标；通知页与控制中心都收起后恢复。左右切页时持续隐藏，不绘制副本、不移动图标。控制中心自己的右侧保持原生，左侧小时间和通知图标仅由此开关按原生边距管理。", CONTROL_CENTER, StatusBarShadeIconSettings.MASTER);
        return new Group(b);
    }
    private static Group qs() {
        Builder b = builder("qs_appearance", "磁贴颜色", "活动磁贴的底色、渐变与透明度，保留原生玻璃和高光。渐变效果开销大，请按需开启。", CONTROL_CENTER, QsTileAppearance.MASTER);
        b.colorPair("qs_global_light_color", "qs_global_dark_color", "活动填充颜色", "填充颜色");
        b.colorPair("qs_global_light_gradient_color", "qs_global_dark_gradient_color", "渐变终点颜色", "渐变颜色");
        for (String scene : new String[]{"light", "dark"}) {
            String s = "light".equals(scene) ? "浅色背景" : "深色背景", p = QsTileAppearance.prefix(scene);
            b.toggle(p + "_gradient_enabled", "启用渐变", "渐变效果开销大；填充透明度与颜色透明度相乘", s + " · 填充效果");
            b.number(p + "_opacity", "填充透明度", s + " · 填充效果", "%", 0, 100, .01f);
            b.number(p + "_gradient_angle", "渐变方向", s + " · 填充效果", "°", 0, 360, .01f);
        }
        return new Group(b);
    }
    private static Group bigClock() {
        Builder b = builder("notification_big_clock", "通知栏大时钟", "竖屏通知页显示日期与大时钟，随上滑平滑收起。开启后下拉期间隐藏原顶部状态栏的小时间、通知图标和右侧图标，以及通知页自己的小时间和右侧图标；左右切页保持隐藏，两页都收起后恢复原生。控制中心自己的右侧保持原生，左侧仍由“顶部图标切页”独立管理；横屏保留原有时钟。", NOTIFICATION, NotificationBigClockSettings.MASTER);
        b.text(NotificationBigClockSettings.PATTERN, "时间格式", "可选择常用格式或输入自定义格式", "时间", CLOCK_VALUES, CLOCK_LABELS);
        b.choice(NotificationBigClockSettings.FONT, "字体来源", "自选字体使用“文字字体”中导入的文件", "时间", new String[]{"native", "system", "pingfang", "custom"}, new String[]{"原生", "系统", "苹方", "自选"});
        b.number(NotificationBigClockSettings.MAX_SIZE, "展开字号上限", "时间", "dp", 48, 240, .01f);
        b.number(NotificationBigClockSettings.SCALE, "展开大小比例", "时间", "%", 25, 200, .01f);
        b.number(NotificationBigClockSettings.COMPACT_MAX_SIZE, "收起字高上限", "时间", "dp", 12, 96, .01f);
        b.number(NotificationBigClockSettings.COMPACT_SCALE, "收起字高比例", "时间", "%", 10, 100, .01f);
        b.number(NotificationBigClockSettings.WEIGHT, "时间字体粗细", "时间", "", 100, 900, 1);
        b.number(NotificationBigClockSettings.LETTER_SPACING, "时间字距", "时间", "em", -.15f, .5f, .01f);
        b.toggle(NotificationBigClockSettings.GLASS, "液态玻璃文字", "保留原生字形与数字切换", "玻璃文字");
        b.colorPair(NotificationBigClockSettings.COLOR_LIGHT, NotificationBigClockSettings.COLOR_DARK, "时间文字", "玻璃文字");
        b.toggle(NotificationBigClockSettings.GLASS_BORDER_ENABLED, "显示玻璃边框", "", "玻璃边框");
        b.number(NotificationBigClockSettings.GLASS_BORDER_WIDTH, "玻璃边框宽度", "玻璃边框", "dp", 0, 4, .01f);
        b.colorPair(NotificationBigClockSettings.GLASS_BORDER_COLOR_LIGHT, NotificationBigClockSettings.GLASS_BORDER_COLOR_DARK, "边框颜色", "玻璃边框");
        b.toggle(NotificationBigClockSettings.ENTRY_EFFECT_ENABLED, "随下拉手势移动、模糊与渐显", "展开与收起使用同一过渡", "入场效果");
        b.number(NotificationBigClockSettings.ENTRY_TRAVEL, "入场移动距离", "入场效果", "dp", 0, 120, .01f);
        b.number(NotificationBigClockSettings.ENTRY_BLUR_RADIUS, "入口模糊半径", "入场效果", "dp", 0, 32, .01f);
        b.number(NotificationBigClockSettings.ENTRY_FADE_STRENGTH, "入口渐显强度", "入场效果", "%", 0, 100, .01f);
        b.number(NotificationBigClockSettings.ENTRY_COMPLETION, "清晰显示的下拉进度", "入场效果", "%", 1, 100, .01f);
        b.toggle(NotificationBigClockSettings.DATE_ENABLED, "显示日期", "", "日期");
        b.text(NotificationBigClockSettings.DATE_PATTERN, "日期格式", "支持星期、农历日期与干支年", "日期", DATE_VALUES, DATE_LABELS);
        b.number(NotificationBigClockSettings.DATE_SIZE, "日期字号", "日期", "dp", 8, 40, .01f);
        b.number(NotificationBigClockSettings.DATE_WEIGHT, "日期字体粗细", "日期", "", 100, 900, 1);
        b.number(NotificationBigClockSettings.DATE_OFFSET_X, "日期水平位置", "日期", "dp", -120, 120, .01f);
        b.number(NotificationBigClockSettings.DATE_OFFSET_Y, "日期垂直位置", "日期", "dp", -120, 120, .01f);
        b.number(NotificationBigClockSettings.DATE_GAP, "日期与时间间距", "日期", "dp", 0, 80, .01f);
        alignment(b, NotificationBigClockSettings.DATE_ALIGNMENT, "日期对齐", "日期");
        b.colorPair(NotificationBigClockSettings.DATE_COLOR_LIGHT, NotificationBigClockSettings.DATE_COLOR_DARK, "日期文字", "日期颜色");
        alignment(b, NotificationBigClockSettings.ALIGNMENT, "时间对齐", "布局");
        b.number(NotificationBigClockSettings.OFFSET_X, "初始水平位置", "初始位置", "dp", -160, 160, .01f);
        b.number(NotificationBigClockSettings.OFFSET_Y, "初始垂直位置", "初始位置", "dp", -120, 200, .01f);
        b.number(NotificationBigClockSettings.COMPACT_OFFSET_X, "收起水平位置", "布局", "dp", -160, 160, .01f);
        b.number(NotificationBigClockSettings.COMPACT_OFFSET_Y, "收起垂直位置", "布局", "dp", -120, 120, .01f);
        b.toggle(NotificationBigClockSettings.NOTIFICATION_GAP_ENABLED, "自动保留时钟与首张通知的距离", "", "通知留白");
        b.number(NotificationBigClockSettings.NOTIFICATION_GAP, "时间与通知间距", "通知留白", "dp", 0, 100, .01f);
        b.toggle(NotificationBigClockSettings.STACK_ENABLED, "通知堆叠（不可用）", "底层bug修复不了 该功能不予适用", "通知堆叠");
        b.number(NotificationBigClockSettings.VISIBLE_COUNT, "完整通知数量", "通知堆叠", "", 1, 3, 1);
        b.number(NotificationBigClockSettings.TAIL_WIDTH_1, "第一层底边宽度", "通知堆叠", "%", 50, 100, .01f);
        b.number(NotificationBigClockSettings.TAIL_WIDTH_2, "第二层底边宽度", "通知堆叠", "%", 50, 100, .01f);
        b.number(NotificationBigClockSettings.TAIL_WIDTH_3, "第三层底边宽度", "通知堆叠", "%", 50, 100, .01f);
        b.toggle(NotificationBigClockSettings.NOTIFICATION_EDGE_ENABLED, "通知进入顶部边缘时模糊与渐隐", "效果随真实通知位置变化", "通知顶部边缘");
        b.number(NotificationBigClockSettings.NOTIFICATION_EDGE_SAFE_DISTANCE, "顶部安全距离", "通知顶部边缘", "dp", 0, 100, .01f);
        b.number(NotificationBigClockSettings.NOTIFICATION_EDGE_RANGE, "过渡范围", "通知顶部边缘", "dp", 0, 120, .01f);
        b.number(NotificationBigClockSettings.NOTIFICATION_EDGE_BLUR_RADIUS, "边缘模糊半径", "通知顶部边缘", "dp", 0, 32, .01f);
        b.toggle(NotificationBigClockSettings.FOOTER_ENABLED, "显示底部内容", "", "底部内容");
        b.text(NotificationBigClockSettings.FOOTER_PATTERN, "内容格式", "{text} 对应下面的纯文本内容", "底部内容", FOOTER_VALUES, FOOTER_LABELS);
        b.text(NotificationBigClockSettings.FOOTER_TEXT, "编辑纯文本", "支持中文、英文与 emoji，最多 120 个字符", "底部内容", null, null);
        b.number(NotificationBigClockSettings.FOOTER_SIZE, "底部字号", "底部内容", "dp", 8, 36, .01f);
        b.number(NotificationBigClockSettings.FOOTER_WEIGHT, "底部字体粗细", "底部内容", "", 100, 900, 1);
        b.number(NotificationBigClockSettings.FOOTER_OFFSET_X, "底部水平位置", "底部内容", "dp", -160, 160, .01f);
        b.number(NotificationBigClockSettings.FOOTER_OFFSET_Y, "底部垂直位置", "底部内容", "dp", -120, 120, .01f);
        b.number(NotificationBigClockSettings.FOOTER_MARGIN, "底部安全间距", "底部内容", "dp", 0, 120, .01f);
        alignment(b, NotificationBigClockSettings.FOOTER_ALIGNMENT, "底部内容对齐", "底部内容");
        b.colorPair(NotificationBigClockSettings.FOOTER_COLOR_LIGHT, NotificationBigClockSettings.FOOTER_COLOR_DARK, "底部文字", "底部颜色");
        return new Group(b);
    }
    private static Group clear() {
        Builder b = builder("notification_clear", "通知清除按钮", "独立设置底色、滑入淡入与通知间距，保留原生玻璃和清除操作。", NOTIFICATION, NotificationClearAppearance.MASTER);
        b.toggle("notification_clear_motion_enabled", "独立滑入与淡入", "只调整清除按钮的入场与收起，不依赖大时钟", "动效与安全距离");
        b.number("notification_clear_safe_distance", "与通知安全距离", "动效与安全距离", "dp", 0, 160, .1f);
        b.number("notification_clear_entry_travel", "入场位移", "动效与安全距离", "dp", 0, 160, .1f);
        b.number("notification_clear_offset_y", "垂直位置", "动效与安全距离", "dp", -160, 160, .1f);
        b.colorPair(NotificationClearAppearance.COLOR_LIGHT, NotificationClearAppearance.COLOR_DARK, "按钮底色", "按钮底色");
        b.toggle(NotificationClearAppearance.GRADIENT_ENABLED, "启用渐变", "", "渐变效果");
        b.colorPair(NotificationClearAppearance.GRADIENT_COLOR_LIGHT, NotificationClearAppearance.GRADIENT_COLOR_DARK, "渐变终点", "渐变效果");
        b.number(NotificationClearAppearance.OPACITY, "底色透明度", "按钮底色", "%", 0, 100, .01f);
        b.number(NotificationClearAppearance.GRADIENT_ANGLE, "渐变方向", "渐变效果", "°", 0, 360, .01f);
        return new Group(b);
    }
    private static Group media() {
        Builder b = builder("qs_media", "音乐卡片", "封面背景、虚化与小封面发光，按浅色和深色界面分别设置。", CONTROL_CENTER, QsMediaAppearance.MASTER);
        b.toggle(QsMediaAppearance.BACKGROUND, "使用封面作为卡片背景", "居中裁切，保留原生标题与播放按钮", "显示效果");
        b.toggle(QsMediaAppearance.GLOW, "小封面外发光", "保留系统玻璃与高光", "显示效果");
        for (String scene : new String[]{"light", "dark"}) {
            String s = "light".equals(scene) ? "浅色界面" : "深色界面", p = QsMediaAppearance.prefix(scene);
            b.number(p + "_background_opacity", "背景透明度", s + " · 背景", "%", 0, 100, .01f);
            b.number(p + "_background_blur", "背景模糊度", s + " · 背景", "dp", 0, 48, .01f);
            b.toggle(p + "_glow_inverse_enabled", "发光使用封面反色", "关闭后使用自定义发光颜色", s + " · 外发光");
            b.number(p + "_glow_opacity", "发光透明度", s + " · 外发光", "%", 0, 100, .01f);
            b.number(p + "_glow_radius", "发光模糊半径", s + " · 外发光", "dp", 0, 48, .01f);
            b.number(p + "_glow_spread", "发光扩散距离", s + " · 外发光", "dp", 0, 24, .01f);
        }
        b.colorPair("qs_media_light_glow_color", "qs_media_dark_glow_color", "自定义发光颜色", "发光颜色");
        return new Group(b);
    }

    public static List<Group> groups() { return GROUPS; }
    public static List<Group> groups(String category) {
        List<Group> result = new ArrayList<>();
        for (NavigationSection section : navigationSections(category)) result.addAll(section.groups);
        return Collections.unmodifiableList(result);
    }
    public static List<NavigationSection> navigationSections(String category) {
        if (STATUSBAR.equals(category)) return Arrays.asList(
                new NavigationSection("时间与电量", "clock", "battery"),
                new NavigationSection("网络信号", "speed", "data", "wifi", "label"),
                new NavigationSection("通知图标与字体", "notification_icons", "font"));
        if (CONTROL_CENTER.equals(category)) return Arrays.asList(
                new NavigationSection("磁贴与滑块", "tile_corners", "tile_icon_size", "qs_appearance", "tiles"),
                new NavigationSection("音乐卡片", "qs_media"),
                new NavigationSection("顶部时间与文字", "shade_status_icons", "shade_clock", "carrier"));
        if (NOTIFICATION.equals(category)) return Arrays.asList(
                new NavigationSection("时钟与日期", "notification_big_clock", "shade_clock"),
                new NavigationSection("通知显示", "shade_status_icons", "notification_clear"),
                new NavigationSection("界面文字", "carrier"));
        if (LOCK_SCREEN.equals(category)) return Collections.singletonList(new NavigationSection("锁屏文字", "carrier"));
        return Collections.emptyList();
    }
    public static List<DetailPage> detailPages(Group group) {
        if (group == null) return Collections.emptyList();
        LinkedHashMap<String, List<Item>> buckets = new LinkedHashMap<>();
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        if ("notification_big_clock".equals(group.id)) {
            labels.put("time", "时间"); labels.put("date", "日期"); labels.put("notifications", "通知");
            labels.put("effects", "动效"); labels.put("footer", "底部");
        } else if ("carrier".equals(group.id)) {
            labels.put("notification", "通知栏"); labels.put("control", "控制中心"); labels.put("lockscreen", "锁屏");
        } else if ("battery".equals(group.id)) {
            labels.put("style", "样式"); labels.put("text", "内文字"); labels.put("layout", "布局"); labels.put("colors", "颜色");
        } else if ("qs_media".equals(group.id) || "qs_appearance".equals(group.id)) {
            labels.put("common", "常用"); labels.put("light", "浅色界面"); labels.put("dark", "深色界面");
        } else labels.put("all", "全部");
        for (String id : labels.keySet()) buckets.put(id, new ArrayList<>());
        for (Item item : group.items) if (!item.master) buckets.get(detailPageId(group, item)).add(item);
        List<DetailPage> pages = new ArrayList<>();
        for (List<Item> items : buckets.values()) items.sort((left, right) ->
                Integer.compare(sectionPriority(left.section), sectionPriority(right.section)));
        for (Map.Entry<String, List<Item>> entry : buckets.entrySet()) if (!entry.getValue().isEmpty())
            pages.add(new DetailPage(entry.getKey(), labels.get(entry.getKey()), entry.getValue()));
        return Collections.unmodifiableList(pages);
    }
    private static int sectionPriority(String section) {
        String title = section.contains(" · ") ? section.substring(section.indexOf(" · ") + 3) : section;
        if ("初始位置".equals(title)) return -1;
        if (Arrays.asList("显示样式", "时间格式", "字体来源", "显示效果", "信号布局", "电池样式", "通知页", "控制中心", "锁屏", "底色").contains(title)) return 0;
        if ("位置".equals(title)) return 10;
        if ("大小".equals(title) || "展开样式".equals(title)) return 20;
        if ("文字样式".equals(title) || "文字与间距".equals(title)) return 30;
        if (title.contains("颜色") || "颜色与透明度".equals(title)) return 50;
        if ("收起样式".equals(title) || "收起位置".equals(title)) return 60;
        return 40;
    }
    private static String detailPageId(Group group, Item item) {
        String section = item.section;
        if ("notification_big_clock".equals(group.id)) {
            if (section.startsWith("日期")) return "date";
            if (section.startsWith("通知")) return "notifications";
            if (section.startsWith("底部")) return "footer";
            if (section.startsWith("玻璃") || "入场效果".equals(section)) return "effects";
            return "time";
        }
        if ("carrier".equals(group.id)) {
            if (item.key.startsWith(CarrierPanels.CONTROL + "_")) return "control";
            if (item.key.startsWith(CarrierPanels.LOCKSCREEN + "_")) return "lockscreen";
            return "notification";
        }
        if ("battery".equals(group.id)) {
            if ("电池内文字".equals(section)) return "text";
            if (COLOR.equals(item.type) || item.key.endsWith("_color_enabled")) return "colors";
            if ("位置".equals(section) || "大小".equals(section)) return "layout";
            return "style";
        }
        if ("qs_media".equals(group.id) || "qs_appearance".equals(group.id)) {
            if (!item.scene.isEmpty()) return item.scene;
            if (section.startsWith("浅色")) return "light";
            if (section.startsWith("深色")) return "dark";
            return "common";
        }
        return "all";
    }
    public static Group group(String id) { return GROUP_BY_ID.get(id); }
    public static List<Item> items(String groupId) {
        Group group = group(groupId);
        return group == null ? Collections.emptyList() : group.items;
    }
    public static Item item(String key) { return ITEM_BY_KEY.get(key); }
    /** Keep the abandoned stacking controls discoverable without enabling runtime ownership. */
    public static String unavailableReason(String key) {
        if (NotificationBigClockSettings.STACK_ENABLED.equals(key) || NotificationBigClockSettings.VISIBLE_COUNT.equals(key)
                || NotificationBigClockSettings.TAIL_WIDTH_1.equals(key) || NotificationBigClockSettings.TAIL_WIDTH_2.equals(key)
                || NotificationBigClockSettings.TAIL_WIDTH_3.equals(key)) return "底层bug修复不了 该功能不予适用";
        return "";
    }

    /** Actual precedence and mutual exclusion, shown at the top of each affected feature. */
    public static String conflictDescription(String groupId, String category) {
        if ("clock".equals(groupId)) return "优先级：竖屏通知页启用大时钟后，下拉页的小时间由大时钟接管；状态栏本身仍使用本页设置。";
        if ("shade_clock".equals(groupId)) return "冲突：与通知栏大时钟共用通知页时间区域。修改本页启用的条件会关闭大时钟，控制中心和横屏仍使用下拉时钟。";
        if ("carrier".equals(groupId) && NOTIFICATION.equals(category)) return "冲突：与通知栏大时钟共用顶部区域。启用或编辑本页条件会关闭大时钟；关闭本页开关不会自动开启大时钟。";
        if ("notification_big_clock".equals(groupId)) return "优先级：大时钟或“顶部图标切页”任一开启，下拉期间顶部原生小时间、通知图标与右侧图标及通知页的小时间和右侧图标持续隐藏，两页都真正收起后恢复；控制中心自己的右侧保留原生，左侧仅由“顶部图标切页”管理。大时钟、日期和底部文字正常显示。编辑下拉时钟或通知栏运营商文字会关闭大时钟。通知堆叠不可用。";
        if ("font".equals(groupId)) return "优先级：各功能独立字号和粗细仍有效；大时钟选择原生字体时使用自身字形，选择自选字体后才使用这里导入的文件。";
        if ("notification_icons".equals(groupId)) return "互斥：原生图标、爱心、文字和图片只选一种。最多图标数量仅作用于原生模式；通知页隐藏规则优先于本页外观设置。";
        if ("shade_status_icons".equals(groupId)) return "优先级：此开关或大时钟任一开启，顶部原生小时间、通知图标与右侧图标及通知页的小时间和右侧图标在整个下拉、左右切页期间持续隐藏。两页都收起或两个开关都关闭时恢复原生。控制中心自己的右侧始终保留原生，左侧小时间与通知图标仅由此开关管理。通知图标样式和数量继续在“通知图标区域”中设置。";
        if ("label".equals(groupId)) return "优先级：隐藏网络制式、Wi-Fi 已连接或两个网络开关都关闭时，隐藏优先于名称、位置、字体和颜色设置。";
        if ("battery".equals(groupId)) return "优先级：电池内文字调整只作用于数字；充电数字与闪电交替开启时，数字阶段使用文字设置。切换电池样式后按该样式的原生字形重新应用。";
        if ("qs_media".equals(groupId)) return "互斥：外发光使用封面反色时，自定义发光颜色不生效。封面背景和统一圆角可同时使用。";
        if ("data".equals(groupId)) return "优先级：隐藏网络角标不会隐藏独立网络制式文字；该文字在“网络制式文字”中设置。蜂窝数据箭头始终隐藏。";
        return "";
    }
    public static List<Item> allItems() { return Collections.unmodifiableList(new ArrayList<>(ITEM_BY_KEY.values())); }
    public static Set<String> hiddenKeys() { return HIDDEN; }
    public static boolean isVisible(String key) { return ITEM_BY_KEY.containsKey(key); }
    public static Object defaultValue(String key) {
        if (StatusBarSettings.BOOLEAN_DEFAULTS.containsKey(key)) return StatusBarSettings.BOOLEAN_DEFAULTS.get(key);
        if (StatusBarSettings.NUMERIC_DEFAULTS.containsKey(key)) return StatusBarSettings.NUMERIC_DEFAULTS.get(key);
        if (StatusBarSettings.COLOR_DEFAULTS.containsKey(key)) return StatusBarSettings.COLOR_DEFAULTS.get(key);
        return StatusBarSettings.STRING_DEFAULTS.get(key);
    }
    /** Read through the existing legacy fallback rules; never rewrite stored values just by viewing. */
    public static Object value(Map<String, ?> saved, String key) {
        if (StatusBarSettings.BOOLEAN_DEFAULTS.containsKey(key)) return StatusBarSettings.bool(saved, key);
        if (StatusBarSettings.NUMERIC_DEFAULTS.containsKey(key)) return StatusBarSettings.settingNumber(saved, key, StatusBarSettings.NUMERIC_DEFAULTS.get(key));
        if (StatusBarSettings.COLOR_DEFAULTS.containsKey(key)) return StatusBarSettings.color(saved, key);
        if (StatusBarSettings.STRING_DEFAULTS.containsKey(key)) return StatusBarSettings.string(saved, key);
        return saved == null ? null : saved.get(key);
    }
    /** Check domain restrictions before float conversion can erase a fractional input. */
    static String numericInputError(String key, BigDecimal raw) {
        if (QsTileCorners.RADIUS.equals(key) && (raw.signum() < 0 || raw.compareTo(BigDecimal.valueOf(30)) > 0))
            return "圆角半径应为 0 到 30 dp";
        if (NotificationIconArea.MAX_COUNT.equals(key) && (raw.signum() < 0 || raw.stripTrailingZeros().scale() > 0))
            return "图标数量应为非负整数，0 表示不显示";
        return null;
    }
    public static float customNumber(Item item, String input) {
        BigDecimal raw = NumericInput.decimal(input);
        String error = numericInputError(item.key, raw);
        if (error != null) throw new IllegalArgumentException(error);
        return raw.floatValue();
    }
    /** Options are closed sets. Pattern/string presets intentionally remain user-editable. */
    public static String validationError(Item item, Object value) {
        if (item == null) return "未找到此设置";
        if (!unavailableReason(item.key).isEmpty()) return unavailableReason(item.key);
        if (BOOLEAN.equals(item.type)) return value instanceof Boolean ? null : "请选择开启或关闭";
        if (NUMERIC.equals(item.type)) {
            if (!(value instanceof Number)) return "请输入有效数字";
            float number = ((Number) value).floatValue();
            if (Float.isNaN(number) || Float.isInfinite(number)) return "请输入有限数字";
            if ("qs_tile_corner_radius".equals(item.key) && (number < 0 || number > 30)) return "圆角半径应为 0 到 30 dp";
            if ("notification_icons_max_count".equals(item.key) && (number < 0 || number != Math.floor(number))) return "图标数量应为非负整数，0 表示不显示";
            if (NotificationBigClockSettings.VISIBLE_COUNT.equals(item.key) && (number < 1 || number > 3 || number != Math.round(number))) return "完整通知数量只能为 1、2 或 3";
            return null;
        }
        if (COLOR.equals(item.type)) return value instanceof Number ? null : "请输入有效颜色";
        if (!(value instanceof String)) return "请输入文字";
        String text = (String) value;
        if (NotificationIconArea.TEXT.equals(item.key) && text.codePointCount(0, text.length()) > 12) return "符号或文字最多12个字符";
        if (OPTIONS.equals(item.type)) {
            for (String option : item.values) if (option.equals(text)) return null;
            return "请选择列表中的选项";
        }
        if (item.key.endsWith("_pattern")) return NotificationBigClockSettings.FOOTER_PATTERN.equals(item.key) ? TimeFormat.contentValidationError(text) : TimeFormat.validationError(text);
        if (STRING.equals(item.type) && (text.length() > TimeFormat.MAX_TEXT || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0)) return "请使用不超过 120 个字符的单行文字";
        return null;
    }
    private SettingsCatalog() { }
}
