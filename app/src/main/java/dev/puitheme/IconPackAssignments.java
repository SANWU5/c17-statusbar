// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.io.IOException;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

/** Explicit target-to-artwork choices. Only validated roles and immutable library IDs are stored. */
public final class IconPackAssignments {
    public static final String ASSIGNMENTS="status_icon_pack_assignments";
    public static final int MAX_ASSIGNMENTS=128,MAX_JSON_LENGTH=32768;
    private IconPackAssignments(){}
    public static Map<String,String> stringDefaults(){return Collections.singletonMap(ASSIGNMENTS,"[]");}
    public static final class Assignment {
        public final String targetRole,packId,sourceRole;
        public Assignment(String targetRole,String packId,String sourceRole){
            if(!validTarget(targetRole))throw new IllegalArgumentException("目标系统图标无效");
            if(!IconPackManifest.validRevision(packId))throw new IllegalArgumentException("图标来源库无效");
            if(!IconPackManifest.validKey(sourceRole))throw new IllegalArgumentException("来源图案标识无效");
            this.targetRole=targetRole;this.packId=packId;this.sourceRole=sourceRole;
        }
    }
    public static boolean validTarget(String role){
        if(!IconPackManifest.validKey(role))return false;
        if(!role.startsWith("hint."))return true;
        String slot=role.substring(5).replaceFirst("\\.(on|off)$","");
        return Arrays.asList(NativeStatusIcons.PRIORITY_SLOTS).contains(slot);
    }
    public static List<String> targetRoles(){
        List<String> roles=new ArrayList<>();
        for(String slot:NativeStatusIcons.PRIORITY_SLOTS){
            roles.add("hint."+slot);
            // Only Bluetooth has two verified visible native resources in the current binder.
            if("bluetooth".equals(slot)){roles.add("hint."+slot+".on");roles.add("hint."+slot+".off");}
        }
        for(String family:new String[]{"wifi","cellular"}){
            // The inspected native WifiIcon model returns Hidden when disconnected. Keep
            // wifi.none valid for stored/imported compatibility, but never offer an inert target.
            if(!"wifi".equals(family))roles.add(family+".none");
            for(int level=0;level<=4;level++)roles.add(family+"."+level);
        }
        for(int level=0;level<=100;level+=10){roles.add("battery."+level);roles.add("battery.charging."+level);}
        return Collections.unmodifiableList(roles);
    }
    public static String unavailableTargetReason(String role){
        if("wifi.none".equals(role))return "当前系统在 Wi-Fi 未连接时隐藏图标，此项暂不参与绘制";
        if(role!=null&&role.startsWith("hint.")&&role.endsWith(".off")&&!"hint.bluetooth.off".equals(role))
            return "当前仅替换这个图标的显示状态，此项暂不参与绘制";
        return "";
    }
    public static String label(String role){
        if(role==null)return "未选择图标";
        if(role.startsWith("hint.")){
            String slot=role.substring(5),state="";
            if(slot.endsWith(".on")){slot=slot.substring(0,slot.length()-3);state=" · 开启";}
            else if(slot.endsWith(".off")){slot=slot.substring(0,slot.length()-4);state=" · 关闭";}
            if("bluetooth".equals(slot)&&!state.isEmpty())state=role.endsWith(".on")?" · 已连接":" · 未连接";
            return NativeStatusIcons.priorityLabel(slot)+state;
        }
        if(role.startsWith("wifi."))return "Wi-Fi · "+("none".equals(role.substring(5))?"未连接":"信号 "+role.substring(5));
        if(role.startsWith("cellular."))return "蜂窝信号 · "+("none".equals(role.substring(9))?"未连接":"信号 "+role.substring(9));
        if(role.startsWith("battery.charging."))return "电池充电 · "+role.substring(17)+"%";
        if(role.startsWith("battery."))return "电池 · "+role.substring(8)+"%";
        return role;
    }
    public static List<Assignment> rules(String json)throws IOException{
        if(json==null||json.length()>MAX_JSON_LENGTH)throw new IOException("逐项图标设置过长");
        try{
            JSONArray array=new JSONArray(json);if(array.length()>MAX_ASSIGNMENTS)throw new IOException("最多设置128项图标");
            List<Assignment> result=new ArrayList<>();Set<String> targets=new HashSet<>();
            for(int i=0;i<array.length();i++){
                Object value=array.get(i);if(!(value instanceof JSONObject))throw new IOException("逐项图标设置格式错误");
                JSONObject item=(JSONObject)value;Object target=item.opt("targetRole"),pack=item.opt("packId"),source=item.opt("sourceRole");
                if(item.length()!=3||!(target instanceof String)||!(pack instanceof String)||!(source instanceof String))throw new IOException("逐项图标设置字段无效");
                Assignment assignment=new Assignment((String)target,(String)pack,(String)source);
                if(!targets.add(assignment.targetRole))throw new IOException("同一目标图标只能设置一次");
                result.add(assignment);
            }
            return Collections.unmodifiableList(result);
        }catch(JSONException|IllegalArgumentException malformed){throw new IOException("逐项图标设置无效："+malformed.getMessage(),malformed);}
    }
    public static String validationError(String json){try{rules(json);return null;}catch(IOException invalid){return invalid.getMessage();}}
    public static String encode(List<Assignment> assignments)throws IOException{
        if(assignments==null||assignments.size()>MAX_ASSIGNMENTS)throw new IOException("最多设置128项图标");
        try{
            JSONArray array=new JSONArray();
            for(Assignment assignment:assignments){if(assignment==null)throw new IOException("逐项图标设置无效");
                array.put(new JSONObject().put("targetRole",assignment.targetRole).put("packId",assignment.packId).put("sourceRole",assignment.sourceRole));}
            String json=array.toString();rules(json);return json;
        }catch(JSONException malformed){throw new IOException("逐项图标设置格式错误",malformed);}
    }
    public static String put(String json,String targetRole,String packId,String sourceRole)throws IOException{
        Assignment replacement=new Assignment(targetRole,packId,sourceRole);List<Assignment> current=new ArrayList<>(rules(json));
        for(int i=0;i<current.size();i++)if(current.get(i).targetRole.equals(targetRole)){current.set(i,replacement);return encode(current);}
        current.add(replacement);return encode(current);
    }
    public static String remove(String json,String targetRole)throws IOException{
        List<Assignment> current=new ArrayList<>(rules(json));current.removeIf(assignment->assignment.targetRole.equals(targetRole));return encode(current);
    }
}
