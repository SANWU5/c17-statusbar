package android.util;
/** Minimal ordered framework cache used by native pager replay fixtures. */
public class SparseArray<E> {
    private final java.util.TreeMap<Integer,E> values=new java.util.TreeMap<>();
    public SparseArray(){ }
    public SparseArray(int capacity){ }
    public E get(int key){return values.get(key);}
    public E get(int key,E fallback){return values.getOrDefault(key,fallback);}
    public void put(int key,E value){values.put(key,value);}
    public void remove(int key){values.remove(key);}
    public void delete(int key){values.remove(key);}
    public void clear(){values.clear();}
    public int size(){return values.size();}
    public int keyAt(int index){return new java.util.ArrayList<>(values.keySet()).get(index);}
    public E valueAt(int index){return values.get(keyAt(index));}
}
