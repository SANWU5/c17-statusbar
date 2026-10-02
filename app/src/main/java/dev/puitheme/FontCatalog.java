// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Distinct OFL variable families from the official, pinned Google Fonts distribution. */
public final class FontCatalog {
    public static final String DISTRIBUTION_COMMIT = "9710da1eacb3be272583c3224dcb70f9da6eadbb";
    public static final long MAX_DOWNLOAD_BYTES = 30L * 1024 * 1024;
    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
        new Entry("notosanssc", "Noto Sans SC · 思源黑体", "支持简体中文、拉丁字母与数字", "简体中文黑体", "https://github.com/notofonts/noto-cjk", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/notosanssc/OFL.txt", 17772300L, "a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da", 100, 900),
        new Entry("notoserifsc", "Noto Serif SC · 思源宋体", "支持简体中文、拉丁字母与数字", "简体中文宋体", "https://github.com/notofonts/noto-cjk", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/notoserifsc/NotoSerifSC%5Bwght%5D.ttf", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/notoserifsc/OFL.txt", 25125512L, "050080d9255a86808f2945bffac582b31ef32bc36411ce29563b4961670c66f9", 200, 900),
        new Entry("inter", "Inter", "拉丁字母与数字；中文使用系统字体", "清晰的界面字体", "https://github.com/rsms/inter", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/inter/OFL.txt", 876576L, "29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031", 100, 900),
        new Entry("roboto", "Roboto", "拉丁字母与数字；中文使用系统字体", "Android 经典界面字体", "https://github.com/googlefonts/roboto-classic", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/roboto/Roboto%5Bwdth%2Cwght%5D.ttf", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/roboto/OFL.txt", 488584L, "d7598e12c5dbef095ff8272cfc55da0250bd07fbdecbac8a530b9b277872a134", 100, 900),
        new Entry("montserrat", "Montserrat", "拉丁字母与数字；中文使用系统字体", "几何标题字体", "https://github.com/JulietaUla/Montserrat", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/montserrat/Montserrat%5Bwght%5D.ttf", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/montserrat/OFL.txt", 744936L, "0f7b311b2f3279e4eef9b2f968bcdbab6e28f4daeb1f049f4f278a902bcd82f7", 100, 900),
        new Entry("manrope", "Manrope", "拉丁字母与数字；中文使用系统字体", "现代几何字体", "https://github.com/googlefonts/manrope", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/manrope/Manrope%5Bwght%5D.ttf", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/manrope/OFL.txt", 164700L, "3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6", 200, 800),
        new Entry("opensans", "Open Sans", "拉丁字母与数字；中文使用系统字体", "人文无衬线字体", "https://github.com/googlefonts/opensans", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/opensans/OpenSans%5Bwdth%2Cwght%5D.ttf", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/opensans/OFL.txt", 532636L, "36643644f318a812aab2d2ed3bb98f8cf0872527f835fe9398d95fe6b9adb878", 300, 800),
        new Entry("jetbrainsmono", "JetBrains Mono", "拉丁字母与数字；中文使用系统字体", "等宽数字与代码字体", "https://github.com/JetBrains/JetBrainsMono", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/jetbrainsmono/OFL.txt", 187208L, "48715a42ec242c21e9f02692891e147d022299a52e48d5e413e1a942193ffeda", 100, 800),
        new Entry("nunito", "Nunito", "拉丁字母与数字；中文使用系统字体", "圆润无衬线字体", "https://github.com/googlefonts/nunito", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/nunito/Nunito%5Bwght%5D.ttf", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/nunito/OFL.txt", 276932L, "bb55a5ca5c2042335b3991af27c4d0705d0ef41cac6164ac737fd8f2a1e85207", 200, 1000),
        new Entry("oswald", "Oswald", "拉丁字母与数字；中文使用系统字体", "紧凑长形字体", "https://github.com/googlefonts/OswaldFont", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/oswald/Oswald%5Bwght%5D.ttf", "https://raw.githubusercontent.com/google/fonts/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/oswald/OFL.txt", 172088L, "5b38c246e255a12f5712d640d56bcced0472466fc68983d2d0410ec0457c2817", 200, 700)
    ));
    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_REVISION = new LinkedHashMap<>();
    static { for (Entry entry : ENTRIES) { BY_ID.put(entry.id, entry); BY_REVISION.put(entry.sha256, entry); } }
    private FontCatalog() { }
    public static List<Entry> entries() { return ENTRIES; }
    public static Entry find(String id) { return BY_ID.get(id); }
    public static Entry forRevision(String revision) { return BY_REVISION.get(revision); }

    public static final class Entry {
        public final String id, displayName, coverage, style, sourceUrl, downloadUrl, licenseUrl, sha256;
        public final String licenseName = "SIL Open Font License 1.1";
        public final String licenseAsset;
        public final long bytes;
        public final int minWeight, maxWeight;
        private Entry(String id, String displayName, String coverage, String style, String sourceUrl,
                String downloadUrl, String licenseUrl, long bytes, String sha256, int minWeight, int maxWeight) {
            this.id=id; this.displayName=displayName; this.coverage=coverage; this.style=style;
            this.sourceUrl=sourceUrl; this.downloadUrl=downloadUrl; this.licenseUrl=licenseUrl;
            this.bytes=bytes; this.sha256=sha256; this.minWeight=minWeight; this.maxWeight=maxWeight;
            this.licenseAsset="fonts/catalog/licenses/"+id+"-OFL.txt";
        }
        /** Preserve source-axis semantics rather than synthesizing a weight outside the variable font. */
        public int clampWeight(int requested) { return Math.max(minWeight, Math.min(maxWeight, requested)); }
    }
}
