package dev.puitheme;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.ShapeDrawable;
import android.os.Bundle;
import com.oplus.systemui.notification.clearall.OplusClearAllButton;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Real scene draw and durable raw migration; no device, live preferences or UI automation. */
public final class NotificationClearAppearanceCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Clear appearance "+checks+": "+expected+" != "+actual);}
    private static final class Store {
        final ActivationGuardPreferencesCheck.MemoryPreferences memory=new ActivationGuardPreferencesCheck.MemoryPreferences();
        int fail;
        final SharedPreferences preferences=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(owner,method,args)->{
            if(!method.getName().equals("edit"))return method.invoke(memory,args);
            SharedPreferences.Editor delegate=memory.edit();
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(editor,operation,parameters)->{
                if(operation.getName().equals("commit")){delegate.commit();if(fail>0){fail--;return false;}return true;}
                if(operation.getName().equals("apply")){delegate.apply();return null;}
                operation.invoke(delegate,parameters);return editor;
            });
        });
    }
    private static void migration(){
        Store old=new Store(),metadata=new Store();
        old.memory.values.put(NotificationClearAppearance.MASTER,true);
        old.memory.values.put(NotificationClearAppearance.GRADIENT_ENABLED,true);
        old.memory.values.put(NotificationClearAppearance.OPACITY,37.125f);
        old.memory.values.put(NotificationClearAppearance.GRADIENT_ANGLE,-22.5f);
        old.memory.values.put(NotificationClearAppearance.COLOR_LIGHT,0x80112233);
        old.memory.values.put(StatusBarSettings.alphaKey(NotificationClearAppearance.COLOR_LIGHT),false);
        Map<String,Object> before=new LinkedHashMap<>(old.memory.values);
        Map<String,Object> inherited=NotificationClearAppearance.inheritedLandscape(before);
        equal(12,inherited.size());equal(true,NotificationClearAppearance.migrate(old.preferences,metadata.preferences,true));
        for(String key:before.keySet())equal(before.get(key),old.memory.values.get(key));
        for(String key:inherited.keySet())equal(inherited.get(key),old.memory.values.get(key));
        old.memory.values.put(NotificationClearAppearance.COLOR_LIGHT,0xffabcdef);
        old.memory.values.put(NotificationClearAppearance.MASTER,false);
        equal(true,NotificationClearAppearance.migrate(old.preferences,metadata.preferences,true));
        equal(0x80112233,old.memory.values.get(NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT));
        equal(true,old.memory.values.get(NotificationClearAppearance.LANDSCAPE_MASTER));
        equal(false,old.memory.values.get(StatusBarSettings.alphaKey(NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT)));

        Store fresh=new Store(),freshMeta=new Store();
        equal(false,NotificationClearAppearance.migrate(fresh.preferences,freshMeta.preferences,false));equal(0,freshMeta.memory.writes);
        equal(true,NotificationClearAppearance.migrate(fresh.preferences,freshMeta.preferences,true));equal(true,fresh.memory.values.isEmpty());
        fresh.memory.values.put(NotificationClearAppearance.MASTER,true);
        equal(true,NotificationClearAppearance.migrate(fresh.preferences,freshMeta.preferences,true));
        equal(false,fresh.memory.values.get(NotificationClearAppearance.LANDSCAPE_MASTER));

        Store failed=new Store(),failedMeta=new Store();failed.memory.values.put(NotificationClearAppearance.MASTER,true);failed.fail=1;
        equal(false,NotificationClearAppearance.migrate(failed.preferences,failedMeta.preferences,true));
        equal(false,failedMeta.memory.values.containsKey("notification_clear_landscape_migrated_v62"));
        equal(false,PreferenceWrites.values(failed.preferences).containsKey(NotificationClearAppearance.LANDSCAPE_MASTER));
        equal(true,NotificationClearAppearance.migrate(failed.preferences,failedMeta.preferences,true));
        equal(true,failed.memory.values.get(NotificationClearAppearance.LANDSCAPE_MASTER));

        Store metaFailed=new Store(),badMeta=new Store();metaFailed.memory.values.put(NotificationClearAppearance.MASTER,true);badMeta.fail=1;
        equal(false,NotificationClearAppearance.migrate(metaFailed.preferences,badMeta.preferences,true));
        equal(false,PreferenceWrites.values(badMeta.preferences).containsKey("notification_clear_landscape_migrated_v62"));
        equal(true,NotificationClearAppearance.migrate(metaFailed.preferences,badMeta.preferences,true));
        Store malformed=new Store(),unmarked=new Store();malformed.memory.values.put(NotificationClearAppearance.OPACITY,"invalid");
        equal(false,NotificationClearAppearance.migrate(malformed.preferences,unmarked.preferences,true));equal(0,unmarked.memory.writes);
    }
    private static Shader draw(NotificationClearAppearance appearance,OplusClearAllButton button,ShapeDrawable background) throws Throwable {
        Shader[] seen={null};appearance.drawButton(button,new Canvas(),canvas->seen[0]=background.getPaint().getShader());
        equal(null,background.getPaint().getShader());return seen[0];
    }
    private static void glass(NotificationClearAppearance appearance,OplusClearAllButton button,Bundle settings) throws Throwable {
        com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable wrapper=new com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable();
        com.oplus.posteffect.drawable.BlendDrawable engine=wrapper.viewBlurProxy.actual.blurDrawable;
        engine.setBounds(0,0,96,48);wrapper.setBounds(0,0,96,48);button.setBackground(wrapper);
        com.oplus.posteffect.agsl.DrawableShader nativeShader=engine.drawableShader;
        nativeShader.multiBlendParam.clear();nativeShader.summaryBlendParam.clear();
        nativeShader.multiBlendParam.add(new com.oplus.posteffect.agsl.ShaderBlendParam(5,0xffaabbcc));
        nativeShader.multiBlendParam.add(new com.oplus.posteffect.agsl.ShaderBlendParam(3,0xffddeeff));
        nativeShader.summaryBlendParam.addAll(nativeShader.multiBlendParam);
        android.graphics.RuntimeShader original=nativeShader.shader;
        settings.putBoolean(StatusBarSettings.SAFE_MODE,false);appearance.configure(settings);appearance.bind(button);
        button.getResources().getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        appearance.drawBlur(wrapper,new Canvas(),canvas->appearance.drawGlassContent(engine,canvas,engine::onDrawContent));
        equal(true,engine.recorded!=original);equal(original,nativeShader.shader);equal(null,engine.drawableShaderPaint.getShader());
        LinearGradient fill=(LinearGradient)engine.recorded.inputs.get("c17QsFill");equal(0x40123456,fill.colors[0]);equal(0x10998877,fill.colors[1]);
        engine.contentDirty=false;
        button.getResources().getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        appearance.drawBlur(wrapper,new Canvas(),canvas->appearance.drawGlassContent(engine,canvas,engine::onDrawContent));
        equal(true,engine.contentDirty);equal(original,engine.recorded);equal(original,nativeShader.shader);
        button.getResources().getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        appearance.drawBlur(wrapper,new Canvas(),canvas->appearance.drawGlassContent(engine,canvas,engine::onDrawContent));
        equal(0x40123456,((LinearGradient)engine.recorded.inputs.get("c17QsFill")).colors[0]);equal(original,nativeShader.shader);
        engine.contentDirty=false;settings.putBoolean(NotificationClearAppearance.LANDSCAPE_MASTER,false);appearance.configure(settings);
        equal(true,engine.contentDirty);
        appearance.drawBlur(wrapper,new Canvas(),canvas->appearance.drawGlassContent(engine,canvas,engine::onDrawContent));
        // Disabled customization preserves the paint update performed by native rendering.
        equal(original,engine.recorded);equal(original,nativeShader.shader);equal(original,engine.drawableShaderPaint.getShader());
    }
    public static void main(String[] args) throws Throwable {
        migration();
        equal(false,NotificationClearAppearance.BOOLEANS.get(NotificationClearAppearance.LANDSCAPE_MASTER));
        equal(false,NotificationClearAppearance.BOOLEANS.get(NotificationClearAppearance.LANDSCAPE_GRADIENT_ENABLED));
        NotificationClearAppearance appearance=new NotificationClearAppearance();OplusClearAllButton button=new OplusClearAllButton();
        ShapeDrawable background=new ShapeDrawable();background.setBounds(0,0,96,48);button.setBackground(background);
        button.setAlpha(.7f);button.setTranslationX(12f);button.setTranslationY(23f);
        Bundle settings=new Bundle();settings.putBoolean(NotificationClearAppearance.MASTER,true);
        settings.putInt(NotificationClearAppearance.COLOR_LIGHT,0xffff1122);
        settings.putBoolean(NotificationClearAppearance.LANDSCAPE_MASTER,true);
        settings.putInt(NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT,0x80123456);
        settings.putBoolean(StatusBarSettings.alphaKey(NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT),true);
        settings.putInt(NotificationClearAppearance.LANDSCAPE_COLOR_DARK,0xff778899);
        settings.putInt(NotificationClearAppearance.LANDSCAPE_GRADIENT_COLOR_LIGHT,0x20998877);
        settings.putBoolean(StatusBarSettings.alphaKey(NotificationClearAppearance.LANDSCAPE_GRADIENT_COLOR_LIGHT),true);
        settings.putBoolean(NotificationClearAppearance.LANDSCAPE_GRADIENT_ENABLED,true);
        settings.putFloat(NotificationClearAppearance.LANDSCAPE_OPACITY,50f);appearance.configure(settings);
        button.getResources().getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        equal(0xffff1122,((LinearGradient)draw(appearance,button,background)).colors[0]);
        button.getResources().getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        LinearGradient landscape=(LinearGradient)draw(appearance,button,background);
        equal(0x40123456,landscape.colors[0]);equal(0x10998877,landscape.colors[1]);
        button.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_YES;
        equal(0x80778899,((LinearGradient)draw(appearance,button,background)).colors[0]);
        settings.putBoolean(NotificationClearAppearance.LANDSCAPE_MASTER,false);appearance.configure(settings);
        equal(null,draw(appearance,button,background));
        button.getResources().getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        button.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_NO;
        equal(0xffff1122,((LinearGradient)draw(appearance,button,background)).colors[0]);
        settings.putBoolean(NotificationClearAppearance.LANDSCAPE_MASTER,true);settings.putBoolean(NotificationClearAppearance.MASTER,false);appearance.configure(settings);
        equal(null,draw(appearance,button,background));
        button.getResources().getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        equal(0x40123456,((LinearGradient)draw(appearance,button,background)).colors[0]);
        settings.putBoolean(StatusBarSettings.SAFE_MODE,true);appearance.configure(settings);equal(null,draw(appearance,button,background));
        equal(.7f,button.getAlpha());equal(12f,button.getTranslationX());equal(23f,button.getTranslationY());
        glass(appearance,button,settings);
        appearance.configure(null);equal(null,draw(appearance,button,background));appearance.forget(button);
        System.out.println("NotificationClearAppearanceCheck passed: "+checks);
    }
}
