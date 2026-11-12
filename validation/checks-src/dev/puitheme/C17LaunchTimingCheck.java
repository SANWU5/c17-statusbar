// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

/** Exercises the production splash session through a deterministic clock and UI scheduler. */
public final class C17LaunchTimingCheck {
    private static int count;
    private static void check(boolean value,String reason){count++;if(!value)throw new AssertionError(reason);}
    private static void equal(long expected,long actual,String reason){check(expected==actual,reason+": "+actual+" != "+expected);}
    private static final class Harness implements C17LaunchTiming.Clock,C17LaunchTiming.Output {
        long up=1000,wall=100000,due,fadeDuration,fadeStart;
        int posts,removes,cancels,fadeStarts;
        Runnable pending,finished;
        final java.util.Map<Runnable,Long> timers=new java.util.LinkedHashMap<>();
        boolean fading,removed;
        final C17LaunchTiming.Session session;
        Harness(boolean first){session=new C17LaunchTiming.Session(first,this,this);}
        @Override public long uptime(){return up;}
        @Override public long wallTime(){return wall;}
        private void next(){pending=null;due=Long.MAX_VALUE;for(java.util.Map.Entry<Runnable,Long> item:timers.entrySet())if(item.getValue()<due){pending=item.getKey();due=item.getValue();}}
        @Override public void post(Runnable task,long delay){check(!timers.containsKey(task),"a launch callback is never queued twice");timers.put(task,up+delay);posts++;next();}
        @Override public void cancel(Runnable task){timers.remove(task);next();cancels++;}
        @Override public void fade(long duration,Runnable done){check(!removed,"removed window cannot animate");fading=true;fadeStarts++;fadeStart=up;fadeDuration=duration;finished=done;}
        @Override public void cancelFade(){fading=false;}
        @Override public void remove(){removed=true;removes++;}
        void at(long time){wall+=time-up;up=time;}
        void fireAt(long time){Runnable task=pending;check(task!=null,"timer exists");timers.remove(task);next();at(time);task.run();}
        void finishFade(){check(fading,"fade is active");at(fadeStart+fadeDuration);finished.run();}
    }
    public static void main(String[] args) {
        // Native animation has played 100ms when the app becomes ready. It must finish at 1500ms,
        // rather than the old first-ready-frame + 160ms or an added fresh 1500ms.
        Harness early=new Harness(true);early.session.ready(true,900,99900L);
        equal(2,early.posts,"early ready queues only fade and deadline one-shots");equal(2200,early.due,"hold uses real icon start");
        check(!early.removed&&!early.fading,"first app frame does not cut the animation short");
        early.fireAt(2200);equal(200,early.fadeDuration,"exit is included in total budget");
        early.finishFade();equal(2400,early.up,"native start at uptime900 ends at2400");
        equal(1,early.removes,"normal completion removes once");check(early.pending==null&&!early.fading,"normal completion leaves no work");

        Harness late=new Harness(true);late.session.ready(true,999,98000L);
        equal(0,late.posts,"slow startup gets no new wait");equal(0,late.fadeStarts,"already spent budget is not extended by fade");equal(1,late.removes,"slow startup removes immediately");
        Harness nearEnd=new Harness(true);nearEnd.session.ready(true,999,98550L);
        equal(1,nearEnd.posts,"remaining final50ms needs only a deadline");equal(50,nearEnd.fadeDuration,"late fade spends only the remaining50ms");nearEnd.finishFade();equal(1050,nearEnd.up,"late total deadline respected");

        Harness fallback=new Harness(true);fallback.session.ready(true,700,null);
        equal(2000,fallback.due,"missing native time uses monotonic attached uptime");
        fallback.fireAt(2000);fallback.finishFade();equal(2200,fallback.up,"fallback attached700 +1500");
        Harness preferred=new Harness(true);preferred.session.ready(true,1000,99100L);
        equal(1400,preferred.due,"native start overrides later app attachment");preferred.session.cancel();
        Harness future=new Harness(true);future.session.ready(true,500,100100L);
        equal(1800,future.due,"future wall time falls back to attached clock");future.session.cancel();

        Harness off=new Harness(true);off.session.ready(false,1000,100000L);
        equal(0,off.posts,"animations disabled never wait");equal(0,off.fadeStarts,"animations disabled never fade");equal(1,off.removes,"animations disabled immediately remove");
        Harness replay=new Harness(false);replay.session.ready(true,1000,100000L);
        equal(0,replay.posts,"restored or later activity never waits");equal(1,replay.removes,"restored activity clears any system splash");

        Harness paused=new Harness(true);paused.session.ready(true,1000,100000L);
        Runnable stale=paused.pending;paused.session.cancel();
        equal(1,paused.removes,"pause/destroy cancellation removes");check(paused.pending==null,"pause removes pending timer");
        stale.run();paused.session.cancel();equal(0,paused.fadeStarts,"stale callback after pause cannot start fade");equal(1,paused.removes,"repeat cancellation is idempotent");
        Harness duringFade=new Harness(true);duringFade.session.ready(true,1000,100000L);duringFade.fireAt(2300);
        Runnable staleEnd=duringFade.finished;duringFade.session.cancel();check(!duringFade.fading,"leaving or disabling animations cancels active fade");
        staleEnd.run();equal(1,duringFade.removes,"late animation end cannot double-remove");
        Harness beforeReady=new Harness(true);beforeReady.session.cancel();beforeReady.session.ready(true,1000,100000L);
        equal(0,beforeReady.posts,"leaving before exit callback cannot revive splash");equal(1,beforeReady.removes,"before-ready cleanup runs once");

        Harness duplicate=new Harness(true);duplicate.session.ready(true,1000,100000L);duplicate.session.ready(true,1000,100000L);
        equal(2,duplicate.posts,"duplicate ready callback does not reschedule either task");duplicate.session.cancel();
        Harness delayed=new Harness(true);delayed.session.ready(true,1000,100000L);delayed.fireAt(2700);
        equal(0,delayed.fadeStarts,"late UI callback does not append200ms");equal(1,delayed.removes,"late UI callback immediately removes");
        Harness wallJump=new Harness(true);wallJump.session.ready(true,1000,100000L);wallJump.wall+=86400000L;
        wallJump.fireAt(2300);equal(200,wallJump.fadeDuration,"wall-clock changes cannot reset the monotonic deadline");wallJump.finishFade();equal(2500,wallJump.up,"wall-clock jump keeps original deadline");

        Harness halfScale=new Harness(true);halfScale.session.ready(true,1000,100000L);halfScale.fireAt(2300);
        halfScale.at(2400);halfScale.finished.run();check(!halfScale.removed,".5x Animator finish cannot shorten total presentation");
        equal(2500,halfScale.due,"early fade completion retains original deadline");halfScale.fireAt(2500);
        equal(1,halfScale.removes,".5x removes once at exactly1500ms");check(halfScale.timers.isEmpty(),".5x leaves no queued callback");
        Harness doubleScale=new Harness(true);doubleScale.session.ready(true,1000,100000L);doubleScale.fireAt(2300);
        check(doubleScale.fading,"2x Animator may still be running at deadline");doubleScale.fireAt(2500);
        check(doubleScale.removed&&!doubleScale.fading,"2x is cancelled by absolute deadline");
        doubleScale.at(2700);doubleScale.finished.run();equal(1,doubleScale.removes,"2x late Animator completion cannot remove twice");check(doubleScale.timers.isEmpty(),"2x leaves no queued callback");

        C17LaunchTiming.Gate gate=new C17LaunchTiming.Gate();
        check(gate.claim(false),"first cold activity claims sequence");check(!gate.claim(false),"new activity in same process cannot replay");check(!gate.claim(true),"rotation cannot replay");
        C17LaunchTiming.Gate restored=new C17LaunchTiming.Gate();
        check(!restored.claim(true),"saved state restored after process recreation skips forced presentation");check(!restored.claim(false),"later launch after restore remains consumed");
        equal(1000,Math.round(C17LaunchTiming.fadeAlpha(1300,1300,200)*1000),"absolute fade starts fully visible");
        equal(250,Math.round(C17LaunchTiming.fadeAlpha(1400,1300,200)*1000),"absolute half-time fade retains25percent alpha at .5x/1x/2x");
        equal(0,Math.round(C17LaunchTiming.fadeAlpha(1500,1300,200)*1000),"absolute fade ends with1500ms deadline");
        equal(0,Math.round(C17LaunchTiming.fadeAlpha(1550,1300,200)*1000),"late frame alpha is clamped");
        equal(1000,Math.round(C17LaunchTiming.fadeAlpha(1250,1300,200)*1000),"early frame alpha is clamped");
        System.out.println("C17LaunchTimingCheck: "+count+" checks passed");
    }
}
