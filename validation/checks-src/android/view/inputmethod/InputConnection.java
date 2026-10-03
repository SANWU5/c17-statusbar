package android.view.inputmethod;
import android.os.Bundle;
public interface InputConnection {
    boolean performContextMenuAction(int action);
    boolean commitContent(InputContentInfo content,int flags,Bundle options);
    boolean commitText(CharSequence text,int cursor);
    boolean setComposingText(CharSequence text,int cursor);
}
