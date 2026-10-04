package android.view;
public class Display {
    public int displayId,rotation;
    public int realWidth=1440,realHeight=3168;
    public int getDisplayId(){return displayId;}
    public int getRotation(){return rotation;}
    public void getRealSize(android.graphics.Point size){size.x=realWidth;size.y=realHeight;}
}
