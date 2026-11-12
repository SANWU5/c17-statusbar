package dev.puitheme;

import android.os.Bundle;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exercises the real display projection and shared-slot planner, never a second implementation. */
public final class NetworkDisplaySettingsCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void equal(Object expected,Object actual,String why){check(java.util.Objects.equals(expected,actual),why+": "+expected+" != "+actual);}
    private static Map<String,Object> values(Bundle b){Map<String,Object> result=new LinkedHashMap<>();for(String key:b.keySet())result.put(key,b.get(key));return result;}
    private static void modes()throws Exception{
        for(boolean label:new boolean[]{false,true})for(boolean hidden:new boolean[]{false,true})for(boolean data:new boolean[]{false,true}){
            Bundle raw=new Bundle();raw.putBoolean("label_enabled",label);raw.putBoolean(StatusBarSettings.LABEL_HIDDEN,hidden);
            raw.putBoolean("data_enabled",data);raw.putBoolean("data_badge_hidden",true);
            raw.putFloat(StatusBarSettings.LABEL_SCALE,137.5f);raw.putFloat(NativeNetworkBadgeSettings.key(1,"scale"),86.25f);
            raw.putInt("label_color_light",0x80123456);Map<String,Object> before=values(raw);
            String wanted=!label?NetworkDisplaySettings.BADGE:hidden?NetworkDisplaySettings.HIDDEN:NetworkDisplaySettings.TEXT;
            equal(wanted,NetworkDisplaySettings.mode(raw),"Bundle mode uses the existing two switches");
            equal(wanted,NetworkDisplaySettings.mode(before),"UI and runtime resolve the same mode");
            Bundle projected=NetworkDisplaySettings.runtime(raw);FeatureOptions options=FeatureOptions.from(projected);
            equal(label,options.enabled("label"),"badge mode releases custom text ownership");
            equal(label&&hidden,options.hideNetworkLabel(),"hidden mode is separate from disabling text");
            equal(label&&data,options.hideNativeNetworkBadge(),"old native-badge suppression cannot hide badge mode");
            equal(before,values(raw),"display projection never overwrites prior style, size or visibility choices");
            equal(137.5f,projected.getFloat(StatusBarSettings.LABEL_SCALE),"text size survives every mode");
            equal(86.25f,projected.getFloat(NativeNetworkBadgeSettings.key(1,"scale")),"badge size survives every mode");
            equal(0x80123456,projected.getInt("label_color_light"),"saved network color survives every mode");
            raw.putBoolean(StatusBarSettings.SAFE_MODE,true);
            equal(values(raw),values(NetworkDisplaySettings.runtime(raw)),"safe mode leaves the complete original native configuration");
        }
        for(boolean custom:new boolean[]{false,true}){
            Bundle raw=new Bundle();raw.putBoolean("wifi_enabled",custom);raw.putBoolean(NetworkDisplaySettings.WIFI_HIDDEN,true);
            check(FeatureOptions.from(raw).hideWifiIcon(),"Wi-Fi display hiding is independent of its custom-style master");
            raw.putBoolean(NetworkDisplaySettings.WIFI_HIDDEN,false);raw.putBoolean("wifi_icon_enabled",false);
            check(!FeatureOptions.from(raw).hideWifiIcon(),"disabling the custom Wi-Fi pattern does not hide native Wi-Fi");
            raw.putBoolean(NetworkDisplaySettings.WIFI_HIDDEN,true);raw.putBoolean(StatusBarSettings.SAFE_MODE,true);
            check(!FeatureOptions.from(raw).hideWifiIcon(),"safe mode relinquishes Wi-Fi hiding");
        }
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(NetworkDisplaySettings.WIFI_HIDDEN),"new Wi-Fi display flag has the portable Boolean protocol");
        check(!NetworkDisplaySettings.validMode(null)&&!NetworkDisplaySettings.validMode("native"),"unsupported mode inputs are rejected");
        for(String mode:Arrays.asList(NetworkDisplaySettings.TEXT,NetworkDisplaySettings.BADGE,NetworkDisplaySettings.HIDDEN))check(NetworkDisplaySettings.validMode(mode),"supported explicit display choice");
    }
    private static void sharedX(){
        for(String side:new String[]{StatusBarLayoutSettings.LEFT,StatusBarLayoutSettings.RIGHT}){
            Bundle raw=new Bundle();raw.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED,true);raw.putBoolean(StatusBarLayoutSettings.MASTER,true);
            raw.putString(StatusBarLayoutSettings.key("wifi","side"),side);raw.putString(StatusBarLayoutSettings.key("mobile","side"),side);
            raw.putFloat(StatusBarLayoutSettings.key("wifi","margin_left"),7);raw.putFloat(StatusBarLayoutSettings.key("wifi","margin_right"),9);
            StatusBarFlowPlanner.Node wifi=new StatusBarFlowPlanner.Node("wifi",side,0,20,0,0),signal=new StatusBarFlowPlanner.Node("mobile",side,1,18,0,0);
            StatusBarFlowPlanner.Bounds bounds=new StatusBarFlowPlanner.Bounds(0,300,0,300);
            StatusBarFlowPlanner.Plan before=StatusBarFlowPlanner.plan(bounds,Arrays.asList(wifi,signal),StatusBarLayoutSettings.Snapshot.parse(raw),1);
            raw.putFloat(StatusBarSettings.WIFI_OFFSET_X,6.25f);raw.putFloat(StatusBarSettings.LABEL_OFFSET_X,-18f);
            StatusBarLayoutSettings.Snapshot shared=StatusBarLayoutSettings.Snapshot.parse(raw);
            StatusBarFlowPlanner.Plan after=StatusBarFlowPlanner.plan(bounds,Arrays.asList(wifi,signal),shared,1);
            equal(before.find(wifi).x+6.25f,after.find(wifi).x,"shared X moves its physical slot exactly once on either side");
            equal(before.find(signal).x,after.find(signal).x,"shared X does not push the independent signal");
            equal(before.leftUsed,after.leftUsed,"shared X never changes left-side allocation");equal(before.rightUsed,after.rightUsed,"shared X never changes right-side allocation");
            equal(7f,shared.left("wifi"),"X adjustment does not rewrite or reinterpret left gap");equal(9f,shared.right("wifi"),"X adjustment does not rewrite or reinterpret right gap");
            equal(6.25f,shared.withoutOrdering().offsetX("wifi"),"capsule ordering suspension retains shared relative position");
            equal(StatusBarSettings.WIFI_OFFSET_X,StatusBarLayoutSettings.editingKey(StatusBarSettings.LABEL_OFFSET_X,values(raw)),"label X editor aliases canonical shared X");
            equal(-18f,raw.getFloat(StatusBarSettings.LABEL_OFFSET_X),"shared X keeps the independently saved legacy label position");
            raw.putBoolean("wifi_position_enabled",false);equal(0f,StatusBarLayoutSettings.Snapshot.parse(raw).offsetX("wifi"),"common position switch gates shared X");
            raw.putBoolean("wifi_position_enabled",true);raw.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED,false);
            equal(0f,StatusBarLayoutSettings.Snapshot.parse(raw).offsetX("wifi"),"closing sharing releases the common X projection");
        }
    }
    public static void main(String[] args)throws Exception{modes();sharedX();System.out.println("NetworkDisplaySettingsCheck: "+checks+" checks passed");}
}
