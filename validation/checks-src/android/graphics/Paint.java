package android.graphics;
public class Paint {
    public static final int ANTI_ALIAS_FLAG=1;
    public enum Align { LEFT, CENTER, RIGHT }
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Join { MITER, ROUND, BEVEL }
    public enum Cap { BUTT, ROUND, SQUARE }
    public static class FontMetrics { public float top,ascent,descent,bottom,leading; }
    private int alpha=255,color;
    private float textSize=14;
    public Xfermode mode;
    public Style style=Style.FILL;
    public float strokeWidth;
    public PathEffect pathEffect;
    public Shader shader;
    public Paint(int flags) { }
    public void set(Paint source) {alpha=source.alpha;color=source.color;textSize=source.textSize;mode=source.mode;}
    public void setAlpha(int value) {alpha=value;}
    public int getAlpha() {return alpha;}
    public void setColor(int value) {color=value;alpha=value>>>24;}
    public int getColor() {return color;}
    public Xfermode setXfermode(Xfermode value) {Xfermode old=mode;mode=value;return old;}
    public void setStyle(Style value) {style=value;}
    public void setStrokeJoin(Join value) { }
    public void setStrokeCap(Cap value) { }
    public void setStrokeWidth(float value) {strokeWidth=value;}
    public PathEffect setPathEffect(PathEffect value) {PathEffect old=pathEffect;pathEffect=value;return old;}
    public Shader setShader(Shader value) {Shader old=shader;shader=value;return old;}
    public Shader getShader() {return shader;}
    public float getTextSize() {return textSize;}
    public void setTextSize(float value) {textSize=value;}
    public void getTextBounds(String value,int start,int end,Rect bounds) {bounds.left=0;bounds.right=(int)((end-start)*textSize*.6f);bounds.top=-(int)textSize;bounds.bottom=0;}
    public Typeface setTypeface(Typeface face) { return face; }
    public void setFakeBoldText(boolean bold) { }
    public void setTextAlign(Align align) { }
    public float getFontMetrics(FontMetrics metrics) {metrics.ascent=-textSize*.8f;metrics.descent=textSize*.2f;return textSize;}
}
