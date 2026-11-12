// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.json.JSONObject;

/** Shared form input semantics; manual precision is independent of a slider's gesture step. */
public final class CustomPluginControlValues {
    private CustomPluginControlValues() { }

    public static double parseSlider(JSONObject control,String input) {
        requireSlider(control);
        BigDecimal value=NumericInput.decimal(input);
        BigDecimal min=decimal(control.opt("min")),max=decimal(control.opt("max"));
        if(!CustomPluginSchema.unboundedInput(control)&&(value.compareTo(min)<0||value.compareTo(max)>0))
            throw new IllegalArgumentException("请输入 "+plain(min)+" 到 "+plain(max)+" 之间的数值");
        double result=value.doubleValue();
        CustomPluginSchema.stateValue(control,result);
        return result;
    }

    /** Finger movement snaps to the declared step, anchored at min, without float tails. */
    public static double snapSlider(JSONObject control,double value) {
        requireSlider(control);
        if(!Double.isFinite(value))throw new IllegalArgumentException("请输入有效的有限数值");
        BigDecimal min=decimal(control.opt("min")),max=decimal(control.opt("max"));
        BigDecimal step=control.has("step")?decimal(control.opt("step")):BigDecimal.ONE;
        BigDecimal bounded=BigDecimal.valueOf(value).max(min).min(max);
        BigDecimal ticks=bounded.subtract(min).divide(step,0,RoundingMode.HALF_UP);
        return min.add(ticks.multiply(step)).max(min).min(max).doubleValue();
    }

    /** No unit suffix, so the same representation can seed the numeric editor. */
    public static String formatSlider(JSONObject control,Number value) {
        requireSlider(control);
        CustomPluginSchema.stateValue(control,value);
        return plain(decimal(value));
    }

    /** Editor hint: the drag range, and whether typing may go beyond it. */
    public static String sliderRange(JSONObject control) {
        requireSlider(control);
        String range=plain(decimal(control.opt("min")))+"–"+plain(decimal(control.opt("max")))+control.optString("unit");
        return CustomPluginSchema.unboundedInput(control)?"滑块范围 "+range+"，手动输入不限范围":range;
    }

    public static int parseColor(String input) {
        String value=input==null?"":input.trim();
        if(!value.matches("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})"))
            throw new IllegalArgumentException("颜色应为 #RRGGBB 或 #AARRGGBB");
        long color=Long.parseLong(value.substring(1),16);
        return (int)(value.length()==7?color|0xff000000L:color);
    }

    public static String formatColor(int color,boolean includeAlpha) {
        return includeAlpha?String.format(Locale.ROOT,"#%08X",color)
                :String.format(Locale.ROOT,"#%06X",color&0x00ffffff);
    }

    private static void requireSlider(JSONObject control) {
        if(control==null||!"slider".equals(control.optString("type")))
            throw new IllegalArgumentException("未找到此数值设置");
        // Validate bounds/step before decimal operations; old stored off-step values stay valid.
        CustomPluginSchema.stateValue(control,control.opt("min"));
    }
    private static BigDecimal decimal(Object value) {
        CustomPluginSchema.number(value);
        return new BigDecimal(value.toString());
    }
    private static String plain(BigDecimal value) {
        return value.signum()==0?"0":value.stripTrailingZeros().toPlainString();
    }
}
