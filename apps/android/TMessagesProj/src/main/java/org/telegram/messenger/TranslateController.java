/* NativeDo stub of org.telegram.messenger.TranslateController. */
package org.telegram.messenger;

public class TranslateController {

    private static final TranslateController[] Instance = new TranslateController[UserConfig.MAX_ACCOUNT_COUNT];

    private TranslateController() {
    }

    public static TranslateController getInstance(int account) {
        TranslateController local = Instance[account];
        if (local == null) {
            synchronized (TranslateController.class) {
                local = Instance[account];
                if (local == null) {
                    Instance[account] = local = new TranslateController();
                }
            }
        }
        return local;
    }

    public boolean isFeatureAvailable() { return false; }
    public boolean isContextTranslateEnabled() { return false; }
    public boolean isDialogTranslatable(long dialogId) { return false; }

    /** Pin signature; translation state is not carried in this port, so nothing is translating. */
    public boolean isTranslating(MessageObject messageObject) {
        return false;
    }

    /** Pin holder for a translated poll; carried so vendored poll UI keeps the pin's field shape. */
    public static class PollText extends org.telegram.tgnet.TLObject {
        public static final int constructor = 0x24953ab8;

        public org.telegram.tgnet.TLRPC.TL_textWithEntities question;
        public java.util.ArrayList<org.telegram.tgnet.TLRPC.PollAnswer> answers = new java.util.ArrayList<>();
        public org.telegram.tgnet.TLRPC.TL_textWithEntities solution;
    }
}
