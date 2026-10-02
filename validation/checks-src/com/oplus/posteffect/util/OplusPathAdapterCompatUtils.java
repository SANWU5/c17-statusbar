package com.oplus.posteffect.util;
import android.graphics.Path;
import android.graphics.RectF;
/** Synthetic contract of the audited OEM continuous-path adapter. */
public final class OplusPathAdapterCompatUtils {
    public static final OplusPathAdapterCompatUtils INSTANCE=new OplusPathAdapterCompatUtils();
    public static final class Adapter {
        public RectF rect;
        public float[] radii;
        public float weight;
        public Path.Direction direction;
        public int writes;
    }
    public boolean addSmoothRoundRect(Object value,RectF rect,float[] radii,Path.Direction direction,float weight) {
        if(!(value instanceof Adapter))return false;
        Adapter adapter=(Adapter)value;adapter.rect=rect;adapter.radii=radii.clone();
        adapter.weight=weight;adapter.direction=direction;adapter.writes++;return true;
    }
}
