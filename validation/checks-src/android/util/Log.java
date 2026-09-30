package android.util;
public class Log {
    public static final int VERBOSE=2,DEBUG=3,INFO=4,WARN=5,ERROR=6,ASSERT=7;
    public static int i(String tag,String message){return 0;}
    public static int w(String tag,String message){return 0;}
    public static int w(String tag,String message,Throwable error){return 0;}
    public static int d(String tag,String message){return 0;}
    public static int e(String tag,String message,Throwable error){return 0;}
    public static int println(int priority,String tag,String message){return 0;}
    public static String getStackTraceString(Throwable error){return error.toString();}
}
