package androidx.compose.ui.node;
import androidx.compose.ui.Modifier;
public class LayoutNode {
    public final String name;
    public final int width;
    public Modifier modifier;
    public LayoutNode(String name,int width) { this.name=name;this.width=width; }
    public void setModifier(Modifier modifier) { this.modifier=modifier; }
}
