package dev.puitheme;
import android.os.Bundle;
import java.util.LinkedHashMap;
import java.util.Map;
/** Independent opt-in preserves legacy active media fill and rejects partial IPC. */
public final class Upgrade85Check {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        Bundle defaults=SettingsSnapshot.fromPreferences(new LinkedHashMap<>());
        check(SettingsSnapshot.complete(defaults)&&!defaults.getBoolean(FluidCloudStyle.FOLLOW_MEDIA,false),"fresh complete snapshot defaults OFF");
        Map<String,Object> old=new LinkedHashMap<>();for(String key:defaults.keySet())old.put(key,defaults.get(key));
        for(String key:Upgrade90.DEFAULTS.keySet())old.remove(key);
        for(String key:Upgrade91.DEFAULTS.keySet())old.remove(key);
        for(String key:Upgrade96.DEFAULTS.keySet())old.remove(key);
        for(String key:Upgrade98.DEFAULTS.keySet())old.remove(key);
        for(String key:Upgrade99.DEFAULTS.keySet())old.remove(key);
        for(String key:Upgrade100.DEFAULTS.keySet())old.remove(key);
        for(String key:Upgrade101.DEFAULTS.keySet())old.remove(key);
        for(String key:Upgrade93.DEFAULTS.keySet())old.remove(key);
        old.remove(FluidCloudStyle.FOLLOW_MEDIA);old.put(SettingsFrameworkMirror.SCHEMA,11);
        for(String key:Upgrade103.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())old.remove(key);old.put(SettingsFrameworkMirror.REVISION,8501L);
        old.put(FluidCloudStyle.MASTER,true);old.put(FluidCloudStyle.DESKTOP_BACKGROUND,true);old.put(FluidCloudStyle.BACKGROUND_MODE,"media");
        old.put(FluidCloudAccent.MASK_OPACITY,45f);
        Bundle upgraded=SettingsFrameworkMirror.decode(old);
        check(upgraded!=null&&upgraded.getBoolean(FluidCloudStyle.FOLLOW_MEDIA,false),"active historical media source migrates to independent switch");
        check(((Float)upgraded.get(FluidCloudAccent.MASK_OPACITY))==45f,"mask settings retained");
        old.put(FluidCloudStyle.MASTER,false);
        check(!SettingsFrameworkMirror.decode(old).getBoolean(FluidCloudStyle.FOLLOW_MEDIA,false),"old disabled page does not enable new policy");
        old.put(SettingsFrameworkMirror.SCHEMA,12);
        for(String key:Upgrade103.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())old.remove(key);
        check(SettingsFrameworkMirror.decode(old)==null,"schema12 cannot silently default missing field");
        old.put(FluidCloudStyle.FOLLOW_MEDIA,true);
        check(SettingsFrameworkMirror.decode(old).getBoolean(FluidCloudStyle.FOLLOW_MEDIA,false),"independent enabled switch survives page OFF");
        old.put(FluidCloudAccent.ENABLED,true);
        check(!FeatureOptions.from(old).effective("fluid_cloud_accent",FluidCloudStyle.FOLLOW_MEDIA),"code100: without the style page's custom base colour following is off");
        old.put(FluidCloudStyle.MASTER,true);
        check(FeatureOptions.from(old).effective("fluid_cloud_accent",FluidCloudStyle.FOLLOW_MEDIA),"following reports its effective state once the page master and the custom base colour are on");
        Map<String,Object> portable=ConfigTransfer.prepare(ConfigTransfer.exportJson(old),true).values();
        check(Boolean.TRUE.equals(portable.get(FluidCloudStyle.FOLLOW_MEDIA)),"portable roundtrip retains independent opt-in");
        check("native".equals(portable.get(FluidCloudStyle.BACKGROUND_MODE)),"retired media choice is replaced by independent switch");
        old.put(FluidCloudStyle.FOLLOW_MEDIA,false);
        check(!StatusBarSettings.bool(old,FluidCloudStyle.FOLLOW_MEDIA),"explicit new OFF is authoritative over old mode");
        check("fluid_cloud_accent".equals(SettingsCatalog.item(FluidCloudStyle.FOLLOW_MEDIA).groupId),"following is an entry on the media-accent page");
        System.out.println("Upgrade85Check: "+count+" checks passed");
    }
}
