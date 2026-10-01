package com.oplus.deviceplugin.sdk.ui.view.separatecardview;
import android.content.Context;
import android.view.View;
import com.oplus.deviceplugin.sdk.ui.view.drawable.SmoothViewOutlineProvider;
public class d extends View {
    public final SmoothViewOutlineProvider outline=new SmoothViewOutlineProvider();
    public com.oplus.systemui.plugins.drawable.PluginDrawable blurDrawable;
    public d(){super(new Context());}
    public int getShapeType(){return 1;}
    public SmoothViewOutlineProvider getSmoothViewOutlineProvider(){return outline;}
    public com.oplus.systemui.plugins.drawable.PluginDrawable getBlurDrawable(){return blurDrawable;}
    public void setBlurDrawable(com.oplus.systemui.plugins.drawable.PluginDrawable value){blurDrawable=value;}
}
