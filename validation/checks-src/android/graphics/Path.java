package android.graphics;
public class Path {
    public enum Direction {CW,CCW}
    public float nativeRadiusX,nativeRadiusY;
    private final RectF recordedBounds=new RectF();
    private boolean empty=true;
    public void addRoundRect(RectF rect,float x,float y,Direction direction){recordedBounds.set(rect.left,rect.top,rect.right,rect.bottom);nativeRadiusX=x;nativeRadiusY=y;empty=false;}
    public void reset() {empty=true;}
    public boolean isEmpty(){return empty;}
    public void computeBounds(RectF output,boolean exact){output.set(recordedBounds.left,recordedBounds.top,recordedBounds.right,recordedBounds.bottom);}
    public void set(Path other){other.computeBounds(recordedBounds,true);nativeRadiusX=other.nativeRadiusX;nativeRadiusY=other.nativeRadiusY;empty=other.empty;}
    public void moveTo(float x,float y) { }
    public void lineTo(float x,float y) { }
    public void close() { }
}
