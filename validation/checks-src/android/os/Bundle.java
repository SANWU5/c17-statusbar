package android.os;
public class Bundle {
    private final java.util.Map<String,Object> values=new java.util.HashMap<>();
    public Bundle() { }
    public Bundle(Bundle source) { values.putAll(source.values); }
    public java.util.Set<String> keySet() { return values.keySet(); }
    public Object get(String key){return values.get(key);}
    public void putFloat(String key,float value){values.put(key,value);}
    public void putInt(String key,int value){values.put(key,value);}
    public void putBoolean(String key,boolean value){values.put(key,value);}
    public void putString(String key,String value){values.put(key,value);}
    public boolean getBoolean(String key,boolean fallback){Object value=get(key);return value instanceof Boolean?(Boolean)value:fallback;}
    public String getString(String key){return getString(key,null);}
    public String getString(String key,String fallback){Object value=get(key);return value instanceof String?(String)value:fallback;}
}
