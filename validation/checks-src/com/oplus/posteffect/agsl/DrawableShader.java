package com.oplus.posteffect.agsl;
import android.graphics.RuntimeShader;
import java.util.*;
public class DrawableShader {
    public static int allEffectsReads,baseUploads,commonUploads,baseAttempts;
    public static boolean failBaseUniform;
    public boolean isValid=true;
    public float width=240,height=120;
    public android.graphics.PointF localMatrixScalePointF=new android.graphics.PointF(1,1);
    public float[] commonArray={1,2,3,4,5,6,7,8};
    public StringBuilder shaderStringBuilder;
    public RuntimeShader shader=new RuntimeShader("native");
    public final ArrayList<ShaderBlendParam> multiBlendParam=new ArrayList<>(),summaryBlendParam=new ArrayList<>();
    public boolean enableBlend=true;
    public int blendAlgorithmMask=42;
    public final Corner mCornerParams=new Corner();
    public final Meta metaBallParams=new Meta();
    public final List<Effect> effects=Arrays.asList(new Effect("nativeGlassHighlight"),new Effect("nativeOptics"));
    public static final class Corner {public String getType(){return "ROUND";}}
    public static final class Meta {public boolean valid;}
    public static final class Effect {public final String name;Effect(String name){this.name=name;}public boolean isEnabled(){return true;}public void pushUniforms(RuntimeShader shader){shader.setFloatUniform(name,17f);}}
    public DrawableShader(){multiBlendParam.add(new ShaderBlendParam(5,0x98050505));multiBlendParam.add(new ShaderBlendParam(2,0x66999999));multiBlendParam.add(new ShaderBlendParam(5,0x73e6e6e6));multiBlendParam.add(new ShaderBlendParam(3,0x40cccccc));summaryBlendParam.add(new ShaderBlendParam(0,0));summaryBlendParam.addAll(multiBlendParam);}
    public List<Effect> getAllEffects(){allEffectsReads++;return effects;}
    public void setBaseUniform(RuntimeShader target){baseAttempts++;if(failBaseUniform)throw new IllegalStateException("native base uniform unavailable");baseUploads++;target.setFloatUniform("nativeShape",100f,300f);target.setFloatUniform("nativeMatrixScale",localMatrixScalePointF.x,localMatrixScalePointF.y);}
    public void setCommonArray(RuntimeShader target){commonUploads++;target.setFloatUniform("nativeCommon",commonArray);}
}
