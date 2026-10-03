package com.oplus.posteffect.agsl.effects;

import android.graphics.RuntimeShader;

/** Exact OEM effect ABI; array positions come from GradientStrokeLineParamsKt in device DEX. */
public final class GradientStrokeEffect {
    public final float[] values = new float[16];
    public boolean enabled = true;
    public int uploads;
    public boolean isEnabled() { return enabled; }
    public String getEffectName() { return "gradientStroke"; }
    public int getOrder() { return 200; }
    public Object getStructuralKey() { return 0; }
    public void pushUniforms(RuntimeShader shader) { uploads++; shader.setFloatUniform("u_edgeArray", values); }
}
