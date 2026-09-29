package dev.puitheme;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
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
    private static final int INK = 0xff18202d, MUTED = 0xff718096, ACCENT = 0xff2869e8;
    private final Context context;
    private final HsbColor color;
    private final TextView[] values = new TextView[4];
    private final Palette[] palettes = new Palette[3];
    private EditText hex;
    private TextView summary, alphaHint;
    private Switch systemAlpha;
    private Preview preview;
    private boolean updating;

    private ColorPickerDialog(Context context, int color, boolean customAlpha) {
        this.context = context;
        this.color = new HsbColor(color, customAlpha);
    }

    public static void show(Context context, String title, int color, boolean customAlpha, Result result) {
        new ColorPickerDialog(context, color, customAlpha).open(title, result);
    }

    private void open(String title, Result result) {
        LinearLayout content = column();
        content.setPadding(dp(18), dp(8), dp(18), dp(6));
        content.setFocusableInTouchMode(true);
        LinearLayout heading = row();
        preview = new Preview();
        preview.setContentDescription("当前颜色预览");
        heading.addView(preview, new LinearLayout.LayoutParams(dp(54), dp(38)));
        LinearLayout captions = column();
        captions.setPadding(dp(12), 0, 0, 0);
        captions.addView(text("HSB 调色盘", 16, INK));
        summary = text("", 11, MUTED);
        captions.addView(summary);
        heading.addView(captions, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(heading, match());
        palettes[0] = new Palette(0);
        palettes[0].setContentDescription("饱和度与亮度调色盘，左右调节饱和度，上下调节亮度");
        addSpace(content, palettes[0], 156, 12);
        palettes[1] = new Palette(1);
        palettes[1].setContentDescription("色相 H 滑条，0 至 360 度");
        addSpace(content, palettes[1], 30, 6);

        LinearLayout components = row();
        for (int i = 0; i < 3; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1);
            if (i > 0) params.leftMargin = dp(6);
            components.addView(component(i), params);
        }
        addSpace(content, components, 52, 4);
        systemAlpha = new Switch(context);
        systemAlpha.setText("透明度跟随系统");
        systemAlpha.setTextSize(13);
        systemAlpha.setTextColor(INK);
        systemAlpha.setContentDescription("透明度跟随系统");
        content.addView(systemAlpha, match());
        LinearLayout alphaRow = row();
        alphaRow.addView(text("自定义透明度", 12, INK), new LinearLayout.LayoutParams(0, -2, 1));
        values[3] = text("", 13, ACCENT);
        values[3].setPadding(dp(10), dp(6), dp(10), dp(6));
        values[3].setBackground(rounded(0xffedf3ff, 8));
        values[3].setOnClickListener(v -> editComponent(3));
        alphaRow.addView(values[3]);
        content.addView(alphaRow, match());
        palettes[2] = new Palette(2);
        palettes[2].setContentDescription("透明度滑条，0 至 100 百分比");
        addSpace(content, palettes[2], 30, 2);
        alphaHint = text("", 10, MUTED);
        addSpace(content, alphaHint, -2, 2);

        hex = new EditText(context);
        hex.setSingleLine(true);
        hex.setTextSize(16);
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
        hex.setHint("#RRGGBB / #AARRGGBB");
        hex.setSelectAllOnFocus(true);
        hex.setContentDescription("十六进制颜色，6 位 RGB 或 8 位 ARGB");
        content.addView(hex, match());
        LinearLayout presets = row();
        for (int preset : new int[]{0xff000000, 0xffffffff, 0xff64748b, 0xff2869e8, 0xff22c55e, 0xfff59e0b}) {
            View swatch = new View(context);
            swatch.setBackground(rounded(preset, 7));
            swatch.setContentDescription(String.format(Locale.ROOT, "预设颜色 #%06X", preset & 0xffffff));
            swatch.setOnClickListener(v -> { color.setRgb(preset); update(true); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(30), 1);
            params.setMargins(dp(3), dp(4), dp(3), dp(4));
            presets.addView(swatch, params);
        }
        content.addView(presets, match());
        content.addView(text("点击 H / S / B 数值可输入，精确到 0.01。", 10, MUTED), match());
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(title).setView(scroll)
                .setNegativeButton("取消", null).setPositiveButton("应用", null).create();
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
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            content.requestFocus();
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
        box.setGravity(Gravity.CENTER);
        box.setBackground(rounded(0xffedf3ff, 8));
        box.setPadding(dp(3), dp(3), dp(3), dp(3));
        box.addView(text(new String[]{"色相 H", "饱和度 S", "亮度 B"}[index], 10, MUTED));
        values[index] = text("", 13, ACCENT);
        box.addView(values[index]);
        box.setContentDescription(new String[]{"色相 H", "饱和度 S", "亮度 B"}[index] + "，点击输入");
        box.setOnClickListener(v -> editComponent(index));
        return box;
    }

    private void editComponent(int index) {
        String name = new String[]{"色相 H", "饱和度 S", "亮度 B", "透明度"}[index];
        double maximum = index == 0 ? 360 : 100;
        EditText field = new EditText(context);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setText(String.format(Locale.ROOT, "%.2f", value(index)));
        field.selectAll();
        field.setContentDescription(name + "数值");
        LinearLayout body = column();
        body.setPadding(dp(20), dp(6), dp(20), dp(6));
        body.addView(text("范围 0.00 ～ " + (index == 0 ? "360.00°" : "100.00%"), 12, MUTED));
        body.addView(field, match());
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(name).setView(body)
                .setNegativeButton("取消", null).setPositiveButton("应用", null).create();
        dialog.setOnShowListener(ignored -> {
            field.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    double number = new BigDecimal(field.getText().toString().trim()).setScale(2, RoundingMode.HALF_UP).doubleValue();
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
        for (int i = 0; i < 4; i++) values[i].setText(String.format(Locale.ROOT, "%.2f%s", value(i), i == 0 ? "°" : "%"));
        values[3].setContentDescription("透明度，点击输入，" + values[3].getText());
        systemAlpha.setChecked(!color.customAlpha());
        summary.setText(color.hex() + (color.customAlpha() ? " · 自定义透明度" : " · 跟随系统透明度"));
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

    private final class Palette extends View {
        private final int kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        Palette(int kind) { super(context); this.kind = kind; setClickable(true); setFocusable(true); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // Keep the complete indicator (7 dp radius + stroke) inside the view at every edge.
            float inset = dp(10), w = getWidth(), h = getHeight();
            bounds.set(inset, inset, w - inset, h - inset);
            if (bounds.width() <= 0 || bounds.height() <= 0) return;
            int saved = canvas.save();
            canvas.clipRect(bounds);
            int rgb = color.argb() | 0xff000000;
            if (kind == 0) {
                int hueColor = Color.HSVToColor(new float[]{(float) color.hue(), 1, 1});
                paint.setShader(new LinearGradient(bounds.left, 0, bounds.right, 0, Color.WHITE, hueColor, Shader.TileMode.CLAMP));
                canvas.drawRect(bounds, paint);
                paint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
            } else if (kind == 1) {
                paint.setShader(new LinearGradient(bounds.left, 0, bounds.right, 0,
                        new int[]{0xffff0000, 0xffffff00, 0xff00ff00, 0xff00ffff, 0xff0000ff, 0xffff00ff, 0xffff0000}, null, Shader.TileMode.CLAMP));
            } else {
                checkerboard(canvas, bounds, paint);
                paint.setShader(new LinearGradient(bounds.left, 0, bounds.right, 0, rgb & 0xffffff, rgb, Shader.TileMode.CLAMP));
            }
            canvas.drawRect(bounds, paint);
            paint.setShader(null);
            canvas.restoreToCount(saved);
            float x = bounds.left + bounds.width() * (float) (kind == 0 ? color.saturation() / 100 : kind == 1 ? color.hue() / 360 : color.customAlpha() ? color.opacity() / 100 : 1);
            float y = kind == 0 ? bounds.top + bounds.height() * (float) (1 - color.brightness() / 100) : bounds.centerY();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(0xaa000000);
            canvas.drawCircle(x, y, dp(7), paint);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, dp(6), paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float inset = dp(10);
                double x = Math.max(0, Math.min(1, (event.getX() - inset) / Math.max(1, getWidth() - 2 * inset)));
                double y = Math.max(0, Math.min(1, (event.getY() - inset) / Math.max(1, getHeight() - 2 * inset)));
                if (kind == 0) color.setHsb(color.hue(), x * 100, (1 - y) * 100);
                else if (kind == 1) color.setHsb(x * 360, color.saturation(), color.brightness());
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
        Preview() { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            bounds.set(1, 1, getWidth() - 1, getHeight() - 1);
            checkerboard(canvas, bounds, paint);
            paint.setColor(color.argb());
            canvas.drawRect(bounds, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(0xffd5dce7);
            canvas.drawRect(bounds, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void checkerboard(Canvas canvas, RectF area, Paint paint) {
        paint.setShader(null);
        float side = dp(6);
        int saved = canvas.save();
        canvas.clipRect(area);
        for (int row = 0; row * side < area.height(); row++) for (int col = 0; col * side < area.width(); col++) {
            paint.setColor((row + col) % 2 == 0 ? Color.WHITE : 0xffd3d8e1);
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
    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(-1, -2); }
    private TextView text(String value, int size, int tint) {
        TextView view = new TextView(context); view.setText(value); view.setTextSize(size); view.setTextColor(tint); return view;
    }
    private GradientDrawable rounded(int tint, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(tint); drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), 0xffe1e6ef); return drawable;
    }
    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
