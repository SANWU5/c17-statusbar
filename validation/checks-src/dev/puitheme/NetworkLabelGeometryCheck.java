package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.telephony.TelephonyManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayDeque;

/** Replays the real Compose AndroidView update/measure/draw path with font metric changes. */
public final class NetworkLabelGeometryCheck {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Field field(Class<?> type,String name)throws Exception{Field value=type.getDeclaredField(name);value.setAccessible(true);return value;}
    /** Font width genuinely may change at a fixed text size. The platform fixture otherwise ignores weight. */
    private static final class Metrics extends Paint {
        private float factor(){return effectiveWeight()/600f;}
        @Override public float measureText(String text){return super.measureText(text)*factor();}
        @Override public void getTextBounds(String text,int start,int end,Rect bounds){super.getTextBounds(text,start,end,bounds);bounds.right=Math.round(bounds.right*factor());}
    }
    private static final class Trace extends Canvas {
        float sx=1f,sy=1f,tx,ty,left,right,weight,drawScaleX,drawScaleY;int draws,color;String text;boolean fail;
        final ArrayDeque<float[]> saved=new ArrayDeque<>();
        @Override public int save(){saved.push(new float[]{sx,sy,tx,ty});return super.save();}
        @Override public void restoreToCount(int count){while(getSaveCount()>count){float[] values=saved.pop();sx=values[0];sy=values[1];tx=values[2];ty=values[3];super.restoreToCount(getSaveCount()-1);}}
        @Override public void translate(float x,float y){tx+=sx*x;ty+=sy*y;}
        @Override public void scale(float x,float y){sx*=x;sy*=y;}
        @Override public void drawText(String label,float x,float y,Paint paint){Rect bounds=new Rect();paint.getTextBounds(label,0,label.length(),bounds);left=tx+sx*(x+bounds.left);right=tx+sx*(x+bounds.right);weight=paint.effectiveWeight();color=paint.getColor();text=label;drawScaleX=sx;drawScaleY=sy;draws++;if(fail)throw new IllegalStateException("native text failed");}
    }
    private static void configure(int weight)throws Exception{
        Map<String,Object> options=new HashMap<>();options.put("label_enabled",true);options.put("label_text_style_enabled",true);options.put("label_color_enabled",false);
        field(StatusBarModule.class,"FEATURES").set(null,FeatureOptions.from(options));field(StatusBarModule.class,"fontWeight").setInt(null,weight);
    }
    private static void measure(View view){view.measure(View.MeasureSpec.makeMeasureSpec(10000,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST));view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());}
    private static void automaticWidth(){
        Metrics paint=new Metrics();paint.setTextSize(13f);
        for(float density:new float[]{1f,2.75f})for(float scale:new float[]{0f,50f,100f,200f,300f,1e9f})for(String text:new String[]{"","5G","5GA","自定义😀"}){
            NetworkLabelGeometry.Plan first=NetworkLabelGeometry.measure(paint,text,density,scale,0f);
            for(float oldWidth:new float[]{0f,22f,80f}){
                NetworkLabelGeometry.Plan actual=NetworkLabelGeometry.measure(paint,text,density,scale,oldWidth);
                check(actual.width==first.width&&actual.scale==first.scale&&actual.glyph.equals(first.glyph),"obsolete manual width must not affect automatic geometry: "+oldWidth);
                if(text.isEmpty()||scale==0f)check(actual.width==0,"hidden or zero-scale text takes no automatic space");
                else {Trace trace=new Trace();actual.drawHorizontal(trace,text,paint,actual.width/2f,10f);check(trace.left>=-.01f&&trace.right<=actual.width+.01f,"automatic width contains the true ink");}
            }
        }
    }
    public static final class Subscription { public static int current(){return 7;} }
    @SuppressWarnings("unchecked") private static void legacyAutomaticWidth()throws Exception{
        TelephonyManager phone=new TelephonyManager();phone.dataEnabled=true;
        Context context=new Context(){@Override public Object getSystemService(String name){return "phone".equals(name)?phone:null;}};
        field(StatusBarModule.class,"context").set(null,context);field(StatusBarModule.class,"observedTelephony").set(null,phone);field(StatusBarModule.class,"observedDataSub").setInt(null,7);
        field(StatusBarModule.class,"getActiveDataSubId").set(null,Subscription.class.getMethod("current"));field(StatusBarModule.class,"getDefaultDataSubId").set(null,Subscription.class.getMethod("current"));
        field(StatusBarModule.class,"composeStatusBar").setBoolean(null,false);field(StatusBarModule.class,"wifiConnected").setBoolean(null,false);field(StatusBarModule.class,"airplaneMode").setBoolean(null,false);
        field(StatusBarModule.class,"mobileDataSwitchEnabled").set(null,Boolean.TRUE);
        SingleNetworkLabelControls labels=(SingleNetworkLabelControls)field(StatusBarModule.class,"SINGLE_LABEL").get(null);
        TextView text=new TextView(context);View image=new View(context);Object binding=new Object();String[] rat={"5G"};
        field(SingleNetworkLabelControls.class,"access").set(labels,new SingleNetworkLabelControls.Access(){public TextView text(Object owner){return text;}public View image(Object owner){return image;}public int subscription(Object owner){return 7;}public String nativeLabel(Object owner){return rat[0];}public void bind(Object owner,Object model){}});labels.bound(binding);
        FrameLayout host=new FrameLayout(context);host.setMinimumWidth(9);host.useLayoutBoundsForCheck=true;
        StatusBarModule.Slot slot=new StatusBarModule.Slot(host);Map<View,StatusBarModule.Slot> slots=(Map<View,StatusBarModule.Slot>)field(StatusBarModule.class,"SLOTS").get(null);slots.put(host,slot);
        Method refresh=StatusBarModule.class.getDeclaredMethod("refreshLabel");refresh.setAccessible(true);
        Method draw=StatusBarModule.class.getDeclaredMethod("drawLabel",ViewGroup.class,StatusBarModule.Slot.class,Canvas.class);draw.setAccessible(true);
        for(float scale:new float[]{0f,100f,200f})for(String label:new String[]{"5G","5GA"}){
            int previous=-1;rat[0]=label;
            for(float oldWidth:new float[]{0f,22f,80f}){
                Map<String,Object> values=new HashMap<>();values.put("label_enabled",true);values.put("label_text_style_enabled",true);values.put(StatusBarSettings.SLOT_WIDTH,oldWidth);
                field(StatusBarModule.class,"FEATURES").set(null,FeatureOptions.from(values));field(StatusBarModule.class,"labelScalePercent").setFloat(null,scale);refresh.invoke(null);
                check(host.getMinimumWidth()==slot.labelGeometry.width,"legacy refresh reserves its actual measured glyph");
                if(previous>=0)check(previous==host.getMinimumWidth(),"legacy refresh ignores all obsolete manual reserve values");previous=host.getMinimumWidth();
                host.layout(0,0,host.getMinimumWidth(),20);Trace canvas=new Trace();draw.invoke(null,host,slot,canvas);
                if(scale==0f)check(canvas.draws==0&&host.getMinimumWidth()==0,"legacy zero scale reserves no width");
                else check(canvas.left>=-.01f&&canvas.right<=host.getMinimumWidth()+.01f,"legacy draw fits its actual width while retaining glyph-height normalization");
                check(canvas.getSaveCount()==1,"legacy drawing restores native canvas");
            }
        }
        Map<String,Object> disabled=new HashMap<>();disabled.put("label_enabled",false);field(StatusBarModule.class,"FEATURES").set(null,FeatureOptions.from(disabled));refresh.invoke(null);
        check(host.getMinimumWidth()==9,"legacy label disable restores the original native minimum");slots.remove(host);
    }
    public static void main(String[] args)throws Exception{
        automaticWidth();if(args.length>0&&"auto".equals(args[0])){System.out.println("NetworkLabelGeometryCheck auto passed: "+checks);return;}
        configure(600);Class<?> type=Class.forName("dev.puitheme.StatusBarModule$NetworkLabelView");Constructor<?> constructor=type.getDeclaredConstructor(Context.class);constructor.setAccessible(true);
        View view=(View)constructor.newInstance(new Context());view.useLayoutBoundsForCheck=true;field(type,"paint").set(view,new Metrics());
        Method update=type.getDeclaredMethod("update",String.class,int.class);update.setAccessible(true);Method draw=type.getDeclaredMethod("onDraw",Canvas.class);draw.setAccessible(true);
        update.invoke(view,"5G",0xffffffff);measure(view);int width=view.getMeasuredWidth(),requests=view.layoutRequests;
        update.invoke(view,"5G",0xff123456);check(view.layoutRequests==requests,"color-only update keeps the existing geometry");
        configure(900);update.invoke(view,"5G",0xff123456);
        check(view.layoutRequests>requests,"same label with a wider font must request native remeasurement");measure(view);check(view.getMeasuredWidth()>width,"remeasure expands the real label allocation for wider glyphs");
        requests=view.layoutRequests;for(int i=0;i<20;i++){update.invoke(view,"5G",0xff123456);draw.invoke(view,new Canvas());}
        check(view.layoutRequests==requests,"stable update/draw does not repeatedly request layout");
        configure(600);update.invoke(view,"5G",0xff123456);check(view.layoutRequests>requests,"returning to the original font requests layout");measure(view);check(view.getMeasuredWidth()==width,"font restoration recovers original allocation");
        requests=view.layoutRequests;view.getResources().getDisplayMetrics().density*=1.25f;update.invoke(view,"5G",0xff123456);check(view.layoutRequests>requests,"density change with unchanged text updates native allocation");measure(view);
        requests=view.layoutRequests;configure(900);Trace pending=new Trace();draw.invoke(view,pending);check(view.layoutRequests>requests,"font becoming available during draw also invalidates old allocation");check(pending.left>=0&&pending.right<=view.getMeasuredWidth(),"pending font measure keeps drawing inside the previous allocation");check(pending.weight==600f,"pending frame retains its measured font");
        update.invoke(view,"5G",0xffaabbcc);Trace tinted=new Trace();draw.invoke(view,tinted);check(tinted.weight==600f&&tinted.color==0xffaabbcc,"pending metric change still accepts a current native tint");
        measure(view);requests=view.layoutRequests;Trace settled=new Trace();draw.invoke(view,settled);check(view.layoutRequests==requests&&settled.weight==900f,"settled draw publishes new font and keeps layout stable");
        view.measure(View.MeasureSpec.makeMeasureSpec(5,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(20,View.MeasureSpec.EXACTLY));requests=view.layoutRequests;
        for(int i=0;i<20;i++){update.invoke(view,"5G",0xff123456);draw.invoke(view,new Canvas());}
        check(view.getMeasuredWidth()==5&&view.getMeasuredHeight()==20,"the parent still owns exact slot constraints");check(view.layoutRequests==requests,"a constrained slot does not enter a remeasure loop");
        update.invoke(view,"",0xff123456);check("".contentEquals(view.getContentDescription()),"empty label clears its accessibility text");
        view.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(20,View.MeasureSpec.EXACTLY));requests=view.layoutRequests;draw.invoke(view,new Canvas());
        check(view.getMeasuredWidth()==0&&view.layoutRequests==requests,"a hidden zero-width label remains settled");
        configure(600);view.getResources().getDisplayMetrics().density=1f;field(StatusBarModule.class,"labelScalePercent").setFloat(null,200f);update.invoke(view,"5G",0xffffffff);measure(view);
        Paint paint=(Paint)field(type,"paint").get(view);Rect ink=new Rect();paint.getTextBounds("5G",0,2,ink);
        check(view.getMeasuredWidth()>=ink.width()*2f,"200 percent text must allocate its scaled visible width in the parent");
        for(float scale:new float[]{0f,50f,100f,200f,300f,1e9f})for(String text:new String[]{"5G","5GA","自定义😀"}){
            field(StatusBarModule.class,"labelScalePercent").setFloat(null,scale);update.invoke(view,text,0xffffffff);
            view.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED),View.MeasureSpec.makeMeasureSpec(20,View.MeasureSpec.EXACTLY));view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());
            Trace canvas=new Trace();draw.invoke(view,canvas);check(canvas.getSaveCount()==1,"scaled drawing restores native canvas");
            NetworkLabelGeometry.Plan allocated=(NetworkLabelGeometry.Plan)field(type,"geometry").get(view);check(view.getScaleX()==1f&&view.getScaleY()==allocated.scale,"only vertical scale stays outside the native content clip");
            if(scale==0){check(canvas.draws==0,"zero scale stays invisible");check(view.getMeasuredWidth()==0,"zero scale takes no automatic space");}
            else{check(canvas.left>=-.01f&&canvas.right<=view.getMeasuredWidth()+.01f,"scaled glyph extent fits the true parent allocation");check(view.getMeasuredWidth()<=65535,"scale and allocated width share the physical layout budget");
                check(canvas.drawScaleY==1f&&canvas.drawScaleX==view.getScaleY(),"total X and Y each scale exactly once while native content stays unscaled vertically");
                if(scale<=300){paint.getTextBounds(text,0,text.length(),ink);check(Math.abs((canvas.right-canvas.left)-ink.width()*scale/100f)<.01f,"text scale applies exactly once");}}
            requests=view.layoutRequests;Object plan=field(type,"geometry").get(view);draw.invoke(view,new Trace());check(plan==field(type,"geometry").get(view)&&view.layoutRequests==requests,"stable draw reuses immutable geometry without relayout");
        }
        field(StatusBarModule.class,"labelScalePercent").setFloat(null,200f);update.invoke(view,"5G",0xffffffff);measure(view);check(view.getMeasuredWidth()==NetworkLabelGeometry.measure(paint,"5G",1f,200f).width,"Compose allocation uses actual automatic glyph geometry");
        requests=view.layoutRequests;field(StatusBarModule.class,"labelScalePercent").setFloat(null,300f);update.invoke(view,"5G",0xffffffff);Trace oldScale=new Trace();draw.invoke(view,oldScale);
        check(view.layoutRequests>requests&&oldScale.drawScaleX==2f&&view.getScaleY()==2f,"scale changes keep both axes on the measured generation until layout");measure(view);Trace newScale=new Trace();draw.invoke(view,newScale);
        check(newScale.drawScaleX==3f&&newScale.drawScaleY==1f&&view.getScaleY()==3f&&view.getMeasuredHeight()==20,"new geometry publishes one matching external Y and internal X without enlarging the row");
        Trace failed=new Trace();failed.translate(10,20);failed.fail=true;try{draw.invoke(view,failed);throw new AssertionError("missing native failure");}catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof IllegalStateException,"native text failure preserved");}
        check(failed.getSaveCount()==1&&failed.tx==10f&&failed.ty==20f&&failed.sx==1f,"failed drawing restores the caller transform");
        legacyAutomaticWidth();
        System.out.println("NetworkLabelGeometryCheck passed: "+checks);
    }
}
