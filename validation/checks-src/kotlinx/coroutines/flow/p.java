package kotlinx.coroutines.flow;
public final class p implements com.oplus.systemui.plugins.r8.Lg {
    public Object value;
    public int reads;
    public p(Object value) { this.value = value; }
    public Object getValue() { reads++; return value; }
}
