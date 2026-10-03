package android.text.style;
public abstract class MetricAffectingSpan {
    public abstract void updateDrawState(android.text.TextPaint paint);
    public abstract void updateMeasureState(android.text.TextPaint paint);
}
