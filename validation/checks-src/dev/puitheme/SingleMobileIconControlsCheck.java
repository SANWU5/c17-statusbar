package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.util.Objects;

/** Replays C17 unstacked setImageResource events without any resource-wrapper/Compose path. */
public final class SingleMobileIconControlsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Single mobile "+checks+": "+expected+" != "+actual);}
    private static class Glyph extends Drawable {
        public void draw(Canvas canvas){ }public void setAlpha(int alpha){ }public void setColorFilter(ColorFilter filter){ }public int getOpacity(){return -3;}
    }
    private static final class Wrapped extends Glyph { }
    private static final class Binding {
        final ImageView icon;
        final ViewGroup host;
        int subscription=-1;
        Binding(){icon=new ImageView(new Context());host=new ViewGroup(new Context());
            host.addView(icon);host.setTranslationX(41);host.setTranslationY(17);host.setAlpha(.7f);
            icon.setImageDrawable(new Glyph());icon.setTranslationX(3);icon.setTranslationY(-2);icon.setScaleX(.8f);icon.setScaleY(.9f);}
        Binding(ImageView shared){icon=shared;host=(ViewGroup)shared.getParent();}
    }
    private static final class Native implements SingleMobileIconControls.Access {
        public ImageView icon(Object owner){return ((Binding)owner).icon;}
        public boolean rendererOwnsPlacement(Drawable drawable){return drawable instanceof Wrapped;}
        public int subscription(Object owner){return ((Binding)owner).subscription;}
        public View host(Object owner){return ((Binding)owner).host;}
    }
    private static FeatureOptions options(boolean enabled){Bundle values=new Bundle();values.putBoolean("data_enabled",enabled);return FeatureOptions.from(values);}
    public static void main(String[] ignored){
        Native nativeCode=new Native();SingleMobileIconControls helper=new SingleMobileIconControls(nativeCode);Binding row=new Binding();
        helper.bound(row);equal(3f,row.icon.getTranslationX());equal(-2f,row.icon.getTranslationY());equal(.8f,row.icon.getScaleX());
        helper.update(options(true),5,-3,150);
        equal(23f,row.icon.getTranslationX());equal(-14f,row.icon.getTranslationY());equal(1.2f,row.icon.getScaleX());equal(1.3499999f,row.icon.getScaleY());
        // Native slot animation/visibility/touch layout are not owned by the glyph setting.
        equal(41f,row.host.getTranslationX());equal(17f,row.host.getTranslationY());equal(.7f,row.host.getAlpha());equal(View.VISIBLE,row.icon.getVisibility());
        for(int i=0;i<200;i++)try(SingleMobileIconControls.NativeScope event=helper.beforeNative(row)){
            equal(3f,row.icon.getTranslationX());equal(-2f,row.icon.getTranslationY());row.icon.setImageDrawable(new Glyph());
        }
        equal(23f,row.icon.getTranslationX());equal(1.2f,row.icon.getScaleX());
        // A density/native layout change is captured without feeding our previous offset back.
        try(SingleMobileIconControls.NativeScope outer=helper.beforeNative(row)){
            try(SingleMobileIconControls.NativeScope nested=helper.beforeNative(row)){
                row.icon.setTranslationX(7);row.icon.setTranslationY(9);row.icon.setScaleX(1);row.icon.setScaleY(1);
            }
            equal(7f,row.icon.getTranslationX());
        }
        equal(27f,row.icon.getTranslationX());equal(-3f,row.icon.getTranslationY());equal(1.5f,row.icon.getScaleX());
        // Same icon becoming wrapped must not receive the setting twice.
        try(SingleMobileIconControls.NativeScope event=helper.beforeNative(row)){row.icon.setImageDrawable(new Wrapped());}
        equal(7f,row.icon.getTranslationX());equal(9f,row.icon.getTranslationY());equal(1f,row.icon.getScaleX());
        helper.update(options(true),-2,4,200);equal(7f,row.icon.getTranslationX());equal(1f,row.icon.getScaleX());
        try(SingleMobileIconControls.NativeScope event=helper.beforeNative(row)){row.icon.setImageDrawable(new Glyph());}
        equal(-1f,row.icon.getTranslationX());equal(25f,row.icon.getTranslationY());equal(2f,row.icon.getScaleX());
        // Position and size sub-switches are independent.
        Bundle noPosition=new Bundle();noPosition.putBoolean("data_enabled",true);noPosition.putBoolean("data_position_enabled",false);
        helper.update(FeatureOptions.from(noPosition),100,100,200);equal(7f,row.icon.getTranslationX());equal(9f,row.icon.getTranslationY());equal(2f,row.icon.getScaleX());
        noPosition.putBoolean("data_size_enabled",false);helper.update(FeatureOptions.from(noPosition),100,100,200);equal(1f,row.icon.getScaleX());
        helper.update(options(false),0,0,100);equal(7f,row.icon.getTranslationX());equal(9f,row.icon.getTranslationY());equal(1f,row.icon.getScaleX());
        helper.update(options(true),1,1,120);Bundle safe=new Bundle();safe.putBoolean("data_enabled",true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);
        helper.update(FeatureOptions.from(safe),1,1,120);equal(7f,row.icon.getTranslationX());equal(1f,row.icon.getScaleX());
        helper.update(options(true),1,1,120);helper.releaseRuntime();equal(7f,row.icon.getTranslationX());equal(9f,row.icon.getTranslationY());equal(1f,row.icon.getScaleX());
        helper.bound(row);equal(true,helper.diagnosticSummary().startsWith("Single mobile icon rows 0"));
        // Exact feedback configuration at PLK110 density; saving a negative x must
        // move the native single glyph and never the animated parent status slot.
        Binding actualRow=new Binding();float oldDensity=actualRow.icon.getResources().getDisplayMetrics().density;
        try{
            actualRow.icon.getResources().getDisplayMetrics().density=3.40625f;
            SingleMobileIconControls actualDensity=new SingleMobileIconControls(nativeCode);actualDensity.bound(actualRow);
            actualDensity.update(options(true),-5.39f,0,100);
            equal(3f+NumericPolicy.pixels(-5.39f,3.40625f),actualRow.icon.getTranslationX());equal(-2f,actualRow.icon.getTranslationY());
            equal(41f,actualRow.host.getTranslationX());equal(1f,actualRow.icon.getAlpha());
            for(float requested:new float[]{-80,80,-300,300,-5.39f}){
                actualDensity.update(options(true),requested,0,100);
                equal(3f+NumericPolicy.pixels(requested,3.40625f),actualRow.icon.getTranslationX());
                try(SingleMobileIconControls.NativeScope event=actualDensity.beforeNative(actualRow)){equal(3f,actualRow.icon.getTranslationX());}
                equal(3f+NumericPolicy.pixels(requested,3.40625f),actualRow.icon.getTranslationX());
            }
            actualDensity.releaseRuntime();equal(3f,actualRow.icon.getTranslationX());equal(-2f,actualRow.icon.getTranslationY());
        }finally{actualRow.icon.getResources().getDisplayMetrics().density=oldDensity;}
        SingleMobileIconControls removed=new SingleMobileIconControls(nativeCode);Binding removedRow=new Binding();removed.bound(removedRow);
        removed.update(options(true),10,10,200);
        SingleMobileIconControls.NativeScope pending=removed.beforeNative(removedRow);
        removed.releaseRuntime();pending.close();pending.close();
        equal(3f,removedRow.icon.getTranslationX());equal(-2f,removedRow.icon.getTranslationY());equal(.8f,removedRow.icon.getScaleX());
        equal(true,removed.diagnosticSummary().startsWith("Single mobile icon rows 0"));
        // BigTypeLegacy's actual updateSignalIcon(ImageView, SignalIconModel)
        // owns one glyph directly, without a base Binding. Its neighboring
        // arrows/RAT icons and animated group must keep their native values.
        Binding legacy=new Binding();ImageView arrow=new ImageView(new Context());legacy.host.addView(arrow);
        arrow.setTranslationX(2);arrow.setScaleX(.5f);
        SingleMobileIconControls direct=new SingleMobileIconControls(nativeCode);
        direct.bound(legacy.icon);direct.update(options(true),-2,4,150);
        equal(-5f,legacy.icon.getTranslationX());equal(14f,legacy.icon.getTranslationY());equal(1.2f,legacy.icon.getScaleX());
        for(int i=0;i<10;i++)try(SingleMobileIconControls.NativeScope event=direct.beforeNative(legacy.icon)) {
            equal(3f,legacy.icon.getTranslationX());legacy.icon.setImageDrawable(new Glyph());
        }
        equal(-5f,legacy.icon.getTranslationX());equal(41f,legacy.host.getTranslationX());equal(17f,legacy.host.getTranslationY());
        equal(2f,arrow.getTranslationX());equal(.5f,arrow.getScaleX());
        try(SingleMobileIconControls.NativeScope event=direct.beforeNative(legacy.icon)){legacy.icon.setImageDrawable(new Wrapped());}
        equal(3f,legacy.icon.getTranslationX());equal(-2f,legacy.icon.getTranslationY());equal(.8f,legacy.icon.getScaleX());
        direct.releaseRuntime();equal(3f,legacy.icon.getTranslationX());
        bindingAliases(nativeCode);
        primarySubscriptions(nativeCode);
        secondaryBadgeOwnership(nativeCode);
        System.out.println("SingleMobileIconControls checks passed: "+checks);
    }
    private static void secondaryBadgeOwnership(Native nativeCode){
        ViewGroup phone=new ViewGroup(new Context());Binding main=new Binding(),other=new Binding();
        main.subscription=11;other.subscription=22;phone.addView(main.host);phone.addView(other.host);
        SingleMobileIconControls helper=new SingleMobileIconControls(nativeCode);
        helper.update(options(true),0,0,100,true,11);helper.bound(other);
        equal(false,helper.hideSecondaryBadge(22,other.host));
        helper.bound(main);equal(true,helper.hideSecondaryBadge(22,other.host));
        equal(false,helper.hideSecondaryBadge(11,main.host));
        equal(false,helper.hideSecondaryBadge(-1,other.host));
        equal(false,helper.hideSecondaryBadge(22,null));
        ViewGroup unrelated=new ViewGroup(new Context());phone.addView(unrelated);
        equal(false,helper.hideSecondaryBadge(22,unrelated));
        equal(false,helper.hideSecondaryBadge(11,other.host));
        // Native binding restores visibility during its transaction; role ownership still holds.
        try(SingleMobileIconControls.NativeScope ignored=helper.beforeNative(other)){
            equal(View.VISIBLE,other.icon.getVisibility());equal(true,helper.hideSecondaryBadge(22,other.host));
        }
        helper.activeSubscription(22);equal(false,helper.hideSecondaryBadge(22,other.host));
        equal(true,helper.hideSecondaryBadge(11,main.host));
        helper.update(options(true),0,0,100,false,22);equal(false,helper.hideSecondaryBadge(11,main.host));
        helper.update(options(false),0,0,100,true,22);equal(false,helper.hideSecondaryBadge(11,main.host));
        Bundle safe=new Bundle();safe.putBoolean("data_enabled",true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);
        helper.update(FeatureOptions.from(safe),0,0,100,true,22);equal(false,helper.hideSecondaryBadge(11,main.host));
        Bundle nativeIcon=new Bundle();nativeIcon.putBoolean("data_enabled",true);nativeIcon.putBoolean("data_icon_enabled",false);
        helper.update(FeatureOptions.from(nativeIcon),0,0,100,true,22);equal(true,helper.hideSecondaryBadge(11,main.host));
        other.host.setVisibility(View.GONE);helper.activeSubscription(22);equal(false,helper.hideSecondaryBadge(11,main.host));
        other.host.setVisibility(View.VISIBLE);helper.activeSubscription(22);equal(true,helper.hideSecondaryBadge(11,main.host));
        other.icon.attachListeners.get(0).onViewDetachedFromWindow(other.icon);
        equal(false,helper.hideSecondaryBadge(11,main.host));
        other.icon.attachListeners.get(0).onViewAttachedToWindow(other.icon);
        equal(true,helper.hideSecondaryBadge(11,main.host));
        helper.activeSubscription(-1);equal(false,helper.hideSecondaryBadge(11,main.host));
        helper.activeSubscription(999);equal(false,helper.hideSecondaryBadge(11,main.host));
        helper.activeSubscription(11);helper.releaseRuntime();equal(false,helper.hideSecondaryBadge(22,other.host));
    }
    private static void primarySubscriptions(Native nativeCode){
        ViewGroup phone=new ViewGroup(new Context());Binding first=new Binding(),second=new Binding();
        first.subscription=1;second.subscription=2;phone.addView(first.host);phone.addView(second.host);
        ImageView badge=new ImageView(new Context());second.host.addView(badge);badge.setTranslationX(2);
        SingleMobileIconControls helper=new SingleMobileIconControls(nativeCode);
        helper.update(options(true),5,-3,150,true,2);helper.bound(first);
        // Sole/missing primary evidence never hides the only known signal.
        equal(View.VISIBLE,first.icon.getVisibility());helper.bound(second);
        equal(View.GONE,first.icon.getVisibility());equal(View.VISIBLE,second.icon.getVisibility());
        equal(View.VISIBLE,first.host.getVisibility());equal(View.VISIBLE,second.host.getVisibility());
        equal(View.VISIBLE,badge.getVisibility());equal(2f,badge.getTranslationX());
        second.host.setVisibility(View.GONE);helper.activeSubscription(2);equal(View.VISIBLE,first.icon.getVisibility());
        second.host.setVisibility(View.VISIBLE);helper.activeSubscription(2);equal(View.GONE,first.icon.getVisibility());
        // Main data switch restores the first native glyph and hides only the second.
        helper.activeSubscription(1);equal(View.VISIBLE,first.icon.getVisibility());equal(View.GONE,second.icon.getVisibility());
        for(int i=0;i<10;i++)try(SingleMobileIconControls.NativeScope event=helper.beforeNative(second)){
            equal(View.VISIBLE,second.icon.getVisibility());second.icon.setImageDrawable(new Glyph());
        }
        equal(View.GONE,second.icon.getVisibility());
        // A ScaledDrawable already owns placement, but it must still obey the primary selection.
        try(SingleMobileIconControls.NativeScope event=helper.beforeNative(second)){second.icon.setImageDrawable(new Wrapped());}
        equal(View.GONE,second.icon.getVisibility());equal(3f,second.icon.getTranslationX());equal(.8f,second.icon.getScaleX());
        helper.activeSubscription(2);equal(View.GONE,first.icon.getVisibility());equal(View.VISIBLE,second.icon.getVisibility());
        equal(View.VISIBLE,badge.getVisibility());equal(41f,second.host.getTranslationX());
        helper.activeSubscription(-1);equal(View.VISIBLE,first.icon.getVisibility());equal(View.VISIBLE,second.icon.getVisibility());
        helper.activeSubscription(99);equal(View.VISIBLE,first.icon.getVisibility());equal(View.VISIBLE,second.icon.getVisibility());
        helper.activeSubscription(1);
        // A native visibility request is captured within the transaction and restored on disable.
        try(SingleMobileIconControls.NativeScope event=helper.beforeNative(second)){second.icon.setVisibility(View.INVISIBLE);}
        equal(View.GONE,second.icon.getVisibility());helper.update(options(false),0,0,100,true,1);
        equal(View.INVISIBLE,second.icon.getVisibility());equal(3f,second.icon.getTranslationX());equal(.8f,second.icon.getScaleX());
        helper.update(options(true),0,0,100,true,1);equal(View.GONE,second.icon.getVisibility());
        Bundle iconOff=new Bundle();iconOff.putBoolean("data_enabled",true);iconOff.putBoolean("data_icon_enabled",false);
        helper.update(FeatureOptions.from(iconOff),0,0,100,true,1);equal(View.GONE,second.icon.getVisibility());
        helper.activeSubscription(2);equal(View.INVISIBLE,second.icon.getVisibility());equal(View.VISIBLE,first.icon.getVisibility());
        iconOff.putBoolean(StatusBarSettings.SAFE_MODE,true);
        helper.update(FeatureOptions.from(iconOff),0,0,100,true,2);equal(View.INVISIBLE,second.icon.getVisibility());equal(View.VISIBLE,first.icon.getVisibility());
        helper.update(options(true),0,0,100,true,1);helper.releaseRuntime();equal(View.INVISIBLE,second.icon.getVisibility());
        equal(0,first.icon.attachListeners.size());equal(0,second.icon.attachListeners.size());
        // Another status surface with a matching ID cannot suppress this surface's sole signal.
        SingleMobileIconControls isolated=new SingleMobileIconControls(nativeCode);
        Binding local=new Binding(),elsewhere=new Binding();local.subscription=1;elsewhere.subscription=2;
        ViewGroup keyguard=new ViewGroup(new Context());phone.addView(local.host);keyguard.addView(elsewhere.host);
        isolated.update(options(true),0,0,100,true,2);isolated.bound(local);isolated.bound(elsewhere);
        equal(View.VISIBLE,local.icon.getVisibility());equal(View.VISIBLE,elsewhere.icon.getVisibility());isolated.releaseRuntime();
        // Recreated bindings reuse one glyph baseline and one attachment listener.
        SingleMobileIconControls aliases=new SingleMobileIconControls(nativeCode);
        first.icon.setVisibility(View.VISIBLE);second.icon.setVisibility(View.VISIBLE);second.icon.setImageDrawable(new Glyph());
        aliases.update(options(true),0,0,100,true,1);aliases.bound(first);aliases.bound(second);
        Binding alias=new Binding(second.icon);alias.subscription=2;aliases.bound(alias);equal(1,second.icon.attachListeners.size());
        equal(View.GONE,second.icon.getVisibility());
        first.icon.attachListeners.get(0).onViewDetachedFromWindow(first.icon);
        equal(View.VISIBLE,second.icon.getVisibility());
        first.icon.attachListeners.get(0).onViewAttachedToWindow(first.icon);equal(View.GONE,second.icon.getVisibility());
        aliases.update(options(true),0,0,100,false,1);equal(View.VISIBLE,second.icon.getVisibility());aliases.releaseRuntime();
        // BigTypeLegacy receives the same subscription model and native mobile_signal view,
        // despite lacking an Abstract...Binding object.
        SingleMobileIconControls legacy=new SingleMobileIconControls(nativeCode);
        legacy.update(options(true),0,0,100,true,2);legacy.bound(first.icon,1,first.host);legacy.bound(second.icon,2,second.host);
        equal(View.GONE,first.icon.getVisibility());equal(View.VISIBLE,second.icon.getVisibility());
        legacy.activeSubscription(1);equal(View.VISIBLE,first.icon.getVisibility());equal(View.GONE,second.icon.getVisibility());
        try(SingleMobileIconControls.NativeScope event=legacy.beforeNative(second.icon)){equal(View.VISIBLE,second.icon.getVisibility());}
        equal(View.GONE,second.icon.getVisibility());legacy.releaseRuntime();equal(View.VISIBLE,second.icon.getVisibility());
    }
    private static void bindingAliases(Native nativeCode){
        SingleMobileIconControls helper=new SingleMobileIconControls(nativeCode);Binding original=new Binding();
        helper.bound(original);helper.update(options(true),5,-3,150);
        Binding alias=new Binding(original.icon);helper.bound(alias);
        equal(23f,alias.icon.getTranslationX());equal(-14f,alias.icon.getTranslationY());
        equal(1.2f,alias.icon.getScaleX());equal(1.3499999f,alias.icon.getScaleY());
        for(int i=0;i<1000;i++){
            helper.bound(new Binding(original.icon));helper.bound(original);helper.bound(alias);
            equal(23f,alias.icon.getTranslationX());equal(1.2f,alias.icon.getScaleX());
        }
        // Distinct native wrapper objects own the same real glyph; nested events must
        // share depth and the unmodified native baseline as well as the visible result.
        try(SingleMobileIconControls.NativeScope outer=helper.beforeNative(original)){
            equal(3f,alias.icon.getTranslationX());equal(.8f,alias.icon.getScaleX());
            try(SingleMobileIconControls.NativeScope inner=helper.beforeNative(alias)){
                alias.icon.setTranslationX(7);alias.icon.setTranslationY(9);
                alias.icon.setScaleX(1);alias.icon.setScaleY(.6f);
            }
            equal(7f,alias.icon.getTranslationX());equal(1f,alias.icon.getScaleX());
        }
        equal(27f,alias.icon.getTranslationX());equal(-3f,alias.icon.getTranslationY());
        equal(1.5f,alias.icon.getScaleX());equal(.90000004f,alias.icon.getScaleY());
        alias.icon.getResources().getDisplayMetrics().density=2f;
        helper.update(options(true),3,2,200);
        equal(13f,alias.icon.getTranslationX());equal(13f,alias.icon.getTranslationY());equal(2f,alias.icon.getScaleX());
        helper.update(options(false),3,2,200);equal(7f,alias.icon.getTranslationX());equal(1f,alias.icon.getScaleX());
        helper.update(options(true),3,2,200);equal(13f,alias.icon.getTranslationX());equal(2f,alias.icon.getScaleX());
        Bundle safe=new Bundle();safe.putBoolean("data_enabled",true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);
        helper.update(FeatureOptions.from(safe),3,2,200);equal(7f,alias.icon.getTranslationX());equal(1f,alias.icon.getScaleX());
        helper.update(options(true),3,2,200);helper.releaseRuntime();
        equal(7f,alias.icon.getTranslationX());equal(9f,alias.icon.getTranslationY());equal(1f,alias.icon.getScaleX());equal(.6f,alias.icon.getScaleY());
        helper.bound(alias);helper.update(options(true),3,2,200);equal(7f,alias.icon.getTranslationX());equal(1f,alias.icon.getScaleX());
    }
}
