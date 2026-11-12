// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.util.Iterator;
import org.json.JSONObject;

/** Reversible runtime overlays. Plugins never write the user's saved module preferences. */
public final class CustomPluginSettings {
    public static final int MAX_SETTINGS = 64;
    private CustomPluginSettings() { }

    public static void validate(JSONObject settings) {
        if (settings == null) throw new IllegalArgumentException("插件设置不能为空");
        if (settings.length() > MAX_SETTINGS) throw new IllegalArgumentException("每个插件最多覆盖 64 项设置");
        Iterator<String> keys = settings.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = settings.opt(key);
            // Published API 1 outputs remain readable after their host implementation retires.
            if(QsCardSettings.BOOLEANS.containsKey(key)) {
                if(!(value instanceof Boolean))throw new IllegalArgumentException("旧版卡片开关必须为布尔值："+key);
                continue;
            }
            if(QsCardSettings.NUMBERS.containsKey(key)) {
                if(!(value instanceof Number))throw new IllegalArgumentException("旧版卡片数值类型错误："+key);
                double number=((Number)value).doubleValue();
                if(!Double.isFinite(number)|| (QsCardSettings.GRID_ROWS.equals(key)
                        ? !Upgrade196.validRows(value) : number<0d||number>100d))
                    throw new IllegalArgumentException("旧版卡片数值超出范围："+key);
                continue;
            }
            if(QsCardSettings.STRINGS.containsKey(key)) {
                if(!(value instanceof String)||((String)value).length()>512
                        || QsCardSettings.SMALL_MODE.equals(key)&&!QsCardSettings.validSmallMode(value))
                    throw new IllegalArgumentException("旧版卡片文字设置无效："+key);
                continue;
            }
            SettingsCatalog.Item item = SettingsCatalog.item(key);
            if (item == null || SettingsCatalog.hiddenKeys().contains(key)
                    || StatusBarSettings.SAFE_MODE.equals(key)
                    || StatusBarSettings.DIAGNOSTICS_ENABLED.equals(key)
                    || !(SettingsCatalog.BOOLEAN.equals(item.type) || SettingsCatalog.NUMERIC.equals(item.type)
                         || SettingsCatalog.STRING.equals(item.type) || SettingsCatalog.OPTIONS.equals(item.type)
                         || SettingsCatalog.COLOR.equals(item.type)))
                throw new IllegalArgumentException("插件不能覆盖此设置：" + key);
            if (value == null || value == JSONObject.NULL) throw new IllegalArgumentException("设置值为空：" + key);
            if (value instanceof String && ((String) value).length() > 512)
                throw new IllegalArgumentException("插件文字设置过长：" + key);
            String error = SettingsCatalog.validationError(item, value);
            if (error != null) throw new IllegalArgumentException(key + "：" + error);
            if (SettingsCatalog.NUMERIC.equals(item.type)) {
                double number = ((Number) value).doubleValue();
                if (!Double.isFinite(number) || number < item.min || number > item.max)
                    throw new IllegalArgumentException(key + " 应在 " + item.min + " 到 " + item.max + " 之间");
            }
            if (SettingsCatalog.COLOR.equals(item.type)) {
                double number = ((Number) value).doubleValue();
                if (!Double.isFinite(number) || number != Math.rint(number)
                        || number < Integer.MIN_VALUE || number > 0xffffffffL)
                    throw new IllegalArgumentException("颜色必须为 32 位 ARGB 整数：" + key);
            }
        }
    }

    public static Bundle overlay(Bundle saved, JSONObject settings) {
        if (saved == null) return null;
        Bundle result = new Bundle(saved);
        if (saved.getBoolean(StatusBarSettings.SAFE_MODE, false) || settings == null || settings.length() == 0) return result;
        try {
            // A compiled aggregate may contain several plugins, each limited independently.
            Iterator<String> keys = settings.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if(QsCardSettings.BOOLEANS.containsKey(key)||QsCardSettings.NUMBERS.containsKey(key)
                        ||QsCardSettings.STRINGS.containsKey(key))continue;
                JSONObject one = new JSONObject(); one.put(key, settings.get(key)); validate(one);
                SettingsCatalog.Item item = SettingsCatalog.item(key);
                Object value = settings.get(key);
                if (SettingsCatalog.BOOLEAN.equals(item.type)) result.putBoolean(key, (Boolean) value);
                else if (SettingsCatalog.NUMERIC.equals(item.type)) result.putFloat(key, ((Number) value).floatValue());
                else if (SettingsCatalog.COLOR.equals(item.type)) result.putInt(key, ((Number) value).intValue());
                else result.putString(key, (String) value);
            }
            return result;
        } catch (Exception invalid) {
            ModuleDiagnostics.error("plugins", "Invalid plugin overlay; using saved settings", invalid);
            return new Bundle(saved);
        }
    }
}
