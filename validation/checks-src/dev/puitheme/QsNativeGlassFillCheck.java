package dev.puitheme;

import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt;
import com.oplus.posteffect.agsl.DrawableShader;
import com.oplus.posteffect.agsl.ShaderBlendParam;
import com.oplus.posteffect.agsl.effects.InnerShadowEffect;
import com.oplus.posteffect.drawable.BlendDrawable;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/** Counts source preparation separately from live OEM shape, bitmap and optical uploads. */
public final class QsNativeGlassFillCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("expected "+expected+" but got "+actual);}
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.00001f)throw new AssertionError("expected "+expected+" but got "+actual);}
    public static final class CornerShape {public String type="ROUND";public String getType(){return type;}}
    public static final class LiveShader extends DrawableShader {
        public final CornerShape mCornerParams=new CornerShape();
        public Shader bitmap=texture(1);
        @Override public void setBaseUniform(RuntimeShader target){super.setBaseUniform(target);target.setInputShader("uniBDFBitmap",bitmap);}
    }
    private static Shader texture(int color){return new LinearGradient(0,0,1,1,new int[]{color,color},new float[]{0,1},Shader.TileMode.CLAMP);}
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void effect(LiveShader nativeShader,int index,Object effect){((List)nativeShader.effects).set(index,effect);}
    private static RuntimeShader prepare(QsNativeGlassFill fill,BlendDrawable engine,QsTileAppearance.Style style) throws Exception {
        RuntimeShader original=engine.drawableShader.shader;Shader paint=engine.drawableShaderPaint.getShader();
        QsNativeGlassFill.Swap swap=fill.prepare(engine,style);equal(true,swap!=null);
        RuntimeShader replacement=engine.drawableShader.shader;equal(true,replacement!=original);
        engine.drawableShaderPaint.setShader(replacement);swap.restore();
        equal(original,engine.drawableShader.shader);equal(paint,engine.drawableShaderPaint.getShader());return replacement;
    }
    private static void rebuild(QsNativeGlassFill fill,BlendDrawable engine,QsTileAppearance.Style style) throws Exception {
        int before=BlurDrawableShaderBaseStringKt.builds;prepare(fill,engine,style);equal(before+1,BlurDrawableShaderBaseStringKt.builds);
        prepare(fill,engine,style);equal(before+1,BlurDrawableShaderBaseStringKt.builds);
    }
    public static void main(String[] args) throws Exception {
        BlendDrawable engine=new BlendDrawable();engine.setBounds(0,0,100,200);
        LiveShader nativeShader=new LiveShader();equal(true,QsTileAppearance.setField(engine,"drawableShader",nativeShader));
        Object unknown=nativeShader.effects.get(0);
        equal(true,QsTileAppearance.setField(nativeShader,"effects",new ArrayList<>(nativeShader.effects)));
        InnerShadowEffect first=new InnerShadowEffect(),second=new InnerShadowEffect();second.name="testOptics";second.order=4;
        effect(nativeShader,0,first);effect(nativeShader,1,second);
        QsTileAppearance.Style style=new QsTileAppearance.Style(0xff112233,0xff445566,50f,45f,true);
        QsNativeGlassFill fill=new QsNativeGlassFill();
        int builds=BlurDrawableShaderBaseStringKt.builds,base=DrawableShader.baseUploads;
        RuntimeShader replacement=prepare(fill,engine,style);equal(builds+1,BlurDrawableShaderBaseStringKt.builds);
        equal(true,replacement.source.contains("c17QsBase(vec4(u_multiBlendParams"));
        equal(true,replacement.source.contains("nativeGlassHighlight nativeOptics nativeInnerShadow"));
        near(15f,replacement.floats.get("c17QsSlots")[0]);near(20f,replacement.floats.get("c17QsSlots")[1]);
        for(int i=0;i<1000;i++) {
            nativeShader.localMatrixScalePointF.x=1f+i;first.opticalValue=i;second.opticalValue=i+.5f;
            nativeShader.bitmap=texture(i);nativeShader.enableBlend=(i&1)==0;
            // Native active alpha may animate without changing either material structure or RGB.
            nativeShader.multiBlendParam.get(2).color=((i&255)<<24)|0xe6e6e6;
            RuntimeShader current=prepare(fill,engine,style);equal(replacement,current);
            equal(nativeShader.bitmap,current.inputs.get("uniBDFBitmap"));near(1f+i,current.floats.get("nativeMatrixScale")[0]);
            near(i,current.floats.get(first.name)[0]);near(i+.5f,current.floats.get(second.name)[0]);
            equal((i&1)==0?1:0,current.ints.get("uEnableBlend"));
            near((i&255)/255f,current.floats.get("u_multiBlendParams")[19]);
        }
        equal(builds+1,BlurDrawableShaderBaseStringKt.builds);equal(base+1001,DrawableShader.baseUploads);equal(1001,first.uploads);equal(1001,second.uploads);
        nativeShader.blendAlgorithmMask++;rebuild(fill,engine,style);
        nativeShader.summaryBlendParam.add(new ShaderBlendParam(2,0x70aabbcc));rebuild(fill,engine,style);
        equal(30,prepare(fill,engine,style).floats.get("u_multiBlendParams").length);
        nativeShader.mCornerParams.type="G2";rebuild(fill,engine,style);
        first.order++;rebuild(fill,engine,style);
        first.name="changedShadow";rebuild(fill,engine,style);
        first.structuralKey=42;rebuild(fill,engine,style);
        first.structuralKey=null;rebuild(fill,engine,style);
        first.enabled=false;int optical=first.uploads;rebuild(fill,engine,style);equal(optical,first.uploads);
        first.enabled=true;rebuild(fill,engine,style);
        InnerShadowEffect fresh=new InnerShadowEffect();fresh.name=first.name;fresh.order=first.order;fresh.structuralKey=first.structuralKey;
        effect(nativeShader,0,fresh);rebuild(fill,engine,style);first=fresh;
        nativeShader.shader=new RuntimeShader("new OEM material");rebuild(fill,engine,style);
        replacement=prepare(fill,engine,style);
        int stable=BlurDrawableShaderBaseStringKt.builds;
        engine.setBounds(10,20,210,320);prepare(fill,engine,style);equal(stable,BlurDrawableShaderBaseStringKt.builds);
        LinearGradient resized=(LinearGradient)replacement.inputs.get("c17QsFill");
        near(100f,(resized.left+resized.right)/2f);near(150f,(resized.top+resized.bottom)/2f);
        QsTileAppearance.Style night=new QsTileAppearance.Style(0xff654321,0xff123456,100f,90f,true);
        RuntimeShader themed=prepare(fill,engine,night);equal(stable,BlurDrawableShaderBaseStringKt.builds);
        equal(0xff654321,((LinearGradient)themed.inputs.get("c17QsFill")).colors[0]);
        first.structuralKey=new StringBuilder("mutable");int rejected=BlurDrawableShaderBaseStringKt.builds;
        prepare(fill,engine,style);((StringBuilder)first.structuralKey).append(" changed");prepare(fill,engine,style);
        equal(rejected+2,BlurDrawableShaderBaseStringKt.builds);
        first.structuralKey=Boolean.TRUE;first.failKey=true;rejected=BlurDrawableShaderBaseStringKt.builds;
        prepare(fill,engine,style);prepare(fill,engine,style);equal(rejected+2,BlurDrawableShaderBaseStringKt.builds);first.failKey=false;
        effect(nativeShader,0,unknown);rejected=BlurDrawableShaderBaseStringKt.builds;
        prepare(fill,engine,style);prepare(fill,engine,style);equal(rejected+2,BlurDrawableShaderBaseStringKt.builds);
        effect(nativeShader,0,first);rebuild(fill,engine,style);
        // The surrounding module gates prepare. Native admission gates must remain early and inert.
        rejected=BlurDrawableShaderBaseStringKt.builds;base=DrawableShader.baseUploads;
        RuntimeShader original=nativeShader.shader;
        engine.enableShader=false;equal(null,fill.prepare(engine,style));engine.enableShader=true;
        engine.setBounds(0,0,0,0);equal(null,fill.prepare(engine,style));engine.setBounds(0,0,100,200);
        nativeShader.metaBallParams.valid=true;equal(null,fill.prepare(engine,style));nativeShader.metaBallParams.valid=false;
        nativeShader.multiBlendParam.get(2).color=0xff123456;equal(null,fill.prepare(engine,style));nativeShader.multiBlendParam.get(2).color=0x73e6e6e6;
        equal(rejected,BlurDrawableShaderBaseStringKt.builds);equal(base,DrawableShader.baseUploads);equal(original,nativeShader.shader);
        Paint paint=engine.drawableShaderPaint;Shader prior=texture(7);paint.setShader(prior);
        QsNativeGlassFill.Swap swap=fill.prepare(engine,style);RuntimeShader nativeReplacement=new RuntimeShader("native concurrently replaced shader");
        Shader nativePaintReplacement=texture(8);nativeShader.shader=nativeReplacement;paint.setShader(nativePaintReplacement);swap.restore();equal(nativeReplacement,nativeShader.shader);equal(nativePaintReplacement,paint.getShader());
        System.out.println("QsNativeGlassFillCheck passed: "+checks);
    }
}
