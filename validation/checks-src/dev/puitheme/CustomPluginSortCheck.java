// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

/** Sorting changes order only, survives missing tiles, and remains a declared runtime form action. */
public final class CustomPluginSortCheck {
    private static int checks;
    private static void yes(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private interface Task {void run()throws Exception;}
    private static void reject(Task task,String message)throws Exception{checks++;try{task.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError(message);}
    private static JSONObject descriptor()throws Exception{
        return new JSONObject().put("id","sort.form").put("name","Sorting").put("version","test").put("versionCode",1).put("apiVersion",2)
                .put("capabilities",new JSONArray().put("runtime")).put("runtime",new JSONObject().put("source","// test"))
                .put("settings",new JSONObject()).put("hooks",new JSONArray()).put("ui",new JSONArray().put(new JSONObject()
                        .put("type","sort").put("key","order").put("title","磁贴顺序").put("default","[]").put("optionsAction","catalog")));
    }
    private static JSONObject catalog(String revision)throws Exception{
        return new JSONObject().put("available",true).put("revision",revision)
                .put("items",new JSONArray().put(new JSONObject().put("value","wifi").put("title","WLAN"))
                        .put(new JSONObject().put("value","bt").put("title","蓝牙").put("icon","aWNvbg=="))
                        .put(new JSONObject().put("value","flashlight").put("title","手电筒")))
                .put("selected",new JSONArray().put("bt").put("wifi").put("flashlight"));
    }
    public static int run()throws Exception{
        checks=0;JSONObject descriptor=descriptor();CustomPluginSchema.validate(descriptor);CustomPluginSchema.requireConfiguration(descriptor);
        yes(CustomPluginSchema.configurationCount(descriptor)==1,"sorting alone is an editable form");
        JSONObject control=descriptor.getJSONArray("ui").getJSONObject(0);
        yes(CustomPluginSchema.persistentControl(control),"order is independent persistent plugin state");
        yes("[]".equals(CustomPluginSchema.defaultValue(control)),"empty order means the plugin's current order");
        yes(CustomPluginSchema.form(descriptor).getJSONArray("actions").getString(0).equals("catalog"),"catalog callback travels through the existing declared action channel");
        yes(CustomPluginSupport.FEATURES.contains("ui.sort"),"host advertises generic sort support");
        JSONObject old=new JSONObject(descriptor.toString()).put("apiVersion",1).put("capabilities",new JSONArray());old.remove("runtime");
        reject(()->CustomPluginSchema.validate(old),"API1 cannot fetch executable sort options");
        JSONObject undeclared=new JSONObject(descriptor.toString());undeclared.getJSONArray("ui").getJSONObject(0).remove("optionsAction");
        reject(()->CustomPluginSchema.validate(undeclared),"sort must declare its catalog callback");
        JSONObject collision=new JSONObject(descriptor.toString());collision.getJSONArray("ui").put(new JSONObject().put("type","status").put("key","catalog").put("title","Status"));
        reject(()->CustomPluginSchema.validate(collision),"catalog cannot collide with a status/control key");
        JSONObject wrong=new JSONObject(descriptor.toString());wrong.getJSONArray("ui").getJSONObject(0).put("type","input");
        reject(()->CustomPluginSchema.validate(wrong),"unrelated inputs cannot claim catalog actions");
        reject(()->CustomPluginSchema.stateValue(control,"[\"wifi\",\"wifi\"]"),"duplicate tiles cannot be persisted");
        reject(()->CustomPluginSchema.stateValue(control,"[1]"),"only stable string values are saved");
        reject(()->CustomPluginSchema.stateValue(control,"{}"),"order is a JSON array");
        JSONArray large=new JSONArray();for(int i=0;i<130;i++)large.put("custom(pkg.component.with.long.but.valid.identity.Tile"+i+")");
        yes(large.toString().length()>4096,"fixture exercises a real catalog larger than an input box");CustomPluginSchema.stateValue(control,large.toString());
        JSONObject input=new JSONObject().put("type","input");reject(()->CustomPluginSchema.stateValue(input,large.toString()),"ordinary input limit remains unchanged");
        JSONObject source=catalog("u0-c1");CustomPluginSort.Catalog draft=CustomPluginSort.read("[]",source);
        yes(draft.items.get(0).value.equals("bt")&&draft.items.get(1).value.equals("wifi"),"new draft uses the actual visible order");
        yes("蓝牙".equals(draft.items.get(0).title)&&"aWNvbg==".equals(draft.items.get(0).icon),"native label and icon travel together in sorted rows");
        JSONObject badIcon=catalog("u0-c1");badIcon.getJSONArray("items").getJSONObject(0).put("icon","not-a-png-url");reject(()->CustomPluginSort.read("[]",badIcon),"icon payload cannot request remote loading");
        JSONObject app=new JSONObject().put("type","app").put("key","target_app").put("title","打开 App").put("default","");
        JSONObject appForm=descriptor();appForm.getJSONArray("ui").put(app);CustomPluginSchema.validate(appForm);
        yes(CustomPluginSchema.persistentControl(app)&&"".equals(CustomPluginSchema.defaultValue(app)),"app selection is a persistent empty-default form value");
        CustomPluginSchema.stateValue(app,"com.example.player");checks++;
        reject(()->CustomPluginSchema.stateValue(app,"com.example/player"),"app selection rejects component fragments");
        yes(draft.value(Arrays.asList("flashlight","wifi","bt")).equals("[\"flashlight\",\"wifi\",\"bt\"]"),"one save reorders the whole visible set");
        CustomPluginSort.Catalog offline=CustomPluginSort.read("[\"wifi\",\"unavailable\",\"bt\"]",source);
        yes(offline.items.size()==3&&offline.items.get(2).value.equals("flashlight"),"newly available item appears after saved visible members");
        String value=offline.value(Arrays.asList("bt","flashlight","wifi"));
        yes(value.equals("[\"bt\",\"unavailable\",\"flashlight\",\"wifi\"]"),"an unavailable saved item retains its slot while live values move");
        yes(!value.contains("WLAN")&&!value.contains("手电筒"),"only stable IDs are persisted, never live labels");
        reject(()->offline.value(Arrays.asList("bt","wifi")),"save cannot silently delete a visible tile");
        reject(()->offline.value(Arrays.asList("bt","wifi","wifi")),"save cannot duplicate a tile");
        yes(offline.matches(source),"unchanged catalog validates a draft");
        yes(!offline.matches(catalog("u0-c2")),"changed native order/revision refuses stale draft");
        JSONObject changed=catalog("u0-c1");changed.getJSONArray("items").put(new JSONObject().put("value","cell").put("title","移动数据"));changed.getJSONArray("selected").put("cell");
        yes(!offline.matches(changed),"same revision with a different visible set is still refused");
        JSONObject missing=new JSONObject().put("available",false).put("message","展开控制中心后重试");reject(()->CustomPluginSort.read("[]",missing),"unavailable catalog does not become a destructive empty list");
        JSONObject incomplete=catalog("u0-c1");incomplete.put("selected",new JSONArray().put("wifi"));reject(()->CustomPluginSort.read("[]",incomplete),"catalog and current order must describe exactly the same members");
        JSONObject duplicate=catalog("u0-c1");duplicate.getJSONArray("items").put(new JSONObject().put("value","wifi").put("title","Other"));reject(()->CustomPluginSort.read("[]",duplicate),"duplicate catalog values are refused");
        yes(offline.value(Arrays.asList("bt","flashlight","wifi")).equals(value),"draft validation never mutates saved or proposed order");
        JSONObject removable=catalog("manage-1").put("allowRemoval",true);
        CustomPluginSort.Catalog manager=CustomPluginSort.read("[\"wifi\",\"offline\",\"bt\"]",removable);
        String removed=manager.value(Arrays.asList("bt","wifi","flashlight"),new java.util.LinkedHashSet<>(Arrays.asList("wifi","offline")));
        CustomPluginSort.validateValue(removed);CustomPluginSort.Catalog reopened=CustomPluginSort.read(removed,removable);
        yes(reopened.hidden.containsAll(Arrays.asList("wifi","offline")),"removed and offline selections survive reopen");
        yes(reopened.items.size()==3,"removed entries stay manageable");
        String restored=reopened.value(Arrays.asList("wifi","bt","flashlight"),java.util.Collections.singleton("offline"));
        yes(!new JSONObject(restored).getJSONArray("hidden").toString().contains("wifi"),"restore does not change offline removals");
        reject(()->manager.value(Arrays.asList("bt","wifi","flashlight"),java.util.Collections.singleton("unknown")),"unknown removal rejected");
        yes(!manager.matches(catalog("manage-1")),"capability change rejects stale management draft");
        reject(()->CustomPluginSort.validateValue("{\"version\":1,\"order\":[],\"hidden\":[\"wifi\"]}"),"removed identity must be saved");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("CustomPluginSortCheck "+run()+" checks passed");}
}
