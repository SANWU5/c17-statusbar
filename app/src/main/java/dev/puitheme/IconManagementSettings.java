// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.io.IOException;
import java.util.*;

/** One immutable v3 settings domain. Applying a draft never reads or resurrects legacy icon preferences. */
public final class IconManagementSettings {
    /** Pause rendering without discarding the independent saved records or private icon libraries. */
    public static final String ENABLED="icon_management_v3_enabled";
    public static final String KEY="icon_management_v3_records";
    public static final String DEFAULT="{\"version\":3,\"scopePacks\":{},\"overrides\":{},\"appearances\":{},\"animations\":{}}";
    public static final int MAX_BYTES=128*1024;
    public static final Set<String> LEGACY_KEYS=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("status_icon_pack_enabled","status_icon_pack_layers","status_icon_pack_assignments")));
    public static final class Override{
        public final String mode,packId,sourceRole;
        public Override(String mode,String packId,String sourceRole){this.mode=mode;this.packId=packId==null?"":packId;this.sourceRole=sourceRole==null?"":sourceRole;}
    }
    public static final class Appearance{
        public final String mode;public final int lightColor,darkColor;public final Float opacity,sizePercent;
        public Appearance(String mode,int lightColor,int darkColor,Float opacity){this(mode,lightColor,darkColor,opacity,null);}
        public Appearance(String mode,int lightColor,int darkColor,Float opacity,Float sizePercent){this.mode=mode;this.lightColor=lightColor;this.darkColor=darkColor;this.opacity=opacity;this.sizePercent=sizePercent;}
    }
    public static final class State{
        public final Map<String,String> scopePacks;public final Map<String,Override> overrides;public final Map<String,Appearance> appearances;public final Map<String,Boolean> animations;
        private State(Map<String,String> packs,Map<String,Override> overrides,Map<String,Appearance> appearances,Map<String,Boolean> animations){
            scopePacks=Collections.unmodifiableMap(new LinkedHashMap<>(packs));this.overrides=Collections.unmodifiableMap(new LinkedHashMap<>(overrides));this.appearances=Collections.unmodifiableMap(new LinkedHashMap<>(appearances));this.animations=Collections.unmodifiableMap(new LinkedHashMap<>(animations));
        }
    }
    public static boolean validPackId(String id){return id!=null&&id.matches("[0-9a-f]{64}");}
    private static Map<String,Object> optional(Map<String,Object> map,String key)throws IOException{return map.containsKey(key)?IconManagementJson.object(map.get(key)):Collections.emptyMap();}
    public static State parse(String records)throws IOException{
        Map<String,Object> root=IconManagementJson.parse(records,MAX_BYTES);IconManagementJson.only(root,"version","scopePacks","overrides","appearances","animations");if(IconManagementJson.integer(root.get("version"),3,3)!=3)throw IconManagementJson.error("配置版本无效");
        Map<String,String> packs=new LinkedHashMap<>();Map<String,Override> overrides=new LinkedHashMap<>();Map<String,Appearance> appearances=new LinkedHashMap<>();Map<String,Boolean> animations=new LinkedHashMap<>();
        for(Map.Entry<String,Object> row:optional(root,"scopePacks").entrySet()){if(!IconRoleRegistry.validScope(row.getKey())||!(row.getValue() instanceof String)||!validPackId((String)row.getValue()))throw IconManagementJson.error("套装范围或来源无效");packs.put(row.getKey(),(String)row.getValue());}
        Map<String,Object> rawOverrides=optional(root,"overrides"),rawAppearances=optional(root,"appearances");if(rawOverrides.size()>512||rawAppearances.size()>1024)throw IconManagementJson.error("单项图标设置过多");
        for(Map.Entry<String,Object> row:rawOverrides.entrySet()){requireRole(row.getKey());Map<String,Object> value=IconManagementJson.object(row.getValue());IconManagementJson.only(value,"mode","packId","sourceRole");
            Override selected=new Override(IconManagementJson.text(value,"mode",null,16),IconManagementJson.text(value,"packId","",64),IconManagementJson.text(value,"sourceRole","",128));validate(selected);overrides.put(row.getKey(),selected);}
        for(Map.Entry<String,Object> row:rawAppearances.entrySet()){requireAppearanceKey(row.getKey());appearances.put(row.getKey(),parseAppearance(IconManagementJson.object(row.getValue()),true));}
        for(Map.Entry<String,Object> row:optional(root,"animations").entrySet()){if(IconRoleRegistry.target(row.getKey())==null)throw IconManagementJson.error("未知动态图标目标");animations.put(row.getKey(),IconManagementJson.bool(row.getValue()));}
        return new State(packs,overrides,appearances,animations);
    }
    static Appearance parseAppearance(Map<String,Object> value,boolean allowSize)throws IOException{
            if(allowSize)IconManagementJson.only(value,"mode","lightColor","darkColor","opacity","sizePercent");else IconManagementJson.only(value,"mode","lightColor","darkColor","opacity");
            String mode=IconManagementJson.text(value,"mode","inherit",16);int light=0xff000000,dark=0xffffffff;
            if(value.containsKey("lightColor"))light=color(value.get("lightColor"));if(value.containsKey("darkColor"))dark=color(value.get("darkColor"));
            if("custom".equals(mode)&&(!value.containsKey("lightColor")||!value.containsKey("darkColor")))throw IconManagementJson.error("自定义颜色必须含浅深背景两种颜色");
            Float opacity=value.get("opacity")==null?null:(float)IconManagementJson.number(value.get("opacity"));
            Float size=value.get("sizePercent")==null?null:(float)IconManagementJson.number(value.get("sizePercent"));Appearance selected=new Appearance(mode,light,dark,opacity,size);validate(selected);return selected;
    }
    private static int color(Object value)throws IOException{int color=IconManagementJson.integer(value,Integer.MIN_VALUE,Integer.MAX_VALUE);if((color>>>24)!=255)throw IconManagementJson.error("色盘只接受 RGB；透明度请单独设置");return color;}
    private static void validate(Override value)throws IOException{if(value==null)throw IconManagementJson.error("缺少单项图案");if("system".equals(value.mode)){if(!value.packId.isEmpty()||!value.sourceRole.isEmpty())throw IconManagementJson.error("系统图案不能携带来源");}
        else if(!"pack".equals(value.mode)||!validPackId(value.packId)||!IconRoleRegistry.validResourceRole(value.sourceRole)||!IconRoleRegistry.validKnownState(value.sourceRole))throw IconManagementJson.error("单项图案来源无效");}
    private static void validate(Appearance value)throws IOException{if(value==null||!Arrays.asList("inherit","system","custom","artwork").contains(value.mode)||value.opacity!=null&&(!Float.isFinite(value.opacity)||value.opacity<0f||value.opacity>100f))throw IconManagementJson.error("颜色模式或透明度无效");if(value.sizePercent!=null&&(!Float.isFinite(value.sizePercent)||value.sizePercent<25f||value.sizePercent>200f))throw IconManagementJson.error("图标大小须为25%至200%");if("custom".equals(value.mode)&&((value.lightColor>>>24)!=255||(value.darkColor>>>24)!=255))throw IconManagementJson.error("自定义颜色需不透明 RGB");}
    private static void requireRole(String role)throws IOException{if(IconRoleRegistry.role(role)==null)throw IconManagementJson.error("未知图标角色："+role);}
    static void requireAppearanceKey(String key)throws IOException{if(key.startsWith("scope:")&&IconRoleRegistry.validScope(key.substring(6)))return;if(key.startsWith("target:")&&IconRoleRegistry.target(key.substring(7))!=null)return;if(key.startsWith("role:")&&IconRoleRegistry.role(key.substring(5))!=null)return;throw IconManagementJson.error("未知图标外观范围");}
    static String appearanceScope(String key)throws IOException{requireAppearanceKey(key);return key.startsWith("scope:")?key.substring(6):key.startsWith("target:")?IconRoleRegistry.target(key.substring(7)).scope:IconRoleRegistry.role(key.substring(5)).scope;}
    private static Map<String,Object> overrideValue(Override value)throws IOException{validate(value);Map<String,Object> out=new LinkedHashMap<>();out.put("mode",value.mode);if("pack".equals(value.mode)){out.put("packId",value.packId);out.put("sourceRole",value.sourceRole);}return out;}
    static Map<String,Object> appearanceValue(Appearance value)throws IOException{validate(value);Map<String,Object> out=new LinkedHashMap<>();out.put("mode",value.mode);if("custom".equals(value.mode)){out.put("lightColor",value.lightColor);out.put("darkColor",value.darkColor);}if(value.opacity!=null)out.put("opacity",value.opacity);if(value.sizePercent!=null)out.put("sizePercent",value.sizePercent);return out;}
    public static String encode(State state)throws IOException{
        Map<String,Object> root=new LinkedHashMap<>(),overrides=new LinkedHashMap<>(),appearances=new LinkedHashMap<>();root.put("version",3);root.put("scopePacks",state.scopePacks);
        for(Map.Entry<String,Override> row:state.overrides.entrySet())overrides.put(row.getKey(),overrideValue(row.getValue()));for(Map.Entry<String,Appearance> row:state.appearances.entrySet())appearances.put(row.getKey(),appearanceValue(row.getValue()));
        root.put("overrides",overrides);root.put("appearances",appearances);root.put("animations",state.animations);String encoded=IconManagementJson.encode(root);parse(encoded);return encoded;
    }
    public static String applyPack(String records,String packId,Collection<String> scopes,boolean clearOverrides)throws IOException{
        if(!validPackId(packId)||scopes==null||scopes.isEmpty())throw IconManagementJson.error("请选择套装与作用范围");State old=parse(records);Map<String,String> packs=new LinkedHashMap<>(old.scopePacks);Map<String,Override> overrides=new LinkedHashMap<>(old.overrides);
        for(String scope:scopes){if(!IconRoleRegistry.validScope(scope))throw IconManagementJson.error("未知套装范围");packs.put(scope,packId);}if(clearOverrides)overrides.entrySet().removeIf(row->scopes.contains(IconRoleRegistry.scopeOfRole(row.getKey())));
        return encode(new State(packs,overrides,old.appearances,old.animations));
    }
    public static String applyTarget(String records,String targetId,Map<String,Override> roleOverrides,Appearance appearance,Map<String,Appearance> roleAppearances,boolean animationEnabled)throws IOException{
        IconRoleRegistry.Target target=IconRoleRegistry.target(targetId);if(target==null||roleOverrides==null||roleAppearances==null)throw IconManagementJson.error("目标草稿无效");State old=parse(records);Map<String,Override> overrides=new LinkedHashMap<>(old.overrides);Map<String,Appearance> appearances=new LinkedHashMap<>(old.appearances);Map<String,Boolean> animations=new LinkedHashMap<>(old.animations);
        for(String role:target.roles){overrides.remove(role);appearances.remove("role:"+role);}appearances.remove("target:"+targetId);
        for(Map.Entry<String,Override> row:roleOverrides.entrySet()){if(!target.roles.contains(row.getKey()))throw IconManagementJson.error("草稿含其他目标图案");validate(row.getValue());overrides.put(row.getKey(),row.getValue());}
        if(appearance!=null){validate(appearance);appearances.put("target:"+targetId,appearance);}for(Map.Entry<String,Appearance> row:roleAppearances.entrySet()){if(!target.roles.contains(row.getKey()))throw IconManagementJson.error("草稿含其他目标外观");validate(row.getValue());appearances.put("role:"+row.getKey(),row.getValue());}
        if(animationEnabled)animations.remove(targetId);else animations.put(targetId,false);return encode(new State(old.scopePacks,overrides,appearances,animations));
    }
    public static String applyAppearance(String records,String key,Appearance appearance)throws IOException{requireAppearanceKey(key);State old=parse(records);Map<String,Appearance> values=new LinkedHashMap<>(old.appearances);if(appearance==null)values.remove(key);else{validate(appearance);values.put(key,appearance);}return encode(new State(old.scopePacks,old.overrides,values,old.animations));}
    /** Three-way merge for one target. Committed field changes win conflicts; independent draft edits survive. */
    public static String rebaseTargetDraft(String baseRecords,String draftRecords,String currentRecords,String targetId)throws IOException{
        return rebaseTargetDraft(baseRecords,draftRecords,currentRecords,targetId,null);
    }
    /** Null sources means the library is still loading. Only unsaved, unavailable source edits are discarded. */
    public static String rebaseTargetDraft(String baseRecords,String draftRecords,String currentRecords,String targetId,Map<String,IconManagementManifest> availablePacks)throws IOException{
        State base=parse(baseRecords),draft=parse(draftRecords),current=parse(currentRecords);IconRoleRegistry.Target target=IconRoleRegistry.target(targetId);
        if(target==null)throw IconManagementJson.error("目标草稿无效");
        Map<String,Override> overrides=new LinkedHashMap<>();Map<String,Appearance> appearances=new LinkedHashMap<>();
        for(String role:target.roles){
            Override saved=current.overrides.get(role),selected=sameOverride(base.overrides.get(role),saved)?draft.overrides.get(role):saved;
            if(availablePacks!=null&&selected!=null&&"pack".equals(selected.mode)&&!sameOverride(selected,saved)){
                String source=IconManagementSelection.sourceRole(availablePacks.get(selected.packId),selected.sourceRole);
                selected=source==null?saved:new Override("pack",selected.packId,source);
            }
            if(selected!=null)overrides.put(role,selected);
            Appearance appearance=rebaseAppearance(base.appearances.get("role:"+role),draft.appearances.get("role:"+role),current.appearances.get("role:"+role));
            if(appearance!=null)appearances.put(role,appearance);
        }
        String key="target:"+targetId;
        Appearance appearance=rebaseAppearance(base.appearances.get(key),draft.appearances.get(key),current.appearances.get(key));
        boolean oldAnimation=!Boolean.FALSE.equals(base.animations.get(targetId)),savedAnimation=!Boolean.FALSE.equals(current.animations.get(targetId));
        boolean animation=oldAnimation==savedAnimation?!Boolean.FALSE.equals(draft.animations.get(targetId)):savedAnimation;
        return applyTarget(currentRecords,targetId,overrides,appearance,appearances,animation);
    }
    private static boolean sameOverride(Override a,Override b){return a==b||a!=null&&b!=null&&a.mode.equals(b.mode)&&a.packId.equals(b.packId)&&a.sourceRole.equals(b.sourceRole);}
    private static String colorMode(Appearance value){return value==null?"inherit":value.mode;}
    private static boolean sameColor(Appearance a,Appearance b){String mode=colorMode(a);return mode.equals(colorMode(b))&&(!"custom".equals(mode)||a.lightColor==b.lightColor&&a.darkColor==b.darkColor);}
    /** A palette may replace colors while independent in-progress size and opacity edits survive. */
    private static Appearance rebaseAppearance(Appearance base,Appearance draft,Appearance current){
        Appearance color=sameColor(base,current)?draft:current;String mode=colorMode(color);
        Float baseOpacity=base==null?null:base.opacity,currentOpacity=current==null?null:current.opacity;
        Float baseSize=base==null?null:base.sizePercent,currentSize=current==null?null:current.sizePercent;
        Float opacity=Objects.equals(baseOpacity,currentOpacity)?draft==null?null:draft.opacity:currentOpacity;
        Float size=Objects.equals(baseSize,currentSize)?draft==null?null:draft.sizePercent:currentSize;
        if("inherit".equals(mode)&&opacity==null&&size==null)return null;
        return new Appearance(mode,color==null?0xff000000:color.lightColor,color==null?0xffffffff:color.darkColor,opacity,size);
    }
    /** A scope/target palette replaces shadowing child colors, preserving independent size and alpha. */
    public static String applyColorPreset(String records,IconColorPresets.Preset preset,Collection<String> scopes)throws IOException{
        if(preset==null||scopes==null||scopes.isEmpty())throw IconManagementJson.error("请选择颜色预设和应用范围");
        for(String scope:scopes)if(!IconRoleRegistry.validScope(scope))throw IconManagementJson.error("未知预设应用范围");
        State old=parse(records);Map<String,Appearance> values=new LinkedHashMap<>(old.appearances);
        for(Map.Entry<String,Appearance> row:old.appearances.entrySet()){
            String key=row.getKey(),scope=appearanceScope(key);if(!scopes.contains(scope))continue;
            boolean shadowed=!key.startsWith("scope:")&&preset.appearances.containsKey("scope:"+scope);
            if(key.startsWith("role:")){IconRoleRegistry.Role role=IconRoleRegistry.role(key.substring(5));shadowed|=preset.appearances.containsKey("target:"+role.targetId);}
            if(shadowed){Appearance previous=row.getValue();
                if(previous.opacity==null&&previous.sizePercent==null)values.remove(key);
                else values.put(key,new Appearance("inherit",previous.lightColor,previous.darkColor,previous.opacity,previous.sizePercent));
            }
        }
        for(Map.Entry<String,Appearance> row:preset.appearances.entrySet())if(scopes.contains(appearanceScope(row.getKey()))){Appearance color=row.getValue(),previous=values.get(row.getKey());
            values.put(row.getKey(),new Appearance(color.mode,color.lightColor,color.darkColor,color.opacity!=null?color.opacity:previous==null?null:previous.opacity,previous==null?null:previous.sizePercent));}
        return encode(new State(old.scopePacks,old.overrides,values,old.animations));
    }
    public static String removePack(String records,String id)throws IOException{State old=parse(records);Map<String,String> packs=new LinkedHashMap<>(old.scopePacks);Map<String,Override> overrides=new LinkedHashMap<>(old.overrides);packs.values().removeIf(value->value.equals(id));overrides.values().removeIf(value->id.equals(value.packId));return encode(new State(packs,overrides,old.appearances,old.animations));}
    public static String clearScopePack(String records,String scope)throws IOException{if(!IconRoleRegistry.validScope(scope))throw IconManagementJson.error("未知套装范围");State old=parse(records);Map<String,String> packs=new LinkedHashMap<>(old.scopePacks);packs.remove(scope);return encode(new State(packs,old.overrides,old.appearances,old.animations));}
    public static int dependencies(String records,String id)throws IOException{State state=parse(records);int count=0;for(String pack:state.scopePacks.values())if(id.equals(pack))count++;for(Override value:state.overrides.values())if(id.equals(value.packId))count++;return count;}
    public static String reset(){return DEFAULT;}
    private IconManagementSettings(){ }
}
