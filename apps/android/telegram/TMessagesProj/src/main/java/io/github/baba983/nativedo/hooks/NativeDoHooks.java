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

    /** Article thumbnails use the same local-only photo sources as their full-size blocks. */
    private static org.telegram.messenger.ImageLocation groupedPhotoThumb(org.telegram.tgnet.tl.TL_iv.PageBlock block) {
        if (!(block.thumbObject instanceof org.telegram.tgnet.TLRPC.Photo)) {
            return browserHostPresent() ? null : org.telegram.messenger.ImageLocation.getForObject(block.thumb, block.thumbObject);
        }
        org.telegram.tgnet.TLRPC.Photo photo = (org.telegram.tgnet.TLRPC.Photo) block.thumbObject;
        org.telegram.messenger.ImageLocation location = photoImageLocation(block.thumb, photo);
        if (location != null) return location;
        for (org.telegram.tgnet.TLRPC.PhotoSize size : photo.sizes) {
            location = photoImageLocation(size, photo);
            if (location != null) return location;
        }
        return null;
    }

    public static void addGroupedPhotoThumb(java.util.ArrayList<org.telegram.messenger.ImageLocation> out,
            org.telegram.tgnet.tl.TL_iv.PageBlock block, int index) {
        out.add(index, groupedPhotoThumb(block));
    }

    /** Replace only newly available locations, keeping the strip's drag, index and receivers. */
    public static void refreshGroupedPhotoLocations(java.util.ArrayList<org.telegram.messenger.ImageLocation> locations,
            java.util.ArrayList<Object> objects, java.util.ArrayList<org.telegram.messenger.ImageReceiver> receivers,
            Object parent) {
        for (int i = 0; i < objects.size(); i++) {
            if (!(objects.get(i) instanceof org.telegram.tgnet.tl.TL_iv.PageBlock)) continue;
            org.telegram.tgnet.tl.TL_iv.PageBlock block = (org.telegram.tgnet.tl.TL_iv.PageBlock) objects.get(i);
            if (!isClientMediaFile(org.telegram.messenger.FileLoader.getAttachFileName(block.thumb))) continue;
            org.telegram.messenger.ImageLocation next = groupedPhotoThumb(block);
            org.telegram.messenger.ImageLocation previous = locations.get(i);
            if (next == null || previous != null && java.util.Objects.equals(previous.path, next.path)) continue;
            locations.set(i, next);
            for (org.telegram.messenger.ImageReceiver receiver : receivers) {
                if (receiver.getParam() == i) receiver.setImage(null, null, next, "80_80", 0, null, parent, 1);
            }
        }
    }

    public static void refreshGroupedPhotos(String fileName, org.telegram.ui.Components.GroupedPhotosListView strip) {
        if (strip != null && isClientMediaFile(fileName)) strip.fillList();
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
     * NativeDo: the sender tag a chat draws beside the author's name.
     *
     * <p>A forum keeps the site's own user title in that slot ({@code from_rank}). The tag's text
     * is the site's; only its paint is ours, so a holographic title can carry its gradient without
     * upstream knowing what a Discourse title is. A missing decorator leaves upstream's tag
     * exactly as it was.</p>
     */
    public interface NameTagDecorator {
        android.text.SpannableStringBuilder decorate(android.text.SpannableStringBuilder tag);
    }

    private static volatile NameTagDecorator nameTagDecorator;

    public static void setNameTagDecorator(NameTagDecorator decorator) {
        nameTagDecorator = decorator;
    }

    public static android.text.SpannableStringBuilder decorateNameTag(android.text.SpannableStringBuilder tag) {
        NameTagDecorator decorator = nameTagDecorator;
        return tag == null || decorator == null ? tag : decorator.decorate(tag);
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

        java.util.ArrayList<org.telegram.ui.ActionBar.INavigationLayout> getSheetFragmentsStack();

        org.telegram.ui.ActionBar.BaseFragment createBrowserSettings();
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

    /** Sheets belong to the activity that opened them, not Telegram's absent LaunchActivity. */
    public static java.util.ArrayList<org.telegram.ui.ActionBar.INavigationLayout> sheetFragmentsStack() {
        if (browserHost != null) {
            java.util.ArrayList<org.telegram.ui.ActionBar.INavigationLayout> stack = browserHost.getSheetFragmentsStack();
            if (stack != null) {
                return stack;
            }
        }
        return org.telegram.ui.LaunchActivity.instance != null
                ? org.telegram.ui.LaunchActivity.instance.sheetFragmentsStack : null;
    }

    /** Capture the owning stack so a late dismissal cannot remove a replacement activity's sheet. */
    public static java.util.ArrayList<org.telegram.ui.ActionBar.INavigationLayout> addSheetFragment(org.telegram.ui.ActionBar.INavigationLayout layout) {
        java.util.ArrayList<org.telegram.ui.ActionBar.INavigationLayout> stack = sheetFragmentsStack();
        if (stack != null) {
            stack.add(layout);
        }
        return stack;
    }

    public static void removeSheetFragment(java.util.ArrayList<org.telegram.ui.ActionBar.INavigationLayout> stack,
            org.telegram.ui.ActionBar.INavigationLayout layout) {
        if (stack != null) {
            stack.remove(layout);
        }
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

    /** Local settings only: Telegram account settings and shared-cookie clearing are not hosted. */
    public static void openBrowserSettings() {
        org.telegram.ui.ActionBar.BaseFragment lastFragment = getSafeLastFragment();
        BrowserHost host = browserHost;
        if (lastFragment == null || host == null) {
            return;
        }
        org.telegram.ui.ActionBar.BaseFragment.BottomSheetParams params = new org.telegram.ui.ActionBar.BaseFragment.BottomSheetParams();
        params.transitionFromLeft = true;
        lastFragment.showAsSheet(host.createBrowserSettings(), params);
    }

    /**
     * Sharing the browser's current address through the system's own share sheet, rather than
     * Telegram's {@code ShareAlert} (which sends the link as a Telegram message and offers Telegram
     * chats). Returns true when the shell handled it; a real Telegram host keeps upstream's dialog.
     */
    public static boolean shareInAppBrowser(android.app.Activity activity, String url) {
        if (browserHost == null || activity == null || url == null) {
            return false;
        }
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(android.content.Intent.EXTRA_TEXT, url);
        activity.startActivity(android.content.Intent.createChooser(send, null));
        return true;
    }

    /**
     * Adds one of the web browser's saved-message overflow entries
     * ({@code WebActionBar.bookmark_item} and {@code bookmarks_item}). The backing store upstream
     * uses is Telegram's Saved Messages and the server round trip behind it
     * ({@code ArticleViewer.addBookmark}), neither of which exists in this host, so the entries are
     * not offered while the shell hosts the browser. A real Telegram host keeps them.
     */
    public static void addBrowserMenuSavedEntry(org.telegram.ui.Components.ItemOptions options, int id, int icon, CharSequence text, Runnable action) {
        if (browserHost != null
                && (id == org.telegram.ui.web.WebActionBar.bookmark_item
                    || id == org.telegram.ui.web.WebActionBar.bookmarks_item)) {
            return;
        }
        options.add(icon, text, action);
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

        default boolean onComposerRecordStart(int account, int guid) { return false; }
        default void onComposerRecordStop(int send) {}
        default void onComposerRecordTogglePause() {}
        default boolean isComposerRecordingPaused() { return false; }
        default boolean onComposerRecordPermission(android.app.Activity activity) { return false; }
        default int mapRecordInterfaceState(int state) { return state; }
        default void onComposerVoiceEditor(org.telegram.ui.Components.RecordedAudioPlayerView editor, Runnable clear) {}
        default boolean onComposerVoiceSend() { return false; }
        default void onComposerVoiceTrim(long start, long end, Runnable done) {}
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

    private static ComposerRecordHost composerRecordHost(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate) {
        return delegate instanceof ComposerRecordHost ? (ComposerRecordHost) delegate : null;
    }

    public static void requestComposerRecordPermission(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            android.app.Activity activity, Runnable fallback) {
        ComposerRecordHost host = composerRecordHost(delegate);
        if (host == null || !host.onComposerRecordPermission(activity)) fallback.run();
    }

    public static boolean startComposerRecording(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            int account, int guid, Runnable fallback) {
        ComposerRecordHost host = composerRecordHost(delegate);
        if (host != null) return host.onComposerRecordStart(account, guid);
        fallback.run();
        return true;
    }

    public static void stopComposerRecording(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            int send, Runnable fallback) {
        ComposerRecordHost host = composerRecordHost(delegate);
        if (host != null) host.onComposerRecordStop(send); else fallback.run();
    }

    public static void toggleComposerRecordingPause(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            Runnable fallback) {
        ComposerRecordHost host = composerRecordHost(delegate);
        if (host != null) host.onComposerRecordTogglePause(); else fallback.run();
    }

    public static boolean isComposerRecordingPaused(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            java.util.function.BooleanSupplier fallback) {
        ComposerRecordHost host = composerRecordHost(delegate);
        return host == null ? fallback.getAsBoolean() : host.isComposerRecordingPaused();
    }

    public static void cleanComposerRecording(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            boolean fromCancel, Runnable fallback) {
        if (composerRecordHost(delegate) == null) fallback.run();
    }

    public static int mapRecordInterfaceState(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate, int state) {
        ComposerRecordHost host = composerRecordHost(delegate);
        return host == null ? state : host.mapRecordInterfaceState(state);
    }

    public static boolean redirectRecordInterfaceState(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            int state, java.util.function.IntConsumer apply) {
        int mapped = mapRecordInterfaceState(delegate, state);
        if (mapped == state) return false;
        apply.accept(mapped);
        return true;
    }

    public static void bindComposerVoiceEditor(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            org.telegram.ui.Components.RecordedAudioPlayerView editor, Runnable clear) {
        ComposerRecordHost host = composerRecordHost(delegate);
        if (host != null) host.onComposerVoiceEditor(editor, clear);
    }

    public static boolean sendComposerVoice(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate) {
        ComposerRecordHost host = composerRecordHost(delegate);
        return host != null && host.onComposerVoiceSend();
    }

    public static void trimComposerRecording(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            long start, long end, Runnable done, Runnable fallback) {
        ComposerRecordHost host = composerRecordHost(delegate);
        if (host != null) host.onComposerVoiceTrim(start, end, done); else fallback.run();
    }

    /** Draft persistence and Telegram-player cleanup are services, not editor rendering. */
    public static void composerVoiceAccountAction(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate, Runnable fallback) {
        if (composerRecordHost(delegate) == null) fallback.run();
    }

    public static org.telegram.messenger.MessageObject composerPlayingMessage(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            java.util.function.Supplier<org.telegram.messenger.MessageObject> fallback) {
        return composerRecordHost(delegate) == null ? fallback.get() : null;
    }

    /**
     * Media content the composer's own input path hands the app: an IME's {@code commitContent} (a
     * keyboard's GIF or sticker insertion) or an image pasted from the clipboard.
     *
     * <p>Upstream sends a GIF document or a photo through {@code SendMessagesHelper}, and opens its
     * own photo editor for anything else. Both are Telegram media services this host does not have.
     * A delegate that implements this seam claims the content instead and attaches it to the post
     * being written.</p>
     *
     * <p>Like the recording seam, the host is the composer's own
     * {@code ChatActivityEnterViewDelegate} instance, not a process-wide table, so a real Telegram
     * composer's delegate is not an instance and upstream's own branch runs unchanged.</p>
     */
    public interface ComposerMediaInputHost {
        /**
         * Attach the content at {@code uri} to the post being written. {@code releasePermission}
         * releases the read grant the IME handed over (and does nothing for a clipboard paste); it
         * must run exactly once, after the URI's bytes are this screen's own copy - and also when
         * the content cannot be attached or the screen that would take it is gone.
         */
        void onComposerMediaInput(android.net.Uri uri, Runnable releasePermission);
    }

    /** A paste carries no IME grant; the host still receives one release, so it is a no-op. */
    private static final Runnable NO_MEDIA_RELEASE = () -> {
    };

    /**
     * An IME commit ({@code commitContent}): true when this client's composer owns the content, so
     * upstream's Telegram send/photo-edit branch must not run.
     *
     * <p>A native delegate answers for the content even when it has no intake left (a closed
     * screen, a channel without an upload path): the grant is released and nothing is sent. The
     * content is handed over as the keyboard committed it - the screen's upload path, not this
     * hook, decides what the site takes - so no commit can reach a Telegram service. A real
     * Telegram delegate gets false and keeps upstream's behavior.</p>
     */
    public static boolean commitComposerMediaInput(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            androidx.core.view.inputmethod.InputContentInfoCompat content, int flags) {
        if (!(delegate instanceof ComposerMediaInputHost) || content == null) {
            return false;
        }
        Runnable release = NO_MEDIA_RELEASE;
        if ((flags & android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            release = () -> {
                try {
                    content.releasePermission();
                } catch (Exception ignored) {
                }
            };
        }
        ((ComposerMediaInputHost) delegate).onComposerMediaInput(content.getContentUri(), release);
        return true;
    }

    /**
     * An image pasted from the clipboard: the same seam, without the IME's grant. True means the
     * native composer took it (and upstream's own photo editor must not open); false keeps
     * upstream's editor for a real Telegram delegate.
     */
    public static boolean commitComposerClipboardImage(
            org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate delegate,
            android.net.Uri uri) {
        if (!(delegate instanceof ComposerMediaInputHost)) {
            return false;
        }
        ((ComposerMediaInputHost) delegate).onComposerMediaInput(uri, NO_MEDIA_RELEASE);
        return true;
    }

    public interface AttachSheetHost {
        org.telegram.ui.Components.ChatAttachAlert.AttachAlertLayout attachSheetPollLayout(org.telegram.ui.Components.ChatAttachAlert alert);
        boolean attachSheetIsPollLayout(org.telegram.ui.Components.ChatAttachAlert.AttachAlertLayout layout);
        String richAttachSeedHtml();
        void bindRichAttachRows(java.util.List<org.telegram.ui.iv.BlockRow> rows);
        boolean onRichAttachApply(java.util.List<org.telegram.ui.iv.BlockRow> rows);
        boolean onRichAttachPersistDraft(java.util.List<org.telegram.ui.iv.BlockRow> rows);
        boolean canCloseRichAttachPane(java.util.List<org.telegram.ui.iv.BlockRow> rows, boolean showError);

        /**
         * Whether this sheet's gallery may offer the still-image editor's paint and filter tools.
         *
         * <p>The editor is upstream's own local one, so the question is not "is there an editor"
         * but "can this sheet carry its result": the edited still is exported to the entry's
         * {@code imagePath}, which is the file this client's URI upload sends. The sheet answers
         * true only where it can keep the editor's own account-backed sticker/GIF search and
         * photo picker out of reach; a real Telegram alert is not an {@code AttachSheetHost} and
         * keeps upstream's hidden state.</p>
         */
        default boolean attachSheetHostedStillEdit() {
            return false;
        }
    }

    /**
     * Whether a local gallery still picked in a hosted attachment sheet may offer paint and filter.
     *
     * <p>Upstream hides both for the gallery-only selection type ({@code 4}) the sheet opens its
     * local photos with. This client can carry a still the reader paints or filters - the editor
     * writes its result to {@link org.telegram.messenger.MediaController.MediaEditState#imagePath},
     * which the sheet uploads - so the host capability shows the two tools again.</p>
     *
     * <p>The whole boundary is decided here, not at the call sites: the alert must be the hosted
     * sheet itself (instance-bound, so every real Telegram alert and every other gallery host runs
     * upstream's hidden state), the selection must be the sheet's local gallery type, and the entry
     * must be a still photo. Videos, GIF animations (a {@code SearchImage}), live photos and the
     * document picker stay excluded; their edit result is not a file this upload can send.</p>
     */
    public static boolean hostedStillEditAvailable(org.telegram.ui.Components.ChatAttachAlert alert,
            int sendPhotoType, Object media, boolean documentsPicker) {
        if (sendPhotoType != 4 || documentsPicker) {
            return false;
        }
        AttachSheetHost host = attachSheetHost(alert);
        if (host == null || !host.attachSheetHostedStillEdit()) {
            return false;
        }
        if (!(media instanceof org.telegram.messenger.MediaController.PhotoEntry)) {
            return false;
        }
        org.telegram.messenger.MediaController.PhotoEntry photo =
                (org.telegram.messenger.MediaController.PhotoEntry) media;
        if (photo.isVideo || photo.path == null) {
            return false;
        }
        // A GIF picked from the device gallery is an animated image, not a still: the editor would
        // export one frame. GIF search results are SearchImages and already fail the entry check.
        if (photo.path.toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) {
            return false;
        }
        // A live photo carries a video alongside the still; the editor would export its frame,
        // not the motion the reader picked.
        return !photo.isLivePhoto();
    }

    /**
     * Whether a paint editor created under {@code alert} belongs to a hosted attachment sheet.
     *
     * <p>Broader than {@link #hostedStillEditAvailable} on purpose: the hosted sheet can open
     * upstream's editor for a video too, and every editor it opens must keep Telegram's
     * sticker/GIF search and account-backed photo picker out, not only the stills this client
     * newly allows. A real Telegram alert is not an {@code AttachSheetHost} and keeps upstream's
     * full toolbar.</p>
     */
    public static boolean hostedAttachSheetEditor(org.telegram.ui.Components.ChatAttachAlert alert) {
        AttachSheetHost host = attachSheetHost(alert);
        return host != null && host.attachSheetHostedStillEdit();
    }

    /**
     * The hosted paint editors, keyed by their own instance. A real Telegram editor has no entry,
     * so every later question about its toolbar keeps upstream's answer. Weak on both sides: the
     * key is the view the editor owns, and the value is a constant.
     */
    private static final java.util.Map<org.telegram.ui.Components.Paint.Views.LPhotoPaintView, Boolean> hostedPaintEditors =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Binds {@code editor} to the hosted sheet that opened it. Called right after construction, so
     * the editor's first attach can configure its Telegram controls before the reader sees them.
     * A real Telegram alert leaves the editor unbound and untouched.
     */
    public static void bindHostedPaintEditor(
            org.telegram.ui.Components.Paint.Views.LPhotoPaintView editor,
            org.telegram.ui.Components.ChatAttachAlert alert) {
        if (editor == null || !hostedAttachSheetEditor(alert)) {
            return;
        }
        hostedPaintEditors.put(editor, Boolean.TRUE);
    }

    private static boolean hostedPaintEditor(org.telegram.ui.Components.Paint.Views.LPhotoPaintView editor) {
        return editor != null && hostedPaintEditors.containsKey(editor);
    }

    /**
     * The editor's own tabs are built before it is attached, so the hosted state is applied here,
     * on its first attach: Telegram's sticker tab is hidden rather than left as an action the
     * hosted sheet cannot use. Draw and text keep their slots.
     */
    public static void configureHostedPaintEditor(
            org.telegram.ui.Components.Paint.Views.LPhotoPaintView editor, android.view.View stickerTab) {
        if (stickerTab == null || !hostedPaintEditor(editor)) {
            return;
        }
        stickerTab.setVisibility(android.view.View.GONE);
        stickerTab.setEnabled(false);
    }

    /**
     * True when the editor is the hosted sheet's: opening its stickers view would reach Telegram's
     * account-backed sticker/GIF search and, through its photo widget, a fake {@code ChatActivity}
     * over a Telegram media picker. The hosted editor refuses before either is built.
     */
    public static boolean blockHostedPaintStickerSearch(
            org.telegram.ui.Components.Paint.Views.LPhotoPaintView editor) {
        return hostedPaintEditor(editor);
    }

    /**
     * The text tool's emoji panel, built with {@code needAnimatedEmoji} off for a hosted editor:
     * that flag is what makes upstream's {@code EmojiView} load Telegram's animated emoji packs
     * from the account. Plain emoji and the local text tool are unchanged for it.
     */
    public static org.telegram.ui.Components.EmojiView createPaintEmojiView(
            org.telegram.ui.Components.Paint.Views.LPhotoPaintView editor,
            android.content.Context context, org.telegram.ui.ActionBar.Theme.ResourcesProvider resourcesProvider) {
        return new org.telegram.ui.Components.EmojiView(null, !hostedPaintEditor(editor), false, false,
                context, false, null, null, true, resourcesProvider, false);
    }

    /**
     * Upstream's own paint/filter visibility, after it ran: for a hosted still the two controls
     * are shown, exactly as upstream shows them for a chat photo. Nothing is changed for any other
     * alert, type or media - the upstream state (including its {@code GONE}) is left in place.
     *
     * <p>This is a post-pass rather than a rewritten upstream branch so the subtree import keeps
     * its own logic and the host boundary stays in one place. It runs after both visibility
     * passes that matter: the initial show ({@code onPhotoShow}) and every switch
     * ({@code setIsAboutToSwitchToIndex}).</p>
     */
    public static void applyHostedStillEditVisibility(org.telegram.ui.Components.ChatAttachAlert alert,
            int sendPhotoType, Object media, boolean documentsPicker,
            android.view.View paintItem, android.view.View tuneItem) {
        if (paintItem == null || tuneItem == null
                || !hostedStillEditAvailable(alert, sendPhotoType, media, documentsPicker)) {
            return;
        }
        paintItem.setTag(1);
        tuneItem.setTag(1);
        paintItem.setVisibility(android.view.View.VISIBLE);
        tuneItem.setVisibility(android.view.View.VISIBLE);
    }

    private static AttachSheetHost attachSheetHost(org.telegram.ui.Components.ChatAttachAlert alert) {
        return alert instanceof AttachSheetHost ? (AttachSheetHost) alert : null;
    }

    public static int attachSheetRichTab(org.telegram.ui.Components.ChatAttachAlert alert, int index) {
        return attachSheetHost(alert) != null ? index : -1;
    }

    public static int attachSheetPollTab(org.telegram.ui.Components.ChatAttachAlert alert, int richIndex) {
        return attachSheetHost(alert) != null ? richIndex + 1 : -1;
    }

    public static int attachSheetTabCount(org.telegram.ui.Components.ChatAttachAlert alert, int count) {
        return attachSheetHost(alert) != null ? count + 2 : count;
    }

    public static boolean attachSheetRichTabPremium(org.telegram.ui.Components.ChatAttachAlert alert, boolean upstream) {
        return attachSheetHost(alert) == null && upstream;
    }

    public static long attachSheetLayoutId(org.telegram.ui.Components.ChatAttachAlert alert,
            org.telegram.ui.Components.ChatAttachAlert.AttachAlertLayout layout, long currentId) {
        AttachSheetHost host = attachSheetHost(alert);
        return host != null && host.attachSheetIsPollLayout(layout)
                ? org.telegram.ui.Components.ChatAttachAlert.LAYOUT_TYPE_POLL : currentId;
    }

    public static boolean attachSheetNeedsStoragePermission(org.telegram.ui.Components.ChatAttachAlert alert) {
        return attachSheetHost(alert) == null;
    }

    public static boolean attachDocumentPermissionWarning(org.telegram.ui.Components.ChatAttachAlert alert, boolean upstream) {
        return attachSheetNeedsStoragePermission(alert) && upstream;
    }

    public static void setAttachDocumentImageFilter(org.telegram.ui.Components.ChatAttachAlert alert,
            org.telegram.ui.Components.ChatAttachAlertDocumentLayout layout, boolean imagesOnly) {
        layout.setCanSelectOnlyImageFiles(attachSheetHost(alert) == null && imagesOnly);
    }

    public static boolean showAttachPoll(org.telegram.ui.Components.ChatAttachAlert alert, boolean animated, Boolean quiz) {
        AttachSheetHost host = attachSheetHost(alert);
        if (host == null) return false;
        alert.showLayout(host.attachSheetPollLayout(alert));
        return true;
    }

    public static void seedRichAttachPane(org.telegram.ui.Components.ChatAttachAlert alert, org.telegram.ui.iv.RichEditorListView listView) {
        AttachSheetHost host = attachSheetHost(alert);
        String html = host == null ? null : host.richAttachSeedHtml();
        if (html != null) listView.loadHtml(html); else listView.seedEmptyArticle();
    }

    public static boolean richAttachPaneHosted(org.telegram.ui.Components.ChatAttachAlert alert) {
        return attachSheetHost(alert) != null;
    }

    public static boolean richAttachPaneApply(org.telegram.ui.Components.ChatAttachAlert alert, java.util.List<org.telegram.ui.iv.BlockRow> rows) {
        AttachSheetHost host = attachSheetHost(alert);
        return host != null && host.onRichAttachApply(rows);
    }

    public static boolean richAttachPanePersistDraft(org.telegram.ui.Components.ChatAttachAlert alert, java.util.List<org.telegram.ui.iv.BlockRow> rows) {
        AttachSheetHost host = attachSheetHost(alert);
        return host != null && host.onRichAttachPersistDraft(rows);
    }

    public static boolean richAttachPaneOpen(org.telegram.ui.Components.ChatAttachAlert alert, java.util.List<org.telegram.ui.iv.BlockRow> rows) {
        AttachSheetHost host = attachSheetHost(alert);
        return host == null || host.canCloseRichAttachPane(rows, true);
    }

    public static boolean richAttachPaneBack(org.telegram.ui.Components.ChatAttachAlert alert, java.util.List<org.telegram.ui.iv.BlockRow> rows) {
        return attachSheetHost(alert) != null;
    }

    public static boolean richAttachPremiumLock(org.telegram.ui.Components.ChatAttachAlert alert, boolean upstream) {
        return attachSheetHost(alert) == null && upstream;
    }

    /**
     * The alert behind each hosted rich toolbar, keyed by the toolbar's own delegate instance (so a
     * real Telegram delegate carries no entry and upstream's own behavior runs). Weak on both sides:
     * the value holds the alert weakly because the alert already reaches the delegate through its
     * pane, so a strong value would keep every closed pane's entry alive for the life of the process.
     */
    private static final java.util.Map<Object, java.lang.ref.WeakReference<org.telegram.ui.Components.ChatAttachAlert>> hostedRichPanes =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static void bindRichAttachPane(org.telegram.ui.Components.ChatAttachAlert alert,
            org.telegram.ui.iv.RichEditorToolbar.Delegate delegate, java.util.List<org.telegram.ui.iv.BlockRow> rows) {
        AttachSheetHost host = attachSheetHost(alert);
        if (host != null) {
            hostedRichPanes.put(delegate, new java.lang.ref.WeakReference<>(alert));
            host.bindRichAttachRows(rows);
        }
    }

    /**
     * The rich pane's own top-left back button, shown only for the site's hosted pane.
     *
     * <p>Upstream hides it in attachment mode. This client's pane is a draft editor with an owning
     * reader screen, so the same button is shown and closes the pane (see {@link #richAttachBack}).
     * A real Telegram rich-editor pane keeps upstream's hidden button.</p>
     */
    public static void configureRichAttachBack(org.telegram.ui.Components.ChatAttachAlert alert,
            org.telegram.ui.iv.RichEditorToolbar toolbar, org.telegram.ui.iv.RichEditorListView list) {
        toolbar.setBackVisible(attachSheetHost(alert) != null);
        if (attachSheetHost(alert) == null) return;
        // The standalone RichEditor reserves 60dp for its top controls. Reserve the same space
        // without introducing a Markdown block, moving the list's touch origin or replacing its layout manager.
        list.addItemDecoration(new androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect out, android.view.View view,
                    androidx.recyclerview.widget.RecyclerView parent, androidx.recyclerview.widget.RecyclerView.State state) {
                if (parent.getChildAdapterPosition(view) == 0) out.top = org.telegram.messenger.AndroidUtilities.dp(60);
            }
        });
    }

    /** The reserved control row belongs to sheet chrome, not to its scrollable Markdown body. */
    public static int richAttachContentTop(org.telegram.ui.Components.ChatAttachAlert alert,
            org.telegram.ui.iv.RichEditorListView list, int top) {
        return attachSheetHost(alert) != null && list.findViewHolderForAdapterPosition(0) != null
                ? top - org.telegram.messenger.AndroidUtilities.dp(60) : top;
    }

    public static void richAttachToolbarOffset(org.telegram.ui.Components.ChatAttachAlert alert,
            org.telegram.ui.iv.RichEditorToolbar toolbar, int offset) {
        // Full expansion must not put the hosted back control above the safe screen edge.
        toolbar.setTopButtonsOffset(attachSheetHost(alert) == null ? offset : Math.max(offset,
                org.telegram.messenger.AndroidUtilities.statusBarHeight + org.telegram.messenger.AndroidUtilities.dp(8)));
    }

    /**
     * Where the toolbar's own back button sends a press: the hosted pane's sheet, or upstream's own
     * {@code delegate.onBack()} when a real Telegram toolbar built this button.
     *
     * <p>Pressing it is the sheet's own back, so the pane exits by the same path as the system back
     * - saving the supported draft, and staying open for content the site cannot store - rather than
     * Telegram's rich-draft dialog.</p>
     */
    public static void richAttachBack(org.telegram.ui.iv.RichEditorToolbar.Delegate delegate,
            android.widget.ImageView backButton) {
        java.lang.ref.WeakReference<org.telegram.ui.Components.ChatAttachAlert> pane = hostedRichPanes.get(delegate);
        final org.telegram.ui.Components.ChatAttachAlert alert = pane == null ? null : pane.get();
        backButton.setOnClickListener(v -> {
            if (alert != null) {
                alert.onBackPressed();
            } else {
                delegate.onBack();
            }
        });
    }

    public static void configureRichAttachApply(org.telegram.ui.Components.ChatAttachAlert alert,
            org.telegram.ui.Components.ChatActivityEnterView.SendButton control) {
        if (attachSheetHost(alert) != null) {
            control.setResourceId(org.telegram.messenger.R.drawable.input_done);
            control.setContentDescription("完成并返回");
            control.setOnLongClickListener(null);
        }
    }

    public static void richAttachControl(org.telegram.ui.iv.RichEditorToolbar.Delegate delegate, android.view.View control) {
        if (hostedRichPanes.containsKey(delegate)) control.setVisibility(android.view.View.GONE);
    }

    /**
     * A poll cell sizes its answer checkboxes from Telegram's own cap - {@code poll_answers_max}, 12
     * by default - and then indexes that array once per answer, so a poll with more options than the
     * cap throws on attach. A Discourse poll has no such cap: the site's own count raises it before
     * the cell that draws the poll is built, and Telegram's value stays the floor.
     */
    public static void ensurePollAnswerCapacity(int answers) {
        if (answers <= 0) {
            return;
        }
        org.telegram.messenger.MessagesController controller =
                org.telegram.messenger.MessagesController.getInstance(
                        org.telegram.messenger.UserConfig.selectedAccount);
        if (answers <= controller.config.pollAnswersMax.get()) {
            return;
        }
        // The cap is read from the account's own preferences when the config loads, so the value is
        // written there and re-read through the config's own public load: no reflection into it.
        android.content.SharedPreferences preferences =
                org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences(
                        "mainconfig" + org.telegram.messenger.UserConfig.selectedAccount,
                        android.content.Context.MODE_PRIVATE);
        preferences.edit().putInt("poll_answers_max", answers).apply();
        controller.config.load(preferences);
    }

    private NativeDoHooks() {
    }
}
