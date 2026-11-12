// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.view.View;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Only framework/JDK types cross the native settings ABI; no private host classes are required. */
public final class CustomPluginNativeSettings {
    private CustomPluginNativeSettings(){}
    public static Method resolve(ClassLoader loader,String name)throws ReflectiveOperationException{
        Method create=Class.forName(name,false,loader).getMethod("createView",Context.class,String.class,InvocationHandler.class);
        if(!Modifier.isStatic(create.getModifiers())||!View.class.isAssignableFrom(create.getReturnType()))throw new IllegalArgumentException("插件原生设置入口必须为静态 createView 并返回 View");
        return create;
    }
    public static View create(Method entry,Context context,String state,InvocationHandler bridge)throws Throwable{
        Object result;try{result=entry.invoke(null,context,state,bridge);}catch(InvocationTargetException failure){throw failure.getCause();}
        if(!(result instanceof View)||((View)result).getParent()!=null)throw new IllegalArgumentException("插件设置必须返回未挂载的 View");
        return (View)result;
    }
}
