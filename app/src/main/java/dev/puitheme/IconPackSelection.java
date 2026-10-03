// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Pure layered role plan. Effective state fallback is resolved before any bitmap is decoded. */
public final class IconPackSelection {
    private IconPackSelection(){ }
    public static final class Pack {
        public final String id;public final IconPackManifest manifest;public final boolean autoMatch;
        public Pack(String id,IconPackManifest manifest){this(id,manifest,true);}
        public Pack(String id,IconPackManifest manifest,boolean autoMatch){this.id=id;this.manifest=manifest;this.autoMatch=autoMatch;}
    }
    public static final class Asset {
        public final String id,path;
        Asset(String id,String path){this.id=id;this.path=path;}
        public String cacheKey(){return id+"/"+path;}
    }
    public static Map<String,List<Asset>> plan(List<Pack> packs){
        Map<String,List<Asset>> plan=new LinkedHashMap<>();
        Set<String> knownHints=new HashSet<>(Arrays.asList(NativeStatusIcons.PRIORITY_SLOTS));
        for(Pack pack:packs){
            if(!pack.autoMatch)continue;
            Map<String,String> effective=new LinkedHashMap<>();Set<String> hints=new TreeSet<>();
            for(String key:pack.manifest.icons.keySet())if(key.startsWith("hint.")){
                String slot=key.substring(5);if(slot.endsWith(".on"))slot=slot.substring(0,slot.length()-3);
                else if(slot.endsWith(".off"))slot=slot.substring(0,slot.length()-4);
                if(knownHints.contains(slot))hints.add(slot);
            }
            for(String slot:hints){effective.put("hint."+slot+".on",pack.manifest.hint(slot,true));effective.put("hint."+slot+".off",pack.manifest.hint(slot,false));}
            for(String family:new String[]{"wifi","cellular"}){
                effective.put(family+".none",pack.manifest.asset(family+".none"));
                for(int level=0;level<=4;level++)effective.put(family+"."+level,pack.manifest.asset(family+"."+level));
            }
            for(int level=0;level<=100;level+=10){effective.put("battery."+level,pack.manifest.battery(level,false));effective.put("battery.charging."+level,pack.manifest.battery(level,true));}
            for(Map.Entry<String,String> entry:effective.entrySet())if(entry.getValue()!=null){
                List<Asset> candidates=plan.get(entry.getKey());if(candidates==null){candidates=new ArrayList<>();plan.put(entry.getKey(),candidates);}
                candidates.add(new Asset(pack.id,entry.getValue()));
            }
        }
        Map<String,List<Asset>> result=new LinkedHashMap<>();
        for(Map.Entry<String,List<Asset>> entry:plan.entrySet())result.put(entry.getKey(),Collections.unmodifiableList(entry.getValue()));
        return Collections.unmodifiableMap(result);
    }
    /** Explicit source artwork precedes the existing ordered, automatic whole-library plan. */
    public static Map<String,List<Asset>> plan(List<Pack> packs,List<IconPackAssignments.Assignment> assignments){
        Map<String,List<Asset>> automatic=plan(packs);Map<String,Pack> libraries=new HashMap<>();
        for(Pack pack:packs)libraries.put(pack.id,pack);
        Map<String,IconPackAssignments.Assignment> selected=new LinkedHashMap<>();Set<String> roles=new LinkedHashSet<>();
        for(IconPackAssignments.Assignment assignment:assignments){
            selected.put(assignment.targetRole,assignment);
            if(assignment.targetRole.startsWith("hint.")&&!assignment.targetRole.endsWith(".on")&&!assignment.targetRole.endsWith(".off")){
                roles.add(assignment.targetRole+".on");roles.add(assignment.targetRole+".off");
            }else roles.add(assignment.targetRole);
        }
        // Under the shared memory budget, user-picked artwork gets the first opportunity;
        // large automatic libraries cannot consume the cache before the explicit choices.
        roles.addAll(automatic.keySet());
        Map<String,List<Asset>> result=new LinkedHashMap<>();
        for(String role:roles){
            IconPackAssignments.Assignment assignment=selected.get(role);
            if(assignment==null&&role.startsWith("hint."))assignment=selected.get(role.replaceFirst("\\.(on|off)$",""));
            List<Asset> candidates=new ArrayList<>();
            if(assignment!=null){
                Pack source=libraries.get(assignment.packId);String path=source==null?null:source.manifest.asset(assignment.sourceRole);
                if(path!=null)candidates.add(new Asset(source.id,path));
            }
            List<Asset> fallback=automatic.get(role);if(fallback!=null)candidates.addAll(fallback);
            if(!candidates.isEmpty())result.put(role,Collections.unmodifiableList(candidates));
        }
        return Collections.unmodifiableMap(result);
    }
    public static final class Decoded<T> {
        public final T value;public final int pixels;
        public Decoded(T value,int pixels){this.value=value;this.pixels=pixels;}
    }
    public interface Decoder<T>{Decoded<T> decode(Asset asset,int remainingPixels)throws Exception;}
    /** Production cache publication shares this tested candidate order, deduplication and budget. */
    public static <T> Map<String,T> resolve(Map<String,List<Asset>> plan,int budget,BooleanSupplier cancelled,Decoder<T> decoder){
        Map<String,T> result=new LinkedHashMap<>(),images=new HashMap<>();Set<String> failed=new HashSet<>();int pixels=0;
        for(Map.Entry<String,List<Asset>> role:plan.entrySet())for(Asset asset:role.getValue()){
            if(cancelled!=null&&cancelled.getAsBoolean())return Collections.emptyMap();
            String key=asset.cacheKey();if(failed.contains(key))continue;T image=images.get(key);
            if(image==null)try{
                Decoded<T> decoded=decoder.decode(asset,Math.max(0,budget-pixels));
                if(decoded!=null&&decoded.value!=null&&decoded.pixels>0&&decoded.pixels<=budget-pixels){
                    image=decoded.value;images.put(key,image);pixels+=decoded.pixels;
                }
            }catch(Exception unavailable){/* A failed asset falls through to the next library, once. */}
            catch(OutOfMemoryError unavailable){/* Remain on bounded/native fallback. */}
            if(image!=null){result.put(role.getKey(),image);break;}failed.add(key);
        }
        return Collections.unmodifiableMap(result);
    }
}
