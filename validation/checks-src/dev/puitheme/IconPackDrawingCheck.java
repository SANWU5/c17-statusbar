package dev.puitheme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.service.notification.StatusBarNotification;
import android.view.View;
import com.android.systemui.statusbar.StatusBarIconView;
import com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable;
import com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView;
import java.util.ArrayList;
import java.util.List;

/** Runs real renderer code against typed OEM draw slots, not a mocked always-successful replacement. */
public final class IconPackDrawingCheck {
    private static int checks;
    private static void eq(int expected,int actual){checks++;if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
    private static void eq(Object expected,Object actual){checks++;if(expected==null?actual!=null:!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    private static void yes(boolean value){checks++;if(!value)throw new AssertionError("Expected true");}
    private static void no(boolean value){yes(!value);}
    private static void same(Object expected,Object actual){checks++;if(expected!=actual)throw new AssertionError("Identity changed");}
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.0001f)throw new AssertionError(expected+" != "+actual);}
    private static final class Assets implements IconPackDrawing.Assets {
        final Bitmap image=Bitmap.createBitmap(116,52,Bitmap.Config.ARGB_8888);
        boolean available=true;int wifiCalls,cellCalls,batteryCalls,hintCalls,level;boolean connected,charging,on;
        String slot;final List<View> observed=new ArrayList<>();
        public Bitmap hint(String slot,boolean on){hintCalls++;this.slot=slot;this.on=on;return available?image:null;}
        public Bitmap wifi(int level,boolean connected){wifiCalls++;this.level=level;this.connected=connected;return available?image:null;}
        public Bitmap cellular(int level,boolean connected){cellCalls++;this.level=level;this.connected=connected;return available?image:null;}
        public Bitmap battery(int level,boolean charging){batteryCalls++;this.level=level;this.charging=charging;return available?image:null;}
        public void observe(View view){observed.add(view);}
    }
    public static final class Graphic extends Drawable {
        int draws,alpha=255;ColorFilter filter;boolean shown=true;
        @Override public void draw(Canvas canvas){draws++;}
        @Override public void setAlpha(int value){alpha=value;}
        @Override public int getAlpha(){return alpha;}
        @Override public void setColorFilter(ColorFilter value){filter=value;}
        @Override public ColorFilter getColorFilter(){return filter;}
        @Override public int getOpacity(){return -3;}
        @Override public int getIntrinsicWidth(){return 58;}
        @Override public int getIntrinsicHeight(){return 26;}
        @Override public boolean setVisible(boolean value,boolean restart){shown=value;return true;}
    }
    private static final class RecordingCanvas extends Canvas {
        RectF bitmapBounds=new RectF();int tint,alpha;ColorFilter filter;boolean failBitmap;
        @Override public void drawBitmap(Bitmap image,Rect source,RectF bounds,Paint paint){
            if(failBitmap){failBitmap=false;throw new IllegalStateException("GPU unavailable");}
            super.drawBitmap(image,source,bounds,paint);bitmapBounds.set(bounds.left,bounds.top,bounds.right,bounds.bottom);
            tint=paint.getColor();alpha=paint.getAlpha();filter=paint.getColorFilter();
        }
    }
    public static final class HintView extends StatusBarIconView {
        String slot="bluetooth";
        final Resources resources=new Resources(){@Override public String getResourceEntryName(int id){return id==1?"stat_sys_data_bluetooth":"stat_sys_data_bluetooth_connected";}};
        HintView(){super(new Context());}
        public String getSlot(){return slot;}
        @Override public Resources getResources(){return resources;}
    }
    public static final class ModelIcon {final int id;ModelIcon(int id){this.id=id;}public int getResId(){return id;}}
    public static final class Model {public final ModelIcon icon;Model(int id){icon=new ModelIcon(id);}}
    public static final class Charge {public final boolean isVisible;public final int iconId;Charge(boolean visible,int id){isVisible=visible;iconId=id;}}
    private static final class Callback implements Drawable.Callback {
        int invalidations,schedules,unschedules;
        public void invalidateDrawable(Drawable who){invalidations++;}
        public void scheduleDrawable(Drawable who,Runnable task,long when){schedules++;}
        public void unscheduleDrawable(Drawable who,Runnable task){unschedules++;}
    }
    private static void signals(){
        Assets assets=new Assets();IconPackDrawing drawing=new IconPackDrawing(assets);Graphic original=new Graphic();
        original.setBounds(4,8,62,34);RecordingCanvas canvas=new RecordingCanvas();
        for(int level=0;level<=4;level++)for(String suffix:new String[]{"","_os17"}){
            yes(drawing.drawSignal("stat_signal_wifi_signal_"+level+suffix,original,canvas,0xffabcdef,173,null,false));
            eq(level,assets.level);yes(assets.connected);eq(0xffabcdef,canvas.tint);eq(173,canvas.alpha);
            near(4,canvas.bitmapBounds.left);near(8,canvas.bitmapBounds.top);near(62,canvas.bitmapBounds.right);near(34,canvas.bitmapBounds.bottom);
        }
        for(int level=0;level<=4;level++)for(String family:new String[]{"stat_signal_lte_signal_","stat_signal_soft_signal_","stat_signal_lte_signal_stacked_primary_","stat_signal_soft_signal_stacked_secondary_","stat_signal_signal_lte_single_","stat_sys_signal_"}){
            yes(drawing.drawSignal(family+level,original,canvas,0xffffffff,255,null,false));eq(level,assets.level);yes(assets.connected);
        }
        for(String name:new String[]{"stat_signal_lte_signal_stacked_primary_noservice","stat_signal_soft_signal_noservice_os17","stat_signal_noservice_lte","stat_signal_soft_noservice_os17","stat_signal_signal_null_lte","stat_sys_signal_null","stat_sys_signal_noservice"}){
            yes(drawing.drawSignal(name,original,canvas,0xffffffff,255,null,false));no(assets.connected);
        }
        for(int level=1;level<=4;level++){yes(drawing.drawSignal("stat_signal_signal_novoice_"+level+"_os17",original,canvas,0xffffffff,255,null,false));eq(level,assets.level);}
        yes(drawing.drawSignal("stat_signal_single_novoice_0_os17",original,canvas,0xffffffff,255,null,false));eq(0,assets.level);yes(assets.connected);
        int cellCalls=assets.cellCalls;
        no(drawing.drawSignal("stat_signal_lte_signal_stacked_secondary_4",original,canvas,0xffffffff,255,null,true));eq(cellCalls,assets.cellCalls);
        int wifiCalls=assets.wifiCalls;
        for(String unknown:new String[]{"ic_wifi","app_icon","c17_signal_single_4","stat_signal_wifi_signal_5","stat_signal_wifi_6","stat_signal_signal_novoice_0",
                "stat_signal_activity_wifi_none","stat_signal_activity_wifi_none_os17","stat_signal_wifi_none","stat_signal_wifi_signal_none"}){
            no(drawing.drawSignal(unknown,original,canvas,0xffffffff,255,null,false));
        }
        eq(wifiCalls,assets.wifiCalls);
        eq(null,SignalResources.moduleName("stat_signal_activity_wifi_none",false));
        eq(null,SignalResources.moduleName("stat_signal_activity_wifi_none_os17",false));
        near(1,canvas.scaleX);near(1,canvas.scaleY);near(0,canvas.translateX);near(0,canvas.translateY);eq(0,canvas.saves);eq(0,canvas.restores);eq(0,original.draws);
        ColorFilter nativeFilter=new PorterDuffColorFilter(0x80abcdef,PorterDuff.Mode.SRC_IN);
        yes(drawing.drawSignal("stat_signal_wifi_signal_4",original,canvas,0x80abcdef,201,nativeFilter,false));eq(201,canvas.alpha);same(nativeFilter,canvas.filter);
        yes(drawing.drawSignal("stat_signal_wifi_signal_4",original,canvas,0x80abcdef,201,null,false));eq(100,canvas.alpha);
        assets.available=false;no(drawing.drawSignal("stat_signal_wifi_signal_4",original,canvas,0xffffffff,255,null,false));
        assets.available=true;canvas.failBitmap=true;no(drawing.drawSignal("stat_signal_wifi_signal_4",original,canvas,0xffffffff,255,null,false));
        int creations=Bitmap.creations,reads=Bitmap.reads;
        for(int i=0;i<10000;i++)yes(drawing.drawSignal("stat_signal_wifi_signal_4",original,canvas,0xffffffff,255,null,false));
        eq(creations,Bitmap.creations);eq(reads,Bitmap.reads);
        drawing.releaseRuntime();
        no(drawing.drawSignal("stat_signal_wifi_signal_4",original,canvas,0xffffffff,255,null,false));
    }
    private static void hints(){
        Assets assets=new Assets();IconPackDrawing drawing=new IconPackDrawing(assets);Graphic nativeGraphic=new Graphic();HintView view=new HintView();
        nativeGraphic.setBounds(2,3,60,29);nativeGraphic.setAlpha(190);
        Drawable hint=drawing.wrapHint(view,new Model(1),nativeGraphic);yes(hint!=nativeGraphic);eq(58,hint.getIntrinsicWidth());eq(26,hint.getIntrinsicHeight());same(view,assets.observed.get(0));
        RecordingCanvas canvas=new RecordingCanvas();hint.draw(canvas);eq("bluetooth",assets.slot);no(assets.on);eq(190,canvas.alpha);eq(0,nativeGraphic.draws);
        same(hint,drawing.wrapHint(view,new Model(2),hint));hint.draw(canvas);yes(assets.on);
        hint.setTint(0xff123456);hint.setAlpha(144);hint.draw(canvas);eq(0xff123456,canvas.tint);eq(144,canvas.alpha);
        ColorFilter filter=new PorterDuffColorFilter(0x80887766,PorterDuff.Mode.SRC_IN);hint.setColorFilter(filter);hint.draw(canvas);same(filter,canvas.filter);eq(144,canvas.alpha);
        hint.setBounds(8,7,66,33);hint.draw(canvas);eq(8,nativeGraphic.getBounds().left);near(8,canvas.bitmapBounds.left);
        Callback callback=new Callback();hint.setCallback(callback);nativeGraphic.invalidateSelf();eq(1,callback.invalidations);
        Runnable event=()->{};nativeGraphic.scheduleSelf(event,12);nativeGraphic.unscheduleSelf(event);eq(1,callback.schedules);eq(1,callback.unschedules);
        yes(hint.setVisible(false,false));no(nativeGraphic.shown);
        view.setNotification(new StatusBarNotification(1));same(nativeGraphic,drawing.wrapHint(view,null,nativeGraphic));
        view.setNotification(null);view.slot="mobile";same(nativeGraphic,drawing.wrapHint(view,null,nativeGraphic));
        same(nativeGraphic,drawing.wrapHint(new View(new Context()),null,nativeGraphic));
        view.slot="location";Drawable location=drawing.wrapHint(view,null,new Graphic());location.setBounds(0,0,58,26);location.draw(canvas);eq("location",assets.slot);yes(assets.on);
        assets.available=false;hint.draw(canvas);eq(1,nativeGraphic.draws);
        drawing.invalidateAll();yes(callback.invalidations>1);drawing.releaseRuntime();
        assets.available=true;hint.draw(canvas);eq(2,nativeGraphic.draws);same(nativeGraphic,drawing.wrapHint(view,null,nativeGraphic));
    }
    private static HorizontalBatteryContentDrawable battery(Graphic[] shapes){
        for(int i=0;i<shapes.length;i++)shapes[i]=new Graphic();
        HorizontalBatteryContentDrawable battery=new HorizontalBatteryContentDrawable();
        battery.outsideDrawable=shapes[0];battery.insideDrawable=new LayerDrawable(new Drawable[]{shapes[1]});
        battery.frameDrawable=shapes[2];battery.bgDrawable=shapes[3];battery.progressDrawable=new LayerDrawable(new Drawable[]{shapes[4]});
        battery.percentInPaint.setTextSize(18);battery.percentInPaint.setTypeface(Typeface.create(Typeface.DEFAULT,700,false));battery.setBounds(0,0,66,34);return battery;
    }
    private static Drawable[] originalFields(HorizontalBatteryContentDrawable b){return new Drawable[]{b.outsideDrawable,b.insideDrawable,b.frameDrawable,b.bgDrawable,b.progressDrawable};}
    private static void restored(HorizontalBatteryContentDrawable b,Drawable[] expected){Drawable[] actual=originalFields(b);for(int i=0;i<actual.length;i++)same(expected[i],actual[i]);}
    private static void batteries(){
        Assets assets=new Assets();IconPackDrawing drawing=new IconPackDrawing(assets);drawing.bindBatteryClass(HorizontalBatteryContentDrawable.class);
        Graphic[] shapes=new Graphic[5];HorizontalBatteryContentDrawable b=battery(shapes);Drawable[] original=originalFields(b);
        Callback callback=new Callback();for(Graphic shape:shapes)shape.setCallback(callback);
        Typeface face=b.percentInPaint.getTypeface();RecordingCanvas canvas=new RecordingCanvas();
        try(IconPackDrawing.BatteryScope scope=drawing.beginBattery(b)){yes(scope!=null);b.draw(canvas);}
        eq(1,canvas.bitmapDraws);eq(0,shapes[0].draws);eq(0,shapes[1].draws);eq(0,shapes[2].draws);eq(1,b.textDraws);
        eq("84",canvas.text);near(18,canvas.textSize);same(face,b.percentInPaint.getTypeface());near(700,canvas.textEffectiveWeight);near(36,canvas.textX);near(14,canvas.textY);restored(b,original);
        for(Graphic shape:shapes)same(callback,shape.getCallback());
        eq(84,assets.level);no(assets.charging);near(4,canvas.bitmapBounds.left);near(3,canvas.bitmapBounds.top);near(62,canvas.bitmapBounds.right);near(29,canvas.bitmapBounds.bottom);
        int creations=Bitmap.creations;
        for(int i=0;i<1000;i++){
            b.batteryLevel=i%101;b.chargeIconId=i%2;
            try(IconPackDrawing.BatteryScope scope=drawing.beginBattery(b)){b.draw(canvas);}
            eq(Integer.toString(b.batteryLevel),canvas.text);eq(b.batteryLevel,assets.level);eq(b.chargeIconId>0,assets.charging);restored(b,original);
        }
        eq(creations,Bitmap.creations);eq(1001,b.textDraws);eq(500,b.chargeDraws);same(face,b.percentInPaint.getTypeface());near(18,b.percentInPaint.getTextSize());
        b.batteryLevel=61;b.chargeIconId=1;b.isShowPercentIn=false;int oldText=b.textDraws;
        try(IconPackDrawing.BatteryScope scope=drawing.beginBattery(b)){b.draw(canvas);}
        eq(oldText,b.textDraws);eq(501,b.chargeDraws);near(2,canvas.bitmapBounds.left);near(7,canvas.bitmapBounds.top);restored(b,original);
        b.isShowPercentIn=true;b.chargeIconId=0;StatBatteryMeterView owner=new StatBatteryMeterView();owner.style=b;owner.charge=new Charge(true,4);
        drawing.syncBattery(owner,null);try(IconPackDrawing.BatteryScope scope=drawing.beginBattery(b)){b.draw(canvas);}yes(assets.charging);same(owner,assets.observed.get(0));
        drawing.syncBattery(owner,new Charge(false,0));try(IconPackDrawing.BatteryScope scope=drawing.beginBattery(b)){b.draw(canvas);}no(assets.charging);
        int masks=canvas.bitmapDraws;
        try(IconPackDrawing.BatteryScope outer=drawing.beginBattery(b)){
            b.draw(canvas);try(IconPackDrawing.BatteryScope inner=drawing.beginBattery(b)){b.draw(canvas);}
            yes(b.outsideDrawable!=original[0]);
        }
        eq(masks+1,canvas.bitmapDraws);restored(b,original);
        canvas.failBitmap=true;try(IconPackDrawing.BatteryScope scope=drawing.beginBattery(b)){b.draw(canvas);}
        eq(1,shapes[0].draws);eq(1,shapes[1].draws);eq(1,shapes[2].draws);restored(b,original);
        assets.available=false;same(null,drawing.beginBattery(b));b.draw(canvas);eq(2,shapes[0].draws);restored(b,original);
        assets.available=true;b.batteryLevel=-1;same(null,drawing.beginBattery(b));restored(b,original);
        b.batteryLevel=101;same(null,drawing.beginBattery(b));restored(b,original);
        b.batteryLevel=30;b.rect.right=b.rect.left;same(null,drawing.beginBattery(b));restored(b,original);b.rect.right=62;
        b.rect.top=Float.NaN;same(null,drawing.beginBattery(b));restored(b,original);b.rect.top=3;
        b.batteryLevel=30;b.failContent=true;
        try(IconPackDrawing.BatteryScope scope=drawing.beginBattery(b)){b.draw(canvas);throw new AssertionError("Expected native exception");}
        catch(IllegalStateException expected){eq("Native content failed",expected.getMessage());}restored(b,original);b.failContent=false;
        Graphic replacement=new Graphic();try(IconPackDrawing.BatteryScope scope=drawing.beginBattery(b)){b.bgDrawable=replacement;}
        same(replacement,b.bgDrawable);b.bgDrawable=original[3];
        IconPackDrawing.BatteryScope scope=drawing.beginBattery(b);drawing.releaseRuntime();restored(b,original);scope.close();restored(b,original);
        same(null,drawing.beginBattery(b));
        same(null,drawing.beginBattery(new Graphic()));
    }
    public static void main(String[] args){signals();hints();batteries();System.out.println("IconPackDrawingCheck: "+checks+" checks passed");}
}
