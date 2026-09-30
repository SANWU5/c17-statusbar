package dev.puitheme;

import android.graphics.Canvas;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RecordingCanvas;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Temporary edge effects for actual horizontal QS paging, preserving native tile drawing. */
public final class TilePageEffects {
    public interface DrawAction { void draw(Canvas canvas) throws Throwable; }
    private static final int MAX_NODE_SIZE = 8192;
    private static final long MAX_NODE_PIXELS = 16777216L;
    private static final float MAX_BLUR_PIXELS = 128f;
    private final Map<View, Entry> entries = new WeakHashMap<>();
    private boolean enabled = true, fadeEnabled = true, blurEnabled;
    private boolean portraitEnabled=true,landscapeEnabled=true;
    private boolean diagnosticsEnabled;
    private float leftRangeDp = 24f,rightRangeDp = 24f,radiusDp = 8f,strength = 1f;
    private float leftOffsetDp,rightOffsetDp,offsetYDp,regionHeightDp,verticalFeatherDp=32f;

    private static final class Entry {
        int scrollState;
        float offset;
        boolean blurFailed,loggedBlur,loggedFade,loggedOverflow;
        boolean loggedError,loggedNativeError,loggedClip;
        boolean nativeSynced;
        Class<?> viewType,managerType;
        Method viewManager,viewState,managerPages,managerHorizontal,managerOffset;
        Object renderer;
        Shader mask;
        MaskProfile profile;
        Shader fence;
        FenceProfile fenceProfile;
        final Paint fencePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint erase = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint keep = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint leftGradient = new Paint(Paint.ANTI_ALIAS_FLAG),rightGradient = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint verticalGradient = new Paint(Paint.ANTI_ALIAS_FLAG),eraseRestore = new Paint(Paint.ANTI_ALIAS_FLAG);
        Entry() {
            erase.setColor(Color.WHITE);keep.setColor(Color.WHITE);
            leftGradient.setColor(Color.WHITE);rightGradient.setColor(Color.WHITE);verticalGradient.setColor(Color.WHITE);
            erase.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            keep.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            rightGradient.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
            verticalGradient.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            eraseRestore.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            fencePaint.setColor(Color.WHITE);fencePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }
        void mask(MaskProfile next) {
            if(mask!=null&&profile!=null&&profile.same(next))return;
            profile=next;Shader[] parts=next.parts();
            mask=new ComposeShader(new ComposeShader(parts[0],parts[1],PorterDuff.Mode.SCREEN),parts[2],PorterDuff.Mode.DST_IN);
            leftGradient.setShader(parts[0]);rightGradient.setShader(parts[1]);verticalGradient.setShader(parts[2]);
            erase.setShader(mask);keep.setShader(mask);
        }
        void fence(Geometry geometry,float density,float transition) {
            FenceProfile next=new FenceProfile(geometry,density,transition);
            if(fence!=null&&fenceProfile!=null&&fenceProfile.same(next))return;
            fenceProfile=next;fence=next.shader();fencePaint.setShader(fence);
        }
    }

    /** A narrow horizontal safety edge contains sampling without changing top/bottom opacity. */
    private static final class FenceProfile {
        final float left,right,leftWidth,rightWidth;
        FenceProfile(Geometry geometry,float density,float transition) {
            left=geometry.left;right=geometry.right;
            float halfWidth=(right-left)/2f;
            float requested=2f+(Math.max(2f,NumericPolicy.pixels(4f,density))-2f)*transition;
            leftWidth=Math.min(halfWidth,requested);rightWidth=Math.min(halfWidth,requested);
        }
        boolean same(FenceProfile p) {
            return left==p.left&&right==p.right&&leftWidth==p.leftWidth&&rightWidth==p.rightWidth;
        }
        Shader shader() {
            return window(left,right,leftWidth,rightWidth);
        }
        private static Shader window(float start,float end,float leading,float trailing) {
            float extent=end-start;
            float a=leading/extent,b=trailing/extent;
            float[] stops={0,a*.25f,a*.5f,a*.75f,a,1-b,1-b*.75f,1-b*.5f,1-b*.25f,1};
            int[] colors={0x00ffffff,0x28ffffff,0x80ffffff,0xd7ffffff,0xffffffff,
                    0xffffffff,0xd7ffffff,0x80ffffff,0x28ffffff,0x00ffffff};
            return new LinearGradient(start,0,end,0,colors,stops,Shader.TileMode.CLAMP);
        }
    }

    /** Left/right transition union multiplied by a continuous top/bottom feather. */
    private static final class MaskProfile {
        final float left,right,leftRange,rightRange,top,bottom,feather;
        MaskProfile(float left,float right,float leftRange,float rightRange,float top,float bottom,float feather) {
            this.left=left;this.right=right;this.leftRange=leftRange;this.rightRange=rightRange;
            this.top=top;this.bottom=bottom;this.feather=Math.min(Math.max(.001f,feather),(bottom-top)/2f);
        }
        boolean same(MaskProfile p) {
            return left==p.left&&right==p.right&&leftRange==p.leftRange&&rightRange==p.rightRange
                    &&top==p.top&&bottom==p.bottom&&feather==p.feather;
        }
        Shader[] parts() {
            Shader l=leftRange<=0f||left+leftRange==left?transparent():ramp(left,left+leftRange,false);
            Shader r=rightRange<=0f||right-rightRange==right?transparent():ramp(right-rightRange,right,true);
            float q=feather/(bottom-top);
            float[] stops={0,q*.25f,q*.5f,q*.75f,q,1-q,1-q*.75f,1-q*.5f,1-q*.25f,1};
            int[] colors={0x00ffffff,0x28ffffff,0x80ffffff,0xd7ffffff,0xffffffff,
                    0xffffffff,0xd7ffffff,0x80ffffff,0x28ffffff,0x00ffffff};
            Shader vertical=new LinearGradient(0,top,0,bottom,colors,stops,Shader.TileMode.CLAMP);
            return new Shader[]{l,r,vertical};
        }
        private static Shader transparent() {
            return new LinearGradient(0,0,1,0,new int[]{0,0},new float[]{0,1},Shader.TileMode.CLAMP);
        }
        private static Shader ramp(float start,float end,boolean increasing) {
            float[] stops={0,.125f,.25f,.375f,.5f,.625f,.75f,.875f,1};int[] colors=new int[stops.length];
            for(int i=0;i<stops.length;i++) {
                float alpha=smooth(stops[i]);if(!increasing)alpha=1f-alpha;
                colors[i]=(Math.round(alpha*255f)<<24)|0xffffff;
            }
            return new LinearGradient(start,0,end,0,colors,stops,Shader.TileMode.CLAMP);
        }
    }

    private static final class Geometry {
        final int width,height,scrollX,scrollY,padX,padY,sourceWidth,sourceHeight;
        final float left,right,top,bottom;
        Geometry(View view,Canvas canvas,float radius) {
            width=view.getWidth();height=view.getHeight();scrollX=view.getScrollX();scrollY=view.getScrollY();
            padX=Math.max(2,(int)Math.ceil(radius*3f+2f));padY=Math.max(200,padX);
            sourceWidth=width+2*padX;sourceHeight=height+2*padY;
            // Incoming Canvas clips only restrict output. They must never relocate the blur/fade bands.
            float l=0f,r=width,t=0f,b=height;
            boolean nativePaddedPager=view.getClass().getName().equals("com.oplus.systemui.qs.base.widget.recyclerview.ParallelCOUIRecyclerView");
            if(view instanceof ViewGroup&&(nativePaddedPager||((ViewGroup)view).getClipToPadding())) {
                l=Math.max(l,view.getPaddingLeft());r=Math.min(r,width-view.getPaddingRight());
                t=Math.max(t,view.getPaddingTop());b=Math.min(b,height-view.getPaddingBottom());
            }
            left=l;right=r;top=t;bottom=b;
        }
        boolean valid() {
            return width>0&&height>0&&sourceWidth>0&&sourceHeight>0&&width<=MAX_NODE_SIZE&&height<=MAX_NODE_SIZE
                    &&sourceWidth<=MAX_NODE_SIZE&&sourceHeight<=MAX_NODE_SIZE
                    &&(long)sourceWidth*sourceHeight<=MAX_NODE_PIXELS&&right-left>1f&&bottom>top;
        }
        float minX(){return -padX;}float maxX(){return width+padX;}
        float minY(){return -padY;}float maxY(){return height+padY;}
    }

    public void configure(Bundle settings) {
        boolean nextDiagnostics=settings.getBoolean("diagnostics_enabled",false);
        boolean rearmDiagnostics=nextDiagnostics&&!diagnosticsEnabled;
        diagnosticsEnabled=nextDiagnostics;
        enabled=settings.getBoolean("tiles_enabled",true);
        portraitEnabled=settings.getBoolean("tiles_portrait_enabled",true);
        landscapeEnabled=settings.getBoolean("tiles_landscape_enabled",true);
        fadeEnabled=settings.getBoolean("tiles_fade_enabled",true);
        blurEnabled=settings.getBoolean("tiles_blur_enabled",false);
        float legacyRange=NumericPolicy.setting("tiles_fade_range",settings.get("tiles_fade_range"),24f);
        leftRangeDp=NumericPolicy.setting("tiles_left_range",settings.get("tiles_left_range"),legacyRange);
        rightRangeDp=NumericPolicy.setting("tiles_right_range",settings.get("tiles_right_range"),legacyRange);
        leftOffsetDp=NumericPolicy.setting("tiles_left_offset_x",settings.get("tiles_left_offset_x"),0f);
        rightOffsetDp=NumericPolicy.setting("tiles_right_offset_x",settings.get("tiles_right_offset_x"),0f);
        offsetYDp=NumericPolicy.setting("tiles_offset_y",settings.get("tiles_offset_y"),0f);
        regionHeightDp=NumericPolicy.setting("tiles_region_height",settings.get("tiles_region_height"),0f);
        verticalFeatherDp=NumericPolicy.setting("tiles_vertical_feather",settings.get("tiles_vertical_feather"),32f);
        radiusDp=NumericPolicy.setting("tiles_blur_radius",settings.get("tiles_blur_radius"),8f);
        strength=Math.min(100f,NumericPolicy.setting("tiles_strength",settings.get("tiles_strength"),100f))/100f;
        for(Map.Entry<View,Entry> item:entries.entrySet()) {
            Entry entry=item.getValue();entry.blurFailed=false;
            if(rearmDiagnostics) {
                entry.loggedBlur=false;entry.loggedFade=false;entry.loggedOverflow=false;
                entry.loggedError=false;entry.loggedNativeError=false;entry.loggedClip=false;
            }
            if(!enabled||!directionEnabled(item.getKey())||!blurEnabled||strength==0f)releaseRenderer(entry);
            item.getKey().invalidate();
        }
    }

    public boolean identifies(View view) {
        if(view==null)return false;
        String type=view.getClass().getName();
        if(type.equals("com.android.systemui.qs.deprecated.PagedTileLayout")
                ||type.equals("com.android.systemui.qs.PagedTileLayout"))return true;
        if(!type.equals("com.oplus.systemui.qs.base.widget.recyclerview.ParallelCOUIRecyclerView"))return false;
        try {
            String name=view.getResources().getResourceEntryName(view.getId());
            return name.equals("other_tiles_container")||name.equals("other_tiles_land_left_container")||name.equals("qs_tiles_container")
                    ||name.equals("qs_pager")||name.equals("qs_paged_tile_layout");
        } catch(RuntimeException unknown) {return false;}
    }

    private Entry entry(View view) {
        Entry entry=entries.get(view);
        if(entry==null){entry=new Entry();entries.put(view,entry);}
        return entry;
    }

    private boolean directionEnabled(View view) {
        int direction=Configuration.ORIENTATION_UNDEFINED;
        try {direction=view.getResources().getConfiguration().orientation;}catch(RuntimeException unavailable) { }
        if(direction==Configuration.ORIENTATION_LANDSCAPE)return landscapeEnabled;
        if(direction==Configuration.ORIENTATION_PORTRAIT)return portraitEnabled;
        View root=view.getRootView();
        if(root==null)root=view;
        return root.getWidth()>root.getHeight()?landscapeEnabled:portraitEnabled;
    }

    public void onScrollState(View view,int state) {
        if(!identifies(view))return;
        Entry entry=entry(view);entry.scrollState=state==1||state==2?state:0;
        if(entry.scrollState==0)entry.offset=0f;
        view.invalidate();
    }

    public void onPageScroll(View view,float offset) {
        if(!identifies(view))return;
        entry(view).offset=fraction(offset);view.invalidate();
    }

    /** Current OPlus manager exposes the view and physical scroll offset as public getters. */
    public void onLayoutManagerScroll(Object manager) {
        if(manager==null)return;
        try {
            Class<?> type=manager.getClass();
            View view=(View)type.getMethod("getRecyclerView").invoke(manager);
            if(!identifies(view))return;
            int pageCount=((Number)type.getMethod("getPageCount").invoke(manager)).intValue();
            if(pageCount<=1||!Boolean.TRUE.equals(type.getMethod("canScrollHorizontally").invoke(manager))) {
                onScrollState(view,0);return;
            }
            int[] offset=(int[])type.getMethod("currScrollOffset").invoke(manager);
            int pageWidth=view.getMeasuredWidth();
            if(offset==null||offset.length<1||pageWidth<=0){onPageScroll(view,0f);return;}
            onPageScroll(view,(float)((Math.abs((double)offset[0])%pageWidth)/pageWidth));
        } catch(Exception unavailable) { }
    }

    /** Calls native dispatch exactly once. Static pages always take the unmodified native path. */
    public void draw(View view,Canvas canvas,DrawAction nativeDraw) throws Throwable {
        if(!identifies(view)){nativeDraw.draw(canvas);return;}
        Entry entry=entry(view);
        if(!enabled||!directionEnabled(view)){releaseRenderer(entry);nativeDraw.draw(canvas);return;}
        syncNative(view,entry);
        float transition=transitionAmount(entry.offset);
        float amount=transition*strength;
        if(!enabled||(!fadeEnabled&&!blurEnabled)||(leftRangeDp<=0f&&rightRangeDp<=0f)||amount<=0f||entry.scrollState==0
                ||!view.isAttachedToWindow()||!view.isShown()) {nativeDraw.draw(canvas);return;}
        if(nativeOverflow(view)) {
            if(!entry.loggedOverflow) {entry.loggedOverflow=true;ModuleDiagnostics.info("tiles","QS tile edge effects preserve native overflow animation");}
            nativeDraw.draw(canvas);return;
        }
        float density=view.getResources().getDisplayMetrics().density;
        float radius=Math.min(MAX_BLUR_PIXELS,NumericPolicy.pixels(radiusDp,density));
        Geometry geometry=null;
        try {
            geometry=new Geometry(view,canvas,blurEnabled?radius:0f);
            if(geometry.valid()) {
                float top=NumericPolicy.pixels((double)geometry.top+NumericPolicy.pixels(offsetYDp,density));
                float height=regionHeightDp==0f?geometry.bottom-geometry.top:NumericPolicy.pixels(regionHeightDp,density);
                float bottom=top+height;
                if(bottom>top) {
                    entry.mask(new MaskProfile(
                            NumericPolicy.pixels((double)geometry.left+NumericPolicy.pixels(leftOffsetDp,density)),
                            NumericPolicy.pixels((double)geometry.right+NumericPolicy.pixels(rightOffsetDp,density)),
                            NumericPolicy.pixels(leftRangeDp,density),NumericPolicy.pixels(rightRangeDp,density),
                            top,bottom,NumericPolicy.pixels(verticalFeatherDp,density)));
                    entry.fence(geometry,density,transition);
                }
                else geometry=null;
            } else geometry=null;
        } catch(RuntimeException unavailable) {recordError(entry,"QS tile edge mask preparation failed",unavailable);geometry=null;}
        if(geometry==null){nativeDraw.draw(canvas);return;}
        entry.erase.setAlpha(Math.round(amount*255f));entry.keep.setAlpha(Math.round(amount*255f));
        if(blurEnabled&&radius>0f&&!entry.blurFailed&&Build.VERSION.SDK_INT>=31&&canvas.isHardwareAccelerated()) {
            Api31.Renderer renderer;
            try {
                if(entry.renderer==null)entry.renderer=new Api31.Renderer();
                renderer=(Api31.Renderer)entry.renderer;renderer.prepare(geometry,radius);
            } catch(Throwable unavailable) {entry.blurFailed=true;recordError(entry,"QS tile edge blur preparation failed",unavailable);drawFade(view,canvas,geometry,entry,nativeDraw);return;}
            if(renderer.draw(view,canvas,geometry,entry,fadeEnabled,nativeDraw))logApplied(entry,true);
        } else drawFade(view,canvas,geometry,entry,nativeDraw);
    }

    /** Read live state even when a final native callback has been inlined. Never invalidate here. */
    private static void syncNative(View view,Entry entry) {
        if(!view.getClass().getName().equals("com.oplus.systemui.qs.base.widget.recyclerview.ParallelCOUIRecyclerView"))return;
        try {
            Class<?> viewType=view.getClass();
            if(entry.viewType!=viewType) {
                entry.viewType=viewType;
                entry.viewManager=viewType.getMethod("getLayoutManager");
                entry.viewState=viewType.getMethod("getScrollState");
            }
            if(entry.viewManager==null||entry.viewState==null)return;
            Object manager=entry.viewManager.invoke(view);
            if(manager==null){entry.scrollState=0;entry.offset=0f;entry.nativeSynced=true;return;}
            Class<?> managerType=manager.getClass();
            if(entry.managerType!=managerType) {
                entry.managerType=managerType;
                entry.managerPages=managerType.getMethod("getPageCount");
                entry.managerHorizontal=managerType.getMethod("canScrollHorizontally");
                entry.managerOffset=managerType.getMethod("currScrollOffset");
            }
            if(entry.managerPages==null||entry.managerHorizontal==null||entry.managerOffset==null)return;
            int pages=((Number)entry.managerPages.invoke(manager)).intValue();
            int width=view.getMeasuredWidth();
            if(pages<=1||width<=0||!Boolean.TRUE.equals(entry.managerHorizontal.invoke(manager))) {
                entry.scrollState=0;entry.offset=0f;entry.nativeSynced=true;return;
            }
            int[] offset=(int[])entry.managerOffset.invoke(manager);
            if(offset==null||offset.length<1){entry.scrollState=0;entry.offset=0f;entry.nativeSynced=true;return;}
            int state=((Number)entry.viewState.invoke(view)).intValue();
            if(state!=1&&state!=2) {
                entry.scrollState=0;entry.offset=0f;entry.nativeSynced=true;return;
            }
            entry.offset=(float)((Math.abs((double)offset[0])%width)/width);
            entry.scrollState=state;
            entry.nativeSynced=true;
        } catch(Exception unavailable) {
            if(!entry.loggedNativeError){entry.loggedNativeError=true;ModuleDiagnostics.error("tiles","QS tile native pager state unavailable",unavailable);}
            if(entry.nativeSynced){entry.scrollState=0;entry.offset=0f;}
        }
    }

    private static boolean nativeOverflow(View view) {
        try {
            boolean handlesOverflow=false;
            for(String name:new String[]{"tag_should_handle_ignore_parent_bounds","tag_should_handle_ignore_parent_bounds_scroll"}) {
                int id=view.getResources().getIdentifier(name,"id","com.android.systemui");
                if(id!=0&&Boolean.TRUE.equals(view.getTag(id)))handlesOverflow=true;
            }
            int childTag=view.getResources().getIdentifier("tag_view_ignore_parent_bounds","id","com.android.systemui");
            if(handlesOverflow&&childTag!=0&&view instanceof ViewGroup) {
                ViewGroup group=(ViewGroup)view;
                for(int i=0;i<group.getChildCount();i++) {
                    View child=group.getChildAt(i);
                    if(child.getVisibility()==View.VISIBLE&&child.isShown()&&Boolean.TRUE.equals(child.getTag(childTag)))return true;
                }
            }
        } catch(RuntimeException unavailable){return true;}
        return false;
    }

    private static void logApplied(Entry entry,boolean blur) {
        if(blur?entry.loggedBlur:entry.loggedFade)return;
        if(blur)entry.loggedBlur=true;else entry.loggedFade=true;
        ModuleDiagnostics.info("tiles",blur?"QS tile edge blur and alpha composition active":"QS tile edge alpha composition active");
    }

    private static void recordError(Entry entry,String stage,Throwable error) {
        if(entry.loggedError)return;
        entry.loggedError=true;ModuleDiagnostics.error("tiles",stage,error);
    }

    /** Bypasses OPlus' split-page crop only for this isolated edge-effect draw. */
    private static void recordUnsplitSource(View view,Entry entry,Canvas canvas,DrawAction nativeDraw) throws Throwable {
        NativeClipGuard guard=null;
        try {guard=NativeClipGuard.begin(view);}
        catch(RuntimeException unavailable) {recordError(entry,"QS tile native split crop guard unavailable",unavailable);}
        try {
            if(guard!=null&&!entry.loggedClip) {
                entry.loggedClip=true;ModuleDiagnostics.info("tiles","QS tile native split crop bypass active during isolated edge composition");
            }
            nativeDraw.draw(canvas);
        } finally {if(guard!=null)guard.close();}
    }

    private static final class NativeClipGuard {
        final View view;final int splitId,scrollId;final Object originalSplit,originalScroll;
        boolean splitWritten,scrollWritten;
        NativeClipGuard(View view,int splitId,int scrollId) {
            this.view=view;this.splitId=splitId;this.scrollId=scrollId;
            originalSplit=view.getTag(splitId);originalScroll=view.getTag(scrollId);
        }
        static NativeClipGuard begin(View view) {
            if(!view.getClass().getName().equals("com.oplus.systemui.qs.base.widget.recyclerview.ParallelCOUIRecyclerView"))return null;
            int splitId=view.getResources().getIdentifier("tag_should_clip_split_parent_bounds","id","com.android.systemui");
            int scrollId=view.getResources().getIdentifier("tag_should_handle_ignore_parent_bounds_scroll","id","com.android.systemui");
            if(splitId==0||scrollId==0)return null;
            NativeClipGuard guard=new NativeClipGuard(view,splitId,scrollId);
            try {
                view.setTag(splitId,Boolean.FALSE);guard.splitWritten=true;
                view.setTag(scrollId,Boolean.TRUE);guard.scrollWritten=true;
                return guard;
            } catch(RuntimeException failure) {guard.close();throw failure;}
        }
        void close() {
            try {if(scrollWritten)view.setTag(scrollId,originalScroll);}
            finally {if(splitWritten)view.setTag(splitId,originalSplit);}
        }
    }

    private void drawFade(View view,Canvas canvas,Geometry geometry,Entry entry,DrawAction nativeDraw) throws Throwable {
        if(!fadeEnabled){nativeDraw.draw(canvas);return;}
        int original=-1,layer;
        try {
            original=canvas.save();
            canvas.clipRect(geometry.scrollX+geometry.left,geometry.scrollY+geometry.top,
                    geometry.scrollX+geometry.right,geometry.scrollY+geometry.bottom);
            layer=canvas.saveLayer(geometry.scrollX+geometry.left,geometry.scrollY+geometry.top,
                    geometry.scrollX+geometry.right,geometry.scrollY+geometry.bottom,null);
        } catch(RuntimeException unavailable) {
            if(original>=0)canvas.restoreToCount(original);
            recordError(entry,"QS tile edge fade output unavailable",unavailable);nativeDraw.draw(canvas);return;
        }
        boolean applied=false;
        try {
            try {recordUnsplitSource(view,entry,canvas,nativeDraw);}
            catch(Throwable nativeFailure) {canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);throw nativeFailure;}
            int save=canvas.save();
            try {
                canvas.translate(geometry.scrollX,geometry.scrollY);
                try {applyMask(canvas,geometry,entry,true);applied=true;}
                catch(RuntimeException unavailable) {recordError(entry,"QS tile edge fade mask unavailable",unavailable);}
                try {applyFence(canvas,geometry,entry);}
                catch(RuntimeException unavailable) {
                    applied=false;canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
                    recordError(entry,"QS tile edge fade safety mask unavailable",unavailable);
                }
            } catch(RuntimeException unavailable) {
                applied=false;canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
                recordError(entry,"QS tile edge fade composition unavailable",unavailable);
            }
            finally {canvas.restoreToCount(save);}
        } finally {try {canvas.restoreToCount(layer);}finally {canvas.restoreToCount(original);}}
        if(applied)logApplied(entry,false);
    }

    private static void applyMask(Canvas canvas,Geometry geometry,Entry entry,boolean erase) {
        if(Build.VERSION.SDK_INT>=28) {
            canvas.drawRect(geometry.minX(),geometry.minY(),geometry.maxX(),geometry.maxY(),erase?entry.erase:entry.keep);
            return;
        }
        // Older hardware renderers cannot nest identical gradient shader types. Compose alpha in a layer instead.
        entry.eraseRestore.setAlpha(entry.erase.getAlpha());
        int mask=canvas.saveLayer(geometry.minX(),geometry.minY(),geometry.maxX(),geometry.maxY(),entry.eraseRestore);
        try {
            canvas.drawRect(geometry.minX(),geometry.minY(),geometry.maxX(),geometry.maxY(),entry.leftGradient);
            canvas.drawRect(geometry.minX(),geometry.minY(),geometry.maxX(),geometry.maxY(),entry.rightGradient);
            canvas.drawRect(geometry.minX(),geometry.minY(),geometry.maxX(),geometry.maxY(),entry.verticalGradient);
        } finally {canvas.restoreToCount(mask);}
    }

    private static void applyFence(Canvas canvas,Geometry geometry,Entry entry) {
        canvas.drawRect(geometry.minX(),geometry.minY(),geometry.maxX(),geometry.maxY(),entry.fencePaint);
    }

    public void detach(View view) {Entry entry=entries.remove(view);if(entry!=null)releaseRenderer(entry);}
    private static void releaseRenderer(Entry entry) {
        if(entry.renderer!=null&&Build.VERSION.SDK_INT>=31) {
            try {((Api31.Renderer)entry.renderer).close();}catch(Throwable ignored) { }
        }
        entry.renderer=null;
    }

    static float fraction(float offset) {
        return Float.isNaN(offset)||Float.isInfinite(offset)?0f:Math.abs(offset)%1f;
    }
    static float transitionAmount(float offset) {
        float fraction=fraction(offset);float progress=Math.min(1f,Math.min(fraction,1f-fraction)*8f);
        return progress*progress*(3f-2f*progress);
    }
    static float edgeWeight(float x,float left,float right,float range) {
        if(range<=0f||right<=left)return 0f;
        float distance=Math.max(0f,Math.min(x-left,right-x));
        float p=Math.min(1f,distance/Math.min(range,(right-left)/2f));
        return 1f-p*p*(3f-2f*p);
    }
    private static float smooth(float x) {float p=Math.max(0f,Math.min(1f,x));return p*p*(3f-2f*p);}
    static float maskWeight(float x,float y,float left,float right,float leftRange,float rightRange,
            float top,float bottom,float feather) {
        if(bottom<=top||y<=top||y>=bottom)return 0f;
        float l=leftRange<=0f?0f:1f-smooth((x-left)/leftRange);
        float r=rightRange<=0f?0f:smooth((x-(right-rightRange))/rightRange);
        float f=Math.min(Math.max(.001f,feather),(bottom-top)/2f);
        float vertical=smooth(Math.min(y-top,bottom-y)/f);
        return (l+r-l*r)*vertical;
    }

    /** Kept in an API-gated nested class so Android 26-30 can load the fade controller. */
    private static final class Api31 {
        private static final class Renderer {
            final RenderNode source=new RenderNode("C17TilePageSource");
            final RenderNode blurred=new RenderNode("C17TilePageBlur");
            final Paint add=new Paint(Paint.ANTI_ALIAS_FLAG);
            float radius=-1f;
            Renderer() {add.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));source.setClipToBounds(false);blurred.setClipToBounds(false);}
            void prepare(Geometry geometry,float requested) {
                source.setPosition(-geometry.padX,-geometry.padY,geometry.width+geometry.padX,geometry.height+geometry.padY);
                blurred.setPosition(-geometry.padX,-geometry.padY,geometry.width+geometry.padX,geometry.height+geometry.padY);
                if(radius!=requested) {blurred.setRenderEffect(RenderEffect.createBlurEffect(requested,requested,Shader.TileMode.DECAL));radius=requested;}
            }
            boolean draw(View view,Canvas target,Geometry geometry,Entry entry,boolean fade,DrawAction nativeDraw) throws Throwable {
                RecordingCanvas recording;
                try {recording=source.beginRecording(geometry.sourceWidth,geometry.sourceHeight);}
                catch(Throwable unavailable) {entry.blurFailed=true;recordError(entry,"QS tile edge source recording unavailable",unavailable);nativeDraw.draw(target);return false;}
                try {recording.translate(geometry.padX-geometry.scrollX,geometry.padY-geometry.scrollY);recordUnsplitSource(view,entry,recording,nativeDraw);}
                finally {source.endRecording();}
                // Native dispatch has completed. Every following fallback reuses this display list.
                try {
                    RecordingCanvas blurredCanvas=blurred.beginRecording(geometry.sourceWidth,geometry.sourceHeight);
                    try {blurredCanvas.translate(geometry.padX,geometry.padY);blurredCanvas.drawRenderNode(source);}finally{blurred.endRecording();}
                } catch(Throwable unavailable) {entry.blurFailed=true;recordError(entry,"QS tile edge blur recording unavailable",unavailable);drawSource(target,geometry,entry);return false;}
                int original=-1;boolean applied=true;
                try {
                    original=target.save();
                    target.translate(geometry.scrollX,geometry.scrollY);
                    target.clipRect(geometry.left,geometry.top,geometry.right,geometry.bottom);
                    int output=target.saveLayer(geometry.left,geometry.top,geometry.right,geometry.bottom,null);
                    int outputDepth=target.getSaveCount();
                    try {
                        int clear=target.saveLayer(geometry.left,geometry.top,geometry.right,geometry.bottom,null);
                        target.drawRenderNode(source);
                        applyMask(target,geometry,entry,true);
                        target.restoreToCount(clear);
                        int blur=target.saveLayer(geometry.left,geometry.top,geometry.right,geometry.bottom,add);
                        target.drawRenderNode(blurred);
                        applyMask(target,geometry,entry,false);
                        target.restoreToCount(blur);
                        if(fade)applyMask(target,geometry,entry,true);
                        applyFence(target,geometry,entry);
                    } catch(Throwable unavailable) {
                        recordError(entry,"QS tile edge blur composition unavailable",unavailable);
                        applied=false;entry.blurFailed=true;target.restoreToCount(outputDepth);
                        target.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
                        try {target.drawRenderNode(source);applyFence(target,geometry,entry);}
                        catch(Throwable fallbackFailure) {target.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);recordError(entry,"QS tile safe source fallback unavailable",fallbackFailure);}
                    } finally {target.restoreToCount(output);}
                } catch(Throwable unavailable) {
                    recordError(entry,"QS tile edge output layer unavailable",unavailable);
                    applied=false;entry.blurFailed=true;
                    if(original>=0){target.restoreToCount(original);original=-1;}
                    drawSource(target,geometry,entry);
                } finally {if(original>=0)target.restoreToCount(original);}
                return applied;
            }
            void drawSource(Canvas target,Geometry geometry,Entry entry) {
                int save=-1;
                try {
                    save=target.save();target.translate(geometry.scrollX,geometry.scrollY);
                    target.clipRect(geometry.left,geometry.top,geometry.right,geometry.bottom);
                    int layer=target.saveLayer(geometry.left,geometry.top,geometry.right,geometry.bottom,null);
                    try {target.drawRenderNode(source);applyFence(target,geometry,entry);}
                    catch(Throwable unavailable) {target.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);recordError(entry,"QS tile safe source fallback unavailable",unavailable);}
                    finally {target.restoreToCount(layer);}
                } catch(Throwable unavailable) {recordError(entry,"QS tile safe output fallback unavailable",unavailable);}
                finally {if(save>=0)target.restoreToCount(save);}
            }
            void close() {source.discardDisplayList();blurred.discardDisplayList();blurred.setRenderEffect(null);}
        }
    }
}
