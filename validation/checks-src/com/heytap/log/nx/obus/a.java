package com.heytap.log.nx.obus;
/** Simulates the intercepted native BackgroundUtils without implementing OEM blur. */
public final class a {
    public static dev.puitheme.FluidCloudAccent controller;
    public static int builds, mixed, gradient;
    public a(android.content.Context context, int ignored) { }
    public void b(android.view.View host, com.oplus.view.ViewRootManager manager) {
        dev.puitheme.FluidCloudAccent.BackgroundScope scope = controller.beginBackground(this, host, manager);
        try {
            builds++;
            mixed = controller.resourceColor(host.getResources(), 1, 0xb3262626);
            gradient = controller.resourceColor(host.getResources(), 2, 0xff000000);
            if (scope != null) scope.completed();
        } finally { if (scope != null) scope.close(); }
    }
}
