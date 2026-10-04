package dev.puitheme;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.oplus.systemui.plugins.seedling.card.ui.view.CardBackgroundView;
import com.oplus.systemui.plugins.seedling.card.ui.view.CardView;
import com.oplus.systemui.plugins.shared.template.section.media.K0;
import com.oplus.systemui.plugins.shared.template.section.media.s1;

/** Verifies exact ownership, balanced native scopes, alpha preservation and restore. */
public final class FluidCloudAccentCheck {
    private static int checks;
    private static void check(boolean okay, String why) {
        checks++; if (!okay) throw new AssertionError(why);
    }
    private static class PluginResources extends android.content.res.Resources {
        @Override public String getResourceEntryName(int id) {
            return id == 1 ? "card_background_mix_color" : id == 2 ? "card_background_gradient_middle_color" : "card_content_title_color";
        }
    }
    private static final class LateFailureResources extends PluginResources {
        boolean fail=true;
        @Override public String getResourceEntryName(int id) {
            if(id==2&&fail){fail=false;throw new IllegalStateException("Native later gradient lookup failed after mix was written");}
            return super.getResourceEntryName(id);
        }
    }
    private static Bundle enabled(boolean value) {
        Bundle result = new Bundle(); result.putBoolean(FluidCloudAccent.ENABLED, value); return result;
    }
    private static void refresh(FluidCloudAccent control) throws Exception {
        // Desktop Handler doesn't run posts; explicitly execute the real batch entries.
        Field backgrounds = FluidCloudAccent.class.getDeclaredField("backgrounds"); backgrounds.setAccessible(true);
        Class<?> bindingClass = Class.forName("dev.puitheme.FluidCloudAccent$BackgroundBinding");
        Method apply = FluidCloudAccent.class.getDeclaredMethod("replay", bindingClass, Boolean.TYPE); apply.setAccessible(true);
        for (Object binding : new java.util.ArrayList<>(((java.util.Map<?,?>) backgrounds.get(control)).values()))
            apply.invoke(control, binding, control.enabled());
    }
    public static void main(String[] args) throws Exception {
        FluidCloudAccent control = new FluidCloudAccent();
        FluidCloudAccent.Hooks hooks = control.resolve(FluidCloudAccentCheck.class.getClassLoader());
        check(hooks.background.getName().equals("b"), "real fingerprint resolves");
        check(!control.enabled(), "default disabled");
        check(!FluidCloudAccent.isBackgroundColor("card_content_title_color"), "content color stays native");
        check(!FluidCloudAccent.isBackgroundColor("capsule_background_color"), "collapsed capsule stays native");
        check(FluidCloudAccent.preserveAlpha(0x00123456, 0xffaabbcc) == 0x00123456, "transparent native colors stay unchanged");
        PluginResources resources = new PluginResources();
        CardView card = new CardView(); CardBackgroundView backdrop = new CardBackgroundView(resources);
        ViewGroup root = new ViewGroup(null); card.addView(backdrop); card.addView(root);
        kotlinx.coroutines.flow.p colors = new kotlinx.coroutines.flow.p(
                new com.oplus.systemui.plugins.shared.data.media.model.a(0xffd4798e));
        s1 owner = new s1(null, new K0(colors), root, null);
        com.heytap.log.nx.obus.a.controller = control;
        com.heytap.log.nx.obus.a helper = new com.heytap.log.nx.obus.a(null, 3);
        com.oplus.view.ViewRootManager manager = new com.oplus.view.ViewRootManager();
        helper.b(backdrop, manager);
        check(com.heytap.log.nx.obus.a.gradient == 0xff000000, "disabled native black remains");
        control.mediaChanged(owner); control.configure(enabled(true)); refresh(control);
        check(com.heytap.log.nx.obus.a.mixed == 0xb3d4798e, "mix adopts exact OEM accent preserving alpha");
        check(com.heytap.log.nx.obus.a.gradient == 0xffd4798e, "black gradient receives accent");
        check(control.resourceColor(resources, 2, 0xff000000) == 0xff000000, "native scope does not leak");
        int builds = com.heytap.log.nx.obus.a.builds;
        for (int i = 0; i < 1000; i++) refresh(control);
        check(com.heytap.log.nx.obus.a.builds == builds, "unchanged bindings don't rebuild blur or gradient");
        control.nativeBlurAlpha(backdrop, .36f);
        colors.value = new com.oplus.systemui.plugins.shared.data.media.model.a(0xff66aadd);
        control.mediaChanged(owner); refresh(control);
        check(com.heytap.log.nx.obus.a.gradient == 0xff66aadd, "native color update is observed");
        check(backdrop.blurAlpha == .36f, "native transition alpha survives recolor");
        check(control.beginBackground(helper, new View(null), manager) == null, "unrelated host can't open scope");
        FluidCloudAccent.BackgroundScope scope = control.beginBackground(helper, backdrop, manager);
        try {
            check(control.resourceColor(new PluginResources(), 2, 0xff000000) == 0xff000000, "other Resources isn't recolored");
            check(control.resourceColor(resources, 3, 0xffffffff) == 0xffffffff, "title color retained");
            throw new IllegalStateException("native failure");
        } catch (IllegalStateException expected) { } finally { scope.close(); }
        check(control.resourceColor(resources, 2, 0xff000000) == 0xff000000, "failed native scope closes");
        Bundle safe = enabled(true); safe.putBoolean(StatusBarSettings.SAFE_MODE, true);
        control.configure(safe); refresh(control);
        check(!control.enabled() && com.heytap.log.nx.obus.a.gradient == 0xff000000, "safe mode restores original native material");
        control.configure(enabled(true)); refresh(control); control.mediaDisposed(owner); refresh(control);
        check(com.heytap.log.nx.obus.a.gradient == 0xff000000, "unbind discards stale accent and restores");
        control.mediaChanged(owner); refresh(control); control.release();
        check(!control.enabled(), "release cannot retain ownership");
        lateNativeFailureRecovery();
        System.out.println("FluidCloudAccentCheck: " + checks + " checks passed");
    }
    private static void lateNativeFailureRecovery() throws Exception {
        // The real intercepted helper writes mix first and then reads gradient. An
        // exception in that later read must retain dirty ownership on the very first tint.
        for(int mode=0;mode<6;mode++){
            FluidCloudAccent control=new FluidCloudAccent();control.resolve(FluidCloudAccentCheck.class.getClassLoader());
            LateFailureResources resources=new LateFailureResources();
            CardView card=new CardView();CardBackgroundView backdrop=new CardBackgroundView(resources);
            ViewGroup root=new ViewGroup(null);card.addView(backdrop);card.addView(root);
            s1 owner=new s1(null,new K0(new kotlinx.coroutines.flow.p(new com.oplus.systemui.plugins.shared.data.media.model.a(0xff773399))),root,null);
            com.heytap.log.nx.obus.a.controller=control;com.heytap.log.nx.obus.a helper=new com.heytap.log.nx.obus.a(null,3);
            com.oplus.view.ViewRootManager manager=new com.oplus.view.ViewRootManager();
            helper.b(backdrop,manager);control.mediaChanged(owner);control.configure(enabled(true));
            int before=com.heytap.log.nx.obus.a.builds;
            boolean threw=false;
            try{helper.b(backdrop,manager);}catch(IllegalStateException expected){threw=true;}
            check(threw,"later native helper failure is real: "+mode);
            check(com.heytap.log.nx.obus.a.builds==before+1,"failure doesn't immediately replay the throwing helper: "+mode);
            check(com.heytap.log.nx.obus.a.mixed==0xb3773399,"native mix was partially tinted before failure: "+mode);
            check(com.heytap.log.nx.obus.a.gradient==0xff000000,"later native write did not complete: "+mode);
            check(control.resourceColor(resources,1,0xb3262626)==0xb3262626,"throwing scope leaves unrelated reads native: "+mode);
            before=com.heytap.log.nx.obus.a.builds;
            if(mode==0){control.configure(enabled(false));refresh(control);}
            else if(mode==1){Bundle safe=enabled(true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);control.configure(safe);refresh(control);}
            else if(mode==2){control.mediaDisposed(owner);refresh(control);}
            else if(mode==3)control.detach(backdrop);
            else if(mode==4)control.release();
            else{
                refresh(control);
                check(com.heytap.log.nx.obus.a.gradient==0xff773399,"enabled dirty binding retries a full material: "+mode);
                int built=com.heytap.log.nx.obus.a.builds;for(int i=0;i<1000;i++)refresh(control);
                check(com.heytap.log.nx.obus.a.builds==built,"completed retry resumes color deduplication: "+mode);
                control.configure(enabled(false));refresh(control);
            }
            check(com.heytap.log.nx.obus.a.builds>before,"dirty material receives native reinitialization: "+mode);
            check(com.heytap.log.nx.obus.a.mixed==0xb3262626&&com.heytap.log.nx.obus.a.gradient==0xff000000,"native material fully restored after failure: "+mode);
            control.release();
        }
    }
}
