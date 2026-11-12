package dev.puitheme;

import android.os.Bundle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;

/** Real full configuration package, every old mode upgrade, transactional reset and immutable overlays. */
public final class CustomPluginQsCardControlsCheck {
    private static final Path FIXTURE=Paths.get("validation/checks-src/dev/puitheme/fixtures/qs-card-controls");
    private static final Path OLD=Paths.get("validation/checks-src/dev/puitheme/fixtures/qs-card-validation");
    private static final Path PREVIOUS=Paths.get("validation/checks-src/dev/puitheme/fixtures/qs-card-controls-1.1.0");
    private static final Path PREVIOUS_PATCH=Paths.get("validation/checks-src/dev/puitheme/fixtures/qs-card-controls-1.1.1");
    private static final Path PREVIOUS_NAV=Paths.get("validation/checks-src/dev/puitheme/fixtures/qs-card-controls-1.2.0");
    private static final String ID="dev.c17.validation.qs-card";
    private static final String[] FLAGS={QsCardSettings.LARGE_MEDIA,QsCardSettings.RESIZE_NATIVE,QsCardSettings.SMALL_TILES,QsCardSettings.HIDE_IDLE,QsCardSettings.WALLPAPER_ENABLED};
    private static final String[] FILES={"LICENSE","README.md","main.js","plugin.json"};
    private static int checks;
    private interface Task {void run()throws Exception;}
    private static void equal(Object expected,Object actual,String label){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Full QS "+checks+" "+label+": "+expected+" != "+actual);}
    private static void reject(Task action,String label)throws Exception {checks++;try{action.run();}catch(Exception expected){return;}throw new AssertionError("Accepted "+label);}
    private static byte[] zip(Path directory)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream archive=new ZipOutputStream(bytes,StandardCharsets.UTF_8)) {
            for(String name:FILES){archive.putNextEntry(new ZipEntry(name));archive.write(Files.readAllBytes(directory.resolve(name)));archive.closeEntry();}
        }
        return bytes.toByteArray();
    }
    private static byte[] read(ZipInputStream input)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
        while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);return bytes.toByteArray();
    }
    private static void packageContents(byte[] archive)throws Exception {
        Set<String> found=new LinkedHashSet<>();
        try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(archive),StandardCharsets.UTF_8)) {
            ZipEntry entry;while((entry=input.getNextEntry())!=null) {
                equal(true,Arrays.asList(FILES).contains(entry.getName()),"only reviewed source and license");equal(true,found.add(entry.getName()),"unique package entry");
                byte[] actual=read(input),expected=Files.readAllBytes(FIXTURE.resolve(entry.getName()));
                if("plugin.json".equals(entry.getName()))equal(true,new JSONObject(new String(expected,StandardCharsets.UTF_8)).similar(new JSONObject(new String(actual,StandardCharsets.UTF_8))),"canonical manifest equals source");
                else equal(true,Arrays.equals(expected,actual),"delivered file equals reviewed source");
            }
        }
        equal(new LinkedHashSet<>(Arrays.asList(FILES)),found,"exact package contents");
    }
    private static void defaults(JSONObject state)throws Exception {
        equal(8,state.length(),"eight independent saved controls");equal("4",state.getString(QsCardSettings.GRID_ROWS),"row choice uses declared string state");
        equal("more",state.getString(QsCardSettings.SMALL_MODE),"default is independent more-tiles entry");equal(100d,state.getDouble(QsCardSettings.WALLPAPER_OPACITY),"default opacity");
        for(String flag:FLAGS)equal(false,state.getBoolean(flag),"independent opt-in "+flag);
        equal(false,state.has("mode"),"old preset is removed");equal(false,state.has(QsCardSettings.GRID_6),"old row switch is not exposed");
    }
    private static void descriptor(String source)throws Exception {
        JSONObject descriptor=CustomPluginEngine.evaluate(source,new JSONObject());CustomPluginSchema.requireConfiguration(descriptor);
        equal(ID,descriptor.getString("id"),"upgrade identity");equal("控制中心自由编辑",descriptor.getString("name"),"requested name");
        equal(5,descriptor.getInt("versionCode"),"strictly greater code");equal("1.2.1",descriptor.getString("version"),"package version");
        equal(8,CustomPluginSchema.configurationCount(descriptor),"eight genuine editable controls");equal(9,descriptor.getJSONArray("ui").length(),"one explanation plus complete form");
        equal(1,descriptor.getJSONArray("capabilities").length(),"only settings capability");equal("settings",descriptor.getJSONArray("capabilities").getString(0),"no arbitrary hook bridge");
        equal(0,descriptor.getJSONArray("hooks").length(),"no native hook declarations");
        equal(8,descriptor.getJSONObject("settings").length(),"all adjustable outputs represented");
        equal("控制中心自由编辑 · 需要 C17 2.0.6",descriptor.getJSONArray("ui").getJSONObject(0).getString("title"),"minimum fixed host shown to user");
        JSONObject music=descriptor.getJSONArray("ui").getJSONObject(2);
        equal(QsCardSettings.LARGE_MEDIA,music.getString("key"),"music adjustment retains saved key");
        equal("音乐卡片拖拽调整",music.getString("title"),"music control describes manual editing");
        equal(true,music.getString("description").contains("不自动改变当前尺寸"),"enabling does not promise automatic enlargement");
        for(String forbidden:new String[]{"mode",QsCardSettings.GRID_6,QsCardSettings.WALLPAPER_REVISION,StatusBarSettings.SAFE_MODE,"spanX","spanY","room","tile_specs"})
            equal(false,descriptor.getJSONObject("settings").has(forbidden),"does not write protected or obsolete state");
        for(int mask=0;mask<32;mask++)for(int rows=4;rows<=7;rows++)for(String mode:new String[]{"always","hidden","more"}) {
            JSONObject state=new JSONObject().put(QsCardSettings.GRID_ROWS,Integer.toString(rows)).put(QsCardSettings.SMALL_MODE,mode).put(QsCardSettings.WALLPAPER_OPACITY,37.5);
            for(int index=0;index<FLAGS.length;index++)state.put(FLAGS[index],(mask&(1<<index))!=0);
            JSONObject output=CustomPluginEngine.evaluate(source,state).getJSONObject("settings");
            equal((double)rows,output.getDouble(QsCardSettings.GRID_ROWS),"select string converts to exact numeric rows");equal(mode,output.getString(QsCardSettings.SMALL_MODE),"visibility independent");
            equal(37.5d,output.getDouble(QsCardSettings.WALLPAPER_OPACITY),"opacity independent");
            for(int index=0;index<FLAGS.length;index++)equal((mask&(1<<index))!=0,output.getBoolean(FLAGS[index]),"independent flag combination");
        }
        equal("more",CustomPluginEngine.evaluate(source,new JSONObject().put(QsCardSettings.SMALL_MODE,"swipe")).getJSONObject("settings").getString(QsCardSettings.SMALL_MODE),"legacy swipe output normalizes to entry navigation");
    }
    private static Map<String,Object> map(Bundle settings){Map<String,Object> values=new LinkedHashMap<>();for(String key:settings.keySet())values.put(key,settings.get(key));return values;}
    private static void overlay(Bundle saved,JSONObject settings,Map<String,Object> before)throws Exception {
        Bundle actual=CustomPluginSettings.overlay(saved,settings);equal(false,saved==actual,"overlay owns runtime copy");equal(saved.keySet(),actual.keySet(),"overlay adds no private state");
        for(String key:saved.keySet()) {
            equal(saved.get(key),actual.get(key),"readable legacy output leaves retired host values untouched "+key);
        }
        equal(before,map(saved),"saved full settings immutable");
    }
    private static void repository(byte[] archive)throws Exception {
        Path directory=Files.createTempDirectory("c17-qs-controls-");CustomPluginRepository repo=new CustomPluginRepository(directory.resolve("plugins").toFile());
        Map<String,Object> original=new LinkedHashMap<>();original.put(QsCardSettings.GRID_ROWS,7f);original.put(QsCardSettings.SMALL_MODE,"hidden");original.put(QsCardSettings.WALLPAPER_OPACITY,22.5f);
        original.put(QsCardSettings.WALLPAPER_REVISION,"a".repeat(64));original.put("private_note","keep");for(int index=0;index<FLAGS.length;index++)original.put(FLAGS[index],index%2==0);
        Path source=directory.resolve("user-main-settings.json");byte[] sourceBytes=new JSONObject(original).toString().getBytes(StandardCharsets.UTF_8);Files.write(source,sourceBytes);
        Bundle saved=SettingsSnapshot.fromPreferences(original);Map<String,Object> before=map(saved);
        CustomPluginRepository.Preview preview=repo.preview(new ByteArrayInputStream(archive),"control-center.zip");equal(0,repo.list().size(),"preview does not install");
        CustomPluginRepository.Plugin plugin=repo.install(preview,false,"本地完整配置审阅包");defaults(plugin.state);equal(false,plugin.enabled,"new import disabled");
        repo.setEnabled(ID,true);JSONObject defaultPlan=repo.compiledPlan().getJSONObject("settings");
        equal("more",defaultPlan.getString(QsCardSettings.SMALL_MODE),"entry mode can be enabled independently");
        equal(false,defaultPlan.getBoolean(QsCardSettings.SMALL_TILES),"entry mode does not enable promoted small tiles");
        repo.setEnabled(ID,false);
        for(String flag:FLAGS)repo.updateState(ID,flag,true);repo.updateState(ID,QsCardSettings.GRID_ROWS,"7");repo.updateState(ID,QsCardSettings.SMALL_MODE,"more");repo.updateState(ID,QsCardSettings.WALLPAPER_OPACITY,37.5);
        equal(0,repo.compiledPlan().getJSONObject("settings").length(),"editing disabled form does not activate it");repo.setEnabled(ID,true);
        plugin=new CustomPluginRepository(directory.resolve("plugins").toFile()).list().get(0);equal("7",plugin.state.getString(QsCardSettings.GRID_ROWS),"saved rows reopen");
        equal("more",plugin.state.getString(QsCardSettings.SMALL_MODE),"saved mode reopens");equal(37.5d,plugin.state.getDouble(QsCardSettings.WALLPAPER_OPACITY),"saved opacity reopens");
        for(String flag:FLAGS)equal(true,plugin.state.getBoolean(flag),"saved switch reopens");
        overlay(saved,repo.compiledPlan().getJSONObject("settings"),before);
        Bundle safe=new Bundle(saved);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);equal(map(safe),map(CustomPluginSettings.overlay(safe,repo.compiledPlan().getJSONObject("settings"))),"safe mode suppresses every output");
        String stable=plugin.state.toString();reject(()->repo.updateState(ID,QsCardSettings.GRID_ROWS,"8"),"invalid row choice");reject(()->repo.updateState(ID,QsCardSettings.SMALL_MODE,"native"),"invalid bottom mode");reject(()->repo.updateState(ID,QsCardSettings.WALLPAPER_OPACITY,101),"opacity range");
        equal(stable,repo.list().get(0).state.toString(),"invalid input commits no partial state");
        repo.resetState(ID);defaults(repo.list().get(0).state);equal(true,repo.list().get(0).enabled,"default reset preserves deliberate enable");overlay(saved,repo.compiledPlan().getJSONObject("settings"),before);
        repo.setEnabled(ID,false);overlay(saved,repo.compiledPlan().getJSONObject("settings"),before);repo.setEnabled(ID,true);repo.delete(ID);overlay(saved,repo.compiledPlan().getJSONObject("settings"),before);
        equal(true,Arrays.equals(sourceBytes,Files.readAllBytes(source)),"all operations leave saved main preference bytes intact");
        equal(0,repo.list().size(),"delete removes complete plugin");
    }
    private static void upgrades(byte[] archive)throws Exception {
        byte[] old=zip(OLD);
        for(String mode:new String[]{"native","large","rows","resize","idle","wallpaper","rows_large","all"}) {
            Path directory=Files.createTempDirectory("c17-qs-controls-upgrade-");CustomPluginRepository repo=new CustomPluginRepository(directory.toFile());
            repo.install(repo.preview(new ByteArrayInputStream(old),"old.zip"),false);repo.updateState(ID,"mode",mode);repo.setEnabled(ID,true);
            CustomPluginRepository.Preview preview=repo.preview(new ByteArrayInputStream(archive),"complete.zip");equal(true,preview.upgrade,"old preset accepts real upgrade");equal("1.0.0",preview.previousVersion,"review shows old version");
            reject(()->repo.install(preview,false),"unconfirmed upgrade");equal(mode,repo.list().get(0).state.getString("mode"),"unconfirmed keeps original preset");
            CustomPluginRepository.Plugin plugin=repo.install(preview,true);defaults(plugin.state);equal(false,plugin.enabled,"old mode upgrade never auto-enables independent settings");
            equal(0,repo.compiledPlan().getJSONObject("settings").length(),"upgrade withdraws old preset effects");equal("控制中心自由编辑",plugin.name,"full configuration replaces temporary name");
            try(CustomPluginRepository.Preview older=repo.preview(new ByteArrayInputStream(old),"old.zip")){equal(true,older.upgrade,"older version replacement can be reviewed");}
        }
    }
    private static void previousFullUpgrade(byte[] archive)throws Exception {
        int[] masks={0,31,21,10};double[] opacity={0d,37.5d,100d,12.5d};
        for(Path previousFixture:new Path[]{PREVIOUS,PREVIOUS_PATCH,PREVIOUS_NAV})for(int profile=0;profile<masks.length;profile++)for(String mode:
                previousFixture.equals(PREVIOUS_NAV)?new String[]{"always","hidden","more"}:new String[]{"always","hidden","swipe"}) {
            byte[] previous=zip(previousFixture);boolean patch=previousFixture.equals(PREVIOUS_PATCH),navigation=previousFixture.equals(PREVIOUS_NAV);
            String version=navigation?"1.2.0":patch?"1.1.1":"1.1.0";
            Path directory=Files.createTempDirectory("c17-qs-controls-full-upgrade-");CustomPluginRepository repo=new CustomPluginRepository(directory.toFile());
            CustomPluginRepository.Plugin old=repo.install(repo.preview(new ByteArrayInputStream(previous),version+".zip"),false);
            equal(version,old.version,"frozen real previous full version");equal(navigation?4:patch?3:2,old.versionCode,"previous full code");
            for(int flag=0;flag<FLAGS.length;flag++)repo.updateState(ID,FLAGS[flag],(masks[profile]&(1<<flag))!=0);
            repo.updateState(ID,QsCardSettings.GRID_ROWS,Integer.toString(profile+4));repo.updateState(ID,QsCardSettings.SMALL_MODE,mode);
            repo.updateState(ID,QsCardSettings.WALLPAPER_OPACITY,opacity[profile]);
            if(profile%2==0)repo.setEnabled(ID,true);
            JSONObject oldState=new JSONObject(repo.list().get(0).state.toString());byte[] before=Files.readAllBytes(directory.resolve("library.json"));
            CustomPluginRepository.Preview reviewed=repo.preview(new ByteArrayInputStream(archive),"1.2.1.zip");
            equal(true,reviewed.upgrade,"full configuration upgrade advertised");equal(version,reviewed.previousVersion,"preview identifies existing full version");
            reject(()->repo.install(reviewed,false),"unconfirmed full upgrade");equal(true,Arrays.equals(before,Files.readAllBytes(directory.resolve("library.json"))),"unconfirmed full upgrade leaves exact old record");
            CustomPluginRepository.Plugin updated=repo.install(reviewed,true);
            JSONObject expected=new JSONObject(oldState.toString());expected.put(QsCardSettings.SMALL_MODE,QsCardSettings.normalizeSmallMode(mode));
            equal(true,expected.similar(updated.state),navigation?"all eight navigation-release values preserved":"seven values preserved and legacy display mode migrated");equal(8,updated.state.length(),"no hidden migration state added");
            for(String flag:FLAGS)equal(oldState.getBoolean(flag),updated.state.getBoolean(flag),"previous full flag preserved");
            equal(oldState.getString(QsCardSettings.GRID_ROWS),updated.state.getString(QsCardSettings.GRID_ROWS),"previous full rows preserved");
            equal(QsCardSettings.normalizeSmallMode(mode),updated.state.getString(QsCardSettings.SMALL_MODE),"previous full display mode normalized without affecting other fields");
            equal(opacity[profile],updated.state.getDouble(QsCardSettings.WALLPAPER_OPACITY),"previous full opacity preserved");
            equal(false,updated.enabled,"full upgrade never auto-enables regardless previous state");equal(0,repo.compiledPlan().getJSONObject("settings").length(),"previous active plan withdrawn by upgrade");
            try(CustomPluginRepository.Preview older=repo.preview(new ByteArrayInputStream(previous),"old-full.zip")){equal(true,older.upgrade,"older full version replacement can be reviewed");}
        }
    }
    public static void main(String[] arguments)throws Exception {
        for(String retired:new String[]{"QsCardProjection","QsCardProjectionModel","QsCardProjectionRuntime","QsCardEditor",
                "QsCardEditorBridge","QsCardOemProfile","QsSmallTilesVisibility","QsMoreTileHolder","QsMediaCardContent"}) {
            try {Class.forName("dev.puitheme."+retired,false,CustomPluginQsCardControlsCheck.class.getClassLoader());throw new AssertionError("Retired host implementation is still bundled: "+retired);}
            catch(ClassNotFoundException expected){checks++;}
        }
        byte[] archive=arguments.length==0?zip(FIXTURE):Files.readAllBytes(Paths.get(arguments[0]));packageContents(archive);
        descriptor(new String(Files.readAllBytes(FIXTURE.resolve("main.js")),StandardCharsets.UTF_8));repository(archive);upgrades(archive);previousFullUpgrade(archive);
        System.out.println("CustomPluginQsCardControlsCheck passed: "+checks+" (legacy ZIP/configuration/upgrade remain readable; retired host overlays are inert)");
    }
}
