package dev.puitheme;
import android.os.Bundle;
import android.view.View;
import com.coui.appcompat.seekbar.COUIVerticalSeekBar;
import com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar;
import com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView;
import com.oplus.systemui.qs.media.multilight.OplusQsMediaBackgroundDrawable;
import com.oplus.systemui.plugins.drawable.PluginDrawable;
import com.oplus.systemui.plugins.drawable.MixColorPluginDrawable;
import com.oplusos.systemui.common.blurability.BlurConfig;
import com.oplusos.systemui.common.blurability.ViewBlurProxy;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;
import com.oplus.deviceplugin.sdk.ui.view.separatecardview.*;
import com.oplus.deviceplugin.sdk.utils.PluginUtils;
import com.oplusos.systemui.common.outline.CornerOutlineProvider;
import com.oplus.systemui.qs.base.res.util.QSConstant;

/** Runs the compiled native panel adapter against exact native surface and setter contracts. */
public final class QsPanelCornersCheck {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void near(float want,Object value,String label){check(value instanceof Number&&Math.abs(((Number)value).floatValue()-want)<.0001f,label+" wanted="+want+" actual="+value);}
    private static Bundle settings(boolean on,float radius){Bundle b=new Bundle();b.putBoolean(QsTileCorners.MASTER,on);b.putFloat(QsTileCorners.RADIUS,radius);return b;}
    private static final class Slider extends OplusQsVerticalSeekBar {
        int width=180,height=500;
        public int getWidth(){return width;}public int getHeight(){return height;}
    }
    private static void slider(){
        QsPanelCorners corners=new QsPanelCorners();Slider slider=new Slider();slider.getResources().getDisplayMetrics().density=2f;
        corners.onNativeUpdate(slider);near(56f,slider.blurRadius,"initial native blur");
        corners.configure(settings(true,24f));near(48f,slider.blurRadius,"true blur radius uses density");
        slider.mCurBackgroundRadius=67.2f;slider.mCurProgressRadius=61.6f;slider.mThumbOutRadius=72.8f;
        float[] saved={slider.mBackgroundRadius,slider.mProgressRadius,slider.mCurBackgroundRadius,slider.mCurProgressRadius,slider.mThumbOutRadius};
        QsPanelCorners.DrawScope scope=corners.beginDraw(slider);
        near(saved[0],slider.mBackgroundRadius,"native background geometry field preserved");near(saved[2],slider.mCurBackgroundRadius,"native press background geometry preserved");
        near(saved[3],slider.mCurProgressRadius,"native active endpoint geometry preserved");near(saved[4],slider.mThumbOutRadius,"native thumb position preserved");
        near(50f,corners.blurRadius(slider,slider.mCurProgressRadius),"blur keeps native press ratio and actual track half-width bound");
        QsPanelCorners.DrawScope nested=corners.beginDraw(slider);nested.close();near(saved[3],slider.mCurProgressRadius,"nested scope leaves native geometry");
        scope.close();scope.close();
        near(saved[0],slider.mBackgroundRadius,"base restored");near(saved[1],slider.mProgressRadius,"active base restored");
        near(saved[2],slider.mCurBackgroundRadius,"press restored");near(saved[3],slider.mCurProgressRadius,"active press restored");near(saved[4],slider.mThumbOutRadius,"thumb restored");
        corners.configure(settings(false,24f));near(61.6f,slider.blurRadius,"off restores original press blur, not temporary custom");
        COUIVerticalSeekBar ordinary=new COUIVerticalSeekBar();corners.configure(settings(true,0f));corners.beginDraw(ordinary).close();near(56f,ordinary.mCurProgressRadius,"other COUI controls excluded");
        scope=corners.beginDraw(slider);near(61.6f,slider.mCurProgressRadius,"zero radius keeps the native full track endpoint");near(0f,slider.blurRadius,"zero only changes native blur curvature");scope.close();
        corners.configure(settings(true,80f));scope=corners.beginDraw(slider);near(61.6f,slider.mCurProgressRadius,"maximum radius keeps native geometry");near(50f,slider.blurRadius,"actual blur track half width bounds curvature");scope.close();
        slider.width=0;scope=corners.beginDraw(slider);near(61.6f,slider.mCurProgressRadius,"unmeasured native fallback");scope.close();slider.width=180;
        Bundle safe=settings(true,18f);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);corners.configure(safe);near(61.6f,slider.blurRadius,"safe restores original native blur");
        corners.beginDraw(slider).close();near(61.6f,slider.mCurProgressRadius,"safe leaves native path");
        safe.putBoolean(StatusBarSettings.SAFE_MODE,false);corners.configure(safe);scope=corners.beginDraw(slider);near(61.6f,slider.mCurProgressRadius,"safe exit leaves native geometry");near(39.6f,slider.blurRadius,"safe exit rebinds animated curvature");scope.close();
        corners.detach(slider);near(61.6f,slider.blurRadius,"detach restores blur");
        corners.configure(settings(true,12f));slider.failRadiusCalls=1;scope=corners.beginDraw(slider);scope.close();
        near(61.6f,slider.mCurProgressRadius,"partial native failure restores fields");near(61.6f,slider.blurRadius,"partial native failure restores blur");
        check(!corners.isDrawingShapes(),"failed scope releases final shape identity gate");
    }

    /** Exact COUI DEX geometry is invariant while final paths change curvature. */
    private static void sliderShapes(){
        QsPanelCorners corners=new QsPanelCorners();Slider slider=new Slider();slider.getResources().getDisplayMetrics().density=2f;
        slider.mCurProgressRadius=61.6f;slider.mCurBackgroundRadius=67.2f;slider.mThumbOutRadius=72.8f;
        float nativeTop=100f-slider.mCurProgressRadius,nativeHeight=300f+2f*slider.mCurProgressRadius;
        android.graphics.Canvas canvas=new android.graphics.Canvas();corners.configure(settings(true,24f));
        QsPanelCorners.DrawScope draw=corners.beginDraw(slider,canvas);
        near(nativeTop,100f-slider.mCurProgressRadius,"native track top does not shrink at custom radius");
        near(nativeHeight,300f+2f*slider.mCurProgressRadius,"native track height does not shrink at custom radius");
        android.graphics.RectF rect=new android.graphics.RectF(10f,20f,190f,520f);
        Object[] progress={rect,61.6f,61.6f,new Object()};
        QsPanelCorners.ShapeScope shape=corners.shape(slider.mClipProgressPath,"addRoundRect",progress);
        near(52.8f,shape.args[1],"final active curve uses press ratio");near(52.8f,shape.args[2],"final active curve both axes");
        check(shape.args[0]==rect&&rect.top==20f&&rect.bottom==520f,"native final shape rectangle not altered");
        near(61.6f,progress[1],"native shape input args are not mutated");
        QsPanelCorners.ShapeScope nested=corners.shape(slider.mClipProgressPath,"addRoundRect",shape.args);
        check(nested.args==shape.args,"nested OEM/Android shape not scaled twice");nested.close();shape.close();
        Object wrapper=new Object();corners.onShapeWrapper(wrapper,slider.mBackgroundPath);
        Object[] smooth={rect,67.2f,.7f,new Object()};shape=corners.shape(wrapper,"addSmoothRoundRect",smooth);
        near(57.6f,shape.args[1],"bound native OEM path wrapper curvature");near(.6f,shape.args[2],"legacy smooth weight shares tile continuous template");shape.close();
        Object[] standard={rect,67.2f,67.2f,slider.mBackgroundPaint};
        shape=corners.shape(canvas,"drawRoundRect",standard);near(57.6f,shape.args[1],"native plain roundrect paint identity");shape.close();
        shape=corners.shape(new android.graphics.Canvas(),"drawRoundRect",standard);check(shape.args==standard,"another canvas never rewritten");shape.close();
        Object[] icon={rect,9f,9f,new android.graphics.Paint(1)};shape=corners.shape(canvas,"drawRoundRect",icon);check(shape.args==icon,"content/icon paint never rewritten");shape.close();
        shape=corners.shape(new android.graphics.Path(),"addRoundRect",progress);check(shape.args==progress,"other path never rewritten");shape.close();
        android.graphics.Rect blurRect=new android.graphics.Rect(0,12,100,400);Object material=new Object();
        Object[] config={slider,blurRect,61.6f,.9f,material,false,1.05f,true};Object[] altered=corners.blurShape("applySeekBarActiveBlurConfig",config);
        near(50f,altered[2],"native blur factory shape bounded to native rectangle");check(altered[1]==blurRect&&altered[4]==material&&altered[6].equals(1.05f),"blur rectangle material and mirror scale retained");
        Object[] initial={slider,blurRect,61.6f,.9f,false,true,material,new Object()};altered=corners.blurShape("createSeekBarBlurDrawable",initial);
        near(50f,altered[2],"initial native factory uses custom curve without waiting for update setter");check(altered[1]==blurRect&&altered[6]==material,"initial factory keeps native geometry and light template");
        QsPanelCorners.BlurScope setter=corners.beginBlurUpdate(slider,61.6f);config[2]=setter.radius;altered=corners.blurShape("applySeekBarActiveBlurConfig",config);near(50f,altered[2],"setter forwarding does not reapply press ratio");setter.close();
        corners.configure(settings(true,0f));shape=corners.shape(slider.mClipProgressPath,"addRoundRect",progress);near(0f,shape.args[1],"zero final path curvature");shape.close();
        near(nativeTop,100f-slider.mCurProgressRadius,"zero keeps native top");near(nativeHeight,300f+2f*slider.mCurProgressRadius,"zero keeps full native height and icon geometry");
        corners.configure(settings(true,80f));android.graphics.RectF tiny=new android.graphics.RectF(0,0,80,16);
        float[] perCorner={0f,0f,56f,56f,56f,56f,0f,0f};Object[] array={tiny,perCorner,new Object()};shape=corners.shape(slider.mClipProgressPath,"addRoundRect",array);
        float[] next=(float[])shape.args[1];near(8f,next[2],"active segment half-height bounds curve");near(0f,next[0],"native square junction stays square");near(56f,perCorner[2],"native corner array not mutated");shape.close();
        draw.close();check(!corners.isDrawingShapes(),"draw scope releases identities");shape=corners.shape(slider.mClipProgressPath,"addRoundRect",progress);check(shape.args==progress,"owned path outside exact draw is native");shape.close();
        corners.configure(settings(false,24f));draw=corners.beginDraw(slider,canvas);shape=corners.shape(slider.mClipProgressPath,"addRoundRect",progress);check(shape.args==progress,"default/off native final shapes");shape.close();draw.close();
    }
    private static void media(){
        QSConstant.reset();QsPanelCorners corners=new QsPanelCorners();OplusQsBaseMediaPanelView view=new OplusQsBaseMediaPanelView();view.getResources().getDisplayMetrics().density=2f;view.body.getResources().getDisplayMetrics().density=2f;
        OplusQsMediaBackgroundDrawable old=view.transition;CornerOutlineProvider original=old.getPathProvider();Object shader=old.shader,glass=old.glass;
        corners.onNativeUpdate(view);corners.configure(settings(true,24f));near(48f,old.lightRadius,"native media light params");near(48f,corners.cornerRadius(view,56f),"native media edge getter");
        check(original!=old.getPathProvider(),"native media path replaced");check(shader==old.shader&&glass==old.glass,"native media materials retained");check(old.alpha==173&&old.color==0xff123456,"native media colors retained");
        CornerOutlineProvider custom=old.getPathProvider();
        old.setCornerParams(56f,.8f);corners.onNativeUpdate(view);
        near(48f,old.lightRadius,"native light reset is reconciled even when provider identity is unchanged");
        check(custom==old.getPathProvider(),"native media reconciliation retains the cached instance provider");
        CornerOutlineProvider themed=new CornerOutlineProvider(58f,.9f);old.setPathProvider(themed);old.invalidatePath();old.setCornerParams(58f,.9f);corners.onNativeUpdate(view);
        corners.configure(settings(false,24f));check(old.getPathProvider()==themed&&old.childProvider==themed,"native latest themed media provider restored");near(58f,old.lightRadius,"native latest themed media light params restored");near(.9f,old.lightWeight,"native latest weight restored");
        corners.configure(settings(true,15f));view.transition=new OplusQsMediaBackgroundDrawable(original);corners.onNativeUpdate(view);near(58f,old.lightRadius,"retired media drawable restored");near(30f,view.transition.lightRadius,"replacement media drawable bound");
        Bundle safe=settings(true,15f);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);corners.configure(safe);near(56f,view.transition.lightRadius,"safe media light native");check(view.transition.getPathProvider()==original,"safe native provider");
        OplusQsBaseMediaPanelView bornSafe=new OplusQsBaseMediaPanelView();bornSafe.body.getResources().getDisplayMetrics().density=2f;corners.onNativeUpdate(bornSafe);safe.putBoolean(StatusBarSettings.SAFE_MODE,false);corners.configure(safe);near(30f,bornSafe.transition.lightRadius,"safe boot media registered for hot exit");
        corners.configure(settings(true,80f));near(60f,view.transition.lightRadius,"media radius bounded by height");corners.configure(settings(true,0f));near(0f,view.transition.lightRadius,"media native path square");
        view.attached=false;corners.onNativeUpdate(view);near(56f,view.transition.lightRadius,"detached media native");view.attached=true;corners.onNativeUpdate(view);near(0f,view.transition.lightRadius,"reattach media path");
        corners.detach(view);near(56f,view.transition.lightRadius,"media explicit detach native");
        QsPanelCorners failure=new QsPanelCorners();OplusQsBaseMediaPanelView bad=new OplusQsBaseMediaPanelView();CornerOutlineProvider nativeProvider=bad.transition.getPathProvider();bad.transition.failCorners=1;failure.configure(settings(true,10f));failure.onNativeUpdate(bad);
        check(nativeProvider==bad.transition.getPathProvider(),"partial media setter native path restored");near(56f,bad.transition.lightRadius,"partial media setter light restored");
    }
    private static void devices(){
        PluginUtils.radius=56f;PluginUtils.weight=1f;PluginUtils.fail=false;
        DeviceCardFixture[] cards={new RectangleDeviceCardView(),new SquareDeviceCardView(),new NoDeviceEntranceCardView(),new RectangleEntranceCardView(),new SquareEntranceCardView()};
        for(DeviceCardFixture card:cards){
            QsPanelCorners corners=new QsPanelCorners();PluginDrawable plugin=new PluginDrawable();card.body.setBackground(plugin);card.setBlurDrawable(plugin);
            card.setOutlineProvider(plugin.getPathProvider());card.setClipToOutline(true);card.body.setOutlineProvider(card.outline);card.body.setClipToOutline(true);
            card.getResources().getDisplayMetrics().density=2f;Object glass=plugin.glass,shader=plugin.shader;
            corners.onNativeUpdate(card);corners.configure(settings(true,10f));near(20f,plugin.radius,"device native glass geometry");near(20f,card.outline.radius,"device native nonblur outline geometry");near(.6f,plugin.weight,"device uses common native continuous template");
            check(card.body.background==plugin&&plugin.glass==glass&&plugin.shader==shader,"device native materials and body retained");check(plugin.alpha==173&&plugin.color==0xff345678,"device paint untouched");check(card.outer!=card.body.background,"outer card untouched");
            PluginUtils.weight=0f;PluginUtils.radius=57f;plugin.setCornerRadius(57f,0f);card.outline.setSmoothCorner(new com.oplus.deviceplugin.sdk.entity.SmoothRoundCorner(57f,0f));corners.onNativeUpdate(card);near(.6f,plugin.weight,"native theme refresh keeps uniform custom continuous weight");
            PluginDrawable replacement=new PluginDrawable();replacement.setCornerRadius(57f,0f);card.body.setBackground(replacement);card.setBlurDrawable(replacement);
            corners.onNativeUpdate(card);near(20f,plugin.pathProvider.radius,"old outer outline remains live and uses custom radius after native g replaces background");near(20f,replacement.radius,"new native plugin bound");
            check(card.getOutlineProvider()==plugin.getPathProvider(),"native outer provider reference not replaced by a new clipping layer");
            card.setOutlineProvider(replacement.getPathProvider());corners.onNativeUpdate(card);near(57f,plugin.radius,"retired old outer outline restores its own latest native radius");
            corners.configure(settings(false,10f));near(57f,replacement.radius,"off native device restored");near(57f,card.outline.radius,"off native outline restored");near(0f,card.outline.weight,"off native weight restored");
            Bundle safe=settings(true,10f);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);corners.configure(safe);near(57f,replacement.radius,"safe native card retained");safe.putBoolean(StatusBarSettings.SAFE_MODE,false);corners.configure(safe);near(20f,replacement.radius,"safe hot exit card");
            corners.configure(settings(true,0f));near(0f,replacement.radius,"square device glass geometry");near(0f,card.outline.radius,"square device outline");
            corners.configure(settings(true,80f));near(40f,replacement.radius,"device half-size bound");corners.detach(card);near(57f,replacement.radius,"device detach restore");
            card.bodyWidth=0;card.bodyHeight=0;corners.onNativeUpdate(card);near(57f,replacement.radius,"unmeasured body falls back native");
            check(card.getClipToOutline()&&card.body.getClipToOutline()&&card.body.getClipBounds()==null,"native clipping mode and content bounds unchanged");
            PluginUtils.weight=1f;PluginUtils.radius=56f;
        }
    }
    private static void nativeGeometry(){
        QSConstant.reset();QSConstant.mappedScale=1.5f;
        QsPanelCorners corners=new QsPanelCorners();OplusQsBaseMediaPanelView media=new OplusQsBaseMediaPanelView();
        media.body.getResources().getDisplayMetrics().density=2f;
        CornerOutlineProvider nativeProvider=media.transition.getPathProvider();
        corners.configure(new Bundle());corners.onNativeUpdate(media);
        check(nativeProvider==media.transition.getPathProvider(),"missing master leaves native media path");
        corners.configure(settings(true,45.5f));near(60f,media.transition.lightRadius,"factory mapped radius normalized to actual half-height");
        near(60f,media.transition.getPathProvider().radius,"media glass and colored path agree");
        com.oplus.systemui.plugins.qs.customize.view.viewholder.EditablePluginViewHolder$realPluginContainer$2$1 wrapper=
            new com.oplus.systemui.plugins.qs.customize.view.viewholder.EditablePluginViewHolder$realPluginContainer$2$1();
        wrapper.addView(media);near(60f,corners.editableRadius(wrapper,56f),"wrapper spotlight matches owned media edge");
        near(56f,wrapper.viewRadius,"native wrapper field untouched");check(wrapper.getClipChildren(),"native wrapper child clipping unchanged");
        CornerOutlineProvider launch=new CornerOutlineProvider(28f,.4f);
        media.transition.setBlockPathProvider(launch);corners.onDrawableUpdate(media.transition);
        check(media.transition.getPathProvider()==launch,"native launch owns effective media outline");
        near(28f,media.transition.lightRadius,"native launch light path matches effective provider");
        near(56f,corners.editableRadius(wrapper,56f),"native wrapper spotlight preserved during external launch");
        corners.configure(settings(false,45.5f));near(28f,media.transition.lightRadius,"off does not replace native launch light geometry");
        media.transition.setBlockPathProvider(null);corners.onDrawableUpdate(media.transition);
        check(nativeProvider==media.transition.getPathProvider(),"off restores static native media path behind launch");
        near(56f,media.transition.lightRadius,"off restores matching media light path after launch clears");
        corners.configure(settings(true,10f));near(20f,media.transition.lightRadius,"exact dp radius retains smooth weight");
        check(media.body.layoutListeners.size()==1&&media.layoutListeners.size()==1,"body and owner observe native layouts");
        media.bodyHeight=16;media.body.dispatchLayoutChange(240,16,240,120);
        near(8f,media.transition.lightRadius,"body layout updates half-size bounds without resource callbacks");
        media.bodyHeight=0;media.bodyWidth=0;media.measuredWidth=240;media.measuredHeight=64;
        corners.configure(settings(true,80f));near(32f,media.transition.lightRadius,"first onMeasure uses new measured background size");
        corners.configure(settings(true,0f));near(0f,media.transition.lightRadius,"zero radius colored path");
        near(0f,media.transition.getPathProvider().radius,"zero radius native glass path");
        near(0f,corners.editableRadius(wrapper,56f),"zero radius wrapper spotlight");
        check(media.body.getClipBounds()==null&&media.body.layoutRequests==0,"contents and click layout never cropped or resized");
        Bundle safe=settings(true,0f);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);corners.configure(safe);
        near(56f,corners.editableRadius(wrapper,56f),"safe restores native wrapper spotlight");
        near(56f,media.transition.lightRadius,"safe restores native media geometry");
        safe.putBoolean(StatusBarSettings.SAFE_MODE,false);corners.configure(safe);near(0f,media.transition.lightRadius,"hot exit reapplies media square");
        corners.detach(media);check(media.layoutListeners.isEmpty()&&media.body.layoutListeners.isEmpty(),"detach releases native layout observers");
        near(56f,corners.editableRadius(wrapper,56f),"detached wrapper native");
        QSConstant.reset();PluginUtils.radius=56f;PluginUtils.weight=1f;
        RectangleDeviceCardView device=new RectangleDeviceCardView();PluginDrawable glass=new PluginDrawable();device.body.setBackground(glass);
        device.getResources().getDisplayMetrics().density=2f;
        com.oplus.systemui.plugins.qs.customize.view.viewholder.EditablePluginViewHolder$realPluginContainer$2$1 deviceWrapper=
            new com.oplus.systemui.plugins.qs.customize.view.viewholder.EditablePluginViewHolder$realPluginContainer$2$1();
        deviceWrapper.addView(device);corners.configure(settings(true,10f));corners.onNativeUpdate(device);
        near(20f,corners.editableRadius(deviceWrapper,56f),"device wrapper native spotlight matches drawable");
        check(device.invalidations>0,"outer device clip outline invalidated with body");
        corners.configure(settings(false,10f));near(56f,corners.editableRadius(deviceWrapper,56f),"device wrapper restores native on off");
        android.view.ViewGroup unrelated=new android.view.ViewGroup(new android.content.Context());unrelated.addView(device);
        corners.configure(settings(true,10f));near(56f,corners.editableRadius(unrelated,56f),"only exact native wrapper is adapted");
        corners.detach(device);
    }

    private static void deviceSurfaceOwnership(){
        QsPanelCorners corners=new QsPanelCorners();RectangleDeviceCardView card=new RectangleDeviceCardView();card.getResources().getDisplayMetrics().density=1f;
        PluginDrawable oldOuter=new PluginDrawable(),current=new PluginDrawable();oldOuter.setCornerRadius(79f,null);current.setCornerRadius(53f,.7f);
        card.setOutlineProvider(oldOuter.getPathProvider());card.setClipToOutline(true);card.setBlurDrawable(current);card.body.setBackground(current);
        card.outline.setSmoothCorner(new com.oplus.deviceplugin.sdk.entity.SmoothRoundCorner(51f,.4f));card.body.setOutlineProvider(card.outline);card.body.setClipToOutline(true);
        card.resources.bodyPackage="com.oplus.deviceplugin";card.resources.bodyName="overlaid_resource_name";
        card.foreground.setCornerRadius(11f);card.icon.setCornerRadius(8f);
        corners.configure(settings(true,0f));corners.onNativeUpdate(card);
        near(0f,oldOuter.pathProvider.radius,"zero reaches the actual old outer clipped outline");near(0f,current.radius,"zero reaches current native body glass");near(0f,card.outline.radius,"zero reaches independent native smooth outline");
        near(.6f,oldOuter.weight,"old outer uses common native continuous template");near(.6f,current.weight,"current glass uses common continuous template");near(.6f,card.outline.weight,"smooth outline agrees with native glass");
        near(11f,card.foreground.getCornerRadius(),"foreground and click decoration not modified");near(8f,card.icon.getCornerRadius(),"device icon drawable not modified");
        check(card.body.layoutRequests==0&&card.layoutRequests==0&&card.body.getClipBounds()==null,"device contents and input layout retained");
        check(card.getOutlineProvider()==oldOuter.getPathProvider()&&card.body.getOutlineProvider()==card.outline,"actual native provider references retained");
        corners.configure(settings(false,0f));near(79f,oldOuter.pathProvider.radius,"off restores old outer's own native radius");check(oldOuter.weight==null,"off restores old outer null weight");near(53f,current.radius,"off restores current glass's independent original");near(51f,card.outline.radius,"off restores independent smooth original");
        corners.configure(settings(true,10f));oldOuter.pathProvider.update(78f,.2f);current.setCornerRadius(52f,.8f);card.outline.setSmoothCorner(new com.oplus.deviceplugin.sdk.entity.SmoothRoundCorner(50f,.5f));corners.onNativeUpdate(card);
        Bundle safe=settings(true,10f);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);corners.configure(safe);near(78f,oldOuter.radius,"safe restores latest original old outline");near(52f,current.radius,"safe restores latest native theme glass");near(50f,card.outline.radius,"safe restores latest native smooth shape");
        safe.putBoolean(StatusBarSettings.SAFE_MODE,false);corners.configure(safe);near(10f,current.radius,"safe exit rebinds surface radius");
        android.graphics.drawable.GradientDrawable theme=new android.graphics.drawable.GradientDrawable();float[] nativeCorners={13f,13f,17f,17f,19f,19f,23f,23f};theme.setCornerRadii(nativeCorners);theme.setColor(0xff123456);
        card.body.setBackground(theme);card.setBlurDrawable(null);card.setOutlineProvider(null);card.setClipToOutline(false);corners.onNativeUpdate(card);near(10f,theme.getCornerRadius(),"native theme fill curvature changes without replacing its paint");
        near(52f,current.radius,"retired native blur restored even when theme becomes nonblur");check(card.body.background==theme&&theme.mFillPaint.getColor()==0xff123456,"theme drawable and color preserved");
        corners.configure(settings(false,10f));float[] restored=theme.getCornerRadii();check(java.util.Arrays.equals(nativeCorners,restored),"native asymmetric theme corners exactly restored");check(!card.getClipToOutline()&&card.body.getClipToOutline(),"native global clipping choice remains unchanged");
        corners.detach(card);check(card.layoutListeners.isEmpty()&&card.body.layoutListeners.isEmpty(),"device detach releases native observers");
        RectangleDeviceCardView broken=new RectangleDeviceCardView();broken.getResources().getDisplayMetrics().density=1f;
        PluginDrawable bad=new PluginDrawable(),old=new PluginDrawable();bad.setCornerRadius(55f,.6f);old.setCornerRadius(77f,null);
        broken.body.setBackground(bad);broken.setBlurDrawable(bad);broken.setOutlineProvider(old.getPathProvider());
        broken.outline.setSmoothCorner(new com.oplus.deviceplugin.sdk.entity.SmoothRoundCorner(54f,.3f));
        QsPanelCorners failure=new QsPanelCorners();failure.configure(settings(true,0f));bad.failCalls=1;failure.onNativeUpdate(broken);
        near(55f,bad.radius,"partial native setter failure restores own background");near(77f,old.radius,"partial native failure restores old outer independently");near(54f,broken.outline.radius,"partial native failure restores other surfaces");
        failure.onNativeUpdate(broken);near(0f,bad.radius,"next native callback retries after partial failure");near(0f,old.radius,"successful retry owns actual outer");
        bad.fail=true;failure.configure(settings(false,0f));near(55f,bad.radius,"even throwing restoration keeps native background value");near(77f,old.radius,"throwing restoration still visits old outer");near(54f,broken.outline.radius,"throwing restoration still visits smooth surface");
        bad.fail=false;failure.detach(broken);check(broken.layoutListeners.isEmpty(),"successful retry detach releases failed owner");
    }
    private static void actualPluginBinding() {
        QsPanelCorners corners=new QsPanelCorners();
        com.oplus.systemui.plugins.qs.customize.view.viewholder.EditablePluginViewHolder$realPluginContainer$2$1 host=
                new com.oplus.systemui.plugins.qs.customize.view.viewholder.EditablePluginViewHolder$realPluginContainer$2$1();
        android.view.ViewGroup node=host;
        for(int i=0;i<6;i++){android.view.ViewGroup child=new android.view.ViewGroup(new android.content.Context());node.addView(child);node=child;}
        RectangleDeviceCardView headset=new RectangleDeviceCardView();
        SquareDeviceCardView laptop=new SquareDeviceCardView();
        SquareEntranceCardView entrance=new SquareEntranceCardView();
        for(com.oplus.deviceplugin.sdk.ui.view.separatecardview.DeviceCardFixture card:new com.oplus.deviceplugin.sdk.ui.view.separatecardview.DeviceCardFixture[]{headset,laptop,entrance}) {
            card.getResources().getDisplayMetrics().density=1f;
            PluginDrawable bg=new PluginDrawable();bg.setCornerRadius(56f,.5f);card.body.setBackground(bg);card.setBlurDrawable(bg);node.addView(card);
        }
        java.util.List<View> actual=QsPanelCorners.pluginControls(host);
        check(actual.size()==3&&actual.contains(headset)&&actual.contains(laptop)&&actual.contains(entrance),"native binding finds all actual cards beyond old depth four");
        android.view.ViewGroup unrelated=new android.view.ViewGroup(new android.content.Context());
        check(QsPanelCorners.pluginControls(unrelated).isEmpty(),"ordinary View trees are never scanned");
        corners.configure(settings(true,0f));for(View card:actual)corners.onNativeUpdate(card);
        for(com.oplus.deviceplugin.sdk.ui.view.separatecardview.DeviceCardFixture card:new com.oplus.deviceplugin.sdk.ui.view.separatecardview.DeviceCardFixture[]{headset,laptop,entrance}) {
            near(0f,((PluginDrawable)card.body.getBackground()).radius,"each bound device background becomes square");
            check(card.getClipBounds()==null&&card.layoutRequests==0,"plugin binding preserves each card's content and click geometry");
        }
        near(0f,corners.editableRadius(host,56f),"deep plugin spotlight uses actual card radius");
        Bundle safe=settings(true,0f);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);corners.configure(safe);
        for(View card:actual){near(56f,((PluginDrawable)((com.oplus.deviceplugin.sdk.ui.view.separatecardview.DeviceCardFixture)card).body.getBackground()).radius,"safe restores all bound children independently");corners.detach(card);}
        for(View card:actual)check(card.layoutListeners.isEmpty(),"plugin recycling releases every child's observers");
    }
    private static float[] curves(BlurConfig config){return new float[]{config.cornerRadius,config.leftTopCornerRadius,config.rightTopCornerRadius,config.rightBottomCornerRadius,config.leftBottomCornerRadius};}
    private static void nativeCurves(float[] wanted,BlurConfig config,String label){float[] actual=curves(config);for(int i=0;i<5;i++)near(wanted[i],actual[i],label+" config field "+i);}
    private static void visibleCurves(float[] wanted,ViewBlurProxy proxy,String label){near(wanted[0],proxy.visibleScalar,label+" visible scalar");for(int i=0;i<4;i++)near(wanted[i+1],proxy.visibleCorners[i],label+" visible corner "+i);}
    private static float[] uniform(float value){return new float[]{value,value,value,value,value};}
    private static MixColorPluginDrawable bindMix(DeviceCardFixture card){
        MixColorPluginDrawable mix=new MixColorPluginDrawable();card.body.setBackground(mix);card.setBlurDrawable(mix);
        card.setOutlineProvider(mix.getPathProvider());card.body.setOutlineProvider(card.outline);
        card.getResources().getDisplayMetrics().density=2f;return mix;
    }
    /** Device MixColor visible blur ignores the provider radius in several real native blur modes. */
    private static void deviceBlurGeometry(){
        DeviceCardFixture[] cards={new RectangleDeviceCardView(),new SquareDeviceCardView(),new NoDeviceEntranceCardView(),new RectangleEntranceCardView(),new SquareEntranceCardView()};
        for(DeviceCardFixture card:cards){
            QsPanelCorners corners=new QsPanelCorners();MixColorPluginDrawable mix=bindMix(card);
            BlurConfig config=mix.blur().viewBlurProxy.getBlurConfig();ViewBlurProxy proxy=mix.blur().viewBlurProxy;
            float[] original=curves(config);Object material=config.material,colors=config.mixColors,template=config.lightTemplate;
            mix.setCornerRadius(0f,.7f);near(0f,mix.radius,"provider-only old approach can report square");
            visibleCurves(original,proxy,"provider-only old approach leaves native visible blur capsule");
            mix.setCornerRadius(56f,.7f);corners.onNativeUpdate(card);nativeCurves(original,config,"default off leaves independent native blur");
            corners.configure(settings(true,0f));nativeCurves(uniform(0f),config,"zero reaches all five native config fields");visibleCurves(uniform(0f),proxy,"zero actually applies visible blur shape");
            near(0f,mix.radius,"zero outline agrees with visible blur");near(.6f,mix.weight,"native shape uses common continuous template");
            corners.configure(settings(true,34.5f));nativeCurves(uniform(40f),config,"dp target bounded by actual native body half-height");visibleCurves(uniform(40f),proxy,"bounded target reaches native blur");
            corners.configure(settings(true,80f));visibleCurves(uniform(40f),proxy,"80dp uses native physical bound");
            check(config.material==material&&config.mixColors==colors&&config.lightTemplate==template&&config.blurAmount==35f,"native blur material color light and amount unchanged");
            check(card.body.background==mix&&card.body.getClipBounds()==null&&card.layoutRequests==0&&card.body.layoutRequests==0,"native contents click layout and background retained");
            corners.configure(settings(false,80f));nativeCurves(original,config,"off restores five independent original blur fields");visibleCurves(original,proxy,"off reapplies original visible blur shape");near(56f,mix.radius,"off restores independent original outline");
            corners.configure(settings(true,0f));Bundle safe=settings(true,0f);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);corners.configure(safe);
            nativeCurves(original,config,"safe restores native blur config");visibleCurves(original,proxy,"safe restores native visible silhouette");
            safe.putBoolean(StatusBarSettings.SAFE_MODE,false);corners.configure(safe);visibleCurves(uniform(0f),proxy,"safe exit reapplies native blur target");
            corners.detach(card);nativeCurves(original,config,"detach restores native config");visibleCurves(original,proxy,"detach restores native visible shape");
        }
    }
    private static void deviceBlurUpdatesAndRollback() throws Exception {
        RectangleDeviceCardView card=new RectangleDeviceCardView();MixColorPluginDrawable mix=bindMix(card);
        ViewBlurProxy proxy=mix.blur().viewBlurProxy;BlurConfig config=proxy.getBlurConfig();float[] original=curves(config);
        QsDeviceCorners owner=new QsDeviceCorners();owner.refresh(card,0f,QSConstant.weight);
        config.cornerRadius=71f;config.rightTopCornerRadius=73f;proxy.applyBlurConfig();owner.refresh(card,0f,QSConstant.weight);owner.restore();
        float[] updated=original.clone();updated[0]=71f;updated[2]=73f;
        nativeCurves(updated,config,"native scalar and single corner update preserve untouched original corners");visibleCurves(updated,proxy,"latest native transition is reapplied on restore");
        owner.refresh(card,0f,QSConstant.weight);BlurConfig replacement=new BlurConfig();replacement.setCornerRadius(77f);proxy.blurConfig=replacement;proxy.applyBlurConfig();owner.refresh(card,0f,QSConstant.weight);
        nativeCurves(updated,config,"retired config restores its own independent native fields");visibleCurves(uniform(0f),proxy,"replacement config owned after native swap");owner.restore();nativeCurves(uniform(77f),replacement,"replacement config restores its own native curvature");
        owner.refresh(card,0f,QSConstant.weight);AutoBlurDrawable nextAuto=new AutoBlurDrawable();ViewBlurProxy nextProxy=nextAuto.viewBlurProxy;float[] nextOriginal=curves(nextProxy.getBlurConfig());mix.replaceBlur(nextAuto);owner.refresh(card,0f,QSConstant.weight);
        visibleCurves(uniform(77f),proxy,"retired actual blur proxy is restored");visibleCurves(uniform(0f),nextProxy,"new actual proxy receives curvature");owner.restore();visibleCurves(nextOriginal,nextProxy,"replacement proxy restores independent original");
        mix.strokeConfigSideEffects=true;BlurConfig active=nextProxy.getBlurConfig();float[] activeOriginal=curves(active);
        owner.refresh(card,0f,QSConstant.weight);owner.restore();nativeCurves(activeOriginal,active,"provider stroke side effects cannot overwrite config restoration snapshot");visibleCurves(activeOriginal,nextProxy,"two-pass restoration applies config after provider stroke path");
        mix.failCalls=1;boolean failed=false;try{owner.refresh(card,0f,QSConstant.weight);}catch(ReflectiveOperationException|RuntimeException expected){failed=true;}
        check(failed,"partially failing provider setter is reported");near(56f,mix.radius,"partial provider setter restores outline");nativeCurves(activeOriginal,active,"provider failure before config pass restores all five original fields");visibleCurves(activeOriginal,nextProxy,"provider failure restores visible shape after native side effects");
        mix.strokeConfigSideEffects=false;active.failSetCalls=1;failed=false;try{owner.refresh(card,0f,QSConstant.weight);}catch(ReflectiveOperationException|RuntimeException expected){failed=true;}
        check(failed,"partially failing native config setter is reported");nativeCurves(activeOriginal,active,"partial config field write restores full asymmetric original");visibleCurves(activeOriginal,nextProxy,"partial config setter rollback reaches native visible blur");
        nextProxy.failApplyCalls=1;failed=false;try{owner.refresh(card,0f,QSConstant.weight);}catch(ReflectiveOperationException|RuntimeException expected){failed=true;}
        check(failed,"native blur apply failure after visible mutation is reported");nativeCurves(activeOriginal,active,"native apply failure restores original config");visibleCurves(activeOriginal,nextProxy,"native apply failure restores original visible shape");
        owner.refresh(card,0f,QSConstant.weight);visibleCurves(uniform(0f),nextProxy,"successful callback retries after each failure");owner.restore();
        active.cornerRadius=-1f;active.leftTopCornerRadius=-1f;active.rightTopCornerRadius=-1f;active.rightBottomCornerRadius=-1f;active.leftBottomCornerRadius=-1f;nextProxy.applyBlurConfig();
        owner.refresh(card,0f,QSConstant.weight);owner.restore();nativeCurves(uniform(-1f),active,"native blur sentinel fields restore without conversion");visibleCurves(uniform(-1f),nextProxy,"native sentinel is reapplied to actual blur proxy");
    }
    private static void steadySlider() throws Exception {
        QsPanelCorners corners=new QsPanelCorners();Slider slider=new Slider();
        slider.getResources().getDisplayMetrics().density=2f;
        corners.configure(settings(true,12f));android.graphics.Canvas canvas=new android.graphics.Canvas();
        QsPanelCorners.DrawScope scope=corners.beginDraw(slider,canvas);
        java.lang.reflect.Field drawing=QsPanelCorners.class.getDeclaredField("drawing");drawing.setAccessible(true);
        ThreadLocal<?> local=(ThreadLocal<?>)drawing.get(corners);Object originalDrawing=local.get();
        java.lang.reflect.Field paths=originalDrawing.getClass().getDeclaredField("paths");paths.setAccessible(true);
        java.lang.reflect.Field paints=originalDrawing.getClass().getDeclaredField("paints");paints.setAccessible(true);
        java.lang.reflect.Field canvases=originalDrawing.getClass().getDeclaredField("canvases");canvases.setAccessible(true);
        Object pathMap=paths.get(originalDrawing),paintMap=paints.get(originalDrawing),canvasMap=canvases.get(originalDrawing);
        scope.close();int writes=slider.radiusUpdates;
        for(int frame=0;frame<1000;frame++)corners.beginDraw(slider,canvas).close();
        check(slider.radiusUpdates==writes,"1000 unchanged draws do not resubmit native blur config");
        scope=corners.beginDraw(slider,canvas);
        check(local.get()==originalDrawing&&paths.get(local.get())==pathMap&&paints.get(local.get())==paintMap&&canvases.get(local.get())==canvasMap,
                "unchanged frames reuse identity maps without capturing View strongly");
        java.lang.reflect.Field owner=originalDrawing.getClass().getDeclaredField("owner");owner.setAccessible(true);
        check(owner.get(originalDrawing) instanceof java.lang.ref.WeakReference,"owned drawing does not keep its weak View key alive");
        scope.close();
        check(((java.util.Map<?,?>)pathMap).isEmpty()&&((java.util.Map<?,?>)paintMap).isEmpty()&&((java.util.Map<?,?>)canvasMap).isEmpty(),"scope closes clear strong native path/paint/canvas references");
        slider.mCurProgressRadius=61.6f;corners.beginDraw(slider,canvas).close();
        check(slider.radiusUpdates==++writes,"actual press curve updates native blur immediately");near(26.4f,slider.blurRadius,"press ratio reaches custom blur");
        slider.mClipProgressRect.right=40;corners.beginDraw(slider,canvas).close();
        check(slider.radiusUpdates==++writes,"actual Rect resize updates native blur");near(20f,slider.blurRadius,"rect half-width limits blur after resize");
        slider.mClipProgressRect.left=4;corners.beginDraw(slider,canvas).close();check(slider.radiusUpdates==++writes,"rect position changes update its native config");
        slider.mBackgroundRoundCornerWeight=.8f;corners.beginDraw(slider,canvas).close();check(slider.radiusUpdates==++writes,"native corner weight remains live");
        slider.mirrorScaleValue=.9f;corners.beginDraw(slider,canvas).close();check(slider.radiusUpdates==++writes,"native mirror scale remains live");
        slider.isSupportStroke=true;corners.beginDraw(slider,canvas).close();check(slider.radiusUpdates==++writes,"native stroke support remains live");
        slider.isDetailToggle=true;corners.beginDraw(slider,canvas).close();check(slider.radiusUpdates==++writes,"native detail mode remains live");
        slider.baseMixColorDrawable=new android.graphics.drawable.GradientDrawable();corners.beginDraw(slider,canvas).close();
        check(slider.radiusUpdates==++writes,"material created after first draw receives custom blur");
        slider.activeMixColorDrawable=new android.graphics.drawable.GradientDrawable();corners.beginDraw(slider,canvas).close();
        check(slider.radiusUpdates==++writes,"late active material receives custom blur");
        slider.baseMixColorDrawable=new android.graphics.drawable.GradientDrawable();corners.beginDraw(slider,canvas).close();
        check(slider.radiusUpdates==++writes,"same geometry replacement material receives custom blur");
        corners.onNativeUpdate(slider);check(slider.radiusUpdates==++writes,"theme/native update reasserts config on the same material");
        QsPanelCorners.BlurScope nativeScope=corners.beginBlurUpdate(slider,65f);
        slider.updateBaseMixColorDrawableRadius(nativeScope.radius);nativeScope.close();writes++;
        corners.beginDraw(slider,canvas).close();check(slider.radiusUpdates==++writes,"external native radius update dirties blur cache");
        corners.configure(settings(true,0f));check(slider.radiusUpdates==++writes,"settings immediately update existing blur");near(0f,slider.blurRadius,"zero remains native final-shape override");
        for(int frame=0;frame<1000;frame++)corners.beginDraw(slider,canvas).close();check(slider.radiusUpdates==writes,"zero steady frames remain deduplicated");
        corners.configure(settings(false,0f));near(slider.mCurProgressRadius,slider.blurRadius,"off restores latest native radius");writes++;
        for(int frame=0;frame<1000;frame++)corners.beginDraw(slider,canvas).close();check(slider.radiusUpdates==writes,"disabled draw adds no native setter calls");
        corners.configure(settings(true,12f));writes++;
        Bundle safe=settings(true,12f);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);corners.configure(safe);writes++;
        for(int frame=0;frame<1000;frame++)corners.beginDraw(slider,canvas).close();check(slider.radiusUpdates==writes,"safe draws add no native setter calls");
        safe.putBoolean(StatusBarSettings.SAFE_MODE,false);corners.configure(safe);writes++;
        corners.beginDraw(slider,canvas).close();check(slider.radiusUpdates==writes,"safe exit restores custom cache then remains steady");
        corners.detach(slider);writes++;
        corners.beginDraw(slider,canvas).close();check(slider.radiusUpdates==++writes,"new owner after detach does not reuse retired blur cache");
    }
    /** Same physical radius and dimensionless OEM curve reach every native renderer. */
    private static void uniformCurves() throws Exception {
        QSConstant.reset();QSConstant.weight=1.25f;QSConstant.mappedScale=1.5f;
        QsPanelCorners panels=new QsPanelCorners();QsTileCorners tiles=new QsTileCorners();
        Slider slider=new Slider();slider.cornerHook=panels;slider.getResources().getDisplayMetrics().density=1.25f;
        OplusQsBaseMediaPanelView media=new OplusQsBaseMediaPanelView();
        RectangleDeviceCardView card=new RectangleDeviceCardView();MixColorPluginDrawable mix=bindMix(card);
        card.resources.getDisplayMetrics().density=1.25f;card.bodyWidth=160;card.bodyHeight=160;
        media.getResources().getDisplayMetrics().density=1.25f;
        media.body.getResources().getDisplayMetrics().density=1.25f;
        com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne tile=
                new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne();
        com.oplus.systemui.qs.base.res.drawable.TileTransitionDrawable tileDrawable=
                new com.oplus.systemui.qs.base.res.drawable.TileTransitionDrawable(new CornerOutlineProvider(56f,.8f));
        tile.transition=tileDrawable;
        tile.getResources().getDisplayMetrics().density=1.25f;
        panels.onNativeUpdate(slider);panels.onNativeUpdate(media);panels.onNativeUpdate(card);tiles.onNativeUpdate(tile);
        android.graphics.RectF rect=new android.graphics.RectF(0,0,180,500);
        for(float dp:new float[]{0f,.25f,12f,30f}) {
            panels.configure(settings(true,dp));tiles.configure(settings(true,dp));
            float radius=dp*1.25f;
            near(radius,tileDrawable.getPathProvider().radius,"tile exact physical radius without OEM design scaling");
            near(radius,media.transition.lightRadius,"media same physical radius");near(radius,mix.radius,"device same physical radius");
            near(radius,card.outline.radius,"device smooth outline same physical radius");near(radius,slider.blurRadius,"slider native blur same physical radius");
            near(1.25f,tileDrawable.getPathProvider().weight,"tile canonical OEM weight even at zero/tiny radius");
            near(1.25f,media.transition.lightWeight,"media material uses same continuous weight");
            near(1.25f,mix.weight,"device material uses same continuous weight");near(1.25f,card.outline.weight,"device outline uses same continuous weight");
            near(1.25f,mix.blur().viewBlurProxy.visibleWeight,"device visible blur uses same continuous weight");
            near(1.25f,slider.visibleBlurWeight,"slider owned setter outside draw uses same continuous weight");
            QsPanelCorners.DrawScope draw=panels.beginDraw(slider);
            Object[] args={rect,56f,.7f,new Object()};QsPanelCorners.ShapeScope shape=panels.shape(slider.mBackgroundPath,"addSmoothRoundRect",args);
            near(radius,shape.args[1],"slider final path uses same physical radius");near(1.25f,shape.args[2],"slider final path uses same continuous weight");
            shape.close();draw.close();check(!panels.isRewritingBlur(),"native blur scope releases owner after every setter/draw");
        }
        int nativeSetters=slider.radiusUpdates,factories=QSConstant.calls;
        for(int i=0;i<1000;i++)panels.beginDraw(slider).close();
        check(slider.radiusUpdates==nativeSetters&&QSConstant.calls==factories,"1000 steady draw frames reuse native curve and skip blur submission");
        QsPanelCorners.DrawScope draw=panels.beginDraw(slider);
        QsPanelCorners.ShapeScope shape=panels.shape(slider.mBackgroundPath,"addSmoothRoundRect",new Object[]{rect,67.2f,.84f,new Object()});
        near(45f,shape.args[1],"native press radius ratio remains live");near(1.5f,shape.args[2],"native transient continuous-weight ratio remains live");shape.close();draw.close();
        slider.mBackgroundRoundCornerWeight=.8f;panels.onNativeUpdate(slider);near(1.25f,slider.visibleBlurWeight,"new native base coefficient still yields common resting curve");
        draw=panels.beginDraw(slider);shape=panels.shape(slider.mBackgroundPath,"addSmoothRoundRect",new Object[]{rect,56f,.96f,new Object()});
        near(1.5f,shape.args[2],"transient coefficient follows new native theme baseline");shape.close();draw.close();near(.8f,slider.mBackgroundRoundCornerWeight,"native weight field remains untouched");
        Object[] newApi={rect,56f,56f,.8f,new Object()};draw=panels.beginDraw(slider);shape=panels.shape(slider.mBackgroundPath,"addSmoothRoundRect",newApi);
        near(37.5f,shape.args[1],"new OEM two-axis path radius x");near(37.5f,shape.args[2],"new OEM two-axis path radius y");near(1.25f,shape.args[3],"new OEM path weight has distinct argument");shape.close();draw.close();
        panels.configure(settings(false,30f));near(.8f,slider.visibleBlurWeight,"off restores latest native blur coefficient");near(.4f,mix.blur().viewBlurProxy.visibleWeight,"off restores independent native device BlurConfig weight");
        panels.configure(settings(true,12f));panels.detach(slider);near(.8f,slider.visibleBlurWeight,"detach restores native slider weight without custom re-entry");
        QSConstant.reset();
    }
    private static void deviceWeightTransactions() throws Exception {
        QSConstant.reset();RectangleDeviceCardView card=new RectangleDeviceCardView();MixColorPluginDrawable mix=bindMix(card);
        QsDeviceCorners owner=new QsDeviceCorners();ViewBlurProxy proxy=mix.blur().viewBlurProxy;BlurConfig config=proxy.getBlurConfig();
        float[] nativeRadius=curves(config);mix.strokeConfigSideEffects=true;
        owner.refresh(card,12f,QSConstant.weight);near(.6f,config.radiusWeight,"visible blur receives owned common weight");
        int setters=mix.cornerUpdates,apply=proxy.applied,weightSetters=config.weightUpdates;
        for(int i=0;i<1000;i++)owner.refresh(card,12f,QSConstant.weight);
        check(mix.cornerUpdates==setters&&proxy.applied==apply&&config.weightUpdates==weightSetters,"1000 unchanged native callbacks do not rebuild device material or shape");
        config.radiusWeight=.9f;mix.pathProvider.update(mix.radius,.85f);owner.refresh(card,12f,QSConstant.weight);
        near(.6f,mix.weight,"live native path update reapplies common static coefficient");near(.6f,proxy.visibleWeight,"live native blur update reapplies common coefficient");
        owner.restore();near(.85f,mix.weight,"restore preserves latest native weight-only path change");near(56f,mix.radius,"weight-only update never captures module radius as native baseline");
        near(.9f,proxy.visibleWeight,"restore preserves latest independent native blur coefficient");nativeCurves(nativeRadius,config,"weight-only update preserves native asymmetric radii");
        config.radiusWeight=null;owner.refresh(card,12f,QSConstant.weight);owner.restore();check(config.radiusWeight==null&&proxy.visibleWeight==null,"native null smooth-weight sentinel restores exactly");
        mix.strokeConfigSideEffects=false;config.radiusWeight=.35f;config.failWeightCalls=1;boolean failed=false;
        try{owner.refresh(card,12f,QSConstant.weight);}catch(ReflectiveOperationException|RuntimeException expected){failed=true;}
        check(failed,"partially failing radiusWeight setter is reported");near(.35f,config.radiusWeight,"failed weight transaction restores native coefficient");near(.35f,proxy.visibleWeight,"failed weight transaction restores applied native material");nativeCurves(nativeRadius,config,"failed weight transaction restores all native corners");
        owner.refresh(card,12f,QSConstant.weight);owner.restore();
        android.graphics.drawable.GradientDrawable gradient=new android.graphics.drawable.GradientDrawable();float[] nativeCorners={11f,12f,13f,14f,15f,16f,17f,18f};gradient.setCornerRadii(nativeCorners);
        card.body.setBackground(gradient);card.setBlurDrawable(null);owner.refresh(card,0f,QSConstant.weight);
        check(gradient.getCornerRadii()==null&&gradient.getCornerRadius()==0f,"zero custom radius also clears native asymmetric gradient silhouette");owner.restore();
        check(java.util.Arrays.equals(nativeCorners,gradient.getCornerRadii()),"gradient silhouette returns to exact native asymmetric radii");
    }
    private static void spotlightCurve() {
        QSConstant.reset();QsPanelCorners panels=new QsPanelCorners();RectangleDeviceCardView card=new RectangleDeviceCardView();bindMix(card);
        com.oplus.systemui.plugins.qs.customize.view.viewholder.EditablePluginViewHolder$realPluginContainer$2$1 wrapper=
                new com.oplus.systemui.plugins.qs.customize.view.viewholder.EditablePluginViewHolder$realPluginContainer$2$1();wrapper.addView(card);
        android.graphics.Path path=new android.graphics.Path();
        com.oplus.posteffect.util.OplusPathAdapterCompatUtils.Adapter adapter=new com.oplus.posteffect.util.OplusPathAdapterCompatUtils.Adapter();
        android.graphics.RectF rect=new android.graphics.RectF(0,0,124,80);Object[] nativeShape={rect,56f,56f,android.graphics.Path.Direction.CW};
        panels.configure(settings(true,12f));panels.onNativeUpdate(card);
        QsPanelCorners.SpotlightScope scope=panels.beginSpotlight(wrapper,path,adapter);
        QsPanelCorners.ShapeScope shape=panels.shape(path,"addRoundRect",nativeShape);
        check(shape.handled&&adapter.writes==1,"owned device spotlight is rendered by native continuous adapter instead of circular fallback");
        near(24f,adapter.radii[0],"spotlight radius matches visible native body");near(.6f,adapter.weight,"spotlight weight matches native glass/outline");
        check(adapter.rect==rect&&adapter.direction==android.graphics.Path.Direction.CW&&nativeShape[1].equals(56f),"spotlight keeps exact native extent direction and original call args");shape.close();
        shape=panels.shape(new android.graphics.Path(),"addRoundRect",nativeShape);check(!shape.handled&&shape.args==nativeShape,"another path inside native callback remains untouched");shape.close();scope.close();
        check(!panels.isDrawingShapes(),"spotlight finally releases its scope");
        scope=panels.beginSpotlight(wrapper,path,new Object());shape=panels.shape(path,"addRoundRect",nativeShape);
        check(!shape.handled&&shape.args==nativeShape,"unknown OEM adapter retains the original native drawing path");shape.close();scope.close();
        panels.configure(settings(false,12f));scope=panels.beginSpotlight(wrapper,path,adapter);shape=panels.shape(path,"addRoundRect",nativeShape);
        check(!shape.handled&&adapter.writes==1,"off leaves native spotlight untouched");shape.close();scope.close();
        panels.configure(settings(true,12f));panels.detach(card);scope=panels.beginSpotlight(wrapper,path,adapter);shape=panels.shape(path,"addRoundRect",nativeShape);
        check(!shape.handled,"retired device owner never rewrites spotlight");shape.close();scope.close();
    }
    public static void main(String[] args) throws Exception {slider();sliderShapes();steadySlider();media();devices();nativeGeometry();deviceSurfaceOwnership();actualPluginBinding();deviceBlurGeometry();deviceBlurUpdatesAndRollback();uniformCurves();deviceWeightTransactions();spotlightCurve();System.out.println("QsPanelCornersCheck passed: "+checks+" (uniform native continuous curves, exact physical bounds, dynamic slider paths/blur, native restoration and steady-state submission caching)");}
}
