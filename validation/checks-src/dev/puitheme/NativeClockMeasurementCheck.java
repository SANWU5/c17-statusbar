package dev.puitheme;

import java.lang.reflect.Field;

public final class NativeClockMeasurementCheck {
    static class Legacy { private int actualWidth; }
    static class NewRom { private int measuredWidthCache; }
    static class WrongType { private float actualWidth; }
    static class StaticWidth { private static int actualWidth; }
    static class FinalWidth { private final int actualWidth = 0; }
    public static void main(String[] args) throws Exception {
        Legacy old = new Legacy();
        Field field = NativeClockMeasurement.widthField(Legacy.class);
        if (field == null) throw new AssertionError("Legacy width cache missing");
        field.setInt(old, 200);
        if (field.getInt(old) != 200) throw new AssertionError("Legacy cache update failed");
        for (Class<?> variant : new Class<?>[]{NewRom.class, WrongType.class, StaticWidth.class, FinalWidth.class, null}) {
            if (NativeClockMeasurement.widthField(variant) != null) throw new AssertionError("Unsafe optional cache accepted");
        }
        System.out.println("7 legacy and changed-ROM clock width compatibility checks passed");
    }
}
