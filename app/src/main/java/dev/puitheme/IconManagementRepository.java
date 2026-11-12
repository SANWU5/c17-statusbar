// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Version-isolated immutable resources. Importing and previewing never apply a package. */
public final class IconManagementRepository {
    public static final String FOLDER="icon-management-v3",URI_PREFIX="content://"+StatusBarSettings.AUTHORITY+"/icon-management-v3/";
    public static final int MAX_LIBRARY=32;private static final Object LOCK=new Object();
    public static final class Pack{
        public final String id,name,author,license;public final int iconCount,pngCount,vectorCount,animationCount,unsupportedCount;public final IconManagementManifest manifest;
        Pack(String id,String name,IconManagementManifest manifest){this.id=id;this.name=visibleName(name);author=manifest.author;license=manifest.license;this.manifest=manifest;iconCount=manifest.icons.size();int png=0,vector=0;for(IconManagementManifest.Asset asset:manifest.icons.values())if("png".equals(asset.format))png++;else vector++;pngCount=png;vectorCount=vector;animationCount=manifest.transitions.size();unsupportedCount=manifest.unsupportedRoles.size();}
    }
    static File root(Context context)throws IOException{
        if(context==null||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName()))throw new SecurityException("Own application context required");File files=context.createDeviceProtectedStorageContext().getFilesDir().getCanonicalFile();File root=new File(files,FOLDER);
        if(!root.exists()&&!root.mkdir()||!root.isDirectory()||!root.getAbsoluteFile().equals(root.getCanonicalFile()))throw IconManagementJson.error("v3 图标库目录无效");return root;
    }
    static File directory(File root,String id)throws IOException{if(!IconManagementSettings.validPackId(id))throw IconManagementJson.error("图标库摘要无效");File path=new File(root,id);if(!path.getAbsoluteFile().equals(path.getCanonicalFile())||!path.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))throw IconManagementJson.error("图标库路径无效");return path;}
    static File createSource(Context context)throws IOException{return File.createTempFile("source-",".archive",root(context));}
    private static final Pattern LEGACY_EXAMPLE=Pattern.compile("(?i)^C17 Rounded\\s*27(?=$|\\s|[·._-])");
    private static String visibleName(String value){String normalized=Normalizer.normalize(value,Normalizer.Form.NFKC).trim();Matcher legacy=LEGACY_EXAMPLE.matcher(normalized);return legacy.find()?"C17示例库"+normalized.substring(legacy.end()):value;}
    private static String displayName(String value,String fallback)throws IOException{String name=value==null||value.trim().isEmpty()?fallback:value.trim();if(name==null||name.isEmpty()||name.length()>64||name.matches("(?s).*\\p{Cntrl}.*")||Normalizer.normalize(name,Normalizer.Form.NFKC).trim().isEmpty())throw IconManagementJson.error("图标库名称需为 1–64 字符");return visibleName(name);}
    /** Pure comparison for UI drafts; storage and final publication repeat this check under LOCK. */
    public static String nameKey(String name)throws IOException{return Normalizer.normalize(displayName(name,null),Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);}
    public static final class ImportStatus {
        public final Pack existing,nameConflict;
        private ImportStatus(Pack existing,Pack conflict){this.existing=existing;nameConflict=conflict;}
    }
    public static final class NameConflictException extends IOException {
        public final Pack existing;
        private NameConflictException(Pack existing){super("已存在名为“"+existing.name+"”的图标库，请修改名称");this.existing=existing;}
    }
    public static final class AlreadyInstalledException extends IOException {
        public final Pack existing;
        private AlreadyInstalledException(Pack existing){super("此图标库已导入为“"+existing.name+"”，请使用已有库");this.existing=existing;}
    }
    private static Pack existing(File root,String id)throws IOException{File target=directory(root,id);if(!target.isDirectory())return null;Pack pack=load(root,id);verifyPack(target,id,pack.manifest);return pack;}
    private static Pack conflict(File root,String name,String excludeId)throws IOException{
        String key=nameKey(name);File[] directories=root.listFiles();if(directories==null)throw IconManagementJson.error("图标库无法读取");Arrays.sort(directories,Comparator.comparing(File::getName));
        for(File path:directories)if(path.isDirectory()&&IconManagementSettings.validPackId(path.getName())&&!path.getName().equals(excludeId))try{
            Pack pack=load(root,path.getName());if(key.equals(nameKey(pack.name))){verifyPack(path,pack.id,pack.manifest);return pack;}
        }catch(IOException damaged){/* Match the usable library list; damaged sources cannot reserve an invisible name. */}
        return null;
    }
    public static Pack nameConflict(Context context,String name,String excludeId)throws IOException{synchronized(LOCK){return conflict(root(context),name,excludeId);}}
    public static List<Pack> list(Context context)throws IOException{synchronized(LOCK){File root=root(context);File[] directories=root.listFiles();if(directories==null)throw IconManagementJson.error("图标库无法读取");Arrays.sort(directories,Comparator.comparing(File::getName));List<Pack> result=new ArrayList<>();for(File path:directories)if(path.isDirectory()&&IconManagementSettings.validPackId(path.getName()))try{Pack pack=load(root,path.getName());verifyPack(path,pack.id,pack.manifest);result.add(pack);if(result.size()>=MAX_LIBRARY)break;}catch(IOException damaged){/* A damaged package is never offered as an applied source. */}return Collections.unmodifiableList(result);}}
    public static Pack get(Context context,String id)throws IOException{synchronized(LOCK){File root=root(context);Pack pack=load(root,id);verifyPack(directory(root,id),id,pack.manifest);return pack;}}
    private static Pack load(File root,String id)throws IOException{File directory=directory(root,id);IconManagementManifest manifest=IconManagementManifest.parse(read(new File(directory,"manifest.json"),IconManagementManifest.MAX_BYTES));String name=manifest.name;File label=new File(directory,"name.json");if(label.isFile())name=displayName(IconManagementJson.text(IconManagementJson.parse(read(label,1024),1024),"name",manifest.name,64),manifest.name);return new Pack(id,name,manifest);}
    public static PreparedPack prepare(Context context,Uri uri,String name)throws Exception{
        File source=createSource(context);try{try(InputStream input=context.getContentResolver().openInputStream(uri);FileOutputStream output=new FileOutputStream(source)){if(input==null)throw IconManagementJson.error("图标包无法读取");copy(input,output,IconManagementArchive.MAX_ARCHIVE);output.getFD().sync();}return prepare(context,source,name);}finally{source.delete();}
    }
    public static PreparedPack prepare(Context context,File archive,String name)throws Exception{
        Map<String,byte[]> entries=IconManagementArchive.read(archive);IconManagementManifest manifest=IconManagementManifest.parse(entries.get("manifest.json"));String label=displayName(name,manifest.name);
        Map<String,String> hashes=new TreeMap<>();Map<String,Boolean> animations=new HashMap<>();for(IconManagementManifest.Transition transition:manifest.transitions.values()){boolean transientOnly="transient".equals(transition.from);animations.put(transition.source,transientOnly&&!Boolean.FALSE.equals(animations.get(transition.source)));}
        // Validate every supplied asset, including unreferenced ones; invalid extras are not silently dropped.
        for(Map.Entry<String,byte[]> item:entries.entrySet())if(item.getKey().startsWith("assets/")){if(item.getKey().endsWith(".json"))IconAnimationAsset.parse(item.getValue(),Boolean.TRUE.equals(animations.get(item.getKey())));else IconAssetDecoder.validate(item.getKey(),item.getValue());hashes.put(item.getKey(),IconAssetDecoder.sha256(item.getValue()));}
        manifest=manifest.withHashes(hashes);byte[] canonical=manifest.bytes();String id=revision(manifest,canonical,source->{byte[] bytes=entries.get(source);if(bytes==null)throw IconManagementJson.error("图标包缺少资源："+source);return bytes;});
        File root=root(context),staging=File.createTempFile("pending-",".pack",root);if(!staging.delete()||!staging.mkdir())throw IconManagementJson.error("无法创建图标暂存");boolean ready=false;
        try{File assets=new File(staging,"assets");if(!assets.mkdir())throw IconManagementJson.error("无法创建图标资源目录");for(String source:manifest.sources())write(new File(staging,source),entries.get(source));write(new File(staging,"manifest.json"),canonical);writeName(staging,label);Pack pack=new Pack(id,label,manifest);ready=true;return new PreparedPack(context,root,staging,pack);}finally{if(!ready)deleteManaged(root,staging);}
    }
    private interface Source{byte[] read(String path)throws IOException;}
    private static String revision(IconManagementManifest manifest,byte[] canonical,Source source)throws IOException{
        try{MessageDigest hash=MessageDigest.getInstance("SHA-256");hash.update(canonical);for(String path:manifest.sources()){hash.update(path.getBytes(StandardCharsets.UTF_8));hash.update(source.read(path));}StringBuilder out=new StringBuilder(64);for(byte value:hash.digest())out.append(String.format(Locale.ROOT,"%02x",value&255));return out.toString();}catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}
    }
    public static final class PreparedPack implements AutoCloseable{
        private final Context context;private final File root,staging;public volatile Pack pack;private boolean closed;
        private PreparedPack(Context context,File root,File staging,Pack pack){Context app=context.getApplicationContext();this.context=app==null?context:app;this.root=root;this.staging=staging;this.pack=pack;}
        public Bitmap preview(String role,int size)throws Exception{return preview(role,size,role);}
        public Bitmap preview(String role,int size,String geometryRole)throws Exception{synchronized(LOCK){
            if(closed)throw IconManagementJson.error("图标预览已关闭");
            return previewFile(pack,staging,role,size,geometryRole);
        }}
        public ImportStatus status()throws IOException{return status(pack.name);}
        public ImportStatus status(String name)throws IOException{synchronized(LOCK){if(closed)throw IconManagementJson.error("图标暂存已关闭");Pack installed=existing(root,pack.id);return new ImportStatus(installed,installed==null?conflict(root,displayName(name,null),pack.id):null);}}
        /** Explicit draft rename only; failure leaves the previous draft and all installed libraries intact. */
        public Pack rename(String name)throws Exception{synchronized(LOCK){if(closed)throw IconManagementJson.error("图标暂存已关闭");Pack installed=existing(root,pack.id);if(installed!=null)throw new AlreadyInstalledException(installed);String label=displayName(name,null);Pack other=conflict(root,label,pack.id);if(other!=null)throw new NameConflictException(other);if(!label.equals(pack.name)){writeName(staging,label);pack=new Pack(pack.id,label,pack.manifest);}return pack;}}
        public Pack commit(BooleanSupplier canCommit)throws Exception{return commit(pack.name,canCommit);}
        public Pack commit(String name,BooleanSupplier canCommit)throws Exception{Pack result;synchronized(LOCK){if(closed||canCommit==null||!canCommit.getAsBoolean())throw IconManagementJson.error("导入已取消，图标包未保存");Pack installed=existing(root,pack.id);if(installed!=null)throw new AlreadyInstalledException(installed);rename(name);File target=directory(root,pack.id);
            int count=0;File[] files=root.listFiles();if(files==null)throw IconManagementJson.error("图标库不可读");for(File file:files)if(file.isDirectory()&&IconManagementSettings.validPackId(file.getName()))count++;if(count>=MAX_LIBRARY)throw IconManagementJson.error("最多保存 32 个图标库");
            android.system.Os.rename(staging.getAbsolutePath(),target.getAbsolutePath());boolean accepted=false;try{accepted=canCommit.getAsBoolean();}finally{if(!accepted)android.system.Os.rename(target.getAbsolutePath(),staging.getAbsolutePath());}if(!accepted)throw IconManagementJson.error("编辑状态已变化，图标包未保存");closed=true;result=pack;}changed(context);return result;}
        /** The caller explicitly chose the displayed installed library; no imported label is silently discarded. */
        public Pack useExisting(BooleanSupplier canCommit)throws Exception{Pack result;synchronized(LOCK){if(closed||canCommit==null||!canCommit.getAsBoolean())throw IconManagementJson.error("导入已取消，未使用图标库");result=existing(root,pack.id);if(result==null)throw IconManagementJson.error("原图标库已不存在，请重新导入");if(!canCommit.getAsBoolean())throw IconManagementJson.error("编辑状态已变化，未使用图标库");close();}changed(context);return result;}
        @java.lang.Override public void close(){synchronized(LOCK){if(!closed){closed=true;try{deleteManaged(root,staging);}catch(IOException ignored){}}}}
    }
    public static Pack rename(Context context,String id,String name)throws Exception{Pack result;synchronized(LOCK){File root=root(context);Pack pack=load(root,id);String label=displayName(name,null);Pack other=conflict(root,label,id);if(other!=null)throw new NameConflictException(other);writeName(directory(root,id),label);result=load(root,id);}changed(context);return result;}
    /** Prepare real inode/copy backups on the worker before attempting any preference or resource removal. */
    public static PreparedDeletion prepareDeletion(Context context,String id)throws IOException{synchronized(LOCK){
        File root=root(context),original=directory(root,id);if(!original.isDirectory())throw IconManagementJson.error("图标库已删除");
        List<File> files=new ArrayList<>(),directories=new ArrayList<>();snapshotTree(root,original,files,directories);long bytes=0;for(File file:files)bytes+=file.length();
        if(files.size()>IconManagementArchive.MAX_ENTRIES||bytes>IconManagementArchive.MAX_EXPANDED)throw IconManagementJson.error("图标删除备份超出预算");
        File backup=File.createTempFile("delete-",".pack",root);if(!backup.delete()||!backup.mkdir())throw IconManagementJson.error("无法创建图标删除备份");
        PreparedDeletion result=new PreparedDeletion(context,root,original,backup,directories);
        try{for(File file:files){File copy=new File(backup,original.toPath().relativize(file.toPath()).toString());File parent=copy.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw IconManagementJson.error("无法创建图标备份目录");
                Fingerprint before=new Fingerprint(file);linkOrCopy(file,copy);if(!before.same(new Fingerprint(file)))throw IconManagementJson.error("图标备份期间资源已变化");result.files.add(new RemovedFile(file,copy,before));}
            return result;
        }catch(IOException failure){result.close();throw failure;}
    }}
    private static void snapshotTree(File root,File path,List<File> files,List<File> directories)throws IOException{
        if(!path.getAbsoluteFile().equals(path.getCanonicalFile())||!path.toPath().startsWith(root.toPath())||path.equals(root))throw IconManagementJson.error("图标删除路径无效");
        if(path.isDirectory()){directories.add(path);File[] children=path.listFiles();if(children==null)throw IconManagementJson.error("图标目录不可读");Arrays.sort(children,Comparator.comparing(File::getName));for(File child:children)snapshotTree(root,child,files,directories);}
        else{requireFile(path,IconManagementArchive.MAX_ENTRY);files.add(path);}
    }
    private static void linkOrCopy(File original,File backup)throws IOException{
        try{android.system.Os.link(original.getAbsolutePath(),backup.getAbsolutePath());return;}catch(Exception unsupported){/* Copy remains bounded by the imported archive budget. */}
        try(InputStream input=new FileInputStream(original);FileOutputStream output=new FileOutputStream(backup)){copy(input,output,IconManagementArchive.MAX_ENTRY);output.getFD().sync();}
    }
    private static final class RemovedFile {final File original;File backup;final Fingerprint fingerprint;boolean removed;RemovedFile(File original,File backup,Fingerprint fingerprint){this.original=original;this.backup=backup;this.fingerprint=fingerprint;}}
    public static final class PreparedDeletion implements AutoCloseable {
        private final Context context;private final File root,original,backup;private final List<File> directories;private final List<RemovedFile> files=new ArrayList<>();private boolean closed,keepBackup;
        private PreparedDeletion(Context context,File root,File original,File backup,List<File> directories){Context app=context.getApplicationContext();this.context=app==null?context:app;this.root=root;this.original=original;this.backup=backup;this.directories=directories;}
        private void unchanged()throws IOException{List<File> current=new ArrayList<>(),dirs=new ArrayList<>();snapshotTree(root,original,current,dirs);if(current.size()!=files.size()||!dirs.equals(directories))throw IconManagementJson.error("图标库已变化，请重新删除");
            for(int i=0;i<files.size();i++){RemovedFile file=files.get(i);if(!current.get(i).equals(file.original)||!file.fingerprint.same(new Fingerprint(file.original)))throw IconManagementJson.error("图标库已变化，请重新删除");}}
        public void commit(BooleanSupplier canCommit,BooleanSupplier saveOptions,Runnable restoreOptions)throws Exception{
            Objects.requireNonNull(canCommit);Objects.requireNonNull(saveOptions);Objects.requireNonNull(restoreOptions);
            synchronized(LOCK){if(closed)throw IconManagementJson.error("图标删除暂存已关闭");boolean optionsAttempted=false,removalAttempted=false;
                try{if(!canCommit.getAsBoolean())throw IconManagementJson.error("编辑状态已变化，图标库未删除");unchanged();
                    optionsAttempted=true;if(!saveOptions.getAsBoolean())throw IconManagementJson.error("图标回退设置未保存，图标库未删除");if(!canCommit.getAsBoolean())throw IconManagementJson.error("编辑状态已变化，图标库未删除");
                    removalAttempted=true;for(RemovedFile file:files){android.system.Os.remove(file.original.getAbsolutePath());file.removed=true;}
                    for(int i=directories.size()-1;i>=0;i--)android.system.Os.remove(directories.get(i).getAbsolutePath());
                    if(!canCommit.getAsBoolean())throw IconManagementJson.error("编辑状态已变化，已恢复图标库");VERIFIED.remove(original.getAbsolutePath());
                }catch(Exception failure){Exception rollbackFailure=null;
                    if(removalAttempted)try{for(File directory:directories)if(!directory.isDirectory()&&!directory.mkdir())throw IconManagementJson.error("无法恢复图标目录");
                        for(RemovedFile file:files)if(file.removed||!file.original.exists()){try{android.system.Os.rename(file.backup.getAbsolutePath(),file.original.getAbsolutePath());file.backup=null;}catch(Exception rollback){if(rollbackFailure==null)rollbackFailure=rollback;else rollbackFailure.addSuppressed(rollback);}}}
                    catch(Exception rollback){if(rollbackFailure==null)rollbackFailure=rollback;else rollbackFailure.addSuppressed(rollback);}
                    if(optionsAttempted)try{restoreOptions.run();}catch(RuntimeException rollback){if(rollbackFailure==null)rollbackFailure=rollback;else rollbackFailure.addSuppressed(rollback);}
                    if(rollbackFailure!=null){keepBackup=true;IOException incomplete=new IOException("图标库删除失败，原资源或设置未完整恢复，备份已保留",failure);incomplete.addSuppressed(rollbackFailure);throw incomplete;}throw failure;
                }finally{close();}
            }changed(context);
        }
        @java.lang.Override public void close(){synchronized(LOCK){if(closed)return;closed=true;if(!keepBackup)try{deleteManaged(root,backup);}catch(IOException failure){ModuleDiagnostics.error("icon_packs","Icon deletion backup cleanup failed; managed backup retained",failure);}}}
    }
    public static void delete(Context context,String id)throws IOException{try(PreparedDeletion prepared=prepareDeletion(context,id)){prepared.commit(()->true,()->true,()->{});}catch(IOException failure){throw failure;}catch(Exception failure){throw new IOException("图标库未删除",failure);}}
    public static int clear(Context context)throws IOException{int count=0;synchronized(LOCK){File root=root(context);File[] files=root.listFiles();if(files==null)throw IconManagementJson.error("图标库不可读");for(File file:files)if(IconManagementSettings.validPackId(file.getName())||file.getName().startsWith("pending-")||file.getName().startsWith("source-")||file.getName().startsWith("delete-")){deleteManaged(root,file);count++;}VERIFIED.clear();}changed(context);return count;}
    /** Notify after publication and outside the library lock; a failed observer cannot undo durable resource writes. */
    private static void changed(Context context){try{context.getContentResolver().notifyChange(Uri.parse(URI_PREFIX),null);}catch(RuntimeException failure){ModuleDiagnostics.error("icon_packs","V3 resource publication succeeded; observer notification failed",failure);}}
    public static Bitmap preview(Context context,String id,String role,int size)throws Exception{return preview(context,id,role,size,role);}
    public static Bitmap preview(Context context,String id,String role,int size,String geometryRole)throws Exception{Pack pack=get(context,id);return previewFile(pack,directory(root(context),id),role,size,geometryRole);}
    private static Bitmap previewFile(Pack pack,File directory,String role,int size,String geometryRole)throws IOException{
        String selected=IconManagementSelection.sourceRole(pack.manifest,role);IconManagementManifest.Asset asset=selected==null?null:pack.manifest.icons.get(selected);
        if(asset==null)throw IconManagementJson.error("该套装没有此图案");
        IconAssetDecoder.Decoded decoded=IconAssetDecoder.decode(asset,read(new File(directory,asset.source),IconManagementArchive.MAX_ENTRY),size,size*size);
        IconAssetGeometry.Frame frame=IconAssetGeometry.resolve(decoded,asset,selected,geometryRole,pack.manifest,size,
                peer->read(new File(directory,peer.source),IconManagementArchive.MAX_ENTRY));
        return IconAssetDecoder.preview(decoded,frame.preview);
    }
    public static Bitmap previewPrepared(PreparedPack prepared,String role,int size)throws Exception{if(prepared==null)throw IconManagementJson.error("图标预览已关闭");return prepared.preview(role,size);}
    public static Bitmap previewPrepared(PreparedPack prepared,String role,int size,String geometryRole)throws Exception{if(prepared==null)throw IconManagementJson.error("图标预览已关闭");return prepared.preview(role,size,geometryRole);}
    private static String bundledFile(String kind)throws IOException{
        if("original".equals(kind))return "c17-rounded-27.zip";
        if("pui".equals(kind))return "pui-coloros17.zip";
        throw IconManagementJson.error("未知内置套装");
    }
    interface BundledSource { InputStream open(String path)throws IOException; }
    /** Asset availability only. Call on a worker; discovery never imports or applies a library. */
    public static Set<String> bundledKinds(Context context){
        if(context==null)return Collections.emptySet();
        return bundledKinds(path->context.getAssets().open(path));
    }
    static Set<String> bundledKinds(BundledSource source){
        if(source==null)return Collections.emptySet();
        Set<String> available=new LinkedHashSet<>();
        for(String kind:new String[]{"original","pui"}){
            try(InputStream input=source.open(FOLDER+'/'+bundledFile(kind))){if(input==null)continue;}
            catch(IOException|RuntimeException unavailable){continue;}
            available.add(kind);
        }
        return Collections.unmodifiableSet(available);
    }
    public static PreparedPack prepareBundled(Context context,String kind)throws Exception{
        String file=bundledFile(kind);
        File source=createSource(context);try{try(InputStream input=context.getAssets().open("icon-management-v3/"+file);FileOutputStream output=new FileOutputStream(source)){copy(input,output,IconManagementArchive.MAX_ARCHIVE);output.getFD().sync();}return prepare(context,source,"");}
        finally{source.delete();}
    }
    public static Pack installOriginal(Context context)throws Exception{return installBundled(context,"c17-rounded-27.zip","本版本尚未包含 C17示例库 离线套装");}
    public static Pack installPui(Context context)throws Exception{return installBundled(context,"pui-coloros17.zip","本版本尚未包含 PUI ColorOS 17 离线图标库");}
    // Programmatic ensure remains idempotent; interactive imports use status and an explicit existing-library choice.
    private static Pack installBundled(Context context,String file,String absent)throws Exception{File source=createSource(context);try{try(InputStream input=context.getAssets().open("icon-management-v3/"+file);FileOutputStream output=new FileOutputStream(source)){copy(input,output,IconManagementArchive.MAX_ARCHIVE);output.getFD().sync();}try(PreparedPack prepared=prepare(context,source,"")){try{return prepared.commit(()->true);}catch(AlreadyInstalledException installed){return prepared.useExisting(()->true);}}}catch(FileNotFoundException missing){throw IconManagementJson.error(absent);}finally{source.delete();}}
    public static Uri manifestUri(String id){if(!IconManagementSettings.validPackId(id))throw new IllegalArgumentException("Invalid v3 package ID");return Uri.parse(URI_PREFIX+id+"/manifest.json");}
    public static Uri assetUri(String id,String source){if(!IconManagementSettings.validPackId(id)||!IconManagementManifest.validAsset(source))throw new IllegalArgumentException("Invalid v3 asset");return Uri.parse(URI_PREFIX+id+'/'+source);}
    public static File providerFile(Context context,Uri uri)throws IOException{
        if(uri==null||!"content".equals(uri.getScheme())||!StatusBarSettings.AUTHORITY.equals(uri.getAuthority())||uri.getQuery()!=null||uri.getFragment()!=null)throw IconManagementJson.error("图标 Provider 地址无效");List<String> segments=uri.getPathSegments();if(segments.size()<3||!segments.get(0).equals("icon-management-v3"))throw IconManagementJson.error("未知 v3 图标资源");
        synchronized(LOCK){File root=root(context);String id=segments.get(1);Pack pack=load(root,id);File directory=directory(root,id);verifyPack(directory,id,pack.manifest);String path;
            if(segments.size()==3&&segments.get(2).equals("manifest.json"))path="manifest.json";
            else if(segments.size()==4&&segments.get(2).equals("assets")){path="assets/"+segments.get(3);if(!IconManagementManifest.validAsset(path)||!pack.manifest.sources().contains(path))throw IconManagementJson.error("未登记图标资源");}
            else throw IconManagementJson.error("未知 v3 图标资源");File file=new File(directory,path);requireFile(file,IconManagementArchive.MAX_ENTRY);return file;}
    }
    private static final class Fingerprint{final Object key;final long bytes;final java.nio.file.attribute.FileTime time;Fingerprint(File file)throws IOException{BasicFileAttributes attrs=java.nio.file.Files.readAttributes(file.toPath(),BasicFileAttributes.class);if(!attrs.isRegularFile())throw IconManagementJson.error("图标资源不是普通文件");key=attrs.fileKey();bytes=attrs.size();time=attrs.lastModifiedTime();}boolean same(Fingerprint other){return Objects.equals(key,other.key)&&bytes==other.bytes&&time.equals(other.time);}}
    private static final Map<String,Map<String,Fingerprint>> VERIFIED=new LinkedHashMap<String,Map<String,Fingerprint>>(16,.75f,true){@java.lang.Override protected boolean removeEldestEntry(Map.Entry<String,Map<String,Fingerprint>> entry){return size()>32;}};
    private static void verifyPack(File directory,String id,IconManagementManifest manifest)throws IOException{
        String key=directory.getCanonicalPath();Map<String,Fingerprint> current=new TreeMap<>();current.put("manifest.json",new Fingerprint(new File(directory,"manifest.json")));for(String path:manifest.sources()){File file=new File(directory,path);requireFile(file,IconManagementArchive.MAX_ENTRY);current.put(path,new Fingerprint(file));}
        Map<String,Fingerprint> previous=VERIFIED.get(key);boolean same=previous!=null&&previous.keySet().equals(current.keySet());if(same)for(String path:current.keySet())if(current.get(path).key==null||!current.get(path).same(previous.get(path))){same=false;break;}if(same)return;
        Map<String,String> hashes=new HashMap<>();for(IconManagementManifest.Asset asset:manifest.icons.values())hashes.put(asset.source,asset.sha256);for(IconManagementManifest.Transition transition:manifest.transitions.values())hashes.put(transition.source,transition.sha256);
        String actual=revision(manifest,manifest.bytes(),path->{byte[] bytes=read(new File(directory,path),IconManagementArchive.MAX_ENTRY);IconAssetDecoder.verify(bytes,hashes.get(path));return bytes;});if(!id.equals(actual))throw IconManagementJson.error("图标库整体摘要无效");
        for(String path:current.keySet())if(!current.get(path).same(new Fingerprint(new File(directory,path))))throw IconManagementJson.error("图标校验期间资源已变化");VERIFIED.put(key,current);
    }
    static byte[] read(File file,int limit)throws IOException{requireFile(file,limit);try(InputStream input=new FileInputStream(file)){return IconManagementArchive.readEntry(input,limit);}}
    private static void requireFile(File file,int limit)throws IOException{if(!file.isFile()||file.length()>limit||!file.getCanonicalFile().equals(file.getAbsoluteFile()))throw IconManagementJson.error("图标库资源无效");}
    private static void write(File file,byte[] bytes)throws IOException{if(bytes==null)throw IconManagementJson.error("资源缺失");try(FileOutputStream output=new FileOutputStream(file)){output.write(bytes);output.getFD().sync();}}
    private static void writeName(File directory,String name)throws IOException{File pending=File.createTempFile("name-",".json",directory);try{write(pending,IconManagementJson.encode(Collections.singletonMap("name",name)).getBytes(StandardCharsets.UTF_8));android.system.Os.rename(pending.getAbsolutePath(),new File(directory,"name.json").getAbsolutePath());}catch(Exception failure){throw new IOException("图标管理：名称未保存",failure);}finally{pending.delete();}}
    static void copy(InputStream input,OutputStream output,int limit)throws IOException{byte[] bytes=IconManagementArchive.readEntry(input,limit);output.write(bytes);}
    static void deleteManaged(File root,File target)throws IOException{File base=root.getCanonicalFile(),file=target.getCanonicalFile();if(!file.equals(target.getAbsoluteFile())||file.equals(base)||!file.toPath().startsWith(base.toPath()))throw IconManagementJson.error("图标清理路径越界");if(!target.exists())return;if(target.isDirectory()){File[] children=target.listFiles();if(children==null)throw IconManagementJson.error("图标目录不可读");for(File child:children)deleteManaged(root,child);}if(!target.delete())throw IconManagementJson.error("图标资源清理失败");}
    private IconManagementRepository(){ }
}
