package dev.puitheme;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.*;

/** Failed disk commit + unavailable rollback editor must never publish rejected memory later. */
public final class PreferenceWritesRecoveryCheck {
    static final String A=StatusBarSettings.DATA_OFFSET_X,B=StatusBarSettings.DATA_OFFSET_Y;
    static int checks;
    static void yes(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Store {
        final ActivationGuardPreferencesCheck.MemoryPreferences memory=new ActivationGuardPreferencesCheck.MemoryPreferences();
        final Map<String,Object> disk=new HashMap<>();
        int failCommit,failEdit;Runnable afterCommit;
        final SharedPreferences raw=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
            if(!method.getName().equals("edit"))return method.invoke(memory,args);
            if(failEdit>0){failEdit--;throw new IllegalStateException("rollback editor unavailable");}
            SharedPreferences.Editor editor=memory.edit();
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(p,m,a)->{
                if(!m.getName().equals("commit")&&!m.getName().equals("apply")){m.invoke(editor,a);return p;}
                editor.commit();boolean fail=failCommit>0;if(fail)failCommit--;
                if(!fail){disk.clear();disk.putAll(memory.values);}
                Runnable callback=afterCommit;afterCommit=null;if(callback!=null)callback.run();
                return m.getName().equals("commit")?!fail:null;
            });
        });
        Store(){memory.values.put(A,10f);memory.values.put(B,20f);disk.putAll(memory.values);}
        SharedPreferences guard(){return new ActivationGuardPreferences(raw,()->true,()->{});}
        void reject(){failCommit=1;afterCommit=()->failEdit=1;}
        void unchangedA(){yes(Objects.equals(disk.get(A),10f),"rejected A never reaches disk");yes(Objects.equals(PreferenceWrites.values(raw).get(A),10f),"provider sees original A");}
    }
    public static void main(String[] args){
        for(int cycle=0;cycle<40;cycle++){
            Store s=new Store();s.reject();yes(!s.guard().edit().putFloat(A,99f).commit(),"initial failure");s.unchangedA();
            yes(s.guard().edit().putFloat(B,30f).commit(),"unrelated successful commit");s.unchangedA();
            yes(PreferenceWrites.notificationAllowed(s.raw),"successful repaired commit can notify");
            s=new Store();s.reject();s.guard().edit().putFloat(A,99f).commit();s.reject();
            yes(!s.guard().edit().putFloat(B,88f).commit(),"second failure");s.unchangedA();
            yes(Objects.equals(PreferenceWrites.values(s.raw).get(B),20f),"second rejected value masked");
            yes(s.guard().edit().putBoolean(StatusBarSettings.DATA_ACTIVITY_HIDDEN,true).commit(),"third edit repairs both failures");s.unchangedA();
            yes(Objects.equals(s.disk.get(B),20f),"second failure never persisted");
            s=new Store();s.reject();s.guard().edit().putFloat(A,99f).commit();
            yes(s.guard().edit().putFloat(A,99f).commit(),"explicit retry of same value is allowed");
            yes(Objects.equals(s.disk.get(A),99f)&&Objects.equals(PreferenceWrites.values(s.raw).get(A),99f),"confirmed retry is visible");
            s=new Store();s.reject();s.guard().edit().putFloat(A,99f).commit();s.guard().edit().putFloat(B,30f).apply();s.unchangedA();
            Store nested=new Store();nested.failCommit=1;nested.afterCommit=()->{
                yes(nested.guard().edit().putFloat(A,77f).commit(),"nested first commit");
                yes(nested.guard().edit().putFloat(A,99f).commit(),"nested same-value commit");
            };
            yes(!nested.guard().edit().putFloat(A,99f).commit(),"outer failed");
            yes(Objects.equals(nested.disk.get(A),99f)&&Objects.equals(PreferenceWrites.values(nested.raw).get(A),99f),"later confirmed ABA value is not rolled back");
        }
        System.out.println("PreferenceWritesRecoveryCheck "+checks+" checks passed");
    }
}
