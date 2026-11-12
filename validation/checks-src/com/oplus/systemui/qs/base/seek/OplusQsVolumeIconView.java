package com.oplus.systemui.qs.base.seek;
public class OplusQsVolumeIconView extends com.oplus.systemui.qs.base.tile.QSLottieAnimationView {
    private com.android.systemui.plugins.qs.QSVolume.VolumeState qsVolumeState;
    public boolean animating;
    public final java.util.List<android.animation.Animator.AnimatorListener> listeners=new java.util.ArrayList<>();
    public Object currentIconState;
    public static class IconStateForCheck {
        public OplusQsVolumeIconView this$0;
        public com.oplus.systemui.qs.base.tile.QSLottieAnimationView.ColorfulConfig colorfulConfig;
        public Boolean aboveThreshold,isMuted;public boolean isGlobalTheme;
        public int lastLevel,currentStartFrame,currentEndFrame;public float currentSpeed;
        public String currentAsset;
        IconStateForCheck(OplusQsVolumeIconView owner){this$0=owner;}
    }
    public static final class bluetoothRouteIconState$1 extends IconStateForCheck {public bluetoothRouteIconState$1(OplusQsVolumeIconView owner){super(owner);}}
    public static final class normalRouteIconState$1 extends IconStateForCheck {public normalRouteIconState$1(OplusQsVolumeIconView owner){super(owner);}}
    public OplusQsVolumeIconView(){super();}
    public void nativeState(com.android.systemui.plugins.qs.QSVolume.VolumeState state){qsVolumeState=state;}
    public boolean isAnimating(){return animating;}
    public void addAnimatorListener(android.animation.Animator.AnimatorListener listener){listeners.add(listener);}
    public void removeAnimatorListener(android.animation.Animator.AnimatorListener listener){listeners.remove(listener);}
    public void finish(){animating=false;for(android.animation.Animator.AnimatorListener listener:new java.util.ArrayList<>(listeners))listener.onAnimationEnd(null);}
    public void updateIconState(com.android.systemui.plugins.qs.QSVolume.VolumeState state){qsVolumeState=state;}
    public void updateIconState(com.android.systemui.plugins.qs.QSVolume.VolumeState state,boolean animate){qsVolumeState=state;}
    public void updateIconColor(boolean first,boolean second){}
    public void disableTheme(boolean value){}
}
