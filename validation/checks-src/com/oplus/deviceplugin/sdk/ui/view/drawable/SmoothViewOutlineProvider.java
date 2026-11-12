package com.oplus.deviceplugin.sdk.ui.view.drawable;
import com.oplus.deviceplugin.sdk.entity.SmoothRoundCorner;
public class SmoothViewOutlineProvider extends android.view.ViewOutlineProvider {
    public float radius=56f,weight=1f;
    public boolean fail;
    public void getOutline(android.view.View view,android.graphics.Outline outline){outline.setRoundRect(new android.graphics.Rect(0,0,view.getWidth(),view.getHeight()),radius);}
    public void setSmoothCorner(SmoothRoundCorner corner){radius=corner.getRadius();weight=corner.getWeight();if(fail)throw new IllegalStateException("native device outline");}
}
