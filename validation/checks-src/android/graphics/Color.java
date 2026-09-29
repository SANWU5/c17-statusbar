package android.graphics;
public final class Color {
    public static int alpha(int value) { return value >>> 24; }
    public static int red(int value) { return (value >>> 16) & 255; }
    public static int green(int value) { return (value >>> 8) & 255; }
    public static int blue(int value) { return value & 255; }
}
