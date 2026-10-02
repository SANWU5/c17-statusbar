package android.graphics.fonts;

/** Models the public parser; drawing fixtures keep glyph axes separate from style metadata. */
public final class FontVariationAxis {
    private final String tag;
    private final float value;
    public FontVariationAxis(String tag,float value){this.tag=tag;this.value=value;}
    public String getTag(){return tag;}
    public float getStyleValue(){return value;}
    public static FontVariationAxis[] fromFontVariationSettings(String settings){
        if(settings==null||settings.isEmpty())return null;
        String[] parts=settings.split(",");FontVariationAxis[] result=new FontVariationAxis[parts.length];
        java.util.regex.Pattern pattern=java.util.regex.Pattern.compile("['\"]([A-Za-z0-9]{4})['\"]\\s+(.+)");
        for(int i=0;i<parts.length;i++){
            java.util.regex.Matcher matcher=pattern.matcher(parts[i].trim());
            if(!matcher.matches())throw new IllegalArgumentException(settings);
            result[i]=new FontVariationAxis(matcher.group(1),Float.parseFloat(matcher.group(2)));
        }
        return result;
    }
}
