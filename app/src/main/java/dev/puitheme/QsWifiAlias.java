// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.os.Bundle;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import org.json.JSONObject;

/** Display-only copies for connected Wi-Fi tiles. Network state and configurations remain native. */
public final class QsWifiAlias {
    public static final String GROUP="qs_wifi_alias",ENABLED="qs_wifi_alias_enabled",MODE="qs_wifi_alias_mode",
            UNIFIED="qs_wifi_alias_unified_text",RULES="qs_wifi_alias_rules";
    public static final int MAX_ALIAS=32,MAX_NETWORKS=128;
    private final Map<View,Binding> views=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object,DisplaySource> displaySources=Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<Boolean> delivering=new ThreadLocal<>();
    private volatile boolean enabled,released;private volatile String mode="per_network",unified="",ssid;
    private volatile Map<String,String> aliases=Collections.emptyMap();

    public void configure(Bundle settings){
        released=false;enabled=settings!=null&&!SafetyMode.enabled(settings)&&settings.getBoolean(ENABLED,false);
        mode=settings==null?"per_network":settings.getString(MODE,"per_network");
        unified=settings==null?"":settings.getString(UNIFIED,"");
        try{aliases=parse(settings==null?"{}":settings.getString(RULES,"{}"));}
        catch(IllegalArgumentException invalid){aliases=Collections.emptyMap();}
        if(aliasError(unified)!=null)unified="";
        refresh();
    }
    public void currentNetwork(String value){String next=networkKey(value);if(java.util.Objects.equals(ssid,next))return;ssid=next;refresh();}
    /** A settings refresh may seed a readable connection, but cannot declare a disconnect. */
    public void seedNetwork(String value){String next=networkKey(value);if(next!=null)currentNetwork(next);}
    /** Preserve provenance when a native view clones our display state before posting it. */
    public void copiedState(Object state,Object copy){
        if(state==null||copy==null||state==copy||state.getClass()!=copy.getClass())return;
        DisplaySource source=displaySources.get(state);if(source==null)return;
        try{
            Object from=value(state,"label"),to=value(copy,"label");
            if(from instanceof CharSequence&&to instanceof CharSequence&&source.alias.contentEquals((CharSequence)from)
                    &&source.alias.contentEquals((CharSequence)to))displaySources.put(copy,source);
        }catch(ReflectiveOperationException|RuntimeException unavailable){/* Only known display copies carry provenance. */}
    }
    /** Native views can forward our display copy to a second, possibly asynchronous, entry. */
    private Object source(Object state){
        DisplaySource source=displaySources.get(state);if(source==null)return state;
        try{
            Object label=value(state,"label");if(!(label instanceof CharSequence)||!source.alias.contentEquals((CharSequence)label))return state;
            Object original=copy(state);if(original==null)return state;
            field(original.getClass(),"label").set(original,source.label);return original;
        }catch(ReflectiveOperationException|RuntimeException unavailable){return state;}
    }
    private static final class DisplaySource{
        final CharSequence label;final String alias;DisplaySource(CharSequence label,String alias){this.label=label;this.alias=alias;}
    }
    /** The hook supplies the native state before the view makes its own display copy. */
    public Object displayState(View owner,Object nativeState,Method event){
        if(nativeState==null)return null;Object original=source(nativeState);
        if(released)return original;
        if(Boolean.TRUE.equals(delivering.get())||owner==null)return nativeState;
        nativeState=original;
        try{if(!"wifi".equals(value(nativeState,"spec"))){views.remove(owner);return nativeState;}}
        catch(ReflectiveOperationException|RuntimeException unavailable){views.remove(owner);return nativeState;}
        Object baseline=copy(nativeState);if(baseline==null){views.remove(owner);return nativeState;}
        views.put(owner,new Binding(baseline,event));return replacement(nativeState);
    }
    Object replacement(Object nativeState){
        if(nativeState!=null)nativeState=source(nativeState);
        if(!enabled||ssid==null||nativeState==null)return nativeState;
        try{
            if(!"wifi".equals(value(nativeState,"spec"))||!Integer.valueOf(2).equals(value(nativeState,"state"))
                    ||!Boolean.TRUE.equals(value(nativeState,"value")))return nativeState;
            Object nativeLabel=value(nativeState,"label");
            if(!(nativeLabel instanceof CharSequence)||!ssid.equals(nativeLabel.toString())&&!ssid.equals(ssid(nativeLabel.toString())))return nativeState;
            String alias="unified".equals(mode)?unified:"per_network".equals(mode)?aliases.get(ssid):null;
            if(alias==null||alias.isEmpty())return nativeState;
            Object display=copy(nativeState);if(display==null)return nativeState;
            Field label=field(display.getClass(),"label");if(!CharSequence.class.isAssignableFrom(label.getType()))return nativeState;
            label.set(display,alias);displaySources.put(display,new DisplaySource((CharSequence)nativeLabel,alias));return display;
        }catch(ReflectiveOperationException|RuntimeException unavailable){return nativeState;}
    }
    private void refresh(){
        java.util.List<Map.Entry<View,Binding>> snapshot=new ArrayList<>();
        synchronized(views){for(Map.Entry<View,Binding> entry:views.entrySet())snapshot.add(new java.util.AbstractMap.SimpleImmutableEntry<>(entry));}
        for(Map.Entry<View,Binding> entry:snapshot){
            View view=entry.getKey();Binding binding=entry.getValue();if(view==null)continue;
            view.post(()->{if(views.get(view)!=binding)return;delivering.set(true);
                try{binding.event.invoke(view,replacement(binding.state));}
                catch(ReflectiveOperationException|RuntimeException unavailable){/* The next native binding remains authoritative. */}
                finally{delivering.remove();if(released&&views.get(view)==binding)views.remove(view);}});
        }
    }
    public void detached(View view){views.remove(view);}
    public void release(){enabled=false;released=true;refresh();ssid=null;aliases=Collections.emptyMap();}
    private static final class Binding{final Object state;final Method event;Binding(Object state,Method event){this.state=state;this.event=event;event.setAccessible(true);}}
    private static Object copy(Object state){
        try{Method method=state.getClass().getMethod("copy");Object result=method.invoke(state);return result!=null&&result!=state&&result.getClass()==state.getClass()?result:null;}
        catch(ReflectiveOperationException|RuntimeException unavailable){return null;}
    }
    static Object value(Object owner,String name)throws ReflectiveOperationException{return field(owner.getClass(),name).get(owner);}
    private static Field field(Class<?> type,String name)throws ReflectiveOperationException{
        for(Class<?> current=type;current!=null;current=current.getSuperclass())try{Field f=current.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(name);
    }
    /** SSID identity preserves spaces and case. Quoted platform representations share one key. */
    public static String ssid(String value){
        if(value==null)return null;if(value.length()>=2&&value.charAt(0)=='"'&&value.charAt(value.length()-1)=='"')value=value.substring(1,value.length()-1);
        return networkKey(value);
    }
    /** A key already obtained from the platform keeps literal quotes, spaces and case. */
    public static String networkKey(String value){
        if(value==null)return null;
        if(value.isEmpty()||value.equalsIgnoreCase("<unknown ssid>")||value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32)return null;
        for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))return null;return value;
    }
    public static String aliasError(String text){
        if(text==null)return "请输入显示名称";
        if(text.codePointCount(0,text.length())>MAX_ALIAS)return "显示名称最多 32 个字符";
        for(int i=0;i<text.length();i++)if(Character.isISOControl(text.charAt(i)))return "显示名称不能包含换行或控制字符";
        if(!text.isEmpty()&&text.trim().isEmpty())return "显示名称不能只有空格";return null;
    }
    public static Map<String,String> parse(String json){
        try{
            if(json==null||json.length()>32768)throw new IllegalArgumentException("WiFi 名称配置过大");
            JSONObject object=new JSONObject(json);if(object.length()>MAX_NETWORKS)throw new IllegalArgumentException("最多设置 128 个网络");
            Map<String,String> result=new LinkedHashMap<>();Iterator<String> keys=object.keys();while(keys.hasNext()){
                String key=keys.next(),network=networkKey(key);Object value=object.get(key);
                if(network==null||!network.equals(key)||!(value instanceof String)||aliasError((String)value)!=null)throw new IllegalArgumentException("WiFi 名称配置无效");
                if(!((String)value).isEmpty())result.put(network,(String)value);
            }return Collections.unmodifiableMap(result);
        }catch(org.json.JSONException invalid){throw new IllegalArgumentException("WiFi 名称配置无效",invalid);}
    }
    public static String withAlias(String json,String network,String alias){
        String canonical=networkKey(network);if(canonical==null)throw new IllegalArgumentException("无法识别这个 WiFi");
        String error=aliasError(alias);if(error!=null)throw new IllegalArgumentException(error);
        Map<String,String> result=new LinkedHashMap<>(parse(json));if(alias.isEmpty())result.remove(canonical);else result.put(canonical,alias);
        if(result.size()>MAX_NETWORKS)throw new IllegalArgumentException("最多设置 128 个网络");return new JSONObject(result).toString();
    }
}
