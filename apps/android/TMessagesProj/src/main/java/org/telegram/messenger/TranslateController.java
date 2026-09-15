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
}
