// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.*;
import android.database.ContentObserver;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.view.View;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Real archives/provider files/PNG pixels feed the production worker and RoleDrawable.
 * The only reflective access waits for WORK; it never inserts manifests, plans or images.
 * Bitmap-backed Canvas also rasterizes actual parsed XML paths with CPU Java2D. Android Skia appearance remains a device check. */
public final class IconManagementRuntimeCheck {
    private static int checks;
    private static final Thread CALLER=Thread.currentThread();
    private static final int RED=0x80ee2211,BLUE=0x804080c0,GREEN=0xc022bb55;
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Icon runtime "+checks+" "+why+": "+expected+" != "+actual);}
    private static void require(boolean result,String why){equal(true,result,why);}
    private static void near(float expected,float actual,String why){checks++;if(Math.abs(expected-actual)>.001f)throw new AssertionError(why+": "+expected+" != "+actual);}
    private static Map<String,Object> map(Object... pairs){Map<String,Object> result=new LinkedHashMap<>();for(int n=0;n<pairs.length;n+=2)result.put((String)pairs[n],pairs[n+1]);return result;}
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static void settle()throws Exception{
        Field worker=IconManagementRuntime.class.getDeclaredField("WORK");worker.setAccessible(true);
        ExecutorService queue=(ExecutorService)worker.get(null);
        // Windows reports no file key, so the verified-pack cache never hits on a desktop and each provider read re-hashes
        // the pack; Android's inode keys keep it cached. The bound only catches a real hang.
        for(int n=0;n<3;n++){queue.submit(()->{}).get(600,TimeUnit.SECONDS);Handler.drainForCheck();}
    }
    private static Bundle settings(String records){Bundle values=new Bundle();values.putString(IconManagementSettings.KEY,records);return values;}
    private static byte[] png(int color,int width,int height)throws Exception{
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=height/4;y<height*3/4;y++)for(int x=width/4;x<width*3/4;x++)image.setRGB(x,y,color);
        ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);return output.toByteArray();
    }
    private static final class Watch{
        final Uri uri;final boolean descendants;final ContentObserver observer;
        Watch(Uri uri,boolean descendants,ContentObserver observer){this.uri=uri;this.descendants=descendants;this.observer=observer;}
    }
    private static final class Gate{
        final String suffix;final CountDownLatch entered=new CountDownLatch(1),resume=new CountDownLatch(1);
        Gate(String suffix){this.suffix=suffix;}
        void await()throws Exception{require(entered.await(5,TimeUnit.SECONDS),"real provider request reached gate");}
    }
    private static final class FilesContext extends Context{
        final Path directory;final String packageName;Provider resolver;
        FilesContext(Path directory,String packageName){this.directory=directory;this.packageName=packageName;}
        @Override public File getFilesDir(){return directory.toFile();}
        @Override public String getPackageName(){return packageName;}
        @Override public ContentResolver getContentResolver(){return resolver;}
    }
    /** A Binder boundary fixture backed by the module's real, validated Provider file.
     * Failures and byte corruption are deliberate transport faults, never fake decoder success. */
    private static final class Provider extends ContentResolver{
        final FilesContext module;
        final AtomicInteger opens=new AtomicInteger(),changes=new AtomicInteger();
        final List<Watch> watches=new CopyOnWriteArrayList<>();
        final Set<Thread> readThreads=Collections.newSetFromMap(new ConcurrentHashMap<>());
        volatile boolean available=true;volatile String corruptSuffix,failOnceSuffix;volatile Gate gate;
        Provider(FilesContext module){this.module=module;}
        @Override public void registerContentObserver(Uri uri,boolean descendants,ContentObserver observer){watches.add(new Watch(uri,descendants,observer));}
        @Override public void unregisterContentObserver(ContentObserver observer){watches.removeIf(item->item.observer==observer);}
        @Override public void notifyChange(Uri uri,ContentObserver observer){
            changes.incrementAndGet();for(Watch watch:watches)if(uri.toString().equals(watch.uri.toString())||watch.descendants&&uri.toString().startsWith(watch.uri.toString()))watch.observer.onChange(false,uri);
        }
        @Override public InputStream openInputStream(Uri uri)throws FileNotFoundException{
            opens.incrementAndGet();readThreads.add(Thread.currentThread());
            if(!available)throw new FileNotFoundException("controlled provider unavailable");
            if(failOnceSuffix!=null&&uri.toString().endsWith(failOnceSuffix)){failOnceSuffix=null;throw new FileNotFoundException("controlled first family peer read failure");}
            try{
                File file=IconManagementRepository.providerFile(module,uri);byte[] source=Files.readAllBytes(file.toPath());
                Gate waiting=gate;if(waiting!=null&&uri.toString().endsWith(waiting.suffix)&&gate==waiting){gate=null;waiting.entered.countDown();if(!waiting.resume.await(6,TimeUnit.SECONDS))throw new IOException("provider gate timed out");}
                if(corruptSuffix!=null&&uri.toString().endsWith(corruptSuffix)){source=source.clone();source[source.length-5]^=1;}
                return new ByteArrayInputStream(source);
            }catch(Exception failure){FileNotFoundException wrapped=new FileNotFoundException(failure.getMessage());wrapped.initCause(failure);throw wrapped;}
        }
    }
    private static final class Fixture implements AutoCloseable{
        final Path directory;final FilesContext module,system;final Provider provider;
        Fixture()throws Exception{
            IconManagementRuntime.releaseRuntime();settle();ResidentCache.current=null;
            directory=Files.createTempDirectory("c17-runtime-v3-");Path app=Files.createDirectory(directory.resolve("app")),sys=Files.createDirectory(directory.resolve("sys"));
            module=new FilesContext(app,ModuleDiagnostics.PACKAGE_NAME);system=new FilesContext(sys,"com.android.systemui");provider=new Provider(module);module.resolver=system.resolver=provider;
        }
        IconManagementRepository.Pack install(String label,Map<String,Object> icons,Map<String,byte[]> assets)throws Exception{
            return install(label,icons,assets,map());
        }
        IconManagementRepository.Pack install(String label,Map<String,Object> icons,Map<String,byte[]> assets,Map<String,Object> transitions)throws Exception{
            Path archive=directory.resolve(label+".zip");Map<String,byte[]> entries=new LinkedHashMap<>(assets);
            entries.put("manifest.json",bytes(IconManagementJson.encode(map("format",IconManagementManifest.FORMAT,"version",3,"id",label,"name",label,"author","runtime check","license","GPL-3.0-only","fallback","native","icons",icons,"transitions",transitions))));
            try(ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(archive))){for(Map.Entry<String,byte[]> entry:entries.entrySet()){output.putNextEntry(new ZipEntry(entry.getKey()));output.write(entry.getValue());output.closeEntry();}}
            try(IconManagementRepository.PreparedPack source=IconManagementRepository.prepare(module,archive.toFile(),"")){return source.commit(()->true);}
        }
        IconManagementRepository.Pack one(String label,String role,String render,int color)throws Exception{
            return install(label,map(role,map("source","assets/a.png","render",render)),Collections.singletonMap("assets/a.png",png(color,8,4)));
        }
        IconManagementRepository.Pack mixed()throws Exception{
            Map<String,Object> icons=map("wifi.1",map("source","assets/mask.png","render","mask"),"wifi.2",map("source","assets/color.png","render","color"),
                    "cellular.3",map("source","assets/color.png","render","color"),"hint.bluetooth.on",map("source","assets/mask.png","render","mask"),
                    "hint.location.default",map("source","assets/color.png","render","color"),"battery.50",map("source","assets/mask.png","render","mask"),
                    "battery.charging.50",map("source","assets/color.png","render","color"),"tile.cast.on",map("source","assets/mask.png","render","mask"));
            Map<String,byte[]> assets=new LinkedHashMap<>();assets.put("assets/mask.png",png(RED,8,4));assets.put("assets/color.png",png(BLUE,8,4));return install("mixed",icons,assets);
        }
        void configure(String records)throws Exception{IconManagementRuntime.configure(system,settings(records));settle();}
        void notifyResources()throws Exception{provider.notifyChange(Uri.parse(IconManagementRepository.URI_PREFIX),null);settle();}
        @Override public void close()throws Exception{
            IconManagementRuntime.releaseRuntime();settle();ResidentCache cache=ResidentCache.current;ResidentCache.current=null;if(cache!=null)cache.release();
            IconManagementRepository.clear(module);try(java.util.stream.Stream<Path> files=Files.walk(directory)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}
        }
    }
    private static class Graphic extends Drawable{
        int alpha=200,draws,tints;ColorFilter filter=new PorterDuffColorFilter(0xff445566,PorterDuff.Mode.SRC_IN),drawFilter;int drawAlpha;boolean nativeGuard,fail;
        Graphic(){setBounds(10,20,110,120);}
        @Override public void draw(Canvas canvas){draws++;drawFilter=filter;drawAlpha=alpha;nativeGuard=IconManagementRuntime.nativeDrawing(this);if(fail)throw new IllegalStateException("controlled native draw failure");}
        @Override public void setAlpha(int value){alpha=value;}
        @Override public int getAlpha(){return alpha;}
        @Override public void setColorFilter(ColorFilter value){filter=value;}
        @Override public ColorFilter getColorFilter(){return filter;}
        @Override public void setTint(int value){tints++;}
        @Override public int getOpacity(){return 0;}
    }
    private static final class Capture extends Canvas{
        Bitmap bitmap;int color,alpha,calls;ColorFilter filter;RectF bounds,actualTarget;Rect sourceBounds,actualSource;
        @Override public void drawBitmap(Bitmap source,Rect src,RectF target,Paint paint){bitmap=source;color=paint.getColor();alpha=paint.getAlpha();filter=paint.getColorFilter();actualSource=src;actualTarget=target;sourceBounds=src==null?null:new Rect(src.left,src.top,src.right,src.bottom);bounds=new RectF(target.left,target.top,target.right,target.bottom);calls++;super.drawBitmap(source,src,target,paint);}
    }
    private static Capture draw(Fixture fixture,String role,Graphic graphic,int tint)throws Exception{
        View owner=new View(fixture.system);Drawable wrapped=IconManagementRuntime.wrap(owner,role,graphic);IconManagementRuntime.nativeTint(wrapped,tint);settle();Capture canvas=new Capture();wrapped.draw(canvas);java.lang.ref.Reference.reachabilityFence(owner);return canvas;
    }
    private static int center(Bitmap bitmap){return bitmap.pixels[(bitmap.getHeight()/2)*bitmap.getWidth()+bitmap.getWidth()/2];}
    private static String suite(String id,String... scopes)throws Exception{return IconManagementSettings.applyPack(IconManagementSettings.DEFAULT,id,Arrays.asList(scopes),false);}
    private static void idleAndMalformed()throws Exception{
        try(Fixture fixture=new Fixture()){
            AtomicInteger invalidations=new AtomicInteger();IconManagementRuntime.setInvalidationListener(invalidations::incrementAndGet);
            fixture.configure(IconManagementSettings.DEFAULT);equal(false,IconManagementRuntime.active(),"empty default never activates the renderer");equal(0,fixture.provider.opens.get(),"empty default opens no resources");
            int published=invalidations.get();fixture.configure(IconManagementSettings.DEFAULT);equal(published,invalidations.get(),"same empty records keep configure fast path");equal(1,fixture.provider.watches.size(),"empty default registers only one event observer");
            IconManagementRuntime.configure(fixture.system,settings("[]"));settle();equal(false,IconManagementRuntime.active(),"malformed records fail closed");equal(0,fixture.provider.opens.get(),"malformed records never reach provider");
            fixture.notifyResources();equal(false,IconManagementRuntime.active(),"resource event cannot activate rejected records");
        }
    }
    private static void selectorsAndHotPath()throws Exception{
        try(Fixture fixture=new Fixture()){
            IconManagementRepository.Pack pack=fixture.mixed();String records=suite(pack.id,"wifi","hint","battery","tile");fixture.configure(records);
            equal(0x80000000,center(IconManagementRuntime.bitmapForWifi(1,true)),"mask preserves actual PNG alpha and removes RGB");equal(BLUE,center(IconManagementRuntime.bitmapForWifi(2,true)),"original PNG color and alpha survive production decode");
            equal(null,IconManagementRuntime.bitmapForWifi(3,true),"missing Wi-Fi state never borrows another level");equal(null,IconManagementRuntime.bitmapForWifi(1,false),"none never borrows connected artwork");
            require(IconManagementRuntime.bitmapForHint("bluetooth",true)!=null,"connected hint loaded");equal(null,IconManagementRuntime.bitmapForHint("bluetooth",false),"missing disconnected hint never borrows connected");
            equal(BLUE,center(IconManagementRuntime.bitmapForHint("location",false)),"single-state hint exact role");equal(null,IconManagementRuntime.bitmapForCellular(3,true),"unselected cellular scope isolated");
            equal(0x80000000,center(IconManagementRuntime.bitmapForBattery(59,false)),"battery uses its real decade");equal(BLUE,center(IconManagementRuntime.bitmapForBattery(59,true)),"charging uses separate exact family");equal(null,IconManagementRuntime.bitmapForBattery(69,true),"missing charging decade never borrows ordinary battery");
            equal(false,IconManagementRuntime.hasAppearance("tile.cast.on"),"unverified target remains native despite supplied resource");Graphic untouched=new Graphic();equal(untouched,IconManagementRuntime.wrap(new View(fixture.system),"tile.cast.on",untouched),"unsupported target is not wrapped");
            Graphic nativeGraphic=new Graphic();nativeGraphic.alpha=128;nativeGraphic.filter=null;
            View drawingOwner=new View(fixture.system);Drawable drawable=IconManagementRuntime.wrap(drawingOwner,"wifi.1",nativeGraphic);IconManagementRuntime.nativeTint(drawable,0xff223344);settle();
            int reads=fixture.provider.opens.get(),creations=Bitmap.creations,pixelReads=Bitmap.reads;Capture capture=new Capture();
            for(int n=0;n<120;n++)drawable.draw(capture);
            equal(reads,fixture.provider.opens.get(),"draw performs no provider/file access");equal(creations,Bitmap.creations,"draw performs no bitmap decode/allocation");equal(pixelReads,Bitmap.reads,"draw performs no pixel extraction");equal(120,capture.calls,"actual v3 bitmap drawn each frame");equal(0,nativeGraphic.draws,"available source does not draw stale OEM artwork");
            near(10,capture.bounds.left,"aspect left");near(45,capture.bounds.top,"aspect centered vertically");near(110,capture.bounds.right,"aspect right");near(95,capture.bounds.bottom,"aspect height preserves source ratio");
            int before=fixture.provider.opens.get();fixture.configure(records);equal(before,fixture.provider.opens.get(),"same configured records keep worker fast path");
            for(Thread worker:fixture.provider.readThreads)require(worker!=CALLER,"provider bytes only read by real worker");
            java.lang.ref.Reference.reachabilityFence(drawingOwner);
            records=IconManagementSettings.applyTarget(records,"wifi",Collections.singletonMap("wifi.1",new IconManagementSettings.Override("system","","")),null,Collections.emptyMap(),true);fixture.configure(records);
            equal(null,IconManagementRuntime.bitmapForWifi(1,true),"explicit system bypasses scope source");equal(false,IconManagementRuntime.hasAppearance("wifi.1"),"system-only override needs no replacement wrapper");require(IconManagementRuntime.bitmapForWifi(2,true)!=null,"unmodified sibling source retained");
        }
    }
    private static void appearanceAndRestore()throws Exception{
        try(Fixture fixture=new Fixture()){
            IconManagementRepository.Pack pack=fixture.mixed();String base=suite(pack.id,"wifi");fixture.configure(base);
            Graphic graphic=new Graphic();graphic.alpha=128;graphic.filter=null;Capture painted=draw(fixture,"wifi.1",graphic,0x80112233);
            equal(64,painted.alpha,"native tint alpha and drawable alpha apply once each");equal(0x80112233,painted.color,"mask follows actual native RGB");equal(null,painted.filter,"no invented filter for unfiltered native mask");
            String opacity=IconManagementSettings.applyAppearance(base,"scope:wifi",new IconManagementSettings.Appearance("inherit",0xff000000,0xffffffff,50f));fixture.configure(opacity);
            painted=draw(fixture,"wifi.1",graphic,0x80112233);equal(32,painted.alpha,"50 percent user opacity is multiplied once");
            ColorFilter nativeFilter=new PorterDuffColorFilter(0x80112233,PorterDuff.Mode.SRC_IN);graphic.filter=nativeFilter;painted=draw(fixture,"wifi.1",graphic,0x80112233);
            equal(nativeFilter,painted.filter,"real native filter forwarded unchanged");equal(64,painted.alpha,"filter alpha is not duplicated in Paint alpha");equal(nativeFilter,graphic.filter,"draw retains original native filter");
            fixture.configure(base);graphic.alpha=200;painted=draw(fixture,"wifi.2",graphic,0x80112233);
            equal(100,painted.alpha,"original color keeps native filter alpha without applying its RGB");equal(null,painted.filter,"original color removes only the RGB filter");
            graphic.filter=null;painted=draw(fixture,"wifi.2",graphic,0xffeeeeee);
            equal(BLUE,center(painted.bitmap),"color asset retains actual source RGB");equal(null,painted.filter,"artwork default ignores foreground recoloring");equal(200,painted.alpha,"original color retains native drawable opacity");
            String custom=IconManagementSettings.applyAppearance(base,"scope:wifi",new IconManagementSettings.Appearance("custom",0xff123456,0xffabcdef,50f));fixture.configure(custom);
            painted=draw(fixture,"wifi.2",graphic,0xff111111);equal(0xff123456,((PorterDuffColorFilter)painted.filter).getColor(),"color source can be explicitly recolored on light background");equal(100,painted.alpha,"color source user alpha once");
            painted=draw(fixture,"wifi.2",graphic,0xffeeeeee);equal(0xffabcdef,((PorterDuffColorFilter)painted.filter).getColor(),"dark background custom RGB follows host color context");
            int tintWrites=graphic.tints;ColorFilter beforeFilter=graphic.filter;int beforeAlpha=graphic.alpha;Drawable wrapped=IconManagementRuntime.wrap(new View(fixture.system),"wifi.1",graphic);IconManagementRuntime.nativeTint(wrapped,0xff112233);
            equal(tintWrites,graphic.tints,"nativeTint does not call OEM setTint");equal(beforeFilter,graphic.filter,"nativeTint does not write OEM filter");equal(beforeAlpha,graphic.alpha,"nativeTint does not write OEM alpha");equal(graphic,IconManagementRuntime.unwrap(wrapped),"unwrap returns real original object");
            String zero=IconManagementSettings.applyAppearance(base,"scope:wifi",new IconManagementSettings.Appearance("inherit",0xff000000,0xffffffff,0f));fixture.configure(zero);equal(0,draw(fixture,"wifi.1",graphic,0xffffffff).alpha,"explicit zero alpha retained");
            String nativeOnly=IconManagementSettings.applyTarget(IconManagementSettings.DEFAULT,"wifi",Collections.singletonMap("wifi.1",new IconManagementSettings.Override("system","","")),
                    new IconManagementSettings.Appearance("custom",0xff203040,0xffa0b0c0,50f),Collections.emptyMap(),true);fixture.configure(nativeOnly);
            graphic=new Graphic();beforeFilter=graphic.filter;painted=draw(fixture,"wifi.1",graphic,0xff111111);
            equal(0,painted.calls,"explicit system source retains original drawing");equal(100,graphic.drawAlpha,"native fallback receives one user alpha factor");equal(0xff203040,((PorterDuffColorFilter)graphic.drawFilter).getColor(),"native source can have independent color");equal(true,graphic.nativeGuard,"native fallback is guarded against re-entry");equal(200,graphic.alpha,"native alpha restored after draw");equal(beforeFilter,graphic.filter,"native filter restored after draw");equal(false,IconManagementRuntime.nativeDrawing(graphic),"native drawing guard removed");
            graphic.fail=true;Graphic failing=graphic;View failureOwner=new View(fixture.system);Drawable failureWrapper=IconManagementRuntime.wrap(failureOwner,"wifi.1",failing);IconManagementRuntime.nativeTint(failureWrapper,0xff111111);settle();
            checks++;try{failureWrapper.draw(new Capture());throw new AssertionError("Controlled native draw must fail");}catch(IllegalStateException expected){}
            equal(200,failing.alpha,"throwing native draw restores alpha in finally");equal(beforeFilter,failing.filter,"throwing native draw restores filter in finally");equal(false,IconManagementRuntime.nativeDrawing(failing),"throwing native draw clears re-entry guard");
            failing.fail=false;Bundle safety=settings(nativeOnly);safety.putBoolean(StatusBarSettings.SAFE_MODE,true);IconManagementRuntime.configure(fixture.system,safety);settle();failureWrapper.draw(new Capture());equal(false,IconManagementRuntime.active(),"safety mode disables v3");equal(200,failing.drawAlpha,"existing wrapper returns to full native opacity");equal(beforeFilter,failing.drawFilter,"existing wrapper returns to original native filter");
            int reads=fixture.provider.opens.get();fixture.notifyResources();equal(reads,fixture.provider.opens.get(),"resource event cannot bypass safety mode");
            java.lang.ref.Reference.reachabilityFence(failureOwner);
        }
    }
    private static void sourceRevisionAndStyle()throws Exception{
        try(Fixture fixture=new Fixture()){
            IconManagementRepository.Pack pack=fixture.mixed();String base=suite(pack.id,"wifi");fixture.configure(base);
            Bitmap mask=IconManagementRuntime.bitmapForWifi(1,true),color=IconManagementRuntime.bitmapForWifi(2,true);long revision=IconManagementRuntime.sourceRevision();
            require(mask!=null&&color!=null,"style update starts with two real decoded sources");equal(false,IconManagementRuntime.hasExplicitAppearance("wifi.1"),"source-only selection is distinct from explicit appearance");
            Graphic graphic=new Graphic();graphic.filter=null;View owner=new View(fixture.system);Drawable retained=IconManagementRuntime.wrap(owner,"wifi.2",graphic);IconManagementRuntime.nativeTint(retained,0xff111111);settle();
            int reads=fixture.provider.opens.get(),creations=Bitmap.creations,pixelReads=Bitmap.reads;
            String styled=IconManagementSettings.applyAppearance(base,"scope:wifi",new IconManagementSettings.Appearance("custom",0xff214365,0xffbadcfe,50f));fixture.configure(styled);
            equal(revision,IconManagementRuntime.sourceRevision(),"color/opacity draft changes no source revision");equal(mask,IconManagementRuntime.bitmapForWifi(1,true),"style update retains exact decoded mask object");equal(color,IconManagementRuntime.bitmapForWifi(2,true),"style update retains exact decoded artwork object");
            equal(reads,fixture.provider.opens.get(),"appearance-only update opens no provider files");equal(creations,Bitmap.creations,"appearance-only update decodes no replacement bitmap");equal(pixelReads,Bitmap.reads,"appearance-only update extracts no pixels");
            equal(true,IconManagementRuntime.hasExplicitAppearance("wifi.1"),"scope appearance is explicit for selected state");equal(false,IconManagementRuntime.hasExplicitAppearance("hint.location.default"),"scope appearance does not bleed to other family");
            IconManagementSelection.ColorPlan light=IconManagementRuntime.appearance("wifi.2",false,"color"),dark=IconManagementRuntime.appearance("wifi.2",true,"color");
            equal("custom",light.mode,"public appearance exposes current artwork color mode");equal(0xff214365,light.color,"public appearance exposes light background RGB");equal(0xffbadcfe,dark.color,"public appearance exposes dark background RGB");near(.5f,light.alpha,"public appearance exposes current opacity");
            Capture capture=new Capture();retained.draw(capture);equal(color,capture.bitmap,"already-bound drawable uses retained actual source");equal(0xff214365,((PorterDuffColorFilter)capture.filter).getColor(),"already-bound drawable receives new RGB immediately");equal(100,capture.alpha,"already-bound drawable receives new opacity once");
            String opacity=IconManagementSettings.applyAppearance(styled,"scope:wifi",new IconManagementSettings.Appearance("custom",0xff214365,0xffbadcfe,75f));fixture.configure(opacity);capture=new Capture();retained.draw(capture);
            equal(150,capture.alpha,"second opacity draft updates an existing drawable");equal(revision,IconManagementRuntime.sourceRevision(),"opacity-only second update keeps source revision");equal(reads,fixture.provider.opens.get(),"repeated opacity drafts do no resource IO");equal(creations,Bitmap.creations,"repeated opacity drafts reuse decoded bitmaps");
            String disabled=IconManagementSettings.applyTarget(opacity,"tile.flashlight",Collections.emptyMap(),null,Collections.emptyMap(),false);fixture.configure(disabled);
            equal(false,IconManagementRuntime.animationEnabled("tile.flashlight"),"animation preference can revoke transition permission");equal(revision,IconManagementRuntime.sourceRevision(),"animation permission change keeps source revision");equal(reads,fixture.provider.opens.get(),"revoking animation permission opens no assets");equal(color,IconManagementRuntime.bitmapForWifi(2,true),"animation permission does not replace static artwork");
            String allowed=IconManagementSettings.applyTarget(disabled,"tile.flashlight",Collections.emptyMap(),null,Collections.emptyMap(),true);fixture.configure(allowed);
            equal(true,IconManagementRuntime.animationEnabled("tile.flashlight"),"animation preference restores permission only");equal(null,IconManagementRuntime.animation("tile.flashlight","off_to_on"),"permission alone never fabricates a playable animation");equal(revision,IconManagementRuntime.sourceRevision(),"restoring animation permission keeps source revision");equal(reads,fixture.provider.opens.get(),"restoring animation permission does not decode sources");
            fixture.notifyResources();equal(revision,IconManagementRuntime.sourceRevision(),"revalidation of identical sources preserves native bindings");require(fixture.provider.opens.get()>reads,"actual resource publication revalidates provider bytes");require(color!=IconManagementRuntime.bitmapForWifi(2,true),"actual resource publication produces a newly validated decoded bitmap");
            revision=IconManagementRuntime.sourceRevision();String system=IconManagementSettings.applyTarget(allowed,"wifi",Collections.singletonMap("wifi.1",new IconManagementSettings.Override("system","","")),null,Collections.emptyMap(),true);fixture.configure(system);
            require(IconManagementRuntime.sourceRevision()>revision,"explicit source switch advances source revision");equal(null,IconManagementRuntime.bitmapForWifi(1,true),"explicit system clears decoded source");require(IconManagementRuntime.bitmapForWifi(2,true)!=null,"source switch preserves selected sibling behavior");
            java.lang.ref.Reference.reachabilityFence(owner);
        }
        try(Fixture fixture=new Fixture()){
            IconManagementRepository.Pack pack=fixture.one("inflight-style","wifi.1","color",GREEN);String base=suite(pack.id,"wifi");Gate pending=new Gate(pack.id+"/assets/a.png");fixture.provider.gate=pending;
            IconManagementRuntime.configure(fixture.system,settings(base));pending.await();long revision=IconManagementRuntime.sourceRevision();int reads=fixture.provider.opens.get();
            String styled=IconManagementSettings.applyAppearance(base,"scope:wifi",new IconManagementSettings.Appearance("custom",0xff765432,0xffabcdef,25f));IconManagementRuntime.configure(fixture.system,settings(styled));
            equal(revision,IconManagementRuntime.sourceRevision(),"in-flight appearance update does not cancel actual source work");equal(reads,fixture.provider.opens.get(),"in-flight appearance update starts no duplicate read");pending.resume.countDown();settle();
            Bitmap decoded=IconManagementRuntime.bitmapForWifi(1,true);require(decoded!=null,"in-flight source publishes after appearance draft");Graphic graphic=new Graphic();graphic.filter=null;Capture capture=draw(fixture,"wifi.1",graphic,0xff111111);
            equal(0xff765432,((PorterDuffColorFilter)capture.filter).getColor(),"in-flight source uses newest appearance rather than stale worker snapshot");equal(50,capture.alpha,"in-flight source uses newest opacity once");
        }
    }
    private static void resourcesAndGeneration()throws Exception{
        try(Fixture fixture=new Fixture()){
            IconManagementRepository.Pack red=fixture.one("red","wifi.1","color",RED),green=fixture.one("green","wifi.1","color",GREEN);String redRecord=suite(red.id,"wifi");
            fixture.provider.available=false;fixture.configure(redRecord);equal(null,IconManagementRuntime.bitmapForWifi(1,true),"provider failure falls back to native");int failedReads=fixture.provider.opens.get();
            fixture.provider.available=true;fixture.configure(redRecord);equal(failedReads,fixture.provider.opens.get(),"same-record fast path does not poll failed provider");fixture.notifyResources();equal(RED,center(IconManagementRuntime.bitmapForWifi(1,true)),"real resource notification retries same records");
            IconManagementRepository.delete(fixture.module,red.id);settle();equal(null,IconManagementRuntime.bitmapForWifi(1,true),"deleted actual library clears source after notification");
            Path archive=fixture.directory.resolve("red.zip");try(IconManagementRepository.PreparedPack pending=IconManagementRepository.prepare(fixture.module,archive.toFile(),"")){equal(red.id,pending.commit(()->true).id,"same actual archive reappears with same hash");}settle();equal(RED,center(IconManagementRuntime.bitmapForWifi(1,true)),"repository publication restores source without changing records");
            fixture.provider.corruptSuffix=red.id+"/assets/a.png";fixture.notifyResources();equal(null,IconManagementRuntime.bitmapForWifi(1,true),"transport byte corruption rejected by runtime digest");fixture.provider.corruptSuffix=null;fixture.notifyResources();equal(RED,center(IconManagementRuntime.bitmapForWifi(1,true)),"same hash bytes recover on explicit event");
            File file=IconManagementRepository.providerFile(fixture.module,IconManagementRepository.assetUri(red.id,"assets/a.png"));byte[] original=Files.readAllBytes(file.toPath());Files.write(file.toPath(),bytes("damaged real PNG"));fixture.notifyResources();equal(null,IconManagementRuntime.bitmapForWifi(1,true),"damaged on-disk package cannot enter manifest plan");Files.write(file.toPath(),original);fixture.notifyResources();equal(RED,center(IconManagementRuntime.bitmapForWifi(1,true)),"restored on-disk bytes revalidated and reloaded");
            String fallback=suite(green.id,"wifi");fallback=IconManagementSettings.applyTarget(fallback,"wifi",Collections.singletonMap("wifi.1",new IconManagementSettings.Override("pack",red.id,"wifi.1")),null,Collections.emptyMap(),true);
            fixture.provider.corruptSuffix=red.id+"/assets/a.png";fixture.configure(fallback);equal(GREEN,center(IconManagementRuntime.bitmapForWifi(1,true)),"bad explicit bytes fall back to exact healthy scope state");equal(null,IconManagementRuntime.bitmapForWifi(2,true),"fallback never borrows a different state");fixture.provider.corruptSuffix=null;
            IconManagementRuntime.releaseRuntime();settle();Gate old=new Gate(red.id+"/assets/a.png");fixture.provider.gate=old;IconManagementRuntime.configure(fixture.system,settings(redRecord));old.await();
            IconManagementRuntime.configure(fixture.system,settings(suite(green.id,"wifi")));old.resume.countDown();settle();equal(GREEN,center(IconManagementRuntime.bitmapForWifi(1,true)),"obsolete worker generation cannot publish over newer settings");
            IconManagementRuntime.releaseRuntime();settle();Gate released=new Gate(red.id+"/assets/a.png");fixture.provider.gate=released;AtomicInteger callbacks=new AtomicInteger();IconManagementRuntime.setInvalidationListener(callbacks::incrementAndGet);
            IconManagementRuntime.configure(fixture.system,settings(redRecord));released.await();Handler.drainForCheck();IconManagementRuntime.releaseRuntime();int beforeCallbacks=callbacks.get();released.resume.countDown();settle();
            equal(null,IconManagementRuntime.bitmapForWifi(1,true),"release rejects an in-flight real decode publication");equal(false,IconManagementRuntime.active(),"release disables draw path");equal(0,fixture.provider.watches.size(),"release unregisters exact resource observer");equal(beforeCallbacks,callbacks.get(),"release leaves no subsequent listener publication");
            int reads=fixture.provider.opens.get();fixture.notifyResources();equal(reads,fixture.provider.opens.get(),"notification after release causes no worker/provider activity");
        }
    }
    private static void budgetAndResidentCache()throws Exception{
        try(Fixture fixture=new Fixture()){
            Map<String,Object> icons=new LinkedHashMap<>();for(String scope:IconRoleRegistry.SCOPES)for(String role:IconRoleRegistry.roles(scope))icons.put(role,map("source","assets/all.png","render","mask"));
            IconManagementRepository.Pack pack=fixture.install("budget",icons,Collections.singletonMap("assets/all.png",png(GREEN,256,256)));String records=IconManagementSettings.applyPack(IconManagementSettings.DEFAULT,pack.id,Arrays.asList(IconRoleRegistry.SCOPES),false);
            fixture.configure(records);View owner=new View(fixture.system);for(IconRoleRegistry.Target target:IconRoleRegistry.targets(null))if(target.supported)for(String role:target.roles)IconManagementRuntime.request(owner,role,256);settle();
            Set<Bitmap> unique=Collections.newSetFromMap(new IdentityHashMap<>());int configured=0;
            for(IconRoleRegistry.Target target:IconRoleRegistry.targets(null))for(String role:target.roles){Graphic graphic=new Graphic();Capture canvas=draw(fixture,role,graphic,0xffffffff);if(target.supported){configured++;if(canvas.bitmap!=null)unique.add(canvas.bitmap);}else equal(0,canvas.calls,"unsupported resource stays outside cache admission");}
            long pixels=0;for(Bitmap bitmap:unique){pixels+=(long)bitmap.getWidth()*bitmap.getHeight();require(bitmap.getWidth()<=256&&bitmap.getHeight()<=256,"individual published glyph size remains bounded");}
            require(!unique.isEmpty(),"real registered glyphs decoded for pixel budget");require(pixels<=4L*1024*1024,"actual published bitmaps respect 4MP budget");require(unique.size()<=configured,"one published bitmap per admitted role");
            long requestedPixels=0;for(IconRoleRegistry.Target target:IconRoleRegistry.targets(null))if(target.supported)requestedPixels+=(long)target.roles.size()*(Arrays.asList("hint","wifi","cellular","battery").contains(target.scope)?128*128:256*256);
            if(requestedPixels<=4L*1024*1024)equal(configured,unique.size(),"every available registered state loads when the bounded family fits");
            IconManagementRuntime.releaseRuntime();settle();ResidentCache cache=new ResidentCache();cache.initialize(fixture.system);ResidentCache.current=cache;fixture.configure(suite(pack.id,"wifi"));Bitmap first=IconManagementRuntime.bitmapForWifi(1,true);require(first!=null,"runtime loads via real resident file cache");int opens=fixture.provider.opens.get();
            fixture.provider.available=false;IconManagementRuntime.refresh();settle();require(IconManagementRuntime.bitmapForWifi(1,true)!=null,"verified immutable resident bytes survive temporary provider loss");equal(opens,fixture.provider.opens.get(),"verified disk cache avoids unnecessary provider restart");
            Bundle old=new Bundle();old.putBoolean("status_icon_pack_enabled",true);old.putString("status_icon_pack_layers","legacy");IconManagementRuntime.configure(fixture.system,old);settle();equal(false,IconManagementRuntime.active(),"legacy master and layers never activate v3");equal(null,IconManagementRuntime.bitmapForWifi(1,true),"old records cannot revive cached v3 imagery");
        }
    }
    private static void managerSwitch()throws Exception{
        try(Fixture fixture=new Fixture()){
            Map<String,Object> icons=map("wifi.1",map("source","assets/a.png","render","mask"),"tile.flashlight.off",map("source","assets/a.png","render","mask"),"tile.flashlight.on",map("source","assets/a.png","render","mask"));
            Map<String,byte[]> assets=new LinkedHashMap<>();assets.put("assets/a.png",png(GREEN,8,4));assets.put("assets/motion.json",Files.readAllBytes(Path.of("tools/icon-management-v3/original/tile-flashlight-off-to-on.json")));
            IconManagementRepository.Pack pack=fixture.install("master",icons,assets,map("tile.flashlight.off_to_on",map("source","assets/motion.json","from","off","to","on","reversible",true)));
            String records=IconManagementSettings.applyAppearance(suite(pack.id,"wifi","tile"),"scope:wifi",new IconManagementSettings.Appearance("custom",0xff123456,0xffabcdef,50f));fixture.configure(records);
            View owner=new View(fixture.system);Graphic graphic=new Graphic();graphic.filter=null;Drawable wrapped=IconManagementRuntime.wrap(owner,"wifi.1",graphic);IconManagementRuntime.nativeTint(wrapped,0xff111111);IconManagementRuntime.requestAnimation(owner,"tile.flashlight","off_to_on");settle();
            require(IconManagementRuntime.animation("tile.flashlight","off_to_on")!=null,"real verified transition available before master pause");Capture before=new Capture();wrapped.draw(before);equal(100,before.alpha,"selected source opacity before pause");equal(0xff123456,before.color,"selected source color before pause");
            Bundle off=settings(records);off.putBoolean(IconManagementSettings.ENABLED,false);int reads=fixture.provider.opens.get();long revision=IconManagementRuntime.sourceRevision();IconManagementRuntime.configure(fixture.system,off);settle();
            equal(false,IconManagementRuntime.active(),"master false disables whole renderer");equal(false,IconManagementRuntime.hasAppearance("wifi.1"),"master false suppresses selected appearance");equal(false,IconManagementRuntime.hasExplicitAppearance("wifi.1"),"master false suppresses explicit color/opacity");equal(false,IconManagementRuntime.animationEnabled("tile.flashlight"),"master false suppresses animation permission");equal(null,IconManagementRuntime.animation("tile.flashlight","off_to_on"),"master false removes playable animation");equal(null,IconManagementRuntime.bitmapForWifi(1,true),"master false removes source drawing");
            Capture paused=new Capture();wrapped.draw(paused);equal(0,paused.calls,"already bound wrapper restores native drawing");equal(200,graphic.drawAlpha,"native opacity restored while paused");equal(null,graphic.drawFilter,"native filter restored while paused");require(IconManagementRuntime.sourceRevision()>revision,"pause cancels source work");equal(records,off.getString(IconManagementSettings.KEY),"pause does not mutate settings record");
            fixture.notifyResources();IconManagementRuntime.refresh();settle();IconManagementRuntime.request(owner,"wifi.1",128);IconManagementRuntime.requestAnimation(owner,"tile.flashlight","off_to_on");settle();equal(false,IconManagementRuntime.active(),"provider publication and refresh cannot reactivate false master");equal(reads,fixture.provider.opens.get(),"paused publication/draw/request performs no provider reads");equal(1,IconManagementRepository.list(fixture.module).size(),"pause retains private library");
            IconManagementRuntime.configure(fixture.system,settings(records));settle();equal(true,IconManagementRuntime.active(),"explicit reenable restores selected record");Capture restored=new Capture();wrapped.draw(restored);equal(100,restored.alpha,"reenable restores selected opacity");equal(0xff123456,restored.color,"reenable restores selected color");equal(0xc0000000,center(restored.bitmap),"reenable restores validated exact mask source");equal(null,IconManagementRuntime.animation("tile.flashlight","off_to_on"),"reenable alone does not replay old animation");IconManagementRuntime.requestAnimation(owner,"tile.flashlight","off_to_on");settle();require(IconManagementRuntime.animation("tile.flashlight","off_to_on")!=null,"new explicit transition request works after reenable");
            // A decode started before the pause cannot publish when its provider returns later.
            IconManagementRuntime.releaseRuntime();settle();Gate pending=new Gate(pack.id+"/assets/a.png");fixture.provider.gate=pending;IconManagementRuntime.configure(fixture.system,settings(records));pending.await();IconManagementRuntime.configure(fixture.system,off);pending.resume.countDown();settle();equal(false,IconManagementRuntime.active(),"in-flight source cannot revive paused renderer");equal(null,IconManagementRuntime.bitmapForWifi(1,true),"late source bytes never publish while paused");fixture.notifyResources();equal(false,IconManagementRuntime.active(),"later resource event keeps master paused");
            java.lang.ref.Reference.reachabilityFence(owner);
        }
    }
    private static byte[] artwork(int width,int height,int left,int top,int right,int bottom,int color)throws Exception{
        BufferedImage source=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)source.setRGB(x,y,color);
        ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(source,"png",output);return output.toByteArray();
    }
    private static void visibleBoundsAndNativePipeline()throws Exception{
        int[] faint={0,0x01010203,0,0,0,0};IconVisibleBounds edge=IconVisibleBounds.scan(faint,3,2);
        equal(1,edge.left,"alpha=1 outer edge remains visible");equal(2,edge.right,"one visible pixel exclusive right");equal(1,edge.height,"single-row height valid");equal(null,IconVisibleBounds.scan(new int[6],3,2),"empty raster has no invented box");
        equal(null,IconVisibleBounds.scan(new int[2],3,2),"truncated raster rejected");equal(null,IconVisibleBounds.scan(new int[2],Integer.MAX_VALUE,2),"overflowing dimensions rejected");near(0,edge.fitScale(Float.NaN,20),"NaN host region rejected");near(0,edge.fitScale(20,Float.POSITIVE_INFINITY),"infinite host region rejected");
        try(Fixture fixture=new Fixture()){
            // Keep per-source visible-bounds assertions independent of a failed numeric
            // signal family; familyRecovery below covers that conservative fallback.
            Map<String,Object> icons=map("wifi.none",map("source","assets/blank.png","render","color"),"wifi.1",map("source","assets/small.png","render","color"),"wifi.2",map("source","assets/padded.png","render","color"),"wifi.3",map("source","assets/narrow.xml","render","color"),"wifi.4",map("source","assets/wide.png","render","color"));
            Map<String,byte[]> assets=new LinkedHashMap<>();assets.put("assets/blank.png",artwork(32,32,0,0,0,0,0));assets.put("assets/small.png",artwork(32,32,14,4,18,28,RED));assets.put("assets/padded.png",artwork(128,32,62,4,66,28,RED));assets.put("assets/wide.png",artwork(128,64,4,28,124,36,RED));
            assets.put("assets/narrow.xml",bytes("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"32dp\" android:height=\"32dp\" android:viewportWidth=\"32\" android:viewportHeight=\"32\"><path android:fillColor=\"#80ee2211\" android:pathData=\"M14,4 L18,4 L18,28 L14,28 Z\"/></vector>"));
            byte[] layered=bytes("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:viewportWidth=\"32\" android:viewportHeight=\"32\" android:alpha=\"0.5\"><group android:translateX=\"14\" android:translateY=\"4\"><path android:fillColor=\"#ffee2211\" android:pathData=\"M0,0 h4 v24 h-4 Z\"/><path android:fillColor=\"#ffee2211\" android:pathData=\"M0,0 h4 v24 h-4 Z\"/></group></vector>");
            assets.put("assets/layer.xml",layered);icons.put("hint.location.default",map("source","assets/layer.xml","render","color"));
            IconManagementRepository.Pack pack=fixture.install("visible-bounds",icons,assets);String records=IconManagementSettings.applyAppearance(suite(pack.id,"wifi"),"scope:wifi",new IconManagementSettings.Appearance("artwork",0xff000000,0xffffffff,null));fixture.configure(records);
            IconVisibleBounds small=IconManagementRuntime.imageVisibleBounds("wifi.1"),padded=IconManagementRuntime.imageVisibleBounds("wifi.2"),vector=IconManagementRuntime.imageVisibleBounds("wifi.3");
            require(small!=null&&padded!=null&&vector!=null,"PNG and actual XML raster all publish visible boxes");equal(14,small.left,"real PNG visible origin");equal(62,padded.left,"different transparent canvas retained in raw bitmap");equal(24,padded.height,"padded PNG height is measured from alpha");equal(96,vector.height,"XML bounds measured after 128px production rasterization");
            Bitmap source=IconManagementRuntime.bitmapForWifi(2,true);equal(128,source.getWidth(),"original canvas width remains unmodified");equal(0,source.pixels[0],"transparent source pixel untouched");equal(RED,source.pixels[16*128+64],"original semitransparent RGB/alpha untouched");
            Capture first=null;for(int level=1;level<=3;level++){Graphic graphic=new Graphic();graphic.filter=null;graphic.alpha=255;Capture drawn=draw(fixture,"wifi."+level,graphic,0xffffffff);near(100,drawn.bounds.height(),"narrow artwork reaches native glyph height "+level);near(100f/6,drawn.bounds.width(),"visible aspect ratio preserved "+level);near(60,drawn.bounds.centerX(),"glyph remains horizontally centered "+level);near(70,drawn.bounds.centerY(),"glyph remains vertically centered "+level);equal(255,drawn.alpha,"original asset alpha is not multiplied by decoder");equal(null,drawn.filter,"original RGB is drawn without tint filter");if(first==null)first=drawn;else near(first.bounds.width(),drawn.bounds.width(),"padding and XML do not alter visual size");}
            equal(new Rect(62,4,66,28),draw(fixture,"wifi.2",new Graphic(),0xffffffff).sourceBounds,"draw crops actual cached visible source rectangle");
            View failureOwner=new View(fixture.system);Graphic failedNative=new Graphic();failedNative.filter=null;Drawable failureWrapper=IconManagementRuntime.wrap(failureOwner,"wifi.2",failedNative);settle();
            failureWrapper.draw(new Canvas(){@Override public void drawBitmap(Bitmap image,Rect source,RectF target,Paint paint){throw new IllegalStateException("controlled GPU bitmap failure");}});equal(1,failedNative.draws,"bitmap draw exception safely retains native graphic");equal(200,failedNative.alpha,"bitmap exception does not alter native alpha");equal(null,failedNative.filter,"bitmap exception does not alter native filter");Capture retry=new Capture();failureWrapper.draw(retry);equal(1,retry.calls,"later valid draw reuses source after transient canvas failure");java.lang.ref.Reference.reachabilityFence(failureOwner);
            Graphic graphic=new Graphic();graphic.filter=null;graphic.alpha=255;Capture wide=draw(fixture,"wifi.4",graphic,0xffffffff);near(100,wide.bounds.width(),"wide source respects available width");near(100f*8/120,wide.bounds.height(),"wide source remains proportional when width-limited");
            Graphic blank=new Graphic();Capture empty=draw(fixture,"wifi.none",blank,0xffffffff);equal(0,empty.calls,"fully transparent asset never draws an invented icon");equal(1,blank.draws,"fully transparent asset retains native graphic");equal(null,IconManagementRuntime.imageVisibleBounds("wifi.none"),"empty source has no published box");
            // This traverses the real default IconPackDrawing assets port, not a successful fake bitmap port.
            IconPackDrawing helper=new IconPackDrawing();Graphic host=new Graphic();host.filter=null;host.alpha=255;Capture nativePath=new Capture();
            require(helper.drawSignal("stat_signal_wifi_signal_2",host,nativePath,0xffffffff,255,null,false),"native signal pipeline draws loaded real v3 resource");near(100,nativePath.bounds.height(),"outer Mask does not pre-fit padded canvas");near(100f/6,nativePath.bounds.width(),"double-layer signal pipeline fits visible aspect");
            equal(new Rect(62,4,66,28),nativePath.sourceBounds,"native signal uses decoded visible source crop");
            Bitmap output=Bitmap.createBitmap(180,180,Bitmap.Config.ARGB_8888);Canvas transformed=new Canvas(output);int saved=transformed.save();transformed.translate(7,9);transformed.scale(.5f,.5f);int reads=Bitmap.reads,allocations=Bitmap.creations,opens=fixture.provider.opens.get();
            require(helper.drawSignal("stat_signal_wifi_signal_2",host,transformed,0xffffffff,128,null,false),"native transform path draws cached color resource");transformed.restoreToCount(saved);equal(reads,Bitmap.reads,"actual output draw performs no pixel scan");equal(allocations,Bitmap.creations,"actual output draw allocates no bitmap");equal(opens,fixture.provider.opens.get(),"actual output draw performs no IO");equal(1,transformed.getSaveCount(),"incoming canvas save stack preserved");
            IconVisibleBounds rendered=IconVisibleBounds.scan(output.pixels,output.getWidth(),output.getHeight());require(rendered!=null,"software canvas contains real drawn pixels");near(50,rendered.height,"preexisting native scale is respected");near(19,rendered.top,"preexisting native translation is respected");int painted=output.pixels[(rendered.top+rendered.height/2)*output.getWidth()+rendered.left+rendered.width/2];near(64,painted>>>24,"source alpha and native alpha multiplied exactly once");
            IconManagementManifest.Asset previewAsset=pack.manifest.icons.get("wifi.2");Bitmap preview=IconAssetDecoder.preview(previewAsset,assets.get("assets/padded.png"),128,128*128);equal(4,preview.getWidth(),"preview uses same visible width");equal(24,preview.getHeight(),"preview uses same visible height");for(int pixel:preview.pixels)equal(RED,pixel,"preview retains source color/alpha without rewriting");preview.recycle();
            Bitmap xmlPreview=IconAssetDecoder.preview(pack.manifest.icons.get("wifi.3"),assets.get("assets/narrow.xml"),128,128*128);equal(16,xmlPreview.getWidth(),"XML preview crops actual raster bounds");equal(96,xmlPreview.getHeight(),"XML preview matches runtime visible height");equal(center(IconManagementRuntime.bitmapForWifi(3,true)),center(xmlPreview),"XML preview preserves actual raster RGB/alpha exactly");equal(128,center(xmlPreview)>>>24,"XML semitransparent alpha remains unchanged");xmlPreview.recycle();

            Bitmap layeredPreview=IconAssetDecoder.preview(pack.manifest.icons.get("hint.location.default"),layered,128,128*128);equal(16,layeredPreview.getWidth(),"transformed XML group produces measured width");equal(96,layeredPreview.getHeight(),"transformed XML group produces measured height");equal(128,center(layeredPreview)>>>24,"root alpha composites overlapping paths once");layeredPreview.recycle();
            checks++;try{IconAssetDecoder.preview(pack.manifest.icons.get("wifi.none"),assets.get("assets/blank.png"),128,128*128);throw new AssertionError("empty preview accepted");}catch(IOException expected){}
            Capture hot=new Capture();helper.drawSignal("stat_signal_wifi_signal_2",host,hot,0xffffffff,255,null,false);opens=fixture.provider.opens.get();reads=Bitmap.reads;allocations=Bitmap.creations;Rect cachedSource=hot.bitmapLog.get(0).source,sourceIdentity=hot.actualSource;RectF targetIdentity=hot.actualTarget;
            for(int n=0;n<120;n++)helper.drawSignal("stat_signal_wifi_signal_2",host,hot,0xffffffff,255,null,false);equal(opens,fixture.provider.opens.get(),"double-layer hot draw no IO");equal(reads,Bitmap.reads,"double-layer hot draw never scans bbox again");equal(allocations,Bitmap.creations,"double-layer hot draw no bitmap allocations");equal(cachedSource,hot.sourceBounds,"visible source region stays stable across frames");require(sourceIdentity==hot.actualSource,"hot draw reuses immutable crop Rect");require(targetIdentity==hot.actualTarget,"hot draw reuses per-thread destination RectF");
            long revision=IconManagementRuntime.sourceRevision();String plain=suite(pack.id,"wifi");fixture.configure(plain);require(padded==IconManagementRuntime.imageVisibleBounds("wifi.2"),"appearance-only change reuses visible bounds object");equal(revision,IconManagementRuntime.sourceRevision(),"appearance-only change does not regenerate sources");helper.drawSignal("stat_signal_wifi_signal_2",host,hot,0xffffffff,255,null,false);require(sourceIdentity==hot.actualSource,"appearance-only change reuses immutable source Rect");
            Canvas failedCanvas=new Canvas(){@Override public void drawBitmap(Bitmap image,Rect source,RectF target,Paint paint){throw new IllegalStateException("controlled native signal GPU failure");}};int nativeBefore=host.draws;boolean replaced=helper.drawSignal("stat_signal_wifi_signal_2",host,failedCanvas,0xffffffff,255,null,false);equal(false,replaced,"double-layer bitmap exception returns native fallback signal");if(!replaced)host.draw(failedCanvas);equal(nativeBefore+1,host.draws,"native caller draws its original graphic after failure");require(helper.drawSignal("stat_signal_wifi_signal_2",host,new Capture(),0xffffffff,255,null,false),"double-layer next frame can draw after GPU failure");
            Bundle off=settings(plain);off.putBoolean(IconManagementSettings.ENABLED,false);opens=fixture.provider.opens.get();IconManagementRuntime.configure(fixture.system,off);settle();equal(false,helper.drawSignal("stat_signal_wifi_signal_2",host,new Capture(),0xffffffff,255,null,false),"existing native helper preserves original signal while master is off");equal(opens,fixture.provider.opens.get(),"master-off helper draws no provider resources");helper.releaseRuntime();
            for(Thread worker:fixture.provider.readThreads)require(worker!=CALLER,"new padding/XML sources use real decoder worker");
        }
    }
    private static byte[] rowPng(boolean upper)throws Exception{
        BufferedImage raster=new BufferedImage(114,96,BufferedImage.TYPE_INT_ARGB);
        for(int y=upper?12:72;y<(upper?65:85);y++)for(int x=12;x<102;x++)raster.setRGB(x,y,0xffffffff);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(raster,"png",out);return out.toByteArray();
    }
    private static IconVisibleBounds rasterBounds(Bitmap raster){return IconVisibleBounds.scan(raster.pixels,raster.getWidth(),raster.getHeight());}
    /** Original fixture geometry: two simple rectangular rows in one 19x16
     * viewport. No user theme or OEM artwork is needed by public regressions. */
    private static IconManagementRepository.Pack stackedVectors(Fixture fixture)throws Exception{
        Map<String,Object> icons=map();Map<String,byte[]> assets=new LinkedHashMap<>();
        for(int level=0;level<=4;level++)for(String row:new String[]{"primary","secondary","single"}){
            String source="assets/"+row+'-'+level+".xml",role="single".equals(row)?"cellular."+level:"cellular.stacked_"+row+'.'+level;
            float left=2f,right=3f+level*3.5f,top="secondary".equals(row)?12f:2.2f,bottom="primary".equals(row)?11f:14f;
            String path="M"+left+','+top+" H"+right+" V"+bottom+" H"+left+" Z";
            assets.put(source,bytes("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"19dp\" android:height=\"16dp\" android:viewportWidth=\"19\" android:viewportHeight=\"16\"><path android:fillColor=\"#ffffffff\" android:pathData=\""+path+"\"/></vector>"));
            icons.put(role,map("source",source,"render","mask"));
        }
        icons.put("cellular.stacked_primary.none",map("source","assets/primary-0.xml","render","mask"));
        return fixture.install("original-stacked-vectors",icons,assets);
    }
    private static void stackedSignals()throws Exception{
        try(Fixture fixture=new Fixture()){
            IconManagementRepository.Pack pack=stackedVectors(fixture);
            String records=suite(pack.id,"cellular");fixture.configure(records);IconPackDrawing helper=new IconPackDrawing();Graphic nativeGlyph=new Graphic();nativeGlyph.setBounds(0,0,190,160);nativeGlyph.filter=null;
            Bitmap upper=Bitmap.createBitmap(190,160,Bitmap.Config.ARGB_8888),lower=Bitmap.createBitmap(190,160,Bitmap.Config.ARGB_8888);
            require(helper.drawSignal("stat_signal_lte_signal_stacked_primary_4",nativeGlyph,new Canvas(upper),0xffffffff,255,null,false),"imported original upper vector reaches native signal drawing");
            require(helper.drawSignal("stat_signal_lte_signal_stacked_secondary_2",nativeGlyph,new Canvas(lower),0xffffffff,255,null,false),"imported original lower vector reaches native signal drawing");
            IconVisibleBounds a=rasterBounds(upper),b=rasterBounds(lower);require(a!=null&&b!=null,"both source rows produce real pixels");require(a.bottom<b.top,"original fixture rows never overlap");require(a.height>b.height*3,"shared viewport preserves unequal upper and lower shape heights");require(a.top>=20&&a.bottom<=112&&b.top>=114&&b.bottom<=143,"rows retain their original common 19x16 fixture positions: upper="+a.top+".."+a.bottom+", lower="+b.top+".."+b.bottom);
            Capture captured=new Capture();helper.drawSignal("stat_signal_lte_signal_stacked_primary_4",nativeGlyph,captured,0xffffffff,255,null,false);near(-15,captured.bounds.top,"vector square letterbox is compensated without shifting its logical canvas");near(175,captured.bounds.bottom,"vector logical canvas fills original native frame");
            int opens=fixture.provider.opens.get(),reads=Bitmap.reads,creations=Bitmap.creations;Rect source=captured.actualSource;RectF target=captured.actualTarget;
            for(String family:new String[]{"lte","soft"})for(int primary=0;primary<=4;primary++)for(int secondary=0;secondary<=4;secondary++){
                require(helper.drawSignal("stat_signal_"+family+"_signal_stacked_primary_"+primary,nativeGlyph,captured,0xffaabbcc,128,null,false),"native primary level binds independently");require(captured.bitmap==IconManagementRuntime.bitmapForRole("cellular.stacked_primary."+primary),"primary uses its own registered state source");equal(128,captured.alpha,"upper row retains native opacity");
                require(helper.drawSignal("stat_signal_"+family+"_signal_stacked_secondary_"+secondary,nativeGlyph,captured,0xffddeeff,64,null,false),"native secondary level binds independently");require(captured.bitmap==IconManagementRuntime.bitmapForRole("cellular.stacked_secondary."+secondary),"secondary uses its own registered state source");equal(64,captured.alpha,"lower row retains separate native opacity");
            }
            equal(opens,fixture.provider.opens.get(),"two-row draw never reads provider files");equal(reads,Bitmap.reads,"two-row draw never scans pixels");equal(creations,Bitmap.creations,"two-row draw never creates bitmaps");require(target==captured.actualTarget,"two-row draw reuses destination RectF");
            helper.drawSignal("stat_signal_lte_signal_stacked_primary_4",nativeGlyph,captured,0xffffffff,255,null,false);require(source==captured.actualSource,"same row reuses its source rectangle");
            require(helper.drawSignal("stat_signal_lte_signal_stacked_primary_noservice",nativeGlyph,captured,0xffffffff,255,null,false),"declared primary no-service uses its imported source");equal(false,helper.drawSignal("stat_signal_lte_signal_stacked_secondary_noservice",nativeGlyph,captured,0xffffffff,255,null,false),"missing secondary no-service stays native, never borrows zero bars");
            require(helper.drawSignal("stat_signal_lte_signal_stacked_primary_4",nativeGlyph,captured,0xffffffff,255,null,false,true),"single-row preference still uses primary native identity");require(captured.bitmap==IconManagementRuntime.bitmapForRole("cellular.4"),"single-row preference selects full single-row artwork");equal(false,helper.drawSignal("stat_signal_lte_signal_stacked_secondary_4",nativeGlyph,captured,0xffffffff,255,null,false,true),"single-row preference never paints the lower row");
            String styled=IconManagementSettings.applyAppearance(records,"target:cellular.stacked_primary",new IconManagementSettings.Appearance("custom",0xffcc2233,0xffcc2233,50f));styled=IconManagementSettings.applyAppearance(styled,"target:cellular.stacked_secondary",new IconManagementSettings.Appearance("custom",0xff22cc33,0xff22cc33,100f));long revision=IconManagementRuntime.sourceRevision();fixture.configure(styled);equal(revision,IconManagementRuntime.sourceRevision(),"row color changes preserve decoded source revision");
            helper.drawSignal("stat_signal_lte_signal_stacked_primary_4",nativeGlyph,captured,0xffffffff,200,null,false);equal(0xffcc2233,captured.color,"upper row has independent color");equal(100,captured.alpha,"upper row opacity multiplies once");require(source==captured.actualSource,"row appearance changes keep the original shared canvas rectangle");
            helper.drawSignal("stat_signal_lte_signal_stacked_secondary_4",nativeGlyph,captured,0xffffffff,200,null,false);equal(0xff22cc33,captured.color,"lower row has independent color");equal(200,captured.alpha,"lower row has independent opacity");
            Bundle disabled=settings(styled);disabled.putBoolean(IconManagementSettings.ENABLED,false);IconManagementRuntime.configure(fixture.system,disabled);settle();equal(false,helper.drawSignal("stat_signal_lte_signal_stacked_primary_4",nativeGlyph,captured,0xffffffff,255,null,false),"master off restores both native rows");helper.releaseRuntime();upper.recycle();lower.recycle();
        }
        try(Fixture fixture=new Fixture()){
            Map<String,Object> icons=map("cellular.stacked_primary.4",map("source","assets/upper.png","render","mask"),"cellular.stacked_secondary.1",map("source","assets/lower.png","render","mask"),"cellular.4",map("source","assets/upper.png","render","mask"));Map<String,byte[]> assets=new LinkedHashMap<>();assets.put("assets/upper.png",rowPng(true));assets.put("assets/lower.png",rowPng(false));IconManagementRepository.Pack pack=fixture.install("two-row-png",icons,assets);fixture.configure(suite(pack.id,"cellular"));
            IconPackDrawing helper=new IconPackDrawing();Graphic nativeGlyph=new Graphic();nativeGlyph.setBounds(0,0,190,160);Capture captured=new Capture();require(helper.drawSignal("stat_signal_lte_signal_stacked_primary_4",nativeGlyph,captured,0xffffffff,255,null,false),"rectangular PNG uses the same native row contract");near(0,captured.bounds.top,"PNG has no synthetic XML letterbox");near(160,captured.bounds.bottom,"PNG keeps its full common canvas");
            Bitmap upper=Bitmap.createBitmap(190,160,Bitmap.Config.ARGB_8888),lower=Bitmap.createBitmap(190,160,Bitmap.Config.ARGB_8888);helper.drawSignal("stat_signal_lte_signal_stacked_primary_4",nativeGlyph,new Canvas(upper),0xffffffff,255,null,false);helper.drawSignal("stat_signal_lte_signal_stacked_secondary_1",nativeGlyph,new Canvas(lower),0xffffffff,255,null,false);require(rasterBounds(upper).bottom<rasterBounds(lower).top,"real rectangular PNG rows stay separated");
            Bitmap preview=IconManagementRepository.preview(fixture.module,pack.id,"cellular.stacked_primary.4",128);equal(114,preview.getWidth(),"stacked PNG preview keeps common canvas width");equal(96,preview.getHeight(),"stacked PNG preview keeps the other row's reserved space");preview.recycle();upper.recycle();lower.recycle();helper.releaseRuntime();
            Bitmap selectedPreview=IconManagementRepository.preview(fixture.module,pack.id,"cellular.4",128,"cellular.stacked_secondary.1");equal(96,selectedPreview.getHeight(),"manual source choice uses destination row geometry in the target preview");selectedPreview.recycle();
        }
    }
    private static void individualSize()throws Exception{
        try(Fixture fixture=new Fixture()){
            IconManagementRepository.Pack pack=fixture.mixed();String base=suite(pack.id,"wifi");fixture.configure(base);Graphic host=new Graphic();host.filter=null;Capture before=draw(fixture,"wifi.1",host,0xffffffff);
            long revision=IconManagementRuntime.sourceRevision();int reads=fixture.provider.opens.get(),allocations=Bitmap.creations;
            String sized=IconManagementSettings.applyAppearance(base,"target:wifi",new IconManagementSettings.Appearance("inherit",0xff000000,0xffffffff,null,150f));fixture.configure(sized);Capture grown=draw(fixture,"wifi.1",host,0xffffffff);
            near(before.bounds.width()*1.5f,grown.bounds.width(),"individual size scales actual static artwork");near(before.bounds.height()*1.5f,grown.bounds.height(),"static artwork keeps aspect ratio");near(before.bounds.centerX(),grown.bounds.centerX(),"static size keeps native center X");near(before.bounds.centerY(),grown.bounds.centerY(),"static size keeps native center Y");equal(new Rect(10,20,110,120),host.getBounds(),"size never changes OEM drawable bounds");equal(revision,IconManagementRuntime.sourceRevision(),"size-only updates preserve source revision");equal(reads,fixture.provider.opens.get(),"size-only updates open no resources");equal(allocations,Bitmap.creations,"size-only updates decode no new bitmaps");
            fixture.configure(IconManagementSettings.applyAppearance(sized,"target:wifi",new IconManagementSettings.Appearance("inherit",0xff000000,0xffffffff,null,100f)));near(before.bounds.width(),draw(fixture,"wifi.1",host,0xffffffff).bounds.width(),"reset100 restores original size");
            String nativeOnly=IconManagementSettings.applyAppearance(IconManagementSettings.DEFAULT,"target:hint.location",new IconManagementSettings.Appearance("custom",0xff112233,0xffddeeff,25f,75f));fixture.configure(nativeOnly);Graphic nativeGraphic=new Graphic();ColorFilter nativeFilter=nativeGraphic.filter;View owner=new View(fixture.system);Drawable wrapper=IconManagementRuntime.wrap(owner,"hint.location.default",nativeGraphic);Canvas canvas=new Canvas();int saved=canvas.getSaveCount();wrapper.draw(canvas);
            near(.75f,canvas.scaleX,"native-only glyph uses the same size factor");equal(saved,canvas.getSaveCount(),"native size restores the Canvas save stack");equal(new Rect(10,20,110,120),nativeGraphic.getBounds(),"native size preserves drawable bounds");equal(50,nativeGraphic.drawAlpha,"native opacity remains independent of size");equal(200,nativeGraphic.alpha,"native alpha restored after sized draw");equal(nativeFilter,nativeGraphic.filter,"native filter restored after sized draw");
            nativeGraphic.fail=true;try{wrapper.draw(new Canvas());throw new AssertionError("native sized draw should fail");}catch(IllegalStateException expected){}equal(200,nativeGraphic.alpha,"failed sized draw restores original alpha");equal(nativeFilter,nativeGraphic.filter,"failed sized draw restores original filter");equal(false,IconManagementRuntime.nativeDrawing(nativeGraphic),"failed sized draw releases recursion guard");java.lang.ref.Reference.reachabilityFence(owner);
        }
    }
    private static void atomicPublication()throws Exception{
        try(Fixture fixture=new Fixture()){
            IconManagementRepository.Pack old=fixture.mixed();
            Map<String,Object> icons=map("wifi.1",map("source","assets/first.png","render","mask"),"wifi.2",map("source","assets/last.png","render","color"));
            Map<String,byte[]> assets=new LinkedHashMap<>();assets.put("assets/first.png",png(GREEN,8,4));assets.put("assets/last.png",png(RED,8,4));
            IconManagementRepository.Pack replacement=fixture.install("next",icons,assets);
            fixture.configure(suite(old.id,"wifi"));Bitmap first=IconManagementRuntime.bitmapForWifi(1,true),second=IconManagementRuntime.bitmapForWifi(2,true);
            View owner=new View(fixture.system);Graphic nativeGraphic=new Graphic();nativeGraphic.filter=null;Drawable bound=IconManagementRuntime.wrap(owner,"wifi.2",nativeGraphic);settle();
            long revision=IconManagementRuntime.sourceRevision();Gate renamed=new Gate(old.id+"/manifest.json");fixture.provider.gate=renamed;
            IconManagementRepository.rename(fixture.module,old.id,"renamed");renamed.await();
            try{
                equal(first,IconManagementRuntime.bitmapForWifi(1,true),"library rename keeps the visible family during manifest validation");
                equal(second,IconManagementRuntime.bitmapForWifi(2,true),"library metadata event never exposes an empty bitmap cache");
                equal(revision,IconManagementRuntime.sourceRevision(),"metadata-only refresh does not restart native icon interactions");
                Capture canvas=new Capture();bound.draw(canvas);equal(second,canvas.bitmap,"already attached drawable keeps its image while the worker is pending");
            }finally{renamed.resume.countDown();settle();}
            equal(revision,IconManagementRuntime.sourceRevision(),"verified same source family preserves native bindings after publication");
            first=IconManagementRuntime.bitmapForWifi(1,true);second=IconManagementRuntime.bitmapForWifi(2,true);
            Gate switching=new Gate(replacement.id+"/assets/last.png");fixture.provider.gate=switching;
            String next=suite(replacement.id,"wifi");IconManagementRuntime.configure(fixture.system,settings(next));switching.await();
            try{
                equal(first,IconManagementRuntime.bitmapForWifi(1,true),"first decoded state is not published before the rest of the family");
                equal(second,IconManagementRuntime.bitmapForWifi(2,true),"pack change retains the last complete presentation until ready");
                String styled=IconManagementSettings.applyAppearance(next,"target:wifi",new IconManagementSettings.Appearance("custom",0xff123456,0xffabcdef,50f,150f));
                IconManagementRuntime.configure(fixture.system,settings(styled));Capture canvas=new Capture();IconManagementRuntime.nativeTint(bound,0xff111111);bound.draw(canvas);
                equal(100,canvas.alpha,"appearance edits apply once while a source batch is pending");
            }finally{switching.resume.countDown();settle();}
            require(first!=IconManagementRuntime.bitmapForWifi(1,true),"complete next family replaces the prior first state");
            require(second!=IconManagementRuntime.bitmapForWifi(2,true),"complete next family replaces the prior second state");
            Capture canvas=new Capture();bound.draw(canvas);equal(100,canvas.alpha,"completed source batch keeps the latest opacity edit");equal(0xff123456,((PorterDuffColorFilter)canvas.filter).getColor(),"completed source batch keeps the latest color edit");
            Gate restoring=new Gate(old.id+"/manifest.json");fixture.provider.gate=restoring;
            String system=IconManagementSettings.applyTarget(suite(old.id,"wifi"),"wifi",Collections.singletonMap("wifi.2",new IconManagementSettings.Override("system","","")),null,Collections.emptyMap(),true);
            IconManagementRuntime.configure(fixture.system,settings(system));restoring.await();
            try{equal(null,IconManagementRuntime.bitmapForWifi(2,true),"explicit system selection removes the prior replacement immediately");canvas=new Capture();bound.draw(canvas);equal(0,canvas.calls,"restore system does not wait for unrelated pending assets");}
            finally{restoring.resume.countDown();settle();}
            java.lang.ref.Reference.reachabilityFence(owner);
        }
    }
    private static class ProtocolGraphic extends Graphic {
        boolean mirrored;int jumps;float hotspotX,hotspotY;Rect hotspot=new Rect();
        @Override public void setAutoMirrored(boolean value){mirrored=value;}
        @Override public boolean isAutoMirrored(){return mirrored;}
        @Override public void setHotspot(float x,float y){hotspotX=x;hotspotY=y;}
        @Override public void setHotspotBounds(int l,int t,int r,int b){hotspot.set(l,t,r,b);}
        @Override public void getHotspotBounds(Rect out){out.set(hotspot);}
        @Override public void jumpToCurrentState(){jumps++;}
    }
    private static final class MotionGraphic extends ProtocolGraphic implements android.graphics.drawable.Animatable {
        boolean running;public void start(){running=true;}public void stop(){running=false;}public boolean isRunning(){return running;}
    }
    private static void bindingProtocol()throws Exception{
        try(Fixture fixture=new Fixture()){
            IconManagementRepository.Pack pack=fixture.mixed();fixture.configure(suite(pack.id,"wifi"));View owner=new View(fixture.system);
            ProtocolGraphic graphic=new ProtocolGraphic();graphic.setLayoutDirection(1);graphic.setVisible(false,false);Drawable bound=IconManagementRuntime.wrap(owner,"wifi.1",graphic);settle();
            equal(1,bound.getLayoutDirection(),"bound replacement retains the native layout direction");equal(false,bound.isVisible(),"bound replacement retains native visibility before the first draw");
            bound.setLayoutDirection(0);equal(0,graphic.getLayoutDirection(),"direction changes continue to reach the original drawable");
            bound.setAutoMirrored(true);equal(true,graphic.mirrored,"mirror policy reaches the native graphic");equal(true,bound.isAutoMirrored(),"mirror policy remains readable from the wrapper");
            bound.setHotspot(2.5f,7.5f);near(2.5f,graphic.hotspotX,"interaction hotspot X forwards unchanged");near(7.5f,graphic.hotspotY,"interaction hotspot Y forwards unchanged");
            bound.setHotspotBounds(2,3,17,19);Rect region=new Rect();bound.getHotspotBounds(region);equal(new Rect(2,3,17,19),region,"hotspot bounds survive the replacement");
            bound.jumpToCurrentState();equal(1,graphic.jumps,"settle native state is not swallowed");bound.setVisible(true,false);equal(true,graphic.isVisible(),"visible change reaches native object");
            AtomicInteger scheduled=new AtomicInteger(),removed=new AtomicInteger(),invalidated=new AtomicInteger();bound.setCallback(new Drawable.Callback(){
                public void invalidateDrawable(Drawable who){invalidated.incrementAndGet();}public void scheduleDrawable(Drawable who,Runnable task,long time){scheduled.incrementAndGet();}public void unscheduleDrawable(Drawable who,Runnable task){removed.incrementAndGet();}
            });Runnable frame=()->{};graphic.scheduleSelf(frame,10);graphic.unscheduleSelf(frame);graphic.invalidateSelf();
            equal(1,scheduled.get(),"native frame scheduling reaches host");equal(1,removed.get(),"native cancellation reaches host");equal(1,invalidated.get(),"native invalidation reaches host");
            MotionGraphic animated=new MotionGraphic();equal(animated,IconManagementRuntime.wrap(owner,"wifi.1",animated),"native animation type protocol remains intact");
            java.lang.ref.Reference.reachabilityFence(owner);
        }
    }
    private static byte[] familyPng(boolean full)throws Exception{
        BufferedImage image=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        for(int y=full?4:24;y<28;y++)for(int x=full?4:14;x<(full?28:18);x++)image.setRGB(x,y,RED);
        ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);return output.toByteArray();
    }
    private static void familyRecovery()throws Exception{
        try(Fixture fixture=new Fixture()){
            Map<String,Object> icons=map("wifi.0",map("source","assets/low.png","render","color"),"wifi.4",map("source","assets/full.png","render","color"),
                    "cellular.0",map("source","assets/low.png","render","color"),"cellular.4",map("source","assets/full.png","render","color"));
            Map<String,byte[]> assets=new LinkedHashMap<>();assets.put("assets/low.png",familyPng(false));assets.put("assets/full.png",familyPng(true));
            IconManagementRepository.Pack pack=fixture.install("family-recovery",icons,assets);fixture.provider.failOnceSuffix="/assets/full.png";
            fixture.configure(suite(pack.id,"wifi","cellular"));equal(null,fixture.provider.failOnceSuffix,"first peer transport failure was exercised");
            Graphic host=new Graphic();host.filter=null;Capture low=draw(fixture,"wifi.0",host,0xffffffff),full=draw(fixture,"wifi.4",host,0xffffffff);
            require(low.bitmap!=null&&full.bitmap!=null,"selected family states survive a transient peer failure");
            IconVisibleBounds visible=IconManagementRuntime.imageVisibleBounds("wifi.4");
            require(full.sourceBounds.left<=visible.left&&full.sourceBounds.top<=visible.top&&full.sourceBounds.right>=visible.right&&full.sourceBounds.bottom>=visible.bottom,"recovered peer is never clipped by an incomplete cached family frame");
            equal(low.sourceBounds,full.sourceBounds,"earlier and recovered states publish one stable family frame");equal(new Rect(0,0,32,32),full.sourceBounds,"incomplete family uses conservative full canvas for this batch");
            near(low.bounds.left,full.bounds.left,"recovered family left remains stable");near(low.bounds.top,full.bounds.top,"recovered family top remains stable");
            Capture mobile=draw(fixture,"cellular.4",host,0xffffffff);equal(new Rect(4,4,28,28),mobile.sourceBounds,"unaffected family keeps its compact union");
            Bitmap preview=IconManagementRepository.preview(fixture.module,pack.id,"wifi.4",128);equal(24,preview.getWidth(),"healthy preview uses normal union rather than transient runtime fallback");preview.recycle();
            fixture.notifyResources();Capture refreshedLow=draw(fixture,"wifi.0",host,0xffffffff),refreshedFull=draw(fixture,"wifi.4",host,0xffffffff);
            equal(new Rect(4,4,28,28),refreshedFull.sourceBounds,"next successful resource refresh restores compact family geometry");equal(refreshedLow.sourceBounds,refreshedFull.sourceBounds,"refreshed family still publishes aligned states");
        }
        try(Fixture fixture=new Fixture()){
            Map<String,Object> icons=map("wifi.0",map("source","assets/low.png","render","color"),"wifi.4",map("source","assets/blank.png","render","color"));
            Map<String,byte[]> assets=new LinkedHashMap<>();assets.put("assets/low.png",familyPng(false));assets.put("assets/blank.png",artwork(32,32,0,0,0,0,0));
            IconManagementRepository.Pack pack=fixture.install("family-invalid-peer",icons,assets);fixture.configure(suite(pack.id,"wifi"));
            Capture low=draw(fixture,"wifi.0",new Graphic(),0xffffffff);equal(new Rect(0,0,32,32),low.sourceBounds,"peer decode failure also keeps a safe family canvas");
            Graphic blank=new Graphic();Capture missing=draw(fixture,"wifi.4",blank,0xffffffff);equal(0,missing.calls,"invalid family state does not become an invented image");equal(1,blank.draws,"invalid family state still falls back to native");
        }
    }
    /** The public repository ships only the original C17 library; the PUI pack is optional local input. */
    private static String[] bundledArchives(){
        java.util.List<String> present=new java.util.ArrayList<>();
        for(String archive:new String[]{"c17-rounded-27.zip","pui-coloros17.zip"})
            if(Path.of("app/src/main/assets/icon-management-v3",archive).toFile().isFile())present.add(archive);
        require(present.contains("c17-rounded-27.zip"),"the original C17 library is always bundled");
        return present.toArray(new String[0]);
    }
    private static void familyGeometry()throws Exception{
        for(String archive:bundledArchives())try(Fixture fixture=new Fixture()){
            IconManagementRepository.Pack pack;
            try(IconManagementRepository.PreparedPack prepared=IconManagementRepository.prepare(fixture.module,Path.of("app/src/main/assets/icon-management-v3",archive).toFile(),"")){pack=prepared.commit(()->true);}
            fixture.configure(suite(pack.id,"wifi","cellular"));
            for(String family:new String[]{"wifi","cellular"}){
                // PUI explicitly gives wifi.4 a different 20.5 x 15 viewport; levels 0–3 use 20 x 14.4.
                // Do not force unrelated canvas dimensions into the same family frame.
                boolean differentCanvas=archive.startsWith("pui")&&family.equals("wifi");int anchor=differentCanvas?3:4;
                Graphic host=new Graphic();host.filter=null;Capture full=draw(fixture,family+'.'+anchor,host,0xffffffff);require(full.bitmap!=null,"real bundled family decodes its reference state: "+archive);
                for(int level=0;level<4;level++){
                    Capture lower=draw(fixture,family+'.'+level,host,0xffffffff);
                    equal(full.sourceBounds,lower.sourceBounds,"signal levels share one source frame instead of enlarging each partial glyph: "+archive+' '+family+'.'+level);
                    near(full.bounds.left,lower.bounds.left,"family draw anchor remains stable across levels");near(full.bounds.top,lower.bounds.top,"family top remains stable across levels");
                }
                if(differentCanvas)require(!full.sourceBounds.equals(draw(fixture,"wifi.4",host,0xffffffff).sourceBounds),"different declared PUI canvas dimensions retain their own frame");
                Bitmap fullPreview=IconManagementRepository.preview(fixture.module,pack.id,family+'.'+anchor,128);
                for(int level=0;level<4;level++){Bitmap preview=IconManagementRepository.preview(fixture.module,pack.id,family+'.'+level,128);equal(fullPreview.getWidth(),preview.getWidth(),"preview uses the same family frame width");equal(fullPreview.getHeight(),preview.getHeight(),"preview uses the same family frame height");preview.recycle();}fullPreview.recycle();
            }
            if(archive.startsWith("c17")){
                Graphic host=new Graphic();host.filter=null;Capture dot=draw(fixture,"wifi.0",host,0xffffffff);IconVisibleBounds visible=IconManagementRuntime.imageVisibleBounds("wifi.0");
                require(visible.width<dot.sourceBounds.width()/2,"weak signal point remains small inside the family frame");require(visible.top>dot.sourceBounds.top+dot.sourceBounds.height()/2,"weak signal point keeps its designed lower anchor");
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0){if("size".equals(args[0]))individualSize();else if("stacked".equals(args[0]))stackedSignals();else if("atomic".equals(args[0]))atomicPublication();else if("protocol".equals(args[0]))bindingProtocol();else if("family".equals(args[0]))familyGeometry();else if("recovery".equals(args[0]))familyRecovery();else throw new IllegalArgumentException("Unknown focused check");System.out.println("IconManagementRuntimeCheck "+args[0]+" passed: "+checks);return;}
        idleAndMalformed();selectorsAndHotPath();appearanceAndRestore();sourceRevisionAndStyle();resourcesAndGeneration();budgetAndResidentCache();managerSwitch();visibleBoundsAndNativePipeline();stackedSignals();individualSize();atomicPublication();bindingProtocol();familyGeometry();familyRecovery();
        System.out.println("IconManagementRuntimeCheck passed: "+checks+" (real file/provider/PNG worker/draw path; no injected runtime images)");
    }
}
