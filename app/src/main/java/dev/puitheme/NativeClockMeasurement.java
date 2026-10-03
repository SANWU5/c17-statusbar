// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** The optional legacy width cache must not abort all text hooks on newer ROMs. */
final class NativeClockMeasurement {
    static Field widthField(Class<?> type) {
        if (type == null) return null;
        try {
            Field field = type.getDeclaredField("actualWidth");
            if (field.getType() != int.class || Modifier.isStatic(field.getModifiers())
                    || Modifier.isFinal(field.getModifiers())) return null;
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }
}
