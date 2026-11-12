package com.oplusos.systemui.common.blurability.drawable;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable;
public class AutoBlurDrawable extends Drawable {
    public final Proxy viewBlurProxy=new Proxy();
    public Drawable defaultDrawable;
    public static final class Proxy extends com.oplusos.systemui.common.blurability.ViewBlurProxy {public static int nativeRequests;public final PlatformBlurDrawable actual=new PlatformBlurDrawable();public final Lazy platformBlurHelper$delegate=new Lazy(actual);public Drawable getBlurDrawable(Drawable fallback){nativeRequests++;return actual;}}
    public static final class Lazy {public boolean initialized=true;private final Helper helper;Lazy(PlatformBlurDrawable value){helper=new Helper(value);}public boolean isInitialized(){return initialized;}public Helper getValue(){if(!initialized)throw new AssertionError("must not initialize blur while drawing");return helper;}}
    public static final class Helper {public final PlatformBlurDrawable blurDrawable;Helper(PlatformBlurDrawable value){blurDrawable=value;}}
    public Proxy getViewBlurProxy(){return viewBlurProxy;}
    public void draw(Canvas canvas){viewBlurProxy.actual.draw(canvas);}
    public void setAlpha(int value){}
    public void setColorFilter(ColorFilter value){}
    public int getOpacity(){return 0;}
}
