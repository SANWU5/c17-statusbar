package android.view;
public class MotionEvent {
    public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
    private final int action;
    private float x,y,rawY;private long time;private int pointers=1;
    public MotionEvent(int action) { this.action = action; }
    public MotionEvent(int action,float x,float y,long time){this.action=action;this.x=x;this.y=y;this.rawY=y;this.time=time;}
    public static MotionEvent obtain(long downTime,long eventTime,int action,float x,float y,int metaState){return new MotionEvent(action,x,y,eventTime);}
    public void recycle(){ }
    public int getActionMasked() { return action; }
    public int getPointerCount(){return pointers;}
    public float getX(){return x;}public float getY(){return y;}
    public float getRawY(){return rawY;}public void setRawYForCheck(float value){rawY=value;}
    public long getEventTime(){return time;}
}
