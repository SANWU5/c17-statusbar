package dev.puitheme;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** A native local radio update must not display its temporary unprojected target. */
public final class StatusBarFlowContinuityCheck {
    private static int checks;
    private static Object field(Object owner, String name) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) try {
            Field value = type.getDeclaredField(name); value.setAccessible(true); return value.get(owner);
        } catch (NoSuchFieldException inherited) { }
        throw new NoSuchFieldException(name);
    }
    private static void near(float expected, float actual, String why) {
        checks++;
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > .02f)
            throw new AssertionError(why + ": " + expected + " != " + actual);
    }
    private static void check(boolean value, String why) {
        checks++; if (!value) throw new AssertionError(why);
    }
    private static float x(View view) {
        float value = 0f;
        for (View at = view; at != null; at = at.getParent() instanceof View ? (View) at.getParent() : null)
            value += at.getLeft() + at.getTranslationX();
        return value;
    }
    private static Object rig() throws Exception {
        Constructor<?> constructor = Class.forName("dev.puitheme.StatusBarFlowLayoutCheck$Rig")
                .getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true); return constructor.newInstance(0, 300);
    }
    private static Bundle placement() {
        Bundle values = new Bundle(); values.putBoolean(StatusBarLayoutSettings.MASTER, true);
        values.putString(StatusBarLayoutSettings.key("wifi", "side"), "left");
        values.putString(StatusBarLayoutSettings.key("mobile", "side"), "right");
        values.putString(StatusBarLayoutSettings.key("battery", "side"), "left");
        values.putString(StatusBarLayoutSettings.key("clock", "side"), "right");
        return values;
    }
    private static void localRadioHandoff() throws Exception {
        Object fixture = rig();
        StatusBarFlowLayout flow = (StatusBarFlowLayout) field(fixture, "flow");
        StatusBarFlowLayout.NativeAccess access = (StatusBarFlowLayout.NativeAccess) field(fixture, "nativeAccess");
        ViewGroup root = (ViewGroup) field(fixture, "root"), icons = (ViewGroup) field(fixture, "icons");
        View wifi = (View) field(fixture, "wifi"), mobile = (View) field(fixture, "mobile");
        flow.configure(placement()); flow.afterLayout(root);
        float settled = x(mobile);
        Field eligible = wifi.getClass().getDeclaredField("eligible"); eligible.setAccessible(true); eligible.setBoolean(wifi, false);
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(flow);
        try {
            callbacks.updateStates(icons, () -> {
                access.x(access.state(wifi), 125f); access.visible(access.state(wifi), 2);
                access.x(access.state(mobile), 145f); access.visible(access.state(mobile), 0);
                flow.prepareTargets(icons);
                callbacks.applyState(access.state(mobile), mobile, () -> { access.apply(access.state(mobile), mobile); return null; });
                return null;
            });
        } catch (Throwable failure) { throw new AssertionError(failure); }
        near(settled, x(mobile), "removing Wi-Fi keeps the surviving configured mobile position until one complete root transaction");
        check(root.layoutRequests == 1, "one local membership change requests one complete root transaction");
        flow.releaseRuntime();
    }
    private static void repeatedHandoff() throws Exception {
        Object fixture = rig();
        StatusBarFlowLayout flow = (StatusBarFlowLayout) field(fixture, "flow");
        StatusBarFlowLayout.NativeAccess access = (StatusBarFlowLayout.NativeAccess) field(fixture, "nativeAccess");
        ViewGroup root = (ViewGroup) field(fixture, "root"), icons = (ViewGroup) field(fixture, "icons");
        View wifi = (View) field(fixture, "wifi"), mobile = (View) field(fixture, "mobile");
        Field eligible = wifi.getClass().getDeclaredField("eligible"); eligible.setAccessible(true);
        Method pass = fixture.getClass().getDeclaredMethod("pass"); pass.setAccessible(true);
        flow.configure(placement()); pass.invoke(fixture);
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(flow);
        for (int frame = 0; frame < 100; frame++) {
            float settled = x(mobile); int requests = root.layoutRequests;
            boolean available = frame % 2 != 0; eligible.setBoolean(wifi, available);
            boolean cloudHidden = frame % 3 == 0;
            try {
                callbacks.updateStates(icons, () -> {
                    access.x(access.state(wifi), 125f); access.visible(access.state(wifi), available ? 0 : 2);
                    access.x(access.state(mobile), 145f); access.visible(access.state(mobile), 0);
                    flow.prepareTargets(icons);
                    // Seedling runs after the projected targets. Its hide/displacement
                    // must remain visible to the ordinary native apply in this call.
                    if (cloudHidden) { access.x(access.state(mobile), access.x(access.state(mobile)) + 6f); access.visible(access.state(mobile), 2); }
                    mobile.setAlpha(cloudHidden ? .35f : 1f);
                    callbacks.applyState(access.state(mobile), mobile, () -> { access.apply(access.state(mobile), mobile); return null; });
                    callbacks.applyState(access.state(wifi), wifi, () -> { access.apply(access.state(wifi), wifi); return null; });
                    return null;
                });
            } catch (Throwable failure) { throw new AssertionError(failure); }
            near(settled + (cloudHidden ? 6f : 0f), x(mobile), "radio handoff retains its configured basis and the current native cloud displacement, frame=" + frame);
            check(access.visible(access.state(mobile)) == (cloudHidden ? 2 : 0), "native cloud visibility remains authoritative, frame=" + frame);
            check(access.visible(access.state(wifi)) == (available ? 0 : 2), "an absent Wi-Fi cannot be promoted by its retained old endpoint, frame=" + frame);
            near(cloudHidden ? .35f : 1f, mobile.getAlpha(), "continuity cannot write native animated alpha, frame=" + frame);
            check(root.layoutRequests == requests + 1, "radio handoff requests one finite full transaction, frame=" + frame);
            pass.invoke(fixture);
        }
        flow.releaseRuntime();
    }
    private static void changedChildIsNotReused() throws Exception {
        for (boolean replacement : new boolean[]{false, true}) {
            Object fixture = rig();
            StatusBarFlowLayout flow = (StatusBarFlowLayout) field(fixture, "flow");
            StatusBarFlowLayout.NativeAccess access = (StatusBarFlowLayout.NativeAccess) field(fixture, "nativeAccess");
            ViewGroup root = (ViewGroup) field(fixture, "root"), icons = (ViewGroup) field(fixture, "icons");
            View wifi = (View) field(fixture, "wifi"), mobile = (View) field(fixture, "mobile");
            flow.configure(placement()); flow.afterLayout(root);
            Field eligible = wifi.getClass().getDeclaredField("eligible"); eligible.setAccessible(true); eligible.setBoolean(wifi, false);
            if (replacement) {
                Object oldState = access.state(mobile);
                Constructor<?> constructor = oldState.getClass().getDeclaredConstructor(float.class); constructor.setAccessible(true);
                Field state = mobile.getClass().getDeclaredField("state"); state.setAccessible(true); state.set(mobile, constructor.newInstance(145f));
            } else {
                Object geometry = field(mobile, "geometry"); Field width = geometry.getClass().getDeclaredField("measuredWidth"); width.setAccessible(true); width.setInt(geometry, 40);
            }
            StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(flow);
            try {
                callbacks.updateStates(icons, () -> {
                    access.x(access.state(mobile), 145f); flow.prepareTargets(icons);
                    callbacks.applyState(access.state(mobile), mobile, () -> { access.apply(access.state(mobile), mobile); return null; });
                    return null;
                });
            } catch (Throwable failure) { throw new AssertionError(failure); }
            near(145f, x(mobile), "a changed native child cannot reuse the retired allocation, replacement=" + replacement);
            flow.releaseRuntime();
        }
    }
    public static final class Coordinator {
        StatusBarFlowLayout.NativeAccess access;View target;float start,end,observed;int calls;boolean animate;
        public void calculate(Object location,ViewGroup parent,boolean animate){
            calls++;this.animate=animate;Object state=access.state(target);
            observed=x(parent)+target.getLeft()+access.x(state);
            // Mirrors the native cloud overlap decision on the final physical interval.
            access.visible(state,observed<end&&observed+target.getWidth()>start?2:0);
        }
    }
    private static void cloudUsesCommittedGeometry()throws Exception{
        Object fixture=rig();StatusBarFlowLayout flow=(StatusBarFlowLayout)field(fixture,"flow");
        StatusBarFlowLayout.NativeAccess access=(StatusBarFlowLayout.NativeAccess)field(fixture,"nativeAccess");
        ViewGroup root=(ViewGroup)field(fixture,"root"),icons=(ViewGroup)field(fixture,"icons");View wifi=(View)field(fixture,"wifi");
        flow.configure(placement());flow.afterLayout(root);float target=x(wifi);
        Coordinator coordinator=new Coordinator();coordinator.access=access;coordinator.target=wifi;
        Method method=Coordinator.class.getMethod("calculate",Object.class,ViewGroup.class,boolean.class);Object location=new Object();
        StatusBarFlowCallbacks callbacks=new StatusBarFlowCallbacks(flow);
        for(int i=0;i<6;i++){
            boolean overlap=i%2==0;coordinator.start=overlap?target-1:target+100;coordinator.end=coordinator.start+30;
            try{callbacks.dispatch(root,false,()->{
                callbacks.updateStates(icons,()->{
                    access.x(access.state(wifi),125);access.visible(access.state(wifi),0);
                    check(flow.deferCoordinator(icons,coordinator,method,location,true),"root transaction queues the exact native coordinator");
                    int before=coordinator.calls;
                    callbacks.applyState(access.state(wifi),wifi,()->{access.apply(access.state(wifi),wifi);return null;});
                    check(coordinator.calls==before,"coordinator never evaluates intermediate native placement");return null;
                });return null;
            });}catch(Throwable failure){throw new AssertionError(failure);}
            check(coordinator.calls==i+1&&coordinator.animate,"one original cloud decision preserves its animation flag per root pass");
            near(target,coordinator.observed,"cloud overlap reads the committed custom icon location");
            check(access.visible(access.state(wifi))==(overlap?2:0),"cloud covers only the actual icon interval");
            try{callbacks.dispatch(root,false,()->null);}catch(Throwable failure){throw new AssertionError(failure);}
            check(coordinator.calls==i+1,"a root-only relayout does not fabricate another cloud update");
            check(access.visible(access.state(wifi))==(overlap?2:0),"root-only relayout retains the last authoritative cloud hide");
        }
        check(!flow.deferCoordinator(icons,coordinator,method,location,false),"local native cloud updates remain synchronous");flow.releaseRuntime();
    }
    private static void marginOnlyRootCycles()throws Exception{
        for(float margin:new float[]{-13f,9f}){
            Object fixture=rig();StatusBarFlowLayout flow=(StatusBarFlowLayout)field(fixture,"flow");
            StatusBarFlowLayout.NativeAccess access=(StatusBarFlowLayout.NativeAccess)field(fixture,"nativeAccess");
            ViewGroup root=(ViewGroup)field(fixture,"root"),icons=(ViewGroup)field(fixture,"icons");
            View wifi=(View)field(fixture,"wifi"),mobile=(View)field(fixture,"mobile");
            Bundle values=new Bundle();values.putFloat(StatusBarLayoutSettings.key("wifi","margin_left"),margin);
            values.putFloat(StatusBarLayoutSettings.key("mobile","margin_right"),-margin);
            flow.configure(values);flow.afterLayout(root);float initialWifi=x(wifi),initialMobile=x(mobile);
            StatusBarFlowCallbacks callbacks=new StatusBarFlowCallbacks(flow);
            for(int frame=0;frame<40;frame++){
                try{
                    callbacks.dispatch(root,true,()->{
                        callbacks.updateStates(icons,()->{
                            access.x(access.state(wifi),125);access.x(access.state(mobile),145);flow.prepareTargets(icons);
                            callbacks.applyState(access.state(wifi),wifi,()->{access.apply(access.state(wifi),wifi);return null;});return null;
                        });return null;
                    });
                    near(initialWifi,x(wifi),"a measure-only visibility traversal cannot expose the unmodified icon target");
                    callbacks.dispatch(root,false,()->null);
                    callbacks.updateStates(icons,()->{
                        access.x(access.state(wifi),125f);access.x(access.state(mobile),145f);flow.prepareTargets(icons);
                        callbacks.applyState(access.state(wifi),wifi,()->{access.apply(access.state(wifi),wifi);return null;});
                        callbacks.applyState(access.state(mobile),mobile,()->{access.apply(access.state(mobile),mobile);return null;});return null;
                    });
                }catch(Throwable failure){throw new AssertionError(failure);}
                near(initialWifi,x(wifi),"margin-only layout remains stable through measure/root-only/native icon updates: "+frame);
                near(initialMobile,x(mobile),"neighbour margins do not accumulate during shade/wake traversals: "+frame);
            }
            flow.releaseRuntime();
        }
    }
    public static void main(String[] arguments) throws Exception {
        marginOnlyRootCycles();
        cloudUsesCommittedGeometry();
        interruptedCloudTransaction();
        unchangedSettingsKeepPlacement();
        localRadioHandoff(); repeatedHandoff(); changedChildIsNotReused();
        System.out.println("StatusBarFlowContinuityCheck: " + checks + " checks passed");
    }
    private static void unchangedSettingsKeepPlacement()throws Exception{
        Object fixture=rig();StatusBarFlowLayout flow=(StatusBarFlowLayout)field(fixture,"flow");ViewGroup root=(ViewGroup)field(fixture,"root");
        View wifi=(View)field(fixture,"wifi");Bundle values=placement();flow.configure(values);flow.afterLayout(root);
        float before=x(wifi);int layouts=root.layoutRequests;
        for(int i=0;i<10;i++){Bundle update=new Bundle(values);update.putString("unrelated_plugin_status","state-"+i);flow.configure(update);
            near(before,x(wifi),"unrelated settings cannot expose native placement between traversals");check(root.layoutRequests==layouts,"unchanged geometry does not request redundant root layouts");}
        values.putFloat(StatusBarLayoutSettings.key("wifi","margin_left"),7);flow.configure(values);
        check(root.layoutRequests==layouts+1,"actual margin changes still request native measurement and layout");flow.releaseRuntime();
    }
    private static void interruptedCloudTransaction()throws Exception{
        for(int mode=0;mode<3;mode++){
            final int stop=mode;Object fixture=rig();StatusBarFlowLayout flow=(StatusBarFlowLayout)field(fixture,"flow");
            StatusBarFlowLayout.NativeAccess access=(StatusBarFlowLayout.NativeAccess)field(fixture,"nativeAccess");
            ViewGroup root=(ViewGroup)field(fixture,"root"),icons=(ViewGroup)field(fixture,"icons");View wifi=(View)field(fixture,"wifi");
            flow.configure(placement());flow.afterLayout(root);
            Coordinator coordinator=new Coordinator();coordinator.access=access;coordinator.target=wifi;coordinator.start=0;coordinator.end=1000;
            Method method=Coordinator.class.getMethod("calculate",Object.class,ViewGroup.class,boolean.class);Object location=new Object();
            StatusBarFlowCallbacks callbacks=new StatusBarFlowCallbacks(flow);
            try{callbacks.dispatch(root,false,()->{
                callbacks.updateStates(icons,()->{
                    access.x(access.state(wifi),125);access.visible(access.state(wifi),0);
                    check(flow.deferCoordinator(icons,coordinator,method,location,false),"native cloud pending before interruption");
                    callbacks.applyState(access.state(wifi),wifi,()->{access.apply(access.state(wifi),wifi);return null;});return null;
                });
                if(stop==0)flow.releaseRuntime();else if(stop==1)flow.configure(new Bundle());else flow.detached(root);
                check(coordinator.calls==1&&!coordinator.animate,"release, disable and detach each flush the original native cloud decision once");
                near(x(icons)+wifi.getLeft()+access.x(access.state(wifi)),coordinator.observed,"interrupted decision uses restored native geometry");
                check(access.visible(access.state(wifi))==2,"interruption preserves native cloud hiding");return null;
            });}catch(Throwable failure){throw new AssertionError(failure);}
            check(coordinator.calls==1,"completed outer callback cannot replay a flushed cloud decision");flow.releaseRuntime();
        }
    }
}
