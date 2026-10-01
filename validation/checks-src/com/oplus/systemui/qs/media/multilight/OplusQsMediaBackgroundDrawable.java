package com.oplus.systemui.qs.media.multilight;
import com.oplus.systemui.qs.base.res.drawable.TileLayerDrawable;
import com.oplusos.systemui.common.outline.CornerOutlineProvider;
public class OplusQsMediaBackgroundDrawable extends TileLayerDrawable {
    public float lightRadius;
    public Float lightWeight;
    public int cornerUpdates,failCorners;
    public OplusQsMediaBackgroundDrawable(CornerOutlineProvider provider){super(provider);lightRadius=provider.radius;lightWeight=provider.weight;}
    public void setCornerParams(float radius,Float weight){cornerUpdates++;lightRadius=radius;lightWeight=weight;if(failCorners>0){failCorners--;throw new IllegalStateException("native media corners");}}
}
