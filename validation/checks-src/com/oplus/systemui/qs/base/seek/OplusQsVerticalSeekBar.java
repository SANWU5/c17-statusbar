package com.oplus.systemui.qs.base.seek;
import android.view.View;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
public class OplusQsVerticalSeekBar extends View {
    public final Rect mClipProgressRect=new Rect(0,0,100,300);
    public final Paint mProgressPaint=new Paint(1);
    public int mProgressColor=0xffffffff;
    public Drawable activeMixColorDrawable;
    public OplusQsVerticalSeekBar(){super(new Context());}
}
