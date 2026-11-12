// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;
import org.json.*;

public final class CustomPluginVisibilityCheck {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static final class Storage {
        Set<String> values=new HashSet<>();int commits;
        SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)->{
            if(m.getName().equals("getStringSet"))return new HashSet<>(values);
            if(m.getName().equals("edit"))return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},new java.lang.reflect.InvocationHandler(){
                Set<String> next=new HashSet<>(values);
                public Object invoke(Object p,java.lang.reflect.Method m,Object[] a){
                    if(m.getName().equals("putStringSet")){next=new HashSet<>((Set<String>)a[1]);return p;}
                    if(m.getName().equals("commit")){values=next;commits++;return true;}
                    throw new AssertionError("unexpected editor call "+m.getName());
                }
            });
            throw new AssertionError("unexpected preference call "+m.getName());
        });
    }
    private static final class Host extends Context {
        final File root;final Storage storage;final Set<String> grants=new HashSet<>();
        final List<String> events=new ArrayList<>();String fail;
        android.content.BroadcastReceiver receiver;android.content.IntentFilter filter;int registrations,receiverFlags;
        Host(Storage storage)throws Exception{this.storage=storage;root=Files.createTempDirectory("c17-visibility-").toFile();}
        public File getFilesDir(){return root;}
        public SharedPreferences getSharedPreferences(String name,int mode){check(name.equals("custom_plugin_visibility"),"visibility state has separate storage");return storage.prefs;}
        private void marker(Uri uri,int flags){
            check(uri.toString().equals("content://dev.puitheme.iosstatusbar.settings/custom-plugins/visibility"),"grant exact empty marker only");
            check(flags==Intent.FLAG_GRANT_READ_URI_PERMISSION,"no write, prefix, or persistable grant");
        }
        public void grantUriPermission(String target,Uri uri,int flags){marker(uri,flags);events.add("+"+target);if(target.equals(fail))throw new SecurityException("temporary failure");grants.add(target);}
        public void revokeUriPermission(String target,Uri uri,int flags){marker(uri,flags);events.add("-"+target);grants.remove(target);}
        public Intent registerReceiver(android.content.BroadcastReceiver receiver,android.content.IntentFilter filter,int flags){
            this.receiver=receiver;this.filter=filter;registrations++;receiverFlags=flags;return null;
        }
    }
    private static JSONObject record(boolean enabled,int api,String... scopes)throws Exception{
        return new JSONObject().put("enabled",enabled).put("descriptor",new JSONObject().put("apiVersion",api).put("scopes",new JSONArray(Arrays.asList(scopes))));
    }
    public static void main(String[] args)throws Exception{
        Storage storage=new Storage();Host host=new Host(storage);
        JSONObject records=new JSONObject().put("glass",record(true,2,"com.android.systemui","com.oplus.blur","com.android.launcher","com.oplus.blur","android","dev.puitheme.iosstatusbar"))
            .put("disabled",record(false,2,"com.coloros.assistantscreen")).put("legacy",record(true,1,"com.example.legacy"));
        CustomPluginVisibility.reconcile(host,records);
        check(host.grants.equals(new HashSet<>(Arrays.asList("com.oplus.blur","com.android.launcher"))),"only enabled API 2 external packages become visible");
        int events=host.events.size(),commits=storage.commits;
        for(int i=0;i<100;i++)CustomPluginVisibility.reconcile(host,records);
        check(host.events.size()==events&&storage.commits==commits,"ordinary value edits cause no repeated permission or preference writes");
        host.grants.clear();
        CustomPluginVisibility.reconcile(host,records,true);
        check(host.grants.equals(new HashSet<>(Arrays.asList("com.oplus.blur","com.android.launcher"))),"provider readiness repairs invalidated platform grants in the same host process");
        check(storage.commits==commits,"repairing platform visibility preserves the saved scope set");
        java.lang.reflect.Method observe=CustomPluginVisibility.class.getDeclaredMethod("observePackages",Context.class);observe.setAccessible(true);
        observe.invoke(null,host);observe.invoke(null,host);
        check(host.registrations==1,"host process registers package readiness only once");
        check(host.receiverFlags==Context.RECEIVER_NOT_EXPORTED&&host.filter.actions.equals(Collections.singleton(Intent.ACTION_PACKAGE_REPLACED))
                &&host.filter.schemes.equals(Collections.singleton("package")),"only protected package replacement is observed");
        android.os.Handler.postedForCheck.clear();
        host.receiver.onReceive(host,new Intent(Intent.ACTION_PACKAGE_REPLACED,Uri.parse("package:com.example.unrelated")));
        host.receiver.onReceive(host,new Intent(Intent.ACTION_PACKAGE_REPLACED,Uri.parse("package:com.coloros.assistantscreen")));
        host.receiver.onReceive(host,new Intent(Intent.ACTION_PACKAGE_ADDED,Uri.parse("package:com.oplus.blur")));
        check(android.os.Handler.postedForCheck.isEmpty(),"unrelated, disabled and unrecognized package events do not regrant visibility");
        host.receiver.onReceive(host,new Intent(Intent.ACTION_PACKAGE_REPLACED,Uri.parse("package:com.oplus.blur")));
        check(android.os.Handler.postedForCheck.size()==1,"enabled scope replacement schedules fresh readiness reconciliation");
        android.os.Handler.postedForCheck.clear();
        host.receiver.onReceive(host,new Intent(Intent.ACTION_PACKAGE_REPLACED,Uri.parse("package:dev.puitheme.iosstatusbar")));
        check(android.os.Handler.postedForCheck.size()==1,"host replacement repairs visibility without opening the settings UI");
        android.os.Handler.postedForCheck.clear();
        Host restarted=new Host(storage);CustomPluginVisibility.reconcile(restarted,records);
        check(restarted.grants.equals(host.grants),"process startup restores non-persistable grants from enabled library records");
        records.put("second",record(true,2,"com.oplus.blur"));records.getJSONObject("glass").put("enabled",false);
        CustomPluginVisibility.reconcile(restarted,records);
        check(restarted.grants.equals(Collections.singleton("com.oplus.blur")),"disabling one plugin retains scopes used by another");
        records.getJSONObject("second").put("enabled",false);CustomPluginVisibility.reconcile(restarted,records);
        check(restarted.grants.isEmpty()&&storage.values.isEmpty(),"disable-all revokes all marker grants");
        restarted.fail="com.oplus.blur";records.getJSONObject("second").put("enabled",true);CustomPluginVisibility.reconcile(restarted,records);
        check(restarted.grants.isEmpty()&&!storage.values.contains("com.oplus.blur"),"failed grants are not cached as successful");
        restarted.fail=null;CustomPluginVisibility.reconcile(restarted,records);
        check(restarted.grants.contains("com.oplus.blur"),"same desired state retries a failed grant");
        records.remove("second");CustomPluginVisibility.reconcile(restarted,records);
        check(restarted.grants.isEmpty(),"removal revokes the final scope");
        System.out.println("CustomPluginVisibilityCheck: "+checks+" checks passed");
    }
}
