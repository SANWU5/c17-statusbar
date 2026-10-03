// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Converts only verified resource data supplied by the user; APK code is never loaded or installed. */
public final class PuiThemeIconPack {
    private static final int MAX_THEME=48*1024*1024,MAX_APK=2*1024*1024;
    static final String SIGNAL="PuiThemeSignalIcon",STATUS="PuiThemeStatusIcon";
    private PuiThemeIconPack(){ }
    static Map<String,String> roles(String apk){
        Map<String,String> result=new LinkedHashMap<>();
        if(SIGNAL.equals(apk)){
            for(int i=0;i<=4;i++){result.put("wifi."+i,"stat_signal_wifi_signal_"+i+"_os17");result.put("cellular."+i,"stat_signal_signal_lte_single_"+i+"_os17");}
            result.put("cellular.none","stat_signal_signal_null_lte");
        }else if(STATUS.equals(apk)){
            result.put("hint.bluetooth.off","stat_sys_data_bluetooth");result.put("hint.bluetooth.on","stat_sys_data_bluetooth_connected");
            result.put("hint.location","stat_sys_location");result.put("hint.alarm_clock","stat_sys_alarm");result.put("hint.zen","stat_sys_dnd");
            result.put("hint.headset","stat_sys_headset");result.put("hint.vpn","stat_sys_vpn_ic");result.put("hint.nfc","stat_sys_nfc");result.put("hint.airplane","stat_sys_airplane_mode");
        }
        return Collections.unmodifiableMap(result);
    }
    static String selectedApk(String path){
        for(String name:new String[]{SIGNAL,STATUS})if(("system/product/overlay/"+name+".apk").equals(path))return name;
        return null;
    }
    static boolean executableEntry(String name){
        String lower=name.toLowerCase(Locale.ROOT);
        return lower.matches("(?:.*/)?classes(?:[0-9]+)?\\.dex")||lower.endsWith(".so")||lower.startsWith("lib/");
    }
    static void validateApk(File apk)throws IOException{
        try(ZipFile zip=new ZipFile(apk)){
            Set<String> seen=new HashSet<>();int entries=0;long expanded=0;boolean table=false,manifest=false;
            for(Enumeration<? extends ZipEntry> it=zip.entries();it.hasMoreElements();){ZipEntry entry=it.nextElement();String name=entry.getName();
                if(++entries>512||!seen.add(name)||!IconPackArchive.safePath(name,entry.isDirectory())||executableEntry(name))throw new IOException("PUI 资源 APK 包含不支持的内容");
                long size=entry.getSize();if(size<0||size>MAX_APK||(expanded+=size)>MAX_APK)throw new IOException("PUI 资源 APK 大小超限");
                table|=name.equals("resources.arsc");manifest|=name.equals("AndroidManifest.xml");
            }
            if(!table||!manifest)throw new IOException("PUI 资源 APK 缺少资源表");
        }
    }
    public static IconPackRepository.PreparedPack prepare(Context context,Uri uri)throws Exception{
        File theme=IconPackRepository.createSource(context),converted=IconPackRepository.createSource(context);
        List<File> apks=new ArrayList<>();
        try{
            try(InputStream input=context.getContentResolver().openInputStream(uri);FileOutputStream output=new FileOutputStream(theme)){
                if(input==null)throw new IOException("PUI 主题无法读取");copy(input,output,MAX_THEME);output.getFD().sync();
            }
            Map<String,byte[]> images=new LinkedHashMap<>();Map<String,String> icons=new TreeMap<>();Set<String> found=new HashSet<>();
            try(ZipFile zip=new ZipFile(theme)){
                int entries=0;
                for(Enumeration<? extends ZipEntry> it=zip.entries();it.hasMoreElements();){ZipEntry entry=it.nextElement();
                    if(++entries>4096)throw new IOException("PUI 主题文件数量超限");String selected=selectedApk(entry.getName());if(selected==null)continue;
                    if(entry.isDirectory()||!found.add(selected)||entry.getSize()<1||entry.getSize()>MAX_APK)throw new IOException("PUI 主题资源重复或大小无效");
                    File apk=IconPackRepository.createSource(context);apks.add(apk);
                    try(InputStream input=zip.getInputStream(entry);FileOutputStream output=new FileOutputStream(apk)){copy(input,output,MAX_APK);output.getFD().sync();}
                    validateApk(apk);
                    try(Loaded loaded=Loaded.open(apk,context,"com.android.systemui."+selected)){
                        for(Map.Entry<String,String> role:roles(selected).entrySet()){
                            String asset="assets/"+role.getKey().replace('.','-')+".png";
                            images.put(asset,loaded.png("com.android.systemui."+selected,role.getValue()));icons.put(role.getKey(),asset);
                        }
                    }
                }
            }
            if(found.size()!=2)throw new IOException("未找到 PUI ColorOS 17 的信号与系统提示资源，请选择原始主题 ZIP");
            JSONObject manifest=new JSONObject();manifest.put("format",IconPackManifest.FORMAT);manifest.put("version",2);manifest.put("fallback","native");manifest.put("render","mask");
            manifest.put("name","PUI ColorOS 17 · 用户主题");manifest.put("author","天伞桜&PanL");manifest.put("license","用户提供素材，仅本地使用；原主题未声明公开再分发许可");
            JSONObject mapping=new JSONObject();for(Map.Entry<String,String> role:icons.entrySet())mapping.put(role.getKey(),role.getValue());manifest.put("icons",mapping);
            byte[] metadata=IconPackManifest.parse(manifest.toString().getBytes(StandardCharsets.UTF_8)).bytes();
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(converted))){write(zip,"manifest.json",metadata);for(Map.Entry<String,byte[]> asset:images.entrySet())write(zip,asset.getKey(),asset.getValue());}
            return IconPackRepository.prepare(context,converted,"");
        }catch(OutOfMemoryError unavailable){throw new IOException("PUI 主题图片转换内存不足",unavailable);}
        finally{theme.delete();converted.delete();for(File apk:apks)apk.delete();}
    }
    private static void copy(InputStream input,OutputStream output,int max)throws IOException{
        int total=0,n;byte[] bytes=new byte[8192];while((n=input.read(bytes))!=-1){if((long)total+n>max)throw new IOException("PUI 主题文件大小超限");total+=n;output.write(bytes,0,n);}
    }
    private static void write(ZipOutputStream zip,String name,byte[] bytes)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
    /** A unique private APK path owns these Resources; no APK class loader is created.
     * Do not construct Resources on Resources.getSystem().getAssets(): adding a loader
     * to that wrapper would mutate the shared framework AssetManager on Android. */
    private static final class Loaded implements AutoCloseable{
        final Resources resources;
        Loaded(Resources resources){this.resources=resources;}
        static Loaded open(File apk,Context context,String apkPackage)throws Exception{
            PackageManager manager=context.getPackageManager();
            PackageInfo parsed=manager.getPackageArchiveInfo(apk.getAbsolutePath(),0);
            if(parsed==null||!apkPackage.equals(parsed.packageName))throw new IOException("PUI 资源 APK 包名不匹配");
            // Build fresh metadata rather than accepting split/shared-library paths
            // from the supplied manifest. Both resource paths point to our validated file.
            ApplicationInfo info=new ApplicationInfo();info.packageName=apkPackage;
            info.sourceDir=apk.getAbsolutePath();info.publicSourceDir=info.sourceDir;
            Resources resources=manager.getResourcesForApplication(info);
            if(resources.getAssets()==Resources.getSystem().getAssets()||resources.getAssets()==context.getResources().getAssets())
                throw new IOException("PUI 资源未能隔离加载");
            return new Loaded(resources);
        }
        byte[] png(String apkPackage,String name)throws Exception{
            int id=resources.getIdentifier(name,"drawable",apkPackage);if(id==0)throw new IOException("PUI 主题缺少图标："+name);
            validateXml(id,apkPackage,0,new HashSet<Integer>());
            Drawable drawable=resources.getDrawable(id,null);int width=drawable.getIntrinsicWidth(),height=drawable.getIntrinsicHeight();
            if(width<=0||height<=0||width>4096||height>4096)throw new IOException("PUI 图标尺寸无效");
            double factor=256d/Math.max(width,height);width=Math.max(1,(int)Math.round(width*factor));height=Math.max(1,(int)Math.round(height*factor));
            Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            try{drawable.setBounds(0,0,width,height);drawable.draw(new Canvas(bitmap));ByteArrayOutputStream output=new ByteArrayOutputStream();
                if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("PUI 图标转换失败");return output.toByteArray();
            }finally{bitmap.recycle();}
        }
        private void validateXml(int id,String apkPackage,int depth,Set<Integer> visiting)throws Exception{
            if(depth>8||!visiting.add(id)||!apkPackage.equals(resources.getResourcePackageName(id))||!"drawable".equals(resources.getResourceTypeName(id)))throw new IOException("PUI 图标引用无效");
            try(XmlResourceParser xml=resources.getXml(id)){
                int nodes=0,pathChars=0;boolean root=false;
                for(int event=xml.next();event!=XmlPullParser.END_DOCUMENT;event=xml.next())if(event==XmlPullParser.START_TAG){
                    String tag=xml.getName();if(++nodes>128||xml.getDepth()>8||!Arrays.asList("inset","vector","group","path","clip-path").contains(tag))throw new IOException("PUI 图标使用不支持的动态资源");
                    if(!root){if(!tag.equals("vector")&&!tag.equals("inset"))throw new IOException("PUI 图标格式无效");root=true;}
                    String path=xml.getAttributeValue("http://schemas.android.com/apk/res/android","pathData");if(path!=null&&(pathChars+=path.length())>65536)throw new IOException("PUI 图标路径大小超限");
                    if(tag.equals("inset")){
                        int referenced=xml.getAttributeResourceValue("http://schemas.android.com/apk/res/android","drawable",0);
                        if(referenced!=0)validateXml(referenced,apkPackage,depth+1,visiting);
                    }
                }
                if(!root)throw new IOException("PUI 图标内容为空");
            }finally{visiting.remove(id);}
        }
        @Override public void close(){resources.getAssets().close();}
    }
}
