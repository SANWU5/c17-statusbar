// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserManager;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Exercises the customer's persistence/runtime boundary and the actual shared stack consumer. */
public final class DabingCustomerPolicyCheck {
    private static int checks;
    private static void check(boolean value, String why) {
        checks++; if (!value) throw new AssertionError(why);
    }
    private static void equal(Object expected, Object actual, String why) {
        check(Objects.equals(expected, actual), why + ": " + expected + " != " + actual);
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static Map<String,Object> map(Bundle source) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (String key : source.keySet()) result.put(key, source.get(key)); return result;
    }
    private static final class Storage extends Context {
        final Map<String,ActivationGuardPreferencesCheck.MemoryPreferences> stores = new LinkedHashMap<>();
        final ContentResolver resolver = new ContentResolver();
        final UserManager user = new UserManager();
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return stores.computeIfAbsent(name, ignored -> new ActivationGuardPreferencesCheck.MemoryPreferences());
        }
        @Override public Object getSystemService(String name) { return "user".equals(name) ? user : super.getSystemService(name); }
        @Override public ContentResolver getContentResolver() { return resolver; }
        // The unrelated identity avoids starting durable snapshot background work in this isolated memory fixture.
        @Override public String getPackageName() { return "dev.puitheme.customer.check.fixture"; }
    }

    private static void destinationsAndWrites() {
        equal("content://" + ModuleVariant.PACKAGE + ".settings/values", StatusBarSettings.CONTENT_URI,
                "settings reads use the actual independently installed module identity");
        equal(ModuleVariant.PACKAGE + ".settings", StatusBarSettings.AUTHORITY,
                "provider authority matches the installed package");
        for (String group : new String[]{"network", "data"}) {
            SettingsCatalog.Group page = SettingsCatalog.group(group);
            check(page != null, "retained destination exists: " + group);
            for (SettingsCatalog.Item item : page.items) {
                check(DabingCustomerPolicy.allowed(item.key), "retained network control: " + item.key);
                if (!item.pairedKey.isEmpty())
                    check(DabingCustomerPolicy.allowed(item.pairedKey), "paired theme color remains editable");
                if (!item.alphaKey.isEmpty())
                    check(DabingCustomerPolicy.allowed(item.alphaKey), "network opacity mode remains editable");
            }
        }
        for (String key : new String[]{DabingCustomerPolicy.MASTER, NotificationMiniStack.ENABLED,
                NotificationMiniStack.HIDE_BADGE, NotificationMiniStack.AUTO_HIDE_IN_STACK,
                NotificationBigClockSettings.STACK_ENABLED, NotificationBigClockSettings.VISIBLE_COUNT,
                FeatureOptions.STACK_LANDSCAPE_ENABLED})
            check(DabingCustomerPolicy.allowed(key), "only retained independent stack controls are writable");
        for (String key : new String[]{StatusBarSettings.CLOCK_ENABLED, NotificationBigClockSettings.MASTER,
                "battery_enabled", "speed_enabled", "font_enabled", "tiles_enabled", FluidCloudStyle.MASTER,
                StatusBarLayoutSettings.MASTER, NotificationGroupStack.MASTER, "custom_plugin_enabled",
                "wifi_unregistered_injected_key", null}) {
            check(!DabingCustomerPolicy.allowed(key), "hidden or unknown key is denied: " + key);
            check(!DabingCustomerPolicy.set(null, key, true), "denied write stops before touching storage");
        }
        check(!DabingCustomerPolicy.set(null, DabingCustomerPolicy.MASTER, "true"), "master must be a boolean");
        check(!DabingCustomerPolicy.set(null, "wifi_enabled", "true"), "network toggle must be a boolean");
        check(!DabingCustomerPolicy.set(null, NotificationBigClockSettings.VISIBLE_COUNT, 0), "zero count is rejected");
        check(!DabingCustomerPolicy.set(null, NotificationBigClockSettings.VISIBLE_COUNT, -1), "negative count is rejected");
        check(!DabingCustomerPolicy.set(null, NotificationBigClockSettings.VISIBLE_COUNT, 20f), "count storage must retain integer precision");
        check(!DabingCustomerPolicy.set(null, StatusBarSettings.WIFI_ICON_SCALE, Float.NaN), "nonfinite scale is rejected");
        check(!DabingCustomerPolicy.setNetworkMode(null, "unknown"), "unknown display mode cannot edit either coupled key");
    }

    private static void networkModesAndTransactions() {
        check(DabingCustomerPolicy.allowed(StatusBarSettings.LABEL_HIDDEN), "the hidden display mode has a permitted storage key");
        for (String mode : new String[]{NetworkDisplaySettings.TEXT, NetworkDisplaySettings.BADGE, NetworkDisplaySettings.HIDDEN}) {
            Map<String,Object> saved = new LinkedHashMap<>();
            saved.put("label_enabled", !NetworkDisplaySettings.BADGE.equals(mode));
            saved.put(StatusBarSettings.LABEL_HIDDEN, NetworkDisplaySettings.HIDDEN.equals(mode));
            equal(mode, NetworkDisplaySettings.mode(DabingCustomerPolicy.restrict(saved)), "stored three-way mode survives restriction");
            Bundle source = new Bundle(); source.putBoolean("label_enabled", (Boolean)saved.get("label_enabled"));
            source.putBoolean(StatusBarSettings.LABEL_HIDDEN, (Boolean)saved.get(StatusBarSettings.LABEL_HIDDEN));
            equal(mode, NetworkDisplaySettings.mode(DabingCustomerPolicy.runtime(source)), "runtime keeps the exact selected three-way mode");
        }
        Storage storage = new Storage();
        DabingCustomerPolicy.values(storage);
        // Settle the normal storage migrations before measuring a single deliberate UI edit.
        StatusBarSettings.preferences(storage); StatusBarSettings.preferences(storage);
        ActivationGuardPreferencesCheck.MemoryPreferences raw = storage.stores.get(StatusBarSettings.PREFS);
        for (String mode : new String[]{NetworkDisplaySettings.HIDDEN, NetworkDisplaySettings.BADGE, NetworkDisplaySettings.TEXT}) {
            int writes = raw.writes, notifications = storage.resolver.notifications;
            check(DabingCustomerPolicy.setNetworkMode(storage, mode), "three-way display edit saves successfully");
            equal(writes + 1, raw.writes, "mode's coupled keys use one preference transaction");
            equal(notifications + 1, storage.resolver.notifications, "mode publishes only the complete pair");
            equal(mode, NetworkDisplaySettings.mode(PreferenceWrites.values(raw)), "persisted pair exactly matches selection");
        }
        for (String enabled : new String[]{NetworkLabelFormats.ENABLED, NetworkLabelFormats.UNIFY}) {
            String disabled = NetworkLabelFormats.turnsOff(enabled);
            raw.values.put(enabled, false); raw.values.put(disabled, true);
            int writes = raw.writes, notifications = storage.resolver.notifications;
            check(DabingCustomerPolicy.set(storage, enabled, true), "network content option saves successfully");
            equal(writes + 1, raw.writes, "mutually exclusive network options are saved atomically");
            equal(notifications + 1, storage.resolver.notifications, "mutually exclusive edit publishes once");
            equal(true, raw.values.get(enabled), "selected content option is enabled");
            equal(false, raw.values.get(disabled), "incompatible content option is disabled in the same save");
        }
    }

    private static void hostileSnapshots() {
        Bundle source = new Bundle();
        for (String key : StatusBarSettings.BOOLEAN_DEFAULTS.keySet()) source.putBoolean(key, true);
        for (String key : StatusBarSettings.NUMERIC_DEFAULTS.keySet()) source.putFloat(key, 713.25f);
        for (String key : StatusBarSettings.COLOR_DEFAULTS.keySet()) {
            source.putInt(key, 0x7f123456); source.putBoolean(StatusBarSettings.alphaKey(key), true);
        }
        for (String key : StatusBarSettings.STRING_DEFAULTS.keySet()) source.putString(key, "injected-value");
        source.putBoolean(StatusBarSettings.SAFE_MODE, false);
        source.putBoolean("unknown_feature_enabled", true);
        source.putBoolean("wifi_unregistered_injected_key", true);
        source.putFloat(StatusBarSettings.WIFI_OFFSET_Y, -7.25f);
        source.putFloat(StatusBarSettings.DATA_ICON_SCALE, 137.5f);
        source.putString(StatusBarSettings.SIGNAL_LAYOUT, "single");
        source.putInt(NotificationBigClockSettings.VISIBLE_COUNT, 16777217);
        Map<String,Object> before = map(source);
        Bundle projected = DabingCustomerPolicy.runtime(source);
        equal(before, map(source), "filtering never modifies the saved/framework source snapshot");
        check(!projected.containsKey("unknown_feature_enabled"), "unknown feature cannot enter runtime");
        check(!projected.containsKey("wifi_unregistered_injected_key"), "a network-looking prefix does not grant access");
        for (String key : StatusBarSettings.BOOLEAN_DEFAULTS.keySet()) {
            if (DabingCustomerPolicy.allowed(key))
                equal(true, projected.get(key), "permitted network/stack switch survives: " + key);
            else
                equal(false, projected.get(key), "hidden switch forced off despite hostile snapshot: " + key);
        }
        for (String key : StatusBarSettings.NUMERIC_DEFAULTS.keySet()) if (!DabingCustomerPolicy.allowed(key))
            equal(StatusBarSettings.NUMERIC_DEFAULTS.get(key), projected.get(key), "hidden geometry resets to baseline: " + key);
        for (String key : StatusBarSettings.COLOR_DEFAULTS.keySet()) {
            if (!DabingCustomerPolicy.allowed(key))
                equal(StatusBarSettings.COLOR_DEFAULTS.get(key), projected.get(key), "hidden custom color discarded: " + key);
            equal(DabingCustomerPolicy.allowed(StatusBarSettings.alphaKey(key)), projected.get(StatusBarSettings.alphaKey(key)),
                    "every color has a complete bounded alpha mode: " + key);
        }
        for (String key : StatusBarSettings.STRING_DEFAULTS.keySet()) if (!DabingCustomerPolicy.allowed(key))
            equal(StatusBarSettings.STRING_DEFAULTS.get(key), projected.get(key), "hidden pattern/font/asset path discarded: " + key);
        equal(-7.25f, projected.get(StatusBarSettings.WIFI_OFFSET_Y), "Wi-Fi position is retained");
        equal(137.5f, projected.get(StatusBarSettings.DATA_ICON_SCALE), "cellular scale is retained");
        equal("single", projected.get(StatusBarSettings.SIGNAL_LAYOUT), "cellular signal choice is retained");
        equal(16777217, projected.get(NotificationBigClockSettings.VISIBLE_COUNT), "hostile snapshots still preserve the selected exact count");
        equal(map(projected), map(DabingCustomerPolicy.runtime(projected)), "runtime restriction is stable under repeated reads");
        if (ModuleVariant.DABING)
            check(SettingsSnapshot.complete(projected), "customer runtime remains a complete usable snapshot after filtering");
    }

    private static void countsAndMaster() throws Exception {
        for (int count : new int[]{1, 6, 20, 100, 16777217, Integer.MAX_VALUE}) {
            Map<String,Object> saved = new LinkedHashMap<>();
            saved.put(NotificationBigClockSettings.VISIBLE_COUNT, count);
            Map<String,Object> restricted = DabingCustomerPolicy.restrict(saved);
            equal(count, restricted.get(NotificationBigClockSettings.VISIBLE_COUNT), "saved count has no feature cap or float rounding");
            Bundle source = new Bundle(); source.putInt(NotificationBigClockSettings.VISIBLE_COUNT, count);
            source.putBoolean(NotificationBigClockSettings.STACK_ENABLED, true);
            source.putBoolean(NotificationBigClockSettings.MASTER, false);
            Bundle runtime = DabingCustomerPolicy.runtime(source);
            equal(count, runtime.get(NotificationBigClockSettings.VISIBLE_COUNT), "runtime transports count as exact Integer");
            NotificationNativeStack stack = new NotificationNativeStack(); stack.configureIndependent(runtime);
            equal(count, field(stack, "completeCount"), "actual production stack consumes exact requested count");
            equal(true, field(stack, "enabled"), "large stack can operate while the hidden big clock remains off");
            equal(false, runtime.getBoolean(NotificationBigClockSettings.MASTER), "large stack never turns on the big clock");
            if (ModuleVariant.DABING) {
                Bundle snapshot = SettingsSnapshot.fromPreferences(saved);
                check(snapshot != null && SettingsSnapshot.complete(snapshot), "integer count does not invalidate the provider snapshot");
                equal(count, snapshot.get(NotificationBigClockSettings.VISIBLE_COUNT), "provider snapshot preserves the exact stored count");
                ActivationGuardPreferencesCheck.MemoryPreferences mirror = new ActivationGuardPreferencesCheck.MemoryPreferences();
                check(SettingsFrameworkMirror.write(mirror, snapshot, 1L), "complete customer snapshot is accepted by the real mirror writer");
                equal(count, mirror.values.get(NotificationBigClockSettings.VISIBLE_COUNT), "framework storage preserves Integer counts without converting to float");
                Bundle decoded = SettingsFrameworkMirror.decode(mirror.getAll());
                check(decoded != null && SettingsSnapshot.complete(decoded), "framework read returns a complete customer snapshot");
                equal(count, decoded.get(NotificationBigClockSettings.VISIBLE_COUNT), "framework write/decode round-trip preserves the exact count");
                mirror.values.put(NotificationBigClockSettings.VISIBLE_COUNT, 0);
                equal(null, SettingsFrameworkMirror.decode(mirror.getAll()), "framework reader rejects invalid integer count");
            }
        }
        for (boolean enabled : new boolean[]{false, true}) {
            Map<String,Object> saved = new LinkedHashMap<>(); saved.put(DabingCustomerPolicy.MASTER, enabled);
            equal(!enabled, DabingCustomerPolicy.restrict(saved).get(StatusBarSettings.SAFE_MODE), "customer master maps to native safety gate");
            Bundle source = new Bundle(); source.putBoolean(StatusBarSettings.SAFE_MODE, !enabled);
            source.putBoolean(NotificationBigClockSettings.STACK_ENABLED, true);
            source.putBoolean("wifi_enabled", true); source.putBoolean(NetworkDisplaySettings.WIFI_HIDDEN, true);
            Bundle runtime = DabingCustomerPolicy.runtime(source);
            equal(!enabled, runtime.getBoolean(StatusBarSettings.SAFE_MODE), "framework safety gate survives repeated restriction");
            equal(enabled, FeatureOptions.from(runtime).enabled("wifi"), "master off relinquishes network customization");
            equal(enabled, FeatureOptions.from(runtime).hideWifiIcon(), "master off relinquishes independent Wi-Fi hiding");
            NotificationNativeStack stack = new NotificationNativeStack(); stack.configureIndependent(runtime);
            equal(enabled, field(stack, "enabled"), "master off relinquishes the independent large stack");
        }
        Map<String,Object> defaults = DabingCustomerPolicy.restrict(Collections.emptyMap());
        equal(true, defaults.get(NotificationMiniStack.ENABLED), "fresh edition enables small stack");
        equal(true, defaults.get(NotificationBigClockSettings.STACK_ENABLED), "fresh edition enables large stack");
        equal(1, defaults.get(NotificationBigClockSettings.VISIBLE_COUNT), "fresh edition shows one complete notification");
        equal(false, defaults.get(FeatureOptions.STACK_LANDSCAPE_ENABLED), "landscape remains explicit opt-in");
        check(DabingCustomerPolicy.runtime(null).getBoolean(StatusBarSettings.SAFE_MODE), "missing runtime settings keep the module paused");
    }

    public static void main(String[] args) throws Exception {
        destinationsAndWrites(); networkModesAndTransactions(); hostileSnapshots(); countsAndMaster();
        System.out.println("DabingCustomerPolicyCheck: " + checks + " checks passed; customer integration=" + ModuleVariant.DABING);
    }
}
