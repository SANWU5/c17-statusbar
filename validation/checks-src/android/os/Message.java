package android.os;
public final class Message {
    private Runnable callback;
    private boolean asynchronous;
    public static Message obtain(Handler owner, Runnable callback) {
        Message message = new Message();
        message.callback = callback;
        return message;
    }
    public Runnable getCallback() { return callback; }
    public void setAsynchronous(boolean asynchronous) { this.asynchronous = asynchronous; }
    public boolean isAsynchronous() { return asynchronous; }
}
