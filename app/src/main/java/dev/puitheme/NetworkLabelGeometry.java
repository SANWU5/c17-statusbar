// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/** One measured text footprint for the Compose label and native single-SIM span. */
final class NetworkLabelGeometry {
    static final class Plan {
        final int width,height;
        final float scale;
        final Rect glyph;
        Plan(int width,int height,float scale,Rect glyph){this.width=width;this.height=height;this.scale=scale;this.glyph=new Rect(glyph);}
        void draw(Canvas canvas,String label,Paint paint,float centerX,float centerY){
            draw(canvas,label,paint,centerX,centerY,scale);
        }
        /** Y remains on the real View, so its native text/content clip scales with the glyph. */
        void drawHorizontal(Canvas canvas,String label,Paint paint,float centerX,float centerY){
            draw(canvas,label,paint,centerX,centerY,1f);
        }
        private void draw(Canvas canvas,String label,Paint paint,float centerX,float centerY,float verticalScale){
            if(label.isEmpty()||scale==0f)return;
            int saved=canvas.save();
            try{canvas.translate(centerX,centerY);canvas.scale(scale,verticalScale);canvas.drawText(label,-glyph.exactCenterX(),-glyph.exactCenterY(),paint);}
            finally{canvas.restoreToCount(saved);}
        }
    }
    static Plan measure(Paint paint,String label,float density,float scalePercent){
        return measure(paint,label,density,scalePercent,null,new Rect());
    }
    /** Retained for old callers only. A saved manual reserve never changes the text footprint. */
    static Plan measure(Paint paint,String label,float density,float scalePercent,float ignoredMinimumWidthDp){
        return measure(paint,label,density,scalePercent);
    }
    static Plan measure(Paint paint,String label,float density,float scalePercent,float ignoredMinimumWidthDp,Plan previous,Rect scratch){
        return measure(paint,label,density,scalePercent,previous,scratch);
    }
    /** The real view reuses its scratch bounds and immutable plan on stable draw frames. */
    static Plan measure(Paint paint,String label,float density,float scalePercent,Plan previous,Rect scratch){
        String text=label==null?"":label;
        float d=Float.isFinite(density)&&density>0f?density:1f;
        paint.getTextBounds(text,0,text.length(),scratch);
        double advance=paint.measureText(text);if(!Double.isFinite(advance)||advance<0d)advance=0d;
        double inkWidth=Math.max(0L,(long)scratch.right-scratch.left),inkHeight=Math.max(0L,(long)scratch.bottom-scratch.top);
        double natural=Math.max(advance,inkWidth)+2d*d;
        float requested=Math.max(0f,NumericPolicy.finite(scalePercent,100f))/100f;
        // Budget the visible extent before storing the layout size. Merely clamping
        // width while retaining an unbounded drawing scale would still overlap peers.
        double limit=Math.min(NumericPolicy.MAX_LAYOUT_PIXELS/Math.max(1d,natural),
                NumericPolicy.MAX_DRAW_PIXELS/Math.max(1d,Math.max(natural,inkHeight)));
        float cap=(float)limit;if(cap>limit)cap=Math.nextDown(cap);
        float scale=Math.min(requested,cap);
        // One density pixel of breathing room on either side follows the glyph's
        // scale. There is no fixed slot or user reserve to compete with its peers.
        int width=text.isEmpty()?0:NumericPolicy.layoutPixels(Math.ceil(natural*scale));
        int height=NumericPolicy.layoutPixels(20d*d);
        if(previous!=null&&previous.width==width&&previous.height==height&&previous.scale==scale&&previous.glyph.equals(scratch))return previous;
        return new Plan(width,height,scale,scratch);
    }
    private NetworkLabelGeometry(){ }
}
