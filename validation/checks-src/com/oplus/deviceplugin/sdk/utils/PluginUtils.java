package com.oplus.deviceplugin.sdk.utils;
import android.content.res.Resources;
import com.oplus.deviceplugin.sdk.entity.SmoothRoundCorner;
public class PluginUtils {
    public static float radius=56f,weight=1f;
    public static boolean fail;
    public static SmoothRoundCorner getSeparateSmoothCorner(Resources resources,int shape){if(fail)throw new IllegalStateException("native resource");return new SmoothRoundCorner(radius,weight);}
}
