package dev.puitheme;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real persisted metadata, legacy reads, version replacement and every visible sort mode. */
public final class CustomPluginPresentationCheck {
    private static int checks;
    private interface Task {void run()throws Exception;}
    private static void equal(Object want,Object got,String why){checks++;if(!Objects.equals(want,got))throw new AssertionError(why+": "+want+" != "+got);}
    private static void reject(Task task,String why)throws Exception {checks++;try{task.run();}catch(Exception expected){return;}throw new AssertionError("Accepted "+why);}
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static String script(String id,String name,int version)throws Exception {
        return "var p="+new JSONObject().put("id",id).put("name",name).put("version","1."+version).put("versionCode",version).put("apiVersion",1)
                .put("capabilities",new JSONArray()).put("ui",new JSONArray()
                        .put(new JSONObject().put("type","input").put("key","note").put("title","个人备注").put("default",""))
                        .put(new JSONObject().put("type","text").put("title","备注预览")))
                .put("settings",new JSONObject()).put("hooks",new JSONArray())+";p.ui[1].description=typeof C17.state.note==='string'?C17.state.note:'';C17.define(p);";
    }
    private static CustomPluginRepository.Preview preview(CustomPluginRepository repo,String source)throws Exception{return repo.preview(new ByteArrayInputStream(bytes(source)),"extension.js");}
    private static JSONObject read(File root)throws Exception{return new JSONObject(new String(Files.readAllBytes(new File(root,"library.json").toPath()),StandardCharsets.UTF_8));}
    private static void write(File root,JSONObject data)throws Exception{Files.write(new File(root,"library.json").toPath(),bytes(data.toString()));}
    private static List<String> ids(List<CustomPluginRepository.Plugin> values){List<String> out=new ArrayList<>();for(CustomPluginRepository.Plugin value:values)out.add(value.id);return out;}

    private static void ordering()throws Exception {
        File folder=Files.createTempDirectory("c17-presentation-order-").toFile();CustomPluginRepository repo=new CustomPluginRepository(folder);
        String[] names={"阿尔法","贝塔","查理","德尔塔"};int[] versions={2,3,1,1};
        for(int n=0;n<4;n++)repo.install(preview(repo,script("sort."+(char)('a'+n),names[n],versions[n])),false);
        JSONObject data=read(folder);long[] installed={30,10,20,20},updated={100,400,200,300};
        for(int n=0;n<4;n++)data.getJSONObject("plugins").getJSONObject("sort."+(char)('a'+n)).put("installedAt",installed[n]).put("updatedAt",updated[n]).put("enabled",n%2==0);
        write(folder,data);List<CustomPluginRepository.Plugin> values=repo.list();List<String> original=ids(values);
        List<String> manual=Arrays.asList("removed.plugin","sort.c","sort.c","sort.a");
        String[][] expected={{"sort.c","sort.a","sort.b","sort.d"},{"sort.a","sort.b","sort.c","sort.d"},{"sort.b","sort.c","sort.d","sort.a"},
                {"sort.a","sort.c","sort.d","sort.b"},{"sort.c","sort.d","sort.a","sort.b"},{"sort.b","sort.d","sort.a","sort.c"}};
        String[] modes={"custom","name","installed","updated","version","enabled"};
        for(int n=0;n<modes.length;n++) {
            equal(true,CustomPluginOrdering.validMode(modes[n]),"advertised sort mode");
            List<String> ascending=Arrays.asList(expected[n]);equal(ascending,ids(CustomPluginOrdering.sort(values,modes[n],true,manual)),modes[n]+" ascending");
            List<String> descending=new ArrayList<>(ascending);Collections.reverse(descending);
            equal(descending,ids(CustomPluginOrdering.sort(values,modes[n],false,manual)),modes[n]+" descending including deterministic tie direction");
        }
        equal(false,CustomPluginOrdering.validMode("invalid"),"unknown mode rejected");equal(false,CustomPluginOrdering.validMode(null),"null mode rejected");
        equal(original,ids(values),"sorting never mutates supplied plugin snapshots");
        equal(Arrays.asList("removed.plugin","sort.c","sort.c","sort.a"),manual,"sorting never mutates manual preference");
        equal(Arrays.asList("sort.a","sort.b","sort.c","sort.d"),ids(CustomPluginOrdering.sort(values,"custom",true,Collections.emptyList())),"new IDs stable without a manual order");
        equal(Collections.emptyList(),ids(CustomPluginOrdering.sort(Collections.emptyList(),"custom",false,manual)),"empty plugin list");
        reject(()->CustomPluginOrdering.sort(values,"name",true,manual).clear(),"mutating returned sorted snapshot");
        equal(true,CustomPluginPolicy.VERSION>0,"versioned policy");equal(true,CustomPluginPolicy.TEXT.contains("法定权利"),"policy preserves statutory rights");
    }

    private static void metadata()throws Exception {
        File folder=Files.createTempDirectory("c17-presentation-meta-").toFile();CustomPluginRepository repo=new CustomPluginRepository(folder);
        String id="meta.sample",source=script(id,"元数据插件",1);long before=System.currentTimeMillis();
        CustomPluginRepository.Plugin first=repo.install(preview(repo,source),false,"本地文件");long after=System.currentTimeMillis();
        equal(true,first.installedAt>=before&&first.installedAt<=after,"actual initial import timestamp");equal(first.installedAt,first.updatedAt,"first import sets both dates together");equal("本地文件",first.origin,"local origin recorded");
        CustomPluginRepository.Plugin upgraded=repo.install(preview(repo,script(id,"元数据插件",2)),true,"项目仓库 · https://github.com/SANWU5/c17-plugins");
        equal(first.installedAt,upgraded.installedAt,"upgrade preserves original import time");equal(true,upgraded.updatedAt>=first.updatedAt,"upgrade records update time");
        equal("项目仓库 · https://github.com/SANWU5/c17-plugins",upgraded.origin,"upgrade records current actual origin");equal(false,upgraded.enabled,"upgrade remains disabled");
        repo.setEnabled(id,true);CustomPluginRepository.Plugin enabled=repo.list().get(0);
        equal(upgraded.installedAt,enabled.installedAt,"enabling does not rewrite imported time");equal(upgraded.updatedAt,enabled.updatedAt,"enabling does not rewrite package update time");
        JSONObject data=read(folder);JSONObject record=data.getJSONObject("plugins").getJSONObject(id);record.remove("installedAt");record.remove("updatedAt");record.remove("origin");write(folder,data);
        byte[] oldBytes=Files.readAllBytes(new File(folder,"library.json").toPath());CustomPluginRepository.Plugin legacy=repo.list().get(0);repo.compiledPlan();
        equal(0L,legacy.installedAt,"old code192 missing import time remains unknown");equal(0L,legacy.updatedAt,"old code192 missing update time remains unknown");equal("本地文件或旧版未知",legacy.origin,"old code192 origin fallback");
        equal(true,Arrays.equals(oldBytes,Files.readAllBytes(new File(folder,"library.json").toPath())),"reading legacy metadata does not migrate or rewrite disk");
        CustomPluginRepository.Plugin migrated=repo.install(preview(repo,script(id,"元数据插件",3)),true,"第三方仓库");
        equal(0L,migrated.installedAt,"upgrading a legacy record does not invent historical import time");equal(true,migrated.updatedAt>0,"legacy upgrade receives actual new update time");equal("第三方仓库",migrated.origin,"legacy upgrade records source");
        JSONObject clean=read(folder);
        for(String field:new String[]{"installedAt","updatedAt"})for(Object bad:new Object[]{-1L,1.5,"123",true,JSONObject.NULL,new BigInteger("9223372036854775808")}) {
            JSONObject corrupted=new JSONObject(clean.toString());corrupted.getJSONObject("plugins").getJSONObject(id).put(field,bad);write(folder,corrupted);
            reject(repo::list,"invalid "+field+" type/value "+bad);
        }
        write(folder,clean);
        for(String origin:new String[]{"","   ","\u0000",String.join("",Collections.nCopies(2049,"x"))}) {
            CustomPluginRepository.Preview pending=preview(repo,script(id,"元数据插件",4));
            byte[] previous=Files.readAllBytes(new File(folder,"library.json").toPath());
            reject(()->repo.install(pending,true,origin),"invalid origin must fail before disk write");
            equal(true,Arrays.equals(previous,Files.readAllBytes(new File(folder,"library.json").toPath())),"invalid origin preserves complete previous library");
        }
    }
    public static void main(String[] args)throws Exception {ordering();metadata();System.out.println("CustomPluginPresentationCheck "+checks+" checks passed");}
}
