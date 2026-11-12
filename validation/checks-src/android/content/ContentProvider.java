package android.content;
/** Provider lifecycle fixture: tests execute the product's real call/authentication methods. */
public abstract class ContentProvider {
    private Context context;
    public void attachInfo(Context context,android.content.pm.ProviderInfo info){
        if(this.context!=null)throw new IllegalStateException("Provider already attached");
        this.context=context;onCreate();
    }
    public final Context getContext(){return context;}
    public abstract boolean onCreate();
    public android.os.Bundle call(String method,String argument,android.os.Bundle extras){return null;}
    public android.os.ParcelFileDescriptor openFile(android.net.Uri uri,String mode)throws java.io.FileNotFoundException{throw new java.io.FileNotFoundException(uri.toString());}
    public abstract android.database.Cursor query(android.net.Uri uri,String[] projection,String selection,String[] arguments,String sortOrder);
    public abstract String getType(android.net.Uri uri);
    public abstract android.net.Uri insert(android.net.Uri uri,ContentValues values);
    public abstract int delete(android.net.Uri uri,String selection,String[] arguments);
    public abstract int update(android.net.Uri uri,ContentValues values,String selection,String[] arguments);
}
