package android.graphics;
public class Paint {
    public static final int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;
    public enum Align { LEFT, CENTER, RIGHT }
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public Paint() { }
    public enum Join { MITER, ROUND, BEVEL }
    public enum Cap { BUTT, ROUND, SQUARE }
    private Join strokeJoin=Join.MITER;
    private Cap strokeCap=Cap.BUTT;
    private float strokeMiter=4;
    public void setStrokeMiter(float value){strokeMiter=value;}
    public float getStrokeMiter(){return strokeMiter;}
    public static class FontMetrics { public float top,ascent,descent,bottom,leading; }
    public static class FontMetricsInt { public int top,ascent,descent,bottom,leading; }
    private int alpha=255,color;
    private float textSize=14;
    private float letterSpacing;
    private float textScaleX=1f,textSkewX;
    public float getTextScaleX(){return textScaleX;}public void setTextScaleX(float value){textScaleX=value;}
    public float getTextSkewX(){return textSkewX;}public void setTextSkewX(float value){textSkewX=value;}
    private Typeface typeface;
    private String variationSettings;
    private String featureSettings;
    /** Android 36+ can store axes on Paint without changing its Typeface. */
    public static boolean fontVariationOnPaint;
    public static boolean includeLetterSpacingInMeasure;
    public boolean fakeBold;
    public Xfermode mode;
    public Style style=Style.FILL;
    public float strokeWidth;
    public PathEffect pathEffect;
    public Shader shader;
    public float shadowRadius,shadowDx,shadowDy;
    public int shadowColor;
    private ColorFilter colorFilter;
    private int flags;
    public Paint(int flags) { this.flags=flags; }
    public int getFlags(){return flags;}
    public void set(Paint source) {flags=source.flags;alpha=source.alpha;color=source.color;textSize=source.textSize;mode=source.mode;letterSpacing=source.letterSpacing;textScaleX=source.textScaleX;textSkewX=source.textSkewX;featureSettings=source.featureSettings;typeface=source.typeface;fakeBold=source.fakeBold;variationSettings=source.variationSettings;colorFilter=source.colorFilter;shader=source.shader;style=source.style;strokeWidth=source.strokeWidth;strokeMiter=source.strokeMiter;strokeCap=source.strokeCap;strokeJoin=source.strokeJoin;pathEffect=source.pathEffect;shadowRadius=source.shadowRadius;shadowDx=source.shadowDx;shadowDy=source.shadowDy;shadowColor=source.shadowColor;}
    public void setAlpha(int value) {alpha=value;}
    public void setShadowLayer(float radius,float dx,float dy,int color){shadowRadius=radius;shadowDx=dx;shadowDy=dy;shadowColor=color;}
    public int getAlpha() {return alpha;}
    public void setColor(int value) {color=value;alpha=value>>>24;}
    public int getColor() {return color;}
    public Xfermode setXfermode(Xfermode value) {Xfermode old=mode;mode=value;return old;}
    public void setStyle(Style value) {style=value;}
    public Style getStyle(){return style;}
    public float getStrokeWidth(){return strokeWidth;}
    public boolean getFillPath(Path src,Path dst){
        java.awt.geom.Area shape=new java.awt.geom.Area(src.rasterPath);
        if(style!=Style.FILL){
            int cap=strokeCap==Cap.ROUND?java.awt.BasicStroke.CAP_ROUND:strokeCap==Cap.SQUARE?java.awt.BasicStroke.CAP_SQUARE:java.awt.BasicStroke.CAP_BUTT;
            int join=strokeJoin==Join.ROUND?java.awt.BasicStroke.JOIN_ROUND:strokeJoin==Join.BEVEL?java.awt.BasicStroke.JOIN_BEVEL:java.awt.BasicStroke.JOIN_MITER;
            java.awt.geom.Area outline=new java.awt.geom.Area(new java.awt.BasicStroke(Math.max(.001f,strokeWidth),cap,join,Math.max(1f,strokeMiter)).createStrokedShape(src.rasterPath));
            if(style==Style.STROKE)shape=outline;else shape.add(outline);
        }
        dst.setShapeForCheck(shape);return true;
    }
    public Join getStrokeJoin(){return strokeJoin;}
    public Cap getStrokeCap(){return strokeCap;}
    public void setStrokeJoin(Join value) {strokeJoin=value;}
    public void setStrokeCap(Cap value) {strokeCap=value;}
    public void setStrokeWidth(float value) {strokeWidth=value;}
    public PathEffect setPathEffect(PathEffect value) {PathEffect old=pathEffect;pathEffect=value;return old;}
    public PathEffect getPathEffect() {return pathEffect;}
    public Shader setShader(Shader value) {Shader old=shader;shader=value;return old;}
    public Shader getShader() {return shader;}
    public ColorFilter setColorFilter(ColorFilter value){ColorFilter old=colorFilter;colorFilter=value;return old;}
    public ColorFilter getColorFilter(){return colorFilter;}
    public float getTextSize() {return textSize;}
    public static int textBoundsCallsForCheck;
    public boolean equalsForTextMeasurement(Paint other){
        return textSize==other.textSize && textScaleX==other.textScaleX && textSkewX==other.textSkewX
            && typeface==other.typeface && flags==other.flags && fakeBold==other.fakeBold && letterSpacing==other.letterSpacing
            && java.util.Objects.equals(variationSettings,other.variationSettings) && java.util.Objects.equals(featureSettings,other.featureSettings);
    }
    public void setTextSize(float value) {textSize=value;}
    public void getTextBounds(String value,int start,int end,Rect bounds) {
        textBoundsCallsForCheck++;
        // HGHT has a fixed base, unlike a direct percentage of the em size.
        float ink=textSize*(typeface!=null&&!Float.isNaN(typeface.variationHeight)
                ?.2f+.8f*typeface.variationHeight/70f:1f);
        bounds.left=0;bounds.right=(int)((end-start)*textSize*.6f);bounds.top=-(int)ink;bounds.bottom=0;
    }
    public void getTextBounds(CharSequence value,int start,int end,Rect bounds){getTextBounds(value.toString(),start,end,bounds);}
    public float measureText(CharSequence text,int start,int end) {
        return Math.max(0f, (end-start)*textSize*.6f
                + (includeLetterSpacingInMeasure ? Math.max(0,end-start-1)*letterSpacing*textSize : 0f));
    }
    public float measureText(String text) { return text == null ? 0f : measureText(text, 0, text.length()); }
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
    public String getFontFeatureSettings(){return featureSettings;}
    public void setFontFeatureSettings(String value){featureSettings=value;}
    public void setLetterSpacing(float value) {letterSpacing=value;}
    public void setFakeBoldText(boolean bold) {fakeBold=bold;}
    public void setTextAlign(Align align) { }
    public static int fontMetricsObjectsForCheck;
    public FontMetrics getFontMetrics(){fontMetricsObjectsForCheck++;FontMetrics metrics=new FontMetrics();getFontMetrics(metrics);return metrics;}
    public float getFontMetrics(FontMetrics metrics) {metrics.ascent=-textSize*.8f;metrics.descent=textSize*.2f;metrics.top=-textSize*.9f;metrics.bottom=textSize*.25f;return textSize;}
    public int getFontMetricsInt(FontMetricsInt metrics){metrics.top=metrics.ascent=(int)Math.floor(-textSize*.8f);metrics.bottom=metrics.descent=(int)Math.ceil(textSize*.2f);metrics.leading=0;return metrics.descent-metrics.ascent;}
}
