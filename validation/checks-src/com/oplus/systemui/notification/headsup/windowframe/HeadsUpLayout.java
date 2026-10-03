package com.oplus.systemui.notification.headsup.windowframe;

import android.content.Context;
import android.widget.FrameLayout;

/** Exact C17 heads-up window owner. Its native isHeadsUpView is an ancestor test. */
public class HeadsUpLayout extends FrameLayout {
    public HeadsUpLayout(){super(new Context());}
}
