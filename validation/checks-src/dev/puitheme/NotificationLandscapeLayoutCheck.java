package dev.puitheme;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

/** Original native dimensions and transforms survive rotation, closing, and module release. */
public final class NotificationLandscapeLayoutCheck {
    private static int checks;
    private static void expect(boolean result){checks++;if(!result)throw new AssertionError("Landscape notification layout "+checks);}
    private static final class Column extends ViewGroup {
        int left,width,padLeft,padRight,sidePadding=64;
        Column(int w){super(new Context());width=w;}
        @Override public int getWidth(){return width;}
        @Override public int getLeft(){return left;}
        @Override public int getPaddingLeft(){return padLeft;}
        @Override public int getPaddingRight(){return padRight;}
        public int getSidePaddings(){return sidePadding;}
        @Override public void offsetLeftAndRight(int delta){left+=delta;}
    }
    public static void main(String[] args){
        NotificationLandscapeLayout owner=new NotificationLandscapeLayout();
        Column parent=new Column(3168),stack=new Column(1440);parent.addView(stack);
        stack.getResources().getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        int portrait=View.MeasureSpec.makeMeasureSpec(1440,View.MeasureSpec.EXACTLY);
        expect(owner.measure(stack,portrait,false)==portrait);
        stack.getResources().getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        int horizontal=View.MeasureSpec.makeMeasureSpec(3168,View.MeasureSpec.EXACTLY);
        expect(View.MeasureSpec.getSize(owner.measure(stack,horizontal,true))==1440);
        expect(View.MeasureSpec.getSize(owner.measure(stack,horizontal,true,150f))==2160);
        expect(View.MeasureSpec.getSize(owner.measure(stack,horizontal,true,50f))==720);
        int echoed=View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY);
        for(int i=0;i<1000;i++)expect(View.MeasureSpec.getSize(owner.measure(stack,echoed,true,50f))==720);
        expect(owner.columnWidth(stack,3168,50f)==720);
        expect(owner.sidePadding(stack,1)==64);
        stack.sidePadding=80;owner.measure(stack,horizontal,true,100f);
        expect(owner.sidePadding(stack,1)==80);
        // A child can be mid-collapse or still laid out at the previous width.
        // The clock's alignment source remains the native constrained measurement.
        stack.width=100;expect(owner.columnWidth(stack,3168,100f)==1440);
        expect(owner.columnLeft(stack,1440)==864);stack.width=1440;
        // A saved 200% trial clamps once to the actual native space, without storing
        // that constrained output as a portrait baseline or multiplying it again.
        int narrowNative=View.MeasureSpec.makeMeasureSpec(1800,View.MeasureSpec.EXACTLY);
        for(int i=0;i<1000;i++)expect(View.MeasureSpec.getSize(owner.measure(stack,narrowNative,true,200f))==1800);
        expect(View.MeasureSpec.getSize(owner.measure(stack,horizontal,true,200f))==2880);
        int doubleColumn=View.MeasureSpec.makeMeasureSpec(2880,View.MeasureSpec.EXACTLY);
        for(int i=0;i<1000;i++)expect(View.MeasureSpec.getSize(owner.measure(stack,doubleColumn,true,200f))==2880);
        expect(View.MeasureSpec.getSize(owner.measure(stack,horizontal,true,100f))==1440);
        expect(owner.columnWidth(stack,3168,100f)==1440);
        expect(View.MeasureSpec.getSize(owner.measure(stack,horizontal,true,Float.MAX_VALUE))==3168);
        expect(View.MeasureSpec.getSize(owner.measure(stack,horizontal,true,Float.NaN))==1440);
        expect(View.MeasureSpec.getSize(owner.measure(stack,horizontal,true,0f))==1);
        expect(owner.measure(stack,View.MeasureSpec.makeMeasureSpec(1,View.MeasureSpec.EXACTLY),false)==horizontal);
        stack.setTranslationX(73);stack.setTranslationY(-21);stack.setAlpha(.63f);
        for(int i=0;i<1000;i++){
            owner.layout(stack,true);expect(stack.left==864);
            expect(stack.getTranslationX()==73&&stack.getTranslationY()==-21&&stack.getAlpha()==.63f);
        }
        stack.left=48;owner.layout(stack,true);expect(stack.left==864);
        owner.release(stack);expect(stack.left==48);
        expect(owner.measure(stack,horizontal,false)==horizontal);
        parent.padLeft=80;owner.layout(stack,true);expect(stack.left==904);
        stack.left=301;owner.release(stack);expect(stack.left==301);
        stack.getResources().getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        expect(owner.measure(stack,portrait,false)==portrait);
        owner.detach(stack);expect(stack.left==301);
        NotificationLandscapeLayout rotationOwner=new NotificationLandscapeLayout();
        stack.getResources().getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        rotationOwner.measure(stack,portrait,false);
        stack.getResources().getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        expect(View.MeasureSpec.getSize(rotationOwner.measure(stack,horizontal,true,50f))==720);
        stack.getResources().getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        rotationOwner.measure(stack,echoed,false);
        stack.getResources().getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        expect(View.MeasureSpec.getSize(rotationOwner.measure(stack,horizontal,true,100f))==1440);
        int oldWidth=stack.getResources().getDisplayMetrics().widthPixels;
        int oldHeight=stack.getResources().getDisplayMetrics().heightPixels;
        stack.getResources().getDisplayMetrics().widthPixels=2400;
        stack.getResources().getDisplayMetrics().heightPixels=1080;
        expect(rotationOwner.columnWidth(stack,2400,100f)==1080);
        int resized=View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY);
        expect(View.MeasureSpec.getSize(rotationOwner.measure(stack,resized,true,100f))==1080);
        expect(rotationOwner.columnWidth(stack,2400,100f)==1080);
        stack.getResources().getDisplayMetrics().widthPixels=oldWidth;
        stack.getResources().getDisplayMetrics().heightPixels=oldHeight;
        Column coldStack=new Column(1);
        coldStack.getResources().getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        expect(View.MeasureSpec.getSize(new NotificationLandscapeLayout().measure(coldStack,horizontal,true))
                ==Math.min(oldWidth,oldHeight));
        NotificationLandscapeLayout early=new NotificationLandscapeLayout();
        coldStack.getResources().getConfiguration().orientation=Configuration.ORIENTATION_UNDEFINED;
        early.measure(coldStack,horizontal,false);
        coldStack.getResources().getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        expect(View.MeasureSpec.getSize(early.measure(coldStack,horizontal,true))==Math.min(oldWidth,oldHeight));
        stack.getResources().getConfiguration().orientation=Configuration.ORIENTATION_UNDEFINED;
        System.out.println("Notification landscape layout checks passed: "+checks);
    }
}
