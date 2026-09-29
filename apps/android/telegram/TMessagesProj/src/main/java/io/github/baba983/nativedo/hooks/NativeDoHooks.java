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

    private NativeDoHooks() {
    }
}