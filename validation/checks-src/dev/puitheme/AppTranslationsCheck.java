package dev.puitheme;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * App291 interface translations (tools/i18n): every finished language has a complete table for the app and for both
 * bundled plugins; a table holds only current source strings; placeholders, typed tokens and line breaks survive.
 */
public final class AppTranslationsCheck {
    /**
     * Languages still being translated. Their tables may be partial or missing, since the app and the plugins show
     * English for what is not there; what they hold must still be valid. Empty since App291 finished all 29; a newly
     * offered language goes here until tools/i18n/merge.py reports all three of its tables complete.
     */
    private static final Set<String> IN_PROGRESS = Collections.emptySet();
    private static final String FORMAT_WORDS = "天气|日期|周|年月日|星期|农历|农历日期|干支|时间|时段|上午下午|12小时|电池|信号|蜂窝信号|网络|网络状态|格式|"
            + "日期英文|星期英文简|星期英文|天气原色|天气中文|天气英文|年|月|日";
    /** The same pieces as tools/i18n/i18n_common.py VERBATIM. */
    private static final Pattern VERBATIM = Pattern.compile(
            "%(?:\\d+\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?[sdfxXc%](?![A-Za-z\\u00c0-\\u024f])"
            + "|\\{[^{}\\n]{0,40}\\}"
            + "|（(?:" + FORMAT_WORDS + ")(?:[:：][^（）\\n]*)?）"
            + "|(?<!\\p{L})(?:y{2,4}|M{1,2}|d{1,2})(?=[年月日/\\-])[年月日/\\-]?(?:d{1,2}日?)?"
            + "|\\b[Hh]{1,2}:mm(?::ss)?\\b");
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }

    private static JSONObject read(File file) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    private static Set<String> sources(File file) throws Exception {
        Set<String> result = new HashSet<>();
        JSONArray strings = read(file).getJSONArray("strings");
        for (int i = 0; i < strings.length(); i++) result.add(strings.getJSONObject(i).getString("text"));
        return result;
    }

    private static List<String> pieces(String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = VERBATIM.matcher(text);
        while (matcher.find()) result.add(matcher.group());
        Collections.sort(result);
        return result;
    }

    private static int lines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') count++;
        return count;
    }

    private static void table(String where, File file, Set<String> sources, boolean complete) throws Exception {
        JSONObject table = read(file);
        Iterator<String> keys = table.keys();
        Set<String> seen = new HashSet<>();
        while (keys.hasNext()) {
            String source = keys.next();
            Object value = table.get(source);
            check(sources.contains(source), where + ": stale entry, run tools/i18n/merge.py: " + source);
            check(value instanceof String && !((String) value).trim().isEmpty(), where + ": empty translation for " + source);
            String text = (String) value;
            check(pieces(source).equals(pieces(text)), where + ": placeholders or tokens differ for " + source + " -> " + text);
            check(lines(source) == lines(text), where + ": line breaks differ for " + source);
            seen.add(source);
        }
        Set<String> missing = new HashSet<>(sources);
        missing.removeAll(seen);
        if (complete) check(missing.isEmpty(), where + ": " + missing.size() + " strings untranslated, e.g. " + (missing.isEmpty() ? "" : missing.iterator().next()));
        else System.out.println("  in progress " + where + ": " + seen.size() + "/" + sources.size());
    }

    public static void main(String[] args) throws Exception {
        String[][] targets = {
                {"app/src/main/assets/i18n", "tools/i18n/app-source.json"},
                {"plugins/liquid-glass/i18n", "plugins/liquid-glass/i18n-source.json"},
                {"plugins/control-center-free-edit/i18n", "plugins/control-center-free-edit/i18n-source.json"},
        };
        for (String[] target : targets) {
            // The author's plugins (and their tables) are not part of the public repository.
            if (target[0].startsWith("plugins/") && !new File(target[1]).isFile()) continue;
            Set<String> sources = sources(new File(target[1]));
            check(!sources.isEmpty(), target[1] + " lists the strings to translate");
            for (C17Language.Option option : C17Language.OPTIONS) {
                if (option.tag.isEmpty() || C17Language.DEFAULT_TAG.equals(option.tag)) continue;
                File file = new File(target[0], option.tag + ".json");
                boolean pending = IN_PROGRESS.contains(option.tag);
                if (!file.isFile()) {
                    check(pending, "missing table " + file);
                    System.out.println("  in progress " + file.getPath() + ": none yet");
                    continue;
                }
                table(file.getPath(), file, sources, !pending);
            }
        }
        // Without a loaded table the app shows its source text unchanged (desktop checks, other processes).
        check("状态栏".equals(C17Text.tr("状态栏")) && C17Text.tr(null) == null && "".equals(C17Text.tr("")), "no table, source text");
        check("已应用 3 项".equals(C17Text.format("已应用 %d 项", 3)), "templates format as given");
        check(C17Text.source(), "no table loaded on the desktop");
        System.out.println("AppTranslationsCheck: " + checks + " checks passed");
    }
}
