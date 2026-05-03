package io.github.baba983.nativedo.ui.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BrowserPageTest {

    @Test
    public void parsePromotesParagraphImageToPhotoBlock() throws Exception {
        BrowserPage page = BrowserPage.parse("{"
                + "\"url\":\"https://example.com/articles/1\","
                + "\"content\":[{"
                + "\"tag\":\"p\","
                + "\"content\":[{\"tag\":\"a\",\"href\":\"/full\",\"content\":[{\"tag\":\"img\",\"src\":\"/image.jpg\",\"width\":\"640\",\"height\":\"360\",\"alt\":\"cover\"}]}]"
                + "}]"
                + "}");

        assertEquals(1, page.blocks.size());
        assertTrue(page.blocks.get(0) instanceof BrowserPage.PageBlockPhoto);
        BrowserPage.PageBlockPhoto photo = (BrowserPage.PageBlockPhoto) page.blocks.get(0);
        assertEquals("https://example.com/image.jpg", photo.url);
        assertEquals(640, photo.width);
        assertEquals(360, photo.height);
    }

    @Test
    public void parseKeepsParagraphTextBeforeInlinePhotoBlock() throws Exception {
        BrowserPage page = BrowserPage.parse("{"
                + "\"url\":\"https://example.com/articles/1\","
                + "\"content\":[{"
                + "\"tag\":\"p\","
                + "\"content\":[\"Intro\",{\"tag\":\"img\",\"src\":\"image.jpg\"}]"
                + "}]"
                + "}");

        assertEquals(2, page.blocks.size());
        assertTrue(page.blocks.get(0) instanceof BrowserPage.PageBlockParagraph);
        assertTrue(page.blocks.get(1) instanceof BrowserPage.PageBlockPhoto);
        BrowserPage.PageBlockPhoto photo = (BrowserPage.PageBlockPhoto) page.blocks.get(1);
        assertEquals("https://example.com/articles/image.jpg", photo.url);
    }

    @Test
    public void parseListInlineImageAsListItemBlocks() throws Exception {
        BrowserPage page = BrowserPage.parse("{"
                + "\"url\":\"https://example.com/articles/1\","
                + "\"content\":[{\"tag\":\"ul\",\"content\":[{\"tag\":\"li\",\"content\":[\"Intro\",{\"tag\":\"img\",\"src\":\"/li.png\"}]}]}]"
                + "}");

        assertEquals(1, page.blocks.size());
        BrowserPage.PageBlockList list = (BrowserPage.PageBlockList) page.blocks.get(0);
        assertEquals(1, list.items.size());
        BrowserPage.PageListItemBlocks item = (BrowserPage.PageListItemBlocks) list.items.get(0);
        assertEquals(2, item.blocks.size());
        assertTrue(item.blocks.get(0) instanceof BrowserPage.PageBlockParagraph);
        assertTrue(item.blocks.get(1) instanceof BrowserPage.PageBlockPhoto);
    }

    @Test
    public void parseTableCellKeepsInlineImageEntity() throws Exception {
        BrowserPage page = BrowserPage.parse("{"
                + "\"url\":\"https://example.com/articles/1\","
                + "\"content\":[{\"tag\":\"table\",\"content\":[{\"tag\":\"tr\",\"content\":[{\"tag\":\"td\",\"content\":[\"Icon\",{\"tag\":\"img\",\"src\":\"/icon.png\",\"width\":\"24\",\"height\":\"12\"}]}]}]}]"
                + "}");

        BrowserPage.PageBlockTable table = (BrowserPage.PageBlockTable) page.blocks.get(0);
        BrowserPage.RichText text = table.rows.get(0).cells.get(0).text;
        assertEquals("Icon \uFFFC", text.text);
        assertEquals(1, text.entities.size());
        BrowserPage.Entity image = text.entities.get(0);
        assertEquals("image", image.type);
        assertEquals("https://example.com/icon.png", image.url);
        assertEquals(24, image.width);
        assertEquals(12, image.height);
    }

    @Test
    public void parseBlockquoteInlineImageAsPhotoBlock() throws Exception {
        BrowserPage page = BrowserPage.parse("{"
                + "\"url\":\"https://example.com/articles/1\","
                + "\"content\":[{\"tag\":\"blockquote\",\"content\":[\"Quote\",{\"tag\":\"img\",\"src\":\"/quote.png\"}]}]"
                + "}");

        assertEquals(2, page.blocks.size());
        assertTrue(page.blocks.get(0) instanceof BrowserPage.PageBlockBlockquote);
        assertTrue(page.blocks.get(1) instanceof BrowserPage.PageBlockPhoto);
    }

    @Test
    public void parseDetailsSummaryKeepsTelegramRichTextEntities() throws Exception {
        BrowserPage page = BrowserPage.parse("{"
                + "\"url\":\"https://example.com/articles/1\","
                + "\"content\":[{\"tag\":\"details\",\"content\":[{\"tag\":\"summary\",\"content\":["
                + "{\"tag\":\"a\",\"href\":\"tel:+123\",\"content\":[\"Call\"]},"
                + "\" \","
                + "{\"tag\":\"a\",\"href\":\"mailto:hi@example.com\",\"content\":[\"Mail\"]},"
                + "\" \","
                + "{\"tag\":\"span\",\"id\":\"part\",\"content\":[\"Anchor\"]}"
                + "]}]}]"
                + "}");

        BrowserPage.PageBlockDetails details = (BrowserPage.PageBlockDetails) page.blocks.get(0);
        assertEquals("Call Mail Anchor", details.title.text);
        assertEquals("phone", details.title.entities.get(0).type);
        assertEquals("tel:+123", details.title.entities.get(0).url);
        assertEquals("email", details.title.entities.get(1).type);
        assertEquals("mailto:hi@example.com", details.title.entities.get(1).url);
        assertEquals("anchor", details.title.entities.get(2).type);
        assertEquals("part", details.title.entities.get(2).url);
    }

    @Test
    public void parseDetailsWithoutSummaryKeepsBodyBlocks() throws Exception {
        BrowserPage page = BrowserPage.parse("{"
                + "\"url\":\"https://example.com/articles/1\","
                + "\"content\":[{\"tag\":\"details\",\"open\":\"\",\"content\":[{\"tag\":\"p\",\"content\":[\"Body\"]}]}]"
                + "}");

        assertEquals(1, page.blocks.size());
        BrowserPage.PageBlockDetails details = (BrowserPage.PageBlockDetails) page.blocks.get(0);
        assertTrue(details.open);
        assertEquals("", details.title.text);
        assertEquals(1, details.blocks.size());
        assertTrue(details.blocks.get(0) instanceof BrowserPage.PageBlockParagraph);
    }

    @Test
    public void parseDetailsSummaryKeepsEmptyAnchorEntity() throws Exception {
        BrowserPage page = BrowserPage.parse("{"
                + "\"url\":\"https://example.com/articles/1\","
                + "\"content\":[{\"tag\":\"details\",\"content\":["
                + "{\"tag\":\"summary\",\"content\":[{\"tag\":\"span\",\"id\":\"empty\",\"content\":[]}]},"
                + "{\"tag\":\"p\",\"content\":[\"Body\"]}"
                + "]}]"
                + "}");

        BrowserPage.PageBlockDetails details = (BrowserPage.PageBlockDetails) page.blocks.get(0);
        assertEquals("", details.title.text);
        assertEquals(1, details.title.entities.size());
        assertEquals("anchor", details.title.entities.get(0).type);
        assertEquals("empty", details.title.entities.get(0).url);
    }

    @Test
    public void parseFigureKeepsSourceAlternativesOnPhoto() throws Exception {
        BrowserPage page = BrowserPage.parse("{"
                + "\"url\":\"https://example.com/articles/1\","
                + "\"content\":[{\"tag\":\"figure\",\"content\":["
                + "{\"tag\":\"source\",\"srcset\":\"/small.jpg 1x, /big.jpg 2x\"},"
                + "{\"tag\":\"img\",\"src\":\"/image.jpg\",\"alt\":\"caption\"}"
                + "]}]"
                + "}");

        BrowserPage.PageBlockPhoto photo = (BrowserPage.PageBlockPhoto) page.blocks.get(0);
        assertEquals("https://example.com/image.jpg", photo.url);
        assertTrue(photo.urls.contains("https://example.com/image.jpg"));
        assertTrue(photo.urls.contains("https://example.com/small.jpg"));
        assertTrue(photo.urls.contains("https://example.com/big.jpg"));
        assertEquals("caption", photo.caption.text);
    }

    @Test
    public void parseLazyImageUsesRealHttpCandidate() throws Exception {
        BrowserPage page = BrowserPage.parse("{"
                + "\"url\":\"https://example.com/articles/1\","
                + "\"content\":[{\"tag\":\"img\","
                + "\"src\":\"data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==\","
                + "\"data-src\":\"/real.jpg\""
                + "}]"
                + "}");

        BrowserPage.PageBlockPhoto photo = (BrowserPage.PageBlockPhoto) page.blocks.get(0);
        assertEquals("https://example.com/real.jpg", photo.url);
        assertEquals(1, photo.urls.size());
    }

    @Test
    public void parseLazyImageKeepsDataSrcsetCandidates() throws Exception {
        BrowserPage page = BrowserPage.parse("{"
                + "\"url\":\"https://example.com/articles/1\","
                + "\"content\":[{\"tag\":\"img\","
                + "\"data-srcset\":\"/small.jpg 1x, /large.jpg 2x\""
                + "}]"
                + "}");

        BrowserPage.PageBlockPhoto photo = (BrowserPage.PageBlockPhoto) page.blocks.get(0);
        assertEquals("https://example.com/small.jpg", photo.url);
        assertTrue(photo.urls.contains("https://example.com/large.jpg"));
    }
}
