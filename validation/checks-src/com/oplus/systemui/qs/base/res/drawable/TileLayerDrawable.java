package com.oplus.systemui.qs.base.res.drawable;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import com.oplusos.systemui.common.outline.CornerOutlineProvider;
public class TileLayerDrawable extends Drawable {
    public static final class Delegate {
        private CornerOutlineProvider pathProvider,blockPathProvider;
        Delegate(CornerOutlineProvider provider){pathProvider=provider;}
    }
    private final Delegate delegate;
    public CornerOutlineProvider childProvider;
    public int setters,pathUpdates,failSetAfter,failPath;
    public int alpha=173,color=0xff123456;
    public final Object shader=new Object(),glass=new Object();
    public TileLayerDrawable(CornerOutlineProvider provider){delegate=new Delegate(provider);childProvider=provider;setBounds(0,0,240,120);}
    public CornerOutlineProvider getPathProvider(){return delegate.blockPathProvider!=null?delegate.blockPathProvider:delegate.pathProvider;}
    public void setPathProvider(CornerOutlineProvider next){
        setters++;delegate.pathProvider=next;
        if(failSetAfter>0){failSetAfter--;throw new IllegalStateException("partial setter");}
    }
    public void invalidatePath(){
        pathUpdates++;
        if(failPath>0){failPath--;throw new IllegalStateException("path update");}
        childProvider=getPathProvider();
    }
    public void setBlockPathProvider(CornerOutlineProvider block){delegate.blockPathProvider=block;invalidatePath();}
    public void draw(Canvas canvas){}
    public void setAlpha(int value){alpha=value;}
    public void setColorFilter(ColorFilter filter){}
    public int getOpacity(){return -3;}
}
