// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

/** One package identity for the normal app and its independently installed customer build. */
public final class ModuleVariant {
    public static final String PACKAGE;
    public static final boolean DABING;

    static {
        String packageName = "dev.puitheme.iosstatusbar";
        boolean customer = false;
        try {
            // Reflection keeps the Java-only desktop checks independent of Android's generated BuildConfig.
            Class<?> config = Class.forName("dev.puitheme.iosstatusbar.BuildConfig");
            Object identity = config.getField("APPLICATION_ID").get(null);
            Object variant = config.getField("CUSTOMER_DABING").get(null);
            if (identity instanceof String && !((String) identity).isEmpty()) {
                packageName = (String) identity;
                customer = Boolean.TRUE.equals(variant);
            }
        } catch (ReflectiveOperationException | LinkageError | SecurityException unavailable) {
            // A plain JVM verification run has no generated Android BuildConfig.
        }
        PACKAGE = packageName;
        DABING = customer;
    }

    private ModuleVariant() { }
}
