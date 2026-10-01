package com.oplus.systemui.qs.base.res.util;
import android.content.Context;
import com.oplusos.systemui.common.outline.CornerOutlineProvider;
public final class QSConstant {
    public static int calls;
    public static float lastPixels;
    public static Float weight=0.6f;
    public static float mappedScale=1f;
    public static boolean fail,empty,invalidRadius;
    public static CornerOutlineProvider getSmoothRoundRectOutlineProvider(Context context,float pixels){
        calls++;lastPixels=pixels;
        if(fail)throw new IllegalStateException("factory");
        return empty?null:new CornerOutlineProvider(invalidRadius?Float.NaN:pixels*mappedScale,weight);
    }
    public static void reset(){calls=0;lastPixels=0;weight=0.6f;mappedScale=1f;fail=false;empty=false;invalidRadius=false;}
}
