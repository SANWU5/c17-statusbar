package com.android.systemui.statusbar.notification.stack;
/** Exact inspected state accessors; callers keep native state instances and animation ownership. */
public class ViewState {
    private float mXTranslation,mYTranslation;
    public float getXTranslation(){return mXTranslation;}
    public float getYTranslation(){return mYTranslation;}
    public void setXTranslation(float value){mXTranslation=value;}
    public void setYTranslation(float value,String source){mYTranslation=value;}
}
