package dev.puitheme;
import android.os.Bundle;
import android.view.ViewGroup;
import android.content.Context;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSIconView;
public final class QsTileIconSizeCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++; if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static Bundle values(boolean enabled, float size) {
        Bundle values = new Bundle(); values.putBoolean(QsTileIconSize.MASTER, enabled); values.putFloat(QsTileIconSize.SIZE, size); return values;
    }
    public static void main(String[] arguments) {
        equal(false, QsTileIconSize.BOOLEANS.get(QsTileIconSize.MASTER));
        for (Object value : new Object[]{null, "bad", Float.NaN, Float.POSITIVE_INFINITY}) equal(28f, QsTileIconSize.sizeDp(value));
        equal(0f, QsTileIconSize.sizeDp(-2f)); equal(80f, QsTileIconSize.sizeDp(80f)); equal(31.5f, QsTileIconSize.sizeDp("31.5"));
        equal(1e30f, QsTileIconSize.sizeDp(1e30f));
        QsTileIconSize controller = new QsTileIconSize();
        OplusQSResizeableTileViewOneXOne tile = new OplusQSResizeableTileViewOneXOne();
        OplusQSIconView icon = new OplusQSIconView(); tile.icon = icon;
        ViewGroup root = new ViewGroup(new Context()); root.addView(tile); icon.parent = tile;
        controller.onNativeUpdate(tile);
        equal(56, controller.dimension(icon, 11, 56));
        controller.configure(values(true, 31.5f));
        equal(63, controller.dimension(icon, 11, 56)); equal(63, controller.dimension(icon, 12, 56));
        equal(48, controller.dimension(icon, 99, 48)); equal(48, controller.dimension(icon, 0, 48));
        controller.configure(values(true, 80f)); equal(160, controller.dimension(icon, 11, 56));
        controller.configure(values(true, 1e30f)); equal(240, controller.dimension(icon, 11, 56));
        icon.width = icon.height = 0; equal(65535, controller.dimension(icon, 11, 56));
        icon.ratio = 1e30f; controller.onIconMeasure(icon); equal(0, controller.dimension(icon, 11, 56));
        icon.width = icon.height = 240; icon.ratio = 1f;
        controller.configure(values(true, 31.5f));
        int requests = icon.layoutRequests;
        icon.parent = tile;
        for (int i = 0; i < 10000; i++) { controller.onNativeUpdate(tile); controller.onIconMeasure(icon); controller.dimension(icon, 11, 56); }
        equal(requests, icon.layoutRequests);
        controller.configure(values(true, 31.5f)); equal(requests, icon.layoutRequests);
        icon.ratio = 2f; icon.width = 80; icon.height = 100;
        controller.onIconMeasure(icon);
        equal(40, controller.dimension(icon, 11, 56)); equal(2f, icon.ratio);
        controller.configure(values(true, 0f)); equal(0, controller.dimension(icon, 11, 56));
        equal(80, icon.width); equal(100, icon.height); equal(124, tile.getWidth()); equal(80, tile.getHeight());
        controller.configure(values(false, 40f)); equal(56, controller.dimension(icon, 11, 56));
        Bundle safe = values(true, 40f); safe.putBoolean(StatusBarSettings.SAFE_MODE, true);
        controller.configure(safe); equal(56, controller.dimension(icon, 11, 56));
        safe.putBoolean(StatusBarSettings.SAFE_MODE, false); controller.configure(safe); equal(40, controller.dimension(icon, 11, 56));
        icon.ratio = 1f; icon.width = icon.height = 240;
        controller.onIconMeasure(icon);
        icon.getResources().getDisplayMetrics().density = Float.NaN;
        equal(56, controller.dimension(icon, 11, 56)); icon.getResources().getDisplayMetrics().density = 2f;
        icon.attached = false; equal(56, controller.dimension(icon, 11, 56)); icon.attached = true;
        tile.attached = false; equal(56, controller.dimension(icon, 11, 56)); tile.attached = true;
        icon.parent = new ViewGroup(new Context()); equal(56, controller.dimension(icon, 11, 56)); icon.parent = tile;
        OplusQSIconView replacement = new OplusQSIconView(); replacement.parent = tile; tile.icon = replacement;
        controller.onNativeUpdate(tile); equal(56, controller.dimension(icon, 11, 56)); equal(80, controller.dimension(replacement, 11, 56));
        controller.detach(tile); equal(56, controller.dimension(replacement, 11, 56));
        OplusQSResizeableTileViewTwoXOne large = new OplusQSResizeableTileViewTwoXOne(); large.icon = icon; large.parent = root;
        controller.onNativeUpdate(large); equal(56, controller.dimension(icon, 11, 56));

        // The native child calls dimen during its own measurement before any after-parent hook.
        // Discover the real ancestor on that first pass without requesting a second layout.
        QsTileIconSize fresh = new QsTileIconSize(); fresh.configure(values(true, 15f));
        OplusQSResizeableTileViewOneXOne first = new OplusQSResizeableTileViewOneXOne(); first.parent = root;
        OplusQSIconView firstIcon = new OplusQSIconView(); first.icon = firstIcon; firstIcon.parent = first;
        equal(56, fresh.dimension(firstIcon, 11, 56));
        int firstRequests = firstIcon.layoutRequests;
        fresh.onIconMeasure(firstIcon);
        equal(30, fresh.dimension(firstIcon, 11, 56));
        equal(firstRequests, firstIcon.layoutRequests);
        // Current native animation ratio is sampled once per measurement, never overwritten.
        firstIcon.ratio = .5f; firstIcon.width = firstIcon.height = 10;
        fresh.onIconMeasure(firstIcon); equal(20, fresh.dimension(firstIcon, 12, 56)); equal(.5f, firstIcon.ratio);
        OplusQSIconView recycled = new OplusQSIconView(); recycled.parent = first; first.icon = recycled;
        fresh.onIconMeasure(recycled);
        equal(30, fresh.dimension(recycled, 11, 56)); equal(56, fresh.dimension(firstIcon, 11, 56));
        recycled.parent = large; large.icon = recycled; fresh.onIconMeasure(recycled);
        equal(56, fresh.dimension(recycled, 11, 56));
        recycled.parent = first; first.icon = recycled; fresh.onIconMeasure(recycled);
        equal(30, fresh.dimension(recycled, 11, 56));
        fresh.releaseRuntime(); equal(56, fresh.dimension(recycled, 11, 56));
        deviceCards();
        orphanDevices();
        invalidCopiedLayout();
        System.out.println("QsTileIconSizeCheck passed: " + checks + " (ordinary/square device glyph dimensions, native constraints/zero, physical bounds, restore and no steady layouts)");
    }

    private static final class GlyphParams extends ViewGroup.LayoutParams {
        int margin=7,anchor=8; String ratio="1:1"; float heightPercent=.4516f;
        GlyphParams(int width,int height){super(width,height);}
    }
    private static final class DeviceGlyph extends android.widget.ImageView {
        QsTileIconSize owner;
        DeviceGlyph(){super(new Context());}
        @Override public void setLayoutParams(ViewGroup.LayoutParams params) {
            if(owner!=null)owner.deviceLayout(this,params);super.setLayoutParams(params);
        }
        @Override public void setAlpha(float value){super.setAlpha(owner==null?value:owner.deviceAlpha(this,value));}
    }
    private static DeviceGlyph glyph(QsTileIconSize owner,android.view.View parent) {
        DeviceGlyph glyph=new DeviceGlyph();glyph.getResources().getDisplayMetrics().density=2f;
        glyph.parent=parent;glyph.setLayoutParams(new GlyphParams(56,56));glyph.owner=owner;return glyph;
    }
    private static void deviceCards() {
        QsTileIconSize owner=new QsTileIconSize();
        com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareDeviceCardView card=
                new com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareDeviceCardView();
        com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareEntranceCardView entrance=
                new com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareEntranceCardView();
        ViewGroup root=new ViewGroup(new Context());card.parent=root;entrance.parent=root;
        DeviceGlyph headphones=glyph(owner,card.p),space=glyph(owner,entrance.body);
        card.p.f=headphones;entrance.m=space;
        GlyphParams hp=(GlyphParams)headphones.getLayoutParams(),sp=(GlyphParams)space.getLayoutParams();
        Object ring=card.p.c,charging=card.p.d;
        owner.onDeviceUpdate(card);owner.onDeviceUpdate(entrance);equal(56,hp.width);equal(56,sp.width);
        owner.configure(values(true,4f));equal(8,hp.width);equal(8,hp.height);equal(8,sp.width);equal(8,sp.height);
        equal(hp,headphones.getLayoutParams());equal(7,hp.margin);equal(8,hp.anchor);equal("1:1",hp.ratio);equal(.4516f,hp.heightPercent);
        equal(ring,card.p.c);equal(charging,card.p.d);equal(124,card.getWidth());equal(80,card.getHeight());
        equal(card.outer,card.getBackground());equal(1f,headphones.getAlpha());
        owner.configure(values(true,32f));equal(64,hp.width);equal(64,sp.width);
        owner.configure(values(true,1e30f));equal(80,hp.width);equal(80,sp.width);
        int requests=headphones.layoutRequests+space.layoutRequests;
        int invalidations=headphones.invalidations+space.invalidations;
        for(int n=0;n<10000;n++) {
            owner.onDeviceUpdate(card);owner.onDeviceUpdate(entrance);owner.onDeviceGlyphUpdate(card.p);
            card.dispatchLayoutChange(124,80,124,80);entrance.dispatchLayoutChange(124,80,124,80);
        }
        equal(requests,headphones.layoutRequests+space.layoutRequests);
        equal(invalidations,headphones.invalidations+space.invalidations);
        // A native constraints-only write must not capture our size as its original baseline.
        hp.margin=19;hp.anchor=22;headphones.setLayoutParams(hp);
        GlyphParams copied=new GlyphParams(sp.width,sp.height);copied.margin=21;space.setLayoutParams(copied);
        owner.configure(values(false,30f));equal(56,hp.width);equal(56,hp.height);
        equal(19,hp.margin);equal(22,hp.anchor);equal(56,copied.width);equal(56,copied.height);equal(21,copied.margin);
        owner.configure(values(true,32f));
        hp.width=43;headphones.setLayoutParams(hp);equal(64,hp.width);
        owner.configure(values(false,32f));equal(43,hp.width);equal(56,hp.height);
        // Observe legitimate native match/wrap dimensions per axis and restore them exactly.
        for(int nativeWidth:new int[]{0,-1,-2,49}) {
            owner.configure(values(true,32f));
            GlyphParams nativeParams=new GlyphParams(nativeWidth,-2);nativeParams.margin=33;
            headphones.setLayoutParams(nativeParams);equal(64,nativeParams.width);equal(64,nativeParams.height);
            owner.configure(values(false,32f));equal(nativeWidth,nativeParams.width);equal(-2,nativeParams.height);equal(33,nativeParams.margin);
        }
        // Zero must be visually empty; a literal LP zero would re-enable MATCH_CONSTRAINT.
        owner.configure(values(true,0f));equal(1,headphones.getLayoutParams().width);equal(1,space.getLayoutParams().width);
        equal(0f,headphones.getAlpha());equal(0f,space.getAlpha());
        com.oplus.deviceplugin.sdk.ui.view.progressbar.b incoming=new com.oplus.deviceplugin.sdk.ui.view.progressbar.b(card.p);
        com.oplus.deviceplugin.sdk.ui.view.progressbar.d outgoing=new com.oplus.deviceplugin.sdk.ui.view.progressbar.d(card.p);
        for(int frame=0;frame<=20;frame++){incoming.invoke(frame/20f);equal(0f,headphones.getAlpha());outgoing.invoke(1-frame/20f);equal(0f,headphones.getAlpha());}
        equal(ring,card.p.c);equal(charging,card.p.d);
        headphones.setAlpha(.6f);space.setAlpha(0f);equal(0f,headphones.getAlpha());
        Bundle safe=values(true,0f);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);owner.configure(safe);
        equal(49,headphones.getLayoutParams().width);equal(-2,headphones.getLayoutParams().height);
        equal(.6f,headphones.getAlpha());equal(0f,space.getAlpha());
        safe.putBoolean(StatusBarSettings.SAFE_MODE,false);owner.configure(safe);equal(0f,headphones.getAlpha());
        owner.configure(values(true,.01f));headphones.setAlpha(.7f);equal(0f,headphones.getAlpha());
        owner.configure(values(true,32f));equal(.7f,headphones.getAlpha());equal(64,headphones.getLayoutParams().width);
        // A new exact glyph retires only the old one and starts with its own native pair.
        DeviceGlyph replacement=glyph(owner,card.p);replacement.getLayoutParams().width=46;card.p.f=replacement;
        owner.onDeviceUpdate(card);equal(49,headphones.getLayoutParams().width);equal(64,replacement.getLayoutParams().width);
        equal(0,headphones.attachListeners.size());equal(1,replacement.attachListeners.size());
        // Reparenting out of a square card releases ownership before a native layout write.
        replacement.parent=root;replacement.setLayoutParams(replacement.getLayoutParams());equal(46,replacement.getLayoutParams().width);
        equal(0,replacement.attachListeners.size());equal(0,card.layoutListeners.size());
        replacement.parent=card.p;owner.onDeviceUpdate(card);equal(64,replacement.getLayoutParams().width);
        card.attached=false;
        for(android.view.View.OnAttachStateChangeListener listener:new java.util.ArrayList<>(card.attachListeners))listener.onViewDetachedFromWindow(card);
        equal(46,replacement.getLayoutParams().width);equal(0,card.attachListeners.size());equal(0,replacement.attachListeners.size());
        card.attached=true;owner.onDeviceUpdate(card);equal(64,replacement.getLayoutParams().width);
        // The composite empty/rectangular entry and non-device ImageViews remain native.
        com.oplus.deviceplugin.sdk.ui.view.separatecardview.NoDeviceEntranceCardView empty=
                new com.oplus.deviceplugin.sdk.ui.view.separatecardview.NoDeviceEntranceCardView();empty.parent=root;
        owner.onDeviceUpdate(empty);equal(0,empty.layoutListeners.size());
        owner.onDeviceUpdate(new com.oplus.deviceplugin.sdk.ui.view.separatecardview.RectangleDeviceCardView());
        DeviceGlyph unrelated=glyph(owner,root);unrelated.setAlpha(.8f);unrelated.setLayoutParams(new GlyphParams(47,48));
        equal(47,unrelated.getLayoutParams().width);equal(.8f,unrelated.getAlpha());
        // The ring holder can resize independently of the unchanged outer card.
        card.p.width=46;card.p.height=61;card.p.dispatchLayoutChange(46,61,124,80);
        equal(46,replacement.getLayoutParams().width);equal(46,replacement.getLayoutParams().height);
        equal(ring,card.p.c);equal(124,card.getWidth());equal(80,card.getHeight());
        card.p.width=card.p.height=0;card.p.dispatchLayoutChange(0,0,46,61);
        equal(46,replacement.getLayoutParams().width);equal(56,replacement.getLayoutParams().height);
        card.p.width=40;card.p.height=61;card.p.dispatchLayoutChange(40,61,0,0);
        equal(40,replacement.getLayoutParams().width);equal(40,replacement.getLayoutParams().height);
        replacement.getResources().getDisplayMetrics().density=Float.NaN;owner.onDeviceUpdate(card);
        equal(46,replacement.getLayoutParams().width);equal(56,replacement.getLayoutParams().height);
        replacement.getResources().getDisplayMetrics().density=2f;owner.onDeviceUpdate(card);equal(40,replacement.getLayoutParams().width);
        // Same-card reparenting also retires the old physical holder and registers the new one.
        replacement.parent=card.body;owner.onDeviceUpdate(card);equal(64,replacement.getLayoutParams().width);
        equal(0,card.p.layoutListeners.size());equal(1,card.body.layoutListeners.size());
        card.bodyWidth=card.bodyHeight=0;card.body.dispatchLayoutChange(0,0,124,80);
        equal(46,replacement.getLayoutParams().width);equal(56,replacement.getLayoutParams().height);
        card.bodyWidth=124;card.bodyHeight=80;card.body.dispatchLayoutChange(124,80,0,0);
        equal(64,replacement.getLayoutParams().width);
        // Release must restore both square glyphs and remove their listeners, not clear only maps.
        owner.releaseRuntime();equal(46,replacement.getLayoutParams().width);equal(56,space.getLayoutParams().width);
        equal(0,card.layoutListeners.size());equal(0,entrance.layoutListeners.size());equal(0,space.attachListeners.size());
        equal(0,card.body.layoutListeners.size());equal(0,entrance.body.layoutListeners.size());
    }
    private static void orphanDevices() {
        // Simulate weak-card collection without relying on nondeterministic desktop GC.
        for(int mode=0;mode<3;mode++) {
            QsTileIconSize owner=new QsTileIconSize();
            com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareDeviceCardView card=
                    new com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareDeviceCardView();
            DeviceGlyph glyph=glyph(owner,card.p);card.p.f=glyph;owner.onDeviceUpdate(card);
            owner.configure(values(true,0f));glyph.setAlpha(.45f);equal(1,glyph.getLayoutParams().width);equal(0f,glyph.getAlpha());
            try {
                java.lang.reflect.Field devices=QsTileIconSize.class.getDeclaredField("devices");devices.setAccessible(true);
                Object helper=devices.get(owner);
                java.lang.reflect.Field cards=QsDeviceIconSize.class.getDeclaredField("cards");cards.setAccessible(true);
                java.util.Map<?,?> registry=(java.util.Map<?,?>)cards.get(helper);Object state=registry.get(card);
                java.lang.reflect.Field reference=state.getClass().getDeclaredField("card");reference.setAccessible(true);
                ((java.lang.ref.WeakReference<?>)reference.get(state)).clear();registry.clear();
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
            glyph.parent=new ViewGroup(new Context());
            if(mode==0)owner.configure(values(false,0f));
            else if(mode==1){Bundle safe=values(true,0f);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);owner.configure(safe);}
            else owner.releaseRuntime();
            equal(56,glyph.getLayoutParams().width);equal(56,glyph.getLayoutParams().height);equal(.45f,glyph.getAlpha());
            equal(0,glyph.attachListeners.size());equal(0,card.p.layoutListeners.size());
            glyph.setLayoutParams(new GlyphParams(37,38));glyph.setAlpha(.8f);
            equal(37,glyph.getLayoutParams().width);equal(.8f,glyph.getAlpha());
        }
    }
    private static void invalidCopiedLayout() {
        for(int mode=0;mode<2;mode++) {
            QsTileIconSize owner=new QsTileIconSize();
            com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareDeviceCardView card=
                    new com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareDeviceCardView();
            DeviceGlyph glyph=glyph(owner,card.p);card.p.f=glyph;owner.onDeviceUpdate(card);
            owner.configure(values(true,16f));equal(32,glyph.getLayoutParams().width);
            GlyphParams copied=new GlyphParams(mode==0?32:41,32);copied.margin=29;copied.anchor=44;
            glyph.parent=new ViewGroup(new Context());glyph.setLayoutParams(copied);
            equal(mode==0?56:41,copied.width);equal(56,copied.height);equal(29,copied.margin);equal(44,copied.anchor);
            equal(0,glyph.attachListeners.size());equal(0,card.layoutListeners.size());equal(0,card.p.layoutListeners.size());
            owner.configure(values(false,16f));equal(mode==0?56:41,glyph.getLayoutParams().width);equal(56,glyph.getLayoutParams().height);
        }
    }
}
