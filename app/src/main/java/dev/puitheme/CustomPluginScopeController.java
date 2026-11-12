// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import io.github.libxposed.service.XposedService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit user action: ask the official framework to ADD only installed, declared, missing scopes. */
public final class CustomPluginScopeController {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Executor WORK = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "C17-plugin-scope"); thread.setDaemon(true); return thread;
    });
    public enum State { CHECKING, PENDING, AUTHORIZED, PARTIAL, DENIED, FAILED, DISCONNECTED, NO_TARGETS }
    public interface Callback { void onStatus(Snapshot status); }
    public static final class Snapshot {
        public final State state;
        public final Set<String> packages, granted, missing, notInstalled;
        public final String message;
        public final boolean finished;
        Snapshot(State state, Set<String> targets, Set<String> granted, Set<String> absent, String message) {
            this.state=state;packages=copy(targets);this.granted=copy(granted);notInstalled=copy(absent);
            Set<String> remaining=new LinkedHashSet<>(targets);remaining.removeAll(granted);missing=copy(remaining);
            this.message=message;finished=state!=State.CHECKING&&state!=State.PENDING;
        }
        private static Set<String> copy(Set<String> values){return Collections.unmodifiableSet(new LinkedHashSet<>(values));}
    }
    private CustomPluginScopeController() { }

    public static Request request(Context context, JSONObject descriptor, Callback callback) {
        return request(context,descriptor,callback,WORK);
    }
    static Request request(Context context, JSONObject descriptor, Callback callback, Executor work) {
        if(context==null||descriptor==null||callback==null)throw new IllegalArgumentException("Missing plugin scope request");
        Context application=context.getApplicationContext();
        Request result=new Request(application==null?context:application,callback,work);
        try {
            if(descriptor.has("scopes")&&descriptor.optJSONArray("scopes")==null)throw new IllegalArgumentException("Invalid scopes");
            JSONArray declared=CustomPluginSupport.scopes(descriptor);
            if(declared.length()>64)throw new IllegalArgumentException("插件声明的作用域过多");
            for(int i=0;i<declared.length();i++){
                Object value=declared.get(i);
                if(!(value instanceof String)||!CustomPluginSupport.appPackage((String)value))throw new IllegalArgumentException("插件声明了不支持的应用作用域");
                result.declared.add((String)value);
            }
        } catch(Exception invalid){result.finish(State.FAILED,"无法读取插件作用域，请检查插件声明");return result;}
        result.publish(State.CHECKING,"正在检查已安装应用和框架作用域");
        result.observer=AppFrameworkStatus.observe(status->{
            XposedService selected=result.service;
            if(selected!=null&&!AppFrameworkStatus.isScopeServiceLive(selected))result.finish(State.DISCONNECTED,"框架服务已断开，请连接后重试");
        });
        work.execute(result::start);return result;
    }

    /** Closing releases the UI callback; it cannot withdraw an already displayed framework prompt. */
    public static final class Request implements AutoCloseable {
        private final Context owner;private final Executor work;
        private Callback callback;private boolean closed,done;
        private final Set<String> declared=new LinkedHashSet<>(),targets=new LinkedHashSet<>(),granted=new LinkedHashSet<>(),absent=new LinkedHashSet<>();
        private volatile XposedService service;
        private AppFrameworkStatus.Subscription observer;
        private volatile Snapshot current;
        private Request(Context owner,Callback callback,Executor work){this.owner=owner;this.callback=callback;this.work=work;}
        public Snapshot current(){return current;}
        private synchronized boolean ended(){return closed||done;}
        private void start(){
            if(ended())return;
            try {
                for(String name:declared)try{owner.getPackageManager().getApplicationInfo(name,0);targets.add(name);}
                catch(PackageManager.NameNotFoundException missing){absent.add(name);}
                if(targets.isEmpty()){finish(State.NO_TARGETS,"插件声明的目标应用尚未安装，无需申请");return;}
                service=AppFrameworkStatus.currentScopeService();
                if(service==null){finish(State.DISCONNECTED,"框架服务未连接，请启用模块并连接框架后重试");return;}
                readGranted();
                Set<String> missing=new LinkedHashSet<>(targets);missing.removeAll(granted);
                if(missing.isEmpty()){finish(State.AUTHORIZED,"所需作用域已授权；重启对应应用后才会载入插件");return;}
                if(ended())return;
                publish(State.PENDING,"请在框架弹窗或通知中确认添加作用域");
                service.requestScope(new ArrayList<>(missing),new XposedService.OnScopeEventListener(){
                    @Override public void onScopeRequestApproved(List<String> approved){work.execute(()->complete(null));}
                    @Override public void onScopeRequestFailed(String message){work.execute(()->complete(message==null?"框架未提供原因":message));}
                });
            } catch(RuntimeException|LinkageError failure){finishUnavailable("作用域检查或申请失败，请稍后重试");}
        }
        private void readGranted(){
            if(!AppFrameworkStatus.isScopeServiceLive(service))throw new IllegalStateException("Framework disconnected");
            List<String> selected=service.getScope();if(selected==null)throw new IllegalStateException("Missing official scope result");
            Set<String> next=new LinkedHashSet<>();for(String name:selected){if(name==null||name.isEmpty())throw new IllegalStateException("Invalid official scope result");if(targets.contains(name))next.add(name);}
            if(!AppFrameworkStatus.isScopeServiceLive(service))throw new IllegalStateException("Framework disconnected");
            synchronized(this){granted.clear();granted.addAll(next);}
        }
        private void complete(String failure){
            if(ended())return;
            try {
                // Approval is not proof of injection. Read back current scope, including partial approvals.
                readGranted();
                if(granted.containsAll(targets))finish(State.AUTHORIZED,"作用域已授权；重启对应应用后才会载入插件");
                else if(!granted.isEmpty())finish(State.PARTIAL,"部分作用域未获批准，可再次申请；已授权应用需重启后载入");
                else finish(State.DENIED,failure==null?"框架未批准所需作用域，可再次申请":"框架未批准申请："+shortText(failure));
            } catch(RuntimeException|LinkageError unavailable){finishUnavailable("无法确认框架授权结果，请重试");}
        }
        private void finishUnavailable(String message){
            boolean disconnected=service!=null&&!AppFrameworkStatus.isScopeServiceLive(service);
            finish(disconnected?State.DISCONNECTED:State.FAILED,disconnected?"框架服务已断开，请连接后重试":message);
        }
        private void finish(State state,String message){publish(state,message);}
        private void publish(State state,String message){
            Snapshot value;AppFrameworkStatus.Subscription remove=null;
            synchronized(this){
                if(closed||done)return;
                value=new Snapshot(state,targets,granted,absent,message);current=value;
                if(value.finished){done=true;remove=observer;observer=null;}
            }
            if(remove!=null)remove.close();
            // A later state can be posted first by another thread after we release the lock.
            // Never let a delayed CHECKING/PENDING callback replace a terminal UI status.
            MAIN.post(()->{Callback receiver;synchronized(Request.this){receiver=closed||current!=value?null:callback;}if(receiver!=null)receiver.onStatus(value);});
        }
        @Override public void close(){
            AppFrameworkStatus.Subscription remove;
            synchronized(this){closed=true;callback=null;remove=observer;observer=null;}
            if(remove!=null)remove.close();
        }
        private static String shortText(String value){return value.length()>300?value.substring(0,300):value;}
    }
}
