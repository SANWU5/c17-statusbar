package android.graphics;
public class LinearGradient extends Shader {
    public final float left,right,top,bottom;
    public final TileMode tileMode;
    public final int[] colors;
    public final float[] positions;
    public LinearGradient(float x0,float y0,float x1,float y1,int[] colors,float[] positions,TileMode mode) {
        left=x0;right=x1;top=y0;bottom=y1;tileMode=mode;this.colors=colors.clone();this.positions=positions.clone();
        for(int i=1;i<positions.length;i++)if(positions[i]<positions[i-1])throw new IllegalArgumentException("unordered gradient");
    }
    @Override public float alpha(float x,float y) {
        float dx=right-left,dy=bottom-top;
        float t=((x-left)*dx+(y-top)*dy)/(dx*dx+dy*dy);
        if(t<=0)return (colors[0]>>>24)/255f;
        for(int i=1;i<positions.length;i++)if(t<positions[i]) {
            float p=(t-positions[i-1])/(positions[i]-positions[i-1]);
            return ((colors[i-1]>>>24)+(float)((colors[i]>>>24)-(colors[i-1]>>>24))*p)/255f;
        }
        return (colors[colors.length-1]>>>24)/255f;
    }
}
