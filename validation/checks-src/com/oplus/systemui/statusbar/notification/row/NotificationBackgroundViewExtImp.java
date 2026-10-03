package com.oplus.systemui.statusbar.notification.row;
import android.graphics.drawable.Drawable;
import com.android.systemui.statusbar.notification.row.NotificationBackgroundView;
import com.android.systemui.statusbar.notification.row.NotificationBackgroundViewExt;
/** Actual OEM superclass/field names from SystemUI, not a heuristic view-tree walk. */
public class NotificationBackgroundViewExtImp extends NotificationBackgroundViewExt {
    private Drawable zoomAppIcon;
    public NotificationBackgroundViewExtImp(NotificationBackgroundView host){super(host);}
    public void icon(Drawable icon){zoomAppIcon=icon;}
}
