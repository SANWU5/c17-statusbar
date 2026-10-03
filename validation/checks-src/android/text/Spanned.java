package android.text;
public interface Spanned extends CharSequence {
    int SPAN_EXCLUSIVE_EXCLUSIVE=33;
    <T>T[] getSpans(int start,int end,Class<T> type);
    int getSpanStart(Object span);
    int getSpanEnd(Object span);
    int getSpanFlags(Object span);
}
