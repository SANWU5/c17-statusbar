package com.oplus.systemui.plugins.qs.seamless;
import android.view.ViewGroup;
/** The two precise native fields used by the bridge. */
public final class SeparateQSFakeStatusController {
    public ViewGroup statusIconsView;
    public ViewGroup fakeStatusIconContainer;
    public ViewGroup fakeClockContainer, fakeNotificationIconContainer;
    public com.android.systemui.plugins.qs.QSFakeStatusElement qsFakeClock, qsFakeNotificationIcon;
}
