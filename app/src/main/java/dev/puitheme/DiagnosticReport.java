// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
        for(String option:new String[]{"system","native","native_sans","native_digit","native_serif","custom","pingfang","global","ios","apple","module","follow_global"})
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
            result.put("device",safeBuild(Build.DEVICE));result.put("product",safeBuild(Build.PRODUCT));
            result.put("hardware",safeBuild(Build.HARDWARE));result.put("board",safeBuild(Build.BOARD));
            result.put("incremental",safeBuild(Build.VERSION.INCREMENTAL));
            result.put("security_patch",safeBuild(Build.VERSION.SECURITY_PATCH));
            result.put("fingerprint",safeFingerprint(Build.FINGERPRINT));
            JSONArray abis=new JSONArray();
            for(String abi:Build.SUPPORTED_ABIS)abis.put(safeBuild(abi));
            result.put("abis",abis);
            result.put("rom",rom());
            result.put("locale",safeBuild(Locale.getDefault().toLanguageTag()));
            result.put("time_zone",safeBuild(java.util.TimeZone.getDefault().getID()));
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

    /** Fixed ROM version properties; none identifies the owner or the device instance. */
    static JSONObject rom() {
        JSONObject result=new JSONObject();
        try {
            Class<?> properties=Class.forName("android.os.SystemProperties");Method read=properties.getDeclaredMethod("get",String.class,String.class);
            String[][] keys={{"oplusrom_display","ro.build.version.oplusrom.display"},{"oplusrom","ro.build.version.oplusrom"},
                    {"opporom","ro.build.version.opporom"},{"ota_version","ro.build.ota.versionname"},
                    {"market_name","ro.vendor.oplus.market.name"},{"display_id","ro.build.display.id"}};
            for(String[] key:keys) {
                Object value=read.invoke(null,key[1],"");
                if(value instanceof String && !((String)value).isEmpty())result.put(key[0],safeFingerprint((String)value));
            }
        } catch(ReflectiveOperationException|RuntimeException|JSONException unavailable) { }
        return result;
    }

    /** Version, install and update times of the packages the module depends on. */
    static JSONObject packages(Context context) {
        JSONObject result=new JSONObject();
        String[] names={ModuleRuntimeStatus.SYSTEM_UI,"com.oplus.systemui.plugins",ModuleDiagnostics.PACKAGE_NAME,"com.android.launcher",
                "com.oplus.launcher","com.heytap.speechassist"};
        for(String name:names) {
            try {
                PackageInfo info=context.getPackageManager().getPackageInfo(name,0);
                JSONObject item=new JSONObject();
                item.put("version_name",safeBuild(info.versionName));
                item.put("version_code",Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode);
                item.put("first_install_ms",info.firstInstallTime);item.put("last_update_ms",info.lastUpdateTime);
                if(info.applicationInfo != null) {
                    item.put("enabled",info.applicationInfo.enabled);
                    item.put("system",(info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0);
                    item.put("target_sdk",info.applicationInfo.targetSdkVersion);
                }
                result.put(name,item);
            } catch(Exception absent) { /* A missing optional package is not an error. */ }
        }
        return result;
    }

    static String safeFingerprint(String value) {
        return value != null && value.matches("[\\p{L}\\p{N} ._()+:,/-]{1,200}")?value:"unknown";
    }

    static String safeBuild(String value) {
        return value != null && value.matches("[\\p{L}\\p{N} ._()+-]{1,120}")?value:"unknown";
    }

    static JSONObject report(Context context,Map<String,?> values) throws JSONException {
        return report(context,values,Collections.<JSONObject>emptyList());
    }

    /** events are the newest saved events, oldest first; they only feed the diagnosis hints. */
    /** This app: version, the runtime token it expects, and the settings schema. */
    static JSONObject app(Context context) throws JSONException {
        JSONObject app=new JSONObject().put("version_name","unknown").put("version_code",0);
        try {
            PackageInfo info=context.getPackageManager().getPackageInfo(ModuleDiagnostics.PACKAGE_NAME,0);
            app.put("version_name",safeBuild(info.versionName));
            app.put("version_code",Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode);
        } catch(Exception unavailable) { }
        app.put("expected_runtime",ModuleRuntimeStatus.BUILD_TOKEN);
        app.put("settings_schema",SettingsFrameworkMirror.VERSION);
        return app;
    }

    static JSONObject report(Context context,Map<String,?> values,List<JSONObject> events) throws JSONException {
        JSONObject app=app(context);
        JSONObject device=device(context),packages=packages(context);
        JSONObject latest=latestFacts(events);
        return new JSONObject().put("report_schema",2).put("guide",guide()).put("app",app).put("device",device)
                .put("packages",packages).put("diagnosis",diagnose(app,packages,events,values))
                .put("latest_facts",latest).put("configuration",settings(values)).put("limitations",
                        "文本与字体文件内容已脱敏；请补充故障出现的方向、操作顺序、快慢与录屏。配置与日志不足以精确重放任意手势。");
    }

    /** What each part of the file means, so it can be read without a tool. */
    static JSONArray guide() {
        JSONArray result=new JSONArray();
        String[] lines={
                "diagnosis：由本文件自动得出的初步判断，先看这里；status 为 warn 的条目最可能是原因。",
                "device / packages：机型、ColorOS、系统界面与插件版本及更新时间；系统界面更新或模块更新后需对比 last_update_ms。",
                "latest_facts：每个主题最后一次状态；scene=场景，clock=时钟，fluid=流体云与通知背景，ui=设置应用。",
                "configuration：每个设置项的有效值(value)、是否已保存(saved)、原始存储值(stored)。文本内容已脱敏。",
                "近期事件(JSONL)：timestamp 为 UTC。details.kind=fact 的 values 是结构化状态；runtime_applied 表示系统界面成功应用了一次配置。",
                "常见判断：runtime_applied 缺失或 runtime 与 app.expected_runtime 不同=系统界面没有加载当前版本，需要在主页点“重启系统界面”。"};
        for(String line:lines)result.put(line);
        return result;
    }

    /** Last fact of every topic from the saved events. */
    static JSONObject latestFacts(List<JSONObject> events) throws JSONException {
        JSONObject result=new JSONObject();
        if(events == null)return result;
        for(JSONObject event:events) {
            JSONObject details=event.optJSONObject("details");
            if(details == null || !"fact".equals(details.optString("kind")))continue;
            String topic=details.optString("topic","");
            JSONObject entry=new JSONObject().put("timestamp",event.optString("timestamp","")).put("message",event.optString("message",""))
                    .put("values",details.optJSONObject("values") == null?new JSONObject():details.optJSONObject("values"));
            result.put(topic,entry);
        }
        return result;
    }

    /** Heuristics that replace a first look by a person or tool. Each entry explains itself. */
    static JSONArray diagnose(JSONObject app,JSONObject packages,List<JSONObject> events,Map<String,?> values) throws JSONException {
        JSONArray result=new JSONArray();
        JSONObject runtime=null;
        if(events != null)for(JSONObject event:events) {
            JSONObject details=event.optJSONObject("details");
            if(details != null && "runtime_applied".equals(details.optString("kind")))runtime=details;
        }
        JSONObject module=packages.optJSONObject(ModuleDiagnostics.PACKAGE_NAME);
        if(runtime == null) {
            result.put(item("runtime_applied","warn","日志里没有系统界面应用配置的记录。可能刚开启日志、系统界面还没重启，或模块没有在系统界面里生效。"));
        } else {
            String token=runtime.optString("runtime","");
            if(!ModuleRuntimeStatus.BUILD_TOKEN.equals(token))
                result.put(item("runtime_token","warn","系统界面运行的是旧标识 "+token+"，当前模块是 "+ModuleRuntimeStatus.BUILD_TOKEN+"：请重启系统界面。"));
            else result.put(item("runtime_token","ok","系统界面已加载当前版本："+token));
            double started=runtime.optDouble("process_start_ms",0d);
            long updated=module == null?0L:module.optLong("last_update_ms",0L);
            if(started > 0d && updated > 0L && started < updated)
                result.put(item("systemui_older_than_module","warn","系统界面进程启动于模块更新之前，里面不可能是新代码，请重启系统界面。"));
            String version=runtime.optString("systemui_version","");
            JSONObject systemui=packages.optJSONObject(ModuleRuntimeStatus.SYSTEM_UI);
            if(!version.isEmpty() && systemui != null && !version.equals(systemui.optString("version_name","")))
                result.put(item("systemui_version_changed","warn","系统界面包版本在运行期间发生变化："+version+" → "+systemui.optString("version_name","")+"，需要重启。"));
        }
        if(StatusBarSettings.bool(values,StatusBarSettings.SAFE_MODE))
            result.put(item("safe_mode","warn","安全模式已开启：所有修改暂停，这是自定义效果不出现的直接原因。"));
        JSONObject latest=latestFacts(events);
        JSONObject clock=latest.optJSONObject("clock");
        if(clock != null) {
            JSONObject v=clock.optJSONObject("values");
            if(v != null && v.optBoolean("dual_enabled",false) && !v.optBoolean("dual_active",false))
                result.put(item("dual_clock","warn","双排时钟已开启但没有生效，原因："+v.optString("reason","未知")+"。"));
            else if(v != null && v.optBoolean("dual_active",false))
                result.put(item("dual_clock","ok","双排时钟已生效（下排布局已建立："+v.optBoolean("lower_ready",false)+"）。"));
        }
        int healed=0;String healReason="";long healGap=0L;
        if(events != null)for(JSONObject event:events) {
            JSONObject details=event.optJSONObject("details");
            if(details == null || !"fact".equals(details.optString("kind")) || !"clock".equals(details.optString("topic")))continue;
            JSONObject v=details.optJSONObject("values");
            String why=v == null?"":v.optString("reason","");
            if(why.equals("pending_lost") || why.equals("screen_on_missed") || why.equals("draw_without_tick")) {
                healed++;healReason=why;healGap=(long)v.optDouble("gap_ms",0d);
            }
        }
        if(healed > 0)result.put(item("clock_tick","warn","时钟的秒数刷新停过并已自动恢复 "+healed+" 次，最近一次原因："+healReason
                +"，停顿约 "+healGap+" ms。pending_lost=定时器丢失；screen_on_missed=漏收亮屏广播；draw_without_tick=界面还在绘制但没有定时器。"));
        if(result.length() == 0)result.put(item("none","info","没有发现明显问题，请结合 latest_facts 与事件时间线分析。"));
        return result;
    }

    private static JSONObject item(String id,String status,String text) throws JSONException {
        return new JSONObject().put("id",id).put("status",status).put("text",text);
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
            if(token.matches("c17-runtime-[A-Za-z0-9._-]{1,120}"))result.put("runtime",token);
            copyFinite(input,result,"sequence",0,Long.MAX_VALUE);
            copyFinite(input,result,"setting_count",0,2048);
            copyFinite(input,result,"pid",0,10000000);
            copyFinite(input,result,"process_start_ms",0,4102444800000d);
            copyFinite(input,result,"uptime_ms",0,4102444800000d);
            copyFinite(input,result,"systemui_code",0,Long.MAX_VALUE);
            copyFinite(input,result,"plugin_code",0,Long.MAX_VALUE);
            copyFinite(input,result,"schema",0,1000);
            for(String key:new String[]{"systemui_version","plugin_version"}) {
                String value=input.optString(key,"");
                if(!value.isEmpty())result.put(key,safeBuild(value));
            }
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
        if("fact".equals(kind))return validateFact(input);
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

    static final String[] FACT_TOPICS={"scene","clock","fluid","notification","label","ui","environment","weather","weather_slots","weather_widget","backdrop_source","backdrop_material","system_icons","qs_cards"};
    /** Structured state names. Values are booleans, finite numbers or short identifier-like words; never text. */
    static final String[] FACT_NAMES={
            // Control-center adaptation: fixed structural state and pixel bounds only.
            "qs_ready","qs_enabled","qs_ui_thread","qs_session","qs_rows","qs_phase","qs_flags","qs_items",
            "qs_native_rows","qs_native_cols","qs_cell_width","qs_cell_height","qs_native_auto","qs_view_id",
            "qs_parent_type","qs_parent_height","qs_view_top","qs_view_height","qs_footer_top","qs_desired_height",
            "qs_reserved_height","qs_available_height","qs_user_known",
            // scene
            "scene","previous","keyguard","dozing","aod_transition","aod_progress","aod_known","reason","source",
            // clock
            "group","stat_clock","dual_enabled","dual_active","master_enabled","layout_ready","lower_ready","view_width",
            "view_height","baseline","upper_width","lower_width","margin_start_px","margin_end_px","density","text_size",
            "lower_size","digit_fixed","seconds","upper_len","lower_len","gap_ms","interactive","pending",
            // weather pipeline: fixed structural state only, never actual weather/location/text
            "event","owner_class","loader_id","loader_known","hook_mask","controller_known","controller_shape",
            "located","alive","data_present","data_shape","description_present","temperature_present","snapshot_ready","native_icon_ready",
            "revision","snapshot_source","overlay_ready","components_ready","left_enabled","right_enabled",
            "left_weather","right_weather","left_placeholder","right_placeholder","left_matches","right_matches",
            "widget_view_shape","widget_binding","widget_card","widget_card_shape","card_class","widget_info","widget_service",
            "widget_lock","widget_local","widget_cache","widget_engine","widget_pending","widget_render_ready",
            "widget_payload","widget_payload_bytes","widget_json","widget_current_page","widget_root_fields","widget_data_fields","attached",
            "engine_class","inner_class","service_class","widget_weather_package","widget_card_data","widget_init_data","widget_instant_uri","widget_payload_source",
            "widget_renderer","widget_preview","widget_page_known","widget_document","widget_node_count","widget_temperature_node","widget_description_node",
            "widget_image_nodes","widget_icon_nodes","widget_icon_source","widget_image_stage","widget_image_detail",
            // Explicit clock backdrop probe: fixed counters only, no pixels or text.
            "created_sources","image_events","draw_events","image_changes","bitmap_changes","generation_changes","geometry_changes",
            "active_changes","wrapping_changes","invalidations","snapshot_calls","ready_frames","cached_frames","new_frames",
            "unavailable_frames","inactive_frames","missing_images","ambiguous_frames","invalid_frames","poisoned_sources",
            "source_count","ready_source_count","poisoned_source_count","source_reason",
            "promotion_scopes","coalesced_image_events","deferred_frames","promotion_failures",
            "apply_calls","unchanged_calls","ready_to_pending","pending_to_ready","sampler_rebuilds","effect_rebuilds",
            "content_updates","geometry_updates","style_updates","material_failures","material_clears","material_state",
            "deferred_updates",
            "pending_visible","pending_hidden","pending_no_frame","pending_recycled","pending_root_mismatch","pending_other","pending_reason",
            // Component/native status-icon parity: cached scalar state only.
            "icon_components_active","icon_signal_stacked","icon_primary_res","icon_secondary_res","icon_wifi_res","icon_wifi_level",
            "icon_battery_native","icon_battery_type","icon_battery_width_px","icon_battery_height_px","icon_battery_percent",
            "icon_battery_inside","icon_battery_charging","icon_battery_created","icon_battery_failures",
            // fluid cloud / notification background
            "host","backend","row","accent_enabled","accent_active","follow_media","follow_media_active","custom_active",
            "custom_scope","native_follow","priority","fill_argb","opacity","media_bound","media_count","media_argb","applied",
            "mask","aod_hold","recipe","mode","scope_float","scope_shade","scope_lock","unified","notification_media",
            "scope_category","scope_allowed","service_id","business_package",
            "highlight","keep_highlight","count","gradient_range","gradient_start","gradient_mid","gradient_strength","gradient_opacity","gradient_owned","gradient_drawn","gradient_rebuilt",
            // network label
            "raw_label","display_label","custom_formats","wifi_in_use","airplane","compose_ready","enabled",
            "hull_px","label_width_px","signal_width_px","label_left_px","signal_left_px","native_width_px","measured_width_px","row_origin_px","row_width_px",
            "parent_left_px","native_label_left_px","native_signal_left_px","label_lp","signal_lp","parent_lp","failure_class",
            "parent_origin_px","current_x_px","target_x_px","distance_end_px","curve_end_px","baseline_x_px","applied_x_px","residual_x_px",
            // status bar vertical frames
            "root_top","root_height","root_pad_top","root_ty","content_top","content_height","content_pad_top","content_ty",
            "start_top","start_height","start_ty","end_top","end_height","end_ty","capsule",
            // status bar flow plan per host: bounds and up to 8 "token:target:width:visible:drawn" entries
            "bounds_start","bounds_end","bounds_shared","e0","e1","e2","e3","e4","e5","e6","e7",
            // ui
            "theme_mode","scheme","black_level","dynamic","blur_top","glass_nav","dark","night",
            // environment
            "process","pid","process_start_ms","uptime_ms","systemui_version","systemui_code","plugin_version","plugin_code","runtime","schema"};

    static JSONObject validateFact(JSONObject input) throws JSONException {
        String topic=input.optString("topic","");
        boolean known=false;
        for(String value:FACT_TOPICS)if(value.equals(topic))known=true;
        if(!known)return null;
        JSONObject raw=input.optJSONObject("values"),safe=new JSONObject();
        if(raw != null)for(String name:FACT_NAMES) {
            Object value=raw.opt(name);
            if(value instanceof Boolean)safe.put(name,value);
            else if(value instanceof Number) {
                double number=((Number)value).doubleValue();
                if(Double.isFinite(number)&&Math.abs(number)<=1e13)safe.put(name,value);
            } else if(value instanceof String) {
                String text=(String)value;
                if("card_class".equals(name)||"engine_class".equals(name)||"inner_class".equals(name)||"service_class".equals(name)) {
                    if(text.length()<=160&&text.matches("(?:kswc|pantanal\\.app|com\\.nearme\\.instant|com\\.oplus\\.seedling|org\\.hapjs|a\\.a\\.a\\.v[0-9]+)\\.[A-Za-z0-9_.$]+"))safe.put(name,text);
                } else if("owner_class".equals(name)) {
                    if(text.length()<=160&&(text.matches("com\\.(?:oplus\\.keyguard\\.clock|oplus\\.keyguard\\.style\\.widgets|android\\.keyguard|oplus\\.systemui\\.keyguard)\\.[A-Za-z0-9_.$]+")
                            ||"kswc.yN".equals(text)||"kswc.IN".equals(text)))safe.put(name,text);
                } else if(text.matches("[A-Za-z0-9_.:#+-]{0,48}"))safe.put(name,text);
            }
        }
        return new JSONObject().put("kind","fact").put("topic",topic).put("values",safe);
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
                for(String allowed:new String[]{"none","imported","name-redacted","system","native","native_sans","native_digit","native_serif","custom","pingfang","global","ios","apple","module","follow_global","custom-redacted"})
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
