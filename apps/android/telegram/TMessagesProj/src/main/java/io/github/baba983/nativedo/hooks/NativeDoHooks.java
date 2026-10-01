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

    private NativeDoHooks() {
    }
}