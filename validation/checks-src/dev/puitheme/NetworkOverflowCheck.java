package dev.puitheme;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;

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
        System.out.println("NetworkOverflowCheck passed: "+checks);
    }
}
