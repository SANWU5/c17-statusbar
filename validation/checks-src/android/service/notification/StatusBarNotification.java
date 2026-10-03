package android.service.notification;
/** Receipt time is independent of the application-supplied display timestamp. */
public final class StatusBarNotification {
    public long postTime, appWhen;
    public StatusBarNotification(long time){postTime=time;}
    public long getPostTime(){return postTime;}
}
