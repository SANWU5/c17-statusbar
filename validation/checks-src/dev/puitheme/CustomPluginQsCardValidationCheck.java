// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Executes the local validation plugin with the real sandbox engine, admission and repository storage. */
public final class CustomPluginQsCardValidationCheck {
    private static final String ID="dev.c17.validation.qs-card";
    private static final Path FIXTURE=Paths.get("validation/checks-src/dev/puitheme/fixtures/qs-card-validation");
    private static final String[] FILES={"LICENSE","README.md","main.js","plugin.json"};
    private static final String[] MODES={"native","large","rows","resize","idle","wallpaper","rows_large","all"};
    private static final Set<String> ORIGINAL_KEYS=new LinkedHashSet<>(Arrays.asList(QsCardSettings.LARGE_MEDIA,QsCardSettings.GRID_6,
            QsCardSettings.RESIZE_NATIVE,QsCardSettings.HIDE_IDLE,QsCardSettings.WALLPAPER_ENABLED));
    private static int checks;
    private interface Task {void run()throws Exception;}
    private static void equal(Object expected,Object actual,String reason){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("QS validation "+checks+" "+reason+": "+expected+" != "+actual);}
    private static void reject(Task task,String reason)throws Exception {checks++;try{task.run();}catch(Exception expected){return;}throw new AssertionError("Accepted "+reason);}
    private static Set<String> keys(JSONObject value){Set<String> result=new LinkedHashSet<>();java.util.Iterator<String> keys=value.keys();while(keys.hasNext())result.add(keys.next());return result;}
    private static Map<String,Object> map(Bundle bundle){Map<String,Object> result=new LinkedHashMap<>();for(String key:bundle.keySet())result.put(key,bundle.get(key));return result;}
    private static Set<String> enabled(String mode) {
        Set<String> result=new LinkedHashSet<>();
        if(Arrays.asList("large","rows_large","all").contains(mode))result.add(QsCardSettings.LARGE_MEDIA);
        if(Arrays.asList("rows","rows_large","all").contains(mode))result.add(QsCardSettings.GRID_6);
        if(Arrays.asList("resize","all").contains(mode))result.add(QsCardSettings.RESIZE_NATIVE);
        if(Arrays.asList("idle","all").contains(mode))result.add(QsCardSettings.HIDE_IDLE);
        if(Arrays.asList("wallpaper","all").contains(mode))result.add(QsCardSettings.WALLPAPER_ENABLED);
        return result;
    }
    private static void settings(JSONObject values,String mode) {
        equal(ORIGINAL_KEYS,keys(values),"only the five declared code195 opt-in switches");
        for(String key:ORIGINAL_KEYS) {
            equal(true,values.opt(key) instanceof Boolean,"no type coercion for "+key);
            equal(enabled(mode).contains(key),values.getBoolean(key),"mode "+mode+" switch "+key);
        }
        for(String forbidden:new String[]{StatusBarSettings.SAFE_MODE,QsCardSettings.WALLPAPER_REVISION,QsCardSettings.WALLPAPER_OPACITY,
                "spanX","spanY","nativeSpanX","nativeSpanY","room","tile_specs"})equal(false,values.has(forbidden),"no safety/image/storage override");
    }
    private static JSONObject descriptor(String source,String mode)throws Exception {
        JSONObject result=CustomPluginEngine.evaluate(source,mode==null?new JSONObject():new JSONObject().put("mode",mode));
        CustomPluginSchema.validate(result);
        equal(ID,result.getString("id"),"fixture identity");equal("1.0.0",result.getString("version"),"fixture semantic version");
        equal(1,result.getInt("versionCode"),"fixture code");equal(1,result.getInt("apiVersion"),"API1 only");
        equal(1,result.getJSONArray("capabilities").length(),"one declared capability");equal("settings",result.getJSONArray("capabilities").getString(0),"settings is the sole capability");
        equal(0,result.getJSONArray("hooks").length(),"no native bridge rules");settings(result.getJSONObject("settings"),mode==null?"native":mode);return result;
    }
    private static byte[] fixtureZip()throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)) {
            for(String name:FILES){zip.putNextEntry(new ZipEntry(name));zip.write(Files.readAllBytes(FIXTURE.resolve(name)));zip.closeEntry();}
        }
        return output.toByteArray();
    }
    private static byte[] readAll(ZipInputStream input)throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int read;
        while((read=input.read(buffer))!=-1)output.write(buffer,0,read);return output.toByteArray();
    }
    private static void packageContents(byte[] data)throws Exception {
        Set<String> found=new LinkedHashSet<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data),StandardCharsets.UTF_8)) {
            ZipEntry entry;while((entry=zip.getNextEntry())!=null) {
                equal(true,Arrays.asList(FILES).contains(entry.getName()),"only reviewed fixture paths");equal(true,found.add(entry.getName()),"package entry unique");byte[] content=readAll(zip);
                if("plugin.json".equals(entry.getName()))equal(true,new JSONObject(new String(Files.readAllBytes(FIXTURE.resolve(entry.getName())),StandardCharsets.UTF_8)).similar(new JSONObject(new String(content,StandardCharsets.UTF_8))),"tool manifest matches fixture metadata");
                else equal(true,Arrays.equals(Files.readAllBytes(FIXTURE.resolve(entry.getName())),content),"delivered package file matches reviewed fixture");
            }
        }
        equal(new LinkedHashSet<>(Arrays.asList(FILES)),found,"package contains only source, metadata, explanation and license");
    }
    private static void overlay(Bundle saved,JSONObject overrides,Map<String,Object> before)throws Exception {
        Bundle actual=CustomPluginSettings.overlay(saved,overrides);equal(false,actual==saved,"runtime owns a new settings copy");
        equal(saved.keySet(),actual.keySet(),"overlay invents no preference keys");
        for(String key:saved.keySet()) {
            equal(saved.get(key),actual.get(key),"retired host output leaves original baseline "+key);
        }
        equal(before,map(saved),"saved complete settings remain immutable");
    }
    private static JSONObject plan(CustomPluginRepository repo)throws Exception {
        JSONObject plan=repo.compiledPlan();equal(0,plan.getJSONArray("hooks").length(),"stored plan contains no native rules");
        equal(true,plan.getLong("revision")>0,"stored plan has a positive revision");return plan.getJSONObject("settings");
    }
    private static void repository(String source,byte[] archive)throws Exception {
        Path temporary=Files.createTempDirectory("c17-qs-validation-");
        try {
            Path preferences=temporary.resolve("user-main-settings.json");Map<String,Object> primary=new LinkedHashMap<>();
            int index=0;for(String key:ORIGINAL_KEYS)primary.put(key,index++%2==0);
            primary.put(QsCardSettings.WALLPAPER_REVISION,new String(new char[64]).replace('\0','a'));
            primary.put(QsCardSettings.WALLPAPER_OPACITY,37.5f);primary.put(StatusBarSettings.CLOCK_ENABLED,true);primary.put("private_note","keep");
            byte[] original=new JSONObject(primary).toString().getBytes(StandardCharsets.UTF_8);Files.write(preferences,original);
            Map<String,Object> primaryBefore=new LinkedHashMap<>(primary);Bundle saved=SettingsSnapshot.fromPreferences(primary);Map<String,Object> before=map(saved);
            CustomPluginRepository repo=new CustomPluginRepository(temporary.resolve("plugins").toFile());
            CustomPluginRepository.Preview preview=repo.preview(new ByteArrayInputStream(archive),"C17-temporary-qs-card.zip");
            equal(ID,preview.descriptor.getString("id"),"real ZIP importer previews identity");equal(source,preview.source,"ZIP importer evaluates reviewed JavaScript source");equal(false,preview.upgrade,"new plugin is not upgrade");
            equal(0,repo.list().size(),"preview does not install or enable");
            CustomPluginRepository.Plugin installed=repo.install(preview,false,"本地临时验证包（未公开分发）");
            equal(false,installed.enabled,"confirmed installation defaults disabled");equal("native",installed.state.getString("mode"),"declared native default persisted");
            equal(0,plan(repo).length(),"installed but disabled has no overlay");overlay(saved,plan(repo),before);
            repo.updateState(ID,"mode","all");equal(false,repo.list().get(0).enabled,"editing disabled state does not activate");equal(0,plan(repo).length(),"disabled edited state has no runtime effect");
            repo.updateState(ID,"mode","native");repo.setEnabled(ID,true);
            for(String mode:MODES) {
                repo.updateState(ID,"mode",mode);JSONObject overrides=plan(repo);settings(overrides,mode);overlay(saved,overrides,before);
                CustomPluginRepository.Plugin current=repo.list().get(0);equal(true,current.enabled,"valid state change keeps enabled");equal(mode,current.state.getString("mode"),"state value persisted");
                equal(true,Arrays.equals(original,Files.readAllBytes(preferences)),"repository never modifies saved main settings bytes");
                equal(primaryBefore,primary,"repository and overlay never mutate source preferences");
                Bundle safe=new Bundle(saved);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);
                equal(map(safe),map(CustomPluginSettings.overlay(safe,overrides)),"safe mode retains every saved value without plugin overrides");
            }
            repo.updateState(ID,"mode","wallpaper");JSONObject wallpaper=plan(repo);
            Bundle noImage=new Bundle(saved);noImage.putString(QsCardSettings.WALLPAPER_REVISION,"");Bundle noImageResult=CustomPluginSettings.overlay(noImage,wallpaper);
            equal("",noImageResult.getString(QsCardSettings.WALLPAPER_REVISION),"wallpaper mode does not invent a missing image");equal(37.5f,noImageResult.get(QsCardSettings.WALLPAPER_OPACITY),"wallpaper mode preserves saved opacity");
            equal(0,repo.list().get(0).descriptor.getJSONArray("hooks").length(),"mode changes never request systemui");
            String stable=repo.list().get(0).state.toString();reject(()->repo.updateState(ID,"mode","unsupported"),"unlisted select mode");
            reject(()->repo.updateState(ID,"mode",true),"wrong mode type");reject(()->repo.updateState(ID,"safe_mode",false),"undeclared safety state");
            equal(stable,repo.list().get(0).state.toString(),"invalid user input does not mutate valid state");equal(true,repo.list().get(0).enabled,"invalid form input does not enable a different behavior");
            CustomPluginRepository reopened=new CustomPluginRepository(temporary.resolve("plugins").toFile());settings(plan(reopened),"wallpaper");
            reopened.setEnabled(ID,false);equal(0,plan(reopened).length(),"stopping removes the compiled overlay");overlay(saved,plan(reopened),before);
            reopened.setEnabled(ID,true);settings(plan(reopened),"wallpaper");reopened.delete(ID);
            equal(0,reopened.list().size(),"delete removes the temporary plugin");equal(0,plan(reopened).length(),"deleting an enabled plugin withdraws the overlay");overlay(saved,plan(reopened),before);
            equal(true,Arrays.equals(original,Files.readAllBytes(preferences)),"install/change/disable/delete leave saved main preferences byte-identical");equal(primaryBefore,primary,"saved choices retained at end of experiment");
        } finally {
            try(Stream<Path> paths=Files.walk(temporary)){Path[] entries=paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);for(Path path:entries)Files.deleteIfExists(path);}
        }
    }
    public static void main(String[] args)throws Exception {
        String source=new String(Files.readAllBytes(FIXTURE.resolve("main.js")),StandardCharsets.UTF_8);
        JSONObject initial=descriptor(source,null);JSONArray ui=initial.getJSONArray("ui");JSONObject select=ui.getJSONObject(1);
        equal("select",select.getString("type"),"one native select drives modes");equal("mode",select.getString("key"),"stable state key");equal("native",CustomPluginSchema.defaultValue(select),"native is default");
        equal(8,select.getJSONArray("options").length(),"exactly eight requested modes");
        for(int i=0;i<MODES.length;i++) {
            equal(MODES[i],select.getJSONArray("options").getJSONObject(i).getString("value"),"declared option order");
            CustomPluginSchema.stateValue(select,MODES[i]);equal(true,CustomPluginSchema.sameIdentity(initial,descriptor(source,MODES[i])),"identity and capability cannot change with modes");
        }
        JSONObject manifest=new JSONObject(new String(Files.readAllBytes(FIXTURE.resolve("plugin.json")),StandardCharsets.UTF_8));CustomPluginSchema.manifest(manifest);
        equal("main.js",manifest.getString("entry"),"standard entry");
        byte[] archive=args.length==0?fixtureZip():Files.readAllBytes(Paths.get(args[0]));packageContents(archive);repository(source,archive);
        System.out.println("CustomPluginQsCardValidationCheck passed: "+checks+" (legacy engine/ZIP/library remain readable; retired host overlays are inert)");
    }
}
