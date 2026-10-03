package com.android.systemui.statusbar.notification.row;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.ViewGroup;
import com.oplus.systemui.notification.material.NotificationSpotLightDelegate.ClipSpec;
/** Geometry copied from actual getClipPath/updateClipping/getSpotLightClipSpec DEX. */
public class ExpandableNotificationRow extends ViewGroup {
    public static final Path EMPTY_PATH=new Path();
    public int mClipTopAmount,mClipBottomAmount,mTopOverlap,mBottomOverlap;
    public boolean mHasCustomOutline,mAlwaysRoundBothCorners=true;
    public final Rect mOutlineRect=new Rect();
    public final Extension mExpandableViewEx=new Extension();
    public final OutlineExtension mOutlineViewEx=new OutlineExtension();
    public final Object mBackgroundNormal=null;
    public final NativeState state=new NativeState();
    public boolean interaction,removed,dismissed;
    public com.android.systemui.statusbar.notification.collection.NotificationEntry entry=
            new com.android.systemui.statusbar.notification.collection.NotificationEntry(0);
    public java.util.List<ExpandableNotificationRow> attachedChildren;
    public int nativeWidth=328,minimumHeight;
    public ExpandableNotificationRow(Context context,int height,float y,int order){super(context);state.height=height;state.yTranslation=y;state.notGoneIndex=order;}
    @Override public int getWidth(){return nativeWidth;}
    @Override public int getHeight(){return state.height;}
    public NativeState getViewState(){return state;}
    public com.android.systemui.statusbar.notification.collection.NotificationEntry getEntry(){return entry;}
    public java.util.List<ExpandableNotificationRow> getAttachedChildren(){return attachedChildren;}
    public int getActualHeight(){return state.height;}
    public int getClipTopAmount(){return mClipTopAmount;}
    public int getClipBottomAmount(){return mClipBottomAmount;}
    public int getMinimumHeightForClipping(){return minimumHeight;}
    public float getMaxRadius(){return 28f;}
    public float getTopCornerRadius(){return 28f;}
    public float getBottomCornerRadius(){return 28f;}
    public boolean isChildInGroup(){return false;}
    public boolean isRemoved(){return removed;}
    public boolean isDismissed(){return dismissed;}
    public boolean areGutsExposed(){return interaction;}
    public boolean isUserLocked(){return false;}
    public boolean isExpandAnimationRunning(){return false;}
    public boolean isGroupExpansionChanging(){return false;}
    public void applyNative(){mClipTopAmount=state.clipTopAmount;mClipBottomAmount=state.clipBottomAmount;setTranslationY(state.yTranslation);setClipBounds(new Rect(0,mClipTopAmount,nativeWidth,Math.max(mClipTopAmount,state.height-Math.max(mClipBottomAmount,mExpandableViewEx.cut))));}
    public void getRoundedRectPath(int l,int t,int r,int b,float tr,float br,Path path){path.reset();path.addRoundRect(new RectF(l,t,r,b),tr,br,Path.Direction.CW);}
    public Path getClipPath(boolean ignored){
        int top=Math.max(mClipTopAmount,mTopOverlap);
        int bottom=Math.max(minimumHeight,Math.max(state.height-mClipBottomAmount-mExpandableViewEx.cut,(int)(top+getMaxRadius())));
        Path result=new Path();getRoundedRectPath(0,top,nativeWidth,bottom,28f,28f,result);
        mOutlineViewEx.syncClipCanvasToBgView(28f,0,top,28f,nativeWidth,bottom);return result;
    }
    public ClipSpec getSpotLightClipSpec(){
        int top=Math.max(mClipTopAmount,mTopOverlap);
        int bottom=Math.max(minimumHeight,Math.max(state.height-mClipBottomAmount-mExpandableViewEx.cut,(int)(top+getMaxRadius())));
        return new ClipSpec(0,top,nativeWidth,bottom,28f,28f,true);
    }
    public static final class Extension {public int cut;public int getActualClipHeight(){return cut;}}
    public static final class OutlineExtension {
        public int left,top,right,bottom,writes;
        public float topRadius,bottomRadius;
        public void syncClipCanvasToBgView(float tr,int l,int t,float br,int r,int b){
            if(tr==topRadius&&l==left&&t==top&&br==bottomRadius&&r==right&&b==bottom)return;
            left=l;top=t;right=r;bottom=b;topRadius=tr;bottomRadius=br;writes++;
        }
    }
    public static final class NativeState {
        public float yTranslation,alpha=1f;
        public int height,clipTopAmount,clipBottomAmount,notGoneIndex;
        public boolean hidden,gone,inShelf;
    }
}
