// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Collects the interface text that only exists at run time, for the translation tables in tools/i18n.
 *
 *   I18nDump catalog <out.json>              every title, description, section, unit and preset label of the settings catalog
 *   I18nDump plugin <plugin dir> <out.json>  every display string the plugin's main.js produces, over many form states
 *
 * Settings titles are composed in code ("启用" + title, title + " · 浅色背景"), and plugin forms change with their own
 * state, so a scan of string literals alone misses most of what a reader sees. Output: {"text": ["where", ...]}.
 */
public final class I18nDump {
    private static final Map<String, Set<String>> FOUND = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "catalog".equals(args[0])) {
            catalog();
            write(new File(args[1]));
        } else if (args.length == 3 && "plugin".equals(args[0])) {
            plugin(new File(args[1]));
            write(new File(args[2]));
        } else {
            throw new IllegalArgumentException("usage: I18nDump catalog <out> | plugin <dir> <out>");
        }
    }

    private static void add(String text, String where) {
        if (text == null) return;
        String trimmed = text.trim();
        if (trimmed.isEmpty() || !hasCjk(trimmed)) return;
        FOUND.computeIfAbsent(text, ignored -> new LinkedHashSet<>()).add(where);
    }

    static boolean hasCjk(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x3400 && c <= 0x9fff || c >= 0xf900 && c <= 0xfaff || c >= 0x3000 && c <= 0x303f || c >= 0xff00 && c <= 0xffef) return true;
        }
        return false;
    }

    private static void catalog() {
        Map<String, Object> none = Collections.emptyMap();
        for (String category : new String[]{SettingsCatalog.STATUSBAR, SettingsCatalog.NOTIFICATION, SettingsCatalog.CONTROL_CENTER,
                SettingsCatalog.LOCK_SCREEN, SettingsCatalog.OTHER}) {
            for (SettingsCatalog.NavigationSection section : SettingsCatalog.navigationSections(category))
                add(section.title, "catalog:section:" + category);
        }
        for (SettingsCatalog.Group group : SettingsCatalog.groups()) {
            String where = "catalog:" + group.id;
            add(group.title, where + ":title");
            add(group.description, where + ":description");
            add(SettingsOrganization.guide(group.id), where + ":guide");
            for (String category : new String[]{SettingsCatalog.STATUSBAR, SettingsCatalog.NOTIFICATION, SettingsCatalog.CONTROL_CENTER,
                    SettingsCatalog.LOCK_SCREEN, SettingsCatalog.OTHER}) {
                try { add(SettingsCatalog.conflictDescription(group.id, category), where + ":conflict"); } catch (RuntimeException ignored) { }
            }
            for (SettingsCatalog.DetailPage page : SettingsCatalog.detailPages(group)) {
                add(page.title, where + ":page:" + page.id);
                for (String mode : new String[]{"", PanelMode.CLASSIC, PanelMode.SEPARATE}) {
                    try { add(SettingsCatalog.detailModeReason(group, page, mode), where + ":page-reason"); } catch (RuntimeException ignored) { }
                }
            }
            for (SettingsCatalog.Item item : group.items) item(item, where + ":" + item.key, none);
        }
        for (SettingsCatalog.Item item : SettingsCatalog.allItems()) item(item, "catalog:" + item.groupId + ":" + item.key, none);
        for (Map.Entry<String, String> guide : SettingsOrganization.guides().entrySet()) add(guide.getValue(), "guide:" + guide.getKey());
    }

    private static void item(SettingsCatalog.Item item, String where, Map<String, Object> none) {
        add(item.title, where + ":title");
        add(item.description, where + ":description");
        add(item.section, where + ":section");
        add(item.unit, where + ":unit");
        for (String label : item.labels) add(label, where + ":label");
        try { add(SettingsCatalog.displayTitle(item, none), where + ":display"); } catch (RuntimeException ignored) { }
        try { add(SettingsCatalog.unavailableReason(item.key), where + ":unavailable"); } catch (RuntimeException ignored) { }
        try { add(SettingsCatalog.inactivePresentationReason(item, none), where + ":inactive"); } catch (RuntimeException ignored) { }
    }

    /** Explore the form: start empty, then switch every switch on, then try every option of every select. */
    private static void plugin(File directory) throws Exception {
        File manifestFile = new File(directory, "plugin.json");
        JSONObject manifest = new JSONObject(new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8));
        String id = manifest.getString("id");
        add(manifest.optString("name"), id + ":name");
        add(manifest.optString("description"), id + ":description");
        add(manifest.optString("usage"), id + ":usage");
        String source = new String(Files.readAllBytes(new File(directory, manifest.getString("entry")).toPath()), StandardCharsets.UTF_8);
        Set<String> seen = new LinkedHashSet<>();
        JSONObject state = new JSONObject();
        int evaluations = 0, failures = 0;
        for (int round = 0; round < 4; round++) {
            JSONArray ui = evaluate(source, state);
            evaluations++;
            if (ui == null) { failures++; break; }
            boolean grew = collect(ui, id) | rememberKeys(ui, seen);
            JSONObject next = new JSONObject(state.toString());
            for (int i = 0; i < ui.length(); i++) {
                JSONObject control = ui.optJSONObject(i);
                if (control != null && "switch".equals(control.optString("type"))) next.put(control.getString("key"), true);
            }
            if (!grew && next.similar(state)) break;
            state = next;
        }
        JSONArray all = evaluate(source, state);
        if (all != null) {
            for (int i = 0; i < all.length(); i++) {
                JSONObject control = all.optJSONObject(i);
                if (control == null || !"select".equals(control.optString("type"))) continue;
                JSONArray options = control.optJSONArray("options");
                if (options == null) continue;
                for (int n = 0; n < options.length(); n++) {
                    JSONObject trial = new JSONObject(state.toString()).put(control.getString("key"), options.getJSONObject(n).opt("value"));
                    JSONArray ui = evaluate(source, trial);
                    evaluations++;
                    if (ui == null) failures++; else collect(ui, id);
                }
            }
        }
        System.err.println(id + ": " + evaluations + " evaluations, " + failures + " over budget or failed, " + FOUND.size() + " strings");
    }

    private static boolean rememberKeys(JSONArray ui, Set<String> seen) {
        boolean grew = false;
        for (int i = 0; i < ui.length(); i++) {
            JSONObject control = ui.optJSONObject(i);
            if (control != null && control.has("key")) grew |= seen.add(control.optString("key"));
        }
        return grew;
    }

    private static JSONArray evaluate(String source, JSONObject state) {
        try { return CustomPluginEngine.evaluate(source, state).optJSONArray("ui"); }
        catch (Exception failed) { System.err.println("evaluation failed: " + failed); return null; }
    }

    private static boolean collect(JSONArray ui, String id) {
        int before = FOUND.size();
        for (int i = 0; i < ui.length(); i++) {
            JSONObject control = ui.optJSONObject(i);
            if (control == null) continue;
            String where = id + ":" + control.optString("key", control.optString("type"));
            add(control.optString("title"), where + ":title");
            add(control.optString("description"), where + ":description");
            add(control.optString("unit"), where + ":unit");
            add(control.optString("confirmation"), where + ":confirmation");
            add(control.optString("group"), where + ":group");
            JSONArray path = control.optJSONArray("groupPath");
            if (path != null) for (int n = 0; n < path.length(); n++) add(path.optString(n), where + ":groupPath");
            JSONArray options = control.optJSONArray("options");
            if (options != null) for (int n = 0; n < options.length(); n++) {
                JSONObject option = options.optJSONObject(n);
                if (option != null) add(option.optString("label"), where + ":option");
            }
        }
        return FOUND.size() > before;
    }

    private static void write(File out) throws Exception {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, Set<String>> entry : FOUND.entrySet()) json.put(entry.getKey(), new JSONArray(entry.getValue()));
        File parent = out.getAbsoluteFile().getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("cannot create " + parent);
        Files.write(out.toPath(), json.toString(1).getBytes(StandardCharsets.UTF_8));
        System.err.println("wrote " + FOUND.size() + " strings to " + out);
    }

    private I18nDump() { }
}
