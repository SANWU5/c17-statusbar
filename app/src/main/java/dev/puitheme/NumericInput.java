// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Decimal input with a bounded parser; slider rounding and manual input are separate. */
public final class NumericInput {
    private static final int MAX_INPUT_LENGTH = 256;

    private NumericInput() { }

    static BigDecimal decimal(String input) {
        if (input == null || input.length() > MAX_INPUT_LENGTH) throw invalid(null);
        try {
            BigDecimal raw = new BigDecimal(input.trim());
            float stored = raw.floatValue();
            if (!Float.isFinite(stored) || stored == 0f && raw.signum() != 0) throw invalid(null);
            return raw;
        } catch (NumberFormatException | ArithmeticException error) { throw invalid(error); }
    }

    /** Manual entry preserves float precision rather than inheriting a slider's step. */
    public static float unrounded(String input) { return decimal(input).floatValue(); }

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
        } catch (NumberFormatException | ArithmeticException error) {
            throw invalid(error);
        }
    }

    private static NumberFormatException invalid(Throwable cause) {
        NumberFormatException error = new NumberFormatException("请输入有效的有限数值");
        if (cause != null) error.initCause(cause);
        return error;
    }
}
