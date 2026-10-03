package com.android.systemui.statusbar.notification.collection;
import android.service.notification.StatusBarNotification;
public final class NotificationEntry extends ListEntry {
    public StatusBarNotification mSbn;
    public NotificationEntry(long time){mSbn=new StatusBarNotification(time);}
    public StatusBarNotification getSbn(){return mSbn;}
    public void setSbn(StatusBarNotification sbn){mSbn=sbn;}
    @Override public NotificationEntry getRepresentativeEntry(){return this;}
}
