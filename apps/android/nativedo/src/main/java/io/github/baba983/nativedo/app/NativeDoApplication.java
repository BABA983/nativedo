package io.github.baba983.nativedo.app;

import org.telegram.messenger.ApplicationLoader;

import io.github.baba983.nativedo.BuildConfig;

/**
 * The app's Application.
 *
 * <p>Upstream's loader answers "what is this app's id" with null unless the module that ships the
 * APK says otherwise, and its own app module says so with a subclass of its own. The id is not
 * cosmetic: {@code AndroidUtilities.openForView} builds the FileProvider authority from it, so a
 * null id means a tapped attachment throws instead of opening.</p>
 */
public class NativeDoApplication extends ApplicationLoader {

    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }
}
