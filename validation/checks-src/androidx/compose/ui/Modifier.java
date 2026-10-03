package androidx.compose.ui;
import kotlin.jvm.functions.Function2;
public interface Modifier {
    Modifier Companion = new Empty();
    Object foldIn(Object initial, Function2 visitor);
    default Modifier then(Modifier other) { return this == Companion ? other : other == Companion ? this : new Combined(this,other); }
    final class Empty implements Modifier {
        public Object foldIn(Object initial,Function2 visitor) { return initial; }
    }
    final class Combined implements Modifier {
        public final Modifier outer,inner;
        Combined(Modifier outer,Modifier inner) { this.outer=outer;this.inner=inner; }
        public Object foldIn(Object initial,Function2 visitor) { return inner.foldIn(outer.foldIn(initial,visitor),visitor); }
    }
    class Element implements Modifier {
        public int reads;
        public Object foldIn(Object initial,Function2 visitor) { reads++;return visitor.invoke(initial,this); }
    }
}
