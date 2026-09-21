package org.telegram.ui.iv

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_iv
import org.telegram.tgnet.tl.TL_keyboard

/**
 * The rich editor's TL wire: every concrete `PageBlock` / `RichText` / list item / table cell /
 * button / geo type the editor can build is serialized, deserialized through
 * [TL_iv.PageBlock.TLdeserialize], and compared back. The assertions are the ones the old
 * serializerless `TL_iv` could not satisfy: the block comes back non-null, as the same concrete
 * type, with the whole byte string consumed (a null string that skipped its length used to shift
 * every following field), and the re-serialized bytes are a fixed point.
 *
 * The fixture is built from valid producers only (TableModel for table flags), so the test also
 * pins the normalize contract the history path relies on: table title, captions, preformatted
 * language, math source, map geo and button text are never left null on a serializable tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IvRichSerializerContractTest {

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    private fun plain(text: String): TL_iv.textPlain = TL_iv.textPlain().apply { this.text = text }

    private fun caption(text: String): TL_iv.PageCaption = TL_iv.PageCaption().apply {
        this.text = plain(text)
        credit = TL_iv.textEmpty()
    }

    private fun serialize(block: TL_iv.PageBlock): ByteArray {
        val out = SerializedData(block.objectSize)
        block.serializeToStream(out)
        val bytes = out.toByteArray()
        out.cleanup()
        return bytes
    }

    /** serialize -> TLdeserialize -> assert the whole wire was read. */
    private fun roundTrip(block: TL_iv.PageBlock): TL_iv.PageBlock {
        val bytes = serialize(block)
        val stream = SerializedData(bytes)
        val constructor = stream.readInt32(true)
        val copy = TL_iv.PageBlock.TLdeserialize(stream, constructor, true)!!
        assertThat(stream.remaining()).isEqualTo(0)
        stream.cleanup()
        return copy
    }

    /** A block survives as itself, keeps its fields, and re-serializes to the same bytes. */
    private fun assertRoundTrips(block: TL_iv.PageBlock): TL_iv.PageBlock {
        val copy = roundTrip(block)
        assertThat(copy.javaClass).isEqualTo(block.javaClass)
        assertThat(serialize(copy)).isEqualTo(serialize(block))
        return copy
    }

    // ---------- RichText ----------

    /** One instance of each of the pin's 31 concrete rich-text types, nested where the wire nests. */
    private fun everyRichText(): TL_iv.RichText = TL_iv.textConcat().apply {
        texts = arrayListOf(
            TL_iv.textEmpty(),
            plain("plain"),
            TL_iv.textBold().apply { text = plain("bold") },
            TL_iv.textItalic().apply { text = plain("italic") },
            TL_iv.textUnderline().apply { text = plain("underline") },
            TL_iv.textStrike().apply { text = plain("strike") },
            TL_iv.textFixed().apply { text = plain("fixed") },
            TL_iv.textUrl().apply { text = plain("link"); url = "https://linux.do/x"; webpage_id = 7L },
            TL_iv.textEmail().apply { text = plain("mail"); email = "a@b.c" },
            TL_iv.textConcat().apply { texts = arrayListOf(plain("nested"), plain("concat")) },
            TL_iv.textSubscript().apply { text = plain("sub") },
            TL_iv.textSuperscript().apply { text = plain("super") },
            TL_iv.textMarked().apply { text = plain("marked") },
            TL_iv.textPhone().apply { text = plain("phone"); phone = "+1555" },
            TL_iv.textImage().apply { document_id = 5L; photo_id = 6L; w = 10; h = 20 },
            TL_iv.textAnchor().apply { text = plain("anchor"); name = "section-1" },
            TL_iv.textMath().apply { source = "x^2"; w = 1; h = 2; depth = 3 },
            TL_iv.textCustomEmoji().apply { document_id = 9L; alt = "😀" },
            TL_iv.textSpoiler().apply { text = plain("spoiler") },
            TL_iv.textMention().apply { text = plain("@user") },
            TL_iv.textHashtag().apply { text = plain("#topic") },
            TL_iv.textBotCommand().apply { text = plain("/start") },
            TL_iv.textCashtag().apply { text = plain("\$AAPL") },
            TL_iv.textAutoUrl().apply { text = plain("linux.do") },
            TL_iv.textAutoEmail().apply { text = plain("auto@linux.do") },
            TL_iv.textAutoPhone().apply { text = plain("+1556") },
            TL_iv.textBankCard().apply { text = plain("4111 1111") },
            TL_iv.textMentionName().apply { text = plain("Named"); user_id = 42L },
            TL_iv.textDate().apply {
                text = plain("today"); date = 1_700_000_000
                relative = true; short_date = true; short_time = true
            },
            TL_iv.textDiff().apply { text = plain("new"); old_text = plain("old") },
            TL_iv.textButton().apply {
                text = plain("Open")
                type = TL_keyboard.TL_inlineButtonTypeUrl().apply { url = "https://linux.do/go" }
                style = TL_keyboard.RichButtonStyle().apply { bg_primary = true; link = true }
            },
        )
    }

    @Test
    fun everyConcreteRichTextTypeRoundTripsInOrder() {
        val paragraph = TL_iv.pageBlockParagraph().apply { text = everyRichText() }

        val copy = assertRoundTrips(paragraph) as TL_iv.pageBlockParagraph
        val concat = copy.text as TL_iv.textConcat
        assertThat(concat.texts.map { it.javaClass.simpleName }).containsExactly(
            "textEmpty", "textPlain", "textBold", "textItalic", "textUnderline", "textStrike",
            "textFixed", "textUrl", "textEmail", "textConcat", "textSubscript", "textSuperscript",
            "textMarked", "textPhone", "textImage", "textAnchor", "textMath", "textCustomEmoji",
            "textSpoiler", "textMention", "textHashtag", "textBotCommand", "textCashtag",
            "textAutoUrl", "textAutoEmail", "textAutoPhone", "textBankCard", "textMentionName",
            "textDate", "textDiff", "textButton",
        ).inOrder()

        val url = concat.texts[7] as TL_iv.textUrl
        assertThat(url.url).isEqualTo("https://linux.do/x")
        assertThat(url.webpage_id).isEqualTo(7L)
        val date = concat.texts[28] as TL_iv.textDate
        assertThat(date.relative).isTrue()
        assertThat(date.short_time).isTrue()
        assertThat(date.date).isEqualTo(1_700_000_000)
        val button = concat.texts[30] as TL_iv.textButton
        assertThat((button.type as TL_keyboard.TL_inlineButtonTypeUrl).url).isEqualTo("https://linux.do/go")
        assertThat(button.style!!.bg_primary).isTrue()
        assertThat(button.style!!.link).isTrue()
        assertThat(button.getText()).isEqualTo("Open")
    }

    @Test
    fun textFlagsAndNestingSurviveTheWire() {
        val bold = TL_iv.textBold().apply { text = TL_iv.textItalic().apply { text = plain("deep") } }
        val copy = assertRoundTrips(TL_iv.pageBlockParagraph().apply { text = bold }) as TL_iv.pageBlockParagraph
        val restoredBold = copy.text as TL_iv.textBold
        val restoredItalic = restoredBold.text as TL_iv.textItalic
        assertThat((restoredItalic.text as TL_iv.textPlain).text).isEqualTo("deep")
    }

    // ---------- PageBlock / list / table / button / geo ----------

    private fun everyEditorReachableBlock(): List<TL_iv.PageBlock> {
        val table = TL_iv.pageBlockTable().apply {
            bordered = true
            striped = true
            compact = true
            title = plain("Table")
            flags = TLObject.FLAG_0 or TLObject.FLAG_1 or TLObject.FLAG_2
            rows = arrayListOf(TL_iv.pageTableRow().apply {
                cells = arrayListOf(
                    TL_iv.pageTableCell().apply {
                        TableModel.applyPlainText(this, "cell")
                        TableModel.setHeader(this, true)
                        TableModel.setAlign(this, TableModel.ALIGN_CENTER)
                        TableModel.setVAlign(this, TableModel.VALIGN_MIDDLE)
                    },
                    TL_iv.pageTableCell().apply {
                        TableModel.applyPlainText(this, "spanned")
                        colspan = 2
                    },
                )
            })
        }
        // spans/rows go through the producer normalize, never by hand-written flags
        TableModel.normalizeForSend(table)

        val content = TL_iv.pageBlockParagraph().apply { text = plain("Hello ") }

        return arrayListOf(
            TL_iv.pageBlockUnsupported(),
            TL_iv.pageBlockTitle().apply { text = plain("Title") },
            TL_iv.pageBlockSubtitle().apply { text = plain("Subtitle") },
            TL_iv.pageBlockAuthorDate().apply { author = plain("Author"); published_date = 1_700_000_001 },
            TL_iv.pageBlockHeader().apply { text = plain("Header") },
            TL_iv.pageBlockSubheader().apply { text = plain("Subheader") },
            TL_iv.pageBlockHeading1().apply { text = plain("H1") },
            TL_iv.pageBlockHeading2().apply { text = plain("H2") },
            TL_iv.pageBlockHeading3().apply { text = plain("H3") },
            TL_iv.pageBlockHeading4().apply { text = plain("H4") },
            TL_iv.pageBlockHeading5().apply { text = plain("H5") },
            TL_iv.pageBlockHeading6().apply { text = plain("H6") },
            TL_iv.pageBlockKicker().apply { text = plain("Kicker") },
            content,
            TL_iv.pageBlockPreformatted().apply { text = plain("code"); language = "kotlin" },
            TL_iv.pageBlockFooter().apply { text = plain("Footer") },
            TL_iv.pageBlockDivider(),
            TL_iv.pageBlockAnchor().apply { name = "anchor" },
            TL_iv.pageBlockBlockquote().apply {
                text = plain("quote"); caption = plain("author"); collapsed = true
            },
            TL_iv.pageBlockBlockquoteBlocks().apply {
                blocks = arrayListOf(content); caption = plain("author")
            },
            TL_iv.pageBlockPullquote().apply { text = plain("pull"); caption = plain("author") },
            TL_iv.pageBlockCover().apply { cover = content },
            TL_iv.pageBlockEmbed().apply {
                full_width = true
                allow_scrolling = true
                url = "https://linux.do/embed"
                html = "<b>embed</b>"
                poster_photo_id = 3L
                w = 640
                h = 480
                flags = TLObject.FLAG_1 or TLObject.FLAG_2 or TLObject.FLAG_4 or TLObject.FLAG_5
                caption = caption("embed caption")
            },
            TL_iv.pageBlockEmbedPost().apply {
                url = "https://linux.do/post/1"
                webpage_id = 11L
                author_photo_id = 12L
                author = "Author"
                date = 1_700_000_002
                blocks = arrayListOf(content)
                caption = caption("post caption")
            },
            TL_iv.pageBlockCollage().apply {
                items = arrayListOf(TL_iv.pageBlockPhoto().apply { photo_id = 21L; caption = caption("one") })
                caption = caption("collage")
            },
            TL_iv.pageBlockSlideshow().apply {
                items = arrayListOf(TL_iv.pageBlockPhoto().apply { photo_id = 22L; caption = caption("two") })
                caption = caption("slideshow")
            },
            TL_iv.pageBlockAudio().apply { audio_id = 31L; caption = caption("audio") },
            TL_iv.pageBlockPhoto().apply {
                photo_id = 41L
                spoiler = true
                url = "https://linux.do/uploads/1.png"
                webpage_id = 42L
                flags = TLObject.FLAG_0
                caption = caption("photo")
            },
            TL_iv.pageBlockVideo().apply {
                video_id = 51L
                autoplay = true
                loop = true
                spoiler = true
                caption = caption("video")
            },
            TL_iv.pageBlockDocument().apply { document_id = 61L; caption = caption("document") },
            TL_iv.pageBlockMap().apply {
                geo = TLRPC.TL_geoPoint().apply {
                    flags = TLObject.FLAG_0
                    _long = 12.5
                    lat = 41.9
                    access_hash = 71L
                    accuracy_radius = 5
                }
                zoom = 15
                w = 800
                h = 600
                caption = caption("map")
            },
            TL_iv.pageBlockMath().apply { source = "\\frac{1}{2}" },
            TL_iv.pageBlockThinking().apply { text = plain("thinking") },
            TL_iv.pageBlockDetails().apply {
                open = true
                title = plain("Details")
                blocks = arrayListOf(content)
            },
            table,
            TL_iv.pageBlockRelatedArticles().apply {
                title = plain("Related")
                articles = arrayListOf(TL_iv.pageRelatedArticle().apply {
                    flags = TLObject.FLAG_0 or TLObject.FLAG_1 or TLObject.FLAG_2 or TLObject.FLAG_3 or TLObject.FLAG_4
                    url = "https://linux.do/a"
                    webpage_id = 81L
                    title = "A"
                    description = "desc"
                    photo_id = 82L
                    author = "A. Author"
                    published_date = 1_700_000_003
                })
            },
            TL_iv.pageBlockList().apply {
                items = arrayListOf(
                    TL_iv.TL_pageListItemText().apply {
                        text = plain("item"); checkbox = true; checked = true
                    },
                    TL_iv.TL_pageListItemBlocks().apply {
                        blocks = arrayListOf(content); checkbox = true; checked = false
                    },
                )
            },
            TL_iv.pageBlockOrderedList().apply {
                reversed = true
                start = 3
                type = "a"
                flags = TLObject.FLAG_0 or TLObject.FLAG_1
                items = arrayListOf(
                    TL_iv.TL_pageListOrderedItemText().apply {
                        text = plain("first"); num = "3"; checkbox = true; checked = true
                    },
                    TL_iv.TL_pageListOrderedItemBlocks().apply {
                        blocks = arrayListOf(content); num = "4"; type = "a"
                    },
                )
            },
            TL_iv.pageBlockButtonRow().apply {
                align_center = true
                buttons = arrayListOf(
                    TL_keyboard.PageButton().apply {
                        text = plain("Open")
                        type = TL_keyboard.TL_inlineButtonTypeUrl().apply { url = "https://linux.do/go" }
                        style = TL_keyboard.RichButtonStyle().apply { bg_primary = true }
                    },
                    TL_keyboard.PageButton().apply {
                        text = plain("Copy")
                        type = TL_keyboard.TL_inlineButtonTypeCopy().apply { copy_text = "copied" }
                    },
                    TL_keyboard.PageButton().apply {
                        text = plain("Profile")
                        type = TL_keyboard.TL_inlineButtonTypeUserProfile().apply { user_id = 91L }
                    },
                )
            },
        )
    }

    @Test
    fun everyEditorReachableBlockTypeRoundTrips() {
        val blocks = everyEditorReachableBlock()
        for (block in blocks) {
            val copy = assertRoundTrips(block)
            assertThat(copy.javaClass.simpleName).isEqualTo(block.javaClass.simpleName)
        }
        assertThat(blocks.map { it.javaClass.simpleName }).hasSize(40 - 1) // every type but pageBlockChannel
    }

    @Test
    fun concreteTypesAndStructureComeBackNotTheFallbackParagraph() {
        val heading = TL_iv.pageBlockHeading3().apply { text = plain("Heading") }
        val restoredHeading = roundTrip(heading)
        assertThat(restoredHeading).isInstanceOf(TL_iv.pageBlockHeading3::class.java)
        assertThat(RichTextStyle.plainOf(restoredHeading.text)).isEqualTo("Heading")

        val details = TL_iv.pageBlockDetails().apply {
            open = true
            title = plain("Summary")
            blocks = arrayListOf(TL_iv.pageBlockPreformatted().apply { text = plain("nested"); language = "java" })
        }
        val restoredDetails = roundTrip(details) as TL_iv.pageBlockDetails
        assertThat(restoredDetails.open).isTrue()
        assertThat(RichTextStyle.plainOf(restoredDetails.title)).isEqualTo("Summary")
        assertThat(restoredDetails.blocks).hasSize(1)
        val nested = restoredDetails.blocks[0] as TL_iv.pageBlockPreformatted
        assertThat(nested.language).isEqualTo("java")
    }

    @Test
    fun tableCellMetadataSurvivesTheWire() {
        val table = everyEditorReachableBlock().filterIsInstance<TL_iv.pageBlockTable>().single()
        val restored = roundTrip(table) as TL_iv.pageBlockTable

        assertThat(restored.bordered).isTrue()
        assertThat(restored.striped).isTrue()
        assertThat(restored.compact).isTrue()
        assertThat(RichTextStyle.plainOf(restored.title)).isEqualTo("Table")
        assertThat(restored.rows).hasSize(1)
        val cells = restored.rows[0].cells
        assertThat(cells).hasSize(2)

        assertThat(TableModel.readPlainText(cells[0])).isEqualTo("cell")
        assertThat(cells[0].header).isTrue()
        assertThat(TableModel.alignOf(cells[0])).isEqualTo(TableModel.ALIGN_CENTER)
        assertThat(TableModel.valignOf(cells[0])).isEqualTo(TableModel.VALIGN_MIDDLE)

        assertThat(TableModel.readPlainText(cells[1])).isEqualTo("spanned")
        assertThat(TableModel.spanCol(cells[1])).isEqualTo(2)
        assertThat(TableModel.spanRow(cells[1])).isEqualTo(1)
    }

    @Test
    fun buttonTypesAndStylesRoundTrip() {
        val row = everyEditorReachableBlock().filterIsInstance<TL_iv.pageBlockButtonRow>().single()
        val restored = roundTrip(row) as TL_iv.pageBlockButtonRow

        assertThat(restored.align_center).isTrue()
        assertThat(restored.buttons).hasSize(3)

        val urlButton = restored.buttons[0]
        assertThat(urlButton.text.javaClass).isEqualTo(TL_iv.textPlain::class.java)
        assertThat(urlButton.getText()).isEqualTo("Open")
        assertThat((urlButton.type as TL_keyboard.TL_inlineButtonTypeUrl).url).isEqualTo("https://linux.do/go")
        assertThat(urlButton.style!!.bg_primary).isTrue()

        val copyButton = restored.buttons[1]
        assertThat((copyButton.type as TL_keyboard.TL_inlineButtonTypeCopy).copy_text).isEqualTo("copied")
        assertThat(copyButton.style).isNull()

        val profileButton = restored.buttons[2]
        assertThat((profileButton.type as TL_keyboard.TL_inlineButtonTypeUserProfile).user_id).isEqualTo(91L)
    }

    @Test
    fun mediaBlocksKeepTheirFabricatedIds() {
        // The host registers a URL-only media block's id with RichMediaUploadBridge at load time;
        // the wire only has to keep the id, not the URL.
        val photo = TL_iv.pageBlockPhoto().apply { photo_id = 41L; caption = caption("p") }
        assertThat((roundTrip(photo) as TL_iv.pageBlockPhoto).photo_id).isEqualTo(41L)

        val video = TL_iv.pageBlockVideo().apply { video_id = 51L; autoplay = true; caption = caption("v") }
        val videoCopy = roundTrip(video) as TL_iv.pageBlockVideo
        assertThat(videoCopy.video_id).isEqualTo(51L)
        assertThat(videoCopy.autoplay).isTrue()

        val audio = TL_iv.pageBlockAudio().apply { audio_id = 61L; caption = caption("a") }
        assertThat((roundTrip(audio) as TL_iv.pageBlockAudio).audio_id).isEqualTo(61L)

        val document = TL_iv.pageBlockDocument().apply { document_id = 71L; caption = caption("d") }
        assertThat((roundTrip(document) as TL_iv.pageBlockDocument).document_id).isEqualTo(71L)
    }

    @Test
    fun geoPointFamilyRoundTrips() {
        val map = everyEditorReachableBlock().filterIsInstance<TL_iv.pageBlockMap>().single()
        val restored = roundTrip(map) as TL_iv.pageBlockMap

        assertThat(restored.geo).isInstanceOf(TLRPC.TL_geoPoint::class.java)
        val geo = restored.geo as TLRPC.TL_geoPoint
        assertThat(geo.lat).isEqualTo(41.9)
        assertThat(geo._long).isEqualTo(12.5)
        assertThat(geo.access_hash).isEqualTo(71L)
        assertThat(geo.accuracy_radius).isEqualTo(5)
        assertThat(restored.zoom).isEqualTo(15)
        assertThat(restored.w).isEqualTo(800)
        assertThat(restored.h).isEqualTo(600)
    }

    @Test
    fun theEmptyGeoPointIsAWireTypeToo() {
        val empty = TL_iv.pageBlockMap().apply {
            geo = TLRPC.TL_geoPointEmpty()
            caption = caption("no pin")
        }
        val restored = roundTrip(empty) as TL_iv.pageBlockMap
        assertThat(restored.geo).isInstanceOf(TLRPC.TL_geoPointEmpty::class.java)
    }

    /** The dispatch closure ends at the types this tree carries: a legacy magic answers null. */
    @Test
    fun anUncarriedLegacyConstructorAnswersNull() {
        val out = SerializedData(4)
        out.writeInt32(0x3a58c7f4) // pin pageBlockList_layer82
        val stream = SerializedData(out.toByteArray())
        out.cleanup()

        val block = TL_iv.PageBlock.TLdeserialize(stream, stream.readInt32(false), false)
        assertThat(block).isNull()
        stream.cleanup()
    }
}
