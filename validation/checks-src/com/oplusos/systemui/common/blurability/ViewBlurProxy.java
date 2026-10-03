package com.oplusos.systemui.common.blurability;
/** Native apply reads raw BlurConfig fields, independently of the drawable path provider. */
public class ViewBlurProxy {
    /** Confirmed native private final owner field; mutable here only to exercise recycling. */
    public android.view.View view;
    public BlurConfig blurConfig=new BlurConfig();
    public float visibleScalar;
    public Float visibleWeight;
    public final float[] visibleCorners=new float[4];
    public int applied,failApplyCalls;
    public ViewBlurProxy(){applyBlurConfig();applied=0;}
    public BlurConfig getBlurConfig(){return blurConfig;}
    public android.view.View getView(){return view;}
    public android.graphics.drawable.Drawable getBlurDrawable(android.graphics.drawable.Drawable fallback){return fallback;}
    public void applyBlurConfig(){
        applied++;visibleScalar=blurConfig.getCornerRadius();visibleWeight=blurConfig.getRadiusWeight();
        visibleCorners[0]=blurConfig.getLeftTopCornerRadius();visibleCorners[1]=blurConfig.getRightTopCornerRadius();
        visibleCorners[2]=blurConfig.getRightBottomCornerRadius();visibleCorners[3]=blurConfig.getLeftBottomCornerRadius();
        if(failApplyCalls>0){failApplyCalls--;throw new IllegalStateException("native blur apply after mutation");}
    }
}
