// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Process-owned, bounded immutable encoded resources. No View/Context or live background is retained. */
final class ResourceMemoryCache {
    private static final class Entry {
        final byte[] bytes;final boolean core;
        Entry(byte[] value,boolean core){this.bytes=value.clone();this.core=core;}
    }
    private final long limit;
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(32,.75f,true);
    private long bytes,hits,misses,evictions;
    ResourceMemoryCache(long limit){if(limit<0)throw new IllegalArgumentException("cache budget");this.limit=limit;}
    synchronized byte[] get(String key){Entry found=entries.get(key);if(found==null){misses++;return null;}hits++;return found.bytes.clone();}
    synchronized void put(String key,byte[] value,boolean core){
        if(key==null||value==null||value.length==0||value.length>limit)return;
        Entry prior=entries.remove(key);if(prior!=null)bytes-=prior.bytes.length;
        while(bytes+value.length>limit||entries.size()>=256){
            String victim=null;
            for(Map.Entry<String,Entry> e:entries.entrySet())if(!e.getValue().core){victim=e.getKey();break;}
            if(victim==null)victim=entries.keySet().iterator().next();bytes-=entries.remove(victim).bytes.length;evictions++;
        }
        entries.put(key,new Entry(value,core));bytes+=value.length;
    }
    synchronized void trim(boolean severe){
        Iterator<Entry> iterator=entries.values().iterator();
        while(iterator.hasNext()){Entry entry=iterator.next();if(severe||!entry.core){bytes-=entry.bytes.length;iterator.remove();evictions++;}}
    }
    synchronized void removePrefixExcept(String prefix,String keep){
        Iterator<Map.Entry<String,Entry>> iterator=entries.entrySet().iterator();
        while(iterator.hasNext()){Map.Entry<String,Entry> entry=iterator.next();
            if(entry.getKey().startsWith(prefix)&&!entry.getKey().equals(keep)){bytes-=entry.getValue().bytes.length;iterator.remove();}}
    }
    synchronized long bytes(){return bytes;}
    synchronized long hits(){return hits;}
    synchronized long misses(){return misses;}
    synchronized long evictions(){return evictions;}
}
