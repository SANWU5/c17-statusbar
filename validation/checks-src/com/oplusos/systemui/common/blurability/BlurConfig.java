package com.oplusos.systemui.common.blurability;
/** Exact ROM contract: the visible blur has scalar and four independent corner fields. */
public class BlurConfig {
    public float cornerRadius=63f,leftTopCornerRadius=59f,rightTopCornerRadius=61f,
            rightBottomCornerRadius=65f,leftBottomCornerRadius=67f;
    public final Object material=new Object(),mixColors=new Object(),lightTemplate=new Object();
    public float blurAmount=35f;
    public int failSetCalls;
    public float getCornerRadius(){return cornerRadius;}
    public float getLeftTopCornerRadius(){return leftTopCornerRadius;}
    public float getRightTopCornerRadius(){return rightTopCornerRadius;}
    public float getRightBottomCornerRadius(){return rightBottomCornerRadius;}
    public float getLeftBottomCornerRadius(){return leftBottomCornerRadius;}
    public void setCornerRadius(float radius){
        cornerRadius=radius;leftTopCornerRadius=radius;
        if(failSetCalls>0){failSetCalls--;throw new IllegalStateException("partial native blur config");}
        rightTopCornerRadius=radius;rightBottomCornerRadius=radius;leftBottomCornerRadius=radius;
    }
}
