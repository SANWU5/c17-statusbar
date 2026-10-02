package dev.puitheme;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;
import com.oplus.posteffect.drawable.BlendDrawable;
import com.oplus.posteffect.agsl.DrawableShader;
import java.util.*;
import java.util.concurrent.Executor;

/** Exercise the actual pixel pipeline and native paint ownership, including delayed completions. */
public final class QsMediaAppearanceCheck {
    static int checks;
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static final class Queue implements Executor,QsMediaAppearance.Scheduler {
        final ArrayDeque<Runnable> jobs=new ArrayDeque<>();
        final IdentityHashMap<Runnable,Long> timers=new IdentityHashMap<>();
        long clock;int schedules;boolean animations=true;
        public void execute(Runnable job){jobs.add(job);}
        public long now(){return clock;}
        public void after(Runnable task,long delay){timers.put(task,clock+delay);schedules++;}
        public void cancel(Runnable task){timers.remove(task);}
        public boolean animationsEnabled(){return animations;}
        void advance(long elapsed){
            long target=clock+elapsed;
            for(;;){
                Runnable earliest=null;long at=Long.MAX_VALUE;
                for(Map.Entry<Runnable,Long> timer:timers.entrySet())if(timer.getValue()<at){earliest=timer.getKey();at=timer.getValue();}
                if(earliest==null||at>target)break;
                timers.remove(earliest);clock=at;earliest.run();
            }
            clock=target;
        }
        void runJobs(){while(!jobs.isEmpty())jobs.remove().run();}
        void run(){advance(QsMediaAppearance.DEBOUNCE_MS);runJobs();advance(QsMediaAppearance.FADE_MS);}
    }
    static final class Cover extends ImageView {int width=32,height=32;Cover(){super(new Context());}public int getWidth(){return width;}public int getHeight(){return height;}}
    static final class Card extends OplusQsBaseMediaPanelView {
        public final Cover coverImg=new Cover();
        public final View bg=body;
        public Drawable bgDrawable,themeBgDrawable,transitionDrawable;
        public boolean defaultCover;
        public float coverCornerRadius=8;
        static int resourceReads,ownerGetters;
        boolean deferPosts;
        final ArrayDeque<Runnable> posts=new ArrayDeque<>();
        Card(){coverImg.parent=this;body.parent=this;}
        public View getBg(){ownerGetters++;return bg;}
        public View getCoverImg(){ownerGetters++;return coverImg;}
        public Drawable getBgDrawable(){ownerGetters++;return bgDrawable;}
        public Drawable getThemeBgDrawable(){ownerGetters++;return themeBgDrawable;}
        public com.oplus.systemui.qs.media.multilight.OplusQsMediaBackgroundDrawable getTransitionDrawable(){ownerGetters++;return null;}
        public Float getCoverImgRadius(){resourceReads++;return coverCornerRadius;}
        public boolean post(Runnable job){if(deferPosts)posts.add(job);else job.run();return true;}
    }
    static BitmapDrawable image(Card card,int color){Bitmap bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);return new BitmapDrawable(card.getResources(),bitmap);}
    static Bundle values(boolean enabled){Bundle values=new Bundle();values.putBoolean(QsMediaAppearance.MASTER,enabled);values.putFloat("qs_media_light_background_blur",3);values.putFloat("qs_media_light_glow_radius",2);return values;}
    static AutoBlurDrawable material(Card card){AutoBlurDrawable blur=new AutoBlurDrawable();card.bgDrawable=blur;card.bg.setBackground(blur);BlendDrawable engine=blur.viewBlurProxy.actual.blurDrawable;engine.setCallback(card);
        // Native constructor/updateShader always clear the scratch builder after compilation.
        engine.drawableShader.shaderStringBuilder=new StringBuilder();return blur;}
    static RuntimeShader draw(QsMediaAppearance appearance,Card card,AutoBlurDrawable blur) throws Throwable {
        Canvas canvas=new Canvas();BlendDrawable engine=blur.viewBlurProxy.actual.blurDrawable;
        appearance.drawMedia(card,canvas,target -> appearance.drawBlur(blur,target,nativeCanvas -> {
            Drawable actual=blur.viewBlurProxy.getBlurDrawable(null);
            appearance.onNativeBlurResult(blur.viewBlurProxy,actual);
            appearance.drawGlassContent(engine,nativeCanvas,engine::onDrawContent);
        }));
        appearance.drawCover(card,card.coverImg,canvas,target -> true);
        return engine.recorded;
    }
    static int pixel(RuntimeShader shader){return ((BitmapShader)shader.inputs.get("c17MediaCover")).bitmap.pixels[0];}
    static int rendered(RuntimeShader shader,int nativeColor){
        int next=pixel(shader),old=((BitmapShader)shader.inputs.get("c17MediaPrevious")).bitmap.pixels[0];
        float mix=shader.floats.get("c17MediaMix")[0];
        float a=shader.floats.get("c17MediaOpacity")[0]*mix,b=shader.floats.get("c17MediaPreviousOpacity")[0]*(1-mix);
        float nativeWeight=1-a*(next>>>24)/255f-b*(old>>>24)/255f;int result=0xff000000;
        for(int shift=0;shift<=16;shift+=8)result|=Math.round(((nativeColor>>>shift)&255)*nativeWeight
                +((next>>>shift)&255)*a+((old>>>shift)&255)*b)<<shift;
        return result;
    }
    static final class GlowCanvas extends Canvas {
        final ArrayList<Bitmap> images=new ArrayList<>();final ArrayList<Integer> alphas=new ArrayList<>();
        @Override public void drawBitmap(Bitmap bitmap,Rect source,RectF destination,Paint paint){images.add(bitmap);alphas.add(paint.getAlpha());}
    }
    static GlowCanvas glow(QsMediaAppearance appearance,Card card)throws Throwable {
        GlowCanvas canvas=new GlowCanvas();appearance.drawCover(card,card.coverImg,canvas,target->true);return canvas;
    }
    static void steadyAndChanges() throws Throwable {
        Queue worker=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(worker,worker);Card card=new Card();AutoBlurDrawable blur=material(card);
        BitmapDrawable artwork=image(card,0xffcc2200);card.coverImg.setImageDrawable(artwork);
        appearance.onNativeUpdate(card,"bindCoverImg");check(worker.jobs.isEmpty(),"default OFF never snapshots or queues");
        Bundle settings=values(true);appearance.configure(settings);worker.run();RuntimeShader first=draw(appearance,card,blur);
        check(first!=blur.viewBlurProxy.actual.blurDrawable.drawableShader.shader,"native draw actually records the customized material");
        check((pixel(first)&0x00ffffff)==0xcc2200,"real CPU artwork and blur preserve a constant red image");
        check(blur.viewBlurProxy.actual.blurDrawable instanceof com.oplus.posteffect.drawable.ContinuousBlurDrawable,"real PlatformBlur engine follows BlurDrawable -> ContinuousBlurDrawable -> BlendDrawable");
        int builds=com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt.builds;
        check(builds>0&&first.source.contains("c17MediaCover"),"cleared native shader scratch rebuilds a functional sampling source");
        int creations=Bitmap.creations,scales=Bitmap.scales,reads=Bitmap.reads,uploads=first.uploadCount;
        int getters=Card.ownerGetters,resources=Card.resourceReads,effects=DrawableShader.allEffectsReads,requests=AutoBlurDrawable.Proxy.nativeRequests;
        int invalidations=card.invalidations,layouts=card.layoutRequests;
        for(int i=0;i<1000;i++){
            card.coverImg.setImageDrawable(artwork);appearance.onCoverChanged(card.coverImg);
            appearance.onNativeUpdate(card,"bindMediaDataInner");appearance.configure(new Bundle(settings));
            appearance.onNativeMaterialUpdate(blur.viewBlurProxy.actual.blurDrawable.drawableShader,"setSize(float,float)",new Object[]{240f,120f});
            check(draw(appearance,card,blur)==first,"stable native shader cache reused");
        }
        check(Bitmap.creations==creations&&Bitmap.scales==scales&&Bitmap.reads==reads,"1000 unchanged callbacks/draws do not snapshot/resample/blur");
        check(first.uploadCount==uploads&&DrawableShader.allEffectsReads==effects,"1000 unchanged callbacks/draws do not rediscover effects or upload uniforms");
        check(com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt.builds==builds,"1000 stable native draws do not rebuild the cleared shader scratch source");
        check(Card.ownerGetters==getters&&Card.resourceReads==resources,"1000 unchanged bindings do not query native getters or resources");
        check(AutoBlurDrawable.Proxy.nativeRequests==requests+1000,"exactly one native provider call per native draw, no module re-fetch");
        check(card.invalidations==invalidations&&card.layoutRequests==layouts&&worker.jobs.isEmpty(),"steady callbacks do not invalidate, lay out or queue work");
        settings.putFloat("qs_media_light_background_opacity",35);appearance.configure(settings);check(worker.jobs.isEmpty(),"opacity does not blur again");worker.run();RuntimeShader opacity=draw(appearance,card,blur);
        check(opacity==first&&opacity.floats.get("c17MediaOpacity")[0]==.35f,"opacity updates only existing cover uniform");check(Bitmap.creations==creations,"opacity does not recreate bitmap");
        artwork.getBitmap().eraseColor(0xff0044cc);appearance.onCoverChanged(card.coverImg);worker.run();RuntimeShader blue=draw(appearance,card,blur);
        check((pixel(blue)&0x00ffffff)==0x0044cc,"in-place bitmap generation updates real cover pixels");check(Bitmap.scales==scales+1,"one actual content change captures once");
        DrawableShader nativeShader=blur.viewBlurProxy.actual.blurDrawable.drawableShader;nativeShader.commonArray[0]=77;appearance.onNativeMaterialUpdate(nativeShader);
        int beforeNativeUpload=DrawableShader.commonUploads;draw(appearance,card,blur);check(DrawableShader.commonUploads==beforeNativeUpload+1,"changed native corner/geometry uniforms copied once");
        check(blue.floats.get("nativeCommon")[0]==77,"custom material retains latest native common parameters");draw(appearance,card,blur);check(DrawableShader.commonUploads==beforeNativeUpload+1,"native update consumed once");
        appearance.onNativeMaterialUpdate(nativeShader,"setCornerParams(array)",new Object[]{nativeShader.commonArray});draw(appearance,card,blur);
        nativeShader.commonArray[0]=88;appearance.onNativeMaterialUpdate(nativeShader,"setCornerParams(array)",new Object[]{nativeShader.commonArray});draw(appearance,card,blur);
        check(blue.floats.get("nativeCommon")[0]==88,"in-place native argument contents invalidate cached upload");
        RuntimeShader previousNative=nativeShader.shader;nativeShader.shader=new RuntimeShader("native replacement");draw(appearance,card,blur);check(nativeShader.shader!=previousNative,"native shader identity replacement retained");
        card.bodyWidth=300;appearance.onNativeUpdate(card,"bindMediaDataInner");worker.run();int beforeResize=Bitmap.scales;draw(appearance,card,blur);check(Bitmap.scales==beforeResize,"size recompute settles without a second snapshot");
        RuntimeShader original=nativeShader.shader;try{appearance.drawGlassContent(blur.viewBlurProxy.actual.blurDrawable,new Canvas(),target->{throw new IllegalStateException("native draw failed");});}catch(IllegalStateException expected){}
        check(nativeShader.shader==original,"throwing native paint restores original shader");
        appearance.configure(values(false));check(draw(appearance,card,blur)==original,"OFF restores native material");check(card.layoutListeners.isEmpty()&&card.coverImg.layoutListeners.isEmpty(),"OFF releases listeners");
        appearance.configure(values(true));worker.run();check(draw(appearance,card,blur)!=original,"resume rebuilds current live binding");
        Bundle safe=values(true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);appearance.configure(safe);check(draw(appearance,card,blur)==original,"safe restores native material");
        appearance.releaseRuntime();int snapshots=Bitmap.scales;appearance.configure(values(true));appearance.onNativeUpdate(card,"onAttachedToWindow");appearance.onCoverChanged(card.coverImg);worker.run();check(Bitmap.scales==snapshots,"uninstall rejects late callbacks and reconfiguration");
    }
    static void staleAndUnsupported() throws Throwable {
        Queue worker=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(worker,worker);Card card=new Card();AutoBlurDrawable blur=material(card);
        card.coverImg.setImageDrawable(image(card,0xffdd0000));appearance.onNativeUpdate(card,"onAttachedToWindow");appearance.configure(values(true));card.deferPosts=true;worker.run();
        card.coverImg.setImageDrawable(image(card,0xff00dd00));appearance.onCoverChanged(card.coverImg);worker.run();check(card.posts.size()==2,"two real asynchronous completions queued");
        card.posts.remove().run();check(draw(appearance,card,blur)==blur.viewBlurProxy.actual.blurDrawable.drawableShader.shader,"old result cannot cover new artwork");
        card.posts.remove().run();check((pixel(draw(appearance,card,blur))&0x00ffffff)==0x00dd00,"latest artwork completion wins");
        card.deferPosts=false;card.coverImg.setImageDrawable(image(card,0xff0000dd));appearance.onCoverChanged(card.coverImg);worker.advance(1500);appearance.detach(card);int reads=Bitmap.reads;worker.run();
        check(Bitmap.reads==reads,"detach cancels queued blur before processing pixels");check(card.layoutListeners.isEmpty()&&card.coverImg.layoutListeners.isEmpty(),"detach releases listeners");
        appearance.onNativeUpdate(card,"onAttachedToWindow");worker.run();draw(appearance,card,blur);
        card.coverImg.setImageDrawable(new Drawable(){public void draw(Canvas c){}public void setAlpha(int v){}public void setColorFilter(ColorFilter c){}public int getOpacity(){return 0;}});appearance.onCoverChanged(card.coverImg);int scales=Bitmap.scales;
        for(int i=0;i<1000;i++){appearance.onNativeUpdate(card,"bindCoverImg");draw(appearance,card,blur);}
        check(Bitmap.scales==scales&&worker.jobs.isEmpty(),"unknown/animated-style artwork stays native without per-frame snapshots");
        worker.run();check(draw(appearance,card,blur)==blur.viewBlurProxy.actual.blurDrawable.drawableShader.shader,"unsupported artwork preserves original material after the quiet period");
        appearance.releaseRuntime();
    }
    static RuntimeShader drawMask(QsMediaAppearance appearance,Card card,
            com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable mask,
            com.oplusos.systemui.common.blurability.wallpaper.BlendWallpaperBlurDrawable wallpaper)throws Throwable {
        Canvas c=new Canvas();appearance.drawMedia(card,c,target->appearance.drawBlur(mask,target,nativeCanvas->{
            Drawable actual=mask.viewBlurProxy.getBlurDrawable(null);appearance.onNativeBlurResult(mask.viewBlurProxy,actual);
            appearance.drawGlassContent(wallpaper.blendDrawable,nativeCanvas,wallpaper.blendDrawable::onDrawContent);
        }));return wallpaper.blendDrawable.recorded;
    }
    static void nativeWallpaperBranch()throws Throwable {
        Queue worker=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(worker,worker);Card card=new Card();
        com.oplusos.systemui.common.blurability.wallpaper.BlendWallpaperBlurDrawable wallpaper=
                new com.oplusos.systemui.common.blurability.wallpaper.BlendWallpaperBlurDrawable();
        wallpaper.blendDrawable.drawableShader.shaderStringBuilder=new StringBuilder();
        final int[] requests={0};
        com.oplusos.systemui.common.blurability.ViewBlurProxy proxy=new com.oplusos.systemui.common.blurability.ViewBlurProxy(){
            public Drawable getBlurDrawable(Drawable fallback){requests[0]++;return wallpaper;}
        };
        com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable mask=
                new com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable(proxy);
        card.bgDrawable=mask;card.bg.setBackground(mask);card.coverImg.setImageDrawable(image(card,0xff22cc44));
        appearance.onNativeUpdate(card,"onAttachedToWindow");appearance.configure(values(true));worker.run();
        RuntimeShader nativeShader=wallpaper.blendDrawable.drawableShader.shader;
        RuntimeShader first=drawMask(appearance,card,mask,wallpaper);
        check(first!=nativeShader,"exact MaskBlur proxy -> BlendWallpaper wrapper -> Blend native engine is acquired");
        check((pixel(first)&0x00ffffff)==0x22cc44,"wallpaper material records real artwork pixels rather than native placeholder");
        int builds=com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt.builds,reads=Bitmap.reads,uploads=first.uploadCount,fetches=requests[0];
        for(int i=0;i<1000;i++){appearance.onNativeUpdate(card,"onDrawableUpdate");drawMask(appearance,card,mask,wallpaper);}
        check(requests[0]==fetches+1000,"wallpaper branch keeps one native provider call, no module refetch");
        check(Bitmap.reads==reads&&com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt.builds==builds&&first.uploadCount==uploads,
                "1000 stable wallpaper records do not filter/rebuild/upload again");
        appearance.configure(values(false));check(drawMask(appearance,card,mask,wallpaper)==nativeShader,"OFF releases exact wallpaper material ownership");
        appearance.releaseRuntime();
    }
    static void nativeFailureRecovery()throws Throwable {
        Queue worker=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(worker,worker);Card card=new Card();AutoBlurDrawable blur=material(card);
        card.coverImg.setImageDrawable(image(card,0xffaa6600));appearance.onNativeUpdate(card,"onAttachedToWindow");appearance.configure(values(true));worker.run();
        DrawableShader nativeOwner=blur.viewBlurProxy.actual.blurDrawable.drawableShader;
        RuntimeShader original=nativeOwner.shader;DrawableShader.failBaseUniform=true;
        try {
            check(draw(appearance,card,blur)==original,"native uniform failure keeps original material");
            check(appearance.tracksMaterial(nativeOwner),"failed owner remains eligible for exact native update observation");
            int attempts=DrawableShader.baseAttempts,builds=com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt.builds,reads=Bitmap.reads;
            for(int i=0;i<1000;i++){
                appearance.onNativeMaterialUpdate(nativeOwner,"setSize(float,float)",new Object[]{240f,120f});
                appearance.onNativeUpdate(card,"onDrawableUpdate");draw(appearance,card,blur);
            }
            check(DrawableShader.baseAttempts==attempts&&com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt.builds==builds&&Bitmap.reads==reads,
                    "1000 unchanged native events after failure never retry shader/resources/filtering");
            DrawableShader.failBaseUniform=false;
            appearance.onNativeMaterialUpdate(nativeOwner,"setSize(float,float)",new Object[]{240f,120f});
            check(draw(appearance,card,blur)==original,"retry needs actual native content change, not same setter call");
            nativeOwner.commonArray[0]=99;
            appearance.onNativeMaterialUpdate(nativeOwner,"setCorner(nativeArray)",new Object[]{nativeOwner.commonArray});
            RuntimeShader repaired=draw(appearance,card,blur);
            check(repaired!=original&&repaired.floats.get("nativeCommon")[0]==99,"real native material change recovers same original shader identity");
            check((pixel(repaired)&0x00ffffff)==0xaa6600,"recovered material uses previously prepared real artwork");
            check(Bitmap.reads==reads,"native failure recovery reuses artwork instead of reblurring");
        } finally {DrawableShader.failBaseUniform=false;appearance.releaseRuntime();}
    }
    static void pointValues()throws Throwable {
        Queue worker=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(worker,worker);Card card=new Card();AutoBlurDrawable blur=material(card);
        card.coverImg.setImageDrawable(image(card,0xff006699));appearance.onNativeUpdate(card,"onAttachedToWindow");appearance.configure(values(true));worker.run();
        DrawableShader owner=blur.viewBlurProxy.actual.blurDrawable.drawableShader;RuntimeShader shader=draw(appearance,card,blur);
        int before=DrawableShader.baseUploads,reads=Bitmap.reads;PointF point=owner.localMatrixScalePointF;
        point.set(2f,3f);appearance.onNativeMaterialUpdate(owner,"setMatrix",new Object[]{new Matrix(),point});draw(appearance,card,blur);
        check(DrawableShader.baseUploads==before+1&&shader.floats.get("nativeMatrixScale")[0]==2f&&shader.floats.get("nativeMatrixScale")[1]==3f,
                "same PointF x/y mutation updates actual native matrix scale uniforms");
        before=DrawableShader.baseUploads;int builds=com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt.builds;
        for(int i=0;i<1000;i++){
            PointF equivalent=new PointF(2f,3f);owner.localMatrixScalePointF=equivalent;
            appearance.onNativeMaterialUpdate(owner,"setMatrix",new Object[]{new Matrix(),equivalent});draw(appearance,card,blur);
        }
        check(DrawableShader.baseUploads==before&&com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt.builds==builds,
                "1000 new PointF/Matrix objects with identical numerical values do not upload/rebuild");
        owner.localMatrixScalePointF.y=4f;appearance.onNativeMaterialUpdate(owner,"setMatrix",new Object[]{new Matrix(),owner.localMatrixScalePointF});draw(appearance,card,blur);
        check(DrawableShader.baseUploads==before+1&&shader.floats.get("nativeMatrixScale")[1]==4f,"second in-place PointF change is observed after equivalent replacements");
        check(Bitmap.reads==reads,"native PointF changes retain prepared artwork pixels");appearance.releaseRuntime();
    }
    static void trailingDebounce()throws Throwable {
        Queue clock=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(clock,clock);Card card=new Card();AutoBlurDrawable blur=material(card);
        BitmapDrawable red=image(card,0xffcc2200);card.coverImg.setImageDrawable(red);
        appearance.onNativeUpdate(card,"onAttachedToWindow");appearance.configure(values(true));
        RuntimeShader original=blur.viewBlurProxy.actual.blurDrawable.drawableShader.shader;
        int scales=Bitmap.scales,reads=Bitmap.reads;
        clock.advance(1499);
        check(draw(appearance,card,blur)==original&&Bitmap.scales==scales&&clock.jobs.isEmpty(),"initial artwork waits the full quiet period without taking a snapshot");
        clock.advance(1);check(clock.jobs.size()==1&&Bitmap.scales==scales+1,"exactly 1500ms schedules one capture and worker");
        check(draw(appearance,card,blur)==original,"initial preparation never exposes an unfinished result");
        clock.runJobs();clock.advance(QsMediaAppearance.FADE_MS);RuntimeShader complete=draw(appearance,card,blur);
        check((pixel(complete)&0xffffff)==0xcc2200,"first completed red material is committed atomically");
        BitmapDrawable blue=image(card,0xff0044cc);card.coverImg.setImageDrawable(blue);appearance.onCoverChanged(card.coverImg);
        int scheduled=clock.schedules,invalidations=card.invalidations;scales=Bitmap.scales;reads=Bitmap.reads;
        for(int i=0;i<1000;i++){
            appearance.onCoverChanged(card.coverImg);appearance.onNativeUpdate(card,"bindCoverImg");appearance.configure(values(true));
            check(draw(appearance,card,blur)==complete&&(pixel(complete)&0xffffff)==0xcc2200,"pending change keeps the last complete backdrop");
        }
        check(clock.schedules==scheduled&&Bitmap.scales==scales&&Bitmap.reads==reads&&card.invalidations==invalidations,
                "1000 identical pending callbacks do not reset the deadline, snapshot, blur or invalidate");
        clock.advance(1000);
        BitmapDrawable green=image(card,0xff22cc44);card.coverImg.setImageDrawable(green);appearance.onCoverChanged(card.coverImg);
        Bundle style=values(true);style.putFloat("qs_media_light_background_opacity",35);appearance.configure(style);
        card.bodyWidth=300;appearance.onNativeUpdate(card,"bindMediaDataInner");
        clock.advance(1400);
        // A new wrapper for the same bitmap/content is not another real artwork update.
        card.coverImg.setImageDrawable(new BitmapDrawable(card.getResources(),green.getBitmap()));appearance.onCoverChanged(card.coverImg);
        int unchangedSchedules=clock.schedules;
        clock.advance(99);
        check(draw(appearance,card,blur)==complete&&clock.jobs.isEmpty()&&Bitmap.scales==scales,
                "a burst of artwork, style and size changes stays on the old image until its trailing deadline");
        clock.advance(1);
        check(clock.jobs.size()==1&&Bitmap.scales==scales+1&&clock.schedules==unchangedSchedules,
                "same-content wrapper does not postpone the last true update; burst captures exactly once");
        check((pixel(draw(appearance,card,blur))&0xffffff)==0xcc2200,"the old result remains visible while the worker calculates");
        clock.runJobs();RuntimeShader committed=draw(appearance,card,blur);
        check((pixel(committed)&0xffffff)==0x22cc44&&committed.floats.get("c17MediaOpacity")[0]==.35f,
                "the single final commit contains the latest artwork and latest style together");
        // One content-dirty drawable callback and one owner invalidation at the single commit.
        check(card.invalidations==invalidations+2,"burst invalidates the native material and its owner once at commit, never once per binding");
        clock.advance(QsMediaAppearance.FADE_MS);draw(appearance,card,blur);
        int steadyTimers=clock.schedules,steadyReads=Bitmap.reads,steadyUploads=committed.uploadCount;
        for(int i=0;i<1000;i++){clock.advance(1);appearance.onNativeUpdate(card,"onDrawableUpdate");draw(appearance,card,blur);}
        check(clock.schedules==steadyTimers&&Bitmap.reads==steadyReads&&committed.uploadCount==steadyUploads,
                "stable draws after a committed burst neither schedule nor blur nor upload again");

        // Native rebinding often emits a short null/default-cover stage. Keep the previous material.
        card.coverImg.setImageDrawable(null);appearance.onCoverChanged(card.coverImg);clock.advance(700);
        check(draw(appearance,card,blur)==committed,"transient missing artwork never clears a completed backdrop");
        card.defaultCover=true;card.coverImg.setImageDrawable(image(card,0xffeeeeee));appearance.onNativeUpdate(card,"bindCoverImg");
        clock.advance(500);check(draw(appearance,card,blur)==committed,"native default-cover stage remains on the old completed background");
        card.defaultCover=false;card.coverImg.setImageDrawable(image(card,0xff9955aa));appearance.onNativeUpdate(card,"bindCoverImg");
        clock.advance(1499);check(draw(appearance,card,blur)==committed,"new artwork after a transient null/default stage still receives a full quiet period");
        clock.advance(1);clock.runJobs();clock.advance(QsMediaAppearance.FADE_MS);check((pixel(draw(appearance,card,blur))&0xffffff)==0x9955aa,"rebinding burst switches once to final artwork");
        RuntimeShader last=draw(appearance,card,blur);
        card.coverImg.setImageDrawable(null);appearance.onCoverChanged(card.coverImg);clock.advance(1499);
        check(draw(appearance,card,blur)==last,"permanently missing artwork also keeps the completed backdrop during debounce");
        clock.advance(1);clock.advance(QsMediaAppearance.FADE_MS);check(draw(appearance,card,blur)==original,"missing artwork fades back to native only after the quiet period");
        appearance.releaseRuntime();
    }

    static void staleDebounceAndCancellation()throws Throwable {
        Queue clock=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(clock,clock);Card card=new Card();AutoBlurDrawable blur=material(card);
        card.coverImg.setImageDrawable(image(card,0xffcc2200));appearance.onNativeUpdate(card,"onAttachedToWindow");appearance.configure(values(true));clock.run();
        RuntimeShader last=draw(appearance,card,blur);card.deferPosts=true;
        card.coverImg.setImageDrawable(image(card,0xff0044cc));appearance.onCoverChanged(card.coverImg);clock.advance(1500);clock.runJobs();
        check(card.posts.size()==1,"the old worker completion is deliberately delayed at the main-thread boundary");
        card.coverImg.setImageDrawable(image(card,0xff22cc44));appearance.onCoverChanged(card.coverImg);card.posts.remove().run();
        check((pixel(draw(appearance,card,blur))&0xffffff)==0xcc2200,"a stale prepared completion cannot replace the last backdrop during the newer quiet period");
        clock.advance(1499);check(clock.jobs.isEmpty(),"a stale completion cannot shorten the newer debounce interval");
        clock.advance(1);clock.runJobs();check(card.posts.size()==1,"latest generation prepares only after its own quiet period");card.posts.remove().run();
        check((pixel(draw(appearance,card,blur))&0xffffff)==0x22cc44,"the later completed generation is the only visible replacement");
        card.deferPosts=false;
        RuntimeShader original=blur.viewBlurProxy.actual.blurDrawable.drawableShader.shader;
        for(int action=0;action<4;action++){
            card.coverImg.setImageDrawable(image(card,0xff550000+action));appearance.onCoverChanged(card.coverImg);clock.advance(1499);
            int captured=Bitmap.scales;
            Runnable cancelled=clock.timers.keySet().iterator().next();
            if(action==0)appearance.configure(values(false));
            else if(action==1){Bundle safe=values(true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);appearance.configure(safe);}
            else if(action==2){card.attached=false;appearance.detach(card);}
            else appearance.releaseRuntime();
            check(draw(appearance,card,blur)==original&&clock.timers.isEmpty(),"OFF/safe/detach/uninstall restores native immediately and removes delayed work "+action);
            cancelled.run();clock.advance(5000);clock.runJobs();
            check(Bitmap.scales==captured&&draw(appearance,card,blur)==original,"a cancelled callback cannot recreate an effect after release "+action);
            if(action<3){card.attached=true;appearance.configure(values(true));appearance.onNativeUpdate(card,"onAttachedToWindow");clock.run();last=draw(appearance,card,blur);check(last!=original,"a live resume rebuilds after its own quiet period "+action);}
        }
    }

    static void debouncedSubEffects()throws Throwable {
        Queue clock=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(clock,clock);Card card=new Card();AutoBlurDrawable blur=material(card);
        card.coverImg.setImageDrawable(image(card,0xffcc2200));appearance.onNativeUpdate(card,"onAttachedToWindow");Bundle settings=values(true);appearance.configure(settings);clock.run();
        RuntimeShader ready=draw(appearance,card,blur),original=blur.viewBlurProxy.actual.blurDrawable.drawableShader.shader;
        int scales=Bitmap.scales;settings.putFloat("qs_media_light_background_opacity",25);appearance.configure(settings);clock.advance(1499);
        check(draw(appearance,card,blur)==ready&&ready.floats.get("c17MediaOpacity")[0]==.7f,"style-only changes keep the previously committed opacity until quiet");
        clock.advance(1);check(clock.jobs.isEmpty()&&Bitmap.scales==scales,"style-only quiet commit reuses prepared pixels without worker filtering");
        check(draw(appearance,card,blur).floats.get("c17MediaOpacity")[0]==.25f,"style opacity is committed once at its own deadline");
        settings.putBoolean(QsMediaAppearance.BACKGROUND,false);settings.putBoolean(QsMediaAppearance.GLOW,false);appearance.configure(settings);clock.advance(1499);
        check(draw(appearance,card,blur)==ready,"sub-effect setting changes keep the completed backdrop while waiting");
        clock.advance(1);clock.advance(QsMediaAppearance.FADE_MS);check(draw(appearance,card,blur)==original,"sub-effects switch together after quiet without changing the native cover");
        check(Bitmap.scales==scales&&clock.jobs.isEmpty(),"turning off sub-effects performs no bitmap preparation");
        check(!appearance.tracksMaterial(blur.viewBlurProxy.actual.blurDrawable.drawableShader),"completed background fade-out releases its old shader/texture ownership while preserving the native engine");
        appearance.releaseRuntime();
    }

    static void nativeRebindingDebounce()throws Throwable {
        Queue clock=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(clock,clock);Card card=new Card();AutoBlurDrawable old=material(card);
        card.coverImg.setImageDrawable(image(card,0xffcc2200));appearance.onNativeUpdate(card,"onAttachedToWindow");appearance.configure(values(true));clock.run();
        check((pixel(draw(appearance,card,old))&0xffffff)==0xcc2200,"initial native source has the completed red background");
        int scales=Bitmap.scales;
        AutoBlurDrawable replacement=material(card);card.coverImg.setImageDrawable(null);appearance.onNativeUpdate(card,"onDrawableUpdate");
        RuntimeShader newNative=replacement.viewBlurProxy.actual.blurDrawable.drawableShader.shader;
        check(draw(appearance,card,replacement)!=newNative&&(pixel(draw(appearance,card,replacement))&0xffffff)==0xcc2200,
                "a newly bound native engine still draws the previous completed material during a null-cover stage");
        clock.advance(800);card.defaultCover=true;card.coverImg.setImageDrawable(image(card,0xffdddddd));appearance.onNativeUpdate(card,"bindCoverImg");
        check((pixel(draw(appearance,card,replacement))&0xffffff)==0xcc2200,"default-cover rebinding never makes the background fall back to native prematurely");
        clock.advance(200);card.defaultCover=false;card.coverImg.setImageDrawable(image(card,0xff0044cc));appearance.onNativeUpdate(card,"bindCoverImg");
        clock.advance(1499);check((pixel(draw(appearance,card,replacement))&0xffffff)==0xcc2200&&Bitmap.scales==scales,
                "current-source ownership accepts the older Prepared until the final new source is quiet");
        clock.advance(1);clock.runJobs();clock.advance(QsMediaAppearance.FADE_MS);check((pixel(draw(appearance,card,replacement))&0xffffff)==0x0044cc,
                "only a successful new result replaces the retained image on the new native engine");
        appearance.releaseRuntime();
    }

    static void lightweightCrossfade()throws Throwable {
        Queue clock=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(clock,clock);Card card=new Card();AutoBlurDrawable blur=material(card);
        card.coverImg.setImageDrawable(image(card,0xffff0000));appearance.onNativeUpdate(card,"onAttachedToWindow");Bundle settings=values(true);settings.putFloat("qs_media_light_background_opacity",100);
        appearance.configure(settings);clock.advance(1500);clock.runJobs();RuntimeShader shader=draw(appearance,card,blur);
        check(shader.source.contains("c17MediaPrevious")&&shader.source.contains("c17OldWeight"),"the actual native shader source mixes cached old and new artwork before preserving native optics");
        check(rendered(shader,0xff000000)==0xff000000,"initial crossfade starts exactly on native material without revealing the complete new artwork abruptly");
        int creations=Bitmap.creations,reads=Bitmap.reads,scales=Bitmap.scales;
        clock.advance(128);shader=draw(appearance,card,blur);int mid=rendered(shader,0xff000000);
        check(((mid>>>16)&255)>50&&((mid>>>16)&255)<230,"initial native-to-artwork transition produces an intermediate visible alpha");
        clock.advance(122);shader=draw(appearance,card,blur);
        check(rendered(shader,0xff000000)==0xffff0000&&clock.timers.isEmpty(),"initial fade finishes at 250ms and leaves no animation timer");
        check(Bitmap.creations==creations&&Bitmap.reads==reads&&Bitmap.scales==scales,"initial alpha transition never snapshots or filters again");
        GlowCanvas redGlow=glow(appearance,card);Bitmap oldGlow=redGlow.images.get(0);int fullGlow=redGlow.alphas.get(0);

        card.coverImg.setImageDrawable(image(card,0xff0000ff));appearance.onCoverChanged(card.coverImg);clock.advance(1499);
        check(rendered(draw(appearance,card,blur),0xff000000)==0xffff0000,"the 1500ms quiet interval keeps the fully completed old artwork");
        clock.advance(1);clock.runJobs();shader=draw(appearance,card,blur);GlowCanvas startGlow=glow(appearance,card);
        check(rendered(shader,0xff000000)==0xffff0000,"a new successful preparation starts at the previous visible background with no native flash");
        check(startGlow.images.size()==1&&startGlow.images.get(0)==oldGlow&&startGlow.alphas.get(0)==fullGlow,
                "glow transition starts at the exact old cached glow and alpha");
        creations=Bitmap.creations;reads=Bitmap.reads;scales=Bitmap.scales;int builds=com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt.builds;
        int nativeUploads=DrawableShader.baseUploads;Bitmap nextBitmap=((BitmapShader)shader.inputs.get("c17MediaCover")).bitmap;
        clock.advance(128);shader=draw(appearance,card,blur);int mixed=rendered(shader,0xff000000);
        check(((mixed>>>16)&255)>40&&(mixed&255)>40&&((mixed>>>16)&255)+(mixed&255)==255,
                "old-red/new-blue fade actually mixes both pixel caches continuously without dipping to native");
        GlowCanvas midGlow=glow(appearance,card);
        check(midGlow.images.size()==2&&midGlow.images.get(0)==oldGlow&&midGlow.images.get(1)!=oldGlow,
                "glow midpoint draws only the two previously prepared caches");
        check(midGlow.alphas.get(0)>0&&midGlow.alphas.get(1)>0&&Math.abs(midGlow.alphas.get(0)+midGlow.alphas.get(1)-fullGlow)<=1,
                "old and new glow alphas are complementary across the transition");
        for(int i=0;i<1000;i++){appearance.onNativeUpdate(card,"bindMediaDataInner");draw(appearance,card,blur);}
        check(Bitmap.creations==creations&&Bitmap.reads==reads&&Bitmap.scales==scales&&DrawableShader.baseUploads==nativeUploads
                        &&com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt.builds==builds,
                "1000 mid-transition callbacks never snapshot, reblur, rebuild native shader or upload native effects");
        clock.advance(122);shader=draw(appearance,card,blur);GlowCanvas endGlow=glow(appearance,card);
        check(rendered(shader,0xff000000)==0xff0000ff&&endGlow.images.size()==1&&endGlow.alphas.get(0)==fullGlow,
                "background and glow finish together at the latest blue cache");
        check(((BitmapShader)shader.inputs.get("c17MediaPrevious")).bitmap==nextBitmap&&clock.timers.isEmpty(),
                "final native recording releases the old shader input and stops every transition timer");
        int schedules=clock.schedules,uploads=shader.uploadCount,invalidations=card.invalidations;
        for(int i=0;i<1000;i++){clock.advance(1);appearance.onNativeUpdate(card,"onDrawableUpdate");draw(appearance,card,blur);}
        check(clock.schedules==schedules&&shader.uploadCount==uploads&&card.invalidations==invalidations&&Bitmap.reads==reads,
                "1000 stable post-fade frames do not poll time, schedule, upload alpha, reblur or invalidate");

        // A real new binding halfway through a fade retains both caches and the same visual phase.
        card.coverImg.setImageDrawable(image(card,0xff00ff00));appearance.onCoverChanged(card.coverImg);clock.advance(1500);clock.runJobs();draw(appearance,card,blur);clock.advance(128);
        int beforeRebind=rendered(draw(appearance,card,blur),0xff000000);scales=Bitmap.scales;
        AutoBlurDrawable rebound=material(card);appearance.onNativeUpdate(card,"onDrawableUpdate");
        check(rendered(draw(appearance,card,rebound),0xff000000)==beforeRebind&&Bitmap.scales==scales,
                "new native engine during crossfade preserves the current old/new visual blend without preparing any bitmap");
        // Continuous updates wait for quiet; they never restart the already-running alpha transition.
        card.coverImg.setImageDrawable(image(card,0xffffff00));appearance.onCoverChanged(card.coverImg);clock.advance(122);
        check(rendered(draw(appearance,card,rebound),0xff000000)==0xff00ff00,"a later artwork event does not cancel or restart the visible fade in progress");
        clock.advance(1378);clock.runJobs();check(rendered(draw(appearance,card,rebound),0xff000000)==0xff00ff00,"next debounced result starts smoothly from the just-completed prior artwork");
        clock.advance(250);check(rendered(draw(appearance,card,rebound),0xff000000)==0xffffff00,"successive debounced results arrive through one short crossfade each");
        scales=Bitmap.scales;reads=Bitmap.reads;creations=Bitmap.creations;
        settings.putFloat("qs_media_light_background_opacity",20);appearance.configure(settings);clock.advance(1500);
        check(rendered(draw(appearance,card,rebound),0xff000000)==0xffffff00,"opacity-only quiet commit starts from the exact old visible opacity");
        clock.advance(128);int dimmed=rendered(draw(appearance,card,rebound),0xff000000);
        check((dimmed&255)==0&&((dimmed>>>16)&255)>51&&((dimmed>>>16)&255)<255,
                "opacity-only transition continuously interpolates the already cached yellow background toward the new alpha");
        clock.advance(122);check(rendered(draw(appearance,card,rebound),0xff000000)==0xff333300,"opacity-only fade ends at the configured 20 percent material alpha");
        check(Bitmap.scales==scales&&Bitmap.reads==reads&&Bitmap.creations==creations,"opacity-only transition and every intermediate frame reuse the prepared bitmap pixels");
        appearance.releaseRuntime();
    }

    static void crossfadeCancellation()throws Throwable {
        for(int action=0;action<4;action++){
            Queue clock=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(clock,clock);Card card=new Card();AutoBlurDrawable blur=material(card);
            card.coverImg.setImageDrawable(image(card,0xffff0000));appearance.onNativeUpdate(card,"onAttachedToWindow");appearance.configure(values(true));clock.run();
            card.coverImg.setImageDrawable(image(card,0xff0000ff));appearance.onCoverChanged(card.coverImg);clock.advance(1500);clock.runJobs();clock.advance(64);
            RuntimeShader original=blur.viewBlurProxy.actual.blurDrawable.drawableShader.shader;Runnable lateFrame=clock.timers.keySet().iterator().next();
            if(action==0)appearance.configure(values(false));
            else if(action==1){Bundle safe=values(true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);appearance.configure(safe);}
            else if(action==2){card.attached=false;appearance.detach(card);}
            else appearance.releaseRuntime();
            int invalidations=card.invalidations,reads=Bitmap.reads;
            check(draw(appearance,card,blur)==original&&clock.timers.isEmpty(),"OFF/safe/detach/uninstall stops crossfade and restores native immediately "+action);
            lateFrame.run();clock.advance(3000);clock.runJobs();
            check(draw(appearance,card,blur)==original&&card.invalidations==invalidations&&Bitmap.reads==reads,
                    "late animation callback cannot redraw or retain material after cancellation "+action);
            appearance.releaseRuntime();
        }
        Queue clock=new Queue();clock.animations=false;QsMediaAppearance appearance=new QsMediaAppearance(clock,clock);Card card=new Card();AutoBlurDrawable blur=material(card);
        card.coverImg.setImageDrawable(image(card,0xffff0000));appearance.onNativeUpdate(card,"onAttachedToWindow");Bundle settings=values(true);settings.putFloat("qs_media_light_background_opacity",100);appearance.configure(settings);clock.advance(1500);clock.runJobs();
        check(rendered(draw(appearance,card,blur),0xff000000)==0xffff0000&&clock.timers.isEmpty(),"system animation scale zero commits directly without any frame scheduling");
        clock.animations=true;card.coverImg.setImageDrawable(image(card,0xff0000ff));appearance.onCoverChanged(card.coverImg);clock.advance(1500);clock.runJobs();clock.advance(64);draw(appearance,card,blur);
        clock.animations=false;clock.advance(16);check(rendered(draw(appearance,card,blur),0xff000000)==0xff0000ff&&clock.timers.isEmpty(),"turning system animations off during a transition completes it on the next single frame");
        appearance.releaseRuntime();
    }

    public static void main(String[] args)throws Throwable {steadyAndChanges();staleAndUnsupported();nativeWallpaperBranch();nativeFailureRecovery();pointValues();trailingDebounce();staleDebounceAndCancellation();debouncedSubEffects();nativeRebindingDebounce();lightweightCrossfade();crossfadeCancellation();System.out.println("QsMediaAppearanceCheck passed: "+checks+" (native material and real pixels, trailing 1500ms + cached 250ms background/glow crossfade and immediate cancellation)");}
}
