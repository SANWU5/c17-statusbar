package io.github.libxposed.service;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;

/** Synthetic service contract/counters; production is compiled against the official AAR. */
public final class XposedService {
    public static final long PROP_CAP_REMOTE=2L;
    public final SharedPreferences preferences;
    public long properties=PROP_CAP_REMOTE;
    public int requests;
    public boolean unavailable;
    public boolean filesUnavailable;
    private File files;
    public java.util.List<String> scopes=new java.util.ArrayList<>();
    public boolean scopeUnavailable;
    public int scopeRequests;
    public java.util.List<String> requestedScopes;
    public OnScopeEventListener scopeCallback;
    public interface OnScopeEventListener {
        default void onScopeRequestApproved(java.util.List<String> approved) { }
        default void onScopeRequestFailed(String message) { }
    }
    public void requestScope(java.util.List<String> packages,OnScopeEventListener callback) {
        if(scopeUnavailable)throw new IllegalStateException("synthetic scope unavailable");
        scopeRequests++;requestedScopes=new java.util.ArrayList<>(packages);scopeCallback=callback;
    }
    public XposedService(SharedPreferences preferences){this.preferences=preferences;}
    public long getFrameworkProperties(){return properties;}
    public SharedPreferences getRemotePreferences(String group){
        requests++;if(unavailable)throw new IllegalStateException("synthetic service unavailable");return preferences;
    }
    private File remoteDirectory() {
        if(filesUnavailable)throw new IllegalStateException("synthetic remote files unavailable");
        if(files==null)try {
            files=Files.createTempDirectory("c17-remote-").toFile();files.deleteOnExit();
        } catch(IOException failure){throw new IllegalStateException(failure);}
        return files;
    }
    public String[] listRemoteFiles(){return remoteDirectory().list();}
    public ParcelFileDescriptor openRemoteFile(String name){
        try {
            File file=new File(remoteDirectory(),name);file.deleteOnExit();
            return ParcelFileDescriptor.openReadWriteForCheck(file);
        } catch(IOException failure){throw new IllegalStateException(failure);}
    }
    public ParcelFileDescriptor openRemoteFileForCheck(String name)throws FileNotFoundException{
        return ParcelFileDescriptor.open(new File(remoteDirectory(),name),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public boolean deleteRemoteFile(String name){return new File(remoteDirectory(),name).delete();}
    public int getApiVersion(){return 102;}
    public java.util.List<String> getScope(){if(scopeUnavailable)throw new IllegalStateException("synthetic scope unavailable");return scopes;}
    public String getFrameworkName(){return "LSPosed model";}
    public String getFrameworkVersion(){return "synthetic";}
    public long getFrameworkVersionCode(){return 1L;}
}
