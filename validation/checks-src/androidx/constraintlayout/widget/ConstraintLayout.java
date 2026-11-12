package androidx.constraintlayout.widget;

/** Desktop layout-parameter contract only; this is not a ConstraintLayout solver. */
public class ConstraintLayout extends android.view.ViewGroup {
    private final java.util.Map<Integer,android.view.View> solverChildrenForCheck=new java.util.HashMap<>();
    public ConstraintLayout() { super(null); }
    @Override public void addView(android.view.View view){super.addView(view);solverChildrenForCheck.put(view.getId(),view);}
    @Override public void addView(android.view.View view,int index,android.view.ViewGroup.LayoutParams params){super.addView(view,index,params);solverChildrenForCheck.put(view.getId(),view);}
    @Override public void removeView(android.view.View view){super.removeView(view);solverChildrenForCheck.remove(view.getId());}
    public android.view.View solverChildForCheck(int id){return solverChildrenForCheck.get(id);}
    @Override public android.view.View findViewById(int id) {
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i).getId() == id) return getChildAt(i);
        return null;
    }
    public static final class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {
        // The audited OEM copy constructor aliases the solver widget. A page and its
        // header must use (int,int) construction to obtain separate solver ownership.
        public Object mWidget = new Object();
        public int leftToLeft=-1,leftToRight=-1,rightToLeft=-1,rightToRight=-1;
        public int startToStart=-1,startToEnd=-1,endToStart=-1,endToEnd=-1;
        public int topToTop=-1,topToBottom=-1,bottomToTop=-1,bottomToBottom=-1;
        public int baselineToBaseline=-1,baselineToTop=-1,baselineToBottom=-1,horizontalChainStyle;
        public float verticalBias=.5f,horizontalBias=.5f;
        public LayoutParams(int w,int h) { super(w,h); }
        public LayoutParams(android.view.ViewGroup.LayoutParams source) {
            super(source instanceof android.view.ViewGroup.MarginLayoutParams
                    ? (android.view.ViewGroup.MarginLayoutParams) source
                    : new android.view.ViewGroup.MarginLayoutParams(source.width,source.height));
            if (!(source instanceof LayoutParams)) return;
            LayoutParams s=(LayoutParams)source;
            mWidget=s.mWidget;
            leftToLeft=s.leftToLeft;leftToRight=s.leftToRight;rightToLeft=s.rightToLeft;rightToRight=s.rightToRight;
            startToStart=s.startToStart;startToEnd=s.startToEnd;endToStart=s.endToStart;endToEnd=s.endToEnd;
            topToTop=s.topToTop;topToBottom=s.topToBottom;bottomToTop=s.bottomToTop;bottomToBottom=s.bottomToBottom;
            baselineToBaseline=s.baselineToBaseline;baselineToTop=s.baselineToTop;baselineToBottom=s.baselineToBottom;
            horizontalChainStyle=s.horizontalChainStyle;horizontalBias=s.horizontalBias;verticalBias=s.verticalBias;
        }
    }
}
