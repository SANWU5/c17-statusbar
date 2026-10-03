package dev.puitheme;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

/** Export-only schema and privacy boundaries, independent of a device or private preferences. */
public final class DiagnosticReportCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("expected "+expected+" got "+actual);}
    private static final class Device extends Context {
        final Resources resources=new Resources();
        @Override public Resources getResources(){return resources;}
    }
    public static int run() throws Exception {
        Device device=new Device();device.resources.getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        device.resources.getConfiguration().fontScale=1.25f;device.resources.getDisplayMetrics().scaledDensity=5f;
        JSONObject display=DiagnosticReport.display(device);
        equal("landscape",display.getString("orientation"));equal(1440,display.getInt("width_px"));
        equal(3168,display.getInt("height_px"));equal(640,display.getInt("density_dpi"));
        equal(1.25,display.getDouble("font_scale"));equal(5.0,display.getDouble("scaled_density"));
        JSONObject hardware=DiagnosticReport.device(device);
        equal("PJZ110",hardware.getString("model"));equal("OnePlus",hardware.getString("manufacturer"));
        equal("16",hardware.getString("android"));equal(false,hardware.has("serial"));equal(false,hardware.has("fingerprint"));
        equal("unknown",DiagnosticReport.safeBuild("/data/private"));equal("unknown",DiagnosticReport.safeBuild("secret\nline"));

        LinkedHashMap<String,Object> values=new LinkedHashMap<>();
        values.putAll(StatusBarSettings.BOOLEAN_DEFAULTS);values.putAll(StatusBarSettings.NUMERIC_DEFAULTS);
        values.putAll(StatusBarSettings.COLOR_DEFAULTS);values.putAll(StatusBarSettings.STRING_DEFAULTS);
        values.put("clock_scale",79f);values.put("clock_weight",824f);values.put("battery_color_light",0x80aabbcc);
        values.put("clock_pattern","HH:mm secret private text");values.put(StatusBarSettings.CARRIER_TEXT,"private carrier content");
        values.put(StatusBarSettings.FONT_NAME,"/data/user/private.ttf");values.put(StatusBarSettings.FONT_REVISION,"some-private-font-hash");
        values.put("ssid","private network");values.put("pin","PIN_FIXTURE_A7");values.put("unknown_secret","private unknown text");
        values.put(NativeStatusIcons.PRIORITY,"bluetooth,location,private_token_PIN_FIXTURE_A7");
        JSONObject snapshot=DiagnosticReport.settings(values),report=DiagnosticReport.report(device,values);
        equal(79.0,snapshot.getJSONObject("clock_scale").getDouble("value"));
        equal(824.0,snapshot.getJSONObject("clock_weight").getDouble("value"));
        equal("#80AABBCC",snapshot.getJSONObject("battery_color_light").getString("value"));
        equal("HH:mm secret private text".length(),snapshot.getJSONObject("clock_pattern").getInt("length"));
        equal("imported",snapshot.getJSONObject(StatusBarSettings.FONT_REVISION).getString("source"));
        equal("name-redacted",snapshot.getJSONObject(StatusBarSettings.FONT_NAME).getString("source"));
        equal(2,snapshot.getJSONObject(NativeStatusIcons.PRIORITY).getJSONArray("slots").length());
        for(String key:StatusBarSettings.BOOLEAN_DEFAULTS.keySet())equal(true,snapshot.has(key));
        for(String key:StatusBarSettings.NUMERIC_DEFAULTS.keySet())equal(true,snapshot.has(key));
        for(String key:StatusBarSettings.COLOR_DEFAULTS.keySet())equal(true,snapshot.has(key));
        for(String key:StatusBarSettings.STRING_DEFAULTS.keySet())equal(true,snapshot.has(key));
        for(String color:StatusBarSettings.COLOR_DEFAULTS.keySet())equal(true,snapshot.has(StatusBarSettings.alphaKey(color)));
        String serialized=report.toString(2);
        for(String privateValue:new String[]{"secret private text","private carrier content","/data/user","private.ttf",
                "some-private-font-hash","private network","PIN_FIXTURE_A7","private unknown text","private_token"})equal(false,serialized.contains(privateValue));
        equal(true,serialized.getBytes(StandardCharsets.UTF_8).length<=DiagnosticReport.MAX_REPORT_BYTES);
        equal(1,report.getInt("report_schema"));equal(ModuleRuntimeStatus.BUILD_TOKEN,report.getJSONObject("app").getString("expected_runtime"));
        equal(true,report.getString("limitations").contains("不能")||report.getString("limitations").contains("不足"));
        // Wrong types retain a useful failure marker and their applied fallback, without printing the raw value.
        values.put("clock_scale","private corrupted numeric");values.put("clock_enabled","private corrupted boolean");
        snapshot=DiagnosticReport.settings(values);
        equal(true,snapshot.getJSONObject("clock_scale").getJSONObject("stored").getBoolean("invalid_type"));
        equal(true,snapshot.getJSONObject("clock_enabled").getJSONObject("stored").getBoolean("invalid_type"));
        equal(false,snapshot.toString().contains("private corrupted"));
        JSONObject change=DiagnosticReport.change("clock_weight",600f,824f);
        equal(600.0,change.getJSONObject("previous").getDouble("value"));equal(824.0,change.getJSONObject("next").getDouble("value"));
        equal(null,DiagnosticReport.change("pin",1234,528));
        JSONObject textChange=DiagnosticReport.change(StatusBarSettings.CARRIER_TEXT,"private before","private after");
        equal(false,textChange.toString().contains("private"));equal(13,textChange.getJSONObject("next").getInt("length"));
        JSONObject font=DiagnosticReport.settingValue(StatusBarSettings.FONT_REVISION,FontCatalog.entries().get(0).sha256);
        equal("catalog:"+FontCatalog.entries().get(0).id,font.getString("source"));
        JSONObject malicious=new JSONObject(change.toString());malicious.put("private_extra","secret");
        malicious.getJSONObject("next").put("user_text","private payload");
        JSONObject validated=DiagnosticReport.validateDetails(malicious);
        equal(false,validated.toString().contains("secret"));equal(false,validated.toString().contains("private payload"));
        malicious.put("key","pin");equal(null,DiagnosticReport.validateDetails(malicious));
        JSONObject fakeText=new JSONObject().put("kind","setting_change").put("key",StatusBarSettings.CARRIER_TEXT)
                .put("next",new JSONObject().put("type","option").put("value","private payload").put("length",15));
        validated=DiagnosticReport.validateDetails(fakeText);equal(false,validated.toString().contains("private payload"));
        equal(15,validated.getJSONObject("next").getInt("length"));
        JSONObject runtime=new JSONObject().put("kind","runtime_applied").put("runtime",ModuleRuntimeStatus.BUILD_TOKEN)
                .put("sequence",2).put("setting_count",400).put("display",display).put("serial","private serial");
        validated=DiagnosticReport.validateDetails(runtime);equal(false,validated.has("serial"));
        equal("landscape",validated.getJSONObject("display").getString("orientation"));
        JSONObject metrics=new JSONObject().put("owner_known",true).put("elapsed_ms",12.5).put("pin",528).put("ssid","private network");
        JSONObject stage=new JSONObject().put("kind","hook_stage").put("phase","prepare").put("metrics",metrics);
        validated=DiagnosticReport.validateDetails(stage);equal(2,validated.getJSONObject("metrics").length());
        equal(false,validated.toString().contains("528"));equal(false,validated.toString().contains("private network"));
        stage.put("phase","private phase payload");equal(null,DiagnosticReport.validateDetails(stage));
        equal(null,DiagnosticReport.settingValue("clock_scale",null).opt("value"));
        equal(true,DiagnosticReport.settingValue("clock_scale",Float.NaN).getBoolean("invalid_type"));
        JSONObject event=ModuleDiagnostics.event("bigclock","native hook failed",new IllegalStateException("private exception"));
        event.put("details",runtime);validated=ModuleDiagnostics.validateEvent(event);
        equal("bigclock",validated.getString("source"));equal("runtime_applied",validated.getJSONObject("details").getString("kind"));
        equal(false,validated.toString().contains("private exception"));
        return checks;
    }
    public static void main(String[] args) throws Exception {System.out.println("DiagnosticReportCheck: "+run()+" checks passed");}
}
