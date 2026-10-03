// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.math.BigDecimal;
import java.util.Map;

/** Editor context from the real catalog; suggested slider bounds never become input limits. */
public final class SettingEditor {
    private SettingEditor(){ }
    public static final class Info {
        public final String context,label,initial,valueSummary,description;
        public final boolean numeric,integer,signed,pattern;
        private Info(String context,String label,String initial,String valueSummary,String description,
                boolean numeric,boolean integer,boolean signed,boolean pattern){
            this.context=context;this.label=label;this.initial=initial;this.valueSummary=valueSummary;
            this.description=description;this.numeric=numeric;this.integer=integer;this.signed=signed;this.pattern=pattern;
        }
    }
    /** Prefer the confirmed raw number over a rendering guard's bounded result. Viewing never writes it. */
    public static Object storedValue(SettingsCatalog.Item item,Map<String,?> values){
        Object raw=values==null?null:values.get(item.key);
        if(SettingsCatalog.NUMERIC.equals(item.type)&&raw instanceof Number
                &&Float.isFinite(((Number)raw).floatValue()))return raw;
        return SettingsCatalog.value(values,item.key);
    }
    public static String number(float value){
        if(!Float.isFinite(value))return Float.toString(value);
        if(Math.abs(value)>=1e7f||value!=0f&&Math.abs(value)<.001f)return Float.toString(value);
        return new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
    }
    public static String formatted(SettingsCatalog.Item item,Object value){
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
                ||NotificationBigClockSettings.VISIBLE_COUNT.equals(NotificationBigClockSettings.portraitKey(item.key));
        boolean pattern=item.key.endsWith("_pattern")||LockscreenControls.DATE_FORMAT.equals(item.key),signed=NumericPolicy.signed(item.key);
        String initial=value==null?"":numeric?valueText(value):value.toString();
        String summary=numeric?"当前值："+formatted(item,value)+"\n默认值："+formatted(item,item.defaultValue)
                +"\n"+(QsTileCorners.RADIUS.equals(item.key)?"允许范围：":"滑块建议：")
                +number(item.min)+"～"+number(item.max)+(item.unit.isEmpty()?"":" "+item.unit):"";
        String label=numeric?(item.unit.isEmpty()?"字重（无单位）":item.unit.equals("个")?"数量（个）":"数值（"+item.unit+"）")
                :pattern?"自定义格式":"单行文字";
        if(numeric&&item.unit.isEmpty()&&!item.key.endsWith("_weight")&&!item.key.equals(StatusBarSettings.FONT_WEIGHT))label="数值（无单位）";
        String description=numeric?numericDescription(item,values):textDescription(item);
        return new Info(context(item),label,initial,summary,description,numeric,integer,signed,pattern);
    }
    public static String context(SettingsCatalog.Item item){
        SettingsCatalog.Group group=SettingsCatalog.group(item.groupId);
        String title=group==null?item.groupId:group.title;
        if("native_network_badge".equals(item.groupId)) {
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
        if(QsTileCorners.RADIUS.equals(key))return "统一调整磁贴、音乐、亮度、音量与设备卡片的圆角。0 为直角，允许 0～30 dp；手动输入的小数不按滑块步长取整。\n建议范围内直接保存，不自动开启功能。";
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
        else if(key.endsWith("offset_x"))detail=positionDescription(item,true);
        else if(key.endsWith("offset_y"))detail=positionDescription(item,false);
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
        else if("native_network_badge".equals(item.groupId))mode=StatusBarSettings.string(values,
                item.key.startsWith(NativeNetworkBadgeSettings.PART_ONE+"_")?NativeNetworkBadgeSettings.key(1,"font"):
                item.key.startsWith(NativeNetworkBadgeSettings.PART_TWO+"_")?NativeNetworkBadgeSettings.key(2,"font"):NativeNetworkBadgeControls.FONT);
        else if(item.groupId.startsWith("notification_big_clock"))mode=StatusBarSettings.string(values,
                "notification_big_clock_landscape".equals(item.groupId)
                        ?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.FONT):NotificationBigClockSettings.FONT);
        else mode=StatusBarSettings.bool(values,"font_enabled")?StatusBarSettings.string(values,StatusBarSettings.FONT_MODE):"system";
        if("global".equals(mode))mode=StatusBarSettings.bool(values,"font_enabled")?StatusBarSettings.string(values,StatusBarSettings.FONT_MODE):"system";
        String source;
        if("pingfang".equals(mode))source="苹方，实际字重轴为100～900";
        else if("custom".equals(mode)){
            FontCatalog.Entry entry=FontCatalog.forRevision(StatusBarSettings.string(values,StatusBarSettings.FONT_REVISION));
            source=entry==null?"自选字体，实际范围取决于文件中的字重轴":entry.displayName+"，实际字重轴为"+entry.minWeight+"～"+entry.maxWeight;
        }else source="native".equals(mode)?"原生字体":"系统字体";
        return "当前来源："+source+"。数值越大通常越粗；保存小数原值，实际绘制映射至1～1000的整数并遵守所选字体的真实轴范围。";
    }
    private static String textDescription(SettingsCatalog.Item item){
        String key=NotificationBigClockSettings.portraitKey(item.key);
        if(NativeStatusIcons.PRIORITY.equals(item.key))return "按优先顺序输入，例如：蓝牙,定位,闹钟。也可使用 bluetooth,location,alarm_clock 等系统标识；列表靠前项在数量不足时优先显示，未列出的保持系统顺序。只对当前存在且未被系统屏蔽的提示生效，不创造新图标。";
        if(NotificationIconArea.TEXT.equals(item.key))return "存在可显示通知且显示方式为自定义文字时使用。可输入中文、英文或 emoji，最多12个 Unicode 字符；保持单行。";
        if(item.key.endsWith("_pattern"))return (NotificationBigClockSettings.FOOTER_PATTERN.equals(key)
                ?"底部内容格式：{text} 插入已设置的纯文本；例如 HH:mm {text}。"
                :NotificationBigClockSettings.DATE_PATTERN.equals(key)?"日期格式：例如 M月d日 {星期}，支持 {农历} 与 {干支}。"
                :"时间格式：HH:mm 为24小时制，hh:mm 为12小时制，HH:mm:ss 显示秒；可使用 {星期} 与 {时段}。")
                +"\n最多"+TimeFormat.MAX_PATTERN+"个字符，保持单行；英文固定文字使用成对单引号。";
        return item.description+(item.description.isEmpty()?"":"\n")+"保持单行，最多"+TimeFormat.MAX_TEXT+"个字符；保存文字不会自动开启对应功能。";
    }
    /** Captured before NumericTrial.begin(); includes both confirmed and proposed values. */
    public static String trialSummary(SettingsCatalog.Item item,Object confirmed,float proposed,Map<String,?> values){
        return context(item)+"\n上次确认："+formatted(item,confirmed)+"\n本次试用："+formatted(item,proposed)
                +(proposed<item.min||proposed>item.max?"\n超出滑块建议："+number(item.min)+"～"+number(item.max)+(item.unit.isEmpty()?"":" "+item.unit):"")
                +"\n"+numericDescription(item,values)+"\n取消、超时或离开应用会恢复上次确认值。";
    }
}
