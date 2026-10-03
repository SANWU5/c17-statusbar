package android.content;
public class Context {
    public static final int CONTEXT_IGNORE_SECURITY=2,RECEIVER_EXPORTED=2;
    public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}
    public Context createPackageContext(String name,int flags){return this;}
    public Context getApplicationContext(){return this;}
    public Context createDeviceProtectedStorageContext(){return this;}
    public java.io.File getFilesDir(){throw new UnsupportedOperationException("Private files fixture unavailable");}
    public boolean isDeviceProtectedStorage(){return true;}
    public SharedPreferences getSharedPreferences(String name,int mode){throw new UnsupportedOperationException(name);}
    public ContentResolver getContentResolver(){return new ContentResolver();}
    public Object getSystemService(String name){return new android.os.PowerManager();}
    public String getPackageName(){return "dev.puitheme.iosstatusbar";}
    public android.content.pm.PackageManager getPackageManager(){throw new UnsupportedOperationException("PackageManager fixture unavailable");}
    public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter,int flags){return null;}
    public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter){return null;}
    public void unregisterReceiver(BroadcastReceiver receiver){}
}
