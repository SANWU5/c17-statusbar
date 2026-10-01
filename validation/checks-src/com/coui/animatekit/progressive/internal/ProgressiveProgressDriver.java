package com.coui.animatekit.progressive.internal;
public final class ProgressiveProgressDriver {
    public float logicalOffset;
    public float getLogicalOffset() { return logicalOffset; }
    public void onAnimatedValue(float value) { logicalOffset = value; }
}
