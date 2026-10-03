// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Data-only, versioned icon pack contract. No class names, scripts or drawable XML. */
public final class IconPackManifest {
    public static final String FORMAT="c17-statusbar-icons";
    public static final int VERSION=1,NATIVE_FALLBACK_VERSION=2,MAX_BYTES=65536,MAX_ICONS=128;
    public final String name,author,license;
    public final int version;
    public final boolean nativeFallback;
    public final Map<String,String> icons;
    private IconPackManifest(String name,String author,String license,Map<String,String> icons,int version){
        this.name=name;this.author=author;this.license=license;
        this.version=version;this.nativeFallback=version==NATIVE_FALLBACK_VERSION;
        this.icons=Collections.unmodifiableMap(new LinkedHashMap<>(icons));
    }
    public static IconPackManifest parse(byte[] bytes)throws IOException{
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw new IOException("图标包 manifest.json 大小无效");
        try{
            JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(!FORMAT.equals(json.optString("format"))||!(json.opt("version") instanceof Number))throw new IOException("图标包规范版本不支持");
            double declared=json.getDouble("version");
            if(declared!=VERSION&&declared!=NATIVE_FALLBACK_VERSION)throw new IOException("图标包规范版本不支持");
            int version=(int)declared;
            if(version==NATIVE_FALLBACK_VERSION&&!"native".equals(json.opt("fallback")))throw new IOException("部分状态图标包须声明 fallback 为 native");
            if(version==VERSION&&json.has("fallback"))throw new IOException("原版本图标包不支持 fallback 字段");
            String name=text(json,"name",64,true),author=text(json,"author",128,false),license=text(json,"license",128,true);
            if(!"mask".equals(json.optString("render","mask")))throw new IOException("图标仅支持透明蒙版，render 必须为 mask");
            Object source=json.opt("icons");
            if(!(source instanceof JSONObject))throw new IOException("图标包缺少 icons 映射");
            JSONObject items=(JSONObject)source;
            if(items.length()==0||items.length()>MAX_ICONS)throw new IOException("图标包需包含 1–128 项图标");
            TreeMap<String,String> icons=new TreeMap<>();
            for(Iterator<String> it=items.keys();it.hasNext();){
                String key=it.next();Object value=items.opt(key);
                if(!validKey(key)||!(value instanceof String)||!validAsset((String)value))
                    throw new IOException("图标标识或资源路径无效："+key);
                icons.put(key,(String)value);
            }
            if(version==VERSION){
                requireFamily(icons,"wifi.",new String[]{"0","1","2","3","4","none"});
                requireFamily(icons,"cellular.",new String[]{"0","1","2","3","4","none"});
                requireFamily(icons,"battery.",batteryStates(""));
                requireFamily(icons,"battery.charging.",batteryStates(""));
            }
            return new IconPackManifest(name,author,license,icons,version);
        }catch(JSONException malformed){throw new IOException("图标包 manifest.json 格式错误",malformed);}
    }
    private static String text(JSONObject json,String key,int limit,boolean required)throws IOException{
        Object value=json.opt(key);String result=value instanceof String?((String)value).trim():"";
        if((required&&result.isEmpty())||result.length()>limit||result.matches("(?s).*\\p{Cntrl}.*"))
            throw new IOException("图标包 "+key+" 内容无效");
        return result;
    }
    private static String[] batteryStates(String prefix){
        String[] result=new String[11];for(int i=0;i<=10;i++)result[i]=prefix+(i*10);return result;
    }
    private static void requireFamily(Map<String,String> icons,String prefix,String[] states)throws IOException{
        boolean declared=false;
        for(String key:icons.keySet())if(key.startsWith(prefix)){declared=true;break;}
        if(!declared)return;
        for(String state:states)if(!icons.containsKey(prefix+state))
            throw new IOException("图标状态不完整，缺少 "+prefix+state);
    }
    public static boolean validKey(String key){
        return key!=null&&(key.matches("hint\\.[a-z][a-z0-9_]{0,47}(?:\\.(?:on|off))?")
                ||key.matches("(?:wifi|cellular)\\.(?:[0-4]|none)")
                ||key.matches("battery\\.(?:charging\\.)?(?:0|[1-9]0|100)"));
    }
    public static boolean validAsset(String path){
        return path!=null&&path.matches("assets/[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}\\.(?:png|webp)");
    }
    public static boolean validRevision(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    public String asset(String key){return icons.get(key);}
    public String hint(String slot,boolean on){
        if(slot==null||!slot.matches("[a-z][a-z0-9_]{0,47}"))return null;
        String value=icons.get("hint."+slot+"."+(on?"on":"off"));
        return value!=null?value:icons.get("hint."+slot);
    }
    public String wifi(int level,boolean connected){return icons.get("wifi."+(connected?Math.max(0,Math.min(4,level)):"none"));}
    public String cellular(int level,boolean connected){return icons.get("cellular."+(connected?Math.max(0,Math.min(4,level)):"none"));}
    public String battery(int level,boolean charging){
        int bucket=Math.max(0,Math.min(100,level))/10*10;
        String value=charging?icons.get("battery.charging."+bucket):null;
        return value!=null?value:charging&&nativeFallback?null:icons.get("battery."+bucket);
    }
    /** Canonical representation allows a content-addressed directory and reproducible sample packs. */
    public byte[] bytes()throws IOException{
        try{
            JSONObject json=new JSONObject();json.put("format",FORMAT);json.put("version",version);
            if(nativeFallback)json.put("fallback","native");
            json.put("name",name);json.put("author",author);json.put("license",license);json.put("render","mask");
            JSONObject items=new JSONObject();for(Map.Entry<String,String> icon:icons.entrySet())items.put(icon.getKey(),icon.getValue());
            json.put("icons",items);return json.toString().getBytes(StandardCharsets.UTF_8);
        }catch(JSONException impossible){throw new IOException(impossible);}
    }
}
