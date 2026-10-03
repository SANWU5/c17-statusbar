package com.oplus.posteffect.agsl.effects;

import android.graphics.RuntimeShader;

/** Actual C17 ABI: the three additive inner-gloss alphas are native array entries 3/4/5. */
public final class InnerShadowEffect {
    public final float[] values = new float[16];
    // Existing QS source-cache fixture fields remain available to its independent checks.
    public String name = "testInnerShadow";
    public int order = 3;
    public Object structuralKey = Boolean.TRUE;
    public float opticalValue = 17f;
    public boolean failKey, nativeArrays;
    public boolean enabled = true;
    public int uploads;
    public boolean isEnabled() { return enabled; }
    public String getEffectName() { return name; }
    public int getOrder() { return order; }
    public Object getStructuralKey() { if (failKey) throw new IllegalStateException("unknown structural API"); return structuralKey; }
    public void pushUniforms(RuntimeShader shader) { uploads++; if (nativeArrays) shader.setFloatUniform("u_shadowArray", values); else shader.setFloatUniform(name, opticalValue); }
}
