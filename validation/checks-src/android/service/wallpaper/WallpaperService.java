package android.service.wallpaper;

import android.content.Context;
import android.graphics.Rect;
import android.view.SurfaceControl;

/** Audited Engine field names; the fixture owns native handles independently of the helper. */
public class WallpaperService {
    public static class Engine {
        public SurfaceControl mSurfaceControl,mTransformSurfaceControl,mScreenshotSurfaceControl,mBbqSurfaceControl;
        public boolean mDestroyed,preview,visible=true,mVisible=true,zoomOut;
        public int wallpaperFlags=2,mCurWidth=1440,mCurHeight=3168,mWidth=1440,mHeight=3168;
        public final Rect mSurfaceSize=new Rect(0,0,1440,3168),mSurfaceInsets=new Rect();
        public ClientWindowFrames mWinFrames=new ClientWindowFrames();
        public android.view.WindowManager.LayoutParams mLayout=new android.view.WindowManager.LayoutParams(1440,3168);
        public Context displayContext;
        public Engine(Context context) {displayContext=context;}
        public int getWallpaperFlags() {return wallpaperFlags;}
        public boolean isPreview() {return preview;}
        public boolean isVisible() {return visible;}
        public boolean shouldZoomOutWallpaper() {return zoomOut;}
        public Context getDisplayContext() {return displayContext;}
    }
    public static final class ClientWindowFrames {
        public final Rect frame=new Rect(0,0,1440,3168);
    }
}
