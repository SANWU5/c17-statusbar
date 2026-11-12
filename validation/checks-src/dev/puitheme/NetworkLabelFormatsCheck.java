package dev.puitheme;

import android.os.Bundle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Per-generation network texts: portable, editable, mutually exclusive with unify, and applied as the last output step. */
public final class NetworkLabelFormatsCheck {
    private static int checks;
    private static void equal(Object wanted,Object actual,String reason) {
        checks++;
        if (!Objects.equals(wanted,actual)) throw new AssertionError(reason+": "+wanted+" != "+actual);
    }
    private static Bundle custom(String... pairs) {
        Bundle bundle=new Bundle();
        for(int i=0;i+1<pairs.length;i+=2)bundle.putString(NetworkLabelFormats.PREFIX+pairs[i],pairs[i+1]);
        return bundle;
    }
    public static void main(String[] args) throws Exception {
        // Backup, mirror and snapshot round trips keep blank and unicode contents.
        Map<String,Object> saved=new LinkedHashMap<>();
        saved.put("label_enabled",true);saved.put("label_normalize_enabled",false);
        saved.put(NetworkLabelFormats.ENABLED,true);
        saved.put(RadioState.BOTH_VISIBLE,true);
        int i=0;
        for (String key:NetworkLabelFormats.STRINGS.keySet()) saved.put(key,i++%2==0?"":"旧内容 📶");
        ConfigTransfer.PreparedImport imported=ConfigTransfer.prepare(ConfigTransfer.exportJson(saved),true);
        Bundle snapshot=SettingsSnapshot.fromPreferences(imported.values());
        equal(16,NetworkLabelFormats.STRINGS.size(),"every generation has its own text");
        equal(16,NetworkLabel.Level.values().length,"level list matches the text list");
        for (Map.Entry<String,String> item:NetworkLabelFormats.STRINGS.entrySet()) {
            String key=item.getKey();
            equal(saved.get(key),imported.values().get(key),"backup preserves blank/unicode content "+key);
            equal(saved.get(key),snapshot.getString(key),"complete snapshot preserves content "+key);
            equal(true,SettingsCatalog.item(key)!=null,"editor present "+key);
            equal("network",SettingsCatalog.item(key).groupId,"editor belongs to the combined network page "+key);
            equal(false,SettingsCatalog.hiddenKeys().contains(key),"content key is not hidden "+key);
            equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(key),"wire type "+key);
        }
        equal(true,SettingsCatalog.item(NetworkLabelFormats.ENABLED)!=null,"custom switch present");
        equal(true,snapshot.getBoolean(NetworkLabelFormats.ENABLED,false),"saved request remains intact");
        equal(true,FeatureOptions.from(snapshot).effective("label",NetworkLabelFormats.ENABLED),"custom contents run when label is on");
        equal(false,FeatureOptions.from(snapshot).effective("label",NetworkLabelFormats.UNIFY),"unify stays off while custom is on");
        // Mutual exclusion in the editor and at run time.
        equal(NetworkLabelFormats.UNIFY,NetworkLabelFormats.turnsOff(NetworkLabelFormats.ENABLED),"custom turns unify off");
        equal(NetworkLabelFormats.ENABLED,NetworkLabelFormats.turnsOff(NetworkLabelFormats.UNIFY),"unify turns custom off");
        equal(null,NetworkLabelFormats.turnsOff("label_hidden"),"other switches have no partner");
        Map<String,Object> both=new LinkedHashMap<>();
        both.put("label_enabled",true);both.put(NetworkLabelFormats.ENABLED,true);both.put(NetworkLabelFormats.UNIFY,true);
        equal(true,FeatureOptions.from(both).effective("label",NetworkLabelFormats.ENABLED),"a backup with both on runs custom");
        equal(false,FeatureOptions.from(both).effective("label",NetworkLabelFormats.UNIFY),"a backup with both on ignores unify");
        both.remove(NetworkLabelFormats.ENABLED);
        equal(true,FeatureOptions.from(both).effective("label",NetworkLabelFormats.UNIFY),"unify runs alone");
        // Recognition keeps every generation apart.
        equal(NetworkLabel.Level.FIVE_G,NetworkLabel.level("5G"),"5G");
        equal(NetworkLabel.Level.FIVE_G_PLUS,NetworkLabel.level("5G+"),"5G+");
        equal(NetworkLabel.Level.FIVE_G_A,NetworkLabel.level("5GA"),"5GA");
        equal(NetworkLabel.Level.FIVE_G_SA,NetworkLabel.level("NR SA"),"NR SA alias");
        equal(NetworkLabel.Level.FOUR_G_PLUS,NetworkLabel.level("LTE_CA"),"LTE CA alias");
        equal(NetworkLabel.Level.H_PLUS,NetworkLabel.level("HSPA+"),"HSPA+ alias");
        equal(null,NetworkLabel.level("中国广电 5G"),"carrier words are not generations");
        equal("",NetworkLabel.canonical("无服务"),"absent state");
        equal("中国广电 5G",NetworkLabel.canonical(" 中国广电 5G "),"unknown words are kept, trimmed");
        // The formatter: custom, unify, neither.
        NetworkLabelFormats formats=new NetworkLabelFormats();
        formats.configure(custom("5g","XX","5ga","5Ga","4g_plus","","h_plus","H加"));
        equal("XX",formats.display("5G",false,true),"5G=XX");
        equal("XX",formats.display("5G",true,true),"custom wins when both are on");
        equal("5Ga",formats.display("5GA",false,true),"5GA=5Ga");
        equal("5G+",formats.display("5G+",false,true),"untouched generation keeps its default text");
        equal("",formats.display("4G+",false,true),"blank hides the generation");
        equal("H加",formats.display("H+",false,true),"H+ custom");
        equal("中国广电 5G",formats.display("中国广电 5G",false,true),"unknown words keep the system text");
        equal("中国广电 5G",formats.display("中国广电 5G",true,true),"unify does not touch unknown words while custom is on");
        equal("5G",formats.display("5GA",true,false),"unify groups 5GA");
        equal("4G",formats.display("4G+",true,false),"unify groups 4G+");
        equal("5GA",formats.display("5GA",false,false),"neither: system text");
        equal("",formats.display("",false,true),"nothing stays nothing");
        // Bad text (control characters, lone surrogate, too long) falls back to the default.
        Bundle bad=custom("5g","a\nb","4g","\ud800","3g",new String(new char[TimeFormat.MAX_TEXT+1]).replace('\0','x'));
        formats.configure(bad);
        equal("5G",formats.display("5G",false,true),"control characters fall back");
        equal("4G",formats.display("4G",false,true),"lone surrogate falls back");
        equal("3G",formats.display("3G",false,true),"overlong text falls back");
        // The gates still win: Wi-Fi in use, airplane and radios off give no label even with custom text.
        equal("",RadioState.cellularLabel(true,true,false,"5GA","",false,true,true),"Wi-Fi in use hides the label");
        equal("",RadioState.cellularLabel(true,false,true,"5GA","",false,true,true),"airplane hides the label");
        equal("",RadioState.cellularLabel(true,false,false,"5GA","",false,false,false),"both radios off hide the label");
        equal("5GA",RadioState.cellularLabel(true,false,false,"5GA","",false,true,true),"gate keeps the recognised text");
        // The telephony fallback keeps enhanced generations.
        equal("5G+",NetworkLabel.forDataNetwork(20,4),"NR advanced override is 5G+");
        equal("4G+",NetworkLabel.forDataNetwork(13,1),"LTE advanced override is 4G+");
        equal("H",NetworkLabel.forDataNetwork(10,0),"HSPA is H");
        equal("",NetworkLabel.forDataNetwork(0,0),"unknown RAT is nothing");
        equal(true,SettingsSnapshot.complete(snapshot),"full compatible schema retained");
        System.out.println("NetworkLabelFormatsCheck: "+checks+" per-generation text checks passed");
    }
}
