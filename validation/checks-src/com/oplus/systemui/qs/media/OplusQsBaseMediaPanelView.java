package com.oplus.systemui.qs.media;
import android.content.Context;
import android.view.View;
import com.oplus.systemui.qs.media.multilight.OplusQsMediaBackgroundDrawable;
import com.oplusos.systemui.common.outline.CornerOutlineProvider;
public class OplusQsBaseMediaPanelView extends View {
    public int bodyWidth=240,bodyHeight=120,measuredWidth=-1,measuredHeight=-1;
    public final View body=new View(new Context()){
        public int getWidth(){return bodyWidth;}
        public int getHeight(){return bodyHeight;}
        public int getMeasuredWidth(){return measuredWidth>=0?measuredWidth:bodyWidth;}
        public int getMeasuredHeight(){return measuredHeight>=0?measuredHeight:bodyHeight;}
    };
    public OplusQsMediaBackgroundDrawable transition;
    public OplusQsBaseMediaPanelView(){super(new Context());transition=new OplusQsMediaBackgroundDrawable(new CornerOutlineProvider(56f,.8f));}
    public View getBg(){return body;}
    public OplusQsMediaBackgroundDrawable getTransitionDrawable(){return transition;}
}
