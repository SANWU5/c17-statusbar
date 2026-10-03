package android.content.res;
public class Configuration {
    public Configuration(){}
    public Configuration(Configuration other){orientation=other.orientation;uiMode=other.uiMode;fontScale=other.fontScale;screenWidthDp=other.screenWidthDp;screenHeightDp=other.screenHeightDp;smallestScreenWidthDp=other.smallestScreenWidthDp;}
    public boolean equals(Configuration other){return other!=null&&orientation==other.orientation&&uiMode==other.uiMode;}
    public static final int ORIENTATION_UNDEFINED=0,ORIENTATION_PORTRAIT=1,ORIENTATION_LANDSCAPE=2;
    public int orientation;
    public float fontScale=1f;
    public int screenWidthDp=360,screenHeightDp=792,smallestScreenWidthDp=360;
    public static final int UI_MODE_NIGHT_MASK=0x30,UI_MODE_NIGHT_NO=0x10,UI_MODE_NIGHT_YES=0x20;
    public int uiMode;
}
