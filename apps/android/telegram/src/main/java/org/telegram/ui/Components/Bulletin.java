/* NativeDo Bulletin stub — matches upstream structure but all paths are no-ops. */
package org.telegram.ui.Components;

public class Bulletin extends BulletinFactory.Bulletin {
    public Bulletin() { super(); }

    public static Bulletin getVisibleBulletin() {
        return null;
    }

    public void updatePosition() {}
}
