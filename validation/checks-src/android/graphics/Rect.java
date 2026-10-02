package android.graphics;
public class Rect {
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
