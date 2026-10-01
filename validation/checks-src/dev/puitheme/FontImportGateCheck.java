package dev.puitheme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** The final publication gate rejects staged fonts without invoking Android's rename or replacing the old file. */
public final class FontImportGateCheck {
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("c17-font-gate-");
        Path target=directory.resolve("custom.font"),temporary=directory.resolve("import.font");
        try {
            Files.writeString(target,"previous-font");Files.writeString(temporary,"validated-staged-font");
            AtomicInteger checks=new AtomicInteger();
            try {
                FontRepository.publishValidatedFont(temporary.toFile(),target.toFile(),()->{checks.incrementAndGet();return false;});
                throw new AssertionError("Inactive font publication was accepted");
            } catch(IOException expected) { }
            if(checks.get()!=1)throw new AssertionError("Publication did not check current activation");
            if(!Files.readString(target).equals("previous-font"))throw new AssertionError("Rejected import replaced the previous font");
            if(!Files.readString(temporary).equals("validated-staged-font"))throw new AssertionError("Rejected publication touched the staged file before caller cleanup");
            try {
                FontRepository.publishValidatedFont(temporary.toFile(),target.toFile(),()->{throw new IllegalStateException("Activation unavailable");});
                throw new AssertionError("Failed activation check published the font");
            } catch(IllegalStateException expected) { }
            if(!Files.readString(target).equals("previous-font"))throw new AssertionError("Failed activation check replaced the previous font");
            System.out.println("5 font publication gate checks passed");
        } finally { Files.deleteIfExists(temporary);Files.deleteIfExists(target);Files.deleteIfExists(directory); }
    }
}
