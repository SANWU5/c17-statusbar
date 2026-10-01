package com.oplus.systemui.plugins.qs.customize.view.animation.deform.tile.fixed;
import com.oplusos.systemui.common.outline.CornerOutlineProvider;
/** Matches the ROM's persistent block provider (native radius/weight + span-specific stroke). */
public final class TileDeformOutlineProvider extends CornerOutlineProvider {
    public int currentSpanSize;
    public TileDeformOutlineProvider(float radius,Float weight){super(radius,weight);}
    public int getCurrentSpanSize(){return currentSpanSize;}
    public void setCurrentSpanSize(int span){currentSpanSize=span;}
}
