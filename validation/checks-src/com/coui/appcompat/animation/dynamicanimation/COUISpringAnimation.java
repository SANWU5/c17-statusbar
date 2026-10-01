package com.coui.appcompat.animation.dynamicanimation;
/** Native DEX signature fixture; MAX_VALUE means no queued target. */
public final class COUISpringAnimation {
    public float mPendingPosition = Float.MAX_VALUE;
    public COUISpringForce force = new COUISpringForce();
    public COUISpringForce getSpring() { return force; }
}
