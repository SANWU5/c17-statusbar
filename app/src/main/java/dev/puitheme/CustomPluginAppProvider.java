// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Dedicated per-UID transport. This never grants access to the SystemUI settings/admin endpoints. */
final class CustomPluginAppProvider {
    static final String PLAN="read_custom_app_plugin_plan", REPORT="report_custom_app_plugin_runtime",
            STATUS="read_custom_app_plugin_status", ASSETS="/custom-plugins/apps/";
    private static final Map<String,JSONObject> reports=new LinkedHashMap<>();
    private CustomPluginAppProvider(){}
    static boolean handles(String method){return PLAN.equals(method)||REPORT.equals(method)||STATUS.equals(method);}

    static String caller(Context context,String target){
        if(!CustomPluginSupport.appPackage(target)||ModuleRuntimeStatus.SYSTEM_UI.equals(target))
            throw new SecurityException("An explicit external application package is required");
        String[] owned=context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if(owned!=null)for(String name:owned)if(target.equals(name))return target;
        throw new SecurityException("Application package does not belong to the calling UID");
    }
    static Bundle call(Context context,String method,String target,Bundle extras){
        if(STATUS.equals(method)){
            if(Binder.getCallingUid()!=Process.myUid())throw new SecurityException("Only C17 may inspect application runtime reports");
            Bundle answer=new Bundle();JSONArray values=new JSONArray();
            synchronized(reports){for(JSONObject report:reports.values())try{
                if(target==null||target.equals(report.optString("package")))values.put(new JSONObject(report.toString()));
            }catch(Exception impossible){throw new IllegalStateException(impossible);}}
            answer.putString("reports",values.toString());return answer;
        }
        String application=caller(context,target);int uid=Binder.getCallingUid(),pid=Binder.getCallingPid();
        long identity=Binder.clearCallingIdentity();
        try{
            JSONObject plan=new CustomPluginRepository(context).compiledPlan(application);
            // No host styling settings, other packages' inventory, disk roots or root commands cross this boundary.
            plan.remove("settings");plan.remove("hooks");plan.put("targetPackage",application);
            if(PLAN.equals(method)){
                SharedPreferences preferences=context.createDeviceProtectedStorageContext().getSharedPreferences(StatusBarSettings.PREFS,Context.MODE_PRIVATE);
                boolean safe=!SettingsSnapshot.notificationAllowed(preferences);
                Object saved=SettingsSnapshot.readableValues(preferences).get(StatusBarSettings.SAFE_MODE);
                safe|=saved!=null&&(!(saved instanceof Boolean)||(Boolean)saved);
                plan.put("safeMode",safe);return encoded(plan);
            }
            if(!REPORT.equals(method))throw new IllegalArgumentException("Unknown application plugin method");
            String raw=extras==null?null:extras.getString("plugins");
            if(raw==null||raw.length()>262144)throw new IllegalArgumentException("Invalid application runtime report");
            JSONArray input=new JSONArray(raw),admitted=plan.getJSONArray("runtimes"),sanitized=new JSONArray();
            if(input.length()>256)throw new IllegalArgumentException("Too many application runtime reports");
            for(int i=0;i<input.length();i++){
                JSONObject item=input.getJSONObject(i),expected=null;
                for(int j=0;j<admitted.length();j++)if(admitted.getJSONObject(j).getString("id").equals(item.optString("id")))expected=admitted.getJSONObject(j);
                if(expected==null||expected.getLong("versionCode")!=item.optLong("versionCode")||!expected.optString("generation").equals(item.optString("generation")))continue;
                String stage=item.optString("stage");
                if(!stage.matches("waiting|preparing|starting|running|stopping|failed"))throw new IllegalArgumentException("Invalid runtime stage");
                JSONObject status=new JSONObject(),published=item.optJSONObject("status"),form=expected.optJSONObject("form");
                JSONArray fields=form==null?null:form.optJSONArray("statuses");
                if(published!=null&&fields!=null)for(int j=0;j<fields.length();j++){
                    String key=fields.getString(j);Object value=published.opt(key);if(value instanceof String&&((String)value).length()<=4096)status.put(key,value);
                }
                sanitized.put(new JSONObject().put("id",expected.getString("id")).put("versionCode",expected.getLong("versionCode")).put("generation",expected.optString("generation")).put("status",status)
                        .put("stage",stage).put("hooks",Math.max(0,item.optInt("hooks")))
                        .put("assetFailures",Math.max(0,item.optInt("assetFailures"))));
            }
            JSONObject report=new JSONObject().put("package",application).put("uid",uid).put("pid",pid)
                    .put("receivedElapsed",SystemClock.elapsedRealtime()).put("plugins",sanitized);
            synchronized(reports){String key=uid+":"+pid+":"+application;reports.remove(key);reports.put(key,report);
                while(reports.size()>64)reports.remove(reports.keySet().iterator().next());}
            return new Bundle();
        }catch(RuntimeException failure){throw failure;}
        catch(Exception failure){throw new IllegalStateException("Application plugin transport failed",failure);}
        finally{Binder.restoreCallingIdentity(identity);}
    }
    static JSONObject withStatus(JSONObject descriptor,String generation,JSONObject snapshot)throws Exception{
        JSONObject values=snapshot.optJSONObject("status");if(values==null){values=new JSONObject();snapshot.put("status",values);}
        JSONArray fields=CustomPluginSchema.form(descriptor).getJSONArray("statuses"),scopes=CustomPluginSupport.scopes(descriptor);
        java.util.List<JSONObject> recent=new java.util.ArrayList<>();synchronized(reports){for(JSONObject report:reports.values())recent.add(new JSONObject(report.toString()));}
        for(int i=0;i<fields.length();i++){
            String key=fields.getString(i);StringBuilder text=new StringBuilder();
            if(CustomPluginSupport.targets(descriptor,ModuleRuntimeStatus.SYSTEM_UI))text.append("系统界面：").append(values.optString(key,"尚未收到运行报告"));
            for(int j=0;j<scopes.length();j++){
                String target=scopes.getString(j);if(ModuleRuntimeStatus.SYSTEM_UI.equals(target))continue;boolean found=false;
                for(JSONObject report:recent)if(target.equals(report.optString("package"))){
                    JSONArray plugins=report.getJSONArray("plugins");for(int n=0;n<plugins.length();n++){
                        JSONObject plugin=plugins.getJSONObject(n);
                        if(!descriptor.getString("id").equals(plugin.optString("id"))||descriptor.getLong("versionCode")!=plugin.optLong("versionCode")||!generation.equals(plugin.optString("generation")))continue;
                        long seconds=Math.max(0,SystemClock.elapsedRealtime()-report.optLong("receivedElapsed"))/1000;
                        if(text.length()>0)text.append('\n');text.append(target).append("（最近报告 · ").append(seconds).append(" 秒前 · PID ").append(report.optInt("pid")).append("）：")
                                .append(statusText(plugin,key));found=true;
                    }
                }
                if(!found){if(text.length()>0)text.append('\n');text.append(target).append("：尚未收到运行报告，请打开该应用并确认框架作用域");}
            }
            values.put(key,text.toString());
        }
        return snapshot;
    }
    private static String statusText(JSONObject plugin,String key){
        String current=plugin.optString("stage");JSONObject status=plugin.optJSONObject("status");
        // A plugin can publish a successful message before a later callback fails.
        // Retained form text must never hide that the runtime has already stopped.
        if("failed".equals(current)||"stopping".equals(current)||status==null)return stage(current);
        return status.optString(key,stage(current));
    }
    private static String stage(String stage){switch(stage){case "running":return "运行中";case "failed":return "加载失败，保留原生显示";case "stopping":return "正在停用";default:return "正在准备插件";}}
    private static Bundle encoded(JSONObject plan)throws Exception{
        byte[] bytes=plan.toString().getBytes(StandardCharsets.UTF_8);Bundle answer=new Bundle();
        if(bytes.length<=65536)answer.putString(CustomPluginHooks.PLAN,new String(bytes,StandardCharsets.UTF_8));
        else{
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();answer.putParcelable("plan_fd",pipe[0]);
            Thread writer=new Thread(()->{try(java.io.OutputStream output=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){output.write(bytes);}
                catch(java.io.IOException disconnected){/* Recipient exited while a new plan was being transferred. */}},"C17-app-plugin-plan");
            writer.setDaemon(true);writer.start();
        }
        return answer;
    }
    static ParcelFileDescriptor open(Context context,Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Read-only application plugin assets");
        java.util.List<String> parts=uri.getPathSegments();
        if(parts.size()<6)throw new FileNotFoundException("Incomplete application plugin asset URI");
        String target=caller(context,parts.get(2));long identity=Binder.clearCallingIdentity();
        try{
            StringBuilder mapped=new StringBuilder("content://"+StatusBarSettings.AUTHORITY+"/custom-plugins/assets");
            for(int i=3;i<parts.size();i++)mapped.append('/').append(java.net.URLEncoder.encode(parts.get(i),"UTF-8").replace("+","%20"));
            File file=CustomPluginRepository.providerFile(context,Uri.parse(mapped.toString()),target);
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
        }catch(Exception invalid){throw new FileNotFoundException(String.valueOf(invalid.getMessage()));}
        finally{Binder.restoreCallingIdentity(identity);}
    }
}
