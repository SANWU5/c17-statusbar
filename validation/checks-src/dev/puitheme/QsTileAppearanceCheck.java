package dev.puitheme;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne;
import com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable;
import com.oplus.systemui.qs.base.res.drawable.GradientTileDrawable;
import com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar;
import com.oplus.posteffect.drawable.BlendDrawable;
import com.oplus.posteffect.agsl.ShaderBlendParam;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;
import java.util.*;

/** Global base fills, exact isolation, native animation and reversible recording state. */
public final class QsTileAppearanceCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(expected+" != "+actual);}
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.0002f)throw new AssertionError(expected+" != "+actual);}
    public static final class State {public int state;public String spec;State(int state,String spec){this.state=state;this.spec=spec;}}
    public static final class Icon {public Drawable circle;private Drawable getBgDrawable(){return circle;}private Drawable getThemeDrawable(){return circle;}private Drawable getIconBgDrawable(){return circle;}}
    public static final class ResourceView extends View {
        final String name;final Resources resources;
        ResourceView(String name){super(new Context());this.name=name;resources=new Resources(){@Override public String getResourceEntryName(int id){return ResourceView.this.name;}};}
        @Override public Resources getResources(){return resources;}
    }
    private static Bundle settings(){Bundle settings=new Bundle();settings.putBoolean(QsTileAppearance.MASTER,true);settings.putInt("qs_global_light_color",0xff123456);settings.putInt("qs_global_dark_color",0xff654321);return settings;}
    private static MixColorTileDrawable attach(QsTileAppearance a,OplusQSResizeableTileView tile,String spec,int state){tile.state=new State(state,spec);MixColorTileDrawable bg=new MixColorTileDrawable();bg.setBounds(0,0,120,120);tile.bg=bg;a.refreshTile(tile);return bg;}
    private static void colors(Shader value,int a,int b){equal(true,value instanceof LinearGradient);LinearGradient g=(LinearGradient)value;equal(a,g.colors[0]);equal(b,g.colors[1]);}
    public static void main(String[] args) throws Throwable {
        QsTileAppearance a=new QsTileAppearance();OplusQSResizeableTileView tile=new OplusQSResizeableTileView();Canvas c=new Canvas();MixColorTileDrawable bg=attach(a,tile,"wifi",2);
        a.configure(new Bundle());a.drawTile(bg,c,bg::draw);equal(null,bg.seenShader);equal(3,QsTileAppearance.BOOLEANS.size());equal(4,QsTileAppearance.NUMBERS.size());equal(4,QsTileAppearance.COLORS.size());equal(false,QsTileAppearance.BOOLEANS.get(QsTileAppearance.MASTER));
        for(String key:QsTileAppearance.BOOLEANS.keySet())equal(false,key.startsWith("qs_style_"));
        Bundle s=settings();a.configure(s);bg.maskColor=0x80ffffff;bg.alpha=128;Shader texture=new LinearGradient(0,0,1,1,new int[]{-1,-1},new float[]{0,1},Shader.TileMode.CLAMP);bg.highlightPaint.setShader(texture);
        a.drawTile(bg,c,bg::draw);colors(bg.seenShader,0xff123456,0xff123456);equal(64,bg.seenAlpha);equal(128,bg.alpha);equal(0x80ffffff,bg.maskColor);equal(null,bg.paint.getShader());equal(texture,bg.seenHighlight);equal(texture,bg.highlightPaint.getShader());
        tile.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_YES;a.drawTile(bg,c,bg::draw);colors(bg.seenShader,0xff654321,0xff654321);tile.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_NO;
        s.putFloat("qs_global_light_opacity",50f);s.putInt("qs_global_light_color",0x80123456);a.configure(s);a.drawTile(bg,c,bg::draw);colors(bg.seenShader,0x40123456,0x40123456);equal(64,bg.seenAlpha);
        s.putFloat("qs_global_light_opacity",0f);a.configure(s);a.drawTile(bg,c,bg::draw);colors(bg.seenShader,0x00123456,0x00123456);equal(64,bg.seenAlpha);
        s=settings();s.putBoolean("qs_global_light_gradient_enabled",true);s.putInt("qs_global_light_gradient_color",0x80654321);a.configure(s);a.drawTile(bg,c,bg::draw);colors(bg.seenShader,0xff123456,0x80654321);
        bg.maskColor=0xffabcdef;a.drawTile(bg,c,bg::draw);equal(null,bg.seenShader);bg.maskColor=-1;((State)tile.state).state=1;a.drawTile(bg,c,bg::draw);equal(null,bg.seenShader);((State)tile.state).state=2;
        bg.paint.setShader(texture);a.drawTile(bg,c,bg::draw);equal(texture,bg.seenShader);equal(texture,bg.paint.getShader());bg.paint.setShader(null);
        try{a.drawTile(bg,c,target->{equal(true,bg.paint.getShader() instanceof LinearGradient);throw new IllegalStateException("native");});throw new AssertionError();}catch(IllegalStateException expected){equal("native",expected.getMessage());}equal(null,bg.paint.getShader());
        a.drawTile(bg,c,target->a.drawTile(bg,target,bg::draw));colors(bg.seenShader,0xff123456,0x80654321);equal(null,bg.paint.getShader());
        OplusQSResizeableTileView large=new OplusQSResizeableTileViewTwoXOne();MixColorTileDrawable card=attach(a,large,"cell",2),circle=new MixColorTileDrawable();circle.setBounds(0,0,60,60);Icon icon=new Icon();icon.circle=circle;large.icon=icon;a.refreshTile(large);
        a.drawTile(card,c,card::draw);equal(null,card.seenShader);a.drawTile(circle,c,circle::draw);colors(circle.seenShader,0xff123456,0x80654321);equal(null,circle.paint.getShader());equal("cell",((State)large.state).spec);
        GradientTileDrawable gradient=new GradientTileDrawable();gradient.setBounds(0,0,100,100);icon.circle=gradient;a.refreshTile(large);gradient.colorDrawable.color=0x40ffffff;a.drawTile(gradient,c,gradient::draw);colors(gradient.seenShader,0xff123456,0x80654321);equal(null,gradient.paint.getShader());equal(64,gradient.paint.getAlpha());gradient.colorDrawable.color=0xff0099ff;a.drawTile(gradient,c,gradient::draw);equal(null,gradient.seenShader);
        MixColorTileDrawable unrelated=new MixColorTileDrawable();unrelated.setBounds(0,0,100,100);a.drawTile(unrelated,c,unrelated::draw);equal(null,unrelated.seenShader);a.detach(tile);a.drawTile(bg,c,bg::draw);equal(null,bg.seenShader);
        for(String spec:new String[]{"wifi","cell","battery","flashlight","music","custom(example)"}){OplusQSResizeableTileView item=new OplusQSResizeableTileView();MixColorTileDrawable fill=attach(a,item,spec,2);a.drawTile(fill,c,fill::draw);colors(fill.seenShader,0xff123456,0x80654321);}
        OplusQsVerticalSeekBar slider=new OplusQsVerticalSeekBar();slider.parent=new ResourceView("qs_volume_slider_layout");Paint thumb=new Paint(1);thumb.setShader(texture);Shader[] seen={null};
        a.drawSlider(slider,c,target->{slider.mProgressPaint.setColor(0x70ffffff);seen[0]=slider.mProgressPaint.getShader();equal(texture,thumb.getShader());});colors(seen[0],0xff123456,0x80654321);equal(112,slider.mProgressPaint.getAlpha());equal(null,slider.mProgressPaint.getShader());
        slider.mProgressColor=0xff0099ff;a.drawSlider(slider,c,target->equal(null,slider.mProgressPaint.getShader()));slider.mProgressColor=-1;slider.parent=new ResourceView("volume_dialog_rows");a.drawSlider(slider,c,target->equal(null,slider.mProgressPaint.getShader()));slider.parent=new ResourceView("brightness_slider");
        AutoBlurDrawable active=new AutoBlurDrawable();active.setBounds(0,0,100,300);slider.activeMixColorDrawable=active;BlendDrawable engine=active.viewBlurProxy.actual.blurDrawable;engine.setBounds(0,0,100,300);RuntimeShader original=engine.drawableShader.shader;
        a.drawSlider(slider,c,target->a.drawBlur(active,target,active::draw));equal(true,engine.contentDirty);a.drawGlassContent(engine,c,engine::onDrawContent);equal(original,engine.drawableShader.shader);equal(null,engine.drawableShaderPaint.getShader());equal(true,engine.recorded!=original);
        RuntimeShader recorded=engine.recorded;equal(true,recorded.source.contains("nativeGlassHighlight nativeOptics nativeInnerShadow"));equal(true,recorded.source.contains("c17QsBase(vec4(u_multiBlendParams"));colors(recorded.inputs.get("c17QsFill"),0xff123456,0x80654321);near(15f,recorded.floats.get("c17QsSlots")[0]);near(20f,recorded.floats.get("c17QsSlots")[1]);near(17f,recorded.floats.get("nativeGlassHighlight")[0]);near(17f,recorded.floats.get("nativeOptics")[0]);equal(1,recorded.ints.get("uEnableBlend"));equal(0x73e6e6e6,engine.drawableShader.multiBlendParam.get(2).color);equal(0x40cccccc,engine.drawableShader.multiBlendParam.get(3).color);
        try{a.drawGlassContent(engine,c,target->{equal(true,engine.drawableShader.shader!=original);throw new IllegalStateException("record");});throw new AssertionError();}catch(IllegalStateException expected){equal("record",expected.getMessage());}equal(original,engine.drawableShader.shader);equal(null,engine.drawableShaderPaint.getShader());
        engine.contentDirty=false;s.putBoolean(QsTileAppearance.MASTER,false);a.configure(s);equal(true,engine.contentDirty);a.drawGlassContent(engine,c,engine::onDrawContent);equal(original,engine.recorded);equal(original,engine.drawableShader.shader);
        s.putBoolean(QsTileAppearance.MASTER,true);a.configure(s);engine.drawableShader.multiBlendParam.get(2).color=0xff123456;a.drawGlassContent(engine,c,engine::onDrawContent);equal(original,engine.recorded);engine.drawableShader.multiBlendParam.get(2).color=0x7de6e6e6;slider.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_YES;a.drawGlassContent(engine,c,engine::onDrawContent);colors(engine.recorded.inputs.get("c17QsFill"),0xff654321,0xff654321);
        a.detach(slider);a.drawGlassContent(engine,c,engine::onDrawContent);equal(original,engine.recorded);equal(null,QsNativeGlassFill.decorate("unknown program"));equal(null,QsNativeGlassFill.decorate(recorded.source));
        AutoBlurDrawable iconGlass=new AutoBlurDrawable();iconGlass.setBounds(0,0,60,60);circle.maskColor=0;circle.child=iconGlass;icon.circle=circle;a.refreshTile(large);
        BlendDrawable iconEngine=iconGlass.viewBlurProxy.actual.blurDrawable;iconEngine.setBounds(0,0,60,60);RuntimeShader iconNative=iconEngine.drawableShader.shader;
        a.drawTile(circle,c,target->a.drawBlur(iconGlass,target,iconGlass::draw));equal(true,iconEngine.contentDirty);a.drawGlassContent(iconEngine,c,iconEngine::onDrawContent);equal(true,iconEngine.recorded!=iconNative);equal(iconNative,iconEngine.drawableShader.shader);colors(iconEngine.recorded.inputs.get("c17QsFill"),0xff123456,0x80654321);
        ((State)large.state).state=1;a.drawGlassContent(iconEngine,c,iconEngine::onDrawContent);equal(iconNative,iconEngine.recorded);((State)large.state).state=2;
        AutoBlurDrawable wholeCardGlass=new AutoBlurDrawable();wholeCardGlass.setBounds(0,0,120,120);card.child=wholeCardGlass;a.refreshTile(large);BlendDrawable bigEngine=wholeCardGlass.viewBlurProxy.actual.blurDrawable;bigEngine.setBounds(0,0,120,120);RuntimeShader bigNative=bigEngine.drawableShader.shader;
        a.drawTile(card,c,target->a.drawBlur(wholeCardGlass,target,wholeCardGlass::draw));a.drawGlassContent(bigEngine,c,bigEngine::onDrawContent);equal(bigNative,bigEngine.recorded);
        List<ShaderBlendParam> copied=new ArrayList<>();for(ShaderBlendParam p:engine.drawableShader.multiBlendParam)copied.add(new ShaderBlendParam(p.mode,p.color));equal(null,QsNativeGlassFill.activeSlots(engine.drawableShader.multiBlendParam,copied));
        equal(0xff112233,QsTileAppearance.color("#112233",0));equal(0x80112233,QsTileAppearance.color("#80112233",0));equal(0,QsTileAppearance.color("#00000000",1));equal(123,QsTileAppearance.color("invalid",123));near(42f,QsTileAppearance.number(Float.NaN,42f));near(42f,QsTileAppearance.number("Infinity",42f));near(1.23f,QsTileAppearance.number("1.23",42f));near(359f,QsTileAppearance.angle(-1f));near(0f,QsTileAppearance.angle(720f));
        for(int color:new int[]{0xffffffff,0x80123456,0x00123456,0xff000000})for(float opacity:new float[]{0,25,50,100,200,-10})for(int alpha:new int[]{0,128,255}){int value=QsTileAppearance.withOpacity(color,opacity,alpha);equal(color&0xffffff,value&0xffffff);equal(true,(value>>>24)>=0&&(value>>>24)<=255);}
        for(float angle:new float[]{0,45,90,135,180,225,270,315}){LinearGradient g=(LinearGradient)QsTileAppearance.shader(new QsTileAppearance.Style(0xff112233,0xff445566,50f,angle,true),new Rect(0,0,100,200));equal(0x80112233,g.colors[0]);equal(0x80445566,g.colors[1]);near(50f,(g.left+g.right)/2f);near(100f,(g.top+g.bottom)/2f);}
        equal(0,c.layers);equal(0,c.rects);equal(0,c.nodeDraws);System.out.println("QsTileAppearanceCheck passed: "+checks);
    }
}
