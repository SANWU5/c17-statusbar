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
        expect(large.weight == 600 && middle.weight == large.weight && small.weight == large.weight);
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
        NotificationBigClockModel.Frame revealing = frame(0, 0, .15f);
        expect(revealing.clockHeight == large.clockHeight && revealing.weight == large.weight);
        expect(revealing.entryTranslation == 0f && large.entryTranslation == 0f);
        expect(revealing.entryAlpha > 0f && revealing.entryAlpha < large.entryAlpha);
        expect(NotificationBigClockModel.notificationClipTop(small, 1.5f) < NotificationBigClockModel.notificationClipTop(large, 1.5f));
        expect(NotificationBigClockModel.notificationClipTop(large, 1.5f) == large.clockTop + large.clockHeight + 27f);
        expect(NotificationBigClockModel.notificationClipTop(revealing, 1.5f) == NotificationBigClockModel.notificationClipTop(large, 1.5f));
        // Runtime uses measured glyph heights. One fixed weight and intrinsic reserve prevent
        // a second scroll/translation from changing the native list's collapse or rebound.
        NotificationBigClockModel.Frame measuredLarge = measured(0, 0f, 1f);
        NotificationBigClockModel.Frame measuredSmall = measured(10000, 0f, 1f);
        NotificationBigClockModel.Frame measuredReveal = measured(0, 0f, .15f);
        NotificationBigClockModel.Frame measuredRebound = measured(0, 80f, 1f);
        expect(measuredLarge.clockHeight == 320f && measuredSmall.clockHeight == 115f);
        expect(measuredLarge.weight == 601 && measuredSmall.weight == 601);
        expect(measuredLarge.textSizeRatio == 1f && measuredSmall.textSizeRatio == 1f);
        expect(measuredLarge.dateTop < measuredLarge.clockTop && measuredSmall.dateTop < measuredSmall.clockTop);
        expect(measuredLarge.reservedBottom == measuredSmall.reservedBottom);
        expect(measuredReveal.clockTop == measuredLarge.clockTop && measuredReveal.entryTranslation == 0f);
        expect(measuredReveal.entryAlpha > 0f && measuredReveal.entryAlpha < measuredLarge.entryAlpha);
        expect(measuredRebound.clockTop > measuredLarge.clockTop && measuredRebound.dateTop > measuredLarge.dateTop);
        expect(measuredRebound.clockHeight == measuredLarge.clockHeight && measuredRebound.reservedBottom == measuredLarge.reservedBottom);
        expect(measured(0, -80f, 1f).clockTop == measuredLarge.clockTop);
        NotificationBigClockModel.Frame invalid = NotificationBigClockModel.calculate(Float.NaN,
                Float.POSITIVE_INFINITY, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
                Integer.MAX_VALUE, Float.NaN, Float.NaN);
        expect(NotificationBigClockModel.finite(invalid.clockTop) && NotificationBigClockModel.finite(invalid.clockHeight));
        expect(invalid.clockHeight > 0f && invalid.weight >= 1 && invalid.weight <= 1000);
        expect(invalid.progress == 1f && invalid.entryAlpha == 0f);
        expect(NotificationBigClockModel.eligible(true, 2, 0, false, 1f));
        expect(!NotificationBigClockModel.eligible(true, 0, 0, false, 1f));
        NotificationBigClockModel.Frame landscape = landscape(0), scrolled = landscape(100000);
        expect(landscape.clockTop >= 24f && landscape.reservedBottom < 150f);
        expect(landscape.reservedBottom == scrolled.reservedBottom);
        expect(landscape.weight == 824 && scrolled.weight == 824);
        expect(scrolled.clockHeight == landscape.clockHeight && scrolled.clockTop < landscape.clockTop);
        expect(scrolled.clockTop + scrolled.clockHeight < 0f);
        expect(scrolled.dateTop + 19f < 0f);
        expect(NotificationBigClockModel.landscapeHeaderAlpha(1f, 0f) == 1f);
        expect(NotificationBigClockModel.landscapeHeaderAlpha(1f, 1f) == 0f);
        expect(NotificationBigClockModel.landscapeHeaderAlpha(.6f, .5f) == .3f);
        // Absolute scroll yields the same return frame; no direction latch can strand a clock.
        for (float density : new float[]{.5f, 1f, 2f, 4f, 8f}) {
            NotificationBigClockModel.Frame start = NotificationBigClockModel.landscapeMeasured(density,
                    28f * density, 72f * density, 25f * density, 19f * density,
                    12f, 20f, 25f, 75f, 130f, 824f, 0, 1f);
            float previousTop = start.clockTop, previousAlpha = 1f;
            for (int scroll = 0; scroll <= Math.ceil(start.collapseDistance); scroll++) {
                NotificationBigClockModel.Frame current = NotificationBigClockModel.landscapeMeasured(density,
                        28f * density, 72f * density, 25f * density, 19f * density,
                        12f, 20f, 25f, 75f, 130f, 824f, scroll, 1f);
                float alpha = NotificationBigClockModel.landscapeHeaderAlpha(1f, current.progress);
                expect(current.clockTop <= previousTop && alpha <= previousAlpha);
                expect(current.reservedBottom == start.reservedBottom && current.weight == 824);
                expect(current.clockTop == start.clockTop-scroll&&current.dateTop == start.dateTop-scroll);
                expect(current.clockHeight == start.clockHeight);
                previousTop = current.clockTop; previousAlpha = alpha;
            }
            NotificationBigClockModel.Frame hidden = NotificationBigClockModel.landscapeMeasured(density,
                    28f * density, 72f * density, 25f * density, 19f * density,
                    12f, 20f, 25f, 75f, 130f, 824f, Integer.MAX_VALUE, 1f);
            expect(hidden.clockTop + hidden.clockHeight < 0f && hidden.dateTop + 19f * density < 0f);
            expect(NotificationBigClockModel.landscapeHeaderAlpha(1f, hidden.progress) == 0f);
            NotificationBigClockModel.Frame returned = NotificationBigClockModel.landscapeMeasured(density,
                    28f * density, 72f * density, 25f * density, 19f * density,
                    12f, 20f, 25f, 75f, 130f, 824f, 0, 1f);
            expect(returned.clockTop == start.clockTop && returned.dateTop == start.dateTop);
        }
        expect(NotificationBigClockModel.landscapeClockBudget(360, 1) == 72f);
        expect(NotificationBigClockModel.landscapeClockBudget(200, 1) == 40f);
        expect(NotificationBigClockModel.scrollFade(0) == 0f && NotificationBigClockModel.scrollFade(1) == 1f);
        expect(NotificationBigClockModel.scrollFade(.5f) == .5f);
        expect(NotificationBigClockModel.scrollFade(Float.NaN) == 0f);
        expect(NotificationBigClockModel.nativeHeightProgress(0f,1f)==0f);
        expect(NotificationBigClockModel.nativeHeightProgress(0f,0f)==1f);
        expect(NotificationBigClockModel.nativeHeightProgress(1f,1f)==1f);
        expect(NotificationBigClockModel.nativeHeightProgress(.5f,.5f)==.75f);
        expect(NotificationBigClockModel.nativeHeightProgress(0f,1.2f)==0f);
        expect(NotificationBigClockModel.nativeHeightProgress(Float.NaN,Float.NaN)==1f);
        NotificationBigClockModel.Frame nativeBasis=measured(0,0f,1f);
        float lastNativeHeight=115f;
        for(int step=0;step<=100;step++) {
            NotificationBigClockModel.Frame nativePull=NotificationBigClockModel.withNativePanelHeight(nativeBasis,320f,115f,step/100f);
            expect(nativePull.clockHeight>=lastNativeHeight&&nativePull.clockHeight<=320f);
            expect(nativePull.clockTop==nativeBasis.clockTop&&nativePull.dateTop==nativeBasis.dateTop);
            expect(nativePull.reservedBottom==nativeBasis.reservedBottom&&nativePull.weight==nativeBasis.weight);
            lastNativeHeight=nativePull.clockHeight;
        }
        expect(NotificationLandscapeLayout.width(3168, 1440) == 1440);
        expect(NotificationLandscapeLayout.width(1240, 1440) == 1240);
        expect(NotificationLandscapeLayout.center(3168, 0, 0, 1440) == 864);
        expect(NotificationLandscapeLayout.center(3168, 80, 0, 1440) == 904);
        for (int height = 200; height <= 1200; height += 10) {
            float budget = NotificationBigClockModel.landscapeClockBudget(height, 1f);
            expect(budget <= height * .2f && budget <= 72f && budget >= 0f);
        }
        System.out.println("Notification big clock model checks passed: " + checks);
    }
    private static NotificationBigClockModel.Frame measured(int scroll, float over, float fraction) {
        return NotificationBigClockModel.measured(1280f, 1.5f, 48f, 320f, 115f, 28f,
                12f, 18f, 0f, 0f, 0f, 601f, 300f, scroll, over, fraction);
    }
    private static NotificationBigClockModel.Frame landscape(int scroll) {
        return NotificationBigClockModel.landscapeMeasured(1f, 24f, 72f, 25f, 19f,
                12f, 12f, 0f, 0f, 0f, 824f, scroll, 1f);
    }
}
