package com.oplus.posteffect.agsl;
import java.util.List;
public final class BlurDrawableShaderBaseStringKt {
    public static void buildShaderString(StringBuilder result,int mask,int count,String corner,boolean meta,List<?> effects){result.append("uniform float[").append(count*5).append("] u_multiBlendParams;half4 main(float2 position) {for (int i=0;i<25;i+=5){ outputCol = doBlend(outputCol,u_multiBlendParams[i],vec4(u_multiBlendParams[i + 1], u_multiBlendParams[i + 2], u_multiBlendParams[i + 3], u_multiBlendParams[i + 4]));} /* nativeGlassHighlight nativeOptics nativeInnerShadow */ return outputCol*shape;}");}
}
