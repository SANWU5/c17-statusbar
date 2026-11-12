// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import org.json.JSONObject;

/** Real catalog limits and atomic, reversible runtime overlays. No saved preference writes. */
public final class CustomPluginSettingsCheck {
    private static int count;
    private static void check(boolean value,String reason) {
        count++;if(!value)throw new AssertionError(reason);
    }
    private static JSONObject one(String key,Object value) throws Exception {
        return new JSONObject().put(key,value);
    }
    private static void reject(JSONObject value,String reason) {
        try { CustomPluginSettings.validate(value);throw new AssertionError("accepted "+reason); }
        catch(IllegalArgumentException expected) {count++;}
    }
    private static void unchanged(Bundle saved,Bundle value,String reason) {
        check(value!=saved,reason+" is a copy");
        check(value.keySet().equals(saved.keySet()),reason+" retains keys");
        for(String key:saved.keySet())check(java.util.Objects.equals(saved.get(key),value.get(key)),reason+" retains "+key);
    }
    public static void main(String[] args) throws Exception {
        SettingsCatalog.Item scale=SettingsCatalog.item("clock_scale");
        check(scale!=null&&SettingsCatalog.NUMERIC.equals(scale.type),"sample uses actual numeric catalog key");
        check(scale.min==25f&&scale.max==250f,"sample bounds match real clock scale");
        CustomPluginSettings.validate(one("clock_scale",scale.min));count++;
        CustomPluginSettings.validate(one("clock_scale",scale.max));count++;
        reject(one("clock_scale",scale.min-.01d),"below real catalog minimum");
        reject(one("clock_scale",scale.max+.01d),"above real catalog maximum");
        reject(one("clock_scale","100"),"numeric string");
        reject(one("clock_enabled","true"),"boolean string");
        reject(one("clock_pattern",true),"boolean text");
        reject(one("not_a_c17_setting",true),"unknown setting");
        reject(one(StatusBarSettings.SAFE_MODE,false),"safety bypass");
        reject(one(StatusBarSettings.DIAGNOSTICS_ENABLED,true),"diagnostics override");
        String hidden=SettingsCatalog.hiddenKeys().stream().filter(key->!QsCardSettings.GRID_6.equals(key)).findFirst().orElseThrow(AssertionError::new);
        reject(one(hidden,true),"hidden internal setting");
        reject(one("clock_pattern",new String(new char[513]).replace('\0','a')),"overlong setting string");
        reject(one("clock_color_light",1.5d),"fractional ARGB");
        reject(one("clock_color_light",4294967296L),"ARGB overflow");
        JSONObject tooMany=new JSONObject();
        for(int i=0;i<CustomPluginSettings.MAX_SETTINGS+1;i++)tooMany.put("setting"+i,true);
        reject(tooMany,"per-plugin setting cap");

        Bundle saved=new Bundle();saved.putBoolean("clock_enabled",false);saved.putString("clock_pattern","saved-pattern");
        saved.putFloat("clock_scale",87.5f);saved.putBoolean("unrelated",true);
        JSONObject overlay=new JSONObject().put("clock_enabled",true).put("clock_pattern","HH:mm:ss")
                .put("clock_scale",125d).put("clock_color_light",4294967295L);
        CustomPluginSettings.validate(overlay);count++;
        Bundle result=CustomPluginSettings.overlay(saved,overlay);
        check(result!=saved,"runtime overlay owns a separate Bundle");
        check(result.getBoolean("clock_enabled"),"valid Boolean overlay applied");
        check("HH:mm:ss".equals(result.getString("clock_pattern")),"valid pattern overlay applied");
        check(result.get("clock_scale") instanceof Float&&((Number)result.get("clock_scale")).floatValue()==125f,"numeric runtime representation is float");
        check(result.getInt("clock_color_light")==-1,"unsigned ARGB is represented by signed Android int");
        check(result.getBoolean("unrelated"),"unrelated saved settings survive");
        check(!saved.getBoolean("clock_enabled")&&((Number)saved.get("clock_scale")).floatValue()==87.5f
                &&"saved-pattern".equals(saved.getString("clock_pattern"))&&!saved.containsKey("clock_color_light"),"source remains unchanged");
        unchanged(saved,CustomPluginSettings.overlay(saved,null),"disabled/null overlay");
        unchanged(saved,CustomPluginSettings.overlay(saved,new JSONObject()),"empty overlay");
        JSONObject broken=new JSONObject().put("clock_enabled",true).put("clock_scale",251d);
        unchanged(saved,CustomPluginSettings.overlay(saved,broken),"invalid aggregate rolls back all keys");
        saved.putBoolean(StatusBarSettings.SAFE_MODE,true);
        unchanged(saved,CustomPluginSettings.overlay(saved,overlay),"safe mode skips valid plugin settings");
        unchanged(saved,CustomPluginSettings.overlay(saved,broken),"safe mode skips even invalid plugin settings");
        check(CustomPluginSettings.overlay(null,overlay)==null,"missing saved bundle remains absent");
        System.out.println("CustomPluginSettingsCheck: "+count+" checks passed");
    }
}
