package android.view;
public final class ViewConfiguration {
    public static ViewConfiguration get(android.content.Context context){return new ViewConfiguration();}
    public int getScaledTouchSlop(){return 8;}
    public static int getLongPressTimeout(){return 500;}
}
