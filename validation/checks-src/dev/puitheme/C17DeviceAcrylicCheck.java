package dev.puitheme;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import com.oplus.deviceplugin.sdk.ui.view.separatecardview.*;
import com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt;
import com.oplus.posteffect.drawable.BlendDrawable;
import com.oplusos.systemui.common.blurability.ViewBlurProxy;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne;
import java.util.Objects;

/** Five audited SDK body contracts, real native shader uploads and scoped paint restoration. */
public final class C17DeviceAcrylicCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) { checks++; if (!Objects.equals(expected, actual)) throw new AssertionError("expected " + expected + " got " + actual); }
    private static void same(Object expected, Object actual) { checks++; if (expected != actual) throw new AssertionError("native identity changed"); }
    private static void near(float expected, float actual) { checks++; if (Math.abs(expected - actual) > .00001f) throw new AssertionError("expected " + expected + " got " + actual); }
    private static Bundle settings(boolean enabled, boolean background) {
        Bundle result = new Bundle(); result.putBoolean(C17HighlightRemoval.ENABLED, enabled); result.putBoolean(C17HighlightRemoval.BACKGROUND_ENABLED, background);
        result.putInt(C17HighlightRemoval.LIGHT_BACKGROUND, 0x80aabbcc); result.putInt(C17HighlightRemoval.DARK_BACKGROUND, 0x40112233); return result;
    }
    private static void draw(C17HighlightRemoval helper, BlendDrawable engine) throws Throwable {
        try (C17HighlightRemoval.RecordScope record = helper.beginRecord(engine)) {
            helper.drawContent(engine, new Canvas(), canvas -> { engine.onDrawContent(canvas); return null; });
        }
    }
    private static void materialSame(RuntimeShader tile, RuntimeShader device) {
        equal(tile.source, device.source);
        for (String key : new String[]{"c17AcrylicTint", "u_multiBlendParams", "nativeShape", "nativeCommon"}) {
            float[] left=tile.floats.get(key),right=device.floats.get(key);
            equal(left.length,right.length);for(int i=0;i<left.length;i++)near(left[i],right[i]);
        }
        equal(tile.ints.get("uEnableBlend"),device.ints.get("uEnableBlend"));
    }
    public static int run() throws Throwable {
        String nativeSource = "half4 main(float2 position) {half4 outputCol = half4(0);outputCol = mix(outputCol, half4(inputColor.rgb, 1.0), inputColor.a);outputCol *= clipCoverage;return outputCol*shape;}";
        String scoped = C17AcrylicMaterial.decorate(nativeSource);
        equal(true, scoped.contains("mix(outputCol.rgb"));
        equal(true, scoped.endsWith("return outputCol*shape;}"));
        equal(true, scoped.contains("c17AcrylicTint.a), outputCol.a)"));
        equal(true, scoped.contains("outputCol *= clipCoverage;"));
        equal(null, C17AcrylicMaterial.decorate(scoped));
        equal(null, C17AcrylicMaterial.decorate("unknown OEM base"));
        for (DeviceCardFixture card : new DeviceCardFixture[]{new RectangleDeviceCardView(), new SquareDeviceCardView(), new NoDeviceEntranceCardView(), new RectangleEntranceCardView(), new SquareEntranceCardView()}) {
            C17HighlightRemoval helper = new C17HighlightRemoval(); AutoBlurDrawable auto = new AutoBlurDrawable();
            card.body.setBackground(auto); auto.viewBlurProxy.view = card.body; helper.refreshDeviceCard(card);
            BlendDrawable engine = auto.viewBlurProxy.actual.blurDrawable;
            C17AcrylicMaterialCheck.NativeMaterial material = new C17AcrylicMaterialCheck.NativeMaterial(); QsTileAppearance.setField(engine, "drawableShader", material);
            material.multiBlendParam.get(2).color=0x19404040;material.multiBlendParam.get(3).color=0x4d737373;
            OplusQSResizeableTileViewOneXOne tile=new OplusQSResizeableTileViewOneXOne();
            tile.state=new QsTileAppearanceCheck.State(1,"rotation");
            BlendDrawable tileEngine=new BlendDrawable();C17AcrylicMaterialCheck.NativeMaterial tileMaterial=new C17AcrylicMaterialCheck.NativeMaterial();
            tileMaterial.multiBlendParam.get(2).color=0x19404040;tileMaterial.multiBlendParam.get(3).color=0x4d737373;
            QsTileAppearance.setField(tileEngine,"drawableShader",tileMaterial);
            try(C17HighlightRemoval.SurfaceScope surface=helper.beginSurface(tile)){helper.bindDrawable(tileEngine);}
            RuntimeShader original = material.shader; Shader originalPaint = new Shader(); engine.drawableShaderPaint.setShader(originalPaint);
            helper.onNativeBlurResult(auto.viewBlurProxy, auto.viewBlurProxy.actual);
            helper.configure(settings(true, false)); draw(helper, engine); RuntimeShader base = engine.recorded;
            equal(false, base == original); equal(false, base.source.contains("half3(c17AcrylicTint.rgb) * c17AcrylicTint.a"));
            near(0, base.floats.get("c17AcrylicTint")[3]); draw(helper,tileEngine);materialSame(tileEngine.recorded,base);
            equal(true, base.source.contains("CustomClipEffect")); equal(false, base.source.contains("GradientStrokeEffect"));
            equal(4, material.nativeEffects.size()); near(100, base.floats.get("nativeShape")[0]); near(300, base.floats.get("nativeShape")[1]);
            same(original, material.shader); same(originalPaint, engine.drawableShaderPaint.getShader());
            int builds = BlurDrawableShaderBaseStringKt.builds; engine.contentDirty = false;
            for (int i = 0; i < 1000; i++) {
                helper.onNativeBlurResult(auto.viewBlurProxy, auto.viewBlurProxy.actual); draw(helper, engine);
                same(base, engine.recorded); same(original, material.shader); equal(false, engine.contentDirty);
            }
            equal(builds, BlurDrawableShaderBaseStringKt.builds);
            helper.configure(settings(true, true)); draw(helper, engine); same(base, engine.recorded);
            near(0xaa / 255f, base.floats.get("c17AcrylicTint")[0]); near(0x80 / 255f, base.floats.get("c17AcrylicTint")[3]);
            draw(helper,tileEngine);materialSame(tileEngine.recorded,base);
            card.resources.getConfiguration().uiMode = Configuration.UI_MODE_NIGHT_YES;
            tile.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_YES;
            draw(helper, engine); near(0x11 / 255f, base.floats.get("c17AcrylicTint")[0]); near(0x40 / 255f, base.floats.get("c17AcrylicTint")[3]);
            draw(helper,tileEngine);materialSame(tileEngine.recorded,base);
            Bundle transparent = settings(true, true); transparent.putInt(C17HighlightRemoval.DARK_BACKGROUND, 0x00112233);
            helper.configure(transparent); draw(helper, engine); near(0, base.floats.get("c17AcrylicTint")[3]);
            draw(helper,tileEngine);materialSame(tileEngine.recorded,base);
            // Same card's icon/foreground provider does not acquire the body base policy.
            helper.configure(settings(true,true));
            View icon = new View(null); icon.parent = card.body; ViewBlurProxy iconProxy = new ViewBlurProxy(); iconProxy.view = icon;
            BlendDrawable iconEngine = new BlendDrawable(); QsTileAppearance.setField(iconEngine, "drawableShader", new C17AcrylicMaterialCheck.NativeMaterial());
            helper.onNativeBlurResult(iconProxy, iconEngine); draw(helper, iconEngine);
            equal(false, iconEngine.recorded.source.contains("half3(c17AcrylicTint.rgb) * c17AcrylicTint.a"));
            near(0,iconEngine.recorded.floats.get("c17AcrylicTint")[3]);
            // A foreign package with an identical ID name is not the audited SDK body.
            card.resources.bodyPackage = "foreign.theme"; helper.refreshDeviceCard(card); helper.onNativeBlurResult(auto.viewBlurProxy, auto.viewBlurProxy.actual); draw(helper, engine);
            equal(false, engine.recorded.source.contains("half3(c17AcrylicTint.rgb) * c17AcrylicTint.a"));
            card.resources.bodyPackage = "com.android.systemui"; helper.refreshDeviceCard(card); helper.onNativeBlurResult(auto.viewBlurProxy, auto.viewBlurProxy.actual);
            helper.configure(settings(false, true)); draw(helper, engine); same(original, engine.recorded);
            Bundle safe = settings(true, true); safe.putBoolean(StatusBarSettings.SAFE_MODE, true); helper.configure(safe); draw(helper, engine); same(original, engine.recorded);
            helper.configure(settings(true, true));
            engine.drawableShaderPaint.setShader(originalPaint);
            try (C17HighlightRemoval.RecordScope record = helper.beginRecord(engine)) {
                try { helper.drawContent(engine, new Canvas(), canvas -> { engine.onDrawContent(canvas); throw new IllegalStateException("native failure"); }); throw new AssertionError("missing failure"); }
                catch (IllegalStateException expected) { checks++; }
            }
            same(original, material.shader); same(originalPaint, engine.drawableShaderPaint.getShader());
            // QsTileAppearance's independently prepared custom fill remains authoritative.
            try (C17HighlightRemoval.RecordScope record = helper.beginRecord(engine)) {
                QsNativeGlassFill fill = new QsNativeGlassFill(); engine.setBounds(0, 0, 100, 200);
                QsNativeGlassFill.Swap custom = fill.prepare(engine, new QsTileAppearance.Style(0xff22aa55, 0xff227755, 90, 0, false), true);
                equal(true, custom != null); RuntimeShader customShader = material.shader;
                try { helper.drawContent(engine, new Canvas(), canvas -> { engine.onDrawContent(canvas); return null; }); }
                finally { custom.restore(); }
                same(customShader, engine.recorded); equal(true, engine.recorded.source.contains("c17QsFill")); equal(false, engine.recorded.source.contains("c17AcrylicTint"));
            }
            card.attached = false; helper.detach(card); draw(helper, engine); same(original, engine.recorded); helper.release();
            tile.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_NO;
        }
        gradientBases();
        return checks;
    }
    private static void gradientBases() throws Throwable {
        C17HighlightRemoval helper = new C17HighlightRemoval(); SquareDeviceCardView card = new SquareDeviceCardView();
        GradientDrawable base = new GradientDrawable(); base.setColor(0x10223344); base.setCornerRadius(19); base.mStrokePaint.setColor(0xbbaabbcc);
        Shader shader = new Shader(); base.mFillPaint.setShader(shader); card.body.setBackground(base); helper.refreshDeviceCard(card); helper.configure(settings(true, true));
        for (int i = 0; i < 1000; i++) {
            helper.drawDeviceBackground(base, new Canvas(), canvas -> {
                equal(C17DeviceAcrylic.mixNativeTint(0x10223344,0x80aabbcc), base.mFillPaint.getColor()); equal(0x10,base.mFillPaint.getAlpha());
                equal(0, base.mStrokePaint.getAlpha()); same(null, base.mFillPaint.getShader()); base.draw(canvas); return null;
            });
            equal(0x10223344, base.mFillPaint.getColor()); same(shader, base.mFillPaint.getShader()); equal(0xbb, base.mStrokePaint.getAlpha()); near(19, base.getCornerRadius());
        }
        for (GradientDrawable foreground : new GradientDrawable[]{card.outer, card.foreground, card.icon}) {
            foreground.setCallback(card.body); helper.drawDeviceBackground(foreground, new Canvas(), canvas -> { equal(-1, foreground.mFillPaint.getColor()); foreground.draw(canvas); return null; });
        }
        try { helper.drawDeviceBackground(base, new Canvas(), canvas -> { throw new IllegalArgumentException("native failure"); }); throw new AssertionError("missing failure"); }
        catch (IllegalArgumentException expected) { checks++; }
        equal(0x10223344, base.mFillPaint.getColor()); same(shader, base.mFillPaint.getShader()); equal(0xbb, base.mStrokePaint.getAlpha());
        Bundle qs = settings(true, true); qs.putBoolean(QsTileAppearance.MASTER, true); helper.configure(qs);
        helper.drawDeviceBackground(base, new Canvas(), canvas -> { equal(C17DeviceAcrylic.mixNativeTint(0x10223344,0x80aabbcc), base.mFillPaint.getColor()); return null; });
        Bundle separated = settings(true, true); separated.putBoolean(C17HighlightRemoval.CONTROL_ENABLED, false); helper.configure(separated);
        helper.drawDeviceBackground(base, new Canvas(), canvas -> { equal(0x10223344, base.mFillPaint.getColor()); return null; });
        helper.configure(settings(false, true)); helper.drawDeviceBackground(base, new Canvas(), canvas -> { equal(0x10223344, base.mFillPaint.getColor()); return null; });
        Bundle safe = settings(true, true); safe.putBoolean(StatusBarSettings.SAFE_MODE, true); helper.configure(safe);
        helper.drawDeviceBackground(base, new Canvas(), canvas -> { equal(0x10223344, base.mFillPaint.getColor()); return null; });
        helper.release();
        // Color-mask opacity is an RGB blend amount, not a replacement native alpha.
        for(int alpha:new int[]{0,1,12,128,254,255})for(int tint:new int[]{0,0x0dffffff,0x1fffffff,0x80aabbcc,0xff112233})
            equal(alpha,C17DeviceAcrylic.mixNativeTint((alpha<<24)|0x223344,tint)>>>24);
    }
    public static void main(String[] args) throws Throwable { System.out.println("C17DeviceAcrylicCheck: " + run() + " checks passed"); }
}
