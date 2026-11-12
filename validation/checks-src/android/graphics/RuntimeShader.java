package android.graphics;
import java.util.LinkedHashMap;
import java.util.Map;
public class RuntimeShader extends Shader {
    public static int creations;
    public final String source;
    public final Map<String,float[]> floats=new LinkedHashMap<>();
    public final Map<String,Integer> ints=new LinkedHashMap<>();
    public final Map<String,Shader> inputs=new LinkedHashMap<>();
    public final Map<String,Integer> floatWritesForCheck=new LinkedHashMap<>();
    private void floatWrite(String name){floatWritesForCheck.put(name,floatWritesForCheck.getOrDefault(name,0)+1);}
    public int uploadCount;
    public RuntimeShader(String source){this.source=source;creations++;}
    public void setFloatUniform(String name,float... values){uploadCount++;floatWrite(name);floats.put(name,values.clone());}
    public void setFloatUniform(String name,float value){uploadCount++;floatWrite(name);floats.put(name,new float[]{value});}
    public void setFloatUniform(String name,float x,float y){uploadCount++;floatWrite(name);floats.put(name,new float[]{x,y});}
    public void setFloatUniform(String name,float x,float y,float z){uploadCount++;floatWrite(name);floats.put(name,new float[]{x,y,z});}
    public void setFloatUniform(String name,float x,float y,float z,float w){uploadCount++;floatWrite(name);floats.put(name,new float[]{x,y,z,w});}
    public void setIntUniform(String name,int value){uploadCount++;ints.put(name,value);}
    public void setInputShader(String name,Shader shader){uploadCount++;inputs.put(name,shader);}
    public void setInputBuffer(String name,BitmapShader shader){uploadCount++;inputs.put(name,shader);}
    public void setColorUniform(String name,int value){uploadCount++;ints.put(name,value);}
}
