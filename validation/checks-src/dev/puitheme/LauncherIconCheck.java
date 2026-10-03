package dev.puitheme;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Own-component state and the independent manager entry; never executes a device operation. */
public final class LauncherIconCheck {
    private static final String PACKAGE="dev.puitheme.iosstatusbar",ACTIVITY="dev.puitheme.ModernMainActivity";
    private static final String ANDROID="http://schemas.android.com/apk/res/android";
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Launcher "+checks+": "+expected+" != "+actual);}
    private static final class Manager extends PackageManager {
        final Map<String,ActivityInfo> activities=new HashMap<>();
        final Map<String,Integer> states=new HashMap<>();
        int mutations,reads,settingsReads,lastFlags,lastState;ComponentName lastComponent;
        boolean failRead,failWrite,ignoreWrite;
        Manager(){activity(LauncherIcon.LAUNCHER_ALIAS);activity(LauncherIcon.SETTINGS_ALIAS);}
        ActivityInfo activity(String name){ActivityInfo info=new ActivityInfo();info.name=name;info.packageName=PACKAGE;info.targetActivity=ACTIVITY;activities.put(name,info);return info;}
        private void own(ComponentName component){if(!PACKAGE.equals(component.getPackageName())||(!LauncherIcon.LAUNCHER_ALIAS.equals(component.getClassName())&&!LauncherIcon.SETTINGS_ALIAS.equals(component.getClassName())))throw new AssertionError("Foreign component");}
        @Override public ActivityInfo getActivityInfo(ComponentName component,int flags)throws NameNotFoundException {
            own(component);reads++;if(flags!=MATCH_DISABLED_COMPONENTS)throw new AssertionError("Disabled alias must remain queryable");
            if(LauncherIcon.SETTINGS_ALIAS.equals(component.getClassName()))settingsReads++;
            ActivityInfo info=activities.get(component.getClassName());if(failRead||info==null)throw new NameNotFoundException(component.getClassName());return info;
        }
        @Override public int getComponentEnabledSetting(ComponentName component){own(component);return states.getOrDefault(component.getClassName(),COMPONENT_ENABLED_STATE_DEFAULT);}
        @Override public void setComponentEnabledSetting(ComponentName component,int state,int flags){
            own(component);if(!LauncherIcon.LAUNCHER_ALIAS.equals(component.getClassName()))throw new AssertionError("Settings entry must never be toggled");
            mutations++;lastComponent=component;lastFlags=flags;lastState=state;if(failWrite)throw new SecurityException("Rejected fixture operation");if(!ignoreWrite)states.put(component.getClassName(),state);
        }
    }
    private static final class Own extends Context {
        final Manager manager=new Manager();String packageName=PACKAGE;int managerRequests;
        @Override public String getPackageName(){return packageName;}
        @Override public PackageManager getPackageManager(){managerRequests++;return manager;}
    }
    private static void manifest()throws Exception {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        Element manifest=factory.newDocumentBuilder().parse(new File("app/src/main/AndroidManifest.xml")).getDocumentElement();
        equal(PACKAGE,manifest.getAttribute("package"));NodeList aliases=manifest.getElementsByTagName("activity-alias");equal(2,aliases.getLength());
        int launcherCount=0,settingsCount=0;
        for(int i=0;i<aliases.getLength();i++) {
            Element alias=(Element)aliases.item(i);String name=alias.getAttributeNS(ANDROID,"name");
            equal(ACTIVITY,alias.getAttributeNS(ANDROID,"targetActivity"));equal("true",alias.getAttributeNS(ANDROID,"enabled"));equal("true",alias.getAttributeNS(ANDROID,"exported"));
            NodeList categories=alias.getElementsByTagName("category");boolean launcher=false,settings=false,info=false;
            for(int j=0;j<categories.getLength();j++){String value=((Element)categories.item(j)).getAttributeNS(ANDROID,"name");launcher|="android.intent.category.LAUNCHER".equals(value);settings|=LauncherIcon.SETTINGS_CATEGORY.equals(value);info|="android.intent.category.INFO".equals(value);}
            NodeList actions=alias.getElementsByTagName("action");equal(1,actions.getLength());equal("android.intent.action.MAIN",((Element)actions.item(0)).getAttributeNS(ANDROID,"name"));
            if(LauncherIcon.LAUNCHER_ALIAS.equals(name)){equal(true,launcher);equal(false,settings);launcherCount++;}
            else if(LauncherIcon.SETTINGS_ALIAS.equals(name)){equal(false,launcher);equal(true,settings);equal(true,info);settingsCount++;}
            else throw new AssertionError("Unexpected alias "+name);
        }
        equal(1,launcherCount);equal(1,settingsCount);
    }
    public static void main(String[] args)throws Exception {
        manifest();Own context=new Own();LauncherIcon.Result result=LauncherIcon.read(context);
        equal(true,result.success);equal(false,result.hidden);equal(false,LauncherIcon.isHidden(context));
        equal(true,LauncherIcon.setHidden(context,false).success);equal(0,context.manager.mutations);equal(0,context.manager.settingsReads);
        result=LauncherIcon.setHidden(context,true);equal(true,result.success);equal(true,result.hidden);equal(true,LauncherIcon.isHidden(context));
        equal(1,context.manager.mutations);equal(PackageManager.DONT_KILL_APP,context.manager.lastFlags);equal(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,context.manager.lastState);
        equal(PACKAGE,context.manager.lastComponent.getPackageName());equal(LauncherIcon.LAUNCHER_ALIAS,context.manager.lastComponent.getClassName());
        equal(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,context.manager.states.getOrDefault(LauncherIcon.SETTINGS_ALIAS,0));
        equal(true,LauncherIcon.setHidden(context,true).success);equal(1,context.manager.mutations);
        result=LauncherIcon.setHidden(context,false);equal(true,result.success);equal(false,result.hidden);equal(2,context.manager.mutations);equal(PackageManager.COMPONENT_ENABLED_STATE_ENABLED,context.manager.lastState);
        for(int state:new int[]{PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED}){context.manager.states.put(LauncherIcon.LAUNCHER_ALIAS,state);equal(true,LauncherIcon.isHidden(context));}
        context.manager.states.clear();context.manager.activities.get(LauncherIcon.LAUNCHER_ALIAS).enabled=false;equal(true,LauncherIcon.isHidden(context));
        context.manager.states.put(LauncherIcon.LAUNCHER_ALIAS,PackageManager.COMPONENT_ENABLED_STATE_ENABLED);equal(false,LauncherIcon.isHidden(context));
        for(int invalid=0;invalid<4;invalid++) {
            Own blocked=new Own();ActivityInfo settings=blocked.manager.activities.get(LauncherIcon.SETTINGS_ALIAS);
            if(invalid==0)settings.exported=false;else if(invalid==1)settings.targetActivity="other.Activity";else if(invalid==2)blocked.manager.states.put(LauncherIcon.SETTINGS_ALIAS,PackageManager.COMPONENT_ENABLED_STATE_DISABLED);else blocked.manager.activities.remove(LauncherIcon.SETTINGS_ALIAS);
            result=LauncherIcon.setHidden(blocked,true);equal(false,result.success);equal(false,result.hidden);equal(0,blocked.manager.mutations);
        }
        Own recovery=new Own();recovery.manager.states.put(LauncherIcon.LAUNCHER_ALIAS,PackageManager.COMPONENT_ENABLED_STATE_DISABLED);recovery.manager.activities.remove(LauncherIcon.SETTINGS_ALIAS);
        equal(true,LauncherIcon.setHidden(recovery,false).success);equal(false,LauncherIcon.isHidden(recovery));
        Own foreign=new Own();foreign.packageName="other.package";equal(false,LauncherIcon.read(foreign).success);equal(false,LauncherIcon.setHidden(foreign,true).success);equal(0,foreign.managerRequests);
        equal(false,LauncherIcon.read(null).success);
        Own failed=new Own();failed.manager.failRead=true;equal(false,LauncherIcon.read(failed).success);equal(false,LauncherIcon.setHidden(failed,true).success);equal(0,failed.manager.mutations);
        Own wrong=new Own();wrong.manager.activities.get(LauncherIcon.LAUNCHER_ALIAS).targetActivity="other.Activity";equal(false,LauncherIcon.read(wrong).success);equal(false,LauncherIcon.setHidden(wrong,true).success);equal(0,wrong.manager.mutations);
        Own noOp=new Own();noOp.manager.ignoreWrite=true;result=LauncherIcon.setHidden(noOp,true);equal(false,result.success);equal(false,result.hidden);
        Own rejected=new Own();rejected.manager.failWrite=true;result=LauncherIcon.setHidden(rejected,true);equal(false,result.success);equal(false,result.hidden);equal(false,result.message.isEmpty());
        Own unknown=new Own();unknown.manager.states.put(LauncherIcon.LAUNCHER_ALIAS,99);equal(false,LauncherIcon.read(unknown).success);equal(0,unknown.manager.mutations);
        System.out.println("LauncherIconCheck passed "+checks+" checks");
    }
}
