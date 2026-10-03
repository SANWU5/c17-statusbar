package dev.puitheme;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Objects;

/** Fixed-project origin, bytes and same-current-signer checks without network or root execution. */
public final class AppUpdateInstallerCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Update "+checks+": "+expected+" != "+actual);}
    private static void rejected(RunnableIO operation)throws Exception{try{operation.run();throw new AssertionError("Expected rejection");}catch(IOException | IllegalArgumentException expected){checks++;}}
    interface RunnableIO{void run()throws Exception;}
    private static String metadata(long size,String digest,String tag,String path) {
        return "{\"draft\":false,\"prerelease\":false,\"tag_name\":\""+tag+"\",\"html_url\":\"https://github.com/SANWU5/c17-statusbar/releases/tag/"+tag+"\",\"assets\":[{\"name\":\"c17-statusbar.apk\",\"size\":"+size+",\"digest\":\""+digest+"\",\"browser_download_url\":\"https://github.com/SANWU5/c17-statusbar/releases/download/"+path+"\"}]}";
    }
    public static void main(String[] args)throws Exception {
        byte[] apk=new byte[(int)UpdateSecurity.MIN_APK_BYTES];for(int i=0;i<apk.length;i++)apk[i]=(byte)i;
        String hash=UpdateSecurity.hex(MessageDigest.getInstance("SHA-256").digest(apk));
        GitHubUpdates.Result release=GitHubUpdates.parseRelease("1.6.1",metadata(apk.length,"sha256:"+hash,"v1.6.2","v1.6.2/c17.apk"));
        equal(true,release.newer);equal(true,release.canInstall());equal((long)apk.length,release.apkSize);equal(hash,release.apkSha256);
        equal(false,GitHubUpdates.parseRelease("1.6.1",metadata(apk.length,"","v1.6.2","v1.6.2/c17.apk")).canInstall());
        equal(false,GitHubUpdates.parseRelease("1.6.1",metadata(1,"sha256:"+hash,"v1.6.2","v1.6.2/c17.apk")).canInstall());
        equal(false,GitHubUpdates.parseRelease("1.6.1",metadata(UpdateSecurity.MAX_APK_BYTES+1,"sha256:"+hash,"v1.6.2","v1.6.2/c17.apk")).canInstall());
        equal(false,GitHubUpdates.parseRelease("1.6.1",metadata(apk.length,"sha256:"+hash,"v1.6.2","v1.6.3/c17.apk")).canInstall());
        equal(false,GitHubUpdates.parseRelease("1.6.1",metadata(apk.length,"sha256:"+hash,"v1.6.2-beta1","v1.6.2-beta1/c17.apk")).canInstall());
        equal(false,GitHubUpdates.parseRelease("1.6.2",metadata(apk.length,"sha256:"+hash,"v1.6.2","v1.6.2/c17.apk")).canInstall());
        equal(false,GitHubUpdates.parseRelease("1.6.1",metadata(apk.length,"sha256:"+hash,"v1.6.2","v1.6.2/sub/c17.apk")).canInstall());
        equal(hash,UpdateSecurity.digest("SHA256:"+hash.toUpperCase(java.util.Locale.ROOT)));
        for(String url:new String[]{"https://release-assets.githubusercontent.com/github-production-release-asset/123/abc?jwt=temporary",
                "https://objects.githubusercontent.com/github-production-release-asset-123/abc?x=1",
                release.apkUrl})equal(true,UpdateSecurity.allowedRedirect(url));
        for(String url:new String[]{"http://release-assets.githubusercontent.com/github-production-release-asset/123/abc",
                "https://evil.example/github-production-release-asset/123/abc",
                "https://release-assets.githubusercontent.com.evil.example/github-production-release-asset/1/2",
                "https://release-assets.githubusercontent.com/other/1/2",
                "https://user@release-assets.githubusercontent.com/github-production-release-asset/1/2",
                "https://release-assets.githubusercontent.com:443/github-production-release-asset/1/2",
                "https://release-assets.githubusercontent.com/github-production-release-asset/../2",
                "https://release-assets.githubusercontent.com/github-production-release-asset/1/2#fragment",
                "https://github.com/other/repo/releases/download/v1.6.2/c17.apk"})equal(false,UpdateSecurity.allowedRedirect(url));
        ByteArrayOutputStream output=new ByteArrayOutputStream();UpdateSecurity.copyVerified(new ByteArrayInputStream(apk),output,apk.length,hash,()->false);
        equal(apk.length,output.size());equal(true,java.util.Arrays.equals(apk,output.toByteArray()));
        rejected(()->UpdateSecurity.copyVerified(new ByteArrayInputStream(apk),new ByteArrayOutputStream(),apk.length+1,hash,()->false));
        rejected(()->UpdateSecurity.copyVerified(new ByteArrayInputStream(apk),new ByteArrayOutputStream(),apk.length-1,hash,()->false));
        rejected(()->UpdateSecurity.copyVerified(new ByteArrayInputStream(apk),new ByteArrayOutputStream(),apk.length,"0".repeat(64),()->false));
        rejected(()->UpdateSecurity.copyVerified(new ByteArrayInputStream(apk),new ByteArrayOutputStream(),apk.length,hash,()->true));
        byte[][] signer={{1,2,3}},other={{4,5,6}};
        equal(true,UpdateSecurity.packageMatches(ModuleDiagnostics.PACKAGE_NAME,"v1.6.2","1.6.2",57,58,signer,signer));
        equal(false,UpdateSecurity.packageMatches("other.app","v1.6.2","1.6.2",57,58,signer,signer));
        equal(false,UpdateSecurity.packageMatches(ModuleDiagnostics.PACKAGE_NAME,"v1.6.2","1.6.3",57,58,signer,signer));
        equal(false,UpdateSecurity.packageMatches(ModuleDiagnostics.PACKAGE_NAME,"v1.6.2","1.6.2",58,58,signer,signer));
        equal(false,UpdateSecurity.packageMatches(ModuleDiagnostics.PACKAGE_NAME,"v1.6.2","1.6.2",59,58,signer,signer));
        equal(false,UpdateSecurity.packageMatches(ModuleDiagnostics.PACKAGE_NAME,"v1.6.2","1.6.2",57,58,signer,other));
        equal(false,UpdateSecurity.sameSigners(signer,new byte[][]{{1,2,3},{4,5,6}}));
        equal(true,UpdateSecurity.sameSigners(new byte[][]{{1},{2}},new byte[][]{{2},{1}}));
        equal(false,UpdateSecurity.sameSigners(new byte[][]{{1},{1}},new byte[][]{{1},{1}}));
        String source="/data/user/0/"+ModuleDiagnostics.PACKAGE_NAME+"/cache/official-updates/c17-update-"+"a".repeat(32)+".apk";
        String command=RootAccess.installCommand(source,hash,0);
        equal(true,command.contains("pm install -r --user 0"));equal(false,command.contains("install -r -d"));
        equal(true,command.contains("EXIT HUP INT TERM"));equal(true,command.contains("sha256sum"));equal(true,command.contains("/data/local/tmp/c17-update-"));
        rejected(()->RootAccess.installCommand(source,hash,-1));rejected(()->RootAccess.installCommand(source,"bad",0));
        rejected(()->RootAccess.installCommand("/data/local/tmp/arbitrary.apk",hash,0));
        File folder=Files.createTempDirectory("c17-update-check").toFile(),temp=new File(folder,"c17-update-"+"a".repeat(32)+".apk"),keep=new File(folder,"other.apk");
        equal(true,temp.createNewFile());equal(true,keep.createNewFile());AppUpdateInstaller.clearOldDownloads(folder);
        equal(false,temp.exists());equal(true,keep.exists());keep.delete();folder.delete();
        System.out.println("AppUpdateInstallerCheck passed "+checks+" checks");
    }
}
