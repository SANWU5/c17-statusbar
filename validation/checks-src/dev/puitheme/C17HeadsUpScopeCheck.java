package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.NotificationBackgroundView;
import com.oplus.posteffect.drawable.BlendDrawable;
import com.oplus.systemui.notification.headsup.windowframe.HeadsUpLayout;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView;
import com.oplus.systemui.statusbar.notification.row.NotificationBackgroundViewExtImp;
import java.lang.reflect.Field;
import java.util.Map;

/** Same OEM row/background type moves between the heads-up window and shade. */
public final class C17HeadsUpScopeCheck {
    private static int checks;
    private static void equal(boolean expected,boolean actual){checks++;if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
    private static void equal(int expected,int actual){checks++;if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.00001f)throw new AssertionError(expected+" != "+actual);}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Bundle settings(boolean master,boolean shade,boolean control,boolean headsUp){
        Bundle result=new Bundle();result.putBoolean(C17HighlightRemoval.ENABLED,master);
        result.putBoolean(C17HighlightRemoval.NOTIFICATION_ENABLED,shade);result.putBoolean(C17HighlightRemoval.CONTROL_ENABLED,control);
        result.putBoolean(C17HighlightRemoval.HEADS_UP_ENABLED,headsUp);return result;
    }
    private static final class Parent extends ViewGroup {
        int parentReads;
        Parent(){super(new Context());}
        @Override public ViewParent getParent(){parentReads++;return super.getParent();}
    }
    private static final class Card {
        final NotificationBackgroundView host=new NotificationBackgroundView();
        final ExpandableNotificationRow row=new ExpandableNotificationRow(new Context(),100,0,0);
        final BlendDrawable engine=new BlendDrawable();
        final C17HighlightRemovalCheck.NativeMaterial material=new C17HighlightRemovalCheck.NativeMaterial();
        final C17HighlightRemovalCheck.HookedShader shader;
        Card(C17HighlightRemoval helper,ViewGroup parent)throws Throwable{
            parent.addView(row);row.addView(host);shader=new C17HighlightRemovalCheck.HookedShader(helper);material.shader=shader;
            material.stroke.values[7]=.2f;material.stroke.values[8]=.4f;material.optics.values[3]=.6f;
            material.inner.values[3]=.1f;material.inner.values[4]=.3f;material.inner.values[5]=.5f;
            material.stroke.pushUniforms(shader);material.optics.pushUniforms(shader);material.inner.pushUniforms(shader);
            QsTileAppearance.setField(engine,"drawableShader",material);bind(helper);
        }
        void bind(C17HighlightRemoval helper){try(C17HighlightRemoval.SurfaceScope scope=helper.beginSurface(host)){helper.bindDrawable(engine);}}
        void optics(boolean removed){
            near(removed?0:.2f,shader.floats.get("u_edgeArray")[7]);near(removed?0:.4f,shader.floats.get("u_edgeArray")[8]);
            near(removed?0:.6f,shader.floats.get("u_opticsArray")[3]);
            near(removed?0:.1f,shader.floats.get("u_shadowArray")[3]);near(removed?0:.3f,shader.floats.get("u_shadowArray")[4]);
            near(removed?0:.5f,shader.floats.get("u_shadowArray")[5]);
        }
    }
    public static int run()throws Throwable{
        C17HighlightRemoval helper=new C17HighlightRemoval();Parent shade=new Parent();HeadsUpLayout window=new HeadsUpLayout();
        Parent headsUpParent=new Parent();window.addView(headsUpParent);
        Card message=new Card(helper,headsUpParent),notification=new Card(helper,shade);
        OplusQSResizeableTileView control=new OplusQSResizeableTileView();BlendDrawable controlEngine=new BlendDrawable();
        C17HighlightRemovalCheck.NativeMaterial controlMaterial=new C17HighlightRemovalCheck.NativeMaterial();
        C17HighlightRemovalCheck.HookedShader controlShader=new C17HighlightRemovalCheck.HookedShader(helper);
        controlMaterial.shader=controlShader;controlMaterial.optics.values[3]=.8f;QsTileAppearance.setField(controlEngine,"drawableShader",controlMaterial);
        controlMaterial.optics.pushUniforms(controlShader);
        try(C17HighlightRemoval.SurfaceScope scope=helper.beginSurface(control)){helper.bindDrawable(controlEngine);}
        // Old/imported configurations omit the scope: it defaults ON, but the master remains OFF.
        helper.configure(new Bundle());message.optics(false);equal(false,helper.skipHeadsUpEdge(message.host));
        Bundle legacy=new Bundle();legacy.putBoolean(C17HighlightRemoval.ENABLED,true);
        legacy.putBoolean(C17HighlightRemoval.NOTIFICATION_ENABLED,false);legacy.putBoolean(C17HighlightRemoval.CONTROL_ENABLED,false);helper.configure(legacy);
        message.optics(true);notification.optics(false);equal(true,helper.skipHeadsUpEdge(message.host));
        // All three scopes are independent even though both notification cards use the same Java class.
        for(int mask=0;mask<16;mask++){
            boolean master=(mask&1)!=0,shadeOn=(mask&2)!=0,controlOn=(mask&4)!=0,headsUpOn=(mask&8)!=0;
            helper.configure(settings(master,shadeOn,controlOn,headsUpOn));
            message.optics(master&&headsUpOn);notification.optics(master&&shadeOn);near(master&&controlOn?0:.8f,controlShader.floats.get("u_opticsArray")[3]);
            equal(master&&headsUpOn,helper.skipHeadsUpEdge(message.host));equal(false,helper.skipHeadsUpEdge(notification.host));
            equal(false,helper.skipHeadsUpEdge(control));equal(false,helper.skipHeadsUpEdge(new View(new Context())));
            equal(master&&headsUpOn,helper.skipSpotlight(message.host));equal(master&&shadeOn,helper.skipSpotlight(notification.host));
        }
        helper.configure(settings(true,false,false,true));int reads=headsUpParent.parentReads;
        for(int frame=0;frame<1000;frame++){
            message.bind(helper);equal(true,helper.skipHeadsUpEdge(message.host));
            message.material.optics.pushUniforms(message.shader);message.optics(true);
        }
        equal(reads,headsUpParent.parentReads); // Cached row ownership: no ancestor walk on stable frames.
        // An explicit shade owner cannot inherit its enclosing heads-up draw's enabled scope.
        try(C17HighlightRemoval.SurfaceScope scope=helper.beginSurface(message.host)){
            equal(false,helper.skipSpotlight(notification.host));equal(false,helper.skipSpotlight(new View(new Context())));
            equal(true,helper.skipSpotlight(null));
        }
        // Moving the same row into the shade must restore every optical layer before recording.
        message.row.parent=shade;message.bind(helper);message.optics(false);equal(false,helper.skipHeadsUpEdge(message.host));
        message.row.parent=headsUpParent;message.bind(helper);message.optics(true);
        // A moved intermediate container has stable row parent identity: the native state callback retires the cache.
        headsUpParent.parent=shade;helper.notificationStateChanged(new NotificationBackgroundViewExtImp(message.host));
        message.bind(helper);message.optics(false);equal(false,helper.skipHeadsUpEdge(message.host));
        headsUpParent.parent=window;helper.notificationStateChanged(new NotificationBackgroundViewExtImp(message.host));message.bind(helper);message.optics(true);
        // Notification card unification never tints a heads-up background/icon/content.
        Bundle tint=settings(true,true,true,true);tint.putBoolean(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED,true);helper.configure(tint);
        Canvas canvas=new Canvas();int[] nativeDraws={0};
        helper.drawNotificationBackground(message.host,canvas,c->{nativeDraws[0]++;return null;});equal(0,canvas.layers);equal(1,nativeDraws[0]);
        helper.drawNotificationBackground(notification.host,canvas,c->{nativeDraws[0]++;return null;});equal(1,canvas.layers);equal(2,nativeDraws[0]);
        Bundle safe=settings(true,true,true,true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);helper.configure(safe);
        message.optics(false);notification.optics(false);equal(false,helper.skipHeadsUpEdge(message.host));
        helper.configure(settings(true,false,false,true));message.optics(true);helper.detach(message.host);message.optics(false);
        equal(false,helper.skipSpotlight(message.host));message.bind(helper);message.optics(true);
        helper.release();message.optics(false);equal(false,helper.skipHeadsUpEdge(message.host));
        equal(0,((Map<?,?>)field(helper,"notificationOwners")).size());return checks;
    }
    public static void main(String[] args)throws Throwable{System.out.println("C17HeadsUpScopeCheck: "+run()+" checks passed");}
}
