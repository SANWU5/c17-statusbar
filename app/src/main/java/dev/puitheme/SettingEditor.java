// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.math.BigDecimal;
import java.util.Map;

/** Editor context from the real catalog; suggested slider bounds never become input limits. */
public final class SettingEditor {
    private SettingEditor(){ }
    public static boolean appPickerKey(String key) {
        return NotificationBigClockSettings.CLOCK_LAUNCH_APP.equals(key)||NotificationBigClockSettings.DATE_LAUNCH_APP.equals(key)
                ||BigClockWidgetsSettings.launchAppKey(key);
    }
    public static final class Info {
        public final String context,label,initial,valueSummary,description;
        public final boolean numeric,integer,signed,pattern,previewable;
        private Info(String context,String label,String initial,String valueSummary,String description,
                boolean numeric,boolean integer,boolean signed,boolean pattern,boolean previewable){
            this.context=context;this.label=label;this.initial=initial;this.valueSummary=valueSummary;
            this.description=description;this.numeric=numeric;this.integer=integer;this.signed=signed;this.pattern=pattern;
            this.previewable=previewable;
        }
    }
    /** Prefer the confirmed raw number over a rendering guard's bounded result. Viewing never writes it. */
    public static Object storedValue(SettingsCatalog.Item item,Map<String,?> values){
        String key=StatusBarLayoutSettings.editingKey(item.key,values);
        Object raw=values==null?null:values.get(key);
        if(SettingsCatalog.NUMERIC.equals(item.type)&&raw instanceof Number
                &&Float.isFinite(((Number)raw).floatValue()))return raw;
        return SettingsCatalog.value(values,key);
    }
    public static String number(float value){
        if(!Float.isFinite(value))return Float.toString(value);
        if(Math.abs(value)>=1e7f||value!=0f&&Math.abs(value)<.001f)return Float.toString(value);
        return new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
    }
    public static String formatted(SettingsCatalog.Item item,Object value){
        if(PrivacyIndicatorSettings.DURATION.equals(item.key)&&value instanceof Number){
            float seconds=((Number)value).floatValue();
            if(!(seconds>0f)||seconds>=PrivacyIndicatorSettings.CONTINUOUS)return "一直显示";
        }
        String text=valueText(value);
        return text+(item.unit.isEmpty()?"":" "+item.unit);
    }
    private static String valueText(Object value){
        if(value==null)return "未设置";
        return value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long
                ?value.toString():value instanceof Number?number(((Number)value).floatValue()):value.toString();
    }
    public static Info forItem(SettingsCatalog.Item item,Map<String,?> values){
        Object value=storedValue(item,values);
        boolean numeric=SettingsCatalog.NUMERIC.equals(item.type),integer=NativeStatusIcons.MAX.equals(item.key)||NotificationIconArea.MAX_COUNT.equals(item.key)
                ||NotificationBigClockSettings.VISIBLE_COUNT.equals(NotificationBigClockSettings.portraitKey(item.key))
                ||StatusBarLayoutSettings.orderKey(item.key);
        boolean pattern=item.key.endsWith("_pattern")||LockscreenControls.DATE_FORMAT.equals(item.key),signed=NumericPolicy.signed(item.key);
        String initial=value==null?"":numeric?valueText(value):value.toString();
        String summary=numeric?"当前值："+formatted(item,value)+"\n默认值："+formatted(item,item.defaultValue)
                +"\n"+(QsTileCorners.RADIUS.equals(item.key)?"允许范围：":"滑块建议：")
                +number(item.min)+"～"+number(item.max)+(item.unit.isEmpty()?"":" "+item.unit):"";
        boolean component=BigClockWidgetsSettings.textKey(item.key);
        String label=numeric?(item.unit.isEmpty()?"字重（无单位）":item.unit.equals("个")?"数量（个）":"数值（"+item.unit+"）")
                :pattern?"自定义格式":component?"组件内容":"单行文字";
        if(numeric&&item.unit.isEmpty()&&!item.key.endsWith("_weight")&&!item.key.equals(StatusBarSettings.FONT_WEIGHT))label="数值（无单位）";
        String description=numeric?numericDescription(item,values):textDescription(item);
        String context=context(item);
        if(!item.key.equals(StatusBarLayoutSettings.editingKey(item.key,values)))context+=" · 与 Wi-Fi 共用";
        return new Info(context,label,initial,summary,description,numeric,integer,signed,pattern,pattern||component);
    }
    public static String context(SettingsCatalog.Item item){
        SettingsCatalog.Group group=SettingsCatalog.group(item.groupId);
        String title=group==null?item.groupId:group.title;
        if("native_network_badge".equals(SettingsCatalog.networkOwner(item.key))) {
            if(item.key.startsWith(NativeNetworkBadgeSettings.PART_ONE+"_"))title+=" · 角标1";
            else if(item.key.startsWith(NativeNetworkBadgeSettings.PART_TWO+"_"))title+=" · 角标2";
        }
        if("carrier".equals(item.groupId))title=item.key.startsWith(CarrierPanels.LOCKSCREEN)?"锁屏运营商文字"
                :item.key.startsWith(CarrierPanels.CONTROL)?"控制中心运营商文字":"通知栏运营商文字";
        int separator=item.section.lastIndexOf(" · ");
        String section=separator>=0?item.section.substring(separator+3):item.section;
        return title+(section.isEmpty()?"":" · "+section);
    }
    private static String numericDescription(SettingsCatalog.Item item,Map<String,?> values){
        String key=NotificationBigClockSettings.portraitKey(item.key);
        if(StatusBarLayoutSettings.orderKey(key))return StatusBarLayoutSettings.key(StatusBarLayoutSettings.LABEL,"order").equals(key)
                ?"网络制式始终跟随蜂窝区域。0 跟随系统，1 在信号前，2 在信号后；不会强制显示被系统隐藏的制式。"
                :"同一区域按物理从左到右排列。0 跟随系统；1 起按序号排列，同号保留原生先后。只能使用非负整数，保存不会自动开启布局。";
        if(StatusBarLayoutSettings.marginKey(key))return (StatusBarSettings.bool(values,StatusBarLayoutSettings.WIFI_LABEL_SHARED)
                &&("wifi".equals(SettingsCatalog.networkOwner(key))||"label".equals(SettingsCatalog.networkOwner(key)))?"Wi-Fi 与网络制式共用此间距。关闭共用后恢复各自设置。\n":"")
                +"相对元素的原生边界调整留白，左右指屏幕物理左右。正数增加、负数收紧，0 保留原生留白；元素仍参与原生动画。边距独立于排序和样式开关。\n支持正负小数和科学计数法，滑块建议范围不限制手动输入。建议范围外先试用20秒，未确认自动恢复；实际布局仍受物理保护。旧水平偏移保留，边距生效时不再叠加。";
        if(QsTileCorners.RADIUS.equals(key))return "统一调整磁贴、音乐、亮度、音量与设备卡片的圆角。0 为直角，允许 0～"+number(QsTileCorners.MAX_RADIUS)+" dp；手动输入的小数不按滑块步长取整。\n建议范围内直接保存，不自动开启功能。";
        if(NotificationIconArea.MAX_COUNT.equals(key))return "仅用于原生通知图标，必须为非负整数。0 表示不显示图标，不删除通知；可超过滑块建议数量。\n建议范围内直接保存；范围外先试用20秒，确认后保存。";
        if(NotificationBigClockSettings.SCREEN_PADDING.equals(key))return "仅作用于竖屏原生字体。设置时钟距离屏幕左右安全边缘的留白，左右使用相同数值；字形在剩余宽度内改变高度，宽度保持稳定，不改变通知卡片大小。0 保留屏幕安全边缘。\n建议范围内直接保存；范围外先试用20秒，未确认自动恢复。";
        if(NotificationIconArea.SPACING.equals(key))return "在系统通知图标间距上增减距离，0 保留原生间距。只改变多个通知图标之间的空隙，不改变图标大小；单个符号、文字或图片不受影响。显示数量仍受状态栏可用空间限制。\n建议范围内直接保存；范围外先试用20秒，未确认自动恢复。";
        if(NotificationBigClockSettings.MAX_SIZE.equals(key)||NotificationBigClockSettings.COMPACT_MAX_SIZE.equals(key))return "按 dp 设置时间文字的实际高度，数值越大字形越高。横屏固定为一个高度；竖屏只有原生字体区分展开与收起高度，其他字体保持一个高度。实际显示仍受页面可用宽度限制，避免超出屏幕。\n建议范围内直接保存；范围外先试用20秒，未确认自动恢复。";
        if(NativeStatusIcons.MAX.equals(key))return "只计算蓝牙、定位、闹钟等系统提示图标，不包含通知、网络和电池。填写非负整数；0 隐藏提示图标，优先级靠前的先保留。超出实际数量时仅显示现有提示。\n建议范围内直接保存；范围外先试用20秒，未确认自动恢复。";
        if(NativeStatusIcons.X.equals(key)||NativeStatusIcons.Y.equals(key))return "相对于系统默认位置，水平正值向右、负值向左；垂直正值向下、负值向上。只移动提示图标，保留原生动画；显示位置限制在实际可用空间内，避免覆盖网络区域或被父容器裁切。\n建议范围内直接保存；范围外先试用20秒，未确认自动恢复。";
        if(NativeStatusIcons.SPACING.equals(key))return "在原生提示图标间距上增加所填距离；0 保留系统间距。实际显示受容器空间限制，不改动蜂窝信号、电池和Wi-Fi的间距。\n建议范围内直接保存；范围外先试用20秒，未确认自动恢复。";
        if(NotificationBigClockSettings.VISIBLE_COUNT.equals(key))return "设置通知堆叠中完整显示的通知数量，填写 1 到 2147483647 的整数；建议 1～5，可超过建议值。超过实际通知数量时全部正常显示，不生成额外通知。\n建议范围内直接保存；范围外先试用20秒，未确认自动恢复。";
        String detail;
        if(key.endsWith("_weight")||StatusBarSettings.FONT_WEIGHT.equals(key))detail=weightDescription(item,values);
        else if(NotificationBigClockSettings.NOTIFICATION_GAP.equals(key))detail="从时间、日期与组件的实际底边调整首张通知的距离，正值增加留白，负值允许向上重叠；开启或关闭回弹均可使用。需开启自动保留距离，通知进入顶部边缘时沿用模糊与渐隐设置。";
        else if(NotificationBigClockSettings.NOTIFICATION_EDGE_SAFE_DISTANCE.equals(key))detail="相对时钟实际底边调整通知保持清晰的起点，负值把现有模糊与渐隐过渡向上移入时钟区域；开启或关闭回弹均可使用。状态栏和屏幕开孔安全区仍受保护。";
        else if(FluidCloudStyle.NOTIFICATION_FOLLOW_OPACITY.equals(key))detail="锁屏与 AOD 里普通通知的着色浓度，相对流体云卡片：100% 与它们完全一致（用本页的填充透明度，开了锁屏自定义则用锁屏的值），调低则普通通知更淡。";
        else if(SpeedPosition.EDGE_RESERVE.equals(key))detail="信号和 Wi-Fi 的固定预留区域。启用网速位置调整或相邻距离后，网速相对所选区域定位，Wi-Fi、数据状态不改变位置，水平偏移为 0 时也生效。0 表示不预留；实际图标更宽时仍保持位置，若重叠请调大。时钟左右位置跟随时钟边缘，不使用这段预留。";
        else if(SpeedPosition.SAFE_GAP.equals(key))detail="网速与所选固定预留区域或时钟边缘的距离：0 dp 贴边，负数靠近并允许重叠，正数拉开。当前信号图案变宽不会推动网速。需开启“与相邻图标保持距离”；开启后原水平偏移值不生效，关闭后恢复。";
        else if(key.endsWith("offset_x")||key.endsWith("offset_y"))detail=(StatusBarLayoutSettings.sharedPositionKey(key)
                &&StatusBarSettings.bool(values,StatusBarLayoutSettings.WIFI_LABEL_SHARED)
                ?"Wi-Fi 与网络制式共用水平、垂直位置和位置开关；大小和自定义功能开关各自独立。关闭共用后恢复各自位置。\n":"")
                +positionDescription(item,key.endsWith("offset_x"));
        else if("em".equals(item.unit))detail="按字号比例调整字距：1 em 等于当前字号，正值放宽、负值收紧，0 不额外调整。";
        else if(key.endsWith("_spacing"))detail="在原生字距上增加或减少所填距离，正值放宽、负值收紧，0 不额外调整。";
        else if(key.endsWith("_line_gap"))detail="调整数字与单位之间的距离，支持正负值，0 不额外增加间距。";
        else if("sp".equals(item.unit))detail="以 sp 设置字号，会跟随系统字体大小缩放。";
        else if("ms".equals(item.unit))detail="以毫秒填写网速刷新间隔，1 秒等于1000毫秒。建议 1～500，间隔越短计算和更新越频繁；低于1毫秒按1毫秒处理。";
        else if("秒".equals(item.unit)||"s".equals(item.unit))detail="以秒填写时长，支持小数，例如 0.25 表示250毫秒。";
        else if("°".equals(item.unit))detail="以度填写渐变方向，不需要输入度数符号。";
        else if("%".equals(item.unit))detail="直接填写百分数，不需要输入 %；例如 100 表示100%。";
        else if("dp".equals(item.unit))detail="以 dp 设置"+item.title+"，按屏幕密度换算为实际显示距离。";
        else detail=item.description;
        return detail+"\n支持小数和科学计数法，建议范围不限制手动输入。极端值可能造成错位、不可见、卡顿或系统界面异常；实际绘制仍受物理保护。\n建议范围内直接保存；范围外先试用20秒，确认后保存，未确认自动恢复，不自动开启功能。";
    }
    private static String positionDescription(SettingsCatalog.Item item,boolean horizontal){
        String base=item.groupId.startsWith("notification_big_clock")
                ?item.key.contains("_compact_offset_")?"在初始偏移上额外调整收起位置": "相对该内容的默认布局位置调整"
                :"相对原生位置调整";
        return base+"："+(horizontal?"正值向右，负值向左":"正值向下，负值向上")+"，0 不增加偏移。";
    }
    private static String weightDescription(SettingsCatalog.Item item,Map<String,?> values){
        String mode;
        if("battery".equals(item.groupId))mode="native";
        else if("native_network_badge".equals(SettingsCatalog.networkOwner(item.key)))mode=StatusBarSettings.string(values,
                item.key.startsWith(NativeNetworkBadgeSettings.PART_ONE+"_")?NativeNetworkBadgeSettings.key(1,"font"):
                item.key.startsWith(NativeNetworkBadgeSettings.PART_TWO+"_")?NativeNetworkBadgeSettings.key(2,"font"):NativeNetworkBadgeControls.FONT);
        else if(item.groupId.startsWith("notification_big_clock"))mode=StatusBarSettings.string(values,
                "notification_big_clock_landscape".equals(item.groupId)
                        ?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.FONT):NotificationBigClockSettings.FONT);
        else mode=StatusBarSettings.bool(values,"font_enabled")?StatusBarSettings.string(values,StatusBarSettings.FONT_MODE):"system";
        if("global".equals(mode))mode=StatusBarSettings.bool(values,"font_enabled")?StatusBarSettings.string(values,StatusBarSettings.FONT_MODE):"system";
        String revision=StatusBarSettings.string(values,StatusBarSettings.FONT_REVISION);
        String scope=fontScope("network".equals(item.groupId)?SettingsCatalog.networkOwner(item.key):item.groupId);
        if(scope!=null&&FeatureOptions.from(values).enabled("font"))try{
            String assigned=FontScopes.assignment(values,scope);
            if(assigned!=null){mode=FontScopes.mode(assigned);revision=FontScopes.revision(assigned);}
        }catch(IllegalArgumentException malformed){/* The editor mirrors the runtime's legacy fallback. */}
        String source;
        if("pingfang".equals(mode))source="苹方，实际字重轴为100～900";
        else if("custom".equals(mode)){
            FontCatalog.Entry entry=FontCatalog.forRevision(revision);
            source=entry==null?"自选字体，实际范围取决于文件中的字重轴":entry.displayName+"，实际字重轴为"+entry.minWeight+"～"+entry.maxWeight;
        }else source="native".equals(mode)?"原生字体":"系统字体";
        return "当前来源："+source+"。数值越大通常越粗；保存小数原值，实际绘制映射至1～1000的整数并遵守所选字体的真实轴范围。";
    }
    private static String fontScope(String group){
        if("clock".equals(group))return FontScopes.STATUSBAR_CLOCK;
        if("shade_clock".equals(group)||"classic_clock".equals(group))return FontScopes.SHADE_CLOCK;
        if("label".equals(group)||"native_network_badge".equals(group))return FontScopes.NETWORK_TYPE;
        if("speed".equals(group))return FontScopes.NETWORK_SPEED;
        if(CarrierPanels.LOCKSCREEN.equals(group))return FontScopes.LOCKSCREEN_TEXT;
        if("carrier".equals(group)||CarrierPanels.isPanel(group))return FontScopes.SHADE_TEXT;
        if(group.startsWith("notification_big_clock"))return FontScopes.NOTIFICATION_CLOCK;
        return null;
    }
    private static String textDescription(SettingsCatalog.Item item){
        String key=NotificationBigClockSettings.portraitKey(item.key);
        if(NativeStatusIcons.PRIORITY.equals(item.key))return "按优先顺序输入，例如：蓝牙,定位,闹钟。也可使用 bluetooth,location,alarm_clock 等系统标识；列表靠前项在数量不足时优先显示，未列出的保持系统顺序。只对当前存在且未被系统屏蔽的提示生效，不创造新图标。";
        if(NotificationIconArea.TEXT.equals(item.key))return "存在可显示通知且显示方式为自定义文字时使用。可输入中文、英文或 emoji，最多12个 Unicode 字符；保持单行。";
        if(item.key.endsWith("_pattern")||LockscreenControls.DATE_FORMAT.equals(item.key))
            return patternHelp(NotificationBigClockSettings.FOOTER_PATTERN.equals(key));
        if(BigClockWidgetsSettings.textKey(item.key))return componentHelp(item.key.equals(BigClockWidgetsSettings.RIGHT_TEXT));
        return item.description+(item.description.isEmpty()?"":"\n")+"保持单行，最多"+TimeFormat.MAX_TEXT+"个字符；保存文字不会自动开启对应功能。";
    }
    /** What a format may contain, so the field is never a guess. Every token here is accepted by TimeFormat. */
    static String patternHelp(boolean footer){
        return "可用字段（输入时下方有实时预览）\n"
                +"年：yyyy 2026 · yy 26 · {年}\n"
                +"月：M 10 · MM 10（不足补零）· {月}；日：d 7 · dd 07 · {日}\n"
                +"24 小时：H 8 · HH 08 · {时}；12 小时：h 8 · hh 08 · {12时}\n"
                +"分：m 3 · mm 03 · {分}；秒：s 9 · ss 09 · {秒}\n"
                +"上午/下午：a 或 {上午下午}\n"
                +"时段：{时段}，按时间显示 凌晨、早晨、上午、中午、下午、晚上\n"
                +"星期：{周} 周三 · {星期} 星期三，也可写 E（周三）、EEEE（星期三）\n"
                +"英文星期：{星期英文简} Wed · {星期英文} Wednesday；{星期英文简大写} WED · {星期英文大写} WEDNESDAY；{星期英文简小写} wed · {星期英文小写} wednesday\n"
                +"英文月份：{月份英文简} Oct · {月份英文} October；{月份英文简大写} OCT · {月份英文大写} OCTOBER；{月份英文简小写} oct · {月份英文小写} october\n"
                +"英文上午/下午：{AMPM} AM/PM · {ampm} am/pm；英文时段：{英文时段} Noon · {英文时段大写} NOON · {英文时段小写} noon\n"
                +"中文、英文与数字可自由组合，不改变系统语言；{时段} 仍按原规则显示中午。\n"
                +"农历：{农历} 农历八月廿六 · {农历日期} 八月廿六 · {干支} 丙午年\n"
                +(footer?"底部文字：{text} 或 {文本}，插入上面已设置的底部文字，不会被当作格式\n":"")
                +"含秒（s、ss 或 {秒}）时每秒刷新一次。\n"
                +"固定文字放在成对的单引号里，例如 'C17' HH:mm；中文和符号可直接写。\n"
                +"示例：HH:mm:ss → 08:03:09；{星期英文简} HH:mm → Wed 08:03；{时段} HH:mm {星期英文简} → 早晨 08:03 Wed\n"
                +"最多"+TimeFormat.MAX_PATTERN+"个字符，单行；写错时预览会显示原因，保存前也会检查。";
    }
    /** What a left/right component may contain; every token listed is resolved by BigClockTextContent. */
    static String componentHelp(boolean right){
        return "点上方预设一键填入，也可自己输入：普通文字原样显示，可与字段混排（中英文括号均可）。输入时下方有实时预览，图标为示意。\n"
                +"\n自定义格式 （类别:模板）\n"
                +"天气、电池、信号、网络可以像时间格式一样自定义：模板里文字和 {字段} 混排，符号由你自己写。\n"
                +"{字段} 取值 · {字段:参数} 带参数 · {字段?有时显示} · {字段?有时显示|没有时显示} · {电量<=20?低|够} 比较数字（< <= > >= = !=）\n"
                +"要显示 { } | ( ) : 这些符号，把它们放进单引号，例如 '（受限）'；'' 表示一个单引号。\n"
                +"天气：{描述} {描述:中文} {描述:英文} {温度} {温度:数字} {温度:单位} {图标} {图标:原色}；条件 {夜} {昼} {有数据} {有温度}\n"
                +"电池：{电量} {电量:000}（补零）{状态} {状态:英文} {条:格数:实心:空心} {图标}；条件 {充电} {满电} {未充电} {放电} {低电} {有数据}\n"
                +"信号：{格数} {总格} {状态} {条:格数:实心:空心} {图标}；条件 {飞行} {无卡} {无服务} {有信号} {有数据}\n"
                +"网络：{类型} {制式} {状态} {WiFi格数} {条:格数:实心:空心} {图标}；条件 {联网} {WiFi} {蜂窝} {有线} {离线} {受限} {有数据}\n"
                +"示例：（电池:{电量}%{充电?⚡}）→ 85%⚡ · （电池:{条:5:█:░} {电量}%）→ ████░ 85% · （天气:{描述} {温度:数字}°C）→ 多云 23°C\n"
                +"没有数据时整个（类别:…）显示“暂无数据”；想自己写提示，用 {有数据?…|…}。模板里写错字段会在预览里提示，也不能保存。\n"
                +"\n原有写法仍然有效\n"
                +"天气：（天气）（天气中文）（天气英文）（天气原色）（天气描述）（天气图标）（温度）；状态：（蜂窝信号）（网络状态）（电池）\n"
                +"时间：（时间）（12小时）（上午下午）（时段）；日期：（日期）（年月日）（年）（星期）（周）（农历）（农历日期）（干支）\n"
                +"英文：（日期英文）Oct 17 · （星期英文）Saturday · （星期英文简）Sat · （月份英文）October\n"
                +"自定义时间格式：（格式:HH:mm {周}）与时间格式相同；不支持秒，含秒时不会替换。\n"
                +"天气来自锁屏天气小部件；固定中英文只改天气文字，不会联网翻译。最多 "+BigClockWidgetsSettings.MAX_TEXT+" 个字符。";
    }
    /** Ready-made formats ("预设") for a time-class field, as {format, name} pairs the editor offers above the input.
     * Every time-class field has some: the field's own catalog list when it has one, else a general set. */
    public static String[][] presets(SettingsCatalog.Item item){
        if(item!=null&&BigClockWidgetsSettings.textKey(item.key)){
            String[][] result=new String[item.values.length][];
            for(int i=0;i<result.length;i++)result[i]=new String[]{item.values[i],i<item.labels.length?item.labels[i]:item.values[i]};
            return result;
        }
        if(item==null||!(item.key.endsWith("_pattern")||LockscreenControls.DATE_FORMAT.equals(item.key)))return new String[0][];
        String[] values=item.values,labels=item.labels;
        if(values.length==0){
            values=new String[]{"HH:mm","HH:mm:ss","hh:mm","M月d日 {周}","M月d日 {周} HH:mm","yyyy年M月d日 {星期}","{时段} HH:mm","MM/dd HH:mm"};
            labels=new String[]{"时分 · 24 小时","时分秒","时分 · 12 小时","月日与星期","月日、星期与时间","完整日期与星期","时段与时间","短日期与时间"};
        }
        // Keep the existing presets and append independently composable choices.
        // The footer's text marker stays in its examples; no saved format changes.
        java.util.LinkedHashMap<String,String> result=new java.util.LinkedHashMap<>();
        for(int i=0;i<values.length;i++)result.put(values[i],i<labels.length?labels[i]:values[i]);
        boolean footer=NotificationBigClockSettings.FOOTER_PATTERN.equals(NotificationBigClockSettings.portraitKey(item.key));
        String suffix=footer?" {text}":"";
        result.putIfAbsent("{时段} HH:mm"+suffix,"中文：时段与时间");
        result.putIfAbsent("M月d日 {星期} HH:mm"+suffix,"中文：日期、星期与时间");
        result.putIfAbsent("{星期英文简} HH:mm"+suffix,"英文：星期简称与时间");
        result.putIfAbsent("{星期英文} HH:mm"+suffix,"英文：星期全称与时间");
        result.putIfAbsent("{月份英文简} d {星期英文简}"+suffix,"英文：月份与星期");
        result.putIfAbsent("{英文时段} hh:mm {AMPM}"+suffix,"英文：时段与 12 小时");
        result.putIfAbsent("{时段} HH:mm {星期英文简}"+suffix,"组合：中文时段与英文星期");
        result.putIfAbsent("M月d日 {星期英文简} HH:mm"+suffix,"组合：中文日期与英文星期");
        String[][] choices=new String[result.size()][];int index=0;
        for(Map.Entry<String,String> entry:result.entrySet())choices[index++]=new String[]{entry.getKey(),entry.getValue()};
        return choices;
    }
    /** Live result of a format for the editor, or the reason it cannot be saved. Empty when the field is not a format. */
    public static CharSequence preview(SettingsCatalog.Item item,String input){
        if(BigClockWidgetsSettings.textKey(item.key))return BigClockPresets.preview(input,System.currentTimeMillis());
        boolean pattern=item.key.endsWith("_pattern")||LockscreenControls.DATE_FORMAT.equals(item.key);
        if(!pattern)return "";
        try{
            long now=System.currentTimeMillis();
            String key=NotificationBigClockSettings.portraitKey(item.key);
            return NotificationBigClockSettings.FOOTER_PATTERN.equals(key)?TimeFormat.formatContent(input,"示例文字",now):TimeFormat.format(input,now);
        }catch(IllegalArgumentException invalid){
            return "格式有误："+invalid.getMessage();
        }
    }
    /** Captured before NumericTrial.begin(); includes both confirmed and proposed values. */
    public static String trialSummary(SettingsCatalog.Item item,Object confirmed,float proposed,Map<String,?> values){
        return context(item)+"\n上次确认："+formatted(item,confirmed)+"\n本次试用："+formatted(item,proposed)
                +(proposed<item.min||proposed>item.max?"\n超出滑块建议："+number(item.min)+"～"+number(item.max)+(item.unit.isEmpty()?"":" "+item.unit):"")
                +"\n"+numericDescription(item,values)+"\n取消、超时或离开应用会恢复上次确认值。";
    }
}
