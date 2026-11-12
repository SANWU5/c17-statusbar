package com.android.systemui.plugins;
/** Test-only native callbacks for the production data-only plugin bridge. */
public class C17PluginHost extends android.widget.TextView {
    public C17PluginHost() { super(null); }
    public void update() { }
    public void nested() { }
    public int count() { return 7; }
    public boolean enabled() { return false; }
    public long large() { return 0L; }
    public float ratio() { return 0f; }
    public char letter() { return 'x'; }
    public String object() { return "native"; }
    public void ambiguous(int value) { }
    public void ambiguous(String value) { }
    public native int nativeValue();
    public static void staticUpdate() { }
    public abstract static class Abstract extends android.view.View {
        public Abstract() { super(null); }
        public abstract int abstractValue();
    }
}
