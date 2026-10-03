package androidx.compose.ui.layout;
import java.util.List;
public interface MeasurePolicy {
    java.util.Map<String,Integer> measure(Object scope, List<?> children, long constraints);
    int minIntrinsicWidth(Object scope, List<?> children, int height);
}
