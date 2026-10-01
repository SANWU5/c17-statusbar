package dev.puitheme;

/** No SystemUI or device: checks that scrolling, rather than expansion, drives compact geometry. */
public final class NotificationBigClockModelCheck {
    private static int checks;
    private static void expect(boolean value) { checks++; if (!value) throw new AssertionError("Big clock check " + checks); }
    private static NotificationBigClockModel.Frame frame(int scroll, float over, float fraction) {
        return NotificationBigClockModel.calculate(1280f, 1.5f, 100f, 36f, 600f, 300f, 0f, scroll, over, fraction);
    }
    public static void main(String[] args) {
        expect(NotificationBigClockModel.eligible(true, true, 0, false, 1f));
        expect(!NotificationBigClockModel.eligible(false, true, 0, false, 1f));
        expect(!NotificationBigClockModel.eligible(true, false, 0, false, 1f));
        expect(!NotificationBigClockModel.eligible(true, true, 1, false, 1f));
        expect(!NotificationBigClockModel.eligible(true, true, 2, false, 1f));
        expect(!NotificationBigClockModel.eligible(true, true, 0, true, 1f));
        expect(!NotificationBigClockModel.eligible(true, true, 0, false, 0f));
        expect(!NotificationBigClockModel.eligible(true, true, 0, false, Float.NaN));
        NotificationBigClockModel.Frame large = frame(0, 0, 1f), middle = frame(170, 0, 1f), small = frame(10000, 0, 1f);
        expect(large.clockHeight > middle.clockHeight && middle.clockHeight > small.clockHeight);
        expect(large.clockTop > middle.clockTop && middle.clockTop > small.clockTop);
        expect(large.weight > middle.weight && middle.weight > small.weight);
        expect(large.textSizeRatio > middle.textSizeRatio && middle.textSizeRatio > small.textSizeRatio);
        expect(large.reservedBottom == middle.reservedBottom && middle.reservedBottom == small.reservedBottom);
        expect(large.collapseDistance > 0 && large.collapseDistance == small.collapseDistance);
        expect(frame((int) Math.ceil(large.collapseDistance), 0, 1f).progress == 1f);
        expect(large.progress == 0f && small.progress == 1f);
        expect(Math.abs(small.clockHeight / large.clockHeight - .36f) < .001f);
        expect(frame(0, 180, 1f).clockHeight > large.clockHeight);
        expect(frame(0, 0, 1f).clockHeight == large.clockHeight); // Native rebound restores baseline.
        expect(frame(0, -180, 1f).clockHeight == large.clockHeight);
        expect(frame(-500, 0, 1f).progress == 0f);
        NotificationBigClockModel.Frame revealing = frame(0, 0, .4f);
        expect(revealing.clockHeight == large.clockHeight && revealing.weight == large.weight);
        expect(revealing.entryTranslation < 0f && large.entryTranslation == 0f);
        expect(NotificationBigClockModel.notificationClipTop(small, 1.5f) < NotificationBigClockModel.notificationClipTop(large, 1.5f));
        expect(NotificationBigClockModel.notificationClipTop(large, 1.5f) == large.clockTop + large.clockHeight + 27f);
        expect(NotificationBigClockModel.notificationClipTop(revealing, 1.5f) < NotificationBigClockModel.notificationClipTop(large, 1.5f));
        NotificationBigClockModel.Frame invalid = NotificationBigClockModel.calculate(Float.NaN,
                Float.POSITIVE_INFINITY, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
                Integer.MAX_VALUE, Float.NaN, Float.NaN);
        expect(NotificationBigClockModel.finite(invalid.clockTop) && NotificationBigClockModel.finite(invalid.clockHeight));
        expect(invalid.clockHeight > 0f && invalid.weight >= 100 && invalid.weight <= 900);
        expect(invalid.progress == 1f && invalid.entryAlpha == 0f);
        System.out.println("Notification big clock model checks passed: " + checks);
    }
}
