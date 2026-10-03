package dev.puitheme;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Real JSON and ZIP parsing; no mocked successful RAR or Android bitmap decoding. */
public final class IconPackRepositoryCheck {
    private static int checks;
    public static void main(String[] args)throws Exception{
        manifestContract();layersContract();archiveContract();downloadContract();pathContract();selectionContract();assignmentContract();puiThemePolicy();
        System.out.println("IconPackRepositoryCheck passed: "+checks+" checks (JSON/ZIP/path/transport policy; RAR JNI and Bitmap decode require Android)");
    }
    private static void manifestContract()throws Exception{
        JSONObject json=base();JSONObject icons=new JSONObject();icons.put("hint.bluetooth","assets/bluetooth.png");
        icons.put("hint.bluetooth.off","assets/bluetooth-off.webp");icons.put("hint.future_slot","assets/future.png");json.put("icons",icons);
        IconPackManifest manifest=IconPackManifest.parse(bytes(json));
        equal("assets/bluetooth.png",manifest.hint("bluetooth",true));equal("assets/bluetooth-off.webp",manifest.hint("bluetooth",false));
        equal(null,manifest.hint("../bluetooth",true));equal(null,manifest.wifi(4,true));equal(3,manifest.icons.size());
        equal(manifest.icons,IconPackManifest.parse(manifest.bytes()).icons);
        for(String key:new String[]{"hint.bluetooth","hint.camera.on","hint.future_17.off","wifi.none","wifi.4","cellular.0","battery.charging.100"})truth(IconPackManifest.validKey(key));
        for(String key:new String[]{"hint..bluetooth","hint.bluetooth.other","wifi.5","cellular.-1","battery.55","battery.101","class.android.View","../wifi.0"})truth(!IconPackManifest.validKey(key));
        icons=new JSONObject();for(String family:new String[]{"wifi","cellular"}){
            for(int level=0;level<=4;level++)icons.put(family+"."+level,"assets/"+family+level+".png");icons.put(family+".none","assets/"+family+"none.png");}
        for(int level=0;level<=100;level+=10){icons.put("battery."+level,"assets/battery"+level+".png");icons.put("battery.charging."+level,"assets/charging"+level+".png");}
        json.put("icons",icons);manifest=IconPackManifest.parse(bytes(json));
        equal("assets/wifi4.png",manifest.wifi(99,true));equal("assets/wifinone.png",manifest.wifi(4,false));
        equal("assets/cellular0.png",manifest.cellular(-1,true));equal("assets/cellularnone.png",manifest.cellular(4,false));
        equal("assets/battery90.png",manifest.battery(99,false));equal("assets/charging100.png",manifest.battery(200,true));equal("assets/battery0.png",manifest.battery(-1,false));
        JSONObject missing=new JSONObject(json.toString());missing.getJSONObject("icons").remove("wifi.2");reject(()->IconPackManifest.parse(bytes(missing)));
        JSONObject missingBattery=new JSONObject(json.toString());missingBattery.getJSONObject("icons").remove("battery.50");reject(()->IconPackManifest.parse(bytes(missingBattery)));
        JSONObject bad=new JSONObject(json.toString());bad.put("version","1");reject(()->IconPackManifest.parse(bytes(bad)));
        JSONObject badRender=new JSONObject(json.toString());badRender.put("render","executable");reject(()->IconPackManifest.parse(bytes(badRender)));
        JSONObject wrongAsset=new JSONObject(json.toString());wrongAsset.getJSONObject("icons").put("wifi.0","assets/../native.png");reject(()->IconPackManifest.parse(bytes(wrongAsset)));
        reject(()->IconPackManifest.parse(new byte[65537]));
        JSONObject partial=base();partial.put("version",2);partial.put("fallback","native");JSONObject real=new JSONObject();
        for(int i=0;i<=4;i++)real.put("wifi."+i,"assets/wifi"+i+".png");partial.put("icons",real);
        IconPackManifest pui=IconPackManifest.parse(bytes(partial));equal(2,pui.version);truth(pui.nativeFallback);
        equal(null,pui.wifi(4,false));equal("assets/wifi4.png",pui.wifi(4,true));equal(null,pui.battery(50,false));
        IconPackManifest roundTrip=IconPackManifest.parse(pui.bytes());equal(2,roundTrip.version);truth(roundTrip.nativeFallback);equal(pui.icons,roundTrip.icons);
        Map<String,List<IconPackSelection.Asset>> partialPlan=IconPackSelection.plan(Collections.singletonList(new IconPackSelection.Pack(id(7),pui)));
        equal(5,partialPlan.size());truth(!partialPlan.containsKey("wifi.none"));truth(!partialPlan.containsKey("cellular.0"));
        // v2 never fabricates a missing state. A lower enabled library may supply it,
        // otherwise the unchanged native renderer remains the final fallback.
        JSONObject lower=base();lower.put("icons",new JSONObject().put("hint.location","assets/location.png"));
        partialPlan=IconPackSelection.plan(Arrays.asList(new IconPackSelection.Pack(id(7),pui),new IconPackSelection.Pack(id(8),IconPackManifest.parse(bytes(lower)))));
        equal(id(7),partialPlan.get("wifi.4").get(0).id);equal(id(8),partialPlan.get("hint.location.on").get(0).id);truth(!partialPlan.containsKey("wifi.none"));
        real.put("battery.50","assets/normal50.png");partial.put("icons",real);pui=IconPackManifest.parse(bytes(partial));
        equal("assets/normal50.png",pui.battery(50,false));equal(null,pui.battery(50,true));
        truth(!IconPackSelection.plan(Collections.singletonList(new IconPackSelection.Pack(id(7),pui))).containsKey("battery.charging.50"));
        for(Object fallback:new Object[]{"static",true,JSONObject.NULL,1}){JSONObject wrong=new JSONObject(partial.toString());wrong.put("fallback",fallback);reject(()->IconPackManifest.parse(bytes(wrong)));}
        JSONObject omitted=new JSONObject(partial.toString());omitted.remove("fallback");reject(()->IconPackManifest.parse(bytes(omitted)));
        JSONObject legacyFallback=new JSONObject(partial.toString());legacyFallback.put("version",1);reject(()->IconPackManifest.parse(bytes(legacyFallback)));
        for(int version:new int[]{0,3}){JSONObject wrong=new JSONObject(partial.toString());wrong.put("version",version);reject(()->IconPackManifest.parse(bytes(wrong)));}
    }
    private static void layersContract()throws Exception{
        List<IconPackRepository.Layer> layers=new ArrayList<>();
        for(int i=0;i<32;i++)layers.add(new IconPackRepository.Layer(id(i),i<16));
        String encoded=IconPackRepository.layersJson(layers);List<IconPackRepository.Layer> parsed=IconPackRepository.layers(encoded);
        equal(32,parsed.size());equal(id(0),parsed.get(0).id);truth(parsed.get(15).enabled);truth(!parsed.get(16).enabled);
        truth(parsed.get(0).autoMatch);truth(new IconPackRepository.Layer(id(0),true).autoMatch);
        List<IconPackRepository.Layer> legacy=IconPackRepository.layers("[{\"id\":\""+id(0)+"\",\"enabled\":true}]");truth(legacy.get(0).autoMatch);
        List<IconPackRepository.Layer> explicit=IconPackRepository.layers(IconPackRepository.layersJson(Collections.singletonList(new IconPackRepository.Layer(id(0),true,false))));
        truth(explicit.get(0).enabled);truth(!explicit.get(0).autoMatch);
        for(String invalid:new String[]{"1","\"false\"","null"})reject(()->IconPackRepository.layers("[{\"id\":\""+id(0)+"\",\"enabled\":true,\"autoMatch\":"+invalid+"}]"));
        reject(()->IconPackRepository.layers("[{\"id\":\""+id(0)+"\",\"enabled\":true,\"autoMatch\":false,\"path\":\"../\"}]"));
        Collections.reverse(layers);parsed=IconPackRepository.layers(IconPackRepository.layersJson(layers));equal(id(31),parsed.get(0).id);
        List<IconPackRepository.Layer> duplicate=new ArrayList<>(layers);duplicate.set(0,duplicate.get(1));reject(()->IconPackRepository.layersJson(duplicate));
        List<IconPackRepository.Layer> tooManyActive=new ArrayList<>();for(int i=0;i<17;i++)tooManyActive.add(new IconPackRepository.Layer(id(i),true));reject(()->IconPackRepository.layersJson(tooManyActive));
        List<IconPackRepository.Layer> tooMany=new ArrayList<>();for(int i=0;i<33;i++)tooMany.add(new IconPackRepository.Layer(id(i),false));reject(()->IconPackRepository.layersJson(tooMany));
        reject(()->IconPackRepository.layers("[{\"id\":\""+id(0)+"\",\"enabled\":1}]"));
        reject(()->IconPackRepository.layers("[{\"id\":\""+id(0)+"\",\"enabled\":true,\"path\":\"../\"}]"));
        equal(0,IconPackRepository.layers("[]").size());truth(!IconPackRepository.booleanDefaults().get(IconPackRepository.MASTER));
        equal("[]",IconPackRepository.stringDefaults().get(IconPackRepository.LAYERS));
    }
    private static void archiveContract()throws Exception{
        Map<String,byte[]> files=new LinkedHashMap<>();files.put("release-main/manifest.json","{}".getBytes(StandardCharsets.UTF_8));
        files.put("release-main/assets/test.png",new byte[]{1,2,3});files.put("release-main/README.md",new byte[]{4});
        Map<String,byte[]> unpacked=IconPackArchive.zip(new ByteArrayInputStream(zip(files)));equal(2,unpacked.size());truth(Arrays.equals(new byte[]{1,2,3},unpacked.get("assets/test.png")));
        files.put("other/manifest.json",new byte[]{5});byte[] multiple=zip(files);reject(()->IconPackArchive.zip(new ByteArrayInputStream(multiple)));
        files=new LinkedHashMap<>();files.put("../manifest.json",new byte[]{1});byte[] traversal=zip(files);reject(()->IconPackArchive.zip(new ByteArrayInputStream(traversal)));
        files=new LinkedHashMap<>();files.put("manifest.json",new byte[IconPackArchive.MAX_ENTRY+1]);byte[] tooBig=zip(files);reject(()->IconPackArchive.zip(new ByteArrayInputStream(tooBig)));
        IconPackArchive.Collector duplicate=new IconPackArchive.Collector();duplicate.header("assets/a.png",false,1);reject(()->duplicate.header("assets/a.png",false,1));
        IconPackArchive.Collector budget=new IconPackArchive.Collector();for(int i=0;i<16;i++)budget.put("assets/"+i+".png",new byte[IconPackArchive.MAX_ENTRY]);reject(()->budget.put("assets/more.png",new byte[]{1}));
        IconPackArchive.Collector count=new IconPackArchive.Collector();for(int i=0;i<256;i++)count.header("a"+i,false,0);reject(()->count.header("more",false,0));
        reject(()->IconPackArchive.readEntry(new ByteArrayInputStream(new byte[33]),32));
    }
    private static void downloadContract()throws Exception{
        equal("sanwu5/c17-icon-packs",IconPackDownload.repository("sanwu5/c17-icon-packs"));
        equal("sanwu5/c17-icon-packs",IconPackDownload.repository("https://github.com/sanwu5/c17-icon-packs/"));
        equal("sanwu5/c17-icon-packs",IconPackDownload.repository("https://github.com/sanwu5/c17-icon-packs.git"));
        equal(null,IconPackDownload.repository("https://github.com/sanwu5/c17-icon-packs/releases/download/v1/icons.zip"));
        for(String unsafe:new String[]{"http://github.com/a/b.zip","https://evil.github.com/a.zip","https://github.com.evil.com/a.zip","https://name:secret@github.com/a.zip","https://github.com:444/a.zip","https://raw.githubusercontent.com/a.zip#frag","https://127.0.0.1/a.zip"})reject(()->IconPackDownload.checkedUrl(unsafe));
        truth(IconPackDownload.checkedUrl("https://release-assets.githubusercontent.com/a.zip?token=test").getQuery()!=null);
        String release="{\"draft\":false,\"assets\":[{\"name\":\"other.zip\",\"size\":40,\"browser_download_url\":\"https://github.com/a/b/releases/download/v1/other.zip\"},{\"name\":\"icon-pack.zip\",\"size\":40,\"browser_download_url\":\"https://github.com/a/b/releases/download/v1/icon-pack.zip\"}]}";
        equal("https://github.com/a/b/releases/download/v1/icon-pack.zip",IconPackDownload.releaseAsset(release).toString());
        reject(()->IconPackDownload.releaseAsset("{\"assets\":[]}"));reject(()->IconPackDownload.releaseAsset("{\"draft\":true,\"assets\":[]}"));
    }
    private static void pathContract()throws Exception{
        for(String path:new String[]{"../a","a/../b","/a","a\\b","C:/a","a//b","a/./b","a\u0000b"})truth(!IconPackArchive.safePath(path,false));
        truth(IconPackArchive.safePath("repo-main/assets/",true));truth(IconPackManifest.validAsset("assets/icon-001.png"));
        for(String path:new String[]{"assets/../../x.png","assets/a.svg","assets/a.xml","assets/sub/a.png","assets/a.png.exe","assets/%2e.png"})truth(!IconPackManifest.validAsset(path));
        File root=new File(System.getProperty("java.io.tmpdir"),"c17-icon-pack-contract").getCanonicalFile();root.mkdirs();
        equal(new File(root,id(1)),IconPackRepository.packDirectory(root,id(1)));reject(()->IconPackRepository.packDirectory(root,"../../other"));root.delete();
    }
    private static void selectionContract()throws Exception{
        JSONObject upper=base(),lower=base();JSONObject topIcons=new JSONObject(),bottomIcons=new JSONObject();
        topIcons.put("hint.bluetooth","assets/top.png");topIcons.put("hint.future_slot","assets/unused.png");
        bottomIcons.put("hint.bluetooth.off","assets/lower.png");bottomIcons.put("hint.location","assets/location.png");
        upper.put("icons",topIcons);lower.put("icons",bottomIcons);
        List<IconPackSelection.Pack> packs=Arrays.asList(new IconPackSelection.Pack(id(1),IconPackManifest.parse(bytes(upper))),new IconPackSelection.Pack(id(2),IconPackManifest.parse(bytes(lower))));
        Map<String,List<IconPackSelection.Asset>> plan=IconPackSelection.plan(packs);
        truth(!plan.containsKey("hint.future_slot.on"));truth(packs.get(0).manifest.icons.containsKey("hint.future_slot"));
        equal(2,plan.get("hint.bluetooth.off").size());equal(id(1),plan.get("hint.bluetooth.off").get(0).id);
        List<String> decoded=new ArrayList<>();
        Map<String,String> resolved=IconPackSelection.resolve(plan,100,null,(asset,remaining)->{decoded.add(asset.path);return new IconPackSelection.Decoded<>(asset.path,40);});
        equal("assets/top.png",resolved.get("hint.bluetooth.on"));equal("assets/top.png",resolved.get("hint.bluetooth.off"));equal("assets/location.png",resolved.get("hint.location.on"));
        equal(Arrays.asList("assets/top.png","assets/location.png"),decoded); // No decode for completely shadowed lower bluetooth.
        equal(0,Collections.frequency(decoded,"assets/unused.png"));
        decoded.clear();resolved=IconPackSelection.resolve(plan,100,null,(asset,remaining)->{
            decoded.add(asset.path);if(asset.path.equals("assets/top.png"))throw new IOException("Damaged image");return new IconPackSelection.Decoded<>(asset.path,40);
        });
        equal("assets/lower.png",resolved.get("hint.bluetooth.off"));equal(null,resolved.get("hint.bluetooth.on"));equal(1,Collections.frequency(decoded,"assets/top.png"));
        resolved=IconPackSelection.resolve(plan,40,null,(asset,remaining)->new IconPackSelection.Decoded<>(asset.path,40));
        equal(2,resolved.size());equal(null,resolved.get("hint.location.on")); // A single shared top mask fits; another asset cannot exceed the budget.
        decoded.clear();resolved=IconPackSelection.resolve(plan,100,()->true,(asset,remaining)->{decoded.add(asset.path);return new IconPackSelection.Decoded<>(asset.path,1);});
        equal(0,decoded.size());equal(0,resolved.size());
        resolved=IconPackSelection.resolve(plan,10,null,(asset,remaining)->new IconPackSelection.Decoded<>(asset.path,Integer.MAX_VALUE));equal(0,resolved.size());
    }
    private static void assignmentContract()throws Exception{
        equal("[]",IconPackAssignments.stringDefaults().get(IconPackAssignments.ASSIGNMENTS));
        String json=IconPackAssignments.put("[]","hint.bluetooth",id(2),"hint.location");
        json=IconPackAssignments.put(json,"hint.bluetooth.off",id(1),"hint.bluetooth");
        List<IconPackAssignments.Assignment> assignments=IconPackAssignments.rules(json);
        equal(2,assignments.size());equal(json,IconPackAssignments.encode(assignments));
        equal("hint.location",assignments.get(0).sourceRole);equal(id(2),assignments.get(0).packId);
        equal(1,IconPackAssignments.rules(IconPackAssignments.remove(json,"hint.bluetooth.off")).size());
        for(String target:IconPackAssignments.targetRoles())truth(IconPackAssignments.validTarget(target));
        truth(IconPackAssignments.validTarget("wifi.none"));truth(!IconPackAssignments.targetRoles().contains("wifi.none"));
        truth(!IconPackAssignments.unavailableTargetReason("wifi.none").isEmpty());equal("",IconPackAssignments.unavailableTargetReason("wifi.0"));
        truth(!IconPackAssignments.targetRoles().contains("hint.camera.off"));truth(IconPackAssignments.validTarget("hint.camera.off"));
        truth(!IconPackAssignments.unavailableTargetReason("hint.camera.off").isEmpty());
        equal("蓝牙 · 未连接",IconPackAssignments.label("hint.bluetooth.off"));equal("蓝牙 · 已连接",IconPackAssignments.label("hint.bluetooth.on"));
        equal("蓝牙",IconPackAssignments.label("hint.bluetooth"));equal("电池充电 · 100%",IconPackAssignments.label("battery.charging.100"));
        for(String invalid:new String[]{"{}","[1]","[{\"targetRole\":\"hint.bluetooth\",\"packId\":\"../source\",\"sourceRole\":\"hint.location\"}]",
                "[{\"targetRole\":\"hint.unknown\",\"packId\":\""+id(1)+"\",\"sourceRole\":\"hint.location\"}]",
                "[{\"targetRole\":\"hint.bluetooth\",\"packId\":\""+id(1)+"\",\"sourceRole\":\"assets/other.png\"}]",
                "[{\"targetRole\":\"hint.bluetooth\",\"packId\":\""+id(1)+"\",\"sourceRole\":\"hint.location\",\"unknown\":true}]"})truth(IconPackAssignments.validationError(invalid)!=null);
        String duplicate="[{\"targetRole\":\"hint.bluetooth\",\"packId\":\""+id(1)+"\",\"sourceRole\":\"hint.location\"}]";
        final String repeated=duplicate.substring(0,duplicate.length()-1)+","+duplicate.substring(1);reject(()->IconPackAssignments.rules(repeated));
        JSONObject upper=base(),lower=base();upper.put("icons",new JSONObject().put("hint.bluetooth","assets/bluetooth.png"));
        lower.put("icons",new JSONObject().put("hint.location","assets/location.png"));
        List<IconPackSelection.Pack> packs=Arrays.asList(new IconPackSelection.Pack(id(1),IconPackManifest.parse(bytes(upper))),new IconPackSelection.Pack(id(2),IconPackManifest.parse(bytes(lower))));
        Map<String,List<IconPackSelection.Asset>> plan=IconPackSelection.plan(packs,assignments);
        equal("assets/location.png",plan.get("hint.bluetooth.on").get(0).path);
        equal("assets/bluetooth.png",plan.get("hint.bluetooth.off").get(0).path); // Exact state overrides the common choice.
        List<String> reads=new ArrayList<>();Map<String,String> selected=IconPackSelection.resolve(plan,100,null,(asset,remaining)->{reads.add(asset.path);return new IconPackSelection.Decoded<>(asset.path,40);});
        equal("assets/location.png",selected.get("hint.bluetooth.on"));equal("assets/bluetooth.png",selected.get("hint.bluetooth.off"));
        equal(1,Collections.frequency(reads,"assets/location.png"));equal(1,Collections.frequency(reads,"assets/bluetooth.png"));
        // Disabled/deleted source libraries never enter the active pack set and fall back.
        plan=IconPackSelection.plan(Collections.singletonList(packs.get(0)),assignments);equal("assets/bluetooth.png",plan.get("hint.bluetooth.on").get(0).path);
        plan=IconPackSelection.plan(packs,assignments);selected=IconPackSelection.resolve(plan,100,null,(asset,remaining)->{
            if(asset.path.equals("assets/location.png"))throw new IOException("Corrupt explicit artwork");return new IconPackSelection.Decoded<>(asset.path,40);
        });equal("assets/bluetooth.png",selected.get("hint.bluetooth.on"));
        List<IconPackAssignments.Assignment> unavailable=Collections.singletonList(new IconPackAssignments.Assignment("hint.bluetooth",id(9),"hint.location"));
        equal(0,IconPackSelection.plan(Collections.emptyList(),unavailable).size());
        String wifi=IconPackAssignments.put("[]","wifi.4",id(2),"hint.location");plan=IconPackSelection.plan(packs,IconPackAssignments.rules(wifi));
        equal("assets/location.png",plan.get("wifi.4").get(0).path);truth(!plan.containsKey("wifi.3"));
        equal("wifi.4",plan.keySet().iterator().next());
        selected=IconPackSelection.resolve(plan,40,null,(asset,remaining)->new IconPackSelection.Decoded<>(asset.path,40));
        equal("assets/location.png",selected.get("wifi.4"));equal(null,selected.get("hint.bluetooth.on")); // Explicit choice owns a limited cache before automatic assets.
        List<IconPackAssignments.Assignment> many=new ArrayList<>();for(String role:IconPackAssignments.targetRoles())many.add(new IconPackAssignments.Assignment(role,id(1),"hint.bluetooth"));
        equal(many.size(),IconPackAssignments.rules(IconPackAssignments.encode(many)).size());
        // A library enabled only as a source never changes unrelated roles, even if it
        // supplies every family; automatic matching is a separate user choice.
        JSONObject complete=base(),icons=new JSONObject().put("hint.bluetooth","assets/bluetooth.png").put("hint.location","assets/location.png");
        for(String family:new String[]{"wifi","cellular"}){
            icons.put(family+".none","assets/"+family+"none.png");
            for(int level=0;level<=4;level++)icons.put(family+"."+level,"assets/"+family+level+".png");
        }
        for(int level=0;level<=100;level+=10){icons.put("battery."+level,"assets/battery"+level+".png");icons.put("battery.charging."+level,"assets/charging"+level+".png");}
        complete.put("icons",icons);IconPackManifest source=IconPackManifest.parse(bytes(complete));
        List<IconPackSelection.Pack> sourceOnly=Collections.singletonList(new IconPackSelection.Pack(id(2),source,false));
        List<IconPackAssignments.Assignment> bluetooth=IconPackAssignments.rules(IconPackAssignments.put("[]","hint.bluetooth",id(2),"hint.location"));
        equal(0,IconPackSelection.plan(sourceOnly).size());equal(0,IconPackSelection.plan(sourceOnly,Collections.emptyList()).size());
        plan=IconPackSelection.plan(sourceOnly,bluetooth);equal(2,plan.size());
        for(String role:plan.keySet())truth(role.equals("hint.bluetooth.on")||role.equals("hint.bluetooth.off"));
        equal("assets/location.png",plan.get("hint.bluetooth.on").get(0).path);equal("assets/location.png",plan.get("hint.bluetooth.off").get(0).path);
        selected=IconPackSelection.resolve(plan,40,null,(asset,remaining)->new IconPackSelection.Decoded<>(asset.path,40));equal(2,selected.size());
        equal(0,IconPackSelection.plan(Collections.emptyList(),bluetooth).size());
        plan=IconPackSelection.plan(Collections.singletonList(new IconPackSelection.Pack(id(2),source,true)),bluetooth);
        truth(plan.containsKey("wifi.4"));truth(plan.containsKey("cellular.0"));truth(plan.containsKey("battery.charging.100"));
    }
    private static JSONObject base()throws Exception{JSONObject json=new JSONObject();json.put("format",IconPackManifest.FORMAT);json.put("version",1);json.put("name","示例库");json.put("author","Test");json.put("license","CC0-1.0");return json;}
    private static void puiThemePolicy()throws Exception{
        equal(11,PuiThemeIconPack.roles(PuiThemeIconPack.SIGNAL).size());equal(9,PuiThemeIconPack.roles(PuiThemeIconPack.STATUS).size());
        truth(!PuiThemeIconPack.roles(PuiThemeIconPack.SIGNAL).containsKey("wifi.none"));truth(!PuiThemeIconPack.roles(PuiThemeIconPack.SIGNAL).containsKey("battery.100"));
        equal("stat_signal_signal_null_lte",PuiThemeIconPack.roles(PuiThemeIconPack.SIGNAL).get("cellular.none"));
        equal("stat_sys_data_bluetooth_connected",PuiThemeIconPack.roles(PuiThemeIconPack.STATUS).get("hint.bluetooth.on"));
        equal(PuiThemeIconPack.SIGNAL,PuiThemeIconPack.selectedApk("system/product/overlay/PuiThemeSignalIcon.apk"));
        for(String path:new String[]{"../system/product/overlay/PuiThemeSignalIcon.apk","system/product/overlay/Other.apk","system/product/overlay/PuiThemeSignalIcon.apk/","service.sh","system/product/overlay/PuiThemeBatteryHorizontal.apk"})equal(null,PuiThemeIconPack.selectedApk(path));
        for(String code:new String[]{"classes.dex","classes2.dex","CLASSES9.DEX","lib/arm64-v8a/a.so","assets/a.so"})truth(PuiThemeIconPack.executableEntry(code));
        for(String data:new String[]{"resources.arsc","AndroidManifest.xml","res/drawable/wifi.xml","META-INF/CERT.RSA"})truth(!PuiThemeIconPack.executableEntry(data));
        Map<String,byte[]> files=new LinkedHashMap<>();files.put("resources.arsc",new byte[]{1});files.put("AndroidManifest.xml",new byte[]{2});files.put("res/drawable/wifi.xml",new byte[]{3});
        File source=File.createTempFile("pui-check-",".apk");try{
            java.nio.file.Files.write(source.toPath(),zip(files));PuiThemeIconPack.validateApk(source);checks++;
            for(String unsafe:new String[]{"classes.dex","lib/arm64-v8a/a.so","../escape"}){Map<String,byte[]> bad=new LinkedHashMap<>(files);bad.put(unsafe,new byte[]{4});java.nio.file.Files.write(source.toPath(),zip(bad));reject(()->PuiThemeIconPack.validateApk(source));}
            Map<String,byte[]> missing=new LinkedHashMap<>(files);missing.remove("resources.arsc");java.nio.file.Files.write(source.toPath(),zip(missing));reject(()->PuiThemeIconPack.validateApk(source));
            Map<String,byte[]> missingManifest=new LinkedHashMap<>(files);missingManifest.remove("AndroidManifest.xml");java.nio.file.Files.write(source.toPath(),zip(missingManifest));reject(()->PuiThemeIconPack.validateApk(source));
            Map<String,byte[]> oversized=new LinkedHashMap<>(files);oversized.put("res/drawable/large.xml",new byte[2*1024*1024]);java.nio.file.Files.write(source.toPath(),zip(oversized));reject(()->PuiThemeIconPack.validateApk(source));
        }finally{source.delete();}
    }
    private static byte[] bytes(JSONObject value){return value.toString().getBytes(StandardCharsets.UTF_8);}
    private static String id(int n){return String.format(Locale.ROOT,"%064x",n);}
    private static byte[] zip(Map<String,byte[]> files)throws IOException{ByteArrayOutputStream result=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(result)){for(Map.Entry<String,byte[]> entry:files.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}return result.toByteArray();}
    private interface Action{void run()throws Exception;}
    private static void reject(Action action)throws Exception{try{action.run();throw new AssertionError("Rejected input accepted");}catch(IOException expected){checks++;}}
    private static void truth(boolean value){checks++;if(!value)throw new AssertionError("Expected true");}
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+" got "+actual);}
}
