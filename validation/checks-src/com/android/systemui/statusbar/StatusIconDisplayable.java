package com.android.systemui.statusbar;
public interface StatusIconDisplayable {
    String getSlot();
    boolean isIconVisible();
    boolean isIconBlocked();
    default int getVisibleState() { return 0; }
    default void setVisibleState(int state, boolean animate) { }
}
