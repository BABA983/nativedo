package org.telegram.messenger;

import java.util.ArrayList;

public class MessagesStorage {
    private static final MessagesStorage[] Instance = new MessagesStorage[UserConfig.MAX_ACCOUNT_COUNT];

    public static MessagesStorage getInstance(int account) {
        if (Instance[account] == null) {
            Instance[account] = new MessagesStorage();
        }
        return Instance[account];
    }

    public void putUsersAndChats(ArrayList<?> users, ArrayList<?> chats, boolean withTransaction, boolean useQueue) {}
    public void putMessages(Object messages, long dialogId, int loadType, int maxId, boolean createDialog, int scheduled, int mode) {}
    public void putWebPages(ArrayList<?> webpages) {}
    public void putWebPages(androidx.collection.LongSparseArray<?> webpages) {}
    public void updateDialogsWithDeletedMessages(long dialogId, long channelId, ArrayList<?> messages, Object task, boolean loadDialogs) {}
}
