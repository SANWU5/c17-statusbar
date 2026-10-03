package android.view;
public class ActionMode {
    public interface Callback {
        boolean onCreateActionMode(ActionMode mode,Menu menu);
        boolean onPrepareActionMode(ActionMode mode,Menu menu);
        boolean onActionItemClicked(ActionMode mode,MenuItem item);
        void onDestroyActionMode(ActionMode mode);
    }
}
