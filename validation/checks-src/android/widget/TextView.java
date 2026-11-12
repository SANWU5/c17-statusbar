package android.widget;
import android.content.Context;
import android.graphics.Typeface;
public class TextView extends android.view.View {
    public enum BufferType { NORMAL,SPANNABLE,EDITABLE }
    public static final int AUTO_SIZE_TEXT_TYPE_NONE=0,AUTO_SIZE_TEXT_TYPE_UNIFORM=1;
    private int autoSizeTextType;
    private CharSequence text="";
    private float size=13,spacing;
    private int color=0xccffffff,gravity;
    private android.content.res.ColorStateList colors=android.content.res.ColorStateList.valueOf(color);
    private final android.text.TextPaint paint=new android.text.TextPaint(1);
    public int variationWrites,typefaceWrites;
    /** Opt-in native text width measurement for HGHT's fixed-width clock checks. */
    public static boolean measureTextWidth;
    /** Opt-in complete/tight font metrics for component ink clipping checks. */
    public static boolean nativeFontPaddingForCheck;
    /** Opt-in emulation of Android's measured mLayout/checkForRelayout failure. */
    public boolean strictMeasuredRelayout;
    public boolean platformMeasureSpecs;
    private boolean nativeLayoutBuilt,includeFontPadding=true,fallbackLineSpacing;
    private String features;
    private android.text.Layout layout;
    private android.text.TextUtils.TruncateAt ellipsize;
    public int layoutRebuilds, layoutClears;
    public TextView(Context context){super(context);paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(size);}
    @Override protected boolean setFrame(int left,int top,int right,int bottom){
        int oldWidth=getWidth(),oldHeight=getHeight();boolean changed=super.setFrame(left,top,right,bottom);
        if(changed&&(oldWidth!=getWidth()||oldHeight!=getHeight()))nativeFrameChangedForCheck();return changed;
    }
    /** Native TextView.setFrame calls restartMarqueeIfNeeded after a real size change. */
    protected void nativeFrameChangedForCheck(){}
    public CharSequence getText(){return text;}
    public void setText(CharSequence value){checkMeasuredRelayout();text=value;layout=null;if(strictMeasuredRelayout&&nativeLayoutBuilt)requestLayout();}
    public void setText(CharSequence value,BufferType type){setText(value);}
    protected void onMeasure(int width,int height){
        super.onMeasure(width,height);nativeLayoutBuilt=true;
        if(measureTextWidth)setMeasuredDimension((int)Math.ceil(android.text.Layout.getDesiredWidth(text,0,text.length(),paint))+
                (android.view.View.keepPaddingForCheck?getPaddingLeft()+getPaddingRight():0),getMeasuredHeight());
        int baseline=Math.round(paint.getTextSize()*.8f);
        if(android.view.View.keepPaddingForCheck){android.graphics.Paint.FontMetricsInt fm=new android.graphics.Paint.FontMetricsInt();paint.getFontMetricsInt(fm);
            int top=fm.ascent,bottom=fm.descent;
            if(nativeFontPaddingForCheck){top=includeFontPadding?fm.top:fm.ascent;bottom=includeFontPadding?fm.bottom:fm.descent;baseline=-top;}
            setMeasuredDimension(getMeasuredWidth(),Math.max(0,bottom-top)+getPaddingTop()+getPaddingBottom());}
        else if(platformMeasureSpecs&&android.view.View.MeasureSpec.getMode(width)==android.view.View.MeasureSpec.EXACTLY)
            setMeasuredDimension(android.view.View.MeasureSpec.getSize(width),getMeasuredHeight());
        int contentWidth=Math.max(0,getMeasuredWidth()-getCompoundPaddingLeft()-getCompoundPaddingRight());
        if(layout==null||layout.getWidth()!=contentWidth){
            float glyphs=android.text.Layout.getDesiredWidth(text,0,text.length(),paint);
            layout=new android.text.Layout(contentWidth,ellipsize!=null&&glyphs>contentWidth?1:0,paint.getTextSize(),glyphs,text,paint,baseline);
            layoutRebuilds++;
        }
    }
    private void checkMeasuredRelayout(){
        if(strictMeasuredRelayout&&nativeLayoutBuilt&&getLayoutParams()==null)
            throw new NullPointerException("TextView.checkForRelayout: LayoutParams.width");
    }
    public void setSingleLine(boolean value){ }
    public void setHorizontallyScrolling(boolean value){ }
    public void setIncludeFontPadding(boolean value){includeFontPadding=value;}
    public boolean getIncludeFontPadding(){return includeFontPadding;}
    public void setFallbackLineSpacing(boolean value){fallbackLineSpacing=value;}
    public boolean isFallbackLineSpacing(){return fallbackLineSpacing;}
    public void setFontFeatureSettings(String value){checkMeasuredRelayout();features=value;}
    public String getFontFeatureSettings(){return features;}
    public int getGravity(){return gravity;}
    public void setGravity(int value){gravity=value;}
    public float getTextSize(){return paint.getTextSize();}
    public android.text.Layout getLayout(){return layout;}
    public void nullLayouts(){layout=null;layoutClears++;}
    public android.text.TextUtils.TruncateAt getEllipsize(){return ellipsize;}
    public void setEllipsize(android.text.TextUtils.TruncateAt value){
        if(ellipsize!=value){ellipsize=value;nullLayouts();requestLayout();}
    }
    public int getCompoundPaddingLeft(){return getPaddingLeft();}
    public int getExtendedPaddingTop(){return getCompoundPaddingTop();}
    public int getExtendedPaddingBottom(){return getCompoundPaddingBottom();}
    public int getBaseline(){return getCompoundPaddingTop()+(layout==null?0:layout.getLineBaseline(0));}
    public int getCompoundPaddingTop(){return getPaddingTop();}
    public int getTotalPaddingLeft(){return getCompoundPaddingLeft();}
    public int getTotalPaddingTop(){return getExtendedPaddingTop();}
    public int getCompoundPaddingBottom(){return getPaddingBottom();}
    public int getCompoundPaddingRight(){return getPaddingRight();}
    public int getAutoSizeTextType(){return autoSizeTextType;}
    public void setAutoSizeTextTypeWithDefaults(int value){
        if(value!=AUTO_SIZE_TEXT_TYPE_NONE&&value!=AUTO_SIZE_TEXT_TYPE_UNIFORM)throw new IllegalArgumentException("autoSizeTextType");
        if(autoSizeTextType!=value){autoSizeTextType=value;requestLayout();}
    }
    public void setTextSize(int unit,float value){checkMeasuredRelayout();
        size=value;
        if(paint.getTextSize()!=value){paint.setTextSize(value);layout=null;requestLayout();}
    }
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
    public android.content.res.ColorStateList getTextColors(){return colors;}
    public void setTextColor(int value){color=value;colors=android.content.res.ColorStateList.valueOf(value);}
    public void setTextColor(android.content.res.ColorStateList value){colors=value;color=value.getDefaultColor();}
    public int compoundDrawableReadsForCheck;
    private android.graphics.drawable.Drawable[] compoundForCheck=new android.graphics.drawable.Drawable[4];
    private android.graphics.drawable.Drawable[] relativeCompoundForCheck=new android.graphics.drawable.Drawable[4];
    public android.graphics.drawable.Drawable[] getCompoundDrawables(){compoundDrawableReadsForCheck++;return compoundForCheck.clone();}
    public android.graphics.drawable.Drawable[] getCompoundDrawablesRelative(){compoundDrawableReadsForCheck++;return relativeCompoundForCheck.clone();}
    public void setCompoundDrawables(android.graphics.drawable.Drawable left,android.graphics.drawable.Drawable top,android.graphics.drawable.Drawable right,android.graphics.drawable.Drawable bottom){compoundForCheck=new android.graphics.drawable.Drawable[]{left,top,right,bottom};}
    public void setCompoundDrawablesRelative(android.graphics.drawable.Drawable start,android.graphics.drawable.Drawable top,android.graphics.drawable.Drawable end,android.graphics.drawable.Drawable bottom){relativeCompoundForCheck=new android.graphics.drawable.Drawable[]{start,top,end,bottom};}
    public float getShadowRadius(){return paint.shadowRadius;}
    public void setShadowLayer(float radius,float dx,float dy,int color){paint.setShadowLayer(radius,dx,dy,color);}
    protected void onDraw(android.graphics.Canvas canvas){
        if(layout==null)return;int saved=canvas.save();int previous=paint.getColor();
        try{paint.setColor(color);canvas.translate(getCompoundPaddingLeft()-getScrollX(),getTotalPaddingTop()-getScrollY());layout.draw(canvas);}
        finally{paint.setColor(previous);canvas.restoreToCount(saved);}
    }
}
