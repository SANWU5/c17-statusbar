package com.oplus.systemui.plugins.seedling.card.ui.view;
public final class CardBackgroundView extends android.view.View {
    public final android.content.res.Resources resources;
    public float blurAlpha = 1f;
    public CardBackgroundView(android.content.res.Resources resources) { super(null); this.resources = resources; }
    @Override public android.content.res.Resources getResources() { return resources; }
    public void setBlurRadiusFollowAlpha(float value) { blurAlpha = value; }
}
