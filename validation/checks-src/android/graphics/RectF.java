package android.graphics;
public class RectF {
    public float left,top,right,bottom;
    public RectF() { }
    public RectF(float left,float top,float right,float bottom) {this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
    public float width() {return right-left;}
    public boolean contains(float x,float y){return x>=left&&x<right&&y>=top&&y<bottom;}
    public float height() {return bottom-top;}
    public float centerX() {return (left+right)/2;}
    public float centerY() {return (top+bottom)/2;}
    public void set(float left,float top,float right,float bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
    public void inset(float x,float y){left+=x;right-=x;top+=y;bottom-=y;}
    public boolean intersect(float l,float t,float r,float b){
        if(left>=r||l>=right||top>=b||t>=bottom)return false;
        left=Math.max(left,l);top=Math.max(top,t);right=Math.min(right,r);bottom=Math.min(bottom,b);return true;
    }
    public void union(RectF other){
        left=Math.min(left,other.left);top=Math.min(top,other.top);right=Math.max(right,other.right);bottom=Math.max(bottom,other.bottom);
    }
    public void roundOut(Rect result){result.left=(int)Math.floor(left);result.top=(int)Math.floor(top);result.right=(int)Math.ceil(right);result.bottom=(int)Math.ceil(bottom);}
}
