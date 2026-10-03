package dev.puitheme;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Real editor delegation counters verify revocation at submission time, without Android storage. */
public final class ActivationGuardPreferencesCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Preference guard check " + checks + ": " + actual);
    }
    public static void main(String[] args) {
        MemoryPreferences raw = new MemoryPreferences();
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicInteger denied = new AtomicInteger();
        SharedPreferences guarded = new ActivationGuardPreferences(raw, active::get, denied::incrementAndGet);
        SharedPreferences.Editor delayed = guarded.edit().putInt("offset", 7);
        active.set(false);
        delayed.apply();
        equal(0, raw.writes); equal(false, raw.contains("offset")); equal(1, denied.get());
        equal(false, guarded.edit().putBoolean("enabled", true).commit());
        equal(0, raw.writes); equal(2, denied.get());
        active.set(true);
        delayed.apply();
        equal(0, raw.writes); equal(3, denied.get());
        equal(true, guarded.edit().putInt("offset", 9).putString("name", "clock").commit());
        equal(1, raw.writes); equal(9, guarded.getInt("offset", 0)); equal("clock", guarded.getString("name", ""));
        guarded.edit().putFloat("size", 110.5f).apply();
        equal(2, raw.writes); equal(110.5f, guarded.getFloat("size", 0));
        SharedPreferences.Editor deletion = guarded.edit().remove("offset").clear();
        active.set(false);
        deletion.apply();
        equal(2, raw.writes); equal(true, guarded.contains("offset")); equal(4, denied.get());
        active.set(true);
        equal(true, guarded.edit().remove("offset").commit());
        equal(3, raw.writes); equal(false, guarded.contains("offset"));
        equal(true, guarded.edit().clear().commit()); equal(4, raw.writes); equal(3, guarded.getAll().size());
        equal(true,guarded.getBoolean(StatusBarSettings.DATA_ACTIVITY_HIDDEN,false));
        equal(false,guarded.getBoolean(StatusBarShadeIconSettings.MASTER,true));
        guarded.edit().putBoolean(StatusBarSettings.DATA_ACTIVITY_HIDDEN,false).apply();
        equal(true,raw.values.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        guarded.edit().putString(StatusBarSettings.DATA_ACTIVITY_HIDDEN,"bad legacy type").apply();
        equal(true,raw.values.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        guarded.edit().putBoolean(NativeDataActivity.MASTER,true).putBoolean(StatusBarSettings.DATA_ACTIVITY_HIDDEN,true).apply();
        equal(false,raw.values.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));equal(false,guarded.getBoolean(StatusBarSettings.DATA_ACTIVITY_HIDDEN,true));
        guarded.edit().putFloat(NativeDataActivity.X,-2.5f).apply();equal(false,raw.values.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        guarded.edit().putBoolean(NotificationGroupStack.MASTER,true).apply();equal(false,raw.values.get(NotificationGroupStack.MASTER));
        equal(false,guarded.getBoolean(NotificationGroupStack.MASTER,true));
        guarded.edit().remove(NativeDataActivity.MASTER).apply();equal(true,raw.values.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        guarded.edit().putBoolean(NativeDataActivity.MASTER,true).clear().apply();equal(false,raw.values.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        guarded.edit().clear().apply();equal(true,raw.values.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        bigClockConflicts();
        System.out.println("Activation preference guard checks passed: " + checks);
    }

    private static void bigClockConflicts() {
        MemoryPreferences raw=new MemoryPreferences();AtomicBoolean active=new AtomicBoolean(true);AtomicInteger denied=new AtomicInteger();
        SharedPreferences guarded=new ActivationGuardPreferences(raw,active::get,denied::incrementAndGet);
        raw.values.put(NotificationBigClockSettings.MASTER,true);raw.values.put(StatusBarSettings.SHADE_CLOCK_SCALE,130f);
        raw.values.put("carrier_control_scale",170f);raw.values.put("carrier_lockscreen_enabled",true);
        SharedPreferences.Editor revoked=guarded.edit().putFloat(StatusBarSettings.SHADE_CLOCK_SCALE,220f);
        active.set(false);equal(false,revoked.commit());equal(0,raw.writes);equal(130f,raw.values.get(StatusBarSettings.SHADE_CLOCK_SCALE));equal(true,raw.values.get(NotificationBigClockSettings.MASTER));
        active.set(true);revoked.apply();equal(0,raw.writes);equal(2,denied.get());equal(true,raw.values.get(NotificationBigClockSettings.MASTER));
        guarded.edit().putBoolean(StatusBarSettings.SHADE_CLOCK_ENABLED,false).apply();equal(true,raw.values.get(NotificationBigClockSettings.MASTER));
        guarded.edit().putBoolean(StatusBarSettings.SHADE_CLOCK_ENABLED,true).putBoolean(StatusBarSettings.SHADE_CLOCK_ENABLED,false).apply();equal(true,raw.values.get(NotificationBigClockSettings.MASTER));
        guarded.edit().putBoolean("carrier_notification_enabled",false).apply();equal(true,raw.values.get(NotificationBigClockSettings.MASTER));
        guarded.edit().putBoolean("carrier_control_enabled",true).putFloat("carrier_lockscreen_scale",155f).apply();equal(true,raw.values.get(NotificationBigClockSettings.MASTER));
        guarded.edit().putBoolean(StatusBarSettings.SHADE_CLOCK_CONTROLS_ENABLED,true).apply();equal(false,raw.values.get(NotificationBigClockSettings.MASTER));equal(true,raw.values.get(StatusBarSettings.SHADE_CLOCK_CONTROLS_ENABLED));
        guarded.edit().putBoolean(NotificationBigClockSettings.MASTER,true).putFloat(StatusBarSettings.SHADE_CLOCK_SCALE,180f).apply();equal(true,raw.values.get(NotificationBigClockSettings.MASTER));equal(180f,raw.values.get(StatusBarSettings.SHADE_CLOCK_SCALE));
        guarded.edit().putFloat(StatusBarSettings.SHADE_CLOCK_SCALE,190f).putBoolean(NotificationBigClockSettings.MASTER,true).apply();equal(true,raw.values.get(NotificationBigClockSettings.MASTER));equal(190f,raw.values.get(StatusBarSettings.SHADE_CLOCK_SCALE));
        equal(true,guarded.edit().putString("carrier_notification_text","通知文字").commit());equal(false,raw.values.get(NotificationBigClockSettings.MASTER));equal("通知文字",raw.values.get("carrier_notification_text"));
        equal(170f,raw.values.get("carrier_control_scale"));equal(true,raw.values.get("carrier_lockscreen_enabled"));equal(190f,raw.values.get(StatusBarSettings.SHADE_CLOCK_SCALE));
        SharedPreferences.Editor reused=guarded.edit().putString(StatusBarSettings.SHADE_CLOCK_PATTERN,"HH:mm:ss");reused.apply();raw.edit().putBoolean(NotificationBigClockSettings.MASTER,true).apply();reused.putInt("unrelated",9).apply();equal(true,raw.values.get(NotificationBigClockSettings.MASTER));
        guarded.edit().putBoolean(StatusBarSettings.SHADE_CLOCK_ENABLED,true).remove(StatusBarSettings.SHADE_CLOCK_ENABLED).apply();equal(true,raw.values.get(NotificationBigClockSettings.MASTER));equal(false,raw.contains(StatusBarSettings.SHADE_CLOCK_ENABLED));
        guarded.edit().putBoolean(NotificationBigClockSettings.MASTER,true).putFloat(StatusBarSettings.SHADE_CLOCK_SCALE,210f).clear().apply();equal(true,raw.values.get(NotificationBigClockSettings.MASTER));equal(210f,raw.values.get(StatusBarSettings.SHADE_CLOCK_SCALE));
        active.set(false);int writes=raw.writes;guarded.edit().putString("carrier_notification_mode","text").apply();equal(writes,raw.writes);equal(true,raw.values.get(NotificationBigClockSettings.MASTER));equal(false,raw.contains("carrier_notification_mode"));
    }

    static final class MemoryPreferences implements SharedPreferences {
        final Map<String,Object> values = new HashMap<>(); int writes;
        public Map<String,?> getAll(){return new HashMap<>(values);}
        public String getString(String key,String fallback){return (String)values.getOrDefault(key,fallback);}
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String key,Set<String> fallback){return (Set<String>)values.getOrDefault(key,fallback);}
        public int getInt(String key,int fallback){return (Integer)values.getOrDefault(key,fallback);}
        public long getLong(String key,long fallback){return (Long)values.getOrDefault(key,fallback);}
        public float getFloat(String key,float fallback){return (Float)values.getOrDefault(key,fallback);}
        public boolean getBoolean(String key,boolean fallback){return (Boolean)values.getOrDefault(key,fallback);}
        public boolean contains(String key){return values.containsKey(key);}
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){}
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){}
        public Editor edit(){return new Editor(){
            final Map<String,Object> pending=new HashMap<>(); final Set<String> removed=new HashSet<>(); boolean clear;
            private Editor put(String k,Object v){removed.remove(k);if(v==null)return remove(k);pending.put(k,v);return this;}
            public Editor putString(String k,String v){return put(k,v);}
            public Editor putStringSet(String k,Set<String> v){return put(k,v);}
            public Editor putInt(String k,int v){return put(k,v);}
            public Editor putLong(String k,long v){return put(k,v);}
            public Editor putFloat(String k,float v){return put(k,v);}
            public Editor putBoolean(String k,boolean v){return put(k,v);}
            public Editor remove(String k){pending.remove(k);removed.add(k);return this;}
            public Editor clear(){clear=true;return this;}
            public boolean commit(){writes++;if(clear)values.clear();for(String k:removed)values.remove(k);values.putAll(pending);pending.clear();removed.clear();clear=false;return true;}
            public void apply(){commit();}
        };}
    }
}
