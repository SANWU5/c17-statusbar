// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Parameter/scope checks. Real method signatures and animation ownership are separately DEX-audited. */
public final class NotificationNativeStackCheck {
    private static int checks;
    private static void check(boolean yes){checks++;if(!yes)throw new AssertionError("Native whole stack "+checks);}
    private static void equal(float expected,float actual){check(Math.abs(expected-actual)<.001f);}
    public static class State { public float y;public int height;public float getYTranslation(){return y;} }
    public static class Expandable extends View {
        public final State target=new State();
        Expandable(float y,int height){super(new Context());target.y=y;target.height=height;}
        public State getViewState(){return target;}
    }
    public static class Row extends Expandable {
        public boolean child,removed,mDismissed,mIsUserSwipingToExpandRow,changing;
        Row(float y,int height){super(y,height);}
        public boolean isChildInGroup(){return child;}
        public boolean isRemoved(){return removed;}
        public boolean areGutsExposed(){return changing;}
        public boolean isExpandAnimationRunning(){return false;}
        public boolean isGroupExpansionChanging(){return false;}
    }
    public static final class Dimens {
        public float scale=24,translation=18;
        public float getScaleDampingBaseHeight(){return scale;}
        public float getTranslationDampingBaseHeight(){return translation;}
    }
    public static final class Algorithm {
        public final Dimens dimens=new Dimens();
        public Dimens currentDynamicStackDimens(){return dimens;}
        public static float stableHeightOf(Expandable row){return row.target.height;}
    }
    public static final class Manager {
        private final Algorithm dynamicStackAlgorithm=new Algorithm();
        public void applyDynamic(List<?> rows,float border){}
    }
    public interface ReadFlow {Object getValue();}
    public interface WriteFlow extends ReadFlow {void setValue(Object value);}
    public static final class Flow implements WriteFlow {
        boolean value;int reads,writes;
        public Object getValue(){reads++;return value;}
        public void setValue(Object value){writes++;this.value=Boolean.TRUE.equals(value);}
    }
    public static final class NativeContext extends Context {
        final Resources resources=new Resources();
        int reads;
        NativeContext(){resources.getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;}
        @Override public Resources getResources(){reads++;return resources;}
    }
    public static final class Ruler {
        private final NativeContext context=new NativeContext();
        private final Manager stackAlgorithmManager=new Manager();
        public boolean currentIsStackedNotification;
        private WriteFlow _isStackedNotification=new Flow();
        public boolean getCurrentIsStackedNotification(){return currentIsStackedNotification;}
    }
    public static final class Ambient { public int scroll;public int getScrollY(){return scroll;} }
    public static final class Extension {
        public Object current;
        private Object getCurrentStackRuler(){return current;}
        public void updateStackedNotification(List<?> rows,Ambient ambient,float fraction){}
    }
    private static Bundle settings(float count){Bundle b=new Bundle();b.putBoolean(NotificationBigClockSettings.STACK_ENABLED,true);b.putFloat(NotificationBigClockSettings.VISIBLE_COUNT,count);return b;}
    private static NotificationNativeStack helper(float count)throws Throwable {
        NotificationNativeStack nativeStack=new NotificationNativeStack();nativeStack.configure(settings(count));
        NotificationNativeStack.Access access=new NotificationNativeStack.Access(Extension.class,Ruler.class,
                Manager.class,Algorithm.class,Ambient.class,Row.class,Expandable.class);
        Field field=NotificationNativeStack.class.getDeclaredField("access");field.setAccessible(true);field.set(nativeStack,access);
        nativeStack.hooksReady(true);
        return nativeStack;
    }
    public static void main(String[] args)throws Throwable {
        nativeScrollAndDimensions();scopesAndFallback();independentMasters();nativeSceneAndFlow();liveOrientationAndFlow();
        System.out.println("Notification native stack checks passed: "+checks);
    }
    private static void liveOrientationAndFlow()throws Throwable {
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();Extension extension=new Extension();
        extension.current=ruler;Ambient ambient=new Ambient();List<Row> rows=new ArrayList<>();
        rows.add(new Row(100,120));rows.add(new Row(232,120));Flow flow=(Flow)ruler._isStackedNotification;
        // No landscape opt-in in an old saved snapshot: only portrait is affected.
        check(helper.currentStacked(ruler,false));check(flow.value&&flow.writes==1);
        int resourceReads=ruler.context.reads;
        for(int i=0;i<1000;i++)check(helper.currentStacked(ruler,false));
        check(ruler.context.reads==resourceReads); // Cached handle, live configuration.
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        helper.configurationChanged();check(!flow.value&&flow.writes==2);
        check(!helper.currentStacked(ruler,false));check(helper.currentStacked(ruler,true));
        helper.withNativeUpdate(extension,ambient,()->{
            equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;
        });
        Bundle optIn=settings(1);optIn.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);
        // Landscape clock/group switches cannot substitute the independent whole-stack opt-in.
        Bundle old=settings(1);old.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,true);
        old.putBoolean(NotificationGroupStack.MASTER,true);helper.configure(old);
        check(!helper.currentStacked(ruler,false));
        helper.configure(optIn);check(helper.currentStacked(ruler,false));check(flow.value&&flow.writes==3);
        helper.withNativeUpdate(extension,ambient,()->{
            equal(244,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;
        });
        // A save that disables only landscape restores native collectors immediately.
        helper.configure(settings(1));check(!flow.value&&flow.writes==4);
        helper.configure(optIn);helper.currentStacked(ruler,false);check(flow.value);
        ruler.currentIsStackedNotification=true;helper.configure(settings(1));
        check(flow.value);check(helper.currentStacked(ruler,true)); // Native ON is preserved.
        ruler.currentIsStackedNotification=false;flow.value=false;
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        helper.configurationChanged();check(helper.currentStacked(ruler,false));check(flow.value);
        // Rotation without any clock callback is also enforced in both native calculation and getter.
        helper.withNativeUpdate(extension,ambient,()->{
            ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
            equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));
            check(!helper.currentStacked(ruler,false));check(!flow.value);return null;
        });
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        helper.currentStacked(ruler,false);check(flow.value);
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_UNDEFINED;
        helper.configurationChanged();check(!flow.value);check(!helper.currentStacked(ruler,false));
        helper.configure(optIn);check(!helper.currentStacked(ruler,false)); // Unknown scene stays native.
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        Bundle masterOff=new Bundle();masterOff.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);
        helper.configure(masterOff);check(!helper.currentStacked(ruler,false));
        Bundle safe=settings(1);safe.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);
        safe.putBoolean(StatusBarSettings.SAFE_MODE,true);helper.configure(safe);check(!helper.currentStacked(ruler,false));
        helper.configure(optIn);helper.currentStacked(ruler,false);check(flow.value);
        helper.hooksReady(false);check(!flow.value);check(!helper.currentStacked(ruler,false));
    }
    private static void nativeSceneAndFlow()throws Throwable {
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();Extension extension=new Extension();
        extension.current=ruler;Ambient ambient=new Ambient();List<Row> rows=new ArrayList<>();
        rows.add(new Row(120,100));rows.add(new Row(232,100));
        // Actual native target update succeeds before any clock/state/fraction callback.
        helper.withNativeUpdate(extension,ambient,()->{
            equal(244,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;
        });
        Flow flow=(Flow)ruler._isStackedNotification;check(!flow.value);
        check(helper.currentStacked(ruler,false));check(flow.value&&flow.writes==1);
        int reads=flow.reads;
        for(int i=0;i<2000;i++)check(helper.currentStacked(ruler,false));
        check(flow.reads==reads&&flow.writes==1); // No stable-frame reflection/collector writes.
        ruler.currentIsStackedNotification=true;helper.configure(null);
        check(flow.value&&flow.writes==1); // Restore latest native state, not the old false.
        ruler.currentIsStackedNotification=false;
        helper.configure(settings(1));helper.currentStacked(ruler,false);helper.hooksReady(false);
        check(!flow.value&&flow.writes==2);
        helper.hooksReady(true);helper.currentStacked(ruler,false);check(flow.value);
        helper.reset();check(!flow.value);
        helper.hooksReady(true);ruler._isStackedNotification=null;
        helper.currentStacked(ruler,false); // Actual constructor reads before assigning its flow.
        Flow initialized=new Flow();ruler._isStackedNotification=initialized;
        helper.currentStacked(ruler,false);check(initialized.value&&initialized.writes==1);
        helper.configure(null);check(!initialized.value);
        extension.current=new Object();helper.configure(settings(1));
        helper.withNativeUpdate(extension,ambient,()->{
            equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));
            check(!helper.currentStacked(extension.current,false));return null;
        });
    }
    private static void nativeScrollAndDimensions()throws Throwable {
        for(int complete=1;complete<=5;complete++) {
            NotificationNativeStack helper=helper(complete);Ruler ruler=new Ruler();Extension extension=new Extension();extension.current=ruler;
            Ambient ambient=new Ambient();List<Row> rows=new ArrayList<>();
            float y=180;for(int i=0;i<8;i++){rows.add(new Row(y,100+i*8));y+=100+i*8+12;}
            final float expected=rows.get(complete-1).target.y+rows.get(complete-1).target.height+24;
            for(int scroll=0;scroll<2000;scroll+=7) {
                ambient.scroll=scroll;
                float nativeY=180;for(Row row:rows){row.target.y=nativeY-scroll;nativeY+=row.target.height+12;}
                helper.withNativeUpdate(extension,ambient,true,()->{
                    check(helper.currentStacked(ruler,false));
                    equal(expected,helper.bottomBorder(ruler.stackAlgorithmManager,rows,4000));
                    equal(Math.min(600,expected),helper.bottomBorder(ruler.stackAlgorithmManager,rows,600));
                    for(int i=0;i<rows.size();i++) {
                        Row row=rows.get(i);equal(100+i*8,row.target.height);
                        equal(1f,row.getScaleY());
                        check(row.getClipBounds()==null);
                    }
                    return null;
                });
                check(helper.currentStacked(ruler,false));equal(4000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,4000));
            }
        }
        NotificationNativeStack helper=helper(2);Ruler ruler=new Ruler();Extension extension=new Extension();extension.current=ruler;Ambient ambient=new Ambient();
        Row first=new Row(200,100),summary=new Row(312,420),child=new Row(0,80);child.child=true;
        List<Row> nested=new ArrayList<>();nested.add(first);nested.add(child);nested.add(summary);nested.add(new Row(744,100));
        helper.withNativeUpdate(extension,ambient,true,()->{
            equal(756,helper.bottomBorder(ruler.stackAlgorithmManager,nested,4000));
            equal(420,summary.target.height);equal(0,child.target.y);
            summary.changing=true;equal(4000,helper.bottomBorder(ruler.stackAlgorithmManager,nested,4000));summary.changing=false;
            summary.mIsUserSwipingToExpandRow=true;equal(4000,helper.bottomBorder(ruler.stackAlgorithmManager,nested,4000));summary.mIsUserSwipingToExpandRow=false;
            summary.mDismissed=true;equal(868,helper.bottomBorder(ruler.stackAlgorithmManager,nested,4000));summary.mDismissed=false;
            summary.target.height=0;equal(4000,helper.bottomBorder(ruler.stackAlgorithmManager,nested,4000));summary.target.height=420;
            ruler.stackAlgorithmManager.dynamicStackAlgorithm.dimens.scale=Float.NaN;
            equal(4000,helper.bottomBorder(ruler.stackAlgorithmManager,nested,4000));return null;
        });
    }
    private static void scopesAndFallback()throws Throwable {
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();Extension extension=new Extension();extension.current=ruler;Ambient ambient=new Ambient();
        List<Row> rows=new ArrayList<>();rows.add(new Row(100,120));rows.add(new Row(232,120));
        helper.withNativeUpdate(extension,ambient,true,()->{
            equal(244,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));
            equal(1000,helper.bottomBorder(new Manager(),rows,1000));
            check(helper.currentStacked(new Ruler(),false));check(helper.currentStacked(new Ruler(),true));
            check(!helper.currentStacked(new Object(),false));
            helper.withNativeUpdate(extension,ambient,false,()->{check(helper.currentStacked(ruler,false));equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
            check(helper.currentStacked(ruler,false));
            Extension keyguard=new Extension();keyguard.current=new Object();
            helper.withNativeUpdate(keyguard,ambient,true,()->{check(!helper.currentStacked(keyguard.current,false));check(helper.currentStacked(ruler,false));return null;});
            check(helper.currentStacked(ruler,false));return null;
        });
        try{helper.withNativeUpdate(extension,ambient,true,()->{throw new IllegalStateException("native failed");});throw new AssertionError("swallowed");}
        catch(IllegalStateException expected){check("native failed".equals(expected.getMessage()));}
        check(helper.currentStacked(ruler,false));
        ambient.scroll=-1;helper.withNativeUpdate(extension,ambient,true,()->{check(helper.currentStacked(ruler,false));equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
        check(!helper.resolve(new ClassLoader(null){}));check(helper.updateMethod()==null&&helper.currentMethod()==null&&helper.applyMethod()==null);
        equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));
    }
    private static void independentMasters()throws Throwable {
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();Extension extension=new Extension();extension.current=ruler;Ambient ambient=new Ambient();
        List<Row> rows=new ArrayList<>();rows.add(new Row(100,120));rows.add(new Row(232,120));
        for(boolean group:new boolean[]{false,true})for(boolean portrait:new boolean[]{false,true})for(boolean landscape:new boolean[]{false,true}){
            Bundle bundle=settings(1);bundle.putBoolean(NotificationGroupStack.MASTER,group);
            bundle.putBoolean(NotificationBigClockSettings.MASTER,portrait);bundle.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,landscape);
            helper.configure(bundle);helper.withNativeUpdate(extension,ambient,true,()->{equal(244,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
        }
        for(float count:new float[]{0,-20,Float.NaN,Float.POSITIVE_INFINITY,1}){
            helper.configure(settings(count));helper.withNativeUpdate(extension,ambient,true,()->{equal(244,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
        }
        for(float count:new float[]{5,100000,Float.MAX_VALUE}){
            helper.configure(settings(count));helper.withNativeUpdate(extension,ambient,true,()->{equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
        }
        Bundle safe=settings(1);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);helper.configure(safe);
        check(!helper.enabled());check(!helper.currentStacked(ruler,false));helper.withNativeUpdate(extension,ambient,true,()->{equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
        helper.configure(null);check(!helper.enabled());
        helper.configure(settings(1));helper.hooksReady(false);check(!helper.enabled());check(!helper.currentStacked(ruler,false));
        helper.configure(settings(1));check(!helper.enabled()); // Partial hook failure cannot re-enable by saving.
        helper.hooksReady(true);check(helper.enabled());helper.reset();check(!helper.enabled());
    }
}
