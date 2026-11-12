#!/usr/bin/env python3
"""Exercise the production cache client with deterministic Android boundary doubles.

This covers protocol, fallback and ownership, not Android socket/SELinux support.
"""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JDK = Path(os.environ.get("C17_JDK", ROOT / ".local-toolchain/jdk/jdk-17.0.20.1+1"))
SOURCES = {
"android/os/Build.java": "package android.os; public class Build { public static class VERSION { public static int SDK_INT=33; } }",
"android/os/Looper.java": "package android.os; public class Looper { static final Looper MAIN=new Looper(); public static boolean main; public static Looper getMainLooper(){return MAIN;} public static Looper myLooper(){return main?MAIN:null;} }",
"android/os/SystemClock.java": "package android.os; public class SystemClock { public static long time=100000; public static long uptimeMillis(){return time;} }",
"android/os/Bundle.java": "package android.os; public class Bundle extends java.util.HashMap<String,Object> { public void putString(String k,String v){put(k,v);} public void putLong(String k,long v){put(k,v);} }",
"android/system/ErrnoException.java": "package android.system; public class ErrnoException extends Exception {}",
"android/os/ParcelFileDescriptor.java": """package android.os;
public class ParcelFileDescriptor implements java.io.Closeable {
 public static int owned; public java.io.FileDescriptor fd; boolean detached;
 public static ParcelFileDescriptor dup(java.io.FileDescriptor fd){ParcelFileDescriptor p=new ParcelFileDescriptor();p.fd=fd;owned++;return p;}
 public void close(){if(!detached){owned--;detached=true;}}
} """,
"android/os/SharedMemory.java": """package android.os;
public class SharedMemory implements AutoCloseable {
 public static boolean fail; ParcelFileDescriptor descriptor;
 public static SharedMemory fromFileDescriptor(ParcelFileDescriptor fd){if(fail)throw new IllegalArgumentException("not shared memory");SharedMemory s=new SharedMemory();s.descriptor=fd;return s;}
 public int getSize(){return android.net.LocalSocket.payload.length;}
 public java.nio.ByteBuffer mapReadOnly()throws android.system.ErrnoException{return java.nio.ByteBuffer.wrap(android.net.LocalSocket.payload).asReadOnlyBuffer();}
 public static void unmap(java.nio.ByteBuffer b){}
 public void close(){descriptor.close();}
} """,
"android/net/LocalSocketAddress.java": "package android.net; public class LocalSocketAddress { public enum Namespace { ABSTRACT } public LocalSocketAddress(String s,Namespace n){} }",
"android/net/Credentials.java": "package android.net; public class Credentials { public int getUid(){return LocalSocket.uid;} }",
"android/net/LocalSocket.java": """package android.net;
public class LocalSocket implements java.io.Closeable {
 public static int opens,closes,uid; public static boolean fail; public static String response="MISS\\n", command="";
 public static byte[] payload; public static java.io.File file; public static java.io.FileDescriptor lastFd;
 java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); java.io.FileInputStream source; boolean created;
 public LocalSocket(){opens++;}
 public void setSoTimeout(int value)throws java.io.IOException{if(!created)throw new java.io.IOException("socket not created");}
 public void connect(LocalSocketAddress address)throws java.io.IOException{created=true;if(fail)throw new java.io.IOException("absent");}
 public Credentials getPeerCredentials(){return new Credentials();}
 public java.io.OutputStream getOutputStream(){created=true;return out;}
 public java.io.InputStream getInputStream(){command=out.toString();return new java.io.ByteArrayInputStream(response.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
 public java.io.FileDescriptor[] getAncillaryFileDescriptors()throws java.io.IOException{
   if(payload==null)return null;source=new java.io.FileInputStream(file);lastFd=source.getFD();return new java.io.FileDescriptor[]{lastFd};
 }
 public void close()throws java.io.IOException{closes++;}
} """,
"dev/puitheme/CompanionCacheClientCheck.java": r"""package dev.puitheme;
import android.net.LocalSocket;
import android.os.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.CRC32;
public class CompanionCacheClientCheck {
 static int checks; static void ok(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static String key=CompanionCacheClient.key("test","revision");
 static void fixture(String response,byte[] payload)throws Exception{
   SystemClock.time+=40000;LocalSocket.uid=0;LocalSocket.fail=false;SharedMemory.fail=false;
   LocalSocket.response=response;LocalSocket.payload=payload;LocalSocket.lastFd=null;Looper.main=false;Build.VERSION.SDK_INT=33;
   if(payload!=null){LocalSocket.file=Files.createTempFile("c17-client-",".bin").toFile();LocalSocket.file.deleteOnExit();Files.write(LocalSocket.file.toPath(),payload);}
 }
 static String header(byte[] b){CRC32 crc=new CRC32();crc.update(b);return "OK "+b.length+" "+crc.getValue()+"\n";}
 public static void main(String[] args)throws Exception{
   ok(key.matches("[0-9a-f]{64}"),"version key");ok(!key.equals(CompanionCacheClient.key("other","revision")),"namespace isolation");
   fixture("MISS\n",null);int start=LocalSocket.opens;Looper.main=true;
   ok(CompanionCacheClient.get(key,10)==null,"main fallback");ok(!CompanionCacheClient.put(key,new byte[]{1},true),"main put skipped");
   CompanionCacheClient.trim(true);ok(LocalSocket.opens==start,"no render-thread IPC");
   Looper.main=false;Build.VERSION.SDK_INT=32;ok(CompanionCacheClient.get(key,10)==null&&LocalSocket.opens==start,"older API fallback");
   fixture("MISS\n",null);ok(CompanionCacheClient.get(key,10)==null,"cache miss");ok(LocalSocket.command.equals("C17C1 GET "+key+"\n"),"GET protocol");
   byte[] bytes={1,2,3,4};fixture(header(bytes),bytes);byte[] first=CompanionCacheClient.get(key,4);
   ok(Arrays.equals(bytes,first),"verified shared payload");ok(!LocalSocket.lastFd.valid(),"received descriptor closed");ok(ParcelFileDescriptor.owned==0,"mapping descriptor released");
   first[0]=99;ok(Arrays.equals(bytes,CompanionCacheClient.get(key,4)),"repeated read has independent offset and result");
   fixture("OK 4 0\n",bytes);ok(CompanionCacheClient.get(key,4)==null,"CRC rejects corruption");
   ok(!LocalSocket.lastFd.valid()&&ParcelFileDescriptor.owned==0,"failed checksum releases ownership");
   start=LocalSocket.opens;ok(CompanionCacheClient.get(key,4)==null&&LocalSocket.opens==start,"failure backoff");
   fixture(header(bytes),bytes);ok(CompanionCacheClient.get(key,3)==null,"caller budget enforced");ok(!LocalSocket.lastFd.valid(),"oversize closes FD");
   fixture(header(bytes),bytes);SharedMemory.fail=true;ok(CompanionCacheClient.get(key,4)==null,"invalid mapping fallback");ok(ParcelFileDescriptor.owned==0&&!LocalSocket.lastFd.valid(),"mapping constructor failure closes duplicate");
   fixture(header(bytes),bytes);LocalSocket.uid=10000;ok(CompanionCacheClient.get(key,4)==null,"untrusted server rejected");
   fixture("OK 4 1\n",null);ok(CompanionCacheClient.get(key,4)==null,"missing descriptor rejected");
   for(String bad:new String[]{"", "OK\n", "OK NaN 1\n", "OK 0 1\n", new String(new char[300]).replace('\0','x')}){
     fixture(bad,null);ok(CompanionCacheClient.get(key,4)==null,"invalid response fallback");
   }
   fixture("OK\n",null);ok(CompanionCacheClient.put(key,bytes,true),"publication succeeds");ok(LocalSocket.command.startsWith("C17C1 PUT "+key+" 4 1\n"),"PUT includes size and priority");
   fixture("MEMORY\n",null);ok(CompanionCacheClient.put(key,bytes,false),"disk failure can retain bounded memory");
   fixture("CONFLICT\n",null);ok(!CompanionCacheClient.put(key,bytes,false),"immutable version conflict rejected");
   fixture("OK\n",null);CompanionCacheClient.trim(true);ok(LocalSocket.command.equals("C17C1 TRIM 1\n"),"critical trim sent");
   fixture("OK\n",null);CompanionCacheClient.seedFile(key,new java.io.File("not-read"),4);ok(LocalSocket.command.equals("C17C1 HAS "+key+"\n"),"warm file skips redundant read/publication");
   fixture("MISS\n",null);LocalSocket.fail=true;ok(CompanionCacheClient.get(key,4)==null,"absent service fallback");
   ok(LocalSocket.opens==LocalSocket.closes,"all sockets closed");
   System.out.println("Companion cache client: "+checks+" checks passed (Android transport remains device-only)");
 }
} """
}

with tempfile.TemporaryDirectory(prefix="c17-cache-client-") as temporary:
    folder = Path(temporary)
    files = []
    for relative, source in SOURCES.items():
        file = folder / relative
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(source, encoding="utf-8")
        files.append(str(file))
    files.append(str(ROOT / "app/src/main/java/dev/puitheme/CompanionCacheClient.java"))
    extension = ".exe" if os.name == "nt" else ""
    subprocess.run([str(JDK / "bin" / ("javac" + extension)), "-encoding", "UTF-8", "-d", str(folder), *files], check=True)
    subprocess.run([str(JDK / "bin" / ("java" + extension)), "-cp", str(folder), "dev.puitheme.CompanionCacheClientCheck"], check=True)
