package android.graphics;
public class RenderNode {
    public static final java.util.List<RenderNode> nodes=new java.util.ArrayList<>();
    public static boolean failBegin;
    public static boolean failEndOnce;
    public static String failBeginOnce;
    public final String name;
    public int width,height,left,top,recordings,discards,ends;
    public boolean clipToBounds;
    public float alpha=1f;
    public int alphaWrites;
    public boolean setAlpha(float value){alphaWrites++;alpha=value;return true;}
    public RecordingCanvas recording;
    private PositionUpdateListener positionListener;
    public interface PositionUpdateListener {
        void positionChanged(long frame, int left, int top, int right, int bottom);
        void positionChangedF(long frame, float left, float top, float right, float bottom,
                              int clipLeft, int clipTop, int clipRight, int clipBottom,
                              int nodeWidth, int nodeHeight, boolean clipUnchanged);
        void positionLost(long frame);
    }
    public void addPositionUpdateListener(PositionUpdateListener listener) { positionListener=listener; }
    public void removePositionUpdateListener(PositionUpdateListener listener) { if(positionListener==listener)positionListener=null; }
    public void dispatchPositionForCheck(long frame) {
        if (positionListener != null) positionListener.positionChanged(frame, left, top, left + width, top + height);
    }
    public void dispatchPositionForCheck(long frame, float left, float top, float right, float bottom, Rect clip) {
        if (positionListener == null) return;
        Rect localClip = clip == null ? new Rect(0, 0, width, height) : clip;
        positionListener.positionChangedF(frame, left, top, right, bottom, localClip.left, localClip.top,
                localClip.right, localClip.bottom, width, height, clip == null);
    }
    public void dispatchPositionLostForCheck(long frame) {
        if (positionListener != null) positionListener.positionLost(frame);
    }
    public RenderEffect effect;
    public RenderEffect backgroundEffect;
    public static boolean rejectBackground;
    public int backgroundWrites;
    public boolean setBackgroundRenderEffect(RenderEffect value){backgroundWrites++;if(rejectBackground)return false;backgroundEffect=value;return true;}
    public RenderNode(String name) {this.name=name;nodes.add(this);}
    public boolean setPosition(int l,int t,int r,int b) {left=l;top=t;width=r-l;height=b-t;return true;}
    public boolean setClipToBounds(boolean value) {clipToBounds=value;return true;}
    public boolean setRenderEffect(RenderEffect effect) {this.effect=effect;return true;}
    public RecordingCanvas beginRecording(int w,int h) {
        if(failBegin)throw new IllegalStateException("recording unavailable");
        if(failBeginOnce!=null&&name.contains(failBeginOnce)){failBeginOnce=null;throw new IllegalStateException("recording unavailable");}
        recordings++;recording=new RecordingCanvas(w,h);recording.mNode=this;return recording;
    }
    public void endRecording() {ends++;if(failEndOnce){failEndOnce=false;throw new IllegalStateException("end recording unavailable");}}
    public void discardDisplayList() {discards++;}
}
