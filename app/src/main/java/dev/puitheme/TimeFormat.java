// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import java.text.SimpleDateFormat;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Date patterns plus readable Chinese tokens; shared by previews and both clock locations. */
public final class TimeFormat {
    public static final String CLOCK_DEFAULT = "HH:mm";
    public static final String CARRIER_DEFAULT = "M月d日 {周} {时段} HH:mm";
    public static final int MAX_PATTERN = 200, MAX_TEXT = 120;
    private TimeFormat() { }

    public static String period(int hour) {
        if (hour < 6) return "凌晨";
        if (hour < 9) return "早晨";
        if (hour < 12) return "上午";
        if (hour < 14) return "中午";
        if (hour < 18) return "下午";
        return "晚上";
    }

    private static final class ParsedPattern {
        final StringBuilder pattern = new StringBuilder();
        final Map<Character, String> literals = new LinkedHashMap<>();
        void literal(String input, String value) {
            char marker = '\ue000';
            while (input.indexOf(marker) >= 0 || literals.containsKey(marker)) marker++;
            pattern.append(marker); literals.put(marker, value);
        }
        String render(long now, TimeZone zone) {
            SimpleDateFormat formatter = new SimpleDateFormat(pattern.toString(), Locale.SIMPLIFIED_CHINESE);
            formatter.setTimeZone(zone);
            String value = formatter.format(new Date(now));
            // Insert user text after date formatting, so English, quotes and emoji stay literal.
            StringBuilder output = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                String literal = literals.get(c);
                if (literal == null) output.append(c); else output.append(literal);
            }
            return output.toString();
        }
    }

    private static ParsedPattern parse(String input, String text, long now, TimeZone zone, boolean content) {
        if (input == null || input.trim().isEmpty() || input.length() > MAX_PATTERN)
            throw new IllegalArgumentException("时间格式不能为空，最多 " + MAX_PATTERN + " 个字符");
        if (input.indexOf('\n') >= 0 || input.indexOf('\r') >= 0)
            throw new IllegalArgumentException("状态栏时间格式请使用单行");
        Calendar date = Calendar.getInstance(zone, Locale.SIMPLIFIED_CHINESE);
        date.setTimeInMillis(now);
        ParsedPattern parsed = new ParsedPattern();
        StringBuilder result = parsed.pattern;
        LunarDate lunar = null;
        boolean quoted = false;
        for (int i = 0; i < input.length();) {
            char c = input.charAt(i);
            if (c == '\'') {
                result.append(c); i++;
                if (i < input.length() && input.charAt(i) == '\'') { result.append('\''); i++; }
                else quoted = !quoted;
            } else if (c == '{' && !quoted) {
                int end = input.indexOf('}', i);
                if (end < 0) throw new IllegalArgumentException("格式标记缺少 }");
                String token = input.substring(i + 1, end), replacement;
                boolean literal = false;
                switch (token) {
                    case "年": replacement = "yyyy"; break;
                    case "月": replacement = "MM"; break;
                    case "日": replacement = "dd"; break;
                    case "时": replacement = "HH"; break;
                    case "12时": replacement = "hh"; break;
                    case "分": replacement = "mm"; break;
                    case "秒": replacement = "ss"; break;
                    case "上午下午": replacement = date.get(Calendar.HOUR_OF_DAY) < 12 ? "上午" : "下午"; break;
                    case "时段": replacement = period(date.get(Calendar.HOUR_OF_DAY)); break;
                    case "周": replacement = "周" + "日一二三四五六".charAt(date.get(Calendar.DAY_OF_WEEK) - 1); break;
                    case "星期": replacement = "星期" + "日一二三四五六".charAt(date.get(Calendar.DAY_OF_WEEK) - 1); break;
                    case "lunar": case "农历":
                        if (lunar == null) lunar = LunarCalendar.date(now, zone, date);
                        replacement = lunar.date; literal = true; break;
                    case "lunar_date": case "农历日期":
                        if (lunar == null) lunar = LunarCalendar.date(now, zone, date);
                        replacement = lunar.date.startsWith("农历") ? lunar.date.substring(2) : lunar.date;
                        literal = true; break;
                    case "ganzhi": case "干支":
                        if (lunar == null) lunar = LunarCalendar.date(now, zone, date);
                        replacement = lunar.year; literal = true; break;
                    case "text": case "文本":
                        if (!content) throw new IllegalArgumentException("{text} 仅用于大时钟底部内容");
                        replacement = text == null ? "" : text; literal = true; break;
                    default: throw new IllegalArgumentException("未知格式标记：{" + token + "}");
                }
                if (literal) parsed.literal(input, replacement); else result.append(replacement);
                i = end + 1;
            } else { result.append(c); i++; }
        }
        if (quoted) throw new IllegalArgumentException("英文固定文字的单引号需要成对");
        return parsed;
    }

    public static String format(String input, long now, TimeZone zone) {
        return parse(input, null, now, zone, false).render(now, zone);
    }

    public static String format(String input, long now) { return format(input, now, TimeZone.getDefault()); }

    /** Footer date patterns plus {text}; inserted text is never interpreted as a date pattern. */
    public static String formatContent(String pattern, String text, long now) {
        return formatContent(pattern, text, now, TimeZone.getDefault());
    }

    /** The clock, date and footer can share one real time-zone snapshot across a minute change. */
    public static String formatContent(String pattern, String text, long now, TimeZone zone) {
        return parse(pattern, text, now, zone, true).render(now, zone);
    }

    public static String contentValidationError(String input) {
        try {
            TimeZone zone = TimeZone.getTimeZone("UTC");
            parse(input, "", 0L, zone, true).render(0L, zone);
            return null;
        } catch (IllegalArgumentException error) { return error.getMessage(); }
    }

    public static String validationError(String input) {
        try { format(input, 0L, TimeZone.getTimeZone("UTC")); return null; }
        catch (IllegalArgumentException error) { return error.getMessage(); }
    }

    public static boolean hasSeconds(String input) {
        return secondsIn(parse(input, null, 0L, TimeZone.getTimeZone("UTC"), false).pattern.toString());
    }

    public static boolean contentHasSeconds(String input) {
        return secondsIn(parse(input, "", 0L, TimeZone.getTimeZone("UTC"), true).pattern.toString());
    }

    private static boolean secondsIn(String parsed) {
        boolean quoted = false;
        for (int i = 0; i < parsed.length(); i++) {
            char c = parsed.charAt(i);
            if (c == '\'') {
                if (i + 1 < parsed.length() && parsed.charAt(i + 1) == '\'') i++;
                else quoted = !quoted;
            } else if (!quoted && (c == 's' || c == 'S')) return true;
        }
        return false;
    }

    private static final class LunarDate {
        final String date, year;
        LunarDate(String date, String year) { this.date = date; this.year = year; }
    }

    /** Android's astronomical ICU calendar supports leap months without a limited year table. */
    private static final class LunarCalendar {
        private static final String[] MONTHS = {"正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"};
        private static final String DIGITS = "一二三四五六七八九十";
        private static Constructor<?> constructor;
        private static Method setTime, get, setZone, zoneForId;
        static {
            // Reflection keeps ordinary date formatting usable in tools without Android ICU.
            try {
                Class<?> calendar = Class.forName("android.icu.util.ChineseCalendar");
                Class<?> timezone = Class.forName("android.icu.util.TimeZone");
                constructor = calendar.getConstructor();
                setTime = calendar.getMethod("setTimeInMillis", Long.TYPE);
                get = calendar.getMethod("get", Integer.TYPE);
                setZone = calendar.getMethod("setTimeZone", timezone);
                zoneForId = timezone.getMethod("getTimeZone", String.class);
            } catch (ReflectiveOperationException unavailable) { constructor = null; }
        }
        static LunarDate date(long now, TimeZone zone, Calendar solar) {
            if (constructor != null) {
                try {
                    Object calendar = constructor.newInstance();
                    setZone.invoke(calendar, zoneForId.invoke(null, zone.getID()));
                    setTime.invoke(calendar, now);
                    int month = ((Number) get.invoke(calendar, Calendar.MONTH)).intValue();
                    int day = ((Number) get.invoke(calendar, Calendar.DAY_OF_MONTH)).intValue();
                    int cycleYear = ((Number) get.invoke(calendar, Calendar.YEAR)).intValue();
                    boolean leap = ((Number) get.invoke(calendar, 22)).intValue() != 0; // ICU IS_LEAP_MONTH.
                    if (month >= 0 && month < 12 && day >= 1 && day <= 30 && cycleYear >= 1 && cycleYear <= 60) {
                        int yearIndex = cycleYear - 1;
                        String year = "甲乙丙丁戊己庚辛壬癸".charAt(yearIndex % 10)
                                + "" + "子丑寅卯辰巳午未申酉戌亥".charAt(yearIndex % 12) + "年";
                        return new LunarDate("农历" + (leap ? "闰" : "") + MONTHS[month] + "月" + day(day), year);
                    }
                } catch (ReflectiveOperationException | RuntimeException unavailable) { /* Use an explicit solar fallback. */ }
            }
            return new LunarDate("公历" + (solar.get(Calendar.MONTH) + 1) + "月" + solar.get(Calendar.DAY_OF_MONTH) + "日",
                    solar.get(Calendar.YEAR) + "年");
        }
        private static String day(int day) {
            if (day == 10) return "初十";
            if (day == 20) return "二十";
            if (day == 30) return "三十";
            return (day < 10 ? "初" : day < 20 ? "十" : "廿") + DIGITS.charAt((day - 1) % 10);
        }
    }

    public static long nextDelay(long now, boolean seconds) {
        long interval = seconds ? 1000L : 60000L;
        return interval - Math.floorMod(now, interval);
    }
}
