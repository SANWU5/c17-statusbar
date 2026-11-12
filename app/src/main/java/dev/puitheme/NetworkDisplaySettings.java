// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.util.Map;

/** One display decision for the existing text/badge controls; saved styling is never rewritten. */
public final class NetworkDisplaySettings {
    public static final String WIFI_HIDDEN="wifi_hidden";
    public static final String TEXT="text",BADGE="badge",HIDDEN="hidden";
    private NetworkDisplaySettings(){ }
    public static String mode(Map<String,?> values){
        boolean text=values!=null&&Boolean.TRUE.equals(values.get("label_enabled"));
        return !text?BADGE:Boolean.TRUE.equals(values.get(StatusBarSettings.LABEL_HIDDEN))?HIDDEN:TEXT;
    }
    public static String mode(Bundle values){
        return values==null||!values.getBoolean("label_enabled",false)?BADGE
                :values.getBoolean(StatusBarSettings.LABEL_HIDDEN,false)?HIDDEN:TEXT;
    }
    public static boolean validMode(String value){return TEXT.equals(value)||BADGE.equals(value)||HIDDEN.equals(value);}
    /** The old signal-page badge suppression cannot override an explicit return
     * to badge display. Keep that old preference intact for portable backups. */
    public static Bundle runtime(Bundle raw){
        Bundle result=raw==null?new Bundle():new Bundle(raw);
        if(!SafetyMode.enabled(result)&&BADGE.equals(mode(result)))result.putBoolean("data_badge_hidden",false);
        return result;
    }
}
