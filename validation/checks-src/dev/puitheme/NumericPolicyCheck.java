package dev.puitheme;

/** Slider suggestions must never silently constrain an accepted stored number. */
public final class NumericPolicyCheck {
    private static int checks;
    private static void near(float expected,float actual) {
        checks++;
        if(Float.isNaN(actual)||Float.isInfinite(actual)||Math.abs(expected-actual)>.0001f)
            throw new AssertionError(expected+" != "+actual);
    }
    private static void equal(long expected,long actual) {
        checks++;if(expected!=actual)throw new AssertionError(expected+" != "+actual);
    }
    public static void main(String[] args) {
        near(777.77f,NumericPolicy.number(777.77f,100f,25f,250f));
        near(0f,NumericPolicy.number(0f,100f,25f,250f));
        near(-1024.25f,NumericPolicy.number(-1024.25f,0f,-80f,80f));
        near(0f,NumericPolicy.number(-2f,100f,25f,250f));
        near(100f,NumericPolicy.number(Float.NaN,100f,25f,250f));
        near(100f,NumericPolicy.number(Float.POSITIVE_INFINITY,100f,25f,250f));
        near(100f,NumericPolicy.number("500",100f,25f,250f));
        near(0f,NumericPolicy.number(null,Float.NaN,25f,250f));
        String[] sizes={"wifi_icon_scale","data_icon_scale","label_scale","slot_width",
                "speed_scale","speed_number_scale","speed_unit_scale","battery_scale",
                "battery_width_scale","battery_height_scale","clock_scale","shade_clock_scale","carrier_scale",
                "carrier_notification_scale","carrier_control_scale","battery_hold_seconds",
                "battery_fade_seconds"};
        for(String key:sizes) {
            near(888.88f,NumericPolicy.setting(key,888.88f,100f));
            near(0f,NumericPolicy.setting(key,0f,100f));
            near(0f,NumericPolicy.setting(key,-1f,100f));
            near(100f,NumericPolicy.setting(key,Float.NEGATIVE_INFINITY,100f));
        }
        String[] signed={"offset_x","offset_y","wifi_offset_x","wifi_offset_y",
                "data_offset_x","data_offset_y","label_offset_x","label_offset_y",
                "speed_offset_x","speed_offset_y","speed_line_gap","battery_offset_x",
                "battery_offset_y","clock_offset_x","clock_offset_y","clock_spacing",
                "shade_clock_offset_x","shade_clock_offset_y","shade_clock_spacing",
                "carrier_notification_offset_x","carrier_notification_offset_y",
                "carrier_notification_spacing","carrier_control_offset_x",
                "carrier_control_offset_y","carrier_control_spacing"};
        for(String key:signed) {
            near(-999.99f,NumericPolicy.setting(key,-999.99f,0f));
            near(999.99f,NumericPolicy.setting(key,999.99f,0f));
            near(0f,NumericPolicy.setting(key,Float.NaN,0f));
        }
        for(String key:new String[]{"font_weight","speed_weight","clock_weight","shade_clock_weight",
                "carrier_weight","carrier_notification_weight","carrier_control_weight"}) {
            near(100f,NumericPolicy.setting(key,0f,600f));
            near(900f,NumericPolicy.setting(key,99999f,600f));
            near(734.25f,NumericPolicy.setting(key,734.25f,600f));
        }
        near(3200.5f,NumericPolicy.pixels(800.125f,4f));
        near(-4000f,NumericPolicy.pixels(-1000f,4f));
        near(NumericPolicy.MAX_DRAW_PIXELS,NumericPolicy.pixels(Float.MAX_VALUE,Float.MAX_VALUE));
        near(-NumericPolicy.MAX_DRAW_PIXELS,NumericPolicy.pixels(-Float.MAX_VALUE,4f));
        near(NumericPolicy.MAX_DRAW_PIXELS,NumericPolicy.pixels(Double.POSITIVE_INFINITY));
        near(-NumericPolicy.MAX_DRAW_PIXELS,NumericPolicy.pixels(Double.NEGATIVE_INFINITY));
        near(0f,NumericPolicy.pixels(Double.NaN));
        near(1f,NumericPolicy.pixels(1f,Float.NaN));
        near(1f,NumericPolicy.pixels(1f,0f));
        near(2000f,NumericPolicy.layoutPixels(500f,4f));
        near(NumericPolicy.MAX_LAYOUT_PIXELS,NumericPolicy.layoutPixels(Float.MAX_VALUE,4f));
        near(0f,NumericPolicy.layoutPixels(-2f,4f));
        equal(65535,NumericPolicy.layoutPixels(Double.POSITIVE_INFINITY));
        equal(0,NumericPolicy.layoutPixels(Double.NaN));
        equal(100,NumericPolicy.layoutPixels(99.75d));
        near(16383.75f,NumericPolicy.layoutDp(Float.MAX_VALUE,4f));
        near(0f,NumericPolicy.textPixels(0d));
        near(0f,NumericPolicy.textPixels(-100d));
        near(1.25f,NumericPolicy.textPixels(1.25d));
        near(4096f,NumericPolicy.textPixels(Double.MAX_VALUE));
        near(0f,NumericPolicy.textPixels(Double.NaN));
        near(3f,NumericPolicy.scale(3f,124f));
        near(0f,NumericPolicy.scale(0f,124f));
        near(12f,NumericPolicy.scale(3f,4f,124f));
        near(NumericPolicy.MAX_DRAW_PIXELS/124f,
                NumericPolicy.scale(Float.MAX_VALUE,Float.MAX_VALUE,124f));
        near(0f,NumericPolicy.scale(Float.MAX_VALUE,0f,124f));
        near(1f,NumericPolicy.scale(Float.NaN,124f));
        near(NumericPolicy.MAX_DRAW_PIXELS/124f,NumericPolicy.scale(Double.MAX_VALUE,124f));
        near(NumericPolicy.MAX_DRAW_PIXELS/124f,NumericPolicy.scale(Double.POSITIVE_INFINITY,124f));
        near(0f,NumericPolicy.scale(Double.NEGATIVE_INFINITY,124f));
        near(1f,NumericPolicy.scale(Double.NaN,124f));
        equal(2370,NumericPolicy.milliseconds(2.37f,3f));
        equal(100000,NumericPolicy.milliseconds(100f,3f));
        equal(0,NumericPolicy.milliseconds(0f,3f));
        equal(0,NumericPolicy.milliseconds(-1f,3f));
        equal(1,NumericPolicy.milliseconds(Float.MIN_VALUE,3f));
        equal(3000,NumericPolicy.milliseconds(Float.NaN,3f));
        equal(Long.MAX_VALUE/8,NumericPolicy.milliseconds(Float.MAX_VALUE,3f));
        System.out.println("NumericPolicyCheck passed: "+checks);
    }
}
