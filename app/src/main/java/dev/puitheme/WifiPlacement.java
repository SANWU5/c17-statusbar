// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/** Collapses hidden Wi-Fi decorations before measurement, keeping the native glyph as the layout anchor. */
final class WifiPlacement {
    private WifiPlacement() { }

    static boolean isWifiOwner(View view) {
        if(!(view instanceof ViewGroup))return false;
        String type=view.getClass().getName();
        boolean typeMatches=type.equals("com.oplus.systemui.statusbar.phone.signal.widget.OplusModernStatusBarWifiView")
                ||type.equals("com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView")
                ||type.equals("com.android.systemui.statusbar.StatusBarWifiView");
        if(!typeMatches||view.getId()<=0)return false;
        try {
            if(!"com.android.systemui".equals(view.getResources().getResourcePackageName(view.getId())))return false;
            String name=view.getResources().getResourceEntryName(view.getId());
            return name.equals("wifi_combo")||name.equals("wifi_group");
        } catch(RuntimeException unavailable) {return false;}
    }

    static boolean belongsToWifi(View view) {
        for(int depth=0;view!=null&&depth<12;depth++) {
            if(isWifiOwner(view))return true;
            ViewParent parent=view.getParent();view=parent instanceof View?(View)parent:null;
        }
        return false;
    }

    static String contextualKey(View view,String name) {
        if((name.equals("inout_container")||name.equals("wifi_signal_spacer"))&&belongsToWifi(view))
            return "wifi_activity_hidden";
        return null;
    }

    /** Called before native Wi-Fi measurement, so late or inlined visibility updates cannot move its glyph. */
    static void beforeMeasure(View view,NetworkBadgeControls badges) {
        if(isWifiOwner(view)&&badges!=null)badges.tree(view);
    }
}
