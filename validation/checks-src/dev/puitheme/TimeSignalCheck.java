package dev.puitheme;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Locale;
public final class TimeSignalCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {checks++;if(!expected.equals(actual))throw new AssertionError("Expected "+expected+", got "+actual);}
    private static long at(String zone,int year,int month,int day,int hour,int minute,int second) {
        Calendar date=Calendar.getInstance(TimeZone.getTimeZone(zone),Locale.ROOT);date.clear();date.set(year,month-1,day,hour,minute,second);return date.getTimeInMillis();
    }
    private static void field(Object target,String name,Object value) throws Exception {
        java.lang.reflect.Field member=target.getClass().getDeclaredField(name);member.setAccessible(true);member.set(target,value);
    }
    private static Object field(Object target,String name) throws Exception {
        java.lang.reflect.Field member=target.getClass().getDeclaredField(name);member.setAccessible(true);return member.get(target);
    }
    /** Calls the compiled display update without constructing Android layout/blur scaffolding. */
    private static void actualClockDisplay() throws Exception {
        Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field singleton=unsafeClass.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        Object unsafe=singleton.get(null);java.lang.reflect.Method allocate=unsafeClass.getMethod("allocateInstance",Class.class);
        NotificationBigClock owner=new NotificationBigClock();
        Class<?> clockClass=Class.forName("dev.puitheme.NotificationBigClock$ClockView");
        Object display=allocate.invoke(unsafe,clockClass);
        Object digits=allocate.invoke(unsafe,Class.forName("dev.puitheme.NotificationBigClock$ClockText"));
        android.widget.TextView date=new android.widget.TextView(new android.content.Context());
        android.widget.TextView footer=new android.widget.TextView(new android.content.Context());
        field(display,"this$0",owner);field(display,"clock",digits);field(display,"dateLabel",date);field(display,"footerLabel",footer);
        field(display,"time","");field(display,"date","");field(display,"footer","");field(display,"textBucket",Long.MIN_VALUE);
        java.lang.reflect.Method update=clockClass.getDeclaredMethod("refreshText",Boolean.TYPE,Long.TYPE);update.setAccessible(true);
        android.os.Bundle values=new android.os.Bundle();
        values.putString("notification_big_clock_pattern","hh:mm");
        values.putBoolean("notification_big_clock_date_enabled",true);
        values.putString("notification_big_clock_date_pattern","yyyy-MM-dd");
        values.putBoolean("notification_big_clock_footer_enabled",true);
        values.putString("notification_big_clock_footer_pattern","HH:mm");
        owner.configure(values);
        TimeZone previous=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            long edge=at("Asia/Shanghai",2026,10,2,13,23,59)+999;
            equal(true,update.invoke(display,false,edge));equal("01:23",((android.widget.TextView)digits).getText());
            equal("13:23",footer.getText());
            equal(true,update.invoke(display,false,edge+1));equal("01:24",((android.widget.TextView)digits).getText());
            equal("13:24",footer.getText());
            for(int sample=0;sample<1000;sample++)equal(false,update.invoke(display,false,edge+1));
            values.putString("notification_big_clock_pattern","HH:mm");owner.configure(values);
            equal(true,update.invoke(display,false,edge+1));equal("13:24",((android.widget.TextView)digits).getText());
            long midnight=at("Asia/Shanghai",2026,12,31,23,59,59)+999;
            update.invoke(display,false,midnight);equal("23:59",((android.widget.TextView)digits).getText());
            equal("2026-12-31",date.getText());equal("23:59",footer.getText());
            update.invoke(display,false,midnight+1);equal("00:00",((android.widget.TextView)digits).getText());
            equal("2027-01-01",date.getText());equal("00:00",footer.getText());
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            // ACTION_TIMEZONE_CHANGED uses force=true even when its UTC minute bucket is unchanged.
            update.invoke(display,true,midnight+1);equal("16:00",((android.widget.TextView)digits).getText());
            equal("2026-12-31",date.getText());equal("16:00",footer.getText());
            values.putString("notification_big_clock_pattern","HH:mm:ss");owner.configure(values);
            update.invoke(display,false,edge);equal("05:23:59",((android.widget.TextView)digits).getText());
            update.invoke(display,false,edge+1);equal("05:24:00",((android.widget.TextView)digits).getText());
            values.putString("notification_big_clock_pattern",null);owner.configure(values);
            update.invoke(display,true,edge+1);equal(TimeFormat.CLOCK_DEFAULT,"HH:mm");
            equal("05:24",((android.widget.TextView)digits).getText());
        } finally { TimeZone.setDefault(previous); }
    }
    public static void main(String[] args) throws Exception {
        TimeZone shanghai=TimeZone.getTimeZone("Asia/Shanghai");long now=at("Asia/Shanghai",2026,9,29,16,7,9);
        equal("2026年09月29日 星期二 周二 下午 16:07:09",TimeFormat.format("{年}年{月}月{日}日 {星期} {周} {时段} {时}:{分}:{秒}",now,shanghai));
        equal("下午 04:07",TimeFormat.format("{上午下午} {12时}:{分}",now,shanghai));
        equal("C17 16:07",TimeFormat.format("'C17' HH:mm",now,shanghai));
        equal("{秒} 16:07",TimeFormat.format("'{秒}' HH:mm",now,shanghai));
        equal("下午周二星期二下午",TimeFormat.format("{时段}{周}{星期}{上午下午}",now,shanghai));
        equal("2026-09-29 08:07",TimeFormat.format("yyyy-MM-dd HH:mm",now,TimeZone.getTimeZone("UTC")));
        String[] periods={"凌晨","凌晨","凌晨","凌晨","凌晨","凌晨","早晨","早晨","早晨","上午","上午","上午","中午","中午","下午","下午","下午","下午","晚上","晚上","晚上","晚上","晚上","晚上"};
        for(int hour=0;hour<24;hour++) {
            long time=at("Asia/Shanghai",2026,9,29,hour,0,0);equal(periods[hour],TimeFormat.period(hour));
            equal(periods[hour],TimeFormat.format("{时段}",time,shanghai));
            equal(hour<12?"上午":"下午",TimeFormat.format("{上午下午}",time,shanghai));
        }
        equal("2028年02月29日 星期二",TimeFormat.format("yyyy年MM月dd日 {星期}",at("Asia/Shanghai",2028,2,29,0,0,0),shanghai));
        long midnight=at("Asia/Shanghai",2026,12,31,23,59,59);
        equal("2026-12-31 周四",TimeFormat.format("yyyy-MM-dd {周}",midnight,shanghai));
        equal("2027-01-01 周五",TimeFormat.format("yyyy-MM-dd {周}",midnight+1000,shanghai));
        TimeZone ny=TimeZone.getTimeZone("America/New_York");long spring=at("America/New_York",2026,3,8,1,59,59);
        equal("03:00:00",TimeFormat.format("HH:mm:ss",spring+1000,ny));
        for(String invalid:new String[]{""," ","HH:mm\nss","{不存在}","{年","'broken","QQ",new String(new char[201]).replace('\0','x')})equal(true,TimeFormat.validationError(invalid)!=null);
        for(String valid:new String[]{"HH:mm","{周}{时段}","yyyy-MM-dd","'C17' HH:mm","'It''s' HH:mm","{12时}:{分}"})equal(null==TimeFormat.validationError(valid),true);
        equal(false,TimeFormat.hasSeconds("HH:mm 'seconds'"));equal(false,TimeFormat.hasSeconds("'{秒}' HH:mm"));
        equal(true,TimeFormat.hasSeconds("HH:mm:{秒}"));equal(true,TimeFormat.hasSeconds("HH:mm:ss"));
        equal(1L,TimeFormat.nextDelay(999L,true));equal(1000L,TimeFormat.nextDelay(1000L,true));
        equal(1L,TimeFormat.nextDelay(59999L,false));equal(60000L,TimeFormat.nextDelay(60000L,false));equal(1L,TimeFormat.nextDelay(-1L,true));
        for(int level=0;level<=4;level++)for(String family:new String[]{"lte","soft"}) {
            String primary="stat_signal_"+family+"_signal_stacked_primary_"+level,secondary="stat_signal_"+family+"_signal_stacked_secondary_"+level;
            equal(primary,SignalResources.moduleName(primary,false));equal(secondary,SignalResources.moduleName(secondary,false));
            equal("c17_signal_single_"+level,SignalResources.moduleName(primary,true));equal("c17_signal_empty",SignalResources.moduleName(secondary,true));
            equal("c17_signal_single_"+level,SignalResources.moduleName("stat_signal_"+family+"_signal_"+level,false));
            equal("c17_signal_single_"+level,SignalResources.moduleName("stat_signal_"+family+"_signal_"+level+"_os17",false));
        }
        for(int level=0;level<=4;level++) {
            equal("c17_signal_single_"+level,SignalResources.moduleName("stat_sys_signal_"+level+"_fully",false));
            equal("stat_signal_wifi_signal_"+level,SignalResources.moduleName("stat_signal_wifi_signal_"+level,true));
        }
        equal("c17_signal_single_noservice",SignalResources.moduleName("stat_signal_soft_signal_noservice_os17",false));
        equal("c17_signal_single_noservice",SignalResources.moduleName("stat_sys_signal_null",true));
        for(String unsafe:new String[]{"stat_sys_wifi_signal_4","stat_signal_lte_signal_5","stat_signal_lte_signal_4_badge","activity_in","stat_signal_wifi_signal_99"})equal(true,SignalResources.moduleName(unsafe,true)==null);
        actualClockDisplay();
        System.out.println(checks+" time and real signal mapping checks passed");
    }
}
