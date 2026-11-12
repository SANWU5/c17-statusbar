package android.view.animation;
/** Desktop clock for animation checks; tests advance it explicitly. */
public class AnimationUtils {
    public static long nowForCheck;
    public static long currentAnimationTimeMillis(){return nowForCheck;}
}
