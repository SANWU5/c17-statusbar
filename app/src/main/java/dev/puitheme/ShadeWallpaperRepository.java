// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** SAF input is normalized off the UI thread; SystemUI sees only immutable private PNGs. */
public final class ShadeWallpaperRepository {
    public static final int MAX_SOURCE_BYTES = 24 * 1024 * 1024;
    public static final int MAX_EDGE = 2048;
    public static final int MAX_PIXELS = 2 * 1024 * 1024;
    public static final int MAX_ASSET_BYTES = 12 * 1024 * 1024;
    private static final Object LOCK = new Object();
    private static final Set<File> PENDING = new HashSet<>();
    private ShadeWallpaperRepository() { }

    private static File directory(Context context, String scene) {
        if (!ShadeWallpaperSettings.validScene(scene)) throw new IllegalArgumentException("Unknown wallpaper scene");
        return new File(context.createDeviceProtectedStorageContext().getFilesDir(), "shade-wallpapers/" + scene);
    }
    public static Uri uri(String scene, String revision) {
        if (!ShadeWallpaperSettings.validScene(scene) || !ShadeWallpaperSettings.validRevision(revision))
            throw new IllegalArgumentException("Invalid wallpaper asset");
        return Uri.parse("content://" + StatusBarSettings.AUTHORITY + "/shade-wallpaper/" + scene + "/" + revision + ".png");
    }
    public static File providerFile(Context context, Uri uri) throws IOException {
        if (uri == null || !"content".equals(uri.getScheme()) || !StatusBarSettings.AUTHORITY.equals(uri.getAuthority())
                || uri.getQuery() != null || uri.getFragment() != null) throw new IOException("Invalid wallpaper URI");
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 3 || !"shade-wallpaper".equals(segments.get(0))) throw new IOException("Unknown wallpaper asset");
        String scene = segments.get(1), filename = segments.get(2);
        if (!ShadeWallpaperSettings.validScene(scene) || !filename.endsWith(".png")
                || !ShadeWallpaperSettings.validRevision(filename.substring(0, filename.length() - 4)))
            throw new IOException("Invalid wallpaper asset");
        File parent = directory(context, scene).getCanonicalFile();
        File asset = new File(parent, filename).getCanonicalFile();
        if (!parent.equals(asset.getParentFile())) throw new IOException("Invalid wallpaper path");
        return asset;
    }
    public static PreparedWallpaper prepare(Context context, Uri source, String scene) throws Exception {
        if (!ShadeWallpaperSettings.validScene(scene)) throw new IOException("无效的下拉场景");
        byte[] data;
        try (InputStream input = context.getContentResolver().openInputStream(source);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("无法读取图片");
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_SOURCE_BYTES) throw new IOException("图片最多 24 MB");
                bytes.write(buffer, 0, count);
            }
            data = bytes.toByteArray();
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (!NotificationIconRepository.supported(bounds.outMimeType) || bounds.outWidth <= 0 || bounds.outHeight <= 0
                || bounds.outWidth > 32768 || bounds.outHeight > 32768) throw new IOException("请选择 PNG、JPEG 或 WebP 图片");
        Bitmap decoded = null, normalized = null;
        File temporary = null; boolean ready = false;
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(data)), (decoder, info, imageSource) -> {
                    int[] size = normalizedSize(info.getSize().getWidth(), info.getSize().getHeight());
                    decoder.setTargetSize(size[0], size[1]);
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
                });
            } else {
                bounds.inJustDecodeBounds = false; bounds.inSampleSize = 1; bounds.inPreferredConfig = Bitmap.Config.ARGB_8888;
                while (Math.max(bounds.outWidth, bounds.outHeight) / bounds.inSampleSize > MAX_EDGE
                        || (long) Math.max(1, bounds.outWidth / bounds.inSampleSize) * Math.max(1, bounds.outHeight / bounds.inSampleSize) > MAX_PIXELS)
                    bounds.inSampleSize *= 2;
                decoded = BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            }
            if (decoded == null) throw new IOException("图片无法加载");
            int[] size = normalizedSize(decoded.getWidth(), decoded.getHeight());
            normalized = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(normalized);
            canvas.drawBitmap(decoded, null, new android.graphics.Rect(0, 0, size[0], size[1]), new Paint(Paint.FILTER_BITMAP_FLAG));
            // Fully transparent images are rejected; partially transparent pixels preserve the native background.
            if (!hasVisiblePixels(normalized)) throw new IOException("图片完全透明，请选择可见的图片");
            synchronized (LOCK) {
                File parent = directory(context, scene);
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("壁纸目录无法创建");
                File[] abandoned = parent.listFiles(file -> file.isFile() && file.getName().startsWith("pending-") && file.getName().endsWith(".png"));
                if (abandoned != null) for (File file : abandoned) if (!PENDING.contains(file)) file.delete();
                temporary = File.createTempFile("pending-", ".png", parent);
                PENDING.add(temporary);
            }
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!normalized.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("图片保存失败");
                output.getFD().sync();
            }
            if (temporary.length() > MAX_ASSET_BYTES) throw new IOException("归一化后的图片过大");
            String revision = digest(temporary);
            File target = new File(directory(context, scene), revision + ".png");
            ready = true;
            return new PreparedWallpaper(temporary, target, scene, revision);
        } finally {
            if (normalized != null) normalized.recycle();
            if (decoded != null) decoded.recycle();
            if (!ready && temporary != null) synchronized (LOCK) { PENDING.remove(temporary); temporary.delete(); }
        }
    }
    static int[] normalizedSize(int width, int height) {
        double scale = Math.min(1d, Math.min(MAX_EDGE / (double) Math.max(width, height), Math.sqrt(MAX_PIXELS / ((double) width * height))));
        return new int[]{Math.max(1, (int) Math.floor(width * scale)), Math.max(1, (int) Math.floor(height * scale))};
    }
    private static boolean hasVisiblePixels(Bitmap bitmap) {
        if (!bitmap.hasAlpha()) return true;
        int[] row = new int[bitmap.getWidth()];
        for (int y = 0; y < bitmap.getHeight(); y++) {
            bitmap.getPixels(row, 0, row.length, 0, y, row.length, 1);
            for (int pixel : row) if ((pixel >>> 24) != 0) return true;
        }
        return false;
    }
    private static String digest(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        char[] hex = "0123456789abcdef".toCharArray(); StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) { result.append(hex[(value >>> 4) & 15]); result.append(hex[value & 15]); }
        return result.toString();
    }
    /** Call on the import worker after saving. One current and one previous revision bound disk use. */
    public static void prune(Context context, String scene, String currentRevision) {
        if (!ShadeWallpaperSettings.validScene(scene) || !ShadeWallpaperSettings.validRevision(currentRevision)) return;
        synchronized (LOCK) {
            pruneFiles(directory(context, scene), currentRevision);
        }
    }
    static void pruneFiles(File parent, String currentRevision) {
        // The published revision is already committed. Cleanup failure must not turn it into a failed import.
        try {
            File[] files = parent.listFiles(file -> file.isFile()
                    && file.getName().endsWith(".png") && ShadeWallpaperSettings.validRevision(file.getName().substring(0, file.getName().length() - 4)));
            if (files == null) return;
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            boolean retainedPrevious = false;
            for (File file : files) {
                if (file.getName().equals(currentRevision + ".png")) continue;
                if (!retainedPrevious) { retainedPrevious = true; continue; }
                try { file.delete(); } catch (RuntimeException ignored) { }
            }
        } catch (RuntimeException ignored) { }
    }
    /** The caller first clears the scene's enabled/revision keys, then invokes this on its worker. */
    public static void delete(Context context, String scene) throws IOException {
        synchronized (LOCK) {
            deleteFiles(directory(context, scene));
        }
    }
    static void deleteFiles(File parent) throws IOException {
        if (!parent.exists()) return;
        File[] files = parent.listFiles();
        if (files == null) throw new IOException("壁纸目录无法读取");
        IOException failure = null;
        for (File file : files) if (file.isFile() && file.getName().endsWith(".png")) {
            if (!file.delete() && file.exists()) {
                if (failure == null) failure = new IOException("壁纸删除失败，请重试");
            }
        }
        if (failure != null) throw failure;
    }
    /** App-owned configuration removal only; callers invalidate pending imports before clearing. */
    public static void clear(Context context) {
        // Configuration is already removed, so retained files cannot become active wallpapers.
        for (String scene : ShadeWallpaperSettings.SCENES) try { delete(context, scene); } catch (IOException | RuntimeException ignored) { }
    }
    public static final class PreparedWallpaper implements AutoCloseable {
        private final File temporary, target;
        public final String scene, revision;
        private boolean closed;
        PreparedWallpaper(File temporary, File target, String scene, String revision) {
            this.temporary = temporary; this.target = target; this.scene = scene; this.revision = revision;
        }
        /** Revision-addressed publishing cannot overwrite pixels being read by SystemUI. */
        public String commit(BooleanSupplier canCommit, BooleanSupplier save, Runnable restore) throws Exception {
            synchronized (LOCK) {
                if (closed || !canCommit.getAsBoolean()) throw new IOException("页面状态已变化，壁纸未导入");
                boolean published = false, attempted = false;
                try {
                    if (!target.isFile()) { android.system.Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath()); published = true; }
                    if (!canCommit.getAsBoolean()) throw new IOException("页面状态已变化，壁纸未导入");
                    attempted = true;
                    if (!save.getAsBoolean() || !canCommit.getAsBoolean()) throw new IOException("壁纸配置保存失败");
                    return revision;
                } catch (Exception failure) {
                    if (attempted) try { restore.run(); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                    if (published) target.delete();
                    throw failure;
                } finally { close(); }
            }
        }
        @Override public void close() { synchronized (LOCK) { closed = true; PENDING.remove(temporary); temporary.delete(); } }
    }
}
