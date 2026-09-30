package android.graphics;
public class RenderEffect {
    public static boolean fail;
    public final float radius;
    public final Shader.TileMode tileMode;
    private RenderEffect(float radius,Shader.TileMode mode) {this.radius=radius;tileMode=mode;}
    public static RenderEffect createBlurEffect(float x,float y,Shader.TileMode mode) {
        if(fail)throw new IllegalStateException("blur unavailable");return new RenderEffect(x,mode);
    }
}
