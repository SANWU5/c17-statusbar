// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;
import org.json.JSONObject;
public final class CustomPluginMoreRefreshCheck {
    static int checks;
    static void yes(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static void reject(Runnable action){checks++;try{action.run();}catch(IllegalStateException expected){return;}throw new AssertionError("Stale or invalid refresh accepted");}
    static JSONObject status(long generation,String state)throws Exception{return new JSONObject().put("generation",generation).put("state",state);}
    public static void main(String[] args)throws Exception{
        yes(CustomPluginMoreRefresh.matches("dev.c17.validation.qs-card","qs_card_more_refresh_catalog"),"catalog action uses completion polling");
        yes(!CustomPluginMoreRefresh.matches("other","qs_card_more_refresh_catalog"),"another plugin does not inherit a private protocol");
        yes(!CustomPluginMoreRefresh.matches("dev.c17.validation.qs-card","other"),"other actions remain synchronous");
        JSONObject done=status(4,"succeeded"),running=status(5,"running");
        yes(CustomPluginMoreRefresh.generation(done,running)==5,"new request is bound to its generation");
        yes(CustomPluginMoreRefresh.generation(running,running)==5,"duplicate accepted request observes original generation");
        yes(CustomPluginMoreRefresh.terminal(running,5)==null,"accepted or running is never treated as completed");
        reject(()->CustomPluginMoreRefresh.generation(done,done));reject(()->CustomPluginMoreRefresh.generation(running,done));
        reject(()->CustomPluginMoreRefresh.generation(done,null));reject(()->CustomPluginMoreRefresh.terminal(done,5));
        for(String state:new String[]{"succeeded","failed","cancelled","timed_out"}){
            JSONObject terminal=status(5,state);yes(CustomPluginMoreRefresh.generation(done,terminal)==5,"immediate terminal is observable");
            yes(!CustomPluginMoreRefresh.terminal(terminal,5).isEmpty(),"all terminal outcomes have an accurate fallback");
            terminal.put("message","测试原因 "+state);yes(CustomPluginMoreRefresh.terminal(terminal,5).equals("测试原因 "+state),"plugin detailed outcome survives host delivery");
        }
        JSONObject idle=status(0,"idle"),invalid=status(5,"unknown");reject(()->CustomPluginMoreRefresh.generation(null,idle));reject(()->CustomPluginMoreRefresh.terminal(invalid,5));
        System.out.println("CustomPluginMoreRefreshCheck "+checks+" checks passed");
    }
}
