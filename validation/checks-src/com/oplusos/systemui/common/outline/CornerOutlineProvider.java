package com.oplusos.systemui.common.outline;
import android.graphics.drawable.Drawable;
public class CornerOutlineProvider extends android.view.ViewOutlineProvider {
    public float radius;
    public Float weight;
    public CornerOutlineProvider(float radius,Float weight){this.radius=radius;this.weight=weight;}
    public float getCornerRadius(Drawable drawable){return radius;}
    public Float getCornerWeight(Drawable drawable){return weight;}
    public void getOutline(android.view.View view,android.graphics.Outline outline){outline.setRoundRect(new android.graphics.Rect(0,0,view.getWidth(),view.getHeight()),radius);}
    public int pathUpdates;
    public void updatePath(Drawable drawable,android.graphics.Path path,Object oplusPath,Object extra){pathUpdates++;}
}
