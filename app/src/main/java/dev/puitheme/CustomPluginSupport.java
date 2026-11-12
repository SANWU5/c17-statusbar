// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Versioned host contracts, separate from any plugin's business implementation. */
public final class CustomPluginSupport {
    public static final Set<String> FEATURES=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "runtime.java","runtime.dex","runtime.hooks","runtime.main","runtime.actions","runtime.status","runtime.values",
            "ui.groups","ui.group-paths","ui.management-groups","config.aliases","ui.color","ui.numeric-input","ui.unbounded-input","ui.presets","ui.edit-enables","ui.native-settings","ui.sort","ui.sort-removal","ui.app","ui.action-confirmation","ui.action-state","ui.collapsible-groups","ui.i18n","scope.systemui","scope.apps")));
    private CustomPluginSupport(){}
    public static int versionCode(){String token=ModuleRuntimeStatus.BUILD_TOKEN;return Integer.parseInt(token.substring(token.lastIndexOf("-code")+5));}
    public static JSONObject describe(String runtimePackage)throws org.json.JSONException{
        return new JSONObject().put("apiVersion",2).put("versionCode",versionCode()).put("features",new JSONArray(FEATURES))
                .put("runtimePackage",runtimePackage==null?"":runtimePackage);
    }
    public static void requireCompatible(JSONObject descriptor){requireCompatible(descriptor,versionCode());}
    static void requireCompatible(JSONObject descriptor,int host){
        JSONArray required=descriptor.optJSONArray("features");if(required!=null)for(int i=0;i<required.length();i++)
            if(!FEATURES.contains(required.optString(i)))throw new IllegalArgumentException("当前宿主尚不支持插件要求的能力："+required.optString(i));
    }
    public static String executionProblem(JSONObject descriptor){
        boolean executable=descriptor.has("runtime")||descriptor.optJSONArray("hooks")!=null&&descriptor.optJSONArray("hooks").length()>0;
        JSONArray capabilities=descriptor.optJSONArray("capabilities");if(capabilities!=null)for(int i=0;i<capabilities.length();i++)if("systemui".equals(capabilities.optString(i)))executable=true;
        if(executable){JSONArray declared=scopes(descriptor);for(int i=0;i<declared.length();i++)if(!ModuleRuntimeStatus.SYSTEM_UI.equals(declared.optString(i))){
            if(!appPackage(declared.optString(i)))return "此作用域不能运行扩展插件："+declared.optString(i);
            if(descriptor.optInt("apiVersion")!=2||!descriptor.has("runtime")||!requires(descriptor,"scope.apps"))
                return "跨应用插件需要 API 2 运行入口并声明 scope.apps 能力";
            if(descriptor.optJSONObject("settings")!=null&&descriptor.optJSONObject("settings").length()>0
                    ||descriptor.optJSONArray("hooks")!=null&&descriptor.optJSONArray("hooks").length()>0)
                return "跨应用插件请使用独立运行入口；旧系统界面规则不能跨应用执行";
            JSONObject runtime=descriptor.optJSONObject("runtime");JSONArray preferences=runtime==null?null:runtime.optJSONArray("preferences");
            if(preferences!=null&&preferences.length()>0)
                return "跨应用插件不能声明应用共享偏好文件归属，请使用每进程独立存储";
        }}
        if(executable&&!targets(descriptor,ModuleRuntimeStatus.SYSTEM_UI)
                &&CustomPluginSchema.form(descriptor).optJSONArray("actions").length()>0)
            return "表单操作与动态排序需要系统界面作用域，仅外部应用的插件暂不支持这些控件";
        return "";
    }
    static boolean appPackage(String name){return name!=null&&name.length()<=240
            &&name.matches("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)+")
            &&!ModuleDiagnostics.PACKAGE_NAME.equals(name);}
    static boolean requires(JSONObject descriptor,String feature){JSONArray features=descriptor.optJSONArray("features");
        if(features!=null)for(int i=0;i<features.length();i++)if(feature.equals(features.optString(i)))return true;return false;}
    static boolean targets(JSONObject descriptor,String target){return includes(scopes(descriptor),target);}
    static boolean includes(JSONArray declared,String target){
        if(declared==null||declared.length()==0)return ModuleRuntimeStatus.SYSTEM_UI.equals(target);
        for(int i=0;i<declared.length();i++)if(target.equals(declared.optString(i)))return true;return false;
    }
    public static void requireExecutable(JSONObject descriptor){String error=executionProblem(descriptor);if(!error.isEmpty())throw new IllegalArgumentException(error);}
    public static JSONArray scopes(JSONObject descriptor){
        JSONArray declared=descriptor.optJSONArray("scopes");
        if(declared!=null&&declared.length()>0)return declared;
        // Legacy executable plugins were always scoped to the SystemUI engine.
        return descriptor.has("runtime")||descriptor.optJSONArray("hooks")!=null&&descriptor.optJSONArray("hooks").length()>0
                ?new JSONArray().put(ModuleRuntimeStatus.SYSTEM_UI):new JSONArray();
    }
}
