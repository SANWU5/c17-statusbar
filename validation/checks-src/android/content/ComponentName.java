package android.content;
public final class ComponentName {
    private final String packageName,className;
    public ComponentName(String packageName,String className){this.packageName=packageName;this.className=className;}
    public String getPackageName(){return packageName;}
    public String getClassName(){return className;}
    @Override public boolean equals(Object other){return other instanceof ComponentName&&packageName.equals(((ComponentName)other).packageName)&&className.equals(((ComponentName)other).className);}
    @Override public int hashCode(){return packageName.hashCode()*31+className.hashCode();}
}
