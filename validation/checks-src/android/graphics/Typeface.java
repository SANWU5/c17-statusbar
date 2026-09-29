package android.graphics;
public class Typeface {
    public static final Typeface DEFAULT=new Typeface();
    public static final int BOLD=1,NORMAL=0;
    public int weight;
    private static final java.util.Map<String,Typeface> cache=new java.util.HashMap<>();
    public static Typeface create(String family, int style) { return new Typeface(); }
    public static Typeface create(Typeface family,int weight,boolean italic) {
        String key=System.identityHashCode(family)+":"+weight;
        Typeface value=cache.get(key);if(value==null){value=new Typeface();value.weight=weight;cache.put(key,value);}return value;
    }
    public static Typeface create(Typeface family,int style) { return create(family,style==BOLD?700:400,false); }
}
