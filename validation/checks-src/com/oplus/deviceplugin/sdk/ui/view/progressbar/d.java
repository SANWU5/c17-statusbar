package com.oplus.deviceplugin.sdk.ui.view.progressbar;
/** Actual drawable hide-alpha callback; it writes only the device glyph. */
public final class d {
    private final BatteryCircularProgressLayout a;
    public d(BatteryCircularProgressLayout owner){a=owner;}
    public Object invoke(Object alpha){a.f.setAlpha(((Number)alpha).floatValue());return null;}
}
