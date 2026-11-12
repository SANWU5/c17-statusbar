// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Manager-independent, bundled control center. Extras never choose commands or URLs. */
public final class PerformanceWebActivity extends Activity {
    private static final String ORIGIN = "https://c17.local";
    private static final String PAGE = ORIGIN + "/index.html";
    private WebView web;
    private RootPerformanceModule.Request request;
    private boolean closed;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xffedf6f7);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setBlockNetworkLoads(true);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Controls(), "c17");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest navigation) {
                return !PAGE.equals(navigation.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return !PAGE.equals(url); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest resource) {
                return local(resource.getUrl());
            }
        });
        setContentView(web); web.loadUrl(PAGE);
    }
    private WebResourceResponse local(Uri uri) {
        String path = uri.getPath();
        boolean origin = "https".equals(uri.getScheme()) && "c17.local".equals(uri.getHost())
                && uri.getPort() == -1 && uri.getUserInfo() == null;
        String mime = "/index.html".equals(path) ? "text/html" : "/style.css".equals(path) ? "text/css"
                : "/app.js".equals(path) ? "application/javascript" : null;
        if (origin && mime != null && uri.getQuery() == null) {
            try { return new WebResourceResponse(mime, "UTF-8", getAssets().open("performance-module/webroot" + path)); }
            catch (IOException absent) { }
        }
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", java.util.Collections.emptyMap(),
                new ByteArrayInputStream("Unavailable".getBytes(StandardCharsets.UTF_8)));
    }
    private final class Controls {
        @JavascriptInterface public void readStatus(String callback) { submit(RootPerformanceModule.Action.CHECK, callback); }
        @JavascriptInterface public void run(String callback) { submit(RootPerformanceModule.Action.RUN, callback); }
        @JavascriptInterface public void setEnabled(boolean enabled, String callback) {
            submit(enabled ? RootPerformanceModule.Action.ENABLE : RootPerformanceModule.Action.DISABLE, callback);
        }
        @JavascriptInterface public void setRecoveryEnabled(boolean enabled, String callback) {
            submit(enabled ? RootPerformanceModule.Action.RESCUE_ENABLE : RootPerformanceModule.Action.RESCUE_DISABLE, callback);
        }
    }
    private void submit(RootPerformanceModule.Action action, String callback) {
        if (callback == null || !callback.matches("[A-Za-z_$][A-Za-z0-9_$]{0,63}")) return;
        runOnUiThread(() -> {
            if (closed) return;
            request = RootPerformanceModule.submit(getApplicationContext(), action, result -> {
                if (closed) return;
                request = null;
                if (result.success && result.snapshot != null && result.snapshot.root) PerformanceScheduler.observe(getApplicationContext(), result.snapshot);
                String stdout = result.snapshot == null ? "" : result.snapshot.webStatus();
                web.evaluateJavascript("if(typeof window[" + JSONObject.quote(callback) + "]==='function')window["
                        + JSONObject.quote(callback) + "](" + (result.success ? 0 : 1) + "," + JSONObject.quote(stdout)
                        + "," + JSONObject.quote(result.success ? "" : result.message) + ");", null);
            });
        });
    }
    @Override protected void onResume() {
        super.onResume();
        if (web != null) { web.onResume(); web.evaluateJavascript("window.c17HostPaused=false;document.dispatchEvent(new Event('visibilitychange'));", null); }
    }
    @Override protected void onPause() {
        if (web != null) { web.evaluateJavascript("window.c17HostPaused=true;document.dispatchEvent(new Event('visibilitychange'));", null); web.onPause(); }
        super.onPause();
    }
    @Override protected void onDestroy() {
        closed = true;
        if (request != null) request.cancel(); request = null;
        if (web != null) { web.removeJavascriptInterface("c17"); web.stopLoading(); web.destroy(); web = null; }
        super.onDestroy();
    }
}
