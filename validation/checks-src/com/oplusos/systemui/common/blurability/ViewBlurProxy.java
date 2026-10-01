package com.oplusos.systemui.common.blurability;
/** Native apply reads raw BlurConfig fields, independently of the drawable path provider. */
public class ViewBlurProxy {
    public BlurConfig blurConfig=new BlurConfig();
    public float visibleScalar;
    public final float[] visibleCorners=new float[4];
    public int applied,failApplyCalls;
    public ViewBlurProxy(){applyBlurConfig();applied=0;}
    public BlurConfig getBlurConfig(){return blurConfig;}
    public void applyBlurConfig(){
        applied++;visibleScalar=blurConfig.getCornerRadius();
        visibleCorners[0]=blurConfig.getLeftTopCornerRadius();visibleCorners[1]=blurConfig.getRightTopCornerRadius();
        visibleCorners[2]=blurConfig.getRightBottomCornerRadius();visibleCorners[3]=blurConfig.getLeftBottomCornerRadius();
        if(failApplyCalls>0){failApplyCalls--;throw new IllegalStateException("native blur apply after mutation");}
    }
}
