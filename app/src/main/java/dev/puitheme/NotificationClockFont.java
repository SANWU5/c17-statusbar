// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewParent;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/** Height-axis typefaces from the phone's own clock plugin, without copying its font files. */
public final class NotificationClockFont {
    private static final String PACKAGE = "com.oplus.keyguard.personality.clocks";
    private static final String DEFAULT_FAMILY = "fonts/tunable/OPPOSans4.0.ttf";
    private static final int DEFAULT_HEIGHT = 70, DEFAULT_WEIGHT = 703;
    private static final int MAX_FONT_BYTES = 2 * 1024 * 1024;
    private static final int HGHT = 0x48474854, WGHT = 0x77676874, FVAR = 0x66766172;
    private static final Map<Class<?>, Access> ACCESS = new LinkedHashMap<Class<?>, Access>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Class<?>, Access> eldest) { return size() > 16; }
    };
    private static int warnings;

    private NotificationClockFont() { }

    /** Read a lockscreen sample once; unsupported/missing native assets leave the caller's font intact. */
    public static Resolver create(Context context, View nativeTemplate) {
        if (context == null) return null;
        Metadata source = metadata(nativeTemplate);
        Context plugin = nativeTemplate == null ? null : nativeTemplate.getContext();
        if (plugin != null) {
            try {
                Resolver result = resolve(plugin.getAssets(), source);
                if (result != null) return result;
            } catch (Throwable error) { warn(error); }
        }
        try {
            plugin = context.createPackageContext(PACKAGE,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            return resolve(plugin.getAssets(), source);
        } catch (Throwable error) {
            warn(error); return null;
        }
    }

    /** Immutable native font metadata; the small private cache changes no lockscreen objects. */
    public static final class Resolver {
        public final String family, nativeFamily;
        public final int baseHeight, nativeWeight;
        public final int minWeight, maxWeight;
        public final boolean usesFallbackFamily;
        private final AssetManager assets;
        private final Map<Integer, Typeface> faces = new LinkedHashMap<Integer, Typeface>(96, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Integer, Typeface> eldest) { return size() > 96; }
        };
        private int buildWarnings;

        private Resolver(AssetManager assets, String family, Metadata source, boolean fallback, WeightRange weights) {
            this.assets = assets; this.family = family; nativeFamily = source.family;
            usesFallbackFamily = fallback;
            baseHeight = fallback ? DEFAULT_HEIGHT : clamp(source.height, 1, 1000);
            nativeWeight = clamp(source.weight, 1, 1000);
            minWeight=weights.minimum;maxWeight=weights.maximum;
        }

        /** Same integer HGHT interpolation used by native ClockTimeView.setFontStyleProgress. */
        public synchronized Typeface typeface(int weight, float heightRatio) {
            if (Float.isNaN(heightRatio) || Float.isInfinite(heightRatio)) return null;
            int selectedWeight = clamp(weight, minWeight, maxWeight);
            int height = clamp(Math.round(baseHeight * Math.max(0f, heightRatio)), 1, 1000);
            int key = selectedWeight * 1001 + height;
            Typeface result = faces.get(key);
            if (result != null) return result;
            // FontTypefaceCache.buildTypeface uses these exact case-sensitive native axis tags.
            // A fresh Builder avoids mutating the plugin's shared builder/typeface caches.
            try {
                result = new Typeface.Builder(assets, family).setWeight(selectedWeight).setItalic(false)
                        .setFontVariationSettings("'wght' " + selectedWeight + ", 'opsz' 1010, 'HGHT' " + height)
                        .build();
                if (result != null) faces.put(key, result);
                return result;
            } catch (Throwable error) {
                if (buildWarnings++ < 3) ModuleDiagnostics.error("bigclock",
                        "Native clock font height variant unavailable; existing font retained", error);
                return null;
            }
        }
    }

    private static Resolver resolve(AssetManager assets, Metadata source) {
        String name = filename(source.family);
        if (!name.isEmpty()) {
            String candidate = "fonts/tunable/" + name;
            WeightRange weights=heightAxes(assets,candidate);
            if (weights!=null) return new Resolver(assets, candidate, source, false, weights);
            // The older native base-clock family uses the same Sans design without a height axis.
            if (name.equals("OPPOSans4.0No.ttf")) {
                weights=heightAxes(assets,DEFAULT_FAMILY);
                if(weights!=null)return new Resolver(assets, DEFAULT_FAMILY, source, false, weights);
            }
        }
        WeightRange weights=heightAxes(assets,DEFAULT_FAMILY);
        if (weights==null) return null;
        boolean fallback = !source.family.isEmpty() && !source.family.equals(DEFAULT_FAMILY);
        return new Resolver(assets, DEFAULT_FAMILY, source, fallback, weights);
    }

    private static String filename(String family) {
        if (family == null || family.indexOf('\0') >= 0) return "";
        int slash = Math.max(family.lastIndexOf('/'), family.lastIndexOf('\\'));
        String result = family.substring(slash + 1);
        return result.endsWith(".ttf") && !result.equals("..") ? result : "";
    }

    /** Confirm the actual asset's fvar records instead of assuming a height axis for a fixed font. */
    private static final class WeightRange {
        final int minimum,maximum;
        WeightRange(int minimum,int maximum) { this.minimum=minimum;this.maximum=maximum; }
    }
    private static WeightRange heightAxes(AssetManager assets, String path) {
        try (InputStream input = assets.open(path)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(24576);
            byte[] block = new byte[4096];
            int count;
            while ((count = input.read(block)) != -1) {
                if ((long) output.size() + count > MAX_FONT_BYTES) return null;
                output.write(block, 0, count);
            }
            byte[] font = output.toByteArray();
            if (font.length < 12) return null;
            int tableCount = u16(font, 4);
            if (12L + tableCount * 16L > font.length) return null;
            for (int i = 0; i < tableCount; i++) {
                int entry = 12 + i * 16;
                if (u32(font, entry) != FVAR) continue;
                long start = unsigned32(font, entry + 8), length = unsigned32(font, entry + 12);
                if (start + length > font.length || length < 16) return null;
                int base = (int) start, axisOffset = u16(font, base + 4);
                int axes = u16(font, base + 8), axisSize = u16(font, base + 10);
                if (axisOffset<16||axisSize < 20 || (long) axisOffset + axes * (long) axisSize > length) return null;
                boolean height = false;WeightRange weight=null;
                for (int j = 0; j < axes; j++) {
                    int record = base + axisOffset + j * axisSize, tag = u32(font, record);
                    float minimum = fixed(font, record + 4), maximum = fixed(font, record + 12);
                    if (tag == HGHT && minimum <= 1f && maximum >= 1000f) height = true;
                    if (tag == WGHT && minimum>=1f && maximum<=1000f && minimum<maximum)
                        weight=new WeightRange((int)Math.ceil(minimum),(int)Math.floor(maximum));
                }
                return height?weight:null;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static Metadata metadata(View template) {
        // ClockTimeView keeps the expanded family/axes separately from the
        // applied values interpolated by setFontStyleProgress. Read the complete
        // configuration tuple first, before a digit's constructor default (50)
        // or its parent's compact appliedFontHeight can become our base.
        View configured = template;
        for (int depth = 0; configured != null && depth < 16; depth++) {
            Access access = access(configured.getClass());
            if (access.configOwner) {
                String family = string(access.configFamily, configured);
                int height = number(access.configHeight, null, configured);
                int weight = number(access.configWeight, null, configured);
                if (!family.isEmpty() && height > 0 && weight > 0)
                    return new Metadata(family, height, weight);
            }
            ViewParent parent = configured.getParent();
            configured = parent instanceof View ? (View) parent : null;
        }
        String family = "";
        int height = 0, weight = 0;
        View cursor = template;
        for (int depth = 0; cursor != null && depth < 16; depth++) {
            Access access = access(cursor.getClass());
            if (family.isEmpty()) family = string(access.family, cursor);
            if (height <= 0) height = number(access.height, access.heightMethod, cursor);
            if (weight <= 0) weight = number(access.weight, access.weightMethod, cursor);
            if (!family.isEmpty() && height > 0 && weight > 0) break;
            ViewParent parent = cursor.getParent(); cursor = parent instanceof View ? (View) parent : null;
        }
        return new Metadata(family, height > 0 ? height : DEFAULT_HEIGHT, weight > 0 ? weight : DEFAULT_WEIGHT);
    }

    private static final class Metadata {
        final String family;
        final int height, weight;
        Metadata(String family, int height, int weight) { this.family = family; this.height = height; this.weight = weight; }
    }

    private static final class Access {
        final Field family, height, weight;
        final Field configFamily, configHeight, configWeight;
        final boolean configOwner;
        final Method heightMethod, weightMethod;
        Access(Class<?> type) {
            configOwner = configuredClockContainer(type);
            configFamily = configOwner ? field(type, "configFamilyName") : null;
            configHeight = configOwner ? field(type, "configFontHeight") : null;
            configWeight = configOwner ? field(type, "configFontWeight") : null;
            family = field(type, "fontFamilyName", "configFamilyName");
            height = field(type, "fontHeight", "appliedFontHeight", "configFontHeight");
            weight = field(type, "fontWeight", "appliedFontWeight", "configFontWeight");
            heightMethod = method(type, "getFontHeight"); weightMethod = method(type, "getFontWeight");
        }
    }

    private static boolean configuredClockContainer(Class<?> type) {
        for (; type != null; type = type.getSuperclass())
            if (type.getName().equals("com.oplus.keyguard.clock.digital.ui.view.ClockTimeView")) return true;
        return false;
    }

    private static synchronized Access access(Class<?> type) {
        Access result = ACCESS.get(type);
        if (result == null) { result = new Access(type); ACCESS.put(type, result); }
        return result;
    }

    private static String string(Field field, Object owner) {
        if (field != null) try {
            Object value = field.get(owner);
            if (value instanceof String) return ((String) value).trim();
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return "";
    }

    private static int number(Field field, Method method, Object owner) {
        if (field != null) try {
            Object value = field.get(owner);
            if (value instanceof Number && ((Number) value).floatValue() > 0f) return Math.round(((Number) value).floatValue());
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        if (method != null) try {
            Object value = method.invoke(owner);
            if (value instanceof Number) return Math.round(((Number) value).floatValue());
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return 0;
    }

    private static Field field(Class<?> type, String... names) {
        for (String name : names) for (Class<?> candidate = type; candidate != null; candidate = candidate.getSuperclass()) try {
            Field result = candidate.getDeclaredField(name); result.setAccessible(true); return result;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }

    private static Method method(Class<?> type, String name) {
        for (; type != null; type = type.getSuperclass()) try {
            Method result = type.getDeclaredMethod(name); result.setAccessible(true); return result;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }

    private static int u16(byte[] data, int offset) { return (data[offset] & 255) << 8 | data[offset + 1] & 255; }
    private static int u32(byte[] data, int offset) { return u16(data, offset) << 16 | u16(data, offset + 2); }
    private static long unsigned32(byte[] data, int offset) { return Integer.toUnsignedLong(u32(data, offset)); }
    private static float fixed(byte[] data, int offset) { return u32(data, offset) / 65536f; }
    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
    private static void warn(Throwable error) {
        if (warnings++ < 3) ModuleDiagnostics.error("bigclock",
                "Native clock variable font unavailable; existing font retained", error);
    }
}
