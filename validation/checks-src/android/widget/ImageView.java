package android.widget;
public class ImageView extends android.view.View {
    private android.graphics.drawable.Drawable drawable;
    public ImageView(android.content.Context context){super(context);}
    public android.graphics.drawable.Drawable getDrawable(){return drawable;}
    public void setImageDrawable(android.graphics.drawable.Drawable drawable){this.drawable=drawable;}
}
