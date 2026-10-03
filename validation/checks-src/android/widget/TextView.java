package android.widget;
import android.content.Context;
import android.graphics.Typeface;
public class TextView extends android.view.View {
    public static final int AUTO_SIZE_TEXT_TYPE_NONE=0,AUTO_SIZE_TEXT_TYPE_UNIFORM=1;
    private int autoSizeTextType;
    private CharSequence text="";
    private float size=13,spacing;
    private int color=0xccffffff;
    private final android.text.TextPaint paint=new android.text.TextPaint(1);
    public int variationWrites,typefaceWrites;
    /** Opt-in native text width measurement for HGHT's fixed-width clock checks. */
    public static boolean measureTextWidth;
    /** Opt-in emulation of Android's measured mLayout/checkForRelayout failure. */
    public boolean strictMeasuredRelayout;
    private boolean nativeLayoutBuilt,includeFontPadding=true;
    private String features;
    public TextView(Context context){super(context);paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(size);}
    public CharSequence getText(){return text;}
    public void setText(CharSequence value){checkMeasuredRelayout();text=value;if(strictMeasuredRelayout&&nativeLayoutBuilt)requestLayout();}
    protected void onMeasure(int width,int height){
        super.onMeasure(width,height);nativeLayoutBuilt=true;
        if(measureTextWidth)setMeasuredDimension((int)Math.ceil(paint.measureText(text,0,text.length())),getMeasuredHeight());
    }
    private void checkMeasuredRelayout(){
        if(strictMeasuredRelayout&&nativeLayoutBuilt&&getLayoutParams()==null)
            throw new NullPointerException("TextView.checkForRelayout: LayoutParams.width");
    }
    public void setSingleLine(boolean value){ }
    public void setHorizontallyScrolling(boolean value){ }
    public void setIncludeFontPadding(boolean value){includeFontPadding=value;}
    public boolean getIncludeFontPadding(){return includeFontPadding;}
    public void setFontFeatureSettings(String value){checkMeasuredRelayout();features=value;}
    public String getFontFeatureSettings(){return features;}
    public float getTextSize(){return size;}
    public int getAutoSizeTextType(){return autoSizeTextType;}
    public void setAutoSizeTextTypeWithDefaults(int value){
        if(value!=AUTO_SIZE_TEXT_TYPE_NONE&&value!=AUTO_SIZE_TEXT_TYPE_UNIFORM)throw new IllegalArgumentException("autoSizeTextType");
        if(autoSizeTextType!=value){autoSizeTextType=value;requestLayout();}
    }
    public void setTextSize(int unit,float value){checkMeasuredRelayout();size=value;paint.setTextSize(value);requestLayout();}
    public Typeface getTypeface(){return paint.getTypeface();}
    public void setTypeface(Typeface value){checkMeasuredRelayout();paint.setTypeface(value);typefaceWrites++;requestLayout();}
    public android.text.TextPaint getPaint(){return paint;}
    public String getFontVariationSettings(){return paint.getFontVariationSettings();}
    public boolean setFontVariationSettings(String value){
        String previous=paint.getFontVariationSettings();
        if(previous==value||(previous!=null&&previous.equals(value)))return true;
        boolean effective=paint.setFontVariationSettings(value);
        if(effective){variationWrites++;requestLayout();invalidate();}return effective;
    }
    public float getLetterSpacing(){return spacing;}
    public void setLetterSpacing(float value){spacing=value;paint.setLetterSpacing(value);requestLayout();}
    public int getCurrentTextColor(){return color;}
    public void setTextColor(int value){color=value;}
}
