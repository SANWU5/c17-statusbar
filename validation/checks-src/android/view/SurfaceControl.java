package android.view;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/** Records compositor ownership and transactions; no renderer or native wallpaper writes. */
public class SurfaceControl {
    public static final List<SurfaceControl> createdForCheck=new ArrayList<>();
    public static final List<Operation> operationsForCheck=new ArrayList<>();
    public static final List<Transaction> transactionsForCheck=new ArrayList<>();
    public static boolean failNextBuildForCheck;
    public static int buildCallsForCheck,failBuildAtForCheck;
    public static int borrowedMutationAttemptsForCheck;
    private static int nextTransaction;

    public final String name;
    public final boolean owned;
    public final SurfaceControl parent;
    public final int kind; // native=-1, owned container=0, effect=1, color=2, buffer=3
    public boolean valid=true,hidden=true;
    public int releaseCalls,removeCalls,blurRadius,layer,relativeLayer;
    public SurfaceControl relativeTo;
    public Rect crop;
    public float x,y,alpha=1f;
    public float[] color;
    public int bufferWidth,bufferHeight;
    public int bufferFormat=-1;
    public boolean opaque=true;

    public SurfaceControl(String name,SurfaceControl parent) { this(name,parent,false,-1,true); }
    private SurfaceControl(String name,SurfaceControl parent,boolean owned,int kind,boolean hidden) {
        this.name=name; this.parent=parent; this.owned=owned; this.kind=kind; this.hidden=hidden;
        createdForCheck.add(this);
    }
    public boolean isValid() { return valid; }
    public void release() {
        requireOwned(this); releaseCalls++; valid=false;
        operationsForCheck.add(new Operation(-1,"release",this,null,null));
    }
    public static void resetForCheck() {
        createdForCheck.clear();operationsForCheck.clear();transactionsForCheck.clear();
        failNextBuildForCheck=false;buildCallsForCheck=failBuildAtForCheck=borrowedMutationAttemptsForCheck=0;nextTransaction=0;
    }
    private static void requireOwned(SurfaceControl target) {
        if(target==null||!target.owned) {
            borrowedMutationAttemptsForCheck++;
            throw new AssertionError("Attempted mutation of borrowed native SurfaceControl "+(target==null?"null":target.name));
        }
    }

    public static final class Operation {
        public final int transaction;
        public final String name;
        public final SurfaceControl target,reference;
        public final Object value;
        Operation(int transaction,String name,SurfaceControl target,SurfaceControl reference,Object value) {
            this.transaction=transaction;this.name=name;this.target=target;this.reference=reference;this.value=value;
        }
    }
    public static final class Builder {
        private String name;
        private SurfaceControl parent;
        private int kind=-1;
        private boolean hidden;
        private int width,height;
        private int format=-1;
        private boolean opaque=true;
        public Builder() { }
        public Builder setName(String value) {name=value;return this;}
        public Builder setParent(SurfaceControl value) {parent=value;return this;}
        public Builder setEffectLayer() {kind=1;return this;}
        public Builder setColorLayer() {kind=2;return this;}
        public Builder setContainerLayer() {kind=0;return this;}
        public Builder setBufferSize(int width,int height) {this.width=width;this.height=height;kind=3;return this;}
        public Builder setFormat(int value){format=value;return this;}
        public Builder setOpaque(boolean value){opaque=value;return this;}
        public Builder setHidden(boolean value) {hidden=value;return this;}
        public SurfaceControl build() {
            buildCallsForCheck++;
            if(failNextBuildForCheck||failBuildAtForCheck==buildCallsForCheck){failNextBuildForCheck=false;throw new IllegalStateException("unsupported effect layer");}
            if(parent==null||!parent.valid||kind<0) throw new IllegalStateException("invalid fixture parent or layer kind");
            SurfaceControl result=new SurfaceControl(name,parent,true,kind,hidden);
            result.bufferWidth=width;result.bufferHeight=height;result.bufferFormat=format;result.opaque=opaque;return result;
        }
    }
    public static final class Transaction implements AutoCloseable {
        public final int id=++nextTransaction;
        public final List<Operation> pending=new ArrayList<>();
        public int applyCalls,syncApplyCalls,closeCalls;
        public Transaction() {transactionsForCheck.add(this);}
        private Transaction add(String name,SurfaceControl target,SurfaceControl reference,Object value) {
            requireOwned(target);
            if(!target.valid) throw new IllegalStateException("invalid owned fixture surface");
            Operation op=new Operation(id,name,target,reference,value);pending.add(op);operationsForCheck.add(op);return this;
        }
        public Transaction setRelativeLayer(SurfaceControl target,SurfaceControl reference,int value) {return add("relative",target,reference,value);}
        public Transaction setLayer(SurfaceControl target,int value) {return add("layer",target,null,value);}
        public Transaction setCrop(SurfaceControl target,Rect value) {return add("crop",target,null,new Rect(value));}
        public Transaction setPosition(SurfaceControl target,float x,float y) {return add("position",target,null,new float[]{x,y});}
        public Transaction setBackgroundBlurRadius(SurfaceControl target,int value) {return add("blur",target,null,value);}
        public Transaction setColor(SurfaceControl target,float[] value) {return add("color",target,null,value.clone());}
        public Transaction setAlpha(SurfaceControl target,float value) {return add("alpha",target,null,value);}
        public Transaction show(SurfaceControl target) {return add("show",target,null,null);}
        public Transaction hide(SurfaceControl target) {return add("hide",target,null,null);}
        public Transaction remove(SurfaceControl target) {return add("remove",target,null,null);}
        public void apply() {apply(false);}
        public void apply(boolean sync) {
            applyCalls++;if(sync)syncApplyCalls++;
            for(Operation op:pending) {
                SurfaceControl target=op.target;
                switch(op.name) {
                    case "relative": target.relativeTo=op.reference;target.relativeLayer=(Integer)op.value;break;
                    case "layer": target.layer=(Integer)op.value;break;
                    case "crop": target.crop=new Rect((Rect)op.value);break;
                    case "position": target.x=((float[])op.value)[0];target.y=((float[])op.value)[1];break;
                    case "blur": target.blurRadius=(Integer)op.value;break;
                    case "color": target.color=((float[])op.value).clone();break;
                    case "alpha": target.alpha=(Float)op.value;break;
                    case "show": target.hidden=false;break;
                    case "hide": target.hidden=true;break;
                    case "remove": target.removeCalls++;target.valid=false;target.hidden=true;break;
                    default: throw new AssertionError("Unknown transaction operation "+op.name);
                }
            }
            pending.clear();operationsForCheck.add(new Operation(id,sync?"applySync":"apply",null,null,null));
        }
        @Override public void close() {closeCalls++;operationsForCheck.add(new Operation(id,"close",null,null,null));}
    }
}
