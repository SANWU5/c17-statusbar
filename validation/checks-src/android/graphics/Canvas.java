package android.graphics;
public class Canvas {
    public boolean contrast;
    public int textAlpha=-1,pathAlpha=-1,saves,restores;
    public int maskAlpha=-1,pathColor,textColor,clips;
    public Paint.Style maskStyle;
    public float maskStroke;
    public PorterDuff.Mode foregroundMode;
    public float translateX,translateY,scaleX=1f,scaleY=1f,pivotX,pivotY;
    public String text;
    public int save() {return ++saves;}
    public void restoreToCount(int count) {restores++;}
    public boolean clipRect(RectF rect) {clips++;return true;}
    public boolean isHighContrastTextEnabled() {return contrast;}
    public void translate(float x,float y) {translateX+=x;translateY+=y;}
    public void scale(float x,float y,float px,float py) {scaleX*=x;scaleY*=y;pivotX=px;pivotY=py;}
    public void drawText(String value,float x,float y,Paint paint) {text=value;textAlpha=paint.getAlpha();textColor=paint.getColor();}
    public void drawPath(Path value,Paint paint) {
        PorterDuff.Mode mode=paint.mode instanceof PorterDuffXfermode ? ((PorterDuffXfermode)paint.mode).mode : null;
        if(mode==PorterDuff.Mode.DST_OUT) {maskAlpha=paint.getAlpha();maskStyle=paint.style;maskStroke=paint.strokeWidth;}
        else {pathAlpha=paint.getAlpha();pathColor=paint.getColor();foregroundMode=mode;}
    }
}
