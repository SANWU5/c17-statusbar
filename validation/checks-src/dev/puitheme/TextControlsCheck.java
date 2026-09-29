package dev.puitheme;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import com.oplus.systemui.statusbar.widget.StatClock;
import com.oplus.systemui.qs.widget.OplusSecondCarrierText;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
public final class TextControlsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {checks++;if(!expected.equals(actual))throw new AssertionError("Expected "+expected+", got "+actual);}
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.0001)throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args) {
        Handler handler=new Handler(Looper.getMainLooper());TextControls control=new TextControls(handler);Context context=new Context();
        StatClock clock=new StatClock(context);OplusSecondCarrierText carrier=new OplusSecondCarrierText(context);
        clock.setText("系统时钟");carrier.setText("中国移动");clock.setContentDescription("原始时间");carrier.setContentDescription("原始运营商");
        control.attach(clock);control.attach(carrier);equal("系统时钟",clock.getText());equal("中国移动",carrier.getText());equal(true,handler.delayed==null);
        Bundle settings=new Bundle();settings.putBoolean(StatusBarSettings.CLOCK_ENABLED,true);settings.putString(StatusBarSettings.CLOCK_PATTERN,"'C17' HH:mm:ss");
        settings.putString(StatusBarSettings.CARRIER_MODE,"text");settings.putString(StatusBarSettings.CARRIER_TEXT,"aiingjie");
        settings.putFloat(StatusBarSettings.CLOCK_SCALE,123.45f);settings.putFloat(StatusBarSettings.CLOCK_WEIGHT,712f);settings.putFloat(StatusBarSettings.CLOCK_SPACING,.25f);
        control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        equal(true,clock.getText().toString().matches("C17 \\d{2}:\\d{2}:\\d{2}"));equal("aiingjie",carrier.getText());
        near(13*1.2345f,clock.getTextSize());equal(712,clock.getTypeface().weight);equal(0xccffffff,clock.getCurrentTextColor());
        equal(true,handler.delayed!=null&&handler.delay>0&&handler.delay<=1000);near(.25f*4/(13*1.2345f),clock.getLetterSpacing());
        for(int i=0;i<30;i++)control.refresh();near(13*1.2345f,clock.getTextSize());
        control.enter();control.nativeText(clock,"模块自己的文字");control.nativeSize(clock,99);control.exit();
        carrier.setText(control.nativeText(carrier,"中国联通"));equal("aiingjie",carrier.getText());
        carrier.setContentDescription(control.nativeDescription(carrier,"中国联通网络"));equal("aiingjie",carrier.getContentDescription());
        control.setInteractive(false);equal(true,handler.delayed==null);control.setInteractive(true);equal(true,handler.delayed!=null);
        clock.shown=false;control.visibilityChanged();equal(true,handler.delayed==null);clock.shown=true;control.refresh();equal(true,handler.delayed!=null);
        clock.attached=false;carrier.attached=false;control.detach(clock);control.detach(carrier);equal(true,handler.delayed==null);
        clock.attached=true;carrier.attached=true;control.attach(clock);control.attach(carrier);
        settings.putBoolean(StatusBarSettings.CLOCK_ENABLED,false);settings.putString(StatusBarSettings.CARRIER_MODE,"original");
        control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        equal("系统时钟",clock.getText());equal("中国联通",carrier.getText());equal("原始时间",clock.getContentDescription());equal("中国联通网络",carrier.getContentDescription());equal(true,handler.delayed==null);
        settings.putString(StatusBarSettings.CARRIER_MODE,"time");settings.putString(StatusBarSettings.CARRIER_PATTERN,"HH:mm");control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        equal(true,carrier.getText().toString().matches("\\d{2}:\\d{2}"));equal(true,handler.delayed!=null&&handler.delay>0&&handler.delay<=60000);
        settings.putString(StatusBarSettings.CARRIER_PATTERN,"broken'");settings.putFloat(StatusBarSettings.CLOCK_SCALE,Float.NaN);
        control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());near(13,clock.getTextSize());
        equal(true,carrier.getText().toString().contains("周"));
        Map<String,Integer> palette=new HashMap<>(StatusBarSettings.COLOR_DEFAULTS);palette.put("clock_color_dark",0xff123456);
        control.configure(settings,palette,Collections.emptyMap());equal(0xcc123456,clock.getCurrentTextColor());
        equal(0xe6000000,control.nativeColor(clock,0xe6000000));
        equal(TextControls.NONE,TextControls.namedKind("com.oplus.systemui.qs.widget.OplusQSCarrierText","carrier_text",""));
        equal(TextControls.NONE,TextControls.namedKind("com.android.systemui.statusbar.policy.Clock","clock","com.android.systemui.QSHeader"));
        equal(TextControls.CARRIER,TextControls.namedKind("android.widget.TextView","carrier_text","com.android.systemui.shade.carrier.ShadeCarrier"));
        equal(TextControls.NONE,control.kind(new TextView(context)));
        System.out.println(checks+" text state, precision, tint and scheduler checks passed");
    }
}
