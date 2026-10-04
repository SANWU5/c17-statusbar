package android.view;

import android.graphics.Canvas;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/** Owned GPU canvas contracts; never creates a software canvas or touches native handles. */
public class Surface {
    public static final List<Surface> createdForCheck=new ArrayList<>();
    public static boolean failLockForCheck,failPostForCheck,softwareCanvasForCheck;
    public final SurfaceControl control;
    public int lockCalls,postCalls,releaseCalls;
    public Canvas canvas;
    public Surface(SurfaceControl control) {
        if(control==null||!control.owned||control.kind!=3)throw new AssertionError("Canvas Surface must wrap our buffer");
        this.control=control;createdForCheck.add(this);
    }
    public Canvas lockHardwareCanvas() {
        lockCalls++;if(failLockForCheck)throw new IllegalStateException("GPU canvas unavailable");
        canvas=new Canvas();canvas.hardware=!softwareCanvasForCheck;
        canvas.clipBounds=new Rect(0,0,control.bufferWidth,control.bufferHeight);return canvas;
    }
    public void unlockCanvasAndPost(Canvas value) {
        if(value!=canvas)throw new AssertionError("Post must use the locked GPU canvas");
        postCalls++;if(failPostForCheck)throw new IllegalStateException("GPU post unavailable");
    }
    public void release(){releaseCalls++;}
    public static void resetForCheck(){createdForCheck.clear();failLockForCheck=failPostForCheck=softwareCanvasForCheck=false;}
}
