package android.graphics;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;

/** CPU geometry/pixels for bitmap-backed fixture Canvases only. This is not a Skia equivalence claim. */
final class BitmapRasterForCheck {
    private Graphics2D graphics;
    private BufferedImage currentImage;
    private final int width,height;
    private static final class Frame{final Graphics2D graphics;final BufferedImage image;boolean layer;int alpha;Frame(Graphics2D graphics,BufferedImage image){this.graphics=graphics;this.image=image;}}
    private final Deque<Frame> saved=new ArrayDeque<>();
    private static BufferedImage image(Bitmap bitmap){
        int[] masks={0x00ff0000,0x0000ff00,0x000000ff,0xff000000};
        WritableRaster raster=Raster.createPackedRaster(new DataBufferInt(bitmap.pixels,bitmap.pixels.length),bitmap.getWidth(),bitmap.getHeight(),bitmap.getWidth(),masks,null);
        return new BufferedImage(new DirectColorModel(32,masks[0],masks[1],masks[2],masks[3]),raster,false,null);
    }
    BitmapRasterForCheck(Bitmap target){width=target.getWidth();height=target.getHeight();currentImage=image(target);graphics=currentImage.createGraphics();hints();}
    private void hints(){graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);}
    void save(){saved.push(new Frame(graphics,currentImage));graphics=(Graphics2D)graphics.create();}
    void restore(){if(!saved.isEmpty()){BufferedImage completed=currentImage;graphics.dispose();Frame frame=saved.pop();graphics=frame.graphics;currentImage=frame.image;
            if(frame.layer){Graphics2D composite=(Graphics2D)graphics.create();composite.setTransform(new AffineTransform());composite.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,frame.alpha/255f));composite.drawImage(completed,0,0,null);composite.dispose();}}}
    /** A layer composites once on restore, rather than multiplying alpha separately for overlapping paths. */
    void alpha(int alpha){Frame frame=saved.peek();frame.layer=true;frame.alpha=alpha;AffineTransform transform=graphics.getTransform();Shape clip=graphics.getClip();graphics.dispose();currentImage=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);graphics=currentImage.createGraphics();graphics.setTransform(transform);graphics.setClip(clip);hints();}
    void translate(float x,float y){graphics.translate(x,y);}
    void scale(float x,float y){graphics.scale(x,y);}
    void rotate(float degrees){graphics.rotate(Math.toRadians(degrees));}
    void clip(Path path){graphics.clip(path.rasterPath);}
    void clipOut(Path path){
        Shape clip=graphics.getClip();
        if(clip==null)try{clip=graphics.getTransform().createInverse().createTransformedShape(new Rectangle(0,0,width,height));}
        catch(NoninvertibleTransformException invalid){return;}
        Area area=new Area(clip);area.subtract(new Area(path.rasterPath));graphics.setClip(area);
    }
    void clip(float l,float t,float r,float b){graphics.clip(new Rectangle2D.Float(l,t,r-l,b-t));}
    void concat(Matrix matrix){float[] v=new float[9];matrix.getValues(v);graphics.transform(new AffineTransform(v[0],v[3],v[1],v[4],v[2],v[5]));}
    void path(Path path,Paint paint){
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(new java.awt.Color((paint.getColor()&0xffffff)|(paint.getAlpha()<<24),true));
        if(paint.getShader() instanceof LinearGradient){
            LinearGradient gradient=(LinearGradient)paint.getShader();
            java.awt.Color[] colors=new java.awt.Color[gradient.colors.length];
            for(int i=0;i<colors.length;i++)colors[i]=new java.awt.Color((gradient.colors[i]&0xffffff)|((gradient.colors[i]>>>24)*paint.getAlpha()/255<<24),true);
            graphics.setPaint(new LinearGradientPaint(gradient.left,gradient.top,gradient.right,gradient.bottom,gradient.positions,colors));
        }
        if(paint.getStyle()!=Paint.Style.FILL){int cap=paint.getStrokeCap()==Paint.Cap.ROUND?BasicStroke.CAP_ROUND:paint.getStrokeCap()==Paint.Cap.SQUARE?BasicStroke.CAP_SQUARE:BasicStroke.CAP_BUTT;
            int join=paint.getStrokeJoin()==Paint.Join.ROUND?BasicStroke.JOIN_ROUND:paint.getStrokeJoin()==Paint.Join.BEVEL?BasicStroke.JOIN_BEVEL:BasicStroke.JOIN_MITER;
            graphics.setStroke(new BasicStroke(paint.getStrokeWidth(),cap,join,paint.getStrokeMiter()));graphics.draw(path.rasterPath);}
        if(paint.getStyle()!=Paint.Style.STROKE)graphics.fill(path.rasterPath);
    }
    void bitmap(Bitmap bitmap,Rect source,RectF dest,Paint paint){
        BufferedImage input=image(bitmap);
        if(bitmap.getConfig()==Bitmap.Config.ALPHA_8||paint!=null&&paint.getColorFilter() instanceof PorterDuffColorFilter){
            int color=paint==null?0xff000000:paint.getColor()|0xff000000;if(paint!=null&&paint.getColorFilter() instanceof PorterDuffColorFilter)color=((PorterDuffColorFilter)paint.getColorFilter()).getColor();
            input=new BufferedImage(bitmap.getWidth(),bitmap.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++){int a=(bitmap.pixels[y*bitmap.getWidth()+x]>>>24)*(color>>>24)/255;input.setRGB(x,y,(a<<24)|(color&0xffffff));}
        }
        int l=source==null?0:source.left,t=source==null?0:source.top,r=source==null?bitmap.getWidth():source.right,b=source==null?bitmap.getHeight():source.bottom;
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,paint==null?1:paint.getAlpha()/255f));
        AffineTransform transform=new AffineTransform();transform.translate(dest.left,dest.top);transform.scale(dest.width()/(r-l),dest.height()/(b-t));
        graphics.drawImage(input.getSubimage(l,t,r-l,b-t),transform,null);
    }
}
