package com.oplus.systemui.qs.base.widget.recyclerview;
import android.view.View;
/** Exact native inner pager fields; currScrollOffset intentionally exposes its allocation. */
public final class StaggeredPagerLayoutManager {
    private View recyclerView;
    private int pageCount = 2;
    private final LayoutState layoutState = new LayoutState();
    public boolean horizontal = true, fail;
    public int offsetArrays;
    public void setRecyclerView(View view) { recyclerView = view; }
    public void setPageCount(int count) { pageCount = count; }
    public void nativeScrollTo(int offset) { layoutState.scrollX = offset; }
    public boolean canScrollHorizontally() {
        if (fail) throw new IllegalStateException("native horizontal unavailable");
        return horizontal;
    }
    public int[] currScrollOffset() { offsetArrays++; return new int[]{layoutState.scrollX, 0}; }
    public void onPageScrolled() { }
    public void onScrollStateChanged(int state) { layoutState.scrollState = state; }
    public static final class LayoutState { private int scrollX, scrollState; }
}
