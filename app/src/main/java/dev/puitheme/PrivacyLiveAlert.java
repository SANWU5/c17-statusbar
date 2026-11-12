// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Shows the privacy capsule through the system fluid cloud, the way SystemUI's own flashlight capsule does: a
 * notification object that never reaches the notification service carries the capsule, card and options JSON and is
 * handed to the fluid cloud provider. Nothing appears in the notification shade; the capsule, its camera gap, the
 * status bar making room and the animations are the system's own.
 */
@android.annotation.TargetApi(30)
final class PrivacyLiveAlert {
    static final String AUTHORITY = "com.oplus.card.server.systemui.provider";
    static final int SERVICE_ID = 279000001;
    private static final String TAG = "C17PrivacyCapsule";
    private static final String INTENT_KEY = "c17_privacy_pending_intent";

    /** Capsule: icon and name left of the camera, the in-use label right of it (template t_sym). */
    static String capsule(String icon, String name, String inUse) throws JSONException {
        return new JSONObject().put("capsuleData", new JSONObject()
                .put("firstIcon", icon(icon, "CIRCLE", "SMALL"))
                .put("firstTitle", new JSONObject().put("desc", name).put("text", name))
                .put("lastTitle", new JSONObject().put("desc", inUse).put("position", 4).put("text", inUse))
                .put("type", "t_sym")).toString();
    }

    /** Expanded card, the same three parts as the flashlight card; its button opens the system privacy dashboard. */
    static String card(String icon, String name, String inUse, String review) throws JSONException {
        JSONObject button = new JSONObject().put("bgColor", "colorInt/00000000").put("desc", review)
                .put("intent", "pi/" + INTENT_KEY).put("state", "ON").put("style", "BUTTON").put("text", review)
                .put("textColor", "colorInt/FFFFFFFF").put("textSize", 12).put("type", "ICON_LABEL");
        return new JSONObject()
                .put("criticalData", new JSONObject().put("firstContent", text(name, 12, "FFFFFFFF")).put("firstIcon", icon(icon, "CIRCLE", "SMALL"))
                        .put("firstTitle", text(inUse, 12, "FFFFFFFF")).put("lastBtns", new JSONArray().put(button)).put("type", "t_sym_action"))
                .put("primaryData", new JSONObject().put("icon", icon(icon, "RECTANGLE", "REGULAR"))
                        .put("labels", new JSONArray().put(new JSONArray().put(text(name, 14, "8AFFFFFF"))))
                        .put("title", text(inUse, 20, "E6FFFFFF")).put("type", "t_standard"))
                .put("subData", new JSONObject().put("btns", new JSONArray().put(new JSONObject(button.toString()))).put("type", "t_actions"))
                .toString();
    }

    /**
     * Where the fluid cloud shows it, by the system's seedling host ids: 8 is the status bar (the capsule and its card),
     * 16 the notification centre. The flashlight sends both; the privacy capsule only wants the status bar, so the
     * notification centre never lists it (App282, the author's request). The lock-screen list keeps the original value.
     */
    static String options(String packageName) throws JSONException {
        return new JSONObject().put("dataSourcePkgName", packageName).put("ignoreEntry", 0).put("immersiveCardType", 2)
                .put("isMilestone", true).put("liveAlertService", "").put("lockScreenShowHostMap", new JSONArray().put(16))
                .put("remindType", 0).put("requestLockScreenImmersive", -1).put("showHostMap", new JSONArray().put(8))
                .toString();
    }

    /** {@code iconRes} is a SystemUI drawable (the privacy type's own icon). */
    static void show(Context context, int iconRes, String name) {
        try {
            String icon = "drawable/" + context.getResources().getResourceEntryName(iconRes);
            Bundle extras = new Bundle();
            extras.putString("oplusLiveAlertOptions", options(context.getPackageName()));
            extras.putString("oplus.livealert.card", card(icon, name, "使用中", "查看"));
            extras.putString("oplus.livealert.capsule", capsule(icon, name, "使用中"));
            Intent review = new Intent("android.intent.action.REVIEW_PERMISSION_USAGE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            extras.putParcelable(INTENT_KEY, PendingIntent.getActivity(context, 0, review, PendingIntent.FLAG_IMMUTABLE));
            call(context, "sendLiveAlert", iconRes, extras);
        } catch (JSONException | RuntimeException failed) {
            Log.w(TAG, "Privacy capsule unavailable", failed);
        }
    }

    static void cancel(Context context, int iconRes) {
        try { call(context, "cancelLiveAlert", iconRes, new Bundle()); }
        catch (RuntimeException failed) { Log.w(TAG, "Privacy capsule cancel failed", failed); }
    }

    private static void call(Context context, String method, int iconRes, Bundle extras) {
        Notification notification = new Notification.Builder(context, String.valueOf(SERVICE_ID))
                .setSmallIcon(iconRes).setAutoCancel(true).addExtras(extras).build();
        @SuppressWarnings("deprecation")
        StatusBarNotification wrapper = new StatusBarNotification(context.getPackageName(), context.getPackageName(), SERVICE_ID,
                null, 0, 0, 0, notification, android.os.Process.myUserHandle(), 0L);
        Bundle call = new Bundle();
        call.putParcelable("content", wrapper);
        context.getContentResolver().call(AUTHORITY, method, null, call);
    }

    private static JSONObject icon(String icon, String shape, String size) throws JSONException {
        return new JSONObject().put("Icon", new JSONObject().put("icon", icon).put("shape", shape).put("size", size));
    }

    private static JSONObject text(String value, int size, String argb) throws JSONException {
        return new JSONObject().put("bgColor", "colorInt/00000000").put("desc", value).put("style", "Text").put("text", value)
                .put("textColor", "colorInt/" + argb).put("textSize", size).put("type", "Text");
    }

    private PrivacyLiveAlert() { }
}
