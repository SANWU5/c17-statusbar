package android.provider;
public final class Settings {
    private Settings(){}
    public static final class Global {
        public static int getInt(android.content.ContentResolver resolver,String key,int fallback){return fallback;}
        public static android.net.Uri getUriFor(String key){return android.net.Uri.parse("content://settings/global/"+key);}
    }
    public static final class System {
        public static final java.util.Map<String,Integer> values=new java.util.HashMap<>();
        public static final java.util.Map<Integer,java.util.Map<String,Integer>> userValues=new java.util.HashMap<>();
        public static int currentUser,reads;
        public static boolean failReads;
        public static int getInt(android.content.ContentResolver resolver,String key,int fallback){
            reads++;if(failReads)throw new SecurityException("Settings fixture denied");
            Integer value=values.get(key);return value==null?fallback:value;
        }
        public static int getIntForUser(android.content.ContentResolver resolver,String key,int fallback,int user){
            if(user==-2)user=currentUser;
            java.util.Map<String,Integer> selected=userValues.get(user);
            if(selected==null)return getInt(resolver,key,fallback);
            reads++;if(failReads)throw new SecurityException("Settings fixture denied");
            Integer value=selected.get(key);return value==null?fallback:value;
        }
        public static android.net.Uri getUriFor(String key){return android.net.Uri.parse("content://settings/system/"+key);}
    }
    public static final class Secure {
        public static android.net.Uri getUriFor(String key){return android.net.Uri.parse("content://settings/secure/"+key);}
    }
}
