package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.RuntimeShader;
import android.os.Bundle;
import android.view.View;
import com.oplus.posteffect.agsl.DrawableShader;
import com.oplus.posteffect.agsl.effects.GradientStrokeEffect;
import com.oplus.posteffect.agsl.effects.OpticsEffect;
import com.oplus.posteffect.agsl.effects.InnerShadowEffect;
import com.oplus.posteffect.drawable.BlendDrawable;
import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;

/** Exercises native optical uploads and ownership, without replacing glass, blur or interaction. */
public final class C17HighlightRemovalCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }
    private static void same(Object expected, Object actual) { checks++; if (expected != actual) throw new AssertionError("identity changed"); }
    private static void near(float expected, float actual) {
        checks++; if (Math.abs(expected - actual) > .00001f) throw new AssertionError("expected " + expected + " but got " + actual);
    }
    public static final class HookedShader extends RuntimeShader {
        final C17HighlightRemoval helper;
        float[] lastArgument;
        boolean fail;
        HookedShader(C17HighlightRemoval helper) { super("nativeBlur nativeBase nativeShadow gradientStroke optics"); this.helper = helper; }
        @Override public void setFloatUniform(String name, float... values) {
            try { helper.writeUniform(this, name, values, actual -> { lastArgument = actual; if (fail) throw new IllegalStateException("native upload failed"); super.setFloatUniform(name, actual); return null; }); }
            catch (RuntimeException failure) { throw failure; }
            catch (Throwable unexpected) { throw new AssertionError(unexpected); }
        }
        void original(String name, float[] values) { super.setFloatUniform(name, values); }
    }
    public static final class NativeMaterial extends DrawableShader {
        final GradientStrokeEffect stroke = new GradientStrokeEffect();
        final OpticsEffect optics = new OpticsEffect();
        final InnerShadowEffect inner = new InnerShadowEffect();
        int lookups;
        NativeMaterial() { inner.nativeArrays = true; }
        public Object getEffect(String name) {
            lookups++;
            return "gradientStroke".equals(name) ? stroke : "optics".equals(name) ? optics : "innerShadow".equals(name) ? inner : null;
        }
    }
    private static Bundle settings(boolean enabled) { Bundle result = new Bundle(); result.putBoolean(C17HighlightRemoval.ENABLED, enabled); return result; }
    private static void bind(C17HighlightRemoval helper, View view, BlendDrawable engine) {
        try (C17HighlightRemoval.SurfaceScope ignored = helper.beginSurface(view)) { helper.bindDrawable(engine); }
    }
    private static void arrays(float[] expected, float[] actual, int... zero) {
        equal(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            boolean removed = false; for (int index : zero) removed |= i == index;
            near(removed ? 0f : expected[i], actual[i]);
        }
    }
    private static Object privateField(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    public static void main(String[] arguments) throws Throwable {
        equal(false, StatusBarSettings.BOOLEAN_DEFAULTS.get(C17HighlightRemoval.ENABLED));
        SettingsCatalog.Group group = SettingsCatalog.group("c17_highlight_removal");
        equal(C17HighlightRemoval.ENABLED, group.masterKey);
        equal(false, SettingsCatalog.item(C17HighlightRemoval.ENABLED).defaultValue);
        equal(true, SettingsCatalog.groups(SettingsCatalog.OTHER).contains(group));
        equal(false, SettingsCatalog.groups(SettingsCatalog.NOTIFICATION).contains(group));
        equal(false, SettingsCatalog.groups(SettingsCatalog.CONTROL_CENTER).contains(group));
        C17HighlightRemoval helper = new C17HighlightRemoval();
        equal(false, helper.isSurface(new View(null)));
        equal(true, helper.isSurface(new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView()));
        equal(true, helper.isSurface(new com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar()));
        equal(true, helper.isSurface(new com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView()));
        View view = new com.android.systemui.statusbar.notification.row.NotificationBackgroundView(); BlendDrawable engine = new BlendDrawable();
        NativeMaterial material = new NativeMaterial();
        equal(true, QsTileAppearance.setField(engine, "drawableShader", material));
        HookedShader shader = new HookedShader(helper); material.shader = shader;
        for (int i = 0; i < material.stroke.values.length; i++) material.stroke.values[i] = .02f * (i + 1);
        for (int i = 0; i < material.optics.values.length; i++) material.optics.values[i] = .03f * (i + 1);
        for (int i = 0; i < material.inner.values.length; i++) material.inner.values[i] = .04f * (i + 1);
        material.stroke.pushUniforms(shader); material.optics.pushUniforms(shader);
        equal(false, helper.enabled()); same(material.optics.values, shader.lastArgument);
        bind(helper, view, engine); equal(0, material.lookups); equal(false, engine.contentDirty);
        equal(false, helper.skipSpotlight(view));
        int invalidations = view.invalidations;
        helper.configure(settings(true)); equal(true, helper.enabled()); equal(true, engine.contentDirty);
        equal(invalidations + 1, view.invalidations); equal(3, material.lookups);
        arrays(material.stroke.values, shader.floats.get("u_edgeArray"), 7, 8);
        arrays(material.optics.values, shader.floats.get("u_opticsArray"), 3);
        arrays(material.inner.values, shader.floats.get("u_shadowArray"), 3, 4, 5);
        near(.16f, material.stroke.values[7]); near(.18f, material.stroke.values[8]); near(.12f, material.optics.values[3]);
        equal(true, helper.skipSpotlight(view)); equal(false, helper.skipSpotlight(new View(null))); equal(false, helper.skipSpotlight(null));
        try (C17HighlightRemoval.SurfaceScope surface = helper.beginSurface(view)) {
            equal(true, helper.skipSpotlight(null));
            try (C17HighlightRemoval.SurfaceScope nested = helper.beginSurface(new View(null))) { equal(false, helper.skipSpotlight(null)); }
            equal(true, helper.skipSpotlight(null));
        }
        equal(false, helper.skipSpotlight(null));
        engine.contentDirty = false;
        int uploads = shader.uploadCount, lookups = material.lookups;
        material.stroke.pushUniforms(shader); float[] edgeBuffer = shader.lastArgument;
        material.optics.pushUniforms(shader); float[] opticsBuffer = shader.lastArgument;
        material.inner.pushUniforms(shader); float[] innerBuffer = shader.lastArgument;
        C17HighlightRemoval.SurfaceScope reusableSurface = helper.beginSurface(view); reusableSurface.close();
        C17HighlightRemoval.RecordScope reusableRecord = helper.beginRecord(engine); reusableRecord.close();
        for (int i = 0; i < 10000; i++) {
            material.stroke.values[0] = i / 10000f; material.stroke.values[7] = i / 20000f; material.stroke.values[8] = i / 30000f;
            material.optics.values[1] = i / 15000f; material.optics.values[3] = i / 12000f;
            bind(helper, view, engine);
            try (C17HighlightRemoval.SurfaceScope scope = helper.beginSurface(view)) { same(reusableSurface, scope); }
            try (C17HighlightRemoval.RecordScope record = helper.beginRecord(engine)) {
                same(reusableRecord, record);
                material.stroke.pushUniforms(shader); same(edgeBuffer, shader.lastArgument);
                material.optics.pushUniforms(shader); same(opticsBuffer, shader.lastArgument);
                material.inner.values[3] = i / 18000f;
                material.inner.pushUniforms(shader); same(innerBuffer, shader.lastArgument);
                arrays(material.stroke.values, shader.floats.get("u_edgeArray"), 7, 8);
                arrays(material.optics.values, shader.floats.get("u_opticsArray"), 3);
                arrays(material.inner.values, shader.floats.get("u_shadowArray"), 3, 4, 5);
            }
        }
        equal(lookups, material.lookups); equal(uploads + 30003, shader.uploadCount); equal(false, engine.contentDirty);
        float[] blur = {.4f, .6f}, shadow = {.2f, .5f}, wrongSize = {.3f, .9f};
        shader.setFloatUniform("u_multiBlendParams", blur); same(blur, shader.lastArgument);
        shader.setFloatUniform("u_innerShadowArray", shadow); same(shadow, shader.lastArgument);
        shader.setFloatUniform("u_edgeArray", wrongSize); same(wrongSize, shader.lastArgument);
        material.stroke.pushUniforms(shader);
        HookedShader unrelated = new HookedShader(helper); unrelated.setFloatUniform("u_edgeArray", material.stroke.values);
        same(material.stroke.values, unrelated.lastArgument); arrays(material.stroke.values, unrelated.floats.get("u_edgeArray"));
        // Replacement uniforms arrive before QsNativeGlassFill swaps the shader field.
        HookedShader replacement = new HookedShader(helper);
        try (C17HighlightRemoval.RecordScope record = helper.beginRecord(engine)) {
            material.stroke.pushUniforms(replacement); material.optics.pushUniforms(replacement);
        }
        arrays(material.stroke.values, replacement.floats.get("u_edgeArray"), 7, 8);
        arrays(material.optics.values, replacement.floats.get("u_opticsArray"), 3);
        same(shader, material.shader); same(null, engine.drawableShaderPaint.getShader());
        // A failed upload must leave the last successful native alpha available for restoration.
        float latestAlpha = material.stroke.values[7]; material.stroke.values[7] = .99f; shader.fail = true;
        try { material.stroke.pushUniforms(shader); throw new AssertionError("missing failure"); } catch (IllegalStateException expected) { checks++; }
        shader.fail = false; helper.configure(settings(false)); equal(false, helper.enabled());
        near(latestAlpha, shader.floats.get("u_edgeArray")[7]);
        arrays(material.optics.values, shader.floats.get("u_opticsArray"));
        arrays(material.inner.values, shader.floats.get("u_shadowArray"));
        near(latestAlpha, replacement.floats.get("u_edgeArray")[7]);
        equal(true, engine.contentDirty); equal(false, helper.skipSpotlight(view));
        float[] fresh = material.stroke.values.clone(); fresh[7] = .17f; shader.setFloatUniform("u_edgeArray", fresh);
        helper.configure(settings(false)); arrays(fresh, shader.floats.get("u_edgeArray"));
        // A different native shader identity is primed once, rather than retaining an old RenderNode.
        HookedShader rotated = new HookedShader(helper); material.shader = rotated; helper.configure(settings(true));
        arrays(material.stroke.values, rotated.floats.get("u_edgeArray"), 7, 8);
        int before = material.lookups; bind(helper, view, engine); equal(before, material.lookups);
        HookedShader second = new HookedShader(helper); material.shader = second; engine.contentDirty = false; bind(helper, view, engine);
        arrays(material.optics.values, second.floats.get("u_opticsArray"), 3); equal(true, engine.contentDirty); equal(before + 3, material.lookups);
        Bundle safe = settings(true); safe.putBoolean(StatusBarSettings.SAFE_MODE, true); helper.configure(safe);
        equal(false, helper.enabled()); arrays(material.stroke.values, second.floats.get("u_edgeArray"));
        arrays(material.optics.values, second.floats.get("u_opticsArray"));
        arrays(material.inner.values, second.floats.get("u_shadowArray"));
        helper.configure(settings(true)); material.optics.values[3] = .31f; material.optics.pushUniforms(second);
        helper.release(); equal(false, helper.enabled()); near(.31f, second.floats.get("u_opticsArray")[3]);
        arrays(material.inner.values, second.floats.get("u_shadowArray"));
        equal(0, ((Map<?, ?>) privateField(helper, "engines")).size()); equal(0, ((Map<?, ?>) privateField(helper, "shaders")).size());
        equal(0, ((Map<?, ?>) privateField(helper, "surfaces")).size());
        helper.configure(settings(true)); equal(false, helper.enabled());
        // Identity maps cannot keep the native engine/surface alive through their own values.
        C17HighlightRemoval weak = new C17HighlightRemoval(); View weakView = new com.android.systemui.statusbar.notification.row.NotificationBackgroundView(); BlendDrawable weakEngine = new BlendDrawable();
        bind(weak, weakView, weakEngine);
        Map<?, ?> engines = (Map<?, ?>) privateField(weak, "engines"), shaders = (Map<?, ?>) privateField(weak, "shaders");
        equal(true, privateField(engines.values().iterator().next(), "host") instanceof WeakReference);
        equal(true, privateField(shaders.values().iterator().next(), "engine") instanceof WeakReference);
        // Scope cleanup after exceptions cannot enroll an unrelated material or skip its Spotlight.
        weak.configure(settings(true));
        try (C17HighlightRemoval.SurfaceScope surface = weak.beginSurface(weakView)) { throw new IllegalArgumentException("draw failure"); }
        catch (IllegalArgumentException expected) { checks++; }
        equal(false, weak.skipSpotlight(null));
        C17HighlightRemoval.SurfaceScope[] bounded = new C17HighlightRemoval.SurfaceScope[80];
        for (int i = 0; i < bounded.length; i++) bounded[i] = weak.beginSurface(weakView);
        for (int i = bounded.length - 1; i >= 0; i--) bounded[i].close();
        equal(false, weak.skipSpotlight(null));
        Object surfacePool = ((ThreadLocal<?>) privateField(weak, "surfacePool")).get();
        Object frame = privateField(surfacePool, "first"); int frames = 0;
        while (frame != null) { frames++; frame = privateField(frame, "next"); }
        equal(64, frames); equal(0, privateField(surfacePool, "depth"));
        BlendDrawable orphan = new BlendDrawable(); HookedShader orphanShader = new HookedShader(weak);
        try (C17HighlightRemoval.RecordScope record = weak.beginRecord(orphan)) { orphanShader.setFloatUniform("u_edgeArray", material.stroke.values); }
        same(material.stroke.values, orphanShader.lastArgument); arrays(material.stroke.values, orphanShader.floats.get("u_edgeArray"));
        weak.release();
        restorationAndReentry();
        checks += C17AcrylicMaterialCheck.run();
        checks += C17QsSurfaceCheck.run();
        checks += C17NotificationColorCheck.run();
        System.out.println("C17HighlightRemovalCheck: " + checks + " checks passed (10000 live frames, reusable arrays, OFF/safe/release ownership)");
    }

    private static void restorationAndReentry() throws Throwable {
        C17HighlightRemoval helper = new C17HighlightRemoval(); View view = new com.android.systemui.statusbar.notification.row.NotificationBackgroundView(); BlendDrawable engine = new BlendDrawable();
        NativeMaterial material = new NativeMaterial(); QsTileAppearance.setField(engine, "drawableShader", material);
        HookedShader shader = new HookedShader(helper); material.shader = shader;
        material.stroke.values[7] = .2f; material.stroke.values[8] = .4f; material.optics.values[3] = .6f;
        material.inner.values[3] = .2f; material.inner.values[4] = .35f; material.inner.values[5] = .5f;
        bind(helper, view, engine); helper.configure(settings(true));
        shader.fail = true; helper.configure(settings(false));
        near(0, shader.floats.get("u_edgeArray")[7]); near(0, shader.floats.get("u_opticsArray")[3]);
        shader.fail = false;
        float[] nativeFresh = material.stroke.values.clone(); nativeFresh[7] = .77f;
        shader.setFloatUniform("u_edgeArray", nativeFresh); helper.configure(settings(false));
        arrays(nativeFresh, shader.floats.get("u_edgeArray")); near(.6f, shader.floats.get("u_opticsArray")[3]);
        arrays(material.inner.values, shader.floats.get("u_shadowArray"));
        helper.configure(settings(true));
        float[] outer = material.stroke.values.clone(), inner = material.stroke.values.clone(); inner[7] = .65f;
        float[][] nestedBuffers = new float[2][];
        helper.writeUniform(shader, "u_edgeArray", outer, outerMasked -> {
            nestedBuffers[0] = outerMasked; shader.original("u_edgeArray", outerMasked);
            helper.writeUniform(shader, "u_edgeArray", inner, innerMasked -> {
                nestedBuffers[1] = innerMasked; shader.original("u_edgeArray", innerMasked); return null;
            });
            equal(false, outerMasked == nestedBuffers[1]); arrays(outer, outerMasked, 7, 8); return null;
        });
        helper.configure(settings(false)); near(.65f, shader.floats.get("u_edgeArray")[7]);
        helper.configure(settings(true));
        float[] newNative = material.stroke.values.clone(); newNative[7] = .38f;
        helper.writeUniform(shader, "u_edgeArray", newNative, masked -> {
            shader.original("u_edgeArray", masked); helper.configure(settings(false)); return null;
        });
        near(.38f, shader.floats.get("u_edgeArray")[7]); equal(false, helper.enabled());
        helper.configure(settings(true));
        material.stroke.values[7] = .58f; material.stroke.pushUniforms(shader);
        helper.detach(view); near(.58f, shader.floats.get("u_edgeArray")[7]); equal(false, helper.skipSpotlight(view));
        arrays(material.inner.values, shader.floats.get("u_shadowArray"));
        shader.setFloatUniform("u_edgeArray", nativeFresh); same(nativeFresh, shader.lastArgument);
        arrays(nativeFresh, shader.floats.get("u_edgeArray"));
        bind(helper, view, engine); arrays(material.stroke.values, shader.floats.get("u_edgeArray"), 7, 8);
        // Disabled native effect has no source uniform, and must not be forcibly submitted.
        helper.configure(settings(false)); material.optics.enabled = false;
        int uploads = material.optics.uploads; helper.configure(settings(true)); equal(uploads, material.optics.uploads);
        helper.release();
    }
}
