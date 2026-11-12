package dev.puitheme;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class HistoricalResidualCleanupCheck {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void rejected(String input, String message) throws Exception {
        boolean rejected = false;
        try { HistoricalResidualCleanup.preferenceNames(input); } catch (Exception expected) { rejected = true; }
        check(rejected, message);
    }
    private static String shellPath(Path path) {
        String value = path.toAbsolutePath().toString().replace('\\', '/');
        if (value.length() >= 3 && value.charAt(1) == ':')
            return "/" + Character.toLowerCase(value.charAt(0)) + value.substring(2);
        return value;
    }
    /** Execute only the production read-only inventory command on newly-created
     * fixture files. Never execute the cleanup script or access device data. */
    private static void inventoryShell(String first, String second) throws Exception {
        String shell = File.separatorChar == '\\' ? "C:/Program Files/Git/bin/bash.exe" : "/bin/sh";
        if (!new File(shell).isFile()) { System.out.println("Shell fixture skipped: no local shell"); return; }
        Path directory = Files.createTempDirectory("c17 inventory's ");
        Path one = directory.resolve("first.json"), two = directory.resolve("second.json");
        try {
            check(shellInventory(shell, directory, 0).isEmpty(), "missing inventory returns empty");
            Files.write(one, first.getBytes(StandardCharsets.UTF_8));
            check(HistoricalResidualCleanup.preferenceNames(shellInventory(shell, directory, 0)).contains("c17_known"), "one record through shell");
            Files.write(two, second.getBytes(StandardCharsets.UTF_8));
            String output = shellInventory(shell, directory, 0);
            check(output.equals(first + "\n" + second + "\n"), "real shell separates records with newline");
            check(HistoricalResidualCleanup.preferenceNames(output).equals(new LinkedHashSet<>(Arrays.asList("c17_known", "c17_other"))),
                    "real shell and parser preserve both owners");
            Files.write(two, new byte[0]);
            rejected(shellInventory(shell, directory, 0), "empty owner record cannot disappear");
            Files.write(two, " ".getBytes(StandardCharsets.UTF_8));
            rejected(shellInventory(shell, directory, 0), "blank owner record cannot disappear");
            byte[] large = new byte[32769]; Arrays.fill(large, (byte) 'x'); Files.write(two, large);
            shellInventory(shell, directory, 20);
        } finally {
            Files.deleteIfExists(one); Files.deleteIfExists(two); Files.deleteIfExists(directory);
        }
    }
    private static String shellInventory(String shell, Path directory, int expectedExit) throws Exception {
        Process process = new ProcessBuilder(shell).redirectErrorStream(true).start();
        try (OutputStream input = process.getOutputStream()) {
            input.write((HistoricalResidualCleanup.inventoryCommand(shellPath(directory)) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream stream = process.getInputStream()) {
            byte[] buffer = new byte[2048]; int size;
            while ((size = stream.read(buffer)) != -1) output.write(buffer, 0, size);
        }
        check(process.waitFor(10, TimeUnit.SECONDS), "inventory fixture completes");
        check(process.exitValue() == expectedExit, "inventory fixture exit " + expectedExit + ": " + output.toString("UTF-8"));
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
    public static void main(String[] args) throws Exception {
        String first = "{\"id\":\"dev.c17.example\",\"installedAt\":1,\"preferences\":[\"c17_known\"]}";
        String second = "{\"id\":\"dev.c17.other\",\"installedAt\":2,\"preferences\":[\"c17_other\"]}";
        check(HistoricalResidualCleanup.preferenceNames("").isEmpty(), "empty inventory");
        check(HistoricalResidualCleanup.preferenceNames(first + "\n" + second).equals(
                new LinkedHashSet<>(Arrays.asList("c17_known", "c17_other"))), "all plugin ownership records included");
        rejected(first + "\\n" + second, "literal slash-n cannot silently discard later records");
        rejected(first + " garbage", "trailing garbage cannot be ignored");
        rejected(first + "\n" + second.replace("c17_other", "c17_known"), "cross-plugin ownership conflict");
        rejected(first + "\n" + first, "duplicate plugin identity");
        for (String bad : new String[]{first.replace("c17_known", "../native"), first.replace("c17_known", "bad;command"), "{}", "[]"})
            rejected(bad, "untrusted manifest accepted");
        inventoryShell(first, second);
        System.out.println("HistoricalResidualCleanupCheck: " + checks + " checks passed (no Root operations)");
    }
}
