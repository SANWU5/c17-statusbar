// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Fixed-project policy shared by metadata, bounded download and package verification. */
final class UpdateSecurity {
    static final long MIN_APK_BYTES=32L*1024L,MAX_APK_BYTES=80L*1024L*1024L;
    private UpdateSecurity() { }
    static String digest(String field) {
        return field!=null&&field.matches("(?i)sha256:[0-9a-f]{64}")?field.substring(7).toLowerCase(Locale.ROOT):"";
    }
    static boolean validArtifact(String version,String release,String asset,long size,String hash) {
        if(size<MIN_APK_BYTES||size>MAX_APK_BYTES||hash==null||!hash.matches("[0-9a-f]{64}")
                ||GitHubUpdates.compareVersion(version,"0.0.0")==null||version.contains("-")
                ||!GitHubUpdates.isReleaseUrl(release)||!GitHubUpdates.isApkUrl(asset))return false;
        try {
            String releasePrefix="/SANWU5/c17-statusbar/releases/tag/",assetPrefix="/SANWU5/c17-statusbar/releases/download/";
            String released=new URI(release).getPath().substring(releasePrefix.length());
            String downloaded=new URI(asset).getPath().substring(assetPrefix.length());
            int slash=downloaded.indexOf('/');
            return released.equals(version)&&downloaded.substring(0,slash).equals(version)
                    &&downloaded.indexOf('/',slash+1)<0;
        }catch(Exception malformed){return false;}
    }
    /** Only GitHub's binary release hosts, never a caller-selected update origin. */
    static boolean allowedRedirect(String address) {
        if(address==null||address.length()>8192)return false;
        try {
            URI uri=new URI(address);String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getPort()!=-1
                    ||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||host==null)return false;
            if("github.com".equalsIgnoreCase(host))return GitHubUpdates.isApkUrl(address);
            if(!"release-assets.githubusercontent.com".equalsIgnoreCase(host)
                    &&!"objects.githubusercontent.com".equalsIgnoreCase(host))return false;
            String path=uri.getPath();
            if(path==null||!(path.startsWith("/github-production-release-asset/")
                    ||path.startsWith("/github-production-release-asset-")))return false;
            for(String segment:path.split("/"))if("..".equals(segment)||".".equals(segment))return false;
            return true;
        }catch(Exception malformed){return false;}
    }
    static void copyVerified(InputStream input,OutputStream output,long expected,String expectedHash,
                             BooleanSupplier cancelled) throws IOException {
        if(expected<MIN_APK_BYTES||expected>MAX_APK_BYTES||expectedHash==null||!expectedHash.matches("[0-9a-f]{64}"))
            throw new IOException("Missing official asset verification metadata");
        MessageDigest digest;
        try {digest=MessageDigest.getInstance("SHA-256");}catch(Exception unavailable){throw new IOException("Digest unavailable",unavailable);}
        long total=0;byte[] buffer=new byte[32768];int count;
        while((count=input.read(buffer))!=-1) {
            if(cancelled.getAsBoolean())throw new IOException("Update cancelled");
            total+=count;if(total>expected||total>MAX_APK_BYTES)throw new IOException("Unexpected APK size");
            output.write(buffer,0,count);digest.update(buffer,0,count);
        }
        if(cancelled.getAsBoolean()||total!=expected||!hex(digest.digest()).equals(expectedHash))
            throw new IOException("APK size or digest verification failed");
    }
    static boolean packageMatches(String name,String expectedVersion,String archiveVersion,
            long installedCode,long archiveCode,byte[][] installedSigners,byte[][] archiveSigners) {
        Integer same=GitHubUpdates.compareVersion(expectedVersion,archiveVersion);
        return ModuleDiagnostics.PACKAGE_NAME.equals(name)&&same!=null&&same==0
                &&archiveCode>installedCode&&sameSigners(installedSigners,archiveSigners);
    }
    static boolean sameSigners(byte[][] installed,byte[][] archive) {
        if(installed==null||archive==null||installed.length==0||installed.length!=archive.length)return false;
        Set<String> expected=new HashSet<>(),actual=new HashSet<>();
        for(byte[] signer:installed){if(signer==null||signer.length==0)return false;expected.add(hex(signer));}
        for(byte[] signer:archive){if(signer==null||signer.length==0)return false;actual.add(hex(signer));}
        return expected.size()==installed.length&&actual.size()==archive.length&&expected.equals(actual);
    }
    static String hex(byte[] bytes) {
        char[] digits="0123456789abcdef".toCharArray(),text=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++){text[i*2]=digits[(bytes[i]>>>4)&15];text[i*2+1]=digits[bytes[i]&15];}
        return new String(text);
    }
}
