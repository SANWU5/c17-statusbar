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
    public Drawable baseMixColorDrawable,activeMixColorDrawable;
    public float mirrorScaleValue=1f;
    public boolean isSupportStroke,isDetailToggle;
    public float blurRadius=56f;
    public int radiusUpdates;
    public int failRadiusCalls;
    public boolean failRadius;
    public dev.puitheme.QsPanelCorners cornerHook;
    public float visibleBlurWeight=.7f;
    public OplusQsVerticalSeekBar(){super();}
    public void updateBaseMixColorDrawableRadius(float radius){
        dev.puitheme.QsPanelCorners.BlurScope scope=cornerHook==null?null:cornerHook.beginBlurUpdate(this,radius);
        try {
            Object[] args={this,mClipProgressRect,scope==null?radius:scope.radius,mBackgroundRoundCornerWeight,activeMixColorDrawable,false,mirrorScaleValue,true};
            if(cornerHook!=null)args=cornerHook.blurShape("applySeekBarActiveBlurConfig",args);
            radiusUpdates++;blurRadius=((Number)args[2]).floatValue();visibleBlurWeight=((Number)args[3]).floatValue();
            if(failRadius||failRadiusCalls>0){if(failRadiusCalls>0)failRadiusCalls--;throw new IllegalStateException("native blur update");}
        } finally {if(scope!=null)scope.close();}
    }
}
