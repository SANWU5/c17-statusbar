package com.android.systemui.statusbar.notification.collection;
import java.util.ArrayList;
import java.util.List;
/** Actual Android 17 BundleEntry extends PipelineEntry, not ListEntry. */
public final class BundleEntry {
    public final List<ListEntry> children=new ArrayList<>();
    public int reads;
    public List<ListEntry> getChildren(){reads++;return children;}
}
