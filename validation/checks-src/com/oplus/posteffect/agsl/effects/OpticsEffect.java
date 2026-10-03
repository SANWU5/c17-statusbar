package com.oplus.posteffect.agsl.effects;

import android.graphics.RuntimeShader;

/** Exact OEM effect ABI; only index 3 is alpha in OpticsParamsKt's twelve-value array. */
public final class OpticsEffect {
    public final float[] values = new float[12];
    public boolean enabled = true;
    public int uploads;
    public boolean isEnabled() { return enabled; }
    public String getEffectName() { return "optics"; }
    public int getOrder() { return 400; }
    public Object getStructuralKey() { return 0; }
    public void pushUniforms(RuntimeShader shader) { uploads++; shader.setFloatUniform("u_opticsArray", values); }
}
