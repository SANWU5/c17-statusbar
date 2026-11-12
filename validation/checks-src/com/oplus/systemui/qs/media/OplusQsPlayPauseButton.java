package com.oplus.systemui.qs.media;
public class OplusQsPlayPauseButton extends OplusLottieButton {
    public OplusQsPlayPauseButton(){super();}
    public OplusQsPlayPauseButton(android.content.Context context){super(context);}
    public int state=2;
    public void setPlaybackState(int value){state=value;}
    public void handleAnimationEnd(){}
    public void setImageResourceInternal(int resource){}
}
