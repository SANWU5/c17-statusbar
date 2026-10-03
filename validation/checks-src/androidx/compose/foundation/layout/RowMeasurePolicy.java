package androidx.compose.foundation.layout;
import androidx.compose.ui.layout.MeasurePolicy;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.MeasurePassDelegate;
import androidx.compose.ui.node.LookaheadPassDelegate;
import java.util.List;
/** Mirrors native Row's width/RTL placement; child modifiers remain attached to the actual node. */
public class RowMeasurePolicy implements MeasurePolicy {
    public Object lastScope;
    public long lastConstraints;
    public List<?> lastChildren;
    private static LayoutNode node(Object child) { return child instanceof MeasurePassDelegate ? ((MeasurePassDelegate)child).getLayoutNode() : ((LookaheadPassDelegate)child).nativeNode(); }
    public java.util.Map<String,Integer> measure(Object scope,List<?> children,long constraints) {
        lastScope=scope;lastConstraints=constraints;lastChildren=children;
        java.util.Map<String,Integer> result=new java.util.LinkedHashMap<>();
        int x=0,total=0;for(Object child:children)total+=node(child).width;
        for(Object child:children){LayoutNode node=node(child);result.put(node.name,Boolean.TRUE.equals(scope)?total-x-node.width:x);x+=node.width;}
        return result;
    }
    public int minIntrinsicWidth(Object scope,List<?> children,int height) {int total=0;for(Object child:children)total+=node(child).width;return total;}
}
