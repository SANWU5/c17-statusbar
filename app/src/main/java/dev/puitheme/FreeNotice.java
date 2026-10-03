// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import java.util.Collections;

/** App-only acknowledgement. It is never part of the hook settings, mirror or JSON export. */
public final class FreeNotice {
    public static final String PHRASE = "本模块永久免费，捐赠自愿，请勿付费购买。";
    public static final String DESCRIPTION = "本模块是免费、开源项目，不是付费产品，所有功能永久免费，永不收费。\n\n"
            + "捐赠完全自愿，只是对作者的支持；不捐赠也可以正常使用，不会影响任何功能。\n\n"
            + "请勿向任何人付费购买本模块、激活码或所谓收费版本。遇到售卖、收费激活或强制捐赠，请谨防被骗。";
    private static final String PREFERENCES = "app_free_notice";
    private static final String ACCEPTED_VERSION = "accepted_version";
    private static final int VERSION = 1;
    private FreeNotice() { }
    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
    public static boolean accepted(Context context) {
        return Integer.valueOf(VERSION).equals(PreferenceWrites.values(preferences(context)).get(ACCEPTED_VERSION));
    }
    public static boolean matches(String text) { return PHRASE.equals(text); }
    public static boolean accept(Context context, String text) {
        if (!matches(text)) return false;
        SharedPreferences raw = preferences(context);
        return PreferenceWrites.commit(raw, raw.edit().putInt(ACCEPTED_VERSION, VERSION),
                Collections.<String, Object>singletonMap(ACCEPTED_VERSION, VERSION), false);
    }
    public static boolean blocksContextMenuAction(int action) {
        return action == android.R.id.paste || action == android.R.id.pasteAsPlainText
                || action == android.R.id.copy || action == android.R.id.cut
                || action == android.R.id.shareText;
    }
    public static boolean blocksClipboardShortcut(int key, int modifiers) {
        boolean control = (modifiers & KeyEvent.META_CTRL_ON) != 0;
        boolean shift = (modifiers & KeyEvent.META_SHIFT_ON) != 0;
        return control && (key == KeyEvent.KEYCODE_V || key == KeyEvent.KEYCODE_C
                || key == KeyEvent.KEYCODE_X || key == KeyEvent.KEYCODE_INSERT)
                || shift && (key == KeyEvent.KEYCODE_INSERT || key == KeyEvent.KEYCODE_DEL);
    }
}
