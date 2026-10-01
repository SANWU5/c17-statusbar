package com.android.systemui.statusbar;

import android.content.Context;
import android.service.notification.StatusBarNotification;

/** Matches the inspected SystemUI visibility/signatures; height is deliberately private. */
public class StatusBarIconView extends android.widget.ImageView {
    public Object mBundleEntry;
    public float intrinsicHeight = 96f, nativeScale = .5f, iconAppearAmount = 1f;
    public int visibleState, staticDrawableColor = 0xff000000, mDecorColor=0xff000000;
    public boolean blocked;
    public int getterCalls;
    private StatusBarNotification notification;

    public StatusBarIconView(Context context) { super(context); }
    private float getIconHeight() { getterCalls++; return intrinsicHeight; }
    public float getIconScale() { getterCalls++; return nativeScale; }
    public float getIconAppearAmount() { return iconAppearAmount; }
    public StatusBarNotification getNotification() { getterCalls++; return notification; }
    public void setNotification(StatusBarNotification value) { notification = value; }
    public int getVisibleState() { getterCalls++; return visibleState; }
    public boolean isIconBlocked() { getterCalls++; return blocked; }
    public int getStaticDrawableColor() { getterCalls++; return staticDrawableColor; }
}
