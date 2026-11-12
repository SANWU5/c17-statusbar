package dev.puitheme;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.RuntimeShader;
import android.os.Bundle;
import android.widget.FrameLayout;
import com.android.systemui.plugins.qs.QSTile;
import com.oplus.posteffect.drawable.BlendDrawable;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne;
import dev.c17.plugins.freeedit.QsTileGroupHolder;

/** A removed optical layer must not erase the circle separating a group member from its body. */
public final class C17GroupTileMaterialCheck {
    private static int checks;
    private static void yes(boolean value) { checks++; if (!value) throw new AssertionError("Group material regression at " + checks); }
    private static void near(float expected, float actual) { yes(Math.abs(expected - actual) < .00001f); }
    private static Bundle settings(boolean enabled, boolean tint) {
        Bundle result = new Bundle(); result.putBoolean(C17HighlightRemoval.ENABLED, enabled);
        result.putBoolean(C17HighlightRemoval.BACKGROUND_ENABLED, tint); return result;
    }
    private static void draw(C17HighlightRemoval helper, BlendDrawable engine) throws Throwable {
        try (C17HighlightRemoval.RecordScope record = helper.beginRecord(engine)) {
            helper.drawContent(engine, new Canvas(), canvas -> { engine.onDrawContent(canvas); return null; });
        }
    }
    private static float fill(BlendDrawable engine) {
        float[] value = engine.recorded.floats.get("c17GroupTileFill");
        yes(value != null && value.length == 4); return value[3];
    }
    public static void main(String[] args) throws Throwable {
        C17HighlightRemoval helper = new C17HighlightRemoval();
        OplusQSResizeableTileViewOneXOne tile = new OplusQSResizeableTileViewOneXOne();
        QSTile.State state = new QSTile.State(); tile.state = state;
        QsTileGroupHolder.Slot slot = new QsTileGroupHolder.Slot(false);
        FrameLayout deformation = new FrameLayout(new Context()); slot.addView(deformation); deformation.addView(tile);
        BlendDrawable engine = new BlendDrawable();
        C17AcrylicMaterialCheck.NativeMaterial material = new C17AcrylicMaterialCheck.NativeMaterial();
        QsTileAppearance.setField(engine, "drawableShader", material); RuntimeShader original = material.shader;
        try (C17HighlightRemoval.SurfaceScope scope = helper.beginSurface(tile)) { helper.bindDrawable(engine); }
        for (boolean tint : new boolean[]{false, true}) {
            helper.configure(settings(true, tint));
            tile.getResources().getConfiguration().uiMode = Configuration.UI_MODE_NIGHT_YES;
            state.state = 1; draw(helper, engine); near(.14f, fill(engine));
            RuntimeShader cached = engine.recorded;
            near(1, cached.floats.get("c17GroupTileFill")[0]);
            yes(!cached.source.contains("OpticsEffect") && !cached.source.contains("GradientStrokeEffect") && !cached.source.contains("InnerShadowEffect"));
            yes(cached.source.endsWith("return outputCol*shape;}"));
            yes(cached.source.contains("half3(c17GroupTileFill.rgb) * outputCol.a") && cached.source.contains("c17GroupTileFill.a), outputCol.a)"));
            yes(material.shader == original);
            state.state = 0; draw(helper, engine); near(.14f, fill(engine));
            state.state = 2; draw(helper, engine); near(0, fill(engine)); yes(cached == engine.recorded);
            state.state = 1; tile.getResources().getConfiguration().uiMode = Configuration.UI_MODE_NIGHT_NO;
            draw(helper, engine); near(.08f, fill(engine)); near(0, engine.recorded.floats.get("c17GroupTileFill")[0]);
            yes(cached == engine.recorded);
            deformation.removeView(tile); draw(helper, engine); near(0, fill(engine));
            QsTileGroupHolder.Slot copy = new QsTileGroupHolder.Slot(true); copy.addView(tile);
            draw(helper, engine); near(0, fill(engine)); copy.removeView(tile); deformation.addView(tile);
            tile.state = null; draw(helper, engine); near(0, fill(engine)); tile.state = state;
            helper.configure(settings(false, tint)); draw(helper, engine); yes(engine.recorded == original);
            Bundle scoped = settings(true, tint); scoped.putBoolean(C17HighlightRemoval.CONTROL_ENABLED, false);
            helper.configure(scoped); draw(helper, engine); yes(engine.recorded == original);
            Bundle safe = settings(true, tint); safe.putBoolean(StatusBarSettings.SAFE_MODE, true);
            helper.configure(safe); draw(helper, engine); yes(engine.recorded == original);
        }
        helper.configure(settings(true, false)); helper.detach(tile); draw(helper, engine); yes(engine.recorded == original);
        helper.release();
        nativeBlurOwnership();
        expandedSliders();
        System.out.println("C17GroupTileMaterialCheck: " + checks + " checks passed");
    }

    private static void expandedSliders() throws Throwable {
        C17HighlightRemoval helper=new C17HighlightRemoval();helper.configure(settings(true,false));
        dev.c17.plugins.freeedit.QsSliderCardLayout card=new dev.c17.plugins.freeedit.QsSliderCardLayout();
        OplusQSResizeableTileViewOneXOne button=new OplusQSResizeableTileViewOneXOne();QSTile.State state=new QSTile.State();button.state=state;state.state=1;
        card.buttons.top=button;card.addView(button);
        BlendDrawable engine=new BlendDrawable();QsTileAppearance.setField(engine,"drawableShader",new C17AcrylicMaterialCheck.NativeMaterial());
        button.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_YES;
        try(C17HighlightRemoval.SurfaceScope scope=helper.beginSurface(button)){helper.bindDrawable(engine);}
        draw(helper,engine);near(.14f,fill(engine));
        state.state=2;draw(helper,engine);near(0,fill(engine));state.state=1;
        card.columns=1;draw(helper,engine);near(0,fill(engine));card.columns=2;
        card.released=true;draw(helper,engine);near(0,fill(engine));card.released=false;
        card.buttons.top=null;draw(helper,engine);near(0,fill(engine));card.buttons.bottom=button;
        draw(helper,engine);near(.14f,fill(engine));
        com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar seek=new com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar();
        FrameLayout root=new FrameLayout(new Context());card.slider.view=root;card.addView(root);root.addView(seek);
        seek.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_YES;
        com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable base=new com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable(),active=new com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable();
        base.viewBlurProxy.view=seek;active.viewBlurProxy.view=seek;seek.baseMixColorDrawable=base;seek.activeMixColorDrawable=active;
        BlendDrawable background=base.viewBlurProxy.actual.blurDrawable,progress=active.viewBlurProxy.actual.blurDrawable;
        QsTileAppearance.setField(background,"drawableShader",new C17AcrylicMaterialCheck.NativeMaterial());QsTileAppearance.setField(progress,"drawableShader",new C17AcrylicMaterialCheck.NativeMaterial());
        helper.onNativeBlurResult(base.viewBlurProxy,base.viewBlurProxy.actual);helper.onNativeBlurResult(active.viewBlurProxy,active.viewBlurProxy.actual);
        draw(helper,background);near(.14f,fill(background));draw(helper,progress);near(0,fill(progress));
        card.slider.view=null;draw(helper,background);near(0,fill(background));card.slider.view=root;
        root.removeView(seek);draw(helper,background);near(0,fill(background));root.addView(seek);
        base.viewBlurProxy.view=button;draw(helper,background);near(0,fill(background));base.viewBlurProxy.view=seek;
        helper.configure(settings(false,false));draw(helper,background);yes(!background.recorded.floats.containsKey("c17GroupTileFill"));helper.release();
    }

    private static void nativeBlurOwnership() throws Throwable {
        C17HighlightRemoval helper = new C17HighlightRemoval(); helper.configure(settings(true, false));
        OplusQSResizeableTileViewOneXOne tile = new OplusQSResizeableTileViewOneXOne();
        tile.state = new QSTile.State();
        QsTileGroupHolder.Slot slot = new QsTileGroupHolder.Slot(false);
        FrameLayout deformation = new FrameLayout(new Context()); slot.addView(deformation); deformation.addView(tile);
        android.view.View body = new android.view.View(new Context()); body.assignParent(tile); tile.bgView = body;
        com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable blur = new com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable();
        blur.viewBlurProxy.view = body; body.setBackground(blur);
        BlendDrawable engine = blur.viewBlurProxy.actual.blurDrawable;
        C17AcrylicMaterialCheck.NativeMaterial material = new C17AcrylicMaterialCheck.NativeMaterial();
        QsTileAppearance.setField(engine, "drawableShader", material); RuntimeShader original = material.shader;
        tile.getResources().getConfiguration().uiMode = Configuration.UI_MODE_NIGHT_YES;
        helper.onNativeBlurResult(blur.viewBlurProxy, blur.viewBlurProxy.actual);
        draw(helper, engine); near(.14f, fill(engine));
        try (C17HighlightRemoval.SurfaceScope scope = helper.beginBlur(blur)) { helper.bindDrawable(engine); }
        draw(helper, engine); near(.14f, fill(engine)); yes(material.shader == original);
        // A native engine recycled for an ordinary tile must lose the group tint immediately.
        OplusQSResizeableTileViewOneXOne normal = new OplusQSResizeableTileViewOneXOne(); normal.state = new QSTile.State();
        body.assignParent(normal); normal.bgView = body;
        helper.onNativeBlurResult(blur.viewBlurProxy, blur.viewBlurProxy.actual);
        draw(helper, engine); near(0, fill(engine)); yes(material.shader == original);
        // A separate custom active fill keeps the already prepared material untouched.
        body.assignParent(tile); helper.onNativeBlurResult(blur.viewBlurProxy, blur.viewBlurProxy.actual);
        RuntimeShader custom = new RuntimeShader("custom active fill");
        try (C17HighlightRemoval.RecordScope record = helper.beginRecord(engine)) {
            material.shader = custom; C17HighlightRemoval.preparedCustomMaterial(engine, custom, true);
            helper.drawContent(engine, new Canvas(), canvas -> { engine.onDrawContent(canvas); return null; });
            yes(engine.recorded == custom); yes(material.shader == custom);
        } finally { material.shader = original; }
        helper.release(); draw(helper, engine); yes(engine.recorded == original);
    }
}
