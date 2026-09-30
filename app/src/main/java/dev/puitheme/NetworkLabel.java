// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
final class NetworkLabel {
    private NetworkLabel() {
    }

    static String normalize(String str) {
        if (str == null) {
            return "";
        }
        String strTrim = str.trim();
        String strReplace = strTrim.toUpperCase(Locale.ROOT).replace(" ", "").replace("_", "");
        if (strReplace.isEmpty() || strReplace.equals("UNKNOWN") || strReplace.equals("NONE") || strReplace.equals("NOSERVICE") || strReplace.equals("OUTOFSERVICE") || strReplace.equals("无服务")) {
            return "";
        }
        if (strReplace.startsWith("5G") || strReplace.startsWith("5.5G") || strReplace.startsWith("NR5G") || strReplace.startsWith("TIGO5G")) {
            return "5G";
        }
        if (strReplace.equals("NR") || strReplace.equals("NRSA") || strReplace.equals("NRNSA")
                || strReplace.equals("NR+")) {
            return "5G";
        }
        if (strReplace.startsWith("4G") || strReplace.startsWith("4.5G")
                || strReplace.startsWith("LTE") || strReplace.equals("4")) {
            return "4G";
        }
        if (strReplace.startsWith("3G") || strReplace.equals("3")
                || strReplace.equals("H") || strReplace.equals("H+")
                || strReplace.startsWith("HSPA") || strReplace.equals("UMTS")
                || strReplace.equals("WCDMA") || strReplace.equals("TDSCDMA")
                || strReplace.equals("TD-SCDMA") || strReplace.startsWith("EVDO")) {
            return "3G";
        }
        return strTrim;
    }
}
