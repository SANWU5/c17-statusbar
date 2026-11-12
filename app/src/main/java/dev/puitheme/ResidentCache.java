// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Built-in resource cache: files the module would otherwise fetch from its own app again after every SystemUI start, kept in SystemUI's own
 * device-protected storage (files/c17-resident-cache). It is read before the user unlocks, survives SystemUI restarts and reboots,
 * needs no additional process or timer for local fallback, and only fetches missing or changed resources.
 *
 * It holds the imported custom font (every SystemUI start, and every new weight the status bar asks for, opened the font through the
 * app's provider, which starts the app's process when it is not running; one copy per font revision is kept) and the icon libraries in
 * use (their manifest and every glyph were read one by one through the same provider at every start; a library's id is the hash of its
 * content, so a cached entry never goes stale, and entries of libraries no longer in use are removed).
 *
 * It is enabled by default without an app action or a settings broadcast. Legacy installed/running preferences are retained only for
 * compatibility; they no longer disable this built-in cache. Safety mode pauses reads and writes without removing persisted files.
 * The optional companion supplies immutable bytes over a root-authenticated socket on resource workers only.
 * Local storage remains the fallback when the companion is absent; draw paths never wait on IPC. */
final class ResidentCache {
    static final String INSTALLED = "resident_cache_installed";
    static final String RUNNING = "resident_cache_running";
    static final String FOLDER = "c17-resident-cache";
    /** Report keys (SystemUI -> app through the runtime probe). */
    static final String KEY = "resident_cache";
    static final String R_INSTALLED = "installed", R_RUNNING = "running", R_PRESENT = "present", R_FILES = "files", R_BYTES = "bytes",
            R_HITS = "hits", R_MISSES = "misses", R_WRITES = "writes", R_ERROR = "error", R_PATH = "path";
    private static final long MAX_FONT_BYTES = 64L * 1024L * 1024L, MAX_TOTAL_BYTES = 96L * 1024L * 1024L;
    /** SystemUI's instance, for readers that are not handed one (null in the app). */
    static volatile ResidentCache current;
    interface Source { InputStream open() throws IOException; }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "C17ResidentCache");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private volatile File folder;
    private volatile boolean installed, running, released, initialized, paused;
    private volatile String lastError = "";
    private final java.util.Set<String> pendingFonts=java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String,Boolean>());
    private volatile long hits, misses, writes;
    private final ResourceMemoryCache memory = new ResourceMemoryCache(8L * 1024L * 1024L);
    private Context callbackContext;
    private final android.content.ComponentCallbacks2 callbacks = new android.content.ComponentCallbacks2() {
        @Override public void onConfigurationChanged(android.content.res.Configuration value) { }
        @Override public void onLowMemory() { trim(true); }
        @Override public void onTrimMemory(int level) {
            // Hiding a panel is not memory pressure. Keep the current core set warm.
            if (level == TRIM_MEMORY_UI_HIDDEN) return;
            if (level >= TRIM_MEMORY_RUNNING_LOW) trim(level == TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_COMPLETE);
        }
    };
    private void trim(boolean severe) {
        memory.trim(severe);
        if (!released) try { worker.execute(() -> CompanionCacheClient.trim(severe)); }
        catch (java.util.concurrent.RejectedExecutionException stopped) { }
    }
    private byte[] shared(String name, int max) {
        byte[] bytes = memory.get(name);
        if (bytes != null && bytes.length <= max) return bytes;
        bytes = CompanionCacheClient.get(CompanionCacheClient.key("host-resource-v1", name), max);
        if (bytes != null) memory.put(name, bytes, false);
        return bytes;
    }
    private void remember(String name, byte[] bytes) {
        memory.put(name, bytes, false);
        CompanionCacheClient.put(CompanionCacheClient.key("host-resource-v1", name), bytes, false);
    }

    /** First usable SystemUI context, independently of settings delivery. Never resets an already applied safety-mode pause. */
    synchronized void initialize(Context context) {
        if (released || context == null) return;
        if (folder == null) {
            try {
                Context storage = context.createDeviceProtectedStorageContext();
                folder = new File((storage == null ? context : storage).getFilesDir(), FOLDER);
            } catch (RuntimeException unavailable) { lastError = "无法打开系统界面存储"; return; }
        }
        boolean wasRunning = running, first = !initialized;
        installed = true; running = !paused; initialized = true;
        if (callbackContext == null) {
            Context app = context.getApplicationContext();
            callbackContext = app == null ? context : app;
            callbackContext.registerComponentCallbacks(callbacks);
        }
        final File store = folder;
        if (running && (first || !wasRunning)) worker.execute(() -> {
            if (!running || released) return;
            if (!store.isDirectory() && !store.mkdirs()) lastError = "无法创建缓存目录";
        });
    }

    /** Settings only pause/resume the built-in cache; old install/stop/remove values cannot delete or disable it. */
    synchronized void configure(Context context, Bundle settings) {
        if (released) return;
        paused = SafetyMode.enabled(settings);
        if (paused) { running = false; memory.trim(true); }
        initialize(context);
        if(settings!=null&&settings.containsKey(FontScopes.KEY))try{
            java.util.Set<String> keep=new java.util.HashSet<>();
            String legacy=settings.getString(StatusBarSettings.FONT_REVISION,"");
            if(!legacy.isEmpty())keep.add("font-"+safe(legacy)+".font");
            for(String source:FontScopes.parse(settings.getString(FontScopes.KEY,FontScopes.DEFAULT)).values())
                if("custom".equals(FontScopes.mode(source)))keep.add("font-library-"+FontScopes.revision(source)+".font");
            retainFonts(keep);
        }catch(IllegalArgumentException malformed){/* A malformed selection cannot erase a previously usable cached source. */}
    }

    /** The cached copy of the custom font of this revision, or null (then the caller reads it from the app as before, and a copy is
     * made in the background for the next time). */
    File font(Context moduleContext, String revision) {
        return font(moduleContext,revision,false);
    }
    /** Library hashes have a separate namespace from the mutable legacy current endpoint. */
    File font(Context moduleContext,String revision,boolean library){
        if (!running || released || folder == null || revision == null || revision.isEmpty()) return null;
        if(library&&!FontScopes.validRevision(revision))return null;
        File cached = new File(folder, (library?"font-library-":"font-") + safe(revision) + ".font");
        if (cached.isFile() && cached.length() > 0) {
            if(!library||FontLibrary.matchesFile(cached,revision)){hits++;return cached;}
            if(!cached.delete())cached.deleteOnExit();
        }
        misses++;
        scheduleFont(moduleContext, revision, cached,library);
        return null;
    }

    private void scheduleFont(final Context moduleContext, final String revision, final File target,final boolean library) {
        if (moduleContext == null || !pendingFonts.add(target.getName())) return;
        final File store = folder;
        worker.execute(() -> {
            try {
                if (!running || released) return;
                if (!store.isDirectory() && !store.mkdirs()) throw new IOException("无法创建缓存目录");
                File temporary = new File(store, target.getName() + ".new");
                long used=0L;File[] existing=store.listFiles();if(existing!=null)for(File file:existing)if(file.isFile()&&!file.equals(temporary))used+=file.length();
                try (ParcelFileDescriptor source = moduleContext.getContentResolver().openFileDescriptor(library?FontRepository.libraryUri(revision):Uri.parse(StatusBarSettings.FONT_URI), "r")) {
                    if (source == null) throw new IOException("字体不可用");
                    try (InputStream input = new FileInputStream(source.getFileDescriptor()); OutputStream output = new FileOutputStream(temporary)) {
                        byte[] buffer = new byte[64 * 1024];
                        long total = 0;
                        for (int read; (read = input.read(buffer)) != -1; ) {
                            total += read;
                            if (total > MAX_FONT_BYTES) throw new IOException("字体过大");
                            if(used+total>MAX_TOTAL_BYTES)throw new IOException("缓存已满（上限 96 MB）");
                            output.write(buffer, 0, read);
                        }
                    }
                }
                if(library&&!revision.equals(FontFileValidator.sha256(temporary,MAX_FONT_BYTES,null)))throw new IOException("字体缓存校验失败");
                if (!temporary.renameTo(target)) throw new IOException("无法写入缓存");
                writes++; lastError = "";
                // Independent scopes can keep different revisions; caching one must not evict the others.
            } catch (IOException | RuntimeException failure) {
                lastError = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                new File(store, target.getName() + ".new").delete();
            } finally {
                pendingFonts.remove(target.getName());
            }
        });
    }
    private void retainFonts(java.util.Set<String> selected){
        final File store=folder;if(store==null||!running||released)return;
        final java.util.Set<String> keep=new java.util.HashSet<>(selected);
        worker.execute(()->{
            if(!running||released)return;File[] files=store.listFiles();if(files==null)return;
            for(File file:files)if(file.getName().startsWith("font-")&&file.getName().endsWith(".font")
                    &&!keep.contains(file.getName()))if(!file.delete())file.deleteOnExit();
        });
    }

    /** An icon-library entry: from the store when it is there, else from the app (a copy is kept when the cache runs). Runs on the
     * icon library's own worker thread. */
    byte[] iconEntry(String libraryId, String path, Source source, int max) throws IOException {
        File store = folder;
        boolean use = running && !released && store != null && libraryId != null && libraryId.matches("[0-9a-f]{16,128}") && path != null;
        File cached = use ? new File(store, "icon-" + libraryId + "-" + digest(path)) : null;
        if (cached != null) {
            byte[] ready = shared(cached.getName(), max);
            if (ready != null) { hits++; return ready; }
        }
        if (cached != null && cached.isFile() && cached.length() > 0 && cached.length() <= max) {
            try (InputStream input = new FileInputStream(cached)) { byte[] bytes = IconPackArchive.readEntry(input, max); hits++; remember(cached.getName(), bytes); return bytes; }
            catch (IOException damaged) { if (!cached.delete()) cached.deleteOnExit(); }
        }
        byte[] bytes;
        try (InputStream input = source.open()) {
            if (input == null) return null;
            bytes = IconPackArchive.readEntry(input, max);
        }
        if (cached != null) { misses++; keep(store, cached, bytes); remember(cached.getName(), bytes); }
        return bytes;
    }
    /** v3 has a separate namespace and validates cached/provider bytes before publication to a decoder. */
    byte[] iconV3Entry(String libraryId,String path,String expectedHash,Source source,int max)throws IOException{
        if(!IconManagementSettings.validPackId(libraryId)||!("manifest.json".equals(path)||IconManagementManifest.validAsset(path))
                ||max<1||max>IconManagementArchive.MAX_ENTRY)throw IconManagementJson.error("v3 缓存请求无效");
        boolean manifest="manifest.json".equals(path);if(!manifest&&!IconManagementSettings.validPackId(expectedHash))throw IconManagementJson.error("v3 资源必须有摘要");
        File store=folder;boolean use=running&&!released&&store!=null;
        File cached=use?new File(store,"icon-v3-"+libraryId+'-'+digest(path)+'-'+(manifest?"manifest":expectedHash)+".data"):null;
        if(cached!=null)try{byte[] bytes=shared(cached.getName(),max);if(bytes!=null){validateIconV3(bytes,manifest,expectedHash);hits++;return bytes;}}
        catch(IOException|RuntimeException damaged){memory.removePrefixExcept(cached.getName(),"");}
        if(cached!=null&&cached.isFile())try{byte[] bytes=IconManagementRepository.read(cached,max);validateIconV3(bytes,manifest,expectedHash);hits++;remember(cached.getName(),bytes);return bytes;}
        catch(IOException|RuntimeException damaged){if(!cached.delete())cached.deleteOnExit();}
        byte[] bytes;try(InputStream input=source.open()){if(input==null)return null;bytes=IconManagementArchive.readEntry(input,max);}validateIconV3(bytes,manifest,expectedHash);
        if(cached!=null){misses++;keep(store,cached,bytes);remember(cached.getName(),bytes);}return bytes;
    }
    private static void validateIconV3(byte[] bytes,boolean manifest,String expectedHash)throws IOException{
        if(manifest)IconManagementManifest.parse(bytes).requireRuntimeHashes();else IconAssetDecoder.verify(bytes,expectedHash);
    }
    void retainIconsV3(java.util.Set<String> libraries){
        final File store=folder;if(store==null||!running||released)return;final java.util.Set<String> keep=new java.util.HashSet<>(libraries);
        worker.execute(()->{if(!running||released)return;File[] files=store.listFiles();if(files==null)return;for(File file:files){String name=file.getName();if(!name.startsWith("icon-v3-"))continue;int end=name.indexOf('-',8);if(end<0||!keep.contains(name.substring(8,end)))if(!file.delete())file.deleteOnExit();}});
    }

    /** One file of the notification centre or control centre (a wallpaper of one scene, the custom notification icon): from the store
     * when it is there, else from the app (kept when the cache runs). {@code group} names what the file is for; a newer file of the same
     * group replaces the older one. {@code version} is the file's revision, so a stored copy never goes stale. Runs on the caller's
     * worker thread, never the main thread. */
    byte[] entry(String group, String version, Source source, int max) throws IOException {
        File store = folder;
        boolean use = running && !released && store != null && group != null && version != null && !version.isEmpty();
        String prefix = use ? "file-" + safe(group) + "-" : null;
        File cached = use ? new File(store, prefix + digest(version)) : null;
        if (cached != null) {
            byte[] ready = shared(cached.getName(), max);
            if (ready != null) { hits++; return ready; }
        }
        if (cached != null && cached.isFile() && cached.length() > 0 && cached.length() <= max) {
            try (InputStream input = new FileInputStream(cached)) { byte[] bytes = IconPackArchive.readEntry(input, max); hits++; remember(cached.getName(), bytes); return bytes; }
            catch (IOException damaged) { if (!cached.delete()) cached.deleteOnExit(); }
        }
        byte[] bytes;
        try (InputStream input = source.open()) {
            if (input == null) return null;
            bytes = IconPackArchive.readEntry(input, max);
        }
        if (cached != null) {
            misses++;
            keep(store, cached, bytes);
            memory.removePrefixExcept(prefix, cached.getName());
            remember(cached.getName(), bytes);
            File[] files = store.listFiles();
            if (files != null) for (File old : files)
                if (old.getName().startsWith(prefix) && !old.getName().equals(cached.getName()) && !old.delete()) old.deleteOnExit();
        }
        return bytes;
    }

    /** Entries of icon libraries that are no longer selected are removed (on the cache's own thread). */
    void retainIcons(final java.util.Set<String> libraries) {
        final File store = folder;
        if (store == null || !running || released) return;
        final java.util.Set<String> keep = new java.util.HashSet<>(libraries);
        worker.execute(() -> {
            if (!running || released) return;
            File[] files = store.listFiles();
            if (files == null) return;
            for (File file : files) {
                String name = file.getName();
                if (!name.startsWith("icon-")||name.startsWith("icon-v3-")) continue;
                int end = name.indexOf('-', 5);
                if (end < 0 || !keep.contains(name.substring(5, end))) if (!file.delete()) file.deleteOnExit();
            }
        });
    }

    private void keep(File store, File target, byte[] bytes) {
        if (!running || released) return;
        try {
            if (!store.isDirectory() && !store.mkdirs()) throw new IOException("无法创建缓存目录");
            long used = 0;
            File[] files = store.listFiles();
            if (files != null) for (File file : files) used += file.length();
            if (used + bytes.length > MAX_TOTAL_BYTES) throw new IOException("缓存已满（上限 96 MB）");
            File temporary = new File(store, target.getName() + ".new");
            try (OutputStream output = new FileOutputStream(temporary)) { output.write(bytes); }
            if (!temporary.renameTo(target)) { temporary.delete(); throw new IOException("无法写入缓存"); }
            writes++; lastError = "";
        } catch (IOException | RuntimeException failure) {
            lastError = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        }
    }

    static String digest(String value) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 12; i++) hex.append(String.format("%02x", hash[i] & 0xff));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            return safe(value);
        }
    }

    /** What the store really is right now, retained for bounded runtime diagnostics. */
    Bundle report() {
        Bundle report = new Bundle();
        File store = folder;
        long bytes = 0; int files = 0;
        File[] list = store == null ? null : store.listFiles();
        if (list != null) for (File file : list) if (file.isFile() && !file.getName().endsWith(".new")) { files++; bytes += file.length(); }
        report.putBoolean(R_INSTALLED, installed);
        report.putBoolean(R_RUNNING, running);
        report.putBoolean(R_PRESENT, store != null && store.isDirectory());
        report.putInt(R_FILES, files);
        report.putLong(R_BYTES, bytes);
        report.putLong(R_HITS, hits);
        report.putLong(R_MISSES, misses);
        report.putLong(R_WRITES, writes);
        report.putString(R_ERROR, lastError == null ? "" : lastError);
        report.putString(R_PATH, store == null ? "" : store.getPath());
        report.putLong("memory_bytes", memory.bytes()); report.putLong("memory_hits", memory.hits());
        report.putLong("memory_misses", memory.misses()); report.putLong("memory_evictions", memory.evictions());
        CompanionCacheClient.report(report);
        return report;
    }

    void release() {
        released = true; running = false; memory.trim(true);
        if (callbackContext != null) { callbackContext.unregisterComponentCallbacks(callbacks); callbackContext = null; }
        worker.shutdown();
    }

    static String safe(String revision) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < revision.length() && result.length() < 64; i++) {
            char c = revision.charAt(i);
            result.append(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-' || c == '_' ? c : '_');
        }
        return result.toString();
    }

}
