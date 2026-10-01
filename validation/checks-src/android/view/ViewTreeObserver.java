package android.view;
/** Passive traversal fixture; tests explicitly inspect or dispatch the registered observers. */
public class ViewTreeObserver {
    public interface OnPreDrawListener { boolean onPreDraw(); }
    public final java.util.List<OnPreDrawListener> preDrawListeners = new java.util.ArrayList<>();
    public boolean alive = true;
    public boolean isAlive(){return alive;}
    public void addOnPreDrawListener(OnPreDrawListener listener){if(!preDrawListeners.contains(listener))preDrawListeners.add(listener);}
    public void removeOnPreDrawListener(OnPreDrawListener listener){preDrawListeners.remove(listener);}
}
