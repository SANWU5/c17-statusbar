package android.text.style;
public abstract class ReplacementSpan extends MetricAffectingSpan {
    public abstract int getSize(android.graphics.Paint paint,CharSequence text,int start,int end,android.graphics.Paint.FontMetricsInt metrics);
    public abstract void draw(android.graphics.Canvas canvas,CharSequence text,int start,int end,float x,int top,int baseline,int bottom,android.graphics.Paint paint);
    public void updateDrawState(android.text.TextPaint paint){}
    public void updateMeasureState(android.text.TextPaint paint){}
}
