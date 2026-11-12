package dalvik.system;

/** Desktop runtime checks use real fixture JARs through the Android loader constructor contract. */
public class DexClassLoader extends java.net.URLClassLoader {
    public DexClassLoader(String dexPath,String optimizedDirectory,String librarySearchPath,ClassLoader parent) {
        super(urls(dexPath),parent);
    }
    private static java.net.URL[] urls(String path) {
        String[] parts=path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator));
        java.net.URL[] urls=new java.net.URL[parts.length];
        try {for(int i=0;i<parts.length;i++)urls[i]=new java.io.File(parts[i]).toURI().toURL();}
        catch(java.net.MalformedURLException impossible){throw new IllegalArgumentException(impossible);}
        return urls;
    }
}
