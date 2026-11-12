// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;
import android.content.*;
import android.content.pm.*;
import android.os.Handler;
import android.os.Looper;
import io.github.libxposed.service.XposedService;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;
import org.json.*;

public final class CustomPluginScopeCheck {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final Executor WORK=task->{Looper.setCurrentForCheck(null);try{task.run();}finally{Looper.resetCurrentForCheck();}};
    private static final class Packages extends PackageManager {
        final Set<String> installed=new HashSet<>(Arrays.asList("com.android.systemui","com.android.launcher","com.coloros.smartsidebar"));
        String unavailable;
        public ApplicationInfo getApplicationInfo(String name,int flags)throws NameNotFoundException{
            check(Looper.myLooper()!=Looper.getMainLooper(),"package inspection is dispatched off UI thread");
            if(name.equals(unavailable))throw new SecurityException("package query denied");
            if(!installed.contains(name))throw new NameNotFoundException(name);return new ApplicationInfo();
        }
        public ActivityInfo getActivityInfo(ComponentName name,int flags)throws NameNotFoundException{throw new NameNotFoundException();}
        public int getComponentEnabledSetting(ComponentName name){return 0;}public void setComponentEnabledSetting(ComponentName name,int value,int flags){}
    }
    private static JSONObject plugin(String...names)throws Exception{return new JSONObject().put("scopes",new JSONArray(Arrays.asList(names)));}
    private static final List<CustomPluginScopeController.Snapshot> received=new ArrayList<>();
    private static CustomPluginScopeController.Request request(Context context,JSONObject descriptor){
        return CustomPluginScopeController.request(context,descriptor,value->{check(Looper.myLooper()==Looper.getMainLooper(),"UI updates arrive only on main");received.add(value);},WORK);
    }
    private static void state(CustomPluginScopeController.Request request,CustomPluginScopeController.State expected){Handler.drainForCheck();check(request.current().state==expected,"expected "+expected+" got "+request.current().state);}
    public static void main(String[] args)throws Exception{
        Packages packages=new Packages();Context context=new Context(){public PackageManager getPackageManager(){return packages;}};
        Field services=AppFrameworkStatus.class.getDeclaredField("SERVICES"),snapshot=AppFrameworkStatus.class.getDeclaredField("snapshot");services.setAccessible(true);snapshot.setAccessible(true);
        @SuppressWarnings("unchecked") Map<XposedService,Object> live=(Map<XposedService,Object>)services.get(null);Map<XposedService,Object> before=new LinkedHashMap<>(live);Object previous=snapshot.get(null);
        try{
            live.clear();CustomPluginScopeController.Request offline=request(context,plugin("com.android.launcher"));state(offline,CustomPluginScopeController.State.DISCONNECTED);
            Constructor<?> record=Class.forName("dev.puitheme.AppFrameworkStatus$Connection").getDeclaredConstructor();record.setAccessible(true);Object connection=record.newInstance();
            Constructor<?> create=AppFrameworkStatus.Snapshot.class.getDeclaredConstructor(AppFrameworkStatus.State.class,int.class,String.class,String.class,long.class,String.class);create.setAccessible(true);
            Object connected=create.newInstance(AppFrameworkStatus.State.CONNECTED,102,"LSPosed fixture","test",1L,"connected");Field result=connection.getClass().getDeclaredField("result");result.setAccessible(true);result.set(connection,connected);snapshot.set(null,connected);
            XposedService service=new XposedService(null);live.put(service,connection);service.scopes.addAll(Arrays.asList("com.android.systemui","com.example.existing"));
            CustomPluginScopeController.Request grant=request(context,plugin("com.android.systemui","com.android.launcher","com.example.missing"));state(grant,CustomPluginScopeController.State.PENDING);
            check(service.requestedScopes.equals(Collections.singletonList("com.android.launcher")),"request contains only installed declared missing package");
            check(grant.current().notInstalled.equals(Collections.singleton("com.example.missing")),"genuinely absent targets are reported separately");
            check(service.scopes.contains("com.example.existing"),"pre-existing unrelated scope is not removed");
            service.scopes.add("com.android.launcher");service.scopeCallback.onScopeRequestApproved(Collections.singletonList("com.android.launcher"));state(grant,CustomPluginScopeController.State.AUTHORIZED);
            check(grant.current().message.contains("重启")&&!grant.current().message.contains("已运行"),"approval does not claim process injection");
            int calls=service.scopeRequests;CustomPluginScopeController.Request ready=request(context,plugin("com.android.launcher"));state(ready,CustomPluginScopeController.State.AUTHORIZED);check(service.scopeRequests==calls,"already authorized targets do not show another prompt");
            service.scopes.clear();CustomPluginScopeController.Request denied=request(context,plugin("com.android.launcher"));service.scopeCallback.onScopeRequestFailed("User rejected");state(denied,CustomPluginScopeController.State.DENIED);
            CustomPluginScopeController.Request partial=request(context,plugin("com.android.launcher","com.coloros.smartsidebar"));service.scopes.add("com.android.launcher");service.scopeCallback.onScopeRequestApproved(Collections.singletonList("com.android.launcher"));state(partial,CustomPluginScopeController.State.PARTIAL);check(partial.current().missing.equals(Collections.singleton("com.coloros.smartsidebar")),"partial approval retains exact outstanding scope");
            service.scopes.clear();CustomPluginScopeController.Request verify=request(context,plugin("com.android.launcher"));service.scopeCallback.onScopeRequestApproved(Collections.singletonList("com.android.launcher"));state(verify,CustomPluginScopeController.State.DENIED);check(verify.current().granted.isEmpty(),"callback alone cannot invent selected framework scope");
            service.scopes.clear();CustomPluginScopeController.Request reordered=request(context,plugin("com.android.launcher"));
            Runnable latePending=Handler.postedForCheck.remove(Handler.postedForCheck.size()-1);Handler.drainForCheck();
            service.scopes.add("com.android.launcher");service.scopeCallback.onScopeRequestApproved(service.scopes);state(reordered,CustomPluginScopeController.State.AUTHORIZED);
            int deliveredBeforeStale=received.size();check(received.get(received.size()-1).state==CustomPluginScopeController.State.AUTHORIZED,"terminal scope status reaches the UI");
            latePending.run();check(received.size()==deliveredBeforeStale,"late pending callback cannot overwrite a completed request or leave scope controls busy");service.scopes.clear();
            packages.unavailable="com.android.launcher";CustomPluginScopeController.Request unavailable=request(context,plugin("com.android.launcher"));state(unavailable,CustomPluginScopeController.State.FAILED);check(unavailable.current().notInstalled.isEmpty(),"permission errors do not masquerade as missing apps");packages.unavailable=null;
            service.scopeUnavailable=true;CustomPluginScopeController.Request rpc=request(context,plugin("com.android.launcher"));state(rpc,CustomPluginScopeController.State.FAILED);service.scopeUnavailable=false;
            CustomPluginScopeController.Request retry=request(context,plugin("com.android.launcher"));state(retry,CustomPluginScopeController.State.PENDING);check(service.requestedScopes.equals(Collections.singletonList("com.android.launcher")),"explicit retry works after query failure");
            XposedService.OnScopeEventListener late=service.scopeCallback;live.clear();Method publish=AppFrameworkStatus.class.getDeclaredMethod("publish",AppFrameworkStatus.Snapshot.class);publish.setAccessible(true);Object disconnected=create.newInstance(AppFrameworkStatus.State.NOT_CONNECTED,0,"","",0L,"offline");publish.invoke(null,disconnected);state(retry,CustomPluginScopeController.State.DISCONNECTED);service.scopes.add("com.android.launcher");late.onScopeRequestApproved(service.scopes);state(retry,CustomPluginScopeController.State.DISCONNECTED);
            live.put(service,connection);snapshot.set(null,connected);service.scopes.clear();CustomPluginScopeController.Request closed=request(context,plugin("com.android.launcher"));Handler.drainForCheck();int delivered=received.size();closed.close();service.scopeCallback.onScopeRequestFailed("denied");Handler.drainForCheck();check(received.size()==delivered,"closed page receives no callback and retains no UI subscriber");
            CustomPluginScopeController.Request none=request(context,plugin("com.example.missing"));state(none,CustomPluginScopeController.State.NO_TARGETS);
            CustomPluginScopeController.Request invalid=request(context,new JSONObject().put("scopes","invalid"));state(invalid,CustomPluginScopeController.State.FAILED);
            check(CustomPluginScopeController.Snapshot.class.getField("missing").getType()==Set.class,"UI consumes immutable set contract");
        }finally{live.clear();live.putAll(before);snapshot.set(null,previous);}
        System.out.println("CustomPluginScopeCheck "+checks+" checks passed");
    }
}
