// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Per-card native surface ownership. No View clipping, layout or content replacement. */
final class QsDeviceCorners {
    private static final Set<String> traced = new HashSet<>();
    private static volatile boolean diagnostics;
    private static final List<WeakReference<Object>> diagnosticConfigs = new ArrayList<>();
    private static final String PLUGIN = "com.oplus.systemui.plugins.drawable.PluginDrawable";
    private static final String ROUND = "com.oplusos.systemui.common.outline.RoundRectOutlineProvider";
    private static final String SMOOTH = "com.oplus.deviceplugin.sdk.ui.view.drawable.SmoothViewOutlineProvider";
    private static final String[] BODY_FIELDS = {"y", "s", "v", "s", "q"};
    private static final String[] BODY_NAMES = {"rectangleCoLayout", "square_device_constraintlayout",
            "no_device_constraintLayout", "rectangle_entrance_constraintLayout", "square_entrance_constraintLayout"};
    private static final class Surface {
        final WeakReference<Object> object;
        final int kind;
        float nativeRadius, writtenRadius;
        float actualRadius;
        Float nativeWeight, writtenWeight;
        Float actualWeight;
        float[] nativeRadii, actualRadii;
        WeakReference<Object> proxy = new WeakReference<>(null);
        boolean owned, writeComplete;
        Surface(Object object, int kind) { this.object = new WeakReference<>(object); this.kind = kind; }
    }
    final List<Surface> surfaces = new ArrayList<>();
    float radius;
    private boolean writing;

    static void configureDiagnostics(boolean enabled) {
        synchronized (traced) {
            if (diagnostics != enabled) { traced.clear(); diagnosticConfigs.clear(); }
            diagnostics = enabled;
        }
    }
    static void resetGeometryDiagnostics() { synchronized (traced) { traced.clear(); } }
    static void tracePluginChild(View view, int depth) {
        if (!diagnostics || view == null) return;
        String name = view.getClass().getName();
        String key = "plugin-" + name;
        synchronized (traced) { if (traced.size() >= 40 || !traced.add(key)) return; }
        ClassLoader loader = view.getClass().getClassLoader();
        ModuleDiagnostics.info("qs_style", "Bound native plugin child " + name + " depth " + depth
                + " loader " + (loader == null ? "boot" : loader.getClass().getName())
                + " background " + simple(QsTileAppearance.call(view, "getBackground")));
    }

    static View body(View card) {
        for (int i = 0; i < QsPanelCorners.DEVICE_CLASSES.length; i++) {
            if (!QsTileAppearance.type(card, QsPanelCorners.DEVICE_CLASSES[i])) continue;
            Object actual = get(card, BODY_FIELDS[i]);
            // These final fields are the exact inner native ConstraintLayouts in this ROM.
            if (actual instanceof View && actual != card) return (View) actual;
            try {
                int id = card.getResources().getIdentifier(BODY_NAMES[i], "id", "com.android.systemui");
                View candidate = id == 0 ? null : card.findViewById(id);
                if (candidate != null && BODY_NAMES[i].equals(candidate.getResources().getResourceEntryName(candidate.getId())))
                    return candidate;
            } catch (RuntimeException absent) { }
            return null;
        }
        return null;
    }

    boolean refresh(View card, float dp, Float continuousWeight) throws ReflectiveOperationException {
        if (writing) return !surfaces.isEmpty();
        View body = body(card);
        if (body == null || body.getWidth() <= 0 || body.getHeight() <= 0) {
            trace(card, body, "await-body", 0); return false;
        }
        radius = QsTileCorners.radiusPixels(dp, body.getResources().getDisplayMetrics().density, body.getWidth(), body.getHeight());
        if (radius < 0f) return false;
        IdentityHashMap<Object, Integer> live = new IdentityHashMap<>();
        discover(live, QsTileAppearance.call(body, "getBackground"), card);
        discover(live, QsTileAppearance.call(card, "getBlurDrawable"), card);
        discover(live, QsTileAppearance.call(card, "getOutlineProvider"), card);
        discover(live, QsTileAppearance.call(body, "getOutlineProvider"), card);
        discover(live, QsTileAppearance.call(card, "getSmoothViewOutlineProvider"), card);
        // A live PluginDrawable owns its actual provider. Tracking the same
        // object twice can capture our already-written outline as a new native
        // baseline when the outer card starts referencing that provider later.
        for (Object surface : new ArrayList<>(live.keySet())) if (Integer.valueOf(1).equals(live.get(surface))) {
            Object provider = QsTileAppearance.call(surface, "getPathProvider");
            if (live.get(provider) != null && live.get(provider) == 3) live.remove(provider);
        }
        // MixColor has a separate native BlurConfig curvature. Some blur types never
        // consult pathProvider when selecting their visible silhouette.
        IdentityHashMap<Object, Object> proxies = new IdentityHashMap<>();
        for (Object surface : new ArrayList<>(live.keySet())) if (live.get(surface) == 1
                && QsTileAppearance.type(surface, "com.oplus.systemui.plugins.drawable.MixColorPluginDrawable")) {
            Object auto = get(surface, "autoBlurDrawable");
            Object proxy = QsTileAppearance.call(auto, "getViewBlurProxy");
            Object config = QsTileAppearance.call(proxy, "getBlurConfig");
            if (QsTileAppearance.type(config, "com.oplusos.systemui.common.blurability.BlurConfig")) {
                live.put(config, 5); proxies.put(config, proxy);
            }
        }
        if (live.isEmpty()) { trace(card, body, "unsupported-shape", 0); return false; }
        writing = true;
        try {
            // g() can replace the background after h() bound the old outer outline.
            // Keep the old live outline as a separate surface, retire only unused objects.
            List<Surface> retired = new ArrayList<>();
            for (Surface old : surfaces) {
                Object object = old.object.get();
                if (object == null || !live.containsKey(object)) retired.add(old);
            }
            restoreSurfaces(retired); surfaces.removeAll(retired);
            List<Surface> current = new ArrayList<>();
            for (Object object : live.keySet()) {
                Surface found = null;
                for (Surface old : surfaces) if (old.object.get() == object) { found = old; break; }
                if (found == null) { found = new Surface(object, live.get(object)); surfaces.add(found); }
                if (found.kind == 5) found.proxy = new WeakReference<>(proxies.get(object));
                capture(found);
                current.add(found);
            }
            // Capture all original values before any shared path is changed.
            // Native PluginDrawable.invalidatePath may also update blur stroke config.
            // Reconcile the visible blur curvature last, after every native provider.
            // Claim the whole transaction first: a provider can partially alter a
            // BlurConfig before throwing, even when its config write has not run.
            boolean changed = false, providerChanged = false;
            for (Surface surface : current) {
                Float weight = surface.kind == 4 || continuousWeight == null ? surface.nativeWeight : continuousWeight;
                boolean same = matches(surface, radius, weight);
                surface.owned = true;
                surface.writtenRadius = radius; surface.writtenWeight = weight;
                if (!same) { surface.writeComplete = false; if (surface.kind != 5) providerChanged = true; }
            }
            for (int pass = 0; pass < 2; pass++) for (Surface surface : current) {
                if ((surface.kind == 5) != (pass == 1)) continue;
                if (matches(surface, radius, surface.writtenWeight) && (surface.kind != 5 || !providerChanged)) {
                    surface.writeComplete = true; continue;
                }
                write(surface, radius, surface.writtenWeight, null); changed = true;
            }
            traceCurves(card);
            if (changed) { body.invalidateOutline(); body.invalidate(); card.invalidateOutline(); card.invalidate(); }
            trace(card, body, "owned", current.size());
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            try { restore(); } catch (ReflectiveOperationException | RuntimeException restoreError) { error.addSuppressed(restoreError); }
            throw error;
        } finally { writing = false; }
    }

    private static void discover(IdentityHashMap<Object, Integer> live, Object object, View card) {
        if (object == null) return;
        if (QsTileAppearance.type(object, SMOOTH)) live.put(object, 2);
        else if (QsTileAppearance.type(object, ROUND)) live.put(object, 3);
        else if (object instanceof Drawable && isPlugin(object, card)) live.put(object, 1);
        else if (object instanceof GradientDrawable) live.put(object, 4);
    }
    private static boolean isPlugin(Object object, View card) {
        try { return Class.forName(PLUGIN, false, card.getClass().getClassLoader()).isInstance(object); }
        catch (ClassNotFoundException absent) { return false; }
    }
    private static String type(Object value) { return value == null ? "none" : value.getClass().getName(); }
    private static String simple(Object value) { return value == null ? "none" : value.getClass().getSimpleName(); }
    private static void trace(View card, View body, String stage, int count) {
        if (!diagnostics) return;
        String key = card.getClass().getName() + ":" + stage;
        synchronized (traced) { if (traced.size() >= 40 || !traced.add(key)) return; }
        ModuleDiagnostics.info("qs_style", "Device native shape " + stage + " " + simple(card)
                + " bodySize "
                + (body == null ? "none" : body.getWidth() + "x" + body.getHeight())
                + " background " + simple(body == null ? null : QsTileAppearance.call(body, "getBackground"))
                + " outer " + simple(QsTileAppearance.call(card, "getOutlineProvider")) + " surfaces " + count);
    }
    private void traceCurves(View card) {
        if (!diagnostics) return;
        String key = card.getClass().getName() + ":blur-readback";
        synchronized (traced) { if (traced.size() >= 40 || !traced.add(key)) return; }
        for (Surface surface : surfaces) if (surface.kind == 5) {
            Object config = surface.object.get();
            if (config == null) continue;
            int id;
            synchronized (traced) {
                id = 0;
                for (int i = 0; i < diagnosticConfigs.size(); i++)
                    if (diagnosticConfigs.get(i).get() == config) { id = i + 1; break; }
                if (id == 0 && diagnosticConfigs.size() < 40) {
                    diagnosticConfigs.add(new WeakReference<>(config)); id = diagnosticConfigs.size();
                }
            }
            try {
                ModuleDiagnostics.info("qs_style", "Device blur curve " + simple(card) + " config " + id
                        + " scalar " + QsTileAppearance.invoke(config, "getCornerRadius")
                        + " corners " + java.util.Arrays.toString(blurRadii(config)) + " native sync applied");
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                ModuleDiagnostics.info("qs_style", "Device blur readback unavailable " + simple(card));
            }
        }
    }

    private static void capture(Surface surface) throws ReflectiveOperationException {
        Object object = surface.object.get();
        if (object == null) return;
        Object provider = surface.kind == 1 ? QsTileAppearance.invoke(object, "getPathProvider") : object;
        float radius;
        Float weight;
        if (surface.kind == 2) {
            radius = numeric(get(provider, "radius")); weight = numeric(get(provider, "weight"));
        } else if (surface.kind == 5) {
            radius = numeric(QsTileAppearance.invoke(object, "getCornerRadius"));
            Object raw = QsTileAppearance.invoke(object, "getRadiusWeight");
            weight = raw == null ? null : numeric(raw);
        } else if (surface.kind == 4) {
            GradientDrawable gradient = (GradientDrawable) object;
            radius = gradient.getCornerRadius(); weight = null;
        } else {
            Object result = QsTileAppearance.call(provider, "getCornerRadius");
            if (!(result instanceof Number)) result = QsTileAppearance.invoke(provider, "getCornerRadius", (Object) null);
            radius = numeric(result);
            Object raw = QsTileAppearance.call(provider, "getCornerWeight");
            // Null is a valid native standard-round weight.
            if (raw == null) {
                try { raw = QsTileAppearance.invoke(provider, "getCornerWeight", (Object) null); }
                catch (NoSuchMethodException noDrawableGetter) { }
            }
            weight = raw == null ? null : numeric(raw);
        }
        if (!Float.isFinite(radius) || surface.kind != 5 && radius < 0f || weight != null && !Float.isFinite(weight))
            throw new IllegalStateException("Invalid native card surface");
        float[] actualRadii = surface.kind == 5 ? blurRadii(object)
                : surface.kind == 4 ? ((GradientDrawable) object).getCornerRadii() : null;
        surface.actualRadius = radius; surface.actualWeight = weight; surface.actualRadii = actualRadii;
        if (actualRadii != null) for (float corner : actualRadii) {
            if (!Float.isFinite(corner)) throw new IllegalStateException("Invalid native blur curvature");
        }
        if (surface.kind == 5) {
            if (!surface.owned || surface.nativeRadii == null) {
                surface.nativeRadius = radius; surface.nativeRadii = actualRadii;
            } else {
                if (radius != surface.writtenRadius) surface.nativeRadius = radius;
                // A native transition may change only one corner. Do not turn our
                // unchanged other three written values into its restoration baseline.
                for (int i = 0; i < actualRadii.length; i++) if (actualRadii[i] != surface.writtenRadius)
                    surface.nativeRadii[i] = actualRadii[i];
            }
            if (!surface.owned || !equal(weight, surface.writtenWeight)) surface.nativeWeight = weight;
            return;
        }
        boolean changedRadii = false;
        if (surface.kind == 4 && actualRadii != null)
            for (float corner : actualRadii) if (corner != surface.writtenRadius) { changedRadii = true; break; }
        if (!surface.owned || radius != surface.writtenRadius || changedRadii) {
            surface.nativeRadius = radius;
            if (surface.kind == 4) {
                float[] radii = ((GradientDrawable) object).getCornerRadii();
                surface.nativeRadii = radii == null ? null : radii.clone();
            }
        }
        if (!surface.owned || !equal(weight, surface.writtenWeight)) surface.nativeWeight = weight;
    }
    private static float numeric(Object object) {
        if (!(object instanceof Number)) throw new IllegalStateException("Missing native shape parameter");
        return ((Number) object).floatValue();
    }
    private static boolean equal(Float one, Float two) { return one == null ? two == null : one.equals(two); }
    private static boolean matches(Surface surface, float radius, Float weight) {
        if (surface.actualRadius != radius || !equal(surface.actualWeight, weight)) return false;
        if ((surface.kind == 5 || surface.kind == 4) && surface.actualRadii != null)
            for (float corner : surface.actualRadii) if (corner != radius) return false;
        return true;
    }
    private static void write(Surface surface, float radius, Float weight, float[] radii) throws ReflectiveOperationException {
        Object object = surface.object.get();
        if (object == null) return;
        surface.writeComplete = false;
        if (surface.kind == 1) QsTileAppearance.invoke(object, "setCornerRadius", radius, weight);
        else if (surface.kind == 2) {
            Class<?> round = Class.forName("com.oplus.deviceplugin.sdk.entity.SmoothRoundCorner", false, object.getClass().getClassLoader());
            QsTileAppearance.invoke(object, "setSmoothCorner", round.getConstructor(float.class, float.class).newInstance(radius, weight));
        } else if (surface.kind == 3) QsTileAppearance.invoke(object, "update", radius, weight);
        else if (surface.kind == 5) {
            QsTileAppearance.invoke(object, "setCornerRadius", radius);
            QsTileAppearance.invoke(object, "setRadiusWeight", weight);
            if (radii != null) {
                String[] fields = {"leftTopCornerRadius", "rightTopCornerRadius", "rightBottomCornerRadius", "leftBottomCornerRadius"};
                for (int i = 0; i < fields.length; i++) {
                    Field field = object.getClass().getDeclaredField(fields[i]); field.setAccessible(true); field.setFloat(object, radii[i]);
                }
            }
            Object proxy = surface.proxy.get();
            if (proxy != null) QsTileAppearance.invoke(proxy, "applyBlurConfig");
        }
        else if (radii == null) ((GradientDrawable) object).setCornerRadius(radius);
        else ((GradientDrawable) object).setCornerRadii(radii.clone());
        if (object instanceof Drawable) ((Drawable) object).invalidateSelf();
        surface.writeComplete = true;
    }
    private static float[] blurRadii(Object config) throws ReflectiveOperationException {
        return new float[]{numeric(QsTileAppearance.invoke(config, "getLeftTopCornerRadius")),
                numeric(QsTileAppearance.invoke(config, "getRightTopCornerRadius")),
                numeric(QsTileAppearance.invoke(config, "getRightBottomCornerRadius")),
                numeric(QsTileAppearance.invoke(config, "getLeftBottomCornerRadius"))};
    }
    private static void restore(Surface surface) throws ReflectiveOperationException {
        if (!surface.owned) return;
        write(surface, surface.nativeRadius, surface.nativeWeight, surface.nativeRadii);
        surface.owned = false;
    }
    private static void restoreSurfaces(List<Surface> selected) throws ReflectiveOperationException {
        ReflectiveOperationException failure = null;
        RuntimeException runtimeFailure = null;
        // Preserve independent current native snapshots before restoring a provider
        // that may itself rewrite stroke/blur config through the ROM's side effects.
        for (Surface surface : selected) if (surface.owned && surface.writeComplete) try { capture(surface); }
        catch (ReflectiveOperationException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        catch (RuntimeException error) { if (runtimeFailure == null) runtimeFailure = error; else runtimeFailure.addSuppressed(error); }
        for (int pass = 0; pass < 2; pass++) for (Surface surface : selected) {
            if ((surface.kind == 5) != (pass == 1)) continue;
            try { restore(surface); }
            catch (ReflectiveOperationException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
            catch (RuntimeException error) { if (runtimeFailure == null) runtimeFailure = error; else runtimeFailure.addSuppressed(error); }
        }
        if (runtimeFailure != null) { if (failure != null) runtimeFailure.addSuppressed(failure); throw runtimeFailure; }
        if (failure != null) throw failure;
    }
    void restore() throws ReflectiveOperationException {
        restoreSurfaces(surfaces);
        surfaces.clear();
    }
    private static Object get(Object owner, String name) {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) try {
            Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner);
        } catch (NoSuchFieldException absent) { }
        catch (ReflectiveOperationException | RuntimeException failure) { return null; }
        return null;
    }
}
