// SPDX-License-Identifier: GPL-2.0-or-later
// Built from upstream's own input components (RecyclerListView + GridLayoutManager,
// ScrollSlidingTextTabStrip, SearchField, EmptyTextProgressView, BackupImageView).

package io.github.baba983.nativedo.topic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import io.github.baba983.nativedo.core.model.DiscourseEmoji
import io.github.baba983.nativedo.core.model.DiscourseEmojiCategory
import io.github.baba983.nativedo.core.network.EmojiGroupsDto
import io.github.baba983.nativedo.core.network.toEmojiCategories
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ScrollSlidingTextTabStrip
import org.telegram.ui.Components.SearchField
import org.telegram.ui.Components.inset.InAppKeyboardInsetView
import org.telegram.ui.Components.inset.WindowInsetsInAppController
import org.telegram.ui.Components.inset.WindowInsetsProvider

/**
 * The linux.do emoji picker: the site's own catalog, drawn with upstream's own input components.
 *
 * <p>The reader expects the emoji panel the chat composer opens - a grid, category tabs over it and
 * a search field - but what it offers is linux.do's catalog, not Telegram's. Telegram's own
 * `EmojiView` cannot be driven by a site table: its tabs are the fixed `EmojiTabsStrip` icons and its
 * adapter reads `MediaDataController`'s emoji packs and animated-emoji documents, which this client
 * never fills (tgnet is off). So the panel here is assembled from the same pieces `EmojiView` uses
 * for its own lists - upstream [RecyclerListView] over a [GridLayoutManager] for the grid,
 * [ScrollSlidingTextTabStrip] for the tabs, [SearchField] for the search box,
 * [EmptyTextProgressView] for the loading/error/empty states and [BackupImageView] for each tile -
 * and is pointed at the site's catalog instead.</p>
 *
 * <p>Selection inserts the exact site shortcode `:name:` at the caret (replacing a selection) into
 * the composer's field, and nothing else: no Telegram document, no animated-emoji span, no reaction
 * table. The catalog itself is the one the app already keeps ([AppContainer.emojiCatalogStore]),
 * read on the new-topic screen through [io.github.baba983.nativedo.core.network.DiscourseApiClient.fetchEmojiCatalog];
 * a warm table answers without a request. A tile's picture is fetched through the same
 * session-authenticated image cache the reaction chips use ([DiscourseReactionEmoji]); a picture
 * that will not arrive leaves the tile's readable name in its place instead of a blank square.</p>
 *
 * <h3>Hosting (owned by the caller)</h3>
 *
 * <p>The panel does not steal the composer's emoji button: the screen that owns the composer decides
 * when the panel opens, adds this view to `ChatInputViewsContainer.getInAppKeyboardBubbleContainer()`
 * and hands the same `WindowInsetsStateHolder` over with [setInsetsController]. [show] reports the
 * panel's height to the holder - which is what lifts the in-app keyboard bubble - and [dismiss]
 * releases it. The caller then forwards the screen's back press to [onBackPressed], and its
 * pause/destroy lifecycle to [destroy]. The field to write into is given with [setInsertionTarget];
 * [setOnEmojiSelected] observes every selection as well.</p>
 */
class DiscourseEmojiPicker @JvmOverloads constructor(
    context: Context,
    private val container: AppContainer,
    private val resourcesProvider: Theme.ResourcesProvider? = null,
) : FrameLayout(context), InAppKeyboardInsetView {

    /** Told about every selected emoji, by the site's own name (without the colons). */
    fun interface OnEmojiSelected {
        fun onEmojiSelected(name: String)
    }

    private enum class State { LOADING, LOADED, EMPTY, ERROR }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val imageClient = container.imageOkHttpClient

    /**
     * The dispatcher a tile's `BitmapFactory.decodeFile` runs on. Production decodes on
     * [Dispatchers.IO]; a test swaps it for a dispatcher it can hold open, so a decode that is still
     * off the main thread when [destroy] runs can be driven deterministically. It is a seam for the
     * late-decode guard, not a second code path: production never sets it.
     */
    internal var imageDecodeDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * The tile's picture read: the cache lookup and, on a miss, the site fetch. Production goes to
     * the site through [DiscourseReactionEmoji.cache]; a test swaps in a read that returns without
     * suspending, so the read's own timing - and the completion it starts - can be observed
     * deterministically instead of by racing the disk. A seam for the inline-completion guard, not a
     * second code path: production never sets it.
     */
    internal var imageFetcher: suspend (String) -> File? =
        { url -> DiscourseReactionEmoji.cache(imageClient, url) }

    /** Decoded tiles, keyed by the site's URL; small pictures, kept for the panel's life. */
    private val bitmaps = HashMap<String, Bitmap>()

    /** URLs whose bytes could not be produced: the tile keeps its name instead of retrying. */
    private val failedImages = HashSet<String>()
    private val pendingImages = HashSet<String>()

    private var categories: List<DiscourseEmojiCategory> = emptyList()
    private var state = State.LOADING
    private var generation = 0
    private var destroyed = false
    private var catalogJob: Job? = null

    private var insertionTarget: EditTextBoldCursor? = null
    private var onEmojiSelected: OnEmojiSelected? = null
    private var onDismissListener: Runnable? = null
    private var insetsController: WindowInsetsInAppController? = null

    /** The navigation-bar inset the host last reported; it pads the panel's bottom by this much. */
    private var navigationBarHeight = 0

    /** True while the search field holds focus, i.e. the system IME is up over the panel. */
    private var searching = false

    /**
     * The expanded in-app height the search state last asked the host for, in pixels; 0 means the
     * panel is at its own height. Kept so a layout pass that re-reports the same IME does not ask
     * again.
     */
    private var expandedHeightPx = 0

    private val search: SearchField
    private val tabs: ScrollSlidingTextTabStrip
    private val grid: RecyclerListView
    private val empty: EmptyTextProgressView
    private val adapter: GridAdapter

    init {
        setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground, resourcesProvider))

        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL

        search = object : SearchField(context, true, resourcesProvider) {
            override fun onTextChange(text: String) {
                applySearch(text)
            }
        }
        search.setHint(SEARCH_HINT)
        // The search box is a real field, so focusing it opens the system IME over the panel's
        // normal place; the panel grows above it while the field holds focus (see
        // applyInAppKeyboardAnimatedHeight) and drops back when focus leaves.
        search.getSearchEditText()?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                searching = true
                expandForSearch()
            } else {
                searching = false
                if (isOpen()) {
                    collapseToPanel()
                }
            }
        }
        column.addView(search, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, HEADER_HEIGHT_DP))

        tabs = ScrollSlidingTextTabStrip(context, resourcesProvider)
        tabs.setColors(
            Theme.key_chat_emojiPanelStickerPackSelectorLine,
            Theme.key_chat_attachActiveTab,
            Theme.key_chat_attachUnactiveTab,
            Theme.key_listSelector,
        )
        tabs.setDelegate(object : ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate {
            override fun onPageSelected(page: Int, forward: Boolean) {
                showCategoryAt(page)
            }

            override fun onPageScrolled(progress: Float) {
            }
        })
        column.addView(tabs, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, TABS_HEIGHT_DP))

        val gridHolder = FrameLayout(context)

        adapter = GridAdapter()

        grid = RecyclerListView(context)
        grid.layoutManager = GridLayoutManager(context, COLUMNS)
        grid.adapter = adapter
        grid.isVerticalScrollBarEnabled = false
        grid.setOnItemClickListener(object : RecyclerListView.OnItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                selectAt(position)
            }
        })
        gridHolder.addView(grid, LayoutHelper.createFrameMatchParent())

        empty = EmptyTextProgressView(context, null, resourcesProvider)
        empty.setOnTouchListener { _: View, event: MotionEvent ->
            if (event.actionMasked == MotionEvent.ACTION_UP && state == State.ERROR) {
                loadCatalog(force = true)
            }
            true
        }
        gridHolder.addView(empty, LayoutHelper.createFrameMatchParent())

        column.addView(gridHolder, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))
        addView(column, LayoutHelper.createFrameMatchParent())

        // Nothing is loaded until the panel is opened; an unopened picker makes no request.
        visibility = GONE
        state = State.LOADING
    }

    // ------------------------------------------------------------------ host API

    /** The field the selected `:name:` is inserted into, at the caret, keeping the text around it. */
    fun setInsertionTarget(field: EditTextBoldCursor?) {
        insertionTarget = field
    }

    /** Told every time the reader chooses an emoji, in addition to the field insertion. */
    fun setOnEmojiSelected(listener: OnEmojiSelected?) {
        onEmojiSelected = listener
    }

    /** Called when the reader dismisses the panel (a tap outside, a back press). */
    fun setOnDismissListener(listener: Runnable?) {
        onDismissListener = listener
    }

    /**
     * The holder that lifts the host's in-app keyboard bubble. [show] reports the panel's height to
     * it and [dismiss] releases it; without one the panel still works, it just does not move the host.
     */
    fun setInsetsController(controller: WindowInsetsInAppController?) {
        insetsController = controller
    }

    /**
     * Upstream's host (`ChatInputViewsContainer`) hands every child in the in-app keyboard bubble the
     * window's actual navigation-bar inset through this interface. The panel pads its own bottom by
     * it, so the last grid row is not drawn under the system bar; without this the bubble's child
     * would be measured under the bar and its lowest tiles would be unreachable.
     */
    override fun applyNavigationBarHeight(height: Int) {
        if (navigationBarHeight == height) {
            return
        }
        navigationBarHeight = height
        setPadding(paddingLeft, paddingTop, paddingRight, height)
        requestLayout()
    }

    /**
     * The system IME's animated inset, also forwarded by the host (as `max(IME, in-app)`). This
     * panel is the in-app keyboard itself, so when nothing is focused there is nothing to move - the
     * nav inset above is what pads it. While the search field holds focus, though, the real IME is
     * over the panel's place, so the panel follows it: it grows above the keyboard, and it drops
     * back to its own height when the keyboard goes away. Upstream's own emoji view does the same -
     * `ChatActivityEnterView.onSearchOpenClose` expands it and
     * `setStickersExpanded` re-requests the in-app height for it.
     */
    override fun applyInAppKeyboardAnimatedHeight(height: Float) {
        if (destroyed || !searching) {
            return
        }
        if (imeBottomInsetPx() > 0) {
            expandForSearch()
        } else {
            collapseToPanel()
        }
    }

    /**
     * Grows the in-app height to the IME plus the panel's own height, so the field and the results
     * ride in the band between the capsule and the keyboard instead of behind it. Reads the IME's
     * raw inset, not the host's `max(IME, in-app)` report, so a re-request for the same keyboard is
     * a no-op rather than a height that keeps climbing.
     */
    private fun expandForSearch() {
        val ime = imeBottomInsetPx()
        if (ime <= 0) {
            return
        }
        val expanded = ime + panelHeightPx()
        if (expandedHeightPx == expanded) {
            return
        }
        expandedHeightPx = expanded
        insetsController?.requestInAppKeyboardHeightIncludeNavbar(expanded)
    }

    /** Returns the panel to its own height, e.g. when the search field loses focus. */
    private fun collapseToPanel() {
        if (expandedHeightPx == 0) {
            return
        }
        expandedHeightPx = 0
        if (isOpen()) {
            insetsController?.requestInAppKeyboardHeightIncludeNavbar(panelHeightPx())
        }
    }

    /**
     * The system IME's own bottom inset, read from the holder the host handed over; 0 when there is
     * none. The holder is the object the host passes as [WindowInsetsInAppController]; it also
     * answers [WindowInsetsProvider], which is where the raw inset lives.
     */
    private fun imeBottomInsetPx(): Int =
        (insetsController as? WindowInsetsProvider)
            ?.getInsets(WindowInsetsCompat.Type.ime())
            ?.bottom ?: 0

    /** Opens the panel: reads the catalog (if the kept table is empty) and reports its height. */
    fun show() {
        if (destroyed) {
            return
        }
        generation++
        searching = false
        expandedHeightPx = 0
        visibility = VISIBLE
        insertionTarget?.let { AndroidUtilities.hideKeyboard(it) }
        insetsController?.requestInAppKeyboardHeightIncludeNavbar(panelHeightPx())
        loadCatalog(force = false)
    }

    /**
     * Closes the panel. Children of the host stay where they are; an in-flight catalog read is left
     * to finish (it warms the store) but its answer is ignored, so a late response cannot change a
     * closed panel.
     *
     * <p>The in-app keyboard height is released at once ([resetInAppKeyboardHeight] with
     * `waitKeyboardOpen = false`): a dismiss - back, or a tap outside - is the panel going away on
     * its own, not the system keyboard replacing it, so nothing here waits for the IME before the
     * host drops back down.</p>
     */
    fun dismiss() {
        if (destroyed) {
            return
        }
        generation++
        searching = false
        expandedHeightPx = 0
        visibility = GONE
        // Drop the search field's focus so the system IME is told to close: a dismiss - back, or a
        // tap outside - is the panel going away, not the keyboard replacing it.
        search.getSearchEditText()?.clearFocus()
        insetsController?.resetInAppKeyboardHeight(false)
        onDismissListener?.run()
    }

    fun isOpen(): Boolean = !destroyed && visibility == VISIBLE

    /** A back press while the panel is open closes it; false means the screen should handle it. */
    fun onBackPressed(): Boolean {
        if (!isOpen()) {
            return false
        }
        dismiss()
        return true
    }

    /** The screen is going away: every coroutine is cancelled and every kept tile is released. */
    fun destroy() {
        if (destroyed) {
            return
        }
        destroyed = true
        generation++
        searching = false
        expandedHeightPx = 0
        catalogJob?.cancel()
        scope.cancel()
        insetsController?.resetInAppKeyboardHeight(false)
        insertionTarget = null
        visibility = GONE
        bitmaps.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        bitmaps.clear()
        pendingImages.clear()
    }

    // ------------------------------------------------------------------ catalog

    private fun loadCatalog(force: Boolean) {
        val generationAtStart = generation
        if (!force) {
            val kept = container.emojiCatalogStore.groups.value
            if (kept.isNotEmpty()) {
                applyGroups(kept)
                return
            }
        }
        state = State.LOADING
        adapter.submit(emptyList())
        updateEmptyState()
        catalogJob?.cancel()
        catalogJob = scope.launch {
            val result = runCatching { container.discourseApiClient.fetchEmojiCatalog() }
            if (destroyed || generationAtStart != generation) {
                return@launch
            }
            result
                .onSuccess { groups -> if (groups.isEmpty()) showEmptyCatalog() else applyGroups(groups) }
                .onFailure { error ->
                    FileLog.e("emoji picker: catalog failed", error)
                    state = State.ERROR
                    adapter.submit(emptyList())
                    updateEmptyState()
                }
        }
    }

    private fun applyGroups(groups: EmojiGroupsDto) {
        // The site answers `/emojis.json` with root-relative image paths (`/images/...`), which
        // okhttp refuses (no scheme), so every tile would fall back to its name. Resolve each path
        // against the site, exactly as a post's own pictures are resolved; an absolute URL - a CDN,
        // or a site that answers one - is returned unchanged.
        val built = groups.toEmojiCategories().map { category ->
            category.copy(
                emojis = category.emojis.map { emoji ->
                    emoji.copy(url = resolvePostImageUrl(container.baseUrl, emoji.url))
                },
            )
        }
        categories = built
        state = if (built.isEmpty()) State.EMPTY else State.LOADED
        if (built.isEmpty()) {
            showEmptyCatalog()
            return
        }
        rebuildTabs(built)
        tabs.visibility = VISIBLE
        search.getSearchEditText()?.setText("")
        adapter.submit(built[0].emojis)
        updateEmptyState()
    }

    private fun showEmptyCatalog() {
        categories = emptyList()
        state = State.EMPTY
        tabs.visibility = GONE
        adapter.submit(emptyList())
        updateEmptyState()
    }

    private fun rebuildTabs(built: List<DiscourseEmojiCategory>) {
        // A refreshed catalog replaces the strip: `removeTabs` clears the old ids, positions and
        // views, so a group the site added or dropped is drawn rather than the previous set being
        // kept because the strip was not empty.
        tabs.removeTabs()
        built.forEachIndexed { index, category -> tabs.addTextTab(index, category.title) }
        tabs.finishAddingTabs()
        tabs.setInitialTabId(0)
        tabs.updateColors()
    }

    private fun showCategoryAt(index: Int) {
        if (state != State.LOADED) {
            return
        }
        categories.getOrNull(index)?.let { adapter.submit(it.emojis) }
        updateEmptyState()
    }

    private fun applySearch(query: String) {
        if (state != State.LOADED) {
            return
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            tabs.visibility = VISIBLE
            val index = tabs.getCurrentTabId().coerceIn(0, (categories.size - 1).coerceAtLeast(0))
            categories.getOrNull(index)?.let { adapter.submit(it.emojis) } ?: adapter.submit(emptyList())
            updateEmptyState()
            return
        }
        tabs.visibility = GONE
        val matches = categories.asSequence()
            .flatMap { category -> category.emojis.asSequence() }
            .filter { emoji -> emoji.name.contains(trimmed, ignoreCase = true) }
            .distinctBy { emoji -> emoji.name }
            .toList()
        adapter.submit(matches)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        when {
            state == State.LOADING -> {
                empty.visibility = VISIBLE
                empty.showProgress()
            }

            state == State.ERROR -> {
                empty.visibility = VISIBLE
                empty.showTextView()
                empty.setText(ERROR_TEXT)
            }

            state == State.EMPTY -> {
                empty.visibility = VISIBLE
                empty.showTextView()
                empty.setText(EMPTY_TEXT)
            }

            adapter.items.isEmpty() -> {
                empty.visibility = VISIBLE
                empty.showTextView()
                empty.setText(NO_MATCH_TEXT)
            }

            else -> empty.visibility = GONE
        }
    }

    // ------------------------------------------------------------------ selection

    private fun selectAt(position: Int) {
        val emoji = adapter.items.getOrNull(position) ?: return
        insertShortcode(emoji.name)
        onEmojiSelected?.onEmojiSelected(emoji.name)
    }

    /**
     * Inserts the site's own shortcode (`:name:`) at the caret, replacing a selection and leaving the
     * text around it untouched. This is the whole insertion: a plain text edit, so what reaches the
     * site is exactly the name it knows, not a Telegram document or an animated-emoji span.
     */
    private fun insertShortcode(name: String) {
        val field = insertionTarget ?: return
        val editable = field.text ?: return
        val shortcode = ":$name:"
        val start = field.selectionStart
        val end = field.selectionEnd
        val from = if (start < 0 || end < 0) editable.length else minOf(start, end)
        val to = if (start < 0 || end < 0) editable.length else maxOf(start, end)
        editable.replace(from, to, shortcode)
        field.setSelection(from + shortcode.length)
    }

    // ------------------------------------------------------------------ tiles

    /** The decoded picture for [url], or null while it is not here yet (the tile shows its name). */
    private fun bitmapFor(url: String): Bitmap? = bitmaps[url]?.takeIf { bitmap -> !bitmap.isRecycled }

    /**
     * Fetches one tile's picture through the session-authenticated image cache, decodes it and
     * rebinds. A URL that already failed is not asked for again, and a response that lands after the
     * panel closed is dropped.
     */
    private fun requestImage(emoji: DiscourseEmoji) {
        val url = emoji.url
        if (url.isEmpty() || bitmaps.containsKey(url) || failedImages.contains(url) || pendingImages.contains(url)) {
            return
        }
        pendingImages.add(url)
        val generationAtStart = generation
        // Scheduled, not run inline: this coroutine starts during a bind (the tile has no picture
        // yet), and its read can return without ever suspending (a disk hit, or a failure that skips
        // the decode). On the scope's `Dispatchers.Main.immediate` that completion would run
        // synchronously inside `onBindViewHolder` and call `notifyDataSetChanged()` while the
        // RecyclerView computes layout, which AndroidX forbids (the device crash in
        // `crash-emoji.txt`). `Dispatchers.Main` always posts, so the completion lands after the
        // current layout pass.
        scope.launch(Dispatchers.Main) {
            val file = runCatching { imageFetcher(url) }.getOrNull()
            if (destroyed || generationAtStart != generation) {
                pendingImages.remove(url)
                return@launch
            }
            val bitmap = file?.let { cached ->
                withContext(imageDecodeDispatcher) { BitmapFactory.decodeFile(cached.absolutePath) }
            }
            // The decode runs off the main thread, so the panel can be torn down (or reopened)
            // while it runs. `destroy` has already recycled and cleared every kept tile by now, so
            // storing this one would leak it - and notifying would hand a fresh bitmap to a panel
            // that is gone. Recheck liveness here, after the decode, before touching either.
            if (destroyed || generationAtStart != generation) {
                if (bitmap != null && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
                pendingImages.remove(url)
                return@launch
            }
            if (bitmap != null) {
                bitmaps[url] = bitmap
            } else {
                failedImages.add(url)
            }
            pendingImages.remove(url)
            adapter.notifyDataSetChanged()
        }
    }

    private fun panelHeightPx(): Int {
        val screen = resources.displayMetrics.heightPixels
        return minOf(AndroidUtilities.dp(PANEL_HEIGHT_DP.toFloat()), (screen * 0.5f).toInt())
    }

    // ------------------------------------------------------------------ the grid

    private inner class EmojiCell(context: Context) : FrameLayout(context) {

        private val image = BackupImageView(context)
        private val label = TextView(context)

        init {
            // A fixed square-ish row: the grid hands the cell the column width, and this gives it a
            // stable height so a bound tile never collapses to its padding.
            layoutParams = RecyclerView.LayoutParams(
                LayoutHelper.MATCH_PARENT,
                AndroidUtilities.dp(CELL_HEIGHT_DP.toFloat()),
            )
            addView(image, LayoutHelper.createFrame(IMAGE_SIZE_DP, IMAGE_SIZE_DP, Gravity.CENTER))

            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f)
            label.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelEmptyText, resourcesProvider))
            label.gravity = Gravity.CENTER
            label.maxLines = 2
            label.ellipsize = TextUtils.TruncateAt.END
            label.setPadding(
                AndroidUtilities.dp(2f), AndroidUtilities.dp(2f),
                AndroidUtilities.dp(2f), AndroidUtilities.dp(2f),
            )
            addView(label, LayoutHelper.createFrameMatchParent())

            setPadding(AndroidUtilities.dp(2f), AndroidUtilities.dp(2f), AndroidUtilities.dp(2f), AndroidUtilities.dp(2f))
        }

        fun bind(emoji: DiscourseEmoji) {
            // The readable description is the shortcode the reader will get, whether or not the
            // picture arrived; a screen reader and the fallback tile both read it from here.
            contentDescription = ":" + emoji.name + ":"
            val bitmap = bitmapFor(emoji.url)
            if (bitmap != null) {
                image.setImageBitmap(bitmap)
                image.visibility = VISIBLE
                label.visibility = GONE
            } else {
                image.setImageBitmap(null)
                image.visibility = GONE
                label.text = emoji.name.replace('_', ' ')
                label.visibility = VISIBLE
                requestImage(emoji)
            }
        }
    }

    private class EmojiHolder(view: View) : RecyclerView.ViewHolder(view)

    private inner class GridAdapter : RecyclerView.Adapter<EmojiHolder>() {

        val items = ArrayList<DiscourseEmoji>()

        fun submit(next: List<DiscourseEmoji>) {
            items.clear()
            items.addAll(next)
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiHolder =
            EmojiHolder(EmojiCell(parent.context))

        override fun onBindViewHolder(holder: EmojiHolder, position: Int) {
            (holder.itemView as EmojiCell).bind(items[position])
        }
    }

    private companion object {
        const val COLUMNS = 8
        const val CELL_HEIGHT_DP = 48
        const val HEADER_HEIGHT_DP = 58
        const val TABS_HEIGHT_DP = 44
        const val PANEL_HEIGHT_DP = 300
        const val IMAGE_SIZE_DP = 36
        const val SEARCH_HINT = "搜索表情"
        const val EMPTY_TEXT = "这个站点还没有表情"
        const val ERROR_TEXT = "表情加载失败，点击重试"
        const val NO_MATCH_TEXT = "没有匹配的表情"
    }
}
