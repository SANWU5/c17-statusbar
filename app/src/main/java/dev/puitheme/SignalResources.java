// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

/** Resource names carry the actual system level; never infer a level from connectivity. */
public final class SignalResources {
    private SignalResources() { }
    public static String moduleName(String resource, boolean single) {
        if (resource == null) return null;
        // Native O16/O17 Wi-Fi signal arrays contain active levels 0–4 only. The only
        // activity_wifi_none resource is a traffic-arrow state; disconnected Wi-Fi is Hidden.
        if (resource.matches("stat_signal_wifi_signal_[0-4](_os17)?")) return resource;
        if (resource.matches("stat_signal_(lte|soft)_signal_stacked_(primary|secondary)_([0-4]|noservice)")) {
            if (!single) return resource;
            if (resource.contains("_secondary_")) return "c17_signal_empty";
            return "c17_signal_single_" + resource.substring(resource.lastIndexOf('_') + 1);
        }
        if (resource.matches("stat_signal_(lte|soft)_signal_([0-4]|noservice)(_os17)?")
                || resource.matches("stat_sys_signal_[0-4](_fully)?")) {
            String normal = resource.replace("_os17", "").replace("_fully", "");
            return "c17_signal_single_" + normal.substring(normal.lastIndexOf('_') + 1);
        }
        // C17's independent LTE binder reads SIGNAL_STRENGTH(_OS17), whose
        // resources use signal_lte_single rather than lte_signal. Keep the
        // native level, including the no-voice variants selected by telephony.
        if (resource.matches("stat_signal_signal_lte_single_[0-4](_os17)?")
                || resource.matches("stat_signal_signal_novoice_[1-4](_os17)?")
                || resource.matches("stat_signal_single_novoice_0(_os17)?")) {
            String normal = resource.replace("_os17", "");
            return "c17_signal_single_" + normal.substring(normal.lastIndexOf('_') + 1);
        }
        if (resource.matches("stat_signal_(noservice_lte|soft_noservice)(_os17)?")
                || resource.equals("stat_signal_signal_null_lte"))
            return "c17_signal_single_noservice";
        if (resource.equals("stat_sys_signal_null") || resource.equals("stat_sys_signal_noservice"))
            return "c17_signal_single_noservice";
        return null;
    }
}
