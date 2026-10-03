package com.oplus.systemui.shared.clocks;
public class RedHorizontalSingleClockView extends android.widget.FrameLayout {
    public android.widget.TextView tvDate,tvWeek;
    public RedHorizontalSingleClockView(android.content.Context context){super(context);tvDate=new android.widget.TextView(context);tvWeek=new android.widget.TextView(context);addView(tvDate);addView(tvWeek);}
}
