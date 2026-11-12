package io.github.libxposed.service;
/** Desktop listener shape only; a test must provide explicit service evidence. */
public final class XposedServiceHelper {
    public interface OnServiceListener {void onServiceBind(XposedService service);void onServiceDied(XposedService service);}
    public static void registerListener(OnServiceListener listener){}
}
