package android.content;
public class ContentResolver {
    public int notifications;
    public void notifyChange(android.net.Uri uri,android.database.ContentObserver observer){notifications++;}
}
