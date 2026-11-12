package dev.puitheme;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real API 2 admission/storage tests: runtime remains inert until the runtime consumer starts it. */
public final class CustomPluginRuntimePackageCheck {
    private static int checks;
    private interface Task {void run()throws Exception;}
    private static void equal(Object expected,Object actual,String message){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(message+": "+expected+" != "+actual);}
    private static void reject(Task task,String message)throws Exception {checks++;try{task.run();}catch(Exception expected){return;}throw new AssertionError("Accepted "+message);}
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static JSONObject descriptor(String id,int version,JSONObject runtime)throws Exception {
        return new JSONObject().put("id",id).put("name","完整运行插件").put("version","2.1.0_beta."+version).put("versionCode",version)
                .put("apiVersion",2).put("capabilities",new JSONArray().put("runtime"))
                .put("ui",new JSONArray().put(new JSONObject().put("type","switch").put("title","启用布局").put("key","layout").put("default",true)))
                .put("settings",new JSONObject()).put("hooks",new JSONArray()).put("runtime",runtime);
    }
    private static String main(JSONObject descriptor){return "C17.define("+descriptor.toString()+");";}
    private static Map<String,byte[]> bundle(String id,int version,String runtime)throws Exception {
        JSONObject descriptor=descriptor(id,version,new JSONObject().put("entry","scripts/runtime.js"));
        JSONObject manifest=new JSONObject(descriptor.toString());manifest.remove("ui");manifest.remove("settings");manifest.remove("hooks");manifest.put("entry","main.js");
        Map<String,byte[]> entries=new LinkedHashMap<>();entries.put("plugin.json",bytes(manifest.toString()));entries.put("main.js",bytes(main(descriptor)));entries.put("scripts/runtime.js",bytes(runtime));return entries;
    }
    private static byte[] zip(Map<String,byte[]> entries)throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out,StandardCharsets.UTF_8)){for(Map.Entry<String,byte[]> entry:entries.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}return out.toByteArray();}
    private static CustomPluginRepository.Preview preview(CustomPluginRepository repo,Map<String,byte[]> entries)throws Exception {return repo.preview(new ByteArrayInputStream(zip(entries)),"plugin.zip");}
    private static String largeRuntime(int size){char[] source=new char[size];Arrays.fill(source,' ');return "/*"+new String(source)+"*/ throw new Error('runtime must never execute during import');";}
    public static void main(String[] args)throws Exception {
        File directory=Files.createTempDirectory("c17-runtime-package-").toFile();CustomPluginRepository repo=new CustomPluginRepository(directory);
        String runtime=largeRuntime(300000);Map<String,byte[]> entries=bundle("runtime.complete",1,runtime);
        byte[] binary=new byte[1200000];new java.util.Random(17).nextBytes(binary);entries.put("classes/feature.dex",binary);entries.put("lib/extension.jar",new byte[]{0,1,2,3});
        for(int index=0;index<40;index++)entries.put("images/icon-"+index+".png",new byte[]{(byte)index,0,(byte)255});
        CustomPluginRepository.Preview first=preview(repo,entries);equal(2,first.descriptor.getInt("apiVersion"),"API 2 accepted");
        equal("scripts/runtime.js",first.descriptor.getJSONObject("runtime").getString("entry"),"runtime declaration retained");
        equal(0,repo.list().size(),"preview never installs or executes runtime");
        CustomPluginRepository.Plugin installed=repo.install(first,false);equal(false,installed.enabled,"new runtime disabled until confirmed enable");
        equal(runtime,installed.runtimeSource,"runtime over 256 KiB preserved exactly");
        File resource=new File(installed.runtimeBaseDir,"classes/feature.dex");equal(true,Arrays.equals(binary,Files.readAllBytes(resource.toPath())),"binary resource over 1 MiB preserved");
        equal(45,new File(installed.runtimeBaseDir).exists()?new JSONObject(new String(Files.readAllBytes(new File(directory,"library.json").toPath()),StandardCharsets.UTF_8)).getJSONObject("plugins").getJSONObject(installed.id).getJSONArray("runtimeAssets").length():-1,"more than 32 resources preserved");
        equal(0,repo.compiledPlan().getJSONArray("runtimes").length(),"disabled runtime not distributed");
        repo.setEnabled(installed.id,true);JSONObject active=repo.compiledPlan().getJSONArray("runtimes").getJSONObject(0);
        equal(installed.id,active.getString("id"),"runtime identity in plan");equal(runtime,active.getString("source"),"untruncated execution source in plan");
        equal(installed.runtimeBaseDir,active.getString("baseDir"),"private resource root in plan");equal(45,active.getJSONArray("assets").length(),"resource transfer manifest complete");
        equal(true,active.getJSONArray("assets").getJSONObject(0).getString("sha256").matches("[a-f0-9]{64}"),"resource hashes supplied");
        repo.updateState(installed.id,"layout",false);equal(false,repo.compiledPlan().getJSONArray("runtimes").getJSONObject(0).getJSONObject("state").getBoolean("layout"),"complete independent settings distributed");
        CustomPluginRepository.Preview upgrade=preview(repo,bundle(installed.id,2,"throw new Error('version two must remain inert');"));
        reject(()->repo.install(upgrade,false),"unconfirmed runtime upgrade");equal(runtime,repo.list().get(0).runtimeSource,"failed upgrade preserves old runtime");
        CustomPluginRepository.Plugin upgraded=repo.install(upgrade,true);equal(false,upgraded.enabled,"upgrade returns to disabled");equal(false,upgraded.state.getBoolean("layout"),"upgrade preserves independent user settings");
        equal(false,resource.exists(),"replaced version assets are retired once the library names the upgrade");
        String inlineSource=largeRuntime(350000);
        JSONObject inline=descriptor("runtime.inline",1,new JSONObject().put("source",inlineSource));
        CustomPluginRepository.Plugin inlinePlugin=repo.install(repo.preview(new ByteArrayInputStream(bytes(main(inline))),"inline.js"),false);
        equal(inlineSource,inlinePlugin.runtimeSource,"single JS inline runtime preserved");equal(true,new File(inlinePlugin.runtimeBaseDir).isDirectory(),"inline runtime still has private data root");
        JSONObject future=descriptor("runtime.future",1,new JSONObject().put("source","void 0")).put("apiVersion",3);
        reject(()->repo.preview(new ByteArrayInputStream(bytes(main(future))),"future.js"),"unknown future API");
        JSONObject noCapability=descriptor("runtime.undeclared",1,new JSONObject().put("source","void 0")).put("capabilities",new JSONArray());
        reject(()->repo.preview(new ByteArrayInputStream(bytes(main(noCapability))),"undeclared.js"),"unreviewed runtime capability");
        JSONObject invalidRuntime=descriptor("runtime.invalid",1,new JSONObject().put("entry","runtime.js").put("source","void 0"));
        reject(()->repo.preview(new ByteArrayInputStream(bytes(main(invalidRuntime))),"invalid.js"),"ambiguous runtime entry");
        Map<String,byte[]> traversal=bundle("runtime.escape",1,"void 0");traversal.put("../escape.dex",new byte[1]);reject(()->preview(repo,traversal),"resource traversal");
        Map<String,byte[]> missing=bundle("runtime.missing",1,"void 0");missing.remove("scripts/runtime.js");reject(()->preview(repo,missing),"missing runtime entry");
        Map<String,byte[]> crc=bundle("runtime.crc",1,"void 0");byte[] damaged=zip(crc);damaged[30]='X';reject(()->repo.preview(new ByteArrayInputStream(damaged),"damaged.zip"),"central/local name mismatch");
        CustomPluginRepository.Preview cancelled=preview(repo,bundle("runtime.cancel",1,"void 0"));cancelled.close();reject(()->repo.install(cancelled,false),"cancelled resources cannot be installed");
        // A same-named cache of the earlier layout and repeated/lower versions cannot block an import.
        File obstruction=new File(directory,"plugin_data/"+installed.id+"/3");obstruction.mkdir();
        File marker=new File(obstruction,"keep.txt");Files.write(marker.toPath(),bytes("keep"));
        String prior=repo.list().get(0).runtimeBaseDir;
        for(int version:new int[]{3,3,1}){
            try(CustomPluginRepository.Preview replacement=preview(repo,bundle(installed.id,version,"void "+version))){
                CustomPluginRepository.Plugin replaced=repo.install(replacement,true);
                equal(version,replaced.versionCode,"same and older version replacement succeeds");
                equal(false,prior.equals(replaced.runtimeBaseDir),"replacement receives a fresh resource generation");
                equal(false,new File(prior).exists(),"replaced runtime resources are retired by the committed import");
                equal(false,marker.exists()||obstruction.exists(),"a same-named cache of the earlier layout neither blocks the import nor outlives it");
                equal(true,new File(replaced.runtimeBaseDir,"scripts/runtime.js").isFile(),"current generation is complete after the others are retired");
                equal(true,new CustomPluginRepository(directory).list().size()>0,"generation-based library reopens");prior=replaced.runtimeBaseDir;
            }
        }
        // API 2 does not inherit the legacy 16-plugin quota.
        for(int index=0;index<17;index++){JSONObject value=descriptor("runtime.count-"+index,1,new JSONObject().put("source","void 0"));repo.install(repo.preview(new ByteArrayInputStream(bytes(main(value))),"count.js"),false);}
        equal(19,repo.list().size(),"more than 16 API 2 plugins retained");
        repo.delete(installed.id);equal(false,new File(directory,"plugin_data/"+installed.id).exists(),"deletion removes only validated private plugin tree");equal(true,new File(inlinePlugin.runtimeBaseDir).isDirectory(),"other plugin data retained");
        // Exercise a library larger than the former 8 MiB boundary using external runtime entries.
        File largeDirectory=Files.createTempDirectory("c17-runtime-large-library-").toFile();CustomPluginRepository large=new CustomPluginRepository(largeDirectory);
        String external=largeRuntime(900000);for(int index=0;index<10;index++)large.install(preview(large,bundle("runtime.large-"+index,1,external)),false);
        equal(true,new File(largeDirectory,"library.json").length()>8*1024*1024L,"API 2 library exceeds former 8 MiB quota");equal(10,new CustomPluginRepository(largeDirectory).list().size(),"large library reopens without truncation");
        provider();
        System.out.println("CustomPluginRuntimePackageCheck "+checks+" checks passed");
    }
    private static void provider()throws Exception {
        final File files=Files.createTempDirectory("c17-runtime-provider-").toFile();CustomPluginRepository repo=new CustomPluginRepository(new File(files,"custom_plugins"));
        repo.install(preview(repo,bundle("runtime.asset",1,"void 0")),false);
        android.content.Context context=new android.content.Context(){@Override public File getFilesDir(){return files;}};
        android.net.Uri active=android.net.Uri.parse("content://dev.puitheme.iosstatusbar.settings/custom-plugins/assets/runtime.asset/1/scripts/runtime.js");
        reject(()->CustomPluginRepository.providerFile(context,active),"disabled plugin resource access");repo.setEnabled("runtime.asset",true);
        equal("void 0",new String(Files.readAllBytes(CustomPluginRepository.providerFile(context,active).toPath()),StandardCharsets.UTF_8),"enabled admitted resource served");
        reject(()->CustomPluginRepository.providerFile(context,android.net.Uri.parse(active.toString().replace("scripts/runtime.js","scripts/%2e%2e/main.js"))),"encoded asset traversal");
        reject(()->CustomPluginRepository.providerFile(context,android.net.Uri.parse(active.toString().replace("scripts/runtime.js","library.json"))),"unregistered resource");
        reject(()->CustomPluginRepository.providerFile(context,android.net.Uri.parse(active.toString().replace("/1/","/01/"))),"noncanonical version");
        repo.install(preview(repo,bundle("runtime.asset",2,"void 1")),true);repo.setEnabled("runtime.asset",true);
        reject(()->CustomPluginRepository.providerFile(context,active),"stale version resource request");
        File outside=Files.createTempDirectory("c17-runtime-outside-").resolve("keep.txt").toFile();Files.write(outside.toPath(),bytes("keep"));
        // A persisted root is untrusted data even when a caller has root access.
        File database=new File(files,"custom_plugins/library.json");JSONObject data=new JSONObject(new String(Files.readAllBytes(database.toPath()),StandardCharsets.UTF_8));
        data.getJSONObject("plugins").getJSONObject("runtime.asset").put("runtimeBaseDir",outside.getParentFile().getAbsolutePath());Files.write(database.toPath(),bytes(data.toString()));
        reject(()->CustomPluginRepository.providerFile(context,android.net.Uri.parse(active.toString().replace("/1/","/2/"))),"tampered resource base directory");
        reject(()->repo.delete("runtime.asset"),"tampered private root cannot be deleted");equal("keep",new String(Files.readAllBytes(outside.toPath()),StandardCharsets.UTF_8),"external file survives rejected cleanup");
    }
}
