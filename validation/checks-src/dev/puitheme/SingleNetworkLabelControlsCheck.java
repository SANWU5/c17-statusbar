package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Replays the real single-SIM binder transaction and main-data subscription changes. */
public final class SingleNetworkLabelControlsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(expected instanceof CharSequence&&actual instanceof CharSequence){expected=expected.toString();actual=actual.toString();}if(!Objects.equals(expected,actual))throw new AssertionError("Single label "+checks+": "+expected+" != "+actual);}
    private static final class Binder {
        final TextView text;final View image=new View(new Context());final int sub;
        int binds;Object model;
        Binder(int sub){this(sub,null);}
        Binder(int sub,TextView reused){this.sub=sub;text=reused==null?new TextView(new Context()):reused;
            if(reused==null){text.setText("native-"+sub);text.setTextSize(TypedValue.COMPLEX_UNIT_PX,20);text.setMinimumWidth(24);text.setTranslationX(3);text.setTranslationY(5);}}
    }
    private static final class Native implements SingleNetworkLabelControls.Access {
        public TextView text(Object b){return ((Binder)b).text;}
        public View image(Object b){return ((Binder)b).image;}
        public int subscription(Object b){return ((Binder)b).sub;}
        public Object model(Object b){return ((Binder)b).model;}
        public String nativeLabel(Object b){Object model=((Binder)b).model;return model==null?"":model.toString();}
        public void bind(Object b,Object model){Binder row=(Binder)b;row.binds++;row.model=model;row.text.setText(model==null?null:model.toString());row.text.setVisibility(model==null?View.GONE:View.VISIBLE);row.image.setVisibility(View.VISIBLE);}
    }
    private static FeatureOptions options(boolean enabled){Bundle b=new Bundle();b.putBoolean("label_enabled",enabled);b.putBoolean("label_text_style_enabled",false);return FeatureOptions.from(b);}
    private static void event(SingleNetworkLabelControls helper,Native nativeCode,Binder b,Object model){
        try(SingleNetworkLabelControls.NativeScope ignored=helper.beforeNative(b,true,model)){nativeCode.bind(b,model);}
    }
    private static void update(SingleNetworkLabelControls helper,FeatureOptions opts,String text,int sub){
        helper.update(opts,text,sub,2,-1,150,22,650,Collections.emptyMap(),Collections.emptyMap());
    }
    public static void main(String[] ignored)throws Exception{
        if(ignored.length>0&&"auto-width".equals(ignored[0])){autoWidthLegacyContract();System.out.println("SingleNetworkLabelControls auto-width checks passed: "+checks);return;}
        autoWidthLegacyContract();
        scaledInkBudget();
        // Real defect: an OEM 8px badge and a 24px badge must produce the same
        // 13dp independent glyph as the current dual-SIM Compose renderer.
        independentGlyphContract();
        glyphSharedLifecycle();
        styleSwitchContract();
        Native nativeCode=new Native();SingleNetworkLabelControls helper=new SingleNetworkLabelControls(nativeCode);Binder primary=new Binder(8),secondary=new Binder(21);
        event(helper,nativeCode,primary,"native8");event(helper,nativeCode,secondary,"native21");
        equal(false,SingleNetworkLabelControls.managed(primary.text));equal("native8",primary.text.getText());
        update(helper,options(true),"5G",8);
        equal(true,SingleNetworkLabelControls.managed(primary.text));equal(View.VISIBLE,primary.text.getVisibility());equal("5G",primary.text.getText());
        equal(View.GONE,secondary.text.getVisibility());equal(View.GONE,primary.image.getVisibility());equal(View.GONE,secondary.image.getVisibility());
        equal(8f,primary.text.getTranslationX());equal(-4f,primary.text.getTranslationY());equal(52f,primary.text.getTextSize());equal(0,primary.text.getMinimumWidth());
        equal(1.5f,paintedScale(primary.text));equal(1f,primary.text.getScaleX());equal(1.5f,primary.text.getScaleY());
        equal(primary.text.getTypeface(),secondary.text.getTypeface());
        // Main traffic SIM, not native first-or-null/slot order, determines the displayed generation.
        update(helper,options(true),"4G",21);equal(View.GONE,primary.text.getVisibility());equal(View.VISIBLE,secondary.text.getVisibility());equal("4G",secondary.text.getText());
        update(helper,options(true),"",21);equal(View.GONE,secondary.text.getVisibility());
        update(helper,options(true),"5G",8);int writes=primary.text.layoutRequests;
        for(int i=0;i<100;i++)update(helper,options(true),"5G",8);equal(writes,primary.text.layoutRequests);
        // Native density/font-size updates still read their original badge size.
        try(SingleNetworkLabelControls.NativeScope init=helper.beforeNative(primary,false,null)){
            equal(20f,primary.text.getTextSize());
            try(SingleNetworkLabelControls.NativeScope bind=helper.beforeNative(primary,true,"native-new")){
                nativeCode.bind(primary,"native-new");primary.text.setTextSize(TypedValue.COMPLEX_UNIT_PX,24);primary.text.setTextColor(0xffffffff);
            }
            equal(24f,primary.text.getTextSize());
        }
        equal(52f,primary.text.getTextSize());equal("5G",primary.text.getText());
        update(helper,options(false),"5G",8);equal(false,SingleNetworkLabelControls.managed(primary.text));
        equal(24f,primary.text.getTextSize());equal(3f,primary.text.getTranslationX());equal(5f,primary.text.getTranslationY());equal(24,primary.text.getMinimumWidth());
        equal("native-new",primary.text.getText());equal(View.VISIBLE,primary.image.getVisibility());equal(View.VISIBLE,secondary.text.getVisibility());equal("native21",secondary.text.getText());
        int restored=primary.binds;update(helper,options(false),"5G",8);equal(restored,primary.binds);
        // Custom hide and no active data route own visibility without leaking native label text.
        Bundle hidden=new Bundle();hidden.putBoolean("label_enabled",true);hidden.putBoolean(StatusBarSettings.LABEL_HIDDEN,true);
        update(helper,FeatureOptions.from(hidden),"5G",8);equal(View.GONE,primary.text.getVisibility());equal(View.GONE,secondary.text.getVisibility());
        update(helper,options(true),"5G",-1);equal(View.GONE,primary.text.getVisibility());equal(View.GONE,secondary.text.getVisibility());
        update(helper,options(true),"5G",8);Bundle safe=new Bundle();safe.putBoolean("label_enabled",true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);
        update(helper,FeatureOptions.from(safe),"5G",8);equal("native-new",primary.text.getText());equal(false,SingleNetworkLabelControls.managed(primary.text));
        // Native tint is updated inside the OEM transaction; custom colors use the same policy as Compose.
        Map<String,Integer> colors=new LinkedHashMap<>();colors.put("label_color_light",0xffff0000);colors.put("label_color_dark",0xff00ff00);
        helper.update(options(true),"5G",8,0,0,100,22,400,colors,Collections.emptyMap());
        try(SingleNetworkLabelControls.NativeScope scope=helper.beforeNative(primary,false,null)){primary.text.setTextColor(0xff000000);}
        equal(0xffff0000,primary.text.getCurrentTextColor());
        helper.releaseRuntime();equal(false,SingleNetworkLabelControls.managed(primary.text));equal("native-new",primary.text.getText());equal(24f,primary.text.getTextSize());
        // A concrete createBinding result registers a fully initialized row even when
        // OEM initViews/bind calls were inlined and no hooked native transaction ran.
        SingleNetworkLabelControls factoryHelper=new SingleNetworkLabelControls(nativeCode);Binder fromFactory=new Binder(8),otherFactory=new Binder(21);
        nativeCode.bind(fromFactory,"5GA");nativeCode.bind(otherFactory,"4G");
        factoryHelper.bound(fromFactory);factoryHelper.bound(otherFactory);
        equal("5GA",factoryHelper.nativeLabel(8));equal("4G",factoryHelper.nativeLabel(21));equal("",factoryHelper.nativeLabel(-1));equal("",factoryHelper.nativeLabel(99));
        update(factoryHelper,options(true),"5G",8);equal("5G",fromFactory.text.getText());equal(8f,fromFactory.text.getTranslationX());
        equal(View.GONE,otherFactory.text.getVisibility());equal(52f,fromFactory.text.getTextSize());
        fromFactory.model=null;equal("",factoryHelper.nativeLabel(8)); // Latest null model never falls back to remembered output.
        fromFactory.model="LTE";equal("LTE",factoryHelper.nativeLabel(8));
        update(factoryHelper,options(false),"5G",8);equal("5GA",fromFactory.text.getText());equal(20f,fromFactory.text.getTextSize());
        equal(true,factoryHelper.diagnosticSummary().contains("factory events 2"));
        factoryHelper.releaseRuntime();equal("",factoryHelper.nativeLabel(8));
        // Native factories may rebuild a Binding around an already-styled TextView.
        // One genuine glyph must retain one native baseline and transaction depth.
        SingleNetworkLabelControls reusedHelper=new SingleNetworkLabelControls(nativeCode);Binder first=new Binder(8);
        nativeCode.bind(first,"LTE");reusedHelper.bound(first);update(reusedHelper,options(true),"5G",8);
        Binder replacement=new Binder(8,first.text);replacement.model="5GA";reusedHelper.bound(replacement);
        equal(52f,first.text.getTextSize());equal(8f,first.text.getTranslationX());equal(-4f,first.text.getTranslationY());
        equal("5GA",reusedHelper.nativeLabel(8));
        try(SingleNetworkLabelControls.NativeScope oldScope=reusedHelper.beforeNative(first,false,null)){
            equal(20f,first.text.getTextSize());equal(3f,first.text.getTranslationX());
            try(SingleNetworkLabelControls.NativeScope nextScope=reusedHelper.beforeNative(replacement,true,"4G")){
                nativeCode.bind(replacement,"4G");first.text.setTextSize(TypedValue.COMPLEX_UNIT_PX,24);
            }
            equal(24f,first.text.getTextSize());
        }
        equal(52f,first.text.getTextSize());equal(8f,first.text.getTranslationX());
        update(reusedHelper,options(false),"5G",8);
        equal(24f,first.text.getTextSize());equal(3f,first.text.getTranslationX());equal(5f,first.text.getTranslationY());
        equal("4G",first.text.getText());equal(false,SingleNetworkLabelControls.managed(first.text));
        reusedHelper.releaseRuntime();equal(24f,first.text.getTextSize());
        Binder exact=new Binder(8);float oldDensity=exact.text.getResources().getDisplayMetrics().density;
        try{
            exact.text.getResources().getDisplayMetrics().density=3.40625f;
            SingleNetworkLabelControls exactHelper=new SingleNetworkLabelControls(nativeCode);nativeCode.bind(exact,"5G");exactHelper.bound(exact);
            exactHelper.update(options(true),"5G",8,15.11f,8.02f,175.37f,22,600,Collections.emptyMap(),Collections.emptyMap());
            equal(NumericPolicy.pixels(15.11f,3.40625f),exact.text.getTranslationX());
            equal(NumericPolicy.pixels(8.02f,3.40625f),exact.text.getTranslationY());
            equal(13f*3.40625f,exact.text.getTextSize());
            equal(175.37f/100f,paintedScale(exact.text));equal(1f,exact.text.getScaleX());
            equal("5G",exact.text.getText());exactHelper.releaseRuntime();equal(3f,exact.text.getTranslationX());equal(20f,exact.text.getTextSize());
        }finally{exact.text.getResources().getDisplayMetrics().density=oldDensity;}
        SingleNetworkLabelControls removed=new SingleNetworkLabelControls(nativeCode);Binder removedRow=new Binder(8);nativeCode.bind(removedRow,"5G");removed.bound(removedRow);
        update(removed,options(true),"5G",8);
        SingleNetworkLabelControls.NativeScope pending=removed.beforeNative(removedRow,true,"4G");
        removed.releaseRuntime();pending.close();pending.close();
        equal(false,SingleNetworkLabelControls.managed(removedRow.text));equal(20f,removedRow.text.getTextSize());equal(3f,removedRow.text.getTranslationX());
        equal("",removed.nativeLabel(8));equal(true,removed.diagnosticSummary().startsWith("Single network label rows 0"));
        System.out.println("SingleNetworkLabelControls checks passed: "+checks);
    }
    /** Framework WRAP_CONTENT width: ReplacementSpan advance plus the explicit minimum, not View scaling. */
    private static final class MeasuredLabel extends TextView {
        MeasuredLabel(){super(new Context());}
        @Override protected void onMeasure(int width,int height){
            super.onMeasure(width,height);
            setMeasuredDimension(Math.max(getMinimumWidth(),(int)Math.ceil(android.text.Layout.getDesiredWidth(getText(),0,getText().length(),getPaint()))),80);
        }
    }
    /** Records the transformed painted bounds, including the parent-applied View matrix. */
    private static final class InkCanvas extends Canvas {
        private float sx=1f,sy=1f,tx;
        private final Map<Integer,float[]> states=new LinkedHashMap<>();
        float inkLeft,inkRight,drawScale,drawScaleY;
        @Override public int save(){int token=super.save();states.put(token,new float[]{sx,tx,sy});return token;}
        @Override public void restoreToCount(int token){float[] state=states.remove(token);if(state!=null){sx=state[0];tx=state[1];sy=state[2];}super.restoreToCount(token);}
        @Override public void translate(float x,float y){tx+=sx*x;super.translate(x,y);}
        @Override public void scale(float x,float y){sx*=x;sy*=y;super.scale(x,y);}
        @Override public void scale(float x,float y,float px,float py){tx+=sx*px*(1f-x);sx*=x;sy*=y;super.scale(x,y,px,py);}
        @Override public void drawText(String text,float x,float y,Paint paint){Rect bounds=new Rect();paint.getTextBounds(text,0,text.length(),bounds);
            inkLeft=tx+sx*(x+bounds.left);inkRight=tx+sx*(x+bounds.right);drawScale=sx;drawScaleY=sy;super.drawText(text,x,y,paint);}
    }
    private static float paintedScale(TextView view){Spanned text=(Spanned)view.getText();ReplacementSpan span=text.getSpans(0,text.length(),ReplacementSpan.class)[0];
        InkCanvas canvas=new InkCanvas();span.draw(canvas,text,0,text.length(),0,0,40,80,view.getPaint());return canvas.drawScale*view.getScaleX();}
    private static InkCanvas ink(TextView view){Spanned text=(Spanned)view.getText();ReplacementSpan span=text.getSpans(0,text.length(),ReplacementSpan.class)[0];
        InkCanvas canvas=new InkCanvas();span.draw(canvas,text,0,text.length(),0,0,40,80,view.getPaint());return canvas;}
    private static void autoWidthLegacyContract(){
        Native nativeCode=new Native();MeasuredLabel text=new MeasuredLabel();text.strictMeasuredRelayout=true;
        text.setLayoutParams(new ViewGroup.LayoutParams(-2,80));text.setMinimumWidth(17);text.setScaleX(.9f);text.setScaleY(.8f);
        Binder binding=new Binder(8,text);nativeCode.bind(binding,"native-auto");SingleNetworkLabelControls controls=new SingleNetworkLabelControls(nativeCode);controls.bound(binding);
        float originalDensity=text.getResources().getDisplayMetrics().density;
        try {
            for(boolean typography:new boolean[]{true,false})for(float density:new float[]{1f,3.40625f})
                    for(float scale:new float[]{0f,100f,175f})for(String label:new String[]{"5G","5GA","自定义 5G"}){
                text.getResources().getDisplayMetrics().density=density;Bundle saved=new Bundle();saved.putBoolean("label_enabled",true);saved.putBoolean("label_text_style_enabled",typography);
                FeatureOptions features=FeatureOptions.from(saved);controls.update(features,label,8,0,0,scale,0,720,Collections.emptyMap(),Collections.emptyMap());
                text.measure(0,0);text.layout(0,0,text.getMeasuredWidth(),text.getMeasuredHeight());
                CharSequence original=text.getText();int measured=text.getMeasuredWidth(),requests=text.layoutRequests;InkCanvas before=ink(text);
                equal(0,text.getMinimumWidth());if(scale==0)equal(0,measured);
                for(float legacyWidth:new float[]{22,80,0}){
                    controls.update(features,label,8,0,0,scale,legacyWidth,720,Collections.emptyMap(),Collections.emptyMap());
                    equal(true,original==text.getText());equal(requests,text.layoutRequests);equal(0,text.getMinimumWidth());
                    text.measure(0,0);equal(measured,text.getMeasuredWidth());InkCanvas after=ink(text);
                    equal(before.inkLeft,after.inkLeft);equal(before.inkRight,after.inkRight);equal(before.drawScale,after.drawScale);equal(before.drawScaleY,after.drawScaleY);equal(before.text,after.text);
                }
            }
            Bundle saved=new Bundle();saved.putBoolean("label_enabled",true);saved.putBoolean("label_text_style_enabled",true);FeatureOptions features=FeatureOptions.from(saved);
            controls.update(features,"5G",8,0,0,100,80,600,Collections.emptyMap(),Collections.emptyMap());text.measure(0,0);
            int width=text.getMeasuredWidth(),requests=text.layoutRequests;CharSequence original=text.getText();
            controls.update(features,"5GA",8,0,0,100,0,600,Collections.emptyMap(),Collections.emptyMap());text.measure(0,0);
            equal(true,text.getMeasuredWidth()>width);equal(true,text.layoutRequests>requests);equal(false,original==text.getText());
            int longer=text.getMeasuredWidth();controls.update(features,"5GA",8,0,0,175,22,600,Collections.emptyMap(),Collections.emptyMap());text.measure(0,0);
            equal(true,text.getMeasuredWidth()>longer);equal(1.75f,ink(text).drawScale);equal(1.75f,text.getScaleY());
            requests=text.layoutRequests;original=text.getText();controls.update(features,"5GA",8,0,0,175,80,900,Collections.emptyMap(),Collections.emptyMap());
            equal(false,original==text.getText());equal(true,text.layoutRequests>requests);equal(900f,ink(text).textEffectiveWeight);
            controls.update(options(false),"5GA",8,0,0,175,0,900,Collections.emptyMap(),Collections.emptyMap());
            equal(17,text.getMinimumWidth());equal(.9f,text.getScaleX());equal(.8f,text.getScaleY());equal("native-auto",text.getText());
        } finally {controls.releaseRuntime();text.getResources().getDisplayMetrics().density=originalDensity;}
    }
    private static void scaledInkBudget(){
        Native nativeCode=new Native();MeasuredLabel text=new MeasuredLabel();Binder row=new Binder(8,text);
        text.setMinimumWidth(17);text.setScaleX(.9f);text.setScaleY(.8f);text.setTranslationX(0);text.setTranslationY(2);
        float originalDensity=text.getResources().getDisplayMetrics().density;
        SingleNetworkLabelControls controls=new SingleNetworkLabelControls(nativeCode);nativeCode.bind(row,"native-LTE");controls.bound(row);
        Bundle saved=new Bundle();saved.putBoolean("label_enabled",true);
        try {
            for(float density:new float[]{1f,3.40625f})for(float scale:new float[]{1f,25f,100f,175f,250f,Float.MAX_VALUE})for(float minimum:new float[]{0f,22f,80f})
                    for(String label:new String[]{"5G","5GA","自定义 5G"}){
                text.getResources().getDisplayMetrics().density=density;
                controls.update(FeatureOptions.from(saved),label,8,0,0,scale,minimum,720,Collections.emptyMap(),Collections.emptyMap());
                text.measure(0,0);text.layout(0,0,text.getMeasuredWidth(),text.getMeasuredHeight());
                Spanned styled=(Spanned)text.getText();ReplacementSpan span=styled.getSpans(0,styled.length(),ReplacementSpan.class)[0];
                int advance=span.getSize(text.getPaint(),styled,0,styled.length(),null);
                InkCanvas canvas=new InkCanvas();canvas.scale(text.getScaleX(),text.getScaleY(),text.getMeasuredWidth()/2f,text.getMeasuredHeight()/2f);
                span.draw(canvas,styled,0,styled.length(),0,0,40,80,text.getPaint());
                checks++;if(canvas.inkLeft<-.01f||canvas.inkRight>text.getMeasuredWidth()+.01f)
                    throw new AssertionError("scaled network text escapes its native allocated width: scale="+scale+", min="+minimum+", width="+text.getMeasuredWidth()+", painted="+canvas.inkLeft+".."+canvas.inkRight);
                equal(advance,text.getMeasuredWidth());equal(0,text.getMinimumWidth());
                equal(0f,text.getTranslationX());equal(label,canvas.text);
                // Keep the existing external vertical transform: enlarging only the
                // drawing inside a fixed 20dp TextView line introduces new clipping.
                equal(canvas.drawScale,text.getScaleY());equal(canvas.drawScale,canvas.drawScaleY);
                InkCanvas internal=new InkCanvas();span.draw(internal,styled,0,styled.length(),0,0,40,80,text.getPaint());
                equal(1f,internal.drawScaleY);
                Paint.FontMetricsInt metrics=new Paint.FontMetricsInt();span.getSize(text.getPaint(),styled,0,styled.length(),metrics);
                equal((int)Math.round(20d*density),metrics.descent-metrics.ascent);
            }
            for(float minimum:new float[]{0f,22f}){
                controls.update(FeatureOptions.from(saved),"5G",8,0,0,0,minimum,720,Collections.emptyMap(),Collections.emptyMap());
                text.measure(0,0);Spanned styled=(Spanned)text.getText();ReplacementSpan span=styled.getSpans(0,styled.length(),ReplacementSpan.class)[0];
                InkCanvas canvas=new InkCanvas();span.draw(canvas,styled,0,styled.length(),0,0,40,80,text.getPaint());
                equal(null,canvas.text);equal(0,text.getMeasuredWidth());equal(1f,text.getScaleX());equal(0f,text.getScaleY());
            }
            controls.update(FeatureOptions.from(saved),"5G",8,7,-3,175,22,720,Collections.emptyMap(),Collections.emptyMap());
            equal(7f*text.getResources().getDisplayMetrics().density,text.getTranslationX());
            controls.update(options(false),"5G",8,0,0,175,22,720,Collections.emptyMap(),Collections.emptyMap());
            equal(17,text.getMinimumWidth());equal(.9f,text.getScaleX());equal(.8f,text.getScaleY());equal(0f,text.getTranslationX());equal(2f,text.getTranslationY());equal("native-LTE",text.getText());
        } finally {controls.releaseRuntime();text.getResources().getDisplayMetrics().density=originalDensity;}
    }
    private static void independentGlyphContract(){
        boolean keepPadding=View.keepPaddingForCheck;View.keepPaddingForCheck=true;
        Native nativeCode=new Native();SingleNetworkLabelControls controls=new SingleNetworkLabelControls(nativeCode);
        Bundle defaults=new Bundle();defaults.putBoolean("label_enabled",true);FeatureOptions own=FeatureOptions.from(defaults);
        Binder small=new Binder(8),large=new Binder(21);
        small.text.setTextSize(TypedValue.COMPLEX_UNIT_PX,8);large.text.setTextSize(TypedValue.COMPLEX_UNIT_PX,24);
        small.text.setScaleX(.8f);small.text.setScaleY(.9f);small.text.setLetterSpacing(.12f);
        small.text.setPadding(2,4,6,8);small.text.setIncludeFontPadding(true);
        nativeCode.bind(small,"native-small");nativeCode.bind(large,"native-large");controls.bound(small);controls.bound(large);
        controls.update(own,"5G",8,0,0,100,22,600,Collections.emptyMap(),Collections.emptyMap());
        equal(52f,small.text.getTextSize());equal(0f,small.text.getTranslationX());equal(0f,small.text.getTranslationY());
        equal(1f,small.text.getScaleX());equal(1f,small.text.getScaleY());
        equal(0f,small.text.getLetterSpacing());equal(false,small.text.getIncludeFontPadding());
        equal(0,small.text.getPaddingLeft());equal(0,small.text.getPaddingTop());equal(0,small.text.getPaddingRight());equal(0,small.text.getPaddingBottom());
        Spanned text=(Spanned)small.text.getText();ReplacementSpan[] spans=text.getSpans(0,text.length(),ReplacementSpan.class);
        equal(1,spans.length);ReplacementSpan span=spans[0];Paint.FontMetricsInt metrics=new Paint.FontMetricsInt();
        int advance=span.getSize(small.text.getPaint(),text,0,text.length(),metrics);
        equal(80,metrics.descent-metrics.ascent);equal(metrics.ascent,metrics.top);equal(metrics.descent,metrics.bottom);
        equal(Math.max(small.text.getMinimumWidth(),(int)Math.ceil(small.text.getPaint().measureText("5G")+8)),advance);
        Canvas canvas=new Canvas();span.draw(canvas,text,0,text.length(),7,10,50,90,small.text.getPaint());
        Rect glyph=new Rect();small.text.getPaint().getTextBounds("5G",0,2,glyph);
        equal(7+advance/2f-glyph.exactCenterX(),canvas.translateX+canvas.textX*canvas.scaleX);equal(50f-glyph.exactCenterY(),canvas.translateY+canvas.textY*canvas.scaleY);equal(52f,canvas.textSize);
        equal(600f,canvas.textEffectiveWeight);equal("5G",canvas.text);
        // Explicit network scope must win over a different legacy source. This
        // runs FontRepository.apply, rather than comparing a configuration key.
        defaults.putBoolean("font_enabled",true);defaults.putString(FontScopes.KEY,"{\"network_type\":\"system\"}");
        FontRepository.configure(new Context(),"pingfang","");FontRepository.configureScopes(defaults);
        controls.update(FeatureOptions.from(defaults),"5GA",8,2,-1,150,22,720,Collections.emptyMap(),Collections.emptyMap());
        spans=((Spanned)small.text.getText()).getSpans(0,3,ReplacementSpan.class);Canvas fontCanvas=new Canvas();
        spans[0].draw(fontCanvas,small.text.getText(),0,3,0,0,40,80,small.text.getPaint());
        equal(720f,fontCanvas.textEffectiveWeight);equal(52f,fontCanvas.textSize);equal("5GA",fontCanvas.text);
        equal(1.5f,paintedScale(small.text));equal(1f,small.text.getScaleX());equal(8f,small.text.getTranslationX());equal(-4f,small.text.getTranslationY());
        Map<String,Integer> tint=new LinkedHashMap<>();tint.put("label_color_light",0x40112233);tint.put("label_color_dark",0x40112233);
        Map<String,Boolean> alpha=new LinkedHashMap<>();alpha.put("label_color_light",true);alpha.put("label_color_dark",true);
        controls.update(own,"5G",8,0,0,100,22,600,tint,alpha);
        spans=((Spanned)small.text.getText()).getSpans(0,2,ReplacementSpan.class);Canvas tinted=new Canvas();
        spans[0].draw(tinted,small.text.getText(),0,2,0,0,40,80,small.text.getPaint());equal(0x40112233,tinted.textColor);equal(64,tinted.textAlpha);
        controls.update(own,"5G",8,0,0,100,22,600,tint,Collections.emptyMap());
        spans=((Spanned)small.text.getText()).getSpans(0,2,ReplacementSpan.class);Canvas inherited=new Canvas();
        spans[0].draw(inherited,small.text.getText(),0,2,0,0,40,80,small.text.getPaint());equal(0xcc112233,inherited.textColor);equal(204,inherited.textAlpha);
        FontRepository.releaseRuntime();
        controls.update(own,"5G",21,0,0,100,22,600,Collections.emptyMap(),Collections.emptyMap());
        equal(52f,large.text.getTextSize());equal(View.GONE,small.text.getVisibility());
        controls.update(options(false),"5G",21,0,0,100,22,600,Collections.emptyMap(),Collections.emptyMap());
        equal(8f,small.text.getTextSize());equal(24f,large.text.getTextSize());equal(.8f,small.text.getScaleX());equal(.9f,small.text.getScaleY());
        equal(.12f,small.text.getLetterSpacing());equal(true,small.text.getIncludeFontPadding());
        equal(2,small.text.getPaddingLeft());equal(4,small.text.getPaddingTop());equal(6,small.text.getPaddingRight());equal(8,small.text.getPaddingBottom());
        equal("native-small",small.text.getText());equal(false,small.text.getText() instanceof Spanned);
        controls.releaseRuntime();View.keepPaddingForCheck=keepPadding;
    }
    /** Replay the same display/layout projection as reload before reaching the real native binder. */
    private static void styleSwitchContract(){
        for(boolean size:new boolean[]{false,true})for(boolean typography:new boolean[]{false,true})
                for(boolean shared:new boolean[]{false,true})for(boolean position:new boolean[]{false,true}){
            Native nativeCode=new Native();SingleNetworkLabelControls controls=new SingleNetworkLabelControls(nativeCode);Binder row=new Binder(8);
            nativeCode.bind(row,"native-LTE");controls.bound(row);
            Bundle saved=new Bundle();saved.putBoolean("label_enabled",true);saved.putBoolean("label_size_enabled",size);
            saved.putBoolean("label_text_style_enabled",typography);saved.putBoolean("label_position_enabled",position);
            saved.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED,shared);saved.putBoolean("wifi_position_enabled",!position);
            saved.putFloat(StatusBarSettings.LABEL_OFFSET_Y,-7f);saved.putFloat(StatusBarSettings.WIFI_OFFSET_Y,3f);
            saved.putFloat(StatusBarSettings.LABEL_SCALE,175f);saved.putFloat(StatusBarSettings.SLOT_WIDTH,44f);
            Bundle projected=NetworkDisplaySettings.runtime(saved);controls.configureLayout(projected);projected=StatusBarLayoutSettings.runtime(projected);
            controls.update(FeatureOptions.from(projected),"5G",8,0,projected.getFloat(StatusBarSettings.LABEL_OFFSET_Y),175,44,750,Collections.emptyMap(),Collections.emptyMap());
            equal(0,row.text.getMinimumWidth());
            equal(size?1.75f:1f,paintedScale(row.text));equal(1f,row.text.getScaleX());equal(size?1.75f:1f,row.text.getScaleY());
            equal((shared?!position?3f:0f:position?-7f:0f)*row.text.getResources().getDisplayMetrics().density,row.text.getTranslationY());
            equal(52f,row.text.getTextSize()); // Size scales the glyph; it does not replace native text metrics.
            saved.putBoolean(StatusBarSettings.LABEL_HIDDEN,true);projected=NetworkDisplaySettings.runtime(saved);
            controls.configureLayout(projected);projected=StatusBarLayoutSettings.runtime(projected);
            controls.update(FeatureOptions.from(projected),"5G",8,0,projected.getFloat(StatusBarSettings.LABEL_OFFSET_Y),175,44,750,Collections.emptyMap(),Collections.emptyMap());
            equal(View.GONE,row.text.getVisibility());equal(View.GONE,row.image.getVisibility());
            saved.putBoolean("label_enabled",false);saved.putBoolean(StatusBarSettings.LABEL_HIDDEN,false);projected=NetworkDisplaySettings.runtime(saved);
            controls.configureLayout(projected);projected=StatusBarLayoutSettings.runtime(projected);
            controls.update(FeatureOptions.from(projected),"5G",8,0,projected.getFloat(StatusBarSettings.LABEL_OFFSET_Y),175,44,750,Collections.emptyMap(),Collections.emptyMap());
            equal("native-LTE",row.text.getText());equal(24,row.text.getMinimumWidth());equal(1f,row.text.getScaleX());equal(5f,row.text.getTranslationY());
            equal(View.VISIBLE,row.text.getVisibility());equal(View.VISIBLE,row.image.getVisibility());
            saved.putBoolean("label_enabled",true);projected=NetworkDisplaySettings.runtime(saved);
            controls.configureLayout(projected);projected=StatusBarLayoutSettings.runtime(projected);
            controls.update(FeatureOptions.from(projected),"5G",8,0,projected.getFloat(StatusBarSettings.LABEL_OFFSET_Y),175,44,750,Collections.emptyMap(),Collections.emptyMap());
            equal(0,row.text.getMinimumWidth());
            equal(size?1.75f:1f,paintedScale(row.text));equal(1f,row.text.getScaleX());equal(-7f,saved.getFloat(StatusBarSettings.LABEL_OFFSET_Y));equal(44f,saved.getFloat(StatusBarSettings.SLOT_WIDTH));
            controls.releaseRuntime();equal(24,row.text.getMinimumWidth());equal(20f,row.text.getTextSize());
        }
        Native nativeCode=new Native();SingleNetworkLabelControls controls=new SingleNetworkLabelControls(nativeCode);Binder row=new Binder(8);
        nativeCode.bind(row,"native-LTE");controls.bound(row);Bundle saved=new Bundle();saved.putBoolean("label_enabled",true);
        controls.update(FeatureOptions.from(saved),"5G+",8,0,0,200,0,850,Collections.emptyMap(),Collections.emptyMap());
        equal(0,row.text.getMinimumWidth());equal(2f,paintedScale(row.text));equal(1f,row.text.getScaleX());
        Spanned text=(Spanned)row.text.getText();ReplacementSpan span=text.getSpans(0,text.length(),ReplacementSpan.class)[0];
        int advance=span.getSize(row.text.getPaint(),text,0,text.length(),new Paint.FontMetricsInt());
        equal(true,advance>0);Canvas canvas=new Canvas();span.draw(canvas,text,0,text.length(),0,0,40,80,row.text.getPaint());
        equal("5G+",canvas.text);equal(52f,canvas.textSize);equal(850f,canvas.textEffectiveWeight);controls.releaseRuntime();
    }
    private static final class Mobile extends ViewGroup implements StatusIconDisplayable{
        Mobile(){super(null);}
        public String getSlot(){return "stacked_mobile";}public boolean isIconVisible(){return true;}public boolean isIconBlocked(){return false;}
        @Override public int getWidth(){return 188;}
    }
    private static final class GlyphLabel extends TextView{
        GlyphLabel(){super(new Context());setId(101);}
        private final android.content.res.Resources resources=new android.content.res.Resources(){
            @Override public int getIdentifier(String name,String type,String pkg){return "mobile_signal_data_outer".equals(name)?102:0;}
        };
        @Override public android.content.res.Resources getResources(){return resources;}
        @Override public int getWidth(){return 88;}
    }
    public static final class ParentParams extends ViewGroup.LayoutParams{
        public ParentParams(int width,int height){super(width,height);}
        public ParentParams(ViewGroup.LayoutParams nativeParams){super(nativeParams.width,nativeParams.height);}
    }
    /** Uses the real Controls + Placement + SharedSlot bridge through shared
     * on/off and native binding replacement. The fixture is not a solver. */
    private static void glyphSharedLifecycle()throws Exception{
        Native nativeCode=new Native();SingleNetworkLabelControls controls=new SingleNetworkLabelControls(nativeCode);
        NetworkSharedSlotLayout bridge=new NetworkSharedSlotLayout();bridge.resolve(SingleNetworkLabelControlsCheck.class.getClassLoader());
        StatusIconContainer status=new StatusIconContainer();Mobile mobile=new Mobile();status.addView(mobile);
        ConstraintLayout parent=new ConstraintLayout();mobile.addView(parent,new ParentParams(-2,80));parent.layout(8,17,188,97);
        GlyphLabel text=new GlyphLabel();View signal=new View(new Context()){@Override public int getWidth(){return 92;}};signal.setId(102);
        ConstraintLayout.LayoutParams original=new ConstraintLayout.LayoutParams(-2,-2),originalSignal=new ConstraintLayout.LayoutParams(-2,0);
        original.endToEnd=0;original.bottomToBottom=0;original.bottomMargin=38;original.setMarginEnd(58);originalSignal.topToTop=0;originalSignal.bottomToBottom=0;
        parent.addView(text,original);parent.addView(signal,originalSignal);text.layout(0,10,88,42);signal.layout(88,0,180,80);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX,8);text.setGravity(81);Binder first=new Binder(8,text);nativeCode.bind(first,"native-5G");controls.bound(first);
        controls.sharedLayout(bridge);Bundle values=new Bundle();values.putBoolean("label_enabled",true);values.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED,true);
        for(int cycle=0;cycle<3;cycle++){
            bridge.configure(true);controls.configureLayout(values);controls.update(FeatureOptions.from(values),"5G",8,0,0,150,22,600,Collections.emptyMap(),Collections.emptyMap());
            equal(52f,text.getTextSize());equal(1.5f,paintedScale(text));equal(1.5f,text.getScaleY());equal(0f,text.getTranslationY());
            // The real ConstraintLayout's following native traversal resolves
            // its signal -> label chain; this fixture is not a solver.
            text.layout(92,0,180,80);signal.layout(0,0,92,80);
            bridge.nativeMeasured(status);bridge.nativeLaidOut(status);
            NetworkSharedSlotLayout.Frame frame=bridge.frame(mobile);equal(true,frame!=null&&frame.labelPresent);
            equal(true,bridge.apply(frame,188,100,0,true,true));
            ConstraintLayout.LayoutParams placed=(ConstraintLayout.LayoutParams)text.getLayoutParams();
            equal(0,placed.topMargin);equal(0,placed.bottomMargin);equal(17,parent.getTop());equal(0,text.getTop());
            values.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED,false);bridge.configure(false);controls.configureLayout(values);
            controls.update(FeatureOptions.from(values),"5G",8,0,0,150,22,600,Collections.emptyMap(),Collections.emptyMap());
            equal(52f,text.getTextSize());equal(true,text.getText() instanceof Spanned);equal(1.5f,paintedScale(text));equal(1.5f,text.getScaleY());
            values.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED,true);
        }
        Binder replacement=new Binder(8,text);replacement.model="native-rebuilt";controls.bound(replacement);
        equal(52f,text.getTextSize());
        Bundle hidden=new Bundle();hidden.putBoolean("label_enabled",true);hidden.putBoolean(StatusBarSettings.LABEL_HIDDEN,true);
        controls.update(FeatureOptions.from(hidden),"5G",8,0,0,150,22,600,Collections.emptyMap(),Collections.emptyMap());equal(View.GONE,text.getVisibility());
        controls.update(options(false),"5G",8,0,0,150,22,600,Collections.emptyMap(),Collections.emptyMap());
        equal(original,text.getLayoutParams());equal(originalSignal,signal.getLayoutParams());equal(38,original.bottomMargin);equal(58,original.getMarginEnd());
        equal(8f,text.getTextSize());equal(1f,text.getScaleY());equal(81,text.getGravity());equal("native-rebuilt",text.getText());equal(false,text.getText() instanceof Spanned);
        controls.releaseRuntime();bridge.releaseRuntime();
    }
}
