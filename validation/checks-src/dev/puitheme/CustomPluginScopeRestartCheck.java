// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;
import android.content.*;
import android.content.pm.*;
import java.io.IOException;
import java.util.*;
import org.json.*;

public final class CustomPluginScopeRestartCheck {
    private static int checks;
    private static void check(boolean ok, String text) { checks++; if (!ok) throw new AssertionError(text); }
    private static final class Packages extends PackageManager {
        final Map<String, ApplicationInfo> installed = new HashMap<>();
        public ApplicationInfo getApplicationInfo(String name, int flags) throws NameNotFoundException {
            ApplicationInfo info = installed.get(name); if (info == null) throw new NameNotFoundException(name); return info;
        }
        public ActivityInfo getActivityInfo(ComponentName name, int flags) throws NameNotFoundException { throw new NameNotFoundException(); }
        public int getComponentEnabledSetting(ComponentName name) { return 0; }
        public void setComponentEnabledSetting(ComponentName name, int value, int flags) { }
        void add(String name, int uid, String process) { ApplicationInfo info = new ApplicationInfo(); info.uid=uid; info.processName=process; installed.put(name,info); }
    }
    private static JSONObject plugin(String... scopes) throws Exception { return new JSONObject().put("scopes",new JSONArray(Arrays.asList(scopes))); }
    public static void main(String[] args) throws Exception {
        Packages packages = new Packages();
        packages.add("com.android.systemui",1000,"com.android.systemui");
        packages.add("com.android.launcher",10001,"com.android.launcher");
        packages.add("com.example.app",110001,"com.example.app:custom");
        Context context = new Context() {
            public PackageManager getPackageManager() { return packages; }
            public String getPackageName() { return ModuleDiagnostics.PACKAGE_NAME; }
        };
        Set<String> granted = new LinkedHashSet<>(Arrays.asList("com.android.systemui","com.example.app","com.missing.app","com.elsewhere.app"));
        CustomPluginScopeRestart.Plan plan = CustomPluginScopeRestart.plan(context, plugin("com.android.systemui","com.android.systemui","com.android.launcher","com.missing.app","com.example.app"), granted);
        check(plan.targets.size()==2 && plan.skipped==2,"only declared, installed, freshly granted targets; deduplicate");
        check(plan.targets.get(1).uid==110001,"preserve full per-user uid");
        String command=CustomPluginScopeRestart.command(plan.targets);
        check(command.contains("find_scope 110001 'com.example.app' 'com.example.app:custom'"),"exact uid/process binding");
        check(command.contains("\"$2\":*") && command.contains("[ \"$owner\" = \"$1\" ]"),"colon-only children and uid check");
        check(!command.contains("force-stop") && !command.contains("killall") && !command.contains("reboot"),"no stopped flag or broad kill");
        check(command.indexOf("old1=")<command.indexOf("kill -TERM"),"snapshot dependencies before first signal");
        for(String bad:new String[]{"android","system_server","com.app;id","com.app'","com.app\nfoo",ModuleDiagnostics.PACKAGE_NAME}) {
            boolean rejected=false;try{CustomPluginScopeRestart.plan(context,plugin(bad),granted);}catch(IOException|IllegalArgumentException expected){rejected=true;}
            check(rejected,"reject unsafe target "+bad);
        }
        for(String bad:new String[]{"system_server","zygote","com.app;id",ModuleDiagnostics.PACKAGE_NAME,ModuleDiagnostics.PACKAGE_NAME+":remote"})
            check(new CustomPluginScopeRestart.Target("com.example.app",bad,10001).process.equals("com.example.app"),"unsafe process cannot broaden target");
        check(CustomPluginScopeRestart.plan(context,plugin("com.android.systemui"),Collections.emptySet()).targets.isEmpty(),"missing grant never uses cached desired scope");
        String result=CustomPluginScopeRestart.describe(0,"C17_SCOPE:com.android.systemui:RESTARTED\nC17_SCOPE:com.example.app:ENDED\nC17_SCOPE_DONE",plan.targets,2);
        check(result.contains("已重启 1")&&result.contains("下次打开")&&result.contains("跳过 2"),"truthful partial result");
        check(CustomPluginScopeRestart.describe(0,"C17_SCOPE_DONE",plan.targets,0).contains("2 个未确认"),"missing output never success");
        check(CustomPluginScopeRestart.describe(0,"C17_SCOPE:com.android.systemui:IDLE\nC17_SCOPE:com.example.app:FAILED\nC17_SCOPE_DONE",plan.targets,0).contains("1 个未运行"),"idle is not restarted");
        boolean failed=false;try{CustomPluginScopeRestart.describe(21,"C17_ROOT_REQUIRED",plan.targets,0);}catch(IOException expected){failed=true;}
        check(failed,"root denied fails");
        System.out.println("CustomPluginScopeRestartCheck: "+checks+" checks passed");
    }
}
