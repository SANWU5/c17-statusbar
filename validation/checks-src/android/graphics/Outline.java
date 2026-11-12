package android.graphics;
/** Desktop fixture: the last outline a drawable described. */
public class Outline {
    public boolean empty = true;
    public Rect rect;
    public float radius, alpha = 1f;
    public void setEmpty() { empty = true; rect = null; }
    public void setRoundRect(Rect bounds, float cornerRadius) {
        empty = false; rect = new Rect(bounds); radius = cornerRadius;
    }
    public void setAlpha(float value) { alpha = value; }
    public boolean getRect(Rect target){if(empty||rect==null)return false;target.left=rect.left;target.top=rect.top;target.right=rect.right;target.bottom=rect.bottom;return true;}
    public float getRadius(){return radius;}
}
