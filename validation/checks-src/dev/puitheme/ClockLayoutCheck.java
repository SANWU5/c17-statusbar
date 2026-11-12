// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.StaticLayout;
import android.view.View;
import java.lang.reflect.Field;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fixed-width digit cells, the two-row clock geometry, outer margins, and the schema-13 and schema-14 settings batches. */
public final class ClockLayoutCheck {
    private static int count;
    private static void check(boolean value, String why) { count++; if (!value) throw new AssertionError(why); }
    private static void near(float expected, float value, String why) {
        check(Math.abs(expected - value) < .001f, why + ": expected " + expected + ", was " + value);
    }

    private static boolean flag(Bundle bundle, String key) { return Boolean.TRUE.equals(bundle.get(key)); }
    private static float number(Bundle bundle, String key) { return ((Number) bundle.get(key)).floatValue(); }

    /** '1' is narrower than every other glyph, like a proportional font. */
    private static final class ProportionalPaint extends TextPaint {
        @Override public float measureText(CharSequence text, int start, int end) {
            float width = 0f;
            for (int i = start; i < end; i++) width += getTextSize() * (text.charAt(i) == '1' ? .4f : .6f);
            return width;
        }
    }

    /** Digits span 0..-20 above the baseline; the colon's ink is -9..-1 (low). */
    private static final class LowColonPaint extends TextPaint {
        @Override public void getTextBounds(String text, int start, int end, android.graphics.Rect bounds) {
            boolean colon = text.charAt(start) == ':';
            bounds.left = 0; bounds.right = 4;
            bounds.top = colon ? -9 : -20; bounds.bottom = colon ? -1 : 0;
        }
    }

    /** What the characters at these positions carry: counts of spanned digits and spanned colons. */
    private static int[] spanned(CharSequence text) {
        int digits = 0, colons = 0;
        for (int i = 0; i < text.length(); i++) {
            boolean hit = text instanceof android.text.Spanned
                    && ((android.text.Spanned) text).getSpans(i, i + 1, android.text.style.ReplacementSpan.class).length > 0;
            if (hit && ClockDigits.isDigit(text.charAt(i))) digits++;
            else if (hit && text.charAt(i) == ':') colons++;
        }
        return new int[]{digits, colons};
    }

    private static final class ClockContext extends Context {
        final StatClockSizingCheck.ClockResources resources = new StatClockSizingCheck.ClockResources();
        @Override public Resources getResources() { return resources; }
    }

    private static Map<String, Object> asMap(Bundle bundle) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : bundle.keySet()) result.put(key, bundle.get(key));
        return result;
    }

    private static void defaultsAndRegistration() {
        Bundle defaults = SettingsSnapshot.fromPreferences(new LinkedHashMap<>());
        check(SettingsSnapshot.complete(defaults), "fresh snapshot is complete with the new batch");
        for (String group : ClockLayoutSettings.DIGIT_GROUPS) {
            check(flag(defaults, ClockLayoutSettings.digitKey(group)), group + " fixed digits default on");
            near(0f, number(defaults, ClockLayoutSettings.digitSpacingKey(group)), group + " digit spacing default");
            check(group.equals(FeatureOptions.GROUP_BY_KEY.get(ClockLayoutSettings.digitKey(group))), group + " switch belongs to its group");
        }
        check(!flag(defaults, ClockLayoutSettings.DUAL_ENABLED), "two rows are opt-in");
        check(!flag(defaults, ClockLayoutSettings.MARGIN_ENABLED), "margins are opt-in");
        check("clock".equals(FeatureOptions.GROUP_BY_KEY.get(ClockLayoutSettings.DUAL_ENABLED)), "two-row switch belongs to the status clock");
        near(72f, number(defaults, ClockLayoutSettings.DUAL_UPPER_SCALE), "upper size default");
        near(72f, number(defaults, ClockLayoutSettings.DUAL_LOWER_SCALE), "lower size default shares the upper row's percentage");
        near(600f, number(defaults, ClockLayoutSettings.DUAL_UPPER_WEIGHT), "upper weight default");
        near(500f, number(defaults, ClockLayoutSettings.DUAL_LOWER_WEIGHT), "lower weight default");
        near(0f, number(defaults, ClockLayoutSettings.DUAL_LINE_GAP), "row gap default");
        check(ClockLayoutSettings.ALIGN_CENTER.equals(defaults.getString(ClockLayoutSettings.DUAL_ALIGN)), "centered by default");
        check(TimeFormat.validationError(defaults.getString(ClockLayoutSettings.DUAL_UPPER_PATTERN)) == null
                && TimeFormat.validationError(defaults.getString(ClockLayoutSettings.DUAL_LOWER_PATTERN)) == null, "default row formats are valid");
        // Signed values keep their sign; margins and sizes never go negative.
        near(-1f, NumericPolicy.setting(ClockLayoutSettings.digitSpacingKey("clock"), -1f, 0f), "digit spacing may tighten");
        near(-3f, NumericPolicy.setting(ClockLayoutSettings.DUAL_LINE_GAP, -3f, 0f), "rows may overlap");
        near(0f, NumericPolicy.setting(ClockLayoutSettings.MARGIN_START, -4f, 0f), "margin never negative");
        near(1000f, NumericPolicy.setting(ClockLayoutSettings.DUAL_UPPER_WEIGHT, 5000f, 600f), "weight bounded");
        Bundle gated = new Bundle(defaults);
        gated.putBoolean(ClockLayoutSettings.DUAL_ENABLED, true);
        gated.putBoolean("clock_controls_enabled", false);
        check(!FeatureOptions.from(gated).effective("clock", ClockLayoutSettings.DUAL_ENABLED), "group master gates two rows");
        gated.putBoolean("clock_controls_enabled", true);
        check(FeatureOptions.from(gated).effective("clock", ClockLayoutSettings.DUAL_ENABLED), "enabled with its group");
        check(!FeatureOptions.from(gated).dualClock(), "both modes saved on: the default single row wins");
        gated.putBoolean("clock_controls_enabled", false);
        check(FeatureOptions.from(gated).dualClock(), "the two rows alone run");
        gated.putBoolean("clock_controls_enabled", true);
        check(ClockLayoutSettings.validAlign("left") && ClockLayoutSettings.validAlign("right") && !ClockLayoutSettings.validAlign("top"), "alignment values");
        near(0f, ClockLayoutSettings.alignFraction("left"), "left fraction");
        near(.5f, ClockLayoutSettings.alignFraction("center"), "center fraction");
        near(1f, ClockLayoutSettings.alignFraction("right"), "right fraction");
    }

    private static void exclusiveModes() {
        check(ClockLayoutSettings.STATUS.equals("clock"), "the status clock group is the default clock");
        check("clock_controls_enabled".equals(ClockLayoutSettings.turnsOff(ClockLayoutSettings.DUAL_ENABLED)), "two rows on turns the default clock off");
        check(ClockLayoutSettings.DUAL_ENABLED.equals(ClockLayoutSettings.turnsOff("clock_controls_enabled")), "default clock on turns the two rows off");
        check(ClockLayoutSettings.turnsOff("shade_clock_controls_enabled") == null && ClockLayoutSettings.turnsOff(ClockLayoutSettings.MARGIN_ENABLED) == null,
                "no other switch is touched");
    }

    private static void migration() {
        Bundle defaults = SettingsSnapshot.fromPreferences(new LinkedHashMap<>());
        check(SettingsFrameworkMirror.VERSION == 52, "framework copy is schema 52");
        // A schema-12 copy lacks both the schema-13 and the schema-14 batches.
        Map<String, Object> old = asMap(defaults);
        for (String key : Upgrade90.DEFAULTS.keySet()) old.remove(key);
        for (String key : Upgrade91.DEFAULTS.keySet()) old.remove(key);
        for (String key : Upgrade93.DEFAULTS.keySet()) old.remove(key);
        for (String key : Upgrade96.DEFAULTS.keySet()) old.remove(key);
        for (String key : Upgrade98.DEFAULTS.keySet()) old.remove(key);
        for (String key : Upgrade99.DEFAULTS.keySet()) old.remove(key);
        for (String key : Upgrade100.DEFAULTS.keySet()) old.remove(key);
        for (String key : Upgrade101.DEFAULTS.keySet()) old.remove(key);
        old.put(SettingsFrameworkMirror.SCHEMA, 12);
        for(String key:Upgrade103.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())old.remove(key);
        old.put(SettingsFrameworkMirror.REVISION, 9001L);
        Bundle upgraded = SettingsFrameworkMirror.decode(old);
        check(upgraded != null && SettingsSnapshot.complete(upgraded), "schema 12 copy upgrades to a complete snapshot");
        for (Map.Entry<String, Object> entry : Upgrade90.DEFAULTS.entrySet())
            check(entry.getValue().equals(upgraded.get(entry.getKey())), "default retained for " + entry.getKey());
        for (Map.Entry<String, Object> entry : Upgrade91.DEFAULTS.entrySet())
            check(entry.getValue().equals(upgraded.get(entry.getKey())), "schema 14 default retained for " + entry.getKey());
        old.put(ClockLayoutSettings.DUAL_ENABLED, true);
        check(SettingsFrameworkMirror.decode(old) == null, "schema 12 with a new key is a partial write");
        // A schema-13 copy has the first batch and lacks only the second.
        Map<String, Object> thirteen = asMap(defaults);
        for (String key : Upgrade91.DEFAULTS.keySet()) thirteen.remove(key);
        for (String key : Upgrade93.DEFAULTS.keySet()) thirteen.remove(key);
        for (String key : Upgrade96.DEFAULTS.keySet()) thirteen.remove(key);
        for (String key : Upgrade98.DEFAULTS.keySet()) thirteen.remove(key);
        for (String key : Upgrade99.DEFAULTS.keySet()) thirteen.remove(key);
        for (String key : Upgrade100.DEFAULTS.keySet()) thirteen.remove(key);
        for (String key : Upgrade101.DEFAULTS.keySet()) thirteen.remove(key);
        thirteen.put(SettingsFrameworkMirror.SCHEMA, 13);
        for(String key:Upgrade103.DEFAULTS.keySet())thirteen.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())thirteen.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())thirteen.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())thirteen.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())thirteen.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())thirteen.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())thirteen.remove(key);
        thirteen.put(SettingsFrameworkMirror.REVISION, 9003L);
        upgraded = SettingsFrameworkMirror.decode(thirteen);
        check(upgraded != null && SettingsSnapshot.complete(upgraded), "schema 13 copy upgrades to a complete snapshot");
        for (Map.Entry<String, Object> entry : Upgrade91.DEFAULTS.entrySet())
            check(entry.getValue().equals(upgraded.get(entry.getKey())), "schema 13 gets the schema-14 default for " + entry.getKey());
        thirteen.put(ClockLayoutSettings.DUAL_SPACING, 2f);
        check(SettingsFrameworkMirror.decode(thirteen) == null, "schema 13 with a schema-14 key is a partial write");
        Map<String, Object> current = asMap(defaults);
        current.put(SettingsFrameworkMirror.SCHEMA, 20);
        for(String key:Upgrade103.DEFAULTS.keySet())current.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())current.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())current.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())current.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())current.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())current.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())current.remove(key);
        current.put(SettingsFrameworkMirror.REVISION, 9002L);
        check(SettingsFrameworkMirror.decode(current) != null, "complete schema 20 copy decodes");
        current.remove(ClockLayoutSettings.DUAL_LOWER_PATTERN);
        check(SettingsFrameworkMirror.decode(current) == null, "schema 20 cannot silently default a missing key");
        // A schema-19 copy (what code100 phones hold) lacks only the schema-20 pair.
        Map<String, Object> nineteen = asMap(defaults);
        for (String key : Upgrade101.DEFAULTS.keySet()) nineteen.remove(key);
        nineteen.put(SettingsFrameworkMirror.SCHEMA, 19);
        for(String key:Upgrade103.DEFAULTS.keySet())nineteen.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())nineteen.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())nineteen.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())nineteen.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())nineteen.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())nineteen.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())nineteen.remove(key);
        nineteen.put(SettingsFrameworkMirror.REVISION, 9008L);
        upgraded = SettingsFrameworkMirror.decode(nineteen);
        check(upgraded != null && SettingsSnapshot.complete(upgraded), "schema 19 copy upgrades to a complete snapshot");
        check(Boolean.TRUE.equals(upgraded.get(RadioState.WIFI_MERGED)) && Boolean.FALSE.equals(upgraded.get(RadioState.ALWAYS_SHOWN)),
                "Wi-Fi merged on, always-shown text off after the upgrade");
        nineteen.put(RadioState.ALWAYS_SHOWN, true);
        check(SettingsFrameworkMirror.decode(nineteen) == null, "schema 19 with a schema-20 key is a partial write");
        // A schema-18 copy lacks only the schema-19 batch (gradient transition ranges, the capsule cover radius, the control
        // centre's top buttons): it upgrades with the defaults, and a copy that already carries one of the new keys is a partial write.
        Map<String, Object> eighteen = asMap(defaults);
        for (String key : Upgrade100.DEFAULTS.keySet()) eighteen.remove(key);
        for (String key : Upgrade101.DEFAULTS.keySet()) eighteen.remove(key);
        eighteen.put(SettingsFrameworkMirror.SCHEMA, 18);
        for(String key:Upgrade103.DEFAULTS.keySet())eighteen.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())eighteen.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())eighteen.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())eighteen.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())eighteen.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())eighteen.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())eighteen.remove(key);
        eighteen.put(SettingsFrameworkMirror.REVISION, 9007L);
        upgraded = SettingsFrameworkMirror.decode(eighteen);
        check(upgraded != null && SettingsSnapshot.complete(upgraded), "schema 18 copy upgrades to a complete snapshot");
        for (Map.Entry<String, Object> entry : Upgrade100.DEFAULTS.entrySet())
            check(entry.getValue().equals(upgraded.get(entry.getKey())), "schema 19 default retained for " + entry.getKey());
        check(Boolean.FALSE.equals(upgraded.get(QsEntrances.EDIT_HIDDEN)) && Boolean.FALSE.equals(upgraded.get(QsEntrances.SETTINGS_HIDDEN)),
                "the control centre buttons stay shown after the upgrade");
        eighteen.put(FluidCloudCapsule.COVER_RADIUS_ENABLED, true);
        check(SettingsFrameworkMirror.decode(eighteen) == null, "schema 18 with a schema-19 key is a partial write");
        // A schema-17 copy lacks only the schema-18 batch (blur mask colour, six running-light colours and glow, spectrum colour,
        // rebound fade): it upgrades with the defaults, and a copy that already carries one of the new keys is a partial write.
        Map<String, Object> seventeen = asMap(defaults);
        for (String key : Upgrade99.DEFAULTS.keySet()) seventeen.remove(key);
        for (String key : Upgrade100.DEFAULTS.keySet()) seventeen.remove(key);
        for (String key : Upgrade101.DEFAULTS.keySet()) seventeen.remove(key);
        seventeen.put(SettingsFrameworkMirror.SCHEMA, 17);
        for(String key:Upgrade103.DEFAULTS.keySet())seventeen.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())seventeen.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())seventeen.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())seventeen.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())seventeen.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())seventeen.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())seventeen.remove(key);
        seventeen.put(SettingsFrameworkMirror.REVISION, 9006L);
        upgraded = SettingsFrameworkMirror.decode(seventeen);
        check(upgraded != null && SettingsSnapshot.complete(upgraded), "schema 17 copy upgrades to a complete snapshot");
        for (Map.Entry<String, Object> entry : Upgrade99.DEFAULTS.entrySet())
            check(entry.getValue().equals(upgraded.get(entry.getKey())), "schema 18 default retained for " + entry.getKey());
        seventeen.put(FluidCloudCapsule.GLOW_ENABLED, true);
        check(SettingsFrameworkMirror.decode(seventeen) == null, "schema 17 with a schema-18 key is a partial write");
        // A schema-16 copy lacks only the schema-17 batch (lock capsule switch, colour style, capsule gradient/blur/marquee/spectrum,
        // clock rebound blur): it upgrades with the defaults, and a copy that already carries one of the new keys is a partial write.
        Map<String, Object> sixteen = asMap(defaults);
        for (String key : Upgrade98.DEFAULTS.keySet()) sixteen.remove(key);
        for (String key : Upgrade99.DEFAULTS.keySet()) sixteen.remove(key);
        for (String key : Upgrade100.DEFAULTS.keySet()) sixteen.remove(key);
        for (String key : Upgrade101.DEFAULTS.keySet()) sixteen.remove(key);
        sixteen.put(SettingsFrameworkMirror.SCHEMA, 16);
        for(String key:Upgrade103.DEFAULTS.keySet())sixteen.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())sixteen.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())sixteen.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())sixteen.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())sixteen.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())sixteen.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())sixteen.remove(key);
        sixteen.put(SettingsFrameworkMirror.REVISION, 9005L);
        upgraded = SettingsFrameworkMirror.decode(sixteen);
        check(upgraded != null && SettingsSnapshot.complete(upgraded), "schema 16 copy upgrades to a complete snapshot");
        for (Map.Entry<String, Object> entry : Upgrade98.DEFAULTS.entrySet())
            check(entry.getValue().equals(upgraded.get(entry.getKey())), "schema 17 default retained for " + entry.getKey());
        check(Boolean.FALSE.equals(upgraded.get(FluidCloudStyle.LOCK_CAPSULE)), "the lock-screen capsule stays native after the upgrade");
        check(FluidCloudStyle.SOLID.equals(upgraded.get(FluidCloudStyle.COLOR_STYLE)), "the colour style stays solid after the upgrade");
        sixteen.put(FluidCloudCapsule.MARQUEE_ENABLED, true);
        check(SettingsFrameworkMirror.decode(sixteen) == null, "schema 16 with a schema-17 key is a partial write");
        // A schema-15 copy lacks only the schema-16 batch (centred colons and the capsule page): it upgrades with the
        // defaults, and a copy that already carries one of the new keys is a partial write.
        Map<String, Object> fifteen = asMap(defaults);
        for (String key : Upgrade96.DEFAULTS.keySet()) fifteen.remove(key);
        for (String key : Upgrade98.DEFAULTS.keySet()) fifteen.remove(key);
        for (String key : Upgrade99.DEFAULTS.keySet()) fifteen.remove(key);
        for (String key : Upgrade100.DEFAULTS.keySet()) fifteen.remove(key);
        for (String key : Upgrade101.DEFAULTS.keySet()) fifteen.remove(key);
        fifteen.put(SettingsFrameworkMirror.SCHEMA, 15);
        for(String key:Upgrade103.DEFAULTS.keySet())fifteen.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())fifteen.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())fifteen.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())fifteen.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())fifteen.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())fifteen.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())fifteen.remove(key);
        fifteen.put(SettingsFrameworkMirror.REVISION, 9004L);
        upgraded = SettingsFrameworkMirror.decode(fifteen);
        check(upgraded != null && SettingsSnapshot.complete(upgraded), "schema 15 copy upgrades to a complete snapshot");
        for (Map.Entry<String, Object> entry : Upgrade96.DEFAULTS.entrySet())
            check(entry.getValue().equals(upgraded.get(entry.getKey())), "schema 16 default retained for " + entry.getKey());
        for (String group : ClockLayoutSettings.DIGIT_GROUPS)
            check(Boolean.TRUE.equals(upgraded.get(ClockLayoutSettings.colonKey(group))), group + " colons are centred after the upgrade");
        check(Boolean.FALSE.equals(upgraded.get(FluidCloudCapsule.MASTER)), "the capsule page is off after the upgrade");
        fifteen.put(FluidCloudCapsule.MASTER, true);
        check(SettingsFrameworkMirror.decode(fifteen) == null, "schema 15 with a schema-16 key is a partial write");
    }

    private static void portableConfiguration() throws IOException {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(ClockLayoutSettings.DUAL_ENABLED, true);
        source.put(ClockLayoutSettings.DUAL_UPPER_PATTERN, "HH:mm:ss");
        source.put(ClockLayoutSettings.DUAL_LOWER_PATTERN, "M月d日 {周}");
        source.put(ClockLayoutSettings.DUAL_ALIGN, "right");
        source.put(ClockLayoutSettings.MARGIN_ENABLED, true);
        source.put(ClockLayoutSettings.MARGIN_START, 6f);
        source.put(ClockLayoutSettings.DUAL_LINE_GAP, -3f);
        source.put(ClockLayoutSettings.digitSpacingKey("clock"), -1f);
        String json = ConfigTransfer.exportJson(source);
        Map<String, Object> values = ConfigTransfer.prepare(json, true).values();
        check(Boolean.TRUE.equals(values.get(ClockLayoutSettings.DUAL_ENABLED)), "two-row switch round-trips");
        check("HH:mm:ss".equals(values.get(ClockLayoutSettings.DUAL_UPPER_PATTERN)), "upper format round-trips");
        check("right".equals(values.get(ClockLayoutSettings.DUAL_ALIGN)), "alignment round-trips");
        near(-3f, ((Number) values.get(ClockLayoutSettings.DUAL_LINE_GAP)).floatValue(), "negative gap round-trips");
        near(6f, ((Number) values.get(ClockLayoutSettings.MARGIN_START)).floatValue(), "margin round-trips");
        near(-1f, ((Number) values.get(ClockLayoutSettings.digitSpacingKey("clock"))).floatValue(), "digit spacing round-trips");
        String badAlign = json.replaceAll("(\"clock_dual_align\"\\s*:\\s*)\"[a-z]+\"", "$1\"diagonal\"");
        check(!badAlign.equals(json), "alignment present in export");
        boolean rejected = false;
        try { ConfigTransfer.prepare(badAlign, true); } catch (IOException invalid) { rejected = true; }
        check(rejected, "unknown alignment is rejected on import");
        String badFormat = json.replaceAll("(\"clock_dual_upper_pattern\"\\s*:\\s*)\"[^\"]*\"", "$1\"{nonsense}\"");
        check(!badFormat.equals(json), "upper format present in export");
        rejected = false;
        try { ConfigTransfer.prepare(badFormat, true); } catch (IOException invalid) { rejected = true; }
        check(rejected, "invalid row format is rejected on import");
    }

    private static void catalog() {
        for (String key : new String[]{ClockLayoutSettings.DUAL_ENABLED, ClockLayoutSettings.DUAL_UPPER_PATTERN,
                ClockLayoutSettings.DUAL_LOWER_PATTERN, ClockLayoutSettings.DUAL_UPPER_SCALE, ClockLayoutSettings.DUAL_LOWER_SCALE,
                ClockLayoutSettings.DUAL_UPPER_WEIGHT, ClockLayoutSettings.DUAL_LOWER_WEIGHT, ClockLayoutSettings.DUAL_LINE_GAP,
                ClockLayoutSettings.DUAL_ALIGN, ClockLayoutSettings.digitKey("clock"), ClockLayoutSettings.digitSpacingKey("clock"),
                ClockLayoutSettings.DUAL_OFFSET_Y, ClockLayoutSettings.DUAL_SPACING,
                ClockLayoutSettings.DUAL_COLOR_ENABLED, ClockLayoutSettings.DUAL_COLOR_LIGHT, ClockLayoutSettings.DUAL_COLOR_DARK}) {
            SettingsCatalog.Item item = SettingsCatalog.item(key);
            check(item != null && "clock".equals(item.groupId), key + " is on the status clock page");
        }
        check(SettingsCatalog.item(ClockLayoutSettings.digitKey("shade_clock")) != null
                && "shade_clock".equals(SettingsCatalog.item(ClockLayoutSettings.digitKey("shade_clock")).groupId), "shade clock has digit cells");
        check(SettingsCatalog.item(ClockLayoutSettings.digitKey(PanelMode.CLOCK)) != null
                && "classic_text".equals(SettingsCatalog.item(ClockLayoutSettings.digitKey(PanelMode.CLOCK)).groupId), "classic clock has digit cells");
        check(SettingsCatalog.item(ClockLayoutSettings.DUAL_UPPER_PATTERN).values.length > 0, "row formats offer presets");
        check(SettingsCatalog.item(ClockLayoutSettings.DUAL_ALIGN).values.length == 3, "three alignments");
        check(SettingsCatalog.item(ClockLayoutSettings.DUAL_ENABLED).section.equals("内容"), "two-row master opens the content section");
        check(SettingsCatalog.item(ClockLayoutSettings.DUAL_COLOR_LIGHT).type == SettingsCatalog.COLOR, "two-row colours are colour items");
        for(String key:new String[]{StatusBarSettings.CLOCK_OFFSET_X,ClockLayoutSettings.DUAL_OFFSET_X,
                ClockLayoutSettings.MARGIN_ENABLED,ClockLayoutSettings.MARGIN_START,ClockLayoutSettings.MARGIN_END}) {
            check(SettingsCatalog.item(key)==null,"legacy clock horizontal editor retired "+key);
            check(SettingsCatalog.hiddenKeys().contains(key),"legacy clock field remains deliberately hidden "+key);
            check(ConfigTransfer.types().containsKey(key),"legacy clock field stays registered for import/export "+key);
            check(ConfigScope.section("clock").keys().contains(key),"legacy clock field keeps original partial-export owner "+key);
        }
        for(String suffix:new String[]{"side","order","margin_left","margin_right"}) {
            SettingsCatalog.Item field=SettingsCatalog.item(StatusBarLayoutSettings.key(StatusBarLayoutSettings.CLOCK,suffix));
            check(field!=null&&("side".equals(suffix)||"order".equals(suffix)?StatusBarLayoutSettings.GROUP:"clock").equals(field.groupId),
                    "clock ordering is independent and margins remain on clock page "+suffix);
            check(field.section.equals("side".equals(suffix)||"order".equals(suffix)?"时间":"左右间距"),"clock controls keep explicit section "+suffix);
            if(StatusBarLayoutSettings.marginKey(field.key))
                check(SettingsCatalog.detailPages(SettingsCatalog.group("clock")).stream().anyMatch(page -> "common".equals(page.id) && page.items.contains(field)),"shared margin is on the common clock tab "+suffix);
        }
        SettingsCatalog.Item left=SettingsCatalog.item(StatusBarLayoutSettings.key(StatusBarLayoutSettings.CLOCK,"margin_left"));
        near(3.125f,SettingsCatalog.customNumber(left,"3.125"),"new left gap keeps fractional dp");
        check(SettingsCatalog.validationError(left,-1f)==null,"physical gap accepts signed input");
        Map<String, Object> saved = new LinkedHashMap<>();
        check(reason(ClockLayoutSettings.DUAL_UPPER_PATTERN, saved).equals("请先开启双排时钟"), "row format needs two rows");
        check(reason(ClockLayoutSettings.DUAL_LINE_GAP, saved).equals("请先开启双排时钟"), "row gap needs two rows");
        check(reason(ClockLayoutSettings.DUAL_ENABLED, saved).isEmpty(), "the two-row switch itself is editable");
        check(reason(left.key, saved).isEmpty(), "shared outer gap is independent of sorting and style opt-in");
        saved.put(ClockLayoutSettings.DUAL_ENABLED, true); saved.put(ClockLayoutSettings.MARGIN_ENABLED, true);
        check(reason(ClockLayoutSettings.DUAL_UPPER_PATTERN, saved).isEmpty(), "row format editable once two rows are on");
        check(reason(left.key, saved).isEmpty(), "old dual-margin request cannot gate independent new margins");
        saved.put(StatusBarLayoutSettings.MASTER,true);
        check(reason(left.key,saved).isEmpty(),"shared outer gap editable once layout is enabled");
        saved.put(ClockLayoutSettings.digitKey("clock"), false);
        check(reason(ClockLayoutSettings.digitSpacingKey("clock"), saved).equals("请先开启等宽数字"), "digit spacing needs fixed digits");
        saved.remove(ClockLayoutSettings.digitKey("clock"));
        check(reason(ClockLayoutSettings.digitSpacingKey("clock"), saved).isEmpty(), "fixed digits default on, spacing editable");
    }

    private static String reason(String key, Map<String, Object> saved) {
        String result = SettingsCatalog.unavailableReason(key, saved);
        return result == null ? "" : result;
    }

    private static void geometry() {
        ClockDual.Placement center = ClockDual.place(2, 1, 3, 100, 60, 2, 40, 12, 16, 30, 9, 12, 2, 4, .5f);
        near(40f, center.contentWidth, "widest row");
        near(4f, center.upperDx, "upper row moves to the block's left edge");
        near(-14f, center.upperDy, "block is centered vertically");
        near(11f, center.lowerX, "narrower row is centered");
        near(61f, center.lowerBaseline, "lower row sits one gap below the upper");
        ClockDual.Placement left = ClockDual.place(2, 1, 3, 100, 60, 2, 40, 12, 16, 30, 9, 12, 2, 4, 0f);
        near(6f, left.lowerX, "left alignment");
        ClockDual.Placement right = ClockDual.place(2, 1, 3, 100, 60, 2, 40, 12, 16, 30, 9, 12, 2, 4, 1f);
        near(16f, right.lowerX, "right alignment");
        // The upper row can be the narrower one: it moves, the wider lower row defines the block.
        ClockDual.Placement wideLower = ClockDual.place(0, 0, 0, 80, 40, 0, 20, 10, 14, 50, 8, 10, 0, 0, 1f);
        near(30f, wideLower.upperDx, "narrow upper row right-aligned to the lower row");
        near(0f, wideLower.lowerX, "wide lower row at the block edge");
        // A larger gap pushes the rows apart symmetrically around the same center.
        ClockDual.Placement apart = ClockDual.place(0, 0, 0, 80, 40, 0, 20, 10, 14, 20, 8, 10, 10, 0, .5f);
        ClockDual.Placement close = ClockDual.place(0, 0, 0, 80, 40, 0, 20, 10, 14, 20, 8, 10, 0, 0, .5f);
        near(10f, (apart.lowerBaseline - apart.upperDy) - (close.lowerBaseline - close.upperDy), "gap spreads the rows by exactly its size");
        near(51f, (float) ClockDual.viewWidth(40, 2, 4, 5), "view width adds padding and both margins");
        near(0f, (float) ClockDual.viewWidth(10, 0, -20, 0), "view width is never negative");
    }

    private static void fixedDigits() {
        ProportionalPaint paint = new ProportionalPaint();
        paint.setTextSize(20f);
        CharSequence plainWide = "00:00:00", plainNarrow = "11:11:11";
        check(ClockDigits.width(plainWide, paint) > ClockDigits.width(plainNarrow, paint), "proportional digits change the width (the jump)");
        check(ClockDigits.apply("::", 0f) instanceof String, "text without digits is untouched");
        check(!ClockDigits.hasSpans("12:34"), "plain text carries no spans");
        CharSequence wide = ClockDigits.apply(plainWide, 0f), narrow = ClockDigits.apply(plainNarrow, 0f);
        check(ClockDigits.hasSpans(wide) && ClockDigits.hasSpans(narrow), "digits carry cell spans");
        near((float) ClockDigits.width(wide, paint), (float) ClockDigits.width(narrow, paint), "fixed cells give every second the same width");
        double base = ClockDigits.width(wide, paint);
        check(Math.abs(base - 8 * 20f * .6f) < .01, "cell is the widest digit; colons keep their own width");
        near((float) base + 6f * 2f, (float) ClockDigits.width(ClockDigits.apply(plainWide, 2f), paint), "spacing is added to each of the six digit cells");
        near((float) base - 6f, (float) ClockDigits.width(ClockDigits.apply(plainWide, -1f), paint), "negative spacing tightens each cell");
        paint.setTextSize(30f);
        near(1.5f * (float) base, (float) ClockDigits.width(wide, paint), "cell follows the paint it is drawn with");
        // A changed style must not reuse a stale measurement.
        paint.setLetterSpacing(.1f);
        check(ClockDigits.width(wide, paint) >= 0d, "letter spacing change re-measures without failing");
        check(ClockDigits.apply("", 0f).length() == 0 && ClockDigits.apply(null, 0f) == null, "empty text is returned unchanged");
        check(ClockDigits.isDigit('0') && ClockDigits.isDigit('9') && !ClockDigits.isDigit(':') && !ClockDigits.isDigit('٣'), "only ASCII digits");
    }

    /** Colons are drawn at the middle of the digits, with or without fixed digit cells. */
    private static void centredColons() {
        check(ClockDigits.isColon(':') && ClockDigits.isColon('\uff1a') && !ClockDigits.isColon('.') && !ClockDigits.isColon('1'), "colon characters");
        CharSequence both = ClockDigits.align("12:34:56", 0f, true, true);
        check(spanned(both)[0] == 6 && spanned(both)[1] == 2, "fixed digits and centred colons together");
        CharSequence colonsOnly = ClockDigits.align("12:34", 0f, false, true);
        check(spanned(colonsOnly)[0] == 0 && spanned(colonsOnly)[1] == 1, "centred colons need no digit cells");
        CharSequence digitsOnly = ClockDigits.align("12:34", 0f, true, false);
        check(spanned(digitsOnly)[0] == 4 && spanned(digitsOnly)[1] == 0, "digit cells leave the colon alone");
        check(ClockDigits.align("12:34", 0f, false, false) instanceof String, "nothing selected, nothing wrapped");
        check(ClockDigits.align("ab", 0f, false, true) instanceof String, "text without a colon is untouched");
        check(ClockDigits.hasSpans(colonsOnly), "a colon span is a span");
        // The shift is the distance between the middles of the digits' and the colon's ink; a centred font is left alone.
        LowColonPaint low = new LowColonPaint();
        low.setTextSize(40f);
        near(-5f, ClockDigits.colonShift(low), "a low colon moves up to the digits' middle");
        near(0f, ClockDigits.colonShift(new ProportionalPaint()), "a centred colon does not move");
        low.setTextSize(8f);
        near(-2f, ClockDigits.colonShift(low), "the shift is limited to a quarter em");
        // Width is unchanged apart from rounding to whole pixels.
        low.setTextSize(20f);
        ProportionalPaint paint = new ProportionalPaint();
        paint.setTextSize(20f);
        near((float) ClockDigits.width("12:34", paint), (float) ClockDigits.width(ClockDigits.align("12:34", 0f, false, true), paint), "centring a colon keeps the width");
        // Drawn through the span, the colon lands on the shifted baseline.
        android.text.style.ReplacementSpan span = ((android.text.Spanned) colonsOnly).getSpans(2, 3, android.text.style.ReplacementSpan.class)[0];
        Canvas canvas = new Canvas();
        low.setTextSize(40f);
        span.draw(canvas, colonsOnly, 2, 3, 10f, 0, 100, 0, low);
        near(95f, canvas.textY, "the colon is drawn five pixels above the baseline");
        near(10f, canvas.textX, "its horizontal position is unchanged");
    }

    private static Bundle clockSettings(boolean margins, boolean enabled, String pattern, boolean fixedDigits) {
        return clockSettings(margins, enabled, pattern, fixedDigits, false);
    }

    private static Bundle clockSettings(boolean margins, boolean enabled, String pattern, boolean fixedDigits, boolean dual) {
        Bundle b = new Bundle();
        b.putBoolean(ClockLayoutSettings.DUAL_ENABLED, dual);
        // The two modes exclude each other; with both saved on the default single row would win.
        b.putBoolean("clock_controls_enabled", !dual); b.putBoolean("clock_enabled", enabled);
        b.putBoolean("clock_size_enabled", false); b.putBoolean("clock_text_style_enabled", false);
        b.putString("clock_pattern", pattern);
        b.putBoolean(ClockLayoutSettings.digitKey("clock"), fixedDigits);
        b.putBoolean(ClockLayoutSettings.MARGIN_ENABLED, margins);
        b.putFloat(ClockLayoutSettings.MARGIN_START, 3f); b.putFloat(ClockLayoutSettings.MARGIN_END, 5f);
        b.putFloat(NativeClockMeasurement.SCALE_BASIS_VERSION, 2f);
        return b;
    }

    private static int measuredWidth(TextControls controls, StatClockSizingCheck.MeasuredClock clock) {
        return View.MeasureSpec.getSize(controls.prepareClockMeasure(clock, View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST)));
    }

    private static void statusClockRuntime() {
        ClockContext context = new ClockContext();
        TextControls controls = new TextControls(new Handler(Looper.getMainLooper()));
        StatClockSizingCheck.MeasuredClock clock = new StatClockSizingCheck.MeasuredClock(context);
        clock.setText("12:34");
        controls.configure(clockSettings(false, false, "HH:mm", true), StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        controls.attach(clock);
        int plain = measuredWidth(controls, clock);
        Canvas untouched = new Canvas();
        controls.beforeDraw(clock, untouched);
        near(0f, untouched.translateX, "no margins, no shift");
        // The outer margins belong to the two-row clock: on the default clock they change nothing at all.
        controls.configure(clockSettings(true, false, "HH:mm", true), StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        check(measuredWidth(controls, clock) == plain, "margins alone leave the default clock's width untouched");
        Canvas defaultClock = new Canvas();
        controls.beforeDraw(clock, defaultClock);
        near(0f, defaultClock.translateX, "default clock gets no margin shift");
        float density = clock.getResources().getDisplayMetrics().density;
        // The two-row clock is independent of the default clock's master: it needs only its own switch.
        controls.configure(clockSettings(false, false, "HH:mm", true, true), StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        int dualPlain = measuredWidth(controls, clock);
        controls.configure(clockSettings(true, false, "HH:mm", true, true), StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        int dualWide = measuredWidth(controls, clock);
        // 3dp + 5dp at the view's density, added to the natural block width only while the margin switch is on.
        check(dualWide == dualPlain + Math.round(8f * density), "margins widen the two-row clock by both sides (" + dualPlain + " -> " + dualWide + ")");
        Canvas shifted = new Canvas();
        controls.beforeDraw(clock, shifted);
        controls.afterDraw(clock, shifted);
        check(Float.isFinite(shifted.translateX) && Float.isFinite(shifted.translateY), "two-row drawing keeps the canvas finite");
        controls.configure(clockSettings(false, false, "HH:mm", true, true), StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        check(measuredWidth(controls, clock) == dualPlain, "turning margins off restores the natural block width");
        controls.configure(clockSettings(false, false, "HH:mm", true), StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        check(measuredWidth(controls, clock) == plain, "turning the two rows off restores the default clock");

        // Fixed cells only act on a format that shows seconds, and only while the switch is on.
        controls.configure(clockSettings(false, true, "HH:mm:ss", true), StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        controls.attach(clock);
        check(ClockDigits.hasSpans(clock.getText()), "seconds format gets fixed digit cells");
        controls.configure(clockSettings(false, true, "HH:mm", true), StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        controls.attach(clock);
        check(spanned(clock.getText())[0] == 0, "a format without seconds keeps native digit widths");
        check(spanned(clock.getText())[1] == 1, "the colon is centred whether or not seconds are shown");
        controls.configure(clockSettings(false, true, "HH:mm:ss", false), StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        controls.attach(clock);
        check(spanned(clock.getText())[0] == 0, "switch off keeps native digit widths");
        check(spanned(clock.getText())[1] == 2, "switching the digit cells off leaves the colons centred");
        Bundle noColon = clockSettings(false, true, "HH:mm:ss", false);
        noColon.putBoolean(ClockLayoutSettings.colonKey("clock"), false);
        controls.configure(noColon, StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        controls.attach(clock);
        check(!ClockDigits.hasSpans(clock.getText()), "both switches off: the native text, no spans at all");
        Bundle onlyColon = clockSettings(false, true, "HH:mm:ss", true);
        onlyColon.putBoolean(ClockLayoutSettings.colonKey("clock"), false);
        controls.configure(onlyColon, StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        controls.attach(clock);
        check(spanned(clock.getText())[0] == 6 && spanned(clock.getText())[1] == 0, "colon switch off keeps the colon native");
        controls.configure(clockSettings(false, true, "HH:mm:ss", true), StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        controls.attach(clock);
        check(ClockDigits.hasSpans(clock.getText()), "switching back on rebuilds the cells although the characters are unchanged");
        // The accessibility text never carries spans.
        check(clock.getContentDescription() != null && !(clock.getContentDescription() instanceof android.text.Spanned), "spoken text is plain");
    }

    private static StaticLayout lowerLayout(TextControls controls, android.widget.TextView clock) throws Exception {
        Field registry = TextControls.class.getDeclaredField("views"); registry.setAccessible(true);
        Object entry = ((Map<?, ?>) registry.get(controls)).get(clock);
        Field lower = entry.getClass().getDeclaredField("lowerLayout"); lower.setAccessible(true);
        return (StaticLayout) lower.get(entry);
    }

    private static void commonDigitsInDual() throws Exception {
        ClockContext context=new ClockContext();
        TextControls controls=new TextControls(new Handler(Looper.getMainLooper()));
        StatClockSizingCheck.MeasuredClock clock=new StatClockSizingCheck.MeasuredClock(context);
        clock.setText("12:34");
        for(boolean fixed:new boolean[]{true,false}) {
            Bundle values=clockSettings(false,true,"HH:mm:ss",fixed,true);
            values.putString(ClockLayoutSettings.DUAL_UPPER_PATTERN,"HH:mm:ss");
            values.putString(ClockLayoutSettings.DUAL_LOWER_PATTERN,"HH:mm:ss");
            values.putFloat(ClockLayoutSettings.digitSpacingKey("clock"),1.25f);
            controls.configure(values,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());controls.attach(clock);
            measuredWidth(controls,clock);
            check(spanned(clock.getText())[0]==(fixed?6:0),"common digit setting reaches upper dual row with default clock off");
            StaticLayout lower=lowerLayout(controls,clock);
            check(lower!=null&&spanned(lower.getText())[0]==(fixed?6:0),"same common setting reaches lower dual row");
            check(!values.getBoolean("clock_controls_enabled"),"shared digit option never enables the default mode");
        }
    }

    private static void anchorObserverLifecycle() throws Exception {
        ClockContext context = new ClockContext();
        TextControls controls = new TextControls(new Handler(Looper.getMainLooper()));
        StatClockSizingCheck.MeasuredClock existing = new StatClockSizingCheck.MeasuredClock(context);
        existing.setText("12:34"); controls.attach(existing);
        java.util.List<View> observed = new java.util.ArrayList<>();
        controls.observeLayoutAnchors(observed::add);
        check(observed.size() == 1 && observed.get(0) == existing, "late observer immediately sees an already bound native clock");
        StatClockSizingCheck.MeasuredClock future = new StatClockSizingCheck.MeasuredClock(context);
        future.setText("12:35"); controls.attach(future);
        check(observed.size() == 2 && observed.get(1) == future, "future clock attachment reaches the same observer");
        controls.observeLayoutAnchors(null);
        Field observer = TextControls.class.getDeclaredField("layoutAnchorObserver"); observer.setAccessible(true);
        check(observer.get(controls) == null, "null observer releases its callback reference");
        controls.attach(existing); controls.attach(new StatClockSizingCheck.MeasuredClock(context));
        check(observed.size() == 2, "disabled observer receives neither existing nor future attachments");
        java.util.List<View> replacement = new java.util.ArrayList<>(); controls.observeLayoutAnchors(replacement::add);
        check(replacement.size() == 3 && replacement.contains(existing) && replacement.contains(future),
                "replacement observer replays all actual native bindings without retaining the old callback");
        controls.observeLayoutAnchors(null);
    }

    /** Runs OEM measure, module super-measure and both production draw callbacks; no text-only assertions. */
    private static void equalDualPixelSizes() throws Exception {
        for (float density : new float[]{1f, 2f, 3.5f, 4f}) {
            ClockContext context = new ClockContext();
            context.resources.getDisplayMetrics().density = density;
            context.resources.getDisplayMetrics().scaledDensity = density * 1.3f;
            StatClockSizingCheck.MeasuredClock clock = new StatClockSizingCheck.MeasuredClock(context);
            clock.setLayoutParams(new android.view.ViewGroup.LayoutParams(100, 120)); clock.platformMeasureSpecs = true;
            clock.setText("12:34"); clock.stripHeight = 120;
            TextControls controls = new TextControls(new Handler(Looper.getMainLooper()));
            Bundle options = new Bundle(); options.putBoolean(ClockLayoutSettings.DUAL_ENABLED, true);
            options.putBoolean("clock_controls_enabled", false);
            options.putString(ClockLayoutSettings.DUAL_UPPER_PATTERN, "HH:mm:ss");
            options.putString(ClockLayoutSettings.DUAL_LOWER_PATTERN, "HH:mm:ss");
            options.putFloat(ClockLayoutSettings.DUAL_UPPER_WEIGHT, 600f);
            options.putFloat(ClockLayoutSettings.DUAL_LOWER_WEIGHT, 600f);
            for (float percent : new float[]{Float.NaN, 25f, 72f, 100f, 79.22f, 250f}) {
                if (Float.isFinite(percent)) {
                    options.putFloat(ClockLayoutSettings.DUAL_UPPER_SCALE, percent);
                    options.putFloat(ClockLayoutSettings.DUAL_LOWER_SCALE, percent);
                }
                controls.configure(options, StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap()); controls.attach(clock);
                float nativePixels = context.resources.getDimensionPixelSize(1);
                for (int pass = 0; pass < 3; pass++) {
                    TextControls.NativeStyleScope nativeScope = controls.beginClockMeasure(clock);
                    check(nativeScope != null, "real status clock retains its native measurement callback");
                    clock.getPaint().setTextSize(nativePixels); // OEM writes Paint directly on every measure.
                    clock.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
                    nativeScope.close();
                    int width = controls.prepareClockMeasure(clock, View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST));
                    clock.measure(width, View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY)); controls.afterClockMeasure(clock);
                    StaticLayout lower = lowerLayout(controls, clock);
                    check(clock.getLayout() != null && lower != null, "both rows have real measured layouts");
                    float requested = Float.isFinite(percent) ? percent : 72f;
                    near(nativePixels * requested / 100f, clock.getLayout().measuredTextSize,
                            "upper layout uses the original native pixel base");
                    near(clock.getLayout().measuredTextSize, lower.measuredTextSize,
                            "equal row values produce equal measured pixel sizes, including defaults");
                    near(clock.getPaint().getTextSize(), lower.paintForCheck().getTextSize(),
                            "both production paint instances retain the same size after OEM remeasure");
                    near(clock.getLayout().getLineWidth(0), lower.getLineWidth(0), "equal text and size have equal measured glyph widths");
                    int upperDraws = clock.getLayout().drawCalls, lowerDraws = lower.drawCalls;
                    Canvas canvas = new Canvas(); controls.beforeDraw(clock, canvas); clock.getLayout().draw(canvas); controls.afterDraw(clock, canvas);
                    check(clock.getLayout().drawCalls == upperDraws + 1 && lower.drawCalls == lowerDraws + 1,
                            "native upper draw and production lower draw both execute");
                    check(Float.isFinite(canvas.translateX) && Float.isFinite(canvas.translateY), "equal rows draw with finite placement");
                }
            }
            for (float[] saved : new float[][]{{72f, 56f}, {48.64f, 79.22f}}) {
                options.putFloat(ClockLayoutSettings.DUAL_UPPER_SCALE, saved[0]); options.putFloat(ClockLayoutSettings.DUAL_LOWER_SCALE, saved[1]);
                controls.configure(options, StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
                int width = controls.prepareClockMeasure(clock, View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST));
                clock.measure(width, View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
                StaticLayout lower = lowerLayout(controls, clock);
                near(context.resources.getDimensionPixelSize(1) * saved[0] / 100f, clock.getPaint().getTextSize(), "saved independent upper value is retained");
                near(context.resources.getDimensionPixelSize(1) * saved[1] / 100f, lower.paintForCheck().getTextSize(), "saved independent lower value is retained");
                near(saved[0], number(options, ClockLayoutSettings.DUAL_UPPER_SCALE), "configuration never rewrites the saved upper percentage");
                near(saved[1], number(options, ClockLayoutSettings.DUAL_LOWER_SCALE), "configuration never rewrites the saved lower percentage");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        anchorObserverLifecycle();
        equalDualPixelSizes();
        defaultsAndRegistration();
        migration();
        exclusiveModes();
        portableConfiguration();
        catalog();
        geometry();
        fixedDigits();
        centredColons();
        statusClockRuntime();
        commonDigitsInDual();
        System.out.println("ClockLayoutCheck: " + count + " checks passed");
    }
}
