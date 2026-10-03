package android.widget;
public class ImageView extends android.view.View {
    private android.graphics.drawable.Drawable drawable;
    private final android.graphics.Matrix imageMatrix=new android.graphics.Matrix();
    public ImageView(android.content.Context context){super(context);}
    public android.graphics.drawable.Drawable getDrawable(){return drawable;}
    public void setImageDrawable(android.graphics.drawable.Drawable drawable){this.drawable=drawable;}
    public android.graphics.Matrix getImageMatrix(){return imageMatrix;}
    public void setImageMatrix(android.graphics.Matrix matrix){if(matrix==null)imageMatrix.reset();else imageMatrix.set(matrix);}
}
