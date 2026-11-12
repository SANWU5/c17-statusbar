package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Font scopes add no inferred assignments and keep unrelated legacy choices across migration and import. */
public final class Upgrade186Check {
    private static int checks;private static final String A="a".repeat(64),B="b".repeat(64);
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade186 "+checks+": "+expected+" != "+actual);}
    private static String document(String selected){return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{\""+FontScopes.KEY+"\":\""+selected.replace("\\","\\\\").replace("\"","\\\"")+"\"}}";}
    private static Map<String,Object> legacy(){Map<String,Object> saved=new LinkedHashMap<>();saved.put(StatusBarSettings.FONT_MODE,"custom");saved.put(StatusBarSettings.FONT_REVISION,A);saved.put(StatusBarSettings.FONT_NAME,"原字体");saved.put(NotificationBigClockSettings.FONT,"native");saved.put(NativeNetworkBadgeSettings.key(1,"font"),"pingfang");saved.put("private_note","保留");return saved;}
    private static void migration(){
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();Upgrade186.migrate(store);equal(0,store.writes);
        store.values.putAll(legacy());Map<String,Object> before=new LinkedHashMap<>(store.values);Upgrade186.migrate(store);equal(1,store.writes);equal(FontScopes.DEFAULT,store.values.get(FontScopes.KEY));
        for(String key:before.keySet())equal(before.get(key),store.values.get(key));Upgrade186.migrate(store);equal(1,store.writes);
        for(Object saved:new Object[]{"{}","{\"statusbar_clock\":\"pingfang\"}",null,0}){
            store.values.put(FontScopes.KEY,saved);before=new LinkedHashMap<>(store.values);Upgrade186.migrate(store);equal(before,store.values);equal(1,store.writes);
        }
    }
    private static void imports()throws Exception{
        String assignments=FontScopes.merge("{}",Arrays.asList(FontScopes.STATUSBAR_CLOCK),"custom:"+A);
        assignments=FontScopes.merge(assignments,Arrays.asList(FontScopes.NETWORK_TYPE),"custom:"+B);
        ConfigTransfer.PreparedImport prepared=ConfigTransfer.prepare(document(assignments),true,hash->hash.equals(A));
        equal(1,prepared.count);equal(true,prepared.fontFallback);Map<String,String> resolved=FontScopes.parse((String)prepared.values().get(FontScopes.KEY));
        equal("custom:"+A,resolved.get(FontScopes.STATUSBAR_CLOCK));equal("system",resolved.get(FontScopes.NETWORK_TYPE));
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.putAll(legacy());Map<String,Object> before=new LinkedHashMap<>(store.values);
        equal(true,ConfigTransfer.commit(store,prepared));for(String key:before.keySet())equal(before.get(key),store.values.get(key));
        equal(false,ConfigTransfer.prepare(document(assignments),true,hash->true).fontFallback);
        equal(true,ConfigTransfer.prepare(document(assignments),true).fontFallback);
        String old="{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{\"font_mode\":\"pingfang\"}}";
        equal(false,ConfigTransfer.prepare(old,true).values().containsKey(FontScopes.KEY));
        for(String invalid:new String[]{"[]","{\"bad\":\"system\"}","{\"statusbar_clock\":\"custom\"}","{\"statusbar_clock\":\"system\",\"statusbar_clock\":\"pingfang\"}"}){
            checks++;try{ConfigTransfer.prepare(document(invalid),true);throw new AssertionError("Accepted malformed assignment");}catch(IOException expected){}
        }
    }
    public static void main(String[] args)throws Exception{
        equal(Collections.singletonMap(FontScopes.KEY,FontScopes.DEFAULT),Upgrade186.DEFAULTS);
        equal(FontScopes.DEFAULT,StatusBarSettings.STRING_DEFAULTS.get(FontScopes.KEY));equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(FontScopes.KEY));
        equal(52,SettingsFrameworkMirror.VERSION);equal(1,ConfigTransfer.SCHEMA_VERSION);
        Bundle defaults=SettingsSnapshot.fromPreferences(Collections.emptyMap());equal(1000, defaults.keySet().size());equal(987,ConfigTransfer.types().size());
        equal(FontScopes.DEFAULT,defaults.getString(FontScopes.KEY));migration();imports();
        System.out.println("Upgrade186Check passed: "+checks);
    }
}
