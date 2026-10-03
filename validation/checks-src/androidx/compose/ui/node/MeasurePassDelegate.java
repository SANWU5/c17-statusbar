package androidx.compose.ui.node;
public class MeasurePassDelegate {
    private final LayoutNode node;
    public MeasurePassDelegate(LayoutNode node) { this.node=node; }
    public final LayoutNode getLayoutNode() { return node; }
}
