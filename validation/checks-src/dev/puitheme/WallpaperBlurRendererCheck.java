// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.PorterDuff;
import android.graphics.RenderNode;
import android.graphics.Shader;

/** Native GPU command/shader contracts; desktop checks do not claim visual or frame-rate validation. */
public final class WallpaperBlurRendererCheck {
    private static int checks;
    private static void check(boolean value,String scenario){checks++;if(!value)throw new AssertionError("Wallpaper renderer: "+scenario);}
    private static void equal(int expected,int actual,String scenario){check(expected==actual,scenario+" expected "+expected+", was "+actual);}
    private static void near(float expected,float actual,float tolerance,String scenario){check(Math.abs(expected-actual)<=tolerance,scenario+" expected "+expected+", was "+actual);}
    private static void reset(){RenderNode.nodes.clear();RenderNode.failBegin=false;RenderNode.failBeginOnce=null;android.graphics.RenderEffect.fail=false;}
    private static void gradients() {
        for(float density:new float[]{.25f,1f,3f,8f})for(float core:new float[]{0f,1f,56f,640f})for(float tail:new float[]{0f,1f,32f,320f}) {
            LockscreenBlurGeometry.Plan plan=LockscreenBlurGeometry.plan(3168,density,16f,core,tail);
            if(plan.end==0)continue;
            for(int color:new int[]{0xffffffff,0x80402010,0x00010203}) {
                LinearGradient shader=WallpaperBlurRenderer.gradient(plan,color);
                equal(0,Math.round(shader.left),"vertical feather begins at x=0");equal(0,Math.round(shader.right),"vertical feather ends at x=0");
                near(plan.end,shader.bottom,0f,"shader range matches configured output");check(shader.tileMode==Shader.TileMode.CLAMP,"shader clamps outside its range");
                near((color>>>24)/255f,shader.alpha(0,0),.0001f,"top retains configured alpha");
                for(int i=0;i<shader.colors.length;i++) {
                    equal(color&0xffffff,shader.colors[i]&0xffffff,"feather preserves RGB without tint shifts");
                    check(Float.isFinite(shader.positions[i])&&shader.positions[i]>=0f&&shader.positions[i]<=1f,"finite gradient stops remain in range");
                    if(i>0){check(shader.positions[i]>=shader.positions[i-1],"gradient positions are ordered");check((shader.colors[i]>>>24)<=(shader.colors[i-1]>>>24),"gradient opacity is monotone");}
                }
                if(plan.end>plan.core) {
                    near(0f,shader.alpha(0,plan.end),.0001f,"transition reaches fully clear at its endpoint");
                    float previous=shader.alpha(0,0);
                    for(int i=1;i<=257;i++) {
                        float y=plan.end*i/257f,actual=shader.alpha(0,y);
                        near((color>>>24)/255f*plan.strength(y),actual,2f/255f,"native interpolation follows smoothstep within alpha quantization");
                        check(actual<=previous+.00001f,"continuous feather never becomes stronger downward");previous=actual;
                    }
                    for(int i=1;i<shader.positions.length;i++) {
                        float y=shader.positions[i]*plan.end;
                        float epsilon=(plan.end-plan.core)*.00001f;
                        near(shader.alpha(0,y-epsilon),shader.alpha(0,y+epsilon),.0001f,"adjacent shader intervals join continuously");
                    }
                } else near((color>>>24)/255f,shader.alpha(0,plan.end),.0001f,"zero transition retains explicit solid range");
            }
        }
    }
    private static void gpuCommandsAndRelease() {
        reset();WallpaperBlurRenderer renderer=new WallpaperBlurRenderer();
        LockscreenBlurGeometry.Plan plan=LockscreenBlurGeometry.plan(3168,3f,16f,56f,32f);
        Bitmap source=Bitmap.createBitmap(1440,plan.end+plan.radius*3,Bitmap.Config.HARDWARE);
        int reads=Bitmap.reads,scales=Bitmap.scales,copies=Bitmap.copies;
        renderer.record(source,1440,plan,plan.radius,0x80402010);
        equal(2,RenderNode.nodes.size(),"one blurred wallpaper node and one feathered output node");
        RenderNode blur=RenderNode.nodes.get(0),output=RenderNode.nodes.get(1);
        check(blur.effect!=null&&blur.effect.tileMode==Shader.TileMode.CLAMP,"wallpaper pixels use the native GPU blur");near(48f,blur.effect.radius,0f,"native blur radius uses real density");
        equal(source.getHeight(),blur.height,"sampling halo is retained through blur");check(!blur.clipToBounds,"blur node can sample its halo");
        equal(plan.end,output.height,"final output stops at the configured range");check(output.clipToBounds,"final output cannot escape its crop");
        equal(1,blur.recording.bitmapDraws,"source is recorded once");check(blur.recording.drawnBitmapsForCheck.get(0)==source,"GPU source remains the captured hardware bitmap");
        equal(1,output.recording.nodeDraws,"one image effect is composed");equal(2,output.recording.rects,"continuous feather and tinted mask each draw once");
        equal(1,output.recording.layers,"DST_IN feather uses one compositing layer");check(output.recording.drawnNodesForCheck.get(0)==blur,"output references only isolated wallpaper blur");
        Canvas canvas=new Canvas();canvas.hardware=true;renderer.draw(canvas);equal(1,canvas.clears,"cached surface is cleared before posting");
        equal(0,canvas.lastClearColorForCheck,"clear removes stale opaque pixels");check(canvas.lastClearModeForCheck==PorterDuff.Mode.CLEAR,"clear uses native CLEAR mode");
        equal(1,canvas.nodeDraws,"one cached output node is drawn");check(canvas.drawnNodesForCheck.get(0)==output,"draw submits the feathered node");
        for(int i=0;i<100;i++)renderer.draw(canvas);
        equal(1,blur.recordings,"repeat output draws reuse recorded blur");equal(1,output.recordings,"repeat output draws reuse the continuous shader");
        equal(reads,Bitmap.reads,"no CPU pixel reads");equal(scales,Bitmap.scales,"no software bitmap resizing");equal(copies,Bitmap.copies,"no bitmap copies");
        renderer.release();check(blur.effect==null,"release removes native blur effect");check(blur.discards>=2&&output.discards>=2,"release discards both GPU display lists");
        check(!source.isRecycled(),"renderer borrows source lifetime from layer owner");
    }
    private static void zeroAndFailureContracts() {
        reset();WallpaperBlurRenderer renderer=new WallpaperBlurRenderer();LockscreenBlurGeometry.Plan plan=LockscreenBlurGeometry.plan(3168,3f,0f,56f,32f);
        renderer.record(null,1440,plan,0,0x80402010);RenderNode blur=RenderNode.nodes.get(0),output=RenderNode.nodes.get(1);
        check(blur.effect==null,"zero radius has no blur effect");equal(0,blur.recordings,"zero radius does not record a source bitmap");equal(1,output.recording.rects,"zero radius keeps only the requested mask");equal(0,output.recording.nodeDraws,"zero radius does not draw blurred pixels");
        renderer.record(null,1440,plan,0,0);equal(0,output.recording.rects,"zero mask and zero radius render no overlay content");
        Bitmap source=Bitmap.createBitmap(1440,plan.end+48*3,Bitmap.Config.HARDWARE);renderer.record(source,1440,plan,48,0);equal(1,output.recording.rects,"zero mask retains only the blur feather");check(output.recording.rectMode==PorterDuff.Mode.DST_IN,"blur feather uses native DST_IN");
        boolean rejected=false;try{renderer.draw(new Canvas());}catch(IllegalStateException expected){rejected=true;}check(rejected,"software canvas is rejected");
        rejected=false;try{renderer.record(null,1440,plan,48,0);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"positive radius requires an isolated source");
        source.recycle();rejected=false;try{renderer.record(source,1440,plan,48,0);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"recycled source cannot reach GPU recording");
        renderer.release();
    }
    public static void main(String[] args){gradients();gpuCommandsAndRelease();zeroAndFailureContracts();System.out.println(checks+" continuous wallpaper GPU renderer checks passed");}
}
