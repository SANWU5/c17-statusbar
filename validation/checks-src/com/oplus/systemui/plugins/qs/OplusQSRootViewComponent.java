package com.oplus.systemui.plugins.qs;
import android.view.View;
import com.oplus.systemui.plugins.qs.animator.QSPanelAnimatorManager;
import com.oplus.systemui.plugins.qs.seamless.SeparateQSFakeStatusController;
/** Exact field/getter names audited against the device's classes4.dex. */
public final class OplusQSRootViewComponent {
    public static final class Stub {
        public boolean keyguard, fail;
        public boolean isKeyguardShowing() {
            if (fail) throw new IllegalStateException("Native QS stub temporarily unavailable");
            return keyguard;
        }
    }
    public View mView;
    public SeparateQSFakeStatusController fakeStatusContainerController = new SeparateQSFakeStatusController();
    public float curRawFraction;
    public QSPanelAnimatorManager qsPanelAnimatorManager = new QSPanelAnimatorManager();
    public boolean isTracking, running;
    public Stub stub = new Stub();
    public boolean isFractionAnimationRunning() { return running; }
}
