package dev.puitheme;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import com.oplus.systemui.statusbar.phone.signal.widget.OplusModernStatusBarWifiView;
import java.util.HashMap;
import java.util.Map;

/** Visibility restoration uses the captured OnePlus wifi_inout resource identity. */
public final class NetworkBadgeCheck {
    private static int count;
    private static void require(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private static final class Badge extends View {
        String name;
        Badge(String name){super(null);this.name=name;}
        @Override public int getId(){return 100;}
        @Override public Resources getResources(){return new Resources(){@Override public String getResourceEntryName(int id){return name;}};}
    }
    private static final class Row extends ViewGroup {
        final String name;
        Row(String name){super(null);this.name=name;}
        @Override public int getId(){return 100;}
        @Override public Resources getResources(){return new Resources(){@Override public String getResourceEntryName(int id){return name;}};}
    }
    public static void main(String[] args) {
        Handler handler=new Handler(Looper.getMainLooper());NetworkBadgeControls controls=new NetworkBadgeControls(handler);
        Map<String,Object> settings=new HashMap<>();
        for(String name:new String[]{"wifi_inout","wifi_in","wifi_out","wifi_activity_container","mobile_inout","data_inout","mobile_type_5g","wifi_left_tv"}) {
            Badge view=new Badge(name);String key=NetworkBadgeControls.keyFor(name);
            require(key!=null,"candidate absent "+name);
            settings.clear();controls.configure(FeatureOptions.from(settings));controls.apply(view);
            require(view.getVisibility()==View.GONE,"candidate remains visible "+name);
            require(controls.suppressDraw(view),"compiled vendor draw bypass "+name);
            settings.put(key,false);controls.configure(FeatureOptions.from(settings));
            require(view.getVisibility()==View.VISIBLE,"disable does not restore native "+name);
            require(!controls.suppressDraw(view),"draw guard ignores disable "+name);
            settings.put(key,true);controls.configure(FeatureOptions.from(settings));
            require(controls.requestedVisibility(view,View.INVISIBLE)==View.GONE,"external update leaks "+name);
            settings.put(name.startsWith("wifi")?"wifi_enabled":"data_enabled",false);controls.configure(FeatureOptions.from(settings));
            require(view.getVisibility()==View.INVISIBLE,"master does not restore latest native request "+name);
            require(!controls.suppressDraw(view),"master does not disable draw guard "+name);
            // Native invisibility while hidden must survive turning the hiding switch off.
            settings.clear();controls.configure(FeatureOptions.from(settings));
            require(controls.requestedVisibility(view,View.GONE)==View.GONE,"native gone update "+name);
            settings.put(key,false);controls.configure(FeatureOptions.from(settings));
            require(view.getVisibility()==View.GONE,"native gone becomes visible "+name);
        }
        for(String name:new String[]{"wifi_signal","network_speed","wifi_combo","stacked_mobile","alarm_clock","clock"}) {
            require(NetworkBadgeControls.keyFor(name)==null,"main icon/text matched "+name);
            Badge view=new Badge(name);controls.apply(view);
            require(view.getVisibility()==View.VISIBLE,"main content hidden "+name);
            require(!controls.suppressDraw(view),"main draw suppressed "+name);
        }
        wifiMeasureStability(handler);
        System.out.println("NetworkBadgeCheck passed: "+count);
    }

    private static int visibleWidth(View view) {
        if(view.getVisibility()==View.GONE)return 0;
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;int width=0;
            for(int i=0;i<group.getChildCount();i++) {
                int next=visibleWidth(group.getChildAt(i));
                width=group instanceof Row&&((Row)group).name.equals("wifi_combo")?Math.max(width,next):width+next;
            }
            return width;
        }
        ViewGroup.LayoutParams params=view.getLayoutParams();return params==null?0:params.width;
    }
    private static Badge child(ViewGroup parent,String name,int width) {
        Badge child=new Badge(name);child.setLayoutParams(new ViewGroup.LayoutParams(width,56));parent.addView(child);return child;
    }
    private static void wifiMeasureStability(Handler handler) {
        NetworkBadgeControls controls=new NetworkBadgeControls(handler);Map<String,Object> values=new HashMap<>();
        controls.configure(FeatureOptions.from(values));
        OplusModernStatusBarWifiView owner=new OplusModernStatusBarWifiView();Row row=new Row("wifi_group");owner.addView(row);
        Row activity=new Row("inout_container");row.addView(activity);
        Badge in=child(activity,"wifi_in",14),out=child(activity,"wifi_out",14);
        Row combo=new Row("wifi_combo");row.addView(combo);
        Badge signal=child(combo,"wifi_signal",88),badge=child(combo,"wifi_left",160),text=child(combo,"wifi_left_tv",240);
        Badge overlay=child(combo,"wifi_inout",120),spacer=child(row,"wifi_signal_spacer",10);
        Badge airplane=child(row,"wifi_airplane_spacer",8);
        owner.appearAmount=.37f;owner.transXForCoord=4.25f;owner.translationX=20.01f;owner.translationY=-8.37f;owner.visibleState=1;
        require(WifiPlacement.isWifiOwner(owner),"exact native owner absent");
        WifiPlacement.beforeMeasure(owner,controls);int stableWidth=visibleWidth(owner);
        require(stableWidth==96,"native glyph-only width with real airplane gap");
        for(int i=0;i<64;i++) {
            // Simulate a vendor callback with inlined visibility setters after the earlier draw guard.
            in.setVisibility(i%2==0?View.VISIBLE:View.GONE);out.setVisibility(i%3==0?View.VISIBLE:View.GONE);
            overlay.setVisibility(i%5==0?View.VISIBLE:View.GONE);spacer.setVisibility(i%2==0?View.VISIBLE:View.GONE);
            activity.setVisibility(i%2==0?View.VISIBLE:View.GONE);badge.setVisibility(View.VISIBLE);text.setVisibility(View.VISIBLE);
            WifiPlacement.beforeMeasure(owner,controls);
            require(visibleWidth(owner)==stableWidth,"hidden activity changed glyph anchor "+i);
            require(300-visibleWidth(owner)+44==248,"right aligned glyph center moved "+i);
            require(activity.getVisibility()==View.GONE&&spacer.getVisibility()==View.GONE,"native hidden layout holes remain "+i);
            require(signal.getVisibility()==View.VISIBLE,"main glyph hidden "+i);
            require(airplane.getVisibility()==View.VISIBLE,"real airplane gap suppressed "+i);
        }
        require(owner.appearAmount==.37f&&owner.transXForCoord==4.25f&&owner.visibleState==1,"native appearance/state animation changed");
        require(owner.translationX==20.01f&&owner.translationY==-8.37f,"native placement changed");
        owner.setVisibility(View.GONE);WifiPlacement.beforeMeasure(owner,controls);
        require(owner.getVisibility()==View.GONE,"disconnected WiFi resurrected");owner.setVisibility(View.VISIBLE);
        airplane.setVisibility(View.GONE);WifiPlacement.beforeMeasure(owner,controls);
        require(visibleWidth(owner)==88&&airplane.getVisibility()==View.GONE,"actual airplane transition locked");
        controls.requestedVisibility(spacer,View.INVISIBLE);controls.requestedVisibility(activity,View.VISIBLE);
        controls.requestedVisibility(in,View.GONE);controls.requestedVisibility(out,View.INVISIBLE);
        values.put("wifi_activity_hidden",false);controls.configure(FeatureOptions.from(values));WifiPlacement.beforeMeasure(owner,controls);
        require(spacer.getVisibility()==View.INVISIBLE,"spacer latest native requested visibility not restored");
        require(activity.getVisibility()==View.VISIBLE,"container current native state not restored");
        require(in.getVisibility()==View.GONE&&out.getVisibility()==View.INVISIBLE,"latest arrow native requests not restored");
        values.put("wifi_activity_hidden",true);controls.configure(FeatureOptions.from(values));
        Badge detached=new Badge("inout_container");controls.apply(detached);
        require(detached.getVisibility()==View.VISIBLE,"generic activity ID outside WiFi suppressed");
        Row mobile=new Row("mobile_group");mobile.addView(detached);controls.apply(detached);
        require(detached.getVisibility()==View.VISIBLE,"mobile activity container classified as WiFi");
        Badge contextual=child(row,"inout_container",40);controls.apply(contextual);
        require(contextual.getVisibility()==View.GONE,"WiFi generic activity container not collapsed");
        contextual.parent=mobile;controls.apply(contextual);
        require(contextual.getVisibility()==View.VISIBLE,"reparent did not restore native visibility");
        badge.name="clock";controls.idChanged(badge);
        require(badge.getVisibility()==View.VISIBLE,"rebound resource retained hidden WiFi visibility");
        owner.resourceName="clock";spacer.setVisibility(View.VISIBLE);WifiPlacement.beforeMeasure(owner,controls);
        require(spacer.getVisibility()==View.VISIBLE,"unrelated resource measured as WiFi");
        owner.resourceName="wifi_combo";owner.resourcePackage="other.app";WifiPlacement.beforeMeasure(owner,controls);
        require(spacer.getVisibility()==View.VISIBLE,"foreign package measured as SystemUI WiFi");
    }
}
