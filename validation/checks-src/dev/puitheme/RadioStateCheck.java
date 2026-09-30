package dev.puitheme;

/** Covers handovers, lingering Wi-Fi networks, VPNs, local-only Wi-Fi and model hydration. */
public final class RadioStateCheck {
    private static int checks;
    private static void equal(Object expected, Object actual, String context) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(context + ": " + expected + " != " + actual);
    }
    public static void main(String[] args) {
        equal(false, RadioState.wifiInUse(false, true, true), "Wi-Fi disabled overrides stale native model");
        equal(false, RadioState.wifiInUse(true, false, true), "Wi-Fi hidden overrides lingering route capabilities");
        equal(false, RadioState.wifiInUse(true, false, false), "Cellular after walking out of Wi-Fi coverage");
        equal(true, RadioState.wifiInUse(true, true, false), "Visible local-only Wi-Fi follows native status icon");
        equal(true, RadioState.wifiInUse(true, true, true), "Normal Wi-Fi");
        equal(true, RadioState.wifiInUse(true, null, true), "Unknown native model uses default Wi-Fi, including VPN transport");
        equal(false, RadioState.wifiInUse(true, null, false), "Unknown model with cellular or VPN over cellular");
        equal(false, RadioState.wifiInUse(false, null, true), "Off radio with stale default Wi-Fi");

        for (boolean normalize : new boolean[]{false, true}) {
            equal("", RadioState.cellularLabel(false, false, false, "5GA", "4G", normalize), "Module label master disabled");
            equal("", RadioState.cellularLabel(true, true, false, "5GA", "4G", normalize), "Wi-Fi icon remains visible");
            equal("", RadioState.cellularLabel(true, false, true, "5GA", "4G", normalize), "Airplane mode");
            equal(normalize ? "5G" : "5GA", RadioState.cellularLabel(true, false, false, " 5GA ", "4G", normalize), "Native model wins");
            for (String absent : new String[]{null, "", " ", "Unknown", "None", "NoService", "无服务"}) {
                equal("4G", RadioState.cellularLabel(true, false, false, absent, "4G", normalize), "Current active-data fallback");
                equal("", RadioState.cellularLabel(true, false, false, absent, "", normalize), "No current cellular service");
            }
        }
        boolean[] enabled={true,true,true,true,true,false,true};
        Boolean[] nativeIcon={true,false,false,null,true,false,false};
        boolean[] defaultWifi={true,true,false,false,false,false,false};
        String[] expected={"","5G","5G","5G","","5G","5G"};
        for (int round=0;round<12;round++) for(int step=0;step<enabled.length;step++) {
            boolean wifi=RadioState.wifiInUse(enabled[step],nativeIcon[step],defaultWifi[step]);
            equal(expected[step],RadioState.cellularLabel(true,wifi,false,null,"5G",true),"Repeated handover step " + step);
        }
        equal("",RadioState.cellularLabel(true,false,false,null,null,true),"Removed SIM cannot reuse old label");
        equal("LTE-A",RadioState.cellularLabel(true,false,false,null,"LTE-A",false),"Normalization switch off during hydration");
        equal("4G",RadioState.cellularLabel(true,false,false,null,"LTE-A",true),"Normalization switch on during hydration");
        System.out.println(checks + " checks passed (Wi-Fi handover, native icon authority, default routes, model fallback)");
    }
}
