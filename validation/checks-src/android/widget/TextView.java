package android.widget;
import android.content.Context;
import android.graphics.Typeface;
public class TextView extends android.view.View {
    private CharSequence text="";
    private float size=13,spacing;
    private int color=0xccffffff;
    private Typeface face=Typeface.DEFAULT;
    public TextView(Context context){super(context);}
    public CharSequence getText(){return text;}
    public void setText(CharSequence value){text=value;}
    public float getTextSize(){return size;}
    public void setTextSize(int unit,float value){size=value;requestLayout();}
    public Typeface getTypeface(){return face;}
    public void setTypeface(Typeface value){face=value;requestLayout();}
    public float getLetterSpacing(){return spacing;}
    public void setLetterSpacing(float value){spacing=value;requestLayout();}
    public int getCurrentTextColor(){return color;}
    public void setTextColor(int value){color=value;}
}
