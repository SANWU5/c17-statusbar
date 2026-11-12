package android.graphics;
public class Rect {
    public boolean isEmpty(){return left>=right||top>=bottom;}
    public boolean contains(int x,int y){return left<right&&top<bottom&&x>=left&&x<right&&y>=top&&y<bottom;}
    public void set(int left,int top,int right,int bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
    public void set(Rect value){set(value.left,value.top,value.right,value.bottom);}
    public void setEmpty(){left=top=right=bottom=0;}
    public boolean intersect(Rect other){
        if(left<other.right&&other.left<right&&top<other.bottom&&other.top<bottom){
            left=Math.max(left,other.left);top=Math.max(top,other.top);
            right=Math.min(right,other.right);bottom=Math.min(bottom,other.bottom);return true;
        }
        return false;
    }
    public void offset(int dx,int dy){left+=dx;right+=dx;top+=dy;bottom+=dy;}
    public void union(Rect other){
        if(other.isEmpty())return;
        if(isEmpty()){set(other.left,other.top,other.right,other.bottom);return;}
        left=Math.min(left,other.left);top=Math.min(top,other.top);
        right=Math.max(right,other.right);bottom=Math.max(bottom,other.bottom);
    }
    public Rect() { }
    public Rect(Rect other) {this(other.left,other.top,other.right,other.bottom);}
    public Rect(int left,int top,int right,int bottom) {this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
    public int left, top, right, bottom;
    public int width() { return right-left; }
    public int height() { return bottom-top; }
    public float exactCenterX() { return (left + right) / 2f; }
    public float exactCenterY() { return (top + bottom) / 2f; }
    @Override public boolean equals(Object value){if(!(value instanceof Rect))return false;Rect other=(Rect)value;return left==other.left&&top==other.top&&right==other.right&&bottom==other.bottom;}
    @Override public int hashCode(){return ((left*31+top)*31+right)*31+bottom;}
}
