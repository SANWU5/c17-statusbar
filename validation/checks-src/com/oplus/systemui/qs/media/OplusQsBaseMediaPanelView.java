package com.oplus.systemui.qs.media;
import android.content.Context;
import android.view.View;
import com.oplus.systemui.qs.media.multilight.OplusQsMediaBackgroundDrawable;
import com.oplusos.systemui.common.outline.CornerOutlineProvider;
public class OplusQsBaseMediaPanelView extends View {
    public int bodyWidth=240,bodyHeight=120,measuredWidth=-1,measuredHeight=-1;
    public final View body=new View(new Context()){
        public int getWidth(){return bodyWidth;}
        public int getHeight(){return bodyHeight;}
        public int getMeasuredWidth(){return measuredWidth>=0?measuredWidth:bodyWidth;}
        public int getMeasuredHeight(){return measuredHeight>=0?measuredHeight:bodyHeight;}
    };
    public OplusQsMediaBackgroundDrawable transition;
    public OplusLottieButton preBtn=new OplusLottieButton(),nextBtn=new OplusLottieButton();
    public OplusQsPlayPauseButton playOrPauseBtn=new OplusQsPlayPauseButton();
    public android.widget.ImageView mediaOutputBtn=new android.widget.ImageView(getContext());
    public boolean inAudioSharing;
    public OplusQsBaseMediaPanelView(){super(new Context());transition=new OplusQsMediaBackgroundDrawable(new CornerOutlineProvider(56f,.8f));
        preBtn.parent=this;nextBtn.parent=this;playOrPauseBtn.parent=this;mediaOutputBtn.parent=this;}
    public View getBg(){return body;}
    public void onFinishInflate(){}
    public OplusQsMediaBackgroundDrawable getTransitionDrawable(){return transition;}
    private int getPreBtnRes(){return 0x7f080e5a;}private int getNextBtnRes(){return 0x7f080e54;}private int getDeviceSwitchRes(){return 0x7f0818f6;}
    public void setSemanticButton(OplusLottieButton button,int resource,com.android.systemui.media.controls.shared.model.MediaAction action,com.android.systemui.media.controls.shared.model.MediaData data){}
    public void bindViewWithAction(OplusLottieButton button,com.android.systemui.media.controls.shared.model.MediaAction action,com.android.systemui.media.controls.shared.model.MediaData data){}
    public void updateMediaOutputBtn(int route){}
    public void bindMediaDataInner(com.android.systemui.media.controls.shared.model.MediaData data,String key){}
    public void bindCoverImg(com.android.systemui.media.controls.shared.model.MediaData data){}
    public void updateMultiLightForCurrentTrack(com.android.systemui.media.controls.shared.model.MediaData data){}
    public void resetCoverImg(){}
    public void onDrawableUpdate(boolean theme){}
    public void unBindMediaData(){}
}
