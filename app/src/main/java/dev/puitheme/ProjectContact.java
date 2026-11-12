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
    /** The author's Coolapk profile; the Coolapk app opens it when installed, otherwise the browser does. */
    public static final String COOLAPK_URL = "https://www.coolapk.com/u/745949";
    /** Shown with the donation entries. */
    public static final String DONATION_NOTE = "捐赠金额不限。捐赠后可以联系作者，申请加入测试群；更新会先在测试群测试，稳定后再发布正式版。";
    public static final String QQ = "2726344450";
    /** The author's Telegram group (2026-11-10). */
    public static final String TELEGRAM_URL = "https://t.me/+Da1w6yZKubxlZTQ1";
    /**
     * Crypto donation (2026-11-12): two networks, two different addresses. The X Layer entry is retired; its 0x
     * address is the same wallet as the Ethereum one.
     */
    public static final String CRYPTO_ETH_NETWORK = "Ethereum (ERC20)";
    /** The author gave it in lower case; shown with its EIP-55 checksum case, which is the same address. */
    public static final String CRYPTO_ETH_ADDRESS = "0x2a1aE58B5fDcF48876903Ed5FB2a578a464b1FB9";
    public static final String CRYPTO_TRON_NETWORK = "Tron (TRC20)";
    /** Base58Check, version byte 0x41. */
    public static final String CRYPTO_TRON_ADDRESS = "TJtQSu4UJYpjH355JyvurxHyyvGrPDU6sU";
    private ProjectContact() { }
    public static boolean copyCryptoAddress(Context context, String address) {
        return copy(context, "加密货币捐赠地址", address);
    }
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
