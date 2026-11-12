package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Real files survive disable, disappear after uninstall, and cannot reappear on reinstall. */
public final class CustomPluginStorageCheck {
    static int checks;
    static void yes(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    static JSONObject entry(String id,long install,String... names)throws Exception{return new JSONObject().put("id",id).put("installedAt",install).put("preferences",new JSONArray(Arrays.asList(names)));}
    static String hash(String id)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8)))s.append(String.format("%02x",b&255));return s.toString();}
    static File data(Owner owner,String id)throws Exception{return new File(owner.root,"c17_plugins/data/"+hash(id)+"/value.json");}
    static void write(File file)throws Exception{file.getParentFile().mkdirs();Files.write(file.toPath(),new byte[]{1,2,3});}
    public static void main(String[] args)throws Exception{
        Owner owner=new Owner();JSONObject plugin=entry("test.plugin",1,"plugin_layout","plugin_pager");JSONArray installed=new JSONArray().put(plugin);
        CustomPluginStorage.reconcile(owner,installed,Collections.emptySet());File data=data(owner,"test.plugin"),asset=new File(owner.root,"c17_plugins/test.plugin/57/classes.dex");write(data);write(asset);
        owner.values.put("plugin_layout","draft");owner.values.put("plugin_pager","order");owner.values.put("unrelated","keep");
        CustomPluginStorage.reconcile(owner,installed,Collections.emptySet());
        yes(data.isFile()&&asset.isFile()&&"draft".equals(owner.values.get("plugin_layout")),"disabled installation retains its saved values without applying them");
        CustomPluginStorage.reconcile(owner,new JSONArray(),Collections.singleton("test.plugin"));
        yes(data.isFile()&&!owner.deleted.contains("plugin_layout"),"retiring callbacks restore native views before storage removal");
        File foreign=new File(owner.root,"c17_plugins/other.plugin/1/classes.dex");write(foreign);
        CustomPluginStorage.reconcile(owner,new JSONArray(),Collections.emptySet());
        yes(!data.exists()&&!asset.exists(),"uninstall removes only this plugin's runtime data and copied DEX assets");
        yes(owner.deleted.containsAll(Arrays.asList("plugin_layout","plugin_pager"))&&!owner.values.containsKey("plugin_layout"),"uninstall clears both preference caches and their files");
        yes(foreign.isFile()&&"keep".equals(owner.values.get("unrelated")),"other plugins and unrelated preferences survive deletion");
        yes(new File(owner.root,"c17_plugins/owners").list().length==0,"uninstall retires its ownership ledger");
        CustomPluginStorage.reconcile(owner,installed,Collections.emptySet());write(data);owner.values.put("plugin_layout","old");
        JSONArray replacement=new JSONArray().put(entry("test.plugin",2,"plugin_layout","plugin_pager"));
        CustomPluginStorage.reconcile(owner,replacement,Collections.singleton("test.plugin"));yes(data.exists(),"replacement waits for the preceding instance cleanup");
        CustomPluginStorage.reconcile(owner,replacement,Collections.emptySet());yes(!data.exists()&&!owner.values.containsKey("plugin_layout"),"missed delete followed by reinstall cannot inherit the preceding installation");
        write(data);JSONArray updated=new JSONArray().put(entry("test.plugin",2,"plugin_new"));CustomPluginStorage.reconcile(owner,updated,Collections.emptySet());yes(data.exists(),"same-installation version update preserves private data");
        owner.values.put("plugin_layout","retired setting");CustomPluginStorage.reconcile(owner,new JSONArray(),Collections.emptySet());yes(!owner.values.containsKey("plugin_layout")&&owner.deleted.contains("plugin_new"),"uninstall also removes retired preference declarations from earlier versions");
        boolean rejected=false;try{CustomPluginStorage.inventory(new JSONArray().put(entry("../outside",1)));}catch(IllegalArgumentException expected){rejected=true;}yes(rejected,"inventory refuses paths before touching storage");
        rejected=false;try{CustomPluginStorage.preferences(new JSONArray().put("../outside"));}catch(IllegalArgumentException expected){rejected=true;}yes(rejected,"preference ownership cannot escape the app preference directory");
        Owner upgrades=new Owner();
        for(int batch=0;batch<8;batch++){
            String[] names=new String[32];for(int i=0;i<32;i++)names[i]="version_pref_"+(batch*32+i);
            CustomPluginStorage.reconcile(upgrades,new JSONArray().put(entry("upgrade.plugin",9,names)),Collections.emptySet());
        }
        File ledger=new File(upgrades.root,"c17_plugins/owners/"+hash("upgrade.plugin")+".json");
        String complete=Files.readString(ledger.toPath());
        yes(new JSONObject(complete).getJSONArray("preferences").length()==256,"historical ownership can grow beyond one version's 32 declarations");
        yes(HistoricalResidualCleanup.preferenceNames(complete).size()==256,"cleanup uses the historical ownership limit");
        rejected=false;try{CustomPluginStorage.reconcile(upgrades,new JSONArray().put(entry("upgrade.plugin",9,"overflow_pref")),Collections.emptySet());}catch(Exception expected){rejected=true;}
        yes(rejected&&complete.equals(Files.readString(ledger.toPath())),"overflow is rejected before replacing a readable ledger");
        CustomPluginStorage.reconcile(upgrades,new JSONArray().put(entry("upgrade.plugin",9,"version_pref_255")),Collections.emptySet());
        yes(complete.equals(Files.readString(ledger.toPath())),"full ledger remains readable after rejected upgrade");
        CustomPluginStorage.reconcile(upgrades,new JSONArray(),Collections.emptySet());
        yes(upgrades.deleted.size()==256&&!ledger.exists(),"uninstall removes every retired preference at the boundary");
        String[] tooMany=new String[33];for(int i=0;i<33;i++)tooMany[i]="declared_pref_"+i;
        rejected=false;try{CustomPluginStorage.inventory(new JSONArray().put(entry("current.plugin",1,tooMany)));}catch(IllegalArgumentException expected){rejected=true;}
        yes(rejected,"current manifest declaration limit is still 32");
        System.out.println("CustomPluginStorageCheck "+checks+" checks passed");
    }
    static final class Owner extends Context {
        final File root;final Map<String,String> values=new HashMap<>();final Set<String> deleted=new HashSet<>();
        Owner()throws Exception{root=Files.createTempDirectory("c17-plugin-storage-check-").toFile();}
        @Override public Context createDeviceProtectedStorageContext(){return this;}
        @Override public File getFilesDir(){return root;}
        @Override public boolean deleteSharedPreferences(String name){values.remove(name);deleted.add(name);return true;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){
            return (SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)->{
                if(m.getName().equals("edit"))return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(e,method,args)->{
                    if(method.getName().equals("clear")){values.remove(name);return e;}if(method.getName().equals("commit"))return true;throw new UnsupportedOperationException(method.getName());});
                throw new UnsupportedOperationException(m.getName());
            });
        }
    }
}
