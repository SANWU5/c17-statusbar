package android.graphics;
public class RecordingCanvas extends Canvas {
    public RenderNode mNode;
    public interface Command {void replay(Canvas canvas,int base);}
    public final java.util.List<Command> commandsForCheck=new java.util.ArrayList<>();
    public RecordingCanvas(int width,int height) {hardware=true;clipBounds=new Rect(0,0,width,height);}
    private static Paint snapshot(Paint source){Paint copy=new Paint();copy.set(source);return copy;}
    @Override public int save(){commandsForCheck.add((c,b)->c.save());return super.save();}
    @Override public void restoreToCount(int count){commandsForCheck.add((c,b)->c.restoreToCount(b+count-1));super.restoreToCount(count);}
    @Override public void translate(float x,float y){commandsForCheck.add((c,b)->c.translate(x,y));super.translate(x,y);}
    @Override public void scale(float x,float y){commandsForCheck.add((c,b)->c.scale(x,y));super.scale(x,y);}
    @Override public void concat(Matrix matrix){Matrix copy=new Matrix();copy.set(matrix);commandsForCheck.add((c,b)->c.concat(copy));}
    @Override public boolean clipRect(float l,float t,float r,float b){commandsForCheck.add((c,base)->c.clipRect(l,t,r,b));return super.clipRect(l,t,r,b);}
    @Override public void drawText(CharSequence text,int start,int end,float x,float y,Paint paint){
        String value=text.subSequence(start,end).toString();Paint copy=snapshot(paint);
        commandsForCheck.add((c,b)->c.drawText(value,0,value.length(),x,y,copy));super.drawText(value,x,y,paint);
    }
    @Override public void drawText(String value,float x,float y,Paint paint){
        Paint copy=snapshot(paint);commandsForCheck.add((c,b)->c.drawText(value,0,value.length(),x,y,copy));super.drawText(value,x,y,paint);
    }
    @Override public void drawRect(float l,float t,float r,float b,Paint paint){Paint copy=snapshot(paint);commandsForCheck.add((c,base)->c.drawRect(l,t,r,b,copy));super.drawRect(l,t,r,b,paint);}
    @Override public void drawRenderNode(RenderNode node){commandsForCheck.add((c,b)->c.drawRenderNode(node));super.drawRenderNode(node);}
}
