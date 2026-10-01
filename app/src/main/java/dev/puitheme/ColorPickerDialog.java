// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Shared, asset-free HSB picker. All edits stay local until Apply. */
public final class ColorPickerDialog {
    public interface Result { void apply(int color, boolean customAlpha); }
    private int BG = 0xfff5f5f5, CARD = Color.WHITE, INK = 0xff171717, MUTED = 0xff666666,
            ACCENT = 0xff2467df, SOFT = 0xffeeeeee, BORDER = 0xffdedede,
            ON_ACCENT = Color.WHITE, CHECKER_LIGHT = Color.WHITE, CHECKER_DARK = 0xffd3d8e1;
    private final Context context;
    private final HsbColor color;
    private final TextView[] values = new TextView[4];
    private final Palette[] palettes = new Palette[4];
    private EditText hex;
    private TextView summary, alphaHint;
    private Switch systemAlpha;
    private Preview preview;
    private boolean updating;

    private ColorPickerDialog(Context context, int color, boolean customAlpha) {
        this.context = context;
        this.color = new HsbColor(color, customAlpha);
        if ((context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            BG=0xff101010;CARD=0xff202020;INK=0xfff2f2f2;MUTED=0xffaaaaaa;
            ACCENT=0xff75aaff;SOFT=0xff303030;BORDER=0xff393939;
            ON_ACCENT=0xff101010;CHECKER_LIGHT=0xff656565;CHECKER_DARK=0xff454545;
        }
    }

    public static void show(Context context, String title, int color, boolean customAlpha, Result result) {
        new ColorPickerDialog(context, color, customAlpha).open(title, result);
    }

    private void open(String title, Result result) {
        LinearLayout shell = column();
        shell.setBackground(rounded(BG, 24));
        shell.setFocusableInTouchMode(true);
        LinearLayout titleBlock = column();
        titleBlock.setPadding(dp(22), dp(22), dp(22), dp(12));
        TextView titleView = text(title, 22, INK);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleBlock.addView(titleView, match());
        addSpace(titleBlock, text("调色 · 精确到 0.01", 12, MUTED), -2, 6);
        shell.addView(titleBlock, match());

        LinearLayout content = column();
        content.setPadding(dp(18), dp(4), dp(18), dp(12));
        LinearLayout previewCard = card();
        LinearLayout heading = row();
        preview = new Preview();
        preview.setContentDescription("当前颜色预览");
        heading.addView(preview, new LinearLayout.LayoutParams(dp(84), dp(84)));
        LinearLayout captions = column();
        captions.setPadding(dp(16), 0, 0, 0);
        captions.addView(text("颜色预览", 12, MUTED));
        summary = text("", 18, INK);
        summary.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        summary.setMaxLines(1);
        summary.setAutoSizeTextTypeUniformWithConfiguration(12, 18, 1, TypedValue.COMPLEX_UNIT_SP);
        addSpace(captions, summary, -2, 7);
        heading.addView(captions, new LinearLayout.LayoutParams(0, -2, 1));
        previewCard.addView(heading, match());

        hex = new EditText(context);
        styleInput(hex, 18);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
        hex.setHint("#RRGGBB / #AARRGGBB");
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setSelectAllOnFocus(true);
        hex.setContentDescription("十六进制颜色，6 位 RGB 或 8 位 ARGB");
        addSpace(previewCard, hex, -2, 16);
        addSpace(previewCard, text("6 位跟随系统透明度，8 位指定透明度", 12, MUTED), -2, 8);
        addSpace(content, previewCard, -2, 0);

        LinearLayout colorCard = card();
        colorCard.addView(sectionTitle("HSB 调色"), match());
        addSpace(colorCard, text("拖动滑条，或点击右侧数值精确输入", 12, MUTED), -2, 6);
        for (int i = 0; i < 3; i++) addSpace(colorCard, component(i), -2, i == 0 ? 16 : 8);
        addSpace(content, colorCard, -2, 14);

        LinearLayout alphaCard = card();
        alphaCard.addView(sectionTitle("透明度"), match());
        systemAlpha = new Switch(context);
        systemAlpha.setText("透明度跟随系统");
        systemAlpha.setTextSize(14);
        systemAlpha.setTextColor(INK);
        systemAlpha.setMinHeight(dp(56));
        systemAlpha.setSwitchPadding(dp(12));
        systemAlpha.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        systemAlpha.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{ACCENT, BORDER}));
        systemAlpha.setContentDescription("透明度跟随系统");
        addSpace(alphaCard, systemAlpha, -2, 6);
        LinearLayout alphaRow = row();
        alphaRow.addView(text("自定义透明度", 14, INK), new LinearLayout.LayoutParams(0, -2, 1));
        alphaRow.addView(valueButton(3));
        addSpace(alphaCard, alphaRow, -2, 10);
        palettes[3] = new Palette(3);
        palettes[3].setContentDescription("透明度滑条，0 至 100 百分比");
        addSpace(alphaCard, palettes[3], 60, 4);
        alphaHint = text("", 12, MUTED);
        alphaHint.setLineSpacing(dp(3), 1);
        addSpace(alphaCard, alphaHint, -2, 2);
        addSpace(content, alphaCard, -2, 14);

        LinearLayout presetsCard = card();
        presetsCard.addView(sectionTitle("常用颜色"), match());
        int[] presets = {0xff000000, 0xffffffff, 0xff64748b, 0xff3482ff, 0xff22c55e, 0xfff59e0b};
        String[] names = {"黑色", "白色", "灰蓝", "蓝色", "绿色", "琥珀"};
        for (int r = 0; r < 2; r++) {
            LinearLayout swatches = row();
            for (int c = 0; c < 3; c++) {
                int index = r * 3 + c;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                if (c > 0) params.leftMargin = dp(8);
                swatches.addView(preset(presets[index], names[index]), params);
            }
            addSpace(presetsCard, swatches, -2, r == 0 ? 12 : 8);
        }
        addSpace(content, presetsCard, -2, 14);
        addSpace(content, text("应用后保存；取消保留原来的颜色。", 12, MUTED), -2, 14);
        BoundedScroll scroll = new BoundedScroll();
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        shell.addView(scroll, match());
        AlertDialog dialog = new AlertDialog.Builder(context).setView(shell)
                .setNegativeButton("取消", null).setPositiveButton("应用", null).create();
        dialog.setView(shell,0,0,0,0);
        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!updating && color.readHex(s.toString())) { hex.setError(null); update(false); }
            }
            public void afterTextChanged(Editable editable) { }
        });
        systemAlpha.setOnCheckedChangeListener((button, checked) -> {
            if (!updating) { color.followSystem(checked); update(true); }
        });
        update(true);
        dialog.setOnShowListener(ignored -> {
            styleDialog(dialog);
            scroll.window = dialog.getWindow();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            shell.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                HsbColor parsed = new HsbColor(color.argb(), color.customAlpha());
                if (!parsed.readHex(hex.getText().toString())) {
                    hex.setError("请输入 6 位或 8 位十六进制色值");
                    hex.requestFocus();
                    return;
                }
                hideKeyboard(hex);
                result.apply(parsed.argb(), parsed.customAlpha());
                dialog.dismiss();
            });
        });
        dialog.setOnDismissListener(ignored -> hideKeyboard(hex));
        dialog.show();
    }

    private View component(int index) {
        LinearLayout box = column();
        LinearLayout heading = row();
        heading.addView(text(new String[]{"色相 H", "饱和度 S", "亮度 B"}[index], 14, INK),
                new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(valueButton(index));
        box.addView(heading, match());
        palettes[index] = new Palette(index);
        palettes[index].setContentDescription(new String[]{"色相 H，0 至 360 度", "饱和度 S，0 至 100 百分比", "亮度 B，0 至 100 百分比"}[index]);
        addSpace(box, palettes[index], 60, 3);
        return box;
    }

    private TextView valueButton(int index) {
        values[index] = text("", 14, ACCENT);
        values[index].setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        values[index].setGravity(Gravity.CENTER);
        values[index].setMaxLines(1);
        values[index].setMinHeight(dp(48));
        values[index].setMinWidth(dp(96));
        values[index].setPadding(dp(10), dp(8), dp(10), dp(8));
        values[index].setBackground(interactive(SOFT, 14));
        values[index].setFocusable(true);
        values[index].setOnClickListener(v -> editComponent(index));
        return values[index];
    }

    private View preset(int rgb, String name) {
        LinearLayout tile = row();
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(interactive(SOFT, 14));
        tile.setMinimumHeight(dp(54));
        tile.setPadding(dp(4), dp(6), dp(4), dp(6));
        View swatch = new View(context);
        GradientDrawable dot = rounded(rgb, 20);
        dot.setStroke(dp(1), BORDER);
        swatch.setBackground(dot);
        tile.addView(swatch, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView label = text(name, 12, INK);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.leftMargin = dp(6);
        tile.addView(label, labelParams);
        tile.setFocusable(true);
        tile.setContentDescription(name + String.format(Locale.ROOT, "，预设颜色 #%06X", rgb & 0xffffff));
        tile.setOnClickListener(v -> { color.setRgb(rgb); update(true); });
        return tile;
    }

    private void editComponent(int index) {
        String name = new String[]{"色相 H", "饱和度 S", "亮度 B", "透明度"}[index];
        double maximum = index == 0 ? 360 : 100;
        EditText field = new EditText(context);
        styleInput(field, 22);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        field.setText(String.format(Locale.ROOT, "%.2f", value(index)));
        field.selectAll();
        field.setContentDescription(name + "数值");
        LinearLayout shell = column();
        shell.setBackground(rounded(BG, 24));
        LinearLayout title = column();
        title.setPadding(dp(22), dp(22), dp(22), dp(8));
        TextView heading = text(name, 22, INK);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.addView(heading, match());
        addSpace(title, text("范围 0.00 ～ " + (index == 0 ? "360.00°" : "100.00%"), 12, MUTED), -2, 6);
        shell.addView(title, match());
        LinearLayout body = column();
        body.setPadding(dp(22), dp(8), dp(22), dp(12));
        body.addView(field, match());
        addSpace(body, text("确定后回填调色盘，最终点“应用”保存。", 12, MUTED), -2, 10);
        BoundedScroll scroll = new BoundedScroll();
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body);
        shell.addView(scroll, match());
        AlertDialog dialog = new AlertDialog.Builder(context).setView(shell)
                .setNegativeButton("取消", null).setPositiveButton("确定", null).create();
        dialog.setView(shell,0,0,0,0);
        dialog.setOnShowListener(ignored -> {
            styleDialog(dialog);
            scroll.window = dialog.getWindow();
            field.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    BigDecimal raw=new BigDecimal(field.getText().toString().trim());
                    if(raw.compareTo(BigDecimal.ZERO)<0||raw.compareTo(BigDecimal.valueOf(maximum))>0)
                        throw new IllegalArgumentException();
                    double preliminary=raw.doubleValue();
                    double number=preliminary==0d?0d:raw.setScale(2,RoundingMode.HALF_UP).doubleValue();
                    if (Double.isNaN(number) || Double.isInfinite(number) || number < 0 || number > maximum)
                        throw new IllegalArgumentException();
                    if (index == 3) color.setOpacity(number);
                    else color.setHsb(index == 0 ? number : color.hue(), index == 1 ? number : color.saturation(), index == 2 ? number : color.brightness());
                    update(true);
                    hideKeyboard(field);
                    dialog.dismiss();
                } catch (IllegalArgumentException | ArithmeticException error) { field.setError("请输入范围内的有效数值"); }
            });
        });
        dialog.setOnDismissListener(ignored -> hideKeyboard(field));
        dialog.show();
    }

    private double value(int index) {
        return index == 0 ? color.hue() : index == 1 ? color.saturation() : index == 2 ? color.brightness() : color.opacity();
    }

    private void update(boolean writeHex) {
        updating = true;
        for (int i = 0; i < 4; i++) {
            values[i].setText(String.format(Locale.ROOT, "%.2f%s", value(i), i == 0 ? "°" : "%"));
            values[i].setContentDescription(new String[]{"色相 H", "饱和度 S", "亮度 B", "透明度"}[i]
                    + "，点击精确输入，" + values[i].getText());
        }
        systemAlpha.setChecked(!color.customAlpha());
        summary.setText(color.hex());
        alphaHint.setText(color.customAlpha() ? "0% 完全透明 · 100% 不透明" : "当前沿用系统透明度；调整滑条可改为自定义。");
        if (writeHex) { hex.setText(color.hex()); hex.setError(null); }
        preview.invalidate();
        for (Palette palette : palettes) palette.invalidate();
        updating = false;
    }

    private void hideKeyboard(View view) {
        InputMethodManager ime = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (ime != null) ime.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(rounded(BG, 24));
            int width = Math.min(dp(480), Math.max(dp(200), context.getResources().getDisplayMetrics().widthPixels - dp(32)));
            dialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        TextView apply = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        TextView cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        apply.setTextColor(ON_ACCENT);
        apply.setBackground(interactive(ACCENT, 16));
        cancel.setTextColor(INK);
        cancel.setBackground(interactive(SOFT, 16));
        for (TextView button : new TextView[]{apply, cancel}) {
            button.setAllCaps(false);
            button.setTextSize(15);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setMinHeight(dp(48));
            button.setMinWidth(dp(96));
            button.setPadding(dp(18), dp(8), dp(18), dp(8));
        }
    }

    private void styleInput(EditText field, int size) {
        field.setSingleLine(true);
        field.setTextSize(size);
        field.setTextColor(INK);
        field.setHintTextColor(MUTED);
        field.setHighlightColor((ACCENT & 0x00ffffff) | 0x33000000);
        field.setMinHeight(dp(56));
        field.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable background = rounded(SOFT, 16);
        background.setStroke(dp(1), BORDER);
        field.setBackground(background);
    }

    /** Keep the title and native action row outside the scrolling/IME resize area. */
    private final class BoundedScroll extends ScrollView {
        private final Rect visible = new Rect();
        Window window;
        BoundedScroll() { super(context); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int available = context.getResources().getDisplayMetrics().heightPixels;
            if (window != null) {
                window.getDecorView().getWindowVisibleDisplayFrame(visible);
                if (visible.height() > 0) available = Math.min(available, visible.height());
            }
            int maximum = Math.min(dp(620), Math.max(dp(72), available - dp(200)));
            if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
                maximum = Math.min(maximum, MeasureSpec.getSize(heightSpec));
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maximum, MeasureSpec.AT_MOST));
        }
    }

    private final class Palette extends View {
        private final int kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final Path clip = new Path();
        Palette(int kind) { super(context); this.kind = kind; setClickable(true); setFocusable(true); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // A 14 dp inset contains the whole 10 dp indicator, including its highlight.
            float inset = dp(14), w = getWidth(), h = getHeight();
            float halfTrack = Math.min(dp(10), h / 2 - dp(3));
            bounds.set(inset, h / 2 - halfTrack, w - inset, h / 2 + halfTrack);
            if (bounds.width() <= 0 || bounds.height() <= 0) return;
            paint.setStyle(Paint.Style.FILL);
            int saved = canvas.save();
            float radius = bounds.height() / 2;
            clip.reset();
            clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            int rgb = color.argb() | 0xff000000;
            if (kind == 0) {
                paint.setShader(new LinearGradient(bounds.left, 0, bounds.right, 0,
                        new int[]{0xffff0000, 0xffffff00, 0xff00ff00, 0xff00ffff, 0xff0000ff, 0xffff00ff, 0xffff0000}, null, Shader.TileMode.CLAMP));
            } else if (kind == 1) {
                int grey = Color.HSVToColor(new float[]{(float) color.hue(), 0, (float) color.brightness() / 100});
                int saturated = Color.HSVToColor(new float[]{(float) color.hue(), 1, (float) color.brightness() / 100});
                paint.setShader(new LinearGradient(bounds.left, 0, bounds.right, 0, grey, saturated, Shader.TileMode.CLAMP));
            } else if (kind == 2) {
                int bright = Color.HSVToColor(new float[]{(float) color.hue(), (float) color.saturation() / 100, 1});
                paint.setShader(new LinearGradient(bounds.left, 0, bounds.right, 0, Color.BLACK, bright, Shader.TileMode.CLAMP));
            } else {
                checkerboard(canvas, bounds, paint);
                paint.setShader(new LinearGradient(bounds.left, 0, bounds.right, 0, rgb & 0xffffff, rgb, Shader.TileMode.CLAMP));
            }
            canvas.drawRect(bounds, paint);
            paint.setShader(null);
            canvas.restoreToCount(saved);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(BORDER);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            float fraction = (float) (kind == 0 ? color.hue() / 360 : kind == 1 ? color.saturation() / 100
                    : kind == 2 ? color.brightness() / 100 : color.customAlpha() ? color.opacity() / 100 : 1);
            float x = bounds.left + bounds.width() * fraction, y = bounds.centerY();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(rgb);
            canvas.drawCircle(x, y, dp(9), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(0x66000000);
            canvas.drawCircle(x, y, dp(10), paint);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, dp(9), paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float inset = dp(14);
                double x = Math.max(0, Math.min(1, (event.getX() - inset) / Math.max(1, getWidth() - 2 * inset)));
                if (kind == 0) color.setHsb(x * 360, color.saturation(), color.brightness());
                else if (kind == 1) color.setHsb(color.hue(), x * 100, color.brightness());
                else if (kind == 2) color.setHsb(color.hue(), color.saturation(), x * 100);
                else color.setOpacity(x * 100);
                update(true);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                return true;
            }
            return super.onTouchEvent(event);
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }

    private final class Preview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final Path clip = new Path();
        Preview() { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            bounds.set(1, 1, getWidth() - 1, getHeight() - 1);
            int saved = canvas.save();
            clip.reset();
            clip.addRoundRect(bounds, dp(14), dp(14), Path.Direction.CW);
            canvas.clipPath(clip);
            checkerboard(canvas, bounds, paint);
            paint.setColor(color.argb());
            canvas.drawRect(bounds, paint);
            canvas.restoreToCount(saved);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(BORDER);
            canvas.drawRoundRect(bounds, dp(14), dp(14), paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void checkerboard(Canvas canvas, RectF area, Paint paint) {
        paint.setShader(null);
        float side = dp(6);
        int saved = canvas.save();
        canvas.clipRect(area);
        for (int row = 0; row * side < area.height(); row++) for (int col = 0; col * side < area.width(); col++) {
            paint.setColor((row + col) % 2 == 0 ? CHECKER_LIGHT : CHECKER_DARK);
            float x = area.left + col * side, y = area.top + row * side;
            canvas.drawRect(x, y, x + side, y + side, paint);
        }
        canvas.restoreToCount(saved);
    }

    private void addSpace(LinearLayout parent, View child, int height, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height < 0 ? height : dp(height));
        params.topMargin = dp(margin);
        parent.addView(child, params);
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(context); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(context); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout card() {
        LinearLayout view = column();
        view.setBackground(rounded(CARD, 22));
        view.setPadding(dp(16), dp(18), dp(16), dp(18));
        return view;
    }
    private TextView sectionTitle(String value) {
        TextView view = text(value, 16, INK);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(-1, -2); }
    private TextView text(String value, int size, int tint) {
        TextView view = new TextView(context); view.setText(value); view.setTextSize(size); view.setTextColor(tint); return view;
    }
    private GradientDrawable rounded(int tint, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(tint); drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private RippleDrawable interactive(int tint, int radius) {
        int ripple = ((tint == ACCENT ? ON_ACCENT : ACCENT) & 0x00ffffff) | 0x22000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple), rounded(tint, radius), rounded(Color.WHITE, radius));
    }
    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
