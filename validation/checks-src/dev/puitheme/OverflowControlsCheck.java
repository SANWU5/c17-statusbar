package dev.puitheme;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

/** Shared ancestor restoration, changing native trees, and weak owner cleanup. */
public final class OverflowControlsCheck {
    private static int checks;
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    private static OverflowControls registry() throws Exception {
        Constructor<OverflowControls> constructor=OverflowControls.class.getDeclaredConstructor();
        constructor.setAccessible(true);return constructor.newInstance();
    }
    private static void bounds(Rect expected,Rect actual,String message) {
        check(expected==null?actual==null:actual!=null&&expected.left==actual.left&&expected.top==actual.top
                &&expected.right==actual.right&&expected.bottom==actual.bottom,message);
    }
    public static void main(String[] args) throws Exception {
        check(OverflowControls.shared()==OverflowControls.shared(),"registry is not shared");
        sharedAncestors();changingTrees();depthLimits();weakOwners();
        System.out.println("OverflowControlsCheck passed: "+checks);
    }
    private static void sharedAncestors() throws Exception {
        OverflowControls registry=registry();
        ViewGroup parent=new ViewGroup(null),network=new ViewGroup(null),battery=new ViewGroup(null);
        parent.addView(network);parent.addView(battery);
        Rect original=new Rect(1,2,90,70);parent.setClipBounds(original);
        network.setClipToPadding(false);battery.setClipChildren(false);
        registry.acquire(network,true);registry.acquire(battery,false);
        check(!parent.getClipChildren()&&!parent.getClipToPadding(),"shared parent remains clipped");
        bounds(null,parent.getClipBounds(),"parent clip bounds remain");
        registry.acquire(network,true);registry.acquire(network,true);
        registry.release(network);
        check(!parent.getClipChildren(),"network release restored battery ancestor too early");
        check(network.getClipChildren()&&!network.getClipToPadding(),"network flags did not restore exactly");
        original.right=1000;
        registry.release(battery);
        check(parent.getClipChildren()&&parent.getClipToPadding(),"last release did not restore shared parent");
        bounds(new Rect(1,2,90,70),parent.getClipBounds(),"snapshot aliased caller's mutable rectangle");
        check(!battery.getClipChildren()&&battery.getClipToPadding(),"original mixed battery flags changed");
        registry.release(battery);bounds(new Rect(1,2,90,70),parent.getClipBounds(),"duplicate release changed parent");
    }
    private static void changingTrees() throws Exception {
        OverflowControls registry=registry();
        ViewGroup parent=new ViewGroup(null),host=new ViewGroup(null),compose=new ViewGroup(null);
        View child=new View(null);parent.addView(host);host.addView(compose);compose.addView(child);
        Rect clip=new Rect(2,3,40,50);child.setClipBounds(clip);
        registry.acquire(host,true);
        check(!compose.getClipChildren(),"nested native Compose container clips");
        bounds(null,child.getClipBounds(),"leaf clip bounds remain");
        compose.setClipChildren(true);child.setClipBounds(clip);
        registry.acquire(host,true);
        check(!compose.getClipChildren(),"native flag reset was not refreshed");
        bounds(null,child.getClipBounds(),"native bound reset was not refreshed");
        registry.acquire(host,false);
        check(compose.getClipChildren(),"removed child-tree lease did not restore descendant");
        bounds(clip,child.getClipBounds(),"removed leaf did not restore bounds");
        check(!host.getClipChildren()&&!parent.getClipChildren(),"ancestor lease vanished when tree mode changed");
        ViewGroup nextParent=new ViewGroup(null);host.parent=nextParent;
        registry.acquire(host,false);
        check(parent.getClipChildren(),"old ancestor retained a lease after reparenting");
        check(!nextParent.getClipChildren(),"new ancestor is clipped");
        registry.release(host);
        check(host.getClipChildren()&&nextParent.getClipChildren(),"reparented lease did not restore");
    }
    private static void depthLimits() throws Exception {
        OverflowControls registry=registry();ViewGroup owner=new ViewGroup(null);
        ViewGroup[] descendants=new ViewGroup[10];ViewGroup current=owner;
        for(int index=0;index<descendants.length;index++){descendants[index]=new ViewGroup(null);current.addView(descendants[index]);current=descendants[index];}
        ViewGroup[] ancestors=new ViewGroup[14];current=owner;
        for(int index=0;index<ancestors.length;index++){ancestors[index]=new ViewGroup(null);current.parent=ancestors[index];current=ancestors[index];}
        registry.acquire(owner,true);
        check(!descendants[7].getClipChildren(),"eighth descendant excluded");
        check(descendants[8].getClipChildren(),"child depth exceeded eight");
        check(!ancestors[11].getClipChildren(),"twelfth ancestor excluded");
        check(ancestors[12].getClipChildren(),"ancestor depth exceeded twelve");
        registry.release(owner);
        for(ViewGroup group:descendants)check(group.getClipChildren(),"descendant not restored");
        for(ViewGroup group:ancestors)check(group.getClipChildren(),"ancestor not restored");
        registry.acquire(null,true);registry.release(null);
    }
    private static void weakOwners() throws Exception {
        OverflowControls registry=registry();ViewGroup parent=new ViewGroup(null);View owner=new View(null);
        owner.parent=parent;registry.acquire(owner,false);
        Field leases=OverflowControls.class.getDeclaredField("leases");leases.setAccessible(true);
        Object lease=((List<?>)leases.get(registry)).get(0);
        Field ownerField=lease.getClass().getDeclaredField("owner");ownerField.setAccessible(true);
        ((WeakReference<?>)ownerField.get(lease)).clear();
        // Deterministically simulate GC clearing the owner; a later API call must release its ancestors.
        registry.release(null);
        check(parent.getClipChildren()&&parent.getClipToPadding(),"collected owner leaked ancestor flags");
        check(((List<?>)leases.get(registry)).isEmpty(),"collected owner lease retained");
        registry.release(owner);
    }
}
