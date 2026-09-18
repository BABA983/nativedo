package org.telegram.tgnet;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.telegram.tgnet.tl.TL_iv;

public class TLRPCTest {
    @Test
    public void constructsInstantViewWebPage() {
        TLRPC.TL_webPage webPage = new TLRPC.TL_webPage();
        webPage.cached_page = new TL_iv.TL_page();

        assertNotNull(webPage.cached_page);
    }
}
