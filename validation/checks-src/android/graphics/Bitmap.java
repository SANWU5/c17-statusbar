package android.graphics;
/** CPU pixels and observable work counts, rather than an always-successful media mock. */
public final class Bitmap {
    public enum Config { ARGB_8888, HARDWARE, ALPHA_8 }
    public static int creations, scales, reads, copies;
    private static int nextGeneration;
    private final int width,height;
    public final int[] pixels;
    private final Config config;
    private int generation=++nextGeneration;
    private boolean recycled;
    private boolean hasAlpha=true;
    public int recycleCalls;
    private Bitmap(int width,int height,Config config){this.width=width;this.height=height;this.config=config;pixels=new int[width*height];creations++;}
    public static Bitmap createBitmap(int width,int height,Config config){return new Bitmap(width,height,config);}
    public static Bitmap createBitmap(int[] colors,int width,int height,Config config){Bitmap result=new Bitmap(width,height,config);System.arraycopy(colors,0,result.pixels,0,width*height);return result;}
    public static Bitmap createBitmap(Bitmap source,int x,int y,int width,int height){if(x<0||y<0||width<1||height<1||x+width>source.width||y+height>source.height)throw new IllegalArgumentException("crop outside bitmap");if(x==0&&y==0&&width==source.width&&height==source.height)return source;Bitmap result=createBitmap(width,height,source.config);for(int row=0;row<height;row++)System.arraycopy(source.pixels,(y+row)*source.width+x,result.pixels,row*width,width);return result;}
    public static Bitmap createScaledBitmap(Bitmap source,int width,int height,boolean filter){scales++;if(width==source.width&&height==source.height)return source;Bitmap result=createBitmap(width,height,source.config);for(int y=0;y<height;y++)for(int x=0;x<width;x++)result.pixels[y*width+x]=source.pixels[Math.min(source.height-1,y*source.height/height)*source.width+Math.min(source.width-1,x*source.width/width)];return result;}
    public Bitmap copy(Config config,boolean mutable){copies++;Bitmap result=createBitmap(width,height,config);System.arraycopy(pixels,0,result.pixels,0,pixels.length);return result;}
    public int getWidth(){return width;} public int getHeight(){return height;} public Config getConfig(){return config;}
    public int getAllocationByteCount(){return pixels.length*4;}
    public int getGenerationId(){return generation;} public boolean isRecycled(){return recycled;}
    public boolean sameAs(Bitmap other){if(recycled||other!=null&&other.recycled)throw new IllegalStateException("recycled bitmap");return other!=null&&width==other.width&&height==other.height&&config==other.config&&java.util.Arrays.equals(pixels,other.pixels);}
    public void recycle(){recycleCalls++;recycled=true;}
    public boolean hasAlpha(){return hasAlpha;}
    public void setHasAlpha(boolean value){hasAlpha=value;}
    public Bitmap extractAlpha(){Bitmap result=createBitmap(width,height,Config.ALPHA_8);for(int i=0;i<pixels.length;i++)result.pixels[i]=pixels[i]&0xff000000;return result;}
    public void eraseColor(int color){java.util.Arrays.fill(pixels,color);generation=++nextGeneration;}
    public void getPixels(int[] output,int offset,int stride,int x,int y,int width,int height){reads++;for(int row=0;row<height;row++)System.arraycopy(pixels,(y+row)*this.width+x,output,offset+row*stride,width);}
    public void setPixels(int[] input,int offset,int stride,int x,int y,int width,int height){for(int row=0;row<height;row++)System.arraycopy(input,offset+row*stride,pixels,(y+row)*this.width+x,width);generation=++nextGeneration;}
}
