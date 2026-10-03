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
    private static final class Native implements NativeNetworkBadgeControls.Access {
        final Map<Integer,Weight> weights=new HashMap<>();final Map<String,Family> families=new HashMap<>();
        int translations,createdWeights,createdFamilies;boolean fail;
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
        System.out.println("NativeNetworkBadgeControls checks passed: "+checks);
    }
}
