package com.android.systemui.statusbar;
public interface StatusIconDisplayable {
    String getSlot();
    boolean isIconVisible();
    boolean isIconBlocked();
}
