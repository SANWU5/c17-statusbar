package android.os;
public class Bundle implements Parcelable {
    private final java.util.Map<String,Object> values=new java.util.HashMap<>();
    public Bundle() { }
    public Bundle(Bundle source) { values.putAll(source.values); }
    public java.util.Set<String> keySet() { return values.keySet(); }
    public boolean containsKey(String key) { return values.containsKey(key); }
    public void remove(String key){values.remove(key);}
    public Object get(String key){return values.get(key);}
    public void putFloat(String key,float value){values.put(key,value);}
    public void putForWallpaperCheck(String key,Object value){values.put(key,value);}
    public void putInt(String key,int value){values.put(key,value);}
    public void putLong(String key,long value){values.put(key,value);}
    public void putBoolean(String key,boolean value){values.put(key,value);}
    public void putString(String key,String value){values.put(key,value);}
    public boolean getBoolean(String key,boolean fallback){Object value=get(key);return value instanceof Boolean?(Boolean)value:fallback;}
    public float getFloat(String key){return getFloat(key,0f);}
    public float getFloat(String key,float fallback){Object value=get(key);return value instanceof Float?(Float)value:fallback;}
    public String getString(String key){return getString(key,null);}
    public String getString(String key,String fallback){Object value=get(key);return value instanceof String?(String)value:fallback;}
    public int getInt(String key,int fallback){Object value=get(key);return value instanceof Integer?(Integer)value:fallback;}
    public boolean getBoolean(String key){return getBoolean(key,false);}
    public int getInt(String key){return getInt(key,0);}
    public long getLong(String key){return getLong(key,0L);}
    public long getLong(String key,long fallback){Object value=get(key);return value instanceof Long?(Long)value:fallback;}
    public void putStringArrayList(String key,java.util.ArrayList<String> value){values.put(key,value);}
    @SuppressWarnings("unchecked")
    public java.util.ArrayList<String> getStringArrayList(String key){Object value=get(key);return value instanceof java.util.ArrayList?(java.util.ArrayList<String>)value:null;}
    public void putBundle(String key,Bundle value){values.put(key,value);}
    public Bundle getBundle(String key){Object value=get(key);return value instanceof Bundle?(Bundle)value:null;}
    public void putParcelable(String key,Parcelable value){values.put(key,value);}
    @SuppressWarnings("unchecked")
    public <T extends Parcelable> T getParcelable(String key){Object value=get(key);return value instanceof Parcelable?(T)value:null;}
    public <T> T getParcelable(String key,Class<T> type){Object value=get(key);return type.isInstance(value)?type.cast(value):null;}
    @Override public int describeContents(){return 0;}
    @Override public void writeToParcel(Parcel destination,int flags){throw new UnsupportedOperationException("Parcel serialization is not emulated");}
}
