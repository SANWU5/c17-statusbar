package android.graphics.drawable;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
public abstract class Drawable {
    public interface Callback {
        void invalidateDrawable(Drawable drawable);
        void scheduleDrawable(Drawable drawable, Runnable task, long when);
        void unscheduleDrawable(Drawable drawable, Runnable task);
    }
    private Callback callback;
    private final Rect bounds = new Rect();
    public abstract void draw(Canvas canvas);
    public abstract void setAlpha(int alpha);
    public abstract void setColorFilter(ColorFilter filter);
    public abstract int getOpacity();
    public int getIntrinsicWidth() { return 72; }
    public int getIntrinsicHeight() { return 56; }
    public void setCallback(Callback callback) { this.callback = callback; }
    public Callback getCallback() { return callback; }
    public Rect getBounds() { return bounds; }
    public void setBounds(int l, int t, int r, int b) { bounds.left=l; bounds.top=t; bounds.right=r; bounds.bottom=b; }
    public void invalidateSelf() { if (callback != null) callback.invalidateDrawable(this); }
}
