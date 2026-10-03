// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Complete additions after the last schema-1 release. Never fills a partial schema-2 snapshot. */
final class Upgrade65 {
    static final Map<String,Object> DEFAULTS;
    static {
        Map<String,Object> values=new LinkedHashMap<>();
        values.put(SpeedPosition.POSITION,SpeedPosition.NATIVE);
        values.put(C17HighlightRemoval.HEADS_UP_ENABLED,true);
        values.put(NotificationIconOverrides.MASTER,false);
        values.put(NotificationIconOverrides.RULES,"[]");
        values.put(IconPackRepository.MASTER,false);
        values.put(IconPackRepository.LAYERS,"[]");
        DEFAULTS=Collections.unmodifiableMap(values);
    }
    private Upgrade65(){}
}
