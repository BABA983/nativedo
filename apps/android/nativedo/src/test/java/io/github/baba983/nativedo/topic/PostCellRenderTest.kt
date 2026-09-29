package io.github.baba983.nativedo.topic

import androidx.collection.LongSparseArray
import android.view.ViewGroup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.iv.RichHtml
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.shadows.ShadowAnimatedFileNative
import io.github.baba983.nativedo.harness.shadows.ShadowConnectionsManager
import io.github.baba983.nativedo.harness.shadows.ShadowIntro
import io.github.baba983.nativedo.harness.shadows.ShadowMediaController
import io.github.baba983.nativedo.harness.shadows.ShadowMrzRecognizer
import io.github.baba983.nativedo.harness.shadows.ShadowNativeByteBuffer
import io.github.baba983.nativedo.harness.shadows.ShadowRLottieNative
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteCursor
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteDatabase
import io.github.baba983.nativedo.harness.shadows.ShadowSQLitePreparedStatement
import io.github.baba983.nativedo.harness.shadows.ShadowUtilities

/**
 * Spike: does the *stock* upstream [ChatMessageCell] draw a linux.do post?
 *
 * The body is a cooked fixture from `fixtures/discourse/`, parsed by upstream's own HTML
 * importer (`RichHtml`, the rich-message editor's jsoup importer) into `TL_iv.PageBlock`s, and
 * handed to `TLRPC.TL_message.rich_message` the way `ThemePreviewActivity` builds a local
 * message for its preview list. Nothing upstream is patched: the only seam is the shadows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = ApplicationLoader::class,
    // Upstream's own packages are not instrumented by default; without this the natives in
    // libtmessages.49.so stay real natives and every cell dies on UnsatisfiedLinkError.
    instrumentedPackages = ["org.telegram"],
    // A phone-shaped screen, so the cells lay out at a real device width.
    qualifiers = "w411dp-h914dp-xxhdpi",
    shadows = [
        ShadowConnectionsManager::class,
        ShadowNativeByteBuffer::class,
        ShadowUtilities::class,
        ShadowMediaController::class,
        ShadowIntro::class,
        ShadowMrzRecognizer::class,
        ShadowRLottieNative::class,
        ShadowAnimatedFileNative::class,
        ShadowSQLiteDatabase::class,
        ShadowSQLiteCursor::class,
        ShadowSQLitePreparedStatement::class,
    ],
)
internal class PostCellRenderTest {

    private val harness = UpstreamUiHarness()

    @Test
    fun tableBlockquotePhoto() = snapshotPost("cooked-table-lightbox-blockquote", "post-table-blockquote-photo")

    /**
     * The hook's A/B on the same code path: constructing a ConnectionsManager with the hook
     * disabled reaches `native_init`, with the hook enabled it does not. Without this the
     * "tgnet stayed dead" checks in the render tests could pass simply because nothing ever
     * constructed a ConnectionsManager.
     */
    @Test
    fun hookDecidesWhetherTgnetInitialises() {
        harness.bootViews()
        ShadowConnectionsManager.resetCounters()

        io.github.baba983.nativedo.hooks.NativeDoHooks.setMtprotoEnabled(true)
        ConnectionsManager(1)
        val withHookOff = ShadowConnectionsManager.NATIVE_INIT_CALLS
        println("spike: hook disabled -> native_init=$withHookOff")

        io.github.baba983.nativedo.hooks.NativeDoHooks.setMtprotoEnabled(false)
        ConnectionsManager(2)
        val withHookOn = ShadowConnectionsManager.NATIVE_INIT_CALLS
        println("spike: hook enabled  -> native_init=$withHookOn (unchanged)")

        if (withHookOff != 1) throw AssertionError("the probe is blind: native_init=$withHookOff with the hook off")
        if (withHookOn != withHookOff) throw AssertionError("the hook did not stop tgnet: native_init=$withHookOn")
    }

    @Test
    fun detailsEmojiSpoiler() = snapshotPost("cooked-details-emoji-spoiler", "post-details-emoji-spoiler")

    @Test
    fun mentionLightbox() = snapshotPost("cooked-mention-lightbox", "post-mention-lightbox")

    @Test
    fun codeblockOnebox() = snapshotPost("cooked-codeblock-onebox", "post-codeblock-onebox")

    @Test
    fun topicListDialogCell() {
        harness.bootViews()
        harness.attach(topicList(), matchWidth())
        harness.render("topic-list-dialog-cell")
        reportSideEffects()
    }

    // ------------------------------------------------------------------ linux.do post -> cell

    private fun snapshotPost(fixtureName: String, snapshotName: String) {
        harness.bootViews()
        val (cell, messageObject) = richPostCell(harness.fixture("$fixtureName.html"))
        // Attach first (the parent gets its width), then bind: a real chat list does the same.
        harness.attach(cell, matchWidth())
        cell.setMessageObject(messageObject, null, false, false, false)
        harness.render(snapshotName)
        reportSideEffects()
    }

    /**
     * What the stock code touched outside the cell. tgnet and the Telegram database must stay
     * untouched; the one exception the spike found is a post carrying a custom emoji, where
     * upstream tries to fetch the emoji's sticker set over MTProto (see the printed trace).
     */
    private fun reportSideEffects() {
        println("spike: request_attempts=${ShadowConnectionsManager.REQUEST_ATTEMPTS} " +
            "native_sendRequest=${ShadowConnectionsManager.SEND_REQUEST_CALLS} " +
            "native_init=${ShadowConnectionsManager.NATIVE_INIT_CALLS} " +
            "sqlite_opendb=${ShadowSQLiteDatabase.OPEN_DB_CALLS}")
        ShadowConnectionsManager.LAST_REQUEST_CLASS?.let {
            println("spike: last request = $it")
            println("spike: request origin = ${ShadowConnectionsManager.LAST_REQUEST_TRACE}")
        }
        expectZero("tgnet initialisations", ShadowConnectionsManager.NATIVE_INIT_CALLS)
        expectZero("Telegram database opens", ShadowSQLiteDatabase.OPEN_DB_CALLS)
        expectZero("calls reaching the tgnet native layer", ShadowConnectionsManager.SEND_REQUEST_CALLS)
    }

    private fun expectZero(what: String, value: Int) {
        if (value != 0) throw AssertionError("$what: expected 0 but was $value")
    }

    private fun richPostCell(html: String): Pair<ChatMessageCell, MessageObject> {
        val body = DiscourseImageBridge.prepare(html, "https://linux.do/")
        val rows = RichHtml.parse(body.html)
        println("spike: RichHtml.parse -> ${rows.size} rows")
        rows.forEach { println("spike:   level=${it.level} num=${it.num} ${it.block.javaClass.simpleName}") }

        val richMessage = TL_iv.RichMessage().apply {
            blocks = ArrayList(rows.map { it.block })
            photos = ArrayList(body.photos)
            documents = ArrayList()
        }
        val message = TLRPC.TL_message().apply {
            id = 1
            date = (System.currentTimeMillis() / 1000).toInt() - 60
            dialog_id = 1
            flags = 257 + 8
            this.message = "linux.do post"
            media = TLRPC.TL_messageMediaEmpty()
            out = false
            from_id = TLRPC.TL_peerUser().apply { user_id = AUTHOR_ID }
            peer_id = TLRPC.TL_peerUser().apply { user_id = AUTHOR_ID }
            rich_message = richMessage
        }
        val users = HashMap<Long, TLRPC.User>()
        users[AUTHOR_ID] = TLRPC.TL_user().apply {
            id = AUTHOR_ID
            first_name = "alice"
            last_name = ""
            username = "alice"
            min = false
        }
        val messageObject = MessageObject(
            0, message, null, users, HashMap<Long, TLRPC.Chat>(),
            LongSparseArray(), LongSparseArray(),
            true, false, 0, false, false, false,
        )

        return ChatMessageCell(harness.context, 0, false, null, null).apply {
            setDelegate(object : ChatMessageCell.ChatMessageCellDelegate {})
            isChat = false
            setFullyDraw(true)
        } to messageObject
    }

    /** The topic list half of the question: upstream's own fake-dialog row. */
    private fun topicList(): ViewGroup {
        val rows = listOf(
            DialogCell.CustomDialog().apply {
                id = 1
                name = "求推荐：安卓上最好用的 RSS 阅读器"
                message = "alice: 我用的是 Feedly，同步稳定。"
                date = (System.currentTimeMillis() / 1000).toInt() - 120
                unread_count = 3
                pinned = true
            },
            DialogCell.CustomDialog().apply {
                id = 2
                name = "自建服务分享：把家里的旧笔记本变成 NAS"
                message = "bob: 已经把 Docker Compose 写好了"
                date = (System.currentTimeMillis() / 1000).toInt() - 3600
                unread_count = 0
            },
            DialogCell.CustomDialog().apply {
                id = 3
                name = "被云服务商限速了，怎么排查？"
                message = "carol: 先看 mtr 结果"
                date = (System.currentTimeMillis() / 1000).toInt() - 86400
                unread_count = 12
                muted = true
            },
        )
        val column = android.widget.FrameLayout(harness.context)
        rows.forEachIndexed { index, dialog ->
            val cell = DialogCell(null, harness.context, false, false, 0, null)
            // `isDialogCell` is the chat-list mode driven by RecyclerListView's own dialog rows;
            // a synthetic CustomDialog row takes the plain path (what setDialog(TLRPC.Dialog) does).
            cell.isDialogCell = false
            cell.setDialog(dialog)
            cell.buildLayout()
            column.addView(
                cell,
                android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(72f),
                ).apply { topMargin = index * AndroidUtilities.dp(72f) },
            )
        }
        return column
    }

    private fun matchWidth() = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private companion object {
        const val AUTHOR_ID = 12345L
    }
}