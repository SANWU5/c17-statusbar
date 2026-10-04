package android.widget;
/** Minimal faithful container fixture; native LinearLayout is a ViewGroup. */
public class LinearLayout extends android.view.ViewGroup {
    public static final int HORIZONTAL=0,VERTICAL=1;
    private int orientation=HORIZONTAL;
    public int getOrientation(){return orientation;}
    public void setOrientation(int value){orientation=value;}
    public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {
        public float weight;
        public LayoutParams(int width,int height){super(width,height);}
        public LayoutParams(int width,int height,float weight){super(width,height);this.weight=weight;}
    }
    public LinearLayout(android.content.Context context){super(context);}
}
