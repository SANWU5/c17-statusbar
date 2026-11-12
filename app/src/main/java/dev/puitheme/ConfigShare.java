// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/** A configuration prepared for the system share sheet: a private snapshot of the JSON the receiver can read once, through a
 * provider that only answers an exact, temporary read grant. Nothing here touches preferences; old snapshots are removed. */
public final class ConfigShare {
    public static final String AUTHORITY = ModuleVariant.PACKAGE + ".config";
    public static final String FILE_NAME = "C17-settings.json";
    static final long SNAPSHOT_LIFETIME_MS = 10L * 60L * 1000L;
    private static final int RETAINED = 3;
    private static final Object LOCK = new Object();

    private ConfigShare() {}

    /** Write {@code json} to a new snapshot and return the content URI to hand out. */
    public static Uri prepare(Context context, String json) throws IOException {
        if (context == null || !ConfigTransfer.PACKAGE_NAME.equals(context.getPackageName())) throw new IOException("无法分享配置");
        byte[] bytes = json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > ConfigTransfer.MAX_BYTES) throw new IOException("配置为空或超过 256 KB");
        synchronized (LOCK) {
            File directory = new File(context.getCacheDir(), "config-shares");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建分享文件");
            long now = System.currentTimeMillis();
            File[] old = directory.listFiles((folder, name) -> name.matches("[0-9a-f]{32}\\.json"));
            if (old != null) {
                Arrays.sort(old, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
                for (int i = 0; i < old.length; i++)
                    if (now - old[i].lastModified() > SNAPSHOT_LIFETIME_MS || i < old.length - (RETAINED - 1)) remove(context, old[i]);
            }
            String token = UUID.randomUUID().toString().replace("-", "");
            try (FileOutputStream output = new FileOutputStream(new File(directory, token + ".json"))) { output.write(bytes); }
            return uri(token);
        }
    }

    private static void remove(Context context, File file) {
        String token = file.getName().substring(0, 32);
        try { context.revokeUriPermission(uri(token), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (RuntimeException ignored) { }
        file.delete();
    }

    static Uri uri(String token) { return Uri.parse("content://" + AUTHORITY + "/snapshot/" + token + "/" + FILE_NAME); }

    /** The snapshot file behind an exact URI of ours, if it exists and is still fresh. */
    static File file(Context context, Uri uri) throws FileNotFoundException {
        if (uri == null || !"content".equals(uri.getScheme()) || !AUTHORITY.equals(uri.getAuthority())
                || uri.getQuery() != null || uri.getFragment() != null || uri.getPathSegments().size() != 3
                || !"snapshot".equals(uri.getPathSegments().get(0)) || !FILE_NAME.equals(uri.getPathSegments().get(2)))
            throw new FileNotFoundException("Unknown configuration snapshot");
        String token = uri.getPathSegments().get(1);
        if (!token.matches("[0-9a-f]{32}")) throw new FileNotFoundException("Unknown configuration snapshot");
        File file = new File(new File(context.getCacheDir(), "config-shares"), token + ".json");
        long age = System.currentTimeMillis() - file.lastModified();
        if (!file.isFile() || age > SNAPSHOT_LIFETIME_MS || file.length() > ConfigTransfer.MAX_BYTES)
            throw new FileNotFoundException("Configuration snapshot expired");
        return file;
    }
}
