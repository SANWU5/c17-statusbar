package com.oplus.systemui.statusbar.phone.netspeed;
/** Inspected OEM state; the original native view copies this when binding. */
public final class NetworkSpeedIconState {
    public boolean visible;public long speedText;
    public NetworkSpeedIconState copy(){NetworkSpeedIconState result=new NetworkSpeedIconState();result.visible=visible;result.speedText=speedText;return result;}
}
