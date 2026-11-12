// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Installation ownership survives disable/restart, so deleting a disabled plugin also removes its data. */
final class CustomPluginStorage {
    static final int MAX_OWNED_PREFERENCES=256, MAX_LEDGER_BYTES=32768;
    private CustomPluginStorage() { }
    static void preferences(JSONArray names) {
        preferences(names,32);
    }
    private static void preferences(JSONArray names,int maximum) {
        if(names==null)return;
        Set<String> seen=new LinkedHashSet<>();
        if(names.length()>maximum)throw new IllegalArgumentException("插件偏好文件过多");
        for(int i=0;i<names.length();i++){
            Object name=names.opt(i);
            if(!(name instanceof String)||!((String)name).matches("[a-zA-Z][a-zA-Z0-9_.-]{2,119}")||!seen.add((String)name))
                throw new IllegalArgumentException("插件偏好文件名无效或重复");
        }
    }
    static Map<String,JSONObject> inventory(JSONArray values)throws Exception {
        return inventory(values,32);
    }
    static Map<String,JSONObject> historicalInventory(JSONArray values)throws Exception {
        return inventory(values,MAX_OWNED_PREFERENCES);
    }
    private static Map<String,JSONObject> inventory(JSONArray values,int maximum)throws Exception {
        Map<String,JSONObject> result=new LinkedHashMap<>();Set<String> claimed=new LinkedHashSet<>();
        if(values==null)throw new IllegalArgumentException("Missing installed plugin inventory");
        for(int i=0;i<values.length();i++){
            JSONObject entry=values.getJSONObject(i);String id=entry.getString("id");
            if(!id.matches("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*")||id.length()>80||entry.getLong("installedAt")<0||result.put(id,entry)!=null)
                throw new IllegalArgumentException("Invalid installed plugin identity");
            preferences(entry.getJSONArray("preferences"),maximum);
            JSONArray names=entry.getJSONArray("preferences");for(int j=0;j<names.length();j++)if(!claimed.add(names.getString(j)))throw new IllegalArgumentException("插件偏好文件归属冲突");
        }
        return result;
    }
    static void reconcile(Context context,JSONArray values,Set<String> busy)throws Exception {
        reconcile(context,new File(context.createDeviceProtectedStorageContext().getFilesDir(),"c17_plugins"),values,busy);
    }
    /** External processes own distinct roots; the legacy SystemUI layout stays unchanged. */
    static void reconcile(Context context,File root,JSONArray values,Set<String> busy)throws Exception {
        Map<String,JSONObject> installed=inventory(values);
        Context owner=context.createDeviceProtectedStorageContext();
        File ledgers=new File(root,"owners");
        confined(owner.getFilesDir(),root);confined(root,ledgers);
        if(!ledgers.isDirectory()&&!ledgers.mkdirs())throw new IOException("Cannot create plugin ownership directory");
        File[] files=ledgers.listFiles();if(files==null)throw new IOException("Cannot read plugin ownership directory");
        Map<File,JSONObject> previousRecords=new LinkedHashMap<>();
        for(File file:files){
            if(!file.getName().matches("[a-f0-9]{64}\\.json"))continue;
            confined(ledgers,file);
            if(file.length()>MAX_LEDGER_BYTES)throw new IOException("Plugin ownership record exceeds the supported size");
            JSONObject previous=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
            String id=previous.getString("id");historicalInventory(new JSONArray().put(previous));
            if(!file.getName().equals(hash(id)+".json"))throw new IOException("Plugin ownership identity mismatch");
            previousRecords.put(file,previous);
            JSONObject next=installed.get(id);
            if(next!=null&&next.getLong("installedAt")==previous.getLong("installedAt")){
                // Retired preferences still belong to this installation until it is deleted.
                Set<String> names=new LinkedHashSet<>();for(JSONArray array:new JSONArray[]{previous.getJSONArray("preferences"),next.getJSONArray("preferences")})
                    for(int i=0;i<array.length();i++)names.add(array.getString(i));
                JSONObject merged=new JSONObject(next.toString()).put("preferences",new JSONArray(names));
                historicalInventory(new JSONArray().put(merged));
                installed.put(id,merged);
            }
        }
        // Validate the complete future ledger before deleting or replacing any owned data.
        Set<String> owned=new LinkedHashSet<>();
        for(JSONObject next:installed.values()){
            if(next.toString().getBytes(StandardCharsets.UTF_8).length>MAX_LEDGER_BYTES)
                throw new IOException("Plugin ownership record exceeds the supported size");
            JSONArray names=next.getJSONArray("preferences");for(int i=0;i<names.length();i++)owned.add(names.getString(i));
        }
        if(owned.size()>MAX_OWNED_PREFERENCES)throw new IOException("Plugin historical preference inventory exceeds the supported size");
        for(Map.Entry<File,JSONObject> record:previousRecords.entrySet()){
            File file=record.getKey();JSONObject previous=record.getValue();String id=previous.getString("id");
            JSONObject next=installed.get(id);
            if(next!=null&&next.getLong("installedAt")==previous.getLong("installedAt"))continue;
            if(busy.contains(id))continue; // Exact old callbacks must finish restoring before files disappear.
            JSONArray names=previous.getJSONArray("preferences");
            for(int i=0;i<names.length();i++){
                String name=names.getString(i);boolean claimedElsewhere=false;
                for(Map.Entry<String,JSONObject> current:installed.entrySet())if(!current.getKey().equals(id)){
                    JSONArray declared=current.getValue().getJSONArray("preferences");for(int j=0;j<declared.length();j++)if(name.equals(declared.getString(j)))claimedElsewhere=true;
                }
                if(claimedElsewhere)continue;
                if(!owner.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit()||!owner.deleteSharedPreferences(name))
                    throw new IOException("Cannot remove plugin preferences: "+name);
            }
            remove(root,new File(new File(root,"data"),hash(id)));
            if(!id.equals("data")&&!id.equals("owners"))remove(root,new File(root,id));
            if(!file.delete())throw new IOException("Cannot retire plugin ownership record");
        }
        for(Map.Entry<String,JSONObject> entry:installed.entrySet()){
            if(busy.contains(entry.getKey()))continue;
            File file=new File(ledgers,hash(entry.getKey())+".json");
            byte[] bytes=entry.getValue().toString().getBytes(StandardCharsets.UTF_8);
            if(file.isFile()&&java.util.Arrays.equals(Files.readAllBytes(file.toPath()),bytes))continue;
            File temp=File.createTempFile("owner-",".tmp",ledgers);
            try{Files.write(temp.toPath(),bytes);Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            finally{if(temp.exists())temp.delete();}
        }
    }
    /** Canonical paths of the version copies this process has read from. Loaded code can keep one mapped until the process exits. */
    private static final Set<String> OPENED=new java.util.HashSet<>();
    /** Called before the first file of a copy is read, verified or written. */
    static void opened(File version)throws IOException {
        String path=version.getCanonicalPath();
        // Waits for a sweep in progress, so a copy is never verified while it is being removed.
        synchronized(OPENED){OPENED.add(path);}
    }
    /**
     * A plugin's copied resources are a cache of files the host still holds. Once the current copy is
     * complete, sibling versions this process never opened cannot back anything running here: remove them.
     * A copy this process did open (running, retiring, or still mapped by a retained hook) is left for a
     * later process. Only directories named like a version are considered, never data or ownership records.
     */
    static int pruneVersions(File root,String id,String current)throws IOException {
        if(id.equals("data")||id.equals("owners"))return 0; // Same reserved names as ownership removal.
        File plugin=new File(root,id);confined(root,plugin);
        if(Files.isSymbolicLink(plugin.toPath()))return 0;
        File[] versions=plugin.listFiles();if(versions==null)return 0;
        int removed=0;IOException incomplete=null;
        synchronized(OPENED){
            for(File version:versions){
                String name=version.getName();
                if(name.equals(current)||!name.matches("[1-9][0-9]{0,9}")||Files.isSymbolicLink(version.toPath())||!version.isDirectory())continue;
                try{
                    if(OPENED.contains(version.getCanonicalPath()))continue;
                    remove(root,version);removed++;
                }catch(IOException retained){if(incomplete==null)incomplete=retained;}
            }
        }
        if(incomplete!=null)throw incomplete;
        return removed;
    }
    private static String hash(String id)throws Exception {
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));StringBuilder result=new StringBuilder();
        for(byte value:bytes)result.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return result.toString();
    }
    private static void confined(File root,File file)throws IOException {
        if(!file.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("Plugin storage escapes its owner");
    }
    private static void remove(File root,File file)throws IOException {
        confined(root,file);if(!file.exists())return;
        if(Files.isSymbolicLink(file.toPath()))throw new IOException("Linked plugin storage is not owned");
        if(file.isDirectory()){File[] children=file.listFiles();if(children==null)throw new IOException("Cannot read plugin storage");for(File child:children)remove(root,child);}
        if(!file.delete())throw new IOException("Cannot remove plugin storage");
    }
}
