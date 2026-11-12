package android.content;
public class Context {
    public android.graphics.drawable.Drawable getDrawable(int id){return getResources().getDrawable(id,getTheme());}
    private java.io.File filesRootForCheck;
    private ContentResolver resolverForCheck;
    public Context(){}
    public Context(java.io.File filesRoot){filesRootForCheck=filesRoot;resolverForCheck=new ContentResolver();}
    public static final int CONTEXT_IGNORE_SECURITY=2,RECEIVER_EXPORTED=2,RECEIVER_NOT_EXPORTED=4;
    public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}
    public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();}
    public ClassLoader getClassLoader(){return getClass().getClassLoader();}
    public android.view.Display getDisplay(){return new android.view.Display();}
    public android.content.res.Resources.Theme getTheme(){return null;}
    public Context createPackageContext(String name,int flags){return this;}
    public Context getApplicationContext(){return this;}
    public Context createDeviceProtectedStorageContext(){return this;}
    public java.io.File getFilesDir(){if(filesRootForCheck!=null)return filesRootForCheck;throw new UnsupportedOperationException("Private files fixture unavailable");}
    public boolean isDeviceProtectedStorage(){return true;}
    public boolean deleteSharedPreferences(String name){throw new UnsupportedOperationException(name);}
    public SharedPreferences getSharedPreferences(String name,int mode){throw new UnsupportedOperationException(name);}
    public ContentResolver getContentResolver(){return resolverForCheck==null?new ContentResolver():resolverForCheck;}
    public Object getSystemService(String name){return new android.os.PowerManager();}
    public String getPackageName(){return "dev.puitheme.iosstatusbar";}
    public java.io.File cacheDirForCheck;
    public java.io.File getCacheDir(){return cacheDirForCheck;}
    public void grantUriPermission(String target,android.net.Uri uri,int flags){}
    public void revokeUriPermission(String target,android.net.Uri uri,int flags){}
    public int revokedForCheck;
    public void revokeUriPermission(android.net.Uri uri,int flags){revokedForCheck++;}
    public android.content.pm.PackageManager getPackageManager(){throw new UnsupportedOperationException("PackageManager fixture unavailable");}
    public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter,int flags){return null;}
    public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter){return null;}
    public void unregisterReceiver(BroadcastReceiver receiver){}
    public final java.util.List<ComponentCallbacks> componentCallbacksForCheck=new java.util.ArrayList<>();
    public int componentRegistrationsForCheck,componentUnregistrationsForCheck;
    public void registerComponentCallbacks(ComponentCallbacks callbacks){componentCallbacksForCheck.add(callbacks);componentRegistrationsForCheck++;}
    public void unregisterComponentCallbacks(ComponentCallbacks callbacks){componentCallbacksForCheck.remove(callbacks);componentUnregistrationsForCheck++;}
}
