package android.hardware;
import java.util.ArrayList;
import java.util.List;
public class HardwareBuffer implements AutoCloseable {
    public static final List<HardwareBuffer> createdForCheck=new ArrayList<>();
    public int closeCalls;
    public HardwareBuffer(){createdForCheck.add(this);}
    @Override public void close(){closeCalls++;}
}
