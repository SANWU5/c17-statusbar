package android.view;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
public class View implements ViewParent, Drawable.Callback {
    public static final int VISIBLE=0,INVISIBLE=4,GONE=8;
    private int minimumWidth, visibility;
    private float alpha=1f,translationY,scaleY=1f;
    public int layoutRequests, invalidations;
    public boolean dirty;
    public ViewParent parent;
    public boolean attached=true,shown=true;
    public int windowVisibility;
    private Context context;
    private CharSequence description;
    private android.graphics.Rect clipBounds;
    private Object outlineProvider;
    private boolean clipToOutline;
    private ViewGroup.LayoutParams layoutParams;
    private final Resources resources = Resources.getSystem();
    public interface OnLayoutChangeListener {
        void onLayoutChange(View view,int l,int t,int r,int b,int oldL,int oldT,int oldR,int oldB);
    }
    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View view);
        void onViewDetachedFromWindow(View view);
    }
    public final java.util.List<OnAttachStateChangeListener> attachListeners=new java.util.ArrayList<>();
    private final ViewTreeObserver treeObserver = new ViewTreeObserver();
    public void addOnAttachStateChangeListener(OnAttachStateChangeListener listener){if(!attachListeners.contains(listener))attachListeners.add(listener);}
    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener listener){attachListeners.remove(listener);}
    public ViewTreeObserver getViewTreeObserver(){return treeObserver;}
    public final java.util.List<OnLayoutChangeListener> layoutListeners=new java.util.ArrayList<>();
    public void addOnLayoutChangeListener(OnLayoutChangeListener listener){if(!layoutListeners.contains(listener))layoutListeners.add(listener);}
    public void removeOnLayoutChangeListener(OnLayoutChangeListener listener){layoutListeners.remove(listener);}
    public void dispatchLayoutChange(int width,int height,int oldWidth,int oldHeight){
        for(OnLayoutChangeListener listener:new java.util.ArrayList<>(layoutListeners))
            listener.onLayoutChange(this,0,0,width,height,0,0,oldWidth,oldHeight);
    }
    public View(Context context) { this.context=context; }
    public Context getContext() { return context; }
    public boolean isAttachedToWindow() { return attached; }
    public boolean isShown() { return shown; }
    public int getWindowVisibility() { return windowVisibility; }
    public int getWidth() { return 124; }
    public int getHeight() { return 80; }
    public int getTop() {return 0;}
    public int getLeft() {return 0;}
    public float getTranslationX() {return 0f;}
    public int getLayoutDirection() {return 0;}
    public float getAlpha() {return alpha;}
    public void setAlpha(float value) {alpha=value;}
    public float getTranslationY() {return translationY;}
    public void setTranslationY(float value) {translationY=value;}
    public float getScaleY() {return scaleY;}
    public void setScaleY(float value) {scaleY=value;}
    public float getPivotY() {return getHeight()*.5f;}
    public int getMeasuredWidth() {return getWidth();}
    public int getMeasuredHeight() {return getHeight();}
    public Object getTag(int key) {return null;}
    public Object getTag() {return null;}
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
    public Object getOutlineProvider(){return outlineProvider;}
    public void setOutlineProvider(Object value){outlineProvider=value;}
    public boolean getClipToOutline(){return clipToOutline;}
    public void setClipToOutline(boolean value){clipToOutline=value;}
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
    public android.os.IBinder getWindowToken() {return null;}
    public Resources getResources() { return resources; }
    public void requestLayout() { layoutRequests++; }
    public void invalidate() { invalidations++; }
    public boolean isDirty() { return dirty; }
    public void invalidateOutline() { invalidations++; }
    public void invalidateDrawable(Drawable drawable) { invalidate(); }
    public void scheduleDrawable(Drawable drawable, Runnable task, long when) { }
    public void unscheduleDrawable(Drawable drawable, Runnable task) { }
}
