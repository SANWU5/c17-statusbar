// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Small vector glyphs for the settings UI; no bitmap assets or icon fonts. */
public final class UiGlyph extends View {
    private final String key;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public UiGlyph(Context context, String key, int color) {
        super(context); this.key=key; paint.setColor(color);
        paint.setStrokeWidth(1.7f); paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float availableWidth=getWidth()-getPaddingLeft()-getPaddingRight(),availableHeight=getHeight()-getPaddingTop()-getPaddingBottom();
        float unit=Math.min(availableWidth,availableHeight)/24f;
        if(unit<=0)return;
        int saved=canvas.save();canvas.translate(getPaddingLeft()+(availableWidth-24*unit)/2f,getPaddingTop()+(availableHeight-24*unit)/2f);canvas.scale(unit,unit);
        paint.setStyle(Paint.Style.STROKE);
        switch(key) {
            case "bell":
                canvas.drawArc(new RectF(6,4,18,17),180,180,false,paint);
                canvas.drawLine(6,10,5,18,paint);canvas.drawLine(18,10,19,18,paint);
                canvas.drawLine(5,18,19,18,paint);canvas.drawArc(new RectF(10,18,14,22),0,180,false,paint);break;
            case "notification_clear":
                canvas.drawLine(5,6,19,6,paint);canvas.drawLine(9,3,15,3,paint);
                canvas.drawRoundRect(new RectF(7,6,17,21),2,2,paint);canvas.drawLine(10,10,10,17,paint);canvas.drawLine(14,10,14,17,paint);break;
            case "qs_media":
                canvas.drawRoundRect(new RectF(3,4,21,20),4,4,paint);
                paint.setStyle(Paint.Style.FILL);Path play=new Path();play.moveTo(10,8);play.lineTo(17,12);play.lineTo(10,16);play.close();canvas.drawPath(play,paint);break;
            case "speed": {
                canvas.drawArc(new RectF(3,4,21,22),160,220,false,paint);canvas.drawLine(12,14,17,7,paint);break;
            }
            case "data":
                paint.setStyle(Paint.Style.FILL);
                for(int i=0;i<4;i++)canvas.drawRoundRect(new RectF(3+i*5,16-i*3,6+i*5,21),1.2f,1.2f,paint);
                break;
            case "wifi":
                canvas.drawArc(new RectF(2,5,22,25),222,96,false,paint);
                canvas.drawArc(new RectF(6,10,18,22),222,96,false,paint);
                paint.setStyle(Paint.Style.FILL);canvas.drawCircle(12,18,1.6f,paint);break;
            case "clock":
                canvas.drawCircle(12,12,9,paint);canvas.drawLine(12,7,12,12,paint);canvas.drawLine(12,12,16,14,paint);break;
            case "carrier": {
                canvas.drawRoundRect(new RectF(3,4,21,17),4,4,paint);
                Path p=new Path();p.moveTo(8,17);p.lineTo(6,21);p.lineTo(13,17);canvas.drawPath(p,paint);
                canvas.drawLine(7,9,17,9,paint);canvas.drawLine(7,12,14,12,paint);break;
            }
            case "battery":
                canvas.drawRoundRect(new RectF(2,7,20,17),3,3,paint);canvas.drawLine(22,10,22,14,paint);
                paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(new RectF(5,10,14,14),1,1,paint);break;
            case "tiles":
                canvas.drawRoundRect(new RectF(4,3,17,18),3,3,paint);
                canvas.drawLine(20,7,20,19,paint);canvas.drawLine(8,21,17,21,paint);break;
            case "qs_appearance":
                canvas.drawCircle(8,8,4,paint);canvas.drawCircle(17,10,4,paint);canvas.drawCircle(11,17,4,paint);break;
            default:
                paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
                paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(key.equals("brand")?11:12);
                canvas.drawText(key.equals("label")?"5G":key.equals("brand")?"C17":"Aa",12,16,paint);
        }
        canvas.restoreToCount(saved);
    }
}
