package dev.puitheme;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

/** Complete plugin-owned forms use the real importer, engine and durable repository. */
public final class CustomPluginConfigurationCheck {
    private static int checks;
    private interface Task { void run() throws Exception; }
    private static void equal(Object expected,Object actual,String label) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError(label+": "+expected+" != "+actual);
    }
    private static void reject(Task task,String label)throws Exception {
        checks++;try{task.run();}catch(Exception expected){return;}throw new AssertionError("Accepted "+label);
    }
    private static String script(JSONArray controls,String body) {
        return "var form="+controls+";"+body+"C17.define({id:'dev.c17.configuration',name:'完整配置检查',version:'1.0',versionCode:1,apiVersion:1,"
                +"capabilities:['settings'],ui:form,settings:{clock_enabled:true,clock_pattern:C17.state.pattern||'HH:mm'},hooks:[]});";
    }
    private static CustomPluginRepository.Plugin install(CustomPluginRepository repo,String source)throws Exception {
        return repo.install(repo.preview(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),"configuration.js"),false);
    }
    private static void renamedStateUpgrade()throws Exception {
        File directory=Files.createTempDirectory("c17-state-upgrade-").toFile();
        CustomPluginRepository repo=new CustomPluginRepository(directory);
        String legacy=script(new JSONArray("[{type:'slider',key:'old_amount',title:'Amount',min:0,max:300,default:100}]"),"");
        String id=install(repo,legacy).id;repo.updateState(id,"old_amount",37);
        String next=script(new JSONArray("[{type:'slider',key:'panel_amount',title:'Shared amount',min:0,max:300,default:100}]"),
                "form[0].default=typeof C17.state.panel_amount==='number'?C17.state.panel_amount:typeof C17.state.old_amount==='number'?C17.state.old_amount:100;").replace("versionCode:1","versionCode:2");
        try(CustomPluginRepository.Preview preview=repo.preview(new ByteArrayInputStream(next.getBytes(StandardCharsets.UTF_8)),"next.js")) {
            equal(37,preview.descriptor.getJSONArray("ui").getJSONObject(0).getInt("default"),"reviewed renamed control keeps saved legacy value");
            equal(37,repo.install(preview,true).state.getInt("panel_amount"),"installation uses reviewed migrated default");
        }
        equal(false,repo.list().get(0).state.has("old_amount"),"obsolete independent key removed after explicit merged control upgrade");
        repo.updateState(id,"panel_amount",62);
        try(CustomPluginRepository.Preview preview=repo.preview(new ByteArrayInputStream(next.getBytes(StandardCharsets.UTF_8)),"same.js")) {
            equal(62,repo.install(preview,true).state.getInt("panel_amount"),"same-version reimport preserves new setting");
        }
    }
    private static void nestedColorUpgrade()throws Exception {
        File directory=Files.createTempDirectory("c17-nested-color-").toFile();
        CustomPluginRepository repo=new CustomPluginRepository(directory);
        String old=script(new JSONArray("[{type:'input',key:'tint',title:'颜色',default:'#FFFFFF',group:'配色'}]"),"");
        String id=install(repo,old).id;repo.updateState(id,"tint","#8044aa66");
        String next=script(new JSONArray("[{type:'color',key:'tint',title:'颜色',default:'#FFFFFF',groupPath:['效果调节','部件','浅色遮罩']}]"),"").replace("versionCode:1","versionCode:2");
        try(CustomPluginRepository.Preview preview=repo.preview(new ByteArrayInputStream(next.getBytes(StandardCharsets.UTF_8)),"color.js")) {
            equal(1,CustomPluginSchema.configurationCount(preview.descriptor),"color is a complete configurable form");
            equal("#8044aa66",repo.install(preview,true).state.getString("tint"),"input to explicit color preserves valid prior value");
        }
        repo.updateState(id,"tint","#00112233");
        equal("#00112233",new CustomPluginRepository(directory).list().get(0).state.getString("tint"),"transparent color survives save and reopen");
        byte[] before=Files.readAllBytes(new File(directory,"library.json").toPath());
        for(Object invalid:new Object[]{"red","#abc","#FFFFFF00extra",123,true," #FFFFFF",JSONObject.NULL})
            reject(()->repo.updateState(id,"tint",invalid),"invalid color state "+invalid);
        equal(true,java.util.Arrays.equals(before,Files.readAllBytes(new File(directory,"library.json").toPath())),"failed color writes preserve record");
        repo.resetState(id);equal("#FFFFFF",repo.list().get(0).state.getString("tint"),"color reset restores factory value");
        CustomPluginRepository spaced=new CustomPluginRepository(Files.createTempDirectory("c17-spaced-color-").toFile());
        String spacedId=install(spaced,old).id;spaced.updateState(spacedId,"tint"," #8055aa77 ");
        try(CustomPluginRepository.Preview preview=spaced.preview(new ByteArrayInputStream(next.getBytes(StandardCharsets.UTF_8)),"color.js")) {
            equal("#8055aa77",spaced.install(preview,true).state.getString("tint"),"legacy trimmed input color upgrades without reverting to default");
        }
        equal("#8055aa77",spaced.list().get(0).state.getString("tint"),"normalized legacy color remains saved after reopening");
    }
    private static void structuredFormContract()throws Exception {
        JSONObject control=new JSONObject("{type:'color',key:'tint',title:'颜色',group:'旧分组',groupPath:['效果调节','部件','浅色遮罩']}");
        equal(java.util.Arrays.asList("效果调节","部件","浅色遮罩"),CustomPluginSchema.groupPath(control),"explicit three-level path takes precedence");
        reject(()->CustomPluginSchema.groupPath(control).set(0,"mutated"),"mutable menu path");
        control.remove("groupPath");control.put("group","外观 / 配色");
        equal(java.util.Arrays.asList("外观 / 配色"),CustomPluginSchema.groupPath(control),"legacy group remains one literal level");
        control.remove("group");equal(0,CustomPluginSchema.groupPath(control).size(),"ungrouped legacy control remains ungrouped");
        for(Object invalid:new Object[]{new JSONArray(),new JSONArray("['1','2','3','4']"),"a/b",new JSONArray().put(1),new JSONArray().put(" "),new JSONArray().put("a\0b"),new JSONArray().put("x".repeat(121)),JSONObject.NULL}) {
            control.put("groupPath",invalid);
            reject(()->CustomPluginEngine.evaluate(script(new JSONArray().put(control),""),new JSONObject()),"invalid group path "+invalid);
        }
        control.put("groupPath",new JSONArray("['效果调节','部件','颜色']"));
        JSONObject defined=CustomPluginEngine.evaluate(script(new JSONArray().put(control),""),new JSONObject());
        equal("#FFFFFF",CustomPluginSchema.defaultValue(defined.getJSONArray("ui").getJSONObject(0)),"color default is valid opaque white");
        JSONObject runtime=new JSONObject(defined.toString()).put("apiVersion",2).put("capabilities",new JSONArray("['settings','runtime']"))
                .put("runtime",new JSONObject().put("source","void 0;"));
        runtime.getJSONArray("ui").put(new JSONObject("{type:'action',key:'refresh',title:'刷新',groupPath:['管理','运行','查询']}"))
                .put(new JSONObject("{type:'status',key:'status',title:'状态',groupPath:['管理','运行','查询']}"))
                .put(new JSONObject("{type:'sort',key:'sort',title:'排序',optionsAction:'items',groupPath:['设置','部件','顺序']}"));
        CustomPluginSchema.validate(runtime);equal(2,CustomPluginSchema.form(runtime).getJSONArray("actions").length(),"nested paths preserve action and dynamic list contracts");
        for(Object bad:new Object[]{"transparent","#12345G",0,JSONObject.NULL}) {
            control.put("default",bad);reject(()->CustomPluginEngine.evaluate(script(new JSONArray().put(control),""),new JSONObject()),"invalid color default");
        }
    }
    private static void controlValues()throws Exception {
        JSONObject amount=new JSONObject("{type:'slider',key:'amount',title:'数值',min:0.25,max:4,step:0.05}");
        equal(1.23d,CustomPluginControlValues.parseSlider(amount,"1.23"),"manual precision is independent of gesture step");
        equal(1.25d,CustomPluginControlValues.parseSlider(amount," 1.25e0 "),"same bounded scientific parser as main settings");
        equal(0.25d,CustomPluginControlValues.parseSlider(amount,".25"),"minimum accepted");
        equal(4d,CustomPluginControlValues.parseSlider(amount,"4"),"maximum accepted");
        for(String bad:new String[]{".24999","4.00001","NaN","Infinity","1e999999999","1e-999999999","","x".repeat(257),null})
            reject(()->CustomPluginControlValues.parseSlider(amount,bad),"invalid manual value");
        equal(1.25d,CustomPluginControlValues.snapSlider(amount,1.249999976158142),"float drag snapped to decimal step");
        equal(0.25d,CustomPluginControlValues.snapSlider(amount,-5),"drag lower bound clamped");
        equal(4d,CustomPluginControlValues.snapSlider(amount,99),"drag upper bound clamped");
        equal("1.23",CustomPluginControlValues.formatSlider(amount,1.23),"editor seed preserves exact manually entered decimal");
        equal("4",CustomPluginControlValues.formatSlider(amount,4d),"integer display omits float suffix and unit");
        amount.put("min",-.25);amount.put("step",.1);
        equal(-.15d,CustomPluginControlValues.snapSlider(amount,-.16),"gesture step anchored at signed fractional minimum");
        equal(0xff22aa66,CustomPluginControlValues.parseColor("#22aa66"),"RGB picker value gets opaque alpha");
        equal(0x0022aa66,CustomPluginControlValues.parseColor("#0022aa66"),"explicit transparent alpha preserved");
        equal("#0022AA66",CustomPluginControlValues.formatColor(0x0022aa66,true),"ARGB formatting preserves alpha");
        equal("#22AA66",CustomPluginControlValues.formatColor(0xff22aa66,false),"RGB formatting has six digits");
        reject(()->CustomPluginControlValues.parseColor("#abcd"),"unsupported color shorthand");
        reject(()->CustomPluginControlValues.parseColor(null),"missing color value");
    }
    private static void presetContract()throws Exception {
        JSONArray ui=new JSONArray("[{type:'color',key:'tint',title:'颜色',default:'#FFFFFF'},"
                +"{type:'slider',key:'amount',title:'数值',min:0,max:100,default:40},"
                +"{type:'preset',key:'clear',title:'通透',groupPath:['效果调节','预设'],values:{tint:'#80112233',amount:25}}]");
        JSONObject defined=CustomPluginEngine.evaluate(script(ui,""),new JSONObject());
        equal(2,CustomPluginSchema.configurationCount(defined),"preset itself is not persistent configuration");
        equal(false,CustomPluginSchema.persistentControl(ui.getJSONObject(2)),"preset does not acquire a state key");
        equal(0,CustomPluginSchema.form(defined).getJSONArray("actions").length(),"preset is a local transaction not runtime action");
        JSONObject preset=ui.getJSONObject(2);
        for(JSONObject bad:new JSONObject[]{new JSONObject(),new JSONObject().put("missing",1),new JSONObject().put("clear",1),new JSONObject().put("amount",101),new JSONObject().put("tint","red")}) {
            preset.put("values",bad);reject(()->CustomPluginEngine.evaluate(script(ui,""),new JSONObject()),"invalid preset target or value");
        }
        preset.put("values",new JSONObject().put("amount",15));preset.put("default",false);
        reject(()->CustomPluginEngine.evaluate(script(ui,""),new JSONObject()),"persistent default on preset");
        preset.remove("default");ui.getJSONObject(0).put("values",new JSONObject().put("amount",1));
        reject(()->CustomPluginEngine.evaluate(script(ui,""),new JSONObject()),"values on non-preset");
    }
    private static String runtimeScript(JSONArray controls) {
        return script(controls,"").replace("apiVersion:1","apiVersion:2")
                .replace("capabilities:['settings']","capabilities:['settings','runtime'],runtime:{source:'void 0;'}");
    }
    private static void presetField(JSONArray ui,JSONObject values,int index)throws Exception {
        String key="setting_"+index;
        JSONObject field=new JSONObject().put("key",key).put("title","设置 "+index);Object value;
        switch(index%6) {
            case 0:field.put("type","switch").put("default",false);value=true;break;
            case 1:field.put("type","slider").put("min",0).put("max",1000).put("step",.05).put("default",0);value=index+.25;break;
            case 2:field.put("type","input").put("default","");value="作者设置 "+index;break;
            case 3:field.put("type","color").put("default","#FFFFFF");value="#80112233";break;
            case 4:field.put("type","select").put("default","off").put("options",new JSONArray("[{value:'off',label:'关闭'},{value:'on',label:'开启'}]"));value="on";break;
            default:field.put("type","app").put("default","");value="dev.example.player";break;
        }
        ui.put(field);values.put(key,value);
    }
    private static void completePresetContract()throws Exception {
        JSONArray ui=new JSONArray();JSONObject values=new JSONObject();
        for(int index=0;index<277;index++)presetField(ui,values,index);
        JSONObject author=new JSONObject().put("type","preset").put("key","author").put("title","作者预设").put("values",values);
        ui.put(new JSONObject("{type:'switch',key:'outside',title:'预设外设置',default:false}"))
                .put(new JSONObject("{type:'action',key:'refresh',title:'刷新'}"))
                .put(new JSONObject("{type:'status',key:'status',title:'状态'}")).put(author);
        String source=runtimeScript(ui);
        JSONObject descriptor=CustomPluginEngine.evaluate(source,new JSONObject());
        equal(277,descriptor.getJSONArray("ui").getJSONObject(280).getJSONObject("values").length(),"complete author preset accepted without splitting");
        File directory=Files.createTempDirectory("c17-complete-author-preset-").toFile();
        CustomPluginRepository repo=new CustomPluginRepository(directory);
        String id=install(repo,source).id;repo.updateState(id,"outside",true);
        equal(false,repo.list().get(0).state.getBoolean("setting_0"),"import does not apply the author preset");
        repo.applyPreset(id,1,"author");
        JSONObject saved=new CustomPluginRepository(directory).list().get(0).state;
        for(int index=0;index<277;index++) {
            String key="setting_"+index;equal(values.get(key).toString(),saved.get(key).toString(),"complete author setting persists: "+key);
        }
        equal(true,saved.getBoolean("outside"),"complete preset preserves configuration outside its declared values");
        for(String invalid:new String[]{"missing","author","refresh","status"}) {
            values.put(invalid,true);
            reject(()->CustomPluginEngine.evaluate(runtimeScript(ui),new JSONObject()),"large preset rejects non-persistent target "+invalid);
            values.remove(invalid);
        }
        values.put("setting_0","true");
        reject(()->CustomPluginEngine.evaluate(runtimeScript(ui),new JSONObject()),"large preset retains switch type validation");
        values.put("setting_0",true).put("setting_1",1000.25);
        reject(()->CustomPluginEngine.evaluate(runtimeScript(ui),new JSONObject()),"large preset retains bounded slider range validation");
        values.put("setting_1",1.25);
        JSONArray duplicate=new JSONArray(ui.toString()).put(new JSONObject(ui.getJSONObject(0).toString()));
        reject(()->CustomPluginEngine.evaluate(runtimeScript(duplicate),new JSONObject()),"large preset retains duplicate control key rejection");
        for(int index=277;index<512;index++)presetField(ui,values,index);
        CustomPluginSchema.validate(CustomPluginEngine.evaluate(runtimeScript(ui),new JSONObject()));
        equal(512,values.length(),"512 declared preset values accepted at the limit");
        presetField(ui,values,512);
        reject(()->CustomPluginEngine.evaluate(runtimeScript(ui),new JSONObject()),"513 otherwise valid declared preset values exceed the limit");
    }
    private static JSONObject stored(File directory)throws Exception {
        return new JSONObject(new String(Files.readAllBytes(new File(directory,"library.json").toPath()),StandardCharsets.UTF_8));
    }
    private static void replaceSource(File directory,String source)throws Exception {
        JSONObject data=stored(directory);
        data.getJSONObject("plugins").getJSONObject("dev.c17.configuration").put("source",source);
        Files.write(new File(directory,"library.json").toPath(),data.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static JSONArray controls()throws Exception {
        return new JSONArray("[{type:'text',title:'完整配置',description:'说明'},"
                +"{type:'switch',key:'seconds',title:'秒数',default:false},"
                +"{type:'slider',key:'size',title:'字号比例',min:25,max:250,step:5,default:100,unit:'%'},"
                +"{type:'input',key:'pattern',title:'格式',default:'HH:mm'},"
                +"{type:'select',key:'theme',title:'样式',options:[{value:'native',label:'原生'},{value:'custom',label:'自定义'}]},"
                +"{type:'button',key:'action',title:'动作',default:false,value:true}]");
    }
    private static void completeForm()throws Exception {
        File directory=Files.createTempDirectory("c17-complete-form-").toFile();
        CustomPluginRepository repo=new CustomPluginRepository(directory);
        CustomPluginRepository.Plugin plugin=install(repo,script(controls(),""));
        equal(6,plugin.descriptor.getJSONArray("ui").length(),"all six controls admitted");
        equal(5,plugin.state.length(),"every editable control owns independent state");
        equal("native",plugin.state.getString("theme"),"implicit select default saved");
        repo.setEnabled(plugin.id,true);
        repo.updateState(plugin.id,"seconds",true);
        repo.updateState(plugin.id,"size",145d);
        repo.updateState(plugin.id,"pattern","HH:mm:ss");
        repo.updateState(plugin.id,"theme","custom");
        repo.updateState(plugin.id,"action",true);
        plugin=new CustomPluginRepository(directory).list().get(0);
        equal(true,plugin.enabled,"enabled form reopens");
        equal(true,plugin.state.getBoolean("seconds"),"switch reopens");
        equal(145d,plugin.state.getDouble("size"),"slider reopens");
        equal("HH:mm:ss",plugin.state.getString("pattern"),"input reopens");
        equal("custom",plugin.state.getString("theme"),"select reopens");
        equal(true,plugin.state.getBoolean("action"),"button reopens");
        equal("HH:mm:ss",repo.compiledPlan().getJSONObject("settings").getString("clock_pattern"),"full form compiles effective settings");
        final String id=plugin.id;
        reject(()->repo.updateState(id,"theme","unknown"),"unknown choice");
        equal(true,repo.list().get(0).enabled,"invalid value preserves enabled form");
        long revision=stored(directory).getLong("revision");
        repo.resetState(id);
        plugin=new CustomPluginRepository(directory).list().get(0);
        equal(revision+1,stored(directory).getLong("revision"),"complete reset saves once");
        equal(true,plugin.enabled,"reset preserves enabled state");
        equal(false,plugin.state.getBoolean("seconds"),"reset restores switch default");
        equal(100d,plugin.state.getDouble("size"),"reset restores slider default");
        equal("HH:mm",plugin.state.getString("pattern"),"reset restores input default");
        equal("native",plugin.state.getString("theme"),"reset restores implicit select default");
        equal(false,plugin.state.getBoolean("action"),"reset restores button default");
        equal("HH:mm",repo.compiledPlan().getJSONObject("settings").getString("clock_pattern"),"reset publishes complete default plan");
        repo.setEnabled(id,false);repo.updateState(id,"seconds",true);repo.resetState(id);
        equal(false,repo.list().get(0).enabled,"reset never enables disabled plugin");
        equal(0,repo.compiledPlan().getJSONObject("settings").length(),"disabled reset has no overlays");
    }
    private static void units()throws Exception {
        JSONArray form=controls();String source=script(form,"");
        equal("%",CustomPluginEngine.evaluate(source,new JSONObject()).getJSONArray("ui").getJSONObject(2).getString("unit"),"slider unit admitted");
        form.getJSONObject(2).put("unit","");CustomPluginEngine.evaluate(script(form,""),new JSONObject());checks++;
        form.getJSONObject(2).put("unit","abcdefghijklmnopq");reject(()->CustomPluginEngine.evaluate(script(form,""),new JSONObject()),"unit longer than 16 characters");
        form.getJSONObject(2).put("unit",2);reject(()->CustomPluginEngine.evaluate(script(form,""),new JSONObject()),"non-string unit");
        form.getJSONObject(2).put("unit","%\0");reject(()->CustomPluginEngine.evaluate(script(form,""),new JSONObject()),"NUL unit");
        form.getJSONObject(2).remove("unit");form.getJSONObject(1).put("unit","%");reject(()->CustomPluginEngine.evaluate(script(form,""),new JSONObject()),"unit on switch");
        form.getJSONObject(1).remove("unit");form.getJSONObject(0).put("unit","%");reject(()->CustomPluginEngine.evaluate(script(form,""),new JSONObject()),"unit on text");
    }
    private static void failedResetPreservesRecord()throws Exception {
        File directory=Files.createTempDirectory("c17-reset-atomic-").toFile();
        CustomPluginRepository repo=new CustomPluginRepository(directory);String original=script(controls(),"");
        String id=install(repo,original).id;repo.updateState(id,"theme","custom");repo.updateState(id,"pattern","HH:mm:ss");repo.setEnabled(id,true);
        String[] broken={
            "if(C17.state.theme!=='custom')throw new Error('defaults unavailable');"+original,
            original.replace("name:'完整配置检查'","name:C17.state.theme==='custom'?'完整配置检查':'changed'"),
            original.replace("\"default\":false","\"default\":C17.state.seconds!==true"),
            original.replace("C17.define(","form[0].description=String(Math.random());C17.define(")
        };
        for(String source:broken) {
            replaceSource(directory,source);byte[] before=Files.readAllBytes(new File(directory,"library.json").toPath());
            reject(()->repo.resetState(id),"invalid complete defaults");
            equal(true,java.util.Arrays.equals(before,Files.readAllBytes(new File(directory,"library.json").toPath())),"failed reset keeps exact prior durable record");
            CustomPluginRepository.Plugin plugin=repo.list().get(0);
            equal(true,plugin.enabled,"failed reset preserves active plugin");
            equal("custom",plugin.state.getString("theme"),"failed reset preserves every old choice");
            equal("",plugin.error,"failed reset does not quarantine valid old plan");
            equal("HH:mm:ss",repo.compiledPlan().getJSONObject("settings").getString("clock_pattern"),"failed reset keeps old runtime plan");
        }
        replaceSource(directory,original);repo.resetState(id);equal("native",repo.list().get(0).state.getString("theme"),"fixed source can explicitly retry reset");
    }
    private static void conflictingReset()throws Exception {
        File directory=Files.createTempDirectory("c17-reset-conflict-").toFile();CustomPluginRepository repo=new CustomPluginRepository(directory);
        String first=script(new JSONArray("[{type:'switch',key:'usePattern',title:'格式',default:false}]"),"")
                .replace("settings:{clock_enabled:true,clock_pattern:C17.state.pattern||'HH:mm'}","settings:C17.state.usePattern===true?{clock_pattern:'HH:mm:ss'}:{clock_scale:100}");
        String id=install(repo,first).id;repo.updateState(id,"usePattern",true);repo.setEnabled(id,true);
        String second="C17.define({id:'dev.c17.other',name:'其他插件',version:'1',versionCode:1,apiVersion:1,capabilities:['settings'],ui:[{type:'slider',key:'scale',title:'字号',min:25,max:250,default:120}],settings:{clock_scale:typeof C17.state.scale==='number'?C17.state.scale:120},hooks:[]});";
        CustomPluginRepository.Plugin other=repo.install(repo.preview(new ByteArrayInputStream(second.getBytes(StandardCharsets.UTF_8)),"other.js"),false);repo.setEnabled(other.id,true);
        byte[] before=Files.readAllBytes(new File(directory,"library.json").toPath());
        reject(()->repo.resetState(id),"reset would conflict with another enabled plugin");
        equal(true,java.util.Arrays.equals(before,Files.readAllBytes(new File(directory,"library.json").toPath())),"conflicting reset commits nothing");
        equal("HH:mm:ss",repo.compiledPlan().getJSONObject("settings").getString("clock_pattern"),"conflict preserves original complete plan");
        equal(120d,repo.compiledPlan().getJSONObject("settings").getDouble("clock_scale"),"conflict preserves other plugin");
        repo.setEnabled(other.id,false);repo.resetState(id);
        equal(100d,repo.compiledPlan().getJSONObject("settings").getDouble("clock_scale"),"reset succeeds after user resolves conflict");
    }
    private static void dependentDefaults()throws Exception {
        File directory=Files.createTempDirectory("c17-reset-dependent-").toFile();CustomPluginRepository repo=new CustomPluginRepository(directory);
        String source=script(new JSONArray("[{type:'input',key:'pattern',title:'格式',default:'HH:mm'},"
                +"{type:'input',key:'label',title:'说明',default:'initial'}]"),"")
                .replace("\"default\":\"initial\"","\"default\":C17.state.pattern==='HH:mm'?'final':'initial'");
        String id=install(repo,source).id;repo.updateState(id,"label","changed");repo.resetState(id);
        equal("final",repo.list().get(0).state.getString("label"),"dependent defaults resolve before single commit");
        equal("HH:mm",repo.list().get(0).state.getString("pattern"),"first default preserved while resolving dependent defaults");
    }
    private static void quarantinedReset()throws Exception {
        File directory=Files.createTempDirectory("c17-reset-quarantined-").toFile();CustomPluginRepository repo=new CustomPluginRepository(directory);
        String source="if(C17.state.action===true)throw new Error('intentional bad action');"+script(controls(),"");
        String id=install(repo,source).id;repo.setEnabled(id,true);
        reject(()->repo.updateState(id,"action",true),"bad action quarantines plugin");
        equal(false,repo.list().get(0).enabled,"bad action is disabled");
        equal(true,!repo.list().get(0).error.isEmpty(),"failure reason visible before reset");
        repo.resetState(id);
        equal("",repo.list().get(0).error,"successful complete defaults clear old error");
        equal(false,repo.list().get(0).enabled,"reset never auto-enables quarantined plugin");
        equal(false,repo.list().get(0).state.getBoolean("action"),"reset uses declared safe button default");
        repo.setEnabled(id,true);equal(true,repo.list().get(0).enabled,"user can explicitly enable recovered defaults");
    }
    private static void completeFormAdmissionAndLegacy()throws Exception {
        File directory=Files.createTempDirectory("c17-configuration-admission-").toFile();CustomPluginRepository repo=new CustomPluginRepository(directory);
        for(JSONArray ui:new JSONArray[]{new JSONArray(),new JSONArray("[{type:'text',title:'说明'}]"),new JSONArray("[{type:'button',title:'执行',key:'action',value:true}]")}) {
            String source=script(ui,"");reject(()->install(repo,source),"new plugin with no editable configuration");
            equal(false,new File(directory,"library.json").exists(),"refused configuration never creates library");
        }
        String withoutUi=script(new JSONArray(),"").replace("ui:form,","");reject(()->install(repo,withoutUi),"missing ui declaration");
        for(String kind:new String[]{"switch","slider","input","select"}) {
            JSONArray ui=controls();JSONObject control=ui.getJSONObject("switch".equals(kind)?1:"slider".equals(kind)?2:"input".equals(kind)?3:4);
            JSONObject descriptor=CustomPluginEngine.evaluate(script(new JSONArray().put(control),""),new JSONObject());
            CustomPluginSchema.requireConfiguration(descriptor);equal(1,CustomPluginSchema.configurationCount(descriptor),"genuine editable configuration: "+kind);
        }
        String original=script(controls(),"");String id=install(repo,original).id;repo.setEnabled(id,true);
        String legacySource=script(new JSONArray(),"");JSONObject legacyDescriptor=CustomPluginEngine.evaluate(legacySource,new JSONObject());
        JSONObject data=stored(directory),record=data.getJSONObject("plugins").getJSONObject(id);
        record.put("source",legacySource).put("descriptor",legacyDescriptor).put("state",new JSONObject());
        Files.write(new File(directory,"library.json").toPath(),data.toString().getBytes(StandardCharsets.UTF_8));
        byte[] before=Files.readAllBytes(new File(directory,"library.json").toPath());
        CustomPluginRepository.Plugin legacy=repo.list().get(0);equal(0,CustomPluginSchema.configurationCount(legacy.descriptor),"existing legacy can be marked as missing configuration");
        equal(true,legacy.enabled,"new app does not silently disable legacy plugin");
        equal("HH:mm",repo.compiledPlan().getJSONObject("settings").getString("clock_pattern"),"existing legacy plan remains readable");
        equal(true,java.util.Arrays.equals(before,Files.readAllBytes(new File(directory,"library.json").toPath())),"reading legacy does not rewrite settings or enabled state");
        String invalidUpgrade=legacySource.replace("versionCode:1","versionCode:2");
        reject(()->repo.preview(new ByteArrayInputStream(invalidUpgrade.getBytes(StandardCharsets.UTF_8)),"legacy-upgrade.js"),"new upgrade without configuration");
        equal(true,java.util.Arrays.equals(before,Files.readAllBytes(new File(directory,"library.json").toPath())),"refused upgrade keeps exact legacy record");
        repo.setEnabled(id,false);equal(false,repo.list().get(0).enabled,"legacy can still be explicitly disabled");
        repo.setEnabled(id,true);equal(true,repo.list().get(0).enabled,"legacy can still be explicitly managed");
        String validUpgrade=original.replace("versionCode:1","versionCode:2");
        repo.install(repo.preview(new ByteArrayInputStream(validUpgrade.getBytes(StandardCharsets.UTF_8)),"complete-upgrade.js"),true);
        equal(4,CustomPluginSchema.configurationCount(repo.list().get(0).descriptor),"legacy upgrade now supplies complete form");
        equal(false,repo.list().get(0).enabled,"complete legacy upgrade defaults disabled");
    }
    private static void previousRowsCompatibility()throws Exception {
        android.os.Bundle saved=SettingsSnapshot.fromPreferences(java.util.Collections.emptyMap());
        JSONObject old=new JSONObject().put(QsCardSettings.GRID_6,true);
        CustomPluginSettings.validate(old);android.os.Bundle projected=CustomPluginSettings.overlay(saved,old);
        equal(4f,projected.get(QsCardSettings.GRID_ROWS),"previous API1 row switch no longer controls runtime copy");
        equal(4f,saved.get(QsCardSettings.GRID_ROWS),"old API1 overlay never changes saved row selector");
        old.put(QsCardSettings.GRID_ROWS,5);projected=CustomPluginSettings.overlay(saved,old);
        equal(4f,projected.get(QsCardSettings.GRID_ROWS),"legacy explicit rows also leave saved host values untouched");
        saved.putBoolean(StatusBarSettings.SAFE_MODE,true);equal(4f,CustomPluginSettings.overlay(saved,old).get(QsCardSettings.GRID_ROWS),"safety suppresses old and new plugin rows");
        File directory=Files.createTempDirectory("c17-plugin-row-alias-").toFile();CustomPluginRepository repo=new CustomPluginRepository(directory);
        String first="C17.define({id:'dev.c17.legacy-row',name:'旧六行',version:'1',versionCode:1,apiVersion:1,capabilities:['settings'],ui:[{type:'switch',key:'six',title:'六行',default:true}],settings:{qs_card_six_rows_enabled:C17.state.six!==false},hooks:[]});";
        String second="C17.define({id:'dev.c17.current-row',name:'行数',version:'1',versionCode:1,apiVersion:1,capabilities:['settings'],ui:[{type:'slider',key:'rows',title:'行数',min:4,max:7,step:1,default:5}],settings:{qs_card_grid_rows:typeof C17.state.rows==='number'?C17.state.rows:5},hooks:[]});";
        String firstId=repo.install(repo.preview(new ByteArrayInputStream(first.getBytes(StandardCharsets.UTF_8)),"old.js"),false).id;
        String secondId=repo.install(repo.preview(new ByteArrayInputStream(second.getBytes(StandardCharsets.UTF_8)),"new.js"),false).id;
        repo.setEnabled(firstId,true);reject(()->repo.setEnabled(secondId,true),"old and current row aliases conflict across plugins");
        equal(true,repo.compiledPlan().getJSONObject("settings").getBoolean(QsCardSettings.GRID_6),"failed conflicting enable keeps readable old plan");
    }
    private static void moreModeCompatibility()throws Exception {
        equal(java.util.Arrays.asList("always","hidden","more"),java.util.Arrays.asList(QsCardSettings.SMALL_MODES),"current choices omit legacy swipe gesture");
        for(String mode:new String[]{"always","hidden","more","swipe"}) {
            equal(true,QsCardSettings.validSmallMode(mode),"current and exact historical mode admitted");
            JSONObject output=new JSONObject().put(QsCardSettings.SMALL_MODE,mode);CustomPluginSettings.validate(output);
            android.os.Bundle saved=SettingsSnapshot.fromPreferences(java.util.Collections.singletonMap(QsCardSettings.SMALL_MODE,"swipe"));
            android.os.Bundle runtime=CustomPluginSettings.overlay(saved,output);
            equal("swipe",saved.getString(QsCardSettings.SMALL_MODE),"compatibility never changes persisted source value");
            equal("swipe",runtime.getString(QsCardSettings.SMALL_MODE),"legacy output no longer controls host visibility or normalizes saved values");
            saved.putBoolean(StatusBarSettings.SAFE_MODE,true);
            equal("swipe",CustomPluginSettings.overlay(saved,output).getString(QsCardSettings.SMALL_MODE),"safe overlay retains source instead of enabling navigation");
        }
        for(Object bad:new Object[]{null,true,1,"","SWIPE","swipe-more","native"}) {
            equal(false,QsCardSettings.validSmallMode(bad),"malformed mode rejected");
            JSONObject output=new JSONObject().put(QsCardSettings.SMALL_MODE,bad==null?JSONObject.NULL:bad);
            reject(()->CustomPluginSettings.validate(output),"compatibility does not bypass mode/type validation");
        }
        equal("always",QsCardSettings.normalizeSmallMode(null),"absent runtime mode falls back safely");
        equal("always",QsCardSettings.normalizeSmallMode("future"),"unknown runtime mode falls back safely");
        JSONObject unrelated=new JSONObject().put("clock_enabled","swipe");
        reject(()->CustomPluginSettings.validate(unrelated),"legacy alias does not bypass validation for another key");
    }
    private static void retiredOutputsCompatibility()throws Exception {
        JSONObject outputs=new JSONObject();
        for(String key:QsCardSettings.BOOLEANS.keySet()) {
            outputs.put(key,true);CustomPluginSettings.validate(new JSONObject().put(key,false));
            for(Object bad:new Object[]{"true",1,JSONObject.NULL})
                reject(()->CustomPluginSettings.validate(new JSONObject().put(key,bad)),"retired flag type "+key);
        }
        outputs.put(QsCardSettings.GRID_ROWS,7).put(QsCardSettings.WALLPAPER_OPACITY,37.5)
                .put(QsCardSettings.SMALL_MODE,"swipe").put(QsCardSettings.WALLPAPER_REVISION,"a".repeat(64));
        CustomPluginSettings.validate(outputs);
        for(Object bad:new Object[]{"6",true,3,8,4.5,Double.NaN,Double.POSITIVE_INFINITY,JSONObject.NULL})
            reject(()->CustomPluginSettings.validate(new JSONObject().put(QsCardSettings.GRID_ROWS,bad)),"retired row type/range");
        for(Object bad:new Object[]{"50",true,-.1,100.1,Double.NaN,Double.NEGATIVE_INFINITY,JSONObject.NULL})
            reject(()->CustomPluginSettings.validate(new JSONObject().put(QsCardSettings.WALLPAPER_OPACITY,bad)),"retired opacity type/range");
        for(Object bad:new Object[]{true,1,JSONObject.NULL,"x".repeat(513)})
            reject(()->CustomPluginSettings.validate(new JSONObject().put(QsCardSettings.WALLPAPER_REVISION,bad)),"retired revision type/length");
        android.os.Bundle saved=SettingsSnapshot.fromPreferences(java.util.Collections.emptyMap());
        outputs.put(StatusBarSettings.CLOCK_SCALE,125);android.os.Bundle applied=CustomPluginSettings.overlay(saved,outputs);
        for(String key:QsCardSettings.BOOLEANS.keySet())equal(saved.get(key),applied.get(key),"retired flag stays saved "+key);
        for(String key:QsCardSettings.NUMBERS.keySet())equal(saved.get(key),applied.get(key),"retired number stays saved "+key);
        for(String key:QsCardSettings.STRINGS.keySet())equal(saved.get(key),applied.get(key),"retired string stays saved "+key);
        equal(125f,applied.get(StatusBarSettings.CLOCK_SCALE),"retired outputs do not invalidate unrelated supported overrides");
        android.os.Bundle absent=CustomPluginSettings.overlay(new android.os.Bundle(),outputs);
        equal(1,absent.keySet().size(),"retired outputs never add host preference keys");
    }
    private static CustomPluginRepository.ConfigurationPreview configuration(CustomPluginRepository repo,String id,int version,String json)throws Exception {
        return repo.previewConfiguration(id,version,new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
    private static void configurationFiles()throws Exception {
        File directory=Files.createTempDirectory("c17-configuration-files-").toFile();
        CustomPluginRepository repo=new CustomPluginRepository(directory);
        String source=script(controls(),"");String id=install(repo,source).id;
        repo.setEnabled(id,true);repo.updateState(id,"size",175d);repo.updateState(id,"pattern","HH:mm:ss");
        String json=repo.exportConfiguration(id,1);JSONObject exported=new JSONObject(json);
        equal("c17-plugin-configuration",exported.getString("format"),"configuration file has its own format");
        equal(id,exported.getString("pluginId"),"export is bound to one plugin");
        equal(5,exported.getJSONObject("state").length(),"every declared persistent value is exported");
        equal(false,exported.has("enabled"),"enablement is never transferred by configuration");
        equal(false,exported.has("source"),"configuration export contains no plugin code");
        equal(false,exported.has("settings"),"configuration export contains no host preference overlays");
        CustomPluginRepository.ConfigurationPreview preview=configuration(repo,id,1,json);
        equal(5,preview.settingCount,"preview counts the values reviewed by the user");
        repo.resetState(id);repo.setEnabled(id,false);repo.importConfiguration(preview);
        CustomPluginRepository.Plugin restored=new CustomPluginRepository(directory).list().get(0);
        equal(false,restored.enabled,"configuration restore preserves current disabled state");
        equal(175d,restored.state.getDouble("size"),"configuration restores saved numeric precision");
        equal("HH:mm:ss",restored.state.getString("pattern"),"configuration restores custom text");
        repo.setEnabled(id,true);repo.importConfiguration(preview);
        equal(true,new CustomPluginRepository(directory).list().get(0).enabled,"configuration restore preserves enabled state after a cold reopen");
        JSONObject partial=new JSONObject(json).put("state",new JSONObject().put("seconds",true));
        repo.importConfiguration(configuration(repo,id,1,partial.toString()));
        restored=repo.list().get(0);
        equal(true,restored.state.getBoolean("seconds"),"partial compatible configuration applies its explicit values");
        equal("HH:mm:ss",restored.state.getString("pattern"),"partial import preserves values outside the file");
        byte[] before=Files.readAllBytes(new File(directory,"library.json").toPath());
        for(JSONObject invalid:new JSONObject[]{
                new JSONObject(json).put("format","host-preferences"),new JSONObject(json).put("formatVersion",2),
                new JSONObject(json).put("pluginId","dev.other.plugin"),new JSONObject(json).put("pluginVersionCode",2),
                new JSONObject(json).put("pluginVersionCode",1.5),new JSONObject(json).put("pluginVersionCode","1"),
                new JSONObject(json).put("source","throw new Error('executed')"),new JSONObject(json).put("state",new JSONObject().put("clock_enabled",false)),
                new JSONObject(json).put("state",new JSONObject().put("size",251)),new JSONObject(json).put("state",new JSONObject().put("seconds","true")),
                new JSONObject(json).put("state",new JSONObject().put("size",JSONObject.NULL)),new JSONObject(json).put("state",new JSONArray())})
            reject(()->configuration(repo,id,1,invalid.toString()),"invalid or foreign configuration file");
        reject(()->configuration(repo,id,1,"not JSON"),"malformed configuration file");
        reject(()->repo.previewConfiguration(id,1,new ByteArrayInputStream(new byte[4*1024*1024+1])),"oversized configuration file");
        equal(true,java.util.Arrays.equals(before,Files.readAllBytes(new File(directory,"library.json").toPath())),"all failed previews preserve the complete saved library");
        reject(()->repo.exportConfiguration(id,2),"stale configuration export target");
        reject(()->configuration(repo,id,2,json),"stale configuration import target");
        String next=source.replace("versionCode:1","versionCode:2");
        try(CustomPluginRepository.Preview update=repo.preview(new ByteArrayInputStream(next.getBytes(StandardCharsets.UTF_8)),"upgrade.js")){repo.install(update,true);}
        byte[] upgraded=Files.readAllBytes(new File(directory,"library.json").toPath());
        reject(()->repo.importConfiguration(preview),"plugin upgraded after the configuration confirmation opened");
        equal(true,java.util.Arrays.equals(upgraded,Files.readAllBytes(new File(directory,"library.json").toPath())),"stale confirmation preserves upgraded plugin");
        repo.importConfiguration(configuration(repo,id,2,json));
        equal(175d,repo.list().get(0).state.getDouble("size"),"compatible older configuration imports after a plugin upgrade");
        equal(false,repo.list().get(0).enabled,"import never re-enables a freshly upgraded plugin");
    }
    /** Versioned, explicitly reviewed aliases import old names without weakening unknown-key rejection. */
    private static void configurationAliases()throws Exception {
        JSONArray oldUi=new JSONArray("[{type:'slider',key:'old_amount',title:'旧数值',min:0,max:100,default:10},{type:'switch',key:'old_on',title:'旧开关',default:false}]");
        File directory=Files.createTempDirectory("c17-configuration-aliases-").toFile();
        CustomPluginRepository repo=new CustomPluginRepository(directory);
        String id=install(repo,runtimeScript(oldUi)).id;
        repo.updateState(id,"old_amount",37);repo.updateState(id,"old_on",true);
        String exported=repo.exportConfiguration(id,1);
        JSONArray currentUi=new JSONArray("[{type:'slider',key:'amount',title:'数值',min:0,max:100,default:10},{type:'switch',key:'on',title:'开关',default:false},{type:'action',key:'refresh',title:'刷新'},{type:'text',key:'note',title:'说明'}]");
        JSONObject descriptor=CustomPluginEngine.evaluate(runtimeScript(currentUi),new JSONObject())
                .put("versionCode",2).put("minimumHost",265).put("features",new JSONArray().put("config.aliases"))
                .put("configurationAliases",new JSONObject("{old_amount:'amount',old_on:'on'}"));
        String next="C17.define("+descriptor+");";
        try(CustomPluginRepository.Preview update=repo.preview(new ByteArrayInputStream(next.getBytes(StandardCharsets.UTF_8)),"next.js")){repo.install(update,true);}
        CustomPluginRepository.ConfigurationPreview aliasOnly=configuration(repo,id,2,exported);
        equal(2,aliasOnly.settingCount,"alias preview counts canonical persistent settings");
        repo.setEnabled(id,true);repo.importConfiguration(aliasOnly);
        equal(37,repo.list().get(0).state.getInt("amount"),"older alias-only numeric value maps to current control");
        equal(true,repo.list().get(0).state.getBoolean("on"),"older alias-only switch maps to current control");
        equal(true,repo.list().get(0).enabled,"valid older alias import preserves enabled state");
        JSONObject mixed=new JSONObject(exported);mixed.getJSONObject("state").put("amount",81).put("on",false);
        repo.importConfiguration(configuration(repo,id,2,mixed.toString()));
        equal(81,repo.list().get(0).state.getInt("amount"),"canonical number wins a differing alias");
        equal(false,repo.list().get(0).state.getBoolean("on"),"canonical false wins a true alias");
        JSONObject rewritten=new JSONObject(repo.exportConfiguration(id,2));
        equal(false,rewritten.getJSONObject("state").has("old_amount"),"new exports contain only canonical controls");
        byte[] before=Files.readAllBytes(new File(directory,"library.json").toPath());
        for(JSONObject invalid:new JSONObject[]{
                new JSONObject(exported).put("pluginVersionCode",2),new JSONObject(exported).put("pluginVersionCode",3),
                new JSONObject(exported).put("state",new JSONObject().put("old_amount",101)),
                new JSONObject(exported).put("state",new JSONObject().put("old_amount","37")),
                new JSONObject(exported).put("state",new JSONObject().put("old_on","true")),
                new JSONObject(exported).put("state",new JSONObject().put("old_on",JSONObject.NULL)),
                new JSONObject(exported).put("state",new JSONObject().put("old_amount",JSONObject.NULL).put("amount",44)),
                new JSONObject(exported).put("state",new JSONObject().put("unknown",17))})
            reject(()->configuration(repo,id,2,invalid.toString()),"invalid aliases, unknown settings and non-older aliases rejected");
        equal(true,java.util.Arrays.equals(before,Files.readAllBytes(new File(directory,"library.json").toPath())),"invalid alias imports leave state and enabled record byte-for-byte intact");
        for(Object invalid:new Object[]{JSONObject.NULL,"old_amount",new JSONArray(),
                new JSONObject("{amount:'on'}"),new JSONObject("{old:'missing'}"),new JSONObject("{old:'refresh'}"),
                new JSONObject("{note:'amount'}"),new JSONObject("{old:'amount',older:'amount'}"),
                new JSONObject("{old:'older',older:'amount'}"),new JSONObject("{old:'old'}"),
                new JSONObject("{constructor:'amount'}"),new JSONObject("{old:'prototype'}"),new JSONObject("{'bad key':'amount'}")})
            reject(()->CustomPluginSchema.validate(new JSONObject(descriptor.toString()).put("configurationAliases",invalid)),"invalid reviewed alias metadata");
        JSONObject tooMany=new JSONObject();for(int n=0;n<513;n++)tooMany.put("old_"+n,"new_"+n);
        reject(()->CustomPluginSchema.validate(new JSONObject(descriptor.toString()).put("configurationAliases",tooMany)),"alias count is bounded");
        for(JSONObject invalid:new JSONObject[]{new JSONObject(descriptor.toString()).put("apiVersion",1),
                new JSONObject(descriptor.toString()).put("minimumHost",264),new JSONObject(descriptor.toString()).put("features",new JSONArray())})
            reject(()->CustomPluginSchema.validate(invalid),"aliases require the versioned host capability");
        JSONObject changed=new JSONObject(descriptor.toString()).put("configurationAliases",new JSONObject("{older_amount:'amount',old_on:'on'}"));
        equal(false,CustomPluginSchema.sameIdentity(descriptor,changed),"reviewed aliases are part of immutable descriptor identity");
        CustomPluginSchema.validate(new JSONObject(descriptor.toString()).put("configurationAliases",new JSONObject()));
        equal(true,CustomPluginSupport.FEATURES.contains("config.aliases"),"host advertises configuration aliases");
        repo.setEnabled(id,false);repo.importConfiguration(aliasOnly);
        equal(false,repo.list().get(0).enabled,"valid alias import also preserves disabled state");
    }
    public static void main(String[] arguments)throws Exception {
        configurationFiles();configurationAliases();nestedColorUpgrade();structuredFormContract();controlValues();presetContract();completePresetContract();renamedStateUpgrade();completeForm();units();failedResetPreservesRecord();conflictingReset();dependentDefaults();quarantinedReset();completeFormAdmissionAndLegacy();previousRowsCompatibility();moreModeCompatibility();retiredOutputsCompatibility();
        System.out.println("CustomPluginConfigurationCheck "+checks+" checks passed");
    }
}
