package android.content;
public class Intent {
    public static final String ACTION_PACKAGE_ADDED="android.intent.action.PACKAGE_ADDED",
        ACTION_PACKAGE_CHANGED="android.intent.action.PACKAGE_CHANGED",ACTION_PACKAGE_REMOVED="android.intent.action.PACKAGE_REMOVED",
        ACTION_PACKAGE_REPLACED="android.intent.action.PACKAGE_REPLACED",ACTION_PACKAGE_FULLY_REMOVED="android.intent.action.PACKAGE_FULLY_REMOVED",
        EXTRA_REPLACING="android.intent.extra.REPLACING";
    public android.net.Uri getData(){return null;}
    public String getAction(){return null;}
    public boolean getBooleanExtra(String key,boolean fallback){return fallback;}
}
