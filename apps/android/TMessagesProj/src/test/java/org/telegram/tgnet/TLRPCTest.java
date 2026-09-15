package org.telegram.tgnet;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class TLRPCTest {
    @Test
    public void constructsInstantViewWebPage() {
        TLRPC.TL_webPage webPage = new TLRPC.TL_webPage();
        webPage.cached_page = new TLRPC.TL_page();

        assertNotNull(webPage.cached_page);
    }
}
