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
import android.widget.HorizontalScrollView;
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
    private static final int BG=0xfff4f6fa, CARD=Color.WHITE, INK=0xff18202d;
    private static final int MUTED=0xff718096, ACCENT=0xff2869e8, FONT_REQUEST=17;
    private static final Uri SETTINGS_URI=Uri.parse(StatusBarSettings.CONTENT_URI);
    private SharedPreferences preferences;
    private LinearLayout editor, tabs;
    private TextView sample;
    private int selected=1;
    private boolean resumed, importing;
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
        new Group("carrier", "下拉运营商文字", "可保留运营商、替换为时间或自定义文本；时间格式独立保存。", new Setting[]{
            pos("水平位置",StatusBarSettings.CARRIER_OFFSET_X,true),pos("垂直位置",StatusBarSettings.CARRIER_OFFSET_Y,false),
            scale("文字大小",StatusBarSettings.CARRIER_SCALE),weight(StatusBarSettings.CARRIER_WEIGHT),
            new Setting("字距微调",StatusBarSettings.CARRIER_SPACING," dp",-2,8,.01f)}),
        new Group("font", "文字字体", "统一用于时间、下拉替换文字、网络制式和网速。",new Setting[0]),
        new Group("battery", "电池", "原有电池外形与真实电量保留，外形、数字和闪电整体移动、缩放。", new Setting[]{
            pos("水平位置",StatusBarSettings.BATTERY_OFFSET_X,true),pos("垂直位置",StatusBarSettings.BATTERY_OFFSET_Y,false),
            scale("整体大小",StatusBarSettings.BATTERY_SCALE),scale("宽度微调",StatusBarSettings.BATTERY_WIDTH_SCALE),scale("高度微调",StatusBarSettings.BATTERY_HEIGHT_SCALE),
            new Setting("每项停留时间",StatusBarSettings.BATTERY_HOLD," 秒",1,10,.01f),
            new Setting("淡入淡出时长",StatusBarSettings.BATTERY_FADE," 秒",.15f,3,.01f)})
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
        String format(float number) { return String.format(Locale.ROOT,step==1?"%.0f%s":"%.2f%s",number,unit); }
        float round(float number) { return Math.max(min,Math.min(max,Math.round(number/step)*step)); }
    }
    private final class SliderControl {
        final Setting setting; final SeekBar bar; final TextView value; float current;
        SliderControl(Setting setting,SeekBar bar,TextView value) { this.setting=setting;this.bar=bar;this.value=value; }
        void set(float number,boolean save) {
            current=setting.round(number);bar.setProgress(Math.round((current-setting.min)/setting.step));
            value.setText(setting.format(current)+"  ✎");if(save)saveNumber(setting.key,current);
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);preferences=StatusBarSettings.preferences(this);
        if(state!=null)selected=Math.max(0,Math.min(groups.length-1,state.getInt("group",1)));
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
        root.addView(text("更好的C17状态栏",23,INK,true),matchWrap());
        TextView author=text("作者 aiingjie",11,MUTED,false);author.setPadding(0,dp(3),0,0);root.addView(author,matchWrap());
        TextView hint=text("点击数值直接输入 · 位置和大小精确到 0.01",11,MUTED,false);
        hint.setPadding(0,dp(4),0,dp(12));root.addView(hint,matchWrap());
        HorizontalScrollView navigation=new HorizontalScrollView(this);navigation.setHorizontalScrollBarEnabled(false);
        tabs=row();navigation.addView(tabs);root.addView(navigation,matchWrap());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        editor=column();editor.setPadding(dp(1),dp(12),dp(1),dp(18));scroll.addView(editor,matchWrap());
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        selectGroup(selected);
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("group",selected);super.onSaveInstanceState(state); }
    @Override protected void onResume() { super.onResume();resumed=true;updateSample();schedulePreview(); }
    @Override protected void onPause() { resumed=false;handler.removeCallbacks(previewTick);super.onPause(); }
    private void selectGroup(int index) {
        selected=index;sample=null;handler.removeCallbacks(previewTick);tabs.removeAllViews();
        String[] titles={"网速","蜂窝","Wi-Fi","网络文字","时间","下拉文字","字体","电池"};
        for(int i=0;i<groups.length;i++) {
            final int target=i;TextView tab=text(titles[i],12,i==index?Color.WHITE:MUTED,true);
            tab.setGravity(Gravity.CENTER);tab.setPadding(dp(14),dp(12),dp(14),dp(12));tab.setMinHeight(dp(44));
            tab.setBackground(rounded(i==index?ACCENT:0xffe9edf4,12,false));tab.setOnClickListener(v->selectGroup(target));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);if(i>0)params.leftMargin=dp(6);tabs.addView(tab,params);
        }
        controls.clear();editor.removeAllViews();Group group=groups[index];LinearLayout card=column();
        card.setPadding(dp(15),dp(15),dp(15),dp(15));card.setBackground(rounded(CARD,18,true));editor.addView(card,matchWrap());
        card.addView(text(group.title,18,INK,true),matchWrap());addHint(card,group.detail);
        if(group.key.equals("font")) { addFonts(card);return; }
        if(group.key.equals("battery")) {
            Switch enabled=new Switch(this);enabled.setText("电池内充电动画");enabled.setTextColor(INK);
            enabled.setPadding(0,dp(8),0,dp(8));enabled.setChecked(preferences.getBoolean(StatusBarSettings.BATTERY_CHARGE_INSIDE,true));
            enabled.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(StatusBarSettings.BATTERY_CHARGE_INSIDE,checked).apply();changed();});
            card.addView(enabled,matchWrap());
            addHint(card,"充电时：镂空闪电 → 电量 → 镂空闪电，平滑淡入淡出。透明切口随切换恢复，闪电颜色自动跟随系统明暗。停止充电后显示原生电量。");
            for(Setting setting:group.settings)addSlider(card,setting);
            addBatteryColors(card,"battery","电池外形与普通填充");
            addBatteryColors(card,"battery_text","电量数字");
            addBatteryColors(card,"battery_bolt","充电闪电");
            addBatteryColors(card,"battery_charge","充电填充");
            addBatteryColors(card,"battery_alert","低电量／省电填充");
            addHint(card,"数字、闪电、电池外形和填充分别调色，默认保留系统颜色与透明度。浅色、深色背景分别设置；六位色值跟随原生透明度，八位色值可自定义透明度。位置与大小即时应用；透明切口会一起移动和缩放。");
            addHint(card,"位置和大小用于整个电池控件，内部动画与颜色适配当前系统横向电池。其他电池样式保留原生内容。");
            addAction(editor,"恢复本组默认值","",()->new AlertDialog.Builder(this).setTitle("恢复电池设置")
                .setMessage("重置本组设置。").setNegativeButton("取消",null).setPositiveButton("恢复",(dialog,which)->resetGroup(group)).show());
            return;
        }
        if(group.key.equals("data")) {
            addChoice(card,StatusBarSettings.SIGNAL_LAYOUT,new String[]{"system","single"},new String[]{"跟随系统单双排","只显示主信号单排"});
            addHint(card,"单排使用系统提供的主信号强度，双卡次信号仍由系统维护。");
        }
        if(group.key.equals("clock")) {
            Switch enabled=new Switch(this);enabled.setText("自定义时间格式");enabled.setTextColor(INK);enabled.setPadding(0,dp(8),0,dp(8));
            enabled.setChecked(preferences.getBoolean(StatusBarSettings.CLOCK_ENABLED,false));
            enabled.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(StatusBarSettings.CLOCK_ENABLED,checked).apply();changed();});
            card.addView(enabled,matchWrap());addFormat(card,StatusBarSettings.CLOCK_PATTERN);
        }
        if(group.key.equals("carrier")) {
            addChoice(card,StatusBarSettings.CARRIER_MODE,new String[]{"original","time","text"},new String[]{"运营商","自定义时间","纯文本"});
            String mode=string(StatusBarSettings.CARRIER_MODE);
            if(mode.equals("time"))addFormat(card,StatusBarSettings.CARRIER_PATTERN);
            if(mode.equals("text")) {
                addAction(card,"编辑文本",string(StatusBarSettings.CARRIER_TEXT),()->editText());addSample(card);
            }
        }
        for(Setting setting:group.settings)addSlider(card,setting);
        TextView colorTitle=text("背景对应颜色",14,INK,true);colorTitle.setPadding(0,dp(14),0,dp(8));card.addView(colorTitle,matchWrap());
        LinearLayout colors=row();addColor(colors,group.key+"_color_light","浅色背景",false);
        addColor(colors,group.key+"_color_dark","深色背景",true);card.addView(colors,matchWrap());
        addHint(card,"自动跟随当前界面明暗。六位颜色保留系统透明度，八位颜色可指定透明度。");
        addAction(editor,"恢复本组默认值","",()->new AlertDialog.Builder(this).setTitle("恢复"+group.title)
                .setMessage("重置本组设置。").setNegativeButton("取消",null).setPositiveButton("恢复",(dialog,which)->resetGroup(group)).show());
        updateSample();schedulePreview();
    }
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
    private void editText() {
        EditText field=input();field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TimeFormat.MAX_TEXT)});field.setText(string(StatusBarSettings.CARRIER_TEXT));
        new AlertDialog.Builder(this).setTitle("下拉面板文本").setView(field).setNegativeButton("取消",null).setPositiveButton("应用",(dialog,which)->{
            preferences.edit().putString(StatusBarSettings.CARRIER_TEXT,field.getText().toString().replace('\n',' ').replace('\r',' ')).apply();changed();selectGroup(selected);
        }).show();
    }
    private void pickFont() {
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);picker.addCategory(Intent.CATEGORY_OPENABLE);picker.setType("*/*");
        try { startActivityForResult(picker,FONT_REQUEST); } catch(android.content.ActivityNotFoundException unavailable) { Toast.makeText(this,"未找到文件选择器",Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request!=FONT_REQUEST||result!=RESULT_OK||data==null||data.getData()==null||importing)return;
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
                runOnUiThread(()->{importing=false;if(!isFinishing()&&!isDestroyed()) { selectGroup(selected);Toast.makeText(this,"导入失败："+invalid.getMessage(),Toast.LENGTH_LONG).show(); }});
            }
        },"C17-font-import").start();
    }
    private void updateSample() {
        if(sample==null||preferences==null)return;
        FontRepository.configure(this,string(StatusBarSettings.FONT_MODE),string(StatusBarSettings.FONT_REVISION));
        Group group=groups[selected];String value="";int weight=500;
        if(group.key.equals("font"))value="09月29日 周二 下午 5G KB/s";
        else if(group.key.equals("clock")) { value=TimeFormat.format(string(StatusBarSettings.CLOCK_PATTERN),System.currentTimeMillis());weight=Math.round(number(StatusBarSettings.CLOCK_WEIGHT)); }
        else if(group.key.equals("carrier")) { value=string(StatusBarSettings.CARRIER_MODE).equals("text")?string(StatusBarSettings.CARRIER_TEXT):TimeFormat.format(string(StatusBarSettings.CARRIER_PATTERN),System.currentTimeMillis());weight=Math.round(number(StatusBarSettings.CARRIER_WEIGHT)); }
        sample.setText(value);sample.setTypeface(FontRepository.typeface(Typeface.DEFAULT,weight));
    }
    private void schedulePreview() {
        handler.removeCallbacks(previewTick);if(!resumed||sample==null)return;
        String key=groups[selected].key;if(!key.equals("clock")&&!key.equals("carrier"))return;
        if(key.equals("carrier")&&!string(StatusBarSettings.CARRIER_MODE).equals("time"))return;
        String pattern=string(key.equals("clock")?StatusBarSettings.CLOCK_PATTERN:StatusBarSettings.CARRIER_PATTERN);
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
        control.set(StatusBarSettings.number(preferences, setting.key, setting.fallback()), false);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) control.set(setting.min + progress * setting.step, true);
            }
        });
        value.setOnClickListener(v -> editNumber(control));
        value.setContentDescription(groups[selected].title + " " + setting.title + "，点击输入");
        minus.setOnClickListener(v -> control.set(control.current - setting.step, true));
        plus.setOnClickListener(v -> control.set(control.current + setting.step, true));
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
        content.addView(text("范围 " + setting.format(setting.min) + " ～ " + setting.format(setting.max), 12, MUTED, false));
        content.addView(input, matchWrap());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(setting.title).setView(content)
                .setNegativeButton("取消", null).setPositiveButton("应用", null).create();
        dialog.setOnShowListener(ignored -> {
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    float number = new BigDecimal(input.getText().toString().trim())
                            .setScale(setting.step == 1 ? 0 : 2, RoundingMode.HALF_UP).floatValue();
                    if (Float.isNaN(number) || Float.isInfinite(number) || number < setting.min || number > setting.max)
                        throw new NumberFormatException();
                    control.set(number, true);
                    android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (ime != null) ime.hideSoftInputFromWindow(input.getWindowToken(), 0);
                    dialog.dismiss();
                } catch (NumberFormatException error) { input.setError("请输入范围内的有效数值"); }
            });
        });
        dialog.show();
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
        SharedPreferences.Editor writer=preferences.edit();for(Setting setting:group.settings)writer.putFloat(setting.key,setting.fallback());
        writer.putInt(group.key+"_color_light",Color.BLACK).putInt(group.key+"_color_dark",Color.WHITE)
                .putBoolean(StatusBarSettings.alphaKey(group.key+"_color_light"),false).putBoolean(StatusBarSettings.alphaKey(group.key+"_color_dark"),false);
        if(group.key.equals("clock"))writer.putBoolean(StatusBarSettings.CLOCK_ENABLED,false).putString(StatusBarSettings.CLOCK_PATTERN,TimeFormat.CLOCK_DEFAULT);
        if(group.key.equals("carrier"))writer.putString(StatusBarSettings.CARRIER_MODE,"original").putString(StatusBarSettings.CARRIER_PATTERN,TimeFormat.CARRIER_DEFAULT)
                .putString(StatusBarSettings.CARRIER_TEXT,StatusBarSettings.STRING_DEFAULTS.get(StatusBarSettings.CARRIER_TEXT));
        if(group.key.equals("data"))writer.putString(StatusBarSettings.SIGNAL_LAYOUT,"system");
        if(group.key.equals("battery")) {
            writer.putBoolean(StatusBarSettings.BATTERY_CHARGE_INSIDE,true);
            for(String item:new String[]{"battery_text","battery_bolt","battery_charge","battery_alert"})for(String scene:new String[]{"light","dark"}) {
                String key=item+"_color_"+scene;writer.putInt(key,StatusBarSettings.COLOR_DEFAULTS.get(key)).putBoolean(StatusBarSettings.alphaKey(key),false);
            }
        }
        writer.apply();changed();selectGroup(selected);
    }
    private void saveNumber(String key,float value) { preferences.edit().putFloat(key,value).apply();changed(); }
    private void changed() {
        FontRepository.configure(this,string(StatusBarSettings.FONT_MODE),string(StatusBarSettings.FONT_REVISION));
        getContentResolver().notifyChange(SETTINGS_URI,null);updateSample();schedulePreview();
    }
    private String string(String key) { return StatusBarSettings.string(preferences.getAll(),key); }
    private float number(String key) { return StatusBarSettings.number(preferences,key,StatusBarSettings.NUMERIC_DEFAULTS.get(key)); }
    private int color(String key) {
        Object value=preferences.getAll().get(key);return value instanceof Number?((Number)value).intValue():StatusBarSettings.COLOR_DEFAULTS.get(key);
    }
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
