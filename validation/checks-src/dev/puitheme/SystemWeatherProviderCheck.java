package dev.puitheme;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spanned;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import com.oplus.keyguard.clock.common.R;
import com.oplus.keyguard.clock.common.entry.WeatherData;
import com.oplus.keyguard.clock.common.util.WeatherEffectController;

/** Exercises actual cache queries, subscriptions, delivery and module span drawing. */
public final class SystemWeatherProviderCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final long DAY=86400000L, NOON=DAY*20000+DAY/2;
    private static final class Rows implements Cursor {
        final String[] columns;final Object[] values;boolean closed;
        Rows(String[] columns,Object...values){this.columns=columns;this.values=values;}
        public boolean moveToFirst(){return values.length>0;}
        public int getColumnIndex(String name){return Arrays.asList(columns).indexOf(name);}
        public boolean isNull(int index){return values[index]==null;}
        public String getString(int index){return values[index]==null?null:values[index].toString();}
        public int getInt(int index){return ((Number)values[index]).intValue();}
        public long getLong(int index){return ((Number)values[index]).longValue();}
        public void close(){closed=true;}
    }
    private static final class Resolver extends ContentResolver {
        long city=88,weatherCity=88;String description="晴",temperature="26";int code=54;
        long sunrise=DAY*20000+DAY/4,sunset=DAY*20000+DAY*3/4;
        int queries;boolean noCity,noWeather,denied,nullWeather;final List<Rows> cursors=new ArrayList<>();
        public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
            queries++;if(denied)throw new SecurityException("provider denied");
            Rows result;
            if(uri.toString().equals(SystemWeatherProvider.CITY.toString())){
                check(Arrays.equals(projection,new String[]{"_id"})&&"location=?".equals(selection)
                        &&Arrays.equals(args,new String[]{"1"})&&sort==null,"only audited located-city selection is queried");
                result=noCity?new Rows(new String[]{"_id"}):new Rows(new String[]{"_id"},city);
            }else{
                check(uri.toString().equals(SystemWeatherProvider.WEATHER.toString())
                        &&Arrays.equals(projection,new String[]{"weather_id","current_weather","current_temp","city_id","sunrise_time","sunset_time"})
                        &&"city_id=? and weather_index=?".equals(selection)&&Arrays.equals(args,new String[]{String.valueOf(city),"0"})
                        &&"date DESC".equals(sort),"actual city current weather uses audited fields and newest first");
                if(nullWeather)return null;
                String[] columns={"weather_id","current_weather","current_temp","city_id","sunrise_time","sunset_time"};
                result=noWeather?new Rows(columns):new Rows(columns,code,description,temperature,weatherCity,sunrise,sunset);
            }
            cursors.add(result);return result;
        }
        void closed(){for(Rows cursor:cursors)check(cursor.closed,"all provider cursors close on success and missing rows");cursors.clear();}
    }
    private static final class TestContext extends Context {
        final Resolver resolver;TestContext(Resolver resolver){this.resolver=resolver;}
        public ContentResolver getContentResolver(){return resolver;}
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> jobs=new ArrayDeque<>();
        public void execute(Runnable job){jobs.add(job);}
        void one(){check(!jobs.isEmpty(),"expected scheduled production task");jobs.remove().run();}
    }
    private static WeatherTextIcon.Span span(CharSequence text){
        WeatherTextIcon.Span[] spans=((Spanned)text).getSpans(0,text.length(),WeatherTextIcon.Span.class);
        check(spans.length==1,"weather template creates exactly one module glyph from semantics");return spans[0];
    }
    private static void needed(){
        Bundle settings=new Bundle();check(!SystemWeatherProvider.needed(settings),"disabled clock never observes weather");
        settings.putBoolean(NotificationBigClockSettings.MASTER,true);settings.putBoolean(BigClockWidgetsSettings.LEFT_ENABLED,true);
        check(SystemWeatherProvider.needed(settings),"enabled left default weather template requires independent provider");
        settings.putString(BigClockWidgetsSettings.LEFT_TEXT,"（日期）");check(!SystemWeatherProvider.needed(settings),"date component does not read weather");
        settings.putBoolean(BigClockWidgetsSettings.RIGHT_ENABLED,true);settings.putString(BigClockWidgetsSettings.RIGHT_TEXT,"（天气:{温度}）");
        check(SystemWeatherProvider.needed(settings),"right weather category template requires provider");
        settings.putBoolean(NotificationBigClockSettings.MASTER,false);settings.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,true);
        check(SystemWeatherProvider.needed(settings),"landscape-only clock also receives weather");
        settings.putBoolean(StatusBarSettings.SAFE_MODE,true);check(!SystemWeatherProvider.needed(settings),"safe mode suppresses provider ownership");
    }
    private static void actualQuery(){
        Resolver resolver=new Resolver();SystemWeatherProvider.Result day=SystemWeatherProvider.read(resolver,NOON);
        check(day.cityId==88&&day.description.equals("晴")&&day.temperature.equals("26°")&&day.code==54
                &&day.daylight==1&&day.kind==WeatherTextIcon.Kind.SUNNY,"provider result works before any widget or controller exists");
        SystemWeatherProvider.Result night=SystemWeatherProvider.read(resolver,NOON+DAY/2);
        check(night.daylight==0&&night.kind==WeatherTextIcon.Kind.CLEAR_NIGHT,"same real code uses provider sunrise/sunset for night");
        resolver.sunrise=0;check(SystemWeatherProvider.read(resolver,NOON).kind==WeatherTextIcon.Kind.UNKNOWN,"missing sun times never invent a sunny or moon icon");
        resolver.sunrise=DAY*20000+DAY*3/4;resolver.sunset=DAY*20000+DAY/4;
        check(SystemWeatherProvider.read(resolver,NOON).daylight==0&&SystemWeatherProvider.read(resolver,NOON+DAY/2).daylight==1,"provider day interval supports UTC midnight wrapping");
        resolver.temperature="−7";resolver.code=35;
        SystemWeatherProvider.Result snow=SystemWeatherProvider.read(resolver,NOON);
        check(snow.temperature.equals("-7°")&&snow.kind==WeatherTextIcon.Kind.HEAVY_SNOW,"negative actual temperature and heavy snow remain distinct");
        resolver.temperature="--";check(SystemWeatherProvider.read(resolver,NOON).temperature.isEmpty(),"missing temperature never becomes zero degrees");
        resolver.code=999;check(SystemWeatherProvider.read(resolver,NOON).kind==WeatherTextIcon.Kind.UNKNOWN,"unknown future weather code has no false artwork");
        int[] codes={2,1,5,17,18,22,23,24,27,21,33,37,38,45,46,50,53,59,60,63,66,68,70,71};
        WeatherTextIcon.Kind[] kinds={WeatherTextIcon.Kind.LIGHT_RAIN,WeatherTextIcon.Kind.MODERATE_RAIN,WeatherTextIcon.Kind.HEAVY_RAIN,
                WeatherTextIcon.Kind.ICE_RAIN,WeatherTextIcon.Kind.HAILSTONE,WeatherTextIcon.Kind.THUNDERSHOWERS,
                WeatherTextIcon.Kind.THUNDERSHOWERS_HAILSTONE,WeatherTextIcon.Kind.THUNDERSTORM,WeatherTextIcon.Kind.LIGHT_SNOW,
                WeatherTextIcon.Kind.MODERATE_SNOW,WeatherTextIcon.Kind.HEAVY_SNOW,WeatherTextIcon.Kind.SLEET,WeatherTextIcon.Kind.FOG,
                WeatherTextIcon.Kind.SINK_FLOAT,WeatherTextIcon.Kind.DUST_DEVIL,WeatherTextIcon.Kind.HAZE,WeatherTextIcon.Kind.PARTLY_CLOUDY,
                WeatherTextIcon.Kind.CLOUDY,WeatherTextIcon.Kind.WIND,WeatherTextIcon.Kind.STORM,WeatherTextIcon.Kind.CLOUDY_NIGHT,
                WeatherTextIcon.Kind.CLEAR_NIGHT,WeatherTextIcon.Kind.SUNRISE,WeatherTextIcon.Kind.SUNSET};
        for(int i=0;i<codes.length;i++){resolver.code=codes[i];check(SystemWeatherProvider.read(resolver,NOON).kind==kinds[i],"audited weather code reaches actual provider result: "+codes[i]);}
        resolver.sunrise=DAY*20000+DAY/4;resolver.sunset=DAY*20000+DAY*3/4;
        for(int code=54;code<=58;code++){
            resolver.code=code;check(SystemWeatherProvider.read(resolver,NOON).kind==(code<=55?WeatherTextIcon.Kind.SUNNY:WeatherTextIcon.Kind.PARTLY_CLOUDY)
                    &&SystemWeatherProvider.read(resolver,NOON+DAY/2).kind==(code<=55?WeatherTextIcon.Kind.CLEAR_NIGHT:WeatherTextIcon.Kind.CLOUDY_NIGHT),"all five special codes use real sunrise/sunset: "+code);
        }
        resolver.weatherCity=123;check(SystemWeatherProvider.read(resolver,NOON)==null,"mismatched weather city cannot replace current user city");
        resolver.weatherCity=88;resolver.noWeather=true;
        check(SystemWeatherProvider.read(resolver,NOON).description.isEmpty(),"confirmed missing current-city row clears stale weather");
        resolver.noCity=true;check(SystemWeatherProvider.read(resolver,NOON).cityId==Long.MIN_VALUE,"no located city has explicit unavailable state");
        resolver.closed();
    }
    private static void lifecycle(){
        SystemWeatherText.clear();Resolver first=new Resolver();Queue worker=new Queue(),delivery=new Queue();
        SystemWeatherProvider provider=new SystemWeatherProvider(worker,delivery);int[] changes={0};
        provider.configure(new TestContext(first),false,()->changes[0]++);
        check(first.observers.isEmpty()&&worker.jobs.isEmpty(),"unneeded provider has no observers or work");
        provider.configure(new TestContext(first),true,()->changes[0]++);
        check(first.observers.size()==2&&worker.jobs.size()==1,"cold configure observes both cache URIs and schedules exactly one read");
        for(int i=0;i<100;i++)first.dispatchSettingsChange();
        check(worker.jobs.size()==1,"event burst coalesces while one read waits");
        worker.one();delivery.one();check(SystemWeatherText.snapshot().cityId==88&&changes[0]==1,"delivery publishes independent provider cache");
        check(worker.jobs.size()==1,"burst creates at most one coalesced follow-up");worker.one();delivery.one();
        check(changes[0]==1&&worker.jobs.isEmpty(),"unchanged cache does not request redraw or another query");
        first.denied=true;provider.refresh();worker.one();delivery.one();check(SystemWeatherText.snapshot().description.equals("晴"),"transient provider denial retains last confirmed value");first.denied=false;
        provider.refresh();worker.one();SystemWeatherText.clear();delivery.one();
        check(SystemWeatherText.snapshot().description.isEmpty(),"generation clear rejects a query already awaiting delivery");
        provider.refresh();worker.one();Resolver second=new Resolver();second.description="小雨";second.code=3;
        provider.close();SystemWeatherText.clear();provider.configure(new TestContext(second),true,()->changes[0]++);
        delivery.one();check(SystemWeatherText.snapshot().description.isEmpty()&&worker.jobs.size()==1,"closed user query cannot leak into new user or clear its pending job");
        worker.one();delivery.one();check(SystemWeatherText.snapshot().description.equals("小雨")&&SystemWeatherText.snapshot().weatherIcon==null,"new user loads directly with no widget image dependency");
        second.noWeather=true;provider.refresh();worker.one();delivery.one();check(SystemWeatherText.snapshot().description.isEmpty(),"confirmed absence clears previously published city weather");
        provider.close();check(first.observers.isEmpty()&&second.observers.isEmpty(),"close removes both URI registrations for both users");
        provider.refresh();check(worker.jobs.isEmpty(),"closed provider schedules nothing");first.closed();second.closed();
    }
    private static final class Colours extends Canvas {
        final List<Integer> colours=new ArrayList<>();final List<Integer> alpha=new ArrayList<>();
        final List<android.graphics.LinearGradient> gradients=new ArrayList<>();
        void record(Paint p){colours.add(p.getColor());alpha.add(p.getAlpha());if(p.getShader() instanceof android.graphics.LinearGradient)gradients.add((android.graphics.LinearGradient)p.getShader());}
        public void drawPath(Path path,Paint paint){record(paint);super.drawPath(path,paint);}
        public void drawCircle(float x,float y,float r,Paint paint){record(paint);super.drawCircle(x,y,r,paint);}
        public void drawRoundRect(RectF rect,float x,float y,Paint paint){record(paint);super.drawRoundRect(rect,x,y,paint);}
    }
    private static void artwork(){
        Resolver resolver=new Resolver();resolver.code=3;resolver.description="小雨";
        SystemWeatherText.clear();check(SystemWeatherText.captureProvider(SystemWeatherProvider.read(resolver,NOON),SystemWeatherText.generation()),"real read advances provider snapshot");
        CharSequence text=BigClockTextContent.resolveStyled("（天气）",NOON,TimeZone.getTimeZone("UTC"));WeatherTextIcon.Span glyph=span(text);
        check(glyph.kind==WeatherTextIcon.Kind.LIGHT_RAIN&&text.toString().equals("小雨 ·26° \ufffc"),"actual code selects rainfall glyph independently of widgets");
        Paint paint=new Paint();paint.setTextSize(20f);paint.setColor(0x80445566);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(7f);
        Colours mono=new Colours();glyph.draw(mono,"\ufffc",0,1,0f,0,20,30,paint);
        check(!mono.colours.isEmpty()&&mono.colours.stream().allMatch(value->value==0x80445566),"component colour covers all module glyph shapes");
        WeatherTextIcon.Span original=span(BigClockTextContent.resolveStyled("（天气:{图标:原色}）",NOON,TimeZone.getTimeZone("UTC")));Colours colours=new Colours();
        original.draw(colours,"\ufffc",0,1,0f,0,20,30,paint);
        check(colours.gradients.stream().anyMatch(value->java.util.Arrays.equals(value.colors,new int[]{0xff86d6ff,0xff3b9bff}))
                &&colours.gradients.stream().anyMatch(value->java.util.Arrays.equals(value.colors,new int[]{0xffffffff,0xffdde5f0}))
                &&colours.alpha.stream().allMatch(value->value==128),"v3 original colours keep the selected cloud/rain gradients and parent alpha");
        check(paint.getColor()==0x80445566&&paint.getStyle()==Paint.Style.STROKE&&paint.getStrokeWidth()==7f&&colours.getSaveCount()==1,"module draw restores caller paint and canvas");
        for(WeatherTextIcon.Kind kind:WeatherTextIcon.Kind.values())if(kind!=WeatherTextIcon.Kind.UNKNOWN){
            Colours canvas=new Colours();new WeatherTextIcon.Span(kind,null,true).draw(canvas,"\ufffc",0,1,0f,0,20,30,paint);
            check(!canvas.colours.isEmpty()&&canvas.bitmapDraws==0&&canvas.pictureDraws==0,"each supported weather category draws stable module vector paths");
        }
        resolver.closed();
    }
    private static void userIsolation() throws ReflectiveOperationException {
        // Fixture-only restoration; production deliberately exposes no way to
        // reopen user-unverified delivery during the lifetime of SystemUI.
        java.lang.reflect.Field allowed=SystemWeatherText.class.getDeclaredField("unverifiedFallbackAllowed");allowed.setAccessible(true);
        boolean previous=allowed.getBoolean(null);
        try {
            SystemWeatherText.clear();WeatherData oldData=new WeatherData(54,"原用户天气",26,20,R.drawable.ic_weather_sunny,123L);
            WeatherEffectController oldController=new WeatherEffectController(true,oldData);
            check(SystemWeatherText.captureController(oldController),"native controller fallback is enabled at process startup");
            oldController.sendForCheck();SystemWeatherText.disableUnverifiedFallback();SystemWeatherText.clear();
            long revision=SystemWeatherText.revision();int[] delayed={0};
            oldController.drainForCheck(()->{delayed[0]++;check(!SystemWeatherText.captureController(oldController),"queued old controller delivery is rejected after user change");});
            check(delayed[0]==1&&!SystemWeatherText.captureRead(oldController,oldData)
                    &&SystemWeatherText.snapshot().description.isEmpty()&&SystemWeatherText.revision()==revision,"old cache read cannot republish or revise cleared user state");
            WeatherNativeFixture oldWidget=new WeatherNativeFixture();
            check(!oldWidget.update("旧小部件天气","21°","sunny.svg",true)&&SystemWeatherText.snapshot().description.isEmpty(),"central publish also rejects historical widget source after user change");
            Resolver current=new Resolver();current.description="当前用户小雨";current.code=3;
            check(SystemWeatherText.captureProvider(SystemWeatherProvider.read(current,NOON),SystemWeatherText.generation())
                    &&SystemWeatherText.snapshot().description.equals("当前用户小雨")&&SystemWeatherText.snapshot().cityId==88,"explicitly user-bound provider remains able to publish");
            SystemWeatherText.clear();check(!SystemWeatherText.captureController(oldController)&&!allowed.getBoolean(null),"clear never re-enables user-unverified fallback");current.closed();
        } finally {allowed.setBoolean(null,previous);SystemWeatherText.clear();}
    }
    public static void main(String[] args) throws Exception {try{needed();actualQuery();lifecycle();artwork();userIsolation();}finally{SystemWeatherText.clear();}
        System.out.println("SystemWeatherProviderCheck: "+checks+" checks passed (read-only cache, lifecycle and semantic artwork)");}
}
