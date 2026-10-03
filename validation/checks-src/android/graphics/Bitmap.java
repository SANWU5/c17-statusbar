package android.graphics;
/** CPU pixels and observable work counts, rather than an always-successful media mock. */
public final class Bitmap {
    public enum Config { ARGB_8888, HARDWARE }
    public static int creations, scales, reads;
    private static int nextGeneration;
    private final int width,height;
    public final int[] pixels;
    private final Config config;
    private int generation=++nextGeneration;
    private boolean recycled;
    private Bitmap(int width,int height,Config config){this.width=width;this.height=height;this.config=config;pixels=new int[width*height];creations++;}
    public static Bitmap createBitmap(int width,int height,Config config){return new Bitmap(width,height,config);}
    public static Bitmap createScaledBitmap(Bitmap source,int width,int height,boolean filter){scales++;if(width==source.width&&height==source.height)return source;Bitmap result=createBitmap(width,height,source.config);for(int y=0;y<height;y++)for(int x=0;x<width;x++)result.pixels[y*width+x]=source.pixels[Math.min(source.height-1,y*source.height/height)*source.width+Math.min(source.width-1,x*source.width/width)];return result;}
    public Bitmap copy(Config config,boolean mutable){Bitmap result=createBitmap(width,height,config);System.arraycopy(pixels,0,result.pixels,0,pixels.length);return result;}
    public int getWidth(){return width;} public int getHeight(){return height;} public Config getConfig(){return config;}
    public int getAllocationByteCount(){return pixels.length*4;}
    public int getGenerationId(){return generation;} public boolean isRecycled(){return recycled;}
    public void recycle(){recycled=true;}
    public void eraseColor(int color){java.util.Arrays.fill(pixels,color);generation=++nextGeneration;}
    public void getPixels(int[] output,int offset,int stride,int x,int y,int width,int height){reads++;for(int row=0;row<height;row++)System.arraycopy(pixels,(y+row)*this.width+x,output,offset+row*stride,width);}
    public void setPixels(int[] input,int offset,int stride,int x,int y,int width,int height){for(int row=0;row<height;row++)System.arraycopy(input,offset+row*stride,pixels,(y+row)*this.width+x,width);generation=++nextGeneration;}
}
