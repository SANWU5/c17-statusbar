package android.view;
public final class KeyEvent {
    public static final int META_CTRL_ON=4096,META_SHIFT_ON=1;
    public static final int KEYCODE_V=50,KEYCODE_C=31,KEYCODE_X=52,KEYCODE_INSERT=124,KEYCODE_DEL=67,KEYCODE_A=29;
    private final int modifiers;
    public KeyEvent(int modifiers){this.modifiers=modifiers;}
    public int getMetaState(){return modifiers;}
}
