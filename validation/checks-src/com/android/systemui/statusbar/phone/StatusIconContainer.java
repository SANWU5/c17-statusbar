package com.android.systemui.statusbar.phone;
import android.view.View;
import android.view.ViewGroup;
import dev.puitheme.NetworkIconOrder;
/** getChildAt is intercepted only during the production helper's exact layout scope. */
public class StatusIconContainer extends ViewGroup {
    public final java.util.Set<String> mIgnoredSlots = new java.util.HashSet<>();
    public final java.util.List<View> mMeasureViews = new java.util.ArrayList<>();
    public NetworkIconOrder ordering;
    public dev.puitheme.NativeStatusIcons hints;
    public int childReads;
    public void updateStates() { }
    public StatusIconContainer() { super(null); }
    @Override public View getChildAt(int index) { childReads++;int mapped=ordering == null ? index : ordering.childIndex(this,index);return super.getChildAt(hints==null?mapped:hints.childIndex(this,mapped)); }
    public static final class StatusIconState extends com.android.systemui.statusbar.notification.stack.ViewState {
        public int visibleState;
    }
}
