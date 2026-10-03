package com.oplus.keyguard.clock.base.ui.view;
/** Native base-style owner is a ChildViewProxy, not an Android View. */
public final class DateMessageView {
    public android.widget.TextView dateTextView,weekTextView,dateTextViewExt;
    public ExtraMessageView extraMsgView,extraMsgViewExt;
    public android.view.ViewGroup viewParent;
    public android.view.ViewGroup getViewParent(){return viewParent;}
}
