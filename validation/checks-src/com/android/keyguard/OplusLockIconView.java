package com.android.keyguard;
/** The real owner is FrameLayout, not ImageView; fingerprint is a separate child. */
public class OplusLockIconView extends android.widget.FrameLayout {
    public final android.widget.ImageView mLockIcon,mBgView;
    public final android.view.View fingerprint;
    public OplusLockIconView(android.content.Context context){super(context);mLockIcon=new android.widget.ImageView(context);mBgView=new android.widget.ImageView(context);fingerprint=new android.view.View(context);addView(mBgView);addView(mLockIcon);addView(fingerprint);}
}
