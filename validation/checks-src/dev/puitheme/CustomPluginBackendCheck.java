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
import org.json.JSONObject;

/** Executes the real engine, strict package importer, storage transactions and compiled plans. */
public final class CustomPluginBackendCheck {
    private static int checks;
    private interface Task {void run()throws Exception;}
    private static void equal(Object want,Object actual,String message){checks++;if(!Objects.equals(want,actual))throw new AssertionError(message+": "+want+" != "+actual);}
    private static void reject(Task task,String message)throws Exception {checks++;try{task.run();}catch(Exception expected){return;}throw new AssertionError("Accepted "+message);}
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static String script(String id,int version,String extra) {
        return "C17.define({id:'"+id+"',name:'测试插件',version:'1."+version+"',versionCode:"+version+",apiVersion:1,capabilities:['settings'],"
                +"ui:[{type:'switch',key:'seconds',title:'显示秒',default:true},{type:'button',key:'fail',title:'失败测试',value:true,default:false}],"
                +"settings:{clock_enabled:true,clock_pattern:C17.state.seconds===false?'HH:mm':'HH:mm:ss'},hooks:[]"+extra+"});";
    }
    private static CustomPluginRepository.Preview preview(CustomPluginRepository repo,String source)throws Exception{return repo.preview(new ByteArrayInputStream(bytes(source)),"test.js");}
    private static byte[] zip(Map<String,byte[]> entries)throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)) {
            for(Map.Entry<String,byte[]> entry:entries.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}
        }return output.toByteArray();
    }
    private static LinkedHashMap<String,byte[]> bundle(String source)throws Exception {
        JSONObject descriptor=CustomPluginEngine.evaluate(source,new JSONObject());JSONObject manifest=new JSONObject();
        for(String key:new String[]{"id","name","version","versionCode","apiVersion","capabilities"})manifest.put(key,descriptor.get(key));
        manifest.put("entry","main.js");LinkedHashMap<String,byte[]> entries=new LinkedHashMap<>();entries.put("plugin.json",bytes(manifest.toString()));entries.put("main.js",bytes(source));return entries;
    }
    private static CustomPluginRepository repository()throws Exception{return new CustomPluginRepository(Files.createTempDirectory("c17-plugin-check-").toFile());}

    private static void engine()throws Exception {
        String good=script("sample.seconds",1,"");JSONObject value=CustomPluginEngine.evaluate(good,new JSONObject());
        equal("sample.seconds",value.getString("id"),"real JavaScript descriptor");
        equal("HH:mm",CustomPluginEngine.evaluate(good,new JSONObject().put("seconds",false)).getJSONObject("settings").getString("clock_pattern"),"state drives native settings");
        reject(()->CustomPluginEngine.evaluate("while(true){}",new JSONObject()),"infinite JavaScript loop");
        reject(()->CustomPluginEngine.evaluate("try{while(true){}}catch(e){}",new JSONObject()),"catching budget termination");
        reject(()->CustomPluginEngine.evaluate("java.lang.System.exit(0);"+good,new JSONObject()),"Java global bridge");
        reject(()->CustomPluginEngine.evaluate("Packages.java.lang.System.exit(0);"+good,new JSONObject()),"Packages bridge");
        reject(()->CustomPluginEngine.evaluate("C17.define({a:1});C17.define({b:2});",new JSONObject()),"multiple definitions");
        reject(()->CustomPluginEngine.evaluate(good.replace("apiVersion:1","apiVersion:3"),new JSONObject()),"future API version");
        reject(()->CustomPluginEngine.evaluate(good.replace("versionCode:1","versionCode:1.5"),new JSONObject()),"fractional version");
        reject(()->CustomPluginEngine.evaluate(good.replace("sample.seconds","../escape"),new JSONObject()),"metadata path traversal");
        reject(()->CustomPluginEngine.evaluate(good.replace("capabilities:['settings']","capabilities:[]"),new JSONObject()),"undeclared settings capability");
        reject(()->CustomPluginEngine.evaluate(good.replace("capabilities:['settings']","capabilities:['root']"),new JSONObject()),"unknown capability");
        reject(()->CustomPluginEngine.evaluate(good.replace("clock_enabled:true","safe_mode:false"),new JSONObject()),"safe mode override");
        reject(()->CustomPluginEngine.evaluate(good.replace("clock_enabled:true","clock_enabled:'true'"),new JSONObject()),"settings type coercion");
        reject(()->CustomPluginEngine.evaluate(script("sample.seconds",1,",unknown:1"),new JSONObject()),"unknown top level field");
        reject(()->CustomPluginEngine.evaluate(script("sample.seconds",1,",author:function(){}"),new JSONObject()),"functions in descriptor");
        reject(()->CustomPluginEngine.evaluate(script("sample.seconds",1,",author:undefined"),new JSONObject()),"undefined in descriptor");
        reject(()->CustomPluginEngine.evaluate(script("sample.seconds",1,",author:NaN"),new JSONObject()),"nonfinite descriptor data");
        reject(()->CustomPluginEngine.evaluate(script("sample.seconds",1,",get author(){while(true){}}"),new JSONObject()),"runaway descriptor getter");
        reject(()->CustomPluginEngine.evaluate("var x={};x.self=x;C17.define(x);",new JSONObject()),"circular descriptor");
        reject(()->CustomPluginEngine.evaluate(good.replace("default:true","default:'yes'"),new JSONObject()),"switch default wrong type");
        JSONObject ui=new JSONObject("{type:'slider',title:'数字',key:'number',min:0,max:10,default:5}");CustomPluginSchema.stateValue(ui,7);
        reject(()->CustomPluginSchema.stateValue(ui,11),"slider out of range");
        JSONObject select=new JSONObject("{type:'select',title:'选择',key:'choice',options:[{label:'甲',value:'a'}]}");
        equal("a",CustomPluginSchema.defaultValue(select),"select default");reject(()->CustomPluginSchema.stateValue(select,"b"),"unknown select choice");
        String recursive="function f(){return f();} f();"+good;reject(()->CustomPluginEngine.evaluate(recursive,new JSONObject()),"deep recursive script");
    }

    private static void storage()throws Exception {
        File untouched=Files.createTempDirectory("c17-plugin-noop-").resolve("custom_plugins").toFile();
        new CustomPluginRepository(untouched).setAllDisabled();equal(false,untouched.exists(),"maintenance stop without library does not create storage");
        CustomPluginRepository repo=repository();String source=script("sample.seconds",1,"");
        CustomPluginRepository.Preview first=preview(repo,source);equal(false,first.upgrade,"new preview");
        // UI snapshots are mutable; the private approved plan/source remain authoritative.
        first.descriptor.put("id","tampered.id");CustomPluginRepository.Plugin installed=repo.install(first,false);
        equal("sample.seconds",installed.id,"preview snapshot cannot change install identity");equal(false,installed.enabled,"installed disabled");equal(true,installed.state.getBoolean("seconds"),"declared default persisted");
        equal(0,repo.compiledPlan().getJSONObject("settings").length(),"disabled plan empty");
        repo.setEnabled(installed.id,true);equal("HH:mm:ss",repo.compiledPlan().getJSONObject("settings").getString("clock_pattern"),"enabled settings plan");
        repo.updateState(installed.id,"seconds",false);equal("HH:mm",repo.compiledPlan().getJSONObject("settings").getString("clock_pattern"),"state recompiles settings");
        reject(()->repo.updateState(installed.id,"seconds","false"),"state type coercion");equal(true,repo.list().get(0).enabled,"invalid UI input does not quarantine");
        reject(()->repo.updateState(installed.id,"missing",true),"undeclared state key");
        equal(true,preview(repo,source).upgrade,"same version replacement can be reviewed");reject(()->preview(repo,script(installed.id,1,"").replace("versionCode:1","versionCode:0")),"version downgrade");
        final CustomPluginRepository.Preview upgrade=preview(repo,script(installed.id,2,""));equal(true,upgrade.upgrade,"upgrade advertised");equal("1.1",upgrade.previousVersion,"old version advertised");
        reject(()->repo.install(upgrade,false),"unconfirmed replacement");equal(1,repo.list().get(0).versionCode,"unconfirmed keeps old code");
        repo.updateState(installed.id,"seconds",true);reject(()->repo.install(upgrade,true),"stale preview after state change");
        repo.install(preview(repo,script(installed.id,2,"")),true);equal(false,repo.list().get(0).enabled,"upgrade defaults disabled");equal(true,repo.list().get(0).state.getBoolean("seconds"),"upgrade preserves state");
        repo.setEnabled(installed.id,true);
        CustomPluginRepository.Plugin second=repo.install(preview(repo,script("sample.other",1,"")),false);
        reject(()->repo.setEnabled(second.id,true),"competing setting overrides");equal(false,repo.list().get(0).enabled,"conflicting plugin quarantined by sorted ID");
        equal("HH:mm:ss",repo.compiledPlan().getJSONObject("settings").getString("clock_pattern"),"first plugin stays active on conflict");
        repo.setAllDisabled();equal(0,repo.compiledPlan().getJSONObject("settings").length(),"stop all removes overlays");
        repo.delete(second.id);equal(1,repo.list().size(),"delete plugin");reject(()->repo.delete(second.id),"delete missing ID");
        String failing="if(C17.state.fail===true)throw new Error('intentional state failure');"+script("sample.failure",1,"");
        repo.install(preview(repo,failing),false);repo.setEnabled("sample.failure",true);
        reject(()->repo.updateState("sample.failure","fail",true),"script failure on state update");
        CustomPluginRepository.Plugin failed=repo.list().get(0);equal("sample.failure",failed.id,"stable sorted list");equal(false,failed.enabled,"failed state quarantines");equal(false,failed.state.getBoolean("fail"),"failure preserves old state");equal(true,failed.error.contains("intentional"),"quarantine records reason");
        repo.setEnabled(failed.id,true);equal(true,repo.list().get(0).enabled,"explicit retry compiles old safe state");
        repo.setAllDisabled();
        String changing=script("sample.identity",1,"").replace("name:'测试插件'","name:C17.state.seconds===false?'伪装插件':'测试插件'");
        repo.install(preview(repo,changing),false);repo.setEnabled("sample.identity",true);
        reject(()->repo.updateState("sample.identity","seconds",false),"identity changing with plugin state");
        CustomPluginRepository.Plugin identity=repo.list().get(1);equal("sample.identity",identity.id,"identity failure remains manageable");equal(false,identity.enabled,"identity mismatch quarantined");equal(true,identity.state.getBoolean("seconds"),"identity mismatch preserves state");
        File folder=Files.createTempDirectory("c17-plugin-reload-").toFile();CustomPluginRepository persistent=new CustomPluginRepository(folder);
        persistent.install(preview(persistent,source),false);persistent.setEnabled("sample.seconds",true);
        CustomPluginRepository reopened=new CustomPluginRepository(folder);equal(true,reopened.list().get(0).enabled,"enable flag survives new repository instance");equal("HH:mm:ss",reopened.compiledPlan().getJSONObject("settings").getString("clock_pattern"),"compiled plan survives new repository instance");
        File database=new File(folder,"library.json");JSONObject record=new JSONObject(new String(Files.readAllBytes(database.toPath()),StandardCharsets.UTF_8));
        record.getJSONObject("plugins").getJSONObject("sample.seconds").put("source","while(true){}");Files.write(database.toPath(),bytes(record.toString()));
        equal("HH:mm:ss",reopened.compiledPlan().getJSONObject("settings").getString("clock_pattern"),"plan reads stored JSON without executing JavaScript");
        reject(()->reopened.setEnabled("sample.seconds",true),"failed explicit recompile quarantines");equal(false,reopened.list().get(0).enabled,"bad saved script disabled");
    }

    private static String hookScript(String id,String ruleId) {
        return "C17.define({id:'"+id+"',name:'Hook示例',version:'1.0',versionCode:1,apiVersion:1,capabilities:['systemui'],"
                +"ui:[{type:'switch',key:'apply',title:'添加时钟前缀',default:true}],settings:{},hooks:C17.state.apply===false?[]:[{id:'"+ruleId+"',className:'com.android.systemui.statusbar.policy.Clock',method:'updateClock',argCount:0,kind:'text',prefix:'C17 '}]});";
    }
    private static void sortTransactions()throws Exception {
        CustomPluginRepository repo=repository();
        String source="C17.define({id:'sample.sort',name:'排序测试',version:'1.0',versionCode:1,apiVersion:2,capabilities:['runtime'],"
                +"runtime:{source:'C17.onLoad(function(){});'},ui:[{type:'sort',key:'order',title:'顺序',optionsAction:'catalog'},"
                +"{type:'switch',key:'other',title:'其他设置',default:false}],settings:{},hooks:[]});";
        repo.install(preview(repo,source),false);
        equal("[]",repo.list().get(0).state.getString("order"),"sort default persists");
        reject(()->repo.updateStateCompared("sample.sort",1,"order","[]","[\"wifi\"]"),"disabled sort draft");
        repo.setEnabled("sample.sort",true);
        reject(()->repo.updateStateCompared("sample.sort",2,"order","[]","[\"wifi\"]"),"changed version sort draft");
        reject(()->repo.updateStateCompared("sample.sort",1,"other","false","[]"),"non-sort compared field");
        repo.updateStateCompared("sample.sort",1,"order","[]","[\"wifi\"]");
        equal("[\"wifi\"]",repo.list().get(0).state.getString("order"),"complete order replaces field");
        reject(()->repo.updateStateCompared("sample.sort",1,"order","[]","[\"bt\"]"),"stale sort draft");
        equal("[\"wifi\"]",repo.list().get(0).state.getString("order"),"stale sort preserves winner");
        repo.updateState("sample.sort","other",true);
        repo.updateStateCompared("sample.sort",1,"order","[\"wifi\"]","[]");
        equal(true,repo.list().get(0).state.getBoolean("other"),"sort save preserves other fields");
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger accepted=new java.util.concurrent.atomic.AtomicInteger(),rejected=new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread[] writers=new Thread[2];
        for(int i=0;i<writers.length;i++){
            final String order=i==0?"[\"wifi\"]":"[\"bt\"]";
            writers[i]=new Thread(()->{try{start.await();repo.updateStateCompared("sample.sort",1,"order","[]",order);accepted.incrementAndGet();}
                catch(java.io.IOException stale){rejected.incrementAndGet();}catch(Throwable unexpected){failure.set(unexpected);}});
            writers[i].start();
        }
        start.countDown();for(Thread writer:writers)writer.join();
        if(failure.get()!=null)throw new AssertionError("sort writer failed",failure.get());
        equal(1,accepted.get(),"only one concurrent sort draft commits");equal(1,rejected.get(),"other concurrent sort draft rejected");
        String saved=repo.list().get(0).state.getString("order");
        equal(true,saved.equals("[\"wifi\"]")||saved.equals("[\"bt\"]"),"concurrent save keeps one complete order");
        repo.setEnabled("sample.sort",false);
        reject(()->repo.updateStateCompared("sample.sort",1,"order",saved,"[]"),"disabled after opening sort draft");
        equal(saved,repo.list().get(0).state.getString("order"),"disable rejection preserves draft baseline");
    }
    private static void hooks()throws Exception {
        CustomPluginRepository repo=repository();repo.install(preview(repo,hookScript("sample.hook-a","a")),false);repo.setEnabled("sample.hook-a",true);
        equal("sample.hook-a",repo.compiledPlan().getJSONArray("hooks").getJSONObject(0).getString("pluginId"),"compiled hook carries plugin identity");
        repo.install(preview(repo,hookScript("sample.hook-b","b")),false);reject(()->repo.setEnabled("sample.hook-b",true),"duplicate hook site conflict");
        equal(1,repo.compiledPlan().getJSONArray("hooks").length(),"conflicting hook never reaches native plan");
        String undeclared=hookScript("sample.missing","x").replace("capabilities:['systemui']","capabilities:[]");reject(()->preview(repo,undeclared),"undeclared SystemUI capability");
    }
    private static JSONObject definition(String id)throws Exception {
        return new JSONObject().put("id",id).put("name","上限检查").put("version","1.0").put("versionCode",1).put("apiVersion",1)
                .put("ui",new org.json.JSONArray().put(new JSONObject().put("type","switch").put("key","apply").put("title","应用本插件规则").put("default",true)))
                .put("settings",new JSONObject()).put("hooks",new org.json.JSONArray());
    }
    private static String configured(JSONObject descriptor) {
        return "var p="+descriptor+";if(C17.state.apply===false){p.settings={};p.hooks=[];}C17.define(p);";
    }
    private static void aggregateLimits()throws Exception {
        final CustomPluginRepository repo=repository();int accepted=0,index=0;JSONObject settings=new JSONObject();
        for(SettingsCatalog.Item item:SettingsCatalog.allItems()) {
            if(accepted>=513)break;
            JSONObject one=new JSONObject().put(item.key,item.defaultValue);
            try {CustomPluginSettings.validate(one);}catch(IllegalArgumentException unsupported){continue;}
            settings.put(item.key,item.defaultValue);accepted++;
            if(settings.length()==64||accepted==513) {
                String id="limit.settings-"+(index++);JSONObject plugin=definition(id).put("capabilities",new org.json.JSONArray().put("settings")).put("settings",settings);
                repo.install(preview(repo,configured(plugin)),false);
                if(accepted==513)reject(()->repo.setEnabled(id,true),"aggregate more than 512 settings");else repo.setEnabled(id,true);
                settings=new JSONObject();
            }
        }
        equal(513,accepted,"catalog fixture supplies enough valid independent settings");
        equal(512,repo.compiledPlan().getJSONObject("settings").length(),"settings overflow leaves previous complete plan");
        final CustomPluginRepository hookRepo=repository();StringBuilder repeated=new StringBuilder();for(int n=0;n<256;n++)repeated.append('甲');
        String text=repeated.toString();
        for(int group=0;group<2;group++) {
            String id="limit.hooks-"+group;org.json.JSONArray hooks=new org.json.JSONArray();
            for(int n=0;n<32;n++)hooks.put(new JSONObject().put("id","rule-"+n).put("className","com.android.systemui.statusbar.policy.Clock")
                    .put("method","method"+(group*32+n)).put("argCount",0).put("kind","text").put("find",text).put("replace",text).put("prefix",text).put("suffix",text));
            JSONObject plugin=definition(id).put("capabilities",new org.json.JSONArray().put("systemui")).put("hooks",hooks);
            hookRepo.install(preview(hookRepo,configured(plugin)),false);
            if(group==0)hookRepo.setEnabled(id,true);else reject(()->hookRepo.setEnabled(id,true),"aggregate JSON exceeding 128 KB");
        }
        equal(32,hookRepo.compiledPlan().getJSONArray("hooks").length(),"byte overflow leaves previous complete hook plan");
    }

    private static void packages()throws Exception {
        CustomPluginRepository repo=repository();String source=script("sample.bundle",1,"");Map<String,byte[]> entries=bundle(source);byte[] good=zip(entries);
        CustomPluginRepository.Preview imported=repo.preview(new ByteArrayInputStream(good),"demo.zip");equal("sample.bundle",imported.descriptor.getString("id"),"real ZIP manifest and entry");
        reject(()->repo.preview(new ByteArrayInputStream(bytes(source)),"script.txt"),"unsupported suffix");
        reject(()->repo.preview(new ByteArrayInputStream(new byte[]{(byte)0xc3,(byte)0x28}),"bad.js"),"malformed UTF-8");
        reject(()->repo.preview(new ByteArrayInputStream(new byte[CustomPluginEngine.MAX_SOURCE_BYTES+1]),"large.js"),"oversized script");
        Map<String,byte[]> traversal=new LinkedHashMap<>(entries);traversal.put("../escape.js",bytes("x"));reject(()->repo.preview(new ByteArrayInputStream(zip(traversal)),"bad.zip"),"ZIP traversal");
        Map<String,byte[]> windows=new LinkedHashMap<>(entries);windows.put("C:\\escape.js",bytes("x"));reject(()->repo.preview(new ByteArrayInputStream(zip(windows)),"bad.zip"),"ZIP Windows absolute path");
        Map<String,byte[]> duplicate=new LinkedHashMap<>(entries);duplicate.put("MAIN.JS",bytes(source));reject(()->repo.preview(new ByteArrayInputStream(zip(duplicate)),"bad.zip"),"case insensitive duplicate paths");
        Map<String,byte[]> unknown=new LinkedHashMap<>(entries);unknown.put("payload.dex",new byte[3]);reject(()->repo.preview(new ByteArrayInputStream(zip(unknown)),"bad.zip"),"executable asset payload");
        Map<String,byte[]> mismatch=new LinkedHashMap<>(entries);mismatch.put("plugin.json",bytes(new String(entries.get("plugin.json"),StandardCharsets.UTF_8).replace("sample.bundle","sample.changed")));reject(()->repo.preview(new ByteArrayInputStream(zip(mismatch)),"bad.zip"),"manifest metadata mismatch");
        Map<String,byte[]> missing=new LinkedHashMap<>(entries);missing.remove("main.js");reject(()->repo.preview(new ByteArrayInputStream(zip(missing)),"bad.zip"),"missing entry script");
        reject(()->repo.preview(new ByteArrayInputStream(Arrays.copyOf(good,good.length-15)),"bad.zip"),"truncated end directory");
        byte[] differing=good.clone();differing[30]=(byte)'X';reject(()->repo.preview(new ByteArrayInputStream(differing),"bad.zip"),"local and central name mismatch");
        Map<String,byte[]> bomb=new LinkedHashMap<>(entries);byte[] repetition=new byte[250000];Arrays.fill(repetition,(byte)' ');bomb.put("large.txt",repetition);reject(()->repo.preview(new ByteArrayInputStream(zip(bomb)),"bad.zip"),"high compression ratio bomb");
        equal(0,repo.list().size(),"preview and failed imports never install");
    }
    private static void examples(File parent)throws Exception {
        for(String name:new String[]{"clock-seconds","text-fix","ui-spacing"}) {
            File folder=new File(parent,name);String source=new String(Files.readAllBytes(new File(folder,"main.js").toPath()),StandardCharsets.UTF_8);
            CustomPluginRepository repo=repository();LinkedHashMap<String,byte[]> entries=new LinkedHashMap<>();
            entries.put("plugin.json",Files.readAllBytes(new File(folder,"plugin.json").toPath()));entries.put("main.js",bytes(source));
            CustomPluginRepository.Preview plugin=repo.preview(new ByteArrayInputStream(zip(entries)),name+".zip");
            equal("dev.c17."+name,plugin.descriptor.getString("id"),"official example real ZIP import: "+name);
            repo.install(plugin,false);repo.setEnabled("dev.c17."+name,true);equal(true,repo.list().get(0).enabled,"official example real activation: "+name);
        }
    }
    public static void main(String[] args)throws Exception {engine();storage();sortTransactions();hooks();aggregateLimits();packages();if(args.length>0)examples(new File(args[0]));System.out.println("CustomPluginBackendCheck "+checks+" checks passed");}
}
