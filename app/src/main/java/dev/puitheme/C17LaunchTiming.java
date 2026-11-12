// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

/** Clock/scheduler seam shared by the native splash and desktop lifecycle checks. */
final class C17LaunchTiming {
    static final long TOTAL_MS = 1500L, FADE_MS = 200L;
    interface Clock { long uptime(); long wallTime(); }
    interface Output {
        void post(Runnable task,long delay);
        void cancel(Runnable task);
        void fade(long duration,Runnable finished);
        void cancelFade();
        void remove();
    }
    static final class Gate {
        private boolean claimed;
        boolean claim(boolean restored) { boolean first=!claimed&&!restored;claimed=true;return first; }
    }
    static final class Plan {
        final long hold,fade;
        Plan(long hold,long fade) {this.hold=hold;this.fade=fade;}
    }
    static Plan plan(boolean first,boolean animations,long nowUp,long attachedUp,long nowWall,Long iconStart) {
        if(!first||!animations)return new Plan(0,0);
        long elapsed=elapsed(nowUp,attachedUp);
        if(iconStart!=null&&iconStart<=nowWall)elapsed=elapsed(nowWall,iconStart);
        long remaining=elapsed>=TOTAL_MS?0:TOTAL_MS-elapsed;
        long fade=Math.min(FADE_MS,remaining);
        return new Plan(remaining-fade,fade);
    }
    private static long elapsed(long now,long start) {
        if(start<0||now<start)return 0;
        long value=now-start;return value<0?Long.MAX_VALUE:value;
    }
    static float fadeAlpha(long now,long start,long duration) {
        if(duration<=0)return 0f;
        float remaining=1f-Math.min(1f,Math.max(0f,(now-start)/(float)duration));
        return remaining*remaining;
    }
    static final class Session {
        private final boolean first;
        private final Clock clock;
        private final Output output;
        private boolean ready,removed;
        private long deadline;
        private final Runnable fadeTask=this::fade;
        private final Runnable removeTask=this::cancel;
        Session(boolean first,Clock clock,Output output) {this.first=first;this.clock=clock;this.output=output;}
        void ready(boolean animations,long attachedUp,Long iconStart) {
            if(ready||removed)return;
            ready=true;
            long now=clock.uptime();
            Plan plan=plan(first,animations,now,attachedUp,clock.wallTime(),iconStart);
            if(plan.hold+plan.fade==0){cancel();return;}
            deadline=now+plan.hold+plan.fade;
            // An independent one-shot UI deadline is unaffected by the system Animator scale.
            output.post(removeTask,plan.hold+plan.fade);
            if(plan.hold>0)output.post(fadeTask,plan.hold);else fade();
        }
        private void fade() {
            if(removed)return;
            // A delayed main-thread callback spends the remaining budget, never adds a fresh fade.
            long remaining=Math.max(0L,deadline-clock.uptime());
            if(remaining==0){cancel();return;}
            output.fade(Math.min(FADE_MS,remaining),() -> {
                // At .5x the fade may finish early; at 2x the deadline cancels it first.
                if(clock.uptime()>=deadline)cancel();
            });
        }
        void cancel() {
            if(removed)return;
            removed=true;
            output.cancel(fadeTask);
            output.cancel(removeTask);
            output.cancelFade();
            output.remove();
        }
    }
    private C17LaunchTiming() { }
}
