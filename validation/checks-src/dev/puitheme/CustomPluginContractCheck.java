// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.view.View;
import io.github.libxposed.service.XposedService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Admission -> actual ZIP/library -> capability/scope evidence -> public native View ABI. */
public final class CustomPluginContractCheck {
    private static int checks;
    private static void yes(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private interface Task{void run()throws Throwable;}
    private static void reject(Task task,String why)throws Throwable{checks++;try{task.run();}catch(Exception expected){return;}throw new AssertionError("Accepted "+why);}
    private static JSONObject descriptor()throws Exception{return new JSONObject().put("id","contract.sample").put("name","Generic contract").put("version","1.0").put("versionCode",1).put("apiVersion",2)
            .put("capabilities",new JSONArray().put("runtime")).put("runtime",new JSONObject().put("source","// plugin-owned runtime"))
            .put("usage","适用系统说明和功能概况").put("scopes",new JSONArray().put("com.android.systemui")).put("minimumHost",1)
            .put("features",new JSONArray().put("runtime.java").put("ui.groups"))
            .put("settings",new JSONObject()).put("hooks",new JSONArray()).put("ui",new JSONArray()
                    .put(new JSONObject().put("type","switch").put("title","业务开关").put("key","feature").put("default",false).put("group","功能").put("section","features"))
                    .put(new JSONObject().put("type","action").put("title","管理操作").put("key","manage").put("group","管理").put("section","management"))
                    .put(new JSONObject().put("type","status").put("title","状态").put("key","state").put("group","功能")));}
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static byte[] bundle(JSONObject descriptor,boolean code,boolean mismatch)throws Exception{
        JSONObject manifest=new JSONObject(descriptor.toString());manifest.remove("ui");manifest.remove("settings");manifest.remove("hooks");manifest.put("entry","main.js");if(mismatch)manifest.put("usage","different");
        Map<String,byte[]> entries=new LinkedHashMap<>();entries.put("plugin.json",bytes(manifest.toString()));entries.put("main.js",bytes("C17.define("+descriptor+");"));if(code)entries.put("settings.dex",bytes("admitted native settings fixture"));
        ByteArrayOutputStream output=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(output)){for(Map.Entry<String,byte[]> entry:entries.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}return output.toByteArray();
    }
    private static void admission()throws Throwable{
        JSONObject good=descriptor();CustomPluginSchema.validate(good);CustomPluginSchema.requireConfiguration(good);CustomPluginSupport.requireCompatible(good,210);
        yes(CustomPluginSchema.configurationCount(good)==1,"groups, management actions and statuses do not create fake persisted settings");
        for(String field:new String[]{"usage","scopes","minimumHost","features","nativeSettings"}){
            JSONObject changed=new JSONObject(good.toString());if(field.equals("usage"))changed.put(field,"new");else if(field.equals("minimumHost"))changed.put(field,210);else if(field.equals("nativeSettings"))changed.put(field,new JSONObject().put("dex","settings.dex").put("className",NativeEntry.class.getName()));else changed.put(field,new JSONArray().put("changed.scope"));
            yes(!CustomPluginSchema.sameIdentity(good,changed),"reviewed identity includes "+field);
        }
        for(Object invalid:new Object[]{0,-1,1.5,"210",true,JSONObject.NULL}){JSONObject value=new JSONObject(good.toString()).put("minimumHost",invalid);reject(()->CustomPluginSchema.validate(value),"invalid minimumHost "+invalid);}
        for(Object invalid:new Object[]{new JSONArray().put("runtime.java").put("runtime.java"),new JSONArray().put("root *"),"runtime.java",new JSONArray().put(2)}){JSONObject value=new JSONObject(good.toString()).put("features",invalid);reject(()->CustomPluginSchema.validate(value),"invalid feature requirements");}
        for(Object invalid:new Object[]{new JSONArray().put("com.android.systemui").put("com.android.systemui"),new JSONArray().put("control-center"),new JSONArray().put("../scope"),new JSONArray().put(true)}){JSONObject value=new JSONObject(good.toString()).put("scopes",invalid);reject(()->CustomPluginSchema.validate(value),"invalid executable scope");}
        JSONObject future=new JSONObject(good.toString()).put("minimumHost",211);CustomPluginSupport.requireCompatible(future,1);yes(true,"numeric minimumHost is descriptive, not admission");
        JSONObject confirming=new JSONObject(good.toString());
        JSONObject decision=new JSONObject().put("type","action").put("key","skip_backup").put("title","跳过安全回退").put("confirmation","跳过后将缺少完整回退记录，是否继续？");
        confirming.getJSONArray("ui").put(decision);CustomPluginSchema.validate(confirming);
        yes(CustomPluginSchema.form(confirming).getJSONArray("actions").toList().contains("skip_backup"),"confirmed actions retain the normal runtime callback contract");
        decision.put("confirmation","");reject(()->CustomPluginSchema.validate(confirming),"empty confirmation cannot pretend to display risk");
        decision.put("confirmation","risk").put("type","switch");reject(()->CustomPluginSchema.validate(confirming),"persistent switches cannot silently accept one-shot confirmation");
        JSONObject absent=new JSONObject(good.toString()).put("features",new JSONArray().put("future.contract"));reject(()->CustomPluginSupport.requireCompatible(absent,210),"unavailable generic capability");
        JSONObject foreign=new JSONObject(good.toString()).put("scopes",new JSONArray().put("com.example.target"));CustomPluginSchema.validate(foreign);yes(!CustomPluginSupport.executionProblem(foreign).isEmpty(),"valid declarations do not fabricate a supported cross-process engine");reject(()->CustomPluginSupport.requireExecutable(foreign),"unsupported executable scope");
        JSONObject legacy=new JSONObject(good.toString());legacy.remove("scopes");yes(CustomPluginSupport.scopes(legacy).getString(0).equals("com.android.systemui"),"existing API2 packages retain their actual SystemUI engine scope");
        for(String path:new String[]{"../settings.dex","/settings.dex","a//settings.dex","a/./settings.dex","settings.apk","含有中文.dex"}){JSONObject value=new JSONObject(good.toString()).put("nativeSettings",new JSONObject().put("dex",path).put("className",NativeEntry.class.getName()));reject(()->CustomPluginSchema.validate(value),"unsafe or unsupported native code path");}
        JSONObject invalidGroup=new JSONObject(good.toString());invalidGroup.getJSONArray("ui").getJSONObject(0).put("section","prerequisites");reject(()->CustomPluginSchema.validate(invalidGroup),"a plugin cannot replace the host prerequisite section");
    }
    private static void packageChain()throws Throwable{
        File root=Files.createTempDirectory("c17-contract-").toFile();CustomPluginRepository repository=new CustomPluginRepository(root);
        JSONObject descriptor=descriptor().put("nativeSettings",new JSONObject().put("dex","settings.dex").put("className",NativeEntry.class.getName()));CustomPluginSchema.validate(descriptor);
        reject(()->repository.preview(new ByteArrayInputStream(bytes("C17.define("+descriptor+");")),"native.js"),"native UI metadata without its ZIP code");
        reject(()->repository.preview(new ByteArrayInputStream(bundle(descriptor,false,false)),"missing.zip"),"ZIP without declared native settings code");
        reject(()->repository.preview(new ByteArrayInputStream(bundle(descriptor,true,true)),"mismatch.zip"),"manifest/descriptor prerequisite identity mismatch");
        try(CustomPluginRepository.Preview preview=repository.preview(new ByteArrayInputStream(bundle(descriptor,true,false)),"native.zip")){repository.install(preview,false);}
        CustomPluginRepository.Plugin installed=repository.list().get(0);yes(installed.descriptor.toString().equals(descriptor.toString()),"all generic metadata and sections survive the actual saved library");
        byte[] before=Files.readAllBytes(new File(root,"library.json").toPath());repository.list();repository.compiledPlan();yes(Arrays.equals(before,Files.readAllBytes(new File(root,"library.json").toPath())),"reading native settings metadata never rewrites the library");
        File code=repository.nativeSettingsFile(installed.id,1);yes(code.isFile(),"disabled plugins can open their admitted settings without injecting runtime code");
        CustomPluginRepository.NativeSettingsSession first=repository.nativeSettingsSession(installed.id,1);int[] invalidated={0};first.onInvalidated(()->invalidated[0]++);
        repository.updateStateVersion(installed.id,1,"feature",true);yes(repository.list().get(0).state.getBoolean("feature"),"native UI uses the same validated independent settings path");
        repository.updateStateSession(first,"feature",false);yes(first.isCurrent()&&!repository.list().get(0).state.getBoolean("feature"),"ordinary configuration changes retain the active installation lease");
        reject(()->repository.updateStateVersion(installed.id,2,"feature",false),"stale native UI version cannot write another version");
        Files.write(code.toPath(),bytes("modified native settings fixture"));reject(()->repository.nativeSettingsFile(installed.id,1),"changed native settings code");
        repository.delete(installed.id);yes(!first.isCurrent()&&invalidated[0]==1,"deletion synchronously invalidates the old native page before queued creation or callbacks");
        try(CustomPluginRepository.Preview preview=repository.preview(new ByteArrayInputStream(bundle(descriptor,true,false)),"native.zip")){repository.install(preview,false);}
        reject(()->repository.updateStateSession(first,"feature",true),"deleting and reinstalling the exact same ID/version cannot revive an old page callback");
        CustomPluginRepository.NativeSettingsSession current=repository.nativeSettingsSession(installed.id,1);
        JSONObject newer=new JSONObject(descriptor.toString()).put("versionCode",2).put("version","2.0");
        try(CustomPluginRepository.Preview preview=repository.preview(new ByteArrayInputStream(bundle(newer,true,false)),"native.zip")){repository.install(preview,true);}
        yes(!current.isCurrent(),"updating a plugin synchronously invalidates its old native page lease");
        reject(()->repository.updateStateSession(current,"feature",true),"queued native writes cannot cross a version update");
        CustomPluginRepository.NativeSettingsSession closed=repository.nativeSettingsSession(installed.id,2);closed.close();reject(()->repository.updateStateSession(closed,"feature",true),"closed page cannot write after cleanup");
        JSONObject foreign=descriptor().put("id","contract.foreign").put("scopes",new JSONArray().put("com.example.target"));
        try(CustomPluginRepository.Preview preview=repository.preview(new ByteArrayInputStream(bytes("C17.define("+foreign+");")),"foreign.js")){repository.install(preview,false);}
        reject(()->repository.setEnabled("contract.foreign",true),"unsupported target cannot be enabled even if its package is present or selected in the framework");
        yes(!repository.list().stream().filter(value->value.id.equals("contract.foreign")).findFirst().get().enabled,"unsupported runtime remains stopped after refused enable");
    }
    private static void scopes()throws Throwable{
        Field services=AppFrameworkStatus.class.getDeclaredField("SERVICES"),snapshot=AppFrameworkStatus.class.getDeclaredField("snapshot");services.setAccessible(true);snapshot.setAccessible(true);
        @SuppressWarnings("unchecked") Map<XposedService,Object> live=(Map<XposedService,Object>)services.get(null);Map<XposedService,Object> saved=new LinkedHashMap<>(live);Object old=snapshot.get(null);
        try{
            live.clear();yes(!AppFrameworkStatus.queryScopes().known,"no official framework service reports unknown rather than unchecked");
            Constructor<?> record=Class.forName("dev.puitheme.AppFrameworkStatus$Connection").getDeclaredConstructor();record.setAccessible(true);Object connection=record.newInstance();
            Constructor<?> create=AppFrameworkStatus.Snapshot.class.getDeclaredConstructor(AppFrameworkStatus.State.class,int.class,String.class,String.class,long.class,String.class);create.setAccessible(true);Object connected=create.newInstance(AppFrameworkStatus.State.CONNECTED,102,"LSPosed fixture","test",1L,"connected");
            Field result=connection.getClass().getDeclaredField("result");result.setAccessible(true);result.set(connection,connected);snapshot.set(null,connected);
            XposedService service=new XposedService(null);live.put(service,connection);
            yes(AppFrameworkStatus.queryScopes().known&&AppFrameworkStatus.queryScopes().packages.isEmpty(),"a real successful empty getScope result means no packages selected");
            service.scopes=new ArrayList<>(Arrays.asList("com.android.systemui","android"));AppFrameworkStatus.ScopeSnapshot scope=AppFrameworkStatus.queryScopes();yes(scope.known&&scope.packages.contains("com.android.systemui"),"selection is derived from official getScope package data");
            service.scopeUnavailable=true;yes(!AppFrameworkStatus.queryScopes().known,"framework scope RPC failure returns unknown even with metadata connected");service.scopeUnavailable=false;
            service.scopes=null;yes(!AppFrameworkStatus.queryScopes().known,"a missing framework list is unknown");
            service.scopes=new ArrayList<>();AppFrameworkStatus.ScopeSnapshot empty=AppFrameworkStatus.queryScopes();
            yes(empty.known&&empty.packages.isEmpty(),"scope lookup recovers from unavailable to an authoritative unchecked result");
            service.scopes.add("com.android.systemui");yes(AppFrameworkStatus.queryScopes().packages.contains("com.android.systemui"),"a later framework selection replaces the old unchecked result");
            live.clear();yes(!AppFrameworkStatus.queryScopes().known,"disconnect after a successful query cannot reuse its selected package evidence");
            yes(scope.known&&scope.packages.contains("com.android.systemui"),"old scope snapshot is immutable evidence rather than a mutable current-state substitute");
        }finally{live.clear();live.putAll(saved);snapshot.set(null,old);}
    }
    private static void groupedUpgradeKeepsState()throws Throwable{
        File root=Files.createTempDirectory("c17-detail-upgrade-").toFile();CustomPluginRepository repository=new CustomPluginRepository(root);
        JSONObject original=descriptor().put("minimumHost",210);JSONArray form=original.getJSONArray("ui");
        form.put(new JSONObject().put("type","slider").put("key","intensity").put("title","强度").put("min",0).put("max",100).put("default",50).put("section","features"));
        form.put(new JSONObject().put("type","input").put("key","note").put("title","备注").put("default","").put("section","management"));
        form.put(new JSONObject().put("type","select").put("key","mode").put("title","模式").put("default","system")
                .put("options",new JSONArray().put(new JSONObject().put("value","system").put("label","系统")).put(new JSONObject().put("value","custom").put("label","自定义"))));
        try(CustomPluginRepository.Preview preview=repository.preview(new ByteArrayInputStream(bundle(original,false,false)),"detail.zip")){repository.install(preview,false);}
        String id=original.getString("id");repository.updateState(id,"feature",true);repository.updateState(id,"intensity",73);repository.updateState(id,"note","已保存的备注");repository.updateState(id,"mode","custom");repository.setEnabled(id,true);
        JSONObject other=descriptor().put("id","contract.other");
        try(CustomPluginRepository.Preview preview=repository.preview(new ByteArrayInputStream(bundle(other,false,false)),"other.zip")){repository.install(preview,false);}
        repository.updateState("contract.other","feature",true);
        JSONObject next=new JSONObject(original.toString()).put("versionCode",2).put("version","1.1");JSONArray moved=new JSONArray();
        for(int index=form.length()-1;index>=0;index--){JSONObject control=new JSONObject(form.getJSONObject(index).toString());control.put("group","新版分组").put("section","intensity".equals(control.optString("key"))?"management":"features");moved.put(control);}
        next.put("ui",moved);CustomPluginSupport.requireCompatible(next,210);
        try(CustomPluginRepository.Preview preview=repository.preview(new ByteArrayInputStream(bundle(next,false,false)),"detail.zip")){repository.install(preview,true);}
        CustomPluginRepository.Plugin updated=repository.list().stream().filter(value->value.id.equals(id)).findFirst().get();
        yes(updated.state.getBoolean("feature")&&updated.state.getInt("intensity")==73&&updated.state.getString("note").equals("已保存的备注")&&updated.state.getString("mode").equals("custom"),"all four persisted control types survive reordered sections and groups by key");
        yes(updated.state.length()==4&&!updated.state.has("manage")&&!updated.state.has("state"),"regrouping never persists action/status or display metadata");
        yes(!updated.enabled&&updated.descriptor.getInt("minimumHost")==210,"a generic UI upgrade retains the host contract and remains disabled until explicitly enabled");
        yes(updated.descriptor.getJSONArray("ui").getJSONObject(0).getString("key").equals("mode"),"the installed descriptor preserves the author's new display order");
        repository.updateState(id,"intensity",81);yes(repository.list().stream().filter(value->value.id.equals(id)).findFirst().get().state.getInt("intensity")==81,"a persisted control moved to management remains editable");
        yes(repository.list().stream().filter(value->value.id.equals("contract.other")).findFirst().get().state.getBoolean("feature"),"another plugin's stored configuration is untouched by regrouping");
        repository.resetState(id);CustomPluginRepository.Plugin reset=repository.list().stream().filter(value->value.id.equals(id)).findFirst().get();
        yes(!reset.state.getBoolean("feature")&&reset.state.getInt("intensity")==50&&reset.state.getString("note").isEmpty()&&reset.state.getString("mode").equals("system"),"host reset reaches every editable control across both sections");
    }
    public static final class NativeEntry {public static View createView(Context context,String state,InvocationHandler bridge)throws Throwable{bridge.invoke(null,null,new Object[]{"state"});return new View(context);}}
    public static final class WrongEntry {public static Object createView(Context context,String state,InvocationHandler bridge){return new Object();}}
    public static final class BrokenEntry {static final IllegalStateException ERROR=new IllegalStateException("native entry failure");public static View createView(Context context,String state,InvocationHandler bridge){throw ERROR;}}
    private static void nativeEntry()throws Throwable{
        int[] calls={0};Context context=new Context();InvocationHandler bridge=(owner,method,args)->{yes(args[0].equals("state"),"native code uses only the public JDK bridge ABI");calls[0]++;return "{}";};
        Method entry=CustomPluginNativeSettings.resolve(CustomPluginContractCheck.class.getClassLoader(),NativeEntry.class.getName());View view=CustomPluginNativeSettings.create(entry,context,"{}",bridge);yes(view.getContext()==context&&calls[0]==1,"the actual reflected plugin createView creates an Android View with context and state bridge");
        reject(()->CustomPluginNativeSettings.resolve(CustomPluginContractCheck.class.getClassLoader(),WrongEntry.class.getName()),"native entry with an incompatible return contract");
        Method broken=CustomPluginNativeSettings.resolve(CustomPluginContractCheck.class.getClassLoader(),BrokenEntry.class.getName());try{CustomPluginNativeSettings.create(broken,context,"{}",bridge);throw new AssertionError("Missing entry failure");}catch(IllegalStateException failure){yes(failure==BrokenEntry.ERROR,"native entry failure identity is preserved for host error handling");}
    }
    /** Execute real metadata and migrate through the repository, not a copied form parser. */
    private static void standardPlugins()throws Throwable {
        String[] paths={"plugins/liquid-glass/main.js","plugins/control-center-free-edit/main.js","tools/custom-plugins/templates/standard-runtime/main.js"};
        String[] names={"更好的玻璃","更好的编辑控制中心","配置记录开发示例"};
        int[] counts={453,9,2}; // glass vc116: 媒体取色 (8 media keys -> 2 choices + source); vc116: 媒体取色 -6, vc115: +8 媒体强调色; vc114: +15 主模块功能; vc99: +44 iOS highlight / black line settings; vc100: +3 automatic notification text; vc107: +22 black line
        for(int n=0;n<paths.length;n++) {
            // The public repository ships the plugin standard template, not the author's plugins.
            if(!new File(paths[n]).isFile())continue;
            String source=new String(Files.readAllBytes(new File(paths[n]).toPath()),StandardCharsets.UTF_8);
            JSONObject actual=CustomPluginEngine.evaluate(source,new JSONObject());
            CustomPluginSupport.requireCompatible(actual);CustomPluginSupport.requireExecutable(actual);
            yes(names[n].equals(actual.getString("name")),"real plugin display name");
            yes(CustomPluginSchema.configurationCount(actual)==counts[n],"real plugin declares exactly its supported persistent settings");
            yes(CustomPluginSupport.requires(actual,"ui.management-groups"),"management navigation capability is explicit");
            JSONArray controls=actual.getJSONArray("ui");
            for(int i=0;i<controls.length();i++) {
                JSONObject control=controls.getJSONObject(i);
                if("management".equals(control.optString("section")))yes(!CustomPluginSchema.groupPath(control).isEmpty(),"management controls have a destination");
            }
            JSONObject next=new JSONObject(actual.toString()).put("runtime",new JSONObject().put("source","void 0;"));
            JSONObject before=new JSONObject(next.toString()).put("name","Old display name").put("versionCode",actual.getInt("versionCode")+100);
            // Reclassification is independent from release numbering; simulate a previous import.
            next.put("versionCode",before.getInt("versionCode")+1);
            if(n==0) {
                JSONObject aliases=actual.getJSONObject("configurationAliases");
                yes(aliases.length()==12,"shared shortcuts declare exactly eleven retired independent camera keys, plus vc116's retired media switch");
                yes("media_template_accent_clear".equals(aliases.optString("media_clear_color")),"the retired 媒体控件去除原厂取色 switch keeps older exports importable");
                java.util.Set<String> suffixes=new java.util.HashSet<>(Arrays.asList("glass","custom","travel","band","transition","rim","rim_strength","milk","saturation","mask","native_fx"));
                for(String suffix:suffixes)yes(("torch_"+suffix).equals(aliases.getString("camera_"+suffix)),"retired camera key aliases only its corresponding shared value: "+suffix);
                int sharedControls=0;
                for(int i=0;i<controls.length();i++) {
                    JSONObject control=controls.getJSONObject(i);String key=control.optString("key");
                    yes(!key.startsWith("camera_"),"no duplicate independent camera control remains: "+key);
                    java.util.List<String> path=CustomPluginSchema.groupPath(control);
                    yes(!path.contains("手电筒")&&!path.contains("相机"),"separate native-button settings pages are retired");
                    if(key.startsWith("torch_")) {
                        sharedControls++;
                        yes(path.size()>=2&&path.get(0).equals("锁屏")&&path.get(1).equals("锁屏小组件"),"both shortcuts have one shared settings entry");
                    }
                    if(key.equals("lockcard_custom"))yes(path.equals(Arrays.asList("锁屏","锁屏卡片")),"other lock cards keep a distinct settings page");
                }
                yes(sharedControls==15,"the shared shortcut page exposes one material set plus one actual background blur (vc99: +iOS 高光, 黑线; vc107: +黑线粗细)");
                // Rebuild the previously installed independent camera controls, then prove that
                // this real repository upgrade removes only those eleven duplicate keys.
                before.remove("configurationAliases");
                java.util.Set<String> additions=new java.util.HashSet<>();
                for(String prefix:new String[]{"folder","dock","recent","widget","drawer_surface","cc","nc","clear","lock","lockwidget","lockcard","torch","lockpin","lock_notice"})additions.add(prefix+"_blur");
                additions.addAll(Arrays.asList("lock_notice_color_custom","lock_notice_milk","lock_notice_saturation","lock_notice_mask"));
                // vc99 iOS highlight / black line settings did not exist in the previous form either.
                for(int i=0;i<before.getJSONArray("ui").length();i++){String key=before.getJSONArray("ui").getJSONObject(i).optString("key");if(key.matches(".*_(ios|line|line_width)")||key.matches("line_(angle|range|soft)")||key.matches("float_text_(mode|dark_color|light_color)")||key.matches("float_(black|white)_(color|strength)")||key.startsWith("host_keep_")||key.startsWith("media_accent")||key.equals("media_cc_light")||key.equals("media_float_light"))additions.add(key);}
                JSONArray previousControls=new JSONArray();
                for(int i=0;i<before.getJSONArray("ui").length();i++){
                    JSONObject c=before.getJSONArray("ui").getJSONObject(i);if(additions.contains(c.optString("key")))continue;
                    if(c.has("values"))for(String key:additions)c.getJSONObject("values").remove(key);
                    previousControls.put(c);
                }
                // vc116 replaced the old control-centre switch with a choice; the previous form still had it.
                previousControls.put(new JSONObject().put("type","switch").put("key","media_clear_color").put("title","媒体控件去除原厂取色").put("default",false).put("section","features"));
                before.put("ui",previousControls);JSONArray oldControls=before.getJSONArray("ui");int oldCount=oldControls.length();
                for(int i=0;i<oldCount;i++) {
                    JSONObject control=oldControls.getJSONObject(i);String key=control.optString("key");
                    if(!key.startsWith("torch_"))continue;
                    JSONObject camera=new JSONObject(control.toString()).put("key","camera_"+key.substring(6));
                    if(camera.has("enableOnEdit")) {
                        JSONArray gates=camera.getJSONArray("enableOnEdit");
                        for(int g=0;g<gates.length();g++)gates.put(g,gates.getString(g).replace("torch_","camera_"));
                    }
                    oldControls.put(camera);
                }
                yes(CustomPluginSchema.configurationCount(before)==356,"legacy fixture restores exactly the previous independent shortcut form");
            }
            for(int i=0;i<before.getJSONArray("ui").length();i++) {
                JSONObject control=before.getJSONArray("ui").getJSONObject(i);control.remove("groupPath");control.put("group","旧分类");
            }
            CustomPluginRepository repository=new CustomPluginRepository(Files.createTempDirectory("c17-standard-upgrade-").toFile());
            CustomPluginRepository.Plugin old=repository.install(repository.preview(new ByteArrayInputStream(bytes("C17.define("+before+");")),"main.js"),false);
            if(n==0){repository.updateState(old.id,"d_blur",73);repository.updateState(old.id,"float_handle_color","#80446688");repository.updateState(old.id,"torch_milk",37);repository.updateState(old.id,"camera_milk",82);}
            if(n==1){repository.updateState(old.id,"qs_card_wallpaper_opacity",37);repository.updateState(old.id,"qs_card_more_order","[\"wifi\",\"bt\"]");repository.updateState(old.id,"qs_card_wallpaper_click_app_package","com.example.player");}
            if(n==2)repository.updateState(old.id,"note","保留的自定义备注");
            JSONObject saved=new JSONObject(repository.list().get(0).state.toString());
            CustomPluginRepository.Plugin updated=repository.install(repository.preview(new ByteArrayInputStream(bytes("C17.define("+next+");")),"main.js"),true);
            yes(old.id.equals(updated.id),"renaming preserves installation identity");yes(saved.length()+(n==0?97:0)==updated.state.length(),"(vc116: 媒体取色 replaces the removal switch and the eight accent keys with two choices) only eleven duplicate shortcut settings retire while eighteen (and vc99's 44 iOS, vc107's 22 black line, vc114's 15 主模块功能) supported controls are added");
            for(String key:saved.keySet()) {
                if(n==0&&(key.startsWith("camera_")||key.equals("media_clear_color")))yes(!updated.state.has(key),"retired value removed (camera duplicates; vc116 媒体取色 switch): "+key);
                else yes(saved.get(key).toString().equals(updated.state.get(key).toString()),"saved value retained: "+key);
            }
            if(n==0)yes(updated.state.length()==453&&updated.state.getInt("torch_milk")==37,"current form has exactly 453 values (vc116: 媒体取色 -6, vc115: +8 媒体强调色, vc114: +15 主模块功能, vc99: +44 iOS, vc100: +3 text, vc105: +4 black/white, vc107: +22 black line) and preserves the original shared left-button material");
            yes(!updated.enabled,"API 2 upgrade retains host review semantics");
        }
    }
    public static void main(String[] args)throws Throwable{admission();packageChain();scopes();groupedUpgradeKeepsState();nativeEntry();standardPlugins();System.out.println("CustomPluginContractCheck "+checks+" checks passed");}
}
