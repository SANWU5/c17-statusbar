package com.oplus.deviceplugin.sdk.ui.view.drawable;
import com.oplus.deviceplugin.sdk.entity.SmoothRoundCorner;
public class SmoothViewOutlineProvider {
    public float radius=56f,weight=1f;
    public boolean fail;
    public void setSmoothCorner(SmoothRoundCorner corner){radius=corner.getRadius();weight=corner.getWeight();if(fail)throw new IllegalStateException("native device outline");}
}
