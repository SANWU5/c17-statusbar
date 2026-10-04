package com.oplus.systemui.plugins.qs.customize.view.animation.deform.tile.fixed;

import android.graphics.drawable.Drawable;
import android.view.View;
import com.oplus.systemui.qs.base.res.drawable.TileLayerDrawable;

/** Verified OEM topology: the animator retains its own provider after a drawable replacement. */
public final class TileFixedRadiusShareTransitionProperty {
    public final Drawable tileDrawable;
    public final TileDeformOutlineProvider deformPathProvider;
    public Float deformCornerWeight;
    public TileFixedRadiusShareTransitionProperty(TileLayerDrawable drawable, TileDeformOutlineProvider provider) {
        tileDrawable=drawable; deformPathProvider=provider; deformCornerWeight=provider.weight;
    }
    public void setValue(View view, float radius) {
        deformPathProvider.update(radius,deformCornerWeight);
        ((TileLayerDrawable)tileDrawable).invalidatePath();
    }
}
