package android.widget;
import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.ActionMode;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
/** Only the native editor entry points needed for the confirmation field's real overrides. */
public class EditText extends TextView {
    public ActionMode.Callback selectionCallback,insertionCallback;
    public InputConnection inputConnection;
    public int nativeMenus,nativeShortcuts,nativeKeyDowns,nativeDrops,nativeAutofill,nativeAccessibility;
    public boolean longClickable=true;
    public EditText(Context context){super(context);}
    public void setInputType(int type){ }
    public void setImeOptions(int options){ }
    public void setFilters(InputFilter[] filters){ }
    public void setMinLines(int value){ }
    public void setMaxLines(int value){ }
    public void setLongClickable(boolean value){longClickable=value;}
    public void setImportantForAutofill(int value){ }
    public void setCustomSelectionActionModeCallback(ActionMode.Callback callback){selectionCallback=callback;}
    public void setCustomInsertionActionModeCallback(ActionMode.Callback callback){insertionCallback=callback;}
    public boolean onTextContextMenuItem(int action){nativeMenus++;return false;}
    public boolean onKeyShortcut(int key,KeyEvent event){nativeShortcuts++;return false;}
    public boolean onKeyDown(int key,KeyEvent event){nativeKeyDowns++;return false;}
    public boolean onDragEvent(DragEvent event){nativeDrops++;return false;}
    public void autofill(AutofillValue value){nativeAutofill++;}
    public boolean performAccessibilityAction(int action,Bundle arguments){nativeAccessibility++;return false;}
    public InputConnection onCreateInputConnection(EditorInfo attributes){return inputConnection;}
}
