package dev.puitheme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import com.android.systemui.statusbar.StatusBarIconView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Actual StatusBarModule resource wrapper and hint adapter; native geometry stays observable. */
public final class IconStatusGeometryCheck {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void near(float expected,float actual,String message){check(Math.abs(expected-actual)<.0001f,message+": "+expected+" != "+actual);}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static class NativeGlyph extends Drawable {
        final int width,height;int draws,alpha=255,jumps,direction;ColorFilter filter;boolean mirror,fail;
        float x,y,drawX,drawY,drawWidth,drawHeight;Rect hotspot=new Rect();
        NativeGlyph(int width,int height){this.width=width;this.height=height;}
        @Override public void draw(Canvas canvas){draws++;Rect r=getBounds();Transform c=(Transform)canvas;drawX=c.x(r.exactCenterX());drawY=c.y(r.exactCenterY());drawWidth=r.width()*c.sx;drawHeight=r.height()*c.sy;if(fail)throw new IllegalStateException("native drawing failed");}
        @Override public void setAlpha(int value){alpha=value;}@Override public int getAlpha(){return alpha;}
        @Override public void setColorFilter(ColorFilter value){filter=value;}@Override public ColorFilter getColorFilter(){return filter;}
        @Override public int getIntrinsicWidth(){return width;}@Override public int getIntrinsicHeight(){return height;}@Override public int getOpacity(){return -3;}
        @Override public boolean onLayoutDirectionChanged(int value){direction=value;return true;}
        @Override public void setAutoMirrored(boolean value){mirror=value;}@Override public boolean isAutoMirrored(){return mirror;}
        @Override public void setHotspot(float a,float b){x=a;y=b;}
        @Override public void setHotspotBounds(int l,int t,int r,int b){hotspot.set(l,t,r,b);}
        @Override public void getHotspotBounds(Rect out){out.set(hotspot);}
        @Override public void jumpToCurrentState(){jumps++;}
    }
    private static final class AnimatedGlyph extends NativeGlyph implements android.graphics.drawable.Animatable {
        boolean running;AnimatedGlyph(){super(25,19);}public void start(){running=true;}public void stop(){running=false;}public boolean isRunning(){return running;}
    }
    /** Android Canvas post-concatenation; no fixture's non-restoring scalar counters. */
    private static final class Transform extends Canvas {
        float sx=1,sy=1,tx,ty;final ArrayDeque<float[]> saved=new ArrayDeque<>();
        int imageAlpha=-1;ColorFilter imageFilter;float imageX,imageY,imageWidth,imageHeight;
        @Override public int save(){saved.push(new float[]{sx,sy,tx,ty});return super.save();}
        @Override public void restoreToCount(int value){while(getSaveCount()>value){float[] p=saved.pop();sx=p[0];sy=p[1];tx=p[2];ty=p[3];super.restoreToCount(getSaveCount()-1);}}
        @Override public void translate(float x,float y){tx+=sx*x;ty+=sy*y;}
        @Override public void scale(float x,float y){sx*=x;sy*=y;}
        @Override public void scale(float x,float y,float px,float py){translate(px,py);scale(x,y);translate(-px,-py);}
        @Override public void drawBitmap(Bitmap bitmap,Rect source,RectF target,Paint paint){imageAlpha=paint.getAlpha();imageFilter=paint.getColorFilter();imageX=x(target.centerX());imageY=y(target.centerY());imageWidth=target.width()*sx;imageHeight=target.height()*sy;super.drawBitmap(bitmap,source,target,paint);}
        float x(float value){return sx*value+tx;}float y(float value){return sy*value+ty;}
    }
    public static final class Hint extends StatusBarIconView {Hint(){super(new Context());}public String getSlot(){return "alarm_clock";}}
    private static Drawable wrapper(NativeGlyph styled,NativeGlyph original,boolean wifi)throws Exception{
        Class<?> type=Class.forName("dev.puitheme.StatusBarModule$ScaledDrawable");Constructor<?> ctor=type.getDeclaredConstructor(Drawable.class,Resources.class,boolean.class,Drawable.class);ctor.setAccessible(true);
        Drawable result=(Drawable)ctor.newInstance(styled,Resources.getSystem(),wifi,original);field(type,"sourceName").set(result,wifi?"stat_signal_wifi_signal_4":"stat_signal_lte_signal_4");return result;
    }
    private static void features(boolean icon)throws Exception{
        Map<String,Object> values=new HashMap<>();for(String group:new String[]{"wifi","data"}){values.put(group+"_enabled",true);values.put(group+"_icon_enabled",icon);values.put(group+"_color_enabled",false);}
        field(StatusBarModule.class,"FEATURES").set(null,FeatureOptions.from(values));
        for(String name:new String[]{"wifiOffsetXdp","wifiOffsetYdp","dataOffsetXdp","dataOffsetYdp"})field(StatusBarModule.class,name).setFloat(null,0);
        for(String name:new String[]{"wifiIconScalePercent","dataIconScalePercent"})field(StatusBarModule.class,name).setFloat(null,100);
    }
    private static void centers()throws Exception{
        features(true);
        for(boolean compose:new boolean[]{false,true})for(boolean moved:new boolean[]{false,true})for(boolean wifi:new boolean[]{true,false})for(int width:new int[]{24,25})for(int height:new int[]{18,19})for(int outer:new int[]{60,61}){
            NativeGlyph glyph=new NativeGlyph(width,height);Drawable icon=wrapper(glyph,new NativeGlyph(30,22),wifi);icon.setBounds(3,7,3+outer,7+outer);
            field(icon.getClass(),"composePlacement").setBoolean(icon,compose);String group=wifi?"wifi":"data";field(StatusBarModule.class,group+"OffsetXdp").setFloat(null,moved?2.25f:0);field(StatusBarModule.class,group+"OffsetYdp").setFloat(null,moved?-3.5f:0);field(StatusBarModule.class,group+"IconScalePercent").setFloat(null,moved?125:100);
            float density=Resources.getSystem().getDisplayMetrics().density;
            Transform canvas=new Transform();canvas.translate(100.25f,50.75f);canvas.scale(1.25f,.75f);float expectedX=canvas.x(icon.getBounds().exactCenterX()+(!compose&&moved?2.25f*density:0)),expectedY=canvas.y(icon.getBounds().exactCenterY()+(!compose&&moved?-3.5f*density:0));
            icon.draw(canvas);near(expectedX,glyph.drawX,"glyph center X, odd native/outer dimensions");near(expectedY,glyph.drawY,"glyph center Y, odd native/outer dimensions");near(100.25f,canvas.tx,"draw restores caller X");near(50.75f,canvas.ty,"draw restores caller Y");check(canvas.getSaveCount()==1,"balanced draw stack");
        }
    }
    private static void animatedSources()throws Exception{
        AnimatedGlyph glyph=new AnimatedGlyph();check(new IconPackDrawing().wrapHint(new Hint(),null,glyph)==glyph,"hint preserves native animated runtime type");
        java.lang.reflect.Method replace=StatusBarModule.class.getDeclaredMethod("replacementDrawable",Resources.class,int.class,Drawable.class);replace.setAccessible(true);
        check(replace.invoke(null,Resources.getSystem(),1,glyph)==null,"animated native signal is not wrapped");
    }
    private static final class FilesContext extends Context {
        final java.nio.file.Path directory;final String name;android.content.ContentResolver provider;
        FilesContext(java.nio.file.Path directory,String name){this.directory=directory;this.name=name;}
        @Override public java.io.File getFilesDir(){return directory.toFile();}@Override public String getPackageName(){return name;}@Override public android.content.ContentResolver getContentResolver(){return provider;}
    }
    /** Built-in real ZIP -> repository validation -> provider bytes -> production worker -> renderer. */
    private static final class RealAssets implements AutoCloseable {
        final java.nio.file.Path directory;final FilesContext module,system;final IconManagementRepository.Pack pack;String records;
        RealAssets(String asset)throws Exception{
            IconManagementRuntime.releaseRuntime();settle();ResidentCache.current=null;directory=java.nio.file.Files.createTempDirectory("c17-status-geometry-");
            module=new FilesContext(java.nio.file.Files.createDirectory(directory.resolve("app")),ModuleDiagnostics.PACKAGE_NAME);system=new FilesContext(java.nio.file.Files.createDirectory(directory.resolve("sys")),"com.android.systemui");
            module.provider=system.provider=new android.content.ContentResolver(){@Override public java.io.InputStream openInputStream(android.net.Uri uri)throws java.io.FileNotFoundException{try{return new java.io.FileInputStream(IconManagementRepository.providerFile(module,uri));}catch(Exception error){throw new java.io.FileNotFoundException(error.toString());}}};
            try(IconManagementRepository.PreparedPack prepared=IconManagementRepository.prepare(module,new java.io.File("app/src/main/assets/icon-management-v3/"+asset+".zip"),"")){pack=prepared.commit(()->true);}
            records=IconManagementSettings.applyPack(IconManagementSettings.DEFAULT,pack.id,java.util.Arrays.asList("wifi","cellular","hint"),true);configure(records);
        }
        void configure(String value)throws Exception{Bundle b=new Bundle();b.putString(IconManagementSettings.KEY,value);IconManagementRuntime.configure(system,b);settle();records=value;}
        public void close()throws Exception{IconManagementRuntime.releaseRuntime();settle();IconManagementRepository.clear(module);try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(directory)){for(java.nio.file.Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.deleteIfExists(p);}}
    }
    // Windows reports no file key, so the repository's verified-pack cache never hits on a desktop and every provider read
    // re-hashes the real pack; Android's inode keys keep it cached. The bound only catches a real hang.
    private static void settle()throws Exception{java.util.concurrent.ExecutorService work=(java.util.concurrent.ExecutorService)field(IconManagementRuntime.class,"WORK").get(null);for(int n=0;n<3;n++){work.submit(()->{}).get(600,java.util.concurrent.TimeUnit.SECONDS);android.os.Handler.drainForCheck();}}
    private static void realBitmap()throws Exception{
        features(true);try(RealAssets assets=new RealAssets("c17-rounded-27")){
            check(IconManagementRuntime.hasImage("wifi.4"),"real artwork loaded through provider");
            for(boolean hint:new boolean[]{false,true})for(boolean compose:new boolean[]{false,true}){
                NativeGlyph glyph=new NativeGlyph(25,19);Drawable icon=hint?new IconPackDrawing().wrapHint(new Hint(),null,glyph):wrapper(glyph,new NativeGlyph(30,22),true);icon.setBounds(3,7,64,68);if(!hint)field(icon.getClass(),"composePlacement").setBoolean(icon,compose);
                glyph.setAlpha(73);ColorFilter filter=new android.graphics.PorterDuffColorFilter(0xff557799,android.graphics.PorterDuff.Mode.SRC_IN);glyph.setColorFilter(filter);Transform canvas=new Transform();canvas.translate(100.25f,50.75f);canvas.scale(1.25f,.75f);float x=canvas.x(33.5f),y=canvas.y(37.5f);icon.draw(canvas);
                check(canvas.bitmapDraws==1&&glyph.draws==0,"real bitmap takes replacement branch");near(x,canvas.imageX,"real bitmap keeps exact host center X");near(y,canvas.imageY,"real bitmap keeps exact host center Y");check(canvas.imageAlpha==73&&canvas.imageFilter==filter,"actual bitmap follows live native alpha/filter");
            }
        }
    }
    private static void releaseBitmap()throws Exception{
        try(RealAssets assets=new RealAssets("c17-rounded-27")){
            IconPackDrawing helper=new IconPackDrawing();NativeGlyph nativeGlyph=new NativeGlyph(25,19);Drawable hint=helper.wrapHint(new Hint(),null,nativeGlyph);hint.setBounds(0,0,40,40);Transform canvas=new Transform();hint.draw(canvas);
            check(canvas.bitmapDraws==1&&nativeGlyph.draws==0,"active real hint draws replacement");helper.releaseRuntime();hint.draw(canvas);
            check(canvas.bitmapDraws==1&&nativeGlyph.draws==1,"released helper retains real native hint while global cache remains active");
        }
    }
    private static void composePlacement()throws Exception{
        features(true);try(RealAssets assets=new RealAssets("c17-rounded-27")){
            NativeGlyph glyph=new NativeGlyph(25,19);Drawable icon=wrapper(glyph,new NativeGlyph(25,19),true);icon.setBounds(0,0,25,19);field(icon.getClass(),"composePlacement").setBoolean(icon,true);
            Transform normal=new Transform();icon.draw(normal);float width=normal.imageWidth,height=normal.imageHeight;
            assets.configure(IconManagementSettings.applyAppearance(assets.records,"target:wifi",new IconManagementSettings.Appearance("inherit",0xff000000,0xffffffff,null,150f)));
            Transform inside=new Transform();icon.draw(inside);near(width,inside.imageWidth,"Compose leaves v3 size outside native clip");near(height,inside.imageHeight,"Compose has no second inner v3 scale");
            java.lang.reflect.Method placement=icon.getClass().getDeclaredMethod("composeScale",float.class);placement.setAccessible(true);float external=(Float)placement.invoke(icon,25f);near(1.5f,external,"v3 scale belongs to actual outer placement helper");
            Transform nativeClip=new Transform();nativeClip.scale(external,external,12.5f,9.5f);float left=nativeClip.x(0),right=nativeClip.x(25),top=nativeClip.y(0),bottom=nativeClip.y(19);icon.draw(nativeClip);
            near(width*1.5f,nativeClip.imageWidth,"overall scale applies once");check(nativeClip.imageX-nativeClip.imageWidth*.5f>=left-.001f&&nativeClip.imageX+nativeClip.imageWidth*.5f<=right+.001f&&nativeClip.imageY-nativeClip.imageHeight*.5f>=top-.001f&&nativeClip.imageY+nativeClip.imageHeight*.5f<=bottom+.001f,"native Image clip moves with enlarged artwork");
            field(StatusBarModule.class,"wifiIconScalePercent").setFloat(null,125);near(1.875f,(Float)placement.invoke(icon,25f),"legacy and v3 size compose once in same outer layer");
            Map<String,Object> off=new HashMap<>();off.put("wifi_enabled",false);field(StatusBarModule.class,"FEATURES").set(null,FeatureOptions.from(off));near(1.5f,(Float)placement.invoke(icon,25f),"independent v3 size works when legacy controls are off");features(true);
            assets.configure(IconManagementSettings.applyTarget(assets.records,"wifi",java.util.Collections.singletonMap("wifi.4",new IconManagementSettings.Override("system",null,null)),new IconManagementSettings.Appearance("inherit",0xff000000,0xffffffff,null,150f),java.util.Collections.emptyMap(),true));
            NativeGlyph original=new NativeGlyph(25,19);icon=wrapper(new NativeGlyph(25,19),original,true);field(icon.getClass(),"composePlacement").setBoolean(icon,true);icon.setBounds(0,0,25,19);Transform originalInside=new Transform();icon.draw(originalInside);near(25,original.drawWidth,"native system glyph also omits internal size");near(19,original.drawHeight,"native system glyph keeps original draw frame");
            assets.configure(IconManagementSettings.applyAppearance(assets.records,"target:wifi",new IconManagementSettings.Appearance("inherit",0xff000000,0xffffffff,null,100f)));near(1f,(Float)placement.invoke(icon,25f),"size reset restores exact native placement");
        }
    }
    private static void exceptions()throws Exception{
        features(true);NativeGlyph glyph=new NativeGlyph(25,19);glyph.fail=true;Drawable icon=wrapper(glyph,new NativeGlyph(30,22),true);icon.setBounds(4,8,65,69);Transform canvas=new Transform();canvas.translate(10,20);
        try{icon.draw(canvas);throw new AssertionError("missing native failure");}catch(IllegalStateException expected){check("native drawing failed".equals(expected.getMessage()),"native exception preserved");}
        check(canvas.getSaveCount()==1,"native exception restores wrapper Canvas");near(10,canvas.tx,"native exception restores X");near(20,canvas.ty,"native exception restores Y");
    }
    private static void lifecycle()throws Exception{
        features(true);
        for(boolean hint:new boolean[]{false,true}){
            NativeGlyph nativeGlyph=new NativeGlyph(25,19),original=new NativeGlyph(30,22);Drawable icon=hint?new IconPackDrawing().wrapHint(new Hint(),null,nativeGlyph):wrapper(nativeGlyph,original,true);
            icon.setVisible(false,true);check(!nativeGlyph.isVisible(),"native visibility forwarded "+hint);icon.setLayoutDirection(1);check(nativeGlyph.direction==1,"native RTL forwarded "+hint);
            icon.setAutoMirrored(true);check(nativeGlyph.mirror&&icon.isAutoMirrored(),"native mirror forwarded "+hint);icon.setHotspot(3.5f,4.5f);near(3.5f,nativeGlyph.x,"hotspot X");near(4.5f,nativeGlyph.y,"hotspot Y");
            icon.setHotspotBounds(2,3,20,30);Rect out=new Rect();icon.getHotspotBounds(out);check(out.equals(nativeGlyph.hotspot)&&out.equals(new Rect(2,3,20,30)),"hotspot bounds forwarded");icon.jumpToCurrentState();check(nativeGlyph.jumps==1,"jump forwarded "+hint);
            nativeGlyph.setAlpha(73);ColorFilter changed=new ColorFilter();nativeGlyph.setColorFilter(changed);check(icon.getAlpha()==73,"live native alpha visible "+hint);check(icon.getColorFilter()==changed,"live native filter visible "+hint);
            if(!hint){features(false);icon.draw(new Transform());check(!original.isVisible()&&original.direction==1&&original.mirror,"delegate switch retains interaction state");near(3.5f,original.x,"delegate switch retains hotspot");check(original.hotspot.equals(out),"delegate switch retains hotspot bounds");check(original.getAlpha()==73&&original.getColorFilter()==changed,"delegate switch retains live native alpha and filter");
                ColorFilter returned=new ColorFilter();original.setAlpha(49);original.setColorFilter(returned);features(true);icon.draw(new Transform());check(nativeGlyph.getAlpha()==49&&nativeGlyph.getColorFilter()==returned,"return switch retains newly changed native alpha and filter");}
            features(true);
        }
    }
    private static void initialState()throws Exception{
        features(true);NativeGlyph original=new NativeGlyph(30,22),styled=new NativeGlyph(25,19);ColorFilter filter=new ColorFilter();original.setAlpha(57);original.setColorFilter(filter);original.setVisible(false,false);original.setLayoutDirection(1);original.setState(new int[]{7,11});original.setLevel(42);
        Drawable icon=wrapper(styled,original,true);check(icon.getAlpha()==57&&icon.getColorFilter()==filter,"constructor preserves real native alpha and filter");check(!icon.isVisible()&&!styled.isVisible()&&icon.getLayoutDirection()==1&&styled.direction==1,"constructor preserves real native visibility and direction");
        check(java.util.Arrays.equals(icon.getState(),new int[]{7,11})&&icon.getLevel()==42,"constructor preserves real native state and level");
        features(false);icon.draw(new Transform());check(original.getAlpha()==57&&original.getColorFilter()==filter&&!original.isVisible()&&original.direction==1,"first native restore retains initial native state");
        check(java.util.Arrays.equals(original.getState(),new int[]{7,11})&&original.getLevel()==42,"first native restore retains initial state and level");
        features(true);original=new NativeGlyph(30,22);icon=wrapper(new NativeGlyph(25,19),original,true);ColorFilter live=new ColorFilter();NativeGlyph delegate=(NativeGlyph)field(icon.getClass(),"delegate").get(icon);delegate.setAlpha(63);delegate.setColorFilter(live);
        Map<String,Object> colored=new HashMap<>();colored.put("wifi_enabled",true);colored.put("wifi_color_enabled",true);field(StatusBarModule.class,"FEATURES").set(null,FeatureOptions.from(colored));icon.draw(new Transform());check(delegate.getColorFilter()!=live,"real color path temporarily applies custom filter");features(false);icon.draw(new Transform());check(original.getAlpha()==63&&original.getColorFilter()==live,"switch with custom color restores current native filter, not custom tint");features(true);icon.draw(new Transform());check(delegate.getAlpha()==63&&delegate.getColorFilter()==live,"custom color removal restores current native filter on return");
    }
    private static void systemSource()throws Exception{
        features(true);NativeGlyph styled=new NativeGlyph(72,56),original=new NativeGlyph(54,40);Drawable icon=wrapper(styled,original,true);icon.setBounds(0,0,72,56);
        String records=IconManagementSettings.applyTarget(IconManagementSettings.DEFAULT,"wifi",java.util.Collections.singletonMap("wifi.4",new IconManagementSettings.Override("system",null,null)),null,java.util.Collections.emptyMap(),true);
        Bundle settings=new Bundle();settings.putBoolean(IconManagementSettings.ENABLED,true);settings.putString(IconManagementSettings.KEY,records);IconManagementRuntime.configure(new Context(),settings);
        icon.draw(new Transform());check(original.draws==1&&styled.draws==0,"explicit system uses real OEM glyph with builtin iOS styling enabled");check(icon.getIntrinsicWidth()==54,"explicit system restores OEM intrinsic width");
        settings.putBoolean(IconManagementSettings.ENABLED,false);IconManagementRuntime.configure(new Context(),settings);icon.draw(new Transform());check(styled.draws==1,"pausing manager restores independently enabled builtin source");
    }
    public static void main(String[] args)throws Exception{
        String name=args.length==0?"all":args[0];if(name.equals("all")||name.equals("centers"))centers();if(name.equals("all")||name.equals("exceptions"))exceptions();if(name.equals("all")||name.equals("lifecycle"))lifecycle();if(name.equals("all")||name.equals("initial"))initialState();if(name.equals("all")||name.equals("system"))systemSource();if(name.equals("all")||name.equals("bitmap"))realBitmap();if(name.equals("all")||name.equals("animated"))animatedSources();if(name.equals("all")||name.equals("release"))releaseBitmap();if(name.equals("all")||name.equals("compose"))composePlacement();
        System.out.println("IconStatusGeometryCheck "+name+" passed: "+checks);
    }
}
