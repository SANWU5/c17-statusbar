package android.content;
public class Context {
    public static final int CONTEXT_IGNORE_SECURITY=2;
    public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}
    public Context createPackageContext(String name,int flags){return this;}
    public Context getApplicationContext(){return this;}
    public Object getSystemService(String name){return new android.os.PowerManager();}
    public String getPackageName(){return "dev.puitheme.iosstatusbar";}
}
