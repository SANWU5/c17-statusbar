package android.view;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
public class View implements ViewParent, Drawable.Callback {
    public static final int VISIBLE=0,INVISIBLE=4,GONE=8;
    private int minimumWidth, visibility;
    public int layoutRequests, invalidations;
    public ViewParent parent;
    public boolean attached=true,shown=true;
    public int windowVisibility;
    private Context context;
    private CharSequence description;
    private android.graphics.Rect clipBounds;
    private final Resources resources = Resources.getSystem();
    public View(Context context) { this.context=context; }
    public Context getContext() { return context; }
    public boolean isAttachedToWindow() { return attached; }
    public boolean isShown() { return shown; }
    public int getWindowVisibility() { return windowVisibility; }
    public int getWidth() { return 124; }
    public int getHeight() { return 80; }
    public void setClipBounds(android.graphics.Rect value) {clipBounds=value;}
    public android.graphics.Rect getClipBounds() {return clipBounds;}
    public View findViewById(int id) { return null; }
    public int getId() { return -1; }
    public CharSequence getContentDescription() { return description; }
    public void setContentDescription(CharSequence value) { description=value; }
    public int getMinimumWidth() { return minimumWidth; }
    public void setMinimumWidth(int value) { minimumWidth = value; requestLayout(); }
    public int getVisibility() { return visibility; }
    public void setVisibility(int value) { visibility = value; }
    public ViewParent getParent() { return parent; }
    public Resources getResources() { return resources; }
    public void requestLayout() { layoutRequests++; }
    public void invalidate() { invalidations++; }
    public void invalidateDrawable(Drawable drawable) { invalidate(); }
    public void scheduleDrawable(Drawable drawable, Runnable task, long when) { }
    public void unscheduleDrawable(Drawable drawable, Runnable task) { }
}
