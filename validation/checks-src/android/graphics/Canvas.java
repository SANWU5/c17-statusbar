package android.graphics;
public class Canvas {
    public boolean contrast;
    public boolean hardware,failMaskOnce,failClipOnce,failLayerOnce,failFenceOnce;
    public int nodeDraws,layers,rects,clears;
    public String failNodeOnce;
    public Shader lastShader;
    public Shader lastEffectShader,lastFenceShader;
    public Paint lastLayerPaint;
    public float clipLeft,clipTop,clipRight,clipBottom,layerLeft,layerTop,layerRight,layerBottom;
    public float effectiveClipLeft,effectiveClipTop,effectiveClipRight,effectiveClipBottom;
    public PorterDuff.Mode rectMode;
    public int rectAlpha;
    public int effectAlpha;
    public int effectRects,fenceRects;
    public Rect clipBounds=new Rect(0,0,1000000,1000000);
    private int saveCount=1;
    public int textAlpha=-1,pathAlpha=-1,saves,restores;
    public int maskAlpha=-1,pathColor,textColor,clips;
    public Paint.Style maskStyle;
    public float maskStroke;
    public PorterDuff.Mode foregroundMode;
    public float translateX,translateY,scaleX=1f,scaleY=1f,pivotX,pivotY;
    public String text;
    public float textX,textY,textSize,textSpacing,textEffectiveWeight;
    public Typeface textTypeface;
    public int save() {saves++;return saveCount++;}
    public void restoreToCount(int count) {restores++;saveCount=count;}
    public int getSaveCount() {return saveCount;}
    public int saveLayer(float left,float top,float right,float bottom,Paint paint) {if(failLayerOnce){failLayerOnce=false;throw new IllegalStateException("layer unavailable");}layers++;layerLeft=left;layerTop=top;layerRight=right;layerBottom=bottom;return save();}
    public int saveLayer(RectF bounds,Paint paint){lastLayerPaint=paint;return bounds==null
            ?saveLayer(clipBounds.left,clipBounds.top,clipBounds.right,clipBounds.bottom,paint)
            :saveLayer(bounds.left,bounds.top,bounds.right,bounds.bottom,paint);}
    public int saveLayerAlpha(RectF bounds,int alpha){layers++;layerLeft=bounds.left;layerTop=bounds.top;layerRight=bounds.right;layerBottom=bounds.bottom;return save();}
    public boolean isHardwareAccelerated() {return hardware;}
    public boolean getClipBounds(Rect rect) {rect.left=clipBounds.left;rect.top=clipBounds.top;rect.right=clipBounds.right;rect.bottom=clipBounds.bottom;return true;}
    public boolean clipRect(float left,float top,float right,float bottom) {
        if(failClipOnce){failClipOnce=false;throw new IllegalStateException("clip unavailable");}
        clips++;clipLeft=left;clipTop=top;clipRight=right;clipBottom=bottom;
        effectiveClipLeft=Math.max(left,clipBounds.left-translateX);effectiveClipTop=Math.max(top,clipBounds.top-translateY);
        effectiveClipRight=Math.min(right,clipBounds.right-translateX);effectiveClipBottom=Math.min(bottom,clipBounds.bottom-translateY);
        return true;
    }
    public void drawRect(float left,float top,float right,float bottom,Paint paint) {
        if(failMaskOnce){failMaskOnce=false;throw new IllegalStateException("mask unavailable");}
        PorterDuff.Mode mode=paint.mode instanceof PorterDuffXfermode?((PorterDuffXfermode)paint.mode).mode:null;
        boolean isFence=paint.shader instanceof LinearGradient&&((LinearGradient)paint.shader).left!=((LinearGradient)paint.shader).right&&mode==PorterDuff.Mode.DST_IN;
        if(isFence&&failFenceOnce){failFenceOnce=false;throw new IllegalStateException("fence unavailable");}
        rects++;lastShader=paint.shader;rectAlpha=paint.getAlpha();
        rectMode=paint.mode instanceof PorterDuffXfermode?((PorterDuffXfermode)paint.mode).mode:null;
        if(isFence){lastFenceShader=paint.shader;fenceRects++;}
        else if(paint.shader!=null){lastEffectShader=paint.shader;effectAlpha=paint.getAlpha();effectRects++;}
    }
    public void drawColor(int color,PorterDuff.Mode mode) {clears++;}
    public void drawRenderNode(RenderNode node) {
        nodeDraws++;if(failNodeOnce!=null&&node.name.contains(failNodeOnce)){failNodeOnce=null;throw new IllegalStateException("node unavailable");}
    }
    public boolean clipRect(RectF rect) {clips++;return true;}
    public boolean isHighContrastTextEnabled() {return contrast;}
    public void translate(float x,float y) {translateX+=x;translateY+=y;}
    public void scale(float x,float y,float px,float py) {scaleX*=x;scaleY*=y;pivotX=px;pivotY=py;}
    public void scale(float x,float y) {scaleX*=x;scaleY*=y;}
    public void drawText(String value,float x,float y,Paint paint) {text=value;textAlpha=paint.getAlpha();textColor=paint.getColor();textX=x;textY=y;textSize=paint.getTextSize();textSpacing=paint.getLetterSpacing();textTypeface=paint.getTypeface();textEffectiveWeight=paint.effectiveWeight();}
    public void drawPath(Path value,Paint paint) {
        PorterDuff.Mode mode=paint.mode instanceof PorterDuffXfermode ? ((PorterDuffXfermode)paint.mode).mode : null;
        if(mode==PorterDuff.Mode.DST_OUT) {maskAlpha=paint.getAlpha();maskStyle=paint.style;maskStroke=paint.strokeWidth;}
        else {pathAlpha=paint.getAlpha();pathColor=paint.getColor();foregroundMode=mode;}
    }
    private Bitmap bitmapTarget;
    public int bitmapDraws;
    public Canvas(){}
    public Canvas(Bitmap bitmap){bitmapTarget=bitmap;}
    public void concat(Matrix matrix){}
    public boolean clipPath(Path path){clips++;return true;}
    public void drawBitmap(Bitmap bitmap,Rect source,RectF dest,Paint paint){
        bitmapDraws++;
        if(bitmapTarget==null)return;
        int left=Math.max(0,(int)dest.left),top=Math.max(0,(int)dest.top);
        int right=Math.min(bitmapTarget.getWidth(),(int)Math.ceil(dest.right)),bottom=Math.min(bitmapTarget.getHeight(),(int)Math.ceil(dest.bottom));
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){
            int sx=Math.min(bitmap.getWidth()-1,Math.max(0,(int)((x-dest.left)*bitmap.getWidth()/(dest.right-dest.left))));
            int sy=Math.min(bitmap.getHeight()-1,Math.max(0,(int)((y-dest.top)*bitmap.getHeight()/(dest.bottom-dest.top))));
            bitmapTarget.pixels[y*bitmapTarget.getWidth()+x]=bitmap.pixels[sy*bitmap.getWidth()+sx];
        }
    }
    public int paintFills;
    public int getWidth(){return bitmapTarget==null?clipBounds.width():bitmapTarget.getWidth();}
    public int getHeight(){return bitmapTarget==null?clipBounds.height():bitmapTarget.getHeight();}
    public void drawPaint(Paint paint){
        if(failMaskOnce){failMaskOnce=false;throw new IllegalStateException("mask unavailable");}
        paintFills++;lastShader=paint.shader;rectAlpha=paint.getAlpha();
        rectMode=paint.mode instanceof PorterDuffXfermode?((PorterDuffXfermode)paint.mode).mode:null;
        if(paint.shader!=null){lastEffectShader=paint.shader;effectAlpha=paint.getAlpha();}
    }
}
