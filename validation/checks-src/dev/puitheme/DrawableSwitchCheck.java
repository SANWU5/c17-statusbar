package dev.puitheme;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/** Exercises the actual resource wrapper when switching between native and module glyphs. */
public final class DrawableSwitchCheck {
    private static int count;
    private static void require(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private static final class Glyph extends Drawable {
        final int width;int draws,alpha,tint;ColorStateList list;PorterDuff.Mode mode;
        Glyph(int width){this.width=width;}
        public void draw(Canvas canvas){draws++;}
        public void setAlpha(int alpha){this.alpha=alpha;}
        public void setColorFilter(ColorFilter filter){}
        public int getOpacity(){return -3;}
        public int getIntrinsicWidth(){return width;}
        public void setTint(int color){tint=color;}
        public void setTintList(ColorStateList value){list=value;}
        public void setTintMode(PorterDuff.Mode value){mode=value;}
    }
    public static void main(String[] args)throws Exception {
        Class<?> module=Class.forName("dev.puitheme.StatusBarModule");Field features=module.getDeclaredField("FEATURES");features.setAccessible(true);
        Constructor<?> constructor=Class.forName("dev.puitheme.StatusBarModule$ScaledDrawable")
                .getDeclaredConstructor(Drawable.class,Resources.class,boolean.class,Drawable.class);constructor.setAccessible(true);
        for(String group:new String[]{"wifi","data"}) {
            Glyph styled=new Glyph(72),nativeGlyph=new Glyph(54);
            Drawable wrapper=(Drawable)constructor.newInstance(styled,Resources.getSystem(),group.equals("wifi"),nativeGlyph);
            Map<String,Object> values=new HashMap<>();values.put(group+"_enabled",true);values.put(group+"_color_enabled",false);
            features.set(null,FeatureOptions.from(values));wrapper.setBounds(0,0,72,56);wrapper.draw(new Canvas());
            require(styled.draws==1&&nativeGlyph.draws==0,"module glyph absent "+group);
            require(wrapper.getIntrinsicWidth()==72,"module dimensions "+group);
            wrapper.setAlpha(144);wrapper.setTintMode(PorterDuff.Mode.SRC_OVER);
            ColorStateList tint=ColorStateList.valueOf(0xaa334455);wrapper.setTintList(tint);
            values.put(group+"_icon_enabled",false);features.set(null,FeatureOptions.from(values));wrapper.draw(new Canvas());
            require(nativeGlyph.draws==1,"style off cannot restore native glyph "+group);
            require(wrapper.getIntrinsicWidth()==54,"native width not restored "+group);
            require(nativeGlyph.alpha==144,"native alpha lost "+group);
            require(nativeGlyph.mode==PorterDuff.Mode.SRC_OVER,"native tint mode lost "+group);
            require(nativeGlyph.list==tint,"native tint list lost "+group);
            values.put(group+"_icon_enabled",true);features.set(null,FeatureOptions.from(values));wrapper.draw(new Canvas());
            require(styled.draws==2,"style enable did not restore saved glyph "+group);
            wrapper.setTint(0xaa112233);wrapper.setTintList(null);
            values.put(group+"_enabled",false);features.set(null,FeatureOptions.from(values));wrapper.draw(new Canvas());
            require(nativeGlyph.draws==2,"master off does not restore native "+group);
            require(nativeGlyph.list==null,"cleared tint list reapplied "+group);
            require(nativeGlyph.tint==0,"obsolete setTint color resurrected "+group);
        }
        features.set(null,FeatureOptions.from(new HashMap<>()));
        System.out.println("DrawableSwitchCheck passed: "+count);
    }
}
