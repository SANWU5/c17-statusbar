package dev.puitheme;

import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Only the native detail frame containing the free-edit More content owns this plate.
 * The plugin marks its own content by type; no native tags, backgrounds or layouts change. */
final class QsMoreMaterial {
    private static final String FRAME="com.oplus.systemui.qs.base.widget.MaxHeightAndWidthLinearLayout";
    private static final String CONTENT="dev.c17.plugins.freeedit.QsMoreWindow$Panel";
    private static final Map<View,WeakReference<View>> owners=Collections.synchronizedMap(new WeakHashMap<>());

    static boolean nativeFrame(View view){return view!=null&&FRAME.equals(view.getClass().getName());}
    static boolean owns(View view){
        if(!nativeFrame(view)||!(view instanceof ViewGroup))return false;
        WeakReference<View> ref=owners.get(view);View content=ref==null?null:ref.get();
        if(content!=null&&ancestor(content,view))return true;
        owners.remove(view);content=find((ViewGroup)view,3);
        if(content==null)return false;
        owners.put(view,new WeakReference<>(content));return true;
    }
    private static boolean ancestor(View child,View frame){
        for(int i=0;i<3&&child.getParent() instanceof View;i++){
            child=(View)child.getParent();if(child==frame)return true;
        }
        return false;
    }
    private static View find(ViewGroup parent,int depth){
        if(depth==0||parent.getChildCount()>12)return null;
        for(int i=0;i<parent.getChildCount();i++){
            View child=parent.getChildAt(i);
            if(CONTENT.equals(child.getClass().getName()))return child;
            if(child instanceof ViewGroup){View found=find((ViewGroup)child,depth-1);if(found!=null)return found;}
        }
        return null;
    }
}
