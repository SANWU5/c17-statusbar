package dev.puitheme;

import android.os.Bundle;
import android.os.Handler;
import android.view.ViewGroup;
import com.android.systemui.plugins.custom.CustomRowEntry;
import com.android.systemui.statusbar.StatusBarStateControllerImpl;
import com.android.systemui.statusbar.notification.row.NotificationBackgroundView;
import com.oplus.systemui.plugins.shared.template.component.light.f;
import com.oplus.systemui.plugins.shared.template.component.media.view.MediaBackGroundView;
import com.oplus.systemui.plugins.shared.template.section.media.K0;
import com.oplus.systemui.plugins.shared.template.section.media.s1;
import com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow;

/** The colour order of one fluid-cloud card in every scene: media accent, follow-media, custom base, then the material only. */
public final class FluidCloudPriorityCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final int MEDIA=0xff123456, CUSTOM=0xffc04020;

    private static final class Scene {
        final FluidCloudStyle style=new FluidCloudStyle();
        final FluidCloudAccent accent=new FluidCloudAccent();
        final Integer[] colour={null};final boolean[] follow={false},keep={false},usesAccent={false};
        final Integer[] layer={null};final int[] layerCalls={0};
        final Integer[] overlayColour={null};final java.util.function.BooleanSupplier[] overlayGate={null};final int[] overlayCalls={0};
        final Bundle config=new Bundle();
        final NotificationBackgroundView body=new NotificationBackgroundView();
        final CustomRowEntry entry=new CustomRowEntry();
        final OplusCustomRow row=new OplusCustomRow();
        final ViewGroup card=new ViewGroup(new android.content.Context());
        final kotlinx.coroutines.flow.p flow=new kotlinx.coroutines.flow.p(new com.oplus.systemui.plugins.shared.data.media.model.a(MEDIA));
        final K0 model=new K0(flow);
        final s1 section;
        Scene() throws Exception {
            ClassLoader loader=FluidCloudPriorityCheck.class.getClassLoader();
            style.setAccentController(accent);accent.resolve(loader);style.resolveMedia(loader);style.resolveNotification(loader);
            MediaBackGroundView host=new MediaBackGroundView();host.parent=card;host.F=new f();host.setBackground(host.F);
            com.oplus.systemui.plugins.shared.template.page.entity.t page=new com.oplus.systemui.plugins.shared.template.page.entity.t();
            page.u=new com.oplus.systemui.plugins.shared.template.page.view.PageBackgroundView();page.u.b.setBounds(0,0,100,80);
            section=new s1(new android.content.Context(),model,card,page);section.q=host;
            ((kotlinx.coroutines.flow.p)model.U.b).value=Boolean.TRUE;
            style.setNotificationStyler(new FluidCloudStyle.NotificationStyler() {
                @Override public void update(android.view.View target,boolean following,boolean keepHighlight) {
                    if(target!=body)return;
                    colour[0]=style.normalNotificationColor(target);follow[0]=following;keep[0]=keepHighlight;usesAccent[0]=style.notificationUsesAccent(target);
                }
                @Override public void gradient(android.view.View target,Integer argb) {if(target==body){layer[0]=argb;layerCalls[0]++;}}
                @Override public void overlay(android.view.View target,Integer argb,java.util.function.BooleanSupplier needed) {
                    if(target==body){overlayColour[0]=argb;overlayGate[0]=needed;overlayCalls[0]++;}
                }
            });
            row.setMBackgroundNormal(body);row.setEntry(entry);entry.immersiveCardView=card;
            style.mediaChanged(section);style.notificationRow(row);apply();
        }
        void apply(){accent.configure(config);style.configure(config);style.refreshAfterAccent();style.notificationRow(row);Handler.drainForCheck();style.finishFillFadesForCheck();}
        void lock(boolean showing,boolean dozing) {
            style.setKeyguard(showing);
            StatusBarStateControllerImpl state=new StatusBarStateControllerImpl();state.state=showing?1:0;state.dozing=dozing;style.onNativeState(state);
            style.notificationRow(row);Handler.drainForCheck();style.finishFillFadesForCheck();
        }
    }

    private static Object field(Object owner,Class<?> type,String name) throws Exception {
        java.lang.reflect.Field value=type.getDeclaredField(name);value.setAccessible(true);return value.get(owner);
    }
    /** What the scene material backend was handed for this body: the baked fill and the lock-screen gradient colour. */
    private static Object target(Scene scene) throws Exception {
        Object material=field(scene.style,FluidCloudStyle.class,"sceneMaterial");
        return ((java.util.Map<?,?>)field(material,FluidCloudSceneMaterial.class,"targets")).get(scene.body);
    }
    private static Integer fill(Scene scene) throws Exception {
        Object target=target(scene);return (Integer)field(target,target.getClass(),"fill");
    }
    private static Integer gradient(Scene scene) throws Exception {
        Object target=target(scene);return (Integer)field(target,target.getClass(),"accent");
    }

    private static int brightness(int argb) { return ((argb>>16)&0xff)+((argb>>8)&0xff)+(argb&0xff); }

    /** The colour laid over white instead of over the dark wallpaper: one flat colour that stands for a white layer under the tint. */
    private static void lighten() {
        // No white base, no tint, or nothing to fold in: unchanged.
        check(FluidCloudStyle.lighten(0xa5805040,0f)==0xa5805040,"no white base: the colour is unchanged");
        check(FluidCloudStyle.lighten(0x00805040,.7f)==0x00805040,"no tint: nothing to lighten");
        check(FluidCloudStyle.lighten(0xa5805040,-3f)==0xa5805040,"a negative base counts as none");
        check(FluidCloudStyle.lighten(0xff805040,.7f)==0xff805040,"an opaque colour is left alone");
        // Fully on white: the hue of tint over white, at the tint's own alpha.
        int onWhite=FluidCloudStyle.lighten(0x80ff0000,1f);
        check((onWhite>>>24)==0x80&&((onWhite>>16)&0xff)==255&&Math.abs(((onWhite>>8)&0xff)-127)<=1&&Math.abs((onWhite&0xff)-127)<=1,"half red on white is a pink hue, still half transparent");
        check(FluidCloudStyle.lighten(0x80ff0000,5f)==onWhite,"a base above 100% counts as 100%");
        // In between: the colour is the weighted mix of a tint a over a white layer l, divided by a + (1 - a) * l.
        int third=FluidCloudStyle.lighten(0xa5805040,.7f);
        float a=0xa5/255f,total=a+(1f-a)*.7f;
        check((third>>>24)==0xa5,"the alpha is the user's opacity, unchanged");
        float expectRed=(a*0x80+(1f-a)*.7f*255f)/total;
        check(Math.abs(((third>>16)&0xff)-expectRed)<=1,"red is the weighted mix of the tint and white");
        // Lighter than the tint alone, and never darker in any channel (white is the lightest thing to mix in).
        for(int i=0;i<3;i++) {
            int shift=16-8*i;
            check(((third>>shift)&0xff)>=((0xa5805040>>shift)&0xff),"channel "+i+" is never darker than the tint");
        }
        // code100: the lock-screen opacity means what it says. 100 % is opaque, 0 % fully transparent, and every step in between is
        // exactly as see-through as set, whatever the white base (folding the white into the alpha made 30 % look whiter and more solid
        // than 60 %).
        for(int alpha=0;alpha<=255;alpha+=15)
            check((FluidCloudStyle.lighten(alpha<<24|0x805040,.7f)>>>24)==alpha,"opacity "+alpha+" is kept");
        // Monotonic: more white base is never darker, and never changes how see-through the colour is.
        int before=0;
        for(int percent=0;percent<=100;percent+=10) {
            int now=FluidCloudStyle.lighten(0xa5805040,percent/100f);
            check((now>>>24)==0xa5&&(percent==0||brightness(now)>=brightness(before)),"monotonic at "+percent);
            before=now;
        }
    }

    public static void main(String[] args) throws Exception {
        // The lock-screen gradient is opt-in: by default every card on the lock screen has the same flat glass material.
        check(Boolean.FALSE.equals(StatusBarSettings.BOOLEAN_DEFAULTS.get(FluidCloudAccent.LOCK_GRADIENT)),"lock gradient is off by default");
        check(!StatusBarSettings.bool(new java.util.HashMap<String,Object>(),FluidCloudAccent.LOCK_GRADIENT),"an unstored lock gradient is off");
        scenes();
        lighten();
        colourStyle();
        fillFade();
        lockOwnLook();
        disableKeepsCustomLook();
        System.out.println("FluidCloudPriorityCheck: "+checks+" checks passed");
    }

    /** Lock/AOD look settings retain neutral glass, but only the media-accent master can supply media colour. */
    private static void lockOwnLook() throws Exception {
        for(String name:new String[]{"lock","aod"}) {
            boolean aod=name.equals("aod");
            String own=aod?FluidCloudAccent.AOD_GRADIENT:FluidCloudAccent.LOCK_GRADIENT,other=aod?FluidCloudAccent.LOCK_GRADIENT:FluidCloudAccent.AOD_GRADIENT;
            Scene s=new Scene();
            s.lock(true,aod);
            s.apply();
            check(!s.usesAccent[0]&&s.colour[0]==null&&fill(s)==null,name+": masters off and nothing asked: the card is native");
            s.config.putBoolean(other,true);s.apply();
            check(!s.usesAccent[0]&&s.colour[0]==null,name+": the other scene's look asks for nothing here");
            s.config.putBoolean(own,true);s.apply();
            check(!s.usesAccent[0]&&s.colour[0]==null&&gradient(s)==null&&s.layer[0]==null,name+": its gradient cannot bypass the media-accent master");
            check(!s.accent.enabled()&&s.accent.lockOwn(aod),name+": media colour is off while the independently selected neutral look stays requested");
            s.config.putBoolean(own,false);s.config.putBoolean(aod?FluidCloudAccent.AOD_CUSTOM:FluidCloudAccent.LOCK_CUSTOM,true);
            s.config.putFloat(aod?FluidCloudAccent.AOD_MASK_OPACITY:FluidCloudAccent.LOCK_MASK_OPACITY,0f);s.apply();
            check(!s.usesAccent[0]&&s.colour[0]==null&&fill(s)==null,name+": custom opacity and mask cannot reactivate media colour either");
            s.config.putBoolean(FluidCloudAccent.ENABLED,true);s.apply();
            check(s.usesAccent[0]&&s.colour[0]!=null&&s.colour[0]==MEDIA,name+": enabling media accent makes the existing look use the current media colour");
            s.config.putBoolean(FluidCloudAccent.ENABLED,false);s.apply();
            check(!s.usesAccent[0]&&s.colour[0]==null&&fill(s)==null&&gradient(s)==null,name+": switching it off while active removes the media colour again");
            s.config.putBoolean(aod?FluidCloudAccent.AOD_CUSTOM:FluidCloudAccent.LOCK_CUSTOM,false);s.apply();
            check(!s.usesAccent[0]&&s.colour[0]==null,name+": nothing asked again: native");
            s.style.release();s.accent.release();Handler.drainForCheck();
        }
        // The other scenes still wait for the master: the shade and the floating card are untouched by the lock page.
        Scene shade=new Scene();
        shade.config.putBoolean(FluidCloudAccent.LOCK_GRADIENT,true);shade.config.putBoolean(FluidCloudAccent.AOD_GRADIENT,true);
        shade.config.putBoolean(FluidCloudAccent.LOCK_CUSTOM,true);shade.apply();
        check(!shade.usesAccent[0]&&shade.colour[0]==null,"the lock page's looks do not colour the shade's card");
        shade.style.release();shade.accent.release();Handler.drainForCheck();
    }

    /** Disabling media colour falls through to a user's existing custom colour, with its scene's gradient and opacity. */
    private static void disableKeepsCustomLook() throws Exception {
        for(boolean aod:new boolean[]{false,true}) for(boolean music:new boolean[]{false,true}) {
            Scene s=new Scene();s.lock(true,aod);
            if(!music)s.entry.immersiveCardView=new ViewGroup(new android.content.Context());
            s.config.putBoolean(FluidCloudAccent.ENABLED,true);s.config.putBoolean(FluidCloudStyle.MASTER,true);
            s.config.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,true);s.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,true);
            s.config.putString(FluidCloudStyle.BACKGROUND_MODE,"custom");s.config.putInt(FluidCloudStyle.BACKGROUND_COLOR,CUSTOM);
            s.config.putFloat(FluidCloudStyle.BACKGROUND_OPACITY,37f);s.config.putBoolean(FluidCloudStyle.LOCK_CAPSULE,true);
            s.config.putBoolean(FluidCloudAccent.LOCK_GRADIENT,true);s.config.putBoolean(FluidCloudAccent.AOD_GRADIENT,true);
            s.config.putBoolean(FluidCloudAccent.LOCK_CUSTOM,true);s.config.putBoolean(FluidCloudAccent.AOD_CUSTOM,true);
            s.config.putFloat(FluidCloudAccent.LOCK_OPACITY,25f);s.config.putFloat(FluidCloudAccent.AOD_OPACITY,25f);
            s.config.putFloat(FluidCloudAccent.LOCK_MASK_OPACITY,0f);s.config.putFloat(FluidCloudAccent.AOD_MASK_OPACITY,0f);
            s.config.putFloat(FluidCloudAccent.LOCK_LIGHT,0f);s.config.putFloat(FluidCloudAccent.AOD_LIGHT,0f);s.apply();
            check(s.colour[0]!=null&&s.colour[0]==MEDIA,"before disable, the live media source takes precedence: AOD="+aod+", music="+music);
            s.config.putBoolean(FluidCloudAccent.ENABLED,false);s.apply();
            check(!s.usesAccent[0]&&s.colour[0]!=null&&s.colour[0]==CUSTOM,"disabling media accent restores the user's custom colour: AOD="+aod+", music="+music);
            check(fill(s)==null&&s.layer[0]!=null&&(s.layer[0]&0xffffff)==(CUSTOM&0xffffff)&&(s.layer[0]>>>24)==64,
                    "the existing custom gradient and lock/AOD opacity remain effective after disable");
            LockCapsuleTint.Plan plan=s.style.lockCapsulePlan(music);
            check(plan!=null&&"custom".equals(plan.source)&&(plan.argb&0xffffff)==(CUSTOM&0xffffff),"capsule falls through to the existing custom source, never cached media");
            s.config.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,false);s.apply();
            check(s.colour[0]==null&&s.layer[0]==null&&s.overlayColour[0]==null&&s.style.lockCapsulePlan(music)==null,
                    "without a custom source, neutral glass has no media gradient, flat tint or capsule tint");
            s.style.release();s.accent.release();Handler.drainForCheck();
        }
    }

    /** Coloured non-media fluid cards use the same selected lock/AOD fade as music; ordinary notification policy is separate. */
    private static void colourStyle() throws Exception {
        check(FluidCloudStyle.SOLID.equals(StatusBarSettings.STRING_DEFAULTS.get(FluidCloudStyle.COLOR_STYLE)),"solid is the default style");
        // code100: the shade keeps its own colours (its scope is retired; scenes() checks that), so the styles are about the lock screen.
        for(String name:new String[]{"lock","aod"}) {
            boolean aod=name.equals("aod");
            Scene s=new Scene();
            s.lock(true,aod);
            s.config.putBoolean(FluidCloudAccent.ENABLED,true);
            s.config.putBoolean(FluidCloudStyle.MASTER,true);s.config.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,true);
            s.config.putString(FluidCloudStyle.BACKGROUND_MODE,"custom");s.config.putInt(FluidCloudStyle.BACKGROUND_COLOR,CUSTOM);
            s.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,true);
            // A card that does not hold the music itself follows the media colour.
            s.entry.immersiveCardView=new ViewGroup(new android.content.Context());s.style.notificationRow(s.row);s.apply();
            check(s.layer[0]==null&&fill(s)!=null&&(fill(s)&0xffffff)==(MEDIA&0xffffff),name+": solid style: a flat fill, no layer");
            Integer solidFill=fill(s);
            s.config.putString(FluidCloudStyle.COLOR_STYLE,FluidCloudStyle.GRADIENT);s.apply();
            check(s.layer[0]!=null&&s.overlayColour[0]==null&&fill(s)==null&&gradient(s)==null,
                    name+": a non-media card uses one spatial colour layer, with no flat cover or media MetaBall tint");
            check(s.layer[0]==FluidCloudStyle.lighten(solidFill,FluidCloudAccent.DEFAULT_LOCK_LIGHT/100f)&&s.follow[0],
                    name+": the fade has exactly the same lightened media colour and opacity as the solid style");
            s.config.putString(FluidCloudStyle.COLOR_STYLE,FluidCloudStyle.SOLID);s.apply();
            // The colour layer is told the same colour, laid over white (lighten) as on the unlocked glass.
            {
                int expected=FluidCloudStyle.lighten(solidFill,FluidCloudAccent.DEFAULT_LOCK_LIGHT/100f);
                check(s.overlayColour[0]!=null&&s.overlayColour[0]==expected&&s.overlayGate[0]!=null,name+": the colour layer holds the fill, lightened by the default white base");
                check((s.overlayColour[0]>>>24)==(solidFill>>>24),name+": lightened is exactly as opaque as the fill alone");
                java.util.function.BooleanSupplier gate=s.overlayGate[0];
                int calls=s.overlayCalls[0];s.apply();
                check(s.overlayCalls[0]==calls||s.overlayGate[0]==gate,name+": an unchanged decision keeps the same gate (nothing allocated per update)");
                check(gate.getAsBoolean()&&gate.getAsBoolean(),name+": the card wants a fill, so the colour layer draws it over the card, with no patience and no state to wait for");
                // The white base is the user's: off it is the plain fill, full it is the fill laid on white.
                s.config.putFloat(FluidCloudAccent.LOCK_LIGHT,0f);s.config.putFloat(FluidCloudAccent.AOD_LIGHT,0f);s.apply();
                check(s.overlayColour[0]!=null&&s.overlayColour[0].equals(solidFill),name+": white base 0: exactly the fill");
                s.config.putFloat(FluidCloudAccent.LOCK_LIGHT,100f);s.config.putFloat(FluidCloudAccent.AOD_LIGHT,100f);s.apply();
                check(s.overlayColour[0]!=null&&(s.overlayColour[0]>>>24)==(solidFill>>>24),name+": white base 100: the colour as it looks on white, as opaque as set");
                s.config.putFloat(FluidCloudAccent.LOCK_LIGHT,FluidCloudAccent.DEFAULT_LOCK_LIGHT);s.config.putFloat(FluidCloudAccent.AOD_LIGHT,FluidCloudAccent.DEFAULT_LOCK_LIGHT);s.apply();
            }
            // The custom base works without a media-follow owner, including its gradient and opacity.
            s.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,false);s.apply();
            check(s.layer[0]==null&&fill(s)!=null&&(fill(s)&0xffffff)==(CUSTOM&0xffffff),name+": custom solid uses the flat fill");
            check(s.overlayColour[0]!=null&&s.overlayColour[0]==FluidCloudStyle.lighten(fill(s),FluidCloudAccent.DEFAULT_LOCK_LIGHT/100f),name+": and it is the overlay too");
            // "Lock custom" is the lock-screen opacity of every colour on a lock/AOD card, the custom base colour's included.
            {
                int ownAlpha=fill(s)>>>24;
                s.config.putBoolean(FluidCloudAccent.LOCK_CUSTOM,true);s.config.putBoolean(FluidCloudAccent.AOD_CUSTOM,true);s.config.putFloat(FluidCloudAccent.LOCK_OPACITY,20f);s.config.putFloat(FluidCloudAccent.AOD_OPACITY,20f);s.apply();
                check(fill(s)!=null&&(fill(s)>>>24)==Math.round(255*.2f),name+": the lock-screen opacity is the custom base colour's strength too");
                s.config.putBoolean(FluidCloudAccent.LOCK_CUSTOM,false);s.config.putBoolean(FluidCloudAccent.AOD_CUSTOM,false);s.apply();
                check(fill(s)!=null&&(fill(s)>>>24)==ownAlpha,name+": and with lock custom off it is the base colour's own opacity again");
            }
            Integer customFill=fill(s);s.config.putString(FluidCloudStyle.COLOR_STYLE,FluidCloudStyle.GRADIENT);s.apply();
            check(fill(s)==null&&gradient(s)==null&&s.overlayColour[0]==null&&s.layer[0]!=null
                    &&s.layer[0]==FluidCloudStyle.lighten(customFill,FluidCloudAccent.DEFAULT_LOCK_LIGHT/100f),
                    name+": a custom-colour non-media card also fades, without an accent owner");
            // The independent lock-gradient option also applies to non-media fluid cards in the solid style.
            s.config.putString(FluidCloudStyle.COLOR_STYLE,FluidCloudStyle.SOLID);s.config.putBoolean(FluidCloudAccent.LOCK_GRADIENT,true);s.config.putBoolean(FluidCloudAccent.AOD_GRADIENT,true);s.apply();
            check(fill(s)==null&&s.overlayColour[0]==null&&s.layer[0]!=null,name+": lock-gradient option reaches a non-media card");
            s.config.putBoolean(FluidCloudAccent.LOCK_GRADIENT,false);s.config.putBoolean(FluidCloudAccent.AOD_GRADIENT,false);s.apply();
            check(customFill.equals(fill(s))&&s.layer[0]==null,name+": disabling the fade restores custom solid");
            // Nothing wanted: no fallback.
            s.config.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,false);s.config.putBoolean(FluidCloudStyle.MASTER,false);s.apply();
            check(s.overlayColour[0]==null&&s.overlayGate[0]==null&&s.layer[0]==null,name+": nothing wanted, no colour layer");
            s.config.putBoolean(FluidCloudStyle.MASTER,true);s.config.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,true);s.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,true);s.apply();
            check(s.overlayColour[0]!=null,name+": back on, the overlay is back");
            // The card that holds the music: its own gradient (no fill, no fallback). code100: the lightened colour comes as a layer that
            // fades out down the card, as the gradient does; the flat overlay of code99 covered the gradient the user chose.
            s.entry.immersiveCardView=s.card;s.style.notificationRow(s.row);s.config.putString(FluidCloudStyle.COLOR_STYLE,FluidCloudStyle.GRADIENT);s.apply();
            check(gradient(s)!=null&&(gradient(s)&0xffffff)==(MEDIA&0xffffff)&&fill(s)==null&&s.overlayColour[0]==null
                    &&s.layer[0]!=null&&(s.layer[0]>>>24)==(gradient(s)>>>24)&&brightness(s.layer[0])>=brightness(MEDIA),
                    name+": the lock-screen music card recolours its own gradient and takes the lightened colour as a fading layer, never a flat cover");
            s.config.putString(FluidCloudStyle.COLOR_STYLE,FluidCloudStyle.SOLID);s.apply();
            check(fill(s)!=null&&gradient(s)==null&&s.layer[0]==null&&s.overlayColour[0]!=null,name+": solid style without the lock gradient option: the music card is a flat fill like the others");
            s.style.release();
            check(s.layer[0]==null&&s.overlayColour[0]==null,name+": release takes everything off");
        }
    }

    private static void scenes() throws Exception {
        for(String name:new String[]{"shade","lock","aod"}) {
            boolean shade=name.equals("shade"),aod=name.equals("aod");
            Scene s=new Scene();
            if(!shade)s.lock(true,aod);
            check(s.colour[0]==null&&!s.follow[0]&&!s.usesAccent[0],name+": nothing enabled leaves the card native");
            s.config.putBoolean(FluidCloudAccent.ENABLED,true);
            s.config.putBoolean(FluidCloudStyle.MASTER,true);s.config.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,true);
            s.config.putString(FluidCloudStyle.BACKGROUND_MODE,"custom");s.config.putInt(FluidCloudStyle.BACKGROUND_COLOR,CUSTOM);
            s.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,true);
            s.config.putBoolean(FluidCloudStyle.SCOPE_LOCK,false);
            if(shade)s.config.putBoolean(FluidCloudStyle.SCOPE_SHADE,false);
            s.apply();
            if(shade) {
                // With its scope switched off the notification shade keeps its own colours whatever else is switched on.
                check(!s.usesAccent[0]&&s.colour[0]==null&&fill(s)==null&&gradient(s)==null,"shade: no accent, follow or custom colour ever reaches the shade");
                s.config.putBoolean(FluidCloudStyle.SCOPE_LOCK,true);s.config.putBoolean(FluidCloudStyle.SCOPE_FLOAT,true);s.apply();
                check(!s.usesAccent[0]&&s.colour[0]==null&&fill(s)==null,"shade: the other scenes' switches change nothing here");
                s.config.putBoolean(FluidCloudStyle.FOLLOW_NOTIFICATION,true);s.apply();
                check(s.follow[0]&&s.colour[0]==null,"shade: the follow-notification material still owns the card, without a colour");
                // code100: the shade scope is retired. A value saved as on (code99 made it the default) changes nothing.
                s.config.putBoolean(FluidCloudStyle.SCOPE_SHADE,true);s.apply();
                check(!s.usesAccent[0]&&s.colour[0]==null&&fill(s)==null&&gradient(s)==null,"shade scope saved on: the shade still keeps its own colours");
                Scene unset=new Scene();
                unset.config.putBoolean(FluidCloudAccent.ENABLED,true);unset.config.putBoolean(FluidCloudStyle.MASTER,true);
                unset.apply();
                check(!unset.usesAccent[0]&&unset.colour[0]==null,"shade scope unset: off");
                continue;
            }
            // 1. The accent outranks everything and needs no scene switch.
            check(s.usesAccent[0]&&s.colour[0]!=null&&s.colour[0]==MEDIA,name+": the accent applies although the appearance scope is off");
            check(s.follow[0],name+": an accent card is owned by the policy, in AOD too (a direct lock has no earlier composition to hold)");
            // Music card and every other card share one material: the glass recipe plus a fill, never a private gradient.
            check(gradient(s)==null&&fill(s)!=null&&(fill(s)&0xffffff)==(MEDIA&0xffffff),name+": the accent is a fill on the same glass material as the other cards");
            // 2. Accent off: the appearance scope now decides. Off means native.
            s.config.putBoolean(FluidCloudAccent.ENABLED,false);s.apply();
            check(!s.usesAccent[0]&&s.colour[0]==null,name+": appearance scope OFF keeps the card native");
            // 3. Following is part of the media-accent page: it needs that master, and it colours a card that does not hold
            // the music itself. Priority 2 (follow-media) beats priority 3 (custom base colour).
            s.config.putBoolean(FluidCloudStyle.SCOPE_LOCK,true);s.apply();
            check(s.colour[0]!=null&&s.colour[0]==CUSTOM,name+": with the page master off following is inert and the custom base shows");
            s.config.putBoolean(FluidCloudAccent.ENABLED,true);
            ViewGroup other=new ViewGroup(new android.content.Context());
            s.entry.immersiveCardView=other;s.style.notificationRow(s.row);s.apply();
            check(!s.usesAccent[0]&&s.colour[0]!=null&&s.colour[0]==MEDIA,name+": follow-media outranks the custom base colour");
            check(s.follow[0],name+": a coloured card takes the optics policy");
            // Not playing: the card does not follow, and the custom base colour takes over.
            // code98: a track change reports "not playing" for a moment; that must not let go of the colour and bring it back.
            s.style.setMediaPlaying(false);Handler.drainForCheck();s.apply();
            check(s.colour[0]!=null&&s.colour[0]==MEDIA,name+": a brief 'not playing' (track change) keeps following");
            s.style.setMediaPlaying(true);Handler.drainForCheck();s.apply();
            check(s.colour[0]!=null&&s.colour[0]==MEDIA,name+": playing again after the blink never changed the colour");
            s.style.setMediaPlaying(false);s.style.expireStopGraceForCheck();Handler.drainForCheck();s.apply();
            check(s.colour[0]!=null&&s.colour[0]==CUSTOM,name+": nothing playing is not followed");
            s.config.putBoolean(FluidCloudStyle.FOLLOW_ONLY_PLAYING,false);s.apply();
            check(s.colour[0]!=null&&s.colour[0]==MEDIA,name+": with the option off a paused track is still followed");
            s.config.putBoolean(FluidCloudStyle.FOLLOW_ONLY_PLAYING,true);s.style.setMediaPlaying(true);s.apply();
            check(s.colour[0]!=null&&s.colour[0]==MEDIA,name+": playing again follows again");
            // 4. Without a media colour the custom base colour applies.
            s.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,false);s.apply();
            check(s.colour[0]!=null&&s.colour[0]==CUSTOM,name+": custom base colour when nothing outranks it");
            // 5. The custom base uses the style page's opacity; the media colour uses the media-accent page's own.
            s.config.putFloat(FluidCloudStyle.BACKGROUND_OPACITY,50f);s.apply();
            check(fill(s)!=null&&(fill(s)>>>24)==128&&(fill(s)&0xffffff)==(CUSTOM&0xffffff),name+": fill opacity bakes into the custom colour");
            s.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,true);s.apply();
            check(fill(s)!=null&&(fill(s)>>>24)==255&&(fill(s)&0xffffff)==(MEDIA&0xffffff),name+": the media colour ignores the style page's opacity");
            s.config.putFloat(FluidCloudAccent.OPACITY,50f);s.apply();
            check(fill(s)!=null&&(fill(s)>>>24)==128&&(fill(s)&0xffffff)==(MEDIA&0xffffff),name+": it uses the media-accent page's opacity");
            s.entry.immersiveCardView=s.card;s.style.notificationRow(s.row);s.apply();
            check(fill(s)!=null&&(fill(s)>>>24)==128&&(fill(s)&0xffffff)==(MEDIA&0xffffff),name+": and so does the accent, so music and other cards look alike");
            // Lock screen and AOD can take their own opacity and mask; the floating card and shared settings are untouched.
            s.config.putBoolean(FluidCloudAccent.LOCK_CUSTOM,true);s.config.putBoolean(FluidCloudAccent.AOD_CUSTOM,true);s.config.putFloat(FluidCloudAccent.LOCK_OPACITY,25f);s.config.putFloat(FluidCloudAccent.AOD_OPACITY,25f);
            s.config.putBoolean(FluidCloudAccent.MASK_ENABLED,false);s.config.putFloat(FluidCloudAccent.LOCK_MASK_OPACITY,0f);s.config.putFloat(FluidCloudAccent.AOD_MASK_OPACITY,0f);s.apply();
            check(fill(s)!=null&&(fill(s)>>>24)==64&&(fill(s)&0xffffff)==(MEDIA&0xffffff),name+": lock-screen custom opacity replaces the shared one");
            s.config.putFloat(FluidCloudAccent.LOCK_MASK_OPACITY,50f);s.config.putFloat(FluidCloudAccent.AOD_MASK_OPACITY,50f);s.config.putInt(FluidCloudAccent.LOCK_MASK_COLOR,0xff000000);s.config.putInt(FluidCloudAccent.AOD_MASK_COLOR,0xff000000);s.apply();
            check(fill(s)!=null&&(fill(s)&0xffffff)==(FluidCloudAccent.mask(MEDIA,0xff000000,50f)&0xffffff),name+": lock-screen custom mask darkens the colour");
            s.config.putBoolean(FluidCloudAccent.LOCK_CUSTOM,false);s.config.putBoolean(FluidCloudAccent.AOD_CUSTOM,false);s.apply();
            check(fill(s)!=null&&(fill(s)>>>24)==128&&(fill(s)&0xffffff)==(MEDIA&0xffffff),name+": switching it off returns to the shared settings");
            // The gradient style: main colour to transparent, instead of one fill, only on the lock screen and AOD.
            s.config.putBoolean(FluidCloudAccent.LOCK_GRADIENT,true);s.config.putBoolean(FluidCloudAccent.AOD_GRADIENT,true);s.apply();
            check(gradient(s)!=null&&(gradient(s)&0xffffff)==(MEDIA&0xffffff)&&fill(s)==null,name+": the gradient style hands the accent to the gradient path");
            s.config.putBoolean(FluidCloudAccent.LOCK_GRADIENT,false);s.config.putBoolean(FluidCloudAccent.AOD_GRADIENT,false);s.apply();
            check(gradient(s)==null&&fill(s)!=null,name+": gradient off fills the card");
            s.config.putFloat(FluidCloudAccent.OPACITY,100f);
            s.config.putBoolean(FluidCloudAccent.ENABLED,false);
            s.config.putFloat(FluidCloudStyle.BACKGROUND_OPACITY,100f);
            // 6. The lock scope decides here (AOD follows the lock screen); the floating scope does not.
            s.config.putBoolean(FluidCloudStyle.SCOPE_LOCK,false);s.config.putBoolean(FluidCloudStyle.SCOPE_FLOAT,true);s.apply();
            check(s.colour[0]==null,name+": the floating switch does not recolour the lock screen");
            s.config.putBoolean(FluidCloudStyle.SCOPE_LOCK,true);s.config.putBoolean(FluidCloudStyle.SCOPE_FLOAT,false);s.apply();
            check(s.colour[0]!=null,name+": the lock switch turns the colour on");
            // 7. The notification-style follow alone owns the card without any colour.
            s.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,false);s.config.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,false);
            s.config.putBoolean(FluidCloudStyle.FOLLOW_NOTIFICATION,true);s.apply();
            check(s.colour[0]==null&&s.follow[0],name+": the follow-notification material alone owns the card with no colour");
            s.config.putBoolean(FluidCloudStyle.FOLLOW_NOTIFICATION,false);s.apply();
            // AOD keeps what the card has until the screen wakes; every other scene lets go at once.
            check(aod?s.follow[0]:!s.follow[0],name+": nothing owns the card again (AOD holds it until wake)");
            if(aod){s.lock(true,false);check(!s.follow[0],name+": waking releases it");}
        }
        // Entering and leaving AOD never flips the owned state or the colour.
        Scene s=new Scene();
        s.config.putBoolean(FluidCloudStyle.MASTER,true);s.config.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,true);
        s.config.putString(FluidCloudStyle.BACKGROUND_MODE,"custom");s.config.putInt(FluidCloudStyle.BACKGROUND_COLOR,CUSTOM);
        s.config.putBoolean(FluidCloudStyle.FOLLOW_NOTIFICATION,true);s.config.putBoolean(FluidCloudAccent.ENABLED,true);
        s.config.putBoolean(FluidCloudStyle.SCOPE_SHADE,false);
        s.apply();s.lock(true,false);
        Integer awake=s.colour[0];boolean awakeFollow=s.follow[0];
        check(awake!=null&&awakeFollow,"lock: the awake composition is owned and coloured");
        s.lock(true,true);
        check(s.colour[0]!=null&&s.colour[0].equals(awake)&&s.follow[0]==awakeFollow,"AOD holds the awake colour and ownership");
        s.lock(true,false);
        check(s.colour[0]!=null&&s.colour[0].equals(awake)&&s.follow[0]==awakeFollow,"leaving AOD returns to the same colour and ownership");
        // Unlocking moves to the shade: the accent and every colour leave the card.
        s.lock(false,false);
        check(!s.usesAccent[0]&&s.colour[0]==null,"unlock gives the shade card its own colour back");
        // An old saved "shade scope on" does not keep the colour after unlocking either (code100: retired).
        s.config.putBoolean(FluidCloudStyle.SCOPE_SHADE,true);s.apply();
        check(!s.usesAccent[0]&&s.colour[0]==null,"unlock with an old saved shade scope: the card still gets its own colour back");
        s.config.putBoolean(FluidCloudStyle.SCOPE_SHADE,false);s.apply();
        // A card that does not hold the media section is never coloured by the accent.
        s.lock(true,false);
        s.entry.immersiveCardView=new ViewGroup(new android.content.Context());s.style.notificationRow(s.row);Handler.drainForCheck();
        check(!s.usesAccent[0],"a card without the media section never takes the accent");
        s.style.release();s.accent.release();Handler.drainForCheck();
    }

    /** code100: the card's fill cross-fades when one colour replaces another (following stops and the custom base colour comes back). */
    private static void fillFade() throws Exception {
        Scene s=new Scene();s.lock(true,false);
        s.config.putBoolean(FluidCloudAccent.ENABLED,true);s.config.putBoolean(FluidCloudStyle.MASTER,true);s.config.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,true);
        s.config.putString(FluidCloudStyle.BACKGROUND_MODE,"custom");s.config.putInt(FluidCloudStyle.BACKGROUND_COLOR,CUSTOM);
        s.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,true);s.config.putBoolean(FluidCloudStyle.FOLLOW_ONLY_PLAYING,false);
        s.entry.immersiveCardView=new ViewGroup(new android.content.Context());s.style.notificationRow(s.row);s.apply();
        Integer following=fill(s);
        check(following!=null&&(following&0xffffff)==(MEDIA&0xffffff),"a card that follows the media colour");
        s.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,false);
        long fadeStarted=android.os.SystemClock.uptime;
        s.accent.configure(s.config);s.style.configure(s.config);s.style.refreshAfterAccent();s.style.notificationRow(s.row);Handler.drainForCheck();
        check(following.equals(fill(s)),"following stops: the first frame is still the colour on the screen");
        java.lang.reflect.Field field=FluidCloudStyle.class.getDeclaredField("main");field.setAccessible(true);
        Handler main=(Handler)field.get(s.style);
        android.os.SystemClock.uptime+=FluidCloudStyle.FILL_FADE_MS/2;
        Runnable step=main.delayed;check(step!=null,"the next frame of the fade is asked for");step.run();
        Integer middle=fill(s);
        check(middle!=null&&!middle.equals(following)&&(middle&0xffffff)!=(CUSTOM&0xffffff),"half way it is a blend of the two");
        // The same target may be republished by native ownership and scene events during the fade.
        // It must keep the original step/deadline instead of restarting from every intermediate colour.
        int scheduled=main.scheduled;
        for(int i=0;i<5;i++) {
            android.os.SystemClock.uptime+=10;
            s.style.mediaOwnershipChanged();Handler.drainForCheck();
            check(main.delayed==step&&main.scheduled==scheduled,"an in-progress same-colour refresh keeps one fade and its deadline");
        }
        android.os.SystemClock.uptime=fadeStarted+FluidCloudStyle.FILL_FADE_MS+1;
        main.delayed.run();
        check(fill(s)!=null&&(fill(s)&0xffffff)==(CUSTOM&0xffffff),"the custom base colour is reached at the original deadline despite repeated refreshes");
        main.delayed=null;
        s.style.notificationRow(s.row);Handler.drainForCheck();
        check(main.delayed==null,"an unchanged colour asks for no frames");
        s.style.release();s.accent.release();Handler.drainForCheck();
    }

}
