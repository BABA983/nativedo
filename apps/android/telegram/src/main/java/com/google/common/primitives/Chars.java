// NativeDo stub for Guava Chars helpers actually referenced by ported code.
package com.google.common.primitives;

public final class Chars {
    private Chars() {}

    public static char[] toArray(java.util.Collection<Character> collection) {
        char[] out = new char[collection.size()];
        int i = 0;
        for (Character c : collection) {
            out[i++] = c;
        }
        return out;
    }
}
