package org.telegram.ui.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.json.JSONObject;
import org.junit.Test;
import org.telegram.tgnet.TLRPC;

public class WebInstantViewTest {
    @Test
    public void parseJsonCreatesCachedPageBlocks() throws Exception {
        JSONObject json = new JSONObject(
                "{"
                        + "\"siteName\":\"linux.do\","
                        + "\"title\":\"NativeDo Android\","
                        + "\"byline\":\"baba\","
                        + "\"excerpt\":\"Instant View parser smoke\","
                        + "\"content\":["
                        + "\"Hello NativeDo\","
                        + "{\"type\":\"h1\",\"content\":[\"Section\"]},"
                        + "{\"type\":\"p\",\"content\":[\"Paragraph text\"]}"
                        + "]"
                        + "}"
        );

        TLRPC.TL_webPage webPage = new WebInstantView().parseJSON("https://linux.do/t/101", json);

        assertNotNull(webPage.cached_page);
        assertFalse(webPage.cached_page.blocks.isEmpty());
    }
}
