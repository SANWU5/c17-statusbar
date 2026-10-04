// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.RenderNode;
import android.view.Surface;
import android.window.ScreenCaptureInternal;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.View;
import com.android.systemui.wallpapers.ImageWallpaper;
import java.util.ArrayList;
import java.util.List;

/** Compositor contract checks; desktop fixtures do not render a wallpaper or capture screen content. */
public final class WallpaperBlurLayerCheck {
    private static int checks;
    private static final int MASK=0x80402010;

    public static final class WallpaperContext extends Context {
        final Resources resources=new Resources();
        final Display display=new Display();
        final WindowService window=new WindowService(display);
        boolean displayAvailable=true;
        WallpaperContext() {resources.getDisplayMetrics().density=3f;}
        @Override public Resources getResources() {return resources;}
        public Display getDisplay() {return displayAvailable?display:null;}
        @Override public Object getSystemService(String name) {return "window".equals(name)?window:super.getSystemService(name);}
    }
    /** Supplies the optional reflective window-manager fallback without altering Context defaults. */
    public static final class WindowService {
        final Display display;
        WindowService(Display display) {this.display=display;}
        public Display getDefaultDisplay() {return display;}
        public WindowMetrics getCurrentWindowMetrics() {return new WindowMetrics(display);}
    }
    public static final class WindowMetrics {
        final Display display;
        WindowMetrics(Display display) {this.display=display;}
        public Rect getBounds() {return new Rect(0,0,display.realWidth,display.realHeight);}
    }
    private static final class Fixture {
        final WallpaperContext context=new WallpaperContext();
        final ImageWallpaper.CanvasEngine engine=new ImageWallpaper.CanvasEngine(context);
        final SurfaceControl nativeRoot=new SurfaceControl("native wallpaper window",null);
        final SurfaceControl transform=new SurfaceControl("native wallpaper transform",nativeRoot);
        final SurfaceControl buffer=new SurfaceControl("native BLAST wallpaper buffer",transform);
        final WallpaperBlurLayer helper=new WallpaperBlurLayer();
        Fixture() {
            engine.mSurfaceControl=nativeRoot;engine.mTransformSurfaceControl=transform;engine.mBbqSurfaceControl=buffer;
            // The native wallpaper buffer and zoom wrapper are intentionally larger than the viewport.
            engine.mCurWidth=4000;engine.mCurHeight=8000;engine.mSurfaceSize.set(0,0,4000,8000);
            nativeRoot.alpha=.91f;transform.alpha=.81f;buffer.alpha=.37f;
        }
        void enable() {helper.configure(true,true,.75f,16f,56f,32f,MASK);helper.onEngine(engine);settle();}
        void event() {helper.onEngine(engine);settle();}
    }

    private static void check(boolean value,String scenario) {
        checks++;if(!value)throw new AssertionError("Wallpaper layer: "+scenario);
    }
    private static void equal(int expected,int actual,String scenario) {check(expected==actual,scenario+" expected "+expected+", was "+actual);}
    private static void near(float expected,float actual,String scenario) {check(Math.abs(expected-actual)<.0001f,scenario+" expected "+expected+", was "+actual);}
    private static void settle() {Handler.drainForCheck();}
    private static void reset() {
        SurfaceControl.resetForCheck();Surface.resetForCheck();ScreenCaptureInternal.resetForCheck();RenderNode.nodes.clear();
        RenderNode.failBegin=false;RenderNode.failBeginOnce=null;android.graphics.RenderEffect.fail=false;
        android.os.Process.uidForCheck=1000;Handler.postedForCheck.clear();Looper.resetCurrentForCheck();
    }
    private static List<SurfaceControl> owned() {
        List<SurfaceControl> result=new ArrayList<>();
        for(SurfaceControl value:SurfaceControl.createdForCheck)if(value.owned)result.add(value);
        return result;
    }
    private static List<SurfaceControl> activeOwned() {
        List<SurfaceControl> result=new ArrayList<>();
        for(SurfaceControl value:owned())if(value.valid)result.add(value);
        return result;
    }
    private static SurfaceControl group() {
        for(SurfaceControl value:activeOwned())if(value.kind==0)return value;
        throw new AssertionError("No owned wallpaper group");
    }
    private static int applied() {int result=0;for(SurfaceControl.Transaction t:SurfaceControl.transactionsForCheck)result+=t.applyCalls;return result;}
    private static int operations(String name,SurfaceControl target) {
        int result=0;for(SurfaceControl.Operation op:SurfaceControl.operationsForCheck)if(name.equals(op.name)&&op.target==target)result++;return result;
    }
    private static int captures(){return ScreenCaptureInternal.capturesForCheck.size();}
    private static int renders(){int count=0;for(Surface s:Surface.createdForCheck)count+=s.postCalls;return count;}
    private static void samplesScoped(Fixture f) {
        equal(0,ScreenCaptureInternal.invalidCaptureAttemptsForCheck,"invalid capture cannot be hidden by reflective fallback");
        for(ScreenCaptureInternal.LayerCaptureArgs sample:ScreenCaptureInternal.capturesForCheck) {
            check(!sample.parent.owned,"source is native wallpaper rather than our output");check(sample.childrenOnly,"source scope contains wallpaper children only");
            equal(1000,(int)sample.uid,"sampling uses the SystemUI UID");equal(1,sample.excluded.length,"owned effects are explicitly excluded");
            check(sample.excluded[0].owned&&sample.excluded[0].kind==0&&sample.excluded[0].parent==sample.parent,"whole own subtree excluded to prevent recursive blur");
        }
    }
    private static void gpuReleased() {
        for(Surface s:Surface.createdForCheck)equal(1,s.releaseCalls,"every owned canvas surface releases once");
        for(ScreenCaptureInternal.ScreenshotHardwareBuffer sample:ScreenCaptureInternal.resultsForCheck) {
            equal(1,sample.hardwareBuffer.closeCalls,"each GPU buffer closes once");
            if(sample.bitmap!=null){check(sample.bitmap.isRecycled(),"each acquired hardware bitmap releases");equal(1,sample.bitmap.recycleCalls,"each hardware bitmap recycles once");}
        }
        for(RenderNode node:RenderNode.nodes){check(node.discards>0,"GPU display list discarded on release");check(node.effect==null,"native blur effect released");}
    }
    private static void borrowedUntouched(Fixture f) {
        equal(0,SurfaceControl.borrowedMutationAttemptsForCheck,"borrowed mutation attempt cannot be hidden by reflective fallback");
        for(SurfaceControl.Operation op:SurfaceControl.operationsForCheck)if(op.target!=null)check(op.target.owned,"every transaction targets an owned child");
        near(.91f,f.nativeRoot.alpha,"wallpaper root alpha preserved");near(.81f,f.transform.alpha,"native transform alpha preserved");near(.37f,f.buffer.alpha,"native dim alpha preserved");
        for(SurfaceControl nativeHandle:new SurfaceControl[]{f.nativeRoot,f.transform,f.buffer}) {
            equal(0,nativeHandle.blurRadius,"native blur radius preserved");equal(0,nativeHandle.releaseCalls,"borrowed handle never released");
            equal(0,nativeHandle.removeCalls,"borrowed handle never removed");check(nativeHandle.valid,"borrowed handle remains valid");
        }
        for(SurfaceControl.Transaction t:SurfaceControl.transactionsForCheck)equal(1,t.closeCalls,"every created transaction closes exactly once");
        for(SurfaceControl.Operation op:SurfaceControl.operationsForCheck)check(!"blur".equals(op.name)&&!"color".equals(op.name),"GPU result never adds separate native blur/color bands");
    }
    private static void fullyReleased(List<SurfaceControl> values) {
        for(SurfaceControl value:values){check(!value.valid,"owned surface retired");equal(1,value.releaseCalls,"owned surface released once");}
    }

    private static void ownedViewportAndStableEvents() {
        reset();Fixture f=new Fixture();f.enable();SurfaceControl group=group();
        check(group.parent==f.nativeRoot,"group attaches to real wallpaper root");
        check(group.parent!=f.transform&&group.parent!=f.buffer,"group remains outside wallpaper zoom/buffer subtree");
        check(group.relativeTo==f.transform,"normal ordering anchors native transform");equal(1,group.relativeLayer,"relative sibling ordering is +1");
        check(new Rect(0,0,1440,3168).equals(group.crop),"crop is physical viewport, not 4000x8000 wallpaper buffer");
        near(0f,group.x,"normal viewport x");near(0f,group.y,"normal viewport y");check(!group.hidden,"owned group shown");
        LockscreenBlurGeometry.Plan plan=LockscreenBlurGeometry.plan(3168,3f,16f,56f,32f);
        equal(2,activeOwned().size(),"one group and one cached GPU buffer");
        for(SurfaceControl child:activeOwned())if(child!=group) {
            check(child.parent==group,"GPU buffer remains inside owned group");check(!child.hidden,"GPU buffer shown");
            equal(3,child.kind,"one native buffer replaces all bands");check(new Rect(0,0,1440,plan.end).equals(child.crop),"output covers configured upper wallpaper range");
            equal(1440,child.bufferWidth,"GPU buffer matches viewport width");equal(plan.end,child.bufferHeight,"GPU buffer height excludes sampling halo");
            equal(1,child.bufferFormat,"output buffer uses RGBA_8888");check(!child.opaque,"output buffer preserves continuous per-pixel alpha");
            equal(0,child.layer,"single output has stable ordering");near(1f,child.alpha,"shader already owns mask alpha");check(child.color==null,"output has no separate color fill");
        }
        near(.75f,group.alpha,"native progress is applied once to the group");equal(1,captures(),"initial image is sampled once");equal(1,renders(),"initial GPU result is recorded once");
        ScreenCaptureInternal.LayerCaptureArgs sample=ScreenCaptureInternal.capturesForCheck.get(0);
        check(sample.parent==f.nativeRoot,"sample root is exclusively the real wallpaper window");check(new Rect(0,0,1440,plan.end+plan.radius*3).equals(sample.crop),"sample covers upper wallpaper plus Gaussian halo");
        check(ScreenCaptureInternal.resultsForCheck.get(0).bitmap.getConfig()==Bitmap.Config.HARDWARE,"source stays a GPU hardware bitmap");
        int commits=applied(),builds=SurfaceControl.buildCallsForCheck,samples=captures(),posts=renders();
        for(int i=0;i<100;i++){f.event();f.helper.configure(true,true,.75f,16f,56f,32f,MASK);settle();}
        equal(commits,applied(),"unchanged native events do not commit duplicate transactions");equal(builds,SurfaceControl.buildCallsForCheck,"unchanged events do not create layers");
        equal(samples,captures(),"unchanged events never resample wallpaper");equal(posts,renders(),"unchanged events never rerender cached pixels");
        View foreground=new View(f.context);f.helper.onEngine(foreground);f.helper.onEngine(new WallpaperService.Engine(f.context));settle();
        f.helper.onNativeFrame(foreground);f.helper.onNativeFrame(new WallpaperService.Engine(f.context));settle();
        equal(commits,applied(),"foreground view and external base Engine cannot become wallpaper hosts");
        equal(samples,captures(),"foreground/base-engine frame events cannot become capture sources");equal(posts,renders(),"foreground/base-engine frame events never render pixels");
        equal(0,foreground.invalidations,"foreground View is not invalidated");equal(0,foreground.layoutRequests,"foreground View never relayouts");near(1f,foreground.getAlpha(),"foreground alpha preserved");
        borrowedUntouched(f);samplesScoped(f);List<SurfaceControl> created=owned();f.helper.releaseRuntime();settle();fullyReleased(created);gpuReleased();
    }

    private static void flagsPreviewAndVisibility() {
        reset();Fixture f=new Fixture();f.engine.wallpaperFlags=1;f.enable();equal(0,owned().size(),"system-only wallpaper flags=1 rejected");
        f.engine.wallpaperFlags=2;f.event();check(!group().hidden,"lock-only flags=2 accepted");
        f.engine.wallpaperFlags=3;int commits=applied();f.event();equal(commits,applied(),"combined flags=3 accepted without redundant write");
        f.engine.visible=false;f.event();check(group().hidden,"native invisible wallpaper hides only owned group");commits=applied();f.event();equal(commits,applied(),"repeated hidden event is stable");
        f.engine.visible=true;f.event();check(!group().hidden,"native visible wallpaper restores owned group");
        f.helper.configure(true,false,.75f,16f,56f,32f,MASK);settle();
        check(RenderNode.nodes.get(0).effect==null,"cross-window blur disabled retains mask with zero blur");
        int samples=captures();
        f.helper.configure(true,true,.75f,16f,56f,32f,MASK);settle();
        equal(samples+1,captures(),"re-enabling blur reacquires its released source");near(48f,RenderNode.nodes.get(0).effect.radius,"cross-window blur re-enables GPU radius");
        List<SurfaceControl> created=owned();f.engine.preview=true;f.event();fullyReleased(created);equal(0,activeOwned().size(),"preview retires owned wallpaper layers");
        f.engine.preview=false;f.event();check(!group().hidden,"normal wallpaper can rebuild after preview");
        borrowedUntouched(f);samplesScoped(f);f.helper.releaseRuntime();settle();fullyReleased(owned());gpuReleased();
    }

    private static void nativeSnapshots() {
        reset();Fixture f=new Fixture();f.enable();SurfaceControl group=group();int index=SurfaceControl.operationsForCheck.size();
        f.helper.beforeNativeSnapshot(f.engine);
        check(group.hidden,"owned group hidden before native capture");
        boolean hide=false,synchronous=false;
        for(int i=index;i<SurfaceControl.operationsForCheck.size();i++) {
            SurfaceControl.Operation op=SurfaceControl.operationsForCheck.get(i);
            if("hide".equals(op.name)&&op.target==group)hide=true;
            if("applySync".equals(op.name)){check(hide,"sync apply follows owned hide");synchronous=true;}
        }
        check(synchronous,"native screenshot exclusion uses synchronous transaction");
        int commits=applied();f.event();f.helper.configure(true,true,.5f,16f,56f,32f,MASK);settle();equal(commits,applied(),"capturing blocks re-show from native/settings events");
        SurfaceControl snapshot=new SurfaceControl("native frozen wallpaper screenshot",f.nativeRoot);snapshot.layer=Integer.MAX_VALUE;
        f.engine.mScreenshotSurfaceControl=snapshot;f.helper.afterNativeSnapshot(f.engine);settle();
        check(!group.hidden,"owned group restored after native screenshot");check(group.relativeTo==snapshot,"frozen wallpaper screenshot becomes relative anchor");equal(1,group.relativeLayer,"snapshot ordering uses relative +1 without integer overflow");
        equal(Integer.MAX_VALUE,snapshot.layer,"native snapshot maximum absolute layer untouched");equal(0,snapshot.releaseCalls,"native snapshot is borrowed");
        f.engine.visible=false;f.engine.mVisible=true;f.event();check(!group.hidden,"native frozen snapshot retains requested visibility when reported visibility is false");
        f.engine.mVisible=false;f.event();check(group.hidden,"frozen snapshot without requested visibility hides owned group");
        f.engine.visible=true;f.engine.mVisible=true;f.event();check(!group.hidden,"normal visibility restores frozen owned group");
        snapshot.valid=false;f.engine.mScreenshotSurfaceControl=null;f.event();check(group.relativeTo==f.transform,"native screenshot cleanup returns to transform anchor");
        f.helper.beforeNativeSnapshot(f.engine);check(group.hidden,"capture-failure path also hides owned effects");f.helper.afterNativeSnapshot(f.engine);settle();check(!group.hidden,"capture failure restores normal transform ordering");
        check(f.helper.summary().contains("nativeSnapshots=2"),"only real capture events increment snapshot diagnostics");
        borrowedUntouched(f);f.helper.releaseRuntime();settle();fullyReleased(owned());
    }

    private static void disableDestroyAndParentReplacement() {
        reset();Fixture f=new Fixture();f.event();equal(0,owned().size(),"default disabled creates no surface");f.enable();
        List<SurfaceControl> first=owned();f.helper.configure(false,true,.75f,16f,56f,32f,MASK);settle();fullyReleased(first);
        int commits=applied();f.helper.configure(false,true,.75f,16f,56f,32f,MASK);f.event();equal(commits,applied(),"repeated disabled events commit nothing");
        f.helper.configure(true,true,.75f,16f,56f,32f,MASK);settle();check(group().valid,"enabling restores a fresh owned group");
        List<SurfaceControl> old=activeOwned();SurfaceControl replacement=new SurfaceControl("replacement native wallpaper root",null);
        SurfaceControl replacementTransform=new SurfaceControl("replacement native transform",replacement);
        f.engine.mSurfaceControl=replacement;f.engine.mTransformSurfaceControl=replacementTransform;f.event();fullyReleased(old);
        check(group().parent==replacement&&group().relativeTo==replacementTransform,"native parent generation replaces only owned subtree");
        equal(0,replacement.releaseCalls,"new native parent remains borrowed");equal(0,replacementTransform.releaseCalls,"new transform remains borrowed");
        f.helper.onDestroyed(f.engine);settle();fullyReleased(owned());equal(0,activeOwned().size(),"engine destruction releases all owned surfaces");
        check(f.helper.summary().contains("wallpaperEngines=0"),"destroyed engine removed from registry");commits=applied();f.helper.onDestroyed(f.engine);equal(commits,applied(),"repeated destroy is stable");
        borrowedUntouched(f);f.helper.releaseRuntime();settle();
    }

    private static void unsupportedIsStable() {
        reset();Fixture f=new Fixture();SurfaceControl.failBuildAtForCheck=2;f.enable();
        equal(0,activeOwned().size(),"partial build failure releases every created owned handle");fullyReleased(owned());
        int attempts=SurfaceControl.buildCallsForCheck,commits=applied();
        for(int i=0;i<100;i++)f.event();
        equal(attempts,SurfaceControl.buildCallsForCheck,"unsupported parent does not retry building on every callback");equal(commits,applied(),"unsupported parent does not queue removal transactions repeatedly");
        check(f.helper.summary().contains("nativeFallback=1"),"unsupported native contract counted once per parent");
        SurfaceControl replacement=new SurfaceControl("new supported wallpaper generation",null);
        f.engine.mSurfaceControl=replacement;f.engine.mTransformSurfaceControl=new SurfaceControl("new transform",replacement);f.event();
        check(group().parent==replacement,"new parent generation may recover once");borrowedUntouched(f);
        f.helper.releaseRuntime();settle();fullyReleased(owned());
    }

    private static void viewportChangesAndCoordinates() {
        reset();Fixture f=new Fixture();f.enable();SurfaceControl group=group();
        f.context.display.realWidth=3168;f.context.display.realHeight=1440;f.context.display.rotation=1;
        f.engine.mWidth=f.engine.mLayout.width=3168;f.engine.mHeight=f.engine.mLayout.height=1440;f.engine.mWinFrames.frame.set(0,0,3168,1440);f.event();
        group=group();
        check(new Rect(0,0,3168,1440).equals(group.crop),"rotation rebuilds physical viewport crop");
        for(SurfaceControl value:activeOwned())if(value!=group)check(value.crop.right==3168&&value.crop.bottom<=1440,"rotated GPU output remains inside viewport");
        f.engine.mWinFrames.frame.set(-30,-40,3138,1400);f.event();check(group.hidden,"unknown nonzero native frame origin rejects compositor mapping");
        f.engine.mWinFrames.frame.set(0,0,3168,1440);f.event();check(!group.hidden,"physical zero-origin frame restores mapping");near(0f,group.x,"strict identity viewport x");near(0f,group.y,"strict identity viewport y");
        f.context.display.realWidth=0;f.event();check(group.hidden,"unknown physical size hides owned effects");
        f.context.display.realWidth=3168;f.event();check(!group.hidden,"valid physical size restores owned effects");
        f.context.display.displayId=1;f.event();check(group.hidden,"non-default physical display is unsupported");
        f.context.display.displayId=0;f.event();check(!group.hidden,"default display restores same parent without sticky fallback");
        WallpaperService.ClientWindowFrames frames=f.engine.mWinFrames;f.engine.mWinFrames=null;f.event();check(group.hidden,"missing native frame does not guess compositor mapping");
        f.engine.mWinFrames=frames;f.event();check(!group.hidden,"restored frame mapping reuses native parent");
        Rect frame=new Rect(frames.frame);frames.frame.setEmpty();f.event();check(group.hidden,"empty native frame hides owned effects");
        frames.frame.set(frame.left,frame.top,frame.right,frame.bottom);f.event();check(!group.hidden,"valid native frame restores mapping");
        f.engine.mLayout.surfaceInsets.set(1,0,0,0);f.event();check(group.hidden,"unknown nonzero surface insets do not shift blur into foreground");
        f.engine.mLayout.surfaceInsets.setEmpty();f.event();check(!group.hidden,"zero insets restore compositor mapping");
        f.engine.mLayout.flags|=0x4000;f.engine.mLayout.width=1000;f.event();check(group.hidden,"native FLAG_SCALED layout/requested mismatch is not treated as identity");
        f.engine.mLayout.width=f.engine.mWidth;f.event();check(!group.hidden,"FLAG_SCALED identity mapping works with a larger native wallpaper buffer");
        f.engine.zoomOut=true;f.event();check(group.hidden,"native wallpaper zoom-out capability hides unknown mapping");
        f.engine.zoomOut=false;f.event();check(!group.hidden,"zoom-out disabled restores strict identity mapping");
        f.engine.mWinFrames.frame.right--;f.event();check(group.hidden,"native frame/physical viewport mismatch hides during rotation transition");
        f.engine.mWinFrames.frame.right++;f.event();check(!group.hidden,"rotation mapping can recover on the same native parent");
        borrowedUntouched(f);f.helper.releaseRuntime();settle();fullyReleased(owned());
    }

    private static void cachedContentAndNativeFrames() {
        reset();Fixture f=new Fixture();f.enable();int samples=captures(),posts=renders();
        int reads=Bitmap.reads,scales=Bitmap.scales,copies=Bitmap.copies;
        for(int i=1;i<=20;i++){f.helper.configure(true,true,i/20f,16f,56f,32f,MASK);settle();}
        equal(samples,captures(),"native visibility animation never resamples source");equal(posts,renders(),"native visibility animation only updates compositor alpha");near(1f,group().alpha,"final native visibility is exact");
        f.helper.configure(true,true,1f,16f,56f,32f,0x60403020);settle();
        equal(samples,captures(),"mask change reuses the isolated hardware bitmap");equal(++posts,renders(),"mask change records one updated continuous shader");
        f.helper.configure(true,true,1f,8f,56f,32f,0x60403020);settle();
        equal(samples,captures(),"smaller radius reuses existing sampling halo");equal(++posts,renders(),"radius change rebuilds GPU commands once");
        f.helper.configure(true,true,1f,20f,56f,32f,0x60403020);settle();
        equal(++samples,captures(),"larger radius samples only when its halo exceeds cached pixels");equal(++posts,renders(),"larger radius rerenders once");
        f.helper.onNativeFrame(f.engine);settle();equal(++samples,captures(),"one posted native frame samples once");equal(++posts,renders(),"one posted native frame rerenders once");
        for(int i=0;i<20;i++)f.event();equal(samples,captures(),"metadata callbacks after native frame reuse cache");equal(posts,renders(),"metadata callbacks after native frame do no GPU work");
        f.helper.configure(true,true,0f,20f,56f,32f,0x60403020);settle();check(group().hidden,"zero native progress hides owned output");
        f.helper.onNativeFrame(f.engine);settle();equal(samples,captures(),"hidden wallpaper frame marks cache dirty without sampling");equal(posts,renders(),"hidden wallpaper frame records no GPU work");
        f.helper.configure(true,true,.5f,20f,56f,32f,0x60403020);settle();equal(++samples,captures(),"show consumes the pending wallpaper change once");equal(++posts,renders(),"show renders the pending wallpaper change once");
        equal(reads,Bitmap.reads,"GPU cache never reads CPU pixels");equal(scales,Bitmap.scales,"GPU cache never software-resizes source");equal(copies,Bitmap.copies,"GPU cache never copies source bitmaps");
        samplesScoped(f);borrowedUntouched(f);f.helper.releaseRuntime();settle();fullyReleased(owned());gpuReleased();

        reset();f=new Fixture();f.helper.configure(true,true,.75f,16f,56f,32f,MASK);
        f.helper.onNativeFrame(f.engine);settle();equal(1,captures(),"first native frame registers and samples exactly once");equal(1,renders(),"first native frame renders exactly once");
        f.helper.releaseRuntime();fullyReleased(owned());gpuReleased();
    }

    private static void zeroContentAndSourceFailures() {
        reset();Fixture f=new Fixture();f.helper.configure(true,true,.75f,0f,56f,32f,MASK);f.event();
        equal(0,captures(),"zero radius needs no wallpaper pixels");equal(1,renders(),"zero radius renders requested mask once");
        f.helper.configure(true,true,.75f,0f,56f,32f,0);settle();equal(0,captures(),"transparent mask and zero blur never sample");
        for(RenderNode node:RenderNode.nodes)if(node.name.contains("feather"))equal(0,node.recording.rects,"zero mask/blur posts transparent output");
        f.helper.releaseRuntime();fullyReleased(owned());gpuReleased();
        for(int failure=0;failure<8;failure++) {
            reset();f=new Fixture();
            switch(failure) {
                case 0:ScreenCaptureInternal.returnNullForCheck=true;break;
                case 1:ScreenCaptureInternal.secureForCheck=true;break;
                case 2:ScreenCaptureInternal.hdrForCheck=true;break;
                case 3:ScreenCaptureInternal.softwareForCheck=true;break;
                case 4:ScreenCaptureInternal.nullBitmapForCheck=true;break;
                case 5:ScreenCaptureInternal.widthDeltaForCheck=-1;break;
                case 6:ScreenCaptureInternal.heightDeltaForCheck=-1;break;
                case 7:Surface.softwareCanvasForCheck=true;break;
                default:throw new AssertionError("unknown failure");
            }
            f.enable();equal(0,activeOwned().size(),"unsupported source retires owned resources failure="+failure);
            int samples=captures(),posts=renders(),builds=SurfaceControl.buildCallsForCheck;
            for(int i=0;i<20;i++)f.event();equal(samples,captures(),"source failure is stable without capture polling failure="+failure);
            equal(posts,renders(),"source failure is stable without render polling failure="+failure);equal(builds,SurfaceControl.buildCallsForCheck,"source failure does not rebuild each callback failure="+failure);
            check(f.helper.summary().contains("nativeFallback=1"),"source failure recorded once per native generation");
            samplesScoped(f);borrowedUntouched(f);f.helper.releaseRuntime();fullyReleased(owned());gpuReleased();
        }
    }

    public static void main(String[] args) {
        ownedViewportAndStableEvents();flagsPreviewAndVisibility();nativeSnapshots();
        disableDestroyAndParentReplacement();unsupportedIsStable();viewportChangesAndCoordinates();
        cachedContentAndNativeFrames();zeroContentAndSourceFailures();
        System.out.println(checks+" wallpaper-owned cached GPU layer checks passed");
    }
}
