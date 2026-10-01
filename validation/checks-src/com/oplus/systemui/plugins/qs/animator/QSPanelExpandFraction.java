package com.oplus.systemui.plugins.qs.animator;
import com.coui.appcompat.animation.dynamicanimation.COUISpringAnimation;
/** Native DEX signature fixture. Native skipToEnd does not update the manager's cached target. */
public final class QSPanelExpandFraction {
    public COUISpringAnimation animation = new COUISpringAnimation();
    private COUISpringAnimation getFractionAnimation() { return animation; }
    public void skipToEnd(boolean expanded) {
        animation.mPendingPosition = Float.MAX_VALUE;
        animation.getSpring().finalPosition = expanded ? 1f : 0f;
    }
}
