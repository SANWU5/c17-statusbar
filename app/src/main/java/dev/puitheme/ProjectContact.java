// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

/** Public contact information; copying occurs only after the user taps the about-page row. */
public final class ProjectContact {
    public static final String AUTHOR = "aiingjie";
    public static final String GITHUB = "sanwu5";
    public static final String COOLAPK = "konwo";
    public static final String QQ = "2726344450";
    private ProjectContact() { }
    public static boolean copyQq(Context context) {
        return copy(context, "合作与捐赠 QQ", QQ);
    }
    public static boolean copyCoolapk(Context context) {
        return copy(context, "酷安用户名", COOLAPK);
    }
    private static boolean copy(Context context, String label, String value) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return false;
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }
}
