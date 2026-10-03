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
        for(String category:new String[]{SettingsCatalog.STATUSBAR,SettingsCatalog.NOTIFICATION,SettingsCatalog.CONTROL_CENTER,SettingsCatalog.LOCK_SCREEN,SettingsCatalog.OTHER})
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
                    } else if(NotificationBigClockSettings.VISIBLE_COUNT.equals(item.key)) {
                        for(float count:new float[]{1f,2f,3f,4f,5f,6f,1000000f,(float)Integer.MAX_VALUE})equal(null,SettingsCatalog.validationError(item,count));
                        for(float count:new float[]{0f,-1f,1.5f,Float.MAX_VALUE})equal(true,SettingsCatalog.validationError(item,count)!=null);
                    } else equal(null,SettingsCatalog.validationError(item,1000000f));
                    equal(true,SettingsCatalog.validationError(item,Float.NaN)!=null);equal(true,SettingsCatalog.validationError(item,Float.POSITIVE_INFINITY)!=null);
                }
            }
        }
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),StatusBarShadeIconSettings.MASTER));
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),NetworkIconOrder.SWAP));
        equal(NetworkIconOrder.SWAP,SettingsCatalog.group("network_order").masterKey);
        equal(SettingsCatalog.STATUSBAR,SettingsCatalog.group("network_order").category);
        equal(true,SettingsCatalog.item(NetworkIconOrder.SWAP).master);
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(NetworkIconOrder.SWAP));
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),NotificationBigClockSettings.STACK_ENABLED));
        equal(true,StatusBarSettings.bool(java.util.Collections.singletonMap(NotificationBigClockSettings.STACK_ENABLED,true),NotificationBigClockSettings.STACK_ENABLED));
        equal(false,StatusBarSettings.bool(java.util.Collections.singletonMap(StatusBarShadeIconSettings.MASTER,true),StatusBarShadeIconSettings.MASTER));
        equal(null,SettingsCatalog.group("shade_status_icons"));
        for(String key:new String[]{StatusBarShadeIconSettings.MASTER,NotificationBigClockSettings.INTERACTIVE_STACK,
                NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.STACK_ENABLED),
                NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.VISIBLE_COUNT),
                NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.TAIL_WIDTH_1)}) {
            equal(false,SettingsCatalog.isVisible(key)); equal(true,SettingsCatalog.hiddenKeys().contains(key));
        }
        SettingsCatalog.Group whole=SettingsCatalog.group("notification_stack");
        equal(null,SettingsCatalog.item("data_single_enabled"));
        equal(SettingsCatalog.STATUSBAR,whole.category);equal("堆叠通知",whole.title);
        equal(NotificationBigClockSettings.STACK_ENABLED,whole.masterKey);
        equal(1f,SettingsCatalog.item(NotificationBigClockSettings.VISIBLE_COUNT).min);
        equal(5f,SettingsCatalog.item(NotificationBigClockSettings.VISIBLE_COUNT).max);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),whole.masterKey));
        equal(null,SettingsCatalog.group("notification_group_stack"));
        equal(null,SettingsCatalog.item(NotificationGroupStack.MASTER));
        equal(true,SettingsCatalog.hiddenKeys().contains(NotificationGroupStack.MASTER));
        SettingsCatalog.Item landscapeStack=SettingsCatalog.item(FeatureOptions.STACK_LANDSCAPE_ENABLED);
        equal(whole.id,landscapeStack.groupId);equal(SettingsCatalog.BOOLEAN,landscapeStack.type);
        equal("横屏也生效",landscapeStack.title);equal(true,landscapeStack.description.contains("开启堆叠通知后"));
        equal(false,landscapeStack.defaultValue);equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(landscapeStack.key));
        equal(false,StatusBarSettings.bool(java.util.Collections.singletonMap(NotificationGroupStack.MASTER,true),NotificationGroupStack.MASTER));
        SettingsCatalog.Group hints=SettingsCatalog.group("status_hint_icons");
        equal(SettingsCatalog.STATUSBAR,hints.category);equal(NativeStatusIcons.MASTER,hints.masterKey);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),hints.masterKey));
        SettingsCatalog.Group badge=SettingsCatalog.group("native_network_badge");
        equal("原生角标调整",badge.title);equal(SettingsCatalog.STATUSBAR,badge.category);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),badge.masterKey));
        java.util.Map<String,Object> badgeValues=new java.util.HashMap<>();
        equal("",SettingsCatalog.unavailableReason(badge.masterKey,badgeValues));
        badgeValues.put("label_enabled",true);
        for(SettingsCatalog.Item item:badge.items)
            if(item.key.startsWith("native_data_activity_"))equal("",SettingsCatalog.unavailableReason(item.key,badgeValues));
            else equal(true,SettingsCatalog.unavailableReason(item.key,badgeValues).contains("关闭网络制式文字"));
        equal(badge.id,SettingsCatalog.item(NativeDataActivity.MASTER).groupId);
        equal(false,StatusBarSettings.bool(badgeValues,NativeDataActivity.MASTER));
        equal(0xff000000,StatusBarSettings.COLOR_DEFAULTS.get(NativeDataActivity.COLOR_LIGHT));
        equal(0xffffffff,StatusBarSettings.COLOR_DEFAULTS.get(NativeDataActivity.COLOR_DARK));
        badgeValues.put("label_enabled",false);
        for(SettingsCatalog.Item item:badge.items)equal("",SettingsCatalog.unavailableReason(item.key,badgeValues));
        equal(-3.5f,SettingsCatalog.customNumber(SettingsCatalog.item(NativeNetworkBadgeControls.X),"-3.5"));
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NativeNetworkBadgeControls.WEIGHT),badgeValues).description.contains("原生字体"));
        SettingsCatalog.Item badgeFont=SettingsCatalog.item(NativeNetworkBadgeControls.FONT);
        equal(badge.id,badgeFont.groupId);equal(SettingsCatalog.OPTIONS,badgeFont.type);equal("native",badgeFont.defaultValue);
        equal("native",StatusBarSettings.STRING_DEFAULTS.get(badgeFont.key));equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(badgeFont.key));
        equal(5,badgeFont.values.length);
        for(String source:new String[]{"native","global","system","pingfang","custom"})equal(null,SettingsCatalog.validationError(badgeFont,source));
        equal(true,SettingsCatalog.validationError(badgeFont,"unknown")!=null);
        badgeValues.put(badgeFont.key,"system");
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NativeNetworkBadgeControls.WEIGHT),badgeValues).description.contains("系统字体"));
        badgeValues.put(badgeFont.key,"custom");badgeValues.put(StatusBarSettings.FONT_REVISION,FontCatalog.find("manrope").sha256);
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NativeNetworkBadgeControls.WEIGHT),badgeValues).description.contains("Manrope"));
        equal(true,NumericPolicy.signed(NativeStatusIcons.X));equal(true,NumericPolicy.signed(NativeStatusIcons.Y));
        equal(-4.5f,SettingsCatalog.customNumber(SettingsCatalog.item(NativeStatusIcons.X),"-4.5"));
        equal(14f,SettingsCatalog.item(NativeStatusIcons.MAX).defaultValue);
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NativeStatusIcons.MAX),java.util.Collections.emptyMap()).integer);
        equal(true,SettingsCatalog.validationError(SettingsCatalog.item(NativeStatusIcons.MAX),-.5f)!=null);
        equal(true,SettingsCatalog.validationError(SettingsCatalog.item(NativeStatusIcons.MAX),1.5f)!=null);
        equal(true,SettingsCatalog.requiresNumericTrial(SettingsCatalog.item(NativeStatusIcons.MAX),15));
        SettingsCatalog.Group portrait=SettingsCatalog.group("notification_big_clock"),landscape=SettingsCatalog.group("notification_big_clock_landscape");
        equal("竖屏大时钟",portrait.title);equal("横屏大时钟",landscape.title);
        equal(NotificationBigClockSettings.LANDSCAPE_MASTER,landscape.masterKey);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),landscape.masterKey));
        Set<String> unusedLandscapeCompact=new HashSet<>();
        for(String key:new String[]{NotificationBigClockSettings.COMPACT_SCALE,NotificationBigClockSettings.COMPACT_MAX_SIZE,
                NotificationBigClockSettings.COMPACT_OFFSET_X,NotificationBigClockSettings.COMPACT_OFFSET_Y}){
            String mapped=NotificationBigClockSettings.landscapeKey(key);unusedLandscapeCompact.add(mapped);
            equal(true,SettingsCatalog.hiddenKeys().contains(mapped));equal(null,SettingsCatalog.item(mapped));
        }
        for(SettingsCatalog.Item item:portrait.items)if(!item.master) {
            String mapped=NotificationBigClockSettings.landscapeKey(item.key);
            SettingsCatalog.Item other=SettingsCatalog.item(mapped);
            if(unusedLandscapeCompact.contains(mapped)){equal(null,other);continue;}
            equal(true,other!=null);equal(landscape.id,other.groupId);equal(item.type,other.type);
            equal(item.defaultValue,other.defaultValue);equal(item.section,other.section);
        }
        SettingsCatalog.Item widthEnabled=SettingsCatalog.item(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH_ENABLED),
                width=SettingsCatalog.item(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH);
        equal(landscape.id,widthEnabled.groupId);equal(landscape.id,width.groupId);
        equal(false,widthEnabled.defaultValue);equal(100f,width.defaultValue);equal("%",width.unit);
        equal(null,SettingsCatalog.item(NotificationBigClockSettings.portraitKey(width.key)));
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),widthEnabled.key));
        equal(137.5f,SettingsCatalog.customNumber(width,"137.5"));
        equal(null,SettingsCatalog.validationError(width,1000000f));
        SettingsCatalog.Item millis=SettingsCatalog.item(NetworkSpeedControls.INTERVAL_MILLIS);
        equal("speed",millis.groupId);equal("ms",millis.unit);equal(1f,millis.min);equal(500f,millis.max);equal(250f,millis.defaultValue);
        equal(true,SettingsCatalog.hiddenKeys().contains(NetworkSpeedControls.INTERVAL_SECONDS));
        equal(null,SettingsCatalog.item(NetworkSpeedControls.INTERVAL_SECONDS));
        equal(false,SettingsCatalog.requiresNumericTrial(millis,250f));equal(true,SettingsCatalog.requiresNumericTrial(millis,.25f));
        equal(true,SettingsCatalog.requiresNumericTrial(millis,500.01f));
        for(float value:new float[]{Float.MIN_VALUE,.25f,501f,Float.MAX_VALUE})equal(null,SettingsCatalog.validationError(millis,value));
        for(float value:new float[]{0f,-1f})equal(true,SettingsCatalog.validationError(millis,value)!=null);
        equal(true,SettingsCatalog.conflictDescription(portrait.id,SettingsCatalog.NOTIFICATION).contains("关闭两个方向"));
        equal(true,SettingsCatalog.conflictDescription(landscape.id,SettingsCatalog.NOTIFICATION).contains("独立设置"));
        equal(true,SettingsCatalog.conflictDescription("carrier",SettingsCatalog.NOTIFICATION).contains("关闭大时钟"));
        equal("",SettingsCatalog.conflictDescription("carrier",SettingsCatalog.LOCK_SCREEN));
        equal(true,SettingsCatalog.group("tiles").description.contains("参数难调，开销大"));
        equal(true,SettingsCatalog.group("qs_appearance").description.contains("渐变效果开销大"));
        SettingsCatalog.Group materials=SettingsCatalog.group("c17_highlight_removal");
        equal(SettingsCatalog.OTHER,materials.category);
        equal(materials.id,SettingsCatalog.item(C17HighlightRemoval.NOTIFICATION_ENABLED).groupId);
        equal(materials.id,SettingsCatalog.item(C17HighlightRemoval.CONTROL_ENABLED).groupId);
        equal(materials.id,SettingsCatalog.item(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED).groupId);
        equal(false,SettingsCatalog.item(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED).defaultValue);
        SettingsCatalog.Group portraitClear=SettingsCatalog.group("notification_clear"),landscapeClear=SettingsCatalog.group("notification_clear_landscape");
        equal("竖屏清除按钮位置",portraitClear.title);equal("横屏清除按钮位置",landscapeClear.title);
        equal(NotificationClearAppearance.MASTER,portraitClear.masterKey);equal(NotificationClearMotion.LANDSCAPE_MASTER,landscapeClear.masterKey);
        equal(SettingsCatalog.NOTIFICATION,landscapeClear.category);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),landscapeClear.masterKey));
        equal("",SettingsCatalog.unavailableReason(landscapeClear.masterKey,java.util.Collections.emptyMap()));
        for(String key:new String[]{NotificationClearMotion.LANDSCAPE_OFFSET_X,NotificationClearMotion.LANDSCAPE_OFFSET_Y}){
            SettingsCatalog.Item position=SettingsCatalog.item(key);
            equal(landscapeClear.id,position.groupId);equal(0f,position.defaultValue);equal("dp",position.unit);
            equal(-160f,position.min);equal(160f,position.max);equal(-12.25f,SettingsCatalog.customNumber(position,"-12.25"));
        }
        equal(portraitClear.id,SettingsCatalog.item(NotificationClearMotion.OFFSET_Y).groupId);
        equal(landscapeClear.id,SettingsCatalog.item(NotificationClearAppearance.LANDSCAPE_MASTER).groupId);
        equal(false,SettingsCatalog.item(NotificationClearAppearance.LANDSCAPE_MASTER).defaultValue);
        equal(false,SettingsCatalog.item(NotificationClearAppearance.LANDSCAPE_GRADIENT_ENABLED).defaultValue);
        equal(100f,SettingsCatalog.item(NotificationClearAppearance.LANDSCAPE_OPACITY).defaultValue);
        equal(90f,SettingsCatalog.item(NotificationClearAppearance.LANDSCAPE_GRADIENT_ANGLE).defaultValue);
        for(String key:new String[]{NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT,NotificationClearAppearance.LANDSCAPE_COLOR_DARK,
                NotificationClearAppearance.LANDSCAPE_GRADIENT_COLOR_LIGHT,NotificationClearAppearance.LANDSCAPE_GRADIENT_COLOR_DARK}){
            equal(landscapeClear.id,SettingsCatalog.item(key).groupId);equal(SettingsCatalog.COLOR,SettingsCatalog.item(key).type);
            equal(ConfigTransfer.Type.COLOR,ConfigTransfer.types().get(key));
            equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(StatusBarSettings.alphaKey(key)));
        }
        SettingsCatalog.Group lockDate=SettingsCatalog.group("lockscreen_date"),lockIcon=SettingsCatalog.group("lockscreen_lock_icon");
        equal(SettingsCatalog.LOCK_SCREEN,lockDate.category);equal(SettingsCatalog.LOCK_SCREEN,lockIcon.category);
        equal(LockscreenControls.DATE_ENABLED,lockDate.masterKey);equal(LockscreenControls.HIDE_LOCK,lockIcon.masterKey);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),lockDate.masterKey));
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),lockIcon.masterKey));
        SettingsCatalog.Item lockPattern=SettingsCatalog.item(LockscreenControls.DATE_FORMAT);
        equal(lockDate.id,lockPattern.groupId);equal(SettingsCatalog.STRING,lockPattern.type);
        equal("M月d日 {周}",lockPattern.defaultValue);equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(lockPattern.key));
        equal(null,SettingsCatalog.validationError(lockPattern,"yyyy年M月d日 {星期}"));
        for(String key:new String[]{NetworkSpeedControls.DISPLAY_STYLE,NetworkSpeedControls.STYLE_ENABLED}){
            equal(null,SettingsCatalog.item(key));equal(true,SettingsCatalog.hiddenKeys().contains(key));
        }
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
        for(String unit:new String[]{"dp","sp","em","%","秒","ms","°","个",""})equal(true,units.contains(unit));
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
