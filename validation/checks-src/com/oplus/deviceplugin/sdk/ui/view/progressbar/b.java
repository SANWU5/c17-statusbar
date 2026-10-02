package com.oplus.deviceplugin.sdk.ui.view.progressbar;
/** Actual drawable show-alpha callback; it writes only the device glyph. */
public final class b {
    private final BatteryCircularProgressLayout a;
    public b(BatteryCircularProgressLayout owner){a=owner;}
    public Object invoke(Object alpha){a.f.setAlpha(((Number)alpha).floatValue());return null;}
}
