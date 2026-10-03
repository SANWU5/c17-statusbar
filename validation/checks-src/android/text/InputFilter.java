package android.text;
public interface InputFilter {
    final class LengthFilter implements InputFilter {
        public final int length;
        public LengthFilter(int length){this.length=length;}
    }
}
