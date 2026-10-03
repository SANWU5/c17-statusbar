package android.content.pm;
import android.content.ComponentName;
public abstract class PackageManager {
    public static final int COMPONENT_ENABLED_STATE_DEFAULT=0,COMPONENT_ENABLED_STATE_ENABLED=1,
        COMPONENT_ENABLED_STATE_DISABLED=2,COMPONENT_ENABLED_STATE_DISABLED_USER=3,COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED=4,
        DONT_KILL_APP=1,MATCH_DISABLED_COMPONENTS=512,GET_SIGNATURES=64,GET_SIGNING_CERTIFICATES=134217728;
    public static class NameNotFoundException extends Exception {
        public NameNotFoundException(){}
        public NameNotFoundException(String message){super(message);}
    }
    public abstract ActivityInfo getActivityInfo(ComponentName component,int flags)throws NameNotFoundException;
    public abstract int getComponentEnabledSetting(ComponentName component);
    public abstract void setComponentEnabledSetting(ComponentName component,int state,int flags);
    public ApplicationInfo getApplicationInfo(String name,int flags)throws NameNotFoundException{throw new NameNotFoundException(name);}
    public android.graphics.drawable.Drawable getApplicationIcon(String name)throws NameNotFoundException{throw new NameNotFoundException(name);}
    public PackageInfo getPackageInfo(String name,int flags)throws NameNotFoundException{throw new NameNotFoundException(name);}
    public PackageInfo getPackageArchiveInfo(String name,int flags){return null;}
    public String[] getPackagesForUid(int uid){return null;}
}
