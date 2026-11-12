// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;

/** User-facing descriptors shared by the Compose configuration pages. No View or Activity state. */
public final class SettingsCatalog {
    public static final String STATUSBAR = "statusbar", NOTIFICATION = "notification", CONTROL_CENTER = "control_center", LOCK_SCREEN = "lockscreen", OTHER = "other";
    public static final String BOOLEAN = "boolean", NUMERIC = "numeric", STRING = "string";
    public static final String COLOR = "color", FONT = "font", IMAGE = "image", OPTIONS = "options";
    private static final String[] CLOCK_VALUES = {"HH:mm", "HH:mm:ss", "{上午下午} hh:mm", "{时段} HH:mm", "M月d日 {周} HH:mm", "yyyy年MM月dd日 {星期} HH:mm", "MM/dd HH:mm"};
    private static final String[] CLOCK_LABELS = {"时分 · 24 小时", "时分秒 · 24 小时", "上午／下午 · 12 小时", "时段与时间", "月日、星期与时间", "完整日期、星期与时间", "短日期与时间"};
    private static final String[] DUAL_VALUES = {"HH:mm", "HH:mm:ss", "M月d日 {周}", "M月d日 EEEE", "{周}", "{时段}", "yyyy年M月d日", "{农历日期}", "MM/dd"};
    private static final String[] DUAL_LABELS = {"时分", "时分秒", "月日与星期", "月日与完整星期", "星期", "时段", "完整日期", "农历日期", "短日期"};
    private static final String[] DATE_VALUES = {NotificationBigClockSettings.DATE_DEFAULT, "M月d日 EEEE", "yyyy年M月d日 {星期}", "M月d日 {周} {农历}", "{干支} {农历日期}"};
    private static final String[] DATE_LABELS = {"月日、星期、干支与农历", "月日与星期", "完整日期与星期", "月日、星期与农历", "干支年与农历日期"};
    private static final String[] FOOTER_VALUES = {"{text}", "HH:mm {text}", "yyyy年M月d日 {周} {text}", "{农历} {text}", "'C17' HH:mm {text}", "✨ {text}"};
    private static final String[] FOOTER_LABELS = {"纯文本", "时间与文字", "日期、星期与文字", "农历与文字", "C17、时间与文字", "星光与文字"};
    private static final List<Group> GROUPS;
    private static final Map<String, Group> GROUP_BY_ID;
    private static final Map<String, Item> ITEM_BY_KEY;
    private static final Set<String> HIDDEN;
    /** One wording for every "过渡范围": a gradient between two colours, and a colour that fades out. */
    static final String RANGE_BETWEEN = "两种颜色之间过渡所占的长度：100% 为从一端均匀过渡到另一端（原来的样子）；越小，过渡越集中在中间，两端保留纯色。";
    static final String RANGE_FADE = "颜色从起点开始，到这个位置完全淡出：100% 为一直淡到底（原来的样子），越小过渡越短、颜色越集中在起点。";
    /** The same for a fade measured in dp (a glow, an edge): the distance over which it goes from full to nothing. */
    static final String RANGE_DISTANCE = "从起点开始，在这段距离内完全淡出：越大过渡越长、越柔和，越小越集中在起点。";

    public static final class Group {
        public final String id, key, title, description, category, masterKey;
        /** Several independent opt-ins share a page without a page-wide switch. */
        public final boolean independentSwitches;
        public final List<Item> items;
        private Group(Builder builder) {
            id = key = builder.id;
            title = builder.title; description = builder.description;
            category = builder.category; masterKey = builder.master;
            independentSwitches = builder.independentSwitches;
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
            groupId = group.id; master = !group.independentSwitches && key.equals(group.master);
            allowsCustomValue = STRING.equals(type) || NUMERIC.equals(type);
            defaultValue = SettingsCatalog.defaultValue(key);
        }
    }

    /** What a time-class field means, in one line. Landscape copies share the portrait wording. */
    static String fieldNote(String key) {
        String k = NotificationBigClockSettings.portraitKey(key);
        String big = "notification_big_clock_";
        if (k.startsWith(big)) {
            switch (k.substring(big.length())) {
                case "max_size": return "展开时时间的字体高度；原生字体会随上滑收起";
                case "compact_max_size": return "上滑收起后时间的字体高度";
                case "screen_padding": return "原生字体铺满屏幕宽度时，左右各留出的距离";
                case "letter_spacing": return "时间的字距，正值拉开，负值收紧";
                case "alignment": return "时间在屏幕里的对齐方式";
                case "glass_border_enabled": return "给数字、日期和附加文字统一加一圈玻璃质感的细边框";
                case "glass_border_width": return "玻璃边框的粗细";
                case "entry_travel": return "下拉时，时间和日期从上方移入的距离";
                case "entry_blur_radius": return "刚进入时的模糊程度，随下拉逐渐变清晰";
                case "entry_fade_strength": return "刚进入时的透明程度，数值越大越透明";
                case "entry_completion": return "下拉到这个进度时，时间和日期完全清晰";
                case "date_enabled": return "在大时钟区域显示日期；竖屏可与左右组件整行互换位置";
                case "date_size": return "日期的字号";
                case "date_gap": return "日期与时间之间的距离";
                case "date_alignment": return "日期在屏幕里的对齐方式";
                case "notification_gap_enabled": return "自动在时钟和第一张通知之间留出距离";
                case "notification_gap": return "时间或日期底部与第一张通知之间的距离";
                case "notification_edge_safe_distance": return "通知上滑靠近时钟时，开始淡出之前保留的距离";
                case "notification_edge_range": return "通知在时钟下方逐渐淡出的范围";
                case "notification_edge_blur_radius": return "淡出区域的模糊程度";
                case "footer_enabled": return "在通知列表底部显示一行自定义内容";
                case "footer_size": return "底部内容的字号";
                case "footer_margin": return "底部内容与屏幕底边之间的安全距离";
                case "footer_alignment": return "底部内容的对齐方式";
                default: return "";
            }
        }
        boolean clockLike = k.startsWith("clock_") || k.startsWith("shade_clock_") || k.startsWith("classic_clock_");
        if (clockLike || k.startsWith("carrier_")) {
            if (k.endsWith("_digit_spacing")) return "每个数字格额外增加的宽度，正值更松，负值更紧";
            if (k.endsWith("_dual_upper_scale") || k.endsWith("_dual_lower_scale")) return "这一排的字号，相对原生时钟的百分比";
            if (k.endsWith("_dual_line_gap")) return "两排之间的额外距离，可以为负";
            if (k.endsWith("_margin_start")) return "时钟左侧额外留白，相邻图标随之让开";
            if (k.endsWith("_margin_end")) return "时钟右侧额外留白，相邻图标随之让开";
            if (k.endsWith("_spacing")) return "字符之间的距离，正值拉开，负值收紧";
            if (k.endsWith("_scale")) return "文字大小，相对原生的百分比，100 为原样";
        }
        return "";
    }

    private static final class Builder {
        final String id, title, description, category, master;
        boolean independentSwitches;
        final List<Item> items = new ArrayList<>();
        Builder(String id, String title, String description, String category, String master) {
            this(id, title, description, category, master, false, "启用" + title, description);
        }
        /** labelled: the page's own switch is a named, searchable row instead of the unlabelled top-bar switch. */
        Builder(String id, String title, String description, String category, String master,
                boolean labelled, String masterTitle, String masterDescription) {
            this.id = id; this.title = title; this.description = description;
            this.category = category; this.master = master; independentSwitches = labelled;
            if (master != null) toggle(master, masterTitle, masterDescription, "功能开关");
        }
        void toggle(String key, String title, String description, String section) {
            add(key, title, description.isEmpty() ? fieldNote(key) : description, BOOLEAN, section, "", 0, 1, 1, null, null, null, null);
        }
        void number(String key, String title, String section, String unit, float min, float max, float step) {
            String description = key.contains("offset_") ? "相对于系统默认位置" : key.endsWith("_weight") || key.equals(StatusBarSettings.FONT_WEIGHT) ? "100 最细，900 最粗" : fieldNote(key);
            add(key, title, description, NUMERIC, section, unit, min, max, step, null, null, null, null);
        }
        void choice(String key, String title, String description, String section, String[] values, String[] labels) {
            add(key, title, description.isEmpty() ? fieldNote(key) : description, OPTIONS, section, "", 0, 0, 0, values, labels, null, null);
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
                ClockBackdropSettings.NOTIFICATION_FOLLOW, ClockBackdropSettings.NOTIFICATION_RELIEF,
                StatusBarSettings.DIAGNOSTICS_ENABLED, StatusBarSettings.FONT_REVISION, NotificationIconArea.IMAGE_REVISION,
                ResidentCache.INSTALLED, ResidentCache.RUNNING,
                StatusBarSettings.FONT_NAME, FontScopes.KEY,
                IconPackRepository.MASTER, IconPackRepository.LAYERS, IconPackAssignments.ASSIGNMENTS,
                NotificationBigClockSettings.STATUS_ICONS_FIXED, NotificationBigClockSettings.COMPACT_WEIGHT,
                NotificationBigClockSettings.STACK_GAP, NotificationBigClockSettings.STACK_INSET,
                StatusBarSettings.TILES_FADE_RANGE,
                "carrier_position_enabled", "carrier_size_enabled", "carrier_color_enabled",
                "carrier_text_style_enabled", "carrier_replace_enabled",
                StatusBarSettings.CARRIER_MODE, StatusBarSettings.CARRIER_PATTERN, StatusBarSettings.CARRIER_TEXT,
                StatusBarSettings.CARRIER_OFFSET_X, StatusBarSettings.CARRIER_OFFSET_Y,
                StatusBarSettings.CARRIER_SCALE, StatusBarSettings.CARRIER_WEIGHT, StatusBarSettings.CARRIER_SPACING,
                "carrier_color_light", "carrier_color_dark"));
        hidden.add(StatusBarShadeIconSettings.MASTER);
        hidden.addAll(QsCardSettings.BOOLEANS.keySet());
        hidden.addAll(QsCardSettings.NUMBERS.keySet());
        hidden.addAll(QsCardSettings.STRINGS.keySet());
        hidden.add(NativeClockMeasurement.SCALE_BASIS_VERSION);
        hidden.add(NativeClockMeasurement.LEGACY_SCALE_FACTOR);
        hidden.add(NotificationBigClockSettings.INTERACTIVE_STACK);
        hidden.add(NotificationGroupStack.MASTER);
        hidden.add(RadioState.WIFI_MERGED);
        hidden.add(RadioState.ALWAYS_SHOWN);
        hidden.add(RadioState.BOTH_VISIBLE);
        hidden.add(FluidCloudStyle.FOLLOW_FOREGROUND);
        // Retired (code100): the notification-shade scene of the fluid-cloud style; always off.
        hidden.add(FluidCloudStyle.SCOPE_SHADE);
        // Retired: highlight removal belongs to the C17 feature; the media accent is no longer lock-screen-only.
        hidden.add(FluidCloudStyle.DESKTOP_HIGHLIGHTS);
        hidden.add(FluidCloudAccent.LOCKSCREEN_ENABLED);
        hidden.add(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED);
        hidden.add("data_single_enabled");
        hidden.addAll(Arrays.asList(NativeNetworkBadgeControls.X,NativeNetworkBadgeControls.Y,
                NativeNetworkBadgeControls.SCALE,NativeNetworkBadgeControls.WEIGHT,NativeNetworkBadgeControls.FONT));
        hidden.add(NetworkSpeedControls.INTERVAL_SECONDS);
        hidden.add(NetworkSpeedControls.STYLE_ENABLED);
        hidden.add(NetworkSpeedControls.DISPLAY_STYLE);
        // The code160 area de-dimming was withdrawn in code161 (the see-through belongs to the digits only).
        for (String key : new String[]{NotificationBigClockSettings.REVEAL_ENABLED, NotificationBigClockSettings.REVEAL_AMOUNT,
                NotificationBigClockSettings.REVEAL_FEATHER}) { hidden.add(key); hidden.add(NotificationBigClockSettings.landscapeKey(key)); }
        // code169 replaces background sampling with a colour mask; the old reveal strength stays portable only.
        hidden.add(NotificationBigClockSettings.DIGIT_REVEAL_AMOUNT);
        hidden.add(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.DIGIT_REVEAL_AMOUNT));
        // code169 text components replace the AppWidget editor; historical provider/height data remains portable.
        hidden.addAll(Arrays.asList(BigClockWidgetsSettings.LEFT_PROVIDER,BigClockWidgetsSettings.RIGHT_PROVIDER,
                BigClockWidgetsSettings.LEFT_HEIGHT,BigClockWidgetsSettings.RIGHT_HEIGHT));
        // The page-turn effect was withdrawn in code161: its saved values stay importable, never shown.
        hidden.add("tiles_enabled");
        for (Item item : tiles().items) hidden.add(item.key);
        hidden.add(NotificationBigClockSettings.SCALE);
        hidden.add(NotificationBigClockSettings.COMPACT_SCALE);
        for (String key : new String[]{NotificationBigClockSettings.COMPACT_SCALE,
                NotificationBigClockSettings.COMPACT_MAX_SIZE, NotificationBigClockSettings.COMPACT_OFFSET_X,
                NotificationBigClockSettings.COMPACT_OFFSET_Y}) hidden.add(NotificationBigClockSettings.landscapeKey(key));
        hidden.add(NotificationBigClockSettings.TAIL_WIDTH_1);
        hidden.add(NotificationBigClockSettings.TAIL_WIDTH_2);
        hidden.add(NotificationBigClockSettings.TAIL_WIDTH_3);
        for (String key : new String[]{NotificationBigClockSettings.COMPACT_WEIGHT,
                NotificationBigClockSettings.STACK_ENABLED, NotificationBigClockSettings.VISIBLE_COUNT, NotificationBigClockSettings.TAIL_WIDTH_1,
                NotificationBigClockSettings.TAIL_WIDTH_2, NotificationBigClockSettings.TAIL_WIDTH_3}) {
            String landscape = NotificationBigClockSettings.landscapeKey(key);
            if (StatusBarSettings.BOOLEAN_DEFAULTS.containsKey(landscape)
                    || StatusBarSettings.NUMERIC_DEFAULTS.containsKey(landscape)) hidden.add(landscape);
        }
        // Newer network settings remain saved for compatibility; 1.8.2 does not expose or use them (the per-generation texts are back).
        hidden.addAll(Arrays.asList(NetworkSlotGeometry.POSITION, NetworkSlotGeometry.OFFSET_X,
                NetworkSlotGeometry.OFFSET_Y, NetworkSlotGeometry.MARGIN_LEFT, NetworkSlotGeometry.MARGIN_RIGHT));
        hidden.add("data_roaming_hidden");
        // Drawing offsets remain portable legacy data; the layout editor uses measured gaps instead.
        hidden.addAll(Arrays.asList(StatusBarSettings.CLOCK_OFFSET_X, ClockLayoutSettings.DUAL_OFFSET_X,
                StatusBarSettings.SPEED_OFFSET_X, StatusBarSettings.DATA_OFFSET_X,
                StatusBarSettings.LABEL_OFFSET_X, StatusBarSettings.BATTERY_OFFSET_X, NotificationIconArea.X,
                NativeStatusIcons.X, SpeedPosition.POSITION, SpeedPosition.SAFE_GAP_ENABLED, SpeedPosition.SAFE_GAP,
                SpeedPosition.EDGE_RESERVE, ClockLayoutSettings.MARGIN_ENABLED, ClockLayoutSettings.MARGIN_START,
                ClockLayoutSettings.MARGIN_END, NetworkIconOrder.SWAP));
        // One display-mode choice edits these two historical booleans together.
        hidden.add(StatusBarSettings.LABEL_HIDDEN);
        hidden.add("data_badge_hidden");
        // Text width is measured automatically; retain the old numeric key for portable backups only.
        hidden.add(StatusBarSettings.SLOT_WIDTH);
        // Retired code175 parents keep their stored protocol keys, with no editor or effect ownership.
        hidden.addAll(Arrays.asList("statusbar_layout_adjustments_enabled", "notification_stack_effects_enabled",
                "notification_clear_controls_enabled", "lock_notification_effects_enabled"));
        HIDDEN = Collections.unmodifiableSet(hidden);
        List<Group> groups = new ArrayList<>();
        groups.add(statusBarLayout());
        groups.add(speed()); groups.add(data()); groups.add(network());
        groups.add(clock("clock", STATUSBAR)); groups.add(carrier()); groups.add(font()); groups.add(battery()); groups.add(notificationIcons());
        groups.add(nativeStatusIcons());
        groups.add(privacyDot());
        groups.add(notificationOverrides()); groups.add(iconLibrary());
        groups.add(qs()); groups.add(tileFullFill()); groups.add(tileCorners()); groups.add(tileIconSize()); groups.add(qsEntrances());
        groups.add(clock("shade_clock", NOTIFICATION));
        groups.add(bigClock()); groups.add(landscapeClock()); groups.add(wholeStack()); groups.add(miniStack()); groups.add(clear()); groups.add(landscapeClear()); groups.add(media());
        groups.add(highlightRemoval());
        groups.add(classicText()); groups.add(shadeWallpaperPage(false)); groups.add(shadeWallpaperPage(true));
        groups.add(lockscreenDate()); groups.add(lockscreenLock()); groups.add(lockscreenBlur()); groups.add(fluidAccent()); groups.add(fluidStyle()); groups.add(fluidCapsule());
        groups.add(lockAwake()); groups.add(lockAod());
        groups.add(wifiAlias());
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

    private static Group wifiAlias() {
        Builder b=builder(QsWifiAlias.GROUP,"WiFi 名称","只修改控制中心已连接 WiFi 的显示名称；按系统已保存或模块已识别的网络分别设置，同名网络共用一个别名。未知网络或未连接时保留系统显示。",CONTROL_CENTER,QsWifiAlias.ENABLED);
        b.choice(QsWifiAlias.MODE,"显示方式","按网络单独设置，或让已识别的连接统一显示同一个名称。","名称",new String[]{"per_network","unified"},new String[]{"各 WiFi 独立设置","统一显示"});
        b.text(QsWifiAlias.UNIFIED,"统一显示名称","最多 32 个字符，留空使用系统原名。","名称",null,null);
        b.text(QsWifiAlias.RULES,"按网络设置名称","选择系统已保存或模块实际识别过的 WiFi；不包含已被系统删除且模块从未识别过的记录。","名称",null,null);
        return new Group(b);
    }
    private static Builder builder(String id, String title, String description, String category, String master) {
        return new Builder(id, title, description, category, master);
    }
    private static String organizedSection(String groupId, String key, String section) {
        // The camera-area capsule names its own sections; the key-suffix rules below pulled "调整音乐封面大小" out into a lone "大小" section.
        if ("fluid_cloud_capsule".equals(groupId)) return section;
        if ("privacy_dot".equals(groupId)) return section;
        if (StatusBarLayoutSettings.GROUP.equals(groupId)) return section;
        if ("battery".equals(groupId) && (BatteryTextStyle.MASTER.equals(key) || BatteryTextStyle.NUMBERS.containsKey(key))) return "电池内文字";
        String theme = key.contains("_light_") ? "浅色界面 · " : key.contains("_dark_") ? "深色界面 · " : "";
        if (!theme.isEmpty() && "qs_appearance".equals(groupId)) return theme + (key.contains("gradient") ? "渐变" : "底色");
        if (!theme.isEmpty() && "qs_media".equals(groupId)) return theme + (key.contains("background") ? "背景" : "外发光");
        if (groupId.startsWith("notification_big_clock")) {
            key = NotificationBigClockSettings.portraitKey(key);
            if (ClockBackdropSettings.DATE_MASK_OPACITY.equals(key)) return "日期背景样式";
            if (ClockBackdropSettings.FOOTER_MASK_OPACITY.equals(key)) return "底部背景样式";
            if (ClockBackdropSettings.EDGE_ENABLED.equals(key) || ClockBackdropSettings.EDGE_WIDTH.equals(key)
                    || ClockBackdropSettings.EDGE_COLOR.equals(key) || ClockBackdropSettings.EDGE_OPACITY.equals(key)
                    || ClockBackdropSettings.HIGHLIGHT_ANGLE.equals(key) || ClockBackdropSettings.HIGHLIGHT_STRENGTH.equals(key)
                    || ClockBackdropSettings.SHADE_STRENGTH.equals(key) || ClockBackdropSettings.DEPTH_WIDTH.equals(key)) return "主时间立体边缘";
            if (NotificationBigClockSettings.COMPACT_MAX_SIZE.equals(key)) return "收起样式";
            if (NotificationBigClockSettings.COMPACT_OFFSET_X.equals(key) || NotificationBigClockSettings.COMPACT_OFFSET_Y.equals(key)) return "收起位置";
            if (NotificationBigClockSettings.DATE_OFFSET_X.equals(key) || NotificationBigClockSettings.DATE_OFFSET_Y.equals(key)
                    || NotificationBigClockSettings.DATE_GAP.equals(key) || NotificationBigClockSettings.DATE_ALIGNMENT.equals(key)) return "日期位置";
            if (NotificationBigClockSettings.FOOTER_OFFSET_X.equals(key) || NotificationBigClockSettings.FOOTER_OFFSET_Y.equals(key)
                    || NotificationBigClockSettings.FOOTER_MARGIN.equals(key) || NotificationBigClockSettings.FOOTER_ALIGNMENT.equals(key)) return "底部位置";
            // 时间页：内容与字体 → 大小与字重 → 位置与对齐 → 点击操作。
            if (NotificationBigClockSettings.PATTERN.equals(key) || NotificationBigClockSettings.FONT.equals(key)) return "内容与字体";
            if (NotificationBigClockSettings.MAX_SIZE.equals(key) || NotificationBigClockSettings.COMPACT_MAX_SIZE.equals(key)
                    || NotificationBigClockSettings.WEIGHT.equals(key) || NotificationBigClockSettings.LETTER_SPACING.equals(key)
                    || NotificationBigClockSettings.PROPORTIONAL_DIGITS.equals(key)) return "大小与字重";
            if (NotificationBigClockSettings.ALIGNMENT.equals(key) || NotificationBigClockSettings.SCREEN_PADDING.equals(key)
                    || NotificationBigClockSettings.OFFSET_X.equals(key) || NotificationBigClockSettings.OFFSET_Y.equals(key)
                    || NotificationBigClockSettings.COMPACT_OFFSET_X.equals(key) || NotificationBigClockSettings.COMPACT_OFFSET_Y.equals(key)) return "位置与对齐";
        }
        String scene = section.contains(" · ") ? section.substring(0, section.indexOf(" · ")) + " · " : "";
        if (Arrays.asList("clock", "shade_clock", "carrier", "classic_text").contains(groupId)) {
            if (key.endsWith("_size_enabled") || key.endsWith("_scale")) return scene + "大小";
            // Numeric digit alignment remains a common setting, separate from typographic fine tuning.
            if (!key.endsWith("_digit_spacing") && (key.endsWith("_weight") || key.endsWith("_spacing")
                    || key.endsWith("_text_style_enabled"))) return scene + "文字样式";
        }
        if (key.endsWith("_position_enabled")) return scene + "位置";
        if (key.endsWith("_size_enabled")) return scene + "大小";
        if (key.endsWith("_color_enabled") && (section.endsWith("独立开关") || "独立开关".equals(section))) return scene + "颜色与透明度";
        if (key.endsWith("_text_style_enabled")) return scene + ("speed".equals(groupId) || "label".equals(groupId) || "network".equals(groupId) ? "文字与间距" : "文字样式");
        if ("speed_lines_enabled".equals(key)) return "文字与间距";
        if ("独立开关".equals(section)) return "显示样式";
        return section;
    }
    private static void controls(Builder b, String prefix, String section, boolean text) {
        boolean outer = outerPosition(prefix) && !"network".equals(b.id);
        b.toggle(prefix + "_position_enabled", outer ? "垂直位置调整" : "位置调整",
                outer ? "调整上下位置；左右间距在本页独立设置"
                        + ("wifi".equals(prefix)||"label".equals(prefix)?"。开启共用位置后，两页共用此开关和上下偏移":"")
                        : "应用所设置的水平与垂直位置", section);
        b.toggle(prefix + "_size_enabled", "大小调整", "应用所设置的大小比例", section);
        b.toggle(prefix + "_color_enabled", "颜色调整", "浅色与深色背景分别设置", section);
        if (text) b.toggle(prefix + "_text_style_enabled", "文字粗细与字距", "应用所设置的文字样式", section);
    }
    private static void position(Builder b, String prefix, String section) {
        if (!outerPosition(prefix)) b.number(prefix + "_offset_x", "水平位置", section, "dp", -80, 80, .01f);
        b.number(prefix + "_offset_y", "垂直位置", section, "dp", -24, 24, .01f);
    }
    private static boolean outerPosition(String prefix) {
        return Arrays.asList("clock", "speed", "data", "wifi", "label", "battery").contains(prefix);
    }
    private static Group statusBarLayout() {
        Builder b = builder(StatusBarLayoutSettings.GROUP, "状态栏排序（实验性）",
                "选择元素所在区域与排列顺序。关闭后跟随系统区域和顺序；各元素页面的左右间距仍独立生效。未显示的元素不会被强制显示。",
                STATUSBAR, StatusBarLayoutSettings.MASTER);
        for (String token : StatusBarLayoutSettings.TOKENS) {
            String name = StatusBarLayoutSettings.label(token);
            b.choice(StatusBarLayoutSettings.key(token, "side"), "所在区域",
                    StatusBarLayoutSettings.CARRIER.equals(token) ? "运营商只在下拉栏顶部和锁屏显示，普通状态栏没有运营商；系统或模块隐藏运营商时不生效，也不会创造新文字"
                            : "左侧、右侧或跟随系统所在区域；跟随当前容器移动", name,
                    new String[]{StatusBarLayoutSettings.NATIVE, StatusBarLayoutSettings.LEFT, StatusBarLayoutSettings.RIGHT},
                    new String[]{"跟随系统", "左侧", "右侧"});
            b.add(StatusBarLayoutSettings.key(token, "order"), "排列顺序", "0 跟随系统；1 起按物理从左到右排列，同号保留原生先后。也可打开顺序列表调整。",
                    NUMERIC, name, "", 0, 16, 1, null, null, null, null);
        }
        String label = "网络制式 · 跟随蜂窝";
        b.add(StatusBarLayoutSettings.key(StatusBarLayoutSettings.LABEL, "order"), "制式与信号顺序",
                "跟随蜂窝所在区域；0 跟随系统，1 在信号前，2 在信号后。Wi-Fi 显示与制式隐藏仍由系统和原有设置决定。",
                NUMERIC, label, "", 0, 2, 1, null, null, null, null);
        b.text(NativeStatusIcons.LAYOUT,"系统提示图标逐个排序","分别选择蓝牙、定位、闹钟等提示图标所在的一侧，并调整同侧提示图标的先后；整组位置仍由系统提示图标锚点决定。","系统提示图标",null,null);
        return new Group(b);
    }
    private static void layoutMargins(Builder b, String token, String section) {
        String scope = StatusBarLayoutSettings.LABEL.equals(token) ? "网络制式跟随蜂窝。" : "";
        b.add(StatusBarLayoutSettings.key(token, "margin_left"), "左侧间距", scope + "相对原生边界调整左侧留白：正数增加、负数收紧，0 保留原生留白。元素在右侧且最靠中间时（如网速），左侧间距会直接把它向右（正数）或向左（负数）移动。保存后独立生效，无需开启状态栏排序。",
                NUMERIC, section, "dp", -40, 40, .1f, null, null, null, null);
        b.add(StatusBarLayoutSettings.key(token, "margin_right"), "右侧间距", scope + "相对原生边界调整右侧留白：正数增加、负数收紧，0 保留原生留白。元素在左侧且最靠中间时，右侧间距会直接把它向左（正数）或向右（负数）移动。保存后独立生效，无需开启状态栏排序。",
                NUMERIC, section, "dp", -40, 40, .1f, null, null, null, null);
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
        b.toggle(NetworkSpeedControls.INTERVAL_ENABLED, "调整刷新间隔", "关闭时跟随系统；较短间隔更新更快", "刷新速度");
        b.add(NetworkSpeedControls.INTERVAL_MILLIS, "刷新间隔", "单位为毫秒，建议 1–500 ms。间隔越短，采样和界面更新越频繁，请按需要调节。", NUMERIC,
                "刷新速度", "ms", 1f, 500f, 1f, null, null, null, null);
        b.toggle("speed_lines_enabled", "数字与单位间距", "分别安排数字与单位的间距", "独立开关");
        position(b, "speed", "位置");
        layoutMargins(b, StatusBarLayoutSettings.SPEED, "左右间距");
        scale(b, StatusBarSettings.SPEED_SCALE, "整体大小", "大小");
        scale(b, StatusBarSettings.SPEED_NUMBER_SCALE, "数字大小", "大小");
        scale(b, StatusBarSettings.SPEED_UNIT_SCALE, "单位大小", "大小");
        b.number(StatusBarSettings.SPEED_LINE_GAP, "数字与单位间距", "文字与间距", "dp", -8, 8, .01f);
        b.number(StatusBarSettings.SPEED_WEIGHT, "文字粗细", "文字与间距", "", 100, 900, 1);
        normalColors(b, "speed", "网速文字");
        return new Group(b);
    }
    private static Group data() {
        Builder b = builder("data", "蜂窝信号", "信号格数跟随真实系统信号。制式文字、角标与数据小箭头在“网络组合”统一设置。", STATUSBAR, "data_enabled");
        controls(b, "data", "独立开关", false);
        b.toggle("data_icon_enabled", "仿 iOS 信号样式", "保留真实信号强度", "独立开关");
        b.toggle(DataBatterySpacing.ENABLED, "无网络标识间距", "系统确认 Wi-Fi 图标和网络制式文字均不显示、蜂窝直接邻接电池时生效；状态未知时保留原生间距。", "电池边界间距");
        b.add(DataBatterySpacing.SPACING, "蜂窝与电池间距微调", "相对原生边界增加或减少，0 保留原生；仅改变蜂窝与电池边界，不改变其他图标间距与偏移。", NUMERIC,
                "电池边界间距", "dp", -12, 24, .01f, null, null, null, null);
        b.choice(StatusBarSettings.SIGNAL_LAYOUT, "信号布局", "双卡次信号仍由系统维护", "信号布局", new String[]{"system", "single"}, new String[]{"跟随系统单双排", "只显示主信号单排"});
        position(b, "data", "位置"); layoutMargins(b, StatusBarLayoutSettings.MOBILE, "左右间距");
        scale(b, StatusBarSettings.DATA_ICON_SCALE, "图标大小", "大小");
        normalColors(b, "data", "蜂窝信号");
        return new Group(b);
    }
    private static Group network() {
        Builder b=new Builder("network", "网络组合",
                "Wi-Fi、独立制式文字、原生角标在这里统一配置。Wi-Fi 与独立制式文字可共用位置，大小各自设置；角标保持信号角位置并独立微调。制式可选文字、原生角标或隐藏，切换保留各项参数。",
                STATUSBAR,null,true,"","");
        b.toggle(StatusBarLayoutSettings.WIFI_LABEL_SHARED,"Wi-Fi 与制式文字共用位置",
                "Wi-Fi 与独立制式文字共用水平、垂直位置和左右间距；所在区域与顺序在状态栏排序中一起调整。大小各自独立，关闭后恢复各自保存的位置。原生角标保持信号角位置，在角标与箭头中独立微调。","位置模式");
        b.toggle("wifi_position_enabled","位置调整","共用时调整 Wi-Fi 与独立制式文字；关闭共用或使用原生角标时只调整 Wi-Fi。","位置");
        b.number(StatusBarSettings.WIFI_OFFSET_X,"水平位置","位置","dp",-80,80,.01f);
        b.number(StatusBarSettings.WIFI_OFFSET_Y,"垂直位置","位置","dp",-24,24,.01f);
        layoutMargins(b,StatusBarLayoutSettings.WIFI,"左右间距");
        networkWifi(b);networkLabel(b);networkBadges(b);
        return new Group(b);
    }
    private static void networkBadges(Builder b) {
        b.toggle("label_color_enabled","制式配色","独立制式文字与原生角标共用这一组浅色、深色配色；关闭后跟随系统颜色。","制式配色");
        b.colorPair("label_color_light","label_color_dark","制式颜色","制式配色");
        b.toggle(NativeNetworkBadgeControls.MASTER,"自定义原生角标",
                "制式选择原生角标时生效。关闭后使用原生角标样式，保留下面两段参数。","角标自定义");
        for(int part=1;part<=2;part++) {
            String p=NativeNetworkBadgeSettings.group(part),s="角标"+part;
            b.toggle(p+"_enabled","调整"+s,part==1?"主流量卡角标；双排布局对应前段文字":"副卡角标；双排布局对应后段文字",s+" · 开关");
            b.toggle(p+"_hidden","隐藏"+s,"仅隐藏这一段文字，另一段保持原有设置。",s+" · 开关");
            b.number(p+"_offset_x","水平位置",s+" · 位置","dp",-32,32,.01f);
            b.number(p+"_offset_y","垂直位置",s+" · 位置","dp",-16,16,.01f);
            scale(b,p+"_scale","角标大小",s+" · 大小");
            b.number(p+"_weight","字体粗细",s+" · 文字","",100,900,1);
            b.choice(p+"_font","角标字体","自选字体在“其他 → 字体配置”中导入。",s+" · 文字",
                    new String[]{"native","global","system","pingfang","custom"},new String[]{"原生角标字体","跟随文字字体","系统字体","苹方","自选字体"});
        }
        b.toggle(NativeDataActivity.MASTER,"显示数据小箭头","开启后恢复原生蜂窝上传/下载活动提示，独立于本页角标主开关。","数据小箭头");
        b.number(NativeDataActivity.X,"箭头水平位置","数据小箭头","dp",-32,32,.01f);
        b.number(NativeDataActivity.Y,"箭头垂直位置","数据小箭头","dp",-16,16,.01f);
        scale(b,NativeDataActivity.SCALE,"箭头大小","数据小箭头");
        b.toggle(NativeDataActivity.COLOR_ENABLED,"自定义箭头颜色","关闭使用系统随背景变化的原生颜色。","数据小箭头");
        b.colorPair(NativeDataActivity.COLOR_LIGHT,NativeDataActivity.COLOR_DARK,"箭头颜色","数据小箭头");
    }

    private static void networkWifi(Builder b) {
        b.toggle(NetworkDisplaySettings.WIFI_HIDDEN,"隐藏 Wi-Fi 图标","只隐藏状态栏 Wi-Fi 图标，不改变实际连接；独立于自定义开关。","显示");
        b.toggle("wifi_enabled","自定义 Wi-Fi 图标","关闭后恢复原生外观，保留位置、大小与颜色参数。","显示");
        b.toggle("wifi_size_enabled","大小调整","只应用 Wi-Fi 图标的大小，不改变制式大小。","大小");
        b.toggle("wifi_color_enabled","颜色调整","浅色与深色背景分别设置。","颜色与透明度");
        b.toggle("wifi_icon_enabled", "仿 iOS Wi-Fi 样式", "保留真实 Wi-Fi 信号强度", "独立开关");
        b.toggle("wifi_activity_hidden", "隐藏 Wi-Fi 上传下载箭头", "保留 Wi-Fi 图标与连接状态", "独立开关");
        b.toggle("wifi_badge_hidden", "隐藏 Wi-Fi 角标", "减少重复网络标识", "独立开关");
        scale(b, StatusBarSettings.WIFI_ICON_SCALE, "图标大小", "大小");
        normalColors(b, "wifi", "Wi-Fi 图标");
    }
    private static void networkLabel(Builder b) {
        b.toggle("label_enabled","制式显示方式","文字：显示独立制式文字；原生角标：恢复信号旁的原生制式；隐藏：隐藏制式文字和角标。关闭文字会转为原生角标。","显示方式");
        b.toggle("label_position_enabled","独立文字位置","关闭共用位置后应用文字自己的垂直偏移；左右间距独立设置。","位置");
        b.toggle("label_size_enabled","大小调整","只应用制式文字大小，不改变 Wi-Fi 与角标大小。","大小");
        b.toggle("label_text_style_enabled","文字粗细","应用设置的文字粗细；宽度自动按文字内容、字体和大小计算，无需手动调整。","文字与间距");
        b.toggle("label_normalize_enabled", "统一网络制式名称", "5G、5G+、5GA 等都显示 5G；4G、4G+、4.5G 等都显示 4G；H、H+ 显示 3G。与“自定义制式显示内容”互斥：开启它会关闭自定义。", "独立开关");
        b.toggle(NetworkLabelFormats.ENABLED, "自定义制式显示内容", "每种制式分别设置文字或表情，例如 5G 显示“XX”、5GA 显示“5Ga”。留空则隐藏这种制式；未识别的运营商标识保留系统原文。与“统一网络制式名称”互斥：开启它会关闭统一。", "显示内容");
        for (NetworkLabel.Level level : NetworkLabel.Level.values())
            b.text(NetworkLabelFormats.key(level), level.text + " 显示内容",
                    "默认 " + level.text + "；只替换显示的文字，不改变实际网络。留空隐藏这种制式。",
                    level.family + " · 自定义显示内容", null, null);
        position(b, "label", "位置"); layoutMargins(b, StatusBarLayoutSettings.LABEL, "左右间距");
        scale(b, StatusBarSettings.LABEL_SCALE, "文字大小", "大小");
        b.number(StatusBarSettings.FONT_WEIGHT, "文字粗细", "文字与间距", "", 100, 900, 1);
    }
    private static Group clock(String id, String category) {
        boolean shade = "shade_clock".equals(id);
        Builder b = builder(id, shade ? "通知栏与控制中心时钟" : "状态栏时间", shade ? "独立设置下拉时钟；关闭后跟随状态栏时间格式与文字样式。竖屏通知页的大时钟优先显示。" : "默认时钟与双排时钟二选一，开关在各自标签的顶部。等宽数字、数字间距、冒号对齐和左右间距在“通用设置”中调整，两种模式共用。", category, id + "_controls_enabled");
        controls(b, id, "独立开关", true);
        b.toggle(id + "_enabled", "自定义时间格式", "支持中文、英文和混合日期时间格式，保留中文时段", "时间格式");
        b.text(id + "_pattern", "时间格式", "时间显示的内容，可选常用格式或自定义", "时间格式", CLOCK_VALUES, CLOCK_LABELS);
        position(b, id, "位置"); textSize(b, id, "文字样式"); normalColors(b, id, "时间文字");
        digitCells(b, id, "数字对齐");
        if (!shade) { layoutMargins(b, StatusBarLayoutSettings.CLOCK, "左右间距"); clockLayout(b); }
        return new Group(b);
    }
    /** Fixed-width digit cells stop a seconds display from moving as proportional digits change. */
    private static void digitCells(Builder b, String group, String section) {
        b.toggle(ClockLayoutSettings.digitKey(group), "等宽数字（防秒数跳动）",
                "每个数字占用相同宽度，秒数变化时间距不再左右跳动。只在时间格式含秒时生效；开启后数字间距在下面单独调整。", section);
        b.number(ClockLayoutSettings.digitSpacingKey(group), "数字间距", section, "dp", -2, 8, .01f);
        b.toggle(ClockLayoutSettings.colonKey(group), "冒号居中",
                "冒号按数字的视觉中线绘制，不再比数字偏低。时间格式里的冒号都生效，含秒或不含秒都一样，与等宽数字互不影响。", section);
    }
    /** The two-row clock is a separate mode of the status-bar time with its own master switch (shown on its own tab). */
    private static void clockLayout(Builder b) {
        b.toggle(ClockLayoutSettings.DUAL_ENABLED, "双排时钟",
                "时间分上下两排显示。双排独立于“默认时钟”：不需要打开默认时钟的开关，开启后它接管状态栏时间；内容、大小、位置、颜色都在本页设置。", "内容");
        b.text(ClockLayoutSettings.DUAL_UPPER_PATTERN, "上排内容", "第一排显示的内容，可选常用格式或自定义", "内容", DUAL_VALUES, DUAL_LABELS);
        b.text(ClockLayoutSettings.DUAL_LOWER_PATTERN, "下排内容", "第二排显示的内容，格式规则与上排相同", "内容", DUAL_VALUES, DUAL_LABELS);
        b.number(ClockLayoutSettings.DUAL_UPPER_SCALE, "上排大小", "大小", "%", 25, 250, .01f);
        b.number(ClockLayoutSettings.DUAL_LOWER_SCALE, "下排大小", "大小", "%", 25, 250, .01f);
        b.number(ClockLayoutSettings.DUAL_UPPER_WEIGHT, "上排文字粗细", "文字样式", "", 100, 900, 1);
        b.number(ClockLayoutSettings.DUAL_LOWER_WEIGHT, "下排文字粗细", "文字样式", "", 100, 900, 1);
        b.number(ClockLayoutSettings.DUAL_SPACING, "字距微调", "文字样式", "dp", -2, 8, .01f);
        b.number(ClockLayoutSettings.DUAL_OFFSET_Y, "垂直位置", "位置", "dp", -24, 24, .01f);
        b.number(ClockLayoutSettings.DUAL_LINE_GAP, "两排间距", "位置", "dp", -8, 16, .01f);
        b.choice(ClockLayoutSettings.DUAL_ALIGN, "两排对齐", "以较宽的一排为准", "位置",
                new String[]{ClockLayoutSettings.ALIGN_CENTER, ClockLayoutSettings.ALIGN_LEFT, ClockLayoutSettings.ALIGN_RIGHT},
                new String[]{"居中", "靠左", "靠右"});
        b.toggle(ClockLayoutSettings.DUAL_COLOR_ENABLED, "自定义颜色", "关闭时双排沿用系统随背景变化的原生颜色；开启后按浅色、深色背景分别设置。", "颜色与透明度");
        b.colorPair(ClockLayoutSettings.DUAL_COLOR_LIGHT, ClockLayoutSettings.DUAL_COLOR_DARK, "时间文字", "颜色与透明度");
    }
    private static Group notificationIcons() {
        Builder b = builder("notification_icons", "通知图标区域", "调整状态栏左侧通知图标，可选择原生图标、单个爱心、文字或图片。", STATUSBAR, NotificationIconArea.MASTER);
        b.choice(NotificationIconArea.MODE, "显示方式", "只在存在可显示的通知时出现", "显示样式",
                new String[]{"native", "heart", "text", "image"},
                new String[]{"原生通知图标", "单个爱心", "自定义符号或文字", "自定义图片"});
        b.toggle(NotificationIconArea.FOLLOW_LAUNCHER, "跟随桌面当前图标包", "覆盖状态栏与通知消息卡片的应用标识；仅作用于原生显示方式。指定应用替换优先，头像和消息大图保持原样，无法读取时保留系统图标。", "显示样式");
        b.text(NotificationIconArea.TEXT, "符号或文字", "最多12个字符，可输入符号或短文字", "显示样式", null, null);
        b.add(NotificationIconArea.IMAGE_NAME, "自定义图片", "选择 PNG、JPEG 或 WebP，不超过2MB", IMAGE,
                "显示样式", "", 0, 0, 0, null, null, null, null);
        b.toggle(NotificationIconArea.POSITION, "垂直位置调整", "调整上下位置；左右间距在本页独立设置", "位置");
        b.number(NotificationIconArea.Y, "垂直位置", "位置", "dp", -160, 160, .01f);
        layoutMargins(b, StatusBarLayoutSettings.NOTIFICATION, "左右间距");
        b.toggle(NotificationIconArea.SPACING_ENABLED,"调整图标间距","用于多个通知图标，单个符号、文字或图片不受影响","图标间距");
        b.number(NotificationIconArea.SPACING,"图标间距增减","图标间距","dp",-10,20,.1f);
        b.toggle(NotificationIconArea.SIZE_ENABLED, "大小调整", "应用图标或内容的大小", "大小");
        b.number(NotificationIconArea.SIZE, "显示大小", "大小", "dp", 8, 40, .01f);
        b.toggle("notification_icons_count_enabled", "限制原生图标数量", "只作用于原生显示方式，保留所有实际通知", "图标数量");
        b.number("notification_icons_max_count", "最多显示图标数量", "图标数量", "个", 0, 10, 1);
        b.toggle(NotificationIconArea.COLOR_ENABLED, "颜色调整", "关闭后跟随系统图标颜色", "颜色与透明度");
        b.colorPair("notification_icons_color_light", "notification_icons_color_dark", "图标颜色", "颜色与透明度");
        return new Group(b);
    }
    private static Group notificationOverrides() {
        Builder b = builder("notification_icon_overrides", "指定应用通知图标", "从系统或非系统应用中选择，将其状态栏通知图标替换为文字、表情或其他应用的图标。每个应用独立设置，不修改通知内容。来源应用缺失时保留原生图标。", STATUSBAR, NotificationIconOverrides.MASTER);
        b.text(NotificationIconOverrides.RULES, "管理应用替换", "选择应用与替换内容，可分别启用、编辑或删除", "应用规则", null, null);
        return new Group(b);
    }
    private static Group iconLibrary() {
        Builder b = builder("status_icon_library", "图标管理", "导入图标库后选择套装范围，或按功能单独设置图案、状态、颜色与透明度。关闭总开关暂停全部图标管理效果，保留配置与图标库；重新开启后恢复。", OTHER, IconManagementSettings.ENABLED);
        b.text(IconManagementSettings.KEY, "管理图标", "图标库、套装应用、各功能图案与颜色", "图标管理", null, null);
        return new Group(b);
    }
    private static Group carrier() {
        Builder b = builder("carrier", "运营商文字自定义", "通知栏、控制中心与锁屏分别设置运营商、时间或纯文本。", NOTIFICATION, "carrier_enabled");
        layoutMargins(b, StatusBarLayoutSettings.CARRIER, "左右间距");
        String[] titles = {"通知页", "控制中心", "锁屏"};
        for (int i = 0; i < CarrierPanels.GROUPS.length; i++) {
            String p = CarrierPanels.GROUPS[i], s = titles[i];
            b.toggle(p + "_enabled", "启用" + s + "独立设置", i == 2 ? "关闭时恢复系统运营商内容" : "只作用于此界面", s);
            controls(b, p, s + " · 独立开关", true);
            b.toggle(p + "_replace_enabled", "运营商替换", "应用下面所选的显示内容", s + " · 显示内容");
            b.choice(p + "_mode", "显示内容", "运营商位置显示什么：系统运营商、自定义时间或纯文本", s + " · 显示内容", new String[]{"original", "time", "text"}, new String[]{"运营商", "自定义时间", "纯文本"});
            b.text(p + "_pattern", "时间格式", "“显示内容”选自定义时间时显示的内容", s + " · 显示内容", CLOCK_VALUES, CLOCK_LABELS);
            b.text(p + "_text", "自定义文本", "“显示内容”选纯文本时显示的文字，中文、英文、emoji 均可", s + " · 显示内容", null, null);
            position(b, p, s + " · 位置"); textSize(b, p, s + " · 文字样式");
            b.colorPair(p + "_color_light", p + "_color_dark", "运营商文字", s + " · 颜色与透明度");
        }
        return new Group(b);
    }
    private static Group classicText() {
        String carrier = CarrierPanels.CLASSIC, clock = ClassicTextSettings.CLOCK;
        Builder b = builder("classic_text", "经典模式文字自定义",
                "只作用于系统经典下拉面板。运营商与时间日期分别设置，各标签中的具名开关控制对应功能；格式、位置、大小、颜色和透明度分别保存。切到分离模式暂停使用，原配置保留。",
                NOTIFICATION, carrier + "_enabled");
        controls(b, carrier, "运营商 · 独立开关", true);
        b.toggle(carrier + "_replace_enabled", "运营商替换", "选择原生运营商、自定义时间日期或纯文本", "运营商 · 显示内容");
        b.choice(carrier + "_mode", "显示内容", "运营商位置显示什么：系统运营商、自定义时间日期或纯文本", "运营商 · 显示内容", new String[]{"original", "time", "text"}, new String[]{"运营商", "自定义时间与日期", "纯文本"});
        b.text(carrier + "_pattern", "时间与日期格式", "“显示内容”选自定义时间与日期时显示的内容", "运营商 · 显示内容", CLOCK_VALUES, CLOCK_LABELS);
        b.text(carrier + "_text", "自定义运营商文字", "“显示内容”选纯文本时显示的文字，中文、英文、emoji 均可", "运营商 · 显示内容", null, null);
        position(b, carrier, "运营商 · 位置"); textSize(b, carrier, "运营商 · 文字样式");
        b.colorPair(carrier + "_color_light", carrier + "_color_dark", "运营商颜色", "运营商 · 颜色与透明度");
        b.toggle(ClassicTextSettings.CLOCK_MASTER, "独立调整经典模式时间", "与本页运营商开关独立，关闭恢复经典面板原生时间", "时间 · 功能开关");
        controls(b, clock, "时间 · 独立开关", true);
        b.toggle(clock + "_enabled", "自定义时间格式", "支持时间、日期和星期", "时间 · 时间格式");
        b.text(clock + "_pattern", "时间与日期格式", "经典面板时间显示的内容，可选常用格式或自定义", "时间 · 时间格式", CLOCK_VALUES, CLOCK_LABELS);
        position(b, clock, "时间 · 位置"); textSize(b, clock, "时间 · 文字样式");
        b.colorPair(clock + "_color_light", clock + "_color_dark", "时间颜色", "时间 · 颜色与透明度");
        digitCells(b, clock, "时间 · 数字对齐");
        return new Group(b);
    }
    /**
     * One orientation, one entrance: the page's own switch sits in the top bar and inside there is one tab per panel (notification centre,
     * control centre, and for the system's classic pull-down the single panel). Each tab: the picture (a card with the album picker, the
     * cropper and the rest), whether it is used, its brightness and blur, and for the notification centre the clock zone.
     */
    private static Group shadeWallpaperPage(boolean landscape) {
        String name = landscape ? "横屏" : "竖屏";
        Builder b = builder(landscape ? "shade_wallpaper_landscape" : "shade_wallpaper_portrait", name + "下拉壁纸",
                "功能未完成。" + name + "时下拉面板的壁纸。通知中心和控制中心各有一页：从相册选图、缩放裁剪，再调亮度和模糊；通知中心还能给大时钟所在的区域单独设置。"
                        + "壁纸随下拉进度出现、随左右切页混合；只设了一个面板时，另一个面板沿用同一张。大时钟的“锁屏质感”透出的就是这里的壁纸。",
                OTHER, ShadeWallpaperSettings.masterKey(landscape));
        b.toggle(ShadeWallpaperSettings.blurWithPullKey(landscape), "随下拉渐变模糊",
                "壁纸在刚拉下时是清晰的，随下拉逐渐变成设定的模糊程度，和系统自己的下拉模糊一致；关闭则一开始就是设定的模糊。两个面板共用。", "下拉过程");
        b.toggle(ShadeWallpaperSettings.swipeSlideKey(landscape), "左右切换时壁纸跟着滑动",
                "在通知中心和控制中心之间左右滑动时：关闭是两张壁纸随手势淡入淡出（背景不动），开启是两张壁纸跟着页面一起平移。只有两个面板的图片不同才有区别；同一张图始终不动。", "下拉过程");
        String[] scenes = {landscape ? ShadeWallpaperSettings.NOTIFICATION_LANDSCAPE : ShadeWallpaperSettings.NOTIFICATION_PORTRAIT,
                landscape ? ShadeWallpaperSettings.CONTROL_LANDSCAPE : ShadeWallpaperSettings.CONTROL_PORTRAIT,
                landscape ? ShadeWallpaperSettings.CLASSIC_LANDSCAPE : ShadeWallpaperSettings.CLASSIC_PORTRAIT};
        String[] panels = {"通知中心", "控制中心", "经典下拉"};
        for (int i = 0; i < scenes.length; i++) {
            String scene = scenes[i], panel = panels[i];
            b.text(ShadeWallpaperSettings.revisionKey(scene), "壁纸图片", "从相册选择图片，缩放、拖动、旋转来裁剪；没有图片时使用原生背景。", "壁纸图片", null, null);
            b.toggle(ShadeWallpaperSettings.enabledKey(scene), "使用" + panel + "壁纸",
                    i == 2 ? "系统是经典下拉（通知与控制中心在同一个面板）时使用这张图片；分离模式不用这一页。"
                            : "启用后，" + name + "下拉的" + panel + "使用这张图片；没有图片或关闭时使用原生背景。", "显示");
            b.number(ShadeWallpaperSettings.brightnessKey(scene), "图片明暗", "显示", "%", 0, 200, 1f);
            b.number(ShadeWallpaperSettings.blurKey(scene), "壁纸模糊", "显示", "dp", 0, ShadeWallpaperSettings.MAX_BLUR_DP, .5f);
            if (!ShadeWallpaperSettings.hasZone(scene)) continue;
            b.toggle(ShadeWallpaperSettings.zoneEnabledKey(scene), "大时钟区域单独设置", "大时钟所在的顶部区域用自己的一套：字下背景模糊、提亮、饱和度、叠加色，向下平滑过渡到上面的设置。大时钟的玻璃质感透出的就是这块区域的画面。", "大时钟区域");
            b.number(ShadeWallpaperSettings.zoneBlurKey(scene), "字下背景模糊", "大时钟区域", "dp", 0, ShadeWallpaperSettings.MAX_BLUR_DP, .5f);
            b.add(ShadeWallpaperSettings.zoneLiftKey(scene), "字下背景提亮", "正数向白色提亮、负数向黑色压暗，0 不改变。只作用于大时钟区域的背景。", NUMERIC, "大时钟区域", "%", -100, 100, 1f, null, null, null, null);
            b.add(ShadeWallpaperSettings.zoneSaturationKey(scene), "字下背景饱和度", "100% 保持原色，0% 变成灰色，200% 加倍鲜艳。", NUMERIC, "大时钟区域", "%", 0, 200, 1f, null, null, null, null);
            b.add(ShadeWallpaperSettings.zoneTintKey(scene), "字下背景叠加色", "盖在大时钟区域背景上的一层颜色，浓淡由下面的“叠加强度”决定。", COLOR, "大时钟区域", "", 0, 0, 0, null, null, null, null);
            b.add(ShadeWallpaperSettings.zoneTintStrengthKey(scene), "叠加强度", "0% 不叠加，100% 完全是叠加色。", NUMERIC, "大时钟区域", "%", 0, 100, 1f, null, null, null, null);
            b.toggle(ShadeWallpaperSettings.zoneFollowKey(scene), "区域高度跟随大时钟", "区域的下沿取大时钟（含日期）的下沿，随字号和位置变化；关闭后用下面的固定高度。", "大时钟区域");
            b.number(ShadeWallpaperSettings.zoneHeightKey(scene), "区域高度", "大时钟区域", "dp", 0, ShadeWallpaperSettings.MAX_ZONE_HEIGHT_DP, 1f);
            b.number(ShadeWallpaperSettings.zoneFeatherKey(scene), "过渡高度", "大时钟区域", "dp", 0, ShadeWallpaperSettings.MAX_ZONE_FEATHER_DP, 1f);
        }
        return new Group(b);
    }
    private static Group font() {
        Builder b = builder("font", "字体配置", "先选字体，再选择作用范围；字号、粗细与颜色仍在各功能页设置。", OTHER, "font_enabled");
        b.choice(StatusBarSettings.FONT_MODE, "文字来源", "统一选择系统、苹方、已导入和需下载的字体；支持导入、重命名和删除", "文字来源", new String[]{"system", "pingfang", "custom"}, new String[]{"跟随系统", "苹方", "自选字体"});
        return new Group(b);
    }
    private static Group battery() {
        Builder b = builder("battery", "电池", "跟随真实电量，电池外形、数字与闪电分别设置。", STATUSBAR, "battery_enabled");
        controls(b, "battery", "独立开关", false);
        b.toggle("battery_style_enabled", "应用所选电池样式", "关闭时恢复系统原生样式", "电池样式");
        b.choice(StatusBarSettings.BATTERY_STYLE, "电池样式", "PUI 跟随系统的电池百分比设置：百分比显示在电池里面时显示数字，显示在外面时保留电量条。需要系统开启百分比（内部或外部），关闭时恢复系统原生样式；低电量、省电和充电颜色跟随系统。", "电池样式", new String[]{"pui", "native"}, new String[]{"PUI 默认样式", "系统原生样式"});
        b.toggle(BatteryControls.HIDE_CHARGE, "隐藏充电图标", "隐藏电池内外的充电闪电；优先于数字与闪电切换，不改变充电状态与充电填充颜色", "充电显示");
        b.toggle(StatusBarSettings.BATTERY_CHARGE_INSIDE, "电池内数字与闪电切换", "充电时平滑切换，停止充电后显示电量", "充电切换");
        b.number(StatusBarSettings.BATTERY_HOLD, "每项停留时间", "充电切换", "秒", 1, 10, .01f);
        b.number(StatusBarSettings.BATTERY_FADE, "淡入淡出时长", "充电切换", "秒", .15f, 3, .01f);
        position(b, "battery", "位置");
        layoutMargins(b, StatusBarLayoutSettings.BATTERY, "左右间距");
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
        Builder b = builder("tiles", "翻页动效", "控制中心磁贴左右翻页时，离开和进入的两页各自做动效：随手指连续变化，松手后跟随系统回弹。只在翻页时绘制，静止时没有额外开销。", CONTROL_CENTER, "tiles_enabled");
        b.choice(TilePageEffects.STYLE, "动效风格", "iOS：两页轻微缩小并渐隐；立体：两页像立方体的两个面绕接缝转动；缩放：只缩小不渐隐；渐隐：只改变透明度；经典边缘：保留原来的边缘渐隐与模糊（下方“经典边缘”参数只对它生效）。", "风格",
                TilePageEffects.STYLES, new String[]{"iOS 渐隐缩放", "立体旋转", "缩放", "仅渐隐", "经典边缘渐隐"});
        b.add(StatusBarSettings.TILES_STRENGTH, "效果强度", "所有风格共用：0% 没有效果，100% 为完整效果。", NUMERIC, "风格", "%", 0, 100, .01f, null, null, null, null);
        b.toggle("tiles_portrait_enabled", "竖屏启用", "", "独立开关");
        b.toggle("tiles_landscape_enabled", "横屏启用", "", "独立开关");
        b.toggle("tiles_fade_enabled", "滑动边缘渐隐", "仅“经典边缘”风格", "经典边缘");
        b.toggle("tiles_blur_enabled", "高级模糊", "仅“经典边缘”风格；默认关闭，随滑动逐渐虚化边缘", "经典边缘");
        b.number(StatusBarSettings.TILES_LEFT_RANGE, "左侧渐隐范围", "经典边缘 · 范围与位置", "dp", 0, 192, .01f);
        b.number(StatusBarSettings.TILES_RIGHT_RANGE, "右侧渐隐范围", "经典边缘 · 范围与位置", "dp", 0, 192, .01f);
        b.number(StatusBarSettings.TILES_LEFT_OFFSET_X, "左侧水平位置", "经典边缘 · 范围与位置", "dp", -160, 160, .01f);
        b.number(StatusBarSettings.TILES_RIGHT_OFFSET_X, "右侧水平位置", "经典边缘 · 范围与位置", "dp", -160, 160, .01f);
        b.number(StatusBarSettings.TILES_OFFSET_Y, "渐隐区域垂直位置", "经典边缘 · 范围与位置", "dp", -240, 240, .01f);
        b.number(StatusBarSettings.TILES_REGION_HEIGHT, "渐隐区域高度", "经典边缘 · 范围与位置", "dp", 0, 1000, .01f);
        b.number(StatusBarSettings.TILES_VERTICAL_FEATHER, "上下柔和过渡范围", "经典边缘 · 范围与位置", "dp", 0, 192, .01f);
        b.number(StatusBarSettings.TILES_BLUR_RADIUS, "模糊半径", "经典边缘", "dp", 0, 48, .01f);
        return new Group(b);
    }
    private static Group tileFullFill() {
        return new Group(builder("tile_2x1_fill", "2×1 磁贴铺满",
                "所有普通 2×1 磁贴统一隐藏图标后的独立圆形底片，正常、编辑和拖动时保持一致；启用的磁贴铺满整张卡片，关闭或不可用的磁贴保留系统玻璃。通过原生磁贴视图显示的第三方 QS 磁贴遵循相同规则，音乐和设备卡片不受影响。颜色可在“磁贴颜色”中调整；关闭本功能恢复原生外观，自由编辑插件启用时按其圆形底片规则显示。",
                CONTROL_CENTER, QsTileFullFill.ENABLED));
    }

    private static Group tileCorners() {
        Builder b = builder("tile_corners", "统一圆角", "统一调整磁贴、亮度与音量滑块、音乐和设备卡片。关闭后恢复系统圆角。为避免原生绘制异常，半径仅支持 0–" + (int) QsTileCorners.MAX_RADIUS + " dp。", CONTROL_CENTER, "qs_tile_corners_enabled");
        b.number(QsTileCorners.RADIUS, "圆角半径", "圆角", "dp", 0, QsTileCorners.MAX_RADIUS, .5f);
        return new Group(b);
    }
    private static Group tileIconSize() {
        Builder b = builder("tile_icon_size", "1×1 磁贴与设备图标", "调整 1×1 磁贴及耳机、设备空间卡片内的图标大小，保留原生居中、卡片外形、设备电量环和点击区域。", CONTROL_CENTER, QsTileIconSize.MASTER);
        b.number(QsTileIconSize.SIZE, "图标大小", "图标大小", "dp", 0, 64, .01f);
        return new Group(b);
    }
    private static Group qsEntrances() {
        Builder b = builder(QsEntrances.GROUP, "顶部按钮", "控制中心顶部左边的“编辑”和右边的“设置”按钮。隐藏后按钮不显示，也点不到（开启“使隐藏可点击”则看不见但原位置仍可点）；其余按钮和磁贴保持原生位置。关闭本页或下面的开关即恢复。", CONTROL_CENTER, QsEntrances.MASTER);
        b.toggle(QsEntrances.EDIT_HIDDEN, "隐藏“编辑”", "控制中心左上角编辑磁贴的按钮（连同它的小红点）。隐藏后可在系统设置里调整磁贴，或临时关掉这个开关再编辑。", "按钮");
        b.toggle(QsEntrances.SETTINGS_HIDDEN, "隐藏“设置”", "控制中心右上角打开系统设置的齿轮按钮。", "按钮");
        b.toggle(QsEntrances.HIDDEN_CLICKABLE, "使隐藏可点击", "已隐藏的“编辑”“设置”按钮仍留在原来的位置，只是完全透明：点按原来的位置照常打开编辑或系统设置。关闭时隐藏的按钮不占位置、也点不到。只作用于上面已开启隐藏的按钮。", "按钮");
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
            b.add(p + "_gradient_range", "过渡范围", RANGE_BETWEEN, NUMERIC, s + " · 填充效果", "%", 5, 100, 1f, null, null, null, null);
        }
        return new Group(b);
    }
    private static Group bigClock() {
        Builder b = builder("notification_big_clock", "竖屏大时钟", "只调整竖屏通知页，横屏单独配置。时间与通知左右边缘对齐，通知字号、图标与卡片尺寸保持原生。上滑时顶部时间与通知边缘渐隐、模糊，底部自定义文字保持清晰。默认关闭；既有竖屏配置继续保存。", NOTIFICATION, NotificationBigClockSettings.MASTER);
        b.text(NotificationBigClockSettings.PATTERN, "时间格式", "通知页大时钟显示的内容，可选常用格式或自定义", "时间", CLOCK_VALUES, CLOCK_LABELS);
        b.choice(NotificationBigClockSettings.FONT, "字体来源", "原生字体有三种：跟随锁屏当前字体，或指定 OPPO Sans、OPPO Digit、OPPO Serif；竖屏可分别设置展开与收起高度，其他字体保持一个高度。自选字体使用“其他 → 字体配置”中导入的文件。", "时间", NotificationBigClockSettings.FONT_VALUES, NotificationBigClockSettings.FONT_LABELS);
        b.number(NotificationBigClockSettings.MAX_SIZE, "字体高度调节", "时间", "dp", 48, 240, .01f);
        b.number(NotificationBigClockSettings.COMPACT_MAX_SIZE, "收起字体高度", "时间", "dp", 12, 96, .01f);
        b.number(NotificationBigClockSettings.SCREEN_PADDING, "屏幕内间距设置", "时间", "dp", 0, 80, .1f);
        b.number(NotificationBigClockSettings.WEIGHT, "时间字体粗细", "时间", "", 100, 900, 1);
        b.number(NotificationBigClockSettings.LETTER_SPACING, "时间字距", "时间", "em", -.15f, .5f, .01f);
        b.toggle(NotificationBigClockSettings.PROPORTIONAL_DIGITS, "等宽字距限制解除",
                "时间数字按各自字形宽度排布，可配合时间字距调整；实际宽度取决于所选字体。", "时间");
        b.toggle(NotificationBigClockSettings.TIME_ANIMATION_DISABLED, "关闭时间变动动画",
                "时间变化时直接更新数字，保留大时钟的入场、退场、下拉和回弹动效。", "时间变动");
        b.toggle(NotificationBigClockSettings.LOCK_STYLE, "锁屏质感",
                "让通知页大时钟的字形和质感像锁屏的时钟：圆润的轮廓、上浓下淡的奶白字身、一侧受光的亮边、另一侧的暗部和淡淡的外发光。这是对锁屏效果的模拟，不是逐像素复制（锁屏会取壁纸画面，这里只用时钟自己的像素）。开启后“液态玻璃文字”和“玻璃边框”暂不生效，原设置会保留；需要 Android 13 及以上。", "锁屏质感");
        b.toggle(NotificationBigClockSettings.LOCK_STYLE_NATIVE, "借用锁屏原生玻璃",
                "锁屏显示过时钟之后，直接用锁屏时钟自己的玻璃效果（透出壁纸、折射、发光、亮边），这是最像锁屏的做法；此时只有“字身浓度”（玻璃强度）起作用。锁屏还没显示过、或系统版本不同借不到时，自动改用下面的近似模拟，下面其余强度只作用于近似模拟。", "锁屏质感");
        b.add(NotificationBigClockSettings.LOCK_STYLE_BODY, "字身浓度", "原生玻璃的强度，也是近似模拟字身的不透明程度：100% 是锁屏的样子，越小越透。", NUMERIC, "锁屏质感", "%", 0, NotificationBigClockSettings.LOCK_STYLE_STRENGTH_MAX, 1f, null, null, null, null);
        b.add(NotificationBigClockSettings.LOCK_STYLE_GRADIENT, "上浓下淡", "字身从上到下由浓变淡的幅度：0% 上下一样，越大上下差别越明显。", NUMERIC, "锁屏质感", "%", 0, NotificationBigClockSettings.LOCK_STYLE_STRENGTH_MAX, 1f, null, null, null, null);
        b.add(NotificationBigClockSettings.LOCK_STYLE_RIM, "亮边强度", "受光一侧字边的高光：0% 没有高光，越大越亮。", NUMERIC, "锁屏质感", "%", 0, NotificationBigClockSettings.LOCK_STYLE_STRENGTH_MAX, 1f, null, null, null, null);
        b.add(NotificationBigClockSettings.LOCK_STYLE_RIM_DEPTH, "亮边宽度", "高光和暗部向字身里面延伸的宽度：越大越宽越柔，越小越贴边。", NUMERIC, "锁屏质感", "%", 0, NotificationBigClockSettings.LOCK_STYLE_STRENGTH_MAX, 1f, null, null, null, null);
        b.add(NotificationBigClockSettings.LOCK_STYLE_ROUND, "圆润度", "字形轮廓的圆润程度：0% 保持原来的锐利边角，越大越圆、越像锁屏的软糯。", NUMERIC, "锁屏质感", "%", 0, NotificationBigClockSettings.LOCK_STYLE_STRENGTH_MAX, 1f, null, null, null, null);
        b.add(NotificationBigClockSettings.LOCK_STYLE_SHADE, "暗部强度", "背光一侧字边的暗色：0% 没有暗部，越大越深，字更有立体感。", NUMERIC, "锁屏质感", "%", 0, NotificationBigClockSettings.LOCK_STYLE_STRENGTH_MAX, 1f, null, null, null, null);
        b.add(NotificationBigClockSettings.LOCK_STYLE_GLOW, "外发光", "字形外面一圈淡淡的光晕：0% 没有，越大越明显。", NUMERIC, "锁屏质感", "%", 0, NotificationBigClockSettings.LOCK_STYLE_STRENGTH_MAX, 1f, null, null, null, null);
        b.add(NotificationBigClockSettings.LOCK_STYLE_ANGLE, "光照方向", "光从哪个方向照来：0° 来自右边，90° 来自上方，180° 来自左边，270° 来自下方，默认 300°（右下偏下）。", NUMERIC, "锁屏质感", "°", 0, 360, 1f, null, null, null, null);
        b.toggle(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED, "字内背景透出",
                "时间字形内透出系统已有的模糊背景，移除其中的黑色压暗，再叠加可调颜色遮罩。开启后固定使用专用背景材质，原有文字颜色、液态玻璃与锁屏质感设置保留，关闭后恢复；字体来源、字号、字重、字距、位置和动效仍可调整。", "背景透出与遮罩");
        b.toggle(NotificationBigClockSettings.FOLLOW_STYLE, "时间和日期样式跟随",
                "默认关闭。仅在字内背景透出开启后可用：日期和附加文字跟随时间使用背景透出与同一遮罩颜色，遮罩强度可分别调整，保持平面效果；立体边缘仅用于大时钟数字。关闭时保留各自独立的颜色和外观。", "背景透出与遮罩");
        b.add(NotificationBigClockSettings.REVEAL_MASK_COLOR, "遮罩颜色", "叠加在字形内模糊背景上的颜色，默认白色；横竖屏分别保存。颜色自身的透明度与各类文字的遮罩强度共同决定浓淡。", COLOR, "背景透出与遮罩", "", 0, 0, 0, null, null, null, null);
        b.add(NotificationBigClockSettings.REVEAL_MASK_OPACITY, "主时间遮罩强度", "只调整大时钟时间数字。0% 不叠加颜色，字内仍透出无黑色压暗的模糊背景；100% 按所选颜色及其透明度覆盖背景。默认白色 20%。", NUMERIC, "背景透出与遮罩", "%", 0, 100, 1f, null, null, null, null);
        b.add(ClockBackdropSettings.DATE_MASK_OPACITY,"日期遮罩强度","开启时间和日期样式跟随后，只调整日期文字的颜色遮罩浓淡，0% 保留字内背景。",NUMERIC,"背景透出与遮罩","%",0,100,1f,null,null,null,null);
        b.add(ClockBackdropSettings.FOOTER_MASK_OPACITY,"附文遮罩强度","开启时间和日期样式跟随后，只调整底部附加文字的颜色遮罩浓淡，0% 保留字内背景。",NUMERIC,"背景透出与遮罩","%",0,100,1f,null,null,null,null);
        b.toggle(ClockBackdropSettings.EDGE_ENABLED,"边缘描边","沿大时钟数字边缘显示描边，提高复杂背景上的辨识度；默认开启。日期、附文与左右组件保持平面。","背景透出与遮罩");
        b.add(ClockBackdropSettings.EDGE_WIDTH,"描边宽度","控制字形描边的宽度，默认 0.6 dp。",NUMERIC,"背景透出与遮罩","dp",0,4,.01f,null,null,null,null);
        b.add(ClockBackdropSettings.EDGE_COLOR,"描边颜色","默认白色，支持颜色自身透明度，并与描边不透明度共同决定浓淡。",COLOR,"背景透出与遮罩","",0,0,0,null,null,null,null);
        b.add(ClockBackdropSettings.EDGE_OPACITY,"描边不透明度","描边的浓淡，默认 60%。",NUMERIC,"背景透出与遮罩","%",0,100,1f,null,null,null,null);
        b.add(ClockBackdropSettings.HIGHLIGHT_ANGLE,"高光角度","调整字形立体边缘的光照方向，默认 315°。",NUMERIC,"背景透出与遮罩","°",0,360,1f,null,null,null,null);
        b.add(ClockBackdropSettings.HIGHLIGHT_STRENGTH,"高光亮度","受光一侧的亮边强度，默认 40%；0% 关闭高光。",NUMERIC,"背景透出与遮罩","%",0,100,1f,null,null,null,null);
        b.add(ClockBackdropSettings.SHADE_STRENGTH,"暗部强度","背光一侧的暗边强度，默认 0%，保留无黑色压暗的字身；仅在主动调高后增加边缘暗部。",NUMERIC,"背景透出与遮罩","%",0,100,1f,null,null,null,null);
        b.add(ClockBackdropSettings.DEPTH_WIDTH,"立体边缘宽度","高光与暗边向字形内部延伸的宽度，默认 1.5 dp。",NUMERIC,"背景透出与遮罩","dp",0,8,.01f,null,null,null,null);
        b.toggle(NotificationBigClockSettings.GLASS, "液态玻璃文字", "调整时间文字的材质；字内背景透出开启时暂不生效，原设置保留，关闭背景透出后恢复。", "液态玻璃");
        b.colorPair(NotificationBigClockSettings.COLOR_LIGHT, NotificationBigClockSettings.COLOR_DARK, "时间文字颜色", "文字颜色");
        b.toggle(NotificationBigClockSettings.GLASS_BORDER_ENABLED, "显示玻璃边框", "", "玻璃边框");
        b.number(NotificationBigClockSettings.GLASS_BORDER_WIDTH, "玻璃边框宽度", "玻璃边框", "dp", 0, 4, .01f);
        b.colorPair(NotificationBigClockSettings.GLASS_BORDER_COLOR_LIGHT, NotificationBigClockSettings.GLASS_BORDER_COLOR_DARK, "边框颜色", "玻璃边框");
        b.toggle(NotificationBigClockSettings.ENTRY_EFFECT_ENABLED, "随下拉手势移动、模糊与渐显", "展开与收起使用同一过渡", "入场效果");
        b.number(NotificationBigClockSettings.ENTRY_TRAVEL, "入场移动距离", "入场效果", "dp", 0, 120, .01f);
        b.number(NotificationBigClockSettings.ENTRY_BLUR_RADIUS, "入口模糊半径", "入场效果", "dp", 0, 32, .01f);
        b.number(NotificationBigClockSettings.ENTRY_FADE_STRENGTH, "入口渐显强度", "入场效果", "%", 0, 100, .01f);
        b.number(NotificationBigClockSettings.ENTRY_COMPLETION, "清晰显示的下拉进度", "入场效果", "%", 1, 100, .01f);
        b.toggle(NotificationBigClockSettings.REBOUND_BLUR_ENABLED, "回弹时模糊", "通知列表在顶部或底部过度滑动、被原生弹簧弹回时，时钟被推开的距离越大越模糊，回到原位后恢复清晰。只作用于竖屏大时钟。", "回弹效果");
        b.number(NotificationBigClockSettings.REBOUND_BLUR_RADIUS, "回弹模糊半径", "回弹效果", "dp", 0, 32, .1f);
        b.number(NotificationBigClockSettings.REBOUND_FADE, "回弹渐隐", "回弹效果", "%", 0, 100, 1f);
        b.toggle(NotificationBigClockSettings.REBOUND_LOCK, "关闭回弹效果", "通知列表过度滑动、被原生弹簧弹回时，时钟不再被推着移动：快速下拉、滑到底继续上推时都固定在原位。下拉时时钟的渐显和模糊、上拉时的渐隐消失都保留，只去掉回弹这一个互动。与“回弹时模糊”互斥：开启后后者被锁定关闭。只作用于竖屏大时钟。", "回弹效果");
        b.toggle(NotificationBigClockSettings.CLOCK_CLICK_ENABLED, "点击时间打开时钟", "只作用于竖屏大时钟，默认开启", "点击操作");
        b.text(NotificationBigClockSettings.CLOCK_LAUNCH_APP, "时间点击打开的应用", "默认使用系统时钟，也可选择其他应用", "点击操作", null, null);
        b.toggle(NotificationBigClockSettings.DATE_CLICK_ENABLED, "点击日期打开日历", "日期显示时生效，默认开启", "日期 · 点击操作");
        b.text(NotificationBigClockSettings.DATE_LAUNCH_APP, "日期点击打开的应用", "默认使用系统日历，也可选择其他应用", "日期 · 点击操作", null, null);
        b.toggle(NotificationBigClockSettings.DATE_ENABLED, "显示日期", "", "日期");
        b.text(NotificationBigClockSettings.DATE_PATTERN, "日期格式", "日期行显示的内容，支持星期、农历与干支", "日期", DATE_VALUES, DATE_LABELS);
        b.number(NotificationBigClockSettings.DATE_SIZE, "日期字号", "日期", "dp", 8, 40, .01f);
        b.number(NotificationBigClockSettings.DATE_WEIGHT, "日期字体粗细", "日期", "", 100, 900, 1);
        b.number(NotificationBigClockSettings.DATE_OFFSET_X, "日期水平位置", "日期", "dp", -120, 120, .01f);
        b.number(NotificationBigClockSettings.DATE_OFFSET_Y, "日期垂直位置", "日期", "dp", -120, 120, .01f);
        b.number(NotificationBigClockSettings.DATE_GAP, "日期与时间间距", "日期", "dp", 0, 80, .01f);
        alignment(b, NotificationBigClockSettings.DATE_ALIGNMENT, "日期对齐", "日期");
        b.colorPair(NotificationBigClockSettings.DATE_COLOR_LIGHT, NotificationBigClockSettings.DATE_COLOR_DARK, "日期文字", "日期颜色");
        b.toggle(BigClockWidgetsSettings.SWAP_WITH_DATE,"与日期互换位置","只在竖屏交换日期行与左右组件整行，主时间保持原位。日期或组件不可用、上方空间不足或日期与时间位置重叠时，自动使用原布局；已保存的开关不变。","组件布局");
        bigClockTextComponent(b,BigClockWidgetsSettings.LEFT);
        bigClockTextComponent(b,BigClockWidgetsSettings.RIGHT);
        alignment(b, NotificationBigClockSettings.ALIGNMENT, "时间对齐", "位置与对齐");
        b.number(NotificationBigClockSettings.OFFSET_X, "初始水平位置", "位置与对齐", "dp", -160, 160, .01f);
        b.number(NotificationBigClockSettings.OFFSET_Y, "初始垂直位置", "位置与对齐", "dp", -120, 200, .01f);
        b.number(NotificationBigClockSettings.COMPACT_OFFSET_X, "收起水平位置", "位置与对齐", "dp", -160, 160, .01f);
        b.number(NotificationBigClockSettings.COMPACT_OFFSET_Y, "收起垂直位置", "位置与对齐", "dp", -120, 120, .01f);
        b.toggle(NotificationBigClockSettings.NOTIFICATION_GAP_ENABLED, "自动保留时钟与首张通知的距离", "", "通知留白");
        b.add(NotificationBigClockSettings.NOTIFICATION_GAP, "时间与通知间距", "正值增加留白，负值让通知靠近并进入时钟区域；不受回弹开关影响。",
                NUMERIC, "通知留白", "dp", -100, 100, .01f, null, null, null, null);
        b.toggle(NotificationBigClockSettings.NOTIFICATION_EDGE_ENABLED, "通知进入顶部边缘时模糊与渐隐", "效果随真实通知位置变化", "通知顶部边缘");
        b.add(NotificationBigClockSettings.NOTIFICATION_EDGE_SAFE_DISTANCE, "顶部安全距离", "相对时钟实际底边调整清晰区域起点，负值向上移动模糊与渐隐区域；保留屏幕顶部安全区，不受回弹开关影响。",
                NUMERIC, "通知顶部边缘", "dp", -100, 100, .01f, null, null, null, null);
        b.add(NotificationBigClockSettings.NOTIFICATION_EDGE_RANGE, "过渡范围", "通知从顶部安全距离处开始模糊与渐隐。" + RANGE_DISTANCE,
                NUMERIC, "通知顶部边缘", "dp", 0, 120, .01f, null, null, null, null);
        b.number(NotificationBigClockSettings.NOTIFICATION_EDGE_BLUR_RADIUS, "边缘模糊半径", "通知顶部边缘", "dp", 0, 32, .01f);
        b.toggle(NotificationBigClockSettings.FOOTER_ENABLED, "显示底部内容", "", "底部内容");
        b.text(NotificationBigClockSettings.FOOTER_PATTERN, "内容格式", "底部一行的格式，{text} 代表下面设置的文字", "底部内容", FOOTER_VALUES, FOOTER_LABELS);
        b.text(NotificationBigClockSettings.FOOTER_TEXT, "编辑纯文本", "底部显示的文字，中文、英文、emoji 均可", "底部内容", null, null);
        b.number(NotificationBigClockSettings.FOOTER_SIZE, "底部字号", "底部内容", "dp", 8, 36, .01f);
        b.number(NotificationBigClockSettings.FOOTER_WEIGHT, "底部字体粗细", "底部内容", "", 100, 900, 1);
        b.number(NotificationBigClockSettings.FOOTER_OFFSET_X, "底部水平位置", "底部内容", "dp", -160, 160, .01f);
        b.number(NotificationBigClockSettings.FOOTER_OFFSET_Y, "底部垂直位置", "底部内容", "dp", -120, 120, .01f);
        b.number(NotificationBigClockSettings.FOOTER_MARGIN, "底部安全间距", "底部内容", "dp", 0, 120, .01f);
        alignment(b, NotificationBigClockSettings.FOOTER_ALIGNMENT, "底部内容对齐", "底部内容");
        b.colorPair(NotificationBigClockSettings.FOOTER_COLOR_LIGHT, NotificationBigClockSettings.FOOTER_COLOR_DARK, "底部文字", "底部颜色");
        return new Group(b);
    }
    private static void bigClockTextComponent(Builder b,int side) {
        String name=side==BigClockWidgetsSettings.LEFT?"左侧":"右侧",section=name+"组件";
        boolean right=side==BigClockWidgetsSettings.RIGHT;
        b.toggle(BigClockWidgetsSettings.enabledKey(side),"显示"+section,"在竖屏大时钟区域显示"+name+"文字组件，可与日期整行互换位置。默认关闭。",section);
        b.text(BigClockWidgetsSettings.textKey(side),name+"组件内容","可直接选预设，也可自由输入并与普通文字组合。"
                        +"天气、电池、信号、网络可像时间格式一样自定义，符号自己写：（电池:{电量}%{充电?⚡}）、（天气:{描述} {温度:数字}°C）、（信号:{条:4:●:○}）、（网络:{类型}）。"
                        +"原有的（天气）（天气原色）（蜂窝信号）（网络状态）（电池）等仍然有效，显示跟随状态栏样式的图标与实时状态。"
                        +"日期时间：（时间）（日期）（星期）（周）（年月日）（12小时）（时段）（农历）（农历日期）（干支）（日期英文）（星期英文）；（格式:HH:mm {周}）可按时间格式自定义。"
                        +"半角括号同样有效。",section,
                BigClockPresets.values(right),BigClockPresets.labels(right));
        b.add(BigClockWidgetsSettings.sizeKey(side),name+"组件字号","只调整这侧组件的文字大小。",NUMERIC,section,"dp",BigClockWidgetsSettings.MIN_SIZE,BigClockWidgetsSettings.MAX_SIZE,1f,null,null,null,null);
        b.add(BigClockWidgetsSettings.weightKey(side),name+"组件字体粗细","只调整这侧文字粗细，默认 400；图标保持状态栏所选外形。",NUMERIC,section,"",BigClockWidgetsSettings.MIN_WEIGHT,BigClockWidgetsSettings.MAX_WEIGHT,1f,null,null,null,null);
        b.add(BigClockWidgetsSettings.offsetXKey(side),name+"组件水平位置","相对于这侧默认位置的水平偏移，负值向左，正值向右。",NUMERIC,section,"dp",-BigClockWidgetsSettings.MAX_OFFSET,BigClockWidgetsSettings.MAX_OFFSET,1f,null,null,null,null);
        b.add(BigClockWidgetsSettings.offsetYKey(side),name+"组件垂直位置","相对于大时钟下方默认位置的垂直偏移，负值向上，正值向下。",NUMERIC,section,"dp",-BigClockWidgetsSettings.MAX_OFFSET,BigClockWidgetsSettings.MAX_OFFSET,1f,null,null,null,null);
        b.toggle(BigClockWidgetsSettings.followTimeColorKey(side),name+"组件颜色跟随主时间","默认开启，跟随主时间的颜色；主时间启用字内背景透出时，跟随其遮罩颜色，遮罩强度可独立调整，组件保持平面效果。关闭后可独立设置组件颜色。",section);
        b.add(BigClockWidgetsSettings.maskOpacityKey(side),name+"组件遮罩强度","此侧组件跟随主时间且主时间开启字内背景透出时生效；只调整此侧颜色遮罩浓淡，0% 保留字内背景。",NUMERIC,section,"%",0,100,1f,null,null,null,null);
        b.colorPair(BigClockWidgetsSettings.colorKey(side,false),BigClockWidgetsSettings.colorKey(side,true),name+"组件颜色",section);
        b.toggle(BigClockWidgetsSettings.clickEnabledKey(side),name+"组件点击打开应用","默认关闭，开启后点击这侧文字组件打开选定应用。",section);
        b.text(BigClockWidgetsSettings.launchAppKey(side),name+"组件点击打开的应用","选择应用后，点击这侧组件打开；留空时不启动应用。",section,null,null);
    }
    private static Group landscapeClock() {
        Group portrait = bigClock();
        Builder b = builder("notification_big_clock_landscape", "横屏大时钟",
                "只调整横屏通知页，上方时钟、下方通知，清除按钮在右侧。通知大小保持原生，时间与通知左右对齐；顶部可随上滑淡出、模糊，底部自定义文字保持清晰。与竖屏大时钟独立保存、独立开启，默认关闭。",
                NOTIFICATION, NotificationBigClockSettings.LANDSCAPE_MASTER);
        for (Item item : portrait.items) if (!item.master && !NotificationBigClockSettings.SCREEN_PADDING.equals(item.key)
                && !bigClockWidget(item.key)
                && !NotificationBigClockSettings.CLOCK_CLICK_ENABLED.equals(item.key)
                && !NotificationBigClockSettings.DATE_CLICK_ENABLED.equals(item.key)
                && !NotificationBigClockSettings.CLOCK_LAUNCH_APP.equals(item.key)
                && !NotificationBigClockSettings.DATE_LAUNCH_APP.equals(item.key)
                && !NotificationBigClockSettings.REBOUND_BLUR_ENABLED.equals(item.key)
                && !NotificationBigClockSettings.REBOUND_BLUR_RADIUS.equals(item.key)
                && !NotificationBigClockSettings.REBOUND_FADE.equals(item.key)
                && !NotificationBigClockSettings.REBOUND_LOCK.equals(item.key)
                && !HIDDEN.contains(NotificationBigClockSettings.landscapeKey(item.key))) b.add(
                NotificationBigClockSettings.landscapeKey(item.key), item.title, item.description, item.type,
                item.section, item.unit, item.min, item.max, item.step, item.values, item.labels,
                NotificationBigClockSettings.landscapeKey(item.pairedKey), item.scene);
        b.toggle(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH_ENABLED,"调整通知长度","只改变横屏通知卡片宽度，通知文字与图标大小保持原生。","通知布局");
        b.number(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH,"通知长度","通知布局","%",50,150,1);
        return new Group(b);
    }
    private static Group clear() {
        Builder b = builder("notification_clear", "竖屏清除按钮位置", "位置、入场动效、通知安全距离和按钮颜色只作用于竖屏，保留原生清除操作。横屏位置与颜色请到横屏清除按钮位置单独设置。", NOTIFICATION, NotificationClearAppearance.MASTER);
        b.toggle("notification_clear_motion_enabled", "独立滑入与淡入", "只调整清除按钮的入场与收起，不依赖大时钟", "动效与安全距离");
        b.number("notification_clear_safe_distance", "与通知安全距离", "动效与安全距离", "dp", 0, 160, .1f);
        b.number("notification_clear_entry_travel", "入场位移", "动效与安全距离", "dp", 0, 160, .1f);
        b.number("notification_clear_offset_y", "垂直位置", "动效与安全距离", "dp", -160, 160, .1f);
        b.colorPair(NotificationClearAppearance.COLOR_LIGHT, NotificationClearAppearance.COLOR_DARK, "按钮底色", "按钮底色");
        b.toggle(NotificationClearAppearance.GRADIENT_ENABLED, "启用渐变", "", "渐变效果");
        b.colorPair(NotificationClearAppearance.GRADIENT_COLOR_LIGHT, NotificationClearAppearance.GRADIENT_COLOR_DARK, "渐变终点", "渐变效果");
        b.number(NotificationClearAppearance.OPACITY, "底色透明度", "按钮底色", "%", 0, 100, .01f);
        b.number(NotificationClearAppearance.GRADIENT_ANGLE, "渐变方向", "渐变效果", "°", 0, 360, .01f);
        b.add(NotificationClearAppearance.GRADIENT_RANGE, "过渡范围", RANGE_BETWEEN, NUMERIC, "渐变效果", "%", 5, 100, 1f, null, null, null, null);
        return new Group(b);
    }
    private static Group landscapeClear() {
        Builder b = builder("notification_clear_landscape", "横屏清除按钮位置",
                "位置与颜色独立于竖屏保存，位置开关不影响下方颜色开关。跟随通知页原生移动、渐隐与清除操作；位移过大可能与通知重叠或超出屏幕。首次升级继承原先共用颜色，之后两方向分别调整。",
                NOTIFICATION, NotificationClearMotion.LANDSCAPE_MASTER);
        b.number(NotificationClearMotion.LANDSCAPE_OFFSET_X, "水平位置", "位置", "dp", -160, 160, .1f);
        b.number(NotificationClearMotion.LANDSCAPE_OFFSET_Y, "垂直位置", "位置", "dp", -160, 160, .1f);
        b.toggle(NotificationClearAppearance.LANDSCAPE_MASTER,"自定义按钮颜色","仅横屏生效，不依赖位置开关；关闭恢复横屏原生材质。","按钮底色");
        b.colorPair(NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT,NotificationClearAppearance.LANDSCAPE_COLOR_DARK,"按钮底色","按钮底色");
        b.number(NotificationClearAppearance.LANDSCAPE_OPACITY,"底色透明度","按钮底色","%",0,100,.01f);
        b.toggle(NotificationClearAppearance.LANDSCAPE_GRADIENT_ENABLED,"启用渐变","仅横屏自定义颜色开启时生效。","渐变效果");
        b.colorPair(NotificationClearAppearance.LANDSCAPE_GRADIENT_COLOR_LIGHT,NotificationClearAppearance.LANDSCAPE_GRADIENT_COLOR_DARK,"渐变终点","渐变效果");
        b.number(NotificationClearAppearance.LANDSCAPE_GRADIENT_ANGLE,"渐变方向","渐变效果","°",0,360,.01f);
        b.add(NotificationClearAppearance.LANDSCAPE_GRADIENT_RANGE,"过渡范围",RANGE_BETWEEN,NUMERIC,"渐变效果","%",5,100,1f,null,null,null,null);
        return new Group(b);
    }
    private static Group lockscreenDate() {
        Builder b=builder("lockscreen_date", "锁屏日期自定义",
                "自定义锁屏的年月日与星期文字，保持系统原生字体、大小和更新频率。仅处理锁屏日期，不改变通知页或控制中心时间，默认关闭。",
                LOCK_SCREEN, LockscreenControls.DATE_ENABLED);
        b.text(LockscreenControls.DATE_FORMAT,"日期格式","锁屏日期显示的内容，例如 yyyy年M月d日 {星期}","显示内容",
                new String[]{"M月d日 {周}","yyyy年M月d日 {星期}","yyyy/MM/dd EEEE","M月d日"},
                new String[]{"月日与星期","年月日与完整星期","数字日期与星期","只显示月日"});
        return new Group(b);
    }
    private static Group lockscreenLock() {
        return new Group(builder("lockscreen_lock_icon", "隐藏锁屏小锁",
                "隐藏锁屏小锁和它旁边的充电提示文字，只改绘制；保留解锁触摸区域、原生状态和指纹交互，默认关闭。",
                LOCK_SCREEN, LockscreenControls.HIDE_LOCK));
    }
    private static Group lockscreenBlur() {
        Builder b=builder("lockscreen_status_blur", "状态栏区域模糊",
                "只模糊锁屏顶部壁纸，向下逐渐恢复清晰。时间、通知和状态图标保持清晰；缓存壁纸模糊，随原生解锁和息屏进度淡出。",
                LOCK_SCREEN, LockscreenStatusBlur.ENABLED);
        b.number(LockscreenStatusBlur.RADIUS,"模糊强度","模糊范围","dp",0,40,.1f);
        b.number(LockscreenStatusBlur.RANGE,"主要模糊高度","模糊范围","dp",0,200,.1f);
        b.number(LockscreenStatusBlur.TRANSITION,"由虚到实过渡高度","模糊范围","dp",0,120,.1f);
        b.add(LockscreenStatusBlur.MASK_COLOR,"颜色遮罩","向下渐隐，只覆盖壁纸；颜色透明度为 0 时不显示遮罩。",COLOR,"颜色遮罩","",0,0,0,null,null,null,null);
        return new Group(b);
    }
    private static Group fluidAccent() {
        Builder b=builder("fluid_cloud_accent", "流体云媒体强调色",
                "音乐流体云使用系统取色的强调色，其他卡片可跟随当前媒体色。关闭总开关后，锁屏与 AOD 的卡片、普通通知和底部胶囊也停止媒体取色；各页的渐变、不透明度、提亮和跟随设置仍会保留。锁屏与 AOD 的样式在“锁屏 → 亮屏锁屏 / AOD 息屏显示”里分别设置。作用于悬浮展开、锁屏和 AOD；通知栏不受影响，该场景已停用。",
                OTHER, FluidCloudAccent.ENABLED);
        b.toggle(FluidCloudAccent.MASK_ENABLED,"强调色遮罩","在媒体色上叠一层颜色，强调色与跟随色共用。","遮罩");
        b.add(FluidCloudAccent.MASK_COLOR,"遮罩颜色","",COLOR,"遮罩","",0,0,0,null,null,null,null);
        b.add(FluidCloudAccent.MASK_OPACITY,"遮罩不透明度","遮罩盖在媒体色上的浓淡：100% 完全不透明，0% 完全透明。",NUMERIC,"遮罩","%",0,100,.1f,null,null,null,null);
        b.add(FluidCloudAccent.OPACITY,"填充不透明度","强调色与跟随色铺在卡片上的浓淡：100% 完全不透明，0% 完全透明。锁屏开了“锁屏自定义”时用下面的锁屏值。",NUMERIC,"颜色填充","%",0,100,.1f,null,null,null,null);
        b.toggle(FluidCloudStyle.FOLLOW_MEDIA,"跟随媒体强调色","没有自己强调色的卡片使用当前媒体色，用本页的遮罩与不透明度；没有媒体时使用“流体云样式”里的自定义底色。需要先开启“流体云样式”里的自定义底色，关掉它跟随也一起停止。颜色变化（切歌、关掉媒体、回到系统颜色）会淡入淡出，不会突然跳变。","跟随媒体色");
        b.toggle(FluidCloudStyle.FOLLOW_ONLY_PLAYING,"仅播放时跟随","媒体暂停或停止后不再跟随，回到自定义底色。","跟随媒体色");
        return new Group(b);
    }
    /**
     * Lock screen with the screen on: how the fluid-cloud cards (music, stopwatch, countdown) and the ordinary notifications are coloured,
     * and the "unify style" switch that makes AOD and the notifications follow it. Where the colour comes from stays on the media-accent page.
     */
    private static Group lockAwake() {
        Builder b = new Builder("lock_awake", "亮屏锁屏",
                "屏幕亮着时锁屏上的样子：音乐、秒表、计时等流体云卡片怎么铺颜色、渐变到哪里、提亮和遮罩，底部的小胶囊，以及普通通知（微信、短信等）是否跟随音乐主色。息屏显示在“AOD”页单独设置；“统一样式”让普通通知采用流体云卡片的样式。媒体取色需要开启“流体云媒体强调色”；关闭后，锁屏停止媒体取色，这一页的样式设置仍保留，中性玻璃与自定义底色按各自设置生效。",
                LOCK_SCREEN, FluidCloudAccent.LOCK_GRADIENT, true, "亮屏渐变样式", "音乐卡在亮屏锁屏用顶部主色渐变到透明（到哪里完全淡出由下面的“过渡范围”决定）；默认关闭，整张卡片和秒表等其他卡片一样填充，锁屏观感统一。");
        b.toggle(LockNotificationSettings.UNIFY_AOD,"统一样式","开启后，锁屏与 AOD 上的普通通知按流体云卡片的样子着色——颜色、渐变、浓淡与卡片完全一致（亮屏按本页的样式，AOD 按“AOD”页的样式），不再用单独的跟随开关和强度；没有音乐在播放时，AOD 里的通知和流体云卡片沿用亮屏锁屏卡片的中性玻璃底色和同一套颜色层。亮屏和 AOD 的样式仍然各自设置，互不影响。关闭则通知用各自的跟随设置。默认关闭。","统一样式");
        b.add(FluidCloudAccent.GRADIENT_RANGE,"过渡范围","亮屏锁屏音乐卡和锁屏底部胶囊的渐变，从顶部开始。"+RANGE_FADE,
                NUMERIC,"亮屏样式","%",5,100,1f,null,null,null,null);
        b.choice(FluidCloudStyle.COLOR_STYLE,"颜色填充样式","只决定锁屏音乐卡的颜色怎么铺：纯色是整张卡片一种颜色（原来的样子）；原生渐变是直接改它自带渐变的颜色，替换原本的黑色渐变（“亮屏渐变样式”已开时本来就是渐变）。锁屏底部胶囊同样跟这里。秒表、外卖等其他卡片和普通通知不受这个选择影响。","亮屏样式",
                new String[]{"solid","gradient"},
                new String[]{"纯色","原生渐变"});
        b.toggle(FluidCloudStyle.LOCK_CAPSULE,"锁屏底部胶囊着色","锁屏和 AOD 底部、手电筒与相机按钮之间的小胶囊。默认关闭，保持系统原样；开启后它按“强调色 → 跟随媒体色 → 自定义底色”的顺序着色。","亮屏样式");
        b.toggle(FluidCloudAccent.LOCK_CUSTOM,"亮屏自定义","亮屏锁屏用下面的不透明度和遮罩，代替媒体强调色页的共用设置。媒体取色需要开启“流体云媒体强调色”；关闭总开关时保留这些设置。","亮屏自定义");
        b.add(FluidCloudAccent.LOCK_OPACITY,"亮屏填充不透明度","亮屏锁屏上颜色铺在卡片上的浓淡：100% 完全不透明，0% 完全透明。它只决定透不透明，颜色深浅由“亮屏提亮底色”决定。",NUMERIC,"亮屏自定义","%",0,100,.1f,null,null,null,null);
        b.add(FluidCloudAccent.LOCK_MASK_COLOR,"亮屏遮罩颜色","",COLOR,"亮屏自定义","",0,0,0,null,null,null,null);
        b.add(FluidCloudAccent.LOCK_MASK_OPACITY,"亮屏遮罩不透明度","亮屏锁屏上遮罩的浓淡：100% 完全不透明，0% 完全透明。",NUMERIC,"亮屏自定义","%",0,100,.1f,null,null,null,null);
        b.add(FluidCloudAccent.LOCK_LIGHT,"亮屏提亮底色","锁屏的背景是暗的，半透明的颜色叠上去会发暗发灰，和解锁后（悬浮、下拉）亮玻璃上的样子差很多。提亮让颜色像放在白色上看那样变浅：0 不提亮，100 最浅。它只改颜色深浅，不改透明度：透不透明只由填充不透明度决定。",
                NUMERIC,"亮屏自定义","%",0,100,1f,null,null,null,null);
        b.toggle(FluidCloudStyle.NOTIFICATION_FOLLOW,"通知跟随媒体色","让锁屏与 AOD 的普通通知跟随音乐主色，使用与流体云卡片相同的渐变、透明过渡和高光。需要开启“流体云媒体强调色”，跟随强度在下方调整；开了“统一样式”则按流体云卡片的样子，不再用这个开关。","通知");
        b.number(FluidCloudStyle.NOTIFICATION_FOLLOW_OPACITY,"跟随强度","通知","%",0,100,.1f);
        return new Group(b);
    }
    /** Lock screen in AOD: the same look as the lit lock screen, set on its own; "unify style" on the lit page turns this page off. */
    private static Group lockAod() {
        Builder b = new Builder("lock_aod", "AOD 息屏显示",
                "息屏显示（AOD）上流体云卡片和普通通知的样子，和亮屏锁屏分开设置；初始值与亮屏相同，之后各改各的。“亮屏锁屏”页的“统一样式”只决定普通通知是否按流体云卡片的样子着色，不影响这一页的设置。媒体取色需要开启“流体云媒体强调色”；关闭后，AOD 停止媒体取色，这一页的渐变、透明度、提亮等设置仍保留。普通通知的跟随设置在“亮屏锁屏”页，强度在这一页调。",
                LOCK_SCREEN, FluidCloudAccent.AOD_GRADIENT, true, "AOD 渐变样式", "音乐卡在 AOD 用顶部主色渐变到透明。AOD 的背景是纯黑，渐变的下半截会露出黑底，所以默认关闭，整张卡片同一种颜色。");
        b.toggle(AodNotificationBackground.ENABLED,"AOD 通知白色玻璃背景","仅在 AOD 中，为已适配的普通通知、流体云通知和媒体卡片背景增加一层轻白玻璃质感，保留原生模糊、壁纸颜色与细节、透明度、圆角和过渡，仍可透出壁纸。文字、图标和卡片内容保持系统配色。独立生效，亮屏锁屏不受影响；关闭后恢复原背景。默认关闭。","通知背景");
        b.toggle(FluidCloudAccent.AOD_CUSTOM,"AOD 自定义","AOD 用下面的不透明度和遮罩，代替媒体强调色页的共用设置。媒体取色需要开启“流体云媒体强调色”；关闭总开关时保留这些设置。","AOD 自定义");
        b.add(FluidCloudAccent.AOD_OPACITY,"AOD 填充不透明度","AOD 上颜色铺在卡片上的浓淡：100% 完全不透明，0% 完全透明。它只决定透不透明，颜色深浅由“AOD 提亮底色”决定。",NUMERIC,"AOD 自定义","%",0,100,.1f,null,null,null,null);
        b.add(FluidCloudAccent.AOD_MASK_COLOR,"AOD 遮罩颜色","",COLOR,"AOD 自定义","",0,0,0,null,null,null,null);
        b.add(FluidCloudAccent.AOD_MASK_OPACITY,"AOD 遮罩不透明度","AOD 上遮罩的浓淡：100% 完全不透明，0% 完全透明。",NUMERIC,"AOD 自定义","%",0,100,.1f,null,null,null,null);
        b.add(FluidCloudAccent.AOD_LIGHT,"AOD 提亮底色","让颜色像放在白色上看那样变浅：0 不提亮，100 最浅。它只改颜色深浅，不改透明度。",
                NUMERIC,"AOD 自定义","%",0,100,1f,null,null,null,null);
        b.number(LockNotificationSettings.AOD_NOTIFICATION_FOLLOW_OPACITY,"AOD 通知跟随强度","通知","%",0,100,.1f);
        return new Group(b);
    }
    /** The system privacy dot and a camera-area capsule that can replace it; two independent switches. */
    private static Group privacyDot() {
        Builder b = new Builder("privacy_dot", "隐私小点",
                "相机、麦克风或位置信息正在被使用时，系统会在屏幕角落显示一个小圆点。可以去掉这个小圆点，也可以在摄像头处显示「相机 使用中」这样的胶囊。",
                STATUSBAR, PrivacyIndicatorSettings.HIDE_DOT, true, "去除隐私小点",
                "不再显示屏幕角落的隐私小圆点，以及状态栏里随之出现的橙色或绿色提示动画。下拉面板里的隐私图标和系统的使用记录不受影响。");
        b.toggle(PrivacyIndicatorSettings.CAPSULE, "隐私小点上流体云",
                "用系统流体云在摄像头处显示使用中胶囊，左边是图标和名称，右边是「使用中」，点开可查看使用记录；开启时会同时打开「去除隐私小点」，之后可以单独关掉。", "胶囊");
        b.toggle(PrivacyIndicatorSettings.CAMERA, "相机", "相机使用中时显示胶囊。", "显示类型");
        b.toggle(PrivacyIndicatorSettings.MICROPHONE, "麦克风", "麦克风使用中时显示胶囊。", "显示类型");
        b.toggle(PrivacyIndicatorSettings.LOCATION, "位置信息", "位置信息使用中时显示胶囊。很多应用会在后台频繁定位，开启后胶囊可能经常出现。", "显示类型");
        b.add(PrivacyIndicatorSettings.DURATION, "显示时长", "开始使用后胶囊最多显示多久，最短 1 秒；拖到最右是一直显示到停止使用。停止使用时胶囊会立即收起。", NUMERIC, "显示时长", "秒",
                PrivacyIndicatorSettings.MIN_DURATION, PrivacyIndicatorSettings.MAX_DURATION, 1f, null, null, null, null);
        return new Group(b);
    }
    /** Camera-area appearance plus an independent persistence switch shared by its tabs. */
    private static Group fluidCapsule() {
        Builder b=builder("fluid_cloud_capsule", "摄像头区域流体云",
                "摄像头周围的流体云胶囊（音乐、计时、来电等收起或展开时出现的黑色胶囊）。分三页：底色（颜色、渐变、模糊）、边框（边框、跑马灯、向内渐隐）、形状与内容（圆角、专辑封面、出现回弹、实时频谱）。外观总开关关闭时恢复原生外观；常驻流体云独立控制自动收起。",
                OTHER, FluidCloudCapsule.MASTER);
        b.toggle(CapsulePersistence.ENABLED,"常驻流体云","开启后，摄像头区域的流体云不会因停留超时自动收起，仍可手动收起；音乐或任务结束时由系统正常清理。独立于本页外观总开关。","显示规则");
        b.toggle(FluidCloudCapsule.FILL_ENABLED,"自定义底色","胶囊使用下面的颜色与透明度，代替原生黑色。底色较浅时胶囊里的白色文字会不易看清。","底色");
        b.add(FluidCloudCapsule.FILL_COLOR,"底色","",COLOR,"底色","",0,0,0,null,null,null,null);
        b.add(FluidCloudCapsule.FILL_OPACITY,"底色不透明度","100% 完全不透明，0% 完全透明。",NUMERIC,"底色","%",0,100,.1f,null,null,null,null);
        b.toggle(FluidCloudCapsule.FOLLOW_MEDIA,"跟随媒体强调色","有媒体时胶囊使用当前媒体色，并叠加“流体云媒体强调色”页的遮罩，浓淡用本页的“底色不透明度”；没有媒体时用自定义底色或原生黑色。颜色变化会在约 0.6 秒内渐变过去。","底色");
        b.toggle(FluidCloudCapsule.GRADIENT_ENABLED,"渐变底色","底色从上面的“底色”（或媒体色）渐变到下面的渐变颜色；需要先开启自定义底色或跟随媒体强调色。","渐变");
        b.choice(FluidCloudCapsule.GRADIENT_MODE,"渐变方向","从上到下、从下到上，或从胶囊中间向四周。","渐变",
                new String[]{FluidCloudCapsule.TOP_BOTTOM,FluidCloudCapsule.BOTTOM_TOP,FluidCloudCapsule.CENTER},
                new String[]{"从上到下","从下到上","中间向四周"});
        b.add(FluidCloudCapsule.GRADIENT_COLOR,"渐变颜色","",COLOR,"渐变","",0,0,0,null,null,null,null);
        b.add(FluidCloudCapsule.GRADIENT_RANGE,"过渡范围",RANGE_BETWEEN,
                NUMERIC,"渐变","%",5,100,1f,null,null,null,null);
        b.toggle(FluidCloudCapsule.BLUR_ENABLED,"背景模糊","胶囊变成毛玻璃：模糊它后面的画面，再盖一层“遮罩颜色”。开启后底色由遮罩色决定，自定义底色、渐变、跟随媒体强调色这几项会变灰、不再起作用；边框、跑马灯、圆角照常。依赖系统的窗口背景模糊，系统不支持或关闭了模糊时保持原来的底色。","模糊");
        b.number(FluidCloudCapsule.BLUR_RADIUS,"模糊强度","模糊","dp",0,FluidCloudCapsule.MAX_BLUR_RADIUS,.5f);
        b.add(FluidCloudCapsule.BLUR_MASK_COLOR,"遮罩颜色","盖在模糊上的一层颜色，颜色自带的透明度决定浓淡；背景模糊开启后，上面的自定义底色、渐变和跟随媒体色不再起作用。",COLOR,"模糊","",0,0,0,null,null,null,null);
        b.toggle(FluidCloudCapsule.BORDER_ENABLED,"自定义边框","胶囊始终显示这条边框（原生只在收起时有一圈很淡的边）。","边框");
        b.add(FluidCloudCapsule.BORDER_COLOR,"边框颜色","",COLOR,"边框","",0,0,0,null,null,null,null);
        b.number(FluidCloudCapsule.BORDER_WIDTH,"边框粗细","边框","dp",0,FluidCloudCapsule.MAX_BORDER_WIDTH,.1f);
        b.toggle(FluidCloudCapsule.MARQUEE_ENABLED,"边框跑马灯","边框变成一圈绕着胶囊转动的彩色光，代替上面的普通边框。开了自定义边框时用它的粗细，否则用系统默认的 1.3 dp。只在胶囊显示时转动，约每秒 30 帧。","跑马灯");
        b.choice(FluidCloudCapsule.MARQUEE_PRESET,"预设","自带颜色的预设有二十个；“单色流光”用颜色 1 拖出一道渐隐的尾巴；“自定义”用下面的颜色（2 到 6 个）。","跑马灯",
                FluidCloudCapsule.MARQUEE_PRESETS,
                FluidCloudCapsule.MARQUEE_PRESET_LABELS);
        b.number(FluidCloudCapsule.MARQUEE_COLOR_COUNT,"自定义颜色数量","跑马灯","个",FluidCloudCapsule.MIN_MARQUEE_COLORS,FluidCloudCapsule.MAX_MARQUEE_COLORS,1f);
        b.add(FluidCloudCapsule.MARQUEE_COLOR_1,"颜色 1","",COLOR,"跑马灯","",0,0,0,null,null,null,null);
        b.add(FluidCloudCapsule.MARQUEE_COLOR_2,"颜色 2","",COLOR,"跑马灯","",0,0,0,null,null,null,null);
        b.add(FluidCloudCapsule.MARQUEE_COLOR_3,"颜色 3","",COLOR,"跑马灯","",0,0,0,null,null,null,null);
        b.add(FluidCloudCapsule.MARQUEE_COLOR_4,"颜色 4","",COLOR,"跑马灯","",0,0,0,null,null,null,null);
        b.add(FluidCloudCapsule.MARQUEE_COLOR_5,"颜色 5","",COLOR,"跑马灯","",0,0,0,null,null,null,null);
        b.add(FluidCloudCapsule.MARQUEE_COLOR_6,"颜色 6","",COLOR,"跑马灯","",0,0,0,null,null,null,null);
        b.add(FluidCloudCapsule.MARQUEE_OPACITY,"不透明度","100% 完全不透明，0% 完全透明。",NUMERIC,"跑马灯","%",0,100,.1f,null,null,null,null);
        b.number(FluidCloudCapsule.MARQUEE_PERIOD,"转一圈用时","跑马灯","秒",FluidCloudCapsule.MIN_MARQUEE_PERIOD,FluidCloudCapsule.MAX_MARQUEE_PERIOD,.1f);
        b.toggle(FluidCloudCapsule.MARQUEE_REVERSE,"反方向转动","默认顺时针；开启后逆时针。","跑马灯");
        b.toggle(FluidCloudCapsule.GLOW_ENABLED,"向内渐隐","光环再向胶囊里面连续地淡出，像一圈柔和的光晕，颜色跟着跑马灯一起转。","向内渐隐");
        b.add(FluidCloudCapsule.GLOW_DEPTH,"过渡范围","光晕从边框开始向内。"+RANGE_DISTANCE,
                NUMERIC,"向内渐隐","dp",FluidCloudCapsule.MIN_GLOW_DEPTH,FluidCloudCapsule.MAX_GLOW_DEPTH,.5f,null,null,null,null);
        b.toggle(FluidCloudCapsule.RADIUS_ENABLED,"自定义圆角","展开的胶囊，底色、边框和跑马灯使用同一个圆角半径。胶囊约 30dp 高，半径 15dp 及以上都是完整的胶囊形（原生也是），调小才会变成更方的圆角。收起时胶囊围着摄像头挖孔，始终保持原生形状，展开过程中再平滑过渡到这里的半径。","形状");
        b.number(FluidCloudCapsule.RADIUS,"圆角半径","形状","dp",0,FluidCloudCapsule.MAX_RADIUS,.5f);
        b.toggle(FluidCloudCapsule.COVER_RADIUS_ENABLED,"自定义封面圆角","音乐胶囊左边的专辑封面原生是圆形；开启后展开的胶囊用下面的圆角。收起时封面正好在摄像头挖孔处，始终保持原生圆形，展开过程中再平滑变过去。只作用于音乐胶囊，计时、来电等胶囊的图标不变；关闭即恢复圆形。","专辑封面");
        b.add(FluidCloudCapsule.COVER_RADIUS,"封面圆角","0 为直角方形，10 dp 约为原生的圆形。",NUMERIC,"专辑封面","dp",0,FluidCloudCapsule.MAX_COVER_RADIUS,.5f,null,null,null,null);
        b.toggle(FluidCloudCapsule.COVER_SIZE_ENABLED,"调整音乐封面大小","只改变展开时的音乐封面；胶囊整体尺寸、文字和其他图标不变，收起时恢复系统大小。","专辑封面");
        b.add(FluidCloudCapsule.COVER_SCALE,"音乐封面大小","100% 为系统大小，展开和收起时跟随原生动画。",NUMERIC,"专辑封面","%",FluidCloudCapsule.MIN_COVER_SCALE,FluidCloudCapsule.MAX_COVER_SCALE,1f,null,null,null,null);
        b.toggle(CapsuleEffects.BOUNCE_ENABLED,"自定义出现回弹","系统原生效果：胶囊出现时会先多撑开约 16dp 宽、3dp 高，再回落到正常大小。开启后用下面的幅度；关闭即系统原样。下一次胶囊出现时生效，不改变胶囊在摄像头处的位置。","出现回弹");
        b.add(CapsuleEffects.BOUNCE,"回弹幅度","0% 没有回弹，100% 为系统原样，最多 200%。",NUMERIC,"出现回弹","%",0,CapsuleEffects.MAX_BOUNCE,1f,null,null,null,null);
        b.toggle(FluidCloudCapsule.SPECTRUM_ENABLED,"实时频谱","胶囊里的音乐频谱不再播放固定动画，改为跟随正在播放的音乐实时跳动。只在胶囊显示、音乐播放时工作，其余时间不占用电量；依赖系统的音频可视化，不支持时保留原动画。","实时频谱");
        b.add(FluidCloudCapsule.SPECTRUM_SENSITIVITY,"频谱灵敏度","可以按喜好自己调：0 时条很安静，只有明显的鼓点才会升起，最高到三分之一多一点；100 时条又高又活跃，最高处能到顶。条的高低始终跟着每个频段自己最近的响度范围走，所以调到最高也不会一直顶满；音量很小时整体会矮一些。",
                NUMERIC,"实时频谱","%",0,100,1f,null,null,null,null);
        b.toggle(FluidCloudCapsule.SPECTRUM_FOLLOW,"频谱跟随强调色","频谱条用当前媒体色（正在播放的音乐封面的主色，太暗时会提亮到看得清；胶囊底色正好是同一个颜色时改用深色或白色），不再用下面的固定颜色。暂时取不到媒体色时仍用下面的颜色。","实时频谱");
        b.add(FluidCloudCapsule.SPECTRUM_COLOR,"频谱颜色","",COLOR,"实时频谱","",0,0,0,null,null,null,null);
        return new Group(b);
    }
    private static Group fluidStyle() {
        Builder b=builder("fluid_cloud_style", "流体云样式",
                "通知类跟随系统样式，以及流体云在悬浮展开、锁屏与 AOD 的底色。适配范围按可识别的业务分类选择，只控制本页样式；未识别的卡片归入“其他与未识别”。强调色和跟随媒体色独立设置，优先于这里的底色。",
                OTHER, FluidCloudStyle.MASTER);
        b.toggle(FluidCloudStyle.FOLLOW_NOTIFICATION,"通知类跟随系统样式","通知栏跟随通知卡片，悬浮通知跟随悬浮通知材质，锁屏保留系统小组件材质。","通知类");
        b.toggle(FluidCloudStyle.SCOPE_FLOAT,"悬浮展开","桌面悬浮的流体云卡片。","作用场景");
        b.toggle(FluidCloudStyle.SCOPE_LOCK,"锁屏与 AOD","锁屏和 AOD 保持同一种颜色，进出 AOD 不跳变。","作用场景");
        b.toggle(FluidCloudStyle.DESKTOP_BACKGROUND,"自定义底色","只改颜色与透明度，保留原生模糊和动效。","底色");
        b.choice(FluidCloudStyle.BACKGROUND_MODE,"背景模式","原生渐变保留原生模糊、材质、形状和动画，可调整过渡范围；自定义纯色背景使用所选颜色。媒体强调色与跟随媒体色优先。","底色",
                new String[]{"native","custom"},
                new String[]{"原生渐变","自定义纯色背景"});
        b.add(FluidCloudStyle.BACKGROUND_COLOR,"自定义颜色","",COLOR,"底色","",0,0,0,null,null,null,null);
        b.add(FluidCloudStyle.BACKGROUND_OPACITY,"填充不透明度","100% 完全不透明，0% 完全透明。",NUMERIC,"底色","%",0,100,.1f,null,null,null,null);
        b.add(FluidCloudStyle.BACKGROUND_GRADIENT_START,"渐变起点","从卡片顶部往下多远才开始变化，之前保持起点色。0% 即从最顶部开始。只作用于悬浮展开卡片的原生渐变；锁屏与 AOD 使用各自页面的外观设置。",NUMERIC,"渐变","%",0,98,.1f,null,null,null,null);
        b.add(FluidCloudStyle.BACKGROUND_GRADIENT_RANGE,"渐变终点","变化到多远结束，之后保持终点色。100% 保留系统渐变的长度，调小则更短、更快地到达终点色。",NUMERIC,"渐变","%",1,100,.1f,null,null,null,null);
        b.add(FluidCloudStyle.BACKGROUND_GRADIENT_MID,"渐变中线","起点与终点之间，颜色变化到一半的位置。50% 为匀速；调小更早变化，调大更晚变化。",NUMERIC,"渐变","%",1,99,.1f,null,null,null,null);
        b.add(FluidCloudStyle.BACKGROUND_GRADIENT_STRENGTH,"渐变强度","起点色与终点色的差距：100% 为系统原样；调低渐变变平，0% 整张都是起点色，没有渐变；调高对比更强，终点更淡。",NUMERIC,"渐变","%",0,200,.1f,null,null,null,null);
        b.add(FluidCloudStyle.BACKGROUND_GRADIENT_OPACITY,"渐变透明度","只让渐变这一层变透明，底色与模糊不受影响；100% 为系统原样。与上面的“填充不透明度”不同，后者作用于整张底色。",NUMERIC,"渐变","%",0,100,.1f,null,null,null,null);
        b.toggle(FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT,"音乐媒体保留高光","音乐卡片保留原生高光；需先开启 C17 高光去除。","高光");
        String[] scopeIds={"media","ride_hailing","food_delivery","parcel_pickup","food_pickup","timer","alarm","navigation","travel","battery","earphones","smart_suggestions","other"};
        String[] scopeTitles={"音乐媒体","打车","外卖","取件码与快递","取餐码与到店自提","倒计时","闹钟","导航","交通出行","低电量与充电","耳机","情景建议","其他与未识别"};
        for(int index=0;index<scopeIds.length;index++)b.toggle("fluid_cloud_scope_"+scopeIds[index]+"_enabled",scopeTitles[index],
                "other".equals(scopeIds[index])?"其他任务和暂未识别类别的流体云。关闭后停用本页对这些卡片的样式调整。"
                        :"smart_suggestions".equals(scopeIds[index])?"到家或到公司开启 Wi-Fi、观影静音、关闭手电筒等系统建议。关闭后停用本页对这些卡片的样式调整。"
                        :"battery".equals(scopeIds[index])?"低电量省电和充电流体云提醒。关闭后停用本页对这些卡片的样式调整。"
                        :"earphones".equals(scopeIds[index])?"耳机连接流体云。关闭后停用本页对这类卡片的样式调整。"
                        :"关闭后停用本页对这类流体云的样式调整；未识别的卡片归入“其他与未识别”。","适配范围");
        return new Group(b);
    }
    private static Group wholeStack() {
        Builder b=builder("notification_stack", "堆叠通知", "使用系统原生堆叠，随列表滑动自动展开和缩放。竖屏需开启竖屏大时钟；横屏需开启横屏大时钟和“横屏也生效”。关闭对应大时钟后，该方向自动恢复原生通知，保留已保存参数。展开应用分组仍使用系统原生点击操作。", NOTIFICATION, NotificationBigClockSettings.STACK_ENABLED);
        b.toggle(FeatureOptions.STACK_LANDSCAPE_ENABLED,"横屏也生效","开启堆叠通知后，还需开启横屏大时钟，才允许横屏使用同样的堆叠。默认关闭。","显示规则");
        b.toggle(NotificationMiniStack.AUTO_HIDE_IN_STACK,"自动隐藏小堆叠","同应用小堆叠被整列堆叠覆盖时隐藏后面的层，展开后恢复。需同时开启堆叠通知与同应用小堆叠；关闭任一种堆叠时暂停生效，保留此项选择。","显示规则");
        b.number(NotificationBigClockSettings.VISIBLE_COUNT,"完整通知数量","显示规则","个",1,5,1);
        return new Group(b);
    }
    /** Same-app mini stack: an independent page, not tied to either big clock, the whole-list stack or the fluid cloud. */
    private static Group miniStack() {
        Builder b = new Builder("notification_mini_stack", "同应用小堆叠", NotificationMiniStackSource.SHORT
                + "。系统已把同一应用的通知收成一组时，收起状态显示完整的第一条，后面最多露出两层真实通知；点按第一条展开整组，"
                + "展开、收起、侧滑和清除仍由系统处理。默认关闭；开启或关闭后，在主页“重启系统界面”后完整生效。",
                NOTIFICATION, NotificationMiniStack.ENABLED, true, NotificationMiniStackSource.FEATURE,
                "只作用于系统原生通知组（同一应用、至少两条）；单条或未分组的通知保持原样。首次开启会先显示功能来源声明。");
        b.toggle(NotificationMiniStack.HIDE_BADGE, "隐藏原生角标数量", "隐藏小堆叠的数量及收起动画残留；关闭后恢复数量。展开的通知组保持系统行为。", "显示规则");
        return new Group(b);
    }
    private static Group nativeStatusIcons() {
        Builder b=builder("status_hint_icons", "系统提示图标", "独立调整蓝牙、定位、闹钟等原生提示图标的数量、间距与保留优先级。每个图标的左右位置和先后在状态栏排序页统一设置。通知图标、Wi-Fi、蜂窝信号、电池和网速不由本页控制。", STATUSBAR, NativeStatusIcons.MASTER);
        b.number(NativeStatusIcons.Y,"垂直位置","位置","dp",-16,16,.1f);
        layoutMargins(b, StatusBarLayoutSettings.SYSTEM, "左右间距");
        b.number(NativeStatusIcons.MAX,"最多显示数量","显示规则","个",0,14,1);
        b.add(NativeStatusIcons.SPACING,"附加间距","0 保留系统间距，正值放宽。只移动提示图标，不改变网络和电池位置；间距或位移过大可能超出屏幕。",NUMERIC,"位置","dp",0,12,.1f,null,null,null,null);
        b.text(NativeStatusIcons.PRIORITY,"显示优先级","长按拖拽中文图标名称排序，靠前的优先保留；只显示系统当前允许显示的图标。","显示规则",
                new String[]{"","bluetooth,location,alarm_clock","location,microphone,camera,bluetooth","zen,alarm_clock,bluetooth"},
                new String[]{"跟随系统","蓝牙优先","定位与隐私优先","勿扰与闹钟优先"});
        return new Group(b);
    }
    private static Group media() {
        Builder b = builder("qs_media", "音乐卡片", "封面背景、虚化与小封面发光，按浅色和深色界面分别设置。换封面立即显示新封面的取样纯色，停止更新约 1 秒后后台计算模糊背景，再柔和淡入。", CONTROL_CENTER, QsMediaAppearance.MASTER);
        b.toggle(QsMediaAppearance.BACKGROUND, "使用封面作为卡片背景", "居中裁切，保留原生标题与播放按钮", "显示效果");
        b.toggle(QsMediaAppearance.GLOW, "小封面外发光", "保留系统玻璃与高光", "显示效果");
        b.number(QsMediaAppearance.TRANSITION, "封面过渡时间", "过渡", "ms", 0, QsMediaAppearance.MAX_TRANSITION_MS, 10f);
        b.number(QsMediaAppearance.SETTLE, "换封面后的等待时间", "过渡", "ms", 0, QsMediaAppearance.MAX_SETTLE_MS, 50f);
        for (String scene : new String[]{"light", "dark"}) {
            String s = "light".equals(scene) ? "浅色界面" : "深色界面", p = QsMediaAppearance.prefix(scene);
            b.number(p + "_background_opacity", "背景透明度", s + " · 背景", "%", 0, 100, .01f);
            b.number(p + "_background_blur", "背景模糊度", s + " · 背景", "dp", 0, 48, .01f);
            b.toggle(p + "_background_solid_enabled", "背景改用封面主色", "不显示封面图片，只铺封面取样的一块纯色；仍受背景透明度控制", s + " · 背景");
            b.toggle(p + "_background_custom_enabled", "背景改用自定义颜色", "优先于封面主色；没有封面时保持系统原样", s + " · 背景");
            b.number(p + "_background_tint_opacity", "背景叠加色强度", s + " · 背景", "%", 0, 100, .01f);
            b.toggle(p + "_glow_inverse_enabled", "发光使用封面反色", "关闭后使用自定义发光颜色", s + " · 外发光");
            b.number(p + "_glow_opacity", "发光透明度", s + " · 外发光", "%", 0, 100, .01f);
            b.number(p + "_glow_radius", "发光模糊半径", s + " · 外发光", "dp", 0, 48, .01f);
            b.number(p + "_glow_spread", "发光扩散距离", s + " · 外发光", "dp", 0, 24, .01f);
        }
        b.colorPair("qs_media_light_glow_color", "qs_media_dark_glow_color", "自定义发光颜色", "发光颜色");
        b.colorPair("qs_media_light_background_color", "qs_media_dark_background_color", "自定义背景颜色", "背景颜色");
        b.colorPair("qs_media_light_background_tint_color", "qs_media_dark_background_tint_color", "背景叠加色", "背景叠加色");
        return new Group(b);
    }

    private static Group highlightRemoval() {
        Builder b = builder("c17_highlight_removal", "C17 高光去除",
                "去除通知与控制中心原生材质的边缘高光、内缘光泽和聚光，保留模糊、圆角与原生交互，使表面更接近亚克力。可分别设置浅色和深色底色，默认关闭。",
                OTHER, C17HighlightRemoval.ENABLED);
        b.toggle(C17HighlightRemoval.NOTIFICATION_ENABLED, "通知栏去高光", "只处理通知页和通知卡片的高光，关闭时恢复原生。", "作用范围");
        b.toggle(C17HighlightRemoval.HEADS_UP_ENABLED, "消息通知去高光", "去除屏幕顶部悬浮横幅通知的高光与边缘效果，保留模糊、内容和点击操作。与下拉通知栏范围独立。", "作用范围");
        b.toggle(C17HighlightRemoval.CONTROL_ENABLED, "状态栏面板去高光", "处理右侧控制中心、磁贴和音乐卡片的高光，关闭时恢复原生。", "作用范围");
        b.toggle(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED, "统一通知卡片颜色", "可单独使用。使用下方浅色或深色底色，保留原生模糊和内容。会增加一次背景合成，请按需使用。", "通知颜色");
        b.toggle(C17HighlightRemoval.BACKGROUND_ENABLED, "自定义亚克力底色",
                "为普通材质设置统一底色；磁贴自定义颜色与音乐封面背景保持各自设置。关闭后只去高光。", "底色");
        b.colorPair(C17HighlightRemoval.LIGHT_BACKGROUND, C17HighlightRemoval.DARK_BACKGROUND,
                "亚克力底色", "颜色与透明度");
        return new Group(b);
    }

    public static List<Group> groups() { return GROUPS; }
    public static List<Group> groups(String category) {
        List<Group> result = new ArrayList<>();
        for (NavigationSection section : navigationSections(category)) result.addAll(section.groups);
        return Collections.unmodifiableList(result);
    }
    public static List<NavigationSection> navigationSections(String category) {
        // Each page appears once, where its effect is seen; network entries follow their on-screen order.
        // The per-surface carrier text and the shared pull-down clock are the only deliberate repeats.
        if (STATUSBAR.equals(category)) return Arrays.asList(
                new NavigationSection("排列顺序", StatusBarLayoutSettings.GROUP),
                new NavigationSection("时间与电量", "clock", "battery"),
                new NavigationSection("网络信号", "speed", "data", "network"),
                new NavigationSection("通知图标", "notification_icons", "notification_icon_overrides"),
                new NavigationSection("系统图标", "status_hint_icons", "privacy_dot"));
        if (CONTROL_CENTER.equals(category)) return Arrays.asList(
                new NavigationSection("磁贴与滑块", "tile_2x1_fill", "tile_corners", "tile_icon_size", "qs_appearance"),
                new NavigationSection("音乐与卡片", "qs_media"),
                new NavigationSection("WiFi", QsWifiAlias.GROUP),
                new NavigationSection("顶部时间与文字", "shade_clock", "carrier"),
                new NavigationSection("顶部按钮", QsEntrances.GROUP));
        if (NOTIFICATION.equals(category)) return Arrays.asList(
                new NavigationSection("时钟与日期", "notification_big_clock", "notification_big_clock_landscape", "shade_clock"),
                new NavigationSection("通知堆叠", "notification_stack", "notification_mini_stack"),
                new NavigationSection("清除按钮", "notification_clear", "notification_clear_landscape"),
                new NavigationSection("界面文字", "carrier", "classic_text"));
        if (OTHER.equals(category)) return Arrays.asList(
                new NavigationSection("图标", "status_icon_library"),
                new NavigationSection("字体", "font"),
                new NavigationSection("流体云", "fluid_cloud_accent", "fluid_cloud_style", "fluid_cloud_capsule"),
                new NavigationSection("下拉面板壁纸", "shade_wallpaper_portrait", "shade_wallpaper_landscape"),
                new NavigationSection("面板外观", "c17_highlight_removal"));
        if (LOCK_SCREEN.equals(category)) return Arrays.asList(new NavigationSection("日期与文字", "lockscreen_date", "carrier"),
                new NavigationSection("提示图标", "lockscreen_lock_icon"),
                new NavigationSection("壁纸与模糊", "lockscreen_status_blur"),
                new NavigationSection("亮屏与 AOD", "lock_awake", "lock_aod"));
        return Collections.emptyList();
    }
    public static List<DetailPage> detailPages(Group group) {
        if (group == null) return Collections.emptyList();
        LinkedHashMap<String, List<Item>> buckets = new LinkedHashMap<>();
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        if (group.id.startsWith("notification_big_clock")) {
            labels.put("time", "时间"); labels.put("look", "外观"); labels.put("date", "日期");
            labels.put("widgets", "左右组件");
            labels.put("motion", "动效与通知"); labels.put("footer", "底部");
        } else if ("carrier".equals(group.id)) {
            labels.put("notification", "通知栏"); labels.put("control", "控制中心"); labels.put("lockscreen", "锁屏");
        } else if ("classic_text".equals(group.id)) {
            labels.put("carrier", "运营商"); labels.put("clock", "时间与日期");
        } else if ("network".equals(group.id)) {
            labels.put("common", "共用位置"); labels.put("wifi", "Wi-Fi");
            labels.put("text", "制式文字"); labels.put("badge", "角标与箭头");
        } else if ("battery".equals(group.id)) {
            labels.put("style", "样式"); labels.put("text", "内文字"); labels.put("layout", "布局"); labels.put("colors", "颜色");
        } else if ("qs_media".equals(group.id) || "qs_appearance".equals(group.id)) {
            labels.put("common", "常用"); labels.put("light", "浅色界面"); labels.put("dark", "深色界面");
        } else if ("clock".equals(group.id)) {
            labels.put("default", "默认时钟"); labels.put("dual", "双排时钟"); labels.put("common", "通用设置");
        } else if ("fluid_cloud_capsule".equals(group.id)) {
            labels.put("fill","底色"); labels.put("border","边框"); labels.put("content","形状与内容");
        } else if ("fluid_cloud_style".equals(group.id)) {
            labels.put("notification","通知类");labels.put("appearance","流体云外观");labels.put("scope","适配范围");
        } else if (group.id.startsWith("shade_wallpaper")) {
            labels.put("notification", "通知中心"); labels.put("control", "控制中心"); labels.put("classic", "经典下拉");
        } else labels.put("all", "全部");
        for (String id : labels.keySet()) buckets.put(id, new ArrayList<>());
        Set<String> shared = new HashSet<>();
        for (Item item : commonDetailItems(group)) shared.add(item.key);
        for (Item item : group.items) if (!item.master && !shared.contains(item.key))
            buckets.get(detailPageId(group, item)).add(item);
        List<DetailPage> pages = new ArrayList<>();
        boolean wallpaper = group.id.startsWith("shade_wallpaper");
        for (List<Item> items : buckets.values()) items.sort((left, right) -> wallpaper
                ? Integer.compare(wallpaperSectionPriority(left.section), wallpaperSectionPriority(right.section))
                : "network".equals(group.id)
                ? Integer.compare(networkSectionPriority(left.section),networkSectionPriority(right.section))
                : Integer.compare(sectionPriority(left.section), sectionPriority(right.section)));
        for (Map.Entry<String, List<Item>> entry : buckets.entrySet()) if (!entry.getValue().isEmpty())
            pages.add(new DetailPage(entry.getKey(), labels.get(entry.getKey()), entry.getValue()));
        return Collections.unmodifiableList(pages);
    }
    /** A search result may temporarily reveal another saved panel scene without changing the system mode. */
    public static List<DetailPage> editorDetailPages(Group group, String panelMode, String requestedKey) {
        List<DetailPage> pages = detailPages(group);
        if (group == null || !group.id.startsWith("shade_wallpaper")) return pages;
        List<DetailPage> result = new ArrayList<>();
        for (DetailPage page : pages) {
            boolean requested = false;
            for (Item item : page.items) if (item.key.equals(requestedKey)) { requested = true; break; }
            if (requested || "classic".equals(page.id) == PanelMode.CLASSIC.equals(panelMode)) result.add(page);
        }
        return Collections.unmodifiableList(result);
    }
    public static String detailModeReason(Group group, DetailPage page, String panelMode) {
        if (group == null || page == null || !group.id.startsWith("shade_wallpaper")) return "";
        if (!PanelMode.CLASSIC.equals(panelMode) && !PanelMode.SEPARATE.equals(panelMode))
            return "系统面板模式尚未确认；这里显示已保存的配置，按实际模式使用";
        if ("classic".equals(page.id) == PanelMode.CLASSIC.equals(panelMode)) return "";
        return PanelMode.CLASSIC.equals(panelMode)
                ? "当前为经典模式；独立通知中心与控制中心壁纸暂停，切回分离模式后按保存值恢复"
                : "当前为分离模式；经典下拉壁纸暂停，切回经典模式后按保存值恢复";
    }
    /** The old shared carrier switch remains portable but has no editor; three scene switches replace it. */
    public static List<Item> searchItems(Group group) {
        if (group == null) return Collections.emptyList();
        if (!"carrier".equals(group.id)) return group.items;
        List<Item> result = new ArrayList<>();
        for (Item item : group.items) if (!item.key.equals(group.masterKey)) result.add(item);
        return Collections.unmodifiableList(result);
    }
    public static List<Item> commonDetailItems(Group group) {
        if (group != null && "carrier".equals(group.id)) {
            String token = StatusBarLayoutSettings.CARRIER;
            List<Item> shared = new ArrayList<>();
            Item left = ITEM_BY_KEY.get(StatusBarLayoutSettings.key(token, "margin_left"));
            Item right = ITEM_BY_KEY.get(StatusBarLayoutSettings.key(token, "margin_right"));
            if (left != null) shared.add(left);
            if (right != null) shared.add(right);
            return Collections.unmodifiableList(shared);
        }
        if (group != null && "fluid_cloud_capsule".equals(group.id)) {
            Item persistent = ITEM_BY_KEY.get(CapsulePersistence.ENABLED);
            return persistent == null ? Collections.emptyList() : Collections.singletonList(persistent);
        }
        if (group != null && group.id.startsWith("shade_wallpaper")) {
            boolean landscape = "shade_wallpaper_landscape".equals(group.id);
            List<Item> shared = new ArrayList<>();
            Item pull = ITEM_BY_KEY.get(ShadeWallpaperSettings.blurWithPullKey(landscape)), slide = ITEM_BY_KEY.get(ShadeWallpaperSettings.swipeSlideKey(landscape));
            if (pull != null) shared.add(pull);
            if (slide != null) shared.add(slide);
            return Collections.unmodifiableList(shared);
        }
        return Collections.emptyList();
    }
    /** One panel's tab reads top to bottom: the picture, how it is shown, the clock zone. */
    private static int wallpaperSectionPriority(String section) {
        if ("壁纸图片".equals(section)) return 0;
        if ("显示".equals(section)) return 10;
        if ("大时钟区域".equals(section)) return 20;
        return 50;
    }
    private static int networkSectionPriority(String section) {
        if ("位置模式".equals(section)||"显示方式".equals(section)||"显示".equals(section)||"角标自定义".equals(section)) return -1;
        if (section.startsWith("角标1")) return 100+(section.endsWith(" · 开关")?0:sectionPriority(section));
        if (section.startsWith("角标2")) return 200+(section.endsWith(" · 开关")?0:sectionPriority(section));
        if ("数据小箭头".equals(section)) return 300;
        return sectionPriority(section);
    }
    private static int sectionPriority(String section) {
        String title = section.contains(" · ") ? section.substring(section.indexOf(" · ") + 3) : section;
        if ("背景透出与遮罩".equals(title)) return -10;
        if ("主时间立体边缘".equals(title)) return 0;
        if ("日期".equals(title) || "底部内容".equals(title)) return 0;
        if ("日期位置".equals(title) || "底部位置".equals(title)) return 10;
        if ("日期背景样式".equals(title) || "底部背景样式".equals(title)) return 30;
        if ("初始位置".equals(title)) return -1;
        if ("锁屏质感".equals(title)) return 5;
        if ("液态玻璃".equals(title)) return 15;
        if ("玻璃边框".equals(title)) return 25;
        if ("内容与字体".equals(title)) return 0;
        if ("大小与字重".equals(title)) return 20;
        if ("位置与对齐".equals(title)) return 30;
        if (Arrays.asList("显示样式", "时间格式", "字体来源", "显示效果", "信号布局", "电池样式", "通知页", "控制中心", "锁屏", "底色", "内容").contains(title)) return 0;
        if ("位置".equals(title)) return 10;
        if ("左右间距".equals(title)) return 11;
        if ("大小".equals(title) || "字体高度".equals(title)) return 20;
        if ("文字样式".equals(title) || "文字与间距".equals(title)) return 30;
        if (title.contains("颜色") || "颜色与透明度".equals(title)) return 50;
        if ("收起样式".equals(title) || "收起位置".equals(title)) return 60;
        return 40;
    }
    /** The panel a wallpaper setting belongs to: its tab. */
    private static String wallpaperPanel(String key) {
        if (key.contains("_control_")) return "control";
        if (key.contains("_classic_")) return "classic";
        return "notification";
    }
    private static String detailPageId(Group group, Item item) {
        if (group.id.startsWith("shade_wallpaper")) return wallpaperPanel(item.key);
        String section = item.section;
        if("fluid_cloud_style".equals(group.id))return FluidCloudScope.isScopeKey(item.key)?"scope"
                :FluidCloudStyle.FOLLOW_NOTIFICATION.equals(item.key)?"notification":"appearance";
        if("fluid_cloud_capsule".equals(group.id))
            return Arrays.asList("底色","渐变","模糊").contains(section)?"fill":Arrays.asList("边框","跑马灯","向内渐隐").contains(section)?"border":"content";
        if ("clock".equals(group.id) && (ClockLayoutSettings.digitKey("clock").equals(item.key)
                || ClockLayoutSettings.digitSpacingKey("clock").equals(item.key) || ClockLayoutSettings.colonKey("clock").equals(item.key)
                || StatusBarLayoutSettings.key(StatusBarLayoutSettings.CLOCK,"margin_left").equals(item.key)
                || StatusBarLayoutSettings.key(StatusBarLayoutSettings.CLOCK,"margin_right").equals(item.key))) return "common";
        if ("clock".equals(group.id)) return item.key.startsWith("clock_dual_") || ClockLayoutSettings.MARGIN_ENABLED.equals(item.key)
                || ClockLayoutSettings.MARGIN_START.equals(item.key) || ClockLayoutSettings.MARGIN_END.equals(item.key) ? "dual" : "default";
        if (group.id.startsWith("notification_big_clock")) {
            if (bigClockWidget(item.key)) return "widgets";
            if (section.startsWith("日期")) return "date";
            if (section.startsWith("通知") || "入场效果".equals(section) || "回弹效果".equals(section)) return "motion";
            if (section.startsWith("底部")) return "footer";
            if (section.startsWith("玻璃") || "锁屏质感".equals(section) || "液态玻璃".equals(section) || "文字颜色".equals(section)
                    || "背景透出与遮罩".equals(section) || "主时间立体边缘".equals(section)) return "look";
            return "time";
        }
        if ("carrier".equals(group.id)) {
            if (item.key.startsWith(CarrierPanels.CONTROL + "_")) return "control";
            if (item.key.startsWith(CarrierPanels.LOCKSCREEN + "_")) return "lockscreen";
            return "notification";
        }
        if ("classic_text".equals(group.id)) return item.key.startsWith(ClassicTextSettings.CLOCK + "_") ? "clock" : "carrier";
        if ("network".equals(group.id)) return networkDetailPageId(item.key);
        if ("battery".equals(group.id)) {
            if ("电池内文字".equals(section)) return "text";
            if (COLOR.equals(item.type) || item.key.endsWith("_color_enabled")) return "colors";
            if ("位置".equals(section) || "左右间距".equals(section) || "大小".equals(section)) return "layout";
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
    /** Old destinations resolve to the one editor without adding duplicate catalogue groups or fields. */
    public static String canonicalGroupId(String id) {
        return "wifi".equals(id)||"label".equals(id)||"native_network_badge".equals(id)?"network":id;
    }
    public static String networkRoutePage(String id) {
        return "wifi".equals(id)?"wifi":"label".equals(id)?"text":"native_network_badge".equals(id)?"badge":"common";
    }
    /** Historical portable sections remain independent from the current editor's ownership. */
    public static String networkOwner(String key) {
        if (key == null) return "";
        if (key.startsWith("native_network_badge_")||key.startsWith("native_data_activity_")) return "native_network_badge";
        if (key.startsWith("label_")||StatusBarSettings.FONT_WEIGHT.equals(key)||StatusBarSettings.SLOT_WIDTH.equals(key)
                ||key.equals(StatusBarLayoutSettings.key(StatusBarLayoutSettings.LABEL,"margin_left"))
                ||key.equals(StatusBarLayoutSettings.key(StatusBarLayoutSettings.LABEL,"margin_right"))) return "label";
        if (key.startsWith("wifi_")||StatusBarLayoutSettings.WIFI_LABEL_SHARED.equals(key)
                ||key.equals(StatusBarLayoutSettings.key(StatusBarLayoutSettings.WIFI,"margin_left"))
                ||key.equals(StatusBarLayoutSettings.key(StatusBarLayoutSettings.WIFI,"margin_right"))) return "wifi";
        return "";
    }
    public static String networkDetailPageId(String key) {
        if("label_color_enabled".equals(key)||"label_color_light".equals(key)||"label_color_dark".equals(key)) return "badge";
        if (StatusBarLayoutSettings.WIFI_LABEL_SHARED.equals(key)||"wifi_position_enabled".equals(key)
                ||StatusBarSettings.WIFI_OFFSET_X.equals(key)||StatusBarSettings.WIFI_OFFSET_Y.equals(key)
                ||StatusBarLayoutSettings.key(StatusBarLayoutSettings.WIFI,"margin_left").equals(key)
                ||StatusBarLayoutSettings.key(StatusBarLayoutSettings.WIFI,"margin_right").equals(key)) return "common";
        String owner=networkOwner(key);
        return "wifi".equals(owner)?"wifi":"label".equals(owner)?"text":"badge";
    }
    public static List<Item> items(String groupId) {
        Group group = group(groupId);
        return group == null ? Collections.emptyList() : group.items;
    }
    public static Item item(String key) { return ITEM_BY_KEY.get(key); }
    /** Unavailable controls are disabled by the editor; experimental controls remain opt-in. */
    public static String unavailableReason(String key) {
        return ShadeWallpaperSettings.unavailableReason(key);
    }
    /** Saved conflicting values are retained; their effects resume only when the conflict ends. */
    /** The value a greyed numeric item shows: the one that is actually in force while it is unavailable (null: the stored value). */
    public static Float inactiveNumber(String key, Map<String,?> values) {
        if(FluidCloudCapsule.BORDER_WIDTH.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.BORDER_ENABLED))
            return FluidCloudCapsule.DEFAULT_BORDER_WIDTH;
        return null;
    }



    private static boolean lockStyleStrength(String portraitKey) {
        return NotificationBigClockSettings.LOCK_STYLE_NATIVE.equals(portraitKey)||NotificationBigClockSettings.LOCK_STYLE_BODY.equals(portraitKey)||NotificationBigClockSettings.LOCK_STYLE_GRADIENT.equals(portraitKey)
                ||NotificationBigClockSettings.LOCK_STYLE_RIM.equals(portraitKey)||NotificationBigClockSettings.LOCK_STYLE_RIM_DEPTH.equals(portraitKey)
                ||NotificationBigClockSettings.LOCK_STYLE_ROUND.equals(portraitKey)||NotificationBigClockSettings.LOCK_STYLE_SHADE.equals(portraitKey)
                ||NotificationBigClockSettings.LOCK_STYLE_GLOW.equals(portraitKey)||NotificationBigClockSettings.LOCK_STYLE_ANGLE.equals(portraitKey);
    }
    /** The plugin's own glass material and its border, replaced while the lock-screen style is on. */
    private static boolean plainGlass(String portraitKey) {
        return NotificationBigClockSettings.GLASS.equals(portraitKey)||NotificationBigClockSettings.GLASS_BORDER_ENABLED.equals(portraitKey)
                ||NotificationBigClockSettings.GLASS_BORDER_WIDTH.equals(portraitKey)||NotificationBigClockSettings.GLASS_BORDER_COLOR_LIGHT.equals(portraitKey)
                ||NotificationBigClockSettings.GLASS_BORDER_COLOR_DARK.equals(portraitKey);
    }
    private static boolean backdropMaterialControl(String key) {
        String base=key!=null&&key.endsWith("_custom_alpha")?key.substring(0,key.length()-"_custom_alpha".length()):key;
        String clock=NotificationBigClockSettings.portraitKey(base);
        return plainGlass(clock)||NotificationBigClockSettings.LOCK_STYLE.equals(clock)||lockStyleStrength(clock)
                ||NotificationBigClockSettings.COLOR_LIGHT.equals(clock)||NotificationBigClockSettings.COLOR_DARK.equals(clock);
    }
    public static String unavailableReason(String key, Map<String,?> values) {
        String shelved = ShadeWallpaperSettings.unavailableReason(key);
        if (!shelved.isEmpty()) return shelved;
        if(StatusBarSettings.WIFI_OFFSET_X.equals(key)&&!StatusBarSettings.bool(values,StatusBarLayoutSettings.WIFI_LABEL_SHARED))
            return "请先开启 Wi-Fi 与制式文字共用位置；独立模式的左右位置使用左右间距调整";
        if(BigClockWidgetsSettings.SWAP_WITH_DATE.equals(key)) {
            if(!StatusBarSettings.bool(values,NotificationBigClockSettings.DATE_ENABLED)
                    ||!(StatusBarSettings.settingNumber(values,NotificationBigClockSettings.DATE_SIZE,15f)>0f))return "请先开启有效日期";
            if(!StatusBarSettings.bool(values,BigClockWidgetsSettings.LEFT_ENABLED)
                    &&!StatusBarSettings.bool(values,BigClockWidgetsSettings.RIGHT_ENABLED))return "请先开启至少一侧组件";
        }
        if(bigClockWidget(key)) {
            int side=BigClockWidgetsSettings.textSide(key);
            String parent=side>=0&&!BigClockWidgetsSettings.enabledKey(side).equals(key)?BigClockWidgetsSettings.enabledKey(side):null;
            if(parent!=null&&!StatusBarSettings.bool(values,parent))return BigClockWidgetsSettings.LEFT_ENABLED.equals(parent)
                    ?"请先开启显示左侧组件":"请先开启显示右侧组件";
        }
        if (StatusBarLayoutSettings.key(StatusBarLayoutSettings.LABEL, "order").equals(key)
                && StatusBarSettings.bool(values, StatusBarLayoutSettings.WIFI_LABEL_SHARED))
            return "已与 Wi-Fi 共用排序，请调整 Wi-Fi 的所在区域与顺序";
        if ((StatusBarLayoutSettings.sideKey(key) || StatusBarLayoutSettings.orderKey(key))
                && !StatusBarSettings.bool(values, StatusBarLayoutSettings.MASTER)) return "请先开启状态栏排序";
        if ((DataBatterySpacing.ENABLED.equals(key) || DataBatterySpacing.SPACING.equals(key))
                && StatusBarLayoutSettings.enabledValues(values))
            return "状态栏排序或左右间距已接管布局；原值保留，关闭排序并将所有左右间距恢复为 0 后恢复";
        if (StatusBarSettings.SPEED_OFFSET_X.equals(key)
                && StatusBarSettings.bool(values, SpeedPosition.SAFE_GAP_ENABLED))
            return "距离模式已接管水平定位；关闭“与相邻图标保持距离”后可调整，原水平数值会保留";
        String modeReason = PanelMode.freezeReason(key);
        if (modeReason != null && !modeReason.isEmpty()) return modeReason;
        if(NetworkLabelFormats.owns(key)&&!StatusBarSettings.bool(values,NetworkLabelFormats.ENABLED))
            return "请先开启自定义制式显示内容";
        String clockKey=NotificationBigClockSettings.portraitKey(key);
        String backdropSwitch=key!=null&&key.startsWith(NotificationBigClockSettings.LANDSCAPE_PREFIX)
                ?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED):NotificationBigClockSettings.DIGIT_REVEAL_ENABLED;
        if(ClockBackdropSettings.owns(key)&&!StatusBarSettings.bool(values,backdropSwitch))return "请先开启字内背景透出";
        if((ClockBackdropSettings.DATE_MASK_OPACITY.equals(clockKey)||ClockBackdropSettings.FOOTER_MASK_OPACITY.equals(clockKey))
                &&!StatusBarSettings.bool(values,clockKey.equals(key)?NotificationBigClockSettings.FOLLOW_STYLE:NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.FOLLOW_STYLE)))
            return "请先开启时间和日期样式跟随";
        if(BigClockWidgetsSettings.maskOpacityKey(key)) {
            if(!StatusBarSettings.bool(values,BigClockWidgetsSettings.followTimeColorKey(BigClockWidgetsSettings.textSide(key))))return "请先开启组件颜色跟随主时间";
            if(!StatusBarSettings.bool(values,NotificationBigClockSettings.DIGIT_REVEAL_ENABLED))return "请先开启竖屏字内背景透出";
        }
        if(ClockBackdropSettings.NOTIFICATION_FOLLOW.equals(key)||ClockBackdropSettings.NOTIFICATION_RELIEF.equals(key))
            return "通知文字背景材质已移除；旧设置仅保留配置兼容";
        if(BigClockWidgetsSettings.launchAppKey(key)&&!StatusBarSettings.bool(values,BigClockWidgetsSettings.clickEnabledKey(BigClockWidgetsSettings.textSide(key))))
            return "请先开启组件点击打开应用";
        if(backdropMaterialControl(key)&&StatusBarSettings.bool(values,backdropSwitch))
            return "已开启字内背景透出，当前使用专用背景材质；关闭后恢复原外观设置";
        if ((NotificationBigClockSettings.FOLLOW_STYLE.equals(clockKey)
                ||NotificationBigClockSettings.REVEAL_MASK_COLOR.equals(clockKey)
                ||NotificationBigClockSettings.REVEAL_MASK_OPACITY.equals(clockKey))
                &&!StatusBarSettings.bool(values,clockKey.equals(key)?NotificationBigClockSettings.DIGIT_REVEAL_ENABLED
                :NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED)))
            return "请先开启字内背景透出";
        String styleSwitch=clockKey.equals(key)?NotificationBigClockSettings.LOCK_STYLE
                :NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.LOCK_STYLE);
        if(lockStyleStrength(clockKey)&&!StatusBarSettings.bool(values,styleSwitch))return "请先开启锁屏质感";
        if(plainGlass(clockKey)&&StatusBarSettings.bool(values,styleSwitch))return "已被锁屏质感替代，关闭锁屏质感后可用";
        if(NotificationBigClockSettings.CLOCK_LAUNCH_APP.equals(key)
                &&!StatusBarSettings.bool(values,NotificationBigClockSettings.CLOCK_CLICK_ENABLED))
            return "请先开启点击时间打开时钟";
        if(NotificationBigClockSettings.DATE_LAUNCH_APP.equals(key)
                &&!StatusBarSettings.bool(values,NotificationBigClockSettings.DATE_CLICK_ENABLED))
            return "请先开启点击日期打开日历";
        if((NotificationBigClockSettings.REBOUND_BLUR_ENABLED.equals(key)||NotificationBigClockSettings.REBOUND_BLUR_RADIUS.equals(key)
                ||NotificationBigClockSettings.REBOUND_FADE.equals(key))
                &&StatusBarSettings.bool(values,NotificationBigClockSettings.REBOUND_LOCK))
            return "已开启“关闭回弹效果”，没有回弹也就没有回弹时的模糊，两者互斥；先关闭它才能使用";
        if((NotificationBigClockSettings.REBOUND_BLUR_RADIUS.equals(key)||NotificationBigClockSettings.REBOUND_FADE.equals(key))
                &&!StatusBarSettings.bool(values,NotificationBigClockSettings.REBOUND_BLUR_ENABLED))
            return "请先开启回弹时模糊";
        boolean unified=StatusBarSettings.bool(values,LockNotificationSettings.UNIFY_AOD);
        if(unified&&(FluidCloudStyle.NOTIFICATION_FOLLOW.equals(key)||FluidCloudStyle.NOTIFICATION_FOLLOW_OPACITY.equals(key)
                ||LockNotificationSettings.AOD_NOTIFICATION_FOLLOW_OPACITY.equals(key)))
            return "已开启“统一样式”：通知按流体云卡片的样子着色，不再用这里的开关和强度";
        if((FluidCloudAccent.AOD_OPACITY.equals(key)||FluidCloudAccent.AOD_MASK_COLOR.equals(key)||FluidCloudAccent.AOD_MASK_OPACITY.equals(key))
                &&!StatusBarSettings.bool(values,FluidCloudAccent.AOD_CUSTOM))
            return "请先开启 AOD 自定义";
        String wallpaper=wallpaperReason(key,values);
        if(!wallpaper.isEmpty())return wallpaper;
        if(NotificationIconArea.FOLLOW_LAUNCHER.equals(key)
                &&!"native".equals(StatusBarSettings.string(values,NotificationIconArea.MODE)))
            return "仅在原生通知图标显示方式下生效";
        if(FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT.equals(key)
                &&!StatusBarSettings.bool(values,C17HighlightRemoval.ENABLED))
            return "请先在“界面材质 → C17 高光去除”开启高光去除；本项只决定音乐卡片是否保留";
        // The media-accent page has one master. Everything on it, following included, is greyed while it is off.
        if(fluidAccentChild(key)&&!StatusBarSettings.bool(values,FluidCloudAccent.ENABLED))
            return "请先开启流体云媒体强调色";
        if((FluidCloudAccent.MASK_COLOR.equals(key)||FluidCloudAccent.MASK_OPACITY.equals(key))
                &&!StatusBarSettings.bool(values,FluidCloudAccent.MASK_ENABLED))
            return "请先开启强调色遮罩";
        boolean customBase=StatusBarSettings.bool(values,FluidCloudStyle.MASTER)&&StatusBarSettings.bool(values,FluidCloudStyle.DESKTOP_BACKGROUND);
        if(FluidCloudStyle.FOLLOW_MEDIA.equals(key)&&!customBase)
            return "请先开启“流体云样式”里的自定义底色";
        // "Only while playing" also governs the lock screen's and AOD's own following, which needs neither master.
        if(FluidCloudStyle.FOLLOW_ONLY_PLAYING.equals(key)&&!lockOwnFollowing(values)
                &&!(StatusBarSettings.bool(values,FluidCloudStyle.FOLLOW_MEDIA)&&customBase))
            return "请先开启跟随媒体强调色，或锁屏 / AOD 页里的通知跟随、统一样式、渐变样式、自定义";
        if(FluidCloudStyle.NOTIFICATION_FOLLOW_OPACITY.equals(key)
                &&!StatusBarSettings.bool(values,FluidCloudStyle.NOTIFICATION_FOLLOW))
            return "请先开启通知跟随媒体色";
        if((FluidCloudAccent.LOCK_OPACITY.equals(key)||FluidCloudAccent.LOCK_MASK_COLOR.equals(key)
                ||FluidCloudAccent.LOCK_MASK_OPACITY.equals(key))
                &&!StatusBarSettings.bool(values,FluidCloudAccent.LOCK_CUSTOM))
            return "请先开启锁屏自定义";
        if(fluidStyleChild(key)&&!StatusBarSettings.bool(values,FluidCloudStyle.MASTER))
            return "请先开启流体云样式";
        if(capsuleChild(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.MASTER))
            return "请先开启摄像头区域流体云";
        if((PrivacyIndicatorSettings.CAMERA.equals(key)||PrivacyIndicatorSettings.MICROPHONE.equals(key)
                ||PrivacyIndicatorSettings.LOCATION.equals(key)||PrivacyIndicatorSettings.DURATION.equals(key))
                &&!StatusBarSettings.bool(values,PrivacyIndicatorSettings.CAPSULE))
            return "请先开启隐私小点上流体云";
        if(glassOverridesFill(key)&&StatusBarSettings.bool(values,FluidCloudCapsule.BLUR_ENABLED))
            return "背景模糊开启后底色由遮罩色决定，这一项不再起作用";
        if(FluidCloudCapsule.BLUR_MASK_COLOR.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.BLUR_ENABLED))
            return "请先开启背景模糊";
        if(FluidCloudCapsule.GLOW_ENABLED.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.MARQUEE_ENABLED))
            return "请先开启边框跑马灯";
        if(FluidCloudCapsule.GLOW_DEPTH.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.GLOW_ENABLED))
            return "请先开启向内渐隐";
        if((FluidCloudCapsule.SPECTRUM_COLOR.equals(key)||FluidCloudCapsule.SPECTRUM_FOLLOW.equals(key))
                &&!StatusBarSettings.bool(values,FluidCloudCapsule.SPECTRUM_ENABLED))
            return "请先开启实时频谱";
        if(FluidCloudCapsule.MARQUEE_COLOR_COUNT.equals(key)&&!"custom".equals(StatusBarSettings.string(values,FluidCloudCapsule.MARQUEE_PRESET)))
            return "只有“自定义”预设使用";
        for(int i=3;i<FluidCloudCapsule.MARQUEE_COLOR_KEYS.length;i++)
            if(FluidCloudCapsule.MARQUEE_COLOR_KEYS[i].equals(key)&&"custom".equals(StatusBarSettings.string(values,FluidCloudCapsule.MARQUEE_PRESET))
                    &&i>=Math.round(StatusBarSettings.settingNumber(values,FluidCloudCapsule.MARQUEE_COLOR_COUNT,FluidCloudCapsule.DEFAULT_MARQUEE_COLOR_COUNT)))
                return "自定义颜色数量只有 "+Math.round(StatusBarSettings.settingNumber(values,FluidCloudCapsule.MARQUEE_COLOR_COUNT,FluidCloudCapsule.DEFAULT_MARQUEE_COLOR_COUNT))+" 个";
        if(FluidCloudCapsule.FILL_COLOR.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.FILL_ENABLED))
            return "请先开启自定义底色";
        if(FluidCloudCapsule.FILL_OPACITY.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.FILL_ENABLED)
                &&!StatusBarSettings.bool(values,FluidCloudCapsule.FOLLOW_MEDIA))
            return "请先开启自定义底色或跟随媒体强调色";
        if(FluidCloudCapsule.BORDER_COLOR.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.BORDER_ENABLED))
            return "请先开启自定义边框";
        // The running light alone draws at the plugin's own outline width; only the custom border makes the width adjustable.
        if(FluidCloudCapsule.BORDER_WIDTH.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.BORDER_ENABLED))
            return StatusBarSettings.bool(values,FluidCloudCapsule.MARQUEE_ENABLED)
                    ?"未开启自定义边框，跑马灯使用系统默认粗细 1.3 dp；开启自定义边框后可调"
                    :"请先开启自定义边框";
        if(FluidCloudCapsule.GRADIENT_ENABLED.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.FILL_ENABLED)
                &&!StatusBarSettings.bool(values,FluidCloudCapsule.FOLLOW_MEDIA))
            return "请先开启自定义底色或跟随媒体强调色";
        if((FluidCloudCapsule.GRADIENT_MODE.equals(key)||FluidCloudCapsule.GRADIENT_COLOR.equals(key))
                &&!StatusBarSettings.bool(values,FluidCloudCapsule.GRADIENT_ENABLED))
            return "请先开启渐变底色";
        if(FluidCloudCapsule.BLUR_RADIUS.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.BLUR_ENABLED))
            return "请先开启背景模糊";
        if(marqueeChild(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.MARQUEE_ENABLED))
            return "请先开启边框跑马灯";
        if(FluidCloudCapsule.MARQUEE_COLOR_1.equals(key)&&!"comet".equals(StatusBarSettings.string(values,FluidCloudCapsule.MARQUEE_PRESET))
                &&!"custom".equals(StatusBarSettings.string(values,FluidCloudCapsule.MARQUEE_PRESET)))
            return "当前预设自带颜色；选“单色流光”或“自定义”后可改";
        if((FluidCloudCapsule.MARQUEE_COLOR_2.equals(key)||FluidCloudCapsule.MARQUEE_COLOR_3.equals(key))
                &&!"custom".equals(StatusBarSettings.string(values,FluidCloudCapsule.MARQUEE_PRESET)))
            return "只有“自定义”预设使用这个颜色";
        if(FluidCloudCapsule.SPECTRUM_SENSITIVITY.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.SPECTRUM_ENABLED))
            return "请先开启实时频谱";
        if(FluidCloudCapsule.RADIUS.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.RADIUS_ENABLED))
            return "请先开启自定义圆角";
        if(FluidCloudCapsule.COVER_RADIUS.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.COVER_RADIUS_ENABLED))
            return "请先开启自定义封面圆角";
        if(FluidCloudCapsule.COVER_SCALE.equals(key)&&!StatusBarSettings.bool(values,FluidCloudCapsule.COVER_SIZE_ENABLED))
            return "请先开启调整音乐封面大小";
        if(CapsuleEffects.BOUNCE.equals(key)&&!StatusBarSettings.bool(values,CapsuleEffects.BOUNCE_ENABLED))
            return "请先开启自定义出现回弹";
        boolean gradientShape=FluidCloudStyle.BACKGROUND_GRADIENT_RANGE.equals(key)||FluidCloudStyle.BACKGROUND_GRADIENT_START.equals(key)
                ||FluidCloudStyle.BACKGROUND_GRADIENT_MID.equals(key)||FluidCloudStyle.BACKGROUND_GRADIENT_STRENGTH.equals(key)
                ||FluidCloudStyle.BACKGROUND_GRADIENT_OPACITY.equals(key);
        if(FluidCloudStyle.BACKGROUND_MODE.equals(key)||FluidCloudStyle.BACKGROUND_COLOR.equals(key)
                ||FluidCloudStyle.BACKGROUND_OPACITY.equals(key)||gradientShape) {
            if(!StatusBarSettings.bool(values,FluidCloudStyle.MASTER))return "请先开启流体云样式";
            if(!StatusBarSettings.bool(values,FluidCloudStyle.DESKTOP_BACKGROUND))return "请先开启自定义底色";
            if(gradientShape
                    &&!"native".equals(StatusBarSettings.string(values,FluidCloudStyle.BACKGROUND_MODE)))
                return "请选择原生渐变背景模式";
            if(FluidCloudStyle.BACKGROUND_COLOR.equals(key)
                    &&"notification".equals(StatusBarSettings.string(values,FluidCloudStyle.BACKGROUND_MODE)))
                return "跟随系统通知时使用系统颜色；选择系统默认或自定义颜色后可编辑";
        }
        if(ClockLayoutSettings.STRINGS.containsKey(key)||ClockLayoutSettings.NUMBERS.containsKey(key)||ClockLayoutSettings.COLORS.containsKey(key)) {
            for(String group:ClockLayoutSettings.DIGIT_GROUPS)
                if(ClockLayoutSettings.digitSpacingKey(group).equals(key)
                        &&!StatusBarSettings.bool(values,ClockLayoutSettings.digitKey(group)))return "请先开启等宽数字";
            if(key.startsWith("clock_dual_")&&!StatusBarSettings.bool(values,ClockLayoutSettings.DUAL_ENABLED))
                return "请先开启双排时钟";
            if((ClockLayoutSettings.MARGIN_START.equals(key)||ClockLayoutSettings.MARGIN_END.equals(key))
                    &&!StatusBarSettings.bool(values,ClockLayoutSettings.MARGIN_ENABLED))return "请先开启整体边距";
        }
        if(NotificationBigClockSettings.STACK_ENABLED.equals(key)
                &&!StatusBarSettings.bool(values,NotificationBigClockSettings.MASTER)
                &&!(StatusBarSettings.bool(values,NotificationBigClockSettings.LANDSCAPE_MASTER)
                    &&StatusBarSettings.bool(values,FeatureOptions.STACK_LANDSCAPE_ENABLED)))
            return "请先开启对应方向的大时钟，避免通知无法滚动";
        if(FeatureOptions.STACK_LANDSCAPE_ENABLED.equals(key)
                &&!StatusBarSettings.bool(values,NotificationBigClockSettings.LANDSCAPE_MASTER))
            return "请先开启对应方向的大时钟，避免通知无法滚动";
        if(NotificationMiniStack.HIDE_BADGE.equals(key)&&!StatusBarSettings.bool(values,NotificationMiniStack.ENABLED))
            return "请先开启小堆叠（同应用）";
        if(NotificationMiniStack.AUTO_HIDE_IN_STACK.equals(key)) {
            if(!StatusBarSettings.bool(values,NotificationBigClockSettings.STACK_ENABLED))return "请先开启堆叠通知";
            if(!StatusBarSettings.bool(values,NotificationMiniStack.ENABLED))return "请先开启小堆叠（同应用）";
        }
        if("native_network_badge".equals(networkOwner(key))
                && !key.startsWith("native_data_activity_")
                && !NetworkDisplaySettings.BADGE.equals(NetworkDisplaySettings.mode(values)))
            return "制式选择原生角标后使用这些参数，当前保存值会保留";
        return unavailableReason(key);
    }

    /** One of the clock zone's own settings (not its switch). */
    private static boolean zoneKey(String scene,String key) {
        return key.equals(ShadeWallpaperSettings.zoneBlurKey(scene))||key.equals(ShadeWallpaperSettings.zoneFollowKey(scene))
                ||key.equals(ShadeWallpaperSettings.zoneHeightKey(scene))||key.equals(ShadeWallpaperSettings.zoneFeatherKey(scene))
                ||key.equals(ShadeWallpaperSettings.zoneLiftKey(scene))||key.equals(ShadeWallpaperSettings.zoneSaturationKey(scene))
                ||key.equals(ShadeWallpaperSettings.zoneTintKey(scene))||key.equals(ShadeWallpaperSettings.zoneTintStrengthKey(scene));
    }
    /** The pull-down wallpaper's switches: everything waits for its page; a scene's switch waits for its picture; the zone's numbers wait for the zone. */
    private static String wallpaperReason(String key,Map<String,?> values) {
        if(key==null||!key.startsWith("shade_wallpaper_")||ShadeWallpaperSettings.MASTER.equals(key)
                ||ShadeWallpaperSettings.PORTRAIT_MASTER.equals(key)||ShadeWallpaperSettings.LANDSCAPE_MASTER.equals(key)
                ||ShadeWallpaperSettings.LEGACY_MANAGE.equals(key)||ShadeWallpaperSettings.sceneOfRevisionKey(key)!=null)return "";
        for(boolean landscape:new boolean[]{false,true})
            if((key.equals(ShadeWallpaperSettings.blurWithPullKey(landscape))||key.equals(ShadeWallpaperSettings.swipeSlideKey(landscape)))
                    &&!StatusBarSettings.bool(values,ShadeWallpaperSettings.masterKey(landscape)))
                return landscape?"请先开启横屏下拉壁纸":"请先开启竖屏下拉壁纸";
        for(String scene:ShadeWallpaperSettings.SCENES) {
            boolean ofScene=key.equals(ShadeWallpaperSettings.enabledKey(scene))||key.equals(ShadeWallpaperSettings.brightnessKey(scene))||key.equals(ShadeWallpaperSettings.blurKey(scene))
                    ||(ShadeWallpaperSettings.hasZone(scene)&&(zoneKey(scene,key)||key.equals(ShadeWallpaperSettings.zoneEnabledKey(scene))));
            if(ofScene&&!StatusBarSettings.bool(values,ShadeWallpaperSettings.masterKey(scene)))
                return ShadeWallpaperSettings.landscape(scene)?"请先开启横屏下拉壁纸":"请先开启竖屏下拉壁纸";
            if(key.equals(ShadeWallpaperSettings.enabledKey(scene))
                    &&!ShadeWallpaperSettings.validRevision(String.valueOf(values==null?null:values.get(ShadeWallpaperSettings.revisionKey(scene)))))
                return "请先在“壁纸图片”里选择这个面板的图片";
            if(!ShadeWallpaperSettings.hasZone(scene))continue;
            if(zoneKey(scene,key)&&!StatusBarSettings.bool(values,ShadeWallpaperSettings.zoneEnabledKey(scene)))return "请先开启大时钟区域单独设置";
            if(key.equals(ShadeWallpaperSettings.zoneHeightKey(scene))&&StatusBarSettings.bool(values,ShadeWallpaperSettings.zoneFollowKey(scene)))
                return "区域高度正跟随大时钟；关闭“区域高度跟随大时钟”后才用这个固定高度";
        }
        return "";
    }
    /**
     * What waits for the media-accent master. The lock screen and AOD pages are not in it: they are set on their own and take effect without
     * the media-accent and fluid-cloud style masters (those govern the other scenes).
     */
    private static boolean fluidAccentChild(String key) {
        return FluidCloudAccent.MASK_ENABLED.equals(key)||FluidCloudAccent.MASK_COLOR.equals(key)
                ||FluidCloudAccent.MASK_OPACITY.equals(key)||FluidCloudAccent.OPACITY.equals(key)
                ||FluidCloudStyle.FOLLOW_MEDIA.equals(key);
    }
    /** The lock screen or AOD follows the media colour on its own pages' switches. */
    private static boolean lockOwnFollowing(Map<String,?> values) {
        return StatusBarSettings.bool(values,FluidCloudStyle.NOTIFICATION_FOLLOW)||StatusBarSettings.bool(values,LockNotificationSettings.UNIFY_AOD)
                ||StatusBarSettings.bool(values,FluidCloudStyle.LOCK_CAPSULE)||StatusBarSettings.bool(values,FluidCloudAccent.LOCK_GRADIENT)
                ||StatusBarSettings.bool(values,FluidCloudAccent.LOCK_CUSTOM)||StatusBarSettings.bool(values,FluidCloudAccent.AOD_GRADIENT)
                ||StatusBarSettings.bool(values,FluidCloudAccent.AOD_CUSTOM);
    }
    private static boolean marqueeChild(String key) {
        for(String colour:FluidCloudCapsule.MARQUEE_COLOR_KEYS)if(colour.equals(key))return true;
        return FluidCloudCapsule.MARQUEE_PRESET.equals(key)||FluidCloudCapsule.MARQUEE_COLOR_COUNT.equals(key)
                ||FluidCloudCapsule.MARQUEE_OPACITY.equals(key)||FluidCloudCapsule.MARQUEE_PERIOD.equals(key)
                ||FluidCloudCapsule.MARQUEE_REVERSE.equals(key);
    }
    /** What the frosted glass replaces: the fill, its gradient and following the media colour. */
    private static boolean glassOverridesFill(String key) {
        return FluidCloudCapsule.FILL_ENABLED.equals(key)||FluidCloudCapsule.FILL_COLOR.equals(key)||FluidCloudCapsule.FILL_OPACITY.equals(key)
                ||FluidCloudCapsule.FOLLOW_MEDIA.equals(key)||FluidCloudCapsule.GRADIENT_ENABLED.equals(key)
                ||FluidCloudCapsule.GRADIENT_MODE.equals(key)||FluidCloudCapsule.GRADIENT_COLOR.equals(key);
    }
    private static boolean capsuleChild(String key) {
        return FluidCloudCapsule.FILL_ENABLED.equals(key)||FluidCloudCapsule.FILL_COLOR.equals(key)
                ||FluidCloudCapsule.FILL_OPACITY.equals(key)||FluidCloudCapsule.FOLLOW_MEDIA.equals(key)
                ||FluidCloudCapsule.BORDER_ENABLED.equals(key)||FluidCloudCapsule.BORDER_COLOR.equals(key)
                ||FluidCloudCapsule.BORDER_WIDTH.equals(key)||FluidCloudCapsule.RADIUS_ENABLED.equals(key)
                ||FluidCloudCapsule.RADIUS.equals(key)||FluidCloudCapsule.GRADIENT_ENABLED.equals(key)
                ||FluidCloudCapsule.GRADIENT_MODE.equals(key)||FluidCloudCapsule.GRADIENT_COLOR.equals(key)
                ||FluidCloudCapsule.BLUR_ENABLED.equals(key)||FluidCloudCapsule.BLUR_RADIUS.equals(key)||FluidCloudCapsule.BLUR_MASK_COLOR.equals(key)
                ||FluidCloudCapsule.GLOW_ENABLED.equals(key)||FluidCloudCapsule.GLOW_DEPTH.equals(key)||FluidCloudCapsule.SPECTRUM_COLOR.equals(key)
                ||FluidCloudCapsule.MARQUEE_ENABLED.equals(key)||marqueeChild(key)
                ||FluidCloudCapsule.SPECTRUM_ENABLED.equals(key)||FluidCloudCapsule.SPECTRUM_SENSITIVITY.equals(key)
                ||FluidCloudCapsule.SPECTRUM_FOLLOW.equals(key)
                ||FluidCloudCapsule.COVER_RADIUS_ENABLED.equals(key)||FluidCloudCapsule.COVER_RADIUS.equals(key)
                ||FluidCloudCapsule.COVER_SIZE_ENABLED.equals(key)||FluidCloudCapsule.COVER_SCALE.equals(key)
                ||CapsuleEffects.BOUNCE_ENABLED.equals(key)||CapsuleEffects.BOUNCE.equals(key);
    }
    /** The style page's own master governs what only that page does; keeping a music card's highlight does not belong to it. */
    private static boolean fluidStyleChild(String key) {
        return FluidCloudStyle.FOLLOW_NOTIFICATION.equals(key)||FluidCloudStyle.SCOPE_FLOAT.equals(key)
                ||FluidCloudStyle.SCOPE_SHADE.equals(key)||FluidCloudStyle.SCOPE_LOCK.equals(key)||FluidCloudStyle.DESKTOP_BACKGROUND.equals(key)
                ||FluidCloudScope.isScopeKey(key);
    }

    /** Actual precedence and mutual exclusion, shown at the top of each affected feature. */
    public static String conflictDescription(String groupId, String category) {
        if ("clock".equals(groupId)) return "默认时钟与双排时钟二选一：开启一个会自动关闭另一个，各自的开关在对应标签顶部。竖屏或横屏通知页启用大时钟后，下拉页的小时间由大时钟接管；状态栏本身仍使用本页设置。双排时钟只接管状态栏时间，下拉页等其他位置的时钟仍按默认时钟设置。";
        if ("shade_clock".equals(groupId)) return "冲突：与通知栏大时钟共用通知页时间区域。修改本页启用的条件会关闭大时钟；大时钟未启用时通知页保留下拉时钟，控制中心仍使用本页设置。";
        if ("carrier".equals(groupId) && NOTIFICATION.equals(category)) return "冲突：与通知栏大时钟共用顶部区域。启用或编辑本页条件会关闭大时钟；关闭本页开关不会自动开启大时钟。";
        if (groupId.startsWith("notification_big_clock")) return "优先级：仅在本页对应的横屏或竖屏方向替代通知页小时间，保留控制中心原生图标。编辑下拉时钟或通知栏运营商文字会关闭两个方向的大时钟；堆叠通知在“通知栏 → 通知卡片”独立设置。";
        if ("font".equals(groupId)) return "优先级：各功能独立字号和粗细仍有效；大时钟选择原生字体时使用自身字形，选择自选字体后才使用这里导入的文件。";
        if ("notification_icons".equals(groupId)) return "互斥：原生图标、爱心、文字和图片只选一种。最多图标数量仅作用于原生模式；通知页隐藏规则优先于本页外观设置。";
        if ("notification_icon_overrides".equals(groupId)) return "优先级：通知图标区域使用单个爱心、文字或图片时，暂停逐应用替换；恢复原生显示方式后继续生效。通知数量和隐藏规则仍由原生系统及对应配置控制。";
        if ("status_icon_library".equals(groupId)) return "先导入图标库，再应用到所选范围；单项选择优先于套装。颜色默认跟随系统，原色素材可保留自带颜色；颜色和透明度分别继承。关闭、不可用与动态能力以实际原生状态为准。";
        if ("notification_mini_stack".equals(groupId)) return "独立：只呈现系统已形成的原生通知组，不重新分组、不排序、不增加下拉手势，通知上滑展开保持原样；"
                + "与“堆叠通知”（整列）互不依赖，不需要大时钟或流体云。通知颜色、渐变与高光仍由各自功能处理。隐私内容、悬浮通知和打开设置菜单的通知保持原生。"
                + NotificationMiniStackSource.DECLARATION + NotificationMiniStackSource.TECHNICAL_ORIGIN;
        if ("notification_stack".equals(groupId)) return "依赖：请先开启对应方向的大时钟，避免通知无法滚动。竖屏需竖屏大时钟；横屏还需“横屏也生效”和横屏大时钟。仅折叠外层通知列表，应用内分组保持系统原生逻辑。";
        if ("shade_status_icons".equals(groupId)) return "优先级：此开关或大时钟任一开启，顶部原生小时间、通知图标与右侧图标及通知页的小时间和右侧图标在整个下拉、左右切页期间持续隐藏。两页都收起或两个开关都关闭时恢复原生。控制中心自己的右侧始终保留原生，左侧小时间与通知图标仅由此开关管理。通知图标样式和数量继续在“通知图标区域”中设置。";
        if ("network".equals(groupId)) return "制式文字、原生角标与隐藏互斥，切换保留各自参数。Wi-Fi 真隐藏与自定义外观独立；Wi-Fi 与独立制式文字共用位置，不共享大小，原生角标保持信号角位置并独立微调。数据小箭头独立设置。";
        if ("battery".equals(groupId)) return "优先级：电池内文字调整只作用于数字；充电数字与闪电交替开启时，数字阶段使用文字设置。切换电池样式后按该样式的原生字形重新应用。";
        if ("qs_media".equals(groupId)) return "互斥：外发光使用封面反色时，自定义发光颜色不生效。背景来源的优先级：自定义颜色 > 封面主色 > 封面图片；叠加色只作用于封面图片和封面主色。封面背景和统一圆角可同时使用。";
        if ("c17_highlight_removal".equals(groupId)) return "共同使用：去除高光可与磁贴颜色、音乐封面背景同时开启。这两类自定义背景保留；本页亚克力底色作用于其他原生材质。";
        if ("fluid_cloud_style".equals(groupId)) return "颜色优先级：媒体强调色 > 跟随媒体色 > 这里的自定义底色；强调色与跟随色用“流体云媒体强调色”页的遮罩与透明度，只有其余情况才用这里的底色。通知类跟随系统样式只提供材质，可与颜色叠加。颜色只作用于悬浮展开、锁屏与 AOD（AOD 与锁屏一致），不改动通知栏。去高光由 C17 高光去除统一控制。";
        if ("fluid_cloud_accent".equals(groupId)) return "优先级 1：总开关开启后音乐卡始终使用强调色，覆盖悬浮、锁屏和 AOD；其他卡片可跟随当前媒体色（优先级 2，高于自定义底色）。强调色与跟随色共用本页的遮罩与填充透明度；锁屏与 AOD 可各用自己的自定义设置。关闭总开关后，锁屏与 AOD 的所有媒体取色一起停止，原有样式设置保留。没有媒体或不在播放（开启“仅播放时跟随”时）则回到“流体云样式”里的自定义底色。";
        if ("data".equals(groupId)) return "制式文字、原生角标与隐藏在“网络组合”中选择。数据小箭头也在该页独立设置，不受蜂窝样式或制式文字开关限制。";
        return "";
    }
    public static List<Item> allItems() { return Collections.unmodifiableList(new ArrayList<>(ITEM_BY_KEY.values())); }
    public static Set<String> hiddenKeys() { return HIDDEN; }
    public static boolean isVisible(String key) { return ITEM_BY_KEY.containsKey(key); }
    /** Scene-specific editors never change the saved compatibility keys. */
    public static boolean applicable(Item item,Map<String,?> saved) {
        if(item==null)return false;
        if("network".equals(item.groupId)&&StatusBarLayoutSettings.sharedPositionKey(item.key)
                &&"label".equals(networkOwner(item.key)))
            return !StatusBarSettings.bool(saved,StatusBarLayoutSettings.WIFI_LABEL_SHARED);
        if(QsWifiAlias.UNIFIED.equals(item.key))return "unified".equals(StatusBarSettings.string(saved,QsWifiAlias.MODE));
        if(QsWifiAlias.RULES.equals(item.key))return !"unified".equals(StatusBarSettings.string(saved,QsWifiAlias.MODE));
        int widgetSide=BigClockWidgetsSettings.textSide(item.key);
        if(widgetSide>=0&&(BigClockWidgetsSettings.colorKey(widgetSide,false).equals(item.key)||BigClockWidgetsSettings.colorKey(widgetSide,true).equals(item.key)))
            return !StatusBarSettings.bool(saved,BigClockWidgetsSettings.followTimeColorKey(widgetSide));
        String portrait=NotificationBigClockSettings.portraitKey(item.key);
        // Per-line colours return whenever the optional follow policy is paused.
        if(NotificationBigClockSettings.DATE_COLOR_LIGHT.equals(portrait)||NotificationBigClockSettings.DATE_COLOR_DARK.equals(portrait)
                ||NotificationBigClockSettings.FOOTER_COLOR_LIGHT.equals(portrait)||NotificationBigClockSettings.FOOTER_COLOR_DARK.equals(portrait))
            return !NotificationBigClockSettings.effectiveFollowStyle(saved,item.key.startsWith(NotificationBigClockSettings.LANDSCAPE_PREFIX));
        if(NotificationBigClockSettings.SCREEN_PADDING.equals(item.key)
                ||NotificationBigClockSettings.COMPACT_MAX_SIZE.equals(NotificationBigClockSettings.portraitKey(item.key)))
            return "notification_big_clock".equals(item.groupId)
                    && NotificationBigClockSettings.nativeHeightFont(StatusBarSettings.string(saved,NotificationBigClockSettings.FONT));
        return true;
    }
    /** Search may reveal an inactive mode's saved setting; explain it without changing that mode. */
    public static String inactivePresentationReason(Item item, Map<String,?> saved) {
        if (item == null || applicable(item, saved)) return "";
        if("network".equals(item.groupId)&&"label".equals(networkOwner(item.key)))
            return "当前与 Wi-Fi 共用位置；关闭共用后恢复这里保存的独立位置。";
        if(QsWifiAlias.UNIFIED.equals(item.key))return "当前按 WiFi 独立设置；切换到统一显示后使用这个名称。";
        if(QsWifiAlias.RULES.equals(item.key))return "当前使用统一名称；切换到各 WiFi 独立设置后使用这里保存的别名。";
        if (BigClockWidgetsSettings.textSide(item.key) >= 0) return "当前组件跟随主时间颜色；关闭跟随后使用这里保存的独立颜色";
        String key = NotificationBigClockSettings.portraitKey(item.key);
        if (NotificationBigClockSettings.DATE_COLOR_LIGHT.equals(key) || NotificationBigClockSettings.DATE_COLOR_DARK.equals(key)
                || NotificationBigClockSettings.FOOTER_COLOR_LIGHT.equals(key) || NotificationBigClockSettings.FOOTER_COLOR_DARK.equals(key))
            return "当前跟随背景样式；关闭跟随后恢复这里保存的独立颜色";
        return "仅适用于竖屏原生字体；当前字体或方向暂不使用此参数";
    }
    public static String displayTitle(Item item,Map<String,?> saved) {
        if(item==null)return "";
        if (StatusBarLayoutSettings.sharedPositionKey(item.key)
                && StatusBarSettings.bool(saved, StatusBarLayoutSettings.WIFI_LABEL_SHARED)) return item.title + " · 共用";
        if(NotificationBigClockSettings.MAX_SIZE.equals(item.key)
                && NotificationBigClockSettings.nativeHeightFont(StatusBarSettings.string(saved,NotificationBigClockSettings.FONT)))return "展开字体高度";
        return item.title;
    }
    private static boolean bigClockWidget(String key) {
        return BigClockWidgetsSettings.SWAP_WITH_DATE.equals(key)||BigClockWidgetsSettings.textSide(key)>=0
                ||BigClockWidgetsSettings.LEFT_PROVIDER.equals(key)||BigClockWidgetsSettings.RIGHT_PROVIDER.equals(key)
                ||BigClockWidgetsSettings.LEFT_HEIGHT.equals(key)||BigClockWidgetsSettings.RIGHT_HEIGHT.equals(key);
    }
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
        if(BigClockWidgetsSettings.maskOpacityKey(key)&&(raw.signum()<0||raw.compareTo(BigDecimal.valueOf(100))>0))return "组件遮罩强度应为 0 到 100%";
        if(BigClockWidgetsSettings.weightKey(key)&&(raw.compareTo(BigDecimal.valueOf(BigClockWidgetsSettings.MIN_WEIGHT))<0
                ||raw.compareTo(BigDecimal.valueOf(BigClockWidgetsSettings.MAX_WEIGHT))>0))return "组件字体粗细应为 100 到 1000";
        if(BigClockWidgetsSettings.sizeKey(key)&&(raw.compareTo(BigDecimal.valueOf(BigClockWidgetsSettings.MIN_SIZE))<0
                ||raw.compareTo(BigDecimal.valueOf(BigClockWidgetsSettings.MAX_SIZE))>0))return "组件字号应为 8 到 64 dp";
        if(BigClockWidgetsSettings.offsetKey(key)&&(raw.compareTo(BigDecimal.valueOf(-BigClockWidgetsSettings.MAX_OFFSET))<0
                ||raw.compareTo(BigDecimal.valueOf(BigClockWidgetsSettings.MAX_OFFSET))>0))return "组件位置应为 -1000 到 1000 dp";
        if(NotificationBigClockSettings.REVEAL_MASK_OPACITY.equals(NotificationBigClockSettings.portraitKey(key))
                &&(raw.signum()<0||raw.compareTo(BigDecimal.valueOf(100))>0))return "遮罩不透明度应为 0 到 100%";
        String backdropNumberError=ClockBackdropSettings.numericInputError(key,raw);
        if(backdropNumberError!=null)return backdropNumberError;
        if (StatusBarLayoutSettings.orderKey(key) && (raw.signum() < 0 || raw.stripTrailingZeros().scale() > 0
                || raw.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0)) return "排列顺序应为 0 到 2147483647 的整数";
        if (StatusBarLayoutSettings.orderKey(key) && (double) raw.floatValue() > Integer.MAX_VALUE)
            return "排列顺序过大，请使用较小的序号";
        if (StatusBarLayoutSettings.key(StatusBarLayoutSettings.LABEL, "order").equals(key)
                && raw.compareTo(BigDecimal.valueOf(2)) > 0) return "制式顺序只能是 0 跟随系统、1 信号前或 2 信号后";
        if (NetworkSpeedControls.INTERVAL_MILLIS.equals(key) && raw.signum() <= 0)
            return "刷新间隔应大于 0 ms";
        if (QsTileCorners.RADIUS.equals(key) && (raw.signum() < 0 || raw.compareTo(BigDecimal.valueOf(QsTileCorners.MAX_RADIUS)) > 0))
            return "圆角半径应为 0 到 " + (int) QsTileCorners.MAX_RADIUS + " dp";
        if ((NativeStatusIcons.MAX.equals(key)||NotificationIconArea.MAX_COUNT.equals(key)) && (raw.signum() < 0 || raw.stripTrailingZeros().scale() > 0))
            return "图标数量应为非负整数，0 表示不显示";
        if (NotificationBigClockSettings.VISIBLE_COUNT.equals(NotificationBigClockSettings.portraitKey(key)) && (raw.compareTo(BigDecimal.ONE) < 0
                || raw.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0 || raw.stripTrailingZeros().scale() > 0))
            return "完整通知数量应为 1 到 2147483647 的整数";
        return null;
    }
    /** Suggested bounds control trial confirmation, not valid manual-entry limits. */
    public static boolean requiresNumericTrial(Item item, Number value) {
        if (item == null || value == null || !NUMERIC.equals(item.type)) return false;
        double number = value.doubleValue();
        return Double.isFinite(number) && (number < item.min || number > item.max);
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
            String widgetNumberError=BigClockWidgetsSettings.numericValidationError(item.key,number);
            if(widgetNumberError!=null)return widgetNumberError;
            String backdropNumberError=ClockBackdropSettings.numericValidationError(item.key,number);
            if(backdropNumberError!=null)return backdropNumberError;
            if(NotificationBigClockSettings.REVEAL_MASK_OPACITY.equals(NotificationBigClockSettings.portraitKey(item.key))
                    &&(number<0f||number>100f))return "遮罩不透明度应为 0 到 100%";
            if (StatusBarLayoutSettings.orderKey(item.key) && (number < 0 || number != Math.floor(number)
                    || (double) number > Integer.MAX_VALUE)) return "排列顺序应为非负整数";
            if (StatusBarLayoutSettings.key(StatusBarLayoutSettings.LABEL, "order").equals(item.key) && number > 2)
                return "制式顺序只能是 0、1 或 2";
            if (NetworkSpeedControls.INTERVAL_MILLIS.equals(item.key) && number <= 0) return "刷新间隔应大于 0 ms";
            if (QsTileCorners.RADIUS.equals(item.key) && (number < 0 || number > QsTileCorners.MAX_RADIUS)) return "圆角半径应为 0 到 " + (int) QsTileCorners.MAX_RADIUS + " dp";
            if ((NativeStatusIcons.MAX.equals(item.key)||NotificationIconArea.MAX_COUNT.equals(item.key)) && (number < 0 || number != Math.floor(number))) return "图标数量应为非负整数，0 表示不显示";
            if (NotificationBigClockSettings.VISIBLE_COUNT.equals(NotificationBigClockSettings.portraitKey(item.key)) && (number < 1
                    || number > (float)Integer.MAX_VALUE || number != Math.floor(number))) return "完整通知数量应为 1 到 2147483647 的整数";
            return null;
        }
        if (COLOR.equals(item.type)) return value instanceof Number ? null : "请输入有效颜色";
        if (!(value instanceof String)) return "请输入文字";
        String text = (String) value;
        if(QsWifiAlias.UNIFIED.equals(item.key))return QsWifiAlias.aliasError(text);
        if(QsWifiAlias.RULES.equals(item.key))try{QsWifiAlias.parse(text);return null;}catch(IllegalArgumentException invalid){return invalid.getMessage();}
        if (IconManagementSettings.KEY.equals(item.key)) {
            try { IconManagementSettings.parse(text); return null; }
            catch (java.io.IOException invalid) { return invalid.getMessage(); }
        }
        if(BigClockWidgetsSettings.launchAppKey(item.key))return BigClockWidgetsSettings.launchAppValidationError(text);
        if(BigClockWidgetsSettings.textKey(item.key))return BigClockWidgetsSettings.textValidationError(text);
        if (NotificationIconOverrides.RULES.equals(item.key)) return NotificationIconOverrides.validationError(text);
        if (IconPackAssignments.ASSIGNMENTS.equals(item.key)) return IconPackAssignments.validationError(text);
        if (IconPackRepository.LAYERS.equals(item.key)) {
            try { IconPackRepository.layers(text); return null; }
            catch (java.io.IOException invalid) { return invalid.getMessage(); }
        }
        if (LockscreenControls.DATE_FORMAT.equals(item.key)) return TimeFormat.validationError(text);
        if (NotificationIconArea.TEXT.equals(item.key) && text.codePointCount(0, text.length()) > 12) return "符号或文字最多12个字符";
        if (OPTIONS.equals(item.type)) {
            for (String option : item.values) if (option.equals(text)) return null;
            return "请选择列表中的选项";
        }
        if (item.key.endsWith("_pattern")) return NotificationBigClockSettings.FOOTER_PATTERN.equals(NotificationBigClockSettings.portraitKey(item.key)) ? TimeFormat.contentValidationError(text) : TimeFormat.validationError(text);
        if (STRING.equals(item.type) && (text.length() > TimeFormat.MAX_TEXT || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0)) return "请使用不超过 120 个字符的单行文字";
        return null;
    }
    private SettingsCatalog() { }
}
