package android.view;
import android.content.Context;
public class ViewGroup extends View {
    public static class LayoutParams {public int width,height;public LayoutParams(int width,int height){this.width=width;this.height=height;}}
    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin,topMargin,rightMargin,bottomMargin;
        private int start=Integer.MIN_VALUE,end=Integer.MIN_VALUE;
        public MarginLayoutParams(int width,int height){super(width,height);}
        public MarginLayoutParams(MarginLayoutParams source){super(source.width,source.height);leftMargin=source.leftMargin;topMargin=source.topMargin;rightMargin=source.rightMargin;bottomMargin=source.bottomMargin;start=source.start;end=source.end;}
        public int getMarginStart(){return start==Integer.MIN_VALUE?leftMargin:start;}
        public int getMarginEnd(){return end==Integer.MIN_VALUE?rightMargin:end;}
        public void setMarginStart(int value){start=value;}
        public void setMarginEnd(int value){end=value;}
        public boolean isMarginRelative(){return start!=Integer.MIN_VALUE||end!=Integer.MIN_VALUE;}
    }
    private boolean clipChildren=true,clipPadding=true;
    private final java.util.List<View> children=new java.util.ArrayList<>();
    public ViewGroup(Context context) { super(context); }
    public boolean getClipChildren() {return clipChildren;}
    public boolean getClipToPadding() {return clipPadding;}
    public void setClipChildren(boolean value) {clipChildren=value;}
    public void setClipToPadding(boolean value) {clipPadding=value;}
    public void addView(View view) {children.add(view);view.parent=this;}
    public void removeView(View view) {if(children.remove(view)&&view.parent==this)view.parent=null;}
    public int getChildCount() {return children.size();}
    public View getChildAt(int position) {return children.get(position);}
}
