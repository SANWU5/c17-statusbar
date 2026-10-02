package com.oplus.systemui.plugins.qs.customize.view.tile;
import android.content.Context;
import android.content.res.Resources;
import android.view.View;
public final class OplusQSIconView extends View {
    public float ratio = 1f;
    public int width = 240, height = 240;
    private final Resources resources = new Resources() {
        @Override public int getIdentifier(String name, String type, String pkg) {
            return name.equals("std_1x1_entry_icon_size") ? 11 : name.equals("std_1x1_switch_icon_size") ? 12 : 0;
        }
    };
    public OplusQSIconView() { super(new Context()); resources.getDisplayMetrics().density = 2f; }
    public float getIconScaleRatio() { return ratio; }
    @Override public Resources getResources() { return resources; }
    @Override public int getMeasuredWidth() { return width; }
    @Override public int getMeasuredHeight() { return height; }
}
