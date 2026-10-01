package com.oplus.systemui.qs.base.seek;
import android.view.View;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
public class OplusQsVerticalSeekBar extends com.coui.appcompat.seekbar.COUIVerticalSeekBar {
    public final Rect mClipProgressRect=new Rect(0,0,100,300);
    public final Paint mProgressPaint=new Paint(1);
    public int mProgressColor=0xffffffff;
    public Drawable activeMixColorDrawable;
    public float blurRadius=56f;
    public int radiusUpdates;
    public int failRadiusCalls;
    public boolean failRadius;
    public OplusQsVerticalSeekBar(){super();}
    public void updateBaseMixColorDrawableRadius(float radius){radiusUpdates++;blurRadius=radius;if(failRadius||failRadiusCalls>0){if(failRadiusCalls>0)failRadiusCalls--;throw new IllegalStateException("native blur update");}}
}
