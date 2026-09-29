package dev.puitheme;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
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

    private static String pattern(String input, long now, TimeZone zone) {
        if (input == null || input.trim().isEmpty() || input.length() > MAX_PATTERN)
            throw new IllegalArgumentException("时间格式不能为空，最多 " + MAX_PATTERN + " 个字符");
        if (input.indexOf('\n') >= 0 || input.indexOf('\r') >= 0)
            throw new IllegalArgumentException("状态栏时间格式请使用单行");
        Calendar date = Calendar.getInstance(zone, Locale.SIMPLIFIED_CHINESE);
        date.setTimeInMillis(now);
        StringBuilder result = new StringBuilder();
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
                    default: throw new IllegalArgumentException("未知格式标记：{" + token + "}");
                }
                result.append(replacement); i = end + 1;
            } else { result.append(c); i++; }
        }
        if (quoted) throw new IllegalArgumentException("英文固定文字的单引号需要成对");
        return result.toString();
    }

    public static String format(String input, long now, TimeZone zone) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern(input, now, zone), Locale.SIMPLIFIED_CHINESE);
        formatter.setTimeZone(zone);
        return formatter.format(new Date(now));
    }

    public static String format(String input, long now) { return format(input, now, TimeZone.getDefault()); }

    public static String validationError(String input) {
        try { format(input, 0L, TimeZone.getTimeZone("UTC")); return null; }
        catch (IllegalArgumentException error) { return error.getMessage(); }
    }

    public static boolean hasSeconds(String input) {
        String parsed = pattern(input, 0L, TimeZone.getTimeZone("UTC"));
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

    public static long nextDelay(long now, boolean seconds) {
        long interval = seconds ? 1000L : 60000L;
        return interval - Math.floorMod(now, interval);
    }
}
