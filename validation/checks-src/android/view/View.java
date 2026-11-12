package android.view;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
public class View implements ViewParent, Drawable.Callback {
    /** OEM background display-list recording has its own Canvas, separate from the View content node. */
    public android.graphics.RenderNode mBackgroundRenderNode;
    public static final android.util.Property<View,Float> TRANSLATION_X=new android.util.Property<View,Float>(Float.class,"translationX"){
        public Float get(View view){return view.getTranslationX();}
        public void set(View view,Float value){view.setTranslationX(value);}
    };
    public static final int NO_ID=-1;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_NO=2,IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4;
    public static final class MeasureSpec {
        public static final int EXACTLY=0x40000000, AT_MOST=0x80000000, UNSPECIFIED=0;
        public static int getSize(int spec){return spec&0x3fffffff;}
        public static int getMode(int spec){return spec&0xc0000000;}
        public static int makeMeasureSpec(int size,int mode){return (size&0x3fffffff)|(mode&0xc0000000);}
    }
    public static final int VISIBLE=0,INVISIBLE=4,GONE=8;
    public static final int LAYER_TYPE_NONE=0,LAYER_TYPE_SOFTWARE=1,LAYER_TYPE_HARDWARE=2;
    private int layerType;
    private android.graphics.Paint layerPaintForCheck;
    public int layerTypeWrites;
    public boolean hardwareAcceleratedForCheck=true;
    public int getLayerType(){return layerType;}
    public void setLayerType(int type,android.graphics.Paint paint){layerType=type;layerPaintForCheck=paint;layerTypeWrites++;}
    public android.graphics.Paint getLayerPaintForCheck(){return layerPaintForCheck;}
    public boolean isHardwareAccelerated(){return hardwareAcceleratedForCheck;}
    public static final int LAYOUT_DIRECTION_LTR=0,LAYOUT_DIRECTION_RTL=1;
    private int minimumWidth, minimumHeight, visibility;
    private boolean enabled=true,selected;
    private float alpha=1f,translationAlpha=1f,translationX,translationY,scaleX=1f,scaleY=1f,rotation;
    private int layoutLeft;
    private int layoutTop;
    private int layoutRightForCheck,layoutBottomForCheck;
    /** Opt in to Android's real layout bounds for parent-dispatch checks.
     * The legacy measurement fixtures retain their fixed default dimensions. */
    public boolean useLayoutBoundsForCheck;
    private boolean touchDownForCheck;
    public int layoutRequests, invalidations;
    /** Real field name: NotificationMiniStack writes only its visibility bits during a rear card draw. */
    public int mViewFlags;
    public int postedInvalidations;
    public boolean dirty;
    public ViewParent parent;
    public boolean attached=true,shown=true;
    public int windowVisibility;
    private Context context;
    private CharSequence description;
    private android.graphics.Rect clipBounds;
    private ViewOutlineProvider outlineProvider;
    private boolean clipToOutline;
    private ViewGroup.LayoutParams layoutParams;
    private boolean measured,layoutRequested=true;
    private boolean clickable,focusable,longClickable;
    private OnClickListener clickListener;
    private OnLongClickListener longClickListener;
    private OnTouchListener touchListener;
    private int measuredWidth,measuredHeight;
    private final Resources resources = Resources.getSystem();
    public interface OnLayoutChangeListener {
        void onLayoutChange(View view,int l,int t,int r,int b,int oldL,int oldT,int oldR,int oldB);
    }
    public interface OnClickListener { void onClick(View view); }
    public interface OnLongClickListener { boolean onLongClick(View view); }
    public interface OnTouchListener { boolean onTouch(View view, MotionEvent event); }
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
    private boolean pressedForCheck;
    public void setPressed(boolean value){pressedForCheck=value;}
    public boolean isPressed(){return pressedForCheck;}
    public int backgroundColorForCheck;
    public void setBackgroundColor(int value){backgroundColorForCheck=value;}
    public void setWillNotDraw(boolean value) { }
    protected void onAttachedToWindow() { }
    protected void onDetachedFromWindow() { }
    protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) { }
    protected void onLayout(boolean changed,int left,int top,int right,int bottom) { }
    public void onVisibilityAggregated(boolean visible) { }
    protected void onVisibilityChanged(View changedView,int visibility) { }
    protected void onWindowVisibilityChanged(int visibility) { }
    public void assignParent(ViewParent next){parent=next;}
    public ViewRootImpl nativeRootForCheck=new ViewRootImpl();
    private android.graphics.RenderNode backgroundNodeForCheck;
    public ViewRootImpl getViewRootImpl(){return nativeRootForCheck;}
    public android.window.OnBackInvokedDispatcher backDispatcherForCheck;
    public final android.window.OnBackInvokedDispatcher findOnBackInvokedDispatcher(){return backDispatcherForCheck;}
    public IViewWrapper getViewWrapper(){return () -> {
        if(backgroundNodeForCheck==null)backgroundNodeForCheck=new android.graphics.RenderNode("fixture owned backdrop view");
        return backgroundNodeForCheck;
    };}
    public Context getContext() { return context; }
    public boolean isAttachedToWindow() { return attached; }
    public boolean isShown() { return shown; }
    public int getWindowVisibility() { return windowVisibility; }
    public int getWidth() { return useLayoutBoundsForCheck?Math.max(0,layoutRightForCheck-layoutLeft):124; }
    public int getHeight() { return useLayoutBoundsForCheck?Math.max(0,layoutBottomForCheck-layoutTop):80; }
    public int getTop() {return layoutTop;}
    public void offsetTopAndBottom(int value) {layoutTop+=value;}
    public int layoutCalls;
    public void layout(int left,int top,int right,int bottom){setFrame(left,top,right,bottom);layoutCalls++;}
    protected boolean setFrame(int left,int top,int right,int bottom){boolean changed=layoutLeft!=left||layoutTop!=top||layoutRightForCheck!=right||layoutBottomForCheck!=bottom;layoutLeft=left;layoutTop=top;layoutRightForCheck=right;layoutBottomForCheck=bottom;return changed;}
    private boolean horizontalFadingEdgeEnabled;
    private int fadingEdgeLength;
    public boolean isHorizontalFadingEdgeEnabled(){return horizontalFadingEdgeEnabled;}
    public void setHorizontalFadingEdgeEnabled(boolean enabled){horizontalFadingEdgeEnabled=enabled;}
    public int getHorizontalFadingEdgeLength(){return fadingEdgeLength;}
    public void setFadingEdgeLength(int length){fadingEdgeLength=length;}
    public void getLocationOnScreen(int[] output) {output[0]=getLeft();output[1]=getTop();}
    public void getLocationInWindow(int[] output) {getLocationOnScreen(output);}
    public int getLeft() {return layoutLeft;}
    public int getRight() {return getLeft()+getWidth();}
    public int getBottom() {return getTop()+getHeight();}
    public boolean hasOnClickListeners() {return clickListener!=null;}
    public void setOnLongClickListener(OnLongClickListener listener){longClickListener=listener;if(listener!=null)longClickable=true;}
    public boolean performLongClick(){return longClickListener!=null&&longClickListener.onLongClick(this);}
    public void setOnTouchListener(OnTouchListener listener){touchListener=listener;}
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
    public float getRotation(){return rotation;}
    public void setRotation(float value){rotation=value;}
    private Float explicitPivotX,explicitPivotY;
    public float getPivotX() {return explicitPivotX==null?getWidth()*.5f:explicitPivotX;}
    public float getPivotY() {return explicitPivotY==null?getHeight()*.5f:explicitPivotY;}
    public int getMeasuredWidth() {return measured?measuredWidth:getWidth();}
    public int getMeasuredHeight() {return measured?measuredHeight:getHeight();}
    public int getMeasuredWidthAndState(){return getMeasuredWidth();}
    public boolean isLayoutRequested(){return layoutRequested;}
    public boolean transientStateForCheck;
    public boolean hasTransientState(){return transientStateForCheck;}
    public void measure(int width,int height){onMeasure(width,height);measured=true;layoutRequested=false;}
    protected void onMeasure(int width,int height){setMeasuredDimension(getWidth(),getHeight());}
    protected void setMeasuredDimension(int width,int height){measuredWidth=width;measuredHeight=height;measured=true;}
    public static int resolveSizeAndState(int size,int spec,int state){return MeasureSpec.getMode(spec)==MeasureSpec.EXACTLY?MeasureSpec.getSize(spec):size;}
    public static int resolveSize(int size,int spec){
        int mode=MeasureSpec.getMode(spec),limit=MeasureSpec.getSize(spec);
        return mode==MeasureSpec.EXACTLY?limit:mode==MeasureSpec.AT_MOST?Math.min(size,limit):size;
    }
    public void setPivotX(float value) {explicitPivotX=value;}
    public void setPivotY(float value) {explicitPivotY=value;}
    public void setClickable(boolean value) {clickable=value;}
    public boolean isClickable(){return clickable;}
    public boolean isLongClickable(){return longClickable;}
    public void setLongClickable(boolean value){longClickable=value;}
    public void setFocusable(boolean value) {focusable=value;}
    public boolean isFocusable(){return focusable;}
    public void setOnClickListener(OnClickListener listener){clickListener=listener;if(listener!=null)clickable=true;}
    public boolean performClick(){if(clickListener==null)return false;clickListener.onClick(this);return true;}
    public boolean performAccessibilityAction(int action,android.os.Bundle arguments){return action==16&&performClick();}
    public boolean dispatchTouchEvent(MotionEvent event){return touchListener!=null&&touchListener.onTouch(this,event)||onTouchEvent(event);}
    public boolean onTouchEvent(MotionEvent event) {
        if(!isClickable()){touchDownForCheck=false;return false;}
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){touchDownForCheck=true;return true;}
        if(action==MotionEvent.ACTION_CANCEL){touchDownForCheck=false;return true;}
        if(action==MotionEvent.ACTION_MOVE&&(event.getX()<0||event.getX()>=getWidth()||event.getY()<0||event.getY()>=getHeight()))touchDownForCheck=false;
        if(action==MotionEvent.ACTION_UP){boolean click=touchDownForCheck;touchDownForCheck=false;if(click)performClick();}
        return true;
    }
    public boolean getGlobalVisibleRect(android.graphics.Rect output){
        if(!shown||visibility!=VISIBLE||!attached||getWidth()<=0||getHeight()<=0)return false;
        output.set(getLeft(),getTop(),getLeft()+getWidth(),getTop()+getHeight());return true;
    }
    private int importantForAccessibility;
    public void setImportantForAccessibility(int value) { importantForAccessibility=value; }
    public int getImportantForAccessibility() { return importantForAccessibility; }
    /** Opt-in real padding for owned text allocation checks; legacy fixtures keep their old zero-padding contract. */
    public static boolean keepPaddingForCheck;
    private int checkPaddingLeft,checkPaddingTop,checkPaddingRight,checkPaddingBottom;
    public void setPadding(int left,int top,int right,int bottom) {
        if(keepPaddingForCheck){checkPaddingLeft=left;checkPaddingTop=top;checkPaddingRight=right;checkPaddingBottom=bottom;}
    }
    private final java.util.Map<Integer,Object> keyedTags=new java.util.HashMap<>();
    public Object getTag(int key) {return keyedTags.get(key);}
    public Object getTag() {return null;}
    public void setTag(int key,Object value) {if(value==null)keyedTags.remove(key);else keyedTags.put(key,value);}
    private void drawBackground(android.graphics.Canvas canvas) { if (getBackground()!=null) getBackground().draw(canvas); }
    public int getScrollX() {return 0;}
    public int getScrollY() {return 0;}
    public int getPaddingLeft() {return checkPaddingLeft;}
    public int getPaddingTop() {return checkPaddingTop;}
    public int getPaddingRight() {return checkPaddingRight;}
    public int getPaddingBottom() {return checkPaddingBottom;}
    public int getPaddingStart() {return getLayoutDirection()==LAYOUT_DIRECTION_RTL?getPaddingRight():getPaddingLeft();}
    public int getPaddingEnd() {return getLayoutDirection()==LAYOUT_DIRECTION_RTL?getPaddingLeft():getPaddingRight();}
    public void setPaddingRelative(int start,int top,int end,int bottom) { }
    public ViewGroup.LayoutParams getLayoutParams(){return layoutParams;}
    public void setLayoutParams(ViewGroup.LayoutParams params){layoutParams=params;requestLayout();}
    public void setClipBounds(android.graphics.Rect value) {clipBounds=value;}
    public android.graphics.Rect getClipBounds() {return clipBounds;}
    public boolean getClipBounds(android.graphics.Rect result) {if(clipBounds==null)return false;result.left=clipBounds.left;result.top=clipBounds.top;result.right=clipBounds.right;result.bottom=clipBounds.bottom;return true;}
    public ViewOutlineProvider getOutlineProvider(){return outlineProvider;}
    public void setOutlineProvider(ViewOutlineProvider value){outlineProvider=value;}
    public boolean getClipToOutline(){return clipToOutline;}
    public void setClipToOutline(boolean value){clipToOutline=value;}
    public View findViewById(int id) { return id>=0&&getId()==id?this:null; }
    private int viewId=-1;
    public int getId() { return viewId; }
    public void setId(int value) {viewId=value;}
    public CharSequence getContentDescription() { return description; }
    public void setContentDescription(CharSequence value) { description=value; }
    public int getMinimumWidth() { return minimumWidth; }
    public void setMinimumWidth(int value) { minimumWidth = value; requestLayout(); }
    public int getMinimumHeight() { return minimumHeight; }
    public void setMinimumHeight(int value) { minimumHeight=value;requestLayout(); }
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean value){enabled=value;}
    public boolean isSelected(){return selected;}
    public void setSelected(boolean value){selected=value;}
    private boolean activatedForCheck;
    public boolean isActivated(){return activatedForCheck;}
    public void setActivated(boolean value){activatedForCheck=value;}
    public void setLayoutDirection(int value) { }
    public int getVisibility() { return visibility; }
    public int[] getDrawableState(){return new int[0];}
    public void setVisibility(int value) { visibility = value; }
    public ViewParent getParent() { return parent; }
    public View getRootView() {return parent instanceof View?((View)parent).getRootView():this;}
    private android.graphics.Matrix animationMatrix;
    public void setAnimationMatrix(android.graphics.Matrix value){animationMatrix=null;if(value!=null){animationMatrix=new android.graphics.Matrix();animationMatrix.set(value);}}
    public android.graphics.Matrix getAnimationMatrix(){return animationMatrix;}
    public WindowInsets windowInsetsForCheck;
    public WindowInsets getRootWindowInsets() {return windowInsetsForCheck;}
    public android.os.IBinder getWindowToken() {return null;}
    public Display getDisplay() {return null;}
    public void draw(android.graphics.Canvas canvas) { }
    public android.graphics.RenderEffect renderEffect;
    public int renderEffectWrites;
    public void setRenderEffect(android.graphics.RenderEffect effect){renderEffect=effect;renderEffectWrites++;}
    public void postInvalidate() {postedInvalidations++;}
    public long lastDelayedInvalidateForCheck;
    public void postInvalidateDelayed(long delay) {postedInvalidations++;lastDelayedInvalidateForCheck=delay;}
    public void postInvalidateOnAnimation() {postedInvalidations++;}
    public Resources getResources() { return resources; }
    public void requestLayout() { layoutRequests++;layoutRequested=true; }
    public int forcedLayouts;
    public void forceLayout() { forcedLayouts++;layoutRequested=true; }
    public boolean canScrollUpForCheck;
    public boolean canScrollVertically(int direction){return direction<0&&canScrollUpForCheck;}
    public void invalidate() { invalidations++; }
    public boolean isDirty() { return dirty; }
    public void invalidateOutline() { invalidations++; }
    public void invalidateDrawable(Drawable drawable) { invalidate(); }
    public void scheduleDrawable(Drawable drawable, Runnable task, long when) { }
    public void unscheduleDrawable(Drawable drawable, Runnable task) { }
    private Drawable background;
    public Drawable getBackground(){return background;}
    public void setBackground(Drawable value){background=value;}
    public void setBackgroundDrawable(Drawable value){setBackground(value);}
    private Drawable foreground;
    public Drawable getForeground(){return foreground;}
    public void setForeground(Drawable value){foreground=value;}
    public android.graphics.Matrix getMatrix(){return new android.graphics.Matrix();}
    public void transformMatrixToGlobal(android.graphics.Matrix matrix){
        if(parent instanceof View)((View)parent).transformMatrixToGlobal(matrix);
        matrix.preTranslate(getLeft()+getTranslationX(),getTop()+getTranslationY());
    }
    public void transformMatrixToLocal(android.graphics.Matrix matrix){
        android.graphics.Matrix global=new android.graphics.Matrix(),inverse=new android.graphics.Matrix();
        transformMatrixToGlobal(global);global.invert(inverse);matrix.preConcat(inverse);
    }
    /** Opt in per View to the real queued post/remove ordering. Existing
     * synchronous fixture consumers keep their original behavior. */
    public boolean queuePostedForCheck;
    public int postCallsForCheck;
    public final java.util.List<Runnable> postedTasksForCheck=new java.util.ArrayList<>();
    public boolean post(Runnable task){postCallsForCheck++;if(queuePostedForCheck)postedTasksForCheck.add(task);else task.run();return true;}
    /** Queued, not run: a check runs delayed work explicitly with {@link #runDelayedForCheck}. */
    public final java.util.List<Runnable> delayedTasks=new java.util.ArrayList<>();
    public boolean postDelayed(Runnable task,long delay){delayedTasks.add(task);return true;}
    public boolean removeCallbacks(Runnable task){boolean removed=delayedTasks.remove(task);for(int i=postedTasksForCheck.size()-1;i>=0;i--)if(postedTasksForCheck.get(i)==task){postedTasksForCheck.remove(i);removed=true;}return removed;}
    public void runPostedForCheck(){java.util.List<Runnable> due=new java.util.ArrayList<>(postedTasksForCheck);postedTasksForCheck.clear();for(Runnable task:due)task.run();}
    public void runDelayedForCheck(){java.util.List<Runnable> due=new java.util.ArrayList<>(delayedTasks);delayedTasks.clear();for(Runnable task:due)task.run();}
    /** Queued, not run: a check drives display frames explicitly. */
    public final java.util.List<Runnable> animationFrames=new java.util.ArrayList<>();
    public void postOnAnimation(Runnable task){animationFrames.add(task);}
}
