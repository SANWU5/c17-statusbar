// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Focused groups; real time samples share the same formatter as SystemUI. */
public final class MainActivity extends Activity {
    private int BG=0xfff4f5f7, CARD=Color.WHITE, INK=0xff14171c;
    private int MUTED=0xff858a92, ACCENT=0xff2879ff;
    private int SOFT=0xffeaf2ff, SURFACE=0xfff1f3f6, LINE=0xffedf0f4;
    private boolean darkUi;
    private String featureQuery="";
    private final java.util.Set<String> expandedSections=new java.util.HashSet<>();
    private static final int FONT_REQUEST=17, LOG_EXPORT_REQUEST=18;
    private static final int CONFIG_EXPORT_REQUEST=19, CONFIG_IMPORT_REQUEST=20;
    private static final Uri SETTINGS_URI=Uri.parse(StatusBarSettings.CONTENT_URI);
    private SharedPreferences preferences;
    private SharedPreferences rawPreferences;
    private LinearLayout editor, tabs;
    private LinearLayout bottomNavigation;
    private volatile boolean rootGranted;
    private boolean checkingRoot;
    private RootAccess.Request rootRequest;
    private String rootMessage="授予 Root 后，未激活时也能保存配置";
    private ScrollView pageScroll;
    private TextView sample;
    private TextView bigClockConflictHint;
    private TextView updateSummary;
    private int selected=-1;
    private int navigationPage;
    private int configCategory;
    private String carrierPanel=CarrierPanels.NOTIFICATION;
    private final java.util.Set<String> expandedFeatures=new java.util.HashSet<>();
    private boolean transferringConfig;
    private String pendingImportUri;
    private boolean pendingImportReplayed;
    private boolean resumed, importing, checkingUpdates, checkedUpdatesThisSession;
    private volatile boolean runtimeActive;
    private boolean activationPage=true, restartingSystemUi;
    private String runtimeMessage="正在检测 LSP 模块激活状态…";
    private TextView runtimeStatusText;
    private ModuleRuntimeStatus.ProbeHandle runtimeProbe;
    private SystemUiRestart.Request restartRequest;
    private final java.util.Map<View,Boolean> runtimeUiEnabled=new java.util.WeakHashMap<>();
    private Intent deferredActivityResult;
    private int deferredRequest,deferredResultCode;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean settingsNotificationPending;
    private final Runnable settingsNotification=()->{
        settingsNotificationPending=false;
        if(SettingsSnapshot.notificationAllowed(rawPreferences))getContentResolver().notifyChange(SETTINGS_URI,null);
    };
    private final List<SliderControl> controls=new ArrayList<>();
    private final List<View> qsPreviews=new ArrayList<>();
    private boolean bigClockPreviewDark;
    private float bigClockPreviewCollapse;
    private final Runnable previewTick=() -> { updateSample();for(View preview:qsPreviews)preview.invalidate();schedulePreview(); };
    private final Group[] groups={
        new Group("speed", "实时网速", "保留系统实时数值与 KB/s 单位。", new Setting[]{
            pos("水平位置",StatusBarSettings.SPEED_OFFSET_X,true), pos("垂直位置",StatusBarSettings.SPEED_OFFSET_Y,false),
            scale("整体大小",StatusBarSettings.SPEED_SCALE), scale("数字大小",StatusBarSettings.SPEED_NUMBER_SCALE),
            scale("单位大小",StatusBarSettings.SPEED_UNIT_SCALE),
            new Setting("数字与单位间距",StatusBarSettings.SPEED_LINE_GAP," dp",-8,8,.01f),
            weight(StatusBarSettings.SPEED_WEIGHT)}),
        new Group("data", "蜂窝信号", "信号格数跟随真实系统信号，不填充虚假满格。", new Setting[]{
            pos("水平位置",StatusBarSettings.DATA_OFFSET_X,true),pos("垂直位置",StatusBarSettings.DATA_OFFSET_Y,false),
            scale("图标大小",StatusBarSettings.DATA_ICON_SCALE)}),
        new Group("wifi", "Wi-Fi 图标", "位置、大小与颜色分别设置，透明度跟随原生状态。", new Setting[]{
            pos("水平位置",StatusBarSettings.WIFI_OFFSET_X,true),pos("垂直位置",StatusBarSettings.WIFI_OFFSET_Y,false),
            scale("图标大小",StatusBarSettings.WIFI_ICON_SCALE)}),
        new Group("label", "网络制式文字", "统一显示 5G、4G、3G；Wi-Fi 接通后隐藏。", new Setting[]{
            pos("水平位置",StatusBarSettings.LABEL_OFFSET_X,true),pos("垂直位置",StatusBarSettings.LABEL_OFFSET_Y,false),
            scale("文字大小",StatusBarSettings.LABEL_SCALE),
            new Setting("预留宽度",StatusBarSettings.SLOT_WIDTH," dp",0,80,.01f),weight(StatusBarSettings.FONT_WEIGHT)}),
        new Group("clock", "状态栏时间", "设置状态栏时钟的格式、位置、大小与字距。通知栏与控制中心默认跟随此处设置，也可独立调节。长日期会占用更多空间。", new Setting[]{
            pos("水平位置",StatusBarSettings.CLOCK_OFFSET_X,true),pos("垂直位置",StatusBarSettings.CLOCK_OFFSET_Y,false),
            scale("文字大小",StatusBarSettings.CLOCK_SCALE),weight(StatusBarSettings.CLOCK_WEIGHT),
            new Setting("字距微调",StatusBarSettings.CLOCK_SPACING," dp",-2,8,.01f)}),
        new Group("carrier", "运营商文字", "通知页、控制中心与锁屏分别设置，可选时间或纯文本。", new Setting[]{
            pos("水平位置",StatusBarSettings.CARRIER_OFFSET_X,true),pos("垂直位置",StatusBarSettings.CARRIER_OFFSET_Y,false),
            scale("文字大小",StatusBarSettings.CARRIER_SCALE),weight(StatusBarSettings.CARRIER_WEIGHT),
            new Setting("字距微调",StatusBarSettings.CARRIER_SPACING," dp",-2,8,.01f)}),
        new Group("font", "文字字体", "统一用于时间、下拉替换文字、网络制式和网速。",new Setting[0]),
        new Group("battery", "电池", "默认 PUI 横向样式，跟随真实电量；数字与闪电颜色独立。", new Setting[]{
            pos("水平位置",StatusBarSettings.BATTERY_OFFSET_X,true),pos("垂直位置",StatusBarSettings.BATTERY_OFFSET_Y,false),
            scale("整体大小",StatusBarSettings.BATTERY_SCALE),scale("宽度微调",StatusBarSettings.BATTERY_WIDTH_SCALE),scale("高度微调",StatusBarSettings.BATTERY_HEIGHT_SCALE),
            new Setting("每项停留时间",StatusBarSettings.BATTERY_HOLD," 秒",1,10,.01f),
            new Setting("淡入淡出时长",StatusBarSettings.BATTERY_FADE," 秒",.15f,3,.01f)}),
        new Group("tiles","磁贴翻页渐隐","保留原生左右滑动，只让出入边缘柔和淡出；横竖屏分别启用。",new Setting[]{
            new Setting("左侧渐隐范围","tiles_left_range"," dp",0,192,.01f),
            new Setting("右侧渐隐范围","tiles_right_range"," dp",0,192,.01f),
            new Setting("左侧水平位置","tiles_left_offset_x"," dp",-160,160,.01f),
            new Setting("右侧水平位置","tiles_right_offset_x"," dp",-160,160,.01f),
            new Setting("渐隐区域垂直位置","tiles_offset_y"," dp",-240,240,.01f),
            new Setting("渐隐区域高度","tiles_region_height"," dp",0,1000,.01f),
            new Setting("上下柔和过渡范围","tiles_vertical_feather"," dp",0,192,.01f),
            new Setting("模糊半径（高级选项）","tiles_blur_radius"," dp",0,48,.01f),
            new Setting("渐隐强度","tiles_strength","%",0,100,.01f)}),
        new Group("qs_appearance","磁贴活动调色","全局调整内部活动填充，保留 ColorOS 17 原生玻璃与高光。",new Setting[]{}),
        new Group("shade_clock","通知栏与控制中心时钟","单独设置下拉时钟的格式、位置、大小、粗细、字距与颜色，保留系统展开动画。关闭独立调节后跟随状态栏时间格式与文字样式，位置保留原生布局。",new Setting[]{
            pos("水平位置",StatusBarSettings.SHADE_CLOCK_OFFSET_X,true),pos("垂直位置",StatusBarSettings.SHADE_CLOCK_OFFSET_Y,false),
            scale("文字大小",StatusBarSettings.SHADE_CLOCK_SCALE),weight(StatusBarSettings.SHADE_CLOCK_WEIGHT),
            new Setting("字距微调",StatusBarSettings.SHADE_CLOCK_SPACING," dp",-2,8,.01f)}),
        new Group("notification_big_clock","通知栏大时钟","竖屏通知页显示大日期与时钟，随上滑平滑收起，为通知让出空间。横屏和控制中心继续使用原有时钟。",new Setting[]{
            new Setting("展开时钟大小",NotificationBigClockSettings.SCALE,"%",50,150,.01f),
            new Setting("收起时钟大小",NotificationBigClockSettings.COMPACT_SCALE,"%",15,70,.01f),
            new Setting("时间字体粗细",NotificationBigClockSettings.WEIGHT,"",100,900,1),
            new Setting("整体垂直位置",NotificationBigClockSettings.OFFSET_Y," dp",-80,160,.01f)}),
        new Group("notification_clear","通知清除按钮","独立调整清除按钮的玻璃底色、透明度与渐变，保留原生高光和清除操作。",new Setting[]{}),
        new Group("qs_media","音乐卡片","封面铺满背景，居中裁切并虚化；小封面独立发光，保留系统玻璃与高光。",new Setting[]{})
    };
    private static Setting pos(String title,String key,boolean horizontal) {
        return new Setting(title,key," dp",horizontal?-80:-24,horizontal?80:24,.01f);
    }
    private static Setting scale(String title,String key) { return new Setting(title,key,"%",25,250,.01f); }
    private static Setting weight(String key) { return new Setting("文字粗细",key,"",100,900,1f); }
    private static final class Group {
        final String key,title,detail; final Setting[] settings;
        Group(String key,String title,String detail,Setting[] settings) { this.key=key;this.title=title;this.detail=detail;this.settings=settings; }
    }
    private static final class Setting {
        final String title,key,unit; final float min,max,step;
        Setting(String title,String key,String unit,float min,float max,float step) {
            this.title=title;this.key=key;this.unit=unit;this.min=min;this.max=max;this.step=step;
        }
        float fallback() { return StatusBarSettings.NUMERIC_DEFAULTS.get(key); }
        String format(float number) { return String.format(Locale.ROOT,Math.abs(number)>=1000000f?"%.2e%s":step==1?"%.0f%s":"%.2f%s",number,unit); }
        float round(float number) { return new BigDecimal(Float.toString(number)).setScale(step==1?0:2,RoundingMode.HALF_UP).floatValue(); }
    }
    private final class SliderControl {
        final Setting setting; final SeekBar bar; final TextView value; float current;
        SliderControl(Setting setting,SeekBar bar,TextView value) { this.setting=setting;this.bar=bar;this.value=value; }
        void set(float number,boolean save) {
            current=NotificationBigClockSettings.VISIBLE_COUNT.equals(setting.key)
                    ?NotificationBigClockSettings.visibleCount(number):setting.round(number);
            float slider=Math.max(setting.min,Math.min(setting.max,current));
            bar.setProgress(Math.round((slider-setting.min)/setting.step));
            value.setText(setting.format(current));if(save)saveNumber(setting.key,current);
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);preferences=StatusBarSettings.preferences(this);
        darkUi=(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                ==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if(darkUi) { BG=0xff101010;CARD=0xff202020;INK=0xfff2f2f2;MUTED=0xffaaaaaa;
            ACCENT=0xff75aaff;SOFT=0xff253750;SURFACE=0xff2a2a2a;LINE=0xff393939; }
        if(state!=null&&state.getStringArrayList("expanded_sections")!=null)
            expandedSections.addAll(state.getStringArrayList("expanded_sections"));
        if(state!=null){featureQuery=state.getString("feature_query","");configCategory=Math.max(0,Math.min(2,state.getInt("config_category",0)));}
        SharedPreferences migrations=getSharedPreferences("app_migrations",MODE_PRIVATE);
        if(!migrations.getBoolean("native_edge_fade_114",false)) {
            if(!preferences.contains("tiles_blur_enabled"))preferences.edit().putBoolean("tiles_blur_enabled",false).apply();
            migrations.edit().putBoolean("native_edge_fade_114",true).apply();
        }
        if(state!=null&&state.getStringArrayList("expanded_features")!=null)expandedFeatures.addAll(state.getStringArrayList("expanded_features"));
        if(state!=null)selected=Math.max(-1,Math.min(groups.length-1,state.getInt("group",-1)));
        if(state!=null)navigationPage=Math.max(0,Math.min(2,state.getInt("navigation_page",0)));
        if(state!=null&&CarrierPanels.isPanel(state.getString("carrier_panel")))carrierPanel=state.getString("carrier_panel");
        if(state!=null) {
            deferredActivityResult=state.getParcelable("deferred_activity_result");
            deferredRequest=state.getInt("deferred_request",0);deferredResultCode=state.getInt("deferred_result_code",RESULT_CANCELED);
        }
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(20),dp(6),dp(20),0);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                root.setPadding(dp(20)+safe.left,dp(6)+safe.top,dp(20)+safe.right,safe.bottom);
                updateNavigationInsets(safe.bottom);
            } else {root.setPadding(dp(20),dp(6)+insets.getSystemWindowInsetTop(),dp(20),insets.getSystemWindowInsetBottom());updateNavigationInsets(insets.getSystemWindowInsetBottom());}
            return insets;
        });
        boolean compact=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        tabs=row();tabs.setPadding(dp(6),dp(compact?8:20),dp(6),dp(compact?10:16));root.addView(tabs,matchWrap());
        pageScroll=new ScrollView(this);pageScroll.setFillViewport(false);pageScroll.setVerticalScrollBarEnabled(false);
        editor=new PageColumn();editor.setPadding(0,dp(2),0,dp(108));
        pageScroll.addView(editor,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        FrameLayout content=new FrameLayout(this);content.addView(pageScroll,new FrameLayout.LayoutParams(-1,-1));
        bottomNavigation=new NavigationRow();bottomNavigation.setPadding(dp(5),dp(5),dp(5),dp(5));
        bottomNavigation.setBackground(rounded(CARD,22,false));bottomNavigation.setElevation(dp(2));
        FrameLayout.LayoutParams barParams=new FrameLayout.LayoutParams(-1,dp(72),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        barParams.leftMargin=dp(10);barParams.rightMargin=dp(10);barParams.bottomMargin=dp(10);content.addView(bottomNavigation,barParams);
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().getDecorView().setSystemUiVisibility(darkUi?0:View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        rawPreferences=preferences;
        preferences=new ActivationGuardPreferences(preferences,()->resumed&&canEditConfiguration(),()->handler.post(()->{
            if(resumed&&!canEditConfiguration()&&!isFinishing()&&!isDestroyed()) {
                Toast.makeText(this,"激活模块或授予 Root 后即可保存配置",Toast.LENGTH_SHORT).show();showActivationPage();
            }
        })).withPersistence(this,()->handler.post(()->{
            if(resumed&&!isFinishing()&&!isDestroyed())Toast.makeText(this,"设置保存失败，请稍后重新保存",Toast.LENGTH_LONG).show();
        }));
        selectGroup(selected);
        if(state!=null&&state.getString("pending_import_uri")!=null) {
            pendingImportUri=state.getString("pending_import_uri");
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putStringArrayList("expanded_sections",new ArrayList<>(expandedSections));state.putString("feature_query",featureQuery);state.putInt("config_category",configCategory);
        state.putStringArrayList("expanded_features",new ArrayList<>(expandedFeatures));state.putInt("group",selected);state.putInt("navigation_page",navigationPage);state.putString("carrier_panel",carrierPanel);
        if(pendingImportUri!=null)state.putString("pending_import_uri",pendingImportUri);
        if(deferredActivityResult!=null) {
            state.putParcelable("deferred_activity_result",deferredActivityResult);state.putInt("deferred_request",deferredRequest);state.putInt("deferred_result_code",deferredResultCode);
        }
        super.onSaveInstanceState(state);
    }
    @Override protected void onResume() {
        super.onResume();resumed=true;changed();
        if(!restartingSystemUi)checkRuntimeActivation();
        if(getSharedPreferences("app_migrations",MODE_PRIVATE).getBoolean("root_requested",false)&&!rootGranted)checkRootAccess();
        if(rootGranted&&activationPage)selectGroup(selected);
        if(!checkedUpdatesThisSession) {
            checkedUpdatesThisSession=true;long last=getSharedPreferences("github_update",MODE_PRIVATE).getLong("last_attempt",0);
            if(System.currentTimeMillis()-last>=86400000L)checkUpdates(false);
        }
    }
    @Override protected void onPause() {
        if(settingsNotificationPending) { editor.removeCallbacks(settingsNotification);settingsNotification.run(); }
        SettingsSnapshot.flushPending(this);
        resumed=false;runtimeActive=false;if(!checkingRoot)rootGranted=false;cancelRuntimeProbe();
        handler.removeCallbacks(previewTick);super.onPause();
    }
    @Override protected void onDestroy() {
        cancelRuntimeProbe();if(restartRequest!=null)restartRequest.cancel();
        if(rootRequest!=null)rootRequest.cancel();super.onDestroy();
    }
    @Override public void onBackPressed() { if(selected>=0)selectGroup(-1);else if(navigationPage!=0){navigationPage=0;selectGroup(-1);}else super.onBackPressed(); }
    private void selectGroup(int index) {
        if(index>=0&&!canEditConfiguration()) { selected=index;showActivationPage();return; }
        activationPage=false;runtimeStatusText=null;runtimeUiEnabled.clear();
        int oldScroll=selected==index&&pageScroll!=null?pageScroll.getScrollY():0;
        selected=index;sample=null;updateSummary=null;bigClockConflictHint=null;handler.removeCallbacks(previewTick);tabs.removeAllViews();
        if(index>=0) {
            android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(editor.getWindowToken(),0);
        }
        controls.clear();qsPreviews.clear();editor.removeAllViews();
        renderBottomNavigation();
        if(index<0) { showOverview();pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return; }
        Group group=groups[index];LinearLayout card=column();
        LinearLayout pageHeading=row();pageHeading.setPadding(0,0,0,dp(6));
        TextView back=text("‹",36,INK,false);back.setGravity(Gravity.CENTER);back.setMinHeight(dp(48));
        back.setContentDescription("返回配置列表");back.setOnClickListener(v->selectGroup(-1));pageHeading.addView(back,new LinearLayout.LayoutParams(dp(40),dp(48)));
        TextView pageTitle=text(group.title,21,INK,true);pageTitle.setPadding(dp(8),0,dp(8),0);pageHeading.addView(pageTitle,new LinearLayout.LayoutParams(0,-2,1));
        Switch master=styledSwitch(enabled(masterKey(group.key)));master.setContentDescription(group.title+(group.key.equals("shade_clock")?" 独立调节开关":" 总开关"));
        master.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(masterKey(group.key),checked).apply();changed();selectGroup(selected);});pageHeading.addView(master);tabs.addView(pageHeading,new LinearLayout.LayoutParams(-1,-2));
        card.setPadding(dp(18),dp(10),dp(18),dp(12));card.setBackground(rounded(CARD,22,false));editor.addView(card,matchWrap());
        addHint(card,group.detail);
        if(group.key.equals("shade_clock"))addBigClockConflict(card);
        if(group.key.equals("shade_clock"))addHint(card,enabled(masterKey(group.key))?"独立调节已开启，以下设置作用于通知栏与控制中心时钟。":"当前跟随状态栏时间格式与文字样式，位置保留原生布局。打开右上角开关后，以下独立设置才会生效。");
        else if(!enabled(masterKey(group.key)))addHint(card,"本项已停用，当前使用系统显示。开启后应用以下设置。");
        if(group.key.equals("carrier")) {
            addCarrierEditor(card);finishDetailLayout(card);updateSample();schedulePreview();pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return;
        }
        if(group.key.equals("notification_big_clock")) {
            addBigClockEditor(card,group);finishDetailLayout(card);addReset(group);schedulePreview();pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return;
        }
        if(group.key.equals("qs_appearance")) {
            addQsAppearance(card);finishDetailLayout(card);addReset(group);pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return;
        }
        if(group.key.equals("notification_clear")) {
            addNotificationClearEditor(card);finishDetailLayout(card);addReset(group);pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return;
        }
        if(group.key.equals("qs_media")) {
            addMediaEditor(card);finishDetailLayout(card);addReset(group);pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return;
        }
        addFeaturePanel(card,group);
        if(group.key.equals("tiles")) {
            List<Setting> visibleSettings=new ArrayList<>();for(Setting setting:group.settings)if(!setting.key.equals("tiles_blur_radius")||enabled("tiles_blur_enabled"))visibleSettings.add(setting);
            addSettings(card,new Group(group.key,group.title,group.detail,visibleSettings.toArray(new Setting[0])));addHint(card,"渐隐只作用于磁贴左右边缘，停稳后恢复清晰。区域高度为 0 时使用当前磁贴区域全高；左／右范围为 0 时关闭该侧效果。顶部状态栏不参与处理。");
            addHint(card,"水平位置会将渐隐区域移入或移出磁贴边缘。若渐隐过窄，可先把对应水平位置设为 0，再调整范围。");
            finishDetailLayout(card);addReset(group);pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return;
        }
        if(group.key.equals("font")) { addSection(card,"字体来源");addFonts(card);finishDetailLayout(card);addReset(group);pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return; }
        if(group.key.equals("battery")) {
            addSection(card,"电池样式");addChoice(card,StatusBarSettings.BATTERY_STYLE,new String[]{"pui","native"},new String[]{"PUI 默认样式","系统原生样式"});
            addHint(card,"充电时：镂空闪电 → 电量 → 镂空闪电，平滑淡入淡出。透明切口随切换恢复，闪电颜色自动跟随系统明暗。停止充电后显示原生电量。");
            addSettings(card,group);
            addBatteryColors(card,"battery","电池外形与普通填充");
            addBatteryColors(card,"battery_text","电量数字");
            addBatteryColors(card,"battery_bolt","充电闪电");
            addBatteryColors(card,"battery_charge","充电填充");
            addBatteryColors(card,"battery_alert","低电量／省电填充");
            addHint(card,"数字、闪电、电池外形和填充分别调色，默认保留系统颜色与透明度。浅色、深色背景分别设置；六位色值跟随原生透明度，八位色值可自定义透明度。位置与大小即时应用；透明切口会一起移动和缩放。");
            addHint(card,"位置与大小用于整个电池控件；关闭总开关后恢复系统显示。");
            finishDetailLayout(card);addReset(group);pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return;
        }
        if(group.key.equals("data")) {
            addSection(card,"信号布局");
            addChoice(card,StatusBarSettings.SIGNAL_LAYOUT,new String[]{"system","single"},new String[]{"跟随系统单双排","只显示主信号单排"});
            addHint(card,"单排使用系统提供的主信号强度，双卡次信号仍由系统维护。");
        }
        if(isClockGroup(group.key)) {
            addFormat(card,clockPatternKey(group.key));
        }
        addSettings(card,group);
        addSection(card,"图标与文字颜色");
        LinearLayout colors=row();addColor(colors,group.key+"_color_light","浅色背景",false);
        addColor(colors,group.key+"_color_dark","深色背景",true);card.addView(colors,matchWrap());
        addHint(card,"自动跟随当前界面明暗。六位颜色保留系统透明度，八位颜色可指定透明度。");
        finishDetailLayout(card);addReset(group);updateSample();schedulePreview();pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));
    }
    private void showOverview() {
        boolean compact=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout heading=column();TextView title=text(navigationTitle(navigationPage),pageTitleSize(),INK,true);
        heading.addView(title,matchWrap());TextView subtitle=text(new String[]{"让每一处细节，恰到好处","从状态栏到通知中心，随心调整","更好的 C17 状态栏"}[navigationPage],13,MUTED,false);
        subtitle.setPadding(dp(1),dp(8),0,dp(8));if(!compact)heading.addView(subtitle,matchWrap());
        if(navigationPage==1) {
            EditText search=input();search.setTextSize(14);search.setHint("搜索设置");search.setPadding(dp(17),dp(compact?7:13),dp(17),dp(compact?7:13));search.setBackground(rounded(CARD,18,false));
            search.setInputType(InputType.TYPE_CLASS_TEXT);search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);search.setText(featureQuery);
            LinearLayout.LayoutParams searchParams=matchWrap();searchParams.topMargin=dp(compact?6:14);heading.addView(search,searchParams);
            search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){featureQuery=s.toString();renderOverviewBody();}public void afterTextChanged(Editable s){}});
            LinearLayout categories=row();categories.setPadding(0,dp(compact?4:12),0,0);
            String[] names={"全部","状态栏","通知中心"};
            for(int i=0;i<3;i++) {final int category=i;TextView chip=text(names[i],13,configCategory==i?ACCENT:MUTED,true);
                chip.setGravity(Gravity.CENTER);chip.setPadding(dp(8),dp(compact?6:11),dp(8),dp(compact?6:11));pressable(chip,rounded(configCategory==i?SOFT:Color.TRANSPARENT,14,false));
                chip.setSelected(configCategory==i);chip.setOnClickListener(v->{configCategory=category;selectGroup(-1);});
                categories.addView(chip,new LinearLayout.LayoutParams(0,-2,1));}
            heading.addView(categories,matchWrap());
        }
        tabs.addView(heading,new LinearLayout.LayoutParams(-1,-2));renderOverviewBody();
    }
    private void renderOverviewBody() {
        editor.removeAllViews();updateSummary=null;runtimeStatusText=null;
        if(!featureQuery.trim().isEmpty()&&navigationPage==1) {
            addSection(editor,"搜索结果");LinearLayout results=column();results.setBackground(rounded(CARD,24,false));int found=0;
            String query=featureQuery.trim().toLowerCase(Locale.ROOT);
            for(int i=0;i<groups.length;i++) {
                Group group=groups[i];String haystack=group.title+" "+group.detail+" "+group.key+" "+searchTerms(group.key);
                for(Setting setting:group.settings)haystack+=" "+setting.title;
                for(String[] feature:featureKeys(group.key))haystack+=" "+feature[1];
                if(haystack.toLowerCase(Locale.ROOT).contains(query)) {results.addView(overviewItem(i,group.title,overviewDescription(group.key)),matchWrap());found++;}
            }
            if(found==0)addHint(results,"没有匹配的功能，请换一个关键词。");editor.addView(results,matchWrap());return;
        }
        if(navigationPage==1) {
            if(!canEditConfiguration())addConfigurationAccess(editor);
            if(configCategory!=2)addOverviewSection("状态栏","信号、文字与电池",0,8);
            if(configCategory!=1)addOverviewSection("通知中心","时钟、磁贴与玻璃",8,groups.length);
            addConfigTransfer(editor);
            return;
        }
        if(navigationPage==2) {
            addUpdates(editor);addDiagnostics(editor);
            addHint(editor,"为 ColorOS / OxygenOS 打造\n设计参考 Miuix，保留原生 Android 交互。");return;
        }
        addHomeActivation(editor);
        LinearLayout metrics=row();addOverviewMetric(metrics,"data","状态栏",countEnabled(0,8)+" / 8 项开启",1,false);
        addOverviewMetric(metrics,"bell","通知中心",countEnabled(8,groups.length)+" / "+(groups.length-8)+" 项开启",1,true);
        LinearLayout.LayoutParams metricParams=matchWrap();metricParams.topMargin=dp(14);editor.addView(metrics,metricParams);
        addSection(editor,"常用设置");LinearLayout shortcuts=column();shortcuts.setBackground(rounded(CARD,22,false));addRowDividers(shortcuts);
        for(String key:new String[]{"battery","qs_appearance","notification_big_clock","notification_clear"}) {
            int index=groupIndex(key);
            if(index>=0)shortcuts.addView(overviewItem(index,groups[index].title,overviewDescription(key)),matchWrap());
        }
        editor.addView(shortcuts,matchWrap());
        addSection(editor,"运行与恢复");addRuntimeStatus(editor);addSafetyMode(editor);
        TextView footer=text("C17  /  细节，由你定义",11,MUTED,false);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(25),0,dp(8));editor.addView(footer,matchWrap());
    }
    private String groupGlyph(String key) {
        return isClockGroup(key)||key.equals("notification_big_clock")?"clock":key;
    }
    private String searchTerms(String group) {
        switch(group) {
            case "notification_big_clock":return "效果预览 时间格式 时间字体 文字填充 颜色与透明度 玻璃边框 玻璃边框宽度 下拉入口过渡 入场移动距离 入口模糊半径 入口渐显强度 清晰显示的下拉进度 日期格式 日期模板 星期 农历 干支 日期字号 日期字体粗细 日期水平位置 日期垂直位置 日期与时间间距 展开水平位置 展开垂直位置 收起水平位置 收起垂直位置 右上状态图标固定原位 时钟与通知安全间距 时间与通知间距 完整通知条数 堆叠 三层 第一层底边宽度 第二层底边宽度 第三层底边宽度 通知边缘过渡 安全距离 渐隐范围 模糊半径 页脚 纯文本 表情";
            case "qs_media":return "音乐 播放 媒体 专辑 封面背景 背景透明度 背景模糊度 反色发光 小封面外发光 自定义发光颜色 发光透明度 发光模糊半径 发光扩散距离 浅色界面 深色界面";
            case "qs_appearance":return "活动填充颜色 渐变终点颜色 启用渐变 填充透明度 渐变方向 音量 亮度";
            case "notification_clear":return "清除 清空 清楚 玻璃底色 渐变终点颜色 底色透明度 渐变方向";
            case "battery":return "PUI 系统原生 电量数字 电池外形 普通填充 充电闪电 充电填充 低电量 省电 电池内文本 透明 镂空";
            case "font":return "字体来源 跟随系统 苹方 自选 导入 字体文件 粗细";
            case "carrier":return "通知页 控制中心 锁屏 编辑文本 替换 运营商 自定义时间 纯文本 时间格式 年月日 星期 农历 上午 下午 凌晨";
            case "clock":case "shade_clock":return "时间格式 时间模板 日期 星期 农历 上午 下午 凌晨 小时 分钟 秒";
            default:return "颜色 透明度 浅色 深色";
        }
    }
    private void cancelRuntimeProbe() {
        if(runtimeProbe!=null) { runtimeProbe.cancel();runtimeProbe=null; }
    }
    private void checkRuntimeActivation() {
        cancelRuntimeProbe();runtimeActive=false;
        runtimeProbe=ModuleRuntimeStatus.probe(this,result->{
            if(!resumed||isFinishing()||isDestroyed()||restartingSystemUi)return;
            runtimeActive=result.active;
            renderBottomNavigation();
            runtimeMessage=result.active?"LSP 已激活 · "+result.frameworkName+" "+result.frameworkVersion:result.message;
            if(result.state==ModuleRuntimeStatus.State.CHECKING) {
                if(selected>=0&&!canEditConfiguration())setEditingEnabled(editor,false);
                if(runtimeStatusText!=null)runtimeStatusText.setText(runtimeMessage);
                if(selected<0&&navigationPage==0)renderOverviewBody();
                return;
            }
            if(canEditConfiguration()) {
                if(activationPage)selectGroup(selected);else setEditingEnabled(editor,true);
                if(selected<0&&navigationPage==0)renderOverviewBody();
                if(runtimeStatusText!=null)runtimeStatusText.setText(runtimeMessage);
                replayPendingResult();
            } else if(selected>=0)showActivationPage();else if(navigationPage==0||navigationPage==1)renderOverviewBody();
        });
    }
    private void setEditingEnabled(View view,boolean enabled) {
        if(!enabled) { if(!runtimeUiEnabled.containsKey(view))runtimeUiEnabled.put(view,view.isEnabled());view.setEnabled(false); }
        else { Boolean original=runtimeUiEnabled.remove(view);if(original!=null)view.setEnabled(original); }
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)setEditingEnabled(group.getChildAt(i),enabled);
        }
    }
    private void showActivationPage() {
        activationPage=true;sample=null;runtimeStatusText=null;controls.clear();qsPreviews.clear();runtimeUiEnabled.clear();
        handler.removeCallbacks(previewTick);tabs.removeAllViews();editor.removeAllViews();editor.setEnabled(true);
        renderBottomNavigation();
        tabs.addView(text("运行状态",pageTitleSize(),INK,true));
        addRuntimeStatus(editor);
        addConfigurationAccess(editor);
        addHint(editor,"模块激活后配置即时生效。未激活时，可授予 Root 预先配置或调整安全模式。");
        pageScroll.post(()->pageScroll.scrollTo(0,0));
    }
    private void addRuntimeStatus(LinearLayout parent) {
        LinearLayout card=column();card.setPadding(dp(18),dp(18),dp(18),dp(14));card.setBackground(rounded(CARD,22,false));
        LinearLayout heading=row();heading.addView(text("模块与权限",16,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(badge(runtimeActive?"已激活":"未激活",runtimeActive?ACCENT:MUTED,SOFT));card.addView(heading,matchWrap());
        runtimeStatusText=text(runtimeMessage,12,MUTED,false);runtimeStatusText.setPadding(0,dp(9),0,dp(12));card.addView(runtimeStatusText,matchWrap());
        TextView rootStatus=text(rootGranted?"Root 已授权 · 可独立保存配置":rootMessage,12,rootGranted?ACCENT:MUTED,false);card.addView(rootStatus,matchWrap());
        LinearLayout actions=row();TextView check=navigationChip("重新检测"),restart=navigationChip(restartingSystemUi?"重启中…":"重启系统 UI");
        check.setOnClickListener(v->{if(!restartingSystemUi)checkRuntimeActivation();});restart.setOnClickListener(v->restartSystemUi());
        check.setEnabled(!restartingSystemUi);restart.setEnabled(!restartingSystemUi);
        check.setContentDescription("重新检测 LSP 模块激活状态");restart.setContentDescription("使用 Root 重启系统 UI");
        addNavigationChip(actions,check,false);addNavigationChip(actions,restart,true);card.addView(actions,matchWrap());
        if(!rootGranted)addAction(card,checkingRoot?"正在申请 Root…":"授予 Root 权限","未激活 LSP 时也能修改和保存配置",()->{if(!checkingRoot)checkRootAccess();});
        addHint(card,"重启需要 Root，系统界面会短暂重载。");
        LinearLayout.LayoutParams p=matchWrap();p.bottomMargin=dp(4);parent.addView(card,p);
    }
    private void restartSystemUi() {
        if(restartingSystemUi)return;
        restartingSystemUi=true;runtimeActive=false;cancelRuntimeProbe();runtimeMessage="正在重启系统 UI…";showActivationPage();
        restartRequest=SystemUiRestart.restart(result->{
            restartingSystemUi=false;restartRequest=null;
            if(isFinishing()||isDestroyed())return;
            runtimeMessage=result.message;Toast.makeText(this,result.message,Toast.LENGTH_LONG).show();showActivationPage();
            if(resumed)handler.postDelayed(()->{if(resumed&&!restartingSystemUi)checkRuntimeActivation();},result.success?1800L:0L);
        });
    }
    private boolean requireRuntimeActive() {
        if(canEditConfiguration())return true;
        Toast.makeText(this,"激活模块或授予 Root 后即可保存配置",Toast.LENGTH_SHORT).show();showActivationPage();return false;
    }
    private boolean canEditConfiguration() { return runtimeActive||rootGranted; }
    private void checkRootAccess() {
        if(checkingRoot)return;checkingRoot=true;
        getSharedPreferences("app_migrations",MODE_PRIVATE).edit().putBoolean("root_requested",true).apply();
        rootMessage="正在检查 Root 授权…";
        if(selected<0&&navigationPage==0)renderOverviewBody();
        rootRequest=RootAccess.check(result->{
            checkingRoot=false;rootRequest=null;if(isFinishing()||isDestroyed())return;
            rootGranted=result.granted;rootMessage=result.message;
            if(!resumed)return;
            if(rootGranted&&activationPage)selectGroup(selected);
            else if(selected<0)selectGroup(-1);
            if(rootGranted)replayPendingResult();
            if(!rootGranted)Toast.makeText(this,result.message,Toast.LENGTH_LONG).show();
        });
    }
    private void replayPendingResult() {
        if(!canEditConfiguration())return;
        if(deferredActivityResult!=null) {
            Intent deferred=deferredActivityResult;deferredActivityResult=null;
            onActivityResult(deferredRequest,deferredResultCode,deferred);
        } else if(pendingImportUri!=null&&!pendingImportReplayed&&!transferringConfig) {
            pendingImportReplayed=true;transferConfig(CONFIG_IMPORT_REQUEST,Uri.parse(pendingImportUri));
        }
    }
    private void addConfigurationAccess(LinearLayout parent) {
        LinearLayout access=column();access.setPadding(dp(18),dp(14),dp(18),dp(14));access.setBackground(rounded(CARD,22,false));
        access.addView(text("配置权限",16,INK,true));addHint(access,"激活模块或授予 Root 后即可保存配置。现有设置会保留。");
        addAction(access,checkingRoot?"正在申请 Root…":"授予 Root 权限",rootMessage,()->{if(!checkingRoot)checkRootAccess();});
        parent.addView(access,matchWrap());
    }
    private void addHomeActivation(LinearLayout parent) {
        LinearLayout card=row();card.setPadding(dp(16),dp(18),dp(16),dp(18));card.setBackground(rounded(CARD,22,false));
        ImageView logo=new ImageView(this);logo.setImageResource(getResources().getIdentifier("ic_c17_launcher","mipmap",getPackageName()));
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout copy=column();copy.setPadding(dp(12),0,dp(8),0);
        copy.addView(text(enabled(StatusBarSettings.SAFE_MODE)?"安全模式已开启":runtimeActive?"模块已激活":rootGranted?"Root 配置已就绪":"模块待激活",17,INK,true));
        String detail=enabled(StatusBarSettings.SAFE_MODE)?"效果已暂停，配置完整保留":runtimeActive?runtimeMessage.replace("LSP 已激活 · ",""):rootGranted?"可保存设置，激活后应用":"在 LSP 中启用并勾选 SystemUI";
        TextView subtitle=text(detail,12,MUTED,false);subtitle.setPadding(0,dp(6),0,0);copy.addView(subtitle);card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(badge(enabled(StatusBarSettings.SAFE_MODE)?"已暂停":runtimeActive?"已激活":rootGranted?"已授权":"待激活",ACCENT,SOFT));parent.addView(card,matchWrap());
    }
    private void addSafetyMode(LinearLayout parent) {
        LinearLayout card=column();card.setPadding(dp(18),dp(12),dp(18),dp(14));card.setBackground(rounded(CARD,22,false));
        LinearLayout line=row();line.setMinimumHeight(dp(48));line.addView(text("安全模式",16,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=styledSwitch(enabled(StatusBarSettings.SAFE_MODE));toggle.setContentDescription("安全模式开关");
        toggle.setOnCheckedChangeListener((button,value)->{if(!requireRuntimeActive())return;
            preferences.edit().putBoolean(StatusBarSettings.SAFE_MODE,value).apply();changed();selectGroup(-1);});
        line.addView(toggle);card.addView(line,matchWrap());addHint(card,"暂停模块的界面效果，保留全部配置。关闭后恢复；异常时可重启系统 UI。");
        LinearLayout.LayoutParams p=matchWrap();p.topMargin=dp(12);parent.addView(card,p);
    }
    private View addOverviewSection(String title,String detail,int start,int end) {
        LinearLayout heading=row();heading.setPadding(dp(3),dp(23),dp(3),dp(10));
        heading.addView(text(title,14,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));heading.addView(text(detail,12,MUTED,false));editor.addView(heading,matchWrap());
        boolean wide=getResources().getConfiguration().screenWidthDp>=600;
        if(wide) {
            for(int i=start;i<end;i+=2) {
                LinearLayout pair=row();
                for(int j=i;j<Math.min(i+2,end);j++) {
                    LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);if(j>i)cell.leftMargin=dp(10);
                    pair.addView(overviewItem(j,groups[j].title,overviewDescription(groups[j].key)),cell);
                }
                LinearLayout.LayoutParams p=matchWrap();p.topMargin=dp(8);editor.addView(pair,p);
            }
        } else {
            LinearLayout section=column();section.setBackground(rounded(CARD,22,false));addRowDividers(section);
            for(int i=start;i<end;i++) {
                section.addView(overviewItem(i,groups[i].title,overviewDescription(groups[i].key)),matchWrap());
            }
            LinearLayout.LayoutParams p=matchWrap();p.topMargin=dp(5);editor.addView(section,p);
        }
        return heading;
    }
    private LinearLayout overviewItem(int index,String title,String description) {
        final Group group=groups[index];LinearLayout line=row();line.setPadding(dp(16),dp(14),dp(14),dp(14));line.setMinimumHeight(dp(78));
        UiGlyph icon=new UiGlyph(this,groupGlyph(group.key),INK);line.addView(icon,new LinearLayout.LayoutParams(dp(26),dp(26)));
        LinearLayout copy=column();copy.setPadding(dp(14),0,dp(8),0);copy.addView(text(title,16,INK,true));
        TextView detail=text(description,12,MUTED,false);detail.setPadding(0,dp(6),0,0);copy.addView(detail,matchWrap());line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Switch master=styledSwitch(enabled(masterKey(group.key)));master.setContentDescription(group.title+(group.key.equals("shade_clock")?" 独立调节开关":" 总开关"));
        master.setEnabled(canEditConfiguration());master.setAlpha(canEditConfiguration()?1f:.4f);
        master.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(masterKey(group.key),checked).apply();changed();selectGroup(-1);});line.addView(master);
        TextView arrow=text("›",22,MUTED,false);arrow.setPadding(dp(9),0,0,0);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);line.addView(arrow);
        line.setContentDescription("打开"+group.title);pressable(line,rounded(CARD,20,false));line.setOnClickListener(v->selectGroup(index));return line;
    }
    private String overviewDescription(String key) {
        switch(key) {
            case "speed":return "数字、单位与显示位置";
            case "data":return "信号样式、单排与系统角标";
            case "wifi":return "图标样式、大小与活动箭头";
            case "label":return "统一 5G、4G、3G 显示";
            case "clock":return "时间格式、大小与字距";
            case "carrier":return "通知页、控制中心与锁屏文字";
            case "font":return "系统、苹方或自己的字体";
            case "battery":return "电池样式、充电切换与颜色";
            case "tiles":return "横竖屏翻页与边缘渐隐";
            case "qs_appearance":return "填充调色、渐变与原生玻璃";
            case "shade_clock":return enabled(masterKey(key))?"独立调节下拉时钟":"跟随状态栏，可独立调节";
            case "notification_big_clock":return "竖屏大时钟，随通知收起";
            case "notification_clear":return "独立玻璃调色，保留清除操作";
            case "qs_media":return "封面背景、模糊与反色发光";
            default:return "显示样式与位置";
        }
    }
    private int groupIndex(String key) { for(int i=0;i<groups.length;i++)if(groups[i].key.equals(key))return i;return -1; }
    private int countEnabled(int start,int end) { int count=0;for(int i=start;i<end;i++)if(enabled(masterKey(groups[i].key)))count++;return count; }
    private void addOverviewMetric(LinearLayout parent,String glyph,String title,String detail,int page,boolean margin) {
        LinearLayout metric=column();metric.setPadding(dp(17),dp(18),dp(17),dp(18));pressable(metric,rounded(CARD,24,false));
        LinearLayout heading=row();UiGlyph icon=new UiGlyph(this,glyph,ACCENT);heading.addView(icon,new LinearLayout.LayoutParams(dp(22),dp(22)));
        TextView name=text(title,14,MUTED,false);name.setPadding(dp(8),0,0,0);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));metric.addView(heading,matchWrap());
        TextView value=text(detail,14,ACCENT,true);value.setPadding(0,dp(12),0,0);metric.addView(value,matchWrap());
        metric.setContentDescription("打开"+title+"，"+detail);metric.setOnClickListener(v->{configCategory=glyph.equals("data")?1:2;openNavigationPage(page);});
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(margin)params.leftMargin=dp(12);parent.addView(metric,params);
    }
    private TextView badge(String label,int color,int background) {
        TextView badge=text(label,11,color,true);badge.setGravity(Gravity.CENTER);badge.setPadding(dp(9),dp(6),dp(9),dp(6));badge.setBackground(rounded(background,10,false));return badge;
    }
    private TextView navigationChip(String label) {
        TextView chip=text(label,13,ACCENT,true);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(8),dp(12),dp(8),dp(12));chip.setMinHeight(dp(48));pressable(chip,rounded(SOFT,13,false));return chip;
    }
    private void addNavigationChip(LinearLayout parent,TextView chip,boolean margin) {
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(margin)params.leftMargin=dp(8);parent.addView(chip,params);
    }
    private void addActivationStep(LinearLayout parent,String number,String label) {
        LinearLayout step=row();step.setPadding(0,dp(4),0,dp(7));TextView index=badge(number,ACCENT,SOFT);index.setMinWidth(dp(28));
        step.addView(index,new LinearLayout.LayoutParams(dp(28),dp(28)));TextView copy=text(label,13,INK,false);copy.setPadding(dp(10),0,0,0);step.addView(copy,new LinearLayout.LayoutParams(0,-2,1));parent.addView(step,matchWrap());
    }
    private String navigationTitle(int page) { return new String[]{"主页","配置","关于"}[page]; }
    private int pageTitleSize() { return getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?28:36; }
    private void openNavigationPage(int page) {
        navigationPage=page;selected=-1;selectGroup(-1);pageScroll.post(()->pageScroll.scrollTo(0,0));
    }
    private void renderBottomNavigation() {
        if(bottomNavigation==null)return;bottomNavigation.removeAllViews();
        for(int i=0;i<3;i++) {
            final int page=i;boolean active=page==navigationPage;int color=active?ACCENT:MUTED;
            LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(dp(3),dp(5),dp(3),dp(5));
            item.addView(new NavigationGlyph(page,color),new LinearLayout.LayoutParams(dp(22),dp(22)));
            TextView label=text(navigationTitle(page),11,color,true);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(5),0,0);label.setSingleLine(true);item.addView(label);
            item.setContentDescription(navigationTitle(page));item.setSelected(active);
            item.setOnClickListener(v->openNavigationPage(page));bottomNavigation.addView(item,new LinearLayout.LayoutParams(0,-1,1));
        }
    }
    private void updateNavigationInsets(int bottomInset) {
        if(bottomNavigation==null||!(bottomNavigation.getLayoutParams() instanceof FrameLayout.LayoutParams))return;
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)bottomNavigation.getLayoutParams();
        int margin=dp(8); // System navigation inset is already outside this content container.
        if(params.bottomMargin!=margin){params.bottomMargin=margin;bottomNavigation.setLayoutParams(params);}
    }
    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; }
        catch(android.content.pm.PackageManager.NameNotFoundException unavailable) { return "1.6.1"; }
    }
    private void addQsAppearance(LinearLayout card) {
        addHint(card,"统一应用于 Wi-Fi、数据和其他活动磁贴，以及亮度、音量滑条的已填充部分。");
        LinearLayout previews=row();previews.setPadding(0,dp(8),0,0);
        for(String scene:new String[]{"light","dark"}) {
            LinearLayout sample=column();TextView label=text(scene.equals("dark")?"深色背景":"浅色背景",11,MUTED,true);
            label.setPadding(dp(3),0,0,dp(7));sample.addView(label,matchWrap());
            View preview=qsPreview(scene);qsPreviews.add(preview);sample.addView(preview,new LinearLayout.LayoutParams(-1,dp(84)));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(scene.equals("dark"))p.leftMargin=dp(8);previews.addView(sample,p);
        }
        card.addView(previews,matchWrap());
        addHint(card,"效果示意 · 实际控件沿用系统材质。浅色与深色设置随系统主题自动切换。");
        addSection(card,"活动填充颜色");LinearLayout colors=row();
        addColor(colors,"qs_global_light_color","浅色背景",false);addColor(colors,"qs_global_dark_color","深色背景",true);card.addView(colors,matchWrap());
        addSection(card,"渐变终点颜色");LinearLayout gradientColors=row();
        addColor(gradientColors,"qs_global_light_gradient_color","浅色背景",false);addColor(gradientColors,"qs_global_dark_gradient_color","深色背景",true);card.addView(gradientColors,matchWrap());
        addToggle(card,"qs_global_light_gradient_enabled","浅色背景 · 启用渐变");
        addToggle(card,"qs_global_dark_gradient_enabled","深色背景 · 启用渐变");
        addSettings(card,new Group("qs_appearance","","",new Setting[]{
            new Setting("浅色背景 · 填充透明度","qs_global_light_opacity","%",0,100,.01f),
            new Setting("深色背景 · 填充透明度","qs_global_dark_opacity","%",0,100,.01f),
            new Setting("浅色背景 · 渐变方向","qs_global_light_gradient_angle","°",0,360,.01f),
            new Setting("深色背景 · 渐变方向","qs_global_dark_gradient_angle","°",0,360,.01f)}));
        addHint(card,"填充透明度与颜色透明度相乘，并跟随系统原有的透明度变化。渐变方向：0° 从左到右，90° 从上到下。");
    }
    private void addMediaEditor(LinearLayout card) {
        addSection(card,"显示效果");
        addHint(card,"当前音乐封面会居中铺满整个卡片，按比例裁切。原来的小封面、标题、播放按钮和玻璃高光继续保留。");
        addToggle(card,QsMediaAppearance.BACKGROUND,"使用封面作为卡片背景");
        addToggle(card,QsMediaAppearance.GLOW,"小封面外发光");
        for(String scene:new String[]{"light","dark"}) {
            String key=QsMediaAppearance.prefix(scene);boolean dark=scene.equals("dark");
            addSection(card,dark?"深色界面":"浅色界面");
            addSlider(card,new Setting("背景透明度",key+"_background_opacity","%",0,100,.01f));
            addSlider(card,new Setting("背景模糊度",key+"_background_blur"," dp",0,48,.01f));
            addToggle(card,key+"_glow_inverse_enabled","发光使用封面反色");
            if(!enabled(key+"_glow_inverse_enabled")) {
                LinearLayout colors=row();addColor(colors,key+"_glow_color","自定义发光颜色",false);card.addView(colors,matchWrap());
            }
            addSlider(card,new Setting("发光透明度",key+"_glow_opacity","%",0,100,.01f));
            addSlider(card,new Setting("发光模糊半径",key+"_glow_radius"," dp",0,48,.01f));
            addSlider(card,new Setting("发光扩散距离",key+"_glow_spread"," dp",0,24,.01f));
        }
        addHint(card,"浅色与深色配置随系统界面切换。没有可用封面时保留系统背景；关闭总开关恢复原生音乐卡片。");
    }
    private void addBigClockConflict(LinearLayout card) {
        bigClockConflictHint=addHint(card,"竖屏由大时钟接管，修改此页将自动关闭大时钟；横屏和控制中心保留。");
        bigClockConflictHint.setVisibility(enabled(NotificationBigClockSettings.MASTER)?View.VISIBLE:View.GONE);
    }
    private void addNotificationClearEditor(LinearLayout card) {
        addHint(card,"此开关独立于大时钟。只调整系统清除按钮的玻璃底色，保留按钮形状、高光、图标、按压效果与清除功能；关闭后恢复原生绘制。");
        LinearLayout previews=row();previews.setPadding(0,dp(8),0,0);
        for(String scene:new String[]{"light","dark"}) {
            LinearLayout sample=column();TextView label=text(scene.equals("dark")?"深色背景":"浅色背景",11,MUTED,true);
            label.setPadding(dp(3),0,0,dp(7));sample.addView(label,matchWrap());
            View preview=notificationClearPreview(scene.equals("dark"));qsPreviews.add(preview);sample.addView(preview,new LinearLayout.LayoutParams(-1,dp(84)));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(scene.equals("dark"))params.leftMargin=dp(8);previews.addView(sample,params);
        }
        card.addView(previews,matchWrap());addHint(card,"效果示意 · 原生按钮材质与清除图标由系统保留，自动跟随浅色、深色主题。");
        addSection(card,"按钮底色 · 颜色与透明度");
        addBigClockColors(card,NotificationClearAppearance.COLOR_LIGHT,NotificationClearAppearance.COLOR_DARK);
        addToggle(card,NotificationClearAppearance.GRADIENT_ENABLED,"启用渐变");
        addSection(card,"渐变终点 · 颜色与透明度");
        addBigClockColors(card,NotificationClearAppearance.GRADIENT_COLOR_LIGHT,NotificationClearAppearance.GRADIENT_COLOR_DARK);
        addBigClockNumbers(card,new Setting("底色透明度",NotificationClearAppearance.OPACITY,"%",0,100,.01f),
                new Setting("渐变方向",NotificationClearAppearance.GRADIENT_ANGLE,"°",0,360,.01f));
        addHint(card,"透明度仅调整玻璃底色，并与 HSB 中的颜色透明度相乘；原有高光和清除图标不随底色消失。0° 从左到右，90° 从上到下。");
    }
    private View notificationClearPreview(boolean dark) {
        return new View(this) {
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                android.graphics.RectF bounds=new android.graphics.RectF(dp(1),dp(1),getWidth()-dp(1),getHeight()-dp(1));
                paint.setShader(new android.graphics.LinearGradient(0,0,getWidth(),getHeight(),dark?0xff253347:0xffe5edf8,dark?0xff414b60:0xffcdd8eb,android.graphics.Shader.TileMode.CLAMP));
                canvas.drawRoundRect(bounds,dp(18),dp(18),paint);paint.setShader(null);
                float diameter=Math.min(dp(48),Math.min(getWidth()-dp(28),getHeight()-dp(24))),cx=getWidth()/2f,cy=getHeight()/2f;
                android.graphics.RectF button=new android.graphics.RectF(cx-diameter/2,cy-diameter/2,cx+diameter/2,cy+diameter/2);
                paint.setColor(dark?0x307b8496:0x70f6f7fa);canvas.drawOval(button,paint);
                if(enabled(NotificationClearAppearance.MASTER)) {
                    paint.setShader(NotificationClearAppearance.previewShader(preferences.getAll(),dark,new android.graphics.Rect(Math.round(button.left),Math.round(button.top),Math.round(button.right),Math.round(button.bottom))));
                    canvas.drawOval(button,paint);paint.setShader(null);
                }
                paint.setShader(new android.graphics.LinearGradient(0,button.top,0,button.bottom,0x60ffffff,0x08ffffff,android.graphics.Shader.TileMode.CLAMP));
                paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(dp(1));canvas.drawOval(button,paint);paint.setShader(null);
                paint.setColor(dark?0xfff5f5f5:0xff46505e);paint.setStrokeWidth(dp(1.7f));paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                float r=diameter*.12f;canvas.drawLine(cx-r,cy-r,cx+r,cy+r,paint);canvas.drawLine(cx+r,cy-r,cx-r,cy+r,paint);
            }
        };
    }
    private void addBigClockEditor(LinearLayout card,Group group) {
        addHint(card,"开启后仅接管竖屏通知页。原有时钟和运营商设置会保留；修改对应原生设置会自动关闭大时钟。");
        addSection(card,"效果预览");BigClockPreview preview=new BigClockPreview();qsPreviews.add(preview);
        LinearLayout scenes=row();TextView light=navigationChip("浅色背景"),dark=navigationChip("深色背景");
        Runnable sceneStyle=()->{light.setTextColor(preview.dark?MUTED:ACCENT);dark.setTextColor(preview.dark?ACCENT:MUTED);light.setSelected(!preview.dark);dark.setSelected(preview.dark);};
        light.setOnClickListener(v->{bigClockPreviewDark=preview.dark=false;sceneStyle.run();preview.invalidate();});dark.setOnClickListener(v->{bigClockPreviewDark=preview.dark=true;sceneStyle.run();preview.invalidate();});
        addNavigationChip(scenes,light,false);addNavigationChip(scenes,dark,true);sceneStyle.run();card.addView(scenes,matchWrap());
        LinearLayout.LayoutParams previewParams=new LinearLayout.LayoutParams(-1,dp(400));previewParams.topMargin=dp(10);previewParams.gravity=Gravity.CENTER_HORIZONTAL;
        card.addView(preview,previewParams);
        LinearLayout labels=row();labels.setPadding(dp(2),dp(12),dp(2),0);labels.addView(text("展开",12,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));labels.addView(text("收起",12,MUTED,false));card.addView(labels,matchWrap());
        SeekBar progress=new SeekBar(this);progress.setMax(100);progress.setProgress(Math.round(preview.collapse*100));progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));progress.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffdddddd));progress.setContentDescription("预览大时钟收起进度");
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){bigClockPreviewCollapse=preview.collapse=value/100f;preview.invalidate();}
        });card.addView(progress,new LinearLayout.LayoutParams(-1,dp(44)));
        addHint(card,"预览保留顶部状态图标。原生字体通过自身字形变化收起；其他字体或无法读取高度变化的原生字体按自然比例缩小。实际通知卡片仍由系统管理。");
        addSection(card,"时间");
        addAction(card,"时间格式",string(NotificationBigClockSettings.PATTERN),()->editFormat(NotificationBigClockSettings.PATTERN));
        addAction(card,"常用时间格式","时分、秒、上午下午与时段",()->chooseFormat(NotificationBigClockSettings.PATTERN));
        addHint(card,"字体只用于通知栏大时钟、日期和底部内容。自选字体使用“文字字体”中已经导入的文件。");
        addChoice(card,NotificationBigClockSettings.FONT,new String[]{"native","system","pingfang","custom"},new String[]{"原生","系统","苹方","自选"});
        addAction(card,"管理自选字体",string(StatusBarSettings.FONT_NAME),()->selectGroup(6));
        addBigClockNumbers(card,
                new Setting("展开字号上限",NotificationBigClockSettings.MAX_SIZE," dp",48,240,.01f),
                new Setting("展开大小比例",NotificationBigClockSettings.SCALE,"%",25,200,.01f),
                new Setting("收起字高上限",NotificationBigClockSettings.COMPACT_MAX_SIZE," dp",12,96,.01f),
                new Setting("收起字高比例",NotificationBigClockSettings.COMPACT_SCALE,"%",10,100,.01f),
                new Setting("时间字体粗细",NotificationBigClockSettings.WEIGHT,"",100,900,1),
                new Setting("时间字距",NotificationBigClockSettings.LETTER_SPACING," em",-.15f,.5f,.01f));
        addHint(card,"展开与收起使用同一粗细，滑动只改变高度与布局。");
        addSection(card,"时间颜色与玻璃边框");
        addToggle(card,NotificationBigClockSettings.GLASS,"液态玻璃文字");
        addHint(card,"填充与边框分别调色。点击浅色或深色卡片，进入 HSB 调色盘调整颜色与透明度；默认跟随原生透明度。");
        addSection(card,"时间填充 · 颜色与透明度");
        addBigClockColors(card,NotificationBigClockSettings.COLOR_LIGHT,NotificationBigClockSettings.COLOR_DARK);
        addToggle(card,NotificationBigClockSettings.GLASS_BORDER_ENABLED,"显示玻璃边框");
        addSlider(card,new Setting("玻璃边框宽度",NotificationBigClockSettings.GLASS_BORDER_WIDTH," dp",0,4,.01f));
        addSection(card,"玻璃边框 · 颜色与透明度");
        addBigClockColors(card,NotificationBigClockSettings.GLASS_BORDER_COLOR_LIGHT,NotificationBigClockSettings.GLASS_BORDER_COLOR_DARK);

        addSection(card,"下拉入口过渡");
        addToggle(card,NotificationBigClockSettings.ENTRY_EFFECT_ENABLED,"随下拉手势移动、模糊与渐显");
        addBigClockNumbers(card,new Setting("入场移动距离",NotificationBigClockSettings.ENTRY_TRAVEL," dp",0,120,.01f),
                new Setting("入口模糊半径",NotificationBigClockSettings.ENTRY_BLUR_RADIUS," dp",0,32,.01f),
                new Setting("入口渐显强度",NotificationBigClockSettings.ENTRY_FADE_STRENGTH,"%",0,100,.01f),
                new Setting("清晰显示的下拉进度",NotificationBigClockSettings.ENTRY_COMPLETION,"%",1,100,.01f));
        addHint(card,"上下移动、模糊和渐显共用真实下拉与收起进度，达到设定进度时完全清晰。移动距离独立于大时钟高度，可精确调节；顶部状态图标与通知不参与时钟入口模糊。");

        addSection(card,"日期");addToggle(card,NotificationBigClockSettings.DATE_ENABLED,"显示日期");
        addAction(card,"日期格式",string(NotificationBigClockSettings.DATE_PATTERN),()->editFormat(NotificationBigClockSettings.DATE_PATTERN));
        addAction(card,"日期模板","年月日、星期、农历日期与干支年",()->chooseFormat(NotificationBigClockSettings.DATE_PATTERN));
        addHint(card,"默认组合公历日期、星期、干支年与农历日期；“农历日期”只显示月份和日期，不带“农历”前缀。");
        addBigClockNumbers(card,new Setting("日期字号",NotificationBigClockSettings.DATE_SIZE," dp",8,40,.01f),
                new Setting("日期字体粗细",NotificationBigClockSettings.DATE_WEIGHT,"",100,900,1),
                new Setting("日期水平位置",NotificationBigClockSettings.DATE_OFFSET_X," dp",-120,120,.01f),
                new Setting("日期垂直位置",NotificationBigClockSettings.DATE_OFFSET_Y," dp",-120,120,.01f),
                new Setting("日期与时间间距",NotificationBigClockSettings.DATE_GAP," dp",0,80,.01f));
        addBigClockAlignment(card,NotificationBigClockSettings.DATE_ALIGNMENT);
        addBigClockColors(card,NotificationBigClockSettings.DATE_COLOR_LIGHT,NotificationBigClockSettings.DATE_COLOR_DARK);

        addSection(card,"布局");addBigClockAlignment(card,NotificationBigClockSettings.ALIGNMENT);
        addBigClockNumbers(card,new Setting("展开水平位置",NotificationBigClockSettings.OFFSET_X," dp",-160,160,.01f),
                new Setting("展开垂直位置",NotificationBigClockSettings.OFFSET_Y," dp",-120,200,.01f),
                new Setting("收起水平位置",NotificationBigClockSettings.COMPACT_OFFSET_X," dp",-160,160,.01f),
                new Setting("收起垂直位置",NotificationBigClockSettings.COMPACT_OFFSET_Y," dp",-120,120,.01f));
        addHint(card,"位置可正负微调到两位小数。手动字号可超过滑条建议范围，确认后保存；实际排版会为顶部图标和通知留出安全空间。");

        addHint(card,"右上图标随下拉向下渐显，收起时向上渐隐。网速、信号、Wi‑Fi 和电池统一交接，避免重复显示。");

        addSection(card,"时钟与通知安全间距");
        addToggle(card,NotificationBigClockSettings.NOTIFICATION_GAP_ENABLED,"自动保留时钟与首张通知的距离");
        addBigClockNumbers(card,new Setting("时间与通知间距",NotificationBigClockSettings.NOTIFICATION_GAP," dp",0,100,.01f));
        addHint(card,"从调整后的时间与日期实际底边计算，字号和位置改变时同步更新首张通知的留白。支持两位小数与点击输入；此间距独立于通知堆叠及边缘虚化，关闭不会清除已保存的数值。");

        addSection(card,"通知堆叠");addToggle(card,NotificationBigClockSettings.STACK_ENABLED,"大时钟展开时层叠后续通知");
        addBigClockNumbers(card,new Setting("完整通知数量",NotificationBigClockSettings.VISIBLE_COUNT,"",1,3,1),
                new Setting("第一层底边宽度",NotificationBigClockSettings.TAIL_WIDTH_1,"%",50,100,.01f),
                new Setting("第二层底边宽度",NotificationBigClockSettings.TAIL_WIDTH_2,"%",50,100,.01f),
                new Setting("第三层底边宽度",NotificationBigClockSettings.TAIL_WIDTH_3,"%",50,100,.01f));
        addHint(card,"大时钟展开时保留 1–3 张完整通知，后方最多三层仅露出圆角底边。所有通知仍保留，滑动列表可继续查看全部通知。数量只支持 1 到 3 的整数；底边宽度独立固定，100% 对应完整卡片宽度，默认依次为 96%、92%、88%。宽度支持两位小数，超出滑条建议范围须确认。层叠间距沿用系统，旧间距与内缩设置不再生效。预览显示完整卡片及三层底边。");

        addSection(card,"通知边缘过渡");
        addToggle(card,NotificationBigClockSettings.NOTIFICATION_EDGE_ENABLED,"通知进入顶部边缘时模糊与渐隐");
        addBigClockNumbers(card,new Setting("顶部安全距离",NotificationBigClockSettings.NOTIFICATION_EDGE_SAFE_DISTANCE," dp",0,100,.01f),
                new Setting("过渡范围",NotificationBigClockSettings.NOTIFICATION_EDGE_RANGE," dp",0,120,.01f),
                new Setting("边缘模糊半径",NotificationBigClockSettings.NOTIFICATION_EDGE_BLUR_RADIUS," dp",0,32,.01f));
        addHint(card,"完整卡片在安全区保持清晰。卡片进入顶部安全距离后，在设定范围内逐渐模糊与渐隐；停止滑动时保留当前位置对应的效果，返回安全区恢复清晰。点击数值可精细输入到两位小数。");

        addSection(card,"底部内容");addToggle(card,NotificationBigClockSettings.FOOTER_ENABLED,"显示底部内容");
        addAction(card,"内容格式",string(NotificationBigClockSettings.FOOTER_PATTERN),()->editFormat(NotificationBigClockSettings.FOOTER_PATTERN));
        addAction(card,"内容模板","纯文本、日期时间、文字与时间组合",()->chooseFormat(NotificationBigClockSettings.FOOTER_PATTERN));
        addAction(card,"编辑纯文本",string(NotificationBigClockSettings.FOOTER_TEXT).isEmpty()?"可输入中文、英文或 emoji":string(NotificationBigClockSettings.FOOTER_TEXT),()->editText(NotificationBigClockSettings.FOOTER_TEXT));
        addHint(card,"格式中的 {text} 显示上面输入的纯文本。固定英文可加单引号，如 'C17' HH:mm {text}；纯文本本身不会按日期格式解释。");
        addBigClockNumbers(card,new Setting("底部字号",NotificationBigClockSettings.FOOTER_SIZE," dp",8,36,.01f),
                new Setting("底部字体粗细",NotificationBigClockSettings.FOOTER_WEIGHT,"",100,900,1),
                new Setting("底部水平位置",NotificationBigClockSettings.FOOTER_OFFSET_X," dp",-160,160,.01f),
                new Setting("底部垂直位置",NotificationBigClockSettings.FOOTER_OFFSET_Y," dp",-120,120,.01f),
                new Setting("底部安全间距",NotificationBigClockSettings.FOOTER_MARGIN," dp",0,120,.01f));
        addBigClockAlignment(card,NotificationBigClockSettings.FOOTER_ALIGNMENT);
        addBigClockColors(card,NotificationBigClockSettings.FOOTER_COLOR_LIGHT,NotificationBigClockSettings.FOOTER_COLOR_DARK);
    }
    private void addBigClockNumbers(LinearLayout card,Setting... settings) { for(Setting setting:settings)addSlider(card,setting); }
    private void addBigClockColors(LinearLayout card,String light,String dark) {
        LinearLayout colors=row();addColor(colors,light,"浅色背景",false);addColor(colors,dark,"深色背景",true);card.addView(colors,matchWrap());
    }
    private void addBigClockAlignment(LinearLayout card,String key) {
        addChoice(card,key,new String[]{"left","center","right"},new String[]{"左对齐","居中","右对齐"});
    }
    private final class BigClockPreview extends View {
        private float collapse=bigClockPreviewCollapse;
        private boolean dark=bigClockPreviewDark;
        private final TextView letterSpacingProbe=new TextView(MainActivity.this);
        private final NotificationClockStyle.Style nativePreviewStyle=NotificationClockStyle.resolve(this,Typeface.DEFAULT);
        private final NotificationClockFont.Resolver nativePreviewFont=NotificationClockFont.create(MainActivity.this,null);
        private boolean nativePreviewFontUnavailable;
        BigClockPreview(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription("保留顶部图标的通知栏大时钟、通知堆叠与底部内容效果预览");}
        @Override protected void onMeasure(int width,int height) {
            if(MeasureSpec.getSize(width)>dp(360))width=MeasureSpec.makeMeasureSpec(dp(360),MeasureSpec.EXACTLY);
            super.onMeasure(width,height);
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);float width=getWidth(),height=getHeight();if(width<=0||height<=0)return;
            android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            android.graphics.RectF bounds=new android.graphics.RectF(0,0,width,height);android.graphics.Path clip=new android.graphics.Path();clip.addRoundRect(bounds,dp(19),dp(19),android.graphics.Path.Direction.CW);
            int saved=canvas.save();canvas.clipPath(clip);
            paint.setShader(new android.graphics.LinearGradient(0,0,width,height,dark?new int[]{0xff142236,0xff344b51,0xff202d3d}:new int[]{0xff435b7c,0xff839591,0xff566578},null,android.graphics.Shader.TileMode.CLAMP));canvas.drawRect(bounds,paint);paint.setShader(null);
            paint.setShader(new android.graphics.RadialGradient(width*.1f,height*.65f,width*.8f,0x506caecd,0x003b5879,android.graphics.Shader.TileMode.CLAMP));canvas.drawRect(bounds,paint);paint.setShader(null);
            // A single uniform preview scale preserves the font's natural width/height ratio.
            float factor=width/400f,viewportHeight=height/factor;canvas.scale(factor,factor);
            drawStatusIcons(canvas,paint);
            int content=canvas.save();canvas.clipRect(0,44,400,viewportHeight-8);
            long now=System.currentTimeMillis();String time=TimeFormat.format(string(NotificationBigClockSettings.PATTERN),now);
            String date=TimeFormat.format(string(NotificationBigClockSettings.DATE_PATTERN),now);
            String footer=NotificationBigClockSettings.formatFooter(string(NotificationBigClockSettings.FOOTER_PATTERN),string(NotificationBigClockSettings.FOOTER_TEXT),now);
            int fullCount=NotificationBigClockSettings.visibleCount(number(NotificationBigClockSettings.VISIBLE_COUNT)),count=fullCount+3;
            boolean dateEnabled=enabled(NotificationBigClockSettings.DATE_ENABLED),footerEnabled=enabled(NotificationBigClockSettings.FOOTER_ENABLED);
            android.graphics.Paint datePaint=textPaint(date,previewValue(NotificationBigClockSettings.DATE_SIZE),Math.round(previewValue(NotificationBigClockSettings.DATE_WEIGHT)),0);
            android.graphics.Paint footerPaint=textPaint(footer,previewValue(NotificationBigClockSettings.FOOTER_SIZE),Math.round(previewValue(NotificationBigClockSettings.FOOTER_WEIGHT)),0);
            float dateHeight=dateEnabled?lineHeight(datePaint):0,dateGap=dateEnabled?previewValue(NotificationBigClockSettings.DATE_GAP):0;
            float notificationGap=enabled(NotificationBigClockSettings.NOTIFICATION_GAP_ENABLED)
                    ? previewValue(NotificationBigClockSettings.NOTIFICATION_GAP):0f,cardHeight=54;
            double desired=(double)previewValue(NotificationBigClockSettings.MAX_SIZE)*previewValue(NotificationBigClockSettings.SCALE)/100d;
            android.graphics.Paint expandedPaint=textPaint(time,NumericPolicy.textPixels(desired),Math.round(previewValue(NotificationBigClockSettings.WEIGHT)),previewValue(NotificationBigClockSettings.LETTER_SPACING)+nativePreviewStyle.letterSpacing,true);
            fitInkHeight(time,expandedPaint,viewportHeight*.44f);float expandedSize=expandedPaint.getTextSize();
            float compactSize=Math.min(previewValue(NotificationBigClockSettings.COMPACT_MAX_SIZE),NumericPolicy.textPixels((double)expandedSize*previewValue(NotificationBigClockSettings.COMPACT_SCALE)/100d));
            fitHeight(datePaint,viewportHeight*.15f);dateHeight=dateEnabled?lineHeight(datePaint):0;
            fitHeight(footerPaint,viewportHeight*.15f);
            float expandedHeight=inkBounds(time,expandedPaint).height();
            float compactAxisRatio=expandedSize>0?compactSize/expandedSize:1f;
            Typeface compactFace=nativeClockFace(Math.round(previewValue(NotificationBigClockSettings.WEIGHT)),compactAxisRatio);
            float compactHeight;
            if(compactFace!=null) {
                android.graphics.Paint compactPaint=textPaint(time,expandedSize,Math.round(previewValue(NotificationBigClockSettings.WEIGHT)),previewValue(NotificationBigClockSettings.LETTER_SPACING)+nativePreviewStyle.letterSpacing,true,false,compactFace);
                compactHeight=inkBounds(time,compactPaint).height();
            } else compactHeight=expandedSize>0?Math.min(viewportHeight*.22f,expandedHeight*compactSize/expandedSize):0;
            NotificationBigClockModel.Frame initial=previewFrame(viewportHeight,expandedHeight,compactHeight,dateHeight,dateGap,notificationGap,0);
            NotificationBigClockModel.Frame frame=previewFrame(viewportHeight,expandedHeight,compactHeight,dateHeight,dateGap,notificationGap,Math.round(initial.collapseDistance*collapse));
            float progress=frame.progress;
            int animatedWeight=Math.round(previewValue(NotificationBigClockSettings.WEIGHT));
            float heightRatio=Math.max(.001f,lerp(1f,compactAxisRatio,progress));
            Typeface heightFace=nativeClockFace(animatedWeight,heightRatio);
            float currentSize=heightFace!=null?expandedSize:lerp(expandedSize,compactSize,progress);
            paint=textPaint(time,currentSize,animatedWeight,previewValue(NotificationBigClockSettings.LETTER_SPACING)+nativePreviewStyle.letterSpacing,true,false,heightFace);
            float xOffset=NumericPolicy.drawPixels((double)previewValue(NotificationBigClockSettings.OFFSET_X)+previewValue(NotificationBigClockSettings.COMPACT_OFFSET_X)*progress);
            float clockTop=frame.clockTop;
            android.graphics.Rect timeInk=inkBounds(time,paint);
            float baseline=clockTop-timeInk.top,glassBottom=clockTop+Math.max(1,timeInk.height());
            int clockColor=previewColor(dark?NotificationBigClockSettings.COLOR_DARK:NotificationBigClockSettings.COLOR_LIGHT);
            paint.setColor(clockColor);
            if(enabled(NotificationBigClockSettings.GLASS)) {
                int rgb=clockColor&0xffffff;
                // The selected alpha belongs to paint; material alpha is applied once by the shader.
                paint.setShader(new android.graphics.LinearGradient(0,clockTop,0,glassBottom,
                        new int[]{rgb|(Math.round(255*.32f)<<24),rgb|(Math.round(255*.12f)<<24),rgb|(Math.round(255*.26f)<<24)},new float[]{0,.6f,1},android.graphics.Shader.TileMode.CLAMP));
            }
            drawAligned(canvas,time,paint,string(NotificationBigClockSettings.ALIGNMENT),xOffset,baseline);
            if(enabled(NotificationBigClockSettings.GLASS)&&enabled(NotificationBigClockSettings.GLASS_BORDER_ENABLED)&&previewValue(NotificationBigClockSettings.GLASS_BORDER_WIDTH)>0) {
                int border=previewColor(dark?NotificationBigClockSettings.GLASS_BORDER_COLOR_DARK:NotificationBigClockSettings.GLASS_BORDER_COLOR_LIGHT);
                paint.setColor(border);int rgb=border&0xffffff;paint.setShader(new android.graphics.LinearGradient(0,clockTop,0,glassBottom,
                        rgb|(Math.round(255*.85f)<<24),rgb|(Math.round(255*.4f)<<24),android.graphics.Shader.TileMode.CLAMP));
                paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(previewValue(NotificationBigClockSettings.GLASS_BORDER_WIDTH));
                drawAligned(canvas,time,paint,string(NotificationBigClockSettings.ALIGNMENT),xOffset,baseline);
            }
            paint.setShader(null);paint.setStyle(android.graphics.Paint.Style.FILL);
            if(dateEnabled) {
                int dateColor=previewColor(dark?NotificationBigClockSettings.DATE_COLOR_DARK:NotificationBigClockSettings.DATE_COLOR_LIGHT);
                datePaint.setColor(dateColor);
                float dateBaseline=frame.dateTop-datePaint.getFontMetrics().ascent;
                drawAligned(canvas,date,datePaint,string(NotificationBigClockSettings.DATE_ALIGNMENT),previewValue(NotificationBigClockSettings.DATE_OFFSET_X),dateBaseline);
            }
            float top=Math.max(clockTop+frame.clockHeight,frame.dateTop+dateHeight)+notificationGap;
            float stack=enabled(NotificationBigClockSettings.STACK_ENABLED)?1-progress:0;
            float footerTop=viewportHeight-12-previewValue(NotificationBigClockSettings.FOOTER_MARGIN)-lineHeight(footerPaint)+previewValue(NotificationBigClockSettings.FOOTER_OFFSET_Y);
            boolean visibleFooter=footerEnabled&&!footer.isEmpty();
            float rowBottom=visibleFooter?Math.max(top+48,footerTop-12):viewportHeight-8;
            float clearTop=Math.min(viewportHeight-50,visibleFooter?rowBottom-40:viewportHeight-50);
            // Fit the illustrative complete cards and all three tail edges within the preview.
            cardHeight=Math.max(4f,Math.min(cardHeight,(rowBottom-top-(fullCount-1)*8f-24f)/fullCount));
            float rowPitch=cardHeight+8f;
            int noticeClip=canvas.save();canvas.clipRect(0,Math.max(44,top),400,rowBottom);
            for(int i=count-1;i>=0;i--) {
                int behind=Math.min(3,Math.max(0,i-fullCount+1));
                float tailWidth=behind==0?368f:368f*NumericPolicy.scale((double)number(behind==1
                        ?NotificationBigClockSettings.TAIL_WIDTH_1:behind==2
                        ?NotificationBigClockSettings.TAIL_WIDTH_2:NotificationBigClockSettings.TAIL_WIDTH_3)/100d,368f);
                float noticeWidth=lerp(368f,tailWidth,stack);
                // Illustrative native-sized tail spacing; retired config values never affect the preview.
                float normalTop=top+i*rowPitch,peekTop=top+(fullCount-1)*rowPitch+8f*behind;
                float noticeTop=i<fullCount?normalTop:lerp(normalTop,peekTop,stack);
                android.graphics.RectF notice=new android.graphics.RectF(200f-noticeWidth*.5f,noticeTop,200f+noticeWidth*.5f,noticeTop+cardHeight);
                int tailClip=canvas.save();
                if(behind>0&&stack>0) {
                    float previousBottom=top+(fullCount-1)*rowPitch+cardHeight+8f*(behind-1);
                    canvas.clipRect(0,lerp(notice.top,previousBottom,stack),400,rowBottom);
                }
                paint.setColor(dark?0x68eaf0fa:0xb8f7f9fb);canvas.drawRoundRect(notice,14,14,paint);
                if(cardHeight>=20f&&noticeWidth>=108f&&(behind==0||stack<1f)) {
                    float iconSize=Math.min(24f,cardHeight-12f),iconTop=notice.top+(cardHeight-iconSize)*.5f;
                    paint.setColor(new int[]{0xff2f80ff,0xff6fa080,0xff967ad7}[i%3]);canvas.drawRoundRect(new android.graphics.RectF(notice.left+12,iconTop,notice.left+12+iconSize,iconTop+iconSize),7,7,paint);
                    float textLeft=notice.left+24+iconSize,textTop=notice.top+cardHeight*.3f;
                    paint.setColor(0x8842505b);canvas.drawRoundRect(new android.graphics.RectF(textLeft,textTop,notice.right-36,textTop+4),2,2,paint);
                    paint.setColor(0x5c58656f);canvas.drawRoundRect(new android.graphics.RectF(textLeft,textTop+9,notice.right-21,textTop+13),2,2,paint);
                }
                canvas.restoreToCount(tailClip);
            }
            canvas.restoreToCount(noticeClip);
            if(visibleFooter) {
                footerPaint.setColor(previewColor(dark?NotificationBigClockSettings.FOOTER_COLOR_DARK:NotificationBigClockSettings.FOOTER_COLOR_LIGHT));
                float footerBaseline=footerTop-footerPaint.getFontMetrics().ascent;
                drawAligned(canvas,footer,footerPaint,string(NotificationBigClockSettings.FOOTER_ALIGNMENT),previewValue(NotificationBigClockSettings.FOOTER_OFFSET_X),footerBaseline);
            }
            paint.setColor(0x60edf3f9);canvas.drawRoundRect(new android.graphics.RectF(342,clearTop,382,clearTop+32),16,16,paint);
            paint.setColor(Color.WHITE);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(1.6f);
            canvas.drawLine(357,clearTop+12,367,clearTop+22,paint);canvas.drawLine(367,clearTop+12,357,clearTop+22,paint);paint.setStyle(android.graphics.Paint.Style.FILL);
            canvas.restoreToCount(content);
            canvas.restoreToCount(saved);
        }
        private int previewColor(String key) {
            int selected=color(key);
            return StatusBarSettings.customAlpha(preferences.getAll(),key)?selected:
                    (selected&0xffffff)|(Color.alpha(nativePreviewStyle.color)<<24);
        }
        private float previewValue(String key) { return NumericPolicy.drawPixels(number(key)); }
        private NotificationBigClockModel.Frame previewFrame(float height,float expandedHeight,float compactHeight,float dateHeight,float dateGap,float notificationGap,int scroll) {
            return NotificationBigClockModel.measured(height,1f,44f,expandedHeight,compactHeight,dateHeight,dateGap,notificationGap,
                    previewValue(NotificationBigClockSettings.OFFSET_Y),previewValue(NotificationBigClockSettings.COMPACT_OFFSET_Y),
                    previewValue(NotificationBigClockSettings.DATE_OFFSET_Y),previewValue(NotificationBigClockSettings.WEIGHT),
                    previewValue(NotificationBigClockSettings.WEIGHT),scroll,0f,1f);
        }
        private android.graphics.Paint textPaint(String value,float size,int weight,float spacing) {
            return textPaint(value,size,weight,spacing,false);
        }
        private android.graphics.Paint textPaint(String value,float size,int weight,float spacing,boolean timeText) {
            return textPaint(value,size,weight,spacing,timeText,true);
        }
        private android.graphics.Paint textPaint(String value,float size,int weight,float spacing,boolean timeText,boolean fitWidth) {
            return textPaint(value,size,weight,spacing,timeText,fitWidth,timeText?nativeClockFace(weight,1f):null);
        }
        private android.graphics.Paint textPaint(String value,float size,int weight,float spacing,boolean timeText,boolean fitWidth,Typeface nativeFace) {
            TextView source=letterSpacingProbe;source.setText(value);source.setIncludeFontPadding(false);
            source.setTypeface(nativeFace!=null?nativeFace:FontRepository.typefaceForMode(MainActivity.this,string(NotificationBigClockSettings.FONT),timeText?nativePreviewStyle.typeface:Typeface.DEFAULT,weight));
            source.setLetterSpacing(NumericPolicy.drawPixels(spacing));source.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,NumericPolicy.textPixels(size));source.setFontFeatureSettings(nativePreviewStyle.fontFeatureSettings==null?"tnum":nativePreviewStyle.fontFeatureSettings);
            android.graphics.Paint paint=new android.graphics.Paint(source.getPaint());
            float measured=paint.measureText(value);if(fitWidth&&measured>352)paint.setTextSize(paint.getTextSize()*352/measured);
            return paint;
        }
        private Typeface nativeClockFace(int weight,float heightRatio) {
            if(!"native".equals(string(NotificationBigClockSettings.FONT))||nativePreviewFont==null||nativePreviewFontUnavailable)return null;
            // Resolver retains at most 96 weight/height variants across preview frames.
            Typeface result=nativePreviewFont.typeface(weight,heightRatio);
            if(result==null)nativePreviewFontUnavailable=true;
            return result;
        }
        private android.graphics.Rect inkBounds(String value,android.graphics.Paint paint) {
            android.graphics.Rect ink=new android.graphics.Rect();
            if(!value.isEmpty())paint.getTextBounds(value,0,value.length(),ink);
            return ink;
        }
        private void fitInkHeight(String value,android.graphics.Paint paint,float available) { float height=inkBounds(value,paint).height();if(height>available&&height>0)paint.setTextSize(paint.getTextSize()*available/height); }
        private void fitHeight(android.graphics.Paint paint,float available) { float height=lineHeight(paint);if(height>available&&height>0)paint.setTextSize(paint.getTextSize()*available/height); }
        private float lineHeight(android.graphics.Paint paint) { android.graphics.Paint.FontMetrics metrics=paint.getFontMetrics();return metrics.descent-metrics.ascent; }
        private float lerp(float start,float end,float fraction) { return start+(end-start)*fraction; }
        private void drawAligned(android.graphics.Canvas canvas,String value,android.graphics.Paint paint,String alignment,float offset,float baseline) {
            float anchor="left".equals(alignment)?24:"right".equals(alignment)?376:200;
            paint.setTextAlign("left".equals(alignment)?android.graphics.Paint.Align.LEFT:"right".equals(alignment)?android.graphics.Paint.Align.RIGHT:android.graphics.Paint.Align.CENTER);
            canvas.drawText(value,anchor+NumericPolicy.drawPixels(offset),NumericPolicy.drawPixels(baseline),paint);
        }
        private void drawStatusIcons(android.graphics.Canvas canvas,android.graphics.Paint paint) {
            paint.setColor(Color.WHITE);paint.setTextSize(13);paint.setTypeface(Typeface.DEFAULT);canvas.drawText("09:41",17,27,paint);
            for(int i=0;i<4;i++)canvas.drawRoundRect(new android.graphics.RectF(309+i*4,25-i*2,312+i*4,29),1,1,paint);
            paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            for(int i=0;i<3;i++)canvas.drawArc(new android.graphics.RectF(333+i*3,15+i*3,351-i*3,33-i*3),220,100,false,paint);
            canvas.drawRoundRect(new android.graphics.RectF(362,18,383,29),3,3,paint);canvas.drawLine(385,22,385,25,paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);canvas.drawRoundRect(new android.graphics.RectF(365,21,379,26),1,1,paint);
        }
    }
    private View qsPreview(String scene) {
        final boolean dark=scene.equals("dark");final String prefix="qs_global_"+scene;
        View preview=new View(this) {
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                android.graphics.RectF glass=new android.graphics.RectF(dp(1),dp(1),getWidth()-dp(1),getHeight()-dp(1));
                paint.setShader(new android.graphics.LinearGradient(0,0,getWidth(),getHeight(),dark?0xff253347:0xffe5edf8,dark?0xff414b60:0xffcdd8eb,android.graphics.Shader.TileMode.CLAMP));
                canvas.drawRoundRect(glass,dp(18),dp(18),paint);paint.setShader(null);paint.setColor(dark?0x18ffffff:0x60ffffff);
                float inset=Math.min(dp(14),getWidth()*.1f),available=getWidth()-inset*2;
                float diameter=Math.min(dp(48),available*.4f),centerY=getHeight()/2f;
                android.graphics.RectF circle=new android.graphics.RectF(inset,centerY-diameter/2,inset+diameter,centerY+diameter/2);canvas.drawOval(circle,paint);
                float gap=Math.min(dp(16),available*.12f),trackHeight=Math.min(dp(24),diameter/2);
                android.graphics.RectF track=new android.graphics.RectF(circle.right+gap,centerY-trackHeight/2,getWidth()-inset,centerY+trackHeight/2);canvas.drawRoundRect(track,dp(12),dp(12),paint);
                int first=color(prefix+"_color"),second=color(prefix+"_gradient_color");float opacity=Math.max(0f,Math.min(100f,number(prefix+"_opacity")))/100f;
                first=(Math.round(Color.alpha(first)*opacity)<<24)|(first&0xffffff);second=(Math.round(Color.alpha(second)*opacity)<<24)|(second&0xffffff);
                if(enabled(prefix+"_gradient_enabled")) {
                    double angle=Math.toRadians(number(prefix+"_gradient_angle")%360f);float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle),distance=(Math.abs(dx)*glass.width()+Math.abs(dy)*glass.height())/2f;
                    paint.setShader(new android.graphics.LinearGradient(glass.centerX()-dx*distance,glass.centerY()-dy*distance,glass.centerX()+dx*distance,glass.centerY()+dy*distance,first,second,android.graphics.Shader.TileMode.CLAMP));
                } else paint.setColor(first);
                canvas.drawOval(circle,paint);android.graphics.RectF fill=new android.graphics.RectF(track);fill.right=track.left+track.width()*.62f;canvas.drawRoundRect(fill,dp(12),dp(12),paint);
                paint.setShader(null);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x80ffffff);canvas.drawOval(circle,paint);canvas.drawRoundRect(fill,dp(12),dp(12),paint);
                paint.setStyle(android.graphics.Paint.Style.FILL);paint.setShader(new android.graphics.LinearGradient(0,dp(20),0,dp(46),0x50ffffff,0x00ffffff,android.graphics.Shader.TileMode.CLAMP));
                canvas.drawOval(circle,paint);canvas.drawRoundRect(fill,dp(12),dp(12),paint);paint.setShader(null);
            }
        };
        preview.setContentDescription((dark?"深色背景":"浅色背景")+"活动填充预览");return preview;
    }
    private void addToggle(LinearLayout parent,String key,String label) {
        LinearLayout line=row();line.setPadding(0,dp(9),0,dp(7));line.setMinimumHeight(dp(52));
        line.addView(text(label,13,INK,false),new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=styledSwitch(enabled(key));toggle.setContentDescription(label+"开关");
        toggle.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(key,checked).apply();changed();selectGroup(selected);});line.addView(toggle);parent.addView(line,matchWrap());
    }
    private void addConfigTransfer(LinearLayout parent) {
        addSection(parent,"配置文件");LinearLayout box=column();box.setPadding(dp(14),dp(7),dp(14),dp(10));box.setBackground(rounded(CARD,24,true));
        addAction(box,"导出配置","保存位置、大小、颜色与功能设置",()->pickConfig(true));
        addAction(box,"导入配置","检查文件并确认后应用",()->pickConfig(false));
        addHint(box,"配置文件包含自定义文字。自选字体需在新设备另行导入；日志开关由当前设备保留。");parent.addView(box,matchWrap());
    }
    private void pickConfig(boolean export) {
        if(!requireRuntimeActive())return;
        if(transferringConfig){Toast.makeText(this,"正在处理配置，请稍候",Toast.LENGTH_SHORT).show();return;}
        Intent picker=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);picker.addCategory(Intent.CATEGORY_OPENABLE);picker.setType(export?"application/json":"*/*");
        if(export)picker.putExtra(Intent.EXTRA_TITLE,"C17-config-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new java.util.Date())+".json");
        try{startActivityForResult(picker,export?CONFIG_EXPORT_REQUEST:CONFIG_IMPORT_REQUEST);}
        catch(android.content.ActivityNotFoundException unavailable){Toast.makeText(this,"未找到文件选择器",Toast.LENGTH_LONG).show();}
    }
    private void addUpdates(LinearLayout parent) {
        addSection(parent,"关于应用");LinearLayout box=column();box.setPadding(dp(14),dp(16),dp(14),dp(10));box.setBackground(rounded(CARD,24,true));
        LinearLayout brand=row();ImageView logo=new ImageView(this);int logoId=getResources().getIdentifier("ic_c17_launcher","mipmap",getPackageName());if(logoId!=0)logo.setImageResource(logoId);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);brand.addView(logo,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout identity=column();identity.setPadding(dp(12),0,0,0);identity.addView(text("更好的 C17 状态栏",16,INK,false));TextView version=text("v"+appVersion()+" · aiingjie",12,MUTED,false);version.setPadding(0,dp(6),0,0);identity.addView(version);brand.addView(identity,new LinearLayout.LayoutParams(0,-2,1));box.addView(brand,matchWrap());
        updateSummary=text(checkingUpdates?"正在检查 GitHub 发布版本…":getSharedPreferences("github_update",MODE_PRIVATE).getString("last_message","每天自动检查，也可以手动检查更新。"),12,MUTED,false);
        updateSummary.setPadding(0,dp(6),0,dp(5));box.addView(updateSummary,matchWrap());
        addAction(box,"检查更新","SANWU5 / c17-statusbar",()->checkUpdates(true));
        addAction(box,"打开 GitHub 项目","查看源码与正式发布版本",()->openUpdateUrl(GitHubUpdates.REPO_URL));
        addAction(box,"作者 GitHub",ProjectContact.GITHUB,()->openUpdateUrl("https://github.com/"+ProjectContact.GITHUB));
        addAction(box,"作者酷安",ProjectContact.COOLAPK+" · 点击复制",()->
                Toast.makeText(this,ProjectContact.copyCoolapk(this)?"酷安用户名已复制":"复制失败，请手动复制用户名",Toast.LENGTH_SHORT).show());
        addAction(box,"合作与捐赠","QQ："+ProjectContact.QQ+" · 点击复制",()->
                Toast.makeText(this,ProjectContact.copyQq(this)?"QQ号已复制":"复制失败，请手动复制QQ号",Toast.LENGTH_SHORT).show());
        addAction(box,"开源许可证","GNU GPLv3",()->showLicense());parent.addView(box,matchWrap());
    }
    private void addDiagnostics(LinearLayout parent) {
        addSection(parent,"维护与日志");
        LinearLayout card=column();card.setPadding(dp(14),dp(16),dp(14),dp(10));card.setBackground(rounded(CARD,24,true));
        LinearLayout line=row();line.addView(text("记录诊断日志",14,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=styledSwitch(enabled(StatusBarSettings.DIAGNOSTICS_ENABLED));toggle.setContentDescription("诊断日志开关");
        toggle.setOnCheckedChangeListener((button,checked)->{
            preferences.edit().putBoolean(StatusBarSettings.DIAGNOSTICS_ENABLED,checked).apply();changed();
            ModuleDiagnostics.app(this,"app","Diagnostics enabled; module version="+appVersion());
        });line.addView(toggle);card.addView(line,matchWrap());
        addHint(card,"默认关闭。开启后记录模块加载、设置应用、功能回退及异常原因；关闭后停止记录。已有日志可查看、分享或保存为文件。");
        addAction(card,"查看日志","查看最近记录与错误位置",()->showDiagnostics());
        addAction(card,"分享日志","分享只读日志文件",()->ModuleDiagnostics.share(this));
        addAction(card,"下载／保存日志","选择文件夹导出日志",()->ModuleDiagnostics.beginExport(this,LOG_EXPORT_REQUEST));
        addAction(card,"清空日志","删除已经记录的诊断内容",()->new AlertDialog.Builder(this).setTitle("清空日志？")
            .setNegativeButton("取消",null).setPositiveButton("清空",(dialog,which)->{
                try { ModuleDiagnostics.clear(this);Toast.makeText(this,"日志已清空",Toast.LENGTH_SHORT).show(); }
                catch (java.io.IOException error) { ModuleDiagnostics.error("app","Could not clear diagnostic log",error);
                    Toast.makeText(this,"清空失败，请稍后重试",Toast.LENGTH_LONG).show(); }
            }).show());
        parent.addView(card,matchWrap());
    }
    private void showLicense() {
        try(java.io.InputStream input=getAssets().open("licenses/GPL-3.0.txt")) {
            java.io.InputStreamReader reader=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8);
            StringBuilder content=new StringBuilder();char[] buffer=new char[4096];int count;
            while((count=reader.read(buffer))!=-1)content.append(buffer,0,count);
            TextView license=text("Copyright (C) 2026 aiingjie\nGPL-3.0-only\n\n"+content,12,INK,false);
            license.setTextIsSelectable(true);license.setPadding(dp(20),dp(12),dp(20),dp(12));
            ScrollView scroll=new ScrollView(this);scroll.addView(license,matchWrap());
            new AlertDialog.Builder(this).setTitle("GNU GPLv3").setView(scroll).setNegativeButton("关闭",null)
                .setPositiveButton("查看源码",(dialog,which)->openUpdateUrl(GitHubUpdates.REPO_URL)).show();
        } catch(java.io.IOException unavailable) {openUpdateUrl(GitHubUpdates.REPO_URL+"/blob/main/LICENSE");}
    }
    private void showDiagnostics() {
        new Thread(()->{
            String content=ModuleDiagnostics.readText(this);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                TextView value=text(content.isEmpty()?"暂无日志。开启诊断后复现问题，可在这里查看原因。":content,11,INK,false);
                value.setTextIsSelectable(true);value.setTypeface(Typeface.MONOSPACE);value.setPadding(dp(16),dp(12),dp(16),dp(12));
                ScrollView scroll=new ScrollView(this);scroll.addView(value,matchWrap());
                new AlertDialog.Builder(this).setTitle("诊断日志").setView(scroll).setPositiveButton("关闭",null).show();
            });
        },"C17-log-preview").start();
    }
    private void checkUpdates(boolean manual) {
        if(checkingUpdates) { if(manual)Toast.makeText(this,"正在检查，请稍候",Toast.LENGTH_SHORT).show();return; }
        checkingUpdates=true;if(updateSummary!=null)updateSummary.setText("正在检查 GitHub 发布版本…");
        GitHubUpdates.fetch(appVersion(),result->{
            ModuleDiagnostics.app(this,"update","GitHub check completed ("+result.status+")");
            checkingUpdates=false;String message=result.message+(result.version.isEmpty()||result.message.contains(result.version)?"":" · "+result.version);
            getSharedPreferences("github_update",MODE_PRIVATE).edit().putLong("last_attempt",System.currentTimeMillis()).putString("last_message",message).apply();
            if(isFinishing()||isDestroyed())return;
            if(updateSummary!=null)updateSummary.setText(message);
            if(manual)showUpdateResult(result);
        });
    }
    private void showUpdateResult(GitHubUpdates.Result result) {
        String details=result.message+(result.version.isEmpty()?"":"\n发布版本："+result.version);
        if(!result.body.isEmpty())details+="\n\n"+result.body;
        TextView value=text(details,13,INK,false);value.setPadding(dp(20),dp(12),dp(20),dp(12));ScrollView content=new ScrollView(this);content.addView(value,matchWrap());
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(result.newer?"发现新版本":"更新检查").setView(content).setNegativeButton("关闭",null);
        if(result.newer&&!result.apkUrl.isEmpty())dialog.setPositiveButton("下载 APK",(d,w)->openUpdateUrl(result.apkUrl));
        else if(!result.releaseUrl.isEmpty())dialog.setPositiveButton("查看发布页",(d,w)->openUpdateUrl(result.releaseUrl));
        else dialog.setPositiveButton("打开项目",(d,w)->openUpdateUrl(GitHubUpdates.REPO_URL));
        dialog.show();
    }
    private void openUpdateUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
        catch(android.content.ActivityNotFoundException unavailable) { Toast.makeText(this,"未找到可打开链接的浏览器",Toast.LENGTH_LONG).show(); }
    }
    private String masterKey(String group) { return group.equals("qs_media")?QsMediaAppearance.MASTER:group.equals("notification_clear")?NotificationClearAppearance.MASTER:isClockGroup(group)?group+"_controls_enabled":group+"_enabled"; }
    private boolean isClockGroup(String group) { return group.equals("clock")||group.equals("shade_clock"); }
    private String clockPatternKey(String group) { return group.equals("shade_clock")?StatusBarSettings.SHADE_CLOCK_PATTERN:StatusBarSettings.CLOCK_PATTERN; }
    private String clockWeightKey(String group) { return group.equals("shade_clock")?StatusBarSettings.SHADE_CLOCK_WEIGHT:StatusBarSettings.CLOCK_WEIGHT; }
    private String clockPreviewGroup(String group) { return group.equals("shade_clock")&&!enabled(masterKey(group))?"clock":group; }
    private boolean enabled(String key) { return StatusBarSettings.bool(preferences.getAll(),key); }
    private String[][] featureKeys(String group) {
        List<String[]> keys=new ArrayList<>();
        if(!group.equals("font")&&!group.equals("tiles")) {
            keys.add(new String[]{group+"_position_enabled","位置调整"});keys.add(new String[]{group+"_size_enabled","大小调整"});keys.add(new String[]{group+"_color_enabled","颜色调整"});
        }
        if(group.equals("data")) {
            keys.add(new String[]{"data_icon_enabled","仿 iOS 信号样式"});keys.add(new String[]{"data_badge_hidden","隐藏系统网络角标"});
        } else if(group.equals("wifi")) {
            keys.add(new String[]{"wifi_icon_enabled","仿 iOS Wi-Fi 样式"});keys.add(new String[]{"wifi_activity_hidden","隐藏 Wi-Fi 上传下载箭头"});keys.add(new String[]{"wifi_badge_hidden","隐藏 Wi-Fi 角标"});
        } else if(group.equals("label")) {
            keys.add(new String[]{StatusBarSettings.LABEL_HIDDEN,"隐藏网络制式文字"});
            keys.add(new String[]{"label_normalize_enabled","统一网络制式名称"});keys.add(new String[]{"label_text_style_enabled","文字粗细"});
        } else if(isClockGroup(group)) {
            keys.add(new String[]{group.equals("shade_clock")?StatusBarSettings.SHADE_CLOCK_ENABLED:StatusBarSettings.CLOCK_ENABLED,"自定义时间格式"});keys.add(new String[]{group+"_text_style_enabled","文字粗细与字距"});
        } else if(group.equals("carrier")||isCarrierPanel(group)) {
            keys.add(new String[]{group+"_replace_enabled","运营商替换"});keys.add(new String[]{group+"_text_style_enabled","文字粗细与字距"});
        } else if(group.equals("speed")) {
            keys.add(new String[]{"speed_lines_enabled","数字与单位间距"});keys.add(new String[]{"speed_text_style_enabled","文字粗细"});
        } else if(group.equals("battery")) {
            keys.add(new String[]{"battery_style_enabled","应用所选电池样式"});
            keys.add(new String[]{StatusBarSettings.BATTERY_CHARGE_INSIDE,"电池内数字与闪电切换"});
            keys.add(new String[]{"battery_text_color_enabled","独立电量数字颜色"});keys.add(new String[]{"battery_bolt_color_enabled","独立闪电颜色"});keys.add(new String[]{"battery_charge_color_enabled","独立充电填充颜色"});keys.add(new String[]{"battery_alert_color_enabled","独立低电量／省电颜色"});
        } else if(group.equals("tiles")) {
            keys.add(new String[]{"tiles_portrait_enabled","竖屏启用"});keys.add(new String[]{"tiles_landscape_enabled","横屏启用"});
            keys.add(new String[]{"tiles_fade_enabled","滑动边缘渐隐"});keys.add(new String[]{"tiles_blur_enabled","高级模糊（默认关闭）"});
        }
        return keys.toArray(new String[keys.size()][]);
    }
    private void addFeaturePanel(LinearLayout card,Group group) {
        String[][] keys=featureKeys(group.key);if(keys.length==0)return;
        addSection(card,"独立开关");LinearLayout panel=column();
        for(String[] key:keys) {
            LinearLayout line=row();line.setPadding(0,dp(6),0,dp(6));line.setMinimumHeight(dp(52));line.addView(text(key[1],13,INK,false),new LinearLayout.LayoutParams(0,-2,1));
            Switch toggle=styledSwitch(enabled(key[0]));toggle.setContentDescription(key[1]+"开关");
            toggle.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(key[0],checked).apply();changed();if(key[0].equals("tiles_blur_enabled"))selectGroup(selected);});line.addView(toggle);panel.addView(line,matchWrap());
        }
        LinearLayout.LayoutParams params=matchWrap();params.topMargin=dp(6);card.addView(panel,params);
    }
    private boolean isCarrierPanel(String key) { return CarrierPanels.isPanel(key); }
    private Group carrierGroup() {
        String title=carrierPanel.equals(CarrierPanels.NOTIFICATION)?"通知页运营商文字":carrierPanel.equals(CarrierPanels.CONTROL)?"控制中心运营商文字":"锁屏运营商文字";
        return new Group(carrierPanel,title,"",new Setting[]{
            pos("水平位置",CarrierPanels.key(carrierPanel,"offset_x"),true),pos("垂直位置",CarrierPanels.key(carrierPanel,"offset_y"),false),
            scale("文字大小",CarrierPanels.key(carrierPanel,"scale")),weight(CarrierPanels.key(carrierPanel,"weight")),
            new Setting("字距微调",CarrierPanels.key(carrierPanel,"spacing")," dp",-2,8,.01f)});
    }
    private void addCarrierEditor(LinearLayout card) {
        addSection(card,"正在编辑的界面");LinearLayout panels=row();
        String[] labels={"通知页","控制中心","锁屏"};String[] values={CarrierPanels.NOTIFICATION,CarrierPanels.CONTROL,CarrierPanels.LOCKSCREEN};
        for(int i=0;i<values.length;i++) {
            final String panel=values[i];boolean active=panel.equals(carrierPanel);TextView choice=text(labels[i],13,active?ACCENT:MUTED,false);
            choice.setContentDescription("编辑"+labels[i]+"运营商");choice.setGravity(Gravity.CENTER);choice.setPadding(dp(8),dp(13),dp(8),dp(13));
            pressable(choice,rounded(active?SOFT:SURFACE,12,false));choice.setSelected(active);choice.setOnClickListener(v->{carrierPanel=panel;selectGroup(selected);pageScroll.post(()->pageScroll.scrollTo(0,0));});
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(i>0)params.leftMargin=dp(6);panels.addView(choice,params);
        }
        card.addView(panels,matchWrap());Group group=carrierGroup();LinearLayout line=row();line.setPadding(0,dp(12),0,dp(4));
        line.addView(text(group.title,14,INK,true),new LinearLayout.LayoutParams(0,-2,1));Switch master=styledSwitch(enabled(masterKey(group.key)));
        master.setContentDescription(group.title+" 独立开关");
        master.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(masterKey(group.key),checked).apply();changed();});line.addView(master);card.addView(line,matchWrap());
        addHint(card,carrierPanel.equals(CarrierPanels.LOCKSCREEN)?"锁屏独立设置默认关闭。开启后应用此处参数，关闭恢复系统运营商内容。":"下面的设置只作用于所选界面。未单独修改的参数沿用之前的设置，各界面可分别恢复默认值。");
        if(carrierPanel.equals(CarrierPanels.NOTIFICATION))addBigClockConflict(card);
        addFeaturePanel(card,group);String modeKey=CarrierPanels.key(group.key,"mode"),patternKey=CarrierPanels.key(group.key,"pattern"),textKey=CarrierPanels.key(group.key,"text");
        addChoice(card,modeKey,new String[]{"original","time","text"},new String[]{"运营商","自定义时间","纯文本"});
        String mode=string(modeKey);if(mode.equals("time"))addFormat(card,patternKey);
        if(mode.equals("text")) { addAction(card,"编辑文本",string(textKey),()->editText(textKey));addSample(card); }
        addSettings(card,group);addSection(card,"此界面的文字颜色");LinearLayout colors=row();
        addColor(colors,CarrierPanels.key(group.key,"color_light"),"浅色背景",false);addColor(colors,CarrierPanels.key(group.key,"color_dark"),"深色背景",true);card.addView(colors,matchWrap());
        addHint(card,"颜色与透明度只作用于此界面，按原生文字颜色自动切换。");addReset(group);
    }
    private TextView addSection(LinearLayout card,String title) { TextView label=text(title,13,MUTED,true);label.setTag("c17-section:"+title);label.setPadding(dp(5),dp(22),0,dp(10));card.addView(label,matchWrap());return label; }
    /** Rebuilds the old long form into independent settings sections without changing editors. */
    private void finishDetailLayout(LinearLayout source) {
        List<View> children=new ArrayList<>();for(int i=0;i<source.getChildCount();i++)children.add(source.getChildAt(i));
        source.removeAllViews();List<String> titles=new ArrayList<>();List<LinearLayout> bodies=new ArrayList<>();
        LinearLayout current=source;
        for(View child:children) {
            Object tag=child.getTag();
            if(tag instanceof String&&((String)tag).startsWith("c17-section:")) {
                titles.add(((String)tag).substring("c17-section:".length()));current=column();bodies.add(current);continue;
            }
            android.view.ViewGroup.LayoutParams params=child.getLayoutParams();current.addView(child,params);
        }
        int insert=editor.indexOfChild(source)+1;
        for(int i=0;i<titles.size();i++) {
            final String title=titles.get(i),key=groups[selected].key+"/"+i+"/"+title;
            LinearLayout section=column();section.setPadding(dp(18),dp(5),dp(18),dp(7));section.setBackground(rounded(CARD,24,false));
            LinearLayout body=bodies.get(i);body.setPadding(0,0,0,dp(10));
            boolean open=expandedSections.contains(key)||!expandedSections.contains("closed:"+key)&&!title.equals("独立开关");body.setVisibility(open?View.VISIBLE:View.GONE);
            LinearLayout heading=row();heading.setPadding(dp(2),dp(15),dp(2),dp(15));heading.setMinimumHeight(dp(62));
            TextView name=text(title,17,INK,true);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=text(open?"−":"＋",20,MUTED,false);arrow.setPadding(dp(8),0,0,0);heading.addView(arrow);
            heading.setContentDescription(title+"，"+(open?"已展开":"点击展开"));
            heading.setOnClickListener(v->{boolean expand=body.getVisibility()!=View.VISIBLE;
                if(expand){expandedSections.remove("closed:"+key);expandedSections.add(key);body.setAlpha(0f);body.setVisibility(View.VISIBLE);body.animate().alpha(1f).setDuration(160L).start();}
                else{expandedSections.remove(key);expandedSections.add("closed:"+key);body.animate().cancel();body.setVisibility(View.GONE);body.setAlpha(1f);}
                arrow.setText(expand?"−":"＋");heading.setContentDescription(title+"，"+(expand?"已展开":"点击展开"));
            });
            section.addView(heading,matchWrap());section.addView(body,matchWrap());
            LinearLayout.LayoutParams p=matchWrap();p.topMargin=dp(12);editor.addView(section,insert++,p);
        }
    }
    private void addSettings(LinearLayout card,Group group) {
        String previous="";
        for(Setting setting:group.settings) {
            String section=setting.key.startsWith("qs_global_")?"填充效果":setting.key.startsWith("tiles_")?"边缘范围与位置":setting.key.contains("offset_")?"位置":setting.key.contains("scale")?"大小":setting.key.contains("seconds")?"充电切换节奏":"文字与间距";
            if(!previous.equals(section)) { addSection(card,section);previous=section; }addSlider(card,setting);
        }
    }
    private void addReset(Group group) { addAction(editor,"恢复本组默认值","恢复开关及本组显示参数",()->new AlertDialog.Builder(this).setTitle("恢复"+group.title).setMessage("重置本组设置。").setNegativeButton("取消",null).setPositiveButton("恢复",(dialog,which)->resetGroup(group)).show()); }
    private TextView addHint(LinearLayout parent,String value) {
        TextView hint=text(value,12,MUTED,false);hint.setLineSpacing(dp(3),1f);hint.setPadding(0,dp(10),0,dp(8));parent.addView(hint,matchWrap());
        return hint;
    }
    private void addBatteryColors(LinearLayout card,String item,String title) {
        addSection(card,title);
        String label=item.equals("battery")?"外形":item.equals("battery_text")?"数字":item.equals("battery_bolt")?"闪电":item.equals("battery_charge")?"充电填充":"低电量／省电";
        LinearLayout choices=row();addColor(choices,item+"_color_light",label+" · 浅色背景",false);
        addColor(choices,item+"_color_dark",label+" · 深色背景",true);card.addView(choices,matchWrap());
    }
    private void addAction(LinearLayout parent,String title,String detail,Runnable action) {
        LinearLayout box=row();box.setPadding(dp(14),dp(14),dp(12),dp(14));box.setMinimumHeight(dp(62));
        LinearLayout copy=column();copy.addView(text(title,16,INK,false));
        if(!detail.isEmpty()) { TextView subtitle=text(detail,12,MUTED,false);subtitle.setPadding(0,dp(6),dp(5),0);copy.addView(subtitle,matchWrap()); }
        box.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",22,MUTED,false);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);box.addView(arrow);
        pressable(box,rounded(parent==editor?CARD:Color.TRANSPARENT,parent==editor?24:14,false));box.setContentDescription(title+(detail.isEmpty()?"":"，"+detail));box.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams params=matchWrap();params.topMargin=dp(parent==editor?14:4);parent.addView(box,params);
    }
    private void addChoice(LinearLayout card,String key,String[] values,String[] labels) {
        LinearLayout choices=row();String stored=string(key);
        for(int i=0;i<values.length;i++) {
            final String value=values[i];TextView choice=text(labels[i],12,value.equals(stored)?ACCENT:MUTED,true);
            choice.setGravity(Gravity.CENTER);choice.setPadding(dp(7),dp(13),dp(7),dp(13));choice.setMinHeight(dp(48));
            pressable(choice,rounded(value.equals(stored)?SOFT:SURFACE,12,true));choice.setSelected(value.equals(stored));
            choice.setOnClickListener(v->{
                if(key.equals(StatusBarSettings.FONT_MODE)&&value.equals("custom")&&string(StatusBarSettings.FONT_REVISION).isEmpty()) { pickFont();return; }
                preferences.edit().putString(key,value).apply();changed();selectGroup(selected);
            });
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(i>0)params.leftMargin=dp(5);choices.addView(choice,params);
        }
        LinearLayout.LayoutParams params=matchWrap();params.topMargin=dp(6);card.addView(choices,params);
    }
    private void addFonts(LinearLayout card) {
        addChoice(card,StatusBarSettings.FONT_MODE,new String[]{"system","pingfang","custom"},new String[]{"跟随系统","苹方","自选字体"});
        addHint(card,"内置苹方仅保留数字、英文、标点和时间常用中文字，粗细 100–900；其余字符由系统补齐。自选字体的真实字重范围取决于文件。");
        addAction(card,importing?"正在导入字体…":"导入 TTF / OTF / TTC",string(StatusBarSettings.FONT_NAME),()->{if(!importing)pickFont();});
        sample=text("09:41  周三  5G  KB/s",20,INK,false);sample.setPadding(0,dp(20),0,dp(8));card.addView(sample,matchWrap());
        addHint(card,"字体文件复制到模块内部，原文件移动后仍可使用。");updateSample();
    }
    private void addFormat(LinearLayout card,String key) {
        addSection(card,"实时预览");addSample(card);
        addSection(card,"时间格式");addAction(card,"编辑时间格式",string(key),()->editFormat(key));
        addAction(card,"选择常用格式","时分、秒、日期与星期",()->chooseFormat(key));
    }
    private void addSample(LinearLayout card) {
        LinearLayout preview=row();preview.setPadding(dp(13),dp(15),dp(13),dp(15));preview.setBackground(rounded(SURFACE,16,false));
        sample=text("",19,INK,true);sample.setMaxLines(2);preview.addView(sample,new LinearLayout.LayoutParams(0,-2,1));
        if(selected>=0&&isClockGroup(groups[selected].key)) {
            TextView network=text("5G",11,INK,true);network.setPadding(dp(8),0,dp(4),0);preview.addView(network);
            for(String glyph:new String[]{"data","wifi","battery"}) {UiGlyph icon=new UiGlyph(this,glyph,INK);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(16),dp(16));p.leftMargin=dp(4);preview.addView(icon,p);}
            TextView percent=text("100%",10,INK,false);percent.setPadding(dp(4),0,0,0);preview.addView(percent);
        }
        card.addView(preview,matchWrap());
    }
    private void chooseFormat(String key) {
        String[] patterns=key.equals(NotificationBigClockSettings.DATE_PATTERN)
                ?new String[]{NotificationBigClockSettings.DATE_DEFAULT,"M月d日 EEEE","yyyy年M月d日 {星期}","M月d日 {周} {农历}","{干支} {农历日期}"}
                :key.equals(NotificationBigClockSettings.FOOTER_PATTERN)
                ?new String[]{"{text}","HH:mm {text}","yyyy年M月d日 {周} {text}","{农历} {text}","'C17' HH:mm {text}","✨ {text}"}
                :new String[]{"HH:mm","HH:mm:ss","{上午下午} hh:mm","{时段} HH:mm","M月d日 {周} HH:mm","yyyy年MM月dd日 {星期} HH:mm","MM/dd HH:mm"};
        String[] labels=new String[patterns.length];long now=System.currentTimeMillis();
        for(int i=0;i<patterns.length;i++){String value=formatPatternPreview(key,patterns[i],now);labels[i]=value.isEmpty()?"纯文本 {text}":value;}
        new AlertDialog.Builder(this).setTitle("常用时间格式").setItems(labels,(dialog,which)->{
            preferences.edit().putString(key,patterns[which]).apply();changed();selectGroup(selected);
        }).setNegativeButton("取消",null).show();
    }
    private void editFormat(String key) {
        LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(8));EditText field=input();
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TimeFormat.MAX_PATTERN)});
        field.setText(string(key));content.addView(field,matchWrap());TextView result=text("",14,INK,false);content.addView(result,matchWrap());
        addHint(content,"点击添加：年 / 月 / 日 / 周 / 星期 / 时 / 12时 / 分 / 秒 / 上午下午 / 时段 / 农历 / 农历日期 / 干支。“农历日期”不带“农历”前缀。");
        String[][] tokens=key.equals(NotificationBigClockSettings.FOOTER_PATTERN)
                ?new String[][]{{"年","月","日","周","星期"},{"时","12时","分","秒"},{"上午下午","时段"},{"农历","农历日期","干支","text"}}
                :new String[][]{{"年","月","日","周","星期"},{"时","12时","分","秒"},{"上午下午","时段"},{"农历","农历日期","干支"}};
        for(String[] line:tokens) {
            LinearLayout row=row();for(String token:line) {
                TextView chip=text(token.equals("text")?"纯文本":token,11,ACCENT,true);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(3),dp(10),dp(3),dp(10));
                chip.setOnClickListener(v->{int start=Math.max(0,field.getSelectionStart()),end=Math.max(start,field.getSelectionEnd());field.getText().replace(start,end,"{"+token+"}");});
                row.addView(chip,new LinearLayout.LayoutParams(0,-2,1));
            }content.addView(row,matchWrap());
        }
        addHint(content,"也可输入 yyyy-MM-dd HH:mm:ss 等格式。固定英文放在单引号内，例如 'C17' HH:mm；中文直接输入。时段按小时显示凌晨、早晨、上午、中午、下午、晚上。");
        if(key.equals(NotificationBigClockSettings.FOOTER_PATTERN))addHint(content,"{text} 引用独立输入的纯文本，英文与 emoji 都按原文显示；单引号内的 {text} 仅显示这个标记本身。");
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("自定义时间格式").setView(scroll).setNegativeButton("取消",null).setPositiveButton("应用",null).create();
        Runnable validate=()->{
            String pattern=field.getText().toString(),error=formatPatternError(key,pattern);
            result.setText(error==null?formatPatternPreview(key,pattern,System.currentTimeMillis()):error);result.setTextColor(error==null?INK:0xffc23434);
            if(dialog.getButton(AlertDialog.BUTTON_POSITIVE)!=null)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(error==null);
        };
        field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){validate.run();}public void afterTextChanged(Editable value){}});
        dialog.setOnShowListener(ignored->{validate.run();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String pattern=field.getText().toString();if(formatPatternError(key,pattern)!=null){validate.run();return;}
            preferences.edit().putString(key,pattern).apply();changed();selectGroup(selected);dialog.dismiss();
        });});dialog.show();
    }
    private String formatPatternError(String key,String pattern) {
        return key.equals(NotificationBigClockSettings.FOOTER_PATTERN)?NotificationBigClockSettings.footerValidationError(pattern):TimeFormat.validationError(pattern);
    }
    private String formatPatternPreview(String key,String pattern,long now) {
        return key.equals(NotificationBigClockSettings.FOOTER_PATTERN)?NotificationBigClockSettings.formatFooter(pattern,string(NotificationBigClockSettings.FOOTER_TEXT),now):TimeFormat.format(pattern,now);
    }
    private void editText(String key) {
        EditText field=input();field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TimeFormat.MAX_TEXT)});field.setText(string(key));
        new AlertDialog.Builder(this).setTitle("编辑自定义文本").setView(field).setNegativeButton("取消",null).setPositiveButton("应用",(dialog,which)->{
            preferences.edit().putString(key,field.getText().toString().replace('\n',' ').replace('\r',' ')).apply();changed();selectGroup(selected);
        }).show();
    }
    private void pickFont() {
        if(!requireRuntimeActive())return;
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);picker.addCategory(Intent.CATEGORY_OPENABLE);picker.setType("*/*");
        try { startActivityForResult(picker,FONT_REQUEST); } catch(android.content.ActivityNotFoundException unavailable) { Toast.makeText(this,"未找到文件选择器",Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if((request==FONT_REQUEST||request==CONFIG_IMPORT_REQUEST||request==CONFIG_EXPORT_REQUEST)
                &&result==RESULT_OK&&data!=null&&!canEditConfiguration()) {
            deferredRequest=request;deferredResultCode=result;deferredActivityResult=data;return;
        }
        if(request==CONFIG_EXPORT_REQUEST||request==CONFIG_IMPORT_REQUEST) {
            ModuleDiagnostics.app(this,"config","Configuration picker returned; request="+request+" accepted="+(result==RESULT_OK)+" document="+(data!=null&&data.getData()!=null));
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&!transferringConfig)transferConfig(request,data.getData());
            else if(result==RESULT_OK)Toast.makeText(this,"文件选择器未返回有效文件，请重新选择",Toast.LENGTH_LONG).show();
            return;
        }
        if (request == LOG_EXPORT_REQUEST) {
            if (result == RESULT_OK && data != null && data.getData() != null) {
                final Uri uri = data.getData();
                new Thread(() -> {
                    try { ModuleDiagnostics.writeExport(this, uri);
                        runOnUiThread(() -> Toast.makeText(this,"日志文件已保存",Toast.LENGTH_SHORT).show()); }
                    catch (Exception error) { ModuleDiagnostics.error("export","Could not save diagnostics file",error);
                        runOnUiThread(() -> Toast.makeText(this,"保存失败，请重新选择保存位置",Toast.LENGTH_LONG).show()); }
                },"C17-log-export").start();
            }
            return;
        }
        if(request!=FONT_REQUEST||result!=RESULT_OK||data==null||data.getData()==null||importing)return;
        Uri uri=data.getData();String displayName="自选字体";
        try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))displayName=cursor.getString(0);
        } catch(Exception ignored){}
        final String name=displayName.length()>120?displayName.substring(0,120):displayName;importing=true;selectGroup(selected);
        new Thread(()->{
            try {
                FontRepository.PreparedFont prepared=FontRepository.prepareFont(getApplicationContext(),uri);
                runOnUiThread(()->{
                    try(FontRepository.PreparedFont pending=prepared) {
                        SharedPreferences original=StatusBarSettings.preferences(getApplicationContext());
                        java.util.Map<String,?> previous=original.getAll();
                        prepared.commit(()->resumed&&canEditConfiguration()&&!isFinishing()&&!isDestroyed(),
                                ()->preferences.edit().putString(StatusBarSettings.FONT_MODE,"custom").putString(StatusBarSettings.FONT_REVISION,prepared.revision).putString(StatusBarSettings.FONT_NAME,name).commit(),
                                ()->{
                                    // Rollback restores only this import's three prior options, even if activation was revoked.
                                    SharedPreferences.Editor restore=original.edit();
                                    for(String key:new String[]{StatusBarSettings.FONT_MODE,StatusBarSettings.FONT_REVISION,StatusBarSettings.FONT_NAME}) {
                                        if(previous.containsKey(key))restore.putString(key,(String)previous.get(key));else restore.remove(key);
                                    }
                                    if(!restore.commit())throw new IllegalStateException("原字体选项恢复未能写入存储");
                                });
                        importing=false;changed();selectGroup(selected);Toast.makeText(this,"字体已导入",Toast.LENGTH_SHORT).show();
                    } catch(Exception invalid) { finishFontImportFailure(invalid); }
                });
            } catch(Exception invalid) {
                runOnUiThread(()->finishFontImportFailure(invalid));
            }
        },"C17-font-import").start();
    }
    private void finishFontImportFailure(Exception invalid) {
        ModuleDiagnostics.error("font","User font import failed; previous font retained",invalid);
        importing=false;if(!isFinishing()&&!isDestroyed()) { selectGroup(selected);Toast.makeText(this,"导入失败："+invalid.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    private void transferConfig(int request,Uri uri) {
        if(!requireRuntimeActive())return;
        if(request==CONFIG_IMPORT_REQUEST)pendingImportReplayed=true;
        transferringConfig=true;
        if(request==CONFIG_IMPORT_REQUEST)pendingImportUri=uri.toString();
        new Thread(()->{
            try {
                if(request==CONFIG_EXPORT_REQUEST) {
                    try(java.io.OutputStream output=getContentResolver().openOutputStream(uri,"wt")) {
                        if(output==null)throw new java.io.IOException("Output unavailable");
                        ConfigTransfer.write(output,preferences.getAll());
                    }
                    ModuleDiagnostics.app(this,"config","Configuration export completed");
                    runOnUiThread(()->{transferringConfig=false;if(!isFinishing()&&!isDestroyed())Toast.makeText(this,"配置已导出",Toast.LENGTH_SHORT).show();});
                } else {
                    final ConfigTransfer.PreparedImport prepared;
                    try(java.io.InputStream input=getContentResolver().openInputStream(uri)) {
                        if(input==null)throw new java.io.IOException("Input unavailable");
                        prepared=ConfigTransfer.read(input,ConfigTransfer.customFontAvailable(this));
                    }
                    runOnUiThread(()->{
                        transferringConfig=false;if(isFinishing()||isDestroyed())return;
                        String message="文件已通过检查，包含 "+prepared.count+" 项设置。确认后覆盖对应配置。";
                        if(prepared.fontFallback)message+="\n\n本机尚未导入自选字体，将使用系统字体。";
                        new AlertDialog.Builder(this).setTitle("导入这份配置？").setMessage(message)
                            .setNegativeButton("取消",(dialog,which)->pendingImportUri=null)
                            .setOnCancelListener(dialog->pendingImportUri=null)
                            .setPositiveButton("导入",(dialog,which)->{pendingImportUri=null;applyConfig(prepared);}).show();
                    });
                }
            } catch(Exception invalid) {
                ModuleDiagnostics.error("config",request==CONFIG_EXPORT_REQUEST?"Configuration export failed":"Configuration validation failed; settings retained",invalid);
                runOnUiThread(()->{transferringConfig=false;pendingImportUri=null;if(!isFinishing()&&!isDestroyed())Toast.makeText(this,request==CONFIG_EXPORT_REQUEST?"导出失败，请重新选择保存位置":"导入失败：文件格式、版本或设置值不符合要求",Toast.LENGTH_LONG).show();});
            }
        },"C17-config-transfer").start();
    }
    private void applyConfig(ConfigTransfer.PreparedImport prepared) {
        if(!requireRuntimeActive())return;
        if(transferringConfig)return;transferringConfig=true;
        new Thread(()->{
            boolean success=false;
            try{success=ConfigTransfer.commit(preferences,prepared);}
            catch(RuntimeException error){ModuleDiagnostics.error("config","Configuration application failed",error);}
            // Publish only a successful complete import, even if the Activity was rotated.
            try{if(success&&SettingsSnapshot.notificationAllowed(rawPreferences))getApplicationContext().getContentResolver().notifyChange(SETTINGS_URI,null);}
            catch(RuntimeException error){ModuleDiagnostics.error("config","Configuration refresh notification failed",error);}
            final boolean applied=success;
            runOnUiThread(()->{transferringConfig=false;if(isFinishing()||isDestroyed())return;
                changed();selectGroup(selected);
                Toast.makeText(this,applied?"配置已导入并应用":"保存失败，请稍后重试",Toast.LENGTH_LONG).show();
            });
        },"C17-config-apply").start();
    }
    private void updateSample() {
        if(sample==null||preferences==null||selected<0)return;
        FontRepository.configure(this,enabled("font_enabled")?string(StatusBarSettings.FONT_MODE):"system",string(StatusBarSettings.FONT_REVISION));
        Group group=groups[selected];String value="";int weight=500;
        if(group.key.equals("font"))value="09月30日 周三 下午 5G KB/s";
        else if(isClockGroup(group.key)) { String previewGroup=clockPreviewGroup(group.key);value=TimeFormat.format(string(clockPatternKey(previewGroup)),System.currentTimeMillis());weight=Math.round(number(clockWeightKey(previewGroup))); }
        else if(group.key.equals("carrier")) { value=string(CarrierPanels.key(carrierPanel,"mode")).equals("text")?string(CarrierPanels.key(carrierPanel,"text")):TimeFormat.format(string(CarrierPanels.key(carrierPanel,"pattern")),System.currentTimeMillis());weight=Math.round(number(CarrierPanels.key(carrierPanel,"weight"))); }
        sample.setText(value);sample.setTypeface(FontRepository.typeface(Typeface.DEFAULT,weight));
    }
    private void schedulePreview() {
        handler.removeCallbacks(previewTick);if(!resumed||selected<0)return;
        if(groups[selected].key.equals("notification_big_clock")) {
            if(qsPreviews.isEmpty())return;
            boolean seconds=TimeFormat.hasSeconds(string(NotificationBigClockSettings.PATTERN))
                    ||enabled(NotificationBigClockSettings.DATE_ENABLED)&&TimeFormat.hasSeconds(string(NotificationBigClockSettings.DATE_PATTERN))
                    ||enabled(NotificationBigClockSettings.FOOTER_ENABLED)&&NotificationBigClockSettings.footerHasSeconds(string(NotificationBigClockSettings.FOOTER_PATTERN));
            handler.postDelayed(previewTick,TimeFormat.nextDelay(System.currentTimeMillis(),seconds));return;
        }
        if(sample==null)return;
        String key=groups[selected].key;if(!isClockGroup(key)&&!key.equals("carrier"))return;
        if(key.equals("carrier")&&!string(CarrierPanels.key(carrierPanel,"mode")).equals("time"))return;
        String pattern=string(isClockGroup(key)?clockPatternKey(clockPreviewGroup(key)):CarrierPanels.key(carrierPanel,"pattern"));
        handler.postDelayed(previewTick,TimeFormat.nextDelay(System.currentTimeMillis(),TimeFormat.hasSeconds(pattern)));
    }
    private void addSlider(LinearLayout card, Setting setting) {
        LinearLayout box=column();box.setPadding(dp(2),dp(12),dp(2),dp(7));
        LinearLayout.LayoutParams boxParams=matchWrap();boxParams.topMargin=dp(8);card.addView(box,boxParams);
        LinearLayout labels = row();
        labels.setPadding(dp(2),0,0,dp(4));
        labels.addView(text(setting.title, 15, INK, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text("", 13, MUTED, false);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(dp(84));value.setMaxWidth(dp(144));value.setMinHeight(dp(44));
        value.setPadding(dp(8), dp(7), dp(8), dp(7));
        pressable(value,rounded(Color.TRANSPARENT,11,false));
        labels.addView(value, new LinearLayout.LayoutParams(-2, -2));
        box.addView(labels,matchWrap());
        LinearLayout adjust = row();
        TextView minus = stepButton("−");
        TextView plus = stepButton("+");
        SeekBar bar = new SeekBar(this);
        bar.setMax(Math.round((setting.max - setting.min) / setting.step));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(LINE));
        bar.setContentDescription(groups[selected].title + " " + setting.title);
        adjust.addView(bar,new LinearLayout.LayoutParams(0,dp(44),1));
        box.addView(adjust,matchWrap());
        SliderControl control = new SliderControl(setting, bar, value);
        controls.add(control);
        control.set(StatusBarSettings.settingNumber(preferences.getAll(), setting.key, setting.fallback()), false);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) control.set(setting.min + progress * setting.step, true);
            }
        });
        value.setOnClickListener(v -> editNumber(control));
        value.setContentDescription(groups[selected].title + " " + setting.title + "，点击输入");
        minus.setOnClickListener(v -> stepNumber(control,-1));
        plus.setOnClickListener(v -> stepNumber(control,1));
        minus.setContentDescription(setting.title + " 减少 " + setting.step);
        plus.setContentDescription(setting.title + " 增加 " + setting.step);
    }

    private void editNumber(SliderControl control) {
        Setting setting = control.setting;
        boolean completeCount=NotificationBigClockSettings.VISIBLE_COUNT.equals(setting.key);
        EditText input = input();
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                | (setting.step < 1 ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        input.setText(String.format(Locale.ROOT, setting.step == 1 ? "%.0f" : "%.2f", control.current));
        input.selectAll();
        LinearLayout content = column();
        content.setPadding(dp(20), dp(5), dp(20), dp(5));
        content.addView(text("滑条常用范围 " + setting.format(setting.min) + " ～ " + setting.format(setting.max), 12, MUTED, false));
        addHint(content,completeCount?"只支持 1 到 3 的整数。所有通知仍保留，滑动列表可继续查看。":signedNumber(setting.key)?"直接输入可超出此范围，位置与间距支持负值。":NotificationBigClockSettings.positiveSize(setting.key)?"直接输入支持大于 0 的数值，可超出滑条范围。":"直接输入支持 0 及以上的数值，可超出滑条范围。");
        content.addView(input, matchWrap());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(setting.title).setView(content)
                .setNegativeButton("取消", null).setPositiveButton("应用", null).create();
        dialog.setOnShowListener(ignored -> {
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    float number = NumericInput.parse(input.getText().toString(),setting.step == 1 ? 0 : 2);
                    if(completeCount&&(number<1f||number>3f
                            ||new BigDecimal(input.getText().toString().trim()).compareTo(BigDecimal.valueOf(number))!=0))
                        throw new NumberFormatException();
                    if (Float.isNaN(number) || Float.isInfinite(number) || (!signedNumber(setting.key)&&number<0)
                            ||NotificationBigClockSettings.positiveSize(setting.key)&&number<=0)
                        throw new NumberFormatException();
                    android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (ime != null) ime.hideSoftInputFromWindow(input.getWindowToken(), 0);
                    dialog.dismiss();
                    acceptNumber(control,number);
                } catch (NumberFormatException error) { input.setError(completeCount?"请输入 1 到 3 的整数":signedNumber(setting.key)?"请输入有效有限数值":NotificationBigClockSettings.positiveSize(setting.key)?"请输入大于 0 的有效有限数值":"请输入 0 或更大的有效有限数值"); }
            });
        });
        dialog.show();
    }
    private boolean signedNumber(String key) { return key.contains("offset_")||key.endsWith("spacing")||key.endsWith("line_gap"); }
    private void stepNumber(SliderControl control,int direction) {
        Setting setting=control.setting;float next=control.current+direction*setting.step;
        if(!Float.isFinite(next)||!signedNumber(setting.key)&&next<0||NotificationBigClockSettings.positiveSize(setting.key)&&next<=0)return;
        if(control.current>=setting.min&&control.current<=setting.max)next=Math.max(setting.min,Math.min(setting.max,next));
        acceptNumber(control,setting.round(next));
    }
    private void acceptNumber(SliderControl control,float value) {
        Setting setting=control.setting;
        if(NotificationBigClockSettings.VISIBLE_COUNT.equals(setting.key)) {
            if(Float.isFinite(value)&&value>=1f&&value<=3f)control.set(value,true);
            return;
        }
        if(value>=setting.min&&value<=setting.max) { control.set(value,true);return; }
        final long deadline=SystemClock.elapsedRealtime()+10000;
        AlertDialog confirm=new AlertDialog.Builder(this).setTitle("保存超出常用范围的配置？")
            .setMessage(setting.title+"："+setting.format(value)+"\n\n超出常用范围，10 秒内未选择将不保存。")
            .setPositiveButton("是，保存",null).setNegativeButton("否，不保存",null).create();
        final Runnable[] countdown=new Runnable[1];
        countdown[0]=()->{
            long remaining=deadline-SystemClock.elapsedRealtime();
            if(remaining<=0) { confirm.dismiss();return; }
            confirm.setMessage(setting.title+"："+setting.format(value)+"\n\n常用范围："+setting.format(setting.min)+" ～ "+setting.format(setting.max)
                +"\n可能移出屏幕、遮住其他内容或变得不可见。极大数值受系统绘制能力限制，字体粗细取决于字体支持。\n\n"+((remaining+999)/1000)+" 秒内未选择，将不保存。");
            handler.postDelayed(countdown[0],Math.min(1000,remaining));
        };
        confirm.setOnDismissListener(dialog->handler.removeCallbacks(countdown[0]));
        confirm.setOnShowListener(dialog->{
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(SystemClock.elapsedRealtime()<deadline)control.set(value,true);confirm.dismiss();
            });
            countdown[0].run();
        });confirm.show();
    }

    private void addColor(LinearLayout parent,String key,String title,boolean margin) {
        LinearLayout box=column();box.setPadding(dp(12),dp(12),dp(12),dp(12));box.setMinimumHeight(dp(82));
        box.addView(text(title,12,MUTED,false),matchWrap());
        LinearLayout valueRow=row();valueRow.setPadding(0,dp(10),0,0);
        View swatch=new View(this);GradientDrawable swatchBackground=rounded(color(key),7,true);swatch.setBackground(swatchBackground);
        valueRow.addView(swatch,new LinearLayout.LayoutParams(dp(22),dp(22)));
        TextView code=text(colorHex(key),11,INK,true);code.setTypeface(Typeface.MONOSPACE);code.setPadding(dp(8),0,0,0);
        valueRow.addView(code,new LinearLayout.LayoutParams(0,-2,1));box.addView(valueRow,matchWrap());
        box.setContentDescription(title+"颜色，点击输入");pressable(box,rounded(SURFACE,14,false));box.setOnClickListener(v->editColor(key,title));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(margin)params.leftMargin=dp(8);parent.addView(box,params);
    }

    private void editColor(String key, String title) {
        ColorPickerDialog.show(this, groups[selected].title + " · " + title, color(key),
                StatusBarSettings.customAlpha(preferences.getAll(), key), (color, customAlpha) -> {
                    preferences.edit().putInt(key, color).putBoolean(StatusBarSettings.alphaKey(key), customAlpha).apply();
                    changed(); selectGroup(selected);
                });
    }

    private void resetGroup(Group group) {
        if(group.key.equals("notification_big_clock")) {
            SharedPreferences.Editor reset=preferences.edit();
            for(java.util.Map.Entry<String,Boolean> item:NotificationBigClockSettings.BOOLEANS.entrySet())reset.putBoolean(item.getKey(),item.getValue());
            for(java.util.Map.Entry<String,Float> item:NotificationBigClockSettings.NUMBERS.entrySet())if(!NotificationBigClockSettings.COMPACT_WEIGHT.equals(item.getKey()))reset.putFloat(item.getKey(),item.getValue());
            for(java.util.Map.Entry<String,String> item:NotificationBigClockSettings.STRINGS.entrySet())reset.putString(item.getKey(),item.getValue());
            for(java.util.Map.Entry<String,Integer> item:NotificationBigClockSettings.COLORS.entrySet())reset.putInt(item.getKey(),item.getValue()).putBoolean(StatusBarSettings.alphaKey(item.getKey()),false);
            reset.apply();changed();selectGroup(selected);return;
        }
        if(group.key.equals("qs_appearance")) {
            SharedPreferences.Editor reset=preferences.edit();
            for(java.util.Map.Entry<String,Boolean> item:QsTileAppearance.BOOLEANS.entrySet())reset.putBoolean(item.getKey(),item.getValue());
            for(java.util.Map.Entry<String,Float> item:StatusBarSettings.NUMERIC_DEFAULTS.entrySet())if(item.getKey().startsWith("qs_global_"))reset.putFloat(item.getKey(),item.getValue());
            for(java.util.Map.Entry<String,Integer> item:StatusBarSettings.COLOR_DEFAULTS.entrySet())if(item.getKey().startsWith("qs_global_"))reset.putInt(item.getKey(),item.getValue()).putBoolean(StatusBarSettings.alphaKey(item.getKey()),false);
            reset.apply();changed();selectGroup(selected);return;
        }
        if(group.key.equals("qs_media")) {
            SharedPreferences.Editor reset=preferences.edit();
            for(java.util.Map.Entry<String,Boolean> item:QsMediaAppearance.BOOLEANS.entrySet())reset.putBoolean(item.getKey(),item.getValue());
            for(java.util.Map.Entry<String,Float> item:QsMediaAppearance.NUMBERS.entrySet())reset.putFloat(item.getKey(),item.getValue());
            for(java.util.Map.Entry<String,Integer> item:QsMediaAppearance.COLORS.entrySet())reset.putInt(item.getKey(),item.getValue()).putBoolean(StatusBarSettings.alphaKey(item.getKey()),false);
            reset.apply();changed();selectGroup(selected);return;
        }
        if(group.key.equals("notification_clear")) {
            SharedPreferences.Editor reset=preferences.edit();
            for(java.util.Map.Entry<String,Boolean> item:NotificationClearAppearance.BOOLEANS.entrySet())reset.putBoolean(item.getKey(),item.getValue());
            for(java.util.Map.Entry<String,Float> item:NotificationClearAppearance.NUMBERS.entrySet())reset.putFloat(item.getKey(),item.getValue());
            for(java.util.Map.Entry<String,Integer> item:NotificationClearAppearance.COLORS.entrySet())reset.putInt(item.getKey(),item.getValue()).putBoolean(StatusBarSettings.alphaKey(item.getKey()),false);
            reset.apply();changed();selectGroup(selected);return;
        }
        SharedPreferences.Editor writer=preferences.edit();for(Setting setting:group.settings)writer.putFloat(setting.key,setting.fallback());
        writer.putBoolean(masterKey(group.key),true);
        for(String[] item:featureKeys(group.key))writer.putBoolean(item[0],StatusBarSettings.BOOLEAN_DEFAULTS.get(item[0]));
        if(!group.key.equals("font")&&!group.key.equals("tiles"))writer.putInt(group.key+"_color_light",Color.BLACK).putInt(group.key+"_color_dark",Color.WHITE)
                .putBoolean(StatusBarSettings.alphaKey(group.key+"_color_light"),false).putBoolean(StatusBarSettings.alphaKey(group.key+"_color_dark"),false);
        if(group.key.equals("clock"))writer.putBoolean(StatusBarSettings.CLOCK_ENABLED,false).putString(StatusBarSettings.CLOCK_PATTERN,TimeFormat.CLOCK_DEFAULT);
        if(group.key.equals("shade_clock"))writer.putBoolean(masterKey(group.key),false)
                .putBoolean(StatusBarSettings.SHADE_CLOCK_ENABLED,false).putString(StatusBarSettings.SHADE_CLOCK_PATTERN,TimeFormat.CLOCK_DEFAULT);
        if(group.key.equals("carrier"))writer.putString(StatusBarSettings.CARRIER_MODE,"original").putString(StatusBarSettings.CARRIER_PATTERN,TimeFormat.CARRIER_DEFAULT)
                .putString(StatusBarSettings.CARRIER_TEXT,StatusBarSettings.STRING_DEFAULTS.get(StatusBarSettings.CARRIER_TEXT));
        if(isCarrierPanel(group.key))for(String suffix:new String[]{"mode","pattern","text"}) {
            String key=CarrierPanels.key(group.key,suffix);writer.putString(key,StatusBarSettings.STRING_DEFAULTS.get(key));
        }
        if(group.key.equals("data"))writer.putString(StatusBarSettings.SIGNAL_LAYOUT,"system");
        if(group.key.equals("font"))writer.putString(StatusBarSettings.FONT_MODE,"system");
        if(group.key.equals("battery")) {
            writer.putBoolean(StatusBarSettings.BATTERY_CHARGE_INSIDE,true);
            writer.putString("battery_style","pui");
            for(String item:new String[]{"battery_text","battery_bolt","battery_charge","battery_alert"})for(String scene:new String[]{"light","dark"}) {
                String key=item+"_color_"+scene;writer.putInt(key,StatusBarSettings.COLOR_DEFAULTS.get(key)).putBoolean(StatusBarSettings.alphaKey(key),false);
            }
        }
        writer.apply();changed();selectGroup(selected);
    }
    private void saveNumber(String key,float value) {
        if(NotificationBigClockSettings.VISIBLE_COUNT.equals(key))value=NotificationBigClockSettings.visibleCount(value);
        preferences.edit().putFloat(key,value).apply();changed();ModuleDiagnostics.app(this,"settings","Numeric setting saved: "+key);
    }
    private void changed() {
        android.os.Bundle diagnostics = new android.os.Bundle();
        diagnostics.putBoolean(StatusBarSettings.DIAGNOSTICS_ENABLED,enabled(StatusBarSettings.DIAGNOSTICS_ENABLED));
        ModuleDiagnostics.configure(this, diagnostics);
        FontRepository.configure(this,enabled("font_enabled")?string(StatusBarSettings.FONT_MODE):"system",string(StatusBarSettings.FONT_REVISION));
        // The value is saved immediately; one notification per display frame avoids applying
        // the entire runtime form many times before any of those changes can be displayed.
        if(SettingsSnapshot.notificationAllowed(rawPreferences)&&!settingsNotificationPending) { settingsNotificationPending=true;editor.postOnAnimation(settingsNotification); }
        updateSample();schedulePreview();
        for(View preview:qsPreviews)preview.invalidate();
        if(bigClockConflictHint!=null)bigClockConflictHint.setVisibility(enabled(NotificationBigClockSettings.MASTER)?View.VISIBLE:View.GONE);
    }
    private String string(String key) { return StatusBarSettings.string(preferences.getAll(),key); }
    private float number(String key) { return StatusBarSettings.settingNumber(preferences.getAll(),key,StatusBarSettings.NUMERIC_DEFAULTS.get(key)); }
    private int color(String key) { return StatusBarSettings.color(preferences.getAll(),key); }
    private String colorHex(String key) {
        return StatusBarSettings.customAlpha(preferences.getAll(),key)?String.format(Locale.ROOT,"#%08X",color(key)):hex(color(key));
    }
    private String hex(int color) { return Color.alpha(color)==255?String.format(Locale.ROOT,"#%06X",color&0xffffff):String.format(Locale.ROOT,"#%08X",color); }
    private EditText input() { EditText input=new EditText(this);input.setTextSize(17);input.setTextColor(INK);input.setHintTextColor(MUTED);input.setSingleLine(true);input.setPadding(dp(12),dp(12),dp(12),dp(12));input.setBackground(rounded(SURFACE,12,false));return input; }
    private TextView stepButton(String label) {
        TextView button = text(label, 22, INK, false);
        button.setGravity(Gravity.CENTER);pressable(button,rounded(SURFACE,14,false));return button;
    }
    private TextView text(String value, int sp, int color, boolean medium) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color);view.setIncludeFontPadding(false);view.setLineSpacing(dp(2),1f);
        if (medium) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return view;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private void addRowDividers(LinearLayout view) {
        GradientDrawable divider=new GradientDrawable();divider.setColor(LINE);divider.setSize(1,Math.max(1,dp(.5f)));
        view.setDividerDrawable(divider);view.setDividerPadding(dp(16));view.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
    }
    private final class PageColumn extends LinearLayout {
        PageColumn() { super(MainActivity.this);setOrientation(LinearLayout.VERTICAL); }
        @Override protected void onMeasure(int width,int height) {
            if(MeasureSpec.getSize(width)>dp(760))width=MeasureSpec.makeMeasureSpec(dp(760),MeasureSpec.EXACTLY);
            super.onMeasure(width,height);
        }
    }
    private final class NavigationRow extends LinearLayout {
        NavigationRow() { super(MainActivity.this);setOrientation(LinearLayout.HORIZONTAL);setGravity(Gravity.CENTER); }
        @Override protected void onMeasure(int width,int height) {
            if(MeasureSpec.getSize(width)>dp(540))width=MeasureSpec.makeMeasureSpec(dp(540),MeasureSpec.EXACTLY);
            super.onMeasure(width,height);
        }
    }
    private final class NavigationGlyph extends View {
        private final int page,color;
        NavigationGlyph(int page,int color) { super(MainActivity.this);this.page=page;this.color=color;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);paint.setColor(color);
            paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            int saved=canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);
            if(page==0) {
                android.graphics.Path home=new android.graphics.Path();home.moveTo(3,10);home.lineTo(12,3);home.lineTo(21,10);home.lineTo(21,21);home.lineTo(15,21);home.lineTo(15,14);home.lineTo(9,14);home.lineTo(9,21);home.lineTo(3,21);home.close();canvas.drawPath(home,paint);
            } else if(page==1) {
                for(int i=0;i<3;i++){float x=5+i*7,y=i==1?15:8;canvas.drawLine(x,3,x,y-2,paint);canvas.drawLine(x,y+2,x,21,paint);canvas.drawCircle(x,y,2,paint);}
            } else if(page==2) {
                canvas.drawCircle(12,12,9,paint);canvas.drawLine(12,11,12,17,paint);paint.setStyle(android.graphics.Paint.Style.FILL);canvas.drawCircle(12,7,1,paint);
            } else {
                paint.setStyle(android.graphics.Paint.Style.FILL);for(int i=0;i<3;i++)canvas.drawCircle(5+i*7,12,1.8f,paint);
            }
            canvas.restoreToCount(saved);
        }
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private GradientDrawable rounded(int color, int radius, boolean border) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        if (border&&color!=CARD) drawable.setStroke(dp(1), LINE); return drawable;
    }
    private void pressable(View view,GradientDrawable background) {
        view.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x182f80ff),background,null));
    }
    private Switch styledSwitch(boolean checked) {
        Switch toggle=new Switch(this);toggle.setMinHeight(dp(44));toggle.setMinimumWidth(dp(52));toggle.setSwitchMinWidth(dp(52));toggle.setShowText(false);toggle.setSplitTrack(false);toggle.setChecked(checked);
        GradientDrawable thumb=new GradientDrawable();thumb.setShape(GradientDrawable.OVAL);thumb.setColor(Color.WHITE);thumb.setSize(dp(26),dp(26));toggle.setThumbDrawable(thumb);
        GradientDrawable track=rounded(ACCENT,16,false);track.setSize(dp(52),dp(30));toggle.setTrackDrawable(track);
        int[][] states={{android.R.attr.state_checked},{}};
        toggle.setThumbTintList(new android.content.res.ColorStateList(states,new int[]{Color.WHITE,Color.WHITE}));
        toggle.setTrackTintList(new android.content.res.ColorStateList(states,new int[]{darkUi?0xff3482ff:ACCENT,darkUi?0xff555555:0xffdddddd}));return toggle;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
