package android.widget;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

/** Single-child vertical viewport; horizontal gestures remain with the native child. */
public class ScrollView extends FrameLayout {
    private boolean fillViewport,dragging;
    private int scrollY,startY;
    private float downX,downY;
    public ScrollView(Context context){super(context);useLayoutBoundsForCheck=true;}
    public void setFillViewport(boolean value){fillViewport=value;}
    public boolean isFillViewport(){return fillViewport;}
    @Override protected void onMeasure(int width,int height){
        int w=MeasureSpec.getSize(width),h=MeasureSpec.getSize(height);
        setMeasuredDimension(w,h);
        if(getChildCount()==1){
            View child=getChildAt(0);int cw=child.getLayoutParams().width;
            child.measure(MeasureSpec.makeMeasureSpec(cw>=0?cw:w,MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h,MeasureSpec.UNSPECIFIED));
        }
    }
    @Override public void layout(int l,int t,int r,int b){super.layout(l,t,r,b);onLayout(true,l,t,r,b);}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        if(getChildCount()==1){View child=getChildAt(0);child.layout(0,0,child.getMeasuredWidth(),child.getMeasuredHeight());}
        scrollTo(0,scrollY);
    }
    private int range(){return getChildCount()==0?0:Math.max(0,getChildAt(0).getMeasuredHeight()-getHeight());}
    public void scrollTo(int x,int y){scrollY=Math.max(0,Math.min(y,range()));}
    public void scrollBy(int x,int y){scrollTo(0,scrollY+y);}
    @Override public int getScrollY(){return scrollY;}
    @Override public boolean canScrollVertically(int direction){return direction<0?scrollY>0:scrollY<range();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();startY=scrollY;dragging=false;}
        if(action==MotionEvent.ACTION_MOVE){
            float dy=event.getY()-downY,dx=event.getX()-downX;
            if(Math.abs(dy)>8&&Math.abs(dy)>Math.abs(dx))dragging=true;
            if(dragging){scrollTo(0,startY-Math.round(dy));return true;}
        }
        if(action==MotionEvent.ACTION_UP&&dragging){dragging=false;return true;}
        return super.dispatchTouchEvent(event);
    }
}
