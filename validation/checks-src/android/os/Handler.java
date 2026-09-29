package android.os;
public class Handler {
    public Runnable delayed;
    public long delay;
    public int scheduled;
    public Handler(Looper looper) { }
    public boolean post(Runnable task) { return true; }
    public boolean postDelayed(Runnable task,long millis) { delayed=task;delay=millis;scheduled++;return true; }
    public void removeCallbacks(Runnable task) { if(delayed==task)delayed=null; }
}
