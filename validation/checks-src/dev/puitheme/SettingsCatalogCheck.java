package dev.puitheme;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/** Every visible parameter is backed by storage and reachable through a scene destination. */
public final class SettingsCatalogCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Catalog "+checks+": "+expected+" != "+actual);
    }
    private static void equal(Object expected,Object actual,String reason) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Catalog "+checks+" "+reason+": "+expected+" != "+actual);
    }
    /** The camera-area fluid cloud page is long: three tabs, each holding whole sections, in a readable order. */
    private static void capsulePageIsOrganised() {
        SettingsCatalog.Group capsule=SettingsCatalog.group("fluid_cloud_capsule");
        java.util.List<SettingsCatalog.DetailPage> pages=SettingsCatalog.detailPages(capsule);
        equal(3,pages.size(),"capsule tabs");
        equal("fill",pages.get(0).id);equal("底色",pages.get(0).title);
        equal("border",pages.get(1).id);equal("边框",pages.get(1).title);
        equal("content",pages.get(2).id);equal("形状与内容",pages.get(2).title);
        java.util.List<String> order=new java.util.ArrayList<>();
        java.util.Set<String> seen=new java.util.HashSet<>();
        for(SettingsCatalog.DetailPage page:pages) {
            String last="";
            for(SettingsCatalog.Item item:page.items) {
                equal(true,seen.add(item.key),"each setting sits on exactly one tab "+item.key);
                if(!item.section.equals(last)){order.add(page.id+":"+item.section);last=item.section;}
            }
        }
        // A section is never split across tabs or interleaved with another one, and the retired catch-all names are gone.
        equal(java.util.Arrays.asList("fill:底色","fill:渐变","fill:模糊","border:边框","border:跑马灯","border:向内渐隐",
                "content:形状","content:专辑封面","content:出现回弹","content:实时频谱"),order,"section order on the capsule tabs");
        equal("向内渐隐",SettingsCatalog.item(FluidCloudCapsule.GLOW_ENABLED).section);
        equal("向内渐隐",SettingsCatalog.item(FluidCloudCapsule.GLOW_DEPTH).section);
        equal("跑马灯",SettingsCatalog.item(FluidCloudCapsule.MARQUEE_REVERSE).section);
        // Within the marquee the switch and the preset come first and the glow no longer sits between its colours and its speed.
        java.util.List<String> marquee=new java.util.ArrayList<>();
        for(SettingsCatalog.Item item:pages.get(1).items)if("跑马灯".equals(item.section))marquee.add(item.key);
        equal(FluidCloudCapsule.MARQUEE_ENABLED,marquee.get(0));equal(FluidCloudCapsule.MARQUEE_PRESET,marquee.get(1));
        equal(FluidCloudCapsule.MARQUEE_REVERSE,marquee.get(marquee.size()-1));
        // The page text matches what the page now holds.
        equal(true,capsule.description.contains("形状与内容")&&!capsule.description.contains("只改胶囊的底色、边框和圆角"));
    }
    private static void fluidCloudSettings() {
        capsulePageIsOrganised();
        SettingsCatalog.Group style=SettingsCatalog.group("fluid_cloud_style");
        equal(SettingsCatalog.OTHER,style.category);equal(false,style.independentSwitches);
        equal(FluidCloudStyle.MASTER,style.masterKey);equal(true,SettingsCatalog.item(style.masterKey).master);
        equal(27,style.items.size());equal(3,SettingsCatalog.detailPages(style).size());
        equal(null,SettingsCatalog.item(FluidCloudStyle.FOLLOW_FOREGROUND),"retired foreground switch removed from editor");
        equal(true,SettingsCatalog.hiddenKeys().contains(FluidCloudStyle.FOLLOW_FOREGROUND),"retired foreground excluded from uncovered settings");
        equal(false,SettingsCatalog.isVisible(FluidCloudStyle.FOLLOW_FOREGROUND),"retired foreground has no entry");
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(FluidCloudStyle.FOLLOW_FOREGROUND),"historical Boolean type retained");
        equal(null,SettingsCatalog.item(FluidCloudStyle.DESKTOP_HIGHLIGHTS),"desktop highlight removal belongs to the C17 feature now");
        equal(true,SettingsCatalog.hiddenKeys().contains(FluidCloudStyle.DESKTOP_HIGHLIGHTS),"retired desktop highlight switch excluded from uncovered settings");
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(FluidCloudStyle.DESKTOP_HIGHLIGHTS),"historical Boolean type retained for backups");
        equal(1,SettingsCatalog.detailPages(style).get(0).items.size());
        equal("通知类",SettingsCatalog.detailPages(style).get(0).title);
        equal("流体云外观",SettingsCatalog.detailPages(style).get(1).title);
        equal(12,SettingsCatalog.detailPages(style).get(1).items.size(),"the five native gradient numbers belong to the appearance page");
        equal("通知类跟随系统样式",SettingsCatalog.item(FluidCloudStyle.FOLLOW_NOTIFICATION).title);
        equal("自定义底色",SettingsCatalog.item(FluidCloudStyle.DESKTOP_BACKGROUND).title);
        equal("自定义颜色",SettingsCatalog.item(FluidCloudStyle.BACKGROUND_COLOR).title);
        equal(true,SettingsCatalog.item(FluidCloudStyle.DESKTOP_BACKGROUND).description.contains("保留原生模糊"));
        equal(null,SettingsCatalog.item(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED),"edit provenance is not a switch");
        equal(true,SettingsCatalog.hiddenKeys().contains(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED),"edit provenance hidden");
        equal(false,StatusBarSettings.BOOLEAN_DEFAULTS.get(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED),"native defaults remain untouched");
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED),"edit provenance portable");
        SettingsCatalog.Item source=SettingsCatalog.item(FluidCloudStyle.BACKGROUND_MODE);
        equal(2,source.values.length,"the colour source has exactly two choices");
        equal("native",source.values[0]);equal("custom",source.values[1]);
        equal("原生渐变",source.labels[0]);equal("自定义纯色背景",source.labels[1]);
        equal(true,source.description.contains("保留原生模糊"));equal(true,source.description.contains("纯色"));
        Map<String,Object> values=new HashMap<>();
        // code100: the notification-shade scope is retired: no switch, off, and old configs keep their Boolean type to import.
        equal(true,SettingsCatalog.hiddenKeys().contains(FluidCloudStyle.SCOPE_SHADE),"the shade scope has no switch any more");
        equal(false,StatusBarSettings.BOOLEAN_DEFAULTS.get(FluidCloudStyle.SCOPE_SHADE),"the shade keeps its own colours");
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(FluidCloudStyle.SCOPE_SHADE),"Boolean type retained");
        for(String key:new String[]{FluidCloudStyle.FOLLOW_NOTIFICATION,FluidCloudStyle.DESKTOP_BACKGROUND}) {
            SettingsCatalog.Item item=SettingsCatalog.item(key);
            equal(style.id,item.groupId);equal(false,item.master);equal(false,item.defaultValue);
            equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(key));
            equal("请先开启流体云样式",SettingsCatalog.unavailableReason(key,values),"the style page master governs its switches");
        }
        for(String key:new String[]{FluidCloudStyle.SCOPE_FLOAT,FluidCloudStyle.SCOPE_LOCK}) {
            SettingsCatalog.Item item=SettingsCatalog.item(key);
            equal(style.id,item.groupId);equal(false,item.master);equal(true,item.defaultValue,"floating and lock-screen scopes default on");
            equal("作用场景",item.section);
        }
        equal("悬浮展开",SettingsCatalog.item(FluidCloudStyle.SCOPE_FLOAT).title);
        equal("锁屏与 AOD",SettingsCatalog.item(FluidCloudStyle.SCOPE_LOCK).title);
        SettingsCatalog.Group accent=SettingsCatalog.group("fluid_cloud_accent");
        equal(false,accent.independentSwitches);equal(FluidCloudAccent.ENABLED,accent.masterKey);
        equal(null,SettingsCatalog.item(FluidCloudAccent.LOCKSCREEN_ENABLED),"the accent covers every scene; no separate lock-screen switch");
        equal(true,SettingsCatalog.hiddenKeys().contains(FluidCloudAccent.LOCKSCREEN_ENABLED),"retired lock-screen switch excluded from uncovered settings");
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(FluidCloudAccent.LOCKSCREEN_ENABLED),"historical Boolean type retained");
        equal(true,style.description.contains("锁屏")&&style.description.contains("AOD"));
        equal(true,SettingsCatalog.item(FluidCloudStyle.FOLLOW_NOTIFICATION).description.contains("悬浮通知材质"));
        equal(true,accent.description.contains("总开关")&&accent.description.contains("锁屏")&&accent.description.contains("AOD")
                &&accent.description.contains("停止")&&accent.description.contains("保留"),"media master description covers lock/AOD colour revocation and saved appearance");
        equal(true,SettingsCatalog.conflictDescription(accent.id,accent.category).contains("优先级 1")
                &&SettingsCatalog.conflictDescription(accent.id,accent.category).contains("关闭总开关")
                &&SettingsCatalog.conflictDescription(accent.id,accent.category).contains("媒体取色一起停止"));
        equal(null,SettingsCatalog.group("fluid_cloud_media_follow"),"following lives on the media-accent page now");
        // Where the colour comes from stays on the media-accent page; how lock screen and AOD show it has its own pages (lock screen -> fluid cloud and notifications).
        for(String key:new String[]{FluidCloudStyle.FOLLOW_MEDIA,FluidCloudStyle.FOLLOW_ONLY_PLAYING,FluidCloudAccent.OPACITY,
                FluidCloudAccent.MASK_ENABLED,FluidCloudAccent.MASK_COLOR,FluidCloudAccent.MASK_OPACITY})
            equal("fluid_cloud_accent",SettingsCatalog.item(key).groupId,"item on the media-accent page: "+key);
        for(String key:new String[]{FluidCloudAccent.LOCK_GRADIENT,FluidCloudAccent.GRADIENT_RANGE,FluidCloudStyle.COLOR_STYLE,FluidCloudStyle.LOCK_CAPSULE,
                FluidCloudAccent.LOCK_CUSTOM,FluidCloudAccent.LOCK_OPACITY,FluidCloudAccent.LOCK_MASK_COLOR,FluidCloudAccent.LOCK_MASK_OPACITY,
                FluidCloudAccent.LOCK_LIGHT,LockNotificationSettings.UNIFY_AOD,FluidCloudStyle.NOTIFICATION_FOLLOW,FluidCloudStyle.NOTIFICATION_FOLLOW_OPACITY}) {
            equal("lock_awake",SettingsCatalog.item(key).groupId,"item on the lit lock-screen page: "+key);
            equal(SettingsCatalog.LOCK_SCREEN,SettingsCatalog.group("lock_awake").category);
        }
        for(String key:new String[]{FluidCloudAccent.AOD_GRADIENT,FluidCloudAccent.AOD_CUSTOM,FluidCloudAccent.AOD_OPACITY,FluidCloudAccent.AOD_MASK_COLOR,
                FluidCloudAccent.AOD_MASK_OPACITY,FluidCloudAccent.AOD_LIGHT,LockNotificationSettings.AOD_NOTIFICATION_FOLLOW_OPACITY}) {
            equal("lock_aod",SettingsCatalog.item(key).groupId,"item on the AOD page: "+key);
            equal(SettingsCatalog.LOCK_SCREEN,SettingsCatalog.group("lock_aod").category);
        }
        equal(false,SettingsCatalog.group("lock_awake").items.isEmpty());equal(false,SettingsCatalog.group("lock_aod").items.isEmpty());
        // "Unify style" takes the notification switch and strengths over; AOD's own look and its flat tint stay editable.
        {
            Map<String,Object> unify=new HashMap<>();unify.put(LockNotificationSettings.UNIFY_AOD,true);unify.put(FluidCloudStyle.NOTIFICATION_FOLLOW,true);
            for(String key:new String[]{LockNotificationSettings.AOD_NOTIFICATION_FOLLOW_OPACITY,FluidCloudStyle.NOTIFICATION_FOLLOW,FluidCloudStyle.NOTIFICATION_FOLLOW_OPACITY})
                equal(true,SettingsCatalog.unavailableReason(key,unify).contains("统一样式"),"unified: "+key+" is off");
            for(String key:new String[]{FluidCloudAccent.AOD_GRADIENT,FluidCloudAccent.AOD_CUSTOM,FluidCloudAccent.AOD_LIGHT,FluidCloudAccent.AOD_OPACITY,
                    FluidCloudAccent.LOCK_LIGHT,FluidCloudAccent.LOCK_CUSTOM})
                equal(false,SettingsCatalog.unavailableReason(key,unify).contains("统一样式"),"unified: "+key+" stays editable");
            unify.put(LockNotificationSettings.UNIFY_AOD,false);
            equal(false,SettingsCatalog.unavailableReason(LockNotificationSettings.AOD_NOTIFICATION_FOLLOW_OPACITY,unify).contains("统一样式"),"not unified: the AOD strength is free");
        }
        // One entrance on the lock-screen category, and no second copy of those pages under "other".
        boolean hub=false;
        for(SettingsCatalog.NavigationSection section:SettingsCatalog.navigationSections(SettingsCatalog.LOCK_SCREEN))
            if("亮屏与 AOD".equals(section.title)){hub=true;equal(2,section.groups.size());
                equal("lock_awake",section.groups.get(0).id);equal("lock_aod",section.groups.get(1).id);}
        equal(true,hub,"the lock screen has one entrance, split into the lit screen and AOD");
        for(SettingsCatalog.NavigationSection section:SettingsCatalog.navigationSections(SettingsCatalog.OTHER))
            for(SettingsCatalog.Group group:section.groups)
                equal(false,group.id.startsWith("lock_"),"the lock-screen pages are not listed under other");
        equal(true,StatusBarSettings.BOOLEAN_DEFAULTS.get(FluidCloudStyle.FOLLOW_ONLY_PLAYING),"following waits for playback by default");
        equal(false,StatusBarSettings.BOOLEAN_DEFAULTS.get(FluidCloudAccent.LOCK_CUSTOM),"lock-screen custom is opt-in");
        equal(100f,SettingsCatalog.item(FluidCloudAccent.OPACITY).defaultValue);
        equal(StatusBarSettings.alphaKey(FluidCloudAccent.LOCK_MASK_COLOR),SettingsCatalog.item(FluidCloudAccent.LOCK_MASK_COLOR).alphaKey);
        equal(true,SettingsCatalog.conflictDescription(style.id,style.category).contains("颜色优先级"));
        SettingsCatalog.Item opacity=SettingsCatalog.item(FluidCloudAccent.MASK_OPACITY);
        equal(0f,opacity.min);equal(100f,opacity.max);equal(20f,opacity.defaultValue);
        equal(SettingsCatalog.NUMERIC,opacity.type);equal("%",opacity.unit);
        SettingsCatalog.Item color=SettingsCatalog.item(FluidCloudAccent.MASK_COLOR);
        equal(SettingsCatalog.COLOR,color.type);equal(0xff000000,color.defaultValue);
        equal(StatusBarSettings.alphaKey(color.key),color.alphaKey);
        String pageMaster="请先开启流体云媒体强调色";
        String followOnly="请先开启跟随媒体强调色，或锁屏 / AOD 页里的通知跟随、统一样式、渐变样式、自定义";
        String[] children={FluidCloudAccent.MASK_ENABLED,FluidCloudAccent.MASK_COLOR,FluidCloudAccent.MASK_OPACITY,FluidCloudAccent.OPACITY,
                FluidCloudStyle.FOLLOW_MEDIA};
        for(String key:children)equal(pageMaster,SettingsCatalog.unavailableReason(key,values),"everything on the page is greyed while its master is off: "+key);
        // The lock screen and AOD pages are set on their own: neither the media-accent master nor the style master greys them.
        String[] lockOwn={FluidCloudStyle.NOTIFICATION_FOLLOW,FluidCloudAccent.LOCK_CUSTOM,FluidCloudAccent.LOCK_LIGHT,FluidCloudAccent.AOD_CUSTOM,
                FluidCloudAccent.AOD_LIGHT,FluidCloudAccent.LOCK_GRADIENT,FluidCloudAccent.AOD_GRADIENT,FluidCloudStyle.COLOR_STYLE,FluidCloudStyle.LOCK_CAPSULE};
        for(String key:lockOwn)equal("",SettingsCatalog.unavailableReason(key,values),"the lock screen and AOD do not wait for the masters: "+key);
        equal(followOnly,SettingsCatalog.unavailableReason(FluidCloudStyle.FOLLOW_ONLY_PLAYING,values),"nothing follows yet");
        for(String key:new String[]{FluidCloudAccent.LOCK_OPACITY,FluidCloudAccent.LOCK_MASK_COLOR,FluidCloudAccent.LOCK_MASK_OPACITY})
            equal("请先开启锁屏自定义",SettingsCatalog.unavailableReason(key,values));
        for(String key:new String[]{FluidCloudAccent.AOD_OPACITY,FluidCloudAccent.AOD_MASK_COLOR,FluidCloudAccent.AOD_MASK_OPACITY})
            equal("请先开启 AOD 自定义",SettingsCatalog.unavailableReason(key,values));
        equal("请先开启通知跟随媒体色",SettingsCatalog.unavailableReason(FluidCloudStyle.NOTIFICATION_FOLLOW_OPACITY,values));
        values.put(FluidCloudStyle.NOTIFICATION_FOLLOW,true);
        equal("",SettingsCatalog.unavailableReason(FluidCloudStyle.NOTIFICATION_FOLLOW_OPACITY,values),"the strength follows its own switch, no master");
        equal("",SettingsCatalog.unavailableReason(FluidCloudStyle.FOLLOW_ONLY_PLAYING,values),"the playback gate is free once the lock screen follows on its own");
        values.put(FluidCloudStyle.NOTIFICATION_FOLLOW,false);
        values.put(FluidCloudAccent.LOCK_CUSTOM,true);
        for(String key:new String[]{FluidCloudAccent.LOCK_OPACITY,FluidCloudAccent.LOCK_MASK_COLOR,FluidCloudAccent.LOCK_MASK_OPACITY})
            equal("",SettingsCatalog.unavailableReason(key,values),"the lock look's values are free once it is on, without a master: "+key);
        equal("",SettingsCatalog.unavailableReason(FluidCloudStyle.FOLLOW_ONLY_PLAYING,values),"the lock look follows the media too");
        values.put(FluidCloudAccent.LOCK_CUSTOM,false);
        values.put(FluidCloudAccent.ENABLED,true);
        equal("请先开启“流体云样式”里的自定义底色",SettingsCatalog.unavailableReason(FluidCloudStyle.FOLLOW_MEDIA,values),"code100: following needs the custom base colour");
        values.put(FluidCloudStyle.MASTER,true);values.put(FluidCloudStyle.DESKTOP_BACKGROUND,true);
        for(String key:new String[]{FluidCloudAccent.MASK_ENABLED,FluidCloudAccent.OPACITY,FluidCloudStyle.FOLLOW_MEDIA})
            equal("",SettingsCatalog.unavailableReason(key,values),"master on frees the page switches: "+key);
        for(String key:new String[]{FluidCloudAccent.MASK_COLOR,FluidCloudAccent.MASK_OPACITY})
            equal("请先开启强调色遮罩",SettingsCatalog.unavailableReason(key,values));
        equal(followOnly,SettingsCatalog.unavailableReason(FluidCloudStyle.FOLLOW_ONLY_PLAYING,values));
        equal("",SettingsCatalog.unavailableReason(FluidCloudStyle.NOTIFICATION_FOLLOW,values),"ordinary notifications can follow independently of fluid rows");
        equal("请先开启通知跟随媒体色",SettingsCatalog.unavailableReason(FluidCloudStyle.NOTIFICATION_FOLLOW_OPACITY,values));
        values.put(FluidCloudStyle.NOTIFICATION_FOLLOW,true);
        equal("",SettingsCatalog.unavailableReason(FluidCloudStyle.NOTIFICATION_FOLLOW_OPACITY,values),"ordinary strength follows its own switch");
        equal("",SettingsCatalog.unavailableReason(FluidCloudStyle.FOLLOW_ONLY_PLAYING,values),"playback gate is available for ordinary-only following");
        values.put(FluidCloudStyle.NOTIFICATION_FOLLOW,false);
        values.put(FluidCloudAccent.MASK_ENABLED,true);values.put(FluidCloudStyle.FOLLOW_MEDIA,true);values.put(FluidCloudAccent.LOCK_CUSTOM,true);
        for(String key:new String[]{FluidCloudAccent.MASK_COLOR,FluidCloudAccent.MASK_OPACITY,FluidCloudStyle.FOLLOW_ONLY_PLAYING,FluidCloudStyle.NOTIFICATION_FOLLOW,
                FluidCloudAccent.LOCK_OPACITY,FluidCloudAccent.LOCK_MASK_COLOR,FluidCloudAccent.LOCK_MASK_OPACITY})
            equal("",SettingsCatalog.unavailableReason(key,values),"each child is free once its own parent is on: "+key);
        equal("请先开启通知跟随媒体色",SettingsCatalog.unavailableReason(FluidCloudStyle.NOTIFICATION_FOLLOW_OPACITY,values));
        values.put(FluidCloudStyle.NOTIFICATION_FOLLOW,true);
        equal("",SettingsCatalog.unavailableReason(FluidCloudStyle.NOTIFICATION_FOLLOW_OPACITY,values));
        values.put(FluidCloudAccent.ENABLED,false);
        for(String key:children)equal(pageMaster,SettingsCatalog.unavailableReason(key,values),"master off greys stored-on children too: "+key);
        for(String key:lockOwn)equal("",SettingsCatalog.unavailableReason(key,values),"but never the lock screen's own switches: "+key);
        equal(true,values.get(FluidCloudStyle.FOLLOW_MEDIA),"the stored choices stay");
        values.put(FluidCloudStyle.FOLLOW_MEDIA,true);values.remove(FluidCloudStyle.MASTER);values.remove(FluidCloudStyle.DESKTOP_BACKGROUND);
        for(String key:new String[]{FluidCloudStyle.FOLLOW_NOTIFICATION,FluidCloudStyle.DESKTOP_BACKGROUND,FluidCloudStyle.SCOPE_FLOAT,FluidCloudStyle.SCOPE_LOCK})
            equal("请先开启流体云样式",SettingsCatalog.unavailableReason(key,values));
        values.put(FluidCloudStyle.MASTER,true);
        for(String key:new String[]{FluidCloudStyle.FOLLOW_NOTIFICATION,FluidCloudStyle.DESKTOP_BACKGROUND,FluidCloudStyle.SCOPE_FLOAT,FluidCloudStyle.SCOPE_LOCK})
            equal("",SettingsCatalog.unavailableReason(key,values));
        equal("请先开启自定义底色",SettingsCatalog.unavailableReason(FluidCloudStyle.BACKGROUND_COLOR,values));
        values.put(FluidCloudStyle.DESKTOP_BACKGROUND,true);
        for(String mode:new String[]{"native","custom","notification","media"}) {
            values.put(FluidCloudStyle.BACKGROUND_MODE,mode);
            equal("",SettingsCatalog.unavailableReason(FluidCloudStyle.BACKGROUND_COLOR,values),"both sources and retired choices edit the colour "+mode);
            equal("",SettingsCatalog.unavailableReason(FluidCloudStyle.BACKGROUND_OPACITY,values),"every source keeps opacity editing "+mode);
        }
        // Keeping a music card's highlight belongs to the C17 removal, not to the style page master.
        Map<String,Object> keep=new HashMap<>();
        equal(true,SettingsCatalog.unavailableReason(FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT,keep).contains("C17 高光去除"));
        keep.put(C17HighlightRemoval.ENABLED,true);
        equal("",SettingsCatalog.unavailableReason(FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT,keep));
    }
    private static void publicNetworkSettings() {
        SettingsCatalog.Group network=SettingsCatalog.group("network");
        equal("网络组合",network.title);equal(null,network.masterKey);equal(true,network.independentSwitches);
        for(String legacy:new String[]{"wifi","label","native_network_badge"}) {
            equal(null,SettingsCatalog.group(legacy),"old destinations do not register duplicate fields "+legacy);
            equal("network",SettingsCatalog.canonicalGroupId(legacy),"legacy route resolves to merged page "+legacy);
        }
        equal("network",SettingsCatalog.canonicalGroupId("network"));equal(null,SettingsCatalog.canonicalGroupId(null));
        equal("wifi",SettingsCatalog.networkRoutePage("wifi"));equal("text",SettingsCatalog.networkRoutePage("label"));
        equal("badge",SettingsCatalog.networkRoutePage("native_network_badge"));equal("common",SettingsCatalog.networkRoutePage("network"));
        java.util.List<SettingsCatalog.DetailPage> tabs=SettingsCatalog.detailPages(network);
        equal(4,tabs.size());
        equal(java.util.Arrays.asList("common","wifi","text","badge"),java.util.Arrays.asList(tabs.get(0).id,tabs.get(1).id,tabs.get(2).id,tabs.get(3).id));
        equal(java.util.Arrays.asList("共用位置","Wi-Fi","制式文字","角标与箭头"),java.util.Arrays.asList(tabs.get(0).title,tabs.get(1).title,tabs.get(2).title,tabs.get(3).title));
        Set<String> networkKeys=new HashSet<>();
        for(SettingsCatalog.DetailPage tab:tabs)for(SettingsCatalog.Item item:tab.items) {
            equal(true,networkKeys.add(item.key),"one field on one network tab "+item.key);
            equal("network",item.groupId);equal(false,item.master);
            equal(tab.id,SettingsCatalog.networkDetailPageId(item.key),"search resolves to correct tab "+item.key);
            equal(true,!SettingsCatalog.networkOwner(item.key).isEmpty(),"historical portable ownership known "+item.key);
        }
        equal(network.items.size(),networkKeys.size());equal(0,SettingsCatalog.commonDetailItems(network).size());
        for(String key:new String[]{"wifi_enabled",NetworkDisplaySettings.WIFI_HIDDEN,"wifi_size_enabled",StatusBarSettings.WIFI_ICON_SCALE})
            equal(true,tabs.get(1).items.contains(SettingsCatalog.item(key)),"Wi-Fi visibility and styling independent "+key);
        for(String key:new String[]{"wifi_position_enabled",StatusBarSettings.WIFI_OFFSET_X,StatusBarSettings.WIFI_OFFSET_Y,
                StatusBarLayoutSettings.key("wifi","margin_left"),StatusBarLayoutSettings.key("wifi","margin_right")})
            equal(true,tabs.get(0).items.contains(SettingsCatalog.item(key)),"canonical position appears once "+key);
        for(String key:new String[]{"label_color_enabled","label_color_light","label_color_dark"})
            equal(true,tabs.get(3).items.contains(SettingsCatalog.item(key)),"text and badge share one colour block "+key);
        equal(null,SettingsCatalog.item(StatusBarSettings.LABEL_HIDDEN));equal(true,SettingsCatalog.hiddenKeys().contains(StatusBarSettings.LABEL_HIDDEN));
        equal(null,SettingsCatalog.item("data_badge_hidden"));equal(true,SettingsCatalog.hiddenKeys().contains("data_badge_hidden"));
        Map<String,Object> independent=new HashMap<>();independent.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED,true);
        for(String key:new String[]{"label_position_enabled",StatusBarSettings.LABEL_OFFSET_Y,
                StatusBarLayoutSettings.key("label","margin_left"),StatusBarLayoutSettings.key("label","margin_right")}) {
            equal(false,SettingsCatalog.applicable(SettingsCatalog.item(key),independent),"shared geometry not duplicated "+key);
            equal(true,SettingsCatalog.inactivePresentationReason(SettingsCatalog.item(key),independent).contains("独立位置"));
        }
        for(String group:new String[]{"speed","data"}) {
            equal(group,SettingsCatalog.group(group).id,"released independent destination "+group);
            equal(FeatureOptions.masterKey(group),SettingsCatalog.group(group).masterKey,"released page master "+group);
            equal(false,SettingsCatalog.group(group).independentSwitches,"released page has its own master "+group);
        }
        SettingsCatalog.Item unified=SettingsCatalog.item("label_normalize_enabled");
        equal("统一网络制式名称",unified.title);
        equal(true,unified.defaultValue,"released normalization default retained");
        equal("network",unified.groupId,"normalization stays editable in merged page");
        equal("network",SettingsCatalog.item("wifi_position_enabled").groupId,"Wi-Fi position is on network page");
        equal("network",SettingsCatalog.item("label_position_enabled").groupId,"independent label position remains saved");
        equal(null,SettingsCatalog.item(StatusBarSettings.SLOT_WIDTH),"automatic width has no manual editor");
        equal(true,SettingsCatalog.hiddenKeys().contains(StatusBarSettings.SLOT_WIDTH),"old width remains protocol-only");
        equal(false,SettingsCatalog.isVisible(StatusBarSettings.SLOT_WIDTH));
        equal(ConfigTransfer.Type.NUMBER,ConfigTransfer.types().get(StatusBarSettings.SLOT_WIDTH),"old backups still accept the numeric key");
        for(SettingsCatalog.Group group:SettingsCatalog.groups()) {
            for(SettingsCatalog.Item item:SettingsCatalog.searchItems(group))
                equal(false,StatusBarSettings.SLOT_WIDTH.equals(item.key),"search cannot reveal the retired width");
            for(SettingsCatalog.DetailPage page:SettingsCatalog.editorDetailPages(group,PanelMode.SEPARATE,StatusBarSettings.SLOT_WIDTH))
                for(SettingsCatalog.Item item:page.items)
                    equal(false,StatusBarSettings.SLOT_WIDTH.equals(item.key),"a stale requested key cannot reveal the retired width");
        }
        equal("文字粗细",SettingsCatalog.item("label_text_style_enabled").title);
        equal(true,SettingsCatalog.item("label_text_style_enabled").description.contains("宽度自动"));
        for(String key:new String[]{NetworkSlotGeometry.POSITION,NetworkSlotGeometry.OFFSET_X,NetworkSlotGeometry.OFFSET_Y,
                NetworkSlotGeometry.MARGIN_LEFT,NetworkSlotGeometry.MARGIN_RIGHT,"data_roaming_hidden"}) {
            equal(null,SettingsCatalog.item(key),"post-release control absent "+key);
            equal(true,SettingsCatalog.hiddenKeys().contains(key),"saved compatibility key is hidden "+key);
        }
        equal("network",SettingsCatalog.item(NetworkLabelFormats.ENABLED).groupId,"custom network text switch is on merged page");
        for(String key:NetworkLabelFormats.STRINGS.keySet()) {
            equal("network",SettingsCatalog.item(key).groupId,"per-generation text is editable "+key);
            equal(false,SettingsCatalog.hiddenKeys().contains(key),"per-generation text is not hidden "+key);
        }
        equal(null,SettingsCatalog.item(RadioState.BOTH_VISIBLE),"public 1.8.2 has no dual-display option");
        equal(true,SettingsCatalog.hiddenKeys().contains(RadioState.BOTH_VISIBLE),"newer saved key stays compatible without an entry");
    }
    private static void portraitClockActions() {
        SettingsCatalog.Group portrait=SettingsCatalog.group("notification_big_clock");
        SettingsCatalog.Group landscape=SettingsCatalog.group("notification_big_clock_landscape");
        for(String option:new String[]{NotificationBigClockSettings.PROPORTIONAL_DIGITS,NotificationBigClockSettings.TIME_ANIMATION_DISABLED}) {
            for(String key:new String[]{option,NotificationBigClockSettings.landscapeKey(option)}) {
                SettingsCatalog.Item item=SettingsCatalog.item(key);
                equal(true,item!=null,"clock option reachable "+key);
                equal(false,item.defaultValue,"clock option preserves previous behavior "+key);
                equal(SettingsCatalog.BOOLEAN,item.type,"clock option type "+key);
                equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(key),"clock option portable "+key);
                SettingsCatalog.Group owner=key.equals(option)?portrait:landscape;
                Set<String> destinations=new HashSet<>();
                for(SettingsCatalog.DetailPage page:SettingsCatalog.detailPages(owner))
                    for(SettingsCatalog.Item entry:page.items)if(key.equals(entry.key))destinations.add(page.id);
                equal(java.util.Collections.singleton("time"),destinations,"clock option appears once on time page "+key);
                for(String font:NotificationBigClockSettings.FONT_VALUES) {
                    Map<String,Object> settings=new HashMap<>();settings.put(NotificationBigClockSettings.FONT,font);
                    settings.put(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.FONT),font);
                    equal(true,SettingsCatalog.applicable(item,settings),"every font offers clock option "+key+"/"+font);
                }
            }
        }
        String[] keys={NotificationBigClockSettings.CLOCK_CLICK_ENABLED,NotificationBigClockSettings.DATE_CLICK_ENABLED};
        String[] pages={"time","date"};
        for(int i=0;i<keys.length;i++) {
            SettingsCatalog.Item item=SettingsCatalog.item(keys[i]);
            equal(true,item!=null,"portrait click is visible "+keys[i]);
            equal(portrait.id,item.groupId,"portrait click ownership "+keys[i]);
            equal(true,item.defaultValue,"portrait click default "+keys[i]);
            equal(SettingsCatalog.BOOLEAN,item.type,"portrait click Boolean "+keys[i]);
            equal(false,item.master,"portrait click is independent parameter "+keys[i]);
            Set<String> destinations=new HashSet<>();
            for(SettingsCatalog.DetailPage page:SettingsCatalog.detailPages(portrait))
                for(SettingsCatalog.Item entry:page.items)if(keys[i].equals(entry.key))destinations.add(page.id);
            equal(java.util.Collections.singleton(pages[i]),destinations,"one correct detail page "+keys[i]);
            String alias=NotificationBigClockSettings.landscapeKey(keys[i]);
            equal(null,SettingsCatalog.item(alias),"portrait click is absent from landscape "+alias);
            equal(false,StatusBarSettings.BOOLEAN_DEFAULTS.containsKey(alias),"no unsupported landscape default "+alias);
            for(SettingsCatalog.Item entry:landscape.items)equal(false,alias.equals(entry.key),"no copied landscape click "+alias);
        }
        String[] apps={NotificationBigClockSettings.CLOCK_LAUNCH_APP,NotificationBigClockSettings.DATE_LAUNCH_APP};
        Map<String,Object> values=new HashMap<>();
        for(int i=0;i<apps.length;i++) {
            SettingsCatalog.Item app=SettingsCatalog.item(apps[i]);
            equal(SettingsCatalog.STRING,app.type,"app selection row");equal("",app.defaultValue,"native default");
            Set<String> destinations=new HashSet<>();
            for(SettingsCatalog.DetailPage page:SettingsCatalog.detailPages(portrait))
                for(SettingsCatalog.Item entry:page.items)if(apps[i].equals(entry.key))destinations.add(page.id);
            equal(java.util.Collections.singleton(pages[i]),destinations,"app row follows its click page");
            equal("",SettingsCatalog.unavailableReason(apps[i],values),"default click enabled");
            values.put(keys[i],false);
            equal(true,SettingsCatalog.unavailableReason(apps[i],values).contains("请先开启"),"picker disabled with click");
        }
    }
    /** Retired host controls retain their data protocol without a page or search result. */
    private static void qsCardRetiredSettings() {
        equal(null,SettingsCatalog.group("qs_card"),"free editing belongs to the independent plugin");
        Set<String> retired=new HashSet<>(QsCardSettings.BOOLEANS.keySet());
        retired.addAll(QsCardSettings.NUMBERS.keySet());retired.addAll(QsCardSettings.STRINGS.keySet());
        equal(10,retired.size(),"all published host card keys retained");
        for(String key:retired) {
            equal(null,SettingsCatalog.item(key),"no host editor "+key);
            equal(true,SettingsCatalog.hiddenKeys().contains(key),"compatibility key hidden "+key);
            equal(true,ConfigTransfer.types().containsKey(key),"legacy key remains portable "+key);
        }
        for(SettingsCatalog.Group group:SettingsCatalog.groups())for(SettingsCatalog.Item item:SettingsCatalog.searchItems(group))
            equal(false,retired.contains(item.key),"retired host controls absent from search");
    }
    public static void main(String[] args) throws java.io.IOException {
        backdropReadability171();
        bigClockBackdropMaterialLock();
        bigClockColourMask();
        widgetPage();
        publicNetworkSettings();
        speedAdjacentGap();
        miniStackBadgeSetting();
        portraitClockActions();
        qsCardRetiredSettings();
        Set<String> reachable=new HashSet<>();
        for(String category:new String[]{SettingsCatalog.STATUSBAR,SettingsCatalog.NOTIFICATION,SettingsCatalog.CONTROL_CENTER,SettingsCatalog.LOCK_SCREEN,SettingsCatalog.OTHER})
            for(SettingsCatalog.NavigationSection section:SettingsCatalog.navigationSections(category))
                for(SettingsCatalog.Group group:section.groups){equal(true,group!=null);reachable.add(group.id);}
        for(SettingsCatalog.Group group:SettingsCatalog.groups()) {
            equal(true,reachable.contains(group.id),"reachable group "+group.id);
            equal(true,group.independentSwitches&&group.masterKey==null||StatusBarSettings.BOOLEAN_DEFAULTS.containsKey(group.masterKey),"master registered or independent page "+group.id+"/"+group.masterKey);
            Set<String> detailKeys=new HashSet<>();
            for(SettingsCatalog.Item item:SettingsCatalog.commonDetailItems(group))
                equal(true,detailKeys.add(item.key),"unique shared detail item "+group.id+"/"+item.key);
            for(SettingsCatalog.DetailPage page:SettingsCatalog.detailPages(group))for(SettingsCatalog.Item item:page.items)
                equal(true,detailKeys.add(item.key),"unique detail item "+group.id+"/"+page.id+"/"+item.key);
            for(SettingsCatalog.Item item:group.items) {
                equal(true,item.defaultValue!=null,"registered default "+group.id+"/"+item.key);
                equal(true,item.master||detailKeys.contains(item.key),"detail reachable "+group.id+"/"+item.key);
                if(SettingsCatalog.NUMERIC.equals(item.type)) {
                    equal(true,item.allowsCustomValue);equal(true,item.min<item.max);
                    if(!SettingsCatalog.unavailableReason(item.key).isEmpty())equal(true,SettingsCatalog.validationError(item,1f)!=null);
                    else if(QsTileCorners.RADIUS.equals(item.key)) {
                        equal(null,SettingsCatalog.validationError(item,0f));equal(null,SettingsCatalog.validationError(item,28f));equal(null,SettingsCatalog.validationError(item,30f));
                        equal(true,SettingsCatalog.validationError(item,30.01f)!=null);equal(true,SettingsCatalog.validationError(item,-.01f)!=null);
                    } else if(NotificationBigClockSettings.VISIBLE_COUNT.equals(item.key)) {
                        for(float count:new float[]{1f,2f,3f,4f,5f,6f,1000000f,(float)Integer.MAX_VALUE})equal(null,SettingsCatalog.validationError(item,count));
                        for(float count:new float[]{0f,-1f,1.5f,Float.MAX_VALUE})equal(true,SettingsCatalog.validationError(item,count)!=null);
                    } else if(NotificationBigClockSettings.REVEAL_MASK_OPACITY.equals(NotificationBigClockSettings.portraitKey(item.key))||BigClockWidgetsSettings.maskOpacityKey(item.key)) {
                        for(float opacity:new float[]{0f,20f,100f})equal(null,SettingsCatalog.validationError(item,opacity));
                        for(float opacity:new float[]{-1f,100.01f,1000000f})equal(true,SettingsCatalog.validationError(item,opacity)!=null);
                    } else if(ClockBackdropSettings.numericKey(item.key)) {
                        equal(null,SettingsCatalog.validationError(item,0f));equal(null,SettingsCatalog.validationError(item,ClockBackdropSettings.maximum(item.key)));
                        equal(true,SettingsCatalog.validationError(item,-.01f)!=null);equal(true,SettingsCatalog.validationError(item,1000000f)!=null);
                    } else if(BigClockWidgetsSettings.sizeKey(item.key)) {
                        for(float size:new float[]{8f,18f,64f})equal(null,SettingsCatalog.validationError(item,size));
                        for(float size:new float[]{7.99f,64.01f,1000000f})equal(true,SettingsCatalog.validationError(item,size)!=null);
                    } else if(BigClockWidgetsSettings.weightKey(item.key)) {
                        for(float weight:new float[]{100f,400f,687.5f,1000f})equal(null,SettingsCatalog.validationError(item,weight));
                        for(float weight:new float[]{99.99f,1000.01f,1000000f})equal(true,SettingsCatalog.validationError(item,weight)!=null);
                    } else if(BigClockWidgetsSettings.offsetKey(item.key)) {
                        for(float offset:new float[]{-1000f,0f,1000f})equal(null,SettingsCatalog.validationError(item,offset));
                        for(float offset:new float[]{-1000.01f,1000.01f,1000000f})equal(true,SettingsCatalog.validationError(item,offset)!=null);
                    } else if(StatusBarLayoutSettings.key(StatusBarLayoutSettings.LABEL,"order").equals(item.key)) {
                        for(float order:new float[]{0f,1f,2f})equal(null,SettingsCatalog.validationError(item,order));
                        equal(true,SettingsCatalog.validationError(item,3f)!=null);
                    } else equal(null,SettingsCatalog.validationError(item,1000000f));
                    equal(true,SettingsCatalog.validationError(item,Float.NaN)!=null);equal(true,SettingsCatalog.validationError(item,Float.POSITIVE_INFINITY)!=null);
                }
            }
        }
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),StatusBarShadeIconSettings.MASTER));
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),NetworkIconOrder.SWAP));
        equal(null,SettingsCatalog.group("network_order"),"legacy swap destination replaced by complete layout");
        equal(SettingsCatalog.STATUSBAR,SettingsCatalog.group(StatusBarLayoutSettings.GROUP).category);
        equal(null,SettingsCatalog.item(NetworkIconOrder.SWAP),"old swap no longer exposed");
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(NetworkIconOrder.SWAP));
        Map<String,Object> savedOrder=new HashMap<>(); savedOrder.put(NetworkIconOrder.SWAP,true);
        equal("",SettingsCatalog.unavailableReason(NetworkIconOrder.SWAP,savedOrder),"public network order is editable");
        equal(true,StatusBarSettings.bool(savedOrder,NetworkIconOrder.SWAP),"saved sorting choice remains enabled");
        equal(true,SettingsCatalog.groups().contains(SettingsCatalog.group(StatusBarLayoutSettings.GROUP)),"complete layout has a normal destination");
        savedOrder.put(RadioState.BOTH_VISIBLE,true);
        equal(true,StatusBarSettings.bool(savedOrder,RadioState.BOTH_VISIBLE),"newer saved dual value stays in storage only");
        equal(true,SettingsCatalog.applicable(SettingsCatalog.item("wifi_position_enabled"),savedOrder),"saved dual choice does not replace released Wi-Fi position controls");
        equal(true,SettingsCatalog.applicable(SettingsCatalog.item("label_position_enabled"),savedOrder),"saved dual choice does not replace released label position controls");
        equal(null,SettingsCatalog.item(RadioState.BOTH_VISIBLE),"dual display is absent in the public label page");
        equal(true,savedOrder.get(RadioState.BOTH_VISIBLE),"viewing public pages does not erase newer saved keys");
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),NotificationBigClockSettings.STACK_ENABLED));
        equal(true,StatusBarSettings.bool(java.util.Collections.singletonMap(NotificationBigClockSettings.STACK_ENABLED,true),NotificationBigClockSettings.STACK_ENABLED));
        equal(false,StatusBarSettings.bool(java.util.Collections.singletonMap(StatusBarShadeIconSettings.MASTER,true),StatusBarShadeIconSettings.MASTER));
        equal(null,SettingsCatalog.group("shade_status_icons"));
        for(String key:new String[]{StatusBarShadeIconSettings.MASTER,NotificationBigClockSettings.INTERACTIVE_STACK,
                NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.STACK_ENABLED),
                NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.VISIBLE_COUNT),
                NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.TAIL_WIDTH_1)}) {
            equal(false,SettingsCatalog.isVisible(key)); equal(true,SettingsCatalog.hiddenKeys().contains(key));
        }
        // Each page has one home except the per-surface carrier text and the shared pull-down clock.
        java.util.Map<String,Integer> homes=new java.util.HashMap<>();
        for(String category:new String[]{SettingsCatalog.STATUSBAR,SettingsCatalog.NOTIFICATION,SettingsCatalog.CONTROL_CENTER,SettingsCatalog.LOCK_SCREEN,SettingsCatalog.OTHER})
            for(SettingsCatalog.NavigationSection section:SettingsCatalog.navigationSections(category))
                for(SettingsCatalog.Group group:section.groups)homes.merge(group.id,1,Integer::sum);
        for(java.util.Map.Entry<String,Integer> home:homes.entrySet())
            if(!"carrier".equals(home.getKey())&&!"shade_clock".equals(home.getKey()))equal(1,home.getValue(),"single home "+home.getKey());
        for(String category:new String[]{SettingsCatalog.STATUSBAR,SettingsCatalog.NOTIFICATION,SettingsCatalog.CONTROL_CENTER,SettingsCatalog.LOCK_SCREEN,SettingsCatalog.OTHER})
            for(SettingsCatalog.NavigationSection section:SettingsCatalog.navigationSections(category))
                for(SettingsCatalog.Group group:section.groups)
                    if(!"carrier".equals(group.id)&&!"shade_clock".equals(group.id))equal(category,group.category,"page category "+group.id);
        SettingsCatalog.Group icons=SettingsCatalog.group("status_icon_library");
        equal(SettingsCatalog.OTHER,icons.category);equal("图标管理",icons.title);equal(IconManagementSettings.ENABLED,icons.masterKey);
        equal(2,icons.items.size(),"one pause master plus v3 editor destination replace legacy layer controls");
        equal(IconManagementSettings.ENABLED,icons.items.get(0).key);equal(IconManagementSettings.KEY,icons.items.get(1).key);
        equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(IconManagementSettings.KEY));
        for(String retired:IconManagementSettings.LEGACY_KEYS){
            equal(null,SettingsCatalog.item(retired),"retired icon source has no editor");
            equal(false,SettingsCatalog.isVisible(retired));equal(true,SettingsCatalog.hiddenKeys().contains(retired));
            equal(false,ConfigTransfer.types().containsKey(retired),"old icon rules cannot re-enter portable settings");
        }
        for(String key:new String[]{NotificationBigClockSettings.DATE_COLOR_LIGHT,NotificationBigClockSettings.DATE_COLOR_DARK,
                NotificationBigClockSettings.FOOTER_COLOR_LIGHT,NotificationBigClockSettings.FOOTER_COLOR_DARK}) {
            for(String field:new String[]{key,NotificationBigClockSettings.landscapeKey(key)}) {
                equal(true,SettingsCatalog.applicable(SettingsCatalog.item(field),java.util.Collections.emptyMap()),"independent line colour is available by default "+field);
                boolean landscapeField=field.startsWith(NotificationBigClockSettings.LANDSCAPE_PREFIX);
                String follow=landscapeField?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.FOLLOW_STYLE):NotificationBigClockSettings.FOLLOW_STYLE;
                String reveal=landscapeField?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED):NotificationBigClockSettings.DIGIT_REVEAL_ENABLED;
                Map<String,Object> following=new HashMap<>();following.put(follow,true);
                equal(true,SettingsCatalog.applicable(SettingsCatalog.item(field),following),"saved follow cannot hide colour while its parent is off "+field);
                following.put(reveal,true);
                equal(false,SettingsCatalog.applicable(SettingsCatalog.item(field),following),"active follow uses common colour "+field);
                following.put(reveal,false);
                equal(true,SettingsCatalog.applicable(SettingsCatalog.item(field),following),"stopping reveal restores independent colour "+field);
                equal(true,following.get(follow),"viewing paused follow preserves the saved preference "+field);
                equal(true,ConfigTransfer.types().containsKey(field),"saved line colour remains portable "+field);
            }
        }

        SettingsCatalog.Group whole=SettingsCatalog.group("notification_stack");
        equal(null,SettingsCatalog.item("data_single_enabled"));
        equal(SettingsCatalog.NOTIFICATION,whole.category);equal("堆叠通知",whole.title);
        equal(NotificationBigClockSettings.STACK_ENABLED,whole.masterKey);
        equal(whole.id,SettingsCatalog.item(NotificationMiniStack.AUTO_HIDE_IN_STACK).groupId);
        equal(true,SettingsCatalog.item(NotificationMiniStack.AUTO_HIDE_IN_STACK).defaultValue);
        equal(1f,SettingsCatalog.item(NotificationBigClockSettings.VISIBLE_COUNT).min);
        equal(5f,SettingsCatalog.item(NotificationBigClockSettings.VISIBLE_COUNT).max);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),whole.masterKey));
        equal(null,SettingsCatalog.group("notification_group_stack"));
        equal(null,SettingsCatalog.item(NotificationGroupStack.MASTER));
        equal(true,SettingsCatalog.hiddenKeys().contains(NotificationGroupStack.MASTER));
        SettingsCatalog.Item landscapeStack=SettingsCatalog.item(FeatureOptions.STACK_LANDSCAPE_ENABLED);
        equal(whole.id,landscapeStack.groupId);equal(SettingsCatalog.BOOLEAN,landscapeStack.type);
        equal("横屏也生效",landscapeStack.title);equal(true,landscapeStack.description.contains("开启堆叠通知后"));
        equal(false,landscapeStack.defaultValue);equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(landscapeStack.key));
        java.util.Map<String,Object> stackDependencies=new java.util.HashMap<>();
        equal(true,SettingsCatalog.unavailableReason(whole.masterKey,stackDependencies).contains("避免通知无法滚动"));
        stackDependencies.put(whole.masterKey,true); // A saved request cannot remove the dependency warning.
        equal(true,SettingsCatalog.unavailableReason(whole.masterKey,stackDependencies).contains("先开启"));
        stackDependencies.put(NotificationBigClockSettings.MASTER,true);
        equal("",SettingsCatalog.unavailableReason(whole.masterKey,stackDependencies));
        equal(true,SettingsCatalog.unavailableReason(landscapeStack.key,stackDependencies).contains("先开启"));
        stackDependencies.put(NotificationBigClockSettings.MASTER,false);
        stackDependencies.put(NotificationBigClockSettings.LANDSCAPE_MASTER,true);
        equal("",SettingsCatalog.unavailableReason(landscapeStack.key,stackDependencies));
        equal(true,SettingsCatalog.unavailableReason(whole.masterKey,stackDependencies).contains("先开启"));
        stackDependencies.put(landscapeStack.key,true);
        equal("",SettingsCatalog.unavailableReason(whole.masterKey,stackDependencies));
        stackDependencies.put(NotificationBigClockSettings.LANDSCAPE_MASTER,false);
        equal(true,SettingsCatalog.unavailableReason(whole.masterKey,stackDependencies).contains("先开启"));
        equal(true,SettingsCatalog.conflictDescription(whole.id,whole.category).contains("对应方向的大时钟"));
        equal(false,StatusBarSettings.bool(java.util.Collections.singletonMap(NotificationGroupStack.MASTER,true),NotificationGroupStack.MASTER));
        SettingsCatalog.Group hints=SettingsCatalog.group("status_hint_icons");
        equal(SettingsCatalog.STATUSBAR,hints.category);equal(NativeStatusIcons.MASTER,hints.masterKey);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),hints.masterKey));
        SettingsCatalog.Group badge=SettingsCatalog.group("network");
        equal("网络组合",badge.title);equal(SettingsCatalog.STATUSBAR,badge.category);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),NativeNetworkBadgeControls.MASTER));
        java.util.Map<String,Object> badgeValues=new java.util.HashMap<>();
        equal("",SettingsCatalog.unavailableReason(NativeNetworkBadgeControls.MASTER,badgeValues));
        badgeValues.put("label_enabled",true);
        for(SettingsCatalog.Item item:badge.items)if("native_network_badge".equals(SettingsCatalog.networkOwner(item.key)))
            if(item.key.startsWith("native_data_activity_"))equal("",SettingsCatalog.unavailableReason(item.key,badgeValues));
            else equal(true,SettingsCatalog.unavailableReason(item.key,badgeValues).contains("选择原生角标"));
        equal(badge.id,SettingsCatalog.item(NativeDataActivity.MASTER).groupId);
        equal(false,StatusBarSettings.bool(badgeValues,NativeDataActivity.MASTER));
        equal(0xff000000,StatusBarSettings.COLOR_DEFAULTS.get(NativeDataActivity.COLOR_LIGHT));
        equal(0xffffffff,StatusBarSettings.COLOR_DEFAULTS.get(NativeDataActivity.COLOR_DARK));
        badgeValues.put("label_enabled",false);
        for(SettingsCatalog.Item item:badge.items)if("native_network_badge".equals(SettingsCatalog.networkOwner(item.key)))
            equal("",SettingsCatalog.unavailableReason(item.key,badgeValues));
        equal(-3.5f,SettingsCatalog.customNumber(SettingsCatalog.item(NativeNetworkBadgeSettings.key(1,"offset_x")),"-3.5"));
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NativeNetworkBadgeSettings.key(1,"weight")),badgeValues).description.contains("原生字体"));
        SettingsCatalog.Item badgeFont=SettingsCatalog.item(NativeNetworkBadgeSettings.key(1,"font"));
        equal(badge.id,badgeFont.groupId);equal(SettingsCatalog.OPTIONS,badgeFont.type);equal("native",badgeFont.defaultValue);
        equal("native",StatusBarSettings.STRING_DEFAULTS.get(badgeFont.key));equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(badgeFont.key));
        equal(5,badgeFont.values.length);
        for(String source:new String[]{"native","global","system","pingfang","custom"})equal(null,SettingsCatalog.validationError(badgeFont,source));
        equal(true,SettingsCatalog.validationError(badgeFont,"unknown")!=null);
        badgeValues.put(badgeFont.key,"system");
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NativeNetworkBadgeSettings.key(1,"weight")),badgeValues).description.contains("系统字体"));
        badgeValues.put(badgeFont.key,"custom");badgeValues.put(StatusBarSettings.FONT_REVISION,FontCatalog.find("manrope").sha256);
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NativeNetworkBadgeSettings.key(1,"weight")),badgeValues).description.contains("Manrope"));
        equal(4,SettingsCatalog.detailPages(badge).size());
        for(int part=1;part<=2;part++) {
            equal("角标与箭头",SettingsCatalog.detailPages(badge).get(3).title);
            for(String suffix:new String[]{"enabled","offset_x","offset_y","scale","weight","font"}) {
                SettingsCatalog.Item setting=SettingsCatalog.item(NativeNetworkBadgeSettings.key(part,suffix));
                equal(badge.id,setting.groupId);
                equal(true,SettingsCatalog.detailPages(badge).get(3).items.contains(setting));
            }
        }
        equal(true,SettingsCatalog.detailPages(badge).get(3).items.contains(SettingsCatalog.item(NativeDataActivity.MASTER)));
        for(String old:new String[]{NativeNetworkBadgeControls.X,NativeNetworkBadgeControls.Y,NativeNetworkBadgeControls.SCALE,
                NativeNetworkBadgeControls.WEIGHT,NativeNetworkBadgeControls.FONT})equal(true,SettingsCatalog.hiddenKeys().contains(old));
        equal(true,NumericPolicy.signed(NativeStatusIcons.X));equal(true,NumericPolicy.signed(NativeStatusIcons.Y));
        equal(null,SettingsCatalog.item(NativeStatusIcons.X),"legacy system-icon X is retired from the editor");
        equal(true,SettingsCatalog.hiddenKeys().contains(NativeStatusIcons.X),"legacy X is excluded from unmatched controls");
        equal(ConfigTransfer.Type.NUMBER,ConfigTransfer.types().get(NativeStatusIcons.X),"legacy X remains portable");
        equal(-4.5f,SettingsCatalog.value(java.util.Collections.singletonMap(NativeStatusIcons.X,-4.5f),NativeStatusIcons.X),"saved legacy X remains readable");
        SettingsCatalog.Item systemLeft=SettingsCatalog.item(StatusBarLayoutSettings.key(StatusBarLayoutSettings.SYSTEM,"margin_left"));
        SettingsCatalog.Item systemRight=SettingsCatalog.item(StatusBarLayoutSettings.key(StatusBarLayoutSettings.SYSTEM,"margin_right"));
        equal("status_hint_icons",systemLeft.groupId);equal(systemLeft.groupId,systemRight.groupId);
        equal(4.5f,SettingsCatalog.customNumber(systemLeft,"4.5"));
        equal(null,SettingsCatalog.validationError(systemRight,-4.5f),"physical gap accepts negative distances");
        equal(true,SettingsCatalog.item(StatusBarLayoutSettings.key(StatusBarLayoutSettings.SYSTEM,"order"))!=null,"system group participates in new order editor");
        equal(14f,SettingsCatalog.item(NativeStatusIcons.MAX).defaultValue);
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NativeStatusIcons.MAX),java.util.Collections.emptyMap()).integer);
        equal(true,SettingsCatalog.validationError(SettingsCatalog.item(NativeStatusIcons.MAX),-.5f)!=null);
        equal(true,SettingsCatalog.validationError(SettingsCatalog.item(NativeStatusIcons.MAX),1.5f)!=null);
        equal(true,SettingsCatalog.requiresNumericTrial(SettingsCatalog.item(NativeStatusIcons.MAX),15));
        SettingsCatalog.Group portrait=SettingsCatalog.group("notification_big_clock"),landscape=SettingsCatalog.group("notification_big_clock_landscape");
        equal("竖屏大时钟",portrait.title);equal("横屏大时钟",landscape.title);
        equal(NotificationBigClockSettings.LANDSCAPE_MASTER,landscape.masterKey);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),landscape.masterKey));
        SettingsCatalog.Item expandedHeight=SettingsCatalog.item(NotificationBigClockSettings.MAX_SIZE);
        SettingsCatalog.Item compactHeight=SettingsCatalog.item(NotificationBigClockSettings.COMPACT_MAX_SIZE);
        SettingsCatalog.Item landscapeHeight=SettingsCatalog.item(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.MAX_SIZE));
        Map<String,Object> clockFonts=new HashMap<>();
        for(String font:new String[]{"native","system","pingfang","custom"}) {
            clockFonts.put(NotificationBigClockSettings.FONT,font);
            clockFonts.put(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.FONT),font);
            equal(true,SettingsCatalog.applicable(expandedHeight,clockFonts));
            equal("native".equals(font),SettingsCatalog.applicable(compactHeight,clockFonts));
            equal("native".equals(font)?"展开字体高度":"字体高度调节",SettingsCatalog.displayTitle(expandedHeight,clockFonts));
            equal("字体高度调节",SettingsCatalog.displayTitle(landscapeHeight,clockFonts));
            equal("大小与字重",landscapeHeight.section);
        }
        for(String key:new String[]{NotificationBigClockSettings.SCALE,NotificationBigClockSettings.COMPACT_SCALE}) {
            equal(true,SettingsCatalog.hiddenKeys().contains(key));equal(null,SettingsCatalog.item(key));
            equal(true,StatusBarSettings.NUMERIC_DEFAULTS.containsKey(key));
        }
        SettingsCatalog.Item gap=SettingsCatalog.item(NotificationIconArea.SPACING);
        equal("图标间距",gap.section);equal(0f,gap.defaultValue);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),NotificationIconArea.SPACING_ENABLED));
        equal(-3.5f,SettingsCatalog.customNumber(gap,"-3.5"));
        equal(true,SettingEditor.forItem(gap,java.util.Collections.emptyMap()).description.contains("不改变图标大小"));
        Set<String> unusedLandscapeCompact=new HashSet<>();
        for(String key:new String[]{NotificationBigClockSettings.COMPACT_SCALE,NotificationBigClockSettings.COMPACT_MAX_SIZE,
                NotificationBigClockSettings.COMPACT_OFFSET_X,NotificationBigClockSettings.COMPACT_OFFSET_Y}){
            String mapped=NotificationBigClockSettings.landscapeKey(key);unusedLandscapeCompact.add(mapped);
            equal(true,SettingsCatalog.hiddenKeys().contains(mapped));equal(null,SettingsCatalog.item(mapped));
        }
        for(SettingsCatalog.Item item:portrait.items)if(!item.master) {
            String mapped=NotificationBigClockSettings.landscapeKey(item.key);
            SettingsCatalog.Item other=SettingsCatalog.item(mapped);
            if(unusedLandscapeCompact.contains(mapped)||NotificationBigClockSettings.SCREEN_PADDING.equals(item.key)
                    ||NotificationBigClockSettings.CLOCK_CLICK_ENABLED.equals(item.key)
                    ||NotificationBigClockSettings.DATE_CLICK_ENABLED.equals(item.key)
                    ||NotificationBigClockSettings.CLOCK_LAUNCH_APP.equals(item.key)
                    ||NotificationBigClockSettings.DATE_LAUNCH_APP.equals(item.key)
                    ||item.key.startsWith("notification_big_clock_widget_")
                    ||NotificationBigClockSettings.REBOUND_BLUR_ENABLED.equals(item.key)
                    ||NotificationBigClockSettings.REBOUND_BLUR_RADIUS.equals(item.key)
                    ||NotificationBigClockSettings.REBOUND_FADE.equals(item.key)||NotificationBigClockSettings.REBOUND_LOCK.equals(item.key)){
                equal(null,other,"portrait-only item absent from landscape "+item.key);continue;
            }
            equal(true,other!=null,"shared landscape item "+item.key);equal(landscape.id,other.groupId);equal(item.type,other.type);
            equal(item.defaultValue,other.defaultValue);equal(item.section,other.section);
        }
        SettingsCatalog.Item screenPadding=SettingsCatalog.item(NotificationBigClockSettings.SCREEN_PADDING);
        equal(portrait.id,screenPadding.groupId);equal(24f,screenPadding.defaultValue);
        equal(true,SettingsCatalog.applicable(screenPadding,java.util.Collections.emptyMap()));
        java.util.Map<String,Object> customHeight=new java.util.HashMap<>();customHeight.put(NotificationBigClockSettings.FONT,"system");
        equal(false,SettingsCatalog.applicable(screenPadding,customHeight));
        equal(null,SettingsCatalog.item(NotificationBigClockSettings.landscapeKey(screenPadding.key)));
        equal(true,SettingEditor.forItem(screenPadding,java.util.Collections.emptyMap()).description.contains("左右"));
        // Device code162: the pull-down wallpaper is open again.
        equal(true,SettingsCatalog.unavailableReason(ShadeWallpaperSettings.MASTER).isEmpty(),"the wallpaper is available");
        equal(false,SettingsCatalog.groups().stream().anyMatch(group->group.title.contains("未完成")),"no page says unfinished");
        for(int part=1;part<=2;part++) {
            SettingsCatalog.Item hidden=SettingsCatalog.item(NativeNetworkBadgeSettings.key(part,"hidden"));
            equal("network",hidden.groupId);equal(false,hidden.defaultValue);
            equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(hidden.key));
        }
        SettingsCatalog.Item widthEnabled=SettingsCatalog.item(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH_ENABLED),
                width=SettingsCatalog.item(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH);
        equal(landscape.id,widthEnabled.groupId);equal(landscape.id,width.groupId);
        equal(false,widthEnabled.defaultValue);equal(100f,width.defaultValue);equal("%",width.unit);
        equal(null,SettingsCatalog.item(NotificationBigClockSettings.portraitKey(width.key)));
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),widthEnabled.key));
        equal(137.5f,SettingsCatalog.customNumber(width,"137.5"));
        equal(null,SettingsCatalog.validationError(width,1000000f));
        SettingsCatalog.Item millis=SettingsCatalog.item(NetworkSpeedControls.INTERVAL_MILLIS);
        equal("speed",millis.groupId);equal("ms",millis.unit);equal(1f,millis.min);equal(500f,millis.max);equal(250f,millis.defaultValue);
        equal(true,SettingsCatalog.hiddenKeys().contains(NetworkSpeedControls.INTERVAL_SECONDS));
        equal(null,SettingsCatalog.item(NetworkSpeedControls.INTERVAL_SECONDS));
        equal(false,SettingsCatalog.requiresNumericTrial(millis,250f));equal(true,SettingsCatalog.requiresNumericTrial(millis,.25f));
        equal(true,SettingsCatalog.requiresNumericTrial(millis,500.01f));
        for(float value:new float[]{Float.MIN_VALUE,.25f,501f,Float.MAX_VALUE})equal(null,SettingsCatalog.validationError(millis,value));
        for(float value:new float[]{0f,-1f})equal(true,SettingsCatalog.validationError(millis,value)!=null);
        equal(true,SettingsCatalog.conflictDescription(portrait.id,SettingsCatalog.NOTIFICATION).contains("关闭两个方向"));
        equal(true,SettingsCatalog.conflictDescription(landscape.id,SettingsCatalog.NOTIFICATION).contains("独立设置"));
        equal(true,SettingsCatalog.conflictDescription("carrier",SettingsCatalog.NOTIFICATION).contains("关闭大时钟"));
        equal("",SettingsCatalog.conflictDescription("carrier",SettingsCatalog.LOCK_SCREEN));
        equal(null,SettingsCatalog.group("tiles"));equal(true,SettingsCatalog.hiddenKeys().contains(TilePageEffects.STYLE));
        equal(true,SettingsCatalog.group("qs_appearance").description.contains("渐变效果开销大"));
        SettingsCatalog.Group materials=SettingsCatalog.group("c17_highlight_removal");
        equal(SettingsCatalog.OTHER,materials.category);
        equal(materials.id,SettingsCatalog.item(C17HighlightRemoval.NOTIFICATION_ENABLED).groupId);
        equal(materials.id,SettingsCatalog.item(C17HighlightRemoval.CONTROL_ENABLED).groupId);
        equal(materials.id,SettingsCatalog.item(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED).groupId);
        equal(false,SettingsCatalog.item(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED).defaultValue);
        SettingsCatalog.Group portraitClear=SettingsCatalog.group("notification_clear"),landscapeClear=SettingsCatalog.group("notification_clear_landscape");
        equal("竖屏清除按钮位置",portraitClear.title);equal("横屏清除按钮位置",landscapeClear.title);
        equal(NotificationClearAppearance.MASTER,portraitClear.masterKey);equal(NotificationClearMotion.LANDSCAPE_MASTER,landscapeClear.masterKey);
        equal(SettingsCatalog.NOTIFICATION,landscapeClear.category);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),landscapeClear.masterKey));
        equal("",SettingsCatalog.unavailableReason(landscapeClear.masterKey,java.util.Collections.emptyMap()));
        for(String key:new String[]{NotificationClearMotion.LANDSCAPE_OFFSET_X,NotificationClearMotion.LANDSCAPE_OFFSET_Y}){
            SettingsCatalog.Item position=SettingsCatalog.item(key);
            equal(landscapeClear.id,position.groupId);equal(0f,position.defaultValue);equal("dp",position.unit);
            equal(-160f,position.min);equal(160f,position.max);equal(-12.25f,SettingsCatalog.customNumber(position,"-12.25"));
        }
        equal(portraitClear.id,SettingsCatalog.item(NotificationClearMotion.OFFSET_Y).groupId);
        equal(landscapeClear.id,SettingsCatalog.item(NotificationClearAppearance.LANDSCAPE_MASTER).groupId);
        equal(false,SettingsCatalog.item(NotificationClearAppearance.LANDSCAPE_MASTER).defaultValue);
        equal(false,SettingsCatalog.item(NotificationClearAppearance.LANDSCAPE_GRADIENT_ENABLED).defaultValue);
        equal(100f,SettingsCatalog.item(NotificationClearAppearance.LANDSCAPE_OPACITY).defaultValue);
        equal(90f,SettingsCatalog.item(NotificationClearAppearance.LANDSCAPE_GRADIENT_ANGLE).defaultValue);
        for(String key:new String[]{NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT,NotificationClearAppearance.LANDSCAPE_COLOR_DARK,
                NotificationClearAppearance.LANDSCAPE_GRADIENT_COLOR_LIGHT,NotificationClearAppearance.LANDSCAPE_GRADIENT_COLOR_DARK}){
            equal(landscapeClear.id,SettingsCatalog.item(key).groupId);equal(SettingsCatalog.COLOR,SettingsCatalog.item(key).type);
            equal(ConfigTransfer.Type.COLOR,ConfigTransfer.types().get(key));
            equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(StatusBarSettings.alphaKey(key)));
        }
        SettingsCatalog.Group lockDate=SettingsCatalog.group("lockscreen_date"),lockIcon=SettingsCatalog.group("lockscreen_lock_icon");
        equal(SettingsCatalog.LOCK_SCREEN,lockDate.category);equal(SettingsCatalog.LOCK_SCREEN,lockIcon.category);
        equal(LockscreenControls.DATE_ENABLED,lockDate.masterKey);equal(LockscreenControls.HIDE_LOCK,lockIcon.masterKey);
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),lockDate.masterKey));
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),lockIcon.masterKey));
        SettingsCatalog.Item lockPattern=SettingsCatalog.item(LockscreenControls.DATE_FORMAT);
        equal(lockDate.id,lockPattern.groupId);equal(SettingsCatalog.STRING,lockPattern.type);
        equal("M月d日 {周}",lockPattern.defaultValue);equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(lockPattern.key));
        equal(null,SettingsCatalog.validationError(lockPattern,"yyyy年M月d日 {星期}"));
        fluidCloudSettings();
        for(String key:new String[]{NetworkSpeedControls.DISPLAY_STYLE,NetworkSpeedControls.STYLE_ENABLED}){
            equal(null,SettingsCatalog.item(key));equal(true,SettingsCatalog.hiddenKeys().contains(key));
        }
        equal(true,SettingsCatalog.validationError(SettingsCatalog.item(NotificationIconArea.MAX_COUNT),-.1f)!=null);
        equal(true,SettingsCatalog.validationError(SettingsCatalog.item(NotificationIconArea.MAX_COUNT),1.5f)!=null);
        equal(12.34567f,SettingsCatalog.customNumber(SettingsCatalog.item(StatusBarSettings.SHADE_CLOCK_OFFSET_X),"12.34567"));
        equal(3f,SettingsCatalog.customNumber(SettingsCatalog.item(NotificationIconArea.MAX_COUNT),"3.0000"));
        for(String value:new String[]{"3.5","-0.1","2.0000000000000000001"}) {
            checks++;try{SettingsCatalog.customNumber(SettingsCatalog.item(NotificationIconArea.MAX_COUNT),value);throw new AssertionError("Accepted count "+value);}
            catch(IllegalArgumentException expected){}
        }
        for(String value:new String[]{"30.00000000000000001","-0.0000001"}) {
            checks++;try{SettingsCatalog.customNumber(SettingsCatalog.item(QsTileCorners.RADIUS),value);throw new AssertionError("Accepted radius "+value);}
            catch(IllegalArgumentException expected){}
        }
        editorContext();
        System.out.println("Settings catalog and contextual editor checks passed: "+checks);
    }

    private static void backdropReadability171() {
        Map<String,Object> values=new HashMap<>();
        java.util.List<String> controls=new java.util.ArrayList<>();controls.addAll(ClockBackdropSettings.BOOLEANS.keySet());controls.addAll(ClockBackdropSettings.NUMBERS.keySet());controls.addAll(ClockBackdropSettings.COLORS.keySet());
        for(boolean landscape:new boolean[]{false,true}){
            String parent=landscape?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED):NotificationBigClockSettings.DIGIT_REVEAL_ENABLED;
            for(String base:controls){String key=landscape?NotificationBigClockSettings.landscapeKey(base):base;SettingsCatalog.Item item=SettingsCatalog.item(key);
                String section=ClockBackdropSettings.DATE_MASK_OPACITY.equals(base)?"日期背景样式":ClockBackdropSettings.FOOTER_MASK_OPACITY.equals(base)?"底部背景样式":"主时间立体边缘";
                equal(true,item!=null,"new material control reachable "+key);equal(section,item.section);equal(true,SettingsCatalog.unavailableReason(key,values).contains("请先开启"));
                values.put(parent,true);values.put(landscape?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.FOLLOW_STYLE):NotificationBigClockSettings.FOLLOW_STYLE,true);equal("",SettingsCatalog.unavailableReason(key,values));values.put(parent,false);
                if(ClockBackdropSettings.numericKey(key)){equal(0f,item.min);equal(ClockBackdropSettings.maximum(key),item.max);equal(null,SettingsCatalog.validationError(item,0f));equal(true,SettingsCatalog.validationError(item,-.01f)!=null);equal(true,SettingsCatalog.validationError(item,item.max+.01f)!=null);}
            }
        }
        for(String retired:new String[]{ClockBackdropSettings.NOTIFICATION_FOLLOW,ClockBackdropSettings.NOTIFICATION_RELIEF}) {
            equal(null,SettingsCatalog.item(retired),"notification material entry removed");equal(false,SettingsCatalog.isVisible(retired));equal(true,SettingsCatalog.hiddenKeys().contains(retired));
            equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(retired),"published notification protocol remains portable");
            equal(true,SettingsCatalog.unavailableReason(retired,values).contains("已移除"));
            for(SettingsCatalog.Group group:SettingsCatalog.groups())for(SettingsCatalog.Item item:group.items)equal(false,retired.equals(item.key),"retired notification entry absent from every page and search");
        }
        for(int side:new int[]{0,1}){String app=BigClockWidgetsSettings.launchAppKey(side),click=BigClockWidgetsSettings.clickEnabledKey(side);
            equal(false,SettingsCatalog.item(click).defaultValue);equal("",SettingsCatalog.item(app).defaultValue);values.put(BigClockWidgetsSettings.enabledKey(side),true);
            equal(true,SettingsCatalog.unavailableReason(app,values).contains("请先开启组件点击"));values.put(click,true);equal("",SettingsCatalog.unavailableReason(app,values));
            equal(null,SettingsCatalog.validationError(SettingsCatalog.item(app),"com.example.clock"));equal(true,SettingsCatalog.validationError(SettingsCatalog.item(app),"intent://clock")!=null);
        }
    }
    private static void bigClockBackdropMaterialLock() {
        String[] locked={NotificationBigClockSettings.GLASS,NotificationBigClockSettings.GLASS_BORDER_ENABLED,
                NotificationBigClockSettings.GLASS_BORDER_WIDTH,NotificationBigClockSettings.GLASS_BORDER_COLOR_LIGHT,NotificationBigClockSettings.GLASS_BORDER_COLOR_DARK,
                NotificationBigClockSettings.LOCK_STYLE,NotificationBigClockSettings.LOCK_STYLE_NATIVE,NotificationBigClockSettings.LOCK_STYLE_BODY,
                NotificationBigClockSettings.LOCK_STYLE_GRADIENT,NotificationBigClockSettings.LOCK_STYLE_RIM,NotificationBigClockSettings.LOCK_STYLE_RIM_DEPTH,
                NotificationBigClockSettings.LOCK_STYLE_ROUND,NotificationBigClockSettings.LOCK_STYLE_SHADE,NotificationBigClockSettings.LOCK_STYLE_GLOW,NotificationBigClockSettings.LOCK_STYLE_ANGLE,
                NotificationBigClockSettings.COLOR_LIGHT,NotificationBigClockSettings.COLOR_DARK,
                StatusBarSettings.alphaKey(NotificationBigClockSettings.COLOR_LIGHT),StatusBarSettings.alphaKey(NotificationBigClockSettings.COLOR_DARK),
                StatusBarSettings.alphaKey(NotificationBigClockSettings.GLASS_BORDER_COLOR_LIGHT),StatusBarSettings.alphaKey(NotificationBigClockSettings.GLASS_BORDER_COLOR_DARK)};
        String[] editable={NotificationBigClockSettings.FONT,NotificationBigClockSettings.MAX_SIZE,NotificationBigClockSettings.COMPACT_MAX_SIZE,
                NotificationBigClockSettings.WEIGHT,NotificationBigClockSettings.LETTER_SPACING,NotificationBigClockSettings.PROPORTIONAL_DIGITS,
                NotificationBigClockSettings.OFFSET_X,NotificationBigClockSettings.OFFSET_Y,NotificationBigClockSettings.COMPACT_OFFSET_X,NotificationBigClockSettings.COMPACT_OFFSET_Y,
                NotificationBigClockSettings.PATTERN,NotificationBigClockSettings.TIME_ANIMATION_DISABLED,NotificationBigClockSettings.ENTRY_EFFECT_ENABLED,
                NotificationBigClockSettings.DATE_COLOR_LIGHT,NotificationBigClockSettings.DATE_COLOR_DARK,NotificationBigClockSettings.FOOTER_COLOR_LIGHT,NotificationBigClockSettings.FOOTER_COLOR_DARK};
        for(boolean landscape:new boolean[]{false,true}) {
            String parent=landscape?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED):NotificationBigClockSettings.DIGIT_REVEAL_ENABLED;
            String style=landscape?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.LOCK_STYLE):NotificationBigClockSettings.LOCK_STYLE;
            Map<String,Object> saved=new HashMap<>();saved.put(parent,true);saved.put(style,false);
            Map<String,Object> retained=new HashMap<>(saved);
            for(String raw:locked) {
                String key=landscape?NotificationBigClockSettings.landscapeKey(raw):raw;
                equal(true,SettingsCatalog.unavailableReason(key,saved).contains("字内背景透出"),"the selected orientation locks competing material and alpha controls: "+key);
            }
            equal(retained,saved,"availability checks do not overwrite saved appearance choices");
            for(String raw:editable) {
                String key=landscape?NotificationBigClockSettings.landscapeKey(raw):raw;
                equal("",SettingsCatalog.unavailableReason(key,saved),"font geometry, content, motion and independent labels stay editable: "+key);
            }
            String opposite=landscape?NotificationBigClockSettings.GLASS:NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.GLASS);
            equal("",SettingsCatalog.unavailableReason(opposite,saved),"one orientation cannot lock the other orientation's material");
            String follow=landscape?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.FOLLOW_STYLE):NotificationBigClockSettings.FOLLOW_STYLE;
            saved.put(follow,true);String opacity=landscape?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.REVEAL_MASK_OPACITY):NotificationBigClockSettings.REVEAL_MASK_OPACITY;
            saved.put(opacity,0f);
            equal("",SettingsCatalog.unavailableReason(follow,saved),"zero tint still keeps background reveal and optional following available");
            equal(true,SettingsCatalog.unavailableReason(landscape?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.COLOR_LIGHT):NotificationBigClockSettings.COLOR_LIGHT,saved).contains("字内背景透出"),"zero tint does not reactivate the saved foreground colour");
            saved.put(parent,false);
            equal("",SettingsCatalog.unavailableReason(landscape?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.GLASS):NotificationBigClockSettings.GLASS,saved),"closing reveal restores ordinary material availability");
            equal(true,saved.get(follow),"the saved following choice survives closing the parent");
            equal(false,SettingsCatalog.unavailableReason(follow,saved).isEmpty(),"following cannot be enabled without background reveal");
            saved.put(style,true);
            equal("",SettingsCatalog.unavailableReason(landscape?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.LOCK_STYLE_BODY):NotificationBigClockSettings.LOCK_STYLE_BODY,saved),"the prior lock-style strength is editable again after reveal closes");
        }
    }
    /** One visible opacity control replaces the retired background reveal strength, without changing portable data. */
    private static void bigClockColourMask() throws java.io.IOException {
        for(boolean landscape:new boolean[]{false,true}) {
            String enabled=NotificationBigClockSettings.DIGIT_REVEAL_ENABLED;
            String follow=NotificationBigClockSettings.FOLLOW_STYLE;
            String colour=NotificationBigClockSettings.REVEAL_MASK_COLOR;
            String opacity=NotificationBigClockSettings.REVEAL_MASK_OPACITY;
            String amount=NotificationBigClockSettings.DIGIT_REVEAL_AMOUNT;
            if(landscape) {
                enabled=NotificationBigClockSettings.landscapeKey(enabled);
                follow=NotificationBigClockSettings.landscapeKey(follow);
                colour=NotificationBigClockSettings.landscapeKey(colour);
                opacity=NotificationBigClockSettings.landscapeKey(opacity);
                amount=NotificationBigClockSettings.landscapeKey(amount);
            }
            java.util.List<String> visible=new java.util.ArrayList<>(java.util.Arrays.asList(enabled,follow));
            visible.add(colour);visible.add(opacity);
            SettingsCatalog.Item master=SettingsCatalog.item(enabled);
            equal("字内背景透出",master.title);
            equal("背景透出与遮罩",master.section);
            equal(true,master.description.contains("系统已有的模糊背景"));
            equal(true,master.description.contains("黑色压暗"));
            equal(true,SettingsCatalog.item(opacity).description.contains("0% 不叠加颜色"));
            equal(true,SettingsCatalog.item(opacity).description.contains("仍透出"));
            equal(true,SettingsCatalog.item(opacity).description.contains("100%"));
            equal(false,SettingsCatalog.item(follow).defaultValue);
            equal(0xffffffff,SettingsCatalog.item(colour).defaultValue);
            equal(20f,SettingsCatalog.item(opacity).defaultValue);
            Map<String,Object> saved=new HashMap<>();saved.put(follow,true);
            for(String key:new String[]{follow,colour,opacity})
                equal("请先开启字内背景透出",SettingsCatalog.unavailableReason(key,saved));
            saved.put(enabled,true);
            for(String key:new String[]{follow,colour,opacity})equal("",SettingsCatalog.unavailableReason(key,saved));
            java.util.List<String> actual=new java.util.ArrayList<>();
            SettingsCatalog.Group group=SettingsCatalog.group(master.groupId);
            for(SettingsCatalog.DetailPage page:SettingsCatalog.detailPages(group))for(SettingsCatalog.Item item:page.items)
                if(master.section.equals(item.section)) {
                    equal("look",page.id);actual.add(item.key);
                    equal(false,item.description.contains("不采集或模糊背景"));
                }
            equal(visible,actual,"backdrop and main tint remain together; independent line strengths and relief have their own blocks");
            equal(null,SettingsCatalog.item(amount));
            equal(false,SettingsCatalog.isVisible(amount));
            equal(true,SettingsCatalog.hiddenKeys().contains(amount));
            equal(ConfigTransfer.Type.NUMBER,ConfigTransfer.types().get(amount));
            equal(60f,StatusBarSettings.NUMERIC_DEFAULTS.get(amount));
            int searchEntries=0,allEntries=0;
            for(SettingsCatalog.Group entry:SettingsCatalog.groups())for(SettingsCatalog.Item item:entry.items)
                if(amount.equals(item.key))searchEntries++;
            for(SettingsCatalog.Item item:SettingsCatalog.allItems())if(amount.equals(item.key))allEntries++;
            equal(0,searchEntries,"retired strength cannot become a search result");equal(0,allEntries);
            saved.put(amount,37.5f);
            Map<String,Object> imported=ConfigTransfer.prepare(ConfigTransfer.exportJson(saved),true).values();
            equal(37.5f,imported.get(amount),"old strength survives portable round trip");
            equal(true,imported.get(enabled));equal(true,imported.get(follow));
        }
    }
    private static void widgetPage() {
        SettingsCatalog.Group portrait=SettingsCatalog.group("notification_big_clock");
        for(int side:new int[]{BigClockWidgetsSettings.LEFT,BigClockWidgetsSettings.RIGHT}) {
            String enabled=BigClockWidgetsSettings.enabledKey(side),text=BigClockWidgetsSettings.textKey(side),size=BigClockWidgetsSettings.sizeKey(side),
                    x=BigClockWidgetsSettings.offsetXKey(side),y=BigClockWidgetsSettings.offsetYKey(side),follow=BigClockWidgetsSettings.followTimeColorKey(side),
                    light=BigClockWidgetsSettings.colorKey(side,false),dark=BigClockWidgetsSettings.colorKey(side,true);
            equal(false,SettingsCatalog.item(enabled).defaultValue);equal(true,SettingsCatalog.item(follow).defaultValue);
            equal(side==BigClockWidgetsSettings.LEFT?"（天气）":"（日期）",SettingsCatalog.item(text).defaultValue);
            equal(SettingsCatalog.STRING,SettingsCatalog.item(text).type);equal(true,SettingsCatalog.item(text).allowsCustomValue);
            java.util.List<String> presets=java.util.Arrays.asList(SettingsCatalog.item(text).values);
            equal(true,presets.containsAll(java.util.Arrays.asList("（天气）","（天气原色）","（天气:{描述:中文}）","（天气:{描述:英文}）","（日期）","（时间）","（农历）")),"component presets cover weather language, native colours and time-like choices");
            equal(true,presets.containsAll(java.util.Arrays.asList("（蜂窝信号）","（网络状态）","（电池）","（电池:{电量}%{充电?⚡}）","（信号:{条:4:●:○}）","（网络:{类型}{受限?'（受限）'}）")),"both components offer the system-state presets and templates");
            equal(presets.size(),new java.util.HashSet<>(presets).size(),"component presets are unique");
            equal(presets.size(),SettingsCatalog.item(text).labels.length,"every component preset has a name");
            equal(true,java.util.Arrays.asList(SettingsCatalog.item(text).labels).containsAll(java.util.Arrays.asList("蜂窝信号（含图标）","网络状态（含图标）","电池电量（含图标）")),"system-state presets are discoverable");
            if(side==BigClockWidgetsSettings.RIGHT) {
                equal(true,SettingsCatalog.item(text).description.contains("半角括号"),"ASCII token spelling is explained");
                equal("（日期）",BigClockWidgetsSettings.STRINGS.get(text),"adding presets never replaces the right-side default");
                equal(false,BigClockWidgetsSettings.BOOLEANS.get(enabled),"adding presets never enables the right-side component");
            }
            equal(18f,SettingsCatalog.item(size).defaultValue);equal(8f,SettingsCatalog.item(size).min);equal(64f,SettingsCatalog.item(size).max);
            equal(0f,SettingsCatalog.item(x).defaultValue);equal(-1000f,SettingsCatalog.item(x).min);equal(1000f,SettingsCatalog.item(x).max);
            equal(0f,SettingsCatalog.item(y).defaultValue);equal(-1000f,SettingsCatalog.item(y).min);equal(1000f,SettingsCatalog.item(y).max);
            equal(0xff000000,SettingsCatalog.item(light).defaultValue);equal(0xffffffff,SettingsCatalog.item(dark).defaultValue);
            for(String key:new String[]{enabled,text,size,x,y,follow,light,dark,BigClockWidgetsSettings.maskOpacityKey(side)}) {
                SettingsCatalog.Item item=SettingsCatalog.item(key);
                equal(portrait.id,item.groupId);equal(null,SettingsCatalog.item(NotificationBigClockSettings.landscapeKey(key)),"widgets stay portrait only");
                boolean reachable=false;
                for(SettingsCatalog.DetailPage page:SettingsCatalog.detailPages(portrait))if(page.items.contains(item)) {
                    reachable=true;equal("widgets",page.id);equal("左右组件",page.title);
                }
                equal(true,reachable,"widget field is reachable "+key);
            }
            for(String retired:new String[]{BigClockWidgetsSettings.providerKey(side),BigClockWidgetsSettings.heightKey(side)}) {
                equal(null,SettingsCatalog.item(retired));equal(false,SettingsCatalog.isVisible(retired));equal(true,SettingsCatalog.hiddenKeys().contains(retired));
                equal(true,ConfigTransfer.types().containsKey(retired),"old AppWidget key stays portable");
            }
            equal("",StatusBarSettings.STRING_DEFAULTS.get(BigClockWidgetsSettings.providerKey(side)));
            equal(96f,StatusBarSettings.NUMERIC_DEFAULTS.get(BigClockWidgetsSettings.heightKey(side)));
            int sdk=android.os.Build.VERSION.SDK_INT;
            try {
                android.os.Build.VERSION.SDK_INT=29;
                equal("",SettingsCatalog.unavailableReason(enabled,java.util.Collections.emptyMap()),"text does not require an AppWidget host");
                equal(false,SettingsCatalog.unavailableReason(text,java.util.Collections.emptyMap()).isEmpty(),"text waits for its own side");
                Map<String,Object> values=new HashMap<>();values.put(enabled,true);
                for(String key:new String[]{text,size,x,y,follow})equal("",SettingsCatalog.unavailableReason(key,values));
                equal(false,SettingsCatalog.unavailableReason(BigClockWidgetsSettings.textKey(1-side),values).isEmpty(),"text sides have independent gates");
                equal(false,SettingsCatalog.applicable(SettingsCatalog.item(light),values));equal(false,SettingsCatalog.applicable(SettingsCatalog.item(dark),values));
                values.put(follow,false);
                equal(true,SettingsCatalog.applicable(SettingsCatalog.item(light),values));equal(true,SettingsCatalog.applicable(SettingsCatalog.item(dark),values));
                equal(false,values.get(follow),"viewing independent colours does not rewrite saved follow");
            } finally {android.os.Build.VERSION.SDK_INT=sdk;}
            for(String value:new String[]{"","普通文字","（天气）","天气：（天气） （日期）"})equal(null,SettingsCatalog.validationError(SettingsCatalog.item(text),value));
            for(String value:new String[]{"两行\n文字","两行\r文字",new String(new char[BigClockWidgetsSettings.MAX_TEXT+1]).replace('\0','字')})equal(true,SettingsCatalog.validationError(SettingsCatalog.item(text),value)!=null);
            equal(null,SettingsCatalog.validationError(SettingsCatalog.item(text),new String(new char[BigClockWidgetsSettings.MAX_TEXT]).replace('\0','字')));equal(240,BigClockWidgetsSettings.MAX_TEXT,"templates need room: the component text allows 240 characters");
            equal(1000f,SettingsCatalog.customNumber(SettingsCatalog.item(x),"1000"));equal(-1000f,SettingsCatalog.customNumber(SettingsCatalog.item(y),"-1000"));
            for(String value:new String[]{"1000.00001","-1000.00001"}) {
                boolean rejected=false;try{SettingsCatalog.customNumber(SettingsCatalog.item(x),value);}catch(IllegalArgumentException expected){rejected=true;}
                equal(true,rejected,"exact decimal position range is validated before float rounding");
            }
        }
        equal(java.util.Arrays.asList("pui","native"),java.util.Arrays.asList(SettingsCatalog.item(StatusBarSettings.BATTERY_STYLE).values),"battery choice removes iOS 27 while historical decoding stays separate");
    }
    private static void speedAdjacentGap(){
        Map<String,Object> saved=new HashMap<>();saved.put("speed_enabled",true);
        saved.put("speed_position_enabled",true);saved.put(SpeedPosition.POSITION,SpeedPosition.CLOCK_RIGHT);
        saved.put(StatusBarSettings.SPEED_OFFSET_X,23.75f);saved.put(StatusBarSettings.SPEED_OFFSET_Y,-6.25f);
        saved.put(SpeedPosition.SAFE_GAP,3.25f);saved.put(SpeedPosition.SAFE_GAP_ENABLED,true);
        Map<String,Object> before=new HashMap<>(saved);
        for(String key:new String[]{StatusBarSettings.SPEED_OFFSET_X,SpeedPosition.POSITION,
                SpeedPosition.SAFE_GAP,SpeedPosition.SAFE_GAP_ENABLED,SpeedPosition.EDGE_RESERVE}) {
            equal(null,SettingsCatalog.item(key),"conflicting horizontal editor retired "+key);
            equal(false,SettingsCatalog.isVisible(key),"retired editor is not searchable "+key);
            equal(true,ConfigTransfer.types().containsKey(key),"old portable key retained "+key);
        }
        equal("",SettingsCatalog.unavailableReason(StatusBarSettings.SPEED_OFFSET_Y,saved),"vertical editor retained");
        equal(23.75f,SettingsCatalog.value(saved,StatusBarSettings.SPEED_OFFSET_X),"old x is retained as data");
        equal(-6.25f,SettingsCatalog.value(saved,StatusBarSettings.SPEED_OFFSET_Y));
        equal(SpeedPosition.CLOCK_RIGHT,SettingsCatalog.value(saved,SpeedPosition.POSITION));
        equal(before,saved,"viewing does not erase old positions");
        equal("",SettingsCatalog.unavailableReason(StatusBarLayoutSettings.MASTER,saved),"layout can be enabled independently");
    }
    private static void miniStackBadgeSetting(){
        SettingsCatalog.Item hide=SettingsCatalog.item(NotificationMiniStack.HIDE_BADGE);
        equal(true,hide!=null,"mini stack badge hiding is enabled again");
        equal("notification_mini_stack",hide.groupId);equal(SettingsCatalog.BOOLEAN,hide.type);
        equal(false,hide.master);equal(false,hide.defaultValue);
        equal(true,SettingsCatalog.isVisible(hide.key));equal(false,SettingsCatalog.hiddenKeys().contains(hide.key));
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(hide.key));
        equal(null,SettingsCatalog.validationError(hide,true));equal(null,SettingsCatalog.validationError(hide,false));
        Map<String,Object> saved=new HashMap<>();
        equal("请先开启小堆叠（同应用）",SettingsCatalog.unavailableReason(hide.key,saved));
        saved.put(NotificationMiniStack.ENABLED,true);
        for(boolean on:new boolean[]{true,false,true}){
            saved.put(hide.key,on);Map<String,Object> before=new HashMap<>(saved);
            equal("",SettingsCatalog.unavailableReason(hide.key,saved));
            equal(on,SettingsCatalog.value(saved,hide.key));equal(before,saved);
        }
        int reachable=0;
        for(SettingsCatalog.DetailPage page:SettingsCatalog.detailPages(SettingsCatalog.group(hide.groupId)))
            for(SettingsCatalog.Item item:page.items)if(hide.key.equals(item.key))reachable++;
        equal(1,reachable,"one editable badge-hiding entry");
    }
    private static void editorContext(){
        java.util.Map<String,Object> values=new java.util.HashMap<>();
        Set<String> groups=new HashSet<>(),units=new HashSet<>();
        for(SettingsCatalog.Item item:SettingsCatalog.allItems())if(SettingsCatalog.NUMERIC.equals(item.type)){
            SettingEditor.Info info=SettingEditor.forItem(item,values);
            groups.add(item.groupId);units.add(item.unit);
            equal(true,info.numeric);equal(true,info.context.contains(SettingsCatalog.group(item.groupId).title)||"carrier".equals(item.groupId));
            String section=item.section.contains(" · ")?item.section.substring(item.section.lastIndexOf(" · ")+3):item.section;
            equal(true,info.context.endsWith(section));equal(true,info.valueSummary.contains("默认值："));
            equal(true,info.valueSummary.contains(SettingEditor.number(item.min)+"～"+SettingEditor.number(item.max)));
            if(!item.unit.isEmpty())equal(true,info.label.contains(item.unit));
            if(!item.key.equals(QsTileCorners.RADIUS))equal(false,info.description.contains("0～30 dp"));
            if(!item.key.equals(NotificationIconArea.MAX_COUNT))equal(false,info.description.contains("必须为非负整数"));
            equal(false,info.description.contains("100 最细，900 最粗"));
        }
        for(String unit:new String[]{"dp","sp","em","%","秒","ms","°","个",""})equal(true,units.contains(unit));
        for(String group:new String[]{"speed","data","wifi","label","clock","carrier","battery","notification_icons","tiles","qs_appearance","qs_tile_corners","qs_tile_icon_size","shade_clock","notification_big_clock","notification_clear","qs_media"}){
            if(SettingsCatalog.group(group)!=null&&!SettingsCatalog.group(group).items.stream().noneMatch(item->SettingsCatalog.NUMERIC.equals(item.type)))equal(true,groups.contains(group));
        }
        SettingsCatalog.Item weight=SettingsCatalog.item(BatteryTextStyle.WEIGHT),offset=SettingsCatalog.item(StatusBarSettings.SHADE_CLOCK_OFFSET_X);
        values.put(weight.key,1234.567f);SettingEditor.Info saved=SettingEditor.forItem(weight,values);
        equal("1234.567",saved.initial);equal(true,saved.valueSummary.contains("1234.567"));equal(true,saved.description.contains("1～1000"));
        equal("电池 · 电池内文字",saved.context);equal(false,saved.integer);
        values.put(offset.key,-.00012345f);equal(-.00012345f,SettingsCatalog.customNumber(offset,SettingEditor.forItem(offset,values).initial));
        equal(true,SettingEditor.forItem(offset,values).signed);equal(true,SettingEditor.forItem(offset,values).description.contains("负值向左"));
        SettingsCatalog.Item corner=SettingsCatalog.item(QsTileCorners.RADIUS),count=SettingsCatalog.item(NotificationIconArea.MAX_COUNT);
        equal(30f,corner.max);equal(29.875f,SettingsCatalog.customNumber(corner,"29.875"));equal(30f,SettingsCatalog.customNumber(corner,"30"));
        equal(false,SettingsCatalog.requiresNumericTrial(corner,30f));
        equal(true,SettingEditor.forItem(corner,values).valueSummary.contains("允许范围：0～30 dp"));
        equal(true,SettingEditor.forItem(count,values).integer);equal(true,SettingEditor.forItem(count,values).description.contains("0 表示不显示图标"));
        for(int exact:new int[]{16777217,Integer.MAX_VALUE}){
            values.put(count.key,exact);equal(exact,SettingEditor.storedValue(count,values));
            equal(Integer.toString(exact),SettingEditor.forItem(count,values).initial);
            equal(exact+" 个",SettingEditor.formatted(count,exact));
        }
        equal(10.12345f,SettingsCatalog.customNumber(corner,"10.12345"));
        equal(20000f,SettingsCatalog.customNumber(count,"2e4"));
        equal(Float.MAX_VALUE,SettingsCatalog.customNumber(weight,Float.toString(Float.MAX_VALUE)));
        values.put("font_enabled",true);values.put(StatusBarSettings.FONT_MODE,"custom");values.put(StatusBarSettings.FONT_REVISION,FontCatalog.find("manrope").sha256);
        equal(true,SettingEditor.forItem(SettingsCatalog.item(StatusBarSettings.CLOCK_WEIGHT),values).description.contains("Manrope，实际字重轴为200～800"));
        values.put(NotificationBigClockSettings.FONT,"native");
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NotificationBigClockSettings.WEIGHT),values).description.contains("当前来源：原生字体"));
        values.put(NotificationBigClockSettings.FONT,"system");
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NotificationBigClockSettings.DATE_WEIGHT),values).description.contains("当前来源：系统字体"));
        values.put(NotificationBigClockSettings.FONT,"custom");
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NotificationBigClockSettings.FOOTER_WEIGHT),values).description.contains("200～800"));
        values.put("font_enabled",false);equal(true,SettingEditor.forItem(SettingsCatalog.item(StatusBarSettings.CLOCK_WEIGHT),values).description.contains("当前来源：系统字体"));
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NotificationBigClockSettings.COMPACT_OFFSET_Y),values).description.contains("在初始偏移上额外调整收起位置"));
        for(String group:CarrierPanels.GROUPS){
            String context=SettingEditor.context(SettingsCatalog.item(CarrierPanels.key(group,"weight")));
            equal(true,context.contains(group.equals(CarrierPanels.LOCKSCREEN)?"锁屏":group.equals(CarrierPanels.CONTROL)?"控制中心":"通知栏"));
            equal(true,context.endsWith("文字样式"));
        }
        SettingEditor.Info pattern=SettingEditor.forItem(SettingsCatalog.item(StatusBarSettings.CLOCK_PATTERN),values);
        equal(true,pattern.pattern);equal(false,pattern.numeric);equal(true,pattern.description.contains("HH:mm:ss"));equal(true,pattern.description.contains(Integer.toString(TimeFormat.MAX_PATTERN)));
        for(String token:new String[]{"{星期英文简}","{星期英文}","{星期英文简大写}","{星期英文大写}","{星期英文简小写}","{星期英文小写}","{月份英文简}","{月份英文}","{月份英文简大写}","{月份英文大写}","{月份英文简小写}","{月份英文小写}","{英文时段}","{英文时段大写}","{英文时段小写}","{AMPM}","{ampm}","{时段}"})
            equal(true,pattern.description.contains(token));
        equal(true,pattern.description.contains("中午"));equal(true,pattern.description.contains("不改变系统语言"));
        java.util.Set<String> clockCategories=new java.util.HashSet<>();
        for(String[] choice:SettingEditor.presets(SettingsCatalog.item(StatusBarSettings.CLOCK_PATTERN))){
            if(choice[1].startsWith("中文："))clockCategories.add("中文");
            if(choice[1].startsWith("英文："))clockCategories.add("英文");
            if(choice[1].startsWith("组合："))clockCategories.add("组合");
        }
        equal(new java.util.HashSet<>(java.util.Arrays.asList("中文","英文","组合")),clockCategories);
        long noon=java.time.Instant.parse("2026-10-28T04:30:00Z").toEpochMilli();
        java.util.TimeZone shanghai=java.util.TimeZone.getTimeZone("Asia/Shanghai");
        equal("中午 12:30 Wed",TimeFormat.format("{时段} HH:mm {星期英文简}",noon,shanghai));
        equal("Oct 28 Wed",TimeFormat.format("{月份英文简} d {星期英文简}",noon,shanghai));
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NotificationBigClockSettings.FOOTER_PATTERN),values).description.contains("{text}"));
        equal(true,SettingEditor.forItem(SettingsCatalog.item(NotificationIconArea.TEXT),values).description.contains("12个"));
        // "预设": every time-class field offers ready-made formats, each one saves and previews without an error.
        int timeFields=0;
        for(SettingsCatalog.Item item:SettingsCatalog.allItems()){
            boolean timeClass=item.key.endsWith("_pattern")||LockscreenControls.DATE_FORMAT.equals(item.key);
            String[][] presets=SettingEditor.presets(item);
            if(BigClockWidgetsSettings.textKey(item.key)){
                // Left/right components offer presets like the time fields; each one saves and previews.
                equal(true,presets.length>0);equal(true,SettingEditor.forItem(item,values).previewable);equal(false,SettingEditor.forItem(item,values).pattern);
                for(String[] preset:presets){
                    equal(null,BigClockWidgetsSettings.textValidationError(preset[0]));
                    String shown=SettingEditor.preview(item,preset[0]).toString();
                    equal(true,!shown.isEmpty()&&!shown.startsWith("格式有误"));
                    equal(true,!shown.contains("（")&&!shown.contains("格式:")&&!shown.contains("暂无数据"));
                }
                continue;
            }
            if(!timeClass){equal(0,presets.length);continue;}
            timeFields++;
            equal(true,presets.length>0);
            boolean footer=NotificationBigClockSettings.FOOTER_PATTERN.equals(NotificationBigClockSettings.portraitKey(item.key));
            java.util.HashSet<String> seen=new java.util.HashSet<>();
            for(String[] preset:presets){
                equal(2,preset.length);equal(true,!preset[0].isEmpty()&&!preset[1].isEmpty());equal(true,seen.add(preset[0]));
                String problem=footer?TimeFormat.contentValidationError(preset[0]):TimeFormat.validationError(preset[0]);
                equal(null,problem);
                equal(false,SettingEditor.preview(item,preset[0]).toString().startsWith("格式有误"));
            }
        }
        equal(true,timeFields>=10);
        equal(0,SettingEditor.presets(null).length);
        String trial=SettingEditor.trialSummary(weight,824f,937.5f,values);
        equal(true,trial.contains("上次确认：824"));equal(true,trial.contains("本次试用：937.5"));equal(true,trial.contains("超出滑块建议：100～900"));equal(true,trial.contains("20秒"));
    }
}
