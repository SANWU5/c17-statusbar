package dev.puitheme;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Pure request validation: no Android processes, broadcasts, preferences or saved reports. */
public final class RuntimeHandshakeCheck {
    private static int checks;
    private static void expect(boolean expected, boolean actual) {
        checks++;
        if (expected != actual) throw new AssertionError("Runtime handshake check " + checks);
    }
    private static boolean accept(ModuleRuntimeStatus.Challenge request, String nonce, int claimedPid,
            int callerPid, long uptime, long now, String build, int api, String framework, String version) {
        return ModuleRuntimeStatus.accepts(request, nonce, claimedPid, callerPid, uptime, now, 10010L,
                build, api, framework, version);
    }
    private static String one(String source,String expression,int group,String field) {
        Matcher match=Pattern.compile(expression,Pattern.MULTILINE).matcher(source);
        if(!match.find())throw new AssertionError("Missing "+field);String value=match.group(group);
        if(match.find())throw new AssertionError("Duplicate "+field);return value;
    }
    private static void buildIdentity()throws Exception {
        String gradle=new String(Files.readAllBytes(new File("app/build.gradle").toPath()),StandardCharsets.UTF_8);
        int packagedCode=Integer.parseInt(one(gradle,"^\\s*versionCode\\s+(\\d+)\\s*$",1,"package versionCode"));
        String packagedVersion=one(gradle,"^\\s*versionName\\s+['\"]([^'\"]+)['\"]\\s*$",1,"package versionName");
        if(ModuleVariant.DABING) {
            packagedCode=Integer.parseInt(one(gradle,"^\\s*defaultConfig\\.versionCode\\s*=\\s*(\\d+)\\s*$",1,"customer versionCode"));
            packagedVersion=one(gradle,"^\\s*defaultConfig\\.versionName\\s*=\\s*['\"]([^'\"]+)['\"]\\s*$",1,"customer versionName");
        }
        String source=new String(Files.readAllBytes(new File("app/src/main/java/dev/puitheme/ModuleRuntimeStatus.java").toPath()),StandardCharsets.UTF_8);
        String tokenPrefix="public\\s+static\\s+final\\s+String\\s+BUILD_TOKEN\\s*=\\s*";
        String tokenExpression=ModuleVariant.DABING
                ?tokenPrefix+"ModuleVariant\\.DABING\\s*\\?\\s*\"([^\"]+)\""
                :tokenPrefix+"(?:ModuleVariant\\.DABING\\s*\\?\\s*\"[^\"]+\"\\s*:\\s*)?\"([^\"]+)\"";
        String sourceToken=one(source,tokenExpression,1,"source runtime token");
        // Reflect instead of reading an inlined constant, so stale production classes are detected too.
        String compiledToken=(String)ModuleRuntimeStatus.class.getField("BUILD_TOKEN").get(null);
        expect(true,sourceToken.equals(compiledToken));
        Matcher token=Pattern.compile((ModuleVariant.DABING?"dabing":"c17")+"-runtime-(\\d{8})-(.+)-code(\\d+)").matcher(compiledToken);
        expect(true,token.matches());
        expect(true,packagedCode==Integer.parseInt(token.group(3)));
        expect(true,packagedVersion.equals(token.group(2)));
        expect(true,packagedCode==CustomPluginSupport.versionCode());
        expect(true,packagedCode==CustomPluginSupport.describe("com.android.systemui").getInt("versionCode"));
        JSONObject current=new JSONObject().put("minimumHost",packagedCode).put("features",new JSONArray().put("ui.sort"));
        checks++;CustomPluginSupport.requireCompatible(current);
        boolean refused=false;try{CustomPluginSupport.requireCompatible(new JSONObject(current.toString()).put("minimumHost",packagedCode+1));}
        catch(IllegalArgumentException futureHost){refused=true;}
        expect(false,refused);
    }
    public static void main(String[] args)throws Exception {
        buildIdentity();
        String nonce = "0123456789abcdef0123456789abcdef";
        ModuleRuntimeStatus.Challenge request = new ModuleRuntimeStatus.Challenge(nonce, 1000L, 10000L);
        String build = ModuleRuntimeStatus.BUILD_TOKEN;
        expect(true, ModuleRuntimeStatus.allowedReporter(1000, 10200, 1000));
        expect(false, ModuleRuntimeStatus.allowedReporter(10200, 10200, 1000));
        expect(false, ModuleRuntimeStatus.allowedReporter(10300, 10200, 1000));
        expect(false, ModuleRuntimeStatus.allowedReporter(1000, 1000, 1000));
        expect(true, accept(request, nonce, 321, 321, 10005, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, "ffffffffffffffffffffffffffffffff", 321, 321, 10005, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 322, 10005, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 0, 0, 10005, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 9999, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10011, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 999, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 3500, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 9000, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 1200, "older-build", 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 1200, "c17-runtime-20261021-2.1.0_beta.012-code210", 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 1200, build, 100, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 1200, build, 101, "", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 1200, build, 101, "OtherFramework", "2.2.0"));
        expect(true, accept(request, nonce, 321, 321, 10005, 1200, build, 102, "lSpOsEd Modern", "2.2.0-it"));
        expect(false, accept(request, nonce, 321, 321, 10005, 1200, build, 101, "LSPosed", null));
        expect(false, accept(null, nonce, 321, 321, 10005, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, null, 321, 321, 10005, 1200, build, 101, "LSPosed", "2.2.0"));
        ModuleRuntimeStatus.ProbeState replied = new ModuleRuntimeStatus.ProbeState();
        expect(true, replied.open());
        expect(true, replied.beginQuery());
        expect(false, replied.beginQuery()); // Duplicate notifications cannot query twice.
        expect(true, replied.finish());
        expect(false, replied.open());
        expect(false, replied.finish()); // Success, timeout and cancel clean up only once.
        expect(false, replied.beginQuery());
        ModuleRuntimeStatus.ProbeState cancelled = new ModuleRuntimeStatus.ProbeState();
        expect(true, cancelled.finish());
        expect(false, cancelled.beginQuery()); // A late response cannot revive a cancelled probe.
        expect(false, cancelled.open());
        System.out.println("Runtime handshake checks passed: " + checks);
    }
}
