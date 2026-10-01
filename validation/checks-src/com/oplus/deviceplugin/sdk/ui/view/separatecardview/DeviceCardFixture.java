package com.oplus.deviceplugin.sdk.ui.view.separatecardview;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
/** Native cards use a dedicated inner background; their content getter returns the outer card. */
public class DeviceCardFixture extends View {
    public final Body body;
    public final GradientDrawable outer=new GradientDrawable(),foreground=new GradientDrawable(),icon=new GradientDrawable();
    public final CardResources resources;
    public boolean bodyPresent=true;
    public DeviceCardFixture(String nativeName){
        super(new Context());resources=new CardResources(nativeName);body=new Body();body.parent=this;
        outer.setColor(-1);foreground.setColor(-1);icon.setColor(-1);
    }
    public final class CardResources extends Resources {
        final String nativeName;
        public String bodyName,bodyPackage="com.android.systemui";
        CardResources(String name){nativeName=name;bodyName=name;}
        @Override public int getIdentifier(String name,String type,String pkg){return nativeName.equals(name)&&"id".equals(type)&&"com.android.systemui".equals(pkg)?37:0;}
        @Override public String getResourceEntryName(int id){return bodyName;}
        @Override public String getResourcePackageName(int id){return bodyPackage;}
    }
    public final class Body extends View {
        public Drawable background;
        Body(){super(new Context());}
        public Drawable getBackground(){return background;}
        public void setBackground(Drawable value){background=value;if(value!=null)value.setCallback(this);}
        @Override public int getId(){return 37;}
        @Override public Resources getResources(){return resources;}
    }
    @Override public Resources getResources(){return resources;}
    @Override public View findViewById(int id){return id==37&&bodyPresent?body:null;}
    public View getContentView(){return this;}
    public Drawable getBackground(){return outer;}
    public void drawNative(Canvas canvas){outer.draw(canvas);if(body.background!=null)body.background.draw(canvas);foreground.draw(canvas);icon.draw(canvas);}
}
