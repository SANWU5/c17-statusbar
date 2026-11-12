package dev.puitheme;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Rect;
import android.widget.TextView;
import java.lang.reflect.*;

/** Exercises the actual ClockText entry used by measure, draw, and material-mask alignment. */
public final class BigClockInkCacheCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        Class<?> type=Class.forName("dev.puitheme.NotificationBigClock$ClockText");
        Constructor<?> ctor=type.getDeclaredConstructor(NotificationBigClock.class,Context.class);ctor.setAccessible(true);
        TextView view=(TextView)ctor.newInstance(new NotificationBigClock(),new Context());
        Method measure=type.getDeclaredMethod("measureInk");measure.setAccessible(true);
        Field bounds=type.getDeclaredField("ink");bounds.setAccessible(true);
        view.setText("10:28");view.getPaint().setTextSize(120);measure.invoke(view);
        int before=Paint.textBoundsCallsForCheck;
        for(int frame=0;frame<10000;frame++){measure.invoke(view);measure.invoke(view);measure.invoke(view);}
        check(Paint.textBoundsCallsForCheck==before,"stable measure/draw/mask paths must not reshape glyph bounds");
        view.setText("10:29");measure.invoke(view);check(Paint.textBoundsCallsForCheck==++before,"changed digits invalidate");
        view.getPaint().setTextSize(140);measure.invoke(view);check(Paint.textBoundsCallsForCheck==++before,"font size invalidates");
        check(((Rect)bounds.get(view)).height()==140,"new ink bounds reach the actual view");
        view.getPaint().setTextScaleX(.8f);measure.invoke(view);check(Paint.textBoundsCallsForCheck==++before,"width scale invalidates");
        view.getPaint().setFontFeatureSettings("kern");measure.invoke(view);check(Paint.textBoundsCallsForCheck==++before,"feature changes invalidate");
        view.getPaint().setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,650,false));
        measure.invoke(view);check(Paint.textBoundsCallsForCheck==++before,"family/weight invalidates");
        view.getPaint().setColor(0xff123456);view.getPaint().setAlpha(70);measure.invoke(view);
        check(Paint.textBoundsCallsForCheck==before,"foreground color does not invalidate text measurement");
        view.setText("");measure.invoke(view);check(((Rect)bounds.get(view)).isEmpty(),"empty text clears old bounds");
        view.setText("11:30");measure.invoke(view);check(Paint.textBoundsCallsForCheck==++before,"text after empty state recomputes");
        System.out.println("BigClockInkCacheCheck: "+checks+" checks; 10000 frames, stable bounds calls=0");
    }
}
