// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Pixel geometry and one-shot drop decisions, independent of Compose and repository writes. */
public final class CustomPluginDragGeometry {
    public static final class Cell {
        public final String id;
        public final float left, top, width, height;
        public Cell(String id,float left,float top,float width,float height) {
            if(id==null||!finite(left)||!finite(top)||!finite(width)||!finite(height)||width<=0||height<=0)
                throw new IllegalArgumentException("Invalid drag cell");
            this.id=id;this.left=left;this.top=top;this.width=width;this.height=height;
        }
        boolean contains(float x,float y) {return x>=left&&x<=left+width&&y>=top&&y<=top+height;}
        float distance(float x,float y) {
            float dx=Math.max(Math.max(left-x,0),x-left-width),dy=Math.max(Math.max(top-y,0),y-top-height);
            return dx*dx+dy*dy;
        }
    }

    /** Keep the original slots stable during a drag; variable-height reflow cannot move the target. */
    public static final class Session {
        private List<String> original=Collections.emptyList();
        private String dragged, target;
        private float pointerX,pointerY,grabX,grabY,cardWidth,cardHeight,viewportWidth,viewportHeight;

        public boolean start(List<String> order,String id,Cell source,float x,float y,float width,float height) {
            cancel();
            if(!valid(order)||id==null||source==null||!id.equals(source.id)||!order.contains(id)
                    ||!finite(x)||!finite(y)||!finite(width)||!finite(height)||width<=0||height<=0
                    ||x<0||x>width||y<0||y>height||!source.contains(x,y))return false;
            original=Collections.unmodifiableList(new ArrayList<>(order));dragged=id;
            pointerX=x;pointerY=y;grabX=x-source.left;grabY=y-source.top;
            cardWidth=source.width;cardHeight=source.height;viewportWidth=width;viewportHeight=height;
            return true;
        }
        public void position(float x,float y) {
            if(dragged==null)return;
            if(!finite(x)||!finite(y)||x<0||x>viewportWidth||y<0||y>viewportHeight){cancel();return;}
            pointerX=x;pointerY=y;
        }
        public void layout(List<Cell> visible,float width,float height,float gap) {
            if(dragged==null)return;
            // A rotation/window resize invalidates both touch coordinates and the stable column slots.
            if(!finite(width)||!finite(height)||Math.abs(width-viewportWidth)>.5f||Math.abs(height-viewportHeight)>.5f){cancel();return;}
            target=null;
            if(visible==null||!finite(gap)||gap<0)return;
            float x=left()+cardWidth/2,y=top()+cardHeight/2,best=Float.MAX_VALUE;
            int bestRank=Integer.MAX_VALUE;
            for(Cell cell:visible) {
                int rank=original.indexOf(cell.id);
                if(rank<0)continue; // Lazy grid indices also include notices, sorting and footer rows.
                float distance=cell.distance(x,y);
                if(distance>Math.max(0,gap)*Math.max(0,gap))continue;
                if(distance<best||distance==best&&rank<bestRank) {best=distance;bestRank=rank;target=cell.id;}
            }
        }
        public boolean matches(List<String> order) {return dragged==null||original.equals(order);}
        public List<String> finish(List<String> current,boolean enabled) {
            List<String> result=null;
            if(enabled&&dragged!=null&&original.equals(current)&&target!=null&&!target.equals(dragged)) {
                List<String> next=new ArrayList<>(original);
                int destination=next.indexOf(target);
                next.remove(dragged);next.add(destination,dragged);
                result=Collections.unmodifiableList(next);
            }
            cancel();return result;
        }
        public float scrollSpeed(float edge,float maxPerSecond) {
            if(dragged==null)return 0;
            return edgeSpeed(pointerY,viewportHeight,edge,maxPerSecond);
        }
        public void cancel() {dragged=null;target=null;original=Collections.emptyList();}
        public String dragged() {return dragged;}
        public String target() {return dragged!=null&&!dragged.equals(target)?target:null;}
        public float left() {return pointerX-grabX;}
        public float top() {return pointerY-grabY;}
        public float width() {return cardWidth;}
        public float height() {return cardHeight;}
    }

    public static float edgeSpeed(float y,float height,float edge,float maxPerSecond) {
        if(!finite(y)||!finite(height)||!finite(edge)||!finite(maxPerSecond)||height<=0||edge<=0||maxPerSecond<=0||y<0||y>height)return 0;
        float band=Math.min(edge,height/3);
        if(y<band)return -maxPerSecond*(band-y)/band;
        if(y>height-band)return maxPerSecond*(y-height+band)/band;
        return 0;
    }
    private static boolean valid(List<String> order) {
        return order!=null&&!order.contains(null)&&new HashSet<>(order).size()==order.size();
    }
    private static boolean finite(float value) {return !Float.isNaN(value)&&!Float.isInfinite(value);}
    private CustomPluginDragGeometry() { }
}
