package android.os;
public final class Looper {
    private static final Looper MAIN = new Looper();
    private static final ThreadLocal<Looper> CURRENT=ThreadLocal.withInitial(()->MAIN);
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return CURRENT.get(); }
    public static void setCurrentForCheck(Looper value){CURRENT.set(value);}
    public static void resetCurrentForCheck(){CURRENT.remove();}
}
