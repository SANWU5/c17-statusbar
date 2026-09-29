package android.content;
public class Context {
    public Context getApplicationContext(){return this;}
    public Object getSystemService(String name){return new android.os.PowerManager();}
    public String getPackageName(){return "dev.puitheme.iosstatusbar";}
}
