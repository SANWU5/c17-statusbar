// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.os.ParcelFileDescriptor;
import io.github.libxposed.service.XposedService;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/** Stages read-only plugin boot files before the host's shared settings revision is committed. */
final class CustomPluginFrameworkPublisher {
    static final String KEY="_c17_plugin_boot_v1", WITHDRAWN="unavailable";
    static final String PLAN_PREFIX="c17-plugin-plan-", ASSET_PREFIX="c17-plugin-asset-";
    static final int FORMAT=1, MAX_PLAN_BYTES=16*1024*1024, MAX_METADATA_CHARS=64*1024;

    private CustomPluginFrameworkPublisher(){}

    static final class Snapshot {
        final long revision;
        final boolean safeMode;
        final Map<String,JSONObject> plans;
        final Map<String,File> assets;

        Snapshot(long revision,boolean safeMode,Map<String,JSONObject> plans,Map<String,File> assets) {
            this.revision=revision;
            this.safeMode=safeMode;
            this.plans=plans;
            this.assets=assets;
        }
    }

    static final class Staged {
        final String metadata;
        final int scopes;
        final Set<String> referenced;
        final Set<String> obsolete;

        Staged(String metadata,int scopes,Set<String> referenced,Set<String> obsolete) {
            this.metadata=metadata;
            this.scopes=scopes;
            this.referenced=referenced;
            this.obsolete=obsolete;
        }
    }

    static Staged stage(XposedService service,Snapshot snapshot,Set<String> verified)throws Exception {
        Set<String> existing=new HashSet<>(Arrays.asList(service.listRemoteFiles()));
        Set<String> referenced=new HashSet<>();
        JSONObject plans=new JSONObject();
        for(Map.Entry<String,File> asset:snapshot.assets.entrySet()) {
            String name=asset.getKey();
            File file=asset.getValue();
            try(InputStream input=new FileInputStream(file)) {
                stageFile(service,existing,verified,name,name.substring(ASSET_PREFIX.length()),file.length(),input);
            }
            referenced.add(name);
        }
        for(Map.Entry<String,JSONObject> plan:snapshot.plans.entrySet()) {
            byte[] bytes=plan.getValue().toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>MAX_PLAN_BYTES)throw new IOException("Plugin boot plan exceeds the framework copy limit");
            String digest=hex(MessageDigest.getInstance("SHA-256").digest(bytes));
            String name=PLAN_PREFIX+digest;
            stageFile(service,existing,verified,name,digest,bytes.length,new ByteArrayInputStream(bytes));
            referenced.add(name);
            plans.put(plan.getKey(),new JSONObject().put("file",name).put("bytes",bytes.length).put("sha256",digest));
        }
        String metadata=new JSONObject().put("format",FORMAT).put("revision",snapshot.revision)
                .put("safeMode",snapshot.safeMode).put("plans",plans).toString();
        if(metadata.length()>MAX_METADATA_CHARS)throw new IOException("Plugin boot index exceeds the framework copy limit");
        existing.removeAll(referenced);
        existing.removeIf(name->!name.matches("c17-plugin-(plan|asset)-[a-f0-9]{64}"));
        return new Staged(metadata,plans.length(),referenced,existing);
    }

    private static void stageFile(XposedService service,Set<String> existing,Set<String> verified,
                                  String name,String digest,long bytes,InputStream source)throws Exception {
        if(existing.contains(name)) {
            if(verified.contains(name)||matches(service,name,digest,bytes))return;
            // An interrupted writer may leave a partial file before it publishes any pointer.
            // Recreate only a file whose bytes cannot be consumed by an integrity-checking reader.
            if(!service.deleteRemoteFile(name))throw new IOException("Could not replace an invalid framework file");
        }
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        long written=0L;
        ParcelFileDescriptor fd=service.openRemoteFile(name);
        try(ParcelFileDescriptor.AutoCloseOutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(fd)) {
            byte[] buffer=new byte[16384];
            int count;
            while((count=source.read(buffer))!=-1) {
                if(count==0)continue;
                written+=count;
                if(written>bytes)throw new IOException("Plugin boot source grew during publication");
                hash.update(buffer,0,count);
                out.write(buffer,0,count);
            }
            if(written!=bytes||!digest.equals(hex(hash.digest())))throw new IOException("Plugin boot source integrity check failed");
            out.flush();
            fd.getFileDescriptor().sync();
        }
    }

    private static boolean matches(XposedService service,String name,String digest,long bytes)throws Exception {
        ParcelFileDescriptor fd=service.openRemoteFile(name);
        try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[16384];
            long size=0L;
            int count;
            while((count=input.read(buffer))!=-1) {
                if(count==0)continue;
                size+=count;
                if(size>bytes)return false;
                hash.update(buffer,0,count);
            }
            return size==bytes&&digest.equals(hex(hash.digest()));
        }
    }

    private static String hex(byte[] bytes) {
        final String alphabet="0123456789abcdef";
        StringBuilder hex=new StringBuilder(bytes.length*2);
        for(byte value:bytes)hex.append(alphabet.charAt((value>>4)&15)).append(alphabet.charAt(value&15));
        return hex.toString();
    }
}
