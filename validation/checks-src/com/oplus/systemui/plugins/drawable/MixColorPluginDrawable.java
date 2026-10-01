package com.oplus.systemui.plugins.drawable;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;
/** Same-instance native MixColor path and separately applied visible blur. */
public class MixColorPluginDrawable extends PluginDrawable {
    private AutoBlurDrawable autoBlurDrawable=new AutoBlurDrawable();
    public boolean strokeConfigSideEffects;
    public AutoBlurDrawable blur(){return autoBlurDrawable;}
    public void replaceBlur(AutoBlurDrawable replacement){autoBlurDrawable=replacement;}
    @Override public void setCornerRadius(float radius,Float weight){
        // Simulate native stroke-template side effects before a partially failing setter.
        if(strokeConfigSideEffects) {
            autoBlurDrawable.viewBlurProxy.getBlurConfig().setCornerRadius(radius);
            autoBlurDrawable.viewBlurProxy.applyBlurConfig();
        }
        super.setCornerRadius(radius,weight);
    }
}
