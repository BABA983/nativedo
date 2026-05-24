/* NativeDo stub — me.vkryl.core.reference.ReferenceList. Upstream provides a
 * weak-ref list for listeners. Real impl would be vendored from TGX. */
package me.vkryl.core.reference;

import java.util.ArrayList;
import java.util.Iterator;

public class ReferenceList<T> implements Iterable<T> {

    private final ArrayList<T> list = new ArrayList<>();

    public ReferenceList() {}
    public ReferenceList(boolean isThreadSafe) {}

    public boolean add(T item) {
        return list.add(item);
    }

    public boolean remove(T item) {
        return list.remove(item);
    }

    public boolean isEmpty() { return list.isEmpty(); }

    public int size() { return list.size(); }

    public T get(int index) { return list.get(index); }

    @Override
    public Iterator<T> iterator() {
        return list.iterator();
    }
}
