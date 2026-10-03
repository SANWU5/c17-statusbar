package com.oplus.systemui.shared.clocks;
/** Exact field/class fixture from this device's original keyguard clock DEX. */
public class SingleClockView extends android.widget.FrameLayout {
    public android.widget.TextView mDate;
    public java.util.Calendar mCalendar=java.util.Calendar.getInstance();
    public java.util.TimeZone mTimeZone=java.util.TimeZone.getDefault();
    public SingleClockView(android.content.Context context){super(context);mDate=new android.widget.TextView(context);addView(mDate);}
}
