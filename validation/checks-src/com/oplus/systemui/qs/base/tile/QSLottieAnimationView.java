package com.oplus.systemui.qs.base.tile;
public class QSLottieAnimationView extends android.widget.ImageView {
    public int cancelledLoaderTasks,nativeCancels,nativeSetCompositions;public boolean autoPlay=true;
    private ColorfulConfig colorfulConfig;
    private String assetFileName;
    private android.graphics.drawable.Drawable lottieDrawable;
    private int frame;
    private float speed;
    public float minFrameForCheck,maxFrameForCheck;
    public static final class ColorfulConfig {
        private final float startAlpha,endAlpha;private final int startColor,endColor;
        public ColorfulConfig(float startAlpha,float endAlpha,int startColor,int endColor){this.startAlpha=startAlpha;this.endAlpha=endAlpha;this.startColor=startColor;this.endColor=endColor;}
        public float getStartAlpha(){return startAlpha;}public float getEndAlpha(){return endAlpha;}
        public int getStartColor(){return startColor;}public int getEndColor(){return endColor;}
    }
    public QSLottieAnimationView(){super(new android.content.Context());}
    public int getFrame(){return frame;}public float getSpeed(){return speed;}
    public float getMinFrame(){return minFrameForCheck;}public float getMaxFrame(){return maxFrameForCheck;}
    public void lottieForCheck(android.graphics.drawable.Drawable source,ColorfulConfig config,String asset,int frame,float speed){
        lottieDrawable=source;colorfulConfig=config;assetFileName=asset;this.frame=frame;this.speed=speed;minFrameForCheck=0;maxFrameForCheck=frame;setImageDrawable(source);
    }
    public void cancelLoaderTask(){cancelledLoaderTasks++;}
    public void cancelAnimation(){nativeCancels++;}
    public void setComposition(com.airbnb.lottie.LottieComposition composition){nativeSetCompositions++;}
}
