package dev.puitheme;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Controlled boot/provider/main/worker boundaries, without device preferences. */
public final class SettingsStartupLoaderCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Startup "+checks+": "+expected+" != "+actual);
    }
    private static final class Timer {
        final Runnable task;final long at;boolean cancelled;
        Timer(Runnable task,long at){this.task=task;this.at=at;}
    }
    private static final class Loop implements SettingsStartupLoader.Scheduler {
        final ArrayDeque<Runnable> main=new ArrayDeque<>(),worker=new ArrayDeque<>();
        final List<Timer> timers=new ArrayList<>();
        final List<Long> delays=new ArrayList<>();
        long now;int lane,closed,maxWorkers;
        public void main(Runnable task){main.add(task);}
        public void worker(Runnable task){worker.add(task);maxWorkers=Math.max(maxWorkers,worker.size());}
        public void after(Runnable task,long delay){timers.add(new Timer(task,now+delay));delays.add(delay);}
        public void cancel(Runnable task){for(Timer timer:timers)if(timer.task==task)timer.cancelled=true;}
        public void close(){closed++;}
        void mains(){lane=1;while(!main.isEmpty())main.remove().run();lane=0;}
        void worker(){lane=2;worker.remove().run();lane=0;mains();}
        boolean timer(){Timer next=null;for(Timer timer:timers)if(!timer.cancelled&&(next==null||timer.at<next.at))next=timer;if(next==null)return false;next.cancelled=true;now=next.at;lane=1;next.task.run();lane=0;mains();return true;}
        int pendingTimers(){int n=0;for(Timer t:timers)if(!t.cancelled)n++;return n;}
        void late(Timer timer){lane=1;timer.task.run();lane=0;mains();}
    }
    private static final class Fixture {
        final Loop loop=new Loop();final SettingsStartupLoader loader;
        Bundle offered,applied;boolean removed,observed=true,failRead,failApply,accept=true;
        int reads,applies,prepares;final List<Boolean> forces=new ArrayList<>();
        Fixture(){loader=new SettingsStartupLoader(loop,()->{
            equal(2,loop.lane);reads++;if(failRead)throw new IllegalStateException("provider unavailable");return offered==null?null:new Bundle(offered);
        },(snapshot,force)->{
            equal(1,loop.lane);applies++;forces.add(force);if(failApply)throw new IllegalStateException("consumer not ready");applied=new Bundle(snapshot);return accept;
        },()->removed,()->{equal(1,loop.lane);prepares++;return observed;});}
        void start(){loader.start();loop.mains();}
        void signal(boolean force){loader.signal(force);loop.mains();}
        void finish(){loop.worker();}
    }
    private static Bundle saved(boolean safe) {
        java.util.Map<String,Object> values=new java.util.HashMap<>();
        values.put(StatusBarSettings.SAFE_MODE,safe);values.put("data_enabled",true);
        values.put(StatusBarSettings.DATA_OFFSET_X,-8.24f);values.put("clock_enabled",false);
        return SettingsSnapshot.fromPreferences(values);
    }
    public static void main(String[] args) {
        Fixture boot=new Fixture();boot.start();equal(1,boot.loop.worker.size());
        for(int view=0;view<10000;view++)boot.loader.start();boot.loop.mains();equal(1,boot.loop.worker.size());
        boot.failRead=true;boot.finish();equal(1,boot.loop.pendingTimers());equal(250L,boot.loop.delays.get(0));
        boot.loop.timer();boot.failRead=false;boot.finish();equal(500L,boot.loop.delays.get(1));
        boot.offered=saved(true);boot.loop.timer();boot.finish();equal(3,boot.reads);equal(1,boot.applies);equal(0,boot.loop.pendingTimers());
        equal(true,boot.applied.get(StatusBarSettings.SAFE_MODE));equal(true,boot.applied.get("data_enabled"));equal(false,boot.applied.get("clock_enabled"));equal(-8.24f,boot.applied.get(StatusBarSettings.DATA_OFFSET_X));equal(1,boot.loop.maxWorkers);
        equal(true,SettingsSnapshot.complete(boot.applied));equal(false,FeatureOptions.from(SafetyMode.runtimeSettings(boot.applied)).enabled("data"));
        // Saved enabled flags and safe mode are delivered unchanged; runtime safety
        // gates remain outside the loader, with no preferences writes or forced ON.
        equal(true,boot.offered.get("data_enabled"));equal(true,boot.offered.get(StatusBarSettings.SAFE_MODE));

        Fixture exhausted=new Fixture();exhausted.start();exhausted.finish();
        while(exhausted.loop.timer())exhausted.finish();
        equal(8,exhausted.reads);equal(7,exhausted.loop.delays.size());equal(31750L,exhausted.loop.now);equal(0,exhausted.applies);equal(0,exhausted.loop.pendingTimers());
        for(int frame=0;frame<10000;frame++)exhausted.loader.start();exhausted.loop.mains();equal(8,exhausted.reads);equal(0,exhausted.loop.worker.size());
        exhausted.offered=saved(false);exhausted.signal(false);exhausted.finish();equal(9,exhausted.reads);equal(1,exhausted.applies);equal(0,exhausted.loop.pendingTimers());

        Fixture stale=new Fixture();stale.offered=saved(true);stale.start();
        for(int update=0;update<100;update++)stale.signal(update==99);
        equal(1,stale.loop.worker.size());stale.finish();equal(0,stale.applies);equal(1,stale.loop.worker.size());
        stale.offered=saved(false);stale.finish();equal(1,stale.applies);equal(false,stale.applied.get(StatusBarSettings.SAFE_MODE));equal(true,stale.forces.get(0));equal(1,stale.loop.maxWorkers);
        // Same snapshot is still ready, while explicit new-consumer binding carries force.
        stale.signal(false);stale.finish();equal(false,stale.forces.get(1));stale.signal(true);stale.finish();equal(true,stale.forces.get(2));equal(0,stale.loop.pendingTimers());

        Fixture observe=new Fixture();observe.offered=saved(false);observe.observed=false;observe.start();observe.finish();
        equal(1,observe.applies);equal(1,observe.loop.pendingTimers());
        observe.observed=true;observe.offered=saved(true);observe.loop.timer();observe.finish();equal(2,observe.reads);equal(true,observe.applied.get(StatusBarSettings.SAFE_MODE));equal(0,observe.loop.pendingTimers());
        Fixture applyFailed=new Fixture();applyFailed.offered=saved(true);applyFailed.failApply=true;applyFailed.start();applyFailed.finish();equal(null,applyFailed.applied);equal(1,applyFailed.loop.pendingTimers());applyFailed.failApply=false;applyFailed.loop.timer();applyFailed.finish();equal(true,applyFailed.applied.get(StatusBarSettings.SAFE_MODE));equal(0,applyFailed.loop.pendingTimers());
        Fixture retryApply=new Fixture();retryApply.offered=saved(false);retryApply.accept=false;retryApply.start();retryApply.finish();equal(1,retryApply.loop.pendingTimers());retryApply.accept=true;retryApply.loop.timer();retryApply.finish();equal(0,retryApply.loop.pendingTimers());

        Fixture cancel=new Fixture();cancel.start();cancel.finish();Timer old=cancel.loop.timers.get(0);cancel.offered=saved(false);cancel.signal(false);cancel.loop.late(old);equal(1,cancel.loop.worker.size());cancel.finish();equal(1,cancel.applies);equal(0,cancel.loop.pendingTimers());
        Fixture stop=new Fixture();stop.offered=saved(false);stop.start();stop.loader.stop();stop.finish();equal(0,stop.reads);equal(0,stop.applies);equal(1,stop.loop.closed);stop.loader.start();stop.loader.signal(true);stop.loop.mains();equal(0,stop.loop.worker.size());
        Fixture uninstall=new Fixture();uninstall.start();uninstall.finish();Timer pending=uninstall.loop.timers.get(0);uninstall.removed=true;uninstall.loop.late(pending);equal(1,uninstall.reads);equal(0,uninstall.applies);equal(1,uninstall.loop.closed);
        Fixture lateCompletion=new Fixture();lateCompletion.offered=saved(false);lateCompletion.start();lateCompletion.loop.lane=2;lateCompletion.loop.worker.remove().run();lateCompletion.loop.lane=0;lateCompletion.loader.stop();lateCompletion.loop.mains();equal(0,lateCompletion.applies);equal(0,lateCompletion.loop.pendingTimers());
        freshIpc();
        System.out.println(checks+" checks passed (bounded async boot reads, unlock recovery, observer-before-read, stale generations, safe settings and removal)");
    }
    private static void freshIpc() {
        final class Resolver extends ContentResolver {
            Bundle value;boolean fail;int reads;
            @Override public Bundle call(Uri uri,String method,String arg,Bundle extras){reads++;equal("read_statusbar_settings",method);if(fail)throw new IllegalStateException("provider not ready");return value;}
        }
        Resolver resolver=new Resolver();Context context=new Context(){@Override public ContentResolver getContentResolver(){return resolver;}};
        Bundle previous=saved(true);SettingsSnapshot.rememberApplied(context,previous);
        equal(null,SettingsSnapshot.readFresh(context));equal(true,SettingsSnapshot.lastApplied(context).get(StatusBarSettings.SAFE_MODE));
        resolver.fail=true;equal(null,SettingsSnapshot.readFresh(context));equal(true,SettingsSnapshot.lastApplied(context).get(StatusBarSettings.SAFE_MODE));
        resolver.fail=false;resolver.value=new Bundle();equal(null,SettingsSnapshot.readFresh(context));
        resolver.value=saved(false);Bundle fresh=SettingsSnapshot.readFresh(context);equal(false,fresh.get(StatusBarSettings.SAFE_MODE));equal(true,SettingsSnapshot.lastApplied(context).get(StatusBarSettings.SAFE_MODE));
        fresh.putBoolean(StatusBarSettings.SAFE_MODE,true);equal(false,resolver.value.get(StatusBarSettings.SAFE_MODE));equal(4,resolver.reads);
        equal(null,SettingsSnapshot.readFresh(null));SettingsSnapshot.forgetApplied();
    }
}
