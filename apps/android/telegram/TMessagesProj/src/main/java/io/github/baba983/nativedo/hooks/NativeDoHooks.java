package io.github.baba983.nativedo.hooks;

/**
 * NativeDo: the single seam between upstream Telegram code and this client.
 *
 * Upstream files touch this class with one line at most, so a subtree merge can never conflict
 * with more than that single line. Everything the client decides (never start MTProto, replace a
 * screen, intercept an action) is expressed here, in our own package, never by rewriting upstream
 * logic.
 */
public final class NativeDoHooks {

    /**
     * linux.do is a Discourse site reached over HTTPS, so tgnet must never initialize: the app
     * speaks to linux.do, not to Telegram. When this is false, {@code ConnectionsManager}'s
     * constructor returns before {@code native_init()}, which is the only path that builds the
     * native datacenter list.
     */
    private static volatile boolean mtprotoEnabled;

    public static boolean mtprotoEnabled() {
        return mtprotoEnabled;
    }

    public static void setMtprotoEnabled(boolean value) {
        mtprotoEnabled = value;
    }

    /**
     * Telegram's message database is not this client's storage: linux.do's data lives in
     * {@code io.github.baba983.nativedo.core.storage}, and an empty Telegram database would only
     * shadow it. When this is false, {@code MessagesStorage} still builds its queue (upstream code
     * posts work to it) but never opens the file.
     */
    private static volatile boolean telegramStorageEnabled;

    public static boolean telegramStorageEnabled() {
        return telegramStorageEnabled;
    }

    public static void setTelegramStorageEnabled(boolean value) {
        telegramStorageEnabled = value;
    }

    /**
     * The files this client is fetching itself, by the name upstream's own media block looks its
     * file up under. A linux.do clip is fetched by our HTTP client - the site serves some uploads
     * to a signed-in reader only, and it is that client which carries the session - so upstream's
     * {@code FileLoader} never learns a transfer is running. Without this, a block whose file is
     * missing can only draw the state of a download it does not have; with it, the block draws its
     * own loading state while the module fetches, and the module reports the progress itself.
     */
    private static final java.util.Set<String> clientFilesLoading =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean isFileLoading(String fileName) {
        return fileName != null && clientFilesLoading.contains(fileName);
    }

    public static void setFileLoading(String fileName, boolean loading) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        if (loading) {
            clientFilesLoading.add(fileName);
        } else {
            clientFilesLoading.remove(fileName);
        }
    }

    /**
     * Whether a media block has to draw its loading state for {@code fileName}.
     *
     * <p>Upstream's own condition is {@code autoDownload || mediaForced || FileLoader.isLoadingFile},
     * and a file this client fetches itself is none of those. The two upstream values are handed in
     * so that the whole decision is made here and the call site stays one call.</p>
     */
    public static boolean mediaBlockLoading(String fileName, boolean autoDownload, boolean mediaForced) {
        return autoDownload || mediaForced || isFileLoading(fileName);
    }

    /**
     * The transfer this client runs for a file, so the reader's own cancel button can stop it.
     *
     * <p>Upstream's cancel only stops a {@code FileLoader} download; the module's fetch is its own,
     * so the module registers how to stop one here and the button reaches it through the hook.</p>
     */
    public interface FetchCanceller {
        void cancel();
    }

    private static final java.util.Map<String, FetchCanceller> fetchCancellers =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void setFetchCanceller(String fileName, FetchCanceller canceller) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        if (canceller == null) {
            fetchCancellers.remove(fileName);
        } else {
            fetchCancellers.put(fileName, canceller);
        }
    }

    /**
     * A press on a media block's cancel button: whatever fetch this client runs for that file stops.
     * The name is the one the block itself keys the file by, so the caller needs no lookup of its
     * own.
     */
    public static void onMediaBlockCancel(String fileName) {
        if (fileName == null) {
            return;
        }
        FetchCanceller canceller = fetchCancellers.get(fileName);
        if (canceller != null) {
            canceller.cancel();
        }
    }

    /**
     * Whether a message may put {@code FLAG_SECURE} on the activity's window.
     *
     * <p>Upstream reads this off {@code noforwards}: content it may not forward is content it keeps
     * out of a screen recording. That is not what the flag means here. {@code DiscourseRows} sets
     * {@code noforwards} so that no forum post offers to forward one, and a forum post is public
     * text; left alone, upstream would secure the window for every post, which on a device means
     * no screenshot of any topic and a blank thumbnail in the recents list. Nothing this client
     * draws is Telegram's secret media, so the answer is always no.</p>
     */
    public static boolean messageMaySecureWindow() {
        return false;
    }

    /**
     * The shell that hosts upstream's screens, as far as upstream's browser is concerned.
     *
     * <p>Upstream's in-app browser ({@code Browser.openInTelegramBrowser}, {@code ArticleViewer},
     * {@code BottomSheetTabs}, {@code BottomSheetTabsOverlay}) finds its host through
     * {@code LaunchActivity}: the activity's static instance, its bottom-sheet tabs and the safe
     * last fragment of its {@code ActionBarLayout}. This client is hosted by
     * {@code NativeDoActivity}, so {@code LaunchActivity.instance} is null and every one of those
     * lookups would find nothing (or crash). The hooks below hand upstream the same four answers
     * from our own shell, and fall back to {@code LaunchActivity} when it exists, so the upstream
     * lines that call them keep their meaning inside Telegram too.</p>
     */
    public interface BrowserHost {
        org.telegram.ui.ActionBar.BottomSheetTabs getBottomSheetTabs();

        org.telegram.ui.ActionBar.BottomSheetTabsOverlay getBottomSheetTabsOverlay();

        org.telegram.ui.ActionBar.BaseFragment getLastFragment();

        org.telegram.ui.ActionBar.BaseFragment getSafeLastFragment();

        void checkSystemBarColors();
    }

    private static volatile BrowserHost browserHost;

    public static void setBrowserHost(BrowserHost host) {
        browserHost = host;
    }

    /**
     * Whether this client's shell is hosting the browser. Upstream's article-webpage lookup goes
     * through Telegram's server ({@code messages.getWebPage}); with tgnet off that answer never
     * comes, so the browser loads the page directly instead of waiting for it.
     */
    public static boolean browserHostPresent() {
        return browserHost != null;
    }

    public static org.telegram.ui.ActionBar.BottomSheetTabs getBottomSheetTabs() {
        if (browserHost != null) {
            org.telegram.ui.ActionBar.BottomSheetTabs tabs = browserHost.getBottomSheetTabs();
            if (tabs != null) {
                return tabs;
            }
        }
        return org.telegram.ui.LaunchActivity.instance != null
                ? org.telegram.ui.LaunchActivity.instance.getBottomSheetTabs() : null;
    }

    public static org.telegram.ui.ActionBar.BottomSheetTabsOverlay getBottomSheetTabsOverlay() {
        if (browserHost != null) {
            org.telegram.ui.ActionBar.BottomSheetTabsOverlay overlay = browserHost.getBottomSheetTabsOverlay();
            if (overlay != null) {
                return overlay;
            }
        }
        return org.telegram.ui.LaunchActivity.instance != null
                ? org.telegram.ui.LaunchActivity.instance.getBottomSheetTabsOverlay() : null;
    }

    public static org.telegram.ui.ActionBar.BaseFragment getLastFragment() {
        if (browserHost != null) {
            org.telegram.ui.ActionBar.BaseFragment fragment = browserHost.getLastFragment();
            if (fragment != null) {
                return fragment;
            }
        }
        return null;
    }

    public static org.telegram.ui.ActionBar.BaseFragment getSafeLastFragment() {
        if (browserHost != null) {
            org.telegram.ui.ActionBar.BaseFragment fragment = browserHost.getSafeLastFragment();
            if (fragment != null) {
                return fragment;
            }
        }
        return null;
    }

    /**
     * Upstream's {@code ActionBarLayout} hands a freshly created {@code BottomSheetTabs} to the
     * activity's overlay; this is the same hand-over for this client's shell.
     */
    public static void onBottomSheetTabsCreated(org.telegram.ui.ActionBar.BottomSheetTabs tabs) {
        org.telegram.ui.ActionBar.BottomSheetTabsOverlay overlay = getBottomSheetTabsOverlay();
        if (overlay != null) {
            overlay.setTabsView(tabs);
        }
    }

    /** A browser sheet being dismissed into the minimized-tabs strip. */
    public static void dismissSheet(org.telegram.ui.ActionBar.BottomSheetTabsOverlay.Sheet sheet) {
        org.telegram.ui.ActionBar.BottomSheetTabsOverlay overlay = getBottomSheetTabsOverlay();
        if (overlay != null) {
            overlay.dismissSheet(sheet);
        }
    }

    /** The browser changing its navigation-bar color; the shell re-derives the system bars. */
    public static void checkSystemBarColors() {
        if (browserHost != null) {
            browserHost.checkSystemBarColors();
            return;
        }
        if (org.telegram.ui.LaunchActivity.instance != null) {
            org.telegram.ui.LaunchActivity.instance.checkSystemBarColors(true, true, true);
        }
    }

    /**
     * The reader's choice for {@link #isWebBrowserOpenInApp}: whether an {@code http(s)} link opens
     * in the in-app browser. Default true, which is the behavior this shell has always had; the
     * reader's settings screen writes it through {@code setWebBrowserOpenInAppEnabled} and persists
     * it, and the app reads it back at startup before any link can be pressed.
     */
    private static volatile boolean webBrowserOpenInApp = true;

    public static boolean isWebBrowserOpenInAppEnabled() {
        return webBrowserOpenInApp;
    }

    public static void setWebBrowserOpenInAppEnabled(boolean value) {
        webBrowserOpenInApp = value;
    }

    /**
     * The write-back for the toggles that live in upstream's hosted screens ({@code ThemeActivity}'s
     * InappBrowser row, {@code WebBrowserSettings}): this client's settings module registers how to
     * persist the flip, and the hosted row goes through it. Null while a real Telegram host runs,
     * where the upstream server setting is the source of truth and must keep being written.
     */
    private static volatile Runnable webBrowserToggleHandler;

    public static void setWebBrowserToggleHandler(Runnable handler) {
        webBrowserToggleHandler = handler;
    }

    /**
     * Upstream's {@code MessagesController.isWebBrowserInAppEnabled}, answered by this shell when
     * it hosts the app: the reader's stored choice, not Telegram's server-delivered setting.
     * Returns [upstream] when no shell is hosting, so a real Telegram host reads its own value.
     */
    public static boolean isWebBrowserInAppEnabled(boolean upstream) {
        if (browserHost == null) {
            return upstream;
        }
        return webBrowserOpenInApp;
    }

    /**
     * The flip behind that getter. True means the shell handled it (the registered handler
     * persisted the new value); false tells upstream to run its own account-setting path.
     */
    public static boolean toggleWebBrowserInAppEnabled() {
        if (browserHost == null) {
            return false;
        }
        Runnable handler = webBrowserToggleHandler;
        if (handler != null) {
            handler.run();
        } else {
            setWebBrowserOpenInAppEnabled(!webBrowserOpenInApp);
        }
        return true;
    }

    /**
     * Whether a URL opens in the in-app browser.
     *
     * <p>Upstream decides this from {@code MessagesController.webBrowserSettings}, a
     * server-delivered Telegram account setting. This client has no Telegram account, so those
     * settings never arrive and upstream's default would be "outside the app" (custom tabs or the
     * system browser). The chosen default here is the opposite: while this client's shell is
     * hosting the app, every {@code http(s)} URL opens in the in-app browser unless the reader
     * turned that off in the settings screen ({@link #setWebBrowserOpenInAppEnabled}); when it is
     * off, upstream's custom-tabs branch takes the link instead. The upstream value still wins when
     * it is true, so a real Telegram host keeps its own behavior.</p>
     */
    public static boolean isWebBrowserOpenInApp(String url, boolean upstream) {
        if (upstream) {
            return true;
        }
        if (!webBrowserOpenInApp || browserHost == null || url == null) {
            return false;
        }
        android.net.Uri uri = android.net.Uri.parse(url);
        String scheme = uri.getScheme();
        return scheme == null || "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private NativeDoHooks() {
    }
}