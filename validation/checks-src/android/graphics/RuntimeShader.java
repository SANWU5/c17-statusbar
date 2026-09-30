package android.graphics;
import java.util.LinkedHashMap;
import java.util.Map;
public class RuntimeShader extends Shader {
    public final String source;
    public final Map<String,float[]> floats=new LinkedHashMap<>();
    public final Map<String,Integer> ints=new LinkedHashMap<>();
    public final Map<String,Shader> inputs=new LinkedHashMap<>();
    public RuntimeShader(String source){this.source=source;}
    public void setFloatUniform(String name,float... values){floats.put(name,values.clone());}
    public void setFloatUniform(String name,float x,float y){floats.put(name,new float[]{x,y});}
    public void setIntUniform(String name,int value){ints.put(name,value);}
    public void setInputShader(String name,Shader shader){inputs.put(name,shader);}
}
