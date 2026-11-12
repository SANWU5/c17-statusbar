// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

/** Open editors retain their own field contract even when the form updates in place. */
public final class CustomPluginControlSessionCheck {
    private static int checks;
    private interface Task { void run() throws Exception; }
    private static void equal(Object expected,Object actual,String label) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError(label+": "+expected+" != "+actual);
    }
    private static void reject(Task task,String label)throws Exception {
        checks++;try{task.run();}catch(Exception expected){return;}throw new AssertionError("Accepted "+label);
    }
    private static String source(int version) {
        return "var ui=[{type:'switch',key:'compact',title:'Compact',default:false},"
                +"{type:'slider',key:'amount',title:'Amount',min:0,max:C17.state.compact===true?50:100,default:40,step:5},"
                +"{type:C17.state.color_mode===true?'color':'input',key:'tint',title:'Tint',default:'#FFFFFF'},"
                +"{type:'switch',key:'color_mode',title:'Color mode',default:false}];"
                +"C17.define({id:'dev.c17.controlsession',name:'Control session',version:'"+version+"',versionCode:"+version+","
                +"apiVersion:1,capabilities:['settings'],ui:ui,settings:{},hooks:[]});";
    }
    private static CustomPluginRepository.Plugin install(CustomPluginRepository repo,int version)throws Exception {
        try(CustomPluginRepository.Preview preview=repo.preview(new ByteArrayInputStream(source(version).getBytes(StandardCharsets.UTF_8)),"session.js")) {
            return repo.install(preview,version>1);
        }
    }
    private static JSONObject field(CustomPluginRepository repo,String key)throws Exception {
        JSONArray fields=repo.list().get(0).descriptor.getJSONArray("ui");
        for(int index=0;index<fields.length();index++) {
            JSONObject field=fields.getJSONObject(index);
            if(key.equals(field.optString("key")))return new JSONObject(field.toString());
        }
        throw new AssertionError("Missing field "+key);
    }
    private static byte[] database(File dir)throws Exception {return Files.readAllBytes(new File(dir,"library.json").toPath());}
    private static void rejectUnchanged(CustomPluginRepository repo,File dir,Task task,String label)throws Exception {
        byte[] before=database(dir);reject(task,label);
        equal(true,Arrays.equals(before,database(dir)),label+" leaves durable settings and revision untouched");
        equal(true,repo.list().get(0).enabled,label+" does not disable the plugin");
    }
    public static void main(String[] args)throws Exception {
        File dir=Files.createTempDirectory("c17-control-session-").toFile();CustomPluginRepository repo=new CustomPluginRepository(dir);
        String id=install(repo,1).id;repo.setEnabled(id,true);
        JSONObject amount=field(repo,"amount"),tint=field(repo,"tint");
        repo.updateStateControl(id,1,amount,21.25d);
        equal(21.25d,repo.list().get(0).state.getDouble("amount"),"matching editor saves exact manual precision");
        equal(true,repo.list().get(0).enabled,"successful editor preserves enablement");
        repo.updateState(id,"compact",true);
        equal(50,field(repo,"amount").getInt("max"),"same-version form changed its range");
        rejectUnchanged(repo,dir,()->repo.updateStateControl(id,1,amount,45d),"old range snapshot even with a still-valid new value");
        JSONObject compactAmount=field(repo,"amount");repo.updateStateControl(id,1,compactAmount,44.5d);
        equal(44.5d,repo.list().get(0).state.getDouble("amount"),"fresh range snapshot saves normally");
        repo.updateState(id,"color_mode",true);
        equal("color",field(repo,"tint").getString("type"),"same-version form changed input to color");
        rejectUnchanged(repo,dir,()->repo.updateStateControl(id,1,tint,"#445566"),"old type snapshot even with valid color text");
        JSONObject color=field(repo,"tint");repo.updateStateControl(id,1,color,"#00445566");
        equal("#00445566",repo.list().get(0).state.getString("tint"),"fresh color snapshot preserves transparent alpha");
        rejectUnchanged(repo,dir,()->repo.updateStateControl(id,1,null,1),"missing editor contract");
        JSONObject missing=new JSONObject(color.toString()).put("key","removed");
        rejectUnchanged(repo,dir,()->repo.updateStateControl(id,1,missing,"#FFFFFF"),"missing field");
        install(repo,2);repo.setEnabled(id,true);
        rejectUnchanged(repo,dir,()->repo.updateStateControl(id,1,color,"#FFFFFF"),"previous plugin version");
        JSONObject current=field(repo,"tint");repo.updateStateControl(id,2,current,"#FFAABB");
        equal("#FFAABB",new CustomPluginRepository(dir).list().get(0).state.getString("tint"),"current version editor saves and reopens");
        System.out.println("CustomPluginControlSessionCheck "+checks+" checks passed");
    }
}
