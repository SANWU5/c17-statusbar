// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Looper;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SharedMemory;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

/** Optional KSU resource service. Background callers only; rendering never waits for it. */
final class CompanionCacheClient {
    static final int MAX_ENTRY=32*1024*1024;
    private static final String SOCKET="c17.performance.cache.v1";
    private static final AtomicLong RETRY_AFTER=new AtomicLong();
    private static volatile String lastState="not_connected";
    private static final AtomicLong hits=new AtomicLong(),misses=new AtomicLong(),writes=new AtomicLong();
    private CompanionCacheClient(){}

    static String key(String namespace,String version){
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            digest.update(namespace.getBytes(StandardCharsets.UTF_8));digest.update((byte)0);
            byte[] bytes=digest.digest(version.getBytes(StandardCharsets.UTF_8));char[] chars=new char[bytes.length*2];
            final char[] hex="0123456789abcdef".toCharArray();
            for(int i=0;i<bytes.length;i++){chars[i*2]=hex[(bytes[i]>>>4)&15];chars[i*2+1]=hex[bytes[i]&15];}return new String(chars);
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static boolean available(){
        return Build.VERSION.SDK_INT>=33&&Looper.myLooper()!=Looper.getMainLooper()&&SystemClock.uptimeMillis()>=RETRY_AFTER.get();
    }
    private static LocalSocket open()throws IOException{
        LocalSocket socket=new LocalSocket();
        try{
            // LocalSocket is lazy: setSoTimeout does not create its native FD.
            // Obtain the stream first so send/receive deadlines also precede connect.
            socket.getOutputStream();socket.setSoTimeout(1500);
            socket.connect(new LocalSocketAddress(SOCKET,LocalSocketAddress.Namespace.ABSTRACT));
            if(socket.getPeerCredentials().getUid()!=0)throw new IOException("Cache service identity rejected");return socket;}
        catch(IOException|RuntimeException failure){try{socket.close();}catch(IOException ignored){}throw failure;}
    }
    private static void failed(){lastState="unavailable";RETRY_AFTER.set(SystemClock.uptimeMillis()+30_000L);}
    private static void command(OutputStream out,String line)throws IOException{out.write(("C17C1 "+line+"\n").getBytes(StandardCharsets.US_ASCII));out.flush();}
    private static String line(InputStream in,int first)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(96);int c=first;
        while(c!='\n'){
            if(c<32||c>126||bytes.size()>=256)throw new IOException("Invalid cache response");
            bytes.write(c);c=in.read();
        }
        return new String(bytes.toByteArray(),StandardCharsets.US_ASCII);
    }
    static byte[] get(String key,int max){
        if(!available()||max<=0)return null;
        FileDescriptor[] received=null;
        try(LocalSocket socket=open()){
            command(socket.getOutputStream(),"GET "+key);InputStream in=socket.getInputStream();int first=in.read();
            received=socket.getAncillaryFileDescriptors();String response=line(in,first);
            if("MISS".equals(response)){misses.incrementAndGet();lastState="connected";return null;}
            String[] fields=response.split(" ");
            if(fields.length!=3||!"OK".equals(fields[0])||received==null||received.length!=1)throw new IOException("Invalid cache payload");
            int size=Integer.parseInt(fields[1]);long checksum=Long.parseLong(fields[2]);
            if(size<=0||size>max||size>MAX_ENTRY)throw new IOException("Cache size rejected");
            byte[] data=new byte[size];
            // A mapping has no shared file offset, so simultaneous/repeated consumers cannot race.
            try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.dup(received[0]);
                SharedMemory source=SharedMemory.fromFileDescriptor(descriptor)){
                if(source.getSize()!=size)throw new IOException("Cache mapping size rejected");
                java.nio.ByteBuffer mapping=source.mapReadOnly();
                try{mapping.get(data);}finally{SharedMemory.unmap(mapping);}
            }
            CRC32 crc=new CRC32();crc.update(data);if(crc.getValue()!=checksum)throw new IOException("Cache checksum rejected");
            hits.incrementAndGet();lastState="connected";return data;
        }catch(IOException|RuntimeException|android.system.ErrnoException unavailable){failed();return null;}
        finally{closeReceived(received);}
    }
    static boolean put(String key,byte[] data,boolean core){
        if(!available()||data==null||data.length==0||data.length>MAX_ENTRY)return false;
        try(LocalSocket socket=open()){
            OutputStream out=socket.getOutputStream();command(out,"PUT "+key+' '+data.length+' '+(core?1:0));out.write(data);out.flush();
            InputStream in=socket.getInputStream();String response=line(in,in.read());
            if(!"OK".equals(response)&&!"MEMORY".equals(response))throw new IOException("Cache publication rejected");
            writes.incrementAndGet();lastState="MEMORY".equals(response)?"memory_only":"connected";return true;
        }catch(IOException|RuntimeException unavailable){failed();return false;}
    }
    /** Warm an already verified immutable plugin resource on the asset worker, once per service lifetime. */
    static void seedFile(String key,java.io.File file,long size){
        if(!available()||size<=0||size>4*1024*1024)return;
        try(LocalSocket socket=open()){
            command(socket.getOutputStream(),"HAS "+key);InputStream in=socket.getInputStream();String response=line(in,in.read());
            if("OK".equals(response)){lastState="connected";return;}
            if(!"MISS".equals(response))throw new IOException("Cache presence rejected");
        }catch(IOException|RuntimeException unavailable){failed();return;}
        try(FileInputStream input=new FileInputStream(file)){
            byte[] bytes=new byte[(int)size];int offset=0;
            while(offset<bytes.length){int count=input.read(bytes,offset,bytes.length-offset);if(count<=0)return;offset+=count;}
            if(input.read()==-1)put(key,bytes,true);
        }catch(IOException|RuntimeException unavailable){/* Existing local source remains authoritative. */}
    }
    static void trim(boolean severe){
        if(!available())return;
        try(LocalSocket socket=open()){
            command(socket.getOutputStream(),"TRIM "+(severe?1:0));InputStream in=socket.getInputStream();
            if(!"OK".equals(line(in,in.read())))throw new IOException("Cache trim rejected");
        }catch(IOException|RuntimeException unavailable){failed();}
    }
    private static void closeReceived(FileDescriptor[] values){
        if(values==null)return;
        for(FileDescriptor fd:values)try{new FileInputStream(fd).close();}catch(IOException ignored){}
    }
    static void report(android.os.Bundle report){
        report.putString("companion_state",lastState);report.putLong("companion_hits",hits.get());
        report.putLong("companion_misses",misses.get());report.putLong("companion_writes",writes.get());
    }
}
