package android.graphics.drawable;
import android.graphics.Bitmap;
/** Observable bitmap revisions and unknown URI assets for media content deduplication checks. */
public final class Icon {
    private final int type;private final Bitmap bitmap;private final String pkg;private final int id;
    private Icon(int type,Bitmap bitmap,String pkg,int id){this.type=type;this.bitmap=bitmap;this.pkg=pkg;this.id=id;}
    public static Icon createWithBitmap(Bitmap bitmap){return new Icon(1,bitmap,null,0);}
    public static Icon createWithAdaptiveBitmap(Bitmap bitmap){return new Icon(5,bitmap,null,0);}
    public static Icon createWithResource(String pkg,int id){return new Icon(2,null,pkg,id);}
    public static Icon createWithContentUri(String uri){return new Icon(4,null,uri,0);}
    public int getType(){return type;}public Bitmap getBitmap(){return bitmap;}public String getResPackage(){return pkg;}public int getResId(){return id;}
    public boolean sameAs(Icon other){return other!=null&&type==other.type&&bitmap==other.bitmap&&java.util.Objects.equals(pkg,other.pkg)&&id==other.id;}
}
