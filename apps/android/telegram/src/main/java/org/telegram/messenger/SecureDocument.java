/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/**
 * NativeDo stub of org.telegram.messenger.SecureDocument.
 * Extends TLObject so it can be passed where TLObject is expected (e.g.
 * FileLoader.getAttachFileName(TLObject)).
 */
public class SecureDocument extends TLObject {
    public TLRPC.SecureFile secureFile;
    public SecureDocumentKey secureDocumentKey;
    public byte[] secret;
    public byte[] fileHash;
    public byte[] fileSecret;
    public String filePath;
}
