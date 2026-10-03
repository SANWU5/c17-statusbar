package android.graphics;
public class Matrix {
    private final float[] values={1,0,0,0,1,0,0,0,1};
    public void setScale(float x,float y){values[0]=x;values[4]=y;}
    public boolean postTranslate(float x,float y){values[2]+=x;values[5]+=y;return true;}
    public void getValues(float[] output){System.arraycopy(values,0,output,0,9);}
    public void mapPoints(float[] points){for(int i=0;i+1<points.length;i+=2){float x=points[i],y=points[i+1];float divisor=values[6]*x+values[7]*y+values[8];points[i]=(values[0]*x+values[1]*y+values[2])/divisor;points[i+1]=(values[3]*x+values[4]*y+values[5])/divisor;}}
    public void set(Matrix matrix){System.arraycopy(matrix.values,0,values,0,9);}
    public void setTranslate(float x,float y){for(int i=0;i<9;i++)values[i]=0;values[0]=values[4]=values[8]=1;values[2]=x;values[5]=y;}
    public boolean preTranslate(float x,float y){values[2]+=values[0]*x+values[1]*y;values[5]+=values[3]*x+values[4]*y;return true;}
    public boolean isIdentity(){return values[0]==1&&values[1]==0&&values[2]==0&&values[3]==0&&values[4]==1&&values[5]==0&&values[6]==0&&values[7]==0&&values[8]==1;}
    public void reset(){setTranslate(0f,0f);}
    public boolean invert(Matrix target){
        float a=values[0],b=values[1],c=values[2],d=values[3],e=values[4],f=values[5],g=values[6],h=values[7],i=values[8];
        float determinant=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if(determinant==0f)return false;
        float[] inverse={(e*i-f*h)/determinant,(c*h-b*i)/determinant,(b*f-c*e)/determinant,
                (f*g-d*i)/determinant,(a*i-c*g)/determinant,(c*d-a*f)/determinant,
                (d*h-e*g)/determinant,(b*g-a*h)/determinant,(a*e-b*d)/determinant};
        System.arraycopy(inverse,0,target.values,0,9);return true;
    }
    public boolean preConcat(Matrix other){
        float[] result=new float[9];
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)for(int k=0;k<3;k++)result[row*3+col]+=values[row*3+k]*other.values[k*3+col];
        System.arraycopy(result,0,values,0,9);return true;
    }
    public boolean mapRect(RectF rect){
        float l=rect.left,t=rect.top,r=rect.right,b=rect.bottom;
        float x1=values[0]*l+values[1]*t+values[2],y1=values[3]*l+values[4]*t+values[5];
        float x2=values[0]*r+values[1]*t+values[2],y2=values[3]*r+values[4]*t+values[5];
        float x3=values[0]*r+values[1]*b+values[2],y3=values[3]*r+values[4]*b+values[5];
        float x4=values[0]*l+values[1]*b+values[2],y4=values[3]*l+values[4]*b+values[5];
        rect.set(Math.min(Math.min(x1,x2),Math.min(x3,x4)),Math.min(Math.min(y1,y2),Math.min(y3,y4)),Math.max(Math.max(x1,x2),Math.max(x3,x4)),Math.max(Math.max(y1,y2),Math.max(y3,y4)));return true;
    }
}
