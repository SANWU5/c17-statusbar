// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** The customer edition's complete settings boundary, also enforced on boot/fallback reads. */
public final class DabingCustomerPolicy {
    public static final String MASTER = "dabing_enabled";
    private static final String MIGRATED = "dabing_customer_v110_migrated";
    private static final Set<String> ALLOWED = allowedKeys();
    private DabingCustomerPolicy() { }

    private static Set<String> allowedKeys() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(MASTER);
        // The three-way network mode stores its "hidden" state in this retired standalone row.
        keys.add(StatusBarSettings.LABEL_HIDDEN);
        Collections.addAll(keys, NotificationMiniStack.ENABLED, NotificationMiniStack.HIDE_BADGE,
                NotificationMiniStack.AUTO_HIDE_IN_STACK, NotificationBigClockSettings.STACK_ENABLED,
                NotificationBigClockSettings.VISIBLE_COUNT, FeatureOptions.STACK_LANDSCAPE_ENABLED);
        for (String id : new String[]{"network", "data"}) {
            SettingsCatalog.Group group = SettingsCatalog.group(id);
            if (group.masterKey != null) keys.add(group.masterKey);
            for (SettingsCatalog.Item item : group.items) {
                keys.add(item.key);
                if (!item.pairedKey.isEmpty()) keys.add(item.pairedKey);
                if (!item.alphaKey.isEmpty()) keys.add(item.alphaKey);
                if (!item.pairedKey.isEmpty() && SettingsCatalog.COLOR.equals(item.type))
                    keys.add(StatusBarSettings.alphaKey(item.pairedKey));
            }
        }
        return Collections.unmodifiableSet(keys);
    }
    public static boolean allowed(String key) { return ALLOWED.contains(key); }

    /** Defaults for unrelated features are explicitly off, including their independent switches. */
    static Map<String,Object> restrict(Map<String,?> supplied) {
        Map<String,Object> values = new LinkedHashMap<>();
        values.putAll(StatusBarSettings.NUMERIC_DEFAULTS);
        values.putAll(StatusBarSettings.COLOR_DEFAULTS);
        for (String key : StatusBarSettings.COLOR_DEFAULTS.keySet())
            values.put(StatusBarSettings.alphaKey(key), false);
        values.putAll(StatusBarSettings.STRING_DEFAULTS);
        for (String key : StatusBarSettings.BOOLEAN_DEFAULTS.keySet())
            values.put(key, allowed(key) ? StatusBarSettings.BOOLEAN_DEFAULTS.get(key) : false);
        values.put(MASTER, true);
        values.put(NotificationMiniStack.ENABLED, true);
        values.put(NotificationMiniStack.AUTO_HIDE_IN_STACK, true);
        values.put(NotificationBigClockSettings.STACK_ENABLED, true);
        values.put(NotificationBigClockSettings.VISIBLE_COUNT, 1);
        if (supplied != null) for (String key : ALLOWED)
            if (supplied.containsKey(key)) values.put(key, supplied.get(key));
        Object count = values.get(NotificationBigClockSettings.VISIBLE_COUNT);
        long n = count instanceof Number ? ((Number) count).longValue() : 1;
        values.put(NotificationBigClockSettings.VISIBLE_COUNT, (int)Math.max(1L, Math.min(Integer.MAX_VALUE, n)));
        values.put(StatusBarSettings.SAFE_MODE, !Boolean.TRUE.equals(values.get(MASTER)));
        return values;
    }

    /** Never trust a last-good/framework/plugin snapshot to widen the customer's feature set. */
    static Bundle runtime(Bundle supplied) {
        Map<String,Object> source = new LinkedHashMap<>();
        if (supplied != null) for (String key : supplied.keySet()) source.put(key, supplied.get(key));
        source.put(MASTER, supplied != null && !supplied.getBoolean(StatusBarSettings.SAFE_MODE, false));
        Map<String,Object> restricted = restrict(source);
        Bundle result = new Bundle();
        for (Map.Entry<String,Object> entry : restricted.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof Boolean) result.putBoolean(key, (Boolean)value);
            else if (value instanceof Number && StatusBarSettings.NUMERIC_DEFAULTS.containsKey(key)
                    && !NotificationBigClockSettings.VISIBLE_COUNT.equals(key))
                result.putFloat(key, ((Number)value).floatValue());
            else if (value instanceof Integer) result.putInt(key, (Integer)value);
            else if (value instanceof Number) result.putFloat(key, ((Number)value).floatValue());
            else if (value instanceof String) result.putString(key, (String)value);
        }
        return result;
    }

    public static Map<String,Object> values(Context context) {
        SharedPreferences preferences = StatusBarSettings.preferences(context);
        migrate(context, preferences);
        return restrict(PreferenceWrites.values(preferences));
    }

    private static void migrate(Context context, SharedPreferences current) {
        if (current.getBoolean(MIGRATED, false)) return;
        SharedPreferences old = context.createDeviceProtectedStorageContext()
                .getSharedPreferences("dabing_stack", Context.MODE_PRIVATE);
        Map<String,?> previous = old.getAll();
        Map<String,Object> changes = new LinkedHashMap<>();
        String[][] names = {{"enabled", MASTER}, {"mini", NotificationMiniStack.ENABLED},
                {"large", NotificationBigClockSettings.STACK_ENABLED}, {"count", NotificationBigClockSettings.VISIBLE_COUNT},
                {"hide_badge", NotificationMiniStack.HIDE_BADGE}, {"auto_hide", NotificationMiniStack.AUTO_HIDE_IN_STACK},
                {"landscape", FeatureOptions.STACK_LANDSCAPE_ENABLED}};
        for (String[] pair : names) if (!current.contains(pair[1]) && previous.containsKey(pair[0])) {
            Object value = previous.get(pair[0]);
            if ("count".equals(pair[0]) ? value instanceof Integer && (Integer)value > 0 : value instanceof Boolean)
                changes.put(pair[1], value);
        }
        changes.put(MIGRATED, true);
        commit(context, current, changes, false);
    }

    public static boolean set(Context context, String key, Object value) {
        if (!allowed(key) || !valid(key, value)) return false;
        Map<String,Object> changes = new LinkedHashMap<>();
        if (value instanceof Number && StatusBarSettings.NUMERIC_DEFAULTS.containsKey(key)
                && !NotificationBigClockSettings.VISIBLE_COUNT.equals(key)) value = ((Number)value).floatValue();
        changes.put(key, value);
        if (Boolean.TRUE.equals(value)) {
            String partner = NetworkLabelFormats.turnsOff(key);
            if (partner != null && allowed(partner)) changes.put(partner, false);
        }
        return commit(context, StatusBarSettings.preferences(context), changes, false);
    }

    private static boolean valid(String key, Object value) {
        if (MASTER.equals(key)) return value instanceof Boolean;
        if (NotificationBigClockSettings.VISIBLE_COUNT.equals(key)) return value instanceof Integer && (Integer)value > 0;
        if (StatusBarSettings.BOOLEAN_DEFAULTS.containsKey(key) || key.endsWith("_custom_alpha")) return value instanceof Boolean;
        if (StatusBarSettings.COLOR_DEFAULTS.containsKey(key)) return value instanceof Integer;
        if (StatusBarSettings.NUMERIC_DEFAULTS.containsKey(key)) {
            if (!(value instanceof Number) || !Double.isFinite(((Number)value).doubleValue())) return false;
        } else if (StatusBarSettings.STRING_DEFAULTS.containsKey(key) && !(value instanceof String)) return false;
        SettingsCatalog.Item item = SettingsCatalog.item(key);
        return item != null && SettingsCatalog.validationError(item, value) == null;
    }

    public static boolean setNetworkMode(Context context, String mode) {
        if (!NetworkDisplaySettings.validMode(mode)) return false;
        Map<String,Object> changes = new LinkedHashMap<>();
        changes.put("label_enabled", !NetworkDisplaySettings.BADGE.equals(mode));
        changes.put(StatusBarSettings.LABEL_HIDDEN, NetworkDisplaySettings.HIDDEN.equals(mode));
        return commit(context, StatusBarSettings.preferences(context), changes, false);
    }

    public static boolean reset(Context context) {
        Map<String,Object> defaults = new LinkedHashMap<>(); defaults.put(MIGRATED, true);
        return commit(context, StatusBarSettings.preferences(context), defaults, true);
    }

    private static boolean commit(Context context, SharedPreferences preferences, Map<String,?> changes, boolean clear) {
        SharedPreferences.Editor editor = preferences.edit();
        if (clear) editor.clear();
        Map<String,Object> copied = new LinkedHashMap<>();
        for (Map.Entry<String,?> change : changes.entrySet()) {
            copied.put(change.getKey(), change.getValue());
            StatusBarSettings.copyPreference(editor, change.getKey(), change.getValue());
        }
        if (!PreferenceWrites.commit(preferences, editor, copied, clear)) return false;
        SettingsSnapshot.scheduleSave(context, preferences, null);
        context.getContentResolver().notifyChange(Uri.parse(StatusBarSettings.CONTENT_URI), null);
        return true;
    }
}
