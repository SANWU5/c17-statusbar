// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A configuration-only immutable lookup. Native package identity is supplied by icon events. */
public final class NotificationIconOverrides {
    public static final String MASTER="notification_icon_overrides_enabled",RULES="notification_icon_overrides_rules";
    public static final int MAX_RULES=128,MAX_CODE_POINTS=8,MAX_JSON_LENGTH=32768;
    public static final String TYPE_TEXT="text",TYPE_APP_ICON="appIcon";
    public static Map<String,Boolean> booleanDefaults(){return Collections.singletonMap(MASTER,false);}
    public static Map<String,String> stringDefaults(){return Collections.singletonMap(RULES,"[]");}
    public static final class Rule {
        public final String packageName,text,type,sourcePackage;public final boolean enabled;
        public Rule(String packageName,String text,boolean enabled){
            this(packageName,TYPE_TEXT,text,"",enabled);
        }
        public Rule(String packageName,String type,String text,String sourcePackage,boolean enabled){
            validatePackage(packageName,"应用包名格式不正确");
            if(TYPE_TEXT.equals(type)){
                validateText(text);if(sourcePackage!=null&&!sourcePackage.isEmpty())throw new IllegalArgumentException("文字规则不能包含图标来源应用");
                this.text=text.trim();this.sourcePackage="";
            }else if(TYPE_APP_ICON.equals(type)){
                validatePackage(sourcePackage,"图标来源应用包名格式不正确");
                if(text!=null&&!text.isEmpty())throw new IllegalArgumentException("应用图标规则不能包含替换文字");
                this.text="";this.sourcePackage=sourcePackage;
            }else throw new IllegalArgumentException("通知图标替换类型不正确");
            this.packageName=packageName;this.type=type;this.enabled=enabled;
        }
        public boolean isAppIcon(){return TYPE_APP_ICON.equals(type);}
    }
    private volatile boolean enabled;
    private volatile Map<String,Rule> selected=Collections.emptyMap();
    private volatile long revision;
    private String raw="[]";
    private boolean released,valid=true;
    public boolean enabled(){return enabled&&!released&&!ModuleLifecycle.removed();}
    public Rule match(String packageName){return enabled()?selected.get(packageName):null;}
    public long revision(){return revision;}
    public List<String> sourcePackages(){
        if(!enabled())return Collections.emptyList();
        java.util.LinkedHashSet<String> sources=new java.util.LinkedHashSet<>();
        for(Rule rule:selected.values())if(rule.isAppIcon())sources.add(rule.sourcePackage);
        return new ArrayList<>(sources);
    }
    /** A malformed imported/legacy list falls back to native icons as one transaction. */
    public boolean configure(Bundle settings){
        if(released)return false;
        String next=settings==null?"[]":settings.getString(RULES,"[]");
        boolean on=settings!=null&&settings.getBoolean(MASTER,false)&&!SafetyMode.enabled(settings)&&!ModuleLifecycle.removed();
        if(Objects.equals(raw,next))on&=valid;
        if(Objects.equals(raw,next)&&enabled==on)return false;
        if(!Objects.equals(raw,next)){
            valid=true;
            Map<String,Rule> mapped=new LinkedHashMap<>();
            try{for(Rule rule:rules(next))if(rule.enabled)mapped.put(rule.packageName,rule);}
            catch(IllegalArgumentException malformed){valid=false;on=false;ModuleDiagnostics.info("hooks","Invalid notification icon rule list; retaining native glyphs");}
            selected=Collections.unmodifiableMap(mapped);raw=next;
        }
        enabled=on;revision++;return true;
    }
    public void releaseRuntime(){enabled=false;selected=Collections.emptyMap();raw="[]";released=true;revision++;}
    public static String validationError(String json){try{rules(json);return null;}catch(IllegalArgumentException invalid){return invalid.getMessage();}}
    public static List<Rule> rules(String json){
        if(json==null||json.length()>MAX_JSON_LENGTH)throw new IllegalArgumentException("通知图标规则数据过长");
        try{
            JSONArray source=new JSONArray(json);if(source.length()>MAX_RULES)throw new IllegalArgumentException("最多设置128个应用");
            List<Rule> result=new ArrayList<>();Map<String,Boolean> seen=new LinkedHashMap<>();
            for(int i=0;i<source.length();i++){
                Object value=source.get(i);if(!(value instanceof JSONObject))throw new IllegalArgumentException("通知图标规则格式不正确");
                JSONObject object=(JSONObject)value;
                for(java.util.Iterator<String> keys=object.keys();keys.hasNext();){String key=keys.next();
                    if(!key.equals("package")&&!key.equals("text")&&!key.equals("enabled")&&!key.equals("type")&&!key.equals("sourcePackage"))throw new IllegalArgumentException("通知图标规则包含未知字段");}
                Object pkg=object.opt("package"),type=object.has("type")?object.get("type"):TYPE_TEXT;
                Object text=object.has("text")?object.get("text"):"",sourcePackage=object.has("sourcePackage")?object.get("sourcePackage"):"";
                Object on=object.has("enabled")?object.get("enabled"):Boolean.TRUE;
                if(!(pkg instanceof String)||!(type instanceof String)||!(text instanceof String)||!(sourcePackage instanceof String)||!(on instanceof Boolean))throw new IllegalArgumentException("通知图标规则格式不正确");
                Rule rule=new Rule((String)pkg,(String)type,(String)text,(String)sourcePackage,(Boolean)on);
                if(seen.put(rule.packageName,true)!=null)throw new IllegalArgumentException("同一应用只能设置一条通知图标规则");
                result.add(rule);
            }
            return Collections.unmodifiableList(result);
        }catch(JSONException invalid){throw new IllegalArgumentException("通知图标规则格式不正确");}
    }
    public static String encode(List<Rule> rules){
        if(rules==null||rules.size()>MAX_RULES)throw new IllegalArgumentException("最多设置128个应用");
        JSONArray encoded=new JSONArray();Map<String,Boolean> seen=new LinkedHashMap<>();
        try{
            for(Rule input:rules){if(input==null)throw new IllegalArgumentException("通知图标规则格式不正确");
                Rule rule=new Rule(input.packageName,input.type,input.text,input.sourcePackage,input.enabled);
                if(seen.put(rule.packageName,true)!=null)throw new IllegalArgumentException("同一应用只能设置一条通知图标规则");
                JSONObject object=new JSONObject().put("package",rule.packageName).put("enabled",rule.enabled);
                if(rule.isAppIcon())object.put("type",TYPE_APP_ICON).put("sourcePackage",rule.sourcePackage);
                else object.put("text",rule.text);
                encoded.put(object);}
        }catch(JSONException impossible){throw new IllegalArgumentException("通知图标规则格式不正确");}
        String result=encoded.toString();if(result.length()>MAX_JSON_LENGTH)throw new IllegalArgumentException("通知图标规则数据过长");return result;
    }
    public static String put(String json,String packageName,String text,boolean enabled){
        return putRule(json,new Rule(packageName,text,enabled));
    }
    public static String putAppIcon(String json,String packageName,String sourcePackage,boolean enabled){
        return putRule(json,new Rule(packageName,TYPE_APP_ICON,"",sourcePackage,enabled));
    }
    private static String putRule(String json,Rule next){
        List<Rule> current=new ArrayList<>(rules(json));String packageName=next.packageName;
        for(int i=0;i<current.size();i++)if(current.get(i).packageName.equals(packageName)){current.set(i,next);return encode(current);}
        current.add(next);return encode(current);
    }
    public static String remove(String json,String packageName){
        List<Rule> current=new ArrayList<>(rules(json));for(int i=current.size()-1;i>=0;i--)if(current.get(i).packageName.equals(packageName))current.remove(i);
        return encode(current);
    }
    public static void validatePackage(String packageName,String message){
        if(packageName==null||packageName.length()>255||!packageName.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))
            throw new IllegalArgumentException(message);
    }
    private static void validateText(String text){
        if(text==null||text.trim().isEmpty())throw new IllegalArgumentException("请填写文字或表情");
        if(text.codePointCount(0,text.length())>MAX_CODE_POINTS)throw new IllegalArgumentException("文字或表情最多8个Unicode字符");
        boolean visible=false;
        for(int i=0;i<text.length();){char ch=text.charAt(i);
            if(Character.isSurrogate(ch)&&(!Character.isHighSurrogate(ch)||i+1==text.length()||!Character.isLowSurrogate(text.charAt(i+1))))
                throw new IllegalArgumentException("文字或表情包含无效字符");
            int cp=text.codePointAt(i);if(Character.isISOControl(cp)||cp==0x2028||cp==0x2029)throw new IllegalArgumentException("文字或表情不能包含换行或控制字符");
            if(!Character.isWhitespace(cp)&&Character.getType(cp)!=Character.FORMAT&&cp!=0xfe0f&&cp!=0xfe0e)visible=true;
            i+=Character.charCount(cp);
        }
        if(!visible)throw new IllegalArgumentException("请填写可见的文字或表情");
    }
}
