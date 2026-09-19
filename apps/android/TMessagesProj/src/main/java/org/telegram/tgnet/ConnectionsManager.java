package org.telegram.tgnet;

/**
 * Minimal ConnectionsManager stub.
 * Provides getCurrentTimeMillis() used by ImageLoader scheduling.
 */
public class ConnectionsManager {

    public static final int FileTypeFile = 0;
    public static final int FileTypePhoto = 1;
    public static final int FileTypeVideo = 2;
    public static final int FileTypeAudio = 3;

    public static final int ConnectionTypeGeneric = 1;
    public static final int ConnectionTypeDownload = 2;
    public static final int ConnectionTypeUpload = 4;

    public static final int RequestFlagFailOnServerErrors = 2;
    public static final int RequestFlagWithoutLogin = 4;
    public static final int RequestFlagEnableUnauthorized = 8;
    public static final int RequestFlagInvokeAfter = 16;
    public static final int RequestFlagNeedQuickAck = 32;
    public static final int RequestFlagTryDifferentDc = 64;
    public static final int RequestFlagForceDownload = 128;

    private static final ConnectionsManager[] Instance = new ConnectionsManager[1];

    public static ConnectionsManager getInstance(int account) {
        ConnectionsManager local = Instance[0];
        if (local == null) {
            synchronized (ConnectionsManager.class) {
                local = Instance[0];
                if (local == null) {
                    Instance[0] = local = new ConnectionsManager();
                }
            }
        }
        return local;
    }

    public static int getCurrentTime() {
        return (int) (System.currentTimeMillis() / 1000);
    }

    public static long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    public static boolean isNetworkOnline() {
        return true;
    }

    public static int generateClassGuid() {
        return (int) System.nanoTime();
    }

    /** Stub of upstream {@code ConnectionsManager.sendRequest}. */
    public int sendRequest(TLObject req, RequestDelegate completionBlock) {
        // NativeDo: Discourse polls answer messages.getPollVotes from the site's polls API.
        if (req instanceof TLRPC.TL_messages_getPollVotes) {
            final int token = generateClassGuid();
            org.telegram.messenger.nativedo.PollVotesBridge.loadVotes((TLRPC.TL_messages_getPollVotes) req,
                    result -> completionBlock.run(result, null));
            return token;
        }
        return 0;
    }

    public int sendRequest(TLObject req, RequestDelegate completionBlock, int flags) {
        return 0;
    }

    public int sendRequest(TLObject req, RequestDelegate completionBlock, QuickAckDelegate quickAckBlock, int flags) {
        return 0;
    }

    public void cancelRequest(int token, boolean notifyServer) {
    }

    public void cancelRequestsForGuid(int guid) {
    }

    public interface RequestDelegate {
        void run(TLObject response, TL_error error);
    }

    public interface QuickAckDelegate {
        void run();
    }

    public static class TL_error {
        public int code;
        public String text;
    }
}
