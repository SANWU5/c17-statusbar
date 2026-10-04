// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.phone.NotificationIconContainer;
import com.oplus.systemui.statusbar.widget.StatClock;

/** Native allocation wins over hidden children; only zero allocation needs visible-state recovery. */
public final class NativeClockBudgetCheck {
    private static int checks;
    private static final int ICON=0, DOT=1, HIDDEN=2;

    private static final class ClockResources extends Resources {
        @Override public String getResourceEntryName(int id) {
            switch(id) {
                case 0x7f0a0dfb: return "status_bar_start_side_container";
                case 0x7f0a0dfc: return "status_bar_start_side_content";
                case 0x7f0a0dfe: return "status_bar_start_side_except_heads_up";
                case 0x7f0a0307: return "clock_for_fake";
                default: return "notification_icon_area";
            }
        }
        @Override public String getResourcePackageName(int id) { return "com.android.systemui"; }
    }

    private static final class ClockContext extends Context {
        final Resources resources=new ClockResources();
        @Override public Resources getResources() { return resources; }
    }

    private static final class FrameNode extends FrameLayout {
        int measuredWidth, width, paddingLeft;
        final int id;
        FrameNode(Context context,int allocated,int id) { super(context); measuredWidth=width=allocated; this.id=id; }
        @Override public int getMeasuredWidth() { return measuredWidth; }
        @Override public int getWidth() { return width; }
        @Override public int getPaddingLeft() { return paddingLeft; }
        @Override public int getId() { return id; }
        @Override public Resources getResources() { return getContext().getResources(); }
    }

    private static final class LinearNode extends LinearLayout {
        LinearNode(Context context) { super(context); }
        @Override public int getMeasuredWidth() { return 0; }
        @Override public int getWidth() { return 0; }
        @Override public int getId() { return 0x7f0a0dfe; }
        @Override public Resources getResources() { return getContext().getResources(); }
    }

    private static final class NativeIcons extends NotificationIconContainer {
        int measuredWidth, width;
        float actualPaddingStart, actualPaddingEnd;
        NativeIcons(Context context) { super(context); }
        @Override public int getMeasuredWidth() { return measuredWidth; }
        @Override public int getWidth() { return width; }
        @Override public float getActualPaddingStart() { return actualPaddingStart; }
        @Override public float getActualPaddingEnd() { return actualPaddingEnd; }
    }

    private static final class Glyph extends StatusBarIconView {
        int measuredWidth, width;
        Glyph(Context context,int pixels,int state) { super(context); measuredWidth=width=pixels; visibleState=state; }
        @Override public int getMeasuredWidth() { return measuredWidth; }
        @Override public int getWidth() { return width; }
    }

    private static final class Fixture {
        final ClockContext context=new ClockContext();
        final TextView clock=new TextView(context);
        final FrameNode allocated=new FrameNode(context,666,0x7f0a0dfb);
        final NativeIcons icons=new NativeIcons(context);
        Fixture() {
            allocated.paddingLeft=78;
            FrameNode content=new FrameNode(context,12,0x7f0a0dfc), wrapper=new FrameNode(context,0,0x7f0a0307);
            FrameNode notificationArea=new FrameNode(context,0,0x7f0a0961);
            LinearNode exceptHeadsUp=new LinearNode(context);
            allocated.addView(content); content.addView(exceptHeadsUp);
            exceptHeadsUp.addView(wrapper); exceptHeadsUp.addView(notificationArea);
            wrapper.addView(clock); notificationArea.addView(icons);
        }
        Glyph add(int width,int state) { Glyph glyph=new Glyph(context,width,state); icons.addView(glyph); return glyph; }
        int budget() { return NativeClockMeasurement.availableWidth(clock); }
    }

    private static void equal(int expected,int actual,String scenario) {
        checks++;
        if(expected!=actual) throw new AssertionError(scenario+": expected "+expected+", was "+actual);
    }

    private static void check(boolean value,String scenario) {
        checks++;
        if(!value) throw new AssertionError(scenario);
    }

    private static void measuredAllocation() {
        Fixture f=new Fixture(); f.icons.mMaxIcons=2;
        f.add(72,ICON); f.add(72,ICON); f.add(72,DOT);
        for(int i=0;i<87;i++) f.add(72,HIDDEN);
        f.icons.measuredWidth=230; f.icons.width=6480;
        equal(358,f.budget(),"positive native measurement with 87 Java VISIBLE hidden children");
        for(int i=0;i<f.icons.getChildCount();i++) {
            Glyph glyph=(Glyph)f.icons.getChildAt(i);
            check(glyph.getVisibility()==View.VISIBLE,"HIDDEN is a native state, not Java GONE");
            equal(0,glyph.getterCalls,"positive allocation does not inspect child states");
        }
        // A pending layout retains an older width; current native measurements own both directions.
        f.icons.measuredWidth=100; f.icons.width=230;
        equal(488,f.budget(),"measured notification shrink precedes old layout");
        f.icons.measuredWidth=320; f.icons.width=100;
        equal(268,f.budget(),"measured notification growth precedes old layout");
        f.allocated.measuredWidth=600; f.allocated.width=1000;
        equal(202,f.budget(),"measured outer shrink precedes old layout");
        f.allocated.measuredWidth=700; f.allocated.width=300;
        equal(302,f.budget(),"measured outer growth precedes old layout");
        f.icons.addView(new View(f.context));
        equal(302,f.budget(),"known positive allocation remains authoritative with an unknown child");
    }

    private static void zeroAllocation() {
        Fixture f=new Fixture(); f.icons.mMaxIcons=2;
        f.icons.width=9999; // Historical layout is not an allocation for this zero-measured pass.
        f.icons.actualPaddingStart=3.25f; f.icons.actualPaddingEnd=4.5f;
        Glyph first=f.add(60,ICON), second=f.add(70,ICON), dot=f.add(12,DOT);
        ViewGroup.MarginLayoutParams margin=new ViewGroup.MarginLayoutParams(60,80);
        margin.leftMargin=2; margin.rightMargin=3; first.setLayoutParams(margin);
        margin=new ViewGroup.MarginLayoutParams(70,80); margin.setMarginStart(4); margin.setMarginEnd(6);
        second.setLayoutParams(margin);
        margin=new ViewGroup.MarginLayoutParams(12,80); margin.leftMargin=1; margin.rightMargin=2;
        dot.setLayoutParams(margin);
        f.add(1000,ICON); f.add(1000,DOT); // Beyond mMaxIcons and duplicate overflow cannot add slots.
        Glyph gone=f.add(1000,ICON); gone.setVisibility(View.GONE);
        for(int i=0;i<87;i++) f.add(72,HIDDEN);
        equal(420,f.budget(),"zero native allocation recovers 2 ICON, one DOT, padding and margins");
        f.icons.mMaxIcons=1;
        equal(500,f.budget(),"mMaxIcons reduction retains exactly one overflow slot");
        f.icons.mMaxIcons=0;
        equal(565,f.budget(),"zero icon capacity can retain its native DOT slot");
        f.icons.mMaxIcons=-1;
        equal(-1,f.budget(),"invalid native capacity is unknown");

        Fixture hidden=new Fixture(); hidden.icons.mMaxIcons=2;
        for(int i=0;i<87;i++) hidden.add(72,HIDDEN);
        equal(588,hidden.budget(),"87 HIDDEN widths cannot consume a zero allocation");
        equal(87,hidden.icons.getChildCount(),"hidden children remain attached");
        for(int i=0;i<hidden.icons.getChildCount();i++) {
            Glyph glyph=(Glyph)hidden.icons.getChildAt(i);
            equal(0,glyph.layoutRequests,"budget lookup does not request child layout");
            check(glyph.getScaleX()==1f&&glyph.getScaleY()==1f,"budget lookup preserves child scale");
        }

        Fixture childMeasurement=new Fixture(); childMeasurement.icons.mMaxIcons=1;
        Glyph measured=childMeasurement.add(60,ICON); measured.width=500;
        equal(528,childMeasurement.budget(),"current child measurement overrides historical layout");
        measured.measuredWidth=0; measured.width=40;
        equal(548,childMeasurement.budget(),"zero child measurement conservatively uses its native layout");
    }

    private static void unknownVisibleState() {
        Fixture unknown=new Fixture(); unknown.icons.mMaxIcons=1;
        unknown.add(60,ICON); Glyph unsupported=unknown.add(72,7);
        equal(-1,unknown.budget(),"unknown visibleState returns unknown budget");
        unsupported.visibleState=HIDDEN;
        equal(528,unknown.budget(),"known hidden state recovers the budget");
        View unrecognized=new View(unknown.context); unknown.icons.addView(unrecognized);
        equal(-1,unknown.budget(),"missing native StatusBarIconView contract returns unknown budget");
        unrecognized.setVisibility(View.GONE);
        equal(528,unknown.budget(),"Java GONE child is outside native allocation");
    }

    private static void diagnosticBudgetDoesNotLimitClock() {
        ClockContext context=new ClockContext();StatClock clock=new StatClock(context);
        for(int incoming:new int[]{View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(67,View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1,View.MeasureSpec.UNSPECIFIED)}) {
            for(int budget:new int[]{-1,0,67,358}) {
                equal(1000,NativeClockMeasurement.clockWidth(clock,1000,incoming,budget),
                        "StatClock complete natural width regardless of diagnostic allocation");
            }
        }
        equal(0,NativeClockMeasurement.clockWidth(clock,-1,0,0),"negative desired size cannot corrupt MeasureSpec");
    }

    public static void main(String[] args) {
        measuredAllocation(); zeroAllocation(); unknownVisibleState(); diagnosticBudgetDoesNotLimitClock();
        System.out.println(checks+" native allocation diagnostics and unrestricted clock-width checks passed");
    }
}
