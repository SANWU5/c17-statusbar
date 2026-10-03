package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Independent binder transactions and real-subscription ownership, without Compose assumptions. */
public final class NativeNetworkBadgeBindingsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Native badge bridge "+checks+": "+expected+" != "+actual);}
    private static final class Glyph extends Drawable {
        public void draw(Canvas c){}public void setAlpha(int a){}public void setColorFilter(ColorFilter c){}public int getOpacity(){return -3;}
    }
    private static final class Binding {
        final ViewGroup host;final TextView text;final ImageView image,signal;
        final int subscription;
        NativeNetworkBadgeBindings.Parts model=new NativeNetworkBadgeBindings.Parts("5G", "+");
        Binding(int subscription){this.subscription=subscription;host=new ViewGroup(new Context());
            text=new TextView(new Context());image=new ImageView(new Context());signal=new ImageView(new Context());
            signal.setImageDrawable(new Glyph());host.addView(signal);host.addView(text);host.addView(image);
            text.setText("5G+");image.setVisibility(View.GONE);}
        Binding(Binding previous,int subscription){this.subscription=subscription;host=previous.host;text=previous.text;image=previous.image;signal=previous.signal;}
    }
    private static final class Access implements NativeNetworkBadgeBindings.Access,SingleMobileIconControls.Access {
        int reads,parts;boolean failParts;
        public TextView text(Object o){return ((Binding)o).text;}
        public View image(Object o){return ((Binding)o).image;}
        public View host(Object o){return ((Binding)o).host;}
        public int subscription(Object o){return ((Binding)o).subscription;}
        public Object model(Object o){reads++;return ((Binding)o).model;}
        public NativeNetworkBadgeBindings.Parts parts(Object model){parts++;if(failParts)throw new IllegalStateException("native model unavailable");return model==null?NativeNetworkBadgeBindings.Parts.UNKNOWN:(NativeNetworkBadgeBindings.Parts)model;}
        public ImageView icon(Object o){return ((Binding)o).signal;}
        public boolean rendererOwnsPlacement(Drawable d){return false;}
    }
    private static final class Styler implements NativeNetworkBadgeBindings.TextStyler {
        final Map<TextView,String> nativeText=new WeakHashMap<>(),outputs=new WeakHashMap<>();
        final Map<TextView,Integer> roles=new WeakHashMap<>();
        boolean failBefore;
        public void beforeNative(TextView view){
            if(failBefore){failBefore=false;throw new IllegalStateException("native font unavailable");}
            String shown=outputs.remove(view);if(shown!=null&&shown.equals(view.getText().toString()))view.setText(nativeText.get(view));
        }
        public void updated(TextView view,String prefix,String suffix,int role){
            beforeNative(view);roles.put(view,role);
            if(role<=0||prefix==null||suffix==null||!(prefix+suffix).equals(view.getText().toString())){nativeText.remove(view);return;}
            String raw=view.getText().toString(),shown="["+role+"]"+raw;nativeText.put(view,raw);outputs.put(view,shown);view.setText(shown);
        }
    }
    private static FeatureOptions options(boolean data,boolean label,boolean safe){Bundle b=new Bundle();b.putBoolean("data_enabled",data);b.putBoolean("label_enabled",label);b.putBoolean(StatusBarSettings.SAFE_MODE,safe);return FeatureOptions.from(b);}
    public static void main(String[] args)throws Exception{
        Access access=new Access();Styler style=new Styler();SingleMobileIconControls signal=new SingleMobileIconControls(access);
        NativeNetworkBadgeBindings bridge=new NativeNetworkBadgeBindings(access,style,signal);
        ViewGroup phone=new ViewGroup(new Context());Binding main=new Binding(11),other=new Binding(22);phone.addView(main.host);phone.addView(other.host);
        FeatureOptions enabled=options(true,false,false);
        signal.update(enabled,0,0,100,true,11);signal.bound(main);signal.bound(other);
        bridge.configure(enabled,11);bridge.bound(main);bridge.bound(other);
        equal("[1]5G+",main.text.getText().toString());equal("[2]5G+",other.text.getText().toString());
        equal(View.VISIBLE,main.text.getVisibility());equal(View.GONE,other.text.getVisibility());
        equal(View.GONE,other.image.getVisibility());equal(View.VISIBLE,other.host.getVisibility());
        int reads=access.reads;
        for(int i=0;i<200;i++){
            int primary=(i&1)==0?22:11;signal.activeSubscription(primary);bridge.activeSubscription(primary);
            equal(primary==11?1:2,style.roles.get(main.text));equal(primary==22?1:2,style.roles.get(other.text));
            equal(primary==11?View.VISIBLE:View.GONE,main.text.getVisibility());
            equal(primary==22?View.VISIBLE:View.GONE,other.text.getVisibility());
        }
        equal(reads,access.reads);
        FeatureOptions disabled=options(false,false,false),safe=options(true,false,true);
        signal.update(disabled,0,0,100,true,11);bridge.configure(disabled,11);
        equal(View.VISIBLE,other.text.getVisibility());equal(View.GONE,other.image.getVisibility());
        signal.update(enabled,0,0,100,true,11);bridge.configure(enabled,11);equal(View.GONE,other.text.getVisibility());
        signal.update(safe,0,0,100,true,11);bridge.configure(safe,11);equal(View.VISIBLE,other.text.getVisibility());
        signal.update(enabled,0,0,100,true,11);bridge.configure(enabled,11);equal(View.GONE,other.text.getVisibility());
        signal.update(enabled,0,0,100,false,11);bridge.configure(enabled,11);
        equal(View.VISIBLE,other.text.getVisibility());equal(View.GONE,other.image.getVisibility());
        signal.activeSubscription(-1);bridge.activeSubscription(-1);
        equal(0,style.roles.get(main.text));equal(0,style.roles.get(other.text));equal("5G+",main.text.getText());equal("5G+",other.text.getText());
        signal.update(enabled,0,0,100,true,11);bridge.configure(enabled,11);
        // Model/font callbacks always restore the actual native text/visibility first.
        main.model=new NativeNetworkBadgeBindings.Parts("4G", "+");
        try(NativeNetworkBadgeBindings.NativeScope event=bridge.beforeNative(main,true,main.model)){
            equal("5G+",main.text.getText());main.text.setText("4G+");main.text.setVisibility(View.INVISIBLE);
            try(NativeNetworkBadgeBindings.NativeScope nested=bridge.beforeNative(main,false,null)){equal("4G+",main.text.getText());}
            equal("4G+",main.text.getText());
        }
        equal("[1]4G+",main.text.getText());equal(View.INVISIBLE,main.text.getVisibility());
        main.model=new NativeNetworkBadgeBindings.Parts("LTE", "");
        try(NativeNetworkBadgeBindings.NativeScope font=bridge.beforeNative(main,false,null)){main.text.setText("LTE");main.text.setVisibility(View.VISIBLE);}
        equal("[1]LTE",main.text.getText());
        try(NativeNetworkBadgeBindings.NativeScope event=bridge.beforeNative(other,true,null)){other.text.setText("");other.text.setVisibility(View.GONE);other.image.setVisibility(View.VISIBLE);}
        equal(0,style.roles.get(other.text));equal(View.GONE,other.image.getVisibility());
        signal.update(enabled,0,0,100,false,11);bridge.configure(enabled,11);
        equal(View.GONE,other.text.getVisibility());equal(View.VISIBLE,other.image.getVisibility());
        customLabel(bridge,style,main);
        // Model/typography failures must not strand the transaction depth.
        access.failParts=true;bridge.beforeNative(main,true,main.model).close();access.failParts=false;
        style.failBefore=true;bridge.beforeNative(main,false,null).close();
        try(NativeNetworkBadgeBindings.NativeScope event=bridge.beforeNative(main,false,null)){main.text.setText("LTE");}
        bridge.configure(enabled,11);equal("[1]LTE",main.text.getText());
        // A recycled native glyph keeps one baseline/listener; the old owner cannot restyle it.
        Binding replacement=new Binding(main,33);signal.bound(replacement);bridge.bound(replacement);
        equal(2,style.roles.get(main.text));equal(1,main.text.attachListeners.size());
        bridge.bound(main);equal(2,style.roles.get(main.text));
        signal.activeSubscription(33);bridge.activeSubscription(33);equal(1,style.roles.get(main.text));
        main.text.attachListeners.get(0).onViewDetachedFromWindow(main.text);equal(0,style.roles.get(main.text));equal("LTE",main.text.getText());
        main.text.attachListeners.get(0).onViewAttachedToWindow(main.text);equal(1,style.roles.get(main.text));
        int beforeRelease=access.reads;
        bridge.releaseRuntime();bridge.releaseRuntime();signal.releaseRuntime();
        equal("LTE",main.text.getText());equal(View.VISIBLE,other.image.getVisibility());
        equal(0,main.text.attachListeners.size());equal(0,other.text.attachListeners.size());
        bridge.bound(replacement);bridge.configure(enabled,33);bridge.activeSubscription(11);bridge.beforeNative(replacement,true,replacement.model).close();
        equal(beforeRelease,access.reads);equal(true,bridge.diagnosticSummary().startsWith("Native badge independent rows 0"));
        System.out.println("NativeNetworkBadgeBindings checks passed: "+checks);
    }
    private static void customLabel(NativeNetworkBadgeBindings bridge,Styler style,Binding row)throws Exception{
        FeatureOptions custom=options(true,true,false);bridge.configure(custom,11);
        equal(0,style.roles.get(row.text));equal("LTE",row.text.getText());
        Field field=SingleNetworkLabelControls.class.getDeclaredField("MANAGED");field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<View,Boolean> managed=(Map<View,Boolean>)field.get(null);
        managed.put(row.text,true);
        try{
            try(NativeNetworkBadgeBindings.NativeScope event=bridge.beforeNative(row,false,null)){
                row.text.setText("custom main");row.text.setVisibility(View.GONE);
            }
            equal("custom main",row.text.getText());equal(View.GONE,row.text.getVisibility());equal(0,style.roles.get(row.text));
            bridge.configure(options(true,false,false),11);equal("custom main",row.text.getText());
        }finally{managed.remove(row.text);}
        try(NativeNetworkBadgeBindings.NativeScope replay=bridge.beforeNative(row,false,null)){row.text.setText("LTE");row.text.setVisibility(View.VISIBLE);}
        equal("[1]LTE",row.text.getText());equal(View.VISIBLE,row.text.getVisibility());
    }
}
