package dev.puitheme;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/** Replays the captured native Wi-Fi host -> ComposeView -> AndroidComposeView hierarchy. */
public final class NetworkOverflowCheck {
    private static int checks;
    private static void require(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        Class<?> module=Class.forName("dev.puitheme.StatusBarModule");
        Method slot=module.getDeclaredMethod("isNetworkSlot",String.class);slot.setAccessible(true);
        // Captured from the actual OnePlus host. The lifecycle guard must reach this tree.
        require((Boolean)slot.invoke(null,"stacked_mobile"),"actual stacked_mobile host is excluded by lifecycle guard");
        require((Boolean)slot.invoke(null,"stacked_mobile_2"),"stacked mobile multi-SIM slot excluded");
        require((Boolean)slot.invoke(null,"mobile"),"classic mobile slot excluded");
        require((Boolean)slot.invoke(null,"wifi"),"Wi-Fi slot excluded");
        require((Boolean)slot.invoke(null,"wifi_secondary"),"secondary Wi-Fi excluded");
        require(!(Boolean)slot.invoke(null,"alarm_clock"),"unrelated slot included");
        require(!(Boolean)slot.invoke(null,new Object[]{null}),"null slot included");
        Method allow=module.getDeclaredMethod("allowDrawableOverflow",View.class);allow.setAccessible(true);
        Field features=module.getDeclaredField("FEATURES");features.setAccessible(true);
        Map<String,Object> values=new HashMap<>();values.put("data_enabled",true);values.put("label_enabled",true);
        features.set(null,FeatureOptions.from(values));
        ViewGroup parent=new ViewGroup(null),host=new ViewGroup(null),compose=new ViewGroup(null),androidOwner=new ViewGroup(null);
        parent.addView(host);host.addView(compose);compose.addView(androidOwner);
        host.setMinimumWidth(72);host.setClipBounds(new Rect(0,0,72,80));
        androidOwner.setClipBounds(new Rect(0,0,72,80));
        allow.invoke(null,host);
        require(!parent.getClipChildren(),"status icon container still clips moved Wi-Fi");
        require(!host.getClipChildren(),"native Wi-Fi host still clips");
        require(!compose.getClipChildren(),"nested ComposeView still clips moved Wi-Fi");
        require(!androidOwner.getClipChildren(),"AndroidComposeView still clips moved Wi-Fi");
        require(host.getClipBounds()==null,"native clip bounds still crop moved Wi-Fi");
        require(androidOwner.getClipBounds()==null,"Compose native clip bounds still crop moved Wi-Fi");
        require(host.getMinimumWidth()==72,"clipping fix changes neighboring icon anchors");
        values.put("data_enabled",false);values.put("label_enabled",false);
        features.set(null,FeatureOptions.from(values));module.getMethod("invalidateLiveDrawables").invoke(null);
        require(parent.getClipChildren(),"all network groups off leaves ancestor unclipped");
        require(host.getClipChildren(),"all network groups off leaves host unclipped");
        require(compose.getClipChildren(),"all network groups off leaves ComposeView unclipped");
        require(androidOwner.getClipChildren(),"all network groups off leaves AndroidComposeView unclipped");
        require(host.getClipBounds()!=null&&host.getClipBounds().right==72,"original host clip bounds not restored");
        require(androidOwner.getClipBounds()!=null&&androidOwner.getClipBounds().right==72,"original nested clip bounds not restored");
        values.clear();values.put("data_enabled",true);values.put("label_enabled",true);
        features.set(null,FeatureOptions.from(values));module.getMethod("invalidateLiveDrawables").invoke(null);
        require(!androidOwner.getClipChildren()&&androidOwner.getClipBounds()==null,"re-enable does not restore overflow allowance");
        for(String group:new String[]{"data","label"}){values.put(group+"_position_enabled",false);values.put(group+"_size_enabled",false);}
        features.set(null,FeatureOptions.from(values));module.getMethod("invalidateLiveDrawables").invoke(null);
        require(androidOwner.getClipChildren(),"position and size switches off do not release clipping lease");
        require(host.getMinimumWidth()==72,"restore changes native layout anchor");
        values.clear();features.set(null,FeatureOptions.from(values));
        System.out.println("NetworkOverflowCheck passed: "+checks);
    }
}
