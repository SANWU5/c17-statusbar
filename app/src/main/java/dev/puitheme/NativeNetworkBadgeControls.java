// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Styles the real NetworkTypeText content without changing its Compose slot contract. */
public final class NativeNetworkBadgeControls {
    public static final String MASTER="native_network_badge_enabled";
    public static final String X="native_network_badge_offset_x",Y="native_network_badge_offset_y";
    public static final String SCALE="native_network_badge_scale",WEIGHT="native_network_badge_weight",FONT="native_network_badge_font";
    public static Map<String,Boolean> booleanDefaults(){return NativeNetworkBadgeSettings.booleanDefaults();}
    public static Map<String,String> stringDefaults(){return NativeNetworkBadgeSettings.stringDefaults();}
    public static Map<String,Float> floatDefaults(){return NativeNetworkBadgeSettings.floatDefaults();}
    interface Access {
        boolean modifier(Object value);
        Object translate(Object base,float x,float y)throws ReflectiveOperationException;
        int weight(Object value)throws ReflectiveOperationException;
        Object weight(int value)throws ReflectiveOperationException;
        Object family(Context context,Object nativeFamily,String mode,String revision,int weight)throws ReflectiveOperationException;
        default boolean supportsParts(){return false;}
        default Object drawing(Object base,Object token)throws ReflectiveOperationException{throw new NoSuchMethodException("Native text drawing unavailable");}
    }
    private volatile Access access;
    private volatile Settings settings=new Settings(null);
    private volatile boolean released;
    private volatile boolean partsReady;
    private volatile boolean drawRunsReady;
    private final Map<TextView,ViewBinding> textViews=new WeakHashMap<>();
    // Values never strongly retain their weak native modifier/composer keys or a native View/VM.
    private final ReferenceQueue<Object> expiredOwners=new ReferenceQueue<>();
    private final Map<Owner,Map<Object,Map<Object,Binding>>> rows=new LinkedHashMap<>();
    private final Map<Object,Map<Object,WeakReference<Binding>>> nativeRows=new WeakHashMap<>();
    private final Map<Object,WeakReference<Binding>> outputs=new WeakHashMap<>();
    private final ThreadLocal<Object> content=new ThreadLocal<>();
    private final ThreadLocal<TextScope> text=new ThreadLocal<>();
    private final ThreadLocal<Binding> drawing=new ThreadLocal<>();
    private final ThreadLocal<Boolean> drawingRun=new ThreadLocal<>();
    private volatile Field contentOwner;
    public NativeNetworkBadgeControls(){ }
    NativeNetworkBadgeControls(Access access){this.access=access;}
    private static final class Style {
        final boolean enabled,hidden;final float x,y,scale;final int weight;final String font;
        Style(Bundle b,int part){
            String group=part==0?"native_network_badge":NativeNetworkBadgeSettings.group(part);
            enabled=part==0||b==null||b.getBoolean(group+"_enabled",true);
            hidden=part!=0&&b!=null&&b.getBoolean(group+"_hidden",false);
            x=partNumber(b,group+"_offset_x",0f);y=partNumber(b,group+"_offset_y",0f);
            scale=Math.max(0f,partNumber(b,group+"_scale",100f));
            weight=Math.round(Math.max(1f,Math.min(1000f,partNumber(b,group+"_weight",400f))));
            String key=group+"_font";Object value=b==null?null:b.get(key);
            if(!(value instanceof String)&&b!=null){String legacy=NativeNetworkBadgeSettings.legacyKey(key);if(legacy!=null)value=b.get(legacy);}
            String selected=value instanceof String?(String)value:"native";
            font="global".equals(selected)||"system".equals(selected)||"pingfang".equals(selected)||"custom".equals(selected)?selected:"native";
        }
        boolean same(Style other){return enabled==other.enabled&&hidden==other.hidden&&x==other.x&&y==other.y&&scale==other.scale&&weight==other.weight&&font.equals(other.font);}
    }
    private static final class Settings {
        final boolean enabled,explicitParts;final Style legacy;final Style[] parts;final String revision;
        Settings(Bundle b){
            enabled=b!=null&&b.getBoolean(MASTER,false)&&!b.getBoolean("label_enabled",false)&&!SafetyMode.enabled(b);
            legacy=new Style(b,0);parts=new Style[]{new Style(b,1),new Style(b,2)};
            boolean explicit=false;
            if(b!=null)for(String key:b.keySet())if(key.startsWith(NativeNetworkBadgeSettings.PART_ONE+"_")
                    ||key.startsWith(NativeNetworkBadgeSettings.PART_TWO+"_")){explicit=true;break;}
            explicitParts=explicit;
            // Include the selected global source in the independent family cache identity.
            // A switch system→bundled can otherwise retain a cached "global" family.
            revision=b==null?"":b.getString(StatusBarSettings.FONT_REVISION,"")+':'
                    +(b.getBoolean("font_enabled",false)?b.getString(StatusBarSettings.FONT_MODE,"system"):"system");
        }
        boolean same(Settings other){return enabled==other.enabled&&explicitParts==other.explicitParts&&legacy.same(other.legacy)&&parts[0].same(other.parts[0])
                &&parts[1].same(other.parts[1])&&revision.equals(other.revision);}
    }
    private static float partNumber(Bundle b,String key,float fallback){
        Object value=b==null?null:b.get(key);
        if(!(value instanceof Number)&&b!=null){String legacy=NativeNetworkBadgeSettings.legacyKey(key);if(legacy!=null)value=b.get(legacy);}
        if(!(value instanceof Number))return fallback;
        float result=((Number)value).floatValue();return Float.isFinite(result)?result:fallback;
    }
    public boolean configure(Bundle b){
        if(released)return false;Settings next=new Settings(b);if(settings.same(next))return false;settings=next;return true;
    }
    public boolean enabled(){return !released&&settings.enabled&&!ModuleLifecycle.removed();}
    private boolean active(){Settings configured=settings;return enabled()&&(!(partsReady||configured.explicitParts)||configured.parts[0].enabled||configured.parts[1].enabled);}
    /** Typography is independent of the optional platform drawing overloads. */
    public boolean setTypographyReady(boolean ready){if(!released)partsReady=ready&&access!=null;return partsReady;}
    public boolean setDrawRunsReady(boolean ready){if(!released)drawRunsReady=ready&&access!=null&&access.supportsParts();return drawRunsReady;}
    /** Compatibility for the original combined installation call. */
    public boolean setPartsReady(boolean ready){setTypographyReady(ready);setDrawRunsReady(ready);return partsReady&&drawRunsReady;}
    public synchronized void resolve(ClassLoader loader)throws ReflectiveOperationException{
        if(released)return;NativeAccess nativeAccess=new NativeAccess(loader);
        Field owner=loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconLayoutStrategyKt$$ExternalSyntheticLambda2").getField("f$0");
        contentOwner=owner;access=nativeAccess;
    }
    /** Exact NetworkTypeText content scope. It excludes roaming and other global Text renderers. */
    public Scope enterContent(Object lambda){
        Object owner=null;Field field=contentOwner;
        if(field!=null&&lambda!=null&&field.getDeclaringClass().isInstance(lambda))try{owner=field.get(lambda);}
        catch(ReflectiveOperationException|RuntimeException unsupported){ }
        return enterOwner(owner);
    }
    Scope enterOwner(Object owner){Object previous=content.get();if(owner==null)content.remove();else content.set(owner);return new Scope(previous);}
    public final class Scope implements AutoCloseable {
        private Object previous;private boolean closed;
        private Scope(Object previous){this.previous=previous;}
        @Override public void close(){if(closed)return;closed=true;if(previous==null)content.remove();else content.set(previous);previous=null;}
    }
    private static final class Binding {
        final WeakReference<?>[] original=new WeakReference<?>[3],applied=new WeakReference<?>[3];
        final boolean[] nullOriginal=new boolean[3],known=new boolean[3];
        final long[] units=new long[2],appliedUnits=new long[2];final WeakReference<Object> composer,owner;
        // FontFamily is immutable and contains no View/composer/VM. Preserve it until
        // this weak native row expires, so OFF can restore after a saved callback
        // has replaced the native family's last external reference.
        Object originalFamily;
        int weight;float appliedX,appliedY;
        String prefix="",suffix="",nativePrefix="",nativeSuffix="";float density=1f;
        boolean prefixHidden,suffixHidden;
        WeakReference<Object> drawingModifier;
        Settings drawingSettings;
        boolean drawingUsesRuns,drawingUsesTypography;
        Binding(Object composer,Object owner){this.composer=new WeakReference<>(composer);this.owner=new WeakReference<>(owner);}
    }
    private static final class Owner extends WeakReference<Object>{
        final int hash;Owner(Object owner,ReferenceQueue<Object> queue){super(owner,queue);hash=System.identityHashCode(owner);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other||other instanceof Owner&&get()!=null&&get()==((Owner)other).get();}
    }
    private static final int[] INDEX={2,3,4};
    private Binding binding(Object modifier,Object composer,boolean create){
        if(modifier==null||composer==null)return null;
        Object owner=content.get();Owner expired;while((expired=(Owner)expiredOwners.poll())!=null)rows.remove(expired);
        WeakReference<Binding> derived=outputs.get(modifier);Binding result=derived==null?null:derived.get();
        if(result!=null&&result.composer.get()==composer&&(owner==null||result.owner.get()==owner))return result;
        if(owner==null){
            Map<Object,WeakReference<Binding>> compositions=nativeRows.get(modifier);WeakReference<Binding> nativeRow=compositions==null?null:compositions.get(composer);
            return nativeRow==null?null:nativeRow.get();
        }
        Owner identity=new Owner(owner,null);Map<Object,Map<Object,Binding>> modifiers=rows.get(identity);
        if(modifiers==null&&create){modifiers=new WeakHashMap<>();rows.put(new Owner(owner,expiredOwners),modifiers);}
        if(modifiers==null)return null;
        Map<Object,Binding> compositions=modifiers.get(modifier);
        if(compositions==null&&create){compositions=new WeakHashMap<>();modifiers.put(modifier,compositions);}
        if(compositions==null)return null;result=compositions.get(composer);
        if(result==null&&create){
            result=new Binding(composer,owner);compositions.put(composer,result);
            Map<Object,WeakReference<Binding>> nativeCompositions=nativeRows.get(modifier);
            if(nativeCompositions==null){nativeCompositions=new WeakHashMap<>();nativeRows.put(modifier,nativeCompositions);}
            nativeCompositions.put(composer,new WeakReference<>(result));
        }
        return result;
    }
    /** Upper renderer stays native. Clearing dirty flags creates extra ungrouped changed()
     * slots before rememberComposableLambda; code59 then read a TextUnit from the lambda slot. */
    public Object[] adjust(Object[] nativeArgs,float density){return nativeArgs;}
    public synchronized boolean needsAdjustment(Object modifier,Object composer){
        return !released&&(content.get()!=null&&active()||binding(modifier,composer,false)!=null);
    }
    /** Exact 13-argument StackedMobileText-wW3Lq_U: sizes are Long, not boxed TextUnits.
     * NetworkTypeText content passes changed=0/default=0. Keep its slot and default paths
     * unchanged on every call, including ON/OFF and the lower renderer's saved restarts. */
    public synchronized Object[] adjustText(Object[] nativeArgs,float density,Context context){
        if(released||access==null||nativeArgs==null||nativeArgs.length!=13
                ||!(nativeArgs[6] instanceof Long)||!(nativeArgs[7] instanceof Long)
                ||!(nativeArgs[11] instanceof Integer)||!(nativeArgs[12] instanceof Integer)
                ||((Integer)nativeArgs[12])!=0||nativeArgs[2]==null)return nativeArgs;
        boolean scoped=content.get()!=null;Binding state=binding(nativeArgs[2],nativeArgs[10],scoped&&active());
        if(state==null||!access.modifier(nativeArgs[2]))return nativeArgs;
        if(!scoped&&!matchesText(state,nativeArgs))return nativeArgs;
        try {
            Object[] base=nativeArgs;
            // Fresh content owns its new native baseline. Saved lower callbacks first restore
            // our previous outputs, even after OFF, safe mode or a custom-label change.
            if(!scoped){
                if(state.prefixHidden&&state.prefix.equals(nativeArgs[0])){
                    base=nativeArgs.clone();base[0]=state.nativePrefix;
                }
                if(state.suffixHidden&&state.suffix.equals(nativeArgs[5])){
                    if(base==nativeArgs)base=nativeArgs.clone();base[5]=state.nativeSuffix;
                }
                for(int i=0;i<INDEX.length;i++){
                    Object previous=state.applied[i]==null?null:state.applied[i].get();
                    if(!state.known[i]||previous==null||previous!=nativeArgs[INDEX[i]])continue;
                    Object original=i==1?state.originalFamily:state.original[i]==null?null:state.original[i].get();
                    if(original==null&&!state.nullOriginal[i]){if(i==2)original=access.weight(state.weight);else return nativeArgs;}
                    if(base==nativeArgs)base=nativeArgs.clone();base[INDEX[i]]=original;
                }
                for(int i=0;i<2;i++)if((Long)nativeArgs[6+i]==state.appliedUnits[i]&&state.appliedUnits[i]!=state.units[i]){
                    if(base==nativeArgs)base=nativeArgs.clone();base[6+i]=state.units[i];
                }
            }
            if(!enabled())return base;if(!Float.isFinite(density)||density<=0f)density=1f;
            Settings configured=settings;Style current=configured.legacy;
            boolean separate=(partsReady||configured.explicitParts)&&base[0] instanceof String&&base[5] instanceof String
                    &&((String)base[0]).length()+((String)base[5]).length()<=128;
            float dx=NumericPolicy.pixels(current.x,density),dy=NumericPolicy.pixels(current.y,density);
            Object[] result=base.clone();
            if(base[0] instanceof String&&base[5] instanceof String){state.nativePrefix=(String)base[0];state.nativeSuffix=(String)base[5];}
            if(separate){
                state.density=density;
                Object old=state.drawingModifier==null?null:state.drawingModifier.get();
                Object original=state.original[0]==null?null:state.original[0].get();
                if(configured.parts[0].enabled||configured.parts[1].enabled){
                    // A font/weight-only change must give the real lower native group a
                    // changed Modifier argument, otherwise its compiler can skip the spans.
                    result[2]=old!=null&&original==base[2]&&state.drawingSettings==configured
                            &&state.drawingUsesRuns==drawRunsReady&&state.drawingUsesTypography==partsReady?old:
                            drawRunsReady?access.drawing(base[2],state):access.translate(base[2],0f,0f);
                    state.drawingModifier=new WeakReference<>(result[2]);
                    state.drawingSettings=configured;
                    state.drawingUsesRuns=drawRunsReady;state.drawingUsesTypography=partsReady;
                }
            }else if(dx!=0f||dy!=0f){
                Object old=state.applied[0]==null?null:state.applied[0].get();Object original=state.original[0]==null?null:state.original[0].get();
                result[2]=old!=null&&original==base[2]&&state.appliedX==dx&&state.appliedY==dy?old:access.translate(base[2],dx,dy);
            }
            if(!separate){result[3]=access.family(context,base[3],current.font,configured.revision,current.weight);result[4]=access.weight(current.weight);}
            for(int i=0;i<2;i++){
                state.units[i]=(Long)base[6+i];Style part=separate?configured.parts[i]:current;
                result[6+i]=scaleUnit(state.units[i],part.enabled?part.scale:100f);state.appliedUnits[i]=(Long)result[6+i];
            }
            // Keep both native SpanStyle constructors and the single Text node. Empty
            // content removes only this part's glyph/advance, without extra Compose slots.
            state.prefixHidden=separate&&configured.parts[0].enabled&&configured.parts[0].hidden;
            state.suffixHidden=separate&&configured.parts[1].enabled&&configured.parts[1].hidden;
            if(state.prefixHidden)result[0]="";if(state.suffixHidden)result[5]="";
            if(result[0] instanceof String&&result[5] instanceof String){state.prefix=(String)result[0];state.suffix=(String)result[5];}
            state.weight=base[4]==null?700:access.weight(base[4]);boolean changed=!result[6].equals(base[6])||!result[7].equals(base[7])
                    ||!java.util.Objects.equals(result[0],base[0])||!java.util.Objects.equals(result[5],base[5]);
            for(int i=0;i<INDEX.length;i++){
                state.known[i]=true;state.nullOriginal[i]=base[INDEX[i]]==null;
                if(i==1)state.originalFamily=base[INDEX[i]];
                if(state.original[i]==null||state.original[i].get()!=base[INDEX[i]])state.original[i]=new WeakReference<>(base[INDEX[i]]);
                if(state.applied[i]==null||state.applied[i].get()!=result[INDEX[i]])state.applied[i]=new WeakReference<>(result[INDEX[i]]);
                changed|=result[INDEX[i]]!=base[INDEX[i]];
            }
            state.appliedX=separate?0f:dx;state.appliedY=separate?0f:dy;if(result[2]!=base[2])outputs.put(result[2],new WeakReference<>(state));return changed?result:base;
        }catch(ReflectiveOperationException|RuntimeException unsupported){return nativeArgs;}
    }
    private boolean matchesText(Binding state,Object[] args){
        if(state.nativePrefix.equals(args[0])&&state.nativeSuffix.equals(args[5]))return true;
        // Empty native text on another row may share Modifier+Composer. Only a
        // modifier we actually returned can identify an erased saved callback.
        WeakReference<Binding> output=outputs.get(args[2]);
        return output!=null&&output.get()==state&&state.prefix.equals(args[0])&&state.suffix.equals(args[5]);
    }
    /** Lower native restarts also enter this scope; no extra Composer slots or nodes are emitted. */
    public TextScope enterText(Object[] args,Context context){
        TextScope previous=text.get();Binding state=null;
        if(partsReady&&!released&&args!=null&&args.length==13&&args[0] instanceof String&&args[5] instanceof String){
            synchronized(this){state=binding(args[2],args[10],false);}
            if(state!=null&&(!state.prefix.equals(args[0])||!state.suffix.equals(args[5])))state=null;
        }
        TextScope scope=new TextScope(previous,state,args,context);text.set(scope);return scope;
    }
    public final class TextScope implements AutoCloseable{
        private TextScope previous;final Binding state;final Object family;final Context context;int part;boolean closed;
        TextScope(TextScope previous,Binding state,Object[] args,Context context){this.previous=previous;this.state=state;this.context=context;family=state==null?null:args[3];}
        @Override public void close(){if(closed)return;closed=true;if(previous==null)text.remove();else text.set(previous);previous=null;}
    }
    public boolean needsSpanAdjustment(){
        TextScope scope=text.get();Settings configured=settings;
        return partsReady&&enabled()&&scope!=null&&scope.state!=null&&scope.part<2
                &&(configured.parts[0].enabled||configured.parts[1].enabled);
    }
    /** Exact synthetic 15-parameter native constructor; all other span creation stays native. */
    public synchronized Object[] adjustSpanStyle(Object[] args){
        TextScope scope=text.get();
        if(scope==null||scope.state==null||scope.part>=2||args==null||args.length!=15
                ||!(args[1] instanceof Long)||!(args[14] instanceof Integer))return args;
        int part=scope.part,mask=(Integer)args[14];
        if(mask!=(part==0?0xfffd:0xfefd))return args;
        scope.part++;
        if(!enabled())return args;Settings configured=settings;Style style=configured.parts[part];if(!style.enabled)return args;
        try{
            Object[] result=args.clone();result[2]=access.weight(style.weight);
            result[5]=access.family(scope.context,scope.family,style.font,configured.revision,style.weight);
            // Only the span's two typography default bits change; Compose changed/default parameters stay intact.
            result[14]=mask&~(4|32);return result;
        }catch(ReflectiveOperationException|RuntimeException unavailable){return args;}
    }
    public DrawScope enterDrawing(Object token){
        Binding previous=drawing.get();Binding state=token instanceof Binding?(Binding)token:null;
        if(state==null)drawing.remove();else drawing.set(state);return new DrawScope(previous);
    }
    public final class DrawScope implements AutoCloseable{
        private Binding previous;boolean closed;
        DrawScope(Binding previous){this.previous=previous;}
        @Override public void close(){if(closed)return;closed=true;if(previous==null)drawing.remove();else drawing.set(previous);previous=null;}
    }
    public boolean needsRunAdjustment(){
        Settings configured=settings;
        return drawRunsReady&&enabled()&&drawing.get()!=null&&!Boolean.TRUE.equals(drawingRun.get())
                &&(configured.parts[0].enabled&&(configured.parts[0].x!=0f||configured.parts[0].y!=0f)
                ||configured.parts[1].enabled&&(configured.parts[1].x!=0f||configured.parts[1].y!=0f));
    }
    public RunScope enterRun(){boolean previous=Boolean.TRUE.equals(drawingRun.get());drawingRun.set(true);return new RunScope(previous,previous?null:drawing.get());}
    public final class RunScope implements AutoCloseable{
        final boolean previous;final Binding state;boolean closed;
        RunScope(boolean previous,Binding state){this.previous=previous;this.state=state;}
        public Object[] adjust(Object[] args,boolean chars){
            if(state==null||!drawRunsReady||!enabled()||args==null||args.length!=9||!(args[1] instanceof Integer)||!(args[2] instanceof Integer)
                    ||!(args[3] instanceof Integer)||!(args[4] instanceof Integer)||!(args[5] instanceof Float)||!(args[6] instanceof Float))return args;
            int length=state.prefix.length()+state.suffix.length(),start=(Integer)args[1],end=chars?start+(Integer)args[2]:(Integer)args[2];
            int contextStart=0;
            if(length==0||end<=start)return args;
            if(chars){
                if(!(args[0] instanceof char[]))return args;
                char[] value=(char[])args[0];
                if(!matches(state,value,0,length)){
                    contextStart=(Integer)args[3];if(!matches(state,value,contextStart,length))return args;
                }
            }else{
                if(!(args[0] instanceof CharSequence)||((CharSequence)args[0]).length()!=length)return args;
                CharSequence value=(CharSequence)args[0];for(int i=0;i<length;i++)if(value.charAt(i)!=character(state,i))return args;
            }
            if(start<contextStart||end>contextStart+length)return args;
            Settings configured=settings;int boundary=contextStart+state.prefix.length();
            Style first=configured.parts[0],second=configured.parts[1];
            float x,y;
            if(end<=boundary){x=first.enabled?first.x:0f;y=first.enabled?first.y:0f;}
            else if(start>=boundary){x=second.enabled?second.x:0f;y=second.enabled?second.y:0f;}
            else{
                // An unexpected unsplit platform run may move as a whole only when both offsets agree.
                float secondX=second.enabled?second.x:0f,secondY=second.enabled?second.y:0f;
                x=first.enabled?first.x:0f;y=first.enabled?first.y:0f;if(x!=secondX||y!=secondY)return args;
            }
            if(x==0f&&y==0f)return args;Object[] result=args.clone();
            result[5]=(Float)args[5]+NumericPolicy.pixels(x,state.density);result[6]=(Float)args[6]+NumericPolicy.pixels(y,state.density);return result;
        }
        @Override public void close(){if(closed)return;closed=true;if(previous)drawingRun.set(true);else drawingRun.remove();}
    }
    private static char character(Binding state,int index){return index<state.prefix.length()?state.prefix.charAt(index):state.suffix.charAt(index-state.prefix.length());}
    private static boolean matches(Binding state,char[] value,int start,int length){
        if(start<0||start+length>value.length)return false;
        for(int i=0;i<length;i++)if(value[start+i]!=character(state,i))return false;return true;
    }
    static long scaleUnit(long nativeValue,float percent){
        float value=Float.intBitsToFloat((int)nativeValue);long type=nativeValue&0xffffffff00000000L;
        if(type==0L||!Float.isFinite(value)||value<0f||!Float.isFinite(percent)||percent<0f)return nativeValue;
        float size=NumericPolicy.textPixels((double)value*percent/100d);return type|(Float.floatToRawIntBits(size)&0xffffffffL);
    }
    /** Restore our spans and row translation before the OEM updates its native baseline. */
    public synchronized void beforeNativeTextViewUpdate(TextView view){
        ViewBinding state=view==null?null:textViews.get(view);if(state!=null)restoreTextView(view,state,false);
    }
    /** OS17 independent rows have a real subscription. part=1 means active-data row,
     * part=2 means its secondary row; prefix/suffix belong to the same RAT. Unknown
     * subscription mappings must pass 0 and preserve native rendering. */
    public synchronized void onTextViewNativeUpdated(TextView view,String prefix,String suffix,int part){
        if(released||view==null)return;
        ViewBinding state=textViews.get(view);CharSequence current=view.getText();
        if(part<1||part>2||current==null||prefix==null||suffix==null||prefix.length()+suffix.length()>128
                ||!contentEquals(current,prefix+suffix)){
            if(state!=null){restoreTextView(view,state);textViews.remove(view);}return;
        }
        if(state!=null&&ownsText(view,state)){
            // Duplicate native callbacks/configuration remaps never capture our output.
            if(state.part!=part){state.part=part;state.appliedSettings=null;}
            applyTextView(view,state);return;
        }
        if(state==null){state=new ViewBinding();textViews.put(view,state);}
        // A fresh OEM text can arrive without a style write. Remove an owned move
        // first so its last output cannot become the new native baseline.
        restoreTranslation(view,state);
        state.nativeText=new SpannableStringBuilder(current);state.prefix=prefix;state.suffix=suffix;state.part=part;
        state.face=view.getTypeface();state.density=view.getResources().getDisplayMetrics().density;
        state.nativeX=NumericPolicy.finite(view.getTranslationX(),0f);state.nativeY=NumericPolicy.finite(view.getTranslationY(),0f);
        if(!Float.isFinite(state.density)||state.density<=0f)state.density=1f;
        state.applied=null;state.appliedSettings=null;applyTextView(view,state);
    }
    /** Configuration/font events only, on the same UI thread as native TextView binds. */
    public synchronized void refreshTextViews(){
        if(released)return;
        for(Map.Entry<TextView,ViewBinding> row:new ArrayList<>(textViews.entrySet()))applyTextView(row.getKey(),row.getValue());
    }
    private static final class ViewBinding{
        CharSequence nativeText,applied;String prefix="",suffix="";Typeface face;float density=1f;int part;
        float nativeX,nativeY,appliedX,appliedY;boolean ownsX,ownsY;
        boolean overflowOwned;
        Settings appliedSettings;long fontRevision=-1;
    }
    private static boolean contentEquals(CharSequence value,String expected){
        if(value.length()!=expected.length())return false;
        for(int i=0;i<expected.length();i++)if(value.charAt(i)!=expected.charAt(i))return false;return true;
    }
    private static boolean ownsText(TextView view,ViewBinding state){return state.applied!=null&&view.getText()==state.applied;}
    private static void restoreTextView(TextView view,ViewBinding state){
        restoreTextView(view,state,true);
    }
    private static void restoreTextView(TextView view,ViewBinding state,boolean releaseOverflow){
        if(ownsText(view,state)&&state.nativeText!=null)view.setText(state.nativeText);
        restoreTranslation(view,state);
        if(releaseOverflow&&state.overflowOwned){OverflowControls.shared().release(view);state.overflowOwned=false;}
        state.applied=null;state.appliedSettings=null;
    }
    private static void restoreTranslation(TextView view,ViewBinding state){
        // Preserve an external/native position write that superseded our output.
        if(state.ownsX&&view.getTranslationX()==state.appliedX)view.setTranslationX(state.nativeX);
        if(state.ownsY&&view.getTranslationY()==state.appliedY)view.setTranslationY(state.nativeY);
        state.ownsX=false;state.ownsY=false;
    }
    private static void applyTranslation(TextView view,ViewBinding state,Style style){
        float currentX=view.getTranslationX(),currentY=view.getTranslationY();
        if(currentX!=(state.ownsX?state.appliedX:state.nativeX))state.nativeX=NumericPolicy.finite(currentX,0f);
        if(currentY!=(state.ownsY?state.appliedY:state.nativeY))state.nativeY=NumericPolicy.finite(currentY,0f);
        float x=NumericPolicy.drawPixels((double)state.nativeX+NumericPolicy.pixels(style.x,state.density));
        float y=NumericPolicy.drawPixels((double)state.nativeY+NumericPolicy.pixels(style.y,state.density));
        if(currentX!=x)view.setTranslationX(x);if(currentY!=y)view.setTranslationY(y);
        state.appliedX=x;state.appliedY=y;state.ownsX=x!=state.nativeX;state.ownsY=y!=state.nativeY;
    }
    private void applyTextView(TextView view,ViewBinding state){
        if(view==null||state.nativeText==null)return;
        if(!enabled()||SingleNetworkLabelControls.managed(view)){
            restoreTextView(view,state);return;
        }
        // A different label owner may have replaced the native text between events.
        CharSequence current=view.getText();
        if(!ownsText(view,state)&&(current==null||!contentEquals(current,state.prefix+state.suffix))){
            restoreTextView(view,state);return;
        }
        Settings configured=settings;Style style=configured.parts[state.part-1];long revision=FontRepository.styleRevision();
        if(!style.enabled){restoreTextView(view,state);return;}
        // TextView clips Layout drawing to its own bounds. Move the entire row's
        // transform instead of offsetting glyphs inside that fixed local clip.
        applyTranslation(view,state,style);
        if(!style.hidden&&(style.x!=0f||style.y!=0f)){
            // This leaf is our owner; siblings/features retain their own ancestor leases.
            // Native/configuration events refresh at most the ancestor chain, never draw.
            OverflowControls.shared().acquire(view,false);state.overflowOwned=true;
        }else if(state.overflowOwned){OverflowControls.shared().release(view);state.overflowOwned=false;}
        if(ownsText(view,state)&&state.appliedSettings==configured&&state.fontRevision==revision)return;
        SpannableStringBuilder styled=new SpannableStringBuilder(state.nativeText);
        for(BadgePartSpan span:styled.getSpans(0,styled.length(),BadgePartSpan.class))styled.removeSpan(span);
        if(styled.length()==0){restoreTextView(view,state);return;}
        try{
            Typeface selected=null;
            if(!style.hidden){
                selected="native".equals(style.font)?FontWeight.typeface(state.face,style.weight)
                        :FontRepository.typefaceForMode(view.getContext(),style.font,state.face,style.weight);
                selected=NativeNetworkBadgeFont.axisFace(selected,FontRepository.weightForMode(style.font,style.weight));
            }
            // Preserve native relative sizes and suffix baseline. Both runs receive
            // the one real subscription row's style, including the '+' suffix.
            int boundary=state.prefix.length();
            if(boundary>0)styled.setSpan(new BadgePartSpan(configured,style,selected),0,boundary,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if(boundary<styled.length())styled.setSpan(new BadgePartSpan(configured,style,selected),boundary,styled.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            view.setText(styled);state.applied=view.getText();state.appliedSettings=configured;state.fontRevision=revision;
        }catch(RuntimeException unsupported){restoreTextView(view,state);}
    }
    /** Native AbsoluteSizeSpan/BaselineShiftRatioSpan already modify this Paint.
     * Font preparation happens at bind/configure time, never during drawing. */
    private final class BadgePartSpan extends ReplacementSpan{
        final Settings configured;final Style style;final Typeface face;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        BadgePartSpan(Settings configured,Style style,Typeface face){
            this.configured=configured;this.style=style;this.face=face;
        }
        private boolean active(){return enabled()&&settings==configured;}
        private Paint paint(Paint nativePaint,boolean active){
            paint.set(nativePaint);
            if(active){
                paint.setTextSize(NumericPolicy.textPixels((double)nativePaint.getTextSize()*style.scale/100d));
                if(paint.getFontVariationSettings()!=null)paint.setFontVariationSettings(null);
                if(face!=null)paint.setTypeface(face);
            }
            return paint;
        }
        private int baselineShift(Paint nativePaint,boolean active){
            int shift=nativePaint instanceof TextPaint?((TextPaint)nativePaint).baselineShift:0;
            return active?Math.round(shift*style.scale/100f):shift;
        }
        @Override public int getSize(Paint nativePaint,CharSequence value,int start,int end,Paint.FontMetricsInt metrics){
            boolean active=active();if(active&&style.hidden)return 0;
            Paint styled=paint(nativePaint,active);if(metrics!=null)styled.getFontMetricsInt(metrics);
            if(metrics!=null){int shift=baselineShift(nativePaint,active);metrics.top+=shift;metrics.ascent+=shift;metrics.descent+=shift;metrics.bottom+=shift;}
            return (int)Math.ceil(styled.measureText(value,start,end));
        }
        @Override public void draw(Canvas canvas,CharSequence value,int start,int end,float left,int top,int baseline,int bottom,Paint nativePaint){
            boolean active=active();if(active&&style.hidden)return;
            canvas.drawText(value,start,end,left,baseline+baselineShift(nativePaint,active),paint(nativePaint,active));
        }
    }
    public synchronized void releaseRuntime(){
        for(Map.Entry<TextView,ViewBinding> row:new ArrayList<>(textViews.entrySet()))restoreTextView(row.getKey(),row.getValue());
        textViews.clear();released=true;rows.clear();nativeRows.clear();outputs.clear();while(expiredOwners.poll()!=null){ }
        content.remove();text.remove();drawing.remove();drawingRun.remove();contentOwner=null;access=null;settings=new Settings(null);partsReady=false;drawRunsReady=false;
    }
    static Method resolveDrawContent(ClassLoader loader)throws ReflectiveOperationException{
        try{return loader.loadClass("androidx.compose.ui.graphics.drawscope.ContentDrawScope").getMethod("drawContent");}
        catch(NoSuchMethodException stripped){
            // The actual OEM R8 output moves this method off the interface. Native
            // PainterNode and drawing lambdas invoke this exact concrete method.
            return loader.loadClass("androidx.compose.ui.node.LayoutNodeDrawScope").getMethod("drawContent");
        }
    }
    private final class NativeAccess implements Access {
        final Class<?> modifier,function,loadedFamily,wrapper;final Object kotlinUnit;
        final Method layer,setX,setY,drawWithContent,drawContent,familyFace,wrapperFace,wrapFamily;final Field weightValue;final Constructor<?> weightConstructor;
        final Map<Integer,Object> weights=new LinkedHashMap<>();final Map<Object,Map<String,Object>> families=new WeakHashMap<>();
        NativeAccess(ClassLoader loader)throws ReflectiveOperationException{
            modifier=loader.loadClass("androidx.compose.ui.Modifier");function=loader.loadClass("kotlin.jvm.functions.Function1");
            kotlinUnit=loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null);
            layer=loader.loadClass("androidx.compose.ui.graphics.GraphicsLayerModifierKt").getMethod("graphicsLayer",modifier,function);
            Method draw=null,content=null;
            try{
                draw=loader.loadClass("androidx.compose.ui.draw.DrawModifierKt").getMethod("drawWithContent",modifier,function);
                content=resolveDrawContent(loader);
            }catch(ReflectiveOperationException unavailable){ }
            drawWithContent=draw;drawContent=content;
            Class<?> scope=loader.loadClass("androidx.compose.ui.graphics.GraphicsLayerScope");setX=scope.getMethod("setTranslationX",Float.TYPE);setY=scope.getMethod("setTranslationY",Float.TYPE);
            Class<?> weight=loader.loadClass("androidx.compose.ui.text.font.FontWeight");weightConstructor=weight.getConstructor(Integer.TYPE);weightValue=weight.getField("weight");
            loadedFamily=loader.loadClass("androidx.compose.ui.text.font.LoadedFontFamily");familyFace=loadedFamily.getMethod("getTypeface");
            wrapper=loader.loadClass("androidx.compose.ui.text.platform.AndroidTypefaceWrapper");wrapperFace=wrapper.getMethod("getTypeface");
            wrapFamily=loader.loadClass("androidx.compose.ui.text.font.AndroidTypeface_androidKt").getMethod("FontFamily",Typeface.class);
        }
        @Override public boolean modifier(Object value){return modifier.isInstance(value);}
        @Override public boolean supportsParts(){return drawWithContent!=null&&drawContent!=null;}
        @Override public Object translate(Object base,float x,float y)throws ReflectiveOperationException{
            Object action=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(proxy,method,args)->{
                switch(method.getName()){
                    case "invoke":setX.invoke(args[0],enabled()?x:0f);setY.invoke(args[0],enabled()?y:0f);return kotlinUnit;
                    case "hashCode":return System.identityHashCode(proxy);case "equals":return proxy==args[0];default:return "NativeNetworkBadgePlacement";
                }
            });return layer.invoke(null,base,action);
        }
        @Override public Object drawing(Object base,Object token)throws ReflectiveOperationException{
            if(!supportsParts())throw new NoSuchMethodException("Native text drawing unavailable");
            Object action=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(proxy,method,args)->{
                switch(method.getName()){
                    case "invoke":try(DrawScope ignored=enterDrawing(token)){drawContent.invoke(args[0]);}return kotlinUnit;
                    case "hashCode":return System.identityHashCode(proxy);case "equals":return proxy==args[0];default:return "NativeNetworkBadgeParts";
                }
            });return drawWithContent.invoke(null,base,action);
        }
        @Override public int weight(Object value)throws ReflectiveOperationException{return weightValue.getInt(value);}
        @Override public Object weight(int value)throws ReflectiveOperationException{
            Object result=weights.get(value);if(result==null){result=weightConstructor.newInstance(value);if(weights.size()>=16)weights.clear();weights.put(value,result);}return result;
        }
        @Override public Object family(Context context,Object nativeFamily,String mode,String revision,int requested)throws ReflectiveOperationException{
            // OEM AndroidTypefaceWrapper ignores FontWeight and returns a pre-varied Typeface.
            // Rebuild the real wght instance before wrapping, rather than changing metadata.
            Object cacheOwner=nativeFamily==null?modifier:nativeFamily;String key=mode+':'+revision+':'+requested;
            Map<String,Object> cache=families.get(cacheOwner);if(cache!=null&&cache.containsKey(key))return cache.get(key);
            Typeface nativeFace=null;if(loadedFamily.isInstance(nativeFamily)){
                Object loaded=familyFace.invoke(nativeFamily);if(wrapper.isInstance(loaded))nativeFace=(Typeface)wrapperFace.invoke(loaded);
            }
            Typeface selected="native".equals(mode)?FontWeight.typeface(nativeFace,requested):FontRepository.typefaceForMode(context,mode,nativeFace,requested);
            int actual=FontRepository.weightForMode(mode,requested);
            selected=NativeNetworkBadgeFont.axisFace(selected,actual);
            Object result=wrapFamily.invoke(null,selected);
            if(cache==null){if(families.size()>=32)families.clear();cache=new LinkedHashMap<>();families.put(cacheOwner,cache);}
            if(cache.size()>=48)cache.clear();cache.put(key,result);return result;
        }
    }
}
