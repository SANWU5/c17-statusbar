package android.view;

/** Minimal existing framework contract, including the actual LayoutParams type used by Engine. */
public interface WindowManager {
    Display getDefaultDisplay();
    boolean isCrossWindowBlurEnabled();
    void addCrossWindowBlurEnabledListener(java.util.concurrent.Executor executor,java.util.function.Consumer<Boolean> listener);
    void removeCrossWindowBlurEnabledListener(java.util.function.Consumer<Boolean> listener);
    class LayoutParams extends ViewGroup.LayoutParams {
        public static final int FLAG_SCALED=0x4000,FLAG_SHOW_WALLPAPER=0x100000;
        public static final int SOFT_INPUT_STATE_ALWAYS_HIDDEN=3,SOFT_INPUT_STATE_ALWAYS_VISIBLE=5,SOFT_INPUT_ADJUST_RESIZE=16;
        public int flags;
        public final android.graphics.Rect surfaceInsets=new android.graphics.Rect();
        public LayoutParams() {super(MATCH_PARENT,MATCH_PARENT);}
        public LayoutParams(int width,int height) {super(width,height);}
    }
}
