package dev.puitheme;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** Pure metadata/SFNT boundaries plus optional real official fonts; no network or drawing substitutes. */
public final class FontCatalogCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {
        checks++;if(!java.util.Objects.equals(expected,actual))throw new AssertionError("Font catalog check "+checks+": "+actual);
    }
    private static void check(boolean condition) { equal(true,condition); }
    private interface Checked { void run() throws Exception; }
    private static void rejected(Checked task) throws Exception {
        try { task.run();throw new AssertionError("Accepted invalid font data"); }
        catch(IOException expected) { checks++; }
    }
    private static byte[] font(float minimum,float normal,float maximum) {
        ByteBuffer bytes=ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(0x00010000).putShort((short)1).putShort((short)0).putInt(0);
        bytes.putInt(0x66766172).putInt(0).putInt(28).putInt(36);
        bytes.putInt(0x00010000).putShort((short)16).putShort((short)2)
                .putShort((short)1).putShort((short)20).putShort((short)0).putShort((short)0);
        bytes.putInt(0x77676874).putInt(Math.round(minimum*65536)).putInt(Math.round(normal*65536))
                .putInt(Math.round(maximum*65536)).putShort((short)0).putShort((short)256);
        return bytes.array();
    }
    public static void main(String[] args) throws Exception {
        equal(10,FontCatalog.entries().size());
        Set<String> ids=new HashSet<>(),revisions=new HashSet<>(),names=new HashSet<>();int chinese=0;
        for(FontCatalog.Entry entry:FontCatalog.entries()) {
            check(ids.add(entry.id)&&names.add(entry.displayName)&&revisions.add(entry.sha256));
            check(entry.id.matches("[a-z0-9]+")&&entry.sha256.matches("[0-9a-f]{64}"));
            check(entry.bytes>0&&entry.bytes<=FontCatalog.MAX_DOWNLOAD_BYTES);
            check(entry.minWeight>=1&&entry.maxWeight<=1000&&entry.minWeight<entry.maxWeight);
            equal(entry,FontCatalog.find(entry.id));equal(entry,FontCatalog.forRevision(entry.sha256));
            equal(entry.minWeight,entry.clampWeight(Integer.MIN_VALUE));
            equal(entry.maxWeight,entry.clampWeight(Integer.MAX_VALUE));
            equal("https",FontDownloadRepository.downloadUrl(entry).getProtocol());
            equal("raw.githubusercontent.com",FontDownloadRepository.downloadUrl(entry).getHost());
            check(entry.downloadUrl.contains(FontCatalog.DISTRIBUTION_COMMIT));
            check(entry.sourceUrl.startsWith("https://github.com/"));
            equal("SIL Open Font License 1.1",entry.licenseName);
            check(entry.licenseAsset.equals("fonts/catalog/licenses/"+entry.id+"-OFL.txt"));
            if(entry.coverage.contains("支持简体中文"))chinese++;
            else check(entry.coverage.contains("中文使用系统字体"));
        }
        equal(2,chinese);equal(null,FontCatalog.find("../../custom"));equal(null,FontCatalog.forRevision("missing"));
        try { FontCatalog.entries().clear();throw new AssertionError("Mutable font catalog"); }
        catch(UnsupportedOperationException expected) { checks++; }
        rejected(()->FontDownloadRepository.downloadUrl(null));
        FontDownloadRepository.Cancellation cancellation=new FontDownloadRepository.Cancellation();
        equal(false,cancellation.getAsBoolean());cancellation.cancel();equal(true,cancellation.getAsBoolean());
        try { FontFileValidator.checkCancelled(cancellation);throw new AssertionError("Cancellation ignored"); }
        catch(InterruptedIOException expected) { checks++; }

        Path file=Files.createTempFile("c17-variable-font-check-",".ttf");
        try {
            Files.write(file,font(200,400,1000));FontFileValidator.WeightAxis axis=FontFileValidator.weightAxis(file.toFile());
            equal(200f,axis.minimum);equal(400f,axis.defaultValue);equal(1000f,axis.maximum);
            rejected(()->FontFileValidator.validate(file.toFile(),FontCatalog.find("nunito"),null));
            rejected(()->FontFileValidator.sha256(file.toFile(),32,null));
            rejected(()->FontFileValidator.sha256(file.toFile(),128,()->true));
            byte[] bytes=font(100,400,900);ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(44,0x77647468);
            Files.write(file,bytes);equal(null,FontFileValidator.weightAxis(file.toFile()));
            Files.write(file,font(900,400,100));rejected(()->FontFileValidator.weightAxis(file.toFile()));
            Files.write(file,font(1,400,1001));rejected(()->FontFileValidator.weightAxis(file.toFile()));
            bytes=font(100,400,900);ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(20,Integer.MAX_VALUE);
            Files.write(file,bytes);rejected(()->FontFileValidator.weightAxis(file.toFile()));
            bytes=font(100,400,900);ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putShort(36,(short)65);
            Files.write(file,bytes);rejected(()->FontFileValidator.weightAxis(file.toFile()));
            Files.write(file,new byte[]{0,1,0});rejected(()->FontFileValidator.weightAxis(file.toFile()));
        } finally { Files.deleteIfExists(file); }

        Field revision=FontRepository.class.getDeclaredField("revision");revision.setAccessible(true);
        Object previous=revision.get(null);
        Method sourceWeight=FontRepository.class.getDeclaredMethod("sourceWeight",String.class,int.class);sourceWeight.setAccessible(true);
        try {
            revision.set(null,FontCatalog.find("nunito").sha256);
            equal(1000,sourceWeight.invoke(null,"custom",1000));equal(200,sourceWeight.invoke(null,"custom",1));
            equal(1,sourceWeight.invoke(null,"system",1));
            equal(900,sourceWeight.invoke(null,"pingfang",1000));equal(100,sourceWeight.invoke(null,"pingfang",1));
            revision.set(null,FontCatalog.find("oswald").sha256);equal(700,sourceWeight.invoke(null,"custom",1000));
            revision.set(null,"");equal(1000,sourceWeight.invoke(null,"custom",1000));
        } finally { revision.set(null,previous); }

        equal(1000,NotificationBigClockModel.calculate(1280,1,100,36,1000,300,0,0,0,1).weight);
        equal(1,NotificationBigClockModel.calculate(1280,1,100,36,1,300,0,0,0,1).weight);
        equal(1000,NotificationBigClockModel.measured(1280,1,32,200,72,20,8,18,0,0,0,1000,300,0,0,1).weight);
        equal(1,NotificationBigClockModel.measured(1280,1,32,200,72,20,8,18,0,0,0,1,300,0,0,1).weight);

        if(args.length>0)for(FontCatalog.Entry entry:FontCatalog.entries()) {
            String filename=java.net.URLDecoder.decode(FontDownloadRepository.downloadUrl(entry).getPath(),"UTF-8");
            filename=filename.substring(filename.lastIndexOf('/')+1);
            FontFileValidator.validate(Path.of(args[0],filename).toFile(),entry,null);checks++;
        }
        System.out.println("Font catalog checks passed: "+checks);
    }
}
