// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.IdentityHashMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/** App-side JavaScript to native JSON compiler. No Java objects, Android bridge, IO or timers. */
public final class CustomPluginEngine {
    public static final int MAX_SOURCE_BYTES = 256 * 1024;
    public static final int MAX_JSON_BYTES = 128 * 1024;
    private static final int MAX_NODES = 4096;
    private CustomPluginEngine() { }

    /** This method must never be called from a SystemUI hook. */
    public static JSONObject evaluate(String source, JSONObject state) throws Exception {
        if (source == null) throw new IllegalArgumentException("缺少插件脚本");
        final BudgetFactory factory = new BudgetFactory();
        try {
            JSONObject result = factory.call(cx -> {
                ScriptableObject scope = cx.initSafeStandardObjects(null, false);
                // initSafeStandardObjects excludes LiveConnect; remove optional historical aliases too.
                for (String name : new String[]{"Packages","java","javax","org","com","edu","net",
                        "JavaAdapter","JavaImporter","getClass","load","loadClass","importClass","importPackage"})
                    ScriptableObject.deleteProperty(scope, name);
                NativeObject api = new NativeObject();
                api.setParentScope(scope);
                api.setPrototype(ScriptableObject.getObjectPrototype(scope));
                final Object[] declared = new Object[1];
                BaseFunction define = new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope)) {
                    @Override public Object call(Context context, Scriptable parent, Scriptable self, Object[] args) {
                        if (declared[0] != null || args.length != 1 || !(args[0] instanceof NativeObject))
                            throw new IllegalArgumentException("脚本必须且只能调用一次 C17.define({...})");
                        declared[0] = args[0]; return Undefined.instance;
                    }
                };
                api.defineProperty("define", define, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
                api.defineProperty("state", toScript(cx, scope, state == null ? new JSONObject() : state),
                        ScriptableObject.READONLY | ScriptableObject.PERMANENT);
                api.sealObject();
                scope.defineProperty("C17", api, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
                cx.evaluateString(scope, source, "custom-plugin.js", 1, null);
                if (declared[0] == null) throw new IllegalArgumentException("脚本没有调用 C17.define({...})");
                NativeObject declaration=(NativeObject)declared[0];
                Object apiVersion=declaration.get("apiVersion",declaration);
                boolean runtimeApi=apiVersion instanceof Number&&((Number)apiVersion).intValue()==2;
                Object value = json(declared[0], new IdentityHashMap<>(), new int[1], 0,runtimeApi);
                if (!(value instanceof JSONObject)) throw new IllegalArgumentException("插件描述必须为对象");
                return (JSONObject) value;
            });
            if(result.optInt("apiVersion")==1&&source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_SOURCE_BYTES)
                throw new IllegalArgumentException("API 1 插件脚本过大，最多 256 KB；完整执行插件请使用 API 2");
            if (result.optInt("apiVersion")==1&&result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
                throw new IllegalArgumentException("插件生成的界面或规则过大");
            CustomPluginSchema.validate(result);
            return result;
        } catch (BudgetExceeded problem) {
            throw new IllegalArgumentException("插件运行超出时间或计算预算，已停止", problem);
        } catch (StackOverflowError problem) {
            throw new IllegalArgumentException("插件递归过深，已停止", problem);
        }
    }

    private static Object toScript(Context cx, Scriptable scope, Object value) {
        if (value instanceof JSONObject) {
            Scriptable out = cx.newObject(scope);
            java.util.Iterator<String> keys = ((JSONObject) value).keys();
            while (keys.hasNext()) { String key = keys.next(); ScriptableObject.putProperty(out,key,toScript(cx,scope,((JSONObject)value).opt(key))); }
            return out;
        }
        if (value instanceof JSONArray) {
            JSONArray array=(JSONArray)value; Object[] values=new Object[array.length()];
            for(int i=0;i<values.length;i++)values[i]=toScript(cx,scope,array.opt(i));
            return cx.newArray(scope,values);
        }
        if(value==null||value==JSONObject.NULL)return null;
        if(value instanceof String||value instanceof Number||value instanceof Boolean)return value;
        throw new IllegalArgumentException("插件状态只能包含 JSON 数据");
    }

    /** Walk the value directly; never call a plugin supplied toJSON or String conversion. */
    private static Object json(Object value, IdentityHashMap<Object,Boolean> path, int[] count, int depth,boolean runtimeApi) {
        if ((!runtimeApi&&++count[0] > MAX_NODES) || depth > (runtimeApi?64:12)) throw new IllegalArgumentException("插件数据层级或数量过多");
        if (value == null) return JSONObject.NULL;
        if (value instanceof String || value instanceof Boolean) return value;
        if (value instanceof CharSequence) {
            // Rhino ConsString is a concatenated JavaScript string, not a Java bridge.
            if (!value.getClass().getName().equals("org.mozilla.javascript.ConsString")) throw new IllegalArgumentException("非法插件数据");
            String text=value.toString(); if(!runtimeApi&&text.length()>32768)throw new IllegalArgumentException("插件文本过长");return text;
        }
        if (value instanceof Number) {
            double number=((Number)value).doubleValue();
            if(Double.isNaN(number)||Double.isInfinite(number))throw new IllegalArgumentException("插件数字必须有限");
            return value;
        }
        if (value instanceof Function || !(value instanceof NativeObject || value instanceof NativeArray))
            throw new IllegalArgumentException("C17.define 只能返回纯 JSON，不能包含函数、undefined 或 Java 对象");
        if(path.put(value,Boolean.TRUE)!=null)throw new IllegalArgumentException("插件描述不能循环引用");
        try {
            if(value instanceof NativeArray) {
                NativeArray input=(NativeArray)value; long length=input.getLength();
                if(length>Integer.MAX_VALUE||!runtimeApi&&length>256)throw new IllegalArgumentException("插件数组过大");
                JSONArray out=new JSONArray();
                for(int i=0;i<length;i++)out.put(json(input.get(i,input),path,count,depth+1,runtimeApi));
                return out;
            }
            NativeObject input=(NativeObject)value; JSONObject out=new JSONObject();
            Object[] ids=input.getIds();if(!runtimeApi&&ids.length>256)throw new IllegalArgumentException("插件对象字段过多");
            for(Object id:ids) {
                if(!(id instanceof String))throw new IllegalArgumentException("插件对象键必须为字符串");
                String key=(String)id;
                if(key.length()>128||"__proto__".equals(key)||"prototype".equals(key)||"constructor".equals(key))
                    throw new IllegalArgumentException("非法插件对象键");
                try {out.put(key,json(input.get(key,input),path,count,depth+1,runtimeApi));}
                catch(org.json.JSONException e){throw new IllegalArgumentException("非法插件 JSON",e);}
            }
            return out;
        } finally {path.remove(value);}
    }

    private static final class BudgetExceeded extends Error { }
    private static final class BudgetFactory extends ContextFactory {
        private long deadline, instructions;
        @Override protected Context makeContext() {
            Context context=super.makeContext();
            context.setOptimizationLevel(-1);
            context.setLanguageVersion(Context.VERSION_ES6);
            context.setMaximumInterpreterStackDepth(64);
            context.setInstructionObserverThreshold(1000);
            context.setClassShutter(new ClassShutter(){@Override public boolean visibleToScripts(String name){return false;}});
            deadline=System.nanoTime()+600_000_000L;
            return context;
        }
        @Override protected void observeInstructionCount(Context context,int amount) {
            instructions+=amount;
            if(instructions>200_000L||System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new BudgetExceeded();
        }
        @Override protected boolean hasFeature(Context context,int feature) {
            if(feature==Context.FEATURE_ENHANCED_JAVA_ACCESS||feature==Context.FEATURE_E4X||feature==Context.FEATURE_DYNAMIC_SCOPE)return false;
            return super.hasFeature(context,feature);
        }
    }
}
