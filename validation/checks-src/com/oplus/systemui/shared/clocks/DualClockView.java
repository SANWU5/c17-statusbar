package com.oplus.systemui.shared.clocks;
/** A resident timezone is independent of the current local timezone. */
public class DualClockView extends android.widget.FrameLayout {
    public static class TimeInfo {public String zone;public TimeInfo(String zone){this.zone=zone;}public String getTimeZone(){return zone;}}
    public android.widget.TextView locatedDate,residentDate;
    public TimeInfo locatedTimeInfo=new TimeInfo("Asia/Shanghai"),residentTimeInfo=new TimeInfo("America/Los_Angeles");
    public TimeInfo residentWeatherInfo=new TimeInfo("-8.0");
    public DualClockView(android.content.Context context){super(context);locatedDate=new android.widget.TextView(context);residentDate=new android.widget.TextView(context);addView(locatedDate);addView(residentDate);}
}
