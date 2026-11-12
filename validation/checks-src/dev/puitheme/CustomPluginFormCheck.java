// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real repository -> ephemeral provider queue -> existing Hooks dispatcher -> Rhino callback -> report path. */
public final class CustomPluginFormCheck {
    private static int checks;
    private static void yes(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
    private interface Task{void run()throws Exception;}
    private static void reject(Task task,String why)throws Exception{checks++;try{task.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError(why);}
    private static final class Dispatcher extends Handler{
        final ConcurrentLinkedQueue<Runnable> ready=new ConcurrentLinkedQueue<>();final List<Runnable> later=new ArrayList<>();
        Dispatcher(){super(Looper.getMainLooper());}
        @Override public boolean post(Runnable action){ready.add(action);return true;}
        @Override public synchronized boolean postDelayed(Runnable action,long delay){later.add(action);return true;}
        @Override public synchronized void removeCallbacks(Runnable action){ready.remove(action);later.removeIf(item->item==action);}
        void drain(){Runnable action;int limit=10000;while((action=ready.poll())!=null){if(--limit==0)throw new AssertionError("Unbounded form dispatch");action.run();}}
        void tick(){List<Runnable> delayed;synchronized(this){delayed=new ArrayList<>(later);later.clear();}ready.addAll(delayed);drain();}
        void until(BooleanSupplier done)throws Exception{long end=System.nanoTime()+6_000_000_000L;do{tick();if(done.getAsBoolean())return;Thread.sleep(5);}while(System.nanoTime()<end);throw new AssertionError("Timed out waiting for form pipeline");}
    }
    public static final class NativeOwner extends Context{
        final File files;final Context app;final Thread mainThread=Thread.currentThread();final List<String> executions=new ArrayList<>();int refusedReports;
        NativeOwner(File files,Context app){this.files=files;this.app=app;}
        @Override public File getFilesDir(){return files;}
        @Override public ContentResolver getContentResolver(){return new ContentResolver(){
            @Override public Bundle call(Uri uri,String method,String argument,Bundle extras){try{
                if(CustomPluginHooks.METHOD.equals(method))return CustomPluginAdmin.plan(app);
                if(CustomPluginAdmin.REPORT.equals(method)){
                    if(!extras.getBoolean("form_check",false)&&refusedReports-->0)throw new IllegalStateException("Transient form report failure");
                    return CustomPluginAdmin.report(app,extras);
                }
                return null;
            }catch(Exception failure){throw new IllegalStateException(failure);}}
        };}
        public void record(String request){if(Thread.currentThread()!=mainThread)throw new AssertionError("Form callback ran outside the existing main dispatcher");executions.add(request);}
    }
    private static JSONObject descriptor(String runtime)throws Exception{
        return new JSONObject().put("id","form.pipeline").put("name","Form pipeline").put("version","test").put("versionCode",1).put("apiVersion",2)
                .put("capabilities",new JSONArray().put("runtime")).put("runtime",new JSONObject().put("source",runtime))
                .put("settings",new JSONObject()).put("hooks",new JSONArray()).put("ui",new JSONArray()
                        .put(new JSONObject().put("type","switch").put("title","Configuration").put("key","configured").put("default",false))
                        .put(new JSONObject().put("type","action").put("title","Capture").put("key","capture"))
                        .put(new JSONObject().put("type","status").put("title","Saved time").put("key","saved_at")));
    }
    private static Bundle await(Dispatcher main,Context app,String request)throws Exception{
        final Bundle[] answer={null};final Exception[] failure={null};
        main.until(()->{try{answer[0]=CustomPluginAdmin.takeFormResult(app,request);}catch(Exception error){failure[0]=error;return true;}return answer[0]!=null;});
        if(failure[0]!=null)throw failure[0];return answer[0];
    }
    private static JSONObject response(Bundle result)throws Exception{yes(!result.containsKey("error"),"form command returns a successful transport result");return new JSONObject(result.getString("result"));}
    private static JSONObject plan(Context app)throws Exception{return new JSONObject(CustomPluginAdmin.plan(app).getString(CustomPluginHooks.PLAN));}
    private static java.util.Map<?,?> retainedCommands(CustomPluginHooks hooks)throws Exception{
        java.lang.reflect.Field field=CustomPluginHooks.class.getDeclaredField("adminResults");field.setAccessible(true);
        return (java.util.Map<?,?>)field.get(hooks);
    }
    private static Bundle retainedResponse(CustomPluginHooks hooks,String request)throws Exception{
        Object command=retainedCommands(hooks).get(request);if(command==null)return null;
        java.lang.reflect.Field values=command.getClass().getDeclaredField("values");values.setAccessible(true);return (Bundle)values.get(command);
    }
    private static void withdrawnReports(Dispatcher main,Context app,NativeOwner owner,CustomPluginHooks hooks)throws Exception{
        String query=CustomPluginAdmin.submitForm(app,"form.pipeline",null,null);hooks.accept(plan(app));
        String instance=response(await(main,app,query)).getString("instanceId");
        int executions=owner.executions.size();
        for(int cycle=0;cycle<20;cycle++){
            String request=CustomPluginAdmin.submitForm(app,"form.pipeline","capture",instance);JSONObject delivered=plan(app);
            owner.refusedReports=1;hooks.accept(delivered);main.drain();
            yes(owner.executions.size()==++executions,"a report failure follows exactly one successful callback");
            Bundle retained=retainedResponse(hooks,request);
            yes(retained!=null&&retained.getString("result").length()>180000,"unacknowledged live request retains its complete response for retry");
            CustomPluginAdmin.cancelForm(app,request);hooks.accept(plan(app));main.drain();
            yes(!retainedCommands(hooks).containsKey(request),"withdrawn unacknowledged response is released");
            hooks.accept(delivered);main.tick();
            yes(owner.executions.size()==executions,"redelivering a withdrawn action cannot replay its callback");
            retained=retainedResponse(hooks,request);
            yes(retained==null||!retained.containsKey("result"),"an expired old plan cannot recreate the large response");
            hooks.accept(plan(app));main.drain();
            yes(!retainedCommands(hooks).containsKey(request),"withdrawn expired marker is also released");
        }
        yes(retainedCommands(hooks).isEmpty(),"repeated withdrawal leaves no command response cache");
    }
    public static void main(String[] args)throws Exception{
        checks+=CustomPluginSortCheck.run();
        File root=Files.createTempDirectory("c17-form-pipeline-").toFile(),appFiles=new File(root,"app"),nativeFiles=new File(root,"systemui");appFiles.mkdirs();nativeFiles.mkdirs();Context app=new Context(appFiles);
        String runtime="C17.publishStatus({saved_at:String(C17.load('proof',{request:'not saved'}).request)});C17.onAction('capture',function(request){C17.context.record(request.requestId);C17.save('proof',{request:request.requestId});C17.publishStatus({saved_at:request.requestId});return {message:'saved',payload:new Array(180001).join('a')};});";
        JSONObject descriptor=descriptor(runtime);CustomPluginSchema.validate(descriptor);
        JSONObject bad=new JSONObject(descriptor.toString());bad.getJSONArray("ui").getJSONObject(1).put("default",true);reject(()->CustomPluginSchema.validate(bad),"actions cannot carry a persisted default value");
        JSONObject duplicate=new JSONObject(descriptor.toString());duplicate.getJSONArray("ui").getJSONObject(2).put("key","capture");reject(()->CustomPluginSchema.validate(duplicate),"read-only status cannot collide with action or saved-state keys");
        JSONObject api1=new JSONObject(descriptor.toString()).put("apiVersion",1).put("capabilities",new JSONArray());api1.remove("runtime");reject(()->CustomPluginSchema.validate(api1),"API1 does not claim executable action support");
        CustomPluginRepository repository=new CustomPluginRepository(new File(appFiles,"custom_plugins"));String source="C17.define("+descriptor.toString()+");";
        try(CustomPluginRepository.Preview preview=repository.preview(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),"form.js")){repository.install(preview,false);}repository.setEnabled("form.pipeline",true);
        CustomPluginRepository.Plugin installed=repository.list().get(0);yes(installed.state.length()==1&&!installed.state.has("capture")&&!installed.state.has("saved_at"),"action and status never enter persistent plugin state");
        reject(()->repository.updateState("form.pipeline","capture",true),"the legacy settings mutator cannot execute a form action");reject(()->repository.updateState("form.pipeline","saved_at","forged"),"read-only status cannot be forged as saved configuration");
        byte[] libraryBefore=Files.readAllBytes(new File(appFiles,"custom_plugins/library.json").toPath());
        NativeOwner owner=new NativeOwner(nativeFiles,app);Dispatcher main=new Dispatcher();CustomPluginHooks hooks=new CustomPluginHooks(main);
        hooks.configure(owner,(method,callback)->{throw new AssertionError("No hooks required for a form callback");},null);hooks.setSafeMode(false);
        main.until(()->{Bundle health=new Bundle();hooks.health(health);return health.getInt("custom_plugin_runtime_loaded")==1;});
        String statusRequest=CustomPluginAdmin.submitForm(app,"form.pipeline",null,null);hooks.accept(plan(app));JSONObject status=response(await(main,app,statusRequest));String instance=status.getString("instanceId");
        yes(status.getJSONObject("status").getString("saved_at").equals("not saved"),"a real initial callback status reaches the form");
        String first=CustomPluginAdmin.submitForm(app,"form.pipeline","capture",instance);JSONObject delivered=plan(app);owner.refusedReports=1;hooks.accept(delivered);main.drain();
        yes(owner.executions.size()==1&&owner.executions.get(0).equals(first),"the existing main-thread dispatcher runs the exact once-only click");
        yes(retainedResponse(hooks,first).getString("result").length()>180000,"failed report keeps its complete response while the request remains live");
        JSONObject captured=response(await(main,app,first));yes(captured.getBoolean("ok")&&captured.getJSONObject("status").getString("saved_at").equals(first),"the result and real stored snapshot status round-trip through provider report");
        yes(captured.getJSONObject("value").getString("payload").length()==180000,"normal report retry returns the complete original large response");
        yes(!retainedResponse(hooks,first).containsKey("result"),"acknowledged form releases its full response without replaying its callback");
        hooks.accept(delivered);main.tick();yes(owner.executions.size()==1,"redelivering a plan or retrying a failed report never repeats the action callback");
        String second=CustomPluginAdmin.submitForm(app,"form.pipeline","capture",instance);hooks.accept(plan(app));JSONObject overwritten=response(await(main,app,second));
        yes(!first.equals(second)&&owner.executions.size()==2&&overwritten.getJSONObject("status").getString("saved_at").equals(second),"a fresh explicit click overwrites the real snapshot and exposes its new saved value");
        String cancelled=CustomPluginAdmin.submitForm(app,"form.pipeline","capture",instance);JSONObject old=plan(app);CustomPluginAdmin.cancelForm(app,cancelled);hooks.accept(old);main.tick();
        yes(owner.executions.size()==2,"a form removed before execution cannot be revived from the runtime's old delivered command array");
        JSONObject replacement=plan(app);replacement.getJSONArray("runtimes").getJSONObject(0).put("source",runtime+"\n// new instance");hooks.accept(replacement);main.tick();
        String stale=CustomPluginAdmin.submitForm(app,"form.pipeline","capture",instance);JSONObject stalePlan=CustomPluginAdmin.withCommands(app,new JSONObject(replacement.toString()));hooks.accept(stalePlan);Bundle refused=await(main,app,stale);
        yes(refused.containsKey("error")&&owner.executions.size()==2,"a stale instance request is returned as an error without executing in the replacement");
        String resumedStatus=CustomPluginAdmin.submitForm(app,"form.pipeline",null,null);hooks.accept(CustomPluginAdmin.withCommands(app,new JSONObject(replacement.toString())));JSONObject current=response(await(main,app,resumedStatus));String next=current.getString("instanceId");
        yes(!instance.equals(next)&&current.getJSONObject("status").getString("saved_at").equals(second),"replacement queries redisplay the last real saved data without replaying its action");
        hooks.setSafeMode(true);String paused=CustomPluginAdmin.submitForm(app,"form.pipeline","capture",next);hooks.accept(CustomPluginAdmin.withCommands(app,new JSONObject(replacement.toString())));Bundle pausedResult=await(main,app,paused);
        yes(pausedResult.containsKey("error")&&owner.executions.size()==2,"the generic form queue cannot execute a plugin while safety mode is active");hooks.setSafeMode(false);main.tick();yes(owner.executions.size()==2,"safety resume does not replay consumed form commands");
        yes(java.util.Arrays.equals(libraryBefore,Files.readAllBytes(new File(appFiles,"custom_plugins/library.json").toPath())),"status reads, both action clicks, stale requests and safety transitions leave the entire plugin library byte-for-byte unchanged");
        yes(!new File(appFiles,"custom_plugin_admin/request.json").exists()&&!new File(appFiles,"custom_plugin_admin/result.json").exists(),"form requests and results are never persisted as replayable root admin commands");
        Bundle expired=new Bundle();expired.putString("nonce",java.util.UUID.randomUUID().toString());expired.putBoolean("ephemeral_form",true);expired.putString("result","old");yes(CustomPluginAdmin.report(app,expired).getBoolean("expired"),"a report from an old App process only acknowledges expiry and cannot create a request");
        withdrawnReports(main,app,owner,hooks);
        hooks.stop();System.out.println("CustomPluginFormCheck "+checks+" checks passed");
    }
}
