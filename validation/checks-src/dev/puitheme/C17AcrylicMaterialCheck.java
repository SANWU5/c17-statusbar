package dev.puitheme;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.View;
import com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt;
import com.oplus.posteffect.agsl.DrawableShader;
import com.oplus.posteffect.agsl.effects.CustomClipEffect;
import com.oplus.posteffect.agsl.effects.GradientStrokeEffect;
import com.oplus.posteffect.agsl.effects.InnerShadowEffect;
import com.oplus.posteffect.agsl.effects.OpticsEffect;
import com.oplus.posteffect.drawable.BlendDrawable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Actual native primitive contracts, not a preview bitmap or replacement card. */
public final class C17AcrylicMaterialCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("expected "+expected+" got "+actual);}
    private static void same(Object expected,Object actual){checks++;if(expected!=actual)throw new AssertionError("native identity changed");}
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.00001f)throw new AssertionError("expected "+expected+" got "+actual);}
    public static final class NativeMaterial extends DrawableShader {
        final GradientStrokeEffect edge=new GradientStrokeEffect();
        final OpticsEffect optics=new OpticsEffect();
        final InnerShadowEffect inner=new InnerShadowEffect();
        final CustomClipEffect clip=new CustomClipEffect();
        final List<Object> nativeEffects=Arrays.asList(edge,optics,inner,clip);
        NativeMaterial(){inner.nativeArrays=true;}
        @SuppressWarnings({"rawtypes","unchecked"}) public List getAllEffects(){return nativeEffects;}
        public Object getEffect(String name){return "gradientStroke".equals(name)?edge:"optics".equals(name)?optics:"innerShadow".equals(name)?inner:null;}
    }
    private static Bundle settings(boolean enabled,boolean background){Bundle s=new Bundle();s.putBoolean(C17HighlightRemoval.ENABLED,enabled);s.putBoolean(C17HighlightRemoval.BACKGROUND_ENABLED,background);s.putInt(C17HighlightRemoval.LIGHT_BACKGROUND,0x80aabbcc);s.putInt(C17HighlightRemoval.DARK_BACKGROUND,0x40112233);return s;}
    public static int run() throws Throwable {
        String source="uniform shader uniBDFBitmap;half4 main(float2 position) {half4 outputCol=uniBDFBitmap.eval(position);return outputCol*shape;}";
        String tinted=C17AcrylicMaterial.decorate(source);
        equal(true,tinted.contains("uniBDFBitmap.eval(position)"));equal(true,tinted.endsWith("return outputCol*shape;}"));
        equal(true,tinted.contains("outputCol.a"));equal(null,C17AcrylicMaterial.decorate(tinted));
        equal(null,C17AcrylicMaterial.decorate("an unrelated shader"));
        equal(null,C17AcrylicMaterial.decorate(source.replace("return outputCol*shape;}" ,"return outputCol*shape;}return outputCol*shape;}")));
        C17HighlightRemoval helper=new C17HighlightRemoval();View view=new com.android.systemui.statusbar.notification.row.NotificationBackgroundView();BlendDrawable engine=new BlendDrawable();NativeMaterial material=new NativeMaterial();
        QsTileAppearance.setField(engine,"drawableShader",material);RuntimeShader original=material.shader;Shader oldPaint=new Shader();engine.drawableShaderPaint.setShader(oldPaint);
        try(C17HighlightRemoval.SurfaceScope scope=helper.beginSurface(view)){helper.bindDrawable(engine);}
        helper.configure(settings(true,false));helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);return 17;});same(original,engine.recorded);
        engine.drawableShaderPaint.setShader(oldPaint);
        helper.configure(settings(true,true));int builds=BlurDrawableShaderBaseStringKt.builds;
        view.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_NO;
        Object result=helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);return 19;});equal(19,result);
        RuntimeShader variant=engine.recorded;equal(false,variant==original);same(original,material.shader);same(oldPaint,engine.drawableShaderPaint.getShader());
        equal(builds+1,BlurDrawableShaderBaseStringKt.builds);
        equal(false,variant.source.contains("GradientStrokeEffect"));equal(false,variant.source.contains("OpticsEffect"));equal(false,variant.source.contains("InnerShadowEffect"));equal(true,variant.source.contains("CustomClipEffect"));
        equal(4,material.nativeEffects.size());near(0xaa/255f,variant.floats.get("c17AcrylicTint")[0]);near(0x80/255f,variant.floats.get("c17AcrylicTint")[3]);
        near(100,variant.floats.get("nativeShape")[0]);near(300,variant.floats.get("nativeShape")[1]);equal(1,variant.ints.get("uEnableBlend"));near(1,variant.floats.get("u_nativeCustomClip")[0]);
        for(int i=0;i<1000;i++) {
            material.summaryBlendParam.get(0).color=0x80010203;
            material.localMatrixScalePointF.x=1+i/1000f;
            helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);return null;});same(variant,engine.recorded);same(original,material.shader);same(oldPaint,engine.drawableShaderPaint.getShader());
            near(material.localMatrixScalePointF.x,variant.floats.get("nativeMatrixScale")[0]);near(1/255f,variant.floats.get("u_multiBlendParams")[1]);
        }
        equal(builds+1,BlurDrawableShaderBaseStringKt.builds);
        view.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_YES;
        helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);return null;});same(variant,engine.recorded);near(0x11/255f,variant.floats.get("c17AcrylicTint")[0]);near(0x40/255f,variant.floats.get("c17AcrylicTint")[3]);
        equal(builds+1,BlurDrawableShaderBaseStringKt.builds);
        try {helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);throw new IllegalArgumentException("native draw failed");});throw new AssertionError("missing failure");}catch(IllegalArgumentException expected){checks++;}
        same(original,material.shader);same(oldPaint,engine.drawableShaderPaint.getShader());
        RuntimeShader nativeReplacement=new RuntimeShader("native changed mid-draw");
        helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);material.shader=nativeReplacement;return null;});same(nativeReplacement,material.shader);
        material.shader=original;
        DrawableShader.failBaseUniform=true;helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);return null;});same(original,engine.recorded);same(original,material.shader);DrawableShader.failBaseUniform=false;
        material.clip.structuralKey=2;helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);return null;});equal(builds+2,BlurDrawableShaderBaseStringKt.builds);near(2,engine.recorded.floats.get("u_nativeCustomClip")[0]);
        Bundle transparent=settings(true,true);transparent.putInt(C17HighlightRemoval.DARK_BACKGROUND,0x00112233);helper.configure(transparent);
        helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);return null;});near(0,engine.recorded.floats.get("c17AcrylicTint")[3]);
        material.metaBallParams.valid=true;helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);return null;});same(original,engine.recorded);material.metaBallParams.valid=false;
        engine.enableShader=false;helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);return null;});same(original,engine.recorded);engine.enableShader=true;
        BlendDrawable unbound=new BlendDrawable();helper.drawContent(unbound,new Canvas(),c->{unbound.onDrawContent(c);return null;});same(unbound.drawableShader.shader,unbound.recorded);
        helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);helper.configure(settings(false,true));return null;});same(original,material.shader);
        helper.configure(settings(true,true));helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);helper.detach(view);return null;});same(original,material.shader);
        try(C17HighlightRemoval.SurfaceScope scope=helper.beginSurface(view)){helper.bindDrawable(engine);}
        helper.configure(settings(false,true));helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);return null;});same(original,engine.recorded);
        Bundle safe=settings(true,true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);helper.configure(safe);helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);return null;});same(original,engine.recorded);
        helper.configure(settings(true,true));helper.detach(view);helper.drawContent(engine,new Canvas(),c->{engine.onDrawContent(c);return null;});same(original,engine.recorded);
        helper.release();view.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_NO;
        return checks;
    }
    public static void main(String[] args) throws Throwable {System.out.println("C17AcrylicMaterialCheck: "+run()+" checks passed");}
}
