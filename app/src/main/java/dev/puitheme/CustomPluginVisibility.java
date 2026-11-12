// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Makes only the plugin host discoverable to its configured external runtimes on Android 11+.
 * The exact marker has no data. Provider call/file ownership checks remain authoritative. */
final class CustomPluginVisibility {
    static final Uri MARKER=Uri.parse("content://"+StatusBarSettings.AUTHORITY+"/custom-plugins/visibility");
    private static String owner;
    private static Set<String> current;
    private static Set<String> configuredTargets=Collections.emptySet();
    private static boolean observingPackages;
    private CustomPluginVisibility(){}
    static void start(Context context){
        // onCreate runs before ActivityThread publishes the provider to other processes.
        new Handler(Looper.getMainLooper()).post(()->start(context,()->{}));
    }
    static void start(Context context,Runnable complete){
        observePackages(context);
        Handler main=new Handler(Looper.getMainLooper());
        Thread worker=new Thread(()->{
            try{
                new CustomPluginRepository(context).reconcileVisibility(true);
                // Run after provider publication and after the scoped grants have been restored.
                if(main.post(()->{
                    try{context.getContentResolver().notifyChange(Uri.parse(StatusBarSettings.CONTENT_URI),null);}
                    catch(RuntimeException unavailable){/* Readers can still recover on their bounded retry. */}
                    finally{complete.run();}
                }))return;
            }catch(Exception unavailable){/* The next boot stage or library change retries visibility setup. */}
            complete.run();
        },"C17-plugin-visibility");
        worker.setDaemon(true);
        try{worker.start();}catch(RuntimeException unavailable){complete.run();}
    }
    private static synchronized void observePackages(Context context){
        if(observingPackages)return;
        Context application=context.getApplicationContext();
        Context receiverContext=application==null?context:application;
        BroadcastReceiver receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context received,Intent intent){
                if(intent==null||!Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())||intent.getData()==null)return;
                String changed=intent.getData().getSchemeSpecificPart();
                synchronized(CustomPluginVisibility.class){
                    if(!receiverContext.getPackageName().equals(changed)&&!configuredTargets.contains(changed))return;
                }
                start(receiverContext);
            }
        };
        IntentFilter filter=new IntentFilter(Intent.ACTION_PACKAGE_REPLACED);filter.addDataScheme("package");
        try{
            if(Build.VERSION.SDK_INT>=33)receiverContext.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
            else receiverContext.registerReceiver(receiver,filter);
            observingPackages=true;
        }catch(RuntimeException unavailable){/* Provider startup or the next boot event still restores visibility. */}
    }
    static synchronized void reconcile(Context context,JSONObject records){
        reconcile(context,records,false);
    }
    static synchronized void reconcile(Context context,JSONObject records,boolean force){
        if(context==null)return;
        try{
            Set<String> wanted=new HashSet<>();
            for(Iterator<String> ids=records.keys();ids.hasNext();){
                String id=ids.next();
                JSONObject record=records.getJSONObject(id),descriptor=record.getJSONObject("descriptor");
                if(!record.optBoolean("enabled")||descriptor.optInt("apiVersion")!=2)continue;
                JSONArray scopes=CustomPluginSupport.scopes(descriptor);
                for(int i=0;i<scopes.length();i++){
                    String target=scopes.optString(i);
                    if(CustomPluginSupport.appPackage(target)&&!ModuleRuntimeStatus.SYSTEM_UI.equals(target))wanted.add(target);
                }
            }
            Context storage=context.createDeviceProtectedStorageContext();
            String directory=storage.getFilesDir().getAbsolutePath();
            configuredTargets=new HashSet<>(wanted);
            if(!force&&directory.equals(owner)&&wanted.equals(current))return;
            SharedPreferences prefs=storage.getSharedPreferences("custom_plugin_visibility",Context.MODE_PRIVATE);
            Set<String> previous=new HashSet<>(prefs.getStringSet("packages",Collections.emptySet())),actual=new HashSet<>(previous);
            for(String target:previous)if(!wanted.contains(target)){
                try{context.revokeUriPermission(target,MARKER,Intent.FLAG_GRANT_READ_URI_PERMISSION);actual.remove(target);}
                catch(RuntimeException unavailable){/* Retry on the next change. */}
            }
            for(String target:wanted)try{
                context.grantUriPermission(target,MARKER,Intent.FLAG_GRANT_READ_URI_PERMISSION);actual.add(target);
            }catch(RuntimeException unavailable){actual.remove(target);}
            if(!actual.equals(previous)&&!prefs.edit().putStringSet("packages",actual).commit())return;
            owner=directory;current=actual.equals(wanted)?wanted:null;
        }catch(Exception unavailable){/* Never prevent a saved disable or safe-mode change. */}
    }
}
