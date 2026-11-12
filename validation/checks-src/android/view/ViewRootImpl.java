package android.view;
public class ViewRootImpl {
    public SurfaceControl surfaceForCheck;
    public SurfaceControl getSurfaceControl(){return surfaceForCheck;}
    public long lastMergedFrameForCheck = -1;
    public void mergeWithNextTransaction(SurfaceControl.Transaction transaction,long frame){lastMergedFrameForCheck=frame;transaction.apply();}
    public void applyTransactionOnDraw(SurfaceControl.Transaction transaction){transaction.apply();}
    public android.window.InputTransferToken inputToken=new android.window.InputTransferToken();
    public android.window.InputTransferToken getInputTransferToken(){return inputToken;}
    /** The platform's window background blur region (hidden API): what a capsule asks for when its blur is on. */
    public static final class BackgroundBlurDrawable extends android.graphics.drawable.Drawable {
        public int blurRadius = -1; public float cornerRadius = -1f; public int draws, maskColor;
        public void setColor(int color) { maskColor = color; }
        public void setBlurRadius(int radius) { blurRadius = radius; }
        public void setCornerRadius(float radius) { cornerRadius = radius; }
        @Override public void draw(android.graphics.Canvas canvas) { draws++; }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return 0; }
    }
    public boolean blurUnavailable;
    public BackgroundBlurDrawable lastBlur;
    public BackgroundBlurDrawable createBackgroundBlurDrawable() { return lastBlur = new BackgroundBlurDrawable(); }
}
