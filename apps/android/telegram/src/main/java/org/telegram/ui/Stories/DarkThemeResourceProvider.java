/* NativeDo stub of org.telegram.ui.Stories.DarkThemeResourceProvider. */
package org.telegram.ui.Stories;

import org.telegram.ui.ActionBar.Theme;

public class DarkThemeResourceProvider implements Theme.ResourcesProvider {

    @Override
    public int getColor(int key) {
        return 0xFF000000;
    }

    public int getColorOrDefault(int key) {
        return 0xFF000000;
    }
}
