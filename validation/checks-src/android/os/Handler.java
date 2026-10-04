package android.os;
public class Handler {
    public static final java.util.List<Runnable> postedForCheck=new java.util.ArrayList<>();
    public Runnable delayed;
    public long delay;
    public int scheduled;
    public Handler(Looper looper) { }
    public Looper getLooper() { return Looper.getMainLooper(); }
    public boolean post(Runnable task) {postedForCheck.add(task);return true; }
    public static void drainForCheck(){
        while(!postedForCheck.isEmpty()){
            java.util.List<Runnable> pending=new java.util.ArrayList<>(postedForCheck);postedForCheck.clear();
            for(Runnable task:pending)task.run();
        }
    }
    public boolean postDelayed(Runnable task,long millis) { delayed=task;delay=millis;scheduled++;return true; }
    public void removeCallbacks(Runnable task) { if(delayed==task)delayed=null; }
}
