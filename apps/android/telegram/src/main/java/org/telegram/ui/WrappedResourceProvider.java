package org.telegram.ui;

import android.util.SparseIntArray;

import org.telegram.ui.ActionBar.Theme;

public class WrappedResourceProvider implements Theme.ResourcesProvider {
    private final Theme.ResourcesProvider resourcesProvider;
    public final SparseIntArray sparseIntArray = new SparseIntArray();

    public WrappedResourceProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    @Override
    public Integer getColorOrNull(int key) {
        int index = sparseIntArray.indexOfKey(key);
        if (index >= 0) {
            return sparseIntArray.valueAt(index);
        }
        return resourcesProvider == null ? null : resourcesProvider.getColorOrNull(key);
    }
}
