package com.oplus.systemui.notification.material;
/** Actual ColorOS ClipSpec constructor/fields; no replacement material is simulated. */
public final class NotificationSpotLightDelegate {
    public static final class ClipSpec {
        public final int left,top,right,bottom;
        public final float topCornerRadius,bottomCornerRadius;
        public final boolean useSmoothRadius;
        public ClipSpec(int left,int top,int right,int bottom,float topRadius,float bottomRadius,boolean smooth){
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
            topCornerRadius=topRadius;bottomCornerRadius=bottomRadius;useSmoothRadius=smooth;
        }
    }
}
