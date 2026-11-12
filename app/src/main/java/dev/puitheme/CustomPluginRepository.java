// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.os.Bundle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;
import java.util.Enumeration;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Private, atomic library. Metadata is compiled in isolation; API 2 runtime is admitted, never executed here. */
public final class CustomPluginRepository {
    private static final Object LOCK=new Object();
    private static final List<java.lang.ref.WeakReference<NativeSettingsSession>> NATIVE_SETTINGS=new ArrayList<>();
    private static final int MAX_PLUGINS=16,MAX_PACKAGE=1024*1024,MAX_DATABASE=8*1024*1024;
    private final File directory,database;
    private final Context context;
    public CustomPluginRepository(Context context) {
        this.context=context.createDeviceProtectedStorageContext();
        directory=new File(this.context.getFilesDir(),"custom_plugins");database=new File(directory,"library.json");
    }
    /** Package-local desktop checks use the same production storage with a temporary directory. */
    CustomPluginRepository(File directory) {this.context=null;this.directory=directory;database=new File(directory,"library.json");}
    public static final class NativeSettingsSession implements AutoCloseable{
        private final String owner,id;private final int version;private volatile boolean valid=true;private Runnable invalidated;
        private NativeSettingsSession(String owner,String id,int version){this.owner=owner;this.id=id;this.version=version;}
        public boolean isCurrent(){return valid;}
        public void onInvalidated(Runnable listener){synchronized(LOCK){invalidated=listener;if(!valid&&listener!=null)listener.run();}}
        @Override public void close(){synchronized(LOCK){valid=false;invalidated=null;NATIVE_SETTINGS.removeIf(reference->reference.get()==null||reference.get()==this);}}
    }
    public NativeSettingsSession nativeSettingsSession(String id,int version)throws Exception{
        synchronized(LOCK){JSONObject record=require(load(),id);if(record.getJSONObject("descriptor").getInt("versionCode")!=version||!record.getJSONObject("descriptor").has("nativeSettings"))throw new IOException("插件原生设置版本已改变");
            NativeSettingsSession session=new NativeSettingsSession(database.getAbsolutePath(),id,version);NATIVE_SETTINGS.add(new java.lang.ref.WeakReference<>(session));return session;}
    }
    private void invalidateNativeSettings(String id){
        String owner=database.getAbsolutePath();for(java.util.Iterator<java.lang.ref.WeakReference<NativeSettingsSession>> it=NATIVE_SETTINGS.iterator();it.hasNext();){
            NativeSettingsSession session=it.next().get();if(session==null||!session.valid){it.remove();continue;}
            if(session.owner.equals(owner)&&session.id.equals(id)){session.valid=false;if(session.invalidated!=null)try{session.invalidated.run();}catch(Throwable ignored){}it.remove();}
        }
    }
    private void clearNativeSettingsCache(String id)throws IOException{
        if(context==null)return;File root=new File(context.getCodeCacheDir(),"plugin-settings"),folder=child(root,id);if(folder.exists())removeTree(folder,root);
    }

    public static final class Plugin {
        public final String id,name,version,description,author,error,origin;
        public final int versionCode;
        public final long installedAt,updatedAt;
        public final boolean enabled;
        public final JSONObject descriptor,state;
        public final String runtimeSource,runtimeBaseDir;
        private Plugin(JSONObject record)throws Exception {
            descriptor=copy(record.getJSONObject("descriptor"));state=copy(record.getJSONObject("state"));
            id=descriptor.getString("id");name=descriptor.getString("name");version=descriptor.getString("version");
            versionCode=descriptor.getInt("versionCode");description=descriptor.optString("description","");author=descriptor.optString("author","");
            enabled=record.getBoolean("enabled");error=record.optString("error","");
            installedAt=record.optLong("installedAt",0L);updatedAt=record.optLong("updatedAt",0L);
            origin=record.optString("origin","本地文件或旧版未知");
            runtimeSource=record.optString("runtimeSource","");runtimeBaseDir=record.optString("runtimeBaseDir","");
        }
    }
    public static final class Preview implements AutoCloseable {
        public final JSONObject descriptor;
        public final String source,previousVersion;
        public final boolean upgrade;
        private final String approvedDescriptor,originalSource;
        private final String previousRecord;
        private final int previousCode;
        private final File assets,previewRoot;
        private final String runtimeSource;
        private Preview(JSONObject descriptor,String source,JSONObject old,File assets,File previewRoot,String runtimeSource)throws Exception {
            this.descriptor=copy(descriptor);this.source=source;this.originalSource=source;
            approvedDescriptor=descriptor.toString();upgrade=old!=null;
            previousRecord=old==null?"":old.toString();
            JSONObject previous=old==null?null:old.getJSONObject("descriptor");
            previousVersion=previous==null?"":previous.getString("version");previousCode=previous==null?0:previous.getInt("versionCode");
            this.assets=assets;this.previewRoot=previewRoot;this.runtimeSource=runtimeSource;
        }
        /** A cancelled import can immediately release its untrusted private staging files. */
        @Override public void close()throws IOException {if(assets!=null)removeTree(assets,previewRoot);}
    }

    public Preview preview(InputStream input,String fileName)throws Exception {
        if(input==null)throw new IOException("无法读取插件文件");
        String lower=fileName==null?"":fileName.toLowerCase(Locale.ROOT);
        if(!lower.endsWith(".js")&&!lower.endsWith(".zip"))throw new IOException("请选择 .js 文件或 .zip 插件包");
        JSONObject manifest=null;String source;File assets=null,previewRoot=new File(directory,".previews");boolean accepted=false;
        try {
        if(lower.endsWith(".zip")) {
            if(!previewRoot.isDirectory()&&!previewRoot.mkdirs())throw new IOException("无法创建插件预览目录");
            assets=new File(previewRoot,UUID.randomUUID().toString());if(!assets.mkdir())throw new IOException("无法创建插件预览目录");
            File archive=File.createTempFile("package-",".zip",previewRoot);
            try {
                try(FileOutputStream output=new FileOutputStream(archive)){transfer(input,output);output.getFD().sync();}
                manifest=extract(archive,assets);
            } finally {Files.deleteIfExists(archive.toPath());}
            String entry=manifest.getString("entry");validPath(entry);
            if(!entry.endsWith(".js"))throw new IOException("插件入口必须为 .js 文件");
            File script=child(assets,entry);if(!script.isFile())throw new IOException("ZIP 缺少入口脚本："+entry);
            source=utf8(Files.readAllBytes(script.toPath()));
        } else source=utf8(readBounded(input,Integer.MAX_VALUE));
        JSONObject descriptor=evaluate(source,new JSONObject());
        CustomPluginSupport.requireCompatible(descriptor);
        if(descriptor.has("nativeSettings")){
            if(assets==null)throw new IOException("原生设置界面需要包含代码资源的 ZIP 插件包");
            if(!child(assets,descriptor.getJSONObject("nativeSettings").getString("dex")).isFile())throw new IOException("ZIP 缺少原生设置代码");
        }
        CustomPluginSchema.requireConfiguration(descriptor);
        if(manifest!=null)matchManifest(manifest,descriptor);
        synchronized(LOCK) {
            JSONObject old=find(load(),descriptor.getString("id"));
            // An upgrade may merge or rename controls. Let its defaults see the previous
            // state before normalization drops retired keys; identity/permissions stay fixed.
            if(old!=null) {
                JSONObject migrated=evaluate(source,copy(old.getJSONObject("state")));
                CustomPluginSchema.requireConfiguration(migrated);
                if(!CustomPluginSchema.sameIdentity(descriptor,migrated))throw new IOException("插件身份或权限不能随旧配置改变");
                descriptor=migrated;
            }
            JSONObject state=stateFor(descriptor,old==null?new JSONObject():old.getJSONObject("state"));
            JSONObject evaluated=evaluate(source,state);
            CustomPluginSchema.requireConfiguration(evaluated);
            if(!CustomPluginSchema.sameIdentity(descriptor,evaluated))throw new IOException("插件身份或权限不能随状态改变");
            // The reviewed plan is generated with exactly the defaults that installation will store.
            stateFor(evaluated,state);descriptor=evaluated;
            String runtime=resolveRuntime(descriptor,assets);
            Preview result=new Preview(descriptor,source,old,assets,previewRoot,runtime);accepted=true;return result;
        }
        } finally {if(!accepted&&assets!=null)removeTree(assets,previewRoot);}
    }

    public Plugin install(Preview preview,boolean confirmUpgrade)throws Exception {
        return install(preview,confirmUpgrade,"本地文件");
    }
    public Plugin install(Preview preview,boolean confirmUpgrade,String origin)throws Exception {
        if(preview==null)throw new IllegalArgumentException("缺少待安装插件");
        if(origin==null||origin.trim().isEmpty()||origin.length()>2048||origin.indexOf('\0')>=0)throw new IllegalArgumentException("插件来源说明无效");
        synchronized(LOCK) {
            JSONObject data=load(),plugins=data.getJSONObject("plugins");
            JSONObject approved=new JSONObject(preview.approvedDescriptor);
            String id=approved.getString("id");JSONObject old=plugins.optJSONObject(id);
            if(old!=null&&!confirmUpgrade)throw new IOException("请先确认替换已安装插件");
            if(old!=null&&(!preview.upgrade||old.getJSONObject("descriptor").getInt("versionCode")!=preview.previousCode))
                throw new IOException("已安装版本发生变化，请重新导入预览");
            if(old!=null&&!preview.previousRecord.equals(old.toString()))throw new IOException("插件状态发生变化，请重新导入预览");
            if(old==null&&preview.upgrade)throw new IOException("原插件已移除，请重新导入预览");
            if(old==null&&approved.optInt("apiVersion")==1&&legacyCount(plugins)>=MAX_PLUGINS)throw new IOException("API 1 最多保留 16 个插件；完整执行插件请使用 API 2");
            JSONObject state=stateFor(approved,old==null?new JSONObject():old.getJSONObject("state"));
            JSONObject descriptor=evaluate(preview.originalSource,state);
            CustomPluginSchema.requireConfiguration(descriptor);
            if(!CustomPluginSchema.sameIdentity(approved,descriptor))throw new IOException("插件身份或权限发生变化，请重新导入");
            if(!equivalent(approved,descriptor))throw new IOException("插件生成的界面或规则与预览不同，请使用可重复生成的脚本");
            JSONObject record=new JSONObject().put("source",preview.originalSource).put("descriptor",descriptor)
                    .put("state",state).put("enabled",false).put("error","");
            long now=System.currentTimeMillis();
            record.put("installedAt",old==null?now:old.optLong("installedAt",0L)).put("updatedAt",now).put("origin",origin);
            File installedAssets=null;
            try {
                if(descriptor.optInt("apiVersion")==2) {
                    installedAssets=installAssets(id,descriptor.getInt("versionCode"),preview.assets);
                    record.put("runtimeSource",preview.runtimeSource).put("runtimeBaseDir",installedAssets.getCanonicalPath())
                            .put("runtimeAssets",assetList(installedAssets));
                }
                plugins.put(id,record);save(data);invalidateNativeSettings(id);
            } catch(Exception failure) {
                if(installedAssets!=null)removeTree(installedAssets,new File(directory,"plugin_data"));
                throw failure;
            }
            // Only a saved library retires what it replaced; a failed import above leaves every earlier copy in place.
            retireGenerations(id,installedAssets);
            try{preview.close();}catch(IOException retainedPreview){/* Installation committed; an obsolete private preview may be cleaned later. */}
            try{clearNativeSettingsCache(id);}catch(IOException retainedCache){/* Best effort for obsolete, non-authoritative code-cache files. */}
            return new Plugin(record);
        }
    }

    void reconcileVisibility()throws Exception {
        reconcileVisibility(false);
    }
    void reconcileVisibility(boolean force)throws Exception {
        // Startup and writes share one lock: a delayed startup cannot regrant a just-disabled scope.
        synchronized(LOCK){CustomPluginVisibility.reconcile(context,load().getJSONObject("plugins"),force);}
    }

    public List<Plugin> list()throws Exception {
        synchronized(LOCK) {
            JSONObject plugins=load().getJSONObject("plugins");List<Plugin> result=new ArrayList<>();Iterator<String> ids=plugins.keys();
            List<String> ordered=new ArrayList<>();while(ids.hasNext())ordered.add(ids.next());java.util.Collections.sort(ordered);
            for(String id:ordered)result.add(new Plugin(plugins.getJSONObject(id)));
            return java.util.Collections.unmodifiableList(result);
        }
    }

    public void setEnabled(String id,boolean enabled)throws Exception {
        synchronized(LOCK) {
            JSONObject data=load(),record=require(data,id);
            if(enabled) {
                JSONObject previous=copy(record.getJSONObject("descriptor"));
                try {compile(record,record.getJSONObject("state"));CustomPluginSupport.requireCompatible(record.getJSONObject("descriptor"));CustomPluginSupport.requireExecutable(record.getJSONObject("descriptor"));record.put("enabled",true).put("error","");validateAggregate(data);}
                catch(Exception problem){record.put("descriptor",previous);quarantine(data,record,problem);throw problem;}
            } else record.put("enabled",false);
            save(data);
        }
    }
    public void setAllDisabled()throws Exception {
        synchronized(LOCK) {
            if(!database.exists())return;
            JSONObject data=load(),plugins=data.getJSONObject("plugins");Iterator<String> ids=plugins.keys();
            while(ids.hasNext())plugins.getJSONObject(ids.next()).put("enabled",false);
            save(data);
        }
    }
    public void updateState(String id,String key,Object value)throws Exception {
        synchronized(LOCK) {
            JSONObject data=load(),record=require(data,id),descriptor=record.getJSONObject("descriptor");
            JSONObject control=control(descriptor,key);if(!CustomPluginSchema.persistentControl(control))throw new IllegalArgumentException("未声明的插件状态键");
            CustomPluginSchema.stateValue(control,value);
            JSONObject state=copy(record.getJSONObject("state"));state.put(key,value);
            // The user's edit and its declared local feature gates are one saved transaction.
            // Imports, presets and resets keep explicit state; turning a switch off never enables parents.
            JSONArray enablers=control.optJSONArray("enableOnEdit");
            if(enablers!=null&&!Boolean.FALSE.equals(value))for(int n=0;n<enablers.length();n++) {
                String target=enablers.getString(n);JSONObject gate=control(descriptor,target);
                if(gate==null||!"switch".equals(gate.optString("type")))throw new IllegalArgumentException("编辑联动开关已失效");
                state.put(target,true);
            }

            JSONObject previous=copy(descriptor);
            try {compile(record,state);validateAggregate(data);}
            catch(Exception problem){record.put("descriptor",previous);quarantine(data,record,problem);throw problem;}
            record.put("state",stateFor(record.getJSONObject("descriptor"),state));save(data);
        }
    }
    public void updateStateVersion(String id,int version,String key,Object value)throws Exception{
        synchronized(LOCK){
            if(require(load(),id).getJSONObject("descriptor").getInt("versionCode")!=version)throw new IOException("插件设置版本已改变，请重新打开");
            updateState(id,key,value);
        }
    }
    /** An open editor cannot commit into a replaced version or a changed form field. */
    public void updateStateControl(String id,int version,JSONObject expected,Object value)throws Exception{
        synchronized(LOCK){
            JSONObject descriptor=require(load(),id).getJSONObject("descriptor");
            if(descriptor.getInt("versionCode")!=version)throw new IOException("插件设置版本已改变，请重新打开");
            if(expected==null)throw new IOException("插件设置已改变，请重新打开编辑");
            String key=expected.optString("key");JSONObject current=control(descriptor,key);
            if(!CustomPluginSchema.persistentControl(current)||!equivalent(expected,current))
                throw new IOException("插件设置已改变，请重新打开编辑");
            updateState(id,key,value);
        }
    }
    /** Apply a declared preset atomically, preserving all settings outside its explicit values. */
    public void applyPreset(String id,int version,String key)throws Exception{
        synchronized(LOCK){
            JSONObject data=load(),record=require(data,id),descriptor=record.getJSONObject("descriptor");
            if(descriptor.getInt("versionCode")!=version)throw new IOException("插件设置版本已改变，请重新打开");
            JSONObject preset=control(descriptor,key);
            if(preset==null||!"preset".equals(preset.optString("type")))throw new IllegalArgumentException("未声明的插件预设");
            JSONObject values=preset.getJSONObject("values"),state=copy(record.getJSONObject("state"));
            Iterator<String> keys=values.keys();
            while(keys.hasNext()){
                String target=keys.next();JSONObject field=control(descriptor,target);
                if(!CustomPluginSchema.persistentControl(field))throw new IllegalArgumentException("预设引用了无效设置");
                CustomPluginSchema.stateValue(field,values.get(target));state.put(target,values.get(target));
            }
            // Re-evaluation also rejects a state-dependent type/range change. Nothing is saved
            // on failure; unlike a failing runtime update, a rejected preset preserves enablement.
            compile(record,state);record.put("state",state).put("error","");
            validateAggregate(data);save(data);
        }
    }
    /** A configuration file contains only this plugin's declared values, never code or host preferences. */
    public String exportConfiguration(String id,int version)throws Exception{
        synchronized(LOCK){
            JSONObject record=require(load(),id),descriptor=record.getJSONObject("descriptor");
            requireConfigurationVersion(descriptor,version);
            return new JSONObject().put("format","c17-plugin-configuration").put("formatVersion",1)
                    .put("pluginId",id).put("pluginName",descriptor.getString("name"))
                    .put("pluginVersion",descriptor.getString("version")).put("pluginVersionCode",version)
                    .put("createdAt",System.currentTimeMillis()).put("state",copy(record.getJSONObject("state"))).toString(2);
        }
    }
    public static final class ConfigurationPreview {
        public final String id,name,version;
        public final int settingCount;
        private final int installedVersion;
        private final String json;
        private ConfigurationPreview(String id,JSONObject descriptor,String json,JSONObject state)throws Exception{
            this.id=id;name=descriptor.getString("name");version=new JSONObject(json).getString("pluginVersion");
            installedVersion=descriptor.getInt("versionCode");this.json=json;settingCount=state.length();
        }
    }
    /** Validation happens before showing a confirmation and again in the final atomic transaction. */
    public ConfigurationPreview previewConfiguration(String id,int version,InputStream input)throws Exception{
        String json=utf8(readBounded(input,4*1024*1024));
        synchronized(LOCK){
            JSONObject record=require(load(),id),descriptor=record.getJSONObject("descriptor");
            requireConfigurationVersion(descriptor,version);
            JSONObject values=configurationValues(id,descriptor,json);
            JSONObject checked=copy(record);compile(checked,mergeConfiguration(checked,values));
            return new ConfigurationPreview(id,descriptor,json,values);
        }
    }
    public void importConfiguration(ConfigurationPreview preview)throws Exception{
        if(preview==null)throw new IllegalArgumentException("尚未选择插件配置");
        synchronized(LOCK){
            JSONObject data=load(),record=require(data,preview.id),descriptor=record.getJSONObject("descriptor");
            requireConfigurationVersion(descriptor,preview.installedVersion);
            JSONObject values=configurationValues(preview.id,descriptor,preview.json),state=mergeConfiguration(record,values);
            compile(record,state);record.put("state",state).put("error","");
            validateAggregate(data);save(data);
        }
    }
    private static void requireConfigurationVersion(JSONObject descriptor,int version)throws Exception{
        if(descriptor.getInt("versionCode")!=version)throw new IOException("插件版本已改变，请重新打开配置管理");
    }
    private static JSONObject configurationValues(String id,JSONObject descriptor,String json)throws Exception{
        JSONObject file=new JSONObject(json);
        Set<String> fields=new HashSet<>(java.util.Arrays.asList("format","formatVersion","pluginId","pluginName","pluginVersion","pluginVersionCode","createdAt","state"));
        Iterator<String> declared=file.keys();while(declared.hasNext())if(!fields.contains(declared.next()))throw new IOException("插件配置文件包含不支持的内容");
        if(!"c17-plugin-configuration".equals(file.opt("format"))||!(file.opt("formatVersion") instanceof Number)
                ||file.getDouble("formatVersion")!=1d)throw new IOException("不是受支持的 C17 插件配置文件");
        if(!id.equals(file.opt("pluginId")))throw new IOException("所选配置不属于此插件");
        Object version=file.opt("pluginVersionCode");
        if(!(version instanceof Number)||!Double.isFinite(((Number)version).doubleValue())
                ||((Number)version).doubleValue()!=Math.rint(((Number)version).doubleValue())||((Number)version).longValue()<1)
            throw new IOException("插件配置版本无效");
        if(((Number)version).longValue()>descriptor.getInt("versionCode"))throw new IOException("配置来自更新版本的插件，请先更新插件");
        if(!(file.opt("pluginVersion") instanceof String))throw new IOException("插件配置缺少版本信息");
        JSONObject values=file.getJSONObject("state"),normalized=new JSONObject(),aliases=descriptor.optJSONObject("configurationAliases");
        boolean older=((Number)version).longValue()<descriptor.getInt("versionCode");
        Iterator<String> keys=values.keys();
        while(keys.hasNext()){
            String key=keys.next();JSONObject field=control(descriptor,key);
            if(!CustomPluginSchema.persistentControl(field)) {
                String target=older&&aliases!=null?aliases.optString(key,""):"";
                JSONObject destination=control(descriptor,target);
                if(!CustomPluginSchema.persistentControl(destination))throw new IOException("配置包含当前插件不支持的设置："+key);
                CustomPluginSchema.stateValue(destination,values.get(key));
                // Validate even a superseded alias; the canonical value alone wins conflicts.
                if(!values.has(target))normalized.put(target,values.get(key));
                continue;
            }
            CustomPluginSchema.stateValue(field,values.get(key));
            normalized.put(key,values.get(key));
        }
        return normalized;
    }
    private static JSONObject mergeConfiguration(JSONObject record,JSONObject values)throws Exception{
        JSONObject state=copy(record.getJSONObject("state"));Iterator<String> keys=values.keys();
        while(keys.hasNext()){String key=keys.next();state.put(key,values.get(key));}
        return state;
    }
    /** An order draft can only replace the exact enabled form value it was opened from. */
    public void updateStateCompared(String id,int version,String key,String baseValue,Object value)throws Exception{
        synchronized(LOCK){
            JSONObject record=require(load(),id),descriptor=record.getJSONObject("descriptor");
            if(!record.getBoolean("enabled"))throw new IOException("插件已停用，请重新打开排序");
            if(descriptor.getInt("versionCode")!=version)throw new IOException("插件版本已改变，请重新打开排序");
            JSONObject field=control(descriptor,key);
            if(field==null||!"sort".equals(field.optString("type")))throw new IOException("排序设置已改变，请重新打开");
            JSONObject state=record.getJSONObject("state");Object current=state.has(key)?state.opt(key):CustomPluginSchema.defaultValue(field);
            if(!(current instanceof String)||!current.equals(baseValue))throw new IOException("排序已在其他位置修改，请重新打开");
            updateState(id,key,value);
        }
    }
    public void updateStateSession(NativeSettingsSession session,String key,Object value)throws Exception{
        synchronized(LOCK){
            if(session==null||!session.valid||!database.getAbsolutePath().equals(session.owner))throw new IOException("插件设置界面已失效，请重新打开");
            updateStateVersion(session.id,session.version,key,value);
        }
    }
    /** Executed by the existing form worker; requests never become persisted configuration. */
    public JSONObject formStatus(String id)throws Exception{
        JSONObject descriptor;String generation;
        synchronized(LOCK){JSONObject record=require(load(),id);descriptor=copy(record.getJSONObject("descriptor"));generation=new File(record.optString("runtimeBaseDir")).getName();}
        if(!CustomPluginSupport.requires(descriptor,"scope.apps"))return awaitForm(CustomPluginAdmin.submitForm(requireContext(),id,null,null));
        JSONObject snapshot=new JSONObject().put("id",id).put("status",new JSONObject()).put("actions",new JSONArray());
        if(CustomPluginSupport.targets(descriptor,ModuleRuntimeStatus.SYSTEM_UI))try{snapshot=awaitForm(CustomPluginAdmin.submitForm(requireContext(),id,null,null));}
        catch(Exception unavailable){snapshot.put("systemUiUnavailable",true);}
        return CustomPluginAppProvider.withStatus(descriptor,generation,snapshot);
    }
    public JSONObject runAction(String id,String key,String instanceId)throws Exception{return awaitForm(CustomPluginAdmin.submitForm(requireContext(),id,key,instanceId));}
    private Context requireContext(){if(context==null)throw new IllegalStateException("Plugin form requires an application context");return context;}
    private JSONObject awaitForm(String requestId)throws Exception{
        boolean completed=false;long deadline=android.os.SystemClock.elapsedRealtime()+8000;
        try{
            do{
                Bundle answer=CustomPluginAdmin.takeFormResult(context,requestId);
                if(answer!=null){completed=true;if(answer.containsKey("error"))throw new IllegalStateException(answer.getString("error"));return new JSONObject(answer.getString("result"));}
                Thread.sleep(60);
            }while(android.os.SystemClock.elapsedRealtime()<deadline);
            throw new IllegalStateException("未收到插件操作结果，请确认插件正在运行后重试");
        }finally{if(!completed)CustomPluginAdmin.cancelForm(context,requestId);}
    }
    /** Restore this plugin's complete form in one transaction; user settings are never written. */
    public void resetState(String id)throws Exception {
        synchronized(LOCK) {
            JSONObject data=load(),record=require(data,id),previous=record.getJSONObject("descriptor");
            JSONObject state=new JSONObject(),descriptor=null;
            String source=record.getString("source");boolean stable=false;
            // Defaults may depend on other defaults. Bound the entire resolution instead of
            // saving each control separately or ever persisting a partially reset plan.
            for(int pass=0;pass<4;pass++) {
                descriptor=evaluate(source,state);
                if(!CustomPluginSchema.sameIdentity(previous,descriptor))throw new IOException("插件身份或能力声明不能随默认值改变");
                JSONObject defaults=stateFor(descriptor,new JSONObject());
                if(equivalent(state,defaults)){stable=true;break;}
                state=defaults;
            }
            if(!stable)throw new IOException("插件默认配置无法稳定生成，已保留原配置");
            JSONObject repeated=evaluate(source,state);
            if(!equivalent(descriptor,repeated))throw new IOException("插件默认配置不能重复生成，已保留原配置");
            record.put("descriptor",descriptor).put("state",state).put("error","");
            validateAggregate(data);save(data);
        }
    }
    public void delete(String id)throws Exception {
        synchronized(LOCK) {JSONObject data=load();require(data,id);data.getJSONObject("plugins").remove(id);save(data);invalidateNativeSettings(id);
            File root=new File(directory,"plugin_data"),assets=child(root,id);if(assets.exists())removeTree(assets,root);try{clearNativeSettingsCache(id);}catch(IOException retainedCache){/* Optional cache cleanup cannot reverse a committed deletion. */}}
    }

    /** App/provider read: validates persisted JSON, with no script execution. */
    public JSONObject compiledPlan()throws Exception {synchronized(LOCK){return aggregate(load());}}
    /** Each process receives only the plugins reviewed for its exact package. */
    JSONObject compiledPlan(String targetPackage)throws Exception {synchronized(LOCK){return aggregate(load(),targetPackage);}}
    /** Capture every boot scope at the same committed library revision. */
    CustomPluginFrameworkPublisher.Snapshot frameworkPlans(boolean safeMode)throws Exception {
        synchronized(LOCK) {
            JSONObject data=load(),plugins=data.getJSONObject("plugins");
            Set<String> targets=new java.util.TreeSet<>();
            targets.add(ModuleRuntimeStatus.SYSTEM_UI);
            Iterator<String> ids=plugins.keys();
            while(ids.hasNext()) {
                JSONArray scopes=CustomPluginSupport.scopes(plugins.getJSONObject(ids.next()).getJSONObject("descriptor"));
                for(int i=0;i<scopes.length();i++)targets.add(scopes.getString(i));
            }
            Map<String,JSONObject> plans=new LinkedHashMap<>();
            Map<String,File> assets=new LinkedHashMap<>();
            for(String target:targets) {
                JSONObject plan=aggregate(data,target).put("targetPackage",target).put("safeMode",safeMode);
                if(!ModuleRuntimeStatus.SYSTEM_UI.equals(target)) {
                    plan.remove("settings");
                    plan.remove("hooks");
                }
                JSONArray runtimes=plan.getJSONArray("runtimes");
                for(int i=0;i<runtimes.length();i++) {
                    JSONObject runtime=runtimes.getJSONObject(i);
                    JSONObject record=plugins.getJSONObject(runtime.getString("id"));
                    File root=new File(record.getString("runtimeBaseDir"));
                    JSONArray descriptors=runtime.getJSONArray("assets");
                    for(int j=0;j<descriptors.length();j++) {
                        JSONObject asset=descriptors.getJSONObject(j);
                        String file=CustomPluginFrameworkPublisher.ASSET_PREFIX+asset.getString("sha256");
                        File source=child(root,asset.getString("path"));
                        if(!source.isFile()||Files.isSymbolicLink(source.toPath())||source.length()!=asset.getLong("bytes"))
                            throw new IOException("插件资源已缺失或改变");
                        assets.putIfAbsent(file,source);
                        asset.put("frameworkFile",file);
                    }
                }
                plans.put(target,plan);
            }
            return new CustomPluginFrameworkPublisher.Snapshot(data.getLong("revision"),safeMode,plans,assets);
        }
    }
    public static JSONObject plan(Context context) {
        try{return new CustomPluginRepository(context).compiledPlan(ModuleRuntimeStatus.SYSTEM_UI);}
        catch(Exception failure){try{return new JSONObject().put("revision",0L).put("settings",new JSONObject()).put("hooks",new JSONArray()).put("runtimes",new JSONArray()).put("error","插件库读取失败，所有插件已暂停");}
            catch(Exception impossible){return new JSONObject();}}
    }

    private static JSONObject require(JSONObject data,String id)throws Exception {
        JSONObject record=find(data,id);if(record==null)throw new IOException("插件不存在或已删除");return record;
    }
    private static JSONObject find(JSONObject data,String id)throws Exception {return data.getJSONObject("plugins").optJSONObject(id);}
    private JSONObject evaluate(String source,JSONObject state)throws Exception {
        return context==null?CustomPluginEngine.evaluate(source,state):CustomPluginSandbox.evaluate(context,source,state);
    }
    private void compile(JSONObject record,JSONObject state)throws Exception {
        JSONObject previous=record.getJSONObject("descriptor");
        JSONObject descriptor=evaluate(record.getString("source"),state);
        if(!CustomPluginSchema.sameIdentity(previous,descriptor))throw new IOException("插件身份或能力声明不能随状态改变");
        JSONObject normalized=stateFor(descriptor,state);
        if(!equivalent(state,normalized))throw new IOException("插件控件类型、选项与默认值不能使已保存状态失效");
        record.put("descriptor",descriptor);
    }
    private void quarantine(JSONObject data,JSONObject record,Exception problem)throws Exception {
        String error=problem.getMessage();if(error==null||error.trim().isEmpty())error="插件执行失败";
        record.put("enabled",false).put("error",error.length()>512?error.substring(0,512):error);save(data);
    }
    private static JSONObject control(JSONObject descriptor,String key) {
        JSONArray ui=descriptor.optJSONArray("ui");if(ui==null)return null;
        for(int i=0;i<ui.length();i++){JSONObject item=ui.optJSONObject(i);if(item!=null&&key.equals(item.optString("key")))return item;}return null;
    }
    private static JSONObject stateFor(JSONObject descriptor,JSONObject old)throws Exception {
        JSONObject state=new JSONObject();JSONArray ui=descriptor.optJSONArray("ui");if(ui==null)return state;
        for(int i=0;i<ui.length();i++) {
            JSONObject item=ui.getJSONObject(i);if(!CustomPluginSchema.persistentControl(item))continue;
            String key=item.getString("key");Object value=old.has(key)?old.opt(key):CustomPluginSchema.defaultValue(item);
            // Legacy colour inputs accepted surrounding whitespace before parsing. Normalize
            // saved values on upgrade only; direct writes still use the strict colour contract.
            if(old.has(key)&&"color".equals(item.optString("type"))&&value instanceof String)value=((String)value).trim();
            // An upgrade may remove a choice or change a field type: use its new declared default.
            try{CustomPluginSchema.stateValue(item,value);}catch(IllegalArgumentException changed){value=CustomPluginSchema.defaultValue(item);CustomPluginSchema.stateValue(item,value);}
            state.put(key,value);
        }
        return state;
    }
    private static void validateAggregate(JSONObject data)throws Exception {aggregate(data);}
    private static JSONObject aggregate(JSONObject data)throws Exception {
        return aggregate(data,null);
    }
    private static JSONObject aggregate(JSONObject data,String targetPackage)throws Exception {
        JSONObject settings=new JSONObject();JSONArray hooks=new JSONArray(),runtimes=new JSONArray(),inventory=new JSONArray();JSONObject plugins=data.getJSONObject("plugins");Set<String> sites=new HashSet<>();Map<String,String> settingOwners=new LinkedHashMap<>();boolean runtimeApi=false;
        List<String> ordered=new ArrayList<>();Iterator<String> ids=plugins.keys();while(ids.hasNext())ordered.add(ids.next());java.util.Collections.sort(ordered);
        for(String id:ordered) {
            JSONObject record=plugins.getJSONObject(id);JSONObject declared=record.getJSONObject("descriptor");
            if(targetPackage!=null&&!CustomPluginSupport.targets(declared,targetPackage))continue;
            JSONObject runtimeOwner=declared.optJSONObject("runtime");
            JSONArray preferences=runtimeOwner==null?null:runtimeOwner.optJSONArray("preferences");
            inventory.put(new JSONObject().put("id",id).put("installedAt",record.optLong("installedAt",0L)).put("preferences",preferences==null?new JSONArray():new JSONArray(preferences.toString())));
            if(!record.getBoolean("enabled")||!record.optString("error","").isEmpty())continue;
            JSONObject descriptor=record.getJSONObject("descriptor");CustomPluginSchema.validate(descriptor);
            CustomPluginSupport.requireCompatible(descriptor);CustomPluginSupport.requireExecutable(descriptor);
            if(descriptor.optInt("apiVersion")==2) {
                runtimeApi=true;
                if(descriptor.has("runtime")){
                    JSONObject runtime=new JSONObject().put("id",id).put("versionCode",descriptor.getInt("versionCode"))
                            .put("source",record.getString("runtimeSource")).put("state",copy(record.getJSONObject("state"))).put("baseDir",record.getString("runtimeBaseDir"))
                            .put("assets",new JSONArray(record.getJSONArray("runtimeAssets").toString()));
                    JSONObject form=CustomPluginSchema.form(descriptor);
                    if(form.getJSONArray("actions").length()>0||form.getJSONArray("statuses").length()>0)runtime.put("form",form);
                    if(descriptor.has("scopes"))runtime.put("scopes",new JSONArray(descriptor.getJSONArray("scopes").toString()));
                    if(targetPackage!=null&&!ModuleRuntimeStatus.SYSTEM_UI.equals(targetPackage))
                        runtime.put("assetPackage",targetPackage).put("baseDir","").put("generation",new File(record.getString("runtimeBaseDir")).getName());
                    runtimes.put(runtime);
                }
            }
            JSONObject pluginSettings=descriptor.optJSONObject("settings");
            if(pluginSettings!=null) {Iterator<String> keys=pluginSettings.keys();while(keys.hasNext()){
                String key=keys.next(),canonical=QsCardSettings.GRID_6.equals(key)?QsCardSettings.GRID_ROWS:key;
                String previous=settingOwners.put(canonical,id);
                if(settings.has(key)||previous!=null&&!previous.equals(id))throw new IOException("插件设置冲突："+key+"，请先停用另一个插件");
                if(settings.length()>=512)throw new IOException("全部插件最多覆盖 512 项设置");
                settings.put(key,pluginSettings.opt(key));
            }}
            JSONArray rules=descriptor.optJSONArray("hooks");
            if(rules!=null)for(int i=0;i<rules.length();i++){
                if(hooks.length()>=64)throw new IOException("全部插件最多启用 64 条系统规则");
                JSONObject rule=rules.getJSONObject(i);
                String site=rule.getString("className")+"#"+rule.getString("method")+"/"+rule.getInt("argCount")+":"+rule.getString("kind")
                        +("property".equals(rule.getString("kind"))?":"+rule.getString("property"):"");
                if(!sites.add(site))throw new IOException("系统规则作用位置冲突："+site+"，请先停用另一个插件");
                hooks.put(copy(rule).put("pluginId",id));
            }
        }
        CustomPluginStorage.inventory(inventory);
        JSONObject plan=new JSONObject().put("revision",data.getLong("revision")).put("settings",settings).put("hooks",hooks).put("runtimes",runtimes).put("installedPlugins",inventory);
        if(!runtimeApi&&plan.toString().getBytes(StandardCharsets.UTF_8).length>CustomPluginEngine.MAX_JSON_BYTES)
            throw new IOException("全部插件的运行计划超过 128 KB，请先停用另一个插件");
        return plan;
    }

    private JSONObject load()throws Exception {
        if(!database.exists())return new JSONObject().put("format",1).put("revision",0L).put("plugins",new JSONObject());
        byte[] bytes;try(InputStream input=new FileInputStream(database)){bytes=readBounded(input,Integer.MAX_VALUE);}
        JSONObject data=new JSONObject(utf8(bytes));CustomPluginSchema.fields(data,"format","revision","plugins");
        if(data.getInt("format")!=1||data.getLong("revision")<0)throw new IOException("插件库版本或序号无效");
        JSONObject plugins=data.getJSONObject("plugins");if(legacyCount(plugins)>MAX_PLUGINS)throw new IOException("API 1 插件库数量过多");
        if(!hasApi2(plugins)&&bytes.length>MAX_DATABASE)throw new IOException("API 1 插件库总大小超过 8 MB");
        Iterator<String> ids=plugins.keys();while(ids.hasNext()) {
            String id=ids.next();JSONObject record=plugins.getJSONObject(id);CustomPluginSchema.fields(record,"source","descriptor","state","enabled","error","installedAt","updatedAt","origin","runtimeSource","runtimeBaseDir","runtimeAssets");
            for(String field:new String[]{"installedAt","updatedAt"})if(record.has(field)) {
                try {if(new java.math.BigDecimal(record.get(field).toString()).longValueExact()<0)throw new ArithmeticException();}
                catch(RuntimeException invalid){throw new IOException("插件时间记录无效");}
                if(!(record.get(field) instanceof Number))throw new IOException("插件时间记录类型无效");
            }
            if(record.has("origin"))CustomPluginSchema.text(record,"origin",2048,true);
            JSONObject descriptor=record.getJSONObject("descriptor");CustomPluginSchema.validate(descriptor);
            if(!id.equals(descriptor.getString("id")))throw new IOException("插件 ID 不一致");
            if(descriptor.optInt("apiVersion")==1&&record.getString("source").getBytes(StandardCharsets.UTF_8).length>CustomPluginEngine.MAX_SOURCE_BYTES)throw new IOException("已存脚本过大");
            if(descriptor.optInt("apiVersion")==2) {
                if(!(record.get("runtimeSource") instanceof String))throw new IOException("插件运行脚本类型无效");
                String version=Integer.toString(descriptor.getInt("versionCode"));
                String generation=new File(record.getString("runtimeBaseDir")).getName();
                // Keep old installations readable; replacements get isolated generations,
                // even when the user imports the same version or an older version.
                if(!generation.equals(version)&&!generation.matches(version+"-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))throw new IOException("插件资源目录无效");
                File expected=child(new File(directory,"plugin_data"),id+"/"+generation);
                if(!expected.getCanonicalPath().equals(record.getString("runtimeBaseDir")))throw new IOException("插件资源目录无效");
                JSONArray assets=record.getJSONArray("runtimeAssets");Set<String> paths=new HashSet<>();
                for(int index=0;index<assets.length();index++) {
                    JSONObject asset=assets.getJSONObject(index);String path=asset.getString("path");validPath(path);
                    if(!paths.add(path.toLowerCase(Locale.ROOT))||!asset.getString("sha256").matches("[a-f0-9]{64}")||asset.getLong("bytes")<0)throw new IOException("插件资源清单无效");
                    child(expected,path);
                }
            }
            record.getBoolean("enabled");JSONObject state=record.getJSONObject("state");
            if(!equivalent(state,stateFor(descriptor,state)))throw new IOException("已存插件状态无效");
        }
        return data;
    }
    private void save(JSONObject data)throws Exception {
        long previous=data.getLong("revision");if(previous==Long.MAX_VALUE)throw new IOException("插件库序号溢出");data.put("revision",previous+1L);
        byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
        if(!hasApi2(data.getJSONObject("plugins"))&&bytes.length>MAX_DATABASE)throw new IOException("API 1 插件库总大小超过 8 MB");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建插件私有目录");
        File temporary=File.createTempFile("library-",".tmp",directory);
        try {
            try(FileOutputStream output=new FileOutputStream(temporary)){output.write(bytes);output.getFD().sync();}
            Files.move(temporary.toPath(),database.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {if(temporary.exists()&&!temporary.delete())temporary.deleteOnExit();}
        SettingsFrameworkMirror.pluginsChanged(context);
        CustomPluginVisibility.reconcile(context,data.getJSONObject("plugins"));
        if(context!=null)try{context.getContentResolver().notifyChange(android.net.Uri.parse(StatusBarSettings.CONTENT_URI),null);}catch(Exception ignored){/* Storage committed; app refresh can retry provider notification. */}
    }

    /** Stream resources to a private preview. Runtime text is not evaluated during admission. */
    private static JSONObject extract(File archive,File assets)throws Exception {
        int expected=verifyZipStructure(archive);
        try(ZipFile zip=new ZipFile(archive,StandardCharsets.UTF_8)) {
            ZipEntry manifestEntry=zip.getEntry("plugin.json");if(manifestEntry==null||manifestEntry.isDirectory())throw new IOException("ZIP 根目录缺少 plugin.json");
            JSONObject manifest;try(InputStream input=zip.getInputStream(manifestEntry)){manifest=new JSONObject(utf8(readBounded(input,Integer.MAX_VALUE)));}
            CustomPluginSchema.manifest(manifest);boolean legacy=manifest.getInt("apiVersion")==1;
            if(legacy&&(archive.length()>MAX_PACKAGE||expected>32))throw new IOException("API 1 插件包超过 1 MB 或 32 项；完整执行插件请使用 API 2");
            Set<String> normalized=new HashSet<>();long total=0;int count=0;
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();count++;String name=entry.getName();boolean folder=entry.isDirectory();
                String checked=folder&&name.endsWith("/")?name.substring(0,name.length()-1):name;validPath(checked);
                if(!normalized.add(checked.toLowerCase(Locale.ROOT)))throw new IOException("ZIP 包含重复路径");
                File target=child(assets,checked);
                if(folder){if(!target.isDirectory()&&!target.mkdirs())throw new IOException("无法创建插件资源目录");continue;}
                if(legacy&&!(name.endsWith(".js")||name.equals("plugin.json")||name.endsWith(".md")||name.endsWith(".txt")||name.equals("LICENSE")))
                    throw new IOException("API 1 插件包只支持 JS、plugin.json 与说明文件");
                File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法创建插件资源目录");
                java.util.zip.CRC32 crc=new java.util.zip.CRC32();long size=0;
                try(InputStream input=zip.getInputStream(entry);FileOutputStream output=new FileOutputStream(target)) {
                    byte[] buffer=new byte[16384];int read;
                    while((read=input.read(buffer))!=-1){if(read==0)continue;size+=read;total+=read;
                        if(legacy&&(size>CustomPluginEngine.MAX_SOURCE_BYTES||total>MAX_PACKAGE))throw new IOException("API 1 插件资源超过大小限制");
                        crc.update(buffer,0,read);output.write(buffer,0,read);}
                    output.getFD().sync();
                }
                if(size!=entry.getSize()||crc.getValue()!=entry.getCrc())throw new IOException("ZIP 条目大小或 CRC 校验失败");
                if(legacy){utf8(Files.readAllBytes(target.toPath()));
                    if(entry.getCompressedSize()>0&&size>65536&&size/(double)entry.getCompressedSize()>200d)throw new IOException("ZIP 压缩比例过高");}
            }
            if(count!=expected)throw new IOException("ZIP 条目数量不一致");
            // Translation tables are display data only, but a broken one is refused here rather than half used later.
            CustomPluginLocalization.validate(assets);
            return manifest;
        }
    }
    /** Classic ZIP's 16/32-bit fields are a format boundary, not a configurable plugin quota. */
    private static int verifyZipStructure(File archive)throws IOException {
        try(RandomAccessFile file=new RandomAccessFile(archive,"r")) {
            long length=file.length(),end=-1;
            for(long cursor=length-22;cursor>=Math.max(0,length-65557);cursor--)
                if(u32(file,cursor)==0x06054b50L&&cursor+22+u16(file,cursor+20)==length){end=cursor;break;}
            if(end<0||u16(file,end+4)!=0||u16(file,end+6)!=0)throw new IOException("ZIP 不完整或不支持分卷");
            int count=u16(file,end+10);long size=u32(file,end+12),offset=u32(file,end+16);
            if(count==65535||size==0xffffffffL||offset==0xffffffffL)throw new IOException("当前导入器支持经典 ZIP，请将 ZIP64 重新打包为普通 ZIP");
            if(count==0||u16(file,end+8)!=count||offset+size!=end)throw new IOException("ZIP 目录无效");
            long cursor=offset;Set<Long> localOffsets=new HashSet<>();
            for(int i=0;i<count;i++) {
                if(cursor<0||cursor+46>end||u32(file,cursor)!=0x02014b50L)throw new IOException("ZIP 中央目录无效");
                int flags=u16(file,cursor+8),method=u16(file,cursor+10),nameLength=u16(file,cursor+28),extraLength=u16(file,cursor+30),commentLength=u16(file,cursor+32);
                long compressed=u32(file,cursor+20),plain=u32(file,cursor+24),local=u32(file,cursor+42),mode=u32(file,cursor+38)>>>16;
                if((flags&1)!=0||(method!=0&&method!=8)||u16(file,cursor+34)!=0||(mode&0xf000)==0xa000||local+30>offset||!localOffsets.add(local)
                        ||cursor+46L+nameLength+extraLength+commentLength>end)throw new IOException("ZIP 不支持加密、链接、重复偏移或无效目录");
                if(compressed==0xffffffffL||plain==0xffffffffL||local==0xffffffffL)throw new IOException("请将 ZIP64 重新打包为普通 ZIP");
                if(u32(file,local)!=0x04034b50L||u16(file,local+6)!=flags||u16(file,local+8)!=method||u16(file,local+26)!=nameLength)
                    throw new IOException("ZIP 本地文件头与目录不一致");
                int localExtra=u16(file,local+28);
                if(local+30L+nameLength+localExtra+compressed>offset)throw new IOException("ZIP 条目边界无效");
                byte[] localName=new byte[nameLength],centralName=new byte[nameLength];file.seek(local+30);file.readFully(localName);file.seek(cursor+46);file.readFully(centralName);
                if(!java.util.Arrays.equals(localName,centralName))throw new IOException("ZIP 本地文件名与目录不一致");
                cursor+=46+nameLength+extraLength+commentLength;
            }
            if(cursor!=end)throw new IOException("ZIP 目录长度无效");return count;
        }
    }
    private static int u16(RandomAccessFile file,long offset)throws IOException {if(offset<0||offset+2>file.length())throw new IOException("ZIP 文件头截断");file.seek(offset);return file.readUnsignedByte()|(file.readUnsignedByte()<<8);}
    private static long u32(RandomAccessFile file,long offset)throws IOException {return u16(file,offset)|((long)u16(file,offset+2)<<16);}
    private static void validPath(String path)throws IOException {
        if(path==null||path.isEmpty()||path.startsWith("/")||path.indexOf('\\')>=0||path.indexOf(':')>=0||path.indexOf('\0')>=0)throw new IOException("ZIP 路径无效");
        for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals("..")||part.endsWith(".")||part.endsWith(" "))throw new IOException("ZIP 路径无效");
    }
    private static void matchManifest(JSONObject manifest,JSONObject descriptor)throws IOException {
        for(String key:new String[]{"id","name","version","versionCode","apiVersion","description","author","capabilities","minimumHost","features","usage","scopes","nativeSettings","configurationAliases"}) {
            if(!manifest.has(key)&&("description".equals(key)||"author".equals(key)||"capabilities".equals(key)))continue;
            if(!equivalent(manifest.opt(key),descriptor.opt(key)))throw new IOException("plugin.json 与脚本声明不一致："+key);
        }
        if(manifest.has("runtime")&&!equivalent(manifest.opt("runtime"),descriptor.opt("runtime")))throw new IOException("plugin.json 与脚本声明不一致：runtime");
    }
    private static String resolveRuntime(JSONObject descriptor,File assets)throws Exception {
        JSONObject runtime=descriptor.optJSONObject("runtime");if(runtime==null)return "";
        if(runtime.has("source"))return runtime.getString("source");
        if(assets==null)throw new IOException("单 JS 插件必须在 runtime.source 中提供运行脚本");
        String entry=runtime.getString("entry");validPath(entry);File source=child(assets,entry);
        if(!entry.endsWith(".js")||!source.isFile())throw new IOException("缺少 runtime.entry 指定的 JS 运行脚本");
        return utf8(Files.readAllBytes(source.toPath()));
    }
    private File installAssets(String id,int version,File source)throws Exception {
        File root=new File(directory,"plugin_data"),parent=child(root,id),target=child(root,id+"/"+version+"-"+java.util.UUID.randomUUID());
        if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法创建插件资源目录");
        File staging=new File(parent,".install-"+UUID.randomUUID());if(!staging.mkdir())throw new IOException("无法创建插件资源暂存目录");
        try {
            if(source!=null)copyTree(source,staging,source);
            Files.move(staging.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);
            return target;
        } finally {if(staging.exists())removeTree(staging,root);}
    }
    private static final String GENERATION_ID="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";
    /** Directory names installAssets has ever produced: a generation, the earlier plain version, an unfinished staging copy. */
    private static final String RETIRED="[0-9]{1,10}(?:-"+GENERATION_ID+")?|\\.install-"+GENERATION_ID;
    /**
     * The saved library is the only pointer to a plugin's resources: providers and boot snapshots resolve
     * the record under LOCK, and settings code runs from a code-cache copy. Once it names {@code current},
     * this plugin's other generations have no reader. Names this library never creates are left alone, and
     * nothing here can fail the committed import; whatever cannot be removed is retried by the next one.
     */
    private void retireGenerations(String id,File current) {
        try {
            File root=new File(directory,"plugin_data");File[] entries=child(root,id).listFiles();if(entries==null)return;
            for(File entry:entries) {
                String name=entry.getName();
                if(current!=null&&name.equals(current.getName())||!name.matches(RETIRED)||!entry.isDirectory())continue;
                try{removeTree(entry,root);}catch(IOException retained){/* Linked or unreadable: left in place; the next import tries again. */}
            }
        } catch(IOException|RuntimeException retained){/* The import is already saved. */}
    }
    private static void copyTree(File source,File target,File root)throws IOException {
        if(Files.isSymbolicLink(source.toPath())||!source.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator)&&!source.getCanonicalFile().equals(root.getCanonicalFile()))
            throw new IOException("插件资源含有链接或越界路径");
        File[] files=source.listFiles();if(files==null)throw new IOException("无法读取插件资源目录");
        for(File file:files) {
            File destination=child(target,file.getName());
            if(file.isDirectory()){if(!destination.mkdir())throw new IOException("无法复制插件资源目录");copyTree(file,destination,root);}
            else {
                if(Files.isSymbolicLink(file.toPath()))throw new IOException("插件资源含有链接");
                try(InputStream input=new FileInputStream(file);FileOutputStream output=new FileOutputStream(destination)){transfer(input,output);output.getFD().sync();}
            }
        }
    }
    private static void transfer(InputStream input,java.io.OutputStream output)throws IOException {
        byte[] buffer=new byte[16384];int read;while((read=input.read(buffer))!=-1){if(read!=0)output.write(buffer,0,read);}
    }
    /** Every recursive deletion verifies the canonical target is a strict descendant first. */
    private static void removeTree(File target,File root)throws IOException {
        String base=root.getCanonicalPath(),path=target.getCanonicalPath();
        if(!path.startsWith(base+File.separator)||Files.isSymbolicLink(target.toPath()))throw new IOException("拒绝清理插件目录外的路径");
        if(target.isDirectory()){File[] children=target.listFiles();if(children==null)throw new IOException("无法读取待清理插件目录");for(File child:children)removeTree(child,root);}
        Files.deleteIfExists(target.toPath());
    }
    private static File child(File root,String path)throws IOException {
        validPath(path);File target=new File(root,path);
        if(!target.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("插件资源路径越界");return target;
    }
    private static JSONArray assetList(File root)throws Exception {JSONArray result=new JSONArray();appendAssets(root,root,result);return result;}
    private static void appendAssets(File root,File current,JSONArray result)throws Exception {
        File[] files=current.listFiles();if(files==null)throw new IOException("无法读取插件资源");java.util.Arrays.sort(files,java.util.Comparator.comparing(File::getName));
        for(File file:files) {
            if(Files.isSymbolicLink(file.toPath()))throw new IOException("插件资源不能包含链接");
            if(file.isDirectory()){appendAssets(root,file,result);continue;}
            String path=root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/');validPath(path);
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[16384];int read;while((read=input.read(buffer))!=-1)if(read!=0)digest.update(buffer,0,read);}
            StringBuilder sha=new StringBuilder();for(byte item:digest.digest())sha.append(String.format(Locale.ROOT,"%02x",item&255));
            result.put(new JSONObject().put("path",path).put("sha256",sha.toString()).put("bytes",file.length()));
        }
    }
    private static int legacyCount(JSONObject plugins)throws Exception {int count=0;Iterator<String> ids=plugins.keys();while(ids.hasNext())if(plugins.getJSONObject(ids.next()).getJSONObject("descriptor").optInt("apiVersion")==1)count++;return count;}
    private static boolean hasApi2(JSONObject plugins)throws Exception {return legacyCount(plugins)!=plugins.length();}
    /** Caller authentication belongs to SettingsProvider; this resolver only serves admitted assets. */
    public static File providerFile(Context context,android.net.Uri uri)throws Exception {
        return providerFile(context,uri,null);
    }
    static File providerFile(Context context,android.net.Uri uri,String targetPackage)throws Exception {
        CustomPluginRepository repository=new CustomPluginRepository(context);
        synchronized(LOCK) {
            List<String> parts=uri.getPathSegments();
            if(parts.size()<5||!"custom-plugins".equals(parts.get(0))||!"assets".equals(parts.get(1)))throw new IOException("插件资源 URI 无效");
            String id=parts.get(2),version=parts.get(3);JSONObject record=require(repository.load(),id),descriptor=record.getJSONObject("descriptor");
            if(targetPackage!=null&&!CustomPluginSupport.targets(descriptor,targetPackage))throw new IOException("插件未声明此应用作用域");
            if(!record.getBoolean("enabled")||!record.optString("error","").isEmpty()||!Integer.toString(descriptor.getInt("versionCode")).equals(version)||descriptor.optInt("apiVersion")!=2)
                throw new IOException("插件资源版本已失效或插件已停用");
            StringBuilder path=new StringBuilder();for(int index=4;index<parts.size();index++){if(index>4)path.append('/');path.append(parts.get(index));}
            String relative=path.toString();validPath(relative);boolean admitted=false;JSONArray assets=record.getJSONArray("runtimeAssets");
            for(int index=0;index<assets.length();index++)if(relative.equals(assets.getJSONObject(index).getString("path"))){admitted=true;break;}
            if(!admitted)throw new IOException("插件资源未登记");
            File file=child(new File(record.getString("runtimeBaseDir")),relative);if(!file.isFile()||Files.isSymbolicLink(file.toPath()))throw new IOException("插件资源不存在");return file;
        }
    }
    /** The internal settings Activity reads only the current admitted, hashed DEX asset. */
    public File nativeSettingsFile(String id,int version)throws Exception{
        synchronized(LOCK){
            JSONObject record=require(load(),id),descriptor=record.getJSONObject("descriptor");
            if(descriptor.getInt("versionCode")!=version||descriptor.optInt("apiVersion")!=2)throw new IOException("插件设置版本已改变，请重新打开");
            JSONObject entry=descriptor.optJSONObject("nativeSettings");if(entry==null)throw new IOException("插件未提供原生设置入口");
            String path=entry.getString("dex");File file=child(new File(record.getString("runtimeBaseDir")),path);
            if(!file.isFile()||Files.isSymbolicLink(file.toPath()))throw new IOException("插件设置代码不存在");
            JSONArray assets=record.getJSONArray("runtimeAssets");JSONObject admitted=null;
            for(int i=0;i<assets.length();i++)if(path.equals(assets.getJSONObject(i).getString("path")))admitted=assets.getJSONObject(i);
            if(admitted==null||file.length()!=admitted.getLong("bytes"))throw new IOException("插件设置代码与导入记录不一致");
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[16384];int read;while((read=input.read(buffer))!=-1)if(read!=0)digest.update(buffer,0,read);}
            StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",value&255));
            if(!hash.toString().equals(admitted.getString("sha256")))throw new IOException("插件设置代码校验失败，请重新导入");
            return file;
        }
    }
    private static byte[] readBounded(InputStream input,int limit)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int read;
        while((read=input.read(buffer))!=-1){if(read==0)continue;if(out.size()>limit-read)throw new IOException("插件文件超过大小限制");out.write(buffer,0,read);}return out.toByteArray();
    }
    private static String utf8(byte[] bytes)throws IOException {
        try {String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if(text.startsWith("\ufeff"))text=text.substring(1);if(text.indexOf('\0')>=0)throw new IOException("插件文本包含 NUL");return text;
        }catch(CharacterCodingException bad){throw new IOException("插件文件必须使用 UTF-8 编码",bad);}
    }
    private static JSONObject copy(JSONObject source)throws Exception{return new JSONObject(source.toString());}
    private static boolean equivalent(Object first,Object second) {
        if(first==second)return true;
        if(first instanceof JSONObject&&second instanceof JSONObject) {
            JSONObject a=(JSONObject)first,b=(JSONObject)second;if(a.length()!=b.length())return false;
            Iterator<String> keys=a.keys();while(keys.hasNext()){String key=keys.next();if(!b.has(key)||!equivalent(a.opt(key),b.opt(key)))return false;}return true;
        }
        if(first instanceof JSONArray&&second instanceof JSONArray) {
            JSONArray a=(JSONArray)first,b=(JSONArray)second;if(a.length()!=b.length())return false;
            for(int i=0;i<a.length();i++)if(!equivalent(a.opt(i),b.opt(i)))return false;return true;
        }
        if(first instanceof Number&&second instanceof Number)return Double.compare(((Number)first).doubleValue(),((Number)second).doubleValue())==0;
        return first!=null&&first.equals(second);
    }
}
