// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Code279 privacy-dot page: defaults, hook demand, duration bounds, safe mode, migration and the settings page. */
public final class PrivacyIndicatorCheck {
    private static int checks;

    private static void equal(Object expected, Object actual, String label) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError(label + ": " + expected + " != " + actual);
    }

    private static PrivacyIndicatorSettings with(Object... pairs) {
        Bundle bundle = new Bundle();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] instanceof Boolean) bundle.putBoolean((String) pairs[i], (Boolean) pairs[i + 1]);
            else bundle.putFloat((String) pairs[i], (Float) pairs[i + 1]);
        }
        return PrivacyIndicatorSettings.from(bundle);
    }

    public static void main(String[] args) {
        PrivacyIndicatorSettings fresh = with();
        equal(false, fresh.hideDot, "dot stays by default");
        equal(false, fresh.capsule, "capsule off by default");
        equal(true, fresh.camera && fresh.microphone, "camera and microphone chosen by default");
        equal(false, fresh.location, "location not chosen by default");
        equal(0L, fresh.durationMillis, "shown for as long as the use lasts by default");
        equal(false, fresh.wanted(), "no hook while both features are off");

        equal(true, with(PrivacyIndicatorSettings.HIDE_DOT, true).wanted(), "hiding the dot needs the hooks");
        equal(true, with(PrivacyIndicatorSettings.CAPSULE, true).wanted(), "capsule needs the hooks");
        equal(false, with(PrivacyIndicatorSettings.CAPSULE, true, PrivacyIndicatorSettings.CAMERA, false,
                PrivacyIndicatorSettings.MICROPHONE, false).wanted(), "a capsule with no chosen type needs no hook");

        equal(5000L, with(PrivacyIndicatorSettings.DURATION, 5f).durationMillis, "seconds become milliseconds");
        equal(60000L, with(PrivacyIndicatorSettings.DURATION, 60f).durationMillis, "60 seconds is the longest timed display");
        // App280: 1 second minimum; the slider's right end (61) and a legacy 0 both mean "until use stops".
        equal(1000L, with(PrivacyIndicatorSettings.DURATION, 1f).durationMillis, "1 second is the shortest display");
        equal(1000L, with(PrivacyIndicatorSettings.DURATION, .3f).durationMillis, "below 1 second rounds up to 1 second");
        equal(0L, with(PrivacyIndicatorSettings.DURATION, PrivacyIndicatorSettings.CONTINUOUS).durationMillis, "right end is continuous");
        equal(0L, with(PrivacyIndicatorSettings.DURATION, 61f).durationMillis, "61 is continuous");
        equal(0L, with(PrivacyIndicatorSettings.DURATION, 0f).durationMillis, "legacy 0 is continuous");
        equal(0L, with(PrivacyIndicatorSettings.DURATION, -1f).durationMillis, "negative falls back to continuous");
        equal(0L, with(PrivacyIndicatorSettings.DURATION, Float.NaN).durationMillis, "NaN falls back to continuous");
        SettingsCatalog.Item durationItem = null;
        for (SettingsCatalog.Item item : SettingsCatalog.items("privacy_dot")) if (PrivacyIndicatorSettings.DURATION.equals(item.key)) durationItem = item;
        equal(1f, durationItem.min, "slider starts at 1 second");
        equal(PrivacyIndicatorSettings.CONTINUOUS, durationItem.max, "slider ends at continuous");
        equal("一直显示", SettingEditor.formatted(durationItem, PrivacyIndicatorSettings.CONTINUOUS), "right end reads as continuous");
        equal("一直显示", SettingEditor.formatted(durationItem, 0f), "legacy 0 reads as continuous");
        equal("5 秒", SettingEditor.formatted(durationItem, 5f), "timed values keep seconds");
        ActivationGuardPreferencesCheck.MemoryPreferences legacy = new ActivationGuardPreferencesCheck.MemoryPreferences();
        legacy.values.putAll(Upgrade279.DEFAULTS); legacy.values.put(PrivacyIndicatorSettings.DURATION, 0f); legacy.values.put("private_note", "keep");
        Upgrade279.migrate(legacy);
        equal(PrivacyIndicatorSettings.CONTINUOUS, legacy.values.get(PrivacyIndicatorSettings.DURATION), "a saved 0 becomes the continuous right end");
        equal("keep", legacy.values.get("private_note"), "other values untouched");
        int writes = legacy.writes; Upgrade279.migrate(legacy);
        equal(writes, legacy.writes, "the duration migration is idempotent");
        ActivationGuardPreferencesCheck.MemoryPreferences timed = new ActivationGuardPreferencesCheck.MemoryPreferences();
        timed.values.putAll(Upgrade279.DEFAULTS); timed.values.put(PrivacyIndicatorSettings.DURATION, 3f);
        Upgrade279.migrate(timed);
        equal(3f, timed.values.get(PrivacyIndicatorSettings.DURATION), "a timed choice is kept");
        equal(0, timed.writes, "nothing to write for a timed choice");

        PrivacyIndicatorSettings safe = with(StatusBarSettings.SAFE_MODE, true, PrivacyIndicatorSettings.HIDE_DOT, true,
                PrivacyIndicatorSettings.CAPSULE, true);
        equal(false, safe.wanted(), "safe mode restores the native dot and removes the capsule");

        Bundle full = SettingsSnapshot.fromPreferences(Collections.<String, Object>emptyMap());
        for (Map.Entry<String, Object> entry : Upgrade279.DEFAULTS.entrySet())
            equal(entry.getValue(), full.get(entry.getKey()), "snapshot default " + entry.getKey());
        equal(52, SettingsFrameworkMirror.VERSION, "privacy page is schema 52");

        boolean reachable = false;
        for (SettingsCatalog.NavigationSection section : SettingsCatalog.navigationSections(SettingsCatalog.STATUSBAR))
            for (SettingsCatalog.Group group : section.groups) reachable |= "privacy_dot".equals(group.id);
        equal(true, reachable, "privacy page reachable from the status bar page");

        Map<String, Object> values = new HashMap<>();
        for (String child : new String[]{PrivacyIndicatorSettings.CAMERA, PrivacyIndicatorSettings.MICROPHONE,
                PrivacyIndicatorSettings.LOCATION, PrivacyIndicatorSettings.DURATION}) {
            values.put(PrivacyIndicatorSettings.CAPSULE, false);
            equal(false, SettingsCatalog.unavailableReason(child, values).isEmpty(), "child waits for the capsule " + child);
            values.put(PrivacyIndicatorSettings.CAPSULE, true);
            equal(true, SettingsCatalog.unavailableReason(child, values).isEmpty(), "child usable with the capsule " + child);
        }
        geometry();
        System.out.println("PrivacyIndicatorCheck passed: " + checks);
    }

    /** The native fluid cloud payload, in the shape verified on the PJZ110 (the flashlight capsule's own format). */
    private static void geometry() {
        try {
            org.json.JSONObject capsule = new org.json.JSONObject(PrivacyLiveAlert.capsule("drawable/ic_privacy_camera", "相机", "使用中")).getJSONObject("capsuleData");
            equal("t_sym", capsule.getString("type"), "capsule template");
            equal("drawable/ic_privacy_camera", capsule.getJSONObject("firstIcon").getJSONObject("Icon").getString("icon"), "icon left of the camera");
            equal("相机", capsule.getJSONObject("firstTitle").getString("text"), "name next to the icon (firstTitle; firstLabel is not drawn)");
            equal("使用中", capsule.getJSONObject("lastTitle").getString("text"), "in-use label right of the camera");
            org.json.JSONObject card = new org.json.JSONObject(PrivacyLiveAlert.card("drawable/ic_privacy_camera", "相机", "使用中", "查看"));
            // A card that is not the flashlight's three-part shape makes the capsule drop all of its text.
            equal("t_sym_action", card.getJSONObject("criticalData").getString("type"), "critical card part");
            equal(1, card.getJSONObject("criticalData").getJSONArray("lastBtns").length(), "critical part has its button");
            equal("t_standard", card.getJSONObject("primaryData").getString("type"), "primary card part");
            equal("t_actions", card.getJSONObject("subData").getString("type"), "sub card part");
            org.json.JSONObject options = new org.json.JSONObject(PrivacyLiveAlert.options("com.android.systemui"));
            equal("com.android.systemui", options.getString("dataSourcePkgName"), "data source is SystemUI");
            // Seedling host ids: 8 status bar, 16 notification centre. App282: the status bar only, never the shade list.
            equal("[8]", options.getJSONArray("showHostMap").toString(), "status bar host only, not the notification centre");
            equal("[16]", options.getJSONArray("lockScreenShowHostMap").toString(), "the lock-screen hosts keep the original value");
        } catch (org.json.JSONException failed) {
            throw new AssertionError(failed);
        }
    }
}
