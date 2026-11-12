// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Temporary native-leaf appearance. A LayerDrawable keeps its own draw order,
 * bounds and child-specific alpha/filter; text and sibling glyphs never enter. */
final class IconNativeAppearance {
    private static final int MAX_NODES=128,MAX_DEPTH=24;
    private static final ThreadLocal<Map<Drawable,Tree>> TREES=ThreadLocal.withInitial(WeakHashMap::new);
    private IconNativeAppearance(){ }

    static void draw(Drawable drawable,Canvas canvas,IconManagementSelection.ColorPlan style){draw(drawable,canvas,style,0xffffffff);}
    static void draw(Drawable drawable,Canvas canvas,IconManagementSelection.ColorPlan style,int nativeTint){
        draw(drawable,canvas,style,nativeTint,false);
    }
    static void draw(Drawable drawable,Canvas canvas,IconManagementSelection.ColorPlan style,int nativeTint,boolean hostScalePlaced){
        if(drawable==null||canvas==null)return;
        if(!IconManagementRuntime.enterNative(drawable)){drawable.draw(canvas);return;}
        Tree tree=null;Throwable drawingFailure=null;int canvasSave=-1;
        try{
            Map<Drawable,Tree> cache=TREES.get();tree=cache.get(drawable);
            if(tree==null||!tree.matches()){tree=new Tree(drawable);cache.put(drawable,tree);}
            if(tree.safe){tree.capture(drawable);tree.apply(style,nativeTint);}
            if(!hostScalePlaced&&style.scale!=1f){canvasSave=canvas.save();android.graphics.Rect bounds=drawable.getBounds();canvas.scale(style.scale,style.scale,bounds.exactCenterX(),bounds.exactCenterY());}
            drawable.draw(canvas);
        }catch(RuntimeException|Error failure){drawingFailure=failure;throw failure;}
        finally{
            try{if(canvasSave>=0)canvas.restoreToCount(canvasSave);}finally{
                RuntimeException restoreFailure=tree==null?null:tree.restore();IconManagementRuntime.exitNative(drawable);
                if(restoreFailure!=null){if(drawingFailure==null)throw restoreFailure;drawingFailure.addSuppressed(restoreFailure);}
            }
        }
    }
    static void releaseRuntime(){TREES.remove();}

    private static final class Node {
        // Every cached drawable reference is weak, including layer children.
        // A strong Tree value cannot keep its WeakHashMap key alive.
        final WeakReference<Drawable> reference;
        final Node[] children;
        Drawable current;ColorFilter filter;int alpha;
        boolean entered,skip,alphaChanged,filterChanged;
        Node(Drawable drawable,Node[] children){reference=new WeakReference<>(drawable);this.children=children;}
        boolean matches(){
            Drawable drawable=reference.get();if(drawable==null)return false;
            if(children!=null){
                if(drawable instanceof LayerDrawable){LayerDrawable layer=(LayerDrawable)drawable;if(layer.getNumberOfLayers()!=children.length)return false;
                    for(int i=0;i<children.length;i++)if(layer.getDrawable(i)!=(children[i]==null?null:children[i].reference.get()))return false;}
                else if(((DrawableWrapper)drawable).getDrawable()!=(children[0]==null?null:children[0].reference.get()))return false;
            }
            return true;
        }
    }
    private static final class Builder {
        final IdentityHashMap<Drawable,Node> nodes=new IdentityHashMap<>();
        final IdentityHashMap<Drawable,Boolean> visiting=new IdentityHashMap<>();
        final ArrayList<Node> order=new ArrayList<>();boolean safe=true;
        Node build(Drawable drawable,int depth){
            if(drawable==null||depth>MAX_DEPTH||nodes.size()>=MAX_NODES){safe=false;return null;}
            if(visiting.containsKey(drawable)){safe=false;return null;}
            Node existing=nodes.get(drawable);if(existing!=null)return existing;
            // DrawableContainer may animate and overwrite child alpha inside
            // draw(). Keep its native implementation until explicitly audited.
            if(drawable instanceof DrawableContainer||drawable instanceof RippleDrawable)safe=false;
            visiting.put(drawable,true);Node[] children=null;
            if(drawable instanceof LayerDrawable){LayerDrawable layer=(LayerDrawable)drawable;int count=layer.getNumberOfLayers();
                if(count>MAX_NODES){safe=false;count=0;}children=new Node[count];
                for(int i=0;i<count;i++){children[i]=build(layer.getDrawable(i),depth+1);if(children[i]==null)safe=false;}}
            else if(drawable instanceof DrawableWrapper){children=new Node[]{build(((DrawableWrapper)drawable).getDrawable(),depth+1)};if(children[0]==null)safe=false;}
            visiting.remove(drawable);Node node=new Node(drawable,children);nodes.put(drawable,node);order.add(node);return node;
        }
    }
    private static final class Tree {
        final Node[] nodes;final boolean safe;
        IconManagementSelection.ColorPlan planned;ColorFilter replacement;
        Tree(Drawable drawable){Builder builder=new Builder();builder.build(drawable,0);nodes=builder.order.toArray(new Node[0]);safe=builder.safe;}
        boolean matches(){for(Node node:nodes)if(!node.matches())return false;return true;}
        void capture(Drawable root){
            for(Node node:nodes){Drawable drawable=node.reference.get();node.current=drawable;
                if(drawable==null)continue;
                if(drawable!=root){node.entered=IconManagementRuntime.enterNative(drawable);node.skip=!node.entered;}
                if(node.children==null&&!node.skip){node.alpha=drawable.getAlpha();node.filter=drawable.getColorFilter();}}
        }
        void apply(IconManagementSelection.ColorPlan style,int nativeTint){
            boolean custom="custom".equals(style.mode);
            if(planned!=style){planned=style;replacement=custom?IconManagementRuntime.nativeTintFilter(style.color):null;}
            for(Node node:nodes){Drawable drawable=node.current;if(drawable==null||node.children!=null||node.skip)continue;
                float opacity=style.alpha;
                if(custom){Integer original=IconManagementRuntime.filterColor(node.filter);
                    if(node.filter==null||original!=null){node.filterChanged=node.filter!=replacement;
                        if(node.filterChanged)drawable.setColorFilter(replacement);
                        // An explicit SRC_IN filter replaces the native tint.
                        // Preserve its alpha once; do not also multiply tintA.
                        opacity*=((original==null?nativeTint:original)>>>24)/255f;}}
                int alpha=Math.round(node.alpha*opacity);node.alphaChanged=alpha!=node.alpha;if(node.alphaChanged)drawable.setAlpha(alpha);
            }
        }
        RuntimeException restore(){RuntimeException failed=null;
            for(int i=nodes.length-1;i>=0;i--){Node node=nodes[i];Drawable drawable=node.current;
                try{if(drawable!=null&&node.alphaChanged)drawable.setAlpha(node.alpha);}
                catch(RuntimeException failure){if(failed==null)failed=failure;}
                try{if(drawable!=null&&node.filterChanged)drawable.setColorFilter(node.filter);}
                catch(RuntimeException failure){if(failed==null)failed=failure;}
                finally{if(drawable!=null&&node.entered)IconManagementRuntime.exitNative(drawable);node.current=null;node.filter=null;node.entered=node.skip=node.alphaChanged=node.filterChanged=false;}
            }return failed;
        }
    }
}
