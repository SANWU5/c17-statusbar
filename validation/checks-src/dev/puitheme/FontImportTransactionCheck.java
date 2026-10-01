package dev.puitheme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Real file replacement/rollback plus option submission failures, including revocation after publication. */
public final class FontImportTransactionCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {
        checks++;if(!expected.equals(actual))throw new AssertionError("Font transaction check "+checks+": "+actual);
    }
    private static void options(Map<String,String> values) {
        values.put("font_mode","custom");values.put("font_revision","new-revision");values.put("font_name","new-name");
    }
    private static void restore(Map<String,String> values,Map<String,String> previous) {
        for(String key:new String[]{"font_mode","font_revision","font_name"}) {
            if(previous.containsKey(key))values.put(key,previous.get(key));else values.remove(key);
        }
    }
    private static FontRepository.PreparedFont prepare(Path directory,boolean oldFont) throws IOException {
        Path target=directory.resolve("custom.font"),staged=directory.resolve("import.font");
        if(oldFont)Files.writeString(target,"old-font");else Files.deleteIfExists(target);
        Files.writeString(staged,"new-font");
        return new FontRepository.PreparedFont(staged.toFile(),target.toFile(),"new-revision");
    }
    private static void retained(Path directory,boolean oldFont) throws IOException {
        equal(oldFont,Files.exists(directory.resolve("custom.font")));
        if(oldFont)equal("old-font",Files.readString(directory.resolve("custom.font")));
        try(var files=Files.list(directory)) { equal(oldFont?1L:0L,files.count()); }
    }
    private static void rejected(RunnableThrowing action) throws Exception {
        try {action.run();throw new AssertionError("Font transaction accepted a rejected commit");}
        catch(IOException|IllegalStateException expected) { checks++; }
    }
    private interface RunnableThrowing { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("c17-font-transaction-");
        try {
            AtomicInteger submissions=new AtomicInteger(),restorations=new AtomicInteger();
            FontRepository.PreparedFont inactive=prepare(directory,true);
            rejected(()->inactive.commit(()->false,()->{submissions.incrementAndGet();return true;},restorations::incrementAndGet));
            retained(directory,true);equal(0,submissions.get());equal(0,restorations.get());

            AtomicInteger gate=new AtomicInteger();
            FontRepository.PreparedFont beforePublication=prepare(directory,true);
            rejected(()->beforePublication.commit(()->gate.incrementAndGet()==1,()->{submissions.incrementAndGet();return true;},restorations::incrementAndGet));
            retained(directory,true);equal(0,submissions.get());equal(0,restorations.get());

            gate.set(0);FontRepository.PreparedFont afterPublication=prepare(directory,true);
            rejected(()->afterPublication.commit(()->gate.incrementAndGet()<3,()->{submissions.incrementAndGet();return true;},restorations::incrementAndGet));
            retained(directory,true);equal(0,submissions.get());equal(0,restorations.get());

            Map<String,String> previous=new HashMap<>();previous.put("font_mode","pingfang");previous.put("font_name","old-name");
            Map<String,String> current=new HashMap<>(previous);
            FontRepository.PreparedFont failedCommit=prepare(directory,true);
            rejected(()->failedCommit.commit(()->true,()->{options(current);current.put("other_setting","keep");return false;},()->restore(current,previous)));
            retained(directory,true);equal("pingfang",current.get("font_mode"));equal("old-name",current.get("font_name"));equal(false,current.containsKey("font_revision"));equal("keep",current.get("other_setting"));

            FontRepository.PreparedFont failedSubmission=prepare(directory,true);
            rejected(()->failedSubmission.commit(()->true,()->{options(current);throw new IllegalStateException("Storage unavailable");},()->restore(current,previous)));
            retained(directory,true);equal("pingfang",current.get("font_mode"));equal(false,current.containsKey("font_revision"));

            AtomicBoolean active=new AtomicBoolean(true);
            FontRepository.PreparedFont revoked=prepare(directory,true);
            rejected(()->revoked.commit(active::get,()->{options(current);active.set(false);return true;},()->restore(current,previous)));
            retained(directory,true);equal("pingfang",current.get("font_mode"));equal("old-name",current.get("font_name"));equal(false,current.containsKey("font_revision"));

            FontRepository.PreparedFont firstImport=prepare(directory,false);
            current.clear();rejected(()->firstImport.commit(()->true,()->{options(current);return false;},current::clear));
            retained(directory,false);equal(0,current.size());

            FontRepository.PreparedFont successful=prepare(directory,true);
            equal("new-revision",successful.commit(()->true,()->{options(current);return true;},()->{throw new AssertionError("Successful import rolled back");}));
            equal("new-font",Files.readString(directory.resolve("custom.font")));equal("custom",current.get("font_mode"));equal("new-revision",current.get("font_revision"));equal("new-name",current.get("font_name"));
            try(var files=Files.list(directory)) { equal(1L,files.count()); }
            rejected(()->successful.commit(()->true,()->true,()->{}));
            equal("new-font",Files.readString(directory.resolve("custom.font")));

            FontRepository.PreparedFont cancelled=prepare(directory,true);cancelled.close();cancelled.close();retained(directory,true);
            System.out.println("Font import transaction checks passed: "+checks);
        } finally {
            try(var files=Files.list(directory)) { for(Path file:files.toList())Files.deleteIfExists(file); }
            Files.deleteIfExists(directory);
        }
    }
}
