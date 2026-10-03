package android.graphics;
/** Observable RGB/alpha transforms for wallpaper brightness checks. */
public final class ColorMatrix {
    public final float[] values;
    public ColorMatrix(float[] values){this.values=values.clone();}
    public float[] getArray(){return values;}
}
