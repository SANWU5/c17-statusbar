package dev.puitheme;
import java.util.Arrays;
public final class ResourceMemoryCacheCheck {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("resource cache "+checks);}
    public static void main(String[] args){
        ResourceMemoryCache cache=new ResourceMemoryCache(12);
        byte[] data={1,2,3,4};cache.put("core",data,true);data[0]=9;
        check(cache.get("core")[0]==1);byte[] read=cache.get("core");read[0]=8;check(cache.get("core")[0]==1);
        cache.put("old",new byte[4],false);cache.put("new",new byte[4],false);cache.put("latest",new byte[4],false);
        check(cache.bytes()==12);check(cache.get("old")==null);check(cache.get("core")!=null);
        cache.trim(false);check(cache.bytes()==4);check(cache.get("core")!=null);
        cache.put("core",new byte[3],true);check(cache.bytes()==3);cache.put("oversize",new byte[13],false);check(cache.bytes()==3);
        cache.put("file:1",new byte[2],false);cache.put("file:2",new byte[2],false);cache.removePrefixExcept("file:","file:2");
        check(cache.get("file:1")==null);check(cache.get("file:2")!=null);check(cache.bytes()==5);
        cache.trim(true);check(cache.bytes()==0);check(cache.get("core")==null);
        for(int i=0;i<10000;i++){cache.put("version:"+i,new byte[]{(byte)i},true);check(cache.bytes()<=12);}
        check(Arrays.equals(cache.get("version:9999"),new byte[]{(byte)9999}));
        ResourceMemoryCache disabled=new ResourceMemoryCache(0);disabled.put("x",new byte[1],true);check(disabled.get("x")==null);
        System.out.println("Resource memory cache checks: "+checks);
    }
}
