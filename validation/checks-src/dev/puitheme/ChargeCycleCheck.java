package dev.puitheme;

public final class ChargeCycleCheck {
    private static int checks;
    private static void near(float expected, float actual) {
        checks++; if (Math.abs(expected - actual) > .0001f) throw new AssertionError(expected + " != " + actual);
    }
    private static void equal(long expected, long actual) {
        checks++; if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        ChargeCycle cycle = new ChargeCycle();
        near(0, cycle.boltOpacity(100)); equal(Long.MAX_VALUE, cycle.nextDelay(100));
        cycle.setCharging(true, 100);
        near(1, cycle.boltOpacity(100)); equal(3000, cycle.nextDelay(100));
        near(1, cycle.boltOpacity(3099)); equal(1, cycle.nextDelay(3099));
        near(.5f, cycle.boltOpacity(3600)); equal(33, cycle.nextDelay(3600));
        near(0, cycle.boltOpacity(4100)); equal(3000, cycle.nextDelay(4100));
        near(0, cycle.boltOpacity(7099)); equal(1, cycle.nextDelay(7099));
        near(.5f, cycle.boltOpacity(7600)); equal(33, cycle.nextDelay(7600));
        near(1, cycle.boltOpacity(8100)); equal(3000, cycle.nextDelay(8100));
        cycle.setCharging(true, 2000); near(.5f, cycle.boltOpacity(3600));
        for (int i = 0; i <= 32000; i += 31) {
            float value = cycle.boltOpacity(100 + i);
            if (value < 0 || value > 1 || Float.isNaN(value)) throw new AssertionError("Invalid opacity");
            equal(1, cycle.nextDelay(100 + i) > 0 ? 1 : 0);
            near(value, cycle.boltOpacity(100 + i + 8000));
        }
        float previous = 1;
        for (int i = 0; i <= 1000; i += 10) {
            float value = cycle.boltOpacity(3100 + i);
            equal(1, value <= previous ? 1 : 0); previous = value;
        }
        cycle.setCharging(false, 9000); near(0, cycle.boltOpacity(9000)); equal(Long.MAX_VALUE, cycle.nextDelay(9000));
        cycle.setCharging(true, 9500); near(1, cycle.boltOpacity(9500)); equal(3000, cycle.nextDelay(9500));
        cycle.configure(2.37f, .81f); equal(2370, cycle.nextDelay(9500)); near(.5f, cycle.boltOpacity(12275));
        cycle.configure(Float.NaN, Float.POSITIVE_INFINITY); equal(3000, cycle.nextDelay(9500));
        cycle.configure(-20, -20); equal(Long.MAX_VALUE, cycle.nextDelay(9500)); near(1f, cycle.boltOpacity(10575));
        cycle.configure(100, 100); equal(100000, cycle.nextDelay(9500)); near(.5f, cycle.boltOpacity(159500));
        near(1, cycle.boltOpacity(0));
        cycle.configure(0,0);near(1,cycle.boltOpacity(9500));near(1,cycle.boltOpacity(Long.MAX_VALUE));
        equal(Long.MAX_VALUE,cycle.nextDelay(9500));
        cycle.configure(0,.2f);near(1,cycle.boltOpacity(9500));near(.5f,cycle.boltOpacity(9600));
        near(0,cycle.boltOpacity(9700));near(.5f,cycle.boltOpacity(9800));equal(33,cycle.nextDelay(9500));
        cycle.configure(.2f,0);near(1,cycle.boltOpacity(9500));near(0,cycle.boltOpacity(9700));
        near(1,cycle.boltOpacity(9900));equal(200,cycle.nextDelay(9500));equal(200,cycle.nextDelay(9700));
        cycle.configure(Float.MAX_VALUE,Float.MAX_VALUE);near(1,cycle.boltOpacity(9500));
        equal(Long.MAX_VALUE/8,cycle.nextDelay(9500));
        for(long uptime:new long[]{0,9500,Long.MAX_VALUE/8,Long.MAX_VALUE/4,Long.MAX_VALUE/2,Long.MAX_VALUE}) {
            float value=cycle.boltOpacity(uptime);equal(1,value>=0&&value<=1&&!Float.isNaN(value)?1:0);
            equal(1,cycle.nextDelay(uptime)>0?1:0);
        }
        System.out.println("ChargeCycleCheck passed: " + checks);
    }
}
