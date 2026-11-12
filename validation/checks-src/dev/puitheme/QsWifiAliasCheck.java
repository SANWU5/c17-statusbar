// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import java.lang.reflect.Method;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import com.android.systemui.plugins.qs.QSTile;

/** Copies only the visible label; switching, rebinding, retirement and SSID privacy remain native. */
public final class QsWifiAliasCheck {
    private static int checks;
    public static final class State extends QSTile.BooleanState {
        public CharSequence label;public Object payload=new Object(),nativeCache=new Object();
        State(String name){spec="wifi";state=2;value=true;label=name;}
        public State copy(){State copy=new State(label.toString());copy.spec=spec;copy.state=state;copy.value=value;copy.payload=payload;copy.nativeCache=nativeCache;copy.icon=icon;return copy;}
    }
    private static final class Tile extends View {
        final QsWifiAlias helper;final List<Runnable> pending=new ArrayList<>();State shown;int deliveries;
        Tile(QsWifiAlias helper){super(new Context());this.helper=helper;}
        @Override public boolean post(Runnable event){pending.add(event);return true;}
        public void onStateChanged(State value){try{shown=(State)helper.displayState(this,value,Tile.class.getMethod("onStateChanged",State.class));deliveries++;}catch(Exception failure){throw new AssertionError(failure);}}
        public void setStateImmediately(State value){try{shown=(State)helper.displayState(this,value,Tile.class.getMethod("setStateImmediately",State.class));deliveries++;}catch(Exception failure){throw new AssertionError(failure);}}
        void drain(){while(!pending.isEmpty())pending.remove(0).run();}
    }
    public static final class Configuration {public String SSID,preSharedKey="SECRET_PASSWORD",BSSID="PRIVATE_ACCESS_POINT";Configuration(String ssid){SSID=ssid;}}
    public static final class Info {public String getSSID(){return "\"Home\"";}}
    public static final class Manager {
        public List<Configuration> getConfiguredNetworks(){return Arrays.asList(new Configuration("\"Home\""),new Configuration("\"Home\""),new Configuration("\"Office\""));}
        public Info getConnectionInfo(){return new Info();}
    }
    private static final class Storage extends Context {
        final Map<String,Object> values=new LinkedHashMap<>();final Manager manager=new Manager();
        final SharedPreferences preferences=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
            if(method.getName().equals("getString"))return values.getOrDefault(args[0],args[1]);
            if(method.getName().equals("getAll"))return new LinkedHashMap<>(values);
            if(method.getName().equals("edit")){
                Map<String,Object> pending=new LinkedHashMap<>();
                return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(editor,operation,parameters)->{
                    if(operation.getName().equals("putString")){pending.put((String)parameters[0],parameters[1]);return editor;}
                    if(operation.getName().equals("commit")){values.putAll(pending);return true;}
                    if(operation.getName().equals("apply")){values.putAll(pending);return null;}return editor;
                });
            }throw new UnsupportedOperationException(method.getName());
        });
        @Override public Context createDeviceProtectedStorageContext(){return this;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return preferences;}
        @Override public Object getSystemService(String name){return name.equals("wifi")?manager:null;}
    }
    public static void main(String[] args)throws Throwable{
        identitiesAndValidation();displayOnly();lifecycle();forwardedCopies();forwardedNativeChanges();settingsRefresh();catalog();schema();importsAndSafety();nativeHooksAndLateLoader();
        System.out.println("QsWifiAliasCheck "+checks+" checks passed");
    }
    private static Bundle settings(boolean enabled,String mode,String unified,String rules){Bundle out=new Bundle();out.putBoolean(QsWifiAlias.ENABLED,enabled);out.putString(QsWifiAlias.MODE,mode);out.putString(QsWifiAlias.UNIFIED,unified);out.putString(QsWifiAlias.RULES,rules);return out;}
    private static void identitiesAndValidation(){
        equal("Home",QsWifiAlias.ssid("\"Home\""),"platform quote normalization");equal(" Home ",QsWifiAlias.ssid("\" Home \""),"SSID spaces preserved");
        yes(!QsWifiAlias.ssid("HOME").equals(QsWifiAlias.ssid("Home")),"SSID case remains significant");
        for(String value:new String[]{null,"","<unknown ssid>","\"<unknown ssid>\"","abc\n","x".repeat(33)})equal(null,QsWifiAlias.ssid(value),"unknown or invalid SSID rejected");
        equal(null,QsWifiAlias.aliasError("🙂".repeat(32)),"32 code points accepted");yes(QsWifiAlias.aliasError("🙂".repeat(33))!=null,"alias character bound");
        yes(QsWifiAlias.aliasError("line\nbreak")!=null,"multiline alias rejected");yes(QsWifiAlias.aliasError(" ")!=null,"blank whitespace alias rejected");equal(null,QsWifiAlias.aliasError(""),"empty alias restores native");
        String rules=QsWifiAlias.withAlias("{}",QsWifiAlias.ssid("\"Home\""),"家里");equal("家里",QsWifiAlias.parse(rules).get("Home"),"canonical alias key");
        rules=QsWifiAlias.withAlias(rules,"Office","工作");equal(2,QsWifiAlias.parse(rules).size(),"independent networks coexist");
        rules=QsWifiAlias.withAlias(rules,"Home","");yes(!QsWifiAlias.parse(rules).containsKey("Home"),"clearing affects just one network");equal("工作",QsWifiAlias.parse(rules).get("Office"),"other alias retained");
        String quoted=QsWifiAlias.ssid("\"\"Home\"\"");equal("\"Home\"",quoted,"literal quotes survive platform decoding");
        String distinct=QsWifiAlias.withAlias(QsWifiAlias.withAlias("{}","Home","普通"),quoted,"带引号");
        equal(2,QsWifiAlias.parse(distinct).size(),"literal quote SSID stays distinct from unquoted network");equal("带引号",QsWifiAlias.parse(distinct).get(quoted),"literal quote SSID round trip");
        for(String json:new String[]{"[]","broken","{\"Home\":true}","{\"<unknown ssid>\":\"x\"}","{\"Home\":\"bad\\nname\"}"}){
            boolean rejected=false;try{QsWifiAlias.parse(json);}catch(IllegalArgumentException invalid){rejected=true;}yes(rejected,"malformed alias map rejected");
        }
    }
    private static void displayOnly(){
        QsWifiAlias helper=new QsWifiAlias();helper.currentNetwork("Home");State state=new State("Home");Object payload=state.payload,cache=state.nativeCache;
        same(state,helper.replacement(state),"default off keeps native state");String rules=QsWifiAlias.withAlias("{}","Home","家里");
        helper.configure(settings(true,"per_network","",rules));State shown=(State)helper.replacement(state);yes(shown!=state,"display receives independent state copy");
        equal("家里",shown.label,"connected network alias");equal("Home",state.label,"shared native label retained");same(payload,shown.payload,"payload reference retained");same(cache,shown.nativeCache,"cache reference retained");
        equal(state.value,shown.value,"connectivity unchanged");equal(state.state,shown.state,"native state unchanged");
        helper.currentNetwork("Office");same(state,helper.replacement(state),"late old-network state remains native");State office=new State("Office");same(office,helper.replacement(office),"unconfigured network remains native");
        helper.configure(settings(true,"unified","我的网络",rules));equal("我的网络",((State)helper.replacement(office)).label,"uniform mode for identified current network");
        office.value=false;same(office,helper.replacement(office),"disconnected native state retained");office.value=true;office.state=1;same(office,helper.replacement(office),"inactive native state retained");
        office.state=2;office.spec="cell";same(office,helper.replacement(office),"non-WiFi state excluded");office.spec="wifi";helper.currentNetwork("<unknown ssid>");same(office,helper.replacement(office),"unknown network excluded even in uniform mode");
        helper.currentNetwork("Office");Bundle safe=settings(true,"unified","自定义",rules);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);helper.configure(safe);same(office,helper.replacement(office),"safety mode retains native");
        helper.configure(settings(true,"unknown","别名",rules));same(office,helper.replacement(office),"unknown mode retains native");
        String quoted="\"Home\"";helper.currentNetwork(quoted);helper.configure(settings(true,"per_network","",QsWifiAlias.withAlias("{}",quoted,"引号网络")));
        equal("引号网络",((State)helper.replacement(new State(quoted))).label,"literal quoted SSID is not normalized twice during display");
    }
    private static void lifecycle(){
        QsWifiAlias helper=new QsWifiAlias();Tile tile=new Tile(helper);State nativeState=new State("Home");helper.currentNetwork("Home");
        helper.configure(settings(true,"unified","统一名称","{}"));tile.onStateChanged(nativeState);equal("统一名称",tile.shown.label,"initial native binding copied");
        helper.configure(settings(false,"unified","统一名称","{}"));tile.drain();equal("Home",tile.shown.label,"turning off redraws native label");
        helper.configure(settings(true,"per_network","",QsWifiAlias.withAlias("{}","Home","家里")));tile.drain();equal("家里",tile.shown.label,"per-network edit redraws existing native view");
        helper.currentNetwork("Office");tile.drain();equal("Home",tile.shown.label,"network switch does not assign new alias to stale state");
        tile.onStateChanged(new State("Office"));helper.configure(settings(true,"unified","办公网络","{}"));tile.drain();equal("办公网络",tile.shown.label,"new native network state gets new display");
        helper.release();tile.drain();equal("Office",tile.shown.label,"release restores native label after queued handoff");
        helper.configure(settings(true,"unified","再设置","{}"));helper.currentNetwork("Office");tile.onStateChanged(new State("Office"));
        helper.configure(settings(false,"unified","再设置","{}"));State cell=new State("移动数据");cell.spec="cell";tile.onStateChanged(cell);tile.drain();same(cell,tile.shown,"recycled native view cancels stale WiFi refresh");
        helper.configure(settings(true,"unified","临时","{}"));tile.onStateChanged(new State("Office"));int before=tile.deliveries;helper.configure(settings(false,"unified","","{}"));helper.detached(tile);tile.drain();equal(before,tile.deliveries,"detached view cancels queued refresh");
    }
    private static void forwardedCopies(){
        QsWifiAlias helper=new QsWifiAlias();Tile tile=new Tile(helper);State nativeState=new State("Home");helper.currentNetwork("Home");
        helper.configure(settings(true,"unified","旧名称","{}"));tile.onStateChanged(nativeState);State forwarded=tile.shown;
        tile.setStateImmediately(forwarded);equal("旧名称",tile.shown.label,"native second display entry accepts alias copy");
        helper.configure(settings(true,"unified","新名称","{}"));tile.drain();equal("新名称",tile.shown.label,"forwarded display copy never becomes the native baseline");
        helper.configure(settings(false,"unified","新名称","{}"));tile.drain();equal("Home",tile.shown.label,"turning off after native forwarding restores real name");
        equal("Home",nativeState.label,"native original remains unchanged after forwarding");equal("旧名称",forwarded.label,"previous display copy is never mutated");
        helper.configure(settings(true,"unified","再改名","{}"));tile.drain();State asynchronous=tile.shown;
        tile.setStateImmediately(asynchronous);helper.configure(settings(true,"unified","最新名称","{}"));tile.drain();equal("最新名称",tile.shown.label,"queued refresh display copy retains native provenance");
        State replacement=new State("Home");tile.onStateChanged(replacement);same(replacement.payload,tile.shown.payload,"new native event updates payload rather than reviving old baseline");
        helper.release();tile.drain();equal("Home",tile.shown.label,"release restores native after two-entry handoff");
        tile.setStateImmediately(asynchronous);equal("Home",tile.shown.label,"late owned display copy after release cannot restore stale alias");
    }
    private static void forwardedNativeChanges(){
        QsWifiAlias helper=new QsWifiAlias();Tile tile=new Tile(helper);helper.currentNetwork("Home");helper.configure(settings(true,"unified","自定义","{}"));
        tile.onStateChanged(new State("Home"));State forwarded=tile.shown;Object payload=new Object();forwarded.payload=payload;forwarded.value=false;forwarded.state=1;
        tile.setStateImmediately(forwarded);equal(false,tile.shown.value,"forwarded display retains subsequent native disconnect flag");equal(1,tile.shown.state,"forwarded display retains native state code");
        equal("Home",tile.shown.label,"disconnected forwarded copy restores only original label");same(payload,tile.shown.payload,"forwarded display retains latest native payload");equal("自定义",forwarded.label,"label restoration never mutates forwarded input");
        tile.onStateChanged(new State("Home"));State renamed=tile.shown;renamed.label="Office";helper.currentNetwork("Office");
        tile.setStateImmediately(renamed);equal("自定义",tile.shown.label,"new native label on a previously owned copy becomes the latest baseline");
        helper.configure(settings(false,"unified","自定义","{}"));tile.drain();equal("Office",tile.shown.label,"disable restores updated native label rather than old connection");
    }
    private static void settingsRefresh(){
        QsWifiAlias helper=new QsWifiAlias();Tile tile=new Tile(helper);helper.currentNetwork("Home");
        Bundle config=settings(true,"per_network","",QsWifiAlias.withAlias("{}","Home","家里"));helper.configure(config);tile.onStateChanged(new State("Home"));
        for(String unreadable:new String[]{null,"","<unknown ssid>"}){
            helper.configure(config);helper.seedNetwork(unreadable);tile.drain();equal("家里",tile.shown.label,"settings-only lookup failure preserves identified native connection");
            tile.onStateChanged(new State("Home"));equal("家里",tile.shown.label,"later native refresh still applies alias after settings reload");
        }
        helper.currentNetwork(null);tile.drain();equal("Home",tile.shown.label,"authoritative native disconnect still clears alias");
        State disconnected=new State("Home");disconnected.value=false;tile.onStateChanged(disconnected);
        helper.seedNetwork("Home");tile.drain();equal("Home",tile.shown.label,"seed cannot relabel native disconnected tile");
        QsWifiAlias fresh=new QsWifiAlias();fresh.configure(config);fresh.seedNetwork("Home");
        equal("家里",((State)fresh.replacement(new State("Home"))).label,"cold settings load seeds known connected network without status-bar visibility");
        fresh.seedNetwork("Office");State home=new State("Home");same(home,fresh.replacement(home),"changed network seed cannot label stale native network");
    }
    private static void catalog()throws Exception{
        Storage storage=new Storage();equal("Home",QsWifiAliasCatalog.connected(storage),"actual WifiInfo current SSID read");
        equal(Arrays.asList("Home","Office"),QsWifiAliasCatalog.read(storage),"saved config names deduplicated across access points");
        Bundle report=new Bundle();report.putString("current","Cafe");report.putStringArrayList("names",new ArrayList<>(Arrays.asList("Home","Cafe","<unknown ssid>")));
        yes(QsWifiAliasCatalog.record(storage,report).getBoolean("ok"),"observed-name report persisted");equal("Cafe",QsWifiAliasCatalog.current(storage),"identified current connection available to editor");
        yes(QsWifiAliasCatalog.read(storage).containsAll(Arrays.asList("Home","Office","Cafe")),"saved and observed names combined");
        yes(!storage.values.toString().contains("SECRET_PASSWORD")&&!storage.values.toString().contains("PRIVATE_ACCESS_POINT"),"password and BSSID never persisted");
        report.putString("current","");QsWifiAliasCatalog.record(storage,report);equal("",QsWifiAliasCatalog.current(storage),"disconnect clears current marker");
        ArrayList<String> many=new ArrayList<>();for(int i=0;i<140;i++)many.add("network-"+i);
        report.putStringArrayList("names",many);report.putString("current","Home");QsWifiAliasCatalog.record(storage,report);
        equal(128,new org.json.JSONArray((String)storage.values.get("names")).length(),"stored observed-name history is bounded");
        yes(QsWifiAliasCatalog.read(storage).contains("Home"),"current network survives bounded-history eviction");
    }
    private static void schema(){
        equal(false,StatusBarSettings.BOOLEAN_DEFAULTS.get(QsWifiAlias.ENABLED),"schema default is off");equal("{}",StatusBarSettings.STRING_DEFAULTS.get(QsWifiAlias.RULES),"schema native aliases by default");
        equal(QsWifiAlias.GROUP,SettingsCatalog.item(QsWifiAlias.RULES).groupId,"settings descriptor has dedicated page");
        long pages=SettingsCatalog.navigationSections(SettingsCatalog.CONTROL_CENTER).stream().flatMap(section->section.groups.stream()).filter(group->QsWifiAlias.GROUP.equals(group.id)).count();equal(1L,pages,"one control-center destination");
    }
    private static void importsAndSafety()throws Exception{
        String rules=QsWifiAlias.withAlias("{}","Home","家里");Map<String,Object> stored=new LinkedHashMap<>();
        stored.put(QsWifiAlias.ENABLED,true);stored.put(QsWifiAlias.MODE,"per_network");stored.put(QsWifiAlias.RULES,rules);stored.put(StatusBarSettings.SAFE_MODE,true);
        Bundle saved=SettingsSnapshot.fromPreferences(stored),runtime=SafetyMode.runtimeSettings(saved);
        equal(false,runtime.getBoolean(QsWifiAlias.ENABLED),"runtime safety disables aliases");equal(true,saved.getBoolean(QsWifiAlias.ENABLED),"safety leaves saved opt-in intact");equal(rules,saved.getString(QsWifiAlias.RULES),"safety retains saved map");
        equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(QsWifiAlias.RULES),"rules participate in normal configuration transfer");
        for(String value:new String[]{"bad_mode","per_network","unified"}){
            String document="{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{\""+QsWifiAlias.MODE+"\":\""+value+"\"}}";
            boolean accepted;try{ConfigTransfer.prepare(document,true);accepted=true;}catch(Exception invalid){accepted=false;}
            equal(!value.equals("bad_mode"),accepted,"import validates display mode");
        }
        Map<String,String> oversized=new LinkedHashMap<>();for(int i=0;i<129;i++)oversized.put("network-"+i,"name");
        boolean rejected=false;try{QsWifiAlias.parse(new org.json.JSONObject(oversized).toString());}catch(IllegalArgumentException invalid){rejected=true;}yes(rejected,"alias map bound checked independently of editor");
        yes(ConfigScope.section(QsWifiAlias.GROUP).keys().containsAll(Arrays.asList(QsWifiAlias.ENABLED,QsWifiAlias.MODE,QsWifiAlias.UNIFIED,QsWifiAlias.RULES)),"new aliases have one independent share scope");
    }
    public static class NativeWifi {
        public String mCurConnectedSSID="Home";public boolean connected=true;public int updates;
        public void handleUpdateState(QSTile.BooleanState state,Object ignored){updates++;state.state=connected?2:1;state.value=connected;((State)state).label=connected?mCurConnectedSSID:"Wi-Fi";}
    }
    public static class NativeNormal extends View {
        public State shown;
        public NativeNormal(){super(new Context());}
        public void onStateChanged(State state){shown=state;}
        public void setStateImmediately(State state){shown=state;}
        @Override public void onDetachedFromWindow(){}
    }
    public static class NativeEditable extends NativeNormal {
        @Override public void onStateChanged(State state){shown=state;}
        @Override public void setStateImmediately(State state){shown=state;}
        @Override public void onDetachedFromWindow(){}
    }
    public static class NativeFactory {
        public android.content.pm.ApplicationInfo pluginAppInfo=new android.content.pm.ApplicationInfo();final ClassLoader next;
        NativeFactory(ClassLoader next){this.next=next;pluginAppInfo.packageName="com.oplus.systemui.plugins";}
        public ClassLoader createClassLoader(){return next;}
    }
    public static class NativeCachedFactory {
        final ClassLoader next;NativeCachedFactory(ClassLoader next){this.next=next;}
        public static ClassLoader access$getCacheClassLoader(NativeCachedFactory owner,String name){return owner.next;}
    }
    /** Map the copy-capable State contract locally; producer BooleanState keeps its native name. */
    private static final class NativeLoader extends ClassLoader {
        final boolean editable;NativeLoader(boolean editable){super(QsWifiAliasCheck.class.getClassLoader());this.editable=editable;}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            switch(name){
                case "com.android.systemui.plugins.qs.QSTile$State":case "com.android.systemui.plugins.qs.QSTile$BooleanState":return State.class;
                case "com.oplus.systemui.qs.base.tile.OplusQSHighlightTileView":return NativeNormal.class;
                case "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView":if(editable)return NativeEditable.class;break;
                case "com.oplus.systemui.qs.tiles.WifiTile":return NativeWifi.class;
                case "com.android.systemui.shared.plugins.PluginInstance$PluginFactory":return NativeFactory.class;
                case "com.android.systemui.shared.plugins.PluginInstance$Factory":return NativeCachedFactory.class;
            }
            throw new ClassNotFoundException("Deliberately absent contract: "+name);
        }
    }
    private static final class NativeFramework {
        final Map<Executable,XposedInterface.Hooker> hooks=new LinkedHashMap<>();int rejectedCopies;
        final XposedInterface api=(XposedInterface)Proxy.newProxyInstance(QsWifiAliasCheck.class.getClassLoader(),new Class<?>[]{XposedInterface.class},(self,event,args)->{
            if(event.getName().equals("deoptimize"))return true;if(event.getName().equals("log"))return null;
            if(event.getName().equals("hook")){
                Executable executable=(Executable)args[0];return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.HookBuilder.class},(builder,operation,parameters)->{
                    if(operation.getName().equals("setPriority")||operation.getName().equals("setExceptionMode"))return builder;
                    if(operation.getName().equals("intercept")){
                        if(executable.getName().equals("copy")&&rejectedCopies>0){rejectedCopies--;throw new IllegalStateException("Controlled optional copy hook failure");}
                        yes(hooks.put(executable,(XposedInterface.Hooker)parameters[0])==null,"production installHook deduplicates Executables");
                        return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.HookHandle.class},(handle,method,values)->method.getName().equals("getExecutable")?executable:null);
                    }throw new AssertionError(operation.getName());
                });
            }throw new AssertionError("Unexpected framework call "+event.getName());
        });
        Object fire(Method method,Object receiver,Object...arguments)throws Throwable{
            XposedInterface.Hooker hook=hooks.get(method);yes(hook!=null,"production installer captured "+method);int[] proceeds={0};
            XposedInterface.Chain chain=(XposedInterface.Chain)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.Chain.class},(proxy,event,args)->{
                switch(event.getName()){
                    case "getExecutable":return method;case "getThisObject":return receiver;case "getArgs":return Arrays.asList(arguments);case "getArg":return arguments[(Integer)args[0]];
                    case "proceed":proceeds[0]++;Object[] actual=args==null||args.length==0?arguments:(Object[])args[0];
                        try{return method.invoke(receiver,actual);}catch(InvocationTargetException failure){throw failure.getCause();}
                    default:throw new AssertionError(event.getName());
                }
            });
            try{return hook.intercept(chain);}finally{equal(1,proceeds[0],"production hook proceeds exactly once");}
        }
    }
    private static Object moduleField(String name)throws Exception{Field field=StatusBarModule.class.getDeclaredField(name);field.setAccessible(true);return field.get(null);}
    private static Object install(StatusBarModule module,String name,ClassLoader loader)throws Throwable{
        Method method=StatusBarModule.class.getDeclaredMethod(name,ClassLoader.class);method.setAccessible(true);
        try{return method.invoke(module,loader);}catch(InvocationTargetException failure){throw failure.getCause();}
    }
    @SuppressWarnings("unchecked")
    private static void nativeHooksAndLateLoader()throws Throwable{
        NativeFramework framework=new NativeFramework();StatusBarModule module=new StatusBarModule();module.attachFramework(framework.api);
        NativeLoader initial=new NativeLoader(false),late=new NativeLoader(true);
        Map<Object,Set<Executable>> installed=(Map<Object,Set<Executable>>)moduleField("INSTALLED_HOOKS"),savedInstalled=new LinkedHashMap<>();
        installed.forEach((key,value)->savedInstalled.put(key,new HashSet<>(value)));
        Map<Object,Integer> counts=(Map<Object,Integer>)moduleField("HOOK_COUNTS"),savedCounts=new LinkedHashMap<>(counts);
        Set<ClassLoader> fluid=(Set<ClassLoader>)moduleField("FLUID_LOADERS"),savedFluid=new HashSet<>(fluid);
        Set<Object> completed=(Set<Object>)moduleField("COMPLETED_HOOK_GROUPS"),savedCompleted=new HashSet<>(completed);
        QsWifiAlias helper=(QsWifiAlias)moduleField("WIFI_ALIAS");
        try{
            framework.rejectedCopies=2;equal(false,install(module,"installWifiAlias",initial),"normal loader stays incomplete without editable entries");
            yes(!framework.hooks.containsKey(State.class.getMethod("copy")),"optional copy failure does not block native display and producer hooks");
            Method normal=NativeNormal.class.getMethod("onStateChanged",State.class);yes(framework.hooks.containsKey(normal),"real normal hook installed");
            int first=framework.hooks.size();equal(false,install(module,"installWifiAlias",initial),"partial retry");equal(first+1,framework.hooks.size(),"partial retry adds only recovered optional copy hook");
            int recovered=framework.hooks.size();equal(false,install(module,"installWifiAlias",initial),"recovered partial retry");equal(recovered,framework.hooks.size(),"inherited copy and existing display hooks never duplicate");
            helper.configure(settings(true,"per_network","",QsWifiAlias.withAlias("{}","Home","家里")));
            NativeWifi producer=new NativeWifi();State nativeState=new State("Home");Method update=NativeWifi.class.getMethod("handleUpdateState",QSTile.BooleanState.class,Object.class);
            framework.fire(update,producer,nativeState,null);NativeNormal tile=new NativeNormal();framework.fire(normal,tile,nativeState);
            equal("家里",((State)tile.shown).label,"actual producer and display hooks apply alias");equal("Home",nativeState.label,"actual hook preserves shared native label");equal(1,producer.updates,"native producer executes once");
            Method copy=State.class.getMethod("copy");State cloned=(State)framework.fire(copy,tile.shown);
            yes(cloned!=tile.shown,"native copy hook preserves independent copy");equal("家里",cloned.label,"native copy result retains display label");
            framework.fire(NativeNormal.class.getMethod("setStateImmediately",State.class),tile,cloned);
            helper.configure(settings(true,"unified","更新名称","{}"));equal("更新名称",tile.shown.label,"native cloned display forwarded to second entry retains original name");
            helper.configure(settings(false,"unified","更新名称","{}"));equal("Home",tile.shown.label,"disable restores native name after native copy and second entry");
            helper.configure(settings(true,"per_network","",QsWifiAlias.withAlias("{}","Home","家里")));framework.fire(normal,tile,nativeState);
            State unowned=new State("家里");State untouched=(State)framework.fire(copy,unowned);NativeNormal unownedTile=new NativeNormal();framework.fire(normal,unownedTile,untouched);
            equal("家里",untouched.label,"copy hook never rewrites an unowned native state by alias text");equal("Home",nativeState.label,"native original remains unchanged after copy propagation");
            helper.configure(settings(false,"unified","","{}"));equal("家里",unownedTile.shown.label,"disable retains unowned native label even when it equals another network alias");
            helper.configure(settings(true,"per_network","",QsWifiAlias.withAlias("{}","Home","家里")));
            fluid.add(initial);yes((Boolean)install(module,"installFluidAccentLoaders",initial),"existing native plugin loader callbacks installed");
            Method editable=NativeEditable.class.getMethod("onStateChanged",State.class);yes(!framework.hooks.containsKey(editable),"editable hook absent before callback");
            fluid.add(late);Method create=NativeFactory.class.getMethod("createClassLoader");
            same(late,framework.fire(create,new NativeFactory(late)),"initial loader callback preserves native result");
            yes(framework.hooks.containsKey(editable),"actual plugin loader callback installs late editable entry before completed-loader return");
            int complete=framework.hooks.size();framework.fire(create,new NativeFactory(late));equal(complete,framework.hooks.size(),"repeated loader callback is deduplicated");
            Method cached=NativeCachedFactory.class.getMethod("access$getCacheClassLoader",NativeCachedFactory.class,String.class);
            same(late,framework.fire(cached,null,new NativeCachedFactory(late),"com.oplus.systemui.plugins"),"cached loader callback preserves native result");
            equal(complete,framework.hooks.size(),"cached loader callback is deduplicated");NativeEditable lateTile=new NativeEditable();framework.fire(editable,lateTile,nativeState);
            equal("家里",((State)lateTile.shown).label,"late editable hook actually runs");
            helper.seedNetwork(null);framework.fire(normal,tile,nativeState);equal("家里",((State)tile.shown).label,"later native hook keeps alias after unreadable settings seed");
            producer.connected=false;framework.fire(update,producer,nativeState,null);framework.fire(normal,tile,nativeState);equal("Wi-Fi",((State)tile.shown).label,"native disconnect through actual hook restores native label");
        }finally{
            helper.release();installed.clear();installed.putAll(savedInstalled);counts.clear();counts.putAll(savedCounts);fluid.clear();fluid.addAll(savedFluid);completed.clear();completed.addAll(savedCompleted);
        }
    }
    private static void yes(boolean value,String why){checks++;if(!value)throw new AssertionError("WifiAlias "+checks+": "+why);}
    private static void equal(Object expected,Object actual,String why){yes(java.util.Objects.equals(expected,actual),why+": "+expected+" != "+actual);}
    private static void same(Object expected,Object actual,String why){yes(expected==actual,why);}
}
