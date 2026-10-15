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
     *
     * <p>A file the module fetches itself owns its own state: it is loading exactly while the
     * module's own transfer runs, and nothing else. Upstream's auto-download and forced flags are
     * about a {@code FileLoader} transfer this file does not have; left in the condition they would
     * hold the block's spinner up after the module's fetch already failed, and the reader could
     * never press the download button again. Files the module does not fetch keep upstream's own
     * condition unchanged.</p>
     */
    public static boolean mediaBlockLoading(String fileName, boolean autoDownload, boolean mediaForced, boolean fileLoaderLoading) {
        if (isClientMediaFile(fileName)) {
            return isFileLoading(fileName);
        }
        return autoDownload || mediaForced || isFileLoading(fileName) || fileLoaderLoading;
    }

    /**
     * The media files the module fetches itself. Registered as the post body is prepared, before any
     * cell can ask about one, so a block never reads a file as "upstream's to fetch" merely because
     * of when it happened to be built.
     */
    private static final java.util.Set<String> clientMediaFiles =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void setClientMediaFile(String fileName, boolean managed) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        if (managed) {
            clientMediaFiles.add(fileName);
        } else {
            clientMediaFiles.remove(fileName);
        }
    }

    public static boolean isClientMediaFile(String fileName) {
        return fileName != null && clientMediaFiles.contains(fileName);
    }

    /**
     * The media files this client fetched that are animations rather than stills, by the name
     * upstream's own block keys them by.
     *
     * <p>A GIF the module fetched sits in the ordinary image cache under an ordinary name, so
     * upstream's loader decodes it as a single still frame. The block is handed a path location
     * either way; marking the file here is what makes that location an <em>animation</em> location,
     * which is upstream's own signal to build its animated drawable and play the frames. A file the
     * module did not fetch, and every file that is not an animation, keeps the still it always had.
     * </p>
     */
    private static final java.util.Set<String> clientMediaAnimations =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void setClientMediaAnimated(String fileName, boolean animated) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        if (animated) {
            clientMediaAnimations.add(fileName);
        } else {
            clientMediaAnimations.remove(fileName);
        }
    }

    public static boolean isClientMediaAnimated(String fileName) {
        return fileName != null && clientMediaAnimations.contains(fileName);
    }

    /**
     * The media files the module fetches itself, told through this seam: how to start (or retry)
     * the fetch when the reader presses the block's own download button. Upstream's download branch
     * only flips the block's own flags; the bytes are fetched by the module, so a press has to reach
     * it for the fetch to start at all.
     */
    public interface MediaBlockDownloadHandler {
        boolean start(String fileName);
    }

    private static volatile MediaBlockDownloadHandler mediaBlockDownloadHandler;

    public static void setMediaBlockDownloadHandler(MediaBlockDownloadHandler handler) {
        mediaBlockDownloadHandler = handler;
    }

    /**
     * A press on a media block's own download button: the module is given the file's name the block
     * keys it by, and answers whether it started a fetch for it. A file the module does not fetch
     * answers false and keeps upstream's own behavior.
     */
    public static boolean onMediaBlockDownload(String fileName) {
        MediaBlockDownloadHandler handler = mediaBlockDownloadHandler;
        return handler != null && handler.start(fileName);
    }

    /**
     * Whether a media block may hand its file to upstream's own downloader on its own.
     *
     * <p>Upstream reads this off the reader's auto-download setting, and with it on the block asks
     * {@code FileLoader} for the file before any bytes exist. A file this client fetches itself has
     * no Telegram datacenter behind it, so that request can never finish - it stays "loading" in
     * {@code FileLoader} forever, and the block never leaves its spinner even after this client's
     * fetch has already failed. So the module's own files are never auto-downloaded by upstream;
     * the module starts (and reports) their transfer, and hands the bytes back itself.</p>
     */
    public static boolean mediaAutoDownload(String fileName, boolean autoDownload) {
        if (isClientMediaFile(fileName)) {
            return false;
        }
        return autoDownload;
    }

    /**
     * The picture a photo block draws as a placeholder while the full one is still on its way.
     *
     * <p>Upstream uses the photo's own stripped size (tiny inline bytes). A page this client builds
     * itself carries the site's preview as a second, smaller regular size next to the full picture;
     * its bytes are a file the module fetched, so upstream's loader draws it the way it draws any
     * other thumb. When there is no such preview - one size, or upstream's own stripped size - this
     * answers exactly what upstream would have, so the block is unchanged.</p>
     */
    public static org.telegram.tgnet.TLRPC.PhotoSize photoThumbSize(java.util.ArrayList<org.telegram.tgnet.TLRPC.PhotoSize> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        org.telegram.tgnet.TLRPC.TL_photoStrippedSize stripped =
                org.telegram.messenger.FileLoader.getStrippedPhotoSize(sizes);
        if (stripped != null) {
            return stripped;
        }
        if (sizes.size() < 2) {
            return null;
        }
        org.telegram.tgnet.TLRPC.PhotoSize smallest = null;
        for (org.telegram.tgnet.TLRPC.PhotoSize size : sizes) {
            if (size == null) {
                continue;
            }
            if (smallest == null || Math.max(size.w, size.h) < Math.max(smallest.w, smallest.h)) {
                smallest = size;
            }
        }
        return smallest != null && isClientMediaFile(org.telegram.messenger.FileLoader.getAttachFileName(smallest))
                ? smallest : null;
    }

    /** Synthetic photos have no Telegram transport; only existing local bytes enter its loader. */
    public static org.telegram.messenger.ImageLocation photoImageLocation(
            org.telegram.tgnet.TLRPC.PhotoSize size, org.telegram.tgnet.TLRPC.Photo photo) {
        if (size != null && isClientMediaFile(org.telegram.messenger.FileLoader.getAttachFileName(size))) {
            org.telegram.messenger.FileLoader loader = org.telegram.messenger.FileLoader.getInstance(0);
            java.io.File cache = loader.getPathToAttach(size, true);
            java.io.File images = loader.getPathToAttach(size, false);
            if (cache.length() == 0 && images.length() == 0) {
                return null;
            }
            // A filesystem path is cache-only in ImageLoader, even if it vanishes before decode.
            String path = (cache.length() > 0 ? cache : images).getAbsolutePath();
            // An animation this client fetched is a GIF in the ordinary cache: the same path, but a
            // location upstream's loader plays frame by frame instead of decoding a single still.
            if (isClientMediaAnimated(org.telegram.messenger.FileLoader.getAttachFileName(size))) {
                return org.telegram.messenger.ImageLocation.getForVideoPath(path);
            }
            return org.telegram.messenger.ImageLocation.getForPath(path);
        }
        return org.telegram.messenger.ImageLocation.getForPhoto(size, photo);
    }

    /** Keeps RichPhotoBlock's receiver parameters while withholding missing synthetic files. */
    public static boolean applyPhotoImage(org.telegram.messenger.ImageReceiver receiver,
            org.telegram.tgnet.TLRPC.PhotoSize full, org.telegram.tgnet.TLRPC.PhotoSize thumb,
            org.telegram.tgnet.TLRPC.Photo photo, Object parent, boolean allowMedia) {
        if (!isClientMediaFile(org.telegram.messenger.FileLoader.getAttachFileName(full))) return false;
        receiver.setImage(null, null, allowMedia ? photoImageLocation(full, photo) : null, null,
                photoImageLocation(thumb, photo), "b1", null, full.size, null, parent, 1);
        return true;
    }

    /** The viewer uses the same local preview as the bubble, without starting Telegram transport. */
    public static org.telegram.messenger.ImageLocation photoViewerThumbLocation(
            org.telegram.tgnet.TLRPC.Photo photo, boolean needed) {
        if (!needed) return null;
        org.telegram.tgnet.TLRPC.PhotoSize full = org.telegram.messenger.FileLoader.getClosestPhotoSizeWithSize(
                photo.sizes, org.telegram.messenger.AndroidUtilities.getPhotoSize());
        org.telegram.tgnet.TLRPC.PhotoSize thumb = isClientMediaFile(org.telegram.messenger.FileLoader.getAttachFileName(full))
                ? photoThumbSize(photo.sizes) : org.telegram.messenger.FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 80);
        return photoImageLocation(thumb, photo);
    }

    public interface PhotoProgressSetter {
        void set(int state, boolean animated, boolean animateIcon);
    }

    /** The viewer's own icons follow the HTTP transfer, including failure and cancellation. */
    public static void photoViewerProgress(PhotoProgressSetter setter, String fileName,
            int upstream, int cancel, int download, boolean animated, boolean animateIcon) {
        int state = isClientMediaFile(fileName) ? (isFileLoading(fileName) ? cancel : download) : upstream;
        setter.set(state, animated, animateIcon);
    }

    /** The viewer's central download/cancel press reaches the same transfer as the bubble. */
    public static boolean onPhotoViewerDownload(String fileName, boolean download) {
        if (!download || !isClientMediaFile(fileName)) return false;
        if (isFileLoading(fileName)) {
            onMediaBlockCancel(fileName);
        } else {
            onMediaBlockDownload(fileName);
        }
        return true;
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

    /**
     * The seam for upstream's rich editor ({@code org.telegram.ui.iv.RichEditor}).
     *
     * <p>The editor is bound to a {@code ChatActivity}: its send path builds a Telegram message and
     * hands it to {@code SendMessagesHelper}, and its draft path writes to Telegram's own draft
     * database. This client has neither. The host is bound to the editor instance itself - this
     * client opens the upstream screen through a {@code DiscourseRichEditor} subclass that
     * implements this interface - so the upstream call sites hand the editor and its live rows
     * straight in, with no process-wide registry to keep in sync and no reflection to reach a
     * package-private list. A real Telegram editor is not an instance of this interface, so the
     * hooks return false and upstream runs unchanged.</p>
     */
    public interface RichEditorHost {
        void bindRows(java.util.List<org.telegram.ui.iv.BlockRow> rows);

        /**
         * The editor's Apply/send was pressed, with the editor's live blocks. Return true when the
         * content was applied here (exported to the reader's composer, say); false lets upstream
         * send it as a Telegram message.
         */
        boolean onApply(java.util.List<org.telegram.ui.iv.BlockRow> rows);

        /**
         * The editor is keeping the reader's work (a back press, a transition out), with the
         * editor's live blocks. Return true when the content was written here; false lets upstream
         * persist its own draft.
         */
        boolean onPersistDraft(java.util.List<org.telegram.ui.iv.BlockRow> rows);

        boolean canClose(java.util.List<org.telegram.ui.iv.BlockRow> rows, boolean showError);
    }

    /** The host bound to {@code editor}, or null for a real Telegram editor. */
    private static RichEditorHost richEditorHost(org.telegram.ui.iv.RichEditor editor) {
        return editor instanceof RichEditorHost ? (RichEditorHost) editor : null;
    }

    /** Whether this client's editor owns the screen; upstream's controls ask here. */
    public static boolean richEditorHostPresent(org.telegram.ui.iv.RichEditor editor) {
        return richEditorHost(editor) != null;
    }

    /**
     * Answered from the editor's own send path with its live rows; true means the content left
     * through this client and upstream's Telegram send is skipped.
     */
    public static boolean onRichEditorSend(org.telegram.ui.iv.RichEditor editor, java.util.List<org.telegram.ui.iv.BlockRow> rows) {
        RichEditorHost host = richEditorHost(editor);
        return host != null && host.onApply(rows);
    }

    /**
     * Answered from the editor's own draft path with its live rows; true means this client wrote
     * the draft and upstream's Telegram draft write is skipped.
     */
    public static boolean onRichEditorPersistDraft(org.telegram.ui.iv.RichEditor editor, java.util.List<org.telegram.ui.iv.BlockRow> rows) {
        RichEditorHost host = richEditorHost(editor);
        return host != null && host.onPersistDraft(rows);
    }

    public static boolean richEditorCanClose(org.telegram.ui.iv.RichEditor editor, java.util.List<org.telegram.ui.iv.BlockRow> rows, boolean showError) {
        RichEditorHost host = richEditorHost(editor);
        return host == null || host.canClose(rows, showError);
    }

    /**
     * Telegram's premium lock on the editor, answered by whether this client owns it.
     *
     * <p>Upstream reads the lock off a Telegram account's entitlement. This client has no account,
     * so the lock would cover formatting the site does support (headings, lists, tables, marked,
     * sub/super) and would block the Apply. While this client owns the editor the lock is off; a
     * real Telegram editor keeps the value upstream computed.</p>
     */
    public static boolean richEditorPremiumLock(org.telegram.ui.iv.RichEditor editor, boolean upstream) {
        return richEditorHost(editor) == null && upstream;
    }

    public static void richEditorCreated(org.telegram.ui.iv.RichEditor editor, org.telegram.ui.Components.ChatActivityEnterView.SendButton button, java.util.List<org.telegram.ui.iv.BlockRow> rows) {
        RichEditorHost host = richEditorHost(editor);
        if (host != null) {
            host.bindRows(rows);
            button.setResourceId(org.telegram.messenger.R.drawable.input_done);
            button.setContentDescription("完成并返回");
            button.setOnLongClickListener(null);
        }
    }

    /**
     * One control that only means something inside Telegram (AI, media attach, inline button, date,
     * formula): hidden while this client owns the editor, so the reader is never offered a screen
     * this client cannot finish. The view is returned so the upstream call site keeps its chain,
     * and it is untouched - still visible - in a real Telegram build.
     */
    public static <T extends android.view.View> T richEditorControl(org.telegram.ui.iv.RichEditor editor, T control) {
        if (control != null && richEditorHost(editor) != null) {
            control.setVisibility(android.view.View.GONE);
        }
        return control;
    }

    /**
     * The seam for upstream's mention panel ({@code org.telegram.ui.Components.MentionsContainerView}).
     *
     * <p>The panel builds its own {@code MentionsAdapter} against Telegram's chat participants,
     * which this client has none of: a linux.do composer offers the site's users and mentionable
     * groups instead. The host is bound to the panel instance itself - this client opens the panel
     * through a {@code DiscourseMentionsPanel} subclass that implements this interface - so
     * upstream's single construction site hands the panel straight in, with no process-wide
     * registry to keep in sync and no reflection. A real Telegram panel is not an instance of this
     * interface, so the hook hands back upstream's own adapter and Telegram runs unchanged.</p>
     */
    public interface MentionsHost {
        org.telegram.ui.Adapters.MentionsAdapter createMentionsAdapter(
                org.telegram.ui.Adapters.MentionsAdapter upstream,
                org.telegram.ui.Components.MentionsContainerView container,
                android.content.Context context,
                long dialogId,
                long threadMessageId,
                org.telegram.ui.ActionBar.Theme.ResourcesProvider resourcesProvider);
    }

    /**
     * The adapter a mention panel runs with.
     *
     * <p>Upstream's own adapter unless this client owns the panel (see {@link MentionsHost}); the
     * arguments are exactly the ones upstream's construction used, so the fallback rebuilds the
     * adapter it would have built itself.</p>
     */
    public static org.telegram.ui.Adapters.MentionsAdapter createMentionsAdapter(
            org.telegram.ui.Adapters.MentionsAdapter upstream,
            org.telegram.ui.Components.MentionsContainerView container,
            android.content.Context context,
            long dialogId,
            long threadMessageId,
            org.telegram.ui.ActionBar.Theme.ResourcesProvider resourcesProvider) {
        if (container instanceof MentionsHost) {
            return ((MentionsHost) container).createMentionsAdapter(
                    upstream, container, context, dialogId, threadMessageId, resourcesProvider);
        }
        return upstream;
    }

    /**
     * The repost of upstream's indicator animation ({@code FilterTabsView.animationRunnable}).
     *
     * <p>Upstream reposts the runnable with no delay and relies on the platform's traversal barrier
     * to hold it to one step per frame: the invalidate inside each step schedules a traversal,
     * whose sync barrier parks the repost until the next frame. Where that barrier does not apply,
     * the whole 320 ms animation drains inside a single frame - the selected capsule jumps to its
     * destination instead of travelling - which is the failure this frame delay removes. Drawing
     * and interpolation stay upstream's own; only the repost's delay changes, and a real Telegram
     * host (no shell bound here) keeps upstream's own scheduling.</p>
     */
    public static void repostIndicatorAnimation(Runnable runnable) {
        if (browserHost == null) {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(runnable);
        } else {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(runnable, 16);
        }
    }

    /** A client-owned reaction PNG must never fall through to a Telegram datacenter download. */
    public static org.telegram.messenger.ImageLocation documentImageLocation(
            org.telegram.tgnet.TLRPC.Document document, org.telegram.messenger.ImageLocation upstream) {
        if (!mtprotoEnabled && "image/png".equals(document.mime_type)
                && document.localPath != null && !document.localPath.isEmpty()) {
            return org.telegram.messenger.ImageLocation.getForPath(document.localPath);
        }
        return upstream;
    }

    /**
     * The decode type for a path {@code ImageLocation}.
     *
     * <p>Upstream tags a reaction picture with the {@code "tgs"} extension because a Telegram
     * reaction is a Lottie animation. This client's reaction pictures are PNGs it fetched itself
     * and hands upstream as a path location, so that tag would make the loader build an empty
     * Lottie drawable out of the PNG and draw nothing at all. While this client owns the file (a
     * host build, not Telegram) a PNG path is decoded as a plain bitmap; upstream's own decode type
     * is kept for anything this client did not fetch, so a real Telegram reaction still animates.</p>
     */
    public static int pathImageType(org.telegram.messenger.ImageLocation imageLocation, int upstream) {
        if (!mtprotoEnabled && imageLocation != null && imageLocation.path != null
                && imageLocation.path.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            return 0;
        }
        return upstream;
    }

    /**
     * NativeDo: the drawn size of an inline custom emoji in a rich message body.
     *
     * <p>Upstream lays a body emoji out at {@code dp(4 + fontSize + 4)} drawn with scale {@code 1.2} -
     * about 1.8x the text it sits in - so a Discourse {@code :laugh:} (and every other inline emoji)
     * bulges out of its line. The only other thing the span contributes is its font metrics, and
     * upstream already preserves those ({@code AnimatedEmojiSpan.preserveFontMetrics}) so the line
     * box stays the text's own; shrinking the span's size therefore shrinks the artwork without
     * touching line height. This sizes it to the body text's line height instead, so the emoji fills
     * its line the way a glyph does, and a later font-size change moves it with the text.</p>
     *
     * <p>Upstream's own value is what ran at the call site before this hook: the span is built
     * exactly as upstream builds it and only its drawn size is adjusted. A link-button label keeps
     * upstream's size, so buttons read the same.</p>
     *
     * @param span the span upstream just built; null-safe.
     * @param paint the body text paint, whose font metrics give the line height.
     * @param inButton whether the emoji is a link-button label (left at upstream's size).
     */
    public static void inlineEmojiSpanForBody(
            org.telegram.ui.Components.AnimatedEmojiSpan span, android.graphics.Paint paint, boolean inButton) {
        if (span == null || paint == null || inButton || mtprotoEnabled) {
            return;
        }
        final android.graphics.Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        final int lineHeight = Math.abs(fm.ascent) + Math.abs(fm.descent);
        // The span draws a square of size * scale; upstream's body scale is 1.2 (1 for a button).
        span.setSize(Math.max(1, Math.round(lineHeight / 1.2f)));
    }

    /** The site's static PNG has no Lottie enter animation to wait for. */
    public static boolean hasReactionEnterAnimation(int account, String emoticon, boolean upstream) {
        if (!mtprotoEnabled && emoticon != null) {
            org.telegram.tgnet.TLRPC.TL_availableReaction reaction =
                    org.telegram.messenger.MediaDataController.getInstance(account).getReactionsMap().get(emoticon);
            if (reaction != null && reaction.appear_animation != null
                    && "image/png".equals(reaction.appear_animation.mime_type)
                    && reaction.appear_animation.localPath != null) {
                return false;
            }
        }
        return upstream;
    }

    /**
     * The seam for upstream's composer recording gesture
     * ({@code org.telegram.ui.Components.ChatActivityEnterView}'s microphone affordance).
     *
     * <p>Upstream's gesture starts and stops Telegram's own recorder directly
     * ({@code MediaController.startRecording}/{@code stopRecording}, and {@code CameraController}
     * for a video note). Neither belongs here: this client records with its own Android recorder and
     * uploads to linux.do, and Telegram's recorder reaches for a Telegram account and datacenter this
     * app does not have.</p>
     *
     * <p>The host is bound to the composer's own {@code ChatActivityEnterViewDelegate} - the one
     * instance the composer already owns - not to a process-wide table. This client's composer
     * delegate implements this interface and forwards to the one recording host bound to its screen,
     * so upstream's single touch call site hands the live delegate in and nothing else has to be
     * kept in sync. A real Telegram delegate is not an instance of this interface (and a screen that
     * never bound a host forwards to null), so upstream's own gesture runs unchanged.</p>
     */
    public interface ComposerRecordHost {
        /**
         * A touch on the composer's recording affordance. Return true when this host owns the
         * gesture (upstream's own handling is then skipped); false lets upstream run unchanged.
         */
        boolean onRecordTouch(android.view.MotionEvent event);
    }

    /**
     * Answered from upstream's composer touch path with the composer's own delegate and the event.
     * True means this client's host owns the gesture and upstream's Telegram recording must not run.
     * Only a delegate that implements {@link ComposerRecordHost} is asked; every other delegate
     * (a real Telegram composer, or a screen with no host bound) keeps upstream's gesture exactly.
     */
    public static boolean handleRecordGesture(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            android.view.MotionEvent event) {
        return delegate instanceof ComposerRecordHost && ((ComposerRecordHost) delegate).onRecordTouch(event);
    }

    private NativeDoHooks() {
    }
}
