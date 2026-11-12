// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import org.json.JSONObject;

/** Real durable repository: preset edits commit together and leave unrelated state intact. */
public final class CustomPluginPresetCheck {
    private static int checks;
    private interface Task { void run() throws Exception; }
    private static void equal(Object wanted,Object actual,String label){checks++;if(!Objects.equals(wanted,actual))throw new AssertionError(label+": "+wanted+" != "+actual);}
    private static void reject(Task action,String label)throws Exception{checks++;try{action.run();}catch(Exception expected){return;}throw new AssertionError("Accepted "+label);}
    private static String source(boolean fail){return "var form=["
        +"{type:'switch',key:'area',title:'Area',default:true},"
        +"{type:'slider',key:'amount',title:'Amount',min:0,max:100,default:80},"
        +"{type:'color',key:'tint',title:'Tint',default:'#FFFFFF'},"
        +"{type:'preset',key:'clear',title:'Clear',values:{amount:25,tint:'#00445566'}}];"
        +(fail?"if(C17.state.amount===25) throw new Error('Rejected combined preset');":"")
        +"C17.define({id:'dev.c17.presetcheck',name:'Preset check',version:'1',versionCode:1,apiVersion:1,capabilities:['settings'],ui:form,settings:{},hooks:[]});";}
    private static CustomPluginRepository.Plugin install(CustomPluginRepository repo,String source)throws Exception{
        try(CustomPluginRepository.Preview preview=repo.preview(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),"preset.js")){return repo.install(preview,false);}
    }
    private static byte[] database(File dir)throws Exception{return Files.readAllBytes(new File(dir,"library.json").toPath());}
    public static void main(String[] args)throws Exception{
        File dir=Files.createTempDirectory("c17-preset-").toFile();CustomPluginRepository repo=new CustomPluginRepository(dir);
        String id=install(repo,source(false)).id;repo.updateState(id,"area",false);repo.setEnabled(id,true);
        long revision=new JSONObject(new String(database(dir),StandardCharsets.UTF_8)).optLong("revision");
        repo.applyPreset(id,1,"clear");CustomPluginRepository.Plugin result=new CustomPluginRepository(dir).list().get(0);
        equal(false,result.state.getBoolean("area"),"unrelated region selection preserved");
        equal(25,result.state.getInt("amount"),"preset amount saved");equal("#00445566",result.state.getString("tint"),"transparent color saved");
        equal(true,result.enabled,"enablement preserved");equal(false,result.state.has("clear"),"preset command not persisted as state");
        equal(revision+1,new JSONObject(new String(database(dir),StandardCharsets.UTF_8)).optLong("revision"),"one aggregate revision for all preset values");
        repo.updateState(id,"amount",44.5);equal(44.5,repo.list().get(0).state.getDouble("amount"),"preset leaves values manually editable");
        byte[] before=database(dir);reject(()->repo.applyPreset(id,2,"clear"),"stale version");reject(()->repo.applyPreset(id,1,"area"),"not a preset");
        equal(true,Arrays.equals(before,database(dir)),"rejected preset leaves durable state untouched");
        File badDir=Files.createTempDirectory("c17-preset-failure-").toFile();CustomPluginRepository bad=new CustomPluginRepository(badDir);
        String badId=install(bad,source(true)).id;bad.setEnabled(badId,true);byte[] original=database(badDir);
        reject(()->bad.applyPreset(badId,1,"clear"),"combined compile failure");
        equal(true,Arrays.equals(original,database(badDir)),"compile failure neither partially applies nor disables plugin");
        System.out.println("CustomPluginPresetCheck "+checks+" checks passed");
    }
}
