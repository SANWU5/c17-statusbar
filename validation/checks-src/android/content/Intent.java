package android.content;
public class Intent {
    public static final int FLAG_GRANT_READ_URI_PERMISSION=1;
    public static final String ACTION_VIEW="android.intent.action.VIEW",ACTION_MAIN="android.intent.action.MAIN",
        ACTION_BATTERY_CHANGED="android.intent.action.BATTERY_CHANGED",
        CATEGORY_APP_CALENDAR="android.intent.category.APP_CALENDAR",CATEGORY_LAUNCHER="android.intent.category.LAUNCHER",
        ACTION_CONFIGURATION_CHANGED="android.intent.action.CONFIGURATION_CHANGED",
        ACTION_USER_UNLOCKED="android.intent.action.USER_UNLOCKED",
        ACTION_LOCKED_BOOT_COMPLETED="android.intent.action.LOCKED_BOOT_COMPLETED",
        ACTION_BOOT_COMPLETED="android.intent.action.BOOT_COMPLETED";
    public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000,FLAG_ACTIVITY_CLEAR_TOP=0x04000000,
        FLAG_ACTIVITY_SINGLE_TOP=0x20000000,FLAG_ACTIVITY_CLEAR_TASK=0x00008000;
    public static final String ACTION_PACKAGE_ADDED="android.intent.action.PACKAGE_ADDED",
        ACTION_PACKAGE_CHANGED="android.intent.action.PACKAGE_CHANGED",ACTION_PACKAGE_REMOVED="android.intent.action.PACKAGE_REMOVED",
        ACTION_PACKAGE_REPLACED="android.intent.action.PACKAGE_REPLACED",ACTION_PACKAGE_FULLY_REMOVED="android.intent.action.PACKAGE_FULLY_REMOVED",
        EXTRA_REPLACING="android.intent.extra.REPLACING";
    private String action,packageName;private android.net.Uri data;private ComponentName component;private int flags;
    private final java.util.Map<String,Object> extras=new java.util.HashMap<>();
    private final java.util.Set<String> categories=new java.util.LinkedHashSet<>();
    public Intent(){}
    public Intent(Intent source){action=source.action;packageName=source.packageName;data=source.data;
        component=source.component;flags=source.flags;categories.addAll(source.categories);extras.putAll(source.extras);}
    public Intent(String action){this.action=action;}
    public Intent(String action,android.net.Uri data){this.action=action;this.data=data;}
    public Intent addCategory(String category){categories.add(category);return this;}
    public boolean hasCategory(String category){return categories.contains(category);}
    public Intent addFlags(int value){flags|=value;return this;}
    public int getFlags(){return flags;}
    public Intent setComponent(ComponentName value){component=value;return this;}
    public ComponentName getComponent(){return component;}
    public android.net.Uri getData(){return data;}
    public String getAction(){return action;}
    public Intent setPackage(String value){packageName=value;return this;}
    public String getPackage(){return packageName;}
    public Intent putExtra(String key,boolean value){extras.put(key,value);return this;}
    public Intent putExtra(String key,int value){extras.put(key,value);return this;}
    public boolean getBooleanExtra(String key,boolean fallback){Object value=extras.get(key);return value instanceof Boolean?(Boolean)value:fallback;}
    public int getIntExtra(String key,int fallback){Object value=extras.get(key);return value instanceof Integer?(Integer)value:fallback;}
}
