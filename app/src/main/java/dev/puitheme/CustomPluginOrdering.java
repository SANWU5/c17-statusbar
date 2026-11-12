// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Presentation order never changes execution order, conflict handling or saved plugin settings. */
public final class CustomPluginOrdering {
    public static boolean validMode(String mode) {
        return "custom".equals(mode)||"name".equals(mode)||"installed".equals(mode)
                ||"updated".equals(mode)||"version".equals(mode)||"enabled".equals(mode);
    }
    public static List<CustomPluginRepository.Plugin> sort(List<CustomPluginRepository.Plugin> supplied,
            String mode,boolean ascending,List<String> manual) {
        List<CustomPluginRepository.Plugin> result=new ArrayList<>(supplied);
        Map<String,Integer> ranks=new HashMap<>();
        for(String id:manual)if(!ranks.containsKey(id))ranks.put(id,ranks.size());
        Collator collator=Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        Comparator<CustomPluginRepository.Plugin> comparison=(left,right)->{
            int order;
            switch(mode) {
                case "installed":order=Long.compare(left.installedAt,right.installedAt);break;
                case "updated":order=Long.compare(left.updatedAt,right.updatedAt);break;
                case "version":order=Integer.compare(left.versionCode,right.versionCode);break;
                case "enabled":order=Boolean.compare(left.enabled,right.enabled);break;
                case "name":order=collator.compare(left.name,right.name);break;
                default:order=Integer.compare(ranks.getOrDefault(left.id,Integer.MAX_VALUE),ranks.getOrDefault(right.id,Integer.MAX_VALUE));break;
            }
            return order!=0?order:left.id.compareTo(right.id);
        };
        result.sort(ascending?comparison:comparison.reversed());
        return Collections.unmodifiableList(result);
    }
    private CustomPluginOrdering() { }
}
