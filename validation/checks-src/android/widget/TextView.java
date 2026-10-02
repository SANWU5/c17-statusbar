package android.widget;
import android.content.Context;
import android.graphics.Typeface;
public class TextView extends android.view.View {
    private CharSequence text="";
    private float size=13,spacing;
    private int color=0xccffffff;
    private final android.text.TextPaint paint=new android.text.TextPaint(1);
    public int variationWrites,typefaceWrites;
    public TextView(Context context){super(context);paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(size);}
    public CharSequence getText(){return text;}
    public void setText(CharSequence value){text=value;}
    public float getTextSize(){return size;}
    public void setTextSize(int unit,float value){size=value;paint.setTextSize(value);requestLayout();}
    public Typeface getTypeface(){return paint.getTypeface();}
    public void setTypeface(Typeface value){paint.setTypeface(value);typefaceWrites++;requestLayout();}
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
