// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** One strict admission contract for the engine, imported manifests and persisted plans. */
public final class CustomPluginSchema {
    private static final int MAX_PRESET_VALUES = 512;
    private CustomPluginSchema() { }
    /** New imports require a usable settings form; legacy stored plans remain readable. */
    public static void requireConfiguration(JSONObject descriptor) {
        if(configurationCount(descriptor)==0&&!descriptor.has("nativeSettings"))fail("插件必须提供完整设置，至少声明一个开关、滑块、输入框、颜色、选择项或排序项；请联系作者完善后重新导入");
    }
    public static int configurationCount(JSONObject descriptor) {
        JSONArray ui=descriptor.optJSONArray("ui");int count=0;
        if(ui!=null)for(int index=0;index<ui.length();index++) {
            JSONObject control=ui.optJSONObject(index);if(control==null)continue;
            if(Arrays.asList("switch","slider","input","color","app","select","sort").contains(control.optString("type")))count++;
        }
        return count;
    }
    public static void validate(JSONObject descriptor) {
        fields(descriptor,"id","name","version","versionCode","apiVersion","description","author","capabilities","ui","settings","hooks","runtime","minimumHost","features","usage","scopes","nativeSettings","configurationAliases");
        metadata(descriptor);
        presentation(descriptor);
        boolean runtimeApi=descriptor.optInt("apiVersion")==2;
        Set<String> capabilities=capabilities(descriptor,runtimeApi);
        if(runtimeApi&&(!descriptor.has("runtime")||!capabilities.contains("runtime")))
            fail("API 2 必须声明 runtime 能力和运行入口");
        JSONObject settings=object(descriptor,"settings");
        if(settings.length()>0&&!capabilities.contains("settings"))fail("使用设置规则必须声明 settings 能力");
        CustomPluginSettings.validate(settings);
        JSONArray hooks=array(descriptor,"hooks",32);
        if(hooks.length()>0&&!capabilities.contains("systemui"))fail("使用系统界面规则必须声明 systemui 能力");
        CustomPluginRules.validate(descriptor);
        if(descriptor.has("runtime")) {
            if(!runtimeApi||!capabilities.contains("runtime"))fail("可执行插件必须使用 API 2 并声明 runtime 能力");
            runtime(object(descriptor,"runtime"));
        } else if(capabilities.contains("runtime"))fail("runtime 能力必须声明运行入口");
        JSONArray ui=array(descriptor,"ui",runtimeApi?Integer.MAX_VALUE:64);Set<String> keys=new HashSet<>();
        Map<String,JSONObject> controls=new HashMap<>();
        for(int i=0;i<ui.length();i++) {
            Object raw=ui.opt(i);if(!(raw instanceof JSONObject))fail("插件界面项目必须为对象");
            JSONObject control=(JSONObject)raw;
            fields(control,"type","title","description","key","default","min","max","step","unit","unboundedInput","options","value","values","section","group","groupPath","optionsAction","confirmation","enableOnEdit");
            String type=text(control,"type",16,true);text(control,"title",120,true);text(control,"description",512,false);
            if(control.has("enableOnEdit")&&!Arrays.asList("switch","slider","color","select").contains(type))fail("仅开关、数值、颜色和选择项支持编辑联动");
            if(control.has("confirmation")){
                if(!"action".equals(type))fail("只有一次性操作可以声明确认提示");
                text(control,"confirmation",1024,true);
            }
            if(control.has("section")&&!Arrays.asList("features","management").contains(text(control,"section",16,true)))fail("插件设置分区无效");
            text(control,"group",120,false);
            groupPath(control);
            if(!Arrays.asList("text","switch","slider","input","color","app","select","sort","button","preset","action","status").contains(type))fail("不支持的插件控件："+type);
            if(control.has("values")&&!"preset".equals(type))fail("只有预设可以声明组合数值");
            if("preset".equals(type))fields(control,"type","title","description","key","values","section","group","groupPath");
            if(control.has("optionsAction")&&!"sort".equals(type))fail("只有排序控件可以声明列表操作");
            if("sort".equals(type)){
                if(!runtimeApi)fail("排序控件需要 API 2");
                fields(control,"type","title","description","key","default","optionsAction","section","group","groupPath");
                String callback=text(control,"optionsAction",64,true);
                if(!validStateKey(callback)||!keys.add(callback))fail("插件列表操作键无效或重复");
            }
            if("action".equals(type)||"status".equals(type)){
                if(!runtimeApi)fail("一次性操作与只读状态需要 API 2");
                fields(control,"type","title","description","key","section","group","groupPath","confirmation");
            }
            if(control.has("unit")) {
                if(!"slider".equals(type))fail("只有滑块可以声明显示单位");
                text(control,"unit",16,false);
            }
            if(control.has("unboundedInput")) {
                if(!"slider".equals(type))fail("只有滑块可以声明不限范围的手动输入");
                if(!(control.opt("unboundedInput") instanceof Boolean))fail("unboundedInput 必须为布尔值");
            }
            if("text".equals(type)){
                if(control.has("key")){String key=text(control,"key",64,true);if(!validStateKey(key)||!keys.add(key))fail("插件状态键无效或重复");controls.put(key,control);}
                continue;
            }
            String key=text(control,"key",64,true);
            if(!validStateKey(key)||!keys.add(key))fail("插件状态键无效或重复");
            controls.put(key,control);
            if("action".equals(type)||"status".equals(type)||"preset".equals(type))continue;
            if(control.has("default"))stateValue(control,control.opt("default"));
            else stateValue(control,defaultValue(control));
            // Typed values may leave an unbounded slider's range; its declared default may not.
            if("slider".equals(type)&&!inSliderRange(control,defaultValue(control)))fail("滑块默认值超出滑块范围");
            if("button".equals(type))primitive(control.has("value")?control.opt("value"):Boolean.TRUE);
        }
        configurationAliases(descriptor,controls);
        for(JSONObject field:controls.values()) if(field.has("enableOnEdit")) {
            JSONArray targets=array(field,"enableOnEdit",4);Set<String> unique=new HashSet<>();
            if(targets.length()==0)fail("编辑联动需要声明目标开关");
            for(int n=0;n<targets.length();n++) {
                Object key=targets.opt(n);JSONObject target=key instanceof String?controls.get((String)key):null;
                if(target==null||!"switch".equals(target.optString("type"))||key.equals(field.optString("key"))||!unique.add((String)key))
                    fail("编辑联动只能引用其他已声明的唯一开关");
            }
        }
        for(int index=0;index<ui.length();index++) {
            JSONObject preset=ui.optJSONObject(index);if(preset==null||!"preset".equals(preset.optString("type")))continue;
            JSONObject values=object(preset,"values");
            if(values.length()==0||values.length()>MAX_PRESET_VALUES)fail("预设需要包含 1 到 "+MAX_PRESET_VALUES+" 个设置值");
            Iterator<String> names=values.keys();
            while(names.hasNext()) {
                String name=names.next();JSONObject field=controls.get(name);
                if(!persistentControl(field))fail("预设引用了未声明的设置："+name);
                stateValue(field,values.opt(name));
            }
        }
        if(!runtimeApi&&descriptor.toString().length()>CustomPluginEngine.MAX_JSON_BYTES)fail("插件描述过大");
    }

    /** Callback names and read-only status names travel separately from persistent configuration. */
    public static JSONObject form(JSONObject descriptor){
        JSONArray actions=new JSONArray(),statuses=new JSONArray();JSONArray ui=descriptor.optJSONArray("ui");
        if(ui!=null)for(int index=0;index<ui.length();index++){
            JSONObject control=ui.optJSONObject(index);if(control==null)continue;
            if("action".equals(control.optString("type")))actions.put(control.optString("key"));
            if("sort".equals(control.optString("type")))actions.put(control.optString("optionsAction"));
            if("status".equals(control.optString("type")))statuses.put(control.optString("key"));
        }
        try{return new JSONObject().put("actions",actions).put("statuses",statuses);}
        catch(org.json.JSONException impossible){throw new IllegalArgumentException(impossible);}
    }

    public static boolean persistentControl(JSONObject control){
        return control!=null&&Arrays.asList("switch","slider","input","color","app","select","sort","button").contains(control.optString("type"));
    }

    /** Explicit menu paths take precedence; a legacy group remains one literal level. */
    public static List<String> groupPath(JSONObject control) {
        if(control==null)return Collections.emptyList();
        if(!control.has("groupPath")) {
            String legacy=text(control,"group",120,false).trim();
            return legacy.isEmpty()?Collections.emptyList():Collections.singletonList(legacy);
        }
        JSONArray path=array(control,"groupPath",3);
        if(path.length()==0)fail("插件菜单路径需要 1 到 3 层");
        List<String> names=new ArrayList<>(path.length());
        for(int index=0;index<path.length();index++) {
            Object value=path.opt(index);
            if(!(value instanceof String)||((String)value).length()>120||((String)value).indexOf('\0')>=0||((String)value).trim().isEmpty())
                fail("插件菜单名称无效");
            names.add(((String)value).trim());
        }
        return Collections.unmodifiableList(names);
    }

    static void metadata(JSONObject value) {
        String id=text(value,"id",80,true);
        if(!id.matches("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*")||id.length()<3)fail("插件 ID 应为至少 3 位小写字母、数字、点、短横或下划线");
        text(value,"name",80,true);text(value,"version",32,true);text(value,"description",1024,false);text(value,"author",80,false);
        integer(value,"versionCode",1,Integer.MAX_VALUE);
        integer(value,"apiVersion",1,2);
    }
    static void manifest(JSONObject manifest) {
        fields(manifest,"id","name","version","versionCode","apiVersion","description","author","entry","capabilities","runtime","minimumHost","features","usage","scopes","nativeSettings","configurationAliases");
        metadata(manifest);text(manifest,"entry",4096,true);
        presentation(manifest);
        configurationAliases(manifest,null);
        boolean runtimeApi=manifest.optInt("apiVersion")==2;
        Set<String> capabilities=capabilities(manifest,runtimeApi);
        if(runtimeApi&&(!manifest.has("runtime")||!capabilities.contains("runtime")))
            fail("API 2 包必须声明 runtime 能力和运行入口");
        if(manifest.has("runtime")){if(manifest.optInt("apiVersion")!=2)fail("runtime 需要 API 2");runtime(object(manifest,"runtime"));}
    }
    private static Set<String> capabilities(JSONObject descriptor,boolean runtimeApi) {
        JSONArray permissions=array(descriptor,"capabilities",runtimeApi?3:2);
        Set<String> result=new HashSet<>();
        for(int index=0;index<permissions.length();index++) {
            Object permission=permissions.opt(index);
            if(!(permission instanceof String)||!("settings".equals(permission)||"systemui".equals(permission)||runtimeApi&&"runtime".equals(permission))||!result.add((String)permission))
                fail("插件能力声明无效或重复");
        }
        return result;
    }
    private static void runtime(JSONObject value) {
        fields(value,"entry","source","preferences");
        if(value.has("preferences")){if(!(value.opt("preferences") instanceof JSONArray))fail("插件偏好声明必须为列表");CustomPluginStorage.preferences(value.optJSONArray("preferences"));}
        if(value.has("entry")==value.has("source"))fail("runtime 必须且只能声明 entry 或 source");
        if(value.has("entry"))text(value,"entry",4096,true);
        else text(value,"source",Integer.MAX_VALUE,true);
    }
    static boolean sameIdentity(JSONObject a,JSONObject b) {
        for(String key:new String[]{"id","name","version","versionCode","apiVersion","description","author","capabilities","runtime","minimumHost","features","usage","scopes","nativeSettings","configurationAliases"}) {
            if(a.opt(key) instanceof Number&&b.opt(key) instanceof Number) {
                if(Double.compare(((Number)a.opt(key)).doubleValue(),((Number)b.opt(key)).doubleValue())!=0)return false;
                continue;
            }
            String first=a.has(key)?a.opt(key).toString():"";
            String second=b.has(key)?b.opt(key).toString():"";
            if(!first.equals(second))return false;
        }
        return true;
    }
    /** Retired names only; the reviewed metadata cannot create settings or alias chains. */
    private static void configurationAliases(JSONObject descriptor,Map<String,JSONObject> controls) {
        if(!descriptor.has("configurationAliases"))return;
        if(descriptor.optInt("apiVersion")!=2||descriptor.optInt("minimumHost")<265)fail("配置别名需要 API 2 与 App265 或更新宿主");
        if(!CustomPluginSupport.requires(descriptor,"config.aliases"))fail("配置别名必须声明 config.aliases 能力");
        Object raw=descriptor.opt("configurationAliases");
        if(!(raw instanceof JSONObject))fail("配置别名必须为对象");
        JSONObject aliases=(JSONObject)raw;Set<String> targets=new HashSet<>();
        if(aliases.length()>512)fail("每个插件最多声明 512 个配置别名");
        Iterator<String> keys=aliases.keys();
        while(keys.hasNext()) {
            String source=keys.next();Object target=aliases.opt(source);
            if(!validStateKey(source)||!(target instanceof String)||!validStateKey((String)target)||source.equals(target))
                fail("配置别名必须连接两个不同的有效设置键");
            if(aliases.has((String)target)||!targets.add((String)target))fail("配置别名不能串联或共享目标");
            if(controls!=null&&(controls.containsKey(source)||!persistentControl(controls.get((String)target))))
                fail("配置别名必须从旧设置指向当前持久控件："+source);
        }
    }
    private static void presentation(JSONObject descriptor){
        text(descriptor,"usage",4096,false);
        if(descriptor.has("minimumHost"))integer(descriptor,"minimumHost",1,Integer.MAX_VALUE);
        Set<String> seen=new HashSet<>();JSONArray features=array(descriptor,"features",64);
        for(int index=0;index<features.length();index++){
            Object raw=features.opt(index);if(!(raw instanceof String)||((String)raw).length()>80||!((String)raw).matches("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*")||!seen.add((String)raw))fail("宿主能力要求无效或重复");
        }
        seen.clear();JSONArray scopes=array(descriptor,"scopes",64);
        for(int index=0;index<scopes.length();index++){
            Object raw=scopes.opt(index);if(!(raw instanceof String)||((String)raw).length()>240||!((String)raw).matches("(?:android|[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)+)")||!seen.add((String)raw))fail("插件作用域声明无效或重复");
        }
        if(descriptor.has("nativeSettings")){
            if(descriptor.optInt("apiVersion")!=2)fail("原生设置界面需要 API 2");
            JSONObject entry=object(descriptor,"nativeSettings");fields(entry,"dex","className");
            String dex=text(entry,"dex",4096,true),name=text(entry,"className",240,true);
            if(!dex.matches("[a-zA-Z0-9_./-]+\\.(?:dex|jar)")||dex.startsWith("/")||Arrays.asList(dex.split("/",-1)).contains("..")||Arrays.asList(dex.split("/",-1)).contains("")||Arrays.asList(dex.split("/",-1)).contains("."))fail("原生设置代码路径无效");
            if(!name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+"))fail("原生设置入口类名无效");
        }
    }
    static boolean validStateKey(String key){return key!=null&&key.matches("[a-zA-Z][a-zA-Z0-9_.-]{0,63}")&&!"constructor".equals(key)&&!"prototype".equals(key);}
    public static Object defaultValue(JSONObject control) {
        if(control.has("default"))return control.opt("default");
        switch(control.optString("type")) {
            case "switch":return false;
            case "slider":return control.opt("min");
            case "color":return "#FFFFFF";
            case "select":JSONArray options=control.optJSONArray("options");return options==null||options.length()==0?"":options.optJSONObject(0).optString("value");
            case "button":return control.has("value")?control.opt("value"):true;
            case "sort":return "[]";
            default:return "";
        }
    }
    public static void stateValue(JSONObject control,Object value) {
        String type=control.optString("type");
        if("sort".equals(type)){CustomPluginSort.validateValue(value);return;}
        primitive(value);
        if("switch".equals(type)&&!(value instanceof Boolean))fail("开关值必须为布尔值");
        if("color".equals(type)&&(!(value instanceof String)||!((String)value).matches("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")))
            fail("颜色应为 #RRGGBB 或 #AARRGGBB");
        if(("input".equals(type)||"app".equals(type))&&(!(value instanceof String)||((String)value).length()>4096))fail("输入文字过长或类型错误");
        if("app".equals(type)&&!((String)value).isEmpty()&&!((String)value).matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))fail("应用包名无效");
        if("slider".equals(type)) {
            double min=number(control.opt("min")),max=number(control.opt("max")),step=control.has("step")?number(control.opt("step")):1d;
            double current=number(value);
            if(min>=max||min< -100000||max>100000||step<=0||step>max-min||!unboundedInput(control)&&(current<min||current>max))fail("滑块值或范围无效");
        }
        if("select".equals(type)) {
            JSONArray options=array(control,"options",64);Set<String> seen=new HashSet<>();boolean match=false;
            if(!(value instanceof String)||options.length()==0)fail("选项值无效");
            for(int j=0;j<options.length();j++) {
                JSONObject option=options.optJSONObject(j);if(option==null)fail("选项必须包含 value 和 label");
                fields(option,"value","label");String id=text(option,"value",128,true);text(option,"label",120,true);
                if(!seen.add(id))fail("选项值重复");if(id.equals(value))match=true;
            }
            if(!match)fail("状态值不在选项中");
        }
    }
    /** The slider still drags within min..max; manual and preset values only need to be finite. */
    public static boolean unboundedInput(JSONObject control) {
        return control!=null&&"slider".equals(control.optString("type"))&&Boolean.TRUE.equals(control.opt("unboundedInput"));
    }
    static boolean inSliderRange(JSONObject control,Object value) {
        double current=number(value);
        return current>=number(control.opt("min"))&&current<=number(control.opt("max"));
    }
    private static void primitive(Object value) {
        if(value instanceof String){if(((String)value).length()>4096)fail("插件状态文字过长");return;}
        if(value instanceof Boolean)return;
        if(value instanceof Number){number(value);return;}
        fail("插件状态只支持文字、数字与布尔值");
    }
    static String text(JSONObject value,String key,int max,boolean required) {
        Object raw=value.opt(key);if(raw==null&&!required)return "";
        if(!(raw instanceof String)||((String)raw).length()>max||((String)raw).indexOf('\0')>=0||(required&&((String)raw).trim().isEmpty()))fail("插件字段无效："+key);
        return (String)raw;
    }
    static int integer(JSONObject value,String key,int min,int max) {
        double number=number(value.opt(key));if(number!=Math.rint(number)||number<min||number>max)fail("插件整数字段无效："+key);return(int)number;
    }
    static double number(Object value) {if(!(value instanceof Number))throw new IllegalArgumentException("插件数字类型无效");double n=((Number)value).doubleValue();if(Double.isNaN(n)||Double.isInfinite(n))fail("插件数字必须有限");return n;}
    static JSONObject object(JSONObject value,String key) {Object raw=value.opt(key);if(raw==null)return new JSONObject();if(!(raw instanceof JSONObject))fail("插件字段必须为对象："+key);return(JSONObject)raw;}
    static JSONArray array(JSONObject value,String key,int max) {Object raw=value.opt(key);if(raw==null)return new JSONArray();if(!(raw instanceof JSONArray)||((JSONArray)raw).length()>max)fail("插件数组无效或过大："+key);return(JSONArray)raw;}
    static void fields(JSONObject value,String... allowed) {Set<String> known=new HashSet<>(Arrays.asList(allowed));Iterator<String> keys=value.keys();while(keys.hasNext()){String key=keys.next();if(!known.contains(key))fail("未知插件字段："+key);}}
    static void fail(String message){throw new IllegalArgumentException(message);}
}
