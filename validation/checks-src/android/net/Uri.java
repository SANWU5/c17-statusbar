package android.net;
public class Uri {
    private final String value;
    private Uri(String value){this.value=value;}
    public static Uri parse(String value){return new Uri(value);}
    private java.net.URI parsed(){return java.net.URI.create(value);}
    public String getScheme(){return parsed().getScheme();}
    public String getAuthority(){return parsed().getAuthority();}
    public String getQuery(){return parsed().getRawQuery();}
    public String getFragment(){return parsed().getRawFragment();}
    public String getPath(){return parsed().getPath();}
    public java.util.List<String> getPathSegments(){
        java.util.List<String> segments=new java.util.ArrayList<>();
        String raw=parsed().getRawPath();
        if(raw!=null)for(String segment:raw.split("/"))if(!segment.isEmpty())
            segments.add(java.net.URLDecoder.decode(segment,java.nio.charset.StandardCharsets.UTF_8));
        return segments;
    }
    @Override public String toString(){return value;}
}
