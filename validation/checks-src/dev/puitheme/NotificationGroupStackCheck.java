package dev.puitheme;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
/** Forced-off legacy snapshots and restoration of pre-upgrade native group ownership. */
public final class NotificationGroupStackCheck {
    private static int checks;
    private static void check(boolean yes){checks++;if(!yes)throw new AssertionError("Group stack "+checks);}
    public static final class Summary extends ExpandableNotificationRow {
        public Group mChildrenContainer;
        public View.OnClickListener mExpandClickListener;
        public boolean mOnKeyguard;
        public int clicks;
        Summary(Context c){super(c,100,0,0);mExpandClickListener=v->{clicks++;mChildrenContainer.mChildrenExpanded=!mChildrenContainer.mChildrenExpanded;};}
    }
    public interface ReceiptAdapter {android.service.notification.StatusBarNotification getSbn();}
    public interface NativeAdapter extends ReceiptAdapter {}
    public static final class Adapter implements NativeAdapter {
        final android.service.notification.StatusBarNotification sbn;
        Adapter(long time){sbn=new android.service.notification.StatusBarNotification(time);}
        public android.service.notification.StatusBarNotification getSbn(){return sbn;}
    }
    /** Real getEntry() returns null for newer PipelineEntry implementations. */
    public static final class ModernRow extends ExpandableNotificationRow {
        final NativeAdapter adapter;
        ModernRow(Context context,long time){super(context,90,0,0);adapter=new Adapter(time);}
        @Override public com.android.systemui.statusbar.notification.collection.NotificationEntry getEntry(){return null;}
        public NativeAdapter getEntryAdapter(){return adapter;}
    }
    public static final class Group extends ViewGroup {
        public List<View> mAttachedChildren=new ArrayList<>();
        public Summary mContainingNotification;
        public boolean mChildrenExpanded,mIsUserSwipingToExpandRow;
        public final NativeHeaderExtension mExt=new NativeHeaderExtension();
        public final NativeOverlay mOverlayEx=new NativeOverlay(this);
        public View.OnClickListener mHeaderClickListener;
        public final View nativeHeader;
        public View cachedFirst;public int cachedFirstHeight;
        Group(Summary summary){super(summary.getContext());mContainingNotification=summary;summary.mChildrenContainer=this;summary.addView(this);nativeHeader=new View(summary.getContext());mHeaderClickListener=summary.mExpandClickListener;}
        void child(ExpandableNotificationRow row){addView(row);mAttachedChildren.add(row);}
        public View getGroupHeader(){return nativeHeader;}
        public void nativeMeasure(){cachedFirst=mAttachedChildren.isEmpty()?null:mAttachedChildren.get(0);
            cachedFirstHeight=cachedFirst instanceof ExpandableNotificationRow?((ExpandableNotificationRow)cachedFirst).state.height:0;}
    }
    /** Native collapsed contents are cached independently of row target measurement. */
    public static final class NativeOverlay {
        final Group group;public int updates;public boolean pending;public View renderedFirst;
        NativeOverlay(Group group){this.group=group;}
        public void onChildUpdated(){updates++;pending=true;}
        void consumeNativePending(){if(pending){pending=false;renderedFirst=group.mAttachedChildren.isEmpty()?null:group.mAttachedChildren.get(0);}}
    }
    public static final class NativeHeaderExtension {
        public View header;public int recreates;public View.OnClickListener collapse;
        public View getOplusExpandGroupHeaderView(){return header;}
        public void recreateOplusNotificationHeader(Group container,View.OnClickListener nativeListener,boolean expanded){
            recreates++;header=new View(container.getContext());collapse=nativeListener;container.addView(header);
        }
    }
    private static ExpandableNotificationRow row(Context c,long time){ExpandableNotificationRow row=new ExpandableNotificationRow(c,80,0,0);row.entry.mSbn.postTime=time;return row;}
    private static Bundle enabled(){Bundle b=new Bundle();b.putBoolean(NotificationGroupStack.MASTER,true);return b;}
    public static void main(String[] args)throws Throwable {
        forcedOffSavedSnapshots();restoreLegacyOwnership();nativeWriterWins();
        System.out.println("Notification group stack checks passed: "+checks);
    }
    /** Old imports and the previous master value cannot reactivate the withdrawn feature. */
    private static void forcedOffSavedSnapshots() {
        Context context=new Context();ViewGroup stack=new ViewGroup(context);Summary summary=new Summary(context);
        stack.addView(summary);Group group=new Group(summary);
        ModernRow old=new ModernRow(context,10),latest=new ModernRow(context,90);
        group.child(old);group.child(latest);List<View> nativeOrder=group.mAttachedChildren;
        group.mOverlayEx.renderedFirst=old;group.nativeMeasure();
        NotificationGroupStack helper=new NotificationGroupStack();
        for(boolean legacy:new boolean[]{false,true})for(boolean whole:new boolean[]{false,true})
                for(boolean portrait:new boolean[]{false,true})for(boolean landscape:new boolean[]{false,true}) {
            Bundle config=new Bundle();config.putBoolean(NotificationGroupStack.MASTER,legacy);
            config.putBoolean(NotificationBigClockSettings.STACK_ENABLED,whole);
            config.putBoolean(NotificationBigClockSettings.MASTER,portrait);
            config.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,landscape);
            config.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,landscape);
            helper.configure(config);check(!helper.enabled());
            int requests=group.layoutRequests;
            for(int i=0;i<1000;i++) {
                helper.prepareNative(group,true);helper.prepareNative(group,false);helper.prepareMeasure(group);
                check(group.mAttachedChildren==nativeOrder&&group.layoutRequests==requests);
            }
            group.nativeMeasure();check(group.cachedFirst==old);
            check(group.mAttachedChildren.get(0)==old&&helper.visibleChildren(group,8)==8);
            check(helper.visibleChildren(group,1)==1&&helper.visibleChildren(group,3)==3);
            check(group.mOverlayEx.updates==0&&group.mOverlayEx.renderedFirst==old&&group.mExt.recreates==0);
            check(!helper.tap(stack,new MotionEvent(MotionEvent.ACTION_DOWN,10,10,1000)));
            check(!helper.tap(stack,new MotionEvent(MotionEvent.ACTION_UP,10,10,1070)));
            check(!helper.expandPending()&&summary.clicks==0&&!group.mChildrenExpanded);
            group.mChildrenExpanded=true;helper.prepareMeasure(group);check(helper.visibleChildren(group,8)==8);
            group.mChildrenExpanded=false;summary.mOnKeyguard=true;helper.prepareMeasure(group);
            check(group.mAttachedChildren==nativeOrder&&group.mAttachedChildren.get(0)==old);
            summary.mOnKeyguard=false;
        }
        Bundle safe=enabled();safe.putBoolean(StatusBarSettings.SAFE_MODE,true);helper.configure(safe);
        check(!helper.enabled());helper.configure(null);check(!helper.enabled());
        helper.restore();check(group.mAttachedChildren==nativeOrder&&group.layoutRequests==0);
    }
    /** Seed only a pre-upgrade runtime; production configure never enables this path. */
    private static void legacyActivation(NotificationGroupStack helper)throws ReflectiveOperationException {
        java.lang.reflect.Field enabled=NotificationGroupStack.class.getDeclaredField("enabled");
        enabled.setAccessible(true);enabled.setBoolean(helper,true);
    }
    private static void restoreLegacyOwnership()throws Throwable {
        Context context=new Context();ViewGroup stack=new ViewGroup(context);Summary summary=new Summary(context);
        stack.addView(summary);Group group=new Group(summary);
        ExpandableNotificationRow old=row(context,10),latest=row(context,90),middle=row(context,50);
        group.child(old);group.child(latest);group.child(middle);List<View> nativeOrder=group.mAttachedChildren;
        group.mOverlayEx.renderedFirst=old;
        NotificationGroupStack helper=new NotificationGroupStack();legacyActivation(helper);
        helper.prepareMeasure(group);group.nativeMeasure();
        check(group.mAttachedChildren!=nativeOrder&&group.cachedFirst==latest);
        check(group.mOverlayEx.updates==1);group.mOverlayEx.consumeNativePending();
        check(group.mOverlayEx.renderedFirst==latest);
        check(!helper.tap(stack,new MotionEvent(MotionEvent.ACTION_DOWN,10,10,1000)));
        check(helper.tap(stack,new MotionEvent(MotionEvent.ACTION_UP,10,10,1070)));
        // A legacy true configuration releases pending tap and restores at native measure.
        int requests=group.layoutRequests;helper.configure(enabled());
        check(!helper.enabled()&&!helper.expandPending()&&summary.clicks==0);
        check(group.layoutRequests==requests+1&&group.cachedFirst==latest);
        helper.prepareNative(group,true);check(group.mAttachedChildren.get(0)==latest);
        helper.prepareMeasure(group);group.nativeMeasure();
        check(group.mAttachedChildren==nativeOrder&&group.cachedFirst==old);
        check(group.mOverlayEx.updates==2);group.mOverlayEx.consumeNativePending();
        check(group.mOverlayEx.renderedFirst==old&&helper.visibleChildren(group,8)==8);
        requests=group.layoutRequests;
        for(int i=0;i<1000;i++){helper.configure(enabled());helper.prepareMeasure(group);}
        check(group.layoutRequests==requests&&group.mOverlayEx.updates==2);
        // Add/remove notifications delivered before migration are retained on release.
        legacyActivation(helper);helper.prepareMeasure(group);
        ExpandableNotificationRow added=row(context,200);group.child(added);group.mAttachedChildren.remove(latest);
        helper.configure(enabled());helper.prepareMeasure(group);
        check(group.mAttachedChildren==nativeOrder&&!nativeOrder.contains(latest)&&nativeOrder.contains(added));
        check(nativeOrder.get(0)==old&&nativeOrder.get(nativeOrder.size()-1)==added);
        // Removal must also restore immediately when no future native measure can occur.
        legacyActivation(helper);helper.prepareMeasure(group);check(group.mAttachedChildren.get(0)==added);
        java.lang.reflect.Field removed=ModuleLifecycle.class.getDeclaredField("removed");removed.setAccessible(true);
        boolean previousRemoved=removed.getBoolean(null);
        try{removed.setBoolean(null,true);helper.configure(enabled());check(group.mAttachedChildren==nativeOrder&&nativeOrder.get(0)==old);}
        finally{removed.setBoolean(null,previousRemoved);}
    }
    private static void nativeWriterWins()throws Throwable {
        Context context=new Context();Summary summary=new Summary(context);Group group=new Group(summary);
        ExpandableNotificationRow old=row(context,10),latest=row(context,90);
        group.child(old);group.child(latest);NotificationGroupStack helper=new NotificationGroupStack();
        legacyActivation(helper);helper.prepareMeasure(group);
        List<View> nativeReplacement=new ArrayList<>(Arrays.asList(old,latest));group.mAttachedChildren=nativeReplacement;
        helper.configure(enabled());helper.prepareMeasure(group);
        check(!helper.enabled()&&group.mAttachedChildren==nativeReplacement&&nativeReplacement.get(0)==old);
        check(group.mOverlayEx.updates==1); // Module release cannot refresh an unrelated native replacement.
        legacyActivation(helper);helper.prepareMeasure(group);helper.detach(group);
        check(group.mAttachedChildren==nativeReplacement&&nativeReplacement.get(0)==old);
        helper.configure(enabled());check(!helper.enabled());
    }
}
