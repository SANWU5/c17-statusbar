package com.android.systemui.wallpapers;

/** Real static-wallpaper class identity, without service rendering or bitmap capture. */
public class ImageWallpaper {
    public static class CanvasEngine extends android.service.wallpaper.WallpaperService.Engine {
        public CanvasEngine(android.content.Context context) {super(context);}
    }
}
