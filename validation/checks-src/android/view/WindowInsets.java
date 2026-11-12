package android.view;
/** Explicit per-view IME/inset facts for the owned QS page boundary checks. */
public final class WindowInsets {
    public boolean imeVisibleForCheck;
    public int bottomForCheck;
    public int topForCheck;
    public DisplayCutout cutoutForCheck;
    public boolean isVisible(int type) { return (type & Type.ime()) != 0 && imeVisibleForCheck; }
    public int getSystemWindowInsetBottom() { return bottomForCheck; }
    public int getSystemWindowInsetTop() { return topForCheck; }
    public int getSystemWindowInsetLeft() { return 0; }
    public int getSystemWindowInsetRight() { return 0; }
    public int getStableInsetBottom() { return bottomForCheck; }
    public DisplayCutout getDisplayCutout() { return cutoutForCheck; }
    public static final class Type { public static int ime() { return 8; } }
}
