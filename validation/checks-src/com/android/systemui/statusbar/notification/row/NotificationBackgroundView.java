package com.android.systemui.statusbar.notification.row;
import android.content.Context;
import android.view.View;
/** Real native background host identity; draw branching is exercised by callers. */
public class NotificationBackgroundView extends View {
    public int actualWidth=124,actualHeight=80;
    public NotificationBackgroundView(){super(new Context());}
    public final int getActualWidth(){return actualWidth;}
    public final int getActualHeight(){return actualHeight;}
}
