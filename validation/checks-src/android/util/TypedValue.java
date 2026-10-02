package android.util;
/** Android's unit selection; nonlinear OEM font scaling remains a device-level check. */
public class TypedValue {
    public static final int COMPLEX_UNIT_PX=0,COMPLEX_UNIT_DIP=1,COMPLEX_UNIT_SP=2;
    public static float applyDimension(int unit,float value,DisplayMetrics metrics) {
        return value*(unit==COMPLEX_UNIT_SP?metrics.scaledDensity:unit==COMPLEX_UNIT_DIP?metrics.density:1f);
    }
}
