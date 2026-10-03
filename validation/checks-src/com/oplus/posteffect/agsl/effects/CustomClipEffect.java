package com.oplus.posteffect.agsl.effects;

import android.graphics.RuntimeShader;

public final class CustomClipEffect {
    public boolean enabled = true;
    public int structuralKey = 1, uploads;
    public boolean isEnabled() { return enabled; }
    public String getEffectName() { return "customClip"; }
    public int getOrder() { return 900; }
    public Object getStructuralKey() { return structuralKey; }
    public void pushUniforms(RuntimeShader shader) { uploads++; shader.setFloatUniform("u_nativeCustomClip", structuralKey); }
}
