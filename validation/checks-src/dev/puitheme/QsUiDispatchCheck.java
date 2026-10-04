// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.RuntimeShader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView;
import com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;
import com.oplus.posteffect.drawable.BlendDrawable;
import java.util.Map;

/** SysUiTileBg registration must neither touch Views nor enqueue one refresh per frame. */
public final class QsUiDispatchCheck {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("QS UI dispatch "+checks);}
    private static final class StrictTile extends OplusQSResizeableTileView {
        int nativeReads;
        @Override public Object getTileState(){
            nativeReads++;
            // Recording reads the native immutable state; registration is marshalled.
            return super.getTileState();
        }
        @Override public void invalidate(){
            if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("off-main invalidate");
            super.invalidate();
        }
    }
    private static final class StrictBackground extends View {
        StrictBackground(){super(null);}
        @Override public void invalidate(){
            if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("off-main background invalidate");
            super.invalidate();
        }
    }
    public static void main(String[] args) throws Throwable {
        Handler.postedForCheck.clear();QsTileAppearance helper=new QsTileAppearance();StrictTile tile=new StrictTile();
        StrictBackground background=new StrictBackground();tile.bgView=background;
        QsTileAppearanceCheck.State state=new QsTileAppearanceCheck.State(1,"rotation");tile.state=state;
        MixColorTileDrawable fill=new MixColorTileDrawable();fill.setBounds(0,0,100,100);tile.bg=fill;
        Looper.setCurrentForCheck(null);
        for(int i=0;i<1000;i++){helper.refreshTile(tile);helper.onTileState(tile,state);}
        check(Handler.postedForCheck.size()==1);check(tile.nativeReads==0);check(background.invalidations==0);
        state.state=2;Looper.resetCurrentForCheck();Handler.drainForCheck();
        check(((Map<?,?>)QsTileAppearance.field(helper,"tiles")).get(tile).equals(2));check(tile.nativeReads==1);
        for(int i=0;i<1000;i++)helper.refreshTile(tile);
        check(background.invalidations==0);check(Handler.postedForCheck.isEmpty());
        // Detach overrides pending updates; a later update observes current state.
        Looper.setCurrentForCheck(null);helper.refreshTile(tile);helper.detach(tile);
        check(Handler.postedForCheck.size()==1);Looper.resetCurrentForCheck();Handler.drainForCheck();
        check(!((Map<?,?>)QsTileAppearance.field(helper,"tiles")).containsKey(tile));
        Looper.setCurrentForCheck(null);helper.detach(tile);helper.refreshTile(tile);
        check(Handler.postedForCheck.size()==1);Looper.resetCurrentForCheck();Handler.drainForCheck();
        check(((Map<?,?>)QsTileAppearance.field(helper,"tiles")).get(tile).equals(2));
        tile.attached=false;Looper.setCurrentForCheck(null);helper.refreshTile(tile);Looper.resetCurrentForCheck();Handler.drainForCheck();
        check(!((Map<?,?>)QsTileAppearance.field(helper,"tiles")).containsKey(tile));tile.attached=true;
        // A first glass engine recording on the native background worker marks
        // the engine dirty and requests a UI invalidation, without calling it.
        Bundle values=new Bundle();values.putBoolean(QsTileAppearance.MASTER,true);helper.configure(values);
        AutoBlurDrawable blur=new AutoBlurDrawable();fill.child=blur;helper.refreshTile(tile);Canvas canvas=new Canvas();
        int immediate=tile.invalidations;Looper.setCurrentForCheck(null);
        helper.drawTile(fill,canvas,target->helper.drawBlur(blur,target,blur::draw));
        check(tile.invalidations==immediate);check(tile.postedInvalidations==1);check(Handler.postedForCheck.size()==1);
        BlendDrawable engine=blur.viewBlurProxy.actual.blurDrawable;check(engine.contentDirty);
        Looper.resetCurrentForCheck();Handler.drainForCheck();
        // The same native path also applies to optical material priming.
        C17HighlightRemoval highlights=new C17HighlightRemoval();Bundle flat=new Bundle();flat.putBoolean(C17HighlightRemoval.ENABLED,true);highlights.configure(flat);
        QsTileAppearance.setField(engine,"drawableShader",new C17AcrylicMaterialCheck.NativeMaterial());
        try(C17HighlightRemoval.SurfaceScope owner=highlights.beginSurface(tile)){highlights.bindDrawable(engine);}
        engine.drawableShader.shader=new RuntimeShader("native replaced on SysUiTileBg");
        RuntimeShader nativeShader=engine.drawableShader.shader;immediate=tile.invalidations;int posted=tile.postedInvalidations;
        Looper.setCurrentForCheck(null);
        try(C17HighlightRemoval.SurfaceScope owner=highlights.beginSurface(tile)){highlights.bindDrawable(engine);}
        try(C17HighlightRemoval.RecordScope record=highlights.beginRecord(engine)){
            highlights.drawContent(engine,canvas,target->{engine.onDrawContent(target);return null;});
        }
        check(tile.invalidations==immediate);check(tile.postedInvalidations==posted+1);check(engine.drawableShader.shader==nativeShader);
        Looper.resetCurrentForCheck();highlights.release();helper.detach(tile);Handler.drainForCheck();
        check(((Map<?,?>)QsTileAppearance.field(helper,"pendingUiViews")).isEmpty());
        System.out.println("QS UI dispatch checks passed: "+checks);
    }
}
