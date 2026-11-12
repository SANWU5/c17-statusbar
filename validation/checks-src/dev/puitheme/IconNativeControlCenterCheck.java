package dev.puitheme;

import android.animation.Animator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import com.android.systemui.media.controls.shared.model.MediaAction;
import com.android.systemui.media.controls.shared.model.MediaData;
import com.android.systemui.media.controls.shared.model.MediaDeviceData;
import com.android.systemui.plugins.qs.QSVolume.VolumeState;
import com.oplus.deviceplugin.sdk.entity.DeviceType;
import com.oplus.deviceplugin.sdk.entity.PanelDevice;
import com.oplus.deviceplugin.sdk.ui.view.progressbar.BatteryCircularProgressLayout;
import com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareDeviceCardView;
import com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareEntranceCardView;
import com.oplus.deviceplugin.sdk.ui.view.separatecardview.RectangleEntranceCardView;
import com.oplus.systemui.qs.OplusQSFooterImpl;
import com.oplus.systemui.qs.base.seek.ClipBrightnessView;
import com.oplus.systemui.qs.base.seek.OplusQsVolumeIconView;
import com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView;
import com.oplus.systemui.qs.slider.OplusQsDetailHookClipBrightnessView;
import java.util.*;

/** Executes the production adapter against exact native field/type contracts.
 * Its injected graphics port isolates asynchronous asset IO from binder ownership. */
public final class IconNativeControlCenterCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Non-tile "+checks+": "+expected+" != "+actual);}
    private static final class Native extends Drawable {
        int alpha=187,draws,drawAlpha;ColorFilter filter,drawFilter;
        public void draw(Canvas c){draws++;drawAlpha=alpha;drawFilter=filter;}public void setAlpha(int value){alpha=value;}public int getAlpha(){return alpha;}
        public void setColorFilter(ColorFilter value){filter=value;}public ColorFilter getColorFilter(){return filter;}public int getOpacity(){return -3;}
    }
    private static final class Wrapped extends Drawable {
        final String role;final Drawable source;Integer tint;Wrapped(String role,Drawable source){this.role=role;this.source=source;}
        public void draw(Canvas c){source.draw(c);}public void setAlpha(int value){source.setAlpha(value);}
        public void setColorFilter(ColorFilter value){source.setColorFilter(value);}public int getOpacity(){return source.getOpacity();}
    }
    private static final class Port implements IconNativeControlCenter.Graphics {
        boolean available=true;int requests,wraps,tints;final List<String> roles=new ArrayList<>();
        public Drawable wrap(View owner,String role,Drawable graphic){wraps++;return new Wrapped(role,graphic);}
        public Drawable unwrap(Drawable graphic){while(graphic instanceof Wrapped)graphic=((Wrapped)graphic).source;return graphic;}
        public boolean hasAppearance(String role){return available;}
        public void nativeTint(Drawable wrapper,int argb){tints++;((Wrapped)wrapper).tint=argb;}
        public void request(View owner,String role,int pixels){requests++;roles.add(role);equal(true,pixels>0&&pixels<=1024);}
    }
    private static String role(Drawable d){return d instanceof Wrapped?((Wrapped)d).role:"native";}
    private static void detach(View view){view.attached=false;for(View.OnAttachStateChangeListener l:new ArrayList<>(view.attachListeners))l.onViewDetachedFromWindow(view);}
    private static void attach(View view){view.attached=true;for(View.OnAttachStateChangeListener l:new ArrayList<>(view.attachListeners))l.onViewAttachedToWindow(view);}
    private static void brightness(){
        Port port=new Port();IconNativeControlCenter adapter=new IconNativeControlCenter(port);ClipBrightnessView view=new ClipBrightnessView();
        Native original=new Native();view.setBrightnessIcon(original);view.setAlpha(.43f);view.setTranslationY(9);
        adapter.onBrightnessUpdated(view);equal(original,view.getBrightnessIcon());adapter.configure(true);
        equal("slider.brightness.default",role(view.getBrightnessIcon()));equal(.43f,view.getAlpha());equal(9f,view.getTranslationY());
        equal(187,original.getAlpha());int wraps=port.wraps,requests=port.requests;
        for(int i=0;i<100;i++)adapter.onBrightnessUpdated(view);equal(wraps,port.wraps);equal(requests,port.requests);
        Native newer=new Native();view.setBrightnessIcon(newer);adapter.onBrightnessUpdated(view);equal(newer,port.unwrap(view.getBrightnessIcon()));
        Native overlay=new Native();equal(overlay,adapter.brightnessOverlay(view,overlay));view.auto=true;adapter.onBrightnessUpdated(view);
        Drawable wrapped=adapter.brightnessOverlay(view,overlay);equal("slider.brightness_auto.default",role(wrapped));
        requests=port.requests;for(int i=0;i<100;i++)equal(wrapped,adapter.brightnessOverlay(view,overlay));equal(requests,port.requests);
        view.auto=false;adapter.onBrightnessUpdated(view);equal(overlay,adapter.brightnessOverlay(view,overlay));
        detach(view);equal(newer,view.getBrightnessIcon());attach(view);equal("slider.brightness.default",role(view.getBrightnessIcon()));
        port.available=false;adapter.refresh();equal(newer,view.getBrightnessIcon());
        port.available=true;adapter.refresh();equal("slider.brightness.default",role(view.getBrightnessIcon()));
        adapter.configure(false);equal(newer,view.getBrightnessIcon());equal(overlay,adapter.brightnessOverlay(view,overlay));
        OplusQsDetailHookClipBrightnessView detail=new OplusQsDetailHookClipBrightnessView();Native detailNative=new Native();detail.setBrightnessIcon(detailNative);
        adapter.configure(true);adapter.onBrightnessUpdated(detail);equal("slider.brightness.default",role(detail.getBrightnessIcon()));
        adapter.releaseRuntime();equal(newer,view.getBrightnessIcon());equal(detailNative,detail.getBrightnessIcon());equal(0,view.attachListeners.size());equal(0,detail.attachListeners.size());
        adapter.onBrightnessUpdated(view);equal(newer,view.getBrightnessIcon());
    }
    private static void nativeMetadata(){
        Port port=new Port();IconNativeControlCenter adapter=new IconNativeControlCenter(port);adapter.configure(true);
        ClipBrightnessView direct=new ClipBrightnessView();Native original=new Native();ColorFilter black=new PorterDuffColorFilter(0xff112233,PorterDuff.Mode.SRC_IN);
        original.filter=black;direct.setBrightnessIcon(original);adapter.onBrightnessUpdated(direct);
        equal(0xff112233,((Wrapped)direct.getBrightnessIcon()).tint);equal(black,original.filter);equal(187,original.alpha);
        int tints=port.tints,wraps=port.wraps,requests=port.requests;
        for(int i=0;i<100;i++)adapter.onBrightnessUpdated(direct);
        equal(tints,port.tints);equal(wraps,port.wraps);equal(requests,port.requests);
        ColorFilter white=new PorterDuffColorFilter(0xffeeeeee,PorterDuff.Mode.SRC_IN);original.filter=white;adapter.onBrightnessUpdated(direct);
        equal(0xffeeeeee,((Wrapped)direct.getBrightnessIcon()).tint);equal(tints+1,port.tints);equal(white,original.filter);
        direct.auto=true;adapter.onBrightnessUpdated(direct);Native overlay=new Native();overlay.filter=black;
        Drawable wrapped=adapter.brightnessOverlay(direct,overlay);equal(0xff112233,((Wrapped)wrapped).tint);tints=port.tints;
        for(int i=0;i<100;i++)equal(wrapped,adapter.brightnessOverlay(direct,overlay));equal(tints,port.tints);
        overlay.filter=white;equal(wrapped,adapter.brightnessOverlay(direct,overlay));equal(0xffeeeeee,((Wrapped)wrapped).tint);equal(tints+1,port.tints);
        OplusQSFooterImpl footer=new OplusQSFooterImpl();Native image=new Native();footer.mSettingsButtonIcon.setImageDrawable(image);
        footer.mSettingsButtonIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xff556677));adapter.onFooterUpdated(footer);
        equal(0xff556677,((Wrapped)footer.mSettingsButtonIcon.getDrawable()).tint);equal(null,image.filter);equal(187,image.alpha);
        adapter.configure(false);equal(original,direct.getBrightnessIcon());equal(white,original.filter);equal(image,footer.mSettingsButtonIcon.getDrawable());equal(null,image.filter);
        equal(overlay,adapter.brightnessOverlay(direct,overlay));equal(white,overlay.filter);adapter.releaseRuntime();
    }
    private static void runtimeMetadata()throws Exception{
        IconManagementRuntime.releaseRuntime();
        String records=IconManagementSettings.applyAppearance(IconManagementSettings.DEFAULT,"target:slider.brightness",new IconManagementSettings.Appearance("custom",0xff123456,0xffabcdef,50f));
        android.os.Bundle settings=new android.os.Bundle();settings.putString(IconManagementSettings.KEY,records);
        IconManagementRuntime.configure(new android.content.Context(),settings);
        IconNativeControlCenter adapter=new IconNativeControlCenter();adapter.configure(true);ClipBrightnessView direct=new ClipBrightnessView();
        Native original=new Native();ColorFilter black=new PorterDuffColorFilter(0xff112233,PorterDuff.Mode.SRC_IN);original.filter=black;
        direct.setBrightnessIcon(original);adapter.onBrightnessUpdated(direct);Drawable wrapped=direct.getBrightnessIcon();equal(false,wrapped==original);
        wrapped.draw(new Canvas());equal(0xff123456,((PorterDuffColorFilter)original.drawFilter).getColor());equal(94,original.drawAlpha);
        equal(black,original.filter);equal(187,original.alpha);
        ColorFilter white=new PorterDuffColorFilter(0xffeeeeee,PorterDuff.Mode.SRC_IN);original.filter=white;adapter.onBrightnessUpdated(direct);
        equal(wrapped,direct.getBrightnessIcon());wrapped.draw(new Canvas());equal(0xffabcdef,((PorterDuffColorFilter)original.drawFilter).getColor());
        equal(94,original.drawAlpha);equal(white,original.filter);equal(187,original.alpha);
        adapter.configure(false);equal(original,direct.getBrightnessIcon());original.draw(new Canvas());equal(white,original.drawFilter);equal(187,original.drawAlpha);
        adapter.releaseRuntime();IconManagementRuntime.releaseRuntime();android.os.Handler.drainForCheck();
    }
    private static void volume(){
        Port port=new Port();IconNativeControlCenter adapter=new IconNativeControlCenter(port);adapter.configure(true);OplusQsVolumeIconView view=new OplusQsVolumeIconView();
        int[] levels={0,1,3,4,6,7,10};String[] names={"mute","low","low","medium","medium","high","high"};
        for(int i=0;i<levels.length;i++){VolumeState state=new VolumeState(2,levels[i],10,false);view.nativeState(state);view.setImageDrawable(new Native());adapter.onVolumeState(view,state);equal("slider.volume.normal."+names[i],role(view.getDrawable()));}
        for(int route=0;route<2;route++)for(int level:new int[]{0,6}){
            VolumeState state=new VolumeState(route,level,10,false);view.nativeState(state);view.setImageDrawable(new Native());adapter.onVolumeState(view,state);
            equal("slider.volume."+(route==0?"wired":"bluetooth")+'.'+(level==0?"mute":"unmute"),role(view.getDrawable()));
        }
        for(VolumeState state:new VolumeState[]{new VolumeState(-1,1,10,false),new VolumeState(3,1,10,false),new VolumeState(2,-1,10,false),new VolumeState(2,4,0,false),new VolumeState(2,11,10,false),new VolumeState(2,4,10,true)}){
            Native original=new Native();view.setImageDrawable(original);view.nativeState(state);adapter.onVolumeState(view,state);equal(original,view.getDrawable());
        }
        VolumeState state=new VolumeState(2,6,10,false);Native original=new Native();view.nativeState(state);view.setImageDrawable(original);view.animating=true;
        Animator.AnimatorListener nativeListener=new Animator.AnimatorListener(){public void onAnimationStart(Animator a){}public void onAnimationEnd(Animator a){}public void onAnimationCancel(Animator a){}public void onAnimationRepeat(Animator a){}};
        view.listeners.add(nativeListener);view.queuePostedForCheck=true;adapter.onVolumeState(view,state);equal(original,view.getDrawable());equal(2,view.listeners.size());
        for(int i=0;i<20;i++)adapter.onVolumeState(view,state);equal(2,view.listeners.size());
        view.finish();equal(1,view.listeners.size());equal(nativeListener,view.listeners.get(0));equal(original,view.getDrawable());
        view.runPostedForCheck();equal("slider.volume.normal.medium",role(view.getDrawable()));
        detach(view);equal(original,view.getDrawable());attach(view);equal("slider.volume.normal.medium",role(view.getDrawable()));
        view.animating=true;adapter.onVolumeState(view,state);equal(2,view.listeners.size());adapter.configure(false);equal(1,view.listeners.size());equal(original,view.getDrawable());
        adapter.configure(true);adapter.onVolumeState(view,state);view.finish();adapter.releaseRuntime();view.runPostedForCheck();equal(original,view.getDrawable());equal(0,view.attachListeners.size());
    }
    private static com.oplus.systemui.qs.base.tile.QSLottieAnimationView.ColorfulConfig palette(int start,int end,float alpha){
        return new com.oplus.systemui.qs.base.tile.QSLottieAnimationView.ColorfulConfig(.3f,alpha,start,end);
    }
    private static OplusQsVolumeIconView.IconStateForCheck lottieState(OplusQsVolumeIconView view,com.airbnb.lottie.LottieDrawable source,
            int route,int level,boolean above,boolean muted,String asset,int start,int end,float speed,
            com.oplus.systemui.qs.base.tile.QSLottieAnimationView.ColorfulConfig config){
        OplusQsVolumeIconView.IconStateForCheck icon=route==1?new OplusQsVolumeIconView.bluetoothRouteIconState$1(view):new OplusQsVolumeIconView.normalRouteIconState$1(view);
        icon.lastLevel=level;icon.aboveThreshold=above;icon.isMuted=muted;icon.currentAsset=asset;icon.currentStartFrame=start;icon.currentEndFrame=end;icon.currentSpeed=speed;icon.colorfulConfig=config;
        view.currentIconState=icon;view.nativeState(new VolumeState(route,level,160,false));view.animating=false;view.setAlpha(config.getEndAlpha());
        view.lottieForCheck(source,config,asset,speed<0?start:end,speed);view.minFrameForCheck=start;view.maxFrameForCheck=end+.99f;return icon;
    }
    private static void volumeLottie(){
        Port port=new Port();IconNativeControlCenter adapter=new IconNativeControlCenter(port);adapter.configure(true);
        OplusQsVolumeIconView view=new OplusQsVolumeIconView();com.airbnb.lottie.LottieDrawable source=new com.airbnb.lottie.LottieDrawable();
        String open="volumeui/ic_qs_volume_media_bt_open_night.json",close="volumeui/ic_qs_volume_media_bt_close_night.json";
        int mute=0x4dffffff,blue=0xff0066ff;
        OplusQsVolumeIconView.IconStateForCheck icon=lottieState(view,source,1,50,true,false,open,17,27,1f,palette(mute,blue,1f));
        adapter.onVolumeUpdated(view);equal("slider.volume.bluetooth.unmute",role(view.getDrawable()));equal(blue,((Wrapped)view.getDrawable()).tint);
        equal(null,source.getColorFilter());equal(255,source.getAlpha());equal(1f,view.getAlpha());equal(mute,icon.colorfulConfig.getStartColor());
        int wraps=port.wraps,tints=port.tints,requests=port.requests;
        for(int i=0;i<100;i++)adapter.onVolumeUpdated(view);
        equal(wraps,port.wraps);equal(tints,port.tints);equal(requests,port.requests);
        // Reverse open ends on startFill; Lottie replaces palette alpha with its
        // own opacity, while QSLottieAnimationView already owns the view alpha.
        icon=lottieState(view,source,1,5,false,false,open,17,27,-1f,palette(mute,blue,.3f));
        adapter.onVolumeUpdated(view);equal(0xffffffff,((Wrapped)view.getDrawable()).tint);equal(.3f,view.getAlpha());equal(255,source.getAlpha());
        icon=lottieState(view,source,1,0,false,true,close,0,17,1f,palette(blue,mute,.3f));
        adapter.onVolumeUpdated(view);equal("slider.volume.bluetooth.mute",role(view.getDrawable()));equal(0xffffffff,((Wrapped)view.getDrawable()).tint);equal(.3f,view.getAlpha());
        icon=lottieState(view,source,1,4,false,false,open,0,16,1f,palette(mute,mute,.3f));
        adapter.onVolumeUpdated(view);equal(0xffffffff,((Wrapped)view.getDrawable()).tint);
        // Palette values, not a hard-coded accent: native theme colors vary.
        icon=lottieState(view,source,1,70,true,false,open,17,27,1f,palette(0x33112233,0x7f9abcde,.7f));
        adapter.onVolumeUpdated(view);equal(0xff9abcde,((Wrapped)view.getDrawable()).tint);equal(.7f,view.getAlpha());
        // The normal speaker uses startFill below its threshold and when muted.
        for(int level:new int[]{0,4,50}){
            boolean above=level==50;String asset=level==0?"volumeui/ic_qs_volume_media_grade_3_to_0_gauss_night.json":"volumeui/ic_qs_volume_media_gauss_night.json";
            icon=lottieState(view,source,2,level,above,level==0,asset,level==0?0:36,level==0?36:46,level==4?-1f:1f,palette(0x33112233,0xffabcdef,above?1f:.3f));
            adapter.onVolumeUpdated(view);equal(above?0xffabcdef:0xff112233,((Wrapped)view.getDrawable()).tint);equal(above?1f:.3f,view.getAlpha());
        }
        // Lottie clamps a JSON out-point of 36 to 35.99, whose public int
        // getFrame() is 35. It is still the genuinely completed native frame.
        String muted="volumeui/ic_qs_volume_media_grade_3_to_0_gauss_night.json";
        icon=lottieState(view,source,2,0,false,true,muted,0,36,1f,palette(mute,blue,.3f));
        view.lottieForCheck(source,icon.colorfulConfig,muted,35,1f);view.maxFrameForCheck=35.99f;
        adapter.onVolumeUpdated(view);equal("slider.volume.normal.mute",role(view.getDrawable()));equal(0xffffffff,((Wrapped)view.getDrawable()).tint);
        // Live animations retain the OEM drawable and listeners; only the
        // genuine finished state can receive the replacement afterward.
        icon=lottieState(view,source,1,50,true,false,open,17,27,1f,palette(mute,blue,1f));
        view.animating=true;view.queuePostedForCheck=true;adapter.onVolumeUpdated(view);equal(source,view.getDrawable());equal(1,view.listeners.size());
        view.finish();equal(source,view.getDrawable());view.runPostedForCheck();equal(blue,((Wrapped)view.getDrawable()).tint);equal(0,view.listeners.size());
        // Missing/stale contracts leave the original Lottie visible. They must
        // neither default to white nor reuse the previous state's tint.
        for(int invalid=0;invalid<10;invalid++){
            icon=lottieState(view,source,1,50,true,false,open,17,27,1f,palette(mute,blue,1f));
            if(invalid==0)icon.colorfulConfig=palette(mute,blue,1f);
            if(invalid==1)icon.this$0=new OplusQsVolumeIconView();
            if(invalid==2)icon.currentAsset=close;
            if(invalid==3)icon.isGlobalTheme=true;
            if(invalid==4)icon.lastLevel=49;
            if(invalid==5)icon.aboveThreshold=null;
            if(invalid==6)view.lottieForCheck(source,icon.colorfulConfig,open,22,1f);
            if(invalid==7)view.lottieForCheck(new com.airbnb.lottie.LottieDrawable(),icon.colorfulConfig,open,27,1f);
            if(invalid==8)view.currentIconState=new Object();
            if(invalid==9)icon.isMuted=true;
            view.setImageDrawable(source);adapter.onVolumeUpdated(view);equal(source,view.getDrawable());equal(null,source.getColorFilter());
        }
        icon=lottieState(view,source,1,50,true,false,open,17,27,1f,palette(mute,blue,1f));adapter.onVolumeUpdated(view);
        icon.colorfulConfig=null;adapter.onVolumeUpdated(view);equal(source,view.getDrawable());
        // Static vector color filters keep their existing priority and alpha.
        Native vector=new Native();vector.filter=new PorterDuffColorFilter(mute,PorterDuff.Mode.SRC_IN);view.setImageDrawable(vector);adapter.onVolumeUpdated(view);
        equal(mute,((Wrapped)view.getDrawable()).tint);equal(187,vector.alpha);adapter.releaseRuntime();equal(vector,view.getDrawable());equal(0,view.attachListeners.size());
    }
    private static void footer(){
        Port port=new Port();IconNativeControlCenter adapter=new IconNativeControlCenter(port);adapter.configure(true);OplusQSFooterImpl panel=new OplusQSFooterImpl();
        Native settings=new Native(),more=new Native(),avatar=new Native();panel.mSettingsButtonIcon.setImageDrawable(settings);panel.mMoreIcon.setImageDrawable(more);panel.mMultiUserAvatar.setImageDrawable(avatar);
        panel.mSettingsButtonIcon.setContentDescription("native accessibility");final int[] clicks={0};panel.mSettingsButtonIcon.setOnClickListener(v->clicks[0]++);
        adapter.onFooterUpdated(panel);equal("footer.settings.default",role(panel.mSettingsButtonIcon.getDrawable()));equal("footer.more.default",role(panel.mMoreIcon.getDrawable()));
        equal(avatar,panel.mMultiUserAvatar.getDrawable());equal("native accessibility",panel.mSettingsButtonIcon.getContentDescription());panel.mSettingsButtonIcon.performClick();equal(1,clicks[0]);
        ImageView old=panel.mMoreIcon;Native newer=new Native();panel.removeView(old);panel.mMoreIcon=new ImageView(panel.getContext());panel.addView(panel.mMoreIcon);panel.mMoreIcon.setImageDrawable(newer);
        adapter.onFooterUpdated(panel);equal(more,old.getDrawable());equal("footer.more.default",role(panel.mMoreIcon.getDrawable()));
        ImageView outsider=new ImageView(panel.getContext());Native foreign=new Native();outsider.setImageDrawable(foreign);panel.mSettingsButtonIcon=outsider;adapter.onFooterUpdated(panel);equal(foreign,outsider.getDrawable());
        adapter.releaseRuntime();equal(newer,panel.mMoreIcon.getDrawable());equal(0,panel.attachListeners.size());equal(avatar,panel.mMultiUserAvatar.getDrawable());
    }
    private static void media(){
        Port port=new Port();IconNativeControlCenter adapter=new IconNativeControlCenter(port);adapter.configure(true);OplusQsBaseMediaPanelView panel=new OplusQsBaseMediaPanelView();
        Native previous=new Native(),next=new Native(),play=new Native(),output=new Native();panel.preBtn.getImageView().setImageDrawable(previous);panel.nextBtn.getImageView().setImageDrawable(next);
        panel.playOrPauseBtn.getImageView().setImageDrawable(play);panel.mediaOutputBtn.setImageDrawable(output);
        MediaAction standard=new MediaAction(true,()->{}),custom=new MediaAction(false,()->{});MediaData data=new MediaData();
        adapter.onMediaPlayPause(panel.playOrPauseBtn);equal(play,panel.playOrPauseBtn.getImageView().getDrawable());
        adapter.onMediaSemantic(panel,panel.preBtn,0x7f080e5a,standard,data);adapter.onMediaSemantic(panel,panel.nextBtn,0x7f080e54,standard,data);
        adapter.onMediaSemantic(panel,panel.playOrPauseBtn,0x7f080e58,standard,data);
        equal("media.previous.default",role(panel.preBtn.getImageView().getDrawable()));equal("media.next.default",role(panel.nextBtn.getImageView().getDrawable()));equal("media.play_pause.play",role(panel.playOrPauseBtn.getImageView().getDrawable()));
        panel.playOrPauseBtn.state=3;adapter.onMediaPlayPause(panel.playOrPauseBtn);equal("media.play_pause.pause",role(panel.playOrPauseBtn.getImageView().getDrawable()));
        int effectiveVisibility=panel.playOrPauseBtn.effective.getVisibility();adapter.onMediaCustom(panel,panel.playOrPauseBtn);equal(play,panel.playOrPauseBtn.getImageView().getDrawable());equal(effectiveVisibility,panel.playOrPauseBtn.effective.getVisibility());
        adapter.onMediaSemantic(panel,panel.preBtn,0x7f080e5a,custom,data);equal(previous,panel.preBtn.getImageView().getDrawable());
        adapter.onMediaSemantic(panel,panel.nextBtn,1,standard,data);equal(next,panel.nextBtn.getImageView().getDrawable());
        adapter.onMediaSemantic(panel,panel.preBtn,0x7f080e5a,new MediaAction(true,null),data);equal(previous,panel.preBtn.getImageView().getDrawable());
        adapter.onMediaOutput(panel,2);equal("media.output.default",role(panel.mediaOutputBtn.getDrawable()));
        data.device=new MediaDeviceData();data.device.resource=0;adapter.onMediaBound(panel,data);equal(output,panel.mediaOutputBtn.getDrawable());
        adapter.onMediaOutput(panel,2);data.device.resource=0x7f0818f6;adapter.onMediaBound(panel,data);equal("media.output.default",role(panel.mediaOutputBtn.getDrawable()));
        panel.inAudioSharing=true;adapter.onMediaOutput(panel,2);equal(output,panel.mediaOutputBtn.getDrawable());panel.inAudioSharing=false;
        for(int route:new int[]{-1,0,1,7}){adapter.onMediaOutput(panel,route);equal(output,panel.mediaOutputBtn.getDrawable());}
        adapter.onMediaSemantic(panel,panel.preBtn,0x7f080e5a,standard,data);adapter.onMediaOutput(panel,2);adapter.onMediaUnbound(panel);
        equal(previous,panel.preBtn.getImageView().getDrawable());equal(output,panel.mediaOutputBtn.getDrawable());adapter.releaseRuntime();equal(0,panel.preBtn.getImageView().attachListeners.size());
    }
    private static final Map<String,Integer> RESOURCES=new HashMap<>();
    static {String[] names={"phone","pad","computer","headset","tv","watch","wristband"};for(int i=0;i<names.length;i++)RESOURCES.put("device_card_"+names[i],700+i);}
    private static final android.content.res.Resources DEVICE_RESOURCES=new android.content.res.Resources(){
        @Override public int getIdentifier(String name,String kind,String pkg){Integer id=RESOURCES.get(name);return id==null?0:id;}
    };
    private static final class Square extends SquareDeviceCardView {@Override public android.content.res.Resources getResources(){return DEVICE_RESOURCES;}}
    private static final class Rectangle extends com.oplus.deviceplugin.sdk.ui.view.separatecardview.RectangleDeviceCardView{
        public final BatteryCircularProgressLayout p=new BatteryCircularProgressLayout();Rectangle(){p.parent=body;}
        @Override public android.content.res.Resources getResources(){return DEVICE_RESOURCES;}
    }
    public static final class Animation {
        public boolean running=true;public final List<Animator.AnimatorListener> listeners=new ArrayList<>();
        public boolean isRunning(){return running;}public void addListener(Animator.AnimatorListener listener){listeners.add(listener);}public void removeListener(Animator.AnimatorListener listener){listeners.remove(listener);}
        void finish(){running=false;for(Animator.AnimatorListener l:new ArrayList<>(listeners))l.onAnimationEnd(null);}
    }
    private static void devices(){
        Port port=new Port();IconNativeControlCenter adapter=new IconNativeControlCenter(port);adapter.configure(true);Square card=new Square();card.q=new PanelDevice(1);
        View ring=card.p.c;ImageView charge=card.p.d;Native charging=new Native();charge.setImageDrawable(charging);
        for(DeviceType type:new DeviceType[]{DeviceType.PHONE,DeviceType.PAD,DeviceType.COMPUTER,DeviceType.HEADSET,DeviceType.TV,DeviceType.WATCH,DeviceType.WRISTBAND}){
            String name=type.name().toLowerCase(java.util.Locale.ROOT);card.q.type=type;card.p.f.setImageDrawable(new Native());adapter.onDeviceIcon(card.p,RESOURCES.get("device_card_"+name));equal("device."+name+".default",role(card.p.f.getDrawable()));
            equal(ring,card.p.c);equal(charging,charge.getDrawable());
        }
        card.q.type=DeviceType.PHONE;card.q.iconUri="content://remote/photo";Native photo=new Native();card.p.f.setImageDrawable(photo);adapter.onDeviceIcon(card.p,700);equal(photo,card.p.f.getDrawable());
        card.q.iconUri=null;card.q.type=DeviceType.IPHONE;adapter.onDeviceIcon(card.p,700);equal(photo,card.p.f.getDrawable());card.q.type=DeviceType.PHONE;
        adapter.onDeviceIcon(card.p,9999);equal(photo,card.p.f.getDrawable());adapter.onDeviceIcon(card.p,700);equal("device.phone.default",role(card.p.f.getDrawable()));
        adapter.onDeviceDrawable(card.p);equal(photo,card.p.f.getDrawable());adapter.onDeviceUpdated(card);equal(photo,card.p.f.getDrawable());
        Animation animation=new Animation();card.p.k=animation;card.p.queuePostedForCheck=true;adapter.onDeviceIcon(card.p,700);equal(photo,card.p.f.getDrawable());equal(1,animation.listeners.size());
        Native nextFrame=new Native();card.p.f.setImageDrawable(nextFrame);animation.finish();equal(0,animation.listeners.size());card.p.runPostedForCheck();equal("device.phone.default",role(card.p.f.getDrawable()));
        Rectangle rectangle=new Rectangle();rectangle.v=new PanelDevice(1);rectangle.v.type=DeviceType.PAD;Native pad=new Native();rectangle.p.f.setImageDrawable(pad);adapter.onDeviceIcon(rectangle.p,701);equal("device.pad.default",role(rectangle.p.f.getDrawable()));
        SquareEntranceCardView square=new SquareEntranceCardView();RectangleEntranceCardView wide=new RectangleEntranceCardView();Native s=new Native(),w=new Native();square.m.setImageDrawable(s);wide.p.setImageDrawable(w);
        adapter.onDeviceUpdated(square);adapter.onDeviceUpdated(wide);equal("device.entrance.default",role(square.m.getDrawable()));equal("device.entrance.default",role(wide.p.getDrawable()));
        adapter.releaseRuntime();equal(nextFrame,card.p.f.getDrawable());equal(pad,rectangle.p.f.getDrawable());equal(s,square.m.getDrawable());equal(w,wide.p.getDrawable());equal(0,card.p.f.attachListeners.size());
        adapter.onDeviceIcon(card.p,700);equal(nextFrame,card.p.f.getDrawable());
    }
    public static void main(String[] args)throws Exception{brightness();nativeMetadata();runtimeMetadata();volume();volumeLottie();footer();media();devices();System.out.println("IconNativeControlCenter checks passed: "+checks);}
}
