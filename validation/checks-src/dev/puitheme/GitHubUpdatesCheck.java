package dev.puitheme;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.json.JSONObject;

/** Pure local release fixtures; no HTTP requests, downloads or installations. */
public final class GitHubUpdatesCheck {
    private static int checks;
    private static final String RELEASE = GitHubUpdates.REPO_URL + "/releases/tag/v1.14.0";
    private static final String APK = GitHubUpdates.REPO_URL + "/releases/download/v1.14.0/C17-1.14.0.apk";
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static String release(String tag, String notes, String assets) {
        return "{\"draft\":false,\"prerelease\":false,\"tag_name\":" + JSONObject.quote(tag)
                + ",\"html_url\":" + JSONObject.quote(RELEASE) + ",\"body\":" + JSONObject.quote(notes)
                + ",\"assets\":" + assets + "}";
    }
    private static String asset(String name, String url) {
        return "{\"name\":" + JSONObject.quote(name) + ",\"browser_download_url\":" + JSONObject.quote(url) + "}";
    }
    private static void nonNull(GitHubUpdates.Result result) {
        equal(true, result.status != null);
        for (String text : new String[]{result.message, result.version, result.body, result.releaseUrl, result.apkUrl})
            equal(true, text != null);
    }

    public static void main(String[] args) throws Exception {
        equal("https://api.github.com/repos/SANWU5/c17-statusbar/releases/latest", GitHubUpdates.API_URL);
        for (String[] ordered : new String[][]{
                {"1.9.9", "1.10.0"}, {"v1.13.1", "v1.14.0"}, {"2.9.99", "10.0.0"},
                {"1.13.1-alpha", "1.13.1-alpha.1"}, {"1.13.1-alpha.1", "1.13.1-alpha.beta"},
                {"1.13.1-alpha.beta", "1.13.1-beta"}, {"1.13.1-beta", "1.13.1-beta.2"},
                {"1.13.1-beta.2", "1.13.1-beta.11"}, {"1.13.1-beta.11", "1.13.1-rc.1"},
                {"1.13.1-rc.1", "1.13.1"}, {"1.0.0-9", "1.0.0-999999999999999999999"},
                {"1.0.0-99999", "1.0.0-a"}, {"999999999999999999999.0.0", "999999999999999999999.0.1"}}) {
            equal(-1, GitHubUpdates.compareVersion(ordered[0], ordered[1]));
            equal(1, GitHubUpdates.compareVersion(ordered[1], ordered[0]));
        }
        equal(0, GitHubUpdates.compareVersion("v1.13.1+build.9", "1.13.1+other.1"));
        equal(0, GitHubUpdates.compareVersion(" V1.13.1 ", "1.13.1"));
        for (String invalid : new String[]{null, "", "release-1.13.1", "latest", "1.13", "1.13.1.1", "01.13.1",
                "1.013.1", "1.13.01", "1.13.1-01", "1.13.1-a..b", "1.13.1+", "1.13.1-", "1.13.1\nother"}) {
            equal(true, GitHubUpdates.compareVersion(invalid, "1.13.1") == null);
            equal(true, GitHubUpdates.compareVersion("1.13.1", invalid) == null);
        }

        equal(true, GitHubUpdates.isReleaseUrl(RELEASE));
        equal(true, GitHubUpdates.isReleaseUrl(RELEASE.replace("SANWU5", "sanwu5")));
        equal(true, GitHubUpdates.isApkUrl(APK));
        equal(true, GitHubUpdates.isApkUrl(GitHubUpdates.REPO_URL + "/releases/download/v1.14.0/%E6%9B%B4%E5%A5%BD.apk"));
        for (String invalid : new String[]{null, "", "http://github.com/SANWU5/c17-statusbar/releases/tag/v1.14.0",
                RELEASE.replace("github.com", "github.com.evil.example"), RELEASE.replace("SANWU5", "another-owner"),
                RELEASE.replace("c17-statusbar", "another-project"), "https://github.com@evil.example/SANWU5/c17-statusbar/releases/tag/x",
                "https://evil.example@github.com/SANWU5/c17-statusbar/releases/tag/x", RELEASE + "?redirect=1",
                RELEASE + "#hash", RELEASE.replace("github.com", "github.com:443"), RELEASE + "/../other",
                RELEASE + "/%2e%2e/other", RELEASE + "%2Fother", RELEASE + "%5Cother", GitHubUpdates.REPO_URL + "/releases/tag/"})
            equal(false, GitHubUpdates.isReleaseUrl(invalid));
        for (String invalid : new String[]{RELEASE, APK.replace("https:", "http:"), APK.replace("github.com", "cdn.example"),
                APK.replace("SANWU5", "another-owner"), APK.replace("c17-statusbar", "c17-statusbar-evil"),
                APK.replace(".apk", ".zip"), APK + "?raw=1", APK + "#x", APK.replace("/v1.14.0/", "/../"),
                GitHubUpdates.REPO_URL + "/releases/download/file.apk", GitHubUpdates.REPO_URL + "/releases/download/v1/.apk"})
            equal(false, GitHubUpdates.isApkUrl(invalid));

        String payload = release("v1.14.0", "新功能\r\n修复问题\u0000", "[" + asset("source.zip", APK.replace(".apk", ".zip"))
                + "," + asset("evil.apk", APK.replace("github.com", "evil.example")) + "," + asset("C17.apk", APK) + "]");
        GitHubUpdates.Result newer = GitHubUpdates.parseRelease("1.13.1", payload);
        equal(GitHubUpdates.Status.UPDATE_AVAILABLE, newer.status);
        equal(true, newer.newer);
        equal("v1.14.0", newer.version);
        equal("新功能\n修复问题", newer.body);
        equal(RELEASE, newer.releaseUrl);
        equal(APK, newer.apkUrl);
        nonNull(newer);
        GitHubUpdates.Result same = GitHubUpdates.parseRelease("1.14.0", payload);
        equal(GitHubUpdates.Status.UP_TO_DATE, same.status);
        equal(false, same.newer);
        GitHubUpdates.Result localAhead = GitHubUpdates.parseRelease("1.15.0", payload);
        equal(GitHubUpdates.Status.UP_TO_DATE, localAhead.status);
        equal(false, localAhead.newer);
        GitHubUpdates.Result unknown = GitHubUpdates.parseRelease("1.13.1", release("nightly-stable", "", "[]"));
        equal(GitHubUpdates.Status.UNKNOWN_VERSION, unknown.status);
        equal(false, unknown.newer);
        equal(RELEASE, unknown.releaseUrl);
        equal("", unknown.apkUrl);
        equal(GitHubUpdates.Status.UNKNOWN_VERSION, GitHubUpdates.parseRelease("custom-version", payload).status);
        equal(GitHubUpdates.Status.NO_RELEASE, GitHubUpdates.parseRelease("1.13.1", payload.replace("\"draft\":false", "\"draft\":true")).status);
        equal(GitHubUpdates.Status.NO_RELEASE, GitHubUpdates.parseRelease("1.13.1", payload.replace("\"prerelease\":false", "\"prerelease\":true")).status);
        equal(GitHubUpdates.Status.INVALID_RESPONSE, GitHubUpdates.parseRelease("1.13.1", payload.replace("\"draft\":false", "\"draft\":\"false\"")).status);
        equal(GitHubUpdates.Status.INVALID_RESPONSE, GitHubUpdates.parseRelease("1.13.1", payload.replace("github.com", "evil.example")).status);
        for (String bad : new String[]{null, "", "garbage", "[]", "{}", "{\"draft\":false}"}) {
            GitHubUpdates.Result invalid = GitHubUpdates.parseRelease("1.13.1", bad);
            equal(GitHubUpdates.Status.INVALID_RESPONSE, invalid.status);
            equal(false, invalid.newer);
            nonNull(invalid);
        }
        String otherApk = GitHubUpdates.REPO_URL + "/releases/download/v1.14.0/other.apk";
        GitHubUpdates.Result preferred = GitHubUpdates.parseRelease("1.13.1", release("1.14.0", "",
                "[" + asset("other.apk", otherApk) + "," + asset("C17-statusbar.apk", APK) + "]"));
        equal(APK, preferred.apkUrl);
        equal("", GitHubUpdates.parseRelease("1.13.1", release("1.14.0", "", "[]")).apkUrl);
        StringBuilder longNotes = new StringBuilder();
        for (int i = 0; i < 3001; i++) longNotes.append("🔋");
        String truncated = GitHubUpdates.parseRelease("1.13.1", release("1.14.0", longNotes.toString(), "[]")).body;
        equal(3000, truncated.codePointCount(0, truncated.length()));
        equal(false, Character.isHighSurrogate(truncated.charAt(truncated.length() - 1)));

        equal(GitHubUpdates.Status.NO_RELEASE, GitHubUpdates.httpFailure(404, null, null, "").status);
        equal(GitHubUpdates.Status.RATE_LIMITED, GitHubUpdates.httpFailure(403, "0", null, "").status);
        equal(GitHubUpdates.Status.RATE_LIMITED, GitHubUpdates.httpFailure(403, null, "120", "").status);
        equal(GitHubUpdates.Status.RATE_LIMITED, GitHubUpdates.httpFailure(403, null, null, "API rate limit exceeded").status);
        equal(GitHubUpdates.Status.RATE_LIMITED, GitHubUpdates.httpFailure(429, null, null, "").status);
        equal(GitHubUpdates.Status.HTTP_ERROR, GitHubUpdates.httpFailure(403, "45", null, "Forbidden").status);
        equal(GitHubUpdates.Status.HTTP_ERROR, GitHubUpdates.httpFailure(302, null, null, "").status);
        equal(GitHubUpdates.Status.HTTP_ERROR, GitHubUpdates.httpFailure(500, null, null, "").status);
        Method read = GitHubUpdates.class.getDeclaredMethod("readBounded", java.io.InputStream.class);
        read.setAccessible(true);
        equal(256 * 1024, ((String) read.invoke(null, new ByteArrayInputStream(new byte[256 * 1024]))).length());
        try {
            read.invoke(null, new ByteArrayInputStream(new byte[256 * 1024 + 1]));
            throw new AssertionError("Oversized release response was accepted");
        } catch (InvocationTargetException expected) {
            equal(true, expected.getCause() instanceof java.io.IOException);
        }
        nonNull(GitHubUpdates.httpFailure(404, null, null, ""));
        nonNull(GitHubUpdates.httpFailure(429, null, null, ""));
        System.out.println(checks + " checks passed (SemVer, formal releases, safe project URLs, HTTP outcomes, response bounds)");
    }
}
