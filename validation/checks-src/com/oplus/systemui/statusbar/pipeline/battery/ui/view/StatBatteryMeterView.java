package com.oplus.systemui.statusbar.pipeline.battery.ui.view;

import android.content.Context;
import android.graphics.drawable.Drawable;

public final class StatBatteryMeterView extends android.view.ViewGroup {
    public Drawable style;
    public Object charge;
    public StatBatteryMeterView(){super(new Context());}
    public Drawable getBatteryStyleDrawable(){return style;}
    public Object getBatteryCharge(){return charge;}
}
