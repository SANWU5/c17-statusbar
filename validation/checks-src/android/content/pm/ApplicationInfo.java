package android.content.pm;
public class ApplicationInfo {
    public static final int FLAG_SYSTEM=1,FLAG_UPDATED_SYSTEM_APP=128;
    public boolean enabled=true;
    public int flags,theme,uid;
    public String packageName,sourceDir,publicSourceDir,dataDir,processName;
}
