package com.oplusos.systemui.common.outline;
import android.graphics.drawable.Drawable;
public class CornerOutlineProvider {
    public float radius;
    public Float weight;
    public CornerOutlineProvider(float radius,Float weight){this.radius=radius;this.weight=weight;}
    public float getCornerRadius(Drawable drawable){return radius;}
    public Float getCornerWeight(Drawable drawable){return weight;}
    public void update(float radius,Float weight){this.radius=radius;this.weight=weight;}
}
