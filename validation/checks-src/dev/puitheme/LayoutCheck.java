package dev.puitheme;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.content.res.Resources;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/** Exercises the compiled refresh methods with observable native-view state. */
public final class LayoutCheck {
    private static int failures, checks;
    private static Class<?> module;
    private static Field field(String name) throws Exception {
        Field field=module.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void equal(String name, int expected, int actual) {
        checks++;
        if (expected != actual) { failures++; System.out.println("FAIL " + name + ": expected=" + expected + " actual=" + actual); }
    }
    private static final class Icon extends Drawable {
        public void draw(Canvas canvas) { }
        public void setAlpha(int alpha) { }
        public void setColorFilter(ColorFilter filter) { }
        public int getOpacity() { return -3; }
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        module=Class.forName("dev.puitheme.StatusBarModule");
        field("context").set(null,null);
        field("composeStatusBar").setBoolean(null,true);
        View wifi=new View(null);
        wifi.setMinimumWidth(8);
        Constructor<?> slotCtor=Class.forName("dev.puitheme.StatusBarModule$Slot").getDeclaredConstructor(View.class);
        slotCtor.setAccessible(true);
        Object slot=slotCtor.newInstance(wifi);
        ((Map<Object,Object>)field("SLOTS").get(null)).put(wifi,slot);
        // The system changes its Wi-Fi width after module construction.
        wifi.setMinimumWidth(24);
        wifi.layoutRequests=0;
        module.getMethod("refreshLabel").invoke(null);
        equal("Compose refresh preserves native Wi-Fi width",24,wifi.getMinimumWidth());
        equal("Compose refresh does not remeasure legacy Wi-Fi",0,wifi.layoutRequests);

        Constructor<?> iconCtor=Class.forName("dev.puitheme.StatusBarModule$ScaledDrawable")
                .getDeclaredConstructor(Drawable.class,Resources.class,boolean.class);
        iconCtor.setAccessible(true);
        Drawable icon=(Drawable)iconCtor.newInstance(new Icon(),Resources.getSystem(),false);
        View image=new View(null), parent=new View(null);
        image.parent=parent;
        icon.setCallback(image);
        ((List<WeakReference<Drawable>>)field("LIVE_DRAWABLES").get(null)).add(new WeakReference<>(icon));
        module.getMethod("invalidateLiveDrawables").invoke(null);
        equal("Color redraw does not remeasure icon",0,image.layoutRequests);
        equal("Color redraw does not remeasure parent",0,parent.layoutRequests);
        equal("Color redraw still invalidates icon",1,image.invalidations);
        equal("Icon reserved width stays fixed",72,icon.getIntrinsicWidth());
        if (failures != 0) throw new AssertionError(failures + " of " + checks + " layout checks failed");
        System.out.println(checks + " layout regression checks passed");
    }
}
