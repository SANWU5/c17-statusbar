package com.oplus.systemui.plugins.drawable;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
public class PluginDrawable extends Drawable {
    public float radius=56f;
    public Float weight=1f;
    public final Object glass=new Object(),shader=new Object();
    public int color=0xff345678,alpha=173,cornerUpdates;
    public boolean fail;
    public int failCalls;
    public final com.oplusos.systemui.common.outline.RoundRectOutlineProvider pathProvider=new com.oplusos.systemui.common.outline.RoundRectOutlineProvider(56f,1f){
        public void update(float radius,Float weight){super.update(radius,weight);PluginDrawable.this.radius=radius;PluginDrawable.this.weight=weight;}
    };
    public Object getPathProvider(){return pathProvider;}
    public void setCornerRadius(float radius,Float weight){cornerUpdates++;this.radius=radius;this.weight=weight;pathProvider.update(radius,weight);if(fail||failCalls>0){if(failCalls>0)failCalls--;throw new IllegalStateException("native plugin radius");}}
    public void draw(Canvas canvas){}
    public void setAlpha(int alpha){this.alpha=alpha;}
    public void setColorFilter(ColorFilter filter){}
    public int getOpacity(){return -3;}
}
