package dev.puitheme;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import javax.net.ssl.HttpsURLConnection;

/** Scripted HTTPS failure boundaries of the real downloader; no production transport injection. */
public final class FontDownloadTransportCheck {
    private static int checks;
    private static int status=200,inputCalls;
    private static long declared;
    private static byte[] body;
    private static boolean timeout;
    private static Connection active;
    private static final FontCatalog.Entry ENTRY=FontCatalog.find("manrope");
    private static void equal(Object expected,Object actual) {
        checks++;if(!java.util.Objects.equals(expected,actual))throw new AssertionError("Font download check "+checks+": "+actual);
    }
    private static final class Connection extends HttpsURLConnection {
        boolean disconnected;
        Connection(URL url) { super(url); }
        @Override public void connect() { }
        @Override public void disconnect() { disconnected=true; }
        @Override public boolean usingProxy() { return false; }
        @Override public String getCipherSuite() { return "test-only"; }
        @Override public java.security.cert.Certificate[] getLocalCertificates() { return null; }
        @Override public java.security.cert.Certificate[] getServerCertificates() { return null; }
        @Override public int getResponseCode() throws IOException {
            if(timeout)throw new SocketTimeoutException("scripted timeout");return status;
        }
        @Override public long getContentLengthLong() { return declared; }
        @Override public InputStream getInputStream() throws IOException {
            inputCalls++;
            return new InputStream() {
                private final ByteArrayInputStream data=new ByteArrayInputStream(body);
                @Override public int read() throws IOException {
                    if(disconnected)throw new IOException("scripted connection closed");return data.read();
                }
                @Override public int read(byte[] bytes,int offset,int length) throws IOException {
                    if(disconnected)throw new IOException("scripted connection closed");return data.read(bytes,offset,length);
                }
            };
        }
    }
    private static void reset() { status=200;inputCalls=0;declared=ENTRY.bytes;body=new byte[(int)ENTRY.bytes];timeout=false;active=null; }
    private static void retained(Path directory,File target) throws IOException {
        equal("original-cache",Files.readString(target.toPath()));
        equal("current-font",Files.readString(directory.resolve("custom.font")));
        try(var files=Files.list(directory)) { equal(2L,files.count()); }
        if(active!=null) {
            equal(true,active.disconnected);equal(false,active.getInstanceFollowRedirects());
            equal(12000,active.getConnectTimeout());equal(12000,active.getReadTimeout());
        }
    }
    private static void invoke(Method download,File target,FontDownloadRepository.Progress progress,BooleanSupplier cancellation) throws Exception {
        try { download.invoke(null,ENTRY,target,progress,cancellation); }
        catch(InvocationTargetException wrapped) {
            if(wrapped.getCause() instanceof IOException)throw (IOException)wrapped.getCause();throw wrapped;
        }
    }
    private static void rejected(Method download,File target,FontDownloadRepository.Progress progress,BooleanSupplier cancellation) throws Exception {
        try { invoke(download,target,progress,cancellation);throw new AssertionError("Unsafe font download accepted"); }
        catch(IOException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                active=new Connection(url);return active;
            }
        }:null);
        Method download=FontDownloadRepository.class.getDeclaredMethod("download",FontCatalog.Entry.class,File.class,
                FontDownloadRepository.Progress.class,BooleanSupplier.class);download.setAccessible(true);
        Path directory=Files.createTempDirectory("c17-font-transport-");File target=directory.resolve("cached.font").toFile();
        try {
            Files.writeString(target.toPath(),"original-cache");Files.writeString(directory.resolve("custom.font"),"current-font");
            reset();timeout=true;rejected(download,target,null,null);retained(directory,target);equal(0,inputCalls);
            reset();status=503;rejected(download,target,null,null);retained(directory,target);equal(0,inputCalls);
            reset();status=302;rejected(download,target,null,null);retained(directory,target);equal(0,inputCalls);
            reset();declared=FontCatalog.MAX_DOWNLOAD_BYTES+1;rejected(download,target,null,null);retained(directory,target);equal(0,inputCalls);
            reset();declared=ENTRY.bytes+1;rejected(download,target,null,null);retained(directory,target);equal(0,inputCalls);
            reset();body=new byte[12];rejected(download,target,null,null);retained(directory,target);
            reset();rejected(download,target,null,null);retained(directory,target); // Exact size, wrong SHA256.
            reset();declared=-1;body=Arrays.copyOf(body,body.length+1);rejected(download,target,null,null);retained(directory,target);
            reset();FontDownloadRepository.Cancellation before=new FontDownloadRepository.Cancellation();before.cancel();
            rejected(download,target,null,before);retained(directory,target);equal(0,inputCalls);
            reset();FontDownloadRepository.Cancellation during=new FontDownloadRepository.Cancellation();
            rejected(download,target,(received,total)->during.cancel(),during);retained(directory,target);equal(true,during.getAsBoolean());
            Field connection=FontDownloadRepository.Cancellation.class.getDeclaredField("connection");connection.setAccessible(true);
            equal(null,connection.get(during));
            if(args.length>0) {
                reset();body=Files.readAllBytes(Path.of(args[0],"Manrope[wght].ttf"));
                long[] last={0};invoke(download,target,(received,total)-> {
                    if(received<last[0]||total!=ENTRY.bytes)throw new AssertionError("Invalid byte progress");last[0]=received;
                },null);
                equal(ENTRY.bytes,last[0]);FontFileValidator.validate(target,ENTRY,null);checks++;
                equal("current-font",Files.readString(directory.resolve("custom.font")));
                try(var files=Files.list(directory)) { equal(2L,files.count()); }
            }
        } finally {
            try(var files=Files.list(directory)) { for(Path path:files.toList())Files.deleteIfExists(path); }
            Files.deleteIfExists(directory);
        }
        System.out.println("Font download transport checks passed: "+checks);
    }
}
