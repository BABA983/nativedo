/* NativeDo Bulletin stub — matches upstream structure but all paths are no-ops. */
package org.telegram.ui.Components;

public class Bulletin extends BulletinFactory.Bulletin {
    public boolean hideAfterBottomSheet;

    public Bulletin() { super(); }

    public interface Delegate {
        default int getTopOffset(int tag) { return 0; }
    }

    public static Bulletin getVisibleBulletin() {
        return null;
    }

    public static void addDelegate(Object owner, Delegate delegate) {
    }

    public static void removeDelegate(Object owner) {
    }

    public boolean isShowing() {
        return false;
    }

    public Bulletin hide(long duration) {
        return this;
    }

    public void updatePosition() {}
}
