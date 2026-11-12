package dev.puitheme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The welcome page reads every key in every offered language: same welcome keys and placeholders everywhere, the
 * retired single introduction gone, each offered language backed by a resource folder and the system locale list,
 * and the typing guide's prefix count. Resource files are read from the checkout; nothing is rendered.
 */
public final class WelcomeResourcesCheck {
    private static int checks;
    private static final Pattern STRING = Pattern.compile("<string name=\"([a-z0-9_]+)\">(.*?)</string>", Pattern.DOTALL);
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[sdf]");
    private static final Pattern REFERENCE = Pattern.compile("R\\.string\\.([a-z0-9_]+)");

    private static void equal(Object expected, Object actual, String why) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Welcome resources " + checks + " " + why + ": " + expected + " != " + actual);
    }
    private static void require(boolean value, String why) { equal(true, value, why); }

    private static Map<String, String> strings(Path file) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = STRING.matcher(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        while (matcher.find()) require(result.put(matcher.group(1), matcher.group(2)) == null, "unique key " + matcher.group(1) + " in " + file);
        return result;
    }
    private static boolean welcomeKey(String key) { return key.startsWith("welcome_") || key.startsWith("about_language") || key.startsWith("about_welcome"); }
    private static List<String> placeholders(String value) {
        List<String> result = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }
    /** C17Language tags to their resource folders; Android keeps the legacy codes for Hebrew and Indonesian. */
    private static String folder(String tag) {
        switch (tag) {
            case "zh-Hans": return "values";
            case "zh-Hant": return "values-b+zh+Hant";
            case "he": return "values-iw";
            case "id": return "values-in";
            default: return "values-" + tag;
        }
    }

    private static void resources() throws IOException {
        Path res = Paths.get("app/src/main/res");
        Map<String, String> source = strings(res.resolve("values/strings.xml"));
        Set<String> keys = new TreeSet<>();
        for (String key : source.keySet()) if (welcomeKey(key)) keys.add(key);
        equal(false, keys.contains("welcome_intro_body"), "the single introduction is split into features");
        for (String key : new String[] {"welcome_hello", "welcome_feature_statusbar_title", "welcome_feature_statusbar_body",
                "welcome_feature_panel_title", "welcome_feature_panel_body", "welcome_feature_plugin_title",
                "welcome_feature_plugin_body", "welcome_requirement", "welcome_confirm_required", "welcome_confirm_matched",
                "welcome_review_note", "welcome_free_phrase", "welcome_plugin_paid_badge", "welcome_plugin_paid_title",
                "welcome_plugin_paid_body", "welcome_greeting_continue", "welcome_support_title", "welcome_telegram_title",
                "welcome_telegram_body", "welcome_telegram_join", "welcome_donate_title", "welcome_donate_body",
                "welcome_donate_warning", "welcome_copy", "welcome_copied", "welcome_copy_failed"})
            require(keys.contains(key), "source key " + key);

        // Every string the page and the About entry ask for exists in the source language.
        for (String file : new String[] {"app/src/main/java/dev/puitheme/WelcomeFlow.kt", "app/src/main/java/dev/puitheme/ModernPages.kt"}) {
            Matcher matcher = REFERENCE.matcher(new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8));
            while (matcher.find()) if (welcomeKey(matcher.group(1))) require(source.containsKey(matcher.group(1)), file + " uses " + matcher.group(1));
        }

        Set<String> configured = new TreeSet<>();
        Matcher locale = Pattern.compile("<locale android:name=\"([^\"]+)\"").matcher(
                new String(Files.readAllBytes(res.resolve("xml/locales_config.xml")), StandardCharsets.UTF_8));
        while (locale.find()) configured.add(locale.group(1));
        Set<String> offered = new TreeSet<>();
        for (C17Language.Option option : C17Language.OPTIONS) if (!option.tag.isEmpty()) offered.add(option.tag);
        equal(offered, configured, "system per-app language list matches the in-app list");
        equal(30, offered.size(), "offered languages");

        Set<String> folders = new TreeSet<>();
        for (String tag : offered) {
            String name = folder(tag);
            require(folders.add(name), "one folder per language " + tag);
            Path file = res.resolve(name).resolve("strings.xml");
            require(Files.isRegularFile(file), "strings for " + tag);
            Map<String, String> translated = strings(file);
            Set<String> present = new TreeSet<>();
            for (String key : translated.keySet()) if (welcomeKey(key)) present.add(key);
            equal(keys, present, "welcome keys in " + name);
            for (String key : keys) {
                String value = translated.get(key);
                require(!value.trim().isEmpty(), "text for " + key + " in " + name);
                equal(placeholders(source.get(key)), placeholders(value), "placeholders of " + key + " in " + name);
                // aapt rejects an unescaped apostrophe outside quotes; catch it here with the file name.
                require(!Pattern.compile("(?<!\\\\)'").matcher(value).find(), "escaped apostrophes in " + key + " of " + name);
            }
            require(!translated.containsKey("welcome_intro_body"), "retired introduction absent in " + name);
            // The author asked for a general plugin line: it must not name the plugins of the day.
            String plugins = translated.get("welcome_feature_plugin_body").toLowerCase(java.util.Locale.ROOT);
            for (String named : new String[] {"liquid glass", "液态玻璃", "液態玻璃", "液体ガラス", "리퀴드 글래스"})
                require(!plugins.contains(named), "general plugin line in " + name);
        }
        // The developer chip shows the author's own avatar from a small bundled image.
        Path avatar = res.resolve("drawable-nodpi/welcome_developer_avatar.webp");
        require(Files.isRegularFile(avatar) && Files.size(avatar) < 32 * 1024, "small bundled avatar");
        require(new String(Files.readAllBytes(Paths.get("app/src/main/java/dev/puitheme/WelcomeFlow.kt")), StandardCharsets.UTF_8)
                .contains("R.drawable.welcome_developer_avatar"), "avatar shown on the page");
        // No translation folder outside the offered list carries a partial welcome page.
        try (java.util.stream.Stream<Path> all = Files.list(res)) {
            for (Path directory : (Iterable<Path>) all::iterator) {
                Path file = directory.resolve("strings.xml");
                if (!Files.isRegularFile(file)) continue;
                boolean welcome = strings(file).containsKey("welcome_tagline");
                if (welcome) require(folders.contains(directory.getFileName().toString()), "offered folder " + directory.getFileName());
            }
        }
    }

    /** The author's plugins may be paid: the page and the About notice both say so, next to "free forever". */
    private static void paidPlugins() throws IOException {
        String page = new String(Files.readAllBytes(Paths.get("app/src/main/java/dev/puitheme/WelcomeFlow.kt")), StandardCharsets.UTF_8);
        for (String key : new String[] {"R.string.welcome_plugin_paid_badge", "R.string.welcome_plugin_paid_title", "R.string.welcome_plugin_paid_body"})
            require(page.contains(key), "page shows " + key);
        require(page.indexOf("R.string.welcome_free_body") < page.indexOf("R.string.welcome_plugin_paid_title"), "notice follows free forever");
        require(FreeNotice.DESCRIPTION.contains("可能收费"), "About free notice mentions paid plugins");
        require(!FreeNotice.PHRASE.contains("插件"), "typed phrase stays about the module itself");
    }

    private static void prefix() {
        String phrase = FreeNotice.PHRASE;
        equal(0, FreeNotice.correctPrefix("", phrase), "nothing typed");
        equal(0, FreeNotice.correctPrefix(null, phrase), "no text");
        equal(0, FreeNotice.correctPrefix("x", null), "no phrase");
        equal(phrase.length(), FreeNotice.correctPrefix(phrase, phrase), "whole phrase");
        equal(3, FreeNotice.correctPrefix(phrase.substring(0, 3), phrase), "typed beginning");
        equal(5, FreeNotice.correctPrefix(phrase.substring(0, 5) + "X" + phrase.substring(6), phrase), "stops at the first wrong character");
        equal(phrase.length(), FreeNotice.correctPrefix(phrase + "!", phrase), "extra text after the phrase");
        // A wrong full stop is still wrong: the guide never accepts what matching would reject.
        String loose = phrase.replace("。", ".");
        equal(loose.length() - 1, FreeNotice.correctPrefix(loose, phrase), "punctuation counts");
        equal(false, FreeNotice.matches(loose, null), "loose punctuation does not confirm");
    }

    /**
     * App285: the first page greets in every offered language and nothing else; the support block shows the Telegram
     * group and the crypto addresses (since App292 Ethereum (ERC20) and Tron (TRC20), each checked).
     */
    private static void greetingAndSupport() throws IOException {
        String greetings = new String(Files.readAllBytes(Paths.get("app/src/main/java/dev/puitheme/WelcomeGreetings.kt")), StandardCharsets.UTF_8);
        List<String> tags = new ArrayList<>();
        Matcher entry = Pattern.compile("\"([A-Za-z-]+)\" to \"([^\"]+)\"").matcher(greetings);
        while (entry.find()) tags.add(entry.group(1));
        Set<String> offered = new TreeSet<>();
        for (C17Language.Option option : C17Language.OPTIONS) if (!option.tag.isEmpty()) offered.add(option.tag);
        equal(offered.size(), tags.size(), "one greeting per offered language");
        equal(offered, new TreeSet<>(tags), "greetings cover the offered languages");

        String page = new String(Files.readAllBytes(Paths.get("app/src/main/java/dev/puitheme/WelcomeFlow.kt")), StandardCharsets.UTF_8);
        require(page.contains("WelcomeGreetingPage(") && page.contains("WelcomeGreetings.ORDER"), "greeting page cycles the words");
        require(page.indexOf("R.string.welcome_free_body") < page.indexOf("R.string.welcome_support_title"), "support follows free forever");
        for (String constant : new String[] {"CRYPTO_ETH_NETWORK", "CRYPTO_ETH_ADDRESS", "CRYPTO_TRON_NETWORK", "CRYPTO_TRON_ADDRESS", "TELEGRAM_URL"})
            require(page.contains("ProjectContact." + constant), "page shows " + constant);
        require(!page.contains("XKO") && !page.contains("X Layer"), "the X Layer entry is retired");

        // App292: the author's two addresses, Ethereum (ERC20) and Tron (TRC20).
        equal("Ethereum (ERC20)", ProjectContact.CRYPTO_ETH_NETWORK, "Ethereum network");
        equal("Tron (TRC20)", ProjectContact.CRYPTO_TRON_NETWORK, "Tron network");
        String eth = ProjectContact.CRYPTO_ETH_ADDRESS, tron = ProjectContact.CRYPTO_TRON_ADDRESS;
        require(eth.matches("0x[0-9a-fA-F]{40}"), "Ethereum address is 0x and 40 hex digits");
        equal("0x2a1ae58b5fdcf48876903ed5fb2a578a464b1fb9", eth.toLowerCase(java.util.Locale.ROOT), "the address the author gave");
        require(tron.matches("T[1-9A-HJ-NP-Za-km-z]{33}"), "Tron address is T and 33 Base58 characters");
        require(base58CheckValid(tron), "Tron address checksum");
        equal("TJtQSu4UJYpjH355JyvurxHyyvGrPDU6sU", tron, "the Tron address the author gave");
        require(ProjectContact.TELEGRAM_URL.startsWith("https://t.me/+"), "Telegram invite link");
    }

    /** Base58Check as Tron writes it: 21 bytes (0x41 and the account) and the first 4 bytes of their double SHA-256. */
    private static boolean base58CheckValid(String address) {
        String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        java.math.BigInteger value = java.math.BigInteger.ZERO;
        for (char c : address.toCharArray()) {
            int digit = alphabet.indexOf(c);
            if (digit < 0) return false;
            value = value.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(digit));
        }
        byte[] raw = value.toByteArray(), bytes = new byte[25];
        int skip = raw.length > 25 ? raw.length - 25 : 0;
        if (raw.length - skip > 25) return false;
        System.arraycopy(raw, skip, bytes, 25 - (raw.length - skip), raw.length - skip);
        try {
            java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
            byte[] once = sha.digest(java.util.Arrays.copyOf(bytes, 21));
            byte[] twice = sha.digest(once);
            return bytes[0] == 0x41 && java.util.Arrays.equals(java.util.Arrays.copyOf(twice, 4), java.util.Arrays.copyOfRange(bytes, 21, 25));
        } catch (java.security.NoSuchAlgorithmException missing) { return false; }
    }

    /**
     * App292: from 2.1 on every update shows the welcome flow again. The record counts only for the version code that
     * saved it; a record from before App292 (no version code) shows it once. The typed phrase is not asked again.
     */
    private static void welcomeEveryUpdate() throws IOException {
        equal(true, WelcomeState.completed(1, 292, 292), "this version completed it");
        equal(false, WelcomeState.completed(1, 291, 292), "an update shows it again");
        equal(false, WelcomeState.completed(1, null, 292), "a record from before App292 shows it once more");
        equal(false, WelcomeState.completed(1, 293, 292), "a downgrade shows it again");
        equal(false, WelcomeState.completed(0, 292, 292), "older statements show it again");
        equal(false, WelcomeState.completed(null, 292, 292), "no record");
        equal(false, WelcomeState.completed("1", "292", 292), "wrong types");
        equal(true, WelcomeState.completed(1, null, -1), "an unreadable version keeps the rule from before App292");
        equal(false, WelcomeState.completed(null, null, -1), "but still needs the statements confirmed");
        String activity = new String(Files.readAllBytes(Paths.get("app/src/main/java/dev/puitheme/ModernMainActivity.kt")), StandardCharsets.UTF_8);
        require(activity.contains("welcomeRequired = !WelcomeState.completed(this)"), "the activity asks WelcomeState on start");
        String page = new String(Files.readAllBytes(Paths.get("app/src/main/java/dev/puitheme/WelcomeFlow.kt")), StandardCharsets.UTF_8);
        require(page.contains("needsPhrase = !review && app.freeNoticeRequired"), "after an update only the agree switch, not the typed phrase");
    }

    private static int count(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) found++;
        return found;
    }
    /** One private composable of the page: from its declaration to the next composable. */
    private static String function(String page, String name) {
        int start = page.indexOf("private fun " + name + "(");
        require(start >= 0, "page has " + name);
        int end = page.indexOf("\n@Composable\n", start);
        return end < 0 ? page.substring(start) : page.substring(start, end);
    }

    /**
     * App287: the overview had grown to three screens. Each feature is told once (the hero no longer repeats them as
     * cards), the two long statements and the donation addresses open in place, and what must be read stays in view.
     */
    private static void compact() throws IOException {
        String page = new String(Files.readAllBytes(Paths.get("app/src/main/java/dev/puitheme/WelcomeFlow.kt")), StandardCharsets.UTF_8);
        for (String key : new String[] {"welcome_feature_statusbar_title", "welcome_feature_panel_title", "welcome_feature_plugin_title"})
            equal(1, count(page, "R.string." + key + ")"), key + " is told once");
        String statements = function(page, "WelcomeStatements");
        equal(2, count(statements, "WelcomeFold("), "licence and disclaimer open in place");
        int fold = statements.indexOf("WelcomeFold(");
        require(statements.indexOf("R.string.welcome_free_body") >= 0 && statements.indexOf("R.string.welcome_free_body") < fold
                && statements.indexOf("R.string.welcome_plugin_paid_body") >= 0 && statements.indexOf("R.string.welcome_plugin_paid_body") < fold,
                "free forever and paid plugins stay in full view");
        require(statements.indexOf("R.string.welcome_respect_body") > fold && statements.indexOf("R.string.welcome_disclaimer_body") > fold,
                "the long statements are inside the rows that open");
        String support = function(page, "WelcomeSupport");
        int donate = support.indexOf("WelcomeFold(");
        require(donate >= 0 && support.indexOf("CRYPTO_ETH_ADDRESS") > donate && support.indexOf("CRYPTO_TRON_ADDRESS") > donate
                && support.indexOf("R.string.welcome_donate_warning") > donate, "addresses and their warning open with the donation row");
        require(support.indexOf("WelcomeDeveloper(") >= 0 && support.indexOf("WelcomeDeveloper(") < donate
                && support.indexOf("R.string.welcome_telegram_join") >= 0 && support.indexOf("R.string.welcome_telegram_join") < donate,
                "the developer and the Telegram group stay in view");
        // App289: WeChat and Alipay codes fold after the crypto row; each points to 关于 → 联系与捐赠.
        int wechat = support.indexOf("R.string.welcome_wechat_title"), alipay = support.indexOf("R.string.welcome_alipay_title");
        require(wechat > support.indexOf("R.string.welcome_donate_warning") && alipay > wechat
                && support.indexOf("donation/wechat.png") > wechat && support.indexOf("donation/alipay.jpg") > alipay,
                "WeChat and Alipay rows fold after the crypto donation");
        require(function(page, "WelcomeQrCode").contains("R.string.welcome_donate_about_hint"), "each code row names the About section");
        int greeting = page.indexOf("internal fun WelcomeGreetingPage(");
        require(greeting >= 0 && page.substring(greeting, page.indexOf("\n@Composable\n", greeting)).contains("WelcomeUiStyle(uiStyle, onUiStyle)"),
                "first page offers the interface style");
        String styles = function(page, "WelcomeUiStyle");
        require(styles.contains("C17UiStyle.entries") && styles.contains("R.string.welcome_ui_coui"), "every style listed, COUI marked unfinished");
        require(function(page, "WelcomeIntro").contains("R.string.welcome_easter_egg_body"), "the introduction mentions the easter egg");
    }

    public static void main(String[] args) throws Exception {
        resources();
        greetingAndSupport();
        welcomeEveryUpdate();
        paidPlugins();
        compact();
        prefix();
        System.out.println("WelcomeResourcesCheck passed: " + checks);
    }
}
