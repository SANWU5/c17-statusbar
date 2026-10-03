// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Read on explicit export/configuration events, never on a rendering frame. */
public final class DiagnosticReport {
    static final int MAX_REPORT_BYTES = 256 * 1024;

    static Map<String,Object> values(Bundle bundle) {
        if (bundle == null) return Collections.emptyMap();
        LinkedHashMap<String,Object> result=new LinkedHashMap<>();
        for (String key:bundle.keySet()) result.put(key,bundle.get(key));
        return result;
    }

    static boolean known(String key) {
        return key != null && (StatusBarSettings.BOOLEAN_DEFAULTS.containsKey(key)
                || StatusBarSettings.NUMERIC_DEFAULTS.containsKey(key)
                || StatusBarSettings.COLOR_DEFAULTS.containsKey(key)
                || StatusBarSettings.STRING_DEFAULTS.containsKey(key)
                || alphaKey(key));
    }

    private static boolean alphaKey(String key) {
        for (String color:StatusBarSettings.COLOR_DEFAULTS.keySet())
            if (StatusBarSettings.alphaKey(color).equals(key)) return true;
        return false;
    }

    /** Unknown keys and arbitrary objects never become report fields. */
    static JSONObject settingValue(String key,Object raw) throws JSONException {
        if (!known(key)) return null;
        JSONObject result=new JSONObject();
        if (raw == null) {result.put("type","unset");return result;}
        if (StatusBarSettings.BOOLEAN_DEFAULTS.containsKey(key) || alphaKey(key)) {
            result.put("type","boolean");
            if(raw instanceof Boolean)result.put("value",raw);else result.put("invalid_type",true);
        } else if (StatusBarSettings.NUMERIC_DEFAULTS.containsKey(key)) {
            result.put("type","number");
            if(raw instanceof Number && Double.isFinite(((Number)raw).doubleValue()))result.put("value",((Number)raw).doubleValue());
            else result.put("invalid_type",true);
        } else if (StatusBarSettings.COLOR_DEFAULTS.containsKey(key)) {
            result.put("type","color");
            if(raw instanceof Number && Double.isFinite(((Number)raw).doubleValue()))
                result.put("value",String.format(Locale.ROOT,"#%08X",((Number)raw).intValue()));
            else result.put("invalid_type",true);
        } else {
            if(!(raw instanceof String)){result.put("type","text");result.put("invalid_type",true);return result;}
            String value=(String)raw;
            if(isFont(key)) {
                result.put("type","font");result.put("source",fontSource(key,value));
            } else if (safeChoice(key,value)) {
                result.put("type","option");result.put("value",value);
            } else if (NativeStatusIcons.PRIORITY.equals(key)) {
                result.put("type","priority");JSONArray slots=new JSONArray();
                for(String slot:NativeStatusIcons.parsePriority(value))if(knownSlot(slot))slots.put(slot);
                result.put("slots",slots);
            } else {
                result.put("type","text");result.put("length",value.length());
                result.put("redacted",true);
            }
        }
        return result;
    }

    private static boolean isFont(String key) {
        return key.equals(StatusBarSettings.FONT_MODE)||key.equals(StatusBarSettings.FONT_REVISION)
                ||key.equals(StatusBarSettings.FONT_NAME)||key.endsWith("_font")||key.endsWith("_font_mode")
                ||key.endsWith("_font_source")||key.endsWith("_font_family");
    }

    private static String fontSource(String key,String value) {
        if(key.equals(StatusBarSettings.FONT_REVISION)) {
            FontCatalog.Entry catalog=FontCatalog.forRevision(value);
            return catalog != null ? "catalog:"+catalog.id : value.isEmpty()?"none":"imported";
        }
        if(key.equals(StatusBarSettings.FONT_NAME))return value.isEmpty()?"none":"name-redacted";
        for(String option:new String[]{"system","native","custom","pingfang","global","ios","apple","module","follow_global"})
            if(option.equals(value))return option;
        return value.isEmpty()?"none":"custom-redacted";
    }

    private static boolean safeChoice(String key,String value) {
        SettingsCatalog.Item item=SettingsCatalog.item(key);
        // Presets for a custom clock pattern/text are still text, not a finite enum.
        if(item != null && SettingsCatalog.OPTIONS.equals(item.type))
            for(String option:item.values)if(option.equals(value))return true;
        if((key.equals(StatusBarSettings.CARRIER_MODE)||key.endsWith("_mode"))
                && ("original".equals(value)||"time".equals(value)||"text".equals(value)))return true;
        return false;
    }

    private static boolean knownSlot(String value) {
        for(String slot:new String[]{"bluetooth","location","alarm_clock","zen","hotspot","headset","volume",
                "microphone","camera","privacy_call","vpn","rotate","cast","tty","managed_profile",
                "nfc","data_saver","sensors_off","sync_active","sync_failing","ethernet","ime","airplane",
                "speakerphone","mute","call_strength","secure","recording"})if(slot.equals(value))return true;
        return false;
    }

    static JSONObject change(String key,Object previous,Object next) throws JSONException {
        if(!known(key))return null;
        return new JSONObject().put("kind","setting_change").put("key",key)
                .put("previous",settingValue(key,previous)).put("next",settingValue(key,next));
    }

    static JSONObject settings(Map<String,?> values) throws JSONException {
        Map<String,?> saved=values == null?Collections.emptyMap():values;
        TreeSet<String> keys=new TreeSet<>();keys.addAll(StatusBarSettings.BOOLEAN_DEFAULTS.keySet());
        keys.addAll(StatusBarSettings.NUMERIC_DEFAULTS.keySet());keys.addAll(StatusBarSettings.COLOR_DEFAULTS.keySet());
        keys.addAll(StatusBarSettings.STRING_DEFAULTS.keySet());
        for(String color:StatusBarSettings.COLOR_DEFAULTS.keySet())keys.add(StatusBarSettings.alphaKey(color));
        JSONObject result=new JSONObject();
        for(String key:keys) {
            Object effective=alphaKey(key)?StatusBarSettings.customAlpha(saved,key.substring(0,key.length()-"_custom_alpha".length()))
                    :SettingsCatalog.value(saved,key);
            JSONObject value=settingValue(key,effective);
            value.put("saved",saved.containsKey(key));
            if(saved.containsKey(key))value.put("stored",settingValue(key,saved.get(key)));
            SettingsCatalog.Item item=SettingsCatalog.item(key);
            if(item != null && SettingsCatalog.NUMERIC.equals(item.type)) {
                value.put("unit",item.unit);value.put("recommended_min",item.min);value.put("recommended_max",item.max);
                Object number=effective;
                if(number instanceof Number)value.put("outside_recommendation",SettingsCatalog.requiresNumericTrial(item,(Number)number));
            }
            result.put(key,value);
        }
        return result;
    }

    static JSONObject display(Context context) {
        JSONObject result=new JSONObject();
        try {
            DisplayMetrics metrics=context.getResources().getDisplayMetrics();
            Configuration config=context.getResources().getConfiguration();
            result.put("width_px",metrics.widthPixels);result.put("height_px",metrics.heightPixels);
            result.put("density",metrics.density);result.put("density_dpi",metrics.densityDpi);
            result.put("scaled_density",metrics.scaledDensity);result.put("font_scale",config.fontScale);
            result.put("orientation",config.orientation==Configuration.ORIENTATION_LANDSCAPE?"landscape":
                    config.orientation==Configuration.ORIENTATION_PORTRAIT?"portrait":"undefined");
            result.put("night",(config.uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
            result.put("width_dp",config.screenWidthDp);result.put("height_dp",config.screenHeightDp);
            result.put("smallest_width_dp",config.smallestScreenWidthDp);
        } catch(Throwable unavailable) {try{result.put("unavailable",true);}catch(JSONException ignored){}}
        return result;
    }

    static JSONObject device(Context context) {
        JSONObject result=new JSONObject();
        try {
            result.put("manufacturer",safeBuild(Build.MANUFACTURER));result.put("brand",safeBuild(Build.BRAND));
            result.put("model",safeBuild(Build.MODEL));result.put("android",safeBuild(Build.VERSION.RELEASE));
            result.put("sdk",Build.VERSION.SDK_INT);result.put("build_id",safeBuild(Build.ID));
            result.put("build_display",safeBuild(Build.DISPLAY));result.put("coloros",colorOs());
            result.put("display",display(context));
        } catch(Throwable unavailable) {try{result.put("unavailable",true);}catch(JSONException ignored){}}
        return result;
    }

    private static String colorOs() {
        // Fixed ROM version keys only. No property enumeration, identifiers or network state.
        try {
            Class<?> properties=Class.forName("android.os.SystemProperties");Method read=properties.getDeclaredMethod("get",String.class,String.class);
            for(String key:new String[]{"ro.build.version.oplusrom.display","ro.build.version.oplusrom","ro.build.version.opporom"}) {
                Object value=read.invoke(null,key,"");
                if(value instanceof String && !((String)value).isEmpty())return safeBuild((String)value);
            }
        } catch(ReflectiveOperationException|RuntimeException unavailable) { }
        return "unknown";
    }

    static String safeBuild(String value) {
        return value != null && value.matches("[\\p{L}\\p{N} ._()+-]{1,120}")?value:"unknown";
    }

    static JSONObject report(Context context,Map<String,?> values) throws JSONException {
        JSONObject app=new JSONObject().put("version_name","unknown").put("version_code",0);
        try {
            PackageInfo info=context.getPackageManager().getPackageInfo(ModuleDiagnostics.PACKAGE_NAME,0);
            app.put("version_name",safeBuild(info.versionName));
            app.put("version_code",Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode);
        } catch(Exception unavailable) { }
        app.put("expected_runtime",ModuleRuntimeStatus.BUILD_TOKEN);
        return new JSONObject().put("report_schema",1).put("app",app).put("device",device(context))
                .put("configuration",settings(values)).put("limitations",
                        "文本与字体文件内容已脱敏；请补充故障出现的方向、操作顺序、快慢与录屏。配置与日志不足以精确重放任意手势。");
    }

    static JSONObject validateDetails(JSONObject input) throws JSONException {
        if(input == null)return null;
        String kind=input.optString("kind","");
        if("setting_change".equals(kind)) {
            String key=input.optString("key","");if(!known(key))return null;
            return new JSONObject().put("kind",kind).put("key",key)
                    .put("previous",validateSetting(key,input.optJSONObject("previous")))
                    .put("next",validateSetting(key,input.optJSONObject("next")));
        }
        if("runtime_applied".equals(kind)) {
            JSONObject result=new JSONObject().put("kind",kind);
            String token=input.optString("runtime","");
            if(token.matches("c17-runtime-[A-Za-z0-9_-]{1,120}"))result.put("runtime",token);
            copyFinite(input,result,"sequence",0,Long.MAX_VALUE);
            copyFinite(input,result,"setting_count",0,2048);
            JSONObject raw=input.optJSONObject("display"),display=new JSONObject();
            if(raw != null) {
                for(String key:new String[]{"width_px","height_px","density","density_dpi","scaled_density","font_scale",
                        "width_dp","height_dp","smallest_width_dp"})copyFinite(raw,display,key,0,100000);
                String orientation=raw.optString("orientation","");
                if("portrait".equals(orientation)||"landscape".equals(orientation)||"undefined".equals(orientation))display.put("orientation",orientation);
                if(raw.opt("night") instanceof Boolean)display.put("night",raw.opt("night"));
            }
            result.put("display",display);return result;
        }
        if("hook_stage".equals(kind)) {
            String phase=input.optString("phase","");if(!phase(phase))return null;
            JSONObject result=new JSONObject().put("kind",kind).put("phase",phase);
            JSONObject raw=input.optJSONObject("metrics"),safe=new JSONObject();
            if(raw != null)for(String key:new String[]{"enabled","owner_known","native_ready","cache_hit","custom_prepared",
                    "view_count","pending_count","result_count","portrait","landscape","width_px","height_px","alpha","progress","elapsed_ms"}) {
                if(raw.opt(key) instanceof Boolean)safe.put(key,raw.opt(key));
                else copyFinite(raw,safe,key,-100000,100000);
            }
            result.put("metrics",safe);return result;
        }
        return null;
    }

    static boolean phase(String phase) {
        for(String value:new String[]{"install","install_failed","configure","apply","prepare","prepare_failed","bind",
                "bind_failed","restore","restore_failed","release","unsupported","cache_hit","skipped"})if(value.equals(phase))return true;
        return false;
    }

    static boolean metricKnown(String name) {
        for(String key:new String[]{"enabled","owner_known","native_ready","cache_hit","custom_prepared","view_count",
                "pending_count","result_count","portrait","landscape","width_px","height_px","alpha","progress","elapsed_ms"})
            if(key.equals(name))return true;
        return false;
    }

    private static JSONObject validateSetting(String key,JSONObject input) throws JSONException {
        if(input == null)return settingValue(key,null);
        if("unset".equals(input.optString("type")))return settingValue(key,null);
        if(input.optBoolean("invalid_type",false))return new JSONObject().put("type",type(key)).put("invalid_type",true);
        if(StatusBarSettings.STRING_DEFAULTS.containsKey(key)) {
            if(isFont(key)) {
                String source=input.optString("source","");
                if(source.startsWith("catalog:") && FontCatalog.find(source.substring(8)) != null)
                    return new JSONObject().put("type","font").put("source",source);
                for(String allowed:new String[]{"none","imported","name-redacted","system","native","custom","pingfang","global","ios","apple","module","follow_global","custom-redacted"})
                    if(allowed.equals(source))return new JSONObject().put("type","font").put("source",source);
                return new JSONObject().put("type","font").put("source","custom-redacted");
            }
            Object option=input.opt("value");
            if(option instanceof String && safeChoice(key,(String)option))return settingValue(key,option);
            if(NativeStatusIcons.PRIORITY.equals(key)) {
                JSONArray raw=input.optJSONArray("slots"),safe=new JSONArray();
                if(raw != null)for(int i=0;i<Math.min(64,raw.length());i++){String slot=raw.optString(i,"");if(knownSlot(slot))safe.put(slot);}
                return new JSONObject().put("type","priority").put("slots",safe);
            }
            JSONObject result=new JSONObject().put("type","text").put("redacted",true);
            copyFinite(input,result,"length",0,Integer.MAX_VALUE);return result;
        }
        Object value=input.opt("value");
        if(StatusBarSettings.COLOR_DEFAULTS.containsKey(key) && value instanceof String && ((String)value).matches("#[0-9A-Fa-f]{8}"))
            return settingValue(key,(int)Long.parseLong(((String)value).substring(1),16));
        return settingValue(key,value);
    }

    private static String type(String key) {
        return StatusBarSettings.STRING_DEFAULTS.containsKey(key)?isFont(key)?"font":"text":
                StatusBarSettings.NUMERIC_DEFAULTS.containsKey(key)?"number":StatusBarSettings.COLOR_DEFAULTS.containsKey(key)?"color":"boolean";
    }

    private static void copyFinite(JSONObject input,JSONObject output,String key,double min,double max) throws JSONException {
        Object value=input.opt(key);
        if(value instanceof Number) {
            double number=((Number)value).doubleValue();
            if(Double.isFinite(number)&&number>=min&&number<=max)output.put(key,value);
        }
    }

    private DiagnosticReport() { }
}
