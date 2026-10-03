package dev.puitheme;

public final class NotificationStackTapCheck {
    private static int checks;
    public static void main(String[] args) {
        for (int offset = 0; offset < 50; offset++) {
            NotificationStackTap tap = new NotificationStackTap();
            check(!tap.event(0,1,30,40,offset,true,8,500));
            check(tap.event(1,1,32,44,offset+100,true,8,500));
            check(!tap.event(1,1,32,44,offset+110,true,8,500));
            tap.event(0,1,30,40,offset,true,8,500);
            check(!tap.event(2,1,60,40,offset+40,true,8,500));
            check(!tap.event(1,1,30,40,offset+100,true,8,500));
            tap.event(0,1,30,40,offset,true,8,500);
            check(!tap.event(1,1,30,40,offset+500,true,8,500));
            tap.event(0,1,30,40,offset,true,8,500);
            check(!tap.event(5,2,30,40,offset+40,true,8,500));
            check(!tap.event(1,1,30,40,offset+100,true,8,500));
            tap.event(0,1,30,40,offset,true,8,500);
            check(!tap.event(3,1,30,40,offset+40,true,8,500));
            check(!tap.event(1,1,30,40,offset+100,true,8,500));
            tap.event(0,1,30,40,offset,true,8,500);
            check(!tap.event(1,1,30,40,offset+100,false,8,500));
            tap.event(0,1,30,40,offset,false,8,500);
            check(!tap.event(1,1,30,40,offset+100,true,8,500));
            tap.event(0,1,30,40,offset,true,8,500);
            tap.reset(); check(!tap.event(1,1,30,40,offset+100,true,8,500));
            tap.event(0,1,30,40,offset,true,8,500);
            check(!tap.event(1,1,Float.NaN,40,offset+100,true,8,500));
            tap.event(0,1,30,40,offset+10,true,8,500);
            check(!tap.event(1,1,30,40,offset,true,8,500));
        }
        System.out.println("NotificationStackTapCheck: " + checks + " checks passed");
    }
    private static void check(boolean result) { checks++; if (!result) throw new AssertionError("tap check " + checks); }
}
