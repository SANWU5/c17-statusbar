package androidx.compose.ui.node;
public class LookaheadPassDelegate {
    private final LayoutNode node;
    public LookaheadPassDelegate(LayoutNode node) { this.node=node; }
    private final LayoutNode getLayoutNode() { return node; }
    public LayoutNode nativeNode() { return node; }
}
