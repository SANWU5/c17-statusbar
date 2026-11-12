package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Customising what a share, an export or an import takes: pages are the units, nothing outside the choice is written. */
public final class ConfigScopeCheck {
    private static int checks;
    private static void check(boolean okay, String why) { checks++; if (!okay) throw new AssertionError(why); }

    private static final class FakePreferences implements SharedPreferences {
        final Map<String, Object> values = new LinkedHashMap<>();
        FakePreferences(Map<String, ?> source) { values.putAll(source); }
        public Map<String, ?> getAll() { return new LinkedHashMap<>(values); }
        public String getString(String key, String fallback) { return values.containsKey(key) ? (String) values.get(key) : fallback; }
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String key, Set<String> fallback) { return values.containsKey(key) ? (Set<String>) values.get(key) : fallback; }
        public int getInt(String key, int fallback) { return values.containsKey(key) ? (Integer) values.get(key) : fallback; }
        public long getLong(String key, long fallback) { return values.containsKey(key) ? (Long) values.get(key) : fallback; }
        public float getFloat(String key, float fallback) { return values.containsKey(key) ? (Float) values.get(key) : fallback; }
        public boolean getBoolean(String key, boolean fallback) { return values.containsKey(key) ? (Boolean) values.get(key) : fallback; }
        public boolean contains(String key) { return values.containsKey(key); }
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener ignored) { }
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener ignored) { }
        public Editor edit() { return new FakeEditor(); }
        private final class FakeEditor implements Editor {
            final Map<String, Object> pending = new HashMap<>();
            public Editor putString(String key, String value) { pending.put(key, value); return this; }
            public Editor putStringSet(String key, Set<String> value) { pending.put(key, new HashSet<>(value)); return this; }
            public Editor putInt(String key, int value) { pending.put(key, value); return this; }
            public Editor putLong(String key, long value) { pending.put(key, value); return this; }
            public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
            public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
            public Editor remove(String key) { throw new AssertionError("Import must not remove preferences"); }
            public Editor clear() { throw new AssertionError("Import must preserve local settings"); }
            public boolean commit() { values.putAll(pending); return true; }
            public void apply() { throw new AssertionError("Import requires one checked commit"); }
        }
    }

    private static void sections() {
        List<ConfigScope.Section> all = ConfigScope.sections();
        check(all.size() > 20, "every settings page is a section");
        Set<String> seen = new HashSet<>(), ids = new HashSet<>();
        for (ConfigScope.Section section : all) {
            check(ids.add(section.id), "section ids are unique: " + section.id);
            check(!section.title.isEmpty() && section.count() > 0, "every section has a title and keys: " + section.id);
            for (String key : section.keys()) {
                check(seen.add(key), "a key belongs to one section only: " + key);
                check(ConfigTransfer.types().containsKey(key), "only portable keys are offered: " + key);
            }
        }
        check(seen.equals(ConfigTransfer.types().keySet()), "the sections together hold exactly the portable keys");
        ConfigScope.Section last = all.get(all.size() - 1);
        check(ConfigScope.OTHER_ID.equals(last.id) || ConfigScope.section(ConfigScope.OTHER_ID) == null, "keys no page shows come last");
        ConfigScope.Section capsule = ConfigScope.section("fluid_cloud_capsule");
        check(capsule != null && capsule.keys().contains(FluidCloudCapsule.MASTER) && capsule.keys().contains(FluidCloudCapsule.MARQUEE_COLOR_6)
                && capsule.keys().contains(StatusBarSettings.alphaKey(FluidCloudCapsule.BLUR_MASK_COLOR))
                && capsule.keys().contains(FluidCloudCapsule.GLOW_ENABLED), "the camera-area page owns its keys, colour alpha flags included");
        check(capsule.label().contains("摄像头") || capsule.label().length() > 2, "the checklist has a readable label");
        check(!capsule.keys().contains(NotificationBigClockSettings.MASTER), "and nothing from another page");
        ConfigScope.Section clock = ConfigScope.section("notification_big_clock");
        check(clock != null && clock.keys().contains(NotificationBigClockSettings.REBOUND_FADE), "the big clock owns the rebound fade");
        check(ConfigScope.section("no_such_page") == null, "unknown page");
        ConfigScope.Section network=ConfigScope.section("network");
        check(network!=null&&network.keys().contains(StatusBarSettings.WIFI_ICON_SCALE)
                &&network.keys().contains(StatusBarSettings.LABEL_SCALE)
                &&network.keys().contains(NativeNetworkBadgeSettings.key(1,"scale")),"one network share section contains all three real control families");
        ConfigScope.Section wifi = ConfigScope.section("wifi"), label = ConfigScope.section("label"), speed = ConfigScope.section("speed");
        check(wifi != null && label != null && speed != null, "old Wi-Fi and label export selections remain readable as exact subsets");
        check(!all.contains(wifi)&&!all.contains(label),"the current checklist does not duplicate old network pages");
        check(wifi.keys().contains(StatusBarSettings.WIFI_OFFSET_X) && !wifi.keys().contains(StatusBarSettings.LABEL_OFFSET_X),
                "Wi-Fi shares only its released independent position");
        check(label.keys().contains(StatusBarSettings.LABEL_OFFSET_X) && label.keys().contains(StatusBarSettings.SLOT_WIDTH)
                && !label.keys().contains(StatusBarSettings.WIFI_OFFSET_X), "label shares its own position and reserved width");
        check(!label.keys().contains(RadioState.BOTH_VISIBLE), "the newer dual option is absent from the public label section");
        check(ConfigScope.keys(Collections.singletonList("wifi")).equals(wifi.keys()),"old Wi-Fi selection exports only its previous subset");
        check(ConfigScope.keys(Arrays.asList("network","wifi","label")).equals(network.keys()),"mixed new and restored old selections deduplicate every network key");
        check(speed.keys().contains(NetworkSpeedControls.INTERVAL_MILLIS), "speed owns its original sampling control");
        // A page's feature switches (they make its values count) travel with the page, not with the unpaged remainder.
        ConfigScope.Section rest = ConfigScope.section(ConfigScope.OTHER_ID);
        for (Map.Entry<String, String> owner : FeatureOptions.GROUP_BY_KEY.entrySet()) {
            if("network".equals(owner.getValue())) {
                check(rest!=null&&rest.keys().contains(owner.getKey()),"retired hidden network switch retains its portable remainder ownership: "+owner.getKey());
                continue;
            }
            ConfigScope.Section page = ConfigScope.section(owner.getValue());
            if (page != null && ConfigTransfer.types().containsKey(owner.getKey()))
                check(page.keys().contains(owner.getKey()) && (rest == null || !rest.keys().contains(owner.getKey())), "feature switch " + owner.getKey() + " is on its page");
        }
        check(rest != null && !rest.keys().contains("carrier_position_enabled") && !rest.keys().contains(FluidCloudStyle.MASTER), "page switches are not left in the remainder");
        ConfigScope.Section icons=ConfigScope.section("status_icon_library");
        check(icons!=null&&icons.keys().equals(new java.util.LinkedHashSet<>(Arrays.asList(IconManagementSettings.ENABLED,IconManagementSettings.KEY))),"icon management shares its pause master and independent v3 draft record");
        for(String retired:IconManagementSettings.LEGACY_KEYS)
            check(!seen.contains(retired),"retired icon fields cannot travel with any page: "+retired);
    }

    private static void keys() {
        Set<String> capsule = ConfigScope.keys(Collections.singletonList("fluid_cloud_capsule"));
        check(capsule.equals(ConfigScope.section("fluid_cloud_capsule").keys()), "one page's keys");
        Set<String> two = ConfigScope.keys(Arrays.asList("fluid_cloud_capsule", "notification_big_clock", "no_such_page"));
        check(two.size() == capsule.size() + ConfigScope.section("notification_big_clock").count(), "two pages, unknown ids ignored");
        check(ConfigScope.keys(null).isEmpty() && ConfigScope.keys(new ArrayList<String>()).isEmpty(), "nothing chosen, nothing taken");
        List<String> everything = new ArrayList<>();
        for (ConfigScope.Section section : ConfigScope.sections()) everything.add(section.id);
        check(ConfigScope.keys(everything).equals(ConfigTransfer.types().keySet()), "every page together is the whole configuration");
        Map<ConfigScope.Section, Integer> present = ConfigScope.present(Arrays.asList(FluidCloudCapsule.MASTER, FluidCloudCapsule.RADIUS, "not_a_setting"));
        check(present.size() == 1 && present.values().iterator().next() == 2 && present.keySet().iterator().next().id.equals("fluid_cloud_capsule"),
                "a file's pages are the pages that hold its keys");
        check(ConfigScope.present(null).isEmpty() && ConfigScope.present(Collections.<String>emptyList()).isEmpty(), "an empty file has no pages");
    }

    private static void partialTransfer() throws Exception {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put(FluidCloudCapsule.MASTER, true);
        stored.put(FluidCloudCapsule.RADIUS, 6f);
        stored.put(FluidCloudCapsule.MARQUEE_PRESET, "galaxy");
        stored.put(StatusBarSettings.SHADE_CLOCK_SCALE, 150f);
        Set<String> capsule = ConfigScope.keys(Collections.singletonList("fluid_cloud_capsule"));
        String json = ConfigTransfer.exportJson(stored, capsule);
        ConfigTransfer.PreparedImport prepared = ConfigTransfer.prepare(json, true);
        check(prepared.keys().equals(capsule), "a partial export carries exactly the chosen page");
        check(Boolean.TRUE.equals(prepared.values().get(FluidCloudCapsule.MASTER)) && Float.valueOf(6f).equals(prepared.values().get(FluidCloudCapsule.RADIUS))
                && "galaxy".equals(prepared.values().get(FluidCloudCapsule.MARQUEE_PRESET)), "with the stored values");
        check(!prepared.values().containsKey(StatusBarSettings.SHADE_CLOCK_SCALE), "and not the others");
        check(ConfigTransfer.exportJson(stored).length() > json.length() * 3, "the full export is much larger");
        check(ConfigTransfer.exportJson(stored, null).equals(ConfigTransfer.exportJson(stored)), "no choice means everything");
        try { ConfigTransfer.exportJson(stored, Collections.<String>emptyList()); check(false, "an empty choice is refused"); }
        catch (IOException refused) { check(refused.getMessage().contains("没有选择"), "with a reason"); }
        try { ConfigTransfer.exportJson(stored, Collections.singletonList("not_a_setting")); check(false, "unknown keys alone are refused"); }
        catch (IOException refused) { check(true, "unknown keys"); }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        ConfigTransfer.write(out, stored, capsule);
        check(new String(out.toByteArray(), StandardCharsets.UTF_8).equals(json), "write streams the same partial document");

        // Importing some pages of a larger file.
        ConfigTransfer.PreparedImport whole = ConfigTransfer.prepare(ConfigTransfer.exportJson(stored), true);
        ConfigTransfer.PreparedImport subset = whole.only(capsule);
        check(subset.count == capsule.size() && subset.keys().equals(capsule), "an import limited to one page");
        check(whole.only(Collections.<String>emptyList()).count == 0 && whole.only(null).count == 0, "nothing chosen leaves nothing to import");
        check(whole.only(Collections.singletonList("not_in_file")).count == 0, "keys the file lacks are not invented");
        Map<String, Object> local = new LinkedHashMap<>();
        local.put(StatusBarSettings.SHADE_CLOCK_SCALE, 222f);
        local.put(FluidCloudCapsule.RADIUS, 15f);
        local.put(FluidCloudCapsule.MASTER, false);
        FakePreferences preferences = new FakePreferences(local);
        check(ConfigTransfer.commit(preferences, subset), "the commit goes through");
        check(Float.valueOf(222f).equals(preferences.values.get(StatusBarSettings.SHADE_CLOCK_SCALE)), "a page that was not chosen keeps this phone's value");
        check(Float.valueOf(6f).equals(preferences.values.get(FluidCloudCapsule.RADIUS)) && Boolean.TRUE.equals(preferences.values.get(FluidCloudCapsule.MASTER)),
                "the chosen page takes the file's values");
        Map<String, Object> before = new LinkedHashMap<>(preferences.values);
        ConfigTransfer.PreparedImport clockOnly = whole.only(ConfigScope.keys(Collections.singletonList("shade_clock")));
        check(ConfigTransfer.commit(preferences, clockOnly) && Float.valueOf(150f).equals(preferences.values.get(StatusBarSettings.SHADE_CLOCK_SCALE)),
                "another page, another commit");
        check(Float.valueOf(6f).equals(preferences.values.get(FluidCloudCapsule.RADIUS)), "which leaves the first alone");
        for (Map.Entry<String, Object> entry : before.entrySet())
            if (!clockOnly.keys().contains(entry.getKey()) && !StatusBarSettings.DATA_ACTIVITY_HIDDEN.equals(entry.getKey())
                    && !NotificationGroupStack.MASTER.equals(entry.getKey()) && !FluidCloudStyle.FOLLOW_FOREGROUND.equals(entry.getKey()))
                check(entry.getValue().equals(preferences.values.get(entry.getKey())), "untouched: " + entry.getKey());
        // A font fallback warning follows the pages that carry fonts.
        String fontFile = "{\"package\":\"" + ConfigTransfer.PACKAGE_NAME + "\",\"schema\":1,\"settings\":{\"" + StatusBarSettings.FONT_MODE + "\":\"custom\",\""
                + FluidCloudCapsule.RADIUS + "\":6}}";
        ConfigTransfer.PreparedImport withFont = ConfigTransfer.prepare(fontFile, false);
        check(withFont.fontFallback && withFont.only(Collections.singletonList(StatusBarSettings.FONT_MODE)).fontFallback, "the font page still warns");
        check(!withFont.only(Collections.singletonList(FluidCloudCapsule.RADIUS)).fontFallback, "a page without fonts does not");
    }

    /** The choices of the fluid cloud pages are portable: they were left out of a shared configuration before code99. */
    private static void fluidChoices() throws Exception {
        for (String key : new String[]{FluidCloudStyle.COLOR_STYLE, FluidCloudCapsule.GRADIENT_MODE, FluidCloudCapsule.MARQUEE_PRESET})
            check(ConfigTransfer.types().get(key) == ConfigTransfer.Type.STRING, key + " travels with a configuration");
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put(FluidCloudStyle.COLOR_STYLE, FluidCloudStyle.GRADIENT);
        stored.put(FluidCloudCapsule.GRADIENT_MODE, FluidCloudCapsule.CENTER);
        stored.put(FluidCloudCapsule.MARQUEE_PRESET, "custom");
        Map<String, Object> back = ConfigTransfer.prepare(ConfigTransfer.exportJson(stored), true).values();
        check(FluidCloudStyle.GRADIENT.equals(back.get(FluidCloudStyle.COLOR_STYLE)) && FluidCloudCapsule.CENTER.equals(back.get(FluidCloudCapsule.GRADIENT_MODE))
                && "custom".equals(back.get(FluidCloudCapsule.MARQUEE_PRESET)), "an export and an import give the choices back");
        for (String id : FluidCloudCapsule.MARQUEE_PRESETS) {
            stored.put(FluidCloudCapsule.MARQUEE_PRESET, id);
            check(id.equals(ConfigTransfer.prepare(ConfigTransfer.exportJson(stored), true).values().get(FluidCloudCapsule.MARQUEE_PRESET)), "preset " + id);
        }
        String header = "{\"package\":\"" + ConfigTransfer.PACKAGE_NAME + "\",\"schema\":1,\"settings\":{";
        for (String bad : new String[]{"\"" + FluidCloudStyle.COLOR_STYLE + "\":\"rainbow\"", "\"" + FluidCloudCapsule.GRADIENT_MODE + "\":\"diagonal\"",
                "\"" + FluidCloudCapsule.MARQUEE_PRESET + "\":\"nope\"", "\"" + FluidCloudCapsule.MARQUEE_PRESET + "\":3"}) {
            try { ConfigTransfer.prepare(header + bad + "}}", true); check(false, "refused: " + bad); }
            catch (IOException refused) { check(true, "refused"); }
        }
    }

    /** code100: the shade scope ("通知栏") is retired. It is off by default, hidden, and nothing turns it back on: not a value saved
     * by code99 (where it was on by default), not an import, not a partial import, not the copy handed to SystemUI. */
    private static void shadeScopeRetired() throws IOException {
        check(Boolean.FALSE.equals(StatusBarSettings.BOOLEAN_DEFAULTS.get(FluidCloudStyle.SCOPE_SHADE)), "the shade scope is off by default");
        check(SettingsCatalog.hiddenKeys().contains(FluidCloudStyle.SCOPE_SHADE), "and has no switch any more");
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put(FluidCloudStyle.SCOPE_SHADE, true);
        check(!StatusBarSettings.bool(stored, FluidCloudStyle.SCOPE_SHADE), "an old saved on reads as off");
        String header = "{\"package\":\"" + ConfigTransfer.PACKAGE_NAME + "\",\"schema\":1,\"settings\":{";
        ConfigTransfer.PreparedImport imported = ConfigTransfer.prepare(header + "\"" + FluidCloudStyle.SCOPE_SHADE + "\":true,\""
                + FluidCloudStyle.SCOPE_LOCK + "\":true}}", true);
        check(Boolean.FALSE.equals(imported.values().get(FluidCloudStyle.SCOPE_SHADE)), "an imported on is stored as off");
        check(Boolean.TRUE.equals(imported.values().get(FluidCloudStyle.SCOPE_LOCK)), "the other scopes import as they are");
        FakePreferences device = new FakePreferences(stored);
        ConfigTransfer.PreparedImport partial = ConfigTransfer.prepare(header + "\"" + FluidCloudStyle.SCOPE_LOCK + "\":false}}", true);
        check(ConfigTransfer.commit(device, partial), "partial import commits");
        check(Boolean.FALSE.equals(device.values.get(FluidCloudStyle.SCOPE_SHADE)), "a partial import also turns off a saved on");
        android.os.Bundle copy = new android.os.Bundle();
        copy.putBoolean(FluidCloudStyle.SCOPE_SHADE, true);
        check(!SettingsSnapshot.runtimeCopy(copy).getBoolean(FluidCloudStyle.SCOPE_SHADE, true), "SystemUI's copy is always off");
    }

    private static void share() throws Exception {
        File directory = java.nio.file.Files.createTempDirectory("c17-config-share").toFile();
        Context context = new Context();
        context.cacheDirForCheck = directory;
        String json = ConfigTransfer.exportJson(Collections.singletonMap(FluidCloudCapsule.MASTER, true),
                Collections.singletonList(FluidCloudCapsule.MASTER));
        Uri uri = ConfigShare.prepare(context, json);
        check("content".equals(uri.getScheme()) && ConfigShare.AUTHORITY.equals(uri.getAuthority()) && uri.getPathSegments().size() == 3
                && ConfigShare.FILE_NAME.equals(uri.getPathSegments().get(2)), "an exact content address with a readable file name");
        File file = ConfigShare.file(context, uri);
        check(file.isFile() && new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).equals(json), "the snapshot holds the document");
        for (String bad : new String[]{"content://other.authority/snapshot/" + uri.getPathSegments().get(1) + "/" + ConfigShare.FILE_NAME,
                "file://" + ConfigShare.AUTHORITY + "/snapshot/" + uri.getPathSegments().get(1) + "/" + ConfigShare.FILE_NAME,
                "content://" + ConfigShare.AUTHORITY + "/snapshot/" + uri.getPathSegments().get(1) + "/other.json",
                "content://" + ConfigShare.AUTHORITY + "/snapshot/" + uri.getPathSegments().get(1) + "/" + ConfigShare.FILE_NAME + "?x=1",
                "content://" + ConfigShare.AUTHORITY + "/snapshot/../" + ConfigShare.FILE_NAME,
                "content://" + ConfigShare.AUTHORITY + "/snapshot/not-a-token/" + ConfigShare.FILE_NAME,
                "content://" + ConfigShare.AUTHORITY + "/snapshot/" + "0".repeat(32) + "/" + ConfigShare.FILE_NAME,
                "content://" + ConfigShare.AUTHORITY + "/other/" + uri.getPathSegments().get(1) + "/" + ConfigShare.FILE_NAME}) {
            try { ConfigShare.file(context, Uri.parse(bad)); check(false, "refused: " + bad); }
            catch (java.io.FileNotFoundException refused) { check(true, "refused"); }
        }
        try { ConfigShare.file(context, null); check(false, "null refused"); } catch (java.io.FileNotFoundException refused) { check(true, "null"); }
        // Old snapshots expire and are removed; only a few are kept.
        check(file.setLastModified(System.currentTimeMillis() - ConfigShare.SNAPSHOT_LIFETIME_MS - 1000L), "age the snapshot");
        try { ConfigShare.file(context, uri); check(false, "an expired snapshot is refused"); } catch (java.io.FileNotFoundException refused) { check(true, "expired"); }
        Uri next = ConfigShare.prepare(context, json);
        check(!file.exists() && context.revokedForCheck >= 1, "the next share removes the expired snapshot and its grant");
        for (int i = 0; i < 5; i++) ConfigShare.prepare(context, json);
        File[] kept = new File(directory, "config-shares").listFiles();
        check(kept != null && kept.length <= 3, "only the last few snapshots are kept");
        try { ConfigShare.prepare(context, ""); check(false, "empty refused"); } catch (IOException refused) { check(true, "empty"); }
        try { ConfigShare.prepare(context, "x".repeat(ConfigTransfer.MAX_BYTES + 1)); check(false, "oversized refused"); } catch (IOException refused) { check(true, "large"); }
        try { ConfigShare.prepare(null, json); check(false, "no context refused"); } catch (IOException refused) { check(true, "context"); }
        check(next != null, "the next uri");
    }

    public static void main(String[] args) throws Exception {
        sections();
        keys();
        partialTransfer();
        fluidChoices();
        shadeScopeRetired();
        share();
        System.out.println("ConfigScopeCheck: " + checks + " checks passed (pages as sections, partition of portable keys, partial export, partial import, share snapshots)");
    }
}
