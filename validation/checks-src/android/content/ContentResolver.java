package android.content;
public class ContentResolver {
    public int notifications;
    public void notifyChange(android.net.Uri uri,android.database.ContentObserver observer){notifications++;}
    public android.os.Bundle call(android.net.Uri uri,String method,String argument,android.os.Bundle extras){return null;}
}
