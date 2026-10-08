package io.github.baba983.nativedo.topic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionRendering
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.model.TopicDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.UserCell
import org.telegram.ui.Components.ChatScrimPopupContainerLayout
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.ReactionsContainerLayout
import org.telegram.ui.Components.RecyclerListView

/**
 * The post menu and the reactions it hosts, in upstream's own composition.
 *
 * <p>Upstream does not draw a reaction rail inside the action list: a message's menu is a
 * [ChatScrimPopupContainerLayout] with the [ReactionsContainerLayout] a sibling above the
 * `ActionBarPopupWindowLayout` that holds the ordinary rows (ChatActivity.java:32111-32175,
 * 32313-32317). The rail is then a rounded bubble floating over the dim, not a rectangle clipped by
 * the list's own frame, and its enter animation is driven by the shared scrim layout
 * ([ChatScrimPopupContainerLayout.setReactionsTransitionProgress]). This object builds that same
 * composition from the site's finite reaction list.</p>
 *
 * <p>Two things upstream does that a naive port does not. The picker is built and fitted to the
 * chat's catalog <em>before</em> the popup is measured and shown (ChatActivity.java:32147-32161),
 * so its first frame already carries every reaction. And the dim is not the popup window's own dim
 * flag - which would darken the very message that was pressed - but a layer the screen draws over
 * the page with the pressed message drawn again above it (ChatActivity.java:10678-10840,
 * 17776-17970). Both are reproduced here.</p>
 */
object DiscourseReactionUi {

    /** One ordinary row of the post menu: its icon, its label and what a tap does. */
    class Action(
        @JvmField val iconRes: Int,
        @JvmField val label: CharSequence,
        @JvmField val run: Runnable,
    )

    /** The open menu; the fragment keeps it only so a departing screen can dismiss it. */
    class Controller internal constructor(
        private val popup: ActionBarPopupWindow?,
        private val dim: MenuDimLayer?,
    ) {
        @JvmField
        var dismissed = false

        fun dismiss() {
            if (dismissed) {
                return
            }
            dismissed = true
            dim?.detach()
            val p = popup ?: return
            runCatching { if (p.isShowing) p.dismiss(true) }
        }
    }

    /**
     * Opens the post menu anchored on [scrim]: the site's reactions lead it as upstream's own rail,
     * directly swipeable, and [actions] follow as the ordinary rows.
     *
     * <p>The rail is populated and measured before the popup is shown, exactly the way upstream
     * builds its picker from the chat catalog and only then measures it, so the first frame the
     * reader sees already carries every permitted reaction. The pictures the site serves arrive
     * afterwards and only swap the chips' icons; the list is never built late.</p>
     *
     * @param bottomInsetPx the band the screen's composer occupies at the bottom of the window; the
     *     menu is constrained to the fragment's own bounds above it, not the whole display.
     */
    @JvmStatic
    @JvmOverloads
    fun showMenu(
        fragment: BaseFragment,
        container: AppContainer,
        scrim: View,
        message: MessageObject?,
        detail: TopicDetail?,
        post: Post?,
        actions: List<Action>,
        bottomInsetPx: Int = 0,
        pressX: Float = 0f,
        pressY: Float = 0f,
        onPick: (String) -> Unit,
    ): Controller {
        val activity = fragment.parentActivity ?: return Controller(null, null)
        val rp = fragment.resourceProvider
        val popupHolder = arrayOfNulls<ActionBarPopupWindow>(1)
        val dimHolder = arrayOfNulls<MenuDimLayer>(1)

        // The ordinary rows, in upstream's own popup layout.
        val popupLayout = ActionBarPopupWindow.ActionBarPopupWindowLayout(
            activity, org.telegram.messenger.R.drawable.popup_fixed_alert4, rp, 0,
        )
        // Upstream gives the message menu a floor of its own, so that short labels do not shrink it
        // (ChatActivity.java:31058: `popupLayout.setMinimumWidth(dp(200))`). It is a floor, not a
        // target: this client's labels are two to four Chinese characters, far under it. The rail's
        // own width is derived from this menu's width - ChatScrimPopupContainerLayout.onMeasure reads
        // the width of the menu's first child, the scroll view that wraps the rows - so the same
        // floor is applied to the rows as well; without it the scroll view measures to the labels
        // alone, the menu the reader sees is under the 200dp the source asks for, and the rail
        // inherits that narrowness.
        val menuMinimumWidth = AndroidUtilities.dp(200f)
        popupLayout.setMinimumWidth(menuMinimumWidth)
        for ((index, action) in actions.withIndex()) {
            val row = ActionBarMenuSubItem(activity, false, false, rp)
            row.setMinimumWidth(menuMinimumWidth)
            row.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
            if (action.iconRes != 0) {
                row.setTextAndIcon(action.label, action.iconRes)
            } else {
                row.setText(action.label)
            }
            row.setColors(
                Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, rp),
                Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, rp),
            )
            row.setSelectorColor(
                Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, rp), 0.12f),
            )
            row.updateSelectorBackground(index == 0, index == actions.size - 1, AndroidUtilities.dp(12f))
            row.setOnClickListener({
                action.run.run()
                dismissOpenMenu(popupHolder[0])
            })
            popupLayout.addView(
                row,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT),
            )
        }
        popupLayout.setFitItems(true)

        val scrimContainer = object : ChatScrimPopupContainerLayout(activity) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
                    dismissOpenMenu(popupHolder[0])
                }
                return super.dispatchKeyEvent(event)
            }

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                val handled = super.dispatchTouchEvent(ev)
                if (ev.action == MotionEvent.ACTION_DOWN && !handled) {
                    dismissOpenMenu(popupHolder[0])
                }
                return handled
            }
        }

        val allowed = detail?.validReactions?.distinct().orEmpty()
        var rail: ReactionsContainerLayout? = null
        var chat: TLRPC.TL_channelFull? = null
        if (message != null && detail != null && post != null && allowed.isNotEmpty()) {
            // The catalogue the picker filters through must hold every permitted reaction before the
            // picker is built: upstream's setMessage keeps only the reactions present in it, so a
            // missing entry is a chip that never appears. The site's own icons are fetched later;
            // here each reaction gets a drawn fallback so the list is complete on the first frame.
            for (id in allowed) {
                val emoticon = DiscourseReactions.emoticonOf(id)
                DiscourseReactionEmoji.registerFallbackBlocking(
                    emoticon, PostReactionRendering.fallbackLabel(id), id,
                )
            }
            chat = TLRPC.TL_channelFull().apply {
                available_reactions = TLRPC.TL_chatReactionsSome().apply {
                    reactions = ArrayList(allowed.map { id ->
                        TLRPC.TL_reactionEmoji().apply { emoticon = DiscourseReactions.emoticonOf(id) }
                    })
                }
            }

            rail = object : ReactionsContainerLayout(
                ReactionsContainerLayout.TYPE_DEFAULT, fragment, activity, 0, rp,
            ) {
                override fun showCustomEmojiReaction() = false
            }
            rail.forceAttachToParent = true
            // Upstream's own padding and row height (ChatActivity.java:32127, 32164): the rail is
            // one bubble's worth tall, with the extra room its shadow and hint need.
            val sPad = AndroidUtilities.dp(24f)
            rail.setPadding(
                AndroidUtilities.dp(4f) + if (LocaleController.isRTL) 0 else sPad,
                AndroidUtilities.dp(4f),
                AndroidUtilities.dp(4f) + if (LocaleController.isRTL) sPad else 0,
                AndroidUtilities.dp(22f),
            )
            scrimContainer.addView(
                rail,
                LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT,
                    (52 + rail.topOffset / AndroidUtilities.density + 22).toInt(),
                    Gravity.RIGHT, 0, 50, 0, -20,
                ),
            )
            scrimContainer.setReactionsLayout(rail)
            scrimContainer.setClipChildren(false)
        }

        scrimContainer.addView(
            popupLayout,
            LayoutHelper.createLinearRelatively(
                LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT,
                if (rail != null) 16f else 0f, 0f, if (rail != null) 36f else 0f, 0f,
            ),
        )
        scrimContainer.setPopupWindowLayout(popupLayout)

        val popup = object : ActionBarPopupWindow(
            scrimContainer, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
        ) {
            override fun dismiss(animated: Boolean) {
                super.dismiss(animated)
                rail?.dismissParent(animated)
                dimHolder[0]?.detach()
            }
        }
        popupHolder[0] = popup
        popup.setPauseNotifications(true)
        popup.setDismissAnimationDuration(220)
        popup.setOutsideTouchable(true)
        popup.setClippingEnabled(true)
        // Upstream lets the rail's own transition be the enter animation when the device can carry
        // it, and falls back to the stock popup animation otherwise (ChatActivity.java:32348-32352).
        popup.setAnimationStyle(
            if (rail != null && ReactionsContainerLayout.allowSmoothEnterTransition()) {
                0
            } else {
                org.telegram.messenger.R.style.PopupContextAnimation
            },
        )
        popup.setFocusable(true)
        popup.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED)
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        // The rail is populated before the measure that gives it its width and height, the way
        // upstream fills its layout before the popup is measured.
        if (rail != null && message != null && chat != null && post != null) {
            rail.setMessage(message, chat, false)
            post.currentUserReaction?.let {
                rail.setSelectedEmojis(arrayListOf(DiscourseReactions.emoticonOf(it)))
            }
            rail.reset()
        }

        scrimContainer.measure(
            View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), View.MeasureSpec.AT_MOST),
        )

        val loc = IntArray(2)
        scrim.getLocationInWindow(loc)
        // Upstream anchors the menu on the point inside the pressed bubble that was pressed, not on
        // the bubble's corner, and lets the finger drive both axes (ChatActivity.java:32365-32412).
        // The menu is bounded by the list the bubble belongs to and by the content view above the
        // composer: upstream reserves `measuredHeight + dp(48)` at the bottom - the composer's own
        // band - and clamps the top between `chatListView.getY() + dp(24)` and that reserve. This
        // host knows its real composer band, so it reserves the larger of the two.
        val fragmentView = fragment.fragmentView
        val contentLoc = IntArray(2)
        (fragmentView ?: scrim).getLocationInWindow(contentLoc)
        val contentTop = contentLoc[1]
        val contentHeight = (fragmentView?.height ?: 0).takeIf { it > 0 } ?: AndroidUtilities.displaySize.y
        val list = scrim.ancestorRecycler()
        val listWidth = list?.measuredWidth?.takeIf { it > 0 } ?: AndroidUtilities.displaySize.x

        // Upstream reads the menu drawable's own padding into the anchor (ChatActivity.java:32366).
        val backgroundPaddings = android.graphics.Rect()
        runCatching {
            activity.resources.getDrawable(org.telegram.messenger.R.drawable.popup_fixed_alert4)
                .mutate().getPadding(backgroundPaddings)
        }
        val width = scrimContainer.measuredWidth
        val measuredHeight = scrimContainer.measuredHeight

        // popupX = v.getLeft() + x - measuredWidth + backgroundPaddings.left - dp(28), clamped into
        // the list (ChatActivity.java:32365-32370).
        var x = loc[0] + pressX.toInt() - width + backgroundPaddings.left - AndroidUtilities.dp(28f)
        if (x < AndroidUtilities.dp(6f)) {
            x = AndroidUtilities.dp(6f)
        } else if (x > listWidth - AndroidUtilities.dp(6f) - width) {
            x = listWidth - AndroidUtilities.dp(6f) - width
        }

        // popupY = the press point, shifted up when the menu is taller than the reserve, then clamped
        // between the list's top and the reserve above the composer (ChatActivity.java:32386-32413).
        val reserve = maxOf(AndroidUtilities.dp(48f), bottomInsetPx)
        val height = measuredHeight + reserve
        val minY = contentTop + (list?.top ?: 0) + AndroidUtilities.dp(24f)
        val maxY = contentTop + contentHeight - height - AndroidUtilities.dp(8f)
        var y = loc[1] + pressY.toInt()
        if (height - backgroundPaddings.top - backgroundPaddings.bottom > AndroidUtilities.dp(240f)) {
            y += AndroidUtilities.dp(240f) - height
        }
        y = org.telegram.messenger.Utilities.clamp(y, maxY, minY)
        // The menu is capped to the space below its own top, so it never runs off the composer
        // (ChatActivity.java:32411).
        scrimContainer.setMaxHeight(maxY + height - y)

        // The dim, drawn by the screen with the pressed post redrawn above it, rather than the
        // popup window's own dim - which would darken the very message the reader pressed.
        if (fragmentView is ViewGroup) {
            val layer = MenuDimLayer(activity, scrim) { dismissOpenMenu(popup) }
            fragmentView.addView(
                layer,
                LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                    Gravity.TOP or Gravity.LEFT,
                ),
            )
            dimHolder[0] = layer
        }

        popup.showAtLocation(scrim, Gravity.LEFT or Gravity.TOP, x, y)

        if (rail != null && message != null && chat != null && post != null) {
            val r = rail
            r.setParentLayout(scrimContainer)
            // Upstream seeds the transition at zero before the popup is on screen and then runs the
            // rail's own enter animation (ChatActivity.java:32161, 32316, 32366); the scrim layout is
            // driven from the same progress through setTransitionProgress.
            r.setTransitionProgress(0f)
            r.setDelegate { _, reaction, _, _ ->
                val id = DiscourseReactions.idOf(reaction.emojicon.orEmpty())
                if (id in allowed) {
                    dismissOpenMenu(popup)
                    onPick(id)
                }
            }
            r.startEnterAnimation(true)

            // The pictures the site serves arrive after the menu is up: they only replace the chips'
            // icons. A rail that has left its window is never touched.
            val icons = allowed.map { id -> PostReaction(id, 0, detail?.emojiCatalog?.get(id)) }
            DiscourseLoader.cacheReactionEmoji(container, icons) {
                if (!r.isAttachedToWindow) {
                    return@cacheReactionEmoji Unit
                }
                r.setMessage(message, chat, false)
                post.currentUserReaction?.let {
                    r.setSelectedEmojis(arrayListOf(DiscourseReactions.emoticonOf(it)))
                }
                // The rebind above reset every chip's enter animation, and a chip already in the
                // rail's previous-frame visible set is skipped by the next draw's play pass: it
                // would stay at scale zero with its picture loaded behind it - a blank tile. The
                // same `reset()` the pre-measure pass makes clears that set so the rebound chips
                // play again on the next frame.
                r.reset()
            }
        }
        return Controller(popup, dimHolder[0])
    }

    private fun dismissOpenMenu(popup: ActionBarPopupWindow?) {
        val p = popup ?: return
        runCatching { if (p.isShowing) p.dismiss(true) }
    }

    @JvmStatic
    fun users(fragment: BaseFragment, container: AppContainer, postId: Long, reactionId: String,
              onUser: (String, String) -> Unit): BottomSheet {
        val sheet = BottomSheet(fragment.parentActivity!!, false)
        sheet.setTitle("$reactionId · 回应者")
        val body = ReactionUsersView(fragment.parentActivity!!, container, postId, reactionId, sheet) { user ->
            sheet.dismiss()
            onUser(user.username, user.displayName)
        }
        sheet.setCustomView(body)
        sheet.setOnDismissListener(Runnable { body.destroy() })
        return sheet
    }
}

/**
 * The dim the menu lays over the page, with the pressed post still lit on top of it.
 *
 * <p>Upstream does not dim with the popup window's own dim flag: the window would paint its scrim
 * over everything, pressed message included, and the bubble the reader pressed would go dark.
 * Instead the screen draws the scrim itself and then draws the pressed message again above it
 * (ChatActivity.java:10678-10840 dims the fragment, 17776-17970 redraws the scrim view lit). This is
 * that layer, hosted by this screen rather than upstream's chat: it covers the page, redraws the
 * pressed post at its live window position so it keeps its brightness, and is removed - restoring
 * the page - when the menu closes or the screen goes away.</p>
 */
internal class MenuDimLayer(context: Context, private val pressed: View, onDismiss: () -> Unit) : View(context) {

    private val paint = Paint()
    private val pressedLoc = IntArray(2)
    private val selfLoc = IntArray(2)
    private val list = pressed.ancestorRecycler()
    private val scrollDismiss = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            onDismiss()
        }
    }

    init {
        setWillNotDraw(false)
        isClickable = true
        paint.color = Color.BLACK
        setOnClickListener { onDismiss() }
        list?.addOnScrollListener(scrollDismiss)
    }

    /** Takes the layer off the screen and stops following the list; the page is itself again. */
    fun detach() {
        list?.removeOnScrollListener(scrollDismiss)
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onDraw(canvas: Canvas) {
        paint.alpha = (255 * 0.2f).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        if (pressed.isAttachedToWindow && pressed.width > 0 && pressed.height > 0) {
            pressed.getLocationInWindow(pressedLoc)
            getLocationInWindow(selfLoc)
            canvas.save()
            canvas.translate(
                (pressedLoc[0] - selfLoc[0]).toFloat(),
                (pressedLoc[1] - selfLoc[1]).toFloat(),
            )
            pressed.draw(canvas)
            canvas.restore()
        }
    }
}

private fun View.ancestorRecycler(): RecyclerView? {
    var parent = parent
    while (parent is View) {
        if (parent is RecyclerView) {
            return parent
        }
        parent = parent.parent
    }
    return null
}

private class ReactionUsersView(context: Context, private val container: AppContainer,
    private val postId: Long, private val reactionId: String, private val sheet: BottomSheet,
    onUser: (PostReactionUser) -> Unit) : FrameLayout(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val list = RecyclerListView(context)
    private val state = EmptyTextProgressView(context)
    private val note = org.telegram.ui.Cells.TextInfoPrivacyCell(context)
    private var users = emptyList<PostReactionUser>()
    private var destroyed = false
    private val avatars = HashMap<String, android.graphics.Bitmap>()
    private val adapter = object : RecyclerListView.SelectionAdapter() {
        override fun isEnabled(holder: RecyclerView.ViewHolder) = true
        override fun getItemCount() = users.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int) = RecyclerListView.Holder(
            UserCell(parent.context, 0, 0, false).apply {
                layoutParams = RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(64f))
            })
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val user = users[position]
            val peer = DiscourseAvatars.user(user.username, user.displayName, "", container.baseUrl)
            (holder.itemView as UserCell).apply {
                setData(peer, user.displayName.ifBlank { user.username }, "@${user.username}", 0)
                avatars[user.username]?.let { avatarImageView.setImageBitmap(it) }
            }
        }
    }
    init {
        minimumHeight = AndroidUtilities.dp(180f)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.setOnItemClickListener { _, position -> users.getOrNull(position)?.let(onUser) }
        addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 180f))
        addView(state, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 180f))
        note.visibility = View.GONE
        addView(note, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))
        state.setOnClickListener { load() }
        load()
    }
    fun destroy() { destroyed = true; scope.cancel() }
    private fun load() {
        if (destroyed) return
        state.visibility = View.VISIBLE
        state.showProgress()
        scope.launch {
            runCatching { container.discourseApiClient.fetchReactionUsers(postId) }
                .onSuccess { groups ->
                    if (destroyed) return@onSuccess
                    val group = groups.firstOrNull { it.id == reactionId }
                    users = group?.users.orEmpty()
                    val title = "$reactionId · ${group?.count ?: 0} 人回应"
                    sheet.setTitle(title)
                    sheet.titleView?.text = title
                    adapter.notifyDataSetChanged()
                    val height = AndroidUtilities.dp(if (users.isEmpty()) 180f else (users.size.coerceAtMost(6) * 64).toFloat())
                    list.layoutParams.height = height
                    state.layoutParams.height = height
                    note.visibility = if ((group?.count ?: 0) > users.size && users.isNotEmpty()) View.VISIBLE else View.GONE
                    note.setText("仅显示服务器返回的最近 ${users.size} 位回应者")
                    (note.layoutParams as LayoutParams).topMargin = height
                    minimumHeight = height
                    requestLayout()
                    if (users.isEmpty()) {
                        state.setText("暂无回应者")
                        state.showTextView()
                    } else {
                        state.visibility = View.GONE
                        for (user in users) scope.launch {
                            val url = DiscourseAvatars.url(user.avatarTemplate, container.baseUrl) ?: return@launch
                            val peer = DiscourseAvatars.user(user.username, user.displayName, user.avatarTemplate, container.baseUrl)
                            val file = DiscourseAvatars.cache(container.imageOkHttpClient, url, peer)
                            val bitmap = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                file?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
                            }
                            if (!destroyed && bitmap != null) {
                                avatars[user.username] = bitmap
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                }.onFailure {
                    if (!destroyed) {
                        state.setText("加载失败，点击重试")
                        state.showTextView()
                    }
                }
        }
    }
}
