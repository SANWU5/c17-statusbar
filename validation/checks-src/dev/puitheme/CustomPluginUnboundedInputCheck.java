package dev.puitheme;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

/** unboundedInput frees typed and preset slider values; drags, defaults and plain sliders keep min..max. */
public final class CustomPluginUnboundedInputCheck {
    private static int checks;
    private static final String[] COMPONENT_BLURS={"folder","dock","recent","widget","drawer_surface","cc","nc","clear","lock","lockwidget","lockcard","torch","lockpin","lock_notice"};
    private static boolean componentAdded(String key) {
        for(String prefix:COMPONENT_BLURS)if(key.equals(prefix+"_blur"))return true;
        return key.equals("lock_notice_color_custom")||key.equals("lock_notice_milk")||key.equals("lock_notice_saturation")||key.equals("lock_notice_mask");
    }
    /** vc99 iOS highlight / black line settings: newer than every previous form these upgrades start from. */
    private static boolean iosAdded(String key) { return key.matches(".*_(ios|line|line_width)")||key.matches("line_(angle|range|soft)")||key.matches("float_text_(mode|dark_color|light_color)")||key.matches("float_(black|white)_(color|strength)")||key.startsWith("host_keep_")||key.startsWith("media_accent")||key.equals("media_cc_light")||key.equals("media_float_light"); }
    private static void removeComponentAdditions(JSONObject descriptor)throws Exception {
        JSONArray all=descriptor.getJSONArray("ui"),old=new JSONArray();
        for(int i=0;i<all.length();i++){
            JSONObject c=all.getJSONObject(i);if(componentAdded(c.optString("key"))||iosAdded(c.optString("key")))continue;
            JSONObject preset=c.optJSONObject("values");
            if(preset!=null)for(String key:new java.util.HashSet<>(preset.keySet()))if(componentAdded(key)||iosAdded(key))preset.remove(key);
            old.put(c);
        }
        // vc116 replaced this earlier control-centre switch with the 媒体取色 choice; the earlier form had it.
        old.put(new JSONObject().put("type","switch").put("key","media_clear_color").put("title","媒体控件去除原厂取色").put("default",false).put("section","features"));
        JSONObject aliases=descriptor.optJSONObject("configurationAliases");if(aliases!=null)aliases.remove("media_clear_color");
        descriptor.put("ui",old);
    }
    private interface Task { void run() throws Exception; }
    private static void equal(Object expected,Object actual,String label) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError(label+": "+expected+" != "+actual);
    }
    private static void reject(Task task,String label)throws Exception {
        checks++;try{task.run();}catch(Exception expected){return;}throw new AssertionError("Accepted "+label);
    }
    private static String script(JSONArray controls,int version) {
        return "C17.define({id:'dev.c17.unbounded',name:'不限范围检查',version:'1.0',versionCode:"+version+",apiVersion:1,"
                +"capabilities:['settings'],ui:"+controls+",settings:{clock_enabled:true},hooks:[]});";
    }
    private static CustomPluginRepository.Plugin install(CustomPluginRepository repo,String source,boolean upgrade)throws Exception {
        return repo.install(repo.preview(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),"unbounded.js"),upgrade);
    }

    public static void main(String[] args)throws Exception {
        controlValues();
        declarations();
        repository();
        liquidGlassForm();
        System.out.println("CustomPluginUnboundedInputCheck: "+checks+" checks passed");
    }

    private static void controlValues()throws Exception {
        JSONObject free=new JSONObject("{type:'slider',key:'amount',title:'数值',min:0,max:100,step:1,unit:'%',unboundedInput:true}");
        JSONObject plain=new JSONObject("{type:'slider',key:'amount',title:'数值',min:0,max:100,step:1,unit:'%'}");
        equal(true,CustomPluginSchema.unboundedInput(free),"declared slider is unbounded");
        equal(false,CustomPluginSchema.unboundedInput(plain),"plain slider stays bounded");
        equal(false,CustomPluginSchema.unboundedInput(new JSONObject("{type:'input',unboundedInput:true}")),"only sliders can be unbounded");
        equal(500d,CustomPluginControlValues.parseSlider(free,"500"),"typed value above max kept");
        equal(-42.5d,CustomPluginControlValues.parseSlider(free,"-42.5"),"typed value below min kept with precision");
        equal(1.0e30d,CustomPluginControlValues.parseSlider(free,"1e30"),"scientific typed value kept");
        for(String bad:new String[]{"NaN","Infinity","1e999999999","","x",null})
            reject(()->CustomPluginControlValues.parseSlider(free,bad),"non-finite or malformed unbounded value "+bad);
        reject(()->CustomPluginControlValues.parseSlider(plain,"101"),"plain slider still rejects typed overflow");
        reject(()->CustomPluginControlValues.parseSlider(plain,"-1"),"plain slider still rejects typed underflow");
        equal(100d,CustomPluginControlValues.snapSlider(free,500),"dragging stays inside the drag range");
        equal(0d,CustomPluginControlValues.snapSlider(free,-500),"dragging lower edge clamped");
        equal("500",CustomPluginControlValues.formatSlider(free,500d),"out-of-range saved value can be shown and edited");
        reject(()->CustomPluginControlValues.formatSlider(plain,500d),"plain slider never displays an invalid saved value");
        equal("滑块范围 0–100%，手动输入不限范围",CustomPluginControlValues.sliderRange(free),"editor explains unbounded typing");
        equal("0–100%",CustomPluginControlValues.sliderRange(plain),"plain editor hint unchanged");
        CustomPluginSchema.stateValue(free,1e6);CustomPluginSchema.stateValue(free,-1e6);checks+=2;
        reject(()->CustomPluginSchema.stateValue(free,Double.valueOf(Double.NaN)),"NaN state");
        reject(()->CustomPluginSchema.stateValue(free,Double.valueOf(Double.POSITIVE_INFINITY)),"infinite state");
        reject(()->CustomPluginSchema.stateValue(free,"500"),"string state for numeric slider");
        reject(()->CustomPluginSchema.stateValue(plain,101),"plain slider state overflow");
    }

    private static void declarations()throws Exception {
        String good="[{type:'slider',key:'amount',title:'数值',min:0,max:100,default:40,unboundedInput:true}]";
        equal(true,CustomPluginEngine.evaluate(script(new JSONArray(good),1),new JSONObject()).getJSONArray("ui").getJSONObject(0).getBoolean("unboundedInput"),"declaration survives engine");
        for(String bad:new String[]{
                "[{type:'slider',key:'amount',title:'数值',min:0,max:100,default:150,unboundedInput:true}]",
                "[{type:'slider',key:'amount',title:'数值',min:0,max:100,unboundedInput:'yes'}]",
                "[{type:'slider',key:'amount',title:'数值',min:0,max:100,unboundedInput:1}]",
                "[{type:'input',key:'amount',title:'文字',unboundedInput:true}]",
                "[{type:'switch',key:'amount',title:'开关',unboundedInput:false}]",
                "[{type:'slider',key:'amount',title:'数值',min:0,max:200000,unboundedInput:true}]"})
            reject(()->CustomPluginEngine.evaluate(script(new JSONArray(bad),1),new JSONObject()),"invalid declaration "+bad);
    }

    private static void repository()throws Exception {
        File directory=Files.createTempDirectory("c17-unbounded-").toFile();
        CustomPluginRepository repo=new CustomPluginRepository(directory);
        String free="[{type:'slider',key:'amount',title:'数值',min:0,max:100,default:40,unboundedInput:true},"
                +"{type:'slider',key:'plain',title:'普通',min:0,max:100,default:10},"
                +"{type:'preset',key:'far',title:'远值',values:{amount:900}}]";
        String id=install(repo,script(new JSONArray(free),1),false).id;
        repo.updateState(id,"amount",250);
        equal(250,repo.list().get(0).state.getInt("amount"),"typed value above slider range saved");
        repo.updateState(id,"amount",-75.5);
        equal(-75.5d,repo.list().get(0).state.getDouble("amount"),"typed value below slider range saved");
        reject(()->repo.updateState(id,"plain",150),"plain slider repository write overflow");
        equal(10,repo.list().get(0).state.getInt("plain"),"rejected plain write leaves state");
        repo.applyPreset(id,1,"far");
        equal(900,repo.list().get(0).state.getInt("amount"),"preset may leave unbounded slider range");
        install(repo,script(new JSONArray(free),2),true);
        equal(900,repo.list().get(0).state.getInt("amount"),"upgrade keeps out-of-range unbounded value");
        String bounded=free.replace(",unboundedInput:true","").replace(",{type:'preset',key:'far',title:'远值',values:{amount:900}}","");
        install(repo,script(new JSONArray(bounded),3),true);
        equal(40,repo.list().get(0).state.getInt("amount"),"dropping unboundedInput resets an out-of-range value to default");
        reject(()->repo.updateState(id,"amount",250),"bounded again after upgrade");
    }

    private static void liquidGlassForm()throws Exception {
        // The author's plugins are not part of the public repository.
        if(!Files.isRegularFile(Paths.get("plugins/liquid-glass/main.js")))return;
        String source=new String(Files.readAllBytes(Paths.get("plugins/liquid-glass/main.js")),StandardCharsets.UTF_8);
        JSONObject descriptor=CustomPluginEngine.evaluate(source,new JSONObject());
        CustomPluginSupport.requireCompatible(descriptor);checks++;
        completeAuthorPreset(source,descriptor);
        clockBudget(source,descriptor);
        equal(true,CustomPluginSupport.requires(descriptor,"ui.unbounded-input"),"plugin declares the host capability");
        equal(true,CustomPluginSupport.requires(descriptor,"ui.action-state"),"plugin declares action-state for the upright pose");
        equal(true,CustomPluginSupport.FEATURES.contains("ui.action-state"),"host offers action-state");
        JSONObject expected=new JSONObject("{light_angle:[0,360],d_blur:[0,300],d_clear:[0,300],d_travel:[0,96],d_band:[4,192],d_rim:[0.5,12],"
                +"d_rim_strength:[0,450],d_milk:[0,120],d_saturation:[-150,300],d_native_fx:[0,300],dock_corner:[0,84],"
                +"s_travel:[0,96],s_band:[4,192],s_rim:[0.5,12],s_rim_strength:[0,450],s_milk:[0,120],s_saturation:[-150,300],"
                +"s_clear:[0,300],s_native_fx:[0,300],d_transition:[0,100],s_transition:[0,100],lockwidget_color_clear:[0,100],lockcard_color_clear:[0,100],"
                +"jelly_press:[50,150],jelly_wobble:[0,100],jelly_bounce:[0,100],jelly_speed:[20,300],recent_corner:[0,84],jelly_swipe_strength:[0,300],"
                +"light_tilt_strength:[0,300],light_rest:[-180,180],light_rest_pitch:[-90,90],icon_glint_strength:[0,300],icon_glint_shadow:[0,150],icon_glint_width:[0.5,6],"
                +"icon_glint_opacity:[0,100],icon_glint_shadow_opacity:[0,100],icon_glint_transition:[0,24],icon_glint_shadow_transition:[0,24],"
                +"clock_opacity:[0,100],clock_color_strength:[0,100],"
                +"bg_blur:[0,300],bg_mask:[0,300],bg_milk:[0,120],bg_saturation:[-150,300],bg_light_strength:[0,100],bg_dark_strength:[0,100],side_blur:[0,300],vol_blur:[0,300],float_blur:[0,300],float_heads_blur:[0,300],float_color_strength:[0,100],"
                +"clock_round:[0,12],clock_info_strength:[0,100],drawer_blur:[0,100],drawer_bg_mask:[0,100],drawer_bg_light_strength:[0,100],drawer_bg_dark_strength:[0,100]}");
        for(String family:new String[]{"d","s"}) { // glass vc99: iOS highlight / black line, line width once per family
            expected.put(family+"_ios",new JSONArray().put(0).put(100)); expected.put(family+"_line",new JSONArray().put(0).put(100));
            expected.put(family+"_line_width",new JSONArray().put(0.2).put(4));
        }
        // glass vc107: the black line's shared position, range and softness (通用 → 黑线).
        expected.put("line_angle",new JSONArray().put(0).put(180));
        expected.put("line_range",new JSONArray().put(0).put(100));expected.put("line_soft",new JSONArray().put(0).put(100));
        for(String prefix:new String[]{"clock_lock","clock_desktop"}) {
            for(String suffix:new String[]{"travel","band","transition","rim","rim_strength","milk","saturation","blur","ios","line","line_width"})
                expected.put(prefix+"_"+suffix,expected.getJSONArray("d_"+suffix));
            for(String suffix:new String[]{"round","opacity","color_strength","info_strength"})
                expected.put(prefix+"_"+suffix,expected.getJSONArray("clock_"+suffix));
        }
        // Section sliders share the family ranges; their mask value is the base's “clear”.
        JSONObject suffixes=new JSONObject("{travel:'travel',band:'band',transition:'transition',rim:'rim',rim_strength:'rim_strength',milk:'milk',saturation:'saturation',mask:'clear',native_fx:'native_fx',ios:'ios',line:'line',line_width:'line_width'}");
        // beta.014: 设备中心 is part of 控制中心 and has no own values any more.
        // beta.015: 文件夹与搜索栏 (folder_*) and 控制中心 (cc_*) have own values; d_* / s_* are the 通用玻璃效果.
        // The two lock-screen shortcuts share torch_* and the lock_jelly interaction switch.
        String[] sections={"folder","dock","recent","widget","drawer","cc","nc","clear","vol","lock","lockwidget","lockcard","torch","lockpin","float","side"};
        expected.put("vol_panel_mask",new JSONArray().put(0).put(200));
        expected.put("vol_panel_blur",new JSONArray().put(0).put(300));
        expected.put("float_heads_corner",new JSONArray().put(0).put(84));
        expected.put("float_handle_opacity",new JSONArray().put(0).put(100));
        expected.put("float_black_strength",new JSONArray().put(0).put(100));expected.put("float_white_strength",new JSONArray().put(0).put(100));
        String[] noticeNames={"travel","band","transition","rim","rim_strength","native_fx"};
        double[] noticeMax={160,40,100,6,100,100};
        for(int i=0;i<noticeNames.length;i++)expected.put("lock_notice_"+noticeNames[i],new JSONArray().put(0).put(noticeMax[i]));
        expected.put("lock_notice_milk",new JSONArray().put(0).put(120));
        expected.put("lock_notice_saturation",new JSONArray().put(-150).put(300));
        expected.put("lock_notice_mask",new JSONArray().put(0).put(300));
        // glass vc99: iOS highlight and black line (percent), the black line width (dp) once per family.
        for(String key:new String[]{"lock_notice_ios","lock_notice_line"})expected.put(key,new JSONArray().put(0).put(100));
        expected.put("lock_notice_line_width",new JSONArray().put(0.2).put(4)); // vc107: every area has its own width
        for(String prefix:COMPONENT_BLURS)expected.put(prefix+"_blur",new JSONArray().put(0).put(300));
        expected.put("dock_width",new JSONArray().put(0).put(480));
        expected.put("dock_height",new JSONArray().put(0).put(240));
        JSONArray ui=descriptor.getJSONArray("ui");int sliders=0,sectionSliders=0;java.util.Set<String> keys=new java.util.HashSet<>();
        for(int i=0;i<ui.length();i++) {
            JSONObject control=ui.getJSONObject(i);if(control.has("key"))keys.add(control.getString("key"));
            if("switch".equals(control.optString("type")))equal(false,control.getBoolean("default"),control.getString("key")+" off by default");
            if(!"slider".equals(control.optString("type")))continue;
            sliders++;String key=control.getString("key");JSONArray range=expected.optJSONArray(key);
            if(range==null){
                String prefix=key.substring(0,key.indexOf('_')),suffix=key.substring(key.indexOf('_')+1);
                equal(true,java.util.Arrays.asList(sections).contains(prefix)&&suffixes.has(suffix),key+" is a section slider");
                range=expected.getJSONArray("s_"+suffixes.getString(suffix));sectionSliders++;
                JSONArray gates=new JSONArray().put(prefix+"_custom").put(prefix+"_glass");
                if(java.util.Arrays.asList("lockwidget","lockcard","torch","camera","lockpin").contains(prefix))gates.put("lock_glass").put("system_glass");
                else gates.put(java.util.Arrays.asList("folder","dock","recent","widget","drawer").contains(prefix)?"desktop_glass":"system_glass");
                equal(gates.toString(),control.getJSONArray("enableOnEdit").toString(),key+" edit enables the complete required scope");
            }
            equal(range.getDouble(0),control.getDouble("min"),key+" min");
            equal(range.getDouble(1),control.getDouble("max"),key+" max");
            boolean transition=key.endsWith("_transition")&&!key.startsWith("icon_glint");
            boolean handleOpacity="float_handle_opacity".equals(key);
            equal(!transition&&!handleOpacity,CustomPluginSchema.unboundedInput(control),key+" typed input range contract");
            double far=control.getDouble("max")*10+7,below=control.getDouble("min")-control.getDouble("max")*10-3;
            if(handleOpacity){
                reject(()->CustomPluginControlValues.parseSlider(control,Double.toString(far)),key+" rejects opacity above 100%");
                reject(()->CustomPluginControlValues.parseSlider(control,Double.toString(below)),key+" rejects negative opacity");
                equal(50d,control.getDouble("default"),key+" defaults to half opacity");
            }else if(transition){
                reject(()->CustomPluginControlValues.parseSlider(control,Double.toString(far)),key+" rejects interpolation above 100%");
                reject(()->CustomPluginControlValues.parseSlider(control,Double.toString(below)),key+" rejects negative interpolation");
                equal(0d,control.getDouble("default"),key+" preserves the old curve by default");
            }else{
                equal(far,CustomPluginControlValues.parseSlider(control,Double.toString(far)),key+" accepts typed value beyond max");
                equal(below,CustomPluginControlValues.parseSlider(control,Double.toString(below)),key+" accepts typed value below min");
            }
        }
        equal(sections.length*12,sectionSliders,"all sections expose the shared transition and their native mask/light controls, plus vc99 iOS 高光 and 黑线 and vc107 黑线粗细");
        equal(expected.length()+sectionSliders,sliders,"every Liquid Glass slider is covered");
        for(String section:sections)for(String suffix:new String[]{"_glass","_custom","_jelly"}){
            if(suffix.equals("_jelly")&&java.util.Arrays.asList("folder","cc","lockwidget","lockcard","torch","camera","lockpin").contains(section))continue; // Q弹 keys of their parent area
            equal(true,keys.contains(section+suffix),section+suffix+" declared");}
        for(String key:new String[]{"recent_clear_jelly","swipe_icons","swipe_recent_clear","vol_blur","float_blur","float_heads_blur","float_heads_g2","float_color_mode","float_color","float_color_strength","clock_round","clock_desktop","clock_lock_glass","clock_lock_custom","clock_desktop_custom","clock_info_mode","clock_info_color","clock_info_strength","media_cc_light","media_float_light"})equal(true,keys.contains(key),key+" declared");
        for(String key:new String[]{"lockwidget_mask","lockwidget_native_fx","lockcard_mask","lockcard_native_fx","lockwidget_color_clear","lockcard_color_clear","float_text_lock","float_text_color","float_handle_color","float_handle_opacity"})
            equal(true,keys.contains(key),key+" independently declared");
        for(String key:new String[]{"clock_source","clock_blur","clock_blur_follow","clock_style","clock_plain","clock_refraction","clock_highlight","clock_shadow","clock_dispersion"})
            equal(false,keys.contains(key),key+" native effect retired");
        for(String key:new String[]{"jelly","d_jelly","s_jelly","preview_heads_up","preview_fluid","preview_clear","refresh_status","glass_status","wallpaper_zoom_sync",
                "recent_corner_on","jelly_swipe","light_tilt","light_calibrate","icon_glint"})equal(true,keys.contains(key),key+" declared");
        // The host's 启用插件 is the only master switch: the plugin no longer declares its own.
        equal(false,keys.contains("enabled"),"no second master switch");
        // New sections start from what their family shows; values beyond the drag range are capped for the default only.
        JSONObject seeded=CustomPluginEngine.evaluate(source,new JSONObject("{s_travel:500,s_band:30,s_clear:29,d_milk:9}"));
        JSONObject byKey=new JSONObject();JSONArray seededUi=seeded.getJSONArray("ui");
        for(int i=0;i<seededUi.length();i++){JSONObject c=seededUi.getJSONObject(i);if(c.has("key"))byKey.put(c.getString("key"),c);}
        equal(96d,byKey.getJSONObject("nc_travel").getDouble("default"),"seed capped at the drag range");
        equal(30d,byKey.getJSONObject("vol_band").getDouble("default"),"system section seeded from 控制中心");
        equal(9d,byKey.getJSONObject("dock_milk").getDouble("default"),"desktop section seeded from 桌面");
        equal(false,byKey.getJSONObject("nc_custom").getBoolean("default"),"sections follow their family by default");
        equal(9d,byKey.getJSONObject("recent_milk").getDouble("default"),"任务栏 seeded from 桌面");
        equal(9d,byKey.getJSONObject("folder_milk").getDouble("default"),"文件夹与搜索栏 seeded from 通用玻璃效果·桌面类");
        equal(30d,byKey.getJSONObject("cc_band").getDouble("default"),"控制中心 seeded from 通用玻璃效果·系统界面");
        equal(false,byKey.getJSONObject("folder_custom").getBoolean("default")||byKey.getJSONObject("cc_custom").getBoolean("default"),"文件夹 and 控制中心 follow 通用玻璃效果 by default");
        equal(false,byKey.has("dev_mask")||byKey.has("dev_custom"),"设备中心 merged into 控制中心: no own page or values");
                // Menu: every area is its own entry on the plugin page; a page keeps its switches, the values sit in 效果参数.
        for(String key:new String[]{"desktop_glass","system_glass","preset_liquid_glass_v2","wallpaper_zoom_sync"})
            equal("[通用]",CustomPluginSchema.groupPath(byKey.getJSONObject(key)).toString(),key+" in 通用效果设置");
        equal("解除背景刷新同步限制",byKey.getJSONObject("wallpaper_zoom_sync").getString("title"),"sync control keeps the requested user-facing name");
        // beta.015: 图标高光 opens on two pages, 高光设置 and 跟随设置.
        for(String key:new String[]{"icon_glint","icon_glint_strength","light_angle"})
            equal("[桌面, 图标高光, 高光设置]",CustomPluginSchema.groupPath(byKey.getJSONObject(key)).toString(),key+" on the 高光设置 page");
        for(String key:new String[]{"light_tilt","light_tilt_strength","light_rest","light_calibrate"})
            equal("[桌面, 图标高光, 跟随设置]",CustomPluginSchema.groupPath(byKey.getJSONObject(key)).toString(),key+" on the 跟随设置 page");
        // beta.015: what every area follows sits in 通用效果设置.
        for(String key:new String[]{"d_blur","d_travel","d_clear"})
            equal("[通用, 通用玻璃效果·桌面类]",CustomPluginSchema.groupPath(byKey.getJSONObject(key)).toString(),key+" in 通用玻璃效果·桌面类");
        for(String key:new String[]{"s_travel","s_clear"})
            equal("[通用, 通用玻璃效果·系统界面]",CustomPluginSchema.groupPath(byKey.getJSONObject(key)).toString(),key+" in 通用玻璃效果·系统界面");
        // beta.015: everything pulled down from the status bar sits in 状态栏.
        for(String key:new String[]{"bg_blur","bg_mask","bg_tint","bg_light_color","bg_dark_strength"})
            equal("[状态栏与通知, 下拉页背景]",CustomPluginSchema.groupPath(byKey.getJSONObject(key)).toString(),key+" on the 下拉页背景 page");
        equal("[状态栏与通知, 控制中心]",CustomPluginSchema.groupPath(byKey.getJSONObject("cc_custom")).toString(),"控制中心 inside 状态栏");
        equal("[状态栏与通知, 控制中心, 效果参数]",CustomPluginSchema.groupPath(byKey.getJSONObject("cc_travel")).toString(),"控制中心 values one level down");
        equal("[状态栏与通知, 通知中心]",CustomPluginSchema.groupPath(byKey.getJSONObject("nc_custom")).toString(),"通知中心 inside 状态栏");
        equal("[状态栏与通知, 清除按钮, 效果参数]",CustomPluginSchema.groupPath(byKey.getJSONObject("clear_travel")).toString(),"清除按钮 inside 状态栏");
        equal("[音量]",CustomPluginSchema.groupPath(byKey.getJSONObject("vol_blur")).toString(),"音量背景模糊 on the 音量 page");
        // beta.015: the lock screen's parts each have a page under 锁屏.
        for(String key:new String[]{"clock_info_mode","clock_info_color","clock_info_strength"})
            equal("[通用, 时钟默认设置]",CustomPluginSchema.groupPath(byKey.getJSONObject(key)).toString(),key+" remains a shared clock default");
        equal("auto",byKey.getJSONObject("clock_info_mode").getString("default"),"小部件 colour follows the clock by default");
        equal("[锁屏, 锁屏小部件]",CustomPluginSchema.groupPath(byKey.getJSONObject("lockwidget_custom")).toString(),"锁屏小部件 inside 锁屏");
        equal("[锁屏, 锁屏卡片]",CustomPluginSchema.groupPath(byKey.getJSONObject("lockcard_custom")).toString(),"other lock-screen cards retain their separate values");
        equal("[锁屏, 锁屏小组件]",CustomPluginSchema.groupPath(byKey.getJSONObject("torch_glass")).toString(),"the two shortcuts share one lock widget page");
        equal("[锁屏, 锁屏小组件, 效果参数]",CustomPluginSchema.groupPath(byKey.getJSONObject("torch_travel")).toString(),"shared shortcut values one level down");
        equal(false,byKey.has("camera_travel")||byKey.has("camera_glass")||byKey.has("camera_custom"),"no duplicate independent camera controls remain");
        equal("[锁屏, 锁屏密码键]",CustomPluginSchema.groupPath(byKey.getJSONObject("lockpin_custom")).toString(),"锁屏密码键 inside 锁屏");
        equal("[锁屏, 锁屏通知]",CustomPluginSchema.groupPath(byKey.getJSONObject("lock_notice_glass")).toString(),"lock notification has an explicit discoverable settings entry");
        for(String suffix:noticeNames) {
            JSONObject control=byKey.getJSONObject("lock_notice_"+suffix);
            equal("[锁屏, 锁屏通知, 效果参数]",CustomPluginSchema.groupPath(control).toString(),"lock notification values live in the actual effects submenu: "+suffix);
            equal("[\"lock_notice_glass\",\"lock_glass\",\"system_glass\"]",control.getJSONArray("enableOnEdit").toString(),"lock notification edits enable only its independent native material gates: "+suffix);
        }
        for(String suffix:new String[]{"milk","saturation","mask"}){
            JSONObject c=byKey.getJSONObject("lock_notice_"+suffix);
            equal("[锁屏, 锁屏通知, 效果参数]",CustomPluginSchema.groupPath(c).toString(),"independent lock notification colour has a visible effects destination: "+suffix);
            equal("[\"lock_notice_color_custom\",\"lock_notice_glass\",\"lock_glass\",\"system_glass\"]",c.getJSONArray("enableOnEdit").toString(),"colour edit opens its own opt-in and native gates: "+suffix);
        }
        for(String prefix:COMPONENT_BLURS){
            JSONObject c=byKey.getJSONObject(prefix+"_blur");
            equal(prefix.equals("drawer_surface")?"玻璃背景模糊":"背景模糊",c.getString("title"),"component blur has a direct user-facing name: "+prefix);
            equal(100d,c.getDouble("default"),"component blur retains the existing shared baseline: "+prefix);
            equal(true,CustomPluginSchema.groupPath(c).get(CustomPluginSchema.groupPath(c).size()-1).equals("效果参数"),"component blur is inside its own effects page: "+prefix);
            JSONArray gates=c.getJSONArray("enableOnEdit");
            equal(false,gates.toString().contains("_custom"),"changing blur does not silently replace optical inheritance: "+prefix);
            equal(false,gates.toString().contains("nc_glass")&&prefix.equals("lock_notice"),"lock notifications do not require ordinary notification enablement");
        }
        equal(100d,byKey.getJSONObject("vol_blur").getDouble("default"),"音量 blur native by default");
        equal(100d,byKey.getJSONObject("bg_blur").getDouble("default"),"下拉页背景 native by default");
        equal(false,byKey.getJSONObject("bg_tint").getBoolean("default"),"no page colour by default");
        equal("[侧边栏]",CustomPluginSchema.groupPath(byKey.getJSONObject("side_blur")).toString(),"侧边栏背景模糊 on the 侧边栏 page");
        for(String key:new String[]{"drawer_glass","drawer_custom","drawer_blur","drawer_bg_mask","drawer_bg_tint","drawer_bg_light_color"})
            equal("[桌面, 应用抽屉]",CustomPluginSchema.groupPath(byKey.getJSONObject(key)).toString(),key+" on the 应用抽屉 page");
        equal("[桌面, 应用抽屉, 效果参数]",CustomPluginSchema.groupPath(byKey.getJSONObject("drawer_travel")).toString(),"drawer values one level down");
        equal(9d,byKey.getJSONObject("drawer_milk").getDouble("default"),"应用抽屉 seeded from 桌面");
        equal("[桌面, 文件夹与搜索栏]",CustomPluginSchema.groupPath(byKey.getJSONObject("folder_custom")).toString(),"folder switches inside 桌面");
        equal("[桌面, 文件夹与搜索栏, 效果参数]",CustomPluginSchema.groupPath(byKey.getJSONObject("folder_travel")).toString(),"folder values one level down");
        equal("[桌面, Dock栏, 效果参数]",CustomPluginSchema.groupPath(byKey.getJSONObject("dock_travel")).toString(),"Dock values one level down");
        equal("[桌面, Dock栏]",CustomPluginSchema.groupPath(byKey.getJSONObject("show_dock")).toString(),"Dock switches on the Dock page");
        equal("[桌面, Dock栏]",CustomPluginSchema.groupPath(byKey.getJSONObject("dock_custom")).toString(),"单独调节 on the Dock page");
        equal("[桌面, 任务栏]",CustomPluginSchema.groupPath(byKey.getJSONObject("recent_corner")).toString(),"任务栏 inside 桌面");
        equal("[桌面, 桌面小组件, 效果参数]",CustomPluginSchema.groupPath(byKey.getJSONObject("widget_travel")).toString(),"widgets inside 桌面");
        equal("[Q弹效果]",CustomPluginSchema.groupPath(byKey.getJSONObject("jelly_swipe_strength")).toString(),"swipe strength with Q弹");
        equal("[Q弹效果, 按压适用范围]",CustomPluginSchema.groupPath(byKey.getJSONObject("dock_jelly")).toString(),"per-area press switches gathered under Q弹");
        equal("[Q弹效果, 按压适用范围]",CustomPluginSchema.groupPath(byKey.getJSONObject("d_jelly")).toString(),"desktop press switch gathered under Q弹");
        for(String key:new String[]{"swipe_dock","swipe_folder","swipe_widget","swipe_icons"})equal(false,byKey.getJSONObject(key).getBoolean("default"),key+" off by default");
        equal("[Q弹效果, 按压适用范围]",CustomPluginSchema.groupPath(byKey.getJSONObject("recent_clear_jelly")).toString(),"任务栏清除按钮 press switch with the others");
        equal(false,byKey.getJSONObject("recent_clear_jelly").getBoolean("default"),"任务栏清除按钮 off by default");
        equal("[Q弹效果, 滑动适用范围]",CustomPluginSchema.groupPath(byKey.getJSONObject("swipe_icons")).toString(),"其他图标 with the swipe scopes");
        equal("[Q弹效果, 滑动适用范围]",CustomPluginSchema.groupPath(byKey.getJSONObject("swipe_recent_clear")).toString(),"任务栏清除按钮 with the swipe scopes");
        equal(false,byKey.getJSONObject("swipe_recent_clear").getBoolean("default"),"任务栏清除按钮 swipe off by default");
        for(String key:new String[]{"swipe_recent","swipe_control","swipe_notification","swipe_other"})equal(false,byKey.getJSONObject(key).getBoolean("default"),key+" off by default");
        equal("[Q弹效果, 滑动适用范围]",CustomPluginSchema.groupPath(byKey.getJSONObject("swipe_widget")).toString(),"swipe scope page");
        equal("native",byKey.getJSONObject("media_cc_light").getString("default"),"control-centre media light keeps the OEM colour by default");
        equal("native",byKey.getJSONObject("media_float_light").getString("default"),"流体云 media light keeps the OEM colour by default");
        equal(false,byKey.has("media_clear_color")||byKey.has("media_accent_glow"),"one choice per place, no overlapping removal and accent switches");
        equal(false,byKey.has("media_auto_color")||byKey.has("media_color_strength"),"no added cover sampling or tint controls");
        equal("[媒体取色]",CustomPluginSchema.groupPath(byKey.getJSONObject("media_cc_light")).toString(),"media cover light choices on one page");
        equal(0d,byKey.getJSONObject("clock_round").getDouble("default"),"lock clock keeps the font corners by default");
        equal(false,byKey.getJSONObject("clock_desktop").getBoolean("default"),"desktop clock widget link starts off");
        for(String key:new String[]{"clock_glass","clock_round","clock_opacity","clock_color_mode","clock_color","clock_color_strength"})
            equal("[通用, 时钟默认设置]",CustomPluginSchema.groupPath(byKey.getJSONObject(key)).toString(),key+" on the shared clock-default page");
        equal("[锁屏, 锁屏时钟]",CustomPluginSchema.groupPath(byKey.getJSONObject("clock_lock_glass")).toString(),"lock and AOD have a real independent enable gate");
        equal("[桌面, 桌面时钟]",CustomPluginSchema.groupPath(byKey.getJSONObject("clock_desktop")).toString(),"desktop keeps its existing gate on its own page");
        for(String prefix:new String[]{"clock_lock","clock_desktop"}) {
            String path=prefix.equals("clock_lock")?"[锁屏, 锁屏时钟]":"[桌面, 桌面时钟]";
            equal(path,CustomPluginSchema.groupPath(byKey.getJSONObject(prefix+"_custom")).toString(),prefix+" has its own opt-in");
            equal(false,byKey.getJSONObject(prefix+"_custom").getBoolean("default"),prefix+" follows the actual folder by default");
            equal(path.substring(0,path.length()-1)+", 效果参数]",CustomPluginSchema.groupPath(byKey.getJSONObject(prefix+"_travel")).toString(),prefix+" values are on its own page");
            equal(new JSONArray().put(prefix+"_custom").put("clock_glass").put(prefix.equals("clock_lock")?"clock_lock_glass":"clock_desktop").toString(),
                    byKey.getJSONObject(prefix+"_blur").getJSONArray("enableOnEdit").toString(),prefix+" edits enable only its own surface");
        }
        equal("native",byKey.getJSONObject("clock_color_mode").getString("default"),"lock clock keeps its own colour by default");
        equal(100d,byKey.getJSONObject("clock_opacity").getDouble("default"),"lock clock fully opaque by default");
        java.util.List<String> tops=new java.util.ArrayList<>();
        for(int i=0;i<seededUi.length();i++){JSONObject c=seededUi.getJSONObject(i);
            if(!"features".equals(c.optString("section"))||!c.has("groupPath"))continue;
            String top=CustomPluginSchema.groupPath(c).get(0);if(!tops.contains(top))tops.add(top);
            equal(false,"高级设置".equals(top),c.optString("key")+" not buried under 高级设置");}
        // vc99 menu (approved by the author): grouped by where the glass is.
        equal("[通用, 桌面, 锁屏, 状态栏与通知, 音量, 侧边栏, Q弹效果, 媒体取色, 主模块功能]",tops.toString(),"top-level entries in order (vc115 媒体强调色, vc114 主模块功能, before 运行状态)");
        equal("[\"recent_corner_on\",\"recent_glass\"]",byKey.getJSONObject("recent_corner").getJSONArray("enableOnEdit").toString(),"editing the corner switches it on");
        upgrade(source);
        clockUpgrade(source);
        transitionUpgrade(source);
        dockUpgrade(source);
        minusUpgrade(source,descriptor);
        shortcutUpgrade(source,descriptor);
        componentUpgrade(source,descriptor);
    }

    /** Actual host upgrade removes only the retired duplicate camera form; shared values stay authoritative. */
    private static void shortcutUpgrade(String source,JSONObject descriptor)throws Exception {
        JSONObject previous=new JSONObject(descriptor.toString());
        removeComponentAdditions(previous);
        previous.remove("configurationAliases");
        JSONArray controls=previous.getJSONArray("ui");int count=controls.length();
        for(int i=0;i<count;i++) {
            JSONObject control=controls.getJSONObject(i);
            String key=control.optString("key");
            if(!key.startsWith("torch_"))continue;
            JSONObject camera=new JSONObject(control.toString()).put("key","camera_"+key.substring(6));
            if(camera.has("enableOnEdit")) {
                JSONArray old=camera.getJSONArray("enableOnEdit"),gates=new JSONArray();
                for(int g=0;g<old.length();g++)gates.put(old.getString(g).replace("torch_","camera_"));
                camera.put("enableOnEdit",gates);
            }
            controls.put(camera);
        }
        previous.put("versionCode",descriptor.getInt("versionCode")-1);
        previous.put("runtime",new JSONObject().put("source","void 0;"));
        String oldSource="var plugin = "+previous+";\nC17.define(plugin);";
        File directory=Files.createTempDirectory("c17-glass-shortcuts-").toFile();
        CustomPluginRepository repo=new CustomPluginRepository(directory);
        String id=install(repo,oldSource,false).id;
        repo.updateState(id,"torch_milk",37);repo.updateState(id,"torch_travel",413);
        repo.updateState(id,"camera_milk",81);repo.updateState(id,"camera_travel",9);
        repo.updateState(id,"lockcard_milk",52);repo.updateState(id,"lockwidget_milk",24);
        String exported=repo.exportConfiguration(id,previous.getInt("versionCode"));
        equal(356,new JSONObject(exported).getJSONObject("state").length(),"real previous plugin export contains both independent shortcut forms");
        JSONObject before=new JSONObject(repo.list().get(0).state.toString());
        String inline=source.replace("\"entry\": \"runtime.js\"","\"source\": \"void 0;\"");
        install(repo,inline,true);
        JSONObject after=repo.list().get(0).state;
        for(String key:before.keySet()) {
            if(key.startsWith("camera_")||key.equals("media_clear_color"))equal(false,after.has(key),"retired duplicate setting is absent: "+key);
            else equal(before.get(key).toString(),after.get(key).toString(),"merging shortcuts preserves existing setting: "+key);
        }
        equal(37,after.getInt("torch_milk"),"shared values preserve the original left shortcut");
        equal(413,after.getInt("torch_travel"),"merging preserves a typed shared value beyond the old drag range");
        repo.updateState(id,"torch_milk",63);
        equal(63,repo.list().get(0).state.getInt("torch_milk"),"the single shared value remains editable after upgrade");
        int version=descriptor.getInt("versionCode");repo.setEnabled(id,true);
        CustomPluginRepository.ConfigurationPreview imported=repo.previewConfiguration(id,version,new ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8)));
        equal(344,imported.settingCount,"older 356-value JSON normalizes to the 344 shared current settings (vc116: its 媒体控件去除原厂取色 value is an alias of the saved lock value)");
        repo.importConfiguration(imported);after=repo.list().get(0).state;
        equal(37,after.getInt("torch_milk"),"old complete JSON gives torch precedence over the independent camera value");
        equal(413,after.getInt("torch_travel"),"old complete JSON preserves the exact typed shared value");
        equal(true,repo.list().get(0).enabled,"old complete JSON import preserves enablement");
        JSONObject cameraOnly=new JSONObject(exported),oldState=cameraOnly.getJSONObject("state");
        for(String key:new java.util.HashSet<>(oldState.keySet()))if(key.startsWith("torch_"))oldState.remove(key);
        repo.importConfiguration(repo.previewConfiguration(id,version,new ByteArrayInputStream(cameraOnly.toString().getBytes(StandardCharsets.UTF_8))));
        after=repo.list().get(0).state;
        equal(81,after.getInt("torch_milk"),"old camera-only JSON maps to the shared setting when no torch value was exported");
        equal(9,after.getInt("torch_travel"),"camera-only refraction also reaches the shared value");
        equal(52,after.getInt("lockcard_milk"),"configuration migration preserves the separate lock card");
        equal(24,after.getInt("lockwidget_milk"),"configuration migration preserves the separate weather/steps widget");
    }

    /** Real 058 import, edits and upgrade: no existing value or gate is rewritten by the new controls. */
    private static void componentUpgrade(String source,JSONObject descriptor)throws Exception {
        JSONObject previous=new JSONObject(descriptor.toString());removeComponentAdditions(previous);
        previous.put("versionCode",descriptor.getInt("versionCode")-1).put("runtime",new JSONObject().put("source","void 0;"));
        equal(345,CustomPluginSchema.configurationCount(previous),"component fixture is the exact previous shared 058 form");
        CustomPluginRepository repo=new CustomPluginRepository(Files.createTempDirectory("c17-component-upgrade-").toFile());
        String id=install(repo,"C17.define("+previous+");",false).id;
        repo.updateState(id,"nc_milk",1e30);repo.updateState(id,"nc_saturation",-1e30);repo.updateState(id,"nc_mask",1e30);
        repo.updateState(id,"d_blur",47);repo.updateState(id,"torch_milk",33);repo.setEnabled(id,true);
        JSONObject before=new JSONObject(repo.list().get(0).state.toString());
        String inline=source.replace("\"entry\": \"runtime.js\"","\"source\": \"void 0;\"");
        install(repo,inline,true);JSONObject state=repo.list().get(0).state;
        equal(453,state.length(),"component upgrade adds exactly eighteen supported settings (vc99: +44 iOS look, vc107: +22 black line, vc114: +15 主模块功能, vc116: 媒体取色 -6, vc115: +8 媒体强调色)");
        for(String key:before.keySet()) {
            if(key.equals("media_clear_color")){equal(false,state.has(key),"vc116 retires the 媒体控件去除原厂取色 switch");continue;}
            equal(before.get(key).toString(),state.get(key).toString(),"058 value survives component controls: "+key);
        }
        for(String prefix:COMPONENT_BLURS)equal(100d,state.getDouble(prefix+"_blur"),"new component blur follows its old backend baseline: "+prefix);
        equal(false,state.getBoolean("lock_notice_color_custom"),"new notification colour remains inherited until explicit edit");
        equal(4d,state.getDouble("lock_notice_milk"),"stable valid default is independent from an unbounded inherited value");
        for(String key:state.keySet())equal(false,key.startsWith("camera_"),"new controls preserve shared shortcut ownership: "+key);
        JSONObject author=null;JSONArray controls=CustomPluginEngine.evaluate(source,state).getJSONArray("ui");
        for(int i=0;i<controls.length();i++)if("preset_liquid_glass_v2".equals(controls.getJSONObject(i).optString("key")))author=controls.getJSONObject(i).getJSONObject("values");
        equal(4d,author.getDouble("lock_notice_milk"),"manual author preset does not copy the installed user's inherited values");
        equal(25d,author.getDouble("lock_notice_mask"),"manual author preset retains its fixed original notification mask");
        repo.updateState(id,"nc_glass",false);repo.updateState(id,"lock_glass",false);repo.updateState(id,"lock_notice_color_custom",false);
        repo.updateState(id,"lock_notice_blur",37);state=repo.list().get(0).state;
        equal(true,state.getBoolean("lock_notice_glass")&&state.getBoolean("lock_glass")&&state.getBoolean("system_glass"),"blur edit opens actual independent lock notification gates");
        equal(false,state.getBoolean("nc_glass")||state.getBoolean("lock_notice_color_custom"),"blur edit leaves ordinary notifications and optical colour inheritance alone");
        repo.updateState(id,"lock_notice_milk",63);state=repo.list().get(0).state;
        equal(true,state.getBoolean("lock_notice_color_custom"),"editing a lock colour explicitly opts into its independent values");
        equal(false,state.getBoolean("nc_glass"),"independent lock colour does not activate ordinary notification glass");
        repo.updateState(id,"torch_custom",false);repo.updateState(id,"torch_blur",24);state=repo.list().get(0).state;
        equal(false,state.getBoolean("torch_custom"),"shared shortcut blur preserves optical inheritance");
        equal(24d,state.getDouble("torch_blur"),"shared shortcut has one persisted blur value");
    }

    /** The shipped preset applies the complete current form, while importing it leaves every effect disabled. */
    private static void completeAuthorPreset(String source,JSONObject descriptor)throws Exception {
        JSONObject expected=null;JSONArray ui=descriptor.getJSONArray("ui");int persistent=0;
        for(int i=0;i<ui.length();i++) {
            JSONObject control=ui.getJSONObject(i);
            if(CustomPluginSchema.persistentControl(control))persistent++;
            if("preset_liquid_glass_v2".equals(control.optString("key")))expected=control.getJSONObject("values");
        }
        equal(persistent,expected==null?0:expected.length(),"author preset covers every current persistent setting");
        equal(453,persistent,"shared shortcuts plus fourteen real component blur controls and four lock colour controls, plus vc99's 44 iOS highlight / black line settings, vc107's 22 black line settings and vc114's 15 主模块功能 switches and vc116's 媒体取色 (-6), vc115's 8 媒体强调色 settings");
        String snapshot=System.getProperty("c17.authorPresetSnapshot");
        if(snapshot!=null) {
            JSONObject captured=new JSONObject(new String(Files.readAllBytes(Paths.get(snapshot)),StandardCharsets.UTF_8));
            equal(captured.keySet(),expected.keySet(),"author preset covers exactly the requested phone snapshot");
            for(String key:captured.keySet()) {
                Object a=captured.get(key),b=expected.get(key);
                if(a instanceof Number && b instanceof Number)equal(((Number)a).doubleValue(),((Number)b).doubleValue(),"phone numeric value copied exactly: "+key);
                else equal(a,b,"phone setting copied exactly: "+key);
            }
        }
        File directory=Files.createTempDirectory("c17-actual-glass-author-").toFile();
        CustomPluginRepository repo=new CustomPluginRepository(directory);
        String inline=source.replace("\"entry\": \"runtime.js\"","\"source\": \"void 0;\"");
        CustomPluginRepository.Plugin installed=repo.install(repo.preview(new ByteArrayInputStream(inline.getBytes(StandardCharsets.UTF_8)),"main.js"),false);
        equal(false,installed.enabled,"fresh plugin import starts disabled");
        for(int i=0;i<ui.length();i++) {
            JSONObject control=ui.getJSONObject(i);
            if("switch".equals(control.optString("type")))
                equal(false,installed.state.getBoolean(control.getString("key")),"fresh import leaves effect off: "+control.getString("key"));
        }
        repo.applyPreset(installed.id,installed.versionCode,"preset_liquid_glass_v2");
        JSONObject saved=new CustomPluginRepository(directory).list().get(0).state;
        java.util.Iterator<String> names=expected.keys();
        while(names.hasNext()) {
            String key=names.next();Object wanted=expected.get(key),actual=saved.get(key);
            if(wanted instanceof Number && actual instanceof Number)
                equal(((Number)wanted).doubleValue(),((Number)actual).doubleValue(),"actual author number persists: "+key);
            else equal(wanted,actual,"actual author preset persists: "+key);
        }
    }

    /** Execute legal saved forms under the unchanged production 200k instruction/time budget. */
    private static void clockBudget(String source,JSONObject descriptor)throws Exception {
        JSONArray ui=descriptor.getJSONArray("ui");JSONObject author=null;
        for(int index=0;index<ui.length();index++)if("preset_liquid_glass_v2".equals(ui.getJSONObject(index).optString("key")))
            author=new JSONObject(ui.getJSONObject(index).getJSONObject("values").toString());
        author.put("clock_lock_custom",true).put("clock_desktop_custom",true);
        equal(2,CustomPluginEngine.evaluate(source,author).getInt("apiVersion"),"both independent clock pages fit the actual host budget");
        for(int index=0;index<ui.length();index++) {
            JSONObject control=ui.getJSONObject(index);String key=control.optString("key");
            if("switch".equals(control.optString("type"))&&key.endsWith("_custom"))author.put(key,true);
        }
        equal(2,CustomPluginEngine.evaluate(source,author).getInt("apiVersion"),"all legal independent-section flags fit the actual host budget");
    }

    private static int persistentCount(JSONArray ui) throws Exception {
        int count=0;for(int i=0;i<ui.length();i++)if(CustomPluginSchema.persistentControl(ui.getJSONObject(i)))count++;return count;
    }

    private static boolean newGlassValue(String key) {
        return key.endsWith("_transition")&&!key.startsWith("icon_glint")
                ||key.equals("lockwidget_mask")||key.equals("lockwidget_native_fx")
                ||key.equals("lockcard_mask")||key.equals("lockcard_native_fx")
                ||key.equals("lockwidget_color_clear")||key.equals("lockcard_color_clear")
                ||key.startsWith("float_text_") // vc100 automatic text colour controls depend on float_text_lock
                ||key.equals("dock_width")||key.equals("dock_height")||minusValue(key);
    }

    private static boolean minusValue(String key) {
        return key.equals("minus_blur")||key.equals("minus_color_mode")||key.equals("minus_color")||key.equals("minus_color_strength");
    }

    /** Remove the transition-era controls from the current form, then verify migration and preservation. */
    private static void transitionUpgrade(String source)throws Exception {
        source=source.replace("\"entry\": \"runtime.js\"","\"source\": \"void 0;\"");
        JSONObject descriptor=CustomPluginEngine.evaluate(source,new JSONObject());
        JSONArray controls=descriptor.getJSONArray("ui"),previous=new JSONArray();JSONObject preset=null;
        for(int i=0;i<controls.length();i++) {
            JSONObject c=new JSONObject(controls.getJSONObject(i).toString());String key=c.optString("key");
            if(newGlassValue(key))continue;
            if("preset_liquid_glass_v2".equals(key)) {
                preset=c.getJSONObject("values");java.util.List<String> remove=new java.util.ArrayList<>();
                java.util.Iterator<String> names=preset.keys();while(names.hasNext()){String name=names.next();if(newGlassValue(name))remove.add(name);}
                for(String name:remove)preset.remove(name);
            }
            previous.put(c);
        }
        equal(persistentCount(previous),preset.length(),"synthetic transition fixture covers every retained setting");
        JSONObject older=new JSONObject(descriptor.toString()).put("ui",previous).put("versionCode",descriptor.getInt("versionCode")-1);older.remove("configurationAliases");
        for(boolean independent:new boolean[]{false,true}) {
            File dir=Files.createTempDirectory("c17-transition-upgrade-").toFile();CustomPluginRepository repo=new CustomPluginRepository(dir);
            CustomPluginRepository.Plugin old=repo.install(repo.preview(new ByteArrayInputStream(("C17.define("+older+");").getBytes(StandardCharsets.UTF_8)),"main.js"),false);
            repo.applyPreset(old.id,old.versionCode,"preset_liquid_glass_v2");
            repo.updateState(old.id,"folder_custom",true);repo.updateState(old.id,"folder_travel",50.5);
            repo.updateState(old.id,"folder_band",640);repo.updateState(old.id,"clock_opacity",80);
            repo.updateState(old.id,"clock_lock_custom",independent);repo.updateState(old.id,"clock_desktop_custom",independent);
            JSONObject before=new JSONObject(repo.list().get(0).state.toString());
            CustomPluginRepository.Plugin updated=repo.install(repo.preview(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),"main.js"),true);
            JSONObject state=updated.state;java.util.Iterator<String> names=before.keys();
            while(names.hasNext()){String key=names.next();equal(before.get(key).toString(),state.get(key).toString(),"existing value survives transition upgrade: "+key);}
            for(String key:state.keySet())if(newGlassValue(key)) {
                if(key.endsWith("_transition"))equal(0d,state.getDouble(key),"new transition preserves every current curve: "+key);
                else if(key.endsWith("_color_clear"))equal(0d,state.getDouble(key),"new native colour removal starts unchanged: "+key);
                else if(key.endsWith("_mask")||key.endsWith("_native_fx"))equal(100d,state.getDouble(key),"new widget controls preserve previous removal: "+key);
                else if(key.equals("float_text_lock"))equal(false,state.getBoolean(key),"new floating text option stays off");
            }
            equal(persistentCount(descriptor.getJSONArray("ui")),state.length(),"upgrade adds only the 36 declared fields");
        }
    }

    /** The immediately previous 344-field form gains only inert dock dimensions. */
    private static void dockUpgrade(String source)throws Exception {
        source=source.replace("\"entry\": \"runtime.js\"","\"source\": \"void 0;\"");
        JSONObject descriptor=CustomPluginEngine.evaluate(source,new JSONObject());
        JSONArray previous=new JSONArray();JSONObject preset=null;
        for(int i=0;i<descriptor.getJSONArray("ui").length();i++) {
            JSONObject c=new JSONObject(descriptor.getJSONArray("ui").getJSONObject(i).toString());
            String key=c.optString("key");
            if(key.equals("dock_width")||key.equals("dock_height")||minusValue(key))continue;
            if(key.equals("preset_liquid_glass_v2")) {
                preset=c.getJSONObject("values");preset.remove("dock_width");preset.remove("dock_height");
                for(String added:new String[]{"minus_blur","minus_color_mode","minus_color","minus_color_strength"})preset.remove(added);
            }
            previous.put(c);
        }
        equal(persistentCount(previous),preset.length(),"synthetic dock fixture covers every retained setting");
        JSONObject older=new JSONObject(descriptor.toString()).put("ui",previous).put("versionCode",descriptor.getInt("versionCode")-1);older.remove("configurationAliases");
        File dir=Files.createTempDirectory("c17-dock-upgrade-").toFile();CustomPluginRepository repo=new CustomPluginRepository(dir);
        CustomPluginRepository.Plugin old=repo.install(repo.preview(new ByteArrayInputStream(("C17.define("+older+");").getBytes(StandardCharsets.UTF_8)),"main.js"),false);
        repo.applyPreset(old.id,old.versionCode,"preset_liquid_glass_v2");
        repo.updateState(old.id,"dock_corner",93.5);repo.updateState(old.id,"folder_travel",50.5);
        repo.updateState(old.id,"clock_lock_transition",66.5);repo.updateState(old.id,"float_heads_g2",true);
        JSONObject before=new JSONObject(repo.list().get(0).state.toString());
        CustomPluginRepository.Plugin updated=repo.install(repo.preview(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),"main.js"),true);
        for(String key:before.keySet())equal(before.get(key).toString(),updated.state.get(key).toString(),"beta.025 value preserved: "+key);
        equal(persistentCount(previous)+2,updated.state.length(),"only two dock settings added");
        equal(0d,updated.state.getDouble("dock_width"),"new width preserves native geometry");
        equal(0d,updated.state.getDouble("dock_height"),"new height preserves native geometry");
    }

    /** Frozen areas have no controls, preset entries or process scope; other settings survive an upgrade. */
    private static void minusUpgrade(String source,JSONObject descriptor)throws Exception {
        JSONArray ui=descriptor.getJSONArray("ui");
        for(int i=0;i<ui.length();i++) {
            JSONObject item=ui.getJSONObject(i);String key=item.optString("key");
            equal(false,key.startsWith("minus_")||key.equals("float_capsule_glass"),"retired controls are absent");
            equal(false,CustomPluginSchema.groupPath(item).contains("负一屏"),"assistant entry is hidden");
            JSONObject values=item.optJSONObject("values");
            if(values!=null)for(String name:values.keySet())equal(false,name.startsWith("minus_")||name.equals("float_capsule_glass"),"preset cannot restore retired effect");
        }
        equal(false,descriptor.getJSONArray("scopes").toString().contains("com.coloros.assistantscreen"),"assistant process no longer loads plugin");
        JSONObject older=new JSONObject(descriptor.toString());older.remove("configurationAliases");
        JSONArray previous=older.getJSONArray("ui");
        for(String key:new String[]{"minus_glass","minus_jelly","minus_custom","float_capsule_glass"})
            previous.put(new JSONObject().put("type","switch").put("key",key).put("title",key).put("default",true));
        older.put("runtime",new JSONObject().put("source","void 0;")).put("versionCode",descriptor.getInt("versionCode")-1);
        String inline=source.replace("\"entry\": \"runtime.js\"","\"source\": \"void 0;\"");
        CustomPluginRepository repo=new CustomPluginRepository(Files.createTempDirectory("c17-frozen-upgrade-").toFile());
        CustomPluginRepository.Plugin old=repo.install(repo.preview(new ByteArrayInputStream(("C17.define("+older+");").getBytes(StandardCharsets.UTF_8)),"main.js"),false);
        repo.applyPreset(old.id,old.versionCode,"preset_liquid_glass_v2");
        repo.updateState(old.id,"d_blur",73);repo.updateState(old.id,"float_text_color","#80446688");
        JSONObject before=new JSONObject(repo.list().get(0).state.toString());
        CustomPluginRepository.Plugin next=repo.install(repo.preview(new ByteArrayInputStream(inline.getBytes(StandardCharsets.UTF_8)),"main.js"),true);
        equal(before.length()-4,next.state.length(),"upgrade removes only retired synthetic controls");
        for(String key:next.state.keySet())equal(before.get(key).toString(),next.state.get(key).toString(),"upgrade preserves "+key);
        reject(()->repo.updateState(old.id,"minus_glass",true),"retired assistant cannot be re-enabled");
        reject(()->repo.updateState(old.id,"float_capsule_glass",true),"retired capsule cannot be re-enabled");
    }

    /** A synthetic pre-independent form upgrades without changing any retained source or enable value. */
    private static void clockUpgrade(String source)throws Exception {
        source=source.replace("\"entry\": \"runtime.js\"","\"source\": \"void 0;\"");
        JSONObject descriptor=CustomPluginEngine.evaluate(source,new JSONObject());
        JSONArray all=descriptor.getJSONArray("ui"),legacy=new JSONArray();
        JSONObject preset=null;
        for(int index=0;index<all.length();index++) {
            JSONObject control=new JSONObject(all.getJSONObject(index).toString());String key=control.optString("key");
            if(key.startsWith("clock_lock_")||key.startsWith("clock_desktop_")||newGlassValue(key))continue;
            if("preset_liquid_glass_v2".equals(key)) {
                preset=control.getJSONObject("values");
                java.util.List<String> remove=new java.util.ArrayList<>();java.util.Iterator<String> names=preset.keys();
                while(names.hasNext()){String name=names.next();if(name.startsWith("clock_lock_")||name.startsWith("clock_desktop_")||newGlassValue(name))remove.add(name);}
                for(String name:remove)preset.remove(name);
            }
            // Old version did not name the new lock gate as an edit enabler.
            if(control.has("enableOnEdit"))control.remove("enableOnEdit");
            legacy.put(control);
        }
        equal(persistentCount(legacy),preset.length(),"synthetic clock fixture covers every retained setting");
        JSONObject older=new JSONObject(descriptor.toString()).put("ui",legacy).put("versionCode",descriptor.getInt("versionCode")-1);older.remove("configurationAliases");
        String previous="C17.define("+older+");";
        for(boolean enabled:new boolean[]{false,true}) {
            File directory=Files.createTempDirectory("c17-clock-independent-").toFile();CustomPluginRepository repo=new CustomPluginRepository(directory);
            CustomPluginRepository.Plugin old=repo.install(repo.preview(new ByteArrayInputStream(previous.getBytes(StandardCharsets.UTF_8)),"main.js"),false);
            repo.applyPreset(old.id,old.versionCode,"preset_liquid_glass_v2");
            repo.updateState(old.id,"clock_glass",enabled);repo.updateState(old.id,"clock_desktop",true);
            repo.updateState(old.id,"folder_custom",true);repo.updateState(old.id,"folder_travel",63.5);repo.updateState(old.id,"folder_band",640);
            repo.updateState(old.id,"d_blur",47.4);repo.updateState(old.id,"clock_opacity",80);
            repo.updateState(old.id,"clock_color_mode","custom");repo.updateState(old.id,"clock_color","#FF336699");
            JSONObject before=new JSONObject(repo.list().get(0).state.toString());
            CustomPluginRepository.Plugin next=repo.install(repo.preview(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),"main.js"),true);
            JSONObject state=next.state;java.util.Iterator<String> names=before.keys();
            while(names.hasNext()){String name=names.next();equal(before.get(name).toString(),state.get(name).toString(),"all previous values survive clock upgrade: "+name);}
            equal(enabled,state.getBoolean("clock_lock_glass"),"new lock gate inherits the previous effective lock enable");
            for(String prefix:new String[]{"clock_lock","clock_desktop"}) {
                equal(false,state.getBoolean(prefix+"_custom"),"new independent page stays following after upgrade");
                equal(63.5d,state.getDouble(prefix+"_travel"),"unsaved independent travel starts from the actual folder");
                equal(640d,state.getDouble(prefix+"_band"),"a typed folder band beyond the old drag range seeds without a jump");
                equal(47d,state.getDouble(prefix+"_blur"),"independent blur seeds the saved folder ratio");
                equal(80d,state.getDouble(prefix+"_opacity"),"independent opacity seeds the old clock value");
                equal("custom",state.getString(prefix+"_color_mode"),"independent mode seeds the old clock choice");
                equal("#FF336699",state.getString(prefix+"_color"),"independent colour seeds the old clock colour");
            }
            repo.updateState(old.id,"clock_lock_glass",false);
            equal(true,repo.list().get(0).state.getBoolean("clock_desktop"),"turning lock off does not modify the saved desktop gate");
            equal(persistentCount(descriptor.getJSONArray("ui")),state.length(),"upgraded form adds exactly the current declared settings");
        }
    }

    /** Legacy state upgraded in the real repository: every saved value stays, new sections follow. */
    private static void upgrade(String source)throws Exception {
        File directory=Files.createTempDirectory("c17-glass-sections-").toFile();
        CustomPluginRepository repo=new CustomPluginRepository(directory);
        // A single-file import carries its runtime inline; the descriptor under test is unchanged.
        source=source.replace("\"entry\": \"runtime.js\"","\"source\": \"void 0;\"");
        int versionCode=new JSONObject(source.substring(source.indexOf("var plugin = ")+13,source.indexOf("};")+1)).getInt("versionCode");
        // The older package: the same descriptor without the per-area pages, written out as a literal. Filtering in
        // the script itself would spend most of the host's 200k-instruction budget on the filter.
        JSONObject older=CustomPluginEngine.evaluate(source,new JSONObject());
        JSONArray all=older.getJSONArray("ui"),kept=new JSONArray();
        java.util.Set<String> areas=new java.util.HashSet<>(java.util.Arrays.asList("folder","cc","dock","recent","widget","nc","clear","vol","lock","lockwidget","lockcard","torch","camera","lockpin","float","side","preview"));
        for(int i=0;i<all.length();i++){JSONObject c=all.getJSONObject(i);String k=c.optString("key");int u=k.indexOf('_');
            // The synthetic legacy form predates today's whole-form preset; its removed sections cannot be targets.
            if("preset".equals(c.optString("type")))continue;
            if(k.equals("dock_corner")||!(areas.contains(u<0?k:k.substring(0,u))||k.contains("jelly")||k.equals("wallpaper_zoom_sync")))kept.put(c);}
        older.put("ui",kept).put("versionCode",versionCode-1);older.remove("configurationAliases");
        String previous="var plugin = "+older+";\nC17.define(plugin);";
        CustomPluginRepository.Plugin old=repo.install(repo.preview(new ByteArrayInputStream(previous.getBytes(StandardCharsets.UTF_8)),"main.js"),false);
        repo.updateState(old.id,"s_travel",32);repo.updateState(old.id,"d_travel",96);repo.updateState(old.id,"dock_corner",84);repo.updateState(old.id,"s_band",640);
        JSONObject before=new JSONObject(repo.list().get(0).state.toString());
        CustomPluginRepository.Plugin next=repo.install(repo.preview(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),"main.js"),true);
        JSONObject state=repo.list().get(0).state;
        java.util.Iterator<String> names=before.keys();
        while(names.hasNext()){String key=names.next();equal(before.get(key).toString(),state.get(key).toString(),key+" kept across the upgrade");}
        equal(32d,state.getDouble("nc_travel"),"new section seeded from the saved 控制中心 value");
        equal(192d,state.getDouble("vol_band"),"seed of a typed value beyond the range is capped");
        equal(96d,state.getDouble("dock_travel"),"desktop section seeded from the saved 桌面 value");
        equal(false,state.getBoolean("nc_custom"),"upgraded sections keep following");
        equal(false,state.getBoolean("jelly"),"Q弹 starts off");
        equal(versionCode,next.versionCode,"upgraded to the new package");
        equal(false,state.getBoolean("wallpaper_zoom_sync"),"new wallpaper zoom sync starts off after the upgrade");
        equal(false,state.has("wallpaper_zoom_off"),"the old zoom-off switch is gone");
        equal(96d,state.getDouble("recent_travel"),"任务栏 seeded from the saved 桌面 value");
        equal(96d,state.getDouble("folder_travel"),"文件夹与搜索栏 seeded from the saved 桌面 value");
        equal(32d,state.getDouble("cc_travel"),"控制中心 seeded from the saved 控制中心 value");
        equal(32d,state.getDouble("torch_travel"),"手电筒 seeded from 锁屏 (here the saved 控制中心 value)");
        equal(false,state.getBoolean("torch_custom")||state.getBoolean("lockwidget_custom"),"lock parts follow 锁屏 after the upgrade");
        equal(false,state.getBoolean("folder_custom")||state.getBoolean("cc_custom"),"文件夹 and 控制中心 keep showing the saved values through 通用玻璃效果");
        equal(false,state.getBoolean("recent_corner_on"),"任务栏 keeps the native corner after the upgrade");
        equal(false,state.getBoolean("jelly_swipe"),"new swipe Q弹 starts off after the upgrade");
    }
}
