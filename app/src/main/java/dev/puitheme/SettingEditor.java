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
        boolean numeric=SettingsCatalog.NUMERIC.equals(item.type),integer=NotificationIconArea.MAX_COUNT.equals(item.key);
        boolean pattern=item.key.endsWith("_pattern"),signed=NumericPolicy.signed(item.key);
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
        if("carrier".equals(item.groupId))title=item.key.startsWith(CarrierPanels.LOCKSCREEN)?"锁屏运营商文字"
                :item.key.startsWith(CarrierPanels.CONTROL)?"控制中心运营商文字":"通知栏运营商文字";
        int separator=item.section.lastIndexOf(" · ");
        String section=separator>=0?item.section.substring(separator+3):item.section;
        return title+(section.isEmpty()?"":" · "+section);
    }
    private static String numericDescription(SettingsCatalog.Item item,Map<String,?> values){
        String key=item.key;
        if(QsTileCorners.RADIUS.equals(key))return "统一调整磁贴、音乐、亮度、音量与设备卡片的圆角。0 为直角，允许 0～30 dp；手动输入的小数不按滑块步长取整。\n先试用20秒，确认后保存。";
        if(NotificationIconArea.MAX_COUNT.equals(key))return "仅用于原生通知图标，必须为非负整数。0 表示不显示图标，不删除通知；可超过滑块建议数量。\n先试用20秒，确认后保存。";
        String detail;
        if(key.endsWith("_weight")||StatusBarSettings.FONT_WEIGHT.equals(key))detail=weightDescription(item,values);
        else if(key.endsWith("offset_x"))detail=positionDescription(item,true);
        else if(key.endsWith("offset_y"))detail=positionDescription(item,false);
        else if("em".equals(item.unit))detail="按字号比例调整字距：1 em 等于当前字号，正值放宽、负值收紧，0 不额外调整。";
        else if(key.endsWith("_spacing"))detail="在原生字距上增加或减少所填距离，正值放宽、负值收紧，0 不额外调整。";
        else if(key.endsWith("_line_gap"))detail="调整数字与单位之间的距离，支持正负值，0 不额外增加间距。";
        else if("sp".equals(item.unit))detail="以 sp 设置字号，会跟随系统字体大小缩放。";
        else if("秒".equals(item.unit))detail="以秒填写时长，支持小数，例如 0.25 表示250毫秒。";
        else if("°".equals(item.unit))detail="以度填写渐变方向，不需要输入度数符号。";
        else if("%".equals(item.unit))detail="直接填写百分数，不需要输入 %；例如 100 表示100%。";
        else if("dp".equals(item.unit))detail="以 dp 设置"+item.title+"，按屏幕密度换算为实际显示距离。";
        else detail=item.description;
        return detail+"\n支持小数和科学计数法，建议范围不限制手动输入。极端值可能造成错位、不可见、卡顿或系统界面异常；实际绘制仍受物理保护。\n先试用20秒，确认后保存；未确认自动恢复，不自动开启功能。";
    }
    private static String positionDescription(SettingsCatalog.Item item,boolean horizontal){
        String base="notification_big_clock".equals(item.groupId)
                ?item.key.contains("_compact_offset_")?"在初始偏移上额外调整收起位置": "相对该内容的默认布局位置调整"
                :"相对原生位置调整";
        return base+"："+(horizontal?"正值向右，负值向左":"正值向下，负值向上")+"，0 不增加偏移。";
    }
    private static String weightDescription(SettingsCatalog.Item item,Map<String,?> values){
        String mode;
        if("battery".equals(item.groupId))mode="native";
        else if("notification_big_clock".equals(item.groupId))mode=StatusBarSettings.string(values,NotificationBigClockSettings.FONT);
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
        if(NotificationIconArea.TEXT.equals(item.key))return "存在可显示通知且显示方式为自定义文字时使用。可输入中文、英文或 emoji，最多12个 Unicode 字符；保持单行。";
        if(item.key.endsWith("_pattern"))return (NotificationBigClockSettings.FOOTER_PATTERN.equals(item.key)
                ?"底部内容格式：{text} 插入已设置的纯文本；例如 HH:mm {text}。"
                :NotificationBigClockSettings.DATE_PATTERN.equals(item.key)?"日期格式：例如 M月d日 {星期}，支持 {农历} 与 {干支}。"
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
