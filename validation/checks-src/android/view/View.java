package android.view;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
public class View implements ViewParent, Drawable.Callback {
    public static final class MeasureSpec {
        public static final int EXACTLY=0x40000000, AT_MOST=0x80000000, UNSPECIFIED=0;
        public static int getSize(int spec){return spec&0x3fffffff;}
        public static int getMode(int spec){return spec&0xc0000000;}
        public static int makeMeasureSpec(int size,int mode){return (size&0x3fffffff)|(mode&0xc0000000);}
    }
    public static final int VISIBLE=0,INVISIBLE=4,GONE=8;
    public static final int LAYOUT_DIRECTION_LTR=0,LAYOUT_DIRECTION_RTL=1;
    private int minimumWidth, visibility;
    private float alpha=1f,translationAlpha=1f,translationX,translationY,scaleX=1f,scaleY=1f;
    private int layoutLeft;
    private int layoutTop;
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
    private boolean measured,layoutRequested=true;
    private int measuredWidth,measuredHeight;
    private final Resources resources = Resources.getSystem();
    public interface OnLayoutChangeListener {
        void onLayoutChange(View view,int l,int t,int r,int b,int oldL,int oldT,int oldR,int oldB);
    }
    public interface OnClickListener { void onClick(View view); }
    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View view);
        void onViewDetachedFromWindow(View view);
    }
    public final java.util.List<OnAttachStateChangeListener> attachListeners=new java.util.ArrayList<>();
    private final ViewTreeObserver treeObserver = new ViewTreeObserver();
    private final ViewOverlay overlay = new ViewOverlay(this);
    public ViewOverlay getOverlay(){return overlay;}
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
    public int getTop() {return layoutTop;}
    public void offsetTopAndBottom(int value) {layoutTop+=value;}
    public void getLocationOnScreen(int[] output) {output[0]=getLeft();output[1]=getTop();}
    public int getLeft() {return layoutLeft;}
    public void offsetLeftAndRight(int value) {layoutLeft+=value;}
    public float getTranslationX() {return translationX;}
    public void setTranslationX(float value) {translationX=value;}
    public int getLayoutDirection() {return 0;}
    public float getAlpha() {return alpha;}
    public void setAlpha(float value) {alpha=value;}
    public float getTransitionAlpha() {return translationAlpha;}
    public void setTransitionAlpha(float value) {translationAlpha=value;}
    public float getTranslationY() {return translationY;}
    public void setTranslationY(float value) {translationY=value;}
    public float getScaleY() {return scaleY;}
    public void setScaleY(float value) {scaleY=value;}
    public float getScaleX() {return scaleX;}
    public void setScaleX(float value) {scaleX=value;}
    public float getPivotY() {return getHeight()*.5f;}
    public int getMeasuredWidth() {return measured?measuredWidth:getWidth();}
    public int getMeasuredHeight() {return measured?measuredHeight:getHeight();}
    public int getMeasuredWidthAndState(){return getMeasuredWidth();}
    public boolean isLayoutRequested(){return layoutRequested;}
    public void measure(int width,int height){onMeasure(width,height);measured=true;layoutRequested=false;}
    protected void onMeasure(int width,int height){setMeasuredDimension(getWidth(),getHeight());}
    protected void setMeasuredDimension(int width,int height){measuredWidth=width;measuredHeight=height;measured=true;}
    public static int resolveSizeAndState(int size,int spec,int state){return MeasureSpec.getMode(spec)==MeasureSpec.EXACTLY?MeasureSpec.getSize(spec):size;}
    public void setClickable(boolean value) { }
    public void setFocusable(boolean value) { }
    public void setImportantForAccessibility(int value) { }
    public void setPadding(int left,int top,int right,int bottom) { }
    public Object getTag(int key) {return null;}
    public Object getTag() {return null;}
    public void setTag(int key,Object value) { }
    public int getScrollX() {return 0;}
    public int getScrollY() {return 0;}
    public int getPaddingLeft() {return 0;}
    public int getPaddingTop() {return 0;}
    public int getPaddingRight() {return 0;}
    public int getPaddingBottom() {return 0;}
    public int getPaddingStart() {return getLayoutDirection()==LAYOUT_DIRECTION_RTL?getPaddingRight():getPaddingLeft();}
    public int getPaddingEnd() {return getLayoutDirection()==LAYOUT_DIRECTION_RTL?getPaddingLeft():getPaddingRight();}
    public void setPaddingRelative(int start,int top,int end,int bottom) { }
    public ViewGroup.LayoutParams getLayoutParams(){return layoutParams;}
    public void setLayoutParams(ViewGroup.LayoutParams params){layoutParams=params;requestLayout();}
    public void setClipBounds(android.graphics.Rect value) {clipBounds=value;}
    public android.graphics.Rect getClipBounds() {return clipBounds;}
    public boolean getClipBounds(android.graphics.Rect result) {if(clipBounds==null)return false;result.left=clipBounds.left;result.top=clipBounds.top;result.right=clipBounds.right;result.bottom=clipBounds.bottom;return true;}
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
    public WindowInsets getRootWindowInsets() {return null;}
    public android.os.IBinder getWindowToken() {return null;}
    public Display getDisplay() {return null;}
    public void draw(android.graphics.Canvas canvas) { }
    public Resources getResources() { return resources; }
    public void requestLayout() { layoutRequests++;layoutRequested=true; }
    public void invalidate() { invalidations++; }
    public boolean isDirty() { return dirty; }
    public void invalidateOutline() { invalidations++; }
    public void invalidateDrawable(Drawable drawable) { invalidate(); }
    public void scheduleDrawable(Drawable drawable, Runnable task, long when) { }
    public void unscheduleDrawable(Drawable drawable, Runnable task) { }
    private Drawable background;
    public Drawable getBackground(){return background;}
    public void setBackground(Drawable value){background=value;}
    public android.graphics.Matrix getMatrix(){return new android.graphics.Matrix();}
    public void transformMatrixToGlobal(android.graphics.Matrix matrix){
        if(parent instanceof View)((View)parent).transformMatrixToGlobal(matrix);
        matrix.preTranslate(getLeft()+getTranslationX(),getTop()+getTranslationY());
    }
    public void transformMatrixToLocal(android.graphics.Matrix matrix){
        android.graphics.Matrix global=new android.graphics.Matrix(),inverse=new android.graphics.Matrix();
        transformMatrixToGlobal(global);global.invert(inverse);matrix.preConcat(inverse);
    }
    public boolean post(Runnable task){task.run();return true;}
}
