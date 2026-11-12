// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 同应用小堆叠: a collapsed ColorOS 17 native notification group shows its first real child in full and at most two
 * real rear children behind it (92%/84% wide, 55%/30% opaque, 4dp/8dp exposed). Only existing native groups qualify
 * (summary with two or more attached children); nothing is regrouped, sorted, synthesized or removed, and expansion,
 * clicks, swipes, clear-all and the spring animations stay native. Colours, gradients and highlights stay with their
 * own owners (C17HighlightRemoval, FluidCloudSceneMaterial, SceneHighlightTint): this class only shows the children's
 * native backgrounds and clips, moves and fades the real cards.
 *
 * Feature reference: OplusX「启用小堆叠（同应用）」by xxxxawl (https://www.coolapk.com/u/18956155), whose ColorOS 17
 * group deck is in turn based on VivoGroupDeck test21. This is a Java 8 re-implementation for this module's hook
 * framework. The reference's 48dp single-notification pull-to-expand gesture is deliberately not included.
 */
public final class NotificationMiniStack {
    public static final String ENABLED="notification_mini_stack_enabled";
    /** Hides the deck count and native count badges for eligible groups, including native expansion. */
    public static final String HIDE_BADGE="notification_mini_stack_hide_badge";
    public static final String AUTO_HIDE_IN_STACK="notification_stack_auto_hide_mini_stack";
    public interface OuterStackPolicy { boolean manages(View summary); }
    public void setOuterStackPolicy(OuterStackPolicy policy){outerStackPolicy=policy;main.post(this::invalidateGroups);}
    /** Native groups stay native until opted in; their counts are shown by default. */
    public static final Map<String,Boolean> BOOLEANS;
    static {
        Map<String,Boolean> values=new java.util.LinkedHashMap<>();
        values.put(ENABLED,false);values.put(HIDE_BADGE,false);values.put(AUTO_HIDE_IN_STACK,true);
        BOOLEANS=java.util.Collections.unmodifiableMap(values);
    }
    static final float FIRST_REAR_ALPHA=.55f,SECOND_REAR_ALPHA=.30f,LAYER_GAP_DP=4f;
    static final int SET_EXPANDED=0,SET_CHANGING=1,ROW_SWIPING=2,CONTAINER_SWIPING=3,SET_ANIMATING=4;
    private static final String SOURCE="C17-mini-stack";
    /** NotificationContentView.VISIBLE_TYPE_CONTRACTED. */
    private static final int CONTRACTED=0;
    private static final int BADGE_FILL=0xffe9e9f2,BADGE_TEXT=0xff53535c;

    public interface Call { Object run()throws Throwable; }

    // ---- Fixed geometry, identical to the reference's VivoNotificationVisualPolicy / VivoGroupOcclusion. ----
    static int depth(int children){return Math.min(Math.max(children,1)-1,2);}
    static float scale(int layer){return layer<=0?1f:layer==1?.92f:.84f;}
    static int tail(int children,float density){
        return density>0f&&!Float.isInfinite(density)?Math.round(depth(children)*LAYER_GAP_DP*density):0;
    }
    /** First contracted height plus 4dp for each visible rear layer; -1 when it cannot be represented. */
    static int stackHeight(int first,int children,float density){
        int tail=tail(children,density);
        return first>0&&first<=Integer.MAX_VALUE-tail?first+tail:-1;
    }
    /** Layout Y of a rear card at the closed endpoint; the draw transform then scales it into backing(). */
    static float foldedY(int frontHeight,int rearHeight,int layer,float gap){
        return frontHeight-rearHeight+Math.min(Math.max(layer,1),2)*gap;
    }
    static float backBottom(float frontBottom,int layer,int depth,float tail){
        int levels=Math.max(depth,1);
        return frontBottom+tail*Math.min(Math.max(layer,0),levels)/levels;
    }
    static void backing(RectF front,int layer,int depth,float tail,RectF out){
        float s=scale(layer),inset=front.width()*(1f-s)/2f,bottom=backBottom(front.bottom,layer,depth,tail);
        out.set(front.left+inset,bottom-front.height()*s,front.right-inset,bottom);
    }
    static float opacity(int index){return index==0?1f:index==1?FIRST_REAR_ALPHA:index==2?SECOND_REAR_ALPHA:0f;}
    static String badge(int count){return count>99?"99+":Integer.toString(count);}
    static float blend(float end,float current,float progress){return end+(current-end)*progress;}
    private static float unit(float value){return Float.isNaN(value)?0f:Math.max(0f,Math.min(1f,value));}

    // ---- ColorOS 17 contract. Every member is resolved and type-checked before the first hook is installed. ----
    static final String ROW="com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    static final String CONTAINER="com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer";
    static final String ROW_EXTENSION="com.oplus.systemui.statusbar.notification.row.OplusExpandableNotificationRowExImpl";
    static final String OVERLAY="com.oplus.systemui.statusbar.notification.stack.OplusNotificationChildrenContainerOverlayExImpl";
    static final String STACK="com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout";
    static final String STATE_EXTENSION="com.oplus.systemui.statusbar.notification.stack.OplusExpandableViewStateExImpl";
    static final String SOURCE_TYPE="com.android.systemui.statusbar.notification.SourceType";
    static final String CLICKER="com.oplus.systemui.statusbar.notification.OplusNotificationClicker";
    static final String CONTAINER_EXTENSION="com.oplus.systemui.statusbar.notification.stack.NotificationChildrenContainerExtImp";
    static final String GROUP_EXTENSION="com.oplus.systemui.notification.row.oplusgroup.OplusNotificationGroupExtImpl";
    static final String ANIMATION_PARAMS="com.oplus.systemui.notification.stackednotification.olive.GroupExpansionAnimParams";

    /** C16 also has the overlay and animation classes; only C17's rows carry the bundle and pull state. */
    public static boolean supports(ClassLoader loader){
        try {
            Class.forName(ANIMATION_PARAMS,false,loader);Class.forName(OVERLAY,false,loader);
            return supports(Class.forName(ROW,false,loader),Class.forName(CONTAINER,false,loader));
        }catch(ClassNotFoundException|LinkageError absent){return false;}
    }
    static boolean supports(Class<?> row,Class<?> container){
        return declares(container,"mOverlayEx")&&declares(container,"mIsUserSwipingToExpandRow")
                &&declares(row,"mIsBundle")&&declares(row,"mIsUserSwipingToExpandRow");
    }
    private static boolean declares(Class<?> type,String name){
        try{type.getDeclaredField(name);return true;}catch(NoSuchFieldException|LinkageError absent){return false;}
    }

    public static final class Targets {
        final Class<?> row,container,extension,overlay,stack,stateExtension,source,clicker,content,state,containerExtension,groupExtension;
        final Field summary,bundle,expanded,changing,swiping,headsUp,launching,minimized,childrenContainer,parent,notificationParent,rowEx;
        final Field animating,progress,contentAlpha,overlayEx,overlayAnimating,overlayLocked,containerSwiping,childClip;
        final Field privateLayout,publicLayout,contracted,inGroup,selectedType,contentOwner,background,expandListener,menu,ambient;
        final Field stateHeight,stateExtensionField,stateHidden,rowClipTop,rowClipBottom,clipTop,clipBottom,overlayContainer,collapsedHeader;
        final Field overflowNumber,containerEx,groupEx,numberView,numberPill;
        final Field nativeChildren;
        final Method transientCount,transientView;
        final Method encrypted,menuVisible,children,showing,publicView,guts,actualHeight,radius,minimum,setGrouped,selectLayout,extensionView;
        final Method getState,setStateContentAlpha,stateY,stateAlpha,roundness;
        /** Optional: the row's Y in the pass being computed. Without it no row is ever treated as off screen. */
        final Method stateGetY;
        /** Optional: NotificationChildrenContainer.mHeaderViewState, the group header's own native view state. */
        final Field headerState;
        final Method rowAnimation,rowScale,scaleX,scaleY;
        public final Method collapsedHeight,containerMinimum,intrinsic,updateChildren,rowDraw,visibleType,heightType,drawChild;
        public final Method setExpanded,setChanging,rowSetSwiping,setSwiping,setAnimating,updateBackground,resetRoundness,click;

        public Targets(ClassLoader loader)throws ReflectiveOperationException {
            this(type(loader,ROW),type(loader,CONTAINER),type(loader,ROW_EXTENSION),type(loader,OVERLAY),type(loader,STACK),
                    type(loader,STATE_EXTENSION),type(loader,SOURCE_TYPE),type(loader,CLICKER),type(loader,CONTAINER_EXTENSION),type(loader,GROUP_EXTENSION));
        }
        Targets(Class<?> row,Class<?> container,Class<?> extension,Class<?> overlay,Class<?> stack,Class<?> stateExtension,
                Class<?> source,Class<?> clicker,Class<?> containerExtension,Class<?> groupExtension)throws ReflectiveOperationException {
            this.row=row;this.container=container;this.extension=extension;this.overlay=overlay;this.stack=stack;
            this.stateExtension=stateExtension;this.source=source;this.clicker=clicker;
            this.containerExtension=containerExtension;this.groupExtension=groupExtension;
            Field array=null;
            try{array=field(ViewGroup.class,"mChildren",View[].class);}
            catch(ReflectiveOperationException|RuntimeException unsupported){/* Preserve public membership checks on other frameworks. */}
            nativeChildren=array;
            Class<?> z=Boolean.TYPE,f=Float.TYPE,i=Integer.TYPE,v=Void.TYPE;
            summary=field(row,"mIsSummaryWithChildren",z);bundle=field(row,"mIsBundle",z);expanded=field(row,"mChildrenExpanded",z);
            changing=field(row,"mGroupExpansionChanging",z);swiping=field(row,"mIsUserSwipingToExpandRow",z);
            headsUp=field(row,"mIsHeadsUp",z);launching=field(row,"mExpandAnimationRunning",z);minimized=field(row,"mIsMinimized",z);
            childrenContainer=field(row,"mChildrenContainer",container);parent=field(container,"mContainingNotification",row);
            notificationParent=field(row,"mNotificationParent",row);rowEx=field(row,"mRowEx",null);
            animating=field(extension,"isChildrenExpandedAnimating",z);progress=field(extension,"groupExpansionProgress",f);
            contentAlpha=field(extension,"groupExpandContentAlpha",f);encrypted=method(extension,"getShouldEncrypted",z);
            overlayEx=field(container,"mOverlayEx",null);overlayAnimating=field(overlay,"isGroupExpandAnimating",z);
            overlayLocked=field(overlay,"isUserLocked",z);containerSwiping=field(container,"mIsUserSwipingToExpandRow",z);
            childClip=field(container,"mChildClipPath",Path.class);
            privateLayout=field(row,"mPrivateLayout",null);content=privateLayout.getType();
            publicLayout=field(row,"mPublicLayout",content);contracted=field(content,"mContractedChild",View.class);
            inGroup=field(content,"mIsChildInGroup",z);selectedType=field(content,"mVisibleType",i);
            contentOwner=field(content,"mContainingNotification",row);
            background=field(row,"mBackgroundNormal",null);expandListener=field(row,"mExpandClickListener",null);
            menu=field(row,"mMenuRow",null);
            Method visible=menu.getType().getMethod("isMenuVisible");
            if(visible.getReturnType()!=z)throw new NoSuchMethodException("isMenuVisible");
            visible.setAccessible(true);menuVisible=visible;
            ambient=field(stack,"mAmbientState",null);
            children=method(row,"getAttachedChildren",List.class);showing=method(row,"getShowingLayout",content);
            publicView=method(row,"shouldShowPublic",z);guts=method(row,"areGutsExposed",z);
            actualHeight=method(row,"getActualHeight",i);radius=method(row,"getMaxRadius",f);
            minimum=method(content,"getMinHeight",i,z);visibleType=method(content,"calculateVisibleType",i);
            heightType=method(content,"getVisualTypeForHeight",i,f);setGrouped=method(content,"setIsChildInGroup",v,z);
            selectLayout=method(content,"selectLayout",v,z,z);updateBackground=method(row,"updateBackgroundForGroupState",v);
            updateChildren=method(row,"updateChildrenStates",v);rowDraw=method(row,"dispatchDraw",v,Canvas.class);
            drawChild=method(container,"drawChild",z,Canvas.class,View.class,Long.TYPE);
            intrinsic=method(container,"getIntrinsicHeight",i);collapsedHeight=method(container,"getCollapsedHeight",i);
            containerMinimum=method(container,"getMinHeight",i);
            setExpanded=method(row,"setChildrenExpanded",v,z);setChanging=method(row,"setGroupExpansionChanging",v,z);
            setSwiping=method(container,"setUserSwipingToExpandRow",v,z);rowSetSwiping=method(row,"setUserSwipingToExpandRow",v,z);
            setAnimating=method(extension,"setChildrenExpandedAnimating",v,z);
            Method view=extension.getMethod("getView");view.setAccessible(true);extensionView=view;
            getState=method(row,"getViewState",null);state=getState.getReturnType();
            stateHeight=field(state,"height",i);stateExtensionField=field(state,"mExpandableViewStateEx",null);
            setStateContentAlpha=method(stateExtension,"setGroupExpandContentAlpha",v,f);
            Method animation=null,scale=null,x=null,y=null;
            try {
                animation=stateExtension.getMethod("getRowAnimationStateEx");
                scale=animation.getReturnType().getMethod("getRowScale",Object.class);
                x=scale.getReturnType().getMethod("getScaleX");y=scale.getReturnType().getMethod("getScaleY");
                if(x.getReturnType()!=f||y.getReturnType()!=f)throw new NoSuchMethodException("Native stack scale");
                animation.setAccessible(true);scale.setAccessible(true);x.setAccessible(true);y.setAccessible(true);
            }catch(ReflectiveOperationException|RuntimeException missing){animation=null;scale=null;x=null;y=null;}
            rowAnimation=animation;rowScale=scale;scaleX=x;scaleY=y;
            overlayContainer=field(overlay,"overlayContainer",null);
            collapsedHeader=field(overlay,"opusCollapsedGroupHeader",ViewGroup.class);
            overflowNumber=field(container,"mOverflowNumber",TextView.class);
            containerEx=field(container,"mExt",null);
            if(!containerEx.getType().isAssignableFrom(containerExtension))throw new NoSuchFieldException("C17 container extension changed");
            groupEx=field(containerExtension,"oplusNotificationGroupExtImpl",groupExtension);
            numberView=field(groupExtension,"numberView",TextView.class);
            numberPill=field(groupExtension,"expandedPill",null);
            if(!ViewGroup.class.isAssignableFrom(numberPill.getType()))throw new NoSuchFieldException("C17 number pill changed");
            // These are reads, not framework hooks. Overflow removal fades through transient children.
            transientCount=method(container,"getTransientViewCount",i);
            transientView=method(container,"getTransientView",View.class,i);
            stateHidden=field(state,"hidden",z);rowClipTop=field(row,"mClipTopAmount",i);rowClipBottom=field(row,"mClipBottomAmount",i);
            clipTop=field(state,"clipTopAmount",i);clipBottom=field(state,"clipBottomAmount",i);
            stateY=method(state,"setYTranslation",v,f,String.class);stateAlpha=method(state,"setAlpha",v,f,String.class);
            Method currentY=null;
            try{for(Class<?> cursor=state;cursor!=null&&currentY==null;cursor=cursor.getSuperclass())try{
                Method candidate=cursor.getDeclaredMethod("getYTranslation");
                if(candidate.getReturnType()==f&&!Modifier.isStatic(candidate.getModifiers())){candidate.setAccessible(true);currentY=candidate;}
            }catch(NoSuchMethodException next){}}catch(RuntimeException|LinkageError unavailable){currentY=null;}
            stateGetY=currentY;
            Field header=null;
            try{
                header=field(container,"mHeaderViewState",null);
                if(!stateHidden.getDeclaringClass().isAssignableFrom(header.getType()))header=null;
            }catch(NoSuchFieldException|RuntimeException|LinkageError absent){header=null;}
            headerState=header;
            roundness=method(row,"requestRoundness",z,f,f,source,z);resetRoundness=method(row,"requestRoundnessReset",v,source,z);
            click=method(clicker,"onClick",v,View.class);
            for(Method hooked:new Method[]{collapsedHeight,containerMinimum,intrinsic,updateChildren,rowDraw,visibleType,heightType,drawChild,
                    setExpanded,setChanging,rowSetSwiping,setSwiping,setAnimating,updateBackground,resetRoundness,click})
                if(framework(hooked.getDeclaringClass()))throw new NoSuchMethodException("Refusing a framework-wide hook: "+hooked);
        }
        private static Class<?> type(ClassLoader loader,String name)throws ClassNotFoundException{return Class.forName(name,false,loader);}
        static boolean framework(Class<?> type){String name=type.getName();return name.startsWith("android.")||name.startsWith("java.");}
        static Field field(Class<?> type,String name,Class<?> expected)throws NoSuchFieldException{
            for(Class<?> current=type;current!=null;current=current.getSuperclass()) {
                Field found;
                try{found=current.getDeclaredField(name);}catch(NoSuchFieldException absent){continue;}
                if(Modifier.isStatic(found.getModifiers())||expected!=null&&found.getType()!=expected)
                    throw new NoSuchFieldException("C17 field changed: "+name);
                found.setAccessible(true);return found;
            }
            throw new NoSuchFieldException("C17 missing field: "+name);
        }
        static Method method(Class<?> type,String name,Class<?> result,Class<?>... args)throws NoSuchMethodException{
            for(Class<?> current=type;current!=null;current=current.getSuperclass()) {
                Method found=null;
                for(Method candidate:current.getDeclaredMethods()) {
                    if(!candidate.getName().equals(name)||!java.util.Arrays.equals(candidate.getParameterTypes(),args))continue;
                    if(found==null||found.isBridge()&&!candidate.isBridge())found=candidate;
                }
                if(found==null)continue;
                if(Modifier.isStatic(found.getModifiers())||Modifier.isAbstract(found.getModifiers())||result!=null&&found.getReturnType()!=result)
                    throw new NoSuchMethodException("C17 method changed: "+name);
                found.setAccessible(true);return found;
            }
            throw new NoSuchMethodException("C17 missing method: "+name);
        }
    }

    /** SourceType.from(String): a @JvmStatic on the interface, or the Kotlin companion when it is not. */
    static Object sourceType(Class<?> type,String name)throws ReflectiveOperationException{
        try {
            Method from=type.getDeclaredMethod("from",String.class);
            if(Modifier.isStatic(from.getModifiers())){from.setAccessible(true);Object value=from.invoke(null,name);if(type.isInstance(value))return value;}
        }catch(NoSuchMethodException absent){ }
        Field companion=type.getDeclaredField("Companion");companion.setAccessible(true);
        Object owner=companion.get(null);
        Method from=owner.getClass().getDeclaredMethod("from",String.class);from.setAccessible(true);
        Object value=from.invoke(owner,name);
        if(!type.isInstance(value))throw new NoSuchMethodException("SourceType.from");
        return value;
    }

    // ---- Runtime state. Hooks run on the SystemUI main thread; configure() posts its release there. ----
    private static final class DrawSample {
        View row;boolean sampled,badgesSynced,coverageSampled,eligibilitySampled;float coverage;List<View> children,eligible;
        final IdentityHashMap<View,Integer> heights=new IdentityHashMap<>();
        final IdentityHashMap<View,Float> radii=new IdentityHashMap<>();
        void clear(){row=null;sampled=false;badgesSynced=false;coverageSampled=false;eligibilitySampled=false;coverage=0f;children=null;eligible=null;heights.clear();radii.clear();}
    }
    private static final class SavedContent {
        final WeakReference<View> view;final float alpha;float applied;
        SavedContent(View view){this.view=new WeakReference<>(view);alpha=view.getAlpha();applied=alpha;}
    }
    private static final class SavedLayout {
        final WeakReference<View> view;final boolean grouped;WeakReference<View> contracted=new WeakReference<>(null);
        SavedLayout(View view,boolean grouped){this.view=new WeakReference<>(view);this.grouped=grouped;}
    }
    private static final class Card {
        final WeakReference<View> child,owner;
        final List<SavedLayout> layouts=new ArrayList<>();
        final List<SavedContent> contents=new ArrayList<>();
        boolean refreshed;
        WeakReference<View> background,privateLayout,publicLayout;
        ViewOutlineProvider originalOutline,appliedOutline;
        boolean originalClip;
        int height;float radius;
        Card(View child,View owner){this.child=new WeakReference<>(child);this.owner=new WeakReference<>(owner);}
    }
    private static final class Session {
        final List<Card> cards=new ArrayList<>();
        final List<SavedContent> summaryContents=new ArrayList<>();
        final List<WeakReference<View>> children=new ArrayList<>();
        OuterOwner outer;
        long visualEpoch=-1L,backgroundEpoch=-1L;
        /** Endpoint written by the last full, settled pass; reused only while every epoch below is unchanged. */
        boolean stable;long stableVisual,stableBackground,stableEvents;int stableStreak,stableFirst;
        final int[] stableHeight=new int[3];final float[] stableY=new float[3];final boolean[] stableWritten=new boolean[3];
        WeakReference<View> summaryBackground=new WeakReference<>(null);
        Session(List<View> members){for(View child:members)children.add(new WeakReference<>(child));}
        boolean matches(List<?> members){
            if(children.size()!=members.size())return false;
            for(int index=0;index<members.size();index++)if(children.get(index).get()!=members.get(index))return false;
            return true;
        }
    }
    private static final class CardOutline extends ViewOutlineProvider {
        private final Card card;
        CardOutline(Card card){this.card=card;}
        @Override public void getOutline(View view,Outline outline){
            int height=Math.min(view.getHeight(),card.height);
            if(view.getWidth()>0&&height>0)outline.setRoundRect(0,0,view.getWidth(),height,card.radius);
        }
    }

    private final Handler main;
    private volatile boolean enabled,ready,hideBadge;
    private volatile boolean autoHideInStack=true,outerSafe;
    private OuterStackPolicy outerStackPolicy;
    private volatile String unavailable="";
    private Targets t;
    private Object roundnessSource;
    private final Map<View,Session> sessions=new WeakHashMap<>();
    private final Map<View,Boolean> handoff=new WeakHashMap<>();
    private final Visibility visibility=new Visibility();
    private final Backgrounds backgrounds=new Backgrounds();
    private final RowAlpha rowAlpha=new RowAlpha();
    private final Set<String> reported=new HashSet<>();
    private boolean refreshing,replaying;
    private final ArrayList<DrawSample> samples=new ArrayList<>(),samplePool=new ArrayList<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics metrics=new Paint.FontMetrics();
    private final RectF front=new RectF(),source=new RectF(),rear=new RectF(),nearer=new RectF(),endpoint=new RectF(),iconRect=new RectF();
    private final Matrix matrix=new Matrix();
    private final Map<Resources,Map<Integer,Integer>> nativeNumberIds=new WeakHashMap<>();
    private final Map<Resources,Integer> nativeCoveredProgressIds=new WeakHashMap<>();
    private final Map<View,BadgeOwner> badgeOwners=new WeakHashMap<>();
    private final Map<View,BadgeLease> badgeViews=new WeakHashMap<>();
    private final Map<ViewGroup,BadgeOwner> badgeBranches=new WeakHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong backgroundHierarchyEpoch=new java.util.concurrent.atomic.AtomicLong();
    private boolean hierarchyHooks,visualHooks;
    private final java.util.concurrent.atomic.AtomicLong visualEpoch=new java.util.concurrent.atomic.AtomicLong();
    private int visualWrites;
    private final Map<View,WeakReference<ViewGroup>> nativeOverflows=new WeakHashMap<>();
    private int badgeWrites;
    private static final int MAX_OUTLINES=48;
    private final ArrayList<OutlineShape> outlines=new ArrayList<>();
    private int nextOutline;
    /** Immutable local geometry; no notification/View references or previous-frame pixels. */
    private static final class OutlineShape {
        final float left,top,right,bottom,radius;
        final Path path=new Path();
        OutlineShape(RectF bounds,float radius){
            left=bounds.left;top=bounds.top;right=bounds.right;bottom=bounds.bottom;this.radius=radius;
            path.addRoundRect(bounds,radius,radius,Path.Direction.CW);
        }
        boolean matches(RectF bounds,float r){return left==bounds.left&&top==bounds.top
                &&right==bounds.right&&bottom==bounds.bottom&&radius==r;}
    }
    private int syncs,releases,clicks,offscreenSkips,stablePasses;
    /** Bumped by every native transition, release and group invalidation: anything that can change an endpoint
     * without a hierarchy or content event. */
    private long stableEvents;
    /** A settled group is fully validated again at least this often, so a silent native write is repaired. */
    static final int STABLE_PASS_LIMIT=30;
    private String lastStage="unbound";

    public NotificationMiniStack(Handler main){
        if(main==null)throw new IllegalArgumentException("Main handler is required");
        this.main=main;
    }

    public void configure(Bundle settings){
        boolean on=settings!=null&&settings.getBoolean(ENABLED,false);
        boolean wasOn=enabled,wasHidden=hideBadge,wasAuto=autoHideInStack,wasSafe=outerSafe;
        enabled=on;hideBadge=settings!=null&&settings.getBoolean(HIDE_BADGE,false);
        autoHideInStack=settings==null||settings.getBoolean(AUTO_HIDE_IN_STACK,true);outerSafe=SafetyMode.enabled(settings);
        if(wasOn&&!on)main.post(this::releaseAll);
        else if(on&&(wasHidden!=hideBadge||wasAuto!=autoHideInStack||wasSafe!=outerSafe))main.post(this::invalidateGroups);
    }
    /** The on-demand hooks are wanted only while the switch is on. */
    public boolean wanted(){return enabled&&!ModuleLifecycle.removed();}
    public boolean active(){return enabled&&ready&&!ModuleLifecycle.removed();}
    public boolean hideBadge(){return hideBadge;}
    /** Enable only after every native array/transient mutation hook was installed. */
    public void hierarchyHooksReady(boolean value){
        hierarchyHooks=value;visualEpoch.incrementAndGet();
        for(BadgeOwner owner:badgeOwners.values())owner.treeEpoch++;
    }
    /** Rare structural changes, never a frame timer. Hooks run both before and after native callbacks. */
    public void hierarchyChanged(ViewGroup group){
        // RemoteViews can inflate off-main. Coalesce those mutations into one validation
        // on the next visible frame instead of posting one UI task per inflated child.
        if(Looper.myLooper()!=main.getLooper()){backgroundHierarchyEpoch.incrementAndGet();return;}
        if(sessions.isEmpty()&&badgeBranches.isEmpty())return;
        // Framework child-array hooks also see unrelated SystemUI widgets. Only a
        // live notification owner can invalidate an already applied mini-stack plan.
        for(View current=group;current!=null;current=parentView(current))if(sessions.containsKey(current)){
            visualEpoch.incrementAndGet();break;
        }
        BadgeOwner owner=badgeBranches.get(group);
        if(owner!=null)owner.treeEpoch++;
    }
    /** Stable visual plans are used only with the complete content and hierarchy callback contract. */
    public void visualHooksReady(boolean value){visualHooks=value;visualEpoch.incrementAndGet();}
    public void contentUpdated(Object content){
        if(!active()||!t.content.isInstance(content))return;
        if(Looper.myLooper()!=main.getLooper()){backgroundHierarchyEpoch.incrementAndGet();return;}
        if(visualWrites!=0)return;
        try {
            Object row=t.contentOwner.get(content),parent=row==null?null:t.notificationParent.get(row);
            if(sessions.containsKey(row)||sessions.containsKey(parent))visualEpoch.incrementAndGet();
        }catch(ReflectiveOperationException|RuntimeException missing){visualEpoch.incrementAndGet();}
    }
    /** View.setAlpha before-hook: retain native intent while the exact number node stays hidden on the GPU. */
    public float nativeBadgeAlpha(View view,float requested){
        if(view==null||Looper.myLooper()!=main.getLooper()||badgeWrites!=0||rowAlpha.writing())return requested;
        if(badgeViews.isEmpty()&&rowAlpha.empty())return requested;
        RowAlpha.Saved rear=rowAlpha.rear(view);
        if(rear!=null){
            View row=rear.owner.get();
            try{
                Object members=row==null?null:t.children.invoke(row);
                if(row==null||!sessions.containsKey(row)||view.getParent()!=t.childrenContainer.get(row)
                        ||t.notificationParent.get(view)!=row||!(members instanceof List)
                        ||rear.index>=((List<?>)members).size()||((List<?>)members).get(rear.index)!=view){
                    if(row==null)rowAlpha.restore(rear);else release(row);
                }
            }catch(ReflectiveOperationException|RuntimeException missing){
                report("后卡透明度归属",missing);
                if(row==null)rowAlpha.restore(rear);else release(row);
            }
        }
        View outer=rowAlpha.coveredOwner(view);
        if(outer!=null){Session session=sessions.get(outer);if(session!=null)updateOuter(outer,session);}
        requested=rowAlpha.nativeAlpha(view,requested);
        BadgeLease lease=badgeViews.get(view);
        if(lease==null)return requested;
        lease.nativeAlpha=requested;
        View row=lease.owner.row.get();
        try {
            if(row==null||!badgeEligible(row)) {
                releaseBadgeOwner(lease.owner);
                return requested;
            }
        }catch(ReflectiveOperationException|RuntimeException failure){
            report("数量角标原生写入",failure);releaseBadgeOwner(lease.owner);return requested;
        }
        return 0f;
    }
    /** Called once all contract members resolved, before the first hook is installed. */
    public void bind(Targets targets)throws ReflectiveOperationException{
        if(targets==null)throw new IllegalArgumentException("Mini stack targets are required");
        roundnessSource=sourceType(targets.source,SOURCE);
        t=targets;lastStage="bound";
    }
    /** False after a partial install: every installed hook then passes straight through to native code. */
    public void hooksReady(boolean value){ready=value&&t!=null;lastStage=ready?"ready":"unavailable";if(!ready)main.post(this::releaseAll);}
    public void unsupported(String reason){ready=false;unavailable=reason==null?"":reason;lastStage="unsupported";}
    public String diagnosticSummary(){
        return "mini requested="+enabled+" ready="+ready+" badge="+!hideBadge+" sessions="+sessions.size()+" syncs="+syncs
                +" releases="+releases+" offscreen="+offscreenSkips+" stable="+stablePasses+" clicks="+clicks+" stage="+lastStage+(unavailable.isEmpty()?"":" unavailable="+unavailable);
    }

    private void report(String stage,Throwable failure){
        lastStage=stage;
        if(reported.add(stage))ModuleDiagnostics.error("mini-stack",stage+"："+failure.getClass().getSimpleName(),failure);
    }

    // ---- Hook entry points. Each one leaves native code untouched unless the feature is active. ----

    /** NotificationChildrenContainer.getCollapsedHeight / getMinHeight / getIntrinsicHeight. Expanded heights stay native. */
    public Object height(Object container,boolean intrinsic,Call call)throws Throwable{
        if(!active()||!t.container.isInstance(container))return call.run();
        View row=null;
        try {
            Object owner=t.parent.get(container);
            if(owner instanceof View) {
                row=(View)owner;
                if(rawClosed(row)){List<View> children=eligibleChildren(row);int height=children==null?-1:stackHeight(row,children);if(height>0)return height;}
            }
        }catch(ReflectiveOperationException|RuntimeException failure){report("收起高度预计算",failure);}
        Object result=call.run();
        if(row==null)return result;
        try {
            if(intrinsic&&(t.expanded.getBoolean(row)||t.swiping.getBoolean(row)))return result;
            List<View> children=eligibleChildren(row);
            int height=children==null?-1:stackHeight(row,children);
            if(height>0)return height;
        }catch(ReflectiveOperationException|RuntimeException failure){report("收起高度",failure);}
        return result;
    }

    /** Native update queries many child templates/heights before our endpoint hook runs.
     * Share only that synchronous read scope; native mutations must be sampled again afterwards. */
    public Object updateChildren(Object owner,Call call)throws Throwable{
        boolean sampled=active()&&t.row.isInstance(owner)&&t.summary.getBoolean(owner);
        if(sampled)beginDraw((View)owner);
        Object result;
        try{result=call.run();}finally{if(sampled)endDraw();}
        childrenUpdated(owner);return result;
    }
    /** After ExpandableNotificationRow.updateChildrenStates: write the closed endpoint into the native child ViewStates. */
    public void childrenUpdated(Object owner){
        if(!active()||replaying||!t.row.isInstance(owner))return;
        View row=(View)owner;
        boolean sampled=false;
        try {
            // Ordinary rows have no group geometry or badge tree. Preserve cleanup for
            // a former summary, but avoid traversing every leaf in a large notification list.
            if(!t.summary.getBoolean(row)&&!sessions.containsKey(row)&&!badgeOwners.containsKey(row))return;
            // The native pass updates every row of the list on every frame. A group more than one list height
            // away cannot be drawn in this frame; its endpoint is written again in the pass that brings it back.
            if(farOutside(row)){offscreenSkips++;return;}
            // The native pass recomputes every child state on every frame. A settled group whose inputs did not
            // change gets the endpoint of its last full pass written back, without sampling the whole contract again.
            Session known=sessions.get(row);
            if(known!=null&&known.stable&&stableEndpoint(row,known)){syncs++;stablePasses++;return;}
            if(known!=null)known.stable=false;
            beginDraw(row);sampled=true;
            List<View> children=returningChildren(row);
            if(children==null){syncBadgeOwnership(row);return;}
            boolean settled=rawClosed(row);
            long visual=visualEpoch.get(),background=backgroundHierarchyEpoch.get(),events=stableEvents;
            sync(row,children,settled);
            int first=contractedHeight(children.get(0));
            if(first<=0)return;
            float gap=LAYER_GAP_DP*density(row);
            Session session=sessions.get(row);
            boolean complete=settled&&session!=null;
            if(session!=null)for(int index=0;index<3;index++)session.stableWritten[index]=false;
            for(int index=0;index<children.size();index++) {
                View child=children.get(index);
                Object state=t.getState.invoke(child);
                if(state==null||!t.state.isInstance(state)){complete=false;continue;}
                if(settled)t.stateHidden.setBoolean(state,index>2);
                Object extension=t.stateExtensionField.get(state);
                if(t.stateExtension.isInstance(extension))t.setStateContentAlpha.invoke(extension,index==0?1f:0f);
                t.stateAlpha.invoke(state,opacity(index),SOURCE);
                if(index>2)continue;
                int height=contractedHeight(child);
                if(height<=0){complete=false;continue;}
                float y=index==0?0f:foldedY(first,height,index,gap);
                t.stateHeight.setInt(state,height);t.clipTop.setInt(state,0);t.clipBottom.setInt(state,0);
                t.stateY.invoke(state,y,SOURCE);
                if(session!=null){session.stableHeight[index]=height;session.stableY[index]=y;session.stableWritten[index]=true;}
            }
            if(settled&&session!=null)hideHeaderState(row);
            syncs++;
            // Only a pass that saw no event of its own may be reused: sync() above can itself release or rebuild.
            if(complete&&hierarchyHooks&&visualHooks&&!handoff.containsKey(row)&&session==sessions.get(row)
                    &&visual==visualEpoch.get()&&background==backgroundHierarchyEpoch.get()&&events==stableEvents) {
                session.stable=true;session.stableVisual=visual;session.stableBackground=background;session.stableEvents=events;
                session.stableStreak=0;session.stableFirst=first;
            }
        }catch(ReflectiveOperationException|RuntimeException failure){report("收起子卡状态",failure);}
        finally{if(sampled)endDraw();}
    }

    /** Writes the remembered endpoint for a settled group. False whenever anything it depends on may have changed;
     * the caller then runs the full pass. Reads only the live expansion state and the member list. */
    private boolean stableEndpoint(View row,Session session)throws ReflectiveOperationException{
        if(!hierarchyHooks||!visualHooks||session.stableVisual!=visualEpoch.get()
                ||session.stableBackground!=backgroundHierarchyEpoch.get()||session.stableEvents!=stableEvents
                ||++session.stableStreak>STABLE_PASS_LIMIT||handoff.containsKey(row))return false;
        // Everything the full pass reads live for a closed, eligible group is read live here too: expansion,
        // swipes, native animation, privacy, guts, heads-up and membership. Only the visual plan is reused.
        if(!t.summary.getBoolean(row)||t.expanded.getBoolean(row)||t.changing.getBoolean(row)||t.swiping.getBoolean(row)
                ||t.bundle.getBoolean(row)||t.headsUp.getBoolean(row)||t.launching.getBoolean(row)||t.minimized.getBoolean(row)
                ||yes(t.publicView,row)||yes(t.guts,row)||encrypted(row))return false;
        Object extension=t.rowEx.get(row);
        if(!t.extension.isInstance(extension)||t.animating.getBoolean(extension)||t.progress.getFloat(extension)!=0f)return false;
        Object container=t.childrenContainer.get(row);
        if(!(container instanceof ViewGroup)||t.containerSwiping.getBoolean(container))return false;
        Object overlay=t.overlayEx.get(container);
        if(!t.overlay.isInstance(overlay)||t.overlayAnimating.getBoolean(overlay)||t.overlayLocked.getBoolean(overlay))return false;
        View stack=parentView(row);
        while(stack!=null&&!t.stack.isInstance(stack))stack=parentView(stack);
        if(stack==null||t.ambient.get(stack)==null)return false;
        Object members=t.children.invoke(row);
        if(!(members instanceof List)||!session.matches((List<?>)members))return false;
        List<?> children=(List<?>)members;
        if(children.size()<2)return false;
        for(int index=0;index<Math.min(3,children.size());index++) {
            Object member=children.get(index);
            if(!(member instanceof View))return false;
            View child=(View)member;
            if(child.getParent()!=container||t.notificationParent.get(child)!=row||t.headsUp.getBoolean(child)
                    ||t.launching.getBoolean(child)||yes(t.publicView,child)||yes(t.guts,child)||encrypted(child))return false;
        }
        // Validate before the first write: a refused reuse must leave the native states untouched for the full pass.
        Object[] states=new Object[children.size()];
        for(int index=0;index<states.length;index++) {
            Object child=children.get(index);
            if(!(child instanceof View))return false;
            states[index]=t.getState.invoke(child);
            if(states[index]==null||!t.state.isInstance(states[index]))return false;
            // Card heights are read live as well: a re-measured template has no event of its own.
            if(index<=2&&(!session.stableWritten[index]||contractedHeight((View)child)!=session.stableHeight[index]))return false;
        }
        // The same live validation and repairs as a full pass: a silent native regrouping, template selection
        // or write to a leased view refuses the reuse (or is undone) in this very update.
        @SuppressWarnings("unchecked") List<View> views=(List<View>)children;
        if(!reuseVisuals(row,views,session))return false;
        for(int index=0;index<states.length;index++) {
            Object state=states[index];
            t.stateHidden.setBoolean(state,index>2);
            Object stateExtension=t.stateExtensionField.get(state);
            if(t.stateExtension.isInstance(stateExtension))t.setStateContentAlpha.invoke(stateExtension,index==0?1f:0f);
            t.stateAlpha.invoke(state,opacity(index),SOURCE);
            if(index>2)continue;
            t.stateHeight.setInt(state,session.stableHeight[index]);t.clipTop.setInt(state,0);t.clipBottom.setInt(state,0);
            t.stateY.invoke(state,session.stableY[index],SOURCE);
        }
        hideHeaderState(row);
        return true;
    }

    /** The native pass applies the group header's own view state to the header on every frame, and resets that
     * state's "hidden" flag each time it computes it. Hiding the header view afterwards therefore loses to the
     * next pass: the header flipped between visible and invisible twice per frame, and each flip ran the
     * visibility callbacks of its whole subtree (measured: 3,553 flips in one two-second scroll). Marking the
     * computed state hidden lets the native apply keep the header invisible; nothing is written to the view. */
    private void hideHeaderState(View row)throws ReflectiveOperationException{
        if(t.headerState==null)return;
        Object container=t.childrenContainer.get(row);
        Object state=container==null?null:t.headerState.get(container);
        if(state!=null&&!t.stateHidden.getBoolean(state))t.stateHidden.setBoolean(state,true);
    }

    /** True only when the pass being computed places the whole row more than one list height outside the list.
     * Uses the freshly computed native state, never the previous frame's position; unknown geometry is "visible". */
    private boolean farOutside(View row){
        if(t.stateGetY==null)return false;
        try {
            View stack=parentView(row);
            while(stack!=null&&!t.stack.isInstance(stack))stack=parentView(stack);
            int viewport=stack==null?0:stack.getHeight();
            if(viewport<=0)return false;
            Object state=t.getState.invoke(row);
            if(state==null||!t.state.isInstance(state))return false;
            Object value=t.stateGetY.invoke(state);
            if(!(value instanceof Float))return false;
            float y=(Float)value;int height=Math.max(t.stateHeight.getInt(state),row.getHeight());
            if(Float.isNaN(y)||Float.isInfinite(y)||height<0)return false;
            return y+height<-viewport||y>2f*viewport;
        }catch(ReflectiveOperationException|RuntimeException unknown){return false;}
    }

    /** ExpandableNotificationRow.dispatchDraw: sample eligibility once per draw, then apply or release the stack. */
    public Object rowDraw(Object owner,Call call)throws Throwable{
        boolean sampled=false;View[] plate=null;int[] flags=null;
        if(active()&&t.row.isInstance(owner))try {
            View row=(View)owner;
            if(t.summary.getBoolean(row)||sessions.containsKey(row)) {
                beginDraw(row);sampled=true;
                if(handoff.containsKey(row)&&rawClosed(row))handoff.remove(row);
                List<View> children=returningChildren(row);
                if(children==null)release(row);else sync(row,children,rawClosed(row));
                syncBadgeOwnership(row);
                releasePlates(row,children);
            } else {
                plate=rearContent(row);
                if(plate!=null)flags=hideForDraw(plate);
            }
        }catch(ReflectiveOperationException|RuntimeException failure){report("收起绘制",failure);plate=null;flags=null;}
        try{return call.run();}
        finally{
            if(flags!=null)restoreAfterDraw(plate,flags);
            if(sampled)endDraw();
        }
    }

    // App289: a settled rear card records only its background plate. Its content layouts are flagged invisible
    // for the duration of its own dispatchDraw (no listener, layout or invalidation sees it), so the RenderThread
    // no longer walks their nodes every frame (App286: prepareTree 3.73 vs 2.22 ms with the stack on/off).
    // A row that stops being a settled rear card is invalidated once so its content is recorded again.
    private final Map<View,Boolean> plated=new WeakHashMap<>();
    private Field viewFlags;
    private boolean platesUnsupported;
    private static final int VISIBILITY_MASK=0x0000000C,INVISIBLE_FLAG=0x00000004;

    private View[] rearContent(View row)throws ReflectiveOperationException{
        if(platesUnsupported)return null;
        Object parent=t.notificationParent.get(row);
        if(!(parent instanceof View))return null;
        List<View> children=returningChildren((View)parent);
        int index=children==null?-1:children.indexOf(row);
        if(index<1||index>2||!rawClosed((View)parent))return null;
        Object first=t.privateLayout.get(row),second=t.publicLayout.get(row);
        View[] content={first instanceof View&&((View)first).getParent()==row?(View)first:null,
                second instanceof View&&((View)second).getParent()==row?(View)second:null};
        if(content[0]==null&&content[1]==null)return null;
        plated.put(row,Boolean.TRUE);
        return content;
    }
    private int[] hideForDraw(View[] views){
        int[] saved=new int[views.length];int changed=-1;
        try{
            if(viewFlags==null){Field flags=View.class.getDeclaredField("mViewFlags");flags.setAccessible(true);viewFlags=flags;}
            for(int i=0;i<views.length;i++)if(views[i]!=null)saved[i]=viewFlags.getInt(views[i]);
            for(int i=0;i<views.length;i++)if(views[i]!=null){viewFlags.setInt(views[i],(saved[i]&~VISIBILITY_MASK)|INVISIBLE_FLAG);changed=i;}
            return saved;
        }catch(ReflectiveOperationException|RuntimeException unavailable){
            for(int i=0;i<=changed;i++)if(views[i]!=null)try{viewFlags.setInt(views[i],saved[i]);}
            catch(ReflectiveOperationException|RuntimeException ignored){}
            platesUnsupported=true;report("后卡只画背景",unavailable);return null;
        }
    }
    private void restoreAfterDraw(View[] views,int[] saved){
        for(int i=0;i<views.length;i++)if(views[i]!=null)try{
            int now=viewFlags.getInt(views[i]);
            // Only the visibility bits are ours; anything else written during the draw stays.
            viewFlags.setInt(views[i],(now&~VISIBILITY_MASK)|(saved[i]&VISIBILITY_MASK));
        }catch(ReflectiveOperationException|RuntimeException failure){report("恢复后卡内容",failure);}
    }
    /** Rows of this group that were drawn as plates but are no longer settled rear cards get their content back. */
    private void releasePlates(View summary,List<View> children)throws ReflectiveOperationException{
        if(plated.isEmpty())return;
        boolean settled=children!=null&&rawClosed(summary);
        for(View child:new ArrayList<>(plated.keySet())){
            if(t.notificationParent.get(child)!=summary)continue;
            int index=children==null?-1:children.indexOf(child);
            if(settled&&index>=1&&index<=2)continue;
            plated.remove(child);child.invalidate();
        }
    }
    private void releaseAllPlates(){
        for(View child:new ArrayList<>(plated.keySet()))child.invalidate();
        plated.clear();
    }

    /** NotificationContentView.calculateVisibleType / getVisualTypeForHeight: the three deck cards keep their contracted template. */
    public Object contentType(Object content,Call call)throws Throwable{
        if(active())try {
            Object child=t.contentOwner.get(content),parent=child==null?null:t.notificationParent.get(child);
            if(parent instanceof View) {
                List<View> children=returningChildren((View)parent);
                int index=children==null?-1:children.indexOf(child);
                if(index>=0&&index<=2)return CONTRACTED;
            }
        }catch(ReflectiveOperationException|RuntimeException failure){report("收起单卡模板",failure);}
        return call.run();
    }

    /** NotificationChildrenContainer.drawChild: clip the front card, occlude and fold the rear cards, then draw the count. */
    public boolean drawChild(Object owner,Canvas canvas,View child,Call call)throws Throwable{
        if(!active()||!t.container.isInstance(owner))return (Boolean)call.run();
        ViewGroup container=(ViewGroup)owner;
        View row=null;int saved=-1;boolean clipCleared=false;Object nativeClip=null;
        try {
            Object summary=t.parent.get(container);
            if(summary instanceof View) {
                syncBadgeOwnership((View)summary);
                if(badgeOwners.containsKey((View)summary)&&nativeOverflow(container,child))return false;
            }
            List<View> children=summary instanceof View?returningChildren((View)summary):null;
            if(children!=null) {
                row=(View)summary;
                boolean settled=rawClosed(row);
                int index=children.indexOf(child);
                if(index<0||index>2) {
                    if(settled)return false;
                    // While returning, the native expanded header keeps its own exit animation, but the folded
                    // summary mirror would cover the real first card.
                    Object overlay=t.overlayEx.get(container);
                    if(t.overlay.isInstance(overlay)&&(child==t.overlayContainer.get(overlay)||child==t.collapsedHeader.get(overlay)))return false;
                } else {
                    View first=children.get(0);
                    if(index>0&&(outerCoverage(row)>=1f||!rearVisible(row,first)))return false;
                    int firstHeight=settled?contractedHeight(first):(Integer)t.actualHeight.invoke(first);
                    if(bounds(first,container,firstHeight,front)) {
                        saved=canvas.save();
                        nativeClip=t.childClip.get(container);
                        // Bypass only the native folded-group clip for this one child; restored right after it draws.
                        t.childClip.set(container,null);clipCleared=true;
                        float frontRadius=radius(first);
                        if(index==0)canvas.clipPath(outline(front,frontRadius));
                        else foldRear(canvas,container,row,children,child,index,settled,frontRadius);
                    }
                }
            }
        }catch(ReflectiveOperationException|RuntimeException failure){report("收起遮挡",failure);}
        try{return (Boolean)call.run();}
        finally {
            if(clipCleared)try{t.childClip.set(container,nativeClip);}catch(IllegalAccessException|RuntimeException failure){report("恢复原生裁剪",failure);}
            if(saved>=0)canvas.restoreToCount(saved);
            if(row!=null)try{drawCount(canvas,row,child,container);}
            catch(ReflectiveOperationException|RuntimeException failure){report("数量角标",failure);}
        }
    }

    private boolean nativeOverflow(ViewGroup container,View child)throws ReflectiveOperationException{
        Object current=t.overflowNumber.get(container);
        if(current instanceof View)nativeOverflows.put((View)current,new WeakReference<>(container));
        if(child==current)return true;
        // updateGroupOverflow clears mOverflowNumber while the old TextView is fading out as a transient view.
        WeakReference<ViewGroup> owner=nativeOverflows.get(child);
        if(owner==null||owner.get()!=container)return false;
        int count=(Integer)t.transientCount.invoke(container);
        for(int index=0;index<count;index++)if(t.transientView.invoke(container,index)==child)return true;
        return false;
    }
    private List<View> nativeNumbers(ViewGroup container,View drawnChild,BadgeOwner owner)throws ReflectiveOperationException{
        List<View> result=new ArrayList<>();
        owner.container.clear();owner.clearBranches();
        owner.number=new WeakReference<>(null);owner.pill=new WeakReference<>(null);
        Object extension=t.containerEx.get(container);
        if(t.containerExtension.isInstance(extension)) {
            Object group=t.groupEx.get(extension);
            if(t.groupExtension.isInstance(group)) {
                Object number=t.numberView.get(group),pill=t.numberPill.get(group);
                owner.number=new WeakReference<>(number instanceof View?(View)number:null);
                owner.pill=new WeakReference<>(pill instanceof View?(View)pill:null);
                if(number instanceof View&&within((View)number,drawnChild))
                    result.add(pill instanceof View&&within((View)number,(View)pill)&&within((View)pill,drawnChild)?(View)pill:(View)number);
            }
        }
        collectNativeNumbers(drawnChild,result,owner);
        return result;
    }
    private void collectNativeNumbers(View view,List<View> result,BadgeOwner owner)throws ReflectiveOperationException{
        if(view instanceof TextView) {
            int kind=nativeNumberKind(view);
            if(kind==1||kind==2) {
                View parent=parentView(view);
                // The OEM pill contains only the number; Android's expand_button_pill also contains the arrow.
                // Keep that arrow native and exclude only Android's number, including duplicate header copies.
                View count=kind==2&&parent!=null&&nativeNumberKind(parent)==3?parent:view;
                boolean covered=false;
                for(View known:result)if(within(count,known)){covered=true;break;}
                if(!covered)result.add(count);
            }
        }
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            BadgeBranch branch=new BadgeBranch(group);owner.branches.add(branch);
            badgeBranches.put(group,owner);
            for(WeakReference<View> reference:branch.children) {
                View child=reference.get();
                if(child!=null)collectNativeNumbers(child,result,owner);
            }
            // Real methods belong to ViewGroup, including old headers retained as transient children. Test
            // contracts can declare them on the container instead; never invoke such a method on another type.
            if(t.transientCount.getDeclaringClass().isInstance(group)) {
                int count=(Integer)t.transientCount.invoke(group);
                for(int index=0;index<count;index++) {
                    Object child=t.transientView.invoke(group,index);
                    if(child instanceof View)collectNativeNumbers((View)child,result,owner);
                }
            }
        }
    }
    private int nativeNumberKind(View view){
        int id=view.getId();
        if(id<=0)return 0;
        Resources resources=view.getResources();
        Map<Integer,Integer> ids=nativeNumberIds.get(resources);
        if(ids==null){ids=new java.util.HashMap<>();nativeNumberIds.put(resources,ids);}
        Integer known=ids.get(id);
        if(known!=null)return known;
        int kind=0;
        try {
            String pkg=resources.getResourcePackageName(id),name=resources.getResourceEntryName(id);
            if("android".equals(pkg)&&"expand_button_number".equals(name))kind=1;
            else if("com.android.systemui".equals(pkg)) {
                // The group icon's top-right count ("2" on the app icon) is the same OEM number-in-a-pill as the expand button's.
                if("oplus_expand_button_number".equals(name)||"oplus_top_right_badge_number".equals(name))kind=2;
                else if("oplus_expand_button_pill".equals(name)||"oplus_capsule_top_right_badge_pill".equals(name))kind=3;
            }
        }catch(Resources.NotFoundException absent){ }
        ids.put(id,kind);
        return kind;
    }
    private static boolean within(View descendant,View ancestor){
        for(View current=descendant;current!=null;current=parentView(current))if(current==ancestor)return true;
        return false;
    }

    /** A persistent RenderNode alpha lease, not a temporary draw mutation or a hole in the underlying icon. */
    private final class BadgeLease implements View.OnAttachStateChangeListener {
        final WeakReference<View> view;
        final BadgeOwner owner;
        float nativeAlpha;
        long seen;
        BadgeLease(View view,BadgeOwner owner){this.view=new WeakReference<>(view);this.owner=owner;nativeAlpha=view.getAlpha();}
        @Override public void onViewAttachedToWindow(View view){ }
        @Override public void onViewDetachedFromWindow(View view){releaseBadgeLease(this);}
    }
    private final class BadgeOwner implements ViewTreeObserver.OnPreDrawListener,View.OnAttachStateChangeListener {
        final WeakReference<View> row;
        final Map<View,BadgeLease> nodes=new WeakHashMap<>();
        final Map<View,Boolean> targetAttachments=new WeakHashMap<>();
        final List<BadgeBranch> branches=new ArrayList<>();
        WeakReference<View> number=new WeakReference<>(null),pill=new WeakReference<>(null),overflow=new WeakReference<>(null);
        WeakReference<ViewGroup> container=new WeakReference<>(null);
        WeakReference<ViewTreeObserver> observer;
        long generation,treeEpoch,checkedEpoch=-1L,checkedBackgroundEpoch=-1L;
        BadgeOwner(View row){this.row=new WeakReference<>(row);observe(row);row.addOnAttachStateChangeListener(this);}
        void clearBranches(){
            for(BadgeBranch branch:branches){ViewGroup group=branch.group.get();
                if(group!=null&&badgeBranches.get(group)==this)badgeBranches.remove(group);}
            branches.clear();
        }
        boolean treeMatches(ViewGroup source)throws ReflectiveOperationException{
            if(checkedEpoch!=treeEpoch||checkedBackgroundEpoch!=backgroundHierarchyEpoch.get()
                    ||container.get()!=source||overflow.get()!=t.overflowNumber.get(source))return false;
            Object extension=t.containerEx.get(source),group=t.containerExtension.isInstance(extension)?t.groupEx.get(extension):null;
            Object currentNumber=t.groupExtension.isInstance(group)?t.numberView.get(group):null;
            Object currentPill=t.groupExtension.isInstance(group)?t.numberPill.get(group):null;
            if(number.get()!=currentNumber||pill.get()!=currentPill)return false;
            // Native array/transient mutation hooks invalidate the exact owning tree. Ordinary
            // fling frames only check live target pointers; they do not walk thousands of
            // unchanged RemoteViews descendants in update, pre-draw and draw again.
            // Partial/unknown framework contracts retain the structural validation fallback.
            if(!hierarchyHooks)for(BadgeBranch branch:branches)if(!branch.matches())return false;
            for(Map.Entry<View,Boolean> target:targetAttachments.entrySet())
                if(target.getKey().isAttachedToWindow()!=target.getValue())return false;
            return true;
        }
        void observe(View view){
            ViewTreeObserver current=view.getViewTreeObserver(),previous=observer==null?null:observer.get();
            if(current==previous&&current.isAlive())return;
            if(previous!=null&&previous.isAlive())previous.removeOnPreDrawListener(this);
            observer=new WeakReference<>(current);
            if(current.isAlive())current.addOnPreDrawListener(this);
        }
        @Override public boolean onPreDraw(){
            View view=row.get();
            try {
                if(view==null)
                    releaseBadgeOwner(this);
                // A hidden shade need not inspect its RemoteViews tree every window frame. Keep the lease and
                // observer so late templates are suppressed before its first visible GPU frame.
                else if(view.isShown())syncBadgeOwnership(view);
                else if(!badgeEligible(view))releaseBadgeOwner(this);
            }catch(ReflectiveOperationException|RuntimeException failure){
                report("数量角标预绘制",failure);releaseBadgeOwner(this);
            }
            return true;
        }
        @Override public void onViewAttachedToWindow(View view){observe(view);}
        @Override public void onViewDetachedFromWindow(View view){releaseBadgeOwner(this);}
    }
    /** Count leases depend on membership, not drawing order. Stable frames never re-enumerate native child indices. */
    private final class BadgeBranch {
        final WeakReference<ViewGroup> group;
        final List<WeakReference<View>> children=new ArrayList<>(),transients=new ArrayList<>();
        final boolean transientSupported;
        BadgeBranch(ViewGroup source)throws ReflectiveOperationException{
            group=new WeakReference<>(source);
            for(int index=0;index<source.getChildCount();index++)children.add(new WeakReference<>(source.getChildAt(index)));
            transientSupported=t.transientCount.getDeclaringClass().isInstance(source);
            if(transientSupported) {
                int count=(Integer)t.transientCount.invoke(source);
                for(int index=0;index<count;index++)transients.add(new WeakReference<>((View)t.transientView.invoke(source,index)));
            }
        }
        boolean matches()throws ReflectiveOperationException{
            ViewGroup source=group.get();
            if(source==null||children.size()!=source.getChildCount())return false;
            View[] nativeArray=t.nativeChildren==null?null:(View[])t.nativeChildren.get(source);
            boolean sameOrder=nativeArray!=null&&nativeArray.length>=children.size();
            if(sameOrder)for(int index=0;index<children.size();index++){
                View child=children.get(index).get();
                if(child==null||child!=nativeArray[index]||child.getParent()!=source){sameOrder=false;break;}
            }
            // A count change catches insert/remove; real membership catches same-count replacement even when
            // startViewTransition keeps a removed child's parent until its disappearance animation finishes.
            // Reordering inside the same parent cannot change the set of native count targets.
            // getChildAt is a process-wide hooked hot path on the audited ROM; do not call it once per frame
            // for every RemoteViews descendant just to rediscover the same members.
            if(!sameOrder)for(WeakReference<View> reference:children) {
                View child=reference.get();
                if(child==null||child.getParent()!=source||source.indexOfChild(child)<0)return false;
            }
            // Ordinary frames are O(n), without a hooked getChildAt or O(n) index
            // search for each descendant. A real reorder pays the fallback once.
            if(!sameOrder&&nativeArray!=null&&nativeArray.length>=children.size())
                for(int index=0;index<children.size();index++)children.set(index,new WeakReference<>(nativeArray[index]));
            if(transientSupported) {
                if(transients.size()!=(Integer)t.transientCount.invoke(source))return false;
                for(int index=0;index<transients.size();index++)if(transients.get(index).get()!=t.transientView.invoke(source,index))return false;
            }
            return true;
        }
    }
    private void setBadgeAlpha(View view,float value){
        badgeWrites++;
        try{if(Float.compare(view.getAlpha(),value)!=0)view.setAlpha(value);}
        finally{badgeWrites--;}
    }
    /** Native expansion owns card geometry, but it does not turn off the author's count-hiding request. */
    private boolean badgeEligible(View row)throws ReflectiveOperationException{
        return active()&&hideBadge&&row.isAttachedToWindow()&&eligibleChildren(row)!=null;
    }
    private void syncBadgeOwnership(View row)throws ReflectiveOperationException{
        DrawSample draw=null;
        for(int index=samples.size()-1;index>=0;index--)if(samples.get(index).row==row){draw=samples.get(index);break;}
        if(draw!=null&&draw.badgesSynced)return;
        if(!badgeEligible(row)){releaseBadgeOwner(row);return;}
        Object source=t.childrenContainer.get(row);
        if(!(source instanceof ViewGroup)){releaseBadgeOwner(row);return;}
        ViewGroup container=(ViewGroup)source;
        BadgeOwner owner=badgeOwners.get(row);
        if(owner==null){owner=new BadgeOwner(row);badgeOwners.put(row,owner);}else owner.observe(row);
        if(owner.treeMatches(container)) {
            // Native hooks keep normal writes at zero. Observe exceptional bypasses without re-reading every
            // template's resources, allocating target lists or changing a stable native hierarchy.
            for(BadgeLease lease:new ArrayList<>(owner.nodes.values())) {
                View view=lease.view.get();
                if(view==null||!view.isAttachedToWindow()){releaseBadgeLease(lease);continue;}
                if(view.getAlpha()!=0f){lease.nativeAlpha=view.getAlpha();setBadgeAlpha(view,0f);}
            }
            if(draw!=null)draw.badgesSynced=true;
            return;
        }
        long treeEpoch=owner.treeEpoch;
        long backgroundEpoch=backgroundHierarchyEpoch.get();
        List<View> numbers=nativeNumbers(container,container,owner);
        Object overflow=t.overflowNumber.get(container);
        owner.overflow=new WeakReference<>(overflow instanceof View?(View)overflow:null);
        if(overflow instanceof View) {
            nativeOverflows.put((View)overflow,new WeakReference<>(container));
            if(within((View)overflow,container))numbers.add((View)overflow);
        }
        int count=(Integer)t.transientCount.invoke(container);
        for(int index=0;index<count;index++) {
            Object transientNumber=t.transientView.invoke(container,index);
            if(transientNumber instanceof View&&nativeOverflow(container,(View)transientNumber))numbers.add((View)transientNumber);
        }
        long generation=++owner.generation;
        owner.targetAttachments.clear();
        for(View view:numbers) {
            owner.targetAttachments.put(view,view.isAttachedToWindow());
            if(!view.isAttachedToWindow())continue;
            BadgeLease lease=badgeViews.get(view);
            if(lease!=null&&lease.owner!=owner){releaseBadgeLease(lease);lease=null;}
            if(lease==null) {
                lease=new BadgeLease(view,owner);
                owner.nodes.put(view,lease);badgeViews.put(view,lease);view.addOnAttachStateChangeListener(lease);
            }
            lease.seen=generation;
            // Setter hooks track requested native alpha even when it is 0/1. This read also retains an
            // observable native write that bypassed the hook; never feed our own zero back into the baseline.
            if(view.getAlpha()!=0f)lease.nativeAlpha=view.getAlpha();
            setBadgeAlpha(view,0f);
        }
        for(BadgeLease lease:new ArrayList<>(owner.nodes.values()))if(lease.seen!=generation)releaseBadgeLease(lease);
        // Releasing an obsolete lease dirties its owner's cache. Commit this replacement tree only after
        // all old targets have been returned, so a successful rebuild is not repeated on the next frame.
        owner.container=new WeakReference<>(container);
        owner.checkedEpoch=treeEpoch;
        owner.checkedBackgroundEpoch=backgroundEpoch;
        if(draw!=null)draw.badgesSynced=true;
    }
    private void releaseBadgeLease(BadgeLease lease){
        View view=lease.view.get();
        if(view==null)return;
        if(badgeViews.get(view)==lease)badgeViews.remove(view);
        if(lease.owner.nodes.get(view)==lease)lease.owner.nodes.remove(view);
        view.removeOnAttachStateChangeListener(lease);
        lease.owner.container.clear();
        // Only undo an alpha still owned by this lease; a direct native overwrite belongs to the host.
        if(view.getAlpha()==0f)setBadgeAlpha(view,lease.nativeAlpha);
    }
    private void releaseBadgeOwner(View row){BadgeOwner owner=badgeOwners.get(row);if(owner!=null)releaseBadgeOwner(owner);}
    private void releaseBadgeOwner(BadgeOwner owner){
        View row=owner.row.get();
        if(row!=null&&badgeOwners.get(row)==owner)badgeOwners.remove(row);
        if(row!=null)row.removeOnAttachStateChangeListener(owner);
        ViewTreeObserver observer=owner.observer==null?null:owner.observer.get();
        if(observer!=null&&observer.isAlive())observer.removeOnPreDrawListener(owner);
        owner.observer=null;
        for(BadgeLease lease:new ArrayList<>(owner.nodes.values()))try{releaseBadgeLease(lease);}
        catch(RuntimeException failure){report("归还原生数量透明度",failure);}
        owner.nodes.clear();owner.clearBranches();owner.targetAttachments.clear();
    }
    private void invalidateGroups(){
        stableEvents++;
        if(!hideBadge)for(BadgeOwner owner:new ArrayList<>(badgeOwners.values()))releaseBadgeOwner(owner);
        if(t==null)return;
        for(View row:new ArrayList<>(sessions.keySet())) {
            Session session=sessions.get(row);if(session!=null)syncOuter(row,session);
            row.invalidate();
            try{Object container=t.childrenContainer.get(row);if(container instanceof View)((View)container).invalidate();}
            catch(IllegalAccessException|RuntimeException failure){report("刷新数量角标",failure);}
        }
    }

    /** Expansion, expansion-changing, swipe and animation setters: hand the group back to native state before they record it. */
    public Object transition(int kind,Object owner,Object value,Call call)throws Throwable{
        if(!active())return call.run();
        stableEvents++;
        View row=null;
        try {
            row=transitionRow(kind,owner);
            if(row!=null&&(kind==SET_EXPANDED||Boolean.TRUE.equals(value))&&sessions.containsKey(row)) {
                handoff.put(row,Boolean.TRUE);
                release(row);
                // Recompute the native child states before the setter records them, without this class's endpoint.
                replaying=true;
                try{t.updateChildren.invoke(row);}finally{replaying=false;}
            }
        }catch(Throwable failure){report("原生展开交接",failure);}
        Object result=call.run();
        try {
            if(row!=null&&rawClosed(row)) {
                handoff.remove(row);
                if(eligibleChildren(row)!=null){row.requestLayout();row.invalidate();}
            }
        }catch(ReflectiveOperationException|RuntimeException failure){report("收起完成",failure);}
        return result;
    }

    /** After the host recomputes a row background: remember its own visibility and refresh that card next time. */
    /** A change that belongs to one row ends the reuse for that row and its group only, not for every group. */
    private void unstable(Object owner){
        Session own=sessions.get(owner);
        if(own!=null)own.stable=false;
        try {
            Object parent=t.notificationParent.get(owner);
            Session group=parent==null?null:sessions.get(parent);
            if(group!=null)group.stable=false;
        }catch(ReflectiveOperationException|RuntimeException unknown){stableEvents++;}
    }
    public void backgroundUpdated(Object owner){
        if(!active()||refreshing||!t.row.isInstance(owner))return;
        unstable(owner);
        try {
            Object view=t.background.get(owner);
            if(view instanceof View)backgrounds.hostUpdated((View)view);
            dirtyCard((View)owner);
        }catch(ReflectiveOperationException|RuntimeException failure){report("原生背景更新",failure);}
    }

    public void roundnessReset(Object owner,Object sourceType){
        if(!active()||refreshing||sourceType!=roundnessSource||!t.row.isInstance(owner))return;
        unstable(owner);
        try{dirtyCard((View)owner);}catch(ReflectiveOperationException|RuntimeException failure){report("原生圆角重置",failure);}
    }

    /** OplusNotificationClicker.onClick: the closed stack's first card expands its group natively instead of opening the app. */
    public boolean click(Object view){
        if(!active()||!t.row.isInstance(view))return false;
        try {
            View child=(View)view;
            Object parent=t.notificationParent.get(child);
            if(!(parent instanceof View))return false;
            View row=(View)parent;
            List<View> children=closedChildren(row);
            if(children==null||children.get(0)!=child||!row.isAttachedToWindow())return false;
            for(View candidate:new View[]{row,child}) {
                Object menu=t.menu.get(candidate);
                if(menu!=null&&Boolean.TRUE.equals(t.menuVisible.invoke(menu)))return false;
            }
            Object listener=t.expandListener.get(row);
            if(!(listener instanceof View.OnClickListener))return false;
            clicks++;
            // SystemUI's own listener runs its checks and the native expand animation.
            try{((View.OnClickListener)listener).onClick(row);}catch(RuntimeException failure){report("原生展开监听",failure);}
            return true;
        }catch(ReflectiveOperationException|RuntimeException failure){report("原生展开点击",failure);return false;}
    }

    // ---- Eligibility and state. ----

    private List<View> eligibleChildren(View row)throws ReflectiveOperationException{
        for(int index=samples.size()-1;index>=0;index--){DrawSample sample=samples.get(index);if(sample.row!=row)continue;
            if(!sample.eligibilitySampled){sample.eligible=freshEligibleChildren(row);sample.eligibilitySampled=true;}
            return sample.eligible;
        }
        return freshEligibleChildren(row);
    }
    /** Scoped to one synchronous native update or draw, never reused across frames/events. */
    private List<View> freshEligibleChildren(View row)throws ReflectiveOperationException{
        if(!t.row.isInstance(row)||!t.summary.getBoolean(row)||t.bundle.getBoolean(row)||t.headsUp.getBoolean(row)
                ||t.launching.getBoolean(row)||t.minimized.getBoolean(row)||yes(t.publicView,row)||yes(t.guts,row)||encrypted(row))return null;
        View stack=parentView(row);
        while(stack!=null&&!t.stack.isInstance(stack))stack=parentView(stack);
        // Doze keeps the stack; native ambient dimming and privacy stay in charge.
        if(stack==null||t.ambient.get(stack)==null)return null;
        Object container=t.childrenContainer.get(row);
        if(!(container instanceof ViewGroup))return null;
        Object members=t.children.invoke(row);
        if(!(members instanceof List))return null;
        List<?> list=(List<?>)members;
        if(list.size()<2)return null;
        for(Object candidate:list)if(!(candidate instanceof View))return null;
        for(int index=0;index<Math.min(3,list.size());index++) {
            View child=(View)list.get(index);
            if(child.getParent()!=container||t.notificationParent.get(child)!=row||t.headsUp.getBoolean(child)
                    ||t.launching.getBoolean(child)||yes(t.publicView,child)||yes(t.guts,child)||encrypted(child))return null;
        }
        // Rear RemoteViews may not be measured right after a reparent; only the front must be ready.
        Object content=t.privateLayout.get(list.get(0)),front=content==null?null:t.contracted.get(content);
        if(!(front instanceof View)||((View)front).getMeasuredHeight()<=0)return null;
        @SuppressWarnings("unchecked") List<View> children=(List<View>)list;
        return children;
    }
    private boolean rawClosed(View row)throws ReflectiveOperationException{
        if(!t.row.isInstance(row)||!t.summary.getBoolean(row)||t.expanded.getBoolean(row)||t.changing.getBoolean(row)||t.swiping.getBoolean(row))return false;
        Object extension=t.rowEx.get(row);
        if(!t.extension.isInstance(extension))return false;
        float progress=t.progress.getFloat(extension);
        if(t.animating.getBoolean(extension)||progress!=0f)return false;
        Object container=t.childrenContainer.get(row);
        if(container==null)return false;
        Object overlay=t.overlayEx.get(container);
        return t.overlay.isInstance(overlay)&&!t.containerSwiping.getBoolean(container)
                &&!t.overlayAnimating.getBoolean(overlay)&&!t.overlayLocked.getBoolean(overlay);
    }
    private List<View> closedChildren(View row)throws ReflectiveOperationException{
        return !handoff.containsKey(row)&&rawClosed(row)?eligibleChildren(row):null;
    }
    private List<View> returningChildren(View row)throws ReflectiveOperationException{
        for(int index=samples.size()-1;index>=0;index--) {
            DrawSample sample=samples.get(index);
            if(sample.row!=row)continue;
            if(!sample.sampled){sample.children=freshReturningChildren(row);sample.sampled=true;}
            return sample.children;
        }
        return freshReturningChildren(row);
    }
    private List<View> freshReturningChildren(View row)throws ReflectiveOperationException{
        if(rawClosed(row))return handoff.containsKey(row)?null:eligibleChildren(row);
        if(!t.row.isInstance(row)||!t.summary.getBoolean(row)||t.expanded.getBoolean(row)||t.swiping.getBoolean(row))return null;
        Object container=t.childrenContainer.get(row);
        if(container==null||t.containerSwiping.getBoolean(container))return null;
        Object extension=t.rowEx.get(row);
        if(!t.extension.isInstance(extension))return null;
        float progress=t.progress.getFloat(extension);
        if(Float.isNaN(progress)||Float.isInfinite(progress))return null;
        Object overlay=t.overlayEx.get(container);
        if(!t.overlay.isInstance(overlay)||t.overlayLocked.getBoolean(overlay))return null;
        return t.changing.getBoolean(row)||t.animating.getBoolean(extension)||t.overlayAnimating.getBoolean(overlay)||progress>0f
                ?eligibleChildren(row):null;
    }
    private View transitionRow(int kind,Object owner)throws ReflectiveOperationException{
        Object row=kind==CONTAINER_SWIPING?t.container.isInstance(owner)?t.parent.get(owner):null
                :kind==SET_ANIMATING?t.extension.isInstance(owner)?t.extensionView.invoke(owner):null:owner;
        return t.row.isInstance(row)?(View)row:null;
    }
    private boolean encrypted(View row)throws ReflectiveOperationException{
        Object extension=t.rowEx.get(row);
        return !t.extension.isInstance(extension)||yes(t.encrypted,extension);
    }
    /** The native covered spring and the exact stack-scale owner are separate native paths.
     * Generic entry/pinch scale is never enough to claim that a row is an outer rear card. */
    private float outerCoverage(View row){
        DrawSample draw=null;
        for(int index=samples.size()-1;index>=0;index--)if(samples.get(index).row==row){draw=samples.get(index);break;}
        if(draw!=null&&draw.coverageSampled)return draw.coverage;
        float coverage=0f;
        Session session=sessions.get(row);
        OuterOwner outer=session==null?null:session.outer;
        try {
            if(!outerEligible(row)){if(outer!=null)outer.scaleBase=1f;return 0f;}
            Resources resources=row.getResources();
            Integer id=nativeCoveredProgressIds.get(resources);
            if(id==null) {
                try{id=resources.getIdentifier("stacked_notification_covered_curr_progress","id","com.android.systemui");}
                catch(RuntimeException absent){id=0;}
                // Package IDs do not change during a Resources lifetime; unknown ROMs stay native.
                nativeCoveredProgressIds.put(resources,id);
            }
            Object value=id>0?row.getTag(id):null;
            coverage=value instanceof Float&&finite((Float)value)?unit((Float)value):0f;
            if(outer!=null&&t.rowAnimation!=null){
                Object state=t.getState.invoke(row),extension=t.stateExtensionField.get(state);
                Object animation=t.rowAnimation.invoke(extension),scale=t.rowScale.invoke(animation,"key_stacked_notification");
                float x=((Number)t.scaleX.invoke(scale)).floatValue(),y=((Number)t.scaleY.invoke(scale)).floatValue();
                float actual=Math.min(row.getScaleX(),row.getScaleY());
                if(finite(x)&&finite(y)&&x>0f&&y>0f&&compressed(Math.min(x,y))&&compressed(actual))
                    outer.scaleBase=Math.min(outer.scaleBase,Math.min(x,y));
                // The keyed target can reset before the real spring. Retain its verified compressed
                // endpoint, then reveal continuously with the actual scale instead of latching hidden.
                if(outer.scaleBase<1f&&finite(actual)&&actual>0f&&actual<1f)
                    coverage=Math.max(coverage,unit((1f-actual)/(1f-outer.scaleBase)));
                else outer.scaleBase=1f;
            }
        }catch(ReflectiveOperationException|RuntimeException absent){/* Unknown native contract keeps the mini deck. */}
        if(draw!=null){draw.coverageSampled=true;draw.coverage=coverage;}
        return coverage;
    }
    private static boolean compressed(float scale){return finite(scale)&&scale>0f&&scale<.9999f;}
    private boolean outerEligible(View row)throws ReflectiveOperationException{
        return active()&&autoHideInStack&&!outerSafe&&outerStackPolicy!=null&&outerStackPolicy.manages(row)
                &&rawClosed(row)&&!handoff.containsKey(row)&&!t.headsUp.getBoolean(row)&&!t.launching.getBoolean(row)
                &&!t.minimized.getBoolean(row)&&!yes(t.publicView,row)&&!yes(t.guts,row)&&!encrypted(row);
    }
    /** A property lease also works when Android reuses the children container's display list. */
    private final class OuterOwner implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> row;WeakReference<ViewTreeObserver> observer;float coverage,scaleBase=1f;
        OuterOwner(View row){this.row=new WeakReference<>(row);ViewTreeObserver tree=row.getViewTreeObserver();
            if(tree.isAlive()){tree.addOnPreDrawListener(this);observer=new WeakReference<>(tree);}}
        @Override public boolean onPreDraw(){View view=row.get();Session session=view==null?null:sessions.get(view);
            if(session==null||session.outer!=this){remove();return true;}updateOuter(view,session);return true;}
        void remove(){ViewTreeObserver tree=observer==null?null:observer.get();if(tree!=null&&tree.isAlive())tree.removeOnPreDrawListener(this);observer=null;}
    }
    private void syncOuter(View row,Session session){
        if(active()&&autoHideInStack&&!outerSafe&&outerStackPolicy!=null){if(session.outer==null)session.outer=new OuterOwner(row);}
        else if(session.outer!=null){session.outer.remove();session.outer=null;}
        updateOuter(row,session);
    }
    private void updateOuter(View row,Session session){
        float coverage=0f;
        try{if(session.outer!=null)coverage=outerCoverage(row);}catch(RuntimeException failure){report("大堆叠后卡",failure);}
        // A reused rear RenderNode can move before updateChildrenStates. Never carry this
        // owner's alpha lease into another native group while waiting for that callback.
        if(coverage>0f||session.outer!=null&&session.outer.coverage>0f)try{
            Object members=t.children.invoke(row),container=t.childrenContainer.get(row);
            if(!(members instanceof List)||!session.matches((List<?>)members)){release(row);return;}
            for(int i=0;i<Math.min(3,session.children.size());i++){
                View child=session.children.get(i).get();
                if(child==null||child.getParent()!=container||t.notificationParent.get(child)!=row){release(row);return;}
            }
        }catch(ReflectiveOperationException|RuntimeException missing){release(row);return;}
        boolean changed=session.outer!=null&&(session.outer.coverage>=1f)!=(coverage>=1f);
        if(session.outer!=null)session.outer.coverage=coverage;
        rowAlpha.outer(row,coverage);
        if(changed)try{Object container=t.childrenContainer.get(row);if(container instanceof View)((View)container).invalidate();}
        catch(IllegalAccessException|RuntimeException ignored){ }
    }
    private boolean rearVisible(View row,View first)throws ReflectiveOperationException{
        Object background=t.background.get(first);
        if(!row.isShown()||row.getAlpha()<=.001f||!first.isShown()||first.getAlpha()<=.001f)return false;
        if(background instanceof View&&(!((View)background).isShown()||((View)background).getAlpha()<=.001f))return false;
        int height=(Integer)t.actualHeight.invoke(row);
        return height-t.rowClipTop.getInt(row)-t.rowClipBottom.getInt(row)>contractedHeight(first)/2;
    }
    private static boolean yes(Method method,Object owner)throws ReflectiveOperationException{return Boolean.TRUE.equals(method.invoke(owner));}
    private static View parentView(View view){return view.getParent() instanceof View?(View)view.getParent():null;}
    private static float density(View view){return view.getResources().getDisplayMetrics().density;}
    private int stackHeight(View row,List<View> children)throws ReflectiveOperationException{
        return stackHeight(contractedHeight(children.get(0)),children.size(),density(row));
    }
    private DrawSample sampleFor(View child){
        for(int index=samples.size()-1;index>=0;index--) {
            DrawSample sample=samples.get(index);
            if(sample.children!=null&&sample.children.contains(child))return sample;
        }
        return null;
    }
    private int contractedHeight(View child)throws ReflectiveOperationException{
        DrawSample sample=sampleFor(child);
        Integer cached=sample==null?null:sample.heights.get(child);
        if(cached!=null)return cached;
        Object content=t.privateLayout.get(child);
        int height=content==null?0:(Integer)t.minimum.invoke(content,true);
        if(sample!=null)sample.heights.put(child,height);
        return height;
    }
    private float radius(View child)throws ReflectiveOperationException{
        DrawSample sample=sampleFor(child);
        Float cached=sample==null?null:sample.radii.get(child);
        if(cached!=null)return cached;
        float radius=Math.max(0f,(Float)t.radius.invoke(child));
        if(sample!=null)sample.radii.put(child,radius);
        return radius;
    }
    private void beginDraw(View row){
        DrawSample sample=samplePool.isEmpty()?new DrawSample():samplePool.remove(samplePool.size()-1);
        sample.row=row;samples.add(sample);
    }
    private void endDraw(){
        if(samples.isEmpty())return;
        DrawSample sample=samples.remove(samples.size()-1);
        sample.clear();
        if(samplePool.size()<2)samplePool.add(sample);
    }

    // ---- Drawing. Reuse a bounded set of exact local shapes while native rows move. ----

    private Path outline(RectF bounds,float radius){
        float limit=Math.max(0f,Math.min(bounds.width(),bounds.height()))/2f;
        float r=Float.isNaN(radius)?0f:Math.max(0f,Math.min(radius,limit));
        for(int index=0;index<outlines.size();index++){
            OutlineShape known=outlines.get(index);if(known.matches(bounds,r))return known.path;
        }
        OutlineShape shape=new OutlineShape(bounds,r);
        if(outlines.size()<MAX_OUTLINES)outlines.add(shape);
        else {outlines.set(nextOutline,shape);nextOutline=(nextOutline+1)%MAX_OUTLINES;}
        return shape.path;
    }
    private void foldRear(Canvas canvas,ViewGroup container,View row,List<View> children,View child,int index,boolean settled,
            float frontRadius)throws ReflectiveOperationException{
        // The front card is translucent: occlude by its shape rather than painting under it.
        canvas.clipOutPath(outline(front,frontRadius));
        if(!bounds(child,container,(Integer)t.actualHeight.invoke(child),source))return;
        int depth=depth(children.size());
        float tail=tail(children.size(),density(row));
        float progress=settled?0f:unit(t.progress.getFloat(t.rowEx.get(row)));
        for(int layer=1;layer<index;layer++) {
            View card=children.get(layer);
            target(container,card,layer,depth,tail,progress,nearer);
            canvas.clipOutPath(outline(nearer,radius(card)*nearer.width()/Math.max(1,card.getWidth())));
        }
        target(container,child,index,depth,tail,progress,rear);
        canvas.clipPath(outline(rear,radius(child)*rear.width()/Math.max(1,child.getWidth())));
        canvas.translate(rear.left,rear.top);
        canvas.scale(rear.width()/source.width(),rear.height()/source.height());
        canvas.translate(-source.left,-source.top);
    }
    /** The closed backing rect, blended toward the live native position by the real C17 spring progress. */
    private void target(ViewGroup container,View card,int layer,int depth,float tail,float progress,RectF out)throws ReflectiveOperationException{
        if(!bounds(card,container,(Integer)t.actualHeight.invoke(card),out)){out.set(front.left,front.top,front.right,front.bottom);return;}
        backing(front,layer,depth,tail,endpoint);
        endpoint.top=endpoint.bottom-contractedHeight(card)*scale(layer);
        out.set(blend(endpoint.left,out.left,progress),blend(endpoint.top,out.top,progress),
                blend(endpoint.right,out.right,progress),blend(endpoint.bottom,out.bottom,progress));
    }
    private boolean bounds(View child,ViewGroup ancestor,int height,RectF out){
        if(child.getWidth()<=0||height<=0)return false;
        out.set(0f,0f,child.getWidth(),height);
        return mapRectInAncestor(child,ancestor,out)&&!out.isEmpty()&&finite(out.left)&&finite(out.top)&&finite(out.right)&&finite(out.bottom);
    }
    private static boolean finite(float value){return !Float.isNaN(value)&&!Float.isInfinite(value);}
    /** offsetDescendantRectToMyCoords ignores the native spring's view matrix; this includes it. */
    private boolean mapRectInAncestor(View view,ViewGroup ancestor,RectF rect){
        matrix.reset();
        for(View current=view;current!=ancestor;) {
            View parent=parentView(current);
            if(parent==null)return false;
            matrix.postConcat(current.getMatrix());
            matrix.postTranslate(current.getLeft()-parent.getScrollX(),current.getTop()-parent.getScrollY());
            current=parent;
        }
        matrix.mapRect(rect);
        return true;
    }
    private void drawCount(Canvas canvas,View row,View child,ViewGroup container)throws ReflectiveOperationException{
        if(hideBadge)return;
        List<View> children=returningChildren(row);
        if(children==null||children.get(0)!=child)return;
        Object content=t.privateLayout.get(child),contracted=content==null?null:t.contracted.get(content);
        if(!(contracted instanceof View))return;
        View icon=((View)contracted).findViewById(android.R.id.icon);
        if(icon==null||!icon.isShown())return;
        iconRect.set(0f,0f,icon.getWidth(),icon.getHeight());
        if(!mapRectInAncestor(icon,container,iconRect))return;
        float density=density(row),x=iconRect.right-density,y=iconRect.top+density;
        float progress=rawClosed(row)?0f:unit(t.progress.getFloat(t.rowEx.get(row)));
        int alpha=Math.round((1f-progress)*255f);
        if(alpha<=0)return;
        paint.setColor(BADGE_FILL);paint.setAlpha(alpha);
        canvas.drawCircle(x,y,8f*density,paint);
        paint.setColor(BADGE_TEXT);paint.setAlpha(alpha);paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize((children.size()>99?8f:10f)*density);
        paint.getFontMetrics(metrics);
        canvas.drawText(badge(children.size()),x,y-(metrics.ascent+metrics.descent)/2f,paint);
    }

    // ---- Apply and restore. Restores only undo values that still belong to this class. ----

    private void ownAlpha(View view,float opacity,List<SavedContent> saved){
        SavedContent entry=null;
        for(SavedContent candidate:saved)if(candidate.view.get()==view){entry=candidate;break;}
        if(entry==null){entry=new SavedContent(view);saved.add(entry);}
        entry.applied=opacity;
        if(view.getAlpha()!=opacity)view.setAlpha(opacity);
    }
    private void dirtyCard(View child)throws ReflectiveOperationException{
        Object row=t.notificationParent.get(child);
        Session session=row instanceof View?sessions.get(row):null;
        if(session!=null)for(Card card:session.cards)if(card.child.get()==child)card.refreshed=false;
    }
    private void sync(View row,List<View> children,boolean settled)throws ReflectiveOperationException{
        Session session=sessions.get(row);
        if(session!=null&&!session.matches(children)){release(row);session=null;}
        if(session==null){session=new Session(children);sessions.put(row,session);row.addOnAttachStateChangeListener(detach);}
        if(settled&&reuseVisuals(row,children,session))return;
        long epoch=visualEpoch.get(),backgroundEpoch=backgroundHierarchyEpoch.get();
        visualWrites++;
        try{syncVisuals(row,children,settled,session);}
        finally{visualWrites--;}
        session.visualEpoch=epoch;session.backgroundEpoch=backgroundEpoch;
    }
    /** Reapply live native properties without rebuilding an unchanged ownership plan on each fling frame. */
    private boolean reuseVisuals(View row,List<View> children,Session session)throws ReflectiveOperationException{
        if(!hierarchyHooks||!visualHooks||session.visualEpoch!=visualEpoch.get()
                ||session.backgroundEpoch!=backgroundHierarchyEpoch.get()||session.cards.size()!=Math.min(3,children.size())
                ||session.summaryBackground.get()!=t.background.get(row))return false;
        for(Card card:session.cards){
            View child=card.child.get();
            if(child==null||!card.refreshed||card.privateLayout==null||card.publicLayout==null
                    ||card.privateLayout.get()!=t.privateLayout.get(child)||card.publicLayout.get()!=t.publicLayout.get(child))return false;
            // Some native callers can inline the setters. Live template checks also catch
            // silent selection/grouping changes before reusing a visual plan.
            for(SavedLayout layout:card.layouts){View view=layout.view.get();if(view==null)continue;
                Object front=t.contracted.get(view);
                if(t.inGroup.getBoolean(view)||front!=layout.contracted.get()
                        ||front instanceof View&&(t.selectedType.getInt(view)!=CONTRACTED
                            ||((View)front).getVisibility()!=View.VISIBLE||((View)front).getAlpha()!=1f))return false;
            }
            Object background=t.background.get(child);
            if(background instanceof View){
                View view=(View)background;
                if(card.background==null||card.background.get()!=view||card.height!=contractedHeight(child)||card.radius!=radius(child)
                        ||view.getOutlineProvider()!=card.appliedOutline||!view.getClipToOutline())return false;
            }else if(card.background!=null&&card.background.get()!=null)return false;
        }
        // Privacy, parent membership and native expansion eligibility were sampled live by the caller.
        return reapplyLeases(row,session);
    }
    /** Alpha/visibility are live leases: native spring writes are repaired on every update, never frozen in a bitmap.
     * False only when a leased card is gone; nothing has been written in that case. */
    private boolean reapplyLeases(View row,Session session)throws ReflectiveOperationException{
        for(Card card:session.cards)if(card.child.get()==null)return false;
        for(int index=0;index<session.cards.size();index++){
            Card card=session.cards.get(index);View child=card.child.get();
            Object current=t.privateLayout.get(child),showing=t.showing.invoke(child);float alpha=index==0?1f:0f;
            if(current instanceof View)ownAlpha((View)current,alpha,card.contents);
            if(showing!=current&&showing instanceof View)ownAlpha((View)showing,alpha,card.contents);
        }
        Object content=t.privateLayout.get(row),showing=t.showing.invoke(row);
        if(content instanceof View)ownAlpha((View)content,0f,session.summaryContents);
        if(showing!=content&&showing instanceof View)ownAlpha((View)showing,0f,session.summaryContents);
        syncBadgeOwnership(row);visibility.reapply(row);rowAlpha.reapply(row);syncOuter(row,session);backgrounds.reapply(row);
        return true;
    }
    private void syncVisuals(View row,List<View> children,boolean settled,Session session)throws ReflectiveOperationException{
        int deck=Math.min(3,children.size());
        // A membership/order change releases the whole session above. Its first three
        // cards therefore cannot become obsolete while this exact session still matches.
        for(int index=0;index<deck;index++) {
            View child=children.get(index);
            Card card=null;
            for(Card candidate:session.cards)if(candidate.child.get()==child){card=candidate;break;}
            if(card==null){card=new Card(child,row);session.cards.add(card);}
            Object privateLayout=t.privateLayout.get(child),publicLayout=t.publicLayout.get(child);
            card.privateLayout=new WeakReference<>(privateLayout instanceof View?(View)privateLayout:null);
            card.publicLayout=new WeakReference<>(publicLayout instanceof View?(View)publicLayout:null);
            for(int slot=0;slot<(privateLayout==publicLayout?1:2);slot++) {
                Object layout=slot==0?privateLayout:publicLayout;
                if(!(layout instanceof View))continue;
                boolean known=false;
                for(SavedLayout entry:card.layouts)if(entry.view.get()==layout){known=true;break;}
                if(!known)card.layouts.add(new SavedLayout((View)layout,t.inGroup.getBoolean(layout)));
                boolean ungrouped=false;
                if(t.inGroup.getBoolean(layout)){t.setGrouped.invoke(layout,false);ungrouped=true;}
                Object contracted=t.contracted.get(layout);
                View front=contracted instanceof View?(View)contracted:null;
                for(SavedLayout entry:card.layouts)if(entry.view.get()==layout){entry.contracted=new WeakReference<>(front);break;}
                if(ungrouped||front!=null&&(t.selectedType.getInt(layout)!=CONTRACTED||front.getVisibility()!=View.VISIBLE||front.getAlpha()!=1f)) {
                    // The native collapse can pick the single-line template before its flags settle; repair that
                    // mismatch at the closed endpoint instead of re-selecting on every draw.
                    t.selectLayout.invoke(layout,false,true);
                    if(settled&&front!=null&&front.getVisibility()==View.VISIBLE&&front.getAlpha()!=1f)front.setAlpha(1f);
                    card.refreshed=false;
                }
            }
            if(!card.refreshed) {
                // A child in a group computes "no background"; without its parent it refreshes as a standalone card.
                refreshing=true;
                Object parent=t.notificationParent.get(child);
                try {
                    t.notificationParent.set(child,null);
                    t.updateBackground.invoke(child);
                    t.roundness.invoke(child,1f,1f,roundnessSource,false);
                    card.refreshed=true;
                } finally {
                    t.notificationParent.set(child,parent);
                    refreshing=false;
                }
            }
            if(settled) {
                float opacity=index==0?1f:0f;
                Object current=t.privateLayout.get(child),showing=t.showing.invoke(child);
                if(current instanceof View)ownAlpha((View)current,opacity,card.contents);
                if(showing!=current&&showing instanceof View)ownAlpha((View)showing,opacity,card.contents);
            }
            Object background=t.background.get(child);
            if(background instanceof View)outline(card,(View)background,settled?contractedHeight(child):(Integer)t.actualHeight.invoke(child),radius(child));
        }
        Object summaryContent=t.privateLayout.get(row),showingSummary=t.showing.invoke(row);
        if(summaryContent instanceof View)ownAlpha((View)summaryContent,0f,session.summaryContents);
        if(showingSummary!=summaryContent&&showingSummary instanceof View)ownAlpha((View)showingSummary,0f,session.summaryContents);
        ViewGroup container=(ViewGroup)t.childrenContainer.get(row);
        syncBadgeOwnership(row);
        if(settled) {
            List<View> hidden=new ArrayList<>();
            int count=container.getChildCount();
            View[] nativeChildren=t.nativeChildren==null?null:(View[])t.nativeChildren.get(container);
            boolean direct=nativeChildren!=null&&nativeChildren.length>=count;
            for(int index=0;index<count;index++) {
                // Read the current native membership without entering the global status-icon
                // index hook thousands of times during a notification fling. Never retain or
                // mutate the array; unsupported frameworks keep the public accessor fallback.
                View view=direct?nativeChildren[index]:container.getChildAt(index);
                if(view!=null&&!t.row.isInstance(view))hidden.add(view);
            }
            for(int index=3;index<children.size();index++)hidden.add(children.get(index));
            visibility.hide(row,hidden,true);
            visibility.hide(row,children.subList(1,deck),false);
            rowAlpha.apply(row,children);
        }
        syncOuter(row,session);
        Object summaryBackground=t.background.get(row);
        session.summaryBackground=new WeakReference<>(summaryBackground instanceof View?(View)summaryBackground:null);
        if(!(summaryBackground instanceof View))return;
        List<View> childBackgrounds=new ArrayList<>();
        for(View child:children){Object background=t.background.get(child);if(background instanceof View)childBackgrounds.add((View)background);}
        backgrounds.apply(row,(View)summaryBackground,childBackgrounds);
    }
    private void outline(Card card,View background,int height,float radius){
        if(card.background==null||card.background.get()!=background) {
            restoreOutline(card);
            card.background=new WeakReference<>(background);
            card.originalOutline=background.getOutlineProvider();card.originalClip=background.getClipToOutline();
            card.appliedOutline=new CardOutline(card);
        } else if(background.getOutlineProvider()!=card.appliedOutline) {
            // The host replaced the provider since the last frame: that is the value to restore later.
            card.originalOutline=background.getOutlineProvider();card.originalClip=background.getClipToOutline();
        }
        boolean changed=card.height!=height||card.radius!=radius||background.getOutlineProvider()!=card.appliedOutline;
        card.height=height;card.radius=radius;
        if(background.getOutlineProvider()!=card.appliedOutline)background.setOutlineProvider(card.appliedOutline);
        background.setClipToOutline(true);
        if(changed)background.invalidateOutline();
    }
    private static void restoreContents(List<SavedContent> saved,Object showing,float nativeAlpha){
        for(SavedContent entry:saved) {
            View view=entry.view.get();
            if(view==null||view.getAlpha()!=entry.applied)continue;
            view.setAlpha(view==showing&&finite(nativeAlpha)?unit(nativeAlpha):entry.alpha);
        }
    }
    private static void restoreOutline(Card card){
        View view=card.background==null?null:card.background.get();
        if(view!=null&&view.getOutlineProvider()==card.appliedOutline) {
            view.setOutlineProvider(card.originalOutline);view.setClipToOutline(card.originalClip);view.invalidateOutline();
        }
        card.background=null;card.appliedOutline=null;
    }
    private void restoreCard(Card card)throws ReflectiveOperationException{
        restoreOutline(card);
        View child=card.child.get();
        if(child==null){restoreContents(card.contents,null,Float.NaN);return;}
        // C17's setter returns early when its property already holds the target, so restoring a stale 0 would leave
        // an expanded card transparent: the showing layout takes the native group content alpha instead.
        Object extension=t.rowEx.get(child);
        restoreContents(card.contents,t.showing.invoke(child),t.extension.isInstance(extension)?t.contentAlpha.getFloat(extension):Float.NaN);
        if(t.notificationParent.get(child)!=card.owner.get())return;
        for(SavedLayout entry:card.layouts) {
            View layout=entry.view.get();
            if(layout==null||t.inGroup.getBoolean(layout)==entry.grouped)continue;
            t.setGrouped.invoke(layout,entry.grouped);
            t.selectLayout.invoke(layout,false,true);
        }
        t.resetRoundness.invoke(child,roundnessSource,false);
        t.updateBackground.invoke(child);
    }
    private void release(View row){
        Session session=sessions.remove(row);
        if(session==null)return;
        releases++;stableEvents++;
        try {
            if(session.outer!=null)session.outer.remove();
            visibility.restore(row);
            rowAlpha.restore(row);
            backgrounds.restore(row);
            for(Card card:session.cards)try{restoreCard(card);}catch(ReflectiveOperationException|RuntimeException failure){report("恢复原生卡片",failure);}
            restoreContents(session.summaryContents,null,Float.NaN);
        } finally {
            row.removeOnAttachStateChangeListener(detach);
            row.invalidate();
            try{Object container=t.childrenContainer.get(row);if(container instanceof View)((View)container).invalidate();}
            catch(IllegalAccessException|RuntimeException ignored){ }
        }
    }
    private void releaseAll(){
        if(t==null)return;
        for(BadgeOwner owner:new ArrayList<>(badgeOwners.values()))releaseBadgeOwner(owner);
        for(View row:new ArrayList<>(sessions.keySet()))try{release(row);}catch(RuntimeException failure){report("关闭时恢复",failure);}
        handoff.clear();nativeOverflows.clear();nativeNumberIds.clear();nativeCoveredProgressIds.clear();
        outlines.clear();nextOutline=0;
        releaseAllPlates();
    }
    private final View.OnAttachStateChangeListener detach=new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view){ }
        @Override public void onViewDetachedFromWindow(View view){
            try{releaseBadgeOwner(view);release(view);}catch(RuntimeException failure){report("收起状态释放",failure);}
            handoff.remove(view);
        }
    };

    /** Host-owned views hidden by the stack; restores only what is still in the state this class left it in. */
    static final class Visibility {
        private static final class Saved {
            final WeakReference<View> view;final int visibility,accessibility;boolean owned;
            Saved(View view){this.view=new WeakReference<>(view);visibility=view.getVisibility();accessibility=view.getImportantForAccessibility();}
        }
        private final Map<View,Map<View,Saved>> snapshots=new WeakHashMap<>();
        void hide(View row,List<View> views,boolean invisible){
            Map<View,Saved> saved=snapshots.get(row);
            if(saved==null){saved=new WeakHashMap<>();snapshots.put(row,saved);}
            for(View view:views) {
                Saved entry=saved.get(view);
                if(entry==null){entry=new Saved(view);saved.put(view,entry);}
                if(invisible&&view.getVisibility()!=View.GONE) {
                    entry.owned=true;
                    if(view.getVisibility()!=View.INVISIBLE)view.setVisibility(View.INVISIBLE);
                }
                if(view.getImportantForAccessibility()!=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
                    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
        void reapply(View row){
            Map<View,Saved> saved=snapshots.get(row);if(saved==null)return;
            for(Saved entry:saved.values()){
                View view=entry.view.get();if(view==null)continue;
                if(entry.owned&&view.getVisibility()!=View.GONE&&view.getVisibility()!=View.INVISIBLE)view.setVisibility(View.INVISIBLE);
                if(view.getImportantForAccessibility()!=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
                    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
        void restore(View row){
            Map<View,Saved> saved=snapshots.remove(row);
            if(saved==null)return;
            for(Saved entry:saved.values()) {
                View view=entry.view.get();
                if(view==null)continue;
                if(entry.owned&&view.getVisibility()==View.INVISIBLE)view.setVisibility(entry.visibility);
                if(view.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
                    view.setImportantForAccessibility(entry.accessibility);
            }
        }
    }

    /** One background owner across the OEM's animation-end switch: the summary's hidden, the children's shown. */
    static final class Backgrounds {
        private static final class Saved {
            final WeakReference<View> view;final int forced;int host;
            Saved(View view,int forced){this.view=new WeakReference<>(view);this.forced=forced;host=view.getVisibility();}
        }
        private final Map<View,List<Saved>> owners=new WeakHashMap<>();
        private final Map<View,Saved> byBackground=new WeakHashMap<>();
        void apply(View owner,View summary,List<View> children){
            List<Saved> saved=owners.get(owner);
            boolean same=saved!=null&&saved.size()==children.size()+1&&saved.get(0).view.get()==summary;
            for(int index=0;same&&index<children.size();index++)same=saved.get(index+1).view.get()==children.get(index);
            if(!same) {
                restore(owner);
                saved=new ArrayList<>();
                saved.add(new Saved(summary,View.INVISIBLE));
                for(View child:children)saved.add(new Saved(child,View.VISIBLE));
                owners.put(owner,saved);
                for(Saved entry:saved){View view=entry.view.get();if(view!=null)byBackground.put(view,entry);}
            }
            for(Saved entry:saved) {
                View view=entry.view.get();
                if(view!=null&&view.getVisibility()!=View.GONE&&view.getVisibility()!=entry.forced)view.setVisibility(entry.forced);
            }
        }
        void reapply(View owner){
            List<Saved> saved=owners.get(owner);if(saved==null)return;
            for(Saved entry:saved){View view=entry.view.get();
                if(view!=null&&view.getVisibility()!=View.GONE&&view.getVisibility()!=entry.forced)view.setVisibility(entry.forced);}
        }
        /** Called only after the host recomputed the visibility, never to snapshot this class's own override. */
        void hostUpdated(View background){Saved entry=byBackground.get(background);if(entry!=null)entry.host=background.getVisibility();}
        void restore(View owner){
            List<Saved> saved=owners.remove(owner);
            if(saved==null)return;
            for(Saved entry:saved) {
                View view=entry.view.get();
                if(view==null)continue;
                if(byBackground.get(view)==entry)byBackground.remove(view);
                if(view.getVisibility()==entry.forced)view.setVisibility(entry.host);
            }
        }
    }

    /** Child row opacity while stacked; restores the freshest host value on release. */
    static final class RowAlpha {
        private static final class Saved {
            final WeakReference<View> view,owner;float host,applied,coverage;long seen;int index;
            Saved(View view,View owner){this.view=new WeakReference<>(view);this.owner=new WeakReference<>(owner);host=view.getAlpha();applied=host;}
        }
        private static final class Session {final Map<View,Saved> rows=new WeakHashMap<>();long generation;}
        private final Map<View,Session> owners=new WeakHashMap<>();
        private final Map<View,Saved> views=new WeakHashMap<>();private int writes;
        boolean writing(){return writes!=0;}
        boolean empty(){return views.isEmpty();}
        Saved rear(View view){Saved entry=views.get(view);return entry!=null&&entry.index>0?entry:null;}
        View coveredOwner(View view){Saved entry=views.get(view);return entry!=null&&entry.coverage>0f?entry.owner.get():null;}
        float nativeAlpha(View view,float requested){Saved entry=views.get(view);if(entry==null||entry.index==0)return requested;entry.host=requested;return entry.applied;}
        private void write(View view,float alpha){if(view.getAlpha()!=alpha){writes++;try{view.setAlpha(alpha);}finally{writes--;}}}
        void apply(View owner,List<View> rows){
            Session session=owners.get(owner);
            if(session==null){session=new Session();owners.put(owner,session);}
            long generation=++session.generation;
            for(int index=0;index<rows.size();index++) {
                View view=rows.get(index);
                Saved entry=session.rows.get(view);
                if(entry==null){entry=new Saved(view,owner);session.rows.put(view,entry);views.put(view,entry);}
                entry.seen=generation;entry.index=index;
                if(view.getAlpha()!=entry.applied)entry.host=view.getAlpha();
                entry.applied=opacity(index)*(1f-entry.coverage);
                write(view,entry.applied);
            }
            for(Iterator<Saved> iterator=session.rows.values().iterator();iterator.hasNext();) {
                Saved entry=iterator.next();
                if(entry.seen!=generation){restore(entry);iterator.remove();}
            }
        }
        void reapply(View owner){Session session=owners.get(owner);if(session==null)return;
            for(Saved entry:session.rows.values()){View view=entry.view.get();if(view==null)continue;
                if(view.getAlpha()!=entry.applied)entry.host=view.getAlpha();
                entry.applied=opacity(entry.index)*(1f-entry.coverage);write(view,entry.applied);}}
        void outer(View owner,float coverage){Session session=owners.get(owner);if(session==null)return;
            for(Saved entry:session.rows.values()){View view=entry.view.get();if(view==null)continue;
                if(view.getAlpha()!=entry.applied)entry.host=view.getAlpha();
                entry.coverage=entry.index==1||entry.index==2?coverage:0f;entry.applied=opacity(entry.index)*(1f-entry.coverage);write(view,entry.applied);}}
        private void restore(Saved entry){View view=entry.view.get();if(view!=null){views.remove(view);entry.coverage=0f;if(view.getAlpha()==entry.applied)write(view,entry.host);}}
        void restore(View owner){Session session=owners.remove(owner);if(session!=null)for(Saved entry:session.rows.values())restore(entry);}
    }
}
