// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Which settings a share, an export or an import takes: every page of the settings is one selectable section, so "customise" is a
 * checklist of pages instead of a list of hundreds of keys. A key belongs to exactly one section (the first page that shows it);
 * portable keys no page shows (internal switches, bases and versions) form one last section that only an "everything" choice or
 * an explicit tick takes along. Pure data: nothing here reads or writes preferences. */
public final class ConfigScope {
    /** The section of portable keys that belong to no page. */
    public static final String OTHER_ID = "__other";

    public static final class Section {
        public final String id, title, category;
        private final Set<String> keys;
        private Section(String id, String title, String category, Set<String> keys) {
            this.id = id; this.title = title; this.category = category;
            this.keys = Collections.unmodifiableSet(keys);
        }
        public Set<String> keys() { return keys; }
        public int count() { return keys.size(); }
        /** The page title with the screen it lives on, as the checklist shows it. */
        public String label() { return category.isEmpty() ? title : title + "（" + category + "）"; }
    }

    private static volatile List<Section> sections;

    private ConfigScope() {}

    static String categoryLabel(String category) {
        if (SettingsCatalog.STATUSBAR.equals(category)) return "状态栏";
        if (SettingsCatalog.NOTIFICATION.equals(category)) return "通知栏";
        if (SettingsCatalog.CONTROL_CENTER.equals(category)) return "控制中心";
        if (SettingsCatalog.LOCK_SCREEN.equals(category)) return "锁屏";
        return "";
    }

    /** All sections in the order the pages are listed, the unpaged remainder last. Only keys a configuration file can carry count. */
    public static List<Section> sections() {
        List<Section> built = sections;
        if (built != null) return built;
        Map<String, ConfigTransfer.Type> portable = ConfigTransfer.types();
        Set<String> claimed = new LinkedHashSet<>();
        List<Section> result = new ArrayList<>();
        for (SettingsCatalog.Group group : SettingsCatalog.groups()) {
            Set<String> keys = new LinkedHashSet<>();
            List<String> candidates = new ArrayList<>();
            if (group.masterKey != null && !group.masterKey.isEmpty()) candidates.add(group.masterKey);
            for (SettingsCatalog.Item item : group.items) {
                candidates.add(item.key);
                if (!item.alphaKey.isEmpty()) candidates.add(item.alphaKey);
            }
            // Retired horizontal values keep their original share page. The new layout page
            // never takes them along or turns a partial import into a conversion of old geometry.
            switch (group.id) {
                case "clock": candidates.add(StatusBarSettings.CLOCK_OFFSET_X);
                    candidates.add(ClockLayoutSettings.DUAL_OFFSET_X); candidates.add(ClockLayoutSettings.MARGIN_ENABLED);
                    candidates.add(ClockLayoutSettings.MARGIN_START); candidates.add(ClockLayoutSettings.MARGIN_END); break;
                case "speed": candidates.add(StatusBarSettings.SPEED_OFFSET_X); candidates.add(SpeedPosition.POSITION);
                    candidates.add(SpeedPosition.SAFE_GAP_ENABLED); candidates.add(SpeedPosition.SAFE_GAP); candidates.add(SpeedPosition.EDGE_RESERVE); break;
                case "data": candidates.add(StatusBarSettings.DATA_OFFSET_X); break;
                case "wifi": candidates.add(StatusBarSettings.WIFI_OFFSET_X); break;
                case "label": candidates.add(StatusBarSettings.LABEL_OFFSET_X); break;
                case "network": candidates.add(StatusBarSettings.WIFI_OFFSET_X); candidates.add(StatusBarSettings.LABEL_OFFSET_X);
                    candidates.add(StatusBarSettings.SLOT_WIDTH); break; // Historical width stays in its original partial backups.
                case "battery": candidates.add(StatusBarSettings.BATTERY_OFFSET_X); break;
                case "notification_icons": candidates.add(NotificationIconArea.X); break;
                case "status_hint_icons": candidates.add(NativeStatusIcons.X); break;
                default: break;
            }
            // The page's feature switches (what makes its values count) travel with the page, shown as items or not.
            for (Map.Entry<String, String> owner : FeatureOptions.GROUP_BY_KEY.entrySet())
                if (!"network".equals(group.id) && group.id.equals(owner.getValue())
                        || "network".equals(group.id) && ("wifi".equals(owner.getValue()) || "label".equals(owner.getValue())))
                    candidates.add(owner.getKey());
            for (String key : candidates)
                if (portable.containsKey(key) && claimed.add(key)) keys.add(key);
            if (!keys.isEmpty()) result.add(new Section(group.id, group.title, categoryLabel(group.category), keys));
        }
        Set<String> rest = new LinkedHashSet<>();
        for (String key : portable.keySet()) if (!claimed.contains(key)) rest.add(key);
        if (!rest.isEmpty()) result.add(new Section(OTHER_ID, "其他设置", "", rest));
        built = Collections.unmodifiableList(result);
        sections = built;
        return built;
    }

    public static Section section(String id) {
        for (Section section : sections()) if (section.id.equals(id)) return section;
        // Previously saved export selections retain their exact old subset,
        // while the current checklist exposes one actual network page.
        if("wifi".equals(id)||"label".equals(id)||"native_network_badge".equals(id)){
            Section network=null;for(Section section:sections())if("network".equals(section.id)){network=section;break;}
            if(network!=null){Set<String> keys=new LinkedHashSet<>();
                for(String key:network.keys)if(id.equals(SettingsCatalog.networkOwner(key)))keys.add(key);
                if(!keys.isEmpty())return new Section(id,"wifi".equals(id)?"Wi-Fi 图标":"label".equals(id)?"网络制式文字":"原生角标调整","状态栏",keys);
            }
        }
        return null;
    }

    /** Every key of the chosen sections; unknown ids are ignored. */
    public static Set<String> keys(Collection<String> ids) {
        Set<String> result = new LinkedHashSet<>();
        if (ids == null) return result;
        for (Section section : sections()) if (ids.contains(section.id)) result.addAll(section.keys);
        for(String legacy:new String[]{"wifi","label","native_network_badge"})if(ids.contains(legacy)){
            Section section=section(legacy);if(section!=null)result.addAll(section.keys);
        }
        return result;
    }

    /** The sections that hold at least one of these keys (what a file actually carries), with the number of those keys. */
    public static Map<Section, Integer> present(Collection<String> available) {
        Map<Section, Integer> result = new LinkedHashMap<>();
        if (available == null) return result;
        for (Section section : sections()) {
            int count = 0;
            for (String key : section.keys) if (available.contains(key)) count++;
            if (count > 0) result.put(section, count);
        }
        return result;
    }
}
