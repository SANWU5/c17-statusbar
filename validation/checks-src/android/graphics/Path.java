package android.graphics;
public class Path {
    public enum Direction {CW,CCW}
    public void addRoundRect(RectF rect,float x,float y,Direction direction){}
    public void reset() { }
    public void moveTo(float x,float y) { }
    public void lineTo(float x,float y) { }
    public void close() { }
}
