package android.view;

import android.graphics.Rect;
import java.util.List;

/** Insets only; no display or cutout-path rendering is simulated. */
public final class DisplayCutout {
    private final Rect safe;
    public DisplayCutout(Rect safeInsets,List<Rect> boundingRects){safe=new Rect(safeInsets);}
    public int getSafeInsetLeft(){return safe.left;}
    public int getSafeInsetTop(){return safe.top;}
    public int getSafeInsetRight(){return safe.right;}
    public int getSafeInsetBottom(){return safe.bottom;}
}
