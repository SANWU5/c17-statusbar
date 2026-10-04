// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** User-owned font library, separate from the bounded network cache and current provider inode. */
public final class FontLibrary {
    private static final Object LOCK=new Object();
    private FontLibrary() { }
    public static final class Entry {
        public final String revision,name;
        public final FontCatalog.Entry catalog;
        Entry(String revision,String name){this.revision=revision;this.name=name;this.catalog=FontCatalog.forRevision(revision);}
    }
    private static File fonts(Context context){return new File(context.createDeviceProtectedStorageContext().getFilesDir(),"fonts");}
    private static File folder(Context context)throws IOException{
        File result=new File(fonts(context),"library");
        if(!result.isDirectory()&&!result.mkdirs())throw new IOException("无法创建字体库");
        return result;
    }
    private static void revision(String revision)throws IOException{
        if(revision==null||!revision.matches("[0-9a-f]{64}"))throw new IOException("字体记录无效");
    }
    public static String displayName(String name){
        String cleaned=name==null?"":name.replaceAll("[\\p{Cntrl}]","").trim();
        cleaned=cleaned.replaceFirst("(?i)\\.(ttf|otf|ttc)$","");
        if(cleaned.length()>120)cleaned=cleaned.substring(0,120);
        return cleaned.isEmpty()?"导入字体":cleaned;
    }
    /** Only sources sharing the deleted provider file fall back; unrelated settings/imports are untouched. */
    public static Map<String,String> deletionFallback(Map<String,?> values,String hash){
        LinkedHashMap<String,String> changes=new LinkedHashMap<>();
        if(values==null||hash==null||!hash.equals(values.get(StatusBarSettings.FONT_REVISION)))return changes;
        changes.put(StatusBarSettings.FONT_REVISION,"");changes.put(StatusBarSettings.FONT_NAME,"");
        if("custom".equals(values.get(StatusBarSettings.FONT_MODE)))changes.put(StatusBarSettings.FONT_MODE,"system");
        for(Map.Entry<String,String> entry:StatusBarSettings.STRING_DEFAULTS.entrySet()){
            String key=entry.getKey(),fallback=entry.getValue();
            if(!key.equals(StatusBarSettings.FONT_MODE)&&key.contains("font")
                    &&("system".equals(fallback)||"global".equals(fallback)||"native".equals(fallback))
                    &&"custom".equals(values.get(key)))changes.put(key,"system");
        }
        return changes;
    }
    /** Runs on a worker: the staged font has already passed header, length and Typeface validation. */
    public static void remember(Context context,FontRepository.PreparedFont prepared,String name)throws Exception{
        synchronized(LOCK){
            revision(prepared.revision);File directory=folder(context),target=new File(directory,prepared.revision+".font");
            File source=prepared.stagedFile();
            if(!target.isFile()||!prepared.revision.equals(FontFileValidator.sha256(target,96L*1024*1024,null)))linkOrCopy(source,target);
            writeName(directory,prepared.revision,displayName(name));
        }
    }
    /** Preserve old single-file installations before importing/selecting another source. */
    public static void retainCurrent(Context context,String selectedRevision,String name)throws Exception{
        if(selectedRevision==null||!selectedRevision.matches("[0-9a-f]{64}"))return;
        synchronized(LOCK){
            File current=new File(fonts(context),"custom.font");if(!current.isFile())return;
            File directory=folder(context),target=new File(directory,selectedRevision+".font");
            if(!target.isFile()){
                if(!selectedRevision.equals(FontFileValidator.sha256(current,96L*1024*1024,null)))return;
                linkOrCopy(current,target);
            }
            File metadata=new File(directory,selectedRevision+".name");
            if(!metadata.isFile())writeName(directory,selectedRevision,displayName(name));
        }
    }
    /** Lightweight listing is called only on the app worker, never from Compose or a native renderer. */
    public static List<Entry> entries(Context context)throws IOException{
        synchronized(LOCK){
            File directory=folder(context);File[] files=directory.listFiles(file->file.isFile()&&file.getName().matches("[0-9a-f]{64}\\.font"));
            ArrayList<Entry> result=new ArrayList<>();
            if(files!=null)for(File file:files){
                String hash=file.getName().substring(0,64);FontCatalog.Entry catalog=FontCatalog.forRevision(hash);
                String name=catalog==null?"导入字体 · "+hash.substring(0,8):catalog.displayName;
                File metadata=new File(directory,hash+".name");
                if(metadata.isFile()&&metadata.length()<=1024){
                    byte[] bytes=new byte[(int)metadata.length()];
                    try(FileInputStream input=new FileInputStream(metadata)){
                        int count=input.read(bytes);if(count==bytes.length)name=displayName(new String(bytes,StandardCharsets.UTF_8));
                    }
                }
                result.add(new Entry(hash,name));
            }
            result.sort(Comparator.comparing(entry->entry.name,String.CASE_INSENSITIVE_ORDER));
            return Collections.unmodifiableList(result);
        }
    }
    /** One-time migration of verified old downloads; later network-cache pruning cannot remove these. */
    public static void retainDownloads(Context context)throws Exception{
        synchronized(LOCK){
            File directory=folder(context),cache=new File(fonts(context),"catalog");
            for(FontCatalog.Entry entry:FontCatalog.entries()){
                File target=new File(directory,entry.sha256+".font");if(target.isFile())continue;
                File source=new File(cache,entry.id+"-"+entry.sha256+".font");if(!source.isFile())continue;
                try{FontFileValidator.validate(source,entry,null);}
                catch(IOException invalid){continue;}
                linkOrCopy(source,target);writeName(directory,entry.sha256,entry.displayName);
            }
        }
    }
    public static FontRepository.PreparedFont prepare(Context context,String hash)throws Exception{
        synchronized(LOCK){
            revision(hash);File source=new File(folder(context),hash+".font");
            if(!source.isFile())throw new IOException("字体文件已删除，请重新导入或下载");
            if(!hash.equals(FontFileValidator.sha256(source,96L*1024*1024,null)))throw new IOException("字体校验失败，请重新导入或下载");
            File temporary=File.createTempFile("library-",".font",fonts(context));
            android.system.Os.remove(temporary.getAbsolutePath());
            try{
                linkOrCopy(source,temporary);
                return new FontRepository.PreparedFont(temporary,new File(fonts(context),"custom.font"),hash);
            }catch(Exception failure){temporary.delete();throw failure;}
        }
    }
    public static File downloaded(Context context,FontCatalog.Entry entry)throws IOException{
        if(entry==null||FontCatalog.find(entry.id)!=entry)throw new IOException("字体来源无效");
        File stored=new File(folder(context),entry.sha256+".font");
        return stored.isFile()?stored:null;
    }
    public static void rename(Context context,String hash,String name)throws IOException{
        synchronized(LOCK){revision(hash);File directory=folder(context);
            if(!new File(directory,hash+".font").isFile())throw new IOException("字体已删除");
            writeName(directory,hash,displayName(name));
        }
    }
    /** Worker prepares inode backups; the main thread commits fallback and file removal together. */
    public static PreparedDeletion prepareDeletion(Context context,String hash,boolean current)throws Exception{
        synchronized(LOCK){
            revision(hash);File directory=folder(context);ArrayList<File> files=new ArrayList<>();
            if(current)files.add(new File(fonts(context),"custom.font"));
            files.add(new File(directory,hash+".font"));files.add(new File(directory,hash+".name"));
            FontCatalog.Entry catalog=FontCatalog.forRevision(hash);
            if(catalog!=null)files.add(new File(new File(fonts(context),"catalog"),catalog.id+"-"+hash+".font"));
            PreparedDeletion result=new PreparedDeletion();
            try{
                for(File file:files)if(file.isFile()){
                    File backup=File.createTempFile("delete-",".backup",directory);android.system.Os.remove(backup.getAbsolutePath());
                    try{linkOrCopy(file,backup);}
                    catch(Exception failure){backup.delete();throw failure;}
                    result.files.add(new RemovedFile(file,backup));
                }
                return result;
            }catch(Exception failure){result.close();throw failure;}
        }
    }
    private static final class RemovedFile {
        final File original;File backup;boolean removed,keep;
        RemovedFile(File original,File backup){this.original=original;this.backup=backup;}
    }
    public static final class PreparedDeletion implements AutoCloseable {
        private final List<RemovedFile> files=new ArrayList<>();private boolean closed;
        private PreparedDeletion() { }
        public void commit(BooleanSupplier canCommit,BooleanSupplier saveOptions,Runnable restoreOptions)throws Exception{
            Objects.requireNonNull(canCommit);Objects.requireNonNull(saveOptions);Objects.requireNonNull(restoreOptions);
            synchronized(LOCK){
                if(closed)throw new IOException("字体删除暂存已关闭");
                boolean optionsAttempted=false;
                try{
                    if(!canCommit.getAsBoolean())throw new IOException("编辑权限已变化，字体未删除");
                    optionsAttempted=true;if(!saveOptions.getAsBoolean())throw new IOException("字体回退未保存，字体未删除");
                    if(!canCommit.getAsBoolean())throw new IOException("编辑权限已变化，字体未删除");
                    for(RemovedFile file:files){if(file.original.exists())android.system.Os.remove(file.original.getAbsolutePath());file.removed=true;}
                    if(!canCommit.getAsBoolean())throw new IOException("编辑权限已变化，已恢复字体");
                }catch(Exception failure){
                    for(RemovedFile file:files)if(file.removed){
                        try{android.system.Os.rename(file.backup.getAbsolutePath(),file.original.getAbsolutePath());file.backup=null;}
                        catch(Exception rollback){file.keep=true;failure.addSuppressed(rollback);}
                    }
                    if(optionsAttempted)try{restoreOptions.run();}catch(RuntimeException rollback){failure.addSuppressed(rollback);}
                    throw failure;
                }finally{close();}
            }
        }
        @Override public void close(){synchronized(LOCK){closed=true;for(RemovedFile file:files)if(!file.keep&&file.backup!=null&&file.backup.exists())file.backup.delete();}}
    }
    private static void writeName(File directory,String hash,String name)throws IOException{
        File temporary=File.createTempFile("name-",".part",directory);
        try{
            try(FileOutputStream output=new FileOutputStream(temporary)){output.write(name.getBytes(StandardCharsets.UTF_8));output.getFD().sync();}
            android.system.Os.rename(temporary.getAbsolutePath(),new File(directory,hash+".name").getAbsolutePath());
        }catch(Exception failure){throw new IOException("字体名称保存失败",failure);}
        finally{if(temporary.exists())temporary.delete();}
    }
    private static void linkOrCopy(File source,File target)throws Exception{
        try{android.system.Os.link(source.getAbsolutePath(),target.getAbsolutePath());return;}
        catch(Exception unsupported){ /* Private storage may not support hard links. */ }
        File temporary=File.createTempFile("store-",".part",target.getParentFile());
        try{
            try(FileInputStream input=new FileInputStream(source);FileOutputStream output=new FileOutputStream(temporary)){
                byte[] buffer=new byte[32768];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);output.getFD().sync();
            }
            android.system.Os.rename(temporary.getAbsolutePath(),target.getAbsolutePath());
        }finally{if(temporary.exists())temporary.delete();}
    }
}
