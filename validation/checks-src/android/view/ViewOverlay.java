package android.view;
import android.graphics.drawable.Drawable;
/** Passive native overlay fixture; no independent frame scheduling. */
public class ViewOverlay {
    private final View host;
    public final java.util.List<Drawable> drawables = new java.util.ArrayList<>();
    ViewOverlay(View host){this.host=host;}
    public void add(Drawable drawable){if(!drawables.contains(drawable)){drawables.add(drawable);drawable.setCallback(host);}}
    public void remove(Drawable drawable){if(drawables.remove(drawable)&&drawable.getCallback()==host)drawable.setCallback(null);}
}
