package dev.puitheme;

/** Resource names carry the actual system level; never infer a level from connectivity. */
public final class SignalResources {
    private SignalResources() { }
    public static String moduleName(String resource, boolean single) {
        if (resource == null) return null;
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
        if (resource.equals("stat_sys_signal_null") || resource.equals("stat_sys_signal_noservice"))
            return "c17_signal_single_noservice";
        return null;
    }
}
