package android.widget;
public class ImageView extends android.view.View {
    public enum ScaleType { MATRIX,FIT_XY,FIT_START,FIT_CENTER,FIT_END,CENTER,CENTER_CROP,CENTER_INSIDE }
    private ScaleType scaleType=ScaleType.FIT_CENTER;
    public ScaleType getScaleType(){return scaleType;}
    public void setScaleType(ScaleType value){scaleType=value;}
    private android.graphics.drawable.Drawable mDrawable;
    private android.content.res.ColorStateList imageTint;
    private final android.graphics.Matrix imageMatrix=new android.graphics.Matrix();
    private int imageAlpha=255;
    public ImageView(android.content.Context context){super(context);}
    public android.graphics.drawable.Drawable getDrawable(){return mDrawable;}
    /** Like the framework: the old drawable is hidden, the new one gets this view as its callback and its visibility. */
    public void setImageDrawable(android.graphics.drawable.Drawable drawable){
        if(this.mDrawable!=null&&this.mDrawable!=drawable)this.mDrawable.setVisible(false,false);
        this.mDrawable=drawable;
        if(drawable!=null){drawable.setCallback(this);drawable.setVisible(isAttachedToWindow()&&shown,true);}
    }
    public android.content.res.ColorStateList getImageTintList(){return imageTint;}
    public void setImageTintList(android.content.res.ColorStateList value){imageTint=value;}
    public android.graphics.Matrix getImageMatrix(){return imageMatrix;}
    public void setImageMatrix(android.graphics.Matrix matrix){if(matrix==null)imageMatrix.reset();else imageMatrix.set(matrix);}
    public int getImageAlpha(){return imageAlpha;}
    public void setImageAlpha(int value){imageAlpha=value;}
    protected void onDraw(android.graphics.Canvas canvas){}
}
