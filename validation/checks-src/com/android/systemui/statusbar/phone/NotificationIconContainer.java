package com.android.systemui.statusbar.phone;

/** Real class name and ViewGroup ownership, without a renderer or notification pipeline. */
public class NotificationIconContainer extends android.view.ViewGroup {
    public int mMaxIcons=Integer.MAX_VALUE;
    public int stateUpdates;
    public boolean failNextStateUpdate;
    public NotificationIconContainer(android.content.Context context) { super(context); }
    public final void setMaxIconsAmount(int count){mMaxIcons=count;}
    /** Exact native order: field is already written when the optional processor can throw. */
    public final void updateState(){
        stateUpdates++;
        if(failNextStateUpdate){failNextStateUpdate=false;throw new IllegalStateException("native processor");}
        for(int i=0;i<getChildCount();i++){
            android.view.View child=getChildAt(i);
            if(child instanceof com.android.systemui.statusbar.StatusBarIconView)
                ((com.android.systemui.statusbar.StatusBarIconView)child).visibleState=i<mMaxIcons?0:i==mMaxIcons?1:2;
        }
    }
}
