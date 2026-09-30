package com.oplus.systemui.qs.base.widget.recyclerview;
import android.content.Context;
import android.content.res.Resources;
import android.view.ViewGroup;
public class ParallelCOUIRecyclerView extends ViewGroup {
    public int width=1440,height=1600,scrollX,scrollY,paddingLeft=140,paddingRight=140,paddingTop,paddingBottom;
    public String resourceName="other_tiles_container";
    public int measuredWidth;
    public boolean overflow,scrollOverflow;
    public boolean managerPresent;
    public Object nativeManager;
    public int nativeScrollState;
    private final java.util.Map<Integer,Object> nativeTags=new java.util.HashMap<>();
    private final Resources resources=new Resources() {
        @Override public String getResourceEntryName(int id) {return resourceName;}
        @Override public int getIdentifier(String name,String type,String pkg) {return name.equals("tag_should_handle_ignore_parent_bounds")?1:name.equals("tag_should_handle_ignore_parent_bounds_scroll")?2:name.equals("tag_view_ignore_parent_bounds")?3:name.equals("tag_should_clip_split_parent_bounds")?4:0;}
    };
    public ParallelCOUIRecyclerView() {super(new Context());}
    @Override public int getWidth() {return width;}
    @Override public int getMeasuredWidth() {return measuredWidth==0?width:measuredWidth;}
    @Override public Object getTag(int key) {return nativeTags.containsKey(key)?nativeTags.get(key):key==1?overflow:key==2?scrollOverflow:null;}
    @Override public void setTag(int key,Object value) {nativeTags.put(key,value);}
    public Object getLayoutManager() {if(!managerPresent)throw new UnsupportedOperationException("callback-only fixture");return nativeManager;}
    public int getScrollState() {return nativeScrollState;}
    @Override public int getHeight() {return height;}
    @Override public int getScrollX() {return scrollX;}
    @Override public int getScrollY() {return scrollY;}
    @Override public int getPaddingLeft() {return paddingLeft;}
    @Override public int getPaddingRight() {return paddingRight;}
    @Override public int getPaddingTop() {return paddingTop;}
    @Override public int getPaddingBottom() {return paddingBottom;}
    @Override public Resources getResources() {return resources;}
}
