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
    private ViewGroup.LayoutParams layoutParams;
    private final Resources resources = Resources.getSystem();
    public View(Context context) { this.context=context; }
    public Context getContext() { return context; }
    public boolean isAttachedToWindow() { return attached; }
    public boolean isShown() { return shown; }
    public int getWindowVisibility() { return windowVisibility; }
    public int getWidth() { return 124; }
    public int getHeight() { return 80; }
    public int getMeasuredWidth() {return getWidth();}
    public Object getTag(int key) {return null;}
    public void setTag(int key,Object value) { }
    public int getScrollX() {return 0;}
    public int getScrollY() {return 0;}
    public int getPaddingLeft() {return 0;}
    public int getPaddingTop() {return 0;}
    public int getPaddingRight() {return 0;}
    public int getPaddingBottom() {return 0;}
    public ViewGroup.LayoutParams getLayoutParams(){return layoutParams;}
    public void setLayoutParams(ViewGroup.LayoutParams params){layoutParams=params;requestLayout();}
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
    public View getRootView() {return parent instanceof View?((View)parent).getRootView():this;}
    public Resources getResources() { return resources; }
    public void requestLayout() { layoutRequests++; }
    public void invalidate() { invalidations++; }
    public void invalidateDrawable(Drawable drawable) { invalidate(); }
    public void scheduleDrawable(Drawable drawable, Runnable task, long when) { }
    public void unscheduleDrawable(Drawable drawable, Runnable task) { }
}
