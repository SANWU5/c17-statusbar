// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exactly the code-67 addition batch; never fills a partial schema-4 snapshot. */
final class Upgrade67 {
    static final Map<String,Object> DEFAULTS;
    static {
        Map<String,Object> values=new LinkedHashMap<>();
        values.put(NativeNetworkBadgeSettings.key(1,"hidden"),false);
        values.put(NativeNetworkBadgeSettings.key(2,"hidden"),false);
        values.put(NotificationBigClockSettings.SCREEN_PADDING,24f);
        DEFAULTS=Collections.unmodifiableMap(values);
    }
    private Upgrade67(){}
}
