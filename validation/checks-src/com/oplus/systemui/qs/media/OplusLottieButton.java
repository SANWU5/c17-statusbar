package com.oplus.systemui.qs.media;
public class OplusLottieButton extends android.widget.FrameLayout {
    private final android.widget.ImageView image=new android.widget.ImageView(getContext());
    public final android.view.View effective=new android.view.View(getContext());
    public OplusLottieButton(){this(new android.content.Context());}
    public OplusLottieButton(android.content.Context context){super(context);addView(image);addView(effective);setClickable(true);}
    public final android.widget.ImageView getImageView(){return image;}
    public final android.view.View getLottieAnimationView(){return effective;}
    public int nativeClickCalls;
    @Override public boolean performClick(){nativeClickCalls++;return super.performClick();}
}
