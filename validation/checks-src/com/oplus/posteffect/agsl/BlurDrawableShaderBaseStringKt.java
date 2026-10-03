package com.oplus.posteffect.agsl;
import java.util.List;
public final class BlurDrawableShaderBaseStringKt {
    public static int builds;
    public static void buildShaderString(StringBuilder result,int mask,int count,String corner,boolean meta,List<?> effects){
        builds++;
        result.append("uniform shader uniBDFBitmap; uniform float[").append(count*5).append("] u_multiBlendParams;half4 main(float2 position) {half4 outputCol = half4(uniBDFBitmap.eval(mapCoords).rgb, 1.0);for (int i=0;i<25;i+=5){ outputCol = doBlend(outputCol,u_multiBlendParams[i],vec4(u_multiBlendParams[i + 1], u_multiBlendParams[i + 2], u_multiBlendParams[i + 3], u_multiBlendParams[i + 4]));}");
        boolean actual=effects.isEmpty()||effects.get(0).getClass().getName().startsWith("com.oplus.posteffect.agsl.effects.");
        if(!effects.isEmpty()&&effects.get(0) instanceof com.oplus.posteffect.agsl.effects.InnerShadowEffect&&!((com.oplus.posteffect.agsl.effects.InnerShadowEffect)effects.get(0)).nativeArrays)actual=false;
        if(actual)for(Object effect:effects)result.append(" /* ").append(effect.getClass().getSimpleName()).append(" */ ");
        else result.append(" /* nativeGlassHighlight nativeOptics nativeInnerShadow */ ");
        result.append("return outputCol*shape;}");
    }
}
