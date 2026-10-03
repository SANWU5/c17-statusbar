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
    private float letterSpacing;
    private Typeface typeface;
    private String variationSettings;
    /** Android 36+ can store axes on Paint without changing its Typeface. */
    public static boolean fontVariationOnPaint;
    public boolean fakeBold;
    public Xfermode mode;
    public Style style=Style.FILL;
    public float strokeWidth;
    public PathEffect pathEffect;
    public Shader shader;
    private ColorFilter colorFilter;
    public Paint(int flags) { }
    public void set(Paint source) {alpha=source.alpha;color=source.color;textSize=source.textSize;mode=source.mode;letterSpacing=source.letterSpacing;typeface=source.typeface;fakeBold=source.fakeBold;variationSettings=source.variationSettings;colorFilter=source.colorFilter;}
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
    public ColorFilter setColorFilter(ColorFilter value){ColorFilter old=colorFilter;colorFilter=value;return old;}
    public ColorFilter getColorFilter(){return colorFilter;}
    public float getTextSize() {return textSize;}
    public void setTextSize(float value) {textSize=value;}
    public void getTextBounds(String value,int start,int end,Rect bounds) {bounds.left=0;bounds.right=(int)((end-start)*textSize*.6f);bounds.top=-(int)textSize;bounds.bottom=0;}
    public float measureText(CharSequence text,int start,int end) {return (end-start)*textSize*.6f;}
    public Typeface getTypeface() {return typeface;}
    public Typeface setTypeface(Typeface face) {Typeface old=typeface;typeface=face;return old;}
    public String getFontVariationSettings() {return variationSettings;}
    public boolean setFontVariationSettings(String settings) {
        android.graphics.fonts.FontVariationAxis[] axes=android.graphics.fonts.FontVariationAxis.fromFontVariationSettings(settings);
        Typeface base=typeface==null?Typeface.DEFAULT:typeface;
        if(fontVariationOnPaint){variationSettings=settings;return true;}
        if(settings==variationSettings||(settings!=null&&settings.equals(variationSettings)))return true;
        if(axes!=null&&!base.variableWeight)return false;
        variationSettings=settings;typeface=Typeface.withVariation(base,axes);return true;
    }
    public float effectiveWeight() {
        Typeface base=typeface==null?Typeface.DEFAULT:typeface;
        if(fontVariationOnPaint&&base.variableWeight&&variationSettings!=null){
            for(android.graphics.fonts.FontVariationAxis axis:android.graphics.fonts.FontVariationAxis.fromFontVariationSettings(variationSettings))
                if("wght".equals(axis.getTag()))return axis.getStyleValue();
        }
        return base.effectiveWeight();
    }
    public float getLetterSpacing() {return letterSpacing;}
    public void setLetterSpacing(float value) {letterSpacing=value;}
    public void setFakeBoldText(boolean bold) {fakeBold=bold;}
    public void setTextAlign(Align align) { }
    public float getFontMetrics(FontMetrics metrics) {metrics.ascent=-textSize*.8f;metrics.descent=textSize*.2f;return textSize;}
}
