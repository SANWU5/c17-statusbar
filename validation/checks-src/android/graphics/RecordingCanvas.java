package android.graphics;
public class RecordingCanvas extends Canvas {
    public RecordingCanvas(int width,int height) {hardware=true;clipBounds=new Rect(0,0,width,height);}
}
