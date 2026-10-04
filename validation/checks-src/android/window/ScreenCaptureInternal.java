package android.window;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.view.SurfaceControl;
import java.util.ArrayList;
import java.util.List;

/** Audited wallpaper subtree capture API; records strict scope rather than capturing pixels. */
public class ScreenCaptureInternal {
    public static final List<LayerCaptureArgs> capturesForCheck=new ArrayList<>();
    public static final List<ScreenshotHardwareBuffer> resultsForCheck=new ArrayList<>();
    public static boolean returnNullForCheck,secureForCheck,hdrForCheck,softwareForCheck,nullBitmapForCheck;
    public static int widthDeltaForCheck,heightDeltaForCheck;
    public static int invalidCaptureAttemptsForCheck;
    public static ScreenshotHardwareBuffer captureLayers(LayerCaptureArgs args) {
        capturesForCheck.add(args);
        if(args.parent==null||args.parent.owned||!args.parent.valid||!args.childrenOnly||args.uid!=1000
                ||args.excluded==null||args.excluded.length!=1||args.excluded[0]==null
                ||!args.excluded[0].owned||args.excluded[0].parent!=args.parent||args.excluded[0].kind!=0
                ||args.crop==null||args.crop.left!=0||args.crop.top!=0||args.crop.width()<=0||args.crop.height()<=0) {
            invalidCaptureAttemptsForCheck++;throw new AssertionError("Wallpaper scope/UID/owned exclusion is mandatory");
        }
        if(returnNullForCheck)return null;
        ScreenshotHardwareBuffer result=new ScreenshotHardwareBuffer(args.crop.width()+widthDeltaForCheck,args.crop.height()+heightDeltaForCheck);
        resultsForCheck.add(result);return result;
    }
    public static void resetForCheck() {
        capturesForCheck.clear();resultsForCheck.clear();HardwareBuffer.createdForCheck.clear();
        returnNullForCheck=secureForCheck=hdrForCheck=softwareForCheck=nullBitmapForCheck=false;invalidCaptureAttemptsForCheck=0;
        widthDeltaForCheck=heightDeltaForCheck=0;
    }
    public static class LayerCaptureArgs {
        public SurfaceControl parent;
        public SurfaceControl[] excluded;
        public Rect crop;
        public boolean childrenOnly;
        public long uid=-1;
        public static class Builder {
            private final LayerCaptureArgs args=new LayerCaptureArgs();
            public Builder(SurfaceControl parent){args.parent=parent;}
            public Builder setChildrenOnly(boolean value){args.childrenOnly=value;return this;}
            public Builder setExcludeLayers(SurfaceControl[] value){args.excluded=value.clone();return this;}
            public Builder setSourceCrop(Rect value){args.crop=new Rect(value);return this;}
            public Builder setUid(long value){args.uid=value;return this;}
            public LayerCaptureArgs build(){return args;}
        }
    }
    public static class ScreenshotHardwareBuffer {
        public final HardwareBuffer hardwareBuffer=new HardwareBuffer();
        public Bitmap bitmap;
        private final int width,height;
        ScreenshotHardwareBuffer(int width,int height) {
            this.width=width;this.height=height;
        }
        public Bitmap asBitmap(){
            if(bitmap==null&&!nullBitmapForCheck)bitmap=Bitmap.createBitmap(width,height,softwareForCheck?Bitmap.Config.ARGB_8888:Bitmap.Config.HARDWARE);
            return bitmap;
        }
        public HardwareBuffer getHardwareBuffer(){return hardwareBuffer;}
        public boolean containsSecureLayers(){return secureForCheck;}
        public boolean containsHdrLayers(){return hdrForCheck;}
    }
}
