// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.drawable.Drawable;
import android.graphics.BlendModeColorFilter;
import android.graphics.ColorFilter;
import android.graphics.PorterDuffColorFilter;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Audited non-Tile glyph hosts. Never searches ImageViews or identifies an action by its text. */
final class IconNativeControlCenter {
    static final String BRIGHTNESS="com.oplus.systemui.qs.base.seek.ClipBrightnessView";
    static final String DETAIL_BRIGHTNESS="com.oplus.systemui.qs.slider.OplusQsDetailHookClipBrightnessView";
    static final String VOLUME="com.oplus.systemui.qs.base.seek.OplusQsVolumeIconView";
    static final String VOLUME_STATE="com.android.systemui.plugins.qs.QSVolume$VolumeState";
    private static final String LOTTIE="com.airbnb.lottie.LottieDrawable";
    private static final String VOLUME_COLORS="com.oplus.systemui.qs.base.tile.QSLottieAnimationView$ColorfulConfig";
    static final String MEDIA="com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView";
    static final String BUTTON="com.oplus.systemui.qs.media.OplusLottieButton";
    static final String PLAY_PAUSE="com.oplus.systemui.qs.media.OplusQsPlayPauseButton";
    static final String MEDIA_ACTION="com.android.systemui.media.controls.shared.model.MediaAction";
    static final String MEDIA_ACTION_EX="com.android.systemui.media.controls.models.player.OplusMediaActionEx";
    static final String MEDIA_DATA="com.android.systemui.media.controls.shared.model.MediaData";
    static final String FOOTER="com.oplus.systemui.qs.OplusQSFooterImpl";
    static final String SDK="com.oplus.deviceplugin.sdk.";
    static final String SQUARE=SDK+"ui.view.separatecardview.SquareDeviceCardView";
    static final String RECTANGLE=SDK+"ui.view.separatecardview.RectangleDeviceCardView";
    static final String SQUARE_ENTRANCE=SDK+"ui.view.separatecardview.SquareEntranceCardView";
    static final String RECTANGLE_ENTRANCE=SDK+"ui.view.separatecardview.RectangleEntranceCardView";
    static final String PROGRESS=SDK+"ui.view.progressbar.BatteryCircularProgressLayout";

    interface Graphics {
        Drawable wrap(View owner,String role,Drawable nativeGraphic);
        Drawable unwrap(Drawable graphic);
        void request(View owner,String role,int pixels);
        boolean hasAppearance(String role);
        void nativeTint(Drawable wrapper,int argb);
    }
    private static final Graphics RUNTIME=new Graphics(){
        public Drawable wrap(View owner,String role,Drawable graphic){return IconManagementRuntime.wrap(owner,role,graphic);}
        public Drawable unwrap(Drawable graphic){return IconManagementRuntime.unwrap(graphic);}
        public void request(View owner,String role,int pixels){IconManagementRuntime.request(owner,role,pixels);}
        public boolean hasAppearance(String role){return IconManagementRuntime.hasAppearance(role);}
        public void nativeTint(Drawable wrapper,int argb){IconManagementRuntime.nativeTint(wrapper,argb);}
    };
    private final Graphics graphics;
    private final Map<View,Binding> bindings=new WeakHashMap<>();
    private final Map<View,Overlay> overlays=new WeakHashMap<>();
    private final Map<View,Boolean> observed=new WeakHashMap<>(),standardPlay=new WeakHashMap<>();
    private final Map<View,Boolean> brightnessAuto=new WeakHashMap<>();
    private final Map<View,Integer> deviceResources=new WeakHashMap<>();
    private final Map<View,AnimationWait> waits=new WeakHashMap<>();
    private final Map<View,Runnable> completions=new WeakHashMap<>();
    private final Map<ColorFilter,Long> filterColors=new WeakHashMap<>();
    private final Map<Class<?>,Members> reflection=new HashMap<>();
    private boolean enabled,released;
    IconNativeControlCenter(){this(RUNTIME);}
    IconNativeControlCenter(Graphics graphics){this.graphics=graphics;}

    private static final class Members {
        final Map<String,Field> fields=new HashMap<>();
        final Map<String,Method> methods=new HashMap<>();
    }
    private static final class Binding {
        final WeakReference<View> owner,host,parent;
        final String slot,field;
        String role;
        WeakReference<Drawable> applied=new WeakReference<>(null);
        int requestedPixels=-1;
        long tint=Long.MIN_VALUE;
        Binding(View owner,View host,String slot,String field,String role){
            this.owner=new WeakReference<>(owner);this.host=new WeakReference<>(host);this.slot=slot;this.field=field;this.role=role;
            parent=new WeakReference<>(owner.getParent() instanceof View?(View)owner.getParent():null);
        }
        boolean valid(){View view=owner.get(),root=host.get();return view!=null&&root!=null&&view.isAttachedToWindow()
                &&root.isAttachedToWindow()&&view.getParent()==parent.get()&&within(view,root);}
    }
    private static final class Overlay {
        WeakReference<Drawable> source=new WeakReference<>(null);
        // The getter does not install its return value in a View. Retain this
        // C17 wrapper (whose owner is weak) so steady draws never allocate one.
        Drawable applied;
        int requestedPixels=-1;
        long tint=Long.MIN_VALUE;
    }
    private final View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){
        @Override public void onViewAttachedToWindow(View view){if(!released)refreshOwner(view);}
        @Override public void onViewDetachedFromWindow(View view){
            cancelWait(view);cancelCompletion(view);Binding binding=bindings.get(view);if(binding!=null)restore(binding);
            for(Binding child:new ArrayList<>(bindings.values()))if(child.host.get()==view)restore(child);
            overlays.remove(view);
        }
    };
    private final View.OnLayoutChangeListener layout=(view,l,t,r,b,ol,ot,or,ob)->{
        if(r-l==or-ol&&b-t==ob-ot)return;Binding binding=bindings.get(view);if(binding!=null)apply(binding);
    };
    void configure(boolean value){enabled=value&&!released&&!ModuleLifecycle.removed();
        if(!enabled){for(View view:new ArrayList<>(waits.keySet()))cancelWait(view);for(View view:new ArrayList<>(completions.keySet()))cancelCompletion(view);}
        refresh();}
    void refresh(){
        for(Binding binding:new ArrayList<>(bindings.values())){restore(binding);apply(binding);}
        // Re-read semantic owners too: an animation may have completed while
        // disabled, so there may be no installed static binding to refresh.
        for(View owner:new ArrayList<>(observed.keySet()))if(type(owner,VOLUME)||type(owner,PROGRESS)||type(owner,PLAY_PAUSE))refreshOwner(owner);
        for(View owner:new ArrayList<>(overlays.keySet())){Overlay overlay=overlays.get(owner);if(overlay!=null){overlay.requestedPixels=-1;overlay.applied=null;overlay.tint=Long.MIN_VALUE;}owner.invalidate();}
    }
    void onBrightnessUpdated(View owner){
        if(released||!type(owner,BRIGHTNESS))return;
        if(field(owner,"brightnessIcon")==null)return;
        brightnessAuto.put(owner,Boolean.TRUE.equals(call(owner,"getAutoMode")));
        bind(owner,owner,"brightness","brightnessIcon","slider.brightness.default");
    }
    /** Getter-result adapter: the OEM decides auto/global-theme visibility and owns its Lazy value. */
    Drawable brightnessOverlay(View owner,Drawable nativeGraphic){
        if(released||!type(owner,BRIGHTNESS)||!enabled||!owner.isAttachedToWindow()||nativeGraphic==null)return graphics.unwrap(nativeGraphic);
        if(!Boolean.TRUE.equals(brightnessAuto.get(owner)))return graphics.unwrap(nativeGraphic);
        String role="slider.brightness_auto.default";if(!graphics.hasAppearance(role))return graphics.unwrap(nativeGraphic);
        observe(owner);Overlay overlay=overlays.get(owner);if(overlay==null){overlay=new Overlay();overlays.put(owner,overlay);}
        Drawable source=graphics.unwrap(nativeGraphic);int pixels=extent(owner,source);
        if(overlay.source.get()!=source){overlay.source=new WeakReference<>(source);overlay.applied=null;overlay.requestedPixels=-1;overlay.tint=Long.MIN_VALUE;}
        if(overlay.requestedPixels!=pixels){graphics.request(owner,role,pixels);overlay.requestedPixels=pixels;}
        Drawable output=overlay.applied;if(output==null){output=graphics.wrap(owner,role,source);if(output!=source)overlay.applied=output;}
        long tint=nativeColor(owner,source);if(output!=source&&tint!=Long.MIN_VALUE&&tint!=overlay.tint){graphics.nativeTint(output,(int)tint);overlay.tint=tint;}
        return output;
    }
    void onVolumeState(View owner,Object state){
        if(released||!type(owner,VOLUME)||!(owner instanceof ImageView))return;
        if(!type(state,VOLUME_STATE)){retire(owner,"volume");return;}
        Integer route=integer(call(state,"getRoute")),level=integer(call(state,"getLevel")),max=integer(call(state,"getMaxLevel"));
        Object zen=call(state,"getZenMuted");
        if(route==null||level==null||max==null||level<0||route<0||route>2||max<=0||level>max||!Boolean.FALSE.equals(zen)){
            retire(owner,"volume");return;
        }
        String role="slider.volume."+(route==0?"wired":route==1?"bluetooth":"normal")+'.';
        role+=level==0?"mute":route!=2?"unmute":level<=(int)(max*.33f)?"low":level<=(int)(max*.66f)?"medium":"high";
        Object animating=call(owner,"isAnimating");if(!(animating instanceof Boolean)){retire(owner,"volume");return;}
        if(Boolean.TRUE.equals(animating)){retire(owner,"volume");if(enabled)waitForAnimation(owner,owner,true);return;}
        cancelWait(owner);bind(owner,owner,"volume",null,role);
    }
    void onVolumeUpdated(View owner){if(type(owner,VOLUME))onVolumeState(owner,read(owner,"qsVolumeState"));}
    void onFooterUpdated(View host){
        if(released||!type(host,FOOTER))return;
        bindImage(host,"settings",read(host,"mSettingsButtonIcon"),"footer.settings.default");
        bindImage(host,"more",read(host,"mMoreIcon"),"footer.more.default");
    }
    /** Only the exact standard semantic binder may opt the media buttons in. */
    void onMediaSemantic(View panel,View button,int nativeResource,Object action,Object data){
        if(released||!type(panel,MEDIA)||!type(button,BUTTON)||!within(button,panel))return;
        String slot=button==read(panel,"preBtn")?"previous":button==read(panel,"nextBtn")?"next":button==read(panel,"playOrPauseBtn")?"play_pause":null;
        if(slot==null)return;
        Object extra=type(action,MEDIA_ACTION)?call(action,"getMediaActionEx"):null;
        boolean standard=type(extra,MEDIA_ACTION_EX)&&Boolean.TRUE.equals(read(extra,"isStandardAction"))&&call(action,"getAction") instanceof Runnable;
        if(!type(data,MEDIA_DATA)||!standard){onMediaCustom(panel,button);return;}
        if(slot.equals("play_pause")){standardPlay.put(button,true);onMediaPlayPause(button);return;}
        Integer expected=integer(call(panel,slot.equals("previous")?"getPreBtnRes":"getNextBtnRes"));
        if(expected==null||expected!=nativeResource){retire(panel,slot);return;}
        bindImage(panel,slot,call(button,"getImageView"),"media."+slot+".default");
    }
    void onMediaCustom(View panel,View button){
        if(released||!type(panel,MEDIA))return;
        if(button==read(panel,"playOrPauseBtn")){standardPlay.remove(button);retire(panel,"play_pause");}
        if(button==read(panel,"preBtn"))retire(panel,"previous");
        if(button==read(panel,"nextBtn"))retire(panel,"next");
    }
    void onMediaPlayPause(View button){
        if(released||!type(button,PLAY_PAUSE)||!Boolean.TRUE.equals(standardPlay.get(button)))return;
        View panel=ancestor(button,MEDIA);if(panel==null||read(panel,"playOrPauseBtn")!=button)return;
        Integer state=integer(read(button,"state"));
        if(state==null||state!=2&&state!=3){retire(panel,"play_pause");return;}
        bindImage(panel,"play_pause",call(button,"getImageView"),"media.play_pause."+(state==2?"play":"pause"));
    }
    void onMediaOutput(View panel,int route){
        if(released||!type(panel,MEDIA))return;
        if(route!=2||!Boolean.FALSE.equals(read(panel,"inAudioSharing"))){retire(panel,"output");return;}
        bindImage(panel,"output",read(panel,"mediaOutputBtn"),"media.output.default");
    }
    /** A notification's actual output-device artwork overrides the generic route entry. */
    void onMediaBound(View panel,Object data){
        if(released||!type(panel,MEDIA))return;
        if(!type(data,MEDIA_DATA)){onMediaUnbound(panel);return;}
        Object device=call(data,"getDevice");if(device==null)return;
        if(!type(device,"com.android.systemui.media.controls.shared.model.MediaDeviceData")){retire(panel,"output");return;}
        Integer resource=integer(call(device,"getIconResId")),expected=integer(call(panel,"getDeviceSwitchRes"));
        if(expected==null||resource==null||!resource.equals(expected))retire(panel,"output");
    }
    void onMediaUnbound(View panel){
        if(!type(panel,MEDIA))return;Object button=read(panel,"playOrPauseBtn");if(button instanceof View)standardPlay.remove(button);
        for(String slot:new String[]{"play_pause","previous","next","output"})retire(panel,slot);
    }
    /** Exact resource input is required; an enum alone cannot identify a generic device image. */
    void onDeviceIcon(View progress,int nativeResource){
        if(released||!type(progress,PROGRESS))return;
        deviceResources.put(progress,nativeResource);onDeviceUpdated(progress);
    }
    /** The Drawable overload can be a remote/brand image. Forget the previous generic resource identity. */
    void onDeviceDrawable(View progress){
        if(released||!type(progress,PROGRESS))return;deviceResources.remove(progress);cancelWait(progress);cancelCompletion(progress);
        View card=deviceCard(progress);if(card!=null)retire(card,"device");
    }
    void onDeviceUpdated(View anchor){
        if(released||anchor==null)return;
        View entrance=ancestorEither(anchor,SQUARE_ENTRANCE,RECTANGLE_ENTRANCE);
        if(entrance!=null){bindImage(entrance,"entrance",read(entrance,type(entrance,SQUARE_ENTRANCE)?"m":"p"),"device.entrance.default");return;}
        View card=deviceCard(anchor);if(card==null)return;
        Object progress=read(card,"p"),model=read(card,type(card,SQUARE)?"q":"v");
        if(!type(progress,PROGRESS)||!type(model,SDK+"entity.PanelDevice")||method(model,"getIconUri")==null||method(model,"getDeviceType")==null||field(progress,"k")==null){retire(card,"device");return;}
        Object uri=call(model,"getIconUri"),kind=call(model,"getDeviceType");
        if(!(uri==null||uri instanceof String&&((String)uri).isEmpty())||!type(kind,SDK+"entity.DeviceType")||!(kind instanceof Enum)){
            retire(card,"device");return;
        }
        String name=((Enum<?>)kind).name(),alias=null;
        switch(name){case "PHONE":alias="phone";break;case "PAD":alias="pad";break;case "COMPUTER":alias="computer";break;
            case "HEADSET":alias="headset";break;case "TV":alias="tv";break;case "WATCH":alias="watch";break;case "WRISTBAND":alias="wristband";break;default:break;}
        if(alias==null){retire(card,"device");return;}
        Integer resource=deviceResources.get(progress);
        int expected=ResourceIds.get(card.getResources(),"device_card_"+alias,"drawable","com.android.systemui");
        if(expected<=0||resource==null||resource!=expected){retire(card,"device");return;}
        Object animation=read(progress,"k");
        if(animation!=null&&Boolean.TRUE.equals(call(animation,"isRunning"))){retire(card,"device");if(enabled)waitForAnimation((View)progress,animation,false);return;}
        cancelWait((View)progress);bindImage(card,"device",read(progress,"f"),"device."+alias+".default");
    }
    private void bindImage(View host,String slot,Object candidate,String role){
        if(!(candidate instanceof ImageView)||!within((View)candidate,host)){retire(host,slot);return;}
        bind((View)candidate,host,slot,null,role);
    }
    private void bind(View owner,View host,String slot,String field,String role){
        for(Binding previous:new ArrayList<>(bindings.values()))if(previous.host.get()==host&&previous.slot.equals(slot)
                &&(previous.owner.get()!=owner||!previous.valid()))remove(previous);
        Binding binding=bindings.get(owner);
        if(binding!=null&&(!binding.role.equals(role)||binding.host.get()!=host||!binding.slot.equals(slot))){remove(binding);binding=null;}
        if(binding==null){binding=new Binding(owner,host,slot,field,role);bindings.put(owner,binding);observe(owner);observe(host);}
        apply(binding);
    }
    private void apply(Binding binding){
        View owner=binding.owner.get();if(owner==null)return;
        if(!enabled||released||ModuleLifecycle.removed()||!binding.valid()||!graphics.hasAppearance(binding.role)){restore(binding);return;}
        Drawable current=graphic(binding),nativeGraphic=graphics.unwrap(current);if(nativeGraphic==null)return;
        int pixels=extent(owner,nativeGraphic);
        if(binding.requestedPixels!=pixels){graphics.request(owner,binding.role,pixels);binding.requestedPixels=pixels;}
        long tint=nativeColor(owner,nativeGraphic);
        // A Lottie palette is not exposed by Drawable.getColorFilter(). If its
        // audited native state is unavailable, keep that native drawing intact.
        if(tint==Long.MIN_VALUE&&type(owner,VOLUME)&&type(nativeGraphic,LOTTIE)){restore(binding);return;}
        if(current==binding.applied.get()){
            if(current!=nativeGraphic&&tint!=Long.MIN_VALUE&&tint!=binding.tint){graphics.nativeTint(current,(int)tint);binding.tint=tint;}return;
        }
        Drawable next=graphics.wrap(owner,binding.role,nativeGraphic);binding.applied=new WeakReference<>(next);
        binding.tint=tint;if(next!=nativeGraphic&&tint!=Long.MIN_VALUE)graphics.nativeTint(next,(int)tint);
        if(next!=current)write(binding,next);
    }
    private Drawable graphic(Binding binding){
        View view=binding.owner.get();Object value=view==null?null:binding.field==null?((ImageView)view).getDrawable():read(view,binding.field);
        return value instanceof Drawable?(Drawable)value:null;
    }
    private void write(Binding binding,Drawable value){
        View owner=binding.owner.get();if(owner==null)return;
        if(binding.field==null)((ImageView)owner).setImageDrawable(value);
        else try{Field field=field(owner,binding.field);if(field!=null){field.set(owner,value);owner.invalidate();}}catch(ReflectiveOperationException|RuntimeException ignored){ }
    }
    private void restore(Binding binding){
        Drawable current=graphic(binding),nativeGraphic=graphics.unwrap(current);
        if(current==binding.applied.get()&&current!=nativeGraphic)write(binding,nativeGraphic);binding.applied.clear();binding.requestedPixels=-1;binding.tint=Long.MIN_VALUE;
    }
    private void retire(View host,String slot){for(Binding binding:new ArrayList<>(bindings.values()))if(binding.host.get()==host&&binding.slot.equals(slot))remove(binding);}
    private void remove(Binding binding){restore(binding);View owner=binding.owner.get();if(owner!=null)bindings.remove(owner);}
    private void observe(View view){if(!observed.containsKey(view)){observed.put(view,true);view.addOnAttachStateChangeListener(attachment);view.addOnLayoutChangeListener(layout);}}
    private void refreshOwner(View view){
        if(type(view,BRIGHTNESS))onBrightnessUpdated(view);
        else if(type(view,VOLUME))onVolumeUpdated(view);
        else if(type(view,FOOTER))onFooterUpdated(view);
        else if(type(view,PLAY_PAUSE))onMediaPlayPause(view);
        else {Binding binding=bindings.get(view);if(binding!=null)apply(binding);onDeviceUpdated(view);}
    }
    private final class AnimationWait {
        final WeakReference<View> owner;final WeakReference<Object> animation;final Method remove;final Object listener;
        AnimationWait(View view,Object animation,Method remove,Class<?> listenerType){
            owner=new WeakReference<>(view);this.animation=new WeakReference<>(animation);this.remove=remove;
            listener=Proxy.newProxyInstance(listenerType.getClassLoader(),new Class<?>[]{listenerType},(proxy,method,args)->{
                String name=method.getName();if(name.equals("hashCode"))return System.identityHashCode(proxy);
                if(name.equals("equals"))return args!=null&&args.length==1&&proxy==args[0];if(name.equals("toString"))return "C17 glyph completion";
                if(name.equals("onAnimationEnd")||name.equals("onAnimationCancel")){
                    View current=owner.get();if(current!=null&&waits.get(current)==this){
                        cancelWait(current);cancelCompletion(current);WeakReference<View> reference=new WeakReference<>(current);
                        Runnable pending=()->{View attached=reference.get();if(attached!=null){completions.remove(attached);if(enabled&&!released)refreshOwner(attached);}};
                        completions.put(current,pending);current.post(pending);
                    }
                }return null;
            });
        }
    }
    private void waitForAnimation(View owner,Object animation,boolean lottie){
        AnimationWait previous=waits.get(owner);if(previous!=null&&previous.animation.get()==animation)return;
        cancelWait(owner);observe(owner);
        try{
            Class<?> listenerType=Class.forName("android.animation.Animator$AnimatorListener",false,owner.getClass().getClassLoader());
            Method add=method(animation,lottie?"addAnimatorListener":"addListener",listenerType),remove=method(animation,lottie?"removeAnimatorListener":"removeListener",listenerType);
            if(add==null||remove==null)return;
            AnimationWait wait=new AnimationWait(owner,animation,remove,listenerType);waits.put(owner,wait);add.invoke(animation,wait.listener);
        }catch(ReflectiveOperationException|RuntimeException unavailable){cancelWait(owner);}
    }
    private void cancelWait(View owner){
        AnimationWait wait=waits.remove(owner);if(wait==null)return;Object animation=wait.animation.get();
        if(animation!=null)try{wait.remove.invoke(animation,wait.listener);}catch(ReflectiveOperationException|RuntimeException ignored){ }
    }
    private void cancelCompletion(View owner){Runnable pending=completions.remove(owner);if(pending!=null)owner.removeCallbacks(pending);}
    void releaseRuntime(){
        if(released)return;enabled=false;released=true;
        for(View owner:new ArrayList<>(waits.keySet()))cancelWait(owner);
        for(View owner:new ArrayList<>(completions.keySet()))cancelCompletion(owner);
        for(Binding binding:new ArrayList<>(bindings.values()))restore(binding);
        for(View view:new ArrayList<>(observed.keySet())){view.removeOnAttachStateChangeListener(attachment);view.removeOnLayoutChangeListener(layout);}
        bindings.clear();overlays.clear();observed.clear();standardPlay.clear();brightnessAuto.clear();deviceResources.clear();completions.clear();filterColors.clear();reflection.clear();
    }
    String summary(){return "Non-tile glyph hosts "+bindings.size()+", overlay hosts "+overlays.size()+", animation waits "+waits.size()+", enabled "+enabled;}
    private static int extent(View view,Drawable drawable){
        int bounds=Math.max(drawable.getBounds().width(),drawable.getBounds().height());
        int pixels=bounds>0?bounds:Math.min(view.getWidth(),view.getHeight());
        return Math.max(1,Math.min(1024,pixels));
    }
    private long nativeColor(View view,Drawable drawable){
        ColorFilter filter=drawable.getColorFilter();
        if(filter instanceof PorterDuffColorFilter||filter instanceof BlendModeColorFilter){
            Long cached=filterColors.get(filter);if(cached!=null)return cached;
            // PorterDuff's getter is hidden in the SDK stub, but present on the
            // audited host. Filters are immutable: resolve once per filter,
            // not on every brightness-overlay getter/draw.
            Integer value=filter instanceof BlendModeColorFilter?((BlendModeColorFilter)filter).getColor():integer(call(filter,"getColor"));
            long color=value==null?Long.MIN_VALUE:value&0xffffffffL;filterColors.put(filter,color);return color;
        }
        if(view instanceof ImageView){ColorStateList colors=((ImageView)view).getImageTintList();
            if(colors!=null)return colors.getColorForState(view.getDrawableState(),colors.getDefaultColor())&0xffffffffL;}
        return volumeLottieColor(view,drawable);
    }
    private long volumeLottieColor(View view,Drawable drawable){
        if(!type(view,VOLUME)||!type(drawable,LOTTIE)||read(view,"lottieDrawable")!=drawable
                ||!Boolean.FALSE.equals(call(view,"isAnimating")))return Long.MIN_VALUE;
        Object state=read(view,"qsVolumeState"),icon=read(view,"currentIconState");
        if(!type(state,VOLUME_STATE))return Long.MIN_VALUE;
        Integer route=integer(call(state,"getRoute")),level=integer(call(state,"getLevel"));
        if(route==null||level==null||level<0||!Boolean.FALSE.equals(call(state,"getZenMuted")))return Long.MIN_VALUE;
        boolean bluetooth=route==1;
        if(!bluetooth&&route!=2||!type(icon,VOLUME+(bluetooth?"$bluetoothRouteIconState$1":"$normalRouteIconState$1"))
                ||read(icon,"this$0")!=view||!Boolean.FALSE.equals(read(icon,"isGlobalTheme"))
                ||!level.equals(integer(read(icon,"lastLevel"))))return Long.MIN_VALUE;
        Object above=read(icon,"aboveThreshold");
        if(!(above instanceof Boolean)||level==0&&Boolean.TRUE.equals(above)
                ||bluetooth&&!Boolean.valueOf(level==0).equals(read(icon,"isMuted")))return Long.MIN_VALUE;
        Object asset=read(icon,"currentAsset"),config=read(icon,"colorfulConfig");
        if(!(asset instanceof String)||!asset.equals(read(view,"assetFileName"))||!volumeAsset((String)asset,bluetooth)
                ||!type(config,VOLUME_COLORS)||config!=read(view,"colorfulConfig"))return Long.MIN_VALUE;
        Integer start=integer(read(icon,"currentStartFrame")),end=integer(read(icon,"currentEndFrame")),frame=integer(call(view,"getFrame"));
        Object speed=read(icon,"currentSpeed"),actualSpeed=call(view,"getSpeed");
        if(start==null||end==null||frame==null||start<0||end<=start||!(speed instanceof Float)||!speed.equals(actualSpeed)
                ||!Float.isFinite((Float)speed)||(Float)speed==0f)return Long.MIN_VALUE;
        boolean reversed=(Float)speed<0f;Object limit=call(view,reversed?"getMinFrame":"getMaxFrame");
        if(!(limit instanceof Float)||!Float.isFinite((Float)limit))return Long.MIN_VALUE;
        float edge=(Float)limit;
        // Lottie's inclusive int end becomes end+.99, then its composition
        // out-point may clamp it (36 becomes 35.99). Use that native bound.
        if((reversed?edge<start||edge>=start+1f:edge>=end+1f||edge<=end-1f)||frame.intValue()!=(int)edge)return Long.MIN_VALUE;
        Integer first=integer(call(config,"getStartColor")),last=integer(call(config,"getEndColor"));
        if(first==null||last==null||first==0||last==0)return Long.MIN_VALUE;
        // These are the startFill/endFill palettes, not chronological endpoints.
        // A reversed open ends on startFill; Bluetooth close swaps its palettes.
        int color=Boolean.TRUE.equals(above)||bluetooth&&level==0?last:first;
        // Native FillContent/StrokeContent replace the palette's alpha with
        // their opacity. The OEM view and drawable still own that opacity here.
        return (color|0xff000000)&0xffffffffL;
    }
    private static boolean volumeAsset(String asset,boolean bluetooth){
        if(bluetooth)return asset.equals("volumeui/ic_qs_volume_media_bt_open_night.json")
                ||asset.equals("volumeui/ic_qs_volume_media_bt_close_night.json");
        String[] names={"ic_qs_volume_media","ic_qs_volume_media_grade_0_to_2","ic_qs_volume_media_grade_0_to_3",
                "ic_qs_volume_media_grade_2_to_0","ic_qs_volume_media_grade_3_to_0"};
        for(String name:names)if(asset.equals("volumeui/"+name+"_night.json")||asset.equals("volumeui/"+name+"_gauss_night.json"))return true;
        return false;
    }
    private static Integer integer(Object value){return value instanceof Integer?(Integer)value:null;}
    private static boolean type(Object object,String name){
        if(object==null)return false;for(Class<?> cls=object.getClass();cls!=null;cls=cls.getSuperclass())if(cls.getName().equals(name))return true;return false;
    }
    private static boolean within(View view,View host){
        for(int depth=0;view!=null&&depth<16;depth++){if(view==host)return true;ViewParent parent=view.getParent();view=parent instanceof View?(View)parent:null;}return false;
    }
    private static View ancestor(View view,String name){
        for(int depth=0;view!=null&&depth<12;depth++){if(type(view,name))return view;view=view.getParent() instanceof View?(View)view.getParent():null;}return null;
    }
    private static View ancestorEither(View view,String first,String second){View value=ancestor(view,first);return value==null?ancestor(view,second):value;}
    private static View deviceCard(View view){return ancestorEither(view,SQUARE,RECTANGLE);}
    private Members members(Object owner){return reflection.computeIfAbsent(owner.getClass(),ignored->new Members());}
    private Field field(Object owner,String name){
        if(owner==null)return null;Members members=members(owner);if(members.fields.containsKey(name))return members.fields.get(name);
        Field found=null;for(Class<?> cls=owner.getClass();cls!=null;cls=cls.getSuperclass())try{found=cls.getDeclaredField(name);found.setAccessible(true);break;}catch(NoSuchFieldException ignored){ }catch(RuntimeException unavailable){found=null;break;}
        members.fields.put(name,found);return found;
    }
    private Object read(Object owner,String name){try{Field found=field(owner,name);return found==null?null:found.get(owner);}catch(ReflectiveOperationException|RuntimeException unavailable){return null;}}
    private Method method(Object owner,String name,Class<?>... parameters){
        if(owner==null)return null;Members members=members(owner);StringBuilder key=new StringBuilder(name);for(Class<?> parameter:parameters)key.append('#').append(parameter.getName());
        String id=key.toString();if(members.methods.containsKey(id))return members.methods.get(id);
        Method found=null;for(Class<?> cls=owner.getClass();cls!=null;cls=cls.getSuperclass())try{found=cls.getDeclaredMethod(name,parameters);found.setAccessible(true);break;}catch(NoSuchMethodException ignored){ }catch(RuntimeException unavailable){found=null;break;}
        members.methods.put(id,found);return found;
    }
    private Object call(Object owner,String name){try{Method found=method(owner,name);return found==null?null:found.invoke(owner);}catch(ReflectiveOperationException|RuntimeException unavailable){return null;}}
}
