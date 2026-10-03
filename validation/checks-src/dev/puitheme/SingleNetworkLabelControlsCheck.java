package dev.puitheme;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Replays the real single-SIM binder transaction and main-data subscription changes. */
public final class SingleNetworkLabelControlsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Single label "+checks+": "+expected+" != "+actual);}
    private static final class Binder {
        final TextView text=new TextView(new Context());final View image=new View(new Context());final int sub;
        int binds;Object model;
        Binder(int sub){this.sub=sub;text.setText("native-"+sub);text.setTextSize(TypedValue.COMPLEX_UNIT_PX,20);text.setMinimumWidth(24);text.setTranslationX(3);text.setTranslationY(5);}
    }
    private static final class Native implements SingleNetworkLabelControls.Access {
        public TextView text(Object b){return ((Binder)b).text;}
        public View image(Object b){return ((Binder)b).image;}
        public int subscription(Object b){return ((Binder)b).sub;}
        public void bind(Object b,Object model){Binder row=(Binder)b;row.binds++;row.model=model;row.text.setText(model==null?null:model.toString());row.text.setVisibility(model==null?View.GONE:View.VISIBLE);row.image.setVisibility(View.VISIBLE);}
    }
    private static FeatureOptions options(boolean enabled){Bundle b=new Bundle();b.putBoolean("label_enabled",enabled);b.putBoolean("label_text_style_enabled",false);return FeatureOptions.from(b);}
    private static void event(SingleNetworkLabelControls helper,Native nativeCode,Binder b,Object model){
        try(SingleNetworkLabelControls.NativeScope ignored=helper.beforeNative(b,true,model)){nativeCode.bind(b,model);}
    }
    private static void update(SingleNetworkLabelControls helper,FeatureOptions opts,String text,int sub){
        helper.update(opts,text,sub,2,-1,150,22,650,Collections.emptyMap(),Collections.emptyMap());
    }
    public static void main(String[] ignored){
        Native nativeCode=new Native();SingleNetworkLabelControls helper=new SingleNetworkLabelControls(nativeCode);Binder primary=new Binder(8),secondary=new Binder(21);
        event(helper,nativeCode,primary,"native8");event(helper,nativeCode,secondary,"native21");
        equal(false,SingleNetworkLabelControls.managed(primary.text));equal("native8",primary.text.getText());
        update(helper,options(true),"5G",8);
        equal(true,SingleNetworkLabelControls.managed(primary.text));equal(View.VISIBLE,primary.text.getVisibility());equal("5G",primary.text.getText());
        equal(View.GONE,secondary.text.getVisibility());equal(View.GONE,primary.image.getVisibility());equal(View.GONE,secondary.image.getVisibility());
        equal(11f,primary.text.getTranslationX());equal(1f,primary.text.getTranslationY());equal(30f,primary.text.getTextSize());equal(88,primary.text.getMinimumWidth());
        equal(primary.text.getTypeface(),secondary.text.getTypeface());
        // Main traffic SIM, not native first-or-null/slot order, determines the displayed generation.
        update(helper,options(true),"4G",21);equal(View.GONE,primary.text.getVisibility());equal(View.VISIBLE,secondary.text.getVisibility());equal("4G",secondary.text.getText());
        update(helper,options(true),"",21);equal(View.GONE,secondary.text.getVisibility());
        update(helper,options(true),"5G",8);int writes=primary.text.layoutRequests;
        for(int i=0;i<100;i++)update(helper,options(true),"5G",8);equal(writes,primary.text.layoutRequests);
        // Native density/font-size update reads 20 px, never our scaled 30 px. Nested init/bind closes once.
        try(SingleNetworkLabelControls.NativeScope init=helper.beforeNative(primary,false,null)){
            equal(20f,primary.text.getTextSize());
            try(SingleNetworkLabelControls.NativeScope bind=helper.beforeNative(primary,true,"native-new")){
                nativeCode.bind(primary,"native-new");primary.text.setTextSize(TypedValue.COMPLEX_UNIT_PX,24);primary.text.setTextColor(0xffffffff);
            }
            equal(24f,primary.text.getTextSize());
        }
        equal(36f,primary.text.getTextSize());equal("5G",primary.text.getText());
        update(helper,options(false),"5G",8);equal(false,SingleNetworkLabelControls.managed(primary.text));
        equal(24f,primary.text.getTextSize());equal(3f,primary.text.getTranslationX());equal(5f,primary.text.getTranslationY());equal(24,primary.text.getMinimumWidth());
        equal("native-new",primary.text.getText());equal(View.VISIBLE,primary.image.getVisibility());equal(View.VISIBLE,secondary.text.getVisibility());equal("native21",secondary.text.getText());
        int restored=primary.binds;update(helper,options(false),"5G",8);equal(restored,primary.binds);
        // Custom hide and no active data route own visibility without leaking native label text.
        Bundle hidden=new Bundle();hidden.putBoolean("label_enabled",true);hidden.putBoolean(StatusBarSettings.LABEL_HIDDEN,true);
        update(helper,FeatureOptions.from(hidden),"5G",8);equal(View.GONE,primary.text.getVisibility());equal(View.GONE,secondary.text.getVisibility());
        update(helper,options(true),"5G",-1);equal(View.GONE,primary.text.getVisibility());equal(View.GONE,secondary.text.getVisibility());
        update(helper,options(true),"5G",8);Bundle safe=new Bundle();safe.putBoolean("label_enabled",true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);
        update(helper,FeatureOptions.from(safe),"5G",8);equal("native-new",primary.text.getText());equal(false,SingleNetworkLabelControls.managed(primary.text));
        // Native tint is updated inside the OEM transaction; custom colors use the same policy as Compose.
        Map<String,Integer> colors=new LinkedHashMap<>();colors.put("label_color_light",0xffff0000);colors.put("label_color_dark",0xff00ff00);
        helper.update(options(true),"5G",8,0,0,100,22,400,colors,Collections.emptyMap());
        try(SingleNetworkLabelControls.NativeScope scope=helper.beforeNative(primary,false,null)){primary.text.setTextColor(0xff000000);}
        equal(0xffff0000,primary.text.getCurrentTextColor());
        helper.releaseRuntime();equal(false,SingleNetworkLabelControls.managed(primary.text));equal("native-new",primary.text.getText());equal(24f,primary.text.getTextSize());
        System.out.println("SingleNetworkLabelControls checks passed: "+checks);
    }
}
