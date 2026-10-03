package android.view.inputmethod;
import android.os.Bundle;
public class InputConnectionWrapper implements InputConnection {
    private final InputConnection target;
    public InputConnectionWrapper(InputConnection target,boolean mutable){this.target=target;}
    public boolean performContextMenuAction(int action){return target.performContextMenuAction(action);}
    public boolean commitContent(InputContentInfo content,int flags,Bundle options){return target.commitContent(content,flags,options);}
    public boolean commitText(CharSequence text,int cursor){return target.commitText(text,cursor);}
    public boolean setComposingText(CharSequence text,int cursor){return target.setComposingText(text,cursor);}
}
