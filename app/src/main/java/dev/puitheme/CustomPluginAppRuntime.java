// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.json.JSONArray;
import org.json.JSONObject;

/** Event-driven API 2 host for explicitly scoped application processes. No status-bar hooks are installed here. */
final class CustomPluginAppRuntime {
    private static final Map<String,Bootstrap> processes=new LinkedHashMap<>();
    private static final Uri URI=Uri.parse(StatusBarSettings.CONTENT_URI);
    private static final long[] RETRY_DELAYS={250L,500L,1000L,2000L,4000L,8000L,16000L};
    private CustomPluginAppRuntime(){}
    static synchronized void install(String packageName,String processName,ClassLoader loader,CustomPluginRuntime.Install installer){
        if(!CustomPluginSupport.appPackage(packageName)||ModuleRuntimeStatus.SYSTEM_UI.equals(packageName)||loader==null
                ||processName==null||processName.isEmpty())return;
        Bootstrap bootstrap=processes.get(packageName);
        if(bootstrap==null){bootstrap=new Bootstrap(packageName,processName,installer);processes.put(packageName,bootstrap);}
        bootstrap.install(loader);
    }
    private static final class Bootstrap {
        final String packageName,processName;final CustomPluginRuntime.Install installer;
        final Handler main=new Handler(Looper.getMainLooper());
        final java.util.List<ClassLoader> loaders=new java.util.ArrayList<>();
        Session session;XposedInterface.HookHandle attach;
        final Runnable applicationReady=this::connectCurrentApplication;
        Bootstrap(String packageName,String processName,CustomPluginRuntime.Install installer){this.packageName=packageName;this.processName=processName;this.installer=installer;}
        void install(ClassLoader loader){
            if(!loaders.contains(loader)&&loaders.size()<16)loaders.add(loader);
            if(session!=null){session.runtime.rememberLoader(loader);return;}
            if(attach==null)try{
                Method method=Application.class.getDeclaredMethod("attach",Context.class);
                attach=installer.hook(method,chain->{Object result=chain.proceed();Object app=chain.getThisObject();
                    if(app instanceof Context&&packageName.equals(((Context)app).getPackageName()))main.post(()->connect((Context)app));
                    return result;});
            }catch(Throwable ignored){/* Package-ready below can still supply the existing application. */}
            // Package-ready can precede ActivityThread.mInitialApplication. With the default
            // Application, optimized framework callers can also bypass the attach hook. Resolve
            // after the current bind-application message completes, instead of sampling null once.
            main.removeCallbacks(applicationReady);main.post(applicationReady);
        }
        void connectCurrentApplication(){
            if(session!=null)return;
            try{
                Method current=Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication");current.setAccessible(true);
                Object app=current.invoke(null);if(app instanceof Context)connect((Context)app);
            }catch(ReflectiveOperationException|RuntimeException ignored){}
        }
        void connect(Context candidate){
            if(session!=null||!packageName.equals(candidate.getPackageName()))return;
            Context application=candidate.getApplicationContext();if(application==null)application=candidate;
            session=new Session(application,main,installer,processName);
            for(ClassLoader loader:loaders)session.runtime.rememberLoader(loader);
            session.start();main.removeCallbacks(applicationReady);
            if(attach!=null)try{attach.unhook();attach=null;}catch(RuntimeException ignored){}
        }
    }
    /** Package-private so checks can exercise the actual provider/load/disable ordering without a device. */
    static final class Session {
        final Context owner;final Handler main;final CustomPluginRuntime runtime;final String packageName;
        final ExecutorService io=Executors.newSingleThreadExecutor(task->{Thread worker=new Thread(task,"C17-app-plugins");worker.setDaemon(true);return worker;});
        final CustomPluginRuntime.Install installer;
        final Runnable reload=this::read,report=this::report,frameworkChanged=()->request(false);
        final CustomPluginFrameworkReader framework;
        final ContentObserver observer;
        final BroadcastReceiver removal,boot;
        boolean stopped,loading,again,observing,receiving,receivingBoot;int retries;long serial;
        private boolean preferProvider,acceptedSafeMode,loggedRunning;
        private long acceptedRevision=-1,primarySnapshotRevision=-1;
        private String acceptedSource;
        Session(Context owner,Handler main,CustomPluginRuntime.Install installer){this(owner,main,installer,owner.getPackageName());}
        Session(Context owner,Handler main,CustomPluginRuntime.Install installer,String processName){
            this.owner=owner;this.main=main;this.installer=installer;packageName=owner.getPackageName();
            runtime=new CustomPluginRuntime(main,packageName,processName);
            framework=new CustomPluginFrameworkReader(installer,packageName,()->main.post(frameworkChanged));
            observer=new ContentObserver(main){@Override public void onChange(boolean selfChange){request();}};
            removal=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){if(ModuleLifecycle.isRemoval(intent))stop();}};
            boot=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
                String action=intent==null?null:intent.getAction();
                if(Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)
                        ||Intent.ACTION_USER_UNLOCKED.equals(action))bootReady();
            }};
        }
        void start(){
            if(stopped)return;
            runtime.observeLifecycle(this::scheduleReport);runtime.configure(owner,installer,this::scheduleReport);
            request(false);
        }
        private boolean connectObservers(){
            // Register separately: an invisible provider can reject the observer until boot grants are restored.
            if(!receivingBoot){IntentFilter filter=new IntentFilter(Intent.ACTION_USER_UNLOCKED);
                filter.addAction(Intent.ACTION_LOCKED_BOOT_COMPLETED);filter.addAction(Intent.ACTION_BOOT_COMPLETED);
                try{if(Build.VERSION.SDK_INT>=33)owner.registerReceiver(boot,filter,Context.RECEIVER_NOT_EXPORTED);else owner.registerReceiver(boot,filter);receivingBoot=true;}
                catch(RuntimeException ignored){}
            }
            if(!observing)try{owner.getContentResolver().registerContentObserver(URI,false,observer);observing=true;}catch(RuntimeException ignored){}
            if(!receiving){IntentFilter filter=new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);filter.addDataScheme("package");
                try{if(Build.VERSION.SDK_INT>=33)owner.registerReceiver(removal,filter,Context.RECEIVER_EXPORTED);else owner.registerReceiver(removal,filter);receiving=true;}catch(RuntimeException ignored){}
            }
            return receiving;
        }
        void request(){request(true);}
        private void request(boolean preferProvider){
            if(stopped)return;
            // A mirror callback can coalesce with a primary notification. Preserve the
            // primary preference until the current request has accepted a valid plan.
            this.preferProvider|=preferProvider;
            serial++;retries=0;main.removeCallbacks(reload);main.post(reload);
        }
        private void bootReady(){
            if(stopped)return;
            retries=0;main.removeCallbacks(reload);
            if(!loading)main.post(reload);
        }
        private void retry(){
            runtime.setSafeMode(true);
            if(retries<RETRY_DELAYS.length)main.postDelayed(reload,RETRY_DELAYS[retries++]);
        }
        private void read(){
            if(stopped)return;
            // Never start an effect that cannot later receive disable or host-uninstall events.
            if(!connectObservers()){retry();return;}
            if(loading){again=true;return;}loading=true;final long request=serial;
            final boolean providerAvailable=observing,tryProviderFirst=preferProvider,savedSafeMode=acceptedSafeMode;
            final long savedRevision=acceptedRevision,confirmedSnapshot=primarySnapshotRevision;
            execute(()->{
                JSONObject mirrored=framework.read();
                long snapshot=framework.snapshotRevision();
                // A lagging snapshot must never restore a disabled plugin or undo a newer safety switch.
                if(mirrored!=null&&(mirrored.optLong("revision",-1)<savedRevision
                        ||(snapshot<=confirmedSnapshot&&mirrored.optBoolean("safeMode")!=savedSafeMode)))mirrored=null;
                JSONObject primary=providerAvailable&&(tryProviderFirst||mirrored==null)?readProvider():null;
                final JSONObject received=primary==null?mirrored:primary;
                final boolean fromProvider=primary!=null;
                main.post(()->{
                    if(stopped)return;loading=false;
                    if(request==serial){
                        if(received==null){retry();scheduleReport();
                        }else try{
                            if(!packageName.equals(received.getString("targetPackage"))||!(received.opt("safeMode") instanceof Boolean)
                                    ||received.has("error")||!(received.opt("runtimes") instanceof JSONArray)
                                    ||!(received.opt("revision") instanceof Number)||received.getLong("revision")<0)
                                throw new IllegalArgumentException("Invalid application plugin plan");
                            boolean safe=received.getBoolean("safeMode");
                            if(safe)runtime.setSafeMode(true);
                            if(!runtime.accept(received))throw new IllegalArgumentException("Invalid application plugin plan");
                            acceptedSource=fromProvider?"provider":"framework";
                            if(acceptedRevision<0)Log.i("C17-PluginBoot",packageName+" accepted plugin plan from "+acceptedSource
                                    +" (runtimes="+received.getJSONArray("runtimes").length()+")");
                            acceptedRevision=received.getLong("revision");acceptedSafeMode=safe;
                            if(fromProvider)primarySnapshotRevision=snapshot;
                            preferProvider=false;runtime.setSafeMode(safe);retries=0;scheduleReport();
                        }catch(Exception invalid){retry();scheduleReport();}
                    }
                    if(again||request!=serial){again=false;main.removeCallbacks(reload);main.post(reload);}
                });
            });
        }
        private JSONObject readProvider(){
            try{return decode(owner.getContentResolver().call(URI,CustomPluginAppProvider.PLAN,packageName,null));}
            catch(Exception|LinkageError unavailable){return null;}
        }
        static JSONObject decode(Bundle answer)throws Exception{
            if(answer==null)throw new IllegalStateException("No application plugin plan");
            String json=answer.getString(CustomPluginHooks.PLAN);ParcelFileDescriptor descriptor=answer.getParcelable("plan_fd");
            if(descriptor!=null)try(java.io.InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(descriptor);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){
                byte[] buffer=new byte[16384];int count;while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);json=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
            }
            if(json==null)throw new IllegalStateException("Empty application plugin plan");return new JSONObject(json);
        }
        void scheduleReport(){if(stopped)return;main.removeCallbacks(report);main.post(report);}
        private void report(){
            if(stopped)return;
            JSONArray states=runtime.applicationStatus();
            if(!loggedRunning&&acceptedSource!=null){
                int running=0;
                for(int i=0;i<states.length();i++)if("running".equals(states.optJSONObject(i).optString("stage")))running++;
                if(running>0){
                    loggedRunning=true;
                    Log.i("C17-PluginBoot",packageName+" started plugins from "+acceptedSource+" (runtimes="+running+")");
                }
            }
            if(!observing)return;
            Bundle values=new Bundle();values.putString("plugins",states.toString());
            execute(()->{try{owner.getContentResolver().call(URI,CustomPluginAppProvider.REPORT,packageName,values);}catch(RuntimeException ignored){}});
        }
        private void execute(Runnable task){try{io.execute(task);}catch(RejectedExecutionException ignored){loading=false;}}
        void stop(){
            if(stopped)return;stopped=true;serial++;runtime.stop();main.removeCallbacks(reload);main.removeCallbacks(report);
            framework.close();main.removeCallbacks(frameworkChanged);
            if(observing)try{owner.getContentResolver().unregisterContentObserver(observer);}catch(RuntimeException ignored){}
            if(receiving)try{owner.unregisterReceiver(removal);}catch(RuntimeException ignored){}
            if(receivingBoot)try{owner.unregisterReceiver(boot);}catch(RuntimeException ignored){}
            io.shutdownNow();
        }
    }
}
