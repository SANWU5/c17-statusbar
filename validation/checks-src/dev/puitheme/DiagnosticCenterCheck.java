package dev.puitheme;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class DiagnosticCenterCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but was " + actual);
    }
    private static void yes(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }

    private static JSONObject event(String time, String source, String level, String message) throws Exception {
        return new JSONObject().put("timestamp", time).put("source", source).put("level", level).put("message", message);
    }

    private static Bundle report(String... groups) {
        Bundle bundle = new Bundle();
        ArrayList<String> counts = new ArrayList<>(), complete = new ArrayList<>(), lazy = new ArrayList<>();
        for (String id : HookHealth.groupIds()) counts.add(id + "=" + (Arrays.asList(groups).contains(id) ? 0 : 3));
        for (String id : HookHealth.groupIds()) if (!Arrays.asList(groups).contains(id)) complete.add(id);
        lazy.add("statusbar_flow_layout=on"); lazy.add("device_gradient=failed");
        bundle.putStringArrayList(HookHealth.GROUPS, counts);
        bundle.putStringArrayList(HookHealth.COMPLETE, complete);
        bundle.putStringArrayList(HookHealth.LAZY, lazy);
        return bundle;
    }

    public static void main(String[] args) throws Exception {
        // Every installation group the module can report has a label and a stated effect.
        equal(33, HookHealth.groupIds().size());
        yes(HookHealth.knownGroup("icon_management"), "v3 native binding health is reported");
        for (String id : HookHealth.groupIds()) yes(!HookHealth.label(id).equals(id), "label for " + id);

        // The bundle SystemUI sends is validated and bounded before the app trusts it.
        Bundle dirty = report();
        dirty.getStringArrayList(HookHealth.GROUPS).add("<script>=1");
        dirty.getStringArrayList(HookHealth.GROUPS).add("wifi=99999999");
        for (int i = 0; i < 200; i++) dirty.getStringArrayList(HookHealth.LAZY).add("name" + i + "=on");
        Bundle copied = HookHealth.copy(dirty);
        equal(HookHealth.groupIds().size(), copied.getStringArrayList(HookHealth.GROUPS).size());
        yes(copied.getStringArrayList(HookHealth.LAZY).size() <= 64, "lazy list bounded");
        yes(HookHealth.copy(null) == null, "null report stays null");

        // A healthy report says so; a missing group names the settings it takes down.
        List<HookHealth.Finding> healthy = HookHealth.findings(report());
        equal("hooks_ok", healthy.get(0).id);
        List<HookHealth.Finding> broken = HookHealth.findings(report("status_flow", "wifi"));
        equal("hooks_missing", broken.get(0).id);
        yes(broken.get(0).detail.contains("状态栏排序") && broken.get(0).detail.contains("Wi-Fi"), "impact named");
        equal(HookHealth.Severity.WARN, broken.get(0).severity);
        equal(HookHealth.Severity.ERROR, HookHealth.findings(report("context")).get(0).severity);
        yes(HookHealth.findings(report()).stream().anyMatch(f -> f.id.equals("lazy_failed")), "failed lazy hook reported");
        equal("hooks_unknown", HookHealth.findings(null).get(0).id);
        // TILES and FLUID_MEDIA install from their own installers and are never marked complete: not a fault.
        equal("hooks_ok", HookHealth.findings(reportWithoutComplete()).get(0).id);
        yes(!HookHealth.knownGroup("qs_cards")&&!HookHealth.knownGroup("qs_small_tiles"), "plugin editing has no host hook groups");
        Bundle legacyCards = report();
        legacyCards.getStringArrayList(HookHealth.GROUPS).addAll(Arrays.asList("qs_cards=8","qs_small_tiles=3"));
        equal("hooks_ok",HookHealth.findings(legacyCards).get(0).id);
        yes(HookHealth.table(legacyCards).stream().noneMatch(row -> row[0].equals("qs_cards")||row[0].equals("qs_small_tiles")), "historical reports cannot revive retired groups");
        runtimeHealthChecks();

        // Events become rows; levels and sources are mapped, exceptions summarised without any message text.
        JSONObject exception = new JSONObject().put("causes", new JSONArray().put(new JSONObject().put("class", "java.lang.NoSuchMethodException")
                .put("stack", new JSONArray().put(new JSONObject().put("class", "dev.puitheme.StatusBarModule").put("method", "installX").put("line", 42)))));
        List<JSONObject> events = new ArrayList<>(Arrays.asList(
                event("2026-10-18T01:00:00.000Z", "hook", "info", "SystemUI hook groups after package-ready: all installed"),
                event("2026-10-18T01:00:01.000Z", "hook", "warn", "On-demand hook unavailable: device_gradient"),
                event("2026-10-18T01:00:02.000Z", "hook", "error", "Hook setup failed").put("exception", exception),
                event("2026-10-18T01:00:03.000Z", "hook", "error", "Hook setup failed").put("exception", exception),
                event("2026-10-18T01:00:04.000Z", "clock", "info", "状态栏时钟状态")
                        .put("details", new JSONObject().put("kind", "fact").put("topic", "clock").put("values", new JSONObject().put("reason", "ok")))));
        Map<String, Object> values = new HashMap<>();
        JSONObject app = new JSONObject().put("version_name", "1.8.3-beta.90").put("version_code", 183).put("expected_runtime", ModuleRuntimeStatus.BUILD_TOKEN);
        JSONObject packages = new JSONObject();
        DiagnosticCenter.Result result = DiagnosticCenter.analyze(events, report(), true, "LSPosed", values, app, packages, true);
        equal(5, result.entries.size());
        equal(2, result.errors); equal(1, result.warnings);
        equal("警告", result.entries.get(1).levelLabel());
        equal("挂钩", result.entries.get(1).sourceLabel);
        yes(result.entries.get(2).detail.contains("NoSuchMethodException") && result.entries.get(2).detail.contains("StatusBarModule.installX:42"), "exception summarised");
        yes(result.entries.get(4).detail.contains("reason=ok"), "fact values shown");
        JSONObject geometry = DiagnosticReport.validateDetails(new JSONObject().put("kind", "fact").put("topic", "qs_cards")
                .put("values", new JSONObject().put("event", "viewport").put("reason", "height")
                        .put("qs_rows", 6).put("qs_footer_top", 3564).put("qs_enabled", true)
                        .put("qs_user_known", false).put("ssid", "private-network")));
        yes(geometry != null, "control-center structural topic is admitted");
        equal(3564, geometry.getJSONObject("values").getInt("qs_footer_top"));
        equal(6, geometry.getJSONObject("values").getInt("qs_rows"));
        yes(geometry.getJSONObject("values").getBoolean("qs_enabled"), "structural boolean preserved");
        yes(!geometry.getJSONObject("values").getBoolean("qs_user_known"), "metadata availability is retained without a user identifier");
        yes(!geometry.toString().contains("private-network"), "unrelated text is excluded from geometry diagnostics");
        yes(result.findings.stream().anyMatch(f -> f.id.equals("log_errors") && f.detail.contains("2 次")), "repeated errors grouped");
        // Most urgent conclusion first.
        equal(HookHealth.Severity.WARN, result.findings.get(0).severity);

        // A verified live answer overrides a stale saved apply event that carries an older runtime token.
        List<JSONObject> stale = new ArrayList<>(events);
        stale.add(new JSONObject().put("timestamp", "2026-10-18T00:59:00.000Z").put("source", "setting").put("level", "info").put("message", "系统界面配置应用成功")
                .put("details", new JSONObject().put("kind", "runtime_applied").put("runtime", "c17-runtime-20250101-old").put("pid", 1)));
        DiagnosticCenter.Result live = DiagnosticCenter.analyze(stale, report(), true, "LSPosed", values, app, packages, true);
        yes(live.findings.stream().anyMatch(f -> f.id.equals("runtime_live") && f.severity == HookHealth.Severity.OK), "live verification reported");
        yes(live.findings.stream().noneMatch(f -> f.id.equals("runtime_token")), "stale token finding suppressed while the module answers");
        DiagnosticCenter.Result deadStale = DiagnosticCenter.analyze(stale, null, false, "未检测到模块激活", values, app, packages, true);
        yes(deadStale.findings.stream().anyMatch(f -> f.id.equals("runtime_token")), "stale token still explained when SystemUI does not answer");
        // An inactive module is the first thing said; logging off is explained, not blamed.
        DiagnosticCenter.Result inactive = DiagnosticCenter.analyze(new ArrayList<JSONObject>(), null, false, "未检测到模块激活", values, app, packages, false);
        equal("runtime_inactive", inactive.findings.get(0).id);
        equal(HookHealth.Severity.ERROR, inactive.findings.get(0).severity);
        yes(inactive.findings.stream().anyMatch(f -> f.id.equals("logging_off")), "logging off explained");
        DiagnosticCenter.Result quiet = DiagnosticCenter.analyze(new ArrayList<JSONObject>(), report(), true, "", values, app, packages, true);
        yes(quiet.findings.stream().anyMatch(f -> f.id.equals("log_empty")), "empty log explained");

        // The export reads as a report: numbered sections in a fixed order, hook table, timeline, no raw JSON.
        String text = DiagnosticCenter.exportText(result, new JSONObject().put("manufacturer", "OnePlus").put("model", "PJZ110").put("android", "17").put("sdk", 37),
                app, packages, report("wifi"), values, 1792290000000L);
        int one = text.indexOf("一、结论"), two = text.indexOf("二、设备与系统"), three = text.indexOf("三、挂钩状态"), four = text.indexOf("四、已保存的配置"), five = text.indexOf("五、近期事件");
        yes(text.startsWith("C17 状态栏 · 诊断报告") && one > 0 && one < two && two < three && three < four && four < five, "section order");
        yes(text.contains("Wi-Fi 图标") && text.contains("未就绪"), "hook table lists the missing group");
        yes(text.contains("OnePlus PJZ110"), "device line");
        yes(!text.contains("{\"") && !text.contains("report_schema"), "no raw JSON in the body");
        yes(text.contains("On-demand hook unavailable"), "timeline lists the warning");
        yes(DiagnosticCenter.pad("好", 4).length() == 3 && DiagnosticCenter.pad("ab", 4).length() == 4, "CJK counts as two columns");
        System.out.println("DiagnosticCenterCheck: " + checks + " checks passed");
    }

    private static void runtimeHealthChecks() {
        Bundle source=report();
        String[] counts={"custom_plugin_runtime_requested","custom_plugin_runtime_loaded","custom_plugin_runtime_hooks",
                "custom_plugin_runtime_pending","custom_plugin_runtime_retained_guards"};
        String[] totals={"custom_plugin_runtime_revision","custom_plugin_runtime_errors","custom_plugin_runtime_hits"};
        for(int i=0;i<counts.length;i++)source.putInt(counts[i],200+i);
        for(int i=0;i<totals.length;i++)source.putLong(totals[i],(long)Integer.MAX_VALUE+100L+i);
        source.putBoolean("custom_plugin_runtime_active",true);
        StringBuilder longError=new StringBuilder("NoClassDefFoundError: plugin Bridge; ");
        while(longError.length()<1400)longError.append("0123456789");
        source.putString("custom_plugin_runtime_last_error",longError.toString());
        Bundle copied=HookHealth.copy(source);
        for(int i=0;i<counts.length;i++)equal(200+i,copied.getInt(counts[i]));
        for(int i=0;i<totals.length;i++)equal((long)Integer.MAX_VALUE+100L+i,copied.getLong(totals[i]));
        yes(copied.getBoolean("custom_plugin_runtime_active"),"API2 active state survives copied live report");
        equal(longError.substring(0,1024),copied.getString("custom_plugin_runtime_last_error"));
        equal(longError.toString(),source.getString("custom_plugin_runtime_last_error"));
        for(String key:counts)source.putInt(key,-4);
        for(String key:totals)source.putLong(key,-9L);
        source.putBoolean("custom_plugin_runtime_active",false);
        Bundle bounded=HookHealth.copy(source);
        for(String key:counts)equal(0,bounded.getInt(key));
        for(String key:totals)equal(0L,bounded.getLong(key));
        yes(!bounded.getBoolean("custom_plugin_runtime_active",true),"copied API2 paused state stays false");
        Bundle legacy=HookHealth.copy(report());
        yes(!legacy.containsKey("custom_plugin_runtime_requested"),"legacy reports do not fabricate API2 runtime counts");
        equal(0,runtimeFindings(legacy).size());

        Bundle pending=runtimeReport(3,1,144,true,"");
        HookHealth.Finding loading=onlyRuntimeFinding(pending,"custom_plugin_runtime_pending");
        equal(HookHealth.Severity.INFO,loading.severity);
        yes(loading.title.contains("1/3") && loading.detail.contains("后台加载"),"pending finding explains partial activation without labelling it an execution failure");
        Bundle missingCount=report();missingCount.putInt("custom_plugin_runtime_requested",3);missingCount.putBoolean("custom_plugin_runtime_active",true);
        yes(onlyRuntimeFinding(missingCount,"custom_plugin_runtime_pending").title.contains("0/3"),"missing loaded count cannot advertise a ready runtime");

        Bundle failed=runtimeReport(3,1,144,true,longError.toString());
        HookHealth.Finding failure=onlyRuntimeFinding(failed,"custom_plugin_runtime_pending");
        equal(HookHealth.Severity.WARN,failure.severity);
        yes(failure.title.contains("1/3") && failure.detail.endsWith(longError.substring(0,1024)),"partial runtime failure includes the copied bounded error and actual loaded count");

        Bundle ready=runtimeReport(3,3,144,true,"old resource failure");ready.putLong("custom_plugin_runtime_errors",4L);
        HookHealth.Finding loaded=onlyRuntimeFinding(ready,"custom_plugin_runtime_ready");
        equal(HookHealth.Severity.INFO,loaded.severity);
        yes(loaded.detail.contains("3 个插件") && loaded.detail.contains("144 个受管理挂钩"),"ready finding uses API2 plugin and hook counts without the API1 64-hook cap");
        yes(!loaded.detail.contains("old resource failure") && loaded.detail.contains("实际交互"),"recovered runtime does not show historical error as a current failure or claim visual verification");

        HookHealth.Finding paused=onlyRuntimeFinding(runtimeReport(3,0,144,false,"old error"),"custom_plugin_runtime_paused");
        equal(HookHealth.Severity.INFO,paused.severity);
        yes(paused.detail.contains("清理") && !paused.detail.contains("old error"),"paused finding describes host cleanup rather than treating disabled runtimes as failed");
        equal(0,runtimeFindings(HookHealth.copy(runtimeReport(0,0,0,false,""))).size());
    }

    private static Bundle runtimeReport(int requested,int loaded,int hooks,boolean active,String error) {
        Bundle report=report();report.putInt("custom_plugin_runtime_requested",requested);
        report.putInt("custom_plugin_runtime_loaded",loaded);report.putInt("custom_plugin_runtime_hooks",hooks);
        report.putBoolean("custom_plugin_runtime_active",active);report.putString("custom_plugin_runtime_last_error",error);
        return report;
    }

    private static List<HookHealth.Finding> runtimeFindings(Bundle report) {
        List<HookHealth.Finding> runtimes=new ArrayList<>();
        for(HookHealth.Finding finding:HookHealth.findings(report))if(finding.id.startsWith("custom_plugin_runtime_"))runtimes.add(finding);
        return runtimes;
    }

    private static HookHealth.Finding onlyRuntimeFinding(Bundle report,String expectedId) {
        List<HookHealth.Finding> findings=runtimeFindings(HookHealth.copy(report));equal(1,findings.size());
        equal(expectedId,findings.get(0).id);return findings.get(0);
    }

    private static Bundle reportWithoutComplete() {
        Bundle bundle = new Bundle();
        ArrayList<String> counts = new ArrayList<>();
        for (String id : HookHealth.groupIds()) counts.add(id + "=" + (id.equals("tiles") || id.equals("fluid_media") ? 0 : 2));
        bundle.putStringArrayList(HookHealth.GROUPS, counts);
        bundle.putStringArrayList(HookHealth.LAZY, new ArrayList<String>());
        return bundle;
    }
}
