package dev.puitheme;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Real multi-font inodes, provider selection and deletion rollback; no network or Compose mocks. */
public final class FontLibraryCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Font library "+checks+": "+actual+" != "+expected);}
    private static final class FilesContext extends Context {
        private final File directory;FilesContext(Path directory){this.directory=directory.toFile();}
        @Override public File getFilesDir(){return directory;}
    }
    private interface Task {void run()throws Exception;}
    private static void rejected(Task task)throws Exception{try{task.run();throw new AssertionError("Font library accepted rejected transaction");}catch(IOException expected){checks++;}}
    private static FontRepository.PreparedFont staged(Path directory,String content)throws Exception{
        Files.createDirectories(directory.resolve("fonts"));Path path=Files.createTempFile(directory.resolve("fonts"),"import-",".font");
        Files.writeString(path,"\u0000\u0001\u0000\u0000"+content);
        String hash=FontFileValidator.sha256(path.toFile(),1024,null);
        return new FontRepository.PreparedFont(path.toFile(),directory.resolve("fonts/custom.font").toFile(),hash);
    }
    public static void main(String[] args)throws Exception{
        Path directory=Files.createTempDirectory("c17-font-library-");Context context=new FilesContext(directory);
        try{
            FontRepository.PreparedFont a=staged(directory,"first-font"),b=staged(directory,"second-font");String ar=a.revision,br=b.revision;
            FontLibrary.remember(context,a,"我的字体.ttf");FontLibrary.remember(context,b,"另一款.otf");
            equal(2,FontLibrary.entries(context).size());equal("我的字体",FontLibrary.entries(context).get(1).name);
            equal("字体名称",FontLibrary.displayName(" 字体\n名称.TTC "));
            equal("导入字体",FontLibrary.displayName("\n\t"));
            FontLibrary.rename(context,ar,"重新命名");equal(true,FontLibrary.entries(context).stream().anyMatch(entry->entry.revision.equals(ar)&&entry.name.equals("重新命名")));
            rejected(()->FontLibrary.rename(context,"../outside","不允许"));
            a.commit(()->true,()->true,()->{});b.close();
            String first=Files.readString(directory.resolve("fonts/custom.font"));
            try(FontRepository.PreparedFont selected=FontLibrary.prepare(context,br)){
                rejected(()->selected.commit(()->true,()->false,()->{}));
            }
            equal(first,Files.readString(directory.resolve("fonts/custom.font")));equal(2,FontLibrary.entries(context).size());
            try(FontRepository.PreparedFont selected=FontLibrary.prepare(context,br)){selected.commit(()->true,()->true,()->{});}
            equal("\u0000\u0001\u0000\u0000second-font",Files.readString(directory.resolve("fonts/custom.font")));
            try(FontRepository.PreparedFont selected=FontLibrary.prepare(context,ar)){selected.commit(()->true,()->true,()->{});}
            // Library keeps all imported fonts when the provider switches or the network cache disappears.
            Path cache=directory.resolve("fonts/catalog");Files.createDirectories(cache);Files.writeString(cache.resolve("temporary"),"cache");Files.delete(cache.resolve("temporary"));Files.delete(cache);
            equal(2,FontLibrary.entries(context).size());
            Map<String,Object> options=new HashMap<>();options.put(StatusBarSettings.FONT_MODE,"custom");options.put(StatusBarSettings.FONT_REVISION,ar);
            options.put(StatusBarSettings.FONT_NAME,"custom");options.put(NotificationBigClockSettings.FONT,"custom");options.put("unrelated","retain");
            Map<String,String> fallback=FontLibrary.deletionFallback(options,ar);
            equal("system",fallback.get(StatusBarSettings.FONT_MODE));equal("system",fallback.get(NotificationBigClockSettings.FONT));
            equal("",fallback.get(StatusBarSettings.FONT_REVISION));equal("",fallback.get(StatusBarSettings.FONT_NAME));equal(false,fallback.containsKey("unrelated"));
            equal(0,FontLibrary.deletionFallback(options,null).size());equal(0,FontLibrary.deletionFallback(options,br).size());
            options.put(StatusBarSettings.FONT_MODE,"pingfang");equal(false,FontLibrary.deletionFallback(options,ar).containsKey(StatusBarSettings.FONT_MODE));options.put(StatusBarSettings.FONT_MODE,"custom");
            Map<String,Object> original=new HashMap<>(options);
            Runnable restore=()->{for(String key:fallback.keySet())options.put(key,original.get(key));};
            try(FontLibrary.PreparedDeletion deletion=FontLibrary.prepareDeletion(context,ar,true)){
                rejected(()->deletion.commit(()->false,()->{options.putAll(fallback);return true;},restore));
            }
            equal(first,Files.readString(directory.resolve("fonts/custom.font")));equal(2,FontLibrary.entries(context).size());equal(original,options);
            try(FontLibrary.PreparedDeletion deletion=FontLibrary.prepareDeletion(context,ar,true)){
                rejected(()->deletion.commit(()->true,()->{options.putAll(fallback);return false;},restore));
            }
            equal(first,Files.readString(directory.resolve("fonts/custom.font")));equal(2,FontLibrary.entries(context).size());equal(original,options);
            AtomicInteger gate=new AtomicInteger();
            try(FontLibrary.PreparedDeletion deletion=FontLibrary.prepareDeletion(context,ar,true)){
                rejected(()->deletion.commit(()->gate.incrementAndGet()<3,()->{options.putAll(fallback);return true;},restore));
            }
            equal(first,Files.readString(directory.resolve("fonts/custom.font")));equal(2,FontLibrary.entries(context).size());equal(original,options);
            try(FontLibrary.PreparedDeletion deletion=FontLibrary.prepareDeletion(context,ar,true)){
                android.system.Os.removeFailurePath=directory.resolve("fonts/library/"+ar+".font").toString();
                rejected(()->deletion.commit(()->true,()->{options.putAll(fallback);return true;},restore));
            }finally{android.system.Os.removeFailurePath=null;}
            equal(first,Files.readString(directory.resolve("fonts/custom.font")));equal(2,FontLibrary.entries(context).size());equal(original,options);
            try(FontLibrary.PreparedDeletion deletion=FontLibrary.prepareDeletion(context,ar,true)){
                deletion.commit(()->true,()->{options.putAll(fallback);return true;},restore);
            }
            equal(false,Files.exists(directory.resolve("fonts/custom.font")));equal(1,FontLibrary.entries(context).size());equal(br,FontLibrary.entries(context).get(0).revision);
            equal("system",options.get(StatusBarSettings.FONT_MODE));equal("retain",options.get("unrelated"));rejected(()->FontLibrary.prepare(context,ar));
            // Integrity failure cannot replace current data or silently create a newly selected source.
            Files.writeString(directory.resolve("fonts/library/"+br+".font"),"corrupt");rejected(()->FontLibrary.prepare(context,br));
            try(FontRepository.PreparedFont repaired=staged(directory,"second-font")){FontLibrary.remember(context,repaired,"已修复.ttf");}
            try(FontRepository.PreparedFont repaired=FontLibrary.prepare(context,br)){equal(br,repaired.revision);}
            equal(1,FontLibrary.entries(context).size());
            try(var files=Files.walk(directory)){equal(0L,files.filter(path->path.getFileName().toString().endsWith(".backup")||path.getFileName().toString().endsWith(".part")).count());}
            System.out.println("FontLibraryCheck passed: "+checks);
        }finally{
            android.system.Os.removeFailurePath=null;
            try(var files=Files.walk(directory)){for(Path path:files.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }
}
