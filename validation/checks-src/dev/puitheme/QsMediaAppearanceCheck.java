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
        void run(){advance(QsMediaAppearance.DEBOUNCE_MS);runJobs();advance(Math.max(QsMediaAppearance.FADE_MS,QsMediaTransition.DIRECT_MS));}
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
        float a=shader.floats.get("c17MediaOpacity")[0]*mix,b=shader.floats.get("c17MediaPreviousOpacity")[0]
                *shader.floats.get("c17MediaPreviousMix")[0];
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
        card.posts.remove().run();check((pixel(draw(appearance,card,blur))&0x00ffffff)==0x00dd00,"old result cannot cover the latest new-artwork solid fill");
        card.posts.remove().run();check((pixel(draw(appearance,card,blur))&0x00ffffff)==0x00dd00,"latest artwork completion wins");
        card.deferPosts=false;card.coverImg.setImageDrawable(image(card,0xff0000dd));appearance.onCoverChanged(card.coverImg);worker.advance(QsMediaAppearance.DEBOUNCE_MS);appearance.detach(card);int reads=Bitmap.reads;worker.run();
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
        clock.advance(QsMediaAppearance.DEBOUNCE_MS-1);
        check((pixel(draw(appearance,card,blur))&0xffffff)==0xcc2200&&Bitmap.scales==scales&&clock.jobs.isEmpty(),"initial artwork immediately shows its own solid color while expensive preparation waits");
        clock.advance(1);check(clock.jobs.size()==1&&Bitmap.scales==scales+1,"exactly the configured 1000ms quiet deadline schedules one capture and worker");
        check((pixel(draw(appearance,card,blur))&0xffffff)==0xcc2200,"initial preparation remains on the new solid fill until blur is ready");
        clock.runJobs();clock.advance(QsMediaAppearance.FADE_MS);RuntimeShader complete=draw(appearance,card,blur);
        check((pixel(complete)&0xffffff)==0xcc2200,"first completed red material is committed atomically");
        BitmapDrawable blue=image(card,0xff0044cc);card.coverImg.setImageDrawable(blue);appearance.onCoverChanged(card.coverImg);
        int scheduled=clock.schedules,invalidations=card.invalidations;scales=Bitmap.scales;reads=Bitmap.reads;
        for(int i=0;i<1000;i++){
            appearance.onCoverChanged(card.coverImg);appearance.onNativeUpdate(card,"bindCoverImg");appearance.configure(values(true));
            check(draw(appearance,card,blur)==complete&&(pixel(complete)&0xffffff)==0x0044cc,"pending change shows the newly sampled blue, never the previous red");
        }
        check(clock.schedules==scheduled&&Bitmap.scales==scales&&Bitmap.reads==reads&&card.invalidations==invalidations,
                "1000 identical pending callbacks do not reset the deadline, snapshot, blur or invalidate");
        clock.advance(QsMediaAppearance.DEBOUNCE_MS/2);
        BitmapDrawable green=image(card,0xff22cc44);card.coverImg.setImageDrawable(green);appearance.onCoverChanged(card.coverImg);
        Bundle style=values(true);style.putFloat("qs_media_light_background_opacity",35);appearance.configure(style);
        card.bodyWidth=300;appearance.onNativeUpdate(card,"bindMediaDataInner");
        clock.advance(QsMediaAppearance.DEBOUNCE_MS-100);
        // A new wrapper for the same bitmap/content is not another real artwork update.
        card.coverImg.setImageDrawable(new BitmapDrawable(card.getResources(),green.getBitmap()));appearance.onCoverChanged(card.coverImg);
        int unchangedSchedules=clock.schedules;
        clock.advance(99);
        check(draw(appearance,card,blur)==complete&&(pixel(complete)&0xffffff)==0x22cc44&&clock.jobs.isEmpty()&&Bitmap.scales==scales,
                "a burst changes the cheap fill immediately but postpones expensive work to its trailing deadline");
        clock.advance(1);
        check(clock.jobs.size()==1&&Bitmap.scales==scales+1&&clock.schedules==unchangedSchedules,
                "same-content wrapper does not postpone the last true update; burst captures exactly once");
        check((pixel(draw(appearance,card,blur))&0xffffff)==0x22cc44,"the latest new solid fill remains visible while the worker calculates");
        clock.runJobs();RuntimeShader committed=draw(appearance,card,blur);
        check((pixel(committed)&0xffffff)==0x22cc44&&committed.floats.get("c17MediaOpacity")[0]==.35f,
                "the single final commit contains the latest artwork and latest style together");
        // One content-dirty drawable callback and one owner invalidation at the single commit.
        check(card.invalidations==invalidations+4,"one real green event and one worker commit invalidate, never duplicate binding callbacks");
        clock.advance(QsMediaAppearance.FADE_MS);draw(appearance,card,blur);
        int steadyTimers=clock.schedules,steadyReads=Bitmap.reads,steadyUploads=committed.uploadCount;
        for(int i=0;i<1000;i++){clock.advance(1);appearance.onNativeUpdate(card,"onDrawableUpdate");draw(appearance,card,blur);}
        check(clock.schedules==steadyTimers&&Bitmap.reads==steadyReads&&committed.uploadCount==steadyUploads,
                "stable draws after a committed burst neither schedule nor blur nor upload again");

        // Native rebinding often emits a short null/default-cover stage. Keep the previous material.
        card.coverImg.setImageDrawable(null);appearance.onCoverChanged(card.coverImg);clock.advance(QsMediaAppearance.DEBOUNCE_MS*2/5);
        check(draw(appearance,card,blur)==committed,"transient missing artwork never clears a completed backdrop");
        card.defaultCover=true;card.coverImg.setImageDrawable(image(card,0xffeeeeee));appearance.onNativeUpdate(card,"bindCoverImg");
        clock.advance(QsMediaAppearance.DEBOUNCE_MS*3/10);check(draw(appearance,card,blur)==committed,"native default-cover stage remains on the old completed background");
        card.defaultCover=false;card.coverImg.setImageDrawable(image(card,0xff9955aa));appearance.onNativeUpdate(card,"bindCoverImg");
        clock.advance(QsMediaAppearance.DEBOUNCE_MS-1);check((pixel(draw(appearance,card,blur))&0xffffff)==0x9955aa,"new artwork after a transient null/default stage immediately gets its own color");
        clock.advance(1);clock.runJobs();clock.advance(QsMediaAppearance.FADE_MS);check((pixel(draw(appearance,card,blur))&0xffffff)==0x9955aa,"rebinding burst switches once to final artwork");
        RuntimeShader last=draw(appearance,card,blur);
        card.coverImg.setImageDrawable(null);appearance.onCoverChanged(card.coverImg);clock.advance(QsMediaAppearance.DEBOUNCE_MS-1);
        check(draw(appearance,card,blur)==last,"permanently missing artwork also keeps the completed backdrop during debounce");
        clock.advance(1);clock.advance(QsMediaTransition.DIRECT_MS);check(draw(appearance,card,blur)==original,"missing artwork fades back to native only after the quiet period");
        appearance.releaseRuntime();
    }

    static void staleDebounceAndCancellation()throws Throwable {
        Queue clock=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(clock,clock);Card card=new Card();AutoBlurDrawable blur=material(card);
        card.coverImg.setImageDrawable(image(card,0xffcc2200));appearance.onNativeUpdate(card,"onAttachedToWindow");appearance.configure(values(true));clock.run();
        RuntimeShader last=draw(appearance,card,blur);card.deferPosts=true;
        card.coverImg.setImageDrawable(image(card,0xff0044cc));appearance.onCoverChanged(card.coverImg);clock.advance(QsMediaAppearance.DEBOUNCE_MS);clock.runJobs();
        check(card.posts.size()==1,"the old worker completion is deliberately delayed at the main-thread boundary");
        card.coverImg.setImageDrawable(image(card,0xff22cc44));appearance.onCoverChanged(card.coverImg);card.posts.remove().run();
        check((pixel(draw(appearance,card,blur))&0xffffff)==0x22cc44,"a stale prepared completion cannot replace the latest new green solid fill");
        clock.advance(QsMediaAppearance.DEBOUNCE_MS-1);check(clock.jobs.isEmpty(),"a stale completion cannot shorten the newer debounce interval");
        clock.advance(1);clock.runJobs();check(card.posts.size()==1,"latest generation prepares only after its own quiet period");card.posts.remove().run();
        check((pixel(draw(appearance,card,blur))&0xffffff)==0x22cc44,"the later completed generation is the only visible replacement");
        card.deferPosts=false;
        RuntimeShader original=blur.viewBlurProxy.actual.blurDrawable.drawableShader.shader;
        for(int action=0;action<4;action++){
            card.coverImg.setImageDrawable(image(card,0xff550000+action));appearance.onCoverChanged(card.coverImg);clock.advance(QsMediaAppearance.DEBOUNCE_MS-1);
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
        int scales=Bitmap.scales;settings.putFloat("qs_media_light_background_opacity",25);appearance.configure(settings);clock.advance(QsMediaAppearance.DEBOUNCE_MS-1);
        check(draw(appearance,card,blur)==ready&&ready.floats.get("c17MediaOpacity")[0]==.7f,"style-only changes keep the previously committed opacity until quiet");
        clock.advance(1);check(clock.jobs.isEmpty()&&Bitmap.scales==scales,"style-only quiet commit reuses prepared pixels without worker filtering");
        check(draw(appearance,card,blur).floats.get("c17MediaOpacity")[0]==.25f,"style opacity is committed once at its own deadline");
        settings.putBoolean(QsMediaAppearance.BACKGROUND,false);settings.putBoolean(QsMediaAppearance.GLOW,false);appearance.configure(settings);clock.advance(QsMediaAppearance.DEBOUNCE_MS-1);
        check(draw(appearance,card,blur)==ready,"sub-effect setting changes keep the completed backdrop while waiting");
        clock.advance(1);clock.advance(QsMediaTransition.DIRECT_MS);check(draw(appearance,card,blur)==original,"sub-effects switch together after quiet without changing the native cover");
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
        clock.advance(QsMediaAppearance.DEBOUNCE_MS/2);card.defaultCover=true;card.coverImg.setImageDrawable(image(card,0xffdddddd));appearance.onNativeUpdate(card,"bindCoverImg");
        check((pixel(draw(appearance,card,replacement))&0xffffff)==0xcc2200,"default-cover rebinding never makes the background fall back to native prematurely");
        clock.advance(200);card.defaultCover=false;card.coverImg.setImageDrawable(image(card,0xff0044cc));appearance.onNativeUpdate(card,"bindCoverImg");
        clock.advance(QsMediaAppearance.DEBOUNCE_MS-1);check((pixel(draw(appearance,card,replacement))&0xffffff)==0x0044cc&&Bitmap.scales==scales,
                "current-source ownership accepts the new immediate solid fill while blur waits");
        clock.advance(1);clock.runJobs();clock.advance(QsMediaAppearance.FADE_MS);check((pixel(draw(appearance,card,replacement))&0xffffff)==0x0044cc,
                "only a successful new result replaces the retained image on the new native engine");
        appearance.releaseRuntime();
    }

    static void lightweightCrossfade()throws Throwable {
        Queue clock=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(clock,clock);Card card=new Card();AutoBlurDrawable blur=material(card);
        card.coverImg.setImageDrawable(image(card,0xff22dd55));appearance.onNativeUpdate(card,"onAttachedToWindow");Bundle settings=values(true);settings.putFloat("qs_media_light_background_opacity",100);
        appearance.configure(settings);
        RuntimeShader shader=draw(appearance,card,blur);
        check(rendered(shader,0xff24405a)==0xff22dd55,"first enable immediately fills from the actual new artwork, not native material");
        check(((BitmapShader)shader.inputs.get("c17MediaCover")).bitmap.getWidth()==1&&clock.jobs.isEmpty(),"the first visible fill is one cached pixel and requires no blur job yet");
        clock.run();check(rendered(draw(appearance,card,blur),0xff000000)==0xff22dd55,"first successful blur finishes on the same artwork color");
        Bitmap colored=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);int[] pixels=new int[1024];
        for(int y=0;y<32;y++)for(int x=0;x<32;x++)pixels[y*32+x]=x<11?0xffff0000:0xff0000ff;
        colored.setPixels(pixels,0,32,0,0,32,32);
        int scales=Bitmap.scales,reads=Bitmap.reads;
        card.coverImg.setImageDrawable(new BitmapDrawable(card.getResources(),colored));appearance.onCoverChanged(card.coverImg);
        shader=draw(appearance,card,blur);
        final int fill=0xff5500aa;
        check(rendered(shader,0xff24405a)==fill&&rendered(shader,0xff6c5040)==fill,"changed artwork immediately uses its new sampled color, independent of old cover and native highlight");
        check(Bitmap.reads==reads+9&&Bitmap.scales==scales&&clock.jobs.isEmpty(),"software artwork color reads exactly nine pixels, with no copy, blur, worker or wait");
        check(glow(appearance,card).images.isEmpty(),"obsolete album glow is removed immediately rather than lingering under the new cover");
        int creations=Bitmap.creations,schedules=clock.schedules,invalidations=card.invalidations,uploads=shader.uploadCount;reads=Bitmap.reads;
        for(int i=0;i<1000;i++){appearance.onNativeUpdate(card,"bindMediaDataInner");appearance.onCoverChanged(card.coverImg);draw(appearance,card,blur);}
        check(Bitmap.creations==creations&&Bitmap.reads==reads&&Bitmap.scales==scales&&clock.schedules==schedules
                        &&card.invalidations==invalidations&&shader.uploadCount==uploads,
                "1000 identical events during solid phase do not sample, blur, reschedule, upload, allocate or invalidate");
        clock.advance(QsMediaAppearance.DEBOUNCE_MS-1);check(rendered(draw(appearance,card,blur),0xff000000)==fill&&clock.jobs.isEmpty(),"heavy work still waits for genuine trailing quiet while the new color is already visible");
        clock.advance(1);check(clock.jobs.size()==1&&Bitmap.scales==scales+1,"exact quiet deadline captures once and queues one heavy worker");
        check(rendered(draw(appearance,card,blur),0xff000000)==fill,"worker computation cannot expose the previous album");
        clock.runJobs();shader=draw(appearance,card,blur);
        check(rendered(shader,0xff24405a)==fill&&shader.floats.get("c17MediaMix")[0]==0f,"prepared blur starts its fade from the exact new-content solid color");
        check(((BitmapShader)shader.inputs.get("c17MediaPrevious")).bitmap.getWidth()==1,"incoming fade retains only the one-pixel new-color fill as its previous input");
        int finishedPixel=pixel(shader);check(finishedPixel!=fill,"textured fixture differentiates new blurred artwork from its flat representative color");
        creations=Bitmap.creations;reads=Bitmap.reads;scales=Bitmap.scales;int nativeUploads=DrawableShader.baseUploads;
        clock.advance(120);shader=draw(appearance,card,blur);int middle=rendered(shader,0xff000000);
        check(middle!=fill&&middle!=finishedPixel,"incoming blur has a visible intermediate frame rather than an abrupt replacement");
        check(rendered(shader,0xff6c5040)==middle,"solid-to-blur transition never exposes a native/default high-light detour");
        GlowCanvas incoming=glow(appearance,card);check(incoming.images.size()==1&&incoming.alphas.get(0)>0,"new prepared glow fades in with the new artwork, without any old glow");
        int builds=com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt.builds;
        for(int i=0;i<1000;i++){appearance.onNativeUpdate(card,"onDrawableUpdate");draw(appearance,card,blur);}
        check(Bitmap.creations==creations&&Bitmap.reads==reads&&Bitmap.scales==scales&&DrawableShader.baseUploads==nativeUploads
                        &&com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt.builds==builds,
                "1000 fade frames reuse prepared caches and never resample, copy, filter, rebuild native effects or reupload");
        clock.advance(120);shader=draw(appearance,card,blur);
        check(rendered(shader,0xff000000)==finishedPixel&&clock.timers.isEmpty(),"incoming fade finishes in 240ms and stops all frame callbacks");
        check(((BitmapShader)shader.inputs.get("c17MediaPrevious")).bitmap==((BitmapShader)shader.inputs.get("c17MediaCover")).bitmap,"completed fade no longer retains the obsolete one-pixel transition shader input");
        schedules=clock.schedules;uploads=shader.uploadCount;invalidations=card.invalidations;
        for(int i=0;i<1000;i++){clock.advance(1);appearance.onNativeUpdate(card,"onDrawableUpdate");draw(appearance,card,blur);}
        check(clock.schedules==schedules&&shader.uploadCount==uploads&&card.invalidations==invalidations&&Bitmap.reads==reads,"1000 stable frames do not poll, upload, read, schedule or invalidate");
        card.coverImg.setImageDrawable(image(card,0xff00ff00));appearance.onCoverChanged(card.coverImg);clock.advance(QsMediaAppearance.DEBOUNCE_MS);clock.runJobs();clock.advance(80);
        int before=rendered(draw(appearance,card,blur),0xff000000);scales=Bitmap.scales;
        AutoBlurDrawable rebound=material(card);appearance.onNativeUpdate(card,"onDrawableUpdate");
        check(rendered(draw(appearance,card,rebound),0xff000000)==before&&Bitmap.scales==scales,"native engine rebinding preserves the current incoming blend without new sampling or blur");
        card.coverImg.setImageDrawable(image(card,0xffffff00));appearance.onCoverChanged(card.coverImg);
        check(rendered(draw(appearance,card,rebound),0xff000000)==0xffffff00&&clock.jobs.isEmpty(),"a genuinely newer artwork cancels the old fade and immediately displays its own yellow color");
        clock.advance(QsMediaAppearance.DEBOUNCE_MS);clock.runJobs();clock.advance(QsMediaAppearance.FADE_MS);
        check(rendered(draw(appearance,card,rebound),0xff000000)==0xffffff00&&clock.timers.isEmpty(),"latest artwork is the sole settled cache after debounce and incoming fade");
        creations=Bitmap.creations;reads=Bitmap.reads;scales=Bitmap.scales;
        settings.putFloat("qs_media_light_background_opacity",35);appearance.configure(settings);clock.advance(QsMediaAppearance.DEBOUNCE_MS);
        clock.advance(QsMediaTransition.DIRECT_MS);shader=draw(appearance,card,rebound);
        check(shader.floats.get("c17MediaOpacity")[0]==.35f&&Bitmap.creations==creations&&Bitmap.reads==reads&&Bitmap.scales==scales,"opacity-only updates reuse pixels and never repeat color sampling or preparation");
        appearance.releaseRuntime();
    }

    static void crossfadeCancellation()throws Throwable {
        for(int action=0;action<4;action++){
            Queue clock=new Queue();QsMediaAppearance appearance=new QsMediaAppearance(clock,clock);Card card=new Card();AutoBlurDrawable blur=material(card);
            card.coverImg.setImageDrawable(image(card,0xffff0000));appearance.onNativeUpdate(card,"onAttachedToWindow");appearance.configure(values(true));clock.run();
            card.coverImg.setImageDrawable(image(card,0xff0000ff));appearance.onCoverChanged(card.coverImg);clock.advance(QsMediaAppearance.DEBOUNCE_MS);clock.runJobs();clock.advance(64);
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
        card.coverImg.setImageDrawable(image(card,0xffff0000));appearance.onNativeUpdate(card,"onAttachedToWindow");Bundle settings=values(true);settings.putFloat("qs_media_light_background_opacity",100);appearance.configure(settings);clock.advance(QsMediaAppearance.DEBOUNCE_MS);clock.runJobs();
        check(rendered(draw(appearance,card,blur),0xff000000)==0xffff0000&&clock.timers.isEmpty(),"system animation scale zero commits directly without any frame scheduling");
        clock.animations=true;card.coverImg.setImageDrawable(image(card,0xff0000ff));appearance.onCoverChanged(card.coverImg);clock.advance(QsMediaAppearance.DEBOUNCE_MS);clock.runJobs();clock.advance(64);draw(appearance,card,blur);
        clock.animations=false;clock.advance(16);check(rendered(draw(appearance,card,blur),0xff000000)==0xff0000ff&&clock.timers.isEmpty(),"turning system animations off during a transition completes it on the next single frame");
        appearance.releaseRuntime();
    }

    static void transitionWeights() {
        check(QsMediaAppearance.DEBOUNCE_MS==1000L&&QsMediaAppearance.FADE_MS==240L,"new color is immediate, expensive processing is debounced, incoming fade is bounded");
        for(boolean solid:new boolean[]{true,false}) {
            long duration=solid?QsMediaTransition.IN_MS:QsMediaTransition.DIRECT_MS;float last=0f;
            for(long elapsed=0;elapsed<=duration;elapsed++) {
                float old=QsMediaTransition.previous(elapsed,solid),next=QsMediaTransition.next(elapsed,solid);
                check(old>=0f&&old<=1f&&next>=0f&&next<=1f&&Float.isFinite(old)&&Float.isFinite(next),"incoming weights remain finite and bounded");
                check(next>=last&&Math.abs(old+next-1f)<.00001f,"cached solid/blur weights are complementary and monotonic, with no native-only gap");
                last=next;
            }
            check(QsMediaTransition.next(0,solid)==0f&&QsMediaTransition.next(duration,solid)==1f,"both cache-only transitions have exact endpoints");
        }
        Bitmap software=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);software.eraseColor(0xff7722aa);
        int reads=Bitmap.reads,scales=Bitmap.scales;
        check(QsMediaColor.sample(software)==0xff7722aa&&Bitmap.reads==reads+9&&Bitmap.scales==scales,"software representative color uses at most nine bounded reads and no scaled bitmap");
        Bitmap hardware=Bitmap.createBitmap(4096,1,Bitmap.Config.HARDWARE);hardware.eraseColor(0xff1188dd);scales=Bitmap.scales;
        check(QsMediaColor.sample(hardware)==0xff1188dd&&Bitmap.scales==scales+1&&!hardware.isRecycled(),"hardware sampling scales to at most eight pixels per edge before bounded CPU readback, preserving the native source");
        software.eraseColor(0x00ffffff);check(QsMediaColor.sample(software)==0,"fully transparent artwork never invents a native or gray replacement color");
    }

    public static void main(String[] args)throws Throwable {steadyAndChanges();staleAndUnsupported();nativeWallpaperBranch();nativeFailureRecovery();pointValues();trailingDebounce();staleDebounceAndCancellation();debouncedSubEffects();nativeRebindingDebounce();lightweightCrossfade();crossfadeCancellation();transitionWeights();System.out.println("QsMediaAppearanceCheck passed: "+checks+" (native material and real pixels, immediate new-artwork color + trailing 1000ms preparation + cached 240ms blur fade and immediate cancellation)");}
}
