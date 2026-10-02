package com.oplus.posteffect.agsl;
import java.util.List;
public final class BlurDrawableShaderBaseStringKt {
    public static int builds;
    public static void buildShaderString(StringBuilder result,int mask,int count,String corner,boolean meta,List<?> effects){builds++;result.append("uniform shader uniBDFBitmap; uniform float[").append(count*5).append("] u_multiBlendParams;half4 main(float2 position) {half4 outputCol = half4(uniBDFBitmap.eval(mapCoords).rgb, 1.0);for (int i=0;i<25;i+=5){ outputCol = doBlend(outputCol,u_multiBlendParams[i],vec4(u_multiBlendParams[i + 1], u_multiBlendParams[i + 2], u_multiBlendParams[i + 3], u_multiBlendParams[i + 4]));} /* nativeGlassHighlight nativeOptics nativeInnerShadow */ return outputCol*shape;}");}
}
