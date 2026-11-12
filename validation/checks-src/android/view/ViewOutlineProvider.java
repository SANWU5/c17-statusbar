package android.view;
/** Native outline API shape, shared by custom and OEM providers. */
public abstract class ViewOutlineProvider {
    public abstract void getOutline(View view,android.graphics.Outline outline);
}
