package dev.puitheme;

import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import io.github.libxposed.api.XposedInterface;

/** Exercise the real runtime with the existing isolated framework fixture. */
public final class CustomPluginRetentionCheck {
    static int checks;
    static void yes(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static Object call(Object object,String name,Class<?>[] types,Object...args)throws Exception{Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}
    static void plan(Object framework,JSONObject...entries)throws Exception{
        call(framework,"plan",new Class<?>[]{JSONObject[].class},(Object)entries);call(field(framework,"dispatcher"),"drain",new Class<?>[0]);
    }
    static JSONObject entry(Object framework,String id,String source)throws Exception{return(JSONObject)call(framework,"entry",new Class<?>[]{String.class,String.class},id,source);}
    public static void main(String[] args)throws Exception{
        Constructor<?> constructor=Class.forName("dev.puitheme.CustomPluginRuntimeCheck$Framework").getDeclaredConstructor();constructor.setAccessible(true);
        Object framework=constructor.newInstance();CustomPluginRuntime runtime=(CustomPluginRuntime)field(framework,"runtime");
        JSONObject entry=entry(framework,"retention","var calls=0;C17.onAction('catalog',function(){calls++;return {calls:calls,icon:new Array(180001).join('a')};});");
        entry.put("form",new JSONObject().put("actions",new JSONArray().put("catalog")).put("statuses",new JSONArray()));plan(framework,entry);
        String instance=new JSONObject(runtime.formStatusCommand("retention")).getString("instanceId"),first=UUID.randomUUID().toString(),latest=first;
        String initial=runtime.actionCommand("retention",instance,first,"catalog");
        yes(initial.equals(runtime.actionCommand("retention",instance,first,"catalog")),"duplicate returns original response without another callback");
        Object plugin=((Map<?,?>)field(runtime,"plugins")).get("retention");
        Map<?,?> journal=(Map<?,?>)field(plugin,"actionResults");
        for(int i=0;i<100;i++){
            latest=UUID.randomUUID().toString();runtime.actionCommand("retention",instance,latest,"catalog");
            long bytes=0;for(Object value:journal.values())bytes+=value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            yes(journal.size()<=8&&bytes<=1048576,"full catalog results obey both count and byte budgets");
        }
        boolean rejected=false;try{runtime.actionCommand("retention",instance,first,"catalog");}catch(IllegalStateException expected){rejected=true;}
        yes(rejected,"evicted response never replays its consumed callback");
        @SuppressWarnings("unchecked") Map<String,Long> times=(Map<String,Long>)field(plugin,"actionResultTimes");
        for(String id:new ArrayList<>(times.keySet()))times.put(id,System.nanoTime()-301000000000L);
        rejected=false;try{runtime.actionCommand("retention",instance,latest,"catalog");}catch(IllegalStateException expected){rejected=true;}
        yes(rejected&&journal.isEmpty(),"expired payloads are released but consumed requests remain blocked");
        @SuppressWarnings("unchecked") Map<String,String> consumed=(Map<String,String>)field(plugin,"consumedActions");
        while(consumed.size()<8192)consumed.put(UUID.randomUUID().toString(),"catalog");
        rejected=false;try{runtime.actionCommand("retention",instance,UUID.randomUUID().toString(),"catalog");}catch(IllegalStateException expected){rejected=true;}
        yes(rejected&&consumed.size()==8192,"journal exhaustion fails before callback rather than forgetting replay protection");
        plan(framework);yes(journal.isEmpty()&&consumed.isEmpty(),"plugin close releases result and replay journals");
        for(int i=0;i<100;i++){
            String source="var H=C17.findClass('dev.puitheme.CustomPluginRuntimeCheck$Host');var h=C17.hookNative(H.getDeclaredMethod('one'),H.getDeclaredMethod('callback').invoke(null,[]),true);h.unhook();h.unhook();";
            plan(framework,entry(framework,"unhooked"+i,source));
            yes(((List<?>)field(runtime,"retainedNativeHandles")).isEmpty(),"successful explicit unhook removes strong retained ownership");
        }
        final int[] attempts={0},removed={0};Executable executable=CustomPluginRetentionCheck.class.getMethod("main",String[].class);
        XposedInterface.HookHandle original=new XposedInterface.HookHandle(){
            public Executable getExecutable(){return executable;}
            public void unhook(){if(++attempts[0]==1)throw new IllegalStateException("transient unhook failure");}
        };
        CustomPluginRuntime.ManagedHandle handle=new CustomPluginRuntime.ManagedHandle(original,h->removed[0]++);
        try{handle.unhook();}catch(IllegalStateException expected){}
        yes(removed[0]==0&&field(handle,"original")!=null,"failed unhook retains the retry handle");
        handle.unhook();handle.unhook();
        yes(attempts[0]==2&&removed[0]==1&&field(handle,"original")==null&&field(handle,"onRemoved")==null,"success releases framework and callback references exactly once");
        yes(handle.getExecutable()==executable,"removed handle retains method metadata");runtime.stop();
        System.out.println("CustomPluginRetentionCheck "+checks+" checks passed");
    }
}
