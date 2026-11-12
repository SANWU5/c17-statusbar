package android.view;
import android.content.Context;
public class ViewGroup extends View {
    public static class LayoutParams {public static final int MATCH_PARENT=-1,WRAP_CONTENT=-2;public int width,height;public LayoutParams(int width,int height){this.width=width;this.height=height;}}
    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin,topMargin,rightMargin,bottomMargin;
        private int start=Integer.MIN_VALUE,end=Integer.MIN_VALUE;
        public MarginLayoutParams(int width,int height){super(width,height);}
        public MarginLayoutParams(MarginLayoutParams source){super(source.width,source.height);leftMargin=source.leftMargin;topMargin=source.topMargin;rightMargin=source.rightMargin;bottomMargin=source.bottomMargin;start=source.start;end=source.end;}
        public int getMarginStart(){return start==Integer.MIN_VALUE?leftMargin:start;}
        public int getMarginEnd(){return end==Integer.MIN_VALUE?rightMargin:end;}
        public void setMarginStart(int value){start=value;}
        public void setMarginEnd(int value){end=value;}
        public boolean isMarginRelative(){return start!=Integer.MIN_VALUE||end!=Integer.MIN_VALUE;}
    }
    private boolean clipChildren=true,clipPadding=true;
    private final java.util.List<View> children=new java.util.ArrayList<>();
    // Framework child array, mirrored for read-only traversal contracts.
    private View[] mChildren=new View[0];
    private void syncChildrenForCheck(){mChildren=children.toArray(new View[0]);}
    private View touchTargetForCheck;
    private final ViewGroupOverlay groupOverlay=new ViewGroupOverlay(this);
    public ViewGroup(Context context) { super(context); }
    protected void onLayout(boolean changed,int l,int t,int r,int b){}
    protected void dispatchDraw(android.graphics.Canvas canvas){}
    @Override public ViewGroupOverlay getOverlay(){return groupOverlay;}
    public boolean getClipChildren() {return clipChildren;}
    public boolean getClipToPadding() {return clipPadding;}
    public void setClipChildren(boolean value) {clipChildren=value;}
    public void setClipToPadding(boolean value) {clipPadding=value;}
    public void addView(View view) {children.add(view);view.parent=this;syncChildrenForCheck();}
    public void addView(View view,LayoutParams params) {view.setLayoutParams(params);addView(view);}
    public void addView(View view,int index,LayoutParams params) {view.setLayoutParams(params);children.add(index,view);view.parent=this;syncChildrenForCheck();}
    public void removeView(View view) {if(children.remove(view)&&view.parent==this)view.parent=null;syncChildrenForCheck();}
    public void removeAllViews(){for(View child:new java.util.ArrayList<>(children))removeView(child);}
    public boolean onInterceptTouchEvent(MotionEvent event){return false;}
    public int getChildCount() {return children.size();}
    public View getChildAt(int position) {return children.get(position);}
    @Override public View findViewById(int id){View own=super.findViewById(id);if(own!=null)return own;for(View child:children){View found=child.findViewById(id);if(found!=null)return found;}return null;}
    public void requestDisallowInterceptTouchEvent(boolean disallow){if(getParent() instanceof ViewGroup)((ViewGroup)getParent()).requestDisallowInterceptTouchEvent(disallow);}
    public int indexOfChild(View child) {for(int i=0;i<children.size();i++)if(children.get(i)==child)return i;return -1;}
    /** Native hit testing enters a child only through its parent's rectangle;
     * clipChildren=false does not enlarge that touch rectangle. */
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) {
            touchTargetForCheck=null;
            if(event.getX()<0||event.getX()>=getWidth()||event.getY()<0||event.getY()>=getHeight())return false;
            for(int i=children.size()-1;i>=0;i--) {
                View child=children.get(i);float x=event.getX()-child.getLeft()-child.getTranslationX(),y=event.getY()-child.getTop()-child.getTranslationY();
                if(child.getVisibility()!=View.VISIBLE||x<0||x>=child.getWidth()||y<0||y>=child.getHeight())continue;
                if(child.dispatchTouchEvent(new MotionEvent(action,x,y,event.getEventTime()))){touchTargetForCheck=child;return true;}
            }
        } else if(touchTargetForCheck!=null) {
            View target=touchTargetForCheck;
            boolean result=target.dispatchTouchEvent(new MotionEvent(action,event.getX()-target.getLeft()-target.getTranslationX(),event.getY()-target.getTop()-target.getTranslationY(),event.getEventTime()));
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)touchTargetForCheck=null;
            return result;
        }
        return super.dispatchTouchEvent(event);
    }
    private android.animation.LayoutTransition layoutTransition;
    public void setLayoutTransition(android.animation.LayoutTransition value){layoutTransition=value;}
    public android.animation.LayoutTransition getLayoutTransition(){return layoutTransition;}
}
