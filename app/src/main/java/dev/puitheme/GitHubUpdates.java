package dev.puitheme;

import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Activity-only release checks. This class never downloads or installs an APK. */
public final class GitHubUpdates {
    public static final String REPO_URL = "https://github.com/SANWU5/c17-statusbar";
    public static final String API_URL = "https://api.github.com/repos/SANWU5/c17-statusbar/releases/latest";
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int MAX_BODY_CHARACTERS = 3000;
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^[vV]?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
            + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
            + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$");

    public enum Status {
        UPDATE_AVAILABLE, UP_TO_DATE, NO_RELEASE, UNKNOWN_VERSION,
        NETWORK_ERROR, RATE_LIMITED, HTTP_ERROR, INVALID_RESPONSE
    }

    public interface Callback { void onResult(Result result); }

    public static final class Result {
        public final Status status;
        public final String message, version, body, releaseUrl, apkUrl;
        public final boolean newer;

        private Result(Status status, String message, String version, String body,
                       String releaseUrl, String apkUrl) {
            this.status = status;
            this.message = nonNull(message);
            this.version = nonNull(version);
            this.body = nonNull(body);
            this.releaseUrl = nonNull(releaseUrl);
            this.apkUrl = nonNull(apkUrl);
            this.newer = status == Status.UPDATE_AVAILABLE;
        }
    }

    /** Starts a background request and always delivers its result on the main looper. */
    public static void fetch(String localVersion, Callback callback) {
        if (callback == null) throw new IllegalArgumentException("Missing callback");
        Handler main = new Handler(Looper.getMainLooper());
        Thread worker = new Thread(() -> {
            Result result = request(localVersion);
            main.post(() -> callback.onResult(result));
        }, "C17-update-check");
        worker.setDaemon(true);
        worker.start();
    }

    private static Result request(String localVersion) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            // A fixed HTTPS API request must not follow redirects to another origin.
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "C17-StatusBar-update-check");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return noRelease();
            if (status != HttpURLConnection.HTTP_OK) {
                String errorBody = "";
                InputStream error = connection.getErrorStream();
                if (error != null) errorBody = readBounded(error);
                return httpFailure(status, connection.getHeaderField("X-RateLimit-Remaining"),
                        connection.getHeaderField("Retry-After"), errorBody);
            }
            if (connection.getContentLengthLong() > MAX_RESPONSE_BYTES)
                return error(Status.INVALID_RESPONSE, "更新信息过大，请到项目主页查看");
            return parseRelease(localVersion, readBounded(connection.getInputStream()));
        } catch (TooLargeException ignored) {
            return error(Status.INVALID_RESPONSE, "更新信息过大，请到项目主页查看");
        } catch (SocketTimeoutException ignored) {
            return error(Status.NETWORK_ERROR, "连接 GitHub 超时，请稍后再试");
        } catch (IOException ignored) {
            return error(Status.NETWORK_ERROR, "无法连接 GitHub，请检查网络后重试");
        } catch (SecurityException ignored) {
            return error(Status.NETWORK_ERROR, "无法访问网络，请检查应用的联网权限");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBounded(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096];
            int count;
            while ((count = source.read(bytes)) != -1) {
                if (output.size() + count > MAX_RESPONSE_BYTES) throw new TooLargeException();
                output.write(bytes, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static Result httpFailure(int code, String remaining, String retryAfter, String body) {
        if (code == 404) return noRelease();
        if (code == 429 || code == 403 && ("0".equals(remaining) || retryAfter != null
                || nonNull(body).toLowerCase(Locale.ROOT).contains("rate limit")))
            return error(Status.RATE_LIMITED, "GitHub 检查次数暂时受限，请稍后再试");
        if (code >= 300 && code < 400)
            return error(Status.HTTP_ERROR, "更新服务发生重定向，请到项目主页查看");
        return error(Status.HTTP_ERROR, "GitHub 暂时无法提供更新信息（" + code + "），请稍后再试");
    }

    /** Parses only stable public releases and safe links in this project's release paths. */
    public static Result parseRelease(String localVersion, String json) {
        if (json == null || json.length() > MAX_RESPONSE_BYTES)
            return error(Status.INVALID_RESPONSE, "更新信息不完整，请稍后再试");
        try {
            JSONObject release = new JSONObject(json);
            if (Boolean.TRUE.equals(release.opt("draft")) || Boolean.TRUE.equals(release.opt("prerelease")))
                return noRelease();
            if (!Boolean.FALSE.equals(release.opt("draft")) || !Boolean.FALSE.equals(release.opt("prerelease")))
                return error(Status.INVALID_RESPONSE, "无法确认正式发布状态，请到项目主页查看");
            String tag = plainText(release.optString("tag_name", ""), 128).trim();
            String releaseUrl = release.optString("html_url", "");
            if (tag.length() == 0 || !isReleaseUrl(releaseUrl))
                return error(Status.INVALID_RESPONSE, "更新信息不完整，请到项目主页查看");
            String notes = plainText(release.optString("body", ""), MAX_BODY_CHARACTERS);
            String apk = "";
            int selectedScore = -1;
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String candidate = asset.optString("browser_download_url", "");
                if (!isApkUrl(candidate)) continue;
                String name = asset.optString("name", "").toLowerCase(Locale.ROOT);
                int score = name.contains("c17") ? 2 : name.contains("statusbar") ? 1 : 0;
                if (score > selectedScore) { apk = candidate; selectedScore = score; }
            }
            Integer compared = compareVersion(tag, localVersion);
            if (compared == null)
                return new Result(Status.UNKNOWN_VERSION, "发现正式发布，但版本号无法自动比较，请手动查看", tag, notes, releaseUrl, apk);
            if (compared > 0)
                return new Result(Status.UPDATE_AVAILABLE, "发现新版本 " + tag, tag, notes, releaseUrl, apk);
            return new Result(Status.UP_TO_DATE, compared < 0 ? "当前本地版本高于最新正式发布（" + tag + "）"
                    : "当前已是最新正式发布", tag, notes, releaseUrl, apk);
        } catch (JSONException | IllegalArgumentException ignored) {
            return error(Status.INVALID_RESPONSE, "无法读取更新信息，请稍后再试");
        }
    }

    public static boolean isReleaseUrl(String url) {
        URI uri = projectUri(url);
        if (uri == null) return false;
        String path = uri.getPath();
        String prefix = "/sanwu5/c17-statusbar/releases/tag/";
        return path.toLowerCase(Locale.ROOT).startsWith(prefix) && path.length() > prefix.length();
    }

    public static boolean isApkUrl(String url) {
        URI uri = projectUri(url);
        if (uri == null) return false;
        String path = uri.getPath().toLowerCase(Locale.ROOT);
        String prefix = "/sanwu5/c17-statusbar/releases/download/";
        if (!path.startsWith(prefix) || !path.endsWith(".apk")) return false;
        String suffix = path.substring(prefix.length());
        int slash = suffix.indexOf('/');
        return slash > 0 && slash < suffix.length() - 5;
    }

    private static URI projectUri(String url) {
        if (url == null || url.length() > 2048) return null;
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getPort() != -1 || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPath() == null) return null;
            String rawPath = uri.getRawPath().toLowerCase(Locale.ROOT);
            if (rawPath.contains("%2f") || rawPath.contains("%5c") || rawPath.contains("%2e")) return null;
            for (String segment : uri.getPath().split("/"))
                if (".".equals(segment) || "..".equals(segment)) return null;
            return uri;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** SemVer order, including numeric prerelease identifiers. Unrecognised tags return null. */
    public static Integer compareVersion(String left, String right) {
        Matcher a = version(left), b = version(right);
        if (a == null || b == null) return null;
        for (int i = 1; i <= 3; i++) {
            int order = new BigInteger(a.group(i)).compareTo(new BigInteger(b.group(i)));
            if (order != 0) return Integer.signum(order);
        }
        String preA = a.group(4), preB = b.group(4);
        if (preA == null || preB == null) return preA == preB ? 0 : preA == null ? 1 : -1;
        String[] partsA = preA.split("\\."), partsB = preB.split("\\.");
        for (int i = 0; i < Math.min(partsA.length, partsB.length); i++) {
            String partA = partsA[i], partB = partsB[i];
            boolean numberA = numeric(partA), numberB = numeric(partB);
            int order = numberA && numberB ? new BigInteger(partA).compareTo(new BigInteger(partB))
                    : numberA != numberB ? (numberA ? -1 : 1) : partA.compareTo(partB);
            if (order != 0) return Integer.signum(order);
        }
        return Integer.compare(partsA.length, partsB.length);
    }

    private static Matcher version(String text) {
        if (text == null || text.length() > 128) return null;
        Matcher matcher = VERSION_PATTERN.matcher(text.trim());
        if (!matcher.matches()) return null;
        if (matcher.group(4) != null) for (String part : matcher.group(4).split("\\."))
            if (numeric(part) && part.length() > 1 && part.charAt(0) == '0') return null;
        return matcher;
    }

    private static boolean numeric(String value) {
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        return value.length() > 0;
    }

    private static String plainText(String text, int maxCharacters) {
        String cleaned = nonNull(text).replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(Math.min(cleaned.length(), maxCharacters));
        int kept = 0;
        for (int offset = 0; offset < cleaned.length() && kept < maxCharacters;) {
            int point = cleaned.codePointAt(offset);
            offset += Character.charCount(point);
            if (Character.isISOControl(point) && point != '\n' && point != '\t') continue;
            result.appendCodePoint(point);
            kept++;
        }
        return result.toString();
    }

    private static Result noRelease() {
        return error(Status.NO_RELEASE, "暂无正式发布，可在 GitHub 项目页查看进展");
    }
    private static Result error(Status status, String message) {
        return new Result(status, message, "", "", "", "");
    }
    private static String nonNull(String text) { return text == null ? "" : text; }
    private static final class TooLargeException extends IOException {}
    private GitHubUpdates() {}
}
