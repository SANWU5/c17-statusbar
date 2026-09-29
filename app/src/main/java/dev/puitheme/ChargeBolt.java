package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

/** A rounded charging symbol with a genuine transparent halo in the native battery layer. */
public final class ChargeBolt {
    private final Path symbol = new Path();
    private final Paint erase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint foreground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float oldLeft = Float.NaN, oldTop, oldRight, oldBottom;
    private int oldBoundTop, oldBoundBottom;

    public ChargeBolt() {
        erase.setColor(0xff000000);
        erase.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        erase.setStyle(Paint.Style.FILL_AND_STROKE);
        erase.setStrokeJoin(Paint.Join.ROUND);
        erase.setStrokeCap(Paint.Cap.ROUND);
        foreground.setStyle(Paint.Style.FILL);
        foreground.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
    }

    private void geometry(RectF body, Rect bounds) {
        if (oldLeft == body.left && oldTop == body.top && oldRight == body.right && oldBottom == body.bottom
                && oldBoundTop == bounds.top && oldBoundBottom == bounds.bottom) return;
        oldLeft = body.left; oldTop = body.top; oldRight = body.right; oldBottom = body.bottom;
        oldBoundTop = bounds.top; oldBoundBottom = bounds.bottom;
        float centerY = body.centerY();
        float available = bounds.height() > 0
                ? 2f * Math.max(0, Math.min(centerY - bounds.top, bounds.bottom - centerY) - .5f)
                : body.height();
        float height = Math.min(body.height() * 1.22f, available);
        float width = Math.min(height * .55f, body.width() * .62f);
        float left = body.centerX() - width / 2f, top = centerY - height / 2f;
        symbol.reset();
        symbol.moveTo(left + width * .81f, top);
        symbol.lineTo(left + width * .56f, top + height * .416f);
        symbol.lineTo(left + width, top + height * .416f);
        symbol.lineTo(left + width * .13f, top + height);
        symbol.lineTo(left + width * .40f, top + height * .57f);
        symbol.lineTo(left, top + height * .57f);
        symbol.close();
        CornerPathEffect corners = new CornerPathEffect(Math.max(.5f, body.height() * .045f));
        erase.setPathEffect(corners);
        foreground.setPathEffect(corners);
        erase.setStrokeWidth(2f * Math.max(.75f, body.height() * .065f));
    }

    /** Must be called before the native battery's saveLayer is restored. */
    public void draw(Canvas canvas, RectF body, Rect bounds, float opacity, int nativeTint) {
        if (opacity <= 0f || body.width() <= 0 || body.height() <= 0) return;
        geometry(body, bounds);
        float amount = Math.min(1f, opacity);
        // The cutout is independent of foreground tint alpha: a fully visible symbol has a clear halo.
        erase.setAlpha(Math.round(255f * amount));
        canvas.drawPath(symbol, erase);
        foreground.setColor(nativeTint);
        foreground.setAlpha(Math.round((nativeTint >>> 24) * amount));
        canvas.drawPath(symbol, foreground);
    }
}
