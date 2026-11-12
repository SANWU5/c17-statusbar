// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** A form order is a list of stable values; unavailable values survive catalog refreshes. */
public final class CustomPluginSort {
    private CustomPluginSort() { }
    public static final class Item {
        public final String value,title,icon;
        Item(String value,String title,String icon){this.value=value;this.title=title;this.icon=icon;}
    }
    public static final class Catalog {
        public final List<Item> items;
        public final String revision;
        public final boolean allowRemoval;
        public final Set<String> hidden;
        private final List<String> saved;
        private final Set<String> live;
        Catalog(List<Item> items,String revision,List<String> saved,Set<String> live,boolean allowRemoval,Set<String> hidden){
            this.items=Collections.unmodifiableList(items);this.revision=revision;
            this.allowRemoval=allowRemoval;this.hidden=Collections.unmodifiableSet(hidden);
            this.saved=Collections.unmodifiableList(saved);this.live=Collections.unmodifiableSet(live);
        }
        public String value(List<String> order){
            return value(order,hidden);
        }
        public String value(List<String> order,Set<String> removed){
            if(order==null||order.size()!=live.size()||new LinkedHashSet<>(order).size()!=order.size()||!live.equals(new LinkedHashSet<>(order)))
                throw new IllegalArgumentException("排序项目已改变，请重新打开");
            if(removed==null||!allowRemoval&&!removed.isEmpty())throw new IllegalArgumentException("当前列表不支持移除");
            Set<String> allowed=new LinkedHashSet<>(saved);allowed.addAll(live);
            if(!allowed.containsAll(removed))throw new IllegalArgumentException("移除项目已改变，请重新打开");
            List<String> result=new ArrayList<>();int index=0;
            // Replace only live slots. An offline item's stored position and relative order survive.
            for(String value:saved)result.add(live.contains(value)?order.get(index++):value);
            while(index<order.size())result.add(order.get(index++));
            String json;
            try{json=allowRemoval?new JSONObject().put("version",1).put("order",new JSONArray(result)).put("hidden",new JSONArray(new ArrayList<>(removed))).toString():new JSONArray(result).toString();}
            catch(org.json.JSONException invalid){throw new IllegalArgumentException(invalid);}
            validateValue(json);return json;
        }
        public boolean matches(JSONObject current){
            try{
                Catalog other=read("[]",current);
                return revision.equals(other.revision)&&live.equals(other.live)&&allowRemoval==other.allowRemoval;
            }catch(IllegalArgumentException invalid){return false;}
        }
    }
    public static Catalog read(String savedValue,JSONObject source){
        try{
            if(source==null||!source.optBoolean("available"))throw new IllegalArgumentException(source==null?"暂时无法读取排序项目":source.optString("message","展开控制中心后重试"));
            String revision=source.optString("revision");if(revision.isEmpty()||revision.length()>512)throw new IllegalArgumentException("排序列表标识无效");
            JSONArray raw=source.getJSONArray("items");Map<String,Item> entries=new LinkedHashMap<>();
            for(int i=0;i<raw.length();i++){
                JSONObject item=raw.getJSONObject(i);String value=item.getString("value"),title=item.getString("title");
                String icon=item.optString("icon","");
                if(icon.length()>8192||!icon.matches("[A-Za-z0-9+/]*={0,2}"))throw new IllegalArgumentException("排序图标无效");
                validValue(value);if(title.trim().isEmpty()||title.length()>120||title.indexOf('\0')>=0||entries.put(value,new Item(value,title,icon))!=null)throw new IllegalArgumentException("排序列表项目无效或重复");
            }
            List<String> selected=parse(source.getJSONArray("selected").toString());
            if(!entries.keySet().equals(new LinkedHashSet<>(selected)))throw new IllegalArgumentException("排序列表已改变，请重新读取");
            Selection selection=selection(savedValue);List<String> saved=selection.order,ordered=new ArrayList<>();
            boolean allowRemoval=source.optBoolean("allowRemoval",false);
            if(!allowRemoval&&!selection.hidden.isEmpty())throw new IllegalArgumentException("当前插件不支持已保存的移除选择");
            for(String value:saved)if(entries.containsKey(value))ordered.add(value);
            for(String value:selected)if(!ordered.contains(value))ordered.add(value);
            List<Item> items=new ArrayList<>();for(String value:ordered)items.add(entries.get(value));
            return new Catalog(items,revision,saved,new LinkedHashSet<>(entries.keySet()),allowRemoval,new LinkedHashSet<>(selection.hidden));
        }catch(org.json.JSONException invalid){throw new IllegalArgumentException("排序数据无效",invalid);}
    }
    public static void validateValue(Object value){
        if(!(value instanceof String))throw new IllegalArgumentException("排序值必须为列表文字");
        selection((String)value);
    }
    private static final class Selection{
        final List<String> order,hidden;
        Selection(List<String> order,List<String> hidden){this.order=order;this.hidden=hidden;}
    }
    private static Selection selection(String value){
        if(value==null||value.length()>65536)throw new IllegalArgumentException("排序数据过大");
        if(!value.trim().startsWith("{"))return new Selection(parse(value),Collections.emptyList());
        try{JSONObject object=new JSONObject(value);
            if(object.length()!=3||object.getInt("version")!=1)throw new IllegalArgumentException("排序选择格式无效");
            List<String> order=parse(object.getJSONArray("order").toString()),hidden=parse(object.getJSONArray("hidden").toString());
            if(!order.containsAll(hidden))throw new IllegalArgumentException("移除项目不在已保存列表中");
            return new Selection(order,hidden);
        }catch(org.json.JSONException invalid){throw new IllegalArgumentException("排序选择数据无效",invalid);}
    }
    private static List<String> parse(String value){
        if(value==null||value.length()>65536)throw new IllegalArgumentException("排序数据过大");
        try{
            JSONArray array=new JSONArray(value);List<String> result=new ArrayList<>();Set<String> seen=new LinkedHashSet<>();
            for(int i=0;i<array.length();i++){
                Object raw=array.get(i);if(!(raw instanceof String))throw new IllegalArgumentException("排序值必须为文字");
                String item=(String)raw;validValue(item);if(!seen.add(item))throw new IllegalArgumentException("排序值重复");result.add(item);
            }
            return result;
        }catch(org.json.JSONException invalid){throw new IllegalArgumentException("排序数据必须为列表",invalid);}
    }
    private static void validValue(String value){if(value==null||value.trim().isEmpty()||value.length()>4096||value.indexOf('\0')>=0)throw new IllegalArgumentException("排序项目标识无效");}
}
