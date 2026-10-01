package com.coui.appcompat.seekbar;
import android.content.Context;
import android.view.View;
public class COUIVerticalSeekBar extends View {
    public float mBackgroundRadius=56f,mProgressRadius=56f,mCurBackgroundRadius=56f,mCurProgressRadius=56f,mThumbOutRadius=56f;
    public final android.graphics.Path mBackgroundPath=new android.graphics.Path(),mClipProgressPath=new android.graphics.Path(),mProgressInnerSmoothPath=new android.graphics.Path(),mThumbSmoothPath=new android.graphics.Path();
    public final android.graphics.Paint mBackgroundPaint=new android.graphics.Paint(1),mThumbPaint=new android.graphics.Paint(1);
    public COUIVerticalSeekBar(){super(new Context());}
}
