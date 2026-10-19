// SPDX-License-Identifier: GPL-2.0-or-later
// Built from upstream's own input components (RecyclerListView + GridLayoutManager, SearchField,
// EmptyTextProgressView), pointed at the site's GIF proxy instead of Telegram's sticker sets.

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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import io.github.baba983.nativedo.core.network.DiscourseGif
import io.github.baba983.nativedo.core.network.DiscourseGifCategory
import io.github.baba983.nativedo.core.network.DiscourseGifUnavailableException
import io.github.baba983.nativedo.core.network.SessionRecoveryRequiredException
import io.github.baba983.nativedo.core.session.LoginRequiredException
import io.github.baba983.nativedo.core.session.SessionLoggedOutException
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ScaleStateListAnimator
import org.telegram.ui.Components.SearchField
import org.telegram.ui.Components.inset.InAppKeyboardInsetView
import org.telegram.ui.Components.inset.WindowInsetsInAppController
import org.telegram.ui.Components.inset.WindowInsetsProvider

/**
 * The site's GIF picker: a grid of the provider's categories or search results, drawn with
 * upstream's own input components and inserted into the composer as image Markdown.
 *
 * <p>Discourse keeps the GIF provider key on the server and exposes `/gifs/categories.json` and
 * `/gifs/search.json` only when `enable_gifs` is on and a key is configured. This panel therefore
 * treats the server's answer as the capability: nothing is fetched until the reader chooses the GIF
 * affordance, an absent route (404) is explained instead of drawn as an empty GIF wall, a challenge
 * goes to the existing WebView recovery, and a refusal, a rate limit or a malformed body stays
 * retryable. A refusal is never reported as "this site has no GIFs": a missing provider key and a
 * denied permission look the same from a 403. No provider key is known here and no Telegram
 * sticker/GIF RPC is involved.</p>
 *
 * <p>Categories are the provider's starting wall. A category tile is a search term, not an
 * insertable GIF; selecting a result inserts the site component's own
 * `![title|widthxheight](url)` Markdown at the composer caret and closes the panel. It never sends
 * the post. Provider thumbnails are loaded over a separate client that carries no site cookies, so
 * a CDN request cannot forward the reader's session.</p>
 *
 * <h3>Hosting</h3>
 *
 * <p>Like the emoji panel, this view is added to `ChatInputViewsContainer`'s in-app keyboard bubble
 * and reports its height through [setInsetsController]. The owner forwards Back, pause/destroy and
 * the in-app IME height. It implements [InAppKeyboardInsetView] so the host can pad the panel by the
 * real navigation-bar inset.</p>
 */
class DiscourseGifPicker @JvmOverloads constructor(
    context: Context,
    private val container: AppContainer,
    private val resourcesProvider: Theme.ResourcesProvider? = null,
) : FrameLayout(context), InAppKeyboardInsetView {

    /** Told after a GIF's Markdown has been inserted, so the owner can return focus to the field. */
    fun interface OnGifSelected {
        fun onGifSelected(gif: DiscourseGif)
    }

    private enum class State { LOADING, CONTENT, EMPTY, UNAVAILABLE, ERROR, SESSION }

    private enum class Mode { CATEGORIES, SEARCH }

    private sealed interface Entry {
        val title: String
        val imageUrl: String?

        data class Category(val category: DiscourseGifCategory) : Entry {
            override val title: String get() = category.name
            override val imageUrl: String? get() = category.imageUrl
        }

        data class Media(val gif: DiscourseGif) : Entry {
            override val title: String get() = gif.title
            override val imageUrl: String? get() = gif.url
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gifClient = container.discourseGifClient

    /**
     * A tile's picture read. Production goes through the session-less image client to the provider
     * CDN; tests can replace the read without changing production behavior.
     */
    internal var imageFetcher: suspend (String) -> File? =
        { url -> DiscourseReactionEmoji.cache(container.gifImageOkHttpClient, url) }

    /** The dispatcher a tile's decode runs on; a seam for deterministic late-decode tests. */
    internal var imageDecodeDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val bitmaps = HashMap<String, Bitmap>()
    private val failedImages = HashSet<String>()
    private val pendingImages = HashSet<String>()

    private var categories: List<DiscourseGifCategory> = emptyList()
    private var state = State.LOADING
    private var mode = Mode.CATEGORIES
    private var generation = 0
    private var destroyed = false
    private var catalogJob: Job? = null
    private var searchJob: Job? = null
    private var nextPosition: String? = null
    private var query = ""
    private var loadingMore = false
    private var suppressQueryChange = false
    private var boundSessionGeneration = -1
    private var sessionMessage: String? = null

    private var composerField: EditTextBoldCursor? = null
    private var onGifSelected: OnGifSelected? = null
    private var onBackToEmoji: Runnable? = null
    private var onDismissListener: Runnable? = null
    private var insetsController: WindowInsetsInAppController? = null

    private var navigationBarHeight = 0
    private var searching = false
    private var expandedHeightPx = 0

    private val search: SearchField
    private val grid: RecyclerListView
    private val empty: EmptyTextProgressView
    private val adapter = GifAdapter()

    init {
        setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground, resourcesProvider))

        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL

        search = object : SearchField(context, true, resourcesProvider) {
            override fun onTextChange(text: String) {
                if (!suppressQueryChange) {
                    applyQuery(text)
                }
            }
        }
        search.setHint(SEARCH_HINT)
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

        val gridHolder = FrameLayout(context)
        grid = object : RecyclerListView(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                val manager = layoutManager as GridLayoutManager
                manager.spanCount = maxOf(
                    2,
                    w / AndroidUtilities.dp(if (AndroidUtilities.isTablet()) 180f else 140f),
                )
            }
        }
        val gridManager = GridLayoutManager(context, COLUMNS)
        grid.layoutManager = gridManager
        grid.adapter = adapter
        grid.isVerticalScrollBarEnabled = false
        grid.setInstantClick(true)
        grid.setPadding(AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(6f), 0)
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || mode != Mode.SEARCH || loadingMore || nextPosition == null) {
                    return
                }
                val last = gridManager.findLastVisibleItemPosition()
                if (last >= adapter.itemCount - 2) {
                    loadMore()
                }
            }
        })
        grid.setOnItemClickListener(object : RecyclerListView.OnItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                choose(adapter.items.getOrNull(position) ?: return)
            }
        })
        gridHolder.addView(grid, LayoutHelper.createFrameMatchParent())

        empty = EmptyTextProgressView(context, null, resourcesProvider)
        empty.setOnTouchListener { _: View, event: MotionEvent ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                retry()
            }
            true
        }
        gridHolder.addView(empty, LayoutHelper.createFrameMatchParent())
        column.addView(gridHolder, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))

        val controls = FrameLayout(context)
        val backToEmoji = ImageView(context).apply {
            setImageResource(R.drawable.smiles_tab_smiles)
            setColorFilter(Theme.getColor(Theme.key_chat_emojiPanelBackspace, resourcesProvider))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = BACK_TO_EMOJI_LABEL
            isFocusable = true
            ScaleStateListAnimator.apply(this)
            setOnClickListener { onBackToEmoji?.run() }
        }
        controls.addView(backToEmoji, LayoutHelper.createFrame(48, 48, Gravity.LEFT))
        controls.addView(
            TextView(context).apply {
                text = MODE_LABEL
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
                setTextColor(Theme.getColor(Theme.key_chat_emojiPanelEmptyText, resourcesProvider))
                gravity = Gravity.CENTER
            },
            LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER),
        )
        column.addView(controls, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48))
        addView(column, LayoutHelper.createFrameMatchParent())

        visibility = GONE
        state = State.LOADING
    }

    // ------------------------------------------------------------------ host API

    /** The composer field the selected GIF's Markdown is written into, at the caret. */
    fun setInsertionTarget(field: EditTextBoldCursor?) {
        composerField = field
    }

    /** Told after a GIF has been inserted, so the owner can return the reader to the field. */
    fun setOnGifSelected(listener: OnGifSelected?) {
        onGifSelected = listener
    }

    /** Told when the reader asks to go back to the site emoji panel. */
    fun setOnBackToEmoji(listener: Runnable?) {
        onBackToEmoji = listener
    }

    fun setOnDismissListener(listener: Runnable?) {
        onDismissListener = listener
    }

    fun setInsetsController(controller: WindowInsetsInAppController?) {
        insetsController = controller
    }

    override fun applyNavigationBarHeight(height: Int) {
        if (navigationBarHeight == height) {
            return
        }
        navigationBarHeight = height
        updateBottomPadding()
        requestLayout()
    }

    private fun updateBottomPadding(panelHeight: Int = height) {
        val imeSpace = minOf(imeBottomInsetPx(), (panelHeight - panelHeightPx()).coerceAtLeast(0))
        val bottom = if (searching) maxOf(navigationBarHeight, imeSpace) else navigationBarHeight
        setPadding(paddingLeft, paddingTop, paddingRight, bottom)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updateBottomPadding(MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

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

    private fun expandForSearch() {
        val ime = imeBottomInsetPx()
        if (ime <= 0) {
            return
        }
        updateBottomPadding()
        val expanded = ime + panelHeightPx()
        if (expandedHeightPx == expanded) {
            return
        }
        expandedHeightPx = expanded
        insetsController?.requestInAppKeyboardHeightIncludeNavbar(expanded)
    }

    private fun collapseToPanel() {
        updateBottomPadding()
        if (expandedHeightPx == 0) {
            return
        }
        expandedHeightPx = 0
        if (isOpen()) {
            insetsController?.requestInAppKeyboardHeightIncludeNavbar(panelHeightPx())
        }
    }

    private fun imeBottomInsetPx(): Int =
        (insetsController as? WindowInsetsProvider)
            ?.getInsets(WindowInsetsCompat.Type.ime())
            ?.bottom ?: 0

    /**
     * Opens the picker. The categories read is the capability check: a site that does not serve the
     * route is explained on this panel, never drawn as a pretend GIF wall, and any other refusal or
     * outage stays a retry the reader can press.
     */
    fun show() {
        if (destroyed) {
            return
        }
        generation++
        searching = false
        expandedHeightPx = 0
        updateBottomPadding()
        visibility = VISIBLE
        composerField?.let { AndroidUtilities.hideKeyboard(it) }
        insetsController?.requestInAppKeyboardHeightIncludeNavbar(panelHeightPx())
        resetSearchField()
        loadCategories(force = false)
    }

    /**
     * Closes the panel. An in-flight answer is ignored: it must not paint a closed panel or a panel
     * reopened for a different reader.
     */
    fun dismiss() {
        if (destroyed) {
            return
        }
        generation++
        searching = false
        expandedHeightPx = 0
        updateBottomPadding()
        visibility = GONE
        search.getSearchEditText()?.clearFocus()
        insetsController?.resetInAppKeyboardHeight(false)
        adapter.items.clear()
        adapter.notifyDataSetChanged()
        nextPosition = null
        onDismissListener?.run()
    }

    fun isOpen(): Boolean = !destroyed && visibility == VISIBLE

    fun onBackPressed(): Boolean {
        if (!isOpen()) {
            return false
        }
        dismiss()
        return true
    }

    fun destroy() {
        if (destroyed) {
            return
        }
        destroyed = true
        generation++
        searching = false
        expandedHeightPx = 0
        catalogJob?.cancel()
        searchJob?.cancel()
        scope.cancel()
        insetsController?.resetInAppKeyboardHeight(false)
        composerField = null
        visibility = GONE
        bitmaps.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        bitmaps.clear()
        pendingImages.clear()
    }

    // ------------------------------------------------------------------ categories + search

    private fun resetSearchField() {
        suppressQueryChange = true
        search.getSearchEditText()?.setText("")
        suppressQueryChange = false
        query = ""
    }

    private fun loadCategories(force: Boolean) {
        val sessionGeneration = container.sessionCoordinator.currentRequestContext().generation
        if (boundSessionGeneration != sessionGeneration) {
            categories = emptyList()
            boundSessionGeneration = sessionGeneration
        }
        if (!force && categories.isNotEmpty() && mode == Mode.CATEGORIES && adapter.items.isNotEmpty()) {
            showCategories()
            return
        }
        val generationAtStart = generation
        state = State.LOADING
        mode = Mode.CATEGORIES
        nextPosition = null
        adapter.items.clear()
        adapter.notifyDataSetChanged()
        updateEmptyState()
        catalogJob?.cancel()
        catalogJob = scope.launch {
            val result = runCatching { gifClient.fetchCategories() }
            if (!isLive(generationAtStart)) {
                return@launch
            }
            result
                .onSuccess { fetched ->
                    categories = fetched.map { category ->
                        category.copy(
                            imageUrl = category.imageUrl?.let { url ->
                                resolvePostImageUrl(container.baseUrl, url)
                            },
                        )
                    }
                    boundSessionGeneration = container.sessionCoordinator.currentRequestContext().generation
                    if (categories.isEmpty()) {
                        showHint()
                    } else {
                        showCategories()
                    }
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) {
                        return@launch
                    }
                    applyFailure(error)
                }
        }
    }

    private fun showCategories() {
        mode = Mode.CATEGORIES
        nextPosition = null
        state = if (categories.isEmpty()) State.EMPTY else State.CONTENT
        resetSearchField()
        adapter.items.clear()
        categories.forEach { category -> adapter.items.add(Entry.Category(category)) }
        adapter.notifyDataSetChanged()
        (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(0, 0)
        updateEmptyState()
    }

    private fun showHint() {
        mode = Mode.CATEGORIES
        state = State.EMPTY
        adapter.items.clear()
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun applyQuery(text: String) {
        val trimmed = text.trim()
        query = trimmed
        if (trimmed.length < MIN_QUERY_LENGTH) {
            searchJob?.cancel()
            if (trimmed.isEmpty() && categories.isNotEmpty()) {
                showCategories()
            } else {
                showHint()
            }
            return
        }
        searchJob?.cancel()
        val generationAtStart = generation
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (!isLive(generationAtStart) || query != trimmed) {
                return@launch
            }
            performSearch(trimmed, reset = true)
        }
    }

    private fun startCategorySearch(category: DiscourseGifCategory) {
        searchJob?.cancel()
        suppressQueryChange = true
        search.getSearchEditText()?.setText(category.searchTerm)
        search.getSearchEditText()?.setSelection(category.searchTerm.length)
        suppressQueryChange = false
        query = category.searchTerm
        searchJob = scope.launch { performSearch(category.searchTerm, reset = true) }
    }

    private fun loadMore() {
        val cursor = nextPosition ?: return
        if (loadingMore) {
            return
        }
        val generationAtStart = generation
        searchJob = scope.launch {
            if (!isLive(generationAtStart) || nextPosition != cursor) {
                return@launch
            }
            performSearch(query, reset = false)
        }
    }

    private suspend fun performSearch(text: String, reset: Boolean) {
        val generationAtStart = generation
        if (reset) {
            mode = Mode.SEARCH
            nextPosition = null
            adapter.items.clear()
            adapter.notifyDataSetChanged()
            state = State.LOADING
            updateEmptyState()
        } else {
            loadingMore = true
        }
        try {
            val position = if (reset) "0" else nextPosition
            val page = gifClient.search(text, position)
            if (!isLive(generationAtStart)) {
                return
            }
            val resolved = page.gifs.map { gif ->
                gif.copy(url = resolvePostImageUrl(container.baseUrl, gif.url))
            }
            if (reset) {
                adapter.items.clear()
            }
            adapter.items.addAll(resolved.map { gif -> Entry.Media(gif) })
            nextPosition = page.next
            state = if (adapter.items.isEmpty()) State.EMPTY else State.CONTENT
            adapter.notifyDataSetChanged()
            updateEmptyState()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (!isLive(generationAtStart)) {
                return
            }
            if (reset) {
                applyFailure(error)
            } else {
                // A failed later page keeps the pages already shown; the next scroll can ask again.
                FileLog.e("gif picker: next page failed", error)
            }
        } finally {
            if (!reset) {
                loadingMore = false
            }
        }
    }

    private fun applyFailure(error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) {
            return
        }
        state = when (error) {
            is DiscourseGifUnavailableException -> State.UNAVAILABLE
            is SessionRecoveryRequiredException -> {
                sessionMessage = SESSION_TEXT
                State.SESSION
            }
            is SessionLoggedOutException, is LoginRequiredException -> {
                sessionMessage = LOGIN_TEXT
                State.SESSION
            }
            // A refusal (a provider key, a permission, a rate limit) keeps the retry affordance:
            // the site answered, so a transient one can succeed and a permanent one is the site's.
            else -> State.ERROR
        }
        adapter.items.clear()
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun retry() {
        if (state == State.CONTENT || state == State.LOADING) {
            return
        }
        if (mode == Mode.SEARCH && query.length >= MIN_QUERY_LENGTH) {
            searchJob?.cancel()
            val generationAtStart = generation
            searchJob = scope.launch {
                if (isLive(generationAtStart)) {
                    performSearch(query, reset = true)
                }
            }
        } else {
            loadCategories(force = true)
        }
    }

    private fun updateEmptyState() {
        when (state) {
            State.LOADING -> {
                empty.visibility = VISIBLE
                empty.setText("")
                empty.showProgress()
            }
            State.CONTENT -> {
                empty.visibility = if (adapter.items.isEmpty()) VISIBLE else GONE
                if (adapter.items.isEmpty()) {
                    empty.showTextView()
                    empty.setText(HINT_TEXT)
                }
            }
            State.EMPTY -> {
                empty.visibility = VISIBLE
                empty.showTextView()
                empty.setText(if (mode == Mode.SEARCH) NO_RESULTS_TEXT else HINT_TEXT)
            }
            State.UNAVAILABLE -> {
                empty.visibility = VISIBLE
                empty.showTextView()
                empty.setText(UNAVAILABLE_TEXT)
            }
            State.SESSION -> {
                empty.visibility = VISIBLE
                empty.showTextView()
                empty.setText(sessionMessage ?: SESSION_TEXT)
            }
            State.ERROR -> {
                empty.visibility = VISIBLE
                empty.showTextView()
                empty.setText(ERROR_TEXT)
            }
        }
    }

    // ------------------------------------------------------------------ selection

    private fun choose(entry: Entry) {
        when (entry) {
            is Entry.Category -> startCategorySearch(entry.category)
            is Entry.Media -> {
                insertMarkdown(entry.gif)
                dismiss()
                onGifSelected?.onGifSelected(entry.gif)
            }
        }
    }

    /**
     * Writes the site component's own Markdown at the caret, replacing a selection and leaving the
     * text around it byte-for-byte. This is the whole insertion: no Telegram document, no send.
     */
    private fun insertMarkdown(gif: DiscourseGif) {
        val field = composerField ?: return
        val editable = field.text ?: return
        val title = gif.title
            .ifBlank { "GIF" }
            .replace("\\", "\\\\")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("\n", " ")
        val size = if (gif.width > 0 && gif.height > 0) {
            "|${gif.width}x${gif.height}"
        } else {
            ""
        }
        val markup = "\n![$title$size](${gif.url})\n"
        val start = field.selectionStart
        val end = field.selectionEnd
        val from = if (start < 0 || end < 0) editable.length else minOf(start, end)
        val to = if (start < 0 || end < 0) editable.length else maxOf(start, end)
        editable.replace(from, to, markup)
        field.setSelection(from + markup.length)
    }

    // ------------------------------------------------------------------ tiles

    private fun isLive(generationAtStart: Int): Boolean =
        !destroyed && isOpen() && generationAtStart == generation

    private fun bitmapFor(url: String?): Bitmap? =
        url?.let { key -> bitmaps[key]?.takeIf { bitmap -> !bitmap.isRecycled } }

    private fun requestImage(url: String?) {
        if (url.isNullOrEmpty() || bitmaps.containsKey(url) || failedImages.contains(url) ||
            pendingImages.contains(url)
        ) {
            return
        }
        pendingImages.add(url)
        val generationAtStart = generation
        // Scheduled, not run inline: the read may return without suspending (a disk hit, or a
        // failure that skips the decode), and its completion would then notify the adapter inside
        // the RecyclerView's layout pass.
        scope.launch(Dispatchers.Main) {
            val file = runCatching { imageFetcher(url) }.getOrNull()
            if (!isLive(generationAtStart)) {
                pendingImages.remove(url)
                return@launch
            }
            val bitmap = file?.let { cached ->
                withContext(imageDecodeDispatcher) { BitmapFactory.decodeFile(cached.absolutePath) }
            }
            if (!isLive(generationAtStart)) {
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
            adapter.items.forEachIndexed { index, entry ->
                if (entry.imageUrl == url) {
                    adapter.notifyItemChanged(index, url)
                }
            }
        }
    }

    private inner class GifCell(context: Context) : FrameLayout(context) {

        private val image = ImageView(context)
        private val label = TextView(context)

        init {
            layoutParams = RecyclerView.LayoutParams(
                LayoutHelper.MATCH_PARENT,
                AndroidUtilities.dp(CELL_HEIGHT_DP.toFloat()),
            )
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            addView(image, LayoutHelper.createFrameMatchParent())

            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
            label.setTextColor(0xffffffff.toInt())
            label.setBackgroundColor(0x99000000.toInt())
            label.gravity = Gravity.CENTER
            label.maxLines = 2
            label.ellipsize = TextUtils.TruncateAt.END
            label.setPadding(
                AndroidUtilities.dp(4f), AndroidUtilities.dp(2f),
                AndroidUtilities.dp(4f), AndroidUtilities.dp(2f),
            )
            addView(
                label,
                LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT,
                    Gravity.BOTTOM,
                ),
            )
            setPadding(
                AndroidUtilities.dp(3f), AndroidUtilities.dp(3f),
                AndroidUtilities.dp(3f), AndroidUtilities.dp(3f),
            )
        }

        fun bind(entry: Entry) {
            val title = entry.title
            val url = entry.imageUrl
            contentDescription = title
            val bitmap = bitmapFor(url)
            if (bitmap != null) {
                image.setImageBitmap(bitmap)
                image.visibility = VISIBLE
                label.visibility = if (entry is Entry.Category) VISIBLE else GONE
            } else {
                image.setImageBitmap(null)
                image.visibility = GONE
                label.text = title
                label.visibility = VISIBLE
                requestImage(url)
            }
            if (entry is Entry.Category) {
                label.text = entry.category.name
            }
        }
    }

    private class GifHolder(view: View) : RecyclerView.ViewHolder(view)

    private inner class GifAdapter : RecyclerView.Adapter<GifHolder>() {

        val items = ArrayList<Entry>()

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifHolder =
            GifHolder(GifCell(parent.context))

        override fun onBindViewHolder(holder: GifHolder, position: Int) {
            (holder.itemView as GifCell).bind(items[position])
        }
    }

    private fun panelHeightPx(): Int {
        val screen = resources.displayMetrics.heightPixels
        return minOf(AndroidUtilities.dp(PANEL_HEIGHT_DP.toFloat()), (screen * 0.5f).toInt())
    }

    private companion object {
        const val COLUMNS = 2
        const val CELL_HEIGHT_DP = 118
        const val HEADER_HEIGHT_DP = 50
        const val PANEL_HEIGHT_DP = 300
        const val SEARCH_DEBOUNCE_MS = 450L
        const val MIN_QUERY_LENGTH = 2

        const val MODE_LABEL = "GIF"
        const val BACK_TO_EMOJI_LABEL = "表情"
        const val SEARCH_HINT = "搜索 GIF"
        const val HINT_TEXT = "输入关键词搜索 GIF"
        const val NO_RESULTS_TEXT = "没有找到 GIF"
        const val UNAVAILABLE_TEXT = "这个站点没有启用 GIF"
        const val SESSION_TEXT = "登录状态暂时无法验证，请完成网站验证后重试"
        const val LOGIN_TEXT = "登录已失效，请重新登录"
        const val ERROR_TEXT = "GIF 加载失败，点击重试"
    }
}
