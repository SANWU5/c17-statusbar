package dev.puitheme;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.io.File;
import java.io.FileOutputStream;

/** Exercises the release renderer with the real Android software rasterizer. */
public final class ChargeBoltRenderCheck {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static Bitmap render(ChargeBolt bolt, RectF body, Rect bounds, int level, float opacity, int tint) {
        Bitmap result = Bitmap.createBitmap(bounds.right, bounds.bottom, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        int layer = canvas.saveLayer(new RectF(bounds), null);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x4d000000);
        canvas.drawRoundRect(body, body.height() * .24f, body.height() * .24f, paint);
        int fill = canvas.save();
        canvas.clipRect(body.left, body.top, body.left + body.width() * level / 100f, body.bottom);
        paint.setColor(0xff00bd13);
        canvas.drawRoundRect(body, body.height() * .24f, body.height() * .24f, paint);
        canvas.restoreToCount(fill);
        if (bolt != null) bolt.draw(canvas, body, bounds, opacity, tint);
        canvas.restoreToCount(layer);
        return result;
    }
    private static Bitmap background(int width, int height) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            result.setPixel(x, y, Color.rgb(35 + x * 50 / width, 48 + y * 80 / height, 146 + x * 80 / width));
        return result;
    }
    private static Bitmap composite(Bitmap battery, Bitmap background) {
        Bitmap result = background.copy(Bitmap.Config.ARGB_8888, true);
        new Canvas(result).drawBitmap(battery, 0f, 0f, null);
        return result;
    }
    private static void save(Bitmap bitmap, File directory, String name) throws Exception {
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output), "PNG encoding");
        }
    }
    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]);
        require(directory.isDirectory(), "output directory");
        ChargeBolt bolt = new ChargeBolt();
        Rect bounds = new Rect(0, 0, 116, 68);
        RectF body = new RectF(8, 8, 100, 60);
        int totalClear = 0;
        for (float scale : new float[] {.5f, 1f, 2f}) {
            Rect sizedBounds = new Rect(0, 0, Math.round(116 * scale), Math.round(68 * scale));
            for (boolean rtl : new boolean[] {false, true}) {
                RectF sizedBody = new RectF((rtl ? 16 : 8) * scale, 8 * scale,
                        (rtl ? 108 : 100) * scale, 60 * scale);
                for (int level : new int[] {0, 1, 5, 50, 86, 99, 100}) {
                    Bitmap nativeBattery = render(null, sizedBody, sizedBounds, level, 0f, 0);
                    Bitmap background = background(sizedBounds.width(), sizedBounds.height());
                    for (int tint : new int[] {0xccffffff, 0xe6000000, 0xffe51b25}) {
                        Bitmap full = render(bolt, sizedBody, sizedBounds, level, 1f, tint);
                        Bitmap partial = render(bolt, sizedBody, sizedBounds, level, .5f, tint);
                        Bitmap restored = render(bolt, sizedBody, sizedBounds, level, 0f, tint);
                        Bitmap scene = composite(full, background);
                        int cx = (int) sizedBody.centerX(), cy = (int) sizedBody.centerY();
                        require(Color.alpha(full.getPixel(cx, cy)) == Color.alpha(tint), "native tint alpha");
                        require(Color.alpha(partial.getPixel(cx, cy)) > 0, "transition visible");
                        require(full.getPixel((int) sizedBody.left + 2, cy) == nativeBattery.getPixel((int) sizedBody.left + 2, cy), "left native fill");
                        require(full.getPixel((int) sizedBody.right - 2, cy) == nativeBattery.getPixel((int) sizedBody.right - 2, cy), "right native fill");
                        int clear = 0, extended = 0;
                        for (int y = 0; y < sizedBounds.height(); y++) for (int x = 0; x < sizedBounds.width(); x++) {
                            require(restored.getPixel(x, y) == nativeBattery.getPixel(x, y), "zero phase restores exact native pixels");
                            if (Color.alpha(nativeBattery.getPixel(x, y)) > 0 && Color.alpha(full.getPixel(x, y)) == 0) {
                                clear++;
                                require(scene.getPixel(x, y) == background.getPixel(x, y), "clear halo exposes actual background");
                            }
                            if (y < sizedBody.top || y >= sizedBody.bottom) {
                                if (Color.alpha(full.getPixel(x, y)) > 0) extended++;
                            }
                        }
                        require(clear > 0, "transparent halo at every level, tint, scale and layout direction");
                        require(extended > 0, "bolt tips use existing native padding without body clipping");
                        totalClear += clear;
                        full.recycle();partial.recycle();restored.recycle();scene.recycle();
                    }
                    nativeBattery.recycle();background.recycle();
                }
            }
        }
        Bitmap light = render(bolt, body, bounds, 86, 1f, 0xe6000000);
        Bitmap dark = render(bolt, body, bounds, 86, 1f, 0xccffffff);
        Bitmap restored = render(null, body, bounds, 86, 0f, 0);
        save(light, directory, "bolt-light-transparent.png");
        save(dark, directory, "bolt-dark-transparent.png");
        save(restored, directory, "bolt-native-restored.png");
        Bitmap scene = composite(dark, background(bounds.width(), bounds.height()));
        save(scene, directory, "bolt-dark-on-background.png");
        System.out.println("ChargeBoltRenderCheck passed: " + checks + "; clear pixels: " + totalClear
                + "; 126 native Canvas cases, 3 scales, 2 layout directions, 7 levels, 3 native tints");
    }
}
