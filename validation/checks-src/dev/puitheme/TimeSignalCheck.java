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
        java.lang.reflect.Constructor<?> textConstructor=Class.forName("dev.puitheme.NotificationBigClock$ClockText")
                .getDeclaredConstructor(NotificationBigClock.class,android.content.Context.class);
        textConstructor.setAccessible(true);
        Object digits=textConstructor.newInstance(owner,new android.content.Context());
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
    private static void englishAndMixedFormats() {
        TimeZone zone=TimeZone.getTimeZone("Asia/Shanghai");
        String[] weekdays={"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};
        String[] months={"January","February","March","April","May","June","July","August","September","October","November","December"};
        Locale previous=Locale.getDefault();
        try {
            // Turkish default would turn ordinary uppercased i into İ if the
            // English formatter leaked the system locale into case handling.
            Locale.setDefault(new Locale("tr", "TR"));
            for(int day=0;day<7;day++) {
                long time=at("Asia/Shanghai",2026,10,25+day,12,30,9);
                String name=weekdays[day],shortName=name.substring(0,3);
                equal(shortName+" "+name+" "+shortName.toUpperCase(Locale.ROOT)+" "+name.toUpperCase(Locale.ROOT)+" "+shortName.toLowerCase(Locale.ROOT)+" "+name.toLowerCase(Locale.ROOT),
                    TimeFormat.format("{星期英文简} {星期英文} {星期英文简大写} {星期英文大写} {星期英文简小写} {星期英文小写}",time,zone));
            }
            for(int month=1;month<=12;month++) {
                long time=at("Asia/Shanghai",2026,month,1,12,30,9);
                String name=months[month-1],shortName=name.substring(0,3);
                equal(shortName+" "+name+" "+shortName.toUpperCase(Locale.ROOT)+" "+name.toUpperCase(Locale.ROOT)+" "+shortName.toLowerCase(Locale.ROOT)+" "+name.toLowerCase(Locale.ROOT),
                    TimeFormat.format("{月份英文简} {月份英文} {月份英文简大写} {月份英文大写} {月份英文简小写} {月份英文小写}",time,zone));
            }
            String[] chinese={"凌晨","凌晨","凌晨","凌晨","凌晨","凌晨","早晨","早晨","早晨","上午","上午","上午","中午","中午","下午","下午","下午","下午","晚上","晚上","晚上","晚上","晚上","晚上"};
            String[] english={"Late Night","Late Night","Late Night","Late Night","Late Night","Late Night","Early Morning","Early Morning","Early Morning","Morning","Morning","Morning","Noon","Noon","Afternoon","Afternoon","Afternoon","Afternoon","Evening","Evening","Evening","Evening","Evening","Evening"};
            for(int hour=0;hour<24;hour++) {
                long time=at("Asia/Shanghai",2026,10,28,hour,30,9);
                equal(chinese[hour]+" "+english[hour]+" "+english[hour].toUpperCase(Locale.ROOT)+" "+english[hour].toLowerCase(Locale.ROOT)+" "+(hour<12?"AM am":"PM pm"),
                    TimeFormat.format("{时段} {英文时段} {英文时段大写} {英文时段小写} {AMPM} {ampm}",time,zone));
            }
            long midnight=at("Asia/Shanghai",2026,10,28,0,30,0);
            equal("周三 Wed 00:30 凌晨 AM",TimeFormat.format("{周} {星期英文简} HH:mm {时段} {AMPM}",midnight,zone));
            equal("周二 Tue 16:30 下午 PM",TimeFormat.format("{周} {星期英文简} HH:mm {时段} {AMPM}",midnight,TimeZone.getTimeZone("UTC")));
            long noon=at("Asia/Shanghai",2026,10,28,12,30,0);
            equal("中午 12:30 Wed Noon PM",TimeFormat.format("{时段} HH:mm {星期英文简} {英文时段} {AMPM}",noon,zone));
            equal("{星期英文简} Wed 12:30",TimeFormat.format("'{星期英文简}' {星期英文简} HH:mm",noon,zone));
            equal("'C17' Wed",TimeFormat.formatContent("{text} {星期英文简}","'C17'",noon,zone));
            equal(false,TimeFormat.hasSeconds("{星期英文} {月份英文} {英文时段}"));
            equal(false,TimeFormat.contentHasSeconds("{text} {星期英文简}"));
            equal(true,TimeFormat.hasSeconds("{星期英文} HH:mm:{秒}"));
            equal(true,TimeFormat.contentHasSeconds("{text} HH:mm:ss"));
            equal(true,TimeFormat.validationError("{英文时段}")==null);
            equal(true,TimeFormat.contentValidationError("{星期英文} {text}")==null);
            equal(true,TimeFormat.validationError("{英文时段未知}")!=null);
        } finally { Locale.setDefault(previous); }
    }
    public static void main(String[] args) throws Exception {
        englishAndMixedFormats();
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
            // Original C17 MobileIconSets SIGNAL_STRENGTH(_OS17) arrays use
            // these names in independent mode (signal_icon_display_mode=0).
            for(String suffix:new String[]{"","_os17"})for(boolean layout:new boolean[]{false,true}) {
                equal("c17_signal_single_"+level,SignalResources.moduleName("stat_signal_signal_lte_single_"+level+suffix,layout));
                String noVoice=level==0?"stat_signal_single_novoice_0":"stat_signal_signal_novoice_"+level;
                equal("c17_signal_single_"+level,SignalResources.moduleName(noVoice+suffix,layout));
            }
        }
        equal("c17_signal_single_noservice",SignalResources.moduleName("stat_signal_soft_signal_noservice_os17",false));
        equal("c17_signal_single_noservice",SignalResources.moduleName("stat_sys_signal_null",true));
        for(String noService:new String[]{"stat_signal_noservice_lte","stat_signal_noservice_lte_os17","stat_signal_soft_noservice","stat_signal_soft_noservice_os17","stat_signal_signal_null_lte"})
            for(boolean layout:new boolean[]{false,true})equal("c17_signal_single_noservice",SignalResources.moduleName(noService,layout));
        for(String unsafe:new String[]{"stat_sys_wifi_signal_4","stat_signal_lte_signal_5","stat_signal_lte_signal_4_badge","activity_in","stat_signal_wifi_signal_99",
                "stat_signal_signal_lte_single_5_os17","stat_signal_signal_novoice_0_os17","stat_signal_single_novoice_4_os17","stat_signal_signal_lte_single_4_os17_activity",
                "stat_signal_roma_lte","stat_signal_data_fully_inout_lte_os17"})equal(true,SignalResources.moduleName(unsafe,true)==null);
        actualClockDisplay();
        System.out.println(checks+" time and real signal mapping checks passed");
    }
}
