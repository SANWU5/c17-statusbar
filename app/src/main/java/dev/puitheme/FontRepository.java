package dev.puitheme;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared font source and bounded weight cache. Imported files stay in private device storage. */
public final class FontRepository {
    private static final String MODULE = "dev.puitheme.iosstatusbar";
    private static String mode = "system", revision = "";
    private static Context moduleContext;
    private static boolean contextFailureLogged, fontFailureLogged;
    private static final Map<Integer,Typeface> CACHE = new LinkedHashMap<Integer,Typeface>(32,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer,Typeface> entry) { return size() > 48; }
    };
    private FontRepository() { }

    public static synchronized void configure(Context context, String newMode, String newRevision) {
        if (context == null) return;
        if (moduleContext == null) {
            try { moduleContext = MODULE.equals(context.getPackageName()) ? context
                    : context.createPackageContext(MODULE, Context.CONTEXT_IGNORE_SECURITY); }
            catch (Exception unavailable) {
                if (!contextFailureLogged) { contextFailureLogged=true;ModuleDiagnostics.error("font","Module font resources unavailable; retaining system font",unavailable); }
                return;
            }
        }
        newMode = "pingfang".equals(newMode) || "custom".equals(newMode) ? newMode : "system";
        if (newRevision == null) newRevision = "";
        if (!newMode.equals(mode) || !newRevision.equals(revision)) {
            mode = newMode; revision = newRevision; CACHE.clear();fontFailureLogged=false;
            ModuleDiagnostics.info("font","Font source changed: "+mode);
        }
    }

    public static synchronized Typeface typeface(Typeface nativeFace, int weight) {
        int bounded = Math.max(100, Math.min(900, weight));
        if (!"system".equals(mode) && moduleContext != null) {
            Typeface loaded = CACHE.get(bounded);
            if (loaded != null) return loaded;
            try {
                if ("pingfang".equals(mode)) {
                    loaded = new Typeface.Builder(moduleContext.getAssets(), "fonts/PingFangSC-VF.ttf")
                            .setFontVariationSettings("'wght' " + bounded).setWeight(bounded).setFallback("sans-serif").build();
                } else if ("custom".equals(mode)) {
                    try (ParcelFileDescriptor font = moduleContext.getContentResolver()
                            .openFileDescriptor(Uri.parse(StatusBarSettings.FONT_URI), "r")) {
                        if (font != null) loaded = new Typeface.Builder(font.getFileDescriptor())
                                .setFontVariationSettings("'wght' " + bounded).setWeight(bounded).setFallback("sans-serif").build();
                    }
                }
                if (loaded != null) { CACHE.put(bounded, loaded); return loaded; }
            } catch (Exception invalidFont) {
                if (!fontFailureLogged) { fontFailureLogged=true;ModuleDiagnostics.error("font","Font loading failed; using native family",invalidFont); }
            }
        }
        Typeface fallback = nativeFace == null ? Typeface.DEFAULT : nativeFace;
        return Build.VERSION.SDK_INT >= 28 ? Typeface.create(fallback, bounded, false)
                : Typeface.create(fallback, bounded >= 600 ? Typeface.BOLD : Typeface.NORMAL);
    }

    public static String importFont(Context context, Uri uri) throws Exception {
        File directory = new File(context.createDeviceProtectedStorageContext().getFilesDir(), "fonts");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("无法创建字体目录");
        File temporary = File.createTempFile("import-", ".font", directory);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                    FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IllegalArgumentException("无法读取字体文件");
                byte[] bytes = new byte[32768]; long length = 0; int count;
                while ((count = input.read(bytes)) != -1) {
                    length += count;
                    if (length > 96L * 1024 * 1024) throw new IllegalArgumentException("字体文件最多 96 MB");
                    output.write(bytes, 0, count); digest.update(bytes, 0, count);
                }
                output.getFD().sync();
            }
            try (FileInputStream input = new FileInputStream(temporary)) {
                byte[] magic = new byte[4];
                if (input.read(magic) != 4 || !supportedHeader(magic))
                    throw new IllegalArgumentException("请选择有效的 TTF、OTF 或 TTC 字体");
            }
            if (new Typeface.Builder(temporary).build() == null) throw new IllegalArgumentException("字体文件无法加载");
            File target = new File(directory, "custom.font");
            // rename(2) atomically replaces the previous font without losing it on an invalid import.
            android.system.Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return hash.toString();
        } finally { if (temporary.exists()) temporary.delete(); }
    }

    public static boolean supportedHeader(byte[] value) {
        if (value == null || value.length < 4) return false;
        return (value[0] == 0 && value[1] == 1 && value[2] == 0 && value[3] == 0)
                || (value[0] == 'O' && value[1] == 'T' && value[2] == 'T' && value[3] == 'O')
                || (value[0] == 't' && value[1] == 't' && value[2] == 'c' && value[3] == 'f');
    }
}
