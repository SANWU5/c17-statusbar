package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Spanned;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/** Left/right component tokens: weather language, native icon colours and the
 * extra date/time fields. Executes the real resolver and span, not the phone UI. */
public final class WeatherComponentTokensCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void same(String expected,String actual,String why){checks++;if(!expected.equals(actual))throw new AssertionError(why+": ["+expected+"] != ["+actual+"]");}
    private static final TimeZone ZONE=TimeZone.getTimeZone("Asia/Shanghai");
    private static long instant(){
        Calendar time=new GregorianCalendar(ZONE,Locale.SIMPLIFIED_CHINESE);
        time.clear();time.set(2026,Calendar.OCTOBER,17,15,20,0);return time.getTimeInMillis();
    }
    private static String text(String template,SystemWeatherText.Snapshot weather){
        return BigClockTextContent.resolveStyled(template,instant(),ZONE,weather,BigClockSystemText.snapshot(),null).toString();
    }
    private static WeatherTextIcon.Span[] spans(CharSequence value){
        return value instanceof Spanned?((Spanned)value).getSpans(0,value.length(),WeatherTextIcon.Span.class):new WeatherTextIcon.Span[0];
    }

    private static void languages(){
        SystemWeatherText.Snapshot sunny=SystemWeatherText.sample("晴","23°",WeatherTextIcon.Kind.SUNNY);
        same("晴 ·23°",text("（天气）",sunny),"system language keeps the delivered text");
        same("Sunny ·23°",text("（天气英文）",sunny),"English weather");
        same("晴 ·23°",text("（天气中文）",sunny),"Chinese weather");
        same("Sunny",text("（天气描述英文）",sunny),"English description only");
        same("晴",text("(天气描述)",sunny),"system description only");
        same("Sunny 23°",BigClockTextContent.resolve("（天气文字英文）",instant(),ZONE,sunny),"plain English text form");
        same("晴 23°",BigClockTextContent.resolve("(天气文字)",instant(),ZONE,sunny),"plain system text stays compatible");
        SystemWeatherText.Snapshot night=SystemWeatherText.sample("晴","18°",WeatherTextIcon.Kind.CLEAR_NIGHT);
        same("Clear ·18°",text("（天气英文）",night),"native night category wins over the day word");
        SystemWeatherText.Snapshot english=SystemWeatherText.sample("Light rain","9°",WeatherTextIcon.Kind.LIGHT_RAIN);
        same("小雨 ·9°",text("（天气中文）",english),"English system text can be shown in Chinese");
        same("Light rain ·9°",text("（天气英文）",english),"same language is unchanged");
        SystemWeatherText.Snapshot drizzle=SystemWeatherText.sample("毛毛雨","6°",WeatherTextIcon.Kind.LIGHT_RAIN);
        same("Drizzle ·6°",text("（天气英文）",drizzle),"closed text table refines the category");
        SystemWeatherText.Snapshot unknown=SystemWeatherText.sample("Mist with ash","5°",WeatherTextIcon.Kind.UNKNOWN);
        same("Mist with ash ·5°",text("（天气中文）",unknown),"unrecognised text is never guessed");
        SystemWeatherText.Snapshot none=SystemWeatherText.sample("","",WeatherTextIcon.Kind.UNKNOWN);
        same("Weather unavailable",text("（天气英文）",none),"missing weather is readable in English");
        same("天气暂无数据",text("（天气中文）",none),"missing weather is readable in Chinese");
        for(WeatherTextIcon.Kind kind:WeatherTextIcon.Kind.values())if(kind!=WeatherTextIcon.Kind.UNKNOWN){
            SystemWeatherText.Snapshot value=SystemWeatherText.sample("x","1°",kind);
            check(!text("（天气描述英文）",value).equals("x")&&!text("（天气描述中文）",value).equals("x"),"every native category has both names: "+kind);
        }
    }

    private static void nativeColours(){
        SystemWeatherText.clear();WeatherNativeFixture fixture=new WeatherNativeFixture();fixture.update("雨","16°","light_rain.svg",true);
        SystemWeatherText.Snapshot snapshot=SystemWeatherText.snapshot();
        check(snapshot.weatherIcon!=null,"fixture supplies a native image");
        CharSequence normal=BigClockTextContent.resolveStyled("（天气）",instant(),ZONE,snapshot,BigClockSystemText.snapshot(),null);
        CharSequence original=BigClockTextContent.resolveStyled("（天气英文原色）",instant(),ZONE,snapshot,BigClockSystemText.snapshot(),null);
        check(spans(normal).length==1&&!spans(normal)[0].nativeColours,"ordinary weather keeps following the text colour");
        check(spans(original).length==1&&spans(original)[0].nativeColours,"the original-colour token keeps the widget colours");
        check(original.toString().startsWith("Rain ·16° "),"language and original colours combine");
        check(spans(BigClockTextContent.resolveStyled("（天气图标原色）",instant(),ZONE,snapshot,BigClockSystemText.snapshot(),null))[0].nativeColours,"icon-only original-colour token");
        CharSequence retained=BigClockTextContent.resolveStyled("（天气）",instant(),ZONE,snapshot,BigClockSystemText.snapshot(),original);
        check(spans(retained)[0]!=spans(original)[0],"a colour-mode change never reuses the old span");
        Paint tinted=new Paint();tinted.setTextSize(20f);tinted.setColor(0xff112233);
        Canvas ordinary=new Canvas();spans(normal)[0].draw(ordinary,"x",0,1,0f,0,20,30,tinted);
        check(ordinary.layers==1&&ordinary.rectFills==1,"text colour silhouette is still the default");
        Canvas native1=new Canvas();spans(original)[0].draw(native1,"x",0,1,0f,0,20,30,tinted);
        check(native1.layers==0&&native1.rectFills==0&&native1.pictureDraws==1,"native colours draw the widget picture without a tint layer");
        Paint faded=new Paint();faded.setTextSize(20f);faded.setColor(0x80112233);
        Canvas fade=new Canvas();spans(original)[0].draw(fade,"x",0,1,0f,0,20,30,faded);
        check(fade.layers==1&&fade.rectFills==0&&fade.pictureDraws==1,"text fades still apply to native colours without recolouring");
        SystemWeatherText.clear();
    }

    /** The widget renderer's own SVG class changes between versions: not a PictureDrawable, not clonable. */
    private static final class LiveDrawable extends android.graphics.drawable.Drawable {
        int draws;
        @Override public void draw(Canvas canvas){draws++;}
        @Override public void setAlpha(int alpha){ }
        @Override public void setColorFilter(android.graphics.ColorFilter filter){ }
        @Override public int getOpacity(){return -3;}
    }

    private static void nativeDrawableFallback(){
        LiveDrawable live=new LiveDrawable();
        check(WeatherTextIcon.NativeImage.capture(live,null)==null&&live.draws==0,"without bounds nothing is drawn or guessed");
        live.setBounds(0,0,36,36);
        WeatherTextIcon.NativeImage first=WeatherTextIcon.NativeImage.capture(live,null);
        check(first!=null&&first.recorded&&first.width==36&&first.height==36&&live.draws==1,"a live drawable is recorded once at its own bounds");
        check(live.getBounds().width()==36&&live.getBounds().height()==36,"the host drawable's bounds are never changed");
        check(WeatherTextIcon.NativeImage.capture(live,first)==first&&live.draws==1,"unchanged bounds reuse the recording");
        WeatherTextIcon.NativeImage fresh=WeatherTextIcon.NativeImage.capture(live,first,true);
        check(fresh!=null&&fresh!=first&&live.draws==2,"a new native delivery records again");
        live.setBounds(0,0,48,48);
        WeatherTextIcon.NativeImage resized=WeatherTextIcon.NativeImage.capture(live,fresh);
        check(resized!=null&&resized!=fresh&&resized.width==48,"a size change records again");
        WeatherTextIcon.Span span=new WeatherTextIcon.Span(WeatherTextIcon.Kind.CLOUDY_NIGHT,resized,true);
        Canvas canvas=new Canvas();Paint paint=new Paint();paint.setTextSize(20f);
        span.draw(canvas,"x",0,1,0f,0,20,30,paint);
        check(canvas.pictureDraws==1,"the recorded icon draws as a vector picture");
        Paint.FontMetricsInt line=new Paint.FontMetricsInt();
        int width=span.getSize(paint,"x",0,1,line);
        float iconHeight=20f;
        check(width==Math.ceil(iconHeight),"the icon adds no trailing pad: the single space is the whole gap");
        check(line.descent-line.ascent>=Math.floor(iconHeight)&&line.bottom-line.top>=line.descent-line.ascent,
                "a taller icon grows the line around its centre instead of being clipped");
    }

    private static void timeTokens(){
        SystemWeatherText.Snapshot none=SystemWeatherText.sample("","",WeatherTextIcon.Kind.UNKNOWN);
        same("2026年10月17日",text("（年月日）",none),"full date");
        same("2026",text("(年)",none),"year");
        same("周六",text("（周）",none),"short weekday");
        same("03:20",text("（12小时）",none),"12-hour time");
        same("下午",text("（上午下午）",none),"meridiem");
        same("下午",text("（时段）",none),"day period");
        same("Oct 17",text("（日期英文）",none),"English date");
        same("Saturday",text("（星期英文）",none),"English weekday");
        same("Sat",text("（星期英文简）",none),"short English weekday");
        same("October",text("（月份英文）",none),"English month");
        same("10月17日 周六",text("（格式:M月d日 {周}）",none),"custom format");
        same("15:20",text("(格式：HH:mm)",none),"wide colon prefix");
        same("（格式:HH:mm:ss）",text("（格式:HH:mm:ss）",none),"seconds are never replaced");
        same("（格式:{未知}）",text("（格式:{未知}）",none),"an invalid format stays visible text");
        check(text("（农历）",none).startsWith("农历")||text("（农历）",none).startsWith("公历"),"lunar date falls back to the solar date without ICU");
        check(!text("（干支）",none).isEmpty(),"ganzhi year");
        same("下午 03:20 · Sat",text("（上午下午） （12小时） · （星期英文简）",none),"tokens mix with ordinary text");
    }

    private static void dependencies(){
        check(BigClockTextContent.dependencies("（格式:HH:mm）")==BigClockTextContent.TIME,"custom format refreshes each minute");
        check(BigClockTextContent.dependencies("（12小时）")==BigClockTextContent.TIME&&BigClockTextContent.dependencies("(星期英文)")==BigClockTextContent.TIME,"extra time tokens refresh each minute");
        for(String token:new String[]{"天气中文","天气英文","天气原色","天气英文原色","天气描述","天气描述中文","天气描述英文","天气文字英文","天气图标原色"})
            check(BigClockTextContent.dependencies("（"+token+"）")==BigClockTextContent.WEATHER,"weather event dependency: "+token);
        check(BigClockTextContent.dependencies("信号（蜂窝信号） （电池）")==(BigClockTextContent.CELLULAR|BigClockTextContent.BATTERY),"system tokens keep their own dependency");
        check(BigClockTextContent.dependencies("（天气英文） （日期英文）")==(BigClockTextContent.WEATHER|BigClockTextContent.TIME),"mixed template depends on both");
        check(BigClockTextContent.dependencies("普通文字 （未知）")==0&&BigClockTextContent.dependencies("（天气")==0,"literals and unclosed brackets need no event");
    }

    public static void main(String[] args){
        languages();nativeColours();nativeDrawableFallback();timeTokens();dependencies();
        System.out.println("WeatherComponentTokensCheck: "+checks+" checks passed");
    }
}
