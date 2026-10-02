package android.graphics;
public class BitmapShader extends Shader {
    public final Bitmap bitmap; public Matrix matrix;
    public BitmapShader(Bitmap bitmap,TileMode x,TileMode y){this.bitmap=bitmap;}
    public void setLocalMatrix(Matrix matrix){this.matrix=matrix;}
}
