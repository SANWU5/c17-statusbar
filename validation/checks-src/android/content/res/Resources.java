package android.content.res;
import android.util.DisplayMetrics;
public class Resources {
    public static class Theme { }
    public static class NotFoundException extends RuntimeException {public NotFoundException(String value){super(value);}}
    public android.graphics.drawable.Drawable getDrawable(int id,Theme theme){return null;}
    public float getDimension(int id){return 0;}
    private final DisplayMetrics metrics = new DisplayMetrics();
    private final Configuration configuration=new Configuration();
    public DisplayMetrics getDisplayMetrics() { return metrics; }
    public Configuration getConfiguration() {return configuration;}
    public static Resources getSystem() { return new Resources(); }
    public String getResourceEntryName(int id) { return "clock"; }
    public String getResourcePackageName(int id) { return "com.android.systemui"; }
    public int getIdentifier(String name,String type,String pkg) { return 0; }
}
