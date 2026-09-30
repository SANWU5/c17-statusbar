package android.graphics;
public class ComposeShader extends Shader {
    public final Shader a,b;public final PorterDuff.Mode mode;
    public ComposeShader(Shader a,Shader b,PorterDuff.Mode mode){this.a=a;this.b=b;this.mode=mode;}
    @Override public float alpha(float x,float y){
        float first=a.alpha(x,y),second=b.alpha(x,y);
        if(mode==PorterDuff.Mode.SCREEN)return first+second-first*second;
        if(mode==PorterDuff.Mode.DST_IN)return first*second;
        throw new AssertionError("Unexpected alpha composition "+mode);
    }
}
