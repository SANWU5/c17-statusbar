// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** App/runtime protocol for physical left/right gaps and native status-bar element placement. */
public final class StatusBarLayoutSettings {
    public static final String MASTER = "statusbar_layout_enabled", GROUP = "statusbar_layout";
    public static final String WIFI_LABEL_SHARED = "statusbar_wifi_label_shared_position";
    public static final String NATIVE = "native", LEFT = "left", RIGHT = "right";
    public static final String CLOCK = "clock", NOTIFICATION = "notification", CARRIER = "carrier", SPEED = "speed";
    public static final String WIFI = "wifi", MOBILE = "mobile", BATTERY = "battery", SYSTEM = "system", LABEL = "label";
    public static final List<String> TOKENS = Collections.unmodifiableList(Arrays.asList(
            CLOCK, NOTIFICATION, CARRIER, SPEED, WIFI, MOBILE, BATTERY, SYSTEM));
    public static final Map<String, Boolean> BOOLEANS;
    public static final Map<String, Float> NUMBERS;
    public static final Map<String, String> STRINGS;
    static {
        Map<String, Boolean> switches = new LinkedHashMap<>();
        switches.put(MASTER, false); switches.put(WIFI_LABEL_SHARED, false);
        BOOLEANS = Collections.unmodifiableMap(switches);
        Map<String, Float> numbers = new LinkedHashMap<>();
        Map<String, String> strings = new LinkedHashMap<>();
        for (String token : TOKENS) {
            strings.put(key(token, "side"), NATIVE);
            numbers.put(key(token, "order"), 0f);
            numbers.put(key(token, "margin_left"), 0f);
            numbers.put(key(token, "margin_right"), 0f);
        }
        numbers.put(key(LABEL, "order"), 0f);
        numbers.put(key(LABEL, "margin_left"), 0f);
        numbers.put(key(LABEL, "margin_right"), 0f);
        NUMBERS = Collections.unmodifiableMap(numbers);
        STRINGS = Collections.unmodifiableMap(strings);
    }
    private StatusBarLayoutSettings() { }

    /** label is an inner mobile-network element and deliberately has no side setting. */
    public static String key(String token, String suffix) {
        boolean outer = TOKENS.contains(token), inner = LABEL.equals(token);
        if ((!outer && !inner) || !("order".equals(suffix) || "margin_left".equals(suffix)
                || "margin_right".equals(suffix) || outer && "side".equals(suffix)))
            throw new IllegalArgumentException("Unknown status-bar layout field");
        return "statusbar_layout_" + token + "_" + suffix;
    }
    public static boolean owns(String key) {
        return BOOLEANS.containsKey(key) || NUMBERS.containsKey(key) || STRINGS.containsKey(key);
    }
    public static boolean orderKey(String key) { return NUMBERS.containsKey(key) && key.endsWith("_order"); }
    public static boolean marginKey(String key) { return NUMBERS.containsKey(key) && !orderKey(key); }
    public static boolean sideKey(String key) { return STRINGS.containsKey(key); }
    public static boolean validSide(Object value) { return NATIVE.equals(value) || LEFT.equals(value) || RIGHT.equals(value); }
    /** Editor aliases never overwrite the label's independently saved position. */
    public static String editingKey(String key, Map<String, ?> values) {
        if (values != null && Boolean.TRUE.equals(values.get(WIFI_LABEL_SHARED))) {
            if (key(LABEL, "margin_left").equals(key)) return key(WIFI, "margin_left");
            if (key(LABEL, "margin_right").equals(key)) return key(WIFI, "margin_right");
            if (StatusBarSettings.LABEL_OFFSET_X.equals(key)) return StatusBarSettings.WIFI_OFFSET_X;
            if (StatusBarSettings.LABEL_OFFSET_Y.equals(key)) return StatusBarSettings.WIFI_OFFSET_Y;
            if ("label_position_enabled".equals(key)) return "wifi_position_enabled";
        }
        return key;
    }
    /** Position only: feature masters, size switches and scales remain independent. */
    public static boolean sharedPositionKey(String key) {
        return StatusBarSettings.WIFI_OFFSET_X.equals(key) || StatusBarSettings.LABEL_OFFSET_X.equals(key)
                || StatusBarSettings.WIFI_OFFSET_Y.equals(key) || StatusBarSettings.LABEL_OFFSET_Y.equals(key)
                || "wifi_position_enabled".equals(key) || "label_position_enabled".equals(key)
                || key(WIFI,"margin_left").equals(key) || key(WIFI,"margin_right").equals(key)
                || key(LABEL,"margin_left").equals(key) || key(LABEL,"margin_right").equals(key);
    }
    public static String label(String token) {
        if (CLOCK.equals(token)) return "时间";
        if (NOTIFICATION.equals(token)) return "通知图标";
        if (CARRIER.equals(token)) return "运营商";
        if (SPEED.equals(token)) return "网速";
        if (WIFI.equals(token)) return "Wi-Fi";
        if (MOBILE.equals(token)) return "蜂窝信号";
        if (BATTERY.equals(token)) return "电池";
        if (SYSTEM.equals(token)) return "系统提示";
        if (LABEL.equals(token)) return "网络制式";
        throw new IllegalArgumentException("Unknown status-bar element");
    }
    /** The saved master controls region/order only; margins have no additional switch. */
    public static boolean orderingEnabled(Bundle values) {
        return values != null && Boolean.TRUE.equals(values.get(MASTER))
                && !Boolean.TRUE.equals(values.get(StatusBarSettings.SAFE_MODE));
    }
    /** Any finite nonzero physical margin requests native flow, even with sorting off. */
    public static boolean enabled(Bundle values) {
        if (values == null || Boolean.TRUE.equals(values.get(StatusBarSettings.SAFE_MODE))) return false;
        if (orderingEnabled(values) || Boolean.TRUE.equals(values.get(WIFI_LABEL_SHARED))) return true;
        for (String key : NUMBERS.keySet()) if (marginKey(key) && margin(values.get(key)) != 0f) return true;
        return false;
    }
    /** Map equivalent used by the settings editor; never seeds missing values or writes preferences. */
    public static boolean enabledValues(Map<String, ?> values) {
        if (values == null || Boolean.TRUE.equals(values.get(StatusBarSettings.SAFE_MODE))) return false;
        if (Boolean.TRUE.equals(values.get(MASTER)) || Boolean.TRUE.equals(values.get(WIFI_LABEL_SHARED))) return true;
        for (String key : NUMBERS.keySet()) if (marginKey(key) && margin(values.get(key)) != 0f) return true;
        return false;
    }
    private static float margin(Object value) { return NumericPolicy.finite(value, 0f); }
    /** Suspend old outer drawing-X paths in an isolated effective copy; never alter persisted data. */
    public static Bundle runtime(Bundle raw) {
        Bundle result = raw == null ? new Bundle() : new Bundle(raw);
        if (!enabled(raw)) return result;
        if (raw.getBoolean(WIFI_LABEL_SHARED, false)) {
            // Share only effective position. Each group's own master still
            // gates that effect; disabling Wi-Fi customization cannot stop
            // an independently enabled label from using the shared position.
            result.putBoolean("label_position_enabled", raw.getBoolean("wifi_position_enabled", true));
            result.putFloat(StatusBarSettings.LABEL_OFFSET_Y, NumericPolicy.finite(raw.get(StatusBarSettings.WIFI_OFFSET_Y), 0f));
        }
        for (String key : new String[]{StatusBarSettings.CLOCK_OFFSET_X, ClockLayoutSettings.DUAL_OFFSET_X,
                StatusBarSettings.CARRIER_OFFSET_X, StatusBarSettings.BATTERY_OFFSET_X,
                StatusBarSettings.SPEED_OFFSET_X, StatusBarSettings.WIFI_OFFSET_X,
                StatusBarSettings.DATA_OFFSET_X, StatusBarSettings.LABEL_OFFSET_X,
                NotificationIconArea.X, NativeStatusIcons.X}) result.putFloat(key, 0f);
        result.putBoolean(ClockLayoutSettings.MARGIN_ENABLED, false);
        result.putBoolean(SpeedPosition.SAFE_GAP_ENABLED, false);
        result.putBoolean(NetworkIconOrder.SWAP, false);
        result.putBoolean(DataBatterySpacing.ENABLED, false);
        // The flow reserves hint spacing as real width. The old target writer
        // must not apply it a second time after our native animation target.
        result.putFloat(NativeStatusIcons.SPACING, 0f);
        // The old speed placement state is gated inside SpeedPosition, keeping its Y switch alive.
        return result;
    }
    /** Immutable parsed values. Renderers still resolve the native host, visibility and density. */
    public static final class Snapshot {
        public final boolean enabled, orderingEnabled, wifiLabelShared;
        /**
         * Fixed signal/Wi-Fi width (dp) held right of the network speed, or 0. The flow owns the
         * speed's X once any margin is set. This keeps the code130 radio envelope while retaining
         * explicit margins; wider radios and insufficient capacity still use the ordinary flow.
         */
        public final float speedRadioReserve;
        private final float sharedX;
        private final NativeStatusIconLayout.Snapshot hintLayout;
        private final String[] sides;
        private final int[] orders;
        private final float[] left, right;
        private Snapshot(Bundle values) {
            orderingEnabled = StatusBarLayoutSettings.orderingEnabled(values);
            Object hintSource=value(values,NativeStatusIcons.LAYOUT);
            hintLayout=NativeStatusIconLayout.parse(hintSource instanceof String?(String)hintSource:NativeStatusIconLayout.DEFAULT);
            boolean safe = values != null && Boolean.TRUE.equals(values.get(StatusBarSettings.SAFE_MODE));
            wifiLabelShared = !safe && values != null && Boolean.TRUE.equals(values.get(WIFI_LABEL_SHARED));
            sharedX = wifiLabelShared && values.getBoolean("wifi_position_enabled",true)
                    ? NumericPolicy.finite(values.get(StatusBarSettings.WIFI_OFFSET_X),0f) : 0f;
            // The flow packs the speed beside the radios like the native bar. A reserve kept
            // after it while Wi-Fi or data was missing showed as an empty gap (49dp before both
            // had been seen), and it was gated by the speed position switch, which in flow mode
            // is the vertical-only switch.
            speedRadioReserve = 0f;
            boolean hasMargin = false;
            sides = new String[TOKENS.size()];
            orders = new int[TOKENS.size() + 1];
            left = new float[orders.length]; right = new float[orders.length];
            for (int index = 0; index < orders.length; index++) {
                String token = index == TOKENS.size() ? LABEL : TOKENS.get(index);
                if (index < sides.length) {
                    Object side = value(values, key(token, "side"));
                    sides[index] = validSide(side) ? (String) side : NATIVE;
                }
                Object rawOrder = value(values, key(token, "order"));
                double order = rawOrder instanceof Number ? ((Number) rawOrder).doubleValue() : 0d;
                orders[index] = Double.isFinite(order) && order >= 0d && order <= Integer.MAX_VALUE
                        && order == Math.floor(order) && (!LABEL.equals(token) || order <= 2d) ? (int) order : 0;
                left[index] = safe ? 0f : margin(value(values, key(token, "margin_left")));
                right[index] = safe ? 0f : margin(value(values, key(token, "margin_right")));
                hasMargin |= left[index] != 0f || right[index] != 0f;
            }
            enabled = orderingEnabled || hasMargin || wifiLabelShared;
        }
        private Snapshot(Snapshot source, boolean ordering) {
            orderingEnabled = ordering && source.orderingEnabled; wifiLabelShared = source.wifiLabelShared;
            sharedX = source.sharedX; speedRadioReserve = source.speedRadioReserve;
            hintLayout=source.hintLayout;
            sides = source.sides; orders = source.orders; left = source.left; right = source.right;
            boolean hasMargin = false;
            for (int index = 0; index < left.length; index++) hasMargin |= left[index] != 0f || right[index] != 0f;
            enabled = orderingEnabled || hasMargin || wifiLabelShared;
        }
        public static Snapshot parse(Bundle values) { return new Snapshot(values); }
        boolean sameGeometry(Snapshot other){
            return other!=null&&enabled==other.enabled&&orderingEnabled==other.orderingEnabled&&wifiLabelShared==other.wifiLabelShared
                    &&Float.compare(sharedX,other.sharedX)==0&&Float.compare(speedRadioReserve,other.speedRadioReserve)==0
                    &&java.util.Arrays.equals(sides,other.sides)
                    &&java.util.Arrays.equals(orders,other.orders)&&java.util.Arrays.equals(left,other.left)&&java.util.Arrays.equals(right,other.right)
                    &&hintLayout.serialize().equals(other.hintLayout.serialize());
        }
        /** Margins (and the shared Wi-Fi slot) without region/order: used while a fluid-cloud
         * capsule occupies the bar, whose native hiding follows the native sides and order. */
        public Snapshot withoutOrdering() { return orderingEnabled ? new Snapshot(this, false) : this; }
        /** The scene adapter resolves one actual shared slot, rather than two equal coordinate values. */
        public String slotToken(String token) { return wifiLabelShared && (WIFI.equals(token) || LABEL.equals(token)) ? WIFI : token; }
        public String side(String token) {
            int index = TOKENS.indexOf(token);
            return !orderingEnabled || index < 0 ? NATIVE : sides[index];
        }
        public boolean hintLayoutActive(){return orderingEnabled&&!hintLayout.empty();}
        public boolean hintSpecified(String slot){return hintLayoutActive()&&hintLayout.specified(slot);}
        public String hintSide(String slot){return hintSpecified(slot)?hintLayout.side(slot):NATIVE;}
        public int hintOrder(String slot){return hintSpecified(slot)?hintLayout.order(slot):0;}
        public int order(String token) { int index = index(token); return !orderingEnabled || index < 0 ? 0 : orders[index]; }
        public float left(String token) { int index = index(token); return index < 0 ? 0f : left[index]; }
        public float right(String token) { int index = index(token); return index < 0 ? 0f : right[index]; }
        public float offsetX(String token) { return WIFI.equals(token)?sharedX:0f; }
        private int index(String token) { return LABEL.equals(token) ? TOKENS.size() : TOKENS.indexOf(token); }
        private static Object value(Bundle values, String key) { return values == null ? null : values.get(key); }
    }
}
