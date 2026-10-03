package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Actual editor overrides and durable private acceptance; no presumed clipboard-origin heuristics. */
public final class FreeNoticeCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Free notice "+checks+": "+expected+" != "+actual);}
    private static final class Store {
        final ActivationGuardPreferencesCheck.MemoryPreferences memory=new ActivationGuardPreferencesCheck.MemoryPreferences();
        final Map<String,Object> disk=new LinkedHashMap<>();
        int failures;
        final SharedPreferences raw=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(owner,method,args)->{
            if(!method.getName().equals("edit"))return method.invoke(memory,args);
            SharedPreferences.Editor nativeEditor=memory.edit();
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(wrapper,operation,parameters)->{
                if(!operation.getName().equals("commit")&&!operation.getName().equals("apply")){operation.invoke(nativeEditor,parameters);return wrapper;}
                nativeEditor.commit();boolean failed=failures>0;if(failed)failures--;
                if(!failed){disk.clear();disk.putAll(memory.values);}
                return operation.getName().equals("apply")?null:!failed;
            });
        });
        Store reopen(){Store result=new Store();result.memory.values.putAll(disk);result.disk.putAll(disk);return result;}
    }
    private static final class Storage extends Context {
        final Map<String,Store> stores=new HashMap<>();
        @Override public SharedPreferences getSharedPreferences(String name,int mode){equal(0,mode);return stores.computeIfAbsent(name,key->new Store()).raw;}
    }
    private static void persistence(){
        Storage context=new Storage();Store runtime=new Store();runtime.memory.values.put("module_safe_mode",true);runtime.disk.putAll(runtime.memory.values);context.stores.put("statusbar_settings",runtime);
        equal(false,FreeNotice.accepted(context));Store notice=context.stores.get("app_free_notice");equal(true,notice!=null);
        int writes=notice.memory.writes;
        for(String invalid:new String[]{null,"",FreeNotice.PHRASE.substring(0,8),FreeNotice.PHRASE.replace("。",".")," "+FreeNotice.PHRASE})equal(false,FreeNotice.accept(context,invalid));
        equal(writes,notice.memory.writes);equal(0,notice.disk.size());
        for(int failures:new int[]{1,2}){
            notice.failures=failures;equal(false,FreeNotice.accept(context,FreeNotice.PHRASE));equal(false,FreeNotice.accepted(context));equal(0,notice.disk.size());equal(false,FreeNotice.accepted(storage(notice.reopen())));
        }
        equal(true,FreeNotice.accept(context,FreeNotice.PHRASE));equal(true,FreeNotice.accepted(context));
        // App restart / upgrade reopens the same private disk store; no runtime-setting export key.
        Storage upgraded=storage(notice.reopen());equal(true,FreeNotice.accepted(upgraded));
        equal(1,notice.disk.size());equal(1,notice.disk.get("accepted_version"));equal(2,context.stores.size());
        equal(true,runtime.memory.values.get("module_safe_mode"));equal(0,runtime.memory.writes);
        for(int n=0;n<1000;n++)equal(true,FreeNotice.accepted(upgraded));
        equal(0,upgraded.stores.get("app_free_notice").memory.writes);
    }
    private static Storage storage(Store notice){Storage result=new Storage();result.stores.put("app_free_notice",notice);return result;}
    private static final class NativeIme implements InputConnection {
        int menus,contents,commits,composing;String value;
        public boolean performContextMenuAction(int action){menus++;return true;}
        public boolean commitContent(InputContentInfo content,int flags,Bundle options){contents++;return true;}
        public boolean commitText(CharSequence text,int cursor){commits++;value=text.toString();return true;}
        public boolean setComposingText(CharSequence text,int cursor){composing++;value=text.toString();return true;}
    }
    private static void input(){
        ManualConfirmationEditText editor=new ManualConfirmationEditText(new Context());
        equal(false,editor.longClickable);equal(false,editor.selectionCallback.onCreateActionMode(null,null));equal(false,editor.insertionCallback.onCreateActionMode(null,null));
        for(int action:new int[]{android.R.id.paste,android.R.id.pasteAsPlainText,android.R.id.copy,android.R.id.cut,android.R.id.shareText})equal(true,editor.onTextContextMenuItem(action));
        equal(0,editor.nativeMenus);equal(false,editor.onTextContextMenuItem(android.R.id.selectAll));equal(1,editor.nativeMenus);
        for(int key:new int[]{KeyEvent.KEYCODE_V,KeyEvent.KEYCODE_C,KeyEvent.KEYCODE_X,KeyEvent.KEYCODE_INSERT}){
            equal(true,editor.onKeyShortcut(key,new KeyEvent(KeyEvent.META_CTRL_ON)));equal(true,editor.onKeyDown(key,new KeyEvent(KeyEvent.META_CTRL_ON)));
        }
        equal(0,editor.nativeShortcuts);equal(0,editor.nativeKeyDowns);
        equal(true,editor.onKeyDown(KeyEvent.KEYCODE_INSERT,new KeyEvent(KeyEvent.META_SHIFT_ON)));equal(true,editor.onKeyDown(KeyEvent.KEYCODE_DEL,new KeyEvent(KeyEvent.META_SHIFT_ON)));
        equal(false,editor.onKeyDown(KeyEvent.KEYCODE_A,new KeyEvent(0)));equal(1,editor.nativeKeyDowns);
        equal(true,editor.onDragEvent(null));equal(0,editor.nativeDrops);editor.autofill(null);equal(0,editor.nativeAutofill);
        for(int action:new int[]{AccessibilityNodeInfo.ACTION_PASTE,AccessibilityNodeInfo.ACTION_COPY,AccessibilityNodeInfo.ACTION_CUT})equal(false,editor.performAccessibilityAction(action,null));
        equal(0,editor.nativeAccessibility);equal(false,editor.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SET_TEXT,null));equal(1,editor.nativeAccessibility);
        equal(null,editor.onCreateInputConnection(new EditorInfo()));NativeIme nativeIme=new NativeIme();editor.inputConnection=nativeIme;
        InputConnection connection=editor.onCreateInputConnection(new EditorInfo());
        for(int action:new int[]{android.R.id.paste,android.R.id.pasteAsPlainText,android.R.id.copy,android.R.id.cut,android.R.id.shareText})equal(true,connection.performContextMenuAction(action));
        equal(0,nativeIme.menus);equal(true,connection.performContextMenuAction(android.R.id.selectAll));equal(1,nativeIme.menus);
        equal(false,connection.commitContent(null,0,null));equal(0,nativeIme.contents);
        equal(true,connection.setComposingText("本模块永久免费",1));equal(1,nativeIme.composing);
        equal(true,connection.commitText("本模块永久免费，捐赠自愿",1));equal(1,nativeIme.commits);equal("本模块永久免费，捐赠自愿",nativeIme.value);
        equal(true,connection.commitText(FreeNotice.PHRASE,1));equal(FreeNotice.PHRASE,nativeIme.value);
    }
    public static void main(String[] args){persistence();input();System.out.println("Free notice checks passed: "+checks);}
}
