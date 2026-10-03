package com.android.systemui.statusbar.notification.collection;
import android.service.notification.StatusBarNotification;
public final class NotificationEntry {
    public StatusBarNotification mSbn;
    public NotificationEntry(long time){mSbn=new StatusBarNotification(time);}
    public StatusBarNotification getSbn(){return mSbn;}
    public void setSbn(StatusBarNotification sbn){mSbn=sbn;}
}
