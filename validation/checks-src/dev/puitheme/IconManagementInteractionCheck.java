package dev.puitheme;

import android.content.Context;
import android.graphics.Bitmap;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Real library/preview and saved-selection paths used by the icon editor. */
public final class IconManagementInteractionCheck {
    private static int checks;
    private static final String A="a".repeat(64),B="b".repeat(64);
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(why+": "+expected+" != "+actual);}
    private static Map<String,Object> map(Object... pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
    private static final class FilesContext extends Context {
        final Path root;FilesContext(Path root){this.root=root;}
        @Override public File getFilesDir(){return root.toFile();}
    }
    private static byte[] png(int color)throws IOException{BufferedImage image=new BufferedImage(4,4,BufferedImage.TYPE_INT_ARGB);image.setRGB(1,1,color);ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);return output.toByteArray();}
    private static Path archive(Path root,boolean exact)throws Exception {
        Map<String,Object> icons=map("hint.alarm_clock",map("source","assets/generic.png","render","color"));
        if(exact)icons.put("hint.alarm_clock.default",map("source","assets/exact.png","render","color"));
        Map<String,byte[]> entries=new LinkedHashMap<>();
        entries.put("manifest.json",IconManagementJson.encode(map("format",IconManagementManifest.FORMAT,"version",3,"id",exact?"exact-hint":"generic-hint","name",exact?"Exact hint":"Generic hint","author","test","license","MIT","fallback","native","icons",icons,"transitions",map())).getBytes(StandardCharsets.UTF_8));
        entries.put("assets/generic.png",png(0xffff0000));if(exact)entries.put("assets/exact.png",png(0xff0000ff));
        Path archive=Files.createTempFile(root,"icons-",".zip");try(ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(archive))){for(Map.Entry<String,byte[]> row:entries.entrySet()){output.putNextEntry(new ZipEntry(row.getKey()));output.write(row.getValue());output.closeEntry();}}return archive;
    }
    private static void pixel(Bitmap bitmap,int color,String why){try{int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());equal(true,Arrays.stream(pixels).anyMatch(value->value==color),why);}finally{bitmap.recycle();}}
    private static void previews()throws Exception {
        Path root=Files.createTempDirectory("c17-icon-interaction-");FilesContext context=new FilesContext(root);
        try {
            for(boolean exact:new boolean[]{false,true})try(IconManagementRepository.PreparedPack pending=IconManagementRepository.prepare(context,archive(root,exact).toFile(),"")){
                String expected=exact?"hint.alarm_clock.default":"hint.alarm_clock";int color=exact?0xff0000ff:0xffff0000;
                String records=IconManagementSettings.applyPack(IconManagementSettings.DEFAULT,pending.pack.id,Collections.singleton("hint"),false);
                IconManagementSelection.Plan plan=IconManagementSelection.plan(IconManagementSettings.parse(records),"hint.alarm_clock.default",Collections.singletonMap(pending.pack.id,pending.pack.manifest));
                equal(expected,plan.candidates.get(0).role,"runtime chooses exact before generic hint");
                pixel(pending.preview("hint.alarm_clock.default",32),color,"direct pending preview shares source resolution");
                pixel(IconManagementRepository.previewPrepared(pending,"hint.alarm_clock.default",32,"hint.alarm_clock.default"),color,"pending preview matches runtime source");
                IconManagementRepository.Pack pack=pending.commit(()->true);
                pixel(IconManagementRepository.preview(context,pack.id,"hint.alarm_clock.default",32,"hint.alarm_clock.default"),color,"installed preview matches runtime source");
                equal(0,IconManagementSelection.plan(IconManagementSettings.parse(records),"hint.vpn.default",Collections.singletonMap(pack.id,pack.manifest)).candidates.size(),"unrelated hint never borrows generic artwork");
            }
        } finally {IconManagementRepository.clear(context);try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    private static void drafts()throws Exception {
        String base=IconManagementSettings.applyPack(IconManagementSettings.DEFAULT,A,Arrays.asList("wifi","hint"),false);
        Map<String,IconManagementSettings.Override> selected=Collections.singletonMap("wifi.4",new IconManagementSettings.Override("pack",A,"wifi.1"));
        base=IconManagementSettings.applyTarget(base,"wifi",selected,new IconManagementSettings.Appearance("custom",0xff112233,0xffddeeff,65f,100f),Collections.emptyMap(),false);
        String draft=IconManagementSettings.applyTarget(base,"wifi",selected,new IconManagementSettings.Appearance("custom",0xff112233,0xffddeeff,35f,140f),Collections.emptyMap(),false);
        String current=IconManagementSettings.applyPack(base,B,Collections.singleton("wifi"),true);
        IconColorPresets.Preset palette=IconColorPresets.parse(map("latest",map("name","Latest palette","appearances",map("scope:wifi",map("mode","custom","lightColor",0xff445566,"darkColor",0xff778899))))).get("latest");
        current=IconManagementSettings.applyColorPreset(current,palette,Collections.singleton("wifi"));
        String merged=IconManagementSettings.rebaseTargetDraft(base,draft,current,"wifi");IconManagementSettings.State state=IconManagementSettings.parse(merged);
        equal(false,state.overrides.containsKey("wifi.4"),"saved suite clearing cannot revive an old target source");
        equal("inherit",state.appearances.get("target:wifi").mode,"saved palette clearing cannot revive target colors");
        equal(35f,state.appearances.get("target:wifi").opacity,"unconflicted draft opacity survives palette change");
        equal(140f,state.appearances.get("target:wifi").sizePercent,"unconflicted draft size survives palette change");
        equal(false,state.animations.get("wifi"),"independent animation preference survives");
        equal(B,state.scopePacks.get("wifi"),"latest suite kept");equal(A,state.scopePacks.get("hint"),"unrelated suite kept");
        equal(0xff445566,IconManagementSelection.appearance(state,"wifi.4",false,"mask").color,"current scope color actually wins rendering plan");
        equal(merged,IconManagementSettings.rebaseTargetDraft(current,merged,current,"wifi"),"return followed by save is idempotent");
        equal(draft,IconManagementSettings.rebaseTargetDraft(base,draft,base,"wifi"),"ordinary editing retains every draft field");

        // A commit after the preview frame is merged again by the save path.
        String late=IconManagementSettings.applyAppearance(current,"target:wifi",new IconManagementSettings.Appearance("custom",0xff010203,0xff040506,0f,75f));
        IconManagementSettings.State saved=IconManagementSettings.parse(IconManagementSettings.rebaseTargetDraft(current,merged,late,"wifi"));
        equal(0f,saved.appearances.get("target:wifi").opacity,"late explicit zero opacity wins");
        equal(75f,saved.appearances.get("target:wifi").sizePercent,"late committed size wins");
        equal(0xff010203,saved.appearances.get("target:wifi").lightColor,"late color wins as one light/dark pair");

        // Different roles and the three independent appearance dimensions do not clobber one another.
        String roleBase=IconManagementSettings.applyAppearance(base,"role:wifi.1",new IconManagementSettings.Appearance("custom",0xffaabbcc,0xff123456,80f,90f));
        String roleDraft=IconManagementSettings.applyAppearance(roleBase,"role:wifi.1",new IconManagementSettings.Appearance("custom",0xffaabbcc,0xff123456,40f,120f));
        roleDraft=IconManagementSettings.applyAppearance(roleDraft,"role:wifi.2",new IconManagementSettings.Appearance("inherit",0xff000000,0xffffffff,25f,null));
        String roleCurrent=IconManagementSettings.applyColorPreset(roleBase,palette,Collections.singleton("wifi"));
        roleCurrent=IconManagementSettings.applyAppearance(roleCurrent,"target:hint.vpn",new IconManagementSettings.Appearance("custom",0xff555555,0xffeeeeee,10f,70f));
        IconManagementSettings.State roles=IconManagementSettings.parse(IconManagementSettings.rebaseTargetDraft(roleBase,roleDraft,roleCurrent,"wifi"));
        equal("inherit",roles.appearances.get("role:wifi.1").mode,"palette removes shadowing state color");
        equal(40f,roles.appearances.get("role:wifi.1").opacity,"state opacity draft survives palette");
        equal(120f,roles.appearances.get("role:wifi.1").sizePercent,"state size draft survives palette");
        equal(25f,roles.appearances.get("role:wifi.2").opacity,"new draft state opacity survives unrelated committed changes");
        equal(70f,roles.appearances.get("target:hint.vpn").sizePercent,"other target is preserved from current records");

        String reset=IconManagementSettings.rebaseTargetDraft(base,draft,IconManagementSettings.reset(),"wifi");
        equal(IconManagementSettings.encode(IconManagementSettings.parse(IconManagementSettings.DEFAULT)),reset,"latest reset does not revive saved sources, styles or animation override");
        String deleted=IconManagementSettings.removePack(base,A);
        saved=IconManagementSettings.parse(IconManagementSettings.rebaseTargetDraft(base,draft,deleted,"wifi"));
        equal(0,saved.overrides.size(),"deleted library source does not revive");
        equal(140f,saved.appearances.get("target:wifi").sizePercent,"library removal retains independent draft appearance");

        String cleared=IconManagementSettings.clearScopePack(base,"wifi");
        saved=IconManagementSettings.parse(cleared);
        equal(false,saved.scopePacks.containsKey("wifi"),"explicit removal clears only requested scope");
        equal(A,saved.scopePacks.get("hint"),"explicit removal preserves another scope");
        equal(A,saved.overrides.get("wifi.4").packId,"explicit removal preserves individual source");
        equal(65f,saved.appearances.get("target:wifi").opacity,"explicit removal preserves opacity");
        equal(100f,saved.appearances.get("target:wifi").sizePercent,"explicit removal preserves size");
        equal(false,saved.animations.get("wifi"),"explicit removal preserves animation");
        equal(2,IconManagementSettings.parse(base).scopePacks.size(),"draft, cancel and preview never mutate the saved input");

        Map<String,IconManagementSettings.Override> draftSources=new LinkedHashMap<>();
        draftSources.put("wifi.4",new IconManagementSettings.Override("system","",""));
        draftSources.put("wifi.1",new IconManagementSettings.Override("pack",B,"wifi.2"));
        String sourcesDraft=IconManagementSettings.applyTarget(base,"wifi",draftSources,null,Collections.emptyMap(),true);
        Map<String,IconManagementSettings.Override> latestSources=new LinkedHashMap<>();
        latestSources.put("wifi.4",new IconManagementSettings.Override("pack",B,"wifi.3"));
        latestSources.put("wifi.2",new IconManagementSettings.Override("system","",""));
        String sourcesCurrent=IconManagementSettings.applyTarget(base,"wifi",latestSources,null,Collections.emptyMap(),true);
        saved=IconManagementSettings.parse(IconManagementSettings.rebaseTargetDraft(base,sourcesDraft,sourcesCurrent,"wifi"));
        equal("wifi.3",saved.overrides.get("wifi.4").sourceRole,"newly committed source wins same-role draft conflict");
        equal("wifi.2",saved.overrides.get("wifi.1").sourceRole,"another role's new draft source survives");
        equal("system",saved.overrides.get("wifi.2").mode,"another role's new committed system source survives");
        equal(null,saved.animations.get("wifi"),"committed animation reset remains enabled");
    }
    private static void deletedDraft()throws Exception {
        Path root=Files.createTempDirectory("c17-icon-draft-delete-");FilesContext context=new FilesContext(root);
        try {
            IconManagementRepository.Pack pack;
            try(IconManagementRepository.PreparedPack pending=IconManagementRepository.prepare(context,archive(root,false).toFile(),"")){pack=pending.commit(()->true);}
            String base=IconManagementSettings.DEFAULT;
            String draft=IconManagementSettings.applyTarget(base,"hint.alarm_clock",Collections.singletonMap("hint.alarm_clock.default",new IconManagementSettings.Override("pack",pack.id,"hint.alarm_clock")),new IconManagementSettings.Appearance("inherit",0xff000000,0xffffffff,0f,200f),Collections.emptyMap(),false);
            String[] saved={base};
            try(IconManagementRepository.PreparedDeletion deletion=IconManagementRepository.prepareDeletion(context,pack.id)){
                String next=IconManagementSettings.removePack(saved[0],pack.id);
                deletion.commit(()->true,()->{saved[0]=next;return true;},()->saved[0]=base);
            }
            Map<String,IconManagementManifest> available=new LinkedHashMap<>();for(IconManagementRepository.Pack installed:IconManagementRepository.list(context))available.put(installed.id,installed.manifest);
            equal(0,available.size(),"real deletion leaves an authoritative empty library");
            String merged=IconManagementSettings.rebaseTargetDraft(base,draft,saved[0],"hint.alarm_clock",available);
            IconManagementSettings.State state=IconManagementSettings.parse(merged);
            equal(0,state.overrides.size(),"a newly drafted source from a deleted library cannot block subsequent save");
            equal(0f,state.appearances.get("target:hint.alarm_clock").opacity,"deleted draft source retains explicit zero opacity");
            equal(200f,state.appearances.get("target:hint.alarm_clock").sizePercent,"deleted draft source retains maximum size");
            equal("inherit",state.appearances.get("target:hint.alarm_clock").mode,"deleted draft source retains color inheritance");
            equal(false,state.animations.get("hint.alarm_clock"),"deleted draft source retains animation choice");
            equal(merged,IconManagementSettings.rebaseTargetDraft(saved[0],merged,saved[0],"hint.alarm_clock",available),"return then save does not resurrect deleted draft source");
            equal(merged,IconManagementSettings.rebaseTargetDraft(saved[0],merged,saved[0],"hint.alarm_clock",null),"a later library refresh cannot revive an already discarded source");
            state=IconManagementSettings.parse(IconManagementSettings.rebaseTargetDraft(base,draft,saved[0],"hint.alarm_clock",null));
            equal(pack.id,state.overrides.get("hint.alarm_clock.default").packId,"unknown or loading library never clears a draft");
            state=IconManagementSettings.parse(IconManagementSettings.rebaseTargetDraft(draft,draft,draft,"hint.alarm_clock",available));
            equal(pack.id,state.overrides.get("hint.alarm_clock.default").packId,"existing persisted missing sources are not silently rewritten");
            String mismatch=IconManagementSettings.applyTarget(base,"hint.alarm_clock",Collections.singletonMap("hint.alarm_clock.default",new IconManagementSettings.Override("pack",pack.id,"hint.vpn.default")),null,Collections.emptyMap(),true);
            Map<String,IconManagementManifest> present=Collections.singletonMap(pack.id,pack.manifest);
            state=IconManagementSettings.parse(IconManagementSettings.rebaseTargetDraft(base,mismatch,base,"hint.alarm_clock",present));
            equal(0,state.overrides.size(),"installed library with a missing requested source is rejected by shared resolver");
            String alias=IconManagementSettings.applyTarget(base,"hint.alarm_clock",Collections.singletonMap("hint.alarm_clock.default",new IconManagementSettings.Override("pack",pack.id,"hint.alarm_clock.default")),null,Collections.emptyMap(),true);
            state=IconManagementSettings.parse(IconManagementSettings.rebaseTargetDraft(base,alias,base,"hint.alarm_clock",present));
            equal("hint.alarm_clock",state.overrides.get("hint.alarm_clock.default").sourceRole,"valid shorthand is canonicalized to the source accepted by the save gate");
            String prior=IconManagementSettings.applyTarget(base,"hint.alarm_clock",Collections.singletonMap("hint.alarm_clock.default",new IconManagementSettings.Override("system","","")),null,Collections.emptyMap(),true);
            state=IconManagementSettings.parse(IconManagementSettings.rebaseTargetDraft(prior,draft,prior,"hint.alarm_clock",available));
            equal("system",state.overrides.get("hint.alarm_clock.default").mode,"an unavailable unsaved replacement restores the current saved source");
            equal(0,IconManagementSettings.parse(saved[0]).overrides.size(),"draft preview or cancellation cannot modify actual saved records");
        } finally {IconManagementRepository.clear(context);try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    private static void appearanceBoundaries()throws Exception {
        IconColorPresets.Preset palette=IconColorPresets.parse(map("palette",map("name","Palette","appearances",map("scope:wifi",map("mode","custom","lightColor",0xff123456,"darkColor",0xffabcdef))))).get("palette");
        for(float size:new float[]{25f,200f}){
            String base=IconManagementSettings.applyAppearance(IconManagementSettings.DEFAULT,"target:wifi",new IconManagementSettings.Appearance("custom",0xff112233,0xff445566,null,null));
            String draft=IconManagementSettings.applyAppearance(base,"target:wifi",new IconManagementSettings.Appearance("inherit",0xff000000,0xffffffff,0f,size));
            String current=IconManagementSettings.applyColorPreset(base,palette,Collections.singleton("wifi"));
            String rebased=IconManagementSettings.rebaseTargetDraft(base,draft,current,"wifi",Collections.emptyMap());
            IconManagementSettings.State state=IconManagementSettings.parse(rebased);
            // Use the same final production applyTarget entry as the UI, after its latest-record recheck.
            String applied=IconManagementSettings.applyTarget(current,"wifi",state.overrides,state.appearances.get("target:wifi"),Collections.emptyMap(),true);
            state=IconManagementSettings.parse(applied);
            equal("inherit",state.appearances.get("target:wifi").mode,"preset leaves draft color inheritance explicit when other dimensions remain");
            equal(size,state.appearances.get("target:wifi").sizePercent,"explicit size boundary survives preset and final apply");
            equal(0f,state.appearances.get("target:wifi").opacity,"explicit transparent boundary survives preset and final apply");
            equal(0xff123456,IconManagementSelection.appearance(state,"wifi.4",false,"mask").color,"inherited final light color resolves latest palette");
            equal(0xffabcdef,IconManagementSelection.appearance(state,"wifi.4",true,"mask").color,"inherited final dark color resolves latest palette");
            equal(null,IconManagementSettings.parse(current).appearances.get("target:wifi"),"cancel or back leaves committed palette without unsaved target fields");
            String resetDraft=IconManagementSettings.applyTarget(current,"wifi",Collections.emptyMap(),null,Collections.emptyMap(),true);
            state=IconManagementSettings.parse(IconManagementSettings.rebaseTargetDraft(current,resetDraft,current,"wifi",Collections.emptyMap()));
            equal(null,state.appearances.get("target:wifi"),"explicit target reset keeps size and opacity inherited");
            equal(100f,IconManagementSelection.appearance(state,"wifi.4",false,"mask").sizePercent,"reset uses inherited default size instead of stale draft boundary");
        }
    }
    public static void main(String[] args)throws Exception {
        if(args.length==0||args[0].equals("previews"))previews();
        if(args.length==0||args[0].equals("drafts"))drafts();
        if(args.length==0||args[0].equals("deletion"))deletedDraft();
        if(args.length==0||args[0].equals("appearance"))appearanceBoundaries();
        System.out.println("IconManagementInteractionCheck: "+checks+" checks passed");
    }
}
