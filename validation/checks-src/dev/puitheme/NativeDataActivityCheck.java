package dev.puitheme;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
public final class NativeDataActivityCheck {
    private static int checks;
    private static void eq(Object expected,Object actual){checks++;if(!java.util.Objects.equals(expected,actual))throw new AssertionError(expected+" != "+actual);}
    private static final class Arrow extends Drawable {
        int calls,filters;ColorFilter filter;int alpha=255;boolean fail;
        public void draw(Canvas c){calls++;if(fail)throw new IllegalStateException("native failed");}
        public void setAlpha(int value){alpha=value;}
        public int getAlpha(){return alpha;}
        public void setColorFilter(ColorFilter value){filters++;filter=value;}
        public ColorFilter getColorFilter(){return filter;}
        public int getOpacity(){return -3;}
        public int getIntrinsicWidth(){return 12;}
        public int getIntrinsicHeight(){return 24;}
    }
    private static final class Res extends Resources {
        String name="stat_signal_stacked_activity_inout_public",pkg="com.android.systemui";
        public String getResourceEntryName(int id){return name;}
        public String getResourcePackageName(int id){return pkg;}
    }
    public static void main(String[] args){
        NativeDataActivity helper=new NativeDataActivity();Res res=new Res();res.getDisplayMetrics().density=2f;Arrow nativeArrow=new Arrow();
        Drawable wrapped=helper.wrap(res,7,nativeArrow);eq(true,wrapped instanceof NativeDataActivity.ArrowDrawable);
        eq(wrapped,helper.wrap(res,7,wrapped));eq(12,wrapped.getIntrinsicWidth());eq(24,wrapped.getIntrinsicHeight());
        res.name="stat_signal_activity_wifi_inout";eq(nativeArrow,helper.wrap(res,7,nativeArrow));
        res.name="stat_signal_stacked_activity_inout_public";res.pkg="other.package";eq(nativeArrow,helper.wrap(res,7,nativeArrow));res.pkg="com.android.systemui";
        for(String variant:new String[]{"default","in","out","inout"})for(String family:new String[]{"activity","activity_soft","soft_stacked_activity","stacked_activity"})
            eq(true,NativeDataActivity.source("stat_signal_"+family+"_"+variant+"_public_os17"));
        eq(false,helper.enabled());Canvas off=new Canvas();wrapped.setBounds(0,0,12,24);wrapped.draw(off);eq(0f,off.translateX);eq(1f,off.scaleX);
        Bundle config=new Bundle();config.putBoolean(NativeDataActivity.MASTER,true);config.putFloat(NativeDataActivity.X,4f);config.putFloat(NativeDataActivity.Y,-3f);config.putFloat(NativeDataActivity.SCALE,150f);
        config.putBoolean(NativeDataActivity.COLOR_ENABLED,true);config.putInt(NativeDataActivity.COLOR_LIGHT,0xffff0000);config.putInt(NativeDataActivity.COLOR_DARK,0xffff0000);
        helper.configure(config);Canvas styled=new Canvas();wrapped.draw(styled);eq(8f,styled.translateX);eq(-6f,styled.translateY);eq(1.5f,styled.scaleX);eq(6f,styled.pivotX);eq(12f,styled.pivotY);
        eq(0xffff0000,((PorterDuffColorFilter)nativeArrow.filter).getColor());int writes=nativeArrow.filters;
        for(int i=0;i<1000;i++)wrapped.draw(new Canvas());eq(writes,nativeArrow.filters);
        ((NativeDataActivity.ArrowDrawable)wrapped).composePlacement=true;Canvas compose=new Canvas();wrapped.draw(compose);eq(0f,compose.translateX);eq(1f,compose.scaleX);eq(null,nativeArrow.filter);
        ((NativeDataActivity.ArrowDrawable)wrapped).composePlacement=false;ColorFilter systemFilter=new PorterDuffColorFilter(0xff000000,PorterDuff.Mode.SRC_IN);wrapped.setColorFilter(systemFilter);wrapped.draw(new Canvas());
        helper.configure(null);wrapped.draw(new Canvas());eq(systemFilter,nativeArrow.filter);eq(false,helper.enabled());
        helper.configure(config);config.putBoolean(StatusBarSettings.SAFE_MODE,true);helper.configure(config);eq(false,helper.enabled());eq(0f,helper.x(2f));eq(1f,helper.scale(24f));
        nativeArrow.fail=true;Canvas failed=new Canvas();try{wrapped.draw(failed);throw new AssertionError("missing native error");}catch(IllegalStateException expected){eq(failed.saves,failed.restores);}
        helper.releaseRuntime();eq(false,helper.enabled());
        System.out.println("NativeDataActivityCheck passed: "+checks);
    }
}
