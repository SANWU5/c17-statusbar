package android.text;

/** Stores measured layout metrics independently from subsequent direct Paint writes. */
public class Layout {
    private final int width, ellipsisCount;
    public final float measuredTextSize, glyphWidth;
    public Layout(int width, int ellipsisCount, float measuredTextSize, float glyphWidth) {
        this.width = width; this.ellipsisCount = ellipsisCount;
        this.measuredTextSize = measuredTextSize; this.glyphWidth = glyphWidth;
    }
    public int getWidth() { return width; }
    public int getEllipsizedWidth() { return width; }
    public int getLineCount() { return 1; }
    public int getEllipsisCount(int line) { return ellipsisCount; }
}
