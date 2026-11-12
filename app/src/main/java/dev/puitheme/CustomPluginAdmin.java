// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.json.JSONArray;
import org.json.JSONObject;

/** Root-only deployment operations. SettingsProvider authenticates the Binder UID before entry. */
final class CustomPluginAdmin {
    static final String METHOD="custom_plugin_admin",REPORT="report_custom_plugin_command";
    private static final Object LOCK=new Object();
    /** App-process memory only: loading a plan or restarting either process cannot replay a saved click. */
    private static final java.util.Map<String,FormRequest> formRequests=new java.util.LinkedHashMap<>();
    private static final class FormRequest{
        final String owner;final JSONObject command;Bundle result;
        FormRequest(String owner,JSONObject command){this.owner=owner;this.command=command;}
    }
    private static String owner(Context context)throws Exception{return context.createDeviceProtectedStorageContext().getFilesDir().getCanonicalPath();}
    static String submitForm(Context context,String id,String key,String instance)throws Exception{
        CustomPluginRepository.Plugin plugin=null;for(CustomPluginRepository.Plugin item:new CustomPluginRepository(context).list())if(item.id.equals(id)){plugin=item;break;}
        if(plugin==null||!plugin.enabled||!plugin.error.isEmpty()||plugin.descriptor.optInt("apiVersion")!=2)throw new IllegalStateException("请先启用插件并等待运行");
        String kind=key==null?"form_status":"form_action";
        if(key!=null){
            boolean declared=false;JSONArray actions=CustomPluginSchema.form(plugin.descriptor).getJSONArray("actions");
            for(int i=0;i<actions.length();i++)if(key.equals(actions.getString(i)))declared=true;
            if(!declared||instance==null||!instance.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("插件操作或运行实例无效，请刷新状态");
        }
        String nonce=java.util.UUID.randomUUID().toString();JSONObject command=new JSONObject().put("action",kind).put("id",id).put("nonce",nonce).put("ephemeral",true);
        if(key!=null)command.put("key",key).put("instanceId",instance);
        synchronized(LOCK){formRequests.put(nonce,new FormRequest(owner(context),command));}
        context.getContentResolver().notifyChange(Uri.parse(StatusBarSettings.CONTENT_URI),null);return nonce;
    }
    static Bundle takeFormResult(Context context,String nonce)throws Exception{
        synchronized(LOCK){FormRequest request=formRequests.get(nonce);if(request==null||!owner(context).equals(request.owner))throw new IllegalStateException("插件操作已失效，请重新执行");
            if(request.result==null)return null;formRequests.remove(nonce);return new Bundle(request.result);}
    }
    static void cancelForm(Context context,String nonce)throws Exception{synchronized(LOCK){FormRequest request=formRequests.get(nonce);if(request!=null&&owner(context).equals(request.owner))formRequests.remove(nonce);}}
    private static File folder(Context context){return new File(context.createDeviceProtectedStorageContext().getFilesDir(),"custom_plugin_admin");}
    private static JSONObject read(File file)throws Exception {
        return file.isFile()?new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)):new JSONObject();
    }
    private static void write(File file,JSONObject value)throws Exception {
        File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new java.io.IOException("Cannot create admin state");
        File temporary=File.createTempFile("command-",".json",parent);
        try {
            try(FileOutputStream output=new FileOutputStream(temporary)){output.write(value.toString().getBytes(StandardCharsets.UTF_8));output.getFD().sync();}
            Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {if(temporary.exists())temporary.delete();}
    }
    static Bundle call(Context context,String argument)throws Exception {
        JSONObject request=new JSONObject(argument==null?"{}":argument),result=new JSONObject();
        CustomPluginRepository repository=new CustomPluginRepository(context);String action=request.getString("action");
        switch(action){
            case "status": {
                JSONArray plugins=new JSONArray();
                for(CustomPluginRepository.Plugin plugin:repository.list())plugins.put(new JSONObject()
                        .put("id",plugin.id).put("version",plugin.version).put("versionCode",plugin.versionCode)
                        .put("enabled",plugin.enabled).put("error",plugin.error).put("state",plugin.state)
                        .put("apiVersion",plugin.descriptor.getInt("apiVersion")));
                result.put("plugins",plugins);break;
            }
            case "import": {
                File file=new File(request.getString("path")).getCanonicalFile();
                if(!file.isFile())throw new java.io.IOException("Plugin file unavailable");
                try(FileInputStream input=new FileInputStream(file);CustomPluginRepository.Preview preview=repository.preview(input,file.getName())){
                    CustomPluginRepository.Plugin plugin=repository.install(preview,true,"ADB/root 本地部署");
                    result.put("id",plugin.id).put("version",plugin.version).put("versionCode",plugin.versionCode);
                }
                break;
            }
            case "state": repository.updateState(request.getString("id"),request.getString("key"),request.get("value"));break;
            case "enable": repository.setEnabled(request.getString("id"),request.getBoolean("enabled"));break;
            case "reset": repository.resetState(request.getString("id"));break;
            case "delete": repository.delete(request.getString("id"));break;
            case "disable_all": repository.setAllDisabled();break;
            case "eval":
            case "runtime_status": {
                JSONObject command=new JSONObject().put("action",action);
                if(action.equals("eval")){
                    String id=request.getString("id");boolean found=false;
                    for(CustomPluginRepository.Plugin plugin:repository.list())if(plugin.id.equals(id)&&plugin.enabled&&plugin.descriptor.getInt("apiVersion")==2)found=true;
                    if(!found)throw new IllegalStateException("An enabled API2 plugin is required");
                    command.put("id",id).put("source",request.getString("source"));
                }else{
                    // Optional controls of the on-demand hook cost report (see HookTrace).
                    if(request.has("hook_trace_limit"))command.put("hook_trace_limit",request.getInt("hook_trace_limit"));
                    if(request.has("hook_trace_reset"))command.put("hook_trace_reset",request.getBoolean("hook_trace_reset"));
                }
                synchronized(LOCK){
                    command.put("nonce",java.util.UUID.randomUUID().toString());
                    write(new File(folder(context),"request.json"),command);result.put("nonce",command.getString("nonce"));
                }
                context.getContentResolver().notifyChange(Uri.parse(StatusBarSettings.CONTENT_URI),null);break;
            }
            case "eval_result": {
                synchronized(LOCK){result=read(new File(folder(context),"result.json"));}
                if(!request.getString("nonce").equals(result.optString("nonce")))result=new JSONObject().put("pending",true);
                break;
            }
            default: throw new IllegalArgumentException("Unknown plugin admin action");
        }
        Bundle answer=new Bundle();answer.putString("result",result.toString());return answer;
    }
    static JSONObject withCommands(Context context,JSONObject plan)throws Exception {
        synchronized(LOCK){
            JSONObject command=read(new File(folder(context),"request.json"));
            JSONObject result=read(new File(folder(context),"result.json"));
            JSONArray commands=new JSONArray();
            if(command.has("nonce")&&!command.getString("nonce").equals(result.optString("nonce")))commands.put(command);
            String owner=owner(context);for(FormRequest request:formRequests.values())if(request.owner.equals(owner)&&request.result==null)commands.put(new JSONObject(request.command.toString()));
            if(commands.length()>0)plan.put("adminCommands",commands);
        }
        return plan;
    }
    static Bundle report(Context context,Bundle values)throws Exception {
        if(values==null)throw new IllegalArgumentException("Missing command report");
        synchronized(LOCK){
            if(values.getBoolean("ephemeral_form",false)){
                String nonce=values.getString("nonce");FormRequest form=nonce==null?null:formRequests.get(nonce);
                boolean present=form!=null&&owner(context).equals(form.owner);Bundle acknowledgement=new Bundle();
                if(values.getBoolean("form_check",false)){acknowledgement.putBoolean("present",present&&form.result==null);return acknowledgement;}
                // A closed form or restarted App has no recipient. Acknowledge expiry, never recreate a request.
                if(!present){acknowledgement.putBoolean("expired",true);return acknowledgement;}
                if(form.result==null)form.result=new Bundle(values);
                return acknowledgement;
            }
            JSONObject request=read(new File(folder(context),"request.json"));String nonce=values.getString("nonce");
            if(nonce==null||!nonce.equals(request.optString("nonce")))throw new IllegalArgumentException("Stale command report");
            JSONObject result=new JSONObject().put("nonce",nonce);
            if(values.containsKey("error"))result.put("error",values.getString("error"));else result.put("result",values.getString("result"));
            write(new File(folder(context),"result.json"),result);
        }
        return new Bundle();
    }
    static Bundle plan(Context context)throws Exception {
        byte[] data=withCommands(context,CustomPluginRepository.plan(context)).toString().getBytes(StandardCharsets.UTF_8);
        Bundle answer=new Bundle();
        // Small API1 plans remain compatible with existing framework clients.
        if(data.length<=65536)answer.putString(CustomPluginHooks.PLAN,new String(data,StandardCharsets.UTF_8));
        else {
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();answer.putParcelable("plan_fd",pipe[0]);
            Thread writer=new Thread(()->{
                try(java.io.OutputStream output=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){output.write(data);}
                catch(java.io.IOException disconnected){/* The receiving runtime may be replaced during transfer. */}
            },"C17-plugin-plan-stream");writer.setDaemon(true);writer.start();
        }
        return answer;
    }
}
