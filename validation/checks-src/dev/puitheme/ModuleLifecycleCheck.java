package dev.puitheme;
import android.content.Intent;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.util.Map;
public final class ModuleLifecycleCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args)throws Exception{
        String own=ModuleDiagnostics.PACKAGE_NAME;
        for(String action:new String[]{Intent.ACTION_PACKAGE_REMOVED,Intent.ACTION_PACKAGE_FULLY_REMOVED}){
            equal(true,ModuleLifecycle.isRemoval(action,"package",own,false));equal(false,ModuleLifecycle.isRemoval(action,"package",own,true));
            equal(false,ModuleLifecycle.isRemoval(action,"package","com.android.systemui",false));equal(false,ModuleLifecycle.isRemoval(action,"https",own,false));
        }
        equal(false,ModuleLifecycle.isRemoval(null,"package",own,false));equal(false,ModuleLifecycle.isRemoval(Intent.ACTION_PACKAGE_ADDED,"package",own,false));
        equal(false,ModuleLifecycle.isRemoval(Intent.ACTION_PACKAGE_REMOVED,"package",null,false));
        Bundle restored=ModuleLifecycle.nativeSettings();equal(true,SettingsSnapshot.complete(restored));equal(true,restored.get(StatusBarSettings.SAFE_MODE));
        Bundle enabled=new Bundle();enabled.putBoolean(ModuleDiagnostics.KEY_ENABLED,true);ModuleDiagnostics.configure(enabled);
        Field applied=SettingsSnapshot.class.getDeclaredField("APPLIED");applied.setAccessible(true);
        @SuppressWarnings("unchecked") Map<Object,Object> cache=(Map<Object,Object>)applied.get(null);cache.put(new Object(),new Bundle());
        final int[] releases={0};Field release=ModuleLifecycle.class.getDeclaredField("release");release.setAccessible(true);release.set(null,(Runnable)()->releases[0]++);
        ModuleLifecycle.markRemoved();ModuleLifecycle.markRemoved();equal(true,ModuleLifecycle.removed());equal(1,releases[0]);equal(true,cache.isEmpty());
        Field logging=ModuleDiagnostics.class.getDeclaredField("enabled");logging.setAccessible(true);equal(false,logging.get(null));
        ModuleDiagnostics.configure(enabled);equal(false,logging.get(null));enabled.putBoolean(NotificationIconArea.MASTER,true);equal(false,NotificationIconArea.active(enabled));
        System.out.println(checks+" checks passed (exact uninstall, upgrade exclusion, native restore and diagnostics release)");
    }
}
