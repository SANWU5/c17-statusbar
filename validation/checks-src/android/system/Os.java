package android.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Real desktop filesystem operations for staged font publication and rollback checks. */
public final class Os {
    private Os() { }
    public static void rename(String oldPath,String newPath) throws IOException {
        Files.move(Path.of(oldPath),Path.of(newPath),StandardCopyOption.REPLACE_EXISTING);
    }
    public static void link(String oldPath,String newPath) throws IOException { Files.createLink(Path.of(newPath),Path.of(oldPath)); }
    public static void remove(String path) throws IOException { Files.delete(Path.of(path)); }
}
