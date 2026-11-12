package android.content.res;
import android.util.DisplayMetrics;
public class Resources {
    public class Theme { }
    public static class NotFoundException extends RuntimeException {public NotFoundException(String value){super(value);}}
    public android.graphics.drawable.Drawable getDrawable(int id,Theme theme){return null;}
    public android.graphics.drawable.Drawable getDrawable(int id){return getDrawable(id,null);}
    public float getDimension(int id){return 0;}
    private final DisplayMetrics metrics = new DisplayMetrics();
    private final Configuration configuration=new Configuration();
    public DisplayMetrics getDisplayMetrics() { return metrics; }
    public Configuration getConfiguration() {return configuration;}
    public static Resources getSystem() { return new Resources(); }
    /** Names for the checks that need them; without any the fixture answers "clock" as before. */
    public final java.util.Map<Integer,String> entryNames=new java.util.HashMap<>();
    public String getResourceEntryName(int id) {
        if(entryNames.isEmpty())return "clock";
        String name=entryNames.get(id);
        if(name==null)throw new NotFoundException("resource "+id);
        return name;
    }
    public String getResourcePackageName(int id) { return "com.android.systemui"; }
    public int getIdentifier(String name,String type,String pkg) { return 0; }
    public String getString(int id) { throw new NotFoundException("string:"+id); }
    public int getColor(int id,Theme theme) { throw new NotFoundException("color:"+id); }
}
