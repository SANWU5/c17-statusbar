package dev.puitheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Real pixel rectangles and drag sessions, including recycled sources and unequal column heights. */
public final class CustomPluginDragCheck {
    private static int checks;
    private static void equal(Object want,Object got,String why) {checks++;if(!Objects.equals(want,got))throw new AssertionError(why+": "+want+" != "+got);}
    private static void near(float want,float got,String why) {checks++;if(Math.abs(want-got)>.001f)throw new AssertionError(why+": "+want+" != "+got);}
    private static CustomPluginDragGeometry.Cell cell(String id,float left,float top,float width,float height) {return new CustomPluginDragGeometry.Cell(id,left,top,width,height);}
    private static final List<String> ORDER=Arrays.asList("a","b","c","d","e");
    private static List<CustomPluginDragGeometry.Cell> grid() {
        // A full-line header consumes lazy indices but is never a sortable plugin.
        return Arrays.asList(cell("sorting",0,0,320,70),cell("a",0,80,150,100),cell("b",170,80,150,180),
                cell("c",0,192,150,140),cell("d",170,272,150,100),cell("e",0,344,150,100));
    }
    private static CustomPluginDragGeometry.Session start() {
        CustomPluginDragGeometry.Session session=new CustomPluginDragGeometry.Session();
        equal(true,session.start(ORDER,"a",grid().get(1),130,100,320,500),"start measured upper-right handle");
        session.layout(grid(),320,500,12);return session;
    }
    private static void geometry() {
        CustomPluginDragGeometry.Session session=start();
        equal("a",session.dragged(),"stable dragged ID");equal(null,session.target(),"initial source slot is not a move");
        near(0,session.left(),"grab x preserved");near(80,session.top(),"grab y preserved");
        session.position(300,100);session.layout(grid(),320,500,12);
        equal("b",session.target(),"cross-column unequal-height hit");
        near(170,session.left(),"ghost follows horizontal motion");
        equal(Arrays.asList("b","a","c","d","e"),session.finish(ORDER,true),"forward drop inserts at target's original index");
        equal(null,session.dragged(),"drop releases drag");equal(null,session.finish(ORDER,true),"drop cannot commit twice");
        equal(Arrays.asList("a","b","c","d","e"),ORDER,"input order immutable");

        session=start();session.position(130,215);session.layout(grid(),320,500,12);
        equal("c",session.target(),"vertical variable-height hit");
        equal(Arrays.asList("b","c","a","d","e"),session.finish(ORDER,true),"cross-row insertion");
        session=new CustomPluginDragGeometry.Session();
        equal(true,session.start(ORDER,"e",grid().get(5),130,364,320,500),"start later short-column card");
        session.position(300,100);session.layout(grid(),320,500,12);
        equal("b",session.target(),"reverse cross-column hit");
        equal(Arrays.asList("a","e","b","c","d"),session.finish(ORDER,true),"reverse insertion before target");

        session=start();session.position(300,20);session.layout(grid(),320,500,12);
        equal(null,session.target(),"header cannot become a plugin destination");equal(null,session.finish(ORDER,true),"header drop does not write");
        session=start();session.position(288,225);session.layout(grid(),320,500,12);
        equal("b",session.target(),"large card body is a target regardless of adjacent column's next row");

        session=start();session.position(300,100);session.layout(grid(),320,500,12);
        List<CustomPluginDragGeometry.Cell> scrolled=Arrays.asList(cell("c",0,-250,150,140),cell("d",170,-170,150,100),cell("e",0,-98,150,100),
                cell("new-header",0,14,320,70),cell("b",170,100,150,180));
        session.layout(scrolled,320,500,12);
        equal("a",session.dragged(),"source recycling does not end parent-owned gesture");
        equal("b",session.target(),"target uses fresh viewport coordinates after scroll");
        equal(Arrays.asList("b","a","c","d","e"),session.finish(ORDER,true),"offscreen source drop by stable ID");

        session=new CustomPluginDragGeometry.Session();
        List<CustomPluginDragGeometry.Cell> tablet=Arrays.asList(cell("a",16,80,150,90),cell("b",178,80,150,180),cell("c",340,80,150,130),
                cell("d",16,182,150,120),cell("e",340,222,150,160));
        equal(true,session.start(ORDER,"a",tablet.get(0),146,100,506,500),"adaptive three-column grid with content padding");
        session.position(470,100);session.layout(tablet,506,500,12);
        equal("c",session.target(),"arbitrary lane count uses measured bounds");
        equal(Arrays.asList("b","c","a","d","e"),session.finish(ORDER,true),"three-column drop keeps logical key order");

        session=new CustomPluginDragGeometry.Session();
        equal(true,session.start(ORDER,"a",cell("a",16,-22,150,100),146,10,320,500),"partially visible source has negative viewport offset");
        session.position(300,100);session.layout(grid(),320,500,12);
        near(170,session.left(),"padding and clipped top do not change x grab");
        near(68,session.top(),"negative item offset preserves actual y grab");
        equal("b",session.target(),"partially visible source still resolves target");
    }
    private static void cancellation() {
        for(float[] point:new float[][]{{-1,100},{321,100},{100,-1},{100,501},{Float.NaN,10},{10,Float.POSITIVE_INFINITY}}) {
            CustomPluginDragGeometry.Session session=start();session.position(point[0],point[1]);
            equal(null,session.dragged(),"outside/nonfinite pointer cancels");equal(null,session.finish(ORDER,true),"outside pointer cannot write");
        }
        for(float[] viewport:new float[][]{{500,320},{320,499},{Float.NaN,500},{320,Float.POSITIVE_INFINITY}}) {
            CustomPluginDragGeometry.Session session=start();session.layout(grid(),viewport[0],viewport[1],12);
            equal(null,session.dragged(),"rotation/window resize cancels");equal(null,session.finish(ORDER,true),"old coordinates cannot write after resize");
        }
        CustomPluginDragGeometry.Session session=start();session.position(300,100);session.layout(grid(),320,500,12);
        equal(null,session.finish(ORDER,false),"refresh/disabled interaction gate cancels write");
        session=start();session.position(300,100);session.layout(grid(),320,500,12);
        equal(false,session.matches(Arrays.asList("b","a","c","d","e")),"sort change invalidates old session");
        equal(null,session.finish(Arrays.asList("b","a","c","d","e"),true),"concurrent order change preserved");
        session=start();session.position(300,100);session.layout(grid(),320,500,12);
        equal(null,session.finish(Arrays.asList("a","c","d","e"),true),"concurrent deletion cannot drop stale ID");
        session=start();session.cancel();equal(null,session.dragged(),"navigation cancellation");
        near(0,session.scrollSpeed(56,720),"cancel stops auto scroll");equal(null,session.finish(ORDER,true),"cancel no write");
        session=start();session.layout(Collections.emptyList(),320,500,12);
        equal(null,session.target(),"empty layout loses target");equal(null,session.finish(ORDER,true),"empty layout no write");
        session=start();session.position(300,100);session.layout(grid(),320,500,Float.NaN);
        equal(null,session.target(),"invalid gap cannot accept arbitrary distant target");equal(null,session.finish(ORDER,true),"invalid geometry margin no write");
        equal(false,new CustomPluginDragGeometry.Session().start(Arrays.asList("a","a"),"a",grid().get(1),130,100,320,500),"duplicate IDs rejected");
        equal(false,new CustomPluginDragGeometry.Session().start(ORDER,"a",grid().get(2),130,100,320,500),"source ID mismatch rejected");
        equal(false,new CustomPluginDragGeometry.Session().start(ORDER,"a",grid().get(1),300,100,320,500),"press outside source rejected");
    }
    private static void scrolling() {
        near(-720,CustomPluginDragGeometry.edgeSpeed(0,500,56,720),"top scroll cap");
        near(-360,CustomPluginDragGeometry.edgeSpeed(28,500,56,720),"top proportional speed");
        near(0,CustomPluginDragGeometry.edgeSpeed(56,500,56,720),"edge boundary stable");
        near(0,CustomPluginDragGeometry.edgeSpeed(250,500,56,720),"center does not scroll");
        near(360,CustomPluginDragGeometry.edgeSpeed(472,500,56,720),"bottom proportional speed");
        near(720,CustomPluginDragGeometry.edgeSpeed(500,500,56,720),"bottom scroll cap");
        near(0,CustomPluginDragGeometry.edgeSpeed(-1,500,56,720),"outside no scroll");
        near(0,CustomPluginDragGeometry.edgeSpeed(10,Float.NaN,56,720),"invalid viewport no scroll");
        near(0,CustomPluginDragGeometry.edgeSpeed(10,100,0,720),"zero edge no scroll");
        near(0,CustomPluginDragGeometry.edgeSpeed(30,60,56,720),"short viewport bands never overlap");
        CustomPluginDragGeometry.Session session=start();session.position(130,10);
        near(-720*46f/56,session.scrollSpeed(56,720),"session scrolls using pointer instead of ghost center");
        session.cancel();near(0,session.scrollSpeed(56,720),"cleanup stops edge loop");
    }
    private static void repeated() {
        for(int i=0;i<100;i++) {
            CustomPluginDragGeometry.Session session=start();
            for(int j=0;j<20;j++) {session.position(300,100);session.layout(grid(),320,500,12);equal("b",session.target(),"stationary pointer cannot reflow-oscillate");}
            List<String> result=session.finish(ORDER,true);
            equal(Arrays.asList("b","a","c","d","e"),result,"stable final order");
            checks++;try {result.add("unexpected");throw new AssertionError("mutable committed snapshot");}catch(UnsupportedOperationException expected) { }
            equal(null,session.target(),"no hover after cleanup");
        }
    }
    public static void main(String[] args) {geometry();cancellation();scrolling();repeated();System.out.println("CustomPluginDragCheck "+checks+" checks passed");}
}
