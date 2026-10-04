// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.phone.NotificationIconContainer;
import com.oplus.systemui.statusbar.widget.StatClock;
import java.util.Collections;

/** Reproduces an OEM without actualWidth: onMeasure writes Paint while the old Entry stayed 13px. */
public final class StatClockSizingCheck {
    private static int checks;
    public static final class ClockResources extends Resources {
        public int pixelReads, identifiers;
        public boolean known = true;
        public ClockResources() { getDisplayMetrics().density = 3.5f; getDisplayMetrics().scaledDensity = 3.5f; }
        @Override public int getIdentifier(String name, String type, String owner) {
            identifiers++; return known && "stat_clock_size".equals(name) && "dimen".equals(type) && "com.android.systemui".equals(owner) ? 1 : 0;
        }
        // Intentionally catches getDimension-vs-native-PixelSize mistakes.
        @Override public float getDimension(int id) { return 13f; }
        public int getDimensionPixelSize(int id) { pixelReads++; return Math.round(13f * getDisplayMetrics().density); }
        @Override public String getResourceEntryName(int id) {
            switch(id) {
                case 2: case 0x7f0a0dfb: return "status_bar_start_side_container";
                case 0x7f0a0dfc: return "status_bar_start_side_content";
                case 0x7f0a0dfe: return "status_bar_start_side_except_heads_up";
                case 0x7f0a0307: return "clock_for_fake";
                case 0x7f0a0dfd: return "status_bar_start_side_content_for_fake";
                case 0x7f0a0961: return "notification_icon_area";
                default: return "clock";
            }
        }
        @Override public String getResourcePackageName(int id) { return "com.android.systemui"; }
    }
    private static final class ClockContext extends Context {
        final ClockResources resources = new ClockResources();
        @Override public Resources getResources() { return resources; }
    }
    private static final class Layout extends LinearLayout {
        final int width, id; final Resources resources;
        Layout(Context context, int width, int id) { super(context); this.width=width; this.id=id; resources=context.getResources(); }
        @Override public int getWidth() { return width; }
        @Override public int getMeasuredWidth() { return width; }
        @Override public int getId() { return id; }
        @Override public Resources getResources() { return resources; }
    }
    private static final class Glyph extends StatusBarIconView {
        final int width;
        Glyph(Context context, int width) { super(context); this.width=width; }
        @Override public int getWidth() { return width; }
        @Override public int getMeasuredWidth() { return width; }
    }
    private static final class FrameNode extends FrameLayout {
        int width, measuredWidth, paddingLeft, paddingRight, direction;
        final int id;
        FrameNode(Context context, int width, int id) { super(context); this.width=measuredWidth=width; this.id=id; }
        @Override public int getWidth() { return width; }
        @Override public int getMeasuredWidth() { return measuredWidth; }
        @Override public int getId() { return id; }
        @Override public Resources getResources() { return getContext().getResources(); }
        @Override public int getPaddingLeft() { return paddingLeft; }
        @Override public int getPaddingRight() { return paddingRight; }
        @Override public int getLayoutDirection() { return direction; }
    }
    /** StartSideExceptHeadsUpLayout's audited superclass is LinearLayout; measure is inherited. */
    private static final class LinearNode extends LinearLayout {
        int width, paddingLeft, paddingRight, direction;
        final int id;
        LinearNode(Context context, int width, int id) { super(context); this.width=width; this.id=id; }
        @Override public int getWidth() { return width; }
        @Override public int getMeasuredWidth() { return width; }
        @Override public int getId() { return id; }
        @Override public Resources getResources() { return getContext().getResources(); }
        @Override public int getPaddingLeft() { return paddingLeft; }
        @Override public int getPaddingRight() { return paddingRight; }
        @Override public int getLayoutDirection() { return direction; }
    }
    public static final class MeasuredClock extends StatClock {
        int laidOutWidth=124;
        public MeasuredClock(Context context) { super(context); }
        void actualMeasure(int width) { laidOutWidth=width; setMeasuredDimension(width,48); }
        @Override public int getWidth() { return laidOutWidth; }
    }
    public static final class AutoClock extends StatClock {
        public int presetWrites;
        private int[] presets = {20, 30, 46};
        public AutoClock(Context context) { super(context); }
        public int[] getAutoSizeTextAvailableSizes() { return presets.clone(); }
        public void setAutoSizeTextTypeUniformWithPresetSizes(int[] values, int unit) {
            check(unit == TypedValue.COMPLEX_UNIT_PX); presets=values.clone(); presetWrites++;
            setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        }
    }
    public static final class CachedClock extends StatClock {
        public int actualWidth;
        public CachedClock(Context context) { super(context); }
    }
    public static final class LayoutClock extends StatClock {
        public int actualWidth;
        public LayoutClock(Context context) { super(context); platformMeasureSpecs=true; }
        @Override public int getPaddingLeft() { return 10; }
        @Override public int getPaddingRight() { return 24; }
        void platformMeasure(int width) {
            super.onMeasure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
            actualWidth = getMeasuredWidth();
        }
    }
    /** Device dump has a 0px clock_for_fake; OEM StatClock still ignores its child spec. */
    public static final class PhoneMeasuredClock extends StatClock {
        public int actualWidth;
        int laidOutWidth;
        public PhoneMeasuredClock(Context context) { super(context); }
        @Override public int getWidth() { return laidOutWidth; }
        @Override public int getPaddingLeft() { return 10; }
        @Override public int getPaddingRight() { return 24; }
        void actualMeasure(int width) { actualWidth=laidOutWidth=width; setMeasuredDimension(width,120); }
        void nativeMeasure() {
            getPaint().setTextSize(((ClockResources)getContext().getResources()).getDimensionPixelSize(1));
            int width=(int)Math.ceil(getPaint().measureText(getText(),0,getText().length()))+getPaddingLeft()+getPaddingRight();
            actualMeasure(width);
        }
    }
    private static Bundle settings(float percent, int version) {
        Bundle b = new Bundle(); b.putBoolean("clock_controls_enabled", true); b.putBoolean("clock_size_enabled", true);
        b.putBoolean("clock_text_style_enabled", true); b.putBoolean("clock_enabled", false);
        b.putFloat("clock_scale", percent); b.putFloat("clock_weight", 800f);
        b.putFloat(NativeClockMeasurement.SCALE_BASIS_VERSION, version); return b;
    }
    private static TextControls controls() { return new TextControls(new Handler(Looper.getMainLooper())); }
    private static void configure(TextControls c, Bundle b) { c.configure(b, StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap()); }
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    private static void near(float expected, float value) { checks++; if (Math.abs(expected-value)>.005f) throw new AssertionError("Expected " + expected + ", was " + value); }
    private static int naturalWidth(TextView view) {
        return (int)Math.ceil(view.getPaint().measureText(view.getText(),0,view.getText().length())
                + view.getCompoundPaddingLeft() + view.getCompoundPaddingRight());
    }

    private static void nestedAllocation() {
        ClockContext context=new ClockContext(); TextControls controls=controls(); MeasuredClock clock=new MeasuredClock(context); clock.setText("10:50");
        FrameNode allocated=new FrameNode(context,500,0x7f0a0dfb), wrapContent=new FrameNode(context,12,0x7f0a0dfc), clockWrapper=new FrameNode(context,12,0x7f0a0307);
        LinearNode exceptHeadsUp=new LinearNode(context,12,0x7f0a0dfe), remaining=new LinearNode(context,0,0x7f0a0dfd);
        FrameNode notificationArea=new FrameNode(context,0,0x7f0a0961);
        NotificationIconContainer nativeIcons=new NotificationIconContainer(context) {
            @Override public int getWidth(){return 0;}
            @Override public int getMeasuredWidth(){return 0;}
        };
        Glyph first=new Glyph(context,72), second=new Glyph(context,72);
        nativeIcons.addView(first); nativeIcons.addView(second);
        notificationArea.addView(nativeIcons); notificationArea.addView(new Glyph(context,96));
        // Two overlapping FrameLayout children need max(144,96), never 144+96.
        remaining.addView(notificationArea); exceptHeadsUp.addView(clockWrapper); exceptHeadsUp.addView(remaining);
        clockWrapper.addView(clock); wrapContent.addView(exceptHeadsUp);
        // An outer overlay does not take away the clock's horizontal allocation.
        wrapContent.addView(new Glyph(context,90)); allocated.addView(wrapContent);
        allocated.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f));
        wrapContent.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.MATCH_PARENT));
        exceptHeadsUp.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        clockWrapper.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.MATCH_PARENT));
        remaining.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.MATCH_PARENT));
        clock.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.MATCH_PARENT));
        check(NativeClockMeasurement.availableWidth(clock)==356);
        int original=View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.AT_MOST), smaller=0, larger=0;
        for(int cycle=0;cycle<100;cycle++) for(int percent:new int[]{100,200,400,100}) {
            configure(controls,settings(percent,2)); if(cycle==0&&percent==100)controls.attach(clock);
            near(46f*percent/100f,clock.getPaint().getTextSize());
            int desired=(int)Math.ceil(clock.getPaint().measureText(clock.getText(),0,clock.getText().length()));
            int actual=View.MeasureSpec.getSize(controls.prepareClockMeasure(clock,original));
            check(actual==desired); clock.actualMeasure(actual); controls.afterClockMeasure(clock);
            Canvas canvas=new Canvas(); controls.beforeDraw(clock,canvas); check(canvas.clips==0);
            if(percent==100)smaller=actual; if(percent==200){larger=actual;check(larger>smaller);check(actual==desired);}
            // Inner content remains a historical tiny width while the parent slot is 500px.
            wrapContent.width=wrapContent.measuredWidth=cycle%2==0?1:18; exceptHeadsUp.width=clockWrapper.width=12;
            check(first.getWidth()==72&&second.getWidth()==72); check(first.layoutRequests==0&&second.layoutRequests==0);
        }
        check(larger==smaller*2); // Real glyph width scales, without fitting back into the old slot.
        allocated.width=1000; allocated.measuredWidth=600; // measured shrink precedes the next layout.
        check(NativeClockMeasurement.availableWidth(clock)==456);
        configure(controls,settings(400,2)); int resized=controls.prepareClockMeasure(clock,View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.AT_MOST));
        check(View.MeasureSpec.getSize(resized)==naturalWidth(clock));
        allocated.width=300; allocated.measuredWidth=700; check(NativeClockMeasurement.availableWidth(clock)==556);
        allocated.width=allocated.measuredWidth=500;
        ((ViewGroup.MarginLayoutParams)clock.getLayoutParams()).setMarginStart(3); ((ViewGroup.MarginLayoutParams)clock.getLayoutParams()).setMarginEnd(7);
        clockWrapper.paddingLeft=2;clockWrapper.paddingRight=4;
        ((ViewGroup.MarginLayoutParams)clockWrapper.getLayoutParams()).setMarginStart(8); ((ViewGroup.MarginLayoutParams)clockWrapper.getLayoutParams()).setMarginEnd(4);
        exceptHeadsUp.paddingLeft=5;exceptHeadsUp.paddingRight=9;
        ((ViewGroup.MarginLayoutParams)remaining.getLayoutParams()).setMarginStart(6); ((ViewGroup.MarginLayoutParams)remaining.getLayoutParams()).setMarginEnd(2);
        wrapContent.paddingLeft=1;wrapContent.paddingRight=2;
        ((ViewGroup.MarginLayoutParams)exceptHeadsUp.getLayoutParams()).setMarginStart(4); ((ViewGroup.MarginLayoutParams)exceptHeadsUp.getLayoutParams()).setMarginEnd(6);
        allocated.paddingLeft=3;allocated.paddingRight=7;
        ((ViewGroup.MarginLayoutParams)wrapContent.getLayoutParams()).setMarginStart(1); ((ViewGroup.MarginLayoutParams)wrapContent.getLayoutParams()).setMarginEnd(2);
        check(NativeClockMeasurement.availableWidth(clock)==280);
        allocated.direction=wrapContent.direction=clockWrapper.direction=exceptHeadsUp.direction=remaining.direction=View.LAYOUT_DIRECTION_RTL;
        check(NativeClockMeasurement.availableWidth(clock)==280); // Relative margins reserve the same real distance in RTL.
        allocated.width=allocated.measuredWidth=0;check(NativeClockMeasurement.availableWidth(clock)==-1);
        allocated.width=allocated.measuredWidth=500;
        exceptHeadsUp.setOrientation(LinearLayout.VERTICAL);check(NativeClockMeasurement.availableWidth(clock)==432);
        exceptHeadsUp.setOrientation(LinearLayout.HORIZONTAL);
        // A similarly named WRAP_CONTENT layout alone is not an allocated start-side slot.
        allocated.removeView(wrapContent);check(NativeClockMeasurement.availableWidth(clock)==-1);
        allocated.addView(wrapContent);
        ViewGroup unknown=new ViewGroup(context); clockWrapper.removeView(clock);clockWrapper.addView(unknown);unknown.addView(clock);
        check(NativeClockMeasurement.availableWidth(clock)==-1);
        int unknownSpec=View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.AT_MOST);
        int expected=naturalWidth(clock);
        check(View.MeasureSpec.getSize(controls.prepareClockMeasure(clock,unknownSpec))==expected);
    }

    private static void nativeZeroChildSpec() {
        ClockContext context=new ClockContext();
        context.resources.getDisplayMetrics().density=4f; context.resources.getDisplayMetrics().scaledDensity=4f;
        TextControls controls=controls(); PhoneMeasuredClock clock=new PhoneMeasuredClock(context); clock.setText("12:37");
        // Exact allocated widths/padding from PJZ110's native hierarchy; no fake minimum width.
        FrameNode allocated=new FrameNode(context,666,0x7f0a0dfb), content=new FrameNode(context,230,0x7f0a0dfc), wrapper=new FrameNode(context,0,0x7f0a0307);
        allocated.paddingLeft=78;
        LinearNode exceptHeadsUp=new LinearNode(context,230,0x7f0a0dfe), remaining=new LinearNode(context,230,0x7f0a0dfd);
        allocated.addView(content);content.addView(exceptHeadsUp);exceptHeadsUp.addView(wrapper);exceptHeadsUp.addView(remaining);wrapper.addView(clock);
        configure(controls,settings(110.52f,2));controls.attach(clock);
        check(NativeClockMeasurement.availableWidth(clock)==358);
        int[] incoming={View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1,View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.EXACTLY)};
        for(int cycle=0;cycle<100;cycle++) for(int original:incoming) {
            // Audited native DEX ignores incoming, computes actualWidth, then calls TextView EXACTLY.
            TextControls.NativeStyleScope scope=controls.beginClockMeasure(clock);
            clock.nativeMeasure();int nativeWidth=clock.getMeasuredWidth(), nativeActual=clock.actualWidth;
            check(nativeWidth>0&&nativeWidth==nativeActual);scope.close();
            controls.onNativeClockMeasure(clock,original,nativeWidth,nativeActual);
            near(52f*1.1052f,clock.getPaint().getTextSize());
            int desired=(int)Math.ceil(clock.getPaint().measureText(clock.getText(),0,clock.getText().length()))+34;
            int outgoing=controls.prepareClockMeasure(clock,original), width=View.MeasureSpec.getSize(outgoing);
            check(View.MeasureSpec.getMode(outgoing)==View.MeasureSpec.EXACTLY);
            check(width==desired&&width>0&&width<=358);clock.actualMeasure(width);controls.afterClockMeasure(clock);
            String summary=controls.summary();
            check(summary.contains("incomingMode="+View.MeasureSpec.getMode(original)));
            check(summary.contains("incomingWidth="+View.MeasureSpec.getSize(original)));
            check(summary.contains("nativeMeasuredWidth="+nativeWidth)&&summary.contains("nativeActualWidth="+nativeActual));
            check(summary.contains("outgoingWidth="+width)&&summary.contains("availableWidth=358"));
            check(!summary.contains("12:37")); // Measurement diagnostics must contain no clock text.
            Canvas canvas=new Canvas();controls.beforeDraw(clock,canvas);check(canvas.clips==0);
        }
        // Even an exhausted or unrecognized parent is diagnostic only, not a font/format restriction.
        remaining.width=588;check(NativeClockMeasurement.availableWidth(clock)==0);
        int hardZero=incoming[0];check(View.MeasureSpec.getSize(controls.prepareClockMeasure(clock,hardZero))==naturalWidth(clock));
        Canvas exhausted=new Canvas();controls.beforeDraw(clock,exhausted);check(exhausted.clips==0);
        remaining.width=230;
        // Unknown host, vertical chain, or no allocated outer width preserves original hard zero.
        ViewGroup unknown=new ViewGroup(context);wrapper.removeView(clock);wrapper.addView(unknown);unknown.addView(clock);
        check(NativeClockMeasurement.availableWidth(clock)==-1);
        check(View.MeasureSpec.getSize(controls.prepareClockMeasure(clock,hardZero))==naturalWidth(clock));
        unknown.removeView(clock);wrapper.removeView(unknown);wrapper.addView(clock);
        exceptHeadsUp.setOrientation(LinearLayout.VERTICAL);
        check(View.MeasureSpec.getSize(controls.prepareClockMeasure(clock,hardZero))==naturalWidth(clock));
        exceptHeadsUp.setOrientation(LinearLayout.HORIZONTAL);
        allocated.width=allocated.measuredWidth=0;check(NativeClockMeasurement.availableWidth(clock)==-1);
        check(View.MeasureSpec.getSize(controls.prepareClockMeasure(clock,hardZero))==naturalWidth(clock));
        allocated.width=allocated.measuredWidth=666;
        // Field absence is diagnostic metadata only, never a reason to skip width recovery.
        controls.onNativeClockMeasure(clock,hardZero,190,-1);
        int recovered=controls.prepareClockMeasure(clock,hardZero);check(View.MeasureSpec.getSize(recovered)>0);
        clock.actualMeasure(View.MeasureSpec.getSize(recovered));controls.afterClockMeasure(clock);
        check(controls.summary().contains("nativeActualWidth=-1"));
        Bundle off=settings(110.52f,2);off.putBoolean("clock_controls_enabled",false);configure(controls,off);
        check(controls.prepareClockMeasure(clock,hardZero)==hardZero);near(52f,clock.getPaint().getTextSize());
    }

    public static void main(String[] args) {
        ClockContext context = new ClockContext(); TextControls controls = controls(); StatClock clock = new StatClock(context);
        clock.setText("21:11"); clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 13f);
        configure(controls, settings(100f, 2)); controls.attach(clock);
        near(46f, clock.getTextSize()); near(46f, clock.getPaint().getTextSize());
        check("21:11".contentEquals(clock.getText())); check(!controls.replaces(clock));
        configure(controls, settings(120f, 2)); near(55.2f, clock.getTextSize());
        for (int i=0;i<2000;i++) {
            TextControls.NativeStyleScope scope = controls.beginClockMeasure(clock);
            near(46f, clock.getPaint().getTextSize());
            // Actual OEM onMeasure writes Paint directly, not TextView.setTextSize().
            clock.getPaint().setTextSize(context.resources.getDimensionPixelSize(1));
            scope.close(); scope.close(); controls.beforeMeasure(clock); controls.beforeDraw(clock, new Canvas());
            near(55.2f, clock.getPaint().getTextSize()); near(55.2f, controls.styledSize(clock)); check(!controls.isInternal());
        }
        int resourceReads = context.resources.pixelReads;
        int layouts = clock.layoutRequests;
        for (int i=0;i<1000;i++) controls.beforeDraw(clock, new Canvas());
        check(resourceReads == context.resources.pixelReads); check(layouts == clock.layoutRequests);
        // A late 13px setter must not poison an actual 46px StatClock resource baseline.
        controls.nativeSize(clock, 13f); near(55.2f, controls.styledSize(clock));
        context.resources.getDisplayMetrics().density=4f; context.resources.getDisplayMetrics().scaledDensity=4f;
        controls.beforeMeasure(clock); near(62.4f, clock.getTextSize());
        Bundle off=settings(120f, 2); off.putBoolean("clock_controls_enabled", false); configure(controls, off); near(52f, clock.getTextSize());

        // Exact old broken path, without actualWidth: preserve 13px*380%, rather than reapply 380% to 46px.
        ClockContext legacyContext=new ClockContext(); StatClock legacy=new StatClock(legacyContext); legacy.setText("10:50");
        legacy.setTextSize(TypedValue.COMPLEX_UNIT_PX, 13f); TextControls old=controls();
        configure(old, settings(380f, 1)); old.attach(legacy); near(49.4f, legacy.getTextSize());
        float factor=old.clockLegacyScaleFactor(legacy); near(13f/46f, factor);
        // Stored legacy size compensation must not shrink an uncustomized native clock.
        Bundle legacyNative=settings(380f,1); legacyNative.putBoolean("clock_size_enabled",false);
        configure(old,legacyNative); near(46f,legacy.getTextSize()); near(46f,old.styledSize(legacy));
        configure(old,settings(380f,1)); near(49.4f,legacy.getTextSize());
        legacyNative.putBoolean("clock_controls_enabled",false);
        configure(old,legacyNative); near(46f,legacy.getTextSize()); near(46f,old.styledSize(legacy));
        // Persisted factor is source evidence, and survives process recreation with a different initial Paint.
        StatClock afterRestart=new StatClock(legacyContext); afterRestart.setTextSize(TypedValue.COMPLEX_UNIT_PX,46f);
        TextControls restarted=controls(); Bundle persisted=settings(380f,1); persisted.putFloat(NativeClockMeasurement.LEGACY_SCALE_FACTOR,factor);
        configure(restarted,persisted); restarted.attach(afterRestart); near(49.4f, afterRestart.getTextSize());
        persisted.putFloat(NativeClockMeasurement.SCALE_BASIS_VERSION,2f); persisted.putFloat("clock_scale",100f);
        configure(restarted,persisted); near(46f,afterRestart.getTextSize());
        near(1f, NativeClockMeasurement.legacyFactor(1,0f,true,46f,46f,380f));
        near(1f, NativeClockMeasurement.legacyFactor(1,0f,false,13f,46f,380f));
        near(1f, NativeClockMeasurement.legacyFactor(1,0f,true,13f,46f,200f));
        near(1f, NativeClockMeasurement.legacyFactor(2,13f/46f,true,13f,46f,380f));
        CachedClock cached=new CachedClock(legacyContext); cached.setTextSize(TypedValue.COMPLEX_UNIT_PX,13f);
        TextControls cachedControls=controls(); configure(cachedControls,settings(380f,1)); cachedControls.attach(cached);
        near(174.8f,cached.getTextSize()); near(0f,cachedControls.clockLegacyScaleFactor(cached));

        // Large text retains the requested natural width; neighboring icon scale/config stays native.
        Layout start=new Layout(legacyContext,500,2), host=new Layout(legacyContext,120,3), notifications=new Layout(legacyContext,0,4);
        Glyph first=new Glyph(legacyContext,72), second=new Glyph(legacyContext,72);
        notifications.addView(first); notifications.addView(second); start.addView(host); start.addView(notifications); host.addView(afterRestart);
        afterRestart.setText(restarted.nativeText(afterRestart,"10:50")); configure(restarted,settings(380f,2)); near(174.8f,afterRestart.getTextSize());
        int spec=restarted.prepareClockMeasure(afterRestart, View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.AT_MOST));
        check(View.MeasureSpec.getSize(spec)==naturalWidth(afterRestart)); check(View.MeasureSpec.getMode(spec)==View.MeasureSpec.EXACTLY);
        check(first.layoutRequests==0 && second.layoutRequests==0); near(1f,first.getScaleX()); near(1f,second.getScaleX());
        check(NativeClockMeasurement.boundedWidth(1000,View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.AT_MOST),356)==200);
        check(NativeClockMeasurement.boundedWidth(1000,View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED),356)==356);
        check(NativeClockMeasurement.boundedWidth(1000,View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY),0)==0);
        check(restarted.summary().contains("nativePx=46.0") && restarted.summary().contains("availableWidth=356"));
        Canvas cropped=new Canvas(); restarted.beforeDraw(afterRestart,cropped);
        check(cropped.clips==0);
        configure(restarted,settings(100f,2)); restarted.prepareClockMeasure(afterRestart,View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.AT_MOST));
        Canvas normal=new Canvas(); restarted.beforeDraw(afterRestart,normal); check(normal.clips==0);

        // Platform uniform auto-size owns native behavior again after disabling custom size.
        AutoClock auto=new AutoClock(legacyContext); auto.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        TextControls autoControls=controls(); configure(autoControls,settings(100f,2)); autoControls.attach(auto);
        check(auto.getAutoSizeTextType()==TextView.AUTO_SIZE_TEXT_TYPE_NONE);
        // An OEM re-enabling auto-size during a native callback must be isolated again.
        auto.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM); autoControls.beforeMeasure(auto);
        check(auto.getAutoSizeTextType()==TextView.AUTO_SIZE_TEXT_TYPE_NONE);
        Bundle noSize=settings(100f,2); noSize.putBoolean("clock_size_enabled",false); configure(autoControls,noSize);
        check(auto.getAutoSizeTextType()==TextView.AUTO_SIZE_TEXT_TYPE_NONE);
        noSize.putBoolean("clock_controls_enabled",false);configure(autoControls,noSize);
        check(auto.getAutoSizeTextType()==TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM); check(auto.presetWrites==1);
        // Unknown resources retain captured native pixels, and do not invent a dp/sp conversion.
        ClockContext unknown=new ClockContext(); unknown.resources.known=false; StatClock unknownClock=new StatClock(unknown);
        unknownClock.setTextSize(TypedValue.COMPLEX_UNIT_PX,51f); TextControls fallback=controls(); configure(fallback,settings(100f,2)); fallback.attach(unknownClock);
        near(51f,unknownClock.getTextSize());
        // Rendering guards apply to customized size, never to unrelated native callbacks.
        Bundle nativeSize=settings(380f,1); nativeSize.putBoolean("clock_size_enabled",false);
        configure(fallback,nativeSize);
        fallback.nativeSize(unknownClock,5000f); near(5000f,fallback.styledSize(unknownClock));
        unknownClock.setTextSize(TypedValue.COMPLEX_UNIT_PX,5000f); fallback.beforeMeasure(unknownClock); near(5000f,unknownClock.getTextSize());
        fallback.nativeSize(unknownClock,0f); near(0f,fallback.styledSize(unknownClock));
        unknownClock.setTextSize(TypedValue.COMPLEX_UNIT_PX,0f); fallback.beforeMeasure(unknownClock); near(0f,unknownClock.getTextSize());
        nestedAllocation();
        nativeZeroChildSpec();
        completeFormatsAndSpacing();
        stalePlatformLayout();
        System.out.println(checks+" OEM StatClock native-pixel, legacy-basis, complete-format and natural-measure checks passed");
    }

    private static void completeFormatsAndSpacing() {
        Paint.includeLetterSpacingInMeasure = true;
        try {
            ClockContext context=new ClockContext();
            context.resources.getDisplayMetrics().density=4f;context.resources.getDisplayMetrics().scaledDensity=4f;
            LayoutClock clock=new LayoutClock(context);clock.setText("04:02");clock.setEllipsize(TextUtils.TruncateAt.END);
            TextControls controls=controls();Bundle values=settings(122.57f,2);
            values.putBoolean("clock_enabled",true);values.putFloat("clock_weight",900f);
            FrameNode outer=new FrameNode(context,120,0x7f0a0dfb), wrapper=new FrameNode(context,67,0x7f0a0307);
            LinearNode row=new LinearNode(context,297,0x7f0a0dfe), notifications=new LinearNode(context,230,0x7f0a0dfd);
            outer.addView(row);row.addView(wrapper);row.addView(notifications);wrapper.addView(clock);
            for(String format:new String[]{"HH:mm","hh:mm","HH:mm:ss","MM-dd HH:mm","'时间 'HH:mm"}) {
                for(float spacing:new float[]{-2f,0f,2f}) {
                    values.putString("clock_pattern",format);values.putFloat("clock_spacing",spacing);
                    configure(controls,values);controls.attach(clock);
                    near(52f*1.2257f,clock.getPaint().getTextSize());
                    check(clock.getEllipsize()==null);
                    String expected=TimeFormat.format(format,System.currentTimeMillis());
                    check(expected.contentEquals(clock.getText())&&!clock.getText().toString().contains("..."));
                    int desired=naturalWidth(clock);
                    for(int original:new int[]{View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(67,View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(1,View.MeasureSpec.UNSPECIFIED)}) {
                        int prepared=controls.prepareClockMeasure(clock,original);
                        check(View.MeasureSpec.getMode(prepared)==View.MeasureSpec.EXACTLY);
                        check(View.MeasureSpec.getSize(prepared)==desired);
                        clock.platformMeasure(View.MeasureSpec.getSize(prepared));controls.afterClockMeasure(clock);
                        check(clock.getLayout().getEllipsisCount(0)==0);
                        near(52f*1.2257f,clock.getLayout().measuredTextSize);
                    }
                    check(((Number)values.get("clock_scale")).floatValue()==122.57f);
                    check(((Number)values.get("clock_weight")).floatValue()==900f);
                    check(controls.summary().contains("widthPolicy=natural"));
                }
            }
            // A native callback restoring END is captured and isolated again; disable returns it.
            clock.setEllipsize(TextUtils.TruncateAt.END);controls.beforeMeasure(clock);check(clock.getEllipsize()==null);
            values.putBoolean("clock_controls_enabled",false);configure(controls,values);
            check(clock.getEllipsize()==TextUtils.TruncateAt.END);near(52f,clock.getTextSize());
            check("04:02".contentEquals(clock.getText()));
        } finally { Paint.includeLetterSpacingInMeasure = false; }
    }

    private static void stalePlatformLayout() {
        ClockContext context=new ClockContext();LayoutClock clock=new LayoutClock(context);clock.setText("02:37");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX,46f);clock.setEllipsize(TextUtils.TruncateAt.END);
        clock.platformMeasure(67);check(clock.getLayout().getEllipsisCount(0)>0);
        int rebuilt=clock.layoutRebuilds;
        // Real TextView setter is a no-op at the Paint's same size and retains a stale Layout.
        clock.getPaint().setTextSize(46f);clock.setTextSize(TypedValue.COMPLEX_UNIT_PX,46f);
        clock.platformMeasure(67);check(clock.layoutRebuilds==rebuilt);check(clock.getLayout().getEllipsisCount(0)>0);
        check(NativeClockMeasurement.clearTextLayout(clock));check(clock.getLayout()==null);
        clock.platformMeasure(naturalWidth(clock));check(clock.getLayout().getEllipsisCount(0)==0);
        TextControls controls=controls();configure(controls,settings(100f,2));controls.attach(clock);
        // OEM can create its first Layout at 13px, then write the requested 46px Paint
        // directly. Same-size styling must not retain that old layout at a matching width.
        clock.getPaint().setTextSize(13f);clock.platformMeasure(naturalWidth(clock));
        near(13f,clock.getLayout().measuredTextSize);clock.getPaint().setTextSize(46f);
        int outgoing=controls.prepareClockMeasure(clock,View.MeasureSpec.makeMeasureSpec(67,View.MeasureSpec.EXACTLY));
        check(clock.getLayout()==null);clock.platformMeasure(View.MeasureSpec.getSize(outgoing));controls.afterClockMeasure(clock);
        near(46f,clock.getLayout().measuredTextSize);check(clock.getLayout().getEllipsisCount(0)==0);
        int clears=clock.layoutClears, layouts=clock.layoutRebuilds;
        for(int i=0;i<1000;i++)controls.beforeDraw(clock,new Canvas());
        check(clock.layoutClears==clears&&clock.layoutRebuilds==layouts);
    }
}
