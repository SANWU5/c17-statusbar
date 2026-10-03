package io.github.libxposed.service;

import android.content.SharedPreferences;

/** Synthetic service contract/counters; production is compiled against the official AAR. */
public final class XposedService {
    public static final long PROP_CAP_REMOTE=2L;
    public final SharedPreferences preferences;
    public long properties=PROP_CAP_REMOTE;
    public int requests;
    public boolean unavailable;
    public XposedService(SharedPreferences preferences){this.preferences=preferences;}
    public long getFrameworkProperties(){return properties;}
    public SharedPreferences getRemotePreferences(String group){
        requests++;if(unavailable)throw new IllegalStateException("synthetic service unavailable");return preferences;
    }
    public int getApiVersion(){return 102;}
    public String getFrameworkName(){return "LSPosed model";}
    public String getFrameworkVersion(){return "synthetic";}
    public long getFrameworkVersionCode(){return 1L;}
}
