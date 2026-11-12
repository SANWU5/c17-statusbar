package dev.puitheme;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Real module radio entry -> legacy slot gate + native single-SIM text, before
 * the queued refresh drains. The SDK doubles supply events, not display policy. */
public final class RadioTransitionCheck {
    private static int checks;
    public static final class Subscription { public static int current(){return 7;} }
    private static Field field(String name)throws Exception {
        Field value=StatusBarModule.class.getDeclaredField(name);value.setAccessible(true);return value;
    }
    private static void set(String name,Object value)throws Exception{field(name).set(null,value);}
    private static Object get(String name)throws Exception{return field(name).get(null);}
    private static Object call(String name)throws Exception {
        Method method=StatusBarModule.class.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(null);
    }
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        TelephonyManager phone=new TelephonyManager();WifiManager wifi=new WifiManager();
        ConnectivityManager network=new ConnectivityManager();
        Context context=new Context(){@Override public Object getSystemService(String name){
            return "phone".equals(name)?phone:"wifi".equals(name)?wifi:"connectivity".equals(name)?network:null;
        }};
        set("context",context);set("observedTelephony",phone);set("observedDataSub",7);
        set("getActiveDataSubId",Subscription.class.getMethod("current"));set("getDefaultDataSubId",Subscription.class.getMethod("current"));
        set("wifiConnected",false);set("wifiSwitchEnabled",false);set("mobileDataSwitchEnabled",true);
        set("airplaneMode",false);set("composeStatusBar",false);set("refreshPending",false);
        Bundle settings=new Bundle();settings.putBoolean("label_enabled",true);settings.putBoolean("label_text_style_enabled",false);
        set("FEATURES",FeatureOptions.from(settings));
        SingleNetworkLabelControls labels=(SingleNetworkLabelControls)get("SINGLE_LABEL");
        TextView text=new TextView(context);View image=new View(context);Object binding=new Object();
        String[] rat={"4G"};
        Field access=SingleNetworkLabelControls.class.getDeclaredField("access");access.setAccessible(true);
        access.set(labels,new SingleNetworkLabelControls.Access(){
            public TextView text(Object owner){return text;}
            public View image(Object owner){return image;}
            public int subscription(Object owner){return 7;}
            public String nativeLabel(Object owner){return rat[0];}
            public void bind(Object owner,Object model){ }
        });
        labels.bound(binding);call("refreshLabel");
        check(text.getVisibility()==View.VISIBLE&&!((String)get("networkLabel")).isEmpty(),"initial cellular label is visible");
        check(Boolean.TRUE.equals(call("cellularMode")),"legacy Wi-Fi-hosted label starts active");
        // Native bind may already have queued a label refresh before the radio event.
        StatusBarModule.scheduleRefresh();
        phone.dataEnabled=false;wifi.enabled=true;call("refreshRadio");
        check(Boolean.FALSE.equals(call("cellularMode")),"data OFF immediately revokes the legacy Wi-Fi host");
        check(text.getVisibility()==View.GONE,"data OFF must hide native cellular text in the same radio event, before queued refresh");
        check("".equals(get("networkLabel")),"shared text is cleared in the same radio event");
        Handler.drainForCheck();
        check(text.getVisibility()==View.GONE,"older queued work cannot resurrect the disabled label");
        for(int round=0;round<12;round++) {
            phone.dataEnabled=true;network.active=null;network.capabilities=null;call("refreshRadio");
            check(text.getVisibility()==View.VISIBLE,"current cellular label resumes when data is enabled");
            network.active=new Network();network.capabilities=new NetworkCapabilities().addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
            call("refreshRadio");
            check(text.getVisibility()==View.GONE&&!((Boolean)call("cellularMode")),"Wi-Fi connection retires both renderers together");
            phone.dataEnabled=false;network.active=null;network.capabilities=null;call("refreshRadio");Handler.drainForCheck();
            check(text.getVisibility()==View.GONE&&"".equals(get("networkLabel")),"disconnecting Wi-Fi cannot reveal voice RAT with data OFF");
        }
        phone.dataEnabled=true;call("refreshRadio");Handler.drainForCheck();
        rat[0]="5G";
        for(int event=0;event<100;event++)StatusBarModule.scheduleRefresh();
        check("4G".contentEquals(text.getText()),"ordinary RAT events remain queued rather than applying repeatedly");
        check(Handler.postedForCheck.size()==1,"ordinary RAT events share a single pending refresh");
        Handler.drainForCheck();check("5G".contentEquals(text.getText()),"the coalesced refresh reads the latest RAT");
        // A superseded runnable must not swallow a newer request queued after
        // the synchronous transport boundary.
        StatusBarModule.scheduleRefresh();phone.dataEnabled=false;call("refreshRadio");
        phone.dataEnabled=true;rat[0]="4G";call("refreshRadio");
        rat[0]="5G";StatusBarModule.scheduleRefresh();Handler.drainForCheck();
        check("5G".contentEquals(text.getText()),"older queued work preserves the newest pending update");
        Handler.postedForCheck.clear();System.out.println("RadioTransitionCheck: "+checks+" checks passed");
    }
}
