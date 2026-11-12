package dev.puitheme;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real URL/schema/redirect/hash admission with a deterministic transport; no fake network-success claims. */
public final class CustomPluginCatalogCheck {
    private static int checks;
    private interface Task {void run()throws Exception;}
    private static void equal(Object want,Object got,String why){checks++;if(!Objects.equals(want,got))throw new AssertionError(why+": "+want+" != "+got);}
    private static void reject(Task task,String why)throws Exception {checks++;try{task.run();}catch(Exception expected){return;}throw new AssertionError("Accepted "+why);}
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private static final CustomPluginCatalog.Resolver PUBLIC=host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8})};
    private static final String INDEX="https://plugins.c17.example/index.json";
    private static final class Fake implements CustomPluginCatalog.Transport {
        final Map<String,Reply> replies=new LinkedHashMap<>();int calls,closes;
        Fake data(String url,byte[] data){return data(url,data,data.length);}
        Fake data(String url,byte[] data,long length){replies.put(url,new Reply(200,null,data,length,null));return this;}
        Fake redirect(String url,String to){replies.put(url,new Reply(302,to,new byte[0],0,null));return this;}
        @Override public CustomPluginCatalog.Response open(URI uri)throws Exception {
            calls++;Reply value=replies.get(uri.toASCIIString());if(value==null)throw new IOException("No fixture for "+uri);
            return new CustomPluginCatalog.Response(value.status,value.location,value.length,new ByteArrayInputStream(value.data),value.encoding,()->closes++);
        }
    }
    private static final class Reply {final int status;final String location,encoding;final byte[] data;final long length;
        Reply(int status,String location,byte[] data,long length,String encoding){this.status=status;this.location=location;this.data=data;this.length=length;this.encoding=encoding;}}
    private static String hash(byte[] data)throws Exception {StringBuilder out=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(data))out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return out.toString();}
    private static JSONObject entry(byte[] data)throws Exception {return new JSONObject().put("id","dev.c17.online").put("name","在线插件").put("version","1.0").put("versionCode",1).put("apiVersion",1)
            .put("description","经过校验再导入").put("author","C17").put("capabilities",new JSONArray().put("settings")).put("file","packages/dev.c17.online-1.zip").put("size",data.length).put("sha256",hash(data));}
    private static JSONObject catalog(JSONObject... entries)throws Exception {JSONArray plugins=new JSONArray();for(JSONObject entry:entries)plugins.put(entry);return new JSONObject().put("schemaVersion",1).put("apiVersion",1).put("plugins",plugins);}
    private static CustomPluginCatalog.Source source()throws Exception{return CustomPluginCatalog.source(INDEX,new Fake(),PUBLIC);}
    private static CustomPluginCatalog.Catalog parse(JSONObject index)throws Exception {return CustomPluginCatalog.fetch(source(),new Fake().data(INDEX,bytes(index.toString())),PUBLIC);}

    private static void urls()throws Exception {
        String[] rejected={"http://github.com/owner/repo","https://u:p@github.com/owner/repo","https://github.com:8443/owner/repo","https://github.com/owner/repo?q=secret",
                "https://github.com/owner/repo#fragment","https://localhost/index.json","https://test.local/index.json","https://printer/index.json",
                "https://127.0.0.1/index.json","https://10.0.0.1/index.json","https://100.64.0.1/index.json","https://169.254.1.1/index.json","https://172.31.255.1/index.json",
                "https://192.168.1.1/index.json","https://192.0.2.1/index.json","https://198.18.0.1/index.json","https://198.51.100.1/index.json","https://203.0.113.1/index.json",
                "https://224.0.0.1/index.json","https://0.0.0.0/index.json","https://2130706433/index.json","https://127.1/index.json","https://0177.0.0.1/index.json",
                "https://[::1]/index.json","https://[fd00::1]/index.json","https://[2001:db8::1]/index.json","https://github.com/owner/../index.json",
                "https://github.com/owner/%2e%2e/index.json","https://github.com/owner/repo%2fother/index.json","https://github.com/owner/repo\\evil","https://github.com./owner/repo"};
        for(String value:rejected)reject(()->CustomPluginCatalog.safeUri(value),"unsafe URL "+value);
        equal("https://github.com/owner/repo",CustomPluginCatalog.safeUri("HTTPS://GitHub.Com:443/owner/repo").toASCIIString(),"canonical TLS origin");
        equal(true,CustomPluginCatalog.publicAddress(InetAddress.getByName("8.8.8.8")),"global IPv4 admitted");
        equal(true,CustomPluginCatalog.publicAddress(InetAddress.getByName("2606:4700:4700::1111")),"global IPv6 admitted");
        CustomPluginCatalog.Resolver mixed=host -> new InetAddress[]{InetAddress.getByName("8.8.8.8"),InetAddress.getByName("127.0.0.1")};
        reject(()->CustomPluginCatalog.source(INDEX,new Fake(),mixed),"DNS answer containing any private IP");
    }
    private static void sources()throws Exception {
        CustomPluginCatalog.Source fixed=CustomPluginCatalog.defaultSource();equal("github:sanwu5/c17-plugins:main:index.json",fixed.id,"offline default stable ID");
        equal(fixed.id,CustomPluginCatalog.restore(fixed.serialize()).id,"offline source restore");
        CustomPluginCatalog.Source raw=CustomPluginCatalog.source(fixed.indexUrl,new Fake(),PUBLIC);equal(fixed.id,raw.id,"raw URL and GitHub source share identity");
        Fake network=new Fake().data("https://api.github.com/repos/Test/plugins",bytes("{\"private\":false,\"default_branch\":\"release\"}"));
        CustomPluginCatalog.Source repo=CustomPluginCatalog.source("https://github.com/Test/plugins",network,PUBLIC);
        equal("release",repo.branch,"public default branch resolved");equal("https://raw.githubusercontent.com/Test/plugins/release/index.json",repo.indexUrl,"default branch raw index");equal(1,network.closes,"metadata connection closed");
        CustomPluginCatalog.Source tree=CustomPluginCatalog.source("https://github.com/Test/plugins/tree/main",new Fake(),PUBLIC);equal("main",tree.branch,"explicit tree branch");
        CustomPluginCatalog.Source blob=CustomPluginCatalog.source("https://github.com/Test/plugins/blob/v1/catalog/index.json",new Fake(),PUBLIC);equal("https://raw.githubusercontent.com/Test/plugins/v1/catalog/index.json",blob.indexUrl,"nested blob index path");
        Fake slashed=new Fake().data("https://api.github.com/repos/Test/plugins",bytes("{\"private\":false,\"default_branch\":\"feature/next\"}"));
        equal("https://raw.githubusercontent.com/Test/plugins/refs/heads/feature/next/index.json",CustomPluginCatalog.source("https://github.com/Test/plugins",slashed,PUBLIC).indexUrl,"default branch with slash");
        Fake privateRepo=new Fake().data("https://api.github.com/repos/Test/plugins",bytes("{\"private\":true,\"default_branch\":\"main\"}"));
        reject(()->CustomPluginCatalog.source("https://github.com/Test/plugins",privateRepo,PUBLIC),"private repository");
        reject(()->CustomPluginCatalog.source("https://github.com/Test/plugins/issues",new Fake(),PUBLIC),"unsupported GitHub path");
        reject(()->CustomPluginCatalog.source("https://plugins.c17.example/data.json",new Fake(),PUBLIC),"direct non-index file");
        JSONObject changed=fixed.serialize().put("indexUrl","https://127.0.0.1/index.json");reject(()->CustomPluginCatalog.restore(changed),"private stored URL");
        JSONObject wrong=fixed.serialize().put("id","github:attacker/plugins:main:index.json");reject(()->CustomPluginCatalog.restore(wrong),"forged source identity");
        equal(INDEX,CustomPluginCatalog.restore(source().serialize()).indexUrl,"third-party restore without HTTP");
    }
    private static void catalogs()throws Exception {
        byte[] artifact=bytes("bounded zip bytes");JSONObject item=entry(artifact);
        CustomPluginCatalog.Catalog parsed=parse(catalog(item).put("name","C17 扩展插件仓库").put("readmeUrl","README.md").put("changesUrl","CHANGELOG.md"));
        equal("C17 扩展插件仓库",parsed.name,"optional catalog title");equal(1,parsed.entries.size(),"entry parsed");
        equal("https://plugins.c17.example/packages/dev.c17.online-1.zip",parsed.entries.get(0).url,"safe relative package directory");
        equal("https://plugins.c17.example/README.md",parsed.readmeUrl,"relative repository documentation");equal(0,parse(catalog()).entries.size(),"empty official catalog valid");
        reject(()->parse(catalog(item,item)),"duplicate plugin ID");
        for(String file:new String[]{"../evil.zip","https://other.example/a.zip","/root.zip","packages/a.js","packages/a.zip?token=x","packages\\a.zip","packages/%2e%2e/a.zip"}) {
            JSONObject bad=entry(artifact).put("file",file);reject(()->parse(catalog(bad)),"unsafe relative package "+file);
        }
        for(Object size:new Object[]{0,1048577,1.5,"12"}) {JSONObject bad=entry(artifact).put("size",size);reject(()->parse(catalog(bad)),"invalid package size");}
        reject(()->parse(catalog(entry(artifact).put("sha256","A".repeat(64)))),"uppercase checksum");
        reject(()->parse(catalog(entry(artifact).put("apiVersion",3)).put("apiVersion",2)),"future plugin API");
        JSONObject runtime=entry(artifact).put("apiVersion",2).put("capabilities",new JSONArray().put("runtime")).put("size",1048577);
        equal(2,parse(catalog(runtime).put("apiVersion",2)).entries.get(0).apiVersion,"API2 runtime catalog admitted");
        equal(1048577,parse(catalog(runtime).put("apiVersion",2)).entries.get(0).size,"API2 package exceeds API1 limit");
        equal(1,parse(catalog(entry(artifact)).put("apiVersion",2)).entries.get(0).apiVersion,"API2 catalog retains API1 compatibility");
        reject(()->parse(catalog(runtime)),"catalog API lower than contained plugin API");
        reject(()->parse(catalog(entry(artifact).put("apiVersion",2)).put("apiVersion",2)),"API2 catalog entry without runtime capability");
        reject(()->parse(catalog().put("apiVersion",3)),"future catalog API");
        reject(()->parse(catalog(entry(artifact).put("capabilities",new JSONArray().put("root")))),"unknown capability");
        reject(()->parse(catalog().put("schemaVersion",2)),"future catalog schema");
        reject(()->parse(catalog().put("unknown",true)),"unknown catalog field");
        reject(()->parse(catalog().put("readmeUrl","https://192.168.1.1/readme")),"private README link");
        for(String raw:new String[]{"{\"schemaVersion\":1,\"schemaVersion\":1,\"apiVersion\":1,\"plugins\":[]}","{schemaVersion:1,apiVersion:1,plugins:[]}",
                "{\"schemaVersion\":1,\"apiVersion\":1,\"plugins\":[],}","/*comment*/{\"schemaVersion\":1,\"apiVersion\":1,\"plugins\":[]}",
                "{\"schemaVersion\":NaN,\"apiVersion\":1,\"plugins\":[]}","{\"schemaVersion\":01,\"apiVersion\":1,\"plugins\":[]}",
                "{\"schemaVersion\":1,\"apiVersion\":1,\"name\":\"\\ud800\",\"plugins\":[]}"}) {
            reject(()->CustomPluginCatalog.fetch(source(),new Fake().data(INDEX,bytes(raw)),PUBLIC),"non-strict network JSON");
        }
        reject(()->CustomPluginCatalog.fetch(source(),new Fake().data(INDEX,new byte[]{(byte)0xc3,(byte)0x28}),PUBLIC),"malformed UTF-8");
        reject(()->CustomPluginCatalog.fetch(source(),new Fake().data(INDEX,new byte[256*1024+1]),PUBLIC),"oversized catalog response");
    }
    private static void downloads()throws Exception {
        byte[] artifact=bytes("verified bytes only; never executes");CustomPluginCatalog.Entry entry=parse(catalog(entry(artifact))).entries.get(0);
        Fake good=new Fake().data(entry.url,artifact);equal(new String(artifact,StandardCharsets.UTF_8),new String(CustomPluginCatalog.download(entry,good,PUBLIC),StandardCharsets.UTF_8),"verified download bytes");equal(1,good.closes,"artifact connection closed");
        byte[] wrong=artifact.clone();wrong[0]^=1;reject(()->CustomPluginCatalog.download(entry,new Fake().data(entry.url,wrong),PUBLIC),"same-size SHA mismatch");
        reject(()->CustomPluginCatalog.download(entry,new Fake().data(entry.url,new byte[1],-1),PUBLIC),"truncated unknown-length body");
        reject(()->CustomPluginCatalog.download(entry,new Fake().data(entry.url,new byte[artifact.length+1],-1),PUBLIC),"oversized unknown-length body");
        reject(()->CustomPluginCatalog.download(entry,new Fake().data(entry.url,artifact,artifact.length+1),PUBLIC),"declared length mismatch");
        Fake redirected=new Fake().redirect(entry.url,"https://cdn.c17.example/one.zip").data("https://cdn.c17.example/one.zip",artifact);
        equal(artifact.length,CustomPluginCatalog.download(entry,redirected,PUBLIC).length,"public HTTPS artifact redirect");equal(2,redirected.closes,"redirect and body closed");
        reject(()->CustomPluginCatalog.download(entry,new Fake().redirect(entry.url,"http://cdn.c17.example/file.zip"),PUBLIC),"TLS downgrade redirect");
        reject(()->CustomPluginCatalog.download(entry,new Fake().redirect(entry.url,"https://10.0.0.1/file.zip"),PUBLIC),"private redirect");
        Fake loop=new Fake().redirect(entry.url,entry.url);reject(()->CustomPluginCatalog.download(entry,loop,PUBLIC),"redirect cycle");equal(1,loop.calls,"cycle stops before second connect");
        Fake hops=new Fake().redirect(entry.url,"https://cdn.c17.example/1").redirect("https://cdn.c17.example/1","https://cdn.c17.example/2").redirect("https://cdn.c17.example/2","https://cdn.c17.example/3").redirect("https://cdn.c17.example/3","https://cdn.c17.example/4");
        reject(()->CustomPluginCatalog.download(entry,hops,PUBLIC),"fourth redirect");equal(4,hops.closes,"all excessive redirect connections closed");
        Fake moved=new Fake().redirect(INDEX,"https://cdn.c17.example/catalog/index.json").data("https://cdn.c17.example/catalog/index.json",bytes(catalog(entry(artifact)).toString()));
        equal("https://cdn.c17.example/catalog/packages/dev.c17.online-1.zip",CustomPluginCatalog.fetch(source(),moved,PUBLIC).entries.get(0).url,"relative packages use final redirected index location");
        Thread.currentThread().interrupt();try{reject(()->CustomPluginCatalog.download(entry,new Fake(),PUBLIC),"interrupted request");}finally{Thread.interrupted();}
    }
    public static void main(String[] args)throws Exception {urls();sources();catalogs();downloads();System.out.println("CustomPluginCatalogCheck "+checks+" checks passed");}
}
