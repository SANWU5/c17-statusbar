package dev.puitheme;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.MeasurePassDelegate;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/** Exercises the real bridge/backends; constraint assertions are LP contracts, not a solver claim. */
public final class NetworkSharedSlotLayoutCheck {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static final class Mobile extends ViewGroup implements StatusIconDisplayable {
        Mobile() { super(null); }
        public String getSlot() { return "stacked_mobile"; }
        public boolean isIconVisible() { return true; }
        public boolean isIconBlocked() { return false; }
    }
    private static NetworkSharedSlotLayout bridge() throws Exception {
        NetworkSharedSlotLayout value = new NetworkSharedSlotLayout();
        value.resolve(NetworkSharedSlotLayoutCheck.class.getClassLoader()); value.configure(true); return value;
    }
    private static void field(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    public static final class Coordinates {
        int x;
        public long root(long ignored) { return (long) Float.floatToIntBits(x) << 32; }
    }
    public static final class Node extends LayoutNode {
        Object owner, parent;
        final Coordinates coordinates = new Coordinates();
        Node(String name, int width, Object owner, Object parent) { super(name, width); this.owner = owner; this.parent = parent; }
        public Object owner() { return owner; }
        public Object parentNode() { return parent; }
        public Coordinates coordinates() { return coordinates; }
    }
    public static final class Measured extends MeasurePassDelegate {
        int width;
        Measured(LayoutNode node) { super(node); width=node.width; }
        public int getWidth() { return width; }
    }
    private static Object[] placement(Object[] children, int[] positions, int width) {
        return new Object[]{children, new Object(), 0, positions, width, 80, null, 0, children.length, 0};
    }
    /** The fixture packs four fields; production resolves Compose's own codec. */
    public static final class Constraints {
        static boolean fail;
        public static long create(int minW,int maxW,int minH,int maxH){
            if(fail||minW<0||minW>maxW||minH<0||minH>maxH||maxW>65535||maxH>65535)throw new IllegalArgumentException("constraints");
            return (long)minW|((long)maxW<<16)|((long)minH<<32)|((long)maxH<<48);
        }
        public static int minW(long value){return (int)(value&65535);}
        public static int maxW(long value){return (int)((value>>>16)&65535);}
        public static int minH(long value){return (int)((value>>>32)&65535);}
        public static int maxH(long value){return (int)((value>>>48)&65535);}
    }
    private static void constraints(NetworkIconOrder order)throws Exception{
        field(order,"constraintMinWidth",Constraints.class.getMethod("minW",long.class));
        field(order,"constraintMaxWidth",Constraints.class.getMethod("maxW",long.class));
        field(order,"constraintMinHeight",Constraints.class.getMethod("minH",long.class));
        field(order,"constraintMaxHeight",Constraints.class.getMethod("maxH",long.class));
        field(order,"createConstraints",Constraints.class.getMethod("create",int.class,int.class,int.class,int.class));
    }
    /** Native Row consumes remaining parent width in child order before it places either child. */
    public static final class BoundedPolicy implements androidx.compose.ui.layout.MeasurePolicy {
        final NetworkIconOrder order;final Node row,signal,label;final Measured measuredSignal,measuredLabel;
        int labelNatural=78,lastWidth,lastMinHeight,lastMaxHeight;boolean fail;
        BoundedPolicy(NetworkIconOrder order,Node row,Node signal,Node label,Measured measuredSignal,Measured measuredLabel){
            this.order=order;this.row=row;this.signal=signal;this.label=label;this.measuredSignal=measuredSignal;this.measuredLabel=measuredLabel;
        }
        public java.util.Map<String,Integer> measure(Object scope,List<?> children,long constraints) {
            if(fail)throw new IllegalStateException("native measure failure");
            int maximum=Constraints.maxW(constraints);lastMinHeight=Constraints.minH(constraints);lastMaxHeight=Constraints.maxH(constraints);
            measuredSignal.width=Math.min(72,maximum);
            measuredLabel.width=Math.min(labelNatural+16,Math.max(0,maximum-measuredSignal.width));
            int[] positions={0,measuredSignal.width};
            Object[] placed=order.placeHelperArguments(this,placement(children.toArray(),positions,measuredSignal.width+measuredLabel.width));
            int[] actual=(int[])placed[3];lastWidth=(Integer)placed[4];
            signal.coordinates.x=row.coordinates.x+actual[0];label.coordinates.x=row.coordinates.x+actual[1];
            java.util.Map<String,Integer> result=new java.util.LinkedHashMap<>();result.put("signal",actual[0]);result.put("type",actual[1]);return result;
        }
        public int minIntrinsicWidth(Object scope,List<?> children,int height){return 72+labelNatural+16;}
    }
    private static final class ConstrainedMobile extends ViewGroup implements StatusIconDisplayable {
        androidx.compose.ui.layout.MeasurePolicy policy;BoundedPolicy nativePolicy;List<?> children;
        ConstrainedMobile(){super(null);}
        public String getSlot(){return "stacked_mobile";}
        public boolean isIconVisible(){return true;}
        public boolean isIconBlocked(){return false;}
        @Override protected void onMeasure(int width,int height){
            int maximum=Math.max(0,View.MeasureSpec.getSize(width)-8);
            policy.measure(null,children,Constraints.create(0,maximum,View.MeasureSpec.getSize(height),View.MeasureSpec.getSize(height)));
            setMeasuredDimension(View.resolveSize(nativePolicy.lastWidth+8,width),80);
        }
    }
    private static final class BoundedFixture {
        final NetworkSharedSlotLayout bridge;final StatusIconContainer container=new StatusIconContainer();
        final ConstrainedMobile mobile=new ConstrainedMobile();View owner=new View(null);
        final NetworkIconOrder order=new NetworkIconOrder();final Node row,signal,label;
        BoundedFixture()throws Exception{this(NetworkSharedSlotLayoutCheck.bridge());}
        BoundedFixture(NetworkSharedSlotLayout bridge)throws Exception{
        this.bridge=bridge;container.addView(mobile);mobile.addView(owner);order.sharedLayout(bridge);
        order.resolve(NetworkSharedSlotLayoutCheck.class.getClassLoader());order.resolveCompose(NetworkSharedSlotLayoutCheck.class.getClassLoader());
        android.os.Bundle settings=new android.os.Bundle();settings.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED,true);order.configure(settings);
        field(order,"nodeOwner",Node.class.getMethod("owner"));field(order,"nodeParent",Node.class.getMethod("parentNode"));
        field(order,"nodeCoordinator",Node.class.getMethod("coordinates"));field(order,"coordinatorPosition",Coordinates.class.getMethod("root",long.class));
        field(order,"placeableWidth",Measured.class.getMethod("getWidth"));
        constraints(order);
        row=new Node("row",166,owner,null);signal=new Node("signal",72,owner,row);label=new Node("type",94,owner,row);
        row.coordinates.x=8;signal.coordinates.x=8;label.coordinates.x=80;
        Modifier sm=(Modifier)order.signalModifier(new Modifier.Element()),lm=(Modifier)order.textModifier(new Modifier.Element());
        signal.setModifier(sm);label.setModifier(lm);order.nodeChanged(signal,sm);order.nodeChanged(label,lm);
        Measured measuredSignal=new Measured(signal),measuredLabel=new Measured(label);
        mobile.children=Arrays.asList(measuredSignal,measuredLabel);mobile.nativePolicy=new BoundedPolicy(order,row,signal,label,measuredSignal,measuredLabel);
        Method create=NetworkIconOrder.class.getDeclaredMethod("createPolicy",Object.class);create.setAccessible(true);
        mobile.policy=(androidx.compose.ui.layout.MeasurePolicy)create.invoke(order,mobile.nativePolicy);
        external(400);
        }
        void external(int width){
            bridge.beginNativeMeasure(container);
            try{mobile.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));}
            finally{bridge.endNativeMeasure(container);}
        }
        void internal(int width,int height){
            bridge.beginHullMeasure(mobile);
            try{mobile.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));}
            finally{bridge.endHullMeasure(mobile);}
        }
        int target(){NetworkSharedSlotLayout.Frame frame=bridge.frame(mobile);int hull=frame.signalWidthPx+frame.labelWidthPx-7;
            check(bridge.apply(frame,hull,73,1,true,true,true),"negative spacing retains both native fragments");return hull;}
        Object backend()throws Exception{return ((java.util.Map<?,?>)field(order,"sharedRows")).get(mobile);}
        void close(){order.releaseRuntime();bridge.releaseRuntime();}
    }
    private static void constrainedContentFeedback() throws Exception {
        BoundedFixture f=new BoundedFixture();NetworkSharedSlotLayout bridge=f.bridge;ConstrainedMobile mobile=f.mobile;
        check(mobile.getMeasuredWidth()==174&&bridge.frame(mobile).labelWidthPx==94,"native constraints establish 78px ink plus 16px label padding and 80px signal budget");
        for(int cycle=0;cycle<12;cycle++){
            int hull=f.target();f.internal(hull,80);
            check(bridge.frame(mobile).labelWidthPx==94,"our compact EXACT hull must not become a smaller natural label budget");
            check(mobile.getMeasuredWidth()==167,"repeated capsule traversals keep the intended overlap without shrinking the text or expanding its hull");
            mobile.policy.measure(null,mobile.children,Constraints.create(0,159,80,80));
            check(bridge.frame(mobile).labelWidthPx==94,"Compose-only delayed remeasure reuses real bounds, never the last 167px owned hull");
        }
        mobile.nativePolicy.labelNatural=120;f.internal(167,90);
        check(bridge.frame(mobile).labelWidthPx==136,"larger text is freshly measured against actual parent capacity, not frozen old natural width");
        check(mobile.nativePolicy.lastMinHeight==90&&mobile.nativePolicy.lastMaxHeight==90,"width protection keeps the current height constraints");
        f.internal(f.target(),80);check(mobile.getMeasuredWidth()==209,"new larger text receives updated overlap hull");
        mobile.nativePolicy.labelNatural=30;f.internal(209,80);
        check(bridge.frame(mobile).labelWidthPx==46,"smaller text can reduce its natural budget");
        f.internal(f.target(),80);check(mobile.getMeasuredWidth()==119,"smaller text receives smaller overlap hull");
        mobile.nativePolicy.labelNatural=78;f.external(133);
        check(bridge.frame(mobile).labelWidthPx==53,"real capsule constraint still limits the native remaining width immediately");
        check(mobile.nativePolicy.lastWidth<=125,"native Row result obeys narrower real parent even while previous shared target remains installed");
        f.internal(f.target(),80);check(bridge.frame(mobile).labelWidthPx==53&&mobile.getMeasuredWidth()==126,"internal compact measure cannot bypass the latest actual external limit");
        f.external(400);check(bridge.frame(mobile).labelWidthPx==94,"real parent expansion restores full natural text");
        mobile.nativePolicy.fail=true;
        try{f.internal(f.target(),80);throw new AssertionError("native exception missing");}catch(IllegalStateException expected){}
        check((Integer)field(f.backend(),"hullMeasureDepth")==0,"native failure leaves no hull scope behind");
        mobile.nativePolicy.fail=false;f.external(133);
        check(bridge.frame(mobile).labelWidthPx==53,"external limits still update after failed internal measure");
        f.close();
    }
    private static void constraintsLifecycle()throws Exception{
        BoundedFixture f=new BoundedFixture();f.internal(f.target(),80);Object old=f.backend();
        f.bridge.beginHullMeasure(f.mobile);f.bridge.beginHullMeasure(f.mobile);
        try{
            f.mobile.removeView(f.owner);f.owner=new View(null);f.mobile.addView(f.owner);
            f.row.owner=f.owner;f.signal.owner=f.owner;f.label.owner=f.owner;
            f.mobile.measure(View.MeasureSpec.makeMeasureSpec(167,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));
            check(f.backend()!=old,"same Compose Row with replacement Android owner receives fresh ownership");
            check(f.bridge.frame(f.mobile)==null,"replacement during owned measure cannot capture compressed width as fresh native bounds");
            check((Integer)field(old,"hullMeasureDepth")==2&&(Integer)field(f.backend(),"hullMeasureDepth")==2,"new backend inherits all active scopes without abandoning old participant");
        }finally{f.bridge.endHullMeasure(f.mobile);f.bridge.endHullMeasure(f.mobile);}
        check((Integer)field(old,"hullMeasureDepth")==0&&(Integer)field(f.backend(),"hullMeasureDepth")==0,"replacement scope finally closes both backend identities");
        f.external(400);check(f.bridge.frame(f.mobile).labelWidthPx==94,"replacement recovers only from real native measure");
        f.internal(f.target(),80);
        f.bridge.configure(false);f.bridge.configure(true);
        f.internal(167,80);check(f.bridge.frame(f.mobile)==null,"disable drops old constraints; reopening inside a compact measure stays unknown");
        f.external(133);check(f.bridge.frame(f.mobile).labelWidthPx==53,"re-enabled row accepts new actual native bound");
        f.external(400);f.internal(f.target(),80);
        long cached=Constraints.create(0,159,80,80);Constraints.fail=true;
        try{f.mobile.policy.measure(null,f.mobile.children,cached);}finally{Constraints.fail=false;}
        check(f.bridge.frame(f.mobile)==null,"unencodable replacement constraints cannot pollute captured natural width");
        f.external(400);check(f.bridge.frame(f.mobile).labelWidthPx==94,"codec failure can recover on actual native traversal");
        field(f.order,"createConstraints",null);
        check(!f.bridge.apply(f.bridge.frame(f.mobile),167,73,1,true,true,true),"missing native constraint codec rejects overlap instead of guessing an unbounded width");
        f.close();
    }
    private static void replacementAfterCompactReplay() throws Exception {
        BoundedFixture f=new BoundedFixture();
        f.internal(f.target(),80);
        Object old=f.backend();
        f.mobile.removeView(f.owner);f.owner=new View(null);f.mobile.addView(f.owner);
        f.row.owner=f.owner;f.signal.owner=f.owner;f.label.owner=f.owner;
        f.mobile.policy.measure(null,f.mobile.children,Constraints.create(0,159,80,80));
        check(f.backend()!=old,"outside-scope replacement creates a fresh row backend");
        check(f.bridge.frame(f.mobile)==null,"replacement must not treat Compose replay of compact hull as native measurement");
        f.external(400);check(f.bridge.frame(f.mobile).labelWidthPx==94,"real traversal recovers full label budget after replacement");
        f.internal(f.target(),80);
        f.bridge.configure(false);f.bridge.configure(true);
        f.mobile.policy.measure(null,f.mobile.children,Constraints.create(0,159,80,80));
        check(f.bridge.frame(f.mobile)==null,"disable and enable cannot erase host's compact-hull witness");
        f.external(400);check(f.bridge.frame(f.mobile).labelWidthPx==94,"real traversal recovers after disable and enable");
        f.internal(f.target(),80);f.mobile.nativePolicy.fail=true;
        try{f.external(133);throw new AssertionError("native exception missing");}catch(IllegalStateException expected){}
        f.mobile.nativePolicy.fail=false;
        f.mobile.policy.measure(null,f.mobile.children,Constraints.create(0,159,80,80));
        check(f.bridge.frame(f.mobile)==null,"failed native traversal cannot publish or reuse captured native constraints");
        f.external(400);check(f.bridge.frame(f.mobile).labelWidthPx==94,"successful native traversal restores failed measurement");
        f.close();
    }
    private static void failedParentTraversal() throws Exception {
        BoundedFixture f=new BoundedFixture();BoundedFixture other=new BoundedFixture(f.bridge);
        f.internal(f.target(),80);other.internal(other.target(),80);
        f.bridge.beginNativeMeasure(f.container);
        Object old;
        try {
            f.mobile.measure(View.MeasureSpec.makeMeasureSpec(133,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));
            old=f.backend();
            check((Boolean)field(old,"nativeConstraintsKnown"),"first Row completed before parent failure");
            f.mobile.removeView(f.owner);f.owner=new View(null);f.mobile.addView(f.owner);
            f.row.owner=f.owner;f.signal.owner=f.owner;f.label.owner=f.owner;
            f.mobile.policy.measure(null,f.mobile.children,Constraints.create(0,125,80,80));
            check(f.backend()!=old,"same traversal can replace a participating Row");
            f.container.removeView(f.mobile);
        } finally { f.bridge.endNativeMeasure(f.container,false); }
        check(!(Boolean)field(old,"nativeConstraintsKnown"),"failed parent invalidates earlier replaced participant");
        check(!(Boolean)field(f.backend(),"nativeConstraintsKnown"),"failed parent invalidates replacement even after host detach");
        check(other.bridge.frame(other.mobile).labelWidthPx==94,"other root's successful constraints are untouched");
        f.container.addView(f.mobile);
        f.mobile.policy.measure(null,f.mobile.children,Constraints.create(0,159,80,80));
        check(f.bridge.frame(f.mobile)==null,"failed parent constraints cannot be reused on Compose-only replay");
        f.external(400);check(f.bridge.frame(f.mobile).labelWidthPx==94,"later successful native traversal recovers participant");
        f.close();other.close();
    }
    /** Compose localToRoot already includes the Row's position, unlike a child-local coordinate. */
    @SuppressWarnings("unchecked") private static void composeRootCoordinates() throws Exception {
        NetworkSharedSlotLayout bridge=bridge();StatusIconContainer container=new StatusIconContainer();
        Mobile mobile=new Mobile();View owner=new View(null);container.addView(mobile);mobile.addView(owner);
        owner.layout(7,0,181,80);owner.setTranslationX(19);
        NetworkIconOrder order=new NetworkIconOrder();order.sharedLayout(bridge);
        order.resolve(NetworkSharedSlotLayoutCheck.class.getClassLoader());order.resolveCompose(NetworkSharedSlotLayoutCheck.class.getClassLoader());
        android.os.Bundle settings=new android.os.Bundle();settings.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED,true);order.configure(settings);
        field(order,"nodeOwner",Node.class.getMethod("owner"));field(order,"nodeParent",Node.class.getMethod("parentNode"));
        field(order,"nodeCoordinator",Node.class.getMethod("coordinates"));field(order,"coordinatorPosition",Coordinates.class.getMethod("root",long.class));
        field(order,"placeableWidth",Measured.class.getMethod("getWidth"));
        Node row=new Node("row",166,owner,null),signal=new Node("signal",72,owner,row),label=new Node("type",94,owner,row);
        row.coordinates.x=8;signal.coordinates.x=8;label.coordinates.x=80;
        Modifier sm=(Modifier)order.signalModifier(new Modifier.Element()),lm=(Modifier)order.textModifier(new Modifier.Element());
        signal.setModifier(sm);label.setModifier(lm);order.nodeChanged(signal,sm);order.nodeChanged(label,lm);
        Object[] children={new Measured(signal),new Measured(label)};
        Method measurement=NetworkIconOrder.class.getDeclaredMethod("sharedMeasurement",Object.class,List.class);measurement.setAccessible(true);
        Object policy=new Object();ThreadLocal<Object> active=(ThreadLocal<Object>)field(order,"sharedMeasure");
        active.set(measurement.invoke(order,policy,Arrays.asList(children)));
        Object[] nativePlacement=placement(children,new int[]{0,72},166);order.placeHelperArguments(policy,nativePlacement);
        NetworkSharedSlotLayout.Frame frame=bridge.frame(mobile);
        check(frame.currentSignalLeftPx==0,"localToRoot signal includes Row origin exactly once while owner offset is preserved");
        check(frame.signalWidthPx==87&&frame.nativeHostWidthPx==181,"physical owner prefix stays part of the native budget");
        check(bridge.apply(frame,181,87,1,true,true,true),"shared target retains signed spacing");
        for(int cycle=0;cycle<12;cycle++) {
            Object[] placed=order.placeHelperArguments(policy,nativePlacement);int[] local=(int[])placed[3];
            signal.coordinates.x=row.coordinates.x+local[0];label.coordinates.x=row.coordinates.x+local[1];
            check(bridge.frame(mobile).currentSignalLeftPx==1,"repeated native/planned sampling does not invent an extra Row displacement");
            check(owner.getTranslationX()==19,"native parent animation stays untouched");
        }
        active.remove();order.releaseRuntime();bridge.releaseRuntime();
    }
    @SuppressWarnings("unchecked") private static void compose() throws Exception {
        NetworkSharedSlotLayout bridge = bridge(); StatusIconContainer container = new StatusIconContainer();
        Mobile mobile = new Mobile(); View owner = new View(null); container.addView(mobile); mobile.addView(owner);
        NetworkIconOrder order = new NetworkIconOrder(); order.sharedLayout(bridge);
        order.resolve(NetworkSharedSlotLayoutCheck.class.getClassLoader()); order.resolveCompose(NetworkSharedSlotLayoutCheck.class.getClassLoader());
        android.os.Bundle settings = new android.os.Bundle(); settings.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED, true); order.configure(settings);
        field(order, "nodeOwner", Node.class.getMethod("owner")); field(order, "nodeParent", Node.class.getMethod("parentNode"));
        field(order, "nodeCoordinator", Node.class.getMethod("coordinates")); field(order, "coordinatorPosition", Coordinates.class.getMethod("root", long.class));
        field(order, "placeableWidth", Measured.class.getMethod("getWidth"));
        Node row = new Node("row", 60, owner, null), roam = new Node("roam", 8, owner, row), signal = new Node("signal", 32, owner, row), label = new Node("5G", 20, owner, row);
        Modifier signalMod = (Modifier) order.signalModifier(new Modifier.Element()), labelMod = (Modifier) order.textModifier(new Modifier.Element());
        signal.setModifier(signalMod); label.setModifier(labelMod); order.nodeChanged(signal, signalMod); order.nodeChanged(label, labelMod);
        Object[] nativeChildren = {new Measured(roam),new Measured(signal),new Measured(label)};
        List<?> children = Arrays.asList(nativeChildren);
        Method measurement = NetworkIconOrder.class.getDeclaredMethod("sharedMeasurement", Object.class, List.class); measurement.setAccessible(true);
        Object policy = new Object(), scope = measurement.invoke(order, policy, children);
        check(scope != null, "exact shared native owner is recognized");
        ThreadLocal<Object> active = (ThreadLocal<Object>) field(order, "sharedMeasure"); active.set(scope);
        int[] originalXs = {0,8,40}; Object[] original = placement(nativeChildren,originalXs,60);
        check(order.placeHelperArguments(policy, original) == original, "first native measure is untouched");
        NetworkSharedSlotLayout.Frame frame = bridge.frame(mobile);
        check(frame != null && frame.valid && frame.labelPresent && frame.labelWidthPx == 20 && frame.signalWidthPx == 40, "independent label and roaming/signal widths");
        check(frame.nativeHostWidthPx == 60 && frame.nativeLabelLeftPx == 40, "frame retains compact native size");
        check(bridge.apply(frame,200,10,160,true,true), "outer flow may separate fragments");
        Object[] changed = order.placeHelperArguments(policy, original);
        check(changed != original && changed[3] != originalXs && Arrays.equals((int[])changed[3],new int[]{160,168,10}), "real physical placement array has separate fragment locations");
        check((Integer)changed[4] == 200 && Arrays.equals(originalXs,new int[]{0,8,40}), "hull grows while original array is immutable");
        check(order.placeHelperArguments(new Object(), original) == original, "nested/unrelated Row policy cannot borrow outer scope");
        Object[] foreignChildren={new Measured(roam),new Measured(signal),new Measured(label)};
        Object[] foreignPlacement=placement(foreignChildren,originalXs,60);
        check(order.placeHelperArguments(policy,foreignPlacement)==foreignPlacement,"nested Row with same policy singleton cannot borrow another measurable array");
        Object[] wideNative = placement(nativeChildren,new int[]{140,148,180},200); order.placeHelperArguments(policy,wideNative);
        check(bridge.frame(mobile).nativeHostWidthPx == 60, "hull remeasure never corrupts natural width");
        check(!bridge.apply(frame,200,10,15,true,true), "overlapping fragments rejected before mutation");
        check(bridge.apply(frame,200,10,15,true,true,true),"intentional signed negative margin may overlap valid native fragments");
        Object[] overlapping=order.placeHelperArguments(policy,original);
        check(Arrays.equals((int[])overlapping[3],new int[]{15,23,10}),"Compose opt-in overlap uses actual native placement");
        check(!bridge.apply(frame,200,10,190,true,true,true)&&!bridge.apply(frame,200,-1,15,true,true,true),"overlap opt-in keeps finite hull bounds");
        check(bridge.apply(frame,200,10,160,true,true),"separated target restores after intentional negative margin");
        check(!bridge.apply(frame,200,10,160,false,true), "backend does not hide native label for overflow");
        check(mobile.getTranslationX() == 0f && label.owner == owner && signal.parent == row, "native host/owner/translation are retained");
        int hostRequests=mobile.layoutRequests;
        for(int i=0;i<20;i++) {
            order.placeHelperArguments(policy,wideNative);
            check(bridge.apply(bridge.frame(mobile),200,10,160,true,true),"stable native frame keeps target");
        }
        check(hostRequests==mobile.layoutRequests,"twenty native frames never schedule extra host layouts");
        row.coordinates.x = 4; check(bridge.apply(frame,200,0,160,true,true), "native row inset permits signed local placement while glyph stays inside host");
        check(((int[])order.placeHelperArguments(policy,original)[3])[2]==-4,"native place array compensates Row origin without a translated host");row.coordinates.x = 0;
        View foreign = new View(null); label.owner = foreign;
        check(measurement.invoke(order,policy,children) == null, "different subscription owner fails closed"); label.owner = owner;
        bridge.configure(false); check(order.placeHelperArguments(policy, original) == original, "disable immediately restores native placement");
        check(bridge.frame(mobile) == null, "disabled bridge has no shared endpoints");
        active.remove(); order.releaseRuntime(); check(bridge.summary().contains("enabled=false"), "release stays disabled");
    }
    private static final int LABEL=101,SIGNAL=102,ROAM=103;
    private static final class PaddedMobile extends ViewGroup implements StatusIconDisplayable {
        int rowWidth=176;
        PaddedMobile(){super(null);}
        public String getSlot(){return "stacked_mobile";}
        public boolean isIconVisible(){return true;}
        public boolean isIconBlocked(){return false;}
        @Override public int getWidth(){return rowWidth+8;}
        @Override protected void onMeasure(int width,int height){setMeasuredDimension(rowWidth+8,80);}
    }
    @SuppressWarnings("unchecked") private static void paddedCompose() throws Exception {
        for(boolean labelFirst:new boolean[]{false,true}) {
            NetworkSharedSlotLayout bridge=bridge();StatusIconContainer container=new StatusIconContainer();
            PaddedMobile mobile=new PaddedMobile();View owner=new View(null);container.addView(mobile);mobile.addView(owner);
            NetworkIconOrder order=new NetworkIconOrder();order.sharedLayout(bridge);
            order.resolve(NetworkSharedSlotLayoutCheck.class.getClassLoader());order.resolveCompose(NetworkSharedSlotLayoutCheck.class.getClassLoader());
            android.os.Bundle settings=new android.os.Bundle();settings.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED,true);order.configure(settings);
            field(order,"nodeOwner",Node.class.getMethod("owner"));field(order,"nodeParent",Node.class.getMethod("parentNode"));
            field(order,"nodeCoordinator",Node.class.getMethod("coordinates"));field(order,"coordinatorPosition",Coordinates.class.getMethod("root",long.class));
            field(order,"placeableWidth",Measured.class.getMethod("getWidth"));
            Node row=new Node("row",176,owner,null),signal=new Node("signal",72,owner,row),label=new Node("type",104,owner,row);row.coordinates.x=8;
            Modifier s=(Modifier)order.signalModifier(new Modifier.Element()),l=(Modifier)order.textModifier(new Modifier.Element());
            signal.setModifier(s);label.setModifier(l);order.nodeChanged(signal,s);order.nodeChanged(label,l);
            Object[] nativeChildren=labelFirst?new Object[]{new Measured(label),new Measured(signal)}:new Object[]{new Measured(signal),new Measured(label)};
            int[] nativeX=labelFirst?new int[]{0,104}:new int[]{0,72};
            Method measurement=NetworkIconOrder.class.getDeclaredMethod("sharedMeasurement",Object.class,List.class);measurement.setAccessible(true);
            Object policy=new Object(),scope=measurement.invoke(order,policy,Arrays.asList(nativeChildren));check(scope!=null,"padded native owner is exact");
            ThreadLocal<Object> active=(ThreadLocal<Object>)field(order,"sharedMeasure");active.set(scope);
            Object[] original=placement(nativeChildren,nativeX,176);
            check(order.placeHelperArguments(policy,original)==original,"native padded first measure stays unmodified");
            NetworkSharedSlotLayout.Frame frame=bridge.frame(mobile);
            check(frame!=null&&frame.nativeHostWidthPx==184,"native 184px includes 8px row origin");
            check(frame.signalWidthPx==(labelFirst?72:80)&&frame.labelWidthPx==(labelFirst?112:104),"native leading belongs to actual first physical fragment budget");
            check(frame.signalWidthPx+frame.labelWidthPx==184&&signal.width==72&&label.width==104,"budget retains eight pixels without changing glyph font widths");
            int nativeLabel=labelFirst?0:80,nativeSignal=labelFirst?112:0;
            check(frame.nativeLabelLeftPx==nativeLabel&&frame.nativeSignalLeftPx==nativeSignal,"native fragment sort points refer to full budget origins");
            check(bridge.apply(frame,184,nativeLabel,nativeSignal,true,true),"native first budget at zero accepts padded Row");
            Object[] nativeTarget=order.placeHelperArguments(policy,original);
            check(Arrays.equals((int[])nativeTarget[3],nativeX)&&(Integer)nativeTarget[4]==176,"zero margins exactly preserve native Row glyph positions");
            mobile.rowWidth=(Integer)nativeTarget[4];mobile.measure(View.MeasureSpec.makeMeasureSpec(184,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));
            check(mobile.getMeasuredWidth()==184,"native row plus eight-pixel wrapper measures requested host hull");
            int reversedLabel=labelFirst?72:0,reversedSignal=labelFirst?0:104;
            check(bridge.apply(frame,184,reversedLabel,reversedSignal,true,true),"padded native fragments can exchange physical sides");
            Object[] reversed=order.placeHelperArguments(policy,original);int[] local=(int[])reversed[3];
            check(Arrays.equals(local,labelFirst?new int[]{72,-8}:new int[]{104,-8}),"crossed first glyph uses signed Row-local placement");
            int labelIndex=labelFirst?0:1,signalIndex=labelFirst?1:0;
            check(local[labelIndex]+8==reversedLabel+(labelFirst?8:0)&&local[signalIndex]+8==reversedSignal+(labelFirst?0:8),"global glyph coordinates include only their own original leading inset");
            check(bridge.apply(frame,400,0,400-frame.signalWidthPx,true,true),"padded label and signal may occupy distant relative regions");
            Object[] distant=order.placeHelperArguments(policy,original);check((Integer)distant[4]==392,"large host hull subtracts exact wrapper origin once");
            mobile.rowWidth=(Integer)distant[4];mobile.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));
            check(mobile.getMeasuredWidth()==400,"expanded padded native host has exact measured hull");
            int requests=mobile.layoutRequests;
            int[] wideX=labelFirst?new int[]{216,320}:new int[]{216,288};Object[] wide=placement(nativeChildren,wideX,392);
            for(int i=0;i<20;i++) {
                order.placeHelperArguments(policy,wide);NetworkSharedSlotLayout.Frame current=bridge.frame(mobile);
                check(current.nativeHostWidthPx==184&&current.signalWidthPx+current.labelWidthPx==184,"expanded padded measure never pollutes saved native budget");
                check(bridge.apply(current,400,0,400-current.signalWidthPx,true,true),"stable padded target remains valid");
            }
            check(mobile.layoutRequests==requests,"padded native repeated frames create no extra host layout requests");
            // Execute actual native capture transitions. The first Wi-Fi pass
            // includes a retained zero-width label; the next omits that node.
            Measured measuredLabel=(Measured)nativeChildren[labelIndex],measuredSignal=(Measured)nativeChildren[signalIndex];
            for(int radioCycle=0;radioCycle<2;radioCycle++) {
                active.set(measurement.invoke(order,policy,Arrays.asList(nativeChildren)));
                NetworkSharedSlotLayout.Frame dataFrame=bridge.frame(mobile);
                check(bridge.apply(dataFrame,184,nativeLabel,nativeSignal,true,true),"data native hull is 184 before Wi-Fi transition");
                Object[] dataPlacement=order.placeHelperArguments(policy,original);mobile.rowWidth=(Integer)dataPlacement[4];
                mobile.measure(View.MeasureSpec.makeMeasureSpec(184,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));
                check(mobile.getMeasuredWidth()==184,"actual data measure has label budget");
                measuredLabel.width=0;
                Object[] wifiChildren=radioCycle==0?nativeChildren:new Object[]{measuredSignal};
                int[] wifiPositions=radioCycle==0?(labelFirst?new int[]{0,0}:new int[]{0,72}):new int[]{0};
                active.set(measurement.invoke(order,policy,Arrays.asList(wifiChildren)));
                Object[] nativeWifi=placement(wifiChildren,wifiPositions,176);
                int beforeWifiRequests=mobile.layoutRequests;Object[] wifiPlacement=order.placeHelperArguments(policy,nativeWifi);
                check(wifiPlacement!=nativeWifi&&(Integer)wifiPlacement[4]==72,"first native Wi-Fi capture drops old data target and old forced min width immediately");
                mobile.rowWidth=(Integer)wifiPlacement[4];mobile.measure(View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));
                NetworkSharedSlotLayout.Frame wifiFrame=bridge.frame(mobile);
                check(mobile.getMeasuredWidth()==80&&wifiFrame!=null&&!wifiFrame.labelPresent&&wifiFrame.nativeHostWidthPx==80&&wifiFrame.signalWidthPx==80,"native Wi-Fi signal host is 80 rather than retained 184");
                check(mobile.getMeasuredWidth()+88==168&&wifiFrame.labelWidthPx==0,"Wi-Fi88 plus mobile80 never double-reserves old label104");
                int[] endAlignedWifi=radioCycle==0?(labelFirst?new int[]{104,104}:new int[]{104,176}):new int[]{104};
                Object[] staleExact=placement(wifiChildren,endAlignedWifi,176),recovered=order.placeHelperArguments(policy,staleExact);
                check((Integer)recovered[4]==72&&Arrays.equals((int[])recovered[3],wifiPositions),"repeated stale EXACT constraints cannot become saved native trailing padding");
                check(bridge.frame(mobile).nativeHostWidthPx==80&&mobile.layoutRequests==beforeWifiRequests,"Wi-Fi recovery is natural80 without another native invalidation");
                Object[] naturalWifi=placement(wifiChildren,wifiPositions,72);
                check(order.placeHelperArguments(policy,naturalWifi)==naturalWifi,"natural Wi-Fi pass ends recovery and preserves original native array");
                measuredLabel.width=104;active.set(measurement.invoke(order,policy,Arrays.asList(nativeChildren)));
                check(order.placeHelperArguments(policy,original)==original,"next data capture starts from genuine native measurement");
                mobile.rowWidth=176;mobile.measure(View.MeasureSpec.makeMeasureSpec(184,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));
                NetworkSharedSlotLayout.Frame returned=bridge.frame(mobile);
                check(returned.labelPresent&&returned.nativeHostWidthPx==184&&returned.labelWidthPx+returned.signalWidthPx==184,"data label returns to complete native184 budget");
                check(bridge.apply(returned,184,nativeLabel,nativeSignal,true,true),"shared data placement resumes after native visibility return");
            }
            bridge.restore(mobile);check(order.placeHelperArguments(policy,original)==original,"padded restore returns exact native position array");
            mobile.rowWidth=176;mobile.measure(View.MeasureSpec.makeMeasureSpec(184,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));
            check(mobile.getMeasuredWidth()==184&&mobile.getTranslationX()==0f,"padded host restores measured native width and native animation property");
            active.remove();order.releaseRuntime();bridge.releaseRuntime();
        }
    }
    private static final class Label extends TextView {
        int direction, nativeWidth=20;
        Label() { super(null); setId(LABEL); }
        final android.content.res.Resources resources = new android.content.res.Resources() {
            @Override public int getIdentifier(String name,String type,String pkg) { return name.equals("mobile_signal_data_outer") ? SIGNAL : name.equals("mobile_roaming_text") ? ROAM : 0; }
        };
        @Override public android.content.res.Resources getResources() { return resources; }
        @Override public int getWidth() { return nativeWidth; }
        @Override public int getLayoutDirection() { return direction; }
    }
    public static final class ParentParams extends ViewGroup.LayoutParams {
        public ParentParams(int width, int height) { super(width,height); }
        public ParentParams(ViewGroup.LayoutParams source) { super(source.width,source.height); }
    }
    private static void singleVisibility() throws Exception {
        NetworkSharedSlotLayout bridge=bridge();StatusIconContainer container=new StatusIconContainer();PaddedMobile mobile=new PaddedMobile();container.addView(mobile);
        ConstraintLayout parent=new ConstraintLayout();ParentParams nativeParent=new ParentParams(-2,80);mobile.addView(parent,nativeParent);
        Label label=new Label();label.nativeWidth=104;View signal=new View(null){@Override public int getWidth(){return 80;}};signal.setId(SIGNAL);
        ConstraintLayout.LayoutParams nativeLabel=new ConstraintLayout.LayoutParams(-2,-2),nativeSignal=new ConstraintLayout.LayoutParams(-2,0);
        parent.addView(label,nativeLabel);parent.addView(signal,nativeSignal);label.layout(80,0,184,80);signal.layout(0,0,80,80);
        SingleNetworkLabelPlacement placement=new SingleNetworkLabelPlacement(label);placement.sharedLayout(bridge);placement.observeSharedHost();
        for(int radioCycle=0;radioCycle<2;radioCycle++) {
            mobile.rowWidth=176;mobile.measure(View.MeasureSpec.makeMeasureSpec(184,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));
            NetworkSharedSlotLayout.Frame data=bridge.frame(mobile);check(data!=null&&data.labelPresent&&data.nativeHostWidthPx==184,"single data native measurement is184");
            check(bridge.apply(data,184,80,0,true,true),"single data shared hull applies");
            // SingleNetworkLabelControls releases its placement before setting
            // GONE; a native binder can also update visibility directly.
            if(radioCycle==1)placement.restore();
            label.setVisibility(View.GONE);
            if(radioCycle==0)check(bridge.frame(mobile)==null,"single first native visibility change restores LPs before recapturing geometry");
            else check(bridge.frame(mobile)==null,"single explicit release also waits for genuine native layout");
            check(label.getLayoutParams()==nativeLabel&&signal.getLayoutParams()==nativeSignal&&parent.getLayoutParams()==nativeParent,"single visibility flip restores three native LP identities once");
            // A radio callback can reach the outer flow repeatedly before the
            // requested native traversal. Its host still has our former EXACT
            // hull, so it is not yet a natural Wi-Fi-only frame.
            for(int pending=0;pending<3;pending++)
                check(bridge.frame(mobile)==null,"single pending native layout never recaptures the former EXACT data hull");
            // A root onLayout-only callback, including Flow's internal
            // restoration layouts, cannot witness a fresh natural measure.
            bridge.nativeLaidOut(container);
            check(bridge.frame(mobile)==null,"single layout-only callback cannot adopt the unchanged old EXACT hull");
            mobile.rowWidth=72;mobile.measure(View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));
            parent.measure(0,0);signal.measure(0,0);
            bridge.nativeMeasured(container);
            check(bridge.frame(mobile)==null,"single root measurement alone cannot complete native placement");
            signal.layout(signal.getLeft(),signal.getTop(),signal.getRight(),signal.getBottom());
            bridge.nativeLaidOut(container);
            NetworkSharedSlotLayout.Frame wifi=bridge.frame(mobile);check(wifi!=null&&!wifi.labelPresent&&wifi.nativeHostWidthPx==80&&mobile.getMeasuredWidth()==80,"single Wi-Fi recaptures native80 rather than former184");
            int writes=label.layoutRequests+signal.layoutRequests+parent.layoutRequests;
            check(bridge.frame(mobile)!=null&&writes==label.layoutRequests+signal.layoutRequests+parent.layoutRequests,"single repeated Wi-Fi frame never restores or rewrites LPs again");
            label.setVisibility(View.VISIBLE);mobile.rowWidth=176;mobile.measure(View.MeasureSpec.makeMeasureSpec(184,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));
            NetworkSharedSlotLayout.Frame returned=bridge.frame(mobile);check(returned!=null&&returned.labelPresent&&returned.nativeHostWidthPx==184,"single data returns to native184 after Wi-Fi");
        }
        placement.sharedLayout(null);bridge.releaseRuntime();
    }
    private static void single() throws Exception {
        NetworkSharedSlotLayout bridge = bridge(); StatusIconContainer container = new StatusIconContainer(); Mobile mobile = new Mobile(); container.addView(mobile);
        ConstraintLayout parent = new ConstraintLayout(); ParentParams originalParent = new ParentParams(-2,80); mobile.addView(parent,originalParent);
        Label label = new Label(); View signal = new View(null) { @Override public int getWidth() { return 32; } };
        View roam = new View(null) { @Override public int getWidth() { return 8; } }; signal.setId(SIGNAL); roam.setId(ROAM);
        ConstraintLayout.LayoutParams labelParams = new ConstraintLayout.LayoutParams(-2,-2), signalParams = new ConstraintLayout.LayoutParams(-2,0), roamParams = new ConstraintLayout.LayoutParams(-2,-2);
        labelParams.endToEnd=0; signalParams.topToTop=0; signalParams.bottomToBottom=0; roamParams.bottomToBottom=0; roamParams.bottomMargin=2;
        parent.addView(label,labelParams); parent.addView(signal,signalParams); parent.addView(roam,roamParams);
        label.layout(40,0,60,80); signal.layout(8,0,40,80); roam.layout(0,0,8,80);
        SingleNetworkLabelPlacement placement = new SingleNetworkLabelPlacement(label); placement.sharedLayout(bridge); placement.observeSharedHost();
        NetworkSharedSlotLayout.Frame frame = bridge.frame(mobile); check(frame != null && frame.labelWidthPx == 20 && frame.signalWidthPx == 40, "single native siblings form actual fragment frame");
        check(!bridge.apply(frame,200,10,15,true,true),"single normal margins reject overlap by default");
        check(bridge.apply(frame,200,10,15,true,true,true),"single signed margin opts in to overlapping native fragments");
        check(((ConstraintLayout.LayoutParams)label.getLayoutParams()).leftMargin==10&&((ConstraintLayout.LayoutParams)signal.getLayoutParams()).leftMargin==23,"single opt-in overlap is physical ConstraintLayout placement");
        check(bridge.apply(frame,200,10,160,true,true), "single shared target accepted");
        ConstraintLayout.LayoutParams l=(ConstraintLayout.LayoutParams)label.getLayoutParams(),s=(ConstraintLayout.LayoutParams)signal.getLayoutParams(),r=(ConstraintLayout.LayoutParams)roam.getLayoutParams();
        check(l!=labelParams&&s!=signalParams&&r!=roamParams,"single clones exact native parameters");
        check(l.leftToLeft==0&&l.leftMargin==10&&s.leftToLeft==0&&s.leftMargin==168&&r.leftMargin==160,"single uses real physical constraints for fragments");
        check(l.startToStart==-1&&l.endToEnd==-1&&!l.isMarginRelative(),"relative anchors cannot override physical placement in RTL");
        check(s.height==0&&s.topToTop==0&&r.bottomMargin==2&&r.bottomToBottom==0,"native vertical signal and roaming constraints retained");
        check(parent.getLayoutParams()!=originalParent&&parent.getLayoutParams().width==200,"native constraint parent measured as complete hull");
        check(label.getVisibility()==View.VISIBLE&&signal.getTranslationX()==0f&&label.getParent()==parent,"single keeps native visibility parent and animation properties");
        int writes=label.layoutRequests+signal.layoutRequests+roam.layoutRequests+parent.layoutRequests;
        check(bridge.apply(frame,200,10,160,true,true),"repeat shared target succeeds");
        check(writes==label.layoutRequests+signal.layoutRequests+roam.layoutRequests+parent.layoutRequests,"repeat target creates no layout loop");
        for(int i=0;i<20;i++)check(bridge.apply(bridge.frame(mobile),200,10,160,true,true),"stable single native frame keeps target");
        check(writes==label.layoutRequests+signal.layoutRequests+roam.layoutRequests+parent.layoutRequests,"twenty single frames do not rewrite LPs or create a layout loop");
        roam.setVisibility(View.GONE);
        check(bridge.apply(bridge.frame(mobile),200,10,160,true,true),"native roaming disappearance updates the same target");
        check(((ConstraintLayout.LayoutParams)signal.getLayoutParams()).leftMargin==160&&roam.getVisibility()==View.GONE,"gone roaming consumes no signal-fragment gap");
        roam.setVisibility(View.VISIBLE);label.direction=View.LAYOUT_DIRECTION_RTL;
        check(bridge.apply(bridge.frame(mobile),200,10,160,true,true),"native roaming reappearance updates without changing the target tuple");
        check(((ConstraintLayout.LayoutParams)signal.getLayoutParams()).leftMargin==168&&((ConstraintLayout.LayoutParams)label.getLayoutParams()).leftMargin==10,"RTL physical coordinates keep one exact shared slot");
        check(!bridge.apply(frame,200,10,160,true,false),"signal suppression cannot alter native visibility");
        bridge.restore(mobile);
        check(label.getLayoutParams()==labelParams&&signal.getLayoutParams()==signalParams&&roam.getLayoutParams()==roamParams&&parent.getLayoutParams()==originalParent,"restore exact four native LP identities");
        label.setVisibility(View.GONE);mobile.measure(0,0);parent.measure(0,0);signal.measure(0,0);
        bridge.nativeMeasured(container);bridge.nativeLaidOut(container);
        NetworkSharedSlotLayout.Frame withoutLabel=bridge.frame(mobile);
        check(withoutLabel!=null&&!withoutLabel.labelPresent&&bridge.apply(withoutLabel,200,0,160,false,true),"Wi-Fi-visible native GONE type still yields an independent signal fragment");
        check(label.getVisibility()==View.GONE,"shared slot never forces a network label visible");bridge.restore(mobile);label.setVisibility(View.VISIBLE);
        mobile.measure(0,0);parent.measure(0,0);signal.measure(0,0);bridge.nativeMeasured(container);bridge.nativeLaidOut(container);frame=bridge.frame(mobile);
        check(bridge.apply(frame,200,10,160,true,true),"target reapplies after restore");
        ConstraintLayout.LayoutParams replacement=new ConstraintLayout.LayoutParams(-2,-2);label.setLayoutParams(replacement);bridge.configure(false);
        check(label.getLayoutParams()==replacement&&signal.getLayoutParams()==signalParams,"new OEM LP ownership survives disable");
        placement.sharedLayout(null);bridge.releaseRuntime();check(bridge.frame(mobile)==null,"release drops single host");
    }
    private static void singlePadded() throws Exception {
        NetworkSharedSlotLayout bridge=bridge();StatusIconContainer container=new StatusIconContainer();PaddedMobile mobile=new PaddedMobile();
        mobile.rowWidth=52;container.addView(mobile);
        ConstraintLayout parent=new ConstraintLayout();ParentParams originalParent=new ParentParams(-2,80);mobile.addView(parent,originalParent);
        // The actual OEM hierarchy has optional native siblings before this parent.
        // Their eight pixels belong to the leading fragment, never to a negative LP.
        parent.layout(8,11,60,71);Label label=new Label();View signal=new View(null){@Override public int getWidth(){return 32;}};signal.setId(SIGNAL);
        ConstraintLayout.LayoutParams originalLabel=new ConstraintLayout.LayoutParams(-2,-2),originalSignal=new ConstraintLayout.LayoutParams(-2,0);
        originalLabel.endToEnd=0;originalLabel.bottomToBottom=0;originalLabel.bottomMargin=38;originalLabel.rightMargin=58;originalLabel.setMarginEnd(58);
        originalSignal.topToTop=0;originalSignal.bottomToBottom=0;
        parent.addView(label,originalLabel);parent.addView(signal,originalSignal);label.layout(0,7,20,67);signal.layout(20,0,52,60);
        SingleNetworkLabelPlacement placement=new SingleNetworkLabelPlacement(label);placement.sharedLayout(bridge);placement.observeSharedHost();
        NetworkSharedSlotLayout.Frame frame=bridge.frame(mobile);
        check(frame!=null&&frame.labelWidthPx==28&&frame.signalWidthPx==32,"single leading budget retains native parent origin");
        check(frame.nativeLabelLeftPx==0&&frame.nativeSignalLeftPx==28,"single native fragment origins use complete budgets");
        check(bridge.apply(frame,60,0,28,true,true),"single zero target with a native parent prefix applies");
        check(((ConstraintLayout.LayoutParams)label.getLayoutParams()).leftMargin==0&&((ConstraintLayout.LayoutParams)signal.getLayoutParams()).leftMargin==20,"single exact native glyph positions remain inside parent");
        ConstraintLayout.LayoutParams centered=(ConstraintLayout.LayoutParams)label.getLayoutParams();
        check(centered.height==ViewGroup.LayoutParams.WRAP_CONTENT&&centered.topMargin==0&&centered.bottomMargin==0,"single shared slot clears real native 38px badge bottom margin before centering");
        check(originalLabel.bottomMargin==38&&originalLabel.getMarginEnd()==58,"single shared slot never mutates real native badge offsets");
        check(parent.getTop()==11&&label.getTop()==7&&signal.getTop()==0,"single shared budget never writes native Y");
        int writes=label.layoutRequests+signal.layoutRequests+parent.layoutRequests;
        for(int i=0;i<20;i++)check(bridge.apply(bridge.frame(mobile),60,0,28,true,true),"single padded repeated target is stable");
        check(writes==label.layoutRequests+signal.layoutRequests+parent.layoutRequests,"single padded repeated frame does not rewrite constraints");
        check(!bridge.apply(bridge.frame(mobile),60,32,0,true,true),"moving unbudgeted glyph over native prefix fails closed");
        bridge.restore(mobile);
        check(label.getLayoutParams()==originalLabel&&signal.getLayoutParams()==originalSignal&&parent.getLayoutParams()==originalParent,"single padded failure restores native identities");
        check(((ConstraintLayout.LayoutParams)label.getLayoutParams()).bottomMargin==38&&((ConstraintLayout.LayoutParams)label.getLayoutParams()).getMarginEnd()==58,"single shared restore recovers real native badge margins");
        mobile.measure(0,0);parent.measure(0,0);signal.measure(0,0);bridge.nativeMeasured(container);bridge.nativeLaidOut(container);frame=bridge.frame(mobile);
        boolean wasLogging=ModuleDiagnostics.enabled();android.os.Bundle logging=new android.os.Bundle();logging.putBoolean(ModuleDiagnostics.KEY_ENABLED,true);ModuleDiagnostics.configure(logging);
        try {
            for(int i=0;i<100;i++)check(!bridge.apply(frame,60,32,0,true,true),"same unsafe prefix target remains rejected");
            java.util.Set<?> failures=(java.util.Set<?>)field(placement,"diagnosedFailures");
            check(failures.size()==1,"same rejected shape records only one companion diagnostic");
            Field pendingField=ModuleDiagnostics.class.getDeclaredField("PENDING");pendingField.setAccessible(true);
            @SuppressWarnings("unchecked") java.util.ArrayDeque<String> pending=(java.util.ArrayDeque<String>)pendingField.get(null);
            org.json.JSONObject diagnostic=new org.json.JSONObject(pending.peekLast()).getJSONObject("details").getJSONObject("values");
            check("native_constraint".equals(diagnostic.getString("backend"))&&"signal_position".equals(diagnostic.getString("reason")),"single rejection names actual backend and failing stage");
            check(diagnostic.getInt("parent_left_px")==8&&diagnostic.getInt("native_label_left_px")==8&&diagnostic.getInt("native_signal_left_px")==28,"single rejection exports true native parent and glyph origins");
            check("constraint".equals(diagnostic.getString("label_lp"))&&"constraint".equals(diagnostic.getString("signal_lp"))&&"plain".equals(diagnostic.getString("parent_lp")),"single rejection exports fixed LP shape tokens only");
            check("IllegalArgumentException".equals(diagnostic.getString("failure_class")),"single rejection preserves failure type without exception contents");
            for(int i=1;i<20;i++)check(!bridge.apply(frame,60+i,32,0,true,true),"unsafe prefix variants remain rejected");
            check(failures.size()==8,"single rejected-shape diagnostics have an eight-record hard limit");
        } finally {logging.putBoolean(ModuleDiagnostics.KEY_ENABLED,wasLogging);ModuleDiagnostics.configure(logging);}
        // With a signal-first native row, the same prefix belongs to that signal.
        label.layout(32,7,52,67);signal.layout(0,0,32,60);
        frame=bridge.frame(mobile);
        check(frame.labelWidthPx==20&&frame.signalWidthPx==40&&frame.nativeSignalLeftPx==0&&frame.nativeLabelLeftPx==40,"single signal-first prefix belongs only to signal budget");
        check(bridge.apply(frame,60,40,0,true,true),"single signal-first original order accepts target at zero");
        check(((ConstraintLayout.LayoutParams)label.getLayoutParams()).leftMargin==32&&((ConstraintLayout.LayoutParams)signal.getLayoutParams()).leftMargin==0,"single signal-first glyphs preserve exact physical origins");
        bridge.restore(mobile);
        placement.sharedLayout(null);bridge.releaseRuntime();
    }
    /** Compare actual Compose placement output with actual single constraints,
     * both consuming the same production planner/config. No duplicate planner
     * or TextView/Compose drawing implementation lives in this check. */
    @SuppressWarnings("unchecked") private static void defaultSingleComposeParity()throws Exception{
        NetworkSharedSlotLayout shared=bridge();StatusIconContainer container=new StatusIconContainer();
        PaddedMobile dual=new PaddedMobile();dual.rowWidth=160;container.addView(dual);View owner=new View(null);dual.addView(owner);
        NetworkIconOrder order=new NetworkIconOrder();order.sharedLayout(shared);order.resolve(NetworkSharedSlotLayoutCheck.class.getClassLoader());order.resolveCompose(NetworkSharedSlotLayoutCheck.class.getClassLoader());
        android.os.Bundle config=new android.os.Bundle();config.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED,true);config.putFloat(StatusBarLayoutSettings.key(StatusBarLayoutSettings.WIFI,"margin_right"),-2.5f);
        order.configure(config);field(order,"nodeOwner",Node.class.getMethod("owner"));field(order,"nodeParent",Node.class.getMethod("parentNode"));
        field(order,"nodeCoordinator",Node.class.getMethod("coordinates"));field(order,"coordinatorPosition",Coordinates.class.getMethod("root",long.class));field(order,"placeableWidth",Measured.class.getMethod("getWidth"));
        Node row=new Node("row",160,owner,null),bars=new Node("signal",72,owner,row),text=new Node("5G",88,owner,row);row.coordinates.x=8;
        Modifier signalModifier=(Modifier)order.signalModifier(new Modifier.Element()),textModifier=(Modifier)order.textModifier(new Modifier.Element());
        bars.setModifier(signalModifier);text.setModifier(textModifier);order.nodeChanged(bars,signalModifier);order.nodeChanged(text,textModifier);
        Object[] children={new Measured(bars),new Measured(text)};Object policy=new Object();
        Method measurement=NetworkIconOrder.class.getDeclaredMethod("sharedMeasurement",Object.class,List.class);measurement.setAccessible(true);
        Object scope=measurement.invoke(order,policy,Arrays.asList(children));ThreadLocal<Object> active=(ThreadLocal<Object>)field(order,"sharedMeasure");active.set(scope);
        Object[] nativePlacement=placement(children,new int[]{0,72},160);order.placeHelperArguments(policy,nativePlacement);
        NetworkSharedSlotLayout.Frame compose=shared.frame(dual);check(compose!=null&&compose.nativeSignalLeftPx<compose.nativeLabelLeftPx,"current Compose default geometry is signal then label");
        PaddedMobile single=new PaddedMobile();single.rowWidth=180;container.addView(single);ConstraintLayout parent=new ConstraintLayout();single.addView(parent,new ParentParams(-2,80));parent.layout(8,0,188,80);
        Label label=new Label();label.nativeWidth=88;View signal=new View(null){@Override public int getWidth(){return 92;}};signal.setId(SIGNAL);
        ConstraintLayout.LayoutParams original=new ConstraintLayout.LayoutParams(-2,-2),originalSignal=new ConstraintLayout.LayoutParams(-2,0);original.bottomMargin=38;original.setMarginEnd(58);
        parent.addView(label,original);parent.addView(signal,originalSignal);
        // Exact r2 capture: the old badge-first geometry must not become a new
        // immutable shared ordering while the replacement chain awaits layout.
        label.layout(0,0,88,80);signal.layout(78,0,170,80);
        SingleNetworkLabelPlacement nativeLabel=new SingleNetworkLabelPlacement(label);nativeLabel.sharedLayout(shared);nativeLabel.observeSharedHost();check(nativeLabel.apply(),"single audited chain installs");
        ConstraintLayout.LayoutParams applied=(ConstraintLayout.LayoutParams)label.getLayoutParams();
        check(applied.startToEnd==SIGNAL&&applied.endToEnd==0,"single default follows the real Compose logical ordering");
        check(shared.frame(single)==null,"pre-layout r2 badge-first coordinates cannot freeze shared natural sort");
        // Following native traversal resolves the declared reciprocal chain.
        // These are measured host/glyph widths from the r2 capture, not a solver.
        label.layout(92,0,180,80);signal.layout(0,0,92,80);
        NetworkSharedSlotLayout.Frame nativeFrame=shared.frame(single);check(nativeFrame!=null&&nativeFrame.nativeSignalLeftPx<nativeFrame.nativeLabelLeftPx,"single measured natural sort now agrees with Compose");
        check(nativeFrame.signalWidthPx==100&&compose.signalWidthPx==80,"single keeps its real native 20px signal prefix without adding it to label width");
        check(nativeFrame.labelWidthPx==compose.labelWidthPx,"same 22dp custom label has the same budget in both backends");
        float[] composeResult=null;
        for(NetworkSharedSlotLayout.Frame frame:new NetworkSharedSlotLayout.Frame[]{compose,nativeFrame}){
            StatusBarFlowPlanner.Node signalNode=new StatusBarFlowPlanner.Node(StatusBarLayoutSettings.MOBILE,StatusBarLayoutSettings.RIGHT,frame.nativeSignalLeftPx,frame.signalWidthPx,0,0);
            StatusBarFlowPlanner.Node labelNode=new StatusBarFlowPlanner.Node(StatusBarLayoutSettings.WIFI,StatusBarLayoutSettings.RIGHT,frame.nativeLabelLeftPx,frame.labelWidthPx,0,0);
            StatusBarFlowPlanner.Node battery=new StatusBarFlowPlanner.Node(StatusBarLayoutSettings.BATTERY,StatusBarLayoutSettings.RIGHT,1000,80,0,0);
            StatusBarFlowPlanner.Plan plan=StatusBarFlowPlanner.plan(new StatusBarFlowPlanner.Bounds(0,0,0,1000),Arrays.asList(signalNode,labelNode,battery),StatusBarLayoutSettings.Snapshot.parse(config),4);
            float signalX=plan.find(signalNode).x,labelX=plan.find(labelNode).x;int start=(int)Math.min(signalX,labelX),end=(int)Math.max(signalX+frame.signalWidthPx,labelX+frame.labelWidthPx);
            check(shared.apply(frame,end-start,Math.round(labelX-start),Math.round(signalX-start),true,true,true),"both real backends accept their common planner targets");
            float actualLabel,actualSignalRight;
            if(frame==compose){Object[] placed=order.placeHelperArguments(policy,nativePlacement);int[] positions=(int[])placed[3];actualLabel=start+8+positions[1];actualSignalRight=start+8+positions[0]+bars.width;}
            else{ConstraintLayout.LayoutParams l=(ConstraintLayout.LayoutParams)label.getLayoutParams(),s=(ConstraintLayout.LayoutParams)signal.getLayoutParams();actualLabel=start+parent.getLeft()+l.leftMargin;actualSignalRight=start+parent.getLeft()+s.leftMargin+92;}
            float[] actual={actualLabel+frame.labelWidthPx/2f,actualLabel-actualSignalRight,plan.find(battery).x};
            if(composeResult==null)composeResult=actual;else check(Arrays.equals(composeResult,actual),"same dual-card config yields identical label center, real signal-right gap, and battery anchor");
        }
        active.remove();nativeLabel.sharedLayout(null);order.releaseRuntime();shared.releaseRuntime();
    }
    public static void main(String[] args) throws Exception { constrainedContentFeedback();constraintsLifecycle();replacementAfterCompactReplay();failedParentTraversal();composeRootCoordinates();defaultSingleComposeParity();compose();paddedCompose();singleVisibility();single();singlePadded();System.out.println("NetworkSharedSlotLayoutCheck: "+checks+" checks passed"); }
}
