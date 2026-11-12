package dev.puitheme;

import android.os.Bundle;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Schema 26: the big clock's "no rebound" and the lock-screen glass style (portrait and landscape), the rebound clamp itself, and
 * where the new items sit in the regrouped big-clock pages.
 */
public final class Upgrade117Check {
    private static int checks;
    private static void equal(Object expected, Object actual, String reason) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Upgrade117 " + checks + " " + reason + ": " + expected + " != " + actual);
    }
    private static void check(boolean value, String reason) { equal(true, value, reason); }
    private static Map<String, Object> wire(Bundle snapshot, int schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : snapshot.keySet()) result.put(key, snapshot.get(key));
        if(schema<37)for(String key:Upgrade169.DEFAULTS.keySet())result.remove(key); if(schema<38)for(String key:Upgrade171.DEFAULTS.keySet())result.remove(key); if(schema<39)for(String key:Upgrade172.DEFAULTS.keySet())result.remove(key); if(schema<40)for(String key:Upgrade173.DEFAULTS.keySet())result.remove(key); if(schema<41)for(String key:Upgrade174.DEFAULTS.keySet())result.remove(key); if(schema<42)for(String key:Upgrade175.DEFAULTS.keySet())result.remove(key); if(schema<44)for(String key:Upgrade184.DEFAULTS.keySet())result.remove(key); if(schema<45)for(String key:Upgrade185.DEFAULTS.keySet())result.remove(key); if(schema<46)for(String key:Upgrade186.DEFAULTS.keySet())result.remove(key); if(schema<47)for(String key:Upgrade187.DEFAULTS.keySet())result.remove(key); if(schema<48)for(String key:Upgrade188.DEFAULTS.keySet())result.remove(key); if(schema<49)for(String key:Upgrade195.DEFAULTS.keySet())result.remove(key); if(schema<50)for(String key:Upgrade196.DEFAULTS.keySet())result.remove(key); if(schema<52)for(String key:Upgrade279.DEFAULTS.keySet())result.remove(key); if(schema<51)for(String key:Upgrade209.DEFAULTS.keySet())result.remove(key); if(schema<43)for(String key:Upgrade177.DEFAULTS.keySet())result.remove(key); if(schema<36)for(String key:Upgrade168.DEFAULTS.keySet())result.remove(key); if(schema<35)for(String key:Upgrade166.DEFAULTS.keySet())result.remove(key); if(schema<34)for(String key:Upgrade160.DEFAULTS.keySet())result.remove(key); if(schema<33)for(String key:Upgrade157.DEFAULTS.keySet())result.remove(key); if(schema<32)for(String key:Upgrade154.DEFAULTS.keySet())result.remove(key); if (schema < 31) for (String key : Upgrade146.DEFAULTS.keySet()) result.remove(key);
        if (schema < 30) for (String key : Upgrade144.DEFAULTS.keySet()) result.remove(key);
        result.put(SettingsFrameworkMirror.SCHEMA, schema);
        result.put(SettingsFrameworkMirror.REVISION, 117L);
        return result;
    }
    private static void defaults() {
        equal(52,SettingsFrameworkMirror.VERSION, "schema");
        equal(21, Upgrade117.DEFAULTS.size(), "one switch, two style switches, two borrow switches, sixteen strengths");
        equal(false, Upgrade117.DEFAULTS.get(NotificationBigClockSettings.REBOUND_LOCK), "no rebound off by default");
        equal(false, Upgrade117.DEFAULTS.get(NotificationBigClockSettings.LOCK_STYLE), "lock style off by default");
        equal(false, Upgrade117.DEFAULTS.get(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.LOCK_STYLE)),
                "landscape lock style off by default");
        for (String key : new String[]{NotificationBigClockSettings.LOCK_STYLE_BODY, NotificationBigClockSettings.LOCK_STYLE_GRADIENT,
                NotificationBigClockSettings.LOCK_STYLE_RIM, NotificationBigClockSettings.LOCK_STYLE_RIM_DEPTH,
                NotificationBigClockSettings.LOCK_STYLE_ROUND, NotificationBigClockSettings.LOCK_STYLE_SHADE,
                NotificationBigClockSettings.LOCK_STYLE_GLOW}) {
            equal(100f, Upgrade117.DEFAULTS.get(key), "100% is the tuned look " + key);
            equal(100f, Upgrade117.DEFAULTS.get(NotificationBigClockSettings.landscapeKey(key)), "landscape too " + key);
        }
        equal(300f, Upgrade117.DEFAULTS.get(NotificationBigClockSettings.LOCK_STYLE_ANGLE), "light angle");
        equal(true, Upgrade117.DEFAULTS.get(NotificationBigClockSettings.LOCK_STYLE_NATIVE), "borrow the lock screen glass by default");
        equal(true, Upgrade117.DEFAULTS.get(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.LOCK_STYLE_NATIVE)), "landscape too");
        for (Map.Entry<String, Object> item : Upgrade117.DEFAULTS.entrySet()) {
            equal(item.getValue(), StatusBarSettings.BOOLEAN_DEFAULTS.containsKey(item.getKey())
                    ? StatusBarSettings.BOOLEAN_DEFAULTS.get(item.getKey()) : StatusBarSettings.NUMERIC_DEFAULTS.get(item.getKey()),
                    "the live defaults agree with the batch " + item.getKey());
        }
        equal(null, NotificationBigClockSettings.BOOLEANS.get(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.REBOUND_LOCK)),
                "no landscape copy of the portrait-only switch");
    }
    private static void mirrors() {
        Bundle full = SettingsSnapshot.fromPreferences(new LinkedHashMap<>());
        check(SettingsSnapshot.complete(full), "fresh current snapshot is complete");
        Map<String, Object> old = wire(full, 25);
        for (String key : Upgrade117.DEFAULTS.keySet()) old.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())old.remove(key);
        Map<String, Object> untouched = new LinkedHashMap<>(old);
        Bundle upgraded = SettingsFrameworkMirror.decode(old);
        check(upgraded != null && SettingsSnapshot.complete(upgraded), "schema 25 copy upgrades");
        equal(untouched, old, "decoder does not edit the old copy");
        for (String key : full.keySet()) equal(full.get(key), upgraded.get(key), "values preserved " + key);
        String[] keys = Upgrade117.DEFAULTS.keySet().toArray(new String[0]);
        for (String key : keys) {
            Map<String, Object> mixed = new LinkedHashMap<>(old);
            mixed.put(key, Upgrade117.DEFAULTS.get(key));
            equal(null, SettingsFrameworkMirror.decode(mixed), "an older schema holding a new key is rejected " + key);
            Map<String, Object> missing = wire(full, SettingsFrameworkMirror.VERSION);
            missing.remove(key);
            equal(null, SettingsFrameworkMirror.decode(missing), "the current schema needs every key " + key);
            Object wrongType = Upgrade117.DEFAULTS.get(key) instanceof Boolean ? (Object) 1f : (Object) Boolean.TRUE;
            Map<String, Object> wrong = wire(full, SettingsFrameworkMirror.VERSION);
            wrong.put(key, wrongType);
            equal(null, SettingsFrameworkMirror.decode(wrong), "strict type " + key);
        }
        Map<String, Object> custom = new LinkedHashMap<>();
        custom.put(NotificationBigClockSettings.LOCK_STYLE, true);
        custom.put(NotificationBigClockSettings.LOCK_STYLE_RIM, 140f);
        custom.put(NotificationBigClockSettings.LOCK_STYLE_ANGLE, 45f);
        custom.put(NotificationBigClockSettings.REBOUND_LOCK, true);
        Bundle kept = SettingsFrameworkMirror.decode(wire(SettingsSnapshot.fromPreferences(custom), SettingsFrameworkMirror.VERSION));
        check(kept != null, "a customised current copy decodes");
        equal(true, kept.get(NotificationBigClockSettings.LOCK_STYLE), "style switch kept");
        equal(140f, kept.get(NotificationBigClockSettings.LOCK_STYLE_RIM), "strength kept");
        equal(45f, kept.get(NotificationBigClockSettings.LOCK_STYLE_ANGLE), "angle kept");
        equal(true, kept.get(NotificationBigClockSettings.REBOUND_LOCK), "no rebound kept");
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade117.migrate(store);
        equal(0, store.writes, "an empty store stays empty");
        store.values.put(NotificationBigClockSettings.MASTER, true);
        store.values.put("local_note", "keep");
        Upgrade117.migrate(store);
        equal(1, store.writes, "all defaults committed together");
        for (Map.Entry<String, Object> item : Upgrade117.DEFAULTS.entrySet())
            equal(item.getValue(), store.values.get(item.getKey()), "initialised " + item.getKey());
        equal(true, store.values.get(NotificationBigClockSettings.MASTER), "existing option retained");
        equal("keep", store.values.get("local_note"), "unrelated metadata retained");
        store.values.put(NotificationBigClockSettings.LOCK_STYLE_BODY, 80f);
        Upgrade117.migrate(store);
        equal(1, store.writes, "idempotent");
        equal(80f, store.values.get(NotificationBigClockSettings.LOCK_STYLE_BODY), "explicit strength never overwritten");
    }
    private static void reboundLock() {
        equal(0f, NotificationBigClock.headerRebound(true, -40f), "no rebound: the spring does not push the clock up");
        equal(0f, NotificationBigClock.headerRebound(true, 25f), "nor pull it down: the clock stays put");
        equal(-40f, NotificationBigClock.headerRebound(false, -40f), "switch off: native behaviour");
        equal(25f, NotificationBigClock.headerRebound(false, 25f), "switch off: native behaviour");
        equal(-32f, NotificationBigClock.entryShift(true, 0f, 60f, 32f), "entry travel starts above rest, and no rebound setting changes it");
        equal(0f, NotificationBigClock.entryShift(true, .6f, 60f, 32f), "and ends at rest");
        equal(0f, NotificationBigClock.entryShift(false, 0f, 60f, 32f), "entry effect off");
        equal("notification_big_clock_rebound_blur_enabled", NotificationBigClockSettings.turnsOff(NotificationBigClockSettings.REBOUND_LOCK), "no rebound turns the blur off");
        equal(null, NotificationBigClockSettings.turnsOff(NotificationBigClockSettings.REBOUND_BLUR_ENABLED), "the blur does not turn the lock off: it is refused while the lock is on");
        java.util.Map<String, Object> values = new java.util.HashMap<>();
        equal("", SettingsCatalog.unavailableReason(NotificationBigClockSettings.REBOUND_BLUR_ENABLED, values), "blur free while no rebound is off");
        values.put(NotificationBigClockSettings.REBOUND_LOCK, true);
        equal(true, !SettingsCatalog.unavailableReason(NotificationBigClockSettings.REBOUND_BLUR_ENABLED, values).isEmpty(), "blur locked while no rebound is on");
        equal(true, !SettingsCatalog.unavailableReason(NotificationBigClockSettings.REBOUND_BLUR_RADIUS, values).isEmpty(), "its radius too");
        equal(true, !SettingsCatalog.unavailableReason(NotificationBigClockSettings.REBOUND_FADE, values).isEmpty(), "and its fade");
        equal("关闭回弹效果", SettingsCatalog.item(NotificationBigClockSettings.REBOUND_LOCK).title, "renamed");
    }
    private static void glass() {
        LockStyleGlass.Params a = new LockStyleGlass.Params(), b = a.copy();
        check(a.same(b), "a copy is the same");
        b.rim = 140f;
        check(!a.same(b), "a different strength is not the same");
        check(!a.same(null), "null is never the same");
        check(LockStyleGlass.blurRadius(a, 4f) > 0f, "100% rounds the glyph");
        equal(0f, LockStyleGlass.blurRadius(zero(), 4f), "0% leaves the glyph sharp");
        equal(300f, a.angle, "default light angle");
    }
    private static LockStyleGlass.Params zero() { LockStyleGlass.Params p = new LockStyleGlass.Params(); p.round = 0f; return p; }
    private static void catalog() {
        SettingsCatalog.Group portrait = SettingsCatalog.group("notification_big_clock");
        SettingsCatalog.Group landscape = SettingsCatalog.group("notification_big_clock_landscape");
        Map<String, String> pageOf = new LinkedHashMap<>();
        Set<String> titles = new HashSet<>();
        for (SettingsCatalog.DetailPage page : SettingsCatalog.detailPages(portrait)) {
            titles.add(page.title);
            for (SettingsCatalog.Item item : page.items) pageOf.put(item.key, page.id);
        }
        equal(6, SettingsCatalog.detailPages(portrait).size(), "six focused pages include portrait widgets");
        check(titles.containsAll(java.util.Arrays.asList("时间", "外观", "日期", "左右组件", "动效与通知", "底部")), "page titles");
        equal("look", pageOf.get(NotificationBigClockSettings.LOCK_STYLE), "lock style is on the look page");
        equal("look", pageOf.get(NotificationBigClockSettings.GLASS), "glass is on the look page");
        equal("look", pageOf.get(NotificationBigClockSettings.COLOR_LIGHT), "text colour is on the look page");
        equal("motion", pageOf.get(NotificationBigClockSettings.REBOUND_LOCK), "no rebound is with the other rebound options");
        equal("motion", pageOf.get(NotificationBigClockSettings.REBOUND_BLUR_ENABLED), "rebound blur");
        equal("motion", pageOf.get(NotificationBigClockSettings.ENTRY_EFFECT_ENABLED), "entry effect");
        equal("motion", pageOf.get(NotificationBigClockSettings.NOTIFICATION_EDGE_ENABLED), "notification edge");
        equal("time", pageOf.get(NotificationBigClockSettings.PATTERN), "format");
        equal("time", pageOf.get(NotificationBigClockSettings.OFFSET_X), "position");
        equal("time", pageOf.get(NotificationBigClockSettings.CLOCK_CLICK_ENABLED), "click stays with the time");
        equal("date", pageOf.get(NotificationBigClockSettings.DATE_SIZE), "date");
        equal("date", pageOf.get(NotificationBigClockSettings.DATE_CLICK_ENABLED), "date click stays with the date");
        equal("footer", pageOf.get(NotificationBigClockSettings.FOOTER_TEXT), "footer");
        String[] strengths = {NotificationBigClockSettings.LOCK_STYLE_NATIVE, NotificationBigClockSettings.LOCK_STYLE_BODY, NotificationBigClockSettings.LOCK_STYLE_GRADIENT,
                NotificationBigClockSettings.LOCK_STYLE_RIM, NotificationBigClockSettings.LOCK_STYLE_RIM_DEPTH,
                NotificationBigClockSettings.LOCK_STYLE_ROUND, NotificationBigClockSettings.LOCK_STYLE_SHADE,
                NotificationBigClockSettings.LOCK_STYLE_GLOW, NotificationBigClockSettings.LOCK_STYLE_ANGLE};
        for (String key : strengths) {
            SettingsCatalog.Item item = SettingsCatalog.item(key);
            check(item != null, "strength editor " + key);
            equal("look", pageOf.get(key), "strength page " + key);
            equal("锁屏质感", item.section, "strength section " + key);
            SettingsCatalog.Item mirror = SettingsCatalog.item(NotificationBigClockSettings.landscapeKey(key));
            check(mirror != null, "landscape strength editor " + key);
            equal(landscape.id, mirror.groupId, "landscape strength group " + key);
        }
        check(SettingsCatalog.item(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.LOCK_STYLE)) != null, "landscape style switch");
        equal(null, SettingsCatalog.item(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.REBOUND_LOCK)),
                "the portrait-only switch has no landscape editor");
        for (SettingsCatalog.DetailPage page : SettingsCatalog.detailPages(landscape))
            for (SettingsCatalog.Item item : page.items)
                check(!NotificationBigClockSettings.REBOUND_LOCK.equals(NotificationBigClockSettings.portraitKey(item.key)),
                        "landscape pages never list no rebound");
    }
    private static void reasons() {
        Map<String, Object> values = new LinkedHashMap<>();
        check(SettingsCatalog.unavailableReason(NotificationBigClockSettings.LOCK_STYLE_RIM, values).contains("请先开启"),
                "strengths wait for the style switch");
        values.put(NotificationBigClockSettings.LOCK_STYLE, true);
        equal("", SettingsCatalog.unavailableReason(NotificationBigClockSettings.LOCK_STYLE_RIM, values), "strengths work once it is on");
        check(SettingsCatalog.unavailableReason(NotificationBigClockSettings.GLASS, values).contains("锁屏质感"),
                "plain glass is replaced while the lock style is on");
        check(SettingsCatalog.unavailableReason(NotificationBigClockSettings.GLASS_BORDER_ENABLED, values).contains("锁屏质感"),
                "the glass border too");
        String landscapeRim = NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.LOCK_STYLE_RIM);
        check(SettingsCatalog.unavailableReason(landscapeRim, new LinkedHashMap<String, Object>()).contains("请先开启"),
                "landscape strengths wait for the landscape switch");
        Map<String, Object> landscape = new LinkedHashMap<>();
        landscape.put(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.LOCK_STYLE), true);
        equal("", SettingsCatalog.unavailableReason(landscapeRim, landscape), "landscape strengths follow their own switch");
    }
    public static void main(String[] args) {
        defaults(); mirrors(); migration(); reboundLock(); glass(); catalog(); reasons();
        System.out.println("Upgrade117Check passed " + checks + " checks");
    }
}
