package android.content.res;
import android.util.DisplayMetrics;
public class Resources {
    private final DisplayMetrics metrics = new DisplayMetrics();
    public DisplayMetrics getDisplayMetrics() { return metrics; }
    public static Resources getSystem() { return new Resources(); }
    public String getResourceEntryName(int id) { return "clock"; }
    public int getIdentifier(String name,String type,String pkg) { return 0; }
}
