package dev.puitheme;

import java.awt.Color;
import java.util.Random;

public final class HsbColorCheck {
    private static int checks;
    private static void require(boolean valid, String name) { checks++; if (!valid) throw new AssertionError(name); }
    private static void near(double a, double b, String name) { require(Math.abs(a - b) < .00001, name); }
    public static void main(String[] args) {
        int[] anchors = {0xff000000, 0xffffffff, 0xffff0000, 0xff00ff00, 0xff0000ff, 0xff00ffff, 0xffff00ff, 0xffffff00, 0x12345678, 0x00abcdef};
        for (int argb : anchors) {
            HsbColor color = new HsbColor(argb, true);
            require(color.argb() == argb, "ARGB round trip");
            HsbColor parsed = new HsbColor(0, false);
            require(parsed.readHex(color.hex()) && parsed.argb() == argb && parsed.customAlpha(), "hex ARGB round trip");
        }
        HsbColor color = new HsbColor(0xff2869e8, false);
        require(color.readHex(" #ff123456 ") && color.customAlpha(), "explicit FF retains alpha policy");
        require(color.hex().equals("#FF123456"), "explicit alpha canonical form");
        require(color.readHex("123456") && !color.customAlpha() && color.argb() == 0xff123456, "six digit system opacity");
        String before = color.hex();
        for (String bad : new String[]{null, "", "#123", "#1234567", "#123456789", "#GG1234", "red", "-123456"}) {
            require(!color.readHex(bad) && color.hex().equals(before), "invalid hex does not mutate");
        }
        color.setHsb(210.12, 54.32, 78.91);
        near(color.hue(), 210.12, "hue decimal"); near(color.saturation(), 54.32, "saturation decimal"); near(color.brightness(), 78.91, "brightness decimal");
        color.setOpacity(33.33);
        require(color.customAlpha() && color.argb() >>> 24 == 85, "decimal opacity rounds to ARGB byte");
        color.setRgb(0xff22c55e);
        require(color.customAlpha() && color.argb() >>> 24 == 85, "preset preserves opacity");
        color.followSystem(true);
        require(!color.customAlpha() && color.argb() >>> 24 == 255 && color.hex().length() == 7, "follow native opacity mode");
        color.followSystem(false);
        require(color.customAlpha() && color.argb() >>> 24 == 85, "switch restores chosen opacity");
        color.setHsb(360, 100, 100); require((color.argb() & 0xffffff) == 0xff0000, "360 hue endpoint");
        color.setHsb(126.78, 67.89, 0); near(color.hue(), 126.78, "black drag retains hue"); near(color.saturation(), 67.89, "black drag retains saturation");
        color.setRgb(0xff808080); near(color.hue(), 126.78, "grey hex retains last hue");
        for (double bad : new double[]{-0.01, 360.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            double h = color.hue(); boolean rejected = false;
            try { color.setHsb(bad, 50, 50); } catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected && color.hue() == h, "invalid hue rejected atomically");
        }
        boolean rejected = false;
        try { color.setHsb(60, 101, 40); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected && color.hue() == 126.78, "invalid saturation does not partly apply hue");
        color.setOpacity(0); require(color.argb() >>> 24 == 0, "zero opacity");
        color.setOpacity(100); require(color.argb() >>> 24 == 255 && color.hex().length() == 9, "100 percent still explicit");
        Random random = new Random(1709);
        for (int i = 0; i < 10000; i++) {
            int argb = random.nextInt();
            HsbColor sample = new HsbColor(argb, true);
            require(sample.argb() == argb, "random RGB/HSB round trip");
            float[] reference = Color.RGBtoHSB(argb >> 16 & 255, argb >> 8 & 255, argb & 255, null);
            require(Math.abs(sample.saturation() - reference[1] * 100) < .00002
                    && Math.abs(sample.brightness() - reference[2] * 100) < .00002, "independent HSB reference");
            sample.setHsb(random.nextDouble() * 360, random.nextDouble() * 100, random.nextDouble() * 100);
            int expected = Color.HSBtoRGB((float) (sample.hue() / 360), (float) (sample.saturation() / 100), (float) (sample.brightness() / 100));
            int actual = sample.argb();
            require(Math.abs((expected >> 16 & 255) - (actual >> 16 & 255)) <= 1
                    && Math.abs((expected >> 8 & 255) - (actual >> 8 & 255)) <= 1
                    && Math.abs((expected & 255) - (actual & 255)) <= 1, "random HSB RGB independent reference");
        }
        System.out.println("HsbColorCheck passed: " + checks);
    }
}
