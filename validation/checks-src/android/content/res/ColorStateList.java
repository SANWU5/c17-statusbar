package android.content.res;
public class ColorStateList {
    private final int color;
    private ColorStateList(int color){this.color=color;}
    public static ColorStateList valueOf(int color){return new ColorStateList(color);}
    public int getDefaultColor(){return color;}
    public int getColorForState(int[] state,int fallback){return color;}
}
