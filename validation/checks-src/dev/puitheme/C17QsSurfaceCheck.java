package dev.puitheme;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt;
import com.oplus.posteffect.drawable.BlendDrawable;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView;
import com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar;
import com.oplusos.systemui.common.blurability.ViewBlurProxy;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;
import com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

/** Hardware QS owner/provider contracts extracted from the native ColorOS 17 DEX. */
public final class C17QsSurfaceCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {checks++;if(!Objects.equals(expected,actual))throw new AssertionError("expected "+expected+" got "+actual);}
    private static void same(Object expected,Object actual) {checks++;if(expected!=actual)throw new AssertionError("native identity changed");}
    private static void near(float expected,float actual) {checks++;if(Math.abs(expected-actual)>.00001f)throw new AssertionError("expected "+expected+" got "+actual);}
    private static Bundle settings(boolean enabled,boolean background) {
        Bundle result=new Bundle();result.putBoolean(C17HighlightRemoval.ENABLED,enabled);
        result.putBoolean(C17HighlightRemoval.BACKGROUND_ENABLED,background);
        result.putInt(C17HighlightRemoval.LIGHT_BACKGROUND,0x80aabbcc);
        result.putInt(C17HighlightRemoval.DARK_BACKGROUND,0x40112233);return result;
    }
    private static Object field(Object owner,String name) throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static void draw(C17HighlightRemoval helper,BlendDrawable engine) throws Throwable {
        try(C17HighlightRemoval.RecordScope record=helper.beginRecord(engine)) {
            helper.drawContent(engine,new Canvas(),canvas->{engine.onDrawContent(canvas);return null;});
        }
    }
    public static final class CountingProxy extends ViewBlurProxy {
        Drawable result;int nativeQueries;
        @Override public Drawable getBlurDrawable(Drawable fallback) {nativeQueries++;return result;}
    }
    public static int run() throws Throwable {
        C17HighlightRemoval helper=new C17HighlightRemoval();
        OplusQSResizeableTileView host=new OplusQSResizeableTileView();
        View nativeBackground=new View(null);nativeBackground.parent=host;
        AutoBlurDrawable auto=new AutoBlurDrawable();auto.viewBlurProxy.view=nativeBackground;
        BlendDrawable engine=auto.viewBlurProxy.actual.blurDrawable;
        C17AcrylicMaterialCheck.NativeMaterial material=new C17AcrylicMaterialCheck.NativeMaterial();
        QsTileAppearance.setField(engine,"drawableShader",material);
        RuntimeShader original=material.shader;Shader nativePaint=new Shader();engine.drawableShaderPaint.setShader(nativePaint);
        int queries=AutoBlurDrawable.Proxy.nativeRequests;
        // Hardware recording has no enclosing one-argument View.draw. Native proxy supplies ownership.
        try(C17HighlightRemoval.SurfaceScope scope=helper.beginBlur(auto)) {
            equal(false,helper.skipSpotlight(null));helper.bindDrawable(engine);
        }
        equal(queries,AutoBlurDrawable.Proxy.nativeRequests);
        helper.onNativeBlurResult(auto.viewBlurProxy,auto.viewBlurProxy.actual);
        equal(queries,AutoBlurDrawable.Proxy.nativeRequests);
        draw(helper,engine);same(original,engine.recorded);
        engine.drawableShaderPaint.setShader(nativePaint);
        helper.configure(settings(true,false));int builds=BlurDrawableShaderBaseStringKt.builds;
        draw(helper,engine);RuntimeShader variant=engine.recorded;
        equal(false,original==variant);same(original,material.shader);same(nativePaint,engine.drawableShaderPaint.getShader());
        equal(builds+1,BlurDrawableShaderBaseStringKt.builds);
        equal(false,variant.source.contains("GradientStrokeEffect"));equal(false,variant.source.contains("OpticsEffect"));
        equal(false,variant.source.contains("InnerShadowEffect"));equal(true,variant.source.contains("CustomClipEffect"));
        near(0,variant.floats.get("c17AcrylicTint")[3]);near(100,variant.floats.get("nativeShape")[0]);
        // Native SharedSpotLightEffect.draw(host,canvas) works independently of View.draw scope.
        equal(true,helper.skipSpotlight(host));equal(true,helper.skipSpotlight(nativeBackground));equal(false,helper.skipSpotlight(new View(null)));
        C17HighlightRemoval.SurfaceScope reusable=helper.beginBlur(auto);reusable.close();
        for(int i=0;i<1000;i++) {
            try(C17HighlightRemoval.SurfaceScope scope=helper.beginBlur(auto)) {same(reusable,scope);equal(true,helper.skipSpotlight(null));}
            helper.onNativeBlurResult(auto.viewBlurProxy,auto.viewBlurProxy.actual);draw(helper,engine);
            same(variant,engine.recorded);same(original,material.shader);same(nativePaint,engine.drawableShaderPaint.getShader());
        }
        equal(queries,AutoBlurDrawable.Proxy.nativeRequests);equal(builds+1,BlurDrawableShaderBaseStringKt.builds);
        equal(false,helper.skipSpotlight(null));
        // Enabled background changes tint only; the shader, native bitmap/shape and effects stay cached.
        helper.configure(settings(true,true));host.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_NO;
        draw(helper,engine);same(variant,engine.recorded);near(0xaa/255f,variant.floats.get("c17AcrylicTint")[0]);near(0x80/255f,variant.floats.get("c17AcrylicTint")[3]);
        host.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_YES;draw(helper,engine);
        same(variant,engine.recorded);near(0x11/255f,variant.floats.get("c17AcrylicTint")[0]);near(0x40/255f,variant.floats.get("c17AcrylicTint")[3]);
        equal(builds+1,BlurDrawableShaderBaseStringKt.builds);
        // Slider separately draws a path with getStrokePaint; only returned copy is made transparent.
        OplusQsVerticalSeekBar slider=new OplusQsVerticalSeekBar();Paint stroke=new Paint(1);stroke.setColor(0xddefaabb);stroke.setStrokeWidth(2);
        Paint masked=helper.sliderStroke(slider,stroke);equal(false,stroke==masked);equal(0,masked.getAlpha());equal(0xdd,stroke.getAlpha());
        for(int i=0;i<1000;i++) {same(masked,helper.sliderStroke(slider,stroke));equal(0,masked.getAlpha());equal(0xdd,stroke.getAlpha());}
        same(stroke,helper.sliderStroke(new View(null),stroke));same(null,helper.sliderStroke(slider,null));
        helper.detach(slider);equal(false,masked==helper.sliderStroke(slider,stroke));
        helper.configure(settings(false,true));same(stroke,helper.sliderStroke(slider,stroke));draw(helper,engine);same(original,engine.recorded);equal(false,helper.skipSpotlight(host));
        Bundle safe=settings(true,true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);helper.configure(safe);
        same(stroke,helper.sliderStroke(slider,stroke));draw(helper,engine);same(original,engine.recorded);
        helper.configure(settings(true,false));
        // A nested ancestor reparent cannot keep the old QS owner despite unchanged immediate parent.
        View middle=new View(null);middle.parent=host;nativeBackground.parent=middle;
        helper.onNativeBlurResult(auto.viewBlurProxy,auto.viewBlurProxy.actual);draw(helper,engine);equal(false,original==engine.recorded);
        middle.parent=new View(null);helper.onNativeBlurResult(auto.viewBlurProxy,auto.viewBlurProxy.actual);draw(helper,engine);same(original,engine.recorded);
        equal(0,((Map<?,?>)field(helper,"engines")).size());equal(queries,AutoBlurDrawable.Proxy.nativeRequests);
        // MaskBlurDrawable provider executes once. Observation/scope do not make another query.
        CountingProxy proxy=new CountingProxy();proxy.view=slider;proxy.result=engine;MaskBlurDrawable mask=new MaskBlurDrawable(proxy);
        Drawable provided=proxy.getBlurDrawable(null);helper.onNativeBlurResult(proxy,provided);equal(1,proxy.nativeQueries);
        try(C17HighlightRemoval.SurfaceScope scope=helper.beginBlur(mask)) {helper.bindDrawable(engine);draw(helper,engine);equal(false,original==engine.recorded);}
        equal(1,proxy.nativeQueries);
        helper.detach(slider);draw(helper,engine);same(original,engine.recorded);equal(0,((Map<?,?>)field(helper,"blurOwners")).size());
        slider.attached=false;helper.onNativeBlurResult(proxy,engine);draw(helper,engine);same(original,engine.recorded);
        // Invalid native contract remains native and does not enroll a similarly named app drawable.
        View arbitrary=new View(null);engine.setCallback(arbitrary);helper.bindDrawable(engine);draw(helper,engine);same(original,engine.recorded);
        OplusQSResizeableTileView callbackHost=new OplusQSResizeableTileView();engine.setCallback(callbackHost);
        helper.bindDrawable(engine);draw(helper,engine);equal(false,original==engine.recorded);
        helper.release();same(stroke,helper.sliderStroke(slider,stroke));draw(helper,engine);same(original,engine.recorded);
        equal(0,((Map<?,?>)field(helper,"sliderStrokes")).size());equal(0,((Map<?,?>)field(helper,"blurOwners")).size());
        host.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_NO;
        customLayers();
        return checks;
    }

    private static void customLayers() throws Throwable {
        C17HighlightRemoval helper=new C17HighlightRemoval();helper.configure(settings(true,true));
        OplusQSResizeableTileView host=new OplusQSResizeableTileView();BlendDrawable engine=new BlendDrawable();
        engine.setBounds(0,0,100,200);
        C17AcrylicMaterialCheck.NativeMaterial material=new C17AcrylicMaterialCheck.NativeMaterial();
        QsTileAppearance.setField(engine,"drawableShader",material);
        RuntimeShader original=material.shader;Shader originalPaint=new Shader();engine.drawableShaderPaint.setShader(originalPaint);
        try(C17HighlightRemoval.SurfaceScope surface=helper.beginSurface(host)){helper.bindDrawable(engine);}
        QsNativeGlassFill fill=new QsNativeGlassFill();
        QsTileAppearance.Style style=new QsTileAppearance.Style(0xffee3388,0xff3322dd,75,45,true);
        int builds=BlurDrawableShaderBaseStringKt.builds;
        RuntimeShader flat=null;
        for(int i=0;i<1000;i++) {
            equal(false,C17HighlightRemoval.removesOptics(engine));
            try(C17HighlightRemoval.RecordScope record=helper.beginRecord(engine)) {
                equal(true,C17HighlightRemoval.removesOptics(engine));
                QsNativeGlassFill.Swap swap=fill.prepare(engine,style);equal(true,swap!=null);
                RuntimeShader prepared=material.shader;
                try {helper.drawContent(engine,new Canvas(),canvas->{engine.onDrawContent(canvas);return null;});}
                finally {swap.restore();}
                same(prepared,engine.recorded);same(original,material.shader);same(originalPaint,engine.drawableShaderPaint.getShader());
                if(flat==null)flat=prepared;else same(flat,prepared);
            }
            equal(false,C17HighlightRemoval.removesOptics(engine));
        }
        equal(builds+1,BlurDrawableShaderBaseStringKt.builds);
        equal(true,flat.source.contains("c17QsFill"));equal(false,flat.source.contains("c17AcrylicTint"));
        equal(false,flat.source.contains("GradientStrokeEffect"));equal(false,flat.source.contains("OpticsEffect"));
        equal(false,flat.source.contains("InnerShadowEffect"));equal(true,flat.source.contains("CustomClipEffect"));
        equal(4,material.nativeEffects.size());equal(true,flat.inputs.containsKey("c17QsFill"));
        equal(191,((android.graphics.LinearGradient)flat.inputs.get("c17QsFill")).colors[0]>>>24);
        // Closing one surface never leaves its optical policy on an unrelated engine.
        BlendDrawable unrelated=new BlendDrawable();equal(false,C17HighlightRemoval.removesOptics(unrelated));
        try(C17HighlightRemoval.RecordScope record=helper.beginRecord(engine)) {
            equal(true,C17HighlightRemoval.removesOptics(engine));
            try(C17HighlightRemoval.SurfaceScope surface=helper.beginSurface(host)){helper.bindDrawable(unrelated);}
            try(C17HighlightRemoval.RecordScope inner=helper.beginRecord(unrelated)) {
                equal(false,C17HighlightRemoval.removesOptics(engine));equal(true,C17HighlightRemoval.removesOptics(unrelated));
            }
            equal(true,C17HighlightRemoval.removesOptics(engine));equal(false,C17HighlightRemoval.removesOptics(unrelated));
        }
        helper.configure(settings(false,true));
        try(C17HighlightRemoval.RecordScope record=helper.beginRecord(engine)) {
            QsNativeGlassFill.Swap swap=fill.prepare(engine,style);RuntimeShader restoredOptics=material.shader;
            try {helper.drawContent(engine,new Canvas(),canvas->{engine.onDrawContent(canvas);return null;});}finally {swap.restore();}
            same(restoredOptics,engine.recorded);equal(true,restoredOptics.source.contains("GradientStrokeEffect"));
            equal(true,restoredOptics.source.contains("c17QsFill"));same(original,material.shader);
        }

        QsMediaAppearanceCheck.Queue clock=new QsMediaAppearanceCheck.Queue();
        QsMediaAppearance media=new QsMediaAppearance(clock,clock);QsMediaAppearanceCheck.Card card=new QsMediaAppearanceCheck.Card();
        AutoBlurDrawable blur=QsMediaAppearanceCheck.material(card);BlendDrawable mediaEngine=blur.viewBlurProxy.actual.blurDrawable;
        C17AcrylicMaterialCheck.NativeMaterial mediaMaterial=new C17AcrylicMaterialCheck.NativeMaterial();
        QsTileAppearance.setField(mediaEngine,"drawableShader",mediaMaterial);blur.viewBlurProxy.view=card;
        RuntimeShader nativeMedia=mediaMaterial.shader;
        card.coverImg.setImageDrawable(QsMediaAppearanceCheck.image(card,0xff22cc44));
        media.onNativeUpdate(card,"onAttachedToWindow");media.configure(QsMediaAppearanceCheck.values(true));clock.run();
        helper.configure(settings(true,true));drawCustomMedia(helper,media,card,blur);
        RuntimeShader mediaFlat=mediaEngine.recorded;
        equal(true,mediaFlat.source.contains("c17MediaCover"));equal(false,mediaFlat.source.contains("c17AcrylicTint"));
        equal(false,mediaFlat.source.contains("GradientStrokeEffect"));equal(false,mediaFlat.source.contains("OpticsEffect"));
        equal(false,mediaFlat.source.contains("InnerShadowEffect"));equal(true,mediaFlat.source.contains("CustomClipEffect"));
        equal(0xff22cc44,QsMediaAppearanceCheck.pixel(mediaFlat));same(nativeMedia,mediaMaterial.shader);
        int cachedBuilds=BlurDrawableShaderBaseStringKt.builds,uploads=mediaFlat.uploadCount,reads=android.graphics.Bitmap.reads;
        for(int i=0;i<1000;i++){drawCustomMedia(helper,media,card,blur);same(mediaFlat,mediaEngine.recorded);}
        equal(cachedBuilds,BlurDrawableShaderBaseStringKt.builds);equal(uploads,mediaFlat.uploadCount);equal(reads,android.graphics.Bitmap.reads);
        helper.configure(settings(false,true));drawCustomMedia(helper,media,card,blur);
        RuntimeShader nativeOpticsMedia=mediaEngine.recorded;equal(true,nativeOpticsMedia.source.contains("GradientStrokeEffect"));
        equal(true,nativeOpticsMedia.source.contains("c17MediaCover"));equal(0xff22cc44,QsMediaAppearanceCheck.pixel(nativeOpticsMedia));
        helper.configure(settings(true,false));drawCustomMedia(helper,media,card,blur);
        equal(false,mediaEngine.recorded.source.contains("GradientStrokeEffect"));equal(true,mediaEngine.recorded.source.contains("c17MediaCover"));
        // Native draw exceptions unwind both the media shader and the record policy.
        try(C17HighlightRemoval.RecordScope record=helper.beginRecord(mediaEngine)) {
            try {media.drawGlassContent(mediaEngine,new Canvas(),canvas->helper.drawContent(mediaEngine,canvas,c->{mediaEngine.onDrawContent(c);throw new IllegalStateException("draw failed");}));
                throw new AssertionError("native draw failure missing");}
            catch(IllegalStateException expected){checks++;}
        }
        same(nativeMedia,mediaMaterial.shader);equal(false,C17HighlightRemoval.removesOptics(mediaEngine));
        media.configure(QsMediaAppearanceCheck.values(false));helper.configure(settings(true,true));
        drawCustomMedia(helper,media,card,blur);equal(true,mediaEngine.recorded.source.contains("c17AcrylicTint"));
        equal(false,mediaEngine.recorded.source.contains("c17MediaCover"));same(nativeMedia,mediaMaterial.shader);
        helper.configure(settings(false,true));
        drawCustomMedia(helper,media,card,blur);same(nativeMedia,mediaEngine.recorded);
        media.releaseRuntime();helper.release();
    }

    private static void drawCustomMedia(C17HighlightRemoval helper,QsMediaAppearance media,QsMediaAppearanceCheck.Card card,AutoBlurDrawable blur) throws Throwable {
        BlendDrawable engine=blur.viewBlurProxy.actual.blurDrawable;Canvas canvas=new Canvas();
        media.drawMedia(card,canvas,target->{
            try(C17HighlightRemoval.SurfaceScope surface=helper.beginBlur(blur)) {
                media.drawBlur(blur,target,nativeCanvas->{
                    Drawable result=blur.viewBlurProxy.getBlurDrawable(null);
                    helper.onNativeBlurResult(blur.viewBlurProxy,result);media.onNativeBlurResult(blur.viewBlurProxy,result);
                    try(C17HighlightRemoval.RecordScope record=helper.beginRecord(engine)) {
                        media.drawGlassContent(engine,nativeCanvas,customCanvas->helper.drawContent(engine,customCanvas,c->{engine.onDrawContent(c);return null;}));
                    }
                });
            }
        });
    }
    public static void main(String[] args) throws Throwable {System.out.println("C17QsSurfaceCheck: "+run()+" checks passed");}
}
