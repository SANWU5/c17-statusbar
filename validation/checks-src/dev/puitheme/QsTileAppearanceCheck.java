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
import com.oplus.deviceplugin.sdk.ui.view.separatecardview.*;
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
    private static void deviceCards(QsTileAppearance appearance,Canvas canvas,Shader texture) throws Throwable {
        Bundle config=settings();config.putBoolean("qs_global_light_gradient_enabled",true);config.putInt("qs_global_light_gradient_color",0xffabcdef);config.putFloat("qs_global_light_opacity",50f);config.putFloat("qs_global_dark_opacity",50f);appearance.configure(config);
        DeviceCardFixture[] cards={new RectangleDeviceCardView(),new SquareDeviceCardView(),new NoDeviceEntranceCardView(),new RectangleEntranceCardView(),new SquareEntranceCardView()};
        for(DeviceCardFixture card:cards) {
            android.graphics.drawable.GradientDrawable fill=new android.graphics.drawable.GradientDrawable();fill.setColor(0x80999999);fill.mStrokePaint.setShader(texture);card.body.setBackground(fill);
            // The first native draw has not initialized background bounds yet.
            appearance.drawDeviceCard(card,canvas,card::drawNative);colors(fill.seenShader,0x80123456,0x80abcdef);equal(128,fill.seenAlpha);equal(0x80999999,fill.mFillPaint.getColor());equal(null,fill.mFillPaint.getShader());equal(texture,fill.seenStroke);
            equal(null,card.outer.seenShader);equal(null,card.foreground.seenShader);equal(null,card.icon.seenShader);equal(true,QsTileAppearance.isDeviceCard(card));
            card.resources.getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_YES;appearance.drawDeviceCard(card,canvas,card::drawNative);colors(fill.seenShader,0x80654321,0x80654321);card.resources.getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_NO;
            int invalidations=card.invalidations;appearance.configure(config);equal(true,card.invalidations>invalidations);
        }
        DeviceCardFixture card=cards[0];android.graphics.drawable.GradientDrawable fill=(android.graphics.drawable.GradientDrawable)card.body.getBackground();
        card.resources.bodyName="oplus_qs_tile_icon_bg";appearance.drawDeviceCard(card,canvas,card::drawNative);equal(null,fill.seenShader);card.resources.bodyName="rectangleCoLayout";
        card.resources.bodyPackage="other.package";appearance.drawDeviceCard(card,canvas,card::drawNative);equal(null,fill.seenShader);card.resources.bodyPackage="com.android.systemui";
        card.bodyPresent=false;appearance.drawDeviceCard(card,canvas,card::drawNative);equal(null,fill.seenShader);card.bodyPresent=true;
        DeviceCardFixture unknown=new DeviceCardFixture("rectangleCoLayout");unknown.body.setBackground(fill);appearance.drawDeviceCard(unknown,canvas,unknown::drawNative);equal(null,fill.seenShader);equal(false,QsTileAppearance.isDeviceCard(unknown));card.body.setBackground(fill);
        fill.mGradientState.mColors=new int[]{-1,0xff0099ff};appearance.drawDeviceCard(card,canvas,card::drawNative);equal(null,fill.seenShader);fill.mGradientState.mColors=null;
        fill.mFillPaint.setShader(texture);appearance.drawDeviceCard(card,canvas,card::drawNative);equal(texture,fill.seenShader);equal(texture,fill.mFillPaint.getShader());fill.mFillPaint.setShader(null);
        try {appearance.drawDeviceCard(card,canvas,target->{equal(true,fill.mFillPaint.getShader() instanceof LinearGradient);throw new IllegalStateException("device");});throw new AssertionError();}catch(IllegalStateException expected){equal("device",expected.getMessage());}equal(null,fill.mFillPaint.getShader());
        android.graphics.drawable.ShapeDrawable shape=new android.graphics.drawable.ShapeDrawable();shape.getPaint().setColor(0x401875f5);shape.setBounds(0,0,110,50);card.body.setBackground(shape);appearance.drawDeviceCard(card,canvas,card::drawNative);colors(shape.seenShader,0x80123456,0x80abcdef);equal(64,shape.seenAlpha);equal(null,shape.getPaint().getShader());
        config.putBoolean(QsTileAppearance.MASTER,false);appearance.configure(config);appearance.drawDeviceCard(card,canvas,card::drawNative);equal(null,shape.seenShader);config.putBoolean(QsTileAppearance.MASTER,true);appearance.configure(config);
        AutoBlurDrawable blur=new AutoBlurDrawable();blur.setBounds(0,0,100,50);card.body.setBackground(blur);BlendDrawable engine=blur.viewBlurProxy.actual.blurDrawable;engine.setBounds(0,0,100,50);RuntimeShader original=engine.drawableShader.shader;
        appearance.drawDeviceCard(card,canvas,target->appearance.drawBlur(blur,target,blur::draw));appearance.drawGlassContent(engine,canvas,engine::onDrawContent);equal(true,engine.recorded!=original);equal(original,engine.drawableShader.shader);
        for(int[] pair:new int[][]{{0x19404040,0x4d737373},{0x80404040,0xb2737373},{0x40404040,0x667b7b7b},{0x5a404040,0xb27b7b7b}}) {
            engine.drawableShader.multiBlendParam.get(2).color=pair[0];engine.drawableShader.multiBlendParam.get(3).color=pair[1];appearance.drawGlassContent(engine,canvas,engine::onDrawContent);equal(true,engine.recorded!=original);colors(engine.recorded.inputs.get("c17QsFill"),0x80123456,0x80abcdef);equal(pair[0],engine.drawableShader.multiBlendParam.get(2).color);equal(pair[1],engine.drawableShader.multiBlendParam.get(3).color);equal(original,engine.drawableShader.shader);
            // The same native gray material stays native on a regular inactive tile or slider.
            QsNativeGlassFill nativeFill=new QsNativeGlassFill();equal(null,nativeFill.prepare(engine,new QsTileAppearance.Style(-1,-1,100f,0f,false)));
        }
        engine.drawableShader.multiBlendParam.get(3).mode=2;appearance.drawGlassContent(engine,canvas,engine::onDrawContent);equal(original,engine.recorded);engine.drawableShader.multiBlendParam.get(3).mode=3;
        ArrayList<ShaderBlendParam> copies=new ArrayList<>();for(ShaderBlendParam p:engine.drawableShader.multiBlendParam)copies.add(new ShaderBlendParam(p.mode,p.color));equal(null,QsNativeGlassFill.inactiveDeviceSlots(engine.drawableShader.multiBlendParam,copies));
        appearance.detach(card);appearance.drawGlassContent(engine,canvas,engine::onDrawContent);equal(original,engine.recorded);
    }
    private static void pressFrames(QsTileAppearance appearance,Canvas canvas) throws Throwable {
        appearance.configure(settings());OplusQSResizeableTileView tile=new OplusQSResizeableTileView();MixColorTileDrawable fill=attach(appearance,tile,"rotation",2);fill.animator.running=true;fill.alpha=200;
        for(int color:new int[]{0x80808080,0x80acacac,0x80cccccc,0x80eeeeee}) {
            fill.maskColor=color;appearance.drawTile(fill,canvas,fill::draw);colors(fill.seenShader,0xff123456,0xff123456);equal(100,fill.seenAlpha);equal(color,fill.maskColor);equal(null,fill.paint.getShader());
        }
        fill.animator.running=false;fill.maskColor=0x80999999;appearance.drawTile(fill,canvas,fill::draw);equal(null,fill.seenShader);
        fill.deforming=true;appearance.drawTile(fill,canvas,fill::draw);colors(fill.seenShader,0xff123456,0xff123456);
        fill.maskColor=0xff0099ff;appearance.drawTile(fill,canvas,fill::draw);equal(null,fill.seenShader);fill.maskColor=0x80999999;
        ((State)tile.state).state=1;appearance.drawTile(fill,canvas,fill::draw);equal(null,fill.seenShader);((State)tile.state).state=2;
        GradientTileDrawable gradient=new GradientTileDrawable();gradient.setBounds(0,0,100,100);tile.bg=gradient;appearance.refreshTile(tile);gradient.animator.running=true;gradient.colorDrawable.color=0x60808080;appearance.drawTile(gradient,canvas,gradient::draw);colors(gradient.seenShader,0xff123456,0xff123456);equal(96,gradient.paint.getAlpha());equal(null,gradient.paint.getShader());gradient.animator.running=false;appearance.drawTile(gradient,canvas,gradient::draw);equal(null,gradient.seenShader);
        AutoBlurDrawable blur=new AutoBlurDrawable();blur.setBounds(0,0,100,100);fill.child=blur;fill.deforming=false;fill.animator.running=true;tile.bg=fill;appearance.refreshTile(tile);BlendDrawable engine=blur.viewBlurProxy.actual.blurDrawable;engine.setBounds(0,0,100,100);RuntimeShader original=engine.drawableShader.shader;
        appearance.drawTile(fill,canvas,target->appearance.drawBlur(blur,target,blur::draw));
        // Native ArgbEvaluator halfway frames for light/dark and stroke/no-stroke pairs.
        for(int[] pair:new int[][]{{0x46acacac,0x47a7a7a7},{0x7aacacac,0x79a7a7a7},{0x5facacac,0x53a9a9a9},{0x6cacacac,0x79a9a9a9},{0x19404040,0x4d737373}}) {
            engine.drawableShader.multiBlendParam.get(2).color=pair[0];engine.drawableShader.multiBlendParam.get(3).color=pair[1];appearance.drawGlassContent(engine,canvas,engine::onDrawContent);equal(true,engine.recorded!=original);colors(engine.recorded.inputs.get("c17QsFill"),0xff123456,0xff123456);equal(pair[0],engine.drawableShader.multiBlendParam.get(2).color);equal(pair[1],engine.drawableShader.multiBlendParam.get(3).color);equal(original,engine.drawableShader.shader);
        }
        fill.animator.running=false;appearance.drawGlassContent(engine,canvas,engine::onDrawContent);equal(original,engine.recorded);
        fill.deforming=true;appearance.drawGlassContent(engine,canvas,engine::onDrawContent);equal(true,engine.recorded!=original);
        ((State)tile.state).state=1;appearance.drawGlassContent(engine,canvas,engine::onDrawContent);equal(original,engine.recorded);((State)tile.state).state=2;
        engine.drawableShader.multiBlendParam.get(2).color=0xff123456;appearance.drawGlassContent(engine,canvas,engine::onDrawContent);equal(original,engine.recorded);
        engine.drawableShader.multiBlendParam.get(2).color=0x46acacac;engine.drawableShader.multiBlendParam.get(3).color=0x47a7a7a7;engine.drawableShader.multiBlendParam.get(3).mode=2;appearance.drawGlassContent(engine,canvas,engine::onDrawContent);equal(original,engine.recorded);
        appearance.detach(tile);
    }
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
        deviceCards(a,c,texture);
        pressFrames(a,c);
        equal(0xff112233,QsTileAppearance.color("#112233",0));equal(0x80112233,QsTileAppearance.color("#80112233",0));equal(0,QsTileAppearance.color("#00000000",1));equal(123,QsTileAppearance.color("invalid",123));near(42f,QsTileAppearance.number(Float.NaN,42f));near(42f,QsTileAppearance.number("Infinity",42f));near(1.23f,QsTileAppearance.number("1.23",42f));near(359f,QsTileAppearance.angle(-1f));near(0f,QsTileAppearance.angle(720f));
        for(int color:new int[]{0xffffffff,0x80123456,0x00123456,0xff000000})for(float opacity:new float[]{0,25,50,100,200,-10})for(int alpha:new int[]{0,128,255}){int value=QsTileAppearance.withOpacity(color,opacity,alpha);equal(color&0xffffff,value&0xffffff);equal(true,(value>>>24)>=0&&(value>>>24)<=255);}
        for(float angle:new float[]{0,45,90,135,180,225,270,315}){LinearGradient g=(LinearGradient)QsTileAppearance.shader(new QsTileAppearance.Style(0xff112233,0xff445566,50f,angle,true),new Rect(0,0,100,200));equal(0x80112233,g.colors[0]);equal(0x80445566,g.colors[1]);near(50f,(g.left+g.right)/2f);near(100f,(g.top+g.bottom)/2f);}
        equal(0,c.layers);equal(0,c.rects);equal(0,c.nodeDraws);System.out.println("QsTileAppearanceCheck passed: "+checks);
    }
}
