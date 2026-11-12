// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Rect;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Worker-only source geometry shared by native drawing and library previews. */
final class IconAssetGeometry {
    interface Reader { byte[] read(IconManagementManifest.Asset asset)throws IOException; }
    static final class Frame {
        final Rect source,preview;final float width,height;
        Frame(Rect source,Rect preview,float width,float height){this.source=source;this.preview=preview;this.width=width;this.height=height;}
        boolean contains(IconVisibleBounds visible){return source.left<=visible.left&&source.top<=visible.top&&source.right>=visible.right&&source.bottom>=visible.bottom;}
    }
    private static String family(String sourceRole,String destinationRole){
        IconRoleRegistry.Role source=IconRoleRegistry.role(sourceRole),destination=IconRoleRegistry.role(destinationRole);
        if(source==null||destination==null||!source.targetId.equals(destination.targetId)
                ||!("wifi".equals(source.targetId)||"cellular".equals(source.targetId))
                ||!source.state.matches("[0-4]")||!destination.state.matches("[0-4]"))return null;
        return source.targetId;
    }
    static String cacheKey(String pack,String sourceRole,String destinationRole,IconAssetDecoder.Decoded decoded){
        String family=family(sourceRole,destinationRole);return family==null?null:pack+'/'+family+'/'+decoded.bitmap.getWidth()+'x'+decoded.bitmap.getHeight()+'/'+decoded.canvasWidth+'x'+decoded.canvasHeight;
    }
    static Frame single(IconAssetDecoder.Decoded decoded,String destinationRole){
        IconVisibleBounds visible=decoded.visibleBounds;
        if(!IconRoleRegistry.sharedSignalCanvas(destinationRole))return crop(new Rect(visible.left,visible.top,visible.right,visible.bottom));
        int width=decoded.bitmap.getWidth(),height=decoded.bitmap.getHeight();
        Rect preview=new Rect(Math.max(0,(int)Math.floor((width-decoded.canvasWidth)*.5f)),Math.max(0,(int)Math.floor((height-decoded.canvasHeight)*.5f)),
                Math.min(width,(int)Math.ceil((width+decoded.canvasWidth)*.5f)),Math.min(height,(int)Math.ceil((height+decoded.canvasHeight)*.5f)));
        return new Frame(new Rect(0,0,width,height),preview,decoded.canvasWidth,decoded.canvasHeight);
    }
    private static Frame crop(Rect rect){return new Frame(rect,rect,rect.width(),rect.height());}
    static Frame full(IconAssetDecoder.Decoded decoded){return crop(new Rect(0,0,decoded.bitmap.getWidth(),decoded.bitmap.getHeight()));}
    static Frame resolve(IconAssetDecoder.Decoded decoded,IconManagementManifest.Asset own,String sourceRole,String destinationRole,
                         IconManagementManifest manifest,int size,Reader reader){
        Frame single=single(decoded,destinationRole);String family=family(sourceRole,destinationRole);
        if(family==null||manifest==null)return single;
        Rect union=new Rect(single.source);Set<String> visited=new HashSet<>();visited.add(own.source);int compatible=1;boolean incomplete=false;
        // A dot/bar is a partial signal, not a smaller icon. Fit the union once so changing
        // native levels preserves its source coordinates. Never union different packs,
        // targets or canvas dimensions; custom unrelated artwork keeps its own geometry.
        for(int level=0;level<=4;level++){
            String role=IconManagementSelection.sourceRole(manifest,family+'.'+level);
            IconManagementManifest.Asset asset=role==null?null:manifest.icons.get(role);
            if(asset==null||!visited.add(asset.source))continue;
            IconAssetDecoder.Decoded peer=null;
            try{
                peer=IconAssetDecoder.decode(asset,reader.read(asset),size,size*size);
                if(peer.bitmap.getWidth()!=decoded.bitmap.getWidth()||peer.bitmap.getHeight()!=decoded.bitmap.getHeight()
                        ||Math.abs(peer.canvasWidth-decoded.canvasWidth)>.001f||Math.abs(peer.canvasHeight-decoded.canvasHeight)>.001f)continue;
                IconVisibleBounds visible=peer.visibleBounds;
                union.set(Math.min(union.left,visible.left),Math.min(union.top,visible.top),Math.max(union.right,visible.right),Math.max(union.bottom,visible.bottom));compatible++;
            }catch(IOException|RuntimeException unavailable){
                // A missing peer cannot replace the selected state or make its valid glyph fail.
                incomplete=true;
            }finally{if(peer!=null)peer.bitmap.recycle();}
        }
        // A peer may recover later in the same batch. Keep one safe family canvas
        // until the next resource refresh; a healthy preview can use the normal union.
        return incomplete?full(decoded):compatible>1?crop(union):single;
    }
    private IconAssetGeometry(){ }
}
