package io.github.baba983.nativedo.app;

import android.content.Context;

import io.github.baba983.nativedo.core.di.AppContainer;

/**
 * Process-wide Discourse session. One {@link AppContainer} owns the cookie store, the session
 * coordinator and the API client, so every screen must share it: two containers would mean two
 * cookie jars and a session that only half the app can see.
 *
 * <p>The factory indirection is what lets a test run the app against a local server instead of
 * linux.do. It exists because the site URL is a construction-time input of {@link AppContainer}
 * rather than something the API client can be told later.</p>
 */
public final class NativeDo {

    /** Where the app talks to by default. */
    public static final String SITE_URL = "https://linux.do/";

    public interface ContainerFactory {
        AppContainer create(Context context);
    }

    private static ContainerFactory factory = context -> new AppContainer(context, SITE_URL, null, null);
    private static AppContainer container;

    public static synchronized AppContainer container(Context context) {
        if (container == null) {
            container = factory.create(context.getApplicationContext());
        }
        return container;
    }

    /** Points the app at another site; the next {@link #container} call builds a fresh container. */
    public static synchronized void setContainerFactory(ContainerFactory newFactory) {
        factory = newFactory;
        container = null;
    }

    private NativeDo() {
    }
}