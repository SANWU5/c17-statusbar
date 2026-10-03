package com.oplus.systemui.statusbar.phone.netspeed.widget;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Native XML replay: fixed-width host, MATCH_PARENT texts, number bottom/unit top margins. */
public final class NetworkSpeedView extends FrameLayout implements com.android.systemui.statusbar.StatusIconDisplayable {
    public com.oplus.systemui.statusbar.phone.netspeed.NetworkSpeedIconState mState;
    public boolean mBlocked;
    public String mSlot="network_speed";
    public int mVisibleState=-1,tint,stateUpdates;
    public final java.util.Map<Integer,Object> tags=new java.util.HashMap<>();
    public String getSlot(){return mSlot;}
    public void setSlot(String value){mSlot=value;}
    public boolean isIconVisible(){return mState!=null&&mState.visible;}
    public boolean isIconBlocked(){return mBlocked;}
    public void setBlocked(boolean value){mBlocked=value;}
    public void setVisibleState(int value,boolean animate){mVisibleState=value;}
    public void setIconTint(int color){tint=color;mSpeedNumber.setTextColor(color);mSpeedUnit.setTextColor(color);}
    public void applyNetworkState(com.oplus.systemui.statusbar.phone.netspeed.NetworkSpeedIconState state){
        if(state==null){mState=null;setVisibility(GONE);return;}
        stateUpdates++;mState=state.copy();mSpeedNumber.setText(Long.toString(state.speedText));mSpeedUnit.setText("KB/s");
    }
    @Override public Object getTag(int id){return tags.get(id);}
    public final NativeTextView mSpeedNumber=new NativeTextView(),mSpeedUnit=new NativeTextView();
    public boolean rtl;
    public int nativeHeight=24;
    public NetworkSpeedView(){
        super(null);setLayoutParams(new ViewGroup.MarginLayoutParams(24,ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams number=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER_HORIZONTAL);
        number.bottomMargin=9;mSpeedNumber.setLayoutParams(number);mSpeedNumber.setText("1.0");
        mSpeedNumber.setTextSize(0,10);
        FrameLayout.LayoutParams unit=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER_HORIZONTAL);
        unit.topMargin=11;mSpeedUnit.setLayoutParams(unit);mSpeedUnit.setText("KB/S");mSpeedUnit.setTextSize(0,6);
        addView(mSpeedNumber);addView(mSpeedUnit);
    }
    @Override public int getWidth(){return Math.max(0,getLayoutParams().width);}
    @Override public int getHeight(){return nativeHeight;}
    @Override public int getLayoutDirection(){return rtl?1:0;}
    public void applyNetworkState(String number,String unit){mSpeedNumber.setText(number);mSpeedUnit.setText(unit);}
    public void applyNativeConfiguration(int width,int numberBottom,int unitTop){
        getLayoutParams().width=width;
        ((FrameLayout.LayoutParams)mSpeedNumber.getLayoutParams()).bottomMargin=numberBottom;
        ((FrameLayout.LayoutParams)mSpeedUnit.getLayoutParams()).topMargin=unitTop;
    }
    public void nativeLayout(){place(mSpeedNumber);place(mSpeedUnit);}
    private void place(NativeTextView view){
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)view.getLayoutParams();
        int start=p.getMarginStart(),end=p.getMarginEnd();
        int left=rtl?end:start,right=rtl?start:end;
        int width=p.width<0?Math.max(0,getWidth()-left-right):p.width;
        int height=p.height<0?Math.max(0,getHeight()-p.topMargin-p.bottomMargin):p.height;
        int x;
        if((p.gravity&Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.CENTER_HORIZONTAL)
            x=(getWidth()-width)/2+left-right;
        else x=rtl?getWidth()-right-width:left;
        int y=(p.gravity&Gravity.VERTICAL_GRAVITY_MASK)==Gravity.CENTER_VERTICAL
                ?(getHeight()-height)/2+p.topMargin-p.bottomMargin: p.topMargin;
        view.left=x;view.top=y;view.width=width;view.height=height;
    }
    public static final class NativeTextView extends TextView {
        int left,top,width,height;
        NativeTextView(){super(null);}
        @Override public int getLeft(){return left;}
        @Override public int getTop(){return top;}
        @Override public int getWidth(){return width;}
        @Override public int getHeight(){return height;}
    }
}
