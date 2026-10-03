package dev.puitheme;

import android.content.Context;
import android.os.Bundle;
import androidx.compose.ui.Modifier;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import kotlin.jvm.functions.Function2;

/** Replays native flags/restarts and confines typography to the actual network content. */
public final class NativeNetworkBadgeControlsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Native badge "+checks+": "+expected+" != "+actual);}
    private static long pack(float value){return pack(value,1L<<32);}
    private static long pack(float value,long type){return type|(Float.floatToRawIntBits(value)&0xffffffffL);}
    private static float size(Object value){return Float.intBitsToFloat((int)((Long)value).longValue());}
    private static final class Weight {final int value;Weight(int value){this.value=value;} }
    private static final class Family {final Object nativeFamily;final String mode;final int realAxis;Family(Object family,String mode,int weight){nativeFamily=family;this.mode=mode;realAxis=weight;} }
    private static final class Layer implements Modifier {
        final Modifier base;final float x,y;Layer(Modifier base,float x,float y){this.base=base;this.x=x;this.y=y;}
        public Object foldIn(Object initial,Function2 visitor){return visitor.invoke(base.foldIn(initial,visitor),this);}
    }
    private static final class Drawing implements Modifier {
        final Modifier base;final Object token;Drawing(Modifier base,Object token){this.base=base;this.token=token;}
        public Object foldIn(Object initial,Function2 visitor){return visitor.invoke(base.foldIn(initial,visitor),this);}
    }
    private static final class Native implements NativeNetworkBadgeControls.Access {
        final Map<Integer,Weight> weights=new HashMap<>();final Map<String,Family> families=new HashMap<>();
        int translations,drawings,createdWeights,createdFamilies;boolean fail,partsSupported=true;
        public boolean supportsParts(){return partsSupported;}
        public Object drawing(Object base,Object token)throws ReflectiveOperationException{if(fail)throw new ReflectiveOperationException();drawings++;return new Drawing((Modifier)base,token);}
        public boolean modifier(Object value){return value instanceof Modifier;}
        public Object translate(Object base,float x,float y)throws ReflectiveOperationException{if(fail)throw new ReflectiveOperationException();translations++;return new Layer((Modifier)base,x,y);}
        public int weight(Object object){return ((Weight)object).value;}
        public Object weight(int weight){return weights.computeIfAbsent(weight,key->{createdWeights++;return new Weight(key);});}
        public Object family(Context context,Object nativeFamily,String mode,String revision,int weight){
            String key=System.identityHashCode(nativeFamily)+":"+mode+":"+revision+":"+weight;
            return families.computeIfAbsent(key,k->{createdFamilies++;return new Family(nativeFamily,mode,weight);});
        }
    }
    private static Bundle settings(boolean enabled,float x,float y,float scale,float weight){
        Bundle b=new Bundle();b.putBoolean(NativeNetworkBadgeControls.MASTER,enabled);b.putFloat(NativeNetworkBadgeControls.X,x);
        b.putFloat(NativeNetworkBadgeControls.Y,y);b.putFloat(NativeNetworkBadgeControls.SCALE,scale);b.putFloat(NativeNetworkBadgeControls.WEIGHT,weight);return b;
    }
    private static Object[] args(Native n,Object composer,Modifier modifier){
        return new Object[]{"5",0x123456789aL,modifier,new Object(),n.weight(700),"G",pack(12),pack(9),.15f,"5G",composer,0,0};
    }
    private static Object[] nativeContent(NativeNetworkBadgeControls h,Object owner,Object[] a,float density){
        try(NativeNetworkBadgeControls.Scope ignored=h.enterOwner(owner)){return h.adjustText(a,density,null);}
    }
    private static Object[] savedRestart(Object[] a){Object[] result=a.clone();result[11]=((Integer)a[11])|1;return result;}
    private static void unchanged(Object[] source,Object[] actual){
        for(int index:new int[]{0,1,5,8,9,10,11,12})equal(source[index],actual[index]);
    }
    /** Upper compiler emits changed slots in order without nested groups. Clearing any
     * supplied dirty pair can shift the later remembered ComposableLambda into a TextUnit. */
    private static int changedSlots(int dirty,int mask){int slots=0;for(int i=0;i<10;i++)if((dirty&(3<<(1+3*i)))==0&&(mask&(1<<i))==0)slots++;return slots;}
    public static void main(String[] ignored)throws Exception{
        for(boolean onPaint:new boolean[]{false,true}){
            android.graphics.Paint.fontVariationOnPaint=onPaint;FontWeight.release();
            android.graphics.Typeface face=new android.graphics.Typeface();face.variableWeight=true;face.weight=450;face.italic=true;
            face.variationWeight=725;face.variationWidth=92;face.variationRound=30;
            for(int w:new int[]{1,200,437,900,1000}){
                android.graphics.Typeface adjusted=NativeNetworkBadgeFont.axisFace(face,w);
                equal((float)w,adjusted.effectiveWeight());equal(true,adjusted.isItalic());
                equal(92f,adjusted.variationWidth);equal(30f,adjusted.variationRound);equal(725f,face.effectiveWeight());
            }
        }
        android.graphics.Paint.fontVariationOnPaint=false;FontWeight.release();
        Native n=new Native();NativeNetworkBadgeControls h=new NativeNetworkBadgeControls(n);
        Object owner=new Object(),composer=new Object();Modifier modifier=new Modifier.Element();Object[] original=args(n,composer,modifier);
        equal(false,h.enabled());equal(false,h.needsAdjustment(modifier,composer));equal(original,h.adjustText(original,2,null));
        equal(false,NativeNetworkBadgeControls.booleanDefaults().get(NativeNetworkBadgeControls.MASTER));equal("native",NativeNetworkBadgeControls.stringDefaults().get(NativeNetworkBadgeControls.FONT));
        Bundle enabled=settings(true,2,-1,150,600);equal(true,h.configure(enabled));
        // An unrelated roaming renderer never receives badge settings even while enabled.
        equal(original,h.adjustText(original,2,null));equal(false,h.needsAdjustment(modifier,composer));
        Object[] styled=nativeContent(h,owner,original,2);unchanged(original,styled);equal(18f,size(styled[6]));equal(13.5f,size(styled[7]));
        equal(600,((Weight)styled[4]).value);equal(600,((Family)styled[3]).realAxis);equal(original[3],((Family)styled[3]).nativeFamily);
        Layer layer=(Layer)styled[2];equal(modifier,layer.base);equal(4f,layer.x);equal(-2f,layer.y);equal(12f,size(original[6]));equal(700,((Weight)original[4]).value);
        int translations=n.translations,weights=n.createdWeights,families=n.createdFamilies;
        Object family=styled[3],weight=styled[4];
        for(int i=0;i<10000;i++){
            Object[] callback=savedRestart(styled);styled=h.adjustText(callback,2,null);unchanged(callback,styled);
            equal(layer,styled[2]);equal(family,styled[3]);equal(weight,styled[4]);equal(18f,size(styled[6]));equal(13.5f,size(styled[7]));
        }
        equal(translations,n.translations);equal(weights,n.createdWeights);equal(families,n.createdFamilies);
        // Fresh network typography/font-scale updates become the new baseline, not our last output.
        Object[] fresh=original.clone();fresh[6]=pack(16);fresh[7]=pack(6);fresh[4]=n.weight(500);fresh[3]=new Object();
        styled=nativeContent(h,owner,fresh,3);equal(24f,size(styled[6]));equal(9f,size(styled[7]));equal(6f,((Layer)styled[2]).x);
        equal(true,h.configure(settings(false,0,0,100,400)));Object[] callback=savedRestart(styled),restored=h.adjustText(callback,3,null);
        equal(fresh[2],restored[2]);equal(fresh[3],restored[3]);equal(fresh[4],restored[4]);equal(fresh[6],restored[6]);equal(fresh[7],restored[7]);unchanged(callback,restored);
        // On/off never changes the upper native arguments or introduces extra changed() slots.
        for(int dirty:new int[]{0,1,0x12492492,0x7fffffff})for(int mask:new int[]{0,8|32|256|512,16|64}){
            Object[] upper=new Object[13];upper[11]=dirty;upper[12]=mask;int before=changedSlots(dirty,mask);
            h.configure(enabled);equal(upper,h.adjust(upper,4));equal(before,changedSlots((Integer)upper[11],(Integer)upper[12]));
            h.configure(new Bundle());equal(upper,h.adjust(upper,4));equal(before,changedSlots((Integer)upper[11],(Integer)upper[12]));
        }
        // Original SP/EM type, relative ratio and native baseline shift remain intact.
        h.configure(settings(true,0,0,50,400));Object[] em=args(n,new Object(),new Modifier.Element());em[6]=pack(22,2L<<32);em[7]=pack(8,2L<<32);
        styled=nativeContent(h,owner,em,Float.NaN);equal(11f,size(styled[6]));equal(4f,size(styled[7]));equal(2L<<32,((Long)styled[6])&0xffffffff00000000L);equal(.15f,styled[8]);
        equal(pack(Float.NaN,0),NativeNetworkBadgeControls.scaleUnit(pack(Float.NaN,0),200));equal(pack(12),NativeNetworkBadgeControls.scaleUnit(pack(12),Float.NaN));
        equal(pack(-1),NativeNetworkBadgeControls.scaleUnit(pack(-1),100));equal(4096f,size(NativeNetworkBadgeControls.scaleUnit(pack(22),Float.MAX_VALUE)));
        h.configure(settings(true,0,0,0,1000));styled=nativeContent(h,owner,original,1);equal(0f,size(styled[6]));equal(1000,((Weight)styled[4]).value);
        h.configure(settings(true,0,0,100,400));Object[] normal=h.adjustText(savedRestart(styled),1,null);equal(modifier,normal[2]);equal(original[6],normal[6]);
        // Independent family choice does not touch any other renderer or the global configuration.
        Bundle choice=settings(true,0,0,100,800);choice.putString(NativeNetworkBadgeControls.FONT,"pingfang");h.configure(choice);
        styled=nativeContent(h,owner,original,1);equal("pingfang",((Family)styled[3]).mode);equal(800,((Family)styled[3]).realAxis);
        choice.putString(NativeNetworkBadgeControls.FONT,"custom");choice.putString(StatusBarSettings.FONT_REVISION,"different-font");equal(true,h.configure(choice));
        styled=nativeContent(h,owner,original,1);equal("custom",((Family)styled[3]).mode);
        // Nested scope/finally restoration does not leak styling into a following roaming call.
        Object[] unrelated=args(n,new Object(),new Modifier.Element());try(NativeNetworkBadgeControls.Scope first=h.enterOwner(owner)){
            try(NativeNetworkBadgeControls.Scope other=h.enterOwner(null)){equal(unrelated,h.adjustText(unrelated,1,null));}
            equal(true,h.needsAdjustment(unrelated[2],unrelated[10]));
        }equal(false,h.needsAdjustment(unrelated[2],unrelated[10]));equal(unrelated,h.adjustText(unrelated,1,null));
        Bundle label=settings(true,1,2,175,650);label.putBoolean("label_enabled",true);h.configure(label);equal(false,h.enabled());
        Object[] labelNative=h.adjustText(savedRestart(styled),1,null);equal(original[3],labelNative[3]);equal(original[6],labelNative[6]);
        h.configure(enabled);styled=nativeContent(h,owner,original,1);Bundle safe=settings(true,1,2,175,650);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);h.configure(safe);
        equal(false,h.enabled());Object[] safeNative=h.adjustText(savedRestart(styled),1,null);equal(original[3],safeNative[3]);equal(original[2],safeNative[2]);
        // Defaulted/unsupported lower structures cannot change parameter masks or slot paths.
        h.configure(enabled);Object[] bad=original.clone();bad[12]=4|16;equal(bad,nativeContent(h,owner,bad,1));bad=original.clone();bad[6]=new Object();equal(bad,nativeContent(h,owner,bad,1));
        bad=original.clone();bad[2]=new Object();equal(bad,nativeContent(h,owner,bad,1));equal(new Object[12].length,12);
        n.fail=true;equal(original,nativeContent(h,owner,original,4));n.fail=false;
        // Network order still sees its one native marker beneath the immutable placement.
        NetworkIconOrder order=new NetworkIconOrder();order.resolveCompose(NativeNetworkBadgeControlsCheck.class.getClassLoader());Bundle swap=new Bundle();swap.putBoolean(NetworkIconOrder.SWAP,true);order.configure(swap);
        Modifier tag=(Modifier)order.textModifier(modifier);Object[] tagged=args(n,new Object(),tag);styled=nativeContent(h,owner,tagged,2);equal(styled[2],order.textModifier(styled[2]));equal(tag,((Layer)styled[2]).base);
        h.configure(new Bundle());restored=h.adjustText(savedRestart(styled),2,null);equal(tag,restored[2]);equal(tagged[6],restored[6]);order.releaseRuntime();
        h.releaseRuntime();equal(false,h.enabled());equal(false,h.needsAdjustment(modifier,composer));equal(false,h.configure(enabled));equal(original,h.adjustText(original,1,null));
        independentParts();
        hiddenParts();
        optionalCapabilities();
        nativeTextViews();
        nativeTextViewMovement();
        System.out.println("NativeNetworkBadgeControls checks passed: "+checks);
    }
    private static Object[] span(Object[] lower,int part){
        Object[] args=new Object[15];args[0]=0L;args[1]=lower[6+part];args[7]=0L;args[11]=0L;args[8]=part==0?null:lower[8];args[14]=part==0?0xfffd:0xfefd;return args;
    }
    private static Object[] run(Object value,int start,int end,int contextStart,int contextEnd){
        return new Object[]{value,start,end,contextStart,contextEnd,10f,20f,false,new Object()};
    }
    private static void independentParts(){
        equal(false,NativeNetworkBadgeSettings.booleanDefaults().get(NativeNetworkBadgeSettings.MASTER));
        equal(true,NativeNetworkBadgeSettings.booleanDefaults().get(NativeNetworkBadgeSettings.key(1,"enabled")));
        equal(true,NativeNetworkBadgeSettings.booleanDefaults().get(NativeNetworkBadgeSettings.key(2,"enabled")));
        Map<String,Object> old=new HashMap<>();old.put(NativeNetworkBadgeControls.X,7f);old.put(NativeNetworkBadgeControls.Y,-3f);
        old.put(NativeNetworkBadgeControls.SCALE,123f);old.put(NativeNetworkBadgeControls.WEIGHT,650f);old.put(NativeNetworkBadgeControls.FONT,"custom");
        Map<String,Object> inherited=NativeNetworkBadgeSettings.inheritedDefaults(old);equal(12,inherited.size());
        for(int part=1;part<=2;part++){
            equal(7f,inherited.get(NativeNetworkBadgeSettings.key(part,"offset_x")));equal(-3f,inherited.get(NativeNetworkBadgeSettings.key(part,"offset_y")));
            equal(123f,inherited.get(NativeNetworkBadgeSettings.key(part,"scale")));equal(650f,inherited.get(NativeNetworkBadgeSettings.key(part,"weight")));
            equal("custom",inherited.get(NativeNetworkBadgeSettings.key(part,"font")));equal(true,inherited.get(NativeNetworkBadgeSettings.key(part,"enabled")));
            equal(NativeNetworkBadgeControls.X,NativeNetworkBadgeSettings.legacyKey(NativeNetworkBadgeSettings.key(part,"offset_x")));
            equal(null,NativeNetworkBadgeSettings.legacyKey(NativeNetworkBadgeSettings.key(part,"enabled")));
            equal(false,NativeNetworkBadgeSettings.booleanDefaults().get(NativeNetworkBadgeSettings.key(part,"hidden")));
            equal(null,NativeNetworkBadgeSettings.legacyKey(NativeNetworkBadgeSettings.key(part,"hidden")));
        }
        old.put(NativeNetworkBadgeSettings.key(1,"offset_x"),11f);old.put(NativeNetworkBadgeSettings.key(2,"enabled"),false);
        inherited=NativeNetworkBadgeSettings.inheritedDefaults(old);equal(11f,inherited.get(NativeNetworkBadgeSettings.key(1,"offset_x")));
        equal(7f,inherited.get(NativeNetworkBadgeSettings.key(2,"offset_x")));equal(false,inherited.get(NativeNetworkBadgeSettings.key(2,"enabled")));
        Native n=new Native();NativeNetworkBadgeControls h=new NativeNetworkBadgeControls(n);equal(true,h.setPartsReady(true));
        Bundle settings=settings(true,0,0,100,400);
        settings.putFloat(NativeNetworkBadgeSettings.key(1,"offset_x"),2f);settings.putFloat(NativeNetworkBadgeSettings.key(1,"offset_y"),-1f);
        settings.putFloat(NativeNetworkBadgeSettings.key(2,"offset_x"),-3f);settings.putFloat(NativeNetworkBadgeSettings.key(2,"offset_y"),4f);
        settings.putFloat(NativeNetworkBadgeSettings.key(1,"scale"),150f);settings.putFloat(NativeNetworkBadgeSettings.key(2,"scale"),50f);
        settings.putFloat(NativeNetworkBadgeSettings.key(1,"weight"),600f);settings.putFloat(NativeNetworkBadgeSettings.key(2,"weight"),900f);
        settings.putString(NativeNetworkBadgeSettings.key(1,"font"),"custom");settings.putString(NativeNetworkBadgeSettings.key(2,"font"),"system");
        h.configure(settings);Object owner=new Object(),composer=new Object();Modifier modifier=new Modifier.Element();Object[] original=args(n,composer,modifier);
        Object[] styled=nativeContent(h,owner,original,2);unchanged(original,styled);equal(18f,size(styled[6]));equal(4.5f,size(styled[7]));
        equal(original[3],styled[3]);equal(original[4],styled[4]);Drawing layer=(Drawing)styled[2];equal(modifier,layer.base);
        Object[] first=span(styled,0),second=span(styled,1),a,b;
        equal(false,h.needsSpanAdjustment());equal(first,h.adjustSpanStyle(first));
        try(NativeNetworkBadgeControls.TextScope scope=h.enterText(styled,null)){
            equal(true,h.needsSpanAdjustment());
            Object[] other=first.clone();other[14]=123;equal(other,h.adjustSpanStyle(other));
            a=h.adjustSpanStyle(first);b=h.adjustSpanStyle(second);
            equal(600,((Weight)a[2]).value);equal(900,((Weight)b[2]).value);
            equal("custom",((Family)a[5]).mode);equal("system",((Family)b[5]).mode);
            equal(original[3],((Family)a[5]).nativeFamily);equal(original[3],((Family)b[5]).nativeFamily);
            equal(0xfffd&~36,a[14]);equal(0xfefd&~36,b[14]);equal(first[1],a[1]);equal(second[1],b[1]);equal(second[8],b[8]);
            equal(first,h.adjustSpanStyle(first));
            equal(false,h.needsSpanAdjustment());
        }
        equal(false,h.needsRunAdjustment());
        try(NativeNetworkBadgeControls.DrawScope scope=h.enterDrawing(layer.token)){
            equal(true,h.needsRunAdjustment());
            Object[] prefix=run("5G",0,1,0,1),suffix=run("5G",1,2,1,2),adjusted;
            try(NativeNetworkBadgeControls.RunScope run=h.enterRun()){
                adjusted=run.adjust(prefix,false);equal(14f,adjusted[5]);equal(18f,adjusted[6]);equal(prefix[0],adjusted[0]);equal(prefix[1],adjusted[1]);
                equal(false,h.needsRunAdjustment());try(NativeNetworkBadgeControls.RunScope nested=h.enterRun()){equal(prefix,nested.adjust(prefix,false));}
            }
            try(NativeNetworkBadgeControls.RunScope run=h.enterRun()){adjusted=run.adjust(suffix,false);equal(4f,adjusted[5]);equal(28f,adjusted[6]);}
            Object[] suffixChars=run("5G".toCharArray(),1,1,1,1);
            try(NativeNetworkBadgeControls.RunScope run=h.enterRun()){adjusted=run.adjust(suffixChars,true);equal(4f,adjusted[5]);equal(28f,adjusted[6]);}
            Object[] unrelated=run("RG",0,1,0,1),merged=run("5G",0,2,0,2);
            try(NativeNetworkBadgeControls.RunScope run=h.enterRun()){equal(unrelated,run.adjust(unrelated,false));equal(merged,run.adjust(merged,false));}
            try(NativeNetworkBadgeControls.DrawScope other=h.enterDrawing(null)){equal(false,h.needsRunAdjustment());}
            equal(true,h.needsRunAdjustment());
        }
        equal(false,h.needsRunAdjustment());int drawings=n.drawings,families=n.createdFamilies,weights=n.createdWeights;
        for(int i=0;i<10000;i++){
            Object[] callback=savedRestart(styled);styled=h.adjustText(callback,2,null);unchanged(callback,styled);equal(layer,styled[2]);equal(18f,size(styled[6]));equal(4.5f,size(styled[7]));
            try(NativeNetworkBadgeControls.TextScope scope=h.enterText(styled,null)){equal(a[5],h.adjustSpanStyle(first)[5]);equal(b[5],h.adjustSpanStyle(second)[5]);}
        }
        equal(drawings,n.drawings);equal(families,n.createdFamilies);equal(weights,n.createdWeights);
        Object previousModifier=styled[2];settings.putFloat(NativeNetworkBadgeSettings.key(1,"weight"),800f);h.configure(settings);
        Object[] previousArgs=savedRestart(styled);styled=h.adjustText(previousArgs,2,null);equal(false,previousModifier==styled[2]);unchanged(previousArgs,styled);
        equal(previousArgs[6],styled[6]);equal(previousArgs[7],styled[7]);
        try(NativeNetworkBadgeControls.TextScope scope=h.enterText(styled,null)){equal(800,((Weight)h.adjustSpanStyle(span(styled,0))[2]).value);}
        settings.putBoolean(NativeNetworkBadgeSettings.key(2,"enabled"),false);h.configure(settings);styled=h.adjustText(savedRestart(styled),2,null);
        equal(18f,size(styled[6]));equal(original[7],styled[7]);second=span(styled,1);
        try(NativeNetworkBadgeControls.TextScope scope=h.enterText(styled,null)){h.adjustSpanStyle(span(styled,0));equal(second,h.adjustSpanStyle(second));}
        try(NativeNetworkBadgeControls.DrawScope scope=h.enterDrawing(layer.token);NativeNetworkBadgeControls.RunScope run=h.enterRun()){
            Object[] suffix=run("5G",1,2,1,2);equal(suffix,run.adjust(suffix,false));
        }
        // Two independent native compositions sharing a Modifier do not share text or density.
        Object[] another=args(n,composer,modifier);another[0]="4";another[5]="G+";Object[] anotherStyled=nativeContent(h,new Object(),another,3);
        equal(false,((Drawing)styled[2]).token==((Drawing)anotherStyled[2]).token);
        try(NativeNetworkBadgeControls.DrawScope scope=h.enterDrawing(((Drawing)anotherStyled[2]).token);NativeNetworkBadgeControls.RunScope run=h.enterRun()){
            Object[] prefix=run("4G+",0,1,0,1);equal(16f,run.adjust(prefix,false)[5]);equal(17f,run.adjust(prefix,false)[6]);
            Object[] wrong=run("5G",0,1,0,1);equal(wrong,run.adjust(wrong,false));
        }
        try(NativeNetworkBadgeControls.DrawScope scope=h.enterDrawing(((Drawing)styled[2]).token);NativeNetworkBadgeControls.RunScope run=h.enterRun()){
            Object[] prefix=run("5G",0,1,0,1);equal(14f,run.adjust(prefix,false)[5]);equal(18f,run.adjust(prefix,false)[6]);
        }
        settings.putBoolean(NativeNetworkBadgeControls.MASTER,false);h.configure(settings);Object[] restored=h.adjustText(savedRestart(styled),2,null);
        equal(original[2],restored[2]);equal(original[3],restored[3]);equal(original[4],restored[4]);equal(original[6],restored[6]);equal(original[7],restored[7]);
        try(NativeNetworkBadgeControls.DrawScope scope=h.enterDrawing(layer.token)){equal(false,h.needsRunAdjustment());}
        settings.putBoolean(NativeNetworkBadgeControls.MASTER,true);settings.putBoolean("label_enabled",true);h.configure(settings);
        restored=h.adjustText(savedRestart(styled),2,null);equal(original[2],restored[2]);equal(original[6],restored[6]);equal(original[7],restored[7]);
        settings.putBoolean("label_enabled",false);settings.putBoolean(StatusBarSettings.SAFE_MODE,true);h.configure(settings);
        restored=h.adjustText(savedRestart(styled),2,null);equal(original[2],restored[2]);equal(original[6],restored[6]);equal(original[7],restored[7]);
        settings.putBoolean(StatusBarSettings.SAFE_MODE,false);settings.putBoolean(NativeNetworkBadgeSettings.key(1,"enabled"),false);h.configure(settings);
        Object[] never=args(n,new Object(),new Modifier.Element());try(NativeNetworkBadgeControls.Scope scope=h.enterOwner(owner)){
            equal(false,h.needsAdjustment(never[2],never[10]));equal(never,h.adjustText(never,2,null));
        }
        h.releaseRuntime();equal(false,h.setPartsReady(true));equal(first,h.adjustSpanStyle(first));
    }
    private static void hiddenParts(){
        Native n=new Native();NativeNetworkBadgeControls h=new NativeNetworkBadgeControls(n);equal(true,h.setPartsReady(true));
        Bundle settings=settings(true,0,0,100,400);
        String firstHidden=NativeNetworkBadgeSettings.key(1,"hidden"),secondHidden=NativeNetworkBadgeSettings.key(2,"hidden");
        equal(false,NativeNetworkBadgeSettings.booleanDefaults().get(firstHidden));
        equal(false,NativeNetworkBadgeSettings.booleanDefaults().get(secondHidden));
        settings.putBoolean(firstHidden,true);settings.putFloat(NativeNetworkBadgeSettings.key(2,"offset_x"),3f);
        settings.putFloat(NativeNetworkBadgeSettings.key(2,"weight"),850f);h.configure(settings);
        Object owner=new Object(),composer=new Object();Modifier modifier=new Modifier.Element();Object[] original=args(n,composer,modifier);
        Object[] styled=nativeContent(h,owner,original,2);
        equal("",styled[0]);equal("G",styled[5]);equal(original[6],styled[6]);equal(original[7],styled[7]);
        for(int index:new int[]{1,8,9,10,11,12})equal(original[index],styled[index]);
        try(NativeNetworkBadgeControls.TextScope scope=h.enterText(styled,null)){
            equal(true,h.needsSpanAdjustment());h.adjustSpanStyle(span(styled,0));
            equal(850,((Weight)h.adjustSpanStyle(span(styled,1))[2]).value);equal(false,h.needsSpanAdjustment());
        }
        Drawing layer=(Drawing)styled[2];
        try(NativeNetworkBadgeControls.DrawScope scope=h.enterDrawing(layer.token);NativeNetworkBadgeControls.RunScope run=h.enterRun()){
            Object[] visible=run("G",0,1,0,1);equal(16f,run.adjust(visible,false)[5]);
            Object[] previous=run("5G",0,1,0,1);equal(previous,run.adjust(previous,false));
        }
        try(NativeNetworkBadgeControls.DrawScope scope=h.enterDrawing(layer.token);NativeNetworkBadgeControls.RunScope run=h.enterRun()){
            Object[] chars=run("G".toCharArray(),0,1,0,1);equal(16f,run.adjust(chars,true)[5]);
        }
        int drawings=n.drawings;
        for(int i=0;i<1000;i++){
            Object[] callback=savedRestart(styled);styled=h.adjustText(callback,2,null);
            equal("",styled[0]);equal("G",styled[5]);equal(layer,styled[2]);equal(callback[11],styled[11]);equal(0,styled[12]);
        }
        equal(drawings,n.drawings);
        settings.putBoolean(secondHidden,true);h.configure(settings);styled=h.adjustText(savedRestart(styled),2,null);
        equal("",styled[0]);equal("",styled[5]);equal(false,layer==styled[2]);
        settings.putBoolean(firstHidden,false);h.configure(settings);styled=h.adjustText(savedRestart(styled),2,null);
        equal("5",styled[0]);equal("",styled[5]);
        // Disabling the child's style restores its native text even while hidden is saved.
        settings.putBoolean(NativeNetworkBadgeSettings.key(2,"enabled"),false);h.configure(settings);
        styled=h.adjustText(savedRestart(styled),2,null);equal("5",styled[0]);equal("G",styled[5]);
        settings.putBoolean(firstHidden,true);h.configure(settings);styled=h.adjustText(savedRestart(styled),2,null);
        equal("",styled[0]);equal("G",styled[5]);
        // Same Modifier+Composer, a second native owner keeps its own original strings.
        Object[] other=original.clone();other[0]="4";other[5]="G+";
        Object[] styledOther=nativeContent(h,new Object(),other,3);equal("",styledOther[0]);equal("G+",styledOther[5]);
        equal(false,((Drawing)styled[2]).token==((Drawing)styledOther[2]).token);
        settings.putBoolean(firstHidden,false);h.configure(settings);
        Object[] shown=h.adjustText(savedRestart(styled),2,null),shownOther=h.adjustText(savedRestart(styledOther),3,null);
        equal("5",shown[0]);equal("G",shown[5]);equal("4",shownOther[0]);equal("G+",shownOther[5]);
        settings.putBoolean(firstHidden,true);settings.putBoolean(NativeNetworkBadgeSettings.key(2,"enabled"),true);
        h.configure(settings);styled=nativeContent(h,owner,original,2);equal("",styled[0]);equal("",styled[5]);
        for(String suppress:new String[]{NativeNetworkBadgeControls.MASTER,"label_enabled",StatusBarSettings.SAFE_MODE}){
            Bundle off=new Bundle(settings);off.putBoolean(suppress,!NativeNetworkBadgeControls.MASTER.equals(suppress));h.configure(off);
            Object[] restored=h.adjustText(savedRestart(styled),2,null);
            equal(original[0],restored[0]);equal(original[5],restored[5]);equal(original[2],restored[2]);
            equal(original[6],restored[6]);equal(original[7],restored[7]);equal(1,restored[11]);equal(0,restored[12]);
            Object[] unrelated=original.clone();unrelated[0]="";unrelated[5]="";unrelated[9]="";
            equal(unrelated,h.adjustText(unrelated,2,null));
            h.configure(settings);
        }
        settings.putBoolean(firstHidden,false);settings.putBoolean(secondHidden,false);h.configure(settings);
        Object[] changedNative=original.clone();changedNative[0]="LTE";changedNative[5]="+";
        Object[] changed=nativeContent(h,owner,changedNative,2);equal("LTE",changed[0]);equal("+",changed[5]);
        settings.putBoolean(firstHidden,true);h.configure(settings);changed=h.adjustText(savedRestart(changed),2,null);equal("",changed[0]);
        settings.putBoolean(NativeNetworkBadgeControls.MASTER,false);h.configure(settings);
        Object[] restored=h.adjustText(savedRestart(changed),2,null);equal("LTE",restored[0]);equal("+",restored[5]);
        Map<String,Object> saved=new HashMap<>();saved.put(firstHidden,true);
        equal(false,NativeNetworkBadgeSettings.inheritedDefaults(saved).containsKey(firstHidden));
        equal(false,NativeNetworkBadgeSettings.inheritedDefaults(saved).containsKey(secondHidden));
        h.releaseRuntime();equal(false,h.needsSpanAdjustment());
    }
    private static void optionalCapabilities()throws Exception{
        // OEM R8 removed drawContent from the interface but kept the concrete method.
        java.lang.reflect.Method draw=NativeNetworkBadgeControls.resolveDrawContent(NativeNetworkBadgeControlsCheck.class.getClassLoader());
        equal(androidx.compose.ui.node.LayoutNodeDrawScope.class,draw.getDeclaringClass());
        androidx.compose.ui.node.LayoutNodeDrawScope scope=new androidx.compose.ui.node.LayoutNodeDrawScope();draw.invoke(scope);equal(1,scope.draws);
        Native n=new Native();n.partsSupported=false;NativeNetworkBadgeControls h=new NativeNetworkBadgeControls(n);
        equal(false,h.setDrawRunsReady(true));equal(true,h.setTypographyReady(true));
        Bundle b=settings(true,0,0,100,400);b.putFloat(NativeNetworkBadgeSettings.key(1,"weight"),800f);
        b.putFloat(NativeNetworkBadgeSettings.key(1,"scale"),150f);b.putBoolean(NativeNetworkBadgeSettings.key(2,"hidden"),true);h.configure(b);
        Object owner=new Object();Object[] original=args(n,new Object(),new Modifier.Element());Object[] styled=nativeContent(h,owner,original,2);
        equal(18f,size(styled[6]));equal("",styled[5]);equal(0,n.drawings);equal(true,styled[2] instanceof Layer);
        Layer layer=(Layer)styled[2];equal(0f,layer.x);equal(0f,layer.y);
        try(NativeNetworkBadgeControls.TextScope text=h.enterText(styled,null)){
            equal(800,((Weight)h.adjustSpanStyle(span(styled,0))[2]).value);
        }
        b.putFloat(NativeNetworkBadgeSettings.key(1,"weight"),500f);h.configure(b);
        Object[] restyled=h.adjustText(savedRestart(styled),2,null);equal(false,styled[2]==restyled[2]);equal(18f,size(restyled[6]));equal(0,restyled[12]);
        h.setTypographyReady(false);styled=nativeContent(h,owner,original,2);
        // Even an unavailable SpanStyle hook cannot disable direct sizes/hidden settings.
        equal(18f,size(styled[6]));equal("",styled[5]);equal(false,h.needsSpanAdjustment());
        b.putBoolean(NativeNetworkBadgeControls.MASTER,false);h.configure(b);Object[] restored=h.adjustText(savedRestart(styled),2,null);
        equal("G",restored[5]);equal(original[6],restored[6]);h.releaseRuntime();
    }
    private static android.text.SpannableStringBuilder nativeText(String prefix,String suffix,int prefixSize,int suffixSize){
        android.text.SpannableStringBuilder text=new android.text.SpannableStringBuilder(prefix+suffix);
        if(!prefix.isEmpty())text.setSpan(new android.text.style.AbsoluteSizeSpan(prefixSize,false),0,prefix.length(),33);
        if(!suffix.isEmpty()){
            text.setSpan(new android.text.style.AbsoluteSizeSpan(suffixSize,false),prefix.length(),text.length(),33);
            text.setSpan(new android.text.style.MetricAffectingSpan(){
                public void updateDrawState(android.text.TextPaint paint){paint.baselineShift=-2;}
                public void updateMeasureState(android.text.TextPaint paint){updateDrawState(paint);}
            },prefix.length(),text.length(),33);
        }
        return text;
    }
    private static android.text.style.ReplacementSpan replacement(android.widget.TextView view,int run){
        android.text.Spanned text=(android.text.Spanned)view.getText();int start=run==0?0:2,end=run==0?2:text.length();
        android.text.style.ReplacementSpan[] spans=text.getSpans(start,end,android.text.style.ReplacementSpan.class);
        equal(1,spans.length);return spans[0];
    }
    private static android.text.TextPaint nativePaint(android.widget.TextView view,int run){
        android.text.TextPaint paint=new android.text.TextPaint();paint.set(view.getPaint());
        android.text.Spanned text=(android.text.Spanned)view.getText();int start=run==0?0:2,end=run==0?2:text.length();
        for(android.text.style.MetricAffectingSpan span:text.getSpans(start,end,android.text.style.MetricAffectingSpan.class))
            if(!(span instanceof android.text.style.ReplacementSpan))span.updateMeasureState(paint);
        return paint;
    }
    private static void drawNativeRun(android.widget.TextView view,int run,android.graphics.Canvas canvas){
        android.text.style.ReplacementSpan span=replacement(view,run);android.text.Spanned text=(android.text.Spanned)view.getText();
        span.draw(canvas,text,text.getSpanStart(span),text.getSpanEnd(span),10,0,20,30,nativePaint(view,run));
    }
    private static void nativeTextViews(){
        NativeNetworkBadgeControls h=new NativeNetworkBadgeControls();
        android.content.Context context=new android.content.Context();context.getResources().getDisplayMetrics().density=2f;
        android.widget.TextView primary=new android.widget.TextView(context),secondary=new android.widget.TextView(context);
        primary.getResources().getDisplayMetrics().density=2f;secondary.getResources().getDisplayMetrics().density=2f;
        primary.setTranslationX(4f);primary.setTranslationY(-3f);secondary.setTranslationX(-2f);secondary.setTranslationY(1f);
        android.graphics.Typeface nativeFace=new android.graphics.Typeface();nativeFace.variableWeight=true;nativeFace.weight=450;
        primary.setTypeface(nativeFace);secondary.setTypeface(nativeFace);
        primary.setText(nativeText("5G","+",12,9));secondary.setText(nativeText("5G","",12,9));
        Object nativePrefix=((android.text.Spanned)primary.getText()).getSpans(0,2,android.text.style.AbsoluteSizeSpan.class)[0];
        Bundle b=settings(true,0,0,100,400);b.putFloat(NativeNetworkBadgeSettings.key(1,"scale"),200f);
        b.putFloat(NativeNetworkBadgeSettings.key(1,"weight"),800f);b.putFloat(NativeNetworkBadgeSettings.key(1,"offset_x"),3f);
        b.putFloat(NativeNetworkBadgeSettings.key(1,"offset_y"),-1f);
        b.putFloat(NativeNetworkBadgeSettings.key(2,"scale"),50f);b.putFloat(NativeNetworkBadgeSettings.key(2,"weight"),600f);h.configure(b);
        h.onTextViewNativeUpdated(primary,"5G","+",1);h.onTextViewNativeUpdated(secondary,"5G","",2);
        equal("5G+",primary.getText().toString());equal(nativeFace,primary.getTypeface());equal(13f,primary.getTextSize());
        equal(nativePrefix,((android.text.Spanned)primary.getText()).getSpans(0,2,android.text.style.AbsoluteSizeSpan.class)[0]);
        android.graphics.Canvas canvas=new android.graphics.Canvas();drawNativeRun(primary,0,canvas);
        equal("5G",canvas.text);equal(24f,canvas.textSize);equal(800f,canvas.textEffectiveWeight);equal(10f,canvas.textX);equal(20f,canvas.textY);
        equal(10f,primary.getTranslationX());equal(-5f,primary.getTranslationY());
        drawNativeRun(primary,1,canvas);equal("+",canvas.text);equal(18f,canvas.textSize);equal(16f,canvas.textY);
        android.graphics.Paint.FontMetricsInt metrics=new android.graphics.Paint.FontMetricsInt();android.text.Spanned primaryText=(android.text.Spanned)primary.getText();
        android.text.style.ReplacementSpan suffix=replacement(primary,1);
        suffix.getSize(nativePaint(primary,1),primaryText,2,3,metrics);equal(-19,metrics.ascent);equal(0,metrics.descent);
        drawNativeRun(secondary,0,canvas);equal(6f,canvas.textSize);equal(600f,canvas.textEffectiveWeight);equal(10f,canvas.textX);
        CharSequence stable=primary.getText();
        for(int i=0;i<1000;i++){h.refreshTextViews();h.onTextViewNativeUpdated(primary,"5G","+",1);equal(stable,primary.getText());}
        // Both runs in a subscription row hide together; the secondary row stays independent.
        b.putBoolean(NativeNetworkBadgeSettings.key(1,"hidden"),true);h.configure(b);h.refreshTextViews();
        for(int run=0;run<2;run++){
            android.text.style.ReplacementSpan span=replacement(primary,run);android.text.Spanned text=(android.text.Spanned)primary.getText();
            equal(0,span.getSize(nativePaint(primary,run),text,text.getSpanStart(span),text.getSpanEnd(span),null));
            canvas.text="unchanged";drawNativeRun(primary,run,canvas);equal("unchanged",canvas.text);
        }
        drawNativeRun(secondary,0,canvas);equal("5G",canvas.text);equal(6f,canvas.textSize);
        b.putBoolean(NativeNetworkBadgeSettings.key(1,"enabled"),false);h.configure(b);h.refreshTextViews();
        equal(0,((android.text.Spanned)primary.getText()).getSpans(0,3,android.text.style.ReplacementSpan.class).length);
        equal(4f,primary.getTranslationX());equal(-3f,primary.getTranslationY());
        // Primary-data role remap uses the other row's whole style, never the '+' as SIM2.
        h.onTextViewNativeUpdated(primary,"5G","+",2);drawNativeRun(primary,0,canvas);equal(6f,canvas.textSize);
        drawNativeRun(primary,1,canvas);equal(4.5f,canvas.textSize);equal(19f,canvas.textY);
        for(String key:new String[]{NativeNetworkBadgeControls.MASTER,"label_enabled",StatusBarSettings.SAFE_MODE}){
            Bundle off=new Bundle(b);off.putBoolean(key,!NativeNetworkBadgeControls.MASTER.equals(key));h.configure(off);h.refreshTextViews();
            equal(0,((android.text.Spanned)primary.getText()).getSpans(0,3,android.text.style.ReplacementSpan.class).length);
            equal(nativeFace,primary.getTypeface());equal(nativePrefix,((android.text.Spanned)primary.getText()).getSpans(0,2,android.text.style.AbsoluteSizeSpan.class)[0]);
            h.configure(b);h.refreshTextViews();
        }
        h.beforeNativeTextViewUpdate(primary);
        equal(0,((android.text.Spanned)primary.getText()).getSpans(0,3,android.text.style.ReplacementSpan.class).length);
        primary.setText(nativeText("LTE","",16,9));h.onTextViewNativeUpdated(primary,"LTE","",2);
        drawNativeRun(primary,0,canvas);equal(8f,canvas.textSize);equal("LTE",canvas.text);
        // Unknown row/source and an independent custom label preserve the incoming native text.
        h.onTextViewNativeUpdated(primary,"LTE","",0);equal(0,((android.text.Spanned)primary.getText()).getSpans(0,3,android.text.style.ReplacementSpan.class).length);
        h.onTextViewNativeUpdated(secondary,"5G","",2);secondary.setText("custom label");h.refreshTextViews();equal("custom label",secondary.getText());
        h.onTextViewNativeUpdated(secondary,"5G","",2);equal("custom label",secondary.getText());
        secondary.setText(nativeText("5G","",12,9));h.onTextViewNativeUpdated(secondary,"5G","",2);h.releaseRuntime();
        equal(0,((android.text.Spanned)secondary.getText()).getSpans(0,2,android.text.style.ReplacementSpan.class).length);
        equal(nativeFace,secondary.getTypeface());equal(13f,secondary.getTextSize());
    }
    private static void nativeTextViewMovement(){
        NativeNetworkBadgeControls h=new NativeNetworkBadgeControls();android.content.Context context=new android.content.Context();
        android.view.ViewGroup parent=new android.view.ViewGroup(context),host=new android.view.ViewGroup(context);
        android.widget.TextView view=new android.widget.TextView(context);parent.addView(host);host.addView(view);
        view.getResources().getDisplayMetrics().density=2f;view.setText(nativeText("5G","+",12,9));
        view.setTranslationX(4f);view.setTranslationY(-3f);
        android.graphics.Rect clip=new android.graphics.Rect(1,2,90,70);parent.setClipBounds(clip);host.setClipToPadding(false);
        Bundle b=settings(true,0,0,100,400);b.putFloat(NativeNetworkBadgeSettings.key(1,"offset_x"),-1000f);
        b.putFloat(NativeNetworkBadgeSettings.key(1,"offset_y"),250f);h.configure(b);h.onTextViewNativeUpdated(view,"5G","+",1);
        equal(-1996f,view.getTranslationX());equal(497f,view.getTranslationY());
        equal(false,parent.getClipChildren());equal(false,parent.getClipToPadding());equal(null,parent.getClipBounds());equal(false,host.getClipChildren());
        android.graphics.Canvas canvas=new android.graphics.Canvas();drawNativeRun(view,0,canvas);
        equal(10f,canvas.textX);equal(20f,canvas.textY);drawNativeRun(view,1,canvas);equal(10f,canvas.textX);equal(18f,canvas.textY);
        // Plain refreshes and genuine native transactions never capture our translation.
        for(int i=0;i<1000;i++){
            h.refreshTextViews();equal(-1996f,view.getTranslationX());equal(497f,view.getTranslationY());
            h.beforeNativeTextViewUpdate(view);equal(4f,view.getTranslationX());equal(-3f,view.getTranslationY());
            equal(false,parent.getClipChildren()); // lease survives this native transaction
            h.onTextViewNativeUpdated(view,"5G","+",1);equal(-1996f,view.getTranslationX());equal(497f,view.getTranslationY());
        }
        // Another feature sharing an ancestor must survive our feature's shutdown.
        android.view.View sibling=new android.view.View(context);parent.addView(sibling);OverflowControls.shared().acquire(sibling,false);
        b.putBoolean(NativeNetworkBadgeControls.MASTER,false);h.configure(b);h.refreshTextViews();
        equal(4f,view.getTranslationX());equal(-3f,view.getTranslationY());equal(false,parent.getClipChildren());equal(true,host.getClipChildren());
        OverflowControls.shared().release(sibling);equal(true,parent.getClipChildren());equal(true,parent.getClipToPadding());
        equal(1,parent.getClipBounds().left);equal(90,parent.getClipBounds().right);equal(false,host.getClipToPadding());
        b.putBoolean(NativeNetworkBadgeControls.MASTER,true);h.configure(b);h.onTextViewNativeUpdated(view,"5G","+",1);
        // A foreign text owner keeps its content while our move and clipping lease restore.
        view.setText("custom label");h.refreshTextViews();equal("custom label",view.getText());
        equal(4f,view.getTranslationX());equal(-3f,view.getTranslationY());equal(true,parent.getClipChildren());
        view.setText(nativeText("5G","+",12,9));h.onTextViewNativeUpdated(view,"5G","+",1);
        h.onTextViewNativeUpdated(view,null,null,0);equal(4f,view.getTranslationX());equal(-3f,view.getTranslationY());equal(true,parent.getClipChildren());
        // New native positions and external writes take precedence over an old owned output.
        h.onTextViewNativeUpdated(view,"5G","+",1);h.beforeNativeTextViewUpdate(view);view.setTranslationX(7f);view.setTranslationY(-2f);
        h.onTextViewNativeUpdated(view,"5G","+",1);equal(-1993f,view.getTranslationX());equal(498f,view.getTranslationY());
        view.setTranslationX(33f);view.setTranslationY(44f);h.onTextViewNativeUpdated(view,null,null,0);
        equal(33f,view.getTranslationX());equal(44f,view.getTranslationY());equal(true,parent.getClipChildren());
        view.setTranslationX(7f);view.setTranslationY(-2f);h.onTextViewNativeUpdated(view,"5G","+",1);
        // Stored values are not constrained to slider ranges; nonfinite and overflow are guarded.
        b.putFloat(NativeNetworkBadgeSettings.key(1,"offset_x"),Float.MAX_VALUE);b.putFloat(NativeNetworkBadgeSettings.key(1,"offset_y"),Float.NaN);
        h.configure(b);h.refreshTextViews();equal(NumericPolicy.MAX_DRAW_PIXELS,view.getTranslationX());equal(-2f,view.getTranslationY());
        b.putFloat(NativeNetworkBadgeSettings.key(1,"offset_x"),0f);b.putFloat(NativeNetworkBadgeSettings.key(1,"offset_y"),Float.POSITIVE_INFINITY);
        h.configure(b);h.refreshTextViews();equal(7f,view.getTranslationX());equal(-2f,view.getTranslationY());equal(true,parent.getClipChildren());
        b.putFloat(NativeNetworkBadgeSettings.key(1,"offset_x"),-1000f);h.configure(b);h.refreshTextViews();equal(false,parent.getClipChildren());
        h.releaseRuntime();equal(7f,view.getTranslationX());equal(-2f,view.getTranslationY());equal(true,parent.getClipChildren());
    }
}
