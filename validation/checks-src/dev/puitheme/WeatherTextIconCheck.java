package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.text.Spanned;
import com.oplus.keyguard.clock.common.R;
import com.oplus.keyguard.clock.common.entry.WeatherData;
import com.oplus.keyguard.clock.common.util.WeatherEffectController;
import java.util.TimeZone;

/** Tests real resource-name resolution, snapshots and ReplacementSpan drawing.
 * Desktop path commands do not prove Android raster appearance or frame rate. */
public final class WeatherTextIconCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static WeatherData data(int id,int resource){return new WeatherData(id,"相同天气文字",26,20,resource,123L);}
    private static WeatherTextIcon.Span[] spans(CharSequence text){return text instanceof Spanned?((Spanned)text).getSpans(0,text.length(),WeatherTextIcon.Span.class):new WeatherTextIcon.Span[0];}

    private static void nativeResources() throws Exception {
        ClassLoader loader=WeatherData.class.getClassLoader();
        for(WeatherTextIcon.Kind kind:WeatherTextIcon.Kind.values())if(kind!=WeatherTextIcon.Kind.UNKNOWN){
            int resource=R.drawable.class.getField(kind.resource).getInt(null);
            check(WeatherTextIcon.nativeKind(loader,resource)==kind,"all 26 native resource semantics resolve in the owning loader");
        }
        check(WeatherTextIcon.nativeKind(loader,0)==WeatherTextIcon.Kind.UNKNOWN,"native zero icon is unknown");
        check(WeatherTextIcon.nativeKind(loader,777777)==WeatherTextIcon.Kind.UNKNOWN,"an unrecognized future resource is never guessed from a weather ID");
        check(WeatherTextIcon.nativeKind(null,R.drawable.ic_weather_sunny)==WeatherTextIcon.Kind.UNKNOWN,"an unavailable loader has no trusted resource map");
        SystemWeatherText.clear();WeatherEffectController source=new WeatherEffectController(true,data(54,R.drawable.ic_weather_sunny));
        check(SystemWeatherText.captureController(source)&&SystemWeatherText.snapshot().weatherId==54
                &&SystemWeatherText.snapshot().weatherIconKind==WeatherTextIcon.Kind.SUNNY,"snapshot stores actual native weather ID and resolved icon kind");
        long first=SystemWeatherText.revision();source.setData(data(54,R.drawable.ic_weather_clear_night));
        check(SystemWeatherText.captureController(source)&&SystemWeatherText.revision()==first+1
                &&SystemWeatherText.snapshot().weatherIconKind==WeatherTextIcon.Kind.CLEAR_NIGHT,"same weather ID and text with native night resource advances the revision");
        source.setData(data(54,R.drawable.ic_weather_light_rain));
        check(SystemWeatherText.captureController(source)&&SystemWeatherText.snapshot().weatherIconKind==WeatherTextIcon.Kind.LIGHT_RAIN,"resolved native resource wins over any conflicting or guessed weather ID");
        long rain=SystemWeatherText.revision();source.setData(data(3,R.drawable.ic_weather_light_rain));
        check(SystemWeatherText.captureController(source)&&SystemWeatherText.revision()==rain+1&&SystemWeatherText.snapshot().weatherId==3,"native weather ID changes are visible even when the category and text match");
        SystemWeatherText.Snapshot stable=SystemWeatherText.snapshot();
        for(int event=0;event<1000;event++)SystemWeatherText.captureController(source);
        check(SystemWeatherText.snapshot()==stable,"same native ID, kind and text preserve snapshot identity");
        check(WeatherTextIcon.aospKind("CLEAR_NIGHT")==WeatherTextIcon.Kind.CLEAR_NIGHT
                &&WeatherTextIcon.aospKind("HEAVY_SNOW")==WeatherTextIcon.Kind.HEAVY_SNOW,"declared AOSP state names keep night and weather categories");
        check(WeatherTextIcon.aospKind("NEW_FUTURE_STATE")==WeatherTextIcon.Kind.UNKNOWN,"unknown enum state is explicit");
    }

    private static void inlineTemplates() {
        SystemWeatherText.clear();WeatherNativeFixture fixture=new WeatherNativeFixture();fixture.update("相同天气文字","26°","light_rain.svg",true);
        TimeZone zone=TimeZone.getTimeZone("UTC");
        CharSequence ordinary=BigClockTextContent.resolveStyled("今天（天气），(温度)",0L,zone);
        check(spans(ordinary).length==1&&spans(ordinary)[0].kind==WeatherTextIcon.Kind.LIGHT_RAIN,"weather uses the actual native image snapshot");
        check(ordinary.toString().equals("今天相同天气文字 ·26° \ufffc，26°"),"final format places native icon at far right after description and dotted temperature");
        CharSequence explicit=BigClockTextContent.resolveStyled("（天气图标）/(天气文字)/(天气)",0L,zone);
        check(spans(explicit).length==2,"native icon-only, text-only and weather tokens coexist");
        check(explicit.toString().equals("\ufffc/相同天气文字 26°/相同天气文字 ·26° \ufffc"),"text-only API stays compatible while combined weather follows requested format");
        check(BigClockTextContent.resolve("（天气）",0L,zone).equals("相同天气文字 26°"),"plain accessible weather text retains compatibility");
        for(String token:new String[]{"（天气图标）","(天气图标)","（天气文字）","(天气文字)"})check(BigClockTextContent.usesWeather(token),"explicit weather tokens retain event dependency");
        CharSequence retained=BigClockTextContent.resolveStyled("（天气）",0L,zone,SystemWeatherText.snapshot(),BigClockSystemText.snapshot(),ordinary);
        check(spans(retained)[0]==spans(ordinary)[0],"same slot snapshot can reuse its independent native image span");
        SystemWeatherText.clear();
        check(BigClockTextContent.resolveStyled("（天气）",0L,zone).toString().equals("天气暂无数据"),"missing weather remains readable text");
        check(BigClockTextContent.resolveStyled("(天气图标)",0L,zone).length()==0,"missing native image never falls back to a counterfeit sunny/question-mark outline");
        SystemWeatherText.captureController(new WeatherEffectController(true,data(3,R.drawable.ic_weather_sunny)));
        CharSequence modelOnly=BigClockTextContent.resolveStyled("（天气）",0L,zone);
        check(spans(modelOnly).length==1&&modelOnly.toString().equals("相同天气文字 ·26° \ufffc")
                &&spans(modelOnly)[0].kind==WeatherTextIcon.Kind.SUNNY,"real controller semantics select stable module artwork without a loaded widget image");
    }
    private static final class DrawCanvas extends Canvas {
        boolean fail;int coverageAlpha;float nativeScaleX,nativeScaleY;
        @Override public void scale(float x,float y){nativeScaleX=x;nativeScaleY=y;super.scale(x,y);}
        @Override public void drawRect(android.graphics.RectF rect,Paint paint){coverageAlpha=paint.getAlpha();super.drawRect(rect,paint);}
        @Override public void drawPicture(android.graphics.Picture picture){if(fail)throw new IllegalStateException("native picture failure");super.drawPicture(picture);}
    }
    private static void drawAndRestore() {
        SystemWeatherText.clear();WeatherNativeFixture fixture=new WeatherNativeFixture();fixture.update("雨","16°","light_rain.svg",true);
        SystemWeatherText.Snapshot snapshot=SystemWeatherText.snapshot();
        android.graphics.drawable.PictureDrawable nativeImage=(android.graphics.drawable.PictureDrawable)readNative(fixture.view);
        nativeImage.setBounds(7,8,47,48);android.graphics.drawable.Drawable.Callback callback=nativeImage.getCallback();
        android.graphics.Picture original=nativeImage.getPicture();
        WeatherTextIcon.Span span=spans(BigClockTextContent.resolveStyled("（天气）",0L,TimeZone.getTimeZone("UTC")))[0];
        Paint paint=new Paint();paint.setTextSize(20f);paint.setColor(0x8044aabb);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3f);
        Shader shader=new LinearGradient(0f,0f,0f,20f,0xff00ff00,0xff0000ff,Shader.TileMode.CLAMP);paint.setShader(shader);
        Paint.FontMetricsInt metrics=new Paint.FontMetricsInt();int width=span.getSize(paint,"\ufffc",0,1,metrics);
        check(width==20&&metrics.ascent<0&&metrics.descent>=0,"native aspect follows visible text height without enlarging the font");
        DrawCanvas target=new DrawCanvas();span.draw(target,"\ufffc",0,1,10f,0,20,30,paint);
        check(target.pictureDraws==1&&target.lastPicture!=original&&target.lastPicture.commandsForCheck()==original.commandsForCheck(),"span draws an independent exact native vector recording, never a replacement path");
        check(Math.abs(target.nativeScaleX-.5f)<.001f&&Math.abs(target.nativeScaleY-.5f)<.001f, "native Picture is scaled to glyph height through the canvas rather than clipped by smaller drawable bounds");
        check(target.lastRectColor==paint.getColor()&&target.coverageAlpha==128,"native silhouette supports selected component colour");
        check(target.lastFillShader==shader&&target.lastRectMode instanceof android.graphics.PorterDuffXfermode
                &&((android.graphics.PorterDuffXfermode)target.lastRectMode).mode==android.graphics.PorterDuff.Mode.SRC_IN,"native icon coverage follows current text shader with a bounded local mask");
        check(paint.getStyle()==Paint.Style.STROKE&&paint.getStrokeWidth()==3f&&paint.getShader()==shader&&paint.getColor()==0x8044aabb,"native drawing never mutates neighbouring text paint");
        for(int frame=0;frame<1000;frame++)span.draw(target,"\ufffc",0,1,10f,0,20,30,paint);
        check(target.pictureDraws==1001&&target.bitmapDraws==0&&target.nodeDraws==0,"stable draws use vector recording without bitmap capture, decode or new blur");
        check(nativeImage.getBounds().left==7&&nativeImage.getBounds().top==8&&nativeImage.getBounds().right==47
                &&nativeImage.getCallback()==callback&&nativeImage.getPicture()==original,"native lock-screen image bounds, callback and recording remain untouched");
        check(fixture.view.getterCalls==0,"native ey.getDrawable path that can rasterize is never invoked");
        Paint coverage=new Paint();coverage.setColor(0xffffffff);coverage.setTextSize(20f);DrawCanvas nativeColours=new DrawCanvas();
        span.draw(nativeColours,"\ufffc",0,1,0,0,20,30,coverage);
        check(nativeColours.pictureDraws==1&&nativeColours.layers==0&&nativeColours.rectFills==0,"ordinary/native colour and parent material coverage draw without an extra offscreen icon layer");
        long revision=snapshot.revision;check(!SystemWeatherText.captureWidgetCard(fixture.card)&&SystemWeatherText.revision()==revision,"identical native image delivery reuses snapshot identity");
        target.fail=true;int saves=target.getSaveCount();boolean failed=false;
        try{span.draw(target,"\ufffc",0,1,10f,0,20,30,paint);}catch(IllegalStateException expected){failed=true;}
        check(failed&&target.getSaveCount()==saves&&paint.getStyle()==Paint.Style.STROKE,"native draw failure restores canvas and leaves paint intact");
        fixture.icon.D.put("src","/cards/card2x1/images/night_sunny.svg");
        check(SystemWeatherText.captureWidgetCard(fixture.card)&&SystemWeatherText.snapshot().weatherIcon==null,"source change cannot reuse an old still-loaded sunny/rain image");
        check(SystemWeatherText.instantImageDocument(fixture.view)==fixture.renderer.b.c,"only observed native image delivery selects its bound document");
        fixture.view.u(WeatherNativeFixture.image());check(SystemWeatherText.captureWidgetCard(fixture.card)&&SystemWeatherText.snapshot().weatherIcon!=null,"late native load callback supplies the real image without polling");
        check(SystemWeatherText.diagnose(null).nativeIconReady,"diagnostic reports detached image readiness without pixels or paths");
        SystemWeatherText.clear();WeatherNativeFixture cold=new WeatherNativeFixture();cold.update("晴","23°","sunny.svg",false);
        android.graphics.drawable.PictureDrawable pending=new android.graphics.drawable.PictureDrawable(null);cold.view.u(pending);
        check(SystemWeatherText.instantImageDocument(cold.view)==cold.renderer.b.c,"cold image delivery identifies only its observed weather owner");
        SystemWeatherText.captureWidgetCard(cold.card);check(!SystemWeatherText.diagnose(null).nativeIconReady,"delivered SVG without native bounds has no recording yet");
        pending.setPicture(WeatherNativeFixture.image().getPicture());
        check(SystemWeatherText.instantPictureDocument(pending)==cold.renderer.b.c&&SystemWeatherText.captureWidgetCard(cold.card)
                &&SystemWeatherText.snapshot().weatherIcon!=null,"native post-bounds recording callback completes the icon without a timer or a second weather delivery");
        SystemWeatherText.clear();check(SystemWeatherText.instantImageDocument(fixture.view)==null,"teardown invalidates native image callbacks");
    }
    private static android.graphics.drawable.Drawable readNative(android.widget.ImageView view){
        try{java.lang.reflect.Field field=android.widget.ImageView.class.getDeclaredField("mDrawable");field.setAccessible(true);return (android.graphics.drawable.Drawable)field.get(view);}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    public static void main(String[] args) throws Exception {
        final int[] reads={0};
        Paint glyphPaint=new Paint(){@Override public void getTextBounds(String value,int start,int end,android.graphics.Rect rect){reads[0]++;rect.set(0,-13,12,2);}};
        glyphPaint.setTextSize(20);
        WeatherTextIcon.IconMetrics glyph=new WeatherTextIcon.IconMetrics();glyph.update(glyphPaint);
        check(glyph.top==-13&&glyph.height==15,"weather uses visible glyph height and baseline, not 1.35 em");
        for(int i=0;i<1000;i++)glyph.update(glyphPaint);
        check(reads[0]==1,"stable frames reuse measured glyph bounds");
        glyphPaint.setTextSize(30);glyph.update(glyphPaint);check(reads[0]==2,"font size invalidates metrics");
        try{nativeResources();inlineTemplates();drawAndRestore();}finally{SystemWeatherText.clear();}
        System.out.println("WeatherTextIconCheck: "+checks+" checks passed (native vector and delivery seams; phone raster pending)");
    }
}
