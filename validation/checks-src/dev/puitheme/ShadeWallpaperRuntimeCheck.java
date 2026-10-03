package dev.puitheme;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** Suspended development must remain inert even when every saved switch is enabled. */
public final class ShadeWallpaperRuntimeCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual))
            throw new AssertionError("Wallpaper retirement " + checks + ": " + expected + " != " + actual);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method result = type.getDeclaredMethod(name, parameters); result.setAccessible(true); return result;
    }
    private static Bundle enabledSnapshot(int schema) {
        Bundle result = new Bundle();
        result.putInt("_schema", schema); result.putBoolean(ShadeWallpaperSettings.MASTER, true);
        result.putBoolean("tiles_enabled", true);
        for (String scene : ShadeWallpaperSettings.SCENES) {
            result.putBoolean(ShadeWallpaperSettings.enabledKey(scene), true);
            result.putString(ShadeWallpaperSettings.revisionKey(scene), "a".repeat(64));
            result.putFloat(ShadeWallpaperSettings.brightnessKey(scene), 174f);
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        Context systemUi = new Context() {
            @Override public String getPackageName() { return "com.android.systemui"; }
        };
        List<Runnable> work = new ArrayList<>(), completed = new ArrayList<>();
        int[] reads = {0};
        ShadeWallpaper wallpaper = new ShadeWallpaper(work::add, completed::add, (context, scene, revision) -> {
            reads[0]++; throw new AssertionError("Suspended wallpaper decoded an image");
        });
        View root = new View(systemUi);
        for (int schema : new int[]{1, 2, 3, 4}) {
            Bundle saved = enabledSnapshot(schema);
            for (String mode : new String[]{"classic", "separate", "unknown", null})
                for (int orientation : new int[]{Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE}) {
                    root.getResources().getConfiguration().orientation = orientation;
                    wallpaper.configure(systemUi, saved);
                    wallpaper.configure(systemUi, new Bundle(saved));
                    wallpaper.onPanelChanged(root, mode, 0, 1f, false);
                    Canvas canvas = new Canvas();
                    for (int i = 0; i < 30; i++) {
                        wallpaper.onHorizontalProgress(i / 29f);
                        wallpaper.onPanelChanged(root, mode, 0, .7f, false);
                        wallpaper.onLayoutChanged(root); wallpaper.draw(root, canvas);
                    }
                    wallpaper.onDetached(root); wallpaper.reset();
                    equal(0, work.size()); equal(0, completed.size()); equal(0, reads[0]);
                    equal(0, canvas.bitmapDraws); equal(0, canvas.layers);
                    equal(null, field(ShadeWallpaper.class, "context").get(wallpaper));
                    equal(null, field(ShadeWallpaper.class, "frame").get(wallpaper));
                    equal(null, field(ShadeWallpaper.class, "pending").get(wallpaper));
                    equal(null, ((WeakReference<?>) field(ShadeWallpaper.class, "root").get(wallpaper)).get());
                    equal(0f, field(ShadeWallpaper.class, "controlWeight").get(wallpaper));
                    equal(true, saved.getBoolean(ShadeWallpaperSettings.MASTER, false));
                    for (String scene : ShadeWallpaperSettings.SCENES) {
                        equal(true, saved.getBoolean(ShadeWallpaperSettings.enabledKey(scene), false));
                        equal("a".repeat(64), saved.getString(ShadeWallpaperSettings.revisionKey(scene)));
                        equal(174f, saved.get(ShadeWallpaperSettings.brightnessKey(scene)));
                    }
                }
        }
        wallpaper.configure(null, enabledSnapshot(3));
        wallpaper.configure(new Context(), enabledSnapshot(3));
        equal(0, work.size()); equal(0, completed.size());
        equal(0, root.invalidations); equal(0, root.layoutRequests);
        staleFrameAndLoad(wallpaper, systemUi, root, work, completed, reads);
        ShadeWallpaper normal = new ShadeWallpaper();
        Executor executor = (Executor) field(ShadeWallpaper.class, "worker").get(normal);
        equal(false, executor instanceof ThreadPoolExecutor);
        int[] executed = {0}; executor.execute(() -> executed[0]++);
        normal.configure(systemUi, enabledSnapshot(3)); normal.reset();
        equal(0, executed[0]);
        System.out.println("Shade wallpaper suspended runtime checks passed: " + checks);
    }
    private static void staleFrameAndLoad(ShadeWallpaper wallpaper, Context systemUi, View root,
            List<Runnable> work, List<Runnable> completed, int[] reads) throws Exception {
        // A previously queued callback or stale frame must also fail closed at the final entry points.
        Bitmap image = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888);
        Class<?> frameType = Class.forName("dev.puitheme.ShadeWallpaper$Frame");
        Constructor<?> frameCtor = frameType.getDeclaredConstructor(Bitmap.class, Bitmap.class, float.class, float.class);
        frameCtor.setAccessible(true);
        field(ShadeWallpaper.class, "frame").set(wallpaper, frameCtor.newInstance(image, image, 100f, 100f));
        field(ShadeWallpaper.class, "root").set(wallpaper, new WeakReference<>(root));
        field(ShadeWallpaper.class, "mode").set(wallpaper, "separate");
        field(ShadeWallpaper.class, "visible").setBoolean(wallpaper, true);
        field(ShadeWallpaper.class, "context").set(wallpaper, systemUi);
        Object config = field(ShadeWallpaper.class, "config").get(wallpaper);
        equal(true, field(config.getClass(), "disabled").get(config));
        field(config.getClass(), "disabled").setBoolean(config, false);
        Canvas canvas = new Canvas(); wallpaper.draw(root, canvas);
        equal(0, canvas.bitmapDraws); equal(0, canvas.layers);
        method(ShadeWallpaper.class, "requestLoad").invoke(wallpaper);
        equal(0, work.size()); equal(0, completed.size());
        Class<?> sceneType = Class.forName("dev.puitheme.ShadeWallpaper$SceneConfig");
        Constructor<?> sceneCtor = sceneType.getDeclaredConstructor(String.class, boolean.class, String.class, float.class);
        sceneCtor.setAccessible(true);
        Object scene = sceneCtor.newInstance(ShadeWallpaperSettings.NOTIFICATION_PORTRAIT, true, "a".repeat(64), 100f);
        equal(null, method(ShadeWallpaper.class, "load", Context.class, sceneType).invoke(wallpaper, systemUi, scene));
        equal(0, reads[0]);
        try {
            method(ShadeWallpaper.class, "readNormalized", Context.class, String.class, String.class)
                    .invoke(null, systemUi, ShadeWallpaperSettings.NOTIFICATION_PORTRAIT, "a".repeat(64));
            throw new AssertionError("Suspended PNG reader opened an image");
        } catch (InvocationTargetException guarded) {
            equal(true, guarded.getCause() instanceof IOException);
            equal(ShadeWallpaperSettings.UNAVAILABLE_REASON, guarded.getCause().getMessage());
        }
        @SuppressWarnings("unchecked") Map<String, Bitmap> cache = (Map<String, Bitmap>) field(ShadeWallpaper.class, "cache").get(wallpaper);
        cache.put("stale", image); field(ShadeWallpaper.class, "cacheBytes").setInt(wallpaper, image.getAllocationByteCount());
        wallpaper.configure(systemUi, enabledSnapshot(3));
        equal(null, field(ShadeWallpaper.class, "frame").get(wallpaper)); equal(0, cache.size());
        equal(0, field(ShadeWallpaper.class, "cacheBytes").get(wallpaper));
        equal(false, image.isRecycled()); equal(0, work.size()); equal(0, completed.size());
    }
}
