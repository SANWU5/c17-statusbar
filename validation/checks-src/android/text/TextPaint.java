package android.text;
/** Only the real TextView.getPaint return type is needed by the native clock bridge checks. */
public class TextPaint extends android.graphics.Paint {
    public TextPaint(){super(0);}
    public TextPaint(int flags){super(flags);}
}
