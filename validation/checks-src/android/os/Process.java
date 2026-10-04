package android.os;
/** SystemUI UID fixture, observable without invoking the Android stub runtime. */
public class Process {
    public static final int THREAD_PRIORITY_BACKGROUND=10;
    public static int uidForCheck=1000,pidForCheck=17;
    public static int myUid(){return uidForCheck;}
    public static int myPid(){return pidForCheck;}
    public static void setThreadPriority(int value) { }
}
