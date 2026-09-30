// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Bounded decimal input that preserves the decimal rounding boundary before float storage. */
public final class NumericInput {
    private static final int MAX_INPUT_LENGTH = 256;

    private NumericInput() { }

    public static float parse(String input, int decimals) {
        if (input == null || input.length() > MAX_INPUT_LENGTH || decimals < 0 || decimals > MAX_INPUT_LENGTH)
            throw invalid(null);
        try {
            BigDecimal raw = new BigDecimal(input.trim());
            float preliminary = raw.floatValue();
            if (!Float.isFinite(preliminary)) throw invalid(null);
            // Huge negative exponents already underflow float storage; never expand their power of ten.
            if (preliminary == 0f) return 0f;
            float result = raw.setScale(decimals, RoundingMode.HALF_UP).floatValue();
            if (!Float.isFinite(result)) throw invalid(null);
            return result;
        } catch (ArithmeticException error) {
            throw invalid(error);
        }
    }

    private static NumberFormatException invalid(Throwable cause) {
        NumberFormatException error = new NumberFormatException("请输入有效的有限数值");
        if (cause != null) error.initCause(cause);
        return error;
    }
}
