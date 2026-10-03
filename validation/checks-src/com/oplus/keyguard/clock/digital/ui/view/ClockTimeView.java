package com.oplus.keyguard.clock.digital.ui.view;

import android.content.Context;
import android.widget.FrameLayout;

/** Native metadata fixture: applied height changes during scroll, configuration does not. */
public class ClockTimeView extends FrameLayout {
    public String configFamilyName = "fonts/tunable/NativeConfigured.ttf";
    public int configFontHeight = 70, configFontWeight = 703;
    public int appliedFontHeight = 24, appliedFontWeight = 703;
    public ClockTimeView(Context context) { super(context); }
    public int getFontHeight() { return appliedFontHeight; }
    public int getFontWeight() { return appliedFontWeight; }
}
