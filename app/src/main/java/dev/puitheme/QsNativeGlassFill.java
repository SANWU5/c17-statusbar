// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native AGSL material with only confirmed QS base blend colors substituted. */
final class QsNativeGlassFill {
    private static final Pattern BASE_ARGUMENT=Pattern.compile("vec4\\(u_multiBlendParams\\[i \\+ 1\\],\\s*u_multiBlendParams\\[i \\+ 2\\],\\s*u_multiBlendParams\\[i \\+ 3\\],\\s*u_multiBlendParams\\[i \\+ 4\\]\\)");
    private static final String MAIN="half4 main(float2 position) {";
    private static final String FILL_CODE="uniform shader c17QsFill;\nuniform float2 c17QsSlots;\n"
            +"vec4 c17QsBase(vec4 value, int index, float2 position) {\n"
            +" if(float(index)!=c17QsSlots.x && float(index)!=c17QsSlots.y) return value;\n"
            +" half4 fill=c17QsFill.eval(position);\n"
            +" return vec4(value.rgb*(fill.rgb/max(float(fill.a),0.000001)),value.a*fill.a);\n}\n";
    private final Map<Object,Cached> cache=Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<String> reason=new ThreadLocal<>();
    String lastReason(){String value=reason.get();return value==null?"unreported":value;}
    private Swap reject(String why){reason.set(why);return null;}
    private static final class Cached {final String source;final RuntimeShader shader;Cached(String source,RuntimeShader shader){this.source=source;this.shader=shader;}}
    static final class Swap {
        private final Object owner;private final RuntimeShader original,replacement;private final Paint paint;private final Shader originalPaint;
        Swap(Object owner,RuntimeShader original,RuntimeShader replacement,Paint paint){this.owner=owner;this.original=original;this.replacement=replacement;this.paint=paint;originalPaint=paint==null?null:paint.getShader();}
        void restore(){
            // Do not touch RenderNode properties or a shader that native code replaced during recording.
            if(QsTileAppearance.field(owner,"shader")==replacement)QsTileAppearance.setField(owner,"shader",original);
            if(paint!=null)paint.setShader(originalPaint);
        }
    }
    Swap prepare(Drawable engine,QsTileAppearance.Style style) throws ReflectiveOperationException {
        return prepare(engine,style,false);
    }
    Swap prepare(Drawable engine,QsTileAppearance.Style style,boolean deviceCard) throws ReflectiveOperationException {
        return prepare(engine,style,deviceCard,false);
    }
    Swap prepare(Drawable engine,QsTileAppearance.Style style,boolean deviceCard,boolean activeTransition) throws ReflectiveOperationException {
        reason.set("ready");
        Object nativeShader=QsTileAppearance.field(engine,"drawableShader"),original=QsTileAppearance.field(nativeShader,"shader");
        if(!(original instanceof RuntimeShader))return reject("native RuntimeShader absent");
        if(!Boolean.TRUE.equals(QsTileAppearance.field(engine,"enableShader")))return reject("native shader disabled");
        Object multi=QsTileAppearance.field(nativeShader,"multiBlendParam"),summary=QsTileAppearance.field(nativeShader,"summaryBlendParam");
        if(!(multi instanceof List)||!(summary instanceof List))return reject("native blend lists absent");
        List<?> params=(List<?>)multi,all=(List<?>)summary;int[] slots=activeSlots(params,all);
        if(slots==null&&deviceCard)slots=inactiveDeviceSlots(params,all);
        if(slots==null&&activeTransition)slots=transitionSlots(params,all);
        if(slots==null)return reject("native active slots unsupported multi "+params.size()+" summary "+all.size()+" foregroundTop "+nativeParam(params,2)+" foregroundBottom "+nativeParam(params,3));
        Object corner=QsTileAppearance.call(QsTileAppearance.field(nativeShader,"mCornerParams"),"getType");
        Object meta=QsTileAppearance.field(nativeShader,"metaBallParams"),valid=QsTileAppearance.field(meta,"valid");
        if(corner==null||Boolean.TRUE.equals(valid))return reject("native corner absent or metaball active");
        Rect bounds=engine.getBounds();if(!QsTileAppearance.valid(bounds))return reject("native bounds empty or unsupported");
        Object effects=QsTileAppearance.invoke(nativeShader,"getAllEffects");if(!(effects instanceof List))return reject("native effects absent");
        ClassLoader loader=nativeShader.getClass().getClassLoader();
        Class<?> builder=Class.forName("com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt",false,loader);
        StringBuilder source=new StringBuilder();
        QsTileAppearance.invoke(builder,"buildShaderString",source,QsTileAppearance.field(nativeShader,"blendAlgorithmMask"),all.size(),corner,false,effects);
        String material=decorate(source.toString());if(material==null)return reject("native shader source unsupported");
        Cached cached=cache.get(nativeShader);
        if(cached==null||!cached.source.equals(material)){cached=new Cached(material,new RuntimeShader(material));cache.put(nativeShader,cached);}
        RuntimeShader replacement=cached.shader;
        // Ask native routines to transfer their current bitmap, shape, coordinates and each optical layer.
        QsTileAppearance.invoke(nativeShader,"setBaseUniform",replacement);
        replacement.setIntUniform("uEnableBlend",Boolean.TRUE.equals(QsTileAppearance.field(nativeShader,"enableBlend"))?1:0);
        replacement.setFloatUniform("u_multiBlendParams",uniforms(all));
        for(Object effect:(List<?>)effects)if(Boolean.TRUE.equals(QsTileAppearance.call(effect,"isEnabled")))QsTileAppearance.invoke(effect,"pushUniforms",replacement);
        replacement.setInputShader("c17QsFill",QsTileAppearance.shader(style,new Rect(0,0,bounds.right-bounds.left,bounds.bottom-bounds.top)));
        replacement.setFloatUniform("c17QsSlots",slots[0]*5f,slots[1]*5f);
        Object nativePaint=QsTileAppearance.field(engine,"drawableShaderPaint");
        Swap swap=new Swap(nativeShader,(RuntimeShader)original,replacement,nativePaint instanceof Paint?(Paint)nativePaint:null);
        if(!QsTileAppearance.setField(nativeShader,"shader",replacement))return reject("native shader field unavailable");
        return swap;
    }
    private static String nativeParam(List<?> values,int index) {
        if(index>=values.size())return "absent";
        Object value=values.get(index),mode=QsTileAppearance.field(value,"mode"),color=QsTileAppearance.field(value,"color");
        return "mode "+mode+" RGB "+(color instanceof Number?Integer.toHexString(((Number)color).intValue()&0xffffff):"absent");
    }
    /** Only the two native QS_ACTIVE material colors, not foreground haze, strokes or icon colors. */
    static int[] activeSlots(List<?> params,List<?> summary) {
        if(params.size()!=4)return null;
        // Constructor argument 1 is foregroundShaderParam; native updateMixMultiShaderParams
        // emits background haze first (0/1), then this original active white fill (2/3).
        Object top=params.get(2),bottom=params.get(3);
        Object a=QsTileAppearance.field(top,"color"),b=QsTileAppearance.field(bottom,"color"),am=QsTileAppearance.field(top,"mode"),bm=QsTileAppearance.field(bottom,"mode");
        if(!(a instanceof Number)||!(b instanceof Number)||!(am instanceof Number)||!(bm instanceof Number))return null;
        if((((Number)a).intValue()&0xffffff)!=0xe6e6e6||(((Number)b).intValue()&0xffffff)!=0xcccccc||((Number)am).intValue()!=5||((Number)bm).intValue()!=3)return null;
        int i=-1,j=-1;for(int n=0;n<summary.size();n++){if(summary.get(n)==top)i=n;if(summary.get(n)==bottom)j=n;}
        return i>=0&&j>=0&&i!=j?new int[]{i,j}:null;
    }
    /** Confirmed devices-row inactive material, admitted only after the exact native card/base gate. */
    static int[] inactiveDeviceSlots(List<?> params,List<?> summary) {
        if(params.size()!=4)return null;
        Object top=params.get(2),bottom=params.get(3);
        Object a=QsTileAppearance.field(top,"color"),b=QsTileAppearance.field(bottom,"color"),am=QsTileAppearance.field(top,"mode"),bm=QsTileAppearance.field(bottom,"mode");
        if(!(a instanceof Number)||!(b instanceof Number)||!(am instanceof Number)||!(bm instanceof Number))return null;
        int first=((Number)a).intValue(),second=((Number)b).intValue();
        boolean known=first==0x19404040&&second==0x4d737373||first==0x80404040&&second==0xb2737373
                ||first==0x40404040&&second==0x667b7b7b||first==0x5a404040&&second==0xb27b7b7b;
        if(!known||((Number)am).intValue()!=5||((Number)bm).intValue()!=3)return null;
        int i=-1,j=-1;for(int n=0;n<summary.size();n++){if(summary.get(n)==top)i=n;if(summary.get(n)==bottom)j=n;}
        return i>=0&&j>=0&&i!=j?new int[]{i,j}:null;
    }
    /** Native active-to-inactive ArgbEvaluator segment, gated by an active tile's running native animator. */
    static int[] transitionSlots(List<?> params,List<?> summary) {
        if(params.size()!=4)return null;
        Object top=params.get(2),bottom=params.get(3),a=QsTileAppearance.field(top,"color"),b=QsTileAppearance.field(bottom,"color");
        Object am=QsTileAppearance.field(top,"mode"),bm=QsTileAppearance.field(bottom,"mode");
        if(!(a instanceof Number)||!(b instanceof Number)||!(am instanceof Number)||!(bm instanceof Number)
                ||((Number)am).intValue()!=5||((Number)bm).intValue()!=3)return null;
        int first=((Number)a).intValue(),second=((Number)b).intValue();boolean known=false;
        for(int[] pair:new int[][]{{0x19404040,0x4d737373,0x73e6e6e6},{0x80404040,0xb2737373,0x73e6e6e6},
                {0x40404040,0x667b7b7b,0x7de6e6e6},{0x5a404040,0xb27b7b7b,0x7de6e6e6}}) {
            // Android ArgbEvaluator interpolates RGB in linear light (gamma 2.2), alpha linearly.
            double start=linear(pair[0]&255),end=linear(pair[2]&255);
            float fraction=(float)((linear(first&255)-start)/(end-start));
            if(fraction>=0f&&fraction<=1f&&interpolated(first,pair[0],pair[2],fraction)&&interpolated(second,pair[1],0x40cccccc,fraction)){known=true;break;}
        }
        if(!known)return null;
        int i=-1,j=-1;for(int n=0;n<summary.size();n++){if(summary.get(n)==top)i=n;if(summary.get(n)==bottom)j=n;}
        return i>=0&&j>=0&&i!=j?new int[]{i,j}:null;
    }
    private static boolean interpolated(int value,int start,int end,float fraction) {
        for(int shift:new int[]{0,8,16,24}) {
            int a=(start>>>shift)&255,b=(end>>>shift)&255,actual=(value>>>shift)&255;
            double expected=shift==24?a+(b-a)*fraction:Math.pow(linear(a)+(linear(b)-linear(a))*fraction,1.0/2.2)*255.0;
            if(Math.abs(actual-expected)>2f)return false;
        }return true;
    }
    private static double linear(int channel){return Math.pow(channel/255.0,2.2);}
    static float[] uniforms(List<?> params) {
        float[] result=new float[params.size()*5];
        for(int i=0;i<params.size();i++) {
            Object param=params.get(i),mode=QsTileAppearance.field(param,"mode"),color=QsTileAppearance.field(param,"color");
            if(!(mode instanceof Number)||!(color instanceof Number))throw new IllegalArgumentException("Unknown native material color parameter");
            int argb=((Number)color).intValue(),offset=i*5;result[offset]=((Number)mode).intValue();
            result[offset+1]=((argb>>>16)&255)/255f;result[offset+2]=((argb>>>8)&255)/255f;result[offset+3]=(argb&255)/255f;result[offset+4]=(argb>>>24)/255f;
        }return result;
    }
    static String decorate(String nativeSource) {
        if(nativeSource==null||nativeSource.indexOf(MAIN)<0||nativeSource.indexOf("c17QsFill")>=0)return null;
        Matcher matcher=BASE_ARGUMENT.matcher(nativeSource);if(!matcher.find())return null;
        String argument=matcher.group();if(matcher.find())return null;
        String body=BASE_ARGUMENT.matcher(nativeSource).replaceFirst(Matcher.quoteReplacement("c17QsBase("+argument+", i, position)"));
        return body.replace(MAIN,FILL_CODE+MAIN);
    }
}
