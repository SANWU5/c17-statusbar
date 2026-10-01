// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.*;
import java.security.MessageDigest;
import java.util.function.BooleanSupplier;

/** Imported pixels live only in app-private DE storage; no persistent document permission. */
public final class NotificationIconRepository {
    public static final String URI = "content://" + StatusBarSettings.AUTHORITY + "/notification-icon/current";
    private static final Object LOCK = new Object();
    private NotificationIconRepository() { }
    public static File file(Context context) {
        return new File(context.createDeviceProtectedStorageContext().getFilesDir(), "notification-icons/custom.png");
    }
    public static PreparedIcon prepare(Context context, Uri uri) throws Exception {
        byte[] data;
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("无法读取图片");
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 2 * 1024 * 1024) throw new IOException("图片最多 2 MB");
                bytes.write(buffer, 0, count);
            }
            data = bytes.toByteArray();
        }
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (!supported(options.outMimeType) || options.outWidth <= 0 || options.outHeight <= 0
                || options.outWidth > 16384 || options.outHeight > 16384) throw new IOException("请选择 PNG、JPEG 或 WebP 图片");
        options.inJustDecodeBounds = false; options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 256) options.inSampleSize *= 2;
        Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (decoded == null) throw new IOException("图片无法加载");
        File target = file(context), directory = target.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) { decoded.recycle(); throw new IOException("图片目录无法创建"); }
        File temporary = File.createTempFile("pending-", ".png", directory); boolean ready = false;
        try {
            int largest = Math.max(decoded.getWidth(), decoded.getHeight());
            Bitmap normalized = largest > 128 ? Bitmap.createScaledBitmap(decoded,
                    Math.max(1, decoded.getWidth() * 128 / largest), Math.max(1, decoded.getHeight() * 128 / largest), true) : decoded;
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!normalized.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("图片保存失败");
                output.getFD().sync();
            } finally { if (normalized != decoded) normalized.recycle(); }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(temporary)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            ready = true; return new PreparedIcon(temporary, target, hash.toString());
        } finally { decoded.recycle(); if (!ready) temporary.delete(); }
    }
    static boolean supported(String mime) { return "image/png".equals(mime) || "image/jpeg".equals(mime) || "image/webp".equals(mime); }
    public static final class PreparedIcon implements AutoCloseable {
        private final File temporary, target;
        public final String revision;
        private boolean closed;
        PreparedIcon(File temporary, File target, String revision) { this.temporary = temporary; this.target = target; this.revision = revision; }
        public String commit(BooleanSupplier canCommit, BooleanSupplier save, Runnable restore) throws Exception {
            synchronized (LOCK) {
                if (closed || !canCommit.getAsBoolean()) throw new IOException("权限状态已变化，图片未导入");
                File backup = null; boolean published = false, attempted = false, retained = false;
                try {
                    if (target.exists()) {
                        backup = File.createTempFile("previous-", ".png", target.getParentFile());
                        android.system.Os.remove(backup.getAbsolutePath());
                        android.system.Os.link(target.getAbsolutePath(), backup.getAbsolutePath());
                    }
                    android.system.Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath()); published = true;
                    if (!canCommit.getAsBoolean()) throw new IOException("权限状态已变化，图片未导入");
                    attempted = true;
                    if (!save.getAsBoolean() || !canCommit.getAsBoolean()) throw new IOException("图片配置保存失败");
                    return revision;
                } catch (Exception failure) {
                    if (published) try {
                        retained = backup != null;
                        if (backup != null) android.system.Os.rename(backup.getAbsolutePath(), target.getAbsolutePath());
                        else android.system.Os.remove(target.getAbsolutePath());
                        retained = false;
                    } catch (Exception rollback) { failure.addSuppressed(rollback); }
                    if (attempted) try { restore.run(); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                    throw failure;
                } finally { if (backup != null && !retained) backup.delete(); close(); }
            }
        }
        @Override public void close() { synchronized (LOCK) { closed = true; temporary.delete(); } }
    }
}
