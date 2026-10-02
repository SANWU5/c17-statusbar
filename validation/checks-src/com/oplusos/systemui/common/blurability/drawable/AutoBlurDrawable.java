package com.oplusos.systemui.common.blurability.drawable;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable;
public class AutoBlurDrawable extends Drawable {
    public final Proxy viewBlurProxy=new Proxy();
    public Drawable defaultDrawable;
    public static final class Proxy extends com.oplusos.systemui.common.blurability.ViewBlurProxy {public static int nativeRequests;public final PlatformBlurDrawable actual=new PlatformBlurDrawable();public Drawable getBlurDrawable(Drawable fallback){nativeRequests++;return actual;}}
    public Proxy getViewBlurProxy(){return viewBlurProxy;}
    public void draw(Canvas canvas){viewBlurProxy.actual.draw(canvas);}
    public void setAlpha(int value){}
    public void setColorFilter(ColorFilter value){}
    public int getOpacity(){return 0;}
}
