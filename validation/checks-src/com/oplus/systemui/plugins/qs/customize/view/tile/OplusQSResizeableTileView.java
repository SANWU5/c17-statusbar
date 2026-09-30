package com.oplus.systemui.plugins.qs.customize.view.tile;
import android.view.View;
import android.content.Context;
import android.graphics.drawable.Drawable;
public class OplusQSResizeableTileView extends View {
    public Object state;public Drawable bg,theme;public View bgView;public Object icon;
    public OplusQSResizeableTileView(){super(new Context());}
    public Object getTileState(){return state;}
    public Drawable getBgDrawable(){return bg;}
    public Drawable getThemeDrawable(){return theme;}
    public View getBg(){return bgView;}
    public Object getIconView(){return icon;}
}
