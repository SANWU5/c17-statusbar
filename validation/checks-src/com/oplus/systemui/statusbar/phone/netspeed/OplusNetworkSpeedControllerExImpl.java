package com.oplus.systemui.statusbar.phone.netspeed;

/** Replay of classes4.dex: BG message 100001, actual elapsed denominator, next schedule 4000L. */
public final class OplusNetworkSpeedControllerExImpl {
    public interface DelayArgument { long apply(long original); }
    public final DelayArgument hook;
    public long clock,totalBytes,lastTime,lastTotalBytes,reportedSpeed,nextAt=-1;
    public int reads,updates;
    public boolean isConnected=true,isSwitchOn=true,isSuspend,isPause;
    public OplusNetworkSpeedControllerExImpl(DelayArgument hook){this.hook=hook;}
    public void postUpdateNetworkSpeedDelay(long delay) {
        // The original replaces pending BG message what=100001 before adding the chosen delay.
        nextAt=clock+(hook==null?delay:hook.apply(delay));
    }
    public void handleMessage(int what) {
        if(what==100001)updateNetworkSpeed();
        else if(what==100002){lastTime=lastTotalBytes=0;postUpdateNetworkSpeedDelay(0);}
    }
    private long getTotalByte(){reads++;return totalBytes;}
    private void updateNetworkSpeed(){
        if(!isConnected||!isSwitchOn){lastTime=lastTotalBytes=0;return;}
        if(isSuspend){lastTime=lastTotalBytes=0;return;}
        if(isPause)return;
        long bytes=getTotalByte();
        if(lastTime!=0&&clock>lastTime){reportedSpeed=(bytes-lastTotalBytes)*1000/(clock-lastTime);updates++;}
        lastTime=clock;lastTotalBytes=bytes;postUpdateNetworkSpeedDelay(4000);
    }
    public void advanceTo(long target,long bytesPerSecond) {
        while(nextAt>=0&&nextAt<=target){
            long due=nextAt;nextAt=-1;totalBytes+=(due-clock)*bytesPerSecond/1000;clock=due;handleMessage(100001);
        }
        totalBytes+=(target-clock)*bytesPerSecond/1000;clock=target;
    }
}
