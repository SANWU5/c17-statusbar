// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Typeface;
import android.graphics.fonts.FontVariationAxis;
import java.lang.reflect.Method;
import java.util.Collections;

/** A Compose loaded family must contain the real variation, not only Paint-side settings. */
final class NativeNetworkBadgeFont {
    private static final Method VARIATION=resolve();
    private static boolean unavailableLogged;
    private NativeNetworkBadgeFont(){ }
    private static Method resolve(){
        try{Method method=Typeface.class.getDeclaredMethod("createFromTypefaceWithVariation",Typeface.class,java.util.List.class);method.setAccessible(true);return method;}
        catch(ReflectiveOperationException|RuntimeException unsupported){return null;}
    }
    static Typeface axisFace(Typeface source,int weight){
        int bounded=Math.max(1,Math.min(1000,weight));
        Typeface face=FontWeight.typeface(source,bounded);
        if(VARIATION!=null)try{
            Object varied=VARIATION.invoke(null,face,Collections.singletonList(new FontVariationAxis("wght",bounded)));
            if(varied instanceof Typeface)return (Typeface)varied;
        }catch(ReflectiveOperationException|RuntimeException unsupported){logUnavailable(unsupported);}
        else logUnavailable(new NoSuchMethodException("Typeface.createFromTypefaceWithVariation"));
        return face;
    }
    private static synchronized void logUnavailable(Throwable failure){
        if(unavailableLogged)return;unavailableLogged=true;
        ModuleDiagnostics.error("native_badge_font","Exact variable font axis unavailable; keeping closest native style",failure);
    }
}
