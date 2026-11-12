// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Worker-only public catalog client. Downloads are verified bytes; installation remains a preview transaction. */
public final class CustomPluginCatalog {
    private static final int MAX_INDEX=256*1024,MAX_PACKAGE=1024*1024,TIMEOUT_MS=8000;
    private static final long REQUEST_NANOS=30_000_000_000L;
    private static final Resolver DNS=InetAddress::getAllByName;
    private static final Transport HTTPS=uri -> {
        HttpsURLConnection connection=(HttpsURLConnection)uri.toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept-Encoding","identity");
        connection.setRequestProperty("Accept","application/json, application/zip, application/octet-stream");
        connection.setRequestProperty("User-Agent","C17-ExtensionCatalog/1");
        try {
            int status=connection.getResponseCode();
            InputStream body=status==200?connection.getInputStream():null;
            return new Response(status,connection.getHeaderField("Location"),connection.getContentLengthLong(),body,
                    connection.getHeaderField("Content-Encoding"),connection::disconnect);
        } catch(Exception failure){connection.disconnect();throw failure;}
    };
    private CustomPluginCatalog() { }

    public static final class Source {
        public final String id,name,input,owner,repo,branch,indexUrl,homeUrl;
        private Source(String input,String owner,String repo,String branch,String indexUrl,String homeUrl)throws Exception {
            this.input=input;this.owner=owner;this.repo=repo;this.branch=branch;
            this.indexUrl=safeUri(indexUrl).toASCIIString();this.homeUrl=safeUri(homeUrl).toASCIIString();
            name=owner.isEmpty()?safeUri(indexUrl).getHost():owner+"/"+repo;
            String path=safeUri(indexUrl).getPath();
            id=owner.isEmpty()?"https:"+this.indexUrl:"github:"+owner.toLowerCase(Locale.ROOT)+"/"+repo.toLowerCase(Locale.ROOT)+":"+branch+":"+pathAfterRef(path,owner,repo,branch);
        }
        /** This snapshot contains public source URLs only, with no authentication or downloaded code. */
        public JSONObject serialize()throws Exception {
            return new JSONObject().put("id",id).put("name",name).put("input",input).put("owner",owner).put("repo",repo)
                    .put("branch",branch).put("indexUrl",indexUrl).put("homeUrl",homeUrl);
        }
    }
    public static final class Catalog {
        public final Source source;
        public final String name,description,updatedAt,readmeUrl,changesUrl;
        public final List<Entry> entries;
        private Catalog(Source source,URI base,JSONObject data,List<Entry> entries)throws Exception {
            this.source=source;name=optional(data,"name",120,source.name);description=optional(data,"description",2048,"");
            updatedAt=optional(data,"updatedAt",64,"");
            readmeUrl=documentUrl(base,optional(data,"readmeUrl",2048,""));changesUrl=documentUrl(base,optional(data,"changesUrl",2048,""));
            this.entries=Collections.unmodifiableList(entries);
        }
    }
    public static final class Entry {
        public final String id,name,version,description,author,url,sha256;
        public final int versionCode,apiVersion,size;
        public final JSONArray capabilities;
        private Entry(URI index,JSONObject data)throws Exception {
            fields(data,"id","name","version","versionCode","apiVersion","description","author","capabilities","file","sha256","size");
            CustomPluginSchema.metadata(data);
            id=data.getString("id");name=data.getString("name");version=data.getString("version");
            versionCode=integer(data,"versionCode",1,Integer.MAX_VALUE);apiVersion=integer(data,"apiVersion",1,2);
            description=optional(data,"description",1024,"");author=optional(data,"author",80,"");
            size=integer(data,"size",1,apiVersion==1?MAX_PACKAGE:Integer.MAX_VALUE);sha256=required(data,"sha256",64);
            if(!sha256.matches("[a-f0-9]{64}"))throw invalid("插件 SHA-256 必须为 64 位小写十六进制");
            String file=required(data,"file",240);relativePath(file);
            if(!file.endsWith(".zip"))throw invalid("在线插件文件必须为 ZIP 包");
            URI resolved=index.resolve(file);
            if(!index.getHost().equalsIgnoreCase(resolved.getHost())||!resolved.getPath().startsWith(directory(index)))throw invalid("插件文件必须位于目录所在路径内");
            url=safeUri(resolved.toASCIIString()).toASCIIString();
            Object raw=data.opt("capabilities");if(raw!=null&&!(raw instanceof JSONArray))throw invalid("插件能力必须为数组");
            capabilities=raw==null?new JSONArray():new JSONArray(raw.toString());
            if(capabilities.length()>(apiVersion==1?2:3))throw invalid("插件能力声明过多");Set<String> unique=new HashSet<>();
            for(int i=0;i<capabilities.length();i++){Object value=capabilities.opt(i);if(!(value instanceof String)||!(value.equals("settings")||value.equals("systemui")||apiVersion==2&&value.equals("runtime"))||!unique.add((String)value))throw invalid("插件能力声明无效或重复");}
            if(apiVersion==2&&!unique.contains("runtime"))throw invalid("API 2 插件必须声明 runtime 能力");
        }
    }

    /** Fixed official source, fully offline; an empty official catalog is valid. */
    public static Source defaultSource() {
        try{return github("https://github.com/SANWU5/c17-plugins","SANWU5","c17-plugins","main","index.json");}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    /** Restores a source snapshot without DNS, HTTP or an automatic refresh. */
    public static Source restore(JSONObject saved)throws Exception {
        fields(saved,"id","name","input","owner","repo","branch","indexUrl","homeUrl");
        String input=required(saved,"input",2048),owner=optional(saved,"owner",100,""),repo=optional(saved,"repo",100,""),branch=optional(saved,"branch",200,"");
        URI index=safeUri(required(saved,"indexUrl",2048)),home=safeUri(required(saved,"homeUrl",2048));safeUri(input);
        if(!index.getPath().endsWith("/index.json"))throw invalid("仓库目录地址必须指向 index.json");
        Source value;
        if(owner.isEmpty()) {
            if(!repo.isEmpty()||!branch.isEmpty())throw invalid("保存的仓库信息不完整");
            if(!home.toASCIIString().equals(index.toASCIIString()))throw invalid("保存的目录主页无效");
            value=new Source(input,"","","",index.toASCIIString(),home.toASCIIString());
        } else {
            repositoryNames(owner,repo);validRef(branch);
            String prefix="/"+owner+"/"+repo+"/"+rawRef(branch)+"/";
            if(!index.getHost().equalsIgnoreCase("raw.githubusercontent.com")||!index.getPath().startsWith(prefix))throw invalid("保存的 GitHub 目录不匹配");
            String relative=index.getPath().substring(prefix.length());relativePath(relative);
            value=github(input,owner,repo,branch,relative);
            if(!value.indexUrl.equals(index.toASCIIString())||!value.homeUrl.equals(home.toASCIIString()))throw invalid("保存的仓库地址不一致");
        }
        if(!required(saved,"id",2048).equals(value.id)||!required(saved,"name",240).equals(value.name))throw invalid("保存的仓库身份不一致");
        return value;
    }
    public static Source source(String input)throws Exception {return source(input,HTTPS,DNS);}
    public static Catalog fetch(Source source)throws Exception {return fetch(source,HTTPS,DNS);}
    public static byte[] download(Entry entry)throws Exception {return download(entry,HTTPS,DNS);}

    static Source source(String input,Transport transport,Resolver resolver)throws Exception {
        if(input==null)throw invalid("请输入 GitHub 仓库或 HTTPS index.json 地址");String typed=input.trim();URI uri=safeUri(typed);
        if(!uri.getHost().equalsIgnoreCase("github.com")) {
            if(!uri.getPath().endsWith("/index.json"))throw invalid("第三方目录地址必须指向 HTTPS index.json");
            verifyHost(uri,resolver);
            if(uri.getHost().equalsIgnoreCase("raw.githubusercontent.com")) {
                String[] raw=uri.getPath().substring(1).split("/");
                if(raw.length>=4&&!raw[2].equals("refs")) {
                    StringBuilder relative=new StringBuilder();for(int n=3;n<raw.length;n++){if(relative.length()>0)relative.append('/');relative.append(raw[n]);}
                    return github(uri.toASCIIString(),raw[0],raw[1],raw[2],relative.toString());
                }
            }
            return new Source(uri.toASCIIString(),"","","",uri.toASCIIString(),uri.toASCIIString());
        }
        String path=uri.getPath();while(path.endsWith("/"))path=path.substring(0,path.length()-1);
        String[] parts=path.substring(1).split("/",-1);if(parts.length<2)throw invalid("请输入完整的 GitHub 仓库地址");
        String owner=parts[0],repo=parts[1];if(repo.endsWith(".git"))repo=repo.substring(0,repo.length()-4);repositoryNames(owner,repo);
        String branch,index="index.json";
        if(parts.length==2) {
            URI metadata=safeUri("https://api.github.com/repos/"+owner+"/"+repo);
            JSONObject response=json(read(metadata,64*1024,-1,transport,resolver));
            if(!Boolean.FALSE.equals(response.opt("private")))throw invalid("仅支持可公开读取的 GitHub 插件仓库");
            branch=required(response,"default_branch",200);validRef(branch);
        } else if(parts.length==4&&parts[2].equals("tree")) {branch=parts[3];validRef(branch);}
        else if(parts.length>=5&&parts[2].equals("blob")) {
            branch=parts[3];validRef(branch);StringBuilder rest=new StringBuilder();for(int i=4;i<parts.length;i++){if(rest.length()>0)rest.append('/');rest.append(parts[i]);}
            index=rest.toString();relativePath(index);if(!index.endsWith("index.json"))throw invalid("GitHub 文件地址必须指向 index.json");
        } else throw invalid("支持 GitHub 仓库、tree/分支或 blob/分支/index.json 地址");
        return github(typed,owner,repo,branch,index);
    }
    private static Source github(String input,String owner,String repo,String branch,String index)throws Exception {
        repositoryNames(owner,repo);validRef(branch);relativePath(index);
        return new Source(input,owner,repo,branch,"https://raw.githubusercontent.com/"+owner+"/"+repo+"/"+rawRef(branch)+"/"+index,"https://github.com/"+owner+"/"+repo);
    }
    private static String rawRef(String branch){return branch.indexOf('/')>=0?"refs/heads/"+branch:branch;}
    private static String pathAfterRef(String path,String owner,String repo,String branch){String prefix="/"+owner+"/"+repo+"/"+rawRef(branch)+"/";return path.startsWith(prefix)?path.substring(prefix.length()):path;}
    private static void repositoryNames(String owner,String repo)throws IOException {
        if(!owner.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})")||!repo.matches("[A-Za-z0-9_.-]{1,100}")||repo.equals(".")||repo.equals(".."))throw invalid("GitHub 用户名或仓库名无效");
    }
    private static void validRef(String branch)throws IOException {
        if(branch.isEmpty()||branch.length()>200||!branch.matches("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*"))throw invalid("GitHub 分支名不受支持");
        for(String part:branch.split("/"))if(part.equals(".")||part.equals("..")||part.endsWith(".lock"))throw invalid("GitHub 分支名无效");
    }

    static Catalog fetch(Source source,Transport transport,Resolver resolver)throws Exception {
        if(source==null)throw invalid("请先选择插件仓库");
        Payload payload=readData(safeUri(source.indexUrl),MAX_INDEX,-1,transport,resolver);
        JSONObject data=json(payload.bytes);
        fields(data,"schemaVersion","apiVersion","name","description","updatedAt","readmeUrl","changesUrl","plugins");
        integer(data,"schemaVersion",1,1);int catalogApi=integer(data,"apiVersion",1,2);
        Object raw=data.opt("plugins");if(!(raw instanceof JSONArray)||((JSONArray)raw).length()>128)throw invalid("插件目录必须包含最多 128 项 plugins 数组");
        JSONArray plugins=(JSONArray)raw;List<Entry> result=new ArrayList<>();Set<String> ids=new HashSet<>(),files=new HashSet<>();
        for(int i=0;i<plugins.length();i++) {
            JSONObject item=plugins.optJSONObject(i);if(item==null)throw invalid("插件目录项目必须为对象");Entry entry=new Entry(payload.uri,item);
            if(entry.apiVersion>catalogApi)throw invalid("目录 API 版本必须覆盖其中的插件版本");
            if(!ids.add(entry.id)||!files.add(entry.url))throw invalid("插件目录包含重复 ID 或文件地址");result.add(entry);
        }
        Catalog catalog=new Catalog(source,payload.uri,data,result);
        if(!catalog.readmeUrl.isEmpty())verifyHost(safeUri(catalog.readmeUrl),resolver);
        if(!catalog.changesUrl.isEmpty())verifyHost(safeUri(catalog.changesUrl),resolver);
        return catalog;
    }
    static byte[] download(Entry entry,Transport transport,Resolver resolver)throws Exception {
        if(entry==null)throw invalid("请选择插件");
        byte[] data=read(safeUri(entry.url),entry.apiVersion==1?MAX_PACKAGE:entry.size,entry.size,transport,resolver);
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(data);StringBuilder hex=new StringBuilder();
        for(byte value:digest)hex.append(String.format(Locale.ROOT,"%02x",value&255));
        if(!hex.toString().equals(entry.sha256))throw invalid("插件校验失败，SHA-256 与仓库目录不一致，请刷新目录后重试");
        return data;
    }
    private static String documentUrl(URI base,String value)throws Exception {
        if(value.isEmpty())return "";
        URI parsed=new URI(value);
        if(!parsed.isAbsolute()){relativePath(value);parsed=base.resolve(value);}
        return safeUri(parsed.toASCIIString()).toASCIIString();
    }
    private static String directory(URI uri){String path=uri.getPath();return path.substring(0,path.lastIndexOf('/')+1);}
    private static void relativePath(String value)throws IOException {
        if(value.length()>240||value.startsWith("/")||!value.matches("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*"))throw invalid("仓库文件必须使用安全的相对路径");
        for(String part:value.split("/"))if(part.equals(".")||part.equals("..")||part.endsWith(".")||part.endsWith(" "))throw invalid("仓库文件路径不能越级或包含特殊目录");
    }

    /** All URLs, including redirects, pass the same syntax and public-network checks. */
    static URI safeUri(String value)throws Exception {
        if(value==null||value.length()>2048||value.indexOf('\\')>=0||!value.equals(value.trim()))throw invalid("仓库地址无效");
        URI uri;
        try{uri=new URI(value);}catch(java.net.URISyntaxException malformed){throw new IOException("仓库地址格式无效",malformed);}
        String host=uri.getHost();
        if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getRawUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443)||uri.getRawQuery()!=null||uri.getRawFragment()!=null)
            throw invalid("仓库地址必须为无账号、参数和片段的 HTTPS 地址，仅支持 443 端口");
        host=host.toLowerCase(Locale.ROOT);if(host.startsWith("[")&&host.endsWith("]"))host=host.substring(1,host.length()-1);
        if(host.endsWith(".")||host.indexOf('%')>=0||host.equals("localhost")||host.indexOf('.')<0&&host.indexOf(':')<0||host.matches(".*\\.(localhost|local|lan|internal|home|onion|test|invalid)$"))throw invalid("不支持本地或内部网络仓库");
        if(host.matches("[0-9.]+")) {
            String[] parts=host.split("\\.",-1);if(parts.length!=4)throw invalid("IP 地址格式无效");
            for(String part:parts)if(!part.matches("0|[1-9][0-9]{0,2}")||Integer.parseInt(part)>255)throw invalid("IP 地址格式无效");
            if(!publicAddress(InetAddress.getByName(host)))throw invalid("不支持私有或保留 IP 地址");
        } else if(host.indexOf(':')>=0&&!publicAddress(InetAddress.getByName(host)))throw invalid("不支持私有或保留 IP 地址");
        String path=uri.getRawPath();if(path==null||path.isEmpty())path="/";
        if(path.indexOf('%')>=0||path.indexOf(';')>=0||path.indexOf('@')>=0||!path.matches("/[A-Za-z0-9_./~-]*")||path.contains("//"))throw invalid("仓库地址路径含有不支持的编码或字符");
        for(String part:path.split("/",-1))if(part.equals(".")||part.equals(".."))throw invalid("仓库地址不能包含越级路径");
        return new URI("https",null,host.indexOf(':')>=0?"["+host+"]":host,-1,path,null,null);
    }
    private static void verifyHost(URI uri,Resolver resolver)throws Exception {
        InetAddress[] addresses;
        try{addresses=resolver.resolve(uri.getHost());}catch(Exception unavailable){throw new IOException("无法解析插件仓库地址，请检查网络",unavailable);}
        if(addresses==null||addresses.length==0)throw invalid("无法解析插件仓库地址");
        for(InetAddress address:addresses)if(!publicAddress(address))throw invalid("仓库地址解析到私有或保留网络，已拒绝连接");
    }
    static boolean publicAddress(InetAddress address) {
        if(address==null||address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())return false;
        byte[] ip=address.getAddress();int a=ip[0]&255,b=ip[1]&255;
        if(ip.length==4)return a!=0&&a!=10&&a!=127&&a<224&&!(a==100&&b>=64&&b<=127)&&!(a==169&&b==254)&&!(a==172&&b>=16&&b<=31)
                &&!(a==192&&(b==168||b==0&&(ip[2]&255)<=2||b==88&&(ip[2]&255)==99))&&!(a==198&&(b==18||b==19||b==51&&(ip[2]&255)==100))&&!(a==203&&b==0&&(ip[2]&255)==113);
        // Global unicast only; exclude documentation, Teredo and 6to4 transition ranges.
        return ip.length==16&&(a&0xe0)==0x20&&!(a==0x20&&b==0x01&&(ip[2]&255)==0x0d&&(ip[3]&255)==0xb8)
                &&!(a==0x20&&b==0x01&&ip[2]==0&&ip[3]==0)&&!(a==0x20&&b==0x02);
    }

    interface Resolver {InetAddress[] resolve(String host)throws Exception;}
    interface Transport {Response open(URI uri)throws Exception;}
    static final class Response implements Closeable {
        final int status;final String location,encoding;final long length;final InputStream body;final Runnable closed;
        Response(int status,String location,long length,InputStream body,String encoding,Runnable closed){this.status=status;this.location=location;this.length=length;this.body=body;this.encoding=encoding;this.closed=closed;}
        @Override public void close()throws IOException{try{if(body!=null)body.close();}finally{if(closed!=null)closed.run();}}
    }
    private static final class Payload {final URI uri;final byte[] bytes;Payload(URI uri,byte[] bytes){this.uri=uri;this.bytes=bytes;}}
    private static byte[] read(URI initial,int max,int exact,Transport transport,Resolver resolver)throws Exception {return readData(initial,max,exact,transport,resolver).bytes;}
    private static Payload readData(URI initial,int max,int exact,Transport transport,Resolver resolver)throws Exception {
        URI current=initial;long deadline=System.nanoTime()+REQUEST_NANOS;Set<String> seen=new HashSet<>();
        for(int redirects=0;;redirects++) {
            current=safeUri(current.toASCIIString());verifyHost(current,resolver);
            if(!seen.add(current.toASCIIString()))throw invalid("插件仓库发生循环重定向");
            if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw invalid("插件仓库请求超时或已取消");
            Response opened;
            try{opened=transport.open(current);}catch(java.net.SocketTimeoutException timeout){throw new IOException("连接插件仓库超时，请稍后重试",timeout);}
            catch(IOException unavailable){throw new IOException("无法安全连接插件仓库，请检查网络后重试",unavailable);}
            try(Response response=opened) {
                if(response.status==301||response.status==302||response.status==303||response.status==307||response.status==308) {
                    if(redirects>=3||response.location==null)throw invalid("插件仓库重定向次数过多或地址缺失");
                    current=safeUri(current.resolve(response.location).toASCIIString());continue;
                }
                if(response.status!=200)throw invalid(response.status==404?"插件仓库目录或文件不存在（404）":response.status==403||response.status==429?"仓库暂时限制访问，请稍后重试":"插件仓库请求失败（"+response.status+"）");
                if(response.encoding!=null&&!response.encoding.isEmpty()&&!response.encoding.equalsIgnoreCase("identity"))throw invalid("插件仓库返回了不支持的压缩传输");
                if(response.length>max||(exact>=0&&response.length>=0&&response.length!=exact))throw invalid("插件文件声明大小与目录不一致或超过限制");
                if(response.body==null)throw invalid("插件仓库返回空响应");
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                while((count=readChunk(response.body,buffer))!=-1) {
                    if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw invalid("插件仓库请求超时或已取消");
                    if(count==0)continue;if(out.size()>max-count||exact>=0&&out.size()>exact-count)throw invalid("插件仓库响应超过大小限制");out.write(buffer,0,count);
                }
                if(exact>=0&&out.size()!=exact)throw invalid("插件实际大小与仓库目录不一致");return new Payload(current,out.toByteArray());
            }
        }
    }
    private static int readChunk(InputStream input,byte[] buffer)throws IOException {
        try{return input.read(buffer);}catch(java.net.SocketTimeoutException timeout){throw new IOException("读取插件仓库超时，请稍后重试",timeout);}
        catch(IOException unavailable){throw new IOException("读取插件仓库失败，请检查网络后重试",unavailable);}
    }
    private static JSONObject json(byte[] bytes)throws Exception {
        String text;
        try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(Exception malformed){throw invalid("插件仓库目录必须使用有效 UTF-8 编码");}
        Object parsed;
        try{parsed=new StrictJson(text).parse();}catch(IOException malformed){throw malformed;}catch(Exception malformed){throw new IOException("插件仓库目录的 JSON 格式无效",malformed);}
        if(!(parsed instanceof JSONObject))throw invalid("插件仓库目录必须为 JSON 对象");return(JSONObject)parsed;
    }
    private static String required(JSONObject data,String key,int max)throws IOException {String value=optional(data,key,max,null);if(value==null||value.trim().isEmpty())throw invalid("仓库字段缺失或为空："+key);return value;}
    private static String optional(JSONObject data,String key,int max,String fallback)throws IOException {Object value=data.opt(key);if(value==null)return fallback;if(!(value instanceof String)||((String)value).length()>max||((String)value).indexOf('\0')>=0)throw invalid("仓库文字字段无效："+key);return(String)value;}
    private static int integer(JSONObject data,String key,int min,int max)throws IOException {Object value=data.opt(key);if(!(value instanceof Number))throw invalid("仓库数字字段无效："+key);double number=((Number)value).doubleValue();if(!Double.isFinite(number)||number!=Math.rint(number)||number<min||number>max)throw invalid("仓库数字字段越界："+key);return(int)number;}
    private static void fields(JSONObject data,String... allowed)throws IOException {Set<String> names=new HashSet<>(Arrays.asList(allowed));for(Iterator<String> keys=data.keys();keys.hasNext();){String key=keys.next();if(!names.contains(key))throw invalid("仓库包含未知字段："+key);}}
    private static IOException invalid(String message){return new IOException(message);}

    /** Android's permissive JSONObject parser accepts duplicate keys/comments. Network catalogs do not. */
    private static final class StrictJson {
        private final String text;private int at,nodes;
        StrictJson(String text){this.text=text;}
        Object parse()throws Exception {Object value=value(0);space();if(at!=text.length())throw invalid("仓库 JSON 尾部存在多余内容");return value;}
        private Object value(int depth)throws Exception {
            if(depth>12||++nodes>8192)throw invalid("仓库 JSON 层级或项目过多");space();if(at>=text.length())throw invalid("仓库 JSON 不完整");char token=text.charAt(at);
            if(token=='{') {
                at++;JSONObject out=new JSONObject();Set<String> keys=new HashSet<>();space();if(take('}'))return out;
                do {space();if(at>=text.length()||text.charAt(at)!='"')throw invalid("仓库 JSON 键必须使用双引号");String key=string();if(key.length()>128||!keys.add(key))throw invalid("仓库 JSON 包含重复或过长的键");space();expect(':');out.put(key,value(depth+1));space();if(take('}'))return out;expect(',');}while(true);
            }
            if(token=='[') {at++;JSONArray out=new JSONArray();space();if(take(']'))return out;do{if(out.length()>=256)throw invalid("仓库 JSON 数组过长");out.put(value(depth+1));space();if(take(']'))return out;expect(',');}while(true);}
            if(token=='"')return string();
            for(String literal:new String[]{"true","false","null"})if(text.startsWith(literal,at)){at+=literal.length();return literal.equals("null")?JSONObject.NULL:literal.equals("true");}
            int start=at;if(take('-')){}if(at>=text.length())throw invalid("仓库 JSON 数字不完整");
            if(take('0')){}else {if(text.charAt(at)<'1'||text.charAt(at)>'9')throw invalid("仓库 JSON 数值无效");while(at<text.length()&&digit(text.charAt(at)))at++;}
            if(take('.')){int mark=at;while(at<text.length()&&digit(text.charAt(at)))at++;if(mark==at)throw invalid("仓库 JSON 小数不完整");}
            if(at<text.length()&&(text.charAt(at)=='e'||text.charAt(at)=='E')){at++;if(at<text.length()&&(text.charAt(at)=='+'||text.charAt(at)=='-'))at++;int mark=at;while(at<text.length()&&digit(text.charAt(at)))at++;if(mark==at)throw invalid("仓库 JSON 指数不完整");}
            if(at-start>80)throw invalid("仓库 JSON 数字过长");BigDecimal number=new BigDecimal(text.substring(start,at));if(!Double.isFinite(number.doubleValue()))throw invalid("仓库 JSON 数字越界");return number;
        }
        private String string()throws IOException {
            expect('"');StringBuilder out=new StringBuilder();boolean ended=false;
            while(at<text.length()) {char c=text.charAt(at++);if(c=='"'){ended=true;break;}if(c<0x20)throw invalid("仓库 JSON 字符串包含控制字符");
                if(c=='\\'){if(at>=text.length())throw invalid("仓库 JSON 转义不完整");char escape=text.charAt(at++);switch(escape){case '"':c='"';break;case '\\':c='\\';break;case '/':c='/';break;case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;case 'u':if(at+4>text.length())throw invalid("仓库 JSON Unicode 不完整");int code=0;for(int n=0;n<4;n++){int digit=Character.digit(text.charAt(at++),16);if(digit<0)throw invalid("仓库 JSON Unicode 无效");code=(code<<4)|digit;}c=(char)code;break;default:throw invalid("仓库 JSON 转义无效");}}
                out.append(c);
            }
            if(!ended)throw invalid("仓库 JSON 字符串未闭合");
            for(int n=0;n<out.length();n++){char c=out.charAt(n);if(Character.isHighSurrogate(c)){if(++n>=out.length()||!Character.isLowSurrogate(out.charAt(n)))throw invalid("仓库 JSON Unicode 代理项无效");}else if(Character.isLowSurrogate(c))throw invalid("仓库 JSON Unicode 代理项无效");}
            return out.toString();
        }
        private static boolean digit(char c){return c>='0'&&c<='9';}
        private void space(){while(at<text.length()&&" \t\r\n".indexOf(text.charAt(at))>=0)at++;}
        private boolean take(char c){if(at<text.length()&&text.charAt(at)==c){at++;return true;}return false;}
        private void expect(char c)throws IOException {if(!take(c))throw invalid("仓库 JSON 格式无效");}
    }
}
