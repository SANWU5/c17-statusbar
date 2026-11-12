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
        equal(0,replacement.ints.get("c17QsKeepNativeRgb"));
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
        deviceOpacity();
        liquidGlassTransfer();
        System.out.println("QsNativeGlassFillCheck passed: "+checks);
    }
    private static void liquidGlassTransfer()throws Exception {
        BlendDrawable engine=new BlendDrawable();engine.setBounds(0,0,100,200);QsNativeGlassFill fill=new QsNativeGlassFill();
        RuntimeShader original=engine.drawableShader.shader,target=new RuntimeShader("composed liquid glass");
        equal(false,QsNativeGlassFill.hasLiquidGlassState(engine.drawableShader,original));
        QsNativeGlassFill.Swap scope=fill.prepare(engine,new QsTileAppearance.Style(0xff1677ff,0xff224466,75,90,true));
        RuntimeShader active=engine.drawableShader.shader;
        try {
            equal(true,QsNativeGlassFill.hasLiquidGlassState(engine.drawableShader,active));
            equal(false,QsNativeGlassFill.hasLiquidGlassState(new Object(),active));
            equal(false,QsNativeGlassFill.transferLiquidGlassState(engine.drawableShader,original,target));
            equal(false,QsNativeGlassFill.transferLiquidGlassState(engine.drawableShader,active,active));
            equal(true,QsNativeGlassFill.transferLiquidGlassState(engine.drawableShader,active,target));
            equal(active.inputs.get("c17QsFill"),target.inputs.get("c17QsFill"));
            near(active.floats.get("c17QsSlots")[0],target.floats.get("c17StateSlots")[0]);near(active.floats.get("c17QsSlots")[1],target.floats.get("c17StateSlots")[1]);
            equal(active.ints.get("c17QsKeepNativeRgb"),target.ints.get("c17QsKeepNativeRgb"));
            final boolean[] other={true};Thread thread=new Thread(()->other[0]=QsNativeGlassFill.hasLiquidGlassState(engine.drawableShader,active));thread.start();thread.join();equal(false,other[0]);
            BlendDrawable nested=new BlendDrawable();nested.setBounds(0,0,50,50);
            QsNativeGlassFill.Swap inner=fill.prepare(nested,new QsTileAppearance.Style(0xff55aa33,0xff55aa33,100,0,false));
            equal(false,QsNativeGlassFill.hasLiquidGlassState(engine.drawableShader,active));inner.restore();
            equal(true,QsNativeGlassFill.hasLiquidGlassState(engine.drawableShader,active));
            engine.drawableShader.shader=original;equal(false,QsNativeGlassFill.transferLiquidGlassState(engine.drawableShader,active,target));engine.drawableShader.shader=active;
        } finally {scope.restore();}
        equal(false,QsNativeGlassFill.hasLiquidGlassState(engine.drawableShader,active));equal(original,engine.drawableShader.shader);
    }
    private static void deviceOpacity() throws Exception {
        BlendDrawable engine=new BlendDrawable();engine.setBounds(0,0,100,200);
        QsNativeGlassFill fill=new QsNativeGlassFill();RuntimeShader original=engine.drawableShader.shader;
        for(int[] pair:new int[][]{{0x19404040,0x4d737373},{0x80404040,0xb2737373},{0x40404040,0x667b7b7b},{0x5a404040,0xb27b7b7b}}) {
            engine.drawableShader.multiBlendParam.get(2).color=pair[0];engine.drawableShader.multiBlendParam.get(3).color=pair[1];
            for(float opacity:new float[]{0,25,50,100}) {
                QsTileAppearance.Style style=new QsTileAppearance.Style(0x80123456,0x40445566,opacity,90,true);
                QsNativeGlassFill.Swap swap=fill.prepare(engine,style,true);equal(true,swap!=null);
                RuntimeShader shader=engine.drawableShader.shader;equal(1,shader.ints.get("c17QsKeepNativeRgb"));
                equal(true,shader.source.contains("vec4(value.rgb,value.a*fill.a)"));
                LinearGradient alpha=(LinearGradient)shader.inputs.get("c17QsFill");
                equal(QsTileAppearance.withOpacity(style.color,opacity,255),alpha.colors[0]);equal(QsTileAppearance.withOpacity(style.color2,opacity,255),alpha.colors[1]);
                near(0,shader.floats.get("c17QsDeviceTint")[3]);
                equal(pair[0],engine.drawableShader.multiBlendParam.get(2).color);equal(pair[1],engine.drawableShader.multiBlendParam.get(3).color);
                swap.restore();equal(original,engine.drawableShader.shader);
            }
        }
        engine.drawableShader.multiBlendParam.get(3).mode=2;equal(null,fill.prepare(engine,new QsTileAppearance.Style(-1,-1,50,0,false),true));
        engine.drawableShader.multiBlendParam.get(3).mode=3;
        engine.drawableShader.multiBlendParam.get(2).color=0x73e6e6e6;engine.drawableShader.multiBlendParam.get(3).color=0x40cccccc;
        QsNativeGlassFill.Swap connected=fill.prepare(engine,new QsTileAppearance.Style(0xff123456,0xff123456,100,0,false),true);
        equal(true,connected!=null);equal(0,engine.drawableShader.shader.ints.get("c17QsKeepNativeRgb"));
        LinearGradient active=(LinearGradient)engine.drawableShader.shader.inputs.get("c17QsFill");equal(0xff123456,active.colors[0]);
        near(0,engine.drawableShader.shader.floats.get("c17QsDeviceTint")[3]);connected.restore();equal(original,engine.drawableShader.shader);
    }
}
