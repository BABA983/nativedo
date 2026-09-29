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

    private NativeDoHooks() {
    }
}