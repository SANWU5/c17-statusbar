package android.view;
public class MotionEvent {
    public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
    private final int action;
    private float x,y;private long time;private int pointers=1;
    public MotionEvent(int action) { this.action = action; }
    public MotionEvent(int action,float x,float y,long time){this.action=action;this.x=x;this.y=y;this.time=time;}
    public int getActionMasked() { return action; }
    public int getPointerCount(){return pointers;}
    public float getX(){return x;}public float getY(){return y;}
    public long getEventTime(){return time;}
}
