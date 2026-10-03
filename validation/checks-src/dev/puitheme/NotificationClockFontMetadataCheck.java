package dev.puitheme;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import com.oplus.keyguard.clock.digital.ui.view.ClockTimeView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/** Replays the native configured/applied split rather than measuring an already-small font. */
public final class NotificationClockFontMetadataCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Clock font metadata "+checks+": "+expected+" != "+actual);
    }
    private static class Digit extends View {
        public String fontFamilyName="fonts/tunable/ConstructorDefault.ttf";
        public int fontHeight=50, fontWeight=500, configFontHeight=50;
        Digit(Context context){super(context);}
    }
    private static class DerivedClock extends ClockTimeView { DerivedClock(Context context){super(context);} }
    private static Object metadata(View template)throws Exception {
        Method method=NotificationClockFont.class.getDeclaredMethod("metadata",View.class);
        method.setAccessible(true);return method.invoke(null,template);
    }
    private static Object value(Object metadata,String name)throws Exception {
        Field field=metadata.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(metadata);
    }
    private static void assertTuple(View view,String family,int height,int weight)throws Exception {
        Object actual=metadata(view);equal(family,value(actual,"family"));equal(height,value(actual,"height"));equal(weight,value(actual,"weight"));
    }
    public static void main(String[] args)throws Exception {
        Context context=new Context();Digit digit=new Digit(context);DerivedClock parent=new DerivedClock(context);
        FrameLayout wrapper=new FrameLayout(context);wrapper.addView(digit);parent.addView(wrapper);
        // A digit's 50 and the parent's instantaneous 1..70 must never replace configured 70.
        for(int applied=1;applied<=70;applied++){
            parent.appliedFontHeight=applied;parent.appliedFontWeight=1000-applied;
            assertTuple(digit,"fonts/tunable/NativeConfigured.ttf",70,703);
        }
        parent.configFontHeight=96;parent.configFontWeight=824;parent.configFamilyName="fonts/tunable/ChangedByUser.ttf";
        assertTuple(digit,"fonts/tunable/ChangedByUser.ttf",96,824);
        parent.configFamilyName="";assertTuple(digit,"fonts/tunable/ConstructorDefault.ttf",50,500);
        parent.configFamilyName="fonts/tunable/NativeConfigured.ttf";parent.configFontHeight=0;
        assertTuple(digit,"fonts/tunable/ConstructorDefault.ttf",50,500);
        parent.configFontHeight=70;parent.configFontWeight=0;
        assertTuple(digit,"fonts/tunable/ConstructorDefault.ttf",50,500);
        wrapper.removeView(digit);assertTuple(digit,"fonts/tunable/ConstructorDefault.ttf",50,500);
        assertTuple(null,"",70,703);
        System.out.println("NotificationClockFontMetadataCheck passed "+checks+" checks");
    }
}
