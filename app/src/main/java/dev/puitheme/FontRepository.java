// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.widget.TextView;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Shared font source and bounded weight cache. Imported files stay in private device storage. */
public final class FontRepository {
    private static final String MODULE = ModuleVariant.PACKAGE;
    private static String mode = "system", revision = "";
    private static volatile long styleRevision;
    private static final long FONT_RETRY_MS=5000L;
    /** Zero in the stable path; only actual users retry a failed source after this monotonic deadline. */
    private static volatile long retryAt;
    private static volatile long scopedRetryAt;
    private static boolean scopesEnabled;
    private static Map<String,String> scopes=java.util.Collections.emptyMap();
    private static final Map<String,Long> SCOPED_RETRIES=new LinkedHashMap<>();
    private static Context moduleContext;
    private static boolean contextFailureLogged, fontFailureLogged;
    private static final Object IMPORT_LOCK = new Object();
    private static final Map<Integer,Typeface> CACHE = new LinkedHashMap<Integer,Typeface>(32,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer,Typeface> entry) { return size() > 48; }
    };
    private static final Map<String,Typeface> MODE_CACHE = new LinkedHashMap<String,Typeface>(32,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Typeface> entry) { return size() > 96; }
    };
    private FontRepository() { }
    /** SystemUI's resident cache (null in the app). */
    static volatile ResidentCache resident;
    /** The imported font of this revision as a file in the resident cache, else from the app's provider (and cached for next time). */
    private static Typeface customFont(int sourceWeight, boolean italic) throws Exception {
        ResidentCache cache = resident;
        java.io.File cached = cache == null ? null : cache.font(moduleContext, revision);
        if (cached != null) return new Typeface.Builder(cached)
                .setFontVariationSettings("'wght' " + sourceWeight).setWeight(sourceWeight).setItalic(italic).setFallback("sans-serif").build();
        try (ParcelFileDescriptor font = moduleContext.getContentResolver()
                .openFileDescriptor(Uri.parse(StatusBarSettings.FONT_URI), "r")) {
            return font == null ? null : new Typeface.Builder(font.getFileDescriptor())
                    .setFontVariationSettings("'wght' " + sourceWeight).setWeight(sourceWeight).setItalic(italic).setFallback("sans-serif").build();
        }
    }
    private static Typeface scopedCustomFont(String hash,int sourceWeight,boolean italic)throws Exception{
        ResidentCache cache=resident;File cached=cache==null?null:cache.font(moduleContext,hash,true);
        if(cached!=null)return new Typeface.Builder(cached).setFontVariationSettings("'wght' "+sourceWeight)
                .setWeight(sourceWeight).setItalic(italic).setFallback("sans-serif").build();
        try(ParcelFileDescriptor font=moduleContext.getContentResolver().openFileDescriptor(libraryUri(hash),"r")){
            return font==null?null:new Typeface.Builder(font.getFileDescriptor()).setFontVariationSettings("'wght' "+sourceWeight)
                    .setWeight(sourceWeight).setItalic(italic).setFallback("sans-serif").build();
        }
    }
    static Uri libraryUri(String hash){
        if(!FontScopes.validRevision(hash))throw new IllegalArgumentException("字体引用无效");
        return Uri.parse("content://"+StatusBarSettings.AUTHORITY+"/font/library/"+hash);
    }
    /** A cheap event token for native renderers; reading it never loads or changes a font. */
    // Source/cache writes occur under the existing font lock, then this volatile token
    // publishes them. A renderer can test stability without taking the font/cache locks.
    static long styleRevision(){return styleRevision;}
    static boolean retryDue(){long deadline=retryAt,scoped=scopedRetryAt,now=SystemClock.uptimeMillis();
        return deadline!=0L&&now>=deadline||scoped!=0L&&now>=scoped;}
    /** An invisible failing scope cannot invalidate another scope's otherwise stable typography cache. */
    static synchronized boolean retryDue(String scope){
        String source=scopedSource(scope);long deadline;
        if(source==null)deadline=retryAt;
        else if("custom".equals(FontScopes.mode(source))){Long selected=SCOPED_RETRIES.get(source);deadline=selected==null?0L:selected;}
        else return false;
        return deadline!=0L&&SystemClock.uptimeMillis()>=deadline;
    }

    /** Release mapped fonts/context when the loaded module is removed; persisted files belong to the app. */
    public static synchronized void releaseRuntime() {
        CACHE.clear();MODE_CACHE.clear();FontWeight.release();moduleContext=null;
        mode="system";revision="";contextFailureLogged=false;fontFailureLogged=false;
        retryAt=0L;scopedRetryAt=0L;SCOPED_RETRIES.clear();scopes=java.util.Collections.emptyMap();scopesEnabled=false;
        styleRevision++;
    }

    public static synchronized void configure(Context context, String newMode, String newRevision) {
        if (context == null) return;
        if (!ensureModuleContext(context)) return;
        newMode = "pingfang".equals(newMode) || "custom".equals(newMode) ? newMode : "system";
        if (newRevision == null) newRevision = "";
        if (!newMode.equals(mode) || !newRevision.equals(revision)) {
            mode = newMode; revision = newRevision; CACHE.clear();MODE_CACHE.clear();FontWeight.release();fontFailureLogged=false;
            retryAt=0L;
            styleRevision++;
            ModuleDiagnostics.info("font","Font source changed: "+mode);
        }
    }
    /** Scope selection is independent of the legacy source and never changes its provider file. */
    public static synchronized void configureScopes(Bundle settings){
        boolean enabled=settings!=null&&FeatureOptions.from(settings).enabled("font");
        Map<String,String> next;
        try{next=FontScopes.parse(settings==null?FontScopes.DEFAULT:settings.getString(FontScopes.KEY,FontScopes.DEFAULT));}
        catch(IllegalArgumentException invalid){next=java.util.Collections.emptyMap();ModuleDiagnostics.error("font","Invalid font scopes; retaining legacy choices",invalid);}
        if(enabled==scopesEnabled&&next.equals(scopes))return;
        scopesEnabled=enabled;scopes=next;MODE_CACHE.clear();SCOPED_RETRIES.clear();scopedRetryAt=0L;fontFailureLogged=false;styleRevision++;
    }
    private static String scopedSource(String scope){return scopesEnabled&&scope!=null?scopes.get(scope):null;}
    public static synchronized boolean hasAssignment(String scope){return scopedSource(scope)!=null;}
    public static synchronized String modeForScope(String legacyMode,String scope){
        String source=scopedSource(scope);return source==null?legacyMode:FontScopes.mode(source);
    }
    public static synchronized String revisionForScope(String scope){
        String source=scopedSource(scope);return source==null?revision:FontScopes.revision(source);
    }

    private static boolean ensureModuleContext(Context context) {
        if (ModuleLifecycle.removed()) return false;
        if (moduleContext == null) {
            if (context == null) return false;
            try { moduleContext = MODULE.equals(context.getPackageName()) ? context
                    : context.createPackageContext(MODULE, Context.CONTEXT_IGNORE_SECURITY); }
            catch (Exception unavailable) {
                if (!contextFailureLogged) { contextFailureLogged=true;ModuleDiagnostics.error("font","Module font resources unavailable; retaining system font",unavailable); }
                return false;
            }
        }
        return true;
    }

    public static synchronized Typeface typeface(Typeface nativeFace, int weight) {
        int bounded = Math.max(1, Math.min(1000, weight));
        if (!ModuleLifecycle.removed() && !"system".equals(mode) && moduleContext != null) {
            int sourceWeight = sourceWeight(mode, bounded);
            boolean italic=nativeFace!=null&&nativeFace.isItalic();
            int cacheKey=sourceWeight*2+(italic?1:0);
            Typeface cached=CACHE.get(cacheKey);
            long deadline=retryAt;
            boolean retry=deadline!=0L&&SystemClock.uptimeMillis()>=deadline;
            if(cached!=null&&!retry)return cached;
            if(deadline!=0L&&!retry)return weighted(nativeFace,bounded);
            // Even an already cached weight can finish a pending retry when the failing view is hidden.
            Typeface loaded=null;
            try {
                if ("pingfang".equals(mode)) {
                    loaded = new Typeface.Builder(moduleContext.getAssets(), "fonts/PingFangSC-VF.ttf")
                            .setFontVariationSettings("'wght' " + sourceWeight).setWeight(sourceWeight).setItalic(italic).setFallback("sans-serif").build();
                } else if ("custom".equals(mode)) {
                    loaded = customFont(sourceWeight, italic);
                }
                if (loaded != null) {
                    CACHE.put(cacheKey,loaded);
                    if(deadline!=0L){retryAt=0L;fontFailureLogged=false;styleRevision++;}
                    return loaded;
                }
            } catch (Exception invalidFont) {
                if (!fontFailureLogged) { fontFailureLogged=true;ModuleDiagnostics.error("font","Font loading failed; using native family",invalidFont); }
            }
            retryAt=SystemClock.uptimeMillis()+FONT_RETRY_MS;
            if(cached!=null)return cached;
        }
        return weighted(nativeFace, bounded);
    }

    /** Independent clock font selection; reading a source never changes the global font setting. */
    public static synchronized Typeface typefaceForMode(Context context, String selectedMode, Typeface nativeFace, int weight) {
        int bounded = Math.max(1, Math.min(1000, weight));
        if ("global".equals(selectedMode)) return typeface(nativeFace, bounded);
        if ("system".equals(selectedMode)) return weighted(nativeFace!=null&&nativeFace.isItalic()
                ?Typeface.create(Typeface.DEFAULT,Typeface.ITALIC):Typeface.DEFAULT, bounded);
        if (!"pingfang".equals(selectedMode) && !"custom".equals(selectedMode)) return weighted(nativeFace, bounded);
        if (ensureModuleContext(context)) {
            int sourceWeight = sourceWeight(selectedMode, bounded);
            boolean italic=nativeFace!=null&&nativeFace.isItalic();
            String key = selectedMode + ':' + revision + ':' + sourceWeight + ':' + italic;
            Typeface loaded = MODE_CACHE.get(key);
            if (loaded != null) return loaded;
            try {
                if ("pingfang".equals(selectedMode)) {
                    loaded = new Typeface.Builder(moduleContext.getAssets(), "fonts/PingFangSC-VF.ttf")
                            .setFontVariationSettings("'wght' " + sourceWeight).setWeight(sourceWeight).setItalic(italic).setFallback("sans-serif").build();
                } else {
                    loaded = customFont(sourceWeight, italic);
                }
                if (loaded != null) { MODE_CACHE.put(key, loaded); return loaded; }
            } catch (Exception invalidFont) {
                if (!fontFailureLogged) { fontFailureLogged=true;ModuleDiagnostics.error("font","Clock font loading failed; using native family",invalidFont); }
            }
        }
        return weighted(nativeFace, bounded);
    }
    public static synchronized Typeface typeface(Typeface nativeFace,int weight,String scope){
        String source=scopedSource(scope);return source==null?typeface(nativeFace,weight):scopedTypeface(source,nativeFace,weight);
    }
    public static synchronized Typeface typefaceForMode(Context context,String selectedMode,Typeface nativeFace,int weight,String scope){
        String source=scopedSource(scope);
        if(source==null)return typefaceForMode(context,selectedMode,nativeFace,weight);
        ensureModuleContext(context);return scopedTypeface(source,nativeFace,weight);
    }
    private static Typeface scopedTypeface(String source,Typeface nativeFace,int weight){
        int bounded=Math.max(1,Math.min(1000,weight));String selected=FontScopes.mode(source);
        if(ModuleLifecycle.removed()||"system".equals(selected)||moduleContext==null)return weighted(nativeFace,bounded);
        int actual=sourceWeight(selected,bounded,FontScopes.revision(source));boolean italic=nativeFace!=null&&nativeFace.isItalic();
        String key="scope:"+source+':'+actual+':'+italic;Typeface loaded=MODE_CACHE.get(key);
        Long deadline=SCOPED_RETRIES.get(source);boolean retry=deadline!=null&&SystemClock.uptimeMillis()>=deadline;
        if(loaded!=null&&!retry)return loaded;if(deadline!=null&&!retry)return weighted(nativeFace,bounded);
        try{
            if("pingfang".equals(selected))loaded=new Typeface.Builder(moduleContext.getAssets(),"fonts/PingFangSC-VF.ttf")
                    .setFontVariationSettings("'wght' "+actual).setWeight(actual).setItalic(italic).setFallback("sans-serif").build();
            else loaded=scopedCustomFont(FontScopes.revision(source),actual,italic);
            if(loaded!=null){MODE_CACHE.put(key,loaded);if(SCOPED_RETRIES.remove(source)!=null){refreshScopedRetry();styleRevision++;}return loaded;}
        }catch(Exception invalid){if(!fontFailureLogged){fontFailureLogged=true;ModuleDiagnostics.error("font","Scoped font unavailable; retaining native family",invalid);}}
        SCOPED_RETRIES.put(source,SystemClock.uptimeMillis()+FONT_RETRY_MS);refreshScopedRetry();return weighted(nativeFace,bounded);
    }
    private static void refreshScopedRetry(){long earliest=0L;for(long value:SCOPED_RETRIES.values())if(earliest==0L||value<earliest)earliest=value;scopedRetryAt=earliest;}

    private static int sourceWeight(String selectedMode, int requested) {
        return sourceWeight(selectedMode,requested,revision);
    }
    private static int sourceWeight(String selectedMode,int requested,String selectedRevision){
        // The existing bundled asset's fvar range is 100..900, independent of the UI's manual range.
        if ("pingfang".equals(selectedMode)) return Math.max(100, Math.min(900, requested));
        FontCatalog.Entry catalog = "custom".equals(selectedMode) ? FontCatalog.forRevision(selectedRevision) : null;
        return catalog == null ? requested : catalog.clampWeight(requested);
    }

    private static Typeface weighted(Typeface nativeFace, int bounded) {
        return FontWeight.typeface(nativeFace,bounded);
    }

    /** Explicit axis application is needed on Android versions storing variations on Paint. */
    public static synchronized void apply(TextView view,Typeface nativeFace,int weight,String nativeAxes){
        FontWeight.apply(view,typeface(nativeFace,weight),sourceWeight(mode,Math.max(1,Math.min(1000,weight))),
                "system".equals(mode)?nativeAxes:null);
    }
    public static synchronized void apply(Paint paint,Typeface nativeFace,int weight,String nativeAxes){
        FontWeight.apply(paint,typeface(nativeFace,weight),sourceWeight(mode,Math.max(1,Math.min(1000,weight))),
                "system".equals(mode)?nativeAxes:null);
    }
    public static synchronized void apply(TextView view,Typeface nativeFace,int weight,String nativeAxes,String scope){
        String source=scopedSource(scope);if(source==null){apply(view,nativeFace,weight,nativeAxes);return;}
        ensureModuleContext(view.getContext());FontWeight.apply(view,scopedTypeface(source,nativeFace,weight),
                weightForMode("global",weight,scope),"system".equals(FontScopes.mode(source))?nativeAxes:null);
    }
    public static synchronized void apply(Paint paint,Typeface nativeFace,int weight,String nativeAxes,String scope){
        String source=scopedSource(scope);if(source==null){apply(paint,nativeFace,weight,nativeAxes);return;}
        FontWeight.apply(paint,scopedTypeface(source,nativeFace,weight),weightForMode("global",weight,scope),
                "system".equals(FontScopes.mode(source))?nativeAxes:null);
    }
    /** Return the exact source event used under the same lock, including a successful retry publication. */
    static synchronized long applyRevision(TextView view,Typeface nativeFace,int weight,String nativeAxes){
        apply(view,nativeFace,weight,nativeAxes);return styleRevision;
    }
    static synchronized long applyRevision(Paint paint,Typeface nativeFace,int weight,String nativeAxes){
        apply(paint,nativeFace,weight,nativeAxes);return styleRevision;
    }
    static synchronized long applyRevision(TextView view,Typeface nativeFace,int weight,String nativeAxes,String scope){apply(view,nativeFace,weight,nativeAxes,scope);return styleRevision;}
    static synchronized long applyRevision(Paint paint,Typeface nativeFace,int weight,String nativeAxes,String scope){apply(paint,nativeFace,weight,nativeAxes,scope);return styleRevision;}
    public static synchronized boolean systemMode(){return "system".equals(mode);}
    public static synchronized boolean systemMode(String scope){String source=scopedSource(scope);return source==null?systemMode():"system".equals(FontScopes.mode(source));}
    /** Applies the same physical source limits to a separately selected large-clock mode. */
    public static synchronized int weightForMode(String selectedMode,int requested){
        return sourceWeight("global".equals(selectedMode)?mode:selectedMode,Math.max(1,Math.min(1000,requested)));
    }
    public static synchronized int weightForMode(String selectedMode,int requested,String scope){
        String source=scopedSource(scope);return source==null?weightForMode(selectedMode,requested)
                :sourceWeight(FontScopes.mode(source),Math.max(1,Math.min(1000,requested)),FontScopes.revision(source));
    }

    public static String importFont(Context context, Uri uri) throws Exception {
        return importFont(context, uri, () -> true);
    }

    public static String importFont(Context context, Uri uri, BooleanSupplier canCommit) throws Exception {
        Objects.requireNonNull(canCommit);
        try (PreparedFont prepared = prepareFont(context, uri)) {
            return prepared.commit(canCommit, () -> true, () -> { });
        }
    }

    /** Reading and Typeface validation stay on the worker; publication runs with the Activity's lifecycle callbacks. */
    public static PreparedFont prepareFont(Context context, Uri uri) throws Exception {
        File directory = new File(context.createDeviceProtectedStorageContext().getFilesDir(), "fonts");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("无法创建字体目录");
        File temporary = File.createTempFile("import-", ".font", directory);
        boolean prepared = false;
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
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            PreparedFont result = new PreparedFont(temporary, new File(directory, "custom.font"), hash.toString());
            prepared = true;
            return result;
        } finally { if (!prepared && temporary.exists()) temporary.delete(); }
    }

    public static final class PreparedFont implements AutoCloseable {
        private final File temporary, target;
        public final String revision;
        private boolean closed;
        PreparedFont(File temporary, File target, String revision) {
            this.temporary = temporary; this.target = target; this.revision = revision;
        }
        /** Validated worker-stage inode for the persistent library; publication still uses commit(). */
        File stagedFile() throws IOException {
            if(closed||!temporary.isFile())throw new IOException("字体暂存已关闭");
            return temporary;
        }

        /** Commit on the main thread so pause/recheck cannot interleave file publication and preference submission. */
        public String commit(BooleanSupplier canCommit, BooleanSupplier saveOptions, Runnable restoreOptions) throws Exception {
            Objects.requireNonNull(canCommit); Objects.requireNonNull(saveOptions); Objects.requireNonNull(restoreOptions);
            synchronized (IMPORT_LOCK) {
                if (closed) throw new IOException("字体暂存已关闭");
                File backup = null;
                boolean published = false, optionsAttempted = false, keepBackup = false;
                try {
                    if (!canCommit.getAsBoolean()) throw new IOException("模块未确认激活，字体未导入");
                    if (target.exists()) {
                        backup = File.createTempFile("previous-", ".font", target.getParentFile());
                        android.system.Os.remove(backup.getAbsolutePath());
                        // A hard link keeps the old inode without copying a large font on the UI thread.
                        android.system.Os.link(target.getAbsolutePath(), backup.getAbsolutePath());
                    }
                    publishValidatedFont(temporary, target, canCommit);
                    published = true;
                    if (!canCommit.getAsBoolean()) throw new IOException("模块激活状态已失效，字体未导入");
                    optionsAttempted = true;
                    if (!saveOptions.getAsBoolean()) throw new IOException("字体设置未提交，已保留原字体");
                    if (!canCommit.getAsBoolean()) throw new IOException("模块激活状态已失效，字体未导入");
                    return revision;
                } catch (Exception failure) {
                    if (published) {
                        keepBackup = backup != null;
                        try {
                            if (backup != null) android.system.Os.rename(backup.getAbsolutePath(), target.getAbsolutePath());
                            else if (target.exists()) android.system.Os.remove(target.getAbsolutePath());
                            keepBackup = false;
                        } catch (Exception rollback) { failure.addSuppressed(rollback); }
                    }
                    if (optionsAttempted) {
                        try { restoreOptions.run(); }
                        catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                    }
                    throw failure;
                } finally {
                    if (backup != null && !keepBackup && backup.exists()) backup.delete();
                    close();
                }
            }
        }

        @Override public void close() {
            synchronized (IMPORT_LOCK) {
                closed = true;
                if (temporary.exists()) temporary.delete();
            }
        }
    }

    static void publishValidatedFont(File temporary, File target, BooleanSupplier canCommit) throws Exception {
        if (!canCommit.getAsBoolean()) throw new IOException("模块未确认激活，字体未导入");
        android.system.Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
    }

    public static boolean supportedHeader(byte[] value) {
        if (value == null || value.length < 4) return false;
        return (value[0] == 0 && value[1] == 1 && value[2] == 0 && value[3] == 0)
                || (value[0] == 'O' && value[1] == 'T' && value[2] == 'T' && value[3] == 'O')
                || (value[0] == 't' && value[1] == 't' && value[2] == 'c' && value[3] == 'f');
    }
}
