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
    private static final int BG=0xfff5f6fa, CARD=Color.WHITE, INK=0xff172033;
    private static final int MUTED=0xff778297, ACCENT=0xff4564ed, FONT_REQUEST=17, LOG_EXPORT_REQUEST=18;
    private static final int CONFIG_EXPORT_REQUEST=19, CONFIG_IMPORT_REQUEST=20;
    private static final Uri SETTINGS_URI=Uri.parse(StatusBarSettings.CONTENT_URI);
    private SharedPreferences preferences;
    private LinearLayout editor, tabs;
    private ScrollView pageScroll;
    private TextView sample;
    private TextView updateSummary;
    private int selected=-1;
    private String carrierPanel=CarrierPanels.NOTIFICATION;
    private String qsScene="light";
    private boolean transferringConfig;
    private String pendingImportUri;
    private boolean resumed, importing, checkingUpdates, checkedUpdatesThisSession;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<SliderControl> controls=new ArrayList<>();
    private final Runnable previewTick=() -> { updateSample(); schedulePreview(); };
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
        new Group("clock", "状态栏时间", "格式、位置、大小与字距独立设置。长日期会占用更多状态栏空间。", new Setting[]{
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
        new Group("qs_appearance","磁贴活动调色","全局调整内部活动填充，保留 ColorOS 17 原生玻璃与高光。",new Setting[]{})
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
            current=setting.round(number);float slider=Math.max(setting.min,Math.min(setting.max,current));
            bar.setProgress(Math.round((slider-setting.min)/setting.step));
            value.setText(setting.format(current)+"  ✎");if(save)saveNumber(setting.key,current);
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);preferences=StatusBarSettings.preferences(this);
        SharedPreferences migrations=getSharedPreferences("app_migrations",MODE_PRIVATE);
        if(!migrations.getBoolean("native_edge_fade_114",false)) {
            preferences.edit().putBoolean("tiles_blur_enabled",false).apply();
            migrations.edit().putBoolean("native_edge_fade_114",true).apply();
        }
        if(state!=null)selected=Math.max(-1,Math.min(groups.length-1,state.getInt("group",-1)));
        if(state!=null&&CarrierPanels.isPanel(state.getString("carrier_panel")))carrierPanel=state.getString("carrier_panel");
        if(state!=null&&"dark".equals(state.getString("qs_scene")))qsScene="dark";
        for(String key:new String[]{StatusBarSettings.WIFI_OFFSET_X,StatusBarSettings.WIFI_OFFSET_Y,
                StatusBarSettings.WIFI_ICON_SCALE,StatusBarSettings.DATA_OFFSET_X,StatusBarSettings.DATA_OFFSET_Y,StatusBarSettings.DATA_ICON_SCALE}) {
            if(!preferences.contains(key))preferences.edit().putFloat(key,StatusBarSettings.settingNumber(preferences.getAll(),key,
                    StatusBarSettings.NUMERIC_DEFAULTS.get(key))).apply();
        }
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),dp(12),dp(18),dp(8));
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                root.setPadding(dp(18)+safe.left,dp(12)+safe.top,dp(18)+safe.right,dp(8)+safe.bottom);
            } else root.setPadding(dp(18),dp(12)+insets.getSystemWindowInsetTop(),dp(18),dp(8)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout brand=row();brand.addView(text("更好的C17状态栏",21,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView version=text(appVersion(),10,ACCENT,true);version.setPadding(dp(9),dp(5),dp(9),dp(5));version.setBackground(rounded(0xffeaf0ff,10,false));brand.addView(version);
        root.addView(brand,matchWrap());
        TextView author=text("作者 aiingjie  ·  自由调整你的状态栏",11,MUTED,false);author.setPadding(0,dp(5),0,dp(14));root.addView(author,matchWrap());
        tabs=row();root.addView(tabs,matchWrap());
        pageScroll=new ScrollView(this);pageScroll.setFillViewport(false);pageScroll.setVerticalScrollBarEnabled(false);
        editor=column();editor.setPadding(dp(1),dp(12),dp(1),dp(18));pageScroll.addView(editor,matchWrap());
        root.addView(pageScroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        selectGroup(selected);
        if(state!=null&&state.getString("pending_import_uri")!=null) {
            pendingImportUri=state.getString("pending_import_uri");
            transferConfig(CONFIG_IMPORT_REQUEST,Uri.parse(pendingImportUri));
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("group",selected);state.putString("carrier_panel",carrierPanel);state.putString("qs_scene",qsScene);if(pendingImportUri!=null)state.putString("pending_import_uri",pendingImportUri);super.onSaveInstanceState(state); }
    @Override protected void onResume() {
        super.onResume();resumed=true;changed();
        if(!checkedUpdatesThisSession) {
            checkedUpdatesThisSession=true;long last=getSharedPreferences("github_update",MODE_PRIVATE).getLong("last_attempt",0);
            if(System.currentTimeMillis()-last>=86400000L)checkUpdates(false);
        }
    }
    @Override protected void onPause() { resumed=false;handler.removeCallbacks(previewTick);super.onPause(); }
    @Override public void onBackPressed() { if(selected>=0)selectGroup(-1);else super.onBackPressed(); }
    private void selectGroup(int index) {
        int oldScroll=selected==index&&pageScroll!=null?pageScroll.getScrollY():0;
        selected=index;sample=null;updateSummary=null;handler.removeCallbacks(previewTick);tabs.removeAllViews();
        controls.clear();editor.removeAllViews();
        if(index<0) { showOverview();pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return; }
        TextView back=text("‹  全部功能",13,ACCENT,true);back.setPadding(dp(12),dp(10),dp(12),dp(10));back.setMinHeight(dp(44));back.setBackground(rounded(0xffeaf0ff,12,false));
        back.setContentDescription("返回全部功能");back.setOnClickListener(v->selectGroup(-1));tabs.addView(back);
        TextView precision=text("0.01 精度 · 点击数值输入",10,MUTED,false);precision.setGravity(Gravity.RIGHT);tabs.addView(precision,new LinearLayout.LayoutParams(0,-2,1));
        Group group=groups[index];LinearLayout card=column();
        card.setPadding(dp(15),dp(15),dp(15),dp(15));card.setBackground(rounded(CARD,18,true));editor.addView(card,matchWrap());
        LinearLayout title=row();title.addView(text(group.title,18,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        Switch master=new Switch(this);master.setChecked(enabled(masterKey(group.key)));master.setContentDescription(group.title+" 总开关");
        master.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(masterKey(group.key),checked).apply();changed();selectGroup(selected);});title.addView(master);card.addView(title,matchWrap());
        addHint(card,group.detail);if(!enabled(masterKey(group.key)))addHint(card,"本项已停用，当前使用系统显示。开启后应用以下设置。");
        if(group.key.equals("carrier")) {
            addCarrierEditor(card);updateSample();schedulePreview();pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return;
        }
        if(group.key.equals("qs_appearance")) {
            addQsAppearance(card);addReset(group);pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return;
        }
        addFeaturePanel(card,group);
        if(group.key.equals("tiles")) {
            List<Setting> visibleSettings=new ArrayList<>();for(Setting setting:group.settings)if(!setting.key.equals("tiles_blur_radius")||enabled("tiles_blur_enabled"))visibleSettings.add(setting);
            addSettings(card,new Group(group.key,group.title,group.detail,visibleSettings.toArray(new Setting[0])));addHint(card,"渐隐只作用于磁贴左右边缘，停稳后恢复清晰。区域高度为 0 时使用当前磁贴区域全高；左／右范围为 0 时关闭该侧效果。顶部状态栏不参与处理。");
            addHint(card,"水平位置会将渐隐区域移入或移出磁贴边缘。若渐隐过窄，可先把对应水平位置设为 0，再调整范围。");
            addReset(group);pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return;
        }
        if(group.key.equals("font")) { addFonts(card);addReset(group);pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return; }
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
            addReset(group);pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));return;
        }
        if(group.key.equals("data")) {
            addChoice(card,StatusBarSettings.SIGNAL_LAYOUT,new String[]{"system","single"},new String[]{"跟随系统单双排","只显示主信号单排"});
            addHint(card,"单排使用系统提供的主信号强度，双卡次信号仍由系统维护。");
        }
        if(group.key.equals("clock")) {
            addFormat(card,StatusBarSettings.CLOCK_PATTERN);
        }
        addSettings(card,group);
        addSection(card,"图标与文字颜色");
        LinearLayout colors=row();addColor(colors,group.key+"_color_light","浅色背景",false);
        addColor(colors,group.key+"_color_dark","深色背景",true);card.addView(colors,matchWrap());
        addHint(card,"自动跟随当前界面明暗。六位颜色保留系统透明度，八位颜色可指定透明度。");
        addReset(group);updateSample();schedulePreview();pageScroll.post(()->pageScroll.scrollTo(0,oldScroll));
    }
    private void showOverview() {
        int count=0;for(Group group:groups)if(enabled(masterKey(group.key)))count++;
        tabs.addView(text("全部功能",16,INK,true),new LinearLayout.LayoutParams(0,-2,1));tabs.addView(text(count+" / "+groups.length+" 已开启",11,ACCENT,true));
        String[] shortNames={"实时网速","蜂窝信号","Wi-Fi","网络文字","时间格式","运营商文字","字体","电池","磁贴翻页","磁贴调色"};
        String[] descriptions={"数字、单位与间距","信号样式与原生角标","图标与数据箭头","5G / 4G / 3G","日期、星期与时段","通知、控制中心与锁屏","系统、苹方或自选","PUI 外观与充电动画","原生滑动与边缘渐隐","全局填充与原生高光"};
        for(int rowIndex=0;rowIndex<groups.length;rowIndex+=2) {
            LinearLayout line=row();
            for(int i=rowIndex;i<Math.min(rowIndex+2,groups.length);i++) {
                final int target=i;Group group=groups[i];LinearLayout box=column();box.setPadding(dp(13),dp(14),dp(13),dp(11));box.setBackground(rounded(CARD,18,true));
                box.addView(text(shortNames[i],15,INK,true));TextView detail=text(descriptions[i],10,MUTED,false);detail.setPadding(0,dp(7),0,dp(12));box.addView(detail,matchWrap());
                LinearLayout bottom=row();TextView enter=text("调整  ›",11,ACCENT,true);enter.setPadding(0,dp(8),0,dp(8));bottom.addView(enter,new LinearLayout.LayoutParams(0,-2,1));
                Switch master=new Switch(this);master.setChecked(enabled(masterKey(group.key)));master.setContentDescription(group.title+" 总开关");master.setMinHeight(dp(44));
                master.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(masterKey(group.key),checked).apply();changed();selectGroup(-1);});bottom.addView(master);box.addView(bottom,matchWrap());
                box.setContentDescription("打开"+group.title);box.setOnClickListener(v->selectGroup(target));
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(i>rowIndex)params.leftMargin=dp(12);line.addView(box,params);
            }
            LinearLayout.LayoutParams params=matchWrap();if(rowIndex>0)params.topMargin=dp(12);editor.addView(line,params);
        }
        addHint(editor,"每项可独立启停。进入项目设置位置、大小、颜色及其他功能；颜色支持 HSB 调色盘。");
        addConfigTransfer(editor);
        addUpdates(editor);
        addDiagnostics(editor);
    }
    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; }
        catch(android.content.pm.PackageManager.NameNotFoundException unavailable) { return "1.14.0"; }
    }
    private void addQsAppearance(LinearLayout card) {
        addHint(card,"统一应用于 Wi-Fi、数据和其他活动磁贴，以及亮度、音量滑条的已填充部分。");
        LinearLayout scenes=row();scenes.setPadding(0,dp(8),0,dp(12));
        for(String scene:new String[]{"light","dark"}) {
            boolean active=scene.equals(qsScene);TextView choice=text(scene.equals("dark")?"深色主题":"浅色主题",13,active?Color.WHITE:ACCENT,true);
            choice.setGravity(Gravity.CENTER);choice.setPadding(dp(12),dp(13),dp(12),dp(13));choice.setBackground(rounded(active?ACCENT:0xffedf0fa,12,false));
            choice.setContentDescription("编辑"+choice.getText());choice.setOnClickListener(v->{qsScene=scene;selectGroup(selected);});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(scene.equals("dark"))p.leftMargin=dp(8);scenes.addView(choice,p);
        }
        card.addView(scenes,matchWrap());final boolean dark=qsScene.equals("dark");final String prefix="qs_global_"+qsScene;
        View preview=new View(this) {
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                android.graphics.RectF glass=new android.graphics.RectF(dp(1),dp(1),getWidth()-dp(1),getHeight()-dp(1));
                paint.setShader(new android.graphics.LinearGradient(0,0,getWidth(),getHeight(),dark?0xff253347:0xffe5edf8,dark?0xff414b60:0xffcdd8eb,android.graphics.Shader.TileMode.CLAMP));
                canvas.drawRoundRect(glass,dp(18),dp(18),paint);paint.setShader(null);paint.setColor(dark?0x18ffffff:0x60ffffff);
                android.graphics.RectF circle=new android.graphics.RectF(dp(14),dp(20),dp(62),dp(68));canvas.drawOval(circle,paint);
                android.graphics.RectF track=new android.graphics.RectF(dp(78),dp(32),getWidth()-dp(14),dp(56));canvas.drawRoundRect(track,dp(12),dp(12),paint);
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
        preview.setContentDescription((dark?"深色":"浅色")+"活动填充预览");card.addView(preview,new LinearLayout.LayoutParams(-1,dp(88)));
        addHint(card,"效果示意 · 实际控件沿用系统材质。浅色与深色设置随系统主题自动切换。");
        addSection(card,"活动填充颜色");LinearLayout colors=row();addColor(colors,prefix+"_color","填充颜色",false);addColor(colors,prefix+"_gradient_color","渐变终点",true);card.addView(colors,matchWrap());
        addToggle(card,prefix+"_gradient_enabled","启用渐变");
        addSettings(card,new Group("qs_appearance","","",new Setting[]{new Setting("填充透明度",prefix+"_opacity","%",0,100,.01f),new Setting("渐变方向",prefix+"_gradient_angle","°",0,360,.01f)}));
        addHint(card,"填充透明度与颜色透明度相乘，并跟随系统原有的透明度变化。渐变方向：0° 从左到右，90° 从上到下。");
    }
    private void addToggle(LinearLayout parent,String key,String label) {
        LinearLayout line=row();line.setPadding(0,dp(8),0,dp(4));line.setMinimumHeight(dp(44));
        line.addView(text(label,12,INK,false),new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);toggle.setChecked(enabled(key));toggle.setContentDescription((key.contains("_dark_")?"深色主题":key.contains("_light_")?"浅色主题":"")+label+"开关");
        toggle.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(key,checked).apply();changed();selectGroup(selected);});line.addView(toggle);parent.addView(line,matchWrap());
    }
    private void addConfigTransfer(LinearLayout parent) {
        addSection(parent,"配置管理");LinearLayout box=column();box.setPadding(dp(13),dp(13),dp(13),dp(13));box.setBackground(rounded(CARD,14,true));
        addAction(box,"导出配置","保存位置、大小、颜色与功能设置",()->pickConfig(true));
        addAction(box,"导入配置","检查文件并确认后应用",()->pickConfig(false));
        addHint(box,"配置文件包含自定义文字。自选字体需在新设备另行导入；日志开关由当前设备保留。");parent.addView(box,matchWrap());
    }
    private void pickConfig(boolean export) {
        if(transferringConfig){Toast.makeText(this,"正在处理配置，请稍候",Toast.LENGTH_SHORT).show();return;}
        Intent picker=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);picker.addCategory(Intent.CATEGORY_OPENABLE);picker.setType(export?"application/json":"*/*");
        if(export)picker.putExtra(Intent.EXTRA_TITLE,"C17-config-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new java.util.Date())+".json");
        try{startActivityForResult(picker,export?CONFIG_EXPORT_REQUEST:CONFIG_IMPORT_REQUEST);}
        catch(android.content.ActivityNotFoundException unavailable){Toast.makeText(this,"未找到文件选择器",Toast.LENGTH_LONG).show();}
    }
    private void addUpdates(LinearLayout parent) {
        addSection(parent,"版本与更新");LinearLayout box=column();box.setPadding(dp(13),dp(13),dp(13),dp(13));box.setBackground(rounded(CARD,14,true));
        box.addView(text("当前版本 "+appVersion(),13,INK,true));
        updateSummary=text(checkingUpdates?"正在检查 GitHub 发布版本…":getSharedPreferences("github_update",MODE_PRIVATE).getString("last_message","启动时每天检查一次，也可以手动检查。"),11,MUTED,false);
        updateSummary.setPadding(0,dp(6),0,dp(5));box.addView(updateSummary,matchWrap());
        addAction(box,"检查更新","SANWU5 / c17-statusbar",()->checkUpdates(true));
        addAction(box,"打开 GitHub 项目","查看源码与正式发布版本",()->openUpdateUrl(GitHubUpdates.REPO_URL));parent.addView(box,matchWrap());
    }
    private void addDiagnostics(LinearLayout parent) {
        addSection(parent,"维护与日志");
        LinearLayout card=column();card.setPadding(dp(13),dp(12),dp(13),dp(12));card.setBackground(rounded(CARD,18,true));
        LinearLayout line=row();line.addView(text("记录诊断日志",14,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);toggle.setChecked(enabled(StatusBarSettings.DIAGNOSTICS_ENABLED));toggle.setContentDescription("诊断日志开关");
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
    private String masterKey(String group) { return group.equals("clock")?"clock_controls_enabled":group+"_enabled"; }
    private boolean enabled(String key) { return StatusBarSettings.bool(preferences.getAll(),key); }
    private String[][] featureKeys(String group) {
        List<String[]> keys=new ArrayList<>();
        if(!group.equals("font")&&!group.equals("tiles")) {
            keys.add(new String[]{group+"_position_enabled","位置调整"});keys.add(new String[]{group+"_size_enabled","大小调整"});keys.add(new String[]{group+"_color_enabled","颜色调整"});
        }
        if(group.equals("data")) {
            keys.add(new String[]{"data_icon_enabled","仿 iOS 信号样式"});keys.add(new String[]{"data_badge_hidden","隐藏系统网络角标"});keys.add(new String[]{"data_activity_hidden","隐藏蜂窝上传下载箭头"});keys.add(new String[]{"data_single_enabled","应用单排信号选项"});
        } else if(group.equals("wifi")) {
            keys.add(new String[]{"wifi_icon_enabled","仿 iOS Wi-Fi 样式"});keys.add(new String[]{"wifi_activity_hidden","隐藏 Wi-Fi 上传下载箭头"});keys.add(new String[]{"wifi_badge_hidden","隐藏 Wi-Fi 角标"});
        } else if(group.equals("label")) {
            keys.add(new String[]{"label_normalize_enabled","统一网络制式名称"});keys.add(new String[]{"label_text_style_enabled","文字粗细"});
        } else if(group.equals("clock")) {
            keys.add(new String[]{StatusBarSettings.CLOCK_ENABLED,"自定义时间格式"});keys.add(new String[]{"clock_text_style_enabled","文字粗细与字距"});
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
        LinearLayout panel=column();panel.setPadding(dp(10),dp(5),dp(10),dp(8));panel.setBackground(rounded(0xfff5f7fd,12,false));panel.setVisibility(View.GONE);
        addAction(card,"独立功能开关  ⌄",keys.length+" 个开关 · 点击展开",()->panel.setVisibility(panel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        for(String[] key:keys) {
            LinearLayout line=row();line.setPadding(0,dp(4),0,dp(4));line.setMinimumHeight(dp(46));line.addView(text(key[1],12,INK,false),new LinearLayout.LayoutParams(0,-2,1));
            Switch toggle=new Switch(this);toggle.setChecked(enabled(key[0]));toggle.setContentDescription(key[1]+"开关");
            toggle.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(key[0],checked).apply();changed();});line.addView(toggle);panel.addView(line,matchWrap());
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
            final String panel=values[i];boolean active=panel.equals(carrierPanel);TextView choice=text(labels[i],12,active?Color.WHITE:ACCENT,true);
            choice.setContentDescription("编辑"+labels[i]+"运营商");choice.setGravity(Gravity.CENTER);choice.setPadding(dp(8),dp(13),dp(8),dp(13));
            choice.setBackground(rounded(active?ACCENT:0xffedf3ff,10,false));choice.setOnClickListener(v->{carrierPanel=panel;selectGroup(selected);pageScroll.post(()->pageScroll.scrollTo(0,0));});
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(i>0)params.leftMargin=dp(6);panels.addView(choice,params);
        }
        card.addView(panels,matchWrap());Group group=carrierGroup();LinearLayout line=row();line.setPadding(0,dp(12),0,dp(4));
        line.addView(text(group.title,14,INK,true),new LinearLayout.LayoutParams(0,-2,1));Switch master=new Switch(this);
        master.setChecked(enabled(masterKey(group.key)));master.setContentDescription(group.title+" 独立开关");
        master.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(masterKey(group.key),checked).apply();changed();});line.addView(master);card.addView(line,matchWrap());
        addHint(card,carrierPanel.equals(CarrierPanels.LOCKSCREEN)?"锁屏独立设置默认关闭。开启后应用此处参数，关闭恢复系统运营商内容。":"下面的设置只作用于所选界面。未单独修改的参数沿用之前的设置，各界面可分别恢复默认值。");
        addFeaturePanel(card,group);String modeKey=CarrierPanels.key(group.key,"mode"),patternKey=CarrierPanels.key(group.key,"pattern"),textKey=CarrierPanels.key(group.key,"text");
        addChoice(card,modeKey,new String[]{"original","time","text"},new String[]{"运营商","自定义时间","纯文本"});
        String mode=string(modeKey);if(mode.equals("time"))addFormat(card,patternKey);
        if(mode.equals("text")) { addAction(card,"编辑文本",string(textKey),()->editText(textKey));addSample(card); }
        addSettings(card,group);addSection(card,"此界面的文字颜色");LinearLayout colors=row();
        addColor(colors,CarrierPanels.key(group.key,"color_light"),"浅色背景",false);addColor(colors,CarrierPanels.key(group.key,"color_dark"),"深色背景",true);card.addView(colors,matchWrap());
        addHint(card,"颜色与透明度只作用于此界面，按原生文字颜色自动切换。");addReset(group);
    }
    private void addSection(LinearLayout card,String title) { TextView label=text(title,13,INK,true);label.setPadding(0,dp(18),0,dp(3));card.addView(label,matchWrap()); }
    private void addSettings(LinearLayout card,Group group) {
        String previous="";
        for(Setting setting:group.settings) {
            String section=setting.key.startsWith("qs_global_")?"填充效果":setting.key.startsWith("tiles_")?"边缘范围与位置":setting.key.contains("offset_")?"位置":setting.key.contains("scale")?"大小":setting.key.contains("seconds")?"充电切换节奏":"文字与间距";
            if(!previous.equals(section)) { addSection(card,section);previous=section; }addSlider(card,setting);
        }
    }
    private void addReset(Group group) { addAction(editor,"恢复本组默认值","恢复开关及本组显示参数",()->new AlertDialog.Builder(this).setTitle("恢复"+group.title).setMessage("重置本组设置。").setNegativeButton("取消",null).setPositiveButton("恢复",(dialog,which)->resetGroup(group)).show()); }
    private void addHint(LinearLayout parent,String value) {
        TextView hint=text(value,11,MUTED,false);hint.setPadding(0,dp(6),0,dp(8));parent.addView(hint,matchWrap());
    }
    private void addBatteryColors(LinearLayout card,String item,String title) {
        TextView heading=text(title,14,INK,true);heading.setPadding(0,dp(14),0,dp(8));card.addView(heading,matchWrap());
        String label=item.equals("battery")?"外形":item.equals("battery_text")?"数字":item.equals("battery_bolt")?"闪电":item.equals("battery_charge")?"充电填充":"低电量／省电";
        LinearLayout choices=row();addColor(choices,item+"_color_light",label+" · 浅色背景",false);
        addColor(choices,item+"_color_dark",label+" · 深色背景",true);card.addView(choices,matchWrap());
    }
    private void addAction(LinearLayout parent,String title,String detail,Runnable action) {
        LinearLayout box=column();box.setPadding(dp(12),dp(12),dp(12),dp(12));box.setBackground(rounded(0xffedf3ff,12,false));
        box.addView(text(title,13,ACCENT,true));if(!detail.isEmpty())box.addView(text(detail,11,MUTED,false));
        box.setOnClickListener(v->action.run());LinearLayout.LayoutParams params=matchWrap();params.topMargin=dp(8);parent.addView(box,params);
    }
    private void addChoice(LinearLayout card,String key,String[] values,String[] labels) {
        LinearLayout choices=row();String stored=string(key);
        for(int i=0;i<values.length;i++) {
            final String value=values[i];TextView choice=text(labels[i],11,value.equals(stored)?Color.WHITE:ACCENT,true);
            choice.setGravity(Gravity.CENTER);choice.setPadding(dp(6),dp(12),dp(6),dp(12));choice.setMinHeight(dp(44));
            choice.setBackground(rounded(value.equals(stored)?ACCENT:0xffedf3ff,10,false));
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
        sample=text("09月29日 周二 下午 5G KB/s",20,INK,false);sample.setPadding(0,dp(20),0,dp(8));card.addView(sample,matchWrap());
        addHint(card,"字体文件复制到模块内部，原文件移动后仍可使用。");updateSample();
    }
    private void addFormat(LinearLayout card,String key) {
        addAction(card,"编辑时间格式",string(key),()->editFormat(key));
        addAction(card,"选择常用格式","时分、秒、年月日、星期、上午下午、时段",()->chooseFormat(key));addSample(card);
    }
    private void addSample(LinearLayout card) {
        TextView label=text("当前内容预览",10,MUTED,false);label.setPadding(0,dp(12),0,dp(4));card.addView(label,matchWrap());
        sample=text("",18,INK,false);sample.setPadding(dp(10),dp(10),dp(10),dp(10));sample.setBackground(rounded(BG,10,false));card.addView(sample,matchWrap());
    }
    private void chooseFormat(String key) {
        String[] patterns={"HH:mm","HH:mm:ss","{上午下午} hh:mm","{时段} HH:mm","M月d日 {周} HH:mm","yyyy年MM月dd日 {星期} HH:mm","MM/dd HH:mm"};
        String[] labels=new String[patterns.length];long now=System.currentTimeMillis();
        for(int i=0;i<patterns.length;i++)labels[i]=TimeFormat.format(patterns[i],now);
        new AlertDialog.Builder(this).setTitle("常用时间格式").setItems(labels,(dialog,which)->{
            preferences.edit().putString(key,patterns[which]).apply();changed();selectGroup(selected);
        }).setNegativeButton("取消",null).show();
    }
    private void editFormat(String key) {
        LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(8));EditText field=input();
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TimeFormat.MAX_PATTERN)});
        field.setText(string(key));content.addView(field,matchWrap());TextView result=text("",14,INK,false);content.addView(result,matchWrap());
        addHint(content,"点击添加：年 / 月 / 日 / 周 / 星期 / 时 / 12时 / 分 / 秒 / 上午下午 / 时段。");
        String[][] tokens={{"年","月","日","周","星期"},{"时","12时","分","秒"},{"上午下午","时段"}};
        for(String[] line:tokens) {
            LinearLayout row=row();for(String token:line) {
                TextView chip=text(token,11,ACCENT,true);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(3),dp(10),dp(3),dp(10));
                chip.setOnClickListener(v->{int start=Math.max(0,field.getSelectionStart()),end=Math.max(start,field.getSelectionEnd());field.getText().replace(start,end,"{"+token+"}");});
                row.addView(chip,new LinearLayout.LayoutParams(0,-2,1));
            }content.addView(row,matchWrap());
        }
        addHint(content,"也可输入 yyyy-MM-dd HH:mm:ss 等格式。固定英文放在单引号内，例如 'C17' HH:mm；中文直接输入。时段按小时显示凌晨、早晨、上午、中午、下午、晚上。");
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("自定义时间格式").setView(scroll).setNegativeButton("取消",null).setPositiveButton("应用",null).create();
        Runnable validate=()->{
            String pattern=field.getText().toString(),error=TimeFormat.validationError(pattern);
            result.setText(error==null?TimeFormat.format(pattern,System.currentTimeMillis()):error);result.setTextColor(error==null?INK:0xffc23434);
            if(dialog.getButton(AlertDialog.BUTTON_POSITIVE)!=null)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(error==null);
        };
        field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){validate.run();}public void afterTextChanged(Editable value){}});
        dialog.setOnShowListener(ignored->{validate.run();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String pattern=field.getText().toString();if(TimeFormat.validationError(pattern)!=null){validate.run();return;}
            preferences.edit().putString(key,pattern).apply();changed();selectGroup(selected);dialog.dismiss();
        });});dialog.show();
    }
    private void editText(String key) {
        EditText field=input();field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TimeFormat.MAX_TEXT)});field.setText(string(key));
        new AlertDialog.Builder(this).setTitle("编辑自定义文本").setView(field).setNegativeButton("取消",null).setPositiveButton("应用",(dialog,which)->{
            preferences.edit().putString(key,field.getText().toString().replace('\n',' ').replace('\r',' ')).apply();changed();selectGroup(selected);
        }).show();
    }
    private void pickFont() {
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);picker.addCategory(Intent.CATEGORY_OPENABLE);picker.setType("*/*");
        try { startActivityForResult(picker,FONT_REQUEST); } catch(android.content.ActivityNotFoundException unavailable) { Toast.makeText(this,"未找到文件选择器",Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
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
                String revision=FontRepository.importFont(getApplicationContext(),uri);
                preferences.edit().putString(StatusBarSettings.FONT_MODE,"custom").putString(StatusBarSettings.FONT_REVISION,revision).putString(StatusBarSettings.FONT_NAME,name).apply();
                runOnUiThread(()->{importing=false;changed();if(!isFinishing()&&!isDestroyed()) { selectGroup(selected);Toast.makeText(this,"字体已导入",Toast.LENGTH_SHORT).show(); }});
            } catch(Exception invalid) {
                ModuleDiagnostics.error("font","User font import failed; previous font retained",invalid);
                runOnUiThread(()->{importing=false;if(!isFinishing()&&!isDestroyed()) { selectGroup(selected);Toast.makeText(this,"导入失败："+invalid.getMessage(),Toast.LENGTH_LONG).show(); }});
            }
        },"C17-font-import").start();
    }
    private void transferConfig(int request,Uri uri) {
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
        if(transferringConfig)return;transferringConfig=true;
        new Thread(()->{
            boolean success=false;
            try{success=ConfigTransfer.commit(preferences,prepared);}
            catch(RuntimeException error){ModuleDiagnostics.error("config","Configuration application failed",error);}
            // Publish the completed snapshot even if this Activity was rotated or closed.
            try{getApplicationContext().getContentResolver().notifyChange(SETTINGS_URI,null);}
            catch(RuntimeException error){ModuleDiagnostics.error("config","Configuration refresh notification failed",error);}
            final boolean applied=success;
            runOnUiThread(()->{transferringConfig=false;if(isFinishing()||isDestroyed())return;
                changed();selectGroup(selected);
                Toast.makeText(this,applied?"配置已导入并应用":"保存失败，请稍后重试",Toast.LENGTH_LONG).show();
            });
        },"C17-config-apply").start();
    }
    private void updateSample() {
        if(sample==null||preferences==null)return;
        FontRepository.configure(this,enabled("font_enabled")?string(StatusBarSettings.FONT_MODE):"system",string(StatusBarSettings.FONT_REVISION));
        Group group=groups[selected];String value="";int weight=500;
        if(group.key.equals("font"))value="09月30日 周三 下午 5G KB/s";
        else if(group.key.equals("clock")) { value=TimeFormat.format(string(StatusBarSettings.CLOCK_PATTERN),System.currentTimeMillis());weight=Math.round(number(StatusBarSettings.CLOCK_WEIGHT)); }
        else if(group.key.equals("carrier")) { value=string(CarrierPanels.key(carrierPanel,"mode")).equals("text")?string(CarrierPanels.key(carrierPanel,"text")):TimeFormat.format(string(CarrierPanels.key(carrierPanel,"pattern")),System.currentTimeMillis());weight=Math.round(number(CarrierPanels.key(carrierPanel,"weight"))); }
        sample.setText(value);sample.setTypeface(FontRepository.typeface(Typeface.DEFAULT,weight));
    }
    private void schedulePreview() {
        handler.removeCallbacks(previewTick);if(!resumed||sample==null)return;
        String key=groups[selected].key;if(!key.equals("clock")&&!key.equals("carrier"))return;
        if(key.equals("carrier")&&!string(CarrierPanels.key(carrierPanel,"mode")).equals("time"))return;
        String pattern=string(key.equals("clock")?StatusBarSettings.CLOCK_PATTERN:CarrierPanels.key(carrierPanel,"pattern"));
        handler.postDelayed(previewTick,TimeFormat.nextDelay(System.currentTimeMillis(),TimeFormat.hasSeconds(pattern)));
    }
    private void addSlider(LinearLayout card, Setting setting) {
        LinearLayout labels = row();
        labels.setPadding(0, dp(10), 0, 0);
        labels.addView(text(setting.title, 13, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text("", 12, ACCENT, true);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(dp(95));
        value.setPadding(dp(8), dp(7), dp(8), dp(7));
        value.setBackground(rounded(0xffedf3ff, 10, false));
        labels.addView(value, new LinearLayout.LayoutParams(-2, -2));
        card.addView(labels, matchWrap());
        LinearLayout adjust = row();
        TextView minus = stepButton("−");
        TextView plus = stepButton("+");
        SeekBar bar = new SeekBar(this);
        bar.setMax(Math.round((setting.max - setting.min) / setting.step));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffdce3ef));
        bar.setContentDescription(groups[selected].title + " " + setting.title);
        adjust.addView(minus, new LinearLayout.LayoutParams(dp(34), dp(36)));
        adjust.addView(bar, new LinearLayout.LayoutParams(0, dp(36), 1));
        adjust.addView(plus, new LinearLayout.LayoutParams(dp(34), dp(36)));
        card.addView(adjust, matchWrap());
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
        EditText input = input();
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                | (setting.step < 1 ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        input.setText(String.format(Locale.ROOT, setting.step == 1 ? "%.0f" : "%.2f", control.current));
        input.selectAll();
        LinearLayout content = column();
        content.setPadding(dp(20), dp(5), dp(20), dp(5));
        content.addView(text("滑条常用范围 " + setting.format(setting.min) + " ～ " + setting.format(setting.max), 12, MUTED, false));
        addHint(content,signedNumber(setting.key)?"直接输入可超出此范围，位置与间距支持负值。":"直接输入支持 0 及以上的数值，可超出滑条范围。");
        content.addView(input, matchWrap());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(setting.title).setView(content)
                .setNegativeButton("取消", null).setPositiveButton("应用", null).create();
        dialog.setOnShowListener(ignored -> {
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    float number = NumericInput.parse(input.getText().toString(),setting.step == 1 ? 0 : 2);
                    if (Float.isNaN(number) || Float.isInfinite(number) || (!signedNumber(setting.key)&&number<0))
                        throw new NumberFormatException();
                    android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (ime != null) ime.hideSoftInputFromWindow(input.getWindowToken(), 0);
                    dialog.dismiss();
                    handler.postDelayed(()->acceptNumber(control,number),180);
                } catch (NumberFormatException error) { input.setError(signedNumber(setting.key)?"请输入有效有限数值":"请输入 0 或更大的有效有限数值"); }
            });
        });
        dialog.show();
    }
    private boolean signedNumber(String key) { return key.contains("offset_")||key.endsWith("spacing")||key.endsWith("line_gap"); }
    private void stepNumber(SliderControl control,int direction) {
        Setting setting=control.setting;float next=control.current+direction*setting.step;
        if(!Float.isFinite(next)||!signedNumber(setting.key)&&next<0)return;
        if(control.current>=setting.min&&control.current<=setting.max)next=Math.max(setting.min,Math.min(setting.max,next));
        acceptNumber(control,setting.round(next));
    }
    private void acceptNumber(SliderControl control,float value) {
        Setting setting=control.setting;
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

    private void addColor(LinearLayout parent, String key, String title, boolean margin) {
        int color = color(key);
        LinearLayout box = column();
        box.setPadding(dp(10), dp(9), dp(10), dp(9));
        box.setBackground(rounded(margin ? 0xff202936 : 0xfff4f6fa, 12, false));
        box.addView(text(title, 11, margin ? 0xffc5cede : MUTED, false));
        LinearLayout colorValue = row();
        colorValue.setPadding(0, dp(6), 0, 0);
        View swatch = new View(this);
        swatch.setBackground(rounded(color, 5, true));
        colorValue.addView(swatch, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView value = text(colorHex(key), 11, margin ? Color.WHITE : INK, true);
        value.setPadding(dp(6), 0, 0, 0);
        colorValue.addView(value);
        box.addView(colorValue);
        box.setContentDescription(title + "颜色，点击输入");
        box.setOnClickListener(v -> editColor(key, title));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (margin) params.leftMargin = dp(8);
        parent.addView(box, params);
    }

    private void editColor(String key, String title) {
        ColorPickerDialog.show(this, groups[selected].title + " · " + title, color(key),
                StatusBarSettings.customAlpha(preferences.getAll(), key), (color, customAlpha) -> {
                    preferences.edit().putInt(key, color).putBoolean(StatusBarSettings.alphaKey(key), customAlpha).apply();
                    changed(); selectGroup(selected);
                });
    }

    private void resetGroup(Group group) {
        if(group.key.equals("qs_appearance")) {
            SharedPreferences.Editor reset=preferences.edit();
            for(java.util.Map.Entry<String,Boolean> item:StatusBarSettings.BOOLEAN_DEFAULTS.entrySet())if(item.getKey().startsWith("qs_"))reset.putBoolean(item.getKey(),item.getValue());
            for(java.util.Map.Entry<String,Float> item:StatusBarSettings.NUMERIC_DEFAULTS.entrySet())if(item.getKey().startsWith("qs_global_"))reset.putFloat(item.getKey(),item.getValue());
            for(java.util.Map.Entry<String,Integer> item:StatusBarSettings.COLOR_DEFAULTS.entrySet())if(item.getKey().startsWith("qs_global_"))reset.putInt(item.getKey(),item.getValue()).putBoolean(StatusBarSettings.alphaKey(item.getKey()),false);
            reset.apply();changed();selectGroup(selected);return;
        }
        SharedPreferences.Editor writer=preferences.edit();for(Setting setting:group.settings)writer.putFloat(setting.key,setting.fallback());
        writer.putBoolean(masterKey(group.key),true);
        for(String[] item:featureKeys(group.key))writer.putBoolean(item[0],StatusBarSettings.BOOLEAN_DEFAULTS.get(item[0]));
        if(!group.key.equals("font")&&!group.key.equals("tiles"))writer.putInt(group.key+"_color_light",Color.BLACK).putInt(group.key+"_color_dark",Color.WHITE)
                .putBoolean(StatusBarSettings.alphaKey(group.key+"_color_light"),false).putBoolean(StatusBarSettings.alphaKey(group.key+"_color_dark"),false);
        if(group.key.equals("clock"))writer.putBoolean(StatusBarSettings.CLOCK_ENABLED,false).putString(StatusBarSettings.CLOCK_PATTERN,TimeFormat.CLOCK_DEFAULT);
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
    private void saveNumber(String key,float value) { preferences.edit().putFloat(key,value).apply();changed();ModuleDiagnostics.app(this,"settings","Numeric setting saved: "+key); }
    private void changed() {
        android.os.Bundle diagnostics = new android.os.Bundle();
        diagnostics.putBoolean(StatusBarSettings.DIAGNOSTICS_ENABLED,enabled(StatusBarSettings.DIAGNOSTICS_ENABLED));
        ModuleDiagnostics.configure(this, diagnostics);
        FontRepository.configure(this,enabled("font_enabled")?string(StatusBarSettings.FONT_MODE):"system",string(StatusBarSettings.FONT_REVISION));
        getContentResolver().notifyChange(SETTINGS_URI,null);updateSample();schedulePreview();
    }
    private String string(String key) { return StatusBarSettings.string(preferences.getAll(),key); }
    private float number(String key) { return StatusBarSettings.settingNumber(preferences.getAll(),key,StatusBarSettings.NUMERIC_DEFAULTS.get(key)); }
    private int color(String key) { return StatusBarSettings.color(preferences.getAll(),key); }
    private String colorHex(String key) {
        return StatusBarSettings.customAlpha(preferences.getAll(),key)?String.format(Locale.ROOT,"#%08X",color(key)):hex(color(key));
    }
    private String hex(int color) { return Color.alpha(color)==255?String.format(Locale.ROOT,"#%06X",color&0xffffff):String.format(Locale.ROOT,"#%08X",color); }
    private EditText input() { EditText input = new EditText(this); input.setTextSize(18); input.setSingleLine(true); return input; }
    private TextView stepButton(String label) {
        TextView button = text(label, 21, ACCENT, true);
        button.setGravity(Gravity.CENTER); return button;
    }
    private TextView text(String value, int sp, int color, boolean medium) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color);
        if (medium) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return view;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private GradientDrawable rounded(int color, int radius, boolean border) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        if (border) drawable.setStroke(dp(1), 0xffe1e6ef); return drawable;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
