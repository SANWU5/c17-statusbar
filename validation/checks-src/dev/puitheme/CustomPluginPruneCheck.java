// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import io.github.libxposed.api.XposedInterface;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Replaced plugin copies are removed once nothing can read them, and nowhere else.
 * Host side: the real repository over a temporary library. Scope side: the real runtime
 * preparing provider assets into a temporary storage root.
 */
public final class CustomPluginPruneCheck {
    private static int checks;
    private interface Task {void run()throws Exception;}
    private static void equal(Object expected,Object actual,String message){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(message+": "+expected+" != "+actual);}
    private static void reject(Task task,String message)throws Exception {checks++;try{task.run();}catch(Exception expected){return;}throw new AssertionError("Accepted "+message);}
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static String sha(byte[] data)throws Exception {
        StringBuilder text=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(data))text.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return text.toString();
    }
    private static File temp(String prefix)throws Exception {return Files.createTempDirectory(prefix).toFile().getCanonicalFile();}
    private static void write(File file,byte[] data)throws Exception {
        File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new AssertionError("Cannot create "+parent);Files.write(file.toPath(),data);
    }
    private static List<String> names(File directory){String[] found=directory.list();if(found==null)return null;Arrays.sort(found);return Arrays.asList(found);}

    // ---------------------------------------------------------------- host library

    private static final String ID="prune.sample",OTHER="prune.other";
    private static JSONObject descriptor(String id,int version)throws Exception {
        return new JSONObject().put("id",id).put("name","清理检查插件").put("version","2.1.0_beta."+version).put("versionCode",version)
                .put("apiVersion",2).put("capabilities",new JSONArray().put("runtime"))
                .put("ui",new JSONArray().put(new JSONObject().put("type","switch").put("title","启用布局").put("key","layout").put("default",true)))
                .put("settings",new JSONObject()).put("hooks",new JSONArray()).put("runtime",new JSONObject().put("entry","scripts/runtime.js"));
    }
    /** The salt makes two packages of the same version differ, like a rebuilt development package. */
    private static Map<String,byte[]> bundle(String id,int version,String salt)throws Exception {
        JSONObject descriptor=descriptor(id,version),manifest=new JSONObject(descriptor.toString());
        manifest.remove("ui");manifest.remove("settings");manifest.remove("hooks");manifest.put("entry","main.js");
        Map<String,byte[]> entries=new LinkedHashMap<>();
        entries.put("plugin.json",bytes(manifest.toString()));entries.put("main.js",bytes("C17.define("+descriptor+");"));
        entries.put("scripts/runtime.js",bytes("var version="+version+";//"+salt));
        byte[] code=new byte[4096];new Random((id+version+salt).hashCode()).nextBytes(code);entries.put("classes/feature.dex",code);
        return entries;
    }
    private static byte[] zip(Map<String,byte[]> entries)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out,StandardCharsets.UTF_8)){for(Map.Entry<String,byte[]> entry:entries.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}
        return out.toByteArray();
    }
    private static CustomPluginRepository.Preview preview(CustomPluginRepository repo,Map<String,byte[]> entries)throws Exception {return repo.preview(new ByteArrayInputStream(zip(entries)),"plugin.zip");}
    private static CustomPluginRepository.Plugin find(CustomPluginRepository repo,String id)throws Exception {
        for(CustomPluginRepository.Plugin plugin:repo.list())if(plugin.id.equals(id))return plugin;throw new AssertionError("Plugin is not installed: "+id);
    }
    private static File generations(File library,String id){return new File(library,"plugin_data/"+id);}
    /** Every file of the package is present, unchanged, and accepted by the boot snapshot reader. */
    private static void intact(CustomPluginRepository repo,String id,Map<String,byte[]> entries,String why)throws Exception {
        CustomPluginRepository.Plugin plugin=find(repo,id);boolean same=true;
        for(Map.Entry<String,byte[]> entry:entries.entrySet()){
            File file=new File(plugin.runtimeBaseDir,entry.getKey());same&=file.isFile()&&Arrays.equals(entry.getValue(),Files.readAllBytes(file.toPath()));
        }
        equal(true,same,why);
        if(!plugin.enabled)return;
        Collection<File> staged=repo.frameworkPlans(false).assets.values();boolean readable=!staged.isEmpty();
        for(File file:staged)readable&=file.isFile();
        equal(true,readable,why+" (boot snapshot)");
    }

    private static void hostUpgrade()throws Exception {
        File library=temp("c17-prune-host-");CustomPluginRepository repo=new CustomPluginRepository(library);
        Map<String,byte[]> one=bundle(ID,1,"a"),two=bundle(ID,2,"a");
        CustomPluginRepository.Plugin first=repo.install(preview(repo,one),false);File old=new File(first.runtimeBaseDir);
        repo.setEnabled(ID,true);repo.updateState(ID,"layout",false);intact(repo,ID,one,"first import is complete");
        equal(1,names(generations(library,ID)).size(),"first import owns one generation");
        // A refused upgrade never reaches storage.
        CustomPluginRepository.Preview refused=preview(repo,two);
        reject(()->repo.install(refused,false),"unconfirmed upgrade");
        equal(Arrays.asList(old.getName()),names(generations(library,ID)),"refused upgrade creates and removes nothing");
        CustomPluginRepository.Plugin second=repo.install(refused,true);File current=new File(second.runtimeBaseDir);
        equal(false,old.exists(),"upgrade removes the replaced generation");
        equal(Arrays.asList(current.getName()),names(generations(library,ID)),"only the generation named by the library remains");
        equal(false,second.state.getBoolean("layout"),"upgrade keeps the user's settings");
        repo.setEnabled(ID,true);intact(repo,ID,two,"upgraded generation is complete");
        equal(current.getCanonicalPath(),find(new CustomPluginRepository(library),ID).runtimeBaseDir,"library reopens on the current generation");
        equal(current.getCanonicalPath(),repo.compiledPlan().getJSONArray("runtimes").getJSONObject(0).getString("baseDir"),"plan names the current generation");
        // Many imports of one plugin stay at one copy.
        for(int round=0;round<12;round++)repo.install(preview(repo,bundle(ID,3+round,"a")),true);
        equal(1,names(generations(library,ID)).size(),"repeated upgrades do not accumulate generations");
        repo.setEnabled(ID,true);intact(repo,ID,bundle(ID,14,"a"),"last of many upgrades is complete");
        repo.delete(ID);equal(false,generations(library,ID).exists(),"deleting the plugin still removes its whole tree");
    }

    private static void hostFailedImport()throws Exception {
        File files=temp("c17-prune-failed-"),library=new File(files,"custom_plugins");CustomPluginRepository repo=new CustomPluginRepository(library);
        Map<String,byte[]> one=bundle(ID,1,"a");
        File old=new File(repo.install(preview(repo,one),false).runtimeBaseDir);repo.setEnabled(ID,true);
        // The last representable revision makes the save fail after the new generation exists.
        File database=new File(library,"library.json");JSONObject data=new JSONObject(new String(Files.readAllBytes(database.toPath()),StandardCharsets.UTF_8));
        data.put("revision",Long.MAX_VALUE);Files.write(database.toPath(),bytes(data.toString()));byte[] saved=Files.readAllBytes(database.toPath());
        CustomPluginRepository.Preview failing=preview(repo,bundle(ID,2,"a"));
        reject(()->repo.install(failing,true),"import whose library save fails");
        equal(true,Arrays.equals(saved,Files.readAllBytes(database.toPath())),"failed import leaves the library untouched");
        equal(Arrays.asList(old.getName()),names(generations(library,ID)),"failed import removes only the generation it created");
        intact(repo,ID,one,"previous version survives a failed import");
        Context context=new Context(){@Override public File getFilesDir(){return files;}};
        Uri served=Uri.parse("content://"+StatusBarSettings.AUTHORITY+"/custom-plugins/assets/"+ID+"/1/classes/feature.dex");
        equal(true,Arrays.equals(one.get("classes/feature.dex"),Files.readAllBytes(CustomPluginRepository.providerFile(context,served).toPath())),"previous version is still served to its scopes");
        // With saving repaired, a package whose staged files disappeared still fails while copying.
        data.put("revision",7L);Files.write(database.toPath(),bytes(data.toString()));
        CustomPluginRepository.Preview vanished=preview(repo,bundle(ID,2,"b"));vanished.close();
        reject(()->repo.install(vanished,true),"import whose staged resources are gone");
        equal(Arrays.asList(old.getName()),names(generations(library,ID)),"aborted copy leaves no staging directory behind");
        intact(repo,ID,one,"previous version survives an aborted copy");
        // The same upgrade then succeeds, and only that success retires the old copy.
        Map<String,byte[]> two=bundle(ID,2,"a");File current=new File(repo.install(preview(repo,two),true).runtimeBaseDir);
        equal(false,old.exists(),"old generation is retired by the import that replaced it");
        equal(Arrays.asList(current.getName()),names(generations(library,ID)),"retry leaves a single generation");
        repo.setEnabled(ID,true);intact(repo,ID,two,"retried upgrade is complete");
    }

    private static void hostSameAndOlder()throws Exception {
        File library=temp("c17-prune-replace-");CustomPluginRepository repo=new CustomPluginRepository(library);
        File prior=new File(repo.install(preview(repo,bundle(ID,3,"first")),false).runtimeBaseDir);
        repo.updateState(ID,"layout",false);
        int[] versions={3,3,1,5};String[] salts={"second","third","older","newer"};
        for(int index=0;index<versions.length;index++){
            Map<String,byte[]> entries=bundle(ID,versions[index],salts[index]);
            CustomPluginRepository.Plugin replaced=repo.install(preview(repo,entries),true);File current=new File(replaced.runtimeBaseDir);
            equal(versions[index],replaced.versionCode,"same, older and newer versions can replace each other");
            equal(false,prior.equals(current),"every import receives its own generation");
            equal(false,prior.exists(),"the generation it replaced is removed ("+salts[index]+")");
            equal(Arrays.asList(current.getName()),names(generations(library,ID)),"one generation after replacement ("+salts[index]+")");
            equal(false,replaced.state.getBoolean("layout"),"replacement keeps the user's settings");
            repo.setEnabled(ID,true);intact(repo,ID,entries,"replacement is complete ("+salts[index]+")");
            equal(1,new CustomPluginRepository(library).list().size(),"library reopens after replacement");prior=current;
        }
        // A settings-only replacement has no resources at all: nothing of the old package is kept.
        String legacy="C17.define({id:'"+ID+"',name:'清理检查插件',version:'1.9',versionCode:9,apiVersion:1,capabilities:['settings'],"
                +"ui:[{type:'switch',key:'layout',title:'启用布局',default:true}],settings:{clock_enabled:true},hooks:[]});";
        CustomPluginRepository.Plugin plain=repo.install(repo.preview(new ByteArrayInputStream(bytes(legacy)),"plain.js"),true);
        equal("",plain.runtimeBaseDir,"a settings-only package names no resource directory");
        equal(new ArrayList<String>(),names(generations(library,ID)),"its predecessor's generation is retired as well");
    }

    private static void hostForeignEntries()throws Exception {
        File library=temp("c17-prune-foreign-");CustomPluginRepository repo=new CustomPluginRepository(library);
        repo.install(preview(repo,bundle(ID,1,"a")),false);
        Map<String,byte[]> otherEntries=bundle(OTHER,1,"a");File other=new File(repo.install(preview(repo,otherEntries),false).runtimeBaseDir);
        File folder=generations(library,ID),otherFolder=generations(library,OTHER);
        File legacy=new File(folder,"2"),staging=new File(folder,".install-"+UUID.randomUUID()),orphan=new File(folder,"1-"+UUID.randomUUID());
        File notes=new File(folder,"notes"),unlike=new File(folder,"4-not-a-generation"),upper=new File(folder,"1-"+UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT));
        File loose=new File(folder,"readme.txt"),looseGeneration=new File(folder,"6-"+UUID.randomUUID()),otherOrphan=new File(otherFolder,"1-"+UUID.randomUUID());
        for(File directory:new File[]{legacy,staging,orphan,notes,unlike,upper,otherOrphan})write(new File(directory,"classes/feature.dex"),new byte[]{1,2,3});
        write(loose,bytes("keep"));write(looseGeneration,bytes("keep"));
        File current=new File(repo.install(preview(repo,bundle(ID,2,"a")),true).runtimeBaseDir);
        equal(false,legacy.exists(),"a version-named directory of the earlier layout is retired");
        equal(false,staging.exists(),"an interrupted import's staging directory is retired");
        equal(false,orphan.exists(),"an unreferenced generation left by an earlier failure is retired");
        equal(true,new File(notes,"classes/feature.dex").isFile()&&new File(unlike,"classes/feature.dex").isFile()&&loose.isFile(),"names this library never creates are left alone");
        equal(true,new File(upper,"classes/feature.dex").isFile(),"a differently spelled name is not a generation");
        equal(true,looseGeneration.isFile(),"a plain file is not a generation whatever it is called");
        equal(true,current.isDirectory(),"current generation is kept beside foreign entries");
        equal(true,new File(otherOrphan,"classes/feature.dex").isFile(),"another plugin's folder is not swept by this import");
        intact(repo,OTHER,otherEntries,"another plugin's current generation is untouched");
        File otherCurrent=new File(repo.install(preview(repo,bundle(OTHER,2,"a")),true).runtimeBaseDir);
        equal(Arrays.asList(otherCurrent.getName()),names(otherFolder),"the other plugin's own import retires its own leftovers");
        equal(false,other.exists(),"the other plugin's replaced generation is retired too");
        // A link named like a generation is never followed.
        File outside=temp("c17-prune-outside-"),keep=new File(outside,"keep.txt");write(keep,bytes("keep"));
        File link=new File(folder,"9-"+UUID.randomUUID());boolean linked;
        try{Files.createSymbolicLink(link.toPath(),outside.toPath());linked=true;}catch(Exception unsupported){linked=false;}
        if(linked){
            File stale=new File(folder,"8-"+UUID.randomUUID());write(new File(stale,"x"),new byte[]{1});
            File next=new File(repo.install(preview(repo,bundle(ID,3,"a")),true).runtimeBaseDir);
            equal("keep",new String(Files.readAllBytes(keep.toPath()),StandardCharsets.UTF_8),"a linked directory outside the library survives");
            equal(true,next.isDirectory()&&!stale.exists()&&!current.exists(),"a refused link does not stop the other generations being retired");
        }
    }

    // ---------------------------------------------------------------- scoped process

    public static class Host {
        static volatile CountDownLatch entered,resume;
        public int one(){return 1;}
        public static void blocking()throws Exception {entered.countDown();if(!resume.await(5,TimeUnit.SECONDS))throw new AssertionError("blocked callback was not resumed");}
    }
    private static final class Owner extends Context {
        final File files;final String packageName;final Map<String,byte[]> served=new ConcurrentHashMap<>();
        volatile boolean offline;volatile CountDownLatch opened,gate;
        Owner(File files,String packageName){this.files=files;this.packageName=packageName;}
        @Override public File getFilesDir(){return files;}
        @Override public String getPackageName(){return packageName;}
        @Override public ContentResolver getContentResolver(){return new ContentResolver(){
            @Override public InputStream openInputStream(Uri uri)throws FileNotFoundException {
                CountDownLatch seen=opened,wait=gate;if(seen!=null)seen.countDown();
                if(wait!=null)try{wait.await(5,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
                byte[] data=offline?null:served.get(uri.toString());if(data==null)throw new FileNotFoundException(uri.toString());
                return new ByteArrayInputStream(data);
            }
        };}
    }
    private static final class Dispatcher extends Handler {
        final ConcurrentLinkedQueue<Runnable> ready=new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Delayed> delayed=new ConcurrentLinkedQueue<>();
        static final class Delayed {final Runnable task;final long at;Delayed(Runnable task,long delay){this.task=task;at=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(delay);}}
        Dispatcher(){super(Looper.getMainLooper());}
        @Override public boolean post(Runnable task){ready.add(task);return true;}
        @Override public boolean postDelayed(Runnable task,long millis){delayed.add(new Delayed(task,millis));return true;}
        @Override public void removeCallbacks(Runnable task){ready.remove(task);delayed.removeIf(item->item.task==task);}
        void drain(){
            for(Delayed item:new ArrayList<>(delayed))if(item.at<=System.nanoTime()&&delayed.remove(item))ready.add(item.task);
            Runnable task;int budget=10000;while((task=ready.poll())!=null){if(--budget==0)throw new AssertionError("Unbounded main dispatch");task.run();}
        }
        void until(BooleanSupplier condition,String what)throws Exception {
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
            do{drain();if(condition.getAsBoolean())return;Thread.sleep(5);}while(System.nanoTime()<end);
            throw new AssertionError("Timed out waiting for "+what);
        }
        /** Let asynchronous work that should not happen have its chance to happen. */
        void settle()throws Exception {for(int round=0;round<40;round++){drain();Thread.sleep(5);}}
    }
    private static final class Site {
        final Executable executable;final XposedInterface.Hooker callback;
        Site(Executable executable,XposedInterface.Hooker callback){this.executable=executable;this.callback=callback;}
    }
    /** One hooked process: the production runtime over a private files directory. */
    private static final class Scope {
        final Dispatcher main=new Dispatcher();final Owner owner;final CustomPluginRuntime runtime;final File root;final String application;
        final List<Site> hooks=new CopyOnWriteArrayList<>();final List<String> installed=new ArrayList<>();
        long revision;
        Scope(File files,String packageName,String processName)throws Exception {
            boolean systemUi=ModuleRuntimeStatus.SYSTEM_UI.equals(packageName);
            owner=new Owner(files,packageName);application=systemUi?null:packageName;
            runtime=systemUi?new CustomPluginRuntime(main):new CustomPluginRuntime(main,packageName,processName);
            root=systemUi?new File(files,"c17_plugins"):new File(files,"c17_plugins/processes/"+sha(bytes(processName)));
            runtime.configure(owner,new CustomPluginRuntime.Install(){
                public XposedInterface.HookHandle hook(Executable executable,XposedInterface.Hooker callback){
                    Site site=new Site(executable,callback);hooks.add(site);
                    return (XposedInterface.HookHandle)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.HookHandle.class},(proxy,method,args)->{
                        if(method.getName().equals("unhook")){hooks.remove(site);return null;}
                        if(method.getName().equals("getExecutable"))return executable;throw new AssertionError(method.getName());
                    });
                }
            },null);
            runtime.rememberLoader(Host.class.getClassLoader());runtime.setSafeMode(false);
        }
        File copy(String id,int version){return new File(new File(root,id),Integer.toString(version));}
        /** A two-file package for this version, published through the provider fixture. */
        JSONObject entry(String id,int version,String source)throws Exception {
            Map<String,byte[]> files=new LinkedHashMap<>();
            files.put("lib.js",bytes("exports.value="+version+";"));
            byte[] code=new byte[2048];new Random((id+version).hashCode()).nextBytes(code);files.put("classes/feature.dex",code);
            JSONArray assets=new JSONArray();
            for(Map.Entry<String,byte[]> file:files.entrySet()){
                assets.put(new JSONObject().put("path",file.getKey()).put("bytes",file.getValue().length).put("sha256",sha(file.getValue())));
                owner.served.put("content://"+StatusBarSettings.AUTHORITY+"/custom-plugins/"+(application==null?"assets/":"apps/"+application+"/")+id+"/"+version+"/"+file.getKey(),file.getValue());
            }
            JSONObject entry=new JSONObject().put("id",id).put("versionCode",version).put("source",source).put("state",new JSONObject())
                    .put("baseDir",application==null?"/data/user_de/0/host/private/unreadable":"").put("assets",assets);
            if(application!=null)entry.put("scopes",new JSONArray().put(application)).put("assetPackage",application).put("generation",version+"-fixture");
            if(!installed.contains(id))installed.add(id);
            return entry;
        }
        void plan(JSONObject...entries)throws Exception {
            JSONArray runtimes=new JSONArray(),inventory=new JSONArray();for(JSONObject entry:entries)runtimes.put(entry);
            for(String id:installed)inventory.put(new JSONObject().put("id",id).put("installedAt",1).put("preferences",new JSONArray()));
            equal(true,runtime.accept(new JSONObject().put("revision",++revision).put("runtimes",runtimes).put("installedPlugins",inventory)),"plan is accepted");
        }
        boolean running(String id,int version){
            JSONArray states=runtime.status();
            for(int i=0;i<states.length();i++){JSONObject state=states.optJSONObject(i);if(id.equals(state.optString("id")))return version==state.optInt("versionCode")&&"running".equals(state.optString("stage"));}
            return false;
        }
        void start(String id,int version,JSONObject...entries)throws Exception {plan(entries);main.until(()->running(id,version),id+" "+version+" to run");}
        int failures(String id){
            JSONArray states=runtime.status();
            for(int i=0;i<states.length();i++){JSONObject state=states.optJSONObject(i);if(id.equals(state.optString("id")))return state.optInt("assetFailures");}
            return 0;
        }
        String value(String id)throws Exception {return runtime.evaluateCommand(id,"String(C17.require('lib.js').value)+'@'+String(C17.pluginDir.getName())");}
        Object call(Executable executable,Object receiver,Object nativeResult)throws Throwable {
            List<Site> sites=new ArrayList<>();for(Site site:hooks)if(site.executable.equals(executable))sites.add(site);
            return next(sites,0,executable,receiver,nativeResult);
        }
        private Object next(List<Site> sites,int index,Executable executable,Object receiver,Object nativeResult)throws Throwable {
            if(index==sites.size())return nativeResult;
            XposedInterface.Chain chain=(XposedInterface.Chain)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.Chain.class},(proxy,method,input)->{
                switch(method.getName()){
                    case "getExecutable":return executable;
                    case "getThisObject":return receiver;
                    case "getArgs":return new ArrayList<>();
                    case "proceed":case "proceedWith":return next(sites,index+1,executable,receiver,nativeResult);
                    default:throw new AssertionError(method.getName());
                }
            });
            return sites.get(index).callback.intercept(chain);
        }
        void stop(){runtime.stop();}
    }
    /** A copy as a device leaves it: copied code plus the files the runtime compiled beside it. */
    private static File stale(File root,String id,String name)throws Exception {
        File version=new File(new File(root,id),name);
        write(new File(version,"classes.dex"),new byte[]{1,2,3});write(new File(version,"lib.js"),bytes("exports.value=-1;"));
        write(new File(version,"oat/arm64/classes.vdex"),new byte[]{4,5});write(new File(version,"oat/arm64/classes.odex"),new byte[]{6});
        return version;
    }
    private static boolean whole(File stale){return new File(stale,"classes.dex").isFile()&&new File(stale,"lib.js").isFile()&&new File(stale,"oat/arm64/classes.vdex").isFile()&&new File(stale,"oat/arm64/classes.odex").isFile();}
    /** A new process has opened nothing. The record does not exist in code that never removes copies. */
    private static void newProcess()throws Exception {
        try{
            Field opened=CustomPluginStorage.class.getDeclaredField("OPENED");opened.setAccessible(true);
            Collection<?> paths=(Collection<?>)opened.get(null);synchronized(paths){paths.clear();}
        }catch(NoSuchFieldException absent){/* Nothing to forget. */}
    }
    private static final String SYSTEM_UI=ModuleRuntimeStatus.SYSTEM_UI,SAMPLE="prune.scope",IDLE="prune.idle";

    private static void scopeLifetime()throws Exception {
        newProcess();File files=temp("c17-prune-scope-");Scope first=new Scope(files,SYSTEM_UI,SYSTEM_UI);
        File one=stale(first.root,SAMPLE,"1"),two=stale(first.root,SAMPLE,"2"),idle=stale(first.root,IDLE,"1"),idleNewer=stale(first.root,IDLE,"2");
        File unknown=stale(first.root,SAMPLE,"keep"),backup=stale(first.root,SAMPLE,"3.bak"),padded=stale(first.root,SAMPLE,"007"),note=new File(first.root,SAMPLE+"/note.txt");
        File numbered=new File(first.root,SAMPLE+"/9");write(note,bytes("keep"));write(numbered,bytes("keep"));
        File data=new File(first.root,"data/"+sha(bytes(SAMPLE))+"/value.json");write(data,bytes("{}"));
        first.installed.add(IDLE); // Installed, switched off: no runtime entry names a version for it.
        first.start(SAMPLE,3,first.entry(SAMPLE,3,"var ready=1;"));
        equal("3@3",first.value(SAMPLE),"current copy runs from its version directory");
        equal(false,one.exists()||two.exists(),"copies this process never opened are removed once the current one is complete");
        equal(true,whole(unknown)&&whole(backup)&&whole(padded)&&note.isFile(),"only exact version directories are considered");
        equal(true,numbered.isFile(),"a plain file is not a copy whatever it is called");
        equal(true,data.isFile()&&new File(first.root,"owners").isDirectory(),"saved plugin data and ownership records are not copies");
        equal(true,whole(idle)&&whole(idleNewer),"a switched-off plugin keeps its copies until it runs again");
        // Hot upgrade inside the same process: the copy it ran is not removed under it.
        File three=first.copy(SAMPLE,3);File again=stale(first.root,SAMPLE,"2");
        first.start(SAMPLE,4,first.entry(SAMPLE,4,"var ready=1;"));
        equal("4@4",first.value(SAMPLE),"hot upgrade runs the new copy");
        equal(true,new File(three,"lib.js").isFile()&&new File(three,"classes/feature.dex").isFile(),"a copy this process opened stays for its lifetime");
        equal(false,again.exists(),"an unopened leftover is removed during the hot upgrade");
        // Downgrade back to the copy it already has open.
        first.start(SAMPLE,3,first.entry(SAMPLE,3,"var ready=1;"));
        equal("3@3",first.value(SAMPLE),"hot downgrade runs the older copy again");
        equal(true,new File(first.copy(SAMPLE,4),"lib.js").isFile()&&new File(three,"lib.js").isFile(),"both copies opened by this process remain");
        // Switching the plugin off removes nothing.
        first.plan();first.main.settle();
        equal(true,new File(first.copy(SAMPLE,4),"lib.js").isFile()&&new File(three,"lib.js").isFile(),"switching off keeps the copies for the next start");
        first.stop();
        // The next process runs version 3 and has never opened version 4.
        newProcess();Scope second=new Scope(files,SYSTEM_UI,SYSTEM_UI);second.installed.add(IDLE);
        second.start(SAMPLE,3,second.entry(SAMPLE,3,"var ready=1;"));
        equal("3@3",second.value(SAMPLE),"next process runs the current copy");
        equal(false,second.copy(SAMPLE,4).exists(),"the next process removes the copy the previous one kept open");
        equal(true,whole(idle)&&whole(idleNewer)&&whole(unknown)&&data.isFile(),"the next process keeps everything else");
        // The switched-off plugin runs again: its other copies go, with nothing else.
        second.start(IDLE,3,second.entry(SAMPLE,3,"var ready=1;"),second.entry(IDLE,3,"var ready=1;"));
        equal("3@3",second.value(IDLE),"re-enabled plugin runs its current copy");
        equal(false,idle.exists()||idleNewer.exists(),"re-enabled plugin retires its own leftovers");
        equal(true,new File(second.copy(SAMPLE,3),"lib.js").isFile(),"another plugin's current copy is untouched");
        second.stop();
    }

    private static void scopeBusy()throws Throwable {
        newProcess();File files=temp("c17-prune-busy-");Scope scope=new Scope(files,SYSTEM_UI,SYSTEM_UI);
        String hook="C17.hook('"+Host.class.getName()+"','one',[],function(c){C17.findClass('"+Host.class.getName()+"').getDeclaredMethod('blocking').invoke(null,[]);return 9;});";
        scope.start(SAMPLE,5,scope.entry(SAMPLE,5,hook));
        File leftover=stale(scope.root,SAMPLE,"1"),five=scope.copy(SAMPLE,5);
        Host.entered=new CountDownLatch(1);Host.resume=new CountDownLatch(1);
        AtomicReference<Object> answer=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread callback=new Thread(()->{
            Looper.setCurrentForCheck(null);
            try{answer.set(scope.call(Host.class.getDeclaredMethod("one"),new Host(),7));}catch(Throwable problem){failure.set(problem);}
            finally{Looper.resetCurrentForCheck();}
        },"C17-prune-busy-callback");callback.setDaemon(true);callback.start();
        try{
            equal(true,Host.entered.await(3,TimeUnit.SECONDS),"old instance is inside a callback");
            scope.plan(scope.entry(SAMPLE,6,"var ready=1;"));scope.main.settle();
            equal(false,scope.running(SAMPLE,6),"replacement waits for the busy instance");
            equal(true,whole(leftover),"nothing is removed while the plugin is busy");
            equal(true,new File(five,"lib.js").isFile(),"the busy instance keeps its copy");
            equal(false,scope.copy(SAMPLE,6).exists(),"the replacement has not been copied yet");
        }finally{Host.resume.countDown();}
        callback.join(3000);equal(false,callback.isAlive(),"busy callback returns");if(failure.get()!=null)throw failure.get();
        equal(7,answer.get(),"retired callback falls back to the native result");
        scope.main.until(()->scope.running(SAMPLE,6),"replacement to run");
        equal("6@6",scope.value(SAMPLE),"replacement runs its own copy");
        equal(false,leftover.exists(),"the leftover is removed once the replacement is complete");
        equal(true,new File(five,"lib.js").isFile(),"the copy the retired instance ran stays until the process ends");
        scope.stop();
    }

    private static void scopeFailedPreparation()throws Exception {
        newProcess();File files=temp("c17-prune-unready-");Scope scope=new Scope(files,SYSTEM_UI,SYSTEM_UI);
        File one=stale(scope.root,SAMPLE,"1");
        // The second file of the package cannot be fetched: the copy stays incomplete.
        JSONObject entry=scope.entry(SAMPLE,7,"var ready=1;");
        String missing="content://"+StatusBarSettings.AUTHORITY+"/custom-plugins/assets/"+SAMPLE+"/7/classes/feature.dex";byte[] held=scope.owner.served.remove(missing);
        scope.plan(entry);scope.main.until(()->scope.failures(SAMPLE)>0,"asset failure");
        equal(true,new File(scope.copy(SAMPLE,7),"lib.js").isFile()&&!new File(scope.copy(SAMPLE,7),"classes/feature.dex").exists(),"the new copy is only partly written");
        equal(true,whole(one),"an incomplete copy removes nothing");
        scope.main.until(()->scope.failures(SAMPLE)>=3,"retry budget");scope.main.settle();
        equal(true,whole(one)&&!scope.running(SAMPLE,7),"exhausted retries still remove nothing");
        scope.owner.served.put(missing,held);
        scope.start(SAMPLE,7,new JSONObject(entry.toString()));
        equal("7@7",scope.value(SAMPLE),"completed copy runs");
        equal(false,one.exists(),"the leftover goes once the copy is complete");
        scope.stop();
        // A copy that finishes after its plugin was switched off does not sweep either.
        newProcess();File late=temp("c17-prune-late-");Scope retired=new Scope(late,SYSTEM_UI,SYSTEM_UI);File old=stale(retired.root,SAMPLE,"1");
        retired.owner.opened=new CountDownLatch(1);retired.owner.gate=new CountDownLatch(1);
        retired.plan(retired.entry(SAMPLE,8,"var ready=1;"));
        equal(true,retired.owner.opened.await(3,TimeUnit.SECONDS),"copy is in flight");
        retired.plan();retired.owner.gate.countDown();
        retired.main.until(()->new File(retired.copy(SAMPLE,8),"classes/feature.dex").isFile(),"in-flight copy to finish");retired.main.settle();
        equal(true,whole(old),"a copy that is no longer wanted removes nothing");
        retired.owner.gate=null;retired.start(SAMPLE,8,retired.entry(SAMPLE,8,"var ready=1;"));
        equal(false,old.exists(),"the wanted copy removes the leftover");
        retired.stop();
    }

    /** Guard, not a new behaviour: deleting a plugin still removes everything it owns, and never while a copy is being written. */
    private static void scopeUninstall()throws Exception {
        newProcess();File files=temp("c17-prune-uninstall-");Scope scope=new Scope(files,SYSTEM_UI,SYSTEM_UI);
        File old=stale(scope.root,SAMPLE,"1"),data=new File(scope.root,"data/"+sha(bytes(SAMPLE))+"/value.json");write(data,bytes("{}"));
        File other=stale(scope.root,IDLE,"1");scope.installed.add(IDLE);
        scope.owner.opened=new CountDownLatch(1);scope.owner.gate=new CountDownLatch(1);
        scope.plan(scope.entry(SAMPLE,9,"var ready=1;"));
        equal(true,scope.owner.opened.await(3,TimeUnit.SECONDS),"copy is in flight");
        scope.installed.remove(SAMPLE);scope.plan();scope.main.settle();
        equal(true,whole(old)&&data.isFile(),"ownership removal waits for the copy in flight");
        scope.owner.gate.countDown();
        scope.main.until(()->!new File(scope.root,SAMPLE).exists(),"the deleted plugin's copies to be removed");
        equal(false,data.exists(),"deleting the plugin removes its saved data as before");
        equal(true,whole(other),"another installed plugin keeps its copies");
        android.os.Bundle health=new android.os.Bundle();scope.runtime.health(health);
        equal(0L,health.getLong("custom_plugin_runtime_errors"),"copy cleanup and ownership removal never collide");
        scope.stop();
    }

    /**
     * One entry that cannot be removed stops nothing else. The file is held open (which refuses deletion on
     * Windows) inside a directory that is not writable (which refuses it elsewhere).
     */
    private static void stubbornEntry()throws Exception {
        File library=temp("c17-prune-stubborn-");CustomPluginRepository repo=new CustomPluginRepository(library);
        repo.install(preview(repo,bundle(ID,1,"a")),false);File folder=generations(library,ID);
        // Listed before and after the stubborn one, whichever order the file system uses.
        File early=new File(folder,"1-00000000-0000-4000-8000-000000000001"),stuck=new File(folder,"1-80000000-0000-4000-8000-000000000002");
        File late=new File(folder,"1-ffffffff-ffff-4fff-bfff-fffffffffff3");
        for(File directory:new File[]{early,stuck,late})write(new File(directory,"classes/feature.dex"),new byte[]{1,2,3});
        File locked=new File(stuck,"classes");Map<String,byte[]> two=bundle(ID,2,"a");
        try(java.io.FileInputStream held=new java.io.FileInputStream(new File(locked,"feature.dex"))){
            locked.setWritable(false);
            equal(2,repo.install(preview(repo,two),true).versionCode,"a generation that cannot be removed does not fail the import");
            equal(true,new File(locked,"feature.dex").isFile(),"the stubborn generation is left in place");
            equal(false,early.exists()||late.exists(),"it does not stop the other generations being retired");
            repo.setEnabled(ID,true);intact(repo,ID,two,"the current generation is complete beside it");
        }finally{locked.setWritable(true);}
        File current=new File(repo.install(preview(repo,bundle(ID,3,"a")),true).runtimeBaseDir);
        equal(Arrays.asList(current.getName()),names(folder),"what could not be removed is retried by the next import");

        newProcess();File files=temp("c17-prune-stubborn-scope-");Scope scope=new Scope(files,SYSTEM_UI,SYSTEM_UI);
        File first=stale(scope.root,SAMPLE,"1"),kept=stale(scope.root,SAMPLE,"2"),last=stale(scope.root,SAMPLE,"3");
        File compiled=new File(kept,"oat/arm64");
        try(java.io.FileInputStream held=new java.io.FileInputStream(new File(compiled,"classes.vdex"))){
            compiled.setWritable(false);
            scope.start(SAMPLE,4,scope.entry(SAMPLE,4,"var ready=1;"));
            equal("4@4",scope.value(SAMPLE),"a copy that cannot be removed does not stop the plugin starting");
            equal(true,new File(compiled,"classes.vdex").isFile(),"the stubborn copy is left in place");
            equal(false,first.exists()||last.exists(),"it does not stop the other copies being removed");
            android.os.Bundle health=new android.os.Bundle();scope.runtime.health(health);
            equal(0L,health.getLong("custom_plugin_runtime_errors"),"an unfinished sweep is logged, not counted as a plugin error");
        }finally{compiled.setWritable(true);}
        scope.stop();
        newProcess();Scope next=new Scope(files,SYSTEM_UI,SYSTEM_UI);
        next.start(SAMPLE,4,next.entry(SAMPLE,4,"var ready=1;"));
        equal(Arrays.asList("4"),names(new File(next.root,SAMPLE)),"the next process start retries what was left");
        next.stop();
    }

    private static void scopeBoundaries()throws Exception {
        // A plugin may be called "data": saved data of every plugin lives in that directory.
        newProcess();File files=temp("c17-prune-reserved-");Scope scope=new Scope(files,SYSTEM_UI,SYSTEM_UI);
        File saved=new File(scope.root,"data/"+sha(bytes(SAMPLE))+"/value.json");write(saved,bytes("{}"));
        File ledger=new File(scope.root,"owners/"+sha(bytes("absent.plugin"))+".txt");write(ledger,bytes("keep"));
        File reserved=stale(scope.root,"data","1"),sample=stale(scope.root,SAMPLE,"1");
        scope.installed.add(SAMPLE);
        scope.start("data",2,scope.entry("data",2,"var ready=1;"));
        equal("2@2",scope.value("data"),"a plugin with a reserved name still runs");
        equal(true,saved.isFile()&&ledger.isFile(),"saved data of other plugins is never treated as a copy");
        equal(true,whole(reserved)&&whole(sample),"reserved directories and other plugins are not swept");
        scope.stop();
        // Application scopes own one root per process.
        newProcess();File shared=temp("c17-prune-apps-");String launcher="com.example.launcher";
        Scope home=new Scope(shared,launcher,launcher);
        File mine=stale(home.root,SAMPLE,"1");
        File sibling=stale(new File(shared,"c17_plugins/processes/"+sha(bytes(launcher+":remote"))),SAMPLE,"1");
        File legacyRoot=stale(new File(shared,"c17_plugins"),SAMPLE,"1");
        home.start(SAMPLE,2,home.entry(SAMPLE,2,"var ready=1;"));
        equal("2@2",home.value(SAMPLE),"application scope runs from its process root");
        equal(false,mine.exists(),"application scope removes unopened copies in its own root");
        equal(true,whole(sibling)&&whole(legacyRoot),"another process's root is never swept");
        home.stop();
    }

    private interface Scenario {void run()throws Throwable;}
    /** No argument runs everything; scenario names run only those. Every scenario except the scopeUninstall guard fails without the cleanup. */
    public static void main(String[] args)throws Throwable {
        Map<String,Scenario> scenarios=new LinkedHashMap<>();
        scenarios.put("hostUpgrade",CustomPluginPruneCheck::hostUpgrade);scenarios.put("hostFailedImport",CustomPluginPruneCheck::hostFailedImport);
        scenarios.put("hostSameAndOlder",CustomPluginPruneCheck::hostSameAndOlder);scenarios.put("hostForeignEntries",CustomPluginPruneCheck::hostForeignEntries);
        scenarios.put("scopeLifetime",CustomPluginPruneCheck::scopeLifetime);scenarios.put("scopeBusy",CustomPluginPruneCheck::scopeBusy);
        scenarios.put("scopeFailedPreparation",CustomPluginPruneCheck::scopeFailedPreparation);scenarios.put("scopeBoundaries",CustomPluginPruneCheck::scopeBoundaries);
        scenarios.put("scopeUninstall",CustomPluginPruneCheck::scopeUninstall);scenarios.put("stubbornEntry",CustomPluginPruneCheck::stubbornEntry);
        List<String> wanted=Arrays.asList(args);if(!scenarios.keySet().containsAll(wanted))throw new IllegalArgumentException("Unknown scenario in "+wanted);
        for(Map.Entry<String,Scenario> scenario:scenarios.entrySet())if(wanted.isEmpty()||wanted.contains(scenario.getKey()))scenario.getValue().run();
        System.out.println("CustomPluginPruneCheck "+checks+" checks passed");
    }
}
