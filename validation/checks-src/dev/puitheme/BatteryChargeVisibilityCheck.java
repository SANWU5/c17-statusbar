package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.util.Collections;

/** Verifies hidden presentation never falsifies the OEM charging state. */
public final class BatteryChargeVisibilityCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static Bundle settings(boolean hidden,boolean cycle,boolean style){
        Bundle b=new Bundle();b.putBoolean("battery_enabled",true);b.putBoolean(BatteryControls.HIDE_CHARGE,hidden);
        b.putBoolean(StatusBarSettings.BATTERY_CHARGE_INSIDE,cycle);b.putBoolean("battery_color_enabled",false);
        b.putBoolean(BatteryTextStyle.MASTER,style);b.putFloat(BatteryTextStyle.SIZE,14);
        return b;
    }
    public static void main(String[] args)throws Exception{
        Handler h=new Handler(Looper.getMainLooper());
        BatteryControls controls=new BatteryControls(h,BatteryControlsCheck.Meter.class,BatteryControlsCheck.Horizontal.class,BatteryControlsCheck.Charge.class);
        BatteryControlsCheck.Meter owner=new BatteryControlsCheck.Meter(new android.content.Context());
        BatteryControlsCheck.Horizontal first=new BatteryControlsCheck.Horizontal(),other=new BatteryControlsCheck.Horizontal();
        first.chargeIconId=8;first.setCallback(owner);owner.drawable=first;owner.charge.iconId=8;
        View external=new View(owner.getContext());controls.sync(owner,owner.charge,external);
        controls.configure(settings(true,true,false),StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        check(external.getVisibility()==View.GONE,"external charging icon hidden");
        check(h.delayed==null,"hidden icon doesn't run charging animation timer");
        check(controls.chargeIconId(first,8)==8&&first.getChargeIconId()==8,"ordinary getter retains actual state");
        check(owner.charge.isVisible&&owner.charge.iconId==8,"native charging model not mutated");
        try(BatteryControls.ChargeDrawScope outer=controls.beginChargeDraw(first)){
            check(controls.chargeIconId(first,8)==0,"only active native draw suppresses bolt");
            check(controls.chargeIconId(other,8)==8,"unowned drawable unchanged");
            try(BatteryControls.ChargeDrawScope inner=controls.beginChargeDraw(other)){
                check(controls.chargeIconId(first,8)==8,"nested draw isolates its own owner");
                throw new IllegalStateException("native drawing failure");
            }catch(IllegalStateException expected){}
            check(controls.chargeIconId(first,8)==0,"exception restores enclosing draw scope");
        }
        check(controls.chargeIconId(first,8)==8,"draw exit restores getter semantics");
        check(!controls.drawContent(first,new Canvas(),new RectF(0,0,50,20)),"hide alone leaves ordinary native percentage drawing");
        controls.configure(settings(true,true,true),StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());Canvas styled=new Canvas();
        check(controls.drawContent(first,styled,new RectF(0,0,50,20))&&styled.text!=null&&styled.pathAlpha==-1,"custom percentage remains without bolt");
        controls.configure(settings(false,true,false),StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        check(h.delayed!=null,"turning hide off resumes actual charging cycle");
        try(BatteryControls.ChargeDrawScope scope=controls.beginChargeDraw(first)){check(controls.chargeIconId(first,8)==8,"disabled hide leaves native bolt");}
        controls.configure(settings(false,false,false),StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        check(external.getVisibility()==View.VISIBLE&&h.delayed==null,"neither hide nor cycle restores native external icon");
        Bundle safe=settings(true,true,true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);
        controls.configure(safe,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        try(BatteryControls.ChargeDrawScope scope=controls.beginChargeDraw(first)){check(controls.chargeIconId(first,8)==8,"safe mode preserves native getter");}
        check(external.getVisibility()==View.VISIBLE,"safe mode restores external icon");
        controls.configure(settings(true,false,false),StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        controls.detach(owner);check(external.getVisibility()==View.VISIBLE,"detach restores external icon");
        try(BatteryControls.ChargeDrawScope scope=controls.beginChargeDraw(first)){check(controls.chargeIconId(first,8)==8,"detached drawable isn't owned");}
        check(first.getChargeIconId()==8&&owner.charge.iconId==8&&owner.charge.isVisible,"all scenarios retain real charging model");
        System.out.println("BatteryChargeVisibilityCheck: "+checks+" checks passed");
    }
}
