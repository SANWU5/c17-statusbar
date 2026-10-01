// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Remembers the system's requested visibility so each hiding switch is reversible. */
final class NetworkBadgeControls {
    private final Map<View, State> views = new WeakHashMap<>();
    private final Handler main;
    private FeatureOptions options;
    private boolean applying;
    private static final class State {
        final String key;
        final boolean contextual;
        int requested;
        boolean hidden;
        State(String key, int requested,boolean contextual) { this.key=key; this.requested=requested;this.contextual=contextual; }
    }
    NetworkBadgeControls(Handler main) { this.main=main; }
    static String keyFor(String name) {
        if (name == null) return null;
        if (name.startsWith("mobile_type")) return "data_badge_hidden";
        switch(name) {
            case "wifi_left": case "wifi_left_tv": return "wifi_badge_hidden";
            case "wifi_in": case "wifi_out": case "wifi_inout":
            case "wifi_activity": case "wifi_activity_container": return "wifi_activity_hidden";
            case "mobile_in": case "mobile_out": case "mobile_inout":
            case "mobile_activity": case "mobile_activity_container":
            case "data_inout": case "data_inout_alone": return "data_activity_hidden";
            default: return null;
        }
    }
    private State state(View view) {
        if (view == null || view.getId() <= 0) return null;
        State state=views.get(view);
        if(state!=null) {
            if(!state.contextual||WifiPlacement.belongsToWifi(view))return state;
            views.remove(view);restore(view,state);return null;
        }
        try {
            if(!"com.android.systemui".equals(view.getResources().getResourcePackageName(view.getId())))return null;
            String name=view.getResources().getResourceEntryName(view.getId());
            String key=keyFor(name);boolean contextual=key==null;
            if(contextual)key=WifiPlacement.contextualKey(view,name);
            if(key==null)return null;
            state=new State(key,view.getVisibility(),contextual);views.put(view,state);return state;
        }catch(Exception ignored){return null;}
    }
    private boolean enabled(State state) {
        if(state==null)return false;
        String group=state.key.startsWith("wifi_")?"wifi":"data";
        return options==null || options.effective(group,state.key)
                || "data_badge_hidden".equals(state.key) && options.hideNetworkLabel();
    }
    int requestedVisibility(View view,int visibility) {
        State state=state(view);
        if(state==null || applying)return visibility;
        state.requested=visibility;
        state.hidden=enabled(state);
        return state.hidden?View.GONE:visibility;
    }
    private void restore(View view,State state) {
        if(state.hidden&&view.getVisibility()!=state.requested) {
            boolean wasApplying=applying;applying=true;
            try{view.setVisibility(state.requested);}finally{applying=wasApplying;}
        }
    }
    void idChanged(View view){State previous=views.remove(view);if(previous!=null)restore(view,previous);apply(view);}
    boolean suppressDraw(View view){return enabled(state(view));}
    void apply(View view) {
        State state=state(view);if(state==null)return;
        boolean hide=enabled(state);
        int target=hide?View.GONE:state.requested;
        if(view.getVisibility()!=target) {
            applying=true;try{view.setVisibility(target);}finally{applying=false;}
        }
        if(state.hidden!=hide){view.invalidate();view.requestLayout();}
        state.hidden=hide;
    }
    void tree(View view) {
        if(view==null)return;apply(view);
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)tree(group.getChildAt(i));
        }
    }
    void configure(FeatureOptions options) {
        this.options=options;
        if(Looper.myLooper()!=main.getLooper()){main.post(this::refresh);return;}
        refresh();
    }
    void refresh(){for(View view:new ArrayList<>(views.keySet()))if(view!=null)apply(view);}
}
