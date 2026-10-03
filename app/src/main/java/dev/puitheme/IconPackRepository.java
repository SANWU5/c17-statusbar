// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Immutable validated assets in app-private DE storage; settings contain only ordered library IDs. */
public final class IconPackRepository {
    public static final String MASTER="status_icon_pack_enabled",LAYERS="status_icon_pack_layers";
    public static final int MAX_LAYERS=32,MAX_LIBRARY=32,MAX_ACTIVE=16;
    public static final String URI_PREFIX="content://"+StatusBarSettings.AUTHORITY+"/icon-pack/";
    private static final Object LOCK=new Object();
    private IconPackRepository(){ }
    public static Map<String,Boolean> booleanDefaults(){return Collections.singletonMap(MASTER,false);}
    public static Map<String,String> stringDefaults(){return Collections.singletonMap(LAYERS,"[]");}
    public static final class Layer {
        public final String id;public final boolean enabled,autoMatch;
        public Layer(String id,boolean enabled){this(id,enabled,true);}
        public Layer(String id,boolean enabled,boolean autoMatch){if(!IconPackManifest.validRevision(id))throw new IllegalArgumentException("Invalid icon pack ID");this.id=id;this.enabled=enabled;this.autoMatch=autoMatch;}
    }
    public static List<Layer> layers(String stored)throws IOException{
        if(stored==null||stored.length()>8192)throw new IOException("图标库列表无效");
        try{
            JSONArray json=new JSONArray(stored);
            if(json.length()>MAX_LAYERS)throw new IOException("最多保存 32 个图标库");
            List<Layer> result=new ArrayList<>();Set<String> seen=new HashSet<>();int active=0;
            for(int i=0;i<json.length();i++){
                Object value=json.get(i);if(!(value instanceof JSONObject))throw new IOException("图标库列表格式错误");
                JSONObject item=(JSONObject)value;Object id=item.opt("id"),enabled=item.opt("enabled");
                boolean legacy=item.length()==2&&!item.has("autoMatch");Object autoMatch=legacy?Boolean.TRUE:item.opt("autoMatch");
                if(!(id instanceof String)||!IconPackManifest.validRevision((String)id)||!(enabled instanceof Boolean)
                        ||!(autoMatch instanceof Boolean)||(!legacy&&item.length()!=3)||!seen.add((String)id))throw new IOException("图标库 ID、开关或重复项无效");
                if(Boolean.TRUE.equals(enabled)&&++active>MAX_ACTIVE)throw new IOException("最多同时启用 16 个图标库");
                result.add(new Layer((String)id,(Boolean)enabled,(Boolean)autoMatch));
            }
            return Collections.unmodifiableList(result);
        }catch(JSONException malformed){throw new IOException("图标库列表格式错误",malformed);}
    }
    public static String layersJson(List<Layer> layers)throws IOException{
        try{
            JSONArray array=new JSONArray();for(Layer layer:layers){JSONObject item=new JSONObject();item.put("id",layer.id);item.put("enabled",layer.enabled);item.put("autoMatch",layer.autoMatch);array.put(item);}
            String result=array.toString();layers(result);return result;
        }catch(JSONException malformed){throw new IOException(malformed);}
    }
    public static final class Pack {
        public final String id,name,author,license;
        public final int iconCount;
        public final IconPackManifest manifest;
        Pack(String id,String name,IconPackManifest manifest){
            this.id=id;this.name=name;this.author=manifest.author;this.license=manifest.license;
            this.iconCount=manifest.icons.size();this.manifest=manifest;
        }
    }
    private static File root(Context context)throws IOException{
        if(context==null||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName()))throw new SecurityException("Own application context required");
        File files=context.createDeviceProtectedStorageContext().getFilesDir().getCanonicalFile();
        File root=new File(files,"status-icon-packs");
        if(!root.exists()&&!root.mkdir())throw new IOException("图标库目录无法创建");
        if(!root.getCanonicalFile().equals(root.getAbsoluteFile())||!root.isDirectory())throw new IOException("图标库目录无效");
        return root;
    }
    static File createSource(Context context)throws IOException{return File.createTempFile("source-",".archive",root(context));}
    static File packDirectory(File root,String id)throws IOException{
        if(!IconPackManifest.validRevision(id))throw new IOException("图标库 ID 无效");
        File target=new File(root,id);
        if(!target.getCanonicalFile().equals(target.getAbsoluteFile())||!target.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))
            throw new IOException("图标库路径无效");
        return target;
    }
    public static List<Pack> list(Context context)throws IOException{
        synchronized(LOCK){
            File root=root(context);File[] folders=root.listFiles();List<Pack> result=new ArrayList<>();
            if(folders==null)throw new IOException("图标库无法读取");
            Arrays.sort(folders,Comparator.comparing(File::getName));
            for(File folder:folders)if(IconPackManifest.validRevision(folder.getName())&&folder.isDirectory()){
                try{result.add(load(root,folder.getName()));}catch(IOException malformed){/* Incomplete/corrupt library never reaches SystemUI. */}
                if(result.size()>=MAX_LIBRARY)break;
            }
            return Collections.unmodifiableList(result);
        }
    }
    /** Optional user-supplied private build asset; call on the settings import worker. */
    public static List<Pack> installBundled(Context context)throws Exception{
        File root=root(context);byte[] source;
        try(InputStream input=context.getAssets().open("icon-packs/pui-local.zip")){
            source=IconPackArchive.readEntry(input,IconPackArchive.MAX_ARCHIVE);
        }catch(FileNotFoundException notBundled){return Collections.emptyList();}
        String sourceHash=sha256(source);
        synchronized(LOCK){
            File marker=new File(root,"bundled-pui.json");
            if(marker.isFile())try{
                JSONObject stored=new JSONObject(new String(readFile(marker,1024),StandardCharsets.UTF_8));
                if(sourceHash.equals(stored.optString("source")))return Collections.singletonList(load(root,stored.optString("id")));
            }catch(IOException|JSONException invalidMarker){/* Rebuild missing or damaged optional library. */}
            File archive=createSource(context);
            try{
                writeSynced(archive,source);
                try(PreparedPack prepared=prepare(context,archive,"")){
                    Pack pack=prepared.commit(()->true);JSONObject json=new JSONObject();json.put("source",sourceHash);json.put("id",pack.id);
                    File pending=File.createTempFile("bundled-",".json",root);
                    try{writeSynced(pending,json.toString().getBytes(StandardCharsets.UTF_8));android.system.Os.rename(pending.getAbsolutePath(),marker.getAbsolutePath());}finally{pending.delete();}
                    return Collections.singletonList(pack);
                }
            }finally{archive.delete();}
        }
    }
    private static String sha256(byte[] data)throws Exception{
        StringBuilder hash=new StringBuilder(64);for(byte value:MessageDigest.getInstance("SHA-256").digest(data))hash.append(String.format(Locale.ROOT,"%02x",value&255));return hash.toString();
    }
    public static PreparedPack preparePuiTheme(Context context,Uri uri)throws Exception{return PuiThemeIconPack.prepare(context,uri);}
    private static Pack load(File root,String id)throws IOException{
        File directory=packDirectory(root,id);
        IconPackManifest manifest=IconPackManifest.parse(readFile(new File(directory,"manifest.json"),IconPackManifest.MAX_BYTES));
        String name=manifest.name;File label=new File(directory,"name.json");
        if(label.isFile())try{name=displayName(new JSONObject(new String(readFile(label,1024),StandardCharsets.UTF_8)).optString("name"),name);}catch(JSONException ignored){ }
        return new Pack(id,name,manifest);
    }
    public static PreparedPack prepare(Context context,Uri uri,String name)throws Exception{
        File archive=createSource(context);
        try{
            try(InputStream input=context.getContentResolver().openInputStream(uri);FileOutputStream output=new FileOutputStream(archive)){
                if(input==null)throw new IOException("图标包无法读取");
                copyBounded(input,output,IconPackArchive.MAX_ARCHIVE);output.getFD().sync();
            }
            return prepare(context,archive,name);
        }finally{archive.delete();}
    }
    public static PreparedPack prepareDownload(Context context,String githubInput,String name)throws Exception{
        return IconPackDownload.prepare(context,githubInput,name,null);
    }
    public static PreparedPack prepareDownload(Context context,String githubInput,String name,BooleanSupplier cancelled)throws Exception{
        return IconPackDownload.prepare(context,githubInput,name,cancelled);
    }
    /** Download workers may pass an already bounded private archive; it is not consumed or deleted. */
    public static PreparedPack prepare(Context context,File archive,String name)throws Exception{
        File root=root(context);Map<String,byte[]> source=IconPackArchive.read(archive);
        IconPackManifest manifest=IconPackManifest.parse(source.get("manifest.json"));
        String label=displayName(name,manifest.name);
        File staging=File.createTempFile("pending-",".pack",root);
        if(!staging.delete()||!staging.mkdir())throw new IOException("图标包临时目录无法创建");
        boolean ready=false;
        try{
            File assets=new File(staging,"assets");if(!assets.mkdir())throw new IOException("图标包资源目录无法创建");
            Map<String,String> normalized=new TreeMap<>();Map<String,String> reused=new HashMap<>();int bytesTotal=0;
            for(Map.Entry<String,String> icon:manifest.icons.entrySet()){
                String destination=reused.get(icon.getValue());
                if(destination==null){
                    byte[] pixels=source.get(icon.getValue());if(pixels==null)throw new IOException("图标包缺少资源："+icon.getValue());
                    byte[] png=normalize(pixels);bytesTotal+=png.length;
                    if(bytesTotal>IconPackArchive.MAX_EXPANDED)throw new IOException("图标包图片总大小超过限制");
                    destination="assets/"+String.format(Locale.ROOT,"icon-%03d.png",reused.size());
                    writeSynced(new File(staging,destination),png);reused.put(icon.getValue(),destination);
                }
                normalized.put(icon.getKey(),destination);
            }
            JSONObject json=new JSONObject(new String(manifest.bytes(),StandardCharsets.UTF_8));
            JSONObject icons=new JSONObject();for(Map.Entry<String,String> icon:normalized.entrySet())icons.put(icon.getKey(),icon.getValue());json.put("icons",icons);
            IconPackManifest validated=IconPackManifest.parse(json.toString().getBytes(StandardCharsets.UTF_8));
            byte[] canonical=validated.bytes();writeSynced(new File(staging,"manifest.json"),canonical);
            String id=revision(staging,validated,canonical);
            writeName(staging,label);ready=true;
            return new PreparedPack(root,staging,new Pack(id,label,validated));
        }finally{if(!ready)deleteManaged(root,staging);}
    }
    private static byte[] normalize(byte[] data)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(!("image/png".equals(bounds.outMimeType)||"image/webp".equals(bounds.outMimeType))
                ||bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>1024||bounds.outHeight>1024)
            throw new IOException("图标须为 1–1024 像素的 PNG 或 WebP");
        Bitmap original=null,scaled=null;
        try{
            original=BitmapFactory.decodeByteArray(data,0,data.length);if(original==null)throw new IOException("图标图片无法解码");
            if(!original.hasAlpha())throw new IOException("图标必须使用透明背景的 PNG 或 WebP");
            int largest=Math.max(original.getWidth(),original.getHeight());
            scaled=largest>256?Bitmap.createScaledBitmap(original,Math.max(1,original.getWidth()*256/largest),Math.max(1,original.getHeight()*256/largest),true):original;
            ByteArrayOutputStream result=new ByteArrayOutputStream();
            if(!scaled.compress(Bitmap.CompressFormat.PNG,100,result))throw new IOException("图标图片无法保存");
            return result.toByteArray();
        }catch(OutOfMemoryError unavailable){throw new IOException("图标图片解码内存不足",unavailable);}
        finally{if(scaled!=null&&scaled!=original)scaled.recycle();if(original!=null)original.recycle();}
    }
    private static String revision(File staging,IconPackManifest manifest,byte[] canonical)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(canonical);
        for(String asset:new TreeSet<>(manifest.icons.values())){
            digest.update(asset.getBytes(StandardCharsets.UTF_8));digest.update(readFile(new File(staging,asset),IconPackArchive.MAX_ENTRY));
        }
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",value&255));return hash.toString();
    }
    public static final class PreparedPack implements AutoCloseable {
        private final File root,staging;public final Pack pack;private boolean closed;
        PreparedPack(File root,File staging,Pack pack){this.root=root;this.staging=staging;this.pack=pack;}
        public Pack commit(BooleanSupplier canCommit)throws Exception{
            synchronized(LOCK){
                if(closed||!canCommit.getAsBoolean())throw new IOException("导入状态已变化，图标包未保存");
                File target=packDirectory(root,pack.id);
                if(target.isDirectory()){writeName(target,pack.name);close();return load(root,pack.id);}
                File[] folders=root.listFiles();int count=0;
                if(folders!=null)for(File folder:folders)if(IconPackManifest.validRevision(folder.getName()))count++;
                if(count>=MAX_LIBRARY)throw new IOException("最多保存 32 个图标库，请先删除不使用的库");
                android.system.Os.rename(staging.getAbsolutePath(),target.getAbsolutePath());closed=true;return pack;
            }
        }
        @Override public void close(){synchronized(LOCK){if(!closed){closed=true;try{deleteManaged(root,staging);}catch(IOException ignored){}}}}
    }
    public static Pack rename(Context context,String id,String name)throws Exception{
        synchronized(LOCK){File root=root(context);Pack pack=load(root,id);writeName(packDirectory(root,id),displayName(name,pack.name));return load(root,id);}
    }
    /** The UI removes this ID from LAYERS first, then deletes the library on its import worker. */
    public static void delete(Context context,String id)throws IOException{
        synchronized(LOCK){File root=root(context);deleteManaged(root,packDirectory(root,id));}
    }
    public static int clear(Context context)throws IOException{
        synchronized(LOCK){File root=root(context);File[] children=root.listFiles();if(children==null)throw new IOException("图标库无法读取");
            int removed=0;for(File child:children)if(IconPackManifest.validRevision(child.getName())||child.getName().startsWith("pending-")||child.getName().startsWith("source-")||child.getName().equals("bundled-pui.json")){deleteManaged(root,child);removed++;}return removed;}
    }
    public static Uri manifestUri(String id){if(!IconPackManifest.validRevision(id))throw new IllegalArgumentException("Invalid icon pack ID");return Uri.parse(URI_PREFIX+id+"/manifest.json");}
    public static Uri assetUri(String id,String asset){if(!IconPackManifest.validRevision(id)||!IconPackManifest.validAsset(asset))throw new IllegalArgumentException("Invalid icon pack asset");return Uri.parse(URI_PREFIX+id+"/"+asset);}
    /** Invoke after SettingsProvider's existing self/SystemUI UID and read-only checks. */
    public static File providerFile(Context context,Uri uri)throws IOException{
        List<String> parts=uri.getPathSegments();
        if(parts.size()<3||!parts.get(0).equals("icon-pack"))throw new IOException("Unknown icon pack asset");
        synchronized(LOCK){
            File root=root(context),directory=packDirectory(root,parts.get(1));String relative;
            if(parts.size()==3&&parts.get(2).equals("manifest.json"))relative="manifest.json";
            else if(parts.size()==4&&parts.get(2).equals("assets")){
                relative="assets/"+parts.get(3);
                if(!IconPackManifest.validAsset(relative)||!load(root,parts.get(1)).manifest.icons.containsValue(relative))throw new IOException("Unknown icon pack asset");
            }else throw new IOException("Unknown icon pack asset");
            File asset=new File(directory,relative);
            if(!asset.getCanonicalFile().equals(asset.getAbsoluteFile())||!asset.isFile())throw new IOException("No imported icon pack asset");
            return asset;
        }
    }
    private static String displayName(String value,String fallback)throws IOException{
        String name=value==null||value.trim().isEmpty()?fallback:value.trim();
        if(name==null||name.isEmpty()||name.length()>64||name.matches("(?s).*\\p{Cntrl}.*"))throw new IOException("图标库名称需为 1–64 字符");return name;
    }
    private static void writeName(File directory,String name)throws Exception{
        JSONObject json=new JSONObject();json.put("name",name);File pending=File.createTempFile("name-",".json",directory);
        try{writeSynced(pending,json.toString().getBytes(StandardCharsets.UTF_8));android.system.Os.rename(pending.getAbsolutePath(),new File(directory,"name.json").getAbsolutePath());}finally{pending.delete();}
    }
    static byte[] readFile(File file,int limit)throws IOException{
        if(!file.isFile()||!file.getCanonicalFile().equals(file.getAbsoluteFile())||file.length()>limit)throw new IOException("图标库文件无效");
        try(InputStream input=new FileInputStream(file)){return IconPackArchive.readEntry(input,limit);}
    }
    private static void writeSynced(File file,byte[] bytes)throws IOException{try(FileOutputStream output=new FileOutputStream(file)){output.write(bytes);output.getFD().sync();}}
    private static void copyBounded(InputStream input,OutputStream output,int limit)throws IOException{
        int total=0,count;byte[] buffer=new byte[8192];while((count=input.read(buffer))!=-1){total+=count;if(total>limit)throw new IOException("图标包最多 8 MB");output.write(buffer,0,count);}
    }
    private static void deleteManaged(File root,File file)throws IOException{
        File canonicalRoot=root.getCanonicalFile(),canonical=file.getCanonicalFile();
        if(!canonical.equals(file.getAbsoluteFile())||!canonical.toPath().startsWith(canonicalRoot.toPath())||canonical.equals(canonicalRoot))throw new IOException("图标库删除路径无效");
        if(!file.exists())return;
        if(file.isDirectory()){File[] children=file.listFiles();if(children==null)throw new IOException("图标库目录无法读取");for(File child:children)deleteManaged(root,child);}
        if(!file.delete())throw new IOException("图标库文件无法删除");
    }
}
