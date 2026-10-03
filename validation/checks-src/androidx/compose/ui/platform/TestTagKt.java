package androidx.compose.ui.platform;
import androidx.compose.ui.Modifier;
public final class TestTagKt {
    public static Modifier testTag(Modifier base,String name) { return base.then(new TestTagElement(name)); }
    public static final class TestTagElement extends Modifier.Element {
        public final String name;
        TestTagElement(String name) { this.name=name; }
    }
}
