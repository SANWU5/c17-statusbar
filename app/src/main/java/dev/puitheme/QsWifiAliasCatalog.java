// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONArray;

/** Only SSID names from saved configurations or an observed connection; no credentials or BSSID. */
public final class QsWifiAliasCatalog {
    public static final String REPORT="report_qs_wifi_names";
    private static final String PREFS="qs_wifi_alias_known",NAMES="names",CURRENT="current";
    private static String lastReport="";
    private static long savedAt;private static Set<String> cachedSaved=new LinkedHashSet<>();
    private QsWifiAliasCatalog(){}
    public static String connected(Context context){
        try{Object manager=context.getSystemService("wifi"),info=invoke(manager,"getConnectionInfo");return QsWifiAlias.ssid((String)invoke(info,"getSSID"));}
        catch(ReflectiveOperationException|RuntimeException unavailable){return null;}
    }
    private static Set<String> saved(Context context){
        Set<String> names=new LinkedHashSet<>();try{
            Object manager=context.getSystemService("wifi"),configurations=invoke(manager,"getConfiguredNetworks");
            if(configurations instanceof Iterable)for(Object config:(Iterable<?>)configurations){
                Object raw=QsWifiAlias.value(config,"SSID");String ssid=raw instanceof String?QsWifiAlias.ssid((String)raw):null;
                if(ssid!=null&&names.size()<QsWifiAlias.MAX_NETWORKS)names.add(ssid);
            }
        }catch(ReflectiveOperationException|RuntimeException unavailable){/* Android may restrict configured-network access. */}return names;
    }
    /** Runs in SystemUI, whose existing Wi-Fi privileges may expose saved configurations. */
    public static void report(Context context,String connected){
        if(context==null)return;Set<String> names;
        synchronized(QsWifiAliasCatalog.class){long now=System.currentTimeMillis();if(now-savedAt>30000||savedAt==0){cachedSaved=saved(context);savedAt=now;}names=new LinkedHashSet<>(cachedSaved);}
        String current=QsWifiAlias.networkKey(connected);if(current!=null)names.add(current);
        String fingerprint=current+"\n"+new JSONArray(names).toString();synchronized(QsWifiAliasCatalog.class){if(fingerprint.equals(lastReport))return;}
        Bundle data=new Bundle();data.putString(CURRENT,current);data.putStringArrayList(NAMES,new ArrayList<>(names));
        try{Bundle result=context.getContentResolver().call(Uri.parse(StatusBarSettings.CONTENT_URI),REPORT,null,data);
            if(result!=null&&result.getBoolean("ok",false))synchronized(QsWifiAliasCatalog.class){lastReport=fingerprint;}
        }catch(RuntimeException unavailable){/* A later network callback retries delivery. */}
    }
    public static Bundle record(Context context,Bundle data){
        if(context==null||data==null)throw new IllegalArgumentException("缺少网络名称");
        SharedPreferences preferences=preferences(context);Set<String> names=new LinkedHashSet<>(read(context));
        ArrayList<String> incoming=data.getStringArrayList(NAMES);if(incoming!=null)for(String value:incoming){String name=QsWifiAlias.networkKey(value);if(name!=null)names.add(name);}
        String current=QsWifiAlias.networkKey(data.getString(CURRENT));if(current!=null){names.remove(current);names.add(current);}
        while(names.size()>QsWifiAlias.MAX_NETWORKS)names.remove(names.iterator().next());
        boolean ok=preferences.edit().putString(NAMES,new JSONArray(names).toString()).putString(CURRENT,current==null?"":current).commit();
        Bundle answer=new Bundle();answer.putBoolean("ok",ok);return answer;
    }
    public static ArrayList<String> read(Context context){
        Set<String> names=new LinkedHashSet<>();try{
            JSONArray array=new JSONArray(preferences(context).getString(NAMES,"[]"));for(int i=0;i<array.length()&&names.size()<QsWifiAlias.MAX_NETWORKS;i++){
                String name=QsWifiAlias.networkKey(array.optString(i,null));if(name!=null)names.add(name);
            }
        }catch(Exception malformed){/* Only this independent list is ignored. */}
        names.addAll(saved(context));ArrayList<String> sorted=new ArrayList<>(names);java.util.Collections.sort(sorted);return sorted;
    }
    public static String current(Context context){String name=QsWifiAlias.networkKey(preferences(context).getString(CURRENT,""));return name==null?"":name;}
    private static SharedPreferences preferences(Context context){return context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static Object invoke(Object owner,String name)throws ReflectiveOperationException{if(owner==null)return null;Method method=owner.getClass().getMethod(name);method.setAccessible(true);return method.invoke(owner);}
}
