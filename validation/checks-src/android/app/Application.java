package android.app;

/** Minimal framework application for exercising the actual attach bootstrap. */
public class Application extends android.content.Context {
    private android.content.Context base;
    private void attach(android.content.Context context){base=context;}
    public android.content.Context getBaseContext(){return base;}
    @Override public String getPackageName(){return base.getPackageName();}
    @Override public android.content.Context getApplicationContext(){return this;}
    @Override public android.content.Context createDeviceProtectedStorageContext(){return base.createDeviceProtectedStorageContext();}
    @Override public java.io.File getFilesDir(){return base.getFilesDir();}
    @Override public android.content.ContentResolver getContentResolver(){return base.getContentResolver();}
    @Override public android.content.pm.PackageManager getPackageManager(){return base.getPackageManager();}
    @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences(name,mode);}
}
