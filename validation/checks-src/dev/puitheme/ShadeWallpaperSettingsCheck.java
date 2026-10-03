package dev.puitheme;

import android.os.Bundle;
import java.util.HashSet;
import java.util.Objects;

public final class ShadeWallpaperSettingsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Wallpaper settings "+checks+": "+expected+" != "+actual);}
    public static void main(String[] args) {
        equal(6,ShadeWallpaperSettings.SCENES.size());equal(6,new HashSet<>(ShadeWallpaperSettings.SCENES).size());
        equal(false,ShadeWallpaperSettings.BOOLEANS.get(ShadeWallpaperSettings.MASTER));
        equal(false,ShadeWallpaperSettings.available());
        equal("未完成的开发",ShadeWallpaperSettings.unavailableReason());
        equal("未完成的开发",ShadeWallpaperSettings.unavailableReason(ShadeWallpaperSettings.MASTER));
        equal("",ShadeWallpaperSettings.unavailableReason(null));equal("",ShadeWallpaperSettings.unavailableReason("tiles_enabled"));
        for(String scene:ShadeWallpaperSettings.SCENES) {
            equal(false,ShadeWallpaperSettings.BOOLEANS.get(ShadeWallpaperSettings.enabledKey(scene)));
            equal(100f,ShadeWallpaperSettings.NUMBERS.get(ShadeWallpaperSettings.brightnessKey(scene)));
            equal("",ShadeWallpaperSettings.STRINGS.get(ShadeWallpaperSettings.revisionKey(scene)));
            equal(true,ShadeWallpaperSettings.title(scene).contains(scene.endsWith("landscape")?"横屏":"竖屏"));
            Bundle values=new Bundle();values.putBoolean(ShadeWallpaperSettings.enabledKey(scene),true);
            for(String other:ShadeWallpaperSettings.SCENES)equal(false,ShadeWallpaperSettings.enabled(values,other));
            equal(true,values.getBoolean(ShadeWallpaperSettings.enabledKey(scene),false));
            equal("未完成的开发",ShadeWallpaperSettings.unavailableReason(ShadeWallpaperSettings.revisionKey(scene)));
        }
        equal(ShadeWallpaperSettings.CLASSIC_PORTRAIT,ShadeWallpaperSettings.scene("classic",true,false));
        equal(ShadeWallpaperSettings.CLASSIC_LANDSCAPE,ShadeWallpaperSettings.scene("classic",false,true));
        equal(ShadeWallpaperSettings.NOTIFICATION_PORTRAIT,ShadeWallpaperSettings.scene("separate",false,false));
        equal(ShadeWallpaperSettings.NOTIFICATION_LANDSCAPE,ShadeWallpaperSettings.scene("separate",false,true));
        equal(ShadeWallpaperSettings.CONTROL_PORTRAIT,ShadeWallpaperSettings.scene("separate",true,false));
        equal(ShadeWallpaperSettings.CONTROL_LANDSCAPE,ShadeWallpaperSettings.scene("separate",true,true));
        equal(null,ShadeWallpaperSettings.scene("unknown",true,false));equal(null,ShadeWallpaperSettings.scene(null,false,true));
        equal(0f,ShadeWallpaperSettings.brightness(-4f));equal(200f,ShadeWallpaperSettings.brightness(204f));
        equal(100f,ShadeWallpaperSettings.brightness(Float.NaN));equal(100f,ShadeWallpaperSettings.brightness(Float.POSITIVE_INFINITY));
        equal(100f,ShadeWallpaperSettings.brightness("0"));equal(76.5f,ShadeWallpaperSettings.brightness(76.5d));
        for(String revision:new String[]{"",null,"../font/current",new String(new char[64]).replace('\0','G'),"a".repeat(63),"a".repeat(65)})
            equal(false,ShadeWallpaperSettings.validRevision(revision));
        equal(true,ShadeWallpaperSettings.validRevision("0123456789abcdef".repeat(4)));
        System.out.println("Shade wallpaper settings checks passed: "+checks);
    }
}
