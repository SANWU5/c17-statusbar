// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import org.json.JSONObject;

/** Completion protocol is limited to the free-edit catalog action; other actions stay synchronous. */
final class CustomPluginMoreRefresh {
    static final String CATALOG_ACTION="qs_card_more_sort_catalog";
    static boolean matches(String id,String action){return "dev.c17.validation.qs-card".equals(id)&&"qs_card_more_refresh_catalog".equals(action);}
    static long generation(JSONObject before,JSONObject current){
        if(current==null)throw new IllegalStateException("当前插件未提供更新完成状态，请检查插件版本");
        long next=current.optLong("generation",0),old=before==null?0:before.optLong("generation",0);
        if(next<=0||next<old||next==old&&(before==null||!"running".equals(before.optString("state"))))
            throw new IllegalStateException("目录更新未启动，请结束编辑并展开控制中心后重试");
        return next;
    }
    static String terminal(JSONObject current,long generation){
        if(current==null||current.optLong("generation",0)!=generation)
            throw new IllegalStateException("目录更新会话已改变，请重新检查结果");
        String state=current.optString("state");
        if("running".equals(state))return null;
        String fallback;
        switch(state){
            case "succeeded":fallback="磁贴目录已更新，排序和移除选择已保留";break;
            case "failed":fallback="目录更新失败，旧目录保留";break;
            case "cancelled":fallback="目录更新已取消，旧目录保留";break;
            case "timed_out":fallback="目录更新超时，旧目录保留，可稍后重试";break;
            default:throw new IllegalStateException("插件返回的目录更新状态无效，请重新检查结果");
        }
        String message=current.optString("message","").trim();return message.isEmpty()?fallback:message;
    }
    private CustomPluginMoreRefresh(){}
}
