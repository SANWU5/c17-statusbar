package android.os;
/** Observable Binder caller identity; no Messenger transport or remote execution is simulated. */
public class Binder {
    private static final ThreadLocal<Integer> callerUid=ThreadLocal.withInitial(Process::myUid);
    private static final ThreadLocal<Integer> callerPid=ThreadLocal.withInitial(Process::myPid);
    public static int clearsForCheck,restoresForCheck;
    public static int getCallingUid(){return callerUid.get();}
    public static int getCallingPid(){return callerPid.get();}
    public static void setCallingIdentityForCheck(int uid,int pid){callerUid.set(uid);callerPid.set(pid);}
    public static long clearCallingIdentity(){
        long token=((long)getCallingUid()<<32)|(getCallingPid()&0xffffffffL);
        callerUid.set(Process.myUid());callerPid.set(Process.myPid());clearsForCheck++;return token;
    }
    public static void restoreCallingIdentity(long token){
        callerUid.set((int)(token>>>32));callerPid.set((int)token);restoresForCheck++;
    }
}
