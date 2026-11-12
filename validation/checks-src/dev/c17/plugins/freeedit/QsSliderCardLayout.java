package dev.c17.plugins.freeedit;
/** Exact plugin ownership fields audited on the phone; no host-to-plugin dependency. */
public final class QsSliderCardLayout extends android.widget.FrameLayout {
    public int columns=2,rows=2;public boolean released;
    public final Buttons buttons=new Buttons();public final Slider slider=new Slider();
    public QsSliderCardLayout(){super(new android.content.Context());}
    public static final class Buttons{public android.view.View top,bottom;}
    public static final class Slider{public android.view.View view;}
}
