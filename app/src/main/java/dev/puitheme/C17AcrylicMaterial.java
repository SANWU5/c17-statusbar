// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** A cached native material variant; no replacement View, bitmap capture, blur or polling. */
final class C17AcrylicMaterial {
    private static final String MATERIAL = "com.oplus.posteffect.agsl.DrawableShader";
    private static final String EFFECTS = "com.oplus.posteffect.agsl.effects.";
    private static final String TINT = "c17AcrylicTint";
    private static final String MAIN = "half4 main(float2 position) {";
    private static final int MAX_MATERIALS = 256;
    private final Map<Object, Cached> materials = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Class<?>, Access> contracts = new ConcurrentHashMap<>();

    static final class Swap {
        final Object material;
        final Field shaderField;
        final RuntimeShader original, variant;
        final Paint paint;
        final Shader oldPaint;
        Swap(Object material, Field field, RuntimeShader original, RuntimeShader variant, Paint paint) {
            this.material = material; shaderField = field; this.original = original; this.variant = variant;
            this.paint = paint; oldPaint = paint == null ? null : paint.getShader();
        }
        void restore() {
            try { if (shaderField.get(material) == variant) shaderField.set(material, original); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { /* Never overwrite a native replacement. */ }
            if (paint != null && paint.getShader() == variant) paint.setShader(oldPaint);
        }
    }

    Swap prepare(Drawable engine, View host, int light, int dark, boolean tint) {
        try {
            if (engine == null || host == null || !Boolean.TRUE.equals(QsTileAppearance.field(engine, "enableShader"))) return null;
            Object material = QsTileAppearance.field(engine, "drawableShader");
            if (material == null || !MATERIAL.equals(material.getClass().getName()) && !hasNativeBase(material.getClass())) return null;
            Access access = contract(material.getClass());
            if (!access.valid()) return null;
            Object shader = access.shader.get(material), params = access.summary.get(material);
            if (!(shader instanceof RuntimeShader) || !(params instanceof List)) return null;
            List<?> colors = (List<?>) params;
            if (colors.size() > 32) return null;
            Object meta = QsTileAppearance.field(material, "metaBallParams");
            if (Boolean.TRUE.equals(QsTileAppearance.field(meta, "valid"))) return null;
            Object mask = access.mask.get(material), corner = access.corner.get(material);
            Object cornerType = QsTileAppearance.invoke(corner, "getType");
            if (!(mask instanceof Integer) || cornerType == null) return null;
            Object all = access.effects.invoke(material);
            if (!(all instanceof List) || ((List<?>) all).size() > 32) return null;
            List<?> effects = (List<?>) all;
            Cached cached = materials.get(material);
            if (cached == null || !cached.signature.matches((RuntimeShader) shader, mask, colors.size(), cornerType, effects)) {
                Signature signature = Signature.capture((RuntimeShader) shader, mask, colors.size(), cornerType, effects);
                if (signature == null) return null;
                Class<?> builder = Class.forName("com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt", false, material.getClass().getClassLoader());
                StringBuilder source = new StringBuilder();
                // Build only the actual native base and its non-optical clipping. Do not mutate the native effect list.
                QsTileAppearance.invoke(builder, "buildShaderString", source, mask, colors.size(), cornerType, false, signature.retained());
                String text = decorate(source.toString());
                if (text == null) return null;
                RuntimeShader variant = cached != null && cached.source.equals(text) ? cached.variant : new RuntimeShader(text);
                cached = new Cached(text, variant, signature, colors.size());
                if (materials.size() >= MAX_MATERIALS && !materials.containsKey(material)) return null;
                materials.put(material, cached);
            }
            RuntimeShader variant = cached.variant;
            // The same native upload methods supply the live bitmap, geometry, matrix and antialiasing.
            access.base.invoke(material, variant);
            access.common.invoke(material, variant);
            variant.setIntUniform("uEnableBlend", Boolean.TRUE.equals(access.blend.get(material)) ? 1 : 0);
            for (int i = 0; i < colors.size(); i++) {
                Object param = colors.get(i), mode = QsTileAppearance.field(param, "mode"), color = QsTileAppearance.field(param, "color");
                if (!(mode instanceof Number) || !(color instanceof Integer)) return null;
                int argb = (Integer) color, at = i * 5;
                cached.blends[at] = ((Number) mode).intValue();
                cached.blends[at + 1] = ((argb >>> 16) & 255) / 255f;
                cached.blends[at + 2] = ((argb >>> 8) & 255) / 255f;
                cached.blends[at + 3] = (argb & 255) / 255f;
                cached.blends[at + 4] = (argb >>> 24) / 255f;
            }
            if (cached.blends.length != 0) variant.setFloatUniform("u_multiBlendParams", cached.blends);
            for (int i = 0; i < cached.signature.effects.length; i++) {
                Effect signature = cached.signature.effects[i];
                if (!signature.active || signature.optical) continue;
                Object effect = signature.owner.get();
                if (effect == null) return null;
                QsTileAppearance.invoke(effect, "pushUniforms", variant);
            }
            boolean night = (host.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int argb = night ? dark : light;
            cached.tint[0] = ((argb >>> 16) & 255) / 255f;
            cached.tint[1] = ((argb >>> 8) & 255) / 255f;
            cached.tint[2] = (argb & 255) / 255f;
            cached.tint[3] = tint ? (argb >>> 24) / 255f : 0f;
            variant.setFloatUniform(TINT, cached.tint);
            Object value = QsTileAppearance.field(engine, "drawableShaderPaint");
            if (!(value instanceof Paint)) return null;
            Swap result = new Swap(material, access.shader, (RuntimeShader) shader, variant, (Paint) value);
            access.shader.set(material, variant);
            return result;
        } catch (ReflectiveOperationException | RuntimeException unsupported) { return null; }
    }

    void clear() { materials.clear(); contracts.clear(); }
    void detach(Drawable engine) { Object material = QsTileAppearance.field(engine, "drawableShader"); if (material != null) materials.remove(material); }

    /** Native shape/clip effects survive; only the three audited optical layers leave. */
    static List<?> drawingEffects(List<?> effects, boolean removeOptics) {
        if (!removeOptics) return effects;
        ArrayList<Object> result = new ArrayList<>(effects.size());
        for (Object effect : effects) if (!optical(effect)) result.add(effect);
        return result;
    }

    static boolean optical(Object effect) {
        if (effect == null) return false;
        String name = effect.getClass().getName();
        return name.equals(EFFECTS + "GradientStrokeEffect") || name.equals(EFFECTS + "OpticsEffect")
                || name.equals(EFFECTS + "InnerShadowEffect");
    }

    /** Only the audited native final return, after base mixing and before the original shape mask. */
    static String decorate(String source) {
        if (source == null || source.indexOf(MAIN) < 0 || source.indexOf(TINT) >= 0) return null;
        String end = "return outputCol*shape;}";
        int at = source.lastIndexOf(end);
        if (at < 0) { end = "return outputCol;}"; at = source.lastIndexOf(end); }
        if (at < 0 || source.indexOf(end) != at) return null;
        String mix = "outputCol = half4(mix(outputCol.rgb, half3(c17AcrylicTint.rgb), c17AcrylicTint.a), outputCol.a);\n";
        return "uniform float4 " + TINT + ";\n" + source.substring(0, at) + mix + source.substring(at);
    }

    private Access contract(Class<?> type) {
        Access existing = contracts.get(type); if (existing != null) return existing;
        Access created = new Access(type), raced = contracts.putIfAbsent(type, created);
        return raced == null ? created : raced;
    }
    private static boolean hasNativeBase(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) if (MATERIAL.equals(current.getName())) return true;
        return false;
    }
    private static boolean immutable(Object value) { return value instanceof String || value instanceof Enum || value instanceof Integer || value instanceof Long || value instanceof Boolean || value instanceof Float || value instanceof Double; }
    private static final class Cached {
        final String source; final RuntimeShader variant; final Signature signature; final float[] blends, tint = new float[4];
        Cached(String source, RuntimeShader shader, Signature signature, int count) {
            this.source = source; variant = shader; this.signature = signature; blends = new float[count * 5];
        }
    }
    private static final class Signature {
        final WeakReference<RuntimeShader> original;
        final Object mask, corner; final int count; final Effect[] effects;
        Signature(RuntimeShader original, Object mask, int count, Object corner, Effect[] effects) { this.original = new WeakReference<>(original); this.mask = mask; this.count = count; this.corner = corner; this.effects = effects; }
        static Signature capture(RuntimeShader original, Object mask, int count, Object corner, List<?> effects) throws ReflectiveOperationException {
            if (!immutable(mask) || !immutable(corner)) return null;
            Effect[] values = new Effect[effects.size()];
            for (int i = 0; i < values.length; i++) { values[i] = Effect.capture(effects.get(i)); if (values[i] == null) return null; }
            return new Signature(original, mask, count, corner, values);
        }
        boolean matches(RuntimeShader original, Object mask, int count, Object corner, List<?> effects) throws ReflectiveOperationException {
            if (this.original.get() != original || !Objects.equals(this.mask, mask) || this.count != count || !Objects.equals(this.corner, corner) || this.effects.length != effects.size()) return false;
            for (int i = 0; i < this.effects.length; i++) if (!this.effects[i].matches(effects.get(i))) return false;
            return true;
        }
        List<Object> retained() { ArrayList<Object> result = new ArrayList<>(); for (Effect effect : effects) if (!effect.optical && effect.owner.get() != null) result.add(effect.owner.get()); return result; }
    }
    private static final class Effect {
        final WeakReference<Object> owner; final boolean active, optical; final Object key;
        Effect(Object owner, boolean active, boolean optical, Object key) { this.owner = new WeakReference<>(owner); this.active = active; this.optical = optical; this.key = key; }
        static Effect capture(Object object) throws ReflectiveOperationException {
            if (object == null) return null;
            String name = object.getClass().getName();
            boolean optical = optical(object);
            if (!optical && !name.equals(EFFECTS + "CustomClipEffect")) return null;
            Object active = QsTileAppearance.invoke(object, "isEnabled"); if (!(active instanceof Boolean)) return null;
            Object key = Boolean.TRUE.equals(active) ? QsTileAppearance.invoke(object, "getStructuralKey") : null;
            if (Boolean.TRUE.equals(active) && key != null && !immutable(key)) return null;
            return new Effect(object, (Boolean) active, optical, key);
        }
        boolean matches(Object object) throws ReflectiveOperationException {
            if (owner.get() != object || !Objects.equals(active, QsTileAppearance.invoke(object, "isEnabled"))) return false;
            Object next = active ? QsTileAppearance.invoke(object, "getStructuralKey") : null;
            return (next == null || immutable(next)) && Objects.equals(key, next);
        }
    }
    private static final class Access {
        final Field shader, summary, mask, corner, blend;
        final Method base, common, effects;
        Access(Class<?> type) {
            shader = field(type, "shader"); summary = field(type, "summaryBlendParam"); mask = field(type, "blendAlgorithmMask"); corner = field(type, "mCornerParams"); blend = field(type, "enableBlend");
            base = method(type, "setBaseUniform", RuntimeShader.class); common = method(type, "setCommonArray", RuntimeShader.class); effects = method(type, "getAllEffects");
        }
        boolean valid() { return shader != null && summary != null && mask != null && corner != null && blend != null && base != null && common != null && effects != null; }
    }
    private static Field field(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try { Field result = current.getDeclaredField(name); result.setAccessible(true); return result; }
        catch (NoSuchFieldException absent) { } catch (RuntimeException denied) { return null; }
        return null;
    }
    private static Method method(Class<?> type, String name, Class<?>... args) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try { Method result = current.getDeclaredMethod(name, args); result.setAccessible(true); return result; }
        catch (NoSuchMethodException absent) { } catch (RuntimeException denied) { return null; }
        return null;
    }
}
