// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.window.SplashScreenView;
import dev.puitheme.iosstatusbar.R;

/** Native launch window only; initialization continues behind its bounded visual sequence. */
final class C17LaunchAnimation {
    private static final C17LaunchTiming.Gate FIRST_LAUNCH=new C17LaunchTiming.Gate();
    private C17LaunchAnimation() {}

    /** Call before super.onCreate so the actual app always uses its normal day/night theme. */
    static void install(Activity activity,boolean restored) {
        activity.setTheme(R.style.AppTheme);
        if (Build.VERSION.SDK_INT >= 31) Api31.install(activity,FIRST_LAUNCH.claim(restored));
    }

    @TargetApi(31)
    private static final class Api31 {
        static void install(Activity activity,boolean first) {
            if(!first) {activity.getSplashScreen().setOnExitAnimationListener(SplashScreenView::remove);return;}
            Host host=new Host(activity);
            activity.getSplashScreen().setOnExitAnimationListener(host::ready);
        }
    }

    @TargetApi(31)
    private static final class Host implements C17LaunchTiming.Output,Application.ActivityLifecycleCallbacks,View.OnAttachStateChangeListener {
        private final Activity activity;
        private final View decor;
        private final Handler handler=new Handler(Looper.getMainLooper());
        private final C17LaunchTiming.Session session;
        private SplashScreenView splash;
        private Runnable fadeFrame;
        private long attachedUp=-1L;
        private boolean removed,observing;
        private final ContentObserver animations=new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) {if(!ValueAnimator.areAnimatorsEnabled())session.cancel();}
        };
        Host(Activity activity) {
            this.activity=activity;
            session=new C17LaunchTiming.Session(true,new C17LaunchTiming.Clock() {
                @Override public long uptime(){return SystemClock.uptimeMillis();}
                @Override public long wallTime(){return System.currentTimeMillis();}
            },this);
            decor=activity.getWindow().getDecorView();
            if(decor.isAttachedToWindow())attachedUp=SystemClock.uptimeMillis();
            decor.addOnAttachStateChangeListener(this);
            activity.getApplication().registerActivityLifecycleCallbacks(this);
            try {
                activity.getContentResolver().registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,animations);
                observing=true;
            } catch(SecurityException unavailable) { /* Check again before showing and fading. */ }
        }
        void ready(SplashScreenView view) {
            if(removed){view.remove();return;}
            splash=view;view.addOnAttachStateChangeListener(this);
            // The app is already drawing below this window; keep covered controls untouchable.
            view.setClickable(true);
            if(activity.isFinishing()||activity.isDestroyed()){session.cancel();return;}
            if(attachedUp<0)attachedUp=SystemClock.uptimeMillis();
            Long iconStart=null;
            try {if(view.getIconAnimationStart()!=null)iconStart=view.getIconAnimationStart().toEpochMilli();}
            catch(ArithmeticException invalidPlatformTime) { /* Attached uptime is the bounded fallback. */ }
            session.ready(ValueAnimator.areAnimatorsEnabled(),attachedUp,iconStart);
        }
        @Override public void post(Runnable task,long delay){handler.postDelayed(task,delay);}
        @Override public void cancel(Runnable task){handler.removeCallbacks(task);}
        @Override public void fade(long duration,Runnable finished) {
            if(removed||activity.isFinishing()||activity.isDestroyed()||!ValueAnimator.areAnimatorsEnabled()){session.cancel();return;}
            final long start=SystemClock.uptimeMillis();
            // Native UI-frame callbacks use real uptime, independent of .5x/2x Animator scale.
            fadeFrame=new Runnable() {
                @Override public void run() {
                    if(removed||splash==null)return;
                    if(activity.isFinishing()||activity.isDestroyed()||!ValueAnimator.areAnimatorsEnabled()){session.cancel();return;}
                    long now=SystemClock.uptimeMillis();
                    splash.setAlpha(C17LaunchTiming.fadeAlpha(now,start,duration));
                    if(now-start>=duration)finished.run();else splash.postOnAnimation(this);
                }
            };
            splash.setAlpha(1f);
            splash.postOnAnimation(fadeFrame);
        }
        @Override public void cancelFade(){if(splash!=null&&fadeFrame!=null)splash.removeCallbacks(fadeFrame);fadeFrame=null;}
        @Override public void remove() {
            if(removed)return;removed=true;
            decor.removeOnAttachStateChangeListener(this);
            activity.getApplication().unregisterActivityLifecycleCallbacks(this);
            if(observing){activity.getContentResolver().unregisterContentObserver(animations);observing=false;}
            activity.getSplashScreen().clearOnExitAnimationListener();
            if(splash!=null){splash.removeOnAttachStateChangeListener(this);splash.remove();splash=null;}
        }
        @Override public void onViewAttachedToWindow(View view){if(view==decor&&attachedUp<0)attachedUp=SystemClock.uptimeMillis();}
        @Override public void onViewDetachedFromWindow(View view){session.cancel();}
        @Override public void onActivityPaused(Activity value){if(value==activity)session.cancel();}
        @Override public void onActivityStopped(Activity value){if(value==activity)session.cancel();}
        @Override public void onActivityDestroyed(Activity value){if(value==activity)session.cancel();}
        @Override public void onActivityCreated(Activity value,Bundle state){ }
        @Override public void onActivityStarted(Activity value){ }
        @Override public void onActivityResumed(Activity value){ }
        @Override public void onActivitySaveInstanceState(Activity value,Bundle state){ }
    }
}
