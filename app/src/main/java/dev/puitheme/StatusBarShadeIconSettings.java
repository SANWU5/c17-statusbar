// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.Collections;
import java.util.Map;

/** One explicit opt-in for native shade-page icon ownership and transitions. */
public final class StatusBarShadeIconSettings {
    public static final String MASTER = "shade_status_icons_enabled";
    public static final Map<String, Boolean> BOOLEANS = Collections.singletonMap(MASTER, false);
    private StatusBarShadeIconSettings() { }
}
