package android.graphics;
public class Canvas {
    public final java.util.List<RenderNode> drawnNodesForCheck=new java.util.ArrayList<>();
    public final java.util.List<Bitmap> drawnBitmapsForCheck=new java.util.ArrayList<>();
    public int lastClearColorForCheck;
    public PorterDuff.Mode lastClearModeForCheck;
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
    public PorterDuff.Mode textMode;
    public int maskAlpha=-1,pathColor,textColor,clips;
    public Paint.Style maskStyle;
    public float maskStroke;
    public PorterDuff.Mode foregroundMode;
    public float translateX,translateY,scaleX=1f,scaleY=1f,pivotX,pivotY;
    public String text;
    public float textX,textY,textSize,textSpacing,textEffectiveWeight;
    public Typeface textTypeface;
    public int save() {saves++;if(raster!=null)raster.save();return saveCount++;}
    public void restoreToCount(int count) {restores++;if(raster!=null)for(int n=saveCount;n>count;n--)raster.restore();saveCount=count;}
    public int pictureDraws;public Picture lastPicture;
    public void drawPicture(Picture picture){pictureDraws++;lastPicture=picture;}
    public int getSaveCount() {return saveCount;}
    public int saveLayer(float left,float top,float right,float bottom,Paint paint) {if(failLayerOnce){failLayerOnce=false;throw new IllegalStateException("layer unavailable");}layers++;layerLeft=left;layerTop=top;layerRight=right;layerBottom=bottom;return save();}
    public int saveLayer(RectF bounds,Paint paint){lastLayerPaint=paint;return bounds==null
            ?saveLayer(clipBounds.left,clipBounds.top,clipBounds.right,clipBounds.bottom,paint)
            :saveLayer(bounds.left,bounds.top,bounds.right,bounds.bottom,paint);}
    public int saveLayerAlpha(RectF bounds,int alpha){layers++;layerLeft=bounds.left;layerTop=bounds.top;layerRight=bounds.right;layerBottom=bounds.bottom;int saved=save();if(raster!=null)raster.alpha(alpha);return saved;}
    public int saveLayerAlpha(float left,float top,float right,float bottom,int alpha){return saveLayerAlpha(new RectF(left,top,right,bottom),alpha);}
    public boolean isHardwareAccelerated() {return hardware;}
    public boolean getClipBounds(Rect rect) {rect.left=clipBounds.left;rect.top=clipBounds.top;rect.right=clipBounds.right;rect.bottom=clipBounds.bottom;return true;}
    public boolean clipRect(float left,float top,float right,float bottom) {
        if(failClipOnce){failClipOnce=false;throw new IllegalStateException("clip unavailable");}
        clips++;clipLeft=left;clipTop=top;clipRight=right;clipBottom=bottom;
        effectiveClipLeft=Math.max(left,clipBounds.left-translateX);effectiveClipTop=Math.max(top,clipBounds.top-translateY);
        effectiveClipRight=Math.min(right,clipBounds.right-translateX);effectiveClipBottom=Math.min(bottom,clipBounds.bottom-translateY);
        if(raster!=null)raster.clip(left,top,right,bottom);
        return true;
    }
    public boolean clipRect(int left,int top,int right,int bottom) {return clipRect((float)left,(float)top,(float)right,(float)bottom);}
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
    public void drawRect(Rect rect,Paint paint) {drawRect(rect.left,rect.top,rect.right,rect.bottom,paint);}
    /** Plain colour and rectangle fills, as the lock-screen capsule tint draws them. */
    public int colorFills,rectFills;public int lastColorFill;public RectF lastFillRect;public Shader lastFillShader;public int lastRectColor;public Xfermode lastRectMode;
    public void drawColor(int color){colorFills++;lastColorFill=color;}
    /** Rounded rectangles, as the capsule's running light draws them. */
    public int roundRects;public RectF lastRoundRect;public float lastRoundRadiusX;public Paint lastRoundPaint;
    public void drawRoundRect(RectF rect,float rx,float ry,Paint paint){roundRects++;lastRoundRect=new RectF(rect.left,rect.top,rect.right,rect.bottom);lastRoundRadiusX=rx;lastRoundPaint=paint;}
    public void drawRect(RectF rect,Paint paint){rectFills++;lastFillRect=new RectF(rect.left,rect.top,rect.right,rect.bottom);lastFillShader=paint.shader;lastRectColor=paint.getColor();lastRectMode=paint.mode;}
    public void drawColor(int color,PorterDuff.Mode mode) {clears++;lastClearColorForCheck=color;lastClearModeForCheck=mode;}
    public void drawRenderNode(RenderNode node) {
        nodeDraws++;if(failNodeOnce!=null&&node.name.contains(failNodeOnce)){failNodeOnce=null;throw new IllegalStateException("node unavailable");}
        drawnNodesForCheck.add(node);
    }
    public boolean clipRect(RectF rect) {clips++;return true;}
    public boolean isHighContrastTextEnabled() {return contrast;}
    public void translate(float x,float y) {translateX+=x;translateY+=y;if(raster!=null)raster.translate(x,y);}
    public void scale(float x,float y,float px,float py) {scaleX*=x;scaleY*=y;pivotX=px;pivotY=py;if(raster!=null){raster.translate(px,py);raster.scale(x,y);raster.translate(-px,-py);}}
    public void scale(float x,float y) {scaleX*=x;scaleY*=y;if(raster!=null)raster.scale(x,y);}
    public void rotate(float degrees){if(raster!=null)raster.rotate(degrees);}
    public void drawText(String value,float x,float y,Paint paint) {text=value;textAlpha=paint.getAlpha();textColor=paint.getColor();textX=x;textY=y;textSize=paint.getTextSize();textSpacing=paint.getLetterSpacing();textTypeface=paint.getTypeface();textEffectiveWeight=paint.effectiveWeight();textMode=paint.mode instanceof PorterDuffXfermode?((PorterDuffXfermode)paint.mode).mode:null;}
    private static final String[] ASCII_GLYPHS=new String[128];
    static {for(int i=0;i<ASCII_GLYPHS.length;i++)ASCII_GLYPHS[i]=String.valueOf((char)i);}
    /** The native Canvas overload consumes a range; it does not construct a
     * new SpannableString and copy its spans for every recorded digit. */
    public void drawText(CharSequence value,int start,int end,float x,float y,Paint paint){
        String recorded=end-start==1&&value.charAt(start)<128?ASCII_GLYPHS[value.charAt(start)]:
                start==0&&end==value.length()?value.toString():value.toString().substring(start,end);
        drawText(recorded,x,y,paint);
    }
    public void drawPath(Path value,Paint paint) {
        PorterDuff.Mode mode=paint.mode instanceof PorterDuffXfermode ? ((PorterDuffXfermode)paint.mode).mode : null;
        if(mode==PorterDuff.Mode.DST_OUT) {maskAlpha=paint.getAlpha();maskStyle=paint.style;maskStroke=paint.strokeWidth;}
        else {pathAlpha=paint.getAlpha();pathColor=paint.getColor();foregroundMode=mode;}
        if(raster!=null)raster.path(value,paint);
    }
    private Bitmap bitmapTarget;
    private BitmapRasterForCheck raster;
    public int bitmapDraws;
    /** Every bitmap drawn, with what it was drawn with: alpha, additive or not, the source part and the destination. */
    public static final class BitmapDraw {public final Bitmap bitmap;public final int alpha;public final boolean additive;public final Rect source;public final RectF dest;BitmapDraw(Bitmap bitmap,int alpha,boolean additive,Rect source,RectF dest){this.bitmap=bitmap;this.alpha=alpha;this.additive=additive;this.source=source;this.dest=dest;}}
    public final java.util.List<BitmapDraw> bitmapLog=new java.util.ArrayList<>();
    public Canvas(){}
    public Canvas(Bitmap bitmap){bitmapTarget=bitmap;raster=new BitmapRasterForCheck(bitmap);}
    public void concat(Matrix matrix){if(raster!=null)raster.concat(matrix);}
    public boolean clipPath(Path path){clips++;pathClips++;if(raster!=null)raster.clip(path);return true;}
    public int pathClips,outClips,circles;public float circleX,circleY,circleRadius;public int circleAlpha=-1;
    public boolean clipOutPath(Path path){clips++;outClips++;if(raster!=null)raster.clipOut(path);return true;}
    /** Recorded native-number exclusion; it is scoped by the caller's save/restore. */
    public int outRectClips,outRectSaveCount;
    public RectF lastOutRect;
    public boolean clipOutRect(RectF rect){
        if(failClipOnce){failClipOnce=false;throw new IllegalStateException("clip unavailable");}
        clips++;outRectClips++;outRectSaveCount=getSaveCount();
        lastOutRect=new RectF(rect.left,rect.top,rect.right,rect.bottom);return true;
    }
    public boolean clipOutRect(float left,float top,float right,float bottom){return clipOutRect(new RectF(left,top,right,bottom));}
    public void drawCircle(float x,float y,float radius,Paint paint){circles++;circleX=x;circleY=y;circleRadius=radius;circleAlpha=paint.getAlpha();}
    public int arcs;
    public void drawArc(RectF oval,float startAngle,float sweepAngle,boolean useCenter,Paint paint){arcs++;}
    public void drawBitmap(Bitmap bitmap,Rect source,RectF dest,Paint paint){
        bitmapDraws++;drawnBitmapsForCheck.add(bitmap);bitmapLog.add(new BitmapDraw(bitmap,paint==null?255:paint.getAlpha(),paint!=null&&paint.mode instanceof PorterDuffXfermode&&((PorterDuffXfermode)paint.mode).mode==PorterDuff.Mode.ADD,source==null?null:new Rect(source.left,source.top,source.right,source.bottom),new RectF(dest.left,dest.top,dest.right,dest.bottom)));
        if(bitmapTarget==null)return;
        raster.bitmap(bitmap,source,dest,paint);
    }
    public void drawBitmap(Bitmap bitmap,Matrix matrix,Paint paint){
        int saved=save();try{concat(matrix);drawBitmap(bitmap,null,new RectF(0,0,bitmap.getWidth(),bitmap.getHeight()),paint);}finally{restoreToCount(saved);}
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
