// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.ActionMode;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputContentInfo;
import android.widget.EditText;

/** Blocks explicit clipboard/drop entry points; ordinary multi-character IME commits remain valid. */
public final class ManualConfirmationEditText extends EditText {
    public ManualConfirmationEditText(Context context) {
        super(context);
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        setFilters(new InputFilter[] { new InputFilter.LengthFilter(160) });
        setMinLines(2); setMaxLines(4);
        setLongClickable(false);
        setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        ActionMode.Callback noMenu = new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) { return false; }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return true; }
            @Override public void onDestroyActionMode(ActionMode mode) { }
        };
        setCustomSelectionActionModeCallback(noMenu);
        setCustomInsertionActionModeCallback(noMenu);
    }
    @Override public boolean onTextContextMenuItem(int action) {
        return FreeNotice.blocksContextMenuAction(action) || super.onTextContextMenuItem(action);
    }
    @Override public boolean onKeyShortcut(int key, KeyEvent event) {
        return FreeNotice.blocksClipboardShortcut(key, event.getMetaState()) || super.onKeyShortcut(key, event);
    }
    @Override public boolean onKeyDown(int key, KeyEvent event) {
        return FreeNotice.blocksClipboardShortcut(key, event.getMetaState()) || super.onKeyDown(key, event);
    }
    @Override public boolean onDragEvent(DragEvent event) { return true; }
    @Override public void autofill(AutofillValue value) { /* No external fill for the acknowledgement. */ }
    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_PASTE || action == AccessibilityNodeInfo.ACTION_COPY
                || action == AccessibilityNodeInfo.ACTION_CUT) return false;
        return super.performAccessibilityAction(action, arguments);
    }
    @Override public InputConnection onCreateInputConnection(EditorInfo attributes) {
        InputConnection nativeConnection = super.onCreateInputConnection(attributes);
        if (nativeConnection == null) return null;
        return new InputConnectionWrapper(nativeConnection, false) {
            @Override public boolean performContextMenuAction(int action) {
                return FreeNotice.blocksContextMenuAction(action) || super.performContextMenuAction(action);
            }
            @Override public boolean commitContent(InputContentInfo content, int flags, Bundle options) { return false; }
            // Do not reject commitText/setComposingText based on length or presumed origin:
            // Chinese IMEs legitimately commit whole words and sentences through these APIs.
        };
    }
}
