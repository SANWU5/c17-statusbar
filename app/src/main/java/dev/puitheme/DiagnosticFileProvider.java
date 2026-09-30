package dev.puitheme;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Only exact, temporary read grants can expose immutable diagnostic snapshots. */
public final class DiagnosticFileProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.puitheme.iosstatusbar.diagnostics";
    @Override public boolean onCreate() { return true; }

    private File readable(Uri uri) throws FileNotFoundException {
        if (Binder.getCallingUid() != Process.myUid() && getContext().checkUriPermission(uri,
                Binder.getCallingPid(), Binder.getCallingUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) throw new SecurityException("No diagnostic snapshot read grant");
        return ModuleDiagnostics.sharedFile(getContext(), uri);
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Diagnostic snapshots are read-only");
        return ParcelFileDescriptor.open(readable(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        try { readable(uri); return "text/plain"; }
        catch (FileNotFoundException unavailable) { return null; }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        try {
            File file = readable(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns, 1);
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = "C17-诊断日志.txt";
                if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
            }
            cursor.addRow(values);return cursor;
        } catch (FileNotFoundException unavailable) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read-only snapshots"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException("Read-only snapshots"); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException("Read-only snapshots"); }
}
