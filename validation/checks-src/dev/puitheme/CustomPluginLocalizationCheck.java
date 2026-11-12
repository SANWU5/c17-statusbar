package dev.puitheme;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;

/** App291 ui.i18n: plugin translation tables are validated at import, merged with fallbacks and only translate display text. */
public final class CustomPluginLocalizationCheck {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void rejects(File folder, String why) {
        try { CustomPluginLocalization.validate(folder); check(false, why); } catch (IOException expected) { checks++; }
    }
    private static File table(File root, String name, String json) throws IOException {
        File folder = new File(root, "i18n");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("mkdir");
        File file = new File(folder, name);
        Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
        return file;
    }
    private static void clear(File root) {
        File[] files = new File(root, "i18n").listFiles();
        if (files != null) for (File file : files) if (!file.delete()) throw new AssertionError("cleanup " + file);
    }

    public static void main(String[] args) throws Exception {
        check(CustomPluginSupport.FEATURES.contains("ui.i18n"), "the host announces ui.i18n");
        File root = Files.createTempDirectory("c17-plugin-i18n").toFile();
        try {
            CustomPluginLocalization.validate(root);
            checks++; // a package without i18n/ is fine

            table(root, "en.json", "{\"更好的玻璃\":\"Better Glass\",\"启用 %s\":\"Enable %s\"}");
            table(root, "zh-Hans.json", "{\"Better Glass\":\"更好的玻璃\"}");
            table(root, "zh-Hant.json", "{\"更好的玻璃\":\"更好的玻璃（繁）\"}");
            table(root, "pt.json", "{\"更好的玻璃\":\"Vidro melhor\"}");
            CustomPluginLocalization.validate(root);
            checks++;

            check(Arrays.equals(new String[]{"en"}, CustomPluginLocalization.chain("en")), "English has no fallback");
            check(Arrays.equals(new String[]{"zh-Hans"}, CustomPluginLocalization.chain("zh-Hans")), "Simplified Chinese reads its own table");
            check(Arrays.equals(new String[]{"zh-Hant", "zh-Hans"}, CustomPluginLocalization.chain("zh-Hant")), "Traditional falls back to Simplified");
            check(Arrays.equals(new String[]{"pt", "en"}, CustomPluginLocalization.chain("pt")), "other languages fall back to English");
            check(Arrays.equals(new String[]{"pt-BR", "pt", "en"}, CustomPluginLocalization.chain("pt-BR")), "a region falls back to its language");

            String dir = root.getAbsolutePath();
            Map<String, String> pt = CustomPluginLocalization.table(dir, "pt");
            check("Vidro melhor".equals(CustomPluginLocalization.translate(pt, "更好的玻璃")), "the exact language wins");
            check("Enable %s".equals(CustomPluginLocalization.translate(pt, "启用 %s")), "a missing entry comes from English");
            check("未翻译".equals(CustomPluginLocalization.translate(pt, "未翻译")), "an unknown string stays as written");
            check(CustomPluginLocalization.translate(pt, "") .isEmpty() && CustomPluginLocalization.translate(pt, null) == null, "empty and null pass through");
            check("更好的玻璃".equals(CustomPluginLocalization.translate(CustomPluginLocalization.table(dir, "zh-Hans"), "Better Glass")),
                    "a plugin written in English can ship Simplified Chinese");
            check("更好的玻璃（繁）".equals(CustomPluginLocalization.translate(CustomPluginLocalization.table(dir, "zh-Hant"), "更好的玻璃")),
                    "Traditional Chinese uses its own table first");
            check(CustomPluginLocalization.table(dir, "ja").get("更好的玻璃").equals("Better Glass"), "a language without a table reads English");
            check(CustomPluginLocalization.table(dir, "pt") == pt, "tables are cached per resource folder and language");
            check(CustomPluginLocalization.table("", "en").isEmpty() && CustomPluginLocalization.table(dir, null).isEmpty(), "no folder, no table");
            check("x".equals(CustomPluginLocalization.forPlugin(null).tr("x")), "no plugin translates nothing");

            clear(root);
            table(root, "readme.txt", "x");
            rejects(root, "only <language>.json files");
            clear(root);
            table(root, "EN.json", "{}");
            rejects(root, "language tags are lower case");
            clear(root);
            table(root, "en_US.json", "{}");
            rejects(root, "regions use a hyphen");
            clear(root);
            table(root, "en.json", "[]");
            rejects(root, "a table is an object");
            clear(root);
            table(root, "en.json", "{\"a\":1}");
            rejects(root, "values are strings");
            clear(root);
            table(root, "en.json", "{\"a\":\"  \"}");
            rejects(root, "translations are not blank");
            clear(root);
            table(root, "en.json", "{\"a\":\"b\\u0000\"}");
            rejects(root, "no NUL characters");
            clear(root);
            StringBuilder many = new StringBuilder("{");
            for (int i = 0; i <= CustomPluginLocalization.MAX_ENTRIES; i++) many.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":\"v\"");
            table(root, "en.json", many.append('}').toString());
            rejects(root, "entry count is bounded");
            clear(root);
            StringBuilder big = new StringBuilder("{\"k\":\"");
            while (big.length() <= CustomPluginLocalization.MAX_FILE_BYTES) big.append("0123456789abcdef");
            table(root, "en.json", big.append("\"}").toString());
            rejects(root, "file size is bounded");
            clear(root);
            if (!new File(root, "i18n").delete() || !new File(root, "i18n").createNewFile()) throw new AssertionError("setup");
            rejects(root, "i18n must be a folder");
        } finally {
            File folder = new File(root, "i18n");
            File[] files = folder.listFiles();
            if (files != null) for (File file : files) file.delete();
            folder.delete();
            root.delete();
        }
        System.out.println("CustomPluginLocalizationCheck: " + checks + " checks passed");
    }
}
