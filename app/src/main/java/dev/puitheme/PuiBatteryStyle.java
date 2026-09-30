// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Exact horizontal assets extracted from PUI Theme For OPlus17, inside the native battery renderer. */
public final class PuiBatteryStyle {
    private static final String PACKAGE="dev.puitheme.iosstatusbar";
    private final Field[] shapes;
    private final Field insideRect,outsideRect,percentPaint,progress,background,outline,level;
    private final Method context,tint,measureText;
    private final Map<Drawable,Entry> entries=new WeakHashMap<>();
    private final Map<ImageView,Dimensions> sizes=new WeakHashMap<>();
    private Resources resources;
    private boolean enabled=true;

    private static final class Entry {
        final Drawable[] nativeShapes=new Drawable[5],puiShapes=new Drawable[5];
        final int[] colors=new int[5];
        final boolean[] tinted=new boolean[5];
        float nativeTextSize,appliedTextSize;
    }
    private static final class Dimensions {
        int nativeWidth,nativeHeight,appliedWidth,appliedHeight;
    }

    public PuiBatteryStyle(Class<?> horizontal) {
        Field[] slots=null;Field in=null,out=null,paint=null,fill=null,bg=null,edge=null,power=null;Method ctx=null,colors=null,measure=null;
        try {
            Class<?> bar=horizontal.getSuperclass();
            slots=new Field[]{field(bar,"outsideDrawable"),field(bar,"insideDrawable"),field(bar,"frameDrawable"),
                    field(horizontal,"bgDrawable"),field(horizontal,"progressDrawable")};
            in=field(bar,"rect");out=field(horizontal,"noPercentRectF");paint=field(horizontal,"percentInPaint");
            fill=field(bar,"progressColor");bg=field(bar,"backgroundColor");edge=field(bar,"outlineColor");
            power=field(bar,"batteryLevel");ctx=horizontal.getMethod("getContext");
            colors=horizontal.getMethod("updateDrawableTint");
            measure=horizontal.getMethod("updatePercentInTextBounds");
        } catch(ReflectiveOperationException unsupported) {slots=null;}
        shapes=slots;insideRect=in;outsideRect=out;percentPaint=paint;progress=fill;background=bg;outline=edge;
        level=power;context=ctx;tint=colors;measureText=measure;
    }

    private static Field field(Class<?> owner,String name) throws ReflectiveOperationException {
        Field result=owner.getDeclaredField(name);result.setAccessible(true);return result;
    }

    public void configure(Bundle settings) {
        enabled=FeatureOptions.from(settings).effective("battery","battery_style_enabled")
                &&"pui".equals(settings.getString(StatusBarSettings.BATTERY_STYLE,"pui"));
        if(!enabled) {
            for(Drawable drawable:entries.keySet().toArray(new Drawable[0]))restore(drawable);
            for(ImageView image:sizes.keySet().toArray(new ImageView[0]))restoreSize(image);
        }
    }

    private Resources resources(Drawable drawable) throws Exception {
        if(resources==null) {
            Context nativeContext=(Context)context.invoke(drawable);
            resources=nativeContext.createPackageContext(PACKAGE,Context.CONTEXT_IGNORE_SECURITY).getResources();
        }
        return resources;
    }

    private Drawable asset(Resources source,String name) {
        int id=source.getIdentifier(name,"drawable",PACKAGE);
        if(id==0)throw new Resources.NotFoundException("Missing PUI battery asset: "+name);
        return source.getDrawable(id,null).mutate();
    }

    private LayerDrawable progress(Drawable graphic) {
        ClipDrawable clip=new ClipDrawable(graphic,Gravity.LEFT,ClipDrawable.HORIZONTAL);
        LayerDrawable layers=new LayerDrawable(new Drawable[]{clip});
        layers.setId(0,android.R.id.progress);
        return layers;
    }

    /** Native update methods may replace their fields, so capture the latest native version before each patch. */
    public boolean prepare(Drawable drawable) throws Exception {
        if(shapes==null)return false;
        if(!enabled){restore(drawable);return false;}
        Entry entry=entries.get(drawable);
        if(entry==null) {
            Resources source=resources(drawable);entry=new Entry();
            entry.puiShapes[0]=asset(source,"c17_pui_battery_bg");
            entry.puiShapes[1]=progress(asset(source,"c17_pui_battery_bg"));
            entry.puiShapes[2]=asset(source,"c17_pui_battery_frame");
            entry.puiShapes[3]=asset(source,"c17_pui_battery_outer_bg");
            entry.puiShapes[4]=progress(asset(source,"c17_pui_battery_outer_fill"));
            entry.nativeTextSize=((Paint)percentPaint.get(drawable)).getTextSize();entries.put(drawable,entry);
        }
        RectF in=(RectF)insideRect.get(drawable),out=(RectF)outsideRect.get(drawable);
        int fill=progress.getInt(drawable),bg=background.getInt(drawable),edge=outline.getInt(drawable);
        int power=Math.max(0,Math.min(100,level.getInt(drawable)))*100;
        for(int index=0;index<shapes.length;index++) {
            Drawable current=(Drawable)shapes[index].get(drawable),replacement=entry.puiShapes[index];
            if(current!=replacement)entry.nativeShapes[index]=current;
            RectF rect=index<3?in:out;
            if(rect!=null)replacement.setBounds((int)rect.left,(int)rect.top,(int)rect.right,(int)rect.bottom);
            int color=index==1||index==4?fill:index==2||index==3?edge:bg;
            if(!entry.tinted[index]||entry.colors[index]!=color) {
                replacement.setTint(color);entry.colors[index]=color;entry.tinted[index]=true;
            }
            if((index==1||index==4)&&replacement.getLevel()!=power)replacement.setLevel(power);
            if(current!=replacement)shapes[index].set(drawable,replacement);
        }
        Paint paint=(Paint)percentPaint.get(drawable);
        if(entry.appliedTextSize!=0&&paint.getTextSize()!=entry.appliedTextSize)entry.nativeTextSize=paint.getTextSize();
        Context nativeContext=(Context)context.invoke(drawable);
        entry.appliedTextSize=10f*nativeContext.getResources().getDisplayMetrics().density;
        if(paint.getTextSize()!=entry.appliedTextSize){paint.setTextSize(entry.appliedTextSize);measureText.invoke(drawable);}
        return true;
    }

    public void updateView(View owner,Drawable drawable) throws Exception {
        if(shapes==null)return;
        ImageView image=findImage(owner,drawable,0);
        if(image==null)return;
        if(!enabled){restoreSize(image);return;}
        ViewGroup.LayoutParams params=image.getLayoutParams();
        if(params==null||params.width<=0||params.height<=0)return;
        Dimensions dimensions=sizes.get(image);
        if(dimensions==null) {
            dimensions=new Dimensions();dimensions.nativeWidth=params.width;dimensions.nativeHeight=params.height;
            sizes.put(image,dimensions);
        } else if(params.width!=dimensions.appliedWidth||params.height!=dimensions.appliedHeight) {
            dimensions.nativeWidth=params.width;dimensions.nativeHeight=params.height;
        }
        Resources nativeResources=owner.getResources();float density=nativeResources.getDisplayMetrics().density;
        int nativeWidthId=nativeResources.getIdentifier("status_bar_battery_horizontal_width","dimen","com.android.systemui");
        float nativeWidth=nativeWidthId==0?29f*density:nativeResources.getDimension(nativeWidthId);
        float factor=dimensions.nativeWidth/Math.max(1f,nativeWidth);
        dimensions.appliedWidth=Math.max(1,Math.round(29f*density*factor));
        dimensions.appliedHeight=Math.max(1,Math.round(17f*density*factor));
        if(params.width!=dimensions.appliedWidth||params.height!=dimensions.appliedHeight) {
            params.width=dimensions.appliedWidth;params.height=dimensions.appliedHeight;image.setLayoutParams(params);
        }
    }

    private static ImageView findImage(View view,Drawable drawable,int depth) {
        if(view instanceof ImageView&&((ImageView)view).getDrawable()==drawable)return (ImageView)view;
        if(depth<8&&view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int index=0;index<group.getChildCount();index++) {
                ImageView found=findImage(group.getChildAt(index),drawable,depth+1);if(found!=null)return found;
            }
        }
        return null;
    }

    private void restoreSize(ImageView image) {
        Dimensions dimensions=sizes.remove(image);if(dimensions==null)return;
        ViewGroup.LayoutParams params=image.getLayoutParams();if(params==null)return;
        if(params.width==dimensions.appliedWidth&&params.height==dimensions.appliedHeight) {
            params.width=dimensions.nativeWidth;params.height=dimensions.nativeHeight;image.setLayoutParams(params);
        }
    }

    public void restore(Drawable drawable) {
        Entry entry=entries.remove(drawable);if(entry==null)return;
        try {
            for(int index=0;index<shapes.length;index++)
                if(shapes[index].get(drawable)==entry.puiShapes[index])shapes[index].set(drawable,entry.nativeShapes[index]);
            Paint paint=(Paint)percentPaint.get(drawable);
            if(paint.getTextSize()==entry.appliedTextSize&&paint.getTextSize()!=entry.nativeTextSize) {
                paint.setTextSize(entry.nativeTextSize);measureText.invoke(drawable);
            }
            tint.invoke(drawable);drawable.invalidateSelf();
        } catch(ReflectiveOperationException ignored) { }
    }

    public void detach(View owner,Drawable drawable) {
        if(drawable!=null)restore(drawable);
        for(ImageView image:sizes.keySet().toArray(new ImageView[0])) {
            View current=image;
            for(int depth=0;current!=null&&depth<12;depth++) {
                if(current==owner){restoreSize(image);break;}
                current=current.getParent() instanceof View?(View)current.getParent():null;
            }
        }
    }
}
