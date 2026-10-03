// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import me.zhanghai.android.libarchive.Archive;
import me.zhanghai.android.libarchive.ArchiveEntry;

/** Bounded archive reader. Entries are never extracted using archive-owned paths. */
final class IconPackArchive {
    static final int MAX_ARCHIVE=8*1024*1024,MAX_ENTRY=1024*1024,MAX_EXPANDED=16*1024*1024,MAX_ENTRIES=256;
    private IconPackArchive(){ }
    static Map<String,byte[]> read(File archive)throws IOException{
        if(!archive.isFile()||archive.length()>MAX_ARCHIVE)throw new IOException("图标包最多 8 MB");
        byte[] header=new byte[8];int read;
        try(InputStream input=new FileInputStream(archive)){read=input.read(header);}
        if(read>=4&&header[0]=='P'&&header[1]=='K')try(InputStream input=new FileInputStream(archive)){return zip(input);}
        if(read>=7&&header[0]=='R'&&header[1]=='a'&&header[2]=='r'&&header[3]=='!'&&header[4]==0x1a&&header[5]==7)
            return rar(archive);
        throw new IOException("请选择 ZIP 或 RAR 图标包");
    }
    static Map<String,byte[]> zip(InputStream source)throws IOException{
        Collector collector=new Collector();
        try(ZipInputStream input=new ZipInputStream(source,StandardCharsets.UTF_8)){
            ZipEntry entry;
            while((entry=input.getNextEntry())!=null){
                collector.header(entry.getName(),entry.isDirectory(),entry.getSize());
                if(!entry.isDirectory())collector.put(entry.getName(),readEntry(input,MAX_ENTRY));
                input.closeEntry();
            }
        }
        return collector.result();
    }
    private static Map<String,byte[]> rar(File source)throws IOException{
        long archive=0;
        try{
            archive=Archive.readNew();Archive.readSupportFilterNone(archive);
            Archive.readSupportFormatRar(archive);Archive.readSupportFormatRar5(archive);
            Archive.readOpenFileName(archive,source.getAbsolutePath().getBytes(StandardCharsets.UTF_8),8192);
            Collector collector=new Collector();long entry;
            while((entry=Archive.readNextHeader(archive))!=0){
                String name=ArchiveEntry.pathnameUtf8(entry);
                if(name==null)throw new IOException("RAR 文件名须使用 UTF-8");
                int type=ArchiveEntry.filetype(entry);
                if(type!=ArchiveEntry.AE_IFREG&&type!=ArchiveEntry.AE_IFDIR)throw new IOException("图标包不允许符号链接或特殊文件");
                if(ArchiveEntry.hardlink(entry)!=null||ArchiveEntry.symlink(entry)!=null||ArchiveEntry.isEncrypted(entry))
                    throw new IOException("图标包不允许链接或加密文件");
                boolean directory=type==ArchiveEntry.AE_IFDIR;
                collector.header(name,directory,ArchiveEntry.size(entry));
                if(directory){Archive.readDataSkip(archive);continue;}
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                ByteBuffer buffer=ByteBuffer.allocateDirect(8192);
                for(;;){
                    buffer.clear();Archive.readData(archive,buffer);int count=buffer.position();
                    if(count==0)break;
                    if(bytes.size()+count>MAX_ENTRY)throw new IOException("图标包单文件最多 1 MB");
                    byte[] block=new byte[count];buffer.flip();buffer.get(block);bytes.write(block);
                }
                collector.put(name,bytes.toByteArray());
            }
            return collector.result();
        }catch(IOException failure){throw failure;}
        catch(Exception failure){throw new IOException("RAR 无法读取；请使用未加密的单卷 RAR4/RAR5",failure);}
        catch(LinkageError unavailable){throw new IOException("当前设备不支持 RAR 解码，请使用 ZIP",unavailable);}
        finally{if(archive!=0)try{Archive.free(archive);}catch(Exception ignored){}}
    }
    static byte[] readEntry(InputStream input,int limit)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){
            if(bytes.size()+count>limit)throw new IOException("图标包文件超过允许大小");
            bytes.write(buffer,0,count);
        }
        return bytes.toByteArray();
    }
    static boolean safePath(String name,boolean directory){
        if(name==null||name.length()>192||name.indexOf('\\')>=0||name.indexOf(':')>=0||name.startsWith("/")
                ||name.matches("(?s).*\\p{Cntrl}.*"))return false;
        String path=directory&&name.endsWith("/")?name.substring(0,name.length()-1):name;
        if(path.isEmpty())return false;
        for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))return false;
        return true;
    }
    static final class Collector {
        final Map<String,byte[]> entries=new LinkedHashMap<>();final Set<String> names=new HashSet<>();
        int count,total;
        void header(String name,boolean directory,long size)throws IOException{
            if(++count>MAX_ENTRIES||!safePath(name,directory)||!names.add(name))throw new IOException("图标包路径、文件数量或重复文件无效");
            if(size>MAX_ENTRY||size< -1)throw new IOException("图标包单文件最多 1 MB");
        }
        void put(String name,byte[] bytes)throws IOException{
            total+=bytes.length;
            if(total>MAX_EXPANDED)throw new IOException("图标包展开后最多 16 MB");
            entries.put(name,bytes);
        }
        Map<String,byte[]> result()throws IOException{
            String prefix=null;
            for(String path:entries.keySet())if(path.equals("manifest.json")||path.endsWith("/manifest.json")){
                if(prefix!=null)throw new IOException("图标包只能包含一个 manifest.json");
                prefix=path.substring(0,path.length()-"manifest.json".length());
            }
            if(prefix==null)throw new IOException("图标包缺少 manifest.json");
            // GitHub source ZIPs may wrap the complete pack in one folder. Never flatten arbitrary assets.
            Map<String,byte[]> pack=new LinkedHashMap<>();
            for(Map.Entry<String,byte[]> entry:entries.entrySet())if(entry.getKey().startsWith(prefix)){
                String relative=entry.getKey().substring(prefix.length());
                if(relative.equals("manifest.json")||IconPackManifest.validAsset(relative))pack.put(relative,entry.getValue());
            }
            return pack;
        }
    }
}
