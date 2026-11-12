// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import org.json.JSONObject;

/** Internal, version-bound native View contract; plugin business UI stays in its own DEX. */
public final class CustomPluginNativeSettingsActivity extends Activity {
    public static final String EXTRA_ID="plugin_id",EXTRA_VERSION="plugin_version";
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final List<Runnable> cleanups=new ArrayList<>();
    private volatile boolean closed;
    private volatile boolean paused;
    private String id,stateJson="{}";
    private int version;
    private CustomPluginRepository repository;
    private android.content.SharedPreferences settings;
    private CustomPluginRepository.NativeSettingsSession session;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener safety=(preferences,key)->{
        if(StatusBarSettings.SAFE_MODE.equals(key)&&preferences.getBoolean(key,false)){paused=true;if(session!=null)session.close();finish();}
    };
    private volatile File nativeCode;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);repository=new CustomPluginRepository(this);
        id=getIntent().getStringExtra(EXTRA_ID);version=getIntent().getIntExtra(EXTRA_VERSION,-1);
        settings=StatusBarSettings.preferences(this);settings.registerOnSharedPreferenceChangeListener(safety);
        if(settings.getBoolean(StatusBarSettings.SAFE_MODE,false)){paused=true;show("安全模式已暂停原生插件设置代码，可返回详情编辑普通配置。");return;}
        show("正在打开插件设置…");
        work.execute(()->{
            File code=null;
            try{
                session=repository.nativeSettingsSession(id,version);session.onInvalidated(()->runOnUiThread(()->{if(!closed)finish();}));
                CustomPluginRepository.Plugin plugin=current();CustomPluginSupport.requireCompatible(plugin.descriptor);
                File admitted=repository.nativeSettingsFile(id,version);
                File folder=new File(createDeviceProtectedStorageContext().getCodeCacheDir(),"plugin-settings/"+id+"/"+version);
                if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("无法准备插件设置代码");
                code=File.createTempFile("settings-",admitted.getName().endsWith(".jar")?".jar":".dex",folder);nativeCode=code;
                try(FileInputStream input=new FileInputStream(admitted);FileOutputStream output=new FileOutputStream(code)){
                    // Android 14 requires dynamically loaded code to be read-only.
                    if(!code.setReadOnly())throw new java.io.IOException("无法保护插件设置代码");
                    byte[] buffer=new byte[16384];int read;while((read=input.read(buffer))!=-1)if(read!=0)output.write(buffer,0,read);output.getFD().sync();
                }
                DexClassLoader loader=new DexClassLoader(code.getPath(),folder.getPath(),null,getClassLoader());
                Method create=CustomPluginNativeSettings.resolve(loader,plugin.descriptor.getJSONObject("nativeSettings").getString("className"));
                runOnUiThread(()->{
                    if(closed||paused||!session.isCurrent()||settings.getBoolean(StatusBarSettings.SAFE_MODE,false)){cleanupCode();return;}
                    try{stateJson=plugin.state.toString();setContentView(CustomPluginNativeSettings.create(create,this,stateJson,this::invoke));
                    }catch(Throwable failure){cleanupCode();show(reason(failure));}
                });
            }catch(Throwable failure){cleanupCode();if(code!=null)code.delete();runOnUiThread(()->{if(!closed)show(reason(failure));});}
            finally{if(closed||paused){if(session!=null)session.close();cleanupCode();if(code!=null)code.delete();}}
        });
    }
    private CustomPluginRepository.Plugin current()throws Exception{
        if(paused||session==null||!session.isCurrent())throw new IllegalStateException("插件设置界面已失效，请重新打开");
        for(CustomPluginRepository.Plugin plugin:repository.list())if(plugin.id.equals(id)&&plugin.versionCode==version)return plugin;
        throw new IllegalStateException("插件已更新或删除，请返回详情重新打开");
    }
    @SuppressWarnings("unchecked") private Object invoke(Object proxy,Method method,Object[] arguments)throws Exception{
        if(closed||paused||session==null||!session.isCurrent()||settings.getBoolean(StatusBarSettings.SAFE_MODE,false))throw new IllegalStateException("插件设置界面已关闭、暂停或失效");
        if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("请在主线程调用设置桥接");
        if(arguments==null||arguments.length==0||!(arguments[0] instanceof String))throw new IllegalArgumentException("缺少插件设置操作");
        switch((String)arguments[0]){
            case "state":return stateJson;
            case "host":return CustomPluginSupport.describe(getPackageName()).toString();
            case "close":finish();return null;
            case "onCleanup":if(arguments.length!=2||!(arguments[1] instanceof Runnable))throw new IllegalArgumentException("清理操作需要 Runnable");cleanups.add((Runnable)arguments[1]);return null;
            case "set":{
                if(arguments.length!=4||!(arguments[1] instanceof String)||!(arguments[3] instanceof BiConsumer))throw new IllegalArgumentException("保存需要 key、value 与 BiConsumer 回调");
                String key=(String)arguments[1];Object value=arguments[2];BiConsumer<String,String> callback=(BiConsumer<String,String>)arguments[3];
                work.execute(()->{String next=null,error=null;try{if(settings.getBoolean(StatusBarSettings.SAFE_MODE,false))throw new IllegalStateException("安全模式已暂停原生设置");repository.updateStateSession(session,key,value);next=current().state.toString();}catch(Exception failure){error=reason(failure);}
                    String result=next,problem=error;runOnUiThread(()->{if(closed||!session.isCurrent()||settings.getBoolean(StatusBarSettings.SAFE_MODE,false))return;if(problem==null)stateJson=result;callback.accept(result,problem);});});return null;
            }
            default:throw new IllegalArgumentException("未知插件设置操作："+arguments[0]);
        }
    }
    private void show(String message){TextView text=new TextView(this);text.setPadding(40,64,40,40);text.setText(message);setContentView(text);}
    private void cleanupCode(){File file=nativeCode;nativeCode=null;if(file!=null)file.delete();}
    private static String reason(Throwable error){while(error instanceof java.lang.reflect.InvocationTargetException&&error.getCause()!=null)error=error.getCause();String text=error.getMessage();return text==null?"插件设置未能打开":text.substring(0,Math.min(text.length(),1024));}
    @Override public void onDestroy(){closed=true;if(settings!=null)settings.unregisterOnSharedPreferenceChangeListener(safety);if(session!=null)session.close();work.shutdownNow();cleanupCode();for(int index=cleanups.size()-1;index>=0;index--)try{cleanups.get(index).run();}catch(Throwable ignored){}cleanups.clear();super.onDestroy();}
}
