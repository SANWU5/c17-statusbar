// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

/** Calls the real provider/admin with observable caller identities and actual streamed plan bytes. */
public final class CustomPluginAdminCheck {
    private static int checks;
    private interface Task {void run()throws Exception;}
    private static void equal(Object expected,Object actual,String message){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(message+": "+expected+" != "+actual);}
    private static void reject(Task task,Class<? extends Throwable> type,String message)throws Exception {
        checks++;try{task.run();}catch(Throwable failure){if(type.isInstance(failure))return;throw new AssertionError(message+": unexpected "+failure,failure);}
        throw new AssertionError("Accepted "+message);
    }
    private static JSONObject request(String action)throws Exception{return new JSONObject().put("action",action);}
    private static Bundle call(SettingsProvider provider,int uid,JSONObject request)throws Exception {
        Binder.setCallingIdentityForCheck(uid,701+uid);
        int cleared=Binder.clearsForCheck,restored=Binder.restoresForCheck;
        Bundle answer=provider.call(CustomPluginAdmin.METHOD,request.toString(),null);
        equal(uid,Binder.getCallingUid(),"admin restores caller UID");equal(701+uid,Binder.getCallingPid(),"admin restores caller PID");
        equal(cleared+1,Binder.clearsForCheck,"root admin clears identity once");equal(restored+1,Binder.restoresForCheck,"root admin restores identity once");
        return answer;
    }
    private static JSONObject result(Bundle answer)throws Exception {
        equal(false,answer.containsKey("error"),"admin action succeeds");return new JSONObject(answer.getString("result"));
    }
    private static JSONObject plan(SettingsProvider provider)throws Exception {
        Binder.setCallingIdentityForCheck(1000,2701);int restored=Binder.restoresForCheck;
        Bundle answer=provider.call(CustomPluginHooks.METHOD,null,null);
        equal(1000,Binder.getCallingUid(),"plan restores SystemUI caller UID");equal(2701,Binder.getCallingPid(),"plan restores SystemUI caller PID");
        equal(restored+1,Binder.restoresForCheck,"plan identity restored");
        String text=answer.getString(CustomPluginHooks.PLAN);
        if(text!=null){equal(false,answer.containsKey("plan_fd"),"small plan keeps string endpoint");return new JSONObject(text);}
        ParcelFileDescriptor descriptor=answer.getParcelable("plan_fd");equal(true,descriptor!=null,"large plan uses exact Parcelable descriptor signature");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(java.io.InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
            byte[] buffer=new byte[1171];int count;while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
        }
        equal(true,bytes.size()>65536,"plan pipe delivers more than Binder string threshold");
        return new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
    }
    private static void report(SettingsProvider provider,String nonce,String value,boolean error)throws Exception {
        Bundle report=new Bundle();report.putString("nonce",nonce);report.putString(error?"error":"result",value);
        Binder.setCallingIdentityForCheck(1000,2701);int restored=Binder.restoresForCheck;
        provider.call(CustomPluginAdmin.REPORT,null,report);
        equal(1000,Binder.getCallingUid(),"report restores SystemUI identity");equal(restored+1,Binder.restoresForCheck,"report identity restored once");
    }
    private static String fixture(String runtime)throws Exception {
        JSONObject descriptor=new JSONObject().put("id","admin.runtime").put("name","Admin runtime fixture").put("version","2.1.0_beta.002")
                .put("versionCode",7).put("apiVersion",2).put("capabilities",new JSONArray().put("runtime"))
                .put("runtime",new JSONObject().put("source",runtime)).put("settings",new JSONObject()).put("hooks",new JSONArray())
                .put("ui",new JSONArray().put(new JSONObject().put("type","switch").put("title","Layout").put("key","layout").put("default",true)));
        return "C17.define("+descriptor.toString()+");";
    }
    public static void main(String[] args)throws Exception {
        File files=Files.createTempDirectory("c17-plugin-admin-").toFile();
        android.os.Process.uidForCheck=20000;android.os.Process.pidForCheck=191;
        Context context=new Context(files){
            private final PackageManager packages=new PackageManager(){
                @Override public ActivityInfo getActivityInfo(android.content.ComponentName component,int flags)throws NameNotFoundException{throw new NameNotFoundException();}
                @Override public int getComponentEnabledSetting(android.content.ComponentName component){return COMPONENT_ENABLED_STATE_DEFAULT;}
                @Override public void setComponentEnabledSetting(android.content.ComponentName component,int state,int flags){}
                @Override public String[] getPackagesForUid(int uid){return uid==1000?new String[]{"com.android.systemui"}:new String[]{"untrusted.application"};}
            };
            @Override public PackageManager getPackageManager(){return packages;}
        };
        SettingsProvider provider=new SettingsProvider();provider.attachInfo(context,null);
        equal(context,provider.getContext(),"real provider receives fixture context");
        for(int uid:new int[]{20000,1000,2000,12000}) {
            Binder.setCallingIdentityForCheck(uid,701);int cleared=Binder.clearsForCheck;
            reject(()->provider.call(CustomPluginAdmin.METHOD,request("status").toString(),null),SecurityException.class,"non-root admin UID "+uid);
            reject(()->provider.call(CustomPluginAdmin.METHOD,request("runtime_status").toString(),null),SecurityException.class,"non-root runtime status UID "+uid);
            equal(uid,Binder.getCallingUid(),"rejected caller identity preserved");equal(cleared,Binder.clearsForCheck,"authorization checked before identity clear");
        }
        equal(0,result(call(provider,0,request("status"))).getJSONArray("plugins").length(),"root can read empty plugin library");
        equal(0,plan(provider).getJSONArray("runtimes").length(),"empty plan stays small");
        Bundle invalid=call(provider,0,request("unknown"));equal(true,invalid.containsKey("error"),"failed root action reports error with restored identity");

        String statusNonce=result(call(provider,0,request("runtime_status"))).getString("nonce");
        equal(true,statusNonce.matches("[a-f0-9-]{36}"),"runtime status returns an opaque nonce without any plugin");
        equal(1,context.getContentResolver().notifications,"runtime status notifies the SystemUI plan consumer");
        equal(true,result(call(provider,0,request("eval_result").put("nonce",statusNonce))).getBoolean("pending"),"runtime status waits for a native runtime report");
        JSONObject nativePlan=plan(provider),nativeCommand=nativePlan.getJSONArray("adminCommands").getJSONObject(0);
        equal(0,nativePlan.getJSONArray("runtimes").length(),"runtime status does not enable or install a plugin");
        equal("runtime_status",nativeCommand.getString("action"),"native command is explicitly dispatched as runtime status");
        equal(statusNonce,nativeCommand.getString("nonce"),"runtime status uses the existing nonce command pipe");
        equal(false,nativeCommand.has("id")||nativeCommand.has("source"),"runtime status carries no plugin identity or JavaScript");
        String nativeResult=new JSONObject().put("build_token",ModuleRuntimeStatus.BUILD_TOKEN).put("pid",2701)
                .put("health",new JSONObject().put("custom_plugin_runtime_loaded",0)).toString();
        report(provider,statusNonce,nativeResult,false);
        equal(nativeResult,result(call(provider,0,request("eval_result").put("nonce",statusNonce))).getString("result"),"runtime status reuses the existing result endpoint");
        equal(false,plan(provider).has("adminCommands"),"completed native status is no longer delivered");

        char[] padding=new char[90000];Arrays.fill(padding,'x');
        String runtime="/*"+new String(padding)+"*/ throw new Error('must not execute while reading plan'); // 完整尾部✓";
        CustomPluginRepository repository=new CustomPluginRepository(new File(files,"custom_plugins"));
        try(CustomPluginRepository.Preview preview=repository.preview(new ByteArrayInputStream(fixture(runtime).getBytes(StandardCharsets.UTF_8)),"runtime.js")){
            repository.install(preview,false);
        }
        JSONObject status=result(call(provider,0,request("status"))).getJSONArray("plugins").getJSONObject(0);
        equal("admin.runtime",status.getString("id"),"root status exposes admitted plugin identity");equal(false,status.getBoolean("enabled"),"new runtime is disabled");
        JSONObject evaluate=request("eval").put("id","admin.runtime").put("source","C17.state.layout;");
        Bundle disabled=call(provider,0,evaluate);equal(true,disabled.getString("error").contains("enabled API2"),"disabled runtime cannot receive root command");
        equal(statusNonce,new JSONObject(new String(Files.readAllBytes(new File(files,"custom_plugin_admin/request.json").toPath()),StandardCharsets.UTF_8)).getString("nonce"),"rejected eval never replaces the native status command");
        // Direct file-root repository runs the real metadata engine, avoiding any fake Messenger sandbox.
        repository.setEnabled("admin.runtime",true);
        JSONObject command=result(call(provider,0,evaluate));String nonce=command.getString("nonce");
        equal(true,nonce.matches("[a-f0-9-]{36}"),"root eval returns opaque nonce");equal(2,context.getContentResolver().notifications,"eval notifies runtime consumer");
        JSONObject pending=result(call(provider,0,request("eval_result").put("nonce",nonce)));equal(true,pending.getBoolean("pending"),"eval remains pending until runtime report");
        JSONObject streamed=plan(provider);equal(runtime,streamed.getJSONArray("runtimes").getJSONObject(0).getString("source"),"entire large UTF-8 runtime survives plan pipe");
        equal(true,streamed.getJSONArray("runtimes").getJSONObject(0).getJSONObject("state").getBoolean("layout"),"complete runtime state survives pipe");
        equal(nonce,streamed.getJSONArray("adminCommands").getJSONObject(0).getString("nonce"),"pending command travels with runtime plan");
        equal("C17.state.layout;",streamed.getJSONArray("adminCommands").getJSONObject(0).getString("source"),"command source preserved without execution");

        Bundle forged=new Bundle();forged.putString("nonce",nonce);forged.putString("result","forged");
        Binder.setCallingIdentityForCheck(12000,571);int before=Binder.clearsForCheck;
        reject(()->provider.call(CustomPluginAdmin.REPORT,null,forged),SecurityException.class,"untrusted application cannot report command result");
        equal(before,Binder.clearsForCheck,"report authorization precedes identity clearing");
        Binder.setCallingIdentityForCheck(1000,2701);int restored=Binder.restoresForCheck;
        forged.putString("nonce","stale-nonce");
        reject(()->provider.call(CustomPluginAdmin.REPORT,null,forged),IllegalStateException.class,"stale runtime report rejected");
        equal(1000,Binder.getCallingUid(),"failed report restores caller identity");equal(restored+1,Binder.restoresForCheck,"failed report executes restore finally");
        equal(true,result(call(provider,0,request("eval_result").put("nonce",nonce))).getBoolean("pending"),"stale report leaves live request pending");
        report(provider,nonce,"{\"answer\":42}",false);
        JSONObject completed=result(call(provider,0,request("eval_result").put("nonce",nonce)));
        equal(nonce,completed.getString("nonce"),"result nonce matches request");equal("{\"answer\":42}",completed.getString("result"),"root reads complete runtime result");
        equal(false,plan(provider).has("adminCommands"),"completed command is not delivered again");

        String next=result(call(provider,0,evaluate.put("source","throw new Error('reported error');"))).getString("nonce");
        equal(false,nonce.equals(next),"new eval gets a new nonce");
        forged.putString("nonce",nonce);Binder.setCallingIdentityForCheck(1000,2701);
        reject(()->provider.call(CustomPluginAdmin.REPORT,null,forged),IllegalStateException.class,"old valid nonce cannot overwrite newer command");
        equal(true,result(call(provider,0,request("eval_result").put("nonce",next))).getBoolean("pending"),"new request remains pending after stale reply");
        report(provider,next,"runtime failure",true);
        equal("runtime failure",result(call(provider,0,request("eval_result").put("nonce",next))).getString("error"),"root can read failed runtime result");
        equal(true,result(call(provider,0,request("eval_result").put("nonce",nonce))).getBoolean("pending"),"result lookup does not return another nonce's data");
        repository.setEnabled("admin.runtime",false);
        equal(true,call(provider,0,evaluate).containsKey("error"),"runtime disabled after use rejects further eval");
        System.out.println("CustomPluginAdminCheck "+checks+" checks passed");
    }
}
