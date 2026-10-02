package android.graphics;
import java.util.LinkedHashMap;
import java.util.Map;
public class RuntimeShader extends Shader {
    public final String source;
    public final Map<String,float[]> floats=new LinkedHashMap<>();
    public final Map<String,Integer> ints=new LinkedHashMap<>();
    public final Map<String,Shader> inputs=new LinkedHashMap<>();
    public int uploadCount;
    public RuntimeShader(String source){this.source=source;}
    public void setFloatUniform(String name,float... values){uploadCount++;floats.put(name,values.clone());}
    public void setFloatUniform(String name,float value){uploadCount++;floats.put(name,new float[]{value});}
    public void setFloatUniform(String name,float x,float y){uploadCount++;floats.put(name,new float[]{x,y});}
    public void setIntUniform(String name,int value){uploadCount++;ints.put(name,value);}
    public void setInputShader(String name,Shader shader){uploadCount++;inputs.put(name,shader);}
}
