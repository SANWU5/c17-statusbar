package android.graphics;
public class Path {
    public static int creationsForCheck;
    public Path(){creationsForCheck++;}
    public enum Direction {CW,CCW}
    public enum Op {DIFFERENCE,INTERSECT,UNION,XOR,REVERSE_DIFFERENCE}
    public enum FillType {WINDING,EVEN_ODD,INVERSE_WINDING,INVERSE_EVEN_ODD}
    final java.awt.geom.Path2D.Float rasterPath=new java.awt.geom.Path2D.Float();
    public float nativeRadiusX,nativeRadiusY;
    public static int roundRectWritesForCheck;
    private final RectF recordedBounds=new RectF();
    private boolean empty=true;
    public void addRoundRect(RectF rect,float x,float y,Direction direction){roundRectWritesForCheck++;recordedBounds.set(rect.left,rect.top,rect.right,rect.bottom);nativeRadiusX=x;nativeRadiusY=y;empty=false;appendSymmetric(new java.awt.geom.RoundRectangle2D.Float(rect.left,rect.top,rect.width(),rect.height(),x*2,y*2),direction);}
    public void reset() {empty=true;rasterPath.reset();}
    public boolean isEmpty(){return empty;}
    public void computeBounds(RectF output,boolean exact){output.set(recordedBounds.left,recordedBounds.top,recordedBounds.right,recordedBounds.bottom);}
    public void set(Path other){if(other==this)return;other.computeBounds(recordedBounds,true);nativeRadiusX=other.nativeRadiusX;nativeRadiusY=other.nativeRadiusY;empty=other.empty;rasterPath.reset();rasterPath.setWindingRule(other.rasterPath.getWindingRule());rasterPath.append(other.rasterPath,false);}
    private void updated(){java.awt.geom.Rectangle2D b=rasterPath.getBounds2D();recordedBounds.set((float)b.getMinX(),(float)b.getMinY(),(float)b.getMaxX(),(float)b.getMaxY());empty=rasterPath.getCurrentPoint()==null;}
    public void setFillType(FillType type){rasterPath.setWindingRule(type==FillType.EVEN_ODD||type==FillType.INVERSE_EVEN_ODD?java.awt.geom.Path2D.WIND_EVEN_ODD:java.awt.geom.Path2D.WIND_NON_ZERO);}
    public void moveTo(float x,float y) {rasterPath.moveTo(x,y);updated();}
    public void lineTo(float x,float y) {rasterPath.lineTo(x,y);updated();}
    public void cubicTo(float x1,float y1,float x2,float y2,float x3,float y3) {rasterPath.curveTo(x1,y1,x2,y2,x3,y3);updated();}
    public void quadTo(float x1,float y1,float x2,float y2){rasterPath.quadTo(x1,y1,x2,y2);updated();}
    private float x(){java.awt.geom.Point2D p=rasterPath.getCurrentPoint();return p==null?0:(float)p.getX();}
    private float y(){java.awt.geom.Point2D p=rasterPath.getCurrentPoint();return p==null?0:(float)p.getY();}
    public void rMoveTo(float x,float y){moveTo(x()+x,y()+y);}
    public void rLineTo(float x,float y){lineTo(x()+x,y()+y);}
    public void rCubicTo(float a,float b,float c,float d,float e,float f){float x=x(),y=y();cubicTo(x+a,y+b,x+c,y+d,x+e,y+f);}
    public void rQuadTo(float a,float b,float c,float d){float x=x(),y=y();quadTo(x+a,y+b,x+c,y+d);}
    public void transform(Matrix matrix) {float[] v=new float[9];matrix.getValues(v);rasterPath.transform(new java.awt.geom.AffineTransform(v[0],v[3],v[1],v[4],v[2],v[5]));updated();}
    public void close() {if(rasterPath.getCurrentPoint()!=null){rasterPath.closePath();updated();}}
    public void addCircle(float x,float y,float radius,Direction direction){appendSymmetric(new java.awt.geom.Ellipse2D.Float(x-radius,y-radius,radius*2,radius*2),direction);updated();}
    public void addRect(float left,float top,float right,float bottom,Direction direction){appendSymmetric(new java.awt.geom.Rectangle2D.Float(left,top,right-left,bottom-top),direction);updated();}
    /** AWT ellipses and round rectangles have opposite default windings. Android's
     * explicit CW/CCW must keep overlapping subpaths filled instead of punching holes. */
    private void appendSymmetric(java.awt.Shape shape,Direction direction){
        java.awt.geom.PathIterator iterator=shape.getPathIterator(null,.01);double[] p=new double[6];
        double x=0,y=0,firstX=0,firstY=0,area=0;
        while(!iterator.isDone()){int type=iterator.currentSegment(p);
            if(type==java.awt.geom.PathIterator.SEG_MOVETO){x=firstX=p[0];y=firstY=p[1];}
            else if(type==java.awt.geom.PathIterator.SEG_LINETO){area+=x*p[1]-p[0]*y;x=p[0];y=p[1];}
            else if(type==java.awt.geom.PathIterator.SEG_CLOSE)area+=x*firstY-firstX*y;
            iterator.next();
        }
        if((area>0)!=(direction==Direction.CW)){
            java.awt.geom.Rectangle2D bounds=shape.getBounds2D();
            shape=new java.awt.geom.AffineTransform(-1,0,0,1,2*bounds.getCenterX(),0).createTransformedShape(shape);
        }
        rasterPath.append(shape,false);
    }
    public void arcTo(RectF oval,float startAngle,float sweepAngle,boolean forceMoveTo){rasterPath.append(new java.awt.geom.Arc2D.Float(oval.left,oval.top,oval.width(),oval.height(),-startAngle,-sweepAngle,java.awt.geom.Arc2D.OPEN),!forceMoveTo);updated();}
    public boolean op(Path other,Op operation){
        java.awt.geom.Area a=new java.awt.geom.Area(rasterPath),b=new java.awt.geom.Area(other.rasterPath);
        switch(operation){case DIFFERENCE:a.subtract(b);break;case INTERSECT:a.intersect(b);break;case UNION:a.add(b);break;case XOR:a.exclusiveOr(b);break;default:b.subtract(a);a=b;break;}
        setShapeForCheck(a);return true;
    }
    void setShapeForCheck(java.awt.Shape shape){rasterPath.reset();rasterPath.append(shape,false);updated();}
}
