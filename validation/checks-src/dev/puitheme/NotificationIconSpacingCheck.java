package dev.puitheme;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.phone.NotificationIconContainer;

/** Replays the extracted native cursor/overflow math and the unchanged child slot dimensions. */
public final class NotificationIconSpacingCheck {
    private static int checks;
    private static void eq(Object expected,Object actual){checks++;if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.0001f)throw new AssertionError(expected+" != "+actual);}
    private static final class Host extends ViewGroup {
        Host(){super(null);}
        @Override public Object getTag(){return "notification_icon_area";}
        @Override public android.content.res.Resources getResources(){return new android.content.res.Resources(){@Override public String getResourceEntryName(int id){return "status_bar";}};}
        @Override public int getId(){return 202;}
    }
    private static final class Owner extends NotificationIconContainer {
        Owner(){super(null);getResources().getDisplayMetrics().density=1f;}
        @Override public int getHeight(){return 24;}
        void nativeMeasure(int spec){
            int count=(int)Math.min(getChildCount(),Math.max(0L,(long)mMaxIcons+1L));
            int width=count*20,mode=View.MeasureSpec.getMode(spec),limit=View.MeasureSpec.getSize(spec);
            if(mode==View.MeasureSpec.EXACTLY)width=limit;else if(mode==View.MeasureSpec.AT_MOST)width=Math.min(width,limit);
            setMeasuredDimension(width,24);
        }
    }
    private static final class Icon extends StatusBarIconView {
        Icon(){super(null);}
        @Override public int getWidth(){return 20;}
        @Override public int getHeight(){return 24;}
    }
    private static Bundle options(float gap){Bundle b=new Bundle();b.putBoolean(NotificationIconArea.MASTER,true);b.putBoolean(NotificationIconArea.SPACING_ENABLED,true);b.putFloat(NotificationIconArea.SPACING,gap);b.putBoolean(NotificationIconArea.SIZE_ENABLED,false);return b;}
    private static int nativePlan(NotificationIconArea area,Owner owner){
        float cursor=0;int overflow=-1,count=owner.getChildCount();
        try(NotificationIconArea.SpacingScope ignored=area.enterSpacingCalculation(owner)){
            for(int i=0;i<count;i++){
                boolean last=i==count-1;Object[] args={last,cursor,(float)owner.getMeasuredWidth(),20f};
                Object[] adjusted=area.adjustSpacingOverflow(args);
                float candidate=(Float)adjusted[1];
                boolean over=last?candidate+20>(Float)adjusted[2]:candidate+40>(Float)adjusted[2];
                if(overflow<0&&(i>=owner.mMaxIcons||over))overflow=i;
                cursor=area.spacingAdvance(cursor+20,1,20,1);
            }
        }
        return overflow<0?count:overflow;
    }
    public static void run()throws Exception{
        eq(false,NotificationIconArea.BOOLEANS.get(NotificationIconArea.SPACING_ENABLED));eq(0f,NotificationIconArea.NUMBERS.get(NotificationIconArea.SPACING));
        NotificationIconArea area=new NotificationIconArea();area.resolve(NotificationIconSpacingCheck.class.getClassLoader());
        Owner owner=new Owner();new Host().addView(owner);for(int i=0;i<3;i++)owner.addView(new Icon());area.beforeLayout(owner);
        eq(true,area.setSpacingReady(true));eq(false,area.needsSpacingAdvance());
        int roomy=View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.AT_MOST),narrow=View.MeasureSpec.makeMeasureSpec(65,View.MeasureSpec.AT_MOST);
        area.configure(null,options(5));owner.nativeMeasure(roomy);area.measureSpacing(owner,roomy);eq(70,owner.getMeasuredWidth());eq(24,owner.getMeasuredHeight());eq(3,nativePlan(area,owner));
        // The extracted native RTL branch mirrors the cursor using the unchanged physical child width.
        try(NotificationIconArea.SpacingScope ignored=area.enterSpacingCalculation(owner)){
            float first=0,second=area.spacingAdvance(first+20,1,20,1),third=area.spacingAdvance(second+20,1,20,1);
            near(5,second-first-20);near(5,third-second-20);
            float rtlFirst=owner.getMeasuredWidth()-first-20,rtlSecond=owner.getMeasuredWidth()-second-20,rtlThird=owner.getMeasuredWidth()-third-20;
            near(5,rtlFirst-rtlSecond-20);near(5,rtlSecond-rtlThird-20);near(0,rtlThird);
        }
        owner.mMaxIcons=1;owner.nativeMeasure(roomy);area.measureSpacing(owner,roomy);eq(45,owner.getMeasuredWidth());eq(1,nativePlan(area,owner));owner.mMaxIcons=Integer.MAX_VALUE;
        owner.nativeMeasure(narrow);area.measureSpacing(owner,narrow);eq(65,owner.getMeasuredWidth());eq(1,nativePlan(area,owner)); // Native dot replaces the next icon before reaching the boundary.
        for(int i=0;i<1000;i++){owner.nativeMeasure(roomy);area.measureSpacing(owner,roomy);eq(70,owner.getMeasuredWidth());eq(3,nativePlan(area,owner));}
        near(0,owner.getChildAt(1).getTranslationX());near(1,owner.getChildAt(1).getScaleX());eq(20,owner.getChildAt(1).getWidth());
        try(NotificationIconArea.SpacingScope ignored=area.enterSpacingCalculation(owner)){
            eq(true,area.needsSpacingAdvance());near(12.5f,area.spacingAdvance(10,.5f,20,1));near(45,area.spacingAdvance(40,1,20,2));
            try(NotificationIconArea.SpacingScope other=area.enterSpacingCalculation(new NotificationIconContainer(null))){eq(false,area.needsSpacingAdvance());near(20,area.spacingAdvance(20,1,20,1));}
            eq(true,area.needsSpacingAdvance());
            Object[] last={true,40f,65f,20f};eq(last,area.adjustSpacingOverflow(last));
        }
        eq(false,area.needsSpacingAdvance());
        area.configure(null,options(-5));owner.nativeMeasure(roomy);area.measureSpacing(owner,roomy);eq(50,owner.getMeasuredWidth());eq(3,nativePlan(area,owner));
        area.configure(null,options(-1000));owner.nativeMeasure(roomy);area.measureSpacing(owner,roomy);eq(22,owner.getMeasuredWidth());eq(3,nativePlan(area,owner));
        near(-19,NotificationIconArea.spacingExtra(20,1,-1000));near(0,NotificationIconArea.spacingExtra(0,1,5));
        area.configure(null,options(5));int exact=View.MeasureSpec.makeMeasureSpec(64,View.MeasureSpec.EXACTLY);owner.nativeMeasure(exact);area.measureSpacing(owner,exact);eq(64,owner.getMeasuredWidth());eq(1,nativePlan(area,owner));
        Bundle off=options(5);off.putBoolean(NotificationIconArea.SPACING_ENABLED,false);area.configure(null,off);owner.nativeMeasure(roomy);area.measureSpacing(owner,roomy);eq(60,owner.getMeasuredWidth());eq(3,nativePlan(area,owner));
        Bundle masterOff=options(5);masterOff.putBoolean(NotificationIconArea.MASTER,false);area.configure(null,masterOff);owner.nativeMeasure(roomy);area.measureSpacing(owner,roomy);eq(60,owner.getMeasuredWidth());eq(3,nativePlan(area,owner));
        area.configure(null,options(5));Owner shelf=new Owner();for(int i=0;i<3;i++)shelf.addView(new Icon());shelf.nativeMeasure(roomy);area.measureSpacing(shelf,roomy);eq(60,shelf.getMeasuredWidth());eq(3,nativePlan(area,shelf));
        for(float gap:new float[]{0f,Float.NaN,Float.POSITIVE_INFINITY}){area.configure(null,options(gap));owner.nativeMeasure(roomy);area.measureSpacing(owner,roomy);eq(60,owner.getMeasuredWidth());eq(3,nativePlan(area,owner));}
        Bundle heart=options(5);heart.putString(NotificationIconArea.MODE,"heart");area.configure(null,heart);owner.nativeMeasure(roomy);area.measureSpacing(owner,roomy);eq(60,owner.getMeasuredWidth());
        area.configure(null,options(5));eq(false,area.setSpacingReady(false));owner.nativeMeasure(roomy);area.measureSpacing(owner,roomy);eq(60,owner.getMeasuredWidth());eq(3,nativePlan(area,owner));
        area.setSpacingReady(true);Bundle safe=options(5);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);area.configure(null,safe);owner.nativeMeasure(roomy);area.measureSpacing(owner,roomy);eq(60,owner.getMeasuredWidth());
        area.configure(null,options(5));area.releaseRuntime();owner.nativeMeasure(roomy);area.measureSpacing(owner,roomy);eq(60,owner.getMeasuredWidth());eq(false,area.needsSpacingAdvance());
        System.out.println(checks+" spacing checks passed (native cursor/dot bounds, parent constraints, animation, non-accumulation, mode scope and restoration)");
    }
    public static void main(String[] args)throws Exception{run();}
}
