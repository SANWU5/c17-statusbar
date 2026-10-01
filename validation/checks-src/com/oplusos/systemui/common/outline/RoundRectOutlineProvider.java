package com.oplusos.systemui.common.outline;
public class RoundRectOutlineProvider extends CornerOutlineProvider {
    public RoundRectOutlineProvider(float radius,Float weight){super(radius,weight);}
    public float getCornerRadius(){return radius;}
    public Float getCornerWeight(){return weight;}
}
