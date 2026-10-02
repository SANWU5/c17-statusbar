package dev.puitheme;

import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/** Every visible parameter is backed by storage and reachable through a scene destination. */
public final class SettingsCatalogCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Catalog "+checks+": "+expected+" != "+actual);
    }
    public static void main(String[] args) {
        Set<String> reachable=new HashSet<>();
        for(String category:new String[]{SettingsCatalog.STATUSBAR,SettingsCatalog.NOTIFICATION,SettingsCatalog.CONTROL_CENTER,SettingsCatalog.LOCK_SCREEN})
            for(SettingsCatalog.NavigationSection section:SettingsCatalog.navigationSections(category))
                for(SettingsCatalog.Group group:section.groups){equal(true,group!=null);reachable.add(group.id);}
        for(SettingsCatalog.Group group:SettingsCatalog.groups()) {
            equal(true,reachable.contains(group.id));equal(true,StatusBarSettings.BOOLEAN_DEFAULTS.containsKey(group.masterKey));
            Set<String> detailKeys=new HashSet<>();
            for(SettingsCatalog.DetailPage page:SettingsCatalog.detailPages(group))for(SettingsCatalog.Item item:page.items)equal(true,detailKeys.add(item.key));
            for(SettingsCatalog.Item item:group.items) {
                equal(true,item.defaultValue!=null);equal(true,item.master||detailKeys.contains(item.key));
                if(SettingsCatalog.NUMERIC.equals(item.type)) {
                    equal(true,item.allowsCustomValue);equal(true,item.min<item.max);
                    if(!SettingsCatalog.unavailableReason(item.key).isEmpty())equal(true,SettingsCatalog.validationError(item,1f)!=null);
                    else if(QsTileCorners.RADIUS.equals(item.key)) {
                        equal(null,SettingsCatalog.validationError(item,0f));equal(null,SettingsCatalog.validationError(item,30f));
                        equal(true,SettingsCatalog.validationError(item,30.01f)!=null);equal(true,SettingsCatalog.validationError(item,-.01f)!=null);
                    } else equal(null,SettingsCatalog.validationError(item,1000000f));
                    equal(true,SettingsCatalog.validationError(item,Float.NaN)!=null);equal(true,SettingsCatalog.validationError(item,Float.POSITIVE_INFINITY)!=null);
                }
            }
        }
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),StatusBarShadeIconSettings.MASTER));
        equal(false,StatusBarSettings.bool(java.util.Collections.singletonMap(NotificationBigClockSettings.STACK_ENABLED,true),NotificationBigClockSettings.STACK_ENABLED));
        equal("底层bug修复不了 该功能不予适用",SettingsCatalog.unavailableReason(NotificationBigClockSettings.STACK_ENABLED));
        equal(true,SettingsCatalog.conflictDescription("notification_big_clock",SettingsCatalog.NOTIFICATION).contains("关闭大时钟"));
        equal(true,SettingsCatalog.conflictDescription("carrier",SettingsCatalog.NOTIFICATION).contains("关闭大时钟"));
        equal("",SettingsCatalog.conflictDescription("carrier",SettingsCatalog.LOCK_SCREEN));
        equal(true,SettingsCatalog.group("tiles").description.contains("参数难调，开销大"));
        equal(true,SettingsCatalog.group("qs_appearance").description.contains("渐变效果开销大"));
        equal(true,SettingsCatalog.validationError(SettingsCatalog.item(NotificationIconArea.MAX_COUNT),-.1f)!=null);
        equal(true,SettingsCatalog.validationError(SettingsCatalog.item(NotificationIconArea.MAX_COUNT),1.5f)!=null);
        equal(12.34567f,SettingsCatalog.customNumber(SettingsCatalog.item(StatusBarSettings.CLOCK_OFFSET_X),"12.34567"));
        equal(3f,SettingsCatalog.customNumber(SettingsCatalog.item(NotificationIconArea.MAX_COUNT),"3.0000"));
        for(String value:new String[]{"3.5","-0.1","2.0000000000000000001"}) {
            checks++;try{SettingsCatalog.customNumber(SettingsCatalog.item(NotificationIconArea.MAX_COUNT),value);throw new AssertionError("Accepted count "+value);}
            catch(IllegalArgumentException expected){}
        }
        for(String value:new String[]{"30.00000000000000001","-0.0000001"}) {
            checks++;try{SettingsCatalog.customNumber(SettingsCatalog.item(QsTileCorners.RADIUS),value);throw new AssertionError("Accepted radius "+value);}
            catch(IllegalArgumentException expected){}
        }
        editorContext();
        System.out.println("Settings catalog and contextual editor checks passed: "+checks);
    }
    private static void editorContext(){
        java.util.Map<String,Object> values=new java.util.HashMap<>();
        Set<String> groups=new HashSet<>(),units=new HashSet<>();
        for(SettingsCatalog.Item item:SettingsCatalog.allItems())if(SettingsCatalog.NUMERIC.equals(item.type)){
            SettingEditor.Info info=SettingEditor.forItem(item,values);
            groups.add(item.groupId);units.add(item.unit);
            equal(true,info.numeric);equal(true,info.context.contains(SettingsCatalog.group(item.groupId).title)||"carrier".equals(item.groupId));
            String section=item.section.contains(" · ")?item.section.substring(item.section.lastIndexOf(" · ")+3):item.section;
            equal(true,info.context.endsWith(section));equal(true,info.valueSummary.contains("默认值："));
            equal(true,info.valueSummary.contains(SettingEditor.number(item.min)+"～"+SettingEditor.number(item.max)));
            if(!item.unit.isEmpty())equal(true,info.label.contains(item.unit));
            if(!item.key.equals(QsTileCorners.RADIUS))equal(false,info.description.contains("0～30 dp"));
            if(!item.key.equals(NotificationIconArea.MAX_COUNT))equal(false,info.description.contains("必须为非负整数"));
            equal(false,info.description.contains("100 最细，900 最粗"));
        }
        for(String unit:new String[]{"dp","sp","em","%","秒","°","个",""})equal(true,units.contains(unit));
        for(String group:new String[]{"speed","data","wifi","label","clock","carrier","battery","notification_icons","tiles","qs_appearance","qs_tile_corners","qs_tile_icon_size","shade_clock","notification_big_clock","notification_clear","qs_media"}){
            if(SettingsCatalog.group(group)!=null&&!SettingsCatalog.group(group).items.stream().noneMatch(item->SettingsCatalog.NUMERIC.equals(item.type)))equal(true,groups.contains(group));
        }
        SettingsCatalog.Item weight=SettingsCatalog.item(BatteryTextStyle.WEIGHT),offset=SettingsCatalog.item(StatusBarSettings.CLOCK_OFFSET_X);
        values.put(weight.key,1234.567f);SettingEditor.Info saved=SettingEditor.forItem(weight,values);
        equal("1234.567",saved.initial);equal(true,saved.valueSummary.contains("1234.567"));equal(true,saved.description.contains("1～1000"));
        equal("电池 · 电池内文字",saved.context);equal(false,saved.integer);
        values.put(offset.key,-.00012345f);equal(-.00012345f,SettingsCatalog.customNumber(offset,SettingEditor.forItem(offset,values).initial));
        equal(true,SettingEditor.forItem(offset,values).signed);equal(true,SettingEditor.forItem(offset,values).description.contains("负值向左"));
        SettingsCatalog.Item corner=SettingsCatalog.item(QsTileCorners.RADIUS),count=SettingsCatalog.item(NotificationIconArea.MAX_COUNT);
        equal(true,SettingEditor.forItem(corner,values).valueSummary.contains("允许范围：0～30 dp"));
        equal(true,SettingEditor.forItem(count,values).integer);equal(true,SettingEditor.forItem(count,values).description.contains("0 表示不显示图标"));
        for(int exact:new int[]{16777217,Integer.MAX_VALUE}){
            values.put(count.key,exact);equal(exact,SettingEditor.storedValue(count,values));
            equal(Integer.toString(exact),SettingEditor.forItem(count,values).initial);
            equal(exact+" 个",SettingEditor.formatted(count,exact));
        }
        equal(10.12345f,SettingsCatalog.customNumber(corner,"10.12345"));
        equal(20000f,SettingsCatalog.customNumber(count,"2e4"));
        equal(Float.MAX_VALUE,SettingsCatalog.customNumber(weight,Float.toString(Float.MAX_VALUE)));
        values.put("font_enabled",true);values.put(StatusBarSettings.FONT_MODE,"custom");values.put(StatusBarSettings.FONT_REVISION,FontCatalog.find("manrope").sha256);
        equal(true,SettingEditor.forItem(SettingsCatalog.item(StatusBarSettings.CLOCK_WEIGHT),values).description.contains("Manrope，实际字重轴为200～800"));
        values.put(NotificationBigClockSettings.FONT,"native");
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NotificationBigClockSettings.WEIGHT),values).description.contains("当前来源：原生字体"));
        values.put(NotificationBigClockSettings.FONT,"system");
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NotificationBigClockSettings.DATE_WEIGHT),values).description.contains("当前来源：系统字体"));
        values.put(NotificationBigClockSettings.FONT,"custom");
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NotificationBigClockSettings.FOOTER_WEIGHT),values).description.contains("200～800"));
        values.put("font_enabled",false);equal(true,SettingEditor.forItem(SettingsCatalog.item(StatusBarSettings.CLOCK_WEIGHT),values).description.contains("当前来源：系统字体"));
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NotificationBigClockSettings.COMPACT_OFFSET_Y),values).description.contains("在初始偏移上额外调整收起位置"));
        for(String group:CarrierPanels.GROUPS){
            String context=SettingEditor.context(SettingsCatalog.item(CarrierPanels.key(group,"weight")));
            equal(true,context.contains(group.equals(CarrierPanels.LOCKSCREEN)?"锁屏":group.equals(CarrierPanels.CONTROL)?"控制中心":"通知栏"));
            equal(true,context.endsWith("文字样式"));
        }
        SettingEditor.Info pattern=SettingEditor.forItem(SettingsCatalog.item(StatusBarSettings.CLOCK_PATTERN),values);
        equal(true,pattern.pattern);equal(false,pattern.numeric);equal(true,pattern.description.contains("HH:mm:ss"));equal(true,pattern.description.contains(Integer.toString(TimeFormat.MAX_PATTERN)));
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NotificationBigClockSettings.FOOTER_PATTERN),values).description.contains("{text}"));
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NotificationIconArea.TEXT),values).description.contains("12个"));
        String trial=SettingEditor.trialSummary(weight,824f,937.5f,values);
        equal(true,trial.contains("上次确认：824"));equal(true,trial.contains("本次试用：937.5"));equal(true,trial.contains("超出滑块建议：100～900"));equal(true,trial.contains("20秒"));
    }
}
