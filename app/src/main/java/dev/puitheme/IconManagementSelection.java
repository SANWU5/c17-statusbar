// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.io.IOException;
import java.util.*;

/** Pure production selection/color plan, with no source discovery, decoding or native-state guessing. */
public final class IconManagementSelection {
    public static final class AssetRef{
        public final String packId,role;public final IconManagementManifest.Asset asset;
        private AssetRef(String packId,String role,IconManagementManifest.Asset asset){this.packId=packId;this.role=role;this.asset=asset;}
        public String cacheKey(){return packId+'/'+asset.source+':'+asset.render;}
    }
    public static final class Plan{
        public final boolean system;public final List<AssetRef> candidates;
        private Plan(boolean system,List<AssetRef> candidates){this.system=system;this.candidates=Collections.unmodifiableList(candidates);}
    }
    public static Plan plan(IconManagementSettings.State settings,String role,Map<String,IconManagementManifest> packs){
        List<AssetRef> result=new ArrayList<>();IconManagementSettings.Override override=settings.overrides.get(role);
        if(override!=null&&"system".equals(override.mode))return new Plan(true,result);
        if(override!=null)add(result,packs,override.packId,override.sourceRole);
        String pack=settings.scopePacks.get(IconRoleRegistry.scopeOfRole(role));if(pack!=null)add(result,packs,pack,role);
        return new Plan(false,result);
    }
    /** The editor, previews and runtime must resolve the same audited source identity. */
    public static String sourceRole(IconManagementManifest manifest,String role){
        if(manifest==null||role==null)return null;
        String selected=PuiIconAliases.sourceRole(manifest,role);if(selected!=null)return selected;
        IconRoleRegistry.Target target=IconRoleRegistry.targetForRole(role);
        return target!=null&&"hint".equals(target.scope)&&manifest.icons.containsKey(target.id)?target.id:null;
    }
    private static void add(List<AssetRef> result,Map<String,IconManagementManifest> packs,String id,String role){
        IconManagementManifest manifest=packs.get(id);if(manifest==null)return;String selected=sourceRole(manifest,role);IconManagementManifest.Asset asset=selected==null?null:manifest.icons.get(selected);
        if(asset==null)return;for(AssetRef old:result)if(old.packId.equals(id)&&old.asset.source.equals(asset.source)&&old.asset.render.equals(asset.render))return;result.add(new AssetRef(id,selected,asset));
    }
    public static Set<String> referencedPacks(IconManagementSettings.State state){Set<String> ids=new LinkedHashSet<>(state.scopePacks.values());for(IconManagementSettings.Override selected:state.overrides.values())if("pack".equals(selected.mode))ids.add(selected.packId);return Collections.unmodifiableSet(ids);}
    public static final class ColorPlan{
        public final String mode;public final int color;public final float opacity,alpha,sizePercent,scale;public final boolean artwork;
        private ColorPlan(String mode,int color,float opacity,float sizePercent){this.mode=mode;this.color=color;this.opacity=opacity;alpha=opacity/100f;this.sizePercent=sizePercent;scale=sizePercent/100f;artwork="artwork".equals(mode);}
    }
    public static ColorPlan appearance(IconManagementSettings.State state,String role,boolean dark,String render){
        IconRoleRegistry.Target target=IconRoleRegistry.targetForRole(role);String scope=IconRoleRegistry.scopeOfRole(role);
        IconManagementSettings.Appearance[] levels={state.appearances.get("role:"+role),target==null?null:state.appearances.get("target:"+target.id),scope==null?null:state.appearances.get("scope:"+scope)};
        String mode=null;int color=0xff000000;Float opacity=null,size=null;
        for(IconManagementSettings.Appearance level:levels)if(level!=null){if(mode==null&&!"inherit".equals(level.mode)){mode=level.mode;color=dark?level.darkColor:level.lightColor;}if(opacity==null&&level.opacity!=null)opacity=level.opacity;if(size==null&&level.sizePercent!=null)size=level.sizePercent;}
        if(mode==null)mode="color".equals(render)?"artwork":"system";if("artwork".equals(mode)&&!"color".equals(render))mode="system";
        return new ColorPlan(mode,color,opacity==null?100f:opacity,size==null?100f:size);
    }
    public static boolean animationEnabled(IconManagementSettings.State state,String target){return !Boolean.FALSE.equals(state.animations.get(target));}
    public static final class ApplicationPreview{
        public final String records;public final List<String> replaced,missing,preserved,unsupported;public final int replaceCount,missingCount,preserveCount,unsupportedCount;
        private ApplicationPreview(String records,List<String> replaced,List<String> missing,List<String> preserved,List<String> unsupported){this.records=records;this.replaced=Collections.unmodifiableList(replaced);this.missing=Collections.unmodifiableList(missing);this.preserved=Collections.unmodifiableList(preserved);this.unsupported=Collections.unmodifiableList(unsupported);replaceCount=replaced.size();missingCount=missing.size();preserveCount=preserved.size();unsupportedCount=unsupported.size();}
    }
    public static ApplicationPreview previewApplication(String records,IconManagementRepository.Pack pack,Collection<String> scopes,boolean clearOverrides)throws IOException{
        IconManagementSettings.State before=IconManagementSettings.parse(records);String after=IconManagementSettings.applyPack(records,pack.id,scopes,clearOverrides);
        List<String> replaced=new ArrayList<>(),missing=new ArrayList<>(),preserved=new ArrayList<>(),unsupported=new ArrayList<>();Map<String,IconManagementManifest> manifests=Collections.singletonMap(pack.id,pack.manifest);
        IconManagementSettings.State planned=IconManagementSettings.parse(after);
        for(String scope:scopes)for(IconRoleRegistry.Target target:IconRoleRegistry.targets(scope))for(String role:target.roles){
            if(!target.supported){unsupported.add(role);continue;}if(!clearOverrides&&before.overrides.containsKey(role)){preserved.add(role);continue;}
            if(plan(planned,role,manifests).candidates.isEmpty())missing.add(role);else replaced.add(role);
        }
        return new ApplicationPreview(after,replaced,missing,preserved,unsupported);
    }
    private IconManagementSelection(){ }
}
