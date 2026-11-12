// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Event-driven material policy for the audited Live Alert native backgrounds.
 *
 * BackgroundUtils owns the desktop material, gradient, outline and native alpha.
 * Intercept only its material/resource inputs; restore by rerunning that same
 * initializer. Never replace the card, artwork, layout, animations or observers.
 * The 18-float OplusBlurParam is cloned before editing, because callers can share it.
 */
public final class FluidCloudStyle {
    static final String CUSTOM_ROW = "com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow";
    public static final String MASTER = "fluid_cloud_style_enabled";
    public static final String FOLLOW_NOTIFICATION = "fluid_cloud_follow_notification_enabled";
    public static final String FOLLOW_MEDIA = "fluid_cloud_follow_media_accent_enabled";
    public static final String DESKTOP_HIGHLIGHTS = "fluid_cloud_desktop_highlight_removal_enabled";
    public static final String DESKTOP_BACKGROUND = "fluid_cloud_desktop_background_removal_enabled";
    public static final String MEDIA_KEEP_HIGHLIGHT = "fluid_cloud_media_keep_highlight_enabled";
    public static final String BACKGROUND_MODE = "fluid_cloud_background_mode";
    public static final String BACKGROUND_COLOR = "fluid_cloud_background_color";
    public static final String BACKGROUND_COLOR_CUSTOMIZED = "fluid_cloud_background_color_customized";
    public static final String BACKGROUND_OPACITY = "fluid_cloud_background_opacity";
    /** Transition length of the expanded card's own gradient. 100 keeps the native stops unchanged. */
    public static final String BACKGROUND_GRADIENT_RANGE = "fluid_cloud_background_gradient_range";
    public static final float DEFAULT_BACKGROUND_GRADIENT_RANGE = 100f;
    /** With the range above (the end), these four reshape the same native gradient; the all-default values are the native gradient. */
    public static final String BACKGROUND_GRADIENT_START = "fluid_cloud_background_gradient_start";
    public static final String BACKGROUND_GRADIENT_MID = "fluid_cloud_background_gradient_mid";
    public static final String BACKGROUND_GRADIENT_STRENGTH = "fluid_cloud_background_gradient_strength";
    public static final String BACKGROUND_GRADIENT_OPACITY = "fluid_cloud_background_gradient_opacity";
    public static final float DEFAULT_GRADIENT_START = 0f, DEFAULT_GRADIENT_MID = 50f, DEFAULT_GRADIENT_STRENGTH = 100f, DEFAULT_GRADIENT_OPACITY = 100f;
    public static final String FOLLOW_FOREGROUND = "fluid_cloud_foreground_follow_system_enabled";
    /** Cards follow the media colour only while something is playing; paused or stopped media is not followed. */
    public static final String FOLLOW_ONLY_PLAYING = "fluid_cloud_follow_only_playing_enabled";
    /** Scenes in which the appearance (custom base colour and follow-media colour) applies. The media accent ignores them.
     * Floating and lock screen are on by default and can be switched off. The notification shade is retired (code100): hidden,
     * always off, and neither a saved value nor an imported one turns it on again. */
    public static final String SCOPE_FLOAT = "fluid_cloud_background_scope_float_enabled";
    public static final String SCOPE_SHADE = "fluid_cloud_background_scope_shade_enabled";
    public static final String SCOPE_LOCK = "fluid_cloud_background_scope_lock_enabled";
    /** The bottom capsule of the lock screen and AOD is only tinted while this is on (default off: it stays native). */
    public static final String LOCK_CAPSULE = "fluid_cloud_lock_capsule_enabled";
    /** How a media or followed colour fills a card: "solid" is one flat colour, "gradient" runs it from the top of the card
     * into the card's own material at the bottom, where the native dark gradient was. */
    public static final String COLOR_STYLE = "fluid_cloud_color_style";
    public static final String SOLID = "solid", GRADIENT = "gradient";
    /** Ordinary notification cards follow the current media colour as well. */
    public static final String NOTIFICATION_FOLLOW = "notification_follow_media_enabled";
    public static final String NOTIFICATION_FOLLOW_OPACITY = "notification_follow_media_opacity";
    /** 100% = exactly the tint the fluid-cloud cards get in the same scene; lower values are fainter. */
    public static final float DEFAULT_NOTIFICATION_FOLLOW_OPACITY = 100f;
    public static final int DEFAULT_BACKGROUND_COLOR = 0xff303438;
    public static final float DEFAULT_BACKGROUND_OPACITY = 100f;
    private static final String MANAGER = "com.oplus.view.ViewRootManager";
    private static final String PARAM = "com.oplus.graphics.OplusBlurParam";
    private static final String TAG = "C17FluidStyle";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<ClassLoader, Hooks> contracts = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<ClassLoader, NotificationHooks> notificationContracts = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, BackgroundBinding> backgrounds = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, FluidCloudIdentity.Identity> cardIdentities = Collections.synchronizedMap(new WeakHashMap<>());
    private volatile FluidCloudScope.Policy businessScope = FluidCloudScope.Policy.DEFAULT;
    private final Map<View, NotificationBinding> notifications = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, WeakReference<View>> notificationRows = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<ClassLoader, MediaHooks> mediaContracts = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, MediaBinding> mediaSections = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, MediaBinding> mediaRoots = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable, MediaBinding> mediaLights = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable, MediaBinding> mediaPages = Collections.synchronizedMap(new WeakHashMap<>());
    private final FluidCloudSceneMaterial sceneMaterial = new FluidCloudSceneMaterial();
    private final FluidCloudForeground foreground = new FluidCloudForeground();
    private final ThreadLocal<BackgroundScope> active = new ThreadLocal<>();
    private final ThreadLocal<Boolean> restoring = new ThreadLocal<>();
    private final ThreadLocal<Boolean> replaying = new ThreadLocal<>();
    private final Object pendingLock = new Object();
    private volatile FluidCloudAccent accent;
    private volatile NotificationStyler notificationStyler;
    private volatile boolean follow, highlights, background, released, failureLogged, keyguard;
    private volatile boolean keepMediaHighlight, followForeground, lockCapsule, gradientStyle;
    private long backgroundEvents;
    private String backgroundGate = "unseen", backgroundResult = "unseen";
    private volatile Runnable lockCapsuleListener;
    private volatile boolean followMedia, followOnlyPlaying = true;
    /** Unknown counts as playing until an observer says otherwise, so a missing observer never disables following. */
    private volatile boolean mediaPlaying = true;
    private volatile boolean scopeFloat = true, scopeLock = true, scopeShade, notificationFollow, notificationHighlights;
    private volatile boolean lockEffectsEnabled=true;
    private volatile float notificationFollowOpacity = DEFAULT_NOTIFICATION_FOLLOW_OPACITY, aodNotificationFollowOpacity = DEFAULT_NOTIFICATION_FOLLOW_OPACITY;
    private volatile boolean unifyAod;
    /** The last media colour the cards followed, kept briefly: the media card can be momentarily absent (the lock screen and AOD swap
     * their view trees) while the music keeps playing, and a card must not drop to its native, in AOD black, material for that. */
    private Integer lastFollowMedia;
    private long lastFollowMediaAt;
    /** Kept for the check seam only: the remembered media colour no longer expires while the media is still there. */
    static final long MEDIA_COLOUR_GRACE_MILLIS = 20000L;
    private String lastScene = "none";
    private static final int CARD_LOG_LIMIT = 120;
    private int cardLogs;
    private volatile String backgroundMode = "native";
    private volatile int backgroundColor = DEFAULT_BACKGROUND_COLOR;
    private volatile boolean backgroundColorCustomized;
    private volatile float backgroundOpacity = DEFAULT_BACKGROUND_OPACITY;
    private volatile float backgroundGradientRange = DEFAULT_BACKGROUND_GRADIENT_RANGE;
    private volatile FluidCloudAppearance.GradientShape backgroundGradient = FluidCloudAppearance.GradientShape.NATIVE;
    private final Map<Class<?>, Method> stateGetters = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Class<?>, Method> dozeGetters = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Class<?>, Field> keyguardFields = Collections.synchronizedMap(new WeakHashMap<>());
    private volatile boolean keyguardKnown;
    private volatile boolean dozing;
    private volatile boolean aodTransition;
    /** The native AOD progress has reached its end, or has not moved for a while: nothing is crossfading any more. */
    private volatile boolean aodSettled;
    private volatile long aodProgressAt;
    static final long AOD_SETTLE_MILLIS = 700L;
    private volatile long revision;
    private volatile long observedAccentRevision = -1;
    private boolean refreshPending;
    private boolean ordinaryObserved;

    /** The same C17 controller renders the registered native notification body.
     * It must restore only this registered body's material on follow=false.
     * Immersive music art is a separate lockscreen backdrop, never this body.
     */
    public interface NotificationStyler {
        /** follow: the fluid-cloud policy owns this card (C17 optics apply); keepHighlight: a music card whose native highlight the user chose to keep. */
        void update(View body, boolean follow, boolean keepHighlight);
        default void forget(View body) { update(body, false, false); gradient(body, null); overlay(body, null, null); }
        /** Proven lock bodies use one final media layer ahead of the uniform acrylic tint. */
        default void lockMediaComposition(View body, boolean active) { }
        /** The music card's selected top-to-bottom colour gradient (ARGB at the top, fading out); null draws none.
         * Other card templates keep their flat fill. */
        default void gradient(View body, Integer argb) { }
        /** The fill this card wants (ARGB, strength in alpha), drawn over the card as one flat colour, as the colour of an ordinary
         * notification is, while {@code needed} says so; null draws none. */
        default void overlay(View body, Integer argb, java.util.function.BooleanSupplier needed) { }
    }

    public void setAccentController(FluidCloudAccent controller) {
        FluidCloudAccent previous = accent;
        if (previous != null && previous != controller) previous.setColorListener(null);
        accent = controller; observedAccentRevision = -1;
        if (controller != null) controller.setColorListener(this::refreshAfterAccent);
        syncAccentScene();
    }
    public void setNotificationStyler(NotificationStyler styler) {
        notificationStyler = styler;
        if (styler != null && !released) scheduleRefresh();
    }
    public boolean enabled() { return !released && (follow || highlights || background || followMedia || followForeground || notificationFollow); }
    public boolean followsNotification() { return !released && follow; }

    public NativeRestoreScope beginNativeRestore() { return new NativeRestoreScope(); }
    public final class NativeRestoreScope implements AutoCloseable {
        private final Boolean previous = restoring.get();
        private boolean closed;
        private NativeRestoreScope() { restoring.set(Boolean.TRUE); }
        @Override public void close() {
            if (closed) return; closed = true;
            if (previous == null) restoring.remove(); else restoring.set(previous);
        }
    }

    /** Default normal notification tint, resolved from the same native resource
     * as OplusActivatableNotificationViewExImpl.getNormalColor(Context).
     * This is read only at bind/theme events, never per drawing frame. Missing
     * overlays/contracts leave the native custom-row material intact.
     */
    public Integer normalNotificationColor(View body) {
        NotificationBinding binding = body == null ? null : notifications.get(body);
        return binding == null ? null : binding.appliedColor;
    }
    public boolean notificationUsesAccent(View body) {
        NotificationBinding binding = body == null ? null : notifications.get(body);
        return binding != null && binding.accentColor;
    }
    public boolean beginSceneFrame(){return sceneMaterial.beginFrame();}
    public void endSceneFrame(boolean previous){sceneMaterial.endFrame(previous);}
    public void sceneGeometryChanged(View body){sceneMaterial.geometry(body);}
    public int foregroundTextColor(View view,int color){return foreground.textColor(view,color);}
    public android.content.res.ColorStateList foregroundColor(View view,android.content.res.ColorStateList color){return foreground.color(view,color);}
    public void foregroundColorApplied(View view){foreground.colorApplied(view);}
    /** Exact native content inflation/theme events can change children under an
     * otherwise unchanged root; stable colour refreshes keep this cache intact. */
    public void notificationContentChanged(Object row) {
        if(released||Boolean.TRUE.equals(replaying.get()))return;
        WeakReference<View> reference=notificationRows.get(row);
        NotificationBinding binding=reference==null?null:notifications.get(reference.get());
        if(binding!=null)for(View root:new View[]{binding.card.get(),binding.custom.get(),binding.mini.get()})
            foreground.invalidate(FluidCloudSurfaceRoots.contentOf(root));
        notificationRow(row);
        if(enabled())scheduleRefresh();
    }
    public void cardContentChanged(View card) {
        if(released||Boolean.TRUE.equals(replaying.get())||card==null||!FluidCloudAccent.CARD.equals(card.getClass().getName()))return;
        foreground.invalidate(card);
        if(followForeground)scheduleRefresh();
    }
    /** Capture business identity before native binding can initialize the reused background. */
    public void cardBound(View card,Object item) {
        if(released||card==null||!FluidCloudAccent.CARD.equals(card.getClass().getName()))return;
        FluidCloudIdentity.Identity identity=FluidCloudIdentity.fromViewItem(item);
        FluidCloudIdentity.Identity previous=cardIdentities.put(card,identity);
        boolean changed=previous==null||!previous.serviceId.equals(identity.serviceId)||!previous.packageName.equals(identity.packageName);
        if(changed) {
            businessFact(identity,FluidCloudScope.classify(identity.serviceId,identity.packageName,mediaUnder(card)),"float");
            for(BackgroundBinding binding:backgroundSnapshot())if(binding.card.get()==card)binding.dirty=true;
            // CardView.h can only replace its content. Its native background need not be initialized again.
            // Repeated timer content with the same business does not rebuild the material.
            scheduleRefresh();
        }
    }
    private void businessFact(FluidCloudIdentity.Identity identity,FluidCloudScope.Category business,String host){
        ModuleDiagnostics.fact("fluid","fluid","流体云业务识别","host",host,
                "service_id",FluidCloudScope.diagnosticId(identity.serviceId),"business_package",FluidCloudScope.diagnosticId(identity.packageName),
                "scope_category",business.id,"scope_allowed",businessScope.allows(business));
    }
    private FluidCloudScope.Category businessOf(View card) {
        FluidCloudIdentity.Identity identity=cardIdentities.get(card);
        return FluidCloudScope.classify(identity==null?"":identity.serviceId,identity==null?"":identity.packageName,mediaUnder(card));
    }
    private FluidCloudScope.Category businessOf(NotificationBinding binding) {
        return FluidCloudScope.classify(binding.identity.serviceId,binding.identity.packageName,mediaUnder(binding.card.get()));
    }
    FluidCloudSceneMaterial.Hooks resolveScene(ClassLoader loader) throws ReflectiveOperationException {return sceneMaterial.resolve(loader);}
    public Object sceneBlurMode(Object extension,Object nativeMode){return sceneMaterial.mode(extension,nativeMode);}
    public void sceneMaterialChanged(Object extension){if(sceneMaterial.observed(extension)&&(followForeground||follow||notificationFollow||ordinaryObserved||unifyAod))scheduleRefresh();}
    public void scenePaletteChanged(Object extension) {
        if(released||Boolean.TRUE.equals(replaying.get()))return;
        if(!sceneMaterial.headsPaletteChanged(extension))return;
        sceneMaterialChanged(extension);
        if(follow&&!keyguard&&!dozing){for(BackgroundBinding binding:backgroundSnapshot())binding.dirty=true;scheduleRefresh();}
    }
    public void sceneAodProgressChanged(Object repository,float progress){
        sceneMaterial.aodProgress(repository,progress);
        boolean blocked=!Float.isFinite(progress)||progress!=0f,settled=Float.isFinite(progress)&&progress>=1f;
        // The settled AOD is a progress of 1, and the progress stays non-zero all through it: a colour decision held from the awake screen
        // may be replaced only here, never during the crossfade (aodTransition alone cannot tell the two apart).
        aodProgressAt=android.os.SystemClock.uptimeMillis();
        if(aodSettled!=settled){aodSettled=settled;scheduleRefresh();}
        if(aodTransition!=blocked){aodTransition=blocked;syncAccentScene();sceneFact("aod_progress");scheduleRefresh();}
    }
    public void prepareSceneAodProgress(Object repository,float progress){sceneMaterial.prepareAodProgress(repository,progress);}
    /** Asleep with no crossfade running (progress at its end, or silent for a moment): a held awake decision may be replaced now. */
    private boolean aodAtRest(){
        return dozing&&(!aodTransition||aodSettled||android.os.SystemClock.uptimeMillis()-aodProgressAt>AOD_SETTLE_MILLIS);
    }
    private boolean materialAllowed(){return !dozing&&!(keyguard&&aodTransition);}
    private boolean floatingAllowed(View host){
        if(!materialAllowed()||keyguard||host==null)return false;
        // Entry fields are reusable cached content, not its current scene. Only
        // a real notification-row parent or its visible remote Surface owns it.
        return !FluidCloudSurfaceRoots.notificationHosted(host);
    }

    private void syncAccentScene(){FluidCloudAccent owner=accent;if(owner!=null)owner.scene(keyguard,!materialAllowed());}
    public void sceneMotionChanged(Object manager,Object proxy){sceneMaterial.motionChanged(manager,proxy);}
    public void sceneMetaChanged(Object helper,android.content.Context context,Object entry,Drawable drawable){sceneMaterial.meta(helper,context,entry,drawable);}
    public AutoCloseable beginSceneDraw(Object extension){return sceneMaterial.draw(extension);}
    public AutoCloseable beginScenePlatform(Object proxy){return sceneMaterial.platform(proxy);}
    public AutoCloseable beginSceneBackend(Object proxy,Method submitted){return sceneMaterial.platform(proxy,submitted);}
    public boolean deferScenePlatform(Object proxy){return sceneMaterial.deferPlatform(proxy);}
    public void scenePlatformCompleted(AutoCloseable scope){if(scope instanceof FluidCloudSceneMaterial.PlatformScope)sceneMaterial.platformCompleted((FluidCloudSceneMaterial.PlatformScope)scope);}
    public void sceneProxyChanged(Object proxy){sceneMaterial.proxyChanged(proxy);}
    public Drawable sceneDrawable(View body,Drawable original){return sceneMaterial.drawable(body,original);}
    public Drawable sceneAodMask(Object extension,Drawable original){return sceneMaterial.aodMask(extension,original);}
    public boolean sceneNeedsMeta(Object entry,boolean original){return sceneMaterial.needsMeta(entry,original);}
    public void onNativeState(Object controller) {
        if (released || controller == null || !controller.getClass().getName().equals(LockscreenStatusBlur.STATE_CONTROLLER)) return;
        try {
            Method getter = stateGetters.get(controller.getClass());
            if (getter == null) {
                getter = controller.getClass().getDeclaredMethod("getState"); getter.setAccessible(true);
                stateGetters.put(controller.getClass(), getter);
            }
            Object value = getter.invoke(controller);
            Method dozeGetter=dozeGetters.get(controller.getClass());
            if(dozeGetter==null) {
                dozeGetter=controller.getClass().getDeclaredMethod("isDozing");dozeGetter.setAccessible(true);
                dozeGetters.put(controller.getClass(),dozeGetter);
            }
            boolean nextDozing=Boolean.TRUE.equals(dozeGetter.invoke(controller));
            if(dozing!=nextDozing){dozing=nextDozing;sceneMaterial.sceneState(follow,keyguard,dozing);syncAccentScene();sceneFact("doze");scheduleRefresh();}
            if (value instanceof Integer && !keyguardKnown) {
                int state = (Integer) value; setKeyguard(state == 1 || state == 2);
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) { reportFailure(unavailable); }
    }
    void setKeyguard(boolean showing) {
        if (keyguard == showing) return;
        keyguard = showing; sceneMaterial.sceneState(follow,keyguard,dozing);syncAccentScene();sceneFact("keyguard");scheduleRefresh();
    }

    private String sceneName() { return !keyguard ? "shade" : dozing || aodTransition ? "aod" : "lock"; }

    /** One record per scene change: the single place a timeline of lock/AOD transitions can be read from. */
    private void sceneFact(String reason) {
        if (!ModuleDiagnostics.enabled()) return;
        String name = sceneName();
        ModuleDiagnostics.fact("scene", "scene", "场景切换", "scene", name, "previous", lastScene, "keyguard", keyguard,
                "dozing", dozing, "aod_transition", aodTransition, "reason", reason);
        lastScene = name;
    }
    public void onNativeKeyguard(Object controller) {
        if (released || controller == null || !controller.getClass().getName().equals(LockscreenStatusBlur.KEYGUARD_CONTROLLER)) return;
        try {
            Field showing = keyguardFields.get(controller.getClass());
            if (showing == null) {
                showing = controller.getClass().getDeclaredField("mShowing");
                if (showing.getType() != Boolean.TYPE) return;
                showing.setAccessible(true); keyguardFields.put(controller.getClass(), showing);
            }
            keyguardKnown = true; setKeyguard(showing.getBoolean(controller));
        } catch (ReflectiveOperationException | RuntimeException unavailable) { reportFailure(unavailable); }
    }

    private Integer readNormalNotificationColor(View body) {
        try {
            Resources resources = body.getResources();
            int id = FluidCloudSceneMaterial.colorId(resources, "notification_material_background_color_compass");
            if (id != 0) return resources.getColor(id, body.getContext().getTheme());
        } catch (RuntimeException | LinkageError unavailable) {
            reportFailure(unavailable);
        }
        return null;
    }

    public Hooks resolve(ClassLoader loader) throws ReflectiveOperationException {
        if (loader == null || released) return null;
        Hooks cached = contracts.get(loader);
        if (cached != null) return cached;
        Hooks result = new Hooks(loader);
        // Plugin discovery can delegate to the loader that defines the view.
        // Native events use that defining loader when looking up this contract.
        contracts.put(loader, result);
        contracts.put(result.backgroundType.getClassLoader(), result);
        return result;
    }

    /** Structural state for an explicit verified probe from our settings app. */
    public String diagnosticSummary() {
        ArrayList<BackgroundBinding> bindings = backgroundSnapshot();
        StringBuilder result = new StringBuilder("desktopContracts=").append(contracts.size())
                .append("; backgroundOwners=").append(bindings.size())
                .append("; appearance=").append(background).append('/').append(backgroundMode)
                .append("; floatScope=").append(scopeFloat).append("; range=").append(backgroundGradientRange)
                .append("; initEvents=").append(backgroundEvents).append("; gate=").append(backgroundGate)
                .append("; lastBackground=").append(backgroundResult);
        int count = 0;
        for (BackgroundBinding binding : bindings) {
            View host = binding.host.get();
            if (host == null || count++ == 4) break;
            Drawable root = host.getBackground();
            result.append("; root=").append(root == null ? "none" : root.getClass().getName());
            if (root != null && root.getClass() == android.graphics.drawable.LayerDrawable.class) {
                android.graphics.drawable.LayerDrawable layers = (android.graphics.drawable.LayerDrawable) root;
                int size = Math.min(4, layers.getNumberOfLayers());
                for (int i = 0; i < size; i++) {
                    Drawable leaf = layers.getDrawable(i);
                    result.append('/').append(leaf == null ? "none" : leaf.getClass().getName());
                }
            }
            result.append("; rangeOwned=").append(FluidCloudAppearance.nativeGradientOwned(root));
        }
        return result.toString();
    }

    private static String backgroundDescription(Drawable root) {
        StringBuilder result = new StringBuilder(root == null ? "none" : root.getClass().getName());
        if (root != null && root.getClass() == android.graphics.drawable.LayerDrawable.class) {
            android.graphics.drawable.LayerDrawable layers = (android.graphics.drawable.LayerDrawable) root;
            for (int i = 0, size = Math.min(4, layers.getNumberOfLayers()); i < size; i++) {
                Drawable leaf = layers.getDrawable(i);
                result.append('/').append(leaf == null ? "none" : leaf.getClass().getName());
            }
        }
        return result.toString();
    }

    public NotificationHooks resolveNotification(ClassLoader loader) throws ReflectiveOperationException {
        if (loader == null || released) return null;
        NotificationHooks cached = notificationContracts.get(loader);
        if (cached != null) return cached;
        NotificationHooks result = new NotificationHooks(loader); notificationContracts.put(loader, result);
        notificationContracts.put(result.rowType.getClassLoader(),result);return result;
    }

    public MediaHooks resolveMedia(ClassLoader loader) throws ReflectiveOperationException {
        if (loader == null || released) return null;
        MediaHooks cached = mediaContracts.get(loader);
        if (cached != null) return cached;
        MediaHooks result = new MediaHooks(loader); mediaContracts.put(loader, result);
        mediaContracts.put(result.sectionType.getClassLoader(),result);return result;
    }

    /** The media template draws a second, independent RuntimeShader background.
     * Bind its exact F identity once per native bind/color event. Album ImageViews
     * and the fullscreen immersive wallpaper are deliberately outside this owner. */
    public void mediaChanged(Object section) {
        if (released || section == null) return;
        MediaHooks access = mediaContracts.get(section.getClass().getClassLoader());
        if (access == null || !access.sectionType.isInstance(section)) return;
        try {
            Object value = access.mediaView.get(section);
            Object nativeContainer = access.root.get(section);
            View root = nativeContainer instanceof View ? (View) nativeContainer : null;
            View host = value instanceof View ? (View) value : null;
            if (root == null && host == null) { mediaDisposed(section); return; }
            Object nativeLight = host == null ? null : access.light.get(host);
            Object model = access.model.get(section);
            Object stream = model == null ? null : access.notification.get(model);
            Object state = stream == null ? null : access.flow.get(stream);
            Object inNotification = state == null ? null : access.value.invoke(state);
            if (!(inNotification instanceof Boolean)) { mediaDisposed(section); return; }
            Drawable light = nativeLight instanceof Drawable && host.getBackground() == nativeLight ? (Drawable) nativeLight : null;
            Object nativePageOwner=access.pageOwner.get(section);
            Object nativeRoot=nativePageOwner==null?null:access.pageView.get(nativePageOwner);
            View page=access.pageType.isInstance(nativeRoot)?(View)nativeRoot:null;
            Drawable pageBackground=null,pageLight=null;
            if(page!=null) {
                Object nativePage=access.pageBackground.get(page);
                if(nativePage instanceof Drawable&&page.getBackground()==nativePage) {
                    pageBackground=(Drawable)nativePage;
                    Object decorative=access.pageLight.get(nativePage);
                    if(decorative instanceof Drawable)pageLight=(Drawable)decorative;
                }
            }
            FluidCloudAccent owner=accent;
            Integer color=owner==null?null:owner.nativeMediaColor(section);
            if(owner!=null&&root!=null)owner.mediaChanged(section,root,color);
            MediaBinding previous=mediaSections.get(section);
            if(previous!=null&&previous.root.get()==root&&previous.host.get()==host&&previous.light.get()==light
                    &&previous.notification==(Boolean)inNotification&&previous.page.get()==page
                    &&previous.pageBackground.get()==pageBackground&&previous.pageLight.get()==pageLight) {
                if(same(previous.nativeColor,color))return;
                previous.nativeColor=color;
                if(mediaPolicyActive())scheduleRefresh(); return;
            }
            if(previous!=null)unregisterMedia(previous);
            MediaBinding binding = new MediaBinding(section, root, host, light, access, (Boolean)inNotification);
            binding.nativeColor=color;
            binding.page=new WeakReference<>(page);binding.pageBackground=new WeakReference<>(pageBackground);
            binding.pageLight=new WeakReference<>(pageLight);
            if(pageBackground!=null)mediaPages.put(pageBackground,binding);
            if(pageLight!=null)mediaLights.put(pageLight,binding);
            mediaSections.put(section, binding); if (light != null) mediaLights.put(light, binding);
            if(root!=null) {
                MediaBinding replaced=mediaRoots.put(root,binding);
                if(replaced!=null&&replaced!=previous) {
                    Object oldSection=replaced.section.get();if(oldSection!=null)mediaSections.remove(oldSection);
                    unregisterMedia(replaced);
                    // unregistering the old owner only clears its own mappings.
                }
            }
            if(root!=null)root.addOnAttachStateChangeListener(binding);
            if(mediaPolicyActive())scheduleRefresh();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { reportFailure(unavailable); }
    }

    public void mediaDisposed(Object section) {
        // A media card that is really gone leaves no colour to follow, not even a remembered one.
        lastFollowMedia=null;
        FluidCloudAccent owner=accent;if(owner!=null)owner.mediaDisposed(section);
        MediaBinding previous = mediaSections.remove(section);
        if (previous == null) return;
        unregisterMedia(previous);
        if(!released)scheduleRefresh();
    }
    private void unregisterMedia(MediaBinding previous) {
        Drawable light = previous.light.get(); View host = previous.host.get();
        if (light != null) synchronized(mediaLights){if(mediaLights.get(light)==previous)mediaLights.remove(light);}
        Drawable pageLight=previous.pageLight.get();
        if(pageLight!=null)synchronized(mediaLights){if(mediaLights.get(pageLight)==previous)mediaLights.remove(pageLight);}
        Drawable page=previous.pageBackground.get();
        if(page!=null)synchronized(mediaPages){if(mediaPages.get(page)==previous)mediaPages.remove(page);}
        if(previous.lightSuppressed||previous.appliedAccent!=null) {
            View pageHost=previous.page.get();if(pageHost!=null)pageHost.invalidate();
            if (host != null) host.invalidate();
        }
        View root=previous.root.get();if(root!=null)root.removeOnAttachStateChangeListener(previous);
        if(root!=null&&mediaRoots.get(root)==previous)mediaRoots.remove(root);
    }

    /** f.draw only draws its decorative shader rectangle (verified native DEX).
     * Bypass it instead of erasing all media children, their native alpha or art. */
    public boolean suppressMediaLight(Drawable light) {
        if (released || !materialAllowed() || light == null) return false;
        MediaBinding binding = mediaLights.get(light);
        if(binding==null||!binding.attached)return false;
        View host = binding == null ? null : binding.host.get();
        boolean ownMedia=host!=null&&host.getBackground()==light;
        View page=binding==null?null:binding.page.get();
        boolean ownPage=page!=null&&binding.pageLight.get()==light&&page.getBackground()==binding.pageBackground.get();
        if (!ownMedia&&!ownPage) return false;
        // F is decoration only. Primary-color inputs are owned by s1/K0 and
        // must not exempt this shader when the independent optical policy is ON.
        // Scene ownership is resolved on native bind/policy events. Draw performs
        // no notification tree walk, snapshot allocation or resource query.
        return binding.lightSuppressed;
    }

    public interface MediaDraw { Object run() throws Throwable; }
    /** The immersive root's own material is separate from mBackgroundNormal.
     * Filter just that drawable's covered pixels; album art and media children
     * remain outside the layer, including the in-process SurfaceView host.
     */
    public Object drawMediaPage(Drawable drawable,Canvas canvas,MediaDraw nativeDraw) throws Throwable {
        // page.a contains an animated blurred light and a decorative shader,
        // not the black template base. Recolouring this whole draw made a solid
        // card and erased the intended gradient. MetaBall owns the actual base.
        return nativeDraw.run();
    }

    /** Bind after native inflation/entry/theme events, never from a drawing frame. */
    public void notificationRow(Object row) {
        if (released || row == null || Boolean.TRUE.equals(replaying.get())) return;
        NotificationHooks access = notificationContracts.get(row.getClass().getClassLoader());
        if (access == null || !access.rowType.isInstance(row)) return;
        try {
            Object body = access.backgroundGetter.invoke(row);
            WeakReference<View> previous = notificationRows.get(row);
            View previousBody = previous == null ? null : previous.get();
            View nextBody = body instanceof View && isNotificationBody((View) body) ? (View) body : null;
            if (previousBody != nextBody) {
                if (nextBody == null) notificationRows.remove(row); else notificationRows.put(row, new WeakReference<>(nextBody));
                if (previousBody != null) forgetNotification(previousBody);
            }
            if (nextBody != null) {
                registerNotificationBody(nextBody);
                Object entry = access.entryGetter.invoke(row);
                sceneMaterial.register(nextBody,entry,row);
                Object card = entry == null ? null : access.cardGetter.invoke(entry);
                Object custom = entry == null ? null : access.customGetter.invoke(entry);
                Object mini = entry == null ? null : access.miniGetter.invoke(entry);
                NotificationBinding binding = notifications.get(nextBody);
                boolean changed=binding.entry.get()!=entry||binding.card.get()!=card||binding.custom.get()!=custom||binding.mini.get()!=mini;
                if(changed) {
                    binding.initialized=false;
                    View[] previousRoots={binding.card.get(),binding.custom.get(),binding.mini.get()};
                    Object[] nextRoots={card,custom,mini};
                    for(int i=0;i<previousRoots.length;i++) {
                        if(previousRoots[i]!=nextRoots[i])foreground.detach(FluidCloudSurfaceRoots.contentOf(previousRoots[i]));
                        if(nextRoots[i] instanceof View)foreground.invalidate(FluidCloudSurfaceRoots.contentOf((View)nextRoots[i]));
                    }
                }
                binding.entry=new WeakReference<>(entry);
                FluidCloudIdentity.Identity identity=FluidCloudIdentity.fromEntry(entry);
                boolean identityChanged=!binding.identity.serviceId.equals(identity.serviceId)||!binding.identity.packageName.equals(identity.packageName);
                binding.identity=identity;
                if(identityChanged)businessFact(identity,businessOf(binding),"notification");
                binding.card = new WeakReference<>(card instanceof View ? (View) card : null);
                binding.custom = new WeakReference<>(custom instanceof View ? (View) custom : null);
                binding.mini = new WeakReference<>(mini instanceof View ? (View) mini : null);
                updateNotification(binding, false);
                if(changed)scheduleRefresh();
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { reportFailure(unavailable); }
    }

    /** Begin only the proven desktop CardBackgroundView -> CardView chain. */
    public BackgroundScope beginBackground(Object helper, View host, Object manager) {
        if (released || Boolean.TRUE.equals(restoring.get()) || helper == null || host == null
                || !helper.getClass().getName().equals(FluidCloudAccent.BACKGROUND_HELPER)
                || !host.getClass().getName().equals(FluidCloudAccent.BACKGROUND)) return null;
        backgroundEvents++;
        Hooks access = contracts.get(host.getClass().getClassLoader());
        if (access == null) { backgroundGate = "contract"; return null; }
        if (manager != null && !access.managerType.isInstance(manager)) { backgroundGate = "manager"; return null; }
        View card = cardOf(host);
        if (card == null) { backgroundGate = "parent"; return null; }
        backgroundGate = "registered";
        // The next OEM initialization may replace or reuse its own gradient.
        // Release the previous shader lease before it constructs the next one.
        FluidCloudAppearance.gradient(host.getBackground(), host.getResources(), DEFAULT_BACKGROUND_GRADIENT_RANGE);
        BackgroundBinding binding = backgrounds.get(host);
        if (binding == null) {
            binding = new BackgroundBinding(host, card, access); backgrounds.put(host, binding);
            Log.i(TAG, "Live Alert desktop owner registered: blur=" + (manager != null));
        }
        if (binding.manager.get() != manager) {
            binding.nativeParams = null; binding.derivedParams = null; binding.appliedRevision = -1;
        }
        binding.helper = new WeakReference<>(helper); binding.manager = new WeakReference<>(manager);
        binding.managerRequired = manager != null;
        FluidCloudAccent owner = accent;
        boolean protectedColor = owner != null && (owner.activeAccentBackground() || owner.protectsBackground(host));
        FluidCloudScope.Category business=businessOf(card);
        boolean selected=businessScope.allows(business);
        boolean sceneAllowed=floatingAllowed(host)&&!Boolean.TRUE.equals(restoring.get());
        boolean allowed = sceneAllowed&&selected;
        Integer ownMedia = owner == null ? null : owner.nativeBackgroundMediaColor(host);
        Integer nativeMedia = owner == null ? null : owner.nativeCurrentMediaColor(host);
        boolean music = ownMedia != null || mediaUnder(card);
        boolean follows = allowed && follow && !keyguard && !protectedColor;
        // Priority 1 is the card's own media accent (applied by FluidCloudAccent and never overridden here),
        // priority 2 follows the current media colour, priority 3 is the custom base colour.
        Integer fill = floatingFade(binding, allowed ? floatingFill(nativeMedia, protectedColor) : null);
        boolean floatBackground = background && scopeFloat, floatFollow = followMedia && scopeFloat;
        float[] heads = follows && !floatBackground && !floatFollow && manager!=null ? sceneMaterial.headsUpParams(host) : null;
        // Legacy no-mode bundles retain their transparent material until Upgrade80
        // writes the explicit custom/0% migration. Native source keeps OEM RGB,
        // while its independent opacity applies to the actual material stops.
        boolean removeFill = allowed && floatBackground && !followMedia && "custom".equals(backgroundMode)
                && backgroundOpacity == 0f;
        // A protected media accent takes the media-accent page's opacity; only the custom base colour, or an opacity
        // on its own, takes the style page's.
        float nativeOpacity = !allowed ? 1f
                : protectedColor && owner != null ? owner.fillFactor(false)
                : floatBackground && fill == null ? backgroundOpacity / 100f : 1f;
        BackgroundScope scope = new BackgroundScope(binding, sceneAllowed && highlights && !(keepMediaHighlight && music),
                removeFill, fill, nativeOpacity, protectedColor,selected, revision, active.get());
        // This page owns only its base appearance. A card's media accent and a
        // followed media colour keep their existing spatial colour policy.
        boolean baseAppearance = allowed && floatBackground && !protectedColor
                && !(followActive() && scopeFloat && nativeMedia != null);
        scope.solidFill = baseAppearance && "custom".equals(backgroundMode) ? fill : null;
        boolean shaped = baseAppearance && "native".equals(backgroundMode);
        scope.nativeGradientIndependent = shaped;
        scope.gradientTint = shaped ? fill : null;
        scope.gradientRange = shaped ? backgroundGradientRange : DEFAULT_BACKGROUND_GRADIENT_RANGE;
        scope.gradient = shaped ? backgroundGradient : FluidCloudAppearance.GradientShape.NATIVE;
        scope.followNotification=heads!=null;
        scope.headsParams=heads;
        if((background||followMedia||highlights||protectedColor)&&binding.appearanceDiagnosticCount<12){
            String appearanceState="floating appearance: allowed="+allowed+", source="+backgroundMode
                    +", edited="+backgroundColorCustomized+", media="+followMedia+", accent="+protectedColor
                    +", fill="+(fill==null?"native":Integer.toHexString(fill))+", opacity="+nativeOpacity
                    +", range="+scope.gradientRange+", business="+business.id+", selected="+selected;
            if(!appearanceState.equals(binding.appearanceDiagnostic)){
                binding.appearanceDiagnostic=appearanceState;binding.appearanceDiagnosticCount++;Log.i(TAG,appearanceState);
                ModuleDiagnostics.fact("fluid","fluid","悬浮卡片背景决策","scene","float","host","card",
                        "source",protectedColor?"accent":fill==null?"native":followMedia&&scopeFloat&&nativeMedia!=null?"follow_media":"custom",
                        "accent_enabled",owner!=null&&owner.enabled(),"accent_active",protectedColor,"follow_media",followMedia,
                        "custom_active",floatBackground,"scope_float",scopeFloat,"applied",allowed,
                        "scope_category",business.id,"scope_allowed",selected,
                        "fill_argb",fill==null?"native":"#"+Integer.toHexString(fill),"opacity",nativeOpacity,
                        "gradient_range",scope.gradientRange,"gradient_start",scope.gradient.start*100f,"gradient_mid",scope.gradient.mid*100f,
                        "gradient_strength",scope.gradient.strength*100f,"gradient_opacity",scope.gradient.opacity*100f,
                        "media_argb",nativeMedia==null?"none":"#"+Integer.toHexString(nativeMedia),
                        "highlight",sceneAllowed&&highlights&&!(keepMediaHighlight&&music),"keep_highlight",keepMediaHighlight&&music);
            }
        }
        if(follows && !background && !followMedia && manager!=null) {
            String state=heads==null?"floating material: native heads-up recipe unavailable"
                    :"floating material: HEADS_UP, blurType="+heads[0]+", blendMode="+heads[3]
                    +", bottomAlpha="+heads[7]+", topAlpha="+heads[11];
            if(!state.equals(binding.diagnostic)&&binding.diagnosticCount<8) {
                binding.diagnostic=state;binding.diagnosticCount++;Log.i(TAG,state);
            }
        }
        active.set(scope); return scope;
    }

    /** Runs after Accent.resourceColor, in its exact native initializer scope. */
    public int resourceColor(Resources resources, int id, int nativeColor) {
        BackgroundScope scope = active.get();
        if (released || scope == null || (!scope.followNotification && !scope.removeBackground && !scope.removeHighlights && scope.fill == null && scope.nativeOpacity == 1f)
                || resources == null || Boolean.TRUE.equals(restoring.get())) return nativeColor;
        View host = scope.binding.host.get();
        if (host == null || host.getResources() != resources) return nativeColor;
        String name;
        try { name = resources.getResourceEntryName(id); }
        catch (Resources.NotFoundException changed) { return nativeColor; }
        boolean gradient = isGradient(name);
        // Keep active album/primaryColor inputs intact. Its native blend still loses
        // GLOW below, but removing its color would erase the existing accent feature.
        boolean clear = scope.removeBackground && (gradient || isMaterialColor(name))
                || scope.followNotification && gradient;
        // Accent rewrites these same resource stops. Removing their alpha also
        // removes the album gradient; its glow is handled by params/F separately.
        if (scope.removeHighlights && gradient && !scope.keepMaterial) clear = true;
        int result = clear ? nativeColor & 0x00ffffff : nativeColor;
        // BackgroundUtils reads a/b/c before constructing the OEM gradient. In native background mode its
        // own Shape parameters are the only appearance controls of that layer; base colour/opacity belong
        // to the blur material below it. Media ownership, explicit solid fill and independent optics keep
        // their existing resource policy.
        boolean baseColor = isMaterialColor(name) || gradient && !scope.nativeGradientIndependent;
        if (scope.fill != null && baseColor) {
            result = materialFill(result, scope.fill);
            scope.resourcesChanged = true;
        }
        else if (scope.nativeOpacity != 1f && baseColor) {
            result = materialOpacity(result, scope.nativeOpacity);
            scope.resourcesChanged = true;
        }
        if (result != nativeColor) scope.changed = true;
        return result;
    }

    /** Before ViewRootManager.setBlurParams. Unowned calls keep their original object.
     * Native radius/shape/tile mode/zoom are retained. Following uses the normal
     * HEADS_UP blur type and both native mix colours, including their alpha.
     */
    public Object blurParams(Object manager, Object nativeParam) {
        BackgroundScope scope = active.get();
        if (released || scope == null || nativeParam == null
                || Boolean.TRUE.equals(restoring.get()) || scope.binding.manager.get() != manager
                || (!scope.followNotification && !scope.removeHighlights && !scope.removeBackground && scope.fill == null && scope.nativeOpacity == 1f)) return nativeParam;
        Hooks access = scope.binding.access;
        if (!access.paramType.isInstance(nativeParam)) return nativeParam;
        try {
            float[] values = (float[]) access.toArray.invoke(nativeParam);
            if (!validParams(values)) return nativeParam;
            BackgroundBinding binding = scope.binding;
            int policy = (scope.removeHighlights ? 1 : 0) | (scope.removeBackground ? 2 : 0);
            if (binding.derivedParams != null && binding.paramPolicy == policy
                    && binding.paramOpacity == scope.nativeOpacity && binding.paramResourceAlpha == scope.resourcesChanged
                    && (scope.fill == null ? binding.paramFill == null : scope.fill.equals(binding.paramFill))
                    && Arrays.equals(binding.headsParams, scope.headsParams)
                    && Arrays.equals(binding.nativeParams, values)) {
                scope.changed = true; return binding.derivedParams;
            }
            float[] derived = transformParams(values, scope.removeHighlights, scope.removeBackground);
            if(scope.headsParams!=null) {
                // Factory owns material, host owns geometry and animation. In
                // particular retain distinct top/bottom alpha and clear the
                // desktop-only blur-colour layer via the factory's defaults.
                derived=scope.headsParams.clone();
                for(int slot:new int[]{1,2,16,17})derived[slot]=values[slot];
                derived=transformParams(derived,scope.removeHighlights,scope.removeBackground);
            }
            // Hardware-only initializers need the alpha adjustment here. When
            // BackgroundUtils read the resource stops it already applied it.
            if (scope.nativeOpacity != 1f && !scope.resourcesChanged)
                for (int offset : new int[]{7, 11, 15}) derived[offset] *= scope.nativeOpacity;
            if (scope.fill != null) {
                if(!scope.resourcesChanged)
                    for(int offset:new int[]{7,11,15})derived[offset]*=(scope.fill>>>24)/255f;
                // BackgroundUtils already consumed our resource substitution.
                // Reapplying the opacity here would square it. Only normalize RGB
                // of the two material colors; OEM and substituted alpha stay intact.
                for (int offset : new int[]{4, 8, 12}) {
                    derived[offset] = ((scope.fill >> 16) & 255) / 255f;
                    derived[offset + 1] = ((scope.fill >> 8) & 255) / 255f;
                    derived[offset + 2] = (scope.fill & 255) / 255f;
                }
            }
            if (Arrays.equals(values, derived)) return nativeParam;
            Object result = access.paramConstructor.newInstance();
            access.fromArray.invoke(result, (Object) derived);
            binding.nativeParams = values.clone(); binding.derivedParams = result; binding.paramPolicy = policy; binding.paramFill = scope.fill;
            binding.paramOpacity = scope.nativeOpacity; binding.paramResourceAlpha = scope.resourcesChanged;
            binding.headsParams=scope.headsParams;
            scope.changed = true; return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            reportFailure(unavailable); return nativeParam;
        }
    }

    static float[] transformParams(float[] nativeValues, boolean removeHighlights, boolean removeBackground) {
        if (!validParams(nativeValues)) return nativeValues;
        float[] result = nativeValues.clone();
        // Real OplusBlurParam constants: GLOW_OVERLAY=4, OVERLAY=2;
        // GLOW_COLORDODGE=5, COLORDODGE=3. Preserve all non-GLOW blend modes.
        if (removeHighlights && result[3] == 4f) result[3] = 2f;
        else if (removeHighlights && result[3] == 5f) result[3] = 3f;
        if (removeBackground) { result[7] = 0f; result[11] = 0f; result[15] = 0f; }
        return result;
    }

    private static boolean validParams(float[] values) {
        if (values == null || values.length != 18) return false;
        for (float value : values) if (!Float.isFinite(value)) return false;
        return true;
    }
    private static boolean isMaterialColor(String name) {
        return "card_background_mix_color".equals(name) || "card_background_blend_color".equals(name)
                || "card_background_blend_color_old".equals(name);
    }
    private static boolean isGradient(String name) {
        return "card_background_gradient_top_color".equals(name)
                || "card_background_gradient_middle_color".equals(name)
                || "card_background_gradient_bottom_color".equals(name);
    }

    public void nativeBlurAlpha(View host, float alpha) {
        if (host == null || !Float.isFinite(alpha)) return;
        BackgroundBinding binding = backgrounds.get(host);
        if (binding != null) binding.nativeAlpha = Math.max(0f, Math.min(1f, alpha));
    }

    /** Register a proven native NotificationBackgroundView only. Call from the
     * exact OplusCustomRow body getter/bind, never immersiveFullBg or a page flag.
     */
    public void notificationBody(View body) {
        registerNotificationBody(body);
        NotificationBinding binding=body==null?null:notifications.get(body);
        if(binding!=null)updateNotification(binding,false);
    }
    private void registerNotificationBody(View body) {
        if (released || body == null || !isNotificationBody(body)) return;
        NotificationBinding binding = notifications.get(body);
        if (binding == null) {
            binding = new NotificationBinding(body); notifications.put(body, binding);
            Log.i(TAG, "Live Alert notification owner registered");
        }
        Integer normal = readNormalNotificationColor(body);
        if (binding.normalColor == null ? normal != null : !binding.normalColor.equals(normal)) {
            binding.normalColor = normal; binding.initialized = false;
        }
    }
    public boolean isNotificationBackground(View body) { return !released && follow && notifications.containsKey(body); }

    public void configure(Bundle settings) {
        if (released) return;
        FluidCloudScope.Policy nextBusinessScope=FluidCloudScope.Policy.from(settings);
        boolean safe = SafetyMode.enabled(settings);
        boolean nextLockEffects=!safe&&!CustomPluginMaterialIsolation.lockOwned(settings);
        boolean legacy = settings != null && (settings.getBoolean(FOLLOW_NOTIFICATION,false)
                ||settings.getBoolean(DESKTOP_BACKGROUND,false));
        boolean master = settings != null && !safe && settings.getBoolean(MASTER, legacy);
        boolean nextFollow = master && settings.getBoolean(FOLLOW_NOTIFICATION, false);
        // Highlight removal is the C17 feature. The desktop fluid-cloud card follows it instead of having a second switch.
        boolean nextHighlights = settings != null && !safe && settings.getBoolean(C17HighlightRemoval.ENABLED, false)
                && settings.getBoolean(C17HighlightRemoval.HEADS_UP_ENABLED, true);
        boolean nextBackground = master && settings.getBoolean(DESKTOP_BACKGROUND, false);
        // Following the media colour is part of the media-accent page, so that page's master governs it; code100: it is also a way of
        // choosing the base colour, so it needs the style page's custom base colour ("自定义底色") switched on as well.
        boolean nextMedia = settings != null && !safe && settings.getBoolean(FluidCloudAccent.ENABLED, false) && nextBackground
                && settings.getBoolean(FOLLOW_MEDIA, "media".equals(settings.get(BACKGROUND_MODE)));
        boolean nextOnlyPlaying = settings == null || settings.getBoolean(FOLLOW_ONLY_PLAYING, true);
        boolean nextKeep = nextHighlights && settings.getBoolean(MEDIA_KEEP_HIGHLIGHT, false);
        // Retired policy: old backups retain the transport key, while native
        // text/tint ownership is restored even if it was previously saved ON.
        boolean nextForeground = false;
        Object mode = settings == null ? null : settings.get(BACKGROUND_MODE);
        String nextMode = validBackgroundMode(mode);
        boolean legacyBackground = mode == null && nextBackground;
        if (legacyBackground) nextMode = "custom";
        Object color = settings == null ? null : settings.get(BACKGROUND_COLOR);
        int nextColor = color instanceof Number ? ((Number) color).intValue() : DEFAULT_BACKGROUND_COLOR;
        Object alpha = settings == null ? null : settings.get(StatusBarSettings.alphaKey(BACKGROUND_COLOR));
        if (!(alpha instanceof Boolean ? (Boolean) alpha : (nextColor >>> 24) != 255)) nextColor |= 0xff000000;
        // Older versions could save custom colour before switching to native.
        // A default inserted during migration is not an explicit colour edit.
        boolean nextCustomized = settings != null && settings.getBoolean(BACKGROUND_COLOR_CUSTOMIZED,false)
                || nextColor != DEFAULT_BACKGROUND_COLOR;
        Object opacity = settings == null ? null : settings.get(BACKGROUND_OPACITY);
        float nextOpacity = legacyBackground ? 0f : opacity instanceof Number ? ((Number) opacity).floatValue() : DEFAULT_BACKGROUND_OPACITY;
        if (!Float.isFinite(nextOpacity)) nextOpacity = DEFAULT_BACKGROUND_OPACITY;
        nextOpacity = Math.max(0f, Math.min(100f, nextOpacity));
        Object range = settings == null ? null : settings.get(BACKGROUND_GRADIENT_RANGE);
        float nextRange = FluidCloudAppearance.range(range instanceof Number ? ((Number) range).floatValue()
                : DEFAULT_BACKGROUND_GRADIENT_RANGE);
        FluidCloudAppearance.GradientShape nextGradient = FluidCloudAppearance.GradientShape.of(
                number(settings, BACKGROUND_GRADIENT_START, DEFAULT_GRADIENT_START), nextRange,
                number(settings, BACKGROUND_GRADIENT_MID, DEFAULT_GRADIENT_MID),
                number(settings, BACKGROUND_GRADIENT_STRENGTH, DEFAULT_GRADIENT_STRENGTH),
                number(settings, BACKGROUND_GRADIENT_OPACITY, DEFAULT_GRADIENT_OPACITY));
        boolean nextFloat = settings == null || settings.getBoolean(SCOPE_FLOAT, true);
        boolean nextLock = nextLockEffects&&(settings == null || settings.getBoolean(SCOPE_LOCK, true));
        // The notification-shade scene is retired: the shade's fluid-cloud cards keep the system's own look, whatever is saved.
        boolean nextShade = false;
        // Retain the page's saved follow request independently of the style master.
        // notificationFollowActive gates its media source through the accent master.
        boolean nextNotification = nextLockEffects && settings != null && settings.getBoolean(NOTIFICATION_FOLLOW, false);
        boolean nextLockCapsule = nextLockEffects && settings != null && settings.getBoolean(LOCK_CAPSULE, false);
        boolean nextGradientStyle = settings != null && !safe && GRADIENT.equals(settings.get(COLOR_STYLE));
        // Only the user's own C17 notification highlight removal may take the glow off a lock-screen media card.
        boolean nextNotificationHighlights = settings != null && !safe && settings.getBoolean(C17HighlightRemoval.ENABLED, false)
                && settings.getBoolean(C17HighlightRemoval.NOTIFICATION_ENABLED, true);
        Object strength = settings == null ? null : settings.get(NOTIFICATION_FOLLOW_OPACITY);
        float nextStrength = strength instanceof Number ? ((Number) strength).floatValue() : DEFAULT_NOTIFICATION_FOLLOW_OPACITY;
        if (!Float.isFinite(nextStrength)) nextStrength = DEFAULT_NOTIFICATION_FOLLOW_OPACITY;
        nextStrength = Math.max(0f, Math.min(100f, nextStrength));
        Object aodStrength = settings == null ? null : settings.get(LockNotificationSettings.AOD_NOTIFICATION_FOLLOW_OPACITY);
        float nextAodStrength = aodStrength instanceof Number ? ((Number) aodStrength).floatValue() : DEFAULT_NOTIFICATION_FOLLOW_OPACITY;
        if (!Float.isFinite(nextAodStrength)) nextAodStrength = DEFAULT_NOTIFICATION_FOLLOW_OPACITY;
        nextAodStrength = Math.max(0f, Math.min(100f, nextAodStrength));
        boolean nextUnify = nextLockEffects && settings != null && settings.getBoolean(LockNotificationSettings.UNIFY_AOD, false);
        if (businessScope.equals(nextBusinessScope)&&lockEffectsEnabled==nextLockEffects && follow == nextFollow && highlights == nextHighlights && background == nextBackground
                && keepMediaHighlight == nextKeep && followForeground == nextForeground && followMedia == nextMedia
                && followOnlyPlaying == nextOnlyPlaying
                && backgroundMode.equals(nextMode) && backgroundColor == nextColor && backgroundOpacity == nextOpacity
                && backgroundGradientRange == nextRange && backgroundGradient.equals(nextGradient)
                && backgroundColorCustomized == nextCustomized && scopeFloat == nextFloat
                && scopeLock == nextLock && scopeShade == nextShade && notificationFollow == nextNotification && notificationFollowOpacity == nextStrength && aodNotificationFollowOpacity == nextAodStrength
                && notificationHighlights == nextNotificationHighlights && lockCapsule == nextLockCapsule
                && gradientStyle == nextGradientStyle && unifyAod == nextUnify) return;
        businessScope=nextBusinessScope;
        lockEffectsEnabled=nextLockEffects;sceneMaterial.lockEffectsEnabled(nextLockEffects);
        lockCapsule = nextLockCapsule; gradientStyle = nextGradientStyle; unifyAod = nextUnify;
        sceneMaterial.unifyAod(nextUnify);
        follow = nextFollow; highlights = nextHighlights; background = nextBackground;
        followMedia = nextMedia; followOnlyPlaying = nextOnlyPlaying;
        keepMediaHighlight = nextKeep; followForeground = nextForeground;
        backgroundMode = nextMode; backgroundColor = nextColor; backgroundOpacity = nextOpacity;
        backgroundGradientRange = nextRange; backgroundGradient = nextGradient;
        backgroundColorCustomized = nextCustomized;
        scopeFloat = nextFloat; scopeLock = nextLock; scopeShade = nextShade; notificationHighlights = nextNotificationHighlights;
        notificationFollow = nextNotification; notificationFollowOpacity = nextStrength; aodNotificationFollowOpacity = nextAodStrength;
        sceneMaterial.sceneState(follow, keyguard, dozing);
        revision++; failureLogged = false; scheduleRefresh();
        if (ModuleDiagnostics.enabled())
            ModuleDiagnostics.fact("fluid", "fluid", "流体云配置", "native_follow", follow, "highlight", highlights,
                    "keep_highlight", keepMediaHighlight, "custom_active", background, "follow_media", followMedia, "only_playing", followOnlyPlaying, "playing", mediaPlaying, "mode", backgroundMode,
                    "opacity", backgroundOpacity, "gradient_range", backgroundGradientRange, "gradient_start", backgroundGradient.start * 100f,
                    "gradient_mid", backgroundGradient.mid * 100f, "gradient_strength", backgroundGradient.strength * 100f, "gradient_opacity", backgroundGradient.opacity * 100f,
                    "scope_float", scopeFloat, "scope_lock", scopeLock, "scope_shade", scopeShade,
                    "notification_media", notificationFollow, "unified", false,
                    "accent_enabled", accent != null && accent.enabled(), "reason", "configure");
    }

    private static float number(Bundle settings, String key, float fallback) {
        Object value = settings == null ? null : settings.get(key);
        return value instanceof Number && Float.isFinite(((Number) value).floatValue()) ? ((Number) value).floatValue() : fallback;
    }

    /** The source is either the system-style colour (native blur and gradient kept, colour and opacity editable)
     * or one solid colour. Retired "follow notification" and "media" choices read as the system-style source. */
    static String validBackgroundMode(Object mode) {
        return "custom".equals(mode) ? "custom" : "native";
    }
    /** Multiply the native alpha once; no replacement of the native blur shape. */
    static int materialFill(int nativeColor, int fill) {
        int alpha = Math.round((nativeColor >>> 24) * ((fill >>> 24) / 255f));
        return alpha << 24 | fill & 0x00ffffff;
    }
    static int materialOpacity(int nativeColor, float opacity) {
        return Math.round((nativeColor >>> 24) * opacity) << 24 | nativeColor & 0x00ffffff;
    }
    /** The floating card: a card that already carries its own media accent keeps it (priority 1);
     * otherwise follow-media (priority 2) wins over the custom base colour (priority 3). */
    private Integer floatingFill(Integer mediaColor, boolean protectedColor) {
        return protectedColor ? null : resolveFill(mediaColor, scopeFloat, false);
    }

    /** Following needs the switch and, when asked, something actually playing. */
    private boolean followActive() { return followMedia && (!followOnlyPlaying || mediaPlaying); }
    /**
     * With "unify style" the ordinary notifications on the lock screen and AOD are styled like the fluid-cloud cards, that is they follow the
     * media colour whenever the cards do (and as strongly); otherwise they have their own follow switch.
     */
    private boolean notificationFollowActive() { return accent != null && accent.enabled() && (unifyAod || notificationFollow) && (!followOnlyPlaying || mediaPlaying); }
    /**
     * The lock/AOD page switches request media following only while the media-accent
     * master permits that source. Their saved style choices remain independent.
     */
    private boolean lockFollowWanted() {
        FluidCloudAccent owner = accent;
        if (!lockEffectsEnabled || owner == null || !owner.enabled()) return false;
        return notificationFollow || unifyAod || lockCapsule || owner.lockOwn(false) || owner.lockOwn(true);
    }
    /** How strongly an ordinary notification takes the card colour: as strongly as the fluid-cloud cards when unified. */
    private float notificationStrength(boolean aod) { return unifyAod ? 100f : aod ? aodNotificationFollowOpacity : notificationFollowOpacity; }
    /** The screen is really in AOD: the AOD look applies from the moment it goes to sleep until the moment it wakes (not through the wake fade). */
    private boolean aodNow() { return keyguard && dozing; }

    /**
     * The media colour a lock-screen or AOD card follows: the live one, else the last one, for as long as the media is still there. The
     * music card is not always bound while the screen is asleep or just waking; a minutes-long AOD must not let the cards go to another
     * colour and come back to the music's when the screen wakes. It ends when the media card is really gone ({@link #mediaDisposed}) or,
     * with "follow only while playing", when playback has stopped.
     */
    private Integer followMediaColour(FluidCloudAccent owner, View body, boolean lockScene) {
        if (owner == null || lockScene && !owner.enabled()) return null;
        Integer live = owner.nativeCurrentMediaColor(body);
        long now = android.os.SystemClock.uptimeMillis();
        if (live != null) {
            if (lockScene) { lastFollowMedia = live; lastFollowMediaAt = now; }
            return live;
        }
        if (lockScene && keyguard && lastFollowMedia != null && (!followOnlyPlaying || mediaPlaying)) return lastFollowMedia;
        return null;
    }

    /** How long "nothing is playing" must last before following lets go. Players report stopped/paused for a moment
     * while the track changes; letting go then made every following card flash to another colour and back. */
    static final long STOP_GRACE_MS = 1500L;
    private final Runnable stopFollowing = () -> applyMediaPlaying(false);

    /** Fed by the playback observer. Only the follow policy depends on it. Playing takes effect at once, stopping after a grace. */
    public void setMediaPlaying(boolean playing) {
        if (released) return;
        main.removeCallbacks(stopFollowing);
        if (playing) { applyMediaPlaying(true); return; }
        if (mediaPlaying) main.postDelayed(stopFollowing, STOP_GRACE_MS);
    }
    private void applyMediaPlaying(boolean playing) {
        if (released || mediaPlaying == playing) return;
        mediaPlaying = playing;
        if ((followMedia || lockFollowWanted()) && followOnlyPlaying) { revision++; scheduleRefresh(); }
    }
    /** Check seam: the grace period has run out. */
    void expireStopGraceForCheck() { main.removeCallbacks(stopFollowing); applyMediaPlaying(false); }
    /** Every running fill fade, finished at once (desktop checks; the fades themselves are checked on their own). */
    void finishFillFadesForCheck() {
        for (BackgroundBinding binding : backgroundSnapshot()) {
            if (binding.fadeTo == null) continue;
            if (binding.fadeStep != null) main.removeCallbacks(binding.fadeStep);
            binding.shownFill = binding.fadeTo; binding.fadeTo = null; binding.fadeStep = null;
            binding.dirty = true; replay(binding, false);
        }
        for (NotificationBinding binding : notificationSnapshot()) {
            if (binding.fadeTo == null) continue;
            View body = binding.fadeBody == null ? null : binding.fadeBody.get();
            if (binding.fadeStep != null) main.removeCallbacks(binding.fadeStep);
            Integer last = binding.fadeTo;
            binding.fadeTo = null; binding.fadeStep = null; binding.shownFill = last; binding.shownOpacity = binding.fadeOpacity;
            if (body != null) sceneMaterial.fill(body, last, binding.fadeOpacity);
        }
    }
    /** True when the playback observer has work to do. */
    public boolean wantsPlaybackState() { return !released && (followMedia || lockFollowWanted()) && followOnlyPlaying; }

    /** One decision shared by every scene. The scope switch gates both the media colour and the base colour. */
    private Integer resolveFill(Integer mediaColor, boolean scope, boolean lockScene) {
        if (!scope) return null;
        FluidCloudAccent owner = accent;
        // The media colour uses the media-accent page's mask and opacity (the lock screen may have its own);
        // only the custom base colour uses the fluid-cloud style page's opacity.
        Integer selected = followActive() && owner != null ? owner.maskedMediaColor(mediaColor, lockScene) : null;
        float factor = selected == null ? 1f : owner.fillFactor(lockScene);
        if (selected == null && background) { selected = customBase(); factor = backgroundOpacity / 100f; }
        if (selected == null) return null;
        return bakeOpacity(selected, factor);
    }

    /** What the lock-screen capsule (the pill at the bottom of the lock screen and of AOD) is tinted with: the same decision
     * order as every card on the lock screen, the music capsule's own media accent first, then following the media colour,
     * then the custom base colour, with the lock-screen opacity and mask. Null leaves the capsule native. The capsule only
     * exists on the lock screen and AOD, so there is no scene to ask about; the lock-screen scope switch gates it. */
    public LockCapsuleTint.Plan lockCapsulePlan(boolean mediaCapsule) {
        FluidCloudAccent owner = accent;
        if (released || owner == null || !lockCapsule) return null;
        boolean aod = aodNow();
        Integer accentColor = mediaCapsule && owner.accentWanted(true, aod) ? owner.mediaAccentColor(owner.nativeCurrentMediaColor(null), true, aod) : null;
        boolean sceneLook = owner.lockOwn(aod) && (!followOnlyPlaying || mediaPlaying);
        Integer mediaColor = owner.enabled() && accentColor == null && scopeLock && (followActive() || sceneLook) ? owner.nativeCurrentMediaColor(null) : null;
        Integer followColor = mediaColor == null ? null : owner.maskedMediaColor(mediaColor, true, aod);
        Integer customColor = accentColor == null && followColor == null && scopeLock && background ? customBase() : null;
        Integer colour = accentColor != null ? accentColor : followColor != null ? followColor : customColor;
        if (colour == null) return null;
        float factor = accentColor != null || followColor != null ? owner.fillFactor(true, aod) : backgroundOpacity / 100f;
        String source = accentColor != null ? "accent" : followColor != null ? "follow_media" : "custom";
        // The same colour as the cards on the lock screen: the user's opacity, lightened by the same white base.
        return new LockCapsuleTint.Plan(lighten(bakeOpacity(colour, Math.max(0f, Math.min(1f, factor))), owner.lockLight(aod)),
                accentColor != null && owner.lockGradient(aod) || gradientStyle && !"custom".equals(source), source, owner.gradientRange());
    }
    /** Called after every refresh batch, so the lock-screen capsules are redrawn when any input of the policy changed. */
    public void setLockCapsuleListener(Runnable listener) { lockCapsuleListener = listener; }

    /** The saved base colour, once it is an explicit choice: a solid colour, or an edited system-style colour. */
    private Integer customBase() {
        return "custom".equals(backgroundMode) || backgroundColorCustomized ? Integer.valueOf(backgroundColor) : null;
    }

    private static int bakeOpacity(int color, float factor) {
        return Math.round((color >>> 24) * factor) << 24 | color & 0x00ffffff;
    }
    private boolean mediaUnder(View card) {
        if (card == null) return false;
        for (MediaBinding binding : mediaSnapshot()) {
            View root = binding.root.get(); if (root != null && binding.attached && under(root, card)) return true;
        }
        return false;
    }

    /** After the native media color event. Accent itself schedules the native
     * initializer; this batch also handles policy changes without polling artwork.
     */
    public void refreshAfterAccent() {
        if (released) return;
        FluidCloudAccent owner = accent;
        if (owner == null || !owner.enabled()) { lastFollowMedia = null; lastFollowMediaAt = 0L; }
        long latest=owner==null?-1:owner.colorRevision();
        if(observedAccentRevision==latest)return;
        observedAccentRevision=latest;
        if(!mediaPolicyActive())return;
        if (background || followActive()) for (BackgroundBinding binding : backgroundSnapshot()) {
            View host = binding.host.get();
            boolean protectedColor = owner != null && host != null && owner.protectsBackground(host);
            if (protectedColor != binding.protectedColor) binding.dirty = true;
            Integer mediaColor = owner == null ? null : owner.nativeCurrentMediaColor(host);
            Integer selected = host == null || !(background || followActive()) ? null : floatingFill(mediaColor, protectedColor);
            if (selected == null ? binding.appliedFill != null : !selected.equals(binding.appliedFill)) binding.dirty = true;
        }
        scheduleRefresh();
    }
    /** Surface package ownership changes independently of the media colour. */
    public void mediaOwnershipChanged() { if(!released&&mediaPolicyActive())scheduleRefresh(); }
    /** Visibility and attachment can change without replacing SurfacePackage or
     * entry. Restrict this observer to registered remote roots with owned glass. */
    public void surfacePresentationChanged(View surface) {
        if(released||!enabled()||!(surface instanceof android.view.SurfaceView))return;
        View root=FluidCloudSurfaceRoots.contentOf(surface);
        if(root==null)return;
        boolean changed=false;
        for(BackgroundBinding binding:backgroundSnapshot()){
            View host=binding.host.get();
            if(FluidCloudSurfaceRoots.contains(root,host)){binding.dirty=true;changed=true;}
        }
        if(changed)scheduleRefresh();
    }
    private boolean mediaPolicyActive() {
        if(enabled()||lockFollowWanted())return true;
        FluidCloudAccent owner=accent;
        // The media accent is always applied once enabled, in every scene.
        if(owner!=null&&owner.enabled())return true;
        // The last lock/AOD look still needs a restoration refresh after its
        // final page switch is turned off, even when no media colour exists.
        for(NotificationBinding binding:notificationSnapshot())if(binding.accentColor||binding.lockLook
                ||binding.colourDecision!=null&&(binding.colourDecision.accent!=null||binding.colourDecision.follow!=null))return true;
        return false;
    }

    public void detach(View host) {
        if (host == null) return;
        sceneMaterial.detach(host);
        foreground.detach(host);
        WeakReference<View> body = notificationRows.remove(host);
        if (body != null && body.get() != null) forgetNotification(body.get());
        forgetNotification(host);
        BackgroundBinding binding = backgrounds.remove(host);
        if (binding != null) {
            FluidCloudAppearance.gradient(host.getBackground(), host.getResources(), DEFAULT_BACKGROUND_GRADIENT_RANGE);
            foreground.detach(binding.card.get()); replay(binding, true);
        }
        if (host.getClass().getName().equals(FluidCloudAccent.CARD))
            for (BackgroundBinding child : backgroundSnapshot()) if (child.card.get() == host) {
                View view = child.host.get();
                if (view != null) {
                    FluidCloudAppearance.gradient(view.getBackground(), view.getResources(), DEFAULT_BACKGROUND_GRADIENT_RANGE);
                    backgrounds.remove(view);
                }
                replay(child, true);
            }
    }

    public void release() {
        if (released) return;
        released = true; main.removeCallbacks(stopFollowing); follow = false; highlights = false; background = false; followMedia=false;
        keepMediaHighlight = false; followForeground = false;
        foreground.release();
        sceneMaterial.release();
        ArrayList<BackgroundBinding> owned = backgroundSnapshot();
        ArrayList<NotificationBinding> bodies = notificationSnapshot();
        Runnable restore = () -> {
            for (BackgroundBinding binding : owned) replay(binding, true);
            FluidCloudAppearance.releaseNativeGradients();
            for (NotificationBinding binding : bodies) {
                View body = binding.host.get(); if (body != null) forgetNotification(body);
            }
            active.remove(); restoring.remove();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) restore.run(); else main.post(restore);
        backgrounds.clear();cardIdentities.clear(); notificationRows.clear(); contracts.clear(); notificationContracts.clear();
        for (MediaBinding binding : mediaSnapshot()) {
            View root=binding.root.get();if(root!=null)root.removeOnAttachStateChangeListener(binding);
            View host = binding.host.get(); if (host != null) host.invalidate();
            View page = binding.page.get(); if(page!=null)page.invalidate();
        }
        mediaSections.clear(); mediaRoots.clear(); mediaLights.clear(); mediaPages.clear(); mediaContracts.clear();
        FluidCloudAccent owner=accent;if(owner!=null)owner.setColorListener(null);
    }

    private void scheduleRefresh() {
        if (released || Boolean.TRUE.equals(replaying.get())) return;
        synchronized (pendingLock) { if (refreshPending) return; refreshPending = true; }
        main.post(() -> {
            Boolean previous=replaying.get();replaying.set(Boolean.TRUE);
            try {
                if (released) return;
                sceneMaterial.policy(follow,keyguard,dozing);
                if(notificationFollow||ordinaryObserved||unifyAod)for(View body:sceneMaterial.ordinaryBodies()) {
                    if(!notifications.containsKey(body)) {
                        registerNotificationBody(body);
                        NotificationBinding binding=notifications.get(body);
                        if(binding!=null){binding.ordinary=true;ordinaryObserved=true;}
                    }
                }
                for (BackgroundBinding binding : backgroundSnapshot()) replay(binding, false);
                ArrayList<MediaBinding> media=mediaSnapshot();
                for(MediaBinding binding:media)binding.nextAccent=null;
                for (NotificationBinding binding : notificationSnapshot()) updateNotification(binding, false);
                Runnable capsules = lockCapsuleListener;
                if (capsules != null) capsules.run();
                for (MediaBinding binding : media) { updateMediaPage(binding); updateMediaLight(binding); }
                foreground.policy(followForeground);
                if (followForeground) for (BackgroundBinding binding : backgroundSnapshot()) {
                    View card = binding.card.get(); if (card != null) foreground.register(card, sceneMaterial.foregroundColor(card));
                }
                if (followForeground) for (NotificationBinding binding : notificationSnapshot()) {
                    for (View view : new View[]{FluidCloudSurfaceRoots.contentOf(binding.card.get()),
                            FluidCloudSurfaceRoots.contentOf(binding.custom.get()), FluidCloudSurfaceRoots.contentOf(binding.mini.get())})
                        if (view != null) foreground.register(view, sceneMaterial.foregroundColor(binding.host.get()));
                }
            } finally {
                if(previous==null)replaying.remove();else replaying.set(previous);
                synchronized(pendingLock){refreshPending=false;}
            }
        });
    }
    private void replay(BackgroundBinding binding, boolean forceNative) {
        View host = binding.host.get(), card = binding.card.get(); Object manager = binding.manager.get();
        if (host == null || card == null || binding.managerRequired && manager == null || cardOf(host) != card) {
            cancelFloatingFade(binding);return;
        }
        boolean selected=businessScope.allows(businessOf(card));
        boolean apply = !forceNative && !released && floatingAllowed(host) && (highlights || selected&&(background || followMedia || follow));
        // A restore skips beginBackground, so its old colour fade cannot finish itself there.
        if(!apply)cancelFloatingFade(binding);
        FluidCloudAccent owner = accent;
        Integer ownMedia = owner == null ? null : owner.nativeBackgroundMediaColor(host);
        Integer mediaColor = owner == null ? null : owner.nativeCurrentMediaColor(host);
        boolean music = ownMedia != null || mediaUnder(card);
        boolean preserveHighlight = highlights && keepMediaHighlight && music;
        if (preserveHighlight != binding.preservedHighlight) binding.dirty = true;
        boolean protectedColor = owner != null && owner.protectsBackground(host);
        Integer nextFill = selected&&(background || followActive()) ? floatingFill(mediaColor, protectedColor) : null;
        if(protectedColor!=binding.protectedColor)binding.dirty=true;
        if(follow&&binding.sceneRevision!=sceneMaterial.revision())binding.dirty=true;
        if (nextFill == null ? binding.appliedFill != null : !nextFill.equals(binding.appliedFill)) binding.dirty = true;
        if (apply && binding.appliedRevision == revision && !binding.dirty) return;
        if (!apply && !binding.applied) return;
        Boolean previous = restoring.get();
        if (!apply) restoring.set(Boolean.TRUE);
        try {
            Object helper = binding.helper.get();
            if (helper == null) helper = binding.access.helperConstructor.newInstance(host.getContext(), 3);
            // Restore before invoking the native helper: forceNative and off
            // refreshes deliberately bypass beginBackground via restoring=true.
            // Old drawables retained by an animation must release their range too.
            FluidCloudAppearance.gradient(host.getBackground(), host.getResources(), DEFAULT_BACKGROUND_GRADIENT_RANGE);
            binding.access.background.invoke(helper, host, manager);
            binding.access.blurAlpha.invoke(host, binding.nativeAlpha);
            host.invalidate();
            if (!apply) { binding.applied = false; binding.dirty = false; binding.appliedRevision = -1; }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { reportFailure(unavailable); }
        finally { if (previous == null) restoring.remove(); else restoring.set(previous); }
    }
    private static final int SHADE = 0, LOCK = 1, AOD = 2, FLOAT = 3;
    private static final String[] SCENES = {"shade", "lock", "aod", "float"};
    private static final String HEADS_UP_LAYOUT = "com.oplus.systemui.notification.headsup.windowframe.HeadsUpLayout";

    /** A custom row shown by the heads-up window is a floating card; the rest follow the keyguard/doze state. */
    private int sceneOf(View body) {
        for (android.view.ViewParent parent = body.getParent(); parent instanceof View; parent = parent.getParent())
            if (HEADS_UP_LAYOUT.equals(parent.getClass().getName())) return FLOAT;
        if (!keyguard) return SHADE;
        return dozing || aodTransition ? AOD : LOCK;
    }
    /** The shade keeps its own colours when its scope is off; on (the default), the accent, the follow and the custom colours reach it. */
    private boolean scopeFor(int scene) { return scene == SHADE ? scopeShade : scene == FLOAT ? scopeFloat : scopeLock; }

    /** Which media section belongs to this fluid-cloud card. Direct containment first; if the remote
     * content cannot be resolved, a single in-notification media section and a single visible card
     * can only belong together. Anything ambiguous stays unowned. */
    private MediaBinding ownMedia(NotificationBinding binding) {
        View card = FluidCloudSurfaceRoots.contentOf(binding.card.get());
        View custom = FluidCloudSurfaceRoots.contentOf(binding.custom.get());
        View mini = FluidCloudSurfaceRoots.contentOf(binding.mini.get());
        MediaBinding sole = null;
        int candidates = 0, attachedSections = 0;
        for (MediaBinding media : mediaSnapshot()) {
            View root = media.root.get();
            if (root == null || !media.attached || media.section.get() == null) continue;
            attachedSections++;
            if (FluidCloudSurfaceRoots.contains(card, root) || FluidCloudSurfaceRoots.contains(custom, root)
                    || FluidCloudSurfaceRoots.contains(mini, root)) { binding.ownReason = "contained"; return media; }
            if (media.notification) { sole = media; candidates++; }
        }
        // Why a card has no media section, for the diagnostic record: no section at all, none inside a notification,
        // several to choose from, a card that is a real view tree without it, or several visible cards sharing one.
        if (attachedSections == 0) { binding.ownReason = "no_media_section"; return null; }
        if (sole == null) { binding.ownReason = "no_notification_media_section"; return null; }
        if (candidates != 1) { binding.ownReason = "several_media_sections"; return null; }
        // Only a remote surface can hide its media root. A real view tree that lacks it belongs to another card.
        if (!(card instanceof android.view.SurfaceView || custom instanceof android.view.SurfaceView || mini instanceof android.view.SurfaceView)) {
            binding.ownReason = card == null && custom == null && mini == null ? "no_card_content" : "content_is_views"; return null;
        }
        int visible = 0;
        for (NotificationBinding other : notificationSnapshot()) {
            if(other.ordinary)continue;
            View view = other.host.get();
            if (view != null && view.isAttachedToWindow() && view.isShown()) visible++;
        }
        binding.ownReason = visible == 1 ? "sole_surface" : "several_visible_cards";
        return visible == 1 ? sole : null;
    }

    private static String hex(Integer color) { return color == null ? "none" : "#" + Integer.toHexString(color); }

    /** The custom base colour's strength: its own opacity, or, with "lock custom" on, the lock-screen opacity in lock and AOD like every other colour there. */
    private float backgroundFactor(FluidCloudAccent owner, boolean lockScene, boolean aod) {
        float lock = owner == null || !lockScene ? -1f : owner.lockCustomOpacity(aod);
        return lock >= 0f ? lock : backgroundOpacity / 100f;
    }

    /**
     * One decision for every card of the fluid cloud and for every ordinary notification that takes part (lock screen and AOD, when the user
     * asked for notifications to follow): the card's own media accent, the current media colour, the custom base colour. The same
     * material, the same layers, the same AOD hold. What makes an ordinary notification different is only whether it takes part and how
     * strongly it takes the colour.
     */
    private void updateNotification(NotificationBinding binding, boolean forceNative) {
        View body = binding.host.get(); NotificationStyler styler = notificationStyler;
        if (body == null || styler == null) return;
        FluidCloudAccent owner = accent;
        boolean live = !forceNative && !released;
        int scene = sceneOf(body);
        boolean lockScene = scene == LOCK || scene == AOD;
        boolean colorLive=live&&(!lockScene||lockEffectsEnabled);
        boolean aod = scene == AOD && dozing;
        FluidCloudScope.Category business=businessOf(binding);
        boolean selected=binding.ordinary||businessScope.allows(business);
        sceneMaterial.businessFollowing(body,selected);
        boolean sceneScope=colorLive&&scopeFor(scene);
        boolean scope = sceneScope&&selected;
        // The page keeps its neutral look when the media-accent master stops colour sourcing.
        boolean sceneLook = colorLive && !binding.ordinary && lockScene && owner != null && owner.lockOwn(aod);
        boolean mediaAllowed = !lockScene || owner != null && owner.enabled();
        MediaBinding own = !binding.ordinary && colorLive && (scene != SHADE || scopeShade) && owner != null && owner.enabled() ? ownMedia(binding) : null;
        Integer accentColor = own == null ? null : owner.mediaAccentColor(own.nativeColor, lockScene, aod);
        boolean mediaWanted=mediaAllowed&&(binding.ordinary?colorLive&&lockScene&&notificationFollowActive():scope&&followActive()||sceneScope&&sceneLook&&(!followOnlyPlaying||mediaPlaying));
        Integer mediaColor = mediaWanted && owner != null && accentColor == null ? followMediaColour(owner, body, lockScene) : null;
        Integer followColor = mediaColor == null || owner == null ? null : owner.maskedMediaColor(mediaColor, lockScene, aod);
        Integer customColor = !binding.ordinary && scope && background && accentColor == null && followColor == null ? customBase() : null;
        Integer colour = accentColor != null ? accentColor : followColor != null ? followColor : customColor;
        // A normal row keeps native shade/headsup and its user's acrylic colour
        // when following ends. Forget the registration, rather than publishing
        // follow=false (that marker would suppress the ordinary colour path).
        // With "same colour in AOD and on the lock screen" an ordinary card stays registered without a colour: the registration is what lets
        // the neutral glass take the black AOD mask's place.
        if(binding.ordinary&&followColor==null&&!(unifyAod&&lockScene&&colorLive)) {
            if(binding.applied||binding.neutral) {
                sceneMaterial.stopOrdinaryFollowing(body);
                try{styler.forget(body);}catch(RuntimeException|LinkageError unavailable){reportFailure(unavailable);}
            }
            binding.applied=false;binding.neutral=false;binding.initialized=false;binding.colourDecision=null;
            binding.appliedColor=binding.normalColor;binding.accentColor=false;return;
        }
        if(binding.ordinary){sceneMaterial.register(body,null,body.getParent());binding.neutral=followColor==null;}
        // Only colours are held with proven AOD optics. User highlight/ownership policy below
        // must still apply immediately. Losing media colour never keeps an older decision.
        NotificationColour held=colorLive&&lockScene&&(accentColor!=null||followColor!=null)
                &&sceneMaterial.holdingMediaComposition(body)?binding.colourDecision:null;
        // The hold keeps the awake colour through the AOD crossfade. If the media colour itself changes while the AOD is settled (the track
        // was switched with the screen off) the card follows the new one, instead of showing the old track's colour until the screen wakes.
        if(held!=null) {
            Integer liveColour=accentColor!=null?accentColor:followColor,heldColour=held.accent!=null?held.accent:held.follow;
            // Only once the AOD has settled: during the crossfade the awake recipe stays untouched, as the native animation needs it.
            if(liveColour!=null&&heldColour!=null&&!heldColour.equals(liveColour)&&aodAtRest())held=null;
        }
        // The held decision was made under the other look (lit lock screen or AOD). Waking goes back to the lit look at once, under the dark
        // start of the wake; going to sleep keeps the lit look through the crossfade and takes the AOD look once the AOD has settled.
        if(held!=null&&held.aod!=aod&&(!aod||aodAtRest()))held=null;
        if(held!=null){accentColor=held.accent;followColor=held.follow;customColor=held.custom;colour=held.colour;}
        // The accent and the colour other cards follow come from the media-accent page and share its opacity
        // (the lock screen may have its own), so every media-coloured card has one material. Only the custom base
        // colour, or an opacity alone, comes from the style page.
        float factor = accentColor != null || followColor != null ? owner.fillFactor(lockScene, aod)
                : background && scope ? backgroundFactor(owner, lockScene, aod) : 1f;
        float mediaFactor=factor,light=owner==null?0f:owner.lockLight(aod);
        if(binding.ordinary)factor*=notificationStrength(aod)/100f;
        if(held!=null){factor=held.factor;mediaFactor=held.mediaFactor;light=held.light;}
        // A coloured lock/AOD fluid card uses the selected spatial gradient even without a media owner.
        // Music, stopwatch and countdown therefore share the same final layer above proven native glass.
        boolean gradient = colour != null && lockScene && (owner != null && owner.lockGradient(aod) || selected&&gradientStyle);
        if(held!=null)gradient=held.gradient;
        Integer fill = gradient || colour == null ? null : bakeOpacity(colour, factor);
        float nativeOpacity = gradient || fill != null ? 1f : factor;
        if(held!=null){fill=held.fill;nativeOpacity=held.nativeOpacity;}
        // On the lock screen and in AOD the same colour is also drawn over the card, as an ordinary notification's is:
        // the platform material mixes it into its own dark glass, far weaker and darker than a flat colour, so the stopwatch card came out
        // grey next to a coloured notification. The music card too: its own gradient is only visible while the card is immersive (the
        // platform's MetaBall is not always effective). The colour is laid over white (lighten), as it looks on the light glass of the
        // unlocked screen, not over the dark wallpaper. Unlocked (shade, floating banner) nothing is added: that is the look to match.
        // The gradient style takes this colour as a layer that fades out down the card (over the range set on the
        // media-accent page); a flat layer would cover the selected gradient.
        Integer shown = lockScene && colour != null
                ? Integer.valueOf(lighten(bakeOpacity(colour, factor), light)) : null;
        if(held!=null)shown=held.shown;
        Integer overlay = gradient ? null : shown, gradientLayer = gradient ? shown : null;
        // Native bright edges follow the selected media hue, retaining their own alpha and
        // geometry. A custom/no-colour card or another scene immediately restores native optics.
        binding.lockLook=live&&sceneLook;
        sceneMaterial.lockLook(body,binding.lockLook);
        sceneMaterial.highlightColor(body,lockScene?(accentColor!=null?accentColor:followColor):null);
        sceneMaterial.colourLayer(body, shown);
        // The native gradient takes the accent at the same opacity, so the lock-screen opacity setting means one thing everywhere.
        sceneMaterial.accent(body, gradient && accentColor != null ? Integer.valueOf(bakeOpacity(accentColor, factor)) : null);
        fadeFill(binding, body, fill, nativeOpacity);
        if (own != null && gradient) { own.nextAccent = accentColor; updateMediaPage(own); }
        // The user's own C17 highlight removal reaches every fluid-cloud card in the scene it covers, whether or
        // not a colour decision does (code98: an un-coloured stopwatch/delivery card or any shade card kept its
        // glow because only colour decisions registered it). A music card is recognised even with the accent
        // page off, so "keep the music highlight" still holds.
        boolean optics = live && (scene == FLOAT ? highlights : notificationHighlights);
        MediaBinding musicCard = own != null ? own : !binding.ordinary && live && keepMediaHighlight && optics ? ownMedia(binding) : null;
        boolean keep = live && keepMediaHighlight && musicCard != null;
        String source = accentColor != null ? "accent" : followColor != null ? "follow_media" : customColor != null ? "custom"
                : factor != 1f ? "opacity" : "none";
        // The C17 optics follow the card while the fluid-cloud policy owns it: the follow-notification
        // material, any colour decision, or the highlight removal itself. The awake lock composition is held
        // through AOD, so entering or leaving it never flips the highlight removal.
        boolean owned = binding.ordinary?followColor!=null:colorLive&&selected&&follow || accentColor != null || followColor != null || customColor != null || factor != 1f || optics;
        // A gradient layer stands in for the flat fill: it needs the card registered, whichever the other policies are.
        // AOD holds what the card already had (it never drops ownership mid-crossfade) but a card that had nothing
        // yet - a direct lock - takes its ownership at once, so waking the screen changes nothing.
        boolean apply = live && (!colorLive || materialAllowed() ? owned : binding.applied || owned);
        String state = SCENES[scene] + (aod ? "*" : "") + "|" + source + "|" + hex(colour) + "|" + factor + "|" + gradient + "|" + keep
                + "|" + (own != null) + "|" + follow + "|" + apply + "|" + sceneMaterial.holdingAod(body) + "|" + optics + "|" + hex(overlay)
                + "|" + hex(gradientLayer) + "|" + sceneLook;
        if (binding.initialized && state.equals(binding.stateKey)) {
            // Lightening does not alter an opaque layer, but a later ordinary
            // card at lower strength still needs the current awake base policy.
            return;
        }
        binding.stateKey = state; binding.initialized = true; binding.applied = apply;
        binding.colourDecision=held!=null?held:new NotificationColour(accentColor,followColor,customColor,colour,
                factor,gradient,fill,nativeOpacity,shown,mediaFactor,light,aod);
        // The decision made for this card, in logcat without the diagnostic switch; bounded for the life of the process.
        if (cardLogs < CARD_LOG_LIMIT) { cardLogs++; Log.i("C17FluidScene", "card " + state + " reason=" + binding.ownReason); }
        // The colour the card shows: the colour decision, else the system normal notification colour.
        binding.appliedColor = colour != null ? colour : binding.normalColor; binding.accentColor = accentColor != null;
        if (ModuleDiagnostics.enabled()) {
            ModuleDiagnostics.fact("fluid", "fluid", "流体云卡片背景决策", "scene", SCENES[scene], "host", "notification",
                    "scope_category",business.id,"scope_allowed",selected,
                    "row", "custom_row", "source", source, "accent_enabled", owner != null && owner.enabled(),
                    "accent_active", accentColor != null, "follow_media", followMedia, "follow_playing", mediaPlaying,
                    "follow_media_active", followColor != null,
                    "custom_active", customColor != null, "native_follow", follow, "scope_float", scopeFloat,
                    "scope_lock", scopeLock, "opacity", factor, "fill_argb", hex(fill),
                    "media_bound", own != null, "media_count", mediaSnapshot().size(), "own_reason", binding.ownReason,
                    "media_argb", hex(own != null ? own.nativeColor : owner == null ? null : owner.nativeCurrentMediaColor(body)),
                    "backend", sceneMaterial.backendName(body), "aod_hold", sceneMaterial.holdingAod(body),
                    "mask", gradient, "keep_highlight", keep, "highlight_removal", optics, "notification_media", notificationFollow, "reason",
                    own == null && owner != null && owner.enabled() && (scene != SHADE || scopeShade) ? "ownership_unresolved" : "ok");
        }
        Boolean previous = replaying.get(); replaying.set(Boolean.TRUE);
        try {
            styler.lockMediaComposition(body,apply&&lockScene&&(accentColor!=null||followColor!=null));
            styler.update(body, apply, keep);
            styler.overlay(body, overlay, overlay == null ? null : overlayGate(binding));
            styler.gradient(body, gradientLayer);
        }
        catch (RuntimeException | LinkageError unavailable) { reportFailure(unavailable); }
        finally { if (previous == null) replaying.remove(); else replaying.set(previous); }
    }

    static final long FILL_FADE_MS = 450L, FILL_FRAME_MS = 32L, FLOATING_FRAME_MS = 48L;

    /** The floating card's colour while one colour replaces another: the plugin's background step is replayed a few times (about one
     * frame in three) with the blended colour, then once with the new one. Nothing is replayed when the colour does not change; a change
     * from or to the system's own colour is direct. */
    private Integer floatingFade(final BackgroundBinding binding, Integer target) {
        long now = android.os.SystemClock.uptimeMillis();
        if (binding.fadeTo != null) {
            if (target != null && target.equals(binding.fadeTo)) {
                if (now - binding.fadeStart < FILL_FADE_MS) {
                    float t = (now - binding.fadeStart) / (float) FILL_FADE_MS;
                    return C17HighlightRemoval.blend(binding.fadeFrom, binding.fadeTo, t * t * (3f - 2f * t));
                }
                binding.fadeTo = null; binding.fadeStep = null; binding.shownFill = target;
                return target;
            }
            // A newer colour during a fade starts from the colour shown now.
            float t = Math.min(1f, (now - binding.fadeStart) / (float) FILL_FADE_MS);
            binding.shownFill = C17HighlightRemoval.blend(binding.fadeFrom, binding.fadeTo, t * t * (3f - 2f * t));
            binding.fadeTo = null;
            if (binding.fadeStep != null) { main.removeCallbacks(binding.fadeStep); binding.fadeStep = null; }
        }
        Integer shown = binding.shownFill;
        if (target == null || shown == null || shown.equals(target) || released) { binding.shownFill = target; return target; }
        binding.fadeFrom = shown; binding.fadeTo = target; binding.fadeStart = now;
        binding.fadeStep = new Runnable() {
            @Override public void run() {
                if (released || binding.fadeStep != this) return;
                binding.dirty = true;
                replay(binding, false);
                if (binding.fadeStep == this && binding.fadeTo != null) main.postDelayed(this, FLOATING_FRAME_MS);
            }
        };
        main.postDelayed(binding.fadeStep, FLOATING_FRAME_MS);
        return shown;
    }
    private void cancelFloatingFade(BackgroundBinding binding) {
        if(binding.fadeStep!=null)main.removeCallbacks(binding.fadeStep);
        binding.fadeStep=null;binding.fadeFrom=null;binding.fadeTo=null;binding.shownFill=null;
    }

    /** The card's fill reaches the platform material as a short cross-fade when one colour replaces another (a new track, following
     * stopping and the custom base colour coming back, a changed setting): about 14 steps over FILL_FADE_MS on the main thread, and
     * nothing at all when the colour does not change. Without a colour on one side (the system's own material) the change is direct:
     * the material's own colour is not known here. */
    private void fadeFill(NotificationBinding binding, View body, Integer fill, float opacity) {
        Integer current = binding.fadeTo != null ? Integer.valueOf(shownFadeColour(binding, android.os.SystemClock.uptimeMillis())) : binding.shownFill;
        boolean same = fill == null ? binding.shownFill == null && binding.fadeTo == null
                : fill.equals(binding.fadeTo != null ? binding.fadeTo : binding.shownFill);
        // Native material/scene events can repeat the current target during its fade. Keep the
        // existing callback and deadline; restarting here turns a short transition into a long tail.
        if (same && binding.fadeTo != null && binding.fadeOpacity == opacity) return;
        if (same && binding.shownOpacity == opacity && binding.fadeTo == null) { sceneMaterial.fill(body, fill, opacity); return; }
        if (binding.fadeStep != null) { main.removeCallbacks(binding.fadeStep); binding.fadeStep = null; }
        if (fill == null || current == null || current.equals(fill) || released) {
            binding.fadeTo = null; binding.shownFill = fill; binding.shownOpacity = opacity;
            sceneMaterial.fill(body, fill, opacity);
            return;
        }
        binding.fadeFrom = current; binding.fadeTo = fill; binding.fadeOpacity = opacity; binding.fadeStart = android.os.SystemClock.uptimeMillis();
        final WeakReference<View> target = new WeakReference<>(body);
        binding.fadeBody = target;
        binding.fadeStep = new Runnable() {
            @Override public void run() {
                View view = target.get();
                if (view == null || released || binding.fadeStep != this || binding.fadeTo == null) return;
                long now = android.os.SystemClock.uptimeMillis();
                if (now - binding.fadeStart >= FILL_FADE_MS) {
                    Integer last = binding.fadeTo;
                    binding.fadeTo = null; binding.fadeStep = null; binding.shownFill = last; binding.shownOpacity = binding.fadeOpacity;
                    sceneMaterial.fill(view, last, binding.fadeOpacity);
                    return;
                }
                sceneMaterial.fill(view, shownFadeColour(binding, now), binding.fadeOpacity);
                main.postDelayed(this, FILL_FRAME_MS);
            }
        };
        binding.shownFill = current;
        binding.fadeStep.run();
    }

    private static int shownFadeColour(NotificationBinding binding, long now) {
        if (binding.fadeTo == null || binding.fadeFrom == null) return binding.shownFill == null ? 0 : binding.shownFill;
        float t = Math.max(0f, Math.min(1f, (now - binding.fadeStart) / (float) FILL_FADE_MS));
        return C17HighlightRemoval.blend(binding.fadeFrom, binding.fadeTo, t * t * (3f - 2f * t));
    }

    /** The question the colour layer asks at each draw: does this card still want its fill drawn over it? One per card, so
     * unchanged decisions allocate nothing. */
    private java.util.function.BooleanSupplier overlayGate(final NotificationBinding binding) {
        if (binding.gate == null) binding.gate = () -> {
            View host = binding.host.get();
            return host != null && !released && sceneMaterial.tintOverlay(host);
        };
        return binding.gate;
    }

    /** The colour lightened the way it looks on the light glass of the unlocked screen, keeping its alpha. Unlocked, a translucent colour
     * sits on a light glass and comes out a light, warm tint; on the lock screen it sits on a dark wallpaper and comes out dark and
     * dull. The hue is taken as seen over a white base of strength {@code light} (0..1): tint t at alpha a over white l is seen as
     * (a * t + (1 - a) * l * 255) / (a + (1 - a) * l). How see-through the result is stays the user's opacity (code100: folding the
     * white into the alpha as well made a lower opacity look whiter and more solid instead of more transparent). An opaque colour,
     * no colour and no base are left alone. */
    static int lighten(int argb, float light) {
        float base = Math.max(0f, Math.min(1f, light));
        int alpha = argb >>> 24;
        if (!(base > 0f) || alpha == 0 || alpha == 255) return argb;
        float a = alpha / 255f, total = a + (1f - a) * base;
        int result = alpha << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float tint = (argb >>> shift) & 0xff;
            result |= Math.max(0, Math.min(255, Math.round((a * tint + (1f - a) * base * 255f) / total))) << shift;
        }
        return result;
    }

    private void updateMediaPage(MediaBinding binding) {
        Integer next=released||!keyguard?null:binding.nextAccent;
        FluidCloudAccent owner=accent;
        if(owner==null||!owner.accentWanted(true,aodNow()))next=null;
        if(next==null?binding.appliedAccent==null:next.equals(binding.appliedAccent))return;
        binding.appliedAccent=next;
        binding.accentPaint=null;
        View page=binding.page.get();if(page!=null)page.invalidate();
    }
    private void updateMediaLight(MediaBinding binding) {
        boolean next=materialAllowed()&&binding.attached
                &&(!binding.notification&&floatingAllowed(binding.root.get())&&highlights&&!keepMediaHighlight
                ||binding.notification&&notificationHighlights&&!keepMediaHighlight);
        if(binding.lightSuppressed==next)return;
        binding.lightSuppressed=next;
        View host=binding.host.get();if(host!=null)host.invalidate();
        View page=binding.page.get();if(page!=null)page.invalidate();
    }
    private static boolean same(Object first,Object second) {return first==null?second==null:first.equals(second);}
    /** The held decision follows this media colour (accent or follow); null decisions never match. */
    static boolean sameMedia(NotificationColour decision,Integer media) {
        if(decision==null||media==null)return false;
        Integer held=decision.accent!=null?decision.accent:decision.follow;
        return held!=null&&held.equals(media);
    }
    private void forgetNotification(View body) {
        NotificationBinding binding = notifications.remove(body);
        if(binding!=null){foreground.detach(FluidCloudSurfaceRoots.contentOf(binding.card.get()));
            foreground.detach(FluidCloudSurfaceRoots.contentOf(binding.custom.get()));foreground.detach(FluidCloudSurfaceRoots.contentOf(binding.mini.get()));}
        sceneMaterial.detach(body);
        NotificationStyler styler = notificationStyler;
        if (binding == null || styler == null) return;
        try { styler.forget(body); }
        catch (RuntimeException | LinkageError unavailable) { reportFailure(unavailable); }
        if(!released)scheduleRefresh();
    }
    private static boolean isNotificationBody(View body) {
        for (Class<?> type = body.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("com.android.systemui.statusbar.notification.row.NotificationBackgroundView")) return true;
        return false;
    }
    private static View cardOf(View host) {
        for (int i = 0; host != null && i < 32; i++) {
            if (host.getClass().getName().equals(FluidCloudAccent.CARD)) return host;
            ViewParent parent = host.getParent(); host = parent instanceof View ? (View) parent : null;
        }
        return null;
    }
    private ArrayList<BackgroundBinding> backgroundSnapshot() {
        synchronized (backgrounds) { return new ArrayList<>(backgrounds.values()); }
    }
    private static boolean under(View host, View root) {
        for (int i = 0; host != null && i < 32; i++) {
            if (host == root) return true;
            ViewParent parent = host.getParent(); host = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
    private ArrayList<NotificationBinding> notificationSnapshot() {
        synchronized (notifications) { return new ArrayList<>(notifications.values()); }
    }
    private ArrayList<MediaBinding> mediaSnapshot() {
        synchronized (mediaSections) { return new ArrayList<>(mediaSections.values()); }
    }
    private void reportFailure(Throwable error) {
        if (failureLogged) return; failureLogged = true;
        Log.w(TAG, "Native Live Alert material unavailable; retaining OEM background", error);
    }

    public final class BackgroundScope implements AutoCloseable {
        private final BackgroundBinding binding;
        private final boolean removeHighlights, removeBackground, protectedColor, keepMaterial;
        private boolean followNotification;
        private float[] headsParams;
        private final Integer fill;
        private final float nativeOpacity;
        private final long appliedRevision;
        private final BackgroundScope previous;
        private boolean completed, changed, closed, resourcesChanged;
        private boolean nativeGradientIndependent;
        private Integer solidFill;
        private Integer gradientTint;
        private float gradientRange = DEFAULT_BACKGROUND_GRADIENT_RANGE;
        private FluidCloudAppearance.GradientShape gradient = FluidCloudAppearance.GradientShape.NATIVE;
        private BackgroundScope(BackgroundBinding binding, boolean highlights, boolean background,
                Integer fill, float nativeOpacity, boolean protectedColor,boolean selected, long revision, BackgroundScope previous) {
            this.binding = binding; removeHighlights = highlights; removeBackground = background;
            this.fill = fill;
            this.nativeOpacity = nativeOpacity;
            this.protectedColor = protectedColor; appliedRevision = revision; this.previous = previous;
            keepMaterial = protectedColor || selected&&(FluidCloudStyle.this.background || followMedia);
        }
        public void completed() {
            // Work only on the verified expanded-card background after native
            // initialization; its light-effect shader is an OEM Drawable, while
            // its no-blur resource fallback is a shape.
            View host = binding.host.get();
            if (host != null && (removeHighlights || removeBackground || fill != null || nativeOpacity != 1f
                    || !gradient.isNative() || gradientTint != null)) {
                Drawable original = host.getBackground();
                Drawable derived = resourcesChanged ? FluidCloudFallback.derive(original, host.getResources(),
                        fill, removeHighlights, removeBackground)
                        : FluidCloudFallback.material(original, host.getResources(),fill, removeHighlights, removeBackground);
                // Static shape resources bypass getColor/BlurParam entirely.
                // Substituted resource paths must not multiply alpha again.
                if (nativeOpacity != 1f && !resourcesChanged)
                    derived = FluidCloudFallback.opacity(derived, host.getResources(), nativeOpacity);
                if (solidFill != null)
                    derived = FluidCloudAppearance.solid(derived, host.getResources(), solidFill);
                else if (!gradient.isNative() || gradientTint != null)
                    derived = FluidCloudAppearance.gradient(derived, host.getResources(), gradient, gradientTint);
                // The actual OEM gradient is adjusted in place: its owning
                // LayerDrawable and hardware blur must retain their identities.
                if (FluidCloudAppearance.nativeGradientOwned(derived)) changed = true;
                if (derived != original) { host.setBackground(derived); changed = true; }
            }
            // Back to the native look: an OEM gradient still holding our solid colour or range is restored.
            if (host != null && solidFill == null && gradientTint == null && gradient.isNative()
                    && FluidCloudAppearance.nativeGradientOwned(host.getBackground())) {
                FluidCloudAppearance.gradient(host.getBackground(), host.getResources(), DEFAULT_BACKGROUND_GRADIENT_RANGE);
                changed = true;
            }
            if (host != null) backgroundResult = backgroundDescription(host.getBackground())
                    + ",range=" + gradientRange + ",owned=" + FluidCloudAppearance.nativeGradientOwned(host.getBackground());
            completed = true;
        }
        @Override public void close() {
            if (closed) return; closed = true;
            if (completed) {
                binding.applied = changed; binding.dirty = false; binding.appliedRevision = appliedRevision;
                binding.protectedColor = protectedColor;
                binding.appliedFill = fill;
                binding.preservedHighlight = highlights && keepMediaHighlight && !removeHighlights;
                binding.sceneRevision=sceneMaterial.revision();
            } else if (changed) { binding.applied = true; binding.dirty = true; binding.appliedRevision = -1; }
            if (previous == null) active.remove(); else active.set(previous);
        }
    }
    private static final class BackgroundBinding {
        final WeakReference<View> host, card; final Hooks access;
        WeakReference<Object> helper = new WeakReference<>(null), manager = new WeakReference<>(null);
        float nativeAlpha = 1f; boolean applied, dirty, protectedColor, managerRequired; long appliedRevision = -1;
        float[] nativeParams; Object derivedParams; int paramPolicy;
        float[] headsParams;long sceneRevision=-1;
        String diagnostic;int diagnosticCount;
        String appearanceDiagnostic;int appearanceDiagnosticCount;
        float paramOpacity = 1f; boolean paramResourceAlpha;
        Integer paramFill, appliedFill; boolean preservedHighlight;
        /** The floating card's colour as shown, and a fade toward the next one (code100). */
        Integer shownFill, fadeFrom, fadeTo; long fadeStart; Runnable fadeStep;
        BackgroundBinding(View host, View card, Hooks access) {
            this.host = new WeakReference<>(host); this.card = new WeakReference<>(card); this.access = access;
        }
    }
    /** Event-built colour decision, shared unchanged with a confirmed held AOD recipe. */
    private static final class NotificationColour {
        final Integer accent,follow,custom,colour,fill,shown;final float factor,nativeOpacity,mediaFactor,light;final boolean gradient,aod;
        NotificationColour(Integer accent,Integer follow,Integer custom,Integer colour,float factor,boolean gradient,
                Integer fill,float nativeOpacity,Integer shown,float mediaFactor,float light,boolean aod){
            this.aod=aod;
            this.accent=accent;this.follow=follow;this.custom=custom;this.colour=colour;this.factor=factor;
            this.gradient=gradient;this.fill=fill;this.nativeOpacity=nativeOpacity;this.shown=shown;
            this.mediaFactor=mediaFactor;this.light=light;
        }
    }
    private static final class NotificationBinding {
        FluidCloudIdentity.Identity identity=FluidCloudIdentity.fromEntry(null);
        final WeakReference<View> host; boolean initialized, applied, accentColor, ordinary, neutral, lockLook; Integer normalColor, appliedColor;
        NotificationColour colourDecision;
        /** The fill the platform material shows now, and a fade toward the next one (code100). */
        Integer shownFill; float shownOpacity = 1f;
        Integer fadeFrom, fadeTo; float fadeOpacity = 1f; long fadeStart; Runnable fadeStep; WeakReference<View> fadeBody;
        String stateKey;
        java.util.function.BooleanSupplier gate;
        String diagnostic;int diagnosticCount;
        /** The last answer ownMedia gave and why; only ever read by the diagnostic fact. */
        String ownReason = "unresolved";
        WeakReference<View> card = new WeakReference<>(null);
        WeakReference<View> custom = new WeakReference<>(null);
        WeakReference<View> mini = new WeakReference<>(null);
        WeakReference<Object> entry = new WeakReference<>(null);
        NotificationBinding(View host) { this.host = new WeakReference<>(host); }
    }
    private final class MediaBinding implements View.OnAttachStateChangeListener {
        final WeakReference<Object> section;
        final WeakReference<View> root;
        final WeakReference<View> host; final WeakReference<Drawable> light;
        final MediaHooks access; final boolean notification;
        WeakReference<View> page=new WeakReference<>(null);
        WeakReference<Drawable> pageBackground=new WeakReference<>(null);
        WeakReference<Drawable> pageLight=new WeakReference<>(null);
        Integer nativeColor,nextAccent,appliedAccent; Paint accentPaint;
        boolean attached,lightSuppressed;
        MediaBinding(Object section, View root, View host, Drawable light, MediaHooks access, boolean notification) {
            this.section = new WeakReference<>(section);
            this.root = new WeakReference<>(root);attached=root!=null&&root.isAttachedToWindow();
            this.host = new WeakReference<>(host); this.light = new WeakReference<>(light);
            this.access = access; this.notification = notification;
        }
        @Override public void onViewAttachedToWindow(View view) {
            if(released||mediaSections.get(section.get())!=this)return;
            attached=true;Object owner=section.get();if(owner!=null)mediaChanged(owner);
            scheduleRefresh();
        }
        @Override public void onViewDetachedFromWindow(View view) {
            if(released||mediaSections.get(section.get())!=this)return;
            attached=false;scheduleRefresh();
        }
    }

    public static final class MediaHooks {
        public final Class<?> sectionType;
        public final Constructor<?> constructor;
        public final Method primary, dispose, draw, pageDraw;
        private final Class<?> pageType;
        private final Field root,mediaView, light, model, notification, flow,pageOwner,pageView,pageBackground,pageLight;
        private final Method value;
        private MediaHooks(ClassLoader loader) throws ReflectiveOperationException {
            sectionType = loader.loadClass(FluidCloudAccent.MEDIA_SECTION);
            Class<?> modelType = loader.loadClass("com.oplus.systemui.plugins.shared.template.section.media.K0");
            Class<?> mediaType = loader.loadClass("com.oplus.systemui.plugins.shared.template.component.media.view.MediaBackGroundView");
            Class<?> lightType = loader.loadClass("com.oplus.systemui.plugins.shared.template.component.light.f");
            Class<?> streamType = loader.loadClass("com.oplus.systemui.plugins.r8.jj");
            pageType=loader.loadClass("com.oplus.systemui.plugins.shared.template.page.view.PageBackgroundView");
            Class<?> pageDrawable=loader.loadClass("com.oplus.systemui.plugins.shared.template.page.view.a");
            if(!View.class.isAssignableFrom(pageType)||!Drawable.class.isAssignableFrom(pageDrawable))
                throw new NoSuchFieldException("Live Alert immersive material changed");
            Class<?> pageOwnerType=loader.loadClass("com.oplus.systemui.plugins.shared.template.page.entity.t");
            pageOwner=sectionType.getDeclaredField("m");pageView=pageOwnerType.getDeclaredField("u");
            pageBackground=pageType.getDeclaredField("b");
            pageLight=pageDrawable.getDeclaredField("c");
            if(pageOwner.getType()!=pageOwnerType||pageView.getType()!=pageType||pageBackground.getType()!=pageDrawable)
                throw new NoSuchFieldException("Live Alert immersive owner changed");
            if(pageLight.getType()!=lightType)throw new NoSuchFieldException("Live Alert page light changed");
            pageDraw=pageDrawable.getDeclaredMethod("draw",Canvas.class);pageDraw.setAccessible(true);
            pageOwner.setAccessible(true);pageView.setAccessible(true);pageBackground.setAccessible(true);
            pageLight.setAccessible(true);
            if (!View.class.isAssignableFrom(mediaType) || !Drawable.class.isAssignableFrom(lightType))
                throw new NoSuchFieldException("Live Alert media background changed");
            mediaView = sectionType.getDeclaredField("q"); light = mediaType.getDeclaredField("F");
            root=sectionType.getDeclaredField("l");
            model = sectionType.getDeclaredField("k"); notification = modelType.getDeclaredField("U");
            flow = streamType.getDeclaredField("b");
            if (mediaView.getType() != mediaType || light.getType() != lightType || model.getType() != modelType
                    || notification.getType() != streamType || root.getType()!=android.view.ViewGroup.class) throw new NoSuchFieldException("Live Alert media owner changed");
            value = loader.loadClass("kotlinx.coroutines.flow.p").getDeclaredMethod("getValue");
            constructor = sectionType.getDeclaredConstructor(android.content.Context.class, modelType,
                    android.view.ViewGroup.class, loader.loadClass("com.oplus.systemui.plugins.shared.template.page.entity.t"));
            primary = sectionType.getDeclaredMethod("r", sectionType); dispose = sectionType.getDeclaredMethod("dispose");
            draw = lightType.getDeclaredMethod("draw", android.graphics.Canvas.class);
            for (Field field : new Field[]{root,mediaView, light, model, notification, flow}) field.setAccessible(true);
            value.setAccessible(true); constructor.setAccessible(true); primary.setAccessible(true); dispose.setAccessible(true); draw.setAccessible(true);
        }
    }
    /** Notification body differs from the lockscreen immersive media backdrop.
     * A changed native signature makes only this notification path unsupported.
     */
    public static final class NotificationHooks {
        public final Class<?> rowType, bodyType;
        public final Method backgroundGetter, backgroundSetter, inflate, attach, updateResource, setEntry, setSeedlingView;
        private final Method entryGetter, cardGetter, customGetter, miniGetter;
        private NotificationHooks(ClassLoader loader) throws ReflectiveOperationException {
            rowType = loader.loadClass("com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow");
            bodyType = loader.loadClass("com.android.systemui.statusbar.notification.row.NotificationBackgroundView");
            if (!View.class.isAssignableFrom(rowType) || !View.class.isAssignableFrom(bodyType))
                throw new NoSuchMethodException("Live Alert notification body changed");
            backgroundGetter = rowType.getDeclaredMethod("getMBackgroundNormal");
            if (backgroundGetter.getReturnType() != bodyType) throw new NoSuchMethodException("Live Alert notification body getter changed");
            Field nativeBody = rowType.getDeclaredField("mBackgroundNormal");
            if (nativeBody.getType() != bodyType) throw new NoSuchFieldException("Live Alert notification body field changed");
            backgroundSetter = rowType.getDeclaredMethod("setMBackgroundNormal", bodyType);
            inflate = rowType.getDeclaredMethod("onFinishInflate"); attach = rowType.getDeclaredMethod("onAttachedToWindow");
            updateResource = rowType.getDeclaredMethod("updateResource", Boolean.TYPE);
            setEntry = rowType.getDeclaredMethod("setEntry", loader.loadClass("com.android.systemui.plugins.custom.CustomRowEntry"));
            setSeedlingView=rowType.getDeclaredMethod("setNewSeedlingView",View.class);setSeedlingView.setAccessible(true);
            entryGetter = rowType.getDeclaredMethod("getEntry");
            cardGetter = loader.loadClass("com.android.systemui.plugins.custom.CustomRowEntry").getDeclaredMethod("getImmersiveCardView");
            customGetter=loader.loadClass("com.android.systemui.plugins.custom.CustomRowEntry").getDeclaredMethod("getCustomView");
            miniGetter=loader.loadClass("com.android.systemui.plugins.custom.CustomRowEntry").getDeclaredMethod("getMiniCardView");miniGetter.setAccessible(true);
            customGetter.setAccessible(true);
            entryGetter.setAccessible(true); cardGetter.setAccessible(true);
            backgroundGetter.setAccessible(true); backgroundSetter.setAccessible(true);
            inflate.setAccessible(true); attach.setAccessible(true); updateResource.setAccessible(true); setEntry.setAccessible(true);
        }
    }
    /** Exact desktop native contract; missing fingerprints keep the OEM path. */
    public static final class Hooks {
        public final Class<?> managerType, paramType, backgroundType;
        public final Method background, setBlurParams, blurAlpha,cardBind;
        private final Method toArray, fromArray;
        private final Constructor<?> paramConstructor, helperConstructor;
        private Hooks(ClassLoader loader) throws ReflectiveOperationException {
            managerType = loader.loadClass(MANAGER); paramType = loader.loadClass(PARAM);
            backgroundType = loader.loadClass(FluidCloudAccent.BACKGROUND);
            Class<?> helper = loader.loadClass(FluidCloudAccent.BACKGROUND_HELPER);
            cardBind=loader.loadClass(FluidCloudAccent.CARD).getDeclaredMethod("h",loader.loadClass("com.oplus.systemui.plugins.seedling.card.ui.model.o"));
            if (!View.class.isAssignableFrom(backgroundType)) throw new NoSuchMethodException("Live Alert background changed");
            background = helper.getDeclaredMethod("b", View.class, managerType);
            helperConstructor = helper.getDeclaredConstructor(android.content.Context.class, Integer.TYPE);
            blurAlpha = backgroundType.getDeclaredMethod("setBlurRadiusFollowAlpha", Float.TYPE);
            setBlurParams = managerType.getDeclaredMethod("setBlurParams", paramType);
            toArray = paramType.getDeclaredMethod("toFloatArray"); fromArray = paramType.getDeclaredMethod("fromFloatArray", float[].class);
            paramConstructor = paramType.getDeclaredConstructor();
            if (toArray.getReturnType() != float[].class || fromArray.getReturnType() != Void.TYPE)
                throw new NoSuchMethodException("Live Alert blur parameter contract changed");
            Object empty = paramConstructor.newInstance();
            if (!validParams((float[]) toArray.invoke(empty))) throw new NoSuchMethodException("Live Alert blur parameter size changed");
            background.setAccessible(true); helperConstructor.setAccessible(true); blurAlpha.setAccessible(true);cardBind.setAccessible(true);
            setBlurParams.setAccessible(true); toArray.setAccessible(true); fromArray.setAccessible(true); paramConstructor.setAccessible(true);
        }
    }
}
