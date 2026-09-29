package android.view;
import android.content.Context;
public class ViewGroup extends View {
    private boolean clipChildren=true,clipPadding=true;
    private final java.util.List<View> children=new java.util.ArrayList<>();
    public ViewGroup(Context context) { super(context); }
    public boolean getClipChildren() {return clipChildren;}
    public boolean getClipToPadding() {return clipPadding;}
    public void setClipChildren(boolean value) {clipChildren=value;}
    public void setClipToPadding(boolean value) {clipPadding=value;}
    public void addView(View view) {children.add(view);view.parent=this;}
    public int getChildCount() {return children.size();}
    public View getChildAt(int position) {return children.get(position);}
}
