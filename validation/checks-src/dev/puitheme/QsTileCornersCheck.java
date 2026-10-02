package dev.puitheme;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableThreeStageView;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXTwo;
import com.oplus.systemui.qs.base.res.drawable.TileTransitionDrawable;
import com.oplus.systemui.qs.base.res.util.QSConstant;
import com.oplusos.systemui.common.outline.CornerOutlineProvider;

/** Models the verified ColorOS 17 per-tile drawable and shared native outline topology. */
public final class QsTileCornersCheck {
    private static int checks;
    private static final class Body extends View {
        int width=240,height=120;
        Body(){super(new Context());getResources().getDisplayMetrics().density=2f;}
        @Override public int getWidth(){return width;}
        @Override public int getHeight(){return height;}
    }
    private static final class Tile extends OplusQSResizeableTileViewTwoXOne {
        final Body body=new Body();
        Drawable transition;
        Tile(CornerOutlineProvider nativeProvider){transition=new TileTransitionDrawable(nativeProvider);bgView=body;}
        public Drawable getTransitionDrawable(){return transition;}
        TileTransitionDrawable drawable(){return (TileTransitionDrawable)transition;}
    }
    private static final class Large extends OplusQSResizeableTileViewTwoXTwo {
        final TileTransitionDrawable transition;
        Large(CornerOutlineProvider nativeProvider){transition=new TileTransitionDrawable(nativeProvider);}
        public Drawable getTransitionDrawable(){return transition;}
    }
    private static Bundle settings(boolean enabled,float dp){
        Bundle b=new Bundle();b.putBoolean(QsTileCorners.MASTER,enabled);b.putFloat(QsTileCorners.RADIUS,dp);return b;
    }
    private static void equal(Object want,Object actual){checks++;if(!java.util.Objects.equals(want,actual))throw new AssertionError("expected "+want+", actual "+actual);}
    private static void near(float want,Object actual){checks++;if(!(actual instanceof Number)||Math.abs(want-((Number)actual).floatValue())>.0001f)throw new AssertionError("expected "+want+", actual "+actual);}
    private static void nativeState(Tile tile,CornerOutlineProvider provider){
        equal(provider,tile.drawable().getPathProvider());equal(provider,tile.drawable().childProvider);
    }
    private static void ownership(){
        QSConstant.reset();QsTileCorners corners=new QsTileCorners();
        CornerOutlineProvider nativeProvider=new CornerOutlineProvider(56f,0.82f);
        Tile first=new Tile(nativeProvider),second=new Tile(nativeProvider);Large large=new Large(nativeProvider);
        corners.onNativeUpdate(first);corners.onNativeUpdate(second);corners.onNativeUpdate(large);
        nativeState(first,nativeProvider);equal(0,QSConstant.calls);equal(false,QsTileCorners.BOOLEANS.get(QsTileCorners.MASTER));
        near(24f,QsTileCorners.NUMBERS.get(QsTileCorners.RADIUS));equal(false,StatusBarSettings.BOOLEAN_DEFAULTS.get(QsTileCorners.MASTER));
        near(24f,StatusBarSettings.NUMERIC_DEFAULTS.get(QsTileCorners.RADIUS));
        Object shader=first.drawable().shader,glass=first.drawable().glass;
        Bundle active=settings(true,24f);corners.configure(active);
        near(48f,corners.cornerRadius(first,56f));near(0.6f,corners.cornerWeight(first,0.82f));
        equal(first.drawable().getPathProvider(),first.drawable().childProvider);
        equal(true,first.drawable().getPathProvider()!=second.drawable().getPathProvider());
        equal(true,nativeProvider!=large.transition.getPathProvider());equal(1,large.transition.setters);
        near(56f,nativeProvider.radius);near(0.82f,nativeProvider.weight);
        equal(173,first.drawable().alpha);equal(0xff123456,first.drawable().color);equal(shader,first.drawable().shader);equal(glass,first.drawable().glass);
        int calls=QSConstant.calls,setters=first.drawable().setters,updates=first.drawable().pathUpdates;
        for(int i=0;i<100;i++)corners.onNativeUpdate(first);
        equal(calls,QSConstant.calls);equal(setters,first.drawable().setters);equal(updates,first.drawable().pathUpdates);
        corners.configure(settings(true,48f));near(60f,corners.cornerRadius(first,56f));near(24f*first.getContext().getResources().getDisplayMetrics().density,QSConstant.lastPixels);
        corners.configure(settings(true,0f));near(0f,corners.cornerRadius(first,56f));
        corners.configure(settings(false,24f));nativeState(first,nativeProvider);nativeState(second,nativeProvider);
        equal(nativeProvider,large.transition.getPathProvider());equal(nativeProvider,large.transition.childProvider);
        near(56f,corners.cornerRadius(first,56f));near(0.82f,corners.cornerWeight(first,0.82f));
        equal(true,active.get(QsTileCorners.MASTER));near(24f,active.get(QsTileCorners.RADIUS));
        corners.configure(active);corners.detach(first);nativeState(first,nativeProvider);
        corners.configure(settings(true,12f));nativeState(first,nativeProvider);near(24f,corners.cornerRadius(second,56f));
        corners.onNativeUpdate(first);near(24f,corners.cornerRadius(first,56f));
        first.attached=false;corners.onNativeUpdate(first);nativeState(first,nativeProvider);
        first.attached=true;corners.onNativeUpdate(first);near(24f,corners.cornerRadius(first,56f));
        corners.detach(first);corners.detach(second);
    }
    private static void generationAndSafety(){
        QSConstant.reset();QsTileCorners corners=new QsTileCorners();CornerOutlineProvider old=new CornerOutlineProvider(50f,0.8f);
        Tile tile=new Tile(old);corners.configure(settings(true,10f));corners.onNativeUpdate(tile);
        CornerOutlineProvider themed=new CornerOutlineProvider(54f,0.9f);
        tile.drawable().setPathProvider(themed);tile.drawable().invalidatePath();corners.onNativeUpdate(tile);near(20f,corners.cornerRadius(tile,54f));
        corners.configure(settings(false,10f));nativeState(tile,themed);
        corners.configure(settings(true,10f));TileTransitionDrawable before=tile.drawable();
        CornerOutlineProvider replacementNative=new CornerOutlineProvider(58f,0.7f);
        tile.transition=new TileTransitionDrawable(replacementNative);corners.onNativeUpdate(tile);
        equal(themed,before.getPathProvider());equal(themed,before.childProvider);near(20f,corners.cornerRadius(tile,58f));
        Bundle saved=settings(true,18f);saved.putBoolean(StatusBarSettings.SAFE_MODE,true);
        Bundle safeRuntime=SafetyMode.runtimeSettings(saved);equal(false,safeRuntime.get(QsTileCorners.MASTER));equal(true,saved.get(QsTileCorners.MASTER));
        corners.configure(saved);nativeState(tile,replacementNative);near(58f,corners.cornerRadius(tile,58f));
        Tile bornSafe=new Tile(old);corners.onNativeUpdate(bornSafe);nativeState(bornSafe,old);
        saved.putBoolean(StatusBarSettings.SAFE_MODE,false);corners.configure(saved);near(36f,corners.cornerRadius(tile,58f));near(36f,corners.cornerRadius(bornSafe,50f));
        QSConstant.weight=null;corners.configure(settings(true,19f));equal(null,corners.cornerWeight(tile,0.7f));
        CornerOutlineProvider foreign=new CornerOutlineProvider(33f,null);tile.drawable().setPathProvider(foreign);tile.drawable().invalidatePath();
        near(33f,corners.cornerRadius(tile,33f));corners.configure(settings(false,19f));nativeState(tile,foreign);
        corners.detach(bornSafe);nativeState(bornSafe,old);
    }
    private static void failures(){
        for(int failure=0;failure<5;failure++){
            QSConstant.reset();QsTileCorners corners=new QsTileCorners();CornerOutlineProvider base=new CornerOutlineProvider(50f,0.8f);Tile tile=new Tile(base);
            if(failure==0)QSConstant.fail=true;
            if(failure==1)QSConstant.empty=true;
            if(failure==2)QSConstant.invalidRadius=true;
            if(failure==3)tile.drawable().failSetAfter=1;
            if(failure==4)tile.drawable().failPath=1;
            corners.configure(settings(true,10f));corners.onNativeUpdate(tile);nativeState(tile,base);near(50f,corners.cornerRadius(tile,50f));
            corners.configure(settings(false,10f));nativeState(tile,base);
        }
        QSConstant.reset();QsTileCorners corners=new QsTileCorners();CornerOutlineProvider base=new CornerOutlineProvider(50f,0.8f);Tile tile=new Tile(base);
        corners.configure(settings(true,10f));corners.onNativeUpdate(tile);near(20f,corners.cornerRadius(tile,50f));
        tile.drawable().failPath=1;corners.configure(settings(false,10f));equal(base,tile.drawable().getPathProvider());near(50f,corners.cornerRadius(tile,50f));
        corners.onNativeUpdate(tile);nativeState(tile,base);
        corners.configure(settings(true,10f));tile.drawable().failPath=2;corners.detach(tile);
        corners.onNativeUpdate(tile);equal(base,tile.drawable().getPathProvider());
        corners.onNativeUpdate(tile);near(20f,corners.cornerRadius(tile,50f));corners.detach(tile);nativeState(tile,base);
        corners.configure(settings(true,10f));corners.onNativeUpdate(tile);TileTransitionDrawable retained=tile.drawable();
        tile.transition=null;corners.configure(settings(false,10f));equal(base,retained.getPathProvider());equal(base,retained.childProvider);
    }
    private static void policy(){
        near(24f,QsTileCorners.radiusDp(null));near(24f,QsTileCorners.radiusDp("broken"));near(24f,QsTileCorners.radiusDp(Float.NaN));near(24f,QsTileCorners.radiusDp(Float.POSITIVE_INFINITY));
        near(0f,QsTileCorners.radiusDp(-10));near(30f,QsTileCorners.radiusDp(100));near(17.5f,QsTileCorners.radiusDp("17.5"));
        for(int width:new int[]{20,120,800})for(int height:new int[]{20,120,800})for(float density:new float[]{0.75f,1f,2f,4f})for(float dp:new float[]{0,1,24,48,80,100}){
            float px=QsTileCorners.radiusPixels(dp,density,width,height);
            equal(true,px>=0&&px<=Math.min(width,height)/2f);near(Math.min(QsTileCorners.radiusDp(dp)*density,Math.min(width,height)/2f),px);
        }
        for(float density:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})near(-1f,QsTileCorners.radiusPixels(24,density,240,120));
        near(-1f,QsTileCorners.radiusPixels(24,2,0,120));near(-1f,QsTileCorners.radiusPixels(24,2,240,0));
        QSConstant.reset();QsTileCorners corners=new QsTileCorners();CornerOutlineProvider base=new CornerOutlineProvider(50f,0.8f);Tile tile=new Tile(base);
        Bundle wrong=settings(true,10f);wrong.putString(QsTileCorners.MASTER,"true");corners.configure(wrong);corners.onNativeUpdate(tile);nativeState(tile,base);
        tile.body.getResources().getDisplayMetrics().density=Float.NaN;corners.configure(settings(true,10f));nativeState(tile,base);equal(0,QSConstant.calls);
        tile.body.getResources().getDisplayMetrics().density=2f;tile.body.width=0;tile.body.height=0;tile.drawable().setBounds(0,0,0,0);corners.onNativeUpdate(tile);nativeState(tile,base);
        tile.drawable().setBounds(0,0,100,40);corners.onNativeUpdate(tile);near(20f,corners.cornerRadius(tile,50f));
        corners.configure(null);near(50f,corners.cornerRadius(tile,50f));
        corners.configure(settings(false,24f));nativeState(tile,base);
    }
    private static void allSizes(){
        QSConstant.reset();QsTileCorners corners=new QsTileCorners();corners.configure(settings(true,24f));
        CornerOutlineProvider shared=new CornerOutlineProvider(56f,0.8f);
        OplusQSResizeableTileView[] tiles={new OplusQSResizeableTileView(),new OplusQSResizeableTileViewOneXOne(),
                new OplusQSResizeableTileViewTwoXOne(),new OplusQSResizeableTileViewTwoXTwo(),new OplusQSResizeableThreeStageView()};
        for(OplusQSResizeableTileView tile:tiles){
            Body body=new Body();body.width=320;body.height=320;tile.bgView=body;tile.transition=new TileTransitionDrawable(shared);
            equal(true,QsTileCorners.isTile(tile));corners.onNativeUpdate(tile);near(48f,corners.cornerRadius(tile,56f));
            equal(true,shared!=((TileTransitionDrawable)tile.transition).getPathProvider());
        }
        corners.configure(settings(true,80f));
        for(OplusQSResizeableTileView tile:tiles)near(60f,corners.cornerRadius(tile,56f));
        Bundle safe=settings(true,80f);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);corners.configure(safe);
        for(OplusQSResizeableTileView tile:tiles){equal(shared,((TileTransitionDrawable)tile.transition).getPathProvider());near(56f,corners.cornerRadius(tile,56f));}
        safe.putBoolean(StatusBarSettings.SAFE_MODE,false);corners.configure(safe);
        for(OplusQSResizeableTileView tile:tiles){near(60f,corners.cornerRadius(tile,56f));corners.detach(tile);equal(shared,((TileTransitionDrawable)tile.transition).getPathProvider());}
        equal(false,QsTileCorners.isTile(new View(new Context())));
    }
    private static String document(String body){return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{"+body+"}}";}
    private static void migration() throws Exception {
        java.util.Map<String,Object> old=new java.util.HashMap<>();old.put(QsTileCorners.MASTER,false);old.put(QsTileCorners.LEGACY_RADIUS,37f);
        equal(false,StatusBarSettings.bool(old,QsTileCorners.MASTER));near(30f,StatusBarSettings.settingNumber(old,QsTileCorners.RADIUS,24f));
        Bundle snapshot=SettingsSnapshot.fromPreferences(old);equal(false,snapshot.get(QsTileCorners.MASTER));near(30f,snapshot.get(QsTileCorners.RADIUS));equal(false,old.get(QsTileCorners.MASTER));
        ActivationGuardPreferencesCheck.MemoryPreferences prefs=new ActivationGuardPreferencesCheck.MemoryPreferences();prefs.values.putAll(old);prefs.values.put("clock_scale",123f);
        QsTileCorners.migrate(prefs);equal(false,prefs.values.get(QsTileCorners.MASTER));near(30f,prefs.values.get(QsTileCorners.RADIUS));equal(true,prefs.values.get(QsTileCorners.MIGRATED));near(123f,prefs.values.get("clock_scale"));
        int writes=prefs.writes;prefs.edit().putBoolean(QsTileCorners.MASTER,false).apply();QsTileCorners.migrate(prefs);equal(writes+1,prefs.writes);equal(false,StatusBarSettings.bool(prefs.values,QsTileCorners.MASTER));
        prefs.edit().remove(QsTileCorners.RADIUS).apply();QsTileCorners.migrate(prefs);equal(false,StatusBarSettings.bool(prefs.values,QsTileCorners.MASTER));near(30f,StatusBarSettings.settingNumber(prefs.values,QsTileCorners.RADIUS,24f));
        String exported=ConfigTransfer.exportJson(old);equal(false,exported.contains(QsTileCorners.LEGACY_RADIUS));equal(false,exported.contains(QsTileCorners.MIGRATED));
        java.util.Map<String,Object> imported=ConfigTransfer.prepare(document("\""+QsTileCorners.MASTER+"\":false,\""+QsTileCorners.LEGACY_RADIUS+"\":27"),true).values();
        equal(false,imported.get(QsTileCorners.MASTER));near(27f,imported.get(QsTileCorners.RADIUS));equal(false,imported.containsKey(QsTileCorners.LEGACY_RADIUS));
        imported=ConfigTransfer.prepare(document("\""+QsTileCorners.LEGACY_RADIUS+"\":37,\""+QsTileCorners.RADIUS+"\":30,\""+QsTileCorners.MASTER+"\":false"),true).values();
        equal(false,imported.get(QsTileCorners.MASTER));near(30f,imported.get(QsTileCorners.RADIUS));
        for(float bad:new float[]{-1,30.5f,80,100})try{ConfigTransfer.prepare(document("\""+QsTileCorners.RADIUS+"\":"+bad),true);throw new AssertionError("out-of-range radius accepted");}catch(java.io.IOException expected){checks++;}
        ActivationGuardPreferencesCheck.MemoryPreferences fresh=new ActivationGuardPreferencesCheck.MemoryPreferences();QsTileCorners.migrate(fresh);equal(false,fresh.values.containsKey(QsTileCorners.MASTER));equal(false,StatusBarSettings.bool(fresh.values,QsTileCorners.MASTER));equal(false,fresh.values.containsKey(QsTileCorners.RADIUS));near(24f,StatusBarSettings.settingNumber(fresh.values,QsTileCorners.RADIUS,24f));
        ActivationGuardPreferencesCheck.MemoryPreferences modern=new ActivationGuardPreferencesCheck.MemoryPreferences();modern.values.put(QsTileCorners.RADIUS,80f);modern.values.put(QsTileCorners.MASTER,false);QsTileCorners.migrate(modern);equal(false,modern.values.get(QsTileCorners.MASTER));near(80f,modern.values.get(QsTileCorners.RADIUS));
    }
    private static void nativeGeometry() {
        QSConstant.reset();QSConstant.mappedScale=1.5f;
        QsTileCorners corners=new QsTileCorners();CornerOutlineProvider nativeProvider=new CornerOutlineProvider(56f,.8f);
        Tile tile=new Tile(nativeProvider);corners.configure(settings(true,45.5f));corners.onNativeUpdate(tile);
        near(60f,corners.cornerRadius(tile,56f));near(.6f,corners.cornerWeight(tile,.8f));
        CornerOutlineProvider custom=tile.drawable().getPathProvider();
        CornerOutlineProvider deform=new CornerOutlineProvider(28f,.4f);tile.drawable().setBlockPathProvider(deform);
        corners.onNativeUpdate(tile);equal(deform,tile.drawable().getPathProvider());near(28f,corners.cornerRadius(tile,28f));
        corners.configure(settings(false,45.5f));equal(deform,tile.drawable().getPathProvider());
        tile.drawable().setBlockPathProvider(null);nativeState(tile,nativeProvider);
        com.oplus.systemui.plugins.qs.customize.view.animation.deform.tile.fixed.TileDeformOutlineProvider fixed=
                new com.oplus.systemui.plugins.qs.customize.view.animation.deform.tile.fixed.TileDeformOutlineProvider(56f,.8f);
        fixed.setCurrentSpanSize(1);tile.drawable().setBlockPathProvider(fixed);
        corners.configure(settings(true,45.5f));
        Object ownedBlock=tile.drawable().getPathProvider();equal(true,ownedBlock!=fixed);
        near(60f,corners.cornerRadius(tile,56f));near(56f,fixed.radius);equal(1,((com.oplus.systemui.plugins.qs.customize.view.animation.deform.tile.fixed.TileDeformOutlineProvider)ownedBlock).getCurrentSpanSize());
        corners.configure(settings(false,45.5f));equal(fixed,tile.drawable().getPathProvider());near(56f,fixed.radius);
        tile.drawable().setBlockPathProvider(null);nativeState(tile,nativeProvider);
        corners.configure(settings(true,10f));near(20f,corners.cornerRadius(tile,56f));
        equal(1,tile.layoutListeners.size());equal(1,tile.body.layoutListeners.size());
        tile.body.width=160;tile.body.height=20;tile.body.dispatchLayoutChange(160,20,240,120);
        near(10f,corners.cornerRadius(tile,56f));
        corners.detach(tile);nativeState(tile,nativeProvider);equal(0,tile.layoutListeners.size());equal(0,tile.body.layoutListeners.size());
        corners.configure(new Bundle());corners.onNativeUpdate(tile);nativeState(tile,nativeProvider);
        corners.configure(settings(true,0f));near(0f,corners.cornerRadius(tile,56f));
        near(56f,nativeProvider.radius);equal(false,custom==nativeProvider);
    }
    public static void main(String[] args) throws Exception {ownership();generationAndSafety();failures();policy();allSizes();migration();nativeGeometry();System.out.println("QsTileCornersCheck passed: "+checks);}
}
